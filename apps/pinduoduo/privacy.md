# 隐私政策措辞与实际采集范围的对照

本文件把《拼多多隐私政策》的**可检索原文**与 APK 内**实际采集代码**逐项对照，
判断哪些采集落在声明范围内、哪些属于未在政策中具名列举的项、以及同意前是否存在
采集行为。权限侧的运行时状态见 [permissions.md](permissions.md)。

## 1. 对照基准（政策原文出处）

| 项 | 值 |
| --- | --- |
| 文档 | `https://mobile.yangkeduo.com/private_policy.html` |
| 标题 / 版本 | 拼多多隐私政策 / **V4.1.1** |
| 更新日期 | 2025年6月30日（§9.3：2017年8月29日首次生效，2025年7月8日修订生效） |
| 页面 SHA-256 | `a71b4edec66adf2d1a4b0bdbbfdd3e08042163ed28cbe90ce2c1872c052f8b65` |
| 服务协议 | `https://mobile.yangkeduo.com/user_agreement.html`，拼多多用户服务协议 **V4.2**（公示 2026年2月8日，生效 2026年2月16日） |
| 协议 SHA-256 | `9fd4a2f667fd327577d8802b2f6bb43c539d92db4c9ed8494055f6b52deba3fe` |

政策正文（§2.7）引用三份清单：**《拼多多权限申请清单》**、
**《拼多多第三方SDK目录》**、**《拼多多信息共享清单》**。三份清单均不在
`private_policy.html` 内联展开，也没有可由客户端解析的独立 URL——
`permission_list.html`、`sdk_list.html`、`third_sdk.html`、`info_share.html`
四条候选路径全部返回 `302 → /portal.html`。因此本对照以政策**正文**
（§2.1–§2.7、附录定义 6/7）为基准。

## 2. 政策声明了什么

### 2.1 安全目的下的总括授权（§2.4「保障账户及交易安全」）

> 为履行保障电子商务交易安全的法定义务，提高您使用拼多多产品及服务的安全性，
> 保障您、其他用户或公众的人身财产安全，更好地预防网络漏洞、计算机病毒、网络
> 攻击及欺诈等风险，更准确地识别违反法律法规或拼多多综合平台相关协议、规则的
> 情况，我们可能会在使用时获取**账户信息、交易信息、设备信息（包括应用列表
> 信息、应用版本信息，Android ID、IMEI仅在6.63.0版本之前的拼多多APP、
> OAID/IDFA、HarmonyOS OAID、Mac 地址，设备网络环境，加速度、重力等传感器
> 信息）、日志信息**以及我们关联公司、合作伙伴取得您授权或依据法律共享的信息，
> 来判断您的账户及交易风险、验证身份、检测及防范安全事件，并依法采取必要的
> 记录、分析、审计、处置措施。

这是风控采集的**主要授权条款**：它以"保障账户及交易安全"为单一目的，
把设备信息、日志信息与应用列表打包授权。

### 2.2 附录对"设备信息"的定义（附录 6）

> 包括设备标识符（IMEI仅在6.63.0版本之前的拼多多APP、IDFA、Android ID、
> IMSI、OAID、HarmonyOS OAID 及其他设备识别信息）、MAC 地址、设备参数及系统
> 信息（设备类型、设备型号、**设备序列号**、操作系统及硬件相关信息）、应用信息
> （**应用崩溃信息**、通知开关状态、**应用安装列表**、应用程序版本及其他应用
> 相关信息）、设备网络环境信息（IP 地址，**WiFi 信息**，**基站信息**及其他网络
> 相关信息）、设备所在位置相关信息（包括您授权的 GPS 位置信息以及 WLAN 接入点、
> **蓝牙信息**）和**设备传感器信息**（包括加速度、磁场、重力、温度、光感、压力、
> 陀螺仪、距离、旋转矢量、记步传感器信息）。
> 具体以实际收集情况为准。

### 2.3 附录对"日志信息"的定义（附录 7）

> 包括搜索记录、点击查看记录、浏览记录、收藏记录、关注关系、分享历史、交易、
> 售后、发布信息，以及 IP 地址、浏览器类型、使用的语言、访问日期和时间、
> 电信运营商。
> 具体以实际收集情况为准。

### 2.4 剪贴板的限缩承诺（§2.6.3）

> 拼多多需要在本地访问您的剪贴板，读取其中包含的口令、分享码、链接，以实现跳转、
> 分享、活动联动等功能或服务。
> 拼多多仅在本地识别出剪贴板内容属于拼多多跳转、分享、活动联动等指令时才会将其
> 上传我们的服务器。除此之外，拼多多不会上传您剪贴板的其他信息至我们的服务器。

### 2.5 权限与附加服务的分离（§2.7）

> 若您不提供这些信息，您依然可以获得拼多多为您提供的基本功能，但您可能无法获得
> 这些附加信息给您带来的用户体验。
> …… 当您开启任一权限即代表您授权我们收集该项权限对应的个人信息来实现对应目标。
> 您一旦关闭任一权限即代表您取消了相应授权，我们将不再继续收集该项个人信息，
> 也无法为您实现该授权所对应目标。

即：§2.1–§2.4 的信息属于**基本功能**，§2.7 的权限属于**附加服务**，且
"关闭权限即不再采集该项"是政策给出的明确承诺。

## 3. 代码实际采集了什么

风控采集有**两条并行的组装面**，二者的字段集与序列化形式都不同。

### 3.1 面 A：设备信息 JSON（`ob2/b.e()`）

组装点是 `ob2/b.e(Context, Map)`（`ob2/b.java:472-591`，classes5.dex）。
它产出 `data_type = 1` 的 JSON：

- 方法体内 `.put(` 调用 **48 处**，其中字面量键 **43 个**；
- 另经 `h(String, JSONObject)` 门控的键 **4 个**：`basebandversion`、`cpu`、
  `locatin_mock`、`p29`；
- 嵌套 `f(JSONObject, Context, boolean)` 追加 **2 个**：`p30`、`p72`；
- 嵌套 `g(Context, JSONObject)` 的 `pm_class` / `pm_proxy` 已含在 43 个之内。

**面 A 可达键合计 49 个**。此外方法首部有一条 map 直通：

```java
for (Iterator it = map.keySet().iterator(); it.hasNext(); it = it) {
    String str2 = (String) it.next();
    jSONObject.put(str2, l.q(map, str2));
}
```

上游 `lb2/t.b(int, boolean, Map)` 注入 **17 个**键（`uid`、`cookie`、`pddid`、
`uuid`、`app_version`、`oaid`、`p46`、`commitid`、`device_id`、`imei_shown`、
`known_device`、`app_type`、`start_by_user`、`clipboard_md5`、
`instrumentation_chain`、`install_token`、`tmp_id`），`lb2/q.a/c/b` 再补
`app_name`、`app_size_info`、`data_type`、`platform`、`keys` 等。`lb2/f0.n()`
在返回后还追加 `platform`、`name`、`collect_begin_time`、`collect_end_time`
四个元字段。

字段按用途分类（面 A 的 49 个键）：

| 组 | 字段 | 采集实现 | 声明覆盖 |
| --- | --- | --- | --- |
| 设备标识 | `android_id`、`fingerprint`、`cert_list` | `fc2.c.z()`、`Build.FINGERPRINT`、APK 签名 X.509 摘要（`ob2/b.q()`） | 已列（Android ID / 设备识别信息） |
| 硬件参数 | `brand`、`model`、`board`、`device`、`product`、`manufactuer`、`display`、`prop`、`board_platform`、`flavor`、`basebandversion1/2`、`version`、`platform` | `Build.*` + `pb2.a.c()` 属性读取 | 已列（设备参数及系统信息） |
| 网络环境 | `net_type`、`wifi_list` | `pb2.a.b()`、`WifiManager` 扫描 | 已列（WiFi 信息） |
| 位置 | `locatin`、`latitude`、`longtitude`、`lac`、`ci`、`mcc`、`mnc`、`pci`、`psc`、`tac`、`nci`、`dbm`、`level`、`is_connected` | `ob2/b.y()`、`ob2/b.r()` 基站/小区扫描 | 已列（GPS / 基站信息） |
| 运营商 | `opertor_info`、`carrier_list` | `ob2/b.p()`、`TelephonyManager` | 已列（电信运营商） |
| 应用信息 | `lib_list`、`market_list`、`p29`、`p30`、`p72` | `ob2/b.z()`、`ob2/b.w()`、`SecureNative.ale()` | 部分（"应用安装列表"可涵盖 `lib_list`；见 §4.3） |
| 运行状态 | `process_id`、`activeTime`、`upTime`、`currentTime`、`data_type`、`foreground` | `Process.myPid()`、`SystemClock` | 未具名（"其他应用相关信息"可涵盖） |
| 无障碍 | `acc_server_list` | `ob2/b.o()`：`getInstalledAccessibilityServiceList()` + `getEnabledAccessibilityServiceList(-1)` | **未列举** |
| 运行进程 | `running_process` | `ob2/b.m()`：`getRunningServices(500)`，拼接 `process` 名 | **未列举** |
| 音量/系统 | `volume`（嵌套 JSON：`system`/`voiceCall`/`ring`/`alarm`/`music`/`notification`） | `ob2/b.A()` 音量通道读取 | **未列举** |
| 传感器 | `gyroscopeSensor`、`lightSensor`（嵌套 JSON：`name`/`vendor`） | `ob2/b.d()` 经 `SensorManager.getDefaultSensor` | 已列（陀螺仪 / 光感） |
| 环境/调试 | `allow_mock_location`、`is_from_mock_provider`、`pm_class`、`pm_proxy` | `Settings.Secure`、`PackageManager` 代理判定（`ob2/b.g()` 用 `ic2.c.r(...).h("mPM")` 反射取 `mPM` 并比较 `InvocationHandler` 类名） | 未具名（风控环境判定） |

### 3.2 面 B：查询串形式（`ob2/f.e()`）

第二条组装点是 `ob2/f.e(Context, String, Map)`（`ob2/f.java:85-223`），
产出的不是 JSON 而是 `k=v&` 拼接串，并以 `version=205` 标识格式版本。它的
29 个字面量键，加上 7 个被拼接的辅助方法键，合计 **37 个**：

| 组 | 键 | 采集实现 | 声明覆盖 |
| --- | --- | --- | --- |
| 设备与系统 | `kernelVersion`、`totalmemory`、`totalcapacity`、`availablecapacity`、`availablememory`、`brightness`、`simState`、`psno`、`target_version`、`machine_arch`、`input_device` | `pb2.a.*`、`StorageApi.u()`、`ActivityManager.MemoryInfo`、`Settings.System` | 部分（存储/内存非个人信息） |
| 设备标识 | `sn_1`、`sn_2`、`sn_3` | `ob2/f.o()`：`ro.serialno` 两条读取路径 + `fc2.c.v()` | 已列（**设备序列号**） |
| 网络 | `net_type`、`ip_list`、`connected_wifi`、`wifi_config` | `ob2/f.p()` 遍历 `NetworkInterface` 取非回环地址；`ob2/f.y()` 取 `WifiInfo` + `DhcpInfo`；`ob2/f.w()` | 已列（IP / WiFi / WLAN 接入点） |
| 环境判定 | `secure_lock`、`development_enabled`、`adb_enabled`、`instrumentation`、`imei_permission`、`fk_result`、`mediaDrm`、`arp_info`、`user_env2`、`foreground` | `KeyguardManager.isKeyguardSecure()`、`Settings.Global/Secure`、`ActivityThread.getInstrumentation()`、`SecureNative.n/a()` | 未具名（风控环境判定） |
| 用户设置 | `ringtone`、`wallpaper_md5`、`input_mathod`、`mDefaultInputMethodCls` | `ob2/f.s()` → `ob2/f.c()` 取 `RingtoneManager.getRingtone()` 摘要；`ob2/f.k()` 取壁纸位图→JPEG→MD5；`ob2/f.q()` 读 `default_input_method` | **未列举**（铃声/壁纸）；输入法未具名 |
| 标识 | `uuid`、`cid`、`cid_inner`、`process_id`、`user_phonename`、`currentTime`、`version` | `ob2/f.b()`、`ob2/f.l()` | 未具名 |

### 3.3 面 A 的服务端字段裁剪

`ob2/b.h(String, JSONObject)` 是每个可选字段的前置判定：

```java
public final boolean h(String str, JSONObject jSONObject) {
    if (!f.i(str)) { return true; }        // 不在黑名单 → 正常采集
    jSONObject.put(str, "");               // 在黑名单 → 只写空串
    return false;
}
```

`f.i(str)` 即 `ob2/f.i()` → `f82237a.contains(str)`，而 `f82237a` 由
`lb2/h0.b(List)` ← `ob2/f.A(List)` 赋值，其唯一调用链为：

```
com.xunmeng.pinduoduo.secure.c.a()
  → secure.b.a(JSONFormatUtils.fromJson2List(
        Configuration.getInstance().getConfiguration(
            "RiskControl.info_collect_blacklist", "[]"), String.class))
  → h0.b(list) → ob2.f.A(list)
```

即**采集哪些字段由服务端下发的 `RiskControl.info_collect_blacklist` 决定**，
且被剔除的字段不是不出现，而是以空串形式照常出现在上报体里——这解释了为什么
"字段存在"不等于"字段有值"。

### 3.4 面 B 的本地开关裁剪

面 B 的多数项还有一层 `ob2/f.m(String)` 开关，以及 `ab_secure_skip_*` 灰度开关
（默认均为 `false`，即默认开启采集）：

| 开关 | 作用点 | 默认 |
| --- | --- | --- |
| `ab_secure_skip_acclist_7630` | `ob2/b.o()` 无障碍列表 | `false` |
| `ab_secure_skip_runproc_7630` | `ob2/b.m()` 运行进程 | `false` |
| `ab_secure_skip_wifilist_7630` | `ob2/b.x()` WiFi 扫描列表 | `false` |
| `ab_secure_skip_location_7630` | `ob2/b.y()` 位置 | `false` |
| `ab_secure_skip_celllist_7630` | `ob2/b.r()` 基站列表 | `false` |
| `ab_secure_skip_wallpaper_7630` | `ob2/f.k()` 壁纸摘要 | `false` |
| `ab_secure_skip_ringrone_7630` | `ob2/f.s()` 铃声摘要 | `false` |
| `ab_secure_skip_iplist_7630` | `ob2/f.p()` 本机 IP 列表 | `false` |
| `ab_secure_skip_wificonfig_7630` | `ob2/f.w()` WiFi 配置 | `false` |
| `ab_secure_skip_connectwifi_7630` | `ob2/f.y()` 已连 WiFi 详情 | `false` |
| `ab_secure_skip_eue20_7630` | `com/xunmeng/pinduoduo/secure/c.java` | `false` |

这些开关的存在本身说明：无障碍列表、运行进程、壁纸、铃声、IP 列表等采集项
是**有意的、可灰度的能力**，而不是历史遗留代码。

### 3.5 拒绝全部危险权限后的采集面

设备实测：13 项 dangerous 权限中 12 项 `granted=false`、appops `mode=ignore`；
仅 `POST_NOTIFICATIONS` 为 true（见 [permissions.md](permissions.md) §2）。在此状态下：

- **仍被采集**（不需要任何危险权限）：
  `Build.*` 全家族、`android_id`、`cert_list`、`net_type`、`lib_list`、
  `market_list`、`running_process`（`getRunningServices` 自 API 21 起仅返回
  自身进程，但代码不校验调用者）、`acc_server_list`（无障碍列表读取不需权限）、
  铃声摘要、壁纸摘要（Android 13+ 且非华为/荣耀/小米时需 `READ_EXTERNAL_STORAGE`，
  故本设备上被 §3.4 的权限分支挡下）、Root/模拟器/多开判定、`/proc` 与 `/system`
  自省。
- **被正确阻断**：`wifi_list`（`WifiManager` 扫描）、`locatin`（`LocationManager`）、
  `cellinfo_list`（基站）三条路径在调用前经
  `com.xunmeng.pinduoduo.permission.scene_manager` 查询权限态，
  未授权时直接返回空串；OAID 采集在隐私闸门未通过时**不执行**而是注册
  `privacy_dialog_finish` 消息等待（`OaidInitTask.run`）。

即：**位置、WiFi 扫描、基站、OAID 的实际采集受权限与同意闸门约束，
而设备指纹、应用清单、无障碍服务列表、运行进程与 Root/模拟器判定
在零危险权限下仍然完整可采集。** 后者是"未告知或超范围收集"判断的核心事实基础。

### 3.6 同意前是否有采集

同意闸门是 `ac2.b.n()`（`ac2/b.java:99`）。判定链：

```
ac2.b.n()  → sc2.b.a()  → b92.a.b()  → ne1.s.b()
ne1.s.b()  → MMKV force_permission.getInt("privacy_passed_5200", 0) == 1
```

`ne1/c.l()` 在 `!ac2.b.n()` 时写入 `privacy_passed_5200 = 1` 并广播
`privacy_dialog_finish`。设备实测 `privacy_passed_5200 = 1`，即闸门已放行。

以下入口在采集/外发前直接以它为前置：

| 入口 | 闸门行为 |
| --- | --- |
| `lb2/f0.o(int, Map)` 设备信息上报 | `if (!ac2.b.n()) { L.i(41166); return; }` —— 不组装、不上报 |
| `com.aimi.android.common.http.i.b(...)` anti-token | `!ac2.b.n()` 时返回 `null`，不下发 `anti-token` 头 |
| `OaidInitTask.run` | 未通过时只注册 `privacy_dialog_finish`，不调用 OAID 初始化 |
| `TitanInitTask.run` | 经 `es1.a.a()`（= `ac2.b.n() \|\| MMKV pdd_market_activity.dialog_enable`）判定，未通过时等待 `privacy_dialog_finish` 才启动长连接 |
| `PreLogicCallbackImp.isPreEnable()` | 同上经 `es1.a.a()`；未通过时对请求做前置拦截，收到 `privacy_dialog_finish` 后放行 |
| `rb2/j.java` 敏感 getter 族 | 6 处 getter（`m`/`a`/`c`/`e`/`h`/`k`：WiFi 扫描、小区、WiFi 配置、`networkInfo.getExtraInfo()`、位置）在 `!ac2.b.n()` 时返回空/`null` |

`ac2.b.n()` 在全 APK 共 **52 处调用点、34 个文件**，覆盖通知、存储初始化、
免流、微信支付、图片搜索等——它是一个全局"隐私已通过"标志，而非风控专用。

因此**同意前的第一方风控数据外发与长连接建立均被显式阻断**。需要单独指出的两点：

- `lb2/f0.d()` 的 `context.getSystemService("clipboard")` 发生在启动阶段，
  未经 `ac2.b.n()` 判定，但**仅取服务引用、不读取内容**，不构成数据采集。
- 支付宝 SDK 的 `apmobilesecuritysdk` 初始化在支付/收银台路径触发，
  非启动即采集。

## 4. 逐项对照结论

### 4.1 落在声明范围内

政策附录 6/7 是一个**开放式枚举**，逐项命中以下实际采集：

- **设备标识**：`Android ID`、`OAID`、`MAC 地址`、`设备序列号`、`IMSI` —
  政策逐字列举，代码逐项读取（`sn_1/2/3` 对应"设备序列号"）。
- **应用信息**：`应用安装列表`、`应用程序版本`、`应用崩溃信息`、
  `通知开关状态` — 政策逐字列举，`lib_list` / `market_list` / 崩溃上报路径对应。
- **网络与位置**：`IP 地址`、`WiFi 信息`、`基站信息`、`WLAN 接入点`、
  `蓝牙信息`、`GPS 位置` — 政策逐字列举（`ip_list`、`connected_wifi`、
  `cellinfo_list`、`locatin`）。
- **传感器**：附录把加速度、磁场、重力、温度、光感、压力、陀螺仪、距离、
  旋转矢量、记步逐项列出，与 `gyroscopeSensor`/`lightSensor` 及
  `mi0/a`、`SensorManager` 的实际读取面一致。
- **日志**：搜索/浏览/收藏/关注/分享/交易/售后/发布 + IP、访问时间 —
  与 `mi0/a` 及埋点链路一致。

这一组的判定是：**声明与实现一致，且是逐项可核对的**。

### 4.2 在"总括授权"内但未被附录具名

`running_process`（运行进程）、`acc_server_list`（已安装+已启用的无障碍服务
列表）、音量通道（`volume` 下的 6 个流）、`market_list`（应用市场列表）、
以及 `secure_lock`/`adb_enabled`/`development_enabled`/`instrumentation`
等环境判定项，都不在附录 6/7 的任何一项里。

它们能落入的只有 §2.4 的"其他应用相关信息"与附录 6 结尾的
**"具体以实际收集情况为准"**。这条兜底句在政策中出现两次，
功能上把附录的封闭枚举变成开放式授权：只要服务端需要，任何"其他相关信息"
都在授权范围内，无需修订政策文本。

`running_process` 与 `acc_server_list` 是本组中信息量最高的两项：
前者还原设备上正在运行的服务进程名，后者还原辅助功能框架下已启用的服务。
两者都是**环境指纹**而非业务必需数据。

### 4.3 未在政策中出现的采集项

对政策全文（正文 + 附录）做关键词计数，命中数为 0 的项：

| 采集项 | 实现位置 | 政策命中数 |
| --- | --- | ---: |
| 已启用无障碍服务列表 | `ob2/b.o()` | `无障碍`=0、`可访问性`=0 |
| 运行中进程名 | `ob2/b.m()` | `进程`=0 |
| 默认铃声摘要 | `ob2/f.c()` ← `ob2/f.s()` | `铃声`=0 |
| 壁纸摘要 | `ob2/f.k()`（`WallpaperManager` 经 `jc2/a.b()`） | `壁纸`=0 |
| 应用市场列表 | `ob2/b.w()`（`MAIN` + `APP_MARKET`） | `应用市场`=0、`应用商店`=0 |
| Root/模拟器/多开判定 | `mi0/a.java`、`libdyncommon.so` | `root`/`模拟器`/`多开` 均未定义 |

其中"应用市场列表"可辩称为"应用安装列表"的子集；Root/模拟器/多开属于对
**本机环境**的判定而非用户数据采集。真正的缺口是前四项：
无障碍服务、运行进程、铃声、壁纸读取的都是**用户个性化设置**，
政策文本没有任何对应的措辞或兜底定义覆盖，且 §3.4 显示这四项都有专属的
服务端灰度开关。

### 4.4 剪贴板的实际行为与承诺

政策 §2.6.3 的承诺是"仅在本地识别出剪贴板内容属于拼多多跳转、分享、活动联动
等指令时才会将其上传"。代码侧存在**两条独立路径**，结论不同。

#### 4.4.1 路径一：正则识别 + 位掩码上报（与承诺一致）

`com.xunmeng.pinduoduo.secure.i_secure_logic.a()` 在初始化时注册剪贴板监听：

```java
HashMap map = (HashMap) JSONFormatUtils.c(
    Configuration.getInstance().getConfiguration(
        "config_monitor_clipboard_info_80002",
        "{\n\"yangkeduo\":1,\n\"pinduoduo\":2,\n\"goods_id\":4,\n\"mall_id\":8\n}"),
    new TypeToken<HashMap<String, Integer>>() {...});
for (Map.Entry entry : map.entrySet()) {
    map2.put(Pattern.compile((String) entry.getKey()), (Integer) entry.getValue());
}
dVar.f107086b = map2;
h.d(new a(), dVar, "com.xunmeng.pinduoduo.secure.MonitorClipInfo");
```

- `h.d(...)` 是 `addClipDataChangedListenerRiskCheck`，内部调用 `w21.j.c(dVar)`
  → `f107087c = false`、`f107088d = false`。
- 过滤链在 `z21/k.m(aVar, dVar)` 中按序执行，其中 `a31.g` 用上表正则逐个匹配：

  ```java
  for (Map.Entry entry : map.entrySet()) {
      Pattern pattern = (Pattern) entry.getKey();
      Integer num = (Integer) entry.getValue();
      if (pattern != null && num != null && pattern.matcher(strH).find()) {
          iIntValue |= num.intValue();
      }
  }
  aVar.f107078h = iIntValue;
  ```

- 回调 `i_secure_logic$a.a(w21.a)` **只在 `f107078h != 0` 时上报**，
  且上报体只有位掩码：

  ```java
  if (aVar == null || (i14 = aVar.f107078h) == 0) { L.i(41161); }
  else { ThreadPool...computeTask(..., new RunnableC0580a(i14)); }
  // RunnableC0580a.run(): JSONObject.put("info", i14); o0.f().d(8, json, true);
  ```

**该路径与政策承诺一致**：只有内容命中 `yangkeduo`/`pinduoduo`/`goods_id`/`mall_id`
四个服务端可配正则之一时才上报，且上报的是位掩码整数，不是内容。默认正则表由
服务端配置项 `config_monitor_clipboard_info_80002` 下发，客户端内置同一份默认值。

#### 4.4.2 路径二：全文摘要进入设备信息 map（不受正则约束）

设备信息组装链 `lb2/t.b(int, boolean, Map)` 走的是另一组 API：

```java
w21.c cVarK = w21.h.k(w21.h.f(), new w21.d(),
                      "com.xunmeng.pinduoduo.secure.LocalInfoCollect");
String str2 = (cVarK == null || (aVarA = cVarK.a()) == null) ? null : aVarA.f107076f;
if (TextUtils.isEmpty(str2)) {
    map2.put("clipboard_md5", "");
} else {
    map2.put("clipboard_md5", pb2.a.q(pb2.f.a(str2)));   // MD5(剪贴板文本)
}
```

关键在于 `w21.h.k(...)` 内部调用 `w21.j.a(dVar)`，置
`f107087c = true`、`f107088d = false`；而 `a31.e` 据此**保留**
`f107076f`、只清空明文 `f107072b`：

```java
public w21.a c(w21.a aVar) {
    if (aVar != null) {
        if (!this.f332a) { aVar.f107076f = null; }   // f332a = f107087c = true → 不清
        if (!this.f333b) { aVar.l(""); }             // f333b = f107088d = false → 清明文
    }
    return aVar;
}
```

`f107076f` 由过滤链首环 `a31.b.d()` 赋值：

```java
String strH = aVar.h();                       // 剪贴板全文
String strD = c.d(strH);                      // 本地/native 识别（SecureNative.y = ecn）
String strDigest = MD5Utils.digest(strH);     // 全文 MD5
if (TextUtils.isEmpty(strD) || TextUtils.isEmpty(strDigest)) { return false; }
aVar.l(strD); aVar.f107076f = strDigest; aVar.f107077g = l.J(strH);
```

即 `clipboard_md5` 是**剪贴板全文的 MD5**，且该赋值不经过
`config_monitor_clipboard_info_80002` 的正则判定。摘要本身不可逆，
但它是**可枚举比对**的（短口令/分享码的 MD5 可被穷举反查）。

`pb2/f.a(String)` 为 `MessageDigest("MD5")` 后大写十六进制化
（`pb2/f.java:33-38` → `b()` → `c()`），`pb2.a.q()` 为不可逆封装。
该 map 随后进入 `/project/meta_info` 上报（见
[transfer.md](transfer.md)）。

#### 4.4.3 其余已证实事实

- 剪贴板读取入口统一收敛到 `rb2/s.java` 的 `b(ClipboardManager)`
  （`getPrimaryClip()`），经 `wb2/b` 接口下发，便于统一 gating。
- `w82/a.java` 的 API 映射表把 `access_clipboard`（`map` 键 `13`）、
  `access_clipboard_api`（`16`）、`getText`（`60`）、`hasText`（`64`）、
  `getPrimaryClip`（`58`）、`getPrimaryClipDescription`（`59`）、
  `setPrimaryClip`（`63`）、`clearPrimaryClip`（`61`）、
  `addPrimaryClipChangedListener`（`66`）作为独立条目登记。
- 应用在 appops 上把 `READ_CLIPBOARD` / `WRITE_CLIPBOARD` 登记为
  `mode=foreground`，即只在前台可读。
- `lb2/f0.d()` 在启动时执行 `context.getSystemService("clipboard")`，
  **取服务引用而不读内容**。
- 明文 `f107072b` 在 `LocalInfoCollect` 路径上被清空（`a31.e` 的
  `f333b = false` 分支），因此**明文不上报**这一点成立。

#### 4.4.4 判定

- **与承诺一致的**：正则识别路径（§4.4.1）确实做到了"仅命中拼多多指令特征时
  才上报"，且上报位掩码而非内容——这是政策 §2.6.3 措辞的直接实现。
- **与承诺存在张力的**：`clipboard_md5`（§4.4.2）是对剪贴板**全文**计算的
  MD5，不受正则判定约束，随后随设备信息上报。政策承诺的是"不会上传您剪贴板的
  其他信息"，而全文摘要属于"其他信息"的派生值。
- **未证实的**：`c.d(strH)` → `SecureNative.y` → native `ecn`
  （`libpdd_secure.so`）的返回值语义只到"字节 → 字符串编码"
  （见 [algorithm.md](algorithm.md) 的导出映射表），本次未断言它对
  "是否为拼多多指令"的判定结果，也未取得该字段的网络侧对照。

## 5. 评估结论

1. **声明覆盖度高但不封闭。** 政策 §2.4 + 附录 6/7 逐字列举的项与代码实现
   基本一一对应（§4.1），属于可核对的披露。但附录两处
   "具体以实际收集情况为准"把封闭枚举改为开放式授权，使 §4.2 的采集
   （运行进程、无障碍服务、音量通道、应用市场列表、环境判定）在文本上"合规"
   而无需修订政策。

2. **存在四项未在政策中出现、且不属用户数据最小必要范围的采集**：
   已启用无障碍服务列表、运行中进程名、默认铃声摘要、壁纸摘要（§4.3）。
   它们读取用户个性化设置，与风控目的（设备环境判定）的关联弱于
   设备标识或应用清单，且政策无任何对应措辞。其中无障碍与进程两项在
   服务端有可灰度的剥离开关（`ab_secure_skip_acclist_7630`、
   `ab_secure_skip_runproc_7630`，均默认 `false`），说明这两项采集
   是**有意的、可开关的**能力，而非遗留代码。

3. **零危险权限下仍可完成大部分风控指纹采集**（§3.5）。位置、WiFi 扫描、
   基站、OAID 受权限闸门约束，但设备指纹、应用清单、无障碍列表、运行进程、
   Root/模拟器/多开判定不受影响。这使 §2.7"关闭权限即不再采集该项信息"
   的承诺只对**权限门控的那部分**成立。

4. **同意前无第一方数据外发**（§3.6），闸门 `ac2.b.n()` 在六个独立入口生效。

5. **字段可见性受服务端控制**（§3.3）：`RiskControl.info_collect_blacklist`
   可逐字段把采集结果替换为空串，因此"上报体里出现某字段"不能单独作为
   "该字段被实际收集"的证据；本报告的面 A 字段表按**代码可达性**给出，
   不按运行值。

6. **剪贴板存在两条路径，其中一条与承诺存在张力**（§4.4）：
   正则识别路径（`config_monitor_clipboard_info_80002` + `a31.g` 位掩码）
   只在命中拼多多指令特征时上报整数掩码，与 §2.6.3 一致；
   但设备信息路径的 `clipboard_md5` 是对**剪贴板全文**计算的 MD5，
   不受该正则约束，随后随 `/project/meta_info` 上报。全文摘要属政策
   "不会上传您剪贴板的其他信息"所指范围的派生值。

**边界声明**：以上 1–6 的判断基于 APK 静态逆向与同一设备的只读运行时快照。
服务端侧的留存、关联、画像与最终用途不在客户端可证范围内；本报告不对其作任何
断言。政策文本的"更新日期 2025年6月30日"对应 V4.1.1，若服务端后续下发新版本，
本对照需按新文本重做。

## 6. 复现方式

```bash
# 政策原文（需经可访问的出口网络）
curl -sSL -A 'Mozilla/5.0 (Linux; Android 14)' \
  https://mobile.yangkeduo.com/private_policy.html -o private_policy.html
sha256sum private_policy.html   # a71b4edec66adf2d1a4b0bdbbfdd3e08042163ed28cbe90ce2c1872c052f8b65

# 面 A 采集点（静态）
grep -c '\.put(' jadx-out/sources/ob2/b.java            # 118 处（全文件）
sed -n '472,591p' jadx-out/sources/ob2/b.java           # e() 本体，48 处 put

# 面 B 采集点（静态）
sed -n '85,223p' jadx-out/sources/ob2/f.java            # e() 查询串组装

# 字段裁剪开关（静态）
grep -rn 'info_collect_blacklist' jadx-out/sources/     # 服务端字段黑名单
grep -rn 'ab_secure_skip_' jadx-out/sources/            # 11 个采集剥离开关

# 权限与同意态（只读，设备侧）
adb shell dumpsys package com.xunmeng.pinduoduo
adb shell dumpsys appops
python3 tools/vita_registry.py mmkv splash            # 需先从设备取出该文件
python3 tools/vita_registry.py mmkv force_permission
```

本文件不公开任何真实设备标识、账号信息、位置、WiFi 列表或行值。
