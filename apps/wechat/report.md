# 微信 8.0.68/8.0.78 数据库、认证、消息、支付与设备风控报告

## 结论摘要

1. 当前数据目录共发现 **68 个 `.db` 文件**。57 个是可直接读取的 SQLite；11 个具有非 SQLite 文件头。`EnMicroMsg.db` 历史快照和 8 个独立库快照已用 SQLCipher v1 解密并通过 `integrity_check=ok`；`MicroMsgPriority.db`、当前 `EnMicroMsg.db` 全量和 `FTS5IndexMicroMsg_encrypt.db` 仍是未完成项。
2. `EnMicroMsg.db` 的历史快照已按静态代码还原的 SQLCipher v1 参数解密：251 张表，100,506 条消息、16,674 个联系人、819 个会话和 221 个群聊记录；消息时间覆盖 2015-12-26 至 2026-09-22。当前库已增长到 5.84 GB，公开报告只引用历史快照的聚合结果。
3. `SnsMicroMsg.db` 是明文 SQLite。当前快照有 372 条朋友圈主记录、171 条用户/相册扩展记录、2 条广告拉取记录、2 条广告朋友圈记录和 5 条封面记录；另有评论、媒体、草稿、标签等表但当前为空。
4. 小程序缓存分为两层：`AppBrandComm.db` 解密后有 54 张表、772 行，覆盖属性、manifest、启动/使用、KV、插件、安全存储和预下载统计；`lite_main.db` 保存包、鉴权、基础库、动态配置、采样配置和触发动作，已解析实例包含 18 个包、21 份配置、30 份采样配置和 2 个触发动作。
5. 支付路径不是单一 JSON API，而是“业务 CGI + PayMars/MMTLS + PSK/session + 签名证书”的组合。静态映射确认 `genprepay`、`payauthapp`、`jsapipay`、`tinyapppay`、`scanqrcodepay`、`h5pay`、`offlinepayconfirm`、`payorderquery`、绑定/短信/数字证书等完整链路。
6. 登录认证由 `ManualAuth` / `AutoAuth` Protobuf 进入 `MMProtocalJni`，再经签名、RSA/AES 或 Hybrid ECDH 封包和 Mars/MMTLS 传输。脱离真实 UIN、device ID、session/autoauth key、cookie、路由和签名材料不能直接重放 CGI。
7. 设备风控信号包括设备标识、安装/构建信息、ADB/开发者状态、Root/Hook 痕迹、触摸轨迹、相机/音频/显示状态和 Normsg 不透明安全数据。静态分析能证明采集或进入上传结构，但不能还原服务端评分权重。

## 样本与方法

| 项目 | 值 |
| --- | --- |
| 数据库/smali/支付 ELF 版本 | `8.0.78` / `versionCode=3180` |
| Java 协议/登录风控版本 | `8.0.68` / `versionCode=3003` |
| 包名 | `com.tencent.mm` |
| ABI | `arm64-v8a` |
| `8.0.78 base.apk` SHA-256 | `41f7dc1f720767fa78fa20dd13ea034b817bbf6ebd23dfd1324c647499c9c1ba` |
| `8.0.68` XAPK SHA-256 | `1ecc00602a31cefd64e0e121db5232b1bfa8893795ee39516088b51ddf847208` |
| 8.0.78 静态范围 | 17 个 DEX、全部 arm64 原生文件、7 个 CSO 载荷、smali 交叉引用和重点 ELF |
| 8.0.68 协议范围 | JADX Java 请求/响应构造、协议 JNI 声明、Mars 网络类和相关原生字符串/符号 |
| 数据范围 | 68 个 SQLite/WCDB 文件、媒体目录结构、历史解密快照 |

数据库字段和密钥路径由 8.0.78 smali、JNI/ELF 与 `PRAGMA table_info` 交叉确认；登录/消息 Java 协议证据来自 8.0.68 JADX，支付网络和数据库原生证据来自 8.0.78。数据库行仅做计数、类型和时间范围聚合，不公开行样例。证据位置见 [evidence.md](evidence.md)。

## 数据库与内容

完整逐库状态见 [database-inventory.md](database-inventory.md)，重点 Schema 与聚合见 [schema.md](schema.md)。

### 消息、登录与联系人

- `message`：100,506 行，109 个不同会话对象；96,337 条接收、4,169 条发送。已识别类型包括文本、图片、语音、视频、表情、App/扩展消息和系统消息，其余类型码保留原值聚合。
- `rcontact`：16,674 行；`rconversation`：819 行；`chatroom`：221 行。
- `AppMessage`：13,664 行，保存 App/小程序/业务卡片 XML、标题、描述、来源和消息 ID 关联。
- `userinfo` / `userinfo2`：保存账号状态和配置键值，但公开报告不输出值。
- 登录/设备聚合：`userinfo` 107 行、`userinfo2` 261 行、`SafeDeviceInfo` 0 行、`HardDeviceInfo` 2 行。`HardDeviceInfo` 的 Schema 包含硬件连接、认证/会话缓冲和签名材料列，但不公开设备值。
- 钱包聚合：`WalletUserInfo` 1 行、`WalletBankcard` 13 行、`walletcache` 6 行、`AAPayRecord` 0 行、`HoneyPayMsgRecord` 0 行、`WalletLuckyMoney` 0 行。结果只说明本地支付绑定/状态缓存规模，不构成真实支付交易记录。

### 朋友圈

- `SnsInfo`：372 行，2018-08-18 至 2026-09-27。
- `snsExtInfo3`：171 行，包含相册摘要、背景、增量游标、媒体认证/加密字段等结构。
- `adsnsinfo`：2 行，`AdPullRecordsInfo`：2 行；说明广告朋友圈和拉取痕迹单独建表。
- `SnsComment`、`SnsMedia`、`snsDraft`、`snsTagInfo2` 等表当前为 0 行，不代表应用从未使用，只代表该快照为空。

### 小程序与支付缓存

- `AppBrandComm.db` 解密后有 54 张表、772 行，其中 26 张非空：`AppBrandCommonKVData=153`、`WxaAttributesTable=99`、`AppBrandMessInfoRecord=99`、`AppBrandWxaPkgManifestRecord=71`、`AppBrandLauncherLayoutItem=38`、`WxaWeDataExptInfo=99`。表覆盖包启动扩展、使用记录、星标/常用/最近任务、manifest、属性、KV、插件、截图、蓝牙设备监控、安全存储和预下载统计；不输出 appId、username、路径或值。
- `WxExpt.db` 解密后有 `ExptItem=959`、`ExptKeyMapId=1873`；`WxCgiReport.db` 只有 1 张空的本地 CGI 报告缓存表；`newuba.db` 有 3 条行为缓存，`reportStr` 长度为 1,042–1,376 字节，内容值不公开。
- `ceee…/lite_main.db` 的 `LiteAppInfo=18`、`LiteAppConfigInfo=21`、`LiteAppSamplingConfigInfo=30`、`LiteAppTriggerActions=2`、`LiteAppBaselibInfo=1`。
- `wxpay_kit` 下两个 `lite_main.db` 当前为空，但 Schema 明确包含 `LiteAppAuthInfo`、`signatureKey`、`packageConfigPath`、`configJson` 等包鉴权/配置字段。
- 三个 `@pay.db` 中一个有 KV `storage` 和 `size` 表，其余为空。公开报告只说明字段形状和行数，不输出 key/value。

### 资源下载、文件索引、收藏与 Edge 缓存

- `EnResDown.db` 解密后仅含 `ResDownloaderRecordTable`，151 行；字段覆盖 URL/版本、重试与状态、路径/大小/类型、过期时间、MD5/签名、压缩/加密、keyVersion、appId/packageId 等下载控制信息，不输出 URL、路径、哈希、key 或业务 ID。
- `WxFileIndex.db` 解密后含 6 表、317,265 行。`WxFileIndex3` 为主索引（消息 ID/用户名/类型/路径/大小/时间/哈希/磁盘占用/链接 UUID/详情等），其余表负责 talker 脏标记、下载迁移、链接化、刷新队列和注册表；不输出用户名、路径、哈希或文件详情。
- `enFavorite.db` 解密后含 `FavItemInfo`、`FavSearchInfo`、`FavEditInfo`、`FavCdnInfo`、`FavTagInfo`、`FavConfigInfo`、`FavDelInfo` 七表，当前均为 0 行；Schema 明确收藏内容、搜索文本、编辑/CDN 状态、标签、配置和删除同步字段。
- `Edge.db` 解密后有 `EdgeComputingCacheDataModel_Instance` 和 `EdgeComputingCacheDataModel_Normal` 两表，共 2 行；列为 `configID TEXT, reportTimeEC LONG, data TEXT`，不公开配置 ID、时间线或 data。

## 登录认证

`ManualAuth` 使用安全模式 `/cgi-bin/micromsg-bin/secmanualauth`（funcId 252）或兼容模式 `/cgi-bin/micromsg-bin/manualauth`（funcId 701）。请求包含 BaseRequest、设备标识、SoftType、ClientSeqID、签名、设备名/类型、语言、时区、渠道、版本、包名、登录标识和 CGI 校验材料，并生成临时 EC key。

`AutoAuth` 对应 `/cgi-bin/micromsg-bin/secautoauth` / `autoauth`，依赖已持久化的 UIN、session/autoauth key、cookie、设备身份和签名材料。响应存在 UIN、ECDH/密钥材料、session/cookie 和同步字段；客户端还会校验 UIN、认证段和 session 的一致性。

结论依据：[protocol.md](protocol.md) 与 `evidence/java/manualauth-request.java`、`autoauth-request.java`、`mmprotocal-jni.java`。

## 消息协议

文本发送使用 `/cgi-bin/micromsg-bin/newsendmsg`，网络 type 522，请求 cmdId 237、响应 cmdId 1000000237。请求根为 `Count + repeated Item`，每项含 `ToUserName`、`Content`、`Type`、`CreateTime`、`ClientMsgId`、`MsgSource` 和可选 `SendMsgTicket`；响应逐项返回 `Ret`、本地/服务器消息 ID、时间和 MsgSource。

收消息/同步通过 `newsync`（type 138）和 `newinit`（type 139）。所有请求先由 Java 构造 Protobuf，再经 `genSignature` / `pack` 或 Hybrid 封包，响应由 `unpack` 还原。

## 支付路径

支付组件使用 PayMars/MMTLS。静态字符串显示 16 字节 device ID、access/refresh PSK、AES-GCM 加密刷新 PSK、握手消息序列化、证书签名校验和 CGI 映射。主要链路：

1. 获取身份/令牌：`getusertoken`、`getpaypwdtoken`、`offlinegettoken`、`getcardserial`。
2. 预支付与授权：`genprepay`、`payauthapp`、`payauthnative`、`checkpayjsapi`、`jsapiauthen`。
3. 场景支付：`jsapipay`、`tinyapppay`、`scanqrcodepay`、`h5pay`、`offlinepayconfirm`、F2F/转账。
4. 验证与绑定：`verifybind`、`verifyreg`、`verifysms`、余额/银行卡/LQT 绑定 authen/verify。
5. 查询、取消与证书：`payorderquery`、`offlinequeryorder`、`cancelpay`、`gendigitalcert`、`deletedigitalcert`。
6. 风控聚合：`riskaggrverifysign`。

具体 CGI reqId/respId、主机族和认证边界见 [protocol.md](protocol.md)。

## 设备风控

已确认进入登录/协议结构的字段包括 UIN、SessionKey、DeviceID、ClientVersion、DeviceType、Scene、SoftType、ClientSeqID、签名证书哈希、厂商/型号/系统构建、语言、时区、包名、渠道、登录标识、ECDH 公钥和 CGI 校验公钥。

采集面还包括 Android ID/OAID/IMSI/SIM 国家、首次安装时间、CPU/Radio/build/board/product、ADB 与开发者选项、应用安装/卸载事件、触摸轨迹、相机/音频/显示状态和厂商 APPRISK 事件。Normsg 原生数据的内部编码未完全还原，因此只能证明数据块进入上传路径，不能逐字段断言。

详细证据强度与误报剔除见 [risk-control.md](risk-control.md)。

## 限制与未完成项

- `AppBrandComm.db`、`WxExpt.db`、`WxCgiReport.db`、`newuba.db`、`EnResDown.db`、`enFavorite.db`、`Edge.db`、`WxFileIndex.db` 的本地快照已通过 `PRAGMA cipher_compatibility=1` 打开并导出只读明文校验；实际 key 不公开。
- `MicroMsgPriority.db` 尚无内容级解密证据；其静态 key、WCDB page size 4096/defaultVersion 打开路径、D3 cache 证据和 280 组失败边界见 `evidence/decryption-attempts.md`。
- 当前 `EnMicroMsg.db` 为 5.84 GB，历史解密快照只有 197 MB；本报告不把历史行数冒充当前全量行数。
- `FTS5IndexMicroMsg_encrypt.db` 为 1.10 GB 搜索索引；需要在应用停止写入时复制并解密，才能列出 FTS 表和索引词分布。
- `WxFileIndex.db` 已解密并验证 6 表/317,265 行；仍不公开任何文件名、用户名、哈希或内容详情。
- 未主动触发真实登录、支付、人脸核验或小游戏，因此没有真实交易/认证请求或响应。
- 服务端评分、远程 feature gate、动态 UDR 模块和加密配置不能仅靠静态分析穷尽。

公开边界遵循 [SECURITY.md](../../SECURITY.md)：不发布数据库、账号标识、联系人、消息、媒体、支付数据、设备 ID、Cookie、Token、key 或真实网络负载。
