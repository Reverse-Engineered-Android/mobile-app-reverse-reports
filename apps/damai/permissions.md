# 权限与导出面

## 1. 清单结论

解码后的 `AndroidManifest.xml` 显示 `cn.damai` 声明 58 个
`uses-permission`，包含 322 个 activity、45 个 service、19 个
receiver、14 个 provider。显式 `android:exported="true"` 的组件共 79 个：

| 类型 | exported=true | 需要签名/自定义权限保护的显式导出 |
|---|---:|---:|
| activity | 52 | 0 |
| service | 15 | 4 |
| receiver | 11 | 3 |
| provider | 1 | 1 |

导出 activity 没有声明 `android:permission`；其中 41 个声明了
`intent-filter`。这会扩大外部可直接唤起的入口面，但是否可利用还取决于
每个 activity 对 intent extras、来源包和任务状态的运行时校验。

## 2. 权限分组

### 2.1 网络、状态与后台

```text
INTERNET
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
CHANGE_NETWORK_STATE
CHANGE_WIFI_STATE
FOREGROUND_SERVICE
FOREGROUND_SERVICE_LOCATION
FOREGROUND_SERVICE_REMOTE_MESSAGING
WAKE_LOCK
POST_NOTIFICATIONS
DOWNLOAD_WITHOUT_NOTIFICATION
EXPAND_STATUS_BAR
```

网络与状态权限用于 MTOP、下载、推送和前台任务；通知权限由运行时
权限模型控制。

### 2.2 位置

```text
ACCESS_COARSE_LOCATION
ACCESS_FINE_LOCATION
ACCESS_LOCATION_EXTRA_COMMANDS
FOREGROUND_SERVICE_LOCATION
```

静态代码中多个定位调用点先检查 `Tools.hasLocationPermission()`，再调用
`getLastKnownLocation()`；这证明客户端有能力发送经纬度，但不证明
未授权时会绕过系统权限。位置头字段的组装见
[network.md](network.md) 与 [privacy.md](privacy.md)。

### 2.3 相机、麦克风、生物识别

```text
CAMERA
RECORD_AUDIO
USE_BIOMETRIC
USE_FINGERPRINT
FLASHLIGHT
```

实名/颜色动作视频流程在进入对应页面时请求 `CAMERA`；`onActivityResult`
会读取返回的媒体或文件 URI，并把图像缩放到 `1280` 或 `800x480`。
上传字段与视频元素见 [transfer.md](transfer.md)。

### 2.4 存储、日历与安装

```text
READ_EXTERNAL_STORAGE
WRITE_EXTERNAL_STORAGE
READ_MEDIA_IMAGES
READ_MEDIA_VIDEO
WRITE_CALENDAR
ACCESS_DOWNLOAD_MANAGER
REQUEST_INSTALL_PACKAGES
MOUNT_UNMOUNT_FILESYSTEMS
```

`WRITE_CALENDAR` 属于日历写入能力；安装权限用于更新包安装流程。拥有
`REQUEST_INSTALL_PACKAGES` 只表示 APK 可以请求系统安装器，**不等于**
获得系统提权或静默安装能力。Android 13+ 的媒体读取由
`READ_MEDIA_IMAGES/VIDEO` 约束，旧版本仍可能走外部存储权限。

### 2.5 蓝牙、NFC、禁用锁屏等

```text
BLUETOOTH
NFC
DISABLE_KEYGUARD
VIBRATE
MODIFY_AUDIO_SETTINGS
BROADCAST_STICKY
```

这些权限对应设备交互、语音/提示和广播；它们本身不授予跨应用数据
读取权。

### 2.6 推送、账号与第三方组件

```text
cn.cyberidentity.certification.AUTH
cn.damai.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
cn.damai.permission.C2D_MESSAGE
cn.damai.permission.MIPUSH_RECEIVE
cn.damai.permission.PROCESS_PUSH_MSG
cn.damai.permission.PUSH_PROVIDER
cn.damai.push.permission.MESSAGE
com.coloros.mcs.permission.RECIEVE_MCS_MESSAGE
com.google.android.c2dm.permission.RECEIVE
com.google.android.gms.permission.AD_ID
com.heytap.mcs.permission.RECIEVE_MCS_MESSAGE
com.hihonor.brain.permission.KIT_SERVICE_ACCESS
com.hihonor.onetouchshare.permission.BIND_ONETOUCHSHARE_SERVICE
com.hihonor.push.permission.READ_PUSH_NOTIFICATION_INFO
com.hihonor.security.permission.ACCESS_THREAT_DETECTION
com.meizu.c2dm.permission.RECEIVE
com.meizu.flyme.push.permission.RECEIVE
com.oplus.metis.factdata.permission.DATABASE
com.oplus.permission.safe.AI_APP
com.taobao.taobao.permission.C2D_MESSAGE
com.vivo.notification.permission.BADGE_ICON
com.xiaomi.security.permission.ACCESS_XSOF
```

清单还声明了 `RUN_INSTRUMENTATION`、`REQUEST_INSTALL_PACKAGES`、
`ACCESS_DOWNLOAD_MANAGER` 等能力；是否由系统授予取决于安装来源、
目标版本和运行时检查，静态清单不能推导为已经获得。

## 3. 导出 activity

按入口职责分组（完整 52 个组件的 manifest 解析结果均计入）：

- **分享/支付/登录**：`WXEntryActivity`、`WXPayEntryActivity`、
  `AlipayEntryActivity`、`DDShareActivity`、`ShareEntryActivity`、
  `AuthActivity`、`MultiAccountActivity`、`SecurityEntranceActivity`、
  `LogoutPanelActivity`、`AliUserRegisterActivity`、`QrScanActivity`、
  `PayResultActivity`、`AlipayResultActivity`。
- **票务/订单**：`ProjectDetailActivity`、`ProjectVenueMapActivity`、
  `OrderListActivity`、`CouponOrderConfirmActivity`、`OrderDetailActivity`、
  `CouponPayResultActivity`、`TickletListActivity`、`TicketDeatilActivity`、
  `TickletTransferManageActivity`、`TicketDetailAcceptTransferActivity`、
  `TicketMyCommentActivity`。
- **首页/搜索/活动**：`SplashMainActivity`、`MainActivity`、
  `DMFilmDetailActivity`、`EntertainmentRankActivity`、
  `ShowFilmListActivity`、`PushMessageActivity`、`ActionWakeUpActivity`、
  `FissionActivity`、`FissionUpgradeActivity`、`FastPreviewActivity`。
- **实名/会员/图片选择**：`RealNameAuthActivity`、
  `RealNameAuthStatusActivity`、`YYMemberTabActivity`、
  `ImageSelectFolderActivity`。
- **SDK/预览/分享接收器**：`PushNotificationDispatchClickActivity`、
  `ShareTransActivity`、`PreviewActivity`、`ResultActivity`、
  `UltronSwitchActivity`、`NotificationClickedActivity`、
  `TaobaoIntentService` 之外的两个 share receiver 等。

没有一个 exported activity 声明 `android:permission`；安全评估因此把
它们标为“入口面需要逐项核对”，而不是直接认定越权。

## 4. 导出 service、receiver、provider

### Service（15）

- 无权限保护：`DumpCoverageService`、`LocalServerService`、
  `ChannelService`、`MsgDistributeService`、`TaobaoIntentService`、
  `AgooService`、`TaobaoMessageIntentReceiverService`、
  `EvoAccsService`、`RemoteUccService`、`HmsMsgService`。
- 有自定义/厂商权限保护：`PushMessageHandler`（`MIPUSH_RECEIVE`）、
  `CompatibleDataMessageCallbackService`（`SEND_MCS_MESSAGE`）、
  `DataMessageCallbackService`（`SEND_PUSH_MESSAGE`）、
  `CommandClientService`（`UPSTAGESERVICE`）、
  `NotificationService`（Meizu `MESSAGE`）。

`RemoteUccService` 导出接口为 `com.ali.user.open.ucc.IRemoteUccService`；
它是否允许第三方跨进程调用，取决于 binder 调用方校验，静态清单本身
不能证明已经发生越权。

### Receiver（11）

- 无自定义权限：`EventReceiver`、`AgooCommondReceiver`、
  `DmPerformWidget`、`HotRecommendWidget`、`ServiceReceiver`、
  `MeizuPushMsgReceiver`、`MiPushBroadcastReceiver`、
  `MeizuPushReceiver`。
- 有权限保护：`ProfileInstallReceiver`（`DUMP`）、
  `PushMsgReceiver` 与 `PushReceiver`（`PROCESS_PUSH_MSG`）。

两个桌面 widget 允许 `APPWIDGET_UPDATE`，并额外接受 `customizedWidget` /
`refreshWidget` 自定义 action。

### Provider（1）

唯一显式导出的 provider 是
`com.huawei.hms.support.api.push.PushProvider`，authority 为
`cn.damai.huawei.push.provider`；清单没有给它声明 `android:readPermission`
或 `android:writePermission`。应把它视为需要进一步核对读写 URI 权限的
组件，不能仅凭“有一个 provider”认定可读取私有数据。

## 5. 越权、提权与提权面结论

| 问题 | 静态结论 | 证据边界 |
|---|---|---|
| 是否声明高敏权限 | 是，含位置、相机、麦克风、生物识别、读写媒体、日历、安装 | 未发送请求，未执行运行时授权 |
| 是否存在导出入口 | 是，79 个显式导出组件 | 需要逐个检查 intent/binder/provider 校验 |
| 是否可越权读取其他应用数据 | 未发现清单直接授予跨应用读取权限；`READ_MEDIA_*` 受系统选择器约束 | 没有实际 exploit 测试 |
| 是否提权到系统/其他 UID | 未发现 `android:sharedUserId`、系统 UID 或私有权限绕过证据 | `REQUEST_INSTALL_PACKAGES` 不是系统提权 |
| 是否静默安装 | 未发现自动安装流程；更新入口只能请求系统安装器 | 未测试下载/安装行为 |
| 是否未告知采集 | 启动隐私闸门和字段条件见 [privacy.md](privacy.md) | 服务端存储与用途不可见 |
| 是否超范围采集 | 客户端存在位置、设备标识、媒体、日历等能力，必须结合告知与运行时条件判断 | 未证明所有字段实际发送 |

### 最终判定

本样本存在**宽导出面和高敏权限声明**，其中导出 activity 无显式组件
权限保护、导出 provider 无 read/write 权限，值得防御性审计；但静态
分析没有发现可直接提升到系统或其他应用 UID 的机制，也没有进行实际
越权/提权测试。`REQUEST_INSTALL_PACKAGES`、`RUN_INSTRUMENTATION`
等名称不能单独作为提权或静默安装的证据。越权与超范围采集的最终判断
必须由告知、运行时授权和实际数据流共同支持。
