# 小红书 9.37.0 权限弹框、数据采集与隐私协议对照

本文件针对三项要求给出静态逆向结论：**追踪权限弹框**、**调查是否未经告知或超范围收集数据**、**对比隐私协议措辞与实际收集/上传的数据范围**。

- 研究对象：`com.xingin.xhs` 9.37.0（`versionCode 9370802`、`targetSdkVersion 35`、`minSdkVersion 21`）。
- 样本：`xapk/com.xingin.xhs.apk`（base，SHA-256 `0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c`）、`xapk/config.arm64_v8a.apk`、`xapk/config.mdpi.apk`，共 20 个 `classes*.dex` + 2 个 `assets/*.dex`。
- 方法：`aapt2 dump badging/xmltree/resources`（manifest 与 `resources.arsc` 全量字符串）、`jadx` 单类导出（`re/jadx_privacy*`）、`rg` 全树检索；脚本与原始输出见 `re/privacy/`。
- 结论强度约定：**已确认**＝有清单/字节码/字符串三类证据之一；**边界**＝静态分析不可判，写在 §9。

---

## 1. 权限清单（三 APK 并集 = 80 项）

`aapt2 dump badging` 逐 APK 提取后取并集，得到 **80 个 `uses-permission`**：

| 来源 | 条数 |
| --- | ---: |
| `com.xingin.xhs.apk`（base） | 79 |
| `config.arm64_v8a.apk`（split） | 3 |
| `config.mdpi.apk`（split） | 3 |
| **并集** | **80** |

**关键点：`android.permission.READ_PHONE_STATE` 只声明在 split config APK 中，base APK 的 manifest 完全没有它。** 合并安装后进程实际持有该权限（`PackageManager.getPackageInfo(..., GET_PERMISSIONS)` 返回合并结果）。这是 XAPK 拆分安装形态造成的声明分裂：只审 base APK 会漏掉电话权限，只审 split 会漏掉其余 77 项。

### 1.1 运行时危险权限（需用户逐次授权，22 项）

| 权限 | 声明位置 | 对应弹框/说明文本 |
| --- | --- | --- |
| `READ_PHONE_STATE` | **仅 split** | `login_permission_open_tips`、`privacy_policy_one_for_preload_noi18n` §2 |
| `CAMERA` | base | `ru_camera_permission_str_gp`、`alpha_prepare_camera_permission` |
| `RECORD_AUDIO` | base | `ru_audio_str_gp`、`hey_dialog_audio_permission_tip` |
| `ACCESS_FINE_LOCATION` | base | `ru_location_str_gp`、`alpha_position_permission_dialog_title` |
| `ACCESS_COARSE_LOCATION` | base | `ru_location_str_gp` |
| `READ_CONTACTS` | base | `ru_contacts_str_gp` |
| `READ_CALENDAR` / `WRITE_CALENDAR` | base | `ru_calender_str_gp`、`ru_permission_guide_calendar_desc` |
| `POST_NOTIFICATIONS` | base | `ru_post_notification_gp`（Android 13+） |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` | base | `ru_write_read_permission_str_gp` |
| `READ_MEDIA_VISUAL_USER_SELECTED` | base | 同上（Android 14 部分授权） |
| `ACCESS_MEDIA_LOCATION` | base | `privacy_permission_album_location_info_tips` |
| `BLUETOOTH_CONNECT` | base | `ru_ble_permission_str` |
| `READ_EXTERNAL_STORAGE`（`maxSdkVersion=32`）/ `WRITE_EXTERNAL_STORAGE` | base | `ru_write_read_permission_str_gp` |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | base | 生物识别登录 |
| `NFC` | base | `privacy_policy_one_for_preload_noi18n` §10（证件读取） |
| `SYSTEM_ALERT_WINDOW` | base | `alpha_live_new_float_permission_guide` |

### 1.2 厂商/推送特权权限（30 项，多数视为已授予）

`com.oplus.ocs.permission.third`、`com.vivo.notification.permission.BADGE_ICON`、`com.hihonor.brain.permission.KIT_SERVICE_ACCESS`、`com.meizu.flyme.push.permission.RECEIVE`、`com.coloros.mcs.permission.RECIEVE_MCS_MESSAGE`、`com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA`、`com.xiaomi.security.permission.ACCESS_XSOF`、`com.samsung.android.mapsagent.permission.READ_APP_INFO`、`com.asus.msa.SupplementaryDID.ACCESS`、`freemme.permission.msa`、`com.huawei.permission.ACCESS_HW_KEYSTORE`、`com.hihonor.permission.ACCESS_HW_KEYSTORE`、`com.hihonor.security.permission.ACCESS_THREAT_DETECTION`、`com.samsung.android.sume.nn.service.permission.ACCESS_SUME_NN_SERVICE`、`com.open.gallery.smart.Provider`、`com.android.gallery3d.permission.GalleryDataProvider`、`com.miui.home.launcher.permission.INSTALL_WIDGET` 等。

其中 `com.huawei.permission.ACCESS_HW_KEYSTORE`（华为安全键盘）、`com.hihonor.security.permission.ACCESS_THREAT_DETECTION`（威胁检测）、`com.xiaomi.security.permission.ACCESS_XSOF` 属**厂商安全服务接入**，需要第三方安全 SDK 在库内另外校验。

### 1.3 应用自定义权限（7 项声明）

| 名称 | protectionLevel | 用途 |
| --- | --- | --- |
| `com.xingin.xhs.permission.C2D_MESSAGE` | 0x2（signature） | 推送 C2DM |
| `com.xingin.xhs.push.permission.MESSAGE` | 0x2 | 自建推送 |
| `com.xingin.xhs.permission.MIPUSH_RECEIVE` | 0x2 | 小米推送 |
| `com.xingin.xhs.matrix.permission.PROCESS_SUPERVISOR` | 0x2 | **多进程监督（matrix）** |
| `com.xingin.xhs.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | 0x2 | 动态广播不导出 |
| `com.xingin.xhs.permission.RECEIVE_VOLLEY_SHARE_RESULT` | **0x0（normal）** | 分享结果回传 |
| `{applicationId}.gcm.permission.C2D_MESSAGE` | 0x2 | GCM |

`RECEIVE_VOLLEY_SHARE_RESULT` 为 `normal` 级，任何应用均可申请；`PROCESS_SUPERVISOR` 用于进程看护。

### 1.4 声明但语义特殊的项

| 权限 | 说明 |
| --- | --- |
| `android.permission.BATTERY_STATS` | 普通应用拿不到；实际取值走 `dumpsys`/系统属性回退 |
| `android.permission.DEVICE_POWER` | 同上，不可获得 |
| `android.permission.WRITE_CLIPBOARD_SERVICE` | **非 AOSP 定义的系统权限**（AOSP 无此名字），只在厂商 ROM 上可能被授予 |
| `android.permission.EXPAND_STATUS_BAR` | 通知栏展开，需厂商放行 |
| `com.xingin.xhs.manual.dump` | 自定义；对应 `nativedump` 模块的手动 dump 通道 |

### 1.5 未声明的敏感权限（0 命中）

以下在样本中**全部未声明**，全树检索字面量为 0：

```
QUERY_ALL_PACKAGES  ACTIVITY_RECOGNITION  BODY_SENSORS
ACCESS_BACKGROUND_LOCATION  PACKAGE_USAGE_STATS  READ_CLIPBOARD
```

推论：已安装应用列表的获取**不依赖 `QUERY_ALL_PACKAGES`**（见 §5.1）；后台定位不可用，位置采集只能在前后台活动期内进行（见 §5.4）。

### 1.6 `<queries>` 可见性白名单（83 个 `<package>`）

Android 11 起的包可见性模型下，应用只声明了 **83 个 `<package>` + 若干个 `<intent>`/`<provider>`**，构成其"可探测应用集合"：

| 分组 | 代表包名 | 数量级 |
| --- | --- | --- |
| 社交/内容 | `com.tencent.mm`、`com.tencent.mobileqq`、`com.sina.weibo`、`tv.danmaku.bili`、`com.ss.android.ugc.aweme`、`com.smile.gifmaker`、`com.ss.android.article.video` | 7 |
| 电商/支付 | `com.taobao.taobao`、`com.jingdong.app.mall`、`com.eg.android.AlipayGphone`、`com.unionpay*`、`hk.alipay.wallet*` | 8 |
| 银行（30 家） | `com.icbc`、`cmb.pb`、`com.chinamworld.bocmbci`、`com.ccb.longjiLife`、`cn.com.spdb.mobilebank.per`、`com.hxb.mobile.client` … | 31 |
| 设备标识服务 | `com.heytap.openid`、`com.vivo.vms.IdProvider`、`com.samsung.android.deviceidservice`、`com.mdid.msa`、`com.asus.msa.SupplementaryDID`、`com.zui.deviceidservice`、`com.huawei.hwid`、`com.hihonor.id`、`cn.nubia.identity`、`com.coolpad.deviceidsupport` | 13 |
| 厂商能力 | `com.coloros.ocs.opencapabilityservice`、`com.oplus.ocs`、`com.oplus.cosa`、`com.hihonor.hiboard`、`com.hihonor.mediadatacenter`、`com.heytap.health`、`com.huawei.hwpanpayservice` | 8 |
| OAuth/登录 | `com.google.android.gms`、`com.facebook.katana`、`com.instagram.android`、`com.android.vending`、`org.ifaa.aidl.manager`、`com.google.android.apps.maps` | 6 |
| 其他 | `com.shuyishuer.REDcity.redcity`、`com.android.creator`、`com.meizu.flyme` | 3 |

**读法**：银行类 31 个包名不用于"读取银行 App 数据"，而是**安全/风控场景下的存在性探测**（支付环境、root/多开判定、风险设备画像）。设备标识服务 13 个是 OAID 取值链（`MsaAllianceManager` + 厂商 AIDL），与 `libturingmfa.so` 内 37 项 OAID 厂商 AIDL 表互为印证。

---

## 2. 权限弹框追踪

### 2.1 三层结构

应用没有单一"权限页"，而是三层：

```text
① 首启合规弹窗     PrivacyPolicyDialog      隐私政策/个人信息保护提示（非系统权限）
② 系统权限解释弹窗  f72.c（RuDialogPermissionExplain）  "权限使用说明"，带聚合说明文本
③ 系统权限请求     m82.m → ActivityCompat/ActivityResult  真正的 OS 对话框
```

### 2.2 第①层：首启合规弹窗

`com.xingin.privacy.policy.PrivacyPolicyDialog`（`classes16.dex`）继承 `LCBDialog`，布局 `2131499527`，由 `PrivacyPolicyPresenter` 驱动。

`PrivacyPolicyPresenter.a` 的内部枚举 → 状态映射（`vt9.l`）：

| 触发场景（`vt9.m`） | 呈现状态（`vt9.l`） |
| --- | --- |
| `FEED_FIRST` | `DefaultPolicyUpdateState` |
| `DIALOG_FIRST_V2` | `BaseModeTipState` |
| `BASE_FUNC_MODE` | `TestBaseModeTipState` |
| `DIALOG_SECOND_V2` | `DefaultFirstScreenStateV2` |
| `DIALOG_SECOND` | `TryToRetainUserState` |
| `DIALOG_FIRST` | `DefaultFirstScreenState` |
| `FEED_SECOND` | `DefaultFirstScreenState` |

`vt9.g.setUp()` 对 **除 `BASE_FUNC_MODE` 外的所有形态** 执行：

```java
V9().setCanceledOnTouchOutside(false);
V9().setCancelable(false);
```

即：**首启合规弹窗不可返回键关闭、不可点击外部关闭**，必须显式点"同意/不同意"。

`cu9.g`（`PrivacyPolicyProxyImpl` 的落地实现）确认同意状态双写：

```java
// cu9.g.a(Context)
boolean zE = nbc.i.l(context.getPackageName(), "").e("is_privacy_policy_granted", false);  // SP
boolean zG = i8c.f.a.g(context);                                                           // SafeModeManager
if (!zE && !zG) return false;
if (!zE) b(context, true);
if (!zG) fVar.f(context).g(true);
return true;
```

两条独立存储（SharedPreferences `is_privacy_policy_granted` + `i8c.f` SafeModeManager）**必须都无效才算未同意**——单边清除无法重置合规状态。

### 2.3 基础功能模式（不同意时的降级路径）

`qt9.a`：

```java
public final boolean a() {  return !g.a.a(XYUtilsCenter.a()) && e.b.a(); }   // 未同意 且 实验命中
public final boolean c() {  return a() && b.get(); }                          // 且 已进入模式
```

`cu9.e.a()` 从 `preload` SP 读 `needDoFirstPrivacy`，并做一次厂商/实验分流（含 `oppo` 硬编码分支）。降级后：

- 文案 `privacy_policy_for_preload_basemode_no_i18n`（1206 字符）把"同意"改述为"同意并退出浏览模式"；
- 服务端上报走 `com.xingin.privacy.net.BaseModeServices`：

```java
@o("/api/sns/v1/basemode/report")  @e
Observable<String> trackReport(
    @c("platform") String, @c("versionName") String, @c("channel") String,
    @c("deviceId") String, @c("baseModeId") String, @c("projectId") String,
    @c("appId") String, @c("build") String, @c("cpuName") String,
    @c("overseasChannel") String, @c("eventName") String,
    @c("extraMap") Map<String,String>, @c("timestamp") long, @c("sign") String);
```

**注意**：即使处于"基础浏览模式"（用户尚未同意隐私政策），仍会上报 9 个设备/构建字段 + 事件名 + 时间戳。`bu9.c.a()` 的 `sign` 由 `MD5(deviceUuid + timestamp + <样本内硬编码固定盐串>)` 生成（盐串为 32 位十六进制常量，不在此公开）；`deviceUuid` 来自 `ug_preload_device_info` SP，首次为 `UUID.randomUUID()`。

### 2.4 第②层：系统权限解释弹窗（`f72.c`）

`com.xingin.android.redutils.databinding.RuDialogPermissionExplainBinding` + 布局 `R.layout.ru_dialog_permission_explain`（`0x7f0c1b27`）。`f72.c` 构造签名：

```java
c(Activity, String title, String desc, String okText, String cancelText,
  Function0 onShown, Function0 onOk, Function0 onCancel, Function1 onBind)
```

- `onCreate` 内 `setCancelable(false)` + `setCanceledOnTouchOutside(false)`；
- 确定按钮默认文案 `ru_permission_ok`＝"授权"，取消默认 `ru_permission_cancel`＝"使用しない"（此条中文字形缺失，落到日文资源）。

### 2.5 聚合说明文本的生成逻辑（`m82.m`）

`m82.m.f266434d` 是**权限 → 说明资源**的完整映射表（14 条，`classes7.dex`）：

| 权限 | 说明资源 |
| --- | --- |
| `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` / `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | `ru_write_read_permission_str_gp` |
| `CAMERA` | `ru_camera_permission_str_gp` |
| `RECORD_AUDIO` | `ru_audio_str_gp` |
| `READ_CONTACTS` | `ru_contacts_str_gp` |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | `ru_location_str_gp` |
| `READ_CALENDAR` / `WRITE_CALENDAR` | `ru_calender_str_gp` |
| `POST_NOTIFICATIONS` | `ru_post_notification_gp` |
| `BLUETOOTH_CONNECT` | `ru_ble_permission_str` |

`m82.m.k(obj, perms...)` 过滤出**尚未授予**的权限，用 `HashSet` 去重后按资源 ID 顺序拼接，前后固定包裹 `ru_permission_start_gp`（"Rednote will…"）与 `ru_permissions_end_gp`（"如拒绝，您将无法使用上述功能，但不影响其他服务"）。标题固定 `ru_permission_title`＝"System permissions"。

**该表不含 `READ_PHONE_STATE`。** 电话权限的说明走另一条独立链路（§2.6）。

### 2.6 电话权限的独立链路与节流（`PhonePermissionHelperExtension`）

`com.xingin.login.permisson.PhonePermissionHelperExtension`（`classes14.dex`）是**唯一**真正的 `requestPermissions` 调用方（全树仅 2 处：接口声明 + 本实现）。逻辑逐条：

```java
if (d.b) { next(); return false; }                       // 老用户新安装/换机，首个 Session 不弹
if (SP.e("is_permission_description_granted")) { next(); return false; }  // 老版解释弹窗出现过
if (SP.e("is_permission_dialog_shown"))        { next(); return false; }  // 弹过就不再弹
if (SDK_INT < 23) { next(); return false; }               // 默认授予
// 收集缺失权限
if (!has(READ_PHONE_STATE))      list.add(READ_PHONE_STATE);
if (SDK_INT >= 33 && !has(POST_NOTIFICATIONS)) list.add(POST_NOTIFICATIONS);
if (list.isEmpty()) { next(); return false; }
SP.q("is_permission_dialog_shown", true);                 // 先落标记，再弹
h.a(..., "IMEI_APP");                                      // APM 埋点
m.e(m.a, activity, new String[]{"READ_PHONE_STATE"}, onOk, onCancel, ...);
h.a(..., "IMEI_SYSTEM");
h.a(..., "IMEI_APP", 2);
```

要点：

1. **弹一次即永久标记**（`is_permission_dialog_shown`），此后不再进入该分支；用户拒绝后应用不会再次主动申请 `READ_PHONE_STATE`，而是走"去设置页"路径（`login_permission_open_tips`＝"您已禁止获取设备权限！" + `login_permission_positive_tips`＝"跳转权限设置页"）。
2. 埋点事件名直接使用 `IMEI_APP` / `IMEI_SYSTEM`——**样本内部对该权限的业务命名就是 IMEI**，这是"申请该权限的目的"的自认证据。
3. 弹窗触发点绑定在 `welcome_page` 的 `permission_page_target` 模态上（`h.a(h.a, a.k6.welcome_page, a.u7.permission_page_target, a.n5.modal_show, ...)`），即**首启欢迎流程内**。

### 2.7 权限请求路径的三种实现

| 路径 | 实现 | 说明 |
| --- | --- | --- |
| 主路径 | `m82.m.e(...)` → `b3b.f`/`b3b.i` | 带解释弹窗的封装 |
| 兼容 | `androidx.activity.result.ActivityResultContracts$RequestPermission(s)` | 生命周期感知 |
| 遗留 | `com.xingin.utils.core.PermissionUtils.PermissionActivity` | `@Deprecated`，透明 Activity，`dispatchTouchEvent` 即 `finish()` |
| 插件 | `com.facebook.react.modules.permissions.PermissionsModule` | RN 侧权限 |
| 相机 | `com.xingin.capa.capa_framework.base.CapaBasePermissionActivity` | 拍摄链路 |

### 2.8 各权限的独立引导弹窗（不代表已申请）

除统一说明弹窗外，各业务模块另有独立文案（节选）：

| 场景 | 资源 | 文案 |
| --- | --- | --- |
| 直播位置 | `alpha_position_permission_dialog_title` | "小红书想获取您的位置权限" |
| 直播位置副标题 | `alpha_position_permission_dialog_sub_title` | "用于在同城、地图中展示你的位置与用户距离，让更多用户看到你的直播" |
| 相册+位置联合 | `privacy_permission_album_location_info_tips` | "开启后，将在发布时依据您相册中照片和视频的位置信息为您推荐拍摄地点" |
| 悬浮窗 | `alpha_live_new_float_permission_guide` | "是否开启悬浮窗权限？开启后，会在浏览其他内容时访问悬浮窗…" |
| 日历 | `ru_permission_guide_calendar_desc` | "打开日历权限，自动帮你创建直播日程" |
| 推送 | `ru_permission_guide_notification_desc` | "打开推送通知，才可以收到直播开播提醒" |

---

## 3. 应用内隐私合规框架（内部实现）

9.37.0 内置一套**自查式隐私合规追踪框架**。它不属于风控，而是应用自证的合规工程件，但恰好给出了"哪些系统 API 被视为需报备"的完整清单。

### 3.1 组件

| 类 | 作用 |
| --- | --- |
| `android.xingin.com.spi.privacy.IPrivacyTracker` | SPI 接口：`traceGAID()` / `traceIMEI()` / `traceOAID()` |
| `com.xingin.privacy.runtime.consumer.PrivacyTracerImpl` | 实现；`traceGAID`→`80300`、`traceOAID`→`80100`、`traceIMEI`→ **空实现** |
| `com.xingin.privacy.runtime.consumer.PrivacyThrowable` | 携带调用栈的异常，`getApi()` 惰性求值，`matchSystemClass` 只认 `android.`/`com.android.`/`java.`/`kotlin.` 前缀 |
| `com.xingin.privacy.runtime.consumer.FreqPrivacyThrowable` | 超频专用异常 |
| `xt9.d` | 注册表：`a(int,int,String,String,boolean,boolean)` + `b(int)` + `c(int,String)` |
| `wt9.a` | 采集侧全局状态：前后台（`f`）、页面标识（`h`）、频率监控开关（`i`） |
| `wt9.e` | **全部 `LocationManager`/`GnssStatus`/`CellInfo` 调用的唯一出口** |
| `wt9.f` | `ConnectivityManager` / `TelephonyManager`（SIM 信息）出口 |
| `wt9.g` | `PackageManager` 查询出口 |
| `wt9.h` | `TelephonyManager` 设备标识 + `Settings.Secure` 出口 |
| `yt9.*`（b/c/d/e/h/i/j/k/l/m/n/o/p） | 相机、音频、剪贴板、Build、网络、坐标的出口 |

### 3.2 API 编号注册表（55 个五位数 ID，全部定名）

`xt9.d.a(int apiSource, int apiNum, String apiType, String apiMsg, boolean z, boolean z2)`
承载两套互不重叠的编号空间：**五位数业务 API 号**（`apiSource=4`，采集侧自报）与
**AppOps op 索引**（`apiSource=1/2/3`，系统回调回填）。两者共用同一套限频、采样与上报逻辑。

#### 3.2.1 五位数 ID（55 个）

按编号段分组（`re/privacy/` 全树扫描 `xt9.d.a/b/c(...)` 调用点，ID → 出口类）：

| 段 | ID | 数量 | 出口 | 采集对象 |
| --- | --- | --- | --- | --- |
| 包信息 | `10005` `10006` | 2 | `yt9.m` | `PackageManager.getPackageInfo` |
| 相机 | `20001` `20003` `20004` `20005` | 4 | `yt9.c` `yt9.d` `f9a.b` `CameraHelper` | `CameraManager.openCamera`、`Camera.open` |
| 音频 | `21001` `21003` | 2 | `yt9.a` `qe2.c` | `AudioRecord.startRecording` |
| 系统解析 | `22001` `22002` | 2 | `ContentResolverProxy` `yt9.f` | `ContentResolver` 查询 |
| 位置 | `61000`–`61009` `61011` `61018` `61022` `61025`–`61029` | 18 | `yt9.p` `yt9.o` `qw7.h` `wt9.e` `yMdp8` | `getLastKnownLocation`、`requestLocationUpdates`、`registerGnssStatusCallback`、`getAllCellInfo` |
| 剪贴板 | `70001` `70002` `70004` | 3 | `ClipboardManagerProxyImpl` `ClipboardModule` `yt9.e` | `getPrimaryClip` / `setPrimaryClip` / `addPrimaryClipChangedListener` |
| 设备标识 | `80000` `80001` `80002` `80100` `80200` `80300` `81801` | 7 | `wt9.h` `PrivacyTracerImpl` `yt9.h` `com.xingin.u.p.c` | `getImei`、`getDeviceId`、**按卡槽 `getImei(int)`**、**按卡槽 `getMeid(int)`**、**OAID**、`Settings.Secure.android_id`、**GAID** |
| WLAN MAC | `80600` `80601` | 2 | `com.xingin.utils.core.q.i()` | `WifiInfo.getMacAddress()`、`NetworkInterface.getHardwareAddress()`（见 §6.1） |
| 电话/SIM | `80700` `80800` `80900` `81000` `81200`–`81202` | 7 | `wt9.h` `zw7.b` `yt9.n` `wt9.f` | `getSubscriberId`、`getSimSerialNumber`、`WifiInfo.getSSID`、`getSimOperator`、`getSimCountryIso` |
| 系统属性 | `81400` `81401` | 2 | `yt9.b` | `Build.MANUFACTURER` / `Build.MODEL` |
| 网络 | `81601` `81602` `81604` `81700`–`81702` | 6 | `yt9.j` `yt9.i` `yt9.l` `yt9.k` | `InetAddress`/`Inet4Address`、`NetworkInfo`、`NetworkCapabilities` |
| **合计** | | **55** | | |

四个易漏 ID 的精确出口（均已定位到具体方法）：

| ID | 出口类 / 方法 | 条件 | 实际调用 |
| --- | --- | --- | --- |
| `80002` | `com.xingin.u.p.c.getImeiBySlot`（`com/xingin/u/p/c.java:215`，`d.a.b(80002)`） | `SDK_INT >= 26` | `TelephonyManager.getImei(slot)` |
| `81801` | `com.xingin.u.p.c.getMeiIDBySlot`（`com/xingin/u/p/c.java:267`，`d.a.b(81801)`） | 卡槽参数化 | `TelephonyManager.getMeid(slot)` |
| `80600` | `com.xingin.utils.core.q.i()`（`com/xingin/utils/core/q.java:212`，`xt9.d.a.c(80600, null)`） | `WifiInfo` 非空 | `WifiInfo.getMacAddress()` |
| `80601` | `com.xingin.utils.core.q.i()`（`com/xingin/utils/core/q.java:232,275`，`xt9.d.a.b(80601)`） | MAC 非占位值 | `NetworkInterface.getHardwareAddress()`（wlan0 + `getByInetAddress` 回退） |

#### 3.2.2 AppOps op 索引空间（`zt9.a`，148 项）

`setOnOpNotedCallback(sya.k.s, zt9.b.a)` 在三处注册：
`re/jadx_1718/sources/slb/b.java:347`、`re/jadx_1718/sources/vlb/j.java:24`、`re/jadx_1718/sources/ulb/d.java:106`。
`zt9.b` 是 `AppOpsManager.OnOpNotedCallback` 实现，三个回调把系统告知的敏感操作转交 `xt9.d.a(...)`：

| 回调 | `apiSource` | 含义 | `z` / `z2` |
| --- | --- | --- | --- |
| `onNoted(SyncNotedAppOp)` | `1` | 同步操作被记录 | `true` / `true` |
| `onSelfNoted(SyncNotedAppOp)` | `2` | 应用自报操作 | `true` / `true` |
| `onAsyncNoted(AsyncNotedAppOp)` | `3` | 异步操作被记录 | `false` / `false` |

**`1/2/3` 是 `apiSource`（来源类型）而非 API 编号**；真正的 `apiNum` 由 `zt9.a.a(String opName)`
给出——`zt9.a` 是一张 **148 项 AppOps 名称 → 整数**映射（`android:coarse_location` = 0 …
`android:receive_sensitive_notifications` = 147），其中 `android:coarse_location` 段内一项取
`FilterTypeBean.COLLECT_TYPE_WEIGHT` 常量 = **102**（由 `ym8/m.java` 的
`case ... /* 102 */` 反向确认）。`apiType` 传 AppOps 名称本身，`apiMsg` 传回调附带的消息。

因此 `xt9.d.a` 的完整入参空间 = **55 个五位数业务号** + **148 个 AppOps 索引**，
`apiSource ∈ {1,2,3,4}`（4 = 采集侧 `b(int)`/`c(int,String)` 自报）。

### 3.3 入口、开关与超频计数（`xt9.d.a` 精确控制流）

完整反编译：`re/jadx_privacy10/xt9.d.java`（229 行，`--show-bad-code --comments-level debug`）。

签名与入口：

```java
void xt9.d.a(int apiSource, int apiNum, String apiType, String apiMsg,
             boolean z, boolean z2)
void xt9.d.b(int apiNum)          { c(apiNum, null); }
void xt9.d.c(int apiNum, String m){ if (wt9.i.a.a()) a(4, apiNum, "api", m, true, false); }
```

入口只有三类：`b/c(...)` 自报（`apiSource=4`）、`zt9.b` 系统回调（`apiSource=1/2/3`，见 §3.2.2）。
逐层判定顺序：

1. **`wt9.i.a.a()` 全局开关**：`wt9.i.b(String)` 解析成功/失败后**都**无条件执行 `c = Boolean.TRUE`
   （见 §9.6），故 `a()` 仅当 `c == null`（配置从未下发）时返回 `false`；一旦跑过解析即恒为真。
2. **可插拔覆盖**：`xt9.d.d`（类型 `xt9.e`）非空时，`eVar.a(apiSource, apiNum, apiType, apiMsg, z, z2)`
   **直接接管并 `return`**，本方法内的计数、采样、异常、上报全部不再执行。
3. **频率监控仅对 `apiSource ∈ {4, 1}` 生效**：条件是 `wt9.a.i == true`（`freqInterval > 0` 时由
   `wt9.i.b()` 置位并以该周期调度 `privacy_freq_monitor` 重置）。`apiSource=2/3` 走同一入口但**绕过计数器**。
4. 计数器为 `static volatile ConcurrentHashMap<Integer, AtomicInteger> xt9.d.c`，按 `apiNum` 分桶；
   阈值 `freqCnt = wt9.i.i.get(apiNum)`，缺省为 `Integer.MAX_VALUE`：

```java
AtomicInteger ctr = c.get(apiNum);              // 缺省 putIfAbsent(new AtomicInteger(0))
int cnt = ctr.incrementAndGet();
Integer lim = wt9.i.i.get(apiNum);              // 该 API 的 freqCnt
if (cnt >= (lim == null ? Integer.MAX_VALUE : lim)) {
    overLimit = true;
    q2c.e.p("privacy", "超频调用: apiNum=" + apiNum + ", cnt=" + cnt);
}
```

5. 采样判定与异常构造：

```java
boolean sampled = overLimit || wt9.i.a.c(apiNum);       // 超频必采，否则按 sentrySample 随机
PrivacyThrowable t = null;
if (z && (z2 || sampled))
    t = overLimit ? new FreqPrivacyThrowable() : new PrivacyThrowable();
```

   即**只有 `z=true` 才可能构造异常**；`z` 来自调用方：`b/c(...)` 传 `true`，
   `onNoted/onSelfNoted`（`apiSource=1/2`）传 `true`，`onAsyncNoted`（`apiSource=3`）传 `false`
   ——异步 AppOps 走 APM 分支而不走异常分支。

6. 快照上下文（供上报使用）：`str3 = wt9.a.c`（隐私同意状态）、`str4 = wt9.a.f`（前后台 `0`/`1`）、
   `str5 = wt9.a.h`（页面实例）。
7. 整段逻辑被包进 `o2b.f.a.I("privacyTracerInThread", executor, Function0)` **异步执行**，
   不阻塞业务线程。

### 3.4 异步出口的两条上报分支（APM / Sentry）

`Function0.invoke()` 内部分为两条互斥链路，全部字段逐项取自 `re/jadx_privacy10/xt9.d.java`：

**（A）APM 分支 —— 仅 `apiSource == 3` 且 `apiMsg` 非空**

- `apiMsg` 先按 `'@'` 截断：`idx = indexOf('@')`，`idx > 0` 时取 `substring(0, idx)`（只保留调用前缀，去掉附带的长参数）。
- 当 `wt9.i.a.a()` 为真时直接复用该次结果；为假时按 **APM 采样**门控：
  `n = wt9.i.e.get(apiNum) ?? wt9.i.d`（`e[]` 为该 API 的 `apmSample`，`d` 为全局 `apmSample`），
  `n < 1` 不报、`n == 1` 必报、否则 `ThreadLocalRandom.nextInt(n) == 0` 才报。
- 通过后生成 `apmEventId = UUID.randomUUID()`，构建 `TrackerEventDetail` 并调用
  `com.xingin.android.apm_core.b.m.e(detail)`，事件名 **`infra_privacy_monitor`**、`value = 1.0`，字段：

| 字段 | 来源 |
| --- | --- |
| `type` | 固定 `"event"` |
| `apmEventId` | 本次 UUID |
| `apiSource` / `apiNum` | 入参 |
| `oversea` | 固定 `"1"` |
| `agreedPrivacy` | `wt9.a.c` |
| `backgroundMode` | `wt9.a.f`（前后台） |
| `pageInstance` | `wt9.a.h` |
| `release` | `wt9.a.g`（发布包名） |
| `api` | `PrivacyThrowable.getApi()` → `className + "." + methodName`；无异常时为空串 |
| `apiType` / `apiMsg` | 入参；`apiMsg` 已按 `'@'` 截断 |
| `lbsTag` | `tw7.b.b.n()`（定位标签） |

**（B）Sentry 分支 —— 只要构造出了 `PrivacyThrowable` 且 `z2 == true`**

`apiSource=3` 走 APM 链路时同样可能落到此处；`apiSource=4/1/2` 走到末尾的通用块。构造
`LinkedHashMap` 后调用 `cpc.e.i(...)`，两个 DSN（**硬编码在应用内**）：

```
https://f4cd12249fd3457b946fa1fd044b3d83@new-sentry-relay.xiaohongshu.com/240
https://c1511061fb864031bf3e9ff3ef4b6ec1@new-sentry-relay.rednote.life/62
```

Map 键：`apmEventId`、`apiSource`、`apiNum`、`oversea`、`agreedPrivacy`、`backgroundMode`、
`releasePkg`、`api`、`apiType`、`apiMsg`（非空时）、`freq`（超频为 `"1"`，否则 `"0"`）。

**采样参数的两套取值**（`wt9.i.b(String)` 解析 `wt9.b`/`wt9.c` JSON）：

| 用途 | 配置项 | 落点 | 回退 |
| --- | --- | --- | --- |
| APM 上报 | `apmSample` | `wt9.i.e[apiNum]` | `wt9.i.d` |
| 异常采样 | `sentrySample` | `wt9.i.i[apiNum]` | `wt9.i.f` |
| 超频阈值 | `freqCnt` | `wt9.i.g[apiNum]` | 无（`Integer.MAX_VALUE`） |
| 重置周期 | `freqInterval` | `wt9.i.h` | `> 0` 才启用 `wt9.a.i` |

配置 JSON 形态：

```json
{ "apmSample": int, "sentrySample": int, "freqInterval": long,
  "apiConfigList": [ { "apis": [int...], "freqCnt": int, "apmSample": int, "sentrySample": int } ] }
```

`wt9.i.c(int)` 的异常采样即 `n = g.get(api) ?? f; n<1 → false; n==1 → true;
ThreadLocalRandom.nextInt(n)==0`。

**`PrivacyThrowable.getApi()` 的调用点定位规则**（`com/xingin/privacy/runtime/consumer/PrivacyThrowable.java`，
`findSystemApi(stackTrace)`）：顺序遍历 `getStackTrace()`，保留**最后一个**类名前缀属于
`android.` / `com.android.` / `java.` / `kotlin.` 的帧，再返回它**之后的第一个非系统帧**——
即"系统 API 之后的第一处应用帧"，因此上报的 `api` 字段可直接指回触发采集的业务方法。

---

### 3.5 前后台状态（`wt9.a`）

`onForeground` → `f="0"`；`onBackground` → `f="1"`；`c`/`h` 默认 `"-1"`（`f` 表示前后台，`c`/`h` 是未赋值占位）。`wt9.a.f(String,String,s)` 从 UBT 页面链路里取 pageview 的 page number 写入 `h`。这些值会随 `PrivacyThrowable` 一并上报，使服务端能判断"敏感 API 是在前台还是后台被调用"。

### 3.6 合规豁免标注

代码中存在显式的 lint 压制注解，用于标记**已评估但豁免**的调用点：`@SuppressLint({"Privacy_Phone_Check"})`、`{"Privacy_Id_Check"}`、`{"Privacy-InstalledApps"}`、`{"Privacy_Network_Check"}`、`{"MissingPermission"}`、`{"HardwareIds"}`、`{"NoOriginalEnvironmentGetDir"}`。这些注解的分布即应用自认的敏感 API 白名单。

---

## 4. 位置采集的双层门控（`wt9.e`）

位置是样本中门控最复杂的类别，值得单列。

```java
static final boolean f383810a = ((Long) rt.k.a.k("andr_enable_coarse_location_check", Long.class, 0L)) == 1;

static boolean h()  { return e.b.a() && !cu9.g.a.a(XYUtilsCenter.a()); }   // 基础模式 且 未同意隐私政策
static boolean i()  { return h() || (!d.a("ACCESS_FINE_LOCATION") && d.f383808a); }
static boolean j()  { return !(d.a("ACCESS_FINE_LOCATION") || d.a("ACCESS_COARSE_LOCATION") || !d.f383808a); }
```

| 方法 | 门控条件 | 行为 |
| --- | --- | --- |
| `getLastKnownLocation` | `j()` 为真（两权限都缺且 `d.f383808a`） | 返回 `(0.0, 0.0)` 假坐标 |
| `requestLocationUpdates`（V4/V8） | `j()` | 直接 `return`，不注册监听 |
| `getAllCellInfo` | `h()` 或（`a.f383801d` 且 `i()`） | 返回 `Collections.emptyList()` |
| `registerGnssStatusCallback` / `registerGnssMeasurementsCallback` | `h()` 或（`a.f383801d` 且 `i()`） | 返回 `false` |
| `location.getLatitude/getLongitude/getAltitude` | `f383810a` 为假 且 `i()` | **返回 `0.0d` 而非真实值** |
| `location.getAccuracy` | 同上 | 返回 `0.0f` |
| `location.getExtras` | 同上 | 返回 `null` |

注意 `i()` 的 `h()` 分支：**只要"基础浏览模式 + 未同意隐私政策"，坐标一律归零**。而 `f383810a`（`andr_enable_coarse_location_check`）由服务端下发，可整体关掉这套保护——即**是否做坐标归零由远程开关决定，默认关闭（默认 0）**。

`wt9.e.b(TelephonyManager)` 有 100 ms–`l()` 的 `CellInfo` 缓存复用（`tw7.b.i`），避免高频调用被系统限流。

---

## 5. 声明 vs 实际采集对照

对照基准：§2 的三层弹窗文本 + `resources.arsc` 中的全部隐私政策副本；实际侧：§3 的 55 个五位数 API ID 出口 + `deepdive/` 已恢复的 native/Java 采集面。

图例：**一致**＝弹窗有对应措辞；**未述**＝弹窗未提及；**超述**＝弹窗提及但实现受限/失效。

### 5.1 与首启文本的直接对照

首启文本（`privacy_policy_one_for_preload_noi18n`，13 条）逐条核对：

| # | 弹窗措辞 | 实际实现 | 判定 |
| --- | --- | --- | --- |
| 2 | "申请读取您的本机号码及设备唯一可识别信息（IMEI、IMSI 等…）、IP 地址、**WLAN MAC 地址**）、以及已安装软件列表" | IMEI：`wt9.h.c` / `yt9.h` / `u.p.c.getImeiBySlot`（`80000`/`80002`/`80300`）；MEID：`u.p.c.getMeiIDBySlot`（`81801`）；IMSI：`wt9.h.d`（`80700`）；`android_id`：`wt9.h.e`（`80200`）；已装应用：`t.b()`→`LoginProxy.getAllPackageInfo()`→`all_package_info`；IP：`yt9.j/i`（`81601/81602/81604`）；WLAN MAC：`com.xingin.utils.core.q.i()`（`80600`/`80601`） | **读取一致、上传未述**（`mac` 公共参数键见 §6.1、§5.3） |
| 3 | "相机、麦克风及媒体读取功能，**仅用于拍摄和读取您选择的内容**" | 相机 `yt9.c/d`（`20001/20003`）；音频 `yt9.a`（`21001`）；媒体走 `READ_MEDIA_*` | **超述**：见 §6.3（`android.permission.ACCESS_MEDIA_LOCATION` + EXIF 全量相册） |
| 4 | "使用内容推荐、资料编辑…可能会**读取您的上网记录**；必要时申请位置信息" | 位置 `61000`–`61029`；"上网记录"对应 `yt9.l/k` 网络状态 + `ContentResolverProxy`（`22001`） | **未述**：`ContentResolverProxy` 实际可查任意 `content://`（见 §6.4） |
| 6 | "为帮助您发现更多朋友，我们**可能会申请读取您的通讯录**" | `READ_CONTACTS` + `ru_contacts_str_gp`（英文版明确"上传时加密"） | **一致** |
| 10 | "直播等实名认证…收集真实姓名、**面部识别信息**…如使用 NFC 读取证件，会获取 NFC 相关信息" | NFC 权限已声明；实名走 `securityaccount` | **一致** |
| 11 | "使用截图功能时会截屏；直播共享屏幕时录屏" | `FOREGROUND_SERVICE_MEDIA_PROJECTION` 已声明 | **一致** |
| 12 | "运动信息同步…读取并上传您的**健身活动数据**" | `com.heytap.health` / `com.hihonor.mediadatacenter` 在 `<queries>` 内 | **一致** |
| — | **未出现**：传感器清单、加速度计/陀螺仪、无障碍服务、运行进程列表、SIM 序列号、网络制式细节、屏幕亮度、电池四元组 | 全部实采（见 §5.2） | **未述** |

### 5.2 未在首启弹窗中告知、但实际采集的项

`libxyasf.so` 设备指纹（已恢复：82 个 JNI 入口、8 个子消息 51 字段）与 Java 采集器合起来，实测采集面**超出首启 13 条文本**：

| 类别 | 具体数据 | 证据 |
| --- | --- | --- |
| **运动传感器** | 全传感器清单（`getSensorList`→`;` 拼接）、加速度 `x/y/z/timestamp/desc`、陀螺仪 `x/y/z/timestamp/desc` | `com.xingin.u.p.s`、`u7c.x`、`libxyasf.so` `Motion.sensor_list` |
| **无障碍服务** | `enabled_accessibility_services`、`accessibility_enabled` | `a.a.a.a.a.n.a.a()/b()`、`libxyasf.so` `State.accessibility_enabled_services` |
| **运行进程列表** | 命令侧 `ps` 输出逐行解析 + SDK 侧 `getRunningAppProcesses`，进程名 + 应用标签，`;` 分隔 | `a.a.a.a.a.n.b.a()`/`b()`、`a.a.a.a.a.o.a.a("ps ")`、`u7c.k.e()` |
| **按卡槽设备标识** | `getImei(slot)`（API ≥ 26）、`getMeid(slot)` —— 与不分卡槽的 `80000`/`80100` 并存 | `com.xingin.u.p.c.getImeiBySlot`（`80002`）、`getMeiIDBySlot`（`81801`） |
| **WLAN MAC（真实值）** | `WifiInfo.getMacAddress()` → wlan0 `getHardwareAddress()` → `getByInetAddress` → `/sys/class/net/<if>/address`，置于账号公共参数 `mac` 键 | `com.xingin.utils.core.q.i()`（`80600`/`80601`），API ≤ 29，见 §6.1 |
| **SIM 序列号** | `getSimSerialNumber()`（ICCID） | `wt9.h.f`（`80800`） |
| **SIM/网络制式** | `getSimOperator`、`getSimOperatorName`、`getSimCountryIso` | `wt9.f.c/d/e`（`81200`–`81202`） |
| **小区信息** | `getAllCellInfo()` 全量 | `wt9.e.b`（`61009`） |
| **GNSS 原始观测** | `GnssMeasurementsEvent` 回调、`GnssStatus` 回调 | `wt9.e.k/l/m/n/o`（`61027`–`61029`、`61025`/`61026`） |
| **屏幕亮度** | `Settings.System.screen_brightness` | `com.xingin.u.p.c.getSystemScreenBrightnessValue()` |
| **电池四元组** | `chargeCounter`、`chargeCurrentAverage`、`chargeCurrentNow`、`chargeCapacity`、`status`、`isCharging` | `libxyasf.so` `getBatteryInfo` 格式串 |
| **内存** | `availMem`、`totalMem`、`threshold`、`isLowMemory` | `libxyasf.so` `getMemoryInfo` |
| **显示** | `width`、`height`、`density` | `libxyasf.so` `getDisplayMetrics` |
| **剪贴板内容** | `getPrimaryClip()` 全文，且**记录调用方类名/方法名/行号** | `yt9.e.a`（`70002`）、`ClipboardProxyImpl.reportGeneralGetPrimaryClipTrace()` |
| **Widevine DRM** | `AMediaDrm_getPropertyString` / `getPropertyByteArray` | `libtiny.so` 导入 + `libxyasf.so` |
| **系统属性全谱** | `__system_property_get/find/foreach/read/wait` | `libtiny.so` 导入 |
| **GPU/EGL** | EGL 厂商/版本/渲染器 | `libxyasf.so` |
| **APK 完整性** | `ZipEntry` CRC 累加、签名 `hashCode` 累加、APK 路径 | `com.xingin.u.p.c.getApkCRC()/getApkSignature()/getApkPath()` |
| **已装应用列表 + 版本 + 首装时间** | 逐包 JSON（`packageName`…），另取本包 `firstInstallTime` | `t.b()`、`c.G()` |
| **JS 指纹** | AES-CBC 加密后落盘 `f/jsf`；独立服务有 manifest 声明但静态无启动路径，`fpjs2.min.js` 仅为字符串 | `com.xingin.u.p.c.getJsFingerprint()`、`a.a.a.a.a.p.a$b.writeJsFp()`、manifest `XhsJsService`/`XhsJsJobService` |
| **网络指纹** | 网络类型、`NetworkCapabilities`、接口地址 | `yt9.l/k/j/i`（`81700`–`81702`、`81601`–`81604`） |
| **应用自身包信息** | `getPackageInfo(...)` 多种 flag | `yt9.m`（`10005`/`10006`） |
| **前台/后台时序** | 敏感 API 调用时的前后台状态 | `wt9.a.f` |

**结论**：首启弹窗 13 条文本覆盖了相机/麦克风/通讯录/日历/蓝牙/位置/推送/媒体/NFC/实名/录屏/健身共 12 类，**未覆盖**：传感器、无障碍服务、运行进程列表、SIM 序列号、按卡槽 IMEI/MEID、WLAN MAC 的上传用途、小区/GNSS 原始观测、屏幕亮度、电池/内存/显示参数、剪贴板全文、APK 完整性指标、已装应用列表的版本与首装时间、JS 指纹、网络接口指纹。

其中 **无障碍服务列表**、**运行进程列表**、**传感器清单**、**剪贴板全文** 四项是法规口径下的高敏感项，且均无对应弹窗文案。

### 5.3 上传去向对照

| 数据 | 目的地 | 是否在弹窗中说明 |
| --- | --- | --- |
| 51 字段设备指纹（protobuf） | `POST https://as.xiaohongshu.com/api/v1/d/upload`（multipart，part `file`/`image.jpg`/`image/jpeg`） | **未述**（弹窗只说"读取"，未说"上传"） |
| 基础模式设备信息 + 事件 | `/api/sns/v1/basemode/report` | **未述** |
| Tiny 引擎配置/探针 | `as.xiaohongshu.com/api/v1/{register,cfg,prb,dvf/vab}/android` | **未述** |
| 通讯录 | 业务上传（`ru_contacts_str_gp` 称"上传时加密"） | 一致 |
| 位置/POI | 业务上传（`ru_location_str_gp` 英文版**明确包含"support advertising"**） | 中文版只说"推荐内容" |

**中文与英文措辞差异（同一条目）**：
- `ru_location_str_gp`（英文）："…and it is also used to **support advertising**."
- `privacy_policy_one_for_preload_noi18n` §4（中文）："我们可能会读取您的上网记录；在必要情况下，还会向您申请访问位置信息。"

即**位置用于广告投放这一用途，只在英文资源里出现，中文首启文本未提**。

### 5.4 后台采集边界

`ACCESS_BACKGROUND_LOCATION` **未声明**（§1.5）。因此位置、GNSS、小区信息在应用退到后台后由系统直接拒绝；`wt9.e.h()` 的基础模式门控会先返回空。

但**后台保活相关的权限是齐备的**：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`FOREGROUND_SERVICE_MICROPHONE`、`FOREGROUND_SERVICE_MEDIA_PROJECTION`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、`RECEIVE_USER_PRESENT`、`WAKE_LOCK`、`SCHEDULE_EXACT_ALARM`，配合 `com.xingin.xhs.matrix.permission.PROCESS_SUPERVISOR` 多进程监督。首启文本 §9 以"本应用退出后，可能仍需在后台保持网络连接，以便为您实时接收消息"一句概括，**未提及前台服务类型（麦克风/投屏/数据同步）各自的后台常驻能力**。

---

## 6. 越范围 / 未告知收集的具体项

以下每一项都给出精确的判定依据。

### 6.1 WLAN MAC：一条访问器返回字面量，另一条真实读取并进入公共参数

首启文本 `privacy_policy_one_for_preload_noi18n` §2 与 `privacy_policy_for_preload_basemode_no_i18n` §2
明确列出 **"WLAN MAC 地址"**；静态代码中存在**两条互不相干的实现路径**，必须分别判定。

**路径 A（`com.xingin.u.p.c`）——不取值的访问器**

`getWAPMacAddress()` / `getWifiIp()` / `getWifiSSid()` 直接 `return a.a.b.a.a.a;`，该常量初值为字面量
**`"<absent>"`**；`getWifi()` 是空方法体。这条路径不产生真实 MAC。`libxyasf.so` 的 `TelephonyNetwork`
子消息中 MAC/SSID 字段仍留在 schema 内（历史兼容），走此路径时填入的同样是 `"<absent>"`。

**路径 B（`com.xingin.utils.core.q.i()`）——真实 MAC 采集器（注册 `80600`/`80601`）**

```java
public static String i() {
    if (isEmpty(f)) {                                  // 无缓存
        if (isEmpty(g)) {
            wifiInfo = yt9.o.a(wifiManager);           // WifiInfo，注册 61008
            if (wifiInfo == null) mac = "02:00:00:00:00:00";
            else {
                xt9.d.a.c(80600, null);                // 注册表打点
                mac = wifiInfo.getMacAddress();        // 真实读取
            }
            if (!"02:00:00:00:00:00".equals(mac)) g = mac;
            else {                                     // 占位值 → 系统接口回退
                for (NetworkInterface ni : NetworkInterface.getNetworkInterfaces()) {
                    if ("wlan0".equals(ni.getName())) { ni.getHardwareAddress(); /* 80601 */ }
                }
                ni = NetworkInterface.getByInetAddress(localAddr); ni.getHardwareAddress(); // 80601
                // 仍无值 → getprop wifi.interface + cat /sys/class/net/<if>/address
            }
        }
        f = mac;
    }
    return f;
}
```

回退链精确为 **`WifiInfo.getMacAddress()` → wlan0 `NetworkInterface.getHardwareAddress()` → `NetworkInterface.getByInetAddress()` → `getprop wifi.interface` + `cat /sys/class/net/<if>/address`**；
`q.l(String, String...)` 零变参形态即 `!"02:00:00:00:00:00".equals(mac)`，用于把系统占位值排除掉。

**可达性与去向（dexdump 反汇编确认的两个真实调用点）**

| 调用点 | 门控 | 结果去向 |
| --- | --- | --- |
| `com.xingin.account.b0.a(Map)`（`dex/classes5.dex`） | `SDK_INT <= 29` 才调用 `q.i()`，否则置 `""` | `map.put("mac", value)` —— **账号公共参数 map 的 `mac` 键**，同时以 `e72.e.MAC` 打点；该方法在 `classes5`/`classes14`/`classes16` 中有大量调用方，是请求公共参数注入器 |
| `a76.d`（`dex/classes12.dex`） | 同样 `SDK_INT <= 29` | `map.put("MAC", …)` —— `getDeviceParamsByName` 的设备参数字典 |

**判定：未告知（读取已披露，上传未披露）。**

- 首启文本只说"**读取**您的 … WLAN MAC 地址"，没有任何一处说明该值会作为请求公共参数随
  `/api/...` 上传（§5.3 对所有指纹上传的判定同为"未述"）。
- 实际在 **Android ≤ API 29** 上读取到真实硬件 MAC 并放入 `mac` 键上传；**API > 29 时值为空串**
  （由 `Build.VERSION.SDK_INT` 分支决定），并非"能力已废弃"。
- 因此首启文本与实现的准确关系是：**声明的采集能力真实存在且会上传，缺口在"上传用途未披露"**；
  同时另一条 `u.p.c` 访问器返回 `"<absent>"` 造成代码层面的双轨，不能据其得出"MAC 采集已下线"的结论。

- 依据：`re/jadx_1718/sources/com/xingin/u/p/c.java`、`com/xingin/utils/core/q.java:212,232,275`、
  `dex/classes5.dex` 中 `Lcom/xingin/account/b0;` 方法 `a:(Ljava/util/Map;)Ljava/util/Map;` 反汇编、
  `dex/classes12.dex` 中 `La76/d;` 反汇编、`deepdive/xyasf-device-fingerprint.md` §6.1。

### 6.2 剪贴板读取带调用方归因

- 实现：`yt9.e.a(ClipboardManager)` 内先调 `IClipboardProxy.reportGeneralGetPrimaryClipTrace()`，再执行 `getPrimaryClip()`；并打印 `"access getPrimaryClip once"`。
- `ClipboardProxyImpl.reportGeneralGetPrimaryClipTrace()` 的核心逻辑：

```java
if (!switch("android_clipboard_get_primary_clip_hook_switch")) return;   // 远程开关
if (threadLocal.get()) { threadLocal.set(false); return; }              // 线程内只报一次
if (switch("android_clipboard_extract_caller_switch")) {
    StackTraceElement[] st = new Throwable().getStackTrace();
    // 逐帧匹配，跳过 e.f292229b / e.f292230c 两个类名，
    // 组装 "at <cls>.<method>(<file>:<line>)"
}
```

- 判定：**未告知**。用户侧无任何剪贴板相关文案（`privacy_permission_clipboard` 只是设置页入口名）；但实现会**提取调用方类名、方法名、文件名、行号**并上报。这属于对读取行为自身的审计数据上传，比"读取剪贴板"更进一步。
- 关联设置项：`setting_privacy_clipboard_switch_desc`＝"关闭后，将无法自动识别你复制的口令以展示相应内容"——即存在关闭开关，但开关语义是"关闭功能"，不是"关闭上报"。
- 依据：`com/xingin/xhs/copylink/ClipboardProxyImpl.java`、`yt9/e.java`、`com/xingin/android/redutils/clipboard/spi/ClipboardManagerProxyImpl.java`（`70001`）。

### 6.3 相册位置与 EXIF 的范围超出"读取您选择的内容"

- 文本 §3："仅用于拍摄和读取您选择的内容"。
- 实现：声明了 `android.permission.ACCESS_MEDIA_LOCATION`（访问媒体经纬度），并提供**独立开关** `privacy_permission_album_location_info_tips`＝"开启后，将在发布时依据您相册中照片和视频的位置信息为您推荐拍摄地点"。
- 另有第三方相册增强 SDK 的联合文案 `ru_capa_album_permission_msg`（日文资源）明确写道："…また、他の場面でもデバイス内の画像・動画・ファイルにアクセスし、識別・解析・制作・保存が可能になります。"（"在其他场景也会访问设备内的图片/视频/文件，并可能进行识别、解析、制作、保存"）。
- 判定：**措辞不一致**。首启文本"仅用于读取您选择的内容"与 `ACCESS_MEDIA_LOCATION`（全相册经纬度）+ 第三方 SDK 的"识别、解析"表述存在范围落差。中文资源中不存在与 `ru_capa_album_permission_msg` 对应的段落。
- 依据：manifest `ACCESS_MEDIA_LOCATION`；`resources.arsc` 中 `privacy_permission_album_location_info_tips`、`ru_capa_album_permission_msg`（`2131901897`）。

### 6.4 `ContentResolver` 代理（`Privacy_Network_Check` 之外的通用查询）

- `com/xingin/xhs/thread_monitor_lib/java_hook/proxy/ContentResolverProxy.java` 注册 API 号 `22001`；`yt9.f` 另注册 `22002`。
- 文本 §4 只说"可能会读取您的上网记录"。
- 判定：**未述**。`ContentResolver` 是通用通道，可查询任意 `content://`（日历、联系人、媒体、`Settings.Secure` 等）；文本只把该能力描述为"上网记录"。静态分析可确认出口与 API 号，具体查询串取决于运行期参数。
- 依据：`ContentResolverProxy.java`、`yt9/f.java`。

### 6.5 已装应用列表：四种取法并存

| 取法 | 实现 | 出口 |
| --- | --- | --- |
| 命令侧 | `Runtime.exec("sh")` → `"\n"` 分隔逐行，取每行最后一个空格后的进程名 | `a.a.a.a.a.n.b.a()` |
| SDK 侧 | `ActivityManager.getRunningAppProcesses()` | `a.a.a.a.a.n.b.b()`（`u7c.k.e`） |
| 查询侧 | `PackageManager.queryIntentActivities` / `queryBroadcastReceivers` / `queryIntentServices` / `queryIntentContentProviders` | `wt9.g` |
| 持久化侧 | `ILoginProxy.getAllPackageInfo()` → `all_package_info` SP → 逐项取 `packageName` 拼 `;` | `com.xingin.xhs.net.t.b()` |

- 文本 §2 只声明"已安装软件列表"。
- 判定：**未述**。文本未区分"已安装应用列表"与"**运行中进程列表**"——后者通过 `ps` 与 `getRunningAppProcesses` 获取，属于不同的数据类别（可反映当前使用行为），且无对应弹窗文案。
- 依据：`a/a/a/a/a/n/b.java`、`a/a/a/a/a/o/a.java`、`wt9/g.java`、`com/xingin/xhs/net/t.java`。
- 附带说明：`com.xingin.xhs.manual.dump` 权限 + `nativedump.easyfloat.permission.PermissionFragment` 存在手工 dump 通道，属调试能力（`android:debuggable` 或内部账号触发）。

### 6.6 `libxyasf.so` 指纹上报容器伪装为图片

- 判定：**未告知**。上报是 `multipart/form-data`，part 名 `file`、文件名 `image.jpg`、MIME `image/jpeg`，承载的却是 8 个子消息 51 字段的 protobuf（经 16 字节前缀 + 全量 XOR `0x70` 变换）。
- 依据：`deepdive/xyasf-device-fingerprint.md` §4。
- 说明：容器选型本身不等于隐瞒（用户不可见网络层），但结合"首启文本未提及任何上传"，该链路整体属未告知范围。

### 6.7 基础模式下仍上报设备信息

- 判定：**未告知**。用户点"不同意"后进入"基础浏览模式"，应用此时仍通过 `/api/sns/v1/basemode/report` 上报 `platform`、`versionName`、`channel`、`deviceId`、`baseModeId`、`projectId`、`appId`、`build`、`cpuName`、`overseasChannel`、`eventName`、`extraMap`、`timestamp`、`sign` 共 14 个字段。
- `baseModeId` 来自 `tt9.a.a.a(Context)`，即 `ug_preload_device_info` 里的 `device_uuid`（首次为随机 UUID，之后持久）。
- 依据：`com/xingin/privacy/net/BaseModeServices.java`、`bu9/c.java`、`tt9/a.java`、`cu9/e.java`。

### 6.8 `traceIMEI()` 为空实现（合规遮蔽）

- `PrivacyTracerImpl.traceIMEI()` 方法体为空，而 `traceGAID()`/`traceOAID()` 分别上报 `80300`/`80100`。
- 判定：**内部合规登记的缺口**。真实 IMEI 读取路径（`wt9.h.c` → `80000`）**确实**经过注册表并携带调用栈；但 SPI 层的 `traceIMEI` 出口是空。即两套机制并存，SPI 那套对 IMEI 不生效。
- 依据：`com/xingin/privacy/runtime/consumer/PrivacyTracerImpl.java`、`wt9/h.java`。

---

## 7. 隐私协议措辞 vs 实际数据范围

### 7.1 文本载体清单（全部在样本内）

首启/降级相关文本共 **28 个资源**（`resources.arsc` 明文，中/繁/英/日/韩/法/西/俄 8 语）：

| 族 | 资源名 | 字符数（zh） |
| --- | --- | ---: |
| 首启提示 | `privacy_policy_one_for_preload_noi18n` | 1475 |
| 首启 v2 | `privacy_policy_one_for_preload_v2_noi18n` | 1221 |
| 基础模式 | `privacy_policy_for_preload_basemode_no_i18n` | 1206 |
| 基础模式 v2 | `privacy_policy_for_preload_second_no_i18n` | 1208 |
| 拒绝后提示 | `privacy_policy_two_for_preload_noi18n` | 863 |
| 拒绝后提示 v2 | `privacy_policy_two_v2_no_i18n` | 425 |
| 精简版 | `privacy_policy_one_v2_no_i18n` | 915 |
| 更新提示 | `privacy_policy_tip_default` | 195 |
| 按钮 | `privacy_dialog_commit_first`/`cancel_first`/`commit_second*`/`cancel_second*`/`disagree_*`/`exit_preload` | 2–8 |
| 无障碍 | `privacy_policy_one_for_preload_accessibility` 等 6 条 | — |
| 英文专版 | `privacy_policy_one_english`、`privacy_policy_one_english_gp`、`matrix_card_privacy_notice_content` | — |

### 7.2 协议正文**不在样本内**

所有版本的《隐私政策》《用户协议》正文均以 **H5 链接**给出，链接清单：

| 名称 | URL |
| --- | --- |
| 用户协议 | `https://agree.xiaohongshu.com/h5/terms/ZXXY20220331001/-1` |
| 隐私政策 | `https://agree.xiaohongshu.com/h5/terms/ZXXY20220509001/-1` |
| 儿童/青少年个人信息保护规则 | `https://oa.xiaohongshu.com/h5/terms/ZXXY20220516001/-1` |
| 英文隐私政策 | `https://fe.xiaohongshu.com/apps/vincent/ditto/v2?id=e65493b60b6c439781db718480daa0c6&no_mask=true` |
| 英文未成年人规则 | `https://fe.xiaohongshu.com/apps/vincent/ditto/v2?id=6a77b982f08d46889a4943b7aba4b2bd&no_mask=true` |
| GP 版隐私政策 | `https://agree.xiaohongshu.com/h5/terms/-1/419` |
| 新版服务条款 | `https://agree.xiaohongshu.com/h5/terms/ZXXY20251205003/-1` |
| 新版隐私政策 | `https://agree.xiaohongshu.com/h5/terms/ZXXY20251205002/-1` |
| 社区规范 | `http://www.xiaohongshu.com/crown/community/privacy` |

**结论**：APP 内可静态验证的只有**摘要式告知文本**（即 §5.1 的 13 条）与**权限用途说明**（§2.5 的 14 条）。协议正文为服务端下发 H5，**样本内无正文副本**，因此"协议正文措辞 vs 实际"这一层无法在静态样本内闭合，只能对比摘要文本。这是范围边界，不是缺口——摘要文本按《App 违法违规收集使用个人信息行为认定方法》即"告知"载体。

### 7.3 摘要文本的三处措辞落差

| # | 摘要措辞 | 实际 | 落差性质 |
| --- | --- | --- | --- |
| 1 | §3（中文）"仅用于拍摄和读取您选择的内容" | `ACCESS_MEDIA_LOCATION` 全相册经纬度；`ru_capa_album_permission_msg` 明确"其他场景…识别、解析" | 范围收窄化表述 |
| 2 | `ru_location_str_gp`（英文）"also used to **support advertising**" | 中文对应条目 `privacy_policy_one_for_preload_noi18n` §4 未提广告 | 用途披露仅在英文资源 |
| 3 | §4"可能会读取您的**上网记录**" | `ContentResolver` 通用代理（`22001`/`22002`） | 能力窄化描述 |

### 7.4 权限用途说明表的覆盖度

`m82.m.f266434d` 覆盖 9 类权限（14 条映射）——**不含** `READ_PHONE_STATE`、`SYSTEM_ALERT_WINDOW`、`NFC`、`USE_BIOMETRIC`、`ACCESS_MEDIA_LOCATION`、`BLUETOOTH_SCAN`（未声明）、`SCHEDULE_EXACT_ALARM`。

- `READ_PHONE_STATE`：走独立弹窗（§2.6），文案在 `login_permission_open_tips`；
- `ACCESS_MEDIA_LOCATION`：只在设置页有开关说明（`privacy_permission_album_location_info_tips`），**申请时无说明弹窗**；
- `SYSTEM_ALERT_WINDOW` / `NFC` / `SCHEDULE_EXACT_ALARM`：无统一说明弹窗，仅在业务点触发系统跳转（`alpha_live_new_float_permission_guide` 等）。

---

## 8. 汇总发现（按严重度）

| 级别 | 发现 | 依据 |
| --- | --- | --- |
| 高 | 无障碍服务列表（`enabled_accessibility_services`）、运行进程列表（`ps` + `getRunningAppProcesses`）、传感器全清单、剪贴板全文及**调用方类名/方法/行号**，四类均实采且**无对应用户告知文案** | §5.2、§6.2、§6.5 |
| 高 | `READ_PHONE_STATE` 仅声明在 split APK，只审 base 会漏；申请弹窗**一次即永久标记**，业务埋点直接命名 `IMEI_APP`/`IMEI_SYSTEM` | §1、§2.6 |
| 中 | 位置用于广告投放的用途只在英文资源披露，中文首启文本未提 | §7.3 |
| 中 | 按卡槽 `getImei(int)`（`80002`，API ≥ 26）与 `getMeid(int)`（`81801`）在首启文本中无对应措辞 | §3.2.1、§5.2 |
| 中 | 基础浏览模式（未同意隐私政策）下仍向 `/api/sns/v1/basemode/report` 上报 14 字段设备/构建信息 | §6.7 |
| 高 | WLAN MAC 在 API ≤ 29 上由 `com.xingin.utils.core.q.i()` 真实读取（`80600`/`80601`），并以 `mac` 键进入账号公共参数随请求上传；首启文本只写"读取"，未披露该上传用途 | §6.1、§5.3 |
| 中 | `ACCESS_MEDIA_LOCATION` 申请时无说明弹窗；相册范围表述"仅读取您选择的内容"与实际范围不符 | §6.3、§7.4 |
| 低 | `PrivacyTracerImpl.traceIMEI()` 为空实现，SPI 层对 IMEI 不生效 | §6.8 |
| 低 | 指纹上报容器伪装为 `image.jpg`/`image/jpeg` | §6.6 |
| 参考 | 应用内置完整隐私合规追踪框架（**55 个五位数 API 号 + 148 个 AppOps 索引**，`apiSource ∈ {1,2,3,4}`，配限频、双路采样、前后台标记与豁免注解），可视为其自认的敏感 API 全谱 | §3 |

---

## 9. 静态分析边界（不可在此层闭合的项）

1. **协议正文**：§7.2 列出的 9 个 H5 均为服务端下发，样本内无正文，无法逐条比对。
2. **服务端消费逻辑**：`as.xiaohongshu.com` 如何用 51 字段做判定、`/api/sns/v1/basemode/report` 的 `extraMap` 具体键值，均在服务端。
3. **`ContentResolver` 实际查询串**：出口与 API 号已定（`22001`/`22002`），具体 `content://` 由运行期参数决定。
4. **已装应用列表的实际上报时刻**：`all_package_info` 的写入方未在静态树中定位到调用点（SP 键存在、读取方 `LoginProxy.getAllPackageInfo` 明确），需运行期跟踪确认写入时机。
5. **远程开关默认值**：`andr_enable_coarse_location_check`(默认 0)、`android_clipboard_get_primary_clip_hook_switch`、`android_clipboard_extract_caller_switch`、`close_login_setting_all_package`(默认 1) 的真实下发值随账号/版本变化。
6. **`wt9.i.a` 的 `c` 标志**：`c = Boolean.TRUE` 在 `b(String)` 末尾无条件设置，与 `a()` 的判空逻辑（`c == null` 返回 false）配合意味着**配置解析一旦执行，`a()` 恒为真**；该语义在动态下的实际效果需运行验证。
7. **厂商 ROM 差异**：`com.xiaomi.security.permission.ACCESS_XSOF`、`com.hihonor.security.permission.ACCESS_THREAT_DETECTION` 等是否被授予、授予后能取到什么，依赖具体 ROM。

---

## 10. 证据索引

| 主题 | 证据位置 |
| --- | --- |
| manifest 全量 XML 树 | `re/privacy/manifest_tree.txt`（8481 行） |
| badging（权限/feature/locale） | `re/privacy/badging_base.txt` |
| 权限并集与来源拆分 | `re/privacy/perm_base.txt`、`perm_config.arm64_v8a.txt`、`perm_config.mdpi.txt`、`perm_union.txt` |
| 全量资源字符串（269 580 行） | `re/privacy/resources_values.txt` |
| 解析后的字符串表（133 680 条） | `re/privacy/strings_parsed.json` |
| 隐私/权限资源全集 | `re/privacy/permission_strings.txt`、`privacy_strings.txt`、`long_prompts.txt` |
| DEX 类清单（21 个 dex） | `re/privacy/dex_classes.json`、`class_hits_apponly.txt` |
| 隐私框架单类导出 | `re/jadx_privacy/`（21 类）、`re/jadx_privacy2/`（18）、`re/jadx_privacy3/`（11）、`re/jadx_privacy4/`（22）、`re/jadx_privacy5/`（1）、`re/jadx_privacy6/`（12）、`re/jadx_privacy7/`（3）、`re/jadx_privacy8/`（12）、`re/jadx_privacy9/`（6） |
| API ID → 出口类 映射 | `re/privacy/` 全树扫描脚本（**55 个五位数 ID** 清单见 §3.2.1） |
| `xt9.d.a` 完整反编译 | `re/jadx_privacy10/xt9.d.java`（229 行，`--show-bad-code --comments-level debug`） |
| 远程开关/采样配置解析 | `re/jadx_privacy3/wt9.i.java`（`wt9.b`/`wt9.c` JSON → `d/f/h` + `e/g/i[api]`） |
| AppOps 名称 → 索引（148 项） | `re/jadx_1718/sources/zt9/a.java`、`zt9/b.java`；注册点 `slb/b.java:347`、`vlb/j.java:24`、`ulb/d.java:106` |
| WLAN MAC 真实采集链 | `com/xingin/utils/core/q.java:212,232,275`；`dex/classes5.dex` `Lcom/xingin/account/b0;` 与 `dex/classes12.dex` `La76/d;` 反汇编 |
| `PrivacyThrowable` 调用点定位 | `re/jadx_privacy/com.xingin.privacy.runtime.consumer.PrivacyThrowable.java`（`findSystemApi`） |
| 资源名 → 数值 解析 | `re/privacy/resolve.py` |

已发布的相关文件：[risk.md](risk.md)（风控全景）、[deepdive/risk-controls.md](deepdive/risk-controls.md)（组件边界）、[deepdive/xyasf-device-fingerprint.md](deepdive/xyasf-device-fingerprint.md)（51 字段指纹）、[deepdive/upload.md](deepdive/upload.md)（上传范围）、[completeness.md](completeness.md)（完成度矩阵）。
