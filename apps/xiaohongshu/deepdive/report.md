# 小红书 9.37.0 原生加密与传输深挖报告

## 样本

| 字段 | 值 |
| --- | --- |
| 包名 | `com.xingin.xhs` |
| versionName | `9.37.0` |
| versionCode | `9370802` |
| minSdk / targetSdk | `21` / `35` |
| XAPK SHA-256 | `42033a369835209738ee5b4b1ad6553e6289cac09fee8559fbf4286c9d490bbd` |
| base APK SHA-256 | `0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c` |
| arm64 config SHA-256 | `b2580ad86d27ec595cb8e07c104d8b90ff0a780d963c876de52ef0ae6afc2487` |
| `libxyass.so` SHA-256 | `8e7db9e41ec7cacaf504fa07e68a325aa1bae220cabb5a745d31e3154922a17b` |
| `libtiny.so` SHA-256 | `b403a883b6bba843197fe076deb332b71be5c74c442781b26c89be69de21f6fd` |
| 规模 | 22 dex（20 个 `classes*.dex` + 2 个 `assets/*.dex`）/ 164 个 arm64 `.so` / 105 877 个 jadx 还原源文件 |

## 目录

| 主题 | 文档 |
| --- | --- |
| 网络流程与协议格式 | [protocol.md](protocol.md) |
| 认证机制 | [auth.md](auth.md) |
| 上传链路与数据范围 | [upload.md](upload.md) |
| 下载链路与 Range 范围 | [download.md](download.md) |
| 风控组件清单 | [risk-controls.md](risk-controls.md) |
| 加密代码覆盖矩阵 | [crypto.md](crypto.md) |
| 证据索引 | [evidence.md](evidence.md) |
| 算法边界与公式 | [algorithm.md](algorithm.md) |
| 需求对照审计 | [audit.md](audit.md) |

## 逆向方法

1. `jadx` 还原 Java/Kotlin/JNI 声明与调用链（含按类补抓缺失类）。
2. `pyelftools` 解析 ELF、重定位与符号；Capstone 反汇编 ARM64。
3. Unicorn 用假 `JNIEnv`/libc stub 模拟 native：构造器、`JNI_OnLoad`、签名装配、`0x50010` 会话变换。
4. 对确定性算法做**逐指令 trace**，并与离线符号化电路对拍。
5. 对 CFF 区域做**静态指令普查**（指令族、常量集、表引用），避免把平坦化误读为“数据驱动加密”。
6. 全部测试向量由合成 build/device/token 生成，不使用真实账号、设备或请求。

## 主要结论

**网络与协议**：主链路是自研 OkHttp 封装；Retrofit 被自定义注解替换（`fvc.f`=GET、`fvc.o`=POST、`fvc.t`=Query、`fvc.c`=Form），这是“路径搜不到”的原因。请求参数袋是明文头部 `xy-common-params`（36 个字段，`k=v&k=v`），签名字段覆盖在其上。

**认证**：核心 API **不使用** `Authorization: Bearer`。身份由账号层 `id_token`、会话层 `sid`、设备层 `deviceId`/`fid`/`gid` 三组字段经明文头部传播，再由 native 组件生成 `shield`、`xy-platform-info`、`x-n0`/`x-o9`/`x-p0`/`x-r4`/`x-r4o` 覆盖签名。Shield 全库仅 2 个接入点。

**上传**：`GET /api/media/v1/upload/{permit,capa/permit,permit_no_login}` 取云厂商临时令牌；`quick_upload_check` 以**全文件 MD5** 做去重（命中判定 `success && code == 1`）；分块由 `c(fileLen)` 决定，基线 1 MiB、按 2000 块上限反推并 MiB 对齐；Qiniu 走 SDK 分块 + 本地断点记录（SHA-1 命名的记录文件，48 h 过期），COS 走整对象 PUT（无 Range）。

**下载**：播放引擎首次 GET **不带 Range**，seek 时用 `Range: bytes=<offset>-` 从偏移续读到 EOF，并清空旧缓冲；206 用 `Content-Range` 取总长、否则用 `Content-Length`。分片尺寸由服务端字段 `http_range_size` 控制。

**加密**：shield 外层 = RC4（13 字节静态 key）+ 16 字节头 + Base64（逐字节验证）；摘要 = 定制 MD5 族 HMAC（IV 逆序存储、K16/K19/K29 篡改、K39/40 与 K41/42 互换、第 63 轮累加器与 c63 特殊处理），3 组向量 0 mismatch；type 6/7 会话变换已固化接口、字节级依赖矩阵、504 处间接跳转与 8 项跳转表、16 张 rodata 常量向量表（含动态可达性）、8 组确定性向量与扩散统计。**覆盖全部 CFF 路径的统一逻辑门级闭式尚未产出**，单 trace lift 经实测不具泛化性（4/4 mismatch），故不作为参考实现。

**风控**：Shield / Tiny / 设备指纹 / JS 指纹（AES-128 硬编码 key 已定位）/ 两套人机验证 / 人脸核身 / 支付风控 / 端智能 / 伴随守护，共 9 类组件，逐条给出证据等级与未闭环环节。

## 关键纠正（相对早期结论）

| 早期结论 | 现状 |
| --- | --- |
| 定制 H 的压缩轮未复原 | **已完整恢复**，3 组向量 0 mismatch |
| `0x50010` 的算法“未定性” | **已定性**：64B payload + 20B key → 16B 输出；宏结构、常量集、8 块跳转表、CFF 机制均确认 |
| **`0x50010` 区域“无 rodata 常量表引用”** | **已撤回（本次修正）**：寄存器精确扫描器发现 **29 个 `.rodata` 对象**被引用（16 张 16 字节序列表 + 13 个常量对象），并经 `.rodata` 读钩子确认为热路径数据；但区域内**无索引式查表**，这些表是常量向量池而非 S 盒 |
| 分派选择子由 payload 决定 ⇒ 算法随数据变化 | **前半误读、后半需补正**：主分派点选择子是状态机块索引（`ldrb w9,[x20]` + `and #3`）；**但整体控制流仍随输入变化**——实测 337 个间接目标（其中相邻 4 组 241 个）、间接跳转 283–2 861 次、指令数 4 889–42 708，8 个顶层块仅 2 个全样本必进 |
| 单 trace lift 可作为 `0x50010` 参考实现 | **已证伪**：修正对比宽度后跨输入 **4/4 mismatch**，仅覆盖其训练轨迹 |
| `libtiny.so` 0x754AC0 是加密 blob | **证伪**：三路扫描 0 引用；正确表述为“未发现被引用的高熵数据区” |
| 疑似 AES-ECB 魔数 `35 16 11` | 就是 RC4 密钥流前三字节 |
| `captcha_link` 端点 | 样本内**不存在**该字面端点；验证页 URL 来自服务端下发 |
| 上传用 Range | **否**：COS 整对象 PUT，Qiniu 走 SDK 分块 |

## 公开范围与边界

公开内容包括：APK/XAPK/SO 哈希、版本、类与方法路径、函数地址、反汇编片段、协议字段名、合成测试向量、聚合统计。

不公开：APK/SO/DEX 本体、反汇编全量文件、真实 key/token/sid/deviceId、真实请求或响应、服务端下发内容、可复现线上风控绕过的构造。

未闭环项已在各文档显式列出，主要四类：Tiny opcode→语义映射（操作码全集 31 个已枚举，仍需逐块 lift）、`libxyasf.so` 70+ 采集点逐条清单、`0x50010` 覆盖全部 CFF 路径的统一闭式（数据相关控制流使单 trace lift 失效，需逐块/逐路径 lift）、以及一切需要运行时抓包才能确定的项（服务端 `http_range_size` 实际取值、CDN `Accept-Ranges`、Cookie 的实际作用）。

另：全应用 164 个 arm64 `.so` 的 native 加密普查已完成（32 带加密 / 132 不带），见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md)。
