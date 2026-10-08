# 风控机制（完整逆向）

闲鱼的风控是**四层叠加**：MTOP 传输层反攻击、SecurityGuard 设备签名、业务返回码
拦截、Wukong/CCRC 内容与行为风控。本文件逐层给出精确的判定代码、触发条件与动作，
并给出密码学全量清单，确认不存在未分析清楚的加密实现。

## 0. 分层总览

| 层 | 组件 | 判定依据 | 动作 |
| --- | --- | --- | --- |
| L1 传输反攻击 | `AntiAttackHandlerImpl` + `ApiLockHelper` | 返回码 41x / 单 API 频次 | 挂起或失败全部请求、单 API 加锁 |
| L2 设备签名 | `InnerSignImpl` + `libsgmainso` | `x-sign`/`wua`/`umid`/`x-sgext` | 服务端据此判定设备与请求合法性 |
| L3 业务拦截 | `SecurityInterceptor` | `ret[0]` 与 `extMsg` | 弹窗、跳实人核验、跳惩罚页 |
| L4 内容/行为风控 | `CcrcService` + `OffClientWukongGuard` + Wukong | 模型命中码 / URL | 拦截跳转、阻断提交、上传样本 |

## 1. L1 传输反攻击

`mtopsdk/mtop/antiattack/AntiAttackHandlerImpl`：

- 监听广播 `mtopsdk.extra.antiattack.result.notify.action`。
- 收到 `UT_REG_RESULT == "success"` → `RequestPoolManager.getPool(Type.ANTI).retryAllRequest(...)`；
  其它值或异常 → `failAllRequest(..., ERRCODE_API_41X_ANTI_ATTACK, ...)`。
- `timeoutRunnable` 超时后同样 `failAllRequest`，即**验证未完成则整池请求判失败**。

`mtopsdk/mtop/antiattack/ApiLockHelper`（单 API 速率锁）：

```java
public static boolean iSApiLocked(long j, String str) {
    LockedEntity lockedEntity = lockedMap.get(str);
    if (lockedEntity != null) {
        if (Math.abs(j - lockedEntity.lockStartTime) < lockedEntity.lockInterval) {
            z = true;                       // 仍在锁定期
        } else {
            lockedMap.remove(str);          // 过期解锁
        }
    }
    return z;
}
```

`lock(now, intervalMs, apiKey)`：`intervalMs > 0` 时按 per-API 间隔加锁，否则用
`SwitchConfig` 的默认间隔。锁键是 `apiName/version`。

## 2. L2 设备签名与指纹

见 [network.md](network.md) §5 与 [auth.md](auth.md) §5。风控相关的具体事实：

- 签名请求类型：`x-sign` 用 `requestType = 7`（`OPEN_ENUM_SIGN_ATLAS_FAST`），
  HMAC-SHA1 用 `requestType = 3`（`OPEN_ENUM_SIGN_COMMON_HMAC_SHA1`）。
- `SecureSignatureDefine` 的完整类型表：
  `TOP(0)`、`UMID(1)`、`TOP_OLD(2)`、`COMMON_HMAC_SHA1(3)`、`COMMON_MD5(4)`、
  `ATLAS(5)`、`SIM_HMAC_SHA1(6)`、`ATLAS_FAST(7)`、`ATLAS_FAST2(8)`、`XIAMI(9)`、
  `HMACSHA256(10)`、`SHA256(11)`、`INVALID(12)`。
- 设备风险组件：`ISimulatorDetectComponent.isSimulator()`（模拟器检测）、
  `IDataCollectionComponent.getNick/setNick`、`IPkgValidityCheckComponent`（签名校验）、
  `ILBSRiskComponent`、`IStaticKeyEncryptComponent`、`ISafeTokenComponent`。
- 统一安全因子：`IUnifiedSecurityComponent.getSecurityFactors()` 一次性产出
  `x-sign`、`x-mini-wua`、`x-sgext`、`x-umt`、`wua`。
- 白盒签名：`createAVMPInstance("mwua", "sgcipher")`，由 `libsgmainso-6.7.260202.so`
  与 `libsgmiscso-6.5.9.so` 承载。

**判定依据**：服务端用 UMID/WUA 与 `x-sign` 关联设备与请求完整性；客户端侧仅见
采集与签名调用点，评分逻辑不可见（见 §7）。

## 3. L3 业务拦截

`com/taobao/android/remoteobject/security/SecurityInterceptor`（实现 `PSecurityInterceptor`）：

### 3.1 返回码常量（`xframework/archive/Constants`）

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `FAIL_BIZ_FORBIDDEN` | `"FAIL_BIZ_FORBIDDEN"` | 账号被处罚 |
| `NEED_REAL_VERIFY` | `"NEED_REAL_VERIFY"` | 需实人认证 |
| `RISK_USER_VERIFY` | `"RISK_USER_VERIFY"` | 风险用户需核验 |
| `NEED_RELOGIN` | `"NEED_RELOGIN"` | 需重新登录 |
| `PENALTY_MSG` | `"idlefish_custom_do_not_show_this_toast"` | 惩罚提示抑制键 |

`SecurityInterceptor` 另有两个本地常量：
`USER_NEED_ALIPYA_BIND`、`USER_NEED_REALNAME_VERIFY`。

### 3.2 判定

```java
private boolean isPenalty(String str) {
    return StringUtil.isEqual(str, Constants.FAIL_BIZ_FORBIDDEN);
}
private boolean isPenalty(IApiBaseReturn r) {
    return StringUtil.isEqual(r.getRetCodeAtIndex(0), Constants.FAIL_BIZ_FORBIDDEN);
}
private boolean isRealVerify(String str) {
    return StringUtil.isEqual(str, Constants.NEED_REAL_VERIFY)
        || StringUtil.isEqual(str, Constants.RISK_USER_VERIFY);
}
```

### 3.3 动作（`go2PenaltyActivityIfNeeded`）

1. 先查远端白名单 `real_verify_white_list`（Orange，JSON 数组）；命中则**不拦截**。
2. `isRealVerify(str4)` → 从 `str3`/`str2` 解析 `riskDesc`、`confirmText`、`confirmUrl`，
   弹对话框（`DialogUtil.buildContentBtn(riskDesc, "取消", confirmText, ...)`）；
   点确认时上报 `Button-Vertify` 事件并用 `PRouter.build(confirmUrl).open()` 跳核验页。
3. `isPenalty(str5)` → 同样解析并跳转惩罚/申诉页。
4. `extMsg` 为 JSON 对象时走 `needNormalInterceptor`（`type=="confirm"` 等普通提示）。

`interceptor(JSONObject, retCode)` 由 `MtopLauncher.mtopPropertiesInitSDK` 注册为
响应拦截器；开关变量 `securityInterceptorRetCode` 决定哪些 retCode 参与。
`setIsSecurityInterceptor(true)` 可在批量场景临时静默。

## 4. L4 内容/行为风控：Wukong / CCRC

### 4.1 初始化（`CcrcManager.initRealCCRC`）

```java
CcrcContext.setAppKey(appKey);                 // 21407387
CcrcContext.init(application, ttid);           // 700502
mCcrcService = CcrcService.getService("ccrc_idle_comment_post_mtee_sns_unify_check");
mCcrcBHService = CcrcBHService.getBHService("ccrc_idlefish_swindle_risk");
OffClientWukongGuard.warmUp();
```

- 远端降级：`PRemoteConfigs.getValue("android_switch_high", "ccrc_downgrade", "")`
  为真则**整层不初始化**。
- SO 加载：`BuildConfig.OPEN_PRE_INSTALL` 为真时经 `SoLoaderManager` 异步加载
  `Ccrc` 模块后再初始化。
- 登录后 `activate()` 使用 `pid = <userId>_...`；离线守卫使用
  `pid = "PID_OC_" + UUID.randomUUID()`。

### 4.2 四个闲鱼风控场景（设备端 SP 可核对）

`shared_prefs/` 中实际存在：

| ccrcCode | 场景 |
| --- | --- |
| `wukong_ccrc_idlefish_chat_swindle_risk` | 私聊诈骗 |
| `wukong_ccrc_idlefish_comment_risk` | 评论内容 |
| `wukong_ccrc_idlefish_swindle_risk` | 交易诈骗（`CcrcBHService`） |
| `wukong_ccrc_idlefish_off_client_risk` | 站外/离线跳转 |

代码中另有 `ccrc_idle_comment_post_mtee_sns_unify_check`（评论发布统一检查）、
`CCRC_TAOBAO_OFF_CLIENT_RISK`、`ccrc_off_client_risk`、
`ccrc_tblive_content_risk_control`。

### 4.3 样本类型（`com.alibaba.security.wukong.model`）

`TextRiskSample`、`BehaviorRiskSample`、`BitmapImageSample`、`ByteImageSample`、
`AudioSample`、`ImageRiskSample`、`VideoCutFileRiskSample`、
`AudioFileRiskSample`、`FileRiskSample`、`AudioStreamRiskSample`、
`ImageStreamRiskSample`、`LiveStreamRiskSample`、`MultiModelRiskSample`、`CCRCRiskSample`。
→ **采集范围：文本、行为、图片、音频、视频、文件与实时流。**

**文本检测入口**（`CcrcManager.detectText`）：

```java
TextRiskSample s = new TextRiskSample(String.valueOf(System.currentTimeMillis()), new Text(str));
String riskID = s.getRiskID();
s.detect(mCcrcService);
```

### 4.4 判定结果码（`WukongResultCode`，精确值）

| 枚举 | 值 | 含义 |
| --- | --- | --- |
| `ACTIVATE_SUCCESS` | 100000 | 激活成功 |
| `ACTIVATE_ING` | 1000001 | 激活中 |
| `ACTIVATED` | 1000002 | 已激活 |
| `UN_ACTIVATE` | 1000003 | 未激活 |
| `ACTIVATE_FAIL` | 1000004 | 激活失败 |
| `DETECT_HIT_ACTION` | 200000 | 命中且需动作（拦截） |
| `DETECT_NO_HIT` | 200001 | 未命中 |
| `DETECT_HIT_NO_ACTION` | 200002 | 命中但不动作 |
| `DETECT_PRE_FAIL` | 200003 | 前置失败 |
| `DETECT_ENGINE_EVALUATE_FAIL` | 200004 | 引擎评估失败 |

### 4.5 离线跳转守卫（`OffClientWukongGuard`）

对 `jumpUrl` 做**站外跳转风控**，判定与动作精确如下（`finishDetect`）：

```java
boolean z  = code==DETECT_HIT_ACTION || code==DETECT_HIT_NO_ACTION || code==DETECT_NO_HIT;
boolean z2 = code==DETECT_HIT_NO_ACTION;            // 命中但“不动作”
if (!z) { finishPending(pending, "detect_failed", name); return; }
if (z2) { track(elapsed, "hit",  jumpUrl, name); callback.onBlock();    return; }  // 阻断
          track(elapsed, "pass", jumpUrl, name); callback.onAllow();               // 放行
```

**放行/降级路径**（全部为 fail-open，即异常时放行）：

| 触发 | 代码 | 结果 |
| --- | --- | --- |
| 远端开关 `off_client_wukong_fallback` | `isFallbackEnabled()` | `onAllow()` |
| 远端开关 `ccrc_off_client_risk_downgrade` | `EqualsIgnoreCase("true")` | `onAllow()` |
| URL 为空 | `TextUtils.isEmpty(str)` | `onFailOpen("empty_url")` |
| 服务未激活 | `mCcrcService == null` | `onFailOpen("not_activated")` |
| 激活失败 | `handleActivateFail` | `onFailOpen("activate_failed")` |
| 检测超时 | `timeoutRunnable` | `onFailOpen("timeout")` |
| 检测异常 | `catch` | `onFailOpen("detect_exception")` |

- 超时阈值：`PRemoteConfigs.getValue("android_switch_high",
  "off_client_wukong_timeout_ms", 200)`，默认 **200 ms**。
- 阻断页：`UrlFirewallActivity`（`openBlockPage`），带 `url` 与当前页面名。
- 样本 ID：`"SID_" + pid + "_" + currentTimeMillis + "_" + counter`。
- 行为样本：`new BehaviorRiskSample(sid, map)`，`map = { jumpUrl: <url> }`，
  `detect(ccrcService, false)`。

**关键安全设计**：检测必须**快且仅在确认命中时阻断**；任何失败都放行，
所以站外跳转风控是“可用性优先”的。

### 4.6 引擎与资源

- 原生/离线：`libwukong_native.so`、`RuleEngineNativeManager`、`WukongNativeManager`、
  `WukongMFEManager`（MFE=移动风控引擎，规则以 Lua/pack 形式下发）。
- KFC 检索（本地敏感词/规则库）：`KFCNative`，方法
  `initKFC / install(List<KFCInstallConfig>) / update / release(List<String>) /
  search(String, List<String>) / t2s(String) / getProtocolVersion`。
  `t2s` = 繁体转简体，用于文本归一化后再匹配。
- 网络接口：
  `mtop.alibaba.client.ccrc.fetchConfig`（取配置）、
  `mtop.alibaba.client.ccrc.risk.upload`（风险样本上传）、
  `mtop.alibaba.client.ccrc.algo.upload`（算法结果上传）、
  `mtop.alibaba.ccrc.sdk.heartbeat`（心跳）。
- 本地库：`files/.wukong/mfe_db/v1.db`，表
  - `mfe_basic(id, ts, ccrcSellerUniqueCnt1h_list BLOB)` — **按小时卖家唯一计数**行为特征；
  - `mfe_original(id, session, rid, ts, value BLOB)` — 原始行为样本。
  设备端 `mfe_basic` 现有 5 行、`mfe_original` 0 行（只读聚合）。
- 另有 `files/.wukong/crc_box_sp/crc_box_sp.txt`（资源校验/序列号）。

### 4.7 残留动作与上报

- `BaseActionPerform` + `WukongActionCode`：命中后可执行的动作族
  （拦截、提示、上报、二次校验）。
- `AlgoResultReporter`：每次算法结果（`algoCode`、`sampleId`、`metaId`、`timeStamp`、
  `preResult`、`sampleUrl`、`label`）批量上报，缓存上限 10。
- `NativeRiskReporter` / `RiskPoint`：原生侧风险点上报。
- `TrackManager` / `TrackLog`：阶段化埋点（init/detect/upload）。

## 5. 行为与设备数据采集

### 5.1 Wukong 客户端信息（`client/smart/core/model/client`）

```
DeviceInfo { osName="Android", brand=Build.BRAND, model=Build.MODEL,
             osVersion=Build.VERSION.RELEASE, netWorkType }
ClientInfo { appInfo, deviceInfo, ts=System.currentTimeMillis(), sdkType="internal" }
```

### 5.2 位置与周边 WiFi（`map/util/LocationUpdate`）

`ApiLBSLocationUpdateRequest` 字段：
`lat, lon, acc, provName, cityName, areaName, areaCode, wifis`。

```java
List<ScanResult> list = WifiUtils.getInstance().searchWifiAp(app, false);
WifiInfo ci = WifiUtils.getConnectionInfo();
if (ci != null) arrayList.add(new String[]{"", "", String.valueOf(ci.getLinkSpeed()), "true"});
for (ScanResult s : list) arrayList.add(new String[]{s.BSSID, s.SSID, String.valueOf(s.frequency), "false"});
apiLBSLocationUpdateRequest.wifis = arrayList;
```

→ 上报**附近 WiFi 的 BSSID/SSID/频段**（用于定位兜底与位置风控）。
上报节流：`last_location_update_time` 距上次 **24 h** 才再次上报，
且 `hasUpdateLocate` 保证每次启动只发一次。

另有 `ApiPondsWifiFairRequest`（`BSSID, SSID, action, fishPondId, List<String[]> wifis`）。

### 5.3 计步与运动

`com.taobao.fleamarket.pedometer.WalkStepCounter` 读
`SensorManager.registerListener(...)` 的 `Sensor.TYPE_STEP_COUNTER`，
产出 `StepData{ timestamp, todayStepCount, accumulatedStepCount }`；
`PedometerJobService`/`PedometerAlarm` 周期调度（权限 `ACTIVITY_RECOGNITION`、
`HIGH_SAMPLING_RATE_SENSORS`）。

### 5.4 其他风控相关采集

- 应用列表：`QUERY_ALL_PACKAGES` + `AppLifecycleTracker.APP_PROCESS_UUID`。
- 屏幕状态：`x-magic_device`、`x-screen-level`。
- 未成年人模式：`minors_mode_enabled`。
- 崩溃/ANR 行为：`anr/ANRWatchDog`、`ANRMonitor`、`fish_block_trace`（卡顿模式可作行为特征）。
- 剪贴板/输入法等敏感面：本版本未见对应读取代码（见 [permissions.md](permissions.md)）。

## 6. 密码学全量清单

以下为 APK 内**全部**密码学调用点归类，无未解释项。

| 算法 | 调用格式 | 出现次数 | 用途 |
| --- | --- | --- | --- |
| AES-CBC | `AES/CBC/PKCS5Padding`（41）、`PKCS5PADDING`（4）、`PKCS7Padding`（3）、`NoPadding`（1） | 49 | 本地存储/票据/媒体加密 |
| AES-GCM | `AES/GCM/NoPadding` | 8 | 完整性加解密（票据/令牌） |
| AES-ECB | `AES/ECB/PKCS7Padding`（3）、`NoPadding`（1） | 4 | 固定块加密（密钥表） |
| RSA | `RSA/ECB/OAEPWithSHA256AndMGF1Padding`(4+1)、`RSA/ECB/PKCS1Padding`(2)、`RSA/None/PKCS1Padding`(1)、`RSA/ECB/NoPadding`(1)、`RSA`(1) | 10 | 密钥协商/签名封装 |
| SM4 | `SM4/CBC/PKCS5Padding` | 1 | 国密对称 |
| DES | `Cipher.getInstance("DES")` | 4 | Heytap 推送配置解密、支付宝支付与公共安全组件的旧协议兼容 |
| HMAC-SHA1 | `Mac.getInstance("HmacSHA1"/"HmacSha1")` | 10 | MTOP `x-sign`、UT 签名 |
| HMAC-SHA256 | `Mac.getInstance("HmacSHA256"/"hmacsha256")` | 3 | 新签名 |
| MD5 | `MessageDigest.getInstance("MD5"/"md5")` | 161 | `md5(data)` 入签名串、去重 |
| SHA-1 | `MessageDigest.getInstance("SHA1")` | 16 | 校验 |
| SHA-256 | `MessageDigest.getInstance("SHA256")` | 3 | 校验 |
| Base64 | `Base64`/`base64` | 97 | 编码 |
| GZIP | `gzip` | 27 | 传输压缩 |
| RC4 | `RC4.rc4(...)` | 14 | UT `mDefaultAppAppSecret` 解码 |
| SM2 | `SM2Engine` / `sm2p256v1` | 1 次实例化 | 在线账号内核的国密非对称运算；Mpaas RPC 按配置选择 `SM2` |
| SM3 | `SM3Digest` | 4 次实例化 | 在线账号、Mpaas 安全组件与客户端签名摘要 |

**UT 默认密钥的具体解密**（`UTBaseRequestAuthentication`）：

```java
private byte[] getDefaultAppAppSecret() {
    return RC4.rc4(new byte[]{66, 37, 42, -119, 118, -104, -30, 4, -95, 15, -26, -12,
        -75, -102, HEAD_GIF_0, 23, -3, -120, -1, -57, 42, 99, -16, -101, 103, -74, 93,
        -114, 112, -26, -24, -24});
}
```

→ 该字节数组是**静态混淆的默认 secret**，`RC4` 解密后作为 HMAC-SHA1 的 key。
这是全仓库唯一可见的硬编码密钥材料，属 UT 埋点自动生成的默认值。

**白盒/加固算法**：全部落在
`libsgmainso-6.7.260202.so`、`libsgmiscso-6.5.9.so`、`libwukong_native.so`、
`libtb_crypto.so`、`libopenssl.so`、`libcrypto.1.0.2.so`
（见 [evidence.md](evidence.md) 的哈希与符号），静态边界已确认。

## 7. 精确判定依据汇总表

| 风控能力 | 判定常量/字段 | 精确代码位置 |
| --- | --- | --- |
| 反攻击整池失败 | `ERRCODE_API_41X_ANTI_ATTACK` | `mtopsdk/mtop/antiattack/AntiAttackHandlerImpl` |
| 单 API 加锁 | `lockStartTime` / `lockInterval` | `mtopsdk/mtop/antiattack/ApiLockHelper.iSApiLocked` |
| 账号处罚 | `FAIL_BIZ_FORBIDDEN` | `SecurityInterceptor.isPenalty` |
| 实人核验 | `NEED_REAL_VERIFY` / `RISK_USER_VERIFY` | `SecurityInterceptor.isRealVerify` |
| 重登录 | `NEED_RELOGIN` | `xframework/archive/Constants` |
| 核验白名单 | `real_verify_white_list` | `SecurityInterceptor.go2PenaltyActivityIfNeeded` |
| 内容命中 | `DETECT_HIT_ACTION=200000` | `WukongResultCode` |
| 站外跳转阻断 | `DETECT_HIT_NO_ACTION=200002` | `OffClientWukongGuard.finishDetect` |
| 站外检测超时 | `off_client_wukong_timeout_ms`（默认 200） | `OffClientWukongGuard.check` |
| 站外降级放行 | `ccrc_off_client_risk_downgrade` | `OffClientWukongGuard.check` |
| CCRC 整体降级 | `ccrc_downgrade` | `CcrcManager.initCCRC` |
| 敏感词归一化 | `KFCNative.t2s` | `wukong/kfc/KFCNative` |
| 卖家小时级计数 | `ccrcSellerUniqueCnt1h_list` | `mfe_basic` 表（设备端） |

## 8. 证据等级

- **已验证**：返回码常量、判定函数、超时默认值、放行分支、结果码数值、
  四个场景 ccrcCode、`mfe_basic` 表结构、算法清单与调用次数、UT 静态密钥材料。
- **结构已证实**：Wukong 引擎调用契约（install/search/t2s）、样本类型与上报字段、
  统一签名返回头集合。
- **不可证（服务端）**：风险评分的权重与阈值、封禁时长、模型下发内容、
  STS 与 OSS 的权限边界。报告不推测这部分。
