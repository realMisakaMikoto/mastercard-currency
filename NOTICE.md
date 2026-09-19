# NOTICE — 自用项目 / Personal use only

## 这是什么

一个**个人自用**的汇率查询小工具。写成发布出来只是因为用 git 存一下方便自己换机、回滚，
不是面向公众的产品，也没有做任何产品化打磨（无多语言、无设置项、无崩溃上报、无灰度）。

**没有官方支持，没有可用性承诺。** 不保证接口格式变化后还能用 —— 万事达改版时它就会失效，
届时需要自己修。

## 商标

- **Mastercard / 万事达**、双圆标识、以及万事达换算页的任何内容，
  均为 Mastercard International Incorporated 的商标或版权资产。
- 本仓库对上述标识的使用**仅限个人学习与自用**，不代表任何授权、合作或背书。
- 除本仓库中由我编写的代码外，**不授予任何商标或素材的使用许可**。
  请勿将其用于商业用途、对外分发或任何可能造成官方关联印象的场景。

## 数据来源与准确性

- 汇率数据来自万事达官网换算页的**公开接口**，本工具只是把它读出来并做展示。
- 本工具**不是**万事达官方 API 客户端（官方接口需要 OAuth 商户凭证，本项目未申请）。
- 汇率含点差，**不等于中间市场汇率**；仅供参考，最终以发卡行入账金额为准。
- 不存储、不上传任何用户数据；所有请求都由设备直接发往 `www.mastercard.com`。

## 第三方素材

| 素材 | 来源 | 许可 |
|---|---|---|
| 圆形国旗图标 `app/src/main/assets/flags/*.png` | [HatScripts/circle-flags](https://github.com/HatScripts/circle-flags) 渲染切片 | MIT |
| 万事达 Logo 加载动画 `assets/mastercard_loader.gif` | 使用者自行提供 | 见上述商标声明 |
| 双圆矢量图标 `res/drawable/ic_mastercard_mark.xml` | 按公开比例自行绘制 | 见上述商标声明 |

## 签名密钥

`keystore/` **不在仓库中**。仓库里的 APK 由本地私钥签名；
clone 本仓库后 `assembleRelease` 会产出**未签名**的 APK，`assembleDebug` 不受影响。
