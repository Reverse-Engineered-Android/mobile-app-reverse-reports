# Android App Reverse Reports

本仓库公开四类授权 Android 样本的静态逆向与本地数据取证结果：高德地图数据库封装、小红书原生签名/风控组件、支付宝数据库与登录/支付/风控调用面、微信数据库与登录/消息/支付/设备风控。内容聚焦可验证的版本、哈希、方法、函数地址、代码片段、数据库结构和脱敏聚合结果。

## 样本清单

| App | 包名 | 版本 | versionCode | 核心主题 |
| --- | --- | --- | ---: | --- |
| 高德地图 | `com.autonavi.minimap` | `17.00.0.2005` | `170000` | 加密 SQLite、JNI key 安装、导航/搜索历史 |
| 小红书 | `com.xingin.xhs` | `9.37.0` | `9370802` | shield 签名、CFF/WASM、设备风险组件 |
| 支付宝 | `com.eg.android.AlipayGphone` | `12.12.30.8000` | `212310` | SQLCrypto/UnQLite、登录授权、在线/离线支付、设备风险 |
| 微信 | `com.tencent.mm` | `8.0.68/8.0.78` | `3003/3180` | 数据库解密、登录/消息/支付协议、朋友圈/小程序缓存、设备风控 |

完整文件哈希见 [sample-manifest.json](sample-manifest.json)。报告入口：

- 高德：[概览](apps/amap/README.md)、[报告](apps/amap/report.md)、[证据](apps/amap/evidence.md)、[Schema](apps/amap/schema.md)
- 小红书：[概览](apps/xiaohongshu/README.md)、[报告](apps/xiaohongshu/report.md)、[证据](apps/xiaohongshu/evidence.md)、[算法边界](apps/xiaohongshu/algorithm.md)
- 支付宝：[概览](apps/alipay/README.md)、[报告](apps/alipay/report.md)、[逐库清单](apps/alipay/database-inventory.md)、[证据](apps/alipay/evidence.md)、[设备风险](apps/alipay/device-risk.md)
- 微信：[概览](apps/wechat/README.md)、[主报告](apps/wechat/report.md)、[数据库清单](apps/wechat/database-inventory.md)、[协议](apps/wechat/protocol.md)、[风控](apps/wechat/risk-control.md)、[证据索引](apps/wechat/evidence.md)
- 通用：[逆向方法](docs/methodology.md)、[证据标准](docs/evidence-standard.md)、[脱敏规则](SECURITY.md)

## 公开范围

公开内容包括 APK/XAPK 哈希、版本、ELF/DEX 符号、函数地址、反汇编片段、SQL 表和列、聚合数量、时间范围、算法结构和合成测试向量。

不公开 APK、SO、数据库、反汇编全量文件、模拟器 trace、真实路线/搜索/POI/地址/坐标、账号、设备 ID、Cookie、Token、数据库 key、会话 key、真实请求或响应。数据库完整性哈希可以公开，但账号维度文件名和所有行值均已脱敏。

## 工具

[tools/README.md](tools/README.md) 提供只读 SQLite 检查、APK 字符串搜索、密钥派生占位脚本、shield 外层重放和发布前脱敏扫描。所有秘密通过环境变量或命令行传入，不写入仓库。

## 使用边界

仅用于安全研究、数据可移植性、数字取证和防御验证。只处理你拥有或获准分析的样本，不提供绕过登录、伪造身份、批量抓取或规避服务端风控的方法。
