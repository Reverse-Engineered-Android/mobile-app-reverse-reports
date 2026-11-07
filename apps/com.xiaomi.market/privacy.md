# 隐私与应用列表

## 应用列表采集总表

| 通道 | 采集对象 | 发送目标 |
|---|---|---|
| 安全扫描 V1 | 非系统可卸载应用包名 | `app.market.xiaomi.com/apm/app/securityDetect` |
| 安全扫描 V2 | 同上应用包名、版本、签名证书 MD5 | `hyperos.developer.xiaomi.com/xms/app/securityDetectV2` |
| 更新检查 | 包名、版本、旧 APK 哈希、是否商店安装、APK 来源、split | `updateinfo` |
| 差分更新 | 包名、旧 APK 哈希 | `updateinfo/diffsize` |
| 桌面推荐/分类 | 本机应用状态 | `desktopfolder/*` |
| 装包校验 | 包名、版本、签名哈希、显示名、installer、时间戳 | `pi/pinstaller/*` |
| 活跃统计 | 包名、版本、安装器、渠道、活跃信息、APK 哈希、安装来源 | `statistics/appactive` + OneTrack |
| AI 推荐 | installed/uninstalled/upgraded/opened 行为、3/7/14/30 天使用统计 | `featuredV4/getAdCandidates` |
| OneTrack | 普通行为、卸载、安全扫描触发、风险通知特征 | `tracking.*.miui.com` |

## 隐私开关

`SettingsUtils.getCanGetInstalledApp()`（78–81 行）要求：

1. `pref_key_get_installed_app_inner` 默认 true；
2. `UserAgreement.allowConnectNetwork()` 为 true。

`PkgUtils.getInstallPackages(int)`（265–280 行）在开关关闭时不再返回 `PackageManager.getInstalledPackages()`，而是只返回：

```text
com.xiaomi.xmsf
com.android.providers.downloads
com.miui.packageinstaller
com.miui.hybrid
```

用户同意隐私协议时会设置 `setCanGetInstalledApp(true)`；基本模式强制关闭该能力。

注意：安全扫描调用链也从本机应用管理器取数据，报告不能仅凭 `PkgUtils` 这一个入口推断安全扫描完全停止；设备快照显示安全扫描处于启用状态。

## UsageStats

AI 推荐与风险通知使用应用使用统计：

- `UsageStatsManager.queryUsageStats()`；
- 频率包括 3 天、1 周、2 周、1 个月；
- 字段包括包名、前台时长、启动次数、最近使用时间；
- 本地表 `app_usage` 也有 168 行聚合数据。

风险通知特征不会发送逐个风险包名，但会发送风险应用数量和风险应用使用时长等级。

## 行为表 `h_b`

`UserBehaviorFeatureRecorder` 把曝光、点击、下载、安装、打开、卸载、升级、搜索等事件写入 `h_b` 表。AI 请求构造时按类型查询并转成 `UserBehavior`。行为对象包含：

```text
appId / packageName / mainCategory / subCategory / apkSize /
isGoldenMIAward / downloadCount / commentScore / tagId /
appCategoryType / term / timestamp
```

设备库中 `h_b` 有 614 行，报告不输出真实包名行值。

## 安全扫描埋点

每次 V1/V2 请求都会触发：

```java
map.put("item_name", "app_security_check");
map.put("ref", REQUEST_APP_SECURITY_CHECK);
map.put("is_auto_check", isAutoCheck);
map.put("check_version", "v1" 或 "v2");
OneTrackAnalyticUtils.trackEvent("receive", map);
```

该事件说明服务端能知道设备何时执行了安全扫描，以及用的是哪个协议版本。

## 告知与控制

客户端存在应用列表隐私开关和用户协议判断，但当前设备快照显示开关为启用。风险应用通知有独立调度开关；忽略操作仅本地生效。
