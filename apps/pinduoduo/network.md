# 网络交互与协议格式

## 1. OkHttp 客户端构造

入口 `com.aimi.android.common.http.f` 的静态块：

```java
new OkHttpClient.b()
    .i(10L, SECONDS)        // connectTimeout
    .F(10L, SECONDS)        // readTimeout
    .A(30L, SECONDS)        // writeTimeout
    .o(new HttpDns())
    .b(new com.aimi.android.common.http.unity.internal.interceptor.d())  // 熔断 / 降级
    .b(new com.aimi.android.common.http.unity.internal.interceptor.i())  // 重定向 / CDN / URL 改写
    .b(new com.aimi.android.common.http.unity.internal.interceptor.b())  // https -> http 改写
    .b(new ev1.l())                                                      // 指标
    .j(new okhttp3.k(1, 55L, SECONDS))                                   // 连接池
    .B(OkHttpClient.StartedReqRetryOnConnectionFailureStrategy.CanRetryGET)
    .f();
```

`CanRetryGET` 表示**只有幂等的 GET** 会在连接失败时重试。另有 `f.a("...")`
判定 `/api/lamer/uuid/report` 为特殊直连路径。

## 2. 域名分类

| 类别 | 域名 |
| --- | --- |
| 主 API | `mobile.yangkeduo.com`、`api.pinduoduo.com`、`apiv2/apiv3/apiv4.yangkeduo.com` |
| 元信息 / 配置 | `meta.pinduoduo.com` |
| CDN 图片 | `cdn.pddpic.com`、`mcdn.pddpic.com`、`img.pddpic.com`、`commimg.pddpic.com` |
| 文件 | `file.pinduoduo.com` |
| APM / 日志 | `apm.pinduoduo.com`、`log.pinduoduo.com` |
| 长连接 / 推送 | `ws.pinduoduo.com`、`social.pinduoduo.com`、Titan 集群 |
| 测试环境 | `apiv2.hutaojie.com`、`api-staging.yangkeduo.com` |

`sv1.c.c(Context)` 在 TEST/staging 环境下会把主域名换成上述测试域名，否则走
`b.h().c()` 的运行时下发值。

## 3. 请求头的四层结构

### 3.1 公共头 — `sv1.d.e(boolean)`

| 头 | 值 | 条件 |
| --- | --- | --- |
| `Content-Type` | `application/json;charset=UTF-8` | 总是 |
| `Referer` | `Android` | 总是 |
| `AccessToken` | `q1.c.z()` | `e(true)` 且非空 |
| `lat` | `q1.c.I()` | `e(true)` 且非空 |
| `ETag` | `bn0.b.a().d()` | 总是 |

### 3.2 UI / 环境头

| 头 | 构造 | 来源 |
| --- | --- | --- |
| `x-app-ui` | `urlencode("dm=<日夜模式>&zm=<缩放>")` | `sv1.d.d()` |
| `x-pdd-info` | `urlencode("bold_free=..&bold_product=..&front=0\|1&tz=..&ccy=..")` | `sv1.d.f()`，`tz`/`ccy` 由 AB `ab_enable_fill_currency_and_timezone_header_80800` 控制 |
| UA 查询串 | `"width=..&height=..&dpr=..&net=" + e.i() + "&brand=..&model=..&osv=..&appv=..&pl=2"` | `sv1.d.g()` = `b() + e.i() + a()` |

Android UA 常量在 `l43.d.a()` = `"android/okhttp"`；插件 UA 在 `tf1.a.z()`。

### 3.3 反爬头 — `anti-token`

单例 `com.aimi.android.common.http.i`（`i.e.f10796a`）产生，两条路径：

```java
Map a(String path, nv1.h monitor, boolean forceNewShortToken)   // 短 token
Map b(String url, boolean forceNewShortToken, nv1.h monitor)    // 长 token
```

- 短 token：`qb2.d.b().p(context, bn0.b.a().d())` → `SecureNative.p`，产物长度约
  109 字符（日志 `len:` 指标）。
- 长 token：`qb2.d.b().B(context, TimeStamp.getRealLocalTime())` →
  `SecureNative.b(Context, Long)`。
- 两种都是 `Map{"anti-token": <value>}`；获取失败时**返回 null**，并把失败原因
  通过 `i.d(url, throwable)` 上报 ITracker `Module(30074).Error(20020)`。
- 隐私闸门 `ac2.b.n()` 未通过时直接返回 null 并记 `privacy not pass`。
- URL 处理：`Uri.getPath()`，以 `/` 开头则去掉首字符，然后查白/黑名单。

远端开关（`RiskControl` 配置节）：

| 键 | 结构 | 默认 |
| --- | --- | --- |
| `RiskControl.anti_token_list_new` | `["path", ...]` 精确匹配 | `[]` |
| `RiskControl.anti_token_list2_new` | `[{"prefix":..,"suffix":..}, ...]` 前后缀匹配 | `[]` |
| `RiskControl.anti_token_black_list` | `["path", ...]` 精确排除 | `[]` |
| `RiskControl.anti_token_black_list2` | `[{"prefix":..,"suffix":..}, ...]` 前后缀排除 | `[]` |

匹配逻辑：`e(path)` 命中白名单返回 true → 走短 token；`f(path)` 命中黑名单返回
true → 返回 null（不加头）；都不命中且 `forceNewShortToken=false` → 走短 token；
否则走长 token。

### 3.4 签名头 — `ApiSignatureProcessor`

`com.aimi.android.common.http.a`，两个开关默认均为 true：

| 字段 | AB 键 | 默认 |
| --- | --- | --- |
| `f10679b` | `ab_api_signature_enable_v1_70400` | `true` |
| `f10678a` | `ab_enalbe_use_v2_wrap_signature_72500` | `true` |

**v1**（`a.e(url, body, accessToken, isDuplex)`）：路径经 `rv1.h.b()` 归一化后匹配
`Network.add_signature_apis`：

```
["/api/jinbao/utils/add/checkclick", "/api/apollo/query_login_history"]
```

命中且 `AccessToken` 存在时：

```java
qb2.d.b().H(accessToken, appVersion, "" + nowMs, path, queryBytes, bodyBytes, isDuplex, outMap)
// -> SecureNative.i(...) -> SE.as(...)
```

路径还会经过 `a.a(path)`：若以 `Network.cross_origin_path_prefix`（默认
`/proxy/api`）开头则剥离该前缀。

**v2**（`a.f(url, body, builder)`）：匹配
`Network.add_signature_apis_V2_72500`：

```
["/video/config/fjbouedvm/dserubn", "/project/meta_info",
 "/api/sigerus/login_credit_mobile", "/api/sigerus/login_mobile", "/login",
 "/api/francis/mobile/code/query", "/api/sigerus/ticket/mobile/code/request",
 "/api/sigerus/mobile/code/request", "/api/sigerus/login_ticket_mobile",
 "/api/galilei/refresh/token"]
```

```java
qb2.d.b().L(url, bodyBytes, headerMap)   // -> SecureNative.sdr(ctx, map)
```

`L()` 把结果写进 `headerMap`：**总是**加 `x-p-t`，且当内部 `code == 0` 时再加
`x-p1`。两份路径列表都注册了 `Configuration` 监听器，可在运行时被远端配置替换。

## 4. 请求体加密（GoldenArch）

拦截器 `com/aimi/android/common/http/unity/internal/interceptor/j.java` 调用
`IGoldenArchService.processReqBody/processRespBody`，仅对
`Network.pay_sec_api`（默认 `["/api/wormhole/equator"]`）生效。实现为
`com.xunmeng.goldenarch.GoldenArchService` + `libgoldarch.so`，即**支付相关体**才走
第二套体加密。

## 5. Cookie 保护名单

`xz2/j.java`：

```java
Arrays.asList("pdd_user_id", "PDDAccessToken", "pdd_user_uin", "ETag", "install_token")
```

这 5 个键进入 native 保护的 cookie 集合，另外还跟踪 `cookie_length`、
`cookie_count`、`cookie_error_length` 三个长度指标，用于检测 cookie 被外部篡改。

## 6. 上传协议（galerie）

### 6.1 掩码路径与 anti-token 白名单

`y51/a.java` 远端配置 `galerie_upload.anti_token_path` 默认列表：

```
/api/galerie/public/signature, /image/signature, /file/signature,
/v4/store_image, /v2/general_file, /api/galerie/large_file/v2/upload_init,
/galerie/business/get_signature, /api/galerie/image/signature,
/api/galerie/file/signature, /api/galerie/v4/store_image,
/api/galerie/v2/general_file
```

### 6.2 multipart 表单（`z51/b.r(w51.d)`）

- `Content-Type: multipart/form-data; boundary=---011000010111000001101001`
- 文件 part 名固定 `file`。
- 表单字段：

| 字段 | 来源 | 条件 |
| --- | --- | --- |
| `ext_info` | `dVar.l()` 的 JSON | 非空 |
| `bucket_tag` | `dVar.f107329j` | `dVar.f107338p == true` |
| `sign` | `dVar.f107337o` | `dVar.f107338p == false` |
| `enable_quick_upload` | `"true"` | 视频任务且 `J0 && L0` 非空 |
| `quick_upload_md5` | `dVar.L0` | 同上（秒传） |
| `quick_upload_crc64` | `dVar.M0` | 同上 |
| `create_media` | `"true"` / `"false"` | 视频类任务 |
| `extra_params` | `dVar.A0.toString()` | 视频类任务且非 null |
| `filename` | `dVar.f107325h`，否则取路径 basename | 总是 |

- 请求头：`anti-token`（`k.e(...)`）、`User-Agent`（`k.h()`）；`bucket_tag` 分支下
  额外用头 `AccessToken: dVar.f107319e`。
- 其他：`ReplaceIpController` 负责重试/换 IP；AB `ab_galerie_force_fill_anti_token`
  默认 true 强制填 anti-token；`k` 里还有 `url_sign`(1/2)、`cdn_sign`、`sign_private`
  三种签名模式；AB `ab_enable_upload_check_exif` 默认 true，上传前检查 EXIF。
- 任务类型枚举：`file_upload`、`image_upload`、`video_upload`、
  `video_pipeline_upload`。
- 端点：`/api/galerie/cos_large_file`、`/api/galerie/large_file`。

## 7. Titan 长连接

- 库：`libtitan.so`（assets 内嵌 + 动态下载）、`libtronav.so`、`libtronkit.so`。
- Java 侧：`com.xunmeng.basiccomponent.titan.jni.Java2C`（52 个 native），
  `com.xunmeng.basiccomponent.titan.api.TitanApiRequest`。
- 群播同步：`https://api.pinduoduo.com/api/titan-multicast/sync`，设备组列表通过
  `Java2C.SetMulticastGroupList(MulticastGroupInfo[])` 下发给 native。
- 缓存：`files/network/titancache/bizgroup_{1,2}.v1_cache`，INI 风格：

```
[<uid>_<group>]
group=N
id=<uid>
offset=<n>
```

- QUIC 会话缓存在 `files/network/pnet/com_xunmeng_pinduoduo/ssl_session/`，命名
  `*_quic.session` / `*_quic.ini`，另有普通 TLS `*.session`。
- `TitanApiRequest` 支持自定义 headers、POST body 字节数组、分片
  (`shardInfo(bizUnit, key, value, preLoadBizList)`)、`multiset`、`waitLongLink`
  与 `sourceProcess`。

## 8. 客户端内建的网络可观测性

`net_adapter/hera/netcapture` 会在客户端内记录每个请求：

```
host, scheme, path, method, queryMap, requestHeaderList, respHeaderList,
requestBody, respBody, requestStartTs, allCost, netCost, respCost, svrCost,
code, isSuccessful, taskId, traceId, channelType, isH5, vip, exceptionMsg
```

注册入口 `netcapture.a.d(listener, tag)`。这不是风控本身，但它是风控/APM 的数据源
之一，属于"客户端把请求全量留痕"的能力边界。

## 9. 降级与熔断

`NetworkDowngradeManager` 与三个拦截器共同实现：

- API 重定向到 CDN；本地缓存重放（HTTP `299`）；
- URL 改写与 https→http 改写；
- 服务端 `512` + `chiru-downgrade` 头触发熔断；
- 降级事件通过 ITracker code `90547` 上报。

`d(a.a, request, flag)`（`com.aimi.android.common.http.h.g`）负责把
`i.g().b(url, forceNewShortToken, monitor)` 产出的 `anti-token` 头写进
`Request.Builder`。
