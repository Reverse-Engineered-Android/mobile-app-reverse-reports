# 风控机制全量清单

## 1. 架构

```text
设备画像采集 (DeviceInfoNameEnum / BatteryReceiver / e82.f)
        |
WSG native (libdidiwsg.so)
  ├── nativeCollect   -> wsgenv 查询参数
  ├── nativeIsDD06    -> 签名算法分流
  ├── dd04Sign / sign -> wsgsig 请求头
  └── nativeEncrypt/Decrypt, SecKey, FaceCollect, Report*
        |
设备级凭据 secdd-authentication (AuthInterceptor)
        |
WAF 挑战 (DiChallengeInterceptor, HTTP 522)
        |
接口加密 (onesdk, AES-128 + GZIP)
        |
服务端评分、验证码、限流或拒绝
```

客户端只产生信号并执行本地处置；服务端阈值、画像留存与最终处罚不可静态证明。

## 2. 请求签名 `wsgsig`

### 2.1 入口

`com/didi/security/wireless/adapter/SignInterceptor.java`：

```java
@a(priority = 999)                                  // :32
public class SignInterceptor implements RpcNetworkInterceptor<k, l> {

    String strPrepareSign = SecurityManager.prepareSign(str, bodyByte);   // :146
    String strDoSign = SecurityManager.doSign(strPrepareSign);            // :155
    logSig(strPrepareSign, strDoSign);                                    // :168
    if (TextUtils.isEmpty(strDoSign)) return kVar;                        // :169-171
    k.a aVarE2 = kVar2.e();                                               // :172
    aVarE2.c("wsgsig");                                                   // :173
    aVarE2.d("wsgsig", strDoSign);                                        // :174
```

`:137-143` 先用 `e.newInstance(contentType, bodyByte)` 重建请求体，保证签名的
输入与实际发送的 body 一致（否则会因流已消费而签名不匹配）。

### 2.2 待签字符串构造

`com/didi/security/wireless/SecurityManager.java:477-496`：

```java
public static String prepareSign(String str, byte[] bArr) {
    String query = getQuery(str);          // :481
    byte[] body = getBody(bArr);           // :482
    Map<String,String> m = queryStringToMap(query);   // :484
    String hex = bytesToHex(body);         // :485
    if (!TextUtils.isEmpty(hex)) m.put(hex, "");      // :486-488
    return signMapToString(m);             // :490
}
```

**body 截断**（`:187-197`）：

```java
public static byte[] getBody(byte[] bArr) {
    if (bArr == null) return null;
    if (bArr.length <= 4096) return (byte[]) bArr.clone();
    byte[] bArr2 = new byte[4096];
    System.arraycopy(bArr, 0, bArr2, 0, 4096);
    return bArr2;
}
```

即**正文最多参与前 4096 字节**，超出部分不进入签名。

**十六进制编码**（`:113-126`）：小写 `0123456789abcdef`，`bytesToHex`。

**query 展开**（`:498-514`）：

```java
for (String str2 : str.split("&")) {
    if (TextUtils.isEmpty(str2)) continue;
    String[] p = str2.split("=", 2);
    if (p.length == 2)
        map.put(URLDecoder.decode(p[0],"utf-8") + URLDecoder.decode(p[1],"utf-8"), "");
    else
        map.put(URLDecoder.decode(p[0],"utf-8"), "");
}
```

每个 key/value 对先做 URL 解码再直接拼接成 `key+value`，作为 map 的**键**，
值为空字符串。因此重复 k/v 组合会去重。

**排序与拼接**（`:592-607`）：

```java
ArrayList arrayList = new ArrayList(map.keySet());
Collections.sort(arrayList, Collections.reverseOrder());   // :596 逆序
for (String str : arrayList) {
    if (!str.startsWith("__x_") && !"wsgsig".equalsIgnoreCase(str)) {  // :599
        sb3.append(str);
        sb3.append(map.get(str));       // 值为空，等价于只 append key
    }
}
```

逆序排序意味着字典序大的片段在前。以 `__x_` 开头的键是内部保留前缀，会被
跳过；`wsgsig` 自身跳过以避免自引用。

### 2.3 签名分派

`SecurityManager.java:712-732`：

```java
public static String sign(byte[] bArr) {
    int iNativeIsDD06;
    try { iNativeIsDD06 = SecurityLib.nativeIsDD06(sContext); }
    catch (Throwable unused) { iNativeIsDD06 = 0; }      // :715-718
    if (iNativeIsDD06 == 0) return dd04Sign(bArr);        // :719-721
    if (bArr == null) { ...; return null; }               // :723-726
    Context context = sContext;
    if (context == null) { ...; return errSign(WSG_CODE_NOTINIT); }  // :727-731
    String strSign = SecurityLib.sign(context, bArr);      // :732
    ...
}
```

`dd04Sign`（`:688-697`）→ `SecurityLib.dd04Sign(context, bArr)`，
`sign` → `SecurityLib.sign(context, bArr)`，两者都进入 `libdidiwsg.so`。
`setSignInitListener`（`:556-572`）在 `nativeIsDD06 == 1` 时立即回调，说明
DD06 初始化是异步且可等待的。

`doSign`（`:699-703`）在结果不满足 `checkSign` 时替换为错误签名：

```java
String strSign = sign(str);
return !checkSign(strSign) ? errSign(WSG_CODE_SIGN_CHARACTEREXCEPTION) : strSign;
```

### 2.4 结果校验与错误签名

`checkSign`（`:128-138`）：

```java
for (byte b : str.getBytes()) {
    if ((b < 97 || b > 122) && ((b < 65 || b > 90)
        && !((b >= 48 && b <= 57) || b == 43 || b == 47 || b == 45)))
        return false;
}
```

允许字符集为 `[A-Za-z0-9+/-]`，即 Base64 字母表去掉填充符并额外允许 `-`。

`errSign`（`:173-185`）：

```java
JSONObject o = new JSONObject();
o.put("version", SDK_VERSION);
o.put("pn", DAQUtils.getPackageName(sContext));
o.put("vc", DAQUtils.getAppVersionName(sContext));
o.put("ec", i13);
return "dd02-" + Base64.encodeToString(o.toString().getBytes(), 3);
```

即 `dd02-<base64(JSON)>`，`ec` 是 `DAQException.WSG_CODE_*` 错误码。相关错误码：
`WSG_CODE_NOTINIT`、`WSG_CODE_SIGN_CHARACTEREXCEPTION`、`WSG_CODE_LOAD_FAIL`、
`WSG_CODE_DATAENC_UNSUPPORTED`。

### 2.5 原生边界

`com/didi/security/wireless/SecurityLib.java:340` `System.loadLibrary("didiwsg")`。
声明的 35 个 `native` 方法：

```text
checkMethod(Method)
getMethodInfo(Method)
nativeCheck(String)
nativeCollect(String)
nativeDD04Sig(Context, long, String, byte[])
nativeDecrypt2(byte[], byte[])
nativeDowngradeCollect(String)
nativeEncrypt(String, String, byte[])
nativeEncrypt2(byte[], byte[])
nativeFaceCollect(String, int)
nativeGetDowngradeApiError()
nativeGetFdInfoInner(String)
nativeGetHost()
nativeGetHttpsCheckUrl()
nativeGetRid()
nativeGetSessionId()
nativeInit(Context)
nativeInitCache()
nativeInitCommon(Context)
nativeInitRetry(Context)
nativeInitSign(Context)
nativeInitWsg(Context)
nativeIsDD06(Context)
nativeIsNewTokenInfoCollected()
nativeReport(String, String)
nativeReportByCmd(String)
nativeSecKey(String)
nativeSecKey2(String)
nativeSecKey3(String)
nativeSetHost(String)
nativeSetServerSessionId(String)
nativeSig(Context, long, String, byte[])
nativeSyncRequestDowngradeConfig(Context)
nativeUpdate(String, String, String, String)
nativeUpdate2(String, String, String, String)
```

`libdidiwsg.so` 静态特征（ARM64，sha256
`9360e325ed6c8c0afcc313f740df6bd910fe2d4bcf2e7721093e0eb6d318a066`）：

```text
导出符号: JNI_OnLoad, .datadiv_decode5773791847378576960
引用:     inflateInit2_, inflate, inflateEnd, uncompress, compress, compressBound, crc32
字符串:   libdidiwsg.so, tWSG, SHA3, ~AeS, AeS~, wITHrsaeNCRYPTION
```

**边界说明**：`libdidiwsg.so` 的导出表只暴露 `JNI_OnLoad` 与一个数据段解混淆
函数，全部 Java 侧 native 方法经 `RegisterNatives` 动态注册，且符号被剥离。
`wITHrsaeNCRYPTION` 逐字节 XOR `0x20`，即仅翻转大小写，得到
`WithRSAEncryption`；`SHA3` 是可复现的明文常量。`~AeS`/`AeS~` 位于高熵
数据区，单字节 XOR 扫描不能稳定还原出 `AES`，因此不作为 AES 字符串证据。
Java 层的 `AES/ECB/PKCS5Padding` 已由 `f32/a.java:11-20` 的 XOR 18 还原
直接确认。`libdidiwsg.so` 同时具备 zlib 解压能力，但**具体签名算法、轮次与
密钥派生不可由这些二进制字符串确定**。报告据此把该库定为不透明边界：接口与
输入输出已完全描述（见 §2.2、§2.3），内部实现不做推断。

### 2.6 精确判定依据汇总

| 判定项 | 精确代码 |
|---|---|
| body 参与签名的上限 | `SecurityManager.java:191` `if (bArr.length <= 4096)` |
| body 十六进制形态 | `SecurityManager.java:113-126` |
| query 解码与拼接 | `SecurityManager.java:505` |
| 排序方向 | `SecurityManager.java:596` `Collections.reverseOrder()` |
| 跳过前缀 | `SecurityManager.java:599` `startsWith("__x_")` |
| 跳过自身 | `SecurityManager.java:599` `"wsgsig".equalsIgnoreCase(str)` |
| 算法分流 | `SecurityManager.java:715,719` `nativeIsDD06` |
| 结果字符集 | `SecurityManager.java:133` |
| 失败降级 | `SecurityManager.java:702` `errSign(WSG_CODE_SIGN_CHARACTEREXCEPTION)` |

## 3. 环境采集 `wsgenv`

`com/didi/security/wireless/adapter/SecurityWrapper.java:36-43`：

```java
public static String a(String str, String str2) {
    URL url = new URL(str2);
    return SecurityManager.collect(str, url.getProtocol() + "://"
                                       + url.getHost() + url.getPath());
}
```

`SecurityManager.collect`（`:643-649`）：

```java
if (sContext == null || str2 == null || "".equals(str2)) return null;
return SecurityLib.collect(str, str2);
```

即采集的输入是**协议+主机+路径**（不含 query，避免自引用），输出为 native 生成
的环境串。附加位置 `com/didichuxing/dfbasesdk/http/SecurityAccessWsgInterceptor.java:19-47`：

```java
String strA = SecurityWrapper.a(null, WSG_TYPE_ACCESS_SECURITY);   // :23
if (TextUtils.isEmpty(strA)) return kVar;                           // :27-29
StringBuilder sb3 = new StringBuilder(str);                          // :31
int i = str.indexOf('?');                                            // :32
if (i < 0) sb3.append('?');
else if (i < str.length() - 1) sb3.append('&');
sb3.append("wsgenv=");
sb3.append(URLEncoder.encode(strA, "utf-8"));                        // :38-39
aVarE.f66704c = sb3.toString();
aVarE.c(WSG_NOT_COLLECT_WSGENV);                                     // :41
aVarE.d(WSG_NOT_COLLECT_WSGENV, "1");                                // :42
```

`WSG_TYPE_ACCESS_SECURITY = "https://access/security"`（`:16`）是 bizType 常量。
同一常量在同目录 `SecurityAccessWsgInterceptorRabbit.java:32` 的 Rabbit 传输
路径复用。

`wsgenv` 也可作为**请求头**出现：`com/didi/ride/kop/WsgenvIntercept.java:22,33`
与 `com/didi/ride/kop/WsgenvRpcIntercept.java:27,43,55`。`WsgenvRpcIntercept`
的 `:55` 读取 Apollo 白名单
`qj_didi_kop_api_wsgenv_white_list` 的 `urls` 字段，说明只有白名单域名走该路径。

其它消费点：`com/didi/hummerx/comp/HMXKopHttpClient.java:443`、
`com/didi/drn/freight_drn/turbo/FreightDRNHyWsgEnvModule.java:48-50`、
`com/didi/security/diface/bioassay/g.java:216`、`h.java:65`、`d.java:977`
（人脸活体把 `wsgenv` 放进上报 JSON）。

### 3.1 蓝牙采集

`SecurityManager.java:144-146`：

```java
private static void collectBluetoothAsync(String str, String str2) {
    BtDelegate.asyncCollect(sContext, new a(str, str2));
}
```

入口 `SecurityManager.report`（`:651-681`），条件为
`isBtApolloOpen(sContext)`（`:657`、`:670`）且事件类型为 `bluetooth` 或
`event_start_charge`/`event_stop_charge`（`:670` 时额外构造
`{"scene":"charge","orderid":<id>}`）。`:653` 限制 `str3` 字节数 > 10240 时
直接丢弃。

## 4. WAF 挑战（HTTP 522）

`com/didichuxing/security/challenge/DiChallengeInterceptor.java`：

```java
@i42.a(priority = 990)                                   // :21
public class DiChallengeInterceptor implements e<k, l> {
    private static final String APOLLO_CL_KEY = "sec_close_challenge_toggle";  // :23
```

`com/didichuxing/security/challenge/a.java` 的执行判定：

```java
if (!cVarBuild.getUrl().toLowerCase().startsWith("https"))   // :59
    return cVarBuild.execute().c();                           // 非 https 直接放行

if (cVarBuild.a("hbGxlbmdlZGVtbyIsInZj") != null) {           // :62 哨兵
    cVarBuild.b();
    return cVarBuild.execute().c();
}

boolean z3 = cVarBuild.a("secdd-challenge") != null;          // :66
if (!z3) aVar2.e(cVarBuild);                                  // :68

d<T> dVarExecute = cVarBuild.execute();                       // :70
...
if (z3) return dVarExecute.c();                               // :89-91
if (522 != dVarExecute.b()) return dVarExecute.c();           // :92-94 关键判定
```

命中 522 后读取响应体、解析 WAF 下发的挑战描述，交由 `ClgJsExecutor` 执行返回
的 JS（QuickJS 优先，WebView 回退），并在 `:338` 写回：

```java
cVar.c("secdd-challenge", sb3.toString());     // :338
```

`:330-337` 显示回填串由 `z3` 标志位、`chid`、`aVar.f108016a` 等字段用 `|` 拼接。
响应侧 `DiChallengeInterceptor.java:121` 读取响应头
`set-secch-sessionid`。

**该机制全部分析清楚，无残留步骤。** 开关 `sec_close_challenge_toggle` 为真时
（Apollo）整个挑战流程关闭。

## 5. 接口级加密

`com/didi/safety/onesdk/encrypt/interceptor/InterfaceEncryptOkHttpInterceptor`：

| 判定 | 位置 |
|---|---|
| 仅 POST | `:175` `!POST.equalsIgnoreCase(request.method())` |
| content-type = `application/json` | `:179-180` |
| content-type = `multipart` | `:182-183` |
| part 数 ≤ 256 | `:72-73` |
| part 名非空 | `:88` |
| 字段不重复 | `:90-91` |
| 文本字段大小上限 | `:95` |
| 密文容器注入 | `:105` |

业务类型枚举 `com/didi/safety/onesdk/BIZ_TYPE.java:4-13`：

```text
TYPE_FACE, TYPE_OCR, TYPE_CAR_FACE, TYPE_GLOBAL_OCR, TYPE_MASK,
TYPE_GLOBAL_FACE, TYPE_EID, TYPE_TOUCHID, TYPE_GLOBAL_BIOMETRIC, TYPE_AUDIO
```

即该加密面向人脸、证件 OCR、车辆人脸、口罩识别、EID、触控指纹与音频，主要
服务于实名与安全验证，而非全部业务请求。

开关由 `com/didi/safety/onesdk/business/ApolloHolder` 提供，
`e81/a.java:85,101,136,151` 按 `ApolloHolder.b(BIZ_TYPE.*)` 与
`TYPE_FACE` 分组决定是否加密。

算法闭包见 [network.md](network.md) §6。

## 6. 设备指纹 `x-ddfp`

`com/didi/didipay/pay/net/DidipayHeadersInterception.java`：

```java
return hasHead(kVar, "Authorization");      // :35 已有则不重复加
aVarE.d("Authorization", getToken());       // :53
aVarE.c("x-ddfp");                          // :67
aVarE.d("x-ddfp", strI);                    // :68
```

`strI = e82.f.i(context)`（`e82/f.java:330-345`），返回值是 `d.B`，即
`DeviceInfoNameEnum.customId` 对应的设备唯一标识，未生成时调 `b.a(context)`
生成并缓存（`:337-339`）。该值相同请求内复用。

## 7. 设备画像字段

`com/didichuxing/security/safecollector/DeviceInfoNameEnum.java:4-34` 全量 31 项：

| 类别 | 字段 |
|---|---|
| 应用 | `appName`、`packageName`、`appVersionCode`、`appVersionName`、`appVersionIssue` |
| 系统 | `osVersion`、`model`、`brand`、`cpu`、`cpuSerialNo` |
| 显示 | `pixels`、`screenHeight`、`screenWidth`、`screenSize` |
| 存储 | `totalSpace`、`totalDisk` |
| 风控信号 | `isRoot`、`emulatorType`、`isDebug` |
| 标识 | `customId` |
| 环境 | `utcOffset`、`countryCode`、`locale`、`mcc`、`mnc`、`networkOperator`、`simCarrier`、`networkType`、`localIp`、`batteryLevel`、`phoneTime` |

采集辅助：`com/didichuxing/security/safecollector/BatteryReceiver.java` 注册
`Intent.ACTION_BATTERY_CHANGED`。聚合器是 `e82/f.java`（导入
`DeviceInfoNameEnum`、`BatteryReceiver`、`Teemo`、`TelephonyManager`、
`NetworkInterface`），`f.java:47` 的 `StringBuffer f86831d` 累积指纹片段。

**判定依据**：`e82/f.java:337`
`if (TextUtils.isEmpty(d.B) && h.c(DeviceInfoNameEnum.customId)) d.B = b.a(context);`
——`customId` 是唯一被持久化的标识字段，其余字段按需读取。

## 8. Hook / Root / 调试检测

`com/didi/security/utils/AppUtils.java`：

```java
Class<?> cls = ((ClassLoader) it.next())
        .loadClass("de.robv.android.xposed.XposedBridge");     // :1836
jSONObject.put("CheckProxyHook",
        "find socket factory hooked: " + name + "|" + name2);  // :2893 / :3204
```

`com/mobile/auth/gatewayauth/utils/security/CheckHook.java`：

```java
if (str.contains("Xposed") || str.contains("xposed") || str.contains("xposed_art"))  // :38
if ("de.robv.android.xposed.XposedBridge".equals(ste.getClassName())
    && "main".equals(ste.getMethodName()))                                            // :67
if ("de.robv.android.xposed.XposedBridge".equals(ste.getClassName())
    && "handleHookedMethod".equals(ste.getMethodName()))                              // :71-72
    h.b("HookDetection, A method on the stack trace has been hooked using Xposed.");
```

`com/didi/security/utils/AppUtils.java:1088` 与 `:2404` 调用
`SecurityLib.checkMethod(method)` / `SecurityLib.getMethodInfo(method)`，即把
**反射目标方法**交给 native 判定是否被 Hook——这是签名之外的第二条完整性校验
路径。

特征字符串统计（DEX 字符串池）：`xposed` 76 次、`magisk` 8 次、
`emulator` 7 次、`frida` 5 次、`"/system/bin/su"` 5 次。

## 9. 行为采集

`com/didi/security/diface/behavior/BehaviorTraceUploadParam.java` —
`{bizCode, dataJson, oneId, token}`。

`BehaviorTraceData` 持有 `ArrayDeque<String>` 轨迹与
`ConsumeTimeData{actionConsumeTime, mirrorsConsumeTime, totalConsumeTime}`，
即**操作时间序列与耗时分布**，用于人脸/活体环节的行为判别。

## 10. 安全盾（SafetyGuard）

| 端点 | 用途 |
|---|---|
| `/api/guard/psg/v2/dashboardConfig` | 面板配置 |
| `/api/guard/psg/v2/getShieldStatus` | 盾状态 |
| `/api/guard/psg/v2/getPassengerReportInfo` | 上报信息 |
| `/api/guard/escort/shareLive` | 行程分享 |
| `/api/guard/escort/getLiveShareCardInfo` | 分享卡片 |
| `/api/guard/deviceConnect/reportV2` | 周边设备上报 |
| `/api/guard/deviceConnect/getCollectStatus` | 采集状态 |
| `/api/guard/component/dashboard` | 组件面板 |
| `/api/report/reportPassengerStatus` | 乘客状态 |
| `/api/report/reportPassengerSecuritySetting` | 安全设置 |

`NzPsgServerApi` 的签名参数含 `language, orderId, timestamp, sign, cityId,
productId, orderStatus, appId, role, recordStatus, appVersion, width, height,
shieldVersion, extra_params, startCityId, endCityId, scenePageType`
（`NzPsgServerApi.java:47,61,68`），`sign` 由 `SgUtil.getSign(timestamp)` 产生
（`NzPsgMainDialogPresenter.java:196`）。

## 11. 本地库

| 库 | Java 加载点 | 判定 |
|---|---|---|
| `libdidiwsg.so` | `SecurityLib.java:340` | 签名与环境采集 |
| `libdfbasenative.so` | `dfbasesdk/utils/AES.java:13` `stringFromJNI` | 基础库 AES/native 字符串 |
| `libsignkey.so` | `SignKey.java:8` `getPhoneSignKey` | 手机号 DES 密钥 |
| `libsqlcipher.so` | SQLCipher | 本地库加密 |
| `libPassGuard.so` | `cn/passguard` | 支付键盘，`PassGuardEncrypt.HmacSM3`（SM3） |
| `libquiet.so` | `com/wsg/wsgbackup/WsgQuiteNative.java:8` | WSG 备份通道 |
| `libdexvmp.so` | `com/fort/andJni/JniLib1773859712.java:13` | 反射调用下沉，用于联通认证 SDK |
| `libteemo_android.so` | `com/didiglobal/teemo/TeemoJni.java:14` | 设备环境探针 |
| `libconceal.so` | Facebook Conceal | 文件加密 |

`libquiet.so` 的 `WsgQuiteNative` 是 WSG 的备用通道：当主签名路径初始化失败时
提供降级签名，与 `nativeGetDowngradeApiError`、`nativeSyncRequestDowngradeConfig`
及设备端 `files/wsg_downgrade_apollo/` 目录相互印证。

## 12. 风控开关清单

| 开关 | 作用域 | 位置 |
|---|---|---|
| `sec_close_challenge_toggle` | WAF 挑战总开关 | `DiChallengeInterceptor.java:23` |
| `disable_certificate_encryption_toggle` | 自定义证书校验 | `ce1/p.java:98` |
| `enohp_encrypt` | 手机号本地加密 | `LoginStore.java:205` |
| `qj_didi_kop_api_wsgenv_white_list` | `wsgenv` 域名白名单 | `WsgenvRpcIntercept.java:55` |
| `llm_ut_ai_callcar_switch` | AI 叫车入口动画 | `scene/view/g.java:317` |
| `isBtApolloOpen(context)` | 蓝牙采集 | `SecurityManager.java:657,670` |
| 轨迹库加密开关 | `track_upload_sdk2` vs `_encrypted_v2` | `tt1/d.java:98` |

## 13. 边界声明

| 项 | 状态 |
|---|---|
| WSG 签名算法内部 | native 不透明，接口与输入输出已完整描述 |
| `libsignkey.so` 密钥派生 | native 不透明 |
| `libdexvmp.so` 虚拟机内部 | 不透明，但调用面已定位（联通认证 SDK） |
| 服务端评分、阈值、处罚 | 不可静态证明 |
| 服务端画像留存时长 | 不可静态证明 |
| 加密载荷内的具体字段 | 报告不把不可见字段写成明文 |
