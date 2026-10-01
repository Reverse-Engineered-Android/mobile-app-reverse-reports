# 小红书 9.37.0 分析完成度矩阵

本文件把“网络、认证、传输、风控、加密”五个要求拆成可核验的代码边界。它不是登录绕过、签名伪造、token 生成或风控规避指南；仓库只发布脱敏结构、证据地址、合成向量和不带秘密的工具。

## 1. 覆盖矩阵

| 要求 | 已覆盖内容 | 主要入口 | 公开结论 |
| --- | --- | --- | --- |
| 主要网络交互 | Retrofit/OkHttp、主 API、Hera/推荐、搜索、画像、CDN、风控直报、实时长连接 | [network.md](network.md) | 域名、调用链、请求/响应封装和长连接生命周期已整理 |
| 协议具体格式 | `xy-common-params`、`EdithBaseResponse`、笔记创建 JSON、protobuf 字段号、multipart/对象存储 token 结构 | [network.md](network.md)、[transfer.md](transfer.md) | 字段和 wire 类型按证据等级标注；不包含真实请求/响应 |
| 认证机制 | session/sessionNum、`sid`、`id_token`、user token、OAuth、登录/找回/风控接口 | [network.md](network.md) | 已确认来源、规范化和传输边界；秘密值与可重放材料不公开 |
| 上传数据范围 | 联系人、位置/POI、图片/视频、Diandian context、native 风控容器、长连接握手 | [transfer.md](transfer.md) | 明确“业务上传”“诊断接口”“风险上报”三类，避免把能力当成实际上传 |
| 下载数据范围 | 图片/视频、前端资源、广告素材、DSL 模板、插件、下载元数据 | [transfer.md](transfer.md) | 按来源、格式和本地缓存范围列出；不展开用户正文 |
| 风控代码 | Shield/Tiny、设备指纹、环境完整性、JS 指纹、验证码/核身、账号处置、推送/策略 SDK | [risk.md](risk.md) | 组件、JNI/Java 入口、采集类别、上报边界和处置链已覆盖 |
| 加密/签名 | Shield 外层、HMAC 外壳、Tiny 边界、长连接 ECDH/AES/gzip、WCDB/Keystore、联系人 AES | [algorithm.md](algorithm.md)、[storage.md](storage.md) | 只发布已验证的结构和依赖；秘密、白盒表、私钥和可重放实现不进入仓库 |

> **深挖批次补充（[deepdive/](deepdive/README.md)）**：上表是上层批次的边界。深挖批次另做了三件本表未覆盖的事——**全应用 164 个 arm64 `.so` 的加密普查**（32 带 / 132 不带）与**混淆形态普查**（重度 CFF 仅 3 个库）、**`libtiny.so` 内联 X25519 域运算**的定名（`0x525000`–`0x531e4c`）、以及 **`libtiny.so` 字符串加密的闭式还原**（320 调用点解出 312 条明文，闭环率 100%）。口径与证据见 [deepdive/tiny-and-app-sweep.md](deepdive/tiny-and-app-sweep.md) §1.5、§2.2.1、§2.4。**最新一轮另做两件**：**`libtiny.so` 域区逐块算术 lift 完成**（435 distinct run 全判读、154 域原语 + 伪代码、九项密码学指纹守恒逐项 EXACT、188 未执行槽位逐段定性且 0 个含密码学指纹，§5.6.9）；以及 **Java/dex 侧混淆审计**（`@u5/@v5` 加密字段名 519/519 闭式还原；Java 侧字符串解密器 **822 调用点按字节码精确计数，811 个内联 (cipher,key) 对全部映射到明文，0 未映射**；11 个反射包装器枚举；**daemon dex 三层混淆完整审计**——字符串 141 调用点、`@x0` 116/116、`@w0` 77 站点、`v.<clinit>` 74 条明文、IPC 12 个命令，§9.2b/§9.7），并**纠正旧稿把插件化框架代号 `Petal` 误记为混淆器**的归因错误。

## 2. 风控组件清单

| 层 | 组件 | 已分析的代码边界 | 证据状态 |
| --- | --- | --- | --- |
| 请求完整性 | `libxyass.so` | JNI 注册、OkHttp interceptor、`shield`/`xy-platform-info`、P/blob/RC4/Base64、摘要容器 | 外层已验证；摘要行为级闭环 |
| 请求完整性 | `libtiny.so` / `libtinyd.so` | JNI/opcode 入口、method/URL/body 输入、mini-sign headers、token refresh、守护特征；深挖批次另给出 **31/31 操作码定名 + 61 分派块三向双射**、**内联 X25519**、**字符串加密闭式（320 调用点 / 312 明文）**、**内嵌 Lua 解释器标识符** | 调用与数据边界已验证；深挖批次见 [deepdive/tiny-and-app-sweep.md](deepdive/tiny-and-app-sweep.md) |
| 设备指纹 | `libxyasf.so` | native collectors、root/hook/ptrace/maps/VirtualApp、APK 完整性、multipart 风控上传 | 采集面与上报容器已验证 |
| Java 采集 | `pt`/`qt`/`fp`/monitor collectors | 传感器、进程、无障碍、电池、网络、屏幕、Build/ROM、JS fingerprint | 类/字段/调度已验证 |
| 人机验证 | Walify、`ValidateActivity`、captcha/self-resolve | H5/RN 验证入口、风险说明、干预/解限响应 | 业务链与响应字段已验证 |
| 实名/人脸 | TuringFD/turingcam、慧眼、KYC/SM2、优图活体 | 设备风险、TuringV2、DeviceToken、活体帧与核身数据封装 | 组件职责和数据类别已验证 |
| 账号处置 | `IRiskService`、登录 risk、anomalies、intervention | 设备违规、账号异常、绑定提醒、干预配置、自助解限 | API/字段/触发条件边界已验证 |
| 第三方策略 | Getui/GTC/GBD、push、远程采集策略 | OAID/设备/网络状态、配置、AES/RSA 包装、策略开关 | 存储与能力边界已验证 |

## 3. 加密代码公开边界

公开仓库不保留“只看到密文但没有解释”的可执行实现。处理规则如下：

1. 已由模拟器或合成向量逐字节验证的算法，给出结构、输入输出和测试向量；秘密通过环境变量或省略。
2. 只能确认调用边界、字段或密文容器的内容，标记为“结构已证实”，不伪称可重放。
3. 受 Android Keystore/TEE、服务端下发会话或第三方 SDK 秘密保护的部分，记录 custodian、算法类别和解密前置条件，不导出 key/IV/token。
4. `libtiny.so` 加密 blob、Tiny opcode payload、TuringFD/WUP 私有 wire 细节不作为公开代码；对应入口和输出 headers/字段仍完整列入证据索引。

因此，`tools/assemble_xhs_shield.py` 只包含已验证的 Shield 外层组装；它不把定制摘要当成标准 HMAC-MD5，也不生成有效会话 token。

## 4. 可复核证据

| 主题 | 复核位置 | 结果 |
| --- | --- | --- |
| Shield 外层 | [algorithm.md](algorithm.md)、`tools/assemble_xhs_shield.py` | 合成向量与 blob/header 顺序一致 |
| 请求/认证 | [network.md](network.md) | Retrofit 注解、公共参数、登录/OAuth/风险 API 字段闭环 |
| 上传/下载 | [transfer.md](transfer.md) | token/permit、分片、联系人/媒体/风控/长连接范围分开 |
| 风控 | [risk.md](risk.md) | native/Java/第三方组件和风险处置链闭环 |
| 存储/密钥 | [storage.md](storage.md) | DB 参数、Keystore custody、Java serialization/gzip 边界闭环 |
| 函数地址与 JNI | [evidence.md](evidence.md) | 地址、签名、符号和证据等级可复查 |

## 5. 研究边界

本报告只适用于自有或获授权样本。它不提供真实账号操作、登录绕过、签名伪造、token 生成、批量抓取或规避服务端风控的方法。服务端评分、阈值、留存、灰度和最终处置规则不在客户端静态逆向的可证明范围内。
