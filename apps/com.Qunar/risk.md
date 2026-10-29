# 风控机制

## 1. 结构总览

风控分三层，彼此的失败域互不重叠：

| 层 | 载体 | 作用点 |
|---|---|---|
| 传输信封 | `libgoblin_6_1_1.so`（`Goblin.sand` / `Goblin.drift`） | 每次 Hotdog 请求/响应 |
| 请求指纹 | `HotdogConductor.cpHasPrivacyInfo` + DataPip 开关 | cparam 与 `b` 参数 |
| 独立 SDK | `com.qunar.rc`（`Sepa`/`RC`）、`com.ctripfinance.risk.device` | 独立上报通道 |

## 2. 请求指纹（cparam）

字段表见 `network.md` §5。风控相关的关键点：

1. **`hk` 反 Hook 位掩码**：`isHook()` 遍历当前线程栈，命中
   `ZygoteInit`（第二次出现）计 1、`com.saurik.substrate.MS$2.invoked` 计 2、
   `XposedBridge.main` 计 4、`XposedBridge.handleHookedMethod` 计 8。
   这是纯栈文本判定，不做字节比对。
2. **`catom` / `cas` 调用方归因**：`getAtomInfo()` 逆向扫描调用栈，取第一个
   `com.mqunar` 类的原子模块包名，再查 `AtomInfoWrap.getAtomInfos()`
   得到模块名与集合；解析失败写入 `atomerror1_0` 之类标记。
3. **`ke` / `ref`**：`ke` 是毫秒时间戳，`ref` 是页面名或宿主
   Activity/Fragment 全限定类名（缺省 `unknown#unknown`），用于把请求归到一个
   具体的页面实例。
4. **`brush`**：固定 `{"lt":"0"}`，语义为“刷单/刷票相关标记位”，取值 0 表示正常。
5. **隐私开关**：`cpHasPrivacyInfo(ctx, false)` 在隐私未授权时把
   `ma/adid/mno/uid/initUid` 替换为 `*Default` 字面量，但 `gid/sid/pid/cid`
   仍然真实上报 —— 即**降级只覆盖部分字段，不覆盖设备与会话标识**。
6. **`fp` 提升为头**：`hotdogParamToHeaderSwitch` 打开时
   `C_PARAM_TO_HEADER_KEY_LIST`（当前仅 `fp`）从 JSON 移除并写入请求头，
   非 ASCII 值被丢弃。

## 3. `libgoblin_6_1_1.so`

### 3.1 结构与符号

| 项目 | 值 |
|---|---|
| SHA-256 | `ceef53a8bc8f893503b8e494775104a7b56ff81975601e4a0e064a2fd7ec2b4a` |
| 大小 | `2,156,224` 字节 |
| 导出函数 | `DriftsandEn` `0xa744`、`DriftsandDe` `0xa9fc`、`EnBufferLHint` `0xa714`、`DeBufferLHint` `0xa72c`、`Ena1907_req` `0x125f58`、`Dea1907_resp` `0xacd8`、`JNI_OnLoad` `0xe7c`、`en` `0xd80`、`register_handler` `0xcac` |
| `.rodata` | `0x20c240`，`0xb6c` 字节 |
| 字符串编码 | `.rodata` 全部为 **ASCII 十六进制串按字节 `XOR 0xFF`**；解码后得到 JNI 类名/方法名与 `hehe : <行号>` 标记 |

解码后的字符串族（节选）：`com/mqunar/core/basectx/application/QApplication`、
`getContext`、`getPackageName`、`getPackageManager`、`getPackageInfo`、
`signatures`、`java/security/MessageDigest`、`SHA1`、`digest`、
`qunar/lego/utils/Goblin`，以及 `getCrc32 / ve / ePoll / dPoll / dn1 / ea / da /
espirt / roller / eg / duch / SHR / getPayKey / version / drift / sand / dn`。
`hehe : NNN` 形式的标记是该库自身的行号/阶段埋点。

### 3.2 `JNI_OnLoad`（`0xe7c`）

1. 注册 `SIGSEGV`（信号 11）处理函数 `0xd1c`：把 `1` 写入故障 fd 后 `close`，
   即崩溃时静默退出而非抛栈。
2. 取 `JNIEnv->FindClass("qunar/lego/utils/Goblin")`，随后
   `JNIEnv->RegisterNatives(..., 18)`（`[x8, #0x6b8]`），方法名与签名来自
   `.rodata` 中的解码串表。
3. 加载后做完整性自检：对照两张大端/小端混合的 32 位表
   `.rodata 0x20cc94`（29 词）与 `.rodata 0x20cd08`（41 词），
   不一致则返回 `0x10006` 而非正常版本号。

29 词表首 8 项：`c9d00381 ea1f0784 c412e189 377bb38b 115c7510 7e873091
4094a513 0bd52214`；41 词表首 8 项：`fb1f9882 e88e9183 49f08f87 ad266488
390a1e8c 6800400e ede4a88f 478c6f13`。两表均无重复项（29/41 个 distinct）。

### 3.3 信封布局（已验证）

`DriftsandEn`（`0xa744`）签名
`int DriftsandEn(int cap, char *out, int inLen, char *in, int *outLen, char **outPtr)`：

| 偏移 | 长度 | 内容 | 证据 |
|---|---|---|---|
| `0x00` | 6 | `05 61 31 39 30 37` | `strb w9,[x21]`（5）+ `stur w11,[x21,#1]` = `0x31613739...`，`strb w10,[x21,#5]`（`0x37`） |
| `0x06` | 8 | `BE64(adler32(in, 1))` | `adler32(0,0,0)` 后 `adler32(x,in,inLen)`，再按 `lsr 56/48/40/32/24/16/8/0` 逐字节写入 `x21+6..+13` |
| `0x0e` | 8 | `BE64(adler32(inner, 1))` | 第二次 `adler32` 作用于 `x24 = x21+0x16` 起的内部块 |
| `0x16` | n | 内部块 | `Ena1907_req(cap-0x16, x21+0x16, T29[usec%29], T41[usec%41], 4, ke_ms, inLen, in, &outLen, &outPtr)` |

长度约束：`cap < 7` 返回 `1`；`cap - 6 < 8` 返回 `2`；`cap - 0x16 < 8` 返回 `2`。
成功返回 `0`，`*outLen = 0x16 + innerLen`。

**两处 `adler32` 都是 zlib 语义**：种子由一次 `adler32(0, NULL, 0)` 取得，
按 zlib 约定该调用返回 `1`，因此两个校验位实际是 `adler32(data, 1)`。
6 组合成输入（0 / 1 / 17 / 64 / 5,000 / 70,000 字节明文）全部满足
`hdr[6:14] == BE64(adler32(comp, 1))` 与 `hdr[14:22] == BE64(adler32(inner, 1))`。

内部块头 4 字节是 `T29[usec % 29]` —— 由 `gettimeofday` 的**微秒字段**经
`smulh` 幻数除以 29 取余得到，实测 `usec ∈ {0,1,29,40,41,58,123456}` 全部
命中对应表项（`0xa894` 的载入值可直接读出）。第二张表同样由**微秒字段**
除以 41 取余索引（`0xa8b8` 处 `smulh` 幻数 + `lsr #4`，
`msub w8, w9, w10, w8` 取余；`0xa8d8` 载入 `T41[usec % 41]`）。
两表索引都取同一次 `gettimeofday` 的 `tv_usec`，与秒字段无关——
固定 `usec` 而改变 `tv_sec` 时两个索引都不变，可用 8 组样本重复确认。
两者作为 `Ena1907_req` 的第 3、4 参数；`T41` 的值不以其原始 4 字节形式
出现在内部块里，而是参与 `Ena1907_req` 的变换（**不单独可见**）。

`DriftsandDe`（`0xa9fc`）的判定顺序：

| 返回码 | 含义 | 位置 |
|---|---|---|
| `1` | 输入长度 `< 0x10` | `0xaa28` |
| `3` | `Dea1907_resp` 返回非 0（状态词不在其分派键集，走默认分支） | `0xabe4` |
| `7` | 第一道校验失败 | `0xabc4` |
| `6` | 第二道校验失败 | `0xaca8` |
| `0` | 成功 | `0xacac` |

`DriftsandDe` 的输入是**去掉 6 字节魔数之后**的缓冲区，即
`BE64(adler32(明文)) | BE64(adler32(inner)) | inner`。这一点由 128 位基向量
探测直接确定：改变 `hdr[0:8]` 只影响第二道比较值，改变 `hdr[8:16]` 只影响
第一道比较值，`hdr` 之外的字节不影响任何一道。

第一道校验（`0xabc0` 比较，失败返回 `7`）：

```
BE64(hdr[8:16]) == adler32(hdr[16:], 1)          # zlib 种子 1
```

第二道校验（`0xaca4` 比较，失败返回 `6`）：

```
BE64(hdr[0:8]) == adler32(明文, 1)               # 明文 = Dea1907_resp 的输出
```

两道都是把 8 字节字段与大端 64 位读出直接比对，**不存在位混合表达式**。
基向量实测：40 组随机 16 字节头下 `check1 == BE64(hdr[8:16])` 40/40、
`check2 == BE64(hdr[0:8])` 40/40；把截断后的真实 `sand` 输出送进
`DriftsandDe`，两道校验都会通过，随后停在 `Dea1907_resp`。

### 3.4 `Ena1907_req` / `Dea1907_resp` 的形态

| 项目 | `Ena1907_req` | `Dea1907_resp` |
|---|---|---|
| 起始 | `0x125f58` | `0xacd8` |
| 字节数 | `0xe5ddc` | `0x11add8` |
| 指令数 | `235,383` | `289,654` |
| 间接分支（`br`/`blr`/`braa`） | **0** | **0** |
| `ret` | 1（`0x126dc8`） | 1（`0xad38`） |
| 直接 `B`/`BL` | 3,273 | 2,547 |
| `B.cond` | 12,192 | 11,738 |

结论（**结构已证实**）：这两个函数不是“取指—译码—分派”的解释器循环，
而是把状态机**编译**成直连控制流的等价变换；每段只有一个函数出口，
内部没有跳转表。

`Dea1907_resp` 的分派键是 `inner[0:4]`，即 `Ena1907_req` 写入的状态词。
`0xacd8`–`0xb000` 是一棵 3,242 次比较（2,161 个互不相同的键值）的二分派
树，默认分支在 `0xae28` 装入 `w0 = 3` 并在 `0xb000` 直接跳回出口。
`DriftsandEn` 写下的状态词是 `T29[usec % 29]`，而 29 个表项
**没有一个落在 `Dea1907_resp` 的键集里**（同表核验 29/29 不在键集），
因此请求侧信封在响应侧 `drift` 里必然走默认分支、返回 `3`。

这说明 `sand` 与 `drift` 是两条**方向不同**的信封：
`sand` 产出请求信封（魔数 + 两个 `adler32` + `Ena1907_req` 状态块），
`drift` 消费响应信封（无魔数 + 两个 `adler32` + `Dea1907_resp` 状态块）。
本次只把请求侧的字节布局与两道校验完整复原；**响应侧状态块的有效键值集
未复原**（键集里只有 `0c4ad3e0` 一个键在前 400 个候选中不返回 3，
且它返回 `2`，即另一个长度/形态拒绝码），因此报告不宣称可重放
`sand`/`drift` 的完整逆运算。

### 3.5 辅助导出

- `EnBufferLHint` / `DeBufferLHint`：`mov/sub/add` 组合后原样返回入参
  （净效果为恒等），是内部缓冲长度提示的占位实现。
- `en`（`0xd80`）：`EnBufferLHint` → `malloc` → `memset` → `DriftsandEn`
  → `malloc`+`memcpy` 出结果，是 `sand` 的 JNI 包装层。
- `register_handler` + `0xd1c`：`sigaction(SIGSEGV)` 安装，收到信号后
  `write(1 字节)` + `close(fd)`，用于静默结束被调试/被篡改进程。

## 4. `com.qunar.rc`（第一方风控 SDK）

### 4.1 初始化

```java
// com/mqunar/atom/defensive/Sepa.java
public static final String APP_TOKEN = "97d7407497855043c7c8d55fcc40710b";
RC.getInstance().init(QApplication.getContext(), APP_TOKEN);
```

`Sepa` 在三种时机触发上报：`LifeCircleCallback`（`cacheRiskControlForDefensive`）、
`PubInterfaces`（`cacheRiskControlForCtrip` / `cacheRiskControlForQunar`）、
`VerifyImageTriggerListener`。三个 Service（`ABService`、`CtripService`、
`PubService`）各自 `sendHotDogRequest()`。

`APP_TOKEN` 是**客户端应用标识**（32 位十六进制），用于 SDK 侧识别调用方，
不是账号凭据；本报告保留其字面量作为证据，不提供任何绕过用法。

### 4.2 原生方法

`com.qunar.rc.d.j` 声明 6 个 native 并 `System.loadLibrary("qlynx_v++")`：

| 方法 | 修饰 | 用途（按调用点） |
|---|---|---|
| `cd(String, int)` | `synchronized` | 生成指纹上报体，`i=10002/10003` |
| `ep()` | — | 取设备指纹（返回值去换行后落 `qrc_sp`） |
| `jcd(int)` | — | 按编号取某字段 |
| `sepa(String)` | — | 风控状态查询 |
| `sfp(String)` | `synchronized` | 写入服务端下发的指纹（`df`） |
| `uti(String)` | — | 返回 JSON 对象形式的加签结果（`network.md` §6） |

`libqlynx_v++`（`libqlynx_v++`，`2,810,216` 字节）的 `JNI_OnLoad`（`0xf3808`）
通过 `vmEntry`（`0x22aee0`）做动态注册，**没有 `Java_*` 导出**，因此方法表
只能从 `.data.rel.ro` 的重定位得出。该库的 `.rodata` 有一段
`QMOD\x02…` 自描述块（`0x2784a8`）与 `setSaveVal / init_fp / risk_file /
jbp / uti / get_opengl_info / init_base_env / root / cjd / get_fp /
build_env_deal` 等短名，说明该库同时承担“环境初始化 + 原生工具”双重角色。
`libqlynx_v++` 同时依赖 `libEGL.so` / `libGLESv2.so`（63 个库里只有它、`libpag.so`、
`libBaiduMapSDK_map_v7_6_6.so` 三者链接 EGL），与 `.rodata` 中的
`get_opengl_info` 短名一致，说明该库还承担 OpenGL 环境采集。

> 上述 6 个方法的**内部实现未复原**。`NUtils` 层与 `FpService` 的调用顺序、
> 参数与重试策略已确定（见下），算法本体属未分析项。

### 4.3 指纹上报

```java
// FpService
private static final String f43332b = UUID.a();          // 每次进程一个 UUID
String strCd = NUtils.cd(f43332b, i3);                   // i3 = 10002 或 10003
Request.b(str, strCd, responseCallBack);                 // 见下
```

```java
// com/qunar/rc/service/net/Request.b
httpURLConnection.setRequestMethod("POST");
httpURLConnection.setRequestProperty("Content-Type",
    "multipart/form-data; boundary=----*****SPLIT_SYMBOL*****");
httpURLConnection.setRequestProperty("Accept", "application/json");
outputStream.write(("------*****SPLIT_SYMBOL*****\r\n"
    + "Content-Disposition: form-data; name=\"b\"\r\n\r\n" + str2 + "\r\n"
    + "------*****SPLIT_SYMBOL*****--\r\n").getBytes());
```

- 超时 30 秒，POST，仅当 HTTP 200 视为成功。
- 失败时若网络可用且重试次数 `< 3` 则递归重试；
  响应 `status == 1001` 时同样重试（最多 3 次）。
- 成功后把 `data.df` 交给 `NUtils.sfp(df)` 并落 `qrc_sp`。

### 4.4 环境检测

`com/qunar/rc/d/Checker.java`：

| 方法 | 逻辑 |
|---|---|
| `c()` | 模拟器评分：`FINGERPRINT` 以 `generic` 开头 +10、含 `vbox` +10、含 `test-keys` +10；`MODEL` 含 `google_sdk` / `Emulator` / `Android SDK built for x86` 各 +10；`MANUFACTURER` 含 `Genymotion` +10；`BRAND` 与 `DEVICE` 均以 `generic` 开头 +10；`PRODUCT == google_sdk` +10；`ro.hardware` 含 `ttvm`/`nox` 各 +10；另有一批 +1 的软判据（baseband 空或 `1.0.0.0`、`ro.build.flavor` 空或含 `vbox`/`sdk_gphone`、`ro.product.board` 含 `goldfish`/`android`、`ro.board.platform` 空或含 `android`、`ro.hardware` 空、无闪光灯特性、传感器数 ≤7、无 `TYPE_LIGHT`、蓝牙名为空）。**总分 > 3 判为可疑**。 |
| `b()` | `ApplicationInfo.FLAG_DEBUGGABLE` 或 `Debug.isDebuggerConnected()` |
| `d()` | 遍历 `ConnectivityManager.getAllNetworks()`，命中 `TRANSPORT_VPN`(4) 返回 true |
| `a(String)` | keystore attestation：`KeyGenParameterSpec.Builder("qr_alias", 12).setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1")).setDigests("SHA-256","SHA-384","SHA-512").setUserAuthenticationRequired(false).setAttestationChallenge(str.getBytes())`，取 `getCertificateChain("qr_alias")` 全链 Base64 成 JSON 数组 |

`a()` 的 `setAttestationChallenge` 让服务端可验证密钥确实由设备的
TEE/StrongBox 生成：这是**硬件级**设备证明，客户端无法伪造。

### 4.5 本地系统读取

`com/qunar/rc/utils/Cmd.java`：

```java
Process p = Runtime.getRuntime().exec("ls -al /sdcard/Android/data/ | head -n 50");
// 逐行累积，最多 50 行；结束用 QASMDispatcher.dispatchVirtualMethod(p, "java.lang.Process|destroy|[]|void|0")
Method m = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
```

即：**外部存储目录清单**（前 50 项）与任意 `SystemProperties` 键读取
（被 `Checker` 用于 6 个 `ro.*` 键）。

### 4.6 TLS 证书链与网络出口

`com/qunar/rc/d/d.getcc()`（结果进程内缓存）：

1. `InetAddress.getByName("www.qunar.com")` → `dnsIp`；
2. `Socket.connect(new InetSocketAddress("www.qunar.com", 443), 5000)`，
   用默认 `SSLSocketFactory` 包装，`setEndpointIdentificationAlgorithm("HTTPS")`，
   `startHandshake()`；
3. 取 `getLocalSocketAddress().getAddress().getHostAddress()` → `localIp`
   （即设备的**出口 IP**）；
4. 取 `session.getPeerCertificates()` 全链，Base64 后放入 JSON 数组。

这份数据用于识别中间人代理（证书链不是预期的公共 CA 链）与出口网络归属
（VPN/机房/代理池）。本次未执行任何连接。

## 5. `com.ctripfinance.risk.device`（金融设备指纹 SDK）

### 5.1 收集器

`com/ctripfinance/risk/device/collector/` 共 17 个：
`AppInfo`、`AppListInfo`、`BKBInfo`、`BasicInfo`、`BasicStateInfo`、`BatteryInfo`、
`BuildInfo`、`CpuInfo`、`HardwareInfo`、`IdentityInfo`、`NetworkInfo`、
`OthersInfo`、`PermissionInfo`、`SafeInfo`、`ScreenInfo`、`SensorInfo`、`SimInfo`。
每个收集器实现 `i()` 返回自身段名（如 `"IdentityInfo"`、`"PermissionInfo"`），
`a()` 填充键值。

### 5.2 标识派生

```java
// IdentityInfoCollector
c("androidId", ...);  c("androidId_1", DeviceUtil.j(ctx));  c("oaid", BaseCentral.d().i());
sb.append(Build.BRAND).append("|").append(Build.MODEL).append("|")
  .append(androidId).append("|").append(StorageUtil.a()).append("|")
  .append(width).append("x").append(height);
c("id5", <digest of sb>);
String s = BaseCentral.h().getString("risk_install_uuid", "", false);
if (StringUtil.c(s)) { s = UUID.randomUUID().toString();
                       BaseCentral.h().setString("risk_install_uuid", s, false); }
c("instUUID", s);
c("clientId", BaseCentral.d().e());  c("gid", BaseCentral.d().h());
```

即 `id5 = f(BRAND|MODEL|androidId|StorageUtil.a()|WxH)`，`instUUID` 落
`risk_install_uuid`，两者分别代表“设备维度”与“安装维度”标识。

`PermissionInfoCollector` 输出 8 个布尔：`phoneStateAuthorized`、
`locationAuthorized`、`rStorageAuthorized`、`wStorageAuthorized`、
`cameraAuthorized`、`recordAudioAuthorized`、`rCalendarAuthorized`、
`wCalendarAuthorized` —— 授权状态本身即是上报字段。

`AppListInfoCollector` 经 `AppInfoManager.d().a()` 取应用列表，
序列化进 `appList` 字段。

### 5.3 结果加密

```java
// com/ctripfinance/risk/device/util/EncryptUtil
public static String b(String str, String str2, String str3) {   // str2=gid, str3=timestamp
    String d = d(str);                                           // 每个字符 ^1，再 Base64
    String key = c(str2.getBytes(UTF_8)).substring(0, 8)         // MD5(gid) hex 前 8 位
               + str3.substring(0, 8);                           // 时间戳前 8 位
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(ENCRYPT_MODE, new SecretKeySpec(key.getBytes(), "AES"),
                new IvParameterSpec(StringUtil.f9345a.getBytes()));
    return Base64.encodeToString(cipher.doFinal(d.getBytes(UTF_8)), 2);
}
// StringUtil.f9345a = 16 字节固定 IV 常量
```

三段结构：先做一次 `^1` 混淆 + Base64，再做 AES-CBC，再 Base64。
密钥的前 8 字节来自 `MD5(gid)`，即**设备标识本身参与派生**；后 8 字节是时间戳，
所以同一设备不同时刻的密文密钥不同，但两者服务端都已知。

### 5.4 上报

```java
// com/mqunar/atom/finance/risk/DeviceInfoRequest
request.hostPath = GlobalEnv.getInstance().isRelease()
    ? "https://jr.qunar.com/m/quser/upDevice.do"
    : "http://<beta-host>/quser/upDevice.do";
```

```java
// com/ctripfinance/risk/device/api/InfoCollectHelper.uploadInfo
long j3 = Math.max(j3, ...60000L);
long last = BaseCentral.h().getLong("last_upload_time", -1L);
if (last > 0 && System.currentTimeMillis() - last < j3) return false;   // 节流
BaseCentral.h().setLong("last_upload_time", System.currentTimeMillis());
...
map.put("clientId", ...); map.put("gid", ...); map.put("timeTag", ...);
map.put("os", "Android"); map.put("reqChannel", ...); map.put("uid", ...);
map.put("qClientUid", ...); map.put("appVersion", ...); map.put("productNo", ...);
map.put("scene", ...); map.put("content", <加密后的收集结果>);
```

`DEFAULT_RESTRICT_TIME = 300000`（5 分钟）是默认节流窗口，
实际取 `max(调用方给定值, 60000)`。这个节流是**客户端自限**：
服务端仍会看到每次真实上报，节流只降低频率。

## 6. 传输层其它反分析点

| 机制 | 位置 | 说明 |
|---|---|---|
| Host 探测重试 | `com/mqunar/libtask/IPHostRetryHelper` | 默认列表含 `http://slugger.qunar.com/favicon.ico`、`http://q.qunar.com/favicon.ico` 与两个硬编码 IP 的 favicon；用 `HostHeaderInterceptor("slugger.qunar.com")` 做连通性探测后决定用 host 还是 IP |
| 代理重试 | `QRetryOkHttpInterceptor` / `QunarRetryInterceptor` | 仅对 `https://slugger.qunar.com/slugger-proxy?qrt=` 前缀生效，`NetOptConfig.netRetryEnable()` |
| 调试替换 | `MockNet.getMockNetCallBack()` | 非 release 构建可整体替换请求体与响应 |
| 染色分流 | `MiniFlowPLTDyeingMng.getDyeingHeader()` | 灰度标记进入请求头 |
| 崩溃静默 | `libgoblin` 的 `SIGSEGV` handler | 见 §3.5 |

## 7. 服务端不可见部分

以下内容无法从客户端静态确定，报告不做任何推断：

- 响应侧信封 `Dea1907_resp` 的有效状态词集合与其变换（请求侧布局与两道
  校验已完整复原，响应侧只确定到“键集不含 `T29` 任何一项”）；
- 服务端对 `hk`/`catom`/`cas`/`id5` 的评分、阈值与处罚；
- `NUtils.cd/ep/uti/jcd/sepa/sfp` 六个 native 方法的内部实现；
- `APP_TOKEN` 在服务端对应的租户/策略配置；
- 设备指纹上报的保留期与共享范围。

## 8. 静态化说明

本页所有常量、地址、指令计数、字段名与调用顺序均来自反编译、ELF 符号表、
`llvm-objdump` 反汇编与 Unicorn 受控模拟（仅对 APK 自带的 `.so` 镜像，
输入为合成数据）。未对任何真实设备或服务端发起请求。
