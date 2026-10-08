# 上传下载的数据范围

本文件给出静态可证的上传/下载端点、报文字段、分片与本地跟踪表。未构造请求。

## 1. 图片上传

`com/zhihu/android/picture/upload/processor/oss/c.java`（`@k` 连接 20s、读 30s、写 30s）：

```java
@o("https://api.zhihu.com/images")                    Single<ImageUploadPayload> a(@a a aVar, @j Map<String,String>);
@o("https://api.zhihu.com/images/upload_token")       Single<ImageUploadPayload.Token> a(@a f fVar, @j Map<String,String>);
@p("https://api.zhihu.com/images/{image_id}/uploading_status") Single<ResponseBody> a(@s("image_id") String, @a e eVar, ...);
@f("https://api.zhihu.com/images/{image_id}")         Single<ImageMetaInfo> a(@s("image_id") String, ...);
```

- `mediauploader/api/a/a.java` 亦有同形
  `@p("https://api.zhihu.com/images/{image_id}/uploading_status")`（`Map<String,String>` body）
  与 `@f("/staging_contents")`（`ids`）。
- 策略分流：`com/zhihu/android/picasa/impl/UploadProcessorStrategyImpl.java` 定义 A/B
  `adr_upload_image_oss`，取值 `false`（legacy）/`true`（OSS）；`useOssUpload()` 返回
  当前分支。两分支共用上面的 token/状态端点。
- 本地跟踪：`MediaUploader.db` 的 `business_table`、`media_table`（见 §5）。

## 2. 视频上传

`lens.zhihu.com` 提供视频专用通道：

```
https://lens.zhihu.com/api/v2/videos/upload_token
https://lens.zhihu.com/api/v2/videos/{video_id}/uploading_status
https://lens.zhihu.com/api/v4/videos/upload_token
https://lens.zhihu.com/api/v4/videos/{video_id}/uploading_status
https://lens.zhihu.com/api/videos/{video_id}/uploading_status
```

投稿发布：

```
POST /zvideo-contribute/contribute/publish
GET  /zvideo-contribute/zvideos/{zvideo_id}/status
POST /zvideos/publish/content/{content_id}/{content_type}
POST /general/video/publish
GET  /zvideos/{zvideo_id}/publish/permissions?full_duration_millis=
PATCH/POST /videos/{videoID}/plugin/interactive
```

## 3. 通用对象直传

```
https://media.zhihu.com/zos/api/?action=ApplyUpload&version=v1
https://api.zhihu.com/zos/object/{id}/uploading_status
```

`mqtt-internal-public.zhihu.com` 与 `messaging.zhihu.com/zhihu/group/{udid}` 为消息通道。

## 4. 下载通道

- 图片 CDN：`pic1/pic2/pic3/pic4/pic5/picx/pica/picd.zhimg.com`、`zhihu-pics-upload.zhimg.com`。
- 视频：由播放器按返回的播放地址拉流（OGV/ZVideo），本地缓存见 §5。
- 下载器本地库：`filedownloader.db`（表 `filedownloader`、`Connection`）。
- 云控/补丁：`appcloud.zhihu.com`、`appcloud2.zhihu.com`、`m-cloud.zhihu.com`。
- 盐选试读缓存：`manuscript_preload_html.db`（表 `manuscript_html`）。

## 5. 设备端上传/媒体跟踪表（只读核对）

`MediaUploader.db`（Room）：

```sql
CREATE TABLE `business_table` (`content_id` INTEGER, `staging_content_id` INTEGER,
  `content_type` INTEGER, `cover_url` TEXT, `percent` REAL NOT NULL,
  `uploadedSize` INTEGER NOT NULL, `totalSize` INTEGER NOT NULL, `status` INTEGER,
  `extras` TEXT, PRIMARY KEY(`content_id`));

CREATE TABLE `media_table` (`media_id` TEXT NOT NULL, `object_key` TEXT NOT NULL,
  `business_id` INTEGER, `path` TEXT, `cached_path` TEXT, `album_path` TEXT,
  `status` INTEGER, `media_type` INTEGER, `extras` TEXT, PRIMARY KEY(`media_id`),
  FOREIGN KEY(`business_id`) REFERENCES `business_table`(`content_id`) ON DELETE CASCADE);

CREATE TABLE `xiangfa_table` (`xiangfa_content_id` INTEGER, `xiangfa_staging_content_id` INTEGER,
  `xiangfa_extras` TEXT, PRIMARY KEY(`xiangfa_content_id`));
```

即本地记录了每个上传任务的 `percent`/`uploadedSize`/`totalSize`/`status`/`object_key`/
`path`/`cached_path`/`media_type`，用于断点续传与相册缓存映射。

## 6. 上传数据范围结论

| 通道 | 范围 |
| --- | --- |
| 图片 | 用户显式选择的本地图片，经压缩/裁剪后上传 |
| 视频 | 用户显式选择的本地视频，经 `lens.zhihu.com` token 分片上传 |
| 稿件 | 回答/文章/视频稿件正文与封面 |
| 对象存储 | 由 `zos` ApplyUpload 授权的对象（范围由服务端决定） |
| 埋点/风控 | 设备指纹串、行为与播放日志（见 [risk.md](risk.md)） |

客户端不区分“上传内容是否含隐私”，也不在本地做内容审查；上传前处理仅限于
用户可见的压缩/裁剪。
