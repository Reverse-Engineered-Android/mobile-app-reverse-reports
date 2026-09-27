# 风控源码证据索引

版本边界：Java ManualAuth/AutoAuth/Hybrid 证据来自微信 8.0.68 / versionCode 3003；`cp/w0.smali` 和原生组件证据来自微信 8.0.78 / versionCode 3180。

## 登录设备字段

- `evidence/java/manualauth-request.java:54-158`：BaseRequest、设备标识、SoftType、ClientSeqID、Signature、DeviceName、DeviceType、Language、TimeZone、渠道、品牌、型号、系统、国家、包名。
- `evidence/java/autoauth-request.java:48-115`：AutoAuth BaseRequest 与 EC key。
- `evidence/java/hybrid-pack.java:33-48`：DeviceID、UIN、funcId、RSA/Hybrid ECDH、pass key、route info。

## 设备信息采集

来源：`smali-classes11/cp/w0.smali`。

- `323-347`：读取 `adb_enabled`。
- `350-374`：读取 `development_settings_enabled`。
- `474-591`：读取 `/proc/cpuinfo` 并构造 CPU 信息。
- `734-762`：返回当前 device ID，空值时使用兼容 device ID。
- `765-828`：读取 IMSI。
- `947-997`：读取 SIM country。
- `1186` 以后：Android ID、device ID、首次安装时间和设备变化检测。

## Root/Hook

- Java RiskScanner：`su` 路径、PATH、root UID/zygote 进程关系、root daemon、setuid、debugger/recovery 文件，输出 `viruscheck` / `RiskCheck`。
- `com.unionpay.a`：检查 `/system/bin/su` 是否存在。
- Tinker：搜索 Java 栈中的 `de.robv.android.xposed.XposedBridge`。
- `libmis.so`：小游戏 V8/JS 调试、断点、栈、变量、Wasm 信号。
- `libhuiyanandroidsdk.so`、`libYTAGReflectLiveCheck.so`：活体核验。

## 误报剔除

- `libWCDB.so:41-59`：`integrity_check`、`quick_check`、`foreignKeyCheck` 是数据库 PRAGMA。
- `libowl.so` JNI hook 用于协程/JNI 调度；`is_root_path()` 只比较字符串 `/`。
- Matrix、crash、backtrace、perf hook 用于稳定性/性能。
- `libmarsquic.so` 的 `TracerPid` 是 debugger-aware 调试支持。
- `KindaDeviceServiceImpl.isRoot()` 当前调用链返回硬编码 `false`。

详细分类与证据强度见 [risk-control.md](../risk-control.md)。
