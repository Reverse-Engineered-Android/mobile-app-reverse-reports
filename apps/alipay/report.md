# Alipay Android 数据层与支付认证路径

## 样本

| 项目 | 值 |
| --- | --- |
| 包名 | `com.eg.android.AlipayGphone` |
| versionName | `12.12.30.8000` |
| versionCode | `212310` |
| 基础 APK SHA-256 | `7274a03d16a223fb8d99a05e6e65071be1000e98586defb9b00a959b00e33122` |
| 分析日期 | 2026-09-27 |

所有数据库均为只读快照检查。数据库行内容、账号字段和设备值不进入公开仓库。

## 数据库覆盖

数据库逐项状态见 [database-inventory.md](database-inventory.md)。总体结论：

| 状态 | 数量 | 说明 |
| --- | ---: | --- |
| 普通 SQLite，可读 | 36 | 已枚举表、列、DDL 和聚合行数 |
| SQLCrypto，已恢复 | 8 | 已恢复 schema/行数；密码和解密文件不公开 |
| SQLCrypto，未恢复 | 12 | 静态代码确认密码来自运行时保护域或派生值 |
| UnQLite 键值库 | 5 | 打开容器并确认单键/单值，值为不透明密文 |

这覆盖清单中的全部 61 个数据库；“未恢复”不等于内容为空，而是静态证据不足以安全、确定地取得该库密码或值密钥。

## SQLCrypto 数据层

`libdatabase_sqlcrypto.so` 的 `sqlite3CodecAttach`（VA `0xe7c74`）安装 codec，页处理回调从 VA `0xe8414` 开始。反汇编循环以 `0x10` 为步长调用 `aes_decrypt`/`aes_encrypt`；同一 ELF 导出 `aes_decrypt_key128`、`aes_encrypt_key128`，说明编解码是 16 字节块级 AES。页面头可恢复为标准 SQLite 头，验证了普通 SQLite 页语义在 codec 下保持不变。

证据：

- `libdatabase_sqlcrypto.so`，`sqlite3CodecAttach`/`sqlite3_key*`/`aes_*` 符号；
- `native/libdatabase_sqlcrypto.codec.disasm.txt:512`，页回调和 `0x10` 步长；
- `native/libdatabase_sqlcrypto.codec.disasm.txt:539`、`:563`，AES 解密/加密调用；
- `jadx4/sources/com/alibaba/sqlcrypto/sqlite/SQLiteConnection.java:1121`、`:1131`，Java `PRAGMA key`/`rekey` 接口。

已恢复的 8 个库来自静态常量、helper 密码入口或受保护接口：`job_state.db`、`FlareRecord-main.db`、4 个 `PrivacyLocalRecord-*` 库、`permission_fortress_invoke_record-main.db` 和 `public_life.db`。恢复输出仅在私有分析目录用于统计，公开报告不提供输出。

### 两条密钥派生路径

- **SQLCrypto 连接密钥**：`SQLiteConnection.setEncryptKey()` 直接把 `mConfiguration.password` 放入 `PRAGMA key`/`rekey`（`jadx4/sources/com/alibaba/sqlcrypto/sqlite/SQLiteConnection.java:1121-1133`）。对已恢复库的离线页解密验证使用密码 UTF-8 前 16 字节、不足补 `{` 的 AES-128 页密钥；这是本样本的验证结果，不应外推为所有 SQLCrypto 文件的通用派生规则。
- **`buildKey` 不是上述连接密钥的已证实来源**：`libdatabase_sqlcrypto.so` 的 `Java_com_alibaba_sqlcrypto_sqlite_SQLiteConnection_buildKey`（VA `0xc9274`）使用 `%s-%d-%s-%s` 拼入固定标签、CPU family、输入串和固定混淆常量，再调用 `MessageDigest`/`digest` 得到 32 位小写 MD5（格式字符串 VA `0x98b05`；Java 声明 `jadx4/sources/com/alibaba/sqlcrypto/sqlite/SQLiteConnection.java:299`）。当前静态调用图没有把该返回值接到 `mConfiguration.password` 或 `setEncryptKey`，所以不把 `buildKey` 结果当作本样本 SQLCrypto 文件的解密密钥；固定常量本身不公开。
- **FTS `index.db` 密钥**：`FTSSearcher` 将 `SqliteDbModel.getPassword()` 写入 `originDBEncryptKey`（`jadx5/sources/com/alipay/android/phone/businesscommon/globalsearch/fts/FTSSearcher.java:987-989`）。`libap_local_search.so` 从该配置字段调用 `get_index_security_key`（VA `0xae3ec` → `0xb69b4`），JNI 调用 `MD5Util.encrypt(String)`；`MD5Util.encrypt` 返回 UTF-8 输入的 MD5 小写 hex（`jadx12/sources/com/alipay/mobile/common/utils/MD5Util.java:38-50`、`:234-248`），随后原生层使用 `PRAGMA key='%s'`（VA `0x88638`）。因此 FTS 索引键是 **源数据库 password 再做 MD5**；它不是 `buildKey` 的 `%s-%d-%s-%s` 路径。
- **社交源密码边界**：社交 helper 的 password 由 `AlipaySecurityEncryptorUtils.encrypt(userId)` 产生（`jadx13/sources/com/alipay/mobile/personalbase/db/EncryptOrmliteSqliteOpenHelper.java:297-322`、`:889-910`）；该工具委托 `BlueShieldSecurityEncryptor.staticSafeEncrypt` 的 Trusted Terminal 模块（`AlipaySecurityEncryptorUtils.java:44-58`；`BlueShieldSecurityEncryptor.java:203-226`）。所以 FTS 证据支持的表达是 `MD5(TrustedTerminal保护变换(userId))`，而不是 `MD5(userId)`；保护变换输出/密钥未恢复。

三个 FTS 文件的首个 16 字节密文块相同、第二块不同；这与相同索引密钥和相同首块明文相容，也与上述共同 password 来源相容，但公开报告不据此恢复或公布任何密钥。

### 未恢复边界

- MobileAiX 三个库的密码由 97 字符随机值生成并以 `AlipaySecurityEncryptor` 保存（`jadx15/sources/com/alipay/mobileaixdatacenter/util/PasswordUtils.java:29`、`:41`、`:64`）；密文位于 `files/antsp/mobileaix_default`，但 `EncryptDataUtils` 的实现不在当前 DEX 集合中。
- `scan_biz.db` 的密码是运行时 `sha1Key`；`UnifiedScanDbHelper` 将其直接传入 `setPassword`（`jadx14/sources/com/alipay/mobile/scan/util/db/UnifiedScanDbHelper.java:47`），而该值来自 Trusted Terminal 的受保护自定义数据槽 `codecsignkey`（`jadx14/sources/com/alipay/mobile/scan/npc/cache/NPayCodeCache.java:562-569`）。
- 社交/账号维度的 `chatmsgdb<account-id>.db`、`contactsdb<account-id>.db`、`discussioncontactdb<account-id>.db`、`socialmobiledb<account-id>.db`、`timelinedb<account-id>.db`：构造器传入用户维度字符串（例如 `ChatEncryptOrmliteHelper.java:134-145`），但实际 password 受 Trusted Terminal 保护变换控制，故不从文件名猜测密码。
- `public_life.db` 已由 `LifeDatabaseHelper` 的密码入口解密；6 个业务表和 `android_metadata` 当前均为 0 行，内容边界见 `schema.md`。
- 三个沙箱 FTS `index.db` 的源 password 和 MD5 派生输入受保护；五个 `sc_edge` UnQLite 文件是值不透明的键值容器。

## 登录与授权

### 密码登录

`AccountServiceImpl.PwdValidate` 将密码交给 `RSAService.RSAEncrypt`，再把 `loginId` 和加密密码放入 `ValidatePasswordRequest`，由服务端验证（`jadx14/sources/com/alipay/mobile/security/accountmanager/service/AccountServiceImpl.java:78`；`jadx15/sources/com/alipay/mobilesecurity/common/service/model/req/ValidatePasswordRequest.java:86`）。静态证据没有证明客户端本地完成密码验证，也没有暴露明文密码。

`alipayclient.db` 的 `userinfo` 是登录/账户状态缓存，包含 `loginToken`、`sessionId`、`userId`、登录标识、手势配置和绑定/认证状态等列；公开报告只列列名，不列任何行值。

### 授权码换取 user token

`UserTokenRequest` 携带 `authCode`、`appid`、`scope`、`site`、`bizScene` 和扩展参数；`UserTokenResult` 返回 `userToken`、结果码、绑定标志和可选 H5 URL（`jadx14/sources/com/alipay/mobile/securitycommon/aliauth/model/UserTokenRequest.java:9`；`jadx14/sources/com/alipay/mobile/securitycommon/aliauth/model/UserTokenResult.java:9`）。请求被编码为 `HavanaUserTokenReqPB`，字段 tag 为 bizScene=1、authCode=2、appid=3、scope=4、quietOauth=5、site=6、extParams=7（`jadx15/sources/com/alipay/mobileapp/biz/rpc/ucc/usertoken/HavanaUserTokenReqPB.java:12`）。Facade 标注 `OperationType("alipay.mobileapp.client.havana.usertoken")` 且 `SignCheck`（`jadx15/sources/com/alipay/mobileapp/biz/rpc/ucc/usertoken/HavanaUserTokenFacade.java:6`）。

授权码不足时，`UccAuthUtils` 走 H5/原生授权，再用返回的 `authCode` 重新请求 user token；成功条件是结果非空且 `userToken` 非空（`jadx14/sources/com/alipay/mobile/securitycommon/aliauth/util/UccAuthUtils.java:231`）。

### 会话保存

`SessionModel.SessionData` 保存 `appId`、`domain`、`sessionId`、`createTime`、`validateTime` 和 `diffTime`（`jadx14/sources/com/alipay/mobile/security/x/session/SessionModel.java:17`）。`SessionManager` 以用户派生的 MD5 存储键、`SecurityShareStore` 和安全存储双路读取会话（`jadx14/sources/com/alipay/mobile/security/x/session/SessionManager.java:1`），说明会话是按用户/应用/域隔离的状态，而不是单一全局 bearer token。

## 在线支付/交易网络路径

在线支付 API 主要通过 MTOP/RPC 适配层进入，而不是在业务库中保存完整网络报文：

1. `MtopRequest` 由 `apiName`、`version`、JSON `data`、`needSession` 和 `needEcode` 构成（`jadx20/sources/mtopsdk/mtop/domain/MtopRequest.java:10`）。
2. `AbstractNetworkConverter.convert` 把协议参数放入 GET 查询串或 POST `application/x-www-form-urlencoded;charset=UTF-8` body，并设置 URL、方法和 header（`jadx20/sources/mtopsdk/mtop/protocol/converter/impl/AbstractNetworkConverter.java:181`）。
3. 协议参数构造接口为 `ProtocolParamBuilder.buildParams(MtopContext)`（`ProtocolParamBuilder.java:5-6`）。当前 Jadx 对 `InnerProtocolParamBuilderImpl.buildParams` 仅保留 1376 条指令的 dump（`InnerProtocolParamBuilderImpl.java:96-106`），所以不能把未恢复的签名算法当作已证实。

### 已证实的请求头面

`HttpHeaderConstant` 定义了内容编码、缓存、追踪和认证/设备字段，包括 `content-type`、`content-encoding`、`x-c-traceid`、`x-s-traceid`、`x-appkey`、`x-req-appkey`、`x-devid`、`x-uid`、`x-sid`、`x-sign`、`x-t`、`x-ua`、`x-utdid`、`x-umt`、`x-wuat` 等（`jadx20/sources/mtopsdk/common/util/HttpHeaderConstant.java:6`）。字段名证明了登录、设备、签名、时间和风控上下文会被组装；字段值、端点和签名密钥均未公开。

### 认证与风险关联

- RPC facade 使用 `@SignCheck`，例如 Havana user-token facade；这表明服务端还要求请求级签名检查。
- `DeviceIDManager.getRemoteDeviceID` 将本地 UTDID、原始 IMEI/IMSI、品牌、型号、序列号、Android ID 组成设备请求，并保存返回的 device id（`jadx20/sources/mtopsdk/mtop/deviceid/DeviceIDManager.java:267`）。这是设备身份/风险输入，不是可公开的持久标识。
- `libtnet-4.0.0.so` 导出 `NAL_session_SubmitRequest`，并导入 `socket`/`connect`；TLS 细节由封装库处理。因此可证实“请求经专用网络栈提交”，不能仅凭符号声称具体 HTTP/2 或 TLS 版本。

## 离线支付路径

### 二维码/离线码

`libofflinecode.so` 导出 `get_qrcode_info`、`parse_offline_code`、`verify_qrcode`/`_v2`/`_v3`、`is_duplicate_qrcode`、`gencode_timestamp`、`pos_timestamp`、`get_key_id` 和 code issuer/public-key 管理函数。符号中还出现 SHA-1、SHA-256、ECDSA、SM2、HMAC，说明验证层支持多种摘要/签名选项（`readelf -Ws libofflinecode.so`；字符串证据 `libofflinecode.so`）。

结构结论（已证实）：离线码包含可解析的版本/issuer/key-id/time/载荷字段，并经过版本化签名验证、issuer key 验证和重复码检查。具体的码字节格式、私钥和服务端策略不在静态证据中恢复。

### OTP/授权码

离线凭据初始化 RPC（`jadx7/sources/com/alipay/android/phone/offlinepaycred/j.java:21`）携带：

- `imei`、`imsi`、`tid`；
- `osType`、`osVersion`；
- 扩展参数 `otpAlgorithm=2`。

续期请求携带 `tid`、`seedMD5`、`index`、`algorithm`（同文件 `:61-78`）。这说明离线授权使用设备绑定、种子摘要和索引/算法版本，而不是仅凭二维码明文。

### NFC

NFC token RPC 模型包含 token unique reference、设备指纹、设备信息、公钥指纹和用户/支付应用实例字段（`jadx20/sources/com/iap/android/nfc/acplugin/virtualcard/rpc/model/ApplyNfcTokenRequest.java`、`GetTokenStatusRequest.java`；`jadx20/sources/com/iap/android/nfc/wrapper/implementation/http/ActivateSyncTokens.java`）。`NFCSensorInfoCollector` 的公开字段显示，NFC/支付场景会按配置采集手势与运动传感器序列，详见 [device-risk.md](device-risk.md)。

## 风险评估

- **积极面**：登录密码先做 RSA 变换；授权码和 user-token 分离；会话按 appId/domain 隔离；离线码有多版本验签、issuer key、时间戳和防重；NFC token 有公钥指纹和状态同步。
- **风险面**：SQLCrypto 页 codec 是块级 AES，且当前反汇编未显示逐页 MAC/IV 层；本地明文 `alipayclient.db` 暴露大量账户/登录状态列；设备请求和 NFC 传感器采集面较宽；多个关键密钥来自 Trusted Terminal/运行时保护域，静态提取失败本身是安全边界而不是内容为空。
- **未证实**：`x-sign` 的完整算法、服务端 nonce/replay 策略、TLS 证书固定细节、NFC token 的实际安全单元策略。报告将这些保持为未知。
