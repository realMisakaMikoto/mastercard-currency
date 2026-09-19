# 丁E��达汁E��转换

[![Android CI](https://github.com/realMisakaMikoto/mastercard-currency/actions/workflows/android.yml/badge.svg)](https://github.com/realMisakaMikoto/mastercard-currency/actions/workflows/android.yml)

> ⚠�E�E**自用项目 / Personal use only** — E个人自用皁E��工具�E�非官方、E��啁E��、无支持承诺、E> 丁E��达栁E��E��E��个人自用�E�详见E[NOTICE.md](NOTICE.md)、E
原生 Android 汁E��查询工具�E�在真宁EWebView 中打开丁E��达官方换算页�E�E并从该页面上下文发起同源请求，读取结算汁E��、E
| 实流E| 结果 |
|---|---|
| USD ↁECNY 10,000 | `1 USD = 6.6991 CNY` ↁE66,991.00 CNY |
| JPY ↁECNY 10,000�E�手续费 2%�E�E| `1 JPY = 0.043852 CNY` ↁE438.52 CNY |
| 冷启动首次查询 / 换币种查询 | 1.7 私E/ 1.1 私E|

## 功�E

| 能劁E| 说昁E|
|---|---|
| 货币E��择 | 下拉 + 搜索�E�货币代码E/ 货币名称 / 国家名称�E�中英斁E+ 拼音别名！E52 种！E|
| 汁E��日朁E| 可不填�E�不填取「最新已发币E��E��」、E*填亁E�E只向迁E��回退�E�最夁E7 天**�E�绝不回退到当前最新汁E���E�窗口冁E��数据会�E确报锁E|
| 银行手续费 | 可空�E�默认 0�E�按百刁E��E|
| 金额精度 | 按目栁E��币的 ISO 4217 小数位换算与显示�E�JPY/KRW 0 位、KWD/BHD 3 位、�E佁E2 佁E|
| 金额联动 | 改金额即时换算，不发新请求；未改动金额时直接釁E��丁E��达返回皁E��颁E|
| 结果渁E�� | 币私E/ 手续费 / 日期任一变更即渁E��结果 |
| 并叁E| 同一时刻只允许一个查询�E�进行中会禁用查询与互换按钮�E�避免结果串台 |
| 加载提示 | 建立会话时全屏提示�E�复用会话取汁E��时只在输�E桁E��置冁E��提示 |
| 汁E��缓孁E| 无�E�每次查询都实时请汁E|
| 诊断 | 界面冁E��查看候送EURL、状态码、原始响应；`adb logcat -s MCFX` 查看�E部请求记彁E|

## 安裁E
```powershell
adb install -r mastercard-currency-1.5.1.apk
```

Android 8.0�E�EPI 26�E�及以上。若裁E��E�E他签名的版本�E�需先卸载、E
## 取数是怎么做皁E
1. 一个**已挂载、�E屁E*皁EWebView 打开官网换算页�E�并自动点掁ECookie 同意弹突E2. 在页面上下文里执衁E`fetch`�E�谁E��官网�E己皁E��台换算服务�E�同源，无 CORS�E�E3. 解析返回皁E`data.conversionRate` / `crdhldBillAmt` / `fxDate`

接口路征E��自页面里的隐藏 input�E�参数名来自官网�E身前端源码E`_currency-converter.js`
�E�是 **snake_case**�E�！E
```
/marketingservices/public/mccom-services/currency-conversions/conversion-rates
  ?exchange_date=0000-00-00&transaction_currency=USD
  &cardholder_billing_currency=CNY&bank_fee=0&transaction_amount=10000
```

### 为什么忁E��用 WebView

官网边缘跁EAkamai Bot Manager。实测同一 IP 丁E`favicon.ico` 返回 200、所朁EHTML 返回 403�E�E即拦截看的是**客户端持E��**而非网络；并丁E`conversion-rates` 在拿到页面皁E`_abck` 筁ECookie 前一征E403、E
老EAndroid WebView 默认 UA 带 `; wv`、未挂载时见E��为 0�E��E是明显皁E��器人特征、E所以这里做亁E��渁E��EUA、接受第三方 Cookie、E*忁E��挂载并全屏币E��**、只允许主桁E��停留在丁E��达域名下、E页面加载后会话会被复用�E�启动颁E�� + 页面复用�E�，只有�E启动才真正加载一次、E
原生 HTTP 不参与正式查询�E�宁E��有页面皁EAkamai Cookie�E�忁E�� 403�E�只会让用户多等一串趁E��、E该能力保留为开发老E��板里的「原甁EHTTP 探测」、E
### 口征E��昁E
丁E��达返回皁E`conversionRate` **已匁E��** `bank_fee`�E�因此界面额外给�E反推皁E��不含手续费汁E��」、E另外结算汁E��含点差�E�E*不等于中间市场汁E��**、E
## 极E��

JDK 17 + Android SDK�E�Elatform 37�E�、E
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:testDebugUnitTest   # 单�E测证E.\gradlew.bat :app:assembleDebug       # debug APK
.\gradlew.bat :app:assembleRelease     # release APK
```

签名寁E��不在仓库中�E�见E`.gitignore`�E�，所以 clone 吁E`assembleRelease` 产出皁E��**未签吁E* APK�E�E`assembleDebug` 不受影响、E
想用自己皁E��E��签名！E*不要E*把口令写进命令行（会留在 shell 厁E��里）�E提交到仓库！E放到本机 `~/.gradle/gradle.properties`�E�E
```properties
MCFX_STORE_FILE=path/to/your.jks
MCFX_STORE_PASSWORD=...
MCFX_KEY_ALIAS=...
MCFX_KEY_PASSWORD=...
```

CI�E�EitHub Actions�E�只跑单允E��试和 debug 极E���E�E*不使用**任佁Erelease 签名材料、E
## 结构

```
app/src/main/java/com/vibecoding/mcfx/
├─ data/    Currency, CurrencyRepository, RateModels
├─ logic/   MastercardEndpoints, RateMath, RateParser, CurrencySearch, QueryGate
├─ net/     WebViewFetcher, HttpFetcher, RateEngine
└─ ui/      ConverterScreen, CurrencyPickerSheet, DiagnosticsSheet, Components, Theme
app/src/main/assets/   currencies.json�E�E52 种货币E+ ISO 4217 小数位）、flags/、加载动画
app/src/test/          单�E测试（汁E�� / 小数佁E/ 日朁E/ 搜索 / URL / 解极E/ 并发门禁E��E```

界面与动效参老EWise 皁E��算页�E�国旗为 MIT 许可皁E[circle-flags](https://github.com/HatScripts/circle-flags) 渲染�E牁E��E
## 已知限制

- 依赖丁E��达官网的页面结构丁EAkamai 会话，官网改版后可能需要E���E、E- 本工具不是丁E��达官方客户端�E�未使用任何商户凭证、E- 汁E��仁E��参老E��最终以发卡行�E账金额为凁E��E
## 声昁E
见E[NOTICE.md](NOTICE.md)、E