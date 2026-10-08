# 上传与下载的数据范围

## 1. 结论

静态可见的传输分六类：通用文件、日志分片、用户主动报警素材、设备连接画像、
轨迹/传感器、动态包与资源缓存。每类的字段集合都由客户端代码给定；报告不把
native 或服务端产生的密文内字段写成明文。

## 2. 上传

### 2.1 通用文件上传（multipart）

统一走 OkHttp `MultipartBody`。静态扫描到的 form 字段名全集：

```text
file, os, api, ts, appname, filelength, sliceid, sliceAt, sdk_ver,
piccontent, photocontent, audiocontent, speechcontent, debugcontent,
key, enReq
```

其中 `file` 是文件本体（`AndroidNativeBridge.java:331`），`key`/`enReq` 是接口
加密容器（见 [network.md](network.md) §5）。

### 2.2 日志分片上传

`af1/c.java:253` 构造完整分片上传请求：

```java
new Request.Builder()
    .url(base + "catch/log/slice_upload")
    .header("taskid", taskId)
    .header("sliceid", String.valueOf(sliceId))
    .header("sliceAt", String.valueOf(startPos))
    .post(new MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("file", file.getName(), new ze1.a(file, startPos, len))
        .addFormDataPart("os", "android")
        .addFormDataPart("api", "1")
        .addFormDataPart("ts", String.valueOf(System.currentTimeMillis()))
        .addFormDataPart("appname", m.a().a)
        .addFormDataPart("filelength", String.valueOf(file.length()))
        .addFormDataPart("sliceid", String.valueOf(sliceId))
        .addFormDataPart("sliceAt", String.valueOf(startPos))
        .addFormDataPart("sdk_ver", "15.3.5.1")
        .build())
    .build()
```

`ze1/a.java:13` 定义 `MediaType "multipart/form-data"`，`:13` 起是流式分片
RequestBody，只发送 `[startPos, startPos+len)` 区间。

单次上传的最大规模在分片层控制：`SliceRecord` 表记录
`sliceId/sliceCount/startPos/endPos/fileSize/uploadCount`（见 §3.1），
按片重试而非整文件重传。

### 2.3 一键报警素材上传

`ce1/p.java:96-126`，目标域名 `https://poi.map.xiaojukeji.com`（`:101`），
接口注解 `@o("trafficevent")`（`de1/a.java:13`），超时
`connect 10s / read 60s / write 60s`（`:98`）：

| part | 来源 | 条件 |
|---|---|---|
| `piccontent` | `reportItem` 主图文件 | 文件存在 |
| `photocontent` | `extraInfo.c` 列表逐张 | 每张存在 |
| `audiocontent` | `extraInfo.d` 录音文件 | `z9` 且文件存在 |
| `speechcontent` | `showInfo.speechContent` 文本 | 非空 |
| `debugcontent` | 调试文本 | 非空 |

全部 part 的 content-type 为 `application/octet-stream`。该接口在图片数 ≤1
时走 `t4(map, ...)` 的另一条路径（`:94`），图片数 >1 时走 multipart 聚合路径。

### 2.4 设备连接画像上报

`com/didi/sdk/safereport/network/SafeReportUtil$reportDeviceConnectInfo$1.java`：

```java
// :54  Gson 序列化后 UTF-8 → Base64
// :101 POST /api/guard/deviceConnect/reportV2, body = {"body": <base64>}
```

`DeviceConnectInfo` 字段（`com/didi/sdk/safereport/model/DeviceConnectInfo.java`）：

```text
backParams, collectType, reportTs, userType, accessKeyId, startCollectTs,
deviceName, canConnectList, curConnectList, historyConnectList,
functionalStatus, permissionStatus, failedReason
```

`ConnectDeviceInfo` 字段（同目录 `ConnectDeviceInfo.java`）：
`bondState, rssi, type, curConnectList` 等，即**周边蓝牙设备**的配对状态、
信号强度与类型。

配套读取接口 `/api/guard/deviceConnect/getCollectStatus`（同文件）。

触发源是 `DMCSafePushListener`，属安全盾（SafetyGuard）链路。

### 2.5 轨迹与传感器上传

`com/didi/trackupload/sdk/` 用 protobuf 编码
（`com/didi/trackupload/sdk/datachannel/protobuf/TrackUploadReq.java`）。

本地落盘表 `tbl_track_nodes` 的 21 列
（`TrackNodeEntityDao.java:29-48` 属性定义与设备端 schema 完全一致）：

| 列 | 类型 | 说明 |
|---|---|---|
| `_id` | INTEGER | 主键 |
| `lat` / `lng` | REAL | 坐标 |
| `type` | INTEGER | 点类型 |
| `src` | INTEGER | 来源 |
| `accuracy` | REAL | 精度 |
| `direction` | REAL | 方向 |
| `speed` | REAL | 速度 |
| `altitude` | REAL | 海拔 |
| `accelerated_speed_x/y/z` | REAL | 三轴加速度 |
| `included_angle_yaw/roll/pitch` | REAL | 姿态角 |
| `time` / `time64` / `time_local` | INTEGER | 三种时间 |
| `tags` | TEXT | 标签 |
| `map_extra_point_data` | BLOB | 地图扩展点 |
| `scene_type` | INTEGER | 场景 |

另有 `tbl_biz_nodes(tag, client_type, extra_data BLOB)`。

存储加密策略在 `tt1/d.java:98-133`：

```java
bVar3.f63068c = kVarB.a() && !z3;                       // :98  开关
if (bVar3.f63068c) {
    new a.C0556a(context2, "track_upload_sdk_encrypted_v2.db", null, 3)
        .getEncryptedWritableDb("track_upload");        // :104  SQLCipher，口令 "track_upload"
} else {
    new a.C0556a(context2, "track_upload_sdk2.db", null, 3).getWritableDb();  // :108
}
```

设备端 `track_upload_sdk2.db` 存在且可读，说明该设备上开关为关闭态。

### 2.6 语音录制上传

`audio_record_2.record_result` 表（26 列）给出完整上传契约：

```text
caller, businessId, businessAlias, audioFilePath, encryptedFilePath,
fileSizeInBytes, voiceLenInSeconds, startRecordTime, finishRecordTime,
orderIds, clientType, utcOffsetInMinutes, token, language, uploadRetryCount,
extraJson, uploadUrl, signKey, userId, voiceStatus, isLastFile, maxCount,
isCanUpload, status, sampleRateType, sliceDurationSeconds
```

其中 `uploadUrl` 与 `signKey` 由服务端在录音开始时下发，`token`/`userId` 标识
归属，`encryptedFilePath` 说明本地先加密再上传。`asr_result`
（`asrText, time, oids, clientType`）保存识别文本。

`VoiceRecognitionModule` 与 `com/xiaoju/speechdetect/SpeechDetectClient` 是语音
链路入口；`didi_speech_download.db.down_thread` 记录语音资源的分块下载进度。

### 2.7 日志与崩溃

| 库/表 | 用途 |
|---|---|
| `log.db.TaskRecord/TaskFileRecord/SliceRecord` | 分片任务状态 |
| `bizsafety_dfbasesdk.db.logs` | 业务安全日志（`content,url,extraParams,upStatus,cTime,uTime,failCount`） |
| `monitor.db.alitx_monitor` | 监控事件（`timestamp,urgency,strategy,upload_flag,upload_count,content`） |
| `helios/logger/upload/AndroidNativeBridge` | native 侧日志上传桥 |
| Breakpad/xcrash | 崩溃转储（`com/didichuxing/tools/nativecrash`） |

`bizsafety_dfbasesdk.db.logs` 的 `upStatus`/`failCount` 两列说明日志按条重试
而不是一次性丢弃。

## 3. 下载

### 3.1 下载相关的四个数据库

| 数据库 | 表 | 列 |
|---|---|---|
| `didi_onedownload.db` | `download_history` | `_id, url, finish_time, file` |
| `didi_onedownload.db` | `download_log` | `_id, url, thread_id, downloaded_size, file` |
| `didi_speech_download.db` | `down_thread` | `_id, thread_id, start, downloaded_size, url` |
| `download_file.db` | `tb_download_file` | `_id, url, downloaded_size, file_size, e_tag, last_modified, accept_range_type, file_dir, temp_file_name, file_name, status, create_datetime` |

`tb_download_file` 的 `e_tag` / `last_modified` / `accept_range_type` 三列组合
是标准的 HTTP 断点续传契约（`If-Range` + `Range`）。

DDL 来源：`ic1/a.java:14-15`（onedownload）、`u00/e.java:31-36`（download_file，
库版本 3，表名 `tb_download_file`）。

### 3.2 DRN / Hummer 动态包

```text
/bundle/api/pre/query            预下载查询
/bundle/api/batch/query          批量查询
/bundle/api/single/query         单个查询
/bundle/api/get/config           全局配置
/bundle/api/recommend/download   推荐下载
/bundle/api/getLockBundle        锁定包
/bundle/api/get/subPackages      子包列表
/bundle/info                     包信息
```

`com/drn/bundle/manager/repo/a.java:27-62`。下载 URL 由服务端在
`DownloadModel.downloadUrl` 下发（`com/didi/drn/download/pkg/impl/g.java:214,292`），
客户端在 `:337` 记录“开始断点续传下载”，并在
`com/drn/bundle/manager/core/DefaultSubPackageLocalIntegrityChecker$verifyFileMd5$1`
校验 MD5。

### 3.3 CDN 静态资源

`com/didi/assistant/common/utils/ResLoader.java:332` 等以
`https://gift-pypu-cdn.didistatic.com/static/llm_assistant/` 为前缀拉取图片、
Lottie JSON 与视频；`:47` 列出 `flow_9.mp4`、`llm_card_smile.webp`、
`llm_new_background_animation_lottie.json` 等具体资源。

### 3.4 DNS 缓存

| 数据库 | 表 | 列 | 行数（本机） |
|---|---|---|---|
| `dns_record.db` | `dns` | `id, host, ips, type, time, ttl` | 28 |
| `lolly_room_db` | `dns_record` | `host, ips, t, load_time` | 75 |

两套 DNS 缓存并存，说明历史模块未清理。缓存内容为域名到 IP 的映射，不含用户
数据。

### 3.5 首页与配置缓存

设备端实际存在的缓存目录/文件：

```text
files/home_cache            files/framework-HomeTabStore
files/framework-MisConfigStore   files/framework-WebConfigStore
files/business-BizConfigStore    files/framework-hydrastore
files/cities_cache (shared_prefs) files/address_cache
files/didimap / didimap_address  files/drn
files/omega / files/omegacache   files/tracking
files/upgrade               files/{bank_ocr_detect_1.0.2, bank_ocr_recognition_1.0.3}
files/accessAlgoModels      files/brdc28105.26n
files/wsg_downgrade_apollo  files/wsg_khporujx_wsg_{init_report_delay_switcher,time_interceptor,time_sign,time_wsgenv}
```

`wsg_*` 四个前缀目录是 WSG 自身的时间统计与 Apollo 降级缓存；`brdc28105.26n`
是北斗星历文件；两个 `bank_ocr_*` 目录是银行卡 OCR 模型。

## 4. 上传下载的加密与完整性

| 机制 | 位置 |
|---|---|
| 接口加密（`key`+`enReq`/`enRes`） | `com/didi/safety/onesdk/encrypt` |
| 分片上传的流式 RequestBody | `ze1/a.java`、`AndroidNativeBridge$b` |
| 动态包 MD5 校验 | `DefaultSubPackageLocalIntegrityChecker` |
| 断点续传（`e_tag`/`accept_range_type`） | `tb_download_file` |
| 录音本地加密 | `record_result.encryptedFilePath` |
| 传输层自定义证书 | `didinet.c.a()` + `disable_certificate_encryption_toggle` |

## 5. 数据范围判定

| 面向 | 判定 |
|---|---|
| 通用文件 | 由调用方决定，接口本身不限定类型 |
| 日志分片 | 固定协议，内容为应用自身日志 |
| 一键报警 | 用户主动触发，含图片/录音/文本 |
| 设备连接 | 自动采集周边蓝牙设备名、配对态、RSSI |
| 轨迹 | 条件触发，含坐标、速度、三轴加速度、姿态角 |
| 语音 | 录音开始即由服务端下发 `uploadUrl`+`signKey` |
| 动态包/资源 | 仅下载，不含用户数据 |
| DNS 缓存 | 域名→IP，仅服务质量用途 |

没有静态证据显示客户端在用户未触发的情况下上传相册、通讯录或聊天记录。
`QUERY_ALL_PACKAGES` 与 `queryIntentActivities` 的组合仅用于可用性探测，
不上传完整包列表（见 [permissions.md](permissions.md) §4）。
