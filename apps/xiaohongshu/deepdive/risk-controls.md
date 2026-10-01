# 小红书 9.37.0 风控组件清单

本文按组件逐个给出：角色、证据位置、证据等级、以及**已知的未闭环边界**。不使用“未知算法”这类未定性表述——每个组件要么给出恢复结果，要么明确写出未闭环的具体环节。

## 0. 总览：纵深结构

| 层 | 组件 | 角色 | 证据等级 |
| --- | --- | --- | --- |
| 请求签名 | `libxyass.so`（Shield） | 全 native OkHttp 拦截器，生成 `shield` / `xy-platform-info` | 算法已恢复（见 crypto.md） |
| 请求签名 | `libtiny.so`（Tiny） | opcode 引擎生成 `x-n0/x-o9/x-p0/x-r4/x-r4o` | 结构已证实 |
| 设备指纹 | `libxyasf.so`（xya FP SDK） | 70+ 采集点，自行 HTTP 上报 | 结构已证实 |
| JS 指纹 | 隐藏 WebView + 服务端下发 JS | 独立进程跑风控 JS | 已验证（硬编码密钥已定位） |
| 人机验证 | Walify（RN）+ ValidateActivity（H5） | 命中风控后的验证 | 已验证（触发链 + URL 来源边界） |
| 人脸核身 | 腾讯慧眼 WBCF + turingcam + 优图 + SM2 | 实名场景 | 结构已证实 |
| 支付风控 | Alipay+ / Antom 收银台组件 | 海外卡 | 结构已证实 |
| 端智能 | PMML LightGBM 模型 | 用户行为分群 | 结构已证实 |
| 伴随守护 | `libtinyd.so` | fork/syslog/abort 特征 | 结构已证实 |

对抗特征：OLLVM 字符串加密、CFF 控制流平坦化、注解驱动方法名加密（代号 Petal）、native 数字 opcode 分发、自定义 XOR 字节串解密。

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
- `JNI_OnLoad` @ `0x18afd8`；opcode 二叉比较点 @ `0x16b08c` / `0x17cdb0`。

**未闭环**：opcode 编号到算法语义的映射未逐条还原（引擎用 int32 操作码而非方法名）。已确认的是分发机制与覆盖范围。

## 3. 设备指纹：`libxyasf.so` 与 Java 调度层

- 调度中枢 `pt.a`（混淆名，classes5.dex）；辅助 `qt.a`（"virposd"，uid→`u0_a%d` 换算，解析 `/proc` 判断运行身份）。
- 采集点 70+，root/模拟器/Xposed/VirtualApp/ptrace 检测均在 native 完成，结果自行 HTTP 上报。

Java 层可见的检测点（硬检测主要在 native）：

| 检测 | 实现 | 证据 |
| --- | --- | --- |
| Xposed | `o1b.c`（XposedChecker）用系统 ClassLoader 加载 `de.robv.android.xposed.XposedHelpers` / `XposedBridge`，类名以 byte 数组藏在 `a.a.a.a.a.c` | Java 源码无调用方 ⇒ native 反射调用 |
| Root 路径 | `io.sentry.core.k0`（定制 Sentry）扫 11 个 su 路径写入崩溃事件 `isRooted`；`aqc.l` 另有 8 路径数组（含 Superuser.apk / daemonsu） | 源码 |
| 多开/多用户 | `os.r0`（"MultiUserManager"）反射 `UserHandle.myUserId()`，塞入推送 extras `sysUserId` 上报 | 源码 |
| Frida / SandHook / LSPosed / Zygisk | Java 层 **0 命中** | 全部在 native |

**未闭环**：native 侧 70+ 采集点的逐条清单（需对 `libxyasf.so` 做与 xyass 同级别的 lift，本次未做）。

## 4. JS 指纹子系统

`XhsJsService` / `XhsJsJobService`（`com.xingin.a.a.f`）：

1. 独立进程创建不可见 WebView（`a.a.a.a.a.p.a`），`WebView.setDataDirectorySuffix("app_webview" + 进程名)`。
2. 加载本地 HTML，`shouldInterceptRequest` 拦截并替换服务端下发的 JS（`fpjs2.min.js`）。
3. JS 桥 `"android"` 两个回写口：
   - `writeJsFp(str)` → **AES/CBC/PKCS5Padding 硬编码密钥**加密后写 SharedPreferences `f/jsf`，记时间戳 `jsfsts`，随后**删除 JS 文件**并广播自杀（`XhsJsService.stop_myself`）；
   - `writeData(str)` → 写 SP `jscomponents/jscomponentskey`。
4. JobService 版置 `jsfscapability` 标记。

硬编码密钥在 `p.a` / `pt.c` 重复出现，长为 16 字节（AES-128），IV 亦为 16 字节；两类敏感字符串（`AES/CBC/PKCS5Padding`、SP 键名）均以 byte 数组藏在 `a.a.a.a.a.c`。

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

**未闭环**：腾讯/优图 SDK 为闭源第三方组件，其内部活体算法不在本次范围。

## 8. 支付风控

Alipay+ / Antom 收银台安全组件，用于海外卡场景。属第三方 SDK 集成，样本内为 jar/aar 形态。

## 9. 端智能模型

PMML LightGBM 分类模型，随包分发于 `models_root/`（如 `PMML$*.data`、`Segmentation$*.data`），用于用户行为分群（增长/风控辅助）。

## 10. 伴随守护组件（`libtinyd.so`）

fork / syslog / abort-message 特征支持“独立守护进程”判断：主进程被终止时该组件仍存活并负责上报。

**未闭环**：`libtinyd.so` 与主进程的 IPC 协议未还原。

## 11. 未闭环清单（汇总）

| 项 | 未闭环的具体环节 | 原因 |
| --- | --- | --- |
| Tiny opcode 语义 | int32 操作码 → 算法映射 | 引擎为独立 VM，需逐 opcode lift |
| `libxyasf.so` 采集点 | 70+ 采集点的逐条字段与判定 | 需与 xyass 同级别 lift |
| `fpjs2.min.js` | 风控 JS 本体 | 服务端下发，样本内不存在 |
| `libtinyd.so` IPC | 与主进程的通道协议 | 未做动态跟踪 |
| 第三方 SDK 内部 | 慧眼/优图/支付宝内部算法 | 闭源第三方 |
| `x-n0`…`x-r4o` 语义 | 头部名已知，取值语义未反推 | 需 Tiny opcode lift |
| Cookie/session 作用 | 静态检索 0 命中，未运行时验证 | 需真实环境抓包 |
| 服务端风控阈值 | ares 判定阈值 | 服务端逻辑，客户端不可见 |
