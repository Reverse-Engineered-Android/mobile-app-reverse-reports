# 微信数据库逐库清单

## 状态定义

- **明文 SQLite**：文件头为 `SQLite format 3`，只读 `integrity_check=ok`，可枚举表/列/行数。
- **SQLCipher v1 快照已解密**：主库历史副本已按源码还原参数解密，并有独立明文副本。
- **独立加密未解密**：文件头不是 SQLite；主库 key、SQLCipher v1/2/3/4 参数组合均未打开。该类包含小库和 `WxFileIndex.db`；内容角色只按文件名和静态代码标注，不冒充解密结果。
- **大库未解密**：当前文件太大，未创建超过 1 GB 的完整副本；历史小快照或 Schema 线索单独标注。

当前源目录共 68 个数据库：57 个明文 SQLite、11 个非 SQLite 头加密库；后者由 2 个加密大库和 9 个独立加密库组成（`WxFileIndex.db` 属于独立加密类）。路径中的账号目录、UIN、AppId/设备 hash 已统一写为 `<opaque-id>`。

## 主库、搜索与文件索引

| 数据库 | 当前大小 | 状态 | 已明确内容 |
| --- | ---: | --- | --- |
| `MicroMsg/<opaque-id>/EnMicroMsg.db` | 5,836,770,304 | SQLCipher v1 历史快照已解密 | 消息、联系人、会话、群聊、AppMessage、账号配置、小程序配置、钱包缓存、设备绑定；历史快照 251 表/100,506 消息 |
| `MicroMsg/<opaque-id>/FTS5IndexMicroMsg_encrypt.db` | 1,104,314,368 | 加密大库未解密 | 全文搜索索引；源码明确使用加密 FTS5 库 |
| `MicroMsg/<opaque-id>/WxFileIndex.db` | 104,028,160 | 独立加密未解密 | 文件索引/文件元数据容器 |

## 独立加密库

| 数据库 | 大小 | 解密状态 | 静态已明确的内容角色 |
| --- | ---: | --- | --- |
| `MicroMsg/<resource-id>/EnResDown.db` | 68,608 | 未解密 | 资源下载/下发记录 |
| `MicroMsg/<opaque-id>/WxExpt.db` | 825,344 | 未解密 | 实验/feature 配置 |
| `MicroMsg/<opaque-id>/newuba.db` | 122,880 | 未解密 | UBA/行为分析缓存 |
| `MicroMsg/<opaque-id>/WxCgiReport.db` | 3,072 | 未解密 | CGI 调用报告/遥测 |
| `MicroMsg/<opaque-id>/MicroMsgPriority.db` | 147,456 | 未解密 | 消息/任务优先级状态 |
| `MicroMsg/<opaque-id>/AppBrandComm.db` | 4,092,928 | 未解密 | 小程序公共业务数据库；当前公开证据恢复至少 23 个表名 |
| `MicroMsg/<opaque-id>/enFavorite.db` | 29,696 | 未解密 | 收藏 |
| `MicroMsg/<opaque-id>/Edge.db` | 10,240 | 未解密 | Edge/边缘缓存状态 |

第 9 个独立加密小库是 `WxFileIndex.db`，已在上表列出。

## 明文 SQLite（57 个）

| 数据库 | 大小 | 内容 |
| --- | ---: | --- |
| `cache/<opaque-id>/finder/aff/finder_main_cpp.db` | 167,936 | Finder/视频号 C++ 侧索引与状态 |
| `cache/<opaque-id>/finder/aff/finder_main_aff.db` | 20,480 | Finder AFF 状态 |
| `files/public/voip/db/voip.db` | 4,096 | 公共 VoIP 状态，当前无表 |
| `files/liteapp/<opaque-id>/db/lite_main.db` | 45,056 | 小程序 LiteApp 包/配置 Schema，当前为空 |
| `files/liteapp/db/<opaque-id>/lite_main.db` | 53,248 | 小程序索引实例，当前为空 |
| `files/liteapp/db/<opaque-id>/lite_main.db` | 102,400 | 已用小程序包、基础库、配置、采样配置、触发动作 |
| `files/liteapp/<opaque-id>/kv/snapshot.db` | 4,096 | 小程序快照 KV，当前无表 |
| `files/liteapp/<opaque-id>/kv/sticker.db` | 4,096 | 小程序贴纸缓存，当前无表 |
| `files/liteapp/<opaque-id>/kv/wxalite…@pay.db` | 20,480 | 支付小程序 KV：`storage` 1 行、`size` 1 行 |
| `files/liteapp/<opaque-id>/kv/wxalite…@pay.db` | 4,096 | 支付小程序 KV，当前无表 |
| `files/liteapp/<opaque-id>/kv/wxalite…@pay.db` | 4,096 | 支付小程序 KV，当前无表 |
| `files/liteapp/<opaque-id>/kv/wxalite….db` | 4,096 | 小程序私有 KV，当前无表 |
| `files/liteapp/<opaque-id>/kv/wxalite….db` | 4,096 | 小程序私有 KV，当前无表 |
| `files/wxpay_kit/.../runtime/0/db/lite_main.db` | 53,248 | 支付 LiteApp 包/鉴权/配置 Schema，当前为空 |
| `files/wxpay_kit/.../runtime/<redacted-uin>/db/lite_main.db` | 53,248 | 与上项字节相同，账户运行实例，当前为空 |
| `MicroMsg/AFFUDRPath/.../udr.db` | 167,936 | UDR 动态模块/资源数据库 |
| `MicroMsg/<opaque-id>/FinderAccounts_01.db` | 4,096 | Finder 账号状态，当前无表 |
| `MicroMsg/<opaque-id>/MMPlayerMediaInfo.db` | 12,288 | 播放器媒体元数据 |
| `MicroMsg/<opaque-id>/VoIPDB-8.db` | 4,096 | VoIP 状态，当前无表 |
| `MicroMsg/<opaque-id>/biz_persist/aff_db/biz.db` | 8,003,584 | 公众号/业务持久化数据 |
| `MicroMsg/<opaque-id>/UnEncryptNewBiz.db` | 4,096 | 非加密新业务状态，当前无表 |
| `MicroMsg/<opaque-id>/WalletPay.db` | 4,096 | 钱包支付数据库，当前无表 |
| `MicroMsg/<opaque-id>/aff_db/status_info_v4.db` | 737,280 | AFF 状态/同步信息 |
| `MicroMsg/<opaque-id>/roam_backup/.../migration.db` | 327,680 | 漫游/迁移缓存 |
| `MicroMsg/<opaque-id>/roam_backup/.../roam_backupper.db` | 28,672 | 漫游备份状态 |
| `MicroMsg/<opaque-id>/MBExternalVideoInfo.db` | 12,288 | 外部视频元数据 |
| `MicroMsg/<opaque-id>/TextStatus.db` | 1,646,592 | 文字状态/动态 |
| `MicroMsg/<opaque-id>/FinderMM029.db` | 761,856 | Finder/视频号业务状态 |
| `MicroMsg/<opaque-id>/multitask/aff_db/star.db` | 4,096 | 多任务星标状态 |
| `MicroMsg/<opaque-id>/FinderLiveShopMsg.db` | 4,096 | Finder 直播小店消息状态 |
| `MicroMsg/<opaque-id>/SnsMicroMsg.db` | 1,785,856 | 朋友圈主库：SnsInfo/评论/媒体/广告/封面/扩展 |
| `MicroMsg/<opaque-id>/FinderMessage006.db` | 4,096 | Finder 消息状态，当前无表 |
| `MicroMsg/<opaque-id>/PreDownloadCheck.db` | 36,864 | 预下载校验状态 |
| `MicroMsg/<opaque-id>/FinderRedDotHistoryRecord.db` | 16,384 | Finder 红点历史 |
| `MicroMsg/<opaque-id>/KaraCore.db` | 16,384 | Kara 核心业务状态 |
| `MicroMsg/<opaque-id>/KaraMM.db` | 4,096 | Kara 微信侧状态 |
| `MicroMsg/<opaque-id>/brand_service_persist/aff_db/brandService.db` | 450,560 | 品牌服务持久化 |
| `MicroMsg/<opaque-id>/brand_service_persist/aff_db/brsNoti.db` | 167,936 | 品牌服务通知 |
| `MicroMsg/<opaque-id>/mediaOpt/.../template_background_video_table.db` | 4,096 | 视频模板背景状态 |
| `MicroMsg/<opaque-id>/mediaOpt/.../background_video_table.db` | 4,096 | 后台视频处理状态 |
| `MicroMsg/<opaque-id>/WalletCoreDB-2.db` | 4,096 | 钱包核心数据库，当前无表 |
| `MicroMsg/<opaque-id>/FinderWCDB.db` | 77,824 | Finder WCDB 状态 |
| `MicroMsg/<opaque-id>/GameLife.db` | 4,096 | 游戏/小游戏生活状态 |
| `MicroMsg/<opaque-id>/HashInfo.db` | 532,480 | 文件/内容 hash 索引 |
| `MicroMsg/<opaque-id>/EcsConversation.db` | 12,288 | ECS 会话状态 |
| `MicroMsg/<opaque-id>/secdata/SecData.db` | 4,096 | 安全数据数据库，当前无表 |
| `MicroMsg/<opaque-id>/FinderLiveTipsBar001.db` | 4,096 | Finder 直播提示状态 |
| `MicroMsg/<opaque-id>/EcsWeShop.db` | 4,096 | ECS 微店状态 |
| `MicroMsg/<opaque-id>/DupCheck-1.db` | 30,285,824 | 去重检查索引 |
| `MicroMsg/<opaque-id>/DeviceFileExtInfo.db` | 4,096 | 设备文件扩展信息，当前无表 |
| `MicroMsg/<opaque-id>/XiaoweiTask.db` | 12,288 | 小微/语音任务状态 |
| `MicroMsg/<opaque-id>/sns_show_teach.db` | 4,096 | 朋友圈展示教学状态 |
| `MicroMsg/<opaque-id>/W1wPersonalMsg.db` | 4,096 | W1W 个人消息状态 |
| `MicroMsg/<opaque-id>/sns_star_info.db` | 135,168 | 朋友圈星标/兴趣信息 |
| `MicroMsg/<opaque-id>/MsgGroupInfo.db` | 4,096 | 消息分组状态 |
| `MicroMsg/<opaque-id>/CommonOneMicroMsg.db` | 12,288 | 通用 MicroMsg 状态 |
| `databases/Scheduler.db` | 28,672 | 调度任务状态 |

## 解密边界

- SQLCipher v1 的参数由 `kh5/f.smali:94-110` 直接确认：page size 1024、SQLCipher version 1。
- key 候选由 `kh5/b0.smali:1658-1716` 还原：遍历历史 device ID，与 UIN 拼接后取摘要前 7 个十六进制字符。
- `IMEISave.smali:15-145` 证明历史 device ID 来自加密 `KeyInfo.bin`，并额外加入当前/兼容 device ID。
- 实际 key、UIN、device ID、`KeyInfo.bin` 内容均不公开。
- 2026-09-27 对 `AppBrandComm.db`、`WxExpt.db`、`WxCgiReport.db`、`newuba.db` 的本地快照执行了 SQLCipher v1/兼容首页 HMAC 候选测试，均未命中；过程和边界见 `evidence/decryption-attempts.md`。
- 9 个独立加密库未解密；它们的“内容角色”是静态代码/文件名分类，不是行级解密结果。
