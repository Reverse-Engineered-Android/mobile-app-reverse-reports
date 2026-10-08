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
unique "Method not decompiled" signatures: 28
```

ARM32 与 ARM64 DEX 内容一致；重复 ARM32 样本已去除。资源、manifest 和
native 库均从同一 APK 解出。

样本来源：`https://i.meituan.com/client/meituan` 的下载入口与
`https://cube.meituan.com/ipromotion/cube/toc/component/base/getVersionInfo?clientType=MT_ANDROID`
返回的版本元数据（检索日期 2026-10-04），据此确认 `12.66.404` / `1200660404`
为当前官方 Android 版本，再取得对应 ARM64 APK。

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

### 3.1 `libmtguard_log.so` 实际是 DEX

对该文件单独运行 JADX（`--show-bad-code --deobf`，rc=0，45 个类）后得到完整
运行时类：`com.meituan.android.common.mtguard.MainBridge`、`MTGuardEntry`、
`MainCryptoKeyIndex`、`collect/*`（`AccessibilityUtils`、`InstalledAppManager`、
`AppInfoWorker`、传感器采集类）、`wtscore/plugin/sign/core/*`、
`wtscore/plugin/encryption/gmtkby`、`Ok3NetworkInterceptor`、DFP `BaseReporter`、
OAID helper、`com.xiaomi.security.xsof.*` 等。

```java
// MainBridge.java:121
private static native Object[] main(int i, Object[] objArr);

// MainBridge.java:487（main3 委托）
MainBridge.main3(i, objArr)  ->  main(i, objArr)
```

`MainBridge.main2`（`MainBridge.java:140` 起）是 Java 侧命令实现的大 switch，
关键分支的精确行为：

| case | 精确代码 |
|---:|---|
| 6 | `return "6.7.15";` |
| 11 | `AccessibilityUtils.isAccessibilityEnable` |
| 13 / 17 / 18 / 19 | 传感器厂商/名称：`getDefaultSensor(9)` / `getDefaultSensor(1)` |
| 33 | `DevicesIDsHelper.getOAID` |
| 37 | `Ok3NetworkInterceptor.MITM_INFO` |
| 41 | `MTGlibInterface.raptorFakeAPI` |
| 54 | `return ...gmtkby() ? "64" : "32";` |
| 59 | 隐私模式 |
| 61 | `raptorAPI` |
| 69 | `getRunningAppProcesses()` |
| 72 | `MTGuardEntry.internalInit` |

`MainCryptoKeyIndex` 枚举值精确为 `AESKEY("aesKey")`、`COMMONKEY("commonKey")`、
`CONCHKEY("conchKey")`、`WTKEY("wtKey")`、`MAOYANKEY("maoyan_aes_key")`、
`OWLKEY("owl_aes_key")`。`wtscore/plugin/encryption/gmtkby.java:26-39` 的
`gmtkby(byte[], String, int)` 调用
`MainBridge.main3(i == 2 ? 31 : 30, {byte[], String})`（枚举 `czjazaupf = 2`、
`gmtkby = 1`），即 Java 侧只负责选择命令号，真正的加解密在 native `main` 内。

### 3.2 `libmtguard_1/2/3.so` 的封装契约

这三个文件不是 ELF、不是 ZIP、不是 gzip/zlib/raw-deflate：首部是 16 字节
版本头（`1.1.4` / `1.1.8` / `1.1.1` + 零填充），正文严格按 16 字节对齐，
1795 / 1851 / 325 个 16 字节块全部唯一（满熵），全文无 ELF/DEX/gzip/ZIP
magic。

`libmtguard.so` 是一个 ELF64 AArch64 库（Android 21、NDK r16b、stripped，
BuildID sha1 `773d1b811dbcf4af1d2e8ddaa24fd1bb15ed6c66`），唯一导出符号是
`JNI_OnLoad`（`0x40970`）。它内置了一个 ELF 动态加载器：

```text
0x77f38:  bl  0x3cca0 <dlopen@plt>     ; dlopen@plt 全库唯一调用点
0x77f30:  mov w1, #2                   ; RTLD_NOW
```

调用点所在函数从 `0x77c84` 开始，语义为：解析 16 字节表项的版本字符串表，
`calloc(count, 0x90)` 分配句柄数组，逐项校验 `strlen(ptr) <= 0x80`、
`strncpy(dst+8, ptr, 0x7f)`，然后 `dlopen(ptr, RTLD_NOW)` 并保存 handle；
全部成功返回 1，任一项失败返回 0。`JNI_OnLoad` 自身被 OLLVM 控制流平坦化，
但加载器语义可从该函数恢复。

Java 侧配套代码：

- `com.meituan.android.common.mtguard.shell.IIVTQYOSF` 常量：
  `FLWMEVUMVC = "6.7.15"`、`GBANDXHNS = "1.1.4"`、`HSPBHBJI = "1.1.8"`、
  `ZZGP = "1.1.1"`、`BRFI = 6071500`，以及
  `FSGIUFGOU = {"libmtguard_1.so","libmtguard_2.so","libmtguard_3.so","libmtguard_log.so"}`。
- `ShellBridge.java:64-75` 的 `main3` 委托 native `main`（第 71 行
  `return main(i, objArr);`），`main2` case 10 返回
  `{1.1.4, 1.1.8, 1.1.1}`。
- `MTGuard.loadSo`（`MTGuard.java:729-757`）：`tryLoad(...)` →
  `prepareForSo(nativeLibraryDir)` → `ShellBridge.main3(1, {strTryLoad, preload_native_dir})`。
- `MTGuard.prepareForSo`（`MTGuard.java:759-936`）：`nativeLibraryDir` 缺失/为空时
  回退到 `sApplicationInfo.dataDir` 与 `OWPIKWGXA.IIVTQYOSF(str)` 路径助手；
  先经 `ShellBridge.main3(64, ...)`（首进程闸门，`0x820`）打开 APK ZIP
  （`new ZipFile(sourceDir)`），把 `FSGIUFGOU` 的 4 个条目复制到 guard 目录，
  拒绝多 ABI，成功后执行 `OWPIKWGXA.IIVTQYOSF("6.7.15", dataDir)` 并设置
  `preload_native_dir`（`0x921-0x925`）。
- `com.meituan.android.common.utils.mtguard.IIVTQYOSF`：读取
  `files/.0852110f868f8a20` JSON（`main_info` 数组、`4_<ver>_version` 键）；
  `BRFI(String)` 组装路径
  `files/cips/common/cc18a13fb3fd351e/32bb636196f91ed5/m/{32|64}/<ver>.so`，
  把 `MD5(file)` 与 `DNFBGIX(OWPIKWGXA(ver))` 比较；`DNFBGIX`（第 140-159 行）
  用硬编码模数
  `D37E339A...72A6CB` 与指数 `10001` 做 `RSA/ECB/NoPadding`，去掉前导零后
  UTF-8 解码，再与 ZIP comment（`ZipFile.getComment()`）前 32 字符比对，
  `DU()` 回写 JSON。

### 3.3 运行时判定：预加载块在本版本中不被执行

对运行中的美团进程（`/proc/<pid>/maps`）做只读检查，只有
`libmtguard.so` 与 `libmtguard_log.so` 被映射；`libmtguard_1/2/3.so` 从未
作为 ELF 映射。设备上 guard 目标目录
`files/cips/common/cc18a13fb3fd351e/32bb636196f91ed5/m/64` 为空，
`c/64`、`e/64`、`s/64` 亦为空，`files/.0852110f868f8a20` 不存在。

因此结论是：这三个文件是加密预加载载荷（容器格式已完整刻画：16 字节版本头 +
16 字节对齐的不透明正文 + Java 侧 `utils/mtguard/IIVTQYOSF` 完整性/版本校验 +
native `dlopen` 加载器 `0x77ef0-0x77f88`）；在没有解密密钥的前提下无法静态
继续解析其内部，且它们在本次构建中从未被映射执行，不是本 APK 的可执行风控
逻辑。报告不声称已解密该载荷。

已尝试并失败的解密路径（用于界定边界，不代表密钥存在）：单字节 XOR、对
`\x7fELF` / `dex\n035\0` 的 XOR、AES-128/192/256-ECB（密钥取自
{首部、版本补零、md5(版本)、sha1(版本)[:16]、重复版本、全零、0xFF、包名、
签名证书 SHA-256、证书 hex}）、IV=首部的 AES-CBC、同密钥候选的 RC4、
gzip/zlib/raw-deflate。`libmtguard.so` 内也未出现 AES S-box / 逆 S-box /
SM4 S-box / ChaCha 常量，说明该密码学层位于被加密的未解析层或为自定义/
混淆例程。

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

### 5.1 具体报文格式

`com.dianping.nvnetwork.tunnel.tool.SecureTools` 是 NVNetwork 隧道报文的全部
密码学实现：

```text
getProtocolData 外层包（SecureTools.java:347-365）：
  FF 01 00 <flag:1B> <secure:1B> <totalLength:int32 BE> <noSecureLength:uint16 BE>
  || payload || encryptedBlock
  secure 字节：不加密=0；加密=1；加密且 macFlag 且 HTTP_REQUEST=3

encryptedBlock = <prefixLength:int32 BE> || prefix || source
  prefix  -> secureLoad（zip 0=raw，1/2=gzip）
  source  -> rsp（zip 0/1=raw，2=gzip）

parseData(int zip, byte[])（SecureTools.java:398-441）：
  要求 >= 4 字节；前 4 字节为大端 prefix 长度；余下按 zip 模式解压。
parseData(byte[])（SecureTools.java:444-475）：旧格式，在首个 NUL 处切分。

compress(byte[])（SecureTools.java:100）：
  ByteArrayOutputStream(16384) + GZIPOutputStream，写满后 toByteArray()。

HMAC（SecureTools.java:571-596）：
  jSONObject.put("z", secureProtocolData.zip);
  Mac.init(new SecretKeySpec(keyBytes, "hmacSHA256"));
  jSONObject.put("h", Base64.encodeToString(mac.doFinal(...), 2));
  密钥字节 = encriptData.f12893b + secureProtocolData.id
```

对称与非对称辅助：

- `tool/c.java`：`SecretKeyFactory("DES")` + `DESKeySpec` + `Cipher("DES")`；
  第 26 行 `init(2, key)` 为解密，第 38 行 `init(1, key)` 为加密。
- `tool/f.java`：`KeyFactory("RSA")` + `X509EncodedKeySpec` +
  `Signature("SHA1WithRSA")`，第 30 行 `signature.verify(Base64 解码后的签名)`。
- `SecureTools.getRSAKeys()`（第 368 行起）返回 RSA 密钥材料，失败抛
  `"Get RSA Error"`。

`MTGuard` 的 native 包装与之一致：`decrypt` → `main3(31, {byte[], keyIndex.value})`、
`encrypt` → `main3(30, ...)`、`encLoad` → `33`、`encStore` → `32`；
`decryptAES`/`encryptAES` 走命令 31/30 并把 byte 密钥 `new String(bArr2)` 传入。
`CryptoKeyIndex`（主 sources）与 `MainCryptoKeyIndex`（log DEX）枚举完全相同。

### 5.2 28 个残留方法的归类

`Method not decompiled` 共 26 个文件、28 个唯一签名。逐一用
`jadx --single-class --comments-level debug` 或源码上下文核对后，归类如下
（`jadx --single-class` 以 rc=3 结束但仍输出完整 debug dump，属预期）：

| 残留方法 | 归类 | 判定依据 |
|---|---|---|
| `com.meituan.cronet.okhttp.a.intercept` | 网络传输 | Cronet/OkHttp 传输切换 + `SseRequestOptions` + 上报；使用 `com.meituan.cronet.config.d.*`、`com.sankuai.meituan.common.net.request.c.*`、`com.meituan.cronet.report.d.*`，最终 `chain.proceed`；无密码学 |
| `com.sankuai.common.utils.b.a(String)` | 编码 | UTF-8 → 字母表 `g(int)` Base64 变体；结果以 gzip magic `0x8b1f` 开头时用 `GZIPInputStream` 解压；无密码学 |
| `com.dianping.nvnetwork.tunnel.tool.SecureTools.compress` | 压缩 | 见 §5.1，GZIP 封装 |
| `com.alipay.sdk.m.l0.b.b(byte[])` | 编码 | Base64 解码（上下文含 `"bad base-64"`） |
| `com.alipay.sdk.m.a0.b.D(Context)` | 设备元数据 | `Build.SERIAL`、`privacy.aop.f.i()`；`E()` 列 `/dev/qemu_pipe` 等模拟器路径 |
| `com.meituan.passport.utils.t0.l(...)` | 埋点 | 分析事件分发（`com.meituan.passport.utils.u0.a/b`，键 `c_hvcwz3nv`、`b_fui1o3ib`、`Locate.once`、`locate_token`、`pt-a3555ae11c727a6b`）；无密码学 |
| `com.meituan.android.pt.homepage.modules.guessyoulike.request.j.d(String,String)` | 字符串映射 | 纯映射（`"first"/"second"/"default"` + 点击过滤/返回/区域/地址/位置串）；无密码学 |
| `com.meituan.android.train.deviceinfo.a.a/e` | 设备元数据 | `MessageDigest` `SHA-1`（第 1044、1846-1848 行）、`SHA-256`（1614-1616）、`Base64.encodeToString(...,2)`（1490、1828）、`GZIPOutputStream`（1815-1823）；仅摘要/Base64/gzip |
| `com.meituan.android.bike.framework.foundation.network.utils.a.f(Context)` | 运营商映射 | MCC/MNC 表（460 → 中国移动/联通/电信）；无密码学 |
| `com.meituan.android.common.unionid.oneid.util.AppUtil.getHarmonyDeviceType/getHarmonyEmuiVersion` | 设备元数据 | Harmony 机型 / `Runtime.exec` 读 EMUI 版本 |
| `com.meituan.passport.standard.utils.j.a(boolean,String)` | 登录埋点 | passport 标准组件事件 |
| `com.meituan.msc.performance.f.a(...)` | 性能埋点 | MSC 性能上报 |
| `com.meituan.android.food.retrofit.base.i.convert(Object)` | 反序列化 | Retrofit converter |
| `com.meituan.android.movie.tradebase.pay.view2.d1.a.onCompleted` | UI 回调 | 支付页动画回调 |
| `com.meituan.android.multilingual.impl.e.getIconResourceId` | 资源 | 多语言图标资源 id |
| `com.sankuai.waimai.order.mach.h.K` | UI | 外卖订单状态机 |
| `com.sankuai.waimai.platform.widget.weather.j.E(int)` | 动画 | 天气组件动画 |
| `com.sankuai.meituan.mtliveqos.utils.cpu.b.b()` | CPU | 直播 QoS CPU 采样 |
| `com.sankuai.meituan.model.c.onUpgrade(...)` | 数据库 | SQLite `onUpgrade` |
| `com.sankuai.xm.base.util.ExifInterface.f(...)` / `com.xiaomi.exif.i.a(...)` | EXIF | 图片 EXIF 解析 |
| `org.commonmark.internal.o.a.a(...)` | 解析 | CommonMark 块解析 |
| `a.a.a.a.b.handleMessage(Message)` | 框架 | 消息处理 |
| `com.huawei.hms.aaid.init.a.run` | 推送 | HMS 推送初始化 |
| `com.meituan.android.mgc.api.minorGuide.c.run` | 引导 | MGC 引导 |
| `com.meituan.android.common.statistics.channel.j.l0(...)` | 统计 | 埋点通道 |

结论：28 个残留中没有未解释的密码学实现；安全相关的全部可读方法已归入
§5 与 [risk.md](risk.md) §2。

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

## 8. 设备端只读数据库验证

在自有 Android 16 设备（Redmi `23117RK66C`）上以只读方式检查运行中应用的真实
落盘格式。SSH 命名空间与 init 不同，因此统一经应用私有数据目录访问；
应用进程 PID 34151。
设备无 `sqlite3` 二进制，改用 `/usr/bin/python3` 的 `sqlite3` 模块，先把文件
复制到 `/tmp` 再读（直接以只读方式打开 `-wal`/`-journal` 相邻路径会失败）。

### 8.1 数据库真实 schema

| 文件 | 表 | 列 | 行数 |
|---|---|---:|---:|
| `databases/com.sankuai.meituanMTLocationDb.db` | `MTLocationTableV2` | `_id, WIFI, CELL, LOC, TIME, GEOHASH, WIFI_TYPE, LOCATION_TAG` | 34 |
| `databases/kitefly.db` | `log` | `id, uploaded, log, tags, type, category, ts, status, token, _value, env, details, raw, is_main_thread, loguuid, thread_id, thread_name, inner_property` | 6 |
| `databases/mt-statistics-db-cache` | `event` | `autokey, channel, environment, evs, level, ctm, pfcount` | 1 |
| `databases/request_monitor.db` | `jakarta_requests` | `id, request_id, host, path, request_start_time, request_type, request_size, extra_type, request_result, cause, message, response_size, time_cost` | 0 |
| `databases/hades_db_sql` | `hades_biz_sql` | `id, modelName, eventType, eventTime, channel, source, resourceId, wifiName, network, cityId, custom, saveTime, custom_json, sessionId` | 0 |
| `databases/battery.db` | `battery` | `processName, businessName, date, bgLongActivityProcessTime, bgSleepProcessTime, bgFreezeProcessTime`（主键 `date,businessName,processName`） | — |
| `databases/1857661084_message_db.db`（667 KB，43 对象） | `msg_info, grp_msg_info, pub_msg_info, msg_sync_read, chat_stamp, session, receipt_info, msg_pub_opposite, msg_group_opposite, addition` | `addition` 含 `recvs BLOB` | — |
| `databases/imkit_db.db` | `vcard` | `avatar_url, big_avatar_url, name, info_id, type, in_group, status, extension, uts, description, …` | — |
| `databases/dx_sdk_statistics_report.db` | `chain_trace, statistics_report` | — | — |

### 8.2 定位库：位置数据不是明文

`MTLocationTableV2.LOC` 是 Base64 外观的不透明串（样例长度 2764，解码后
2073 字节），并非可直接阅读的坐标；`TIME` 为 epoch 毫秒字符串（如
`1791036406473`），`GEOHASH` 为 `webwrumq` 形式的明文 geohash。样例行的
`WIFI`、`CELL` 为空。因此“位置库保存明文经纬度”这一说法不成立。

### 8.3 统计与日志库：明文 JSON，含稳定标识

`kitefly.db.log` 的 `token` 与 `env` 是明文；`env` JSON 含
`babelUserId`、`babelid`（64 位 hex，DPID 形制）、`deviceType`、`mccmnc`、
`networkType`、`sdkVersion`、`buildVersion`、`app`、`appVersion`。
`mt-statistics-db-cache.event.evs` 也是明文 JSON，键含 `dpid`、`uuid`、
`oaid`、`android_id`、`mac`、`bssid`、`union_id`、`micro_msid`、`app_session`、
`locate_city_id`、`cityid`、`district_id`、`pushid`、`msid`、`mk_trackid`、
`ad_tracking_enabled`、`ch`、`logintype`、`svs`；`channel = data_sdk_group`。
报告只保留字段名与截断样例，不落盘完整标识值。

### 8.4 隐私与风控运行态文件

- `files/cips/common/privacy_config/kv` 为二进制 KV（非 SQLite），含
  `is_privacy_mode`、`current_config` 及
  `/data/user/0/com.sankuai.meituan/files/cips/common/privacy_config/assets/…conf` 路径；
  `assets/*.conf` 是二进制同意记录，标签含 `Android-mtguard`、
  `pt-a3555ae11c727a6b`、`Phone.read`、`BlueTooth.admin`、`Locate.once`、
  `Microphone`、`Pasteboard`、`locate_token` 等。
- `files/horn/` 共 690 个文件，其中 20 个 `final_horn_config_mtguard-*` 配置
  （env、env-blk、blk、settings、siua、bio、bio-field、fama、rom-check、xid、
  vmp-funcs、switch、background、raptor-v6、enc-salt、readlink、files-stat、
  report-funcs、app-path、sig-ignore）。这些是 Horn 缓存二进制记录，内嵌明文
  查询串可见：
  `deviceType=23117RK66C&appVersion=12.66.404&osVersion=16&is64=true&sdkVersion=0.4.17&packageName=com.sankuai.meituan&id=…&version=v1&token=…&MtGuardVersionCode=6071500&PhoneManufacturer=XIAOMI&SoVCode=6.7.15`，
  响应为 `{"data":"<base64>","data_sig":"<base64>"}`。
- `files/.mtg_sequence` = `{"sequence":"11492"}`；`files/.mtg_process_file_lock`
  0 字节；`files/mtg_mts_log` 0 字节；
  `files/._mtg_mtdfp_up/.mini/` 含 `hornCache`（47487 B）、`eman_ppa`（428 B，
  Base64 不透明）、`mtg_dfp_gzcf.txt`（3328 B）、`mtg_dfp_gzcf_f.txt` = `[]`。
- `files/.hodor/media_v3_scope/*.scp` 为 1 MiB 分块（54 个），
  `media_v3_content/*.ctt` 47 B。
- `app_turingdfp/1/.turing.dat` 不透明；
  `app_turingfd/mpdc_105498_1`、`mpdc_r_105498_1`（32 B）、
  `12/105498_au_2`（1860 B）。
- `shared_prefs/com.sankuai.meituan_preferences.xml` 含
  `ms_dns_cache_ips_sdk_1rtb_net`、`android_id`、`versionCode=1200660404`。

### 8.5 与预加载块的交叉验证

应用 native 库目录（经设备私有数据目录下的 `lib/arm64`）
含 67 个库、其中 5 个 mtguard（`libmtguard.so`、`_1`、`_2`、`_3`、
`libmtguard_log.so`）；运行进程 maps 只映射前两者（§3.3）。
`MtGuardVersionCode=6071500` 与 `IIVTQYOSF.BRFI = 6071500` 一致，
`SoVCode=6.7.15` 与 `FLWMEVUMVC = "6.7.15"` 一致。
