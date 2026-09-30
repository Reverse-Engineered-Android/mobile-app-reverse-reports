# 小红书网络协议与认证机制

## 1. 域名与客户端

| 面向 | 目标 |
| --- | --- |
| 主业务 API | `edith.xiaohongshu.com` |
| 推荐/Hera | `rec.xiaohongshu.com` |
| 设备画像 | `modelportrait.xiaohongshu.com` |
| 搜索 | `search.xiaohongshu.com` |
| 图片、视频、静态资源 | `cdn.xiaohongshu.com` 与 `*.xhscdn.com` |
| 风控接收 | `https://as.xiaohongshu.com/api/v1/d/upload`，由 `libxyasf.so` 直接通过 native OkHttp 上传 |

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

其余登录态的 Java 可见使用边界如下：

| 状态 | 已确认来源/用途 | 未闭环边界 |
| --- | --- | --- |
| `session` / `secure_session` | 登录响应映射到 `UserInfo.sessionNum/secureSession`；session 进入 `sid` 与 `x-legacy-sid` | `secure_session` 的普通 API 传输位置未找到 |
| `id_token` | 登录响应 `user_extra_info.id_token` 映射到 `UserInfo.idToken`，进入 `xy-common-params.id_token` | 无 |
| `user_token` | 登录响应映射到 `UserInfo.userToken`；Diandian 消息 envelope 的 context 字段名为 `x-access-token`；账号找回/人脸流程以 `user_token` JSON 字段传递；WebView monitor context 也保存该字段 | 不是普通 Edith API 的统一 header |
| `device_password` | `LoginResponse`/`UserInfo` 有同名 JSON 字段；远端开关 `device_password_storage_enabled` 默认 true；生成模型 `InlineObject4` 可把它放入设备注册/归因风格 body | 未找到普通 API 调用点或明确持久化复制点 |

`InlineObject4` 的 JSON 字段范围为 `user_id`, `device_password`, `idfa`, `idfv`, `android_id`, `gaid`, `oaid`, `pasteboard`, `category`, `android_version`, `mac`, `attribution_id`, `imei_encrypted`, `ruleId`, `after_register`, `acct_group_id`, `source`。公开报告只记录结构，不构造该请求。

## 5. 实时长连接协议

主 HTTP API 之外，`libxhslonglink.so` 提供独立的实时/推送长连接。该库静态链接 Tencent Mars STN/XYMars，负责持久 TCP、DNS 候选选择、心跳、弱网检测、重连、任务队列和收发调度；Java 侧通过 `LongLinkProxy`/AIDL 把启动、登录、发送、房间和标签操作转发给 native。`ClientInfo.serializeType` 的默认值明确为 `protobuf`。

### 传输与生命周期

- Java 消息基类的 `messageType` 为 Login `0`、Logout `1`、Up `2`、Tag `3`、Join `4`。
- `ackMode` 为 `0` 不要求 ACK、`1` 立即 ACK、`2` 收到响应后 ACK；Java 默认是 `2`。
- `BaseSendMessage` 还带 `cmdName`, `messageId`, `bizId`, `timeout`（默认 `15000` ms）、重试计数和发送 profile。常用 cmd 为 `login`, `logout`, `up_send`, `add_tag`, `remove_tag`, `join`, `leave`。
- `UpMessage` 是 `type`、`serviceId`、`body byte[]`、`extraInfo map<string,string>`；`DownMessage` 是 `messageId`、`body byte[]`、`extraInfo`。它们是 AIDL/Parcel 边界，wire body 再由 protobuf 打包。
- native 配置包含 connect timeout/interval/max count、heartbeat、DNS delay/TTL/switch、消息队列、流量限制和 send-only 等策略。已确认默认值包括 connect timeout `10000` ms、最大连接尝试 `3`、heartbeat `60000` ms、DNS delay `5000` ms、DNS TTL `300` 秒。

### 登录与协商

`LoginInfo` 的字段是 `uid`, `sid`, `authType`, `domain`, `geographicCode`（默认 `CN`）。登录入口通过 `ClientInfoProvider` 取得这些字段，随后 native 组装 `LoginPacket`。协商消息 `Options` 携带 `mid`、压缩/加密开关以及客户端/服务端 ECDH 公钥。

密码学边界：

- ECDH 曲线为 `secp256r1`；客户端公钥以十六进制传输，解码后是 33 字节压缩点（前缀 `02/03` + 32 字节 X）。
- `LongLink.C2Java.getSharedKeyECDH` 解析服务端十六进制公钥，调用 `KeyAgreement.getInstance("ECDH")` 得到 shared secret。
- 数据保护回调使用 `AES/CBC/PKCS5Padding`；native 压缩函数是 gzip。
- `Options.compress`/`encrypt` 是 protobuf enum，范围均为 `0..1`。shared secret 如何进一步派生 AES key/IV，以及最终外层 frame 的长度/命令头，尚未逐字节闭环，因此不把该链路描述为可直接重放的客户端。

### protobuf wire schema

字段号由 `libxhslonglink.so` 导出的 `k*FieldNumber` 符号和序列化函数交叉确认；下表省略真实业务 body：

```proto
enum AckMode { NON_ACK=0; ACK_IMMEDIATELY=1; ACK_AFTER_RESPONSE=2; }
enum State { /* valid values 0..2; semantic names not recovered */ }
enum SerializeType { /* valid values 0..1 */ }
enum CompressType { /* valid values 0..1 */ }
enum EncryptType { /* valid values 0..1 */ }

message Options {
  string mid = 1;
  CompressType compress = 2;
  EncryptType encrypt = 3;
  string client_public_key = 4;
  string server_public_key = 5;
}
message AuthInfo {
  string auth_type = 1; string sid = 2; string uid = 3; string domain = 4;
}
message DeviceInfo {
  string device_id = 1; string os_version = 2; string app_version = 3;
  string fingerprint = 4; string device_name = 5; string platform = 6; string os = 7;
}
message BizInfo {
  string biz_name = 1; string serialize_type = 2; AuthInfo auth_info = 3;
}
message RoomInfo {
  string room_type = 1; string room_id = 2; string biz_name = 3;
}
message Tag { string key = 1; string value = 2; }
message TagInfo { repeated Tag tags = 1; string biz_name = 2; }
message LoginPacket {
  string app_id = 1; AuthInfo auth_info = 2; DeviceInfo device_info = 3;
  string service_tag = 4; map<string,string> ext_info = 5;
  repeated BizInfo biz_infos = 6; RoomInfo room_info = 9;
  string socket_id = 10; TagInfo tag_info = 11; State state = 12;
}
```

业务流和帧格式：

| 消息 | 字段号与类型 |
| --- | --- |
| `CSStreamData` | `1 ack_mode: enum`, `2 cmd: string`, `3 biz_name: string`, `4 body: bytes`, `5 ext_info: map<string,string>`, `6 service_id: string`, `7 alias: string` |
| `SCStreamData` | `1 ack_mode: enum`, `2 biz_name: string`, `3 body: Event`, `4 time: int64` |
| `SignalStreamData` | `1 ack_mode: enum`, `2 signal: int32`, `3 body: bytes` |
| `AckStreamData` | `1 code: int32`, `2 message: string`, `3 body: bytes` |
| `DataFrame` | `100 data: CSStreamData`, `101 ack: AckStreamData` |
| `SignalFrame` | `100 data: SignalStreamData`, `101 ack: AckStreamData` |
| `SyncFrame` | `100 data: SCStreamData`, `101 ack: AckStreamData` |

辅助包包括 `LoginAckPacket{1 time:int64, 2 socket_id:string}`、`TimeSyncPacket{1 time:int64}`、`KickOutPacket{1 info:string}`、`RoomPacket{1 info:RoomInfo}`、`TagPacket{1 tag_info:TagInfo}`、`BizRegisterPacket{1 biz_info:BizInfo, 2 register:bool}`。`Event` 为 `1 data:bytes`, `2 mid:string`, `4 ext_info:map<string,string>`；`SCStreamData.body` 的序列化函数明确调用 `WriteMessage`，因此它是 `Event` 子消息，不是裸 bytes。

`SerializeType`、`CompressType`、`EncryptType` 的有效范围是 `0..1`，`State` 和 `AckMode` 的有效范围是 `0..2`。除 AckMode 的 Java 语义外，其余 enum 的业务名称未从二进制中恢复。

## 6. OAuth

`IOAuthService`：

| 路径 | 格式 | 数据 |
| --- | --- | --- |
| `api/sns/v1/oauth2/authorize` | POST + form field map | 返回 `code`, `state` |
| `api/sns/v1/oauth2/auth_info` | POST + form field map | 返回 `authorized`, `code`, `state`, app 名称/图标、entity/type、scopes |

## 7. 风控与账号接口

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

## 8. 请求保护

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

## 9. 笔记创建/编辑协议

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

## 10. 证据等级与公开实现边界

| 项目 | 状态 |
| --- | --- |
| Retrofit 方法/参数/响应 JSON | 已确认 |
| `xy-common-params` 序列化与字段 | 已确认 |
| 登录/OAuth/风险路径与字段 | 已确认 |
| Shield 外层、RC4、摘要外壳 | 已确认 |
| Tiny 输入输出 header | 已确认 |
| 定制摘要压缩轮 | 行为级闭环；字节级实现不公开 |
| 会话 token 派生与 type 6/7 | 调用/长度/状态边界闭环；秘密变换不公开 |
| `sid` / `id_token` 公共参数来源 | 已闭环 |
| note 创建/编辑网关与 body 顶层 | 已闭环 |
| 实时长连接 transport、登录/ACK/流 protobuf 字段号 | 已闭环 |
| 长连接 shared-secret 到 AES key/IV、外层 frame 头 | 协商边界闭环；运行时秘密派生不公开 |
| `user_token` 的 Java 可见业务使用点 | 已枚举；native/反射路径仍可能有额外使用 |
| `device_password` 普通 API/持久化调用点 | 未找到；只确认模型、存储开关和设备注册风格 body |
| XHS native 风控 URL/transport/容器 | 已闭环 |
| native 风控 protobuf 字段号与二进制变换 | 字段名/容器闭环；私有变换不作为公开代码 |
