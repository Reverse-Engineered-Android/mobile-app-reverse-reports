# 小红书 9.37.0 原生加密与传输深挖

本目录是 [上层报告](../README.md) 的**独立补充**，不是它的替代。上层文件覆盖网络协议全景、Mars STN 长连接、本地数据库解密与跨版本存储对照；本目录覆盖**原生加密算法的逐字节恢复与传输链路的逐点定位**。

两者文件集不同、结论互补，均应保留。

## 本目录覆盖

- 网络交互流程与协议格式：域名表、拦截器链顺序、`xy-common-params` 36 字段与拼接格式、Retrofit 注解混淆到 GET/POST/Query/Field 的映射、端点与参数名。
- 认证机制：`id_token` 来源与传播、`sid`、设备字段、Shield/Tiny 签名字段与接入点。
- 上传数据范围：permit/quick-upload-check 端点、全文件 MD5 去重、分块公式、Qiniu/COS 分支、断点记录、MIME 表、令牌全字段。
- 下载数据范围：Range 构造点与复位、206/`Content-Range` 解析、服务端可控字段、其他 Range 使用点。
- 加密算法恢复：RC4 外层（逐字节验证）、定制 HMAC-H（含 64 轮调度，3 向量 0 mismatch）、`0x50010` type 6/7 变换（常量池、依赖矩阵、确定性向量、**CFF 转移图 412 site / 767 边 / 417 目标的饱和枚举**、**选择层 262 FIXED / 69 BASE / 18 DATA 分类**、payload 16 个结构性惰性位）。
- 风控组件清单与证据等级。
- `libxyasf.so` 设备指纹：82 个 JNI 入口、8 个 protobuf 子消息 51 字段（含编号）、4 个 native 检测方法判定、`as.xiaohongshu.com` 上报端点与载荷封装、标准 MD5 核对。
- `libtinyd.so` 伴随守护：字符串加密闭式还原（4 解码器 × `i%20` 调度表，7/7 明文）、管道 IPC 定长 4 字节协议、`setArgV0("zygote")` 进程伪装链、CFF 分发池与 14/14 跳转验证。
- **Tiny 引擎 31/31 操作码定名**：`native(31) + Java switch(71)` 双操作码空间（交集为 0）、31/31 常量精确恢复、29 个操作码定位到 dex 调用表达式、**OkHttp 请求签名链路闭式**（`0x96f7fcac`）、TLS 证书链/长连接/定位/传感器上报路径。
- Tiny 引擎执行集度量：单 FDE 178 KB CFF 巨函数、31 个操作码静态块集合两两互异、动态执行集共有 2471 条指令、分组 1/2 的 90–198 条"专属指令"经逐条反汇编**证明为 CFF 调度胶水而非算法**。
- 需求对照审计。

## 入口

- [深挖报告](report.md) — 总览与关键纠正
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

## 口径差异（需注意）

两批文件的分析批次不同，个别说法并不一致，按以下原则阅读：

- **原生算法**以本目录为准：`crypto.md` 给出被引用的 rodata 对象逐个清单 + 动态可达性验证，并撤回了早期“无 rodata 常量表引用”的错误结论。
- **本地存储**以上层为准：本目录未做存储分析。
- **Mars STN / protobuf**以上层为准：本目录未覆盖长连接 wire 格式。
- 上层 `algorithm.md` 中“type 6/7 秘密变换不公开”是本目录 `crypto.md` 明确**不予采用**的表述——该变换已被定性并固化向量，CFF 转移图亦已完整枚举并饱和（412 site / 767 边 / 417 目标），**选择层亦已闭环**（262 FIXED / 69 BASE / 18 DATA，见 `crypto.md` §4.6）。
- **全应用普查**以本目录为准：`tiny-and-app-sweep.md` 覆盖 164 个 arm64 `.so`（32 带加密 / 132 不带），上层未做此项。

base APK SHA-256：`0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c`。
