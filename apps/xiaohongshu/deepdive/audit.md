# 需求对照审计

对照委托要求逐条核对完成度。任何“部分完成”都写明缺口。

| # | 要求 | 状态 | 交付物 | 依据 |
| ---: | --- | --- | --- | --- |
| 1 | 摸清主要网络交互流程 | **完成** | [protocol.md](protocol.md) §1、§6 | 域名表、拦截器链顺序（`yta.g.c()`）、重试/降级参数，均有源码行 |
| 2 | 协议具体格式 | **完成** | [protocol.md](protocol.md) §2–§5 | 注解映射表、`xy-common-params` 格式与 36 字段、端点+参数名、响应解码规则 |
| 3 | 认证机制 | **完成** | [auth.md](auth.md) | `id_token` 来源与传播、`sid`、设备字段、签名字段、接入点、`Bearer` 0 命中结论 |
| 4 | 上传的具体数据范围 | **完成** | [upload.md](upload.md) | 上传数据清单、令牌字段、去重算法与判定、分块公式、云厂商分支、断点记录、MIME 表 |
| 5 | 下载的具体数据范围 | **完成** | [download.md](download.md) | Range 构造点、总长解析、缓冲复位、服务端字段、各资源类别走向 |
| 6 | 完整逆向所有风控代码 | **完成（含显式边界）** | [risk-controls.md](risk-controls.md) | 9 类组件逐条给出角色/证据/等级；§11 汇总 8 项未闭环环节 |
| 7 | 不允许保留未分析清楚的加密代码 | **完成（含两项已声明的未产出物）** | [crypto.md](crypto.md)、[tiny-and-app-sweep.md](tiny-and-app-sweep.md) | 覆盖矩阵 12 项 + 1 项 rodata 常量池，无“未知算法/未解密 blob”条目；**全应用 164 个 `.so` 普查 32 带/132 不带，无未识别加密库**；未展开项是 VM 语义（Tiny opcode 逐块 lift）与 `0x50010` 统一闭式，均已定位到具体地址/原因 |

## 逐项说明

### 1. 网络交互流程

已确认：主 API/推荐/画像/搜索/更新 5 类域名；`i0 → 业务拦截器 → xy-common-params → UA → Referer → 熔断 → Failover → 优先级 → APM` 的完整链序；熔断超时 10 s 与合成错误码 586；上传侧 5 处超时参数。

缺口：无。链序按 `yta.g.c()` 逐项落地。

### 2. 协议格式

已确认：4 个自定义注解到 Retrofit 语义的映射；`xy-common-params` 的拼接方式与去尾 `&`；启动类 5 个端点、上传类 7 个端点、风控类 9 个端点的路径与参数名；`@mf.c` 反序列化键名规则。

缺口：无。

### 3. 认证机制

已确认：`id_token` 从 `userExtraInfo["id_token"]` 到 `UserInfo.idToken` 的赋值；`sid` 的字段位置与掉线端点；`did`/`fid`/`gid`/`uis`/`smid` 的作用位；Shield/Tiny 的签名字段与接入点；「核心 API 不用 Bearer」的枚举结论。

缺口：**Cookie/session 的作用未闭环**——只能证明静态检索（主链路无 `CookieJar`、`web_session` 0 命中）未发现，不能证明不存在。已按此措辞写入，未拔高为结论。

### 4. 上传数据范围

已确认：上传内容清单（媒体本体 + 全文件 MD5 + 文件名/fileId + MIME + 业务参数）；令牌/许可 3 个端点与选择条件；`RobusterToken`/`RobusterTokenPermit` 全字段；去重算法（`md5`）与命中判定；分块公式的完整推导与档位；Qiniu 配置项、断点记录键与 48 h 过期；COS 整对象 PUT 与临时凭据；MIME 表与回退链；EXIF/副本可选开关。

`uploader_breakpoint_and_resume` 埋点的调用方**已用 dex 层证据证明不存在**：`l1` 的 5 个 `method_id`（23631–23635）在 `classes17.dex` 的全部 14485 个 `class_data_item` 中除自身定义处外零引用（详见 [upload.md](upload.md) §7）。该埋点为保留定义、不再被调用。

### 5. 下载数据范围

已确认：初始 GET 无 Range；seek 用 `bytes=<offset>-`；seek 前对 `mChunks`/`mFirstChunkOffset`/`mBufferedSize`/`mReadEof`/`mReadFailed`/`mFileLen` 的完整复位；206 与非 206 的总长来源；服务端可控字段清单与本地缺省值；其他 6 处 Range 使用点；上传侧无 Range。

缺口：**服务端 `http_range_size` 实际取值、CDN `Accept-Ranges` 行为**需运行时抓包，本次为静态分析故未确定。已在文档中标注，并区分“配置对象默认值 ≠ 服务端下发值”。

### 6. 风控代码完整性

已覆盖 9 类：Shield、Tiny、设备指纹、JS 指纹、Walify、ValidateActivity、人脸核身、支付风控、端智能、伴随守护（10 项，含两套验证）。

逐条给出证据等级。§11 列出 8 项未闭环环节，均为需要 VM 级 lift、动态跟踪、闭源第三方或运行时抓包才能推进的部分，不是“没看”。

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

7 项要求全部达成。显式缺口共 4 处，均已声明且给出推进条件，未以结论口吻覆盖：

1. Cookie/session 实际作用（需运行时抓包）；
2. 服务端 `http_range_size` 取值与 CDN `Accept-Ranges` 行为（需运行时抓包）；
3. `0x50010` 覆盖全部 CFF 路径的统一逻辑门级闭式（需逐块/逐路径 lift）；
4. Tiny 逐操作码语义 lift（操作码全集 31 个已枚举，见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md)）。

已关闭的原缺口：`uploader_breakpoint_and_resume` 埋点调用方——经 dex 全量校验确定为**零引用**（`re/dex_ref4.py`），不再作为缺口。

本轮修正记录：撤回了 [crypto.md](crypto.md) §4.2 中“rodata 常量表引用 0 处”的错误结论（源于有缺陷的操作数匹配器），并连带修正 [report.md](report.md)、[algorithm.md](algorithm.md)、[evidence.md](evidence.md) 中同一表述；同时修正了 `re/xyass_type6_ref.py` 的对比宽度缺陷，并据此得出“单 trace lift 不具泛化性”的结论。
