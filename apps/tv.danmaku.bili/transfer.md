# 上传下载范围

## 1. 视频投稿上传

### 1.1 端点与报文

- 预上传：`https://member.bilibili.com/preupload`，可带 `r=probe`；
- 认证头：`X-Upos-Auth`；
- 分片任务由 `UpOSTask`/`UploadTaskInfo` 管理，事件名包括
  `upos.upload.init|preupload|part.start|part.end|merge|pause|cancel`；
- 元数据可另走 `meta_url`、`profile`、`meta_profile` 与转码探针。

### 1.2 本地范围

`BiliContributor.db.video_upload` 实际 DDL：

```sql
taskid, create_time, mid, file_path, file_name, file_length,
status, current_step, is_free_trafic, chunk_list,
uploaded_chunk_bytes, upload_error, upload_error_msg, auth,
upos_uri, biz_id, endpoint_list, upload_url_list, chunk_size,
chunk_retry_delay, chunk_retry_num, chunk_timeout, threads,
upload_id, key, bucket, profile, meta_profile, meta_url,
archive_from, ip, spend_time, error_detail_code,
error_detail_msg, lag_times, last_request_url
```

该表保存本地文件路径和上传认证/分片状态，属于投稿任务所必需；`auth`、`key`、
`upload_id`、`last_request_url` 是敏感凭据或诊断字段，不应公开行值。

## 2. 图片上传

会员购图片使用 `POST api/ticket/upload/imageUpload`；请求包含用户选择的图片和表单
上下文，只有用户触发上传时才产生该流量。静态代码没有证明会自动上传相册全量内容。

## 3. 离线视频下载

`offlineVideo.db.video_info` 关键列：

```text
aid, cid, season_id, episode_id, qn_path, downloaded_size,
storage_path, auth_code, sectionsDownloadedList
```

`periodic_downloader.db.download_task` 管理周期任务；`bilibili_archive` 管理归档任务。
播放器按服务端返回的 DASH/MP4 URL、backup URL 和清晰度分段下载，保存位置与分段
清单。DRM 片源是否可离线由 `play_limit`/credential 决定，不能由普通 M4S 逻辑推断。

## 4. 资源缓存

- `mod_resource_cache.db`：动态模块资源配置；
- `resmanager.db.res_cache`：资源 key/profile/路径；
- `staggers.db.stagger_res`、`stagger_expired`：错峰资源与过期；
- `player_history_r1.db`：播放进度、aid/cid、时长；
- `network_report_stats.db`：页面/总网络统计聚合。

## 5. 数据范围判断

上传范围是用户选中的视频、投稿元数据、分片和诊断；下载范围是服务端授权的媒体分段、
弹幕/元数据和缓存资源。未发现静默上传通讯录、短信、通话记录或相册全量扫描的调用链。
权限声明中的外部存储读取能力与实际自动上传是两个不同结论；前者存在，后者未被静态
证据证明。
