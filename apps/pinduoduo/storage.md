# 本地数据库格式与存储信息范围

数据库文件名中的账号派生部分已替换为占位符：`<UIDMD5>` 表示账号 ID 的 MD5，
`<UID>` 表示账号 ID 的十进制形式。所有行值、设备标识与凭据均不公开。

## 1. 总体形态

| 类别 | 数量 | 加密 | 说明 |
| --- | ---: | --- | --- |
| SQLite 数据库 | 22 | **明文**（`SQLite format 3`） | 无 SQLCipher/WCDB |
| SQLite 伴随文件 | 22 journal/shm/wal | — | 存在未 checkpoint 的 WAL |
| MMKV 存储 | 397 个文件（约 19 MB） | 多数**未加密** | 少数模块显式传入 crypt key |
| SharedPreferences | 12 个 xml | 明文 | — |
| `files/dynamic_so` | 26 个目录，74 MB | **明文 ELF**，未加壳 | 名称含 `_epoch毫秒_MD5` |

同一设备的应用数据目录合计约 157 MB，其中 `files/dynamic_so` 占 74 MB。

## 2. SQLite 数据库清单

### 2.1 聊天消息（每个账号一套）

`MsgDB_<UIDMD5>`，8 个文件，各 57,344 字节。

```sql
CREATE TABLE conversation (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  displayTime INTEGER, draft TEXT, ext TEXT, isPin INTEGER,
  lastLocalId INTEGER, lastMessageStatus INTEGER, lastMsgId TEXT,
  lastReadLocalId INTEGER, lastReadMsgId TEXT, logo TEXT, nickName TEXT,
  remindType INTEGER, summary TEXT, uid TEXT NOT NULL UNIQUE,
  unreadCount INTEGER, updateTime INTEGER);
CREATE TABLE message (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  clientMsgId TEXT UNIQUE, ext TEXT, fromUid TEXT, messageBody TEXT,
  msgId TEXT UNIQUE, status INTEGER, summary TEXT, time INTEGER,
  toUid TEXT, type INTEGER);
CREATE TABLE temp_message (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  clientMsgId TEXT, ext TEXT, fromUid TEXT, localId INTEGER NOT NULL UNIQUE,
  messageBody TEXT, msgId TEXT, retryTime INTEGER, status INTEGER,
  summary TEXT, time INTEGER, toUid TEXT, type INTEGER);
```

索引：`index_uid(uid)`、`index_msg_msgId(msgId)`、`index_msg_clientMsgId(clientMsgId)`、
`index_msg_localId(localId)`。

**信息范围**：会话列表（对端 uid、昵称、头像 URL、最后一条消息摘要、草稿、置顶、
免打扰、未读数、最后已读位置）+ 消息表（收发双方 uid、消息体、类型、状态、时间、
客户端消息 ID）。样本中全部 0 行（全新安装）。`journal_mode=wal`。

### 2.2 消息箱

`MsgboxDB_V2_<UID>`，主库 4,096 字节 + WAL 41,232 字节 + SHM 32,768 字节。
Room 数据库（含 `room_master_table`），

```sql
CREATE TABLE `conversation` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT, `msgGroup` TEXT, `nickName` TEXT,
  `logo` TEXT, `remindType` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL,
  `lastMsgCid` TEXT, `notificationId` TEXT, `lastReadMsgCid` TEXT,
  `displayTime` INTEGER NOT NULL, `updateTime` INTEGER NOT NULL,
  `summary` TEXT, `mentionText` TEXT, `mentionId` TEXT,
  `markUnread` INTEGER NOT NULL, `isTop` INTEGER NOT NULL, `ext` TEXT);
CREATE UNIQUE INDEX `index_conversation_msgGroup` ON `conversation` (`msgGroup`);
```

**信息范围**：多分组消息箱（msgGroup、昵称、头像、提醒类型、未读数、最后消息 CID、
通知 ID、@提及文本与 ID、标记未读、置顶、摘要、时间）。

### 2.3 通知

`pdd.db`，32,768 字节：

```sql
CREATE TABLE t_notification (
  ID INTEGER PRIMARY KEY AUTOINCREMENT, s_0 TEXT, s_3 TEXT, l_0 INTEGER,
  l_1 INTEGER, i_0 INTEGER, deleted INTEGER, extra TEXT, i_1 INTEGER,
  s_2 TEXT, msg_group INTEGER, msg_id TEXT,
  notification_id TEXT NOT NULL UNIQUE, s_1 TEXT,
  read_status INTEGER NOT NULL, time_stamp INTEGER, user_id TEXT);
```

索引：`notification_id`、`(s_0, time_stamp)`、`s_1`。
**信息范围**：本地通知（类型字段 `s_*`/`i_*`/`l_*` 被混淆命名，`msg_group`、
`msg_id`、`notification_id`、已读状态、时间戳、`user_id`）。

### 2.4 商城会话

`<UID>.db`，49,152 字节：

| 表 | 列 |
| --- | --- |
| `t_mall` | `ID, s_0..s_3, l_0, l_1, i_0, i_1, mall_avatar, mall_id UNIQUE, mall_name` |
| `t_mall_conversation` | `ID, c_id UNIQUE, l_0, l_1, s_0..s_3, i_0, i_1, message, ts, unread_count` |
| `t_mall_msg` | `ID, i_0, audio_unread, c_id, s_0..s_3, l_0, l_1, cmd, message, msg_id, offline_state, request_id, send_status, sort_id, ts, uuid` |
| `t_mall_del_conversation` | `ID, mall_id, msg_id, user_id` |

**信息范围**：店铺会话（店铺 ID/名称/头像、会话最后消息、未读数、消息体与命令、
离线状态、发送状态、排序 ID、UUID）。

### 2.5 组件存储（vita）

`vita-database`，49,152 字节 + WAL 416,152 字节：

| 表 | 列 |
| --- | --- |
| `UriInfo` | `uri, comp_id, version, relative_path, absolute_path, length, md5`（主键 `uri+comp_id+version`） |
| `VitaAccessInfo` | `comp_id, version, access_count, access_history`（主键 `comp_id+version`），39 行 |
| `VitaCleanInfo` | `comp_id, clean_time, recover_time, is_auto`（主键 `comp_id`） |
| `VitaVersionInfo` | `id, comp_id, version, time, operator`，42 行 |

**信息范围**：动态组件（Lego/JS 组件）的落盘路径与摘要、访问次数与访问时间序列、
清理/恢复时间、版本变更记录与操作者。`absolute_path` 是设备上的绝对路径，
按 `SECURITY.md` 不公开。

### 2.6 下载器（iris）

`iris_downloader_{main,support,titan}_v12.db`：

```sql
CREATE TABLE irisCallerInfo(
  iris_id TEXT PRIMARY KEY, inner_id INTEGER, url TEXT, filepath TEXT,
  filename VARCHAR, cache_filename TEXT, app_data TEXT,
  speed_limit INTEGER DEFAULT -1, status INTEGER, current_bytes BIGINT,
  total_bytes BITINT, priority INTEGER, callback_ui TINYINT(1) DEFAULT 0,
  verify_md5 TEXT, verify_key TEXT, timeout BIGINT, business VARCHAR,
  file_control_by_iris TINYINT(1) DEFAULT 0, max_connection_count INTEGER,
  last_modification BIGINT, wifi_required TINYINT(1) DEFAULT 0,
  weak_reference TINYINT(1) DEFAULT 0, send_broadcast TINYINT(1) DEFAULT 0,
  connection_type INTEGER, headers TEXT);
CREATE TABLE irisStartInfo(
  iris_id TEXT PRIMARY KEY, url TEXT, start_timestamp BIGINT,
  start_process TEXT, retry_count INTEGER, business TEXT);
```

样本行数：`irisCallerInfo` 2 行、`irisStartInfo` **111 行**（主库）；support/titan
两个变体为 0 行。

**信息范围**：下载任务的完整 URL、目标路径、文件名、缓存文件名、业务标识、
`verify_md5`/`verify_key`（下载后完整性校验材料）、速度限制、并发数、超时、
Wi-Fi 要求、连接类型、自定义 headers，以及**每次下载的开始时间与发起进程**。

### 2.7 下载器（okdownload）

`okdownload-breakpoint{,-support,-titan}.db`：

```sql
CREATE TABLE breakpoint(
  id INTEGER PRIMARY KEY, url VARCHAR NOT NULL, etag VARCHAR,
  parent_path VARCHAR NOT NULL, filename VARCHAR,
  task_only_parent_path TINYINT(1) DEFAULT 0, chunked TINYINT(1) DEFAULT 0,
  respHeaderStr VARCHAR);
CREATE TABLE block(
  id INTEGER PRIMARY KEY AUTOINCREMENT, breakpoint_id INTEGER,
  block_index INTEGER, start_offset INTEGER, content_length INTEGER,
  current_offset INTEGER);
CREATE TABLE okdownloadResponseFilename(
  url VARCHAR NOT NULL PRIMARY KEY, filename VARCHAR NOT NULL);
CREATE TABLE taskFileDirty(id INTEGER PRIMARY KEY);
```

样本行数：`breakpoint` 2、`block` 2、`taskFileDirty` 3；`okdownloadResponseFilename`
0。

**信息范围**：断点续传的 URL、ETag、落盘目录与文件名、分块开关、
**完整响应头字符串**，以及每个分块的起始偏移/总长/已下载偏移。

### 2.8 事件数据

`event_data{,_lifecycle,_support,_titan}.db`：

```sql
CREATE TABLE `event_data` (
  `log_id` TEXT NOT NULL PRIMARY KEY, `url` TEXT, `priority` INTEGER NOT NULL,
  `event_string` TEXT, `time` INTEGER NOT NULL, `importance` INTEGER NOT NULL);
CREATE INDEX `index_event_data_url_priority` ON `event_data` (`url`, `priority`);
```

样本：`event_data.db` 2 行；`log_id` 为 32 位十六进制，`event_string` 为约 1,004
字符的高熵串，`url` 约 22 字符，带 `priority`/`importance`/`time`。这是**待上报
事件队列**（离线缓冲），四个变体对应 base/lifecycle/support/titan 四条通道。

## 3. WAL 重放（只读）

`MsgboxDB_V2_<UID>` 的主库只有 4,096 字节（1 页），实际 schema 与数据都在 WAL 里。
WAL 头：`magic=0x377f0682`、`page_size=4096`、`seq=151100134`，10 帧，覆盖页
`1,2,3,3,1,2,4,5,6,7`。

只读重放方式：把主库与 `-wal` 一起复制到临时目录，以 `mode=ro` 打开并触发
checkpoint，再读 schema 与行数。结果：schema 完整（见 §2.2），`conversation` 行数
为 0，`room_master_table.identity_hash` 为 32 位十六进制。

同类情况还有 `vita-database`（WAL 416 KB，承载 39+42 行的增量）与多个
`MsgDB_*`（`journal_mode=wal`，样本中 WAL 为 0 字节）。

**结论**：若只读主库文件而不重放 WAL，会漏掉 schema 与部分行。本报告的 schema 与
行数均来自重放后的快照。

## 4. MMKV 存储

`files/mmkv/` 共 397 个文件、约 19 MB。MMKV 头部为 4 字节 `actualSize`，随后是
protobuf 风格的键值对。**未加密**：读取 `MMKVCompat.a.b(String)` 的调用点可见少数
模块显式传入 crypt key（`app_login_enc`、`pdd_config_common_enc`、
`module_sensitive_api_disk_cache_encrypt…`、`app_chat_crypto_mmkv`），其余模块不传，
因此可以直接从文件中解出键名。

### 4.1 安全相关存储

| 存储 | 键（仅键名） |
| --- | --- |
| `secure` | `pdd_id`、`enCryptInfoV3`、`scres`、`acc_reported_cnt`、`sensor_reported_cnt`、`sensor_last_updatetime` |
| `secure_collect` | `pcd`、`app_size_info`、`media_drm_cache`、`android_id_cache`、`last_get_app_list_time`、`p29_cache` |
| `pdd_config` | `gender`、`nickName`、`userAvatarUrl`、`app_code_icon`、`app_code_icon2`、`app_info`、`backup_data`、`KEY_LOGIN_TYPE`、`MY_UIN_4100`、`login_time`、`firstOpenTimeStamp`、`isFirstInstalled`、`key_user_label`、`__oksp_migrate__`、`app_last_exit_time`、`pref_key_uuid`、各厂商推送 `*_reg_id` |
| `pdd_config_common` | `jsSecureKey___ACCESS_TOKEN__`、`jsSecureKey___LAST_ACCESS_TOKEN__`、`jsSecureKey___USER_UID__`、`pdd_id`、`key_last_user_id`、`userAgentString`、`longlink_local_ip`、`longlink_local_port`、`cookie_api_uid`、`device_uuid` |
| `pdd_config_basekit` | `cookie_api_uid`、`device_uuid` |
| `app_login` | `LAST_LOGIN_APP_VERSION_4880`、`LAST_REFRESH_TOKEN_<UID>`、`LOGIN_HISTORY_4880`、`is_new_login_version_4540`、`key_last_refresh_flag<UID>`、`key_login_style_info_local`、`login_type_5120`、`mmkv_login_type_5120` |
| `login` | `key_saved_previous_login_account_info_5430` |
| `device_info_setting_monitor` | `USER_SETTING_MONITOR_LAST_SETTING_DATA`、`USER_SETTING_MONITOR_LAST_TIME` |
| `PDD.Wallet`、`pdd_limited_kv`、`fcm_token` | 样本中为空 |

`enCryptInfoV3` 的**具体构串格式已验证**（`lb2/p0.java:284–300`、`lb2/l0.java:105`）：

```
enCryptInfoV3 = "5ec1"
              + %08x( len(encStr) ) + encStr          # SecureNative.enc(设备信息 JSON, version)
              + %08x( len(b64) )    + b64             # l0.b(同一份 JSON)
              + %08x( crc32(str5) )
```

即**两路独立加密的结果拼在一个长度前缀框架里**，末尾是 CRC32：

| 段 | 来源 | 算法 |
| --- | --- | --- |
| `encStr` | `SecureNative.enc(json, version)` | native 实现（见 [algorithm.md](algorithm.md)） |
| `b64` | `lb2/l0.b(json)` | `AES/GCM/NoPadding`，12 字节随机 IV 前置，整体 `Base64(NO_WRAP)` |
| 尾部 | `CRC32` of 前两段的长度前缀拼接 | `java.util.zip.CRC32` |

`b64` 段（Android 6.0+ 分支，`Build.VERSION.SDK_INT >= 23`）的密钥来自
**AndroidKeyStore**，别名 `pdd_secure_cipher_key`，由 `l0.e()` 在首次使用时
生成并持久化；这是密钥**不落在应用私有目录**的原因——只能通过 Keystore 访问。

`pdd_id` 为 `5ec1` 前缀的同类串；`scres` 为单字节值。

`pdd_config_common` 里的 `longlink_local_ip`/`longlink_local_port` 是长连接本地端口
记录，用于进程间复用；按 `SECURITY.md` 其值不公开。

### 4.2 MMKV 加密边界

| 存储 | crypt key 来源 |
| --- | --- |
| `app_login_enc` | `q1.c.E(false)` |
| `pdd_config_common_enc` | `q1.c.E(false)` |
| `module_sensitive_api_disk_cache_encrypt<d>` | `qb2.d.b().a(11)`（选择子 11） |
| `app_chat_crypto_mmkv` | 聊天 CryptoUtil 内部派生 |

其余 MMKV 与 `account`/`HX`/`Chat`/`Login` 等模块名对应的存储未传 crypt key。
模块名清单（`MMKVCompat.a` 的第一个参数）覆盖 `account`、`HX`、`Chat`、`Login`、
`apm`、`app_*` 等 100 余个，对应的具体文件名见 §4.1 与
`evidence/mmkv/` 列表。

## 5. `files/secure`

`files/secure/p29_info.cache` 是明文 JSON：`{"p29":"","p30":"<长 base64 串>"}`。
`p29`/`p30` 与 `secure_collect` 的 `p29_cache` 同源，属设备画像缓存。

## 6. `files/network`

| 路径 | 内容 |
| --- | --- |
| `titancache/bizgroup_1.v1_cache`、`bizgroup_2.v1_cache` | 长连接群播同步游标，INI 风格 `[<uid>_<group>]`、`group=`、`id=`、`offset=` |
| `pnet/com_xunmeng_pinduoduo/ssl_session/*.session` | TLS 会话票据 |
| `pnet/.../<host>_quic.session` | QUIC 会话票据 |
| `pnet/.../<host>_quic.ini` | QUIC 参数 |

样本中出现的 host 包括主 API 与两个图片 CDN 主机；票据本身是二进制会话材料，
按 `SECURITY.md` 不公开。

## 7. `files/dynamic_so`

26 个目录、共 74 MB。目录名格式：

```
<libname>_<epoch毫秒>_<md5>
```

每个目录内：

| 文件 | 内容 |
| --- | --- |
| `lib<name>.so` | **明文 ELF**（未加壳、未加密；库内可另有异或保护的字符串池，见下） |
| `extra_info.json` | 117 字节的元数据 |
| `modified_<epoch毫秒>` | 空标记文件 |
| `uuid_<32位hex>` | 空标记文件 |
| `version_<x.y.z>` | 空标记文件 |

代表性条目（按大小）：

| 库 | 大小 |
| --- | ---: |
| `libmedia_engine` | 22,141,432 |
| `libpdd_j2v8` | 15,988,256 |
| `libpdd_rubik` | 6,335,856（version 0.30.0） |
| `libpnn` | 6,200,608 |
| `libdyncommon` | 5,146,000（version 4.65.0） |
| `libtronavx` | 4,000,176 |
| `libpnet` | 2,820,816（version 39.43.0） |
| `libtitan` | 2,079,080 |
| `libScriptBind` | 1,812,512 |
| `libprobe` | 436,488（version 35.65.0） |
| `libminosTask` | 141,152（version 1.43.0） |
| `libaegis` | 43,424 |

`libpdd_rubik` 暴露 `com.xunmeng.pinduoduo.secure_rubik.SecureRubik.rubik(Context, Map)`
（native），由 `SecureRubik.a()` 包装、`RU.getInfo(long tid)` 读取，`type` 取
`TaskScore.SYNC_QUERY_RESULT_FAILED`。

**落盘形态与混淆的关系**：落在这里的都是原样 ELF，可直接 `readelf`/`objdump`，
没有加壳、没有自解密段。但"文件是明文 ELF"不等于"库里字符串是明文"——
`libdyncommon.so` 的 `.rodata` 就同时含明文与异或密文（池首 `0x408fa0`，
129 条，与 `libpdd_secure.so` 同密钥同工具），内容是该库的反 root/反 hook/
反模拟器探测面与 `ab_secure_*` 开关。还原方法与完整清单见
[obfuscation.md](obfuscation.md) §9.4 与 [risk.md](risk.md) §14。

## 8. SharedPreferences

| 文件 | 关键内容 |
| --- | --- |
| `efix_sp_main.xml` | `load_failed_continuously_count=0`、`last_load_failed_v=0`、`has_record_load_v_9759=true`（无补丁装载） |
| `safemode.xml` | `enter_safe_mode=false`、`enter_safe_mode_level=0` |
| `com.xunmeng.pinduoduo_preferences.xml` | `update_devId=true` |
| `pdd_config.xml`、`pdd_config_common.xml` | 迁移前的旧键 |
| `mipush*.xml` | 推送 devId/regId/appToken（设备标识，不公开） |
| `WebViewChromiumPrefs.xml`、`efix_sp_main.xml`、`ut_sp.xml`、`meco64_storage_sp.xml`、`sp_client_report_status.xml` | 组件状态 |

### 8.1 支付宝设备指纹 SDK 的本地落盘

`apmobilesecuritysdk`（见 [risk.md](risk.md) §13）在本应用私有目录下另有一组
落盘，**与拼多多自研存储完全分离**：

| 形式 | 名称 | 键 / 路径 | 说明 |
| --- | --- | --- | --- |
| SharedPreferences | `vkeyid_settings` | `random`、`vkey_valid`、`last_apdid_env`、`log_switch`、`agent_switch` | vkey 轮换状态与开关 |
| SharedPreferences | `openapi_file_pri` | `openApi` | 上游开放接口返回值缓存 |
| SharedPreferences | `alipay_vkey_random` | — | 随机数种子 |
| SharedPreferences | `virtualImeiAndImsi` | — | **虚拟 IMEI/IMSI 缓存**（非真实设备值） |
| 日志 | `files/log/ap/yyyyMMdd.log` | 按日滚动 | `apmobilesecuritysdk.c.a` 写入 |

其中 `virtualImeiAndImsi` 是本次分析中值得单独注意的一项：它表明该 SDK 会在
本地缓存一套**虚构的** IMEI/IMSI，用于在无 `READ_PHONE_STATE` 或读取失败时
提供替代值，而不是上报空串。

**取证边界**：本节内容由 DEX 代码路径判定（**结构已证实**）。设备在本次会话
末段不可达，上述文件未能与真机快照逐一比对，故**未标注为已验证**——它们的
存在性与键名以代码为准。

## 9. 安全结论

1. 应用数据目录中**没有任何加密的 SQLite**，全部为 `SQLite format 3` 明文库；因此
   设备上的聊天、通知、商城会话、下载元数据、组件记录都可直接读取（受 Android
   sandbox 保护）。
2. 敏感凭据集中在 MMKV `pdd_config_common`（token/uid）与 MMKV `secure`
   （`enCryptInfoV3`/`pdd_id`/`scres`），其中后者的值形态说明它经过了 native 加密。
3. 少数 MMKV 模块带 crypt key，key 本身由 `qb2.d.b().a(<选择子>)` 现场从
   `libpdd_secure.so` 取得，APK 内无明文。
4. 下载器保留了**完整 URL、响应头、落盘路径**，是本地存储中信息量最大的业务元数据。
5. 运行时下载的 native 代码以明文 ELF 落盘，可由任何有该目录读权限的组件加载或
   分析，是攻击面之一。
