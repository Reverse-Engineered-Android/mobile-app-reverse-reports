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

## 应用列表权限关闭后的效果（设备实测 + 代码链路）

### 设备状态

测试设备 `com.xiaomi.market` 的运行时权限 `com.android.permission.GET_INSTALLED_APPS` 当前为：

```text
granted=false, flags=[ USER_FIXED ]
```

MMKV 中 `pref_key_get_installed_app_inner = true`（用户未在商店内关闭内部开关），`enableAutoSecurityCheck = true`，`useSecurityDetectV2 = true`。

最近一次安全检查时间：2026-11-07 17:45:00.198 +08:00。此时系统权限已处于关闭状态，扫描仍然执行。

`files/app_security_data` 在该时间点被重写，新文件 SHA-256：

```text
10a20463a0bbcf30223e4e0e0aad4e3122a35f6807d900b0a4da3c36559a205f
```

内容仍包含 `judgingRulesV2`、`unknownAppClearRules` 和文案，`riskAppList` 为空数组（本次扫描未返回风险项）。这说明：**仅关闭系统"读取应用列表"权限，并不能阻止安全扫描请求执行**。

### 为什么会这样

调用链是：

```text
AutoScannerJobService.tryDoWork()
→ AppSecurityCheckManager.getSecurityCheckList()
→ UninstallAppsManager.getCanUninstallApps()
→ LocalAppManager.getInstalledApps()
→ LocalAppManager.InstalledApps.init()
→ PkgUtils.getInstalledPackages()
```

`PkgUtils.getInstallPackages(int)` 在 `SettingsUtils.getCanGetInstalledApp()` 为 false 时只返回 4 个兜底包；但该函数只检查**商店内部开关 + 用户协议**，不检查系统运行时权限 `com.android.permission.GET_INSTALLED_APPS`。

代码里有两层控制，但没有交集：

| 控制 | 位置 | 检查内容 | 影响 PkgUtils 主入口？ |
|---|---|---|---|
| 系统运行时权限 | Android PMS | `com.android.permission.GET_INSTALLED_APPS` | 是，PMS 层直接拒绝 `getInstalledPackages()` |
| 商店内部开关 | `SettingsUtils.getCanGetInstalledApp()` | `pref_key_get_installed_app_inner` + 用户协议 | 是，PkgUtils 分支 |

关闭系统权限时，PMS 的 `getInstalledPackages()` 抛出/返回空；代码 catch 后得到空列表。`LocalAppManager.InstalledApps.init()` 还会调用 `ensureWhiteListPkgInfo()`，把 4 个白名单包通过 `getPackageInfo()` 逐个补进来，而这 4 个包在 manifest `<queries>` 中已显式声明，不需要 `QUERY_ALL_PACKAGES` 也能查询到。因此安全扫描拿到的是极小清单（理论上只含白名单包），**不是空请求**。

`AppSecurityCheckManager.getSecurityCheckList()` 再过滤 `isSystem`。白名单包里 `com.xiaomi.xmsf`、`com.android.providers.downloads`、`com.miui.packageinstaller`、`com.miui.hybrid` 都可能是 system/priv-app，是否出现在 V2 请求取决于运行时 `isSystem` 判定；至少"空列表"不会触发 `trackRequestCheck` 之外的短路，代码没有"清单为空就不请求"的守卫。

### 结论

| 操作 | 对"完整应用列表上传"的效果 |
|---|---|
| 仅关闭系统权限 `GET_INSTALLED_APPS` | 不能完全阻止；安全扫描仍会触发，但清单退化为最多 4 个白名单包（可能再被系统过滤）。更新检查/AI 推荐等走同一 `LocalAppManager` 缓存的通道也会退化 |
| 仅关闭商店内"读取应用列表"开关 | `PkgUtils` 主入口改为 4 个白名单包；安全扫描链路同样退化为白名单包。其它直接用 PM 的路径（如 `HelperV25` 云游戏图标）不受这个内部开关约束 |
| 同时关闭系统权限 + 商店内开关 | 效果与上面单关系统权限基本一致：内部开关关闭只是让 PkgUtils 走白名单分支，系统权限同时关闭时白名单分支通过 `getPackageInfo()` 仍可拿到 4 个包 |
| 关闭"应用安全检测"开关 | `AutoScannerJobService.tryDoWork()` 直接 return false，安全扫描请求停止；但更新检查/AI/活跃统计等通道不受影响 |
| 基本模式 | 强制 `getCanGetInstalledApp=false`，但同样只影响 PkgUtils 主入口，不代表所有 PM 调用停止 |

因此"从设置里关闭应用列表权限"只能把上传内容从完整清单压缩到白名单包，不能让安全扫描停止；要停掉安全扫描本身需要关闭"应用安全检测"开关。若目标是阻断所有带包名的遥测，代码层面没有单一开关，需要同时关闭多个功能（安全检测、推荐、更新、活跃统计）且部分通道仍会保留白名单包名。

### 未验证项

- 关闭权限后下一次扫描的实际网络请求字段（需要动态抓包或运行时 hook）；
- `isSystem` 过滤是否会把 4 个白名单包全部过滤掉，导致 V2 请求体为空数组；
- 服务端对空/极小清单的响应是否仍写 `app_security_data`（本次已写）。
