# 设备、认证与行为风控

版本边界：登录请求字段、AutoAuth、NewSendMsg/NewSync/NewInit 和 Hybrid 封包来自 8.0.68 JADX Java 源码；`cp/w0.smali`、Normsg、支付设备信息和数据库证据来自 8.0.78。下文仅在两版本都存在相同调用结构时合并结论。

## 1. 已确认进入登录/协议结构

以下字段由请求构造代码直接赋值或在协议封装接口中出现：

- UIN、SessionKey、DeviceID、ClientVersion、DeviceType、Scene。
- SoftType、ClientSeqID、Signature、DeviceName、DeviceType。
- Language、TimeZone、渠道、客户端版本、AndroidPackageName。
- 登录标识/认证上下文、ECDH 公钥、CGI 校验公钥。
- 厂商、型号、Android release/incremental/display、APK 签名证书哈希。

主要代码位置：

- `evidence/java/manualauth-request.java:54-158`：BaseRequest 与设备/环境字段。
- `evidence/java/autoauth-request.java:48-115`：AutoAuth BaseRequest 与 EC key。
- `evidence/java/mmprotocal-jni.java:21-47`：签名、EC key、封包和解包 ABI。
- `evidence/java/hybrid-pack.java:33-48`：DeviceID、UIN、funcId、RSA/Hybrid ECDH、pass key、route info。

## 2. 已确认采集，但上传位置/频率未完全确认

- Android ID、OAID、IMSI、SIM country、首次安装时间。
- CPU、Radio、Build fingerprint、board、product。
- ADB enabled、development settings enabled。
- 应用安装/卸载事件和厂商 APPRISK 事件。
- Display、相机、音频模式和触摸 MotionEvent 轨迹。

`cp/w0.smali` 明确读取 `adb_enabled`、`development_settings_enabled`、CPU 信息、IMSI/SIM country、Android ID、device ID、首次安装时间等。ManualAuth 日志把这些字段放入认证环境结构。Normsg 路径会把 MotionEvent、相机状态、音频模式和显示设备编码成不透明安全数据。

## 3. Root、Hook 与环境完整性

Java RiskScanner 的静态检查包括：

- `/system/bin/su`、`/system/xbin/su`、`/sbin/su` 与 PATH 中的 `su`。
- root UID/zygote 进程关系、root daemon 进程名。
- SELinux 非 enforcing 时的 setuid 文件。
- debugger/recovery 文件的 ELF/script 形态。
- 结果进入 `viruscheck` / `RiskCheck`。

另有：

- QQ/WLogin 设备结构中的环境字段。
- UnionPay SDK 报告 `/system/bin/su` 是否存在。
- Tinker 崩溃保护检查 Java 栈中的 XposedBridge，这是进程内 Hook 指示。
- `libmis.so` 针对小游戏 V8/JS 的调试、断点、调用栈、变量和 Wasm 信号。
- `libhuiyanandroidsdk.so` 与 `libYTAGReflectLiveCheck.so` 是活体核验组件。

需要区分误报：WCDB `integrity_check`、下载配置完整性、Matrix/crash/unwind、Owl JNI hook、MarsQUIC TracerPid 都不是通用 root/Frida 上传链。`KindaDeviceServiceImpl.isRoot()` 当前调用链硬编码 `false`。

## 4. 服务端可能组合的信号

- 设备可信度：官方签名、稳定 device ID、Build 自洽性、账号/设备切换频率。
- 环境完整性：Root/Hook/虚拟显示/模拟器/远程控制、APK 变化、APPRISK。
- 操作行为：触摸轨迹、登录/扫码/支付时序、相机/音频/页面状态。
- 网络与账号关系：出口 IP/地区、时区、SIM country、历史设备、session/UIN/device ID/ECDH 是否匹配。

以上是基于客户端字段和采集能力的防御性分析，不是服务端评分规则。

## 5. 证据强度

### 已验证

- 登录/协议字段、EC key、签名/封包接口、消息 Protobuf 字段。
- Java RiskScanner 的 su/进程/setuid/debugger 检查入口。
- 设备信息读取 API 和 Normsg 事件入口。
- 支付 PSK/AES-GCM/signature/root-cert/CGI 映射字符串。

### 结构已证实

- Normsg 数据块进入认证/安全上传结构，但字段编码未逐项还原。
- 支付组件具备设备信息、交易上下文、签名和 MMTLS 能力，但未触发真实支付。

### 静态推断或未知

- 每项信号的服务端权重、封禁阈值和远程 feature gate。
- 是否上传完整安装应用列表或完整进程列表。
- 动态 UDR 模块在小游戏触发后的行为。
