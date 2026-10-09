# 原神 Android 7.1.0 静态逆向研究报告

## 1. 样本与方法

样本 APKM SHA-256 为
`29d51069aec72cb75e363caa713333aba145b379a39583c7de429715a42c5928`；
`base.apk` 为
`43e1d64940ab767c078330fb5ecb4155a61501a5f20a1828f9306e6f4769583c`；
ARM64 split 为
`4c51b596a746c251ca6483616901a20cb872bf2bc8c3b40db34df4f36b4a5bd5`；
AssetBundles split 为
`41b58d9504c9a497ffefabbb89a7829352758bcb65f57266b671531dba35338a`。
APKM、base、两个 split 的 ZIP 完整性通过；签名证书 SHA-256 为
`68842530E0C7F9D42B021E148BEE247FA741A063BE39440CC8DAECC9A05F669C`。

JADX 使用 `--show-bad-code --deobf` 完成 14,357 个 Java 文件的结构恢复；
第一方 `com.miHoYo`/`com.mihoyo`/`com.hoyoverse`/`com.astrolabe`/
`com.combosdk` 关键路径可读，残留告警来自结构恢复而不是未知算法。
native 使用 ELF 动态符号、AArch64 反汇编、字符串交叉引用和 Java JNI
调用链闭合；IL2CPP 还核对了可执行段、FDE 函数边界、私有元数据容器、
BuildSettings 场景和 GC/线程状态桥。调查全程静态只读，没有发包、
登录、抓包、绕过或游戏运行。

## 2. 最终结论

### 2.1 网络与协议

客户端存在两条不同链路：

1. **HTTPS JSON 控制面**。Retrofit 接口覆盖登录、token 校验/交换、注册、
   action ticket、age gate、Aigis/SmartCaptcha、ABTest、设备指纹和
   `/loginsdk/dataUpload`。请求体由 Gson 生成
   `application/json; charset=utf-8`，`DS`、cookie 和公共 header 在
   `RequestUtils.createHeaders` 组装。
2. **游戏实时数据面**。`libyuanshen.so` 同时导出 UDP/KCP 入口并包含
   mbedTLS TLS/DTLS record 层。record 头为 5 或 13 字节，明文上限
   `0x4000`，认证加密/解密分派位于 `0x4a87400`/`0x4a8750c`，AAD 为
   13 字节，nonce 为 12 字节（4 字节固定 + 8 字节显式）。mode `6`
   走 `0x4be4e30` 的 AES-GCM，mode `8` 走 `0x4a59228` 的 AES-CCM，
   tag 为 16 或 8 字节。静态证据没有证明 KCP send/recv 与该 record
   helper 之间存在直接调用边。

未公开命令号到业务消息的映射；静态包中没有明文 `.proto` 业务 schema，
只有 Google protobuf runtime 和 `report/sec_channel_packet.proto` 等
报告描述。实时数据的具体游戏字段属于服务端会话内容，静态样本不提供
真实字段值。

### 2.2 认证

`RequestUtils.createSign` 的精确逻辑为：取 Unix 秒、生成 6 位
`[A-Za-z0-9]` 随机串，返回
`t,r,MD5("salt=<静态盐>&t=<秒>&r=<随机串>&b=<Gson JSON>&q=")`；
盐的字面值已在本报告脱敏。请求 body 同样由 Gson 生成，因此签名绑定
实际发送的 JSON。cookie 逻辑按 token 类型组装：

- SToken：`stoken` + `st_uid`（v1）；
- CToken：`cookie_token`/`cookie_token_v2` + `account_id`/`account_mid_v2`；
- LToken：`ltoken_v1`/`ltoken_v2` + `lt_uid`/`lt_mid_v2`。

接口族包括 `appLoginByPassword`、`appLoginByAuthTicket`、
`appLoginByThirdParty`、`logout`、`verifySToken`、`verifyCookieToken`、
`verifyLToken`、`getBySToken`、`getByGameToken`、action ticket 创建/
验证、邮箱注册和第三方绑定。登录遇到 `NeedCaptcha` 时重新进入
`SignInManager.loginByPassword`，把 Aigis header 作为下一次请求凭据。

### 2.3 上传与下载

可见的上传数据面包括：

- `ReportWorker`：批量 JSON（最多 30 条），带 `CONTENT-MD5`、
  `DATE`、`cms-signature: hmac-sha1` 和 HMAC-SHA1 Authorization；
- `BaseDataReport`：分辨率、CPU、电量、开机时长、亮度、Android ID、
  RAM、音量、字体缩放、包名、网络/运营商、API、Root、ADB、代理、
  模拟器、品牌/厂商/型号、版本；
- Device FP：Android ID、serial、board/brand/hardware/cpu、build
  tags/type/user/time、屏幕、网络、ROM/RAM、传感器、Root/debug/proxy/
  emulator/mock-location、SIM、UI mode、SD、键盘、电池和安装/更新时间；
- 归因、Firebase、遥测、H5 log、Kibana 和 SDK log。

下载面包括配置/ABTest、语言包、字体、热修、资源 CDN、活动配置、
设备指纹扩展列表和登录/支付 Web 页面。没有发现把游戏存档、聊天、好友、
支付流水或截图主动写入第三方通用云盘的静态调用链；服务端会话数据是否
上传由实时游戏服务决定，静态样本无法证明。

### 2.4 风控

风控闭合为五层：

1. 请求签名和公共 header：`DS`、`x-rpc-app_id`、`x-rpc-package_name`、
   `x-rpc-risky`；
2. 挑战：`common/aigis/api/checkSmartCaptcha`、
   `createBySmartCaptchaTicket`，返回 `AigisEntity` 后由 GeeTest4 标准
   流程生成 `x-rpc-aigis`；
3. 风险票据：`RiskVerifyEntity` 携带 `risk_ticket`、`ticket`、
   `verify_str`、`verify_type`，`verify_type=1` 为 Aigis，`=2` 为
   risk verify；
4. 年龄门/实名：`loadTicket`、`updateTicket`、`resendEmail`，字段含
   `birthday`、`parent_email`、`status`、`ticket_id`；
5. 设备环境：`XDeviceUtils.isRooted/isEmulator/hasOpenDebugMode/isProxy`、
   Astrolabe root 路径、device blacklist、device limit、Android ID/
   OAID/设备指纹及基础数据。

`XDeviceUtils.isRooted` 检查 `test-keys`、`/system/app/Superuser.apk`、
`/system/xbin/su`、`/system/bin/su`；`isEmulator` 检查
`generic/vbox/test-keys/google_sdk/Emulator/x86/Genymotion` 与
SystemProperties；`hasOpenDebugMode` 读取 `adb_enabled`；
`isProxy` 读取 HTTP/HTTPS system properties。`BaseDataReport.java:117`
把这些状态和设备字段一次组装，代码级采集面是确定的。

### 2.5 越权、提权、告知和超范围

- **本地越权面已确认**：Manifest 中 `GameStateService` exported=true
  且无 permission；`SendRequestToGame` 接受 JSON，可调用 RTT、内存模式、
  分辨率、折叠屏、渲染平均时间、播放器详情、共享内存等 10 个命令。
  `SetNotifyPlayerDetailEnable` 会调用 OEM 白名单并失败返回
  `auth_failed`，其他命令没有同等 caller 身份校验。
- **Android UID/系统权限提升未证明**：没有发现把任意文件写入系统目录、
  注入其他 UID 或调用 root 提权的可执行链。
- **隐私告知存在边界**：`ConsentStatusCache` 在 `skipConsent()` 为
  true 时把 analytics、ad user data、ad personalization、ad storage
  全部设为 granted；否则四类均 denied。设备指纹和基础字段可在不看
  Firebase consent 的路径进入 Device FP/ReportWorker，属于超范围风险，
  静态分析不能证明每次都会发往服务器。
- **可确认**：客户端有字段收集、风险状态计算、上传代码和 exported
  Binder。**不能确认**：服务端是否接受、保存、长期关联，或真实用户
  在 consent UI 中看到过哪些字段。

## 3. 结论矩阵

| 问题 | 结论 | 证据等级 |
| --- | --- | --- |
| 主要网络流程 | HTTPS JSON 控制面 + KCP/UDP 与 mbedTLS record 两个实时面组件 | 高（Java/native 符号与反汇编） |
| 协议具体格式 | JSON/Gson、DS 头、5/13 字节 TLS/DTLS record、12-byte nonce、13-byte AAD、16/8-byte AEAD tag | 高（本地代码）/业务 schema 与实际 suite 需会话 |
| 认证机制 | 密码、auth ticket、第三方、SToken/CToken/LToken 链；实时面 TLS/DTLS 握手派生 key block | 高 |
| 风控机制 | 签名、Aigis、risk ticket、年龄门、设备环境、黑名单/限制 | 高 |
| 上传下载范围 | 上述报告与配置/CDN 下载；游戏实时字段不在静态范围 | 高/服务器未知 |
| 越权 | exported `GameStateService` 无权限保护 | 高（Manifest+Binder） |
| 提权 | 未发现 Android UID/系统权限提升链 | 中（静态未发现） |
| 未经告知/超范围 | 设备环境和设备指纹采集路径明确，consent 默认拒绝但存在 skip 路径 | 高（客户端路径），服务端未观测 |
| 加密是否留盲区 | MD5、HMAC-SHA1、AES-128-OFB、ARC4、Tink AES-256-GCM、SaltSign、AES-GCM/AES-CCM 均已映射到用途、算法和调用点；密钥不公开 | 高 |
| IL2CPP 与 GUI 状态机 | `libyuanshen.so` 含 207,612,312 字节可执行 `il2cpp` 段；BuildSettings 含 6 个应用场景，Animage/Animator 并存，`GameStateService` 桥接 IL2CPP GC/线程状态 | 高（ELF、元数据、Unity 文件、JNI），私有类型/方法名表不可读 |

## 4. 最终判断

原神 7.1.0 Android 客户端的账号面是标准 HTTPS JSON + DS/cookie 链；
实时面静态上同时存在 UDP/KCP 导出和 mbedTLS TLS/DTLS AEAD record 层。
客户端具备较完整的
设备风险采集与上报代码，并把一个无权限 exported Binder 暴露给本机应用；
这是明确的本地授权缺口，但不是已证明的系统提权。隐私上存在“设备指纹
和环境状态可在 consent 默认拒绝路径下被组装”的超范围风险，是否实际
上传及服务端保存范围必须由授权服务器日志或流量验证，本次静态任务不作
该项声称。客户端状态面由 `BundleDownload`/`PSPrepare`、`Login`、
`Home`、`Level`/`Game` 场景与 Animage/Animator 状态机分层承载；具体
托管转移条件受私有 `MHY` 元数据 schema 限制，报告只给出可复现的
结构、字符串和运行时桥，不虚构转移边。
