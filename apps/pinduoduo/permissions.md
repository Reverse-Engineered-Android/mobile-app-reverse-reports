# 权限弹框与运行时授权状态

本文件回答三个问题：应用声明了哪些权限、它在运行时实际拿到哪些、以及弹框在什么
条件下出现。数据来自 APK 静态清单（`unpack/AndroidManifest.xml`，经 AXML 解析）、
DEX 权限调用点，以及同一设备上该版本的 `dumpsys package` / `dumpsys appops`
只读快照。

## 1. 声明面：59 项 `uses-permission`

`AndroidManifest.xml` 共声明 **59** 条 `uses-permission`，其中
**35** 条是 `android.permission.*`，**24** 条是厂商推送 / 桌面角标 / 特定
OEM 的私有权限（`com.huawei.*`、`com.vivo.*`、`com.oppo.*`、`com.bbk.*`、
`com.sec.android.*`、`com.samsung.*`、`net.oneplus.*`、`com.coloros.*`、
`com.heytap.*`、`com.hihonor.*`、`com.soter.*` 及本应用自有的
`com.xunmeng.pinduoduo.permission.*` 两条）。

35 条标准权限按保护级别分为三层：

| 层 | 数量 | 权限 |
| --- | ---: | --- |
| normal（安装即授予，无弹框） | 20 | `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_STATE`、`CHANGE_NETWORK_STATE`、`BLUETOOTH`、`BLUETOOTH_ADMIN`、`MODIFY_AUDIO_SETTINGS`、`VIBRATE`、`WAKE_LOCK`、`FLASHLIGHT`、`GET_PACKAGE_SIZE`、`HIGH_SAMPLING_RATE_SENSORS`、`FOREGROUND_SERVICE` 及其 4 个类型子权限、`USE_FINGERPRINT`、`USE_BIOMETRIC` |
| dangerous（需运行时弹框） | 13 | `ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`CAMERA`、`RECORD_AUDIO`、`READ_CONTACTS`、`ACTIVITY_RECOGNITION`、`READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、`READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_AUDIO`、`READ_MEDIA_VISUAL_USER_SELECTED`、`POST_NOTIFICATIONS` |
| special（走 appops / 设置页，不走权限弹框） | 2 | `SYSTEM_ALERT_WINDOW`、`REQUEST_INSTALL_PACKAGES` |

`FOREGROUND_SERVICE` 的四个类型子权限为 `FOREGROUND_SERVICE_CAMERA`、
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`FOREGROUND_SERVICE_MEDIA_PROJECTION`、
`FOREGROUND_SERVICE_MICROPHONE`。

**`READ_PHONE_STATE` 不在声明清单内。** 设备侧 `dumpsys package` 的
`READ_PHONE_STATE` 命中数为 **0**（`requested permissions` 与
`runtime permissions` 两节均无），但 APK 的 DEX 里仍保留该权限的常量与判定
分支，共 **15 处**引用，分布在 `PermissionManager`、`PmmCheckPermission`、
`MainFrameActivity`、`jd2/c`、`dd2/f`、`dp0/f`、`dp0/g`、`g02/c`、`g02/d`
以及支付宝 SDK 的 `com/alipay/b/a/a/b/b.java`。也就是说：代码路径仍在，但
清单不再声明，运行时系统不会授予，这些分支恒走"未授权"侧。

## 2. 实际授权状态（同一设备、同一版本）

设备为 Redmi K70 Pro（`23117RK66C`），Android 16（SDK 36）。

| 项 | 数量 | 说明 |
| --- | ---: | --- |
| 声明的 `android.permission.*` | 35 | 清单静态 |
| install 层 `granted=true` | 22 | 全部是 normal 保护级 |
| runtime 层条目 | 13 | 需用户交互的 dangerous 权限 |
| runtime `granted=true` | **1** | 仅 `POST_NOTIFICATIONS` |
| runtime `granted=false` | **12** | 位置、相机、麦克风、通讯录、活动识别、媒体、存储 |
| 特殊权限 | 0 已授予 | `SYSTEM_ALERT_WINDOW` 为 `ignore`；`REQUEST_INSTALL_PACKAGES` 未出现在 appops 授权表中 |

install 层的 22 条与清单里 20 条 normal 的差额来自 2 条自签名级权限
（`com.xunmeng.pinduoduo.permission.MIPUSH_RECEIVE`、
`com.xunmeng.pinduoduo.permission.common.broadcast`），它们同为安装即授予。

运行时逐项：

| 权限 | 状态 | appops |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | granted=true（`USER_SET`） | `POST_NOTIFICATION: allow` |
| `ACCESS_FINE_LOCATION` | granted=false | `FINE_LOCATION: mode=ignore` |
| `ACCESS_COARSE_LOCATION` | granted=false | `COARSE_LOCATION: mode=ignore` |
| `CAMERA` | granted=false | `CAMERA: mode=ignore` |
| `RECORD_AUDIO` | granted=false | `RECORD_AUDIO: mode=ignore` |
| `READ_CONTACTS` | granted=false | `READ_CONTACTS: mode=ignore` |
| `ACTIVITY_RECOGNITION` | granted=false | `ACTIVITY_RECOGNITION: mode=ignore` |
| `READ_EXTERNAL_STORAGE` | granted=false | `READ_EXTERNAL_STORAGE: mode=ignore` |
| `WRITE_EXTERNAL_STORAGE` | granted=false | `WRITE_EXTERNAL_STORAGE: mode=ignore` |
| `READ_MEDIA_IMAGES` | granted=false | `READ_MEDIA_IMAGES: mode=ignore` |
| `READ_MEDIA_VIDEO` | granted=false | `READ_MEDIA_VIDEO: mode=ignore` |
| `READ_MEDIA_AUDIO` | granted=false | `READ_MEDIA_AUDIO: mode=ignore` |
| `READ_MEDIA_VISUAL_USER_SELECTED` | granted=false | `READ_MEDIA_VISUAL_USER_SELECTED: mode=ignore` |

该 UID 的 appops 表里只有三项为 `mode=foreground`，其余全部 `mode=ignore`：

```
READ_CLIPBOARD:      mode=foreground
WRITE_CLIPBOARD:     mode=foreground
AUDIO_MEDIA_VOLUME:  mode=foreground
```

`SYSTEM_ALERT_WINDOW` 在包级 appops 段为 `ignore`，并有两条 `Reject` 记录。
`READ_PHONE_STATE`、`GET_ACCOUNTS`、`BLUETOOTH_SCAN`、`NEARBY_WIFI_DEVICES`、
`BODY_SENSORS`、`ACCESS_MEDIA_LOCATION`、`CALL_PHONE`、`READ_SMS`、
`READ_CALENDAR`、`READ_CALL_LOG` 等未声明权限同样在 appops 中登记为
`mode=ignore`。

**结论**：本设备上拼多多**没有任何一项**危险权限处于已授权状态，
`ACTIVITY_RECOGNITION`（计步/活动识别）、位置、相机、麦克风、通讯录、
外部存储与媒体读取全部被拒。应用在此状态下仍能正常启动并完成浏览、登录等
基本功能。剪贴板与音量通道被限定为仅前台可用，这是应用主动向 appops 登记的
收敛结果，而非系统默认值。

## 3. 权限弹框的实际代码路径

### 3.1 三个弹框层

| 层 | 入口 | 说明 |
| --- | --- | --- |
| 启动隐私政策弹框 | `MainFrameActivity` 实现 `ne1.b` 接口的 `F`/`T0`/`W0` 回调 | 首启或政策版本变更时出现，文案见 §3.2 |
| 强制权限弹框 | `ne1` 包（`ForcePermissionHelper`，接口 `ne1.f`，实现 `ne1.c`，状态机 `ne1.i`） | 在隐私政策之后，针对 `READ_PHONE_STATE` 等敏感项 |
| 业务内权限请求 | `PermissionManager`、`PmmRequestPermission`、`JSPermission`、`ScenePermissionRequestActivity` | 由具体功能（扫码、语音、相册、定位）触发，非首启强制 |

`ne1.b` 是一个 10 方法的回调接口（`F`、`M`、`Q0`、`T0`、`U`、`W0`、`Y`、
`d0`、`k0`、`w0`），由 `MainFrameActivity` 实现；其中 `F(int,int)`、
`T0(int,int)`、`W0(int,int)` 三个带双 int 参数的回调承载隐私政策弹框的
同意/拒绝结果。

启动链路的埋点顺序为 `splash_force_permission_start`
（`MainFrameActivity.java:694`）→ `splash_force_permission_end`（:2689）→
`splash_onPermissionGranted_granted`（:2691），并把用户是否显式同意写入
埋点键 `permissionUserAllowExplicitly`（`"1"` / `"0"`，:2692）。

### 3.2 弹框文案（资源原文）

启动隐私政策弹框的正文取自资源 `privacy_policy.splash_privacy_policy_content_new`，
非服务端下发：

```
感谢您的信任！为便于您理解，现就《拼多多隐私政策》作如下简要说明：
1. 为向您提供注册/登录、下单交易等基本服务，我们会收集、使用必要的信息；
2. 为保障为您提供的服务顺利完成，保护您的账号与交易安全，我们可能会向第三方
   共享您的个人信息，我们仅会出于合法、正当、必要的目的共享您的个人信息；
3. 为便于您查询、更正、删除您的个人信息，我们列明了具体方式，也提供了账户
   注销的渠道；
4. 我们会严格根据法律法规及《拼多多隐私政策》，并参照业界先进实践，不断完善
   和提升对您个人信息的安全保障水平；
5. 关于保护您个人信息更详细的内容，详见《拼多多隐私政策》，请您阅读并确认。
   如有任何疑问，可随时联系我们。
```

按钮与链接资源：`privacy_policy.splash_privacy_policy_accept`（`同意`）、
`privacy_policy.splash_privacy_policy_refuse`（`不同意`）、
`privacy_policy.splash_privacy_policy_link`（`《拼多多隐私政策》`）、
`splash_user_agreement_link`（`、《服务协议》`）。资源里另有一句更简短的
`splash_privacy_policy_content`：

```
请阅读并确认《拼多多隐私政策》、《服务协议》，我们将按照政策和协议内容为您提供服务。
```

拒绝链路的文案为 `splash_privacy_exit_app_hint`
（`是否确认不同意，不同意将退出拼多多，要不再想想？`）、
`splash_privacy_exit_app`（`退出拼多多`）、`splash_privacy_policy_refuse_again`
（`仍不同意`）与 `splash_privacy_check_out_policy`（`查看协议`）。
强制权限层的拒绝文案是 `force_permission_dialog_privacy_refuse_content`
（`若您不同意，将无法继续为您提供服务`）与
`force_permission_dialog_privacy_refuse_tips`
（`建议同意本隐私政策，继续使用拼多多`）。即拒绝属于硬阻断，不是可跳过的选项。

弹框点击的两个链接由 `oe1/a.java` 的 `ClickableSpan` 处理
（`ForwardProps.setType("web")` + `setProps({"url": …})`），
分别指向 `private_policy.html` 与 `user_agreement.html`，
两者都带 `from_privacy_policy=true` 标记进入 Web 容器
（`oe1/a.java:65-69`）。

### 3.3 强制权限弹框的判定输入

`com.xunmeng.pinduoduo.force_permission` 的状态全部落在 MMKV `splash`：

| 键 | 读取点 | 默认值 |
| --- | --- | --- |
| `privacy_api_` | `ne1.i.G()` | `-1` |
| `refuse_api_` | `ne1.i.I()` | `-1` |
| `admission_api_` | `ne1.i.D()` | `-1` |
| `imei_api_` | `ne1.i`（`imei_api_` + 设备 tag 后缀） | `-1` |
| `privacy_api_ui_style` | `ne1.i`（`x()`） | — |
| `privacy_enterViewMode` | `ne1.i`（`y()`） | — |
| `privacy_api_interval` | `ne1.i`（`w()`） | — |
| `isFirstLamerRequest` | `ne1.i.F()` | `true` |
| `privacy_dialog_shown_` | `ne1.i`（`u()`） | — |
| `privacy_policy_accepted_4801` | `ne1.i`（`l()`，`== 1`） | `0` |
| `imei_permission_request_completed_4610` | `ne1/d.java:223` 写入 | — |
| `imei_device_tag` | `lb2/t.b()` 读取 | `0` |

`D()`/`G()`/`I()` 三项的取值由 `ne1.i` 读取，默认 `-1`（未定），说明弹框版本、
样式与"是否再问"由服务端决定。

设备实测值（只读解码 MMKV `splash`，12 个条目）：

```
imei_device_tag                        = 1
privacy_api_                           = 1
imei_api_                              = 0
refuse_api_                            = 1
admission_api_                         = 0
privacy_api_ui_style                   = 1
privacy_enterViewMode                  = 1
privacy_api_interval                   = 0
isFirstLamerRequest                    = 0
privacy_dialog_shown_                  = 1
privacy_policy_accepted_4801           = 1
imei_permission_request_completed_4610 = 1   （该键在文件中重复出现 3 次，末值胜出）
```

MMKV `force_permission` 只有 1 个条目：`privacy_passed_5200` = `1`。

即：**隐私政策已被用户接受**（`privacy_policy_accepted_4801=1`、
`privacy_passed_5200=1`），强制权限弹框已完成过一次询问
（`imei_permission_request_completed_4610=1`）。`imei_api_=0` 与
`admission_api_=0` 表明 `imei` 询问与准入询问均处于"关闭"档，而
`privacy_api_`/`refuse_api_` 为 `1`。这些状态与 `dumpsys appops` 里危险权限
全部 `ignore` 的记录一致——用户走完了弹框流程，但选择拒绝危险权限。

### 3.4 拒绝权限后的降级行为

权限被拒不会导致崩溃或白屏，代码走的是"取空值 + 继续"：

- 安全侧采集把无可访问权限的字段写成空串（`com.pushsdk.a.f13389d` = `""`），
  而不是抛异常或终止上报（见 [privacy.md](privacy.md) §3.3 的 `h()` 门控）。
- `wifi_list`、`locatin`、`cellinfo_list` 三条路径在调用前先经
  `com.xunmeng.pinduoduo.permission.scene_manager` 查询权限态，未授权时直接
  返回空串（见 [privacy.md](privacy.md) §3.5）。
- OAID 采集在未通过隐私闸门时**不执行**，而是注册
  `privacy_dialog_finish` 消息等待（`OaidInitTask.run`）；这是"同意前不采集"的
  显式实现，见 [privacy.md](privacy.md) §3.6。

### 3.5 同意闸门本身

同意闸门是 `ac2.b.n()`（`ac2/b.java:99`，委托到 `ac2.c` 接口的 `a()`，
实现类 `sc2.b`）。判定链为：

```
ac2.b.n() → f().a()            # sc2.b.a() → b92.a.b() → ne1.s.b()
ne1.s.b()  → MMKV force_permission.getInt("privacy_passed_5200", 0) == 1
```

`ne1/c.l()`（`ForcePermissionHelper` 的同意落盘点）在 `!ac2.b.n()` 时写入
`privacy_passed_5200 = 1`，并广播 `privacy_dialog_finish`——即该键就是
"用户已同意"的持久标志。`ac2.b.n()` 在全 APK 共 **52 处调用点、34 个文件**，
覆盖面远超风控采集本身（通知、存储初始化、免流、微信支付等）。

## 4. 复现方式

```bash
# 声明面（静态）
python3 tools/axml.py <AndroidManifest.xml>          # 解析 AXML，输出 tree
python3 tools/axml.py <AndroidManifest.xml> grep uses-permission

# 运行时面（只读，设备侧）
adb shell dumpsys package com.xunmeng.pinduoduo > dp.txt
adb shell dumpsys appops  | sed -n '/Uid u0a333:/,/Package com.xunmeng.pinduoduo:/p'

# 弹框状态（只读，设备侧；MMKV 需按 varint 键值对解析，见 tools/vita_registry.py mmkv）
adb shell strings /data/data/com.xunmeng.pinduoduo/files/mmkv/splash
python3 tools/vita_registry.py mmkv splash
python3 tools/vita_registry.py mmkv force_permission
```

本文件不公开任何设备标识、账号信息或权限明细以外的用户数据。
