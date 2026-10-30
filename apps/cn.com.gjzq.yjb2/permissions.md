# 权限与导出面

## 1. 权限清单

解码后的 `AndroidManifest.xml` 有 **47** 个 `<uses-permission>` 标签，对应 **44**
个唯一名：**28 个 `android.permission.*`、2 个 `android.hardware.*`、14 个自有或
第三方别名权限**。`QUERY_ALL_PACKAGES`、`MANAGE_EXTERNAL_STORAGE` 均未出现。

### 1.1 Android 平台权限（28）

| 权限 | 静态可见用途 |
|---|---|
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | 网络状态、Wi-Fi 状态判断 |
| `INTERNET` | RPC、静态资源与 WebView 请求 |
| `CHANGE_NETWORK_STATE` / `CHANGE_WIFI_STATE` | 网络切换 |
| `BLUETOOTH` | 蓝牙设备能力 |
| `CAMERA` | 扫码、拍摄或图片输入 |
| `RECORD_AUDIO` / `RECORD_VIDEO` | 语音、视频功能 |
| `READ_PHONE_STATE` | 设备状态/标识相关能力 |
| `POST_NOTIFICATIONS` | Android 13 通知 |
| `VIBRATE` / `FLASHLIGHT` | 提示、闪光灯能力 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 前台服务、行情/媒体播放 |
| `WAKE_LOCK` | 交易/下载期间保持唤醒 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗或系统覆盖层 |
| `REQUEST_INSTALL_PACKAGES` | APK 更新安装入口 |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | 生物识别 |
| `DISABLE_KEYGUARD` | 锁屏解除相关能力 |
| `MODIFY_AUDIO_SETTINGS` | 音频设置 |
| `NFC` | NFC 能力 |
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` | 旧版存储读写 |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Android 13 媒体读取 |
| `MOUNT_UNMOUNT_FILESYSTEMS` | 系统存储挂载接口（平台限制） |

### 1.2 自有/第三方别名（14）

`cn.com.gjzq.yjb2.permission.{C2D_MESSAGE,MIPUSH_RECEIVE,PROCESS_PUSH_MSG,PUSH_PROVIDER}`、
`cn.com.gjzq.yjb2.push.permission.MESSAGE`、`com.meizu.{c2dm.permission.RECEIVE,flyme.push.permission.RECEIVE}`、
`com.coloros.mcs.permission.RECIEVE_MCS_MESSAGE`、`com.heytap.mcs.permission.RECIEVE_MCS_MESSAGE`、
`com.hihonor.push.permission.READ_PUSH_NOTIFICATION_INFO`、
`com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA`、
`com.asus.msa.SupplementaryDID.ACCESS`、`freemme.permission.msa`、
`freemme.permission.msa.SECURITY_ACCESS`。

另有 2 个 `<uses-feature>`：`android.hardware.camera`、
`android.hardware.camera.autofocus`。它们不是运行时权限，但会在安装前参与设备
兼容性筛选。

## 2. 导出组件

清单声明 381 activity、20 service、8 receiver、10 provider；显式导出组件为
**12 activity、3 service、3 receiver、1 provider**。所有导出组件均未带
`android:permission`：

| 类型 | 组件 |
|---|---|
| activity | `cn.com.gjzq.yjb2.activity.SplashActivity`、`cn.com.gjzq.yjb2.portal.HomeActivity`、`cn.com.gjzq.yjb2.push.PushActivity`、`cn.com.gjzq.yjb2.forward.ForwardActivity` |
| activity | `cn.com.gjzq.yjb2.wxapi.WXPayEntryActivity`、`cn.com.gjzq.yjb2.wxapi.WXEntryActivity` |
| activity | `com.alipay.sdk.app.PayResultActivity`、`com.alipay.sdk.app.AlipayResultActivity` |
| activity | `androidx.biometric.DeviceCredentialHandlerActivity` |
| activity | `com.xiaomi.mipush.sdk.NotificationClickedActivity`、`com.linkstec.upushsdk.hw.HWEventActivity`、`com.linkstec.upushsdk.oppo.OppoEventActivity` |
| service | `com.huawei.hms.support.api.push.service.HmsMsgService`、`com.xiaomi.mipush.sdk.PushMessageHandler`、`com.vivo.push.sdk.service.CommandClientService` |
| receiver | `com.huawei.hms.support.api.push.{PushMsgReceiver,PushReceiver}`、`com.linkstec.upushsdk.mi.MiPushReceiver` |
| provider | `com.huawei.hms.support.api.push.PushProvider` |

导出面主要服务于启动、推送、支付/生物识别回调和第三方 SDK；**未证明**存在
可直接读取敏感数据的越权组件。Activity 的 deeplink scheme 包括
`com.yjb.jy://`、`yjb://cn.com.gjzq.yjb2.dp`、`yjb://cn.com.gjzq.yjb2.push/push_detail`、
`yjbapp://`、`upush://cn.com.gjzq.yjb2/detail`。

## 3. 越权与提权判定

静态未发现 `su`、`pm grant`、`Runtime.exec` 以 root 提权、`Instrumentation`
注入或自定义 `ContentProvider` 开放敏感 URI 的闭环。`SYSTEM_ALERT_WINDOW` 与
`REQUEST_INSTALL_PACKAGES` 是需要用户设置授权的高敏能力，不能等同于越权；
本报告未测试实际授予后的攻击面。

`<application>` 还设置 `allowBackup=false`、`grantUriPermissions=true`、
`requestLegacyExternalStorage=true`、`usesCleartextTraffic=true`、
`largeHeap=true`、`extractNativeLibs=true`，详见 [report.md](report.md) §1.2。
