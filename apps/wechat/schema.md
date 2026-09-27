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
| 822083633 | 10,867 | 业务扩展类型，语义未完全还原 |
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

`AppBrandComm.db` 静态注册表中已恢复的表名：

`DevPkgLaunchExtInfo`、`AppBrandLocalUsageRecord`、`AppBrandMessInfoRecord`、`PredownloadCmdGetCodePersistentInfo2`、`AppBrandStarApp`、`AppBrandWxaPkgPreDownloadStatistics2`、`AppBrandFakeNativeSplashScreenshot`、`WxaAttrAvailableBackupTable`、`PersistentWxaSyncInvalidCodeCmd`、`PersistentWxaSyncInvalidContactCmd`、`WxaSecurityStorageInfo`、`MonitoredBluetoothDeviceInfoV2`、`AppBrandScreenshotInfo`、`AppBrandKVData`、`WxaAttributesTable`、`AppBrandWxaPkgManifestRecord`、`AppBrandWxaPkgManifestRecordWithDesc`、`AppBrandLauncherLayoutItem`、`WxagGameInfo`、`LaunchWxaAppRespTable`、`TipsMsgInfo`、`AppBrandCommonUseApp`、`AppBrandTaskRecentApp`。

## 支付 KV

`storage(key,value,access_time,create_time,modify_time,expiration_period)`，当前 1 行；`size(name,size)`，当前 1 行。只公开列名、行数和 value 长度范围，不公开 key/value。

`WalletPay.db`、`WalletCoreDB-2.db`、`SecData.db` 当前均为单页 SQLite、0 表、`integrity_check=ok`。

## 登录、设备与钱包主库聚合

以下结果来自 `EnMicroMsg.db` 的独立历史解密副本，仅查询表名、列名和行数，不输出任何字段值：

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
- 小程序 ORM 表注册：`smali-classes7-full/com/tencent/mm/plugin/appbrand/app/fa$$a.smali:31-115`、`smali-classes7-full/com/tencent/mm/plugin/appbrand/app/l.smali:20-298`。
