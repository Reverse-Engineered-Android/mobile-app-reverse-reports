# 隐私与告知

## 1. 结论摘要

APK 在主启动页实现了明确的隐私同意闸门：未同意时先显示第一阶段
对话框，不同意会进入第二阶段，第二阶段仍不同意则退出应用；只有同意
分支调用 `startAppInitViaUserAgree()`。静态代码显示主初始化、埋点
状态监控和同意状态写入位于该分支之后。

客户端同时具备位置、设备标识、媒体、日历、相机和麦克风能力，且存在
按接口白名单发送坐标、设备评分和广告开关状态的代码。**没有发送任何
网络请求**，因此报告只判断“有能力、在什么条件下调用/组装”，不把
“可能发送”写成“已经发送”，也不推断服务端保存期限。

## 2. 首次启动与同意链

### 2.1 入口判断

`cn/damai/launcher/splash/SplashMainActivity.java:566-569`：

```java
if (nz2.d()) {
    initSetting();
} else {
    showPrivacyDialogStageOne();
}
```

`tb/nz2.java:44-54`：

```java
public static boolean d() {
    SharedPreferences sp = f();
    if (sp == null) {
        return false;
    }
    return sp.getBoolean("privacy_dialog_agree_status", false);
}
```

隐私状态存于 `privacy_dialog` SharedPreferences，默认值为 `false`。

### 2.2 同意与拒绝分支

`SplashMainActivity.java:387-449`：

```text
showPrivacyDialogStageOne()
  onAgree  → startAppInitViaUserAgree()
  onUnAgree → showPrivacyDialogStageTwo()
                 onAgree → startAppInitViaUserAgree()
                 onQuitApp → clearStack(); System.exit(0)
```

`SplashMainActivity.java:451-462`：

```java
private void startAppInitViaUserAgree() {
    initSetting();
    UTAppStatusMonitor.getInstance().onActivityStarted(null);
    nz2.k(true);
    nz2.a();
}
```

`nz2.k(true)` 写入同意状态；`nz2.a()`（`tb/nz2.java:13-24`）把
`privacy_agreement_change_index` 清空，表示当前政策版本已被处理。

### 2.3 初始化范围

`initSetting()`（`SplashMainActivity.java:275-324`）在同意后执行配置
同步、更新检查、patch 应用、广告加载和预加载。它还包含条件：

```java
if (!nz2.d() || this.isStartFromQuitGuideMode) {
    InitUtils.b();
}
```

这说明部分初始化仅在“尚未同意”或“从退出引导重新进入”时执行。
主启动入口的最终分支仍以同意状态控制整体启动。

## 3. 位置

### 3.1 权限前置

多个定位调用点先检查：

```java
Tools.hasLocationPermission(context)
```

再进入 `getLastKnownLocation()` 路径。静态搜索没有发现直接把
`getLastKnownLocation()` 放在权限检查之前的主业务调用；这不能替代
Android 运行时权限日志，但与“未授权不读取”的实现意图一致。

### 3.2 接口白名单

`cn/damai/common/net/mtop/Util`：

```java
double[] coordinates = getDMCoordinates(apiName);
```

`getDMCoordinates(apiName)` 只有在 `isNeedCoordinate(apiName)` 为真时
才返回 `getDMLocation()`，否则返回 `null`。白名单来自 Orange 配置：

```java
String json = OrangeConfigCenter.c().b(
    "dm_mtop_head_coordinates",
    "mtop_coordinates_list",
    "");
```

随后解析 JSON 数组并检查当前 API 名是否包含于其中。网络层再把参数
`lat`/`lng` 合并为 `x-location = lng,lat`，见 [network.md](network.md)。

**结论**：坐标不是无条件头字段，而受运行时权限、位置缓存和远端接口
白名单共同限制；白名单具体内容依赖 Orange 远端配置，APK 静态字符串
不能证明本次会话实际包含哪些接口。

## 4. 设备标识与风险数据

APK 可调用的标识/设备接口包括：

```text
Wi-Fi MAC
Android ID
IMEI / MEID
IMSI
OAID
运营商、SIM、蜂窝位置
Build.MODEL / BRAND / RELEASE
网络接口 MAC
```

`PrivacyDoubleListDelegate` 对这些方法配置采样、调用限制和返回处理，
精确列表见 [risk.md](risk.md) §6。该机制说明某些调用会经过 AOP 规则，
但 `act` 枚举没有静态定义，不能把 `act` 直接解释为“发送”或“拒绝”。

请求还会组装：

```text
utdid
deviceId
umidToken
wua
apdId
x-netinfo
x-location
Dm-token
pictures_device_level_score
pictures_device_level_desc
```

字段的组装点和 MTOP 参数映射见 [network.md](network.md)、
[auth.md](auth.md)、[risk.md](risk.md)。没有值级抓包，报告不展示任何
真实设备标识、token 或坐标。

## 5. 广告与推荐开关

`cn/damai/launcher/utils/PrivacyCommonUtils.java`：

```java
public static boolean a() {
    return !TextUtils.equals(
        "false", ji0.z("interactive_ad_enabled"));
}

public static boolean b() {
    return !TextUtils.equals(
        "false", ji0.z("recommend_ad_enabled"));
}

public static void setInteractiveAdEnable(boolean value) {
    ji0.R("interactive_ad_enabled", "" + value);
}

public static void setRecommendAdEnable(boolean value) {
    ji0.R("recommend_ad_enabled", "" + value);
}
```

默认表达式在键不存在时返回“启用”（`"false"` 之外均启用）。启动迁移
代码 `tb/f04.java:46-57` 会把暂存键 `pre_key_ad_status` /
`pre_key_rec_status` 映射到推荐广告开关。静态代码能证明开关存在及读写
位置，不能证明用户在隐私页面看到的文案是否准确覆盖所有广告用途。

## 6. 相机、媒体与文件

- 实名/生物特征流程只在进入对应 activity 后请求
  `android.permission.CAMERA`，清单声明本身不会自动访问相机。
- `RPTakePhotoActivity.onActivityResult()` 从媒体、下载或文件 URI
  读取用户选择的数据，再缩放为最长边 `1280` 或 `800x480`。
- 请求中的原始图、处理详情、动作日志等见
  [transfer.md](transfer.md)。
- Android 13+ 使用 `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`；Android
  10-12 可能需要 `READ_EXTERNAL_STORAGE`，具体由系统权限结果决定。

这些能力与实名认证功能相符；是否超出功能必要范围还取决于实际上传
字段、保留周期和服务端用途，后两项在静态 APK 中不可证。

## 7. 日历、遥测与后台数据

清单声明 `WRITE_CALENDAR`，但报告没有在购票/搜索/票夹静态调用面中
发现自动写入日历的无条件路径；因此只判定“具备能力”，不判定“购票后
自动写入”。

遥测组件包括：

```text
UT / UTAppStatusMonitor
EagleEye trace id
XFlush failureMonitor
BehaviX bx_config / bx_delay / bx_feature_other
ACC/agoo push message and traffic tables
```

只读设备数据库 schema 支持以下落盘面：

| 数据库 | 表/内容 | 静态含义 |
|---|---|---|
| `data_cache.db` | `data_cache(type,content,timestamp,expire,ret_code,ret_msg,channel)` | 响应/配置缓存 |
| `accs.db` | `traffic(date,host,serviceid,bid,isbackground,size)` | 长连接流量统计 |
| `message_accs_db` | `message`、`accs_message` | 推送消息 |
| `ut.db` | alarm/stat/counter/log/config 系列表 | 统计与配置 |
| `ticketlet.db` | 仅 `android_metadata` | 当前样例无票夹业务表 |
| `yk_gaiax.db` | `yk_template_v2` 150 行及资产表 | 模板/动态资源缓存 |

没有读取或发布数据库行值、设备 ID、坐标和 token。

## 8. 未经告知或超范围获取判断

### 8.1 可确认

- 主启动有“同意 → 初始化、拒绝 → 二次选择、拒绝后退出”的明确闸门。
- 位置、相机、麦克风、生物识别、媒体、日历等敏感能力均声明于 manifest；
  主要运行时入口带权限或用户选择前置。
- 设备标识、位置、风险评分、遥测和广告开关存在实际组装/读取代码。
- 上传数据包含实名图像、动作与传感器日志，字段范围见
  [transfer.md](transfer.md)。

### 8.2 不可确认

- 隐私政策网页的每一项文字、版本和更新是否覆盖 APK 的全部第三方 SDK。
- 后台初始化器、系统广播或第三方 SDK 是否在首次同意前执行了某个字段
  读取；本次是静态 APK 分析，没有运行时抓包或 hook。
- 服务端保存期限、用途、共享对象和删除行为。

### 最终判定

**没有发现主业务启动绕过隐私同意闸门的静态路径，也没有发现把所有
声明权限无条件读取并上传的路径。** 同时，APK 的数据能力显著超出单纯
购票范围，包括设备风险、广告推荐、推送流量、实名生物特征和媒体文件；
这些数据是否“未经告知或超范围”取决于隐私政策实际版本、远端配置、
运行时授权和真实数据流。本报告未做实际测试，因此不作超出静态证据的
最终处罚性断言。
