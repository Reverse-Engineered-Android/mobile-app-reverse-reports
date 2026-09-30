# 小红书上传下载数据范围

## 1. 对象上传流程

### UploadConfig

`com.xingin.compose.service.upload.UploadConfig` 的序列化字段：

```text
contentType
filePaths[]
tokenConfig
openMultipart
retryCount
needCopyFile
keepImageExif
```

`filePaths` 指向待上传文件；`contentType` 决定 MIME；`keepImageExif` 表明可保留图片元数据；`openMultipart` 和 `retryCount` 控制分片与重试。`needCopyFile` 可能在上传前复制临时文件，避免源文件被修改或释放。

`ITokenReqParam` 已从 `classes11.dex` 单类恢复，字段为：

```text
bid: int
scene: String
tokenCount: int
isDebug: boolean = false
```

`UploadConfig` 默认构造为 `bid=Sns.bizType`、`scene=comment.scene`、`tokenCount=1`、`isDebug=false`。

### Token/permit API

`com.xingin.uploader.api.internal.TokenService`：

| 操作 | 方法/路径 | query |
| --- | --- | --- |
| 分配文件名 | `GET /api/sns/v1/system_service/qcloud_filename` | `type`, `num` |
| 分配文件名 v2/秒传校验 | `GET /api/sns/v2/system_service/qcloud_filename` | `md5s`, `type`, `num` |
| 获取混合云 token | `GET /api/sns/v2/system_service/mix_cloud_upload_info` | `operator`, `type`, `business`, `env`, `cross_upload`, `dynamic`, `version` |
| 获取上传 permit | `GET /api/media/v1/upload/permit` | `bid`, `biz_name`, `scene`, `file_count`, `version` |
| Capa permit | `GET /api/media/v1/upload/capa/permit` | 同上 |
| 免登录 permit | `GET /api/media/v1/upload/permit_no_login` | 同上 |
| 秒传检查 | `GET /api/media/v1/upload/quick_upload_check` | `bid`, `bizName`, `scene`, `dedupIdentifier`, `dedupAlgorithm`, 可选 `dedupChecksum` |

permit 响应字符串由 Gson 解析为：

```json
{
  "data": {
    "uploadLimitPolicy": {},
    "uploadTempPermits": []
  }
}
```

`uploadTempPermits` 是 `RobusterTokenPermit[]`。实现按 QoS 选择 token，并按 `storageType` 区分腾讯云/阿里云路由。

### 上传 token

`MixedToken`：

```text
fileBytes, filePath, fileId
fileType, bizCode, chunkSize, hasFailed
```

默认 `chunkSize = 1048576`，即 1 MiB。

`RobusterToken`：

```json
{
  "address": "...",
  "bucket": "...",
  "cloud_type": 0,
  "v": "...",
  "qos": 0.0,
  "region": "...",
  "token_info": {
    "expired_time": 0,
    "start_time": 0,
    "tmp_secret_key": "...",
    "tmp_secret_id": "...",
    "session_token": "..."
  }
}
```

`RobusterTokenPermit`：

```text
bucket cloudType currentTime expireTime fileIds masterCloudId region
secretId secretKey storageType token uploadAddr
```

这些是短时对象存储授权。公开报告不保存 token、secret、bucket 真值或上传地址。

### Qiniu/Robuster 执行

- `c0.java` 创建 Qiniu `UploadManager`，并从 token 的 `address` 配置 zone。
- 输入优先使用 `fileBytes`；否则使用 `filePath`。
- 目标 key 为 `fileId`，上传凭证为 `token`。
- 配置文件 recorder 用于续传，使用临时目录和文件名派生 recorder key。
- `UploadOptions` 根据扩展名或调用方 `contentType` 选择 MIME。
- 结果包含 `fileId/file_id`、`accessUrl`、`staticUrl`、`previewUrl`、`videoId`、bucket/region/cloud type、scene、内容长度和耗时。

`QuickUpload` 的服务端结果：

```text
fileId originFileId originVideoId previewUrl staticUrl videoId
result: {success, code, message}
```

`UploadResponse`：

```text
identifier successed failedPaths[] bizType scene
data: {path -> response}
resultList[]
```

### 秒传、去重与对象存在性

`QuickUpload` 请求使用 `dedupIdentifier`, `dedupAlgorithm`, 可选 `dedupChecksum`。命中结果返回 `fileId/originFileId/originVideoId/previewUrl/staticUrl/videoId` 与 `{success,code,message}`。

`POST /api/uploader/cloudobjectexist` 请求：

```json
{
  "cloudObjects": [
    {"bucket": "...", "region": "...", "fileId": "...", "type": 0}
  ]
}
```

响应只返回 `notExistFileIds[]`，用于跳过已存在对象。

## 2. 上传数据范围

### 联系人

读取 Android `ContactsContract.CommonDataKinds.Phone`，投影仅为：

```text
display_name
data1
```

处理规则：

1. 去掉号码中的空格和 `+`。
2. 去掉中国大陆国家码 `86`。
3. 仅保留以 `1` 开头的 11 位号码。
4. 每条记录只保留 `[手机号, 显示名]`。
5. 按 300 条一页，以 `data`, `page_total`, `page_index` 发送。
6. 变化检测对完整联系人 JSON 做 MD5；只有列表规模变化才触发上传，另有可选 hash 检查。

接口：`api/sns/v1/system_service/upload_contacts`，POST + form-urlencoded。

`data` 不是明文 JSON：应用使用 AES/CBC/PKCS5Padding，固定 16 字节 IV，key 由设备 ID 派生（包含静态后缀），结果 Base64。公开报告不披露 IV、后缀或真实 key，算法边界见 [storage.md](storage.md)。

### 位置与 POI

`api/sns/v1/system_service/uploadlocation` 是 POST form，仅显式参数 `latitude`, `longitude`。其 Java 类位于 `xhs.develop.location`，属于开发/诊断面；当前证据不足以证明普通前台会周期调用。

POI 搜索 `api/sns/v1/local/poi/getpoilist` 可发送：

```text
source keyword latitude longitude page size type [search_context]
```

其他 POI/内容模型可能携带 `note_content`、file IDs 和用户位置；公开报告不展示具体坐标或 POI 内容。

### 媒体与内容

- 上传对象为调用方选择的图片/视频文件或字节；可保留 EXIF。
- `QuickUpload`/UploaderResult 只返回对象 ID、预览/静态 URL、视频 ID 和存储元信息。
- 后续内容通过 `POST /api/sns/v2/note` 的 `common` + `image_info`/`video_info` 创建，详见 [network.md](network.md)。
- `/api/media/v1/imageinfo` 和 `/api/media/v1/video/meta_info` 位于 debug service，查询 `fileKey/video_id + caller`，返回媒体元信息，不能视作所有内容流的通用上传接口。

### 消息、WebView 与风控上报

Diandian 的 `SendMessage.context` / `PayloadContextModel.context` 可包含：

```text
x-access-token x-user-id x-device-id x-ip-info x-app-version
x-platform-info x-location-latitude x-location-longitude
```

`PayloadContextModel` 构造时把经纬度置空；`SendContext` 允许调用方填入可选经纬度。这是 Diandian 消息 envelope 的 context，不是所有普通 XHS API 的公共 header。

WebView monitor context 还包含 `userId`, `userToken`, `hashExp`, `userSessionId`, app/device/build/screen/network/track-session 字段。字段存在表示可上报范围，不代表每个事件都发送全部字段。

`libxyasf.so` 的 native 风控上传不属于对象存储：它把采集结果序列化/变换后，以 multipart `file` part 提交，文件名为 `image.jpg`、MIME 为 `image/jpeg`，目标是 `POST https://as.xiaohongshu.com/api/v1/d/upload`。具体采集字段见 [risk.md](risk.md)。

### 实时长连接

长连接不是文件上传接口，但会传输账号/设备握手和不透明业务 body。已确认的可上报范围是：

- 登录握手：`uid`, `sid`, `authType`, `domain`, `geographicCode`，以及 `deviceId`, `osVersion`, `appVersion`, `fingerprint`, `deviceName`, `platform`, `os`。
- 上行业务：`cmd`, `bizName`, `serviceId`, `alias`, `extraInfo` 和调用方给出的 `body byte[]`。
- 下行业务：`bizName`、服务端时间及 `Event{data, mid, ext_info}`；另有 signal、ACK code/message/body、房间、标签、kick-out 和时间同步消息。
- body 是业务序列化负载，字段内容由 `serviceId/bizName/cmd` 决定；静态逆向不能把每个 body 都解释为具体聊天或笔记内容。

完整字段号、protobuf 类型、ECDH/AES/gzip 边界见 [network.md](network.md)。

## 3. 下载数据范围

| 类别 | 来源/格式 | 本地范围 |
| --- | --- | --- |
| 图片 | CDN URL，可带 `imageView2`, `format`, `q`, `sign`, `t`, `aegis` | 缩略图、正文图、头像、广告/模板图片 |
| 视频 | CDN/static URL | 播放源、封面、预览视频 |
| 前端资源 | `fe-static.xhscdn.com`、平台 CDN | JS/HBC、页面资源、图标 |
| 广告素材 | 广告图片/下载域名 | 图片、跳转配置、下载记录 |
| DSL 模板 | 模板下载 URL | 模板文件、MD5、版本、最低 App 版本、本地路径 |
| 插件/补丁 | Petal/plugin 配置 | AI、扫码、地图、文档预览、脚本引擎等插件包 |
| 下载元数据 | `prdownloader.db` 等 | URL、路径、大小、进度、ETag、状态 |

文档下载完成后还会调用 `POST /api/sns/v1/note/file/download`，form 字段为 `document_id`, `note_id`；这是下载成功回执，不是文件字节接口。文档预览信息来自 `GET /api/sns/v1/search/doc/preview` 的 `doc_id`。

9.47 历史只读快照的 `prdownloader.db` 有 1145 条资源下载记录，2026-09-30 的 9.48 当前只读查询为 2025 条，主要为静态前端资源、广告图片和动态模板。它们不是同等数量的用户内容记录。

## 4. 快照中未观察到的范围

- 未发现笔记正文历史或完整发布草稿。
- 9.47 历史快照的 `msgDB` message/chat 表为空，只有 4 条通知摘要；当前只读查询已有 652 条 message，但本次不展开正文。
- 播放历史在历史快照为 0 行，当前只读查询为 1049 行。
- 搜索词历史没有有效业务行。
- 位置缓存对象未包含实际坐标；Wi-Fi 扫描列表为空。

这些是本次 9.47 快照的观察结果，不代表其他安装、时间点或 9.37 版本从未生成这些数据。
