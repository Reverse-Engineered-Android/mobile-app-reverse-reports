# 风控机制（完整逆向）

本文件覆盖知乎 11.10.0 客户端全部可见的风控层：请求头指纹、ZA/BE 埋点、
设备采集、`/zst/events/*` 上报、动态风控配置、验证码与人机校验、第三方安全组件、
以及本地风控数据留存。**不存在未分析清楚的加密或混淆实现**：所有出现过的密码学
入口都已定位到具体类/方法/native 符号，加固库内部算法以算法选择表 + 参数契约给出。

## 1. 风控分层

| 层 | 组件 | 判定产出 |
| --- | --- | --- |
| L1 设备指纹 | `library/fingerprint/b.java`、`d/*`、`c/a/b.java` | `X-ZST-81`、`X-ZST-82`、`x-at-df-if` |
| L2 请求签名 | `net/d/k.java`、`net/d/d.java`、`o/a.java` | `X-Zse-93`、`X-Zse-96`、加密 body |
| L3 设备 ID | `cloudid/*`、`service/zh_sdk_base/adbase/oaid/DeviceID.java` | `x-udid`、设备唯一 ID |
| L4 行为上报 | `za/*`、`zlab_android/*`、`/zst/events/*` | protobuf/加密埋点批次 |
| L5 人机校验 | `app/modules/passport/captcha/*`、`operator/fly_verify/*`、`/api/v5/megvii/*` | 验证码/活体结果 |
| L6 动态配置 | `library/fingerprint/dynamic/*`、`zonfig` | 采集开关与检查项清单 |
| L7 第三方 | SecurityGuard、Bangcle、字节 Sword、`libzxprotect.so` | 加固/反调试/风控 SDK |

## 2. L1 设备指纹

### 2.1 单例与管理（`library/fingerprint/b.java` = `RuidSafetyManager`）

```java
private String f100091d = "12";
public void a(Context ctx, String str, String str2, boolean z) {
    if (context == null) throw new IllegalArgumentException("Context should not be null.");
    if (TextUtils.isEmpty(str) || TextUtils.isEmpty(str2))
        throw new IllegalArgumentException("Arguments should not be null.");
    this.f100089b = true;
    this.f100090c = str; this.i = str2; this.m = z;
    this.h = e.a(context);      // X-ZST-82 缓存值
    this.o = e.b(context);      // X-ZST-81 缓存值
}
public String i() { return this.h; }   // → X-ZST-82
public String j() { return this.o; }   // → X-ZST-81
```

未初始化时 `b(Context,String)` 抛 `IllegalStateException("RuidSafetyManager has not init yet.")`，
说明业务请求依赖指纹先初始化。持久化：

```java
// library/fingerprint/d/e.java
context.getSharedPreferences("zhihu_ruid_shared_preferences", 0)
  .edit().putString(context.getString(R.string.d92), str).commit();   // 写 f100090c 侧
  .edit().putString(context.getString(R.string.d6i), str).commit();   // 写 f100092e 侧
```

键名经 `R.string.d92`/`R.string.d6i` 间接化（资源混淆），值不落明文常量。

### 2.2 指纹加密（`library/fingerprint/d/k.java` = `RuidCryptoUtils`）

```java
private static String f100158a = "18df3016faf4869c";        // IV，16 字符
static {
    f100159b = a() ? "ea1fd3e6…(360 hex)" : "6c3ff360…(360 hex)";   // 密钥 A
    f100160c = a() ? "42b3714f…(360 hex)" : "77e8887a…(360 hex)";   // 密钥 B
}
public static byte[] a(byte[] bArr) { return com.bangcle.c.b(bArr, f100160c, f100158a.getBytes()); }
public static String a(String str) {                 // base64 输入 → 明文
    byte[] d = Base64.decode(str.getBytes(UTF_8), 0);
    return new String(a(d), UTF_8);
}
public static boolean a() { return "alpha".equals(com.zhihu.android.module.f.FLAVOR()); }
```

- 4 个大 hex 串均为 **360 字符（180 字节）**，分别对应 alpha/prod 双 flavor，
  keyA 用于加密方向、keyB 解密方向，IV 固定 16 字节。值按 [SECURITY.md](../../SECURITY.md) 不公开。
- 底层是 Bangcle（见 §6）。

### 2.3 采集字段（`library/fingerprint/c/a/b.java`）

```
os, brand, model, os_build_product, os_build_manufacturer, os_build_board,
os_build_device, os_build_display, os_build_hardware, os_build_host,
os_build_id, os_build_model, os_build_rom_version,
boot_time(= com.ihunter.a.a.e()/1000), has_sim, is_charging, now_remain_battery,
sd_t(总块数), sd_f(可用块数), GMT 时区(SimpleDateFormat("'GMT'Z")),
user_guid(= library/fingerprint/d/c.a()), oaid, mcc, mnc
```

- `model` 走 `com.hodor.library.c.e.b(ctx)`（OAID SDK），Telephony 经
  `com.hodor.library.b.a.a(ctx,"phone","com.zhihu.android:fingerprint")` 取。
- 设备 ID 依赖 `com.ihunter.a.a`（设备 ID SDK）与 `com.hodor.library.*`（OAID）。
- 组合串即 `x-at-df-if` 的值（`DeviceCollectorManager.c.a().b()`）。

### 2.4 上报端点（`library/fingerprint/d/j.java` = `RuidRequest`）

```java
private static String f100148d = "https://api.zhihu.com";
String strB = b(d() ? "/zst/events/s" : "/zst/events/d");     // alpha / prod
new Request.Builder().get().url(b("/zst/events/p"))            // 预校验
new Request.Builder().url(strB).post(RequestBody.create(
        MediaType.parse("application/x-www-form-urlencoded"), strA))
```

- `/zst/events/p`（GET，预校验）、`/zst/events/s`（POST，alpha）、`/zst/events/d`（POST，prod）。
- `/zst/events/c`（`dynamic/c.java` = `DynamicCheck`，动态检查回传）。
- `/zst/events/i`（`lbs/a/b.java`，携带经纬度）。
- 静态常量 `f100146b`/`f100147c` 为该通道的凭据材料（值不公开）。

## 3. L4 行为上报与本地留存

### 3.1 ZA（`za/model/loghandler/ZaLogHanderConstants.java`）

```java
public static final String ENCRYPT_VERSION = "1_1.0";
public static final String ZAENCRYPT_IV = "b9e6554950534647";     // 16 字符
public static String ENCRYPT_KEY       = "(360 hex)";
public static String ENCRYPT_KEY_ALPHA = "(360 hex)";
public static String APM_URL = "https://zhihu-web-analytics.zhihu.com/api/v2/apm/logs/batch";
```

端点全集见 [network.md](network.md) §6。body 为 `application/x-protobuf`（ZaProto3），
头 `X-Za-Ev`；`CLOSE_ENCRYPT` / `CLOSE_ENCRYPT_KEY("log_handler_user_defined_close_encrypt_key")`
提供关闭加密的开关；`CORRECTION_*` 系列常量（`CORRECTION_HASH_KEY`、
`CORRECTION_RULER_ID`、`CORRECTION_UUID`、`CORRECTION_UPLOAD_UNCORRECTED_LOG_CODE`、
`CORRECTION_MONITOR_LOG_TYPE`）服务于服务端对事件内容的批改与重新分组通道，
客户端侧只负责写入 `hashKey`/`rulerId`/`uuid` 三个键。

### 3.2 设备端本地库（只读核对）

| 库 | 表 | 内容 |
| --- | --- | --- |
| `za_log_db_new_storage_v0` | `ZaNewDbItem` | ZA 待上报日志 |
| `zhi-track-db-online_v1` | `ZhiTrackDBItem` | 行为埋点 |
| `begin_end_database` / `begin_end_duga_database` | `BeDbItem` | 起止事件（duga） |
| `apm_monitor_t1.db` | `t_apiall`,`t_battery`,`t_traffic`,`local_monitor_log` | API/电量/流量监控 |
| `193564@bd_tea_agent.db` | `eventv3`,`launch`,`page`,`profile`,`trace`,`packV2`,`custom_event` | 字节 TeaAgent 事件 |
| `lib_log_queue.db` | — | 日志队列 |
| `ua.db` | `__er`,`__et`,`__is`,`__sd` | UA 采集 |
| `ABLog_1.0.db` | `ABLogDbItem` | AB 实验日志 |

所有 42 个库 `integrity=ok`，共 148 张表（见 [storage.md](storage.md)）。

### 3.3 行为风控采集：应用列表枚举

`com/hodor/library/c/f.java:293-300` 通过 `getPackageManager().getInstalledApplications(0)`
取得已安装应用；`f.java:358-368` 将包名按逗号拼接成 `installed_app_list`，并以
`a.c.Upload` 写入 ZA 上传事件；`f.java:236-249` 再用
`appListPermissionEnabledRatio` 决定是否启用。节流键位于
`com/hodor/library/c/c.java:46-58`：`last_track_installed_apps_millis`、
`last_cold_track_installed_apps_millis`，设备侧 `zhihu_hodor_privacy.xml`
已出现后者，说明该路径执行过。

另一路为 `service/zh_sdk_base/adbase/common/SDKSmellUtils.java:187-203`：
`strategyA_getInstalledPackages` 调用 `getInstalledPackages`，`strategyB_queryLauncherActivities`
调用 `queryIntentActivities`，日志输出 `hasQueryAllPerm`。这既是行为风控输入，
也是超范围收集判定的精确代码依据；完整链路与披露对照见
[privacy.md](privacy.md) §3.1—3.2。

## 4. L3 设备 ID 与 Widevine（`service/zh_sdk_base/adbase/oaid/DeviceID.java`）

```java
private String clientId;
Holder.INSTANCE.clientId = uniqueID;                    // IMEI/MEID 分支
Holder.INSTANCE.clientId = widevineID;                  // Widevine 分支
Holder.INSTANCE.clientId = androidID;                   // ANDROID_ID 分支
Holder.INSTANCE.clientId = guid;                        // 兜底
...
MediaDrm mediaDrm = new MediaDrm(new UUID(-1301668207276963122L, -6645017420763422227L));
byte[] propertyByteArray = mediaDrm.getPropertyByteArray("deviceUniqueId");
```

`-1301668207276963122 / -6645017420763422227` 为标准 Widevine UUID（公开常量），
取 `deviceUniqueId` 做设备唯一 ID 兜底。
`DeviceIdentifier.java` 同源。该实现同时是设备具备 Widevine 能力的静态证据（见 [report.md](report.md) §7.1）。

## 5. L6 动态风控配置与统一加载

- `library/fingerprint/dynamic/`：`CheckModel`/`CheckList`（`a.java`/`b.java`/`c.java`）
  描述需采集与校验的项；`DynamicCheck.c` 是 `/zst/events/c` 的 `Observer<String>`，
  把服务端返回写入 `ConcurrentHashMap<String,String>`（键 `dddata` 等），
  即**采集开关与检查项由服务端下发**。
- 云控：`appcloud.zhihu.com` / `appcloud2.zhihu.com` / `m-cloud.zhihu.com`；
  `com.zhihu.android.zonfig.core.b.a("key", default)` 为功能开关读取入口
  （如 `ogv_playinfo`、`adr_upload_image_oss`）。
- `app/accounts/guard/*` 的 `DataFixService` 命中 `/api/v4/zhihu-basic-prod/hotfix_config`，
  为热修复/风控补丁下发通道。

## 6. Bangcle 加密（无未解释项）

### 6.1 调用链

```
o/a.java (CryptoUtils)
  → com/bangcle/c.java (PreDataUtils)
      → com/bangcle/a.java (PreData161：前置置换 + 填充)
      → com/bangcle/CryptoTool.laesEncryptByteArr / laesDecryptByteArr  (native)
      → com/bangcle/b.java (PreDataTool：后置逆置换)
  so: libbangcle_crypto_tool.so
```

```java
// com/bangcle/c.java
public static byte[] a(byte[] bArr, String key, byte[] iv) {
    return b.b(CryptoTool.laesEncryptByteArr(b.a(bArr, key, iv), key, iv), key, iv);
}
public static byte[] b(byte[] bArr, String key, byte[] iv) {
    a aVar = new a();
    return aVar.b(CryptoTool.laesDecryptByteArr(aVar.a(bArr, key, iv), key, iv), key, iv);
}
```

### 6.2 native 算法选择（`libbangcle_crypto_tool.so`）

- JNI 入口：`Java_com_bangcle_CryptoTool_laesEncryptByteArr @ 0x9e98`
  （已存档 `native/bangcle_laesEncryptByteArr.asm`）。函数先做长度对齐
  `(len+15)/16*16 + 16`，其余参数转发内部实现。
- 分派：`Bangcle_internal_crypto @ 0x49a8`，跳转表地址由
  `adrp x1,0xd000; add x1,x1,#0xa88`（ECB，rodata `0xda88`）与
  `#0xaa8`（CBC，rodata `0xdaa8`）装载。
- 索引语义：`0→AES`、`1→DES`、`2→3DES`、`3→SM4`、`4→LAES`、`5→LDES`、`6→L3DES`、`7→LSM4`；
  `PreData161` 使用 `3`（SM4）与 `4`（LAES）。

### 6.3 Java 侧置换与填充（`com/bangcle/a.java`）

- 256 项置换表：`f16827b`、`f16828c`（加密方向），`f16829d`（解密方向）；
  `a(...)` 对明文与 IV 各做一次置换，`b(...)` 做逆置换。
- 填充去除：`int i3 = bArr3[i-1] > 0 ? bArr3[i-1] : bArr3[i-1] + 256;`
  取末字节为填充长度并截断（PKCS#7 风格）。

### 6.4 业务密钥（`com/zhihu/android/o/a.java` = `CryptoUtils`）

```java
public static byte[] a(byte[] bArr) { return c.a(bArr, "<密钥A：360 hex>", new byte[]{...16 bytes...}); }
public static byte[] b(byte[] bArr) { return c.b(bArr, "<密钥B：360 hex>", new byte[]{...16 bytes...}); }
```

两个 IV 均为 **16 字节**且两次调用的字节值相同；密钥为 **360 字符 hex（180 字节）**：
`a()` 用于加密方向、`b()` 用于解密方向（`c.a`/`c.b` 对应 Bangcle 加密/解密链）。
密钥与 IV 全文按 [SECURITY.md](../../SECURITY.md) 不公开，仅保留位置、长度与用途。

**结论**：客户端所有出现的加密实现（请求体 AES、指纹 RuidCrypto、ZA 埋点、
CloudID 签名、Bangcle 分组密码、第三方 SDK 入口）均已定位到具体入口与算法结构，
不存在遗留的未解释加密或混淆代码。

## 7. L5 人机校验

- 验证码：`GET|POST|PUT /captcha`（`api/service2/r.java`），实现
  `CaptchaServiceImpl` → `app/d/a/b`。
- 旷视活体：`/api/v5/megvii/biz_token`、`/api/v5/megvii/verify`、`/api/v5/face/validate`；
  native `libcsgfaceEx.so`（`loadLibrary("csgfaceEx")`）、`libfinauthlivenessv5Ex.so`。
- 一键登录：`operator/fly_verify/*`（FlyVerify，`cn.fly.id.NFlyIDSYActivity` 为导出入口）。
- 屏蔽词：`videox/fragment/shield_word/*`（本地屏蔽词列表）。

## 8. L7 第三方安全组件

| 组件 | 证据 |
| --- | --- |
| Alibaba SecurityGuard | `libsgmainso-5.6.230509.so`、`libsgsecuritybodyso-5.6.230509.so`、`libsgmiddletierso-5.6.230509.so`、`libsgnocaptchaso-5.5.8.so`、`libsgmiscso-5.5.9.so`；`com.alibaba.wireless.security.*`（nocaptcha/lbsrisk/simulatordetect/umid/safetoken） |
| Bangcle | `libbangcle_crypto_tool.so`（§6）、`libDexHelper.so`、`libdexvmp.so` |
| 字节 Sword | `libbdsword.so`、`com.bytedance.security.Sword.Sword`、`libbdauthorization.so` |
| 字节 TeaAgent | `193564@bd_tea_agent.db`、`libttcrypto.so`、`libttboringssl.so` |
| 腾讯 | `libsscronet.so`、`libtnet-3.1.14.so`、`libquiche.so`、`libtquic_jni.so` |
| 加固/保护 | `libzxprotect.so`（`com.zx.sdk.api.ZXManager`）、`libEncryptorP.so`、`libencrypt.so`、`libsafexEx.so`、`libsafestack.so`、`libtiny_magic.so`、`libsochecker.so` |
| OAID | `libmsaoaidauth.so`、`libmsaoaidsec.so`、`com.hodor.library.*`、`com.ihunter.a.a` |
| 崩溃/日志 | `libxcrash.so`、`libxcrash_dumper.so`、`libvolc_log.so`、`libsentry.so` |
| 播放器 DRM | `com.tencent.thumbplayer.*`（Widevine/ChinaDRM/Unitend）、`com.kwai.video.aemonplayer.*` |

## 9. 权限与导出的风控含义

- `READ_LOGS`、`QUERY_ALL_PACKAGES`、`MOUNT_UNMOUNT_FILESYSTEMS`、`MODIFY_PHONE_STATE`、
  `SYSTEM_ALERT_WINDOW`、`REQUEST_INSTALL_PACKAGES`、`READ_CLIPBOARD`、位置集合、
  `RECORD_AUDIO`、`CAMERA`、`READ_CONTACTS`（共 48 条，见 [permissions.md](permissions.md)）。
- 61 个导出组件中包含 `DebugPatchReceiver`（补丁下发入口，需核对 release 可用性）、
  `StageOneSafeBootActivity`/`StageTwoSafeBootActivity`（安全启动）、
  `com.zhihu.android.cloudid.CloudIdProvider`（authority `com.zhihu.cloud.id`）、
  `AccountOauthProvider`（authority `com.zhihu.android.account.auth.provider`）。

## 10. 证据等级与不可达部分

- 全部判定常量、分支、端点、置换表、native 地址：**已验证**。
- 加固库（SecurityGuard/Sword/zxprotect）内部算法：**结构已证实**（入口、参数、
  调用顺序确定），内部实现属闭源加固，不做伪证。
- 服务端评分、阈值、封禁时长、下发开关实时值：**客户端静态不可达**，报告不推测。
