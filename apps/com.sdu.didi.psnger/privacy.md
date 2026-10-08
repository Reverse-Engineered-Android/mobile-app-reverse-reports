# 隐私、告知与超范围判断

## 1. 隐私状态存储

`com/didi/sdk/app/launch/PrivacyHandler.java:118-138` 是首启协议的写入点：

```java
public static void b(Context context, boolean z3) {
    if (z3) {
        SharedPreferences sp = com.didi.sdk.apm.r.f(context, 0, "privacy_policy");   // :122
        com.didi.sdk.apm.r.a(sp.edit()
            .putBoolean("privacy_policy_ok", true)                                   // :124
            .putString("privacy_policy_region", c(context))
            .putInt("sp_privacy_policy_version", 2));
        ...
        lVar.o("启动协议弹窗，点击了同意按钮", new Object[0]);                        // :130
        lVar.o("用户同意了隐私协议，往sp存储相关信息", new Object[0]);                 // :136
        ...
        com.didi.sdk.privacy.f.c(context, 1, zBooleanValue);                         // :138
    } else {
        ...
    }
}
```

四项状态：`privacy_policy_ok`（布尔）、`privacy_policy_region`（区域字符串）、
`sp_privacy_policy_version`（整数 2）、以及动态协议侧的
`kONEDeviceLegalDialogUserDefaultCacheKey`（`:137`）。读取点在
`com/didi/sdk/privacy/f.java:84`、`com/didi/sdk/app/launch/UserStateService.java:118`、
`com/didi/sdk/app/launch/v.java:26`；另一写入点在
`com/didi/sdk/app/launch/c.java:20`（版本 2）。

`f.c(context, 1, zBooleanValue)` 的第二个参数 `1` 是同意态，`zBooleanValue`
是本次协议的布尔值，因此同意与协议版本是分开记录的两个维度。

## 2. 同意闸门覆盖范围

| 面 | 闸门证据 | 判定 |
|---|---|---|
| 启动协议 | `PrivacyHandler.java:118-138` | 有闸门 |
| 动态协议 | `com/didi/sdk/privacy/store/DynamicPrivacyStore`（`loadPrivacy`/`signPrivacy`/`signSpDocId`） | 有闸门 |
| 行程协议 | `com/didi/ride/biz/manager/RidePrivacyManager`（`requestPrivacy`/`signPrivacyWithCache`/`showProtocolDialog`） | 有闸门 |
| 导航拦截 | `com/didi/sdk/app/navigation/interceptor/BusinessPrivacyInterceptor` | 有闸门 |
| 协议落库签名 | `com/didi/sdk/privacy/request/PrivacyRequest.java:142,146,227` | 有闸门 |
| 小程序容器 | `com/didi/dimina/container/util/PrivacyFunction` | 有闸门 |

`PrivacyRequest.java:142` 构造的签名参数固定为：

```java
new Pair("signed_doc_str", str), new Pair("caller", "passenger_android"),
new Pair("appid", new Integer(10000))
```

`:193-254` 是 `signPrivacy` 协程，`:246` 记录 `doc/sign` 结果，`:254` 记录
`doc/sign接口请求出错`。即用户同意行为会被签名后上报服务端留痕。

## 3. 采集字段清单

### 3.1 设备与环境（`DeviceInfoNameEnum`，31 项）

```text
appName, packageName, appVersionCode, appVersionName, appVersionIssue,
osVersion, model, brand, cpu, cpuSerialNo, pixels, screenHeight, screenWidth,
totalSpace, totalDisk, isRoot, customId, screenSize, emulatorType, utcOffset,
countryCode, locale, mcc, mnc, networkOperator, simCarrier, networkType,
localIp, batteryLevel, phoneTime, isDebug
```

对应 `DeviceInfoNameEnum.java:4-34`。持久化的只有 `customId`
（`e82/f.java:337`：`if (TextUtils.isEmpty(d.B) && h.c(DeviceInfoNameEnum.customId))
d.B = b.a(context);`），其余按需读取。

### 3.2 广播电视网与位置

- `mcc`/`mnc`/`networkOperator`/`simCarrier`/`networkType` 来自
  `TelephonyManager`（`e82/f.java:10` 导入）；
- `localIp` 来自 `NetworkInterface` 枚举（`e82/f.java:24-26` 导入
  `Inet4Address`/`InetAddress`/`NetworkInterface`/`SocketException`）；
- 定位坐标不落明文：`location_info.db.location` 只有
  `_id, ts, type, byte_data BLOB` 四列。

### 3.3 蓝牙周边

见 [transfer.md](transfer.md) §2.4。字段含 `deviceName`、`bondState`、`rssi`、
`canConnectList`/`curConnectList`/`historyConnectList`。采集开关由
`isBtApolloOpen(context)` 控制（`SecurityManager.java:657,670`）。**这是本应用
最需要用户知晓的一类自动采集**：它能反映用户周边存在哪些蓝牙设备。

### 3.4 行为

`com/didi/security/diface/behavior/BehaviorTraceUploadParam`
（`{bizCode, dataJson, oneId, token}`）与 `BehaviorTraceData`
（`ArrayDeque<String>` 轨迹 + `ConsumeTimeData{actionConsumeTime,
mirrorsConsumeTime, totalConsumeTime}`）。仅用于人脸/活体环节的操作时序判别。

### 3.5 风控信号

`isRoot`、`emulatorType`、`isDebug`、`cpuSerialNo`、`ScreenShotMonitor`
（`com/didi/security/wireless/env/screen/`）、Hook 检测（`CheckHook`、
`AppUtils.checkMethodIsHook`）、代理检测（`AppUtils.ProxySec`，`:691,736,745`）、
Root 探测（`com/megvii/lv5/w8.java:47-48` 的 `which su`）、Frida/ADB 端口探测
（`com/megvii/lv5/f9.java:88` `{5555, 27042}`）。

## 4. 同意前后的采集边界

| 采集面 | 同意前 | 依据 |
|---|---|---|
| 启动协议弹窗本身 | 不采集，只展示 | `PrivacyHandler.java:118-138` |
| 协议签名上报 | 用户点击后 | `PrivacyRequest.java:142,227` |
| 设备画像 | 受 `privacy_policy_ok` 影响 | `UserStateService.java:118`、`v.java:26`、`privacy/f.java:84` |
| 蓝牙采集 | 受 `isBtApolloOpen` 叠加控制 | `SecurityManager.java:657,670` |
| 人脸/活体 | 需登录并显式进入核验流程 | `diface`/`onesdk` 路径 |
| 语音 | 用户发起语音叫车/报警 | `audio_record_2` 表 |

**判定**：核心采集存在客户端告知闸门。但不能证明每个第三方 SDK
（旷视活体、联通认证、支付宝 SDK、华为/小米/OPPO/vivo 推送、Teemo 设备探针）
以及每个 Web 页面与导出组件都经过同一闸门——这需要跨 SDK 的逐条验证，静态
只能确认应用自身的 `privacy_policy_ok` 链路。

## 5. 超范围判断

按“具备能力 / 条件触发 / 未证实”三档：

### 5.1 具备能力且无功能对应（超范围）

| 项 | 依据 |
|---|---|
| `MOUNT_UNMOUNT_FILESYSTEMS` | 系统级权限，普通应用无法获得，静态无对应功能 |
| `READ_LOGS` | 仅 `BreakpadStateTracker` 的 logcat 字段对应，崩溃诊断可用应用自身日志实现 |
| `SYSTEM_OVERLAY_WINDOW` | 与 `SYSTEM_ALERT_WINDOW` 能力重复 |
| `cpuSerialNo` | 无用户可见功能，仅风控信号 |
| `QUERY_ALL_PACKAGES` | 粒度过宽（虽有 `queryIntentActivities` 的实际用途） |

### 5.2 条件触发（需在特定场景才采集）

| 项 | 触发条件 |
|---|---|
| `ACCESS_BACKGROUND_LOCATION` | 行程中前台服务启动后 |
| 蓝牙周边设备 | `isBtApolloOpen` 为真且事件类型匹配 |
| 轨迹与传感器 | 轨迹库开关、行程或服务触发 |
| 录音 | 语音叫车或一键报警 |
| 人脸/证件 | 实名、支付或安全验证 |
| 崩溃转储 | 进程异常 |

### 5.3 未证实

| 项 | 说明 |
|---|---|
| 后台定位的实际频率与时长 | 需运行时验证 |
| 服务端画像留存时长 | 客户端不可见 |
| 服务端评分与处置 | 客户端不可见 |
| 服务端收到的密文载荷内具体字段 | `wsgenv`、`enReq` 由 native 生成 |
| 第三方 SDK 的独立采集 | 需逐 SDK 验证其是否遵守同一闸门 |

## 6. 权限与告知的对应

| 权限 | 是否有对应功能 | 是否应在协议中明示 |
|---|---|---|
| 定位（含后台） | 是 | 是 |
| 蓝牙扫描/连接 | 是（安全盾 + WSG） | **应明示** |
| 相机 | 是 | 是 |
| 录音 | 是 | 是 |
| 日历写入 | 是（AI 叫车行程） | 是 |
| 设备标识（OAID/自定义 ID） | 是（风控） | 是 |
| 应用可见性 | 是（可用性探测） | 可选 |
| 日志读取 | 否（过度声明） | 否 |

## 7. 只读设备端核对的隐私相关观察

设备端实际落盘情况（详见 [evidence.md](evidence.md) §8）：

- **无明文手机号**：`carhailing.db.history` 表（0 行）设计上含 `phone` 列，
  但代码路径 `LoginStore.java:211-213,338-340` 对其做 DES 加密后再存；
- **无明文坐标**：`location_info.db.location.byte_data` 是 BLOB；
- **无明文 token**：`shared_prefs/authToken.xml` 存 `seccd-authentication` 设备
  凭据，业务 `token` 由 `su1.e` 在内存持有；
- **`DIDI_DATABASE` 的 14 张表中只有 `city_detail` 有数据（369 行）**，
  `address`/`hot_address` 均为 0 行，说明该设备未沉淀常用地址；
- **`track_upload_sdk2.db` 与 `carhailing.db` 全表 0 行**，说明轨迹与行程历史
  未在此设备落盘（或已清理）；
- **`poi_base_lib_task_data_encrypt.db` 与 `poi_selector_task_data_encrypt.db`
  为加密库**，`sqlite3` 报 `file is not a database`，符合
  `li2/b.java:42`、`sj2/g.java:699` 的加密变体设计。

以上只读核对未写入设备、未发起网络请求。
