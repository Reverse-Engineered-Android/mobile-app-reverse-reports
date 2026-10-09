# 需求对照审计

对照委托要求逐条核对完成度；每个结论同时给出可复核证据与适用边界。

| # | 要求 | 状态 | 交付物 | 依据 |
| ---: | --- | --- | --- | --- |
| 1 | 摸清主要网络交互流程 | **完成** | [protocol.md](protocol.md) §1、§6 | 域名表、拦截器链顺序（`yta.g.c()`）、重试/降级参数，均有源码行 |
| 2 | 协议具体格式 | **完成** | [protocol.md](protocol.md) §2–§5 | 注解映射表、`xy-common-params` 格式与 36 字段、端点+参数名、响应解码规则 |
| 3 | 认证机制 | **完成** | [auth.md](auth.md) | `id_token` 来源与传播、`sid`、设备字段、签名字段、接入点、`Bearer` 0 命中结论 |
| 4 | 上传的具体数据范围 | **完成** | [upload.md](upload.md) | 上传数据清单、令牌字段、去重算法与判定、分块公式、云厂商分支、断点记录、MIME 表 |
| 5 | 下载的具体数据范围 | **完成** | [download.md](download.md) | Range 构造点、总长解析、缓冲复位、服务端字段、各资源类别走向 |
| 6 | 完整逆向所有风控代码 | **完成** | [risk-controls.md](risk-controls.md)、[xyasf-device-fingerprint.md](xyasf-device-fingerprint.md)、[tinyd-companion-daemon.md](tinyd-companion-daemon.md)、[tiny-and-app-sweep.md](tiny-and-app-sweep.md) | 10 类组件逐条给出角色、入口、控制流、判定依据和证据等级；设备指纹 82 个 JNI 入口 + 51 个采集字段逐条恢复；伴随守护 IPC 协议与字符串加密已闭式还原；Tiny 风控引擎 31/31 操作码调用语义已定名（native/Java 双操作码空间，签名头生成链路闭式）；61 分派块 ↔ 61 个谓词槽 ↔ 31 个操作码常量三向双射、零冲突，61 个编译期跳转位移全部复现；154 个域原语 + 九项指纹守恒 EXACT；`@u5/@v5` 519/519、字符串解密 811/811 调用点全映射（0 未映射） |
| 7 | 不允许保留未分析清楚的加密代码 | **完成** | [crypto.md](crypto.md)、[tiny-and-app-sweep.md](tiny-and-app-sweep.md)、[tinyd-companion-daemon.md](tinyd-companion-daemon.md) | 覆盖矩阵 **13 项** + 1 项 rodata 常量池，无“未知算法/未解密 blob”条目；**全应用 164 个 `.so` 普查 33 带 / 131 不带，无未识别加密库**（判据四条：加密指令族、已知算法常量、大整数域特征、加载期自解密数据表；`libturingmfa.so` 只被第 4 条命中，见 §1.6、§1.7）；`libtinyd.so` 字符串加密已闭式（4 解码器 × `i%20` 调度表，7/7 明文）；**Tiny opcode 条目“已恢复”**（31/31 定名 + 签名链路闭式，§5.6）；**条目 13 = `libtiny.so` 内联 X25519 域运算**（既无加密指令也无已知常量，见 §2.2.1）；Tiny 域区逐块算术 lift **已完成**——435 个 distinct run 全判读、154 个域原语定名并给出伪代码、九项密码学指纹（`adds`/`adcs` 各 364、`mul` 514、`umulh` 390、`madd` 252、掩码 178、`extr #51` 130、`lsr #51` 63、×19 58）守恒**逐项 EXACT**，188 个未执行槽位逐段定性且 **0 个**含密码学指纹，见 §5.6.9 |
| 7b | 不允许留下任何**未分析的被混淆代码**（**不只密码学相关**） | **完成（native + Java 双面均已闭环）** | [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §1.5–§1.7、§2.4、§5.6.9、§9、§9.7；[crypto.md](crypto.md) §4.6、§5b | **native 面**：**164 库混淆普查**（`re/obf_census.py`，164/164 成功）——重度 CFF 仅 `libtiny.so`(br 4.124%)、`libtinyd.so`(4.098%)、`libxyass.so`(3.400%)，第 4 名起全部 < 0.9%，`movz/movk` 密度与 `tblsig` 两条独立佐证；该 8 指标对"无 CFF 的原地自解密表"全盲，故另用**第 4 条判据 + 3 台独立仪器对全 164 库各跑一遍**（`re/inplace_strdec_scan.py` 静态位点、`re/initarray_run.py` Unicorn 跑构造子比对前后字节、`re/xor_table_test.py` 单字节 XOR 全扫、`re/initarray_census.py` 构造子清点）——**只有 `libturingmfa.so` 命中**（`.init_array[6]` → `0x34d74`，5 207 条指令、0 调用、0 入边、1 个 `ret`、**完全展开**；密钥调度 `key_index = src_index mod 8`，348/348 条目验证；348 非空条目 + 68 空串 = 源列表 416 项；另在 `.bss` 建出 348 槽指针数组）；`libxyass.so`/`libtinyd.so` CFF 已闭环，`libtiny.so` CFF 结构已完全枚举（61 块 ↔ 61 槽 ↔ 31 常量三向双射、61 位移全复现）且逐块算术 lift 已完成（§5.6.9）；字符串混淆：`libtinyd.so` 7/7 明文、`libtiny.so` 320 调用点解出 312 条明文（§2.4）。**Java/dex 面**：`@u5`/`@v5` 加密字段名 **519/519 闭式还原**（100% 合法 Java 标识符，§9.2）；Java 侧字符串解密器 **822 调用点按字节码精确计数、811 个内联 (cipher,key) 对全部映射到明文（0 未映射、0 矛盾）**（§9.2b）；**daemon dex 三层混淆完整审计**——字符串 141 个解密器调用点（`c1.a` 136 + `d1.a` 2 + `r0$a.a` 3）、`@x0` 116/116、`@w0` 77、`v.<clinit>` 74 条明文、IPC 12 个命令（§9.7）；11 个反射包装器已枚举；类/包名短名属 R8 字典压缩而非名字加密；`PetalConfig` 是插件化框架配置（`PETAL_MODE = false`），非混淆器（§9.1、§9.5）。**扫描口径**：位点扫描用**有效地址区间相交 + 寄存器偏移/宽存储/post-index 全覆盖**（`re/inplace_xref_scan.py`、`re/inplace_xref_content.py`，164/164），`libturingmfa.so` 位点 348 个（348 个条目里 306 个用 `str q2,[x10,x9]` 这类「位移槽里是寄存器」的形态）；密钥规则为 `key_index = src_index mod 8`（`src_index` 计及空串，故源列表 416 项）；`.bss` 另有 348 槽指针数组（`re/tmfa_pointer_arrays.py`）——它才是代码索引表所用对象，其前 9 槽即 9 个前导常量单元；全应用收紧口径下：113 库有位点、非 `.bss` 目标 595 个，逐个定性后**仅 `libturingmfa.so` 为真表**，余 11 项高熵目标熵上界仅 4.11（真表文件原像整体熵 7.074）且所在库构造子**未**改写 `.data`，一律按 `high-entropy-no-constructor-change` 记载 |
| 8 | 追踪权限弹框 | **完成** | [privacy-and-permissions.md](../privacy-and-permissions.md) §1–§2 | 三 APK 权限并集 80 项；三层弹框（`PrivacyPolicyDialog` → `f72.c` 聚合说明 → `m82.m` 实际申请）；`setCancelable(false)`/`setCanceledOnTouchOutside(false)` 不可取消；`PhonePermissionHelperExtension` 为唯一真实申请者与节流点 |
| 9 | 是否未经告知或超范围收集数据 | **完成（含显式边界）** | [privacy-and-permissions.md](../privacy-and-permissions.md) §5–§6 | 8 项逐条给出判定依据：WLAN MAC 双轨实现（访问器 `"<absent>"` vs `q.i()` 真实读取并入 `mac` 公共参数）、按卡槽 IMEI/MEID、剪贴板带调用方归因、相册位置与 EXIF、通用 `ContentResolver`、已装应用列表四种取法、`libxyasf.so` 图片容器伪装、基础模式仍上报、`traceIMEI()` 空实现 |
| 10 | 隐私协议措辞 vs 实际收集/上传范围 | **完成（含显式边界）** | [privacy-and-permissions.md](../privacy-and-permissions.md) §7 | 28 条隐私资源、9 条 H5 正文 URL；协议正文不在样本内（边界）；三处措辞落差：媒体位置、广告用途仅英文、泛化“上网记录” |
| 11 | 内部隐私合规框架（附带发现） | **完成** | [privacy-and-permissions.md](../privacy-and-permissions.md) §3–§4 | 55 个五位数敏感 API 号 + 148 个 AppOps 索引全部定名并映射出口类；`xt9.d.a` 逐层控制流、超频计数与 APM/Sentry 双路采样上报；位置双层门控受远端开关 `andr_enable_coarse_location_check`（默认 0）控制 |

## 逐项说明

### 1. 网络交互流程

已确认：主 API/推荐/画像/搜索/更新 5 类域名；`i0 → 业务拦截器 → xy-common-params → UA → Referer → 熔断 → Failover → 优先级 → APM` 的完整链序；熔断超时 10 s 与合成错误码 586；上传侧 5 处超时参数。

边界：无。链序按 `yta.g.c()` 逐项落地。

### 2. 协议格式

已确认：4 个自定义注解到 Retrofit 语义的映射；`xy-common-params` 的拼接方式与去尾 `&`；启动类 5 个端点、上传类 7 个端点、风控类 9 个端点的路径与参数名；`@mf.c` 反序列化键名规则。

边界：无。

### 3. 认证机制

已确认：`id_token` 从 `userExtraInfo["id_token"]` 到 `UserInfo.idToken` 的赋值；`sid` 的字段位置与掉线端点；`did`/`fid`/`gid`/`uis`/`smid` 的作用位；Shield/Tiny 的签名字段与接入点；「核心 API 不用 Bearer」的枚举结论。

**Cookie/session 的作用**：API 客户端构建方法 `yta.g.c()` 逐行核对，**无 `cookieJar(...)` 调用**，OkHttp 因此使用默认 `CookieJar.NO_COOKIES`（不读 `Set-Cookie`、不发 `Cookie`），API 主链路在结构上不参与 Cookie 会话。全部 `cookie` 字样归属 WebView（`android.webkit.CookieManager`、`com.xiaohongshu.web.sdk.webkit.CookieManager`、`RnCookieManagerFixImpl`）、React Native（`ForwardingCookieHandler`、`ReactCookieJarContainer`）与第三方（支付宝、GMS、nanohttpd）；`web_session` 在全样本 0 命中。详见 [auth.md](auth.md) §3。

### 4. 上传数据范围

已确认：上传内容清单（媒体本体 + 全文件 MD5 + 文件名/fileId + MIME + 业务参数）；令牌/许可 3 个端点与选择条件；`RobusterToken`/`RobusterTokenPermit` 全字段；去重算法（`md5`）与命中判定；分块公式的完整推导与档位；Qiniu 配置项、断点记录键与 48 h 过期；COS 整对象 PUT 与临时凭据；MIME 表与回退链；EXIF/副本可选开关。

`uploader_breakpoint_and_resume` 埋点的调用方**已用 dex 层证据证明不存在**：`l1` 的 5 个 `method_id`（23631–23635）在 `classes17.dex` 的全部 14485 个 `class_data_item` 中除自身定义处外零引用（详见 [upload.md](upload.md) §7）。该埋点为保留定义、不再被调用。

### 5. 下载数据范围

已确认：初始 GET 无 Range；seek 用 `bytes=<offset>-`；seek 前对 `mChunks`/`mFirstChunkOffset`/`mBufferedSize`/`mReadEof`/`mReadFailed`/`mFileLen` 的完整复位；206 与非 206 的总长来源；服务端可控字段清单与本地缺省值；其他 6 处 Range 使用点；上传侧无 Range。

边界：客户端静态缺省为 `http_range_size=0`、`enable_dynamic_range=true`；服务端实际下发值和 CDN 响应属性是运行期数据，[download.md](download.md) 已把配置输入、客户端行为和服务端响应三层分开。

### 6. 风控代码完整性

已覆盖 9 类：Shield、Tiny、设备指纹、JS 指纹、Walify、ValidateActivity、人脸核身、支付风控、端智能、伴随守护（10 项，含两套验证）。

逐条给出证据等级。§11 共列 **16 行**：每行都区分代码语义、运行期输入和服务端响应，且均给出地址、调用点或数据流依据；不存在只给状态、不给机制的条目。

**已闭环项**：

- `libxyasf.so`（设备指纹）**已恢复**。该库**未做字符串加密**（`.rodata` 可打印字符 60.9%、熵 5.29，79 个静态 JNI 导出名全部明文），静态导出即可读出全部采集面，无需 VM 级 lift。已给出：82 个 JNI 入口（79 静态 + 3 动态注册）、8 个 protobuf 子消息的 **51 个字段及编号**、4 个 native 检测方法（`isRoot`/`isPtrace`/`mapsInfo`/`getProcessName`）的完整判定逻辑、上报端点 `POST https://as.xiaohongshu.com/api/v1/d/upload` 与载荷封装（16 字节前缀 + 全量 XOR 0x70）、以及**标准 MD5** 的逐常量核对结论。
- `libxyasf.so` 的父消息封装由 `0x33990` 通用分发器从运行时 type-info 取父层标签；8 个子消息与 51 个叶子字段的完整 schema 已按数据结构给出，不把运行期表误写成静态常量。
- `libtinyd.so`（伴随守护）**已恢复**（见 [tinyd-companion-daemon.md](tinyd-companion-daemon.md)）。已给出：4 个字符串解码器的**闭式调度表**（逐位置 256 值穷举全等）与 **7/7 明文**；管道 IPC 的**定长 4 字节协议**（`pipe2` + `write`/`__read_chk`，写后即 `close`）；`setArgV0("zygote")` 进程伪装链；CFF 分发池 381 槽与 14/14 动态跳转验证；并确证该库**无网络导入**（38 项扫描命中 0）、**不在加载时自启**（`.init_array` 全 0）。
- `libtinyd.so` 的 `JNINativeMethod` 注册目标由 daemon dex 唯一 `native` 方法唯一确定；4 字节载荷的长度、字节序、来源字段、读写循环、接收者与通知用途均已枚举。

### 7. 加密代码无“未分析清楚”

[crypto.md](crypto.md) §1 的矩阵为 13 条编号条目 + 1 条 `8b`（rodata 常量向量池），共 14 行：

- 13 项标注“已恢复/已定性/已固化”（含 rodata 常量向量池与块分派两项）；
- 1 项标注“第三方组件”（SM2 在腾讯/优图 SDK 内）；
- **0 项**标注“逐操作码语义未展开”。

`libtiny.so`（矩阵条目 12）的状态为**“已恢复”**：**31/31 操作码全部定名**——引擎是 `com.xingin.tiny.internal.t` 的 **native(31) + Java switch(71) 双操作码空间**，两空间**交集为 0**；29 个 native 操作码在 dex 里定位到**确切的调用表达式**（含参数类型与个数），其中 `0x96f7fcac` = **OkHttp 请求签名头生成**（`nlb.p` → `yya.f.e`），见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §5.6。

- 21 个 native 操作码的“专属指令”（90–198 条）经逐条反汇编**判定为 CFF 调度胶水**（21 份 20–22 条调度副本；算术原语仅 1 条 `eor`），**不是算法**（§5.5.7）；另 2 个操作码（`0x398bf05d`、`0xf3f89a2a`）的常量**不在任何 dex 中**，属纯 native 路径，已给出精确地址与执行规模（§5.6.5）。
- **31/31 操作码常量精确恢复**：从字段加载点前向找 `cmp`、从 `cmp` 反向沿 `mov`/`movk` 链解析，61/61 分派块全部对上（§5.6.6(a)）。
- **分发层完全闭环**：谓词数组写入点**独立枚举**（68 个站点）→ 61 个分派节点**逐一命中**其中 61 个槽位，恰好填满 `0x1253`–`0x128f`（**零冲突**）；配合常量半字普查（唯一单块操作码 `0x96d0a479` 是结论而非缺口）与 61 个**编译期跳转位移**的复现，构成 61 分派块 ↔ 61 谓词槽 ↔ 31 操作码的**三向双射**（§5.6.6(b)(d)(e)、§5.6.7）。谓词数组恰好 61 字节且被完全占满，**第 62 个分派块在结构上不可能存在**而不引发冲突。
- **口径声明**：Tiny 侧由静态全段覆盖、全 31 操作码动态执行集（每个 5 888–35 577 条，总和 246 772，可复现）、61 分派块/61 谓词槽/31 操作码三向双射和域区逐块 lift 共同闭合；`libxyass.so` `0x50010` 另使用 3000 组输入饱和扫描（§5.6.8–§5.6.9）。
- `0x16b08c`/`0x17cdb0` 即操作码 `0x96f7fcac` 的两个 CFF 重复块（§4）。

关于“不允许保留未分析清楚的加密代码”，边界定义如下：

- **已排除**“未知算法”“未解密的加密 blob”“有引用但无解释的表”三类条目；
- `0x50010` 区域**每一处常量与表引用都给出了地址、内容、引用点与动态可达性**，不存在“指向某处但没说清是什么”的数据；该区确实引用 **16 张常量向量表 + 13 个常量对象**（见 [crypto.md](crypto.md) §4.2），但**无索引式查表**，它们是常量向量池而非 S 盒；
- **`0x50010` 控制流转移关系已完整枚举**——412 个可达间接分支 site、**767 条转移边**、417 个目标地址，由 3 000 组输入、**10 个互不相同的输入族**的饱和实测得到（最后 1 000 组增量为 **0**），落在 `0x50010`–`0x5a264` 之外的目标为 **0**、不齐目标为 **0**，单 site 出度 ≤ 16。完整转移表已落盘（`re/xyass_cfg_transitions_full.txt`）。
- **选择层已闭环**：按生产者操作数分类（60 组输入、单机）得 **FIXED 262 / BASE 69 / DATA 18**。用执行序最后写者追踪确认每个 site 的生产者**唯一**（362 单 / 0 多），即不存在“同一 site 在不同路径由不同指令算目标”。18 个 DATA site **全部**是严格二路分支，判定条件为帧内状态字节的 1 bit 或 1 次比较（例：`0x517ac` 的 `tst w11,#1`，`w11 = ldrb [x19,#0x31c]`），两个候选目标**均为编译期立即数**（`movz`/`movk`/`adrp`+`add`），**无任何运行期表查找**。
- **payload 惰性位**：payload 存在 **16 个结构性惰性位**（每 16 字节块内偏移 3、7 的上位；512 位穷举 ×4 基准结果一致），实际有效熵 **496/512**，单比特影响区间为 **0–82**（§4.7）。

`0x50010` 的控制流是标准 CFF 结构：`add Xd, base, wOff`，`base` 取自 `.data` 槽位，槽位在加载后由重定位确定且**跨实例逐字节一致**（确定性），只是文件镜像中为 0。因此它的转移关系可枚举、选择谓词也可闭环（§4.6.4）：选择谓词是帧内状态字节上的二路断言，两个后继均为静态立即数。

矩阵中**不存在**“未知算法”、“未解密的加密 blob”这类无法推进的条目：

1. `libxyass.so` `0x50010` 各 site 的转移选择谓词：**已闭环**（262 FIXED / 69 BASE / 18 DATA；转移图 412 site / 767 边 / 417 目标，见 [crypto.md](crypto.md) §4.6.4）；
2. `libtiny.so` 签名操作码（`0x96f7fcac` 等）**内部逐块算术步骤**：**已 lift**（见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §5.6.9）：域区 435 个 distinct run 全判读、154 个域原语定名并给出伪代码、九项密码学指纹守恒**逐项 EXACT**、188 个未执行槽位逐段定性且 **0 个**含密码学指纹。分发层本身（31 操作码 / 61 分派块 / 61 谓词槽 / 61 跳转位移）见同文 §5.6.6–§5.6.7。

**Java/dex 面**（按"**不只是密码学相关**"读）：`@u5`/`@v5` 加密字段名 **519/519 闭式还原**、Java 侧字符串解密器 **811/811 调用点全映射（0 未映射）**、daemon dex 三层混淆完整审计（§9.7）、11 个反射包装器枚举；`PetalConfig` 是插件化框架配置而非混淆器。详见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §9。

## 结论

委托的全部要求（网络交互、协议格式、认证机制、上传/下载范围、风控代码全量逆向、「不留未分析清楚的混淆代码」，以及权限弹框追踪、未告知/超范围收集调查、隐私协议措辞与实际范围对照）**全部达成**。客户端代码、数据格式与控制流已经逐项覆盖；服务端 `http_range_size` 取值与 CDN `Accept-Ranges` 作为运行期输入/响应单列，不构成客户端代码分析项。

### 已闭环项（按判据汇总）

- `0x50010` 各 site 的转移选择谓词：412 site / 767 边 / 417 目标；选择层 262 FIXED / 69 BASE / 18 DATA（[crypto.md](crypto.md) §4.6.4）。
- Tiny 分发层结构：61 分派块 ↔ 61 谓词槽 ↔ 31 操作码三向双射、零冲突；61 个编译期跳转位移全部复现（[tiny-and-app-sweep.md](tiny-and-app-sweep.md) §5.6.6–§5.6.7）。
- Tiny 域运算区逐块算术 lift：435 distinct run 全判读、154 域原语 + 伪代码、九项指纹守恒 EXACT、188 未执行槽位逐段定性（§5.6.9）。
- Java/dex 侧混淆面：`@u5/@v5` 519/519；字符串解密器 811/811 调用点全映射、0 未映射；daemon dex 三层混淆完整审计；11 个反射包装器枚举（§9）。
- `libtinyd.so` 父进程侧接收者：`dex/assets/fd2x1e4e2x3f1v2b1s.dex` 的 `com.xingin.tiny.daemon.e.main` → `l.a()`（12 个 IPC case）（[tinyd-companion-daemon.md](tinyd-companion-daemon.md) §10.3）。
- `libtinyd.so` 的 `JNINativeMethod` 注册目标：daemon dex 全库只声明一个 `native` 方法（§10.3.1）。
- 内嵌 dex 归属：`fd2x1e4e2x3f1v2b1s.dex` 是 Java 侧守护进程；`c4d121c215evx1s51d.dex`(940 B) 与 588 B 内嵌 dex 都是单类 `La;` 的 R8 反射蹦床（[tiny-and-app-sweep.md](tiny-and-app-sweep.md) §9.7）。
- Java 侧字符串加密计量：字节码精确口径 822 调用点 → 811 内联对 → 811 全映射、0 未映射（§9.2b）。
- `libturingmfa.so` 密钥调度：`key_index = src_index mod 8`（`src_index` 计及空串），348/348 条目验证。
- `libturingmfa.so` 的 9 个前导常量单元消费者：构造子在 `.bss` 建出 348 槽指针数组（`0x59458`–`0x59f38`），前 9 槽正是这 9 个单元（§1.7(i)）。
- `uploader_breakpoint_and_resume` 埋点调用方：dex 全量校验为零引用（`re/dex_ref4.py`）。
- Cookie/session 作用：API 客户端未装 `CookieJar`，cookie 仅属 WebView/RN/第三方（[auth.md](auth.md) §3）。

### 结论强度与判据边界

**方法论**：`libtiny.so` 内联 X25519 与 `libturingmfa.so` 自解密表是同一类问题的两次出现——**"N 条判据的并集"不证明"不存在"**，只证明"在该判据集下未命中"。本审计的全量否定结论均附判据清单与证据等级。

**证据等级说明**：

1. 加载期自解密表按三类结构性排除：**62 个库无 `.init_array` 条目**、**11 个库实测构造子跑完且 `.data`/`.rodata` 逐字节一致**、**91 个库的构造子静态反汇编不含自解密写入链**。另有静态位点、XOR 表穷举和构造子 census 三类全库仪器；唯一自解密命中为 `libturingmfa.so`，其 416 项链表已完整还原。
2. `libturingmfa.so` 自身 `.init_array` 的其余 8 个构造子（`0xdaf4`/`0x15120`/`0x2abfc`/`0x2c590`/`0x2e588`/`0x34c34`/`0x4cc1c`/`0x4d174`）按 FDE 边界反汇编，并覆盖内部调用与注册析构目标共 23 函数 / 19 导入；密码学指令 0、写 `.data`/`.data.rel.ro`/`.rodata` 目标 0（[risk-controls.md](risk-controls.md) §7）。
3. `libxyasf.so` 的父层标签由 `0x33990` 的运行时 type-info 取得；51 个叶子字段编号由各自序列化器直接给出，父层按该通用分发器的数据结构描述。
4. Tiny 侧结论由静态全段覆盖、全 31 操作码动态执行集（5 888–35 577 条，总和 246 772）和 61 分派块/61 谓词槽/31 操作码三向双射共同构成。
5. CFF 调度尾的 1 347 个执行地址不是算法或密钥：其结构量为 61 块 ↔ 61 槽 ↔ 31 常量三向双射，61 个跳转位移全部复现，“语义”即跳转目标集合，已完全枚举。
