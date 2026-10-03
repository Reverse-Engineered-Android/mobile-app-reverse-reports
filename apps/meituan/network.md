# 网络交互与协议格式

## 1. 调查边界

本页仅使用 APK 内 DEX、资源、配置字符串和 native 导入/字符串做静态分析。
支付、周边餐厅、到店项目和评价接口均未发送请求；URL 是反编译接口或调用点
中的字面量，不表示本次验证过服务端可达性。

## 2. 客户端网络入口

| 入口 | 用途 | 可见协议 |
|---|---|---|
| `com.sankuai.meituan.retrofit2` | 业务 API | GET/POST/PUT、query/form/json/multipart |
| OkHttp + `Ok3*Interceptor` | 通用客户端 | `mtgsig`、body 缓存、pin 检查 |
| NVNetwork/Shark | DFP、灾备、mock 隧道 | byte[] POST、headers、host/path 策略 |
| WebView/Titans/KNB | 页面接口、图片、Yoda 校验 | JS bridge、JSON、`Sec-Yoda` 响应头 |
| MRN/React Native | 页面业务接口 | Retrofit/JSON、动态 `@Url` |
| 对象存储/CDN | 文件和媒体 | multipart、PUT、GET response body |

典型静态域名/接口族：

- `https://apimobile.meituan.com/group/v4/poi/search`
- `https://promotionapi.sankuai.com/content/collect`
- `https://optimus-mtsi.meituan.com/mtsi-worker/{business}`
- `https://appsec-mobile.meituan.com/api/{latte,ristretto,americano,espresso,cappuccino,affogato}`
- `http://apihotel.meituan.com/group/v1/deal/list/id`
- `api.maoyan.com`、`pushopt-wpt.meituan.com`

## 3. 请求头与签名流程

### 3.1 `mtgsig` 收集面

`CommonCandyInterceptor.getRequestSignature()` 的实际分流如下：

```java
map.put("Content-Encoding", str3);       // 有 content-encoding 时
if (method.equals("post")) {
    IIVTQYOSF(context, uri, body, contentType, headers, out, extra);
} else if (method.equals("get")) {
    IIVTQYOSF(context, uri, query, headers, out, extra);
} else {
    IIVTQYOSF(context, uri, body, query, headers, method, out, extra);
}
return out.get("mtgsig");
```

`IIVTQYOSF` 创建匿名 `CandyBaseMaterial`，其中：

- `getFinalUri()` / `getHost()`：最终 URI 与 host；
- `getContentType()`：原 `Content-Type`；
- body/method/query/header：分别由重载参数闭包；
- 核心调用是 `ShellBridge.main3(120, new Object[]{ CandyBaseMaterial })`；
- `MTGuard.loadInitSuccess == false` 时不签名，只上报 `s/m` / code `SIGN_IN_FAILED`；
- native 返回 `mtgsig` 后放入新 header/URI，`sig_ignore` 时不写 header。

因此服务端可静态证明的签名输入至少包括：HTTP method、最终 URL/host/path、
query、POST body、content type、content encoding、原始 headers、版本/环境
字段。具体哈希链、域分隔符和随机盐由 native 命令 120 实现，不在 Java 中。

### 3.2 `Ok3CandyInterceptor`

1. `initOriginalHeaders()` 复制调用方已有 header；
2. `RequestBody` 非空时写入 `ByteArrayOutputStream`；
3. `IIVTQYOSF(...)` 对完整缓冲 body 计算；
4. 对原 URI/header 构造新请求；
5. `chain.proceed(newRequest)` 发送。

这意味着签名覆盖对象存储、表单、JSON 等 POST body 的实际字节；流式上传若在
该拦截器内会被缓冲，重定向前的 URI 参与计算。

### 3.3 旧版 `gmtkby`

JADX dump 显示：

- query/value 先百分号编码，再按 key/value 双重排序；
- body 只取 `min(body.length, 16200)` 字节；
- 拼接 `methodBytes || bodyPrefix || hostBytes`；
- `MainBridge.main3(2, {bytes, hostBytes})` 返回字符串；
- 结果非 `sig_ignore` 时只写 `mtgsig`；
- 空结果上报 code `DRIVE_EXTERNAL_STORAGE_REQUIRED`，native 不可用报
  `RESTRICTED_PROFILE`，body 缺失报 `SERVICE_UPDATING`。

它是兼容路径，不替代命令 120 的完整 body 收集。

## 4. DFP 指纹协议

`BaseReporter.SEC_HOST = "https://appsec-mobile.meituan.com"`；content type
可为 `application/json`、`text/plain; charset=ISO-8859-1` 或
`application_stream`。POST body 是 native 产出的 byte[]，DEX 只可见长度与
content type，不能据此还原密文明文字段。

| 功能 | 路径 | 请求编码 |
|---|---|---|
| DFPID | `/api/latte` | DFP 配置指定的 JSON/text/stream |
| XID | `/api/ristretto` | 同上 |
| 设备信息 | `/api/americano` | 同上 |
| 心跳 | `/api/espresso` | 同上 |
| mini-fama | `/api/cappuccino` | 同上 |
| bio/证据 | `/api/affogato` | 同上 |

`Ok3NetworkInterceptor` 的 pin 表共 14 项，例如
`RpiZH+XxEsJDDVmHh4nbb/cObbh4qrb70P42QrxwkP8=`、
`8Rw90Ej3Ttt8RRkrg+WYDS9n7IS03bk5bjP/UXPtaY8=`。
匹配条件是 X.509 公钥 DER 的 SHA-256 Base64；全部证书不匹配时：

```java
MainBridge.main3(50, new Object[]{303});   // event 50, index/code 303
// key 常量 RAPTOR_MITM_KEY = "n/m/c"
if (gmtkby.jefswxstkc.booleanValue() && NVGlobal.isInit()) {
    chain.call().cancel();
    throw new IOException("Canceled");
}
```

## 5. 周边餐厅、项目和评价（静态）

### 5.1 周边餐厅/POI

`MapUnityAPI` 的 `GET mapchannel/poi_nearby` 精确 query：

```text
cityId, poiId, <count flag>, longitude, latitude, cateIds,
pageSize, page, mapSource, os, sdkVersion, version, key
```

`GET mapchannel/poi_detail`：

```text
poiId, longitude, latitude, mapSource, os, sdkVersion, version, key
```

`GET mapchannel/dynamic_search`：

```text
mapSource, poiId, poiIdEncrypt, stage, key, longitude, latitude,
kindCode, dynamicMapVersion, carPark, userLocation,
perimeterSearchFlag, locationOpenFlag, poiChannel
```

`POST mapchannel/geo_dynamic_search` 和 `mapchannel/map_area_search`
使用 `RequestBody`；静态代码将 body 交给 Gson/JSON 通道，具体动态字段由页面
调用方填充。

### 5.2 到店项目/团购

搜索主请求是 `GET https://apimobile.meituan.com/group/v4/poi/search`，并支持
同 URL 的 JSON `POST`。辅助接口：

| 方法 | 路径 | 可见字段/返回 |
|---|---|---|
| GET | `v4/poi/search/count/{cityId}` | `cityId` + `@QueryMap`；`FilterCount` |
| GET | `v4/poi/search/history/{cityId}` | `cityId` + `@QueryMap`；搜索历史 |
| GET | `v4/poi/search/address` | `@QueryMap`；`SearchPoiModel` |
| GET | `v1/deal/searchpage/hotword/city/divide/{refreshType}/{cityId}` | 路径 + query；热门词 |
| GET | `v1/deal/search/suggest/{cityId}` | 建议词 |
| GET | `v4/poi/search/extensioninfo` | 扩展信息 |
| POST | `http://apihotel.meituan.com/group/v1/deal/list/id` | path 中的 deal id |
| GET | `mapchannel/shop_guide_info` | `poiId, poiIdEncrypt, os, sdkVersion, version, graphGuideVersion, userLocation, poiLocation, key` |

项目列表的动态 URL 和业务 query map 在运行时组装；静态证据能证明接口族、
HTTP 方法、命名字段和返回类型，不能从空 map 推断所有服务端参数。

### 5.3 评价

- `mapchannel/shop_guide_info` 返回门面与评论数据 `d`，query 包含
  `poiId`、`poiIdEncrypt`、用户位置和门店位置；
- `group/v2/deal/xxx/comments` 被 `food` 模块登记为 `deal_comment`；
- 猫眼评论族有 `GET review/v2/comments.json`、
  `GET review/v1/comments/info.json`、
  `GET review/v2/comments/movie/tag.json`；
- 评论提交/修改走 `POST/PUT review/v1/comments.json`，审批/举报走
  `POST/DELETE review/v1/comments/approve.json`、
  `spamreport.json`。

本节没有调用任何评价接口，也没有验证分页、排序或敏感词策略。

## 6. 支付接口（静态）

### 6.1 核心表单

| 方法 | 路径 | 字段 |
|---|---|---|
| POST | `/qdbverify/getidentityinfosignature` | `yzt_random_num` |
| POST | `/qdbpay/gettraninfosignature` | `trans_id, pay_token, client_cert_content` |
| POST | `/qdbverify/getuseridentityinfo` | `real_name_scene` |
| POST | `/qdbverify/digicertdownloadresultnotify` | `cert_download_status, cert_serial_no, verify_types` |
| POST | `/qdbverify/publicverify` | `merchantNo, verifyNo, orderNo, scene, risk_partnerid` + `@Encrypt` 的 `verify_type/challenge/finger_type/pay_password/extra map` |
| POST | `/hellopay/uploadcardimg` | `@FieldMap` + `nb_fingerprint` |
| POST | `{path}`（Soter key） | `@Encrypt @FieldMap` + `nb_fingerprint` |

### 6.2 订单/支付流程接口

- 外卖：`v6/payment/checkpay`、`v8/order/checkprepay`、
  `v6/payment/genpay`；
- 猫眼：`POST /createorder/v6/submitpay.json`、
  `/nexus/createorder/create.json`、`/nexus/createorder/price.json`、
  `/nexus/createorder/unpaid.json`；
- 支付 uniffi：`@GET("{path}")`、`@POST("{path}")`，
  动态 path 由 `NetworkRequestService` 传入；
- 找回支付密码：配置 host + `/api/mpm/findpayhash/redirect`。

`MoviePayOrderApi` 的接口还包括 `sns/agreement/sign.json`、
`coupon/user/cashcouponV2.json`、券种与多支付信息查询。所有字段均来自
`@QueryMap/@FieldMap`；静态报告不猜测 map 中未在方法签名出现的键。

### 6.3 支付安全边界

- `@Encrypt` 是 Retrofit 自定义注解，字段在请求编码阶段由支付加密层处理；
- `libPayRequestCrypt.so`、`libentryexpro.so`、`libCGCipherSDK.so`
  分别服务支付请求、银联协议、国密算法；
- `@FormUrlEncoded` 只说明线格式，不表示敏感字段是明文；
- 本次没有读取真实交易，因此不声称交易成功、限额或服务端鉴权结果。
