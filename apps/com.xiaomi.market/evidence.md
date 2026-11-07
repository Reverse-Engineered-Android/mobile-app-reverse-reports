# 逆向证据

## 复现环境

| 项 | 值 |
|---|---|
| 样本 | `base.apk`，62,827,916 字节 |
| APK SHA-256 | `9b39be35a80d6eddfec960b309fc34ef4dd3449c387fa0b117f3191e21cb8066` |
| 反编译 | `jadx -d /tmp/xm-jadx base.apk` |
| 资源/Manifest | `jadx -r -s --output-dir /tmp/xm-res base.apk` 或等价资源导出 |
| 设备 | 已 root 的 Android 16 真机，包名 `com.xiaomi.market` |
| 设备证据时间 | 2026-11-07 |

只读命令均使用 `cat`、`ls`、`dumpsys`、`find`、SQLite/WAL 快照打开；未修改设备数据。涉及私有主机名/地址的访问方式不公开。

## 代码定位

| 事实 | 文件与行 |
|---|---|
| V1 端点 | `CommonCoroutineServiceApi.java:280-281` |
| V2 端点 | `DevelopCoroutineServiceApi.java:31-33` |
| V1/V2 参数 | `AppSecurityCheckManager.java:933-984` |
| 扫描埋点 | `AppSecurityCheckManager.java:628-640` |
| 待检列表 | `AppSecurityCheckManager.java:716-733` |
| 服务端间隔 | `AppSecurityCheckManager.java:318-327` |
| 自动扫描随机窗口 | `AutoScannerManager.java:35-51` |
| 自动扫描守卫与回调 | `AutoScannerJobService` 及反编译补类 `tryDoWork$1`，写自动时间点在补类 164 行 |
| 风险通知特征 | `RiskNotificationModelManager.java:741-795`、`664-671` |
| 活跃统计上传 | `AppActiveStatService.java:305-365,450-475` |
| 更新检查字段 | `LocalAppManager.java:1348-1367` |
| 应用列表开关 | `SettingsUtils.java:78-81`；`PkgUtils.java:265-280,327-334` |
| 评分 | `AppSecurityCheckRules.java:57-201` |
| 忽略集合 | `AppSecurityIgnoreCacheManager.java:57,200` |
| job id | `Constants.java:2328-2329` |
| 商店/开发者 base | `Constants.java:204,208,559,1626,3467,3557,3646-3649` |

## 关键请求构造

`requestAppSecurityCheckParams()` 的 V2 分支：

```java
new SecurityDetectRequestParam(
    localAppInfo.packageName,
    String.valueOf(localAppInfo.versionCode),
    localAppInfo.getSignatureMD5V2()
);
json = new Gson().toJson(arrayList);
trackRequestCheck("receive", z10, "v2");
coroutineRepository.detectAppSecurityV2(json, baseParameters, cVar);
```

V1 分支：

```java
linkedHashMap.put("packageNameList", TextUtils.join(",", nonSystemApps));
trackRequestCheck("receive", z10, "v1");
```

## 设备文件证据

### app_security_data

SHA-256：

```text
a6b95c535baa765e8ff8dd9e9ecb389ade38cb736a2408c873ebdb3a498b0b8c
```

长度 919 字节，mtime 2026-11-07 13:20。内容为服务端安全检测返回，节选已放在 [report.md](report.md)；其中 `com.bybit.app` 是服务端风险返回示例，不属于用户隐私包列表。

### MMKV

SHA-256：

```text
66a686cd26340ead420bd6a25d5324b6ccef64e257450edc9c0dfe877e879211  com.xiaomi.market_preferences
0a9b8643cf1dbea3ac9cb10e8156cf22e959cbf306259d9b7c20ac672b7ff1ca  com.xiaomi.market_preferences.crc
```

安全相关 key 与值见 [storage.md](storage.md)。

### JobScheduler

`dumpsys jobscheduler` 中 job #730 关键片段：

```text
JOB #u0a168/730:
  com.xiaomi.market/.business_ui.main.mine.app_security.auto_scanner.AutoScannerJobScheduler
  PERSISTED
  Requires: charging=false batteryNotLow=false deviceIdle=false
  Minimum latency: +18h15m44s357ms
  Max execution delay: +18h30m44s357ms
  Backoff: policy=1 initial=+30s0ms
```

时间为抓取时刻的相对值，只证明持久化调度与窗口形态。

### market_2.db

通过主库 + WAL + SHM 快照打开，`PRAGMA integrity_check = ok`。脱敏统计：

| 表 | 行数 |
|---|---|
| `app` | 119 |
| `app_usage` | 168 |
| `local_app_md5` | 160 |
| `h_b` | 614 |
| `install_history` | 47 |
| `package_remove_history` | 36 |
| `update_history` | 123 |
| `update_fail_history` | 108 |
| `pending_update` | 56 |

不公开数据库行值、真实包名列表、设备标识或来源路径。

## Manifest 计数

使用解析后的 `AndroidManifest.xml` 重算：

- `uses-permission` 112，去重 108；
- `<queries>`：22 package、11 intent、2 provider；
- 组件统计见 [permissions.md](permissions.md)。

## 采样限制

- `ClientAIRepo.fetchAdCandidate()` 在 jadx 主输出中无法恢复，已单独尝试 `--show-bad-code --single-class`，仍不能完整重建；
- `AutoScannerJobService$tryDoWork$1` 主输出缺失，单独类补出后确认自动时间写入；
- V1/V2 服务端内部判定逻辑不可由客户端证明；
- 动态网络抓包未包含在本报告；结论基于静态代码、常量、本地持久化与只读设备快照。
