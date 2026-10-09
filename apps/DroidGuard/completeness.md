# 完成度与边界

## 1. 任务矩阵

| 要求 | 状态 | 证据 |
|---|---|---|
| 主要网络交互 | 完成 | `network.md` §1–§6 |
| 协议具体格式 | 完成 | `protocol.md` |
| 认证机制 | 完成 | `auth.md` |
| 上传具体范围 | 完成 | `transfer.md` §1 |
| 下载具体范围 | 完成 | `transfer.md` §2 |
| 越权判断 | 完成 | `permissions.md` §4 |
| 提权判断 | 完成 | `permissions.md` §5 |
| 未经告知判断 | 完成 | `privacy.md` §4–§5 |
| 超范围判断 | 完成 | `privacy.md` §5 |
| 风控机制精确代码 | 完成 | `risk.md` §1–§6 |
| 混淆/动态程序机制 | 完成 | `risk.md` §4 |
| 密码学用途与地址 | 完成 | `risk.md` §5 |
| Google 官方 GMS/StreamZ 代码反混淆 | 完成 | `network.md` §4、`risk.md` §2–§5 |
| 公钥位置 | 完成 | `evidence.md` §3 |
| 设备数据库格式 | 完成 | `evidence.md` §6–§7 |
| 上传示例 | 完成 | `protocol.md` §5 |
| 最终结论文字 | 完成 | `report.md` |

## 2. 风控代码覆盖

### 已闭包

- `hvnz` 构造、flow 分类、Build 字段和 fast/full 分支；
- `hvoa/hvpj` 解析、响应验签、必需字段与 URL 白名单；
- payload 构造、bytecode 二次验签、JNI 注册和全部 native 方法；
- GPU、触摸、传感器、Bundle/Map 输入；
- SHA-1、SHA-256、AES 与 RSA-SHA256 的算法、地址、调用点；
- `app_dgp` 的 `.b`/`.d` 文件映射、`dg.db` schema、有效期和过期清理；
- `_seigd` Base64 Parcel/Bundle 编码；
- 结果返回 Binder 链。
- Clearcut/StreamZ 的 15 个 DroidGuard 指标、字段、log source 与 uploader 端点；
- payload manifest 的零 `uses-permission` 权限面与 Google 签名证书；
- 设备真实 Android data 路径、`app_dgp`/`dg.db` 与 Vending 数据库归属。

### 动态程序的边界

`hvpj.byteCode` 是服务端签名交付的数据程序，由固定 native 解释器执行，不是
随 APK 固定的业务源码。报告闭包的是：

1. 程序交付与双重验签；
2. 解释器输入/输出与执行模型；
3. 解释器调用的标准密码学原语；
4. 采集信号如何进入 session；
5. 结果如何离开 DroidGuard。
6. 本地 `.b`/`.d` 缓存、摘要文件名、SQLite 键值与过期删除。

设备上的三个 `.b` 已用长度、熵、SHA-256 和通用解压探测核对，属于高熵交付
数据；固定代码中的 RSA、SHA、AES 调用参数、算法与 XREF 均已定位。服务端
在特定 flow 下给出的评分语义与阈值属于服务端内容，不由客户端固定代码承载，
报告不把交付数据伪写成 APK 内的固定业务源码。

## 3. 研究方法

- JADX 静态反编译 GMS DEX 与四个 payload APK；
- r2/r2ghidra 静态反汇编与函数 XREF；
- protobuf runtime `RawMessageInfo` 元数据解码；
- manifest、ELF、数据库 schema 只读解析；
- `app_dgp` 文件 hash/熵、`dg.db` schema 与有效期只读核对；
- Android 设备仅通过只读 SSH，并在需要时进入宿主挂载命名空间的 `data/...`
  路径，核对 schema、哈希与存在性。

## 4. 未执行行为

- 未登录真实账号；
- 未构造或发送 DroidGuard 请求；
- 未下载服务端 VM/bytecode；
- 未运行 payload/native 风控；
- 未测试越权、提权或导出组件；
- 未抓取真实业务网络流量；
- 未写入或修改手机数据库、文件或配置；
- 未提交原始私有数据到报告或 Telegram。

## 5. 服务端不可观察项

评分阈值、具体拒绝/挑战策略、原始信号保存期限、服务端二次用途和业务调用方
拿到结果后的上传行为不由固定客户端证明。以上均保持为明确边界，不作推测。
