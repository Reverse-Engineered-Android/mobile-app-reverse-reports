# 认证与授权

## 1. 创建请求认证

`bxmu.java:41-58` 的 `/androidantiabuse/v1/x/create` 使用：

1. Google API key 查询参数，报告中统一写为 `<redacted>`；
2. `Binder.getCallingUid()` 传入请求管线；
3. HTTPS/TLS；
4. `hvnz` typed protobuf；
5. 可选 `Network` 绑定。

代码未在 `hvnz` 中放入账号 OAuth token、cookie 或用户密码。API key 只标识
Google Play services 客户端/应用配置，不是用户身份认证。

## 2. 响应完整性认证

`bxky.java:23` 内嵌 RSA SubjectPublicKeyInfo 公钥；其 DER SHA-256 为：

```text
cfa6b5243de9e3fcc0408aae5b40b5b480c3cfc6db8828f338c176c57c312954
```

`bxky.java:28-46`：

- `KeyFactory("RSA")`
- `X509EncodedKeySpec`
- `Signature("SHA256withRSA")`
- `update(hvoa.c)`
- `verify(hvoa.d)`

`bxkz.java:54-56` 只有验签成功才继续解析。该链保护服务端响应的来源与
完整性，不提供用户身份认证或请求内容机密性；机密性依赖 TLS。

## 3. payload bytecode 认证

`DroidGuard.java:93` 依次尝试两枚 Base64 RSA SPKI 公钥：

| 位置 | DER SHA-256 |
|---|---|
| `DroidGuard.java:93` 第一枚，与 `bxky.java:23` 相同 | `cfa6b5243de9e3fcc0408aae5b40b5b480c3cfc6db8828f338c176c57c312954` |
| `DroidGuard.java:93` 第二枚 | `03551c17e169af5110d7a42b33cad55b156661536ff054e4c0e8fe55a77e3660` |

`DroidGuard.java:107-120` 对 byteCode 执行 `SHA256withRSA.update` +
`verify`；两枚 key 均失败时在 `DroidGuard.java:93-95` 抛出
`Failed to verify bytecode.`。

## 4. 同意与运行闸门

`bxgx.java:93-95` 在汽车受限配置的分支中，当 `gtrs.a(context)` 为真时拒绝
运行并抛出 `Can't run DroidGuard without gTOS acceptance`。

`gtrs.java:11` 的谓词精确组合：

```text
hasSystemFeature("android.hardware.type.automotive")
&& SystemProperties("ro.android.car.restrictbytos")
&& AutomotiveSetupServices__is_google_disabled_before_gtos
&& Settings.Secure("android.car.KEY_USER_TOS_ACCEPTED") == 1
```

该处证明存在显式 gTOS 运行闸门；其日志文本与谓词中 accepted 字段的组合
不一致，因此报告只陈述可观测的分支条件，不外推为所有设备的通用同意模型。

## 5. 调用方授权面

`DroidGuardHandle` Binder 名称是
`com.google.android.gms.droidguard.internal.IDroidGuardHandle`。结果通过
`obtainAndWriteInterfaceToken` 写入并 `transactOneway(1, ...)`。GMS 校验
Binder interface token，并把请求 UID 纳入管线；客户端没有看到把 DroidGuard
结果返回给任意第三方进程的开放组件。
