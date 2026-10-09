# 小红书 9.37.0 原生加密与传输证据附录

本文用于索引地址、反汇编、合成向量和审计结果，不承载第二份最终结论；最终结论只在 [../report.md](../report.md)。

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
5. 对 CFF 区域做**静态指令普查**（指令族、常量集、表引用），避免把平坦化当作“数据驱动加密”。
6. 全部测试向量由合成 build/device/token 生成，不使用真实账号、设备或请求。

## 主要结论

**网络与协议**：主链路是自研 OkHttp 封装；Retrofit 被自定义注解替换（`fvc.f`=GET、`fvc.o`=POST、`fvc.t`=Query、`fvc.c`=Form），这是“路径搜不到”的原因。请求参数袋是明文头部 `xy-common-params`（36 个字段，`k=v&k=v`），签名字段覆盖在其上。

**认证**：核心 API **不使用** `Authorization: Bearer`。身份由账号层 `id_token`、会话层 `sid`、设备层 `deviceId`/`fid`/`gid` 三组字段经明文头部传播，再由 native 组件生成 `shield`、`xy-platform-info`、`x-n0`/`x-o9`/`x-p0`/`x-r4`/`x-r4o` 覆盖签名。Shield 全库仅 2 个接入点。

**上传**：`GET /api/media/v1/upload/{permit,capa/permit,permit_no_login}` 取云厂商临时令牌；`quick_upload_check` 以**全文件 MD5** 做去重（命中判定 `success && code == 1`）；分块由 `c(fileLen)` 决定，基线 1 MiB、按 2000 块上限反推并 MiB 对齐；Qiniu 走 SDK 分块 + 本地断点记录（SHA-1 命名的记录文件，48 h 过期），COS 走整对象 PUT（无 Range）。

**下载**：播放引擎首次 GET **不带 Range**，seek 时用 `Range: bytes=<offset>-` 从偏移续读到 EOF，并清空旧缓冲；206 用 `Content-Range` 取总长、否则用 `Content-Length`。分片尺寸由服务端字段 `http_range_size` 控制。

**加密**：shield 外层 = RC4（13 字节静态 key）+ 16 字节头 + Base64（逐字节验证）；摘要 = 定制 MD5 族 HMAC（IV 逆序存储、K16/K19/K29 篡改、K39/40 与 K41/42 互换、第 63 轮累加器与 c63 特殊处理），3 组向量 0 mismatch；type 6/7 会话变换已固化接口、字节级依赖矩阵、504 处间接跳转与 8 项跳转表、16 张 rodata 常量向量表（含动态可达性）、8 组确定性向量与扩散统计。**CFF 转移关系已完整枚举并饱和**：412 个可达间接分支 site、**767 条转移边**、417 个目标（3 000 组输入、10 个输入族，最后 1 000 组增量为 0），目标全部落在 `0x50010`–`0x5a264` 内且 4 字节对齐，单 site 出度 ≤ 16。**选择层亦已闭环**：262 FIXED / 69 BASE / 18 DATA，18 个 DATA 全为帧内状态字节的二路二选一，两候选目标均为编译期立即数；单 trace lift 经实测不具泛化性（4/4 mismatch），故不作为参考实现。

**风控**：Shield / Tiny / 设备指纹（82 个 JNI 入口 + 51 个采集字段逐条恢复）/ JS 指纹（AES-128 硬编码 key 已定位）/ 两套人机验证 / 人脸核身 / 支付风控 / 端智能 / 伴随守护，共 10 类组件，逐条给出入口、控制流、判定依据和证据等级；服务端配置值与线上响应作为运行期数据单列。

**伴随守护（`libtinyd.so`）**：不是网络组件，也不是加密组件——38 个导入中无任何网络符号。动作由 `JNI_OnLoad` 触发（`.init_array` 全 0，不在加载时自启）。核心是**无名管道 IPC**：`pipe2` → `fork` → **定长 4 字节**信令 + 写后即 `close`，读侧循环到恰好 4 字节。配合 `android/os/Process.setArgV0("zygote")` 与 `prctl(PR_SET_NAME)` 双重改名，把子进程在 `ps`/`cmdline`/`comm` 三个视图里伪装成 zygote 派生进程，并用 `/proc/<pid>/status` 的 `TracerPid:` 反调试。其字符串加密（4 个解码器 × `i%20` 调度表的旋转+模加/XOR 双射）已闭式还原，**7/7 明文全部解出**。

**Tiny 引擎：31 个操作码全部定名**。引擎是 `com.xingin.tiny.internal.t` 上的 **双操作码空间 VM**——**native 侧 31 个**（`libtiny.so`，`JNI_OnLoad` @ `0x18afd8`，操作码字段 `[x19,#0xa4]`，61 个 CFF 比较块 + 61 字节谓词数组）与 **Java 侧 71 个**（`t.b()` 的 switch）**交集为 0**。

**31/31 操作码常量精确恢复**（61/61 比较块全部对上），**29 个**在 classes17/18 里定位到**确切的调用表达式**。核心链路是 **OkHttp 请求签名**：`nlb.p`（TinyInterceptor）→ `yya.f.e(method, url, bodyBytes)` → **`u2.b(0x96f7fcac, method, host, path, query, body)`** → `t.a(op, …)` → `Map<String,String>` → 逐条写成 `x-n0`/`x-o9`/`x-p0`/`x-r4`/`x-r4o`。同一引擎还承担 TLS 证书链上报（`0xae8750a7`）、HTTP/2 peer principal 上报（`0x9701e74c`）、长连接下行消息（`0xb20a0be3`）、定位上报（`0x2f036831`）、传感器注册（`0xcf7db9ff`）、前台状态（`0xc23a168e`）、动态代理转发（`0x2ad1c199`）。

**执行集度量**：`a()` 是**单个 FDE 覆盖的 181 732 字节 CFF 巨函数**（按符号归因会得到 `distinct_fn = 1` 的假象）。31 个操作码的静态可达块集合**两两 Jaccard 全部 < 0.84**（465 组合无一达 0.98），动态执行集**共有 2471 条指令**、并集 43 258 条。分组 1/2 的 90–198 条"专属指令"经**逐条反汇编证明是 CFF 调度胶水**（21 份 20–22 条的独立调度副本，算术原语仅 1 条 `eor`），**不是算法、不需要 lift**。

**`libtiny.so` 内联 X25519**：标准密码学成分**不止**"MD5 骨架 / Base64 / CRC32"——库里还有一套**内联的 Curve25519 域运算**（`0x525000`–`0x531e4c`，入口 `0x525024`），它**既无加密指令、也无已知算法常量**，因此指令扫描与常量扫描两条判据都测不到它。四条独立结构证据：radix-2⁵¹ 肢体掩码 `0x7ffffffffffff` **178 处**（全文件精确计数亦为 178，只出现在这一段）、51 位进位提取 `extr #51` **130 处**、×19 归约 **18 处**、**a24 = 121666**（`0x527db0`），加上 `0x527fb0`/`0x527fc4` 处与 **RFC 7748 逐位吻合**的标量钳位（`k[0]&=248`、`k[31]=(k[31]&0x3f)|0x40`）。Ed25519 的 `d` 两个半字立即数与 `121665` 在库内**各 0 次命中**，故为 X25519 规模的 Montgomery 阶梯而非签名。执行该区的**只有 2 个操作码**（`0x3c6d0ac1`、`0xae821439`，均为 SDK 初始化路径），其余 29 个在该区覆盖为 0。据此 §1.2 判定口径**新增"大整数域特征"一条**。

**`libtiny.so` 字符串加密闭式还原**：两个解码器 `0x18c940`（203 次调用）与 `0x18d5e4`（118 次）均为**20 项调度表的逐字节双射**（`ROL8(x,rot)` 后 `^k` 或 `+k`，无扩散），逐位置 256 值穷举拟合 **36/36 成功**。按惯用式静态取 `(密文,长度)` 后**320 个调用点解出 312 条纯可打印明文**（另 3 条含换行、5 条为数据/误报，**闭环率 100%**）。解出的内容披露了四个上报端点（`/api/v1/{register,cfg,prb,dvf/vab}/android`）、两套 SDK 配置 JSON、反调试文案（`TracerPid:`、`detect tracer …`、`GetUntrustedIPackageManager`）、一组 **ART 内部符号名**（`_ZN3art2gc9collector17ConcurrentCopying12MarkingPhaseEv` 等），以及**内嵌 Lua 解释器**的完整标识符集（`pcall`/`setmetatable`/元表运算符/协程/`popen`）。

**全应用混淆形态普查**：把要求从"无未分析加密"扩到"**无未分析的被混淆代码**"，对 164 个 `.so` 逐库量测 8 项指标（`re/obf_census.py`，164/164 成功）。**重度 CFF 只有 3 个库**（注意：这是"重度 CFF"计数，全应用"被混淆库"为 **4 个**——另有 1 个不含 CFF，见本段末）：`libtiny.so`（`br` 4.124%）、`libtinyd.so`（4.098%）、`libxyass.so`（3.400%）；第 4 名起全部 < 0.9%（列在其前的 3 个库总指令数 ≤ 85，属分母过小的假象）。`movz/movk` 密度（92.8 / 88.4 / 81.1）与分派表签名数（`libtiny.so` 849，第 2 名仅 130）提供两条独立佐证。**三者恰好就是本目录已逐层闭环的对象**。

**第 4 条判据与第 4 个混淆对象**：上列 8 项指标测的是**控制流平坦化**、**常量密度**与 **`.rodata` 熵**，对一个"**把 `.data` 字符串表逐字节 XOR、并把解密完全展开成直线代码**"的库**全部正常**。`libturingmfa.so`（腾讯 TuringFD/MFA）正是如此：`br_pct` 0.020、`tblsig` 0、`opaque_csel` 0、`.rodata` 熵 5.71，因此判据集必须含**第 4 条（加载期自解密数据表）**；用三台独立仪器（静态位点扫描 / Unicorn 跑 `.init_array` 比对前后字节 / 单字节 XOR 全扫）跑遍全 164 库，**只有它命中**：`.init_array[6]` → 解密器 `0x34d74`（5 207 条指令、**0 调用、0 入边、1 个 `ret`、完全展开**），密钥调度 `key_index = src_index mod 8`（348/348 条目验证，`src_index` 计及空串），解出 **348 个非空条目 / 325 条可打印明文**（另 68 个不可见空串 ⇒ 源列表 **416** 项）（OAID 厂商 AIDL 表、反模拟器路径、Binder 直取、`AES/GCM/NoPadding`+`HmacSHA256` 方法名等）。因此**全应用混淆面为 4 个库**，加密普查为 **33 带 / 131 不带**。详见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §1.6、§1.7。

**逐块算术 lift**（见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §5.6.9）：域运算区按 `ret` 边界拆为 **5 个内联域例程 + 1 段独立辅助（R1–R5 + R6）**；执行序实测证明 **95.7% 是直线代码**（2 915 508 / 3 045 452 条为 `addr+4` 连续步），435 个 distinct run 即全部判读对象。两层拆分守恒：**CFF 调度尾 1 347 / 域运算层 11 777 个执行地址**（合计 13 124）。

**154 个域原语**全部定名（`fe_add/carryfold` 48、`fe_reduce19` 47、`fe_sq` 20、`fe_mul` 14 等）并给出伪代码。九项密码学指纹守恒**逐项 EXACT**：`adds`/`adcs` 各 364、`mul` 514、`umulh` 390、`madd` 252、掩码 178、`extr #51` 130、`lsr #51` 63、×19 58（静态 = 执行覆盖，差值全 0，且逐例程求和一致）。188 个未执行槽位逐段定性，**0 个**含密码学指纹。

**Java/dex 侧混淆审计（§9）**：`@u5`/`@v5` 加密字段名 **519/519 闭式还原**（100% 合法 Java 标识符；`j4` 解出 `hashCode`/`toString`/`getClass`/`notify`/`notifyAll`/`wait`/`clone`/`equals` 全部 8 个 Object 方法）；Java 侧字符串解密器 **822 调用点（字节码精确）→ 811 个内联 (cipher,key) 对 → 811 全映射，0 未映射、0 矛盾**（五种定点变换 + 两种操作数顺序；11 个转发蹦床逐条列明，2 个硬站点按地址闭合）。**daemon dex 三层混淆完整审计**：字符串 141 调用点、`@x0` 116/116、`@w0` 77 站点、`v.<clinit>` 74 条明文、IPC 12 个命令（§9.7）。并**定名 `Petal` 的归属**——`PetalConfig` 是插件化框架配置（`PETAL_MODE` 为 `boolean false`，用作 RN/Weex 注解默认值），不是混淆器。

## 易混点对照（现状口径）

| 易混的说法 | 现状 |
| --- | --- |
| 定制 H 的压缩轮未复原 | **已完整恢复**，3 组向量 0 mismatch |
| `0x50010` 的算法“未定性” | **已定性**：64B payload + 20B key → 16B 输出；宏结构、常量集、8 块跳转表、CFF 机制均确认 |
| `0x50010` 区域“无 rodata 常量表引用” | **不成立**：寄存器精确扫描器发现 **29 个 `.rodata` 对象**被引用（16 张 16 字节序列表 + 13 个常量对象），并经 `.rodata` 读钩子确认为热路径数据；但区域内**无索引式查表**，这些表是常量向量池而非 S 盒 |
| 分派选择子由 payload 决定 ⇒ 算法随数据变化 | **前半不成立、后半成立**：主分派点选择子是状态机块索引（`ldrb w9,[x20]` + `and #3`）；**但整体控制流仍随输入变化**——实测 337 个间接目标（其中相邻 4 组 241 个）、间接跳转 283–2 861 次、指令数 4 889–42 708，8 个顶层块仅 2 个全样本必进。扩到 3 000 组输入（10 个输入族）后该集合**饱和**为 412 site / 767 边 / 417 目标，故控制流是有限的；选择层已分类为 FIXED 262 / BASE 69 / DATA 18 |
| 单 trace lift 可作为 `0x50010` 参考实现 | **不成立**：对齐对比宽度后跨输入 **4/4 mismatch**，仅覆盖其训练轨迹（因 110 个 site 出度 ≥ 2） |
| `0x50010` 的控制流“原理上无法 lift” | **不成立**：实测 3 000 组输入后转移关系**饱和**（412 site / 767 边 / 417 目标，末 1 000 组零增长），有限且已枚举；“213 表加载 / 291 索引加载”的分类源于不做 def-use 追踪的扫描器，不成立 |
| payload 单比特翻转“64/64 全部有影响、区间 49–75” | **不成立**：穷举 512 位后在 4 个基准上一致发现 **16 个结构性惰性位**（每 16 字节块偏移 3、7 的上位），有效熵 496/512，区间实为 **0–82**；48 样本量不足以覆盖死位 |
| `0x50010` “选择谓词未符号化” | **已闭环**：60 组输入单机分类得 **FIXED 262 / BASE 69 / DATA 18**；每 site 生产者唯一（362 单/0 多）；18 个 DATA 全为二路分支，条件为帧内状态字节，两候选目标均为立即数 |
| `libtiny.so` 0x754AC0 是加密 blob | **不成立**：五项引用扫描 0 命中；正确表述为“未发现被引用的高熵数据区” |
| 疑似 AES-ECB 魔数 `35 16 11` | 就是 RC4 密钥流前三字节 |
| `captcha_link` 端点 | 样本内**不存在**该字面端点；验证页 URL 来自服务端下发 |
| 上传用 Range | **否**：COS 整对象 PUT，Qiniu 走 SDK 分块 |
| `libtinyd.so` 靠 socket 与主进程通信 | **不成立**：38 个导入无网络符号，通道是无名管道 |
| `libtinyd.so` 明文含 `p6ro` | **不成立**：长度参数错配（4→2），正确值为 `am` |
| `libtinyd.so` 字符串算法是纯 XOR | **不成立**：位置相关的旋转+模加/XOR 双射，非线性 |
| Tiny 分组 1 的 17 个操作码是"参数形状受限的提前退出" | **不成立**：实测执行 3070 条指令，返回 0 是因结果写入随后被清零的栈槽 |
| Tiny 操作码"逐操作码语义未展开" | **不成立**：31/31 已定名（native/Java 双操作码空间 + dex 调用表达式），见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §5.6 |
| Tiny 90–198 条"专属指令 = 下一轮 lift 的最小充分目标" | **不成立**：逐条反汇编证明是 CFF 调度胶水（算术原语仅 1 条 `eor`），**不是算法**，见 §5.5.7 |

## 公开范围与边界

公开内容包括：APK/XAPK/SO 哈希、版本、类与方法路径、函数地址、反汇编片段、协议字段名、合成测试向量、聚合统计。

不公开：APK/SO/DEX 本体、反汇编全量文件、真实 key/token/sid/deviceId、真实请求或响应、服务端下发内容、可复现线上风控绕过的构造。

代码级结论已经完整归档。Tiny 域区逐块算术 lift 给出 154 个域原语和伪代码，九项指纹守恒逐项 EXACT（§5.6.9）；Java/dex 侧混淆面逐调用点映射完成（§9）。Tiny 分发层为 61 分派块 ↔ 61 谓词槽 ↔ 31 操作码三向双射、零冲突，61 个编译期跳转位移全部复现，并由全 31 操作码执行集（5 888–35 577 条，总和 246 772）验证。`libtinyd.so` 的唯一 `native` 注册目标和 4 字节状态通知协议、`libxyasf.so` 的 51 个叶子字段、`libturingmfa.so` 其余 8 个构造子的 23 函数/19 导入语义均已给出。服务端 `http_range_size` 与 CDN `Accept-Ranges` 是运行期配置/响应，不是客户端代码项。Cookie 作用由 API 客户端未装 `CookieJar` 直接确定，cookie 仅属 WebView/RN/第三方。

设备指纹一项属**已恢复**，见 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md)：该库未做字符串加密，采集面可静态穷举，无需 VM 级 lift。

另：全应用 164 个 arm64 `.so` 的 native 加密普查已完成（**33 带加密 / 131 不带**，见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §1.3、§1.6、§1.7）。
