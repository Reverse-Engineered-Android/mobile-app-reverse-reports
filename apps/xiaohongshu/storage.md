# 小红书本地存储格式、范围与解密方法

## 1. 分析范围与版本

9.37.0 代码用于确认存储实现和口令来源；对照主机上的 9.47.0 历史数据及 9.48.0 当前数据库通过 root SSH **只读**访问，用于验证格式、DB 类型和聚合数据范围。分析只读取应用私有数据并使用隔离副本，不写回、不改权限、不重启应用或设备。两组内容计数不能代表 9.37 的历史内容。

对照快照只读结果：

- 9 个加密数据库成功重建为普通 SQLite。
- 17 个明文数据库可直接读取。
- 最大业务数据集是用户关系表；私信、笔记和播放历史在该快照中为空或只有摘要。
- 报告不包含真实账号、设备 ID、token、密钥、联系方式、坐标或数据库行样本。

对照方法边界：只核对数据库头、SQLCipher 参数、schema、表类型、行数和加密容器格式；真实值只在隔离环境解密用于确认算法，不进入 Git。root SSH 地址和设备私有路径不进入公开仓库。

### 两个对照时点

2026-09-30 的包管理器只读查询显示当前安装为 `9.48.0`。本次通过隔离 SQLCipher 快照做实时只读聚合查询；计数与历史快照不同，也说明这些表会随账号使用继续增长。下表只记录瞬时表行数，不记录任何行内容。

| 数据集 | 9.47 历史快照 | 9.48 当前只读查询 |
| --- | ---: | ---: |
| `local_relation_user` | 1005 | 1010 |
| `msgDB.message` / `chat` / `group_chat` | 0 / 0 / 0 | 652 / 96 / 16 |
| `msgDB.chat_set` / `user` | 4 / 0 | 5 / 498 |
| `hedwig_conversations` | 0 | 2 |
| 播放历史 `historyRecord` | 0 | 1049 |
| `prdownloader` | 1145 | 2025 |
| DSL 模板 | 671 | 681 |
| Petal 插件 / 补丁 | 12 / 33 | 14 / 33 |
| `analysisemitter` 表行数（custom/build/emitter） | 10 / 163 / 未分项 | 0 / 8673 / 1 |

`analysisemitter` 的行值可能是复合 payload；表行数和 payload 内事件数不能混用。应用运行中会继续写表，因此这些数字只是同一时点的只读审计值。

## 2. 存储层次

| 层 | 格式 | 主要内容 |
| --- | --- | --- |
| Room/SQLite | 明文 SQLite | 下载、广告、模板、插件、埋点、缓存 |
| Room + WCDB | Tencent WCDB/SQLCipher | 联系关系、消息、编辑草稿、Hey、Alpha、Capa 等 |
| MMKV / SharedPreferences | protobuf/二进制 KV 或 XML | DB 口令、设备 ID、联系人变化 hash、功能状态 |
| 风险 SDK DB | SQLite + 自定义加密 | Getui/GTC/GBD 设备维度、推送身份、策略 |
| 序列化对象 | Java serialization | 风控/推送配置对象 |
| 压缩记录 | 8 字节 ASCII 前缀 + gzip | 推送/设备状态快照 |
| 文件缓存 | 原始媒体/资源文件 | 图片、视频、模板、插件、下载资源 |

## 3. WCDB/SQLCipher

9.37 的 `com.xingin.xhs.xhsstorage.safe.WCDBOpenHelperFactory` 通过 Room 的 `openHelperFactory` 使用 Tencent WCDB：

```text
SQLiteCipherSpec.pageSize = 1024
SQLiteCipherSpec.kdfIteration = 64000
```

9.47 的实际解密复核与此一致：SQLCipher compatibility 3、page size 1024、PBKDF2/KDF iterations 64000。数据库 passphrase 以 `byte[]` 直接传给 `SQLiteOpenHelper`。

### 口令来源

动态口令保存在 MMKV/Preferences。下表列出容器和键名，但不展示任何 passphrase、key、IV 或恢复后的值：

| 数据库 | MMKV/Preferences 容器 | 口令键 | 缺省值来源 |
| --- | --- | --- | --- |
| `msgDB` | `com.xingin.xhs_preferences` | `msg_db_password_updated` | DEX 静态数组 |
| `localRelationDB` | `com.xingin.xhs_preferences` | `relation_db_password_updated` | 仅动态口令 |
| `hedwig.db` | `im_hedwig` | `hedwig_db_password_updated` | DEX 静态数组 |
| `xhs_alpha.db` | `com.xingin.xhs_preferences` | `alpha_db_password_updated` | 仅动态口令 |
| `xhs_capa.db` | `com.xingin.xhs_preferences` | `capa_db_password_updated` | DEX 静态数组 |
| `xhs_hey.db` | `com.xingin.xhs_preferences` | `hey_db_password_v2` | DEX 静态数组 |
| `xhs_recent_used_resource.db` | `com.xingin.xhs_preferences` | `recent_used_resource_db_password` | 仅动态口令 |

广告/下载/缓存族另有代码内静态 passphrase，公开报告不披露字节。MMKV 读取链已复核：定位 UTF-8 键后读取后续 varint 长度，取对应值块，并识别观测到的可选单字节长度前缀；最终以应用配置中的 `byte[]` 作为 SQLCipher passphrase。

### 解密步骤

1. 从应用私有目录只读复制 DB、`-wal`、`-shm`，避免直接打开活动数据库。
2. 从上表对应 MMKV/Preferences 读取 passphrase；若缺失，再按数据库配置类确认默认值。
3. 使用 SQLCipher/WCDB compatibility 3 打开：page size 1024，KDF 64000。
4. 校验 `sqlite_master`、Room identity、表结构和用户版本；错误 passphrase 不应产生空伪命中。
5. 以普通 SQLite 导出，仅保留 schema、类型和聚合计数。

公开仓库的 `tools/wcdb-probe.java` 只做候选输入验证和脱敏统计，不输出 key 或行内容。

## 4. 本地数据范围

### 用户与关系

- `localRelationDB`：9.47 快照有 1005 行 `local_relation_user`；9.48 当前只读查询为 1010 行。
- 字段范围包括内部用户 ID、头像 URL、昵称、备注、小红书号/rid、关注状态、简介、关注时间和关系计数。
- 这是该快照中最主要的个人信息集合；报告只保留表级字段和聚合计数。

### 消息与通知

- 9.47 历史快照的 `msgDB`：chat、group_chat、message 均为 0 行，只有 4 行 `chat_set` 通知摘要。
- 当前 9.48 只读查询已有 652 条 message、96 个 chat、16 个 group_chat、5 条 chat_set 和 498 条 user；本次不展开消息正文或联系人字段。
- `hedwig.db` 从历史快照 0 条会话增长为当前 2 条；报告不复制会话内容。
- 两个时点都没有把完整私信正文纳入公开证据。

### 内容与历史

- `PlayHistoryRecordDB.historyRecord`：历史快照 0 行，当前只读查询 1049 行。
- `xhs_alpha.db`、`xhs_capa.db`、`xhs_hey.db`、`xhs_wk_cache.db`、`xhs_common_demotion_cache.db` 基本为 Room 元数据或空业务表。
- 未观察到笔记正文、完整草稿或搜索词历史。

### 广告、下载、模板与插件

- `xhs_advert.db`：8 组开屏广告计划及素材/下载配置。
- `prdownloader.db`：历史快照 1145 条、当前只读查询 2025 条资源下载记录，记录 URL、路径、大小、进度、ETag 和状态。
- `analysisemitter.sqlite`：历史快照包含 10 条自定义 payload 与 163 条监控构建事件；当前 SQLite 表为 custom 0、build-monitor 8673、emitter-monitor 1 行。复合 payload 内事件数另行统计，不能和表行数混写。
- `xy_dsl_templates.db`：历史快照 671 条、当前只读查询 681 条模板，含名称、URL、版本、MD5、最低 App 版本和本地路径。
- `petal_database`：历史快照 12 条插件记录和 33 条补丁记录；当前只读查询为 14 条插件记录和 33 条补丁记录。

## 5. 风控/推送数据库格式

### `dim.db`

值格式：

```text
Base64(AES-128-CBC/PKCS7(business_value) + ":::" + application_binding)
```

实际业务值又常是 Java serialization 的 `TC_STRING`。解析时不能把整个解密结果直接当 UTF-8，需识别 serialization header、长度字段和可选前缀字节。

缓存字段类别包括：

```text
OAID ANDROID_ID ADVERTISING_ID BRAND MODEL ROM
LOCATION_NETWORK WIFI_SCAN_LIST GETUI_DEVICE_ID
```

9.47 快照中实际稳定的本地值主要是 OAID、Android ID、品牌、机型、ROM；位置对象为空/默认值，Wi-Fi 列表为空，广告/Getui ID 可为空。报告不披露实际值。

### `gtc3-key.db`

存储 RSA 包装的 AES key 与 IV：

```text
MD5("com.xingin.xhs-aes128alias") -> RSA ciphertext(AES-128 key)
MD5("com.xingin.xhs-ivalias")     -> RSA ciphertext(IV)
```

对应 Android Keystore 条目是 TEE-backed RSA-1024、purpose 含 decrypt、私钥不可导出。解密顺序：

1. 从 `gtc3-key.db` 读取两个 RSA ciphertext。
2. 在 app UID/Keystore alias 空间定位对应不可导出 RSA 私钥。
3. 由 KeyMint/TEE 执行 RSA PKCS#1 private decrypt，获得临时 AES key/IV。
4. 用 AES key/IV 解开 `dim.db`、`gtc3.db`、`cg.db`。
5. 解析 Java serialization 或键值文本，随后销毁临时明文 key/IV。

仅导出数据库不足以恢复明文；必须获得同一设备的 Keystore 解密能力。RSA-1024 强度偏低，但私钥仍受 TEE 不可导出保护。

### `gtc3.db`

AES 加密配置解开后是 Getui/GTC 端点池、当前选定配置、版本和时间戳。属于推送/设备 SDK 配置，不是主业务关系数据。

### `pushg3.db`

`ral` 表记录格式：

```text
8-byte ASCII prefix + gzip payload
```

gzip 后是竖线分隔的键值状态记录，包含时间、CID、平台、网络类型、计数和开关。部分字段是授权/令牌值，公开报告只描述结构。

### `cg.db`

`sct` 表为 Base64 + 应用绑定加密配置。使用上述 AES key/IV 解开后，明文是 Getui/GTC/GBD 的 Java serialized 配置对象：

- Push：保活、守护、品牌兼容、包名白/黑名单。
- GTC：设备维度调用策略、缓存有效期、网络端点。
- GBD：4ID、位置、Wi-Fi、蓝牙、应用列表、活动、剪贴板和厂商适配策略。

远程策略表示潜在采集能力，不等于快照中已经采集到实际内容。

## 6. 联系人上传加密

联系人上传的 `data` 使用另一套 AES：

```text
plaintext = JSON([[normalized_phone, display_name], ...])
ciphertext = AES/CBC/PKCS5Padding(key=device-derived-key, iv=fixed_iv, plaintext)
wire = Base64(ciphertext)
```

key 派生链包含设备 ID/模拟设备 ID 的多次 MD5/大写变换和固定后缀；IV 为代码中的固定 16 字节值。公开报告不展示 IV、后缀、key 或联系人样本。解密时必须同时持有同一设备 ID 派生状态和应用常量。

## 7. 发布边界

公开报告不包含：

- APK/DEX/SO、数据库文件或 WAL/SHM。
- 真实账号、用户 ID、设备 ID、OAID、Android ID、CID。
- passphrase、AES key、IV、RSA blob、token、secret。
- 联系人、坐标、Wi-Fi、消息、笔记或数据库行样本。
- 完整反编译日志、请求包或设备路径。

所有解密方法只描述结构和操作顺序，可复现实验应使用合成值或环境变量。
