# Google Play services DroidGuard

研究对象为 Google Play services `26.37.37 (260400-994713346)`、versionCode
`263737035` 内置的 DroidGuard 客户端，以及从自有 Android 设备只读取得的四个
已缓存 DroidGuard payload APK。GMS APK SHA-256 为
`bda6a95f1bb8dd707ad9333dce9639cc8104c4bbc97a639e14a428e63b4a6852`。

调查使用 DEX/JADX 静态反编译、四份 ELF 的静态反汇编、全部 opcode
跳转表/JNI slot 提取、protobuf 反射元数据解码、本地 Unicorn 受控执行
初始化/解码函数、逻辑取流恢复、三个缓存程序的完整 `ssNative`
instruction-level 追踪与 selector 语义提升，以及设备只读
数据库/schema/哈希复核。没有
构造或发送 DroidGuard 请求，没有从网络下载服务端 VM，没有在设备运行完整
payload 或评分程序，没有绕过风控，也没有写入或修改手机数据。

## 结论入口

- `report.md`：最终结论与综合矩阵。
- `network.md`：创建、VM 下载、结果返回的完整交互链。
- `protocol.md`：`hvnz`/`hvoa`/`hvpj` protobuf 精确字段。
- `auth.md`：调用方身份、同意闸门、API key、签名验证。
- `transfer.md`：上传、下载、返回数据的分层范围。
- `risk.md`：具体风控机制、native 采集、动态 VM、`.b` 解码和密码学还原。
- `b-programs.md`：三个 `.b` 程序逐 selector、逐分支、逐状态的可读提升结果。
- `b-program-lift.json`：机器可读的完整 ARM64 指令、调用、取流与输出结构。
- `permissions.md`：权限与越权、提权判断。
- `privacy.md`：告知、采集与超范围判断。
- `evidence.md`：哈希、代码位置、公钥位置和设备只读核对。
- `completeness.md`：覆盖范围与研究边界。

## 核心边界

固定 native 代码中的网络、认证、采集、解码、分派和密码学用途已逐项定位；
设备缓存的三个 `.b` 样本均完成 `ssNative` 指令级追踪并提升为可读 selector
程序，实际执行路径、备选 selector、比较谓词、状态变化、取流范围、AES/HMAC/
序列化调用和 protobuf 输出均有机器可读证据。服务端未下发的评分阈值、处罚
策略和保存期限仍属于服务端不可观察项，不由本地程序结构推测。

客户端存在两类 Google 网络出口：业务链路向
`/androidantiabuse/v1/x/create` 提交创建请求，并从
`https://www.gstatic.com/droidguard/` 下载 VM/程序；另有 Clearcut/StreamZ
遥测链路向 Google Play 日志服务上传 DroidGuard 的 flow、状态、时延、失败原因和
响应大小计数。后者不携带 GPU、触摸、传感器或动态 VM 的原始采集值。风险计算
结果通过 Binder 写回发起调用方；未发现 GMS 把结果字节或本地原始信号再次放入
创建请求或其他业务上传。业务调用方拿到结果后是否上传，属于调用方边界，本报告
不将其混同为 DroidGuard 自身上传。
