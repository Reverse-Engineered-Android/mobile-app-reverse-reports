# 认证机制

## 1. 调查边界

本页只做静态分析：未登录任何账号，未获取或使用任何真实 token、Cookie 或
会话，未触发登录/短信/扫码/人脸流程。所有 token 名称与字段来自 DEX 中的
类定义、常量与调用点。

大麦的认证不是单一体系，而是四层叠加：

| 层 | 归属 | 凭据 |
|---|---|---|
| 账号层 | 淘宝/阿里妈妈统一账号（Havana / mlogin / UCC） | `sid`、`subSid`、`uid`、`autoLoginToken`、`havanaSsoToken` |
| 开放授权层 | 三方站（大麦作为 UCC site） | `accessToken`、`authCode`、`openId`、`bindToken`、`openSid` |
| 设备信任层 | SecurityGuard | `umidToken`、`wua`、`apdId`、`deviceToken{key,salt}` |
| 无密码层 | Passkey / 生物识别 / 人脸 | `passkey.*`、`biometricId`、FaceService |

## 2. MTOP 会话凭据

### 2.1 `sid` 与多账号槽

`InnerProtocolParamBuilderImpl.buildParams` 里：

```java
map3.put("sid", mtop2.getMultiAccountSid(mtopNetworkProp2.userInfo));
map3.put("ttid", mtopNetworkProp2.ttid);
map3.put("deviceId", mtop2.getDeviceId());
```

（`mtopsdk/mtop/protocol/builder/impl/InnerProtocolParamBuilderImpl.java:205-207`）

`Mtop.getMultiAccountSid(userInfo)` / `getMultiAccountUserId(userInfo)`
（`mtopsdk/mtop/intf/Mtop.java:473-487`）：

```java
public String getMultiAccountSid(String str) {
    String str2 = this.instanceId;
    if (StringUtils.isBlank(str)) str = "DEFAULT";
    return XState.getValue(StringUtils.concatStr(str2, str), "sid");
}
```

即 XState 键是 `<instanceId><site>`，缺省站为 `"DEFAULT"`；`uid` 同理。
`CommonBiz.java:1337` 的 `MtopAccountSiteUtils.bindInstanceId(Mtop.Id.INNER, "taobao")`
把大麦内实例默认绑定到 `taobao` 站，所以业务请求用的会话键为
`<instanceId>taobao`。

`sid` 随后被 `InnerNetworkConverter.headerConversionMap` 映射为 `x-sid`
（`mtopsdk/mtop/protocol/converter/impl/InnerNetworkConverter.java:18`），
并在 `buildRequestHeaders` 中按 URL 编码写入头。

### 2.2 Cookie 注入

`com/taobao/login4android/session/SessionManager.java:395`：

```java
private void setCookie(String str, String str2) {
    ...
    CookieManager.getInstance().setCookie(str, str2);
    ...
}
```

`:348` 初始化 `this.storage = context.getSharedPreferences(USERINFO, 4)`，
`USERINFO = "userinfo"`（`:51`）。`:366` 还有一处固定清理：
`removeSingleCookie("unb", ".taobao.com")`，说明会话 Cookie 的域是
`.taobao.com`。

`CookieManagerProxy` / `LoginCookieUtils.getCustomWebViewClass()`
（`:399-405`）为新旧 WebView 各注册一份 Cookie 写入路径，因此业务 H5
与原生请求共享同一份 `.taobao.com` Cookie 视图。

### 2.3 会话落盘

`SessionManager` 的字段清单（`:57-88`）就是会话在内存中的完整范围：

```text
extJson mAutoLoginToken mBiometricId mCookieStr mDomainListStr mEcode mEmail
mHavanaSsoTokenExpiredTime mHeadPicLink mInjectCookieCount mLoginPhone
mLoginSite mLoginTime mNick mOldEncryptedUserId mOldNick mOldSid mOldUserId
mSessionDisastergrd mSessionExpiredTime mShowNick mSid mSubSid mSuccessTip
mUidDigest mUserId mUserName
```

持久化键名在 `com/taobao/login4android/session/constants/SessionConstants.java`：

| 常量 | 键名 | 内容 |
|---|---|---|
| `SID` | `sid` | 会话 ID |
| `SUBSID` | `subSid` | 子站会话 |
| `UID` / `USERID` / `USERNAME` | `uid` / `userId` / `username` | 账号标识 |
| `AUTO_LOGIN_STR` | `auto_login` | 自动登录 token |
| `SSOTOKEN` | `ssoToken` | SSO token |
| `OneTimeTOKEN` | `havanaSsoToken` | Havana 单次 SSO token |
| `HAVANA_SSO_TOKEN_EXPIRE` | `havanaSsoTokenExpiredTime` | 上述 token 过期时间 |
| `SESSION_EXPIRED_TIME` | `sessionExpiredTime` | 会话过期 |
| `SESSION_KEY` | `sessionKey` | 会话密钥 |
| `SESSION_DISASTERGRD` | `sessionDisastergrd` | 容灾会话 |
| `OLDSID` / `OLDUSERID` / `OLDNICK` / `OLD_ENCRYPTED_USERID` | `oldsid` / `olduserid` / `oldnick` / `old_encrypted_userid` | 历史账号快照 |
| `ECODE` | `ecode` | 错误码/校验码 |
| `EMAIL` / `LOGIN_PHONE` / `NICK` / `SHOW_NICK` / `TAOBAO_NICK_NAME` | 同名语义 | 资料 |
| `HEAD_PIC_LINK` | `headPicLink` | 头像 |
| `LOGIN_SITE` / `LOGIN_TIME` | `loginSite` / `loginTime` | 站点与时间 |
| `BIOMETRIC` | `biometricId` | 生物识别绑定 ID |
| `COMMENT_TOKEN_USED` | `commentTokenUsed` | 评论 token 使用标记 |
| `EXT_JSON` | `loginServiceExt_json` | 登录服务扩展 |
| `EVENT_TRACE` | `eventTrace` | 事件追踪 |
| `INJECT_COOKIE_COUNT` | `injectCookieCount` | 注入 Cookie 条数 |
| `INJECT_COOKIE_NEW` / `INJECT_COOKIE_OLD` | `injectCookieNew` / `injectCookieOld` | 注入 Cookie 两份快照 |
| `INJECT_External_H5_COOKIE` | `injectExternalH5Cookie` | 外部 H5 Cookie |
| `SSO_DOMAIN_LIST` | `ssoDomainList` | SSO 域列表 |
| `ALIPAY_LOGIN_ID` | `alipayLoginId` | 支付宝登录标识 |

Cookie 快照单独写文件（`com/taobao/login4android/utils/FileUtils.java:68`
`writeFileData` / `:16` `readFileData`），内容为
`encode(JSON.toJSONString(this.mCookie))`：

```java
FileUtils.writeFileData(this.mContext, SessionConstants.INJECT_COOKIE_NEW,
        encode(JSON.toJSONString(this.mCookie)));       // :1728
FileUtils.writeFileData(this.mContext, SessionConstants.INJECT_COOKIE_OLD, "");  // :1729
FileUtils.writeFileData(this.mContext, SessionConstants.INJECT_External_H5_COOKIE,
        encode(JSON.toJSONString(this.mCookie)));       // :1726
```

`encode` 即 SecurityGuard 的动态数据加密（见 §5）。`mCookie` 是
`CopyOnWriteArrayList<LoginCookie>`（`:87`），元素为拆解后的
`name/value/domain/path/expires/secure`。

`SessionParams`（`com/taobao/login4android/session/SessionParams.java`）
是上述字段的可序列化镜像，用于跨进程/跨页传递：`domainList`、
`mAutoLoginToken`、`mEcode`、`mEmail`、`mHeadPicLink`、`mLoginPhone`、
`mNick`、`mOldEncryptedUserId`、`mOldNick`、`mOldSid`、`mOldUserId`、
`mSessionDisastergrd`、`mShowNick`、`mSid`、`mSubSid`、`mUidDigest`、
`mUserId`、`mUserName`。

### 2.4 会话同步广播

`SessionManager` 常量（`:43-54`）：

```java
public static final String CHANNEL_PROCESS = ":channel";
private static final String CLEAR_SESSION_ACTION = "NOTIFY_CLEAR_SESSION";
private static final String CLEAR_SESSION_COOKIES_ACTION = "NOTIFY_CLEAR_SESSION_COOKIES";
public static final String CURRENT_PROCESS = "PROCESS_NAME";
public static final String NOTIFY_SESSION_VALID = "NOTIFY_SESSION_VALID";
public static final String USERINFO = "userinfo";
```

`:106` 注册的 `BroadcastReceiver` 监听这些 action，`:125` 从
`intent.getStringExtra("session")` 接收序列化会话，`:111` 读 `PROCESS_NAME`
判断来源进程。登录/登出因此会跨进程广播会话与 Cookie 失效。

## 3. Open 授权层（UCC）

### 3.1 `convertAuthCodeToAccessToken` 调用

`com/ali/user/open/tbauth/task/RpcRepository.java:33`：

```java
public static void getAccessTokenWithAuthCode(String str, String str2, RpcRequestCallbackWithCode cb) {
    RpcRequest rpcRequest = new RpcRequest();
    rpcRequest.target = "mtop.alibaba.ucc.convertAuthCodeToAccessToken";
    rpcRequest.version = "1.0";
    JSONObject jSONObject = new JSONObject();
    jSONObject.put("appName", ((StorageService) AliMemberSDK.getService(StorageService.class)).getAppKey());
    jSONObject.put(SignConstants.MIDDLE_PARAM_AUTHCODE, str);
    jSONObject.put("site", str2);
    rpcRequest.addParam("convertAccessTokenRequest", jSONObject);
    ((RpcService) AliMemberSDK.getService(RpcService.class))
        .remoteBusiness(rpcRequest, ConvertAuthCodeToAccessTokenData.class, cb);
}
```

返回值结构 `com/ali/user/open/tbauth/task/ConvertAuthCodeToAccessTokenData.java`：

```java
public String accessToken;   public String authCode;
public String bindToken;     public DeviceTokenRO deviceToken;   // {key, salt}
public String hid;           public String openId;
public String openSid;
```

`RpcPresenter`（`com/ali/user/open/tbauth/task/RpcPresenter.java:128`）
在 `onSuccess` 中先落盘设备 token，再组装 `Session`：

```java
RpcPresenter.saveDeviceToken(convertAuthCodeToAccessTokenData);
...
Session session = new Session();
session.openId        = convertAuthCodeToAccessTokenData2.openId;
session.bindToken     = convertAuthCodeToAccessTokenData2.bindToken;
session.topAccessToken= convertAuthCodeToAccessTokenData2.accessToken;
session.topAuthCode   = convertAuthCodeToAccessTokenData2.authCode;
session.openSid       = convertAuthCodeToAccessTokenData2.openSid;
loginCallback.onSuccess(session);
```

`:276` 的 `saveDeviceToken`：

```java
DeviceTokenAccount deviceTokenAccount = new DeviceTokenAccount();
deviceTokenAccount.site     = "taobao";
deviceTokenAccount.tokenKey = convertAuthCodeToAccessTokenData.deviceToken.key;
deviceTokenAccount.openId   = convertAuthCodeToAccessTokenData.openId;
deviceTokenAccount.hid      = convertAuthCodeToAccessTokenData.hid;
DeviceTokenManager.getInstance().putDeviceToken(deviceTokenAccount,
        convertAuthCodeToAccessTokenData.deviceToken.salt);
```

`validateAuthCode` 分支（`:290`）额外写入 `session.hid`。

`com/ali/user/open/session/Session.java` 的会话有效性判定：

```java
public boolean isSessionValid() {
    return (TextUtils.isEmpty(this.sid) || TextUtils.isEmpty(this.hid)
         || this.loginTime == 0 || this.expireIn == 0
         || System.currentTimeMillis() / 1000 >= this.expireIn) ? false : true;
}
```

### 3.2 设备 token 的双段存储

`com/ali/user/open/device/DeviceTokenManager.java`：key（`tokenKey`）与
salt（`str`）**分开存**。

```java
private static final String DEVICE_TOKEN_ACCOUNT = "device_token";

public void putDeviceToken(DeviceTokenAccount deviceTokenAccount, String str) {
    if (!ConfigManager.getInstance().isSaveHistoryWithSalt()
            || ((StorageService) ...).saveSafeToken(deviceTokenAccount.tokenKey, str)) {
        ((StorageService) ...).putDDpExValue("device_token", toJSONString(deviceTokenAccount));
    }
}

public DeviceTokenAccount getDeviceToken() {
    return parseObject(((StorageService) ...).getDDpExValue("device_token"));
}
```

- salt 走 `saveSafeToken(tokenKey, salt)`（SecurityGuard 安全存储）；
- 账号元数据走 `putDDpExValue("device_token", json)`（动态加密 KV）。
- `toJSONString`（`:46`）写 `openId`、`tokenKey`、`site`、`userId`（取
  `deviceTokenAccount.hid`）、`t`（写入时刻毫秒）。
- `parseObject`（`:33`）读回时把 `"userId"` 还原为 `hid`。
- 语义印证：`saveSafeToken(tokenKey, salt)` 只有在
  `isSaveHistoryWithSalt()` 为真时才要求成功——即“带 salt 保存历史”是一个
  云端开关控制的加固路径。

`DeviceTokenAccount` 字段：`hid`、`openId`、`site`、`t`、`tokenKey`。

### 3.3 `InternalSession` 与历史账号

`com/ali/user/open/session/InternalSession.java`：

```text
sid subSid openId openSid userId loginId mobile email nick avatarUrl
autoLoginToken bindToken loginTime expireIn loginServiceExt
topAccessToken topAuthCode topExpireTime
deviceTokenKey deviceTokenSalt
externalCookies[] otherInfo(Map)
```

`SessionManager`（`com/ali/user/open/service/impl/SessionManager.java:47-51`）：

```java
public static final String COOKIE_KEY_STOEKN = "P_sck";
public static final SessionManager INSTANCE = new SessionManager();
public String internalSessionStoreKey = "internal_session";
public String internalSessionMapKey   = "internal_session_list";
```

`buildYoukuExt`（`:68`）解析 `youkuExt.content.sessionInfo` 里的
`cookieExpireTime`、`ptoken`、`stoken`，以及 `userInfo` 的 `ytid`、`yid`、
`tid`、`uid`、`nickname`、`avatar`、`loginEmail` —— 即大麦客户端同时持有
优酷账号视图（票务与视频生态共账号）。

历史账号与指纹由 `com/ali/user/mobile/security/SecurityGuardManagerWraper.java`
管理（1034 行）：

```java
public static final String FINGER_LIST = "finger_list";                    // :50
private static final String HISTORY_LOGIN_ACCOUNTS = "aliusersdk_history_acounts"; // :51
public static final String LOGIN_IS_MORE_ACCOUNT = "is_more_act";          // :52
private static final String SESSION_LIST = "aliusersdk_session_lists";     // :53
private static final long THREE_MONTH_SECOND = 7776000;                     // :55
```

- 三个键都是**文件**（`FileUtils.readFileData(context, <key>)` 读写，见
  `:118`、`:438`），内容经 `encode`/`decrypt`（动态加密）包裹。
- `filterThreeMonthHistoryAccounts`（`:198`）用 `7776000` 秒（90 天）裁剪
  历史账号列表，即客户端只保留最近三个月。
- `mask(str)` = `hiddenExceptPreAndPost(str, 3, 2)`（`:960` / `:499`），
  展示时保留前 3 后 2 位。
- `HistoryAccount`（`com/ali/user/mobile/rpc/HistoryAccount.java`）字段：
  `accountId alipayCrossed alipayHid autologinToken biometricId clickLoginType
  email hasPwd headImg isVip loginAccount loginPhone loginSite loginTime
  loginType mobile nick nickName srcLoginType tokenKey userId userInputName
  vipExpireTime` —— 含手机号、邮箱、昵称、头像、VIP 到期、登录类型与
  自动登录 token。
- `FingerInfo{key,value,loginTime}` / `FingerList{list}`
  （`com/ali/user/mobile/model/`）是登录风控指纹的最小单元，`saveFinger`
  （`:985`）按白名单 `arrayList` 过滤后写回 `finger_list` 文件。
- `LoginHistory{accountHistory:List<HistoryAccount>, index}` /
  `filterThreeMonthHistoryAccounts` 的内存缓存 `mLoginHistory` +
  `hadReadHistory`（`:56-59`）。

### 3.4 `SessionModel`

`com/ali/user/mobile/rpc/login/model/SessionModel.java` 继承
`AliUserResponseData`，只额外加 `showLoginId` 与 `site`。
`AliUserResponseData` 是登录响应母体：

```text
alipayHid autoLoginToken cookies[] ecode email expires extendAttribute
externalCookies[] havanaId havanaSsoToken havanaSsoTokenExpiredTime
headPicLink loginPhone loginServiceExt loginTime nick sid ssoToken subSid
successTips uidDigest userId
```

## 4. 登录接口面

### 4.1 Havana / mlogin / UCC

| 族 | 接口 |
|---|---|
| Havana | `mtop.alibaba.havana.login.autologin`、`.mlogintokenlogin`、`.signforalipaysnslogin`、`mtop.alibaba.havana.tbmpc.thirdPart.bindAlipay`、`mtop.alibaba.havanaappiv.nonloginverify.url`、`.nonloginverify.url.withenv`、`.verify.url` |
| Havana 注册 | `mtop.havana.register.sdk.register`、`.sns.register`、`.verdor.register`、`.direct.register`、`.checkcode.send`、`.email.checkcode.send`、`.country.get`、`.nick.check.and.recommend`、`mtop.havana.register.queryregisterlink` |
| Havana 其它 | `mtop.taobao.havana.mlogin.alipayaso`、`.logout`、`.youkuLegacy.refreshCookie`、`.verifyCookie`、`mtop.havana.mpc.thirdBind.management.bindThirdPartId`、`.changeBindThirdPartId` |
| mloginService | `mtop.mloginService.login`、`.mloginTokenLogin`、`.ssoLogin`、`mtop.com.taobao.mloginService.appLaunch`、`.appOldAutoLogin`、`.getAppLaunchInfo`、`.prelogininfo`、`mtop.com.taobao.mloginService.unifyssotokenlogin` |
| 扫码登录 | `mtop.taobao.commonuse.mloginService.genQrCode`、`.scanedQrCode`、`.confirmedQrCode`、`.canceledQrCode`、`.qrcodelogin` |
| 短信/Sim | `mtop.taobao.mloginService.smsSend`、`.smsSend.nick`、`.smsLogin`、`.smsLogin.nick`、`.simLogin`、`.simLogin.userinput`、`.touristlogin` |
| 多站点 | `mtop.taobao.alibabaMLoginService.login`、`.logout`、`.autologin`、`.mloginTokenLogin`、`.snsLogin`、`.applySsoToken` |
| UCC | `mtop.alibaba.ucc.oauthLogin`、`.native.oauthLogin`、`.login.continue`、`.grow.continue.login`、`.unbind`、`.bind.change`、`.bind.identify`、`.bind.pure.oauth`、`.bind.token.authcode`、`.bindByNativeAuth`、`.nointeraction.bind`、`.nointeraction.bind.info.query`、`.nointeraction.unbind`、`.nointeraction.update.oauth`、`.recommend.bind`、`.taobao.apply.usertoken`、`.upgrade.account`、`.get.authinfo`、`.getalipaysauthurl`、`.getLocalSiteAuthUrl`、`mtop.alibaba.alsc.ucc.querybind.oauthlogin`、`mtop.alibaba.alsc.ucc.getLocalSiteAuthUrl` |
| 授权码 | `mtop.alibaba.ucc.authcodet.validate`、`mtop.alibaba.ucc.convertAuthCodeToAccessToken`、`mtop.alibaba.ucc.localauthurl.get.bytoken` |
| 三方绑定 | `mtop.alibaba.ucc.grow.url.get`、`.grow.url.get.no.login`、`mtop.alibaba.ucc.bind.identify` |

大麦自有登录入口 `cn/damai/login/`：

- `LoginManager`（349 行）声明登录来源常量（`:37-45`）：
  `LOGIN_FROM_NORMAL=100`、`LOGIN_FROM_ALIPAY=101`、`LOGIN_FROM_TAOBAO=102`、
  `LOGIN_FROM_WEIBO=103`、`LOGIN_FROM_QQ=104`、`LOGIN_FROM_WEIXIN=105`、
  `LOGIN_FROM_AUTO_LOGIN=110`；广播 `action_do_login`、
  `action_login_first_confirm_cancel`。
- 授权 token 请求 `cn/damai/login/authlogin/req/GetAuthorizationTokenRequest.java`：
  API `mtop.damai.wireless.open.api.authorize` v`1.0`，
  **`getNeedEcode() = true`、`getNeedSession() = true`**；
  字段 `action`（`"authorization"` / `"autoLogin"`）、`feature`、`siteCode`、
  `target`；响应 `AuthorizationModel{token}`。
- 三方会话 `cn/damai/login/authlogin/req/ThirdPartyAuthRequest.java`：
  API `mtop.damai.wireless.third.account.session.get` v`1.0`，
  needEcode/needSession 均 true；`operator` 取值 `"authorizeAndLogin"` /
  `"login"`，另有 `feature`、`target`。
- 响应 `ThirdSessionModel{bizType, cookies:ArrayList<CookieBean>, extra, hasAllow}`，
  其中 `Extra{authorizePageInfo:AuthInfoBean}`；
  `CookieBean{domain,expires,name,path,secure,value}` 并能
  `toCookieString()`（`"name=value;"` + 可选
  `"domain=<d>;"` + `"path=<p>;"`）—— 说明三方登录返回的是**服务端下发的
  Cookie 数组**，客户端只做拼接，不自行签发。

### 4.2 登录响应处理与自动登录

`com/ali/user/open/core/util/MtopApiHelper.java:18-20` 把逻辑名映射为 api：

```java
put(ApiConstants.ApiName.AUTO_LOGIN,  new Pair("mtop.alibaba.havana.login.autologin", "1.0"));
put(ApiConstants.ApiName.TOKEN_LOGIN, new Pair("mtop.alibaba.havana.login.mlogintokenlogin", "1.0"));
```

`RpcRepository.loginByIVToken`（`:49`）与 `autoLogin`（`:89`）构造的参数：

```java
// token 登录
jSONObject.put("site", i);
jSONObject.put("appName", <StorageService.getAppKey()>);
jSONObject.put("token", str);
jSONObject.put("t", "" + System.currentTimeMillis());
jSONObject.put("scene", str2);
jSONObject.put("sdkVersion", KernelContext.getSdkVersion());
jSONObject.put(TbAuthConstants.CLIENT_IP, CommonUtils.getLocalIPAddress());
jSONObject.put("ext", jSONObject2);           // 含 h5QueryString
jSONObject3.put("umidToken", <StorageService.getUmid()>);

// 自动登录
jSONObject.put("token", sessionManager.getInternalSession().autoLoginToken);
jSONObject.put("deviceTokenSign", strSignMap);
jSONObject.put("deviceTokenKey", str2);
jSONObject.put(ApiConstants.ApiField.HID, historyAccountFindHistoryAccount.userId);
```

要点：

- 登录请求上行**客户端内网/公网 IP**（`CommonUtils.getLocalIPAddress()`）。
- 自动登录同时提交 `autoLoginToken` + `deviceTokenKey` + `deviceTokenSign`
  + `hid`，即“设备信任 + 账号历史”三者联合校验。
- `umidToken` 在 `ext` 里单独上行一次。

### 4.3 登出

```java
rpcRequest.target = ApiConstants.ApiName.LOGOUT;
jSONObject.put("appKey", <getAppKey()>);
jSONObject.put("sid", SessionManager.INSTANCE.getInternalSession().sid);
jSONObject.put("ip", CommonUtils.getLocalIPAddress());
```

（`RpcRepository.java:144-151`）登出同样上报 IP 与 `sid`。

## 5. SecurityGuard 在认证中的角色

`SecurityGuardManagerWraper` 的两个数据构造器：

```java
public static WSecurityData buildRPSecurityData() {          // :102
    WSecurityData wSecurityData = new WSecurityData();
    WUAData rpwua = getRPWUA();
    if (rpwua != null) { wSecurityData.wua = rpwua.wua; wSecurityData.t = rpwua.t; }
    wSecurityData.apdId = AlipayInfo.getInstance().getApdid();
    wSecurityData.umidToken = AppInfo.getInstance().getUmidToken();
    return wSecurityData;
}

public static WSecurityData buildWSecurityData(WUAData wUAData) {   // :964
    wSecurityData.wua = wUAData.wua;
    wSecurityData.t = wUAData.t;
    wSecurityData.apdId      = AlipayInfo.getInstance().getApdid();
    wSecurityData.umidToken  = AppInfo.getInstance().getUmidToken();
    wSecurityData.appStore   = DataProviderFactory.getDataProvider().getTTID();
    wSecurityData.osName     = lo0.ANDROID;
    wSecurityData.osVersion  = Build.VERSION.getRELEASE();
    wSecurityData.deviceModel= Build.getMODEL();
    wSecurityData.deviceBrand= Build.getMANUFACTURER();
    wSecurityData.deviceName = model;
    wSecurityData.screenSize = <widthPixels> + "x" + <heightPixels>;
    return wSecurityData;
}
```

`WSecurityData` 字段：`apdId appStore deviceBrand deviceModel deviceName
osName osVersion screenSize t umidToken wua` —— 这是**登录风控的设备画像
载体**，每次登录/自动登录随请求上行。

`getWUA()`（`:466`）取 WUA 的两条路径：

```java
String securityBodyOpen = getSecurityBodyOpen(jCurrentTimeMillis, appkey);
if (TextUtils.isEmpty(securityBodyOpen)) {
    securityBodyOpen = securityBodyComp.getSecurityBodyData(strValueOf, appkey);
}
```

`getSecurityBodyOpen`（`:397`）用新版组件：

```java
((ISecurityBodyComponent) getSecurityGuardManager().getInterface(ISecurityBodyComponent.class))
    .getSecurityBodyDataEx(String.valueOf(j), str, "", null, 4, EnvUtil.getAlimmsdk_env());
```

flag 固定为 `4`（`SecurityUtils.GENERAL_WUA_FLAG`），即**通用 WUA**，
与 MTOP 主链路用 `0`（`DEFAULT_WUA_FLAG`）和 `8`（`MINI_WUA_FLAG`）
是不同输入面。

`getRPWUA()`（`:390`）走人脸服务：

```java
return new WUAData(DataProviderFactory.getDataProvider().getAppkey(),
        String.valueOf(System.currentTimeMillis()),
        ((FaceService) ServiceFactory.getService(FaceService.class)).getDeviceInfo());
```

即 RP（风险画像）WUA 由 `FaceService.getDeviceInfo()` 产出。

### 5.1 动态数据加密（落盘保护）

```java
public static String encode(String str) {                    // :186
    IDynamicDataEncryptComponent c = getSecurityGuardManager().getDynamicDataEncryptComp();
    if (c != null) {
        String s = c.dynamicEncryptDDp(str);
        return TextUtils.isEmpty(s) ? str : s;
    }
    return str;
}

public static String decrypt(String str) {                   // :149
    if (TextUtils.isEmpty(str)) return str;
    IDynamicDataEncryptComponent c = getSecurityGuardManager().getDynamicDataEncryptComp();
    return c != null
        ? (str.length() <= 4 || str.charAt(3) != '&')
              ? c.dynamicDecrypt(str)
              : c.dynamicDecryptDDp(str)
        : str;
}
```

`decrypt` 用第 4 个字符是否为 `'&'` 区分两种密文前缀格式
（`dynamicDecrypt` vs `dynamicDecryptDDp`），说明历史上存在两代
DDp 密文格式。落盘的历史账号、会话列表、注入 Cookie 全部经此包裹。

### 5.2 生物识别密钥

`com/ali/user/mobile/rpc/safe/AES.java`：

```java
public static final String ANDROID_KEYSTORE = "AndroidKeyStore";
public static final String MY_KEY = "com.ali.user.sdk.fingerprint";
public static final String BLOCK_MODE = "CBC";
public static final String PADDING = "PKCS7Padding";

@TargetApi(23)
public void createKey() throws Exception {
    KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE);
    keyGenerator.init(new KeyGenParameterSpec.Builder(MY_KEY, 3)
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setUserAuthenticationRequired(true)
            .build());
    keyGenerator.generateKey();
}

public Cipher getCipher(boolean z) throws Exception {
    Key key = getKey();
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
    cipher.init(3, key);      // 3 = ENCRYPT_MODE | DECRYPT_MODE
    LoginStatus.resetFingerPrintEntrolled();
    ...
}
```

- 密钥别名固定 `com.ali.user.sdk.fingerprint`，算法
  `AES/CBC/PKCS7Padding`，密钥存 AndroidKeyStore，**不外泄**。
- `setUserAuthenticationRequired(true)`：使用该密钥必须先通过设备生物识别
  /锁屏认证。
- `cipher.init(3, key)` 的 `3` 是 `Cipher.ENCRYPT_MODE | Cipher.DECRYPT_MODE`
  的位或（`ENCRYPT_MODE=1`、`DECRYPT_MODE=2`），等价于
  `init(key, Cipher.ENCRYPT_MODE | Cipher.DECRYPT_MODE)`。该调用形式在
  Android Keystore 上是**生物识别授权探测的标准用法**：初始化一个双向
  cipher，从而触发 Keystore 的“用户认证”闸门并返回该 cipher 供
  `authenticate` 使用。字节码已核对为 `const/4 v2, 3` +
  `invoke-virtual {v1, v2, v0}, Ljavax/crypto/Cipher;->init(I Ljava/security/Key;)V`。
- `KeyPermanentlyInvalidatedException`（`:52`）被专门捕获，含义是**指纹
  变更导致密钥失效**，此时 `mKeyStore.deleteEntry(MY_KEY)` 并回调
  `onFail(Error_invalid=5001, "指纹变更")`；其它异常报
  `Error_other=5002`。
- `getKey()`（`:103`）在 `!mKeyStore.isKeyEntry(MY_KEY)` 时先 `createKey()`，
  再 `mKeyStore.getKey(MY_KEY, null)`。

**调用面（字节码核对）**：`AES` 的构造器、`checkValid()`、
`checkValid(CommonCallback)` 共被 6 处外部调用：

| 调用点 | 用途 |
|---|---|
| `com/ali/user/mobile/verify/VerifyApi.java:239` | `openBiometric` 前置校验 |
| `com/ali/user/mobile/login/ui/UserLoginActivity.java:700` | `goFragmentByType` / `openFragmentByIntent` 生物识别登录分支 |
| `com/ali/user/mobile/navigation/NavigatorServiceImpl.java:187,210` | `fingerIV` / `fingerLogin` |

即全部调用点都只是**校验**（判断指纹密钥是否仍有效），**没有任何调用点
拿回 cipher 去做 `doFinal`**。

### 5.3 `AES.getCipher` 与 `Rsa.sign` 是未接线的声明面

对全部 25 个载荷 DEX 的字节码做全量引用扫描（方法体指令中出现的
`Lcom/ali/user/mobile/rpc/safe/AES;->` 与 `Lcom/ali/user/mobile/rpc/safe/Rsa;->`
引用），结果：

| 方法 | 外部引用 | 结论 |
|---|---:|---|
| `AES.<init>` / `AES.checkValid()` / `AES.checkValid(CommonCallback)` | 6 | 在用 |
| `AES.getCipher(boolean)` | **0**（仅类内自引用） | 已声明，无外部调用点 |
| `AES.getKey()` / `AES.createKey()` | 仅类内 | 由 `checkValid` 间接使用 |
| `Rsa.encrypt(String,String)` | 3 | 在用 |
| `Rsa.sign(String,String)` | **0** | 已声明，无调用点 |

因此：**`AES` 的加解密出口与 `Rsa.sign` 在本版本中是未接线的声明面**。
生物识别的实际加解密不在 `com/ali/user/mobile/rpc/safe/` 里——客户端只
通过 `FingerprintService` 接口触发生物认证：

```java
public interface FingerprintService {
    public static final int ERROR_CRYPTO_NOT_INIT = 100;
    void authenticate(FingerCallback fingerCallback);
    void cancelIdentify();
    boolean isFingerprintAvailable();
    boolean isFingerprintSetable();
}
```

（`com/ali/user/mobile/service/FingerprintService.java`）实现方由宿主应用
注册（`ServiceFactory.getService(FingerprintService.class)`），大麦包内
**没有该接口的实现类**；`ERROR_CRYPTO_NOT_INIT = 100` 表明密码学初始化
在实现方内部，本次静态分析无法进入，标记为宿主/外部边界。

### 5.4 RSA 密码上行

`Rsa.encrypt(String,String)` 是本版本**唯一在用**的 RSA 入口：

```java
PublicKey publicKeyFromX509 = getPublicKeyFromX509("RSA", str2);
Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
cipher.init(1, publicKeyFromX509);          // 1 = ENCRYPT_MODE
byte[] bytes = str.getBytes("UTF-8");
int blockSize = cipher.getBlockSize();
for (int i = 0; i < bytes.length; i += blockSize) {
    byteArrayOutputStream.write(cipher.doFinal(bytes, i,
            bytes.length - i < blockSize ? bytes.length - i : blockSize));
}
String str3 = new String(Base64.encode(byteArrayOutputStream.toByteArray()));
```

`getPublicKeyFromX509` 用 `X509EncodedKeySpec(Base64.decode(str2))`
构造公钥（`:80`）。

三个调用点：

| 调用点 | 加密内容 |
|---|---|
| `com/ali/user/mobile/login/service/impl/UserLoginServiceImpl.java:391` | `passwordLoginRequest.password = Rsa.encrypt(loginBaseParam.password, rsaPubkey)`，随后 `passwordLoginRequest.pwdEncrypted = true` |
| `com/ali/user/mobile/data/RegisterComponent.java:65` | `Rsa.encrypt(oceanRegisterParam.password, rsaPubkey)` |
| `com/taobao/android/sso/v2/launch/SsoLogin.java:249` | `sSOSlaveParam.uuidKey = Rsa.encrypt(uuid, SSO_RSA_KEY)` |

公钥来源 `com/ali/user/mobile/rpc/safe/RSAKey.java`：

```java
private static final String DEFAULT_RSA_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC8H6Gp...B3+/4wIDAQAB";
public static final String SSO_RSA_KEY      = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCN1SZg...y1T3ywIDAQAB";

public static String getRsaPubkey() {
    if (TextUtils.isEmpty(rsaPubKey)) rsaPubKey = DEFAULT_RSA_KEY;
    return rsaPubKey;
}
```

`getRsaPubkey()` **没有从服务端拉取的路径**——`RSAKey` 类里没有网络调用，
`rsaPubKey` 只在首次访问时被赋为硬编码常量。因此账号密码与注册密码是用一个
**固化的 1024 位 RSA 公钥**加密后上行（`RSA/ECB/PKCS1Padding`）。用
`openssl` 结构解析可确认密钥规格：

```text
SubjectPublicKeyInfo, 162 字节
  AlgorithmIdentifier 06 09 2a 86 48 86 f7 0d 01 01 01 05 00   (rsaEncryption, NULL)
  BIT STRING → RSAPublicKey
      modulus  128 字节 = 1024 位
      exponent 01 00 01 = 65537
```

`SsoLogin` 的 `uuidKey` 用的是 `SSO_RSA_KEY`（另一个 1024 位常量），且
`uuid` 在 `activity.getSharedPreferences("uuid", 0).edit().putString("uuid", uuid).apply()`
里先落盘（`:248`）。该值用于淘宝 SSO 的 IPC 握手
（`SSOSlaveParam{appKey, ssoVersion, t, targetUrl, uuidKey, sign}`），
`sign` 走 `SSOSecurityService.sign(appKey, treeMap, atlas)`
（`com/ali/user/mobile/security/SSOSecurityService.java:66`），其
`sign(str, TreeMap)` 把 map 拼成 `k=v&k=v` 后：

```java
SecurityGuardParamContext securityGuardParamContext = new SecurityGuardParamContext();
securityGuardParamContext.appKey = str;
securityGuardParamContext.paramMap = mapE;         // {"INPUT": <拼好的串>}
securityGuardParamContext.requestType = 5;
String strSignRequest = getSignComponent().signRequest(securityGuardParamContext, "");
```

即 SSO 握手的签名由 SecurityGuard **native** 完成（`requestType = 5`），
Java 层只负责拼串。

包内其余 1024 位 RSA 公钥常量（6 个）分布：

| 常量 | 归属 | 用途 |
|---|---|---|
| `DEFAULT_RSA_KEY` | `com/ali/user/mobile/rpc/safe/RSAKey.java` | 账号/注册密码加密 |
| `SSO_RSA_KEY` | `com/ali/user/mobile/rpc/safe/RSAKey.java`、`com/taobao/android/sso/v2/launch/util/RSAKey.java` | SSO uuid 加密 |
| `tb/c24.java` | 加密工具类 | 未见大麦业务调用点 |
| `com/taobao/android/tlog/protocol/TLogSecret.java` | TLog 日志加密 | 日志链路 |
| `com/alipay/sdk/m/n/a.java` | 支付宝 SDK | 支付链路 |
| `com/uc/webview/export/cyclone/Log.java` | UC 内核日志 | 第三方 |

### 5.5 死代码：`cn/damai/pay/alipay/SignUtils`

`cn/damai/pay/alipay/` 下保留了一套完整的**支付宝「快捷支付」示例工程**
（`AlixDemo extends Activity`、`AlixDefine`、`MobileSecurePayer`、
`MobileSecurePayHelper`、`NetworkManager`、`Base64`、`BaseHelper`、
`ResultChecker`、`PayResult`、`Result`、`Result2`、`AuthResult`、
`AlixId`、`Constant`）。其中：

```java
public class SignUtils {
    private static final String SIGN_ALGORITHMS = "SHA1WithRSA";
    private static final String SIGN_SHA256RSA_ALGORITHMS = "SHA256WithRSA";

    public static String sign(String str, String str2, boolean z) {
        PrivateKey privateKeyGeneratePrivate = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(str2)));
        Signature signature = Signature.getInstance(getAlgorithms(z));
        signature.initSign(privateKeyGeneratePrivate);
        signature.update(str.getBytes("UTF-8"));
        return Base64.encode(signature.sign());
    }
}
```

它接受 **PKCS#8 私钥**并对内容做 `SHA1WithRSA` / `SHA256WithRSA` 签名，
`sSOSlaveParam.sign` 的语义与之对应。字节码全量扫描结果：`SignUtils` 的方法
只被类自身引用，**外部引用数为 0**，且类内**没有任何私钥常量**
（`sign` 的私钥来自调用方入参）。因此这是示例工程残留，本版本不可达，
不构成“客户端内持有签名私钥”的证据。

`AlixDemo` / `AlixDefine` 被大量无关类（含 `kotlin/*`）以 import 形式引用，
属于 JADX 处理混淆时的导入噪声，不代表调用关系。

## 6. Passkey / 无密码

`cn/damai/login/passkey/PasskeyHelper.java` 常量：

```java
public static final String DM_PASSKEY_AVAILABLE_SETTING   = "passkey_available_setting";
public static final String DM_PASSKEY_AVAILABLE_TAG       = "passkey_available";
public static final String DM_PASSKEY_AVAILABLE_UNSETTING = "passkey_available_unsetting";
public static final String DM_PASSKEY_BIZSCENE_TAG        = "default";
public static final String DM_PASSKEY_BIZ_CODE_TAG        = "ali.china.damai";
public static final String DM_PASSKEY_UNAVAILABLE         = "passkey_unavailable";
```

- 业务码 `ali.china.damai` 是 RP ID 语义（服务端按此识别依赖方）。
- 开关走云配 `CloudConfigProxy`（import 可见），实际实现由 APK 内已反编译的
  `com.alibaba.security.wukong.passkey.SecPasskey` 调用 `com.ali.user.mobile.PasskeyManager`
  组织；请求签名、创建/断言数据组装和上传字段均由包内字节码明确给出。

Passkey 接口表（7 个，`mtop.alibaba.security.passkey.*`）：
`config.query`、`usable`、`registration`、`challenge`、`authenticate`、
`remove`、`log`。另有 mtop 层 `mtop.taobao.mloginservice.passkey.challenge`
与 `.passkey.login`，以及账号级 `mtop.account.biometric.open` /
`.biometric.close`、`mtop.taobao.mloginService.biometricLogin`。

Passkey 流程（静态可见的请求序）：

1. `config.query` / `usable` — 探测设备与账号是否支持；
2. `challenge` — 取服务端挑战；
3. `registration` — 注册凭据（首次绑定）；
4. `authenticate` — 用凭据登录；
5. `remove` — 解绑；
6. `log` — 上报 passkey 事件。

请求对象继承 `BaseRequest`，构造时把序列化后的 `ClientInfo` 用
`MacUtils.sign(JSON.toJSONString(clientInfo), umidToken)` 签名；创建凭据走
Android Credential Manager/FIDO2 的 `CredentialCreationOptions`。注册请求提交
`attestationObject`、`clientDataJSON`、`credentialId`，断言请求提交
`authenticatorData`、`clientDataJSON`、`credentialId`、`signature`。Passkey
私钥生成、保管和断言私钥签名由 Credential Manager/FIDO2 authenticator 执行，
客户端代码不接收、不保存该私钥。

## 7. 会话在 MTOP 之外的传递面

| 通道 | 载体 | 位置 |
|---|---|---|
| MTOP 参数/头 | `sid` → `x-sid`；`uid` → `x-uid`；`accessToken` → `x-act` | `InnerProtocolParamBuilderImpl.java:205`、`InnerNetworkConverter.java:18,32` |
| H5/WebView | `.taobao.com` Cookie | `SessionManager.java:366,402` |
| 开放平台 | `openappkey=` / `;accesstoken=` 拼进 `extdata` | `InnerProtocolParamBuilderImpl.java:114-124` |
| 跨进程 | `NOTIFY_SESSION_VALID` / `NOTIFY_CLEAR_SESSION(_COOKIES)` 广播 | `SessionManager.java:44-49` |
| 本地文件 | `injectCookieNew` / `injectCookieOld` / `injectExternalH5Cookie` 经动态加密 | `SessionManager.java:1726-1758` |
| 账号历史文件 | `aliusersdk_history_acounts`、`aliusersdk_session_lists`、`finger_list` | `SecurityGuardManagerWraper.java:50-53` |

## 8. 未越权设计边界（客户端侧可见）

以下事实是“客户端**没有**做什么”的证据：

- 客户端不持有 MTOP appKey/appSecret 明文：`di0.b(index)` 与
  `di0.a("appsecret")` 都从 SecurityGuard 静态数据表取
  （`tb/di0.java:31,48`），`appsecret` 还被逐字符 `-1` 存储
  （`di0.c()`），说明密钥本体由 native SecurityGuard 保管。
- 客户端不持有**签名私钥**：`cn/damai/pay/alipay/SignUtils.sign` 虽接受
  私钥参数，但无外部调用点（§5.5）；`cn/damai/pay/alipay/` 目录内不存在
  任何私钥常量。RSA 只以**公钥加密**方向使用（§5.4）。
- 生物识别的密码学出口未接线：`AES.getCipher` 无调用点（§5.3），实际
  加解密由宿主注册的 `FingerprintService` 实现方承担。
- 客户端不做 token 签发：`ConvertAuthCodeToAccessTokenData`、
  `AliUserResponseData`、`ThirdSessionModel` 全部是**响应**模型，
  会话值均来自服务端返回。
- 客户端不绕过设备认证：指纹密钥
  `setUserAuthenticationRequired(true)`，且在
  `KeyPermanentlyInvalidatedException` 时主动删除本地密钥而不是降级。

## 9. 认证边界与服务端裁决事实

- 服务端 `sid` 有效期、是否绑定设备与 IP、并发登录策略：客户端只见
  `expireIn` / `sessionExpiredTime` 字段，判定逻辑在服务端。
- `accessToken` 的 scope 与可用接口集：客户端不校验 scope，由服务端裁决。
- `deviceTokenSign` 由 SecurityGuard SafeToken 组件生成，输入格式、序列化顺序、
  调用操作码和 native 入口均已还原（见 evidence.md §6）。
- 生物识别的密钥使用由 Android Keystore 的 `setUserAuthenticationRequired(true)`
  约束，具体硬件隔离与认证策略由设备 Keystore/authenticator 执行；大麦包内
  `AES/CBC/PKCS7Padding` 出口没有调用点（§5.3），不参与已还原的登录链路。
- `DEFAULT_RSA_KEY` 对应的服务端私钥持有方：客户端只见公钥常量，私钥归属
  与轮换策略不可见。
- passkey 的请求签名与上传格式完全来自 APK 内悟空 SDK 字节码；仅凭据私钥
  生成和断言签名由 Credential Manager/FIDO2 authenticator 执行（§6）。
- 会话 `sid` 有效期、设备/IP 绑定、并发表决和 `accessToken` scope 都在服务端
  执行；客户端只消费服务端返回字段和错误码，不在本地放大权限。
