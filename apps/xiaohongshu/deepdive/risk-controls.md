# 小红书 9.37.0 风控组件清单

本文按组件逐个给出：角色、证据位置、证据等级，以及**静态证据所能到达的精确边界**。每个机制均给出可复核的入口、控制流、数据结构或判定规则，不以“未知算法”掩盖未完成的分析。

## 0. 总览：纵深结构

| 层 | 组件 | 角色 | 证据等级 |
| --- | --- | --- | --- |
| 请求签名 | `libxyass.so`（Shield） | 全 native OkHttp 拦截器，生成 `shield` / `xy-platform-info` | 算法已恢复（见 crypto.md） |
| 请求签名 | `libtiny.so`（Tiny） | opcode 引擎生成 `x-n0/x-o9/x-p0/x-r4/x-r4o` | **已恢复**（签名操作码 `0x96f7fcac` 已定名，见 §2） |
| 设备指纹 | `libxyasf.so`（xya FP SDK） | 82 个 JNI 入口、51 个采集字段，自行 HTTP 上报 | **已恢复**（见 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md)） |
| JS 指纹 | Java 加解密缓存与独立服务声明 | `fpjs2.min.js` 仅为字符串，独立服务静态不可达 | 读写路径与 AES 已恢复；服务声明按静态不可达件处理 |
| 人机验证 | Walify（RN）+ ValidateActivity（H5） | 命中风控后的验证 | 已验证（触发链 + URL 来源边界） |
| 人脸核身 | 腾讯慧眼 WBCF + turingcam + 优图 + SM2 | 实名场景 | 结构已证实 |
| 支付风控 | Alipay+ / Antom 收银台组件 | 海外卡 | 结构已证实 |
| 端智能 | PMML LightGBM 模型 | 用户行为分群 | 结构已证实 |
| 伴随守护 | `libtinyd.so` | 管道 IPC + 进程伪装（`setArgV0("zygote")`）+ 崩溃兜底 | **已恢复**（见 [tinyd-companion-daemon.md](tinyd-companion-daemon.md)） |

对抗特征：OLLVM 字符串加密、CFF 控制流平坦化、**注解驱动方法名加密（`@u5`/`@v5`，519 站点已闭式还原）**、native 数字 opcode 分发、自定义 XOR 字节串解密。

> **归属**：上述机制是 `@u5/@v5` **加密注解**，不是「Petal 混淆」——`PetalConfig` 是小红书的**插件化框架配置类**（`PETAL_MODE` 是一个 `boolean` 常量 `false`，用作 React Native / Weex 注解的默认值；同类的 `LOCAL_PLUGINS`/`REMOTE_PLUGINS`/`COMPONENT_INFO` 全是插件清单），与混淆无关。真实的 Java 侧方法名加密载体是 `com.xingin.tiny.internal.u5`/`v5`，机制与 519 站点闭式还原见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §9.1–§9.2。

## 1. 请求签名：Shield（`libxyass.so`）

- `JNI_OnLoad` @ `0x3f484`，`RegisterNatives` @ `0x3f6f8`（4 个方法）。
- `intercept` @ `0x45764`，签名为 `(Lokhttp3/Interceptor$Chain;J)Lokhttp3/Response;`。
- signer `0x49634` 输出 `{u32 type, payload[≥64B]}`；assembler `0x467dc` 按 type 分派并装配 `shield`。
- 装配入口 `0x4b3d0`：组装 `P` → RC4 → 16 字节头 → Base64 → `NewStringUTF`。
- 库名隐藏：`c.java` 用字节数组 `{120,121,97,115,115}`（`"xyass"`）经 `SoLoadProxy.loadLibrary` 加载。
- 接入点仅 2 处：`ModelProfile.java:132`、`HeraAbilityImpl.java:52`。
- 启动任务 `r3.g`（"ShieldTask"）注入 `deviceId`/`appId` 到 `ContextHolder`；`x82.b` 打点 `recordShieldMainCostTime`。

**算法恢复情况**：外层（P 布局 / RC4 / blob 头 / Base64）逐字节验证；摘要 `H`（定制 MD5 族）64 轮调度 + Finalization 已完整恢复并三方向量通过；type 6/7 会话变换的宏结构、常量集、状态机已定性（详见 [crypto.md](crypto.md)）。

## 2. 请求签名：Tiny（`libtiny.so`）

- `jt6.a`（TinyInterceptor）挂载在 hera 链。
- 写入顺序：先 `x-legacy-did`（deviceId）、`x-legacy-sid`（会话 id），再读完整 request body。
- 调用链：`yya.f.e(method, url, bodyBytes)` → `u2.b(-1762132820, method, host, path, query, body)` → `t.a(opcode, args)` → `Map<String,String>` → 逐条写 header。
- 签名覆盖：method + host + path + query + body。
- dex 中可见混淆头名：`x-n0`、`x-o9`、`x-p0`、`x-r4`、`x-r4o`、`x-legacy-did/sid/fid/smid`。
- `JNI_OnLoad` @ `0x18afd8`；操作码字段 `[x19,#0xa4]`（写入点 `0x15ea20`），分发为 **61 个操作码比较块 + 61 字节谓词数组**，非二叉比较。`0x16b08c` / `0x17cdb0` 是同一操作码 `0x96f7fcac` 的两个 CFF 重复块，详见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §3–§5。**分发结构已完全闭环**：61 分派块 ↔ 61 个互不相同的谓词槽（`0x1253`–`0x128f`，零冲突）↔ 31 个操作码常量，且 61 个分派块的编译期跳转位移全部复现（§5.6.6–§5.6.7）。

**已闭环**：31 个 native 操作码**全部定名**。引擎是 **native(31) / Java(71) 双操作码空间、交集为 0** 的 VM；29 个 native 操作码在 dex 里定位到**确切的调用表达式**。其中签名主操作码为 **`0x96f7fcac`**（`yya.f.e` → `u2.b(op, method, host, path, query, body)`），另有 `0x96d0a479`（重算）、`0x259cebf7`/`0xcd554fab`（字节数组变换）、`0xd40131d5`（Base64 载荷校验）与之配套。

同一引擎还承担 **TLS 证书链上报**（`0xae8750a7` ← `nlb.q.intercept` 取 `handshake().peerCertificates()`）、**HTTP/2 peer principal 上报**（`0x9701e74c` ← `i4c.a.invoke`）、**长连接下行消息处理**（`0xb20a0be3` ← `nlb.g.onMessage`）、**定位上报**（`0x2f036831` ← `com.xingin.xhs.net.t1.i`，3×double + 2×float）、**传感器注册**（`0xcf7db9ff` ← `j6`）、**前台状态**（`0xc23a168e` ← `r`）、**动态代理转发**（`0x2ad1c199` ← `f6.invoke`）。

完整 31 项对照表见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §5.6.3。**分发层结构量已闭环**（§5.6.6–§5.6.7），**域区内部逐块算术 lift 亦已完成**（154 个域原语 + 九项密码学指纹守恒逐项 EXACT，§5.6.9）。Java 侧的名字/字符串混淆同样已闭式还原（`@u5/@v5` 519/519；字符串解密器 811/811 调用点全映射、0 未映射；daemon dex 三层混淆完整审计，§9）。

#### 2.1 Tiny 引擎内的三处额外成分

除"opcode 分发 + 签名头生成"之外，`libtiny.so` 内另有三处实质成分。三者均已定名：

| 成分 | 位置 | 角色 | 证据 |
| --- | --- | --- | --- |
| **内联 X25519 域运算** | `0x525000`–`0x531e4c`（入口 `0x525024`） | SDK 初始化/配置注入路径上的曲线域运算（`0x3c6d0ac1` / `0xae821439` 两个操作码执行） | radix-2⁵¹ 掩码 178 处 + `extr #51` 130 处 + ×19 归约 18 处 + **a24 = 121666** + RFC 7748 标量钳位逐位吻合；Ed25519 常量 0 命中（§2.2.1） |
| **字符串加密（两个解码器）** | `0x18c940`（203 次调用）、`0x18d5e4`（118 次调用） | 隐藏全部上报端点、检测文案、ART 符号名 | 20 项调度表的逐字节双射，闭式还原；**320 个调用点解出 312 条明文**（§2.4.5） |
| **内嵌 Lua 解释器** | 明文标识符落点见 §2.4.5 | 脚本化风控逻辑 | 解出 `pcall`/`setmetatable`/完整元表运算符集/`__index`/`__newindex`/协程/`popen`/`lines`/`input`/`getupvalue` 等 |

**字符串加密解出的内容直接扩大了风控面认知**（§2.4.5 有完整分类）：四个上报端点（`.../api/v1/register/android`、`/cfg/android`、`/prb/android`、`/dvf/vab/android`）、两套 SDK 配置 JSON、反调试文案（`TracerPid:`、`detect tracer …`、`GetUntrustedIPackageManager`）、以及一组 **ART 内部符号**（`_ZN3art2gc9collector17ConcurrentCopying12MarkingPhaseEv`、`_ZN3art9JNIEnvExt11NewLocalRefEPNS_6mirror6ObjectE`、`_ZNK3art12StackVisitor24GetCurrentQuickFrameInfoEv`）——后者是典型的 **ART 内联/内存扫描检测**手法（按符号名定位运行时结构）。

## 3. 设备指纹：`libxyasf.so` 与 Java 调度层

本节为摘要。完整逐条清单见 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md)。

- 调度中枢 `pt.a`（混淆名，classes5.dex）；辅助 `qt.a`（"virposd"，uid→`u0_a%d` 换算，解析 `/proc` 判断运行身份）。
- 采集面**已逐条列出**：79 个静态 JNI 导出 + 3 个 `RegisterNatives` 动态方法，落到 8 个 protobuf 子消息、**51 个字段**（字段编号全部取得）。
- root/模拟器/Xposed/VirtualApp/ptrace 检测均在 native 完成，结果经 `POST https://as.xiaohongshu.com/api/v1/d/upload` 上报。
- 该库**未做字符串加密**（`.rodata` 可打印字符 60.9%、熵 5.29），因此清单来自 ELF 符号表 + `.rodata` + 反汇编的静态导出，不需要 VM 级 lift。

native 侧检测实现与判定：

| 方法 | 地址 | 判定 |
| --- | --- | --- |
| `isRoot` | `0x320b4` | 遍历 14 项 `char[14][100]` 表（`0x793cd`，步长 `0x64`），拼 `"su"` 后 `fopen`；成功即 root |
| `isPtrace` | `0x32360` → `0x19498` | `getpid` → `sprintf("proc/%d/status")` → `fgets` 逐行 `strncmp("TracerPid",9)` 后 `%lld` 解析 |
| `mapsInfo` | `0x32174` | 调 `0x19020` 取 3 字节结构，`sprintf("%d%d%d", b[2],b[1],b[0])` 逆序回传 |
| `getProcessName` | `0x31f80` | 读取进程名，参与模拟器判定 |

`isRoot` 的 14 条 su 路径：`/data/local/`、`/data/local/bin/`、`/data/local/xbin/`、`/sbin/`、`/su/bin/`、`/system/bin/`、`/system/bin/.ext/`、`/system/bin/failsafe/`、`/system/sd/xbin/`、`/system/usr/we-need-root/`、`/system/xbin/`、`/cache/`、`/data/`、`/dev/`。

对抗面边界：全库 `prctl` 仅 1 处（`PR_SET_NAME`=`"fpthread"`），3 处 `syscall` 全为分配器内 `futex`；**无** Frida 字符串扫描、无 `PTRACE_TRACEME` 自陷、无 `dlopen`/`dlsym`。

Java 层可见的检测点（硬检测主要在 native）：

| 检测 | 实现 | 证据 |
| --- | --- | --- |
| Xposed | `o1b.c`（XposedChecker）用系统 ClassLoader 加载 `de.robv.android.xposed.XposedHelpers` / `XposedBridge`，类名以 byte 数组藏在 `a.a.a.a.a.c` | 调用方**已定位**：native 导出 `existXposed` @ `0x318f8` 经 `FindClass`+`GetMethodID("existXposed","()Z")` 反射调用 `com.xingin.u.p.c` |
| Root 路径 | `io.sentry.core.k0`（定制 Sentry）扫 11 个 su 路径写入崩溃事件 `isRooted`；`aqc.l` 另有 8 路径数组（含 Superuser.apk / daemonsu） | 源码 |
| 多开/多用户 | `os.r0`（"MultiUserManager"）反射 `UserHandle.myUserId()`，塞入推送 extras `sysUserId` 上报 | 源码 |
| Frida / SandHook / LSPosed / Zygisk | Java 层 **0 命中** | 全部在 native |

**该项已闭环**：native 侧采集点的逐条清单见 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md)。该库未做字符串加密，静态即可读出全部符号与字段名，无需 VM 级 lift。

**父消息封装边界**：8 个子消息通过 `0x33990` 的通用序列化分发器封装；该分发器从运行时 type-info 间接取得父层标签，而 51 个叶子字段的编号均直接由各自序列化器的 `mov w0,#FIELD_NUMBER` 给出。公开协议因此以 51 个叶子字段为精确 schema，父层信封按该通用分发器的数据结构描述，不把运行时标签表误写成静态常量。

## 4. JS 指纹子系统

`XhsJsService` / `XhsJsJobService`（`com.xingin.a.a.f`）在 manifest 中均声明 `enabled=true`，并固定运行于 `:jsfp`；但 21 个 DEX 的全量类/字段/方法引用扫描显示：

- `a.a.a.a.a.p.a.f1105k` / `f1106l` 全库零写入，只有 `classes.dex` 的读取；
- 合成 getter `a()` / `b()` 没有外部调用者；
- 除 `classes5.dex` 的声明外，没有 Java 代码引用两个服务，也没有服务类名字符串；
- `fpjs2.min.js` 只作为 Java 字符串出现，没有资产、文件体、下载器或解密脚本。

因此，独立 WebView 服务是**静态不可达件**，不能据 manifest 声明推断它会在本样本运行。可达的 JS 指纹边界只包括：

1. Java 解密侧 `com.xingin.u.p.c.getJsFingerprint()`；
2. Java 写入桥 `a.a.a.a.a.p.a$b.writeJsFp(String)`：
   - `writeJsFp(str)` → **AES/CBC/PKCS5Padding 硬编码密钥**加密后写 SharedPreferences `f/jsf`，记时间戳 `jsfsts`，随后**删除 JS 文件**并广播自杀（`XhsJsService.stop_myself`）；
   - `writeData(str)` → 写 SP `jscomponents/jscomponentskey`。
3. JobService 分支仅保留 `jsfscapability` 标记与广播字符串，没有可达的服务启动链。

硬编码密钥在 `p.a` / `pt.c` 重复出现，长为 16 字节（AES-128），IV 亦为 16 字节；两类敏感字符串（`AES/CBC/PKCS5Padding`、SP 键名）均以 byte 数组藏在 `a.a.a.a.a.c`。**读写两侧与完整密钥/IV 定位过程见 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md) §6.3**——解密侧即 native 导出 `getJsFingerPrint` @ `0x31d1c` 反射调用的 `com.xingin.u.p.c.getJsFingerprint()`。

**边界**：这些是**样本内硬编码密钥**，属于混淆/本地存储保护，不是设备绑定密钥。本文不复现密钥取值。

**结论**：`fpjs2.min.js` 不是样本内资产，也不存在把文件体送入 WebView 的可达路径；本报告只分析样本内真实存在的加解密、缓存和调度声明，不把不可达服务写成已运行的风控逻辑。

## 5. 人机验证（两套）

**a) Walify（wall + verify，自研，RN 驱动）** — `com.xingin.tiny.walify`：

- `BaseCaptchaLayout` / `WebCaptchaLayout`（布局 `walify_captcha_webview`），注册表 `zya.f`。
- RN 桥 `WalifyReactJSBridgeModule`：`getDeviceInfoForWalify`、`launchBioAuth`（生物认证）、`requestPhone` / `requestPhoneInfo`（一键登录号码预取）。
- 场景：登录 / 一键登录反垃圾（antispam）链路。

**b) ValidateActivity（服务端风控拦截页）**：

- 命中风控后被唤起为全屏 WebView，UA 追加 `" XHS/3.0.0 NetType/" + i0.d()`。
- JS 桥 `_xydiscover` / `jsCallApp` 回传结果（`jsCallApp` 有 1/2/3 参数三个重载）。
- 配套：`DialogProxyActivity`、`MaintainTipActivity`、`RestrictAccessActivity`（账号限制页）。
- 页面 URL 来自服务端下发；样本中**不存在** `/api/.../captcha_link` 字面端点（已按此检索确认）。

## 6. 风控 API 端点

`com.xingin.account.net.api.IRiskService`：

| 方法 | 路径 | 参数 |
| --- | --- | --- |
| POST | `/api/sns/v1/system/ares/device/violation/query` | `@c("source")` |
| GET | `/api/sns/v1/account/phone-binding-dialog` | — |
| GET | `/api/sns/v2/user/account_info/anomalies` | — |
| POST | `/api/sns/v2/user/account_info/anomalies/confirm` | — |
| GET | `/api/sns/v1/account/intervention` | `@t("business_code")`, `@t("user_id")` |
| POST | `/api/security/antispam/v1/restriction/self-resolve` | — |

`IDeviceService`：`api/sns/v1/user/login/devices/history`、`api/sns/v1/user/login/sid_reason`、`api/sns/v1/user/login/devices/remove/history`（注意路径字符串**无前导斜杠**）。

账号侧实体：`RiskViolationBean`、`RiskAnomalyAccountBean`、`AccountInterventionBean`、`RiskFrozen`、`UnfreezeResponseData`、`PhoneBindAlertConfigBean`、`DeviceOfflineReason`。
服务端风控引擎代号 **ares**（出现在端点路径中）。

## 7. 人脸核身（eKYC）

链路：`IdentityEssentialEditActivity`（`mBtnFaceVerify`）/ 分步实名流程（`nameAtomization` 包：`RealNameFlowConfig`、`ThemeConfig`，接口 `IIdentityService`）→ 腾讯慧眼 WBCF + `turingcam` + 优图活体 + SM2（国密）。`libturingmfa.so` 提供 TuringFD 设备风险/指纹与 DeviceToken 协同（`JNI_OnLoad` @ `0x1fcc4`，表 `0x56540` / `0x56690`，14 + 1 个动态注册方法）。

**`libturingmfa.so` 的采集面（解出）**：该库把方法名与采集路径**逐字节加密**在 `.data` 字符串表里，
由加载期构造子 `0x34d74` 原地解密（密钥调度 `key_index = src_index mod 8`，348/348 条目验证）。
已还原 **348 个非空条目 / 325 条可打印明文**（源列表共 **416** 项，含 68 个不可见空串），采集面因此可以直接列出（完整清单见
[tiny-and-app-sweep.md](tiny-and-app-sweep.md) §1.7(f)）：

| 类别 | 条数 | 代表条目 |
| --- | ---: | --- |
| OAID / 厂商设备 ID（跨 10 余家厂商 AIDL） | 37 | `com.uodis.opendevice.aidl.OpenDeviceIdentifierService`（华为）、`com.hihonor.cloudservice.oaid.IOAIDService`（荣耀）、`com.samsung.android.deviceidservice.IDeviceIdService`、`com.asus.msa.SupplementaryDID.IDidAidlInterface`、`com.zui.deviceidservice.IDeviceidInterface`（联想）、`com.bun.lib.MsaIdInterface`/`com.mdid.msa`（MSA）、`com.android.id.impl.IdProviderImpl`、`getOAID(Landroid/content/Context;)`、`android_id`/`ANDROID_ID`、`pps_oaid`、`tencent_identifier` |
| 反模拟器 / 环境与完整性 | 57 | `/proc/self/maps`、`/proc/self/mountinfo`、`/proc/self/cgroup`、`/proc/interrupts`、`/proc/net/arp`、`/proc/version`、`/proc/sys/kernel/random/boot_id`、`/sys/bus/virtio`、`/sys/block/mmcblk0/device/cid`、`/sys/class/net/wlan0/address`、`/dev/block/loop`、`/dev/block/dm`、`/system/bin/df`、`init.svc.qemud`/`noxd`/`droid4x`/`vbox86-setup`/`ttVM_x86-setup`、`microvirt.vbox_dpi`、`Hypervisor\|goldfish`、`qemu\|vbox\|eth`、`ro.serialno`/`ro.boot.serialno`/`gsm.serial`、`ro.build.fingerprint`、`/DCIM/.tmfs`、`/.turing.dat` |
| 反射 / Binder 直取（绕公开 API） | 17 | `android/os/ServiceManager`、`ServiceManagerNative`、`com/android/internal/os/BinderInternal`、`android/content/pm/IPackageManager$Stub`、`android/hardware/display/IDisplayManager$Stub`、`android/view/IWindowManager$Stub`、`java/lang/reflect/Proxy` + `newProxyInstance(...)`、`asInterface(...)` ×4、`checkService(...)` |
| 密码学方法名（只持"要调什么"） | 12 | `javax/crypto/Cipher`、`javax/crypto/spec/SecretKeySpec`、`javax/crypto/spec/GCMParameterSpec`、`javax/crypto/spec/IvParameterSpec`、`javax/crypto/Mac`、`AES/GCM/NoPadding`、`HmacSHA256`、`getInstance(...)`、`doFinal([B)[B` |

动态注册方法与上述字符串**互相印证**：`k91_FC6D5B0A7013DB60` 的签名为 `([B)[B`
（对应 `getDFPWup` 的字节变换）、`l91_…` 为 `(InvocationHandler, AtomicReference, ClassLoader)V`
（对应 `Proxy`/`newProxyInstance`，即**运行期动态代理安装器**）。另有
`com/applisto/appcloner/hooking/Hooking` 一条，属**应用克隆（分身）检测**面。

**第三方边界**：腾讯/优图 SDK 为闭源第三方组件，其内部活体算法不在本次范围。

`libturingmfa.so` `.init_array` 其余 8 个构造子已按 `.eh_frame` FDE 的精确函数边界逐条反汇编，并沿内部调用与 `__cxa_atexit` 注册目标继续覆盖 **23 个函数 / 19 个 PLT 导入**：

| 构造子 | 精确执行内容 |
| --- | --- |
| `0xdaf4` | 调用 `0xca40` 初始化三个 `.bss` 槽，随后注册 `0x26908` 为对象 `0x58ce8` 的析构函数，dso handle 为 `0x51aa0` |
| `0x15120` | `getpagesize()` 写 `0x59210`；`mmap(NULL, pagesize, PROT_READ|PROT_WRITE, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0)` 的原始返回值写 `0x59218` |
| `0x2abfc` | `new[0x18]` 指针写 `0x59220`，注册 `0x26908`；初始化 `0x59228` 处递归互斥锁 |
| `0x2c590` | 用 `0x2bfa4` 初始化 `0x59260` 对象并注册 `0x2c01c`；初始化 `0x592c0` 处递归互斥锁并注册 `0x2693c` |
| `0x2e588` | 将 `0x59318`–`0x59328` 的 17 字节对象清零，注册 `0x2d62c` |
| `0x34c34` | 初始化 `0x59330`、`0x59358` 两把递归互斥锁并注册 `0x2693c`；清零并注册 `0x59380`、`0x59398`、`0x593b0` 三个 17 字节对象，析构目标由 `.got 0x51db0` 重定位到 `0x2d62c` |
| `0x4cc1c` | 初始化 `0x59f38` 包装对象，输入对象由 `.got 0x520c8` 重定位到 `0x59318`，析构目标由 `.got 0x51db0` 重定位到 `0x2d62c` |
| `0x4d174` | 精确条件为 `HWCAP bit8 && (property_get("ro.arch") < 1 || strncmp(value, "exynos9810", 10) == 0)`，布尔值写 `.bss 0x59f50` |

该 23 函数闭包的密码学指令命中 **0**，直接写 `.data`/`.data.rel.ro`/`.rodata` 的目标 **0**；除构造子自身和析构回调外，全部动态副作用来自 `mmap`、`atexit`、互斥锁、JNI 局部引用包装和属性读取。真实 Android 运行时只决定分配/映射结果与 `atexit` 是否实际触发，不改变这些函数的静态语义。证据为 `re/tmfa_init_fde.py`、`re/tmfa_init_reach.py` 及其 JSON/反汇编输出。

9 个前导常量单元（`0x569c0`–`0x569e3`）解出 `V Z B C S I J F D`，构造子把它们放入 `.bss` 的 348 槽指针数组前 9 槽。静态代码已确定其内容、槽位和消费者查找结构；业务执行时读取哪一槽属于运行期取值，加密本身已完整还原（见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §1.7）。

### 7.1 TMF/WUP 传输格式与字节变换

TuringFD 的 TMFShark 线程由 `arWPM.d()` 启动，使用默认 URL `https://tdid.m.qq.com/tmf`、10 秒端点选择超时和 `gr.h` 的 POST 传输。POST 请求固定设置 `User-Agent: Turing`、`Accept: */*`、`Accept-Charset: utf-8`、`Content-Type: application/octet-stream`、`Pragma: no-cache`、`Cache-Control: no-cache`、`Connection: close`，不跟随重定向、不读缓存，连接/读取超时均为 15 秒。200 响应读入完整字节流；300/301/302/303/305 只解析 `Location`，其他状态作为错误码返回。伴随的 `fenkF` 是端点探测 GET，头为 `User-Agent: Turing`、`Accept-Charset: utf-8`，同样不缓存。

#### (a) JCE 编码器与 WUP RequestPacket

`Xjpd8` 是逐字段 JCE 编码器：低 4 位为类型，高 4 位为 tag；tag 不小于 15 时先写 `0xF0 | type`，下一字节写完整 tag。类型码为 `0` byte、`1` short、`2` int、`3` long、`4` float、`5` double、`6` 短字符串、`7` 长字符串、`8` map、`9` list/array、`10` struct 起始、`11` struct 结束、`12` null、`13` 带内层类型的 byte array。字符串 `<=255` 字节时写 1 字节长度，否则写 4 字节大端长度；map 写 `size, key(tag 0), value(tag 1)`；struct 以 `type=10, tag=N` 开始、以 `type=11, tag=0` 结束；byte array 写内层类型 `0` 后再写长度。`YunKQ` 按同一类型码严格解码，类型不匹配直接抛错。

`OF1Jz` 的解码异常文本直接给出 `RequestPacket` 类名；类型、tag 和默认值来自该类的读写方法，字段语义按标准 WUP `RequestPacket` 布局命名：

| tag | 类型 | 字段语义 |
| ---: | --- | --- |
| 1 | short | WUP 版本；`AV6dE` 构造和响应解码路径默认写 `3` |
| 2 | byte | packet type |
| 3 | int | message type |
| 4 | int | request id |
| 5 | string | servant name |
| 6 | string | function name |
| 7 | byte[] | JCE 编码的业务 buffer |
| 8 | int | timeout |
| 9 | map<string,string> | context |
| 10 | map<string,string> | status |

`AV6dE.a(name,value)` 把任意受支持值用 UTF-8 JCE 编码成 `Map<String,byte[]>`；`AV6dE.a()` 把该 map 以 tag `0` 写入 RequestPacket tag `7`，再写 RequestPacket 的 1–10 字段，最后在整体前加 4 字节大端总长度。该长度包含长度字段自身。`qtIFA.a()` 的响应方向完全对称：先 `VBlVU.a(payload, VBlVU.a())` 解密，再 `Bp8QH.b()` inflate，跳过 4 字节长度，解码 `OF1Jz`，从 tag `7` 的 map 取键 `resp`，最后按目标 `UMDtK` 类型解出响应对象。

#### (b) TMF 请求外层

实际 TMF POST 不是直接把 `AV6dE.a()` 写入 HTTP。`gr.a.HandlerC0148a.b()` 先把业务对象编码为 `f4Dke`：

- `f4Dke` tag `0/1` 是两个 int 序号，tag `2` 是 `qbihQ` 元数据，tag `3` 是 `kGAMq` 列表。
- `qbihQ` 是 0–9 共 10 个字段：`0/1/5/6/7/8` 为 int，`2/3/4/9` 为 string；该调用点仅写 tag `2` 的 deviceId 和 tag `4` 的 sessionId，递增序号写入 `f4Dke.tag 0`，其余字段保持对象默认值。
- `kGAMq` tag `0/1/2/6` 为 int、tag `3/5` 为 byte[]、tag `4` 为嵌套结构；`0` 写业务命令，`1` 写递增 request id，`3` 写业务字节，其余保持默认。

随后按以下确定顺序变换：业务 `f4Dke` JCE 序列化 → 长度大于 50 字节且 zlib Deflater 结果更短时压缩，并在元数据写入“未压缩”标志位（压缩时该标志缺省）→ 用 16 字节随机密钥经 `VBlVU.b()` 的 XXTEA 类 32 位 Feistel 变换加密 → 外层 JCE 序列化。

外层 wire 的精确字段为：嵌套 tag `0` 结构含 `1:string sessionId`、`2:int uncompressed_flag`、`3:string deviceId`、`4:int symmetric_algorithm`、`5:int request_sequence`、`6:int second_sequence`；结构外为顶层 `1:byte[] RSA_security_context`、`2:byte[] XXTEA_payload`，这些名称同时标明字段序号和构造用途。RSA 上下文由 `RbRz0.a()` 构造：16 字节随机 key、服务器 RSA 公钥 `RSA/ECB/PKCS1Padding` 加密结果和固定业务标识 `EP_TuringMM`，以三个 JCE 字段写入。

`VBlVU.b()` 的编码规则是把明文字节按小端 32 位装入 word 数组，最后一个 word 写原始长度；16 字节随机 key 直接按小端 word 解析，`key.length > 16` 时才先取 MD5，结果补零至至少 4 个 word。每轮使用 `delta=0x9E3779B9`、64 位掩码 `(sum >>> 5) & 3` 和 key 索引 `(i & 3) ^ round_index`，轮数为 `6 + floor(52 / n)`，每轮扫描全部 word 并处理跨界末词。解密函数 `VBlVU.a()` 使用相同 key schedule 逆向该 Feistel 轮函数。`Bp8QH.a/b` 分别是 Java `DeflaterOutputStream`/`InflaterInputStream` 的完整包装，没有自定义压缩格式。

#### (c) 响应结构、状态机与结果选择

HTTP 200 字节先按顶层 `c9YSQ` 解码。状态分支是确定的：解码失败或 `c9YSQ.tag 0` 缺失返回 `-7`；嵌套 `jb1kT.tag 1 == 2` 触发重新生成 RSA 会话并返回 `-9`，`!= 0` 返回 `-12`，只有 `tag 1 == 0` 继续。`c9YSQ.tag 1` 如有内容，直接 JCE 解出 `ZIDl7`：其 tag `0` 非空时，以现有算法、现有 randomKey、新 sessionId 和过期时间替换内存会话，不解密该安全结构。业务体 `c9YSQ.tag 2` 按 `jb1kT.tag 0` 中的 flags 处理：先在 `(flags & 2) == 0` 时用当前会话 randomKey 执行 `VBlVU.a()`，再在 `(flags & 1) == 0` 时 inflate 解密结果。解出的 `LJPko.tag 2` 是 `IEttU` 结果列表，按请求的业务命令 tag `0` 选取唯一项。

`IEttU.tag 3/4` 为状态 int，`tag 5/7` 为业务 byte[]，`tag 6` 为可选结构，`tag 8/9` 为 int/string；`3==0 && 4==0` 走成功分支，其他组合映射到对应负错误码。成功的会话结果写入 `ZY08E`：同一 sessionId 的安全结构按 `compress + XXTEA` 写入私有文件，读取时先按压缩标志解密/inflate；成功响应的 status 字符串 `501` 与毫秒时间戳写入 `XStYH`。这构成请求、刷新、持久化和错误重试的闭环。

#### (d) `getDFPWup` native 入口

JNI 注册表 `.data 0x56540` 的第 10 项就是 `k91_FC6D5B0A7013DB60([B)[B`，目标 `0x21c44`。它要求输入恰好 16 字节；把输入作为密钥，序列化运行期 DFP 状态，调用 `0x1faf8` 做 word 补齐并把原始长度写入末 word，再进入 `0x1f428` 的 XXTEA 类 32 位 Feistel 核心；核心常量为 `0x9E3779B9`，轮数按 `6 + floor(52 / n)` 生成，索引掩码为 `(sum >>> 5) & 3` 与 `(i & 3) ^ round_index`。输出经 `0x27eac` 校验长度后用 JNI `NewByteArray` 原样返回。因此该 native 方法是“DFP 序列化 → 原始长度补齐 → 16 字节 key 的 XXTEA 类变换 → Java byte[]”的固定管线。

## 8. 支付风控

Alipay+ / Antom 收银台安全组件，用于海外卡场景。属第三方 SDK 集成，样本内为 jar/aar 形态。

## 9. 端智能模型

PMML LightGBM 分类模型，随包分发于 `models_root/`（如 `PMML$*.data`、`Segmentation$*.data`），用于用户行为分群（增长/风控辅助）。

## 10. 伴随守护组件（`libtinyd.so`）

完整分析见 [tinyd-companion-daemon.md](tinyd-companion-daemon.md)。摘要：

- **定位**：不是网络组件，也不是加密组件。导入表 38 项中**没有任何** socket/connect/send/recv/dlopen/system/popen/exec/SSL/inet（扫描命中 0）。它唯一的对外通信是**无名管道**。
- **启动时机**：`.init_array` 120 字节**全为 0**（15 项），故**不在加载时自启**；所有动作由 `JNI_OnLoad`（`0xa630`）或 Java 侧显式调用触发。
- **导出面**：仅 `JNI_OnLoad`；无 `Java_*` 导出；`.data.rel.ro`/`.data` 中不存在 `JNINativeMethod` 三元组（下游表项只有 5 个，而 `RegisterNatives` 表至少需 `3×方法数`）→ 注册表在 CFF 内运行时构造。
- **字符串加密**：**已闭式还原**。4 个解码器（`0x7b8c`、`0x14fa0`、`0x1b5e4`、`0x70c8`），算法为"按 `i%20` 查表的 旋转+模加/XOR 逐字节双射"（**不是** XOR 流）。全部 **7 条明文**已解出：`LD_LIBRARY_PATH=`、`State:`、`zygote`、`TracerPid:`、`am`、`setArgV0`、`android/os/Process`。
- **IPC 协议**：`pipe2`（内联 `svc` 59）建管道 → `fork` → **定长 4 字节**信令（`write(fd, &buf, 4)`）+ 写后立即 `close`；读取侧 `__read_chk` 循环到恰好收满 4 字节，`< 1` 置错误标志。**无长度前缀、无类型字段、无魔数**。
- **进程伪装链**：`android/os/Process` + `setArgV0` + `zygote` 构成"把子进程 `argv[0]` 改成 zygote 派生进程"的闭环；叠加 `prctl(PR_SET_NAME)`（`w0=15`，`0x15d0c`/`0x16434`）改写线程名，对 `ps`/`cmdline`/`comm` 三个视图同时生效。
- **反调试**：`openat` → `fdopen("r")` → `fgets` 读取 `/proc/<pid>/status`，比对 `TracerPid:`。
- **退出/替换**：内联 `exit(0)` ×3（`svc` 93，不经 libc `exit`，不跑 `atexit` 链）、`execve` ×1（`svc` 221）、`prctl(PR_SET_NAME)` ×2（`svc` 167）、`nanosleep` ×2（`svc` 101）、`wait4` ×1（`svc` 260）。
- **CFF**：`.data` 分发池 381 槽（`R_AARCH64_RELATIVE`），`br` 直接承载目标。动态跟踪 14 个跳转，**14/14 目标均落在 `.text` 合法块首** → 平坦化不隐藏语义，按 `br` 目标重建后继边即可还原。

**侧重判定**：`fork`/`syslog`/`abort-message` 并非同等权重。实测 `syslog` 只有 **1 个**调用点，而 `fork` 2 个、`close` 7 个、`write` 3 个、`__read_chk` 2 个——**日志是辅助能力**，主功能是管道信令与进程伪装。

**静态注册结论**：`JNI_OnLoad` 的表虽在 CFF 内运行期构造，但 daemon dex 全库只有 `Lcom/xingin/tiny/daemon/d;.a(I,[Ljava/lang/Object;)Ljava/lang/Object;` 一个 `native` 方法，因此注册目标的类名、方法名和签名唯一。**4 字节载荷结论**：写点逐一确认了 `fork` 返回值、镜像字段取负和其他状态字段来源；具体进程每次写入的数值自然是运行期数据，协议的长度、字节序、字段来源、接收者和用途均已完成。

## 11. 边界清单（汇总）

| 项 | 分析结论 | 证据强度 |
| --- | --- | --- |
| Tiny opcode 内部算术 | 调用语义 31/31 定名；分发层结构为 31 操作码 / 61 分派块 / 61 谓词槽 / 61 跳转位移一一对应；域区逐块 lift 给出 435 个 distinct run、154 个域原语和伪代码，九项指纹逐项 EXACT，188 个未执行槽位逐段定性且 0 个含密码学指纹 | 静态全段覆盖 + 全 31 操作码动态执行集；逐输入饱和不是判定算法或控制流所必需的条件（§5.6.8–§5.6.9） |
| Java/dex 侧混淆 | — | **已闭环**：`@u5/@v5` 加密字段名 519/519 闭式还原（100% 合法 Java 标识符）、Java 侧字符串解密器 811/811 调用点全映射（0 未映射）、daemon dex 三层混淆完整审计（§9.7）、11 个反射包装器枚举、类/包名短名经证实为 R8 字典压缩；`Petal` 为插件框架代号而非混淆器（§9） |
| 内嵌 dex 的归属 | — | **已闭环**：`assets/fd2x1e4e2x3f1v2b1s.dex`（78 008 B）是 `libtinyd.so` 的 **Java 侧守护进程**（63 类、12 个 IPC case、`@x0` 116/116、`v.<clinit>` 74 条明文）；`c4d121c215evx1s51d.dex`（940 B）与 588 B 内嵌 dex 均为**单类 `La;` 的 R8 反射蹦床**（4 个方法逐字相同），见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §9.7 |
| Java 侧字符串加密的计量口径 | — | **已重建**：按标识符字面匹配得到的数字随 jadx 树变化（同 dex 三棵树 = 441/674/57）；新口径为**字节码精确**：822 调用点 → 811 内联对 → **811 全映射、0 未映射**，见 §9.2b.1 |
| `libturingmfa.so` 的加密面 | — | **已闭环**：它带一张**加载期原地自解密**的字符串表，前三条判据（加密指令族 / 已知算法常量 / 大整数域特征）均不命中，第 4 条判据（加载期自解密数据表）下**全 164 库只有它命中**：解密器 `0x34d74`（5 207 条指令、**0 调用、0 入边、1 个 `ret`、完全展开**）、密钥调度 `key_index = src_index mod 8`（348/348 条目验证）、**348 个非空条目 / 325 条可打印明文**（另 68 个不可见空串 ⇒ 源列表 **416** 项）。加密普查为 **33/131**，混淆面为 **4 库**，见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §1.6/§1.7 |
| `libturingmfa.so` 其余 8 个构造子 | — | **已闭环（静态语义）**：FDE 精确边界逐条反汇编，并覆盖内部调用与注册析构目标共 23 函数 / 19 导入；逐项给出对象、互斥锁、析构、`mmap`、属性判定与 `.bss` 落点。密码学指令 0、写 `.data`/`.data.rel.ro`/`.rodata` 目标 0；真实 Android 只决定运行期取值（§7） |
| `libturingmfa.so` 9 个前导常量单元 | `0x569c0`–`0x569e3` 解出 `V Z B C S I J F D`，由构造子写入 `.bss` 指针数组前 9 槽 | 无 `R_AARCH64_RELATIVE` 指针直接指向单元，消费者经重建后的数组索引；具体执行选择是运行期取值，不是缺失的代码语义 |
| 加载期自解密表的**全量否定**证据等级 | 163 个库"运行期无改写" | 按三类读：**62 个库无 `.init_array` 条目（结构性排除）**、**11 个库构造子跑完且 `.data`/`.rodata` 逐字节比对（已实测）**、**91 个库构造子无一跑到底（缺真实运行时）**。第三类只能读作"**在我们能执行的范围内无改写**"。另：仪器只覆盖 `.init_array` 路径，挂 `JNI_OnLoad`/业务入口的自解密需更强入口覆盖 |
| `libxyasf.so` 根消息字段号 | 8 个子消息在父消息中的编号 | 编号来自运行时计算的 type-info 表（@ `0x33990`），不在静态数据；**子消息内部 51 字段号已全部取得** |
| `fpjs2.min.js` | 风控 JS 本体 | 服务端下发，样本内不存在 |
| `libtinyd.so` 的 `JNINativeMethod` 表 | — | **已闭环**：不必读 CFF 建表过程，读**被注册者**即可——daemon dex 全库只声明**一个** `native` 方法 `Lcom/xingin/tiny/daemon/d;.a(I[Ljava/lang/Object;)Ljava/lang/Object;`，故该表只能绑定它（类名/方法名/签名三项确定，见 [tinyd-companion-daemon.md](tinyd-companion-daemon.md) §10.3.1） |
| `libtinyd.so` 4 字节载荷语义 | `+0x494` 是由 4 个写点写入的原生小端 `u32` 状态字，来源域为 `fork` 返回值、其镜像字段取负和其他状态字段；写满 4 字节后立即关闭管道，接收端一次性收满 4 字节，语义是子进程就绪/退出/状态通知 | 代码、字节格式、值来源和接收者已确定；每次执行的具体数值由进程状态决定，不是未解析的数据或控制流（[tinyd-companion-daemon.md](tinyd-companion-daemon.md) §3、§10.3） |
| 第三方 SDK 内部 | 慧眼/优图/支付宝内部算法 | 闭源第三方 |
| `x-n0`…`x-r4o` 语义 | 头部名已知；**生成机制已定名**（`0x96f7fcac` 返回 `Map<String,String>`，由 `nlb.p` 逐条写成 header） | 头名出现在 `classes2/15/16/17/20.dex`；取值本身属运行期产物，需真实请求观测 |
| Cookie/session 作用 | **已验证**：API 客户端 `yta.g.c()` 无 `cookieJar(...)`（OkHttp 默认 `NO_COOKIES`）；`cookie` 字样全归属 WebView/RN/第三方 | 构造点与引用面已全量枚举，结构结论直接成立 |
| 服务端风控阈值 | ares 判定阈值 | 服务端逻辑，客户端不可见 |
