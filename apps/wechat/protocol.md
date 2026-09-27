# 登录、消息与支付网络协议

版本边界：本章 Java 请求/响应、协议 JNI 和 Hybrid 封包证据来自微信 8.0.68 / versionCode 3003；支付 ELF、WCDB 封装和数据库证据来自 8.0.78 / versionCode 3180。URI、funcId、网络 type 和 Protobuf 字段只按各自版本的静态证据陈述。

## 1. 通用封包模型

已验证的调用链：

```text
Java Protobuf request
  -> MMProtocalJni.genSignature(uin, signingKey, protobuf)
  -> MMProtocalJni.pack(...) / packHybrid(...) / packHybridEcdh(...)
  -> Mars longlink/shortlink, MMTLS2 or QUIC transport
  -> MMProtocalJni.unpack(...)
  -> Java Protobuf response
```

证据：

- `evidence/java/mmprotocal-jni.java:21`：`genSignature`。
- `evidence/java/mmprotocal-jni.java:23`：`generateECKey`。
- `evidence/java/mmprotocal-jni.java:27`：通用 `pack`。
- `evidence/java/mmprotocal-jni.java:31`、`:33`：RSA/AES Hybrid 与 Hybrid ECDH。
- `evidence/java/mmprotocal-jni.java:47`：`unpack`。

因此，直接请求 `/cgi-bin/micromsg-bin/*` 不足以重放；签名、session key、cookie/设备材料、UIN、funcId、RSA/ECDH 和路由信息属于请求封包的一部分。

## 2. ManualAuth / AutoAuth

### ManualAuth

- URI：安全模式 `/cgi-bin/micromsg-bin/secmanualauth`；兼容模式 `/cgi-bin/micromsg-bin/manualauth`。
- funcId：安全模式 252；兼容模式 701。
- 请求根：认证数据段 + 设备/环境段。
- 请求先写 BaseRequest，再填设备、SoftType、ClientSeqID、签名、DeviceName、DeviceType、Language、TimeZone、渠道/版本和包名。
- `MMProtocalJni.generateECKey(713, ...)` 生成临时 EC 公私钥，公钥进入认证 Protobuf。

证据位置：

- `evidence/java/manualauth-request.java:25`：URI。
- `evidence/java/manualauth-request.java:30`：funcId。
- `evidence/java/manualauth-request.java:54`：BaseRequest。
- `evidence/java/manualauth-request.java:123`：EC key 生成。
- `evidence/java/manualauth-request.java:158`：设备/环境字段日志结构。

### AutoAuth

- URI：`/cgi-bin/micromsg-bin/secautoauth` 或 `/cgi-bin/micromsg-bin/autoauth`。
- funcId：安全模式 763；兼容模式 702。
- 请求依赖持久化 UIN、session/autoauth key、cookie、设备身份和签名材料，并同样生成 EC key。

证据位置：`evidence/java/autoauth-request.java:25`、`:27`、`:48`、`:115`。

### 认证响应与混合加密

ManualAuth/AutoAuth 响应解析为同一认证响应结构。客户端会拒绝“返回码为 0，但 UIN、认证段或 session 无效”的响应。

封包存在两条路径：

- `packHybrid`：RSA/AES 混合封包。
- `packHybridEcdh`：先 `HybridEcdhEncrypt`，再生成外层包。

证据位置：`evidence/java/hybrid-pack.java:33`、`:48`。

## 3. 消息发送与同步

### NewSendMsg

| 项目 | 值 |
| --- | --- |
| URI | `/cgi-bin/micromsg-bin/newsendmsg` |
| 网络 type | 522 |
| request cmdId | 237 |
| response cmdId | 1000000237 |

请求根：`Count` + repeated `Item`。每项包含：

| 字段 | 含义 |
| --- | --- |
| `ToUserName` | 目标会话 |
| `Content` | 消息内容 |
| `Type` | 消息类型 |
| `CreateTime` | 秒级创建时间 |
| `ClientMsgId` | 本地幂等/去重 ID |
| `MsgSource` | 消息来源扩展 |
| `SendMsgTicket` | 可选发送票据 |

响应逐项包含 `Ret`、`ToUserName`、`MsgId`、`ClientMsgId`、`CreateTime`、`ServerTime`、`Type`、`NewMsgId` 和 `MsgSource`。

证据位置：

- `evidence/java/newsendmsg-network.java:292`：网络 type 522。
- `evidence/java/newsendmsg-network.java:251-278`：请求项组装和 MsgSource。
- `evidence/java/newsendmsg-request.java:76-77`：`Count` / `List`。
- `evidence/java/newsendmsg-item.java:130-135`：请求项字段。
- `evidence/java/newsendmsg-response.java:103-105`：响应根字段。
- `evidence/java/newsendmsg-response-item.java:130-138`：响应项字段。

### NewSync / NewInit

- `newsync`：type 138，URI `/cgi-bin/micromsg-bin/newsync`。
- `newinit`：type 139，URI `/cgi-bin/micromsg-bin/newinit`。

证据位置：`evidence/java/newsync-network.java:90-96`、`evidence/java/newinit-network.java:26-32`。

## 4. 支付链路

### 传输和认证边界

`libwechatpaynetwork.so` 的静态字符串/符号证明：

- PayMars `AppManager.GetDeviceInfo` 与 `AccountManager.UpdateDeviceInfo`。
- `JniUpdateDeviceInfo` 写入 device ID/device type。
- device ID 规范长度为 16 字节；日志明确检查长度。
- MMTLS 握手支持 serialized client/access/refresh PSK。
- refresh PSK 使用 AES-GCM 封装。
- `mmcrypto::SignatureChecker::Verify` / `VerifyV1` 配合 RootCert 校验证书签名。
- `mmpack` 输出包含 cert version、压缩算法、加密算法、route info、group key、signature 和 sequence id。

因此支付请求的认证至少包含：连接层 MMTLS/PSK/session、请求签名校验、设备信息和业务 token；不能把 CGI 名称本身视为完整认证。

### CGI 路径

静态 function-list 映射的代表性支付 CGI：

| 阶段 | CGI |
| --- | --- |
| 身份/令牌 | `getusertoken`, `getpaypwdtoken`, `offlinegettoken`, `getcardserial` |
| 预支付 | `genprepay`, `pay-geta8key`, `checkpayjsapi` |
| App/JS/小程序授权 | `payauthapp`, `payauthnative`, `jsapiauthen`, `tinyappauthen` |
| 实际支付 | `jsapipay`, `tinyapppay`, `scanqrcodepay`, `h5pay`, `offlinepayconfirm`, `f2fplaceorder` |
| 查询/取消 | `payorderquery`, `offlinequeryorder`, `cancelpay`, `cancelqrpay` |
| 绑定/验证 | `verifybind`, `verifyreg`, `verifysms`, `balancepaybindauthen`, `balancepaybindverify` |
| 证书 | `gendigitalcert`, `deletedigitalcert` |
| 转账 | `transferlogin`, `transferscanqrcode`, `transfersendcancelf2f` |
| 风控 | `riskaggrverifysign` |

该 function-list 同时给出 long/short 域名族和 `/cgi-bin/micromsg-bin/` 前缀。它证明客户端路由和 CGI 能力，不包含真实账户请求。

### 支付数据库

- `WalletPay.db`、`WalletCoreDB-2.db`、`SecData.db` 当前是无表的单页 SQLite。
- `wxpay_kit/.../lite_main.db` 保存支付 LiteApp 包/鉴权/配置 Schema，但当前实例为空。
- `@pay.db` KV Schema 为 `storage(key,value,access_time,create_time,modify_time,expiration_period)` 和 `size(name,size)`；不公开 key/value。

证据位置见 [schema.md](schema.md)。
