# 数据库源码证据

版本边界：本页 smali、WCDB 打开参数和 AppBrand ORM 注册均来自微信 8.0.78 / versionCode 3180。

## SQLCipher 参数

来源：`smali-classes11/kh5/f.smali`。

```text
94  new-instance SQLiteCipherSpec
98  const/16 v1, 0x400
100 setPageSize(I)
104 const/4 v1, 0x1
106 setSQLCipherVersion(I)
110 sput-object ... kh5/f;->l
```

这直接证明微信 WCDB 封装使用 page size 1024 和 SQLCipher version 1。

动态复核使用 `../../tools/wcdb-probe.java:67-197` 的 `SQLiteCipherSpec`/只读打开链；为避免失败尝试影响活跃主库，`EnMicroMsg.db` 通过同 inode 硬链接句柄打开，不复制 5.84 GB 文件。当前全量返回 `integrity_check=ok`、page size 1024、5,700,355 页和 714 个 schema 对象，脱敏结果见 `main-aggregates.json`。

## key 派生与历史 device ID

来源：`smali-classes11/kh5/b0.smali:1658-1716`。

```text
1658 IMEISave.a()
1687 append(deviceId)
1689 append(uin)
1695 getBytes()
1699 pk/k.g(bytes)
1705-1707 substring(0, 7)
1716 kh5/f.w(path, digest7, ...)
```

来源：`smali-classes11/com/tencent/mm/storagebase/IMEISave.smali:15-145`。

```text
28-32  add current device-id candidate
36-40  add fallback/empty candidate
45-75  RC4 decrypt KeyInfo.bin
86-92  read historical device-id lines
141-143 add compatibility candidate
```

结论：数据库 key 是 device ID 与 UIN 拼接后的摘要前 7 个十六进制字符；历史 device ID 从加密 `KeyInfo.bin` 恢复。公开报告不包含任何实际 key、UIN、device ID 或文件内容。

## 独立加密库通用打开链

来源：`smali-classes11/x91/l0.smali:35-145`。

`x91/l0.b()` 在加密模式下调用 `kh5/b0.R(path, uin, device-id, table-map, ...)`；`WxExpt.db` 的注册入口 `f92/l.smali:80-150` 使用同一包装器。来源：`smali-classes11/kh5/b0.smali:1523-1716`。

`kh5/b0.R()` 遍历 `com.tencent.mm.storagebase.IMEISave.a()`，对每个 device ID 与 UIN 拼接取摘要前 7 个十六进制字符，再调用 `kh5/f.w()` 打开。该链解释了 `AppBrandComm.db`、`WxExpt.db`、`WxCgiReport.db`、`newuba.db` 能用同一类 key 打开；实际 key 不公开。

## EnMicroMsg 与 WxFileIndex 特殊打开标志

来源：`smali-classes11/kh5/f.smali:1507-1524`。

当文件名以 `EnMicroMsg.db` 或 `WxFileIndex.db` 结尾时，打开 flags 追加 `0x20`；两者走相同的加密打开路径。

## AppBrandComm 注册

来源：`smali-classes7-full/com/tencent/mm/plugin/appbrand/app/fa$$a.smali:31-115`。

```text
31 requireAccountInitialized()
41 account database directory
47 append "AppBrandComm.db"
59-108 iterate AppBrand ORM registry
115 x91/l0.a(hashCode, path, tableMap, true)
```

来源：`smali-classes7-full/com/tencent/mm/plugin/appbrand/app/l.smali:20-298`。

该静态构造器逐个注册 AppBrand 业务表类。`f5.smali:14-40` 进一步给出表名 `DevPkgLaunchExtInfo` 及 `getCreateSQLs` 调用。

## EnResDown 注册与特殊 key 路径

来源：`smali-classes16/com/tencent/mm/pluginsdk/res/downloader/model/l0.smali:39-93`。资源下载器同时准备 `ResDown.db` 与 `EnResDown.db`，加密打开时传入 `0x80000000` 的 UIN 哨兵、`cp/w0.g(true)` 的 device 字符串和 `p0.g` 表注册表。

来源：`smali-classes11/com/tencent/mm/pluginsdk/res/downloader/model/p0.smali:22-34`。ORM 注册唯一业务表 `ResDownloaderRecordTable`；实际导出为 34 列/151 行，完整列见 `schema.md` 和 `database-aggregates.json`。该特殊 UIN 哨兵解释了它与账号库 key 不同。

## enFavorite 注册

来源：`smali-classes6/im/ld.smali:20-104,140-190`。构造器注册 `im.ed` 至 `im.kd` 七个 provider，并以 `kh5/b0.R(..., uin, cp/w0.g(true), table-map, true)` 打开 `enFavorite.db`。

七个 provider 对应 `FavItemInfo`、`FavSearchInfo`、`FavEditInfo`、`FavCdnInfo`、`FavTagInfo`、`FavConfigInfo`、`FavDelInfo`；ORM 模型分别为 `im/o3`、`im/q3`、`im/n3`、`im/k3`、`im/r3`、`im/l3`、`im/m3`。`hd2/f.smali` 的表名列表与实测导出 7 表完全一致。

## WxFileIndex 注册与表角色

来源：`smali-classes11/mp3/z0.smali:741-815`、`ny1/l.smali:2909-3030,3228-3645`、`z02/m1.smali:1573`。文件名 `WxFileIndex.db` 走 `kh5/f.smali:1507-1524` 的 `0x20` 特殊打开 flag；`ny1/l.smali` 的 SQL 明确出现 `WxFileIndex3`、`WxFileIndexDirty`、`WxFileIndexDirtyWithTalker`、`WxFileIndexRefresh`、`WxFileIndexRegistry`。

实际导出还存在 `WxFileIndexDownloadMigration` 和 `WxFileIndexLinkify`，分别对应下载迁移与路径链接化维护。主表字段和 6 表/317,265 行聚合见 `schema.md`。

## Edge 缓存注册

来源：`smali-classes16/m92/d.smali`。同一 ORM 模型分别生成 `EdgeComputingCacheDataModel_Instance` 与 `EdgeComputingCacheDataModel_Normal`；`m92/c.smali` 包装对应表 DAO。

`smali-classes16/t92/a.smali` 以 `configID`、`reportTimeEC`、`data` 构造 `EdgeComputingCacheDataModel`，继承模型 `im/s2` 的 `initAutoDBInfo` 明确列类型 `TEXT/LONG/TEXT` 与 `rowid` 主键。实际导出 2 表/2 行。

## MicroMsgPriority 特殊 key 与 Schema

来源：`smali-classes11/tx3/h.smali:37-105`。它不走 `kh5/b0.R()` 的通用 key 路径，而是拼接 `gp0/m.l()`（UIN 字符串）、`b41/y1.u()`（登录用户名）和 `cp/w0.g(true)`（device/D3 字符串），执行 `pk/k.g(bytes)`，取 MD5 十六进制前 7 字符并转为字节串，再直接调用 WCDB `openOrCreateDatabase`。

三个输入的静态来源边界如下：

- `gp0/m.smali:1538-1547` 从账号状态读取 UIN，`gp0/m.smali:37-105` 随后以十进制字符串参与拼接。
- `b41/y1.smali:2088-2105` 从 `userinfo` 的配置项 `id=2` 读取登录用户名；返回 null 时 Java `StringBuilder.append(String)` 写入字面量 `null`。
- `cp/w0.smali:734-763` 返回 primary D3；为空时使用字面量 fallback `1234567890ABCDEF`。

D3 primary 由 `cp/g0` / `cp/u0` 的认证缓存提供。`cp/u0.smali:31-117` 使用 id `"a"` 的 UTF-8 `UUID.nameUUIDFromBytes` 作为目录名，并在 `.auth_cache/<uuid>/0..4` 中轮转保存带 CRC32 的值；对应目录可由 `"a"` 确定性重算。`cp/h0.smali:29-59` 的兼容回退读取 `CompatibleInfo.cfg` 的项 `258`，相同 fallback 值会被转换为 null，最终仍由 `cp/w0.g(true)` 使用字面量 fallback。`cp/p.smali:289-323` 证明该文件路径为 `MicroMsg/CompatibleInfo.cfg`。

授权快照复核显示 primary D3 目录存在但没有缓存文件，`CompatibleInfo.cfg` 为 0 字节。后续用 14 个 UIN 形态、4 个登录用户名/null 形态和 3 个 D3/null/空形态做 WCDB default 只读验证，其中一个组合成功打开快照；这证明 key 输入落在源码公式枚举范围内，但不反向证明数据库创建时使用的是 primary、fallback 或空 D3。

它使用的 WCDB 默认参数也与通用库不同。`tx3/h.smali:83-105` 调用不含 `SQLiteCipherSpec` 的 `SQLiteDatabase.openOrCreateDatabase(String, byte[], ...)`；`SQLiteDatabase.smali:1352-1373` 将 null spec 传给打开链，`Database.smali:1689-1721` 的 `setCipherKey(byte[])` 固定 page size 4096 并选择 `CipherVersion.defaultVersion`。因此不能把 `kh5/f.smali:94-110` 的 page size 1024 / SQLCipher v1 参数直接套用于该库。

来源：`smali-classes11/tx3/h.smali:131-209`：取得 native connection 后调用 `PriorityJni.nativeInit`，并创建 `PriorityConfig(type INTEGER PRIMARY KEY, version INTEGER)`。`smali-classes12/ox3/m.smali:187-281` 初始化多个 C2C 图片/优先级任务组件，`ox3/m.smali:995-1010` 明确维护 `C2CMsgAutoDownloadRes.createtime`。

动态只读复核工具见 `../../tools/wcdb-probe.java`。`../../tools/wcdb-probe.java:67-136` 构造可选 SQLCipher compatibility spec、可选逐候选隔离副本并调用微信 `libcso.so`/`libWCDB.so` 的 JNI 初始化链；`../../tools/wcdb-probe.java:138-197` 使用相同 null cipher spec 和 `SQLiteDatabase.openDatabase(..., OPEN_READONLY, ...)`，并在输出前排除空路径、零长度文件、空 schema 和零页伪命中；`../../tools/wcdb-probe.java:198-301` 仅发布行数、列名/类型、索引名和聚合；候选入口 `../../tools/wcdb-probe.java:337-373` 只输出候选序号和脱敏结果/异常。该动态结果用于验证打开路径，不替代上述 smali 对创建路径的证明。

当前快照的 D3 primary cache 为空、兼容 cache 为 0 字节；但按上述 key 公式枚举的 WCDB default 只读输入有一个命中，快照以 `integrity_check=ok`、page size 4096、36 页打开并得到 23 个对象/16 表/1,003 行，见 `priority-aggregates.json`。SQLCipher compatibility 1/2/3/4 组合仍全部失败，说明该库不沿用主库的 SQLCipher v1 路径。

## FTS5 搜索索引特殊 key 与打开路径

`FTS5IndexMicroMsg_encrypt.db` 不使用 `kh5/b0.R()` 的 device ID + UIN 通用派生路径。`smali-classes11/com/tencent/mm/plugin/fts/p.smali:36-178` 先读取配置项 `t3.Ad`；该值为空时按以下顺序拼接并取 MD5 十六进制前 7 个字符，再写回 `t3.Ad`：

```text
gp0/m.l() + cp/w0.g(true) + b41/y1.u()
UIN string + current/fallback D3 + login username or Java "null"
pk/k.g(bytes).substring(0, 7)
```

`p.smali:215-275` 随后把该 7 字符值转换为 UTF-8 字节，以 `SQLiteCipherSpec=null` 调用 `SQLiteDatabase.openDatabase`。WAL pool 开关只改变 flags：启用时为 `0x30000020`，否则为 `0x10000020`；cipher key 字符串本身先由 `p.smali:104-178` 生成或读取。这个结果解释了为什么不能把 `kh5/f.smali:94-110` 的 SQLCipher v1 / page size 1024 参数直接套到 FTS 索引。

文件选择和生命周期证据如下：

- `smali-classes12/com/tencent/mm/plugin/fts/k0.smali:95-181` 在账号目录中区分 `IndexMicroMsg.db`、未加密 `FTS5IndexMicroMsg.db` 和 `FTS5IndexMicroMsg_encrypt.db`。
- `smali-classes11/com/tencent/mm/plugin/fts/p.smali:45-84` 构造 `FTS5IndexMicroMsg_encrypt.db` 路径并在 `:314-329` 交给 `FTSIndexDB` 初始化。
- `smali-classes11/b41/d.smali:26-45` 将 `EnMicroMsg.db`、`EnMicroMsg.dberr*` 和 `FTS5IndexMicroMsg_encrypt.db` 归入同一文件分类动作，但不参与 key 派生。
- `smali-classes11/f94/c.smali:26-116` 证明修复入口会删除主文件及 `-journal`、`-wal`、`-shm` 后重启，说明这些 sidecar 属于同一数据库生命周期。

`../../tools/wcdb-probe.java:67-197` 增加了同样的 null cipher spec 只读打开能力、逐候选隔离副本和伪命中拒绝；`../../tools/wcdb-probe.java:198-301` 输出可见表计数/列类型，并可尝试用 `fts5vocab` 仅输出词项/文档/出现次数聚合，不输出索引词或文档内容。动态复核先在 15,126,528 字节授权快照上以 `integrity_check=ok` 打开，随后对当前全量文件执行相同的只读 key/打开路径并取得 160 个 `sqlite_master` 对象、270,018 页及表级行数聚合，见 `fts-aggregates.json`。因此 FTS 标为“当前全量内容级只读打开已验证”，但不公开词项或文档内容。
