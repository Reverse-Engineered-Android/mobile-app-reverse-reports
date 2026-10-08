# 认证机制

闲鱼使用淘宝账号体系（Login4Android + UCC 统一账号），登录态由 Cookie + 本地
SP 加密存储 + MTOP 请求头三层表达。所有结论来自静态代码与设备端只读核对。

## 1. 登录体系

- 实例绑定：`MtopAccountSiteUtils.bindInstanceId("xianyu")`，
  淘宝实例 `bindInstanceId("taobao")`。
- 远程登录实现：`RemoteLogin.setLoginImpl(mtopInstance2, UccRemoteLogin.getUccLoginImplWithSite("taobao"))`；
  `InnerSignImpl.initMiddleTier` 内 `RemoteLogin.getLogin(mtopInstance)`
  通过 `key_login_module` 通知中间层登录模块已就绪。
- 账号域接口（静态存在）：
  | 接口 | 用途 |
  | --- | --- |
  | `mtop.com.taobao.mlogin` | 登录 |
  | `mtop.com.taobao.mloginservice.unifyssotokenlogin` | SSO token 登录 |
  | `mtop.com.taobao.ssologin.appinfoget` | SSO 应用信息 |
  | `mtop.alibaba.ucc.oauth` / `native.oauth` / `bind.pure.oauth` | 第三方 OAuth 绑定 |
  | `mtop.alibaba.ucc.get.authinfo` / `authcodet.validate` | 授权码校验 |
  | `mtop.havana.register.sdk.*` | 注册（验证码/邮箱/昵称/SNS） |
  | `mtop.alibaba.ucc.taobao.apply.usertoken` / `upgrade.account` | 令牌申请与账号升级 |

- 登录信息读取接口 `LoginInfo`：`getUserId()`、`getUserIdByLong()`、`getSid()`、
  `getNick()`、`getHeadPicLink()`、`isLogin()`、`isMe(...)`、`isPlayboy()`。

## 2. 会话 Cookie

`com.taobao.login4android.session.SessionManager` 负责 Cookie 注入、持久化与失效广播：

- 写入 `CookieManager`（或自定义 `CookieManagerProxy`）。
- 关键字段（`SessionConstants`）：`sid`、`subSid`、`uid`、`userId`、`nick`、
  `loginPhone`、`email`、`ssoToken`、`auto_login`、`sessionKey`、
  `sessionExpiredTime`、`loginSite`、`injectCookieNew/Old/Count`、
  `havanaSsoTokenExpiredTime`、`oldnick`、`oldsid`、`olduserid`、
  `uninstallReloginFlag2`、`sessionDisastergrd`。
- Cookie 字符串中显式识别 `cookie2`（`setCookie` 记录 `cookie2` 并上报
  `setCookieException`）。
- `SessionManager` 对 `.taobao.com` 域维护 `unb` / `munb`：清除会话时删除
  `unb`；加载登录 Cookie 时读取二者并与当前 `userId` 比对，防止旧账号 Cookie
  残留。
- `LoginSwitch.SGCOOKIE = "sgcookie"`，由 Orange 配置组 `login4android` 控制是否下发。
- 失效广播：`NOTIFY_CLEAR_SESSION`、`NOTIFY_CLEAR_SESSION_COOKIES`、
  `NOTIFY_SESSION_VALID`。
- **静态事实**：本 APK 的 DEX 中不存在 `_m_h5_tk` / `_m_h5_tk_enc` / `_tb_token_`
  字面量（这三个是 h5api/H5 页面 Cookie）。原生 MTOP 调用不使用 h5_tk 令牌，
  而是使用 `x-sign`（见 [network.md](network.md)）与 `sid`/`uid` 头。

## 3. 请求级认证头

`ClientHeaderInterceptor` 在每次发送前补齐身份与设备字段：

| 头 | 值来源 |
| --- | --- |
| `x-custom-cache-uid` | `LoginUtil.getUserId()` |
| `umid` | `FishUmidHelper.getSecurityToken()`（UMID 安全令牌） |
| `x-umt` | 同上，走 MTOP 头映射（`InnerNetworkConverter`） |
| `x-sid` / `x-uid` | XState 中的会话与用户 |
| `oaid` / `aliOaid` | OAID 回调 |
| `imei` | `fish_imei` SP |

`needLogin=true` 的接口（**55** 个）在 MTOP 层校验登录态，未登录时由
`RemoteLogin` 触发登录流程；`BaseApiProtocol.CallbackThread` 与
`ApiBusiness.needLogin` 决定是否强制。

## 4. 设备身份

- 首次启动写 `fish_device_activate` SP 的 `fish_firstOpen_flag`；
  `first_open` 头与 `mtop.taobao.idle.device.report` 的 `first_open` 字段共享该标志。
- `DeviceActivateUtils.reportInfo` 上报 `{ first_open, umidToken, imei, oaid }`
  （UT 事件 `app_device_activate`，事件 ID 19999）。
- `mtop.taobao.idle.device.report/1.0`（`needWua=true`）字段：
  `action, brand, osVersion, processUuid, oaid, oaid2, gzuOaid, aliOaid, first_open, extendArgs`。
  `oaid`/`gzuOaid` 取同一 `FishOaid.getOaidFromCache`，`oaid2` 取荣耀兼容 OAID，
  `aliOaid` 取阿里自研 OAID；`extendArgs` 可含 `userAgent`。
- 登录成功后才激活 CCRC 行为风控：`CcrcManager.activate()` 用
  `pid = <userId> + "_" + ...` 构造会话标识（见 [risk.md](risk.md)）。

## 5. 云 ID / 设备指纹

- UTDID（`x-utdid`）：UT SDK 生成，`UTMini.getInstance().start(app, "21407387", UTDevice.getUtdid(app), ttid)`。
- UMID（`x-umt`/`umid`）：`IUMIDComponent.initUMID(appKey, env, authCode, listener)`，
  成功后 `XState.setValue(instanceId, "umt", token)`。
- OAID：`OpenDeviceId.getOAID(app)` 与 `FishOaid`（多厂商兼容、荣耀/华为兼容）。
- SecurityGuard 静态数据存储与动态数据存储（`getStaticDataStoreComp`、
  `getDynamicDataStoreComp`）承载设备侧密钥/证书与加密票据
  （如 `network_ssl_ticket` SP 用于 SSL ticket 缓存）。

## 6. 会话失效与重登录

- `sessionExpiredTime` + `sessionDisastergrd` 双保险；
  `mtop` 返回 `FAIL_SYS_SESSION_EXPIRED` / `NEED_RELOGIN` 时触发重新登录。
- `SecurityInterceptor` 识别的登录/风控类返回码：
  `NEED_RELOGIN`、`NEED_REAL_VERIFY`、`RISK_USER_VERIFY`、
  `USER_NEED_REALNAME_VERIFY`、`USER_NEED_ALIPYA_BIND`、`FAIL_BIZ_FORBIDDEN`。
- 未登录浏览：`FakeLoginActivity`（游客模式）与 `NeedNotSSOModel` 提供受限浏览路径。

## 7. 证据等级

- **已验证**：Cookie 字段集、失效广播、请求头来源、`needLogin` 接口计数、
  `device.report` 字段与 `first_open` 标志。
- **结构已证实**：UMID/UTDID/OAID 的生成入口与注入点，具体字节算法在加固库内。
- **假说**：服务端对同一设备多次 `device.report` 的去重/关联策略。
