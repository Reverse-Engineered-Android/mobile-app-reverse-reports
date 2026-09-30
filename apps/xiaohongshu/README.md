# 小红书 9.37.0

研究对象是 `com.xingin.xhs` 9.37.0 的 base APK 与 ARM64 native libraries，并使用另一台设备上的 9.47.0 只读数据快照做跨版本存储格式复核。公开部分覆盖：

- 主 API、登录/风控、内容元数据、联系人/位置与对象存储的网络协议。
- `xy-common-params`、Shield、Tiny、登录 token 与 OAuth 的认证边界。
- 上传下载的数据范围、对象存储 token 结构、分片和 CDN 变换。
- Room/Tencent WCDB、MMKV、风险 SDK 数据库、Java serialization 与 gzip 存储格式。
- DB 口令、Android Keystore RSA-AES 解密链路和不可离线恢复的边界。
- 请求签名、设备指纹、环境完整性、人机验证、账号风控与推送 SDK 策略。

入口：

- [综合报告](report.md)
- [网络与认证](network.md)
- [上传下载范围](transfer.md)
- [本地存储与解密](storage.md)
- [风控机制](risk.md)
- [逆向证据](evidence.md)
- [算法边界](algorithm.md)

base APK SHA-256：`0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c`。
