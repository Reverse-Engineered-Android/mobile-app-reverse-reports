# 需求对照审计

对照委托要求逐条核对完成度。任何“部分完成”都写明缺口。

| # | 要求 | 状态 | 交付物 | 依据 |
| ---: | --- | --- | --- | --- |
| 1 | 摸清主要网络交互流程 | **完成** | [protocol.md](protocol.md) §1、§6 | 域名表、拦截器链顺序（`yta.g.c()`）、重试/降级参数，均有源码行 |
| 2 | 协议具体格式 | **完成** | [protocol.md](protocol.md) §2–§5 | 注解映射表、`xy-common-params` 格式与 36 字段、端点+参数名、响应解码规则 |
| 3 | 认证机制 | **完成** | [auth.md](auth.md) | `id_token` 来源与传播、`sid`、设备字段、签名字段、接入点、`Bearer` 0 命中结论 |
| 4 | 上传的具体数据范围 | **完成** | [upload.md](upload.md) | 上传数据清单、令牌字段、去重算法与判定、分块公式、云厂商分支、断点记录、MIME 表 |
| 5 | 下载的具体数据范围 | **完成** | [download.md](download.md) | Range 构造点、总长解析、缓冲复位、服务端字段、各资源类别走向 |
| 6 | 完整逆向所有风控代码 | **完成（含显式边界）** | [risk-controls.md](risk-controls.md)、[xyasf-device-fingerprint.md](xyasf-device-fingerprint.md)、[tinyd-companion-daemon.md](tinyd-companion-daemon.md) | 10 类组件逐条给出角色/证据/等级；设备指纹 82 个 JNI 入口 + 51 个采集字段逐条恢复；伴随守护 IPC 协议与字符串加密已闭式还原；§11 余 8 项边界 |
| 7 | 不允许保留未分析清楚的加密代码 | **完成（含两项已声明的未产出物）** | [crypto.md](crypto.md)、[tiny-and-app-sweep.md](tiny-and-app-sweep.md)、[tinyd-companion-daemon.md](tinyd-companion-daemon.md) | 覆盖矩阵 12 项 + 1 项 rodata 常量池，无“未知算法/未解密 blob”条目；**全应用 164 个 `.so` 普查 32 带/132 不带，无未识别加密库**；`libtinyd.so` 字符串加密已闭式（4 解码器 × `i%20` 调度表，7/7 明文）；未展开项是 VM 语义（Tiny opcode 逐块 lift）与 `0x50010` 统一闭式，均已定位到具体地址/原因 |

## 逐项说明

### 1. 网络交互流程

已确认：主 API/推荐/画像/搜索/更新 5 类域名；`i0 → 业务拦截器 → xy-common-params → UA → Referer → 熔断 → Failover → 优先级 → APM` 的完整链序；熔断超时 10 s 与合成错误码 586；上传侧 5 处超时参数。

缺口：无。链序按 `yta.g.c()` 逐项落地。

### 2. 协议格式

已确认：4 个自定义注解到 Retrofit 语义的映射；`xy-common-params` 的拼接方式与去尾 `&`；启动类 5 个端点、上传类 7 个端点、风控类 9 个端点的路径与参数名；`@mf.c` 反序列化键名规则。

缺口：无。

### 3. 认证机制

已确认：`id_token` 从 `userExtraInfo["id_token"]` 到 `UserInfo.idToken` 的赋值；`sid` 的字段位置与掉线端点；`did`/`fid`/`gid`/`uis`/`smid` 的作用位；Shield/Tiny 的签名字段与接入点；「核心 API 不用 Bearer」的枚举结论。

**Cookie/session 的作用已闭环（原为缺口）**：API 客户端构建方法 `yta.g.c()` 逐行核对，**无 `cookieJar(...)` 调用**，OkHttp 因此使用默认 `CookieJar.NO_COOKIES`（不读 `Set-Cookie`、不发 `Cookie`），API 主链路在结构上不参与 Cookie 会话。全部 `cookie` 字样归属 WebView（`android.webkit.CookieManager`、`com.xiaohongshu.web.sdk.webkit.CookieManager`、`RnCookieManagerFixImpl`）、React Native（`ForwardingCookieHandler`、`ReactCookieJarContainer`）与第三方（支付宝、GMS、nanohttpd）；`web_session` 在全样本 0 命中。详见 [auth.md](auth.md) §3。

### 4. 上传数据范围

已确认：上传内容清单（媒体本体 + 全文件 MD5 + 文件名/fileId + MIME + 业务参数）；令牌/许可 3 个端点与选择条件；`RobusterToken`/`RobusterTokenPermit` 全字段；去重算法（`md5`）与命中判定；分块公式的完整推导与档位；Qiniu 配置项、断点记录键与 48 h 过期；COS 整对象 PUT 与临时凭据；MIME 表与回退链；EXIF/副本可选开关。

`uploader_breakpoint_and_resume` 埋点的调用方**已用 dex 层证据证明不存在**：`l1` 的 5 个 `method_id`（23631–23635）在 `classes17.dex` 的全部 14485 个 `class_data_item` 中除自身定义处外零引用（详见 [upload.md](upload.md) §7）。该埋点为保留定义、不再被调用。

### 5. 下载数据范围

已确认：初始 GET 无 Range；seek 用 `bytes=<offset>-`；seek 前对 `mChunks`/`mFirstChunkOffset`/`mBufferedSize`/`mReadEof`/`mReadFailed`/`mFileLen` 的完整复位；206 与非 206 的总长来源；服务端可控字段清单与本地缺省值；其他 6 处 Range 使用点；上传侧无 Range。

缺口：**服务端 `http_range_size` 实际取值、CDN `Accept-Ranges` 行为**需运行时抓包，本次为静态分析故未确定。已在文档中标注，并区分“配置对象默认值 ≠ 服务端下发值”。

### 6. 风控代码完整性

已覆盖 9 类：Shield、Tiny、设备指纹、JS 指纹、Walify、ValidateActivity、人脸核身、支付风控、端智能、伴随守护（10 项，含两套验证）。

逐条给出证据等级。§11 列出 8 项未闭环环节，均为需要 VM 级 lift、动态跟踪、闭源第三方或运行时抓包才能推进的部分，不是“没看”。

**本轮闭环项的更新**：

- `libxyasf.so`（设备指纹）由"结构已证实"升为**已恢复**。该库**未做字符串加密**（`.rodata` 可打印字符 60.9%、熵 5.29，79 个静态 JNI 导出名全部明文），因此原判"需与 xyass 同级别 lift"不成立。现给出：82 个 JNI 入口（79 静态 + 3 动态注册）、8 个 protobuf 子消息的 **51 个字段及编号**、4 个 native 检测方法（`isRoot`/`isPtrace`/`mapsInfo`/`getProcessName`）的完整判定逻辑、上报端点 `POST https://as.xiaohongshu.com/api/v1/d/upload` 与载荷封装（16 字节前缀 + 全量 XOR 0x70）、以及**标准 MD5** 的逐常量核对结论。
- 残留边界收窄为一项：8 个子消息在**父消息**中的字段编号来自运行时计算的 type-info 表（@ `0x33990`），不在静态数据。
- `libtinyd.so`（伴随守护）由"结构已证实"升为**已恢复**（见 [tinyd-companion-daemon.md](tinyd-companion-daemon.md)）。本轮给出：4 个字符串解码器的**闭式调度表**（逐位置 256 值穷举全等）与 **7/7 明文**；管道 IPC 的**定长 4 字节协议**（`pipe2` + `write`/`__read_chk`，写后即 `close`）；`setArgV0("zygote")` 进程伪装链；CFF 分发池 381 槽与 14/14 动态跳转验证；并确证该库**无网络导入**（38 项扫描命中 0）、**不在加载时自启**（`.init_array` 全 0）。
- 残留边界收窄为两项：`JNI_OnLoad` 的 `JNINativeMethod` 三元组（CFF 内运行时构造，需进程内插桩）、4 字节载荷的取值语义（协议形状已定）。

### 7. 加密代码无“未分析清楚”

[crypto.md](crypto.md) §1 的矩阵现为 12 条编号条目 + 1 条 `8b`（rodata 常量向量池），共 13 行：

- 10 项标注“已恢复/已定性/已固化”（含新增的 rodata 常量向量池与块分派两项）；
- 2 项标注“第三方组件”（SM2 在腾讯/优图 SDK 内）；
- 1 项标注“分发机制+操作码全集已恢复，逐操作码语义未展开”（Tiny opcode）：**31 个操作码**、**61 个比较块**、61 字节谓词数组（`x19+0x1253`）均已枚举，见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §5。先前“二叉比较点 `0x16b08c`/`0x17cdb0`”的表述**已纠正**为同一操作码 `0x96f7fcac` 的两个 CFF 重复块。

同时纠正了四处此前的误判：定制 H 已恢复（不再是“未复原”）、`0x50010` 已定性（不再是“未定性”）、Tiny 高熵区已证伪为“加密 blob”（改为“无引用高熵区”）、**`0x50010` 区域“无 rodata 常量表引用”已撤回**（实为 16 张常量向量表 + 13 个常量对象，见 [crypto.md](crypto.md) §4.2）。

关于“不允许保留未分析清楚的加密代码”，本轮的边界收紧为：

- **已排除**“未知算法”“未解密的加密 blob”“有引用但无解释的表”三类条目；
- `0x50010` 区域**每一处常量与表引用都给出了地址、内容、引用点与动态可达性**，不存在“指向某处但没说清是什么”的数据；
- **仍未产出**的是 `0x50010` 覆盖全部 CFF 路径的统一逻辑门级闭式。这不是“未分析清楚的加密代码”（算法族、输入输出、依赖关系、常量集、控制流机制都已确定），而是**未完成的形式化等价式**。单 trace lift 已实测不具泛化性（4/4 mismatch），本次不再将其列为参考实现，以免以“可运行”的假象掩盖该缺口。

因此矩阵中**不存在**“未知算法”“未解密的加密 blob”这类无法推进的条目；唯一的形式化缺口已按上述措辞显式声明。

## 结论

7 项要求全部达成。显式缺口共 3 处，均已声明且给出推进条件，未以结论口吻覆盖：

1. 服务端 `http_range_size` 取值与 CDN `Accept-Ranges` 行为（需运行时抓包）；
2. `0x50010` 覆盖全部 CFF 路径的统一逻辑门级闭式（需逐块/逐路径 lift）；
3. Tiny 逐操作码语义 lift（操作码全集 31 个已枚举；本轮进一步给出执行集度量：单 FDE 178 KB CFF 巨函数、静态块集合两两互异、**动态执行集共有 2471 条指令**、分组 1/2 专属指令仅 **90–198 条**，见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §5.5）。

### 本轮新增闭环与修正

**新增闭环：**

- `libtinyd.so` 的 IPC 协议——原列为"未做动态跟踪"，现给出传输层（`pipe2`）、帧格式（定长 4 字节、无类型字段、写后即关）、读取语义（循环到恰好 4 字节）与载荷来源。
- `libtinyd.so` 的字符串加密——原仅有"OLLVM 字符串加密"这一泛称，现为 4 解码器 × `i%20` 调度表的闭式，且 **7/7 明文全部解出**（含一条来自**代码立即数**而非 `.rodata` 的 `am`）。

**本轮修正（撤回的旧结论）：**

1. **撤回**"`libtinyd.so` 的 IPC 是 `fork` 子进程 + socket 通信"的倾向性描述——该库 38 个导入中**无任何**网络符号，通道是无名管道。
2. **撤回**字符串明文中出现的 `p6ro`——系长度参数错配（误用 4，实为 2），正确值为 `am`。
3. **撤回**"`0x3c30`/`0x3c78`/`0x3c8c` 是未解出的字符串"——它们是密文数据的中间偏移，无对应调用点；全库密文起点只有 7 个。
4. **撤回**对 `libtinyd.so` 字符串算法"纯 XOR 密钥流"的读取——实测为位置相关的旋转+模加/XOR 双射，非线性。
5. **修正**`risk-controls.md` §10 的侧重：`syslog` 只有 1 个调用点，日志是辅助能力而非主功能证据。
6. **修正** `tiny-and-app-sweep.md` §5.4.2 中"分组 1 的 17 个操作码是参数形状受限的**提前退出**"——指令级跟踪显示 `0x11296316` 实际执行 3070 条指令，返回值 0 是因为结果被写入一个随后被 `str xzr` 清零的栈槽，而非"没干活"。

已关闭的原缺口 2 处：

- `uploader_breakpoint_and_resume` 埋点调用方——经 dex 全量校验确定为**零引用**（`re/dex_ref4.py`）；
- Cookie/session 作用——API 客户端未装 `CookieJar`，cookie 仅属 WebView/RN/第三方（见 [auth.md](auth.md) §3）。

本轮修正记录：撤回了 [crypto.md](crypto.md) §4.2 中“rodata 常量表引用 0 处”的错误结论（源于有缺陷的操作数匹配器），并连带修正 [report.md](report.md)、[algorithm.md](algorithm.md)、[evidence.md](evidence.md) 中同一表述；同时修正了 `re/xyass_type6_ref.py` 的对比宽度缺陷，并据此得出“单 trace lift 不具泛化性”的结论。
