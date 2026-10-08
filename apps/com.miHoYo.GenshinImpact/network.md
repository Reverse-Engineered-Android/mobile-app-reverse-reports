# 网络交互与协议格式

## 1. 调查边界

全部结论来自 APK 静态解包、JADX 二次反编译、ELF 符号和 AArch64
反汇编。没有 DNS、TLS、HTTP、UDP 或游戏服务器交互；因此本文件只说明
客户端构造出的格式，不声称服务端接受这些格式。

## 2. 控制面

### 2.1 Host 与接口族

`PorteOSInfo` 的初始化在 `PorteOSInfo.java:416` 附近选择 SG/US/EU host，
`apiUrlDefault` 在 `PorteOSInfo.java:466-476` 生成，US/EU/静态配置和
上报 host 在 `:477-504` 选择。`server_env_config.json` 提供公开的
`hk4e-sdk-os`、`hk4e-sdk-os-eu`、`hk4e-sdk-os-us`、
`sg-public-api.hoyoverse.com`、`sg-public-data-api.hoyoverse.com`、
`hk4e-sdk-os-static.hoyoverse.com`、`webstatic.hoyoverse.com`、
`log-upload-os.hoyoverse.com` 和活动/CDN host。

第一方 Retrofit 注解共 39 个，主要路径如下（相对路径由 Retrofit 拼接）：

| 方法 | 路径 | 文件 |
| --- | --- | --- |
| POST | `account/ma-passport/api/appLoginByPassword` | `PassportApiService.java:33`、`SignInApiService.java:27` |
| POST | `account/ma-passport/api/appLoginByAuthTicket` | `PassportApiService.java:29` |
| POST | `account/ma-passport/api/appLoginByThirdParty` | `PassportApiService.java:37` |
| POST | `account/ma-passport/api/logout` | `SignInApiService.java:31` |
| POST | `account/ma-passport/token/verifySToken` | `TokenApiService.java:43` |
| POST | `account/ma-passport/token/verifyCookieToken` | `TokenApiService.java:35` |
| POST | `account/ma-passport/token/verifyLToken` | `TokenApiService.java:39` |
| POST | `account/ma-passport/token/getBySToken` | `TokenApiService.java:31` |
| POST | `account/ma-passport/token/getByGameToken` | `TokenApiService.java:27` |
| POST | `account/ma-verifier/api/createActionTicket` | `SignUpApiService.java:23` |
| POST | `account/ma-verifier/api/verifyActionTicket` | `SignUpApiService.java:35` |
| GET | `account/ma-verifier/api/age-gate/user/loadTicket` | `VerifierApi.java:42` |
| POST | `common/aigis/api/checkSmartCaptcha` | `RiskVerifyService.java:20` |
| POST | `common/aigis/api/createBySmartCaptchaTicket` | `RiskVerifyService.java:24` |
| POST | `/loginsdk/dataUpload` | `ReportApiService.java:14` |
| GET | `/device-fp/api/getExtList` | `FingerprintService.java:41` |
| POST | `/device-fp/api/getFp` | `FingerprintService.java:125` |

### 2.2 请求构造

`RequestUtils.java:81-93` 将参数交给 Gson，媒体类型为
`application/json; charset=utf-8`。`createHeaders` 在
`RequestUtils.java:115-137` 先复制 `PorteOSInfo.getRequestCommonHeader()`，
再按非空条件加入 `x-rpc-verify`、`x-rpc-aigis`、VN real-name 和
age-gate ticket，最后合并调用方的 extra headers。

`ComboNetClient.java:41-44` 声明：

```text
x-rpc-app_id
X-Rpc-Language
x-rpc-package_name
x-rpc-risky
```

`ComboNetClient` 的 fullCommonHeader/getHttpHeader 将设备、系统、版本、
渠道和应用字段加入请求；`x-rpc-risky` 是可由登录/风控调用方提供的风险
提示头，而不是一个静态开关。

### 2.3 `DS`

`RequestUtils.java:61-79` 的格式是：

```text
t,r,MD5(salt=<static>&t=<unix_seconds>&r=<6_random>&b=<exact Gson body>&q=)
```

`t` 是 Unix 秒；`r` 是 6 个 `[A-Za-z0-9]` 字符；`b` 是与 body 相同的
Gson 字符串；`q` 为空。静态盐在报告中省略。该签名是请求完整性/重放
挑战的一部分，不替代 TLS，也不构成服务端授权凭据。

### 2.4 Cookie

`RequestUtils.java:154-191` 按 `Token.SToken`、`Token.CToken`、
`Token.LToken` 和 `isV2` 选择 cookie 名与账号字段：

```text
stoken=<token>; st_uid|mid=<account>
cookie_token|cookie_token_v2=<token>; account_id|account_mid_v2=<account>
ltoken_v1|ltoken_v2=<token>; lt_uid|lt_mid_v2=<account>
```

token 字符串本身不进入报告。

## 3. 游戏实时面

### 3.1 传输层

`libyuanshen.so` 导出并可定位到：

| 符号 | VA |
| --- | --- |
| `kcp_client_create` | `0x5d7dd78` |
| `kcp_client_connect` | `0x5d7de00` |
| `kcp_packet_create` | `0x5d7e04c` |
| `kcp_client_send_packet` | `0x5d7e09c` |
| `kcp_client_reconnect` | `0x5d7e018` |
| `kcp_client_network_thread` | `0x5d7e0fc` |

`kcp_client_connect` 根据 IPv6 标志写入 sockaddr，IP 模式使用
`AF_INET`、网络序端口和 16 字节地址；IPv6 分支写入 `AF_INET6` 与
16 字节地址。导入符号包含 `socket/sendto/recvfrom/connect/getaddrinfo`、
`poll/epoll_ctl/epoll_wait`，字符串包含 `do_recv_udp_packet_in_loop`、
`handle_udp_packet`、`ikcp_send/ikcp_recv` 和 reconnect 日志。

KCP 导出证明客户端具备 UDP 可靠传输实现，但静态调用图没有给出
“KCP send/recv 直接调用下述 record helper”的调用边；本报告因此把
KCP 与 TLS/DTLS record 层作为两个已确认组件，不臆断二者在生产会话中的
固定串联顺序。

### 3.2 Record 头与认证加密

record 层来自内嵌 mbedTLS。接收侧 `0x5d001a4-0x5d001bc` 按上下文标志
选择完整 record 头长度 `5` 或 `13`：

```text
TLS 头:  content_type(1) + version(2) + length(2)
DTLS 头: content_type(1) + version(2) + epoch(2) + sequence(6) + length(2)
```

`0x5d0032c-0x5d00344` 分别读取首字节 content type 和由 `in_len`
指针指向的大端长度；`0x5d0039c-0x5d003a8` 对 `content_type & ~3`
执行 `0x14` 分支，覆盖 ChangeCipherSpec、Alert、Handshake、
Application Data。发送与接收路径都把 record 明文长度限制为
`0x4000`（`0x5cfe76c`、`0x5cff5cc`、`0x5d01278`）。

AAD 是 13 字节的 record 元数据：发送侧 `0x5cfea48-0x5cfeacc` 构造
版本/epoch/sequence/length 等字段并传入 `x6=0xd`，接收侧
`0x5d00a10-0x5d00a84` 对称构造。`0x5cfebb8` 调用认证加密分派
`0x4a87400`，`0x5d00b28` 调用认证解密分派 `0x4a8750c`。分派读取
`cipher_info->mode`：

| mode | 实现 | 算法与结构 |
| --- | --- | --- |
| `6` | encrypt `0x4be4e30`，decrypt `0x4be4e30` + tag/finish | AES-GCM；`0x4be507c` 是 4-bit 表驱动的 128-bit GHASH，`0x4be51a0`/`0x4be5334` 完成认证状态与 tag |
| `8` | `0x4a59228` | AES-CCM；nonce 长度限制 `7..13`，tag 长度必须为偶数 `4..16`，构造 CCM flags/counter 后执行 CBC-MAC 与 CTR |
| 其他 | encrypt 返回 `-0x6080`，decrypt 返回 `-0x6080` | 不支持的 mode 拒绝 |

两种 mode 共用 96-bit nonce：session key derivation 将 12 字节 nonce
拆为 4 字节固定部分与 8 字节显式部分；`0x5cfeb90-0x5cfebb8` 和
`0x5d00afc-0x5d00b28` 把 `iv_len`、AAD、输入、输出与 tag 长度一起传入。
tag 长度为 16 字节，启用 short-tag 的 CCM suite 为 8 字节；解密侧
`0x4a87614-0x4a8764c` 逐字节累加 XOR 做常量时间比较，失败时清零输出。

发送与接收日志分别引用 `before encrypt: output payload`、
`mbedtls_cipher_auth_encrypt`、`after encrypt: tag`、
`mbedtls_cipher_auth_decrypt` 和 `input payload after decrypt`。
静态证据因此闭合为“record 头 + 8 字节显式 IV + 密文 + AEAD tag”，
算法是 AES-GCM 或 AES-CCM，而不是未知 XOR。业务 payload 的 protobuf
schema、命令号映射和实际服务器选择的 suite 需要握手/业务消息才能确定，
包内没有可读 `.proto`，因此不伪造命令表。

### 3.3 Protobuf 与状态机边界

字符串和 runtime 中存在 `google/protobuf/descriptor.proto`、
`report/sec_channel_packet.proto` 等 descriptor；`libyuanshen.so` 含
`protodesc_cold` 段。没有公开业务 `.proto` 文件或命令号到消息类型的
可读映射，静态调查只确认 protobuf runtime 和实时通道存在。

## 4. 静态上传/下载 URL 分类

`server_env_config.json` 的 URL 可归为：

- 登录/账号：`account/ma-passport`、`mdk/shield`、`combo/granter`；
- 风控/年龄：`common/aigis`、`ma-verifier/api/age-gate`、
  `guard/api/ping`；
- 上报：`/loginsdk/dataUpload`、`/log/sdk/upload`、
  `h5log/log/batch`、Kibana/telemetry；
- 下载：`getFont`、语言 JSON、`getExtList`、ABTest、静态配置、热修、
  活动和资源 CDN。

所有 URL 均为客户端静态配置；本次没有验证其可达性。
