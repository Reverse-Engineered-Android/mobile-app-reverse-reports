# 高德地图 17.00.0.2005

本目录是 `com.autonavi.minimap` 17.00.0.2005 的授权静态逆向与本地数据取证
脱敏报告。公开内容覆盖：

- `bedstone.db`、`girf_sync.db` 和离线编译数据库的结构、完整性与聚合计数；
- `AM-zlib` 离线容器、`DICE-AM` 页面封装和离线地图格式的已验证边界；
- JNI/网络接口、导航请求字段、重规划触发类型和离线/在线入口线索；
- 不含真实路线、搜索词、POI、地址、坐标、账号、设备、密钥、请求或响应。

入口：

- [分析报告](report.md)
- [逆向证据](evidence.md)
- [数据库 Schema](schema.md)
- [脱敏内容概要](content.md)
- [离线地图格式](offline-map.md)
- [网络与在线导航格式](network-navigation.md)
- [导航算法边界](algorithm.md)

APK 样本元数据：

| 字段 | 值 |
| --- | --- |
| 包名 | `com.autonavi.minimap` |
| versionName | `17.00.0.2005` |
| versionCode | `170000` |
| `base.apk` 大小 | `195437758` 字节 |
| `base.apk` SHA-256 | `475ef983285559042ff194029a78ca3a4c79b537c8c052fc121ee796dbbd8e8a` |

公开仓库只保存报告、必要的反汇编片段、结构化 Schema 和只读检查脚本，
不保存 APK、SO、数据库、离线地图文件、重建镜像、密钥或设备/账号材料。
