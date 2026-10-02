# 微信 Android 逆向与数据取证

本目录合并三批授权取得的样本证据：微信 `8.0.78` / `versionCode=3180` 的同机数据库、Manifest、smali/ELF 静态分析，微信 `8.0.68` / `versionCode=3003` 的 JADX Java 协议/风控源码，以及 2026-10-02 在已 root 的 Android 16 物理真机上对运行中微信进程所作的**只读**权限/AppOps/小程序包目录快照；另包含本地小程序缓存的只读 `wxapkg` 静态提取与汉堡王点餐流程分析。公开报告只保留版本、哈希、代码位置、数据库结构、聚合计数、协议结构、权限/隐私边界和脱敏结论，不包含账号、联系人、消息正文、图片、位置、支付凭证、数据库 key、设备标识、真实路径或真实请求/响应。

版本边界：数据库解密、smali、AppBrand ORM、数据库原生封装和支付 ELF 证据来自 `8.0.78`；`evidence/java/*.java` 的 ManualAuth、AutoAuth、NewSendMsg、NewSync、NewInit 和 Hybrid 封包证据来自 `8.0.68`。跨版本结论只在调用链、URI、协议类型和字段结构保持一致时合并；未对 `8.0.68` 数据库或 `8.0.78` Java 协议实现作越界推断。

入口：

- [主报告](report.md)
- [逐库清单](database-inventory.md)
- [协议与支付路径](protocol.md)
- [设备风控](risk-control.md)
- [权限与提权审计](permissions.md)
- [数据收集与告知边界](privacy.md)
- [小程序与汉堡王点餐](miniprogram.md)
- [真机只读运行时快照](evidence/phone-runtime.md)
- [Schema 与聚合](schema.md)
- [证据索引](evidence.md)

证据分为“已验证”“结构已证实”“静态推断”三级。`EnMicroMsg.db` 当前全量与历史快照、8 个 SQLCipher v1 独立库快照、`MicroMsgPriority.db` 的 WCDB default 快照以及 `FTS5IndexMicroMsg_encrypt.db` 当前全量均已完成内容级只读验证。两个大库均未创建超过 1 GB 的副本；主库以同 inode 只读隔离句柄完成 `integrity_check=ok` 和脱敏聚合。报告明确区分静态结构、当前时点计数与历史快照，不用文件名推测冒充解密结果。
