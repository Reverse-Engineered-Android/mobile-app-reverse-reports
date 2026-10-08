# 权限与导出面

## 1. Manifest 统计

| 项目 | 值 |
| --- | --- |
| 包名 | `com.miHoYo.GenshinImpact` |
| versionCode / versionName | `1242` / `7.1.0_48052158_48145775` |
| minSdk / targetSdk | `23` / `36` |
| `uses-permission` | 27 |
| `exported="true"` 组件 | 13 |
| launcher | `com.miHoYo.GetMobileInfo.MainActivity` |

## 2. 权限清单

自有权限：

```text
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.ACCESS_WIFI_STATE
android.permission.CHANGE_NETWORK_STATE
android.permission.ACCESS_ADSERVICES_AD_ID
android.permission.ACCESS_ADSERVICES_ATTRIBUTION
android.permission.READ_EXTERNAL_STORAGE
android.permission.WRITE_EXTERNAL_STORAGE
android.permission.READ_MEDIA_AUDIO
android.permission.CAMERA
android.permission.FLASHLIGHT
android.permission.RECORD_AUDIO
android.permission.MODIFY_AUDIO_SETTINGS
android.permission.BLUETOOTH
android.permission.VIBRATE
android.permission.WAKE_LOCK
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_DATA_SYNC
android.permission.POST_NOTIFICATIONS
android.permission.SCHEDULE_EXACT_ALARM
```

第三方/商店权限：

```text
com.android.vending.BILLING
com.google.android.c2dm.permission.RECEIVE
com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE
com.google.android.gms.permission.AD_ID
com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA
com.samsung.android.mapsagent.permission.READ_APP_INFO
com.miHoYo.GenshinImpact.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

没有 `READ_PHONE_STATE`、`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、
`READ_CONTACTS`、`READ_SMS`、`QUERY_ALL_PACKAGES`、`MANAGE_EXTERNAL_STORAGE`
或 `REQUEST_INSTALL_PACKAGES`。摄像与录音权限用于游戏内拍摄/语音场景的
运行时申请，静态样本不证明必然使用。

## 3. 导出的 13 个组件

| 类型 | 组件 | permission |
| --- | --- | --- |
| activity | `com.miHoYo.GetMobileInfo.MainActivity` | - |
| **service** | **`com.miHoYo.GameStateService.GameStateService`** | **无** |
| activity | `com.mihoyo.sdk.hoyolink.protocol.HoYoLinkProtocolActivity` | - |
| activity | `com.facebook.CustomTabActivity` | - |
| activity | `com.mihoyoos.sdk.platform.SchemeActivity` | - |
| activity | `com.mihoyo.hoyolab.share.core.callback.HoYoLabShareCallbackActivity` | - |
| activity | `com.google.android.gms.games.internal.v2.appshortcuts.PlayGamesAppShortcutsActivity` | - |
| provider | `com.facebook.FacebookContentProvider` | - |
| receiver | `com.combosdk.module.push.impl.localpush.internal.NotificationReceiver` | - |
| service | `com.combosdk.module.push.impl.localpush.internal.ReceiverAlarmService` | - |
| receiver | `com.mihoyo.hoyolab.share.core.callback.HoYoLabShareCallbackReceiver` | - |
| receiver | `com.google.firebase.iid.FirebaseInstanceIdReceiver` | `com.google.android.c2dm.permission.SEND` |
| service | `com.google.android.gms.auth.api.signin.RevocationBoundService` | `com.google.android.gms.auth.api.signin.permission.REVOCATION_NOTIFICATION` |

`GameStateService` 带 `intent-filter` action `gamestateservice`，并且
**没有声明任何 `android:permission`**。

## 4. `GameStateService` 越权面

`IGameStateService` 的 AIDL 描述符为
`com.miHoYo.GameStateService.IGameStateService`，transaction 为
`RegisterReceiver=1`、`UnRegisterReceiver=2`、`GetApiVersion=3`、
`SendRequestToGame=4`。

`GameStateServiceBinder.SendRequestToGame(int, String)` 解析 JSON 并
按 key 分派 10 个命令：

| JSON key | 行号 | 作用 |
| --- | ---: | --- |
| `SetRttNotify` | `:73` | 开关 RTT 通知 |
| `SetBigMemoryMode` | `:81` | 大内存模式 |
| `SetTooBigRttThreshold` | `:89` | RTT 阈值 |
| `SetCurrentDeviceFoldScreen` | `:97` | 折叠屏状态 |
| `RemoveResolutionLimit` | `:101` | 解除分辨率限制 |
| `UseNewResolutionCalculation` | `:105` | 分辨率计算方式 |
| `SetAvgRenderTimeEnable` | `:109` | 平均渲染时间 |
| `SetNotifyPlayerDetailEnable` | `:117` | 播报玩家详情（**有校验**） |
| `SetPlayerDetailUpdateInterval` | `:151` | 详情更新间隔，<90 走 `BadInputRequest` |
| `SetThreadTimeLineEnable` | `:164` | 创建/销毁共享内存并回传 IPC fd |

唯一带调用方校验的是 `SetNotifyPlayerDetailEnable`：开关为 1 时取
`deviceModel` 与 `deviceMac`，调用
`GameInterface.IsOemInWhiteList(model, mac)`，未命中返回
`auth_failed`。`IsOemInWhiteList`（`GameInterface.java:102-114`）把两者
拼接后取 MD5，与 `OnOemWhiteList`（`:74-82`）下发的集合比较；另有
`IsPkgNameInWhiteList`（`:88-100`）按调用方包名 MD5 比对，但
`SendRequestToGame` 路径并未对它做强制校验。

**越权结论**：本机任意应用可绑定该 exported 无权限 Service 并调用
`SendRequestToGame`，从而在不具备同等授权的条件下改变游戏的 RTT 通知、
分辨率限制、内存模式，并触发共享内存创建与 IPC fd 回传。这是客户端
自证的本地授权缺口。`SetNotifyPlayerDetailEnable` 是唯一例外。

## 5. 提权判断

**未发现 Android UID 或系统权限提升链**：

- 没有 `System.loadLibrary` 之外的可写系统目录、`su` 调用、`Runtime.exec`
  提权或跨 UID Binder 注入路径；
- `isRooted` 只做检测上报，不改变进程权限；
- 共享内存通过 Binder fd 回传，接收方可写自己的映射，但不获得游戏进程
  地址空间控制权；
- `allowBackup="true"`、`requestLegacyExternalStorage="true"` 与
  `usesCleartextTraffic="true"` 是攻击面相关配置，不构成提权。

静态只能说明“未发现闭环”；不排除运行时或服务端协同的利用方式。
