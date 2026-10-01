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
| `libtinyd.so` SHA-256 | `ea32c231da936398b06fa42b1a2726e195ee5fe36bf799a0a4d42ff9c66ac618` |
| `libxyasf.so` SHA-256 | 见 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md) |
| 规模 | 22 dex（20 个 `classes*.dex` + 2 个 `assets/*.dex`）/ 164 个 arm64 `.so` / 105 877 个 jadx 还原源文件 |

## 目录

| 主题 | 文档 |
| --- | --- |
| 网络流程与协议格式 | [protocol.md](protocol.md) |
| 认证机制 | [auth.md](auth.md) |
| 上传链路与数据范围 | [upload.md](upload.md) |
| 下载链路与 Range 范围 | [download.md](download.md) |
| 风控组件清单 | [risk-controls.md](risk-controls.md) |
| 伴随守护 `libtinyd.so` | [tinyd-companion-daemon.md](tinyd-companion-daemon.md) |
| 设备指纹 `libxyasf.so` | [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md) |
| 全应用加密普查与 Tiny 引擎 | [tiny-and-app-sweep.md](tiny-and-app-sweep.md) |
| 加密代码覆盖矩阵 | [crypto.md](crypto.md) |
| 证据索引 | [evidence.md](evidence.md) |
| 算法边界与公式 | [algorithm.md](algorithm.md) |
| 需求对照审计 | [audit.md](audit.md) |

## 逆向方法

1. `jadx` 还原 Java/Kotlin/JNI 声明与调用链（含按类补抓缺失类）。
2. `pyelftools` 解析 ELF、重定位与符号；Capstone 反汇编 ARM64。
3. Unicorn 用假 `JNIEnv`/libc stub 模拟 native：构造器、`JNI_OnLoad`、签名装配、`0x50010` 会话变换；CFF 转移图由 3 000 组输入的饱和实测恢复（412 site / 767 边 / 417 目标），选择层按生产者操作数分类闭环。
4. 对确定性算法做**逐指令 trace**，并与离线符号化电路对拍。
5. 对 CFF 区域做**静态指令普查**（指令族、常量集、表引用），避免把平坦化误读为“数据驱动加密”。
6. 全部测试向量由合成 build/device/token 生成，不使用真实账号、设备或请求。

## 主要结论

**网络与协议**：主链路是自研 OkHttp 封装；Retrofit 被自定义注解替换（`fvc.f`=GET、`fvc.o`=POST、`fvc.t`=Query、`fvc.c`=Form），这是“路径搜不到”的原因。请求参数袋是明文头部 `xy-common-params`（36 个字段，`k=v&k=v`），签名字段覆盖在其上。

**认证**：核心 API **不使用** `Authorization: Bearer`。身份由账号层 `id_token`、会话层 `sid`、设备层 `deviceId`/`fid`/`gid` 三组字段经明文头部传播，再由 native 组件生成 `shield`、`xy-platform-info`、`x-n0`/`x-o9`/`x-p0`/`x-r4`/`x-r4o` 覆盖签名。Shield 全库仅 2 个接入点。

**上传**：`GET /api/media/v1/upload/{permit,capa/permit,permit_no_login}` 取云厂商临时令牌；`quick_upload_check` 以**全文件 MD5** 做去重（命中判定 `success && code == 1`）；分块由 `c(fileLen)` 决定，基线 1 MiB、按 2000 块上限反推并 MiB 对齐；Qiniu 走 SDK 分块 + 本地断点记录（SHA-1 命名的记录文件，48 h 过期），COS 走整对象 PUT（无 Range）。

**下载**：播放引擎首次 GET **不带 Range**，seek 时用 `Range: bytes=<offset>-` 从偏移续读到 EOF，并清空旧缓冲；206 用 `Content-Range` 取总长、否则用 `Content-Length`。分片尺寸由服务端字段 `http_range_size` 控制。

**加密**：shield 外层 = RC4（13 字节静态 key）+ 16 字节头 + Base64（逐字节验证）；摘要 = 定制 MD5 族 HMAC（IV 逆序存储、K16/K19/K29 篡改、K39/40 与 K41/42 互换、第 63 轮累加器与 c63 特殊处理），3 组向量 0 mismatch；type 6/7 会话变换已固化接口、字节级依赖矩阵、504 处间接跳转与 8 项跳转表、16 张 rodata 常量向量表（含动态可达性）、8 组确定性向量与扩散统计。**CFF 转移关系已完整枚举并饱和**：412 个可达间接分支 site、**767 条转移边**、417 个目标（3 000 组输入、10 个输入族，最后 1 000 组增量为 0），目标全部落在 `0x50010`–`0x5a264` 内且 4 字节对齐，单 site 出度 ≤ 16。**选择层亦已闭环**：262 FIXED / 69 BASE / 18 DATA，18 个 DATA 全为帧内状态字节的二路二选一，两候选目标均为编译期立即数；单 trace lift 经实测不具泛化性（4/4 mismatch），故不作为参考实现。

**风控**：Shield / Tiny / 设备指纹（82 个 JNI 入口 + 51 个采集字段逐条恢复）/ JS 指纹（AES-128 硬编码 key 已定位）/ 两套人机验证 / 人脸核身 / 支付风控 / 端智能 / 伴随守护，共 10 类组件，逐条给出证据等级与未闭环环节。

**伴随守护（`libtinyd.so`）**：不是网络组件，也不是加密组件——38 个导入中无任何网络符号。动作由 `JNI_OnLoad` 触发（`.init_array` 全 0，不在加载时自启）。核心是**无名管道 IPC**：`pipe2` → `fork` → **定长 4 字节**信令 + 写后即 `close`，读侧循环到恰好 4 字节。配合 `android/os/Process.setArgV0("zygote")` 与 `prctl(PR_SET_NAME)` 双重改名，把子进程在 `ps`/`cmdline`/`comm` 三个视图里伪装成 zygote 派生进程，并用 `/proc/<pid>/status` 的 `TracerPid:` 反调试。其字符串加密（4 个解码器 × `i%20` 调度表的旋转+模加/XOR 双射）已闭式还原，**7/7 明文全部解出**。

**Tiny 引擎：31 个操作码全部定名**。引擎是 `com.xingin.tiny.internal.t` 上的 **双操作码空间 VM**——**native 侧 31 个**（`libtiny.so`，`JNI_OnLoad` @ `0x18afd8`，操作码字段 `[x19,#0xa4]`，61 个 CFF 比较块 + 61 字节谓词数组）与 **Java 侧 71 个**（`t.b()` 的 switch）**交集为 0**。

**31/31 操作码常量精确恢复**（61/61 比较块全部对上），**29 个**在 classes17/18 里定位到**确切的调用表达式**。核心链路是 **OkHttp 请求签名**：`nlb.p`（TinyInterceptor）→ `yya.f.e(method, url, bodyBytes)` → **`u2.b(0x96f7fcac, method, host, path, query, body)`** → `t.a(op, …)` → `Map<String,String>` → 逐条写成 `x-n0`/`x-o9`/`x-p0`/`x-r4`/`x-r4o`。同一引擎还承担 TLS 证书链上报（`0xae8750a7`）、HTTP/2 peer principal 上报（`0x9701e74c`）、长连接下行消息（`0xb20a0be3`）、定位上报（`0x2f036831`）、传感器注册（`0xcf7db9ff`）、前台状态（`0xc23a168e`）、动态代理转发（`0x2ad1c199`）。

**执行集度量**：`a()` 是**单个 FDE 覆盖的 181 732 字节 CFF 巨函数**（按符号归因会得到 `distinct_fn = 1` 的假象）。31 个操作码的静态可达块集合**两两 Jaccard 全部 < 0.84**（465 组合无一达 0.98），动态执行集**共有 2471 条指令**、并集 43 258 条。分组 1/2 的 90–198 条"专属指令"经**逐条反汇编证明是 CFF 调度胶水**（21 份 20–22 条的独立调度副本，算术原语仅 1 条 `eor`），**不是算法、不需要 lift**（原稿称其为"下一轮 lift 的最小充分目标"已撤回）。

## 关键纠正（相对早期结论）

| 早期结论 | 现状 |
| --- | --- |
| 定制 H 的压缩轮未复原 | **已完整恢复**，3 组向量 0 mismatch |
| `0x50010` 的算法“未定性” | **已定性**：64B payload + 20B key → 16B 输出；宏结构、常量集、8 块跳转表、CFF 机制均确认 |
| **`0x50010` 区域“无 rodata 常量表引用”** | **已撤回（本次修正）**：寄存器精确扫描器发现 **29 个 `.rodata` 对象**被引用（16 张 16 字节序列表 + 13 个常量对象），并经 `.rodata` 读钩子确认为热路径数据；但区域内**无索引式查表**，这些表是常量向量池而非 S 盒 |
| 分派选择子由 payload 决定 ⇒ 算法随数据变化 | **前半误读、后半需补正**：主分派点选择子是状态机块索引（`ldrb w9,[x20]` + `and #3`）；**但整体控制流仍随输入变化**——实测 337 个间接目标（其中相邻 4 组 241 个）、间接跳转 283–2 861 次、指令数 4 889–42 708，8 个顶层块仅 2 个全样本必进。扩到 3 000 组输入（10 个输入族）后该集合**饱和**为 412 site / 767 边 / 417 目标，故控制流是有限的；选择层已分类为 FIXED 262 / BASE 69 / DATA 18 |
| 单 trace lift 可作为 `0x50010` 参考实现 | **已证伪**：修正对比宽度后跨输入 **4/4 mismatch**，仅覆盖其训练轨迹（因 110 个 site 出度 ≥ 2） |
| `0x50010` 的控制流“原理上无法 lift” | **已撤回**：实测 3 000 组输入后转移关系**饱和**（412 site / 767 边 / 417 目标，末 1 000 组零增长），有限且已枚举；上一版依据的“213 表加载 / 291 索引加载”分类源于不做 def-use 追踪的扫描器，不成立 |
| payload 单比特翻转“64/64 全部有影响、区间 49–75” | **已修正**：穷举 512 位后在 4 个基准上一致发现 **16 个结构性惰性位**（每 16 字节块偏移 3、7 的上位），有效熵 496/512，区间实为 **0–82**。旧结论样本量仅 48，未覆盖死位 |
| `0x50010` “选择谓词未符号化” | **已闭环**：60 组输入单机分类得 **FIXED 262 / BASE 69 / DATA 18**；每 site 生产者唯一（362 单/0 多）；18 个 DATA 全为二路分支，条件为帧内状态字节，两候选目标均为立即数 |
| `libtiny.so` 0x754AC0 是加密 blob | **证伪**：三路扫描 0 引用；正确表述为“未发现被引用的高熵数据区” |
| 疑似 AES-ECB 魔数 `35 16 11` | 就是 RC4 密钥流前三字节 |
| `captcha_link` 端点 | 样本内**不存在**该字面端点；验证页 URL 来自服务端下发 |
| 上传用 Range | **否**：COS 整对象 PUT，Qiniu 走 SDK 分块 |
| `libtinyd.so` 靠 socket 与主进程通信 | **撤回**：38 个导入无网络符号，通道是无名管道 |
| `libtinyd.so` 明文含 `p6ro` | **撤回**：长度参数错配（4→2），正确值为 `am` |
| `libtinyd.so` 字符串算法是纯 XOR | **撤回**：位置相关的旋转+模加/XOR 双射，非线性 |
| Tiny 分组 1 的 17 个操作码是"参数形状受限的提前退出" | **撤回**：实测执行 3070 条指令，返回 0 是因结果写入随后被清零的栈槽 |
| Tiny 操作码"逐操作码语义未展开" | **撤回**：31/31 已定名（native/Java 双操作码空间 + dex 调用表达式），见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §5.6 |
| Tiny 90–198 条"专属指令 = 下一轮 lift 的最小充分目标" | **撤回**：逐条反汇编证明是 CFF 调度胶水（算术原语仅 1 条 `eor`），**不是算法**，见 §5.5.7 |

## 公开范围与边界

公开内容包括：APK/XAPK/SO 哈希、版本、类与方法路径、函数地址、反汇编片段、协议字段名、合成测试向量、聚合统计。

不公开：APK/SO/DEX 本体、反汇编全量文件、真实 key/token/sid/deviceId、真实请求或响应、服务端下发内容、可复现线上风控绕过的构造。

未闭环项已在各文档显式列出，主要五类：Tiny 操作码**内部逐块算术步骤** lift（**调用语义已全部定名 31/31**；专属指令已证明是 CFF 胶水而非算法）、`libtinyd.so` 的 `JNINativeMethod` 三元组（CFF 内运行时构造，需进程内插桩）与 4 字节载荷取值语义（协议形状已定）、~~`0x50010` 各 site 的转移选择谓词~~ 本轮已闭环（412 site / 767 边 / 417 目标；262 FIXED / 69 BASE / 18 DATA）、运行时抓包才能确定的项（服务端 `http_range_size` 实际取值、CDN `Accept-Ranges`）、以及 `libxyasf.so` 父消息的 8 个子消息字段号（来自运行时 type-info 表；子消息内部 51 个字段号已全部取得）。Cookie 作用已结构性证清：API 客户端未装 `CookieJar`，cookie 仅属 WebView/RN/第三方。

设备指纹一项本轮已由"结构已证实"升为**已恢复**，见 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md)：该库未做字符串加密，采集面可静态穷举，无需 VM 级 lift。

另：全应用 164 个 arm64 `.so` 的 native 加密普查已完成（32 带加密 / 132 不带），见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md)。
