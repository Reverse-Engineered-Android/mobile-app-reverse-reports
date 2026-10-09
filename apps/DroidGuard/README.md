# Google Play services DroidGuard

研究对象为 Google Play services `26.37.37 (260400-994713346)`、versionCode
`263737035` 内置的 DroidGuard 客户端，以及从自有 Android 设备只读取得的四个
已缓存 DroidGuard payload APK。GMS APK SHA-256 为
`bda6a95f1bb8dd707ad9333dce9639cc8104c4bbc97a639e14a428e63b4a6852`。

调查使用 DEX/JADX 静态反编译、ELF 静态反汇编、protobuf 反射元数据解码和设备
只读数据库/schema 核对。没有构造或发送 DroidGuard 请求，没有下载服务端 VM，
没有运行 payload，没有绕过风控，也没有写入或修改手机数据。

## 结论入口

- `report.md`：最终结论与综合矩阵。
- `network.md`：创建、VM 下载、结果返回的完整交互链。
- `protocol.md`：`hvnz`/`hvoa`/`hvpj` protobuf 精确字段。
- `auth.md`：调用方身份、同意闸门、API key、签名验证。
- `transfer.md`：上传、下载、返回数据的分层范围。
- `risk.md`：具体风控机制、native 采集、动态 VM、密码学闭包。
- `permissions.md`：权限与越权、提权判断。
- `privacy.md`：告知、采集与超范围判断。
- `evidence.md`：哈希、代码位置、公钥位置和设备只读核对。
- `completeness.md`：覆盖范围与研究边界。

## 核心边界

客户端可证实的 DroidGuard 网络出口只有两个方向：向
`/androidantiabuse/v1/x/create` 提交创建请求，以及从
`https://www.gstatic.com/droidguard/` 下载 VM/程序。风险计算结果通过 Binder
写回发起调用方；在已分析的 DroidGuard/GMS 调用链中，未观察到 GMS 把该结果
再次上传到 Google。业务调用方拿到结果后是否上传，属于调用方边界，本报告不
将其混同为 DroidGuard 自身上传。
