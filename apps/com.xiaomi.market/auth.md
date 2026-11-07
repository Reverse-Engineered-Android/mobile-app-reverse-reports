# 认证与签名

## 通用商店请求

商店业务请求通过 `Connection` / `ConnectionBuilder` 发送，并带 `Parameter.getBaseParameters()`。该函数注入设备与区域等通用参数；本报告只记录字段面，不展开可识别设备值。

部分路径使用 `ConnectionRSA`：

- `statistics/appactive` 活跃统计上传；
- `pi/pinstaller/*` 装包校验。

`ConnectionRSA` 使用内置服务端证书校验，并对请求参数做加盐哈希/签名。代码中存在固定 UUID 形态 salt 常量与服务端公钥资源；不公开密钥值或可复用的签名构造方法。

## 小米账号与 xmsf

应用依赖小米账号 SDK 与 `com.xiaomi.xmsf` 基础服务。Manifest 声明账号相关权限：

- `GET_ACCOUNTS`
- `GET_ACCOUNTS_PRIVILEGED`
- `AUTHENTICATE_ACCOUNTS`
- `MANAGE_ACCOUNTS`
- `USE_CREDENTIALS`

账号能力主要用于登录态、商店个性化、支付/下载等业务，不是安全检测请求的前置条件；安全检测通用参数独立构造。

## TrustZone / 设备签名

下载与风控验证链路使用 `SecurityCheckManager`：

- `assc/getAsscToken` 获取风险验证 token；
- `assc/verifyToken` 校验 token；
- payload 含 `tzSignRisk` / `tzParamsRisk`；
- `TrustZoneSignHelper.getTzSignData()` 生成 TrustZone 签名数据，再交给 `SecurityDeviceSignManager.sign()`；
- 服务端返回 actionCode 为 2 时要求重取 token 或人工验证。

## 验证码 SDK

`com.xiaomi.verificationsdk` 使用：

```text
https://verify.sec.xiaomi.com/captcha/v2/config
https://verify.sec.xiaomi.com/captcha/v2/data
```

环境数据包括电池、设备标识、应用版本、SDK 版本、VPN、品牌/机型、系统版本、屏幕亮度、模拟器、root、Xposed/Substrate、开机时间、安装时间、包名、应用名、虚拟化状态及多种传感器值。SDK 内部有：

- `CheckHook`：检查 `/proc/self/maps`、已知 hook 框架包名与调用栈；
- `CheckVirtual`：检查 cgroup 与进程列表；
- 环境 JSON 用随机 AES key 加密，再用内置 X.509 RSA 公钥封装；
- 请求经 `SecurityDeviceSignManager.sign()` 签名后上传。

## OneTrack 会话

OneTrack 请求头包含 `OT_SID`、`OT_ts`、`OT_net`、`OT_sender`、`OT_protocol`。加密密钥可通过 `/track/key_get` 换取，客户端也有内置兜底 key；本报告不公开 key 值。
