# 风控机制全量清单

## 1. 架构

```text
设备/环境采集（XDeviceUtils、DeviceUtils、CommonRequiredParams）
        |
请求签名与公共 header（DS、x-rpc-*、Cookie）
        |
SmartCaptcha/Aigis -> GeeTest4 标准流程 -> x-rpc-aigis(_v4)
        |
risk ticket / action ticket / 年龄门 ticket
        |
device blacklist / device limit / 风险码响应分支
        |
服务端判定、冻结或拒绝
```

客户端负责采集、签名、挑战与票据传递；阈值、画像留存和最终处罚在
服务端，静态样本只能证明客户端信号和本地处置路径。

## 2. 请求签名与公共 header

| 机制 | 位置 | 判定依据 |
| --- | --- | --- |
| `DS` 签名 | `RequestUtils.java:61-79` | `t,r,MD5(salt=<静态盐已脱敏>&t=<unix_seconds>&r=<6 随机字符>&b=<Gson body>&q=)` |
| 签名字符表 | `RequestUtils.java:31-58` | `t` 为 `currentTimeMillis()/1000`；`r` 从 `dictChars` 随机取 6 位 |
| 公共 header 拷贝 | `RequestUtils.java:115-137` | 先 `PorteOSInfo.getRequestCommonHeader()`，再按非空加入 Aigis/verify/实名/年龄门 |
| Cookie 组装 | `RequestUtils.java:154-191` | 按 `Token.SToken`/`CToken`/`LToken` 与 `isV2` 选择 cookie 名与账号字段 |
| SDK 公共头 | `ComboNetClient.java:210-256` | `x-rpc-app_id`、`X-Rpc-Language`、`x-rpc-package_name`、`x-rpc-risky`、版本/渠道/lifecycle |

`HeaderKey.java` 给出的风控相关头包括 `x-rpc-aigis`（`:16`）、
`x-rpc-aigis_v4`（`:31`）、`x-rpc-verify`（`:44`）、
`x-rpc-device_fp`（`:24`）、`x-rpc-age_gate`（`:9`）及其
`_br/_eu/_payload/_ticket/_time/_type` 变体。`DS` 是对请求体的
完整性/重放挑战签名，不是服务端授权凭据；它无法替代 TLS。

## 3. 挑战：SmartCaptcha、Aigis 与 GeeTest4

| 环节 | 位置 | 判定依据 |
| --- | --- | --- |
| 挑战接口 | `RiskVerifyService.java:20,24` | `POST common/aigis/api/checkSmartCaptcha`、`POST common/aigis/api/createBySmartCaptchaTicket` |
| 实体 | `AigisEntity`、`RiskVerifyEntity` | 返回 `gt`/`challenge`/`session_id` 与 `risk_ticket`/`ticket`/`verify_str`/`verify_type` |
| 版本选择 | `GeeTestUtils.java:64` | `useGeeTest4(aigisEntity) ? "4.0" : "3.0"` |
| 4.0 流程 | `GeeTestUtils.java:309-344`、`GTCaptcha4Client.java` | `GTCaptcha4Client` 回调 → `x-rpc-aigis_v4`；3.0 走 `:162` 的 `GT3GeetestUtils` |
| 入口 | `RiskManager.java:32-67` | `startGeeTestVerify` → `PorteOSNonUI.startGeeTestVerify` |
| 重入 | `SignInManager.loginByPassword` | 登录遇 `NeedCaptcha` 后带 Aigis header 重试 |

`verify_type` 取值由响应决定：`1` 走 Aigis，`2` 走 risk verify。
GeeTest SDK 内还包含 Hook 框架检测（`C1708v.java:17-50`：`Substrate`
`com.saurik.substrate`、`XposedBridge`、`edxp.jar`），属于挑战组件
自带的环境判定。

## 4. 风险票据与年龄门

- **action ticket**：`SignUpApiService.java:23,35`、`VerifierApi.java:26,54`
  负责创建/校验；`account/auth/api/getActionTicketBySToken`、
  `/account/ma-verifier/api/createAuthTicketBySToken` 由 token 换票据。
- **风险票据字段**：`ParamKey.PARAM_KEY_RISK_TICKET = "ticket"`、
  `PARAM_KEY_RISK_CHECK_DATA = "check_data"`、`PARAM_KEY_AUTH=…"authorize_key"`。
- **年龄门**：`VerifierApi.java:42,46,50` 的
  `account/ma-verifier/api/age-gate/user/loadTicket`、
  `updateTicket`、`resendEmail`；字段含 `birthday`、`parent_email`、
  `status`、`ticket_id`。年龄门不是一个客户端布尔开关，而是依赖服务端
  ticket 与 header 重新进入认证链。

## 5. 设备环境检测

`XDeviceUtils` 的精确判定：

| 方法 | 行号 | 判定依据 |
| --- | --- | --- |
| `isProxy()` | `:156-163` | `System.getProperty("http.proxyHost")` 或 `https.proxyHost` 非空返回 1 |
| `isEmulator(Context)` | `:169-210` | Build/SystemProperties 含 `generic`、`vbox`、`test-keys`、`google_sdk`、`Emulator`、`x86`、`Genymotion` |
| `isEmulatorByGoogle(Context)` | `:476-480` | `isEmulatorByGoogle` 辅助判定 |
| `isRooted(Context)` | `:450-470` | `test-keys`、`/system/app/Superuser.apk`、`/system/xbin/su`、`/system/bin/su`；结果缓存 `cachedIsRooted` |
| `hasOpenDebugMode(Context)` | `:481-485` | `Settings.Secure` 的 `adb_enabled > 0` 返回 1 |

`BaseDataReport.java:111-117` 把上述状态与设备字段一次性组装为上报 map；
这是“代码级采集面确定”，不代表每次请求必然包含全部字段。

## 6. 设备黑名单与设备上限

| 机制 | 位置 | 判定依据 |
| --- | --- | --- |
| 设备上限检查 | `DeviceLimitManager.java:76-250` | `ComboURL.checkDeviceLimit` 请求；响应头 `X-Rpc-Limit-Max` 决定绑定提示 |
| 环境开关 | `DeviceLimitManager.java:88-93` | `DeviceLimitConfig.getEnvs()` 与 `ComboConfigKeys.DISABLE_DEVICE_LIMIT` |
| 绑定设备 | `DeviceLimitManager.requestBindDevice` | 服务端 `serverId` 维度绑定；失败走 `IInvokeCallback.onFailure` |
| 黑名单实体 | `BlackDeviceEntry.java`、`LinkInnerOpenBlockDeviceConfig.java` | 登录链携带的设备黑名单配置 |
| 广告归因黑名单 | `AnalyticsBlacklistHelper.java`、`AnalyticsBlacklistResponse.java` | 归因上报的排除名单 |

黑名单内容来自服务端配置，静态样本只有容器、字段和匹配代码，没有条目。

## 7. 加密代码用途闭合

| 算法 | 位置 | 用途 |
| --- | --- | --- |
| MD5 | `CryptoExtendKt.java:30-51`、`MD5Utils.java:56-255` | `DS` 签名、`CONTENT-MD5`、白名单 MD5 比较 |
| HMAC-SHA1 | `ReportWorker.java:269-316,349-360`、`HmacSHA1Signature.java:10-54` | 上报 `Authorization` 与 `cms-signature`；HMAC 字面 key 已脱敏 |
| HMAC-SHA256 | Tink `AesCtrHmacAeadKeyManager`、`HmacKey` 等 | Google Tink 依赖自带；未发现第一方业务调用点 |
| AES-128-OFB | `CryptoUtils.java:54-69` → JNI `AESEncryptNative/AESDecryptNative` | Combo/Astrolabe 本地敏感字符串与配置；`set_mode(2)` 的实现是 OFB，固定 16-byte key，无 KDF，IV 位于 `.bss` 且初始为全零 |
| ARC4 | `CryptoUtils.java:36-47` → JNI `RC4EncryptNative/RC4DecryptNative` | 固定 32-byte key，调用 `ARC4::setKey(key,32)`，用于历史兼容路径 |
| 本地存储 AES-256-GCM | Tink `AesGcmKeyManager`、Android Keystore | keyset URI 为 `android-keystore://mhy_plat_porte_master_key`，SharedPreferences 为 `mhy_plat_porte_crypto`，keyset 名为 `mhy_plat_porte_keyset` |
| `SaltSign` | `astrolabe CryptoUtils.java:14` → `SaltSignNative` | canonical query 为 `salt=<固定32字节盐>&t=<Unix秒>&r=<6字符随机串>&b=<参数1>&q=<参数2>`，结果为 `MD5(canonical_query)` 的 32 位十六进制摘要 |
| 上报 HMAC | `ReportWorker.java:269-316,349-360` | JSON body 的 `CONTENT-MD5` 与 HMAC-SHA1 `Authorization`/`cms-signature` |
| 实时面 AES-GCM | `libyuanshen.so` `0x4a87400`/`0x4a8750c` → `0x4be4e30` | mbedTLS mode `6`；12-byte nonce、13-byte AAD、16-byte tag（short-tag suite 为 8） |
| 实时面 AES-CCM | `libyuanshen.so` `0x4a87400`/`0x4a8750c` → `0x4a59228` | mbedTLS mode `8`；nonce `7..13`、偶数 tag `4..16`、CBC-MAC + CTR |

native 侧第一方符号可逐一定位：`_ZN12combo_crypto3AES6CipherEPhS1_`、
`_ZN12combo_crypto4ARC44prgaEPKcPci`、
`Java_com_combosdk_support_base_utils_CryptoUtils_AESEncryptNative`，
`libastrolabe-crypto.so` 为同一套实现的平行副本。TLS/DTLS record key
由握手 key block 派生，本地 AES key/盐/HMAC key 按用途固定或由调用方
提供；公开报告只记录长度、URI、性质与算法，不导出任何真实密钥。

## 8. 覆盖率与边界

- 第一方风控代码可读部分中仅剩 1 个反编译残留
  （`PassportLoginManager.java:673` 的 `authLoginAfterRegister` 回调），
  该处为 UI 回调转发，其余逻辑与相邻 lambda 可读，不构成未知算法。
- 其余 13 个残留全部位于 AndroidX、Kotlin 协程、GMS、AppsFlyer、
  Tink、ZXing 等第三方库，与风控算法无关。
- **可确认**：客户端具备上述采集、签名、挑战、票据、环境判定和设备
  限制代码。**不能确认**：服务端阈值、评分模型、封禁时长、设备指纹
  留存期限，以及风险码到具体处罚的映射。
