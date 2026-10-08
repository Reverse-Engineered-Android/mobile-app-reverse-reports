# 认证机制

## 1. 认证对象

认证链同时服务 Combo SDK、海沃塞账号 SDK 和游戏内 token 交换。账号
token 在 Java 类型 `Token.SToken`、`Token.CToken`、`Token.LToken` 之间
转换，服务端接口按 token 类型选择校验/换发。

## 2. 登录接口族

`PassportApiService.java:29-61` 声明 password、auth-ticket、third-party、
第三方绑定、越南 Web real-name、配置、reactivate、邮箱注册和第三方
注册。`SignInApiService.java:23-31` 也提供 reactivate/password/logout，
属于同一服务的兼容入口。`TokenApiService.java:23-43` 提供：

- `getActionTicketBySToken`
- `getByGameToken`
- `getBySToken`
- `verifyCookieToken`
- `verifyLToken`
- `verifySToken`

`VerifierApi.java:26-54` 增加 action ticket 创建、Auth ticket by SToken、
邮箱验证码、ticket info、age gate load/resend/update/verify。

## 3. 登录状态流

```text
appLoginByPassword / appLoginByAuthTicket / appLoginByThirdParty
        │
        ├─ server returns NeedCaptcha
        │       └─ parse AigisEntity → GeeTest4 → x-rpc-aigis → retry login
        │
        ├─ server returns risk ticket
        │       └─ RiskVerifyEntity → checkSmartCaptcha/create ticket → retry
        │
        └─ token response
                └─ SToken/CToken/LToken → Cookie and DS-signed API calls
```

`SignInManager.java:112-173` 在 password login 中读取 `x-rpc-aigis`，
解析后调用 `GeeTestManager.startGeeTestStandard`，成功回调重新进入
`loginByPassword`；失败/取消路径不会伪造 ticket。注册流程同样在
`SignUpManager` 中把 Aigis 作为 `createActionTicket` 的 header。

## 4. Token 使用边界

`RequestUtils.createCookieHeader` 只把 token 放进 Cookie header；
`DS` 只覆盖 body 签名。`createHeaders` 把 risk/Aigis/real-name/age ticket
放进可选 header，不把它们拼进 URL。`TokenRefreshManager` 和
`TokenManager` 负责校验、换发和过期清理；静态包没有可见的“把 token
发送给第三方日志服务器”的调用。

## 5. 风险与年龄门

`RiskVerifyService.java:20-25` 调用 Aigis SmartCaptcha。`RiskVerifyEntity`
定义 `risk_ticket`、`ticket`、`verify_str`、`verify_type`，其中
`VERIFY_TYPE_AIGIS=1`、`VERIFY_TYPE_RISK_VERIFY=2`。`VerifierApi` 的
age gate 接口使用 `birthday`、`parent_email`、`status`、`ticket_id`
等字段。年龄门不是一个客户端布尔开关：它依赖服务端 ticket 和
header 重新进入认证链。

## 6. 未测试声明

本报告没有验证密码、第三方 OAuth、token 有效期、服务端是否接受伪造
签名、验证码是否可重放，也没有提供绕过认证或风险验证的方法。签名和
cookie 字段的说明仅用于静态格式和隐私审计。
