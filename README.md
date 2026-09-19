# 万事达汇率转换

[![Android CI](https://github.com/realMisakaMikoto/mastercard-currency/actions/workflows/android.yml/badge.svg)](https://github.com/realMisakaMikoto/mastercard-currency/actions/workflows/android.yml)

> ⚠️ **自用项目 / Personal use only** —— 个人自用的小工具，非官方、非商业、无支持承诺。
> 万事达标识仅限个人自用，详见 [NOTICE.md](NOTICE.md)。

原生 Android 汇率查询工具：在真实 WebView 中打开万事达官方换算页，
并从该页面上下文发起同源请求，读取结算汇率。

| 实测 | 结果 |
|---|---|
| USD → CNY 10,000 | `1 USD = 6.6991 CNY` → 66,991.00 CNY |
| JPY → CNY 10,000（手续费 2%） | `1 JPY = 0.043852 CNY` → 438.52 CNY |
| 冷启动首次查询 / 换币种查询 | 1.7 秒 / 1.1 秒 |

## 功能

| 能力 | 说明 |
|---|---|
| 货币选择 | 下拉 + 搜索：货币代码 / 货币名称 / 国家名称（中英文 + 拼音别名，152 种） |
| 汇率日期 | 可不填；不填取「最新已发布汇率」。**填了则只向过去回退，最多 7 天**，绝不回退到当前最新汇率；窗口内无数据会明确报错 |
| 银行手续费 | 可空，默认 0，按百分比 |
| 金额精度 | **不取整**：换算结果直接给出精确乘积，完整显示小数位（只去掉尾随零）；汇率同样按接口返回的完整精度显示，不打折 |
| 金额联动 | 改金额即时换算，不发新请求；未改动金额时直接采用万事达返回的金额 |
| 结果清空 | 币种 / 手续费 / 日期任一变更即清空结果 |
| 并发 | 同一时刻只允许一个查询；进行中会禁用查询与互换按钮，避免结果串台 |
| 加载提示 | 建立会话时全屏提示；复用会话取汇率时只在输入框位置内联提示 |
| 汇率缓存 | 无，每次查询都实时请求 |
| 诊断 | 界面内可查看候选 URL、状态码、原始响应；`adb logcat -s MCFX` 查看全部请求记录 |

## 安装

```powershell
adb install -r mastercard-currency-1.5.2.apk
```

Android 8.0（API 26）及以上。若装过其他签名的版本，需先卸载。

## 取数是怎么做的

1. 一个**已挂载、全屏**的 WebView 打开官网换算页，并自动点掉 Cookie 同意弹窗
2. 在页面上下文里执行 `fetch`，调用官网自己的后台换算服务（同源，无 CORS）
3. 解析返回的 `data.conversionRate` / `crdhldBillAmt` / `fxDate`

接口路径来自页面里的隐藏 input，参数名来自官网自身前端源码 `_currency-converter.js`
（是 **snake_case**）：

```
/marketingservices/public/mccom-services/currency-conversions/conversion-rates
  ?exchange_date=0000-00-00&transaction_currency=USD
  &cardholder_billing_currency=CNY&bank_fee=0&transaction_amount=10000
```

### 为什么必须用 WebView

官网边缘跑 Akamai Bot Manager。实测同一 IP 下 `favicon.ico` 返回 200、所有 HTML 返回 403，
即拦截看的是**客户端指纹**而非网络；并且 `conversion-rates` 在拿到页面的 `_abck` 等 Cookie 前一律 403。

而 Android WebView 默认 UA 带 `; wv`、未挂载时视口为 0，都是明显的机器人特征。
所以这里做了：清洗 UA、接受第三方 Cookie、**必须挂载并全屏布局**、只允许主框架停留在万事达域名下。
页面加载后会话会被复用（启动预热 + 页面复用），只有冷启动才真正加载一次。

原生 HTTP 不参与正式查询：它没有页面的 Akamai Cookie，必然 403，只会让用户多等一串超时。
该能力保留为开发者面板里的「原生 HTTP 探测」。

### 口径说明

万事达返回的 `conversionRate` **已包含** `bank_fee`，因此界面额外给出反推的「不含手续费汇率」。
另外结算汇率含点差，**不等于中间市场汇率**。

## 构建

JDK 17 + Android SDK（platform 37）。

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:testDebugUnitTest   # 单元测试
.\gradlew.bat :app:assembleDebug       # debug APK
.\gradlew.bat :app:assembleRelease     # release APK
```

签名密钥不在仓库中（见 `.gitignore`），所以 clone 后 `assembleRelease` 产出的是**未签名** APK，
`assembleDebug` 不受影响。

想用自己的密钥签名，**不要**把口令写进命令行（会留在 shell 历史里）或提交到仓库；
放到本机 `~/.gradle/gradle.properties`：

```properties
MCFX_STORE_FILE=path/to/your.jks
MCFX_STORE_PASSWORD=...
MCFX_KEY_ALIAS=...
MCFX_KEY_PASSWORD=...
```

CI（GitHub Actions）只跑单元测试和 debug 构建，**不使用**任何 release 签名材料。

## 结构

```
app/src/main/java/com/vibecoding/mcfx/
├─ data/    Currency, CurrencyRepository, RateModels
├─ logic/   MastercardEndpoints, RateMath, RateParser, CurrencySearch, QueryGate
├─ net/     WebViewFetcher, HttpFetcher, RateEngine
└─ ui/      ConverterScreen, CurrencyPickerSheet, DiagnosticsSheet, Components, Theme
app/src/main/assets/   currencies.json（152 种货币 + ISO 4217 小数位）、flags/、加载动画
app/src/test/          单元测试（汇率 / 小数位 / 日期 / 搜索 / URL / 解析 / 并发门禁）
```

界面与动效参考 Wise 的换算页；国旗为 MIT 许可的 [circle-flags](https://github.com/HatScripts/circle-flags) 渲染切片。

## 已知限制

- 依赖万事达官网的页面结构与 Akamai 会话，官网改版后可能需要适配。
- 本工具不是万事达官方客户端，未使用任何商户凭证。
- 汇率仅供参考，最终以发卡行入账金额为准。

## 声明

见 [NOTICE.md](NOTICE.md)。
