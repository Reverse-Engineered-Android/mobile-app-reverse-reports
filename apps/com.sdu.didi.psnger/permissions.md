# 权限与导出面

## 1. Manifest 统计

| 项目 | 数量/值 |
|---|---:|
| `android.permission.*` 声明 | 40 |
| 非 `android.permission` 条目（2 个特殊标识 + 23 个厂商/自有项） | 25 |
| minSdk / targetSdk | 24 / 35 |
| 显式导出组件 | 72 |
| 其中未加 `android:permission` 约束 | 62 |

统计来自 APK 解码后的 manifest；厂商权限与滴滴自有组件分开计数。

## 2. 全部声明权限

```text
android.hardware.nfc.hce
android.permision.TURN_SCREEN_ON
android.permission.ACCESS_BACKGROUND_LOCATION
android.permission.ACCESS_COARSE_LOCATION
android.permission.ACCESS_FINE_LOCATION
android.permission.ACCESS_LOCATION_EXTRA_COMMANDS
android.permission.ACCESS_NETWORK_STATE
android.permission.ACCESS_WIFI_STATE
android.permission.BLUETOOTH
android.permission.BLUETOOTH_ADMIN
android.permission.BLUETOOTH_ADVERTISE
android.permission.BLUETOOTH_CONNECT
android.permission.BLUETOOTH_SCAN
android.permission.CAMERA
android.permission.CHANGE_NETWORK_STATE
android.permission.CHANGE_WIFI_STATE
android.permission.DETECT_SCREEN_CAPTURE
android.permission.FLASHLIGHT
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
android.permission.FOREGROUND_SERVICE_LOCATION
android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
android.permission.FOREGROUND_SERVICE_MICROPHONE
android.permission.INTERNET
android.permission.MODIFY_AUDIO_SETTINGS
android.permission.MOUNT_UNMOUNT_FILESYSTEMS
android.permission.NFC
android.permission.POST_NOTIFICATIONS
android.permission.QUERY_ALL_PACKAGES
android.permission.READ_EXTERNAL_STORAGE
android.permission.READ_LOGS
android.permission.READ_MEDIA_VISUAL_USER_SELECTED
android.permission.RECORD_AUDIO
android.permission.REQUEST_INSTALL_PACKAGES
android.permission.SYSTEM_ALERT_WINDOW
android.permission.SYSTEM_OVERLAY_WINDOW
android.permission.TURN_SCREEN_ON
android.permission.USE_FINGERPRINT
android.permission.VIBRATE
android.permission.WAKE_LOCK
android.permission.WRITE_CALENDAR
android.permission.WRITE_EXTERNAL_STORAGE
```

厂商与自有权限：

```text
com.android.launcher.permission.INSTALL_SHORTCUT
com.android.launcher.permission.UNINSTALL_SHORTCUT
com.android.permission.GET_INSTALLED_APPS
com.asus.msa.SupplementaryDID.ACCESS
com.didi.passenger.sdk.login.permission.broadcast.com.sdu.didi.psnger
com.google.android.gms.permission.AD_ID
com.hihonor.brain.permission.KIT_SERVICE_ACCESS
com.hihonor.push.permission.READ_PUSH_NOTIFICATION_INFO
com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA
com.nemu.oaid.permission.read
com.nemu.oaid.permission.write
com.samsung.android.app.sreminder.permission.NOW_BAR_PROVIDER
com.sdu.didi.psnger.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
com.sdu.didi.psnger.permission.MIPUSH_RECEIVE
com.sdu.didi.psnger.permission.PROCESS_PUSH_MSG
com.sdu.didi.psnger.permission.PUSH_PROVIDER
com.vivo.aiengine.permission.BIND_INTENTION_PLUS_SERVICE
com.vivo.identifier.permission.OAID_STATE_DIALOG
com.xiaomi.security.permission.ACCESS_XSOF
freemme.permission.msa
freemme.permission.msa.SECURITY_ACCESS
oplus.permission.settings.LAUNCH_FOR_EXPORT
org.simalliance.openmobileapi.SMARTCARD
```

## 3. 分类判定

### 3.1 定位

`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`ACCESS_BACKGROUND_LOCATION`、
`ACCESS_LOCATION_EXTRA_COMMANDS`。

打车是强定位业务，前两项为功能必需。**后台定位需要说明**：声明存在，且
`FOREGROUND_SERVICE_LOCATION` 同时声明，说明存在行程中持续上报的设计。设备端
`location_info.db.location` 表 66 行、只含 `_id, ts, type, byte_data BLOB`，
坐标以 BLOB 落盘；`track_upload_sdk2.db.tbl_track_nodes` 0 行。静态无法证明
后台采集的实际时长与频率，取决于运行时授权与行程状态。

### 3.2 蓝牙

`BLUETOOTH`、`BLUETOOTH_ADMIN`、`BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT`、
`BLUETOOTH_ADVERTISE`。

用途有两处：一是安全盾的周边设备采集
（`/api/guard/deviceConnect/reportV2`，字段含 `deviceName`、`bondState`、
`rssi`，见 [transfer.md](transfer.md) §2.4）；二是 WSG 的
`collectBluetoothAsync`（`SecurityManager.java:144-146`）。**两者都需要蓝牙
扫描权限，且采集结果会上报服务端**，这是本应用最需要用户知晓的一类采集。
`BLUETOOTH_ADVERTISE` 用于把手机作为可被发现设备，常见于紧急联系人场景。

### 3.3 存储

`READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、
`READ_MEDIA_VISUAL_USER_SELECTED`、`MOUNT_UNMOUNT_FILESYSTEMS`。

前两项在 targetSdk 35 下对 Android 13+ 已基本失效，属历史残留声明。
`MOUNT_UNMOUNT_FILESYSTEMS` 是系统级权限，普通应用无法获得，**属过度声明**，
应移除。`READ_MEDIA_VISUAL_USER_SELECTED` 是 Android 14+ 的部分媒体选择，
用于一键报警选图。

### 3.4 应用可见性

`QUERY_ALL_PACKAGES`（targetSdk 35）、`com.android.permission.GET_INSTALLED_APPS`。

调用面全部为可用性探测：

```text
com/didi/sdk/util/SystemUtil.java:133            queryIntentActivities(intent, 0)
com/didi/sdk/app/navigation/interceptor/RouterInterceptor.java:111
com/didi/sdk/fusionbridge/module/NavModule.java:73      (导航 App 是否存在)
com/didi/dimina/container/ui/dialog/k.java:157,164
com/didichuxing/unifybridge/core/permission/overlay/setting/LSettingPage.java:27
```

全部使用 `queryIntentActivities` 并只判断 `.size() > 0`，**没有
`getInstalledPackages()` / `getInstalledApplications()` 的全量枚举调用**。因此
不能认定为“上传完整应用列表”，但 `QUERY_ALL_PACKAGES` 本身允许任意查询，
属权限粒度过宽。

### 3.5 日历

`WRITE_CALENDAR`。用途见 AI 叫车功能面：`TripCardViewV2.java:2636` 用
`llm_calendar_enable` 开关控制日历写入，把行程加入日历。**属用户主动触发的
功能**，非后台采集。

### 3.6 读取日志

`READ_LOGS`。该权限自 Android 4.1 起仅对系统应用或通过
`adb pm grant` 授权的应用生效，普通安装无法获得。静态代码中与该权限直接对应
的采集面是崩溃模块的 logcat 抓取
（`com/didichuxing/tools/nativecrash/BreakpadStateTracker.java`，字段
`logcat_status`、`anr_logcat_status`、`anr_logcat_source`、
`native_logcat_status`、`native_logcat_bytes`）。**属过度声明**：崩溃诊断可用
应用自身日志实现，无需 `READ_LOGS`。

### 3.7 安装与悬浮窗

`REQUEST_INSTALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`SYSTEM_OVERLAY_WINDOW`。

`REQUEST_INSTALL_PACKAGES` 的三个调用点：

```java
com/didichuxing/unifybridge/core/permission/source/Source.java:86-88
    canRequestPackageInstalls()
com/didichuxing/unifybridge/core/permission/install/ORequest.java:31,41
y82/a.java:157-158
    ActivityCompat.requestPermissions(activity,
        new String[]{"android.permission.REQUEST_INSTALL_PACKAGES"}, 6666);
```

`Source.java:88` 用 `getPackageManager().canRequestPackageInstalls()` 查询系统
开关，`ORequest` 未获授权即返回。**调用链只服务自身升级安装**，安装包来源与
签名由系统 `PackageInstaller` 校验，客户端无法绕过。

`SYSTEM_ALERT_WINDOW` 用于悬浮窗（`com/didi/sdk/floatwindow`、
`com/didichuxing/travel/thirdparty/floatingwindow`），是行程中悬浮球功能所需。
`SYSTEM_OVERLAY_WINDOW` 是同一能力的旧名，属冗余声明。

### 3.8 截屏检测

`DETECT_SCREEN_CAPTURE`（API 34+）。用于安全盾与支付页的防截屏提示，属防御性
权限，不采集内容。

### 3.9 传感器与音频

`CAMERA`、`RECORD_AUDIO`、`MODIFY_AUDIO_SETTINGS`、`VIBRATE`、
`FOREGROUND_SERVICE_MICROPHONE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`。

相机用于扫码、人脸核验与一键报警拍照；录音用于语音叫车、语音报警与
`safetyguard` 的现场录音。`audio_record_2.record_result` 表（0 行）证明录音
有明确的上传契约（`uploadUrl`、`signKey` 由服务端下发）。

### 3.10 指纹

`USE_FINGERPRINT`。用于支付前生物验证。同一能力还有
`cn/passguard` 的支付键盘与 `PassGuardEncrypt.HmacSM3`（SM3）。

## 4. 导出组件

72 个导出组件，按类型分布：

### 4.1 Activity（53 个）

- **入口**：`com.didi.sdk.app.MainActivity`、
  `com.didi.sdk.app.launch.splash.SplashActivity`、
  `com.didi.sdk.app.scheme.SchemeDispatcherActivity`（onetravel deeplink 总入口）、
  `com.didi.sdk.app.popup.PopupActivity`。
- **支付**：`com.didi.pay.base.SchemeActivity`、
  `com.didi.unifiedPay.component.activity.SchemeActivity`、
  `com.didi.universal.pay.sdk.method.bankPay.SchemeActivity`、
  `com.didi.pay.web.WebProxyActivity`、
  `com.didi.universal.pay.sdk.web.WebProxyActivity`、
  `com.didi.payment.wallet.china.web.WalletWebProxyActivity`、
  `com.didi.pay.activity.Hummer{Prepay,NewCashier,GeneralPay,Pay,OneCarExternal,OnecarPay}Activity`、
  `com.didi.pay.drn.DRN{Prepay,Travel}Activity`、
  `com.didi.pay.activity.{UniversalDispatchActivity,AlipaySignBackActivity}`、
  `com.didi.pay.activity.CashierTest{Config,OpenCashier}Activity`。
- **信用卡/钱包**：`com.didi.payment.creditcard.china.view.activity.*`
  （`CreditCardAddActivity`、`CreditCardManagerActivity`、
  `CreditCardAddSuccessActivity`、`CreditCardMpgs3DActivity`、
  `web.PayWebWithLocalDataActivity`）、
  `com.didi.payment.wallet.china.*`、`com.xiaojukeji.finance.dcep.DcepPayEntryActivity`。
- **支付渠道回调**：`com.alipay.sdk.app.{AlipayResultActivity,PayResultActivity}`、
  `com.didi.sdk.pay.{QQPayEntryActivity,WXPayEntryActivity}`、
  `com.didi.payment.thirdpay.channel.qq.QQPayEntryActivity`、
  `com.didi.payment.thirdpay.channel.wx.{WXPayEntryActivity,WXEntryActivity}`、
  `com.didi.payment.paymethod.sign.channel.{paypay,paypal}.activity.*`。
- **业务**：`com.didi.bike.ui.activity.scan.BikeScanActivity`、
  `com.sdk.address.address.confirm.poiconfirm.PoiSearchConfirmActivity`、
  `com.didi.voipsdk.ui.page.VoipCallActivity`。
- **推送点击**：`com.didi.sdk.push.vivo.VivoPushClickHandleActivity`、
  `com.xiaomi.mipush.sdk.NotificationClickedActivity`、
  `com.didi.sdk.push.oppo.OppoPushIntentReceiver`。

**风险判定**：`CashierTestConfigActivity` 与 `CashierTestOpenCashierActivity`
是**测试用收银台入口，被导出且不带权限约束**。这类组件在正式包中导出，允许
任意应用直接打开测试收银界面。是否可被用于绕过真实支付流程取决于这些页面的
内部校验；静态代码未发现它们校验调用方包名。

### 4.2 activity-alias（5 个）

```text
com.sdu.didi.psnger.wxapi.QQPayEntryActivity
com.sdu.didi.psnger.wxapi.WXPayEntryActivity
com.didi.sdk.app.launch.splash.IconChangeAliasId1
com.didi.sdk.app.launch.splash.IconChangeAliasId2
com.sdu.didi.psnger.wxapi.WXEntryActivity
```

`wxapi.*` 是微信回调入口（微信要求固定包名路径，必须导出）。
`IconChangeAliasId1/2` 是图标切换，导出后可被第三方切换桌面图标。

### 4.3 service（8 个）

```text
com.didi.sdk.fence.huawei.HuaweiFenceService          perm=com.huawei.hms.location.permission.PENDINGINTENT
com.vivo.push.sdk.service.CommandClientService        perm=com.push.permission.UPSTAGESERVICE
com.didi.sdk.push.oppo.OppoDataMessageCallbackService perm=com.heytap.mcs.permission.SEND_PUSH_MESSAGE
com.didi.sdk.push.oppo.OppoCompatibleDataMessageCallbackService perm=com.coloros.mcs.permission.SEND_MCS_MESSAGE
com.didichuxing.travel.support.ThirdLiveService       perm=None
androidx.work.impl.background.systemjob.SystemJobService perm=BIND_JOB_SERVICE
com.huawei.hms.support.api.push.service.HmsMsgService perm=None
com.xiaomi.mipush.sdk.PushMessageHandler              perm=xiaomi.xmsf.permission.MIPUSH_RECEIVE
```

除 `ThirdLiveService` 与 `HmsMsgService` 外均带权限约束。

### 4.4 receiver（5 个）

```text
com.didi.sdk.push.mi.MiPushReceiver                   perm=None
androidx.work.impl.diagnostics.DiagnosticsReceiver     perm=android.permission.DUMP
androidx.profileinstaller.ProfileInstallReceiver       perm=android.permission.DUMP
com.huawei.hms.support.api.push.PushMsgReceiver        perm=com.sdu.didi.psnger.permission.PROCESS_PUSH_MSG
com.huawei.hms.support.api.push.PushReceiver           perm=com.sdu.didi.psnger.permission.PROCESS_PUSH_MSG
```

`MiPushReceiver` 无权限约束，是小米推送 SDK 的强制约定；同名语义的 OPPO 入口
`OppoPushIntentReceiver` 在 manifest 中声明为 Activity，计入 §4.1。

### 4.5 provider（1 个）

`com.huawei.hms.support.api.push.PushProvider`，无权限约束。

## 5. 越权判定

| 场景 | 结论 |
|---|---|
| 第三方应用拉起滴滴页面 | 可（53 个导出 Activity） |
| 第三方应用读取滴滴数据 | 无直接导出 provider（唯一 provider 是华为推送） |
| 第三方应用触发支付 | 可拉起收银台页面，但完成支付需服务端订单与凭据 |
| 第三方应用伪造支付回调 | `wxapi.*` 与 `com.alipay.sdk.app.*ResultActivity` 导出，但回调结果需服务端验签 |
| 测试收银台直达 | `CashierTest*Activity` 导出，属配置缺陷 |
| 系统级权限滥用 | `MOUNT_UNMOUNT_FILESYSTEMS`、`READ_LOGS` 无法获得，无实际风险 |

## 6. 提权判定

### 6.1 结论

**未发现系统 UID 提权闭环。** 全量枚举 20 个直接
`Runtime.getRuntime().exec(...)` 调用点与 2 个经局部变量间接调用的
`runtime.exec(...)` 调用点，共 22 个执行点；另有 3 个 `ProcessBuilder` 执行点。
没有任何一处执行 `su`、`sh -c`、交互式特权 shell、`pm grant` 或
`grantRuntimePermission`。多数命令使用固定字符串或字符串数组；动态部分仅限
属性名、主机名、ping/logcat 参数和任务定义的探测命令，按参数数组或
`Runtime.exec(String)` 的程序化分词执行，**不进入 shell 解析**。唯一的
交互 shell 面是 `v8` 向 `sh` stdin 写入固定调用方字面量，见 §6.4。

### 6.2 全部 shell 调用点

| 位置 | 命令 | 用途 |
|---|---|---|
| `com/megvii/lv5/v8.java:46` | `sh`（交互 shell） | 见 §6.4 |
| `com/megvii/lv5/w8.java:256` | `cat /proc/cpuinfo` | CPU 信息 |
| `com/megvii/lv5/f9.java:311` | `getprop` | 属性读取 |
| `com/megvii/lv5/y8.java:24` | `service list` | 系统服务枚举 |
| `com/didichuxing/mlcp/drtc/utils/NetworkHelper.java:145` | `ping -c 3 -i 0.3 <host>`（默认 `drtc-openapi.xiaojukeji.com`） | 网络 QoS 探测 |
| `com/didi/thanos/weex/util/RomUtil.java:104` | `getprop <属性名>` | ROM 属性读取 |
| `com/didi/dimina/container/ui/dialog/k.java:100` | `getprop ro.miui.ui.version.name` | MIUI 判定 |
| `com/didi/dimina/v8/LibraryLoader.java:25` | `chmod <下载库路径>` | 校验下载库可执行位 |
| `com/didi/sfcar...sfcwaitpsgpay/d.java:49` | `getprop ro.miui.ui.version.name` | MIUI 判定 |
| `com.didi.security.utils.AppUtils.java:330` | `cat /proc/self/mountinfo` | 挂载点探测 |
| `com.didi.security.utils.AppUtils.java:1100` | `cmd(String[])` 通用封装 | 见 §6.5 |
| `com.didi.security.utils.ServerListCollector.java:48` | `service list` | 系统服务枚举 |
| `a42/l.java:40` | `chmod 777 <apk 路径>` | 校验下载 APK 可执行位 |
| `z52/c.java:45` | `ps` | 进程读取 |
| `x6/g.java:90` | `top -n 1` | CPU 占用 |
| `i52/a.java:149` | `top -m 10 -t -s cpu -n 1` | CPU 占用 |
| `vc2/a.java:95` | `/system/bin/getprop metro.host` | Metro 调试宿主探测 |
| `ro0/a.java:41` | `exec(b())` | 见 §6.6 |
| `a62/i.java:75` | `logcat -d -v threadtime -t <N> -b <buffer>` | 日志读取 |
| `h62/j.java:162` | `ping -c %d -w %d [-t %d] <host>` | 质量诊断 |
| `z82/d.java:61` | `ping -c 5 -w 10 <URL 主机名>` | 升级链路探测 |
| `org/apache/commons/io/FileSystemUtils.java:135` | `df`/`du` | 磁盘统计（库自带） |

这些调用点中没有 `su`、`sh -c` 或 `pm grant`。`xcrash/k.java:24`、
`com/megvii/lv5/f9.java:1434`、`com/didichuxing/tools/nativecrash/core/RuntimeHeaderFactory.java:110`、
`com/mobile/auth/gatewayauth/utils/security/CheckRoot.java:25`、
`com/alipay/sdk/m/v/b.java:19`、`com/alipay/sdk/m/d0/e.java:24`、
`e82/a.java:21` 等位置只是检查 `/system/xbin/su`、`/system/bin/su` 等路径
是否存在，属于 root 检测，不执行这些文件。

### 6.3 `ProcessBuilder` 执行点

| 位置 | 命令 | 用途/参数边界 |
|---|---|---|
| `xcrash/k.java:215` | `/system/bin/logcat -b <buffer> -d -v threadtime -t <N> --pid <pid> *:<prio>` | 崩溃日志收集；参数按数组执行 |
| `com/didi/security/utils/AppUtils.java:1888` | `/system/bin/logcat -b main -d -v threadtime -t <SUCCESS> --pid <pid>` | 指定进程日志；`--pid` 始终是数组项 |
| `dw/v.java:59` | `top -n 1 [-p <pid>]` | CPU 快照；Android 8+ 追加当前进程 PID |

`ProcessBuilder` 没有调用 `/system/bin/sh`，也没有拼接 `sh -c`；三处均以
`String[]` 形式启动可执行文件。

### 6.4 `sh` 交互执行的输入边界

`com/megvii/lv5/v8.java:40-55`：

```java
public String a(String str) throws Throwable {
    processExec = Runtime.getRuntime().exec("sh");        // :46
    bufferedOutputStream = new BufferedOutputStream(processExec.getOutputStream());
    ...
    bufferedOutputStream.write(str.getBytes());           // :52
    bufferedOutputStream.write(10);
    bufferedOutputStream.flush();
```

这是一个把 `str` 写入 `sh` 标准输入的**命令代理**，构成潜在的任意命令执行面。
判定其是否可达恶意输入的关键是调用方：

```text
com/megvii/lv5/v8.java:11     public static final v8 f75416a = new v8();
com/megvii/lv5/w8.java:46     v8.a.f75416a.a("/system/bin/which su")   // 固定字面量
com/megvii/lv5/w8.java:48     v8.a.f75416a.a("which su")              // 固定字面量
com/megvii/lv5/w8.java:68     v8.a.f75416a.a("pm list package -3")    // 固定字面量
com/megvii/lv5/w8.java:139,155 v8.a.f75416a.a("cat /proc/self/cgroup") // 固定字面量
com/megvii/lv5/w8.java:186    v8.a.f75416a.a("ps")                    // 固定字面量
```

`v8` 的构造是单例（`:11`），唯一的调用者 `w8` 全部传入**编译期字面量**，
没有任何路径把设备数据、服务端下发内容或用户输入拼进 `str`。因此该面在实践中
不可被外部驱动，但它的存在意味着一旦 `w8` 未来接入动态参数即成为提权风险面。
`w8` 属于旷视（Megvii）活体检测 SDK，服务于人脸核验。

`com/megvii/lv5/f9.java:86` 同时给出该 SDK 的风险特征表：

```java
{"frida", "bytehook", "shadowhook", "hookfunc", "substrate",
 "xposed", "lsposed", "edxposed", "sandhook", "yahfa", "whale"}
```

`:80-84` 是 `getprop` 读取的属性名与 `build.prop` 路径表，`:88` 是
`{5555, 27042}` 两个端口（ADB 与 Frida 默认端口），`:90` 是
`{"15B3","6972"}` 两个 USB VID，`:92` 是 25 个自动化点击类应用包名，
`:96` 是需要检查授权状态的 11 个敏感权限。

### 6.5 `cmd(String[])` 通用封装

`com/didi/security/utils/AppUtils.java:1098-1110`：

```java
private static String cmd(String[] strArr) {
    Process processExec = Runtime.getRuntime().exec(strArr);      // :1100
    BufferedReader bufferedReader = new BufferedReader(
            new InputStreamReader(processExec.getInputStream()));
    StringBuilder sb3 = new StringBuilder();
    while (true) {
        String line = bufferedReader.readLine();
        if (line == null) break;
        sb3.append(line);
    }
```

参数是 `String[]`，不经 shell 解析，无注入面。同文件 `:1077-1090` 的
`checkMethodIsHook` 把反射目标交给 `SecurityLib.checkMethod(method)`
（`:1088`），构成签名之外的第二条完整性校验路径。

### 6.6 `ro0.a` 命令模板

`ro0/a.java:28-48` 的 `a()` 调用 `Runtime.getRuntime().exec(b())`（`:41`），
命令串由子类的抽象方法 `b()` 提供。子类为 `ro0/b`、`ro0/d`、`at1/a`、`at1/d`，
全部服务于 `com/didi/one/netdetect` 网络探测（`DetectionTaskManager.java:245,311`
构造 `ro0.d`，`vo0/d.java:29` 构造 `ro0.b`）。`DetectionItemResult` 的
`resolvePingTaskResult`（`:44`）与 `resolvePingTaskResultExtra`（`:52`）表明
命令主体是 **ping**。命令串来自探测任务定义，不是任意文本。

`a62/i.java:48-75` 是同类封装，命令为 `logcat -d -v threadtime -t N -b <buffer>`，
参数固定在数组内（`:59-70`），仅 `-t` 的 N 来自调用方整数。

### 6.7 安装权限

`REQUEST_INSTALL_PACKAGES` 的调用链只服务自身升级（`Source.java:86-88`、
`ORequest.java:31,41`、`y82/a.java:157-158`），安装始终经系统
`PackageInstaller` 校验签名与来源，无法静默安装。

### 6.8 hook 基础设施

| 库 | 加载点 | 用途 |
|---|---|---|
| `libshadowhook.so` | `com/bytedance/shadowhook/ShadowHook.java:46` | 字节跳动 PLT/GOT inline hook |
| `libtihook.so` | `com/didi/tools/performance/hook/IoThreadHook.java:13`（`libName = "tihook"`） | IO 线程性能监控 |

`jt1/b.java:5,24` 调 `ShadowHook.a()` 初始化。两者都是**应用自身的性能/IO
监控基础设施**，不是提权或逃逸工具；但同时它们也构成了 Hook 检测
（§8 of [risk.md](risk.md)）所针对的同类技术。

## 7. 权限与功能的对应关系

| 权限 | 对应功能 | 是否必需 |
|---|---|---|
| `ACCESS_FINE_LOCATION` / `COARSE` | 打车、地图、POI | 必需 |
| `ACCESS_BACKGROUND_LOCATION` | 行程中持续上报 | 条件必需 |
| `BLUETOOTH_*` | 安全盾设备采集、WSG 采集 | 需告知 |
| `CAMERA` | 扫码、人脸、报警 | 必需 |
| `RECORD_AUDIO` | 语音叫车、报警录音 | 必需 |
| `WRITE_CALENDAR` | 行程加入日历 | 可选功能 |
| `NFC` / `nfc.hce` | 交通卡 | 可选功能 |
| `USE_FINGERPRINT` | 支付验证 | 可选功能 |
| `QUERY_ALL_PACKAGES` | 导航/支付可用性探测 | 粒度过宽 |
| `READ_LOGS` | 崩溃诊断 | 过度声明 |
| `MOUNT_UNMOUNT_FILESYSTEMS` | 无 | 过度声明 |
| `SYSTEM_OVERLAY_WINDOW` | 悬浮球 | 与 `SYSTEM_ALERT_WINDOW` 重复 |
| `DETECT_SCREEN_CAPTURE` | 防截屏提示 | 防御性 |
| `REQUEST_INSTALL_PACKAGES` | 自身升级 | 条件必需 |
| `POST_NOTIFICATIONS` | 通知 | 必需 |
| `FOREGROUND_SERVICE*` | 行程/录音/定位前台服务 | 必需 |
