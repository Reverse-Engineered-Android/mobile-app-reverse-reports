# 证据索引

## 样本

- 包名/版本/哈希：[sample-manifest.json](../../sample-manifest.json)。
- 8.0.78 数据库/smali/原生范围：17 个 DEX、209 个原生相关文件、7 个 CSO 载荷、201 个唯一 ELF。
- 8.0.68 Java 协议范围：JADX `8.0.68` / `versionCode=3003` 请求/响应构造类；本仓库只保留最小必要源码片段。
- 版本边界：`evidence/java/*.java` 全部属于 8.0.68；`evidence/database-source.md`、`evidence/payment-network.md`、数据库聚合和 smali 位置属于 8.0.78。未把 8.0.68 Java 类直接当作 8.0.78 代码位置。

## 登录与消息

| 结论 | 公开证据 |
| --- | --- |
| ManualAuth URI/funcId/设备字段/EC key | `evidence/java/manualauth-request.java:25-35,54-158` |
| AutoAuth URI/funcId/BaseRequest/EC key | `evidence/java/autoauth-request.java:25-48,115` |
| RSA/AES 与 Hybrid ECDH 封包 | `evidence/java/hybrid-pack.java:33-48` |
| genSignature/pack/unpack ABI | `evidence/java/mmprotocal-jni.java:21-47` |
| NewSendMsg 网络 type、请求/响应字段 | `evidence/java/newsendmsg-network.java:251-292`、`newsendmsg-request.java:76-77`、`newsendmsg-item.java:130-135`、`newsendmsg-response*.java:103-138` |
| NewSync/NewInit URI/type | `evidence/java/newsync-network.java:90-96`、`newinit-network.java:26-32` |

## 数据库

| 结论 | 公开证据 |
| --- | --- |
| SQLCipher v1/page 1024 | `evidence/database-source.md` |
| device ID + UIN key 派生 | `evidence/database-source.md` |
| FTS/Priority 的 `t3.Ad` 或 UIN + D3 + username key 路径 | `evidence/database-source.md`、`evidence/decryption-attempts.md` |
| SQLCipher v1 独立库快照解密与剩余边界 | `evidence/decryption-attempts.md` |
| 微信 WCDB default 只读动态复核与 FTS 聚合输出 | `../../tools/wcdb-probe.java:51-224`、`evidence/decryption-attempts.md` |
| 8 个独立库的脱敏表/列/行/长度/年份聚合 | `evidence/database-aggregates.json` |
| 68 库逐库状态/大小/内容角色 | [database-inventory.md](database-inventory.md) |
| 表/列/行数/时间/类型聚合 | [schema.md](schema.md) |
| 登录设备、认证、钱包绑定/缓存聚合 | [schema.md](schema.md) |
| AppBrand ORM/表注册 | `evidence/database-source.md` |

当前 8 个独立加密小库的导出均通过 `integrity_check=ok`；`MicroMsgPriority.db` 仅完成静态 key/Schema 还原，280 组 SQLCipher 兼容和 168 组微信 WCDB default 只读输入均未命中。`FTS5IndexMicroMsg_encrypt.db` 已定位独立 key/打开链，但未完成一致快照和内容级解密，两者均不标为已解密。

## 支付

| 结论 | 公开证据 |
| --- | --- |
| DeviceInfo/UpdateDeviceInfo、PSK、AES-GCM、证书签名 | `evidence/payment-network.md` |
| 支付 CGI 路径 | [protocol.md](protocol.md) |
| 支付 LiteApp/KV Schema | [schema.md](schema.md) |

## 风控

| 结论 | 公开证据 |
| --- | --- |
| 设备字段进入认证 | `evidence/risk-source.md` |
| ADB/开发者/CPU/IMSI/SIM/Android ID 采集 | `evidence/risk-source.md` |
| Root/Hook/活体/MIS 机制 | `evidence/risk-source.md` |
| 误报边界 | `evidence/risk-source.md` |

## 不公开材料

原始数据库、`KeyInfo.bin`、key/UIN/device ID、消息/联系人/朋友圈/支付行、真实网络负载、完整 smali/ELF/反汇编和媒体均留在受控环境，不进入本仓库。
