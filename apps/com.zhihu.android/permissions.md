# 权限与导出面

## 1. 清单与构建参数

| 项 | 值 |
| --- | --- |
| 包名 | `com.zhihu.android` |
| versionName | `11.10.0` |
| versionCode | `41012` |
| minSdkVersion | `21` |
| targetSdkVersion | `34` |
| compileSdkVersion | `31` |
| `android:usesCleartextTraffic` | `true` |
| `android:networkSecurityConfig` | `@xml/s` |
| DEX | 18 个（`classes.dex` … `classes18.dex`） |
| native | 163 个 `.so`（arm64-v8a） |

`usesCleartextTraffic="true"` 允许明文 HTTP；实际收窄由 `@xml/s` 网络安全配置决定
（静态资源为二进制 XML，未展开逐域名规则）。

## 2. 权限声明（66 条唯一 `<uses-permission>`）

清单共 66 条唯一权限名，其中 **48 条为 `android.permission.*`**、18 条为厂商/自研/第三方。
48 条平台权限：

```
ACCESS_COARSE_LOCATION            ACCESS_FINE_LOCATION
ACCESS_LOCATION_EXTRA_COMMANDS    ACCESS_MEDIA_LOCATION
ACCESS_NETWORK_STATE              ACCESS_WIFI_STATE
BLUETOOTH                         BLUETOOTH_ADMIN
BROADCAST_CLOSE_SYSTEM_DIALOGS    CAMERA
CHANGE_CONFIGURATION              CHANGE_NETWORK_STATE
CHANGE_WIFI_MULTICAST_STATE       CHANGE_WIFI_STATE
FLASHLIGHT                        FOREGROUND_SERVICE
FOREGROUND_SERVICE_DATA_SYNC      FOREGROUND_SERVICE_MEDIA_PLAYBACK
GET_TASKS                         INSTALL_SHORTCUT
INTERNET                          MODIFY_AUDIO_SETTINGS
MODIFY_PHONE_STATE                MOUNT_UNMOUNT_FILESYSTEMS
POST_NOTIFICATIONS                QUERY_ALL_PACKAGES
READ_CLIPBOARD                    READ_CONTACTS
READ_EXTERNAL_STORAGE             READ_LOGS
READ_MEDIA_AUDIO                  READ_MEDIA_IMAGES
READ_MEDIA_VIDEO                  READ_MEDIA_VISUAL_USER_SELECTED
RECEIVE_BOOT_COMPLETED            RECEIVE_USER_PRESENT
RECORD_AUDIO                      REORDER_TASKS
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS  REQUEST_INSTALL_PACKAGES
SCHEDULE_EXACT_ALARM              SYSTEM_ALERT_WINDOW
VIBRATE                           WAKE_LOCK
WRITE_CALENDAR                    WRITE_EXTERNAL_STORAGE
WRITE_MEDIA_STORAGE               WRITE_SETTINGS
```

18 条非 `android.permission.*`：

```
android.Manifest.permission.DEVICE_POWER        android.Manifest.permission.READ_PHONE_STATE
Manifest.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS
com.android.launcher.permission.INSTALL_SHORTCUT
com.asus.msa.SupplementaryDID.ACCESS
com.google.android.gms.permission.AD_ID
com.hihonor.android.launcher.permission.CHANGE_BADGE
com.hihonor.security.permission.ACCESS_THREAT_DETECTION
com.huawei.android.launcher.permission.CHANGE_BADGE
com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA
com.vivo.notification.permission.BADGE_ICON
com.zhihu.android.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
com.zhihu.android.ReceivePlayerInfo
com.zhihu.android.download.PRIVATE_BROADCAST_PERMISSION
com.zhihu.android.openadsdk.permission.TT_PANGOLIN
com.zhihu.android.permission.MIPUSH_RECEIVE
freemme.permission.msa
getui.permission.GetuiService.com.zhihu.android
```

> 注：`Manifest.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS`、`android.Manifest.permission.*`
> 属大小写/命名不规范的声明，Android 会按字面量处理（小写 `m` 的 `Manifest.` 前缀不是
> 合法包名写法），这些条目实际不生效；`android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS`
> 已由合法条目覆盖。

### 2.1 高风险权限说明

| 权限 | 用途（代码侧） |
| --- | --- |
| `READ_LOGS` | 日志读取，非普通应用可用权限；声明存在但需系统级授权 |
| `QUERY_ALL_PACKAGES` | 应用列表，用于 SDK 归因/风控与 `simulatordetect` |
| `MOUNT_UNMOUNT_FILESYSTEMS` | 历史遗留，targetSdk 34 下不可获得 |
| `MODIFY_PHONE_STATE` | 系统级，普通应用不可获得 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗（播放器小窗） |
| `REQUEST_INSTALL_PACKAGES` | 动态加载/补丁安装（`libDexHelper.so`/`libdexvmp.so`） |
| `READ_CLIPBOARD` | 口令/链接识别 |
| `READ_CONTACTS` | 通讯录（邀请/找朋友） |
| `SCHEDULE_EXACT_ALARM` | 定时任务/提醒 |
| 位置集合（4 条） | 同城、附近、`/zst/events/i` 经纬度上报 |

## 3. 组件与导出面

| 类型 | 声明总数 | `exported="true"` |
| --- | ---: | ---: |
| activity | 299 | 41 |
| service | 56 | 9 |
| receiver | 24 | 9 |
| provider | 33 | 2 |
| **合计** | **412** | **61** |

### 3.1 导出的 Activity（41 个，节选）

`RouterPortalActivity`、`ActionPortalActivity`、`PortalActivity`、`MainActivity`、
`LauncherActivity`、`AuthActivity`、`AutoAuthActivity`、`AccountActionActivity`、
`QDFaceActivity`、`QDAliPayActivity`、`ShareToFeedActivity`、`ShareToMessageActivity`、
`ZhiDaTransparentActivity`、`PushJumpBoardActivity`、`GreenifyActivity`、
`StageOneSafeBootActivity`、`StageTwoSafeBootActivity`、`DetectNetworkActivity`、
`ShortCutRouterActivity`、`ReportShowActivity`、`LaunchAdActivity`、`AdDialogActivity`、
`AdAlphaVideoActivity`、`DeepLinkTrickActivity`、`WXEntryActivity`/`WXPayEntryActivity`（三处）、
`com.tencent.tauth.AuthActivity`、`com.alipay.sdk.app.PayResultActivity`/`AlipayResultActivity`、
`com.sina.weibo.sdk.share.ShareTransActivity`、`cn.fly.id.NFlyIDSYActivity`、
`com.alibaba.wireless.security.open.middletier.fc.ui.ContainerActivity`、
`com.alibaba.alibclinkpartner.smartlink.ALPEntranceActivity` 等。

### 3.2 导出的 Service / Receiver / Provider

- Provider（2）：
  - `com.zhihu.android.cloudid.CloudIdProvider`，authority `com.zhihu.cloud.id`
  - `com.zhihu.android.app.provider.AccountOauthProvider`，authority `com.zhihu.android.account.auth.provider`
- Service（9，节选）：`MiScenePushService`、`GetuiPushService`、`GetuiProcessCareService`、
  `UserAuthenticatorService`、`MyAccountService`、`GTIntentService`、`GService`、
  `UploadLogSDKService`（`miui.permission.USE_INTERNAL_GENERAL_API`）。
- Receiver（9，节选）：`NotificationBroadcastReceiver`、`HotAppWidgetProvider`、
  `HonorWidgetProvider`、`HotSearchAppWidget`、`FollowsAppWidget`、`SearchAppWidgetProvider`、
  **`com.zhihu.android.patch.debug.DebugPatchReceiver`**、`MiMessageReceiver`、
  `ConnectionChangeReceiver`。

### 3.3 需要关注的两点

1. `DebugPatchReceiver` 是补丁下发/调试入口；release 包中是否可达取决于
   `android:enabled` 与运行时校验（静态仅见声明）。
2. `CloudIdProvider`/`AccountOauthProvider` 会暴露设备 ID 与账户 OAuth 能力给
   同设备其它应用（受 authority 与签名/权限约束）；两 authority 均为知乎自有命名空间。

## 4. 越权 / 提权判定

- **系统提权：无。** 未发现 `su`、`Runtime.exec("su")`、root 检查绕过、
  系统服务反射调用隐藏 API 的提权链。`ReflectHelper` 类仅用于兼容性反射。
- **跨应用越权：未见。** 导出组件未发现未加保护的敏感操作；两个 Provider 的
  authority 独立于系统 Provider（如 `contacts`/`media`），不构成系统数据越权。
- **动态代码加载：存在但范围受限。** `REQUEST_INSTALL_PACKAGES` + `libDexHelper.so`/
  `libdexvmp.so` 构成动态 dex/vmp 加载能力，加载对象为本应用云控下发的签名资源。

## 5. 与风控的关联

`QUERY_ALL_PACKAGES`、`READ_LOGS`、位置集合、`READ_CLIPBOARD` 与
[risk.md](risk.md) §2 的设备采集字段共同构成设备侧风控输入；这些权限的
“最小必要”边界见 [privacy.md](privacy.md) §3。
