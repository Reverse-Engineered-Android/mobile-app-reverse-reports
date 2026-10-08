# 上传与下载的数据范围

## 1. 结论

静态客户端有多个独立数据面：登录/SDK 诊断上报、设备指纹、Firebase/
归因、Kibana/遥测、H5 log、字体/语言/配置/热修下载和资源 CDN。没有
把游戏存档、聊天、好友、支付或截图主动上传到第三方通用云盘的静态
调用链；实时游戏数据是否上传由服务端会话决定，静态样本不能证明。

## 2. 诊断与事件上报

### 2.1 `ReportWorker`

`ReportWorker.java:112-147` 从内存缓存或 Room/SQLite 读取最多 30 条
事件；`reportInternal` 在 `:269-316` 将 `List<ReportEntity>` 转为 JSON，
发送到 `ComboURL.trackingUrl`（配置值包含
`/loginsdk/dataUpload`）。每批请求带：

```text
cms-signature: hmac-sha1
CONTENT-TYPE: application/json; charset=utf-8
CONTENT-MD5: MD5(json)
DATE: unix_seconds
Authorization: HMAC-SHA1(
  POST\n<md5>\n<content-type>\n<date>\n<hmac-sha1>,
  <static client HMAC key>
)
```

HMAC 字面 key 不在报告中出现；`HmacSHA1Signature.java:10-54` 说明使用
UTF-8、HmacSHA1、Base64 输出。`ReportWorker` 在响应 `code==0` 后增加
成功计数，失败按指数间隔重试并最多 4 次。

### 2.2 上报字段

`BaseDataReport.java:117` 将以下字段放入基础 map：

```text
resolution_x/y, phone_chip_info, battery_status, charge_status,
total_time, screen_brightness, android_id, ram_capacity/remain,
volume, os_font_scale, package, project_name, network_type,
mobile_operators, android_api_level, is_root, debug_status,
proxy_status, emulator_status, phone_brand, phone_manufacturer,
device_model, os, os_version, version_code, origin_version_name
```

`ReportWorker.java:173-215` 再写入 deviceId、plat、deviceName、
deviceModel、bundleId、OS、RAM、deviceFp、屏幕、CPU、OAID/IDFA、
channel/subchannel。`DeviceInfo` 是事件实体的嵌套对象，不等于所有
事件必然包含全部字段。

## 3. 设备指纹

`FingerprintService.java:41` 请求
`GET /device-fp/api/getExtList?platform=...&app_name=...`；
`:125` 请求 `POST /device-fp/api/getFp`。`CommonRequiredParams.java:50-448`
可组装：

```text
androidId, serialNumber, board, brand, hardware, cpuType, deviceType,
display, hostname, manufacturer, productName, model, deviceInfo,
sdkVersion, osVersion, devId, buildTags, buildType, buildUser,
buildTime, screenSize, networkType, vendor, romCapacity/remain,
ramCapacity/remain, appMemory, accelerometer, magnetometer, gyroscope,
isRoot, debugStatus, proxyStatus, emulatorStatus, isTablet, simState,
ui_mode, sdCapacity/remain, hasKeyboard, isMockLocation, ringMode,
isAirMode, batteryStatus, chargeStatus, appInstallTimeDiff,
appUpdateTimeDiff, deviceName, packageName, packageVersion
```

这些是“可构造字段”，不是每次请求的保证字段。设备指纹结果通过
`DeviceFPProxy` 区分 CN/OS 实现，OS 发布 host 为
`https://sg-public-data-api.hoyoverse.com`。

## 4. 其他上报

- `LoadConfigService.java:16` 获取归因报告配置；
- `ABTestApi.java:22` 获取 AB 实验列表；
- `LogModule` 和 `Telemetry` 把 H5 log、Kibana、telemetry JSON 发往
  配置的 log/report host；
- `ConsentDataReporter` 负责 consent 变化上报；
- `BaseDataReport` 和 `ReportWorker` 负责设备/事件批量上传。

## 5. 下载范围

静态配置包含以下下载类别：

1. 账号/登录 Web 页面、隐私/协议页；
2. `getFont` 字体和语言 JSON；
3. `getExtList` 设备指纹配置；
4. ABTest、Box/Kibana、SDK 配置和活动配置；
5. 热修、补丁、静态资源、AssetBundle CDN 和 mihoyo/game CDN；
6. 支付、活动、widget、好友关系等业务配置 URL。

下载只证明客户端具备请求代码，不证明用户会下载全部类别。

## 6. 本地缓存与设备端数据库

Java 侧存在 `ReportRecordDbHelper`、Room DAO、`DeviceFingerprintSharedPreferences`
和加密 `OSParamsEncryptedStore`；这些是缓存/队列，不等同于公开数据库。
只读检查发现目标包 `com.miHoYo.GenshinImpact` 没有私有数据目录或
SQLite 文件。同公司国服包 `com.miHoYo.Yuanshen` 已安装，versionCode
`1241`、versionName 与目标样本同为 `7.1.0_48052158_48145775`；其 DDL
仅作为国服旁证，不冒充目标包的运行时 schema，也不读取行值。

```text
report_module_record(_id, event)
porte_account_table(mid, aid, type, timestamp, data)
porte_event_report(_id, event)
cl_jm_device(i4, i8, i1, i7, i9)
cl_jm_behavior(id, i4, bk, bp, bm, b2, bc, bh, ba, b7, bi, b8, bg, bj, bb, bl, b5, b1, b4, be, b3, b6, bd, b9, bf)
plat_h5log_table(id, data, is_aes)
t_localnotification(_id, ln_id, ln_count, ln_remove, ln_type, ln_extra, ln_trigger_time, ln_add_time)
shenhe_local_monitor(id, content, createTime, priority, isSensitive)
shenhe_common(id, content, createTime, priority, isSensitive)
report_data(id, date_created, content, priority, is_sensitive)
meta(key, value)
```

设备检查只读、未写入远端、未发起网络请求；公开报告不包含真实行值、
账号、设备 ID 或带哈希的数据库文件名。

## 7. 数据范围判断

可以确认：客户端构造并可能上传设备、环境、归因、日志和事件字段。
不能确认：服务端接收率、字段保留期限、跨账号关联方式或实时游戏消息
是否包含位置/聊天/交易内容。报告不把静态字段列表夸大成服务端已收集。
