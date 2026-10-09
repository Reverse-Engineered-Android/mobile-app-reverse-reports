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
| `EnMicroMsg.db` 当前全量只读解密、完整性和表级聚合 | `evidence/main-aggregates.json`、`evidence/decryption-attempts.md` |
| FTS/Priority 的 `t3.Ad` 或 UIN + D3 + username key 路径 | `evidence/database-source.md`、`evidence/decryption-attempts.md` |
| FTS 当前全量只读打开及表级内容聚合 | `evidence/fts-aggregates.json`、`evidence/decryption-attempts.md` |
| `MicroMsgPriority.db` WCDB default 内容级 Schema/行数聚合 | `evidence/priority-aggregates.json`、`evidence/decryption-attempts.md` |
| SQLCipher v1 独立库快照解密与剩余边界 | `evidence/decryption-attempts.md` |
| 微信 WCDB default 只读动态复核、防伪命中和脱敏 Schema 输出 | `../../tools/wcdb-probe.java:67-301`、`../../tools/wcdb-probe.java:337-373`、`evidence/decryption-attempts.md` |
| 8 个独立库的脱敏表/列/行/长度/年份聚合 | `evidence/database-aggregates.json` |
| 68 库逐库状态/大小/内容角色 | [database-inventory.md](database-inventory.md) |
| 表/列/行数/时间/类型聚合 | [schema.md](schema.md) |
| 登录设备、认证、钱包绑定/缓存聚合 | [schema.md](schema.md) |
| AppBrand ORM/表注册 | `evidence/database-source.md` |

当前 9 个独立加密小库快照均通过 `integrity_check=ok`：8 个按 SQLCipher v1 导出，`MicroMsgPriority.db` 按 WCDB default 完成内容级 Schema/行数聚合。`EnMicroMsg.db` 当前全量已通过 SQLCipher v1、完整性和脱敏表级聚合；`FTS5IndexMicroMsg_encrypt.db` 当前全量也已完成内容级只读打开。两个大库均未创建超过 1 GB 的副本。

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
| 触屏事件触发时机、限频、字段号与 CGI | `evidence/risk-upload-chain.md` |
| `reportclientcheck` URI 解混淆算法与结果 | `evidence/risk-upload-chain.md` |
| 朋友圈/公众号/小程序共享请求打包、RSA 与算法分发 | `evidence/protocol-crypto-dispatch.md` |
| `libMMProtocalJni` JNI 导出、算法枚举、包头与 checksum | `evidence/mmprotocaljni-native.md` |
| 误报边界 | `evidence/risk-source.md` |

## 协议原生层

| 结论 | 公开证据 |
| --- | --- |
| `EncodePack`/`EncryptPack`/`DecryptPack`/`DecodePack` 签名与地址 | `evidence/mmprotocaljni-native.md` |
| 12 字节 TLV 包头（magic `0x81`、checksum、size、reserved） | `evidence/mmprotocaljni-native.md` |
| checksum = `uint16(~(前缀和[end] - 前缀和[start-1]))` | `evidence/mmprotocaljni-native.md` |
| `emMMCryptAlgo` 枚举 0/3/5/10/11/12/13/14 | `evidence/mmprotocaljni-native.md` |
| Hybrid / HybridECDH / DoubleHybrid 四条封包路径 | `evidence/mmprotocaljni-native.md` |
| 硬编码 RSA 模数与 `010001` 指数 | `evidence/mmprotocaljni-native.md` |

## 协议业务分发

| 结论 | 公开证据 |
| --- | --- |
| 三类业务共享 `modelbase.r2.G2` 打包器与四条封包路径 | `evidence/protocol-crypto-dispatch.md:6-40` |
| 明文直通、flag 位、AES-GCM/SM4-GCM、Hybrid ECDH 分发 | `evidence/protocol-crypto-dispatch.md:42-156` |
| RSA 密钥轮换与 cmdId 白名单 | `evidence/protocol-crypto-dispatch.md:158-194` |

## 权限、提权与隐私

| 结论 | 公开证据 |
| --- | --- |
| 100 权限、59 导出组件、44 无组件权限 | [permissions.md](permissions.md)、`evidence/manifest-audit.md` |
| 重点聊天文件 Provider 的 UID/签名/路径校验 | `evidence/manifest-audit.md` |
| 其余 5 个无 Manifest 权限 Provider 的调用方/作用域校验 | [permissions.md](permissions.md) |
| `XWebCoreContentProvider` 缺少调用方鉴权、`filelist.config` 只读限制 | [permissions.md](permissions.md) |
| 明文网络配置与应用级安全属性 | `evidence/manifest-audit.md` |
| 2026-08-17 隐私指引与静态字段逐项对照 | [privacy.md](privacy.md) |
| IMSI/SIM/细粒度设备字段的告知颗粒度缺口 | [privacy.md](privacy.md)、`evidence/risk-source.md` |
| 真机版本/哈希一致性、10 已授予 + 6 未授予、AppOps `foreground`/`ignore` | `evidence/phone-runtime.md` |
| AppOps 时间戳因只读检查位移，不作为采集证据 | `evidence/phone-runtime.md` |

## 小程序静态提取

| 结论 | 公开证据 |
| --- | --- |
| `wxapkg` 133 文件、包/脚本哈希与“发布源码而非原始工程”边界 | `evidence/miniprogram-static.md` |
| 汉堡王环境域名、门店/菜单/订单/支付/登录 API | `evidence/miniprogram-static.md` |
| 手机号与地址的 `privacyCollect` 调用点 | `evidence/miniprogram-static.md` |
| 门店到支付的页面调用链 | [miniprogram.md](miniprogram.md) |
| 真机 112 个 `wxapkg` 与 appid → 版本序号 → 包/缓存对应链 | `evidence/phone-runtime.md`、`evidence/miniprogram-static.md` |

## 不公开材料

原始数据库、`KeyInfo.bin`、key/UIN/device ID、消息/联系人/朋友圈/支付行、真实网络负载、完整 smali/ELF/反汇编、原始小程序包/缓存和媒体均留在受控环境，不进入本仓库。
