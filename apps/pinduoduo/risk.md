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
