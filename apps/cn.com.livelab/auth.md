# 认证机制

## 1. 登录接口族

| 路径 | PP 偏移 |
|---|---|
| `auth/app/login/phone` | `0x296e0` |
| `auth/app/login/phoneCaptcha` | `0x295e8` |
| `auth/app/login/phoneHw` | `0x1fa98` |
| `auth/app/login/phoneIdentity` | `0x1fa70` |
| `auth/app/v3/login/phoneApple` | `0x1fad8` |
| `auth/app/v3/login/wx` | `0x29390` |
| `auth/app/v3/login/phoneWx` | `0x1f8c0` |
| `auth/app/login/hw` | `0x29518` |
| `auth/app/login/identity` | `0x295a8` |
| `auth/app/logout` | `0x1f760` |
| `member/member/app/modifyPhoneWithNoToken` | `0x1f788` |
| `thirdParty/wangYi/app/v1.1/loginTokenVerify` | `0x296b8` |

第三方绑定族：`thirdParty/wx/app/code/bind`（`0x1ce60`）、
`thirdParty/dy/app/bind`（`0x1cd80`）、`thirdParty/dy/app/getUserInfo`
（`0x1cd88`）、`member/member/app/hw/bind`（`0x1cb10`）。

## 2. 票据

| 名称 | PP 偏移 | 用途 |
|---|---|---|
| `access_token` | `0x17e78` | 主 API 令牌 |
| `refresh_token` | `0x17ea0` | 刷新 |
| `Authorization` + `Bearer ` | `0x188b0` / `0x188d8` | 请求头承载 |
| `mallTokenParam` / `mallTokenCache_` | `0x17df8` / `0x12bb8` | 商城域票据 |
| `accessToken` | `0x29650` | 预取号返回 |
| `identityToken` | `0x1fad0` | 身份认证登录 |

商城票据还会拼接到 URL：`&token=`（`0x1aa18`）、`?token=`（`0x508c0`）、
`&isToken=true`（`0x2ea00`），并写日志 `[mall] 拼接token后的链接`
（`0x17e40`）。该行为会把票据带入 WebView/CDN 链接，属于客户端事实。

## 3. 短信验证码闸门

发送短信前必须完成 Geetest 四代校验：

```
captchaOutput  PP 0x1c200   ->  captcha_output  PP 0x1c208
passToken     PP 0x1c210   ->  pass_token      PP 0x1c218
genTime       PP 0x1c220   ->  gen_time        PP 0x1c228
phoneCountryCode                       PP 0x1c230
-> POST tool/app/captcha/verifyAndSendSms   PP 0x1c238
```

`captchaOutput`/`passToken`/`genTime` 是 Geetest 客户端产出的三种票据，
`captcha_output`/`pass_token`/`gen_time` 是提交给业务服务端的字段名，
转换发生在同一闭包内。未通过校验时不会进入 `verifyAndSendSms`。

## 4. 实名与人脸

| 路径 | PP 偏移 |
|---|---|
| `thirdParty/faceid/app/bizToken` | `0x1b160` |
| `thirdParty/faceid/app/ks/bizToken` | `0x1aed8` |
| `thirdParty/faceid/app/verify` | `0x1b0b8` |
| `member/app/tempIdentity/faceid/verifyKsApp` | `0x1aee0` |
| `member/app/tempIdentity/verifyIdentity` | `0x1b5d8` |
| `member/authInfo/app/identity/authentication` | `0x1c528` |
| `member/authInfo/app/identity/changeBind/verify` | `0x1bfc0` |
| `member/member/app/v2/checkIdCard` | `0x1b630` |

请求字段族：`idCardNo`（`0x1aed0`）、`idCardType`（`0x1aec8`）、
`idCardName`（`0x1b628`）、`biz_token`（`0x1b0e8`）、`facePhotoUrl`
（`0x1af70`）、`fingerprint`（`0x1b308`）。活体由 `libmegface.so`
（`megface_v2_10_16_csg_faceid`）与 `libMegActionFmpJni.so` 完成，
回传 `thirdParty/faceid/app/verify` 的 `verify.jpg`（`0x1b0d8`）。

## 5. 令牌失效分支

`gXa.yhd` 检测响应中的 `reLogin`（PP `0x11868`）即触发
`LoginEvent.loginOut`（PP `0x12d38`），并设置 `is_login`（PP `0x12e08`）。
`ignoreRespError`（PP `0x13cf0`）用于静默请求，不向用户展示错误但仍走同一
错误判定顺序。

## 6. 静态化说明

未登录真实账号、未获取真实令牌、未调用任何登录接口。服务端票据有效期、刷新策略
与风控拦截不可由客户端静态分析断定。
