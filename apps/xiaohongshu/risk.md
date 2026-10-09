# 小红书主要风控机制

## 1. 分层模型

```text
请求完整性层
  Shield / Tiny / 公共参数 / 登录态

设备与环境层
  libxyasf / native detection / Java collectors / JS fingerprint

网络与服务层
  failover / circuit breaker / APM / risk response

账号处置层
  login risk / device violation / anomaly / intervention / self-resolve

挑战与核身层
  Walify / ValidateActivity / face KYC / liveness / payment security

第三方与本地策略层
  Getui/GTC/GBD / push state / remote collection policy
```

## 2. 请求完整性

### Shield

- `libxyass.so` 是 OkHttp native interceptor。
- 覆盖 method、URL path/query、platform info、request body。
- 添加 `shield` 和 `xy-platform-info`。
- 外层是 `XY + Base64`，blob 内含 type、长度、RC4 payload 和摘要。
- 64 字节 token payload 派生 HMAC 形状 key，内部哈希为定制 MD5 族变体。

### Tiny

- `libtiny.so` 通过 opcode 分发生成 mini-sign。
- 读取完整 method/URL/body，输出混淆 header。
- 使用 `x-legacy-did`、`x-legacy-sid` 与 `x-n0`、`x-o9`、`x-p0`、`x-r4`、`x-r4o`。
- `/api/sc/tt` 用于刷新/获取 tiny token。
- 网络切换会通知 Tiny，可能触发重新取 token/重新签名。

### 叠加

Hera/推荐路径可同时使用 Shield 和 Tiny；公共参数、登录态、User-Agent、Referer、优先级、APM 等拦截器进一步约束请求。

## 3. 设备指纹

调度类在首个 Activity resume 后以 10 分钟节流启动，内部有三线程：

```text
virposd  环境/进程信息采集
fpwdog   3 秒看门狗，防止采集线程被 hook 卡死
fp       native FingerPrintJni.start
```

Java 层可见采集器：

| 类别 | 内容 |
| --- | --- |
| 传感器 | 一次加速度、陀螺器差值、全传感器列表 |
| 进程 | `ps`、`getRunningAppProcesses`、uid/name |
| 无障碍 | enabled services 与开关 |
| 电池/系统 | 电量、状态、Build/ROM 信息 |
| 网络 | 网络类型、切换、接口信息 |

native 层还检测/采集 root、模拟器、Xposed、VirtualApp、ptrace/TracerPid、maps、EGL/GPU、Widevine deviceUniqueId、系统属性和 APK 签名/CRC。指纹 native 可自行 HTTP 上报，不能只 hook Java OkHttp。

这批采集器不只服务于风控：其敏感 API 调用同时受应用内置**隐私合规自查框架**登记与限频（55 个五位数 API 号 + 148 个 AppOps 索引、超频异常、APM/Sentry 双路采样上报），位置/GNSS/小区信息还受一层远端可切换的门控；采集类别与用户告知的对照见 [privacy-and-permissions.md](privacy-and-permissions.md)。

### native 上报容器

`libxyasf.so` 内的完整静态链路是：

```text
typed collectors
  -> protobuf serialization
  -> binary/image transform
  -> OkHttp multipart POST
  -> https://as.xiaohongshu.com/api/v1/d/upload
```

multipart 的静态常量确认 form-data part 名为 `file`、文件名 `image.jpg`、MIME `image/jpeg`；另有 `onReportStart/onReportSuccess/onReportFailed` 生命周期回调。这里的“image”是上传容器，不能直接等同于屏幕截图。

protobuf schema 字符串按以下命名空间出现：

| 命名空间 | 字段范围 |
| --- | --- |
| `Extension` | device_id、shumei_fingerprint、session_id、js_fingerprint |
| `JavaRuntime` | install_apps、app_list |
| `TelephonyNetwork` | carrier、国家码、cell/wifi IP、SSID、IMEI/MEI、ICCID、baseband、carrier code、WiFi/WAP MAC |
| `Motion` | sensor_list |
| `State` | accessibility_enabled_services |
| `System` | 语言、CPU ABI、进程、Build、设备/Android ID、IMSI、平台、命令/API 进程列表 |
| `App` | identifier、version、build、私有文件路径、APK 路径、channel |
| `Model` | name、version、fid |

原始采集结构还包括屏幕尺寸/density、内存、电池、位置、加速度计、陀螺仪、root、ptrace、maps 和注入库名。字段名和采集能力已恢复；protobuf 字段号、上传容器和调用链已闭环，私有 JPEG/二进制变换与签名 wire 实现不作为公开代码。

## 4. JS 指纹与伴随组件

- `XhsJsService` / `XhsJsJobService` 在 manifest 中声明于 `:jsfp`，但 21 个 DEX 的全量引用扫描未找到启动路径；`fpjs2.min.js` 只是 Java 字符串，不是样本资产。
- 结果使用 AES-CBC 加密写入本地 JS 指纹缓存，任务结束后清理。
- WebView monitor context 的 Java 模型可携带用户 ID/token、hash 过期信息、用户/track session、设备、屏幕、网络和 App build 字段。
- `libtinyd.so` 和 `com.xingin.tiny.daemon` 具备伴随/守护特征。
- Java 侧方法名加密通过 `@u5`/`@v5` 注解内的加密字节数组实现：`o5.a()` 解出方法名、`o5.b()` 解出参数类型名，`n5<T>` 再反射调用。**519 个站点已闭式还原（100% 合法 Java 标识符）**；类名/常量名解密：**822 个调用点（字节码精确）中 811 个带内联 (cipher,key) 对，全部映射到明文，0 未映射**。详见 [tiny-and-app-sweep.md](deepdive/tiny-and-app-sweep.md) §9.2、§9.2b。
- **归属**：上述机制是 `@u5/@v5` **加密注解**，不是「Petal 混淆」。`PetalConfig` 实为**插件化框架配置**（`PETAL_MODE = false`，用于 RN/Weex 注解默认值），不是混淆器。

## 5. 完整性与环境对抗

主要检测面包括：

- root、su、Magisk 类特征。
- 模拟器 Build、传感器、GPU、设备 ID 异常。
- Xposed/Frida/注入框架、ptrace、maps 和进程名。
- VirtualApp/多开/应用虚拟化。
- 无障碍自动操作。
- APK 重打包：签名、CRC、包名/组件差异。
- native 自检与 watchdog：隐藏、卡死或篡改采集流程会被视为异常。

## 6. 网络风控

网络链包含：

```text
request metrics -> business interceptor -> xy-common-params
-> User-Agent -> Referer -> circuit breaker -> failover/Cronet
-> priority -> response metrics -> transport/APM
```

关键行为：

- 熔断器有短时窗口和合成错误码。
- FailoverCronet/网络恢复探测可切换传输。
- 请求优先级区分前台业务、后台上报和资源任务。
- `X-Apm-*`/tracker 记录耗时、错误、网络和首屏指标。
- 网络变化通知 Tiny，刷新请求保护状态。

## 7. 账号风险处置

| 机制 | 路径/信号 |
| --- | --- |
| 设备违规 | `/api/sns/v1/system/ares/device/violation/query` |
| 登录风险 | `/api/sns/v1/user/login/risk/status` |
| 账号异常 | `/api/sns/v2/user/account_info/anomalies` |
| 手机绑定提醒 | `/api/sns/v1/account/phone-binding-dialog` |
| 干预配置 | `/api/sns/v1/account/intervention` |
| 自助解限 | `/api/security/antispam/v1/restriction/self-resolve` |

验证码入口为 `GET api/sns/v1/system_service/captcha_link`。自助解限响应包含 `antispamVerifyCtrlResp`, `complaintUrl`, `resolveResult`, `verifyUuid`。其余响应可包含风险标题、说明、deeplink、降级标记、异常账号摘要和干预 UI 配置。

## 8. 挑战与核身

- Walify 是自研验证码/风控容器。
- `ValidateActivity` 加载 H5 验证码或风险说明。
- 实名场景使用腾讯慧眼 WBCF、turingcam、TuringV2、优图活体。
- native 工具包含 SM2 加密封装，用于核身数据保护。
- `libturingmfa` 使用腾讯 WUP/Tars 二进制协议，Java 明文默认上报地址为 `https://tdid.m.qq.com/tmf`，服务返回/协同 DeviceToken。**该库把方法名与采集路径逐字节加密在 `.data` 字符串表里，由加载期构造子 `0x34d74` 原地解密**（密钥调度 `key_index = src_index mod 8`；348 个非空条目 / 325 条文法明文（源列表 416 项））；解出后可直接看到 **OAID 厂商 AIDL 表（37 条，覆盖华为/荣耀/小米/OPPO/vivo/三星/华硕/联想/魅族/MSA 等）**、**反模拟器与环境完整性路径（57 条：`/proc/self/maps`、`/sys/bus/virtio`、`init.svc.qemud`/`noxd`/`droid4x`/`vbox86-setup`、`microvirt.*` 等）**，以及 **Binder/反射直取（17 条）**。完整清单见 [deepdive/tiny-and-app-sweep.md](deepdive/tiny-and-app-sweep.md) §1.7(f)。
- 海外支付场景使用 Alipay/Antom 收银台安全组件，包含设备支付 token 与 securityCode。

这些组件面向高风险或高敏场景，不代表所有普通浏览请求都会触发。

## 9. 第三方推送/设备策略

Getui/GTC/GBD 的本地数据库保存：

- OAID、Android ID、广告 ID、品牌、机型、ROM、网络位置维度。
- Getui CID、runtime ID、配置端点、版本和时间戳。
- AES 加密配置、RSA 包装 AES key/IV、Java serialized 策略。
- gzip 设备/网络状态快照。

远程策略可能控制位置、Wi-Fi、蓝牙、应用列表、活动、剪贴板、保活和厂商适配的采集频率。策略开关不等于实际采集成功，仍受 Android 权限、系统版本、厂商限制和运行分支影响。

## 10. 风控绕过风险与研究边界

- 重放请求需同时处理公共参数、登录态、Shield 和 Tiny；只替换一个 header 不足。
- 修改设备字段需考虑 native 指纹、Widevine ID、传感器、Build、APK 完整性和时间/网络一致性。
- 仅导出风险 DB 通常无法恢复 AES 明文，因为 key/IV 被 TEE Keystore 私钥包装。
- native 采集可能绕过 Java hook；动态分析必须覆盖 JNI、独立进程、自报网络和 watchdog。
- 风控采集面与隐私告知面并不重合：无障碍服务列表、运行进程列表、传感器清单、剪贴板全文在首启文本中无对应条目（见 [privacy-and-permissions.md](privacy-and-permissions.md) §5.2）。
- 本报告不提供绕过、伪造 token、自动化真实账号操作或规避风控的方法。
