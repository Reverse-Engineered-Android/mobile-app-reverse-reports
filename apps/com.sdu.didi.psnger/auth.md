# 认证机制

## 1. 三层认证对象

| 层 | 载体 | 来源 | 位置 |
|---|---|---|---|
| 设备 | `secdd-authentication` 请求头 | SharedPreferences `authToken` | `AuthInterceptor.java:19,89-115` |
| 账号 | `token` 业务参数 | 登录态 `su1.e.getToken()` | `ApiBaseRepository.java:103,156` |
| 请求 | `wsgsig` 请求头 | `libdidiwsg.so` | `SignInterceptor.java:146-174` |

三层独立：设备层在未登录时也生效（退化为时间戳），账号层依赖登录，请求层对
每个请求单独计算。

## 2. 设备级 `secdd-authentication`

`com/didi/security/wireless/adapter/AuthInterceptor.java`：

```java
private static final String HEADER_IN_HTTP = "secdd-authentication";   // :19

private static String getToken() {                                     // :89
    if (TextUtils.isEmpty(token)) {
        getSp();                                                       // :92
        if (f58638sp != null) token = f58638sp.getString("authToken", ""); // :94
        if (TextUtils.isEmpty(token))
            token = String.valueOf(System.currentTimeMillis() / 1000); // :97
    }
    return token;
}

private static synchronized void updateToken(String str) {             // :105
    if (!TextUtils.isEmpty(str) && !str.equals(token)) {
        token = str;
        if (f58638sp != null)
            r.a(f58638sp.edit().putString("authToken", str));          // :110
    }
}
```

响应刷新路径在 `:136-139`：读取响应头 `secdd-authentication`，非空则回写。
SharedPreferences 文件名为 `authToken`（`:85`），key 为 `authToken`（`:94`），
设备端实际存在 `shared_prefs/authToken.xml`。

该头在设备和时间戳之间切换的语义是：首次运行尚未获得服务端签发的设备凭据时，
客户端用一个秒级时间戳占位，服务端在响应中下发真正的凭据，此后固定复用。

## 3. 账号级 token

`com/didi/carhailing/net/ApiBaseRepository.java`：

```java
map.put("token", su1.p.f110463b.getToken());     // :103
map2.put("token", su1.p.f110463b.getToken());    // :156
```

`com/didi/carhailing/framework/net/HttpParams.java:113` 同构。`su1.p.f110463b`
是 `su1.e` 的实现，接口 `su1/e` 暴露：

```java
long a();            boolean b(int);   boolean c();
String d();          String e();       String f();
int g();             String getPhone();  String getToken();
String getUid();     boolean isNewUser();
```

另有 `com/didi/sdk/net/ApiCommonParams` 风格路径把 `token`/`ticket` 放在
`didi-header-hint-content` 之外的头里（`Token`、`token`、`ticket` 三个头名在
静态扫描中同时出现），说明新旧两套写法并存。

## 4. 登录接口族

`/passport/login/v5/*` 共 49 个唯一路径字面量（`xu1/c.java` 为主接口）：

| 方法 | 路径 | 语义 |
|---|---|---|
| signInByCode | `:115` | 验证码登录 |
| signInByPassword | `:199` | 密码登录 |
| signInByFace | `:223` | 人脸登录 |
| signByAuth | `:247` | 第三方授权登录 |
| refreshTicket | `:253` | 刷新票据 |
| validateTicket | `:145` | 校验票据 |
| signOff | `:211` | 登出 |
| deleteAccount | `:307` | 注销 |
| getCaptcha | `:301` | 图形验证码 |
| verifyCaptcha | `:121` | 验证图形码 |
| verifyCode | `:91` | 验证短信码 |
| verifySlider | `:331` | 滑块验证 |
| codeMT | `:349` / `mfa/codeMT` `:85` | 多端验证码 |
| gatekeeper | `:367` | 门禁/风控闸门 |
| generateQRCode | `:217` | 生成扫码登录码 |
| queryQRCodeStatus | `:127` | 查询扫码状态 |
| confirmQRCodeLogin | `:181` | 确认扫码登录 |
| checkUserIdentityStatus | `:169` | 身份状态 |
| getUserIdentityStatus | `:337` | 身份状态 |
| verifyIdentity | `:361` | 身份核验 |
| verifyPersonInfo | `:265` | 个人信息核验 |
| mfa/generatePOIQuiz | `:187` | MFA 题库生成 |
| mfa/submitPOIQuiz | `:157` | MFA 题库提交 |
| mfa/commonVerify | `:313` | MFA 通用验证 |
| mfa/getCommonVerifyTypes | `:319` | MFA 类型查询 |
| resetPassword | `:151` | 重置密码 |
| resetEmail | `:103` | 重置邮箱 |
| checkPassword | `:271` | 校验密码 |
| setPassword | `:193` | 设置密码 |
| setCell | `:163` | 设置手机号 |
| getLoginType | `:175` | 登录方式 |
| getAuthList | `:97` | 授权列表 |
| ctrolAuth | `:355` | 授权控制 |
| getAllBizStatus | `:205` | 业务状态 |
| activeBizAccount | `:259` | 激活业务账号 |
| getNavIDList | `:241` | 导航 ID 列表 |
| getVerifyInfo | `:133` | 核验信息 |
| getVerifyTypes | `:343` | 核验类型 |
| getEmailInfo | `:295` | 邮箱信息 |
| activateEmail | `:277` | 激活邮箱 |
| verifyOrder | `:109` | 订单核验 |
| newUserAppeal | `:139` | 新用户申诉 |
| loginStat | `:325` | 登录统计 |
| getPostLoginAction | `:289` | 登录后动作 |
| getCountryList | `:229` | 国家列表 |
| getIdentity | `:235` | 身份 |
| forgetPassword | `:283` | 忘记密码 |

另有：

- `/passport/login/v5/pushTicket`（`com/didi/sdk/push/DPushHelper.java:88`）；
- `/passport/login/v5/getCountryList`（`com/didi/payment/creditcard/china/unionpay/country/m.java:8`）；
- `/passport/login/v5/signByAuth`（`com/didi/sdk/net/carrot/t.java:11`）；
- `/oauth2/oauth_code`、`/didipwd/api/v1/verifyPasswordDi`。

登录域名：`epassport.diditaxi.com.cn`（主）、`epassport.didiglobal.com`、
`epassport-us.didiglobal.com`、`prepassport.diditaxi.com.cn`（预发）、
`passport-test.didichuxing.com`（测试）、`oauth-jw.xiaojukeji.com`。

## 5. 凭据处理的本地边界

`com/didi/sdk/net/LoginNetInterceptor` 对 host 含 `passport` 的请求打印完整
请求与响应，但把下列字段掩码为字符串 `"didi"`：`uid`、`ticket`、`password`、
`new_password`。这是客户端日志脱敏，不是传输加密。

## 6. 手机号的两个独立变换

### 6.1 本地存储（DES）

`com/didi/unifylogin/store/LoginStore.java`：

```java
String phoneSignKey = az.b.c().getPhoneSignKey();                       // :206 / :331
Cipher cipher = Cipher.getInstance("DES");                              // :211 / :338
cipher.init(2, SecretKeyFactory.getInstance("DES")
        .generateSecret(new DESKeySpec(phoneSignKey.getBytes())));      // :212 解密
cipher.init(1, SecretKeyFactory.getInstance("DES")
        .generateSecret(new DESKeySpec(phoneSignKey.getBytes())));      // :339 加密
```

密钥由 `libsignkey.so` 的 native `getPhoneSignKey()` 提供
（`com/didi/sdk/signkylib/SignKey.java:8`），实现不透明。读写受开关
`enohp_encrypt` 控制（`:205`）。

### 6.2 上报哈希

`com/didi/common/map/adapter/didiadapter/g.java:32-50` 用同一密钥对手机号做
DES 加密后 Base64（`n.b(...)`）：

```java
Cipher cipher = Cipher.getInstance("DES");                              // :40
cipher.init(1, SecretKeyFactory.getInstance("DES")
        .generateSecret(new DESKeySpec(str.getBytes())));               // :41
byte[] bArrDoFinal = cipher.doFinal(bytes);                             // :42
return n.b(bArrDoFinal);                                                // :46
```

调用点：`com/didi/sdk/unifybridge/g.java:140`、
`com/didi/sdk/fusionbridge/module/FusionBridgeModule.java:1486`、
`com/didi/quattro/business/scene/callcar/callcarcontact/dialog/QUCallCarInfoDialog$uploadHistoryInfo$1.java:70,117,227`、
`uk1/c.java:147,223`。

同一文件 `:15-30` 提供反向解密用于本地回显。

## 7. 支付域认证

`com/didi/didipay/pay/net/DidipayHeadersInterception.java`：

```java
return hasHead(kVar, "Authorization");     // :35
aVarE.d("Authorization", getToken());      // :53
aVarE.c("x-ddfp");                          // :67
aVarE.d("x-ddfp", strI);                    // :68
```

`x-ddfp` 的值来自 `e82.f.i(context)`（`e82/f.java:330-345`），返回
`d.B`，即 `DeviceInfoNameEnum.customId` 对应的设备唯一标识
（`:337-339` 调 `b.a(context)` 生成）。`Authorization` 复用设备层凭据。

## 8. 票据与业务 token 的关系

| 凭据 | 用途 | 生命周期证据 |
|---|---|---|
| `secdd-authentication` | 全部请求的设备层校验 | 响应头刷新，持久化在 `authToken` |
| `token` | 业务 API 的账号归属 | 登录接口返回，`su1.e` 持有 |
| `ticket` | 登录票据，`refreshTicket` 刷新 | 登录流程 |
| `voyager-ticket` | robotaxi 域票据 | `com/didi/voyager` |
| `Authorization` | 支付域 | `DidipayHeadersInterception` |

`refreshTicket`、`validateTicket` 的存在说明票据有独立有效期，客户端可主动
刷新而无需重新走完整登录。

服务端两类凭据的实际有效时长、并发会话上限与失效策略不可由客户端静态证明。
