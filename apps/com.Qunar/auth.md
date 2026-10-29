# 认证机制

## 1. 登录链路

登录不是 REST 调用，而是一个 `tStr = "p_login"` 的 Hotdog 请求：

```java
LoginParam loginParam = new LoginParam();
loginParam.nt      = activeNetworkInfo.getTypeName();
loginParam.lat/lgt = 缓存定位（若有）
loginParam.channelName / channelInfo / installTimestamp = 华为渠道或广告渠道信息
loginParam.versionCode = PackageInfo.versionCode
loginParam.mno     = QunarUtils.getSimOperator(ctx)
loginParam.verifysource = <调用方传入>
loginParam.md5     = MD5.getMD5(applicationInfo.sourceDir)
String jsonString  = JsonUtils.toJsonString(loginParam);
new HotdogConductor(loginCallback).setParams(Config.PARAM_HOST, Config.PARAM_T_LOGIN, jsonString);
ChiefGuard.getInstance().addTask(..., new Ticket(Ticket.RequestFeature.ADD_INSERT2HEAD));
```

`LoginParam` 的完整字段（`com/mqunar/atom/attemper/login/model/login/LoginParam.java`）：

| 字段 | 说明 |
|---|---|
| `buildMsg` | `QunarUtils.getBuildMsg()` 的 JSON |
| `cidInfo` | `GlobalEnv.getCidInfo()`（渠道/预装信息） |
| `channelInfo` / `channelName` / `installTimestamp` | 渠道归因 |
| `lat` / `lgt` | 最新缓存定位 |
| `md5` | 安装包 `base.apk` 的 MD5 |
| `mno` | SIM 运营商 |
| `nt` | 网络类型名 |
| `rcid` | `GlobalEnv.getRCid()` |
| `cpuAbi` | `AndroidUtils.getCpuAbi` |
| `verifysource` | 登录校验来源标识 |
| `versionCode` | 版本号 |

`md5` 字段把**安装包本体哈希**作为登录入参，属于客户端完整性绑定：
换包/重签会改变该值。

## 2. 其他认证相关功能键

`com/mqunar/atom/attemper/Config.java` 明确列出的功能键：

| 常量 | 值 | 用途 |
|---|---|---|
| `PARAM_T_LOGIN` | `p_login` | 登录 |
| `PARAM_T_COOKIES` | `p_ucUpdateCookie` | Cookie 刷新（`CookieRefresher`） |
| `PARAM_T_APPIDS` | `mk_appIds` | App ID 集合 |
| `PARAM_T_MK_OAID_CERT` | `mk_oaidCert` | OAID 证书 |
| `PARAM_T_MK_PRE_INSTALL_INFO` | `mkd_preInstallInfo` | 预装信息 |
| `PARAM_T_URS` | `f_apollo_x_urs_task_query` | 用户调研任务 |
| `MK_APP_CONFIGS` | `mk_appConfigs` | 应用配置 |
| `PARAM_T_ABTEST` | `p_clientABTest` | AB 实验 |
| `PARAM_T_CLIENT_RISK` | `qpub_clientRisk` | 客户端风险上报 |
| `PARAM_T_LOCATION_UPLOAD` | `qpub_locationUpload` | 位置上报 |
| `PARAM_T_QADINFO` | `p_qadInfo` | 广告信息 |
| `PARAM_T_HINFO` | `p_hupgrade` | 升级信息 |
| `PARAM_T_MINFO` | `p_minfo` | 设备信息 |
| `PARAM_T_QALOCAL` | `p_qaLocal` | 本地 QA |
| `PARAM_T_FILE_DATA` | `app_file_data` | 文件数据 |

`PARAM_HOST = GlobalEnv.getHotDogUrl()`，发行版指向
`http://<corp-host>/fca`（内网常量，已脱敏）。

## 3. 票据与会话载体

- **Cookie**：`PitcherCookieUtils.insertCookie(ctx, netRequest)` 在
  `onInterceptRequest` 注入，`PitcherCookieUtils.saveCookie(ctx, cookieUrl,
  getRespHeader())` 在 `onInterceptResponse` 回收。Cookie 与目标 URL 绑定，
  由 `HotdogConductor.cookieUrl` 持有。
- **`GlobalEnv` 标识族**：`usid`（用户会话）、`uid` / `initUid`（用户/装机）、
  `gid`、`sid`、`pid`、`cid`、`vid`（版本维度）。这些值全部进入 cparam，
  属服务端可关联的会话维度。
- **`Ticket.RequestFeature.ADD_INSERT2HEAD`**：登录使用的票据特性标志，
  表示把凭证插入请求头（而非正文）。
- **`AuthenMng` 一类本地状态**：`shared_prefs/authStatus_com.Qunar.xml` 与
  `authStatus_com.Qunar:remote.xml` 在设备上各存在一份（只读核对时观察到），
  说明登录态按进程/远程进程分文件落盘。

> 本次未登录、未持有任何真实账号票据，报告不含任何 Cookie、令牌、会话 ID 值。

## 4. 本地凭据落盘

| 位置 | 内容（键名层面） | 加密 |
|---|---|---|
| `shared_prefs/authStatus_com.Qunar.xml` | 登录状态 | **明文 XML** |
| `shared_prefs/ssoconfigs.xml`、`sso_config_xf.xml` | 单点登录配置 | 明文 |
| `shared_prefs/qunargpsshare.xml` | 分享相关授权 | 明文 |
| `shared_prefs/userdefault.xml` | 用户默认项 | 明文 |
| `files/kv_storage/*`（16 个 MMKV） | 业务 KV | MMKV 默认（mmap + CRC，非加密模式） |

50 个 `shared_prefs` 文件全部以明文 XML 序言开始（
`<?xml version='1.0' encoding='utf-8' standalone='yes' ?>`），
与同仓库 `apps/cn.com.livelab/storage.md` 记录的密文落盘不同：
**去哪儿没有对 SharedPreferences 做应用层加密**。详见 `storage.md`。

## 5. 第三方登录与支付回调入口

| 组件 | 用途 |
|---|---|
| `com.Qunar.wxapi.WXEntryActivity`（activity + activity-alias） | 微信登录/分享回调 |
| `com.Qunar.wxapi.WXPayEntryActivity`（activity + activity-alias） | 微信支付回调 |
| `com.mqunar.atom.share.wxapi.WXEntryActivity`（activity + activity-alias） | 分享模块的微信回调 |
| `com.alipay.sdk.app.PayResultActivity`、`AlipayResultActivity` | 支付宝结果回传 |
| `com.mqunar.pay.inner.activity.pay.WXPayEntryActivity`、`ThirdPayActivity` | 收银台第三方支付 |
| `com.mqunar.atom.attemper.ulink.UlinkRouteActivity` | 深链路由 |
| `com.mqunar.dispatcher.DispatcherActivity` / `DispatcherProxyHomeActivity` | 统一分发入口 |

这 14 个导出 Activity 与 3 个 activity-alias 全部**没有** `android:permission`
保护（`permissions.md` §2）。它们承担外部 App 回调，属必需导出，但意味着任何
本地应用都能向这些入口发 Intent；这些入口是否对入参做来源校验，需逐个读代码
确认，本报告不宣称已覆盖全部入口。

## 6. 令牌失效与错误分支

`Response.parseResponse` 只做解压与类型标记，业务错误码在 `HotdogConductor`
之上由各业务 `Conductor` 处理。`GlobalEnv.isRelease()` 为 false 时会走
`MockNet.getMockNetCallBack()` 分支，可替换请求/响应内容（仅调试构建路径）。
本次未观察到可静态确认的“令牌失效 → 自动刷新”闭环，`CookieRefresher` 是最接近
的实现（`p_ucUpdateCookie`）。

## 7. 静态化说明

所有字段、常量与调用点来自反编译结果。令牌有效期、刷新策略、服务端校验强度
不可从客户端静态确定。
