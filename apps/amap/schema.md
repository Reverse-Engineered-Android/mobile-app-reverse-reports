# 高德数据库 Schema

## `bedstone.db`

`fLocationInfo` DDL 来自 `libamapbadge.so:0x3e1b5`：

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

| 列 | 含义 |
| --- | --- |
| `trafficType` | 交通/记录类型 |
| `poiid` | POI 标识 |
| `name` | 地点名称 |
| `x`, `y` | 应用内部坐标 |
| `createTime` | 创建时间 |
| `flocationJson` | 扩展位置 JSON |

## `girf_sync.db`

### 对象与数量

| 对象 | 行数 | 作用 |
| --- | ---: | --- |
| `LOCAL` | 15 | 本地设置/对象 |
| `ROUTE_HISTORY_V2_SNAPSHOT` | 1 | 路线基线 |
| `ROUTE_HISTORY_V2_SNAPSHOT29728902` | 187 | 当前路线历史 |
| `ROUTE_SNAPSHOT29728902` | 1 | 路线摘要 |
| `SEARCH_SNAPSHOT` | 1 | 搜索基线 |
| `SEARCH_SNAPSHOT29728902` | 73 | 当前搜索历史 |
| `SETTING_SNAPSHOT` | 13 | 设置基线 |
| `SETTING_SNAPSHOT29728902` | 18 | 当前设置 |
| `SYS_CONFIG` | 5 | 系统配置 |
| `USER29728902` | 284 | 用户同步对象 |
| `__internal_db_category_` | 1 | 内部类别 |
| `POI_SNAPSHOT*` / `UGC_POI_SNAPSHOT*` / `TEMP*` | 0 | 本次样本为空 |

### 已确认列

```text
ROUTE_HISTORY_V2_SNAPSHOT*
  item_id, type, route_name, update_time, data, deleted, stale

ROUTE_SNAPSHOT29728902
  id, item_id, route_name, route_type, route_len, create_time,
  from_name, to_name, start_time, end_time, mCostTime, deleted,
  busPathSection, route_alias

SEARCH_SNAPSHOT*
  item_id, data, history_type, adcode, update_time, deleted, stale

SETTING_SNAPSHOT*
  item_id, data, reserved1

USER29728902
  type, id, data, payload, ts
```

`data`、`payload`、`flocationJson` 是 JSON/序列化字段。本文只展示列结构，不提供真实行。`LOCAL`、`SYS_CONFIG`、`__internal_db_category_` 的完整 DDL 未可靠恢复，不作推测。
