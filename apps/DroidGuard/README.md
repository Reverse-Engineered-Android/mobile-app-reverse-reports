# Google Play services DroidGuard

研究对象为 Google Play services `26.37.37 (260400-994713346)`、versionCode
`263737035` 内置的 DroidGuard 客户端，以及从自有 Android 设备只读取得的四个
已缓存 DroidGuard payload APK。GMS APK SHA-256 为
`bda6a95f1bb8dd707ad9333dce9639cc8104c4bbc97a639e14a428e63b4a6852`。

调查使用 DEX/JADX 静态反编译、四份 ELF 的静态反汇编、全部 opcode
跳转表/JNI slot 提取、protobuf 反射元数据解码、本地 Unicorn 受控执行
初始化/解码函数、逻辑取流恢复、线性取流与入口级追踪，以及设备只读
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
- `permissions.md`：权限与越权、提权判断。
- `privacy.md`：告知、采集与超范围判断。
- `evidence.md`：哈希、代码位置、公钥位置和设备只读核对。
- `completeness.md`：覆盖范围与研究边界。

## 核心边界

固定 native 代码中的网络、认证、采集、解码、分派和密码学用途已逐项定位；
服务端交付的三个 `.b` 样本已证明外层解码和线性取流路径，但未取得足以还原
每个程序控制流和业务含义的 instruction-level 执行轨迹。因此本报告把“固定
解释器机制已恢复”和“逐 `.b` 程序语义已恢复”分开，不以后者结项。

客户端存在两类 Google 网络出口：业务链路向
`/androidantiabuse/v1/x/create` 提交创建请求，并从
`https://www.gstatic.com/droidguard/` 下载 VM/程序；另有 Clearcut/StreamZ
遥测链路向 Google Play 日志服务上传 DroidGuard 的 flow、状态、时延、失败原因和
响应大小计数。后者不携带 GPU、触摸、传感器或动态 VM 的原始采集值。风险计算
结果通过 Binder 写回发起调用方；未发现 GMS 把结果字节或本地原始信号再次放入
创建请求或其他业务上传。业务调用方拿到结果后是否上传，属于调用方边界，本报告
不将其混同为 DroidGuard 自身上传。
