# 小红书 9.37.0 认证机制

本文按“凭据从哪来、存在哪、怎么随请求走”三层拆解 9.37.0 的认证与身份信息。所有名称均可从样本源码复核；不含真实取值。

## 1. 分层总览

```
┌─ 账号层   id_token（登录响应 userExtraInfo["id_token"]）
├─ 会话层   sid（会话标识） / gid（游客标识）
├─ 设备层   deviceId(did) + fid/smid，由指纹 SDK 维护
├─ 签名层   shield / xy-platform-info（libxyass）
│           x-n0/x-o9/x-p0/x-r4/x-r4o（libtiny）
└─ 传输层   业务头部 + xy-common-params（明文参数袋）
```

**核心结论**：核心 API **不使用** `Authorization: Bearer`。9.37.0 的鉴权是把 `sid` / `id_token` / 签名字段拼进业务头部与 `xy-common-params`，签名由 native 组件覆盖在这种明文参数袋之上。Java 侧对 `"Bearer"` 的引用只出现在腾讯 COS、BCIM 和三方 SDK（支付、地图、推送）中，未出现在主 API 链路。

## 2. 账号层：`id_token`

登录响应结构 `loginResponse.f61596d0` 是 `Map<String,String>`，其中 `id_token` 被取出写入 `UserInfo`：

```java
// zu/i.java:168-171
Map<String, String> map = loginResponse.f61596d0;
if (map != null && (str2 = map.get("id_token")) != null) {
    str3 = str2;
}
userInfo.setIdToken(str3);
```

同一 Map 还承载 `signoff_flow`（注销流程状态，反序列化为 `ok6.b` 后交给 `ILoginProxy`）。

`id_token` 随后作为 `xy-common-params` 的一个字段出现在每个请求上（字段名见 `z2c/e.java`），因此它的**传播路径是明文头部**，而不是 `Authorization` 头。未登录态下该字段不出现，改由 `did` / `gid` / `identity_flag` 承担身份区分。

## 3. 会话层：`sid`

- `sid` 是 `xy-common-params` 的固定字段之一。
- 掉线原因走独立端点 `api/sns/v1/user/login/sid_reason`（`IDeviceService`，`@o` + `@d Map<String,String>` 表单体）。
- 设备管理走 `api/sns/v1/user/login/devices/history` 与 `api/sns/v1/user/login/devices/remove/history`（同服务）。

**关于 Cookie 的边界**：本次静态检索未在主链路发现 `CookieJar` 实现，也未在 DEX 中检索到 `web_session` 字面量。这**不足以**得出“不使用 Cookie”的结论——只能说明静态证据未闭环 Cookie/session 的作用位置。WebView 类场景（验证页、H5 活动）仍可能由 WebView 自身维护 cookie 存储，本次未做运行时抓取验证。

## 4. 设备层：`deviceId` / `fid` / `smid`

| 字段 | 含义 | 出现位置 |
| --- | --- | --- |
| `deviceId` / `did` | 设备标识 | `xy-common-params`；`libtiny.so` 写入 `x-legacy-did` |
| `fid` | 指纹标识 | `xy-common-params` |
| `smid` | 会话/设备混合标识 | dex 中与 `x-legacy-*` 同族出现 |
| `gid` | 游客标识 | `xy-common-params` |
| `uis` | 用户标识 | `xy-common-params` |

这些值由设备指纹子系统（`libxyasf.so` + Java 调度层）采集后写入全局上下文，再由 `ContextHolder` 供签名组件读取（Shield 启动任务 `r3.g` 把 `deviceId`/`appId` 注入 `ContextHolder`）。

## 5. 签名层

| 头部 | 组件 | Java 可见性 |
| --- | --- | --- |
| `shield` | `libxyass.so` | Java 侧 **0 命中**，仅在 native 装配 |
| `xy-platform-info` | `libxyass.so` | 同上 |
| `x-legacy-did` / `x-legacy-sid` | `libtiny.so`（`jt6.a` 调用） | 写入点在 Java 可见 |
| `x-n0` `x-o9` `x-p0` `x-r4` `x-r4o` | `libtiny.so` opcode 引擎 | 头部名在 dex，取值来自 native 返回 Map |

`libtiny.so` 的调用形状（`jt6.a`）：

```java
yya.f.e(method, url, bodyBytes)            // 收集请求材料
  → u2.b(-1762132820, method, host, path, query, body)
  → t.a(opcode, args)                      // 数字 opcode 进 native
  → Map<String,String>                     // 逐条写为 header
```

签名覆盖范围是 **method + host + path + query + body**；`libxyass.so` 的 `intercept` 拿到的是完整 `Interceptor.Chain`，因此可覆盖全部请求材料。

## 6. 接入点（只有两处）

`XhsHttpInterceptor.newInstance("main"/"hera")` 在全库只有两个注册点：

- `ModelProfile.java:132` — 设备能力画像（`modelportrait.xiaohongshu.com`）
- `HeraAbilityImpl.java:52` — hera 推荐/增长链路

Tiny 拦截器（`jt6.a`）附加在 hera 链上。这意味着 Shield/Tiny 是**按链路选择性挂载**的，而非全局无条件挂载。

库名本身也做了隐藏：`c.java` 用字节数组 `{120,121,97,115,115}`（`"xyass"`）经 `SoLoadProxy.loadLibrary` 加载。

## 7. 证据等级

| 结论 | 等级 |
| --- | --- |
| `id_token` 来自 `userExtraInfo["id_token"]` → `UserInfo.idToken` | 已验证（`zu/i.java:168-171`） |
| `sid`/`gid`/`did`/`fid` 等为 `xy-common-params` 字段 | 已验证（`z2c/e.java`） |
| 核心 API 不用 `Authorization: Bearer` | 已验证（主链路 0 命中；仅在 COS/BCIM/三方 SDK 出现） |
| Shield 接入点只有 2 处 | 已验证（`newInstance` 调用点枚举） |
| `shield` / `xy-platform-info` 头部名 | 结构已证实（Java 0 命中 + native 装配入口；不还原取值） |
| `x-n0`…`x-r4o` 语义 | 结构已证实（写入点确定，语义未从 native 反推） |
| Cookie/session 不参与核心鉴权 | **未闭环**：仅证明静态检索 0 命中，未做运行时验证 |
