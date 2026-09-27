# Android App Reverse Reports

本仓库公开两类授权 Android 样本的静态逆向与本地数据取证结果：高德地图数据库封装、小红书原生签名/风控组件。内容聚焦可验证的版本、哈希、方法、函数地址、代码片段、数据库结构和脱敏聚合结果。

## 样本清单

| App | 包名 | 版本 | versionCode | 核心主题 |
| --- | --- | --- | ---: | --- |
| 高德地图 | `com.autonavi.minimap` | `17.00.0.2005` | `170000` | 加密 SQLite、JNI key 安装、导航/搜索历史 |
| 小红书 | `com.xingin.xhs` | `9.37.0` | `9370802` | shield 签名、CFF/WASM、设备风险组件 |

完整文件哈希见 [sample-manifest.json](sample-manifest.json)。报告入口：

- 高德：[概览](apps/amap/README.md)、[报告](apps/amap/report.md)、[证据](apps/amap/evidence.md)、[Schema](apps/amap/schema.md)
- 小红书：[概览](apps/xiaohongshu/README.md)、[报告](apps/xiaohongshu/report.md)、[证据](apps/xiaohongshu/evidence.md)、[算法边界](apps/xiaohongshu/algorithm.md)
- 通用：[逆向方法](docs/methodology.md)、[证据标准](docs/evidence-standard.md)、[脱敏规则](SECURITY.md)

## 公开范围

公开内容包括 APK/XAPK 哈希、版本、ELF/DEX 符号、函数地址、反汇编片段、SQL 表和列、聚合数量、时间范围、算法结构和合成测试向量。

不公开 APK、SO、数据库、反汇编全量文件、模拟器 trace、真实路线/搜索/POI/地址/坐标、账号、设备 ID、Cookie、Token、数据库 key、会话 key、真实请求或响应。

## 工具

[tools/README.md](tools/README.md) 提供只读 SQLite 检查、APK 字符串搜索、密钥派生占位脚本、shield 外层重放和发布前脱敏扫描。所有秘密通过环境变量或命令行传入，不写入仓库。

## 使用边界

仅用于安全研究、数据可移植性、数字取证和防御验证。只处理你拥有或获准分析的样本，不提供绕过登录、伪造身份、批量抓取或规避服务端风控的方法。
