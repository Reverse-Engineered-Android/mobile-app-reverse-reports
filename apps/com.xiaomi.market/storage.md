# 本地存储

## 关键文件

| 路径 | 内容 |
|---|---|
| `/data/data/com.xiaomi.market/files/app_security_data` | 最近服务端安全检测结果，919 字节 |
| `/data/data/com.xiaomi.market/files/mmkv/com.xiaomi.market_preferences` | 默认 MMKV，131072 字节，含扫描开关与时间戳 |
| `/data/data/com.xiaomi.market/databases/market_2.db` | 主业务 SQLite，18,059,264 字节 |
| `/data/data/com.xiaomi.market/shared_prefs/com.xiaomi.market_preferences.xml` | 旧命名 SharedPreferences，647 字节 |

## 安全相关 MMKV

| key | 设备值/含义 |
|---|---|
| `enableAutoSecurityCheck` | true |
| `enableScheduleRiskNotification` | true |
| `useSecurityDetectV2` | true |
| `lastAppSecurityCheckTime` | `2026-11-06 04:59:56.916 +08:00` |
| `lastAutoAppSecurityCheckTime` | 同上 |
| `requestAppSecurityCheckDuration` | 7200000 ms |
| `enableRiskNotificationModel` | 不同代际 false/true |
| `enableRiskNotificationModelPrediction` | false |

MMKV 是追加式多代际存储，同一 key 可能出现多个历史值；报告中取文件序靠后的最新快照。

## market_2.db

数据库通过 WAL 快照打开，`PRAGMA integrity_check` 返回 ok。与本任务相关或能反映应用列表规模的主要表：

| 表 | 行数 | 关键列 |
|---|---|---|
| `app` | 119 | 商店应用详情，含 `package_name`、版本、签名、隐私风险字段 |
| `app_usage` | 168 | `package_name`、启动次数、启动天数、最近使用、前台总时长 |
| `local_app_md5` | 160 | 包名、installer、时间、MD5、大小、source_dir、split MD5、版本 |
| `h_b` | 614 | 行为类型、detail JSON、时间戳 |
| `app_active_stat` | 0 | 结构为每应用活跃统计；字段见 `AppActiveStatRecord` |
| `install_history` | 47 | 包名、安装/活跃时间、版本、来源、refInfo |
| `package_remove_history` | 36 | 包名、卸载/更新时间、版本 |
| `update_history` | 123 | 包名、更新时间、版本、是否自动下载/更新 |
| `update_fail_history` | 108 | 包名、错误、本地/服务端版本 |
| `pending_update` | 56 | 待更新列表 |

`AppActiveStatRecord` 结构字段包括：

```text
packageName, versionCode, versionName, installer, channel,
activeTime, activeType, apkHash, firstInstallTime, lastUpdateTime,
userId, traceId, miuiActiveId, miuiActiveTime, installRefInfo,
activeRefInfo, isUpdate, isOneTrackReported
```

`RiskNotificationFeatureRaw` 设计字段包括：

```text
featureId, dateTime, isWeekDay, retainAppCount, riskAppCount,
riskAppDurationPast3Day, riskAppDurationPast1Day, entryForegroundTime
```

设备快照中未见该表落库，代码显示表按模型开关与保存成功与否创建。

## 缓存与历史

- `appInfoCachedConnection`：2772 行请求缓存；
- `downloadCachedConnection`：1004 行下载缓存；
- `download_app` / `download_splits` 等表记录下载任务；
- `local_app_md5` 保存本机 APK 哈希，可支撑更新差分与安装校验。

## 清理

代码存在历史清理逻辑；具体策略以类实现为准。报告未对数据库做过修改。
