# 小红书 9.37.0 风控组件清单

本文按组件逐个给出：角色、证据位置、证据等级、以及**已知的未闭环边界**。不使用“未知算法”这类未定性表述——每个组件要么给出恢复结果，要么明确写出未闭环的具体环节。

## 0. 总览：纵深结构

| 层 | 组件 | 角色 | 证据等级 |
| --- | --- | --- | --- |
| 请求签名 | `libxyass.so`（Shield） | 全 native OkHttp 拦截器，生成 `shield` / `xy-platform-info` | 算法已恢复（见 crypto.md） |
| 请求签名 | `libtiny.so`（Tiny） | opcode 引擎生成 `x-n0/x-o9/x-p0/x-r4/x-r4o` | **已恢复**（签名操作码 `0x96f7fcac` 已定名，见 §2） |
| 设备指纹 | `libxyasf.so`（xya FP SDK） | 82 个 JNI 入口、51 个采集字段，自行 HTTP 上报 | **已恢复**（见 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md)） |
| JS 指纹 | 隐藏 WebView + 服务端下发 JS | 独立进程跑风控 JS | 已验证（硬编码密钥已定位） |
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

**仍余边界**：8 个子消息在**父消息**中的字段编号走运行时计算的 type-info 表（分发循环 @ `0x33990` 以 `ldr w10,[x25,x10]` 从类型描述符间接取号），编号不在静态数据里；子消息内部 51 个字段编号已全部取得。

## 4. JS 指纹子系统

`XhsJsService` / `XhsJsJobService`（`com.xingin.a.a.f`）：

1. 独立进程创建不可见 WebView（`a.a.a.a.a.p.a`），`WebView.setDataDirectorySuffix("app_webview" + 进程名)`。
2. 加载本地 HTML，`shouldInterceptRequest` 拦截并替换服务端下发的 JS（`fpjs2.min.js`）。
3. JS 桥 `"android"` 两个回写口：
   - `writeJsFp(str)` → **AES/CBC/PKCS5Padding 硬编码密钥**加密后写 SharedPreferences `f/jsf`，记时间戳 `jsfsts`，随后**删除 JS 文件**并广播自杀（`XhsJsService.stop_myself`）；
   - `writeData(str)` → 写 SP `jscomponents/jscomponentskey`。
4. JobService 版置 `jsfscapability` 标记。

硬编码密钥在 `p.a` / `pt.c` 重复出现，长为 16 字节（AES-128），IV 亦为 16 字节；两类敏感字符串（`AES/CBC/PKCS5Padding`、SP 键名）均以 byte 数组藏在 `a.a.a.a.a.c`。**读写两侧与完整密钥/IV 定位过程见 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md) §6.3**——解密侧即 native 导出 `getJsFingerPrint` @ `0x31d1c` 反射调用的 `com.xingin.u.p.c.getJsFingerprint()`。

**边界**：这些是**样本内硬编码密钥**，属于混淆/本地存储保护，不是设备绑定密钥。本文不复现密钥取值。

**未闭环**：`fpjs2.min.js` 本体是服务端下发内容，样本内不含其算法。

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

**未闭环**：腾讯/优图 SDK 为闭源第三方组件，其内部活体算法不在本次范围。
`libturingmfa.so` 自身的剩余边界是：`.init_array` 其余 8 个构造子（`0xdaf4`/`0x15120`/`0x2abfc`/`0x2c590`/`0x2e588`/`0x34c34`/`0x4cc1c`/`0x4d174`）需真实 Android 运行时才能执行；
9 个前导常量单元（`0x569c0`–`0x569e3`，解出 `V Z B C S I J F D`）无指针引用，其消费方需运行期观测。
这两项均**不是"未知加密"**——加密已完整解出（见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §1.7）。

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

**未闭环**：`JNI_OnLoad` 注册的 `JNINativeMethod` 三元组（表在 CFF 内运行时构造，需进程内插桩）；4 字节载荷的取值语义（协议形状已定，需 Java 侧调用方或运行时观测）。**协议本身不再是"未知"。**

## 11. 边界清单（汇总）

| 项 | 未闭环的具体环节 | 原因 |
| --- | --- | --- |
| Tiny opcode 内部算术 | — | **已闭环**：调用语义 31/31 定名（§5.6）；分发层结构闭环（31 操作码 / 61 分派块 / 61 谓词槽 / 61 跳转位移，§5.6.6–§5.6.7）；**域区逐块算术 lift 已完成**——435 distinct run 全判读、154 域原语 + 伪代码、九项指纹守恒逐项 EXACT、188 未执行槽位逐段定性且 0 个含密码学指纹（§5.6.9）。仍**未做**逐输入饱和实测，引用时须声明证据等级（§5.6.8） |
| Java/dex 侧混淆 | — | **已闭环**：`@u5/@v5` 加密字段名 519/519 闭式还原（100% 合法 Java 标识符）、Java 侧字符串解密器 811/811 调用点全映射（0 未映射）、daemon dex 三层混淆完整审计（§9.7）、11 个反射包装器枚举、类/包名短名经证实为 R8 字典压缩；`Petal` 为插件框架代号而非混淆器（§9） |
| 内嵌 dex 的归属 | — | **已闭环**：`assets/fd2x1e4e2x3f1v2b1s.dex`（78 008 B）是 `libtinyd.so` 的 **Java 侧守护进程**（63 类、12 个 IPC case、`@x0` 116/116、`v.<clinit>` 74 条明文）；`c4d121c215evx1s51d.dex`（940 B）与 588 B 内嵌 dex 均为**单类 `La;` 的 R8 反射蹦床**（4 个方法逐字相同），见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §9.7 |
| Java 侧字符串加密的计量口径 | — | **已重建**：按标识符字面匹配得到的数字随 jadx 树变化（同 dex 三棵树 = 441/674/57）；新口径为**字节码精确**：822 调用点 → 811 内联对 → **811 全映射、0 未映射**，见 §9.2b.1 |
| `libturingmfa.so` 的加密面 | — | **已闭环**：它带一张**加载期原地自解密**的字符串表，前三条判据（加密指令族 / 已知算法常量 / 大整数域特征）均不命中，第 4 条判据（加载期自解密数据表）下**全 164 库只有它命中**：解密器 `0x34d74`（5 207 条指令、**0 调用、0 入边、1 个 `ret`、完全展开**）、密钥调度 `key_index = src_index mod 8`（348/348 条目验证）、**348 个非空条目 / 325 条可打印明文**（另 68 个不可见空串 ⇒ 源列表 **416** 项）。加密普查为 **33/131**，混淆面为 **4 库**，见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §1.6/§1.7 |
| `libturingmfa.so` 其余 8 个构造子 | `.init_array` 的 `0xdaf4`/`0x15120`/`0x2abfc`/`0x2c590`/`0x2e588`/`0x34c34`/`0x4cc1c`/`0x4d174` | 需真实 Android 运行时的 `JNIEnv`、libc 与 `DT_NEEDED` 符号；**只有 `0x34d74`（表解密器）是自足可跑的**。**非"未知加密"** |
| `libturingmfa.so` 9 个前导常量单元 | `0x569c0`–`0x569e3`（解出 `V Z B C S I J F D`） | 无任何 `R_AARCH64_RELATIVE` 指针指向它们，只能看到原地读取；**哪个消费者读哪一个**需运行期观测。**非"未知加密"** |
| 加载期自解密表的**全量否定**证据等级 | 163 个库"运行期无改写" | 按三类读：**62 个库无 `.init_array` 条目（结构性排除）**、**11 个库构造子跑完且 `.data`/`.rodata` 逐字节比对（已实测）**、**91 个库构造子无一跑到底（缺真实运行时）**。第三类只能读作"**在我们能执行的范围内无改写**"。另：仪器只覆盖 `.init_array` 路径，挂 `JNI_OnLoad`/业务入口的自解密需更强入口覆盖 |
| `libxyasf.so` 根消息字段号 | 8 个子消息在父消息中的编号 | 编号来自运行时计算的 type-info 表（@ `0x33990`），不在静态数据；**子消息内部 51 字段号已全部取得** |
| `fpjs2.min.js` | 风控 JS 本体 | 服务端下发，样本内不存在 |
| `libtinyd.so` 的 `JNINativeMethod` 表 | — | **已闭环**：不必读 CFF 建表过程，读**被注册者**即可——daemon dex 全库只声明**一个** `native` 方法 `Lcom/xingin/tiny/daemon/d;.a(I[Ljava/lang/Object;)Ljava/lang/Object;`，故该表只能绑定它（类名/方法名/签名三项确定，见 [tinyd-companion-daemon.md](tinyd-companion-daemon.md) §10.3.1） |
| `libtinyd.so` 4 字节载荷语义 | `+0x494` 各取值含义 | 协议形状已确定（定长 4 字节、写后关）；**对端已找到**（daemon dex 的 `e.main` → `l.a()`，12 个 IPC case，见 [tinyd-companion-daemon.md](tinyd-companion-daemon.md) §10.3），但"哪个值代表哪种状态"仍需运行时观测——这属**运行期取值**，非代码未分析 |
| 第三方 SDK 内部 | 慧眼/优图/支付宝内部算法 | 闭源第三方 |
| `x-n0`…`x-r4o` 语义 | 头部名已知；**生成机制已定名**（`0x96f7fcac` 返回 `Map<String,String>`，由 `nlb.p` 逐条写成 header） | 头名出现在 `classes2/15/16/17/20.dex`；取值本身属运行期产物，需真实请求观测 |
| Cookie/session 作用 | **已验证**：API 客户端 `yta.g.c()` 无 `cookieJar(...)`（OkHttp 默认 `NO_COOKIES`）；`cookie` 字样全归属 WebView/RN/第三方 | 无需运行时验证 |
| 服务端风控阈值 | ares 判定阈值 | 服务端逻辑，客户端不可见 |
