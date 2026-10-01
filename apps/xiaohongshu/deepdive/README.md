# 小红书 9.37.0 原生加密与传输深挖

本目录是 [上层报告](../README.md) 的**独立补充**，不是它的替代。上层文件覆盖网络协议全景、Mars STN 长连接、本地数据库解密与跨版本存储对照；本目录覆盖**原生加密算法的逐字节恢复与传输链路的逐点定位**。

两者文件集不同、结论互补，均应保留。

## 本目录覆盖

- 网络交互流程与协议格式：域名表、拦截器链顺序、`xy-common-params` 36 字段与拼接格式、Retrofit 注解混淆到 GET/POST/Query/Field 的映射、端点与参数名。
- 认证机制：`id_token` 来源与传播、`sid`、设备字段、Shield/Tiny 签名字段与接入点。
- 上传数据范围：permit/quick-upload-check 端点、全文件 MD5 去重、分块公式、Qiniu/COS 分支、断点记录、MIME 表、令牌全字段。
- 下载数据范围：Range 构造点与复位、206/`Content-Range` 解析、服务端可控字段、其他 Range 使用点。
- 加密算法恢复：RC4 外层（逐字节验证）、定制 HMAC-H（含 64 轮调度，3 向量 0 mismatch）、`0x50010` type 6/7 变换（常量池、依赖矩阵、确定性向量、**CFF 转移图 412 site / 767 边 / 417 目标的饱和枚举**、**选择层 262 FIXED / 69 BASE / 18 DATA 分类**、payload 16 个结构性惰性位）。
- **`libtiny.so` 内联 X25519 域运算**（`0x525000`–`0x531e4c`，radix-2⁵¹ + ×19 归约 + a24=121666 + RFC 7748 钳位；据此新增"大整数域特征"判据）。
- **`libtiny.so` 字符串加密闭式还原**：两个解码器（`0x18c940` / `0x18d5e4`）均为 20 项调度表的逐字节双射，**320 个调用点解出 312 条明文**（含 4 个上报端点、SDK 配置 JSON、ART 内部符号名、内嵌 Lua 解释器标识符）。
- **全应用混淆形态普查**：164 个 `.so` 逐库量测（`br_pct` / 分派表签名 / `movz-movk` 密度 / 字符串解密循环 / rodata 熵）；重度 CFF **仅 3 个库**（`libtiny.so` 4.124%、`libtinyd.so` 4.098%、`libxyass.so` 3.400%），第 4 名起全部 < 0.9%。
- 风控组件清单与证据等级。
- `libxyasf.so` 设备指纹：82 个 JNI 入口、8 个 protobuf 子消息 51 字段（含编号）、4 个 native 检测方法判定、`as.xiaohongshu.com` 上报端点与载荷封装、标准 MD5 核对。
- `libtinyd.so` 伴随守护：字符串加密闭式还原（4 解码器 × `i%20` 调度表，7/7 明文）、管道 IPC 定长 4 字节协议、`setArgV0("zygote")` 进程伪装链、CFF 分发池与 14/14 跳转验证。
- **Tiny 引擎 31/31 操作码定名**：`native(31) + Java switch(71)` 双操作码空间（交集为 0）、31/31 常量精确恢复、29 个操作码定位到 dex 调用表达式、**OkHttp 请求签名链路闭式**（`0x96f7fcac`）、TLS 证书链/长连接/定位/传感器上报路径。
- Tiny 引擎执行集度量：单 FDE 178 KB CFF 巨函数、31 个操作码静态块集合两两互异、动态执行集共有 2471 条指令、分组 1/2 的 90–198 条"专属指令"经逐条反汇编**证明为 CFF 调度胶水而非算法**；**全 31 操作码确定性执行集**（5 888–35 577 条，总和 246 772）。
- **CFF 逐块 lift 已完成**：域运算区拆为 5 个内联域例程 + 1 段独立辅助（R1–R5 + R6）；执行序实测证明 95.7% 是直线代码（435 个 distinct run 全判读），**154 个域原语**定名并给出伪代码，九项密码学指纹守恒**逐项 EXACT**（`adds`/`adcs` 各 364、`mul` 514、`umulh` 390、`madd` 252、掩码 178、`extr #51` 130、`lsr #51` 63、×19 58），188 个未执行槽位逐段定性且 0 个含密码学指纹（§5.6.9）。
- **Java/dex 侧混淆审计**（§9）：`@u5`/`@v5` 加密字段名 **519/519 闭式还原**（100% 合法 Java 标识符）、Java 侧字符串解密器 **822 调用点按字节码精确计数、811 个内联 (cipher,key) 对全部映射到明文（0 未映射、0 矛盾）**、11 个反射包装器枚举、**daemon dex 三层混淆完整审计**（IPC 12 个命令 + `@x0` 116/116 + `@w0` 77 + `v.<clinit>` 74 条明文 + 588 B 内嵌 dex 定性）；并**定名 `Petal` 的归属**——`PetalConfig` 是插件化框架配置（`PETAL_MODE = false`），不是混淆器；`fvc` 注解包是通用 HTTP 注解而非名字加密。
- 需求对照审计。
- 权限与隐私面归上层 [privacy-and-permissions.md](../privacy-and-permissions.md)：权限清单与弹框链路、内部隐私合规框架、声明与实际采集对照、隐私协议措辞对照。

## 入口

- [深挖报告](report.md) — 总览与关键结论
- [网络协议格式](protocol.md)
- [认证机制](auth.md)
- [上传链路与数据范围](upload.md)
- [下载链路与 Range 范围](download.md)
- [加密代码覆盖矩阵](crypto.md)
- [风控组件清单](risk-controls.md)
- [全应用加密普查与 libtiny.so 深挖](tiny-and-app-sweep.md)
- [libxyasf.so 设备指纹深挖](xyasf-device-fingerprint.md)
- [libtinyd.so 伴随守护深挖](tinyd-companion-daemon.md)
- [算法深挖](algorithm.md)
- [深挖证据索引](evidence.md)
- [需求对照审计](audit.md)

## 与上层报告的关系

| 主题 | 上层文件 | 本目录文件 |
| --- | --- | --- |
| 网络协议 | [network.md](../network.md) | [protocol.md](protocol.md) |
| 上传下载 | [transfer.md](../transfer.md) | [upload.md](upload.md)、[download.md](download.md) |
| 本地存储与解密 | [storage.md](../storage.md) | —（本目录不含存储） |
| 风控 | [risk.md](../risk.md) | [risk-controls.md](risk-controls.md) |
| 算法边界 | [algorithm.md](../algorithm.md) | [algorithm.md](algorithm.md)、[crypto.md](crypto.md) |
| 证据 | [evidence.md](../evidence.md) | [evidence.md](evidence.md) |
| 完成度 | [completeness.md](../completeness.md) | [audit.md](audit.md) |
| 全应用 native 加密普查 | —（上层未覆盖） | [tiny-and-app-sweep.md](tiny-and-app-sweep.md) |
| 权限弹框与数据采集 | [privacy-and-permissions.md](../privacy-and-permissions.md) | —（本目录不含权限面） |

## 口径差异（需注意）

两批文件的分析批次不同，个别说法并不一致，按以下原则阅读：

- **原生算法**以本目录为准：`crypto.md` 给出被引用的 rodata 对象逐个清单 + 动态可达性验证（16 张常量向量表 + 13 个常量对象）。
- **本地存储**以上层为准：本目录未做存储分析。
- **Mars STN / protobuf**以上层为准：本目录未覆盖长连接 wire 格式。
- `crypto.md` 的结论以本目录为准：type 6/7 变换已定性并固化向量，CFF 转移图完整枚举并饱和（412 site / 767 边 / 417 目标），**选择层亦已闭环**（262 FIXED / 69 BASE / 18 DATA，见 `crypto.md` §4.6）。
- **全应用普查**以本目录为准：`tiny-and-app-sweep.md` 覆盖 164 个 arm64 `.so`（**33 带加密 / 131 不带**，见该文 §1.3、§1.6、§1.7），上层未做此项。

base APK SHA-256：`0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c`。
