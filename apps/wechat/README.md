# 微信 Android 逆向与数据取证

本目录合并两批授权取得的样本证据：微信 `8.0.78` / `versionCode=3180` 的同机数据库、smali/ELF 静态分析，以及微信 `8.0.68` / `versionCode=3003` 的 JADX Java 协议/风控源码。公开报告只保留版本、哈希、代码位置、数据库结构、聚合计数、协议结构和脱敏结论，不包含账号、联系人、消息正文、图片、位置、支付凭证、数据库 key 或真实请求/响应。

版本边界：数据库解密、smali、AppBrand ORM、数据库原生封装和支付 ELF 证据来自 `8.0.78`；`evidence/java/*.java` 的 ManualAuth、AutoAuth、NewSendMsg、NewSync、NewInit 和 Hybrid 封包证据来自 `8.0.68`。跨版本结论只在调用链、URI、协议类型和字段结构保持一致时合并；未对 `8.0.68` 数据库或 `8.0.78` Java 协议实现作越界推断。

入口：

- [主报告](report.md)
- [逐库清单](database-inventory.md)
- [协议与支付路径](protocol.md)
- [设备风控](risk-control.md)
- [Schema 与聚合](schema.md)
- [证据索引](evidence.md)

证据分为“已验证”“结构已证实”“静态推断”三级。`EnMicroMsg.db` 历史快照和 8 个独立加密快照已通过 SQLCipher v1 解密并校验；`MicroMsgPriority.db` 仍缺一个历史设备组件，280 组 SQLCipher 兼容和 168 组微信 WCDB default 只读输入均未命中，未完成内容级解密；当前 `EnMicroMsg.db` 全量和 `FTS5IndexMicroMsg_encrypt.db` 也未复制/解密。FTS 与 Priority 的独立 key/打开链已由 smali 还原，报告明确区分静态结构与内容级结果，不用文件名推测冒充解密结果。
