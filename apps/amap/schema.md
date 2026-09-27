# 高德数据库 Schema

下列 DDL/列名来自 native SQL 字符串和只读 SQLite 结构检查。所有真实行值、
账户后缀、城市标识和内容字段均不公开；账户维度后缀统一写作 `<user>`。

## `bedstone.db`

`fLocationInfo` DDL 来自 `libamapbadge.so` 的 SQL 字符串：

```sql
CREATE TABLE IF NOT EXISTS fLocationInfo (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    trafficType INT,
    poiid TEXT,
    name TEXT,
    x INT,
    y INT,
    createTime LONG,
    flocationJson TEXT
);

CREATE INDEX IF NOT EXISTS fLocationInfo_Index_Name
    ON fLocationInfo (name);
```

| 列 | 类型 | 语义 |
| --- | --- | --- |
| `trafficType` | `INT` | 记录/交通类型 |
| `poiid` | `TEXT` | POI 标识 |
| `name` | `TEXT` | 地点名 |
| `x`, `y` | `INT` | 应用内部坐标 |
| `createTime` | `LONG` | 创建时间 |
| `flocationJson` | `TEXT` | 扩展位置 JSON |

当前样本 `fLocationInfo` 为 35 行，`PRAGMA integrity_check` 为 `ok`。

## `girf_sync.db`

### 对象聚合

| 对象/类别 | 行数 | 说明 |
| --- | ---: | --- |
| `LOCAL` | 15 | 本地对象 |
| `ROUTE_HISTORY_V2_SNAPSHOT` | 1 | 路线基线 |
| `ROUTE_HISTORY_V2_SNAPSHOT<user>` | 196 | 当前路线快照 |
| `ROUTE_SNAPSHOT<user>` | 1 | 路线摘要 |
| `SEARCH_SNAPSHOT` | 1 | 搜索基线 |
| `SEARCH_SNAPSHOT<user>` | 76 | 当前搜索快照 |
| `SETTING_SNAPSHOT` | 13 | 设置基线 |
| `SETTING_SNAPSHOT<user>` | 18 | 当前设置 |
| `SYS_CONFIG` | 5 | 系统配置 |
| `USER<user>` | 296 | 用户同步对象 |
| `__internal_db_category_` | 1 | 内部类别 |
| `POI_SNAPSHOT*`, `UGC_POI_SNAPSHOT*`, `TEMP*` | 0 | 本次样本为空 |

### 已验证列

```text
ROUTE_HISTORY_V2_SNAPSHOT*
  item_id, type, route_name, update_time, data, deleted, stale

ROUTE_SNAPSHOT<user>
  id, item_id, route_name, route_type, route_len, create_time,
  from_name, to_name, start_time, end_time, mCostTime, deleted,
  busPathSection, route_alias

SEARCH_SNAPSHOT*
  item_id, data, history_type, adcode, update_time, deleted, stale

SETTING_SNAPSHOT*
  item_id, data, reserved1

USER<user>
  type, id, data, payload, ts
```

`data`、`payload` 等列是序列化/JSON 字段，本文不展示其内容。库内 18 个表、
40 个索引，`PRAGMA integrity_check` 为 `ok`。`LOCAL`、`SYS_CONFIG` 和
`__internal_db_category_` 的完整 DDL 未可靠恢复，因此不作推测。

## `ackor_offline_compile.db`

对象：`ackor_offline`、`ackor_offline_version`、`sqlite_sequence`。

| 表 | 已验证结构 |
| --- | --- |
| `ackor_offline` | 4 行；含 `_id`、`primary_id`、`city_id`、`data_type`、`status`、`dl_size`、`data_size`、`dl_time`、`user_mark`、`data_content` |
| `ackor_offline_version` | 1 行；含 `_id`、`version` |

`data_type`/`status` 的已知类别分别为 `map`/`route` 和 `7`。`data_content`、
`city_id` 的值、`user_mark` 和任何行值不公开。

## 检查方法

使用只读 URI 和 `PRAGMA query_only=ON` 枚举 `sqlite_master`、`PRAGMA
table_info`、行数和 `PRAGMA integrity_check`。工具脚本见
[tools/README.md](../../tools/README.md)。
