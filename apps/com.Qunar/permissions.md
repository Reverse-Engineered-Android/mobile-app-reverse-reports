# 权限与导出面

## 1. 权限清单

`<uses-permission>` 共 **52** 条，去重 **51** 个，其中 **9** 条为第三方自定义权限。

### 1.1 能力型（与数据采集直接相关）

| 权限 | 客户端用途（调用点） |
|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | `QLocUtils.onlyForPltGetNewestCacheLocation()` → cparam `lat`/`lgt`；`qpub_locationUpload` |
| `ACCESS_MEDIA_LOCATION` | 相册图片 EXIF 地理位置 |
| `CAMERA` | 扫码、证件、`CaptureAndUploadActivity` |
| `RECORD_AUDIO` | 语音搜索、IM 语音 |
| `READ_CONTACTS` | 仅声明；未见进入上报链路的调用闭环 |
| `WRITE_CALENDAR` | 仅声明（无 `READ_CALENDAR`）；行程写入日历 |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` | 图片选择、视频、语音 |
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` | `Cmd.a()` 的 `ls -al /sdcard/Android/data/` |
| `GET_TASKS` | 进程/任务栈读取（`Checker` 类判据依赖） |
| `BLUETOOTH` / `BLUETOOTH_CONNECT` | `Checker.c()` 读蓝牙名作为模拟器判据 |
| `NFC` | 身份读取、门禁卡 |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | 支付/登录生物识别 |
| `DETECT_SCREEN_CAPTURE` | 检测截屏（支付安全） |
| `CAPTURE_AUDIO_OUTPUT` | 系统级音频采集（系统签名才可授予） |

### 1.2 系统/安装型

`REQUEST_INSTALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`SYSTEM_OVERLAY_WINDOW`、
`MOUNT_UNMOUNT_FILESYSTEMS`、`WRITE_SETTINGS`、`CHANGE_WIFI_STATE`、
`CHANGE_NETWORK_STATE`、`GET_PACKAGE_SIZE`、`SCHEDULE_EXACT_ALARM`、
`RECEIVE_USER_PRESENT`、`FOREGROUND_SERVICE`（含 `DATA_SYNC`、
`MEDIA_PROJECTION`、`MICROPHONE` 三个具体类型）、`POST_NOTIFICATIONS`、
`WAKE_LOCK`、`VIBRATE`、`FLASHLIGHT`、`MODIFY_AUDIO_SETTINGS`。

### 1.3 网络与推送

`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE` +
9 条第三方自定义权限：`com.Qunar.permission.MIPUSH_RECEIVE`、
`qutui.permission.GPushReceiver.com.Qunar`、
`getui.permission.GetuiService.com.Qunar`、
`com.android.launcher.permission.INSTALL_SHORTCUT`、
`com.huawei.android.launcher.permission.CHANGE_BADGE`、
`oplus.permission.settings.LAUNCH_FOR_EXPORT`、
`com.asus.msa.SupplementaryDID.ACCESS`、`freemme.permission.msa`、
`freemme.permission.msa.SECURITY_ACCESS`。

四条 `com.Qunar` 自有权限被 `<permission>` 声明为本应用定义：
`qutui.permission.GPushReceiver.com.Qunar`、`com.Qunar.permission.MIPUSH_RECEIVE`、
`com.Qunar.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`、
`getui.permission.GetuiService.com.Qunar`。

**注意**：`<uses-permission>` 中**没有** `READ_PHONE_STATE`，但
`PermissionInfoCollector` 仍会上报 `phoneStateAuthorized` 字段（值为 "0"）。
这说明该字段表达的是“是否授权”而非“是否已采集”。

## 2. 导出组件

438 个组件：`activity` 391、`activity-alias` 3、`service` 23、`receiver` 12、
`provider` 9。显式 `android:exported="true"` 的 **26** 个，其中
**22 个没有 `android:permission`**。

> 计数口径：只统计清单里显式写出的 `android:exported="true"`（26 处字面量）。
> 该清单没有任何“带 `intent-filter` 但省略 `exported`”的组件（0 个），
> 所以不存在隐式导出的额外项。`activity-alias` 是独立组件，且其
> 3 个名字与 3 个真实 Activity 的类名重合（见下），不可与 Activity 混为一谈。

### 2.1 导出且无权限保护（22）

| 类型 | 组件 |
|---|---|
| activity | `com.mqunar.pay.inner.activity.pay.WXPayEntryActivity` |
| activity | `com.mqunar.pay.inner.react.ThirdPayActivity` |
| activity | `com.mqunar.atom.attemper.ulink.UlinkRouteActivity` |
| activity | `com.alipay.sdk.app.AlipayResultActivity` |
| activity | `com.alipay.sdk.app.PayResultActivity` |
| activity | `com.mqunar.splash.SplashActivity` |
| activity | `com.mqunar.atom.quicksetting.pricecompare.CaptureAndUploadActivity` |
| activity | `com.mqunar.atom.share.weixin.WXEntryActivity` |
| activity | `com.mqunar.dispatcher.DispatcherActivity` |
| activity | `com.mqunar.dispatcher.DispatcherProxyHomeActivity` |
| activity | `com.xiaomi.mipush.sdk.NotificationClickedActivity` |
| activity-alias | `com.Qunar.wxapi.WXPayEntryActivity` → `…pay.WXPayEntryActivity` |
| activity-alias | `com.mqunar.atom.share.wxapi.WXEntryActivity` → `…share.weixin.WXEntryActivity` |
| activity-alias | `com.Qunar.wxapi.WXEntryActivity` → `…share.weixin.WXEntryActivity` |
| service | `com.baidu.location.f` |
| service | `com.xiaomi.mipush.sdk.MessageHandleService` |
| service | `com.xiaomi.push.service.XMPushService` |
| service | `com.mqunar.qutui.NetService` |
| receiver | `com.mqunar.dispatcher.GlobalConnectChangedReceiver` |
| receiver | `com.mqunar.atom.push.MiPushReceiver` |
| receiver | `com.xiaomi.push.service.receivers.NetworkStatusReceiver` |
| receiver | `com.mqunar.atom.widget.sign.QAppSignWidgetProvider` |

这些组件的共同点是“必须能被外部唤起”：支付 App 回调、微信回调、分享回调、
深链分发、桌面小组件、推送唤起。它们是功能必需导出，但攻击面在于
**入参未在清单层收窄**——是否有来源校验只能在各自 `onCreate`/`onReceive`
里逐个人工确认。

三个 `activity-alias` 在清单里**只以 alias 形式出现**，不对应任何独立声明的
`<activity>`，名字也与 `targetActivity` 的类名不同：
`com.Qunar.wxapi.WXPayEntryActivity` → `com.mqunar.pay.inner.activity.pay.WXPayEntryActivity`；
`com.Qunar.wxapi.WXEntryActivity` 与 `com.mqunar.atom.share.wxapi.WXEntryActivity`
均 → `com.mqunar.atom.share.weixin.WXEntryActivity`。因此“微信回调入口”在
清单里是 3 个 alias 组件，加上被指向的 2 个真实 Activity 类，共 5 个组件名，
不是 6 个同名条目。

### 2.2 导出且有权限保护（4）

| 组件 | 保护权限 |
|---|---|
| `com.xiaomi.mipush.sdk.PushMessageHandler` | `com.xiaomi.xmsf.permission.MIPUSH_RECEIVE` |
| `com.mqunar.atom.quicksetting.pricecompare.PriceCompareTileService` | `android.permission.BIND_QUICK_SETTINGS_TILE` |
| `com.mqunar.atom.push.GPushReceiver` | `qutui.permission.GPushReceiver.com.Qunar` |
| `androidx.profileinstaller.ProfileInstallReceiver` | `android.permission.DUMP` |

### 2.3 Provider（9 个，全部 `exported=false`）

| Provider | authority 后缀 | `grantUriPermissions` |
|---|---|---|
| `com.mqunar.qimsdk.base.ImFileProvider` | `.qunarim.authority` | true |
| `androidx.core.content.FileProvider` | `.install.authority` | true |
| `com.mqunar.atom.share.ShareProvider` | `.share.image.provider` | true |
| `org.acra.builder.AppStartCrashProvider` | `.app.start.crash.install` | true |
| `com.google.mlkit.common.internal.MlKitInitProvider` | `.mlkitinitprovider` | — |
| `androidx.startup.InitializationProvider` | `.androidx-startup` | — |
| `com.mercury.sdk.core.widget.BYFileProvider` | `.mry_fileprovider` | true |
| `com.reactnativecommunity.webview.RNCWebViewFileProvider` | `.fileprovider` | true |
| `com.getui.gtc.base.GtcProvider` | `.getui.gtc.provider` | — |

5 个 `FileProvider` 类组件开 `grantUriPermissions`，是 `content://` 分享/安装/
崩溃日志的常规做法；`exported=false` + 临时 URI 授权是正确组合。

## 3. 权限 → 数据 → 端点的对应

| 数据 | 采集点 | 端点 |
|---|---|---|
| 位置 | `QLocUtils.onlyForPltGetNewestCacheLocation`；`LoginParam`；`QunarGPSLocationStrategy.newestGPS` | Hotdog cparam `lat`/`lgt`；`qpub_locationUpload` |
| 外部存储目录列表 | `com.qunar.rc.utils.Cmd.a()` | `NUtils.cd()` 的指纹体 → `FpService` multipart |
| `SystemProperties`（6 个 `ro.*`） | `Cmd.b()` | 同上（经 `Checker.c()` 汇总） |
| 已安装应用（含系统/非系统分流） | `ext.ai()`（非系统）、`SDInfo.getSysAppList()`（系统）、`AppListInfoCollector` | `NUtils.cd()` 与 `upDevice.do` |
| 蓝牙名/传感器/闪光灯 | `Checker.c()` | `NUtils.cd()` |
| keystore attestation 链 | `Checker.a()` | `NUtils.cd()` |
| TLS 证书链 + 出口 IP + DNS | `d.getcc()` | `NUtils.cd()` |
| 8 项运行时授权状态 | `PermissionInfoCollector` | `upDevice.do` |
| SIM 运营商 / 网络类型 | `cpHasPrivacyInfo`、`LoginParam` | cparam `mno`/`nt`/`nettype` |

## 4. 越权判断

**客户端未发现越权调用。** 依据：

- 无 `Runtime.exec("su ...")`、无 `pm grant`、无 `Mount` 命令执行；
  `Cmd` 唯一的 `exec` 是 `ls -al /sdcard/Android/data/`。
- 无 `Instrumentation`/`AccessibilityService` 自启动。
- `READ_CONTACTS`、`WRITE_CALENDAR`、`MOUNT_UNMOUNT_FILESYSTEMS`
  只有声明，未找到“读→写库→上报”的调用闭环。
- 注意：本报告不宣称“不存在任何越权”，只宣称在上述静态检索范围内未发现闭环。

## 5. 提权判断

**未发现。** 依据：

- `SYSTEM_ALERT_WINDOW` / `SYSTEM_OVERLAY_WINDOW` / `REQUEST_INSTALL_PACKAGES` /
  `WRITE_SETTINGS` 有声明；`REQUEST_INSTALL_PACKAGES` 对应
  `androidx.core.content.FileProvider` + `.install.authority`，
  属正常“下载 APK 后引导安装”流程，未见静默安装。
- 22 个无保护导出组件中无 `BroadcastReceiver` 监听 `PACKAGE_ADDED` 后
  自动拉起安装的行为。
- `DETECT_SCREEN_CAPTURE` / `CAPTURE_AUDIO_OUTPUT` 属防御性权限，未见反向利用。

## 6. 未经告知 / 超范围判断

### 6.1 未经告知风险

`<queries>` 显式声明 183 个包名（182 去重），按类别：

| 类别 | 数量 | 示例（存在性证据） |
|---|---|---|
| Root / Hook / 反检测工具 | 17 | Magisk（2 个签名变体）、KernelSU、KernelSU Next、SukiSU Ultra、APatch（2 个变体）、APatch Next、`com.variable.apkhook`、`com.tsng.hidemyapplist`、`io.github.vvb2060.mahoshojo`、`com.byxiaorun.detector`、`icu.nullptr.nativetest`、3 个 secureguard |
| 虚拟摄像头 | 4 | `virtual.camera.app`、`com.example.vcam`、`com.wangyiheng.vcamsx`、`com.pedia.vcamsx` |
| 银行/金融 App | 32 | 工行、建行、农行、中行、招行、浦发、邮储、中信、兴业、华夏、光大、上海银行、北京银行、民生、平安、深发展系列等 |
| 支付 | 11 | 支付宝（含 RC 变体）、云闪付、微信、各钱包 |
| OAID 提供方 | 6 | `com.mdid.msa`、`com.samsung.android.deviceidservice`、`com.coolpad.deviceidsupport`、`com.heytap.openid`、`com.qiku.id`、`com.vivo.vhome` |
| 其它知名 App | 100+ | 淘宝、京东、拼多多、美团、携程、小红书、高德、百度、快手、抖音、网易云音乐等 |

`<queries>` 本身是 Android 11+ 的可见性声明，只有被声明的包名才能被
`PackageManager` 查询到。声明 183 个包名意味着应用**主动要求**能查询这些
应用是否安装。这与 §6.2 的应用列表上报形成完整闭环。

**结论**：隐私政策是否逐项告知“会读取已安装应用列表”无法从客户端证明；
从代码侧看，采集能力与上报通道都存在，故判为“告知粒度不足风险”。

### 6.2 超范围获取

| 项 | 判定 |
|---|---|
| 应用列表上报 | **存在**：`ext.ai()` 上报包名、`lastUpdateTime`、`firstInstallTime`、`sourceDir`、`versionName`、`flags`、`appUid`；`AppListInfoCollector` 上报 `appList` |
| 外部存储目录列表 | **存在**：`Cmd.a()`，前 50 项 |
| 出口 IP | **存在**：`d.getcc()` 的 `localIp` |
| 对端证书链 | **存在**：`d.getcc()` 全链 Base64 |
| 位置 | **条件性**：仅在拿到缓存定位时写入；`QLoc` 权限被拒时静默跳过 |
| 联系人 / 短信 / 通话记录 | **未发现**：无对应权限，无 `ContentResolver` 查询 |
| 剪贴板 | 未在本次检索范围内发现主动读取 |
| 相册全量 | **未发现**：只有用户选择路径 |

## 7. 静态化说明

权限与组件清单直接来自 `jadx` 解码的 `AndroidManifest.xml`
（SHA-256 `8dc48ccb…f4a5f`）。是否真有越权/提权行为，需要在运行期验证；
本报告只陈述静态结构。
