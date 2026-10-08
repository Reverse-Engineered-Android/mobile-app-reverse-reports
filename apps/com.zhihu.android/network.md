# 网络与协议

本文件给出知乎 11.10.0 的 HTTP 分层结构、URL 与报文格式、签名/加密报文构造、
以及主要业务接口的静态字段。所有内容来自 DEX 静态反编译与资源字符串提取，
未构造请求、未登录。

## 1. 分层结构

```
业务 ApiClass（retrofit2 interface，注解 @f/@o/@p/@b/@h）
  └─ OkHttpFamily.API() / API2()（OkHttp 连接池、超时、DNS）
       └─ GlobalRequestDecorator.build()   // 全局头 + 拦截器安装顺序
            ├─ REQUEST_PROCESS_INTERCEPTOR   // EncryptInterceptor + SignInterceptor
            ├─ PAGE_ID_INTERCEPTOR
            ├─ DEV_API_INTERCEPTOR（内部包才装）
            └─ RESPONSE_PROCESS_INTERCEPTOR
```

`GlobalRequestDecorator.java` 的安装顺序（节选）：

```java
builder.addInterceptor(UNICOM_INTERCEPTOR);
builder.addInterceptor(REQUEST_PROCESS_INTERCEPTOR);
builder.addInterceptor(PAGE_ID_INTERCEPTOR);
if (an.s()) builder.addInterceptor(DEV_API_INTERCEPTOR);   // 内部调试包
```

## 2. 全局请求头

`GlobalRequestDecorator.intercept()` 逐个补齐（仅在为空时写入）：

| 头 | 值来源 |
| --- | --- |
| `x-api-version` | `com.zhihu.android.app.a.a.d()` |
| `x-app-version` | `com.zhihu.android.app.a.a.a()` |
| `x-explore-version` | `AppBuildConfig.EXPLORE_VERSION_NAME()`（`an.i()` 为真时） |
| `x-app-za` | `com.zhihu.android.app.a.a.e()` |
| `x-app-bundleid` | `AppBuildConfig.APPLICATION_ID()` |
| `x-app-flavor` | `f.CHANNEL()` |
| `x-app-build` | `com.zhihu.android.app.a.a.g()` |
| `x-network-type` | `com.zhihu.android.app.a.a.c()` |
| `X-ZST-82` | `RuidSafetyManager.a().i()` |
| `X-ZST-81` | `RuidSafetyManager.a().j()` |
| `x-udid` | `CloudIDHelper.a().a(ctx)` |
| `Authorization` | `getAuthorization()`（见 [auth.md](auth.md)） |
| `x-at-df-if` | `DeviceCollectorManager.c().b()`，仅 `cf.a(url)` 命中的域名且头为空时写 |
| `X-Traffic-Free` | `unicom`（联通免流用户，响应拦截器） |

`x-app-za` 是知乎的渠道/设备串，`x-api-version` 由各 retrofit 方法上的
`@k({"x-api-version:3.1.8"})` 覆盖为业务版本。

## 3. URL 与报文格式

- 基址 `https://api.zhihu.com`（多数业务）、`https://www.zhihu.com`（网页/H5 等价接口）、
  `https://billboard-er.zhihu.com`（热榜）、`https://lens.zhihu.com`（视频上传）。
- 方法与语义：`@f`=GET、`@o`=POST、`@p`=PUT、`@b`=DELETE、`@h("DELETE")`=自定义方法。
- 路径参数 `@s`，查询参数 `@t`，form 字段 `@c`（`@e` 表示 `@FormUrlEncoded`），
  JSON body `@a`（`RequestBody` 或 `Map`），头 `@i`，动态 URL `@x`。
- 响应信封（Jackson）：`data`（对象或数组）、`paging`、`error`（`{"code","message"}`）。
  设备端 NetCache 实样：`{"className":..., "key":..., "result":{"stability":..., "data":[...]}}`。

## 4. 签名与加密报文

### 4.1 `X-Zse-93` / `X-Zse-96`（`SignInterceptor`）

```
signString = X-Zse-93
           + "+" + url.encodedPath
           [ + "?" + url.encodedQuery ]
           [ + "+" + x-app-version ]
           [ + "+" + Authorization ]
           [ + "+" + x-udid ]
           [ + "+" + body ]              # 仅当 0 < contentLength <= 4096
md5  = MD5(signString)                  # 32 位大写 hex
enc  = o.a.a(md5.toLowerCase().bytes)    # Bangcle（AES + 置换）
X-Zse-96 = "1.0_" + base64(enc)
```

摘要与编码的精确实现（`k.java:216`、`k.java:133`）：

```java
// k.a(String)：大写 hex
return String.format("%032X", new BigInteger(1, MessageDigest.getInstance("MD5").digest(str.getBytes())));
// k.e(Request)：转小写后加密、base64、加 "1.0_" 前缀
.addHeader("X-Zse-96", "1.0_" + new String(
    this.f106369c.encode(this.f106368b.encrypt(strA.toLowerCase().getBytes()))))
```

`encode` 为 `Base64.encodeToString(bArr, 2)`（NO_WRAP），`encrypt` 为
`com.zhihu.android.o.a.a(bArr)`。

触发条件（`k.a`/`k.b`/`k.c`/`k.d`）：请求已有 `X-Zse-93`、`X-Zse-96` 为空、body 非空；
或已有两头的旧版本重签。GET 无 body → 不签。

### 4.2 请求体加密（`EncryptInterceptor`）

命中 `Map<m,b>` 注册的接口时，body 经 `com.zhihu.android.o.a.a()` 加密后替换，
并写 `X-Zse-93: 101_1_1.0`。图/视频上传等二进制通道不走此加密。

### 4.3 CloudID `x-req-signature`（`cloudid/d/a.java`）

```
x-req-signature = CloudIDHelper.encrypt(
    x-sign-version, f69056d, f69057e, formBodyStr, x-app-id, x-req-ts, f69058f)
```

7 参数 native 方法（`CloudIDHelper.java:78`），另有无 key 变体
`encryptWithoutSecurekey(String)`（`CloudIDHelper.java:80`）。

## 5. 主要业务接口字段

### 5.1 搜索（`api/service2/ar.java` / `km_editor/service/c.java`）

```java
@retrofit2.http.f(a = "/search_v3")
Observable<Response<SearchResultNewAPIWithWarning>> a(
    @t("correction") int i, @t("t") String str, @t("q") String str2);

@retrofit2.http.f(a = "/search_v3")
Observable<Response<SearchResultNewAPIWithWarning>> a(
    @t("correction") int i, @t("t") String str, @t("q") String str2,
    @t("restricted_scene") String s3, @t("restricted_field") String s4,
    @t("restricted_value") String s5);
```

另有 `/search/tabs`（`enable_recent`）、`/search/customize`，以及
`@k({"x-api-version:3.0.65"}) GET /search_v3`（`km_editor/service/c.java`，
`type/text/offset/limit`）。本地 `zhihu_search.room` 保存 `search_history`、
`search_hot_words`、`search_hot_words_tabs`、`search_tabs`。

### 5.2 推荐（`app/feed/ui2/feed/j.java`、`d.java`）

```java
@k({"x-api-version:3.1.8"})
@o("/topstory/recommend?tsp_ad_cardredesign=0&feed_card_exp=card_corner|1&v_serial=1&isDoubleFlow=0")
Observable<Response<FeedList>> a(@a FeedRequestBody body,
    @i("x-close-recommend") int i, @i("x-ad-styles") String str,
    @i("x-feed-prefetch") int i2, @t("is_feed_first_request_tmp") Integer n);

@k({"x-api-version:3.1.8"})
@f("/topstory/recommend?tsp_ad_cardredesign=0&feed_card_exp=card_corner|1&v_serial=1&isDoubleFlow=0")
Observable<Response<FeedList>> a(@t("action") String, @t("refresh_scene") int,
    @t("scroll") String, @t("limit") String, @t("start_type") String,
    @i("x-close-recommend") int, @i("x-ad-styles") String, @i("x-feed-prefetch") int,
    @t("device") String, @t("short_container_setting_value") int,
    @t("include_guide_relation") boolean, @t("interest_tags") String,
    @u Map<String,String>, @t("is_need_force_insert") Boolean, ...);
```

相关：`/feed-root/section/{sectionId}`、`/feed-root/sections/submit/v2`、
`/feed-root/sections/saveUserCity`、`/recommend/app/zhihu_classics_top`、
`/topstory/hot-lists/total`、`/topstory/uninterestv2`（不再感兴趣）。
设备端 `CACHE_KEY_FEED_NEW`（`FeedList`）实样含 `feed_request_id`、`session_token`、
`templates`、`explored` 等字段。

### 5.3 问答（`km_editor/service/a.java`）

浏览：

```java
@b("/questions/{question_id}/draft")   ...   // DELETE 草稿
@o("/answers")                          Observable<Response<Answer>> a(@c("question_id") long, @d Map<String,Object>);
@p("/questions/{question_id}/anonymous") ... @c("is_anonymous") boolean
@p("/answers/{answer_id}")              // 编辑回答
@f("/questions/{question_id}")          // 问题详情
@p("/questions/{question_id}/draft")    // 保存草稿
@k({"x-api-version:3.0.89"})
@f("/v4/answers/{answer_id}")           // 回答详情
@b("/questions/{question_id}/scheduled-answer")
@f("/questions/{question_id}/answer-settings")
@o("/questions/{question_id}/validate-scheduled-answer")
@f("/answers/{answer_id}/segments")     // 分段（付费分段展示）
```

展开为 HTTP 动词即：`POST /answers`（form `question_id` + body map）、
`PUT /answers/{answer_id}`（编辑 body map）、`PUT /questions/{question_id}/draft`、
`PUT /questions/{question_id}/anonymous`（form `is_anonymous`）。当前接口的 `@p` 实际是 `PUT`，
因此未标注 `PATCH /answers/{answer_id}` 或 `PATCH /questions/{question_id}/draft`；
草稿删除则明确为 `DELETE /questions/{question_id}/draft`。

回答列表/幻灯片回答在 `answer/api/service/AnswerService.java`：
`GET /v4/questions/{question_id}/answers`（`order_by,offset,limit,show_detail,with_tags`）、
`GET /questions/{question_id}/slideshow-answers`、`GET /answers/{answer_id}/question`。

### 5.4 评论（`comment_for_v7/a/b.java`）

`DELETE /comment_v5/comment/{comment_id}`、`POST /reaction/comments/{id}/like`、
`POST /comment_v5/comment/{id}/reaction/dislike`、
`/operation/{collapse|hot|top}`。

### 5.5 盐选 / 会员内容（`feature/kvip_catalog/catalog/a.java`）

```java
Observable<Response<EBookSimple>> a(@s("book_id") long j);
Observable<Response<EbookCatalogData>> a(@s("id") String, @t("offset") long, @t("order_by") String);
Observable<Response<PagingSectionData>> a(@s("business_type") String, @s("business_Id") String,
    @t("order_by"), @t("before_id"), @t("after_id"), @t("include") Integer,
    @t("limit") int, @t("mode"), @t("resource_type"), @t("public_status"), ...);
Observable<Response<SectionResponse>> a(@s("business_type"), @s("business_Id"),
    @t("type"), @t("order_by"), @t("before_id"), @t("after_id"),
    @t("include_after_id") Integer, @t("limit") int, @t("mode"), @t("fields"), ...);
```

端点：`/kvip/content/products/{business_type}/{business_Id}/section`、
`/kvip/content/products/ebook/{id}/section`、`/education/manuscript/{business_type}/{business_id}/section/{section_id}`、
`/kvip/right/sku/reversion_sku_ext?scene=ebook_manuscript`、
`/vip/rn_comment/list/{resource_type}/{resource_id}`。H5 侧
`https://www.zhihu.com/kvip/{sku_type}/{business_id}/section/{track_id}`、
`https://story.zhihu.com/vip-ranking?...&zh_app_id=200033`。深链 `zhihu://km_paid_content/share`。
返回字段含 `is_paid`、`is_trial`、`purchase_column`、`sku_id`、`section_count`。
本地 `manuscript_preload_html.db`（`manuscript_html`）为该类内容的只读试读缓存。

### 5.6 视频 / OGV

`video_entity/ogv/a/a.java`（`OgvService.kt`）：

```java
@f("ogv/zvideo/{id}")   Observable<Response<OgvVideoTabList>> a(@s("id") String);
@f("/zvideos/{id}")     Observable<Response<VideoEntity>> a(@s("id") String, @t("type") String); // 默认 "ogv"
```

OGV 实体：`Ogv`（`season: List<OgvSeason>`）、`OgvSeason`（`season_id,name,list,tips,current`）、
`OgvEpisode`（`index,name,desc,mark,current,zvideoId`）、`OgvInfo`（`play_count,season_id,name,url,author`）。
`video_entity/models/VideoEntity.java` 含 `playCount`、`paidInfo`（`isTrial,sectionCount,skuId,skuTitle,skuUrl`）、
`purchaseColumnModel`、`ogvTabs`。漫画/互动场景 `InteractiveSceneCode` 含 OGV 场景码。
`/drama/*` 为语音直播（`api.zhihu.com/drama/theaters` → `TheaterNotExistError: 直播间不存在`），
与点播剧集无关。

ZVideo（普通视频）：`zvideo-tabs/tabs/choice/rank`（公开榜）、`/zvideos/{id}/card`、
`/zvideos/drafts`、`/zvideo-contribute/zvideos/{zvideo_id}/status`、
投稿 `/zvideo-contribute/contribute/publish`、`/zvideos/publish/content/{content_id}/{content_type}`、
`/general/video/publish`。

## 6. 埋点与可观测通道

| 通道 | 端点 | 编码 |
| --- | --- | --- |
| ZA v2 | `https://duga.zhihu.com/api/v2/za/logs/batch` | `application/x-protobuf`（`X-Za-Ev`） |
| ZA v3 | `https://duga.zhihu.com/api/v3inv2/za/logs/batch` | `application/x-protobuf`（`X-Za-Ev`） |
| 实时 | `https://datahub.zhihu.com/collector/galileo`、`/lastn-realtime` | protobuf/JSON |
| 位置埋点 | `https://datahub.zhihu.com/collector/zalocation` | — |
| APM | `https://zhihu-web-analytics.zhihu.com/api/v2/apm/logs/batch`、`https://apm.zhihu.com/collector/apm` | — |
| 大字日志 | `https://duga.zhihu.com/action/zhihu_biglog/log` | — |
| 会员日志 | `https://duga.zhihu.com/action/zhihu_vip/log` | — |
| 风控 | `/zst/events/{p,s,d,c,i}` | form/加密 |

## 7. 域名清单（静态提取，52 个唯一主机）

主要主机（括号为静态出现次数）：

- `www.zhihu.com`(611)、`zhihu.com`(150)、`api.zhihu.com`(89)、`api2.zhihu.com`(6)、`api-quic.zhihu.com`
- 视频/媒体：`lens.zhihu.com`(26)、`media.zhihu.com`(3)、`pic1.zhimg.com`/`pic2.zhimg.com`/`pic3.zhimg.com`/`pic4.zhimg.com`/`pic5.zhimg.com`/`picx.zhimg.com`/`pica.zhimg.com`/`picd.zhimg.com`、`zhihu-pics-upload.zhimg.com`
- 埋点/APM：`duga.zhihu.com`(13)、`datahub.zhihu.com`(4)、`zhihu-web-analytics.zhihu.com`(4)、`apm.zhihu.com`
- 内容/运营：`sugar.zhihu.com`(11)、`zhida.zhihu.com`(10)、`story.zhihu.com`(5)、`zhuanlan.zhihu.com`(4)、`activity.zhihu.com`(2)、`salt.zhihu.com`、`zhihu-pics-upload.zhimg.com`
- 支付/账户：`walletpay.zhihu.com`(10)、`pay.zhihu.com`(8)、`account.zhihu.com`、`coreuserbiz.zhihu.com`
- 云控/风控：`appcloud.zhihu.com`(6)、`appcloud2.zhihu.com`(3)、`m-cloud.zhihu.com`(6)、`billboard-er.zhihu.com`、`httpdns.zhihu.com`
- 内部/测试：`ops.in.zhihu.com`、`updown.in.zhihu.com`、`home-svr.in.zhihu.com`、`cops.dev.zhihu.com`、`qt.dev.zhihu.com`、`page-info-test.zhihu.com`、`*.zpres.zhihu.com`

共 1145 条唯一 URL/路径，52 个唯一主机。`www.zhihu.com` 的 611 条中多数为
H5/Hybrid 页面路径（`/appview/**`、`/kvip/**`、`/market/**`、`/zvideo/**`），
原生接口集中在 `api.zhihu.com`。
