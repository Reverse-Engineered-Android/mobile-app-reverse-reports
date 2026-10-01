# 上传与下载的数据范围

本文件按"接口 → 字段 → 数据范围"列出上传与下载，业务上传、诊断上报、风险上报
分开标注，避免把组件能力当成实际上传。

## 1. 上传：对象存储（galerie）

### 1.1 端点与任务类型

| 端点 | 用途 |
| --- | --- |
| `/api/galerie/public/signature` | 公开签名 |
| `/image/signature`、`/api/galerie/image/signature` | 图片签名 |
| `/file/signature`、`/api/galerie/file/signature` | 文件签名 |
| `/galerie/business/get_signature` | 业务签名 |
| `/v4/store_image`、`/api/galerie/v4/store_image` | 图片入库 |
| `/v2/general_file`、`/api/galerie/v2/general_file` | 通用文件 |
| `/api/galerie/large_file/v2/upload_init` | 大文件初始化 |
| `/api/galerie/large_file`、`/api/galerie/cos_large_file` | 大文件分片 |

任务类型：`file_upload`、`image_upload`、`video_upload`、
`video_pipeline_upload`。

### 1.2 表单与数据范围

| 项 | 值 | 数据范围 |
| --- | --- | --- |
| 请求体 | `multipart/form-data`，boundary `---011000010111000001101001` | 文件二进制 |
| 文件 part | 名固定 `file` | 待上传文件 |
| `filename` | `w51.d.f107325h`，否则路径 basename | 文件名 |
| `ext_info` | `w51.d.l()` 的 JSON 序列化 | 业务扩展信息 |
| `bucket_tag` | `w51.d.f107329j` | 目标 bucket |
| `sign` | `w51.d.f107337o` | 私有桶签名 |
| `enable_quick_upload` | `"true"` | 视频类 + `J0 && L0` 非空 |
| `quick_upload_md5` | `w51.d.L0` | **整文件 MD5**（秒传判定） |
| `quick_upload_crc64` | `w51.d.M0` | **整文件 CRC64**（秒传判定） |
| `create_media` | `"true"`/`"false"` | 是否建媒体记录 |
| `extra_params` | `w51.d.A0` JSON | 视频类附加参数 |

请求头：`anti-token`、`User-Agent`；`bucket_tag` 分支额外带
`AccessToken: w51.d.f107319e`。

### 1.3 上传相关的风控开关

| 开关 | 默认 | 作用 |
| --- | --- | --- |
| `ab_galerie_force_fill_anti_token` | `true` | 强制填 anti-token |
| `ab_enable_upload_check_exif` | `true` | 上传前检查 EXIF |
| `galerie_upload.anti_token_path` | 11 条路径 | 需要 anti-token 的上传路径 |

上传还使用 `ReplaceIpController` 做失败换 IP 重试；`k` 中的 `url_sign`(1/2)、
`cdn_sign`、`sign_private` 三种签名模式决定 URL 的签名方式。

## 2. 上传：风控与环境上报

| 上报 | 载体 | 字段 |
| --- | --- | --- |
| 设备画像 | `mi0/a.java` 组装的 map | Build/ROM/屏幕/存储/电池/包信息、Root 状态、模拟器、多开/`Settings.Secure` 两项，见 [risk.md](risk.md) |
| 设备信息密文 | MMKV `secure` 的 `enCryptInfoV3` | 由 `SecureNative.ng2(...)` 产出 |
| `data_type` 分派 | `SecureNative.p(context, map)` / `v(context, map)` | 按 `data_type` 走 1/4/6/7/12/13/16/17/20/21/22 共 11 条分支 |
| 环境完整性 | `SecureNative.ng/ne/eca/ecb/ecn/egv` | 字节/JSON → 密文字符串 |
| 长连接 | Titan `TitanApiRequest` | 自定义 headers + body 字节 |
| 网络留痕 | `netcapture` | 全量请求/响应 |

`SecureNative.p/v` 的 `data_type` 分支（`HomeTopTab` 常量）：`1` 默认、
`4`、`6`(`TAG_ID_WEB`)、`7`(`TAG_ID_GOVERNMENT_SUBSIDY`)、`12`、`13`
(=`MALL_BRAND_HEAD_TYPE_ENHANCE`)、`16`、`17`、`20`、`21`、`22`，其余落到
`g(lb2.a.c().a(context, map))`。这是"按场景选择不同字段子集"的机制，不是每次都全量
上报。

## 3. 下载

### 3.1 两个下载器

| 下载器 | 数据库 | 表 |
| --- | --- | --- |
| iris | `iris_downloader_{main,support,titan}_v12.db` | `irisCallerInfo`、`irisStartInfo` |
| okdownload | `okdownload-breakpoint{,-support,-titan}.db` | `breakpoint`、`block`、`okdownloadResponseFilename`、`taskFileDirty` |

`irisCallerInfo` 的完整列（下载任务元数据）：

```
iris_id, inner_id, url, filepath, filename, cache_filename, app_data,
speed_limit, status, current_bytes, total_bytes, priority, callback_ui,
verify_md5, verify_key, timeout, business, file_control_by_iris,
max_connection_count, last_modification, wifi_required, weak_reference,
send_broadcast, connection_type, headers
```

`irisStartInfo`：`iris_id, url, start_timestamp, start_process, retry_count,
business`。

`breakpoint`：`id, url, etag, parent_path, filename, task_only_parent_path,
chunked, respHeaderStr`；`block`：`id, breakpoint_id, block_index,
start_offset, content_length, current_offset`。

即下载器本地保存了**完整 URL、ETag、响应头、落盘路径、文件名、分块偏移**，
足以还原每个下载任务的来源与进度。

### 3.2 下载内容范围

| 类别 | 来源 | 本地位置 |
| --- | --- | --- |
| 图片 / 视频 | `cdn.pddpic.com`、`mcdn.pddpic.com`、`img.pddpic.com`、`commimg.pddpic.com` | 应用缓存目录 |
| 业务文件 | `file.pinduoduo.com` | 同上 |
| 运行时 native 库 | 动态加载器 | `files/dynamic_so/<name>_<epoch_ms>_<md5>/` |
| 组件 / 模板 | vita 组件库（`/volantis3-open/component/...`） | `files/.vita/<组件ID>/<版本>/`，路径登记在 `vita-database` 的 `UriInfo.absolute_path` |
| 长连接会话 | PNet | `files/network/pnet/.../ssl_session/` |

### 3.2.1 组件下发（Vita）的下载数据范围

Vita 是全 App 唯一按"组件"粒度批量下载代码/资源/So 的通道，具体范围见
[vita.md](vita.md) §7：

- **下载什么**：`<build_no>/<组件ID>.{zip,7z,br}` 三种压缩格式，或
  `diff/<本地build_no>/<远端build_no>/<组件ID>.br` 差分格式；另有
  `index/<build_no>/<组件ID>.zip` 离线索引。
- **格式选择**：由响应里 6 对 `url*`/`*_diff_url` 与客户端 `PatchType`
  （`ZIP/Z7/BR` × `FULL/DIFF`）比对决定，客户端偏好 Brotli。
- **校验材料**：每个包型带独立 `signkey`（SHA256WithRSA 签名），另有
  文件级 `<组件>.md5checker` 全文件 MD5 表。
- **加密**：`security_level ∈ {1,2}` 的组件走 `/volantis3-open/aes/...`，
  载荷为 AES-128-CBC（全零 IV）密文，密钥由 `security_key` 经 RSA 链解出。
- **回传**：`/api/app/v1/component/report` 会上报 `secure_level`、`secure_key`、
  `secure_version`、`available_space`、`patching_file_name`、
  `patching_old_file_size`、`lock_file_existed`、`manifest_exists`、
  `is_support_zip_patch`、`is_zip_diff_package`，以及 23 个下载/补丁/解密
  事件码（`download_start`…`ipc_download_fail`）。
- **实际落地量**（本设备只读快照）：`files/.vita` 46 个组件目录约 34.6 MB；
  `files/dynamic_so` 26 个目录约 74 MB；组件注册表 128 个，已安装 46 个，
  **82 个已注册但从未下载**。

### 3.3 上传/下载共用的头部

上传和下载都复用 `sv1.d.e(true)`（`Content-Type`、`Referer`、`AccessToken`、
`lat`、`ETag`）、`sv1.d.d()`（`x-app-ui`）、`sv1.d.f()`（`x-pdd-info`）与
`sv1.d.a()`/`sv1.d.b()` 拼出的环境查询串，因此**下载请求同样携带登录态**。
