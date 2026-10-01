# 小红书 9.37.0

研究对象是 `com.xingin.xhs` 9.37.0 的 base APK 与 ARM64 native libraries，并使用另一台设备上的 9.47.0 历史只读快照及 9.48.0 当前只读查询做跨版本存储格式复核。公开部分覆盖：

- 主 API、实时长连接、登录/风控、内容元数据、联系人/位置与对象存储的网络协议。
- Mars STN 长连接、protobuf 字段号、ECDH/AES/gzip 协商与 ACK/推送消息格式。
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
- [分析完成度矩阵](completeness.md)

## 原生加密与传输深挖（独立补充）

另有独立的深挖批次，覆盖原生加密算法的逐字节恢复与传输链路逐点定位，入口为 [deepdive/](deepdive/README.md)：

- `libxyass.so` RC4 外层、定制 HMAC-H（含 64 轮调度）、`0x50010` type 6/7 会话变换的常量池/CFF/依赖矩阵与确定性向量。
- 上传 permit/去重/分块/云厂商分支与令牌全字段；下载 Range 构造点与总长解析。
- 原生请求的拦截器链、协议映射与认证字段来源。
- 该批次与上述文件集**互补而非替代**，口径差异见 [deepdive/README.md](deepdive/README.md)。

base APK SHA-256：`0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c`。
