# 小红书原生安全组件逆向报告

## 样本

| 字段 | 值 |
| --- | --- |
| 包名 | `com.xingin.xhs` |
| versionName | `9.37.0` |
| versionCode | `9370802` |
| minSdk / targetSdk | `21` / `35` |
| XAPK SHA-256 | `42033a369835209738ee5b4b1ad6553e6289cac09fee8559fbf4286c9d490bbd` |
| base APK SHA-256 | `0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c` |

## 逆向方法

1. `jadx` 还原 Java/Kotlin JNI 声明和调用链。
2. `pyelftools` 解析 ELF、重定位和符号，Capstone 反汇编 ARM64。
3. 使用假 `JNIEnv`/libc stub 的 Unicorn 模拟验证构造器、`JNI_OnLoad` 和确定性算法。
4. 对字符串加密、CFF/opcode 和加密 blob 做静态定位；不能离线闭环的部分明确保留未知。
5. 用合成 build/device/token 生成测试向量，不使用真实账号或请求。

## `libxyass.so`

- `JNI_OnLoad` 位于 `0x3f484`，在 `0x3f6f8` 注册 4 个方法。
- `intercept` 位于 `0x45764`，是 OkHttp interceptor native 入口。
- signer `0x49634` 输出 token type 与 payload；assembler `0x467dc` 分派并生成 `shield`。
- 外层公式已由两组合成向量逐字节验证。
- 16 字节摘要槽具有 HMAC 外壳，但内部哈希是定制 MD5 族变体，压缩轮尚未完整复原。

## `libtiny.so` / `libtinyd.so`

- `libtiny.so` 的 `JNI_OnLoad` 为 `0x18afd8`，opcode 分发比较点为 `0x16b08c` / `0x17cdb0`。
- 加密模块 blob 位于 `0x754AC0`，描述符区域为 `0xf7b40`-`0xf7bb0`。
- 静态模拟已跑通构造器、`JNI_OnLoad` 和 opcode 分发；运行时 blob 解密没有离线闭环。
- `libtinyd.so` 的 fork/syslog/abort-message 特征支持“伴随守护组件”判断。

## 其他组件

- `libsecurebase.so`：登录态 KV 文件名派生和定制哈希。
- `libeidjni.so`：eID/NFC 电子证件读取 SDK。
- `libturingmfa.so`：TuringFD 设备风险、设备指纹和 DeviceToken 协同组件。

## 结论

请求安全由会话 token、请求摘要、外层 RC4/Base64、原生混淆和设备风控共同组成。公开内容只展示可复现外层结构和已确认函数位置，不提供真实 key、token、设备标识或请求样本。
