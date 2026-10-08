# 逆向证据

## 1. 样本完整性

| 文件 | 大小（字节） | SHA-256 |
| --- | ---: | --- |
| `com.miHoYo.GenshinImpact_7.1.0_48052158_48145775.apkm` | 494,033,581 | `29d51069aec72cb75e363caa713333aba145b379a39583c7de429715a42c5928` |
| `base.apk` | 35,121,130 | `43e1d64940ab767c078330fb5ecb4155a61501a5f20a1828f9306e6f4769583c` |
| `split_config.arm64_v8a.apk` | 153,843,887 | `4c51b596a746c251ca6483616901a20cb872bf2bc8c3b40db34df4f36b4a5bd5` |
| `split_AssetBundles.apk` | 532,442,797 | `41b58d9504c9a497ffefabbb89a7829352758bcb65f57266b671531dba35338a` |

四个 ZIP 均通过 `unzip -t` 完整性校验。签名证书
`META-INF/BNDLTOOL.RSA`（`keytool -printcert`）：

```text
Owner: CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US
SHA256: 68:84:25:30:E0:C7:F9:D4:2B:02:1E:14:8B:EE:24:7F:A7:41:A0:63:BE:39:44:0C:C8:DA:EC:C9:A0:5F:66:9C
SHA256withRSA
```

`assets-split/stamp-cert-sha256` 与上面证书指纹一致（二进制 32 字节）。
包信息：`versionCode 1242`、`versionName 7.1.0_48052158_48145775`、
`minSdk 23`、`targetSdk 36`、`compileSdk 36`。

DEX：

| 文件 | SHA-256 |
| --- | --- |
| `classes.dex` | `c41a99b2328d6b0199aa7f7b7194c45c39355eecbb080135ef2e58ac1047da05` |
| `classes2.dex` | `531f16cb1358f6eda0fad15bfff5b774f33d9603edbf9904fe35a67fba9a904a` |
| `classes3.dex` | `9540e1724a6da2e8fff2331ab54d2924a2894b3ee789b1f3efb77ce66c1a7bbf` |
| `classes4.dex` | `132b67cb5b69b0a7a2f2bb66f4885bfed105f60ffb64d819bc25384b9125dc9e` |
| `classes5.dex` | `7f6d5172a9c452aa0b8aacbead8ac6eb1acd71ec15b2e12812debe507156e4bc` |

## 2. 反编译证据

第一轮 JADX：14,357 个 Java 文件，567 个文件含 838 处
`Method not decompiled`。第二轮 `--show-bad-code --deobf` 后同一输出
规模下残留降到 14 个文件 15 处：

```text
androidx/datastore/core/{DataMigrationInitializer,SimpleActor}.java
androidx/recyclerview/widget/DiffUtil.java
com/appsflyer/internal/AFi1hSDK.java
com/google/android/gms/internal/games_v2/zzie.java
com/google/android/gms/internal/measurement/zzkn.java
com/google/android/gms/measurement/internal/zzgc.java
com/google/crypto/tink/subtle/Base64.java
com/google/zxing/pdf417/decoder/DecodedBitStreamParser.java
com/mihoyoos/sdk/platform/module/login/passport/PassportLoginManager.java
kotlinx/coroutines/flow/{FlowKt__DelayKt,FlowKt__ErrorsKt,FlowKt__DelayKt$timeoutInternal$1}.java
kotlinx/coroutines/selects/WhileSelectKt.java
```

唯一第一方残留是 `PassportLoginManager.java` 中
`authLoginAfterRegister$1$1$6$$ExternalSyntheticLambda0` 的 UI 回调
（`:673`），其相邻 lambda 与 `onSuccess` 路径均可读，不含未知算法。

## 3. 第一方 Retrofit 端点

二次反编译共 39 个第一方注解端点：

```text
GET  /sdk_global/apphub/api/getAttributionReportConfig   LoadConfigService.java:16
POST /data_abtest_api/config/experiment/list             ABTestApi.java:22
GET  /combo/box/api/config/porte-os/kibana_box           BoxConfigApiService.java:13
GET  account/ma-passport/api/getSwitchStatus             FeatureSwitchApiService.java:16
POST account/ma-passport/api/appLoginByAuthTicket        PassportApiService.java:29
POST account/ma-passport/api/appLoginByPassword          PassportApiService.java:33
POST account/ma-passport/api/appLoginByThirdParty        PassportApiService.java:37
POST /account/ma-passport/api/bindThirdPartyBySToken     PassportApiService.java:41
POST account/ma-passport/app/confirmVNWebviewRealname    PassportApiService.java:45
POST account/ma-passport/api/getConfig                   PassportApiService.java:49
POST account/ma-passport/api/reactivateAccount           PassportApiService.java:53
POST account/ma-passport/api/registerByEmail             PassportApiService.java:57
POST /account/ma-passport/api/registerByThirdParty       PassportApiService.java:61
POST account/ma-passport/token/getCrossTokenUrlBySToken  PassportTokenApi.java:21
POST common/aigis/api/checkSmartCaptcha                  RiskVerifyService.java:20
POST common/aigis/api/createBySmartCaptchaTicket         RiskVerifyService.java:24
POST account/ma-passport/api/reactivateAccount           SignInApiService.java:23
POST account/ma-passport/api/appLoginByPassword          SignInApiService.java:27
POST account/ma-passport/api/logout                      SignInApiService.java:31
POST account/ma-verifier/api/createActionTicket          SignUpApiService.java:23
POST account/ma-verifier/api/createEmailCaptchaByActionTicket  SignUpApiService.java:27
POST account/ma-passport/api/registerByEmail             SignUpApiService.java:31
POST account/ma-verifier/api/verifyActionTicket          SignUpApiService.java:35
POST account/ma-passport/api/appLoginByThirdParty        ThirdPartyApiService.java:22
POST account/auth/api/getActionTicketBySToken            TokenApiService.java:23
POST account/ma-passport/token/getByGameToken            TokenApiService.java:27
POST account/ma-passport/token/getBySToken               TokenApiService.java:31
POST account/ma-passport/token/verifyCookieToken         TokenApiService.java:35
POST account/ma-passport/token/verifyLToken              TokenApiService.java:39
POST account/ma-passport/token/verifySToken              TokenApiService.java:43
POST account/ma-verifier/api/createActionTicket          VerifierApi.java:26
POST /account/ma-verifier/api/createAuthTicketBySToken   VerifierApi.java:30
POST account/ma-verifier/api/createEmailCaptchaByActionTicket  VerifierApi.java:34
POST /account/ma-verifier/api/getActionTicketInfo        VerifierApi.java:38
GET  account/ma-verifier/api/age-gate/user/loadTicket    VerifierApi.java:42
POST account/ma-verifier/api/age-gate/user/resendEmail   VerifierApi.java:46
POST account/ma-verifier/api/age-gate/user/updateTicket  VerifierApi.java:50
POST account/ma-verifier/api/verifyActionTicket          VerifierApi.java:54
POST /loginsdk/dataUpload                                ReportApiService.java:14
```

另有 `FingerprintService.java:41,125` 的 `getExtList`/`getFp`（非
Retrofit，走 `NetEngine`），合计 41 个静态端点。

## 4. 关键代码位置

| 主题 | 位置 |
| --- | --- |
| `DS` 签名 | `RequestUtils.java:61-79`（静态盐 `:37` 已脱敏） |
| Cookie 组装 | `RequestUtils.java:154-191` |
| Aigis/verify/年龄门 header | `RequestUtils.java:115-137`、`HeaderKey.java:9-45` |
| 上报签名 | `ReportWorker.java:269-316`、`:349-360` |
| 基础设备字段 | `BaseDataReport.java:111-117` |
| 指纹字段 | `CommonRequiredParams.java:50-448` |
| Root/模拟器/ADB/代理 | `XDeviceUtils.java:156,169,450,476,481` |
| Consent 闸门 | `ConsentStatusCache.java:66-87,123` |
| 导出的状态服务 | `GameStateServiceBinder.java:49-189`、`IGameStateService.java:47-50` |
| OEM 白名单 | `GameInterface.java:74,88,102` |
| 设备上限 | `DeviceLimitManager.java:76-250` |
| GeeTest 版本选择 | `GeeTestUtils.java:64,309-344` |
| JNI 加密 | `com/combosdk/support/base/utils/CryptoUtils.java:10-69`、`com/mihoyo/astrolabe/crypto/CryptoUtils.java:6-69` |

## 5. native 证据

24 个 ARM64 库，关键库 SHA-256：

| 库 | SHA-256 | 作用 |
| --- | --- | --- |
| `libyuanshen.so` | `9d59283b3235ba5afe3f220d952133b9dbad49af6e88da16ae03ff6e3289171e` | IL2CPP 游戏运行时（361,993,016 字节） |
| `libMHYComboCrypto.so` | `fe4e183e782d6a0748500ad5b122e77b734e618396177a04ea4ea34b19946683` | Combo AES/RC4 JNI |
| `libastrolabe-crypto.so` | `3b94d152356918fdb19799bb1e961864683082d48137e33f6123ab5877237ea8` | Astrolabe AES/RC4/SaltSign |
| `libHoYoNetworkSDK.so` | `91e98aefa1099766a99f12c1de3ba2828012d4a9bc42f450ac36a344744043ac` | HoYo 网络 SDK |
| `libgamestateservice.so` | `fc88db4fe71adc27dbe4f9f0a0a80fc7d4b19a8b67e4dc210f3eb2873c392d4a` | 状态服务辅助 |
| `libhpatchz.so` | `fe58bbd525d73f3a3d19974b67c4644f3ebd520b840391ebfd3efa6cfeae71b1` | HDiffPatch 热修 |
| `libAstrolabe.so` | `01831824852edff3de055b5ff21d067edc4dfbcf04b15cab2ae1e37d496a527c` | Astrolabe 采集 |

KCP 导出符号（VA）：

```text
kcp_client_create          0x5d7e9c8
kcp_client_connect         0x5d7ea50
kcp_packet_create          0x5d7ec9c
kcp_client_send_packet     0x5d7ecec
kcp_client_reconnect       0x5d7ec68
kcp_client_network_thread  0x5d7ed4c
```

游戏帧加解密 AArch64 位置：接收路径 `0x5cfe004`、4/12 字节头判定
`0x5d00000`、payload 上限 `0x4000` 检查 `0x5cff3b8`、加密前日志
`0x5cff39c`、tag 日志 `0x5cffbc4`、解密后 payload 检查 `0x5d01eb4`、
AEAD 分派 `0x4a8802c`、发送帧头写入 `0x5cff2bc`。

加密实现符号（`libMHYComboCrypto.so` / `libastrolabe-crypto.so` 同名同构）：

```text
Java_..._CryptoUtils_AESEncryptNative / AESDecryptNative
Java_..._CryptoUtils_RC4EncryptNative / RC4DecryptNative
_ZN12combo_crypto3AES6CipherEPhS1_ / InvCipher / SetKey / KeyExpansion
_ZN12combo_crypto4ARC43ksaEPKh / 4prgaEPKcPci / 6setKeyEPKhi
_ZN12combo_crypto18AESModeOfOperation7EncryptEPhiS1_i / Decrypt
```

## 6. 资源与渲染证据

| 文件 | SHA-256 |
| --- | --- |
| `global-metadata.dat`（80,748,928 字节） | `ba5bc135e453c342a5ee78a1503896e55d6f313afa4edcd26a427e54f4bab74f` |
| `svc_catalog` | `228b6913a4db0eca7296bb952cc5d64472e270baae7451b5dac11ab8cafeeb84` |
| `asb_settings.json` | `70267397e41d027a3052de958ad3ea21532c3f3e5d9f80178e4d1b25f26f9257` |
| `hardware_model_config.json` | `cce0e98b84fceb2a7705c8f50ec492a2b911704f56783cf5aaa1c35e7066dbc8` |
| `vulkan_gpu_list_config.txt` | `0af3fa2f47f5cd265c47ffaa28278a2ed1a0234c26a21ecd49756f773c216539` |

`global-metadata.md5` 内容为 `c51a2afdd9ac59c895077e01cb18dd18`。
Unity 版本常量 `2017.4.30f1` 在 `libyuanshen.so` 中三处出现，另有
`Invalid serialized file version. ... Expected version: 2017.4.30f1`。

## 7. 证据等级

| 等级 | 含义 |
| --- | --- |
| 高 | 字节级可复现：哈希、Manifest、反编译行号、ELF 符号/地址、配置文件原文 |
| 中 | 由多文件交叉推导的机制（如帧结构、管线分层），格式已确定但业务语义未验证 |
| 待服务端 | 阈值、留存、处罚映射、真实字段值；静态不可证明 |

## 8. 设备端只读验证

对一台自有 Android 设备做只读检查（只读、未写入远端、未发起网络请求）：

```text
已安装同公司包名：com.miHoYo.Yuanshen
目标包 com.miHoYo.GenshinImpact：无私有数据目录、无 SQLite 文件
```

因此没有可公开的真实行级 schema，本报告不虚构数据库结构；检查为只读，
未写入远端，未发起任何网络请求。
