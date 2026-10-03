# 风控机制全量清单

## 1. 架构

```text
DFP/OneID/环境采集
        |
MTGuard JNI 命令  <-- libmtguard*.so + 预加载封装块
        |
Yoda / login challenge / server risk code
        |
mtgsig + certificate pin + DFP endpoint policy
        |
服务端会话、验证码、冻结或风险拒绝
```

客户端只能给出信号和本地处置；服务端阈值、画像留存和最终处罚不可静态证明。

## 2. MTGuard 精确命令表

`MTGuard.java` 中每个方法都直接给出命令号：

| Java 方法 | `ShellBridge.main3` | 判定/动作 |
|---|---:|---|
| `isSigCheckOK()` | 10 | 签名/完整性 |
| `uiAutomatorClickCount()` | 11 | 自动化点击计数 |
| `isRoot()` / `isrootDetect()` | 15 | Root |
| `isEmu()` / `issimulatorDetect()` | 16 | 模拟器 |
| `hasMalware()` | 17 | 风险/恶意应用 |
| `isProxy()` / `isproxyDetect()` | 18 | 代理/VPN |
| `inSandBox()` / `isRemoteCall()` | 19 | 沙箱/远控 |
| `isHook()` | 20 | Hook 框架 |
| `isDebug()` | 21 | 调试器 |
| `isCameraHack()` | 22 | 摄像头劫持 |
| `isVirtualLocation()` | 23 | 虚拟定位 |
| `isDarkSystem()` | 24 | 黑产系统 |
| `isAccessibilityEnable()` | 101 | 无障碍状态 |
| `deviceFingerprintData/ID` | 103/105/106 | 指纹读取 |
| `uploadDeviceInfo` | 103 | 设备信息回调 |
| `upload` | 110 | native 上传 |
| request signature | 120 | `mtgsig` |
| encryption/decryption | 31 | key-index 数据转换 |

`internalInit(context, i)` 的初始状态：

- 隐私态 `Privacy.createPermissionGuard().isPrivacyMode(context) == true`
  时传 `1`；
- 否则传 `2`；
- `MainBridge` 还向 native 传 `5`/`TRUE` 表示隐私态变化；
- 权限查询返回 `5` 时被解释为隐私模式。

## 3. 行为 token 的精确代码

`com.meituan.android.yoda.model.behavior.collection.b:138-163`：

```java
boolean isEmu = MTGuard.isEmu();
boolean isRoot = MTGuard.isRoot();
boolean hasMalware = MTGuard.hasMalware();
boolean isDarkSystem = MTGuard.isDarkSystem();
boolean isVirtualLocation = MTGuard.isVirtualLocation();
boolean isRemoteCall = MTGuard.isRemoteCall();
boolean isSigCheckOK = MTGuard.isSigCheckOK();
boolean inSandBox = MTGuard.inSandBox();
boolean isHook = MTGuard.isHook();
boolean isDebug = MTGuard.isDebug();
boolean isProxy = MTGuard.isProxy();
boolean isCameraHack = MTGuard.isCameraHack();

map2.put("isEmu", isEmu);
map2.put("isRoot", isRoot);
map2.put("hasMalware", hasMalware);
// ... 其余同名布尔键
map2.put("sign", behaviorSign(map2));
```

方法外先放入 `sT`、`bI`、`brR`、`aT/kT/tT/gT`、`cts`，再签 map。
判定依据不是单一文件是否存在，而是多个独立布尔信号 + 行为时间序列。

## 4. Root 与环境检测

当前 12.66.404 `libmtguard.so` 字符串中仍可精确命中：

- `/system/bin/su`
- `/system/xbin/su`
- `de.robv.android.xposed.installer`

12.35 反编译证据进一步给出 64 字节步长路径表，覆盖：

- `/sbin/su`、`/su/bin/su`、`/vendor/bin/su`、`/system/su`、
  `/system/bin/.ext/.su`；
- `/data/local/su`、`/data/local/bin/su`、`/data/local/xbin/su`；
- KingRoot/KingUser、360、SuperSU、daemonsu、旧国产 root 管理器；
- `/data/isRoot`、`/dev/ktools`、`/dev/rt.sh` 等标记。

底层静态导入包括 `stat/open/opendir/readdir`、`__system_property_get`、
`getenv`、`popen`、`dladdr`、`syscall` 和 `/proc/self/maps`，所以命令 15
不只依赖一个路径。

## 5. Hook、调试、模拟器、代理

- Hook：Xposed 包名、`hookMethodNative`、自身 ELF 映射/`dladdr`；
- 调试：命令 21 + `isDebug()`；
- 模拟器：命令 16；Mercury 还检查 `Build.FINGERPRINT/MODEL/MANUFACTURER/
  BRAND/DEVICE/PRODUCT` 的 generic/sdk/Genymotion 特征；
- 代理：命令 18；`Ok3NetworkInterceptor` 检查证书 pin；
- 摄像头/位置：独立命令 22/23，不由权限授予状态替代。

## 6. UI 自动化与行为

- `uiAutomatorCheck(Activity/View)`；
- `uiAutomatorClickCount()` -> 命令 11；
- `wtscore/plugin/detection/uiautomator` 两个混淆类；
- `aT/kT/tT/gT` 行为时间数组；
- Yoda `verifyResponseCode` 是服务端确认，不是本地可伪造的布尔值。

## 7. `mtgsig`、pin 与网络风控

| 机制 | 输入/处置 |
|---|---|
| 命令 120 | method、URI、body、headers、content type |
| 旧命令 2 | 编码排序参数 + 最多 16,200 字节 body + host |
| 14 pin | DFP 关键端点公钥 SHA-256 |
| 事件 50/303 | pin 失败，写 MITM 信息并可能取消 |
| 事件 `s/m` | 签名失败/未初始化/结果错误 |

`Ok3NetworkInterceptor.needCheck()` 只覆盖 DFPID、XID、device info、
mini-fama、heartbeat 路径；这说明 pin 是保护高价值画像面，而非所有业务 API。

## 8. 隐私态与采集开关

`ShowPrivacyDialogHandler` 的精确逻辑：

```java
if (agreed) {
    callback.put("isAgree", true);
    permissionGuard.setPrivacyMode(activity, false);
    Dsp.getSharedPreference().edit().putBoolean("state", true).commit();
    // 回到 MainActivity
}
```

隐私配置持久化在 `privacy_config` 的 `is_privacy_mode`；`config/d` 在版本或
SDK 变化时保留该值。以下代码显式受 `isPrivacyMode` 影响：

- MTGuard 初始化状态；
- `MainBridge` 权限状态；
- `OneIdPrivacyHelper`、`EnvironmentLog`、DFP 上报；
- 传感器代理 `privacy/proxy/v0`；
- push 的 privacy mode；
- launcher/statistics 的隐私分支。

因此“未同意时完全无采集”不是可由所有路径证明的绝对事实；已证明的是核心
画像、OneID、传感器和 native 初始化存在受限态。

## 9. native 库覆盖

当前 ARM64 67 个库的 SHA-256 清单在 `evidence.md`。风险相关分组：

| 组 | 库 | 结论 |
|---|---|---|
| 主风控 | `libmtguard.so` | 环境、签名、native 命令 |
| 预加载块 | `libmtguard_1/2/3.so` | 非 ELF，版本头 1.1.4/1.1.8/1.1.1 |
| 日志封装 | `libmtguard_log.so` | 实际是 DEX，非 ELF |
| 综合防护 | `libmet_defender.so` | 风险环境/加固相关 |
| 崩溃/Root 上报 | `libsnare_2.0.0.so` | 崩溃、ANR、Rooted 标记 |
| 统一标识 | `libunionid.so` | 设备/账号标识 |
| 支付 | `libPayRequestCrypt.so`、`libentryexpro.so`、`libCGCipherSDK.so` | 支付密码学 |
| 网络 | `libmtcronet.so`、`libcronet.*`、`libmtmap-combine.so` | 网络/地图 |

与 12.35 相比：10 个相同、34 个改变、23 个新增、18 个旧版独有；主
`libmtguard.so` 当前 SHA-256 为
`9a714157f288d772f1dd58e16ce72da10984a7fb6a74ae6ce7a65583ac931eba`。

## 10. 支付与业务风控

- 支付接口的 `@Encrypt` 字段与 `publicverify` 的 challenge/fingerprint；
- 订单 `checkpay/checkprepay/genpay`；
- 登录错误码 101039/101190/101144/101135/101285/101299；
- Yoda 行为 token；
- DFPID/XID/DPID、OAID 和厂商标识；
- 位置、Wi-Fi、基站、传感器、应用安装列表的采集入口。

这些信号可能用于账号、支付、优惠、骑手/接单和虚假地址等业务风险，但服务端
具体权重未知。

## 11. 完整性与不确定边界

本报告已枚举所有静态可见的风控组件和命令；native 命令内部的混淆控制流不被
当作“未解释的加密代码”，其用途由 Java 调用参数、`CryptoKeyIndex`、
`SecureTools` 和输出类型界定。服务端评分、留存和处罚无法从 APK 静态证明，
与客户端代码覆盖范围分开陈述。
