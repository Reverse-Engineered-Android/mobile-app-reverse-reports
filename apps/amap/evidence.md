# 高德逆向证据

## DEX 线索

`classes5.dex` / `classes6.dex` 中的可靠字符串：

```text
com.autonavi.jni.bedstone.BaseMapFrequentLocationsJni
com.autonavi.sync.GirfSyncJni
com.autonavi.sync.GirfSyncServiceJni
girf_sync.db
initDb
initDecrypt
```

## Native 字符串

| 文件 | 文件偏移 | 字符串 |
| --- | ---: | --- |
| `libamapbadge.so` | `0x3a59b` | `insert into fLocationInfo (...) values(?,?,?,?,?,?,?)` |
| `libamapbadge.so` | `0x3af40` | `CREATE INDEX ... fLocationInfo ...` |
| `libamapbadge.so` | `0x3bb6a` | `bedstone.db` |
| `libamapbadge.so` | `0x3e1b5` | `create table ... fLocationInfo ...` |
| `libamapsync.so` | `0xa695` | `1265984512` |
| `libamapsync.so` | `0xa84f` | `key_name` |
| `libamapsync.so` | `0xb10d` | `__internal_db_category_` |
| `libamapsync.so` | `0xb8ce` | `girf_sync.db` |

## `libamapbadge.so:0x87954`

```asm
87968: mov  x0, x1
8796c: mov  x19, x1
87970: bl   strlen@plt
87974: mov  x2, x0
87978: mov  x0, x20
8797c: mov  x1, x19
87980: bl   girf_sqlite3_key@plt
87984: cmp  w0, #0
```

证据等级：**已验证**。`x20` 是数据库句柄，`x19` 是 key 字符串指针，`strlen` 返回值进入 `x2`，即调用 `girf_sqlite3_key(db, key, key_len)`。

## `libamaprsq.so:0x3c694`

```asm
3c694 <girf_sqlite3_key>:
3c69c: mov  w3, w2
3c6a0: mov  x2, x1
3c6a4: mov  w1, wzr
3c6a8: bl   3c6d4
```

证据等级：**已验证**。导出函数把旧式参数适配到内部路径；`girf_sqlite3_key_v2` 入口位于 `0x3c6b8`。

## `libamapsync.so:0x2eb34`

```asm
2eb34: sub  sp, sp, #0x60
2eb58: cbz  x1, 2ebd4
...
2ebb8: mov  w2, w0
2ebbc: mov  x0, x19
2ebc0: mov  x1, x20
2ebc4: bl   girf_sqlite3_key@plt
2ebc8: mov  w19, w0
```

证据等级：**结构已证实**。stripped 函数 `sub_2eb34` 通过多个 helper 构造 key 缓冲，再以数据库句柄、key 指针和长度调用 key 安装函数。候选 key 的来源没有完整闭环。

## 方法复核

```text
jadx manifest
  -> version/package/SDK
DEX strings
  -> JNI and database names
readelf/AArch64 objdump
  -> symbol ABI and call sites
read-only SQLite
  -> integrity/schema/counts
```

地址均相对于对应 ARM64 SO；仓库不包含这些二进制。
