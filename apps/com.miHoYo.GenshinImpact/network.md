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
| `kcp_client_create` | `0x5d7e9c8` |
| `kcp_client_connect` | `0x5d7ea50` |
| `kcp_packet_create` | `0x5d7ec9c` |
| `kcp_client_send_packet` | `0x5d7ecec` |
| `kcp_client_reconnect` | `0x5d7ec68` |
| `kcp_client_network_thread` | `0x5d7ed4c` |

`kcp_client_connect` 根据 IPv6 标志写入 sockaddr，IP 模式使用
`AF_INET`、网络序端口和 16 字节地址；IPv6 分支写入 `AF_INET6` 与
16 字节地址。导入符号包含 `socket/sendto/recvfrom/connect/getaddrinfo`、
`poll/epoll_ctl/epoll_wait`，字符串包含 `do_recv_udp_packet_in_loop`、
`handle_udp_packet`、`ikcp_send/ikcp_recv` 和 reconnect 日志。

### 3.2 帧与加密

接收入口的静态控制流在 `0x5cfe004`：

```text
frame_header = 4 bytes or 12 bytes
declared_payload_length = header[1..3] big-endian + header_size
payload_length <= 0x4000
```

发送侧 `0x5cff2bc` 写入第一字节状态、第二字节条件状态、第三字节类型，
并在另一个两字节区写入长度的高/低字节。`0x5cff39c` 打印
`before encrypt: output payload`，随后调用认证加密路径；`0x5cffbc4`
打印 `after encrypt: tag`，`0x5d01eb4` 在解密后检查
`input payload after decrypt`。认证加密调用入口是 `0x4a8802c`，
它先校验 context 和长度，再把 payload/tag 指针交给 16 字节块处理
helper（`0x4be5b74`/`0x4be5ee4`/`0x4be6078`）。

静态证据支持“帧头 + payload + 认证 tag”的 AEAD 包，而不是单纯 XOR。
帧头选择 4 或 12 字节对应加密上下文标志位；接收侧
`0x5d00000` 明确用 4/12 两个候选值做最小长度检查。业务 payload 的
protobuf schema、命令号映射、服务端 key exchange 未在包内以可读
`.proto` 出现，因此不伪造命令表。

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
