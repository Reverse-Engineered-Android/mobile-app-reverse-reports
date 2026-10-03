# 逆向证据

## 1. 样本完整性

```text
file: meituan-12.66.404-arm64.apk
bytes: 91327888
sha256: 06df08431653502ef9988b7f951dfcfdcdc0758eccbb54aa0fe1cda4e423e8ef
zip integrity: passed
package: com.sankuai.meituan
versionCode: 1200660404
versionName: 12.66.404
DEX source files after JADX: 70519
JADX --show-bad-code errors: 50
files containing "Method not decompiled": 26
```

ARM32 与 ARM64 DEX 内容一致；重复 ARM32 样本已去除。资源、manifest 和
native 库均从同一 APK 解出。

## 2. 反编译证据

| 证据 | 位置/方法 | 用途 |
|---|---|---|
| 命令表 | `com.meituan.android.common.mtguard.MTGuard` | 风控命令 10/11/15-24/31/101/103/105/110/120 |
| 行为聚合 | `yoda.model.behavior.collection.b:138-163` | 12 个风险布尔 + 时间序列 |
| 签名拦截 | `sign.interceptors.CommonCandyInterceptor:29-57` | `mtgsig` 分流 |
| POST 缓存 | `sign.interceptors.Ok3CandyInterceptor` | body -> native signer |
| 证书 pin | `sign.interceptors.Ok3NetworkInterceptor:23-99` | 14 pin、event 50/code 303 |
| 旧签名 | `gmtkby` dump | 16,200 body cap、command 2 |
| 登录 | `com.meituan.passport.api.AccountApi` | 登录/刷新/PKCE |
| 支付 | `com.meituan.android.pay.retrofit.PayRequestService` | 支付签名/实名 |
| 周边 | `MapUnityAPI:45-91` | POI nearby/detail/comments |
| 搜索 | `SearchRetrofitService:41-99` | 餐厅/项目查询 |
| 上传 | `DefaultUploadFileRetrofitService` | 对象存储 |
| 隐私 | `ShowPrivacyDialogHandler:53-85` | 同意闸门 |

## 3. 风控 native 证据

`libmtguard.so`：

```text
ELF64 AArch64, Android 21, NDK r16b, stripped
size: 2.4 MiB
BuildID sha1: 773d1b811dbcf4af1d2e8ddaa24fd1bb15ed6c66
sha256: 9a714157f288d772f1dd58e16ce72da10984a7fb6a74ae6ce7a65583ac931eba
current exact strings:
  /system/bin/su
  /system/xbin/su
  de.robv.android.xposed.installer
```

预加载封装：

| 文件 | 大小 | 首部版本/类型 |
|---|---:|---|
| `libmtguard_1.so` | 28,736 | `1.1.4`，非 ELF |
| `libmtguard_2.so` | 29,632 | `1.1.8`，非 ELF |
| `libmtguard_3.so` | 5,216 | `1.1.1`，非 ELF |
| `libmtguard_log.so` | 189,160 | Dalvik DEX，非 ELF |

`com.meituan.android.common.mtguard.shell.IIVTQYOSF` 在初始化时复制/预加载
这四个文件；因此它们不是误命名的普通 shared object。

与 12.35.236 的 62 库比较：

| 状态 | 数量 |
|---|---:|
| 相同 | 10 |
| 改变 | 34 |
| 新增 | 23 |
| 旧版独有 | 18 |

## 4. 关键代码片段

### 4.1 Root/环境命令

```java
public static boolean isRoot() {
    return (Boolean) ShellBridge.main3(15, new Object[0])[0];
}
public static boolean isEmu() {
    return (Boolean) ShellBridge.main3(16, new Object[0])[0];
}
public static boolean isHook() {
    return (Boolean) ShellBridge.main3(20, new Object[0])[0];
}
```

### 4.2 签名

```java
// CommonCandyInterceptor
IIVTQYOSF.IIVTQYOSF(context, uri, body, contentType,
                    headers, method, out, extra);
return out.get("mtgsig");

// IIVTQYOSF 实际核心
ShellBridge.main3(120, new Object[]{ candyBaseMaterial });
```

### 4.3 pin 失败

```java
MainBridge.main3(50, new Object[]{303});
if (gmtkby.jefswxstkc.booleanValue() && NVGlobal.isInit()) {
    chain.call().cancel();
    throw new IOException("Canceled");
}
```

## 5. 加密用途闭包

| 调用/组件 | 输入 | 可见算法/边界 | 输出/用途 |
|---|---|---|---|
| `MTGuard.decrypt` -> command 31 | byte[] + `CryptoKeyIndex` | AES-key index 解密接口 | byte[]，用于打包数据 |
| `decryptAES(byte[], byte[], String)` | cipher + byte key + String mode | AES native path | byte[] |
| `CryptoKeyIndex` | `aesKey, commonKey, conchKey, wtKey, maoyan_aes_key, owl_aes_key` | 6 个命名密钥索引 | 选择 native key |
| `MainCryptoKeyIndex` | native key names | 主密钥索引 | 命令 31 |
| command 120 | URI/method/body/header | native request signature | `mtgsig` |
| command 2 | sorted input + host | native legacy signature | `mtgsig` |
| `SecureTools.a` | HMAC key bytes | `Mac.getInstance("hmacSHA256")` | digest |
| `SecureTools.createCompressedData` | protocol payload | GZIP + HMAC-SHA256 + AES/RSA helper | socket packet |
| `SecureTools.parseData` | packet bytes | version/model + optional gzip | `secureLoad` + rsp |
| payment `@Encrypt` | form values | 支付加密 annotation | encoded field |
| `libPayRequestCrypt.so` | payment request | native 支付请求密码学 | encrypted request |
| `libentryexpro.so` | UnionPay protocol | signature/encryption | 银联报文 |
| `libCGCipherSDK.so` | SM2/SM3/SM4 | 国密算法库 | 支付/证书报文 |
| pin check | public key DER | SHA-256 + Base64 | pin match |

这些条目覆盖了报告中出现的全部“加密/签名”代码。密钥索引是选择器，不是密钥
值；本报告不提取、打印或伪造任何生产密钥。native command 120/2/31 的输出
用途和输入由 Java 调用点界定，不把控制流内部的未解混淆指令当作新的加密算法。

## 6. 证据等级

- **A**：DEX 可读方法、manifest/XML、Retrofit 注解、SHA-256；
- **B**：native 字符串/导入/ELF 结构、跨版本 hash 对比；
- **C**：12.35 反编译表与 12.66 字符串同时命中的路径；
- **未知**：服务端评分、数据留存、真实接口响应、私钥/会话有效期。

任何需要 A 级证据的结论在报告中给出类、方法或路径；B/C 级只用于 native
环境信号和跨版本结构，不用于声称服务端行为。
## 7. 当前 67 个 ARM64 native 库

以下 SHA-256 来自同一 APK 的 `lib/arm64-v8a` 解包目录，文件名与 hash 一一对应：

| 文件 | SHA-256 |
|---|---|
| `libCGCipherSDK.so` | `a1d1769379c9108fbd8e257901d11b4840b60a4bf37e5d31e502ac22719ba9ce` |
| `libCtaApiLib.so` | `b6533c02808f1ed20393ba5f3db01a7771f3b0dfba00f2eb8f113bef579c76e7` |
| `libMBarScannerV2.so` | `5b20a210c35707a00376b7da1a90062a061af695e3767404718c80c6c7e5fd32` |
| `libMNN.so` | `0f9d567f7830f908010f1c57d736686406ac9e328ae5be1c62cc3961cb6c5b9f` |
| `libPayRequestCrypt.so` | `6d5ca2a0d08c1d483dfb8d0b8019a662c64ad2de7d61d554190cb0222d741b8f` |
| `libaemonplayer.so` | `1c9c77b7ab4fe64854bc9de6ee9b1e495c446231e63e954d1c71bfb05c6684c4` |
| `libanimated-webp.so` | `c2188606181234f6a4da51f92c6752ec4fc9c0ab352f79864b12ef26736f858f` |
| `libappmodules_newarch.so` | `9be29c9533b3f67283f48f17f7341590503a4b0558fa1557ea36e794b99e4e2f` |
| `libbsdiff-gzip.so` | `f7ba30243fa94c48e73093ea67ec5ccb5239386bd6be7fd41bf2285fe494eaf5` |
| `libc++_shared.so` | `a6a1e9fc72defe3bb106f1c361845b9d5b8cb24971445e621015b90fb0ae43cc` |
| `libcips.so` | `10821979b018c1a7a71e532e63c6b08ec89092a956e710fd340b65705a4dada2` |
| `libcore_c.so` | `eb7956f46463534d33e1b0beeeadba59a93af838ca41e9c68a6b1c988fe369db` |
| `libcronet.90.0.4402.0.so` | `ba987d5092288f99804ad266db83aaaf7497a7c0f2fd753448201b626a038eb0` |
| `libdpobj.so` | `0a7e50d105c52f8d77adc06d3e6eb4f02e0e73042720465b00ec92e76b07bf92` |
| `libentryexpro.so` | `78c62496188851cbc3bd15ad6fe0f607b7f9916abee25b30b007a2a66542b54d` |
| `libfb.so` | `897cff372987b9b7c9b294e3fcd46dcde7fdd929275f389818a1428af7dc0ed5` |
| `libfbjni_newarch.so` | `c1ffc3a4147477475352754f426ad5f011968971d0a3c6e608f4013080515830` |
| `libffmpeg.so` | `7db0ec47a0a8f1fa00e594fe6ee04968d89fe5672e640b37c729fb1ea9848731` |
| `libfolly_json.so` | `b6f03a106215f3cdc0e7b2476b6da16fe0bb848015aa120a43c502a476b04dee` |
| `libfuse_tts_mp3.so` | `222152148b3bb2c1e56adb2e53e19b33afefdaa8cf546bf945798d6d7ae86fbc` |
| `libglog.so` | `cd635cd8b61fa19e4e23117f8eed8e683bafe8cbae95f4acf264d00baa9c4dc8` |
| `libglog_init.so` | `d22a5424aa104d59829a601f830319382b896b9c2660b276918637456f3506c1` |
| `libjscexecutor.so` | `178bc0fb6bb533ab4f0eb474c13e5a78482eba290e404a4fc9d1d8278f0ec368` |
| `libjse.so` | `60df97c941e4e20e4ef3b192ffada859125df0806d03cb81dce0e3b24f6d5a17` |
| `libjsi.so` | `c772efaa47d7837516ac46313ea474a151ee097fca03b0e61608440a98c188e5` |
| `libjsi_newarch.so` | `be441f57199f4d55020cc1396c2486e2a821f76a4c365f39081bd10e034038d8` |
| `liblocation_engine.so` | `45c2f22f63717df919366e1a70da8f8db7da1496262c481188834690cdd4c3d4` |
| `liblogan.so` | `6f6ef9d95ff8715e26f5c5d434902db16529f8a741e1e1626bb498394429c7ef` |
| `libmach-pro.so` | `2f5c75fe7cd40ac6d507d5519b09f45dcc5ae5c0a48ef499b917765adb96c796` |
| `libmet_defender.so` | `aed9d4d0fa22144f94408dd23c691b95fa3a5f909dfd2a1116d29ee686c12c9e` |
| `libmrncomponentsapis.so` | `05c1af013eb90d2699421b426fbf5db61f5b52f3dd5b3d26b4612a53e5d46996` |
| `libmrnmodule-jni.so` | `ee65b4b5e8453a16babf8953e85b0fa4c66fb04b52990b27ba80759ce82ff419` |
| `libmsc.so` | `7bb309389f866746948c417a9cfcb78ae0f7a50e7c1003caca2fc8617ddec3a9` |
| `libmsc_jsi.so` | `ca77d811679865abd1be978ec804704f63108884c9301ca3d01f54a0521db569` |
| `libmsccsslib.so` | `ef5b201d118325ee8b52cae0904c5395fe3db52b9471c15f7be2e1a6b0591fba` |
| `libmscexecutor.so` | `64aee4992a322953bae3f1da3852cb2baec55b23bc09bd554ae881345810cc3c` |
| `libmscjni.so` | `49d23fd7dc1e2e6110d0c2ff9337737b229ceea1529b7f46f6b2db54216e8695` |
| `libmscrenderer.so` | `5041a37dc3f0f65de1df8013e9897d2a869add5b9ceca6dede2bf3dba0af3818` |
| `libmsi-jsi-lib.so` | `02c4b04cb019066e794d4bd6dffb3aef2b1e37e32911ff5b4c4c77be9541034e` |
| `libmtcronet.so` | `b8e04f804fc8e029968efb9ac4830cec296a784b18d79c4ac48a57a02b68c423` |
| `libmtguard.so` | `9a714157f288d772f1dd58e16ce72da10984a7fb6a74ae6ce7a65583ac931eba` |
| `libmtguard_1.so` | `9e2ddd790624d38e12e371de898f3e954e07a0a64c59f86cbea47b29473575ff` |
| `libmtguard_2.so` | `5c88872a9396c5207b7ca09dd5f80f4c690f0bcbd35a292e5f66bf3b6a4c26db` |
| `libmtguard_3.so` | `05b0236d06b51e719cf5012ed2d7eea7240c256b27041c84d2462918e06234cf` |
| `libmtguard_log.so` | `a9fc4595fec89edb6e6265725cfc4757c9c7a35cfbaca21382f31780dd1203fc` |
| `libmtmap-combine.so` | `0976f7b11724007427842165a9249cb7e42a744d314c14b34806111b739cb6e7` |
| `libmtnncore.so` | `cb570ceba4052616869b60f99e8c719baf645c32707e2275245e3e7d4eabea10` |
| `libmtquickjsi.so` | `fb49a9d3f38f0845bb878b4315d3e4b8c3605b917b5a04e5a6bbd0e3851c9198` |
| `libmtv8.so` | `cbac08fdaaaca1f74ee0a17ec64ae1624183690e1badaee65edfeddb91dcc57b` |
| `libmtv8jsi.so` | `0f849aa1cafae3725bc87a03822b5d9b899be996f7b9167b9f7b9b5546a91d42` |
| `libmtwebview_plat_support.so` | `f3aa65ef8a31952afb23b6891ba8756b63747de7380d88922901b31cdd2d5ad4` |
| `libnh.so` | `8161d548a26b231eabf6c998a521bde8263ce9a00d70663c25d31673b328502c` |
| `libqrcode-engine.so` | `1a9e39b15585f5bcf551fb90c4322e957e2e8ea50317dc67a2ff85c61064b693` |
| `libquickjs-executor-release.so` | `6292cba17e3d8f73669f5819d58fec7073048cdf6d87b06532a8df2067a2a03c` |
| `libquickjstooling_newarch.so` | `2d349ab3d615e4d0bcc56366de66b031d940eb702634a246702c8a3ed93d544a` |
| `libreactnative.so` | `d6b7222599538e1a85a2a8fbcfa875a0a6259b8dd70580e6d02a2a733ff40a5a` |
| `libreactnative_newarch.so` | `38e3e14a7a9eeed62973e28437f9d24224148e60930e1ed06e9776f0b279bba5` |
| `libreactnativeblob.so` | `c5d25e6a302987cbbcc6a639f5b60e05f6b8551c242c6ef0d04a18d4a98a6653` |
| `libreactnativejni.so` | `8919e21ca2a644be1374caaa88d34ddb8844a841e8a77190ddbe54c0b37a37f6` |
| `libsnare_2.0.0.so` | `96994bf97719481959a4e82de560ccb45b9598081d5811358f38ff72aa7456ea` |
| `libunionid.so` | `85f3a298c4c2c79bf4f6ac1764f4fbdc6f925a93b7f998e3aba03ecc3c4e6548` |
| `libv8.mt.so` | `10047321fac80e476d6e9078397b1a2a9dd4ec4108ed426f73a26b282a85668c` |
| `libv8jse.so` | `9643ba6d88248441270009e0c5dc069a4dacb6b5b1234fa27444dc2dce1659e2` |
| `libwasai_platform.so` | `c92e0000f1bf4b8e68926bd76a6cda714a4eaddaf0ea336ddc21893ab8e6add6` |
| `libyoga.so` | `11ebdd2eec98318c4c65e67a3b84d147200fe653cfc3ab2420535ba363684746` |
| `libyoga_newarch.so` | `220d714b5978179336441d3029c0b242df2e7cc334e5c9c8d367e227f7e08be3` |
| `libyoga_recce.so` | `be331a0170b278b39985c12a4b7f508ded4cf2241c6e1c4cb6a152131e217fb0` |
