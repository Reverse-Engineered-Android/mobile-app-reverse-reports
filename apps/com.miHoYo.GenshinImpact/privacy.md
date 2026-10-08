# 隐私、告知与超范围判断

## 1. 结论

客户端存在三类独立采集面：

1. **Consent 门控的广告/分析数据**——由 Firebase consent 控制；
2. **不读 Firebase consent 的设备与基础数据**——`BaseDataReport`
   `ReportWorker` 路径；
3. **设备指纹**——`FingerprintService` 的 `getExtList`/`getFp`。

第 2、3 类的组装代码不检查第 1 类的 consent 状态，构成“默认拒绝路径下
仍可组装并上传设备与环境字段”的超范围风险。静态样本可以证明采集与
上传代码存在，不能证明服务端实际接收、留存或跨账号关联。

## 2. Consent 闸门

`ConsentStatusCache` 构造时读取 `skipConsent()`（`:66-87`）：

| 分支 | analytics / ad_user_data / ad_personalization / ad_storage |
| --- | --- |
| `skipConsent() == true` | 四类全部 `GRANTED`，`isGDPRUser=false`，`adGranted=true` |
| `skipConsent() == false` | 四类全部 `DENIED`，`isGDPRUser=true`，`adGranted=false` |

即默认路径是全部拒绝，`skipConsent()` 为真时一次性全开。`skipConsent()`
在 `:123` 定义，开关来源为构建/渠道配置，不是用户在 UI 上的选择。
Consent 变化由 `ConsentDataReporter` 上报。

## 3. 不受 consent 门控的采集面

### 3.1 设备与环境

`BaseDataReport.java:111-117` 的 `getBaseData` 直接返回以下字段，不读取
consent 状态：

```text
resolution_x/y, phone_chip_info, battery_status, charge_status,
total_time（开机时长）, screen_brightness, android_id,
ram_capacity/remain, volume, os_font_scale, package, project_name,
network_type, mobile_operators, android_api_level,
is_root, debug_status, proxy_status, emulator_status,
phone_brand, phone_manufacturer, device_model,
os, os_version, version_code, origin_version_name
```

`android_id` 是稳定设备标识；`is_root`/`debug_status`/`proxy_status`/
`emulator_status` 是风险状态，而非功能必需。

### 3.2 设备指纹

`FingerprintService.java:41` 与 `:125` 请求 `getExtList`/`getFp`；
`CommonRequiredParams.java:50-448` 可组装：

```text
androidId, serialNumber, board/brand/hardware/cpuType/deviceType,
display, hostname, manufacturer, productName, model, deviceInfo,
sdkVersion, osVersion, devId, buildTags/Type/User/Time,
screenSize, networkType, vendor, romCapacity/remain,
ramCapacity/remain, appMemory, accelerometer, magnetometer, gyroscope,
isRoot, debugStatus, proxyStatus, emulatorStatus, isTablet, simState,
ui_mode, sdCapacity/remain, hasKeyboard, isMockLocation, ringMode,
isAirMode, batteryStatus, chargeStatus,
appInstallTimeDiff, appUpdateTimeDiff, deviceName, packageName, packageVersion
```

`serialNumber`、`androidId`、`devId` 与传感器列表明显超出运行业务所需的
最小统计范围。

### 3.3 事件批量上报

`ReportWorker.java:269-316` 把最多 30 条 `ReportEntity` 序列化为 JSON，
带 `CONTENT-MD5`、`DATE`、`cms-signature` 和 HMAC-SHA1 `Authorization`
发往 `/loginsdk/dataUpload`。`:173-215` 写入 deviceId、plat、deviceName、
deviceModel、bundleId、OS、RAM、deviceFp、屏幕、CPU、OAID/IDFA 与
channel/subchannel。

## 4. 采集条件与用途

| 采集面 | 触发条件 | 用途 | 是否检查 consent |
| --- | --- | --- | --- |
| Firebase analytics/ad | 事件产生 | 统计与广告 | 是 |
| `BaseDataReport` | 上报调用 | 诊断与设备画像 | 否 |
| `CommonRequiredParams` | `getFp` 调用 | 设备指纹 | 否 |
| 归因（AppsFlyer） | 安装/打开 | 渠道归因 | 部分 |
| H5 log / Kibana / telemetry | 配置开关 | 运行日志 | 否 |

“部分”指归因 SDK 自带开关与 consent 桥接，第一方代码未强制校验。

## 5. 设备端验证边界

对一台自有 Android 设备做只读检查，**没有**目标包
`com.miHoYo.GenshinImpact` 的私有数据目录或 SQLite 文件。已安装的
同公司包 `com.miHoYo.Yuanshen` 为 versionCode `1241`、同 versionName，
其 DDL 见 `transfer.md` §6 与 `evidence.md` §8，仅作为国服旁证，不能
证明目标包会在运行时生成相同结构。公开报告不含行值、账号、设备 ID 或
带哈希的数据库文件名。检查过程只读、未写入远端、未发起任何网络请求。

## 6. 最终隐私判断

**可以确认（客户端路径）**：

- 存在明确的字段清单、上传代码与设备指纹接口；
- consent 默认拒绝，但存在一次全开的 `skipConsent()` 分支；
- 设备/环境/指纹组装路径不读取 consent 状态。

**不能从静态分析确认**：

- 服务端是否接收每次上报、保留多久、是否跨账号关联；
- 用户在 consent UI 中实际看到过哪些字段、是否单独同意指纹采集；
- 实时游戏会话是否上传位置、聊天或交易内容。

按“具备该采集能力 = 存在超范围风险，但不等于已实施超范围采集”表述，
不把字段清单夸大成服务端已收集。
