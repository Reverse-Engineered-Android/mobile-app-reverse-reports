# 权限与导出面

## 1. 权限清单

manifest 共 `49` 条 `uses-permission`，去重后 `48` 个；其中 `13` 个不是
`android.permission.*`（厂商推送/广告/签名权限）。

### 1.1 能力型权限（与数据采集直接相关）

| 权限 | 声明 | 采集/使用证据 |
|---|---|---|
| `READ_CONTACTS` | 有 | 票夹持票人 `frequentContactsIds`（PP `0x26d80`）、`frequentContactsId`（`0x1ea60`）；仅本地选择，未见上传到业务端点 |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | 有 | `pgc/advert/app/LocAndType/list`（PP `0x28220`）需城市/位置上下文 |
| `READ_PHONE_STATE` | 有 | `deviceType`（`0x1f8b8`）、设备标识族 |
| `READ_CALENDAR` / `WRITE_CALENDAR` | 有 | 演出日历 `tearCalendar.html` 链接族；未见日历读取后上传调用链 |
| `CAMERA` | 有 | 人脸活体 `thirdParty/faceid/app/verify`、二维码 `libbarhopper_v3.so` |
| `RECORD_AUDIO` / `MODIFY_AUDIO_SETTINGS` | 有 | 未见与业务端点相连的录音上传链 |
| `READ_EXTERNAL_STORAGE`（maxSdk 32）/ `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_VISUAL_USER_SELECTED` | 有 | 头像/图片选择后 `thirdParty/qiniu/app/upload` |
| `WRITE_EXTERNAL_STORAGE`（重复声明 2 次） | 有 | 下载落盘 |
| `GET_ACCOUNTS` / `USE_CREDENTIALS` | 有 | 第三方登录 `auth/app/v3/login/wx`、`login/hw` |
| `CALL_PHONE` | 有 | 客服拨打；未见批量拨号代码 |
| `GET_TASKS` | 有 | 未见与业务端点相连的读取链 |

### 1.2 系统/安装型

| 权限 | 声明 | 判断 |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | 有 | 未见悬浮窗调用闭环 |
| `REQUEST_INSTALL_PACKAGES` | 有 | 未见 `Intent.ACTION_VIEW` + APK 安装闭环 |
| `BROADCAST_PACKAGE_*`（4 个） | 有 | 推送/更新组件监听 |
| `RUN_INSTRUMENTATION` | **未声明** | 不具备 Instrumentation 注入声明 |

### 1.3 网络与推送

`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_NETWORK_STATE`、
`CHANGE_WIFI_STATE`、`WAKE_LOCK`、`POST_NOTIFICATIONS`、`VIBRATE`、
`RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE`、`DOWNLOAD_WITHOUT_NOTIFICATION`、
`com.google.android.gms.permission.AD_ID`、华为/vivo/魅族/OPPO 推送相关 12 个。

## 2. 导出组件

组件总数 `137`，显式 `android:exported="true"` 的 `29` 个，其中 `22` 个**没有**
`android:permission` 约束：

| 类型 | 导出组件（无 permission） |
|---|---|
| Activity | `cn.com.livelab.MainActivity`、`com.um.push.flutter_s_umeng_push.MfrMessageActivity`、`com.arno.umshare.apshare.ShareEntryActivity`、`com.arno.umshare.ddshare.DDShareActivity`、`com.tencent.tauth.AuthActivity`、`com.tencent.connect.common.AssistActivity`、`com.xiaomi.mipush.sdk.NotificationClickedActivity`、`com.alipay.sdk.app.PayResultActivity`、`com.alipay.sdk.app.AlipayResultActivity`、`com.sina.weibo.sdk.share.ShareTransActivity`、`cn.com.livelab.ddshare.DDShareActivity`、`cn.com.livelab.apshare.ShareEntryActivity` |
| Activity-alias | `cn.com.livelab.wxapi.WXEntryActivity`、`cn.com.livelab.wxapi.WXPayEntryActivity`、`cn.com.livelab.douyinapi.DouYinEntryActivity`、`com.umeng.message.UMessageNotifyActivity` |
| Service | `com.google.android.play.core.assetpacks.AssetPackExtractionService`、`com.vivo.push.sdk.service.CommandClientService`、`com.huawei.hms.support.api.push.service.HmsMsgService` |
| Receiver | `org.android.agoo.mezu.MeizuPushReceiver`、`org.android.agoo.xiaomi.MiPushBroadcastReceiver` |
| Provider | `com.huawei.hms.support.api.push.PushProvider` |

有 permission 约束的导出组件：`com.heytap.msp.push.service.*`、
`com.xiaomi.mipush.sdk.PushMessageHandler`、`com.meizu.cloud.pushsdk.NotificationService`、
`com.huawei.hms.support.api.push.PushMsgReceiver` / `PushReceiver`、
`androidx.profileinstaller.ProfileInstallReceiver`（`android.permission.DUMP`）。

## 3. 权限 → 数据 → 端点的对应

| 数据 | 权限 | 是否观察到上传端点 |
|---|---|---|
| 位置 | `ACCESS_FINE/COARSE_LOCATION` | `pgc/advert/app/LocAndType/list`（上下文，未见原始经纬度字段） |
| 联系人 | `READ_CONTACTS` | **未观察到**联系人原始数据汇入业务端点 |
| 电话状态 | `READ_PHONE_STATE` | `deviceType` / 设备信息族（聚合字段，未见 IMEI/MEID 明文） |
| 日历 | `READ/WRITE_CALENDAR` | **未观察到**日历条目读取后上传调用链 |
| 录音 | `RECORD_AUDIO` | **未观察到**音频上传调用链 |
| 相机 | `CAMERA` | `thirdParty/faceid/app/verify` |
| 图片/视频 | `READ_MEDIA_*` | `thirdParty/qiniu/app/upload` |
| 安装包 | `REQUEST_INSTALL_PACKAGES` | **未观察到**安装动作闭环 |
| 悬浮窗 | `SYSTEM_ALERT_WINDOW` | **未观察到**调用闭环 |
| 广告 ID | `com.google.android.gms.permission.AD_ID` | 未见汇入业务端点的调用链 |

## 4. 越权判断

**未发现越权调用。** 依据：

- 未出现 `pm grant`、`su`、`Runtime.exec`/`ProcessBuilder` 提权命令族；
- 未出现 `setUid`/`seteuid`/`capset` 等提权原语；
- 所有受保护 API 都通过 `checkSelfPermission`/运行时申请路径进入；
- `RUN_INSTRUMENTATION` 未声明，不具备注入式提权入口。

越权的定义是“以他人身份访问他人数据”；客户端静态代码中未出现跨 UID 访问、
伪造包名或 Provider 越权查询的实现。

## 5. 提权判断

**未发现提权。** `SYSTEM_ALERT_WINDOW` 与 `REQUEST_INSTALL_PACKAGES` 属于
“高危能力声明”，但缺少对应的调用闭环，属于**过度声明**而非提权实现。
29 个导出组件中有 22 个无 permission 保护，构成**攻击面**（外部组件可被
显式 Intent 调起），但导出本身不等于提权。

## 6. 未经告知 / 超范围判断

| 判断 | 结论 | 理由 |
|---|---|---|
| 未经告知 | **无法静态证明已逐项告知，判为告知粒度不足风险** | 隐私政策为服务端页面，APK 内无逐项权限说明文本；13 个非系统权限（厂商推送/广告）需要单独告知 |
| 超范围获取 | **未发现“采集即上传”的超范围闭环** | 联系人、日历、录音、安装包、悬浮窗均有声明，但缺少到业务端点的调用链 |
| 过度声明 | **成立** | 48 个去重权限中至少 6 个（日历读写、录音、安装包、悬浮窗、`GET_TASKS`）缺少可验证的业务用途 |

## 7. 静态化说明

未安装应用、未授予任何权限、未实际采集数据。权限结论仅基于 manifest 与调用链
静态证据，不构成对服务端行为的判断。
