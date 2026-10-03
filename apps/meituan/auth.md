# 认证机制

## 1. 认证对象

| 对象 | 来源/去向 | 作用 |
|---|---|---|
| `User.token` | 登录响应 `User` | `UserCenter.getToken()` 提供给业务 API |
| `userid` | 用户信息或查询参数 | 与 token 成对出现在收藏等接口 |
| `login_auth_ticket/confirm` | 风控挑战 | 手机号登录与刷新 |
| `verifyResponseCode` | Yoda 响应 | 登录/重开/第三方登录确认 |
| `requestCode` | 短信/挑战流程 | 与验证码绑定 |
| `userTicket` | 票据登录/重开 | 票据验证 |
| `accessTokensJson/code` | 第三方 OAuth | 换取/绑定美团账号 |
| `code_challenge` | PKCE | 换 ticket |

## 2. 登录接口族

`com.meituan.passport.api.AccountApi`：

| 方法 | 路径 | 关键字段 |
|---|---|---|
| POST | `v2/account/mobilelogincode` | mobile, code, fingerprint, uuid |
| POST | `v3/account/mobileloginapply` | encryptMobile, countryCode, verifyLevel, smsVerifyLevel, poiid, specialRiskCode, needRetry, fingerprint, uuid, login_auth_ticket, login_auth_confirm |
| POST | `v3/account/mobilelogin` | FieldMap, verifyResponseCode, fingerprint, uuid, specialRiskCode, extra_param |
| POST | `v2/account/mobileloginapply` | FieldMap, verifyLevel, smsVerifyLevel, specialRiskCode, needRetry, fingerprint, uuid |
| POST | `v7/account/login` | FieldMap, requestCode, verifyResponseCode, fingerprint, uuid |
| POST | `v2/account/bindmobilelogincode` | FieldMap, fingerprint, uuid |
| POST | `v2/account/bindmobilelogin` | FieldMap, supportSecondaryMobile, confirm, fingerprint, uuid |
| POST | `v1/account/refreshtoken` | FieldMap, token, fingerprint, login_auth_ticket, need_auth_ticket |
| POST | `v1/account/ticketlogin` | FieldMap, userTicket, fingerprint, uuid |
| POST | `v1/account/verifylogin` | FieldMap, verifyResponseCode, userTicket, requestCode, fingerprint |
| POST | `account/auth/ticket` | client_id, code_challenge, token |
| GET | `compass/login/query` | 无显式字段 |

登录常量直接暴露服务端挑战语义：

- `user_err_login_need_captcha = 101039`
- `user_err_login_yoda_verify = 101190`
- `user_err_need_captcha_yoda = 101190`
- `user_has_risk = 101144`
- `user_risk_deny = 101135`
- `user_err_mobile_resale_by_operator_confirm_from_riskcontrol = 101285`
- `user_locked_frozen = 101299`
- `connect_need_yoda_verify = 101157`

这说明手机号/短信验证之上还有验证码、Yoda、风险确认、账户冻结/重开四层
服务端分支。

## 3. 第三方登录与绑定

`OpenApi`：

- `POST /thirdlogin/commonlogin/oauth2/access_token/{thirdType}`
- `POST /thirdlogin/commonlogin/oauth2/code/{thirdType}`
- `POST /thirdlogin/commonlogin/firstVerify/{thirdType}`
- `POST /thirdlogin/commonlogin/custom/getRequestCode`
- `POST /thirdinfo/thirdrequest/oauth2/accessToken2ThirdUserinfo/weixin`
- `POST /user/v1/bindthird`
- `POST /user/v1/code/bindthird`
- `POST /thirdlogin/api/v2/account/getmobile`

共同字段为 `state`、`justVerify`、`requestCode`、
`verifyResponseCode`、`fingerprint`、`uuid`、`extra_param`、
`login_auth_ticket`；OAuth token 形式字段是 `accessTokensJson` 或 `code`。

## 4. Token 的使用与持久化边界

大量业务调用执行：

```java
UserCenter.getInstance(context).getToken()
```

收藏接口显式传 `userid`、`token`、`ci` 和 `type/ids`；短视频接口直接把
`token` 放进 JSON map；账户信息用 `@Header("token")`。静态代码没有把 token
写入网络日志的统一实现，但 token 属于会话凭据，任何被 WebView bridge 或
导出 provider 暴露的调用都必须按凭据泄露风险审计。

刷新使用旧 token 而不是客户端自签新 token；PKCE `account/auth/ticket` 进一步
把 `client_id + code_challenge + token` 绑定到票据，服务端是否签发新会话不在
客户端可证明范围。

## 5. 登录风控链

```text
手机号/密码/第三方输入
    -> specialRiskCode / fingerprint / uuid
    -> mobileloginapply / login v7
    -> captcha 或 Yoda challenge
    -> verifyResponseCode + requestCode/userTicket
    -> User(token)
    -> refreshtoken / account/auth/ticket
```

每一步都可独立失败；客户端没有本地生成 `verifyResponseCode` 的能力，说明
Yoda 结果由服务端挑战与 native/页面确认共同产生。

## 6. 未测试声明

本页没有登录真实账号、刷新 token、触发验证码、请求 OAuth 或读取会话。
接口可达性、令牌有效期、重放窗口、设备绑定强度属于服务端状态，保持为未知。
