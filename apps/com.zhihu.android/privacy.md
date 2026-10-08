# 隐私与告知

## 1. 告知链

### 1.1 首启协议弹窗（`app/ui/fragment/account/ProtocolDialogFragment`）

- “下一步/同意”按钮埋点：`e.a((IDataModelSetter) next, "fakeurl://privacy_aggree_panel", "", …)`，
  页面 id `11216`；同时上报 `eo.f66197a.a("passport","privacy","confirm","success")` /
  `"cancel"`。
- 协议文本由 `R.string.ckk` 等资源给出，UA 配置决定是否可点击
  （`uiConfig.protocolTextClickableSpan(...)`）。

### 1.2 协议与政策 URL（静态提取）

```
https://www.zhihu.com/term/privacy                    隐私政策
https://www.zhihu.com/term/zhihu-terms                用户协议
https://www.zhihu.com/term/privacy-protection-guide   隐私保护指引
https://www.zhihu.com/term/children-privacy           儿童隐私
https://www.zhihu.com/natsume/zhihu_agreement.html    协议汇总
https://www.zhihu.com/org_service_agreement
https://www.zhihu.com/fiore/h5/terms-collection/all-collections
https://api.zhihu.com/growth-misc/privacy
```

### 1.3 同意状态

- `com/zhihu/android/app/accounts/privacy/d.java`：SP 名
  `account_privacy_rights_<accountId>`（`k()`），另有
  `<packageName>_preferences_growth`（`l()`），键含 `app_mode`、
  `preference_gw_privacy_operate_state`（`int`，默认 `1`）、`save_app_mode`。
- `com/zhihu/android/cloudid/a.java` 含 `privacy_agreed` 判定。
- **设备端只读核对**：`shared_prefs/` 共 103 个文件，其中与本主题相关的包括
  `account_privacy_rights_<2 个账号哈希>.xml`、`zhihu_hodor_privacy.xml`、
  `zhihu_hodor_privacy_upgrade.xml`、`Map_Privacy.xml`、`IMAGE_X_AUTHORIZATION.xml`、
  `RUID_NET_CACHE_SP_FILE.xml`、`zhihu_ruid_shared_preferences.xml`。
  仅确认键名存在，未读取个人值。

## 2. 隐私相关端点

```
/account-privacy/cnts                      隐私内容开关
/account-privacy/cnts/status               状态
/account-privacy/cnts/setting              设置
/account-privacy/cnts/knows_reason_setting 知情理由设置
/api/privacy/settings                      隐私设置
/people/self/settings                      个人设置
/privacy_agreed                            同意标记
/api/v4/zhihu-basic-prod/{basic,uniform}/settings
/api/v4/zhihu-basic-prod/realname/{status,tip}
/api/v4/zhihu-basic-prod/hotfix_config     热修复/补丁下发
```

## 3. 采集范围与告知的对应关系

| 采集项 | 代码位置 | 与告知/功能的关系 | 最小必要评估 |
| --- | --- | --- | --- |
| 设备指纹串 | `library/fingerprint/c/a/b.java` | 安全风控条款 | 基本对应 |
| `x-at-df-if` | `GlobalRequestDecorator` | 安全风控条款 | 随多数请求携带，范围偏宽 |
| 已安装应用列表 | `com/hodor/library/c/f.java` | **未逐项披露** | **超出最小必要**（见 §3.1） |
| OAID/Widevine/AndroidID/IMEI | `adbase/oaid/DeviceID.java` | 设备标识条款 | 对应 |
| 精确/粗略位置 + 经纬度 | `/zst/events/i`、`lbs/a/b.java` | 同城/附近功能 | 功能必需 |
| 应用列表（风控策略 A/B） | `adbase/common/{SDKSmellUtils,ZhSdkSmellUtils}.java` | 广告/风控条款 | 偏宽 |
| 播放与行为时序 | `za_log_db_new_storage_v0`、`zhi-track-db-online_v1`、`begin_end_database` | 改进服务条款 | 概括授权 |
| 电量/流量/API 监控 | `apm_monitor_t1.db`（`t_battery`/`t_traffic`/`t_apiall`） | 性能优化条款 | 概括授权 |
| UA 采集 | `ua.db`（`__er`/`__et`/`__is`/`__sd`） | 安全风控条款 | 概括授权 |

### 3.1 已安装应用列表被枚举并上报（精确代码）

`com/hodor/library/c/f.java`（Kotlin 反编译）：

```java
// f.java:293 —— 冷启动分支（z == true 走 last_cold_track_installed_apps_millis 节流）
List<String> listA2 = a(c0556aB.c(), c0556aB.d());
if (!listA2.isEmpty()) { a(listA2); i = 0; }
else if (a(c0556aB)) {
    List<ApplicationInfo> installedApplications =
        f27877c.getPackageManager().getInstalledApplications(0);
    List<String> listJ = o.j(o.e(a(a(CollectionsKt.asSequence(installedApplications),
        c0556aB.c()), c0556aB.d()), e.f27882a));
    if (listJ.size() > 1) { a(listJ); ... }
}

// f.java:358 —— 逐包名拼接后作为 ZA 事件上传
private final void a(List<String> list) {
    ...
    wVar.a().a().l = "installed_app_list";
    wVar.a().l = a.c.Upload;                       // 该事件走上传通道
    zVar.j = MapsKt.mapOf(kotlin.w.a("installed_app_list",
        CollectionsKt.joinToString$default(list, ",", null, null, 0, null, null, 62, null)));
    Za.za3Log(bq.c.Show, wVar, zVar, null);
}

// f.java:236 —— 采样率由远端配置控制（默认值来自 sdk 配置）
private final boolean a(String str, Double d2) {
    if (TextUtils.isEmpty(str) || d2 == null) return false;
    try { return com.zhihu.android.appconfig.d.a(str, d2.doubleValue()); }
    catch (Throwable unused) { return false; }
}
private final boolean a(b.a.C0556a c) { return a("appListPermissionEnabledRatio", Double.valueOf(c.e())); }
```

节流与持久化（`com/hodor/library/c/c.java`）：

```java
context.getSharedPreferences("zhihu_hodor_privacy", 0)
  .getLong("last_track_installed_apps_millis", 0L)       // 温启动节流
  .getLong("last_cold_track_installed_apps_millis", 0L)  // 冷启动节流
```

触发点：`com/hodor/library/c/d.java`（`PrivacyManager.kt`）的
`a(boolean z)` → `f.f27875a.a(z)`；`d()` 默认以 `false` 调用。

**设备端只读核对**：`shared_prefs/zhihu_hodor_privacy.xml` 存在
`long name="last_cold_track_installed_apps_millis"`，即该冷启动采集计时器**已在本机写入**，
说明采集路径在本设备上被实际执行过。

**判定**：应用列表枚举 + `installed_app_list` 事件上传**超出**“问答/搜索/推荐”
功能的最小必要范围；`appListPermissionEnabledRatio` 表明其启停由服务端采样率调控，
用户侧无逐项开关。这属于**超范围采集**。

### 3.2 策略 A/B 应用枚举（`service/zh_sdk_base/adbase/common/SDKSmellUtils.java`）

```java
private static Set<String> strategyA_getInstalledPackages(Context context) {
    PackageManager packageManager = context.getPackageManager();
    if (Build.VERSION.SDK_INT >= 30) {
        AdLog.i(TAG, "ZhSdkSmellUtils 策略A 检查 QUERY_ALL_PACKAGES 权限状态, hasQueryAllPerm=" +
            (packageManager.checkPermission("android.permission.QUERY_ALL_PACKAGES",
                context.getPackageName()) == 0));
    }
    List<PackageInfo> listA = a.a(packageManager, 0, "2fa6e30502b5ee3cbde5e72e7b11f0a2cd2fd25e");
    ...
    hashSet.add(packageInfo.packageName);
}
```

另有 `strategyB_queryLauncherActivities(context)`（按 launcher activity 反推已安装应用）。
两策略合并去重，用于广告/风控归因；`QUERY_ALL_PACKAGES` 已显式声明。

## 4. 未经告知获取数据

- **未发现完全无告知的采集通道**：所有上报端点在隐私政策对应条款下均可归类。
- **但存在“概括授权 + 服务端遥控”的模式**：`appListPermissionEnabledRatio`、
  `CLOSE_ENCRYPT_KEY`、`zonfig` 开关决定采集是否发生，普通用户无法从界面感知
  具体采集项与频率。上述 `installed_app_list` 即典型例子。

## 5. 数据留存（客户端侧）

| 类别 | 本地位置 | 表/键 |
| --- | --- | --- |
| 搜索历史 | `zhihu_search.room` | `search_history`、`search_hot_words`、`search_tabs` |
| 浏览历史 | `zhihu.db` | `history` |
| 阅读进度 | `read_progress.room` | — |
| 稍后读 | `read_later` | `ReadLaterModel`、`AudioReadLaterModel` |
| 上传任务 | `MediaUploader.db` | `business_table`、`media_table` |
| 下载任务 | `filedownloader.db` | `filedownloader`、`Connection` |
| 盐选试读 | `manuscript_preload_html.db` | `manuscript_html` |
| 设备/账号 ID | `oneid.db` | `r` |
| 待上报日志 | `za_log_db_new_storage_v0`、`zhi-track-db-online_v1`、`begin_end*` | — |

共 42 个 SQLite（148 张表，677 行）、`integrity=ok`。见 [storage.md](storage.md)。

## 6. 证据等级

- 弹窗/埋点/端点/SP 键名/枚举代码：**已验证**（源码 + 设备端键名核对）。
- 服务端留存时长与二次利用：**超出客户端能力**，不推测。
- `appListPermissionEnabledRatio` 的线上实际取值：**不可达**（服务端下发）。
