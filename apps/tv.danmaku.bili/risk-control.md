# 风控机制

## 1. 组件与入口

风控分为四层：

1. **采集/判定**：`com.bilibili.gripper.riskcontrol.RiskCollect` 注册 36 个
   `Function2<Application,Data,Boolean>` 判定器；
2. **字段压缩**：`RiskCollect.a(...)` 生成分组十进制位图，
   `com.bilibili.gripper.riskcontrol.g` 把字段名映射为 4 位短码（121 项）；
3. **加密封装**：`BuildBody`/`PostBodyModel` 形成 GAIA JSON，`FingerprintReporter`
   或 native `ExBadBasket` 发送到服务端；
4. **服务端决策回传**：`RiskControl`/`RiskToken`/`RiskControlReporter` 处理
   验证码、token、H5 参数和风险事件。

## 2. 精确判定代码

### 2.1 Hook/root 检测

`jadx-all/classes9/sources/n20/a.java` 的 `C0191a`：

```java
// a(): stack walk
throw new Exception("hook");
if (element.getClassName().contains("de.robv.android.xposed.XposedBridge")) xposed=true;
if (element.getClassName().contains("substrate")) substrate=true;
if (element.getClassName().contains("frida")) frida=true;

// b(): /proc/<pid>/maps
if (line.endsWith(".so") || line.endsWith(".jar")) {
    path = line.substring(line.indexOf(' ') + 1);
    if (path.contains("xposed")) xposed=true;
    if (path.contains("substrate")) substrate=true;
    if (path.contains("frida")) frida=true;
    if (path.contains("riru")) riru=true;
}
// additionally checks:
// /system/lib64/libriruloader.so
// /data/adb/riru/modules, /data/misc/riru/modules
// System.getenv("RIRU_API_VERSION")
```

`C0191a.c()` 对 `a()`+`b()` 做 OR，并以一小时 TTL 缓存；返回
`{xposed,substrate,frida,riru}`。这解释了 `hasXposed`、`hasFrida`、`hasRiru`
三个判定器的精确依据。

`n20/c.java` 通过 `SystemProperties.get` 检查
`ro.build.display.id`、`ro.product.name`、`ro.lineage.version`、
`ro.lineage.build.version`、`ro.ttrom.version`、`ro.br.version`，
对应 Lineage/爬虫 ROM 判定。

### 2.2 36 个 collector

下表是 `RiskCollect` 静态初始化中的实际 ID、源文件和触发条件。每个源文件的
`invoke` 都是已反编译的判定代码；`n20.b.a` 方法名出现在 Kotlin
`FunctionReference` 元数据中，但 collector 的 `invoke` 不调用该 receiver，而是直接
读取 `Data.main`、调用 `n20.a`/`n20.c` 或查询系统 API，因此不存在未执行的隐藏检测体。

| ID | collector / 精确触发依据 |
| ---: | --- |
| 0 | `$1` `isUaSimulator`：UA/设备名含模拟器标识（mumu 等） |
| 1 | `$2` `isEmuRisk`：`Data.main["emu"]=="1"` |
| 2 | `$3` `isMissingCriticalSensor`：`sensors_info` 缺少加速度/GPS/光/磁/金鱼等关键传感器 |
| 3 | `$4` `isCpuAbiUnexpected`：`Build.SUPPORTED_ABIS` 与 CPU ABI 不一致 |
| 4 | `$5` `isRoBootHardWareUnexpected`：`ro.boot.hardware` 为 `goldfish`/`qemu`/`ranchu`/`virt` |
| 5 | `$6` `isCpuModelUnexpected`：CPU model 为 AMD/Intel 等异常值 |
| 6 | `$7` `isMultiAppBySys`：系统多开/分身特征 |
| 7 | `$8` `isMultiAppByTool`：`Data.main["virtual"]=="1"` |
| 8 | `$9` `isJavaMethodHooked`：反射 `getRunningAppProcesses`、`getProperty`、`getInstalledPackages` 出现 Hook 异常 |
| 9 | `$10` `noGpsSensor`：`Data.main["gps_sensor"]=="0"` |
| 10 | `$11` `noSpeedSensor`：`Data.main["speed_sensor"]=="0"` |
| 11 | `$12` `noLinearSpeedSensor`：`Data.main["linear_speed_sensor"]=="0"` |
| 12 | `$13` `isBinderProxy`：ActivityManager/Service Binder proxy 特征 |
| 13 | `$14` `isHooked`：通用 Java/native Hook 特征 |
| 14 | `$15` `hasXposed`：`C0191a.c().get("xposed")==TRUE` |
| 15 | `$16` `hasFrida`：`C0191a.c().get("frida")==TRUE` |
| 16 | `$17` `hasRiru`：`C0191a.c().get("riru")==TRUE` |
| 17 | `$18` `isUnidbgCpuEmu`：`/proc/cpuinfo` 含 `unicorn` 或 `unidbg` |
| 18 | `$19` `isRoot`：`Data.main["root"]=="1"` |
| 19 | `$20` `isMagiskProcessRunning`：`Runtime.exec("ps")` 输出含 `magisk`/`zygisk` |
| 20 | `$21` `isAdbEnabled`：`adb_enabled=="1"` |
| 21 | `$22` `isInDebug`：debug 标志/构建标志为真 |
| 22 | `$23` `hasMinicap`：存在 minicap 采集进程/服务 |
| 23 | `$24` `hasSuspiciousAccessibilityService`：启用的 AccessibilityServiceInfo 命中可疑服务 |
| 24 | `$25` `isAdbIme`：`Settings.Secure.default_input_method` 指向 ADB IME |
| 25 | `$26` `isBootloaderUnlocked`：bootloader 解锁状态为真 |
| 26 | `$27` `isLineageRom`：`n20.c` 的 Lineage 属性命中 |
| 27 | `$28` `isCrawlerRom`：`ttrom`/`br` 属性命中 |
| 28 | `$29` `isInstalledBySystem`：安装来源/系统安装标记命中 |
| 29 | `$30` `isUsingVpn`：网络接口/VPN 状态命中 |
| 30 | `$31` `isUsingHttpProxy`：`http_proxy` 属性存在 |
| 31 | `$32` `noBluetoothHardware`：蓝牙硬件信息缺失 |
| 32 | `$33` `noLightSensor`：光线传感器缺失 |
| 33 | `$34` `noGyroscopeSensor`：陀螺仪缺失 |
| 34 | `$35` `noBiometric`：`Data.main["biometric"]=="0"` |
| 35 | `$36` `isUsbConnected`：USB 连接状态为真 |

## 3. 风控字段与上报内容

`notes/gripper-fieldmap.json` 的 121 项由 `g.java` switch/label 恢复，短码不是加密。
高敏字段包括：

```text
imei, imsi, iccid, mac, bssid, wifimac, ssid, oaid, aaid, vaid,
adid, idfa, idfv, btmac, ip, gps_info, is_root, root, emu, virtual,
virtualproc, adb_enabled, screen, sensor, memory, biometrics, apps,
sys_app_cnt, androidapp20, androidsysapp20
```

`BuildBody.java` 的精确外层 JSON：

```json
{"header":{"encode_type":2,"payload_type":2,"encoded_aes_key":"...","ts":1700000000000,"encoded_version":"..."},"encrypt_payload":"..."}
```

接口：

- `POST https://api.bilibili.com/x/internal/gaia-gateway/ExClimbCongLing`
- `POST https://api.bilibili.com/x/internal/gaia-gateway/ExClimbKunLun`
- `POST https://api.bilibili.com/x/internal/gaia-gateway/ExBadBasket`

前两者由 Retrofit `RequestBody.create(MediaType.parse("application/json"), body)` 发送，
query 带 `access_key`；后者由 native 发送 `application/octet-stream`。

## 4. 加密链路

### 4.1 GAIA AES+RSA

`kntr/base/utils/fingerprint/FingerprintReporter.java` 的 `d(payload,pem,legacy)`：

1. 从 62 字符字母数字表生成 16 字符随机 AES key；
2. `dev.whyoleg.cryptography` 以 RAW 格式导入该 16 字节，`a(true)` 选 GCM 风格
   authenticated mode，对 payload 加密；
3. payload 按配置输出 Base64 或大写 hex；
4. 内置 `risk.gaia_rsa_public_key`/`risk.gaia_fg_p_k` 的 2048 位 RSA 公钥加密 AES key，
   输出 Base64 作为 `encoded_aes_key`；
5. 返回 `Pair(encoded_aes_key, encrypt_payload)`。

公钥是客户端内置公开材料，报告不复制完整 PEM。没有发现未知自定义分组算法。

### 4.2 风险 token RSA

`com.bilibili.lib.riskcontrol.a` 的 fallback 反编译 `a(String,boolean)`：

```text
Yh0.a.a/b 读取配置 public key
Base64.decode(publicKey)
KeyFactory("RSA").generatePublic(X509EncodedKeySpec)
Cipher.getInstance("RSA/ECB/PKCS1PADDING")
cipher.init(ENCRYPT_MODE, publicKey)
Base64.encode(cipher.doFinal(plaintext))
```

`b(int)` 负责主进程初始化、`RiskToken` 获取、`KEY_SHOWING` 状态和 token 返回，
不包含额外加密。`BLKV` 键 `bili_risk`、`key_risk_version` 只用于本地状态。

### 4.3 biliid 指纹 RSA/MD5

`com.bilibili.lib.biliid.internal.fingerprint.sync.protocol.security.RSA` 使用
`RSA/ECB/PKCS1Padding`，按 `modulus/8-11` 分块、hex/PEM 编码；
`Md5Utils` 是标准 MD5。该路径用于 biliid fingerprint 请求，不改变 GAIA 报文格式。

### 4.4 native 字符串解密与签名

`libbili.so` 仅含 `.datadiv_decode<id>` `.init_array` 字符串解密；45 个函数、
9,345 字节变更、513 个 ASCII 字符串均已由 `decode_libbili.py` 还原。解密后的
风险签名目标包含 `/system/bin/su`、`XposedBridge`、`riru`、`magisk`、
`TracerPid`、`ro.secure`、`/proc/self/maps`、`/proc/self/mounts` 等，
字段短码和 MD5 常量可由 `native/libbili-decoded.txt` 逐项复核。

## 5. 本地决策树

`assets/device_decision/prod/dd.json` header version `39194`，`list` 含 3,085 个节点；
节点键为 `n/o/l/r/v/pv`，`o` 统计 `or=1523`、`and=307`、无值节点 1255，
`tracks` 5 项、`props` 21 项。它把 `mid/av/buvid/brand/cpu_name/debug/free_storage/
ip_region/language/model/region/startup_time/theme/total_ram/total_storage` 等属性
映射到灰度、AB、风控和播放功能开关；这是本地规则解释器，不是黑盒加密算法。

## 6. 风险事件

`RiskControlReporter.EventType`：

```text
START=1, ALREADY_SHOWING=2, START_SHOW=3, H5_REQUEST_PARAMS=4,
H5_CALLBACK_TOKEN=5, CLOSE_CAPTCHA=6, CAPTCHA_CALLBACK_TOKEN=7,
RISK_CALLBACK_TOKEN=8
```

事件名 `infra.riskcontrol.process.event` 带 session_id、tag、event_type、
event_result、riskcontrol_result、token、error_code/message 和 version。
`RiskControl.showCaptcha` 使用版本 `"1.0"`，reporter 版本 `"2.0"`。

## 7. 越权、提权和超范围判断

- 代码会检测 root、Hook、模拟器、VPN/代理和已安装应用，也会读取设备标识和传感器；
  这是**采集能力**，不证明每个字段每次都会上报。
- 未发现写系统分区、提权到 UID 0、注入系统进程、绕过 SELinux、绕过服务端签名或
  伪造 DRM license 的实现。
- `ExClimb*`/`ExBadBasket` 的请求权限仍由 `access_key`、签名和服务端响应决定；
  客户端无法从静态代码证明越权成功。
- 过宽的设备/应用/位置/广告权限与导出组件扩大了潜在超范围面；是否未经告知要由
  同版本隐私文本、运行时权限和实际网络观测共同判定，本报告只给出静态事实。

## 8. 完整性声明

36 个 collector、121 个字段码、GAIA AES+RSA、风险 token RSA、biliid RSA/MD5、
native MD5、`.datadiv_decode` 字符串混淆、Kotlin function-reference 元数据和
`dd.json` 决策树均已给出结构、地址、字段或精确判定代码。没有保留“未知算法”、
“待解密”或“未分析的混淆闭包”。
