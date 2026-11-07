# 安全检测与风控

## 触发条件

自动扫描由两个层级的开关控制：

1. 服务端/实验开关 `enableAutoSecurityCheck`；
2. 用户侧 `pref_key_app_security_check` 默认为 true，且要求非长辈模式、支持通用守护。

设备 MMKV 当前值：

```text
enableAutoSecurityCheck = true
enableScheduleRiskNotification = true
useSecurityDetectV2 = true
lastAppSecurityCheckTime = 2026-11-06 04:59:56.916 +08:00
lastAutoAppSecurityCheckTime = 同上
requestAppSecurityCheckDuration = 7200000 ms
```

`AutoScannerManager.calculateRandomDelayWindow()` 生成“今天 0 点 + 1 天 − 当前时间 + 0–19 小时随机量”的窗口。设备 job #730 为持久化 job，窗口约 15 分钟。

`AutoScannerJobService.tryDoWork()` 每次先判断当天是否已扫描，然后调用安全检查；完成后写自动扫描时间并继续生成通知特征。

## 清单选择

`AppSecurityCheckManager.getSecurityCheckList()`：

1. 取 `UninstallAppsManager.getCanUninstallApps()`；
2. 过滤 `isSystem`；
3. 结果作为待检测应用。

另有家庭管理白名单 provider（`com.xiaomi.mihomemanager.whitelistProvider`）和系统镜像应用排除逻辑，用于决定应用是否显示为“可卸载”。

## V1 / V2 请求

| 版本 | 判定开关 | 参数 |
|---|---|---|
| V1 | `isUseSecurityDetectV2=false` | `packageNameList` |
| V2 | `isUseSecurityDetectV2=true` | JSON 数组 `[{packageName,versionCode,signatureMD5}]` |

V2 的签名 MD5 是签名证书 MD5，可让服务端识别同签名家族或伪冒包。

请求前都执行：

```java
trackRequestCheck("receive", isAutoCheck, "v1" 或 "v2");
```

采样函数参数为 1000，表示全量记录。

## 服务端返回与评分

`SecurityCheckRiskApp` 字段：

- `packageName`
- `versionCode`
- `signatureMD5`
- `riskType`
- `riskLev`
- `isVirusApp`
- `isAdRisky`
- `riskAppIgnoreDialogMessage`

`AppSecurityCheckRules.Companion` 的评分：

| 项 | 默认/设备阈值 |
|---|---|
| guard | 缺省 10 |
| virusApp | 5 |
| adRisky | 3 |
| notVirusApp | 3 |
| appViolation | 10 |
| excellent | 0 |
| good | 设备返回 9；代码兜底 4 |
| average | 设备返回 20；代码兜底 15 |
| bad | 代码兜底 30 |

总分逻辑：

- guard 结果按状态映射；
- 风险应用按 `virusApp` 或 `notVirusApp` 计分；
- 若支持并启用广告扫描，`adRisky` 计入；否则部分路径会加入 `appViolation`；
- 总分与阈值比较得到 excellent/good/average/bad。

## 忽略机制

`AppSecurityIgnoreCacheManager`：

- 忽略集合保存到 `Constants.SECURITY_CHECK_RISK_APP_IGNORE_PREF_NAME`；
- MMKV key 为 `security_check_risk_app_ignore_pref`；
- 忽略只影响展示与提醒，不改变安全状态；
- 未见上传调用。

设备返回的文案也明确写着：忽略后只是不再展示和提醒，安全状态不会改变。

## 风险通知

`RiskNotificationModelManager`：

- 从安全列表与风险列表计算特征；
- `RiskNotificationFeatureRawManager.saveFeatureRaw()` 先落库；
- 上报 `risk_notification_version`、`is_week_day`、`frequent_time`、`retain_app_count(+level)`、`risk_app_count(+level)`、`risk_app_ratio_level`、`risk_app_duration_past_3_day(+level)`、`past_1_day`、`is_used_risk_app_past_3_day/1_day`；
- 使用 XGBoost 预测合适的通知时间；
- 设备 `enableRiskNotificationModel` 在不同 MMKV 代际有 false/true，`enableRiskNotificationModelPrediction=false`。

通知点击上报风险应用数量，不回传风险包名列表。

## 下载前风控与验证 SDK

`SecurityCheckManager` 调用 `assc/getAsscToken` 与 `assc/verifyToken`，payload 包含 TrustZone 签名相关字段。验证码 SDK 会检测 root、Xposed/Substrate、模拟器、虚拟化、VPN、传感器和设备环境，并在必要时进入人工验证。报告不包含绕过或伪造这些机制的方法。

## “非法 app”是否上报

未发现以下专用行为：

- 把 `riskAppList` 逐包重新 POST 回服务端；
- 把用户点击“忽略”上传；
- 把本地评分结果整体上传。

已有行为包括：

- 每日上传完整清单；
- 扫描 `receive` 埋点；
- 风险数量级与使用时长特征事件；
- 普通 `active` / `uninstall` 事件，服务端可按包名关联。
