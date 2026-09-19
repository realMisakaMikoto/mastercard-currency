# 万事达汇率转换（Mastercard Currency）

> **自用项目 / Personal use only** —— 个人自用的汇率查询小工具，非官方、非商业、无支持承诺。
> 万事达标识仅限个人自用，详见 [NOTICE.md](NOTICE.md)。

一个原生 Android 小工具：查询时打开**万事达官方汇率换算页**，并在该页面内发起同源请求，
读取万事达返回的结算汇率，完成金额换算。

界面与动效参考 **Wise** 汇率换算页（`wise.com/zh-cn/currency-converter/jpy-to-cny-rate`）重新设计：
森林绿 Hero + 柠檬绿主色 + 覆盖式白卡；货币旗帜使用真正的圆形国旗图片。

**已完成实时汇率验证。**

| 实测（模拟器，2026-09-19） | 结果 |
|---|---|
| USD → CNY 10,000 | `1 USD = 6.6991 CNY` → **66,991.00 CNY** |
| JPY → CNY 10,000 | `1 JPY = 0.042993 CNY` → **429.93 CNY** |

---

## 前端设计（参考 Wise）

| 项 | 取值 | 来源 |
|---|---|---|
| Hero 背景 | `#163300` | 从 Wise 页面截图取色 |
| 主色 / 主按钮 | `#9FE870`（柠檬绿），文字 `#163300` | `--coin-colour` |
| 信息横幅 | `#EBF9FF` / 文字 `#37517E` | `--color-background-accent` / `--color-content-primary` |
| 描边 | `rgba(0,0,0,.1)` | `--color-border-neutral` |
| 圆角 | 10 / 16 / 32 / 9999 | `--radius-small/medium/xlarge/full` |
| 布局 | 深色 Hero（大标题「日元兑人民币」）→ 白色圆角卡片上移 28dp 覆盖 → 底部操作区 | Wise 同款结构 |
| 系统栏 | 透明 + 浅色图标，Hero 延伸到状态栏下 | 边缘到边缘 |

**动画**

- 互换按钮：每次互换旋转 180°，`CubicBezier(0.2,0,0,1)` 缓动，按下缩放至 0.9
- 汇率 / 换算金额：`AnimatedContent` 淡入 + 垂直位移，数值变化时平滑切换
- 主按钮：按下缩放至 0.975
- 卡片入场：透明度 + 上移 24dp 的一次性入场动画

**货币旗帜**

原始实现把 emoji 放大 1.15 倍再切圆，导致日本旗变成「白圈+偏移红块」、美国旗被切变形。
现在改为真实圆形国旗图片：用浏览器把 MIT  licensed 的
[`HatScripts/circle-flags`](https://github.com/HatScripts/circle-flags) SVG 渲染成雪碧图，
再切成 152 张 64×64 PNG（共 352 KB）打进 `assets/flags/<cc>.png`。


---

## 两级加载提示（v1.2.0）

「建立会话」和「取一次汇率」的耗时不在一量级，所以用两种提示：

| 场景 | 触发条件 | 表现 |
|---|---|---|
| **建会话（取 Cookie）** | 查询时会话尚未就绪（冷启动、Cookie 过期） | **全屏**：万事达 Logo 动画 + 「正在建立万事达会话…」+ 说明首次 5–10 秒 |
| **取汇率（复用会话）** | 会话已就绪，只是换币种/金额再请求一次 | **内联**：只在输入框位置显示 Logo 动画 + 「正在获取汇率…」，卡片头部（汇率行）保留 |

判定用 `WebViewFetcher.isSessionReady`，在查询发起的那一刻决定，所以用户看到的提示跟实际等待原因是匹配的。

加载动画直接用你提供的 `Mastercard Logo.gif`（150×150、150 帧，双圆收缩→变色→压细→展开）：
`app/src/main/assets/mastercard_loader.gif`。
API 28+ 用 `AnimatedImageDrawable` 循环播放；API 26–27 平台没有动图解码器，显示首帧静态图。

---

## 输入联动（v1.4.0）

**1. 改金额即时换算（像 Google 换算器）**

万事达返回的 `conversionRate` **与金额无关**，所以换算结果直接由金额框的当前内容推导，
而不是用查询那一刻的金额快照：

```kotlin
val liveAmount = RateMath.parseDecimal(state.amountText)?.takeIf { it.signum() > 0 }
val billed = success?.let { r -> liveAmount?.let { RateMath.billedAmount(it, r.quote.conversionRate) } }
```

改金额 → 结果与明细立刻跟着变，**不发任何新请求**。
此时副标题会标注「· 按已获取汇率实时换算」，避免误解成重新查询过。

实测：查询得到 6.6991 后，把金额从 1,000 改成 20,000 → 结果立即变为 133,982.00，
`lookup` 日志次数保持 1（无新请求）。

**2. 改币种立刻清空结果**

`selectCurrency()` 里，只要选中的币种与当前不同，就把状态置回 `Idle` —— 结果、汇率行、
明细卡同时清空，**不需要点查询，也不会自动查询**。手续费同理（手续费会改变万事达返回的汇率），
日期变更原本就会清空。

实测：把目标币种从 CNY 改成 JPY → 结果与明细清空，`lookup` 次数不变。

> 说明：金额联动的「较短时间」不需要额外计时器 —— 任何会改变汇率的输入（币种、手续费、日期）
> 都会先把结果清空，所以屏幕上存在结果时，pair/fee/date 必然与那次查询一致。

---

## 不做本地缓存（v1.3.0）

**每次查询都是实时请求，没有本地汇率缓存。**

之前有一个 SharedPreferences 缓存，但它**从不失效**：写入时存了时间戳，代码里却没有任何地方读它做过期判断。
更糟的是留空日期时缓存 key 用的是字面量 `"today"`（不是当天日期），于是 9/19 缓存的汇率，
9/26 再查（日期留空）会命中同一条，把旧汇率当作「缓存结果」返回。

现在整个缓存层已删除（`RateCache.kt` 移除，`RateResult.Success.fromCache` 字段移除，界面不再有
「缓存结果」标记和「清除缓存」入口）。代价是离线时无法给出旧数据 —— 此时会走错误提示里的「手动输入汇率」。

实测：同一个币种对连续查询两次，两次都会产生真实的 `lookup` 日志（此前第二次是静默的缓存命中）。

---

## 会话预热与复用（v1.1.0）

因为费率接口有 Akamai 保护（详见下文），**必须有一次真实浏览器会话**才能拿到汇率。
这部分做了三件事，把「首次要等 30 秒」降到 **1.7 秒**：

| 优化 | 做法 | 效果 |
|---|---|---|
| **启动预热** | WebView 一挂载就在后台加载换算页并接受 Cookie 弹窗 | 点查询前会话已就绪 |
| **页面复用** | 已加载该页且 Cookie 有效时直接发请求，不再 `loadUrl` | 换币种查询不再重新加载页面 |
| **Cookie 失效自愈** | 复用后若请求失败，判定 Cookie 过期 → 强制重载一次并重试 | 不会因陈旧会话卡死 |
| **不显示官网** | 查询时用全屏不透明进度层盖住 WebView | 不会再看到官网页面跳出来 |

### 「为什么点互换会重新取 Cookie」—— 一个真实 bug 的修复记录

v1.0.0 里**每次缓存未命中都会重新加载页面**。互换按钮必然改变币种对 → 必然缓存未命中 →
必然重新加载（也就是重新取 Cookie）。所以表现为「清完缓存后前两次要加载，之后不用」——
因为查过两次之后两个方向都进缓存了，缓存命中就不走网络。

v1.1.0 加了页面复用，但还留了一个隐蔽缺陷：**`pageReady` 是取数层的实例字段，
而 WebView 可能被重建**（Activity 重建、窗口尺寸变化、进程重启）。重建后
`pageReady` 仍是 `true`、但新 WebView 是空白的 → 复用判断错误地跳过加载 → 请求失败 →
触发「自愈」路径强制整页重载 —— 症状正是「又去取了一次 Cookie」。

v1.1.1 修法：`attach()` 检测到**换了一个 WebView 实例**时立即 `invalidatePage()`。

修复后的实测日志（`adb logcat -s MCFX`）：

```
navigate: LOAD page (forceReload=true pageReady=false ...)   ← 冷启动预热，仅此一次
navigate: REUSE already-loaded page (no request)             ← 首次查询 1.3s
navigate: REUSE already-loaded page (no request)             ← 点互换 ~1s
navigate: REUSE already-loaded page (no request)             ← 清缓存后连续互换，均为 ~1s
```

**现在只有两种情况会重新取 Cookie**：① App 冷启动时的后台预热；② 边缘 Cookie 真的过期时（自愈重载）。

实测（模拟器，`pm clear` 全新安装）：

| 场景 | 耗时 | 日志中的尝试次数 |
|---|---|---|
| 冷安装 → 启动 → 等 25s → 查询 | **1.7s** | 1（无页面加载） |
| 换币种再查 | **1.1s** | 1（无页面加载） |

> App **启动时**并不会弹出官网 —— 那个 WebView 是空的；「打开官网」实际发生在
> **第一次查询**时，上面的预热把它提前到了后台。若连这次后台请求也不想要
> （例如流量敏感），删掉 `ConverterViewModel.attachWebView` 里的 `prewarm()` 调用即可，
> 代价是首次查询回到 3–10 秒。

---

## 功能

| 能力 | 说明 |
|---|---|
| 汇率来源 | 万事达官网换算器后台接口 `conversion-rates`（实时） |
| 取数方式 | 隐藏 WebView 打开换算页 → 页面内 `fetch` 同源请求 → 读取返回 JSON |
| 货币选择 | 下拉 + 搜索，支持**货币代码 / 货币名称 / 国家名称**（中英文 + 拼音别名，152 种） |
| 汇率日期 | 日期选择框，**可不填**；不填时按官网口径取「最新已发布汇率」 |
| 银行手续费 | 可空，**默认 0，按百分比**（与官网口径一致） |
| 换算展示 | 汇率行、不含手续费汇率、换算金额、手续费拆分、合计 |
| 容错 | 三级降级（页面内请求 → 原生 HTTPS → 旧接口兜底）+ 手动汇率兜底；**不缓存汇率，每次实时请求** |
| 诊断 | 每个候选 URL、HTTP 状态码、页面标题、`_abck` 反爬 Cookie、原始响应 |

---

## 构建

环境要求：JDK 17、Android SDK（platform 37、build-tools 36+）。

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleRelease      # 产出 app/build/outputs/apk/release/app-release.apk
.\gradlew.bat :app:testDebugUnitTest    # 单元测试
```

安装：

```powershell
adb install -r mastercard-currency-1.0.0.apk
```

### 签名

`keystore/mcfx-release.jks`（别名 `mcfx`，口令 `mcfx123456`）由 `keytool` 生成，仅用于本工程。
可用 Gradle 属性覆盖：`-PMCFX_STORE_PASSWORD=` / `-PMCFX_KEY_PASSWORD=` / `-PMCFX_KEY_ALIAS=`。

---

## 实现原理

```
用户点「查询汇率」
   └─ WebViewFetcher（必须用真浏览器上下文，原因见下）
        ├─ 已挂载 + 全屏尺寸的 WebView 加载换算页
        ├─ 自动点掉 OneTrust Cookie 同意弹窗
        ├─ onPageFinished 后在页面上下文执行 JS：
        │     fetch(`/marketingservices/public/mccom-services/currency-conversions/conversion-rates?…`)
        │     → 同源，无 CORS，携带页面 Cookie 与 _abck 反爬 Cookie
        ├─ 命中后经 addJavascriptInterface 把原始 JSON 交回 Kotlin
   └─ RateParser 解析 data.{conversionRate, crdhldBillAmt, fxDate}
   └─ RateMath 计算基础汇率、手续费、合计（BigDecimal，HALF_UP）
```

### 换算是怎么定位到正确接口的

1. 万事达换算页把后台地址写在隐藏 input 里：

   ```html
   <input id="currencyListUrl"       data-cmp-url="/marketingservices/public/mccom-services/currency-conversions/currencies"/>
   <input id="currencyConversionUrl" data-cmp-url="/marketingservices/public/mccom-services/currency-conversions/conversion-rates"/>
   ```

2. 该页面的前端源码（`clientlib-site…js` 里的 `_currency-converter.js`）拼装请求的方式是：

   ```js
   const requestData = new URLSearchParams({
     exchange_date: transactionDateValue,            // 不选日期时传 "0000-00-00" = 最新
     transaction_currency: state.fromCurrency,
     cardholder_billing_currency: state.toCurrency,
     bank_fee: state.bankFee,
     transaction_amount: parseFloat(state.amount),
   });
   const convertedData = await fetchData(`${config.conversionUrl}?${requestData}`);
   ```

   **参数名是 snake_case**。任何别的写法（包括开发者文档里的 `transCurr/crdhldBillCurr/...`）
   都会被接口以 `400 {"data":{"errorMessage":"… is required"}}` 拒绝，而且它只会随机报出其中一个缺失项。

3. 线上真实返回：

   ```json
   {"data":{"bankFee":"2","conversionRate":"0.0438524","crdhldBillAmt":"438.5240000",
            "crdhldBillCurr":"CNY","fxDate":"2026-09-18","transAmt":"10000","transCurr":"JPY"}}
   ```

### 为什么必须用 WebView，不能用普通 HTTP

- 万事达边缘跑 Akamai Bot Manager。实测：同一个 IP 下 `favicon.ico` 返回 **200**，
  而所有 HTML 文档返回 **403** —— 拦截基于**客户端指纹**，不是网络。
- `.../currencies` 用普通客户端（带上完整浏览器请求头）可以 200；但 `.../conversion-rates`
  在拿到页面的 `_abck` / `bm_sz` / `bm_sv` Cookie 之前一律 **403**。
- Android WebView 默认 UA 带 `; wv` 与 `Version/4.0`，且**未挂载的 WebView 视口尺寸为 0**，
  两者都是明显的机器人特征 → 页面直接被 403。
  因此 `WebViewFetcher` 做了：清洗 UA、接受第三方 Cookie、**必须挂载并全屏布局**、不拦截跨域导航、
  页面加载后等待 SPA 与风控脚本就绪。

### 手续费口径

万事达返回的 `conversionRate` **已包含** `bank_fee`。上面的实时数据可验证：
`JPY→CNY, bank_fee=2` 返回 `0.0438524`，除回 1.02 得 **0.042993** 的基础汇率。
因此当手续费 > 0 时，界面额外给出「不含手续费汇率」。

### 关于「中间市场汇率」

万事达结算汇率含点差，**不等于**中间市场汇率，因此界面标题使用「万事达结算汇率」。

---

## 诊断与排障

- 底部「诊断信息」：列出每个候选 URL、HTTP 状态码、命中层级、页面标题、`_abck` 状态与原始响应。
- `adb logcat -s MCFX`：每次查询都会打印全部尝试记录，含接口返回体。
- 长按标题 5 次打开**开发者面板**：粘贴任意万事达 JSON 离线验证全链路。

---

## 自定义

- **配色**：`ui/Theme.kt`（默认为万事达红/橙 `#EB001B` / `#F79E1B` / `#FF5F00`）。
- **货币数据集**：`app/src/main/assets/currencies.json`（152 种；字段 `code/cc/nameZh/nameEn/countryZh/countryEn/symbol/aliases`）。
  旗帜为打包的圆形国旗 PNG：`assets/flags/<cc>.png`（152 张，来自 MIT 许可的 circle-flags，64×64）。
- **接口与参数**：`logic/MastercardEndpoints.kt`。

---

## 目录结构

```
app/src/main/java/com/vibecoding/mcfx/
├─ MainActivity.kt
├─ data/      Currency, CurrencyRepository, RateModels
├─ logic/     MastercardEndpoints, RateMath, RateParser, RateCache, CurrencySearch
├─ net/       RateFetcher, WebViewFetcher, HttpFetcher, RateEngine, MockFetcher
└─ ui/        Theme, Components, ConverterScreen, CurrencyPickerSheet,
              DiagnosticsSheet, DevPanel, ConverterViewModel
app/src/test/java/com/vibecoding/mcfx/   单元测试（汇率/手续费/搜索/URL/解析/USER-AGENT）
docs/                                    验证截图
```

### 关于界面

`ConverterScreen` 把 WebView 作为**常驻首个子节点全屏挂载**（这样页面才有真实视口），
应用 UI 覆盖其上；查询期间隐藏 UI 并显示加载条，此时可以直接看到万事达页面正在载入。

---

## 免责声明

万事达（Mastercard）名称与双圆标识为其注册商标，此处仅按使用要求用于个人自用工具。
汇率数据仅供参考，最终以发卡行入账金额为准。
