# 本地存储（设备端只读核对）

本节所有结论来自对 Android 知乎应用私有数据目录的**只读**检查：
文件清单、SQLite `integrity_check`、DDL 与聚合行数。**未读取任何行值**，
未写入设备，未发起业务网络请求。

设备：自有 root shell，应用私有目录通过 procfs 切换到 Android 主根后读取
（`/proc/1` + `/root` 下的 `data/data/com.zhihu.android/`），仅执行只读列举与键名扫描。
`databases/` 共 102 个文件，其中 **42 个是独立 SQLite 库**，60 个为
`-shm`/`-wal`/`-journal`/`-mj`/`.lock` 边车文件；总占用 6.7 MB。
`shared_prefs/` 共 103 个 XML。

## 1. 数据库清单（42 个库 / 148 张表 / 677 行）

| 文件 | 大小 | 表数 | 行数 | 主要内容 |
| --- | ---: | ---: | ---: | --- |
| `im_sticker.room` | 90 KB | 4 | 204 | 表情包（`im_sticker` 188、`im_sticker_group` 14） |
| `dynamic_layout.db` | 1.6 MB | 3 | 157 | 动态布局模板（`MpLayoutInfo` 155） |
| `za_log_db_new_storage_v0` | 324 KB | 3 | 95 | **ZA 待上报日志**（`ZaNewDbItem` 93，`data` BLOB） |
| `pushsdk.db` | — | 6 | 32 | 推送 SDK |
| `zx.db` | — | 2 | 29 | 咨询/ZX 模块 |
| `bd_tea_agent_240734` | — | 8 | 26 | 字节 TeaAgent 事件 |
| `room_launch_ad` | — | 5 | 23 | 开屏广告缓存 |
| `zhihu_search.room` | 48 KB | 6 | 15 | **搜索历史与热词**（见 §2） |
| `dim.db` | — | 2 | 11 | 设备信息映射 |
| `gtc3.db` | — | 4 | 11 | 个推 |
| `vader-client-log` | — | 3 | 7 | Vader 客户端日志 |
| `oneid.db` | — | 2 | 5 | **设备/账号标识**（表 `r`） |
| `cg.db` | — | 2 | 4 | 内容治理 |
| `ABLog_1.0.db` | — | 3 | 3 | AB 实验日志（`ABLogDbItem`） |
| `filedownloader.db` | 24 KB | 3 | 3 | **下载任务**（`filedownloaderConnection` 2） |
| `msre.db` / `msvolcano.db` | — | 3/2 | 3 | 火山/msre 事件 |
| `read_progress.room` | — | 3 | 3 | **阅读进度**（`read_progress` 1） |
| `tab_order.room` | — | 3 | 3 | 首页 tab 顺序 |
| `ua.db` | 40 KB | 5 | 3 | **UA 采集**（`__et` 1、`__sd` 1） |
| `zhihu.db` | — | 3 | 3 | **浏览历史**（`history` 1） |
| `zhihu_strategy_consume.db` | — | 3 | 3 | 策略消费 |
| `MediaUploader.db` | — | 5 | 2 | **上传媒体跟踪**（见 §3） |
| `account_provider.db` | 16 KB | 2 | 2 | **账号**（表 `account` 1） |
| `ad_log.room` | — | 3 | 2 | 广告日志 |
| `apm_monitor_t1.db` | 36 KB | 6 | 2 | **APM 监控**（见 §2） |
| `audio_float.db` | — | 3 | 2 | 音频悬浮窗 |
| `begin_end_database` / `begin_end_duga_database` | — | 3 | 2 | 起止事件（duga） |
| `manuscript_preload_html.db` | — | 3 | 2 | **盐选试读缓存**（`manuscript_html`） |
| `panel_data.room` | — | 3 | 2 | 面板数据 |
| `read_later` | — | 4 | 2 | **稍后读**（`ReadLaterModel`/`AudioReadLaterModel`） |
| `zhi-track-db-online_v1` | — | 3 | 2 | **行为埋点**（`ZhiTrackDBItem`） |
| `193564@bd_tea_agent.db` | 44 KB | 8 | 1 | TeaAgent（`eventv3`/`launch`/`page`/`profile`/`trace`/`packV2`/`custom_event`） |
| `MessageStore.db` / `MsgLogStore.db` | — | 2/3 | 1 | 消息别名与消息统计 |
| `accs.db` / `message_accs_db` | — | 2/3 | 1 | 长连接流量 |
| `bd_tea_agent_1001213` | — | 8 | 1 | TeaAgent（另一 app id） |
| `lib_log_queue.db` | — | 2 | 1 | 日志队列 |
| `npth_log.db` | — | 2 | 1 | NTP 日志 |
| `umeng_zero_cache.db` | — | 2 | 1 | 友盟缓存 |

全部 42 个库 `integrity_check = ok`。

## 2. 关键 Schema

### 2.1 搜索（`zhihu_search.room`）

```sql
CREATE TABLE `search_history` (`key_words` TEXT NOT NULL, `pinyin` TEXT NOT NULL,
  `initial` TEXT NOT NULL, `update_time` INTEGER NOT NULL, `uid` TEXT,
  PRIMARY KEY(`key_words`));
CREATE TABLE `search_hot_words` (`uuid` TEXT NOT NULL, `display_query` TEXT NOT NULL,
  `model` TEXT, `tab_name` TEXT, `update_time` INTEGER NOT NULL, PRIMARY KEY(`uuid`));
CREATE TABLE `search_hot_words_tabs` (`name` TEXT NOT NULL, `model` TEXT,
  `update_time` INTEGER NOT NULL, PRIMARY KEY(`name`));
CREATE TABLE `search_tabs` (`type` TEXT NOT NULL, `model` TEXT,
  `update_time` INTEGER NOT NULL, PRIMARY KEY(`type`));
```

`search_history.uid` 表明搜索历史按账号维度存储。

### 2.2 浏览历史（`zhihu.db`）

```sql
CREATE TABLE `history` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `type` TEXT NOT NULL, `data_id` TEXT NOT NULL, `data` TEXT,
  `viewed_at` INTEGER NOT NULL, `read_progress` INTEGER, `user_id` TEXT);
```

### 2.3 账号（`account_provider.db`）

```sql
CREATE TABLE account(_id INTEGER PRIMARY KEY, refresh_token TEXT, uid INTEGER,
  name TEXT, phone_no INT, email TEXT, gender INT, description TEXT,
  headline TEXT, extras TEXT);
```

含 `refresh_token`、`phone_no`、`email` 等敏感列（列名公开，值不读取）。

### 2.4 上传（`MediaUploader.db`）

```sql
CREATE TABLE `business_table` (`content_id` INTEGER, `staging_content_id` INTEGER,
  `content_type` INTEGER, `cover_url` TEXT, `percent` REAL NOT NULL,
  `uploadedSize` INTEGER NOT NULL, `totalSize` INTEGER NOT NULL, `status` INTEGER,
  `extras` TEXT, PRIMARY KEY(`content_id`));
CREATE TABLE `media_table` (`media_id` TEXT NOT NULL, `object_key` TEXT NOT NULL,
  `business_id` INTEGER, `path` TEXT, `cached_path` TEXT, `album_path` TEXT,
  `status` INTEGER, `media_type` INTEGER, `extras` TEXT, PRIMARY KEY(`media_id`),
  FOREIGN KEY(`business_id`) REFERENCES `business_table`(`content_id`) ON DELETE CASCADE);
CREATE TABLE `xiangfa_table` (`xiangfa_content_id` INTEGER,
  `xiangfa_staging_content_id` INTEGER, `xiangfa_extras` TEXT,
  PRIMARY KEY(`xiangfa_content_id`));
```

### 2.5 下载（`filedownloader.db`）

```sql
CREATE TABLE filedownloader( _id INTEGER PRIMARY KEY, url VARCHAR, path VARCHAR,
  status TINYINT(7), sofar INTEGER, total INTEGER, errMsg VARCHAR, etag VARCHAR,
  pathAsDirectory TINYINT(1) DEFAULT 0, filename VARCHAR, connectionCount INTEGER DEFAULT 1);
CREATE TABLE filedownloaderConnection( id INTEGER, connectionIndex INTEGER,
  startOffset INTEGER, currentOffset INTEGER, endOffset INTEGER,
  PRIMARY KEY ( id, connectionIndex ));
```

### 2.6 埋点与监控

```sql
-- za_log_db_new_storage_v0
CREATE TABLE `ZaNewDbItem` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `timeStamp` INTEGER NOT NULL, `data` BLOB, `priorityType` INTEGER NOT NULL,
  `pbEventType` INTEGER NOT NULL, `uploading` INTEGER NOT NULL);

-- zhi-track-db-online_v1
CREATE TABLE `ZhiTrackDBItem` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `timeStamp` INTEGER NOT NULL, `eventData` BLOB NOT NULL, `environmentData` BLOB NOT NULL,
  `environmentMd5` TEXT NOT NULL, `logType` TEXT NOT NULL, `priorityType` TEXT NOT NULL,
  `uploading` INTEGER NOT NULL);

-- apm_monitor_t1.db
CREATE TABLE t_apiall ( _id INTEGER PRIMARY KEY AUTOINCREMENT, version_id Integer,
  front Integer, timestamp Integer, hit_rules Integer DEFAULT 0, traffic_value Integer DEFAULT 0,
  type TEXT, type2 TEXT, type3 TEXT, type4 TEXT, network_type Integer, sid Integer,
  is_sampled Integer, delete_flag Integer );
CREATE TABLE t_battery ( _id INTEGER PRIMARY KEY AUTOINCREMENT, ..., status Integer,
  scene TEXT, accumulation Integer, source TEXT, process TEXT, main_process Integer, sid TEXT );
CREATE TABLE t_traffic ( _id INTEGER PRIMARY KEY AUTOINCREMENT, ..., network_type Integer,
  front Integer, type TEXT, type2 Integer, value Integer, send Integer, sid Integer, content TEXT );

-- ua.db
CREATE TABLE __et(id INTEGER primary key autoincrement, __i TEXT, __e TEXT, __s TEXT,
  __t INTEGER, __av TEXT, __vc TEXT);
CREATE TABLE __sd(id INTEGER primary key autoincrement, __ii TEXT unique, __a TEXT, ...
  __sp TEXT, __pp TEXT, __av TEXT, __vc TEXT);
```

`ZaNewDbItem.pbEventType` + `data` BLOB 对应 ZA protobuf 事件；
`hit_rules`/`is_sampled` 表明上报按规则命中与采样控制。

### 2.7 盐选试读（`manuscript_preload_html.db`）

```sql
CREATE TABLE `manuscript_html` (`url` TEXT NOT NULL, `request_url` TEXT NOT NULL,
  `html` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(...));
```

即会员内容的 HTML 试读/预加载缓存，落盘后可离线查看已缓存片段。

### 2.8 阅读进度与稍后读

```sql
-- read_progress.room
CREATE TABLE `read_progress` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `content_id` TEXT, `content_type` TEXT, `position` INTEGER NOT NULL,
  `progress` INTEGER NOT NULL, `offset` INTEGER NOT NULL, `user_id` TEXT, `time` INTEGER NOT NULL);
-- read_later
CREATE TABLE `ReadLaterModel` (`fakeUrl` TEXT NOT NULL, `userId` TEXT NOT NULL, ...
  `position` TEXT NOT NULL, `contentType` INTEGER NOT NULL, `contentToken` TEXT ...);
```

## 3. 非数据库运行态文件

`files/` 目录（只读列举）含：`ABData`/`ABData2`（AB 实验）、`INSTALLATION`、
`MossDownloader`/`OneRNMossDownloader`（RN 资源）、`NetCache`（**解密后的接口响应缓存**，
见 [evidence.md](evidence.md) §4）、`Pers`、`ZhJniLibs`、`account`、`ad`、`aegon`、
`apminsight`、`awcn_strategy`、`cache`、`cookieCache`、`crash`、`com.zhihu.android-guard.properties`
（加固属性）、`ezviz`/`Moss` 系列哈希目录等。`files/tx_player` 为本样本中不存在
（DRM 播放缓存为空，说明该设备未播放过 DRM 内容）。

## 4. SharedPreferences（103 个，键名级核对）

本主题相关（只记录文件名与键名，不读取值）：

| 文件 | 键名 |
| --- | --- |
| `zhihu_ruid_shared_preferences.xml` | `preference_id_ruid`、`preference_id_osdid` |
| `zhihu_hodor_privacy.xml` | `last_cold_track_installed_apps_millis` |
| `zhihu_hodor_privacy_upgrade.xml` | `secureIdMap`、`model` |
| `Map_Privacy.xml` | `privacyMode`、`random_uuid` |
| `RUID_NET_CACHE_SP_FILE.xml` | `https://api.zhihu.com/zst/events/c` |
| `IMAGE_X_AUTHORIZATION.xml` | `KEY_AUTH_LIST` |
| `account_privacy_rights_<hash>.xml` | 隐私权利设置（2 个账号） |

`zhihu_hodor_privacy.xml` 中 `last_cold_track_installed_apps_millis` 的**存在**证明
[privacy.md](privacy.md) §3.1 的应用列表冷启动采集计时器在本机被写入。

## 5. 证据等级

- 文件清单、`integrity_check`、DDL、行数、SP 键名：**已验证**（只读查询）。
- 具体行值、账号内容、Token：**未读取**（按 [SECURITY.md](../../SECURITY.md)）。
- 各库的清理周期与服务端对应关系：**不可达**。
