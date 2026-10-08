# Android App Reverse Reports

本仓库公开十类授权 Android 样本的静态逆向与本地数据取证结果：高德地图数据库封装、小红书原生签名/风控组件、支付宝数据库与登录/支付/风控调用面、微信数据库与登录/消息/支付/设备风控、拼多多网络协议/风控/动态库与本地存储、美团网络与设备风控、闲鱼网络协议/认证/上传下载/风控与本地存储、滴滴出行网络/认证/上传下载/风控与权限隐私、知乎网络协议/认证/盐选与视频/风控与本地存储、哔哩哔哩网络协议/认证/上传下载/风控/DRM 与本地存储。内容聚焦可验证的版本、哈希、方法、函数地址、代码片段、数据库结构和脱敏聚合结果。

## 样本清单

| App | 包名 | 版本 | versionCode | 核心主题 |
| --- | --- | --- | ---: | --- |
| 高德地图 | `com.autonavi.minimap` | `17.00.0.2005` | `170000` | 加密 SQLite、JNI key 安装、导航/搜索历史 |
| 小红书 | `com.xingin.xhs` | `9.37.0` | `9370802` | shield 签名、CFF/WASM、设备风险组件 |
| 支付宝 | `com.eg.android.AlipayGphone` | `12.12.30.8000` | `212310` | SQLCrypto/UnQLite、登录授权、在线/离线支付、设备风险 |
| 微信 | `com.tencent.mm` | `8.0.68/8.0.78` | `3003/3180` | 数据库解密、登录/消息/支付协议、朋友圈/小程序缓存、设备风控 |
| 拼多多 | `com.xunmeng.pinduoduo` | `8.26.0` | `82600` | anti-token/v1+v2 签名、登录 token 链、动态库分发、混淆闭包、22 个本地库 |
| 美团 | `com.sankuai.meituan` | `12.66.404` | `1200660404` | mtgsig/DFP/Yoda、登录挑战、支付与 POI 静态接口、权限隐私、67 个 native 库 |
| 闲鱼 | `com.taobao.idlefish` | `7.28.40` | `521` | MTOP 认证与签名、上传下载、四层风控、权限隐私、21 个明文 SQLite |
| 滴滴出行 | `com.sdu.didi.psnger` | `8.0.14` | `1208001404` | WSG 签名/wsgenv、WAF 522 挑战、接口 AES 加密、登录/AI 叫车、权限隐私、89 个 native 库 |
| 知乎 | `com.zhihu.android` | `11.10.0` | `41012` | X-Zse 签名链、Bangcle 分组加密、RUID/ZST 指纹、盐选与 OGV、应用列表上报、42 个设备端 SQLite |
| 哔哩哔哩 | `tv.danmaku.bili` | `9.13.0` | `9130500` | native appkey/MD5 签名、Gripper/GAIA 风控、36 判定器、121 字段码、Widevine/Bilibili DRM |

完整文件哈希见 [sample-manifest.json](sample-manifest.json)。报告入口：

- 高德：[概览](apps/amap/README.md)、[报告](apps/amap/report.md)、[证据](apps/amap/evidence.md)、[Schema](apps/amap/schema.md)
- 小红书：[概览](apps/xiaohongshu/README.md)、[报告](apps/xiaohongshu/report.md)、[证据](apps/xiaohongshu/evidence.md)、[算法边界](apps/xiaohongshu/algorithm.md)
- 支付宝：[概览](apps/alipay/README.md)、[报告](apps/alipay/report.md)、[逐库清单](apps/alipay/database-inventory.md)、[证据](apps/alipay/evidence.md)、[设备风险](apps/alipay/device-risk.md)
- 微信：[概览](apps/wechat/README.md)、[主报告](apps/wechat/report.md)、[数据库清单](apps/wechat/database-inventory.md)、[协议](apps/wechat/protocol.md)、[风控](apps/wechat/risk-control.md)、[证据索引](apps/wechat/evidence.md)
- 拼多多：[概览](apps/pinduoduo/README.md)、[报告](apps/pinduoduo/report.md)、[网络与协议](apps/pinduoduo/network.md)、[认证](apps/pinduoduo/auth.md)、[上传下载](apps/pinduoduo/transfer.md)、[风控](apps/pinduoduo/risk.md)、[混淆闭包](apps/pinduoduo/obfuscation.md)、[算法与 native](apps/pinduoduo/algorithm.md)、[本地存储](apps/pinduoduo/storage.md)、[证据](apps/pinduoduo/evidence.md)、[完成度](apps/pinduoduo/completeness.md)
- 美团：[概览](apps/meituan/README.md)、[报告](apps/meituan/report.md)、[网络与协议](apps/meituan/network.md)、[认证](apps/meituan/auth.md)、[上传下载](apps/meituan/transfer.md)、[风控](apps/meituan/risk.md)、[权限](apps/meituan/permissions.md)、[隐私](apps/meituan/privacy.md)、[证据](apps/meituan/evidence.md)、[完成度](apps/meituan/completeness.md)
- 闲鱼：[概览](apps/idlefish/README.md)、[报告](apps/idlefish/report.md)、[网络与协议](apps/idlefish/network.md)、[认证](apps/idlefish/auth.md)、[上传下载](apps/idlefish/transfer.md)、[风控](apps/idlefish/risk.md)、[权限](apps/idlefish/permissions.md)、[隐私](apps/idlefish/privacy.md)、[本地存储](apps/idlefish/storage.md)、[证据](apps/idlefish/evidence.md)、[完成度](apps/idlefish/completeness.md)
- 滴滴出行：[概览](apps/com.sdu.didi.psnger/README.md)、[报告](apps/com.sdu.didi.psnger/report.md)、[网络与协议](apps/com.sdu.didi.psnger/network.md)、[认证](apps/com.sdu.didi.psnger/auth.md)、[上传下载](apps/com.sdu.didi.psnger/transfer.md)、[风控](apps/com.sdu.didi.psnger/risk.md)、[权限](apps/com.sdu.didi.psnger/permissions.md)、[隐私](apps/com.sdu.didi.psnger/privacy.md)、[证据](apps/com.sdu.didi.psnger/evidence.md)、[完成度](apps/com.sdu.didi.psnger/completeness.md)
- 知乎：[概览](apps/com.zhihu.android/README.md)、[报告](apps/com.zhihu.android/report.md)、[网络与协议](apps/com.zhihu.android/network.md)、[认证](apps/com.zhihu.android/auth.md)、[上传下载](apps/com.zhihu.android/transfer.md)、[风控](apps/com.zhihu.android/risk.md)、[权限](apps/com.zhihu.android/permissions.md)、[隐私](apps/com.zhihu.android/privacy.md)、[本地存储](apps/com.zhihu.android/storage.md)、[证据](apps/com.zhihu.android/evidence.md)、[完成度](apps/com.zhihu.android/completeness.md)
- 哔哩哔哩：[概览](apps/tv.danmaku.bili/README.md)、[报告](apps/tv.danmaku.bili/report.md)、[网络](apps/tv.danmaku.bili/network.md)、[协议](apps/tv.danmaku.bili/protocol.md)、[认证](apps/tv.danmaku.bili/auth.md)、[上传下载](apps/tv.danmaku.bili/transfer.md)、[风控](apps/tv.danmaku.bili/risk-control.md)、[权限与隐私](apps/tv.danmaku.bili/privacy.md)、[DRM](apps/tv.danmaku.bili/drm.md)、[数据库](apps/tv.danmaku.bili/database-inventory.md)、[证据](apps/tv.danmaku.bili/evidence.md)、[完成度](apps/tv.danmaku.bili/completeness.md)
- 通用：[逆向方法](docs/methodology.md)、[证据标准](docs/evidence-standard.md)、[脱敏规则](SECURITY.md)

## 公开范围

公开内容包括 APK/XAPK 哈希、版本、ELF/DEX 符号、函数地址、反汇编片段、SQL 表和列、聚合数量、时间范围、算法结构和合成测试向量。

不公开 APK、SO、数据库、反汇编全量文件、模拟器 trace、真实路线/搜索/POI/地址/坐标、账号、设备 ID、Cookie、Token、数据库 key、会话 key、真实请求或响应。数据库完整性哈希可以公开，但账号维度文件名和所有行值均已脱敏。

## 工具

[tools/README.md](tools/README.md) 提供只读 SQLite 检查、APK 字符串搜索、密钥派生占位脚本、shield 外层重放和发布前脱敏扫描。所有秘密通过环境变量或命令行传入，不写入仓库。

## 使用边界

仅用于安全研究、数据可移植性、数字取证和防御验证。只处理你拥有或获准分析的样本，不提供绕过登录、伪造身份、批量抓取或规避服务端风控的方法。
