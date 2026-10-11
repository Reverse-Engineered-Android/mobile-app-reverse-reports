# 研究完成度与最终边界

## 1. 任务矩阵

| 要求 | 状态 | 证据 |
|---|---|---|
| 主要网络交互 | 已覆盖 | `network.md` §1–§6 |
| 协议具体格式 | 已覆盖 | `protocol.md` |
| 认证机制 | 已覆盖 | `auth.md` |
| 上传具体范围 | 已覆盖 | `transfer.md` §1 |
| 下载具体范围 | 已覆盖 | `transfer.md` §2 |
| 越权判断 | 已覆盖 | `permissions.md` §4 |
| 提权判断 | 已覆盖 | `permissions.md` §5 |
| 未经告知判断 | 已覆盖 | `privacy.md` §4–§5 |
| 超范围判断 | 已覆盖 | `privacy.md` §5 |
| 风控机制精确代码 | 固定客户端已覆盖 | `risk.md` §1–§6 |
| 混淆与动态程序机制 | 已覆盖 | `risk.md` §4.1–§4.7、`b-programs.md` |
| 密码学用途、算法与地址 | 已覆盖 | `risk.md` §5 |
| Google 官方 GMS/StreamZ/固定解释器代码 | 已覆盖 | `network.md` §4、`risk.md` §2–§5 |
| `.b` 服务端程序逐条反编译 | 已覆盖 | `b-programs.md`、`b-program-lift.json` |
| 公钥位置与算法 | 已覆盖 | `auth.md` §2–§3、`evidence.md` §3 |
| 设备数据库格式 | 已覆盖 | `evidence.md` §6–§7 |
| 上传示例 | 已覆盖 | `protocol.md` §5 |
| 最终结论文字 | 已覆盖 | `report.md` |

## 2. 风控代码覆盖

### 已还原

- `hvnz` 构造、flow 分类、Build 字段和 fast/full 分支；
- `hvoa/hvpj` 解析、响应验签、必需字段与 VM URL 白名单；
- payload 构造、bytecode 二次验签、JNI 注册和全部 native 入口；
- GPU、触摸、传感器、Bundle/Map 输入如何进入 native session；
- SHA-1、SHA-256、RSA-SHA256、AES 与 HMAC-SHA256 的算法、调用点、硬件/回退路径；
- 变体 A `0x46cb0` 的 `.b` 外层 AES key、IV、block index、session 字段和 XOR 映射；
- B/C/D 解码入口 `0x182bc`/`0x48a88`/`0x15e0c` 的独立函数体、session 读写和
  C 的偏移差异；
- `0x432c4`/`0x177a4` 的寄存器、PC 与字符串缓冲编码；
- 四个 native 变体各自的 11 张主 opcode 表：site、table、base、越界路径和
  26 项相对位移；
- `session+0x1528` 即 `JNIEnv*`，字段读写、方法调用、数组与分配的精确 JNI slot；
- 四份 SO 中对应的 `IsInstanceOf` 类型门和对象 `GetObjectClass` 路径，A 的
  分派点为 `0x3e4f4`/`0x3e77c`；
- `0x4563c` 的 26 项半字操作数表、rotation/XOR 公式及 `0x45870` 不支持分支；
- 解释器的栈、分支、内存、摘要与 AES handler 数据流；
- `app_dgp` 的 `.b`/`.d` 文件映射、`dg.db` schema、有效期和过期清理；
- `_seigd` Base64 Parcel/Bundle 编码、结果返回 Binder 链；
- Clearcut/StreamZ 的 15 个 DroidGuard 指标、字段、log source 与 uploader 端点；
- payload manifest 的零 `uses-permission` 权限面与 Google 签名证书；
- 设备真实 Android data 路径、`app_dgp`/`dg.db` 与 Vending 数据库归属。

### 动态交付物的最终结论

`hvpj.byteCode` 是服务端签名交付的数据程序，由固定 native 解释器执行，不是随
APK 固定的业务源码。初始抓取与 2026-10-10 复核的 `.b` 原始长度、SHA-256、
正确解码长度和 SHA-256 均已分别核对；外层算法不是未知压缩或未知密码，而是
§4.4 给出的 AES-128 可变块 XOR。对 A 还通过 `0x432c4 → 0x46cb0` 连续取流，
得到 63658 字节线性流，SHA-256 为
`63ded1fab3a6c2616d34967e6d785dbe85bba182962f5681527cfd547cb935b5`；
该结果证明取流函数可连续工作，但线性流仍为高熵数据，不等同于指令级反编译。

固定解释器侧已恢复：opcode 范围和 `opcode-0x42` 跳转规则、四份 SO 的
11×26 主分派表、JNI slot、类型门、`0x4563c` 的 26 项操作数解码器、寄存器/PC
的 rotation/XOR 和 B/C/D 独立入口。设备缓存的三个程序随后全部完成受控
`ssNative` 执行，均正常返回且无执行错误：

| flow | 程序前缀 | native 指令 | selector 段 | 输出字节 |
|---|---|---:|---:|---:|
| `fast` | `0b527259…cdda0` | 607366 | 24 | 49 |
| `pia_express` | `4d85a8af…aeeb7` | 14097 | 14 | 41 |
| `ad_attest` | `8f1a7840…100e` | 15807 | 54 | 41 |

每个 selector 段均记录实际执行的唯一 ARM64 PC 与次数、实际/备选 selector、
比较谓词与实际结果、状态增量、取流范围和函数调用；全部分支均落入实际或备选
路径，没有保留未解析比较。短 handler 已还原为表加载、游标递减和比较选择链：
`0x83ec33cc` 读取 `table[(x26 & 0xff)]`，`0x15529b4a` 递减游标，
`0x682fc45e` 精确执行 `cmp w20, w27` 后以 `csel` 选择
`0xe8afad93`/`0x15529b4a`，`0x7914e417` 推进终态，
`0x9d2333bd` 完成 HMAC 与序列化。

密码学执行也已逐次计数：三个程序各有 1 个外层 range-decode AES block；
`fast` 长 handler `0x1e575b23`/`0x22350` 另执行 1076 次 portable
AES block，硬件路径对应 `0x71a0`；`hmac_sha256` 事件分别为 4/4/6，
`serialize_value` 均为 2。完整可读提升见 `b-programs.md`，结构化逐指令证据
见 `b-program-lift.json`。服务端未下发的评分阈值、处罚策略和调用方二次上传
行为仍不由固定客户端证明，报告不作推测。

本地受控 Unicorn 执行验证了 `0x19308` 初始化、`0x1c5e8` 建帧和 `0x46cb0`
程序解码，输出与独立实现一致。随后通过真实 fetch wrapper 恢复变体 A 的完整逻辑
流：8 次初始化 fetch 加 63654 次受控 fetch 覆盖 `4..63666`，逻辑流 SHA-256 为
`3773c381e3cc07d9312190db50c1bbb8a7f03d1dc380fb82eb29a13bc1037eab`，无缺字节、
反向偏移或执行错误。同一本地会话继续以合成 `fast` 输入执行 `ssNative`，
3393 条 native 指令内完成 105 次 JNI 交互并返回 20 字节结构化结果，SHA-256 为
`9187d4ef9048f36e5264c817e4b0d346fd565f8ed28068fcda9f405c59b36611`；
没有在设备上执行 payload、访问网络或请求评分。
程序携带的 flow 分支输入和服务端参数属于运行数据，评分阈值、拒绝/挑战策略、
原始信号保存期限以及调用方拿到结果后的二次上传不由固定客户端证明，报告不把
这些服务端内容写成客户端代码。

## 3. 研究方法

- JADX 静态反编译 GMS DEX 与四个 payload APK；
- r2/r2ghidra 静态反汇编、函数 XREF 与 handler 数据流恢复；
- 从四份 ELF 与反汇编提取全部 `opcode - 0x42` 跳转表，并按 JNI header 核对
  函数偏移、target 存在性和 role；
- protobuf runtime `RawMessageInfo` 元数据解码；
- manifest、ELF、数据库 schema 只读解析；
- `.b`/`.d` 文件 hash/熵、`dg.db` schema 与有效期只读核对；
- 本地 Unicorn 受控执行变体 A 的 `0x19308`、`0x1c5e8`、`0x46cb0`，并与独立
  解码实现核对；
- 以 `0x432c4` 逐次调用 `0x46cb0` 记录 A 样本的 63658 字节线性取流，
  并用真实 fetch wrapper 恢复 63666 字节逻辑流；
- 对三个缓存 `.b` 程序执行完整 `ssNative` 路径，按 dispatcher 段重建
  unique-PC、selector 分支、状态增量、调用表、取流范围与输出 protobuf；
- 将原始追踪提升为 `b-programs.md` 与 `b-program-lift.json`，发布长度、摘要和
  指令结构，不发布原始程序字节。
- Android 设备仅通过只读 SSH，并在需要时进入宿主挂载命名空间的 `data/...`
  路径，核对 schema、哈希与存在性。

## 4. 执行与数据边界

- 未登录真实账号；
- 未构造或发送 DroidGuard 请求；
- 未从服务端下载 VM/bytecode；
- 未在手机上运行 payload 或 native 风控；
- 缓存程序的完整指令级轨迹和逐程序语义树已由本地受控执行取得；
- 未向服务端请求当前或新的评分程序，分析对象仍是设备已有缓存样本；
- 未测试越权、提权或导出组件；
- 未抓取真实业务网络流量；
- 未写入或修改手机数据库、文件或配置；
- 未把原始程序、缓存、私有 key、账号或设备指纹提交到报告。

## 5. 服务端不可观察项

评分阈值、具体拒绝/挑战策略、原始信号保存期限、服务端二次用途和业务调用方
拿到结果后的上传行为不由固定客户端证明。以上保持为明确边界，不作推测。
