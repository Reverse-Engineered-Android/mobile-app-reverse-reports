# 认证机制

## 1. 凭据清单

| 凭据 | 存储位置 | 读入口 | 写入点 |
| --- | --- | --- | --- |
| `AccessToken` | MMKV `pdd_config_common`，键 `jsSecureKey___ACCESS_TOKEN__` | `h2.b.c().d()`，对上层暴露为 `q1.c.z()` | 登录响应处理 |
| 上一次 token | 同库，`jsSecureKey___LAST_ACCESS_TOKEN__` | `h2.b.c().h()` | 换号/退出 |
| `PDDAccessToken` | Cookie | cookie store（native 保护名单内） | 服务器 `Set-Cookie` |
| `pdd_user_id` | MMKV `pdd_config_common`，键 `pdd_id` / `key_last_user_id` | `q1.c.T()` 系列 | 登录成功 |
| `pdd_user_uin` | Cookie / MMKV `jsSecureKey___USER_UID__` | `h2.b.c().l()` | 登录成功 |
| `install_token` | 由 `DeviceUtil.getUUID(context)` 生成 | 同上 | 首启 |
| `ETag` | `bn0.b.a().d()` | 公共头 | 会话 |

`h2.b` 的 `a()` 会把旧 SharedPreferences 里的 `jsSecureKey___ACCESS_TOKEN__`、
`jsSecureKey___USER_UID__`、`pdd_id`、`key_last_user_id`、
`jsSecureKey___LAST_ACCESS_TOKEN__`、`userAgentString` 六个键迁移进 MMKV，并写
`__oksp_migrate__` 标记。当 `q1.c.w0()` 为真时另开 `pdd_config_common_enc` 库，
用 `q1.c.E(false)` 作为 crypt key。

## 2. token 的来源链

```java
q1.c.z()   // 200+ 行的分支逻辑
  ├─ h2.b.c().d()                     // MMKV 快路径
  ├─ 内存缓存 f89384v / f89380r / f89383u
  ├─ q1.c.q() == true  → 把 MMKV 里的 token 清空并置标志
  └─ 登录态判定 com.aimi.android.common.build.b.i()
```

`q1.c.z()` 的结果被 `sv1.d.e(boolean)` 写成 `AccessToken` 头。注意
`q1.c.z()` 在"已登出但 MMKV 尚有残留"的分支里会主动**清空** MMKV 值，因此它同时
承担了 token 失效清理的职责。

## 3. 登录接口族

全部由 `kp1.a` 的 `u0()` = `sv1.c.c(context)` 拼出 base，路径集中在
`/api/sigerus/*`、`/api/galilei/*`、`/api/apollo/*`、`/api/francis/*`：

| 用途 | 路径 |
| --- | --- |
| 手机号 + 验证码登录 | `/api/sigerus/login_mobile` |
| 授信手机号登录 | `/api/sigerus/login_credit_mobile` |
| 票据登录 | `/api/sigerus/login_ticket_mobile` |
| 虚拟号登录 | `/api/sigerus/virtual/mobile/login` |
| 短信验证码请求 | `/api/sigerus/mobile/code/request` |
| 票据验证码请求 | `/api/sigerus/ticket/mobile/code/request` |
| 验证码查询 | `/api/francis/mobile/code/query` |
| 图形验证码 | `/verification_code_picture` |
| 二维码登录 | `/api/cupid/query_qr_code`、`/api/cupid/login/check_qr_code` |
| 换号登录 | `/api/sigerus/account/change/login` |
| token 刷新 | `/api/galilei/refresh/token` |
| 刷新确认 | `/api/galilei/refresh/ack` |
| token 校验 | `/api/galilei/valid/token` |
| 登出 | `/api/sigerus/logout` |
| 注销账号 | `/api/sigerus/remove_account` |
| 登录历史 | `/api/apollo/query_login_history` |
| 登录方式 | `/api/apollo/logintype` |
| 通用登录入口 | `/login` |
| 本人资料 | `/user/profile/me` |

## 4. token 刷新的强保护

`/api/galilei/refresh/token`、`/api/sigerus/login_mobile`、
`/api/sigerus/login_credit_mobile`、`/api/sigerus/login_ticket_mobile`、
`/api/sigerus/mobile/code/request`、`/api/sigerus/ticket/mobile/code/request`、
`/api/francis/mobile/code/query`、`/login` 这 8 条路径都在
`Network.add_signature_apis_V2_72500` 默认列表中，即**每次登录/刷新请求都带 v2
签名头** `x-p-t`（内部 code == 0 时另加 `x-p1`）。

同时 `/api/jinbao/utils/add/checkclick` 与 `/api/apollo/query_login_history` 走
v1 签名。因此登录相关请求同时承受 anti-token + 签名两层。

## 5. 登录请求体

`mp1/d.java`（`LoginMethodsImpl` 的绑定层）构造登录 JSON：

| 场景 | 字段 |
| --- | --- |
| 手机号 + 验证码 | `mobile`、`code`、`login_app_id`、`tel_code`/`country_id`（国际号且开关开）、`is_force_login_slave_account`、`support_enhance_type`、`extra_vo` |
| 手机号脱敏 + 验证码 | `mobile_id`、`mobile_des`、`code`、`login_app_id`、`is_force_login_slave_account`、`support_enhance_type` |
| 授信登录 | `fuzzy_mobile`、`ticket`、`device_id`(= `MD5(android_id)`)、`support_enhance_type` |

`extra_vo` 在开关开启时把上一次登录响应的字段整体透传；对特定国家区号还会附加
`receive_whats_app_msg`。

`login_app_id` 取 `LoginInfo.LoginType.Phone.app_id`；`support_enhance_type` 由
`q.k2()` 或常量 `3` 决定。

## 6. 登录态与 Cookie

- native 保护名单（`xz2/j.java`）：`pdd_user_id`、`PDDAccessToken`、`pdd_user_uin`、
  `ETag`、`install_token`。`tz2/a.java`、`tz2/b.java`、`c13/b.java` 在合并/回写
  cookie 时对 `PDDAccessToken` 做特殊处理，并统计长度。
- 上报里的登录态：`mi0.a` 输出 `is_login` = `q1.c.X()`。
- 设备模块的登录辅助：`ms1/c.java`、`ms1/d.java` 在请求里带 `install_token`。

## 7. `53001` / `54001` 挑战

响应 `error_code == 54001` 时携带 `verify_auth_token`：

```java
// VerifyAuthTokenProcessor
public static String parseVerifyAuthTokenFromRespStr(String api, String respBody)
// 解析 VerifyAuthTokenModel{error_code, verify_auth_token}
// 命中 54001 且有值 → setToken(token)
```

- TTL：`HeraConfigKey.verify_auth_token_expire_mills` 默认 `1800000` ms（30 分钟）。
- 注入：`iv1/d.a(url)` 返回 `Pair("VerifyAuthToken", token)`，被
  `WrapperInterceptor` 在 `SpecialCode54001ServiceHolder.useVerifyAuthTokenFeature()`
  为真时写进请求；命中
  `RiskControl.black_list_verify_auth_token_apis`（默认 `[]`）的路径除外。
  路径先用 `rv1.h.b()` 归一化再比对。
- 广播：成功后发 `BotMessageConstants.RISK_CONTROL_VERIFY_AUTH_TOKEN`，
  `BaseActivity` 接收；实现类 `net_impl/specialcode/SpecialCode54001ServiceImpl`。
- 缓存：静态字段 `verifyAuthToken` + `lastRecvTokenTime`，加类锁。

## 8. 登录密钥（`getLoginKey`）

```java
public static String f(String str, int i14)      // 日志名 getLoginKey
    → private static native String glk(String str, int i14);
```

`glk` 属于 `libpdd_secure.so` 的 34 个导出之一；按调用图它**不触及 AES/Base64
常量表**（见 [algorithm.md](algorithm.md) 的导出→密码学映射表），属于纯逻辑
派生而非分组密码实现。输入为原始串与一个整型选择子，输出为字符串。
