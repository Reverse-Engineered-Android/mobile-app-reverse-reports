# 高德地图数据库逆向报告

## 样本

| 字段 | 值 |
| --- | --- |
| 包名 | `com.autonavi.minimap` |
| versionName | `17.00.0.2005` |
| versionCode | `170000` |
| minSdk / targetSdk | `21` / `35` |
| `base.apk` 大小 | `195437758` 字节 |
| `base.apk` SHA-256 | `475ef983285559042ff194029a78ca3a4c79b537c8c052fc121ee796dbbd8e8a` |

## 逆向方法

1. 使用 `jadx` 解码 manifest，确认包名、版本和 SDK。
2. 从 APK 的 DEX 中定位 `BaseMapFrequentLocationsJni`、`GirfSyncJni`、`GirfSyncServiceJni` 和 `girf_sync.db`。
3. 在 ARM64 SO 中扫描数据库名、SQL、JNI 和 `girf_sqlite3_key` 线索。
4. 用符号表和反汇编确认 key 调用 ABI：数据库句柄、key 指针、key 长度。
5. 对离线数据库副本执行只读完整性检查、对象枚举和聚合计数。

## `bedstone.db`

- `libamapbadge.so` 同时包含 `bedstone.db`、`fLocationInfo` 建表/插入 SQL 和 `girf_sqlite3_key` 导入。
- 调用点先执行 `strlen`，再把结果作为第三参数传入 `girf_sqlite3_key`。
- SQLite passphrase 从该字符串数据流获得；AES-128 key 是 passphrase 的原始 ASCII 前 16 字节，而不是十六进制字符串解析。
- 完整性检查通过，共 25 条定位/兴趣点缓存记录。

真实 passphrase/key 不公开；[derive_amap_key.py](../../tools/derive_amap_key.py) 只读取环境变量。

## `girf_sync.db`

- `libamapsync.so` 的 stripped 函数 `sub_2eb34` 构造 key 缓冲，并在 `0x2ebc4` 调用 `girf_sqlite3_key`。
- `libamaprsq.so:0x3c694` 提供旧式 key ABI 到内部安装函数的适配。
- 数据库内容层已可读取，但原始 key 的完整构造没有可靠闭环，因此不宣称已提取原始 key。
- 主要对象包括路线历史、搜索历史、用户同步对象、设置、系统配置和内部类别。

## 结论

高德在本地缓存了较完整的导航和搜索历史。`bedstone.db` 的 key 可从 native 字符串调用链离线派生；`girf_sync.db` 的 key 参数来源仍需继续追踪 `key_name`、内部类别和版本数据流。
