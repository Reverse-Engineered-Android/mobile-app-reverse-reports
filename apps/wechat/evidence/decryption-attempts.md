# 解密尝试记录

版本边界：数据库文件来自微信 8.0.78 / versionCode 3180 的授权数据。小库在只读副本上验证；大库使用只读、同 inode 隔离句柄验证，不复制超过 1 GB 的文件，不修改源库，不输出候选值。

## 已验证主库路径

- `EnMicroMsg.db` 使用 SQLCipher v1：page size 1024、PBKDF2-HMAC-SHA1、64000 次迭代、HMAC-SHA1 20 字节。
- `kh5/b0.smali:1658-1716` 与 `com/tencent/mm/storagebase/IMEISave.smali:15-145` 给出 device ID + UIN 摘要前 7 个十六进制字符和历史 device ID 恢复路径。
- 历史快照使用该参数成功解密并通过 `integrity_check=ok`；实际 key/UIN/device ID 不公开。
- 2026-09-28 对当前 5,837,163,520 字节全量文件用同一 key/参数执行 `SQLiteDatabase.openDatabase(..., OPEN_READONLY)`。为避免 WCDB 失败尝试删除路径，使用同文件系统、同 inode 的硬链接作为隔离入口；源文件验证前后大小、inode、mtime 均不变。完整 `PRAGMA integrity_check=ok`，page size 1024、5,700,355 页、44 个 freelist 页、714 个 `sqlite_master` 对象（252 表/462 索引），脱敏计数见 `main-aggregates.json`。

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

`MicroMsgPriority.db` 已完成内容级解密。`tx3/h.smali:37-105` 证明它直接把 UIN 字符串、登录用户名和 device 字符串按该顺序拼接，经 MD5 后取前 7 个十六进制字符并以字节串作为口令；`tx3/h.smali:143-209` 创建 `PriorityConfig(type INTEGER PRIMARY KEY, version INTEGER)`。其无 `SQLiteCipherSpec` 的 WCDB 打开链固定使用 page size 4096 和 `CipherVersion.defaultVersion`，依据见 `database-source.md`。

`FTS5IndexMicroMsg_encrypt.db` 使用与 `MicroMsgPriority.db` 相似的“缓存 key 或 UIN + D3 + 登录用户名”输入，但入口独立：`com/tencent/mm/plugin/fts/p.smali:104-178` 优先读取 `t3.Ad`，缺失时计算 MD5 前 7 字符并持久化；`p.smali:215-275` 以 null cipher spec 和 key 字节串打开。15,126,528 字节授权快照先以只读方式打开并通过 `integrity_check=ok`；随后当前 1.10 GB 全量文件沿同一路径只读打开，得到 160 个 `sqlite_master` 对象、270,018 页以及消息/联系人/群成员/小程序等 FTS 表行数聚合，见 `fts-aggregates.json`。全量复核为避免大库长时间检查而跳过 `integrity_check`，且不导出索引词、文档 ID 或消息正文。

2026-09-28 先执行 280 组只读打开验证：14 个 UIN 形态候选、2 个用户名形态（包含 Java null 拼接结果）、2 个当前/fallback D3 形态，以及 WCDB default、SQLCipher compatibility 1/2/3/4 共 5 组参数；SQLCipher compatibility 组合均未命中。随后使用微信自身的 `libcso.so`、`libWCDB.so` 和 `SQLiteDatabase` 做精确默认路径复核：`../../tools/wcdb-probe.java:67-136` 加载原生库并初始化 `CsoLoader`，`../../tools/wcdb-probe.java:138-197` 以 `SQLiteCipherSpec=null`、只读 flag 调用 `SQLiteDatabase.openDatabase`，与 `SQLiteDatabase.smali:1352-1373`、`Database.smali:1689-1721` 的 page size 4096/defaultVersion 路径保持一致。

精确复核使用 14 个 UIN 形态、4 个登录用户名/null 形态和 3 个 D3/null/空形态，共 168 组。输入文件是 147,456 字节、SHA-256 `7ecfeb84dadd702fc61a44f5c824fd48f69359454acf7290a8aa22fbe7861805` 的授权快照。前 6 组返回 `com.tencent.wcdb.database.SQLiteCantOpenDatabaseException`，第 7 个候选（候选序号 6）成功打开；探针随后停止，因此有效结果为 6 次未命中加 1 次命中。结果为 `integrity_check=ok`、page size 4096、36 页、23 个对象、16 表/1,003 行，脱敏 Schema/计数见 `priority-aggregates.json`。

早期探针曾把 `table_count=0,page_count=0` 的空句柄误报为成功，随后还暴露出输入路径会在多次 WCDB 尝试间消失，导致后续错误不能再计作 key 未命中。修正后的 `../../tools/wcdb-probe.java:138-197` 拒绝空文件、空 schema 和零页，`WDB_COPY_BEFORE_OPEN=1` 在每次打开前复制隔离输入；`../../tools/wcdb-probe.java:337-373` 只输出候选序号、对象/列/行聚合或异常类，不输出 key/UIN/用户名/D3。最终复核前后源文件大小保持 147,456 字节，逐候选副本不影响源快照。

由 id `"a"` 确定性重算的 D3 primary cache 目录当前没有缓存文件，`MicroMsg/CompatibleInfo.cfg` 为 0 字节，因此不能从当前缓存反推创建时究竟使用 primary、fallback 或空 D3。成功结果只证明枚举范围内有一个 key/输入组合正确；不输出候选值。

## 边界

当前所有独立加密小库、`EnMicroMsg.db` 当前全量和 FTS 全量均已有内容级只读证据。活跃主库的行数是验证时点聚合；如需严格时间点消息/支付取证，应在应用停止写入时另存一致快照。FTS 如需词项分布或消息级取证，也应另行取得一致快照并执行更细的最小化查询。历史快照的行数不冒充当前全量行数。
