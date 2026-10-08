# 网络交互流程与协议格式

闲鱼客户端所有业务请求都走阿里 MTOP 网关（MtopSDK 3.x + `anet`/`tnet` 通道）。
本文件描述**静态可证**的请求构造、报文字节格式、签名头与重定向映射。

## 1. 分层结构

```
业务层   BaseApiProtocol 子类（@ApiConfig 注解声明 api/apiVersion/needLogin/needWua）
   │
   │  PApiContext.send()                                    ← 接口协议
   ▼
调度层   MtopLauncher.send() → ClientHeaderInterceptor → MtopSend → ApiBusiness
   │
   │  MtopRequest{ api, v, data }  +  MtopBuilder
   ▼
协议层   mtopsdk.mtop.protocol.builder.InnerProtocolParamBuilderImpl
   │  mtopsdk.mtop.protocol.converter.impl.InnerNetworkConverter
   ▼
签名层   mtopsdk.security.InnerSignImpl（SecurityGuard 中间层/统一签名/AVMP）
   │
   ▼
网络层   anetwork.channel.DegradableNetwork → anet.channel.SessionCenter
         （HTTP/2、SPDY、QUIC；域名由 MTOP 单元策略与 GRS 下发）
```

- 实例与域名绑定（`MtopLauncher.initMtopSdk`）：
  `MtopSetting.setMtopDomain("acs.m.goofish.com", "acs.wapa.goofish.com", "acs.wapatest.goofish.com")`，
  并为淘宝域单独设 `guide-acs.m.taobao.com`。`MtopAccountSiteUtils.bindInstanceId("xianyu")`。
- `registerTtid(ttid)`，`ttid` 资源值 `700502`，完整 `ttid` = `36137` + `21407387` +
  `700502`（`R.string.ttid`，`Env.getTtid()`）。
- `SessionCenter.init(application, <appKey>, ENV.ONLINE)`；`appKey = 21407387`
  （`ReleaseEnvProperties.getAppKey()`）。

## 2. 请求 URL 结构

`AbstractNetworkConverter.buildBaseUrl` 拼接：

```
<protocol>://<domain>/<entrance>/<api>/<version>/
```

- `protocol`：`http` 或 `https`（默认 `https`）。
- `domain`：在线 `acs.m.goofish.com`；预发 `acs.wapa.goofish.com`；日常 `acs.wapatest.goofish.com`。
  可用 `customOnlineDomain`/`customPreDomain`/`customDailyDomain` 覆盖。
- `entrance`：`gw`（网关），部分场景为 `h5`/`mtop`。
- 例：`https://acs.m.goofish.com/gw/mtop.taobao.idle.item.detail/4.0/`。

`EnvModeEnum` 决定环境：`ONLINE=0`、`PREPARE=1`、`TEST=2`、`TEST_SANDBOX=2`
（`AbstractSignImpl.getEnv`、`InnerSignImpl.getMiddleTierEnv`）。

## 3. 公共请求头与转换表

`InnerNetworkConverter` 静态表把 XState 键映射为线上头名：

| 线上头 | XState 键 | 内容 |
| --- | --- | --- |
| `x-sid` | `sid` | 会话 ID（淘宝/闲鱼） |
| `x-t` | `t` | 毫秒时间戳 |
| `x-appkey` | `appKey` | `21407387` |
| `x-ttid` | `ttid` | 渠道+appkey+版本 |
| `x-devid` | `deviceId` | 设备标识 |
| `x-utdid` | `utdid` | UTDID |
| `x-sign` | `sign` | MTOP 请求签名 |
| `x-uid` | `uid` | 用户 ID |
| `x-umt` | `umt` | UMID token |
| `x-reqbiz-ext` | `reqbiz-ext` | 业务扩展 |
| `x-router-id` `x-place-id` `x-open-biz` `x-mini-appkey` `x-req-appkey` `x-open-biz-data` `x-act` | 同名 | 开放/小程序路由 |
| `x-mini-wua` | `x-mini-wua` | 小程序 WUA |
| `x-app-conf-v` `x-exttype` `x-extdata` `x-features` | 同名 | 配置版本/扩展/特性 |
| `x-page-name` `x-page-url` `x-page-mab` `x-app-ver` `x-orange-q` | 同名 | 页面与灰度 |
| `x-nettype` `x-nq` `x-pv` | 同名 | 网络类型/质量/PV |
| `x-sgext` `x-s-c` | `x-sgext` `x-sign-control` | SecurityGuard 扩展与签名控制 |
| `x-falco-id` `f-refer` `x-netinfo` `x-accept-stream` | 同名 | Falco/引用/网络信息/流式 |
| `user-agent` `c-traceid` | 同名 | UA 与链路 |

闲鱼自有头（`ClientHeaderInterceptor`）：

| 头 | 来源 |
| --- | --- |
| `imei` | `fish_imei` SP |
| `umid` | `FishUmidHelper.getSecurityToken` |
| `oaid` / `aliOaid` | OAID 回调（`FishOaid`） |
| `x-magic_device` | 是否折叠屏/魔法屏 |
| `x-screen-level` | 屏幕等级字符串 |
| `first_open` | 首次启动标志 |
| `x-custom-cache-uid` | 登录用户 ID |
| `x-custom-privacy-device-recommend-closed` | 个性化推荐关闭标志 |
| `minors_mode_enabled` | 未成年人模式 |
| `channel_2` / `fish_preinstall` / `fish_preinstall_channel` | 渠道 |

## 4. 请求报文字节格式

`InnerProtocolParamBuilderImpl` 与 `AbstractNetworkConverter` 定义：

- `MtopRequest{ api, v, data }`，`data` 为业务字段序列化串（`ReflectUtil.converMapToDataStr`）。
- 默认 `GET`（`MethodEnum.GET`），`needPost` 或大数据量时为 `POST`
  （`MtopSDKInitConfig...needPost(true)`），表单
  `application/x-www-form-urlencoded;charset=UTF-8`。
- `needJsonReq=true` 的接口把整个参数包进 `RequestWrapper{ "req": "<json>" }`
  （`ApiBusiness.parseAnno`）。
- 响应为 JSON 信封：
  ```json
  { "api": "...", "v": "...", "ret": ["SUCCESS::调用成功"], "data": { ... } }
  ```
  `MtopResponse.getRetCode()/getRetMsg()/getDataJsonObject()`；`ret[0]` 以 `::` 分隔
  状态码与描述，`ResponseParameter{ ret[], data, code, msg, api, version }` 与之对应。
- 二进制/流式上传单独走 `uploadv2.do`（见 [transfer.md](transfer.md)）。

## 5. 请求签名 x-sign 的完整生成

`InnerSignImpl` 是唯一实现（`AbstractSignImpl` 全空，`LocalInnerSignImpl` 为降级）。

### 5.1 待签名串 `convertInnerBaseStrMap`

按固定顺序以 `&` 连接（空值补 `""`）：

```
utdid & uid & <reqbiz-ext> & <appkey> & md5(data) & t & api & v & sid & ttid &
deviceId & lat & lng & [extdata &] x-features &
routerId & placeId & open-biz & mini-appkey & req-appkey & accessToken & open-biz-data
```

- `appkey` 是形参 `str`，即当前请求的 appkey（`21407387`）。
- `md5(data)` = `SecurityUtils.getMd5(data)`，仅对请求体做 MD5。
- `extdata` 仅在 `z=true` 时无条件加入；`getSign()` 传 `false`，因此只有非空才追加。

### 5.2 三条签名路径

`getMtopApiSign(params, appKey, authCode)`：

1. `SwitchConfig.getUseSecurityAdapter() & 1 == 1` → 先试 `getSign()`；
2. `getSign()`：`SecurityGuard` 中间层 `IMiddleTierGenericComponent.getSign()`，
   入参 `{ "data": bytes(INPUT), "env": getMiddleTierEnv(), "appkey": appKey }`，
   返回头 `x-sign`（其余返回项回填 `params`）；
3. 兜底 `SecurityGuardParamContext{ appKey, requestType=7, paramMap={ "INPUT": 待签名串 } }`
   → `ISecureSignatureComponent.signRequest(ctx, authCode)`。
   请求类型 7 = `OPEN_ENUM_SIGN_ATLAS_FAST`（`SecureSignatureDefine`）。

`getCommonHmacSha1Sign(str, appKey)`：`requestType=3`
（`OPEN_ENUM_SIGN_COMMON_HMAC_SHA1`）。

`getUnifiedSign(...)`：`IUnifiedSecurityComponent.getSecurityFactors(...)`，
一次返回 `x-sign`+`x-mini-wua`+`x-sgext`+`x-umt` 等全部安全因子；
SSR 请求改用 `convertSsrBaseStrMap`（含 `ssr-pv` 分支）。

### 5.3 WUA / miniWUA / 安全体

- `getWua` → `IMiddleTierGenericComponent.getWua({ "data": bytes, "env": n })`，
  返回 `wua` 与附加头。
- `getMiniWua` → 追加 `extend_paras = { "api_name": api }`，返回 `x-mini-wua`。
- `getSecBodyDataEx` → `ISecurityBodyComponent.getSecurityBodyDataEx(...)`，产生设备安全体。
- `getAvmpSign` → `AVMPGenericComponent.createAVMPInstance("mwua", "sgcipher")`。

### 5.4 签名降级

`SignDegradedUtils.isSignDegraded`：命中远端 `signDegradedApiList`（支持 `*`）或
AB 实验 `mtop_sign_degraded*` 时，该 API 跳过 SecurityGuard 签名，
`LocalInnerSignImpl.getMtopApiSign` 用本地 HMAC-SHA1 或空签名。
`x-bx-resend` / `bx-sleep` / `bx-usesg` / `x-bx-version` 是重放与降级开关。

## 6. 业务 API 注册表

`com.taobao.idlefish.protocol.api.annotations.Api` 枚举集中声明
`(api, version, 中文说明)`，共 **317** 条；另有一批无枚举实体的
`@ApiConfig(apiName=...)` 接口。静态可解析的请求类共 **320** 个，其中
**55** 个 `needLogin=true`，**5** 个 `needWua=true`。

主要类目（节选）：

| 功能 | 代表接口 |
| --- | --- |
| 首页/推荐 | `mtop.taobao.idle.home.firstdata/5.0`、`home.nextfresh/3.0` |
| 商品详情 | `mtop.taobao.idle.item.detail/4.0`、`mtop.taobao.idle.essay.detail/2.0` |
| 搜索 | `com.taobao.idle.item.search/4.0`、`mtop.taobao.idle.main.item.search/4.0`、`search.spusimilar.itemlist/1.0` |
| 订单/交易 | `mtop.taobao.idle.trade.order.create/2.0`、`com.taobao.idle.user.my.order/1.0`、`mtop.order.doPay/4.0` |
| 评价 | `mtop.taobao.idle.rate.list/2.0`、`mtop.taobao.idle.rate.detail/2.0`、`mtop.taobao.idle.rate.create/2.0` |
| 留言/评论 | `com.taobao.idle.comment.list/2.0`、`mtop.taobao.idle.comment.publish/2.0` |
| 私聊/消息 | `mtop.idle.x.message.message.send/1.0`、`session.sync/2.0`、`mtop.taobao.idle.message.order/2.0` |
| 用户主页 | `mtop.taobao.idle.user.page.info`、`user.headinfo.get/4.0`、`user.publish.items/6.0` |
| 设备上报 | `mtop.taobao.idle.device.report/1.0`（`needWua=true`） |
| 位置 | `com.taobao.idle.location.update/1.0`、`location.citychange/1.0` |

## 7. 灰度接口重定向

`ApiBusiness` 静态表在开关打开时把旧 api/ver 换到新网关：

| 开关 | 旧 → 新 |
| --- | --- |
| `search_api_fl_switch` | `mtop.taobao.idle.main.item.search/4.0` → `mtop.taobao.idle.main.search.glue/1.0`；`search.shade/1.0` → `mtop.taobao.idlemtopsearch.search.shade/3.0`；`search.suggest/1.0` → `mtop.taobao.idlemtopsearch.search.suggest/2.0`；`item.search.filter/3.0` → `mtop.taobao.idlemtopsearch.item.search.filter/4.0` 等 |
| `main_api_fl_switch` | `home.firstdata/5.0` → `mtop.taobao.idlehome.home.firstdata/6.0`；`home.nextfresh/3.0` → `mtop.taobao.idlehome.home.nextfresh/4.0` |
| `msg_api_fl_switch` | 全部 `mtop.idle.x.message.*` → `mtop.taobao.idlemessage.*`（如 `message.send`、`session.sync`、`p2p.data.send`） |

## 8. 通道与协议

- 传输层 `anet.channel`（libtnet.so / libtb_ssl.so），支持 HTTP/2、SPDY、QUIC
  （`liblsquic.so`、`libxquic.so`）、DNS 由 GRS/HttpDNS 下发（`libdps.so`、`httpdns.db`）。
- `mtopsdk.mtop.xcommand` 实现长连接下推指令，`xstate/network` 提供跨进程 AIDL 状态同步。
- 消息通道另有 ACCS（`com.taobao.accs`），承载推送与 IM 长连接；
  `accs.db` 记录各 host 的流量与业务（`serviceid`/`bid`/`isbackground`/`size`）。

## 9. 静态可证事实与不可证部分

- **已验证**：URL 结构、转换头名、待签名串字段顺序、三条签名路径、请求/响应信封、
  317 条 API 注册、55 个 `needLogin`、5 个 `needWua`、灰度重定向表。
- **结构已证实**：SecurityGuard 中间层返回的 `x-sign`/`x-mini-wua`/`x-sgext`/`x-umt`
  字节格式与生成入口，但算法在加固 ELF 内。
- **不可证**：服务端如何用 `wua`/`umid`/`x-sign` 做风控打分；MTOP 网关的实际路由与限流阈值。
