# 本地数据库

## 1. 只读核对方法

通过受控 SSH 对设备上的应用私有目录做只读核对；SQLite 以
`file:...?mode=ro&immutable=1` 打开，只执行
`sqlite_master`、`PRAGMA table_info` 和聚合计数，未写入、未修改 WAL、未读取账号行值。
27 个数据库的文件哈希、表数和表名保存在分析环境的 `device/db-inventory.tsv`；
本报告不发布账号维度文件名或行值。

## 2. 关键表与实际 DDL

### 2.1 投稿

`BiliContributor.db`

```text
video_upload(_id, taskid, create_time, mid, file_path, file_name,
file_length, status, current_step, is_free_trafic, chunk_list,
uploaded_chunk_bytes, upload_error, upload_error_msg, auth, upos_uri,
biz_id, endpoint_list, upload_url_list, chunk_size, chunk_retry_delay,
chunk_retry_num, chunk_timeout, threads, upload_id, key, bucket, profile,
meta_profile, meta_url, archive_from, ip, spend_time, error_detail_code,
error_detail_msg, lag_times, last_request_url)
open_screen_show(...)
```

### 2.2 离线视频

`offlineVideo.db`

```sql
CREATE TABLE video_info (
  aid, cid, season_id, episode_id, qn_path, downloaded_size,
  storage_path, auth_code, sectionsDownloadedList
);
```

`periodic_downloader.db.download_task` 保存下载任务；`bilibili_archive.archive_task`
保存归档任务。

### 2.3 播放与搜索

```text
player_history_r1.db:
  _player_main(_id,_m_user,_m_type,_m_primary_key,_m_secondary_key,
               _m_data,_m_time_stamp)
  _player_extra(_id,_e_key,_e_data)
suggestions.db:
  suggestions(_id,display1,query,date)
```

`_player_main._m_data` 内层 JSON 的结构包含 `aid`、`cid`、`pg`、`pgcnt`、`vtp`、
`cpos`、`dur`；报告不发布行值。

### 2.4 网络、资源和风控

```text
network_report_stats.db: network_stats, page_network_stats
mod_resource_cache.db: mod_resource_cache_config
resmanager.db: res_cache
staggers.db: stagger_res, stagger_expired
trackDatabase_new.db: BaseBean_new(log_id,eventId,pageName,common,
                                   dynamic,extensions,isUploaded)
neuron_core_report_data.db: neuron_report_data2
```

`trackDatabase_new` 的 `isUploaded` 字段是本地队列状态，不等于服务端已保存。

## 3. 格式判断

所有核对到的表均为普通 SQLite；未见 SQLCipher、整库 key 或列级加密。IM 数据库、
事件数据库和资源数据库的 schema/表数不同，不能把一个库的字段推广到全部库。
数据库中可能包含账号、设备、搜索词、观看进度和上传凭据，因此公开报告只给结构与
聚合计数，不给行样例。

## 4. 不可证部分

静态 schema 不能证明服务端是否接收、保存或删除某行；设备只读核对也不能推导远端
留存策略。报告将本地格式事实与远端数据治理问题分开。
