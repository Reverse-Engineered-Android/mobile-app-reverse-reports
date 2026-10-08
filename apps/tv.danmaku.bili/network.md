# 网络与功能

## 1. 调用链

### 1.1 HTTP

Retrofit 接口先经过 `DefaultRequestInterceptor` 或其子类 `AuthInterceptor`：

1. 合并业务 query/body 参数与公共参数；
2. GET 进入 URL query，POST form 进入 body；
3. `net.check_sign` 默认为 `true` 时调用 `signQuery`；
4. 补充 `Display-ID`、`Buvid`、`Device-ID`、`fp_local`、`fp_remote`、
   `session_id`、`x-bili-locale-bin`、`GuestId` 等头；
5. 交给 OkHttp HTTP/2 连接池，服务端返回 JSON 或重定向到 CDN。

精确来源：`com.bilibili.okretro.interceptor.DefaultRequestInterceptor`、
`com.bilibili.lib.accounts.AuthInterceptor`。

### 1.2 gRPC / protobuf

视频详情、播放策略、弹幕、评论和互动消息使用生成的 `com.bapis.bilibili.*Moss`
客户端。请求/响应字段按 protobuf 编号编码，HTTP 层使用 `Content-Type:
application/grpc` 和 gRPC 帧头；APK 中保留完整 message/service 生成类，因此字段编号、
oneof 和 repeated 结构可直接恢复。典型服务包括：

- `bilibili.app.viewunite.v1`：视频/番剧详情；
- `bilibili.app.playerunite.v1`：统一播放信息；
- `bilibili.cheese.gateway.player.v1`：课程播放策略；
- `bilibili.community.service.dm.v1`：弹幕获取与下发；
- `bilibili.main.community.reply.v1`：评论树；
- `bilibili.broadcast.*`：直播、会员购和消息广播。

### 1.3 Ktor / 原生 HTTP

GAIA 指纹使用 Ktor `HttpClient` + JSON；`libbili.so` 的风险遥测直接发送
`POST <path> HTTP/1.1`，`Content-Type: application/octet-stream`、
`Cache-Control: no-cache`，路径为 `/x/internal/gaia-gateway/ExBadBasket`。

## 2. 域名与连接

| 用途 | 静态域名/前缀 |
| --- | --- |
| 主 API | `api.bilibili.com` |
| App API | `app.bilibili.com` |
| 搜索 | `app.bilibili.com`、`api.bilibili.com` |
| 直播 | `live.bilibili.com`、`api.vc.bilibili.com` |
| 投稿 | `member.bilibili.com` |
| 会员购 | `show.bilibili.com`、`api.bilibili.com` |
| 风控网关 | `api.bilibili.com/x/internal/gaia-gateway/*` |
| HTTP DNS | 本地代码含独立 HTTP DNS 地址与 fallback |
| 媒体/资源 CDN | `*.bilivideo.com`、`*.hdslb.com`，以响应 URL 为准 |

域名本身不是权限边界；认证由 `access_key`/session、签名和服务端响应共同决定。

## 3. 目标功能静态接口

### 3.1 搜索

| 方法 | 路径 | 数据流 |
| --- | --- | --- |
| GET | `/x/v2/search/live` | 直播结果，服务端按 query/page 返回列表 |
| GET | `/x/v2/search/episodes`、`/episodes_new` | 番剧/合集匹配 |
| GET | `/x/v2/search/recommend`、`/recommend/noresult` | 关键词补全与无结果推荐 |
| GET | `/x/v2/search/square` | 综合搜索 |
| GET | `/x/v2/search/trending/ranking` | 热搜/排行 |
| GET | `/x/topic/pub/search?page_size=20` | 专题搜索 |

搜索建议本地表为 `suggestions(_id,display1,query,date)`，写入用户输入的关键词和时间，
用于离线建议；不会因此证明关键词被持续上传。

### 3.2 直播

- 首页/分区/关注：`live.bilibili.com/app/all-live`、`area`、`myfollow`、
  `mytag`、`mytag/v2`。
- 分页参数含 parent/area/virtual area、分页、画质 `qn`/`fnval`、分辨率、网络、
  设备名、初始化和推荐 offset。
- 弹幕模式：`POST /live_user/v1/BarrageSetting/get` 与 `/set`。
- 房间内状态、消息和互动由 `bilibili.broadcast.*` gRPC 消息承载。

### 3.3 会员购

主要 REST 路径：

- 地址：`/api/ticket/addrinfo/create|delete|list|update`、`/district/dl`；
- 购票人：`/api/ticket/buyerinfo/add|delete|idtypes|list|query|update`；
- 订单：`/api/ticket/ordercenter/count|list`、`/api/ticket/ticket/list|view|
  transfer|reTransfer|verify`；
- 收藏：`/api/ticket/user/addfav|delfav|favcardlist|view`；
- 图片：`POST /api/ticket/upload/imageUpload`；
- 市场主页：`api/ticket-market/owner/home`、`api/ticket/owner/home`。

请求包含地址、实名购票人、订单与图片等高敏业务数据；是否上传取决于用户创建订单、
上传图片或提交表单，接口权限由服务端 session 与订单归属控制。

### 3.4 视频播放与互动

- 详情/策略：`bilibili.app.viewunite.*`、`bilibili.app.playerunite.*`、
  `bilibili.cheese.gateway.player.v1.PlayViewReply`。
- TV/OTT 播放：`/x/playurl/ott`。
- 弹幕：`bilibili.community.service.dm.v1`，离线任务还请求
  `KDmSegMobileReply` 分段弹幕。
- 评论：`bilibili.main.community.reply.v1`；REST 侧存在 `/x/v2/dm/post`、
  `/x/v2/dm/subject/state/update`。
- 播放策略返回 `PlayLimit`、`PlayStrategy`、清晰度、DASH URL、backup URL、
  `dashDrmType`、Widevine PSSH 或 BILIDRM credential。

### 3.5 缓存与离线下载

- MP4/M4S：媒体 URL 来自播放响应，按分段/分片下载并记录进度。
- `offlineVideo.db`：视频元数据、分段、存储路径和授权码。
- `periodic_downloader.db`：周期下载任务。
- `mod_resource_cache.db`、`resmanager.db`、`staggers.db`：动态资源 key、
  过期时间、profile 与本地路径。
- `network_report_stats.db`：总页/页面网络统计，是统计而非原始报文捕获。

## 4. 无真实流量结论

以上均为代码、字符串、资源和设备 schema 的静态证据。未发请求、未验证在线状态码，
未读取 `access_key`、Cookie、Token、IP、用户 ID 或媒体 URL；远端开关、灰度结果和服务端
动态字段不属于本报告的已证实事实。
