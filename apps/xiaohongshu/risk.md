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

## 4. JS 指纹与伴随组件

- 隐藏 WebView/独立服务运行 `fpjs2.min.js`。
- 结果使用 AES-CBC 加密写入本地 JS 指纹缓存，任务结束后清理。
- `libtinyd.so` 和 `com.xingin.tiny.daemon` 具备伴随/守护特征。
- Petal 混淆通过加密注解字节、运行时解密包装器和 opcode 分发隐藏调用名。

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

响应可包含风险标题、说明、deeplink、降级标记、异常账号摘要和干预 UI 配置。

## 8. 挑战与核身

- Walify 是自研验证码/风控容器。
- `ValidateActivity` 加载 H5 验证码或风险说明。
- 实名场景使用腾讯慧眼 WBCF、turingcam、TuringV2、优图活体。
- native 工具包含 SM2 加密封装，用于核身数据保护。
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
- 本报告不提供绕过、伪造 token、自动化真实账号操作或规避风控的方法。
