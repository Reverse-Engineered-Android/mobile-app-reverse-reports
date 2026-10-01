# 风控机制全量清单

本文件覆盖 APK 中全部风控相关代码路径。每项给出入口、判定逻辑与数据边界。

## 1. anti-token（每请求反爬头）

| 项 | 内容 |
| --- | --- |
| 产生者 | `com.aimi.android.common.http.i`（单例 `i.e.f10796a`） |
| 短 token | `qb2.d.b().p(ctx, bn0.b.a().d())` → `SecureNative.p` |
| 长 token | `qb2.d.b().B(ctx, TimeStamp.getRealLocalTime())` → `SecureNative.b(Context, Long)` |
| 应用点 | `com.aimi.android.common.http.h.g(builder, request, flag)` → `i.g().b(url, forceNewShortToken, monitor)` |
| 头名 | `anti-token` |
| 失败行为 | 返回 null，不上头，并通过 `ITracker.error().Module(30074).Error(20020)` 上报 `failure_url` + 堆栈 |
| 隐私闸门 | `ac2.b.n()` 未通过直接返回 null |
| 时间戳开关 | AB `ab_timestamp_v2_5590`（true 时用 `getRealLocalTimeV2()`） |
| 白/黑名单 | `RiskControl.anti_token_list_new`、`anti_token_list2_new`、`anti_token_black_list`、`anti_token_black_list2` |

`anti_token_list2_new` / `anti_token_black_list2` 的元素是
`{"prefix": "..", "suffix": ".."}`，匹配规则为 `path.startsWith(prefix)`（prefix 空
则跳过）且 `path.endsWith(suffix)`（suffix 空则跳过）。日志 tag 为
`"Pdd.AntiToken"`，会记录 `useOptPart3` / `forceUseNewShortToken` / token 长度 /
耗时。

## 2. `enCryptInfoV3` 设备信息密文

| 项 | 内容 |
| --- | --- |
| 存储 | MMKV `secure`，键 `enCryptInfoV3` |
| 形态 | 前缀 `5ec1` + 长度字段 + 密文 |
| 观测长度 | 0x019e = 414 字节（同设备样本） |
| 产出 | `SecureNative.ng2(context, s1..s4, long, s6)` 家族（`ne`/`ng`/`eca`/…） |
| 读写切换 | AB `ab_use_new_style_read_and_write_encryptInfo_82500_red` |

同库还有 `pdd_id`（前缀 `5ec1` + 长度，形态相同）与 `scres`（1 字节）。

## 3. `scres` 签名内容注册表

- 存储：MMKV SharedPreferences，字符串，元素以 `"#####"` 连接。
- 读：`lb2.h.y()` → `qb2.c.y()`，按 `"#####"` 切分去空。
- 写：`lb2/o0.e()`，从服务端响应的 `scres` 数组重建，`v.d().putString("scres", joined)`。
- 用途：记录服务端要求客户端持有的签名内容标识集合。

## 4. `sdr` 签名描述符与上报端点

构造（`lb2/j.java` 的 `c()`）：

| 键 | 值 |
| --- | --- |
| `f` | 常量 `10` |
| `ud` | `q1.c.T()`（用户标识） |
| `pd` | `bn0.b.a().d()`（设备标识） |
| `sr` | scene 字符串 |
| `st` | `TimeStamp.getRealLocalTimeV2()` |
| `sc` | scene code |
| `info` | 内容 |

流程（`lb2/o0.d(scene, content, force)`）：

1. 前置：`scene != 0` 且 `pb2.g.d()`；并且 `vg0.a.h()` 或 `force`。
2. `qb2.d.b().s("sdr", true)` 置位 → `SecureNative.sdr(ctx, map)` → 复位。
3. 解析结果：`code = map["code"]`，`x-d1 = map["x-d1"]`。
4. 组装 `{"x_d1": <x-d1>, "code": <code>}`，POST 到
   `nb2.a.a()` = `<sv1.c.c(ctx)>` + `/video/config/fjbouedvm/dserubn`。
5. 请求头：`sv1.d.h()` 再追加 `x-d-t` = 从构造到发送的毫秒差。

响应处理 (`lb2/o0` 的回调)：`sr`、`lk`（0/1 → MMKV `lk`）、`scres`（数组 → 见 §3）。

scene 编码（`o0.a(String)` 的字符串 hash switch）：

| scene | 值 |
| --- | --- |
| `login` | 2 |
| `pdd_home` | 4 |
| `pdd_goods_detail` | 5 |
| `order.html` | 6 |
| 周期任务 | 7（空内容） |

`/video/config/fjbouedvm/dserubn` 同时出现在 v2 签名列表里，因此该端点自身也带
`x-p-t`/`x-p1`。

## 5. `error_code 54001` 挑战

见 [auth.md](auth.md) §7。要点：

- `VerifyAuthTokenProcessor` 解析 `{error_code, verify_auth_token}`，缓存 30 分钟。
- `iv1/d.a(url)` 生成 `Pair("VerifyAuthToken", token)`，`RiskControl.black_list_verify_auth_token_apis`
  默认 `[]` 排除，路径经 `rv1.h.b()` 归一化。
- `WrapperInterceptor` 在 `SpecialCode54001ServiceHolder.useVerifyAuthTokenFeature()`
  为真时注入。
- 成功后广播 `BotMessageConstants.RISK_CONTROL_VERIFY_AUTH_TOKEN`。

## 6. Root 检测

三条独立路径，结论汇总为上报字段 `is_root2`：

| 实现 | 判定 |
| --- | --- |
| `DefaultRootServiceImpl.isRooted()` | `Build.TAGS` 含 `test-keys`，或 `/system/app/Superuser.apk`、`/sbin/su`、`/system/bin/su`、`/system/xbin/su`、`/data/local/xbin/su`、`/data/local/bin/su`、`/system/sd/xbin/su`、`/system/bin/failsafe/su`、`/data/local/su`、`/su/bin/su` 之一存在 |
| `u43/l.java` | 11 条路径列表，额外包含 `/system/bin/.ext/su`、`/system/usr/we-need-root/su` |
| `v12/a.java`、`d60/c.java` | 执行 `/system/xbin/which su` |

其结果先写入静态 `Boolean f41377a` 缓存（`DefaultRootServiceImpl`），`g2.b.g()`
转发给 `IRootService.a.f41379a`。

native 侧：

- `SecureNative.cr()` → 原生 root 裁决，`lb2.h.c()` 缓存到 `f73487a` 再判等 1。
- `lb2.h.M()` = `Build.TAGS` 含 `test-keys`（纯 Java）。
- `lb2.h.N()` = 遍历 `Os.getenv("PATH")` 的每一段拼 `su` 并判断存在。
- `lb2.h.E()` = `M() || ro.debuggable == "1" || N()`。

## 7. 模拟器检测（加权打分）

`gn0/d.java`，`c(Context)` 累加权重，**`i14 > 3` 判为模拟器**；单条"命中即返回
true"的探测直接短路。

| 探测 | 来源属性 / API | 命中条件 | 权重 |
| --- | --- | --- | --- |
| `a()` | `gsm.version.baseband` | 含 `1.0.0.0` | 命中即 true；未命中 +2 |
| `d()` | `ro.product.board` | 含 `android` 或 `goldfish` | 命中即 true；未命中 +1 |
| `h()` | `ro.build.flavor` | 含 `vbox` 或 `sdk_gphone` | 命中即 true；未命中 +1 |
| `l()` | `ro.product.model` | 含 `google_sdk`/`emulator`/`android sdk built for x86` | 命中即 true；未命中 +1 |
| `k()` | `ro.product.manufacturer` | 含 `genymotion`/`netease` | 命中即 true；未命中 +1 |
| `n()` | `ro.board.platform` | 含 `android` | 命中即 true；未命中 +1 |
| `j()` | `ro.hardware` | 属于 `{nox, cancro, intel, vbox, vbox86, ttvm, android_x86}` | 命中即 true；未命中 +1 |
| `a()` | `gsm.version.baseband` 为空 | — | +2 |
| `i(ctx)` | `hasSystemFeature("android.hardware.camera.flash")` | 缺失 | +1 |
| `g(ctx)` | `hasSystemFeature("android.hardware.camera.any")` | 缺失 | +1 |
| `e(ctx)` | `hasSystemFeature("android.hardware.bluetooth")` | 缺失 | +1 |
| `f()` | CPU 核数（`basekit.util.h.c().b(...)`） | 值为 0 | +1 |

结果缓存在 `Boolean f61651a`，对外为 `o(context)`，上报字段 `isEmulator` 与耗时
`emulator_consume`。

## 8. 设备画像上报（`mi0/a.java`）

| 组 | 字段 |
| --- | --- |
| 版本 / 渠道 | `channel`、`commit_id`、`is_login`、`is_lite`、`tiny_plugin`、`interval_version`、`volantis_interval_no`、`version_change`、`app_version`、`patch_version` |
| 系统 | `sdk_int`、`market_model`、`board`、`hardware`、`rom_version`、`ota_version`、`is_harmonyos`、`incremental`、`security_patch`、`first_api_level`、`build_date`、`is64Bit`、`apk_arch`、`abilist`、`fingerprint`、`manufacturer`、`brand`、`instrumentation` |
| 时间 | `local_time`、`gap_time` |
| 屏幕 | `screen`（宽x高） |
| 存储 | `storage_free`、`low_storage`、`storage_app`、`storage_data`、`storage_cache`、`storage_total`、`huge_storage`、`huge_cache`、`storage_consume`、`storage_exception` |
| 安装 | `first_install_date`、`last_update_date`、`installer_name`、`package_info`、`start_component`、`volantis_no` |
| 多开 / 目录 | `data_dir`、`native_lib_dir`、`double_instance_v2`、`my_uid`、`install_token`、`data_can_read`、`data_can_write` |
| 调试态 | `ro.secure`、`ro.debuggable`、`sys.usb.config`、`sys.usb.state`、`init.svc.adbd`、`ro.adb.secure`、`android.os.Build.TAGS`、`is_root2` |
| 设置项 | `adb_enabled`、`development_settings_enabled`（`Settings.Secure.getInt`，调用方为 `com.xunmeng.pinduoduo.appstartup.appchanged.CommonReportInfoImpl`） |
| 电池 | `charging_status`、`charge_plug`、`is_charging` |
| 模拟器 | `isEmulator`、`emulator_consume`、`device_info_consume` |

`android_id` 取 `fc2.c.z(ctx, "com.xunmeng.pinduoduo.login.helper.LoginMethodsImpl")`，
即按调用方 tag 派生的设备标识。

## 9. 多开 / 应用克隆检测

```java
map.put("double_instance_v2", String.valueOf(
    !TextUtils.equals(dataDir, "/data/user/0/com.xunmeng.pinduoduo") &&
    !TextUtils.equals(dataDir, "/data/data/com.xunmeng.pinduoduo")));
```

另有 `DeviceUtil.isPddAppClone()`，两者互补。

## 10. 网络降级与熔断

见 [network.md](network.md) §9。风控视角下它的作用是：在检测到服务端降级信号
（HTTP `512` + `chiru-downgrade`）时把业务切到 CDN/缓存/明文通道，并通过 ITracker
`90547` 上报，避免在受限网络下持续失败。

## 11. Hook 对抗与完整性

| 组件 | 作用 |
| --- | --- |
| `libbytehook.so` | PLT/GOT hook 框架（自用） |
| `libpdd_sa_hook.so` | 敏感 API hook |
| `com.xunmeng.basiccomponent.c_bhook.DlopenMonitorConfig` | dlopen 监控配置 |
| `com.xunmeng.pinduoduo.shook.ShadowHook` | 14 个 native（`nativeInit`、`nativeGetRecords`、`nativeSetDisable`、`nativeGetDebuggable`、`nativeToErrmsg` 等） |
| `safemode/*` | 安全模式与修复运行时 |
| `com.xunmeng.pinduoduo.apm.risk.core.RiskPluginJniBridge`、`apm.risk.lock.LockMonitorJniBridge` | 锁/死锁风险与 JNI 桥 |
| `libgoldarch.so` | 支付请求体加解密（`/api/wormhole/equator`） |
| `h4.g` Efix 跳板 | 6,648 处热补丁代理点，见 [obfuscation.md](obfuscation.md) |

`ShadowHook` 的 `nativeGetRecords(int)` 会返回 hook 记录字符串，`nativeGetDebuggable`
返回自身被调试状态，属"检测自身是否被 hook"的闭环。

## 12. 客户端内建网络留痕

`net_adapter/hera/netcapture`（见 [network.md](network.md) §8）在客户端内保留请求/
响应的完整字段，是风控与 APM 共用的数据源。按 `SECURITY.md`，本报告只列出字段名，
不公开任何真实请求/响应值。

## 13. 第三方风控 SDK：支付宝设备指纹（`apmobilesecuritysdk`）

本项不属于拼多多自研风控，而是一条**完整且活着**的第三方指纹采集链路，
采集范围与自研部分不重叠，因此单列。

注意与仓库中[支付宝应用本身的报告](../alipay/device-risk.md)区分：那份报告分析的
是支付宝 APP 自己的 `mtopsdk`/UTDID 采集面，本节分析的是**被嵌入拼多多 APK 的**
支付宝 SDK（`com.alipay.apmobilesecuritysdk`），两者代码与端点均不同。

### 13.1 可达性与触发链

从拼多多的支付桥接进入，**全部为已验证**：

| 步 | 位置 | 动作 |
| --- | --- | --- |
| 1 | `tl0/c.java:53` | `PayTask.pay(...)`（拼多多收银台的支付宝路径） |
| 2 | `com.alipay.sdk.app.PayTask` | 准备支付上下文 |
| 3 | `com.alipay.sdk.data.c.a(context, map)` | 请求设备指纹 |
| 4 | `SecurityClientMobile.GetApdid` | `com.alipay.mobilesecuritysdk.face` 门面 |
| 5 | `APSecuritySdk.initToken(0, {utdid, tid, userId})` | 初始化并带三个上游标识 |
| 6 | `apmobilesecuritysdk.a.a` → `b()` | 组包并上报 |

注意 `initToken` 的入参来自**拼多多侧**（`utdid`/`tid`/`userId`），即拼多多的用户
标识会被送进支付宝 SDK 的指纹上下文。

### 13.2 采集字段（41 个 `AD`/`AL` 编码）

`apmobilesecuritysdk/d/c.java` 用 `map.put` 逐项登记，共 **41** 个编码
（`AD1`–`AD42` 缺 `AD4`/`AD25`，另有 `AL3`），编号到采集函数的映射在
`com.alipay.b.a.a.b.b`（1,184 行、40 个方法）中实现。**41 个编码全部已定位**：

下表是 `d/c.java` 中 `map.put` 到 `com.alipay.b.a.a.b.b` 的**逐条精确映射**
（`tools/alipay_map.py` 可复现），语义按被调方法体判定：

| 编码 | 采集函数 | 采集内容 | 权限门槛 |
| --- | --- | --- | --- |
| `AD1` | 局部变量 | SDK 内部派生串 | — |
| `AD2` | 局部变量 | SDK 内部派生串 | — |
| `AD3` | `b.g(ctx)` | **传感器列表**（`SensorManager`） | — |
| `AD5` | `b.i(ctx)` | 屏幕宽×高（`DisplayMetrics`） | — |
| `AD6` | `b.j(ctx)` | 屏幕宽度 | — |
| `AD7` | `b.k(ctx)` | 屏幕高度 | — |
| `AD8` | 局部变量 | SDK 内部派生串 | — |
| `AD9` | `b.m(ctx)` | 电话信息派生（`TelephonyManager`） | `READ_PHONE_STATE` |
| `AD10` | 局部变量 | SDK 内部派生串 | — |
| `AD11` | `b.d()` | **`/proc/cpuinfo`** | — |
| `AD12` | 上游配置对象 | 配置派生（非本机采集） | — |
| `AD13` | `b.f()` | 网络接口信息（`NetworkInterface`，含 `wlan0` 分支）的 fallback 串 | — |
| `AD14` | `b.h()` | **`/proc/meminfo`** | — |
| `AD15` | `b.i()` | data 分区**总容量**（`StatFs`） | — |
| `AD16` | `b.j()` | 外置分区**总容量**（`StatFs`） | — |
| `AD17` | — | 常量占位 `com.pushsdk.a.f13389d`（不采集） | — |
| `AD18` | 局部变量 | SDK 内部派生串 | — |
| `AD19` | `b.p(ctx)` | 电话信息派生 | — |
| `AD20` | `b.k()` | `SystemProperties.get("gsm.version.baseband")` **基带版本** | — |
| `AD21` | `b.f(ctx)` | 电话信息（门控） | `READ_PHONE_STATE` |
| `AD22` | — | 常量占位（同 `AD17`） | — |
| `AD23` | `b.l()` | **`Build.SERIAL`** | — |
| `AD24` | `摘要(b.h(ctx))` | 传感器列表的**哈希**（`com.alipay.b.a.a.a.a.f`） | — |
| `AD26` | `b.e(ctx)` | 运营商名称（`NetworkOperatorName`） | `READ_PHONE_STATE` |
| `AD27` | `b.q()` | **模拟器判定**：`/dev/qemu_pipe`、`/dev/socket/qemud`、`/dev/socket/genyd`、`/dev/socket/baseband_genyd`、`/sys/qemu_trace`、`/system/bin/qemu-props` | — |
| `AD28` | `b.s()` | **build.prop 仿冒判定**：`/system/build.prop`、`/proc/tty/drivers`、`ro.product.name=sdk` | — |
| `AD29` | `b.u()` | **模拟器属性判定**：`/sys/devices/system/cpu/`、`cpuinfo_max_freq`、`ro.build.fingerprint`、`goldfish`/`generic` | — |
| `AD30` | `b.r()` | hook 框架判定：`Class.forName("dalvik.system.Taint")`（Xposed 类） | — |
| `AD31` | `b.t()` | **build 特征判定**：`BRAND=generic`、`goldfish` 等键值比对 | — |
| `AD32` | `b.o()` | **开机时刻** = `currentTimeMillis − elapsedRealtime` | — |
| `AD33` | `b.p()` | `SystemClock.elapsedRealtime()` | — |
| `AD34` | `b.s(ctx)` | **按键锁 / 解锁凭据文件**：`isKeyguardSecure()`、`/data/system/password.key`、`gesture.key`、`gatekeeper.password.key`、`gatekeeper.gesture.key`、`gatekeeper.pattern.key` | — |
| `AD35` | `b.t(ctx)` | **电池**（`ACTION_BATTERY_CHANGED`） | — |
| `AD36` | `b.r(ctx)` | **连接类型**（`ConnectivityManager`） | `ACCESS_NETWORK_STATE` |
| `AD37` | `b.n()` | **时区** `TimeZone.getDefault().getDisplayName` | — |
| `AD38` | `b.m()` | **Locale** `Locale.getDefault()` | — |
| `AD39` | `b.c(ctx)` | **飞行模式** `Settings.System.airplane_mode_on` | — |
| `AD40` | `b.d(ctx)` | **音频**：铃声模式 + 各音量通道（JSON） | — |
| `AD41` | `b.b()` | data 分区**可用容量**（`StatFs`） | — |
| `AD42` | `b.c()` | 外置分区**可用容量**（`StatFs`） | — |
| `AL3` | `b.q(ctx)` | **BSSID**（`WifiManager.getConnectionInfo().getBSSID()`，Wi-Fi 关闭时返回占位值） | `ACCESS_WIFI_STATE` |

未被上述 41 个编码直接引用的采集函数另有三个，属 SDK 其他入口使用：

| 函数 | 采集内容 | 说明 |
| --- | --- | --- |
| `b.l(ctx)` | **Wi-Fi MAC** | `getMacAddress()`；为空或全零时回退到私有助手 `b.v()`——遍历 `NetworkInterface` 取 `wlan0` 硬件地址，失败返回 `02:00:00:00:00:00` |
| `b.o(ctx)` | **蓝牙 MAC** | `BLUETOOTH` 门控，回退读安全设置 `bluetooth_address` |
| `b.n(ctx)` | **`android_id`** | 经 `y82.d` 包装层读取 |

**SSID 不由本 SDK 采集**：`getSSID()` 只出现在
`com.alipay.sdk.data.c.java:123/174` 与 `com.alipay.sdk.packet.d.java:313`
（即 `PayTask` 支付链路自己的采集器），与 `apmobilesecuritysdk` 是两个独立层。
本报告不对该层展开。

**权限门控的实现细节（已验证）**：`b.a(ctx, perm)` 在**权限未授予时返回真**，
各采集函数写作 `if (a(ctx, "android.permission.X")) return 占位常量;`，
即"无权限 → 上报空值"，而非抛异常或省略字段。`b.n(ctx)` 通过
`y82.d.a(ctx, "com.alipay.b.a.a.b.b")` 读取 `android_id`，`b.s(ctx)` 用同一
包装层访问 `/data/system/*.key`，说明该 SDK 对**多用户与应用分身**有专门处理。
`AD34` 在取不到 `KeyguardManager` 时返回 `"0:0"`。**权限门控的实现细节（已验证）**：`b.a(ctx, perm)` 在**权限未授予时返回真**，
各采集函数的写法是 `if (a(ctx, "android.permission.X")) return 占位常量;`，
即"没有权限 → 上报空值"，而不是抛异常或跳过字段。`b.n(ctx)` 通过
`y82.d.a(ctx, "com.alipay.b.a.a.b.b")` 读取 `android_id`（跨用户/分身场景下的
包装层），`b.s(ctx)` 用同一包装层访问 `password.key`，说明该 SDK 对多用户与
应用分身有专门处理。`AD34`（按键锁）在取不到 `KeyguardManager` 时返回 `"0:0"`。

### 13.3 上报协议

| 项 | 值 |
| --- | --- |
| 端点 | `mobilegw.alipay.com/mgw.htm` |
| 方法 | `POST`，`Content-Type: application/x-www-form-urlencoded` |
| 头 | `uuid: <随机 UUID>`，另有 `id`、`operationType`、`gzip` 标志 |
| 请求体字段 | `extParam`、`operationType`、`id`、`requestData` |
| `requestData` 结构 | `DeviceDataReportRequest{os, apdid, pubApdid, priApdid, token, umidToken, version, lastTime, dataMap}` |
| 响应结构 | `DeviceDataReportResult{success, resultCode, apdid, token, currentTime, version, vkeySwitch, bugTrackSwitch, appListVer}` |
| 成功判定 | `resultStatus == 1000`（外层 `{resultStatus, result, tips}`） |
| 操作名 | `alipay.security.vkeyDFP.staticData.report`（活路径）；另定义 `…appList.get`、`…appListCmd.get` / `.reGet` |

**关键结论（已验证）：传输层没有任何签名或加密**，全程标准 HTTPS 明文表单；
`dataMap` 内的 41 项也是明文键值对，未在客户端做二次加密。这与拼多多自研的
`anti-token` + `enCryptInfoV3`（见 §1、§2）形成对照。

另有一个**休眠能力**：`getAppList` / `AppListCmdService` 在整包中**零调用点**，
`AppListResult{appListData, appListVer}` 只作为类型存在。即"上传已安装应用列表"
的能力已被编译进 SDK 但当前未启用（`DeviceDataReportResult.appListVer` 会回传
版本号，说明服务端可远程开启）。

SDK 内还硬编码了三组远程调试网关（`mobilegw.stable.alipay.net`、
`mobilegw-1-64.test.alipay.net`、`mobilegw.aaa.alipay.net`），由
`apmobilesecuritysdk.b.a.a(int)` 按环境选择。`apmobilesecuritysdk.a.a.a()`
里存在硬编码 **2016-11-10/11 与 2016-12-11/12** 时间窗 + `Math.random()` 的分支，
为历史遗留死代码，实际不生效。

### 13.4 与自研风控的关系

| 维度 | 拼多多自研（§1–§12） | 支付宝 SDK（本节） |
| --- | --- | --- |
| 触发 | 每请求 | 支付/收银台路径 |
| 标识 | 自研设备指纹 | 支付宝 `apdid`/`token` |
| 传输保护 | `anti-token` + 字段级密文 | 无 |
| 应用枚举 | `mi0/a.java` 设备画像（§8） | `appList` 能力已编译但休眠 |
| 模拟器判定 | §7 加权打分 | 独立实现（`AD27`/`AD28`/`AD42`） |

两套指纹体系相互独立，采集内容互补，均在单机内完成采集后各报各的服务端。

## 14. `libdyncommon.so`：native 侧环境探测与 AB 开关

§6–§11 列的是 DEX 侧的风控判定。§9.4 解出了
`libdyncommon.so` 的异或字符串池（四掩码并集 **458** 条，**已验证**），
池中内容是一条完整的
**native 侧反 root / 反 hook / 反模拟器环境探测链**，其判定结果供 DEX 侧
（`com.xunmeng.pinduoduo.secure` 与 `apm/risk/lock` 的 JNI 桥）取用。
该库混淆最重（19,801 个间接派发块、330 处 ADR+RET 返回地址间接化），
但字符串池解开后其用途可完整定性，**不含自解密或虚拟机**。

### 14.1 采集面（按池内明文归组）

| 类别 | 池内明文 | 判定含义 |
| --- | --- | --- |
| Root 二进制/路径 | `/sbin/magiskinit`、`/debug_ramdisk/magisk64`、`/system/xbin/magisk`、`/system/usr/we-need-root/magisk`、`/dev/magisk`、`/data/adb/magisk.img`、`/data/adb/magisk_simple`、`/dev/.magisk.unblock`、`/cache/magisk.log`、`/system/bin/sutemp`、`/data/local/bin/su`、`/vendor/bin/su`、`/data/local/xbin/susu`、`/system/app/Supersupro/Supersupro.apk` | Magisk / SuperSU / su 二进制与安装痕迹 |
| Root 进程名正则 | `^(su\|ku\.sud\|busybox\|daemonsu\|99SuperSUDaemon)$` | 按进程名匹配提权守护进程 |
| Hook / 注入 | `libriru_edxp.so`、`libsandhook.edxp.so`、`libSignatureKiller`、`android_server`、`lsphooker`、`EdHooker_`、`com.virjar.`、`com.sekiro.`、`LppiHelpers`、`/libc.so`、`/libandroid_runtime.so`、`sigaction`、`faccessat`、`__system_property_get` | Riru/EdXposed、SandHook、注入框架与 libc 层 hook 痕迹 |
| Xposed / ART | `_ZN3art9ArtMethod16EnableXposedHook…`、`_ZN3art30InvokeXposedHandleHookedMethod…`（§6.1 明文串） | ART 方法 hook 入口枚举（链接器内部符号表遍历） |
| 模拟器/虚拟机 | `/sys/module/vboxsf`、`/system/bin/mount.vboxsf`、`/sys/module/nemusf`、`/system/bin/mount.nemusf`、`/system/bin/nemuinit`、`/system/bin/ttVM-prop`、`/system/bin/bstshutdown_core`、`/system/lib/libandroidemu.so`、`/system/lib/hw/audio.primary.x86.so`、`/system/lib/hw/gralloc.ranchu.so`、`init.svc.ldinit` | VirtualBox / 网易 MuMu / 夜神 / BlueStacks / QEMU-ranchu 特征 |
| SELinux / 完整性 | `/system/etc/selinux/plat_property_contexts`、`/system/etc/selinux/plat_sepolicy_and_mapping.sha256`、`sepolicy_hash`、`hal_lineage`、`overlay`、`sdcardfs`、`NoNewPrivs:` | SEPolicy 版本与挂载层完整性、沙箱限制位 |
| Verified boot / 调试 | `ro.boot.verifiedbootstate`、`verified_boot_state`、`ro.build.fingerprint`、`ro.debuggable`、`ro.bootmode`、`sys.usb.config`、`adb_status`、`versionCode`、`properties_serial` | 系统完整性状态、调试/ADB/可刷机信号 |
| 进程自省 | `/proc/self/maps`、`/proc/self/mounts`、`/proc/self/mountinfo`、`/proc/self/fd`、`/proc/modules`、`/proc/%d/task/%d`、`/dev/pts/%d` | 自身内存布局与挂载视图（反 hook 核验） |
| JIT / DEX 内存 | `/memfd:jit-zygote-cache`、`/dev/ashmem/jit-cache`、`/memfd:/jit-cache`、`/memfd:dexfile`、`dalvik-classes.dex`、`[anon:dalvik-DEX data]` | 内存中 DEX / JIT 形态识别（动态加载检测） |
| 设备指纹 | `/sys/devices/system/cpu/cpu0/regs/identification/midr_el1`、`/apex/com.android.art`、`getifaddrs`、`neighInfo` | CPU 实现寄存器、ART apex、网卡与邻居表 |
| 无障碍/自动点击 | `ACCESSIBILITY_SERVICE`、`simplehat.clicker`、`autotool` | 无障碍服务滥用与自动点击外挂判定 |
| 反射自省 | `currentActivityThread`、`mInitialApplication`、`getApplicationInfo`、`getAbsolutePath`、`getStackTrace`、`java/lang/reflect/Method`、`java/lang/reflect/Field`、`java/security/cert.Certificate`、`java/util/Iterator`、`java/lang/Class`、`java/lang/ClassLoader`、`android/content/Context` | 通过反射拿 Application/Context、调用栈、类加载器、签名证书 |
| 宿主类名 | `com.xunmeng.pinduoduo.app.PDDApplicationLike` | 定位宿主 Application（反注入时的锚点） |

### 14.2 `ab_secure_*` 开关（风控总闸）

池中解出 **29** 个 `ab_secure_*` 键，**是本 app native 侧风控能力的运行时总开关**。
这 29 个键是四掩码并集的结果（**已验证**；每个键只在其所属掩码下成词）：

| 键 | 掩码 | 关联能力 |
| --- | :---: | --- |
| `ab_secure_hook_detect_7020` | A | hook 检测总开关（§11） |
| `ab_secure_xposed_detect` | C | Xposed 检测 |
| `ab_secure_xpmountd_7230` | C | Xposed 挂载点检测 |
| `ab_secure_xp_8170_001` | C | Xposed 检测 8170 版 |
| `ab_secure_emulator_detect_7030` | B | 模拟器检测 |
| `ab_secure_debug_detect` | B | 调试器检测 |
| `ab_secure_dump_7650` | B | 内存 dump 检测 |
| `ab_secure_new_sig` | B | 新版签名校验路径 |
| `ab_secure_sys_clc_7310` | A | 系统级采集（`sys.*`） |
| `ab_secure_smla_7230` | A | 小型/轻量采集 |
| `ab_secure_smla_7760` | B | 同上，7760 版 |
| `ab_secure_uad_7430` | B | UA/设备采集子模块 |
| `ab_secure_fdc_7430` | D | 文件/目录采集子模块 |
| `ab_secure_emcd_7430` | A | EMC 采集 |
| `ab_secure_emcd_8170` | B | EMC 采集 8170 版 |
| `ab_secure_rep_767` | C | `767` 版上报开关 |
| `ab_secure_repu_7700` | B | 上报分组 |
| `ab_secure_repu_7760` | A | 上报分组 7760 版 |
| `ab_secure_repu_8180` | C | 上报分组 8180 版 |
| `ab_secure_repu_8180_001` | A | 上报子开关 |
| `ab_secure_repu_8180_002` | A | 上报子开关 |
| `ab_secure_repu_8180_003` | D | 上报子开关 |
| `ab_secure_dede_7700` | A | 去重/脱敏（de-dup）分组 |
| `ab_secure_dede_7970_001` | A | 去重子开关 |
| `ab_secure_dede_7970_002` | A | 去重子开关 |
| `ab_secure_dede_7970_004` | C | 去重子开关 |
| `ab_secure_dede_7970_005` | B | 去重子开关 |
| `ab_secure_dede_7970_007` | D | 去重子开关 |
| `ab_secure_dede_8170_002` | A | 去重子开关 8170 版 |

合计 **68 个** `ab_secure_*` 键（native **29** + DEX 39，两集合**无交集**——
逐键比对 `comm` 结果为 0 条共同项），构成一套完整的风控能力开关面。

**计数口径**：native 侧共 **29** 个。若只用一个解码循环（掩码 A）普查，
只能得到 11 个；其中 `ab_secure_xposed_detect`、`ab_secure_emulator_detect_7030`、
`ab_secure_debug_detect`、`ab_secure_dump_7650`、`ab_secure_new_sig`、
`ab_secure_uad_7430`、`ab_secure_fdc_7430`、`ab_secure_xpmountd_7230`、
`ab_secure_xp_8170_001` 这 9 个键分属掩码 B/C/D，单掩码普查不可见，
而它们恰好是**最直接的反分析开关**，因此必须按四掩码并集统计。

要点：**这套开关让 PDD 可以在服务端逐项关闭采集而不换包**——
`ab_secure_skip_*_7630` 一族尤其说明 `7630` 版之后对"位置、基站、WiFi、
应用清单、进程、铃声、壁纸"这些敏感项做了可灰度开关的剥离设计。

### 14.3 与 DEX 侧的衔接

`libdyncommon.so` 的 `.dynsym` 中 `Java_*` 导出数为 **0**，其 `JNI_OnLoad`
导入 `dlsym`/`dlopen`/`dl_iterate_phdr` 并用间接派发注册方法
（`#1720`/`#1728` 位移在全库出现 5 次，**全部是打包偏移派发器**，
见 [obfuscation.md](obfuscation.md) §9.1.1）。池中还解出
`Java_com_xunmeng_pinduoduo_secure_SecureNative_aesDecryptWithKey` 与
`..._encodeBase64` 两条 JNI 名——这两条在全部 6 个 DEX 中**零命中**，
`SecureNative.java` 也无此声明，且没有任何库导出它们，故**不能**判为"第二套
`SecureNative` 实现"，只能是该库自身动态解析的目标名（详见
[obfuscation.md](obfuscation.md) §9.4.4）。

该库的加载点、清单归属与在机情况见 [evidence.md](evidence.md) §3，
在 26 个 `files/dynamic_so` 中随业务按需下载。

## 15. 组件框架（Vita）作为风控库的下发通道

Vita 本身不是风控组件，但它是**风控 native 库的唯一下发通道**，因此风控覆盖面
必须按它来解释。完整逆向见 [vita.md](vita.md)。

| 与风控相关的点 | 内容 |
| --- | --- |
| 证书固定 | 全 App **只有** 3 个 Vita 接口被 `forceEnalbePinner: true` 强制固定（`/api/app/v1/component/query`、`…/manual/query`、`…/manual/query/titan`） |
| 组件签名 | SHA256WithRSA，公钥硬编码在 `classes3.dex`（RSA-1024） |
| 载荷加密 | `security_level ∈ {1,2}` → `/volantis3-open/aes/component/...`，AES-128-CBC、取密钥前 16 字节、**全零 IV** |
| 密钥回传 | `/api/app/v1/component/report` 上报 `secure_level`、**`secure_key`**、`secure_version` |
| 下载/补丁事件 | 23 个事件码（`download_start`…`ipc_download_fail`） |
| 失败诊断字段 | `available_space`、`patching_file_name`、`patching_old_file_size`、`lock_file_existed`、`manifest_exists`、`is_support_zip_patch`、`is_zip_diff_package` |
| 行为画像 | `comp_resource_visit*`、`comp_daily_usage_statistics` 聚合上报 |
| 调试器开关 | MMKV `vita-debugger`、`scan-status-vita-debugger` |
| 版本封禁 | MMKV `vita_version_block_info` / `_fake_info`（样本为空） |

### 15.1 已注册未下发的风控组件（80 个）

`files/.newLocker` 的 183 个 `.vlock` 文件名是 `MD5(组件ID)`，反解得注册表
**126 个组件**；`vita_local_comp_v2` 的已安装表为 **46 条**，故 **80 个已注册
但从未下载**。其中风控/加固相关的至少包括：

```
libpdd_secure      libmeco_cookie    libsargeras        libshadowhook
libshadowhook_nothing  libxunwind    libriskplugin      libpdd_sa_hook
libbytehook        libCSoLoader      libpcrash          libpcrash_anr
libpcrash_dumper   libapm_cpu        libapm_thread_monitor  libpapm_trace
libpapmLeak        libBigAllocMonitor  libwallet_crypto_box  libfastdump
libmdumper         libxdl            libchat_msg        libgoldarch
```

**这直接解释了 `SE`/`meco`/`ShadowHook` 三族的零命中**：`SE`（11 个方法）、`meco.cookie.N`
（12 个）、`shook.ShadowHook`（14 个）三族 native 方法在 APK 内 51 个 ELF 中
（含四掩码字符串池并集、`#1720` 注册表扫描、原始字节搜索）**零命中**。其提供库
（`libriskplugin` / `libmeco_cookie` / `libshadowhook`）**在 Vita 注册表中处于
"已注册未下发"状态**——服务端在本设备上从未触发下载。

因此"全部风控代码"的覆盖边界应表述为：**已下发到设备的部分已穷尽**；
未下发部分（80 个组件）的**存在性、ID 与用途已确认**，但其内部实现不在本设备上，
无法从本机快照静态还原。

## 16. 采集闸门与告知面（与权限/隐私报告交叉引用）

风控采集不是无条件执行的，它同时受**同意闸门**、**权限闸门**与**服务端字段
裁剪**三层约束。这三层的完整证据见 [privacy.md](privacy.md) 与
[permissions.md](permissions.md)，本节给出与风控直接相关的落点。

### 16.1 同意闸门 `ac2.b.n()`

判定链为 `ac2.b.n()` → `sc2.b.a()` → `b92.a.b()` → `ne1.s.b()` →
MMKV `force_permission.privacy_passed_5200 == 1`（由 `ne1/c.l()` 在同意时写入）。
风控相关的强制入口：

| 入口 | 未同意时的行为 |
| --- | --- |
| `lb2/f0.o(int, Map)` | 直接 `return`，设备信息不组装、不上报 |
| `com.aimi.android.common.http.i.b(...)` | 返回 `null`，不下发 `anti-token` 头 |
| `OaidInitTask.run` | 不初始化 OAID，注册 `privacy_dialog_finish` 等待 |
| `TitanInitTask.run` | 不启动长连接，等待 `privacy_dialog_finish` |
| `PreLogicCallbackImp.isPreEnable()` | 前置拦截请求，收到 `privacy_dialog_finish` 后放行 |
| `rb2/j` 敏感 getter 族（6 处） | WiFi/小区/位置/`getExtraInfo` 返回空或 `null` |

`ac2.b.n()` 全 APK 共 52 处调用点、34 个文件，是全局"隐私已通过"标志。
即**同意前不存在第一方风控数据外发**，也没有长连接建立。

### 16.2 服务端字段裁剪

`ob2/b.h(String, JSONObject)` 对每个可选字段先查
`ob2/f.i(str)` → `f82237a.contains(str)`；命中则写入空串并跳过采集。
`f82237a` 的唯一来源是服务端配置项 `RiskControl.info_collect_blacklist`
（`secure/c.a()` → `secure/b.a(List)` → `lb2/h0.b(List)` → `ob2/f.A(List)`）。

因此**风控采集面本身可由服务端逐字段收缩**，且收缩后的字段仍以空串出现在
上报体中——字段存在不等于字段有值。

### 16.3 采集剥离开关

11 个 `ab_secure_skip_*_7630` 开关（默认全部 `false`）可逐项关闭无障碍列表、
运行进程、WiFi 列表、位置、基站列表、壁纸、铃声、IP 列表、WiFi 配置、
已连 WiFi 与 `eue20` 采集。开关的存在说明这些采集项是**有意的、可灰度的能力**，
而非历史遗留代码。逐项表见 [privacy.md](privacy.md) §3.4。

### 16.4 权限闸门

位置、WiFi 扫描、基站三条路径在调用前经
`com.xunmeng.pinduoduo.permission.scene_manager` 查询权限态，未授权直接返回
空串；OAID 受同意闸门约束。设备实测 13 项 dangerous 权限中 12 项被拒
（见 [permissions.md](permissions.md) §2），应用在此状态下仍能完成设备指纹、
应用清单、无障碍列表、运行进程与 Root/模拟器判定的采集。
