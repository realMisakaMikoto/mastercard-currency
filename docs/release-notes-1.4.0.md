## ⚠️ 自用版本 / Personal use only

这是**个人自用**的构建，不是面向公众发布的产品：没有官方支持，没有可用性承诺，
万事达官网改版后它就可能失效。

- **Mastercard / 万事达**名称与双圆标识为 Mastercard International Incorporated 的商标。
  本项目对上述标识的使用**仅限个人学习与自用**，不代表任何授权、合作或背书。详见 NOTICE.md。
- 汇率含点差，**不等于中间市场汇率**；仅供参考，最终以发卡行入账金额为准。
- 本工具**不是**万事达官方 API 客户端。数据来自官网换算页的公开接口，未申请任何商户凭证。
- 不采集、不上传任何数据；请求由设备直接发往 `www.mastercard.com`。

---

## 安装

```powershell
adb install -r mastercard-currency-1.4.0.apk
```

- 系统要求：Android 8.0（API 26）及以上
- 若已装过其他签名的版本，需先卸载
- 本 APK 由**本地私钥签名**，私钥不在仓库中 —— 因此其他人无法签出能覆盖升级的更新包（这是预期行为）

## 本版功能

| 能力 | 说明 |
|---|---|
| 汇率来源 | 万事达官网换算器后台接口（实时读取） |
| 取数方式 | 在真实 WebView 中打开官方换算页，并从该页面上下文发起同源请求 |
| 货币选择 | 下拉 + 搜索，支持**货币代码 / 货币名称 / 国家名称**（中英文 + 拼音别名，152 种） |
| 汇率日期 | 可不填；不填时按官网口径取「最新已发布汇率」，填了未发布会自动回退并标注 |
| 银行手续费 | 可空，默认 0，按百分比（与官网口径一致） |
| 金额联动 | 改动金额即时换算（像 Google 换算器），不发新请求 |
| 结果清空 | 币种 / 手续费 / 日期任一变更即清空结果，无需点查询 |
| 加载提示 | 建会话用全屏提示；复用会话取汇率用输入框内联提示 |
| 汇率缓存 | **不做缓存**，每次查询都实时请求 |
| 诊断 | 界面内可查看每个候选 URL、HTTP 状态码、原始响应；`adb logcat -s MCFX` 看全部请求记录 |

## 实测

| 场景 | 结果 |
|---|---|
| USD → CNY 10,000 | `1 USD = 6.6991 CNY` → 66,991.00 CNY |
| JPY → CNY 10,000（手续费 2%） | `1 JPY = 0.043852 CNY` → 438.52 CNY |
| 冷安装首次查询 | 1.7 秒（页面已在启动时预热） |
| 换币种查询 | 1.1 秒（复用已加载页面，不重新取 Cookie） |

## 构建

需要 JDK 17 与 Android SDK（platform 37）。

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleRelease
```

签名密钥不在仓库中，因此 clone 后 `assembleRelease` 产出的是**未签名** APK（装不上），
`assembleDebug` 不受影响。要用自己的密钥签名，把 keystore 放到 `keystore/mcfx-release.jks`
或通过 `-PMCFX_STORE_PASSWORD=` 等属性指定。
