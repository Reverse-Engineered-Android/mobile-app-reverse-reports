# 已恢复 Schema 与字段边界

## 普通 SQLite

36 个普通 SQLite 的精确 DDL、列和聚合行数见 `evidence/database-inventory.json`。公开版本只保留表名、列名、类型和行数，不保留行值。

高价值表：

| 文件（脱敏） | 表 | 观察内容 |
| --- | --- | --- |
| `databases/Journals.db` | `VerifyIdentityModel_table` | 身份验证/认证器/快速支付授权记录；25 行 |
| `databases/alipayclient.db` | `userinfo` | 登录、会话、手势、绑定/认证状态；1 行 |
| `databases/alipayclient.db` | `hrdyutr` | 编码状态/扩展数据；1 行 |
| `files/client_db/<account-id>/client_database.db` | `$biz_metadata` 等 9 表 + revisit 表 | 配置/任务/变更队列；200 行 |
| `databases/mobileaix_feature.db` | `custom_data*` | 特征数据；12 行 |
| `databases/open_platform_apps.db` | `appentity` 等 | 小程序/开放平台安装与阶段状态；605 行 |
| `databases/messagebox.db` | 消息/服务/搜索表 | 消息盒子状态；142 行 |

## 已恢复 SQLCrypto

| 文件 | 关键表 | 行数 | 主要列 |
| --- | --- | ---: | --- |
| `FlareRecord-main.db` | `APBinAOPFlareRecordDAO_table` | 166 | `appId`, `createdTime`, `permission`, `userId` |
| `PrivacyLocalRecord-main.db` | `APBinAOPLocalRecordDAO_table` | 754 | 调用点、权限、应用状态、前后台/业务上下文 |
| `PrivacyLocalRecord-push.db` | 同上 | 20 | 同上 |
| `PrivacyLocalRecord-tools.db` | 同上 | 0 | 同上 |
| `PrivacyLocalRecord-widgetProvider.db` | 同上 | 0 | 同上 |
| `job_state.db` | `state_data` | 251 | job/namespace/content/extinfo/timestamp/uid |
| `permission_fortress_invoke_record-main.db` | `MiddlewareInvokeLocalRecordDAO_table` | 5 | 权限、接口、授权状态、调用结果 |
| `public_life.db` | `life_home` 等 6 个业务表 | 0 | 生活号主页、设置、广播、插件和模板订阅状态；当前快照无业务行 |

`public_life.db` 的密码入口来自 `LifeDatabaseHelper`；解密后包含 `life_home`、`life_broadcast_msg`、`life_setting`、`life_msg_plugin`、`life_msg_plugin_relation`、`life_biz_template_subscribe_status` 六个业务表，另有 `android_metadata`。七张业务/元数据表当前均为 0 行，因此内容结论是“容器与 schema 已恢复、当前快照无业务内容”，而不是数据库损坏或未解密。

## 敏感字段处理

以下字段只允许列名/类型/计数：`userId`、`uid`、`loginId`、`loginToken`、`sessionId`、`havanaId`、`mobileNumber`、`realName`、`nick`、`deviceId`、IMEI/IMSI/Android ID、token、seed、公钥/私钥、指纹/人脸数据、JSON 行值。真实行、请求/响应和标识符不在公开报告中。
