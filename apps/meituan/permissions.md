# 权限与导出面

## 1. Manifest 统计

| 项目 | 数量/值 |
|---|---:|
| uses-permission | 147（唯一 147） |
| minSdk / targetSdk | 21 / 30 |
| activity | 426 |
| activity-alias | 14 |
| service | 118 |
| receiver | 104 |
| provider | 35 |
| 组件总数 | 697 |
| 显式 exported=true | 265 |
| 显式 exported=false | 247 |
| unset 且含 intent-filter | 68 |
| provider 显式 exported=true | 5 |

统计方法是从 APK 解码后的 XML 逐节点解析；不把第三方 SDK 的声明与美团自有
组件混为一谈。Android 12 之后要求显式 exported，但本 APK targetSdk 30，
存在未声明 exported 的 intent-filter 组件。

## 2. 危险权限

### 2.1 定位

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `ACCESS_LOCATION_EXTRA_COMMANDS`

静态调用面包含 `LocationManager`、Fused Location、地图/POI 附近查询、
`locationOpenFlag`、`userLocation`。后台定位是否在运行时授予取决于 Android
版本和用户选择；manifest 声明本身不是已读取轨迹的证据。

### 2.2 设备与个人资料

- `READ_CONTACTS`
- `WRITE_CALENDAR`
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`
- `READ_MEDIA_IMAGES`

代码包含 `ContactsContract` 与 `CalendarContract.Events` 写入接口，能够读取
联系人选择结果或创建日历事件；外置媒体由图片、文件、评论和升级模块访问。

### 2.3 传感与相机

- `CAMERA`
- `RECORD_AUDIO`
- `ACTIVITY_RECOGNITION`

相机权限对应扫码/拍摄/门脸图片，录音对应语音输入/客服，活动识别对应运动
场景；这些权限的运行时请求由 Privacy permission guard 检查。

### 2.4 电话与通知

- `CALL_PHONE`
- `POST_NOTIFICATIONS`

电话权限可能用于拨打商家/客服，通知权限用于订单/活动提醒；manifest 没有
`READ_PHONE_STATE`，因此不应从 147 项直接推断可读取 IMEI。

### 2.5 安装能力

`REQUEST_INSTALL_PACKAGES` 与 `UpgradeManager` 表明应用可请求安装 APK。
它不是 UID 提权，但会扩大供应链/恶意更新风险，需要校验下载 hash、签名和
安装来源。

## 3. 导出组件

### 3.1 provider

显式导出的 5 个 provider：

```text
com.meituan.android.hades.HadesContentProvider
com.meituan.android.walmai.OrderNextProvider
com.meituan.android.pt.homepage.order.aod.fanzai.OppoFanZaiProvider
com.meituan.android.pt.homepage.order.honorhap.HonorHapContentProvider
com.huawei.hms.support.api.push.PushProvider
```

它们没有普通 signature permission 属性。是否越权取决于：

1. `query/call/openFile` 的路径和调用方校验；
2. provider 是否校验 calling package；
3. 返回列是否包含订单、设备或 token；
4. 是否有 URI permission grant。

因此静态结论是“存在跨应用 IPC 入口”，不是“已证明泄露订单”。Hades、
OrderNext、Oppo/Honor 推广 provider 是优先审计对象。

### 3.2 activity/service/receiver

- 显式导出 activity 148 个、activity-alias 13 个；
- 显式导出 service 31 个、receiver 68 个；
- intent-filter 未显式声明的组件还有 68 个。

第三方 SDK、推送、支付、WebView、深链和设备厂商业务都可能在其中。恶意应用
可发送 intent，若组件信任 `intent.getStringExtra("token")` 或直接启动支付
流程，就会形成越权。需要逐组件检查 `getCallingPackage`、权限、URI 校验和
敏感 extras；报告不把所有导出组件一概判为漏洞。

## 4. 运行时授权链

`IPermissionGuard` 提供：

```java
checkPermission(context, permission, scene)
requestPermission(activity, permission, scene, callback)
isPrivacyMode(context)
registerPrivacyModeListener(...)
setPrivacyMode(context, boolean)
```

`permission/i` 的返回码包括：

- `-11` storage-null / SDK-not-initialized；
- `-19` privacy-mode；
- `-8` permission-null；
- `-1` policy-disabled；
- `-16` version-unsupported；
- `-6` app-authorization-required；
- `-3/-4` permission not granted / prompt limited；
- `-7` rationale；
- `1` memory-granted。

所以运行时权限不是一次性 boolean，而是“隐私态、策略、版本、是否已请求、
rationale、Android 返回码”的组合状态。

## 5. 越权判断

### 已证明的攻击面

- 大量导出组件；
- 5 个导出 provider 没有普通 signature permission；
- intent extras 可携带 token、订单、URL 或回调；
- WebView bridge 可通过页面/JS 触发文件上传、位置和图片能力；
- targetSdk 30 下旧组件导出语义更容易被误配。

### 未证明的说法

- 未构造恶意 app 验证 provider 可读；
- 未证明某个 exported activity 可以操作他人订单；
- 未发现客户端代码能直接修改系统签名、获取 root 或切换 UID；
- 未把第三方 SDK 的权限等同于美团业务实际调用。

## 6. 提权判断

静态未发现提权到系统/root UID 的闭环。主要“提权样风险”是：

1. `REQUEST_INSTALL_PACKAGES` 允许用户确认安装更新；
2. 导出组件可能把本地权限能力间接暴露给其他应用；
3. native 文件/进程检测可以读取进程和路径，但仍在应用 UID 权限内；
4. 支付/相机/位置能力必须经过用户授予或隐私策略。

结论：**存在跨应用越权攻击面，不构成已验证的系统提权漏洞。**
