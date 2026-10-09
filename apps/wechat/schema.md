# Schema 与脱敏聚合

## EnMicroMsg 历史解密快照

| 对象 | 行数 | 说明 |
| --- | ---: | --- |
| `message` | 100,506 | 消息主表 |
| `rcontact` | 16,674 | 联系人 |
| `rconversation` | 819 | 会话 |
| `chatroom` | 221 | 群聊 |
| `AppMessage` | 13,664 | App/小程序/业务卡片 XML |
| `AppInfo` | 6 | App/小程序注册信息 |
| `LiteAppConfigInfo` | 5 | 小程序配置 |
| `LiteAppBaselibInfo` | 1 | 小程序基础库 |
| `walletcache` | 6 | 钱包缓存 |
| `HardDeviceInfo` | 2 | 硬件设备绑定 |
| 全库 | 251 tables | `integrity_check=ok` |

`message` 关键列：`msgId,msgSvrId,type,status,isSend,isShowTimer,createTime,talker,content,imgPath,reserved,lvbuffer,transContent,transBrandWording,talkerId,bizClientMsgId,bizChatId,bizChatUserId,msgSeq,flag,solitaireFoldInfo,historyId`。

时间范围为 2015-12-26 至 2026-09-22。方向聚合：接收 96,337、发送 4,169。不同 `talker` 数量为 109。内容字段非空 100,506，`imgPath` 非空 16,726；不输出正文或路径。

主要消息类型码聚合：

| type | 数量 | 静态/常识映射 |
| ---: | ---: | --- |
| 1 | 65,872 | 文本 |
| 822083633 | 10,867 | App/扩展消息变体；按 appMsg XML 解析，撤回链路与文本同族，FTS 预算 5800 ms |
| 47 | 8,741 | 表情 |
| 3 | 6,677 | 图片 |
| 10000 | 3,669 | 系统消息 |
| 49 | 2,182 | App/扩展消息 |
| 570425393 | 673 | 业务扩展类型 |
| 43 | 563 | 视频 |
| 其他 | 694 | 保留原始类型码 |

## SnsMicroMsg 当前明文快照

| 表 | 行数 | 字段/用途 |
| --- | ---: | --- |
| `SnsInfo` | 372 | `snsId,userName,createTime,type,content,attrBuf,postBuf,...` |
| `snsExtInfo3` | 171 | 相册摘要、背景、增量游标、媒体 AES/auth 字段 |
| `adsnsinfo` | 2 | 广告朋友圈 |
| `AdPullRecordsInfo` | 2 | 广告拉取记录 |
| `SnsCover` | 5 | 朋友圈封面 |
| `SnsComment` | 0 | 评论 |
| `SnsMedia` | 0 | 朋友圈媒体上传状态 |
| `snsDraft` | 0 | 草稿 |
| `snsTagInfo2` | 0 | 标签 |

`SnsInfo` 时间范围为 2018-08-18 至 2026-09-27。内容/属性缓冲均非空 372 行，但不解析或公开内容。

## LiteApp / 小程序

`LiteAppInfo` 列：`appId,groupId,url,md5,signatureKey,path,type,patchId,version,iLinkVersion,minliteappversion,minlvcppversion,maxliteappversion,updateTime,lastUseTime,extra,openOption`。

`LiteAppAuthInfo` 列：`host,url,authInfo`。

`LiteAppConfigInfo` 列：`appId,signatureKey,packageConfigPath,updateTime,md5,dynamicConfigPath,iLinkVersion,configJson`。

`LiteAppSamplingConfigInfo` 列：`appId,signatureKey,updateTime,md5,dynamicConfigPath,iLinkVersion,configJson`。

`LiteAppTriggerActions` 列：`eventName,appId,triggerActionsJson`。

已用实例聚合：`LiteAppInfo=18`、`LiteAppAuthInfo=0`、`LiteAppBaselibInfo=1`、`LiteAppConfigInfo=21`、`LiteAppSamplingConfigInfo=30`、`LiteAppTriggerActions=2`。

`AppBrandComm.db` 独立快照已解密并通过 `integrity_check=ok`：54 张表、772 行，26 张表非空。主要聚合：

| 表 | 行数 | 内容 |
| --- | ---: | --- |
| `AppBrandCommonKVData` | 153 | 小程序公共 KV |
| `WxaAttributesTable` | 99 | 小程序属性、版本、绑定和动态信息 |
| `AppBrandMessInfoRecord` | 99 | App/插件/服务类型、文件配额和最后启动时间 |
| `WxaWeDataExptInfo` | 99 | 小程序实验信息 |
| `AppBrandWxaPkgManifestRecord` | 71 | 包 manifest、版本、校验、路径和下载 URL 结构 |
| `AppBrandLauncherLayoutItem` | 38 | 启动器布局/最近使用位置 |
| `AppBrandIdentifierInfo` | 26 | username/appId 标识映射 |
| `AppBrandPrefetchWxaAttrsMarkTable` | 25 | 属性预取标记 |
| `AppBrandCommonUseApp` | 20 | 常用小程序布局信息 |
| `WxaJsApiPluginInfo` | 16 | JS API 插件权限 Protobuf |
| `AppBrandAppLaunchUsernameDuplicateRecord2` | 11 | 启动用户名重复记录 |
| `AppBrandKVData` | 11 | 小程序 KV 数据/大小 |
| `LaunchWxaAppRespTable` | 11 | 启动响应、JSAPI、host、操作配置 |
| `WxaAttrAvailableBackupTable` | 11 | 属性备份、动态信息、同步版本 |
| `WxaSecurityStorageInfo` | 11 | 安全存储 salt 列 |
| `PluginCodeUsageLRURecord` | 10 | 插件代码版本使用 LRU |
| `WxaAppWebRenderingCacheAccessStatsTable` | 10 | Web 渲染缓存访问统计 |

其余 28 张表当前为 0 行；完整列名、非空数、文本/BLOB 长度范围和年份范围见 [database-aggregates.json](evidence/database-aggregates.json)。所有 appId、username、URL、路径、key/value 和业务缓冲均不公开。

## 其他独立加密库快照

| 数据库 | 表/行聚合 | 内容边界 |
| --- | --- | --- |
| `WxExpt.db` | `ExptItem=959`、`ExptKeyMapId=1873` | 实验 ID、分组/序列、内容配置、有效期、类型和 key→ID 映射；不公开 exptId/key/content |
| `WxCgiReport.db` | `CgiReportLocalItemDataCache=0` | 本地 CGI 报告 ID、itemInfo、businessId、reportTime、delayTime；当前为空 |
| `newuba.db` | `NewUserBehaviourCache=3` | cacheTime、reportStr、isKeyView；`reportStr` 长度 1,042–1,376 字节，内容不公开 |

`ExptItem` 的 `exptContent` 非空 959 行、长度 206–10,628 字节；`startTime/endTime` 是 LONG 列，包含 0 哨兵及 2022–2098 的非零年份范围。`newuba` 三行的 `cacheTime` 均落在 2026 年，但不公开精确时间线。

## 文件、收藏与 Edge 解密快照

### EnResDown.db

仅含 `ResDownloaderRecordTable`，151 行。列为：

`urlKey_hashcode INTEGER, urlKey TEXT, url TEXT, fileVersion TEXT, networkType INTEGER, maxRetryTimes INTEGER, retryTimes INTEGER, filePath TEXT, status INTEGER, contentLength LONG, contentType TEXT, expireTime LONG, md5 TEXT, groupId1 TEXT, groupId2 TEXT, priority INTEGER, fileUpdated INTEGER, deleted INTEGER, resType INTEGER, subType INTEGER, reportId LONG, sampleId TEXT, eccSignature BLOB, originalMd5 TEXT, fileCompress INTEGER, fileEncrypt INTEGER, encryptKey TEXT, keyVersion INTEGER, EID INTEGER, fileSize LONG, needRetry INTEGER, appId TEXT, wvCacheType INTEGER, packageId TEXT`。

这明确资源下载记录保存 URL/版本、网络和重试状态、本地路径、大小/类型、过期时间、完整性/签名、压缩加密与 key 版本、上报/采样以及 app/package 关联。公开聚合不输出任何 URL、路径、哈希、签名、key、appId/packageId。

### WxFileIndex.db

| 表 | 行数 | 列 |
| --- | ---: | --- |
| `WxFileIndex3` | 317,257 | `msgId LONG, username TEXT, msgType INTEGER, msgSubType INTEGER, path TEXT, size LONG, msgtime LONG, hash BLOB, diskSpace LONG, linkUUID BLOB, subIdx INTEGER, detail TEXT, flags LONG, svrId LONG` |
| `WxFileIndexDirtyWithTalker` | 2 | `msgId INTEGER, username TEXT` |
| `WxFileIndexDownloadMigration` | 0 | `id INTEGER, originalPath TEXT, targetPath TEXT, indexRowId INT, msgId INT, username TEXT, status INT` |
| `WxFileIndexLinkify` | 1 | `id INTEGER, originalPath TEXT, targetPath TEXT, status INT` |
| `WxFileIndexRefresh` | 0 | `indexRowId INTEGER` |
| `WxFileIndexRegistry` | 5 | `id INTEGER, value BLOB` |

`WxFileIndex3` 是消息附件/文件元数据主索引；其他表分别保存 talker 脏标记、下载迁移、链接化、刷新队列和线性扫描注册值。`ny1/l.smali` 的查询/维护逻辑与实际导出 Schema 相互印证；不公开用户名、路径、哈希、UUID 或 detail。

### enFavorite.db

| 表 | 行数 | 列 |
| --- | ---: | --- |
| `FavItemInfo` | 0 | `localId INTEGER, id INTEGER, type INTEGER, localSeq INTEGER, updateSeq INTEGER, flag INTEGER, sourceId TEXT, itemStatus INTEGER, sourceType INTEGER, sourceCreateTime INTEGER, updateTime INTEGER, fromUser TEXT, toUser TEXT, realChatName TEXT, favProto BLOB, xml TEXT, ext TEXT, edittime INTEGER, tagProto BLOB, sessionId TEXT, datatotalsize INTEGER, transferCtx TEXT, targetID INTEGER, subType INTEGER, starType INTEGER, starBizKey TEXT, svrRepairReupload INTEGER` |
| `FavSearchInfo` | 0 | `localId INTEGER, content TEXT, tagContent TEXT, time INTEGER, type INTEGER, subtype INTEGER` |
| `FavEditInfo` | 0 | `localId INTEGER, modItem BLOB, time INTEGER, type INTEGER, scene INTEGER` |
| `FavCdnInfo` | 0 | `dataId TEXT, favLocalId INTEGER, type INTEGER, cdnUrl TEXT, cdnKey TEXT, totalLen INTEGER, offset INTEGER, status INTEGER, path TEXT, dataType INTEGER, modifyTime INTEGER, extFlag INTEGER, attrFlag INTEGER, retryTime INTEGER` |
| `FavTagInfo` | 0 | `id INTEGER, name TEXT` |
| `FavConfigInfo` | 0 | `configId INTEGER, value TEXT` |
| `FavDelInfo` | 0 | `favId INTEGER, delTime INTEGER, delSource TEXT, oriXml TEXT, clientVersion TEXT, delType INTEGER, itemType INTEGER, updateTime INTEGER, delFlag INTEGER` |

七表当前均为 0 行。`im/ld.smali:20-104` 注册七个 provider，`im/ed.smali` 至 `im/kd.smali` 分别把 ORM 类绑定到上述表；模型类给出完整字段类型。空表只表示当前快照没有收藏行。

### Edge.db

`EdgeComputingCacheDataModel_Instance` 0 行、`EdgeComputingCacheDataModel_Normal` 2 行，两表列均为 `configID TEXT, reportTimeEC LONG, data TEXT`。`m92/d.smali`/`m92/d.java` 同时创建 `_Instance` 与 `_Normal` 两种表名，`im/s2.java:initAutoDBInfo` 给出列类型与 `rowid` 主键；不公开 configID、精确 reportTimeEC 或 data。

## MicroMsgPriority.db 内容级 Schema

授权快照已按 `tx3/h.smali:37-105` 的 `UIN + 登录用户名 + D3/device` key 和 WCDB default 打开链只读验证：`integrity_check=ok`，23 个对象（16 表、7 索引）、36 页、共 1,003 行。完整脱敏列名/类型和计数见 `evidence/priority-aggregates.json`。

| 表 | 行数 | 内容边界 |
| --- | ---: | --- |
| `C2CChatUsage` | 198 | 会话按日期的打开、发送、停留和消息消费计数 |
| `C2CChatUsageResult` | 79 | 上述特征的归一化/偏差、总分和排名 |
| `C2CMsgFileUsage` | 4 | 文件消息日/周/月接收、点击与点击率 |
| `C2CMsgImgUsage` | 0 | 图片消息同类使用率 Schema，当前为空 |
| `C2CMsgAutoDownloadFile` | 2 | 文件自动下载任务、状态、大小、打开时间、优先级和优先级类型 |
| `C2CMsgAutoDownloadRes` | 0 | 图片/资源自动下载任务 Schema，当前为空 |
| `PriorityConfig` | 9 | `type/version` 版本状态 |
| `consumemsgtemp*` | 158 | 消息消费总量、频率、近期值及偏差特征 |
| `opentemp*` | 158 | 打开总量、频率、近期值及偏差特征 |
| `sendmsgtemp*` | 158 | 发送总量、频率、近期值及偏差特征 |
| `staytimetemp*` | 158 | 停留总量、每次打开时长及偏差特征 |
| `lsptemp` | 79 | 会话排序临时分 |

`PriorityConfig(type INTEGER PRIMARY KEY, version INTEGER)` 同时由 `tx3/h.smali:143-209` 静态证实。`C2CMsgAutoDownloadRes` 的 `createtime` 由 `ox3/m.smali:995-1010` 的 90 天清理 SQL 证实。动态 Schema 是内容级证据，原生表由 `PriorityJni.nativeInit` 创建的来源仍见 `evidence/database-source.md`。

## FTS5IndexMicroMsg_encrypt.db 内容聚合

当前全量文件已按 `com/tencent/mm/plugin/fts/p.smali:104-275` 的 key/打开链只读验证，共 160 个 `sqlite_master` 对象、270,018 页。主要 FTS5 内容表计数为：`FTS5IndexMessage_content=2,857,312`、`FTS5IndexContact_content=12,998`、`FTS5IndexChatroomMember_content=280`、`FTS5IndexKefuContact_content=281`、`FTS5IndexWeApp_content=110`、`FTS5IndexFeature_content=133`、`FTS5IndexServiceNotify_content=16`、`FTS5IndexTopHits_content=5`、`FTS5IndexFinderFollow_content=6`；收藏与 AI 历史内容表当前为 0。元数据还包括 `FTS5ChatRoomMembers=37,638`、`FTS5ContactLabels=3,835`、`FTS5MetaSOSHistory=2` 和 `FTS5NativeQueryClickRecallInfo=13`。完整 JSON 见 `evidence/fts-aggregates.json`，不输出索引词、文档 ID 或消息正文。

## 支付 KV

`storage(key,value,access_time,create_time,modify_time,expiration_period)`，当前 1 行；`size(name,size)`，当前 1 行。只公开列名、行数和 value 长度范围，不公开 key/value。

`WalletPay.db`、`WalletCoreDB-2.db`、`SecData.db` 当前均为单页 SQLite、0 表、`integrity_check=ok`。

## 登录、设备与钱包主库聚合

`EnMicroMsg.db` 当前全量已用 SQLCipher v1/page 1024 只读打开并通过完整 `integrity_check=ok`。验证时点为 5,700,355 页、252 表/462 索引；只查询表名、列名和行数，不输出任何字段值：

| 当前表 | 验证时点行数 | 内容边界 |
| --- | ---: | --- |
| `message` | 3,404,241 | 消息主表 |
| `AppMessage` | 417,373 | App/小程序/业务消息扩展 |
| `rcontact` | 26,644 | 联系人 |
| `rconversation` | 1,280 | 会话 |
| `chatroom` | 284 | 群聊 |
| `userinfo` / `userinfo2` | 107 / 261 | 登录与账号配置键值 |
| `HardDeviceInfo` | 2 | 硬件连接、认证/会话缓冲字段 |
| `WalletUserInfo` / `WalletBankcard` | 1 / 13 | 钱包注册与银行卡绑定状态 |
| `walletcache` / `WalletLuckyMoney` | 7 / 4 | 钱包缓存与红包状态 |

完整当前计数见 `evidence/main-aggregates.json`。下表来自 197 MB 独立历史解密快照，用于补充列结构和历史时点对比，不与当前计数混用：

| 表 | 行数 | 内容边界 |
| --- | ---: | --- |
| `userinfo` | 107 | `id,type,value` 配置/状态键值；不公开 value |
| `userinfo2` | 261 | `sid,type,value` 配置/状态键值；不公开 value |
| `SafeDeviceInfo` | 0 | 登录设备、客户端版本、自动登录能力字段 |
| `LiteAppAuthInfo` | 0 | 小程序 host、请求参数/头、更新时间 |
| `HardDeviceInfo` | 2 | 硬件设备连接、认证/会话缓冲和签名材料列 |
| `WalletUserInfo` | 1 | 钱包注册、实名/卡绑定状态、支付开关和余额能力字段 |
| `WalletBankcard` | 13 | 银行卡绑定状态、卡/银行类型、限额、支付能力和跳转字段 |
| `walletcache` | 6 | `sid,type,value` 钱包缓存；不公开 value |
| `AAPayRecord` | 0 | Apple Pay 消息关联 |
| `HoneyPayMsgRecord` | 0 | Honey Pay 消息关联 |
| `WalletLuckyMoney` | 0 | 红包收发/状态字段 |

`HardDeviceInfo` 的公开列包括 `deviceID,brandName,mac,deviceType,connProto,authKey,sessionKey,sessionBuf,authBuf` 等；`WalletBankcard` 的公开列包括 `bindSerial,cardType,bankcardState,bankName,bankcardTail,mobile,trueName` 等。这里只证明数据模型和本地存储角色，不公开或重建任何值。

## 来源

- 数据库文件名与大小：`database-inventory.md`。
- 表/列：SQLite `PRAGMA table_info`。
- 行数/时间/类型：只读 `count/min/max/group by` 聚合。
- 独立加密库表/列/行/长度/年份：`evidence/database-aggregates.json`；打开和校验过程见 `evidence/decryption-attempts.md`。
- 小程序 ORM 表注册：`smali-classes7-full/com/tencent/mm/plugin/appbrand/app/fa$$a.smali:31-115`、`smali-classes7-full/com/tencent/mm/plugin/appbrand/app/l.smali:20-298`。
