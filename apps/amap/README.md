# 高德地图 17.00.0.2005

研究对象是 `com.autonavi.minimap` 的 `base.apk` 与用户授权设备上的本地数据库副本。公开部分覆盖：

- `bedstone.db` / `fLocationInfo` 的 key 获取路径和精确 DDL。
- `girf_sync.db` 的 JNI/key 调用、对象清单和脱敏内容概要。
- `libamapbadge.so`、`libamaprsq.so`、`libamapsync.so` 的关键函数地址和反汇编。

入口：

- [分析报告](report.md)
- [逆向证据](evidence.md)
- [数据库 Schema](schema.md)
- [内容概要](content.md)

样本 SHA-256：`475ef983285559042ff194029a78ca3a4c79b537c8c052fc121ee796dbbd8e8a`。
