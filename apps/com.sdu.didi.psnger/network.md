# 网络交互流程与协议格式

## 1. 单一出口与拦截器族

`com.didichuxing.foundation.net.rpc.http.OkHttpRpc` 是唯一 HTTP 出口。业务接口
以自定义 `@o("/path")` 注解声明（不是 Retrofit 的 `@GET`/`@POST`），全部经
OkHttp 拦截器链处理，优先级来自 `@i42.a(priority=…)`：

| 优先级 | 类 | 作用 |
|---:|---|---|
| -990 | `com/didi/security/wireless/adapter/AuthInterceptor` | `secdd-authentication` |
| 990 | `com/didichuxing/security/challenge/DiChallengeInterceptor` | WAF 522 挑战 |
| 999 | `com/didi/security/wireless/adapter/SignInterceptor` | `wsgsig` |
| — | `com/didi/sdk/net/interceptor/NewHeaderContentInterceptor` | `didi-header-hint-content` |
| — | `com/didi/sdk/net/CommonParamsInterceptor` | `terminal_id`、`1` |
| — | `com/didi/sdk/net/ParameterInterceptor` | 公共参数合并 |
| — | `com/didichuxing/dfbasesdk/http/SecurityAccessWsgInterceptor` | `wsgenv` |
| — | `com/didichuxing/dfbasesdk/interceptor/SignerRpcInterceptor` | POST 分类型签名 |
| — | `com/didi/safety/onesdk/encrypt/interceptor/InterfaceEncryptOkHttpInterceptor` | 接口级加密 |
| — | `com/didi/didipay/pay/net/DidipayHeadersInterception` | `x-ddfp`、`Authorization` |
| — | `com/didi/carhailing/net/interceptor/ExtraParamInterceptor` | `ocid` |

`SignerRpcInterceptor.java:26-38` 用 `PARSE_MAP` 按 content-type 分派签名器
（`application`→`z22.c`、`multipart`→`z22.b`），并有 `SKIP_SIGN_API` 集合按
最后一段路径跳过。

## 2. 请求头格式

### 2.1 `didi-header-hint-content`

`NewHeaderContentInterceptor.java:119` 读出该头，`:124-148` 用 Gson 反序列化成
map 并按白名单裁剪，然后写入：

```json
{
  "lang": "<语言>",
  "locale": "<区域>",
  "call_id": "<调用 id>",
  "app_timeout_ms": "<超时毫秒>",
  "Cityid": "<城市>",
  "location_cityid": "<定位城市>",
  "utc_offset": "<时区偏移>",
  "currency": "<货币>",
  "xregionkeyname": "<区域键名>",
  "xregionkeyvalue": "<区域键值>",
  "TripCountry": "<行程国家>"
}
```

`com/didi/sdk/net/HeaderContent.java` 是它的 Gson 模型（已标记废弃）：
`app_timeout_ms, Cityid, currency, lang, locale, location_cityid, utc_offset`。
`:173-177` 对 `/passenger/profile/setsingleoption` 特殊处理，补 `lang`/`locale`。

### 2.2 静态扫描到的请求头

43 个不同的 `addHeader` 名：

```text
Authorization, CityId, Cityid, Cookie, Encode-Version, Minsys, Productid,
Token, TripCountry, User-Agent, X-Cluster-Id, X-DFE-Traffic-Feature, cityId,
didi-header-hint-content, didi-header-omgid, didi-httpdns, lang,
net-lib-source, nuwa-wings, ocid, original_url, secdd-authentication, ticket,
token, voyager-ticket, wsgenv, wsgsig, wsgdid, x-callee-encrypt-sn,
x-city-id, x-ddfp, x-digital-envelope-algorithm
```

其中 `wsgsig`、`secdd-authentication`、`x-ddfp`、`wsgenv` 由风控链路写入，
见 [risk.md](risk.md)。

## 3. 公共参数合并

`ParameterInterceptor` 把公共参数 map 合并进请求，规则取决于 content-type：

| content-type | 合并位置 |
|---|---|
| GET（无 body） | URL query |
| `application/json` | POST body JSON |
| `x-www-form-urlencoded` | 表单字段 |
| 支付域 | `walletParam` 查询 JSON |

`CommonParamsInterceptor` 额外追加 `terminal_id=11` 与常量 `1`。

## 4. 主要业务链路

### 4.1 首页与配置

| 路径 | 用途 |
|---|---|
| `/gulfstream/porsche/v1/dache_homepage_layout` | 首页布局 |
| `/gulfstream/api/v1/passenger/pGetPanelConfig` | 面板配置 |
| `/gulfstream/porsche/v1/dache_homepage_shunt` | 首页分流 |
| `/webx/bronze/page-routes` | WebX 页面路由表 |

首页返回的 `common_params` 里 `skin_style` 决定皮肤（见 [report.md](report.md) §1.7）。

### 4.2 估价

| 路径 | 用途 |
|---|---|
| `/gulfstream/pre-sale/v1/core/pMultiEstimatePriceV3` | 多车型估价 V3 |
| `/gulfstream/mamba/v1/pMultilangEstimate` | 多语言估价 |
| `/gulfstream/mamba/v1/pCarpoolEstimatePrice` | 拼车估价 |
| `/gulfstream/mamba/v1/pMinibusEstimateV2` | 小巴估价 |
| `/gulfstream/mamba/v1/pPetsTravelEstimate` | 携宠估价 |
| `/gulfstream/mamba/v1/pCharterMultiEstimate` | 包车多估价 |
| `/gulfstream/mamba/v1/pCompositeTravelCompare` | 复合出行比价 |

### 4.3 下单与订单

| 路径 | 用途 |
|---|---|
| `pNewOrder` | 创建订单 |
| `anycarNewOrder` | 任意车下单 |
| `pCancelOrder` / `pCancelTrip` | 取消 |
| `pOrderStatus` | 订单状态 |
| `pOrderDetail` | 订单详情 |
| `pGetOrderMatchInfo` / `pOrderMatch` | 撮合 |
| `/gulfstream/transaction/v2/other/pOrderMatch` | 交易撮合 |
| `/intercity/ticket/api/v1/other/pOrderMatch` | 城际撮合 |
| `/gulfstream/transaction/v2/pCreatePrepay` | 预支付 |
| `/gulfstream/api/v1/passenger/platformReassign` | 改派 |
| `/gulfstream/passenger-center/v2/other/pUpdateReassignStatus` | 改派状态 |
| `/gulfstream/passenger-center/v2/other/pPressPushDriverButton` | 催单 |
| `/gulfstream/passenger-center/v2/other/pCommitEvaluate` | 评价 |
| `/gulfstream/porsche/v1/bronze/getFullPageInfoLayout` | 全页布局（进行中/结束态复用） |
| `/gulfstream/passenger-center/v2/other/pInTripLayout` | 行程中布局 |

订单接口名白名单在 `com/didi/carhailing/framework/net/k.java:10`；区域白名单在
`com/didi/quattro/common/net/RegionInterceptor.java:28`。

### 4.4 下单调用链

```text
NextNavigationInterceptor.java:32
  host ∈ {dache_anycar, pincheche, flash, unitaxi, dache, care_premium,
          firstclass, intercity, nav_anycar, router, casper}
  path ∈ {/entrance, /entrance_v2, /endservice, /scenehome, /scene_home/mix,
          /confirm, /call_car, /call_car/setting, /tailor_service,
          /intercity_car/multi_confirm, /onestop_confirm,
          /combined_travel_detail, /wait, /wait/v2, /inservice,
          /premium_tailor_service, /invitation, /invitation_detail,
          /minibus/home, /intelligent_minibus/select_station,
          /station_bus/confirm, /pack_car/home, /pack_car/confirm, /page}
  scheme = onetravel
     |
     v
QUCreateOrderInteractor.createOrderWithConfig(config)   (行 606)
  -> QUCreateOrderRouter.createOrderWithConfig            (行 30)
  -> pNewOrder / anycarNewOrder
     |
     v
pOrderMatch -> pOrderStatus -> /inservice -> /endservice
```

`QUOtherNavigationInterceptor.java:48,135` 覆盖 `/gohome`、`/gocompany`、
`/sendorder`、`/live_activity`、`/airport_order`、`/station_guidance`。

### 4.5 无人车（robotaxi）

`com/didi/voyager/robotaxi/foundation/oknet/f.java` 单独一组 `@o` 接口：

```text
/ordercloud/v1/passenger/estimate/
/ordercloud/v1/passenger/create_order/
/ordercloud/v1/passenger/cancel_order/
/ordercloud/v1/passenger/complete_order/
/ordercloud/v1/passenger/update_position/
/ordercloud/v1/passenger/find_walk_route/
/ordercloud/v1/passenger/update_estimate_route/
/ordercloud/v1/passenger/estimate_new_destination/
/ordercloud/v1/passenger/request_change_destination/
/ordercloud/v1/passenger/request_change_pickup_point/
/ordercloud/v1/passenger/reorder_check/
/ocgo/public/v1/passenger/events
/ocgo/public/v1/passenger/set_network_auto_unlock
/ocgo/public/v1/passenger/qq_music/decrypt
```

### 4.6 安全与客服

| 路径 | 用途 |
|---|---|
| `/api/guard/psg/v2/dashboardConfig` | 安全面板配置 |
| `/api/guard/psg/v2/getShieldStatus` | 安全盾状态 |
| `/api/guard/psg/v2/getPassengerReportInfo` | 乘客上报信息 |
| `/api/guard/escort/shareLive` | 行程分享 |
| `/api/guard/escort/getLiveShareCardInfo` | 分享卡片 |
| `/api/report/reportPassengerStatus` | 乘客状态上报 |
| `/api/report/reportPassengerSecuritySetting` | 安全设置上报 |
| `/usernotice.xiaojukeji.com/notice/passenger` | 通知 |

### 4.7 DRN / Hummer 动态包

```text
/bundle/api/pre/query
/bundle/api/batch/query
/bundle/api/single/query
/bundle/api/get/config
/bundle/api/recommend/download
/bundle/api/getLockBundle
/bundle/api/get/subPackages
/bundle/info
```

### 4.8 主机分布

静态提取 211 个主机。业务主机集中在：

```text
api.diditaxi.com.cn          api.udache.com
common.diditaxi.com.cn       conf.diditaxi.com.cn
pay.diditaxi.com.cn          mapi.xiaojukeji.com
llab-asst.xiaojukeji.com     poi.map.xiaojukeji.com
api.map.diditaxi.com.cn      asrwyc.xiaojukeji.com
```

静态资源：

```text
gift-pypu-cdn.didistatic.com   dpubstatic.udache.com
ut-static.udache.com           page.udache.com
page.xiaojukeji.com            s3-*.didistatic.com
```

## 5. 接口级加密（onesdk）

`com/didi/safety/onesdk/encrypt/interceptor/InterfaceEncryptOkHttpInterceptor`
只处理 POST 且 content-type 为 `application/json` 或 `multipart` 的请求：

| 约束 | 值 | 位置 |
|---|---:|---|
| multipart part 数上限 | 256 | `:72-73` |
| 单字段大小上限 | 4 MiB | `:95` |
| 字段名重复 | 抛错 | `:90-91` |
| part 名为空 | 抛错 | `:88` |

JSON 请求把原文替换为 `{ "key": <Base64 会话密钥>, "enReq": <密文> }`；
multipart 请求把 `key` 与 `enReq` 作为两个 form part 附加
（`:105`）。`SgConstants.KEY` 常量值为 `"key"`。

响应侧 `e81/a.java:50-55` 读 `enRes`，`:85-86` 对 `TYPE_FACE` 业务额外处理
`result.enRes`，解密入口是 `x22.a.a(...)`。

## 6. 加密算法闭包

| 步骤 | 实现 | 位置 |
|---|---|---|
| 生成会话密钥 | `KeyGenerator.getInstance("AES")` + `init(128)` | `com/didi/ride/util/g.java:129-132` |
| 编码密钥 | Base64（自定义表） | `f32/d.java:13,93-130` |
| 压缩明文 | GZIP | `f32/k.java:20-30` |
| 加密 | `Cipher.getInstance(f87767a)` + `SecretKeySpec(key,"AES")` | `f32/a.java:23-27` |
| transform 字符串 | `"SWA=WQP=BYQA'Bsvv{|u"` 逐字节 XOR 18 | `f32/a.java:11-20` |
| 解密响应 | 同 transform + `Cipher.DECRYPT_MODE` | `x22/a.java:36-37` |
| 解压响应 | `GZIPInputStream` | `x22/a.java:43` |

`f32/a.java` 静态块还原结果：

```java
byte[] bytes = "SWA=WQP=BYQA'Bsvv{|u".getBytes();
for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)(bytes[i] ^ 18);
f87767a = new String(bytes);   // "AES/ECB/PKCS5Padding"
```

这是字符串混淆，不是未知算法。Base64 解码表在 `f32/d.java:15`，字符集在
`:13`（标准 Base64 字母表）。`f32/d.b(byte[])` 是编码，`f32/d.a(String)` 是解码。

## 7. 请求签名

见 [risk.md](risk.md) §2。摘要：`prepareSign` 把 query 对与 body 十六进制拼成
字符串，`doSign` 交给 `libdidiwsg.so`，结果写入 `wsgsig` 头。

## 8. 传输层

`com/didiglobal/rabbit` 提供 Cronet/OkHttp 双栈。`TransInterceptor.java:41`
在超时或传输错误时合成 HTTP `666666` 响应。`didinet.c.a()` 提供证书与 socket
工厂，`t12.a.f110920a.b("disable_certificate_encryption_toggle")` 控制是否启用
自定义证书校验（`ce1/p.java:98`）。
