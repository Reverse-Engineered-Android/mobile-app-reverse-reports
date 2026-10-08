# 权限与导出面

## 1. 基本声明

- 包名 `com.taobao.idlefish`，versionName `7.28.40`，versionCode `521`。
- `minSdk 21`，`targetSdk 33`，`compileSdk 33`，`installLocation=auto`，
  `allowBackup=false`。
- `uses-permission` 声明共 **116** 条。

## 2. 权限清单（按风险归类）

### 2.1 位置与传感器
`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`ACCESS_LOCATION_EXTRA_COMMANDS`、
`ACTIVITY_RECOGNITION`、`HIGH_SAMPLING_RATE_SENSORS`。
→ 用途：LBS 同城、附近 WiFi 上报（[risk.md](risk.md) §5.2）、计步（§5.3）。

### 2.2 存储与媒体
`READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、`READ_MEDIA_IMAGES`、
`READ_MEDIA_VIDEO`、`READ_MEDIA_AUDIO`、`MOUNT_UNMOUNT_FILESYSTEMS`、
`DOWNLOAD_WITHOUT_NOTIFICATION`。
→ 用途：发布图片/视频、聊天附件、缓存下载。

### 2.3 相机、麦克风、蓝牙、NFC
`CAMERA`、`CAPTURE_VIDEO_OUTPUT`、`RECORD_AUDIO`、`MODIFY_AUDIO_SETTINGS`、
`BLUETOOTH`、`BLUETOOTH_ADMIN`、`BLUETOOTH_CONNECT`、`BLUETOOTH_SCAN`、
`NFC`、`FLASHLIGHT`、`VIBRATE`。
→ 用途：扫码/拍照发布、语音消息、附近设备、NFC 标签。

### 2.4 账号与生物识别
`AUTHENTICATE_ACCOUNTS`、`MANAGE_ACCOUNTS`、`USE_CREDENTIALS`、
`USE_BIOMETRIC`、`USE_FINGERPRINT`（含厂商别名 `USE_FINGERPRIN`、
`MANAGE_FINGERPRINT`、`USE_FACERECOGNITION`）。
→ 用途：淘宝账号同步、支付/实人核验的生物识别。

### 2.5 系统与网络
`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_STATE`、
`CHANGE_NETWORK_STATE`、`CHANGE_WIFI_MULTICAST_STATE`、`RECEIVE_BOOT_COMPLETED`、
`FOREGROUND_SERVICE`、`WAKE_LOCK`、`SCHEDULE_EXACT_ALARM`、`POST_NOTIFICATIONS`、
`REQUEST_INSTALL_PACKAGES`、`REQUEST_DELETE_PACKAGES`、`GET_TASKS`、`REORDER_TASKS`、
`REORDER_TASKS`。
→ 含 `REQUEST_INSTALL_PACKAGES`（应用内升级/装插件）。

### 2.6 需关注的高敏权限
| 权限 | 说明 | 代码依据 |
| --- | --- | --- |
| `QUERY_ALL_PACKAGES` | 读取全部已安装应用 | `ClientHeaderInterceptor`/应用列表统计 |
| `GET_PACKAGE_INFO` | 单个包信息 | 同上 |
| `READ_LOGS` | 读取系统日志 | ANR/崩溃采集 |
| `BROADCAST_PACKAGE_ADDED/CHANGED/INSTALL/REPLACED` | 监控应用安装 | 应用生命周期统计 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗 | 悬浮球/广告 |
| `UPDATE_APP_OPS_STATS` | 修改应用操作统计 | 保活/权限绕过倾向 |
| `WRITE_SETTINGS` | 改系统设置 | 亮度/设置类功能 |
| `WRITE_CALENDAR` | 写日历 | 到货提醒 |
| `DETECT_SCREEN_CAPTURE` / `DETECT_SCREEN_RECORDING` | 侦测截屏录屏 | 隐私/风控 |
| `BATTERY_STATS` | 电池统计 | 保活策略 |
| `READ_SETTINGS` | 读系统设置 | 设备画像 |

## 3. 导出组件

| 类型 | 总数 | 导出 |
| --- | ---: | ---: |
| Activity | 368 | **50** |
| Service | 103 | **30** |
| Receiver | 41 | **24** |
| Provider | 27 | **2** |

### 3.1 导出 Activity（节选，共 50）

- 深链入口：`com.taobao.idlefish.deeplink.h5inst.DeeplinkH5InstActivity`、
  `com.taobao.fleamarket.home.activity.InitActivity`、
  `com.taobao.fleamarket.home.activity.TransparentInitActivity`。
- 主容器：`com.taobao.idlefish.maincontainer.activity.MainActivity`、
  `com.taobao.fleamarket.detail.activity.ItemDetailActivity`、
  `com.taobao.idlefish.city.CityActivity`。
- Hybrid/Weex：`WebHybridActivity`、`WebHybridPopActivity`、
  `WebHybridTransparentActivity`、`WeexWebViewActivity(O)`、
  `WeexWebViewTransparentActivity(O)`。
- 分享回跳：`WXEntryActivity`、`WXPayEntryActivity`、`DDShareActivity`、
  `DouYinEntryActivity`、`XhsShareActivity`、`WbShareResultActivity`、
  `com.tencent.tauth.AuthActivity`、`com.taobao.idlefish.apshare.ShareEntryActivity`。
- 支付/核验：`AlipayResultActivity`、`com.alipay.android.msp.ui.views.MspContainerActivity`、
  `MspUniRenderActivity`、`MspSchemeActivity`、
  `com.alipay.mobile.verifyidentity.prodmanger.biopen.ui.FromTaoActivity`、
  `com.taobao.login4android.activity.AlipaySSOResultActivity`。
- 登录/注册：`LoginActivity`、`AliUserRegisterActivity`、
  `AliUserRegisterChoiceRegionActivity`、`QrScanActivity`、`FakeLoginActivity`。
- 编辑：`ImageEditActivity4Community/4Message/4Windvane`、`LCCropActivity`。
- 直播/通话：`FishRtcResponseActivity`、`FishRoomActivity`。
- 广告：`SplashAdActivity`、`RewardAdActivity`、`FloatPushActivity`。
- 推送厂商入口：`com.xiaomi.mipush.sdk.NotificationClickedActivity`、
  `com.taobao.fleamarket.XiaoMiSystemMessageActivity`。
- 媒体调试：`com.taobao.idlefish.mediadebug.reporter.DebugReporteMediaActivity`。
- Flutter：`com.taobao.flutterchannplugin.FlutterWrapperActivity`。
- 组件皮肤：`com.taobao.fleamarket.home.activity.AppIcon_Skin_Default_Badge`。

### 3.2 导出 Service（节选，共 30）

- 消息/推送：`com.taobao.accs.ChannelService`、`org.android.agoo.accs.AgooService`、
  `TaobaoMessageIntentReceiverService`、`XPushService`、`XAckService`、`XP2PService`、
  `OmegaPushService`、`com.taobao.fleamarket.message.service.ActionService`。
- 保活：`com.taobao.idlefish.alive.PushAliveAccountService`。
- 地理围栏：`com.taobao.idlefish.alive.geofence.SmartFenceService`（监听
  `com.huawei.hms.location.action.common.geofence`）。
- 支付：`com.alipay.android.app.MspService`、`RemoteUccService`。
- 厂商推送：小米/OPPO/vivo/华为/魅族/Firebase 各自的 Service。

### 3.3 导出 Receiver（节选，共 24）

- ACCS：`com.taobao.accs.EventReceiver`、`ServiceReceiver`。
- 桌面小组件：`FishCoinWidgetProvider`、`RedPocketWidgetProvider`、
  `SellerWorkbenchWidgetProvider`、`AttentionWidgetProvider`、
  `YULIDJWidgetProvider`、`AcgnCoinWidgetProvider`、`RemoteWidgetProvider`、
  `FisherWidgetProvider`、`FishPondWidgetProvider`。
- 网络：`com.taobao.fleamarket.function.network.NetworkReceiver`。
- 推送：Agoo/小米/vivo/华为/魅族/Firebase 各自 Receiver。
- 工具：`com.taobao.weex.analyzer.core.LaunchAnalyzerReceiver`（Weex 分析）。
- 支付：`com.alipay.android.msp.core.component.CertPayReceiver`。

### 3.4 导出 Provider（共 2）

- `com.taobao.fleamarket.business.IPCDataProvider`
- `com.huawei.hms.support.api.push.PushProvider`

## 4. 越权/提权/超范围判断

| 项 | 结论 | 依据 |
| --- | --- | --- |
| 是否存在权限提升到系统权限 | **否** | 无 `signature`/`privileged` 级权限；`WRITE_SETTINGS`/`UPDATE_APP_OPS_STATS` 为普通危险权限，需用户授予 |
| 是否利用已 root 环境 | **无证据** | `ReflectHelper.unseal` 只是调用 `VMRuntime.setHiddenApiExemptions`（解除隐藏 API 限制），非提权 |
| 是否动态安装代码 | **是，受限** | `REQUEST_INSTALL_PACKAGES` + Atlas/EMAS 资源更新 + `libdexloaderuc.so`/`libfakedexuc.so`/`libdexvmp.so` 动态加载；范围限于本应用签名资源 |
| 导出组件是否缺少保护 | **部分** | 50 个导出 Activity 中含深链与 Hybrid 入口，属功能必需；`IPCDataProvider` 为跨进程内部数据通道，未声明自定义权限 |
| 是否超范围获取数据 | **部分** | 见 [privacy.md](privacy.md)：附近 WiFi BSSID、应用列表、行为/内容样本均超出“完成当前操作所必需”的直觉范围，但均有对应功能或风控场景 |

**结论**：不存在系统级提权；存在**功能驱动的超范围采集**（应用列表、附近 WiFi、
传感器、内容/行为样本、粘贴/剪贴类未见）。所有采集都有对应的接口或本地写入点，
不属于隐蔽通道（见 [privacy.md](privacy.md) 的告知链）。
