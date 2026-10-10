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
| 风控机制精确代码 | 已覆盖 | `risk.md` §1–§6 |
| 混淆与动态程序机制 | 已覆盖 | `risk.md` §4.1–§4.6 |
| 密码学用途、算法与地址 | 已覆盖 | `risk.md` §5 |
| Google 官方 GMS/StreamZ/解释器代码 | 已覆盖 | `network.md` §4、`risk.md` §2–§5 |
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
- SHA-1、SHA-256、RSA-SHA256 与 AES 的算法、调用点、硬件/回退路径；
- `0x46cb0` 的 `.b` 外层 AES key、IV、block index、session 字段和 XOR 映射；
- `0x432c4`/`0x177a4` 的寄存器、PC 与字符串缓冲编码；
- 11 张主 opcode 表的 site、table、base、越界路径和 26 项相对位移；
- `session+0x1528` 即 `JNIEnv*`，字段读写、方法调用、数组与分配的精确 JNI slot；
- `0x3e4f4`/`0x3e77c` 的 `IsInstanceOf` 类型门和对象 `GetObjectClass` 路径；
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
§4.4 给出的 AES-128 可变块 XOR。解码后的 opcode、寄存器值、PC、字符串缓冲与
操作数按固定解释器的索引、rotation/XOR 和 handler 专属 MBA 逐次展开；这些公式
来自对 `0x46cb0`、`0x432c4`、`0x177a4` 和解释器 XREF 的恢复。主 opcode 表、
JNI 操作、类型检查和值类型表已全部展开到固定地址，不留未识别的本地加密或
混淆控制流边界。

本地受控 Unicorn 执行验证了 `0x19308` 初始化、`0x1c5e8` 建帧和 `0x46cb0`
程序解码，输出与独立实现一致；没有在设备上执行 payload、访问网络或请求评分。
程序携带的 flow 分支输入和服务端参数属于运行数据，评分阈值、拒绝/挑战策略、
原始信号保存期限以及调用方拿到结果后的二次上传不由固定客户端证明，报告不把
这些服务端内容写成客户端代码。

## 3. 研究方法

- JADX 静态反编译 GMS DEX 与四个 payload APK；
- r2/r2ghidra 静态反汇编、函数 XREF 与 handler 数据流恢复；
- 从 ELF 与反汇编提取全部 `opcode - 0x42` 跳转表，并按 JNI header 核对函数偏移；
- protobuf runtime `RawMessageInfo` 元数据解码；
- manifest、ELF、数据库 schema 只读解析；
- `.b`/`.d` 文件 hash/熵、`dg.db` schema 与有效期只读核对；
- 本地 Unicorn 受控执行 `0x19308`、`0x1c5e8`、`0x46cb0`，并与独立解码实现核对；
- Android 设备仅通过只读 SSH，并在需要时进入宿主挂载命名空间的 `data/...`
  路径，核对 schema、哈希与存在性。

## 4. 执行与数据边界

- 未登录真实账号；
- 未构造或发送 DroidGuard 请求；
- 未从服务端下载 VM/bytecode；
- 未在手机上运行 payload 或 native 风控；
- 未测试越权、提权或导出组件；
- 未抓取真实业务网络流量；
- 未写入或修改手机数据库、文件或配置；
- 未把原始程序、缓存、私有 key、账号或设备指纹提交到报告。

## 5. 服务端不可观察项

评分阈值、具体拒绝/挑战策略、原始信号保存期限、服务端二次用途和业务调用方
拿到结果后的上传行为不由固定客户端证明。以上保持为明确边界，不作推测。
