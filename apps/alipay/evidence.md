# 证据索引

## 版本与完整性

- 基础 APK SHA-256：`7274a03d16a223fb8d99a05e6e65071be1000e98586defb9b00a959b00e33122`。
- 数据库快照总数：61；快照复制前后源文件集合与哈希稳定。
- `sample-manifest.json` 记录 APK 哈希；数据库哈希在 `evidence/database-inventory.json` 中，文件名中的账号维度已替换为 `<account-id>`。

## DEX/JAVA

| 结论 | 源位置 |
| --- | --- |
| SQLCrypto `key`/`rekey` | `jadx4/sources/com/alibaba/sqlcrypto/sqlite/SQLiteConnection.java:1121`, `:1131` |
| SQLCrypto `buildKey` | `jadx4/sources/com/alibaba/sqlcrypto/sqlite/SQLiteConnection.java:299`; `native/libdatabase_sqlcrypto.so` VA `0xc9274`, format VA `0x98b05` |
| FTS key derivation | `jadx5/sources/com/alipay/android/phone/businesscommon/globalsearch/fts/FTSSearcher.java:987-989`; `libap_local_search.so` VA `0xae3ec` → `0xb69b4`, `PRAGMA key` VA `0x88638` |
| MD5 primitive | `jadx12/sources/com/alipay/mobile/common/utils/MD5Util.java:38-50`, `:234-248` |
| Social password protection boundary | `jadx13/sources/com/alipay/mobile/personalbase/db/EncryptOrmliteSqliteOpenHelper.java:297-322`, `:889-910`; `AlipaySecurityEncryptorUtils.java:44-58`; `BlueShieldSecurityEncryptor.java:203-226` |
| Scan cache password boundary | `jadx14/sources/com/alipay/mobile/scan/util/db/UnifiedScanDbHelper.java:47`; `jadx14/sources/com/alipay/mobile/scan/npc/cache/NPayCodeCache.java:562-569` |
| 状态库 password interface | `classes15.dex` → state dataset DB helper `:49`, `:53` |
| Flare/Privacy/Invoke password interface | `jadx9/sources/com/alipay/fusion/localrecord/flare/FlareRecordDbHelper.java:26`; `jadx7/sources/com/alipay/android/phone/mobilesdk/permission/fortress/invoke/InvokeRecordDbHelper.java:26`; `jadx7/sources/com/alipay/android/phone/mobilesdk/permission/fortress/auth/AuthStatusDBHelper.java:26` |
| Public-life database password/interface | `jadx12/sources/com/alipay/mobile/life/model/db/LifeDatabaseHelper.java:39-53`; decrypted `public_life.db` schema contains six business tables plus `android_metadata` |
| MobileAiX password generation/storage | `jadx15/sources/com/alipay/mobileaixdatacenter/util/PasswordUtils.java:29-41`, `:64-69` |
| Login RSA and server validation | `jadx14/sources/com/alipay/mobile/security/accountmanager/service/AccountServiceImpl.java:78` |
| User-token request/result | `jadx14/sources/com/alipay/mobile/securitycommon/aliauth/model/UserTokenRequest.java:9`; `UserTokenResult.java:9` |
| Havana PB field tags | `jadx15/sources/com/alipay/mobileapp/biz/rpc/ucc/usertoken/HavanaUserTokenReqPB.java:12` |
| Session model | `jadx14/sources/com/alipay/mobile/security/x/session/SessionModel.java:17` |
| MTOP request shape | `jadx20/sources/mtopsdk/mtop/domain/MtopRequest.java:10` |
| MTOP request assembly | `jadx20/sources/mtopsdk/mtop/protocol/converter/impl/AbstractNetworkConverter.java:181` |
| MTOP headers | `jadx20/sources/mtopsdk/common/util/HttpHeaderConstant.java:6` |
| Device ID request inputs | `jadx20/sources/mtopsdk/mtop/deviceid/DeviceIDManager.java:267` |
| Offline OTP init/renew | `jadx7/sources/com/alipay/android/phone/offlinepaycred/j.java:21`, `:61` |
| NFC token fields | `jadx20/sources/com/iap/android/nfc/acplugin/virtualcard/rpc/model/ApplyNfcTokenRequest.java`; `GetTokenStatusRequest.java`; `jadx20/sources/com/iap/android/nfc/wrapper/implementation/http/ActivateSyncTokens.java` |
| NFC sensor fields | `jadx7/sources/com/alipay/android/phone/nfc/core/NFCSensorInfoCollector.java:5`, `:149` |

## ELF/原生

| 结论 | 源位置 |
| --- | --- |
| SQLCrypto key/codec symbols | `native/libdatabase_sqlcrypto.symbols.txt`；`buildKey` VA `0xc9274`、`sqlite3CodecAttach` VA `0xe7c74` |
| SQLCrypto AES block loop | `native/libdatabase_sqlcrypto.codec.disasm.txt:512-566` |
| Offline QR verification surface | `native/libofflinecode.so` exports: `get_qrcode_info`, `parse_offline_code`, `verify_qrcode_v2/v3`, `is_duplicate_qrcode`, `get_key_id`, `gencode_timestamp`, `pos_timestamp` |
| Offline crypto choices | `native/libofflinecode.so` strings/symbols: SHA-1, SHA-256, ECDSA, SM2, HMAC |
| Network submission | `native/libtnet-4.0.0.so` exports `NAL_session_SubmitRequest`; imports `socket`/`connect` |

## 数据库

`evidence/database-inventory.json` 是 61 行逐库清单；`schema.md` 列出已恢复表/列和聚合行数。未恢复库的来源/原因在主报告“未恢复边界”中逐项说明。
