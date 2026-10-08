# 网络交互流程与协议格式

## 1. 调查边界

本页只使用 APK 内 DEX 与资源做静态分析。搜索、活动/项目详情、下单三段式与
票夹接口**均未发送请求**，未登录账号，未构造或重放任何报文。文中 URL、参数名
和字段名都是反编译代码里的字面量或字段声明，不代表本次验证过服务端可达性。

## 2. 业务请求不经 Retrofit/OkHttp

与多数电商客户端不同，大麦没有业务 HTTP 客户端：所有业务请求统一走火山
MTOP（`mtopsdk`），底层是阿里 `anetwork` 通道。

```
业务层  cn.damai.common.net.mtop.netfit.DMBaseMtopRequest 子类
          （声明 getApiName() / getVersion() / getNeedSession() / getNeedEcode()）
   │  DMBaseMtopRequest.build() / call()
   ▼
装配层  com.taobao.tao.remotebusiness.MtopBusiness.build(mtopInstance, request)
   │  参数：hi0.a().b(apiName, map)  公共业务参数 + 签名
   ▼
协议层  mtopsdk.mtop.protocol.builder.impl.InnerProtocolParamBuilderImpl
        mtopsdk.mtop.protocol.converter.impl.InnerNetworkConverter
        mtopsdk.mtop.protocol.converter.impl.AbstractNetworkConverter
   │
   ▼
签名层  mtopsdk.security.InnerSignImpl  →  SecurityGuard / MiddleTier / AVMP
   │
   ▼
通道层  anetwork.network.cache / mtopsdk.network.impl.ANetworkCallFactory
```

对应文件与行号：

- `mtopsdk/mtop/protocol/builder/impl/InnerProtocolParamBuilderImpl.java:143`
  （`buildParams(MtopContext)` 起点），`:42`（`buildExtParams`），`:106`（`setOldTopProtocolParams`）
- `mtopsdk/mtop/protocol/converter/impl/AbstractNetworkConverter.java:94`（`buildBaseUrl`），
  `:120`（`buildRequestHeaders`），`:178`（`convert`，见 §9）
- `mtopsdk/mtop/protocol/converter/util/NetworkConverterUtils.java:15`（`createParamQueryStr`），
  `:45`（`initUrl`）
- `mtopsdk/mtop/protocol/converter/impl/InnerNetworkConverter.java:14`（静态头映射表）

## 3. MTOP 实例初始化

`cn/damai/launcher/initialize/CommonBiz.java:1324` 起：

```java
MtopSetting.setAppKeyIndex(Mtop.Id.INNER, 0, 2);
MtopSetting.setAppVersion(Mtop.Id.INNER, AppConfig.m());
EnvModeEnum envModeEnum = EnvModeEnum.ONLINE;
...
Mtop mtopSwitchEnvMode = Mtop.instance(Mtop.Id.INNER, application, AppConfig.l())
        .switchEnvMode(envModeEnum);
MtopAccountSiteUtils.bindInstanceId(Mtop.Id.INNER, "taobao");
```

- `MtopSetting.setAppKeyIndex(instanceId, onlineIndex, dailyIndex)` 把
  `onlineAppKeyIndex=0`、`dailyAppkeyIndex=2` 写入 `MtopConfig`
  （`mtopsdk/mtop/intf/MtopSetting.java:128`）。appKey 本身不由客户端硬编码，
  而是由 SecurityGuard 静态数据表按索引取出（见 §7）。
- `ttid` 由 `AppConfig.l()` 生成，格式为 `<渠道串> + "@damai_android_" + <版本>`
  （`cn/damai/common/AppConfig.java:171`），并在每次请求作为 `ttid` 上报。
- `AppConfig.l()` 的返回值同时被设为 `OneConfig.setTtid(...)` 与
  `registerTtid(...)`，所以 OneService/图片库共用同一个 ttid。

环境切换：`AppConfig.EnvMode{online, prepare, test}`（`cn/damai/common/AppConfig.java:58`），
`switchEnvMode` 决定 MTOP 的 `EnvModeEnum`，进而决定签名中间层的 `env`
取值 `0/1/2`（`InnerSignImpl.getMiddleTierEnv()`，
`mtopsdk/security/InnerSignImpl.java:103`）。

## 4. 请求 URL 结构

`AbstractNetworkConverter.buildBaseUrl(MtopContext, apiName, version)`
（`mtopsdk/mtop/protocol/converter/impl/AbstractNetworkConverter.java:94`）：

```java
sb.append(mtopNetworkProp.protocol.getProtocol());      // https
String customDomain = getCustomDomain(mtopContext);
if (StringUtils.isNotBlank(customDomain)) sb.append(customDomain);
else sb.append(mtopConfig.mtopDomain.getDomain(mtopNetworkProp.envMode));
sb.append("/").append(mtopConfig.entrance.getEntrance());
sb.append("/").append(str);      // apiName
sb.append("/").append(str2);     // version
sb.append("/");
```

即：

```text
<protocol>://<domain>/<entrance>/<apiName>/<version>/
```

- 入口枚举 `mtopsdk/mtop/domain/EntranceEnum.java`：`GW_INNER("gw")`、
  `GW_OPEN("gw-open")`。
- 业务请求的域名由 `cn/damai/ultron/net/DMQueryKey.setDomain()`
  （`cn/damai/ultron/net/DMQueryKey.java:66`）按 `AppInfoProxy.getEnv().getEnvMode()`
  选择：

  | 环境 | MTOP 域名 |
  |---|---|
  | `EnvMode.TEST` | `daliy-mtop.damai.cn` |
  | `EnvMode.PREPARE` | `pre-mtop.damai.cn` |
  | 线上 | `mtop.damai.cn` |

- 非 MTOP 的直连域名（源码字面量）：`cn/damai/net/DamaiDataAccessApi.java:6-8`
  `IP = "https://mapi.damai.cn"`、`NEW_IP = "https://gw.damai.cn"`、
  `PROJECT_IMG_ADDR = "https://ossali.damai.cn"`。
- 载荷 DEX 中出现的其它大麦/淘系域名与用途（均为字符串字面量，未验证可达性）：

  | 域名 | 静态可见用途 |
  |---|---|
  | `m.damai.cn` / `www.damai.cn` / `detail.damai.cn` | H5 与分享落地页 |
  | `msecurity.damai.cn` | 安全/H5 风控页 |
  | `passport.damai.cn` / `passport.taobao.com` | 登录与账号 |
  | `havanalogin.taobao.com` | Havana 登录 |
  | `market.wapa.damai.cn` | 预发投放页面 |
  | `damai-android.oss-cn-beijing.aliyuncs.com` | 自有 OSS 桶 |
  | `ossali.damai.cn` / `static.damai.cn` | 静态资源与项目图 |
  | `androiddownload.damai.cn` | 升级包下载 |
  | `mat.damai.cn` / `survey.damai.cn` / `help.damai.cn` | 埋点、问卷、帮助 |
  | `img.alicdn.com` / `g.alicdn.com` / `ykimg.alicdn.com` | CDN 资源 |

## 5. 公共参数装配

`InnerProtocolParamBuilderImpl.buildParams(MtopContext)`
（`mtopsdk/mtop/protocol/builder/impl/InnerProtocolParamBuilderImpl.java:143`）
按固定顺序写入参数表：

| 顺序 | 参数 | 取值来源（行号） |
|---:|---|---|
| 1 | `utdid` | `mtop2.getUtdid()` → `XState.getValue("utdid")`（:163） |
| 2 | `uid` | `reqUserId` 或 `getMultiAccountUserId(userInfo)`（:164） |
| 3 | `reqbiz-ext` | `property.reqBizExt`，非空才写（:166） |
| 4 | `routerId` | 先 `mtopConfig.routerId`，再 `property.routerId` 覆盖（:176/:179） |
| 5 | `placeId` | 同上（:182/:185） |
| 6 | `appKey` | `property.reqAppKey`，为空则回落 `mtopConfig.appKey`（:170/:187） |
| 7 | `data` | `mtopRequest.getData()`；`priorityFlag` 时注入 `x-priority-data`（:198） |
| 8 | `t` | `String.valueOf(SDKUtils.getCorrectionTime())`，服务端校时后的毫秒时间戳（:200） |
| 9 | `api` | `mtopRequest.getApiName().toLowerCase(Locale.US)`（:203） |
| 10 | `v` | `mtopRequest.getVersion().toLowerCase(Locale.US)`（:204） |
| 11 | `sid` | `getMultiAccountSid(userInfo)`（:205） |
| 12 | `ttid` | `property.ttid`（:206） |
| 13 | `deviceId` | `getDeviceId()`（:207） |
| 14 | `lat` `lng` | 仅当 XState 的 `lat`/`lng` 同时非空（:209-213） |
| 15 | `x-features` | `MtopFeatureManager.getMtopTotalFeatures(mtop)` 的十进制串（:223） |
| 16 | `x-accept-stream` | `property.streamMode` 为真时写 `"true"`（:225） |
| 17 | `exttype` / `extdata` | `ApiTypeEnum` + `openappkey=`/`;accesstoken=`（:230-244） |
| 18 | `open-biz` `mini-appkey` `req-appkey` `open-biz-data` `accessToken` | 仅当 `property.openBiz` 非空（:249-262） |
| 19 | `mtopBusiness` | 仅签名前临时写入，签名后 `remove`（:293） |

签名完成后追加（`:315-425`）：`sign`、`wua`（仅 `wuaFlag>=0` 或 `wuaRetry`）、
`x-mini-wua`、`umt`、`x-sgext`（非空时）、`pv = "6.3"`。

最后 `buildExtParams()`（`:42`）追加：

| 参数 | 来源 | 行号 |
|---|---|---|
| `netType` | `XState.getValue("netType")` | :46 |
| `nq` | `XState.getValue("nq")` | :47 |
| `umt` | 已存在则跳过；否则 `XState.getValue(instanceId, "umt")` | :48-50 |
| `x-app-ver` | `mtopConfig.appVersion`，非空才写 | :52-54 |
| `x-orange-q` | `mtopConfig.xOrangeQ`，非空才写 | :56-58 |
| `x-app-conf-v` | `mtopConfig.xAppConfigVersion` | :59 |
| `user-agent` | `XState.getValue("ua")` | :61-63 |
| `x-c-traceid` | `property.clientTraceId` | :64 |
| `x-falco-id` | 仅当 `SwitchConfig.getEnableFalcoId()` | :65-67 |
| `f-refer` | 常量 `"mtop"` | :68 |
| `x-netinfo` | `netParam` 位开关，见下 | :69-93 |
| `x-page-name` | `property.pageName` | :95-97 |
| `x-page-url` | `property.pageUrl` | :99-101 |
| `x-page-mab` | `mtopGlobalABTestParams.get(pageUrl)` | :102-104 |

`x-netinfo` 是 JSON 串，按位从 `NetworkStateReceiver` 取 WiFi 标识
（`mtopsdk/xstate/network/NetworkStateReceiver.java:45-48` 声明
`bssid`/`ssid` 静态字段，`:207-208` 由 `WifiInfo.getBSSID()/getSSID()` 赋值）：

```java
if (mtopNetworkProp.netParam > 0) {
    JSONObject jSONObject = new JSONObject();
    if ((mtopNetworkProp.netParam & 1) != 0) {
        String str3 = NetworkStateReceiver.ssid;      // WiFi SSID
        if (!TextUtils.isEmpty(str3)) jSONObject.put(NetParam.NetParamKey.SSID, str3);
    }
    if ((mtopNetworkProp.netParam & 2) != 0) {
        String str4 = NetworkStateReceiver.bssid;     // WiFi BSSID
        if (!TextUtils.isEmpty(str4)) jSONObject.put(NetParam.NetParamKey.BSSID, str4);
    }
    if (jSONObject.length() > 0) map.put(HttpHeaderConstant.X_NETINFO, jSONObject.toString());
}
```

`ssid`/`bssid` 的缺省值是 `<unknown ssid>` / 对应常量
（`NetworkStateReceiver.java:24` 起），因此 `x-netinfo` 里的 WiFi 名称是
客户端条件采集项，由服务端下发的 `netParam` 位掩码决定是否上报。

## 6. 参数名 → 请求头转换

`InnerNetworkConverter` 的静态表（`InnerNetworkConverter.java:14-54`，
共 38 项）把参数名映射为线上头名；`buildRequestHeaders`
（`AbstractNetworkConverter.java:120`）逐项：

```java
for (Map.Entry<String, String> entry2 : headerConversionMap.entrySet()) {
    String key2 = entry2.getKey();
    String strRemove = map.remove(entry2.getValue());   // 从参数表移除
    if (strRemove != null) {
        map3.put(key2, URLEncoder.encode(strRemove, "utf-8"));  // 值 URL 编码
    }
}
String strRemove2 = map.remove("lng");
String strRemove3 = map.remove("lat");
if (strRemove2 != null && strRemove3 != null) {
    StringBuilder sb = new StringBuilder();
    sb.append(strRemove2).append(",").append(strRemove3);
    map3.put(HttpHeaderConstant.X_LOCATION, URLEncoder.encode(sb.toString(), "utf-8"));
}
```

关键映射（表内 38 项的代表）：

| 线上头 | 参数名 |
|---|---|
| `x-sid` | `sid` |
| `x-t` | `t` |
| `x-appkey` | `appKey` |
| `x-ttid` | `ttid` |
| `x-devid` | `deviceId` |
| `x-utdid` | `utdid` |
| `x-sign` | `sign` |
| `x-nq` | `nq` |
| `x-nettype` | `netType` |
| `x-pv` | `pv` |
| `x-uid` | `uid` |
| `x-umt` | `umt` |
| `x-reqbiz-ext` | `reqbiz-ext` |
| `x-router-id` / `x-place-id` | `routerId` / `placeId` |
| `x-open-biz` / `x-mini-appkey` / `x-req-appkey` / `x-open-biz-data` | `open-biz` / `mini-appkey` / `req-appkey` / `open-biz-data` |
| `x-act` | `accessToken` |
| `x-mini-wua` | `x-mini-wua` |
| `x-app-conf-v` / `x-exttype` / `x-extdata` / `x-features` | 同名 |
| `x-page-name` / `x-page-url` / `x-page-mab` | 同名 |
| `x-app-ver` / `x-orange-q` | 同名 |
| `user-agent` / `x-c-traceid` / `x-falco-id` / `f-refer` | 同名 |
| `x-netinfo` / `x-s-c` / `x-sgext` / `x-accept-stream` | 同名（`x-s-c` 即 `HttpHeaderConstant.X_SIGN_CONTROL`） |
| `x-location` | `lng` + `lat` 合并（不在表中，单独处理） |

因此 `lat`/`lng` 不出现在 query/body 里，而是合并成 `x-location: <lng>,<lat>`
头。`x-page-url` 同时决定 `x-page-mab`。

## 7. 签名输入面

签名实现 `mtopsdk/security/InnerSignImpl.java`（632 行）。base string 由
`convertInnerBaseStrMap(map, appKey, z)`（`:187`）拼装，字段顺序固定：

```text
utdid & uid & reqbiz-ext & <appKey> & MD5(data) & t & api & v & sid & ttid
      & deviceId & lat & lng & [extdata &] x-features & routerId & placeId
      & open-biz & mini-appkey & req-appkey & accessToken & open-biz-data
```

差异点：

- `extdata` 在 `z=true` 时用 `convertNull2Default` 补齐再拼，`z=false` 时
  **仅非空才拼**（`:238-245`），所以线上形态取决于调用方传的第三参。
- `MD5(data)` 是 `SecurityUtils.getMd5()`
  （`mtopsdk/security/util/SecurityUtils.java:18`），Java `MessageDigest("MD5")`
  over UTF-8，逐字节 `Integer.toHexString(b & 255)` 左侧补零 —— 即小写十六进制。
- `SecurityUtils.convertNull2Default(str)` 把 `null` 变成空串（`:13`）。
- 空槽位保留分隔符，所以字段数恒定。

`convertSsrBaseStrMap(map, appKey)`（`:263`）是 SSR 变体，形态明显更短：

```text
utdid & uid & & <appKey> & MD5(data) & t & & & sid & ttid &&&&&&&&&&&&
```

即 `&&` 后直接跟 appKey、`t` 之后连续三个 `&`、`ttid` 之后 12 个 `&`。

四种签名出口：

| 方法 | 行号 | 后端 | 回填字段 |
|---|---:|---|---|
| `getMtopApiSign` | :404 | 先 `getSign`（若 `useSecurityAdapter & 1`），否则 `SecurityGuardParamContext{appKey, requestType=7, paramMap=convertInnerBaseStrMap(...)}` → `getSecureSignatureComp().signRequest(ctx, authCode)` | 返回值写 `sign` |
| `getSign` | :461 | `convertInnerBaseStrMap(...).get("INPUT")` → `mMiddleTier.getSign({data: bytes, env: getMiddleTierEnv(), appkey})` | `remove("x-sign")` 取返回值，其余 `map.putAll(sign)` |
| `getAvmpSign` | :326 | `avmpSign(sign)` → 失败时降级 `getSecBodyDataEx("", "", authCode, null, flag)` | 返回值写 `wua` |
| `getSecBodyDataEx` | :447 | `ISecurityBodyComponent.getSecurityBodyDataEx(data, appKey, authCode, map, flag, env)` | 返回值写 `x-mini-wua` |
| `getMiniWua` | :365 | `mMiddleTier.getMiniWua({env, ext:{api_name: map.get("api")}})` | `remove("x-mini-wua")` |
| `getWua` | :559 | `mMiddleTier.getWua({data: sign.getBytes("UTF-8"), env})` | `remove("wua")` |
| `getUnifiedSign` | :504 | `IUnifiedSecurityComponent`，入参 `{appkey, data: base string, useWua: z, env}` | 返回整表 |
| `getCommonHmacSha1Sign` | :336 | `SecurityGuardParamContext{requestType=3}` → `signRequest(ctx, authCode)` | 返回值 |

native AVMP 路径（`:47` `avmpSign`）：

```java
IAVMPGenericComponent.IAVMPGenericInstance aVMPInstance =
        getAVMPInstance(mtopConfig != null ? mtopConfig.context : MtopUtils.getContext());
byte[] bArr2 = (byte[]) aVMPInstance.invokeAVMP("sign", new byte[0].getClass(), 0,
        str.getBytes(), Integer.valueOf(str.getBytes().length), "", bArr, Integer.valueOf(getEnv()));
```

`getAVMPInstance`（`:293`）用
`createAVMPInstance("mwua", "sgcipher")` 创建实例；`bArr`（4 字节）承载
AVMP 返回的错误码，按小端读回并上报 `SignStatistics`
（`SignStatsType.TYPE_INVOKE_AVMP`）。

appKey 获取走 SecurityGuard 静态数据表（`:80`）：

```java
appKeyByIndex = this.sgMgr.getStaticDataStoreComp().getAppKeyByIndex(i, str);
```

`SignCtx.index` 来自 `MtopSetting.setAppKeyIndex(Mtop.Id.INNER, 0, 2)`。

降级路径：`mtopsdk/mtop/protocol/builder/impl/InnerProtocolParamBuilderImpl.java:275-280`
在 `SignDegradedUtils.isSignDegraded(mtopContext)` 为真时用
`UUID.randomUUID().toString().replaceAll("-", "")` 当签名，并把
`x-s-c`（`X_SIGN_CONTROL`）置 `"1"`、`stats.isSignDegraded = true`。
因此 `x-s-c: 1` 是可以从响应侧观察的降级标记。

## 8. 查询串与报文格式

`NetworkConverterUtils.createParamQueryStr(map, enc)`（`:15`）：

```java
String strEncode  = key   != null ? URLEncoder.encode(key,   enc) : null;
String strEncode2 = value != null ? URLEncoder.encode(value, enc) : null;
sb.append(strEncode).append("=").append((Object) strEncode2);
if (it2.hasNext()) sb.append("&");
```

`initUrl(baseUrl, map)`（`:45`）仅在 baseUrl 不含 `?` 时追加 `?` + 查询串。

报文编码：`data` 是 JSON 字符串，默认
`JsonTypeEnum.ORIGINALJSON`（`cn/damai/common/net/mtop/netfit/DMBaseMtopRequest.java:38`），
即 `data=<json>`；`HttpMethod.GET/POST` 由
`DMBaseMtopRequest.getHttpMethod()` 决定，走
`mtopBusinessBuild.reqMethod(MethodEnum.GET|POST)`（`:83-87`）。

响应解析 `MtopResponse.parseJsonByte()`
（`mtopsdk/mtop/domain/MtopResponse.java:239`）：

- JSON 顶层字段：`api`、`v`、`ret`（数组）、`data`（对象）。
- `ret[0]` 按 `"::"` 切分（`SHARP = "::"`，`:20`），第 0 段写 `retCode`，
  第 1 段写 `retMsg`（`:321-326`）。
- `data` 存 `dataJsonObject`。
- 420 状态且 `retCode == "FAIL_SYS_REQUEST_QUEUED"` 视为排队
  （`:189`，`isRequestQueued`）。

业务侧公共参数由 `tb/hi0.java:44`（`b(apiName, map)`）注入：

```java
map.put("source", "10101");
map.put("version", iN + "");                       // AppConfig.n() versionCode
map.put("channel_from", i9.a(applicationA));       // 常量 "damai_market"
map.put(Constants.KEY_OS_TYPE, "2");               // accs 常量，Android
map.put("appType", "1");
...
map.put("timestamp", (System.currentTimeMillis() / 1000) + "");
map.put("appSecret", di0.c());
map.put("clientGUID", hm0.a(sd1.a()) + "1");       // utdid + "1"
map.put("systemVersion", hm0.e());                 // Build.VERSION.RELEASE
map.put("phoneModels", hm0.b());                   // Build.MODEL
map.put("appClientKey", di0.a("appClientKey"));
...
map.put("sign", strD.toLowerCase());
map.remove("appSecret");
```

`di0`（`tb/di0.java`）是 SecurityGuard 静态数据读取器：

- `di0.a(key)` = `getStaticDataStoreComp().getExtraData(key)`
- `di0.b(index)` = `getStaticDataStoreComp().getAppKeyByIndex(index)`
- `di0.c()` = `getExtraData("appsecret")` 每个字符码点减 1（`charAt(i) - 1`）
- `di0.d(sortedKeys, map)` = 按 key 字典序拼接所有取值后取 MD5（`ry3.g`）

即：业务层在 MTOP 之外还有一层自己的 `sign`，用 SecurityGuard
下发的 `appsecret`（服务端每字节 +1 存储）对
`channel_from + appClientKey + appSecret + appType + clientGUID + osType
+ phoneModels + platform? + source + systemVersion + timestamp + version`
的字典序拼接做 MD5。这一层与 MTOP 的 `x-sign` 是**两套独立签名**。

`platform` 缺失时 `hi0.c(map, apiName)`（`:89`）补默认值 `"271"`，并
`XFlushUtil.commitFail(...)` 上报一次失败监控。

## 9. 未还原方法与其闭合方式

`AbstractNetworkConverter.convert(MtopContext)`（`:178`）JADX 标记为
`Method not decompiled`，指令单元 965。它不在本次结论的关键路径上，因为
拼装所需的三个输入都已单独还原并交叉一致：

1. URL 前缀：`buildBaseUrl`（`:94`，完整）；
2. 参数表：`InnerProtocolParamBuilderImpl.buildParams`（`:143`，完整）；
3. 头表：`getHeaderConversionMap()`（`InnerNetworkConverter.java:57` 直接返回
   静态表）+ `buildRequestHeaders`（`:120`，完整）。

`convert()` 的职责是把这三者组合成 `mtopsdk.network.domain.Request`，其行为
由上述三段唯一确定；报告中不把它当作未知加密代码，而是标注为
“结构已证实”——组合语义已知，仅反编译文本缺失。

## 10. 业务接口族

载荷内 JADX 共还原 **655** 个唯一 MTOP 接口名。分布（按 `mtop.<域>.<族>`
统计）：

| 前缀 | 数量 |
|---|---:|
| `mtop.damai.wireless.*` | 263 |
| `mtop.alibaba.ucc.*` | 25 |
| `mtop.film.MtopOrderAPI.*` | 12 |
| `mtop.taobao.mloginService.*` | 11 |
| `mtop.film.pfusercenter.*` | 11 |
| `mtop.film.MtopCommunityAPI.*` | 11 |
| `mtop.havana.register.*` | 9 |
| `mtop.film.MtopSeatAPI.*` | 9 |
| `mtop.damai.mec.*` | 8 |
| `mtop.damai.item.*` | 8 |
| `mtop.verifycenter.rp.*` | 7 |
| `mtop.film.MtopCinemaAPI.*` | 7 |
| `mtop.alibaba.security.*` | 7 |
| `mtop.trade.order.*` | 6 |
| `mtop.taobao.alibabaMLoginService.*` | 6 |
| `mtop.damai.msgbox.*` | 6 |

`mtop.damai.wireless.*` 下的族分布：

| 族 | 数量 | 代表接口 |
|---|---:|---|
| `user` | 44 | `uploadHeadImg`、`getUserProfile`、`shippingaddress.*`、`customer.*`、`certification.*` |
| `order` | 28 | `orderlist.get`、`orderdetail`、`getpayparam`、`paymentcompletion`、`cancelorder`、`refund.check` |
| `search` | 24 | `search`、`detailedlist.get`、`hotword.get`、`projectlist.byrecommend.get` |
| `ticklet2` | 21 | 见 §10.4 |
| `comment` | 18 | `publish`、`list.get`、`praise`、`delete`、`modify` |
| `home` | 11 | `mec.aristotle.*` 首页卡片 |
| `follow` | 11 | `relation.follow.list`、`relation.fans.list`、`mycircle.query` |
| `content` | 10 | 内容流 |
| `discovery` | 8 | 发现页 |
| `seat` | 6 | 见 §10.5 |
| `venue` | 5 | `info`、`item.list`、`photo.official`、`photo.user` |

### 10.1 搜索

`cn/damai/search/model/SearchListRequest.java`：

```java
public String keyword;            public int pageIndex;
public int pageSize = 15;         public String cityId = "0";
public String sourceType = "10";  public String sortType = "4";
public String returnItemStatusOption = "0";  public String option = "";
public String channel = "10001";  public String returnItemOption = "4";
public String distanceCityId = ji0.c();  public String userId = ji0.D();
public String longitude = "";     public String latitude = "";
public String dmChannel = AppInfoProviderProxy.getDMChannel();
```

- API：`mtop.damai.wireless.search.search`，版本 `1.0`，
  `getNeedEcode() = false`，`getNeedSession() = false`。
- `distanceCityId` 与 `userId` 默认取自本地缓存（`ji0.c()` / `ji0.D()`），
  `dmChannel` 取自渠道代理。
- 翻页只靠 `pageIndex`，页面大小固定 15。

搜索族其它接口（同族，未构造请求）：
`detailedlist.get`、`hotword.get`、`tips.get`、`artist.search`、`brand.search`、
`venue.search`、`video.search`、`baccount.search`、`searchegg.get`、
`favourable.projectlist.get`、`performance.calendar.get`、`rangkinglist.get`、
`byrecommend.get`、`projectlist.byrecommend.get`、`project.classify`、
`project.classfy.statistics`、`cms.category.get`、`merge.category.get`、
`account.head`、`artist.list`、`artistpage.search`、`brand.detail`、
`brand.nearshow`、`broadcast.list`，以及 `mtop.damai.search.pioneer.page.get`、
`mtop.damai.search.tips.get`、`mtop.damai.wireless.ai.search.input.stream`。

### 10.2 活动/项目详情

详情族分散在三个前缀下：

- `mtop.alibaba.damai.detail.getdetail` / `.center`
- `mtop.alibaba.detail.subpage.getdetail`
- `mtop.damai.general.detail.getdetail`
- `mtop.damai.item.detail.getdetail` / `.1.0` / `scriptkill.getdetail`
- `mtop.damai.item.projectdetail.projectid.get`、`mtop.damai.item.oldproject.getitemid`
- `mtop.damai.item.calcTicketPrice`、`mtop.damai.item.notice.query`、
  `mtop.damai.item.tips.information`
- 首页卡片：`mtop.damai.mec.aristotle.get` 及其变体
  `get_app_home_pioneer_4.4` / `_discover_1.3` / `_new_scene_1.0` /
  `get_app_pioneer_cinema_venue_1.1`，以及 `mtop.damai.mec.popup.get` /
  `.report`

`cn/damai/common/net/mtop/Util.java:44`（`getApiParam`）显示
`mtop.damai.mec.aristotle.get` 的缓存 key 会被展开成
`<api>_<patternName>_<patternVersion>`，其中 `data` 参数经
`Uri.parse(data).getQueryParameter("data")` 解析 —— 说明首页卡片请求把
模板名与版本随请求一起上行，客户端据此做本地缓存分区。

### 10.3 下单三段式

三段式由 `DMQueryKey` 选域名、`UltronBuildOrder` / `UltronAdjustOrder` /
`UltronCreateOrder` 选 apiName+version、`UltronParamsMaker` 造参数。

apiName / version 矩阵（`cn/damai/ultron/net/api/*.java`）：

| 阶段 | 新链路 apiName | 新 version | 旧链路 apiName | 旧 version |
|---|---|---|---:|---|---:|
| build | `mtop.damai.trade.order.build` | `1.0` | `mtop.trade.order.build` | `4.0` |
| adjust | `mtop.damai.trade.order.adjust` | `1.0` | `mtop.trade.order.adjust` | `5.0` |
| create | `mtop.damai.trade.order.create` | `1.0` | `mtop.trade.order.create` | `4.0` |

预发环境旧链路用 `mtop.trade.order.build.dmpre` /
`mtop.trade.order.adjust.dmpre` / `mtop.trade.order.create.dmpre`。
新旧链路由 `DMQueryKey.getControlApiName`（`:47`）从 Intent 的
`rtc` 参数经 `yq0.e(str)` 读取云端开关
（`Cornerstone.getCloudConfig().getString("orderconfirm_new_mtop", "mtop_nt", "-1")`，
`tb/yq0.java:99`）决定。

参数构造（`cn/damai/ultron/net/UltronParamsMaker.java`）：

```java
// makeAdjustOrderParams:93
return p8.c("feature", "{\"gzip\":\"true\"}", "params", str);

// makeCreateOrderParams:124
map.put(UltronCreateOrder.K_ORDER_MARKER, "v:utdid=" + utdid);
```

- **build**（`:98`）：按 `purchase_from` 分流。
  - `1`（购物车，`UltronConstants.PURCHASE_FROM_CART`）：优先取 Intent 的
    `buildOrderParams` Serializable；否则 `buyNow=false` + `cartIds` +
    `buyParam` + `bookingDate` + `entranceDate`。
  - `2`（详情页，`PURCHASE_FROM_DETAIL`）：`buyNow=true` + `exParams` +
    `buyParam`。
  - 其它：对整个 Intent data URI 做 query 解析（`getQueryParameterMap`）。
  - 三种分支都过 `addExtraParams`（`:28`）：读取 `exParams` JSON，加入
    `websiteLanguage = Configuration.locale.toString()`，再写回。
- **adjust**（`:93`）：固定两字段 `feature` + `params`，`feature` 值
  `{"gzip":"true"}`（`UltronAdjustOrder.V_FEATURE`）。
- **create**（`:124`）：只有 `orderMarker = "v:utdid=" + UTDevice.getUtdid(context)`。
  `UltronCreateOrder` 另声明 `V_FEATURE =
  "{\"gzip\":\"true\",\"subChannel\":\"damai@tppnew_app\"}"`、`V_GZIP = "true"`
  与键 `appGuide / feature / gzip / orderMarker / params`。

下单请求头另有一路：`UltronDataManager.getHeaderMap()`
（`cn/damai/ultron/net/UltronDataManager.java:309`）按渠道取 `Dm-token`：

```java
if (ChannelUtil.INSTANCE.isDamaiApp()) {
    if (this.suggestData != null) map.put(getSuggestKey(), this.suggestData);
    else {
        String str = AppInfoProxy.INSTANCE.getAppClientName().equals(APPClient.TPP.getClientName())
                ? "5bf087b5656b0cb20aa1eac6e1ddaa2d" : "ddc1299aa63616f9d5c2bf4b6bcfc883";
        String string = SPProviderProxy.getSharedPreferences(str).getString(str, null);
        this.suggestData = string;
        if (!TextUtils.isEmpty(string)) map.put(getSuggestKey(), this.suggestData);
    }
}
```

`getSuggestKey()` 返回常量 `"Dm-token"`（`:675`），因此下单/确认页会带一个
`Dm-token` 头，值来自按渠道命名的 SharedPreferences（键名即上述两个 MD5 形状
常量，大麦与淘票票各一份）。`DMOrderCreateRequester.sendRequest`
（`cn/damai/ultron/net/DMOrderCreateRequester.java:40`）还会把
`yq0.c()`（同一条 Dm-token）拼上 `suggestData` 前缀后写入同一 header：

```java
String strC = yq0.c();
String suggestKey = ((UltronDataManager) this.mDataManager).getSuggestKey();
String suggestData = ((UltronDataManager) this.mDataManager).getSuggestData();
if (TextUtils.isEmpty(suggestData)) headers.put(suggestKey, strC);
else headers.put(suggestKey, suggestData + strC);
```

`yq0.c()`（`tb/yq0.java:78`）本身是
`BehavixProxy.INSTANCE.getItemData(<deviceScore>, {type:"1"})`，即
**下单页会把 BehaviX 设备评分派生值放进 `Dm-token` 头**——与 §risk 的设备
画像闭环。

下单单据公共参数：`DMQueryKey.getRequestParams()`（`:60`）返回
`{"coupon":"true","coVersion":"2.0"}`（`CheckOrderAndLockedSeatsHelper.COUPON`）。

`UltronDataManager.getHeaderMap()` 与 `DMOrderCreateRequester` 之外，
`DMBaseMtopRequest` 还会给所有请求加：

```java
Map<String, String> mapB = hi0.a().b(getApiName(), null);   // :63
for (String str : mapB.keySet()) mtopBusinessBuild.addHttpQueryParameter(str, mapB.get(str));
setRequestData(mtopBusinessBuild);                          // :67
...
if (ji0.u()) headerMap.put("EagleEye-UserData", "scm_project=" + ji0.t());  // :71
```

`setRequestData`（`:232`）把 `hi0.a().b(apiName, dataParams)` 合并进
`request.dataParams` 后由 `ReflectUtil.converMapToDataStr(mapB)` 序列化成
`data` 串；`hi0.a().c(mapB, apiName)` 负责补 `platform`。

### 10.4 票夹

`mtop.damai.wireless.ticklet2.*`（21 个）+ 旧前缀 `ticklet.*`（4 个）：

| 接口 | 客户端类 | 关键字段 |
|---|---|---|
| `ticklet2.perform.detail.get` | `cn/damai/ticklet/net/TickletDetailRequest.java` | `funcVersion="1.5"`, `orderId`, `performId`, `productSystemId`；version `1.0`，needEcode/needSession 均 false |
| `ticklet2.perform.detail.qrcode` | `cn/damai/ticklet/net/TicketQueryBBCQRCodeRequest.java` | `voucherUniqueKey`；version `1.0`，needEcode/needSession 均 false |
| `ticklet2.perform.detail.get.bind` | — | 绑定态详情 |
| `ticklet2.perform.my.detail.get` | — | 我的票详情 |
| `ticklet2.performs.get` / `.first` / `.history.get` / `.my.get` / `.preload` / `.watched` | — | 场次列表族 |
| `ticklet2.performs.my.ticket.delete` | — | 删除 |
| `ticklet2.transfer.grant` / `.accept` / `.cancel` / `.query` / `.accept.query` | — | 转赠 |
| `ticklet2.extension.list2` / `.exchangesite.list` / `.notice.query` | — | 权益/兑换/公告 |
| `ticklet2.nft.prepareIssue` | — | NFT 票 |
| `ticklet2.souvenir.detail.get` | — | 纪念票 |
| `ticklet.face.binding` / `.face.unbinding` | — | 人脸绑定 |
| `ticklet.comment.get` / `ticklet.performs.preload` | — | 旧族 |

票夹接口的 `NeedEcode`/`NeedSession` 在类里显式声明为 `false`，即客户端认为
票详情与二维码查询**不需要登录态标记**；实际鉴权由 `sid`/`x-sid` 与会话
Cookie 承担（见 §7 与 auth.md）。

### 10.5 座位

`mtop.damai.wireless.seat.*`：`chooseSeatParam`、`dynamicInfo`、`precheck`、
`queryperformseatstatus`、`querypricecolor`、`quickSelect`。
座位图图片与 SVG 的解密在 transfer.md。

## 11. 风险相关响应侧协议

| 现象 | 代码位置 | 语义 |
|---|---|---|
| HTTP 419 + `Bx-action: login` | `mtopsdk/framework/filter/after/AntiAttackAfterFilter.java:35-53` | 请求被要求登录，`RequestPoolManager.Type.SESSION` 入池，`RemoteLogin.login(...)` 后返回 `"STOP"` |
| HTTP 419 + `location` + `x-location-ext` | 同文件 `:55-70` | 交给 `antiAttackHandler.handle(location, x-location-ext)`，请求入 `Type.ANTI` 池并 `"STOP"` |
| `ANDROID_SYS_API_41X_ANTI_ATTACK` | 同文件 `:68`（`ErrorConstant`） | 未注册 handler 或响应不完整时写入的 retCode |
| `FAIL_SYS_REQUEST_QUEUED` | `mtopsdk/mtop/domain/MtopResponse.java:189` | 420 排队（`isRequestQueued`） |
| `FAIL_SYS_ILEGEL_SIGN` | `mtopsdk/mtop/util/ErrorConstant.java:38` | 签名不合法 |
| `FAIL_SYS_FLOWLIMIT` | 同文件 `:30` | 限流 |
| `ANDROID_SYS_API_FLOW_LIMIT_LOCKED` | 同文件 `:14` | 客户端本地 API 锁 |
| `FAIL_SYS_ACCESS_TOKEN_*` 系列 | 同文件 `:17-22` | accessToken 过期/限流/停服 |

完整风控面与评分在 risk.md；`location` 重定向后的验证流程在 privacy.md 与
risk.md 中描述。

## 12. 本节无法证明的部分

- 服务端如何处理 `x-sign`/`wua`/`x-mini-wua`/`umt`，是否比对设备画像，未知。
- 各接口的**服务端**权限判定（能否查看他人票、能否代下单）未知；客户端类
  只声明 `NeedEcode`/`NeedSession`，不构成服务端鉴权结论。
- `netParam` 的实际下发值未知；客户端只实现了位 1/2 的 WiFi 上报分支。
- `Dm-token` 的**生成端**（写入 SharedPreferences 的位置）不在本次还原范围内，
  仅确认读取与拼装路径。
