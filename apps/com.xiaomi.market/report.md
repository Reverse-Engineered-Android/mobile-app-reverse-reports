# 小米应用商店 4.129.10 逆向研究报告

## 1. 样本与结构

| 项目 | 值 |
|---|---|
| 包名 | `com.xiaomi.market` |
| versionName / versionCode | `4.129.10` / `40007720` |
| minSdk / targetSdk | `24` / `35` |
| APK SHA-256 | `9b39be35a80d6eddfec960b309fc34ef4dd3449c387fa0b117f3191e21cb8066` |
| APK 字节数 | `62,827,916` |
| `uses-permission` | 112 条声明，108 个去重权限 |
| `<queries>` | 35 个子元素：22 个 package、11 个 intent、2 个 provider authority |
| 组件 | activity 175（导出 23）、activity-alias 7（导出 7）、service 78（导出 26）、receiver 31（导出 24）、provider 15（导出 9） |

反编译主输出约 1.8 万级 Java 源文件；关键业务位于：

- `com.xiaomi.market.business_ui.main.mine.app_security`：安全检测、自动扫描、忽略缓存；
- `com.xiaomi.market.data` / `com.xiaomi.market.util`：本机应用列表、更新检查、UsageStats；
- `com.xiaomi.market.common.network`：Retrofit、`Connection`、`ConnectionRSA`；
- `com.xiaomi.market.common.analytics.onetrack`：OneTrack 埋点与行为特征；
- `com.xiaomi.market.ai`：本地 AI 推荐请求；
- `com.xiaomi.verificationsdk`：验证码、环境检测与设备签名。

## 2. 三个焦点问题

### 2.1 是否上传本机 app 列表：是，多渠道

核心链路是“每日自动安全扫描”。设备上存在持久化 Job：

```text
JOB #u0a168/730
com.xiaomi.market/.business_ui.main.mine.app_security.auto_scanner.AutoScannerJobScheduler
PERSISTED
Requires: charging=false batteryNotLow=false deviceIdle=false
Minimum latency / Max execution delay: 15m 窗口
```

代码侧 `AutoScannerManager.calculateRandomDelayWindow()`（30–51 行）以“今天 0 点 + 1 天 − 当前时间”为基线，再加 0–19 小时随机量；`AutoScannerJobService.tryDoWork()`（257–283 行）先检查当天是否已经扫描，再构造请求。

扫描列表由 `AppSecurityCheckManager.getSecurityCheckList()`（716–733 行）得到：先取 `UninstallAppsManager.getCanUninstallApps()`，再过滤 `!isSystem`。请求构造在 `requestAppSecurityCheckParams()`（933–984 行）：

| 版本 | 端点 | 请求内容 |
|---|---|---|
| V1 | `GET https://app.market.xiaomi.com/apm/app/securityDetect` | `packageNameList`：全部非系统可卸载应用包名，逗号拼接 |
| V2 | `POST https://hyperos.developer.xiaomi.com/xms/app/securityDetectV2` | `securityDetectRequestParamList`：JSON 数组，每项 `packageName`、`versionCode`、`signatureMD5`（签名证书 MD5） |

设备 MMKV 显示当前启用 `useSecurityDetectV2=true`、`enableAutoSecurityCheck=true`，`requestAppSecurityCheckDuration=7200000` 毫秒。设备文件 `files/app_security_data` 保留了最近一次服务端返回。

除安全检测外，客户端还有多类会携带本机应用信息的请求：

- 更新检查：`updateinfo` 批量携带包名、版本、旧 APK 哈希、是否商店安装、APK 来源、split 信息（`LocalAppManager.getUpdateInfoFromServer()`，1348–1367 行）；
- 差分包大小：`updateinfo/diffsize`，含包名与 `oldApkHash`；
- 桌面推荐/分类：`desktopfolder/recapps`、`desktopfolder/category`；
- 装包校验：`a0.app.xiaomi.com/pi/pinstaller/...`，含包名、版本、签名哈希、显示名、安装器与时间戳；
- 每应用活跃统计：`statistics/appactive`，包含包名、版本、安装器、渠道、活跃时间/类型、APK 哈希、首次安装/最近更新时间、安装来源等（`AppActiveStatService`）；
- AI 广告候选：`featuredV4/getAdCandidates` 请求对象包含 installed/uninstalled/upgraded/opened 等行为列表，以及 3/7/14/30 天应用使用时长；
- OneTrack：`active`、`uninstall`、安全扫描 `receive`、风险通知特征等事件。

### 2.2 风险应用检测机制：客户端上传清单，服务端判定

客户端把应用清单发到 `securityDetect` / `securityDetectV2` 后，由服务端返回风险数据。响应映射类包括：

- `AppSecurityCheckModel`：`judgingRulesV2`、`riskAppList`、`appSecurityText`、`updateTime`、`checkDuration`；
- `SecurityCheckRiskApp`：`riskType`、`riskLev`、`packageName`、`isVirusApp`、`isAdRisky`、`riskAppIgnoreDialogMessage`。

设备实际保存的服务端响应节选：

```json
{
  "judgingRulesV2": {
    "guard": "10",
    "appViolation": "10",
    "virusApp": "5",
    "adRisky": "3",
    "notVirusApp": "3",
    "totalExcellent": "0",
    "totalGood": "9",
    "totalAverage": "20"
  },
  "riskAppList": [
    {
      "riskType": "该APP存在非法金融行为",
      "riskLev": "9",
      "packageName": "com.bybit.app",
      "versionCode": "2026102800",
      "signatureMD5": "a994cf4c37ef0a698640a697d3651d63"
    }
  ]
}
```

本地计算集中在 `AppSecurityCheckRules.Companion`：

- `getGuardResult()` 57–91 行：guard 状态缺省计 10；
- `getRiskAppResult()` 98–159 行：`virusApp` 计 5；`adRisky` 只有在 ad-scan 支持且启用时计 3，否则按 `notVirusApp` 计 3；
- `getTotalResult()` 162–181 行：总分是 guard、风险项，以及 ad-scan 未启用时的 `appViolation` 之和；
- `getAppSecurityCheckResult()` 183–201 行：按服务端阈值映射 excellent/good/average/bad。

服务端还可通过 `checkDuration` 控制下一次扫描间隔；客户端把小时值转换为毫秒（318–327 行）。设备值 7200000 毫秒，对应 2 小时的请求间隔字段，但当天自动扫描仍受“每日本扫描”守卫与随机调度约束。

### 2.3 检测到风险/非法 app 是否上报：清单每天上报，检测结果未见专用回传

客户端没有把“检测出的风险 app 包名”单独回传的专用端点。静态检查到的相关上报如下：

| 上报对象 | 内容 |
|---|---|
| 每日安全扫描请求 | 全量非系统可卸载应用清单；V2 还含版本与签名证书 MD5。服务端天然知道设备上装了哪些应用 |
| 扫描触发埋点 | `trackRequestCheck("receive", isAuto, "v1"/"v2")`，`isTrackSampleHit(1000)`，即全量记录 `app_security_check` 事件 |
| 风险通知特征 | `RiskNotificationModelManager.getModelFeature()`（741–795 行）上报风险数量级、保留应用数量级、风险占比等级、风险应用 3/1 天使用时长与是否使用过风险应用；不含逐个风险包名 |
| 卸载/活跃 | 用户卸载或使用应用走普通 `uninstall` / `active` 事件；若被卸载的是风险应用，服务端可从包名与已有风险库关联 |
| 忽略操作 | `AppSecurityIgnoreCacheManager` 只把忽略集合写入本地 `security_check_risk_app_ignore_pref`，未发现上传调用 |

因此更准确的表述是：**完整应用清单每天上传，服务端返回的风险结果会保留在本地并驱动通知/评分；客户端另外上报“有风险”的数量级和使用特征，但未发现把检测结果逐包回传的专用通道。**

## 3. 自动扫描链路

1. AB/配置：`enableAutoSecurityCheck=true`、`useSecurityDetectV2=true`；通知模型与预测开关另有独立键。
2. `AutoScannerManager.updateAutoSchedule()` 只有在自动开关、通用安全检查支持、非长辈模式、job 不存在时才安排 job。
3. `AutoScannerJobScheduler.startSchedule()` 创建持久化 job（id 730）。
4. 到点后 `AutoScannerJobService.tryDoWork()` 检查 `lastAutoAppSecurityCheckTime` 是否今天，未扫描则执行请求。
5. 请求成功后写 `SP_LAST_APP_SECURITY_CHECK_TIME` / `SP_REQUEST_APP_SECURITY_CHECK_DURATION`；自动路径还会写 `SP_LAST_AUTO_APP_SECURITY_CHECK_TIME`（反编译补类 164 行）。
6. 扫描后取忽略列表、生成风险通知特征、更新风险通知 job。

## 4. 结论

小米应用商店在当前版本与设备状态下会每日采集非系统可卸载应用清单，并通过 V2 协议上传包名、版本和签名证书 MD5。风险判定主要由服务端完成，客户端负责展示、评分和通知。客户端有广泛遥测，会反映风险数量级与使用情况，但静态分析未发现“检测结果逐包回传”的专用端点；忽略操作也保持本地。

服务端的判定模型、历史留存、设备级风控决策不在客户端可证明范围内。
