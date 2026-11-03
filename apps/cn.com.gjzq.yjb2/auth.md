# 认证机制

## 1. 账号态载体

| 位置 | 载体 | 内容 |
|---|---|---|
| `shared_prefs/Token.xml` | 177 B 密文 | 登录票据 |
| `shared_prefs/TokenStatus.xml` | 129 B 密文 | 票据状态（有效/过期） |
| `shared_prefs/account_info.xml` | 545 B 密文 | 账号信息 |
| `shared_prefs/user_login.xml` | 81 B 密文 | 登录态标志 |
| `shared_prefs/auth.xml` | 817 B 密文 | 授权信息 |
| WebView Cookie `yjb-webapp-session` | Cookie | 金融账户体系会话（`@sinolink/http`） |
| WebView Cookie `YCOMMENTPHPSESSID` | Cookie | 评论子站会话 |
| `localStorage.STOCKINFORMATIONPHPSESSID` | localStorage | 资讯直连通道会话 |

五个 `shared_prefs` **整体密文存储**（非逐 key 加密），前 48 字节完全一致，
说明相同 XML 明文头进入了相同的块级处理结果；这不等同于已还原逐位置
keystream（见 [storage.md](storage.md) §3）。Cookie 与 localStorage 是
H5 层可见的明文会话载体，与 `shared_prefs` 属于两套互不相通的存储。

## 2. 三层登录入口

| 层 | 入口 | 用途 |
|---|---|---|
| native | `YJBExec(4000, {uKey/fundAccount/…})` | 金融登录，取 `uKey`；`4030 loginWithAccount` 代入资金账号，`4028 userLoginModal` 一键登录弹窗，`4015 userLogin`、`4016 userLogOut`、`4012 getLoginStatus` |
| H5 交易 | `loginLogic({loginArgs:{loginType}}, …)` | 普通(1)/信用(2)/担保(3) 三分支，见 §3 |
| H5 会话 | `@sinolink/login` 的 `checkLogin(type)` | 用 native 的 `uKey` 换 webapps 会话，见 §4 |

`NativeAPI.js` 的 `callNative`（偏移 `0x642`）是三者共同的桥：

```js
let callbackId = uuid().replace(/-/g,'');
if (functionIDClearOnDisappear.indexOf(funcId) === -1) callbackId = 'keep_' + callbackId;
// 1000/1001（交易/YJBAPI）不加 keep_ 前缀：页面 onDisappear 时统一清除
window.respCallbacks[callbackId] = respCallback;
window.yjbInterface.execute(funcId, JSON.stringify(param), callbackId);   // Android
window.webkit.messageHandlers.yjbInterface.postMessage({…});              // iOS
```

native 通过 `window.respFromNative(data, callbackId)`（偏移 `0xdc8`）回调，
按 `callbackId` 取出并删除回调。返回值一律是**字符串**，业务层自行
`JSON.parse`。登录相关分支用 `loginType` 数值区分：`1` 普通、`2` 融资融券、
`3` 担保，`'u'` 是「非金融用户体系 userId」、`'p'` 是 passportId。

## 3. 登录状态机（`fetchYJBAPI`）

`treasurybonds/build/index.js` 的 `loginLogic`（偏移 `0x2dc2c`）是一个
显式状态机，输入是调用点声明的 `loginType`，输出是「是否继续发业务请求」：

1. `nonSystemParam({keys:["loginStatus","tradeOvertime","passportId","userId","optionAccount"]})`
   取 native 侧当前态；
2. 用 `oLoginType = {1:[1,3], 2:[2,3], 3:[1,3]}` 判断「本次要求的登录类型」
   是否已被现有态满足（`1` 普通、`2` 信用、`3` 两者皆登录）；
3. 未满足 → `4002 clientJump` 跳登录页 / `4009 doneSuitability` 走适当性 →
   登录成功后回到第 1 步重试；
4. `overTimeType` 命中 `tradeOvertime` 时要求重新登录；
5. 满足后才执行 `_fetchYJBAPI`。

`loginType` 与 `overTimeBackTrack.rootType` 成对写死：
`loginType 1 → rootType 3`、`loginType 2/3 → rootType 4`。
`getLoginType` 另有一张映射表 `[[0,1],[1,2],[2,1],[15,16],["u","u"]]`，表示
业务侧「登录类型 0/1/2/15」到 native「1/2/1/16」的转换。

`fetchYJBAPI` 支持 `wantToLogin:true`：先跑 `loginLogic`，成功后
`.then(() => this._fetchYJBAPI(...))`，即**登录与业务请求在同一 Promise 链**，
业务请求不会在未登录时被发出。

## 4. Web 会话建立（`@sinolink/login`）

`ycomment/build/ycomment.js`（模块内含 `@sinolink/http` 与 `@sinolink/login`）：

```js
var CHANNELID = window.Login_CHANNELID || 'YPaJ9axolEpz';       // 0x12a781
function checkLogin(loginType) {                                 // 0x12d1e8
  if (await evolution.getAppId() !== 'yjb3.0') return false;      // 只在新容器生效
  const checkResult = await evolution.checkLogin(loginType, CHANNELID);
  if (checkResult.code !== 0) return false;
  const key = checkResult.result;                                // native 侧 uKey
  if (loginType === USER)     return userLogin({channelId: CHANNELID, userUKey: key});
  if (loginType === PASSPORT) return pidLogin({channelId: CHANNELID, uKey: key});
  … fundAccount / marginAccount 分支：
      instantToken = await …;  return fundAccountLogin({op_station, instant_token});
}
```

即 **native 持有真票据，H5 只是把它换成 webapps 会话 cookie**。登录/登出端点：

| 方法 | 路径（相对 `window.Login_API_URL`，未设置时用 `location.origin + /yjbwebservice/core`） |
|---|---|
| POST | `/login/user`（`userLogin`） |
| POST | `/login/passport`（`pidLogin`） |
| POST | `/login/multi`（`multiLogin`） |
| POST | `/login/fundAccount` |
| POST | `/login/marginAccount` |
| POST | `/login/qqstock` |
| GET | `/login/user`、`/login/fundAccount`、`/login/marginAccount`（状态查询） |
| POST | `/logout`、`/logout/{type}` |
| POST | `/yjbwebservice/core/logout/user`（AI 投顾单独一条） |
| GET | `/static/login-bridge.html`（App 内 WebView 中转页） |

`http = axios.create({ withCredentials: location.href.indexOf("http") > -1 })`，
会话 cookie 名为 `yjb-webapp-session`（`COOKIE_NAME`，偏移 `0xc860e` 附近）。
`appInit()` 依次 `checkLogin(PASSPORT)` 与 `checkLogin(USER)`，把
`passportId / fundAccount / marginAccount / userId` 全部校验一遍。

### 4.1 登录失效的自动补救

`@sinolink/http` 的响应拦截器是一个**可重放**中间件（`authMiddleware` +
`parseResponseMiddleware`）：

```js
var AUTH_ERROR_CODE = -1;                    // 0xc2dd9
var LoginThrottleTime = 1e3;
var defaultLoginAction = lodash_throttle(login.beginLogin, 1e3);

if (!directResponse && response.data.code === AUTH_ERROR_CODE)
    return LoginAndReTry(response, config);  // 0xc2e6e
```

`LoginAndReTry` 的语义是：服务端在未登录时返回 `code:-1` 且 `data` 是一个
**占位符字符串**，客户端把占位符翻译成登录类型再弹登录，成功后**重放原请求**：

| `response.data.data` | `LoginTypeEnum` | 含义 |
|---|---|---|
| `($userId)` | `u` | 非金融用户体系 |
| `($passportId)` | `p` | 扫码/passport |
| `($clientId)` `($fundAccount)` `($fundPassword)` `($fundAccountType)` | `1` | 普通（资金账号） |
| `($marginAccount)` `($marginPassword)` | `2` | 融资融券 |

登录成功后以 `{directResponse:true, notParseResponse:true}` 重放，
因此第二次响应不会被再次拦截（避免死循环）；登录取消或失败则原样返回
`code:-1` 响应给业务层。同一占位符机制也用于**参数占位**：请求体里写
`"($fundAccount)"` 之类的字面量，由 native 在真正发出前替换成真实值——
这也是本报告不在正文写出任何真实账号字段的原因。

## 5. SAS / SSO 票据链

融资融券模块 `margintrading/build/commons.js`（偏移 `0x192e5`）把票据链写成
**参数模板常量**，全部走 `functionId:1000` + `subSystemNo:519`：

```js
ssoDToken:  serviceName "sso.registerOnlineUsersSession"
            {app_id:"yjbapp", op_station:"($opStation)", passport_id:"",
             account:"($fundAccount)", cipher_content:"($fundPassword)", auth_type:1}
ssoIToken:  serviceName "sso.applyInstantToken"
            {op_station:"($opStation)", app_id:"yjbapp", durable_token:"", key_info:""}
ukey:       serviceName "passport.ukey.create"   {passport_id:"", channel_id:""}
sasDToken:  serviceName "sas.session.register"
            {app_id:"yjbapp", passport_id:"", account:"($fundAccount)",
             account_type:1, password:"($fundPassword)", expired_time:1800,
             op_station:"($opStation)"}
sasITokenV3:serviceName "sas.v3.register"
            {set_userId:"", expired_seconds:30, reg_instant:1}
sasIToken:  serviceName "sas.instantToken.apply" {durable_token:"", op_station:"($opStation)"}
sasSession: serviceName "sas.sessionInfo.query.durableToken" {durable_token:""}
```

语义链为：

```text
sas.session.register(账号+密码+op_station, 1800s) → durable_token
  → sas.instantToken.apply(durable_token)         → instant_token
  → 业务请求携带 instant_token / account_type / op_station
```

`sas.v3.register` 是另一条短时票据（30 s，`reg_instant:1`），供需要
「一次性注册即时令牌」的场景使用；`sas.sessionInfo.query.durableToken`
用于续期前查询现有 durable token。

回跳链路（`loginWithToken`，偏移 `0x12e53c`）从 URL 读
`instant_token` / `account_type` / `op_station`，命中则直接

```js
marginAccountLogin({op_station, account_type:1, instant_token})   // 信用
fundAccountLogin({op_station, instant_token})                     // 普通
```

即**从 native 或外部页面跳回 H5 时不再走密码**。`opStation` 在拼进跳转 URL
前会做 AES-128-ECB + Pkcs7 + Base64URL（`+`→`-`、`/`→`_`、`=`→`.`），
密钥是 `envConfig.opKey`（`commons.js` 偏移 `0xa980`，取值见 §6）。

## 6. 轻量级加密参数

H5 请求参数中出现 `u.opKey` 时，客户端以 **AES-128-ECB + Pkcs7** 加密：

```js
// BaseIndexNew.js:117-124（同 BaseIndex.js、paramConfig.js 的 APP_SECRET）
const key = CryptoJS.enc.Utf8.parse("g6j0z0q1w0e9bapp");   // 16 字节
CryptoJS.AES.encrypt(content, key, {
  mode: CryptoJS.mode.ECB,
  padding: CryptoJS.pad.Pkcs7,
}).toString();
```

同一常量在 `stockInformation/con-f/paramConfig.js:50` 以 `APP_SECRET` 出现，
并在 `paramConfigForConvert.js` / `paramConfigForFloorFund.js` /
`paramConfigForStockRotation.js` 重复出现；`margintrading/commons.js` 的
`encrypt()` 在 AES 之后再做 Base64URL 变体。另有第二个常量密钥
`z$B&E)H@McQfTjWn`，用于**解密服务端返回的账号标识**：

```js
// ycomment/build/ycomment.js 0x12af1a
function decrypt(value, key = "z$B&E)H@McQfTjWn") {
  var _key = cryptoJs.enc.Utf8.parse(key);
  return cryptoJs.AES.decrypt(value, _key, { mode: cryptoJs.mode.ECB })
                .toString(cryptoJs.enc.Utf8);
}
// bindInfo(): decrypt(data.userId) → 火山 finder userId
//             decrypt(data.clientId) → user_unique_id
//             decrypt(data.passportId) → pid
```

**这两把都是参数混淆，不是认证机制**：密钥随包分发，ECB 无 IV，同一明文在
同一 build 内产生相同密文。它们不构成对请求真实性的保证；服务端是否另验
签名不在静态可见范围内（`ai-adviser` 的 `apiSign` 只对**非 App 浏览器**
生效，见 §7）。

## 7. 其他票据相关面

- `serviceName.ycas.checkTokenFromAll`、`serviceName.ismp.thirdToken.query.userInfo`
  是第三方渠道 token 与 IMS 用户信息的查询入口；
- `ai-adviser/optional.js` 有一段**仅在非 App 环境生效**的签名：
  `apiSign = md5(apiKey + token + userId + "475830")`，`apiKey` 为
  `b6240c3a58de4ca99ac5f12764c97df1`，只在 `!window.isApp` 且 sessionStorage
  中存在 `yascAuth` 时给 GET 请求补 `userIdIms / req_from / token / apiKey /
  apiSign`——App 内走 native 票据，不需要该签名；
- `serviceName.passport.ukey.create` 之外，`4004 hybridGetPassport` 直接读
  native 内存里的 `passportId / userId / loginStatus`。

## 8. 未闭环项

| 项 | 状态 |
|---|---|
| `Token.xml` 密文内容 | 未知（结构已证实：整体密文 + 共享密钥流前 48 B） |
| native 侧票据有效期与刷新 | 未知（`expired_time:1800` / `expired_seconds:30` 是请求参数，实际策略在服务端） |
| `libencrypt.so` 的 `tztEncrypt` 输入输出 | 未知（`.text` 高熵，见 [obfuscation.md](obfuscation.md) §5） |
| `libndktest.so` RC4/SM4 的具体用途 | 结构已证实（符号存在），调用链未知 |
| `yjb-webapp-session` / `YCOMMENTPHPSESSID` 生命周期 | 未知 |
| 服务端 `code:-1` 之外的其他鉴权失败码 | 未知 |

## 9. 边界

未登录真实账号、未构造登录请求、未抓真实票据。以上全部为静态代码结论。
