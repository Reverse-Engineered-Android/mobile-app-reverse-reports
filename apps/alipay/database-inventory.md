# 61 个数据库逐项清单

`sha256` 是源文件完整性标识；路径中的账号维度已脱敏。对象数/行数只统计已恢复容器。未恢复库的“行数”为 `N/A`，不代表空库。

| ID | 文件（脱敏） | 格式/状态 | 对象 | 行数 | 内容结论 |
| ---: | --- | --- | ---: | ---: | --- |
| 1 | `app_webview_mywebview_0/Default/databases/Databases.db` | 普通 SQLite / 可读 | 3 | 3 | WebView IndexedDB registry and metadata |
| 2 | `databases/<account-id>scan_biz.db` | SQLCrypto / 未解密 | 0 | N/A | scan business cache keyed by user/session |
| 3 | `databases/FlareRecord-main.db` | SQLCrypto / 已解密 | 2 | 166 | permission flare invocation records |
| 4 | `databases/Journals.db` | 普通 SQLite / 可读 | 3 | 27 | identity-verification and biometric/fast-pay audit journal |
| 5 | `databases/LogSpmDAU.db` | 普通 SQLite / 可读 | 3 | 0 | SPM/DAU analytics log cache |
| 6 | `databases/MultiMediaTask.db` | 普通 SQLite / 可读 | 2 | 374 | multimedia task queue |
| 7 | `databases/NBNet.db` | 普通 SQLite / 可读 | 4 | 0 | network download and upload records |
| 8 | `databases/PrivacyLocalRecord-main.db` | SQLCrypto / 已解密 | 3 | 755 | main-process privacy/API invocation records |
| 9 | `databases/PrivacyLocalRecord-push.db` | SQLCrypto / 已解密 | 3 | 21 | push-process privacy/API invocation records |
| 10 | `databases/PrivacyLocalRecord-tools.db` | SQLCrypto / 已解密 | 3 | 0 | tools-process privacy/API invocation records |
| 11 | `databases/PrivacyLocalRecord-widgetProvider.db` | SQLCrypto / 已解密 | 3 | 0 | widget-provider privacy/API invocation records |
| 12 | `databases/SLScheme.db` | 普通 SQLite / 可读 | 3 | 2 | log/scheme cache |
| 13 | `databases/alipayclient.db` | 普通 SQLite / 可读 | 4 | 3 | login, account, gesture and session state |
| 14 | `databases/alipayclient_ad.db` | 普通 SQLite / 可读 | 5 | 2335 | ad display, fatigue and placement rules |
| 15 | `databases/alipayclient_ad_cpp.db` | 普通 SQLite / 可读 | 3 | 2351 | native ad fatigue and placement data |
| 16 | `databases/alsn20170807.db` | 普通 SQLite / 可读 | 3 | 3 | generic business cache (opaque table name) |
| 17 | `databases/aomp_favorite.db` | 普通 SQLite / 可读 | 2 | 3 | mini-app favorites |
| 18 | `databases/aomp_mini_app_center.db` | 普通 SQLite / 可读 | 2 | 198 | mini-app business/app mapping |
| 19 | `databases/ap_intelligentdecision_idc.db` | 普通 SQLite / 可读 | 3 | 0 | intelligent-decision behavior cache |
| 20 | `databases/birdnest.db` | 普通 SQLite / 可读 | 2 | 30 | BirdNest templates |
| 21 | `databases/chat_message.db` | 普通 SQLite / 可读 | 3 | 1 | chat message cache |
| 22 | `databases/chatmsgdb<account-id>.db` | SQLCrypto / 未解密 | 0 | N/A | account-scoped social chat messages |
| 23 | `databases/contactsdb<account-id>.db` | SQLCrypto / 未解密 | 0 | N/A | account-scoped social contacts |
| 24 | `databases/discussioncontactdb<account-id>.db` | SQLCrypto / 未解密 | 0 | N/A | account-scoped discussion contacts |
| 25 | `databases/download_cycle_mng.db` | 普通 SQLite / 可读 | 3 | 2 | download cycle manager |
| 26 | `databases/dynamic_release.db` | 普通 SQLite / 可读 | 3 | 1 | dynamic release items |
| 27 | `databases/fhcube.db` | 普通 SQLite / 可读 | 2 | 1 | FHCube templates |
| 28 | `databases/global_search_v_h_<account-id>.db` | 普通 SQLite / 可读 | 2 | 2 | global-search visit history |
| 29 | `databases/globalsearch_group_map.db` | 普通 SQLite / 可读 | 2 | 9 | global-search group mapping |
| 30 | `databases/globalsearch_use_heat.db` | 普通 SQLite / 可读 | 2 | 1 | global-search usage heat |
| 31 | `databases/httpdns.db` | 普通 SQLite / 可读 | 3 | 639 | HTTP DNS cache |
| 32 | `databases/job_state.db` | SQLCrypto / 已解密 | 3 | 252 | UEP/SSP/CEP job state |
| 33 | `databases/messagebox.db` | 普通 SQLite / 可读 | 9 | 142 | message box, service and search state |
| 34 | `databases/mobileaix_datacenter_biz.db` | SQLCrypto / 未解密 | 0 | N/A | MobileAiX business feature data |
| 35 | `databases/mobileaix_datacenter_feature.db` | SQLCrypto / 未解密 | 0 | N/A | MobileAiX feature data |
| 36 | `databases/mobileaix_datacenter_feature_v2.db` | SQLCrypto / 未解密 | 0 | N/A | MobileAiX feature data v2 |
| 37 | `databases/mobileaix_feature.db` | 普通 SQLite / 可读 | 5 | 12 | MobileAiX custom feature aggregates |
| 38 | `databases/nebula_app.db` | 普通 SQLite / 可读 | 7 | 72 | Nebula mini-app install/config state |
| 39 | `databases/nebulax_app.db` | 普通 SQLite / 可读 | 7 | 129 | NebulaX app/plugin/resource state |
| 40 | `databases/nw_conf_mng.db` | 普通 SQLite / 可读 | 3 | 24 | network configuration manager |
| 41 | `databases/open_platform_apps.db` | 普通 SQLite / 可读 | 7 | 605 | open-platform app install and stage state |
| 42 | `databases/permission_fortress_invoke_record-main.db` | SQLCrypto / 已解密 | 3 | 6 | middleware permission invocation records |
| 43 | `databases/publicHome.db` | 普通 SQLite / 可读 | 4 | 11 | public-account home/follow state |
| 44 | `databases/public_life.db` | SQLCrypto / 未解密 | 0 | N/A | public-life home/settings/broadcast/plugin state |
| 45 | `databases/push_msg.db` | 普通 SQLite / 可读 | 5 | 9 | push message and merge/id-map state |
| 46 | `databases/socialmobiledb<account-id>.db` | SQLCrypto / 未解密 | 0 | N/A | account-scoped social mobile data |
| 47 | `databases/sync_dispatch.db` | 普通 SQLite / 可读 | 4 | 1436 | sync dispatch and uplink queue |
| 48 | `databases/timelinedb<account-id>.db` | SQLCrypto / 未解密 | 0 | N/A | account-scoped timeline data |
| 49 | `databases/tiny_app.db` | 普通 SQLite / 可读 | 3 | 0 | tiny-app favorite display state |
| 50 | `databases/ucdp.db` | 普通 SQLite / 可读 | 4 | 0 | UCDP fatigue and position rules |
| 51 | `databases/ucdp<account-id>.db` | 普通 SQLite / 可读 | 4 | 27 | account-scoped UCDP fatigue and position rules |
| 52 | `databases/xriver_app.db` | 普通 SQLite / 可读 | 4 | 192 | XRiver app/plugin install and resource state |
| 53 | `files/Sandbox/B_<redacted>/database/fts_contact_friend_<account-id>_<account-id>/7/index.db` | SQLCrypto / 未解密 | 0 | N/A | protected FTS index: contacts/friends |
| 54 | `files/Sandbox/B_<redacted>/database/fts_mobile<account-id>_<account-id>/7/index.db` | SQLCrypto / 未解密 | 0 | N/A | protected FTS index: mobile contacts |
| 55 | `files/Sandbox/B_<redacted>/database/fts_user_global_label_<account-id>_<account-id>/7/index.db` | SQLCrypto / 未解密 | 0 | N/A | protected FTS index: user global labels |
| 56 | `files/client_db/<account-id>/client_database.db` | 普通 SQLite / 可读 | 14 | 200 | client config/tasks/key-values/revisit changes |
| 57 | `files/sc_edge/DATA00.db` | UnQLite / 值不透明 | 1 | 1 | UnQLite edge/security key-value store |
| 58 | `files/sc_edge/DATA11.db` | UnQLite / 值不透明 | 1 | 1 | UnQLite edge/security key-value store |
| 59 | `files/sc_edge/DATARC.db` | UnQLite / 值不透明 | 1 | 1 | UnQLite remote-configurations store |
| 60 | `files/sc_edge/DATAUSS.db` | UnQLite / 值不透明 | 1 | 1 | UnQLite security service store |
| 61 | `files/sc_edge/DATAV3.db` | UnQLite / 值不透明 | 1 | 1 | UnQLite versioned opaque-value store |

## 未恢复原因

| 类别 | 数量 | 静态结论 |
| --- | ---: | --- |
| MobileAiX | 3 | 密码为运行时随机值，且由 `AlipaySecurityEncryptor` 加密保存；当前 DEX 未含底层 `EncryptDataUtils`。 |
| 社交/时间线 | 5 | 构造器确认用户维度密码入口，但基类转换/保护域不在当前 DEX 证据内。 |
| 扫码业务缓存 | 1 | 密码来自 Trusted Terminal 的受保护自定义数据槽。 |
| 沙箱 FTS | 3 | 客户端 FTS 密钥/索引保护实现不在当前 DEX 证据内。 |
| 公共生活 | 1 | 数据库 helper/密钥入口未在当前 DEX 证据内恢复。 |

五个 `sc_edge` 文件不是 SQLCipher/SQLCrypto：文件头是 `unqlite`，已打开为键值容器；键和值只保留类别/计数，值不公开。
