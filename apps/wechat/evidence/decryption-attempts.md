# 解密尝试记录

版本边界：数据库文件来自微信 8.0.78 / versionCode 3180 的授权快照。所有测试在只读文件副本的首页上进行，不修改源库，不输出候选值。

## 已验证主库路径

- `EnMicroMsg.db` 使用 SQLCipher v1：page size 1024、PBKDF2-HMAC-SHA1、64000 次迭代、HMAC-SHA1 20 字节。
- `kh5/b0.smali:1658-1716` 与 `com/tencent/mm/storagebase/IMEISave.smali:15-145` 给出 device ID + UIN 摘要前 7 个十六进制字符和历史 device ID 恢复路径。
- 历史快照使用该参数成功解密并通过 `integrity_check=ok`；实际 key/UIN/device ID 不公开。

## 独立加密库快照解密

2026-09-27 对当前本地快照 `AppBrandComm.db`、`WxExpt.db`、`WxCgiReport.db`、`newuba.db` 执行只读 SQLCipher 打开和明文导出；2026-09-28 又对 `Edge.db`、`EnResDown.db`、`enFavorite.db`、`WxFileIndex.db` 的授权快照执行同样流程：

1. `PRAGMA cipher_compatibility=1`；
2. 已恢复的 device ID / UIN 派生 7 字符候选集合，实际 key 不公开；
3. `sqlcipher_export` 到临时明文库，再执行 `PRAGMA integrity_check`。

八个库均成功打开，导出库均返回 `integrity_check=ok`。聚合只保留表名、列名、行数、非空数、文本/BLOB 长度范围和年份范围，见 `database-aggregates.json`。此前独立手工首页 HMAC 探测没有复现 SQLCipher 的 reserve/export 行为，因此不作为“密钥不存在”的证据。

第二批结果：`Edge.db=2 表/2 行`、`EnResDown.db=1 表/151 行`、`enFavorite.db=7 表/0 行`、`WxFileIndex.db=6 表/317,265 行`。`Edge.db`、`enFavorite.db`、`WxFileIndex.db` 使用账号数据库的 device ID + UIN 路径；`EnResDown.db` 使用资源下载器传入的特殊 UIN 哨兵和 device ID 路径，因此 key 候选不同。实际 key、UIN、device ID 和 `KeyInfo.bin` 内容不公开。

静态打开链：

- `smali-classes11/x91/l0.smali:35-145`：`b()` 构造加密数据库对象，并调用 `kh5/b0.R()`。
- `smali-classes11/kh5/b0.smali:1523-1716`：遍历 `IMEISave.a()` 的 device ID，拼接 UIN，取摘要前 7 个十六进制字符，再调用 `kh5/f.w()`。
- `smali-classes11/kh5/f.smali:94-110`：SQLCipher v1、page size 1024 参数。

`MicroMsgPriority.db` 是唯一尚未完成内容级解密的独立小库。`tx3/h.smali:37-105` 证明它直接把 UIN 字符串、登录用户名和 device 字符串拼接，经 MD5 后取前 7 个十六进制字符并以字节串作为 SQLCipher 口令；`tx3/h.smali:143-209` 创建 `PriorityConfig(type INTEGER PRIMARY KEY, version INTEGER)`。其无 `SQLiteCipherSpec` 的 WCDB 打开链固定使用 page size 4096 和 `CipherVersion.defaultVersion`，依据见 `database-source.md`。

2026-09-28 先执行 280 组只读打开验证：14 个 UIN 形态候选、2 个用户名形态（包含 Java null 拼接结果）、2 个当前/fallback D3 形态，以及 WCDB default、SQLCipher compatibility 1/2/3/4 共 5 组参数。随后使用微信自身的 `libcso.so`、`libWCDB.so` 和 `SQLiteDatabase` 做精确默认路径复核：`../../tools/wcdb-probe.java:51-89` 加载原生库并初始化 `CsoLoader`，`../../tools/wcdb-probe.java:91-149` 以 `SQLiteCipherSpec=null`、只读 flag 调用 `SQLiteDatabase.openDatabase`，与 `SQLiteDatabase.smali:1352-1373`、`Database.smali:1689-1721` 的 page size 4096/defaultVersion 路径保持一致。

精确复核使用 14 个 UIN 形态、4 个登录用户名/null 形态和 3 个 D3/null/空形态，共 168 组。输入文件是 147,456 字节、SHA-256 `7ecfeb84dadd702fc61a44f5c824fd48f69359454acf7290a8aa22fbe7861805` 的授权快照；每轮只读打开独立临时副本，源文件哈希保持不变。168 组均返回 `com.tencent.wcdb.database.SQLiteCantOpenDatabaseException`，没有可验证打开结果。`../../tools/wcdb-probe.java:151-179` 只输出候选序号、对象计数、PRAGMA 聚合或异常类，不输出 key/UIN/用户名/D3。

由 id `"a"` 确定性重算的 D3 primary cache 目录当前没有缓存文件，`MicroMsg/CompatibleInfo.cfg` 为 0 字节，因此创建该库时的 D3 仍不可重建。所有 SQLCipher 兼容组合和微信 WCDB 默认只读组合均未命中；不输出候选值，也不把静态 Schema 当作已解密内容。

## 边界

未解密只证明当前可访问证据不足，不证明文件损坏。下一步应取得 `MicroMsgPriority.db` 创建时的 D3 primary/兼容项 `258` 或等价密钥材料，再按 `tx3/h.smali` 的直接打开路径做只读验证；当前 `EnMicroMsg.db` 与 FTS 大库仍需在应用停止写入时另做受控复制。
