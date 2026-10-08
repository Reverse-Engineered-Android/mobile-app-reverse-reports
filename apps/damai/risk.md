# 风控机制

本文只描述 APK 内可直接验证的客户端机制：检测条件、评分分支、阈值、
网络控制和本地传输字段。没有发送请求，也没有验证服务端是否会拒绝、
封禁、降权或改变票价与票量；凡客户端不可见的服务端动作均明确标注为
不可证。

## 1. 风控总链路

静态调用面可归纳为五层：

1. **运行环境保护**：`RuntimeProtector` 检测 root、模拟器、调试器、
   Frida/注入、内存篡改、虚拟应用；结果进入配置和推荐数据。
2. **行为评分**：BehaviX 用提交/取消/切换时间差给确认与 SKU 操作加减分，
   同一规则在 Java 与 native 各实现一份。
3. **请求防护**：HTTP 419/420 激活反攻击任务、流控锁定和单接口锁；
   合法验证码结果通过本地广播恢复等待中的请求。
4. **设备与签名证据**：`wua`、`umidToken`、`Dm-token`、`x-sign`、
   `wua`、设备等级评分等作为请求数据或头字段发送，最终是否参与决策
   由服务端决定。
5. **上传风控**：实名上传把图像、动作、传感器日志、背景检测等风险元素
   放入请求 `Data`，见 [transfer.md](transfer.md)。

## 2. 运行环境保护

### 2.1 Java ABI

`com.ali.security.RuntimeProtector` 暴露七个布尔检测和一个初始化入口：

```java
native boolean checkDebug();
native boolean checkEmulator();
native boolean checkHook();
native boolean checkInject();
native boolean checkMem();
native boolean checkRoot();
native boolean checkVirtualApp();
native void init(String str);
```

外壳 `YyBeyer` 的初始化参数是：

```java
runtimeProtector.init(
    "ef1a1897c8ec8fa6a75982e786113bf702808f45e12428aaf24b6b7ec806ca7b");
```

该字符串只作为安全组件初始化值传入；本报告不把它当作登录密钥，也未
使用它发起任何请求。

### 2.2 wrapper 与真实实现

`libsecurity-wrapper.so` 导出表把 ABI 落到固定偏移：

| 导出 | RVA | 转发到 |
|---|---:|---|
| `checkRoot` | `0xdc0` | `a@plt` |
| `checkEmulator` | `0xe78` | `b@plt` |
| `checkHook` | `0xf2c` | 经调用表选择剩余检查 |
| `checkDebug` | `0xffc` | `d@plt` |
| `checkMem` | `0x10b0` | `e@plt` |
| `checkInject` | `0x1164` | 经调用表选择剩余检查 |
| `checkVirtualApp` | `0x1230` | 经调用表选择剩余检查 |
| `init` | `0x1308` | `init@plt` |

每个布尔导出都执行同一语义，只在选择被调函数时使用不透明跳转：

```asm
bl   <check>
cmp  w0,#0
cset w0,ne
ret
```

因此 Java 返回值语义为“底层检查不返回 0”。`libsecurity-wrapper.so`
依赖 `libalisecuritysdk.so`，其真实检查体由后者的 `a`、`b`、`c`、
`d`、`e`、`f`、`g`、`init` 导出提供。

### 2.3 检查依据

`libalisecuritysdk.so` 的动态依赖和字符串给出了可验证检查面：

- 调用 `ptrace`、`waitpid`、`getppid`，可对父进程/调试状态进行判断；
- 调用 `inotify_*`，可监视路径变化；
- 调用 `dl_iterate_phdr`、`dlopen`，可检查已加载映像与动态库；
- 调用 `getauxval`，可读取 ELF auxiliary vector；
- 调用文件系统 API 和系统属性 API；
- 字符串包含 `/proc/self/maps`、`/system/bin/app_process64`、
  `/system/bin/linker64`、`/system/build.prop`；
- 内置 `xhook 1.2.0 (aarch64)`。

没有可读取的 shell 命令、特征表阈值或放行名单，因此报告不虚构额外
判定规则。

### 2.4 结果传播

`RecommendRule.getBeyerData()`（`RecommendRule.java:589-613`）把
`isHook`、`isRoot`、`isDebug`、`isInject`、`isEmulator`、`isMem`
写成字符串 `"1"` 或 `"0"`。配置键为：

```text
enable_runtime_check
runtime_check
```

静态代码能证明检测结果被序列化并进入推荐/配置数据；没有证据证明它
一定触发处罚。

### 2.5 SecurityGuard 组件面

`com/alibaba/wireless/security/open/SecurityGuardManager.java:49-70`
把插件编号固定映射为接口，`a(int)` 再经 `ISGPluginManager.getInterface`
解析实现：

| 编号 | 接口 | 风控相关能力 |
|---:|---|---|
| 1 | `ISecureSignatureComponent` | 安全签名 |
| 5 | `IDataCollectionComponent` | 昵称数据集合 |
| 6 | `IStaticDataEncryptComponent` | 静态数据加密 |
| 7 | `IDynamicDataEncryptComponent` | 动态数据加密 |
| 8 | `ISimulatorDetectComponent` | 模拟器检测 |
| 11 | `IUMIDComponent` | UMID/security token |
| 12 | `IPkgValidityCheckComponent` | APK 包完整性 |
| 14 | `IMalDetect` | 恶意环境检测 |
| 15 | `INoCaptchaComponent` | 无验证码/WUA 证据 |
| 16 | `ISafeTokenComponent` | 安全 token |
| 17 | `ISecurityBodyComponent` | 请求体安全数据 |
| 19 | `IDynamicDataEncryptFastComponent` | 快速动态数据加密 |
| 20 | `IVBHComponent` | 行为组件 |

关键接口给出的精确状态/错误如下：

- `INoCaptchaComponent` 有 `NC_INIT_STAGE=1`、
  `NC_VERIFY_SATGE=2`；成功码 `101/102`，失败码覆盖未初始化、
  HTTP 无 token、WUA 文件缺失/不匹配、session 过期、appkey 不符等
  `105/1201-1218`。返回 JSON 键固定为
  `errorCode/status/token/sessionId/sig/x1/x2/y1/y2`。
- `IUMIDComponent` 的环境值为 online `0`、pre `1`、daily `2`，
  提供 `getSecurityToken(int)`、`initUMIDSync(int)` 和
  `setEnvironment(int)`。
- `IDataCollectionComponent` 在当前接口中只有
  `getNick()/setNick(String)`，不应扩大解释成任意设备采集。
- `SecurityGuardManager.setGlobalUserData(String,String)` 只把键值写入
  进程内 JSON，两个参数缺一返回 `SecException(118)`。

Java 层不实现这些插件的密码算法，而是把 `requestType`、数据和环境交给
SecurityGuard native/plugin；MTOP 的 `requestType=7`、AVMP
`mwua/sgcipher` 与 body 数据入口见 [auth.md](auth.md)。涉及的
`libsgmainso-6.8.260704.so` / `libsgmisc.so` 作为安全插件载荷存在，
但接口、状态码和输入输出合同已由 Java 层闭合，不把其内部黑盒结果当作
未知算法写入业务协议。

## 3. BehaviX 行为评分

### 3.1 默认参数

`ScoreOrangeConfig.confirmDefault()`（`ScoreOrangeConfig.java:41-54`）：

```text
coScoreManualCancelUser    = -50
coScoreForZeroTime         = 500
coThresoldForLessThan      = 100
coScoreForLessThan         = 50
coThresoldForGreaterThan   = 1000
coScoreForGreaterThan      = -50
```

`ScoreOrangeConfig.skuDefault()`（`ScoreOrangeConfig.java:69-84`）：

```text
skuScoreForManualSwitch             = -50
skuScoreForZeroTime                 = 500
skuThresoldForReservedAndLessThan   = 200
skuScoreForReservedAndLessThan      = 50
skuThresoldForUnreservedAndLessThan = 200
skuScoreForUnreservedAndLessThan    = 100
skuThresoldForUnreservedAndGreaterThan = 1000
skuScoreForUnreservedAndGreaterThan = -50
```

配置槽位是 `android_behavix_score`，默认缓存有效期 `172800000 ms`
（2 天）；Orange 配置的 `validTime` 可按小时覆盖。

### 3.2 SKU 分支

`RecommendRule.getSkuScore` 的完整恢复代码位于
`jadxfix/rec/com/alibaba/pictures/behavix/rule/RecommendRule.java:795-907`。
实际判定顺序如下：

```text
if didManualSwitch == "1":
    return skuScoreForManualSwitch
elif t1 == 0 or t2 == 0:
    return skuScoreForZeroTime
elif reserved and (t2 - t1) < skuThresoldForReservedAndLessThan:
    return skuScoreForReservedAndLessThan
elif unreserved and (t2 - t1) > skuThresoldForUnreservedAndGreaterThan:
    return skuScoreForUnreservedAndGreaterThan
elif unreserved and (t2 - t1) < skuThresoldForUnreservedAndLessThan:
    return skuScoreForUnreservedAndLessThan
else:
    return 0 delta
```

其中 `t1/t2` 是行为时间戳，`reserved` 来自项目预留状态。阈值中的
`200` 是毫秒级差值上限；没有静态证据说明服务端使用何种聚合窗口。

### 3.3 确认分支

`RecommendRule.getConfirmScore`（`RecommendRule.java:638-713`）：

```text
if cancelContactsT != 0:
    return coScoreManualCancelUser
elif submitT == 0:
    return coScoreForZeroTime
diff = submitT - cancelContactsT
if diff < coThresoldForLessThan:
    return coScoreForLessThan
if diff > coThresoldForGreaterThan:
    return coScoreForGreaterThan
else:
    return 0 delta
```

代码直接证明“人工取消、零时间、过快、过慢”四种评分路径，不证明这些
分数在服务端有哪一种业务后果。

### 3.4 native 对拍

`TocNative` 的符号和 ELF 地址：

| 函数 | 地址 | 大小 |
|---|---:|---:|
| `nativeSkuScoreNew` | `0x3085c` | `5600` |
| `nativeConfirmNew` | `0x31e3c` | `1832` |
| `nativeGetItemScore` | `0x32564` | `5972` |
| `nativeMd5` | `0x33cb8` | `8432` |

Java 测试入口把异常结果编码为：

```text
-111  确认评分 Java/native 不一致
-112  SKU 评分 Java/native 不一致
-113  另一个特征值或结果不一致
-1002 / -1003 / -1004  对应 safeNative* 调用失败
```

这证明评分存在双实现对拍和降级路径；native 内部的进一步算术没有
单独复述为业务阈值，避免把测试码误写成分数。

## 4. HTTP 419/420 与流控

### 4.1 419 分流

`AntiAttackAfterFilter.java:32-75`：

```text
HTTP status == 419
  ├─ Bx-action: login
  │    → SESSION pool + RemoteLogin.login
  ├─ 同时有 location 与 x-location-ext，且非 background
  │    → antiAttackHandler.handle(...) + ANTI pool
  └─ 其他
       → ANDROID_SYS_API_41X_ANTI_ATTACK
```

`AntiAttackHandlerImpl` 的活动窗口为
`DEFAULT_WAIT_RESULT_TIME_OUT = 20000` ms：

```text
action = mtopsdk.mtop.antiattack.checkcode.validate.activity_action
result = mtopsdk.extra.antiattack.result.notify.action
"success" → retryAllRequest
其他结果或超时 → failAllRequest
```

相关错误：

```text
ANDROID_SYS_API_41X_ANTI_ATTACK
ANDROID_SYS_API_FLOW_LIMIT_LOCKED
```

错误消息含 `(419)` / `(420)`，代码把 HTTP 状态映射到 `EC20000`。
这是客户端等待/失败策略，不是服务端拒绝原因的完整解释。

### 4.2 单接口锁

`ApiLockHelper`：

```java
LOCK_PERIOD = 10;
boolean iSApiLocked =
    Math.abs(now - lockStartTime) < lockInterval;
```

锁定时先读取响应里的 `interval / 1000`，再依次尝试单个 API、全局锁，
最后回退到 `LOCK_PERIOD`。可见结果是客户端暂时不再发送该接口；服务端
限流规则不可见。

## 5. 设备/请求证据

- `BehaviXAgent` 写入并读取 `bx_config`、`bx_delay`、
  `bx_feature_other`、`android_behavix_score`、`bx_score_toc_config`。
- `MainActivity` 写入 `pictures_device_level_score` 和
  `pictures_device_level_desc`。
- `yq0.c()`（`yq0.java:66-75`）调用
  `BehavixProxy.getItemData(score, {type:"1"})`。
- 下单/风控路径可把 BehaviX 的 `Dm-token` 放入订单头，但报告不发布
  token 值。
- `buildRPSecurityData()` 返回 `{wua,t,apdId,umidToken}`；
  `buildWSecurityData(WUAData)` 再加入系统和设备字段。
- `XFlushBxUtil` 的埋点模块是 `bx`，默认点位是 `failureMonitor`。

这些字段能证明客户端会采集/发送设备与行为证据；不能由静态代码推出
服务端如何评分、锁定或处罚。

## 6. 隐私双名单与调用采样

`PrivacyDoubleListDelegate` 的 JSON 配置键为：

```text
pn rid act lmt crt dh
```

被 AOP 拦截的方法包括：

```text
WifiInfo.getMacAddress()
TelephonyManager.getDeviceId() / (int)
TelephonyManager.getImei() / (int)
TelephonyManager.getSubscriberId()
Settings$Secure.getString(...)  仅参数为 android_id
NetworkInterface.getHardwareAddress()
Build.getMODEL/getBRAND/getRELEASE
OpenDeviceId.getOAID(Context)
getCurrentPhoneType/getNetworkOperator/getNetworkOperatorName
getNetworkOperatorForPhone(int)
getSimState/getSimOperator/getSimOperatorName
getPhoneType/getCellLocation
```

配置样本的结构是：

```json
{"rid":47800,"act":1,"rs":0,"sr":0,"dh":"0_0_0"}
{"rid":53000,"act":32,"rs":0,"sr":1,
 "modRet":"","lmt":0.0003472,"crt":1,"dh":"1000_0_999"}
```

AOP 解析器把 `lmt` 解析为 double、`crt` 解析为 int、`dh` 解析为 string。
`act` 的业务枚举没有在 APK 中给出定义，因此不作猜测。它控制/记录调用
采样与返回处理，不等同于已经发送了字段值。

## 7. 安全结论

- **可确认**：运行环境七项检测、BehaviX 的精确评分分支和阈值、
  419/420 的本地等待与重试策略、接口锁周期、设备证据字段、上传风险
  元素与调用采样配置。
- **不可确认**：服务端对各字段的权重、阈值、处罚、账号或交易限制；
  没有进行实际请求或下单测试。
- **没有未解释的加密实现**：外壳 XOR、native 图像/SVG 解密、UT RC4、
  BoringSSL、签名/摘要用途的归类和代码边界见
  [evidence.md](evidence.md)；报告中每个出现的加密/混淆 surface 都说明
  了输入、输出或“未调用”的静态证据。
