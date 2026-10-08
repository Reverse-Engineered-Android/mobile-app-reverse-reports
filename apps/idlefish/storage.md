# 本地存储（设备端只读核对）

本节所有结论来自对 Android 闲鱼应用私有数据目录的**只读**检查：
文件头核对、SQLite `integrity_check`、DDL 与聚合行数。未读取任何行值。

## 1. 数据库清单（databases/，共 21 个 SQLite 文件）

| 文件 | 大小 | 表数 | 主要内容 |
| --- | ---: | ---: | --- |
| `fleamarket_idlefish_im_2207341976150.db` | 180 KB | 7 | **私聊/IM 本地库**：会话、消息、用户、文件、卡片、区域 |
| `fleamarket_datacenter_0.db` / `fleamarket_datacenter_2207341976150.db` / `fleamarket_datacenter_app.db` | 28 KB | 2 | `fishkv`（键值对：key, moduleName, value, 时间） |
| `ut.db` | 233 KB | 14 | UT 埋点（alarm/ap_alarm/ap_stat/counter/…） |
| `ut-abtest-v2.db` | 864 KB | — | AB 实验快照 |
| `accs.db` | 20 KB | 1 | ACCS 长连接流量（host/serviceid/bid/size） |
| `message_accs_db` | 36 KB | — | 消息通道流量 |
| `alsn20170807.db` | 20 KB | — | 推送心跳/长连接日志 |
| `apm_local` | 28 KB | 1 | 本地资源映射（local_id → path） |
| `aus_uploader.db` | 24 KB | — | 上传任务 |
| `dinamicx` | 36 KB | 1 | DinamicX 模板缓存（biz_type/name/version/url） |
| `fleamarket_datacenter_*.db` | 28 KB | 2 | 引导展示（GuideInfo）与 fishkv |
| `hmdb` | 28 KB | 1 | 特征历史（feature/nb/loc/time） |
| `httpdns.db` | 20 KB | 1 | HTTPDNS 解析缓存（domain/ip/port/ttl） |
| `logger.db` | 24 KB | 1 | TLog 日志（alitx_logger） |
| `monitor.db` | 64 KB | 1 | 监控事件（alitx_monitor） |
| `network_analysis_flow_monitor.db` | 20 KB | — | 网络质量分析 |
| `nw_conf_mng.db` | 24 KB | — | 网络配置 |
| `tanx_ad_expose_sdk.db` | 20 KB | — | 广告曝光 |
| `xriver_app.db` | 16 KB | 1 | Nebula/XRiver 小程序资源（api_permission/package_url/…） |
| `idle_fish_space_image_2207341976150.db` | 20 KB | — | 图片空间 |
| `fleamarket_idlefish_im_*.db` | 180 KB | 7 | **聊天**（详见 §2） |

## 2. IM 本地库（`fleamarket_idlefish_im_*.db`）

### 2.1 `SessionInfo`（22 行，设备端实际计数）

```sql
CREATE TABLE SessionInfo (
  sessionId INTEGER NOT NULL PRIMARY KEY,
  sessionType INTEGER,
  sessionInfo TEXT,
  summary TEXT,
  unread INTEGER,
  isAtTop INTEGER,
  isNoDisturb INTEGER,
  operateTimeStamp INTEGER,
  parentSessionId INTEGER,
  summaryContent TEXT,
  title TEXT,
  draft TEXT,
  peerReadVersion INTEGER,
  readVersion INTEGER
);
```

### 2.2 `Message`（0 行，样本设备当前无未同步消息）

```sql
CREATE TABLE Message (
  messageId TEXT NOT NULL PRIMARY KEY,
  sid INTEGER,
  sessionType INTEGER,
  contentType INTEGER,
  textContent TEXT,
  version INTEGER,
  timeStamp INTEGER,
  uid INTEGER,
  readState INTEGER,
  peerReadState INTEGER,
  sendState INTEGER,
  playState INTEGER,
  audioState INTEGER,
  isBreakPoint INTEGER,
  message TEXT
);
```

### 2.3 `UserInfo`（48 行）

```sql
CREATE TABLE UserInfo (
  userIdType TEXT NOT NULL PRIMARY KEY,
  userId INTEGER,
  type INTEGER,
  reMark TEXT,
  fishNick TEXT,
  nick TEXT,
  logo TEXT,
  status TEXT,
  timeStamp INTEGER,
  userInfo TEXT
);
```

### 2.4 其余

- `FileInfo(fileKey, timeStamp, sessionId, localId, data)` — 消息附件。
- `CardInfo(bizType, dataId, bizId, serverTime, newestTime, cardData)` — 商品/订单卡片。
- `RegionInfo(regionId, version, extMap)` — 会话分区。

## 3. 行为与特征库（files/DAI/Database/）

| 文件 | 大小 | 行数 | 内容 |
| --- | ---: | ---: | --- |
| `walle_ut_user_track.db` | 17 MB | **5175** | `usertrack(page_name, event_id, arg1/2/3, args, auction_id, page_stay_time, owner_id, ...)` |
| `edge_compute.db` | 1 MB | 多表 | **行为图**：`node(bid, type, scene, timestamp, args)`、`edge(srcId, dstId, type, args)`、`dc_userBehavior_pv_node`、`dc_userBehavior_tap_node`、`dc_userBehavior_scroll_node`、`dc_userBehavior_ipv` 等 |
| `walle_custom_biz_data.db` | 254 KB | 42 | `dc_raw`、`dc_cache`、`cml_cc_user_states`（模型状态）、`x_msg_ut` |
| `basic_feature_*.db` | — | 0 | 实时/中台/内部特征，样本设备空表 |

**`edge_compute.db` 索引**（节选）：
`PVNodeSceneActionNameIdx`、`TapNodeSceneActionNameIdx`、
`ScrollNodeSceneActionNameIdx`、`IPVItemIdIdx`、`IPVSceneScmIdx`、
`ExposeNodeSceneActionNameIdx`、`EdgeLeftTypeIdx`、`EdgeRightTypeIdx`。
→ 存的是**浏览/点击/滚动/曝光/页面停留/搜索词/商品/卖家** 等行为节点与关系边。

## 4. Wukong 行为库（files/.wukong/mfe_db/v1.db）

```sql
CREATE TABLE mfe_basic (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL,
                        ccrcSellerUniqueCnt1h_list BLOB);
CREATE TABLE mfe_original (id INTEGER PRIMARY KEY AUTOINCREMENT, session INTEGER NOT NULL,
                           rid INTEGER NOT NULL, ts INTEGER NOT NULL, value BLOB);
```

设备端实际：`mfe_basic` **5** 行，`mfe_original` **0** 行。

## 5. 键值与配置

- `files/mmkv/splash_ad_mmkv` + `.crc`：MMKV 格式（文件头全零为空闲页标志）。
- `shared_prefs/`：**214 个** XML。
  关键文件：
  - `user_provacy_policy.xml` / `user_provacy_policy_new.xml` — 隐私同意状态；
  - `fish_device_activate.xml` — `fish_firstOpen_flag=1`；
  - `fish_imei` — 设备自生成 IMEI；
  - `ACCS_SDK.xml`、`AGOO_BIND.xml` — 推送/长连接绑定；
  - `wukong_ccrc_idlefish_*.xml` — 4 个风控场景开关与本地序列号；
  - `AB_-SEARCH_ACT_NATIVE_ANDROID_V2.xml` 等 AB 实验状态。
- `files/flybird`、`files/app_efs`、`files/app_tombstone`：Flutter/飞行器/崩溃缓存。

## 6. 缓存与下载

| 目录 | 说明 |
| --- | --- |
| `cache/` | WebView/UC、图片、临时文件（18 子目录） |
| `files/app_zcache` | HTTP 首屏缓存 |
| `files/AVFSCache` | 首帧/视频缓存（v2.sqlite.1） |
| `files/app_down_libs` | 远端下发 native 库 |
| `files/app_native_libs` | 预置 native 库 |
| `files/app_u4sdk`、`files/app_u4_webview` | UC U4 内核数据 |
| `code_cache/` | JIT/优化代码 |
| `dexpatch/` | 动态补丁（Atlas） |

## 7. 加密状态

- **全部 SQLite 文件均为明文**（文件头 `SQLite format 3\0`），
  无 SQLCipher/WCDB 加密头。
- MMKV 文件为明文二进制。
- 敏感 Cookie/Token 存于 SP，受 Android 沙箱保护（应用私有目录）。
- UMID/签名密钥在 SecurityGuard 静态/动态数据存储中，非 SQLite。

## 8. 与代码的对应

| 存储 | 代码入口 |
| --- | --- |
| `ut.db` | `com.alibaba.analytics.core.Variables`、`UTAnalytics` |
| `accs.db` | `com.taobao.accs.data.TrafficDB` |
| `dinamicx` | `com.taobao.android.dinamicx.storage.DXTemplateStorage` |
| `xriver_app.db` | `com.alibaba.xriver.xriver_app`（小程序资源） |
| `fishkv` | `com.taobao.idlefish.protocol.fishkv.PKV` |
| `mfe_db/v1.db` | `com.alibaba.security.client.smart.core.core.WukongMFEManager` |
| `walle_ut_user_track.db` | `com.alibaba.security.client.smart.core.track.TrackManager` |
| `edge_compute.db` | 阿里行为图引擎（DCI） |
| IM `*.db` | `com.taobao.fleamarket.message.*`（本地消息层） |

## 9. 证据等级

- **已验证**：文件头、`integrity_check=ok`、全部表结构、设备端实际行数、
  MMKV/SP 目录规模。
- **结构已证实**：`edge_compute.db` 的行为图用途、`mfe_db` 的特征含义，
  具体每条数据的写入口在加固/混淆层。
- **不可证**：每条行为数据的精确保留时长。
