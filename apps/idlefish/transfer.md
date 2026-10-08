# 上传下载的数据范围

闲鱼有三条独立的上传通道和两条下载通道。本文件给出静态可证的端点、报文字段、
分片规则与本地路径依据。

## 1. 图片/视频上传：MTOP `uploadv2.do`

`mtopsdk.mtop.upload.service.UploadFileServiceImpl` 定义完整流程：

1. **取 token**：`getUploadToken(UploadFileInfo)`，`UploadFileInfo{ bizCode, filePath,
   privateData(已废弃), type }`；`type` 默认 `RESUMABLE`（枚举 `FileUploadTypeEnum`）。
   token 参数（`TokenParamsEnum` / `UploadConstants`）：
   `version, bizcode, appkey, t, utdid, userid, fileid, filename, filesize, segmentsize`。
2. **分片上传**：
   - URL：`<http|https>://<uploadToken.domain>/uploadv2.do`
     （是否 https 由 `RemoteConfig.useHttpsBizcodeSets` 含该 bizcode 决定）。
   - `POST`，`Content-Type: application/octet-stream`，`Content-Length = 分片大小`，
     带当前 `user-agent`。
   - 请求参数：`token, offset, retrytimes(可选)` + token 参数集。
   - 分片大小 `segmentsize`；`offset` 递增，可重试（`retrytime=1`，读超时 40s）。
   - `setCookieEnabled(false)`：上传请求**不带业务 Cookie**。
3. **响应头**：
   - `X-Error-Code`：`SUCCESS` 表示成功；`token_expired` 触发用
     `X-TimeStamp` 校准本地时间戳偏移后重取 token；
     其它值（`FAIL_BIZ_*` 系列：`FILE_TOO_LARGE`、`MISS_PARAMETER`、
     `PRIVATE_DATA_TOO_LARGE`、`UNSUPPORTED_FILE_TYPE`、`UNSUPPORTED_UPLOAD_TYPE`、
     `WRONG_FILE_SIZE`、`UNKNOWN_ERROR`）为业务失败。
   - `X-Data`：成功数据（URL decode 后为上传结果）。
   - `X-Error-Msg`、`X-Server-Rt`。
   - 传输错误码：`ANDROID_SYS_FILE_UPLOAD_FAIL(-105)`、`INVALID_UPLOAD_TOKEN(-102)`、
     `INVALID_UPLOAD_ADDRESS(-103)`、`INVALID_UPLOAD_OFFSET(-104)`、`FILE_INVALID(-101)`、
     `ADD_TASK_FAIL(-106)`、`NETWORK_ERROR(-100)`。
4. **业务层封装**：`com.taobao.idlefish.uploader.UploadServiceImpl` 使用
   `com.uploader.export`（Uploader SDK，`libtaopai_data_core`/`libworker_bridge` 支撑），
   按 `getFileType()` 分 `image`/`video`，在 `traceUploadBegin` 记录 type、路径、
   视频元数据。

**范围**：用户显式选择的本地图片、视频、音频、文件与消息附件；上传前可压缩/裁剪，
但通道本身不区分内容类型，只按 bizCode 与文件大小管理。

## 2. 对象存储直传：OSS `getststoken` + OSSClient

- `mtop.alibaba.idlefish.getststoken/1.0`（`ApiGetststokenRequest`）返回
  `AccessKeyId / AccessKeySecret / SecurityToken`。
- 调用方据此构造 `OSSStsTokenCredentialProvider` 并创建
  `new OSSClient(ctx, "https://oss-cn-hangzhou.aliyuncs.com", provider)`：
  - `oss/message/MessageFileManager`（聊天文件/图片）
  - `oss/knowledge/KnowledgeFileUploader`（知识库文件）
  - `fci/downloader/FCIDownload`（模型/规则包下载）
- 客户端携带的是**临时 STS 凭证**，权限范围与有效期由服务端 STS 策略决定
  （客户端无法决定，静态不可证）。
- 另有 `mtop.taobao.media.upload.token.get/1.0`（视频上传）与
  `mtop.upload`（通用上传）两个入口。

## 3. 行为/风险数据上传

- `mtop.taobao.idle.device.report/1.0`：设备与应用字段（见 [auth.md](auth.md) §4）。
- `mtop.alibaba.client.ccrc.risk.upload`、`mtop.alibaba.client.ccrc.algo.upload`：
  CCRC 风控样本与算法结果（见 [risk.md](risk.md)）。
- `mtop.verifycenter.rp.upload`：实人核验素材。
- `mtop.idle.idleadv.xyflowin.upload`、`mtop.taobao.idlehome.home.pop.interest.upload`、
  `mtop.taobao.search.highway.upload`、`mtop.taobao.powermsg.monitor.ack.upload`：
  曝光/兴趣/搜索/消息统计上报。
- UT 埋点：`app_device_activate`(19999) 等事件经 `PTBS.commitEvent` 聚合后批量上传。

## 4. 下载通道

### 4.1 MTOP 网关下载
- 动态资源与规则包：`mtop.atlas.getBaseUpdateList`、
  `mtop.alibaba.emas.publish.update.resource.get`、`mtop.taobao.idle.fci.js.fetch`。
- `remote_assets_info` 里的远程资源走 `appdownload.alicdn.com`。

### 4.2 CDN / 媒体下载
- 图片：`img.alicdn.com`、`gw.alicdn.com`、`heic.alicdn.com`、`ilce.alicdn.com`、`ossgw.alicdn.com`。
- 头像：`wwc.alicdn.com/avatar/getAvatar.do?type=sns&userId=...` 与
  `api.2.taobao.com/m/userAvatar.action?id=...`。
- 视频：`cloud.video.taobao.com`、`ice-pub-media.myalicdn.com`、`livenging/livecb` 系列。
- 离线模型：`assets/nativeInfo-asset-so-7z.json` 列出 `libzcachecore.so` 等 7z 压缩包
  （`moduleUrl = compressed-so/arm64-v8a/ZCache.7z`），运行期解压到
  `files/app_local_libs`/`files/app_down_libs`。

## 5. 本地落盘范围（设备端只读核对）

- `files/app_down_libs`、`files/app_local_libs`、`files/app_native_libs`：
  下发与预置 native 库。
- `files/app_zcache`、`files/AVFSCache`：HTTP 缓存与首屏缓存。
- `files/mmkv/splash_ad_mmkv`：开屏广告键值。
- `cache/`、`files/app_u4sdk`、`files/app_u4_webview`：WebView/UC 内核数据。
- `databases/*`：见 [storage.md](storage.md)。
- 数据库整体占 `405 MB`（含 native 库与缓存）。

## 6. 静态可证 / 不可证

- **已验证**：`uploadv2.do` 的 URL、头、参数、分片与响应头语义；OSS STS 凭证流转；
  上传/下载端点清单；本地目录布局。
- **结构已证实**：Uploader SDK 的 bizCode 与文件类型映射，具体 bizCode 常量表由
  服务端/远端配置下发。
- **不可证**：服务端保存时长、OSS bucket 的读权限、STS 策略的最小权限边界。
