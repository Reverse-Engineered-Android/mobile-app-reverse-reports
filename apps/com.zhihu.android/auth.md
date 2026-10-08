# 认证机制

## 1. 请求头层

### 1.1 `Authorization`（`GlobalRequestDecorator.java`）

```java
static String getAuthorization() {
    AccountInterface accountInterface = (AccountInterface) g.b(AccountInterface.class);
    if (accountInterface == null) return "oauth " + com.zhihu.android.api.util.c.f46994a;
    Account currentAccount = accountInterface.getCurrentAccount();
    if (currentAccount == null)  return "oauth " + com.zhihu.android.api.util.c.f46994a;
    return "Bearer " + currentAccount.getAccessToken();
}
```

第二条路径（响应后置拦截器，`lambda$static$3`）在 `Authorization` 头存在但为空、
或 `!accountInterface.hasAccount()` 且头为空时写入：

```java
builderNewBuilder.header("Authorization", "oauth a09343e8e67e44b29e0d850c14c7bf");
```

即：**已登录 → `Bearer <accessToken>`；匿名 → 内置客户端凭据 `oauth <clientId>`**。
客户端凭据常量在 `com.zhihu.android.api.util.c`（`f46994a`）中；此值与
签名串中 `Authorization` 段直接相关（见 [network.md](network.md) §4.1）。

### 1.2 设备身份头

| 头 | 生成 |
| --- | --- |
| `x-udid` | `CloudIDHelper.a().a(ctx)`（CloudID 设备 ID） |
| `X-ZST-81` | `RuidSafetyManager.a().j()`（字段 `o`） |
| `X-ZST-82` | `RuidSafetyManager.a().i()`（字段 `h`） |
| `x-at-df-if` | `DeviceCollectorManager.c().b()` |

`global` 头在 `GlobalRequestDecorator.intercept()` 中逐个补齐。

## 2. 登录体系

| 端点 | 用途 |
| --- | --- |
| `/account/sub/register` | 注册 |
| `/account/switch` | 账号切换 |
| `/api/account/prod/init/udid`、`/init/udid_guest` | 初始化 udid / 游客 udid |
| `/account/{social_type}/bind`、`/api/account/prod/social/associate/{type}/bind` | 第三方绑定 |
| `/api/v5/megvii/biz_token` | 旷视活体 biz token |
| `/api/v5/megvii/verify` | 旷视活体校验 |
| `/api/v5/face/validate` | 人脸校验 |
| `/api/v4/member/login/record` | 登录记录 |
| `/account/unlock/*`（`api/account/prod/account/unlock/...`） | 账号解锁 |
| `/captcha`（GET/POST/PUT） | 验证码 |
| `/license/permission/verify` | 许可校验 |

一键登录：`com/zhihu/android/operator/fly_verify/*`（FlyVerify）。登录成功态由
`AccountInterface.getCurrentAccount()` 提供，`accessToken` 随 `Authorization` 下发。

## 3. CloudID 签名（`cloudid/d/a.java`）

`UdidRequest` 用 form body，方法 1=POST、2=PUT（`throw new IllegalArgumentException(
"method is not 1 or 2")`）。

```java
builderPost.url(this.f69055c);
this.f69054b.put("x-req-signature", a(this.f69054b, formBodyC));
a(this.f69054b, builderPost);            // 写入 x-sign-version / x-app-id / x-req-ts 等

private String a(Map<String,String> map, FormBody formBody) {
    return CloudIDHelper.a().encrypt(
        b(map.get("x-sign-version")), b(this.f69056d), b(this.f69057e),
        b(formBody != null ? a(formBody) : ""),
        b(map.get("x-app-id")), b(map.get("x-req-ts")), b(this.f69058f));
}
```

`CloudIDHelper.encrypt(String×7)` 为 native（`CloudIDHelper.java:78`），另有
`encryptWithoutSecurekey(String)`（:80）。native 库由 `com.c.a.c.a(context, "encrypt",
callback)` 在初始化时按需下载/加载（`CloudIDHelper.java:112`），即在主 APK 之外
可能取到第二段 native 实现。

## 4. 会话与本地凭据

- 账号数据落在 `account_provider.db`（设备端只读核对：1 张业务表 + `android_metadata`）。
- CloudID 落 `oneid.db`（表 `r`）与 `kimi-bot` 无关的 `oneid` 体系。
- 登录态广播（`NOTIFY_CLEAR_SESSION` / `NOTIFY_SESSION_VALID` 语义由
  `com.zhihu.android.account.*` 承载）。
- `passport/captcha/*` 管理验证码状态；`operator/fly_verify/*` 管理一键登录令牌。

## 5. 认证与风控的耦合

`Authorization` 段是签名串的一部分，`x-udid`/`X-ZST-81`/`X-ZST-82` 同属请求头层：
服务端同时校验令牌有效性与设备指纹一致性。客户端侧的指纹生成见
[risk.md](risk.md) §4。匿名请求仍带 `oauth` 客户端凭据与设备指纹，因此
“未登录”不等于“无设备标识”。

## 6. 证据等级

- `Authorization` 两分支、CloudID 7 参数 native、udid 端点：**已验证**（源码字面）。
- `com.zhihu.android.api.util.c.f46994a` 的具体值：**已验证存在**，值不公开。
- CloudID 动态下载的 native 段内部算法：**结构已证实**（入口与参数确定），
  实现细节属加固库，客户端静态不可进一步展开，报告不猜测。
