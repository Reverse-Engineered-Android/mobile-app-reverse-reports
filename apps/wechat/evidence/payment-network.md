# 支付网络源码证据

版本边界：本页 `libwechatpaynetwork.so`、`libpaymarshelper.so` 和 `libwxpay_wechatlv.so` 证据来自微信 8.0.78 / versionCode 3180。

工件：`libwechatpaynetwork.so`，SHA-256 `7c84d1f40dad4de25ed42dac7c0ce4d6f6aacc947aea081acb9514f6be909686`。

以下位置来自该 ELF 的字符串提取文件：

| 位置 | 证据 |
| ---: | --- |
| 16-18 | `paymars::app::AppManager::GetDeviceInfo`、`AccountManager::UpdateDeviceInfo`、JNI 更新入口 |
| 25-26 | `JniUpdateDeviceInfo` 写入 device ID/device type |
| 36-45 | device ID 长度、握手/PSK 序列化、AES-GCM refresh PSK |
| 47-59 | `getcardserial` CGI、access/refresh PSK 状态 |
| 72-98 | `SignatureChecker::Verify/VerifyV1`、RootCert、签名证书校验 |
| 93 | pack 元数据：cert version、压缩/加密算法、route info、group key、signature、sequence id |
| 99 | long/short 域名 function-list 与 `/cgi-bin/micromsg-bin/` CGI 映射 |

`libpaymarshelper.so` 字符串第 16 行证明 schema 有 BaseRequest 但 JSON 无占位符时会自动注入 BaseRequest：

```text
ResolveBaseRequestFields: '%s' has BaseRequest in schema but no placeholder in JSON,
auto-injecting with scene=0
```

`libwxpay_wechatlv.so` 的源路径和符号证明该库包含支付 LiteApp 引擎、包管理、Mars 请求、WebSocket、动态配置、存储、AES-GCM/DES/RSA-PSS 等组件。

边界：这些证据证明客户端能力和静态路由，不提供真实 token、证书、签名、账户、订单或请求负载。
