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

`ITokenReqParam` 的类定义未被 JADX 输出，但构造点可确认默认业务类型为 SNS、scene 为 comment，并有额外版本/布尔参数。其精确字段名不作推测。

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
- 后续内容模型用 `fileIds`、`fileMediaIds`、topics、标题、正文等组合创建内容；具体 note publish gateway 未在 9.37 Java 明文中闭环。
- `/api/media/v1/imageinfo` 和 `/api/media/v1/video/meta_info` 位于 debug service，查询 `fileKey/video_id + caller`，返回媒体元信息，不能视作所有内容流的通用上传接口。

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

9.47 只读快照的 `prdownloader.db` 有 1145 条资源下载记录，主要为静态前端资源、广告图片和动态模板。它们不是 1145 条用户内容记录。

## 4. 快照中未观察到的范围

- 未发现笔记正文历史或完整发布草稿。
- `msgDB` 的 message/chat 表为空，只有 4 条通知摘要。
- 播放历史表为空。
- 搜索词历史没有有效业务行。
- 位置缓存对象未包含实际坐标；Wi-Fi 扫描列表为空。

这些是本次 9.47 快照的观察结果，不代表其他安装、时间点或 9.37 版本从未生成这些数据。
