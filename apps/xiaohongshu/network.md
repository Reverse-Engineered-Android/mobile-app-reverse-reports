# 小红书网络协议与认证机制

## 1. 域名与客户端

| 面向 | 目标 |
| --- | --- |
| 主业务 API | `edith.xiaohongshu.com` |
| 推荐/Hera | `rec.xiaohongshu.com` |
| 设备画像 | `modelportrait.xiaohongshu.com` |
| 搜索 | `search.xiaohongshu.com` |
| 图片、视频、静态资源 | `cdn.xiaohongshu.com` 与 `*.xhscdn.com` |
| 风控接收 | native 代码中存在 `https://as.` 前缀候选；完整域名未在 Java 明文中闭环 |

Java 网络层是 Retrofit 注解模型包装 OkHttp。核心注解被混淆为 `fvc/*`：

| 注解 | Retrofit 语义 | 证据 |
| --- | --- | --- |
| `@fvc.f` | GET | method annotation |
| `@fvc.o` | POST | method annotation |
| `@fvc.b` / `@fvc.p` | DELETE/PUT 类方法 | 其他 service 中出现 |
| `@fvc.e` | form URL encoded | method marker |
| `@fvc.c` | form field | parameter annotation |
| `@fvc.d` | field map | parameter annotation |
| `@fvc.t` | query | parameter annotation |
| `@fvc.u` | header map | parameter annotation |
| `@fvc.q` | multipart part，`encoding=binary` | parameter annotation |
| `@fvc.a` | JSON/body object | parameter annotation |

## 2. 公共请求格式

`it6.h` 和 `z2c.e` 把公共参数序列化为一个 HTTP header：

```http
xy-common-params: platform=<value>&deviceId=<value>&versionName=<value>&...
```

代码按 map 顺序拼接 `key=value`，以 `&` 连接并去掉末尾分隔符。已确认键包括：

```text
platform deviceId versionName channel origin_channel sid lang dlang t fid
uis project_id app_id build launch_id teenager identifier_flag
x_trace_page_current tz cpu_name device_model device_level cpu_abi nqe_score
gid overseas_channel did holder_ctry data_ctry active_ctry auto_trans
mlanguage SUE id_token
```

`id_token` 表明认证状态进入公共参数；其余 token 的精确 header/cookie 映射主要位于混淆/native 路径。公开报告只记录已确认字段，不构造可重放请求。

## 3. 响应封装

主 API 模型使用 `EdithBaseResponse`：

```json
{
  "code": 0,
  "success": true,
  "msg": "",
  "data": {}
}
```

部分旧接口使用 `EdithCommonResponse` 或业务 wrapper，但数据部分仍以 JSON 对象/数组返回。字段名由 Moshi `@Json(name=...)` 或 Gson `@SerializedName(...)` 固定。

## 4. 登录与账号状态

### 登录请求

`AccountApi` 中已确认四类流程：

| 方法 | 路径 | 请求体 |
| --- | --- | --- |
| GET 校验码 | `/api/sns/v1/system_service/check_code` | query：`phone`, `zone`, `code`, `type` |
| 运营商登录 | `/api/sns/v1/user/login/mob_tech` | form：token/op_token、运营商、设备标识、归因字段、手机号等 |
| 电信登录 | `/api/sns/v5/user/login/ctcc` | form：token、`gw_auth`、设备标识、手机号等 |
| 找回账号 | `api/sns/v1/user/login/recovery_account` | form：`state_token`, `face_token`, 设备标识、归因字段 |

短信码接口：

| 路径 | 参数 |
| --- | --- |
| `/api/sns/v1/system_service/check_code` | `phone`, `zone`, `code`, `type` 或 `secure` |
| `/api/sns/v1/system_service/vfc_code` | `phone`, `zone`, `type` 或 `secure` |
| `api/sns/v2/user/phone/zones` | GET，无显式参数 |

### 登录响应状态

`LoginLoginResponse` 已确认包含：

```text
session secure_session user_token userid device_password
phone_number bind_phone user_exists type level
nickname red_id imageb images desc location
fans follows gender collected liked score register_time app_first_time
onboarding_pages birthday progress_bar login_exp_map
```

其中 `session`、`secure_session`、`user_token`、`device_password` 是会话/设备绑定状态；`userid` 是账号关联标识。它们会被持久化，但普通 API 中各 token 的完整传输位置未全部静态闭环。

`UserInfo.getSessionId()` 揭示 session 的规范化形状：空值保持为空，已有 `session.` 前缀则原样保留，否则生成 `session.<sessionNum>`。`z2c.e.c(Request)` 的 `sid` provider 直接调用 `IUserService.getSessionId()`，所以 `xy-common-params.sid` 与该规范化 session 已闭环。`id_token` provider 直接调用 `IUserService.getIdToken()`。Tiny 还把同一 session 写入 `x-legacy-sid`。

## 5. OAuth

`IOAuthService`：

| 路径 | 格式 | 数据 |
| --- | --- | --- |
| `api/sns/v1/oauth2/authorize` | POST + form field map | 返回 `code`, `state` |
| `api/sns/v1/oauth2/auth_info` | POST + form field map | 返回 `authorized`, `code`, `state`, app 名称/图标、entity/type、scopes |

## 6. 风控与账号接口

| 路径 | 方法/格式 | 数据 |
| --- | --- | --- |
| `/api/sns/v1/system/ares/device/violation/query` | POST form `source` | violation title/desc/deeplink/downgrade |
| `/api/sns/v1/account/phone-binding-dialog` | GET | 绑定提醒配置 |
| `/api/sns/v2/user/account_info/anomalies` | GET | phone/nickname/avatar 摘要 |
| `/api/sns/v2/user/account_info/anomalies/confirm` | POST | 确认异常 |
| `/api/sns/v1/account/intervention` | GET `business_code`, `user_id` | intervention alert config |
| `api/sns/v1/system_service/captcha_link` | GET | 验证码/核身入口链接与验证上下文 |
| `/api/security/antispam/v1/restriction/self-resolve` | POST | 自助解限结果 |
| `/api/sns/v1/user/login/risk/status` | POST | 登录风险状态 |

self-resolve 响应已确认包含 `antispamVerifyCtrlResp`, `complaintUrl`, `resolveResult`, `verifyUuid`；这些字段用于返回验证控制、投诉入口、处置结果和后续验证标识，不应与业务鉴权 token 混同。

## 7. 请求保护

### Shield

- Java 入口为 `com.xingin.shield.http.Native.intercept(chain, handle)`。
- native 会读取 method、URL path/query、platform info 和 request body。
- 添加 `shield` 与 `xy-platform-info`。
- `shield = "XY" + Base64(blob)`；blob 包含 type/header/length、RC4 后 payload 和 16 字节摘要槽。
- 摘要是 64 字节 token 派生 key 的 HMAC 外壳，但内部压缩轮为定制哈希，不能按标准 HMAC-MD5 处理。

### Tiny

- `jt6.a` 先添加 `x-legacy-did`、`x-legacy-sid`，随后读取完整 body。
- native 输入是 method、host、path、query、body bytes。
- 输出 `x-n0`、`x-o9`、`x-p0`、`x-r4`、`x-r4o` 等混淆 header。
- `/api/sc/tt` 返回 map `ts`，body 含 `preview_type`, `cursor_score`, `user_id`, `user_action`, `refresh_type`。

### 双重保护

Hera/推荐链可同时经过 Shield 和 Tiny。完整请求还可能经过公共参数、User-Agent、Referer、熔断、Cronet failover、优先级和 APM 拦截器。

## 8. 笔记创建/编辑协议

`com.xingin.capa.network.services.NoteService` 已闭环主发布网关：

| 操作 | 方法/路径 | body |
| --- | --- | --- |
| 创建笔记 | `POST /api/sns/v2/note` | JSON `ReportParamModel` |
| 编辑笔记 | `PUT /api/sns/v2/note` | JSON `ReportParamModel` |
| 获取发布页详情 | `GET /api/sns/v10/note` | `note_id`, `source`, 可选 `edit_mode` |
| 获取作者简要信息 | `GET /api/sns/capa/postgw/note_brife_info` | `note_id` |

`ReportParamModel` 顶层格式：

```json
{
  "common": {},
  "image_info": {},
  "video_info": {}
}
```

`common` 已确认字段：

```text
capa_trace_info business_binds desc goods_info hash_tag ats note_id
post_locs post_loc privacy_info product_reviews source template_icon
template_resource_file_id template_resource_file_md5 template_title title
topic_id type template_tags biz_relations
```

`image_info`：`images`, `music_info`, `soundtrack`。图片项包含 `file_id`, `width`, `height`, `metadata`, `original_metadata`, `stickers`, `fonts`, `prop_id`, `upload_channel`, `master_cloud_id`, `extra_info_json`, 可选 live photo `video_file_id/video_id`。

`video_info` 主要字段：`file_id`, `fsize`, `format_width`, `format_height`, `cover`, `bgm`, `soundtrack`, `segments`, `timelines`, `transitions`, `chapters`, `template_data`, `photo_album`, `upload_channel`, `master_cloud_id`, `bucket`, `upload_region` 等。

## 9. 证据等级与剩余边界

| 项目 | 状态 |
| --- | --- |
| Retrofit 方法/参数/响应 JSON | 已确认 |
| `xy-common-params` 序列化与字段 | 已确认 |
| 登录/OAuth/风险路径与字段 | 已确认 |
| Shield 外层、RC4、摘要外壳 | 已确认 |
| Tiny 输入输出 header | 已确认 |
| 定制摘要压缩轮 | 未完整复原 |
| 会话 token 派生与 type 6/7 | 未完整复原 |
| `sid` / `id_token` 公共参数来源 | 已闭环 |
| note 创建/编辑网关与 body 顶层 | 已闭环 |
| `user_token`/`device_password` 全部业务使用点 | 未逐一枚举 |
| XHS native 风控完整 URL/payload | 部分闭环；`as.` 为运行时拼接候选 |
