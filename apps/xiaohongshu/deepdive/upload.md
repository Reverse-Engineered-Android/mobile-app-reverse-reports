# 小红书 9.37.0 上传链路与数据范围

本文覆盖上传的：令牌获取、去重、分块、断点续传、MIME、云厂商分支，以及“具体上传哪些数据”。所有结论可由样本类与方法复核。

## 1. 上传数据范围（先回答“传什么”）

| 类别 | 是否上传 | 证据 |
| --- | --- | --- |
| 用户选择的图片/视频文件本体 | 是 | `MixedToken.filePath` / `fileBytes` → 云厂商 SDK put |
| 文件 MD5（全文件） | 是 | `internal/c.a(String path)` 全文件 `MessageDigest("MD5")` |
| 文件名 / fileId | 是 | `MixedToken.fileId`、`CheckVideoFileResult.filename` |
| MIME 类型 | 是 | `UploadOptions.mimeType`（`c0.n()` 派生） |
| 业务参数 `bid`/`biz_name`/`scene`/`file_count` | 是 | `TokenService.getTokenPermit(...)` |
| 分块信息（chunkSize） | 本地使用，不进请求体 | `x.c(fileLen)` |
| 相册 EXIF | **可选**，由调用方决定 | `UploadConfig.keepImageExif` |
| 文件副本 | 由 `needCopyFile` 决定 | `UploadConfig.needCopyFile` |

上传**不**把设备标识直接写进媒体请求：设备信息走 `xy-common-params` 与签名字段，媒体本身走云厂商 SDK（Qiniu/COS）独立请求。

## 2. 令牌/许可获取（GET + Query）

`com.xingin.uploader.api.internal.TokenService` 全部使用 `fvc.f`（GET）+ `fvc.t`（Query）：

```java
@fvc.f("/api/media/v1/upload/permit")
Observable<String> getTokenPermit(@fvc.t("bid") int i, @fvc.t("biz_name") String s,
                                 @fvc.t("scene") String s2, @fvc.t("file_count") int n,
                                 @fvc.t("version") int v);

@fvc.f("/api/media/v1/upload/capa/permit")        // 同上参数
@fvc.f("/api/media/v1/upload/permit_no_login")    // 同上参数
@fvc.f("/api/sns/v2/system_service/mix_cloud_upload_info")
Observable<String> getSecurityToken(@fvc.t("operator") String, @fvc.t("type") String,
                                    @fvc.t("business") int, @fvc.t("env") int,
                                    @fvc.t("cross_upload") boolean,
                                    @fvc.t("dynamic") boolean, @fvc.t("version") int);
```

选择逻辑（`internal/o.A()`）：

```java
if (k.k(i, scene))            permit = getTokenPermitIgnoreLogin(i, "", scene, 1,  VERSION);
else if (i == 0 && k.g())     permit = getTokenPermitForCapa(i, "", scene, fileCount, VERSION);
else                          permit = getTokenPermit(i, "", scene, fileCount, VERSION);
```

- `biz_name` 恒为空串，业务区分靠 `bid` + `scene`。
- `version` 取 `j0.c`，样本初值 `1`。
- `permit_no_login` 只对 `IGNORE_LOGIN_MAP` 命中的场景启用（样本中为 `17 → "login_log"`、`27 → "kuri_feedback"`），且 `file_count` 固定为 1。

响应是 **JSON 字符串**，再由 `Gson` 反序列化为 `RobusterToken` / `RobusterTokenPermit`。

## 3. 令牌字段（协议具体格式）

`RobusterTokenPermit`（`@mf.c` 即 JSON 键名）：

```
bucket, cloudType, currentTime, expireTime, fileIds[], masterCloudId, region,
secretId, secretKey, storageType, token, uploadAddr
```

非标注字段：`clientBootTime`（本地时钟基准）、`qos`；`fileIds` 为主键列表，上传时取 `fileIds.get(0)`。
缺省回退：`bucket→"ros-bucket"`、`region→"ros-region"`、`secretId→"ros-sid"`、`secretKey→"ros-sk"`。

`RobusterToken`（另一形态，含嵌套 `token_info`）：

```
address, bucket, cloud_type, v, qos, region,
token_info { expired_time, start_time, tmp_secret_key, tmp_secret_id, session_token }
```

`cloudType` 缺省为 `e.QCLOUD.ordinal()`。`RobusterToken.checkParams()` 要求 `token` 非空且 `expiredTime >= 0`。

本地缓存：MMKV 键前缀 `robuster_permit_ids_<scene><scene.hashCode()>` 与 `robuster_pending_id_...`，超过 3 小时清理（`internal/k.b()`）。

## 4. 去重（quick upload）

```java
@fvc.f("/api/media/v1/upload/quick_upload_check")
Observable<QuickUpload> quickUpload(@fvc.t("bid") int, @fvc.t("bizName") String,
                                    @fvc.t("scene") String,
                                    @fvc.t("dedupIdentifier") String,
                                    @fvc.t("dedupAlgorithm") String);

@fvc.f("/api/media/v1/upload/quick_upload_check")
Observable<String> quickUploadStr(@fvc.t("bid") int, @fvc.t("bizName") String,
                                  @fvc.t("scene") String, @fvc.t("dedupIdentifier") String,
                                  @fvc.t("dedupAlgorithm") String,
                                  @fvc.t("dedupChecksum") String, @fvc.x RequestTag);
```

调用点 `internal/o.z(...)`：

```java
tokenService.quickUploadStr(uid, "", scene, str3 /*identifier*/, str2 /*algorithm*/,
                            str4 /*checksum*/, timeout > 0 ? null : new b(timeout))
```

- `dedupIdentifier` = 文件 MD5（全文件）。
- `dedupAlgorithm` = 字面量 `"md5"`（`UploaderFlow` 中以 `z(..., "md5", md5, …, …)` 形式传入）。
- 命中判定：`QuickUploadResult.isHitQuickUpload() == success && code == 1`。

命中的响应结构：

```java
QuickUploadData { fileId, originFileId, originVideoId, previewUrl, staticUrl, videoId, result }
```

命中后直接构造 `UploaderResult(fileId, 200, previewUrl, "unknown", -1, "unknown", "unknown", bid, scene)` 并跳过云上传。

另有 `CheckVideoFileResult { md5, filename, exists }`，来自 `getQCloudUploadFileNameV2(md5s, type, num)`，用于批量探测“服务器是否已有该 MD5”。

## 5. 分块策略

```java
// com.xingin.uploader.api.x:54
public int c(long fileLen) {
    if (fileLen <= 0) return 1048576;                       // 1 MiB
    return (int)((((fileLen + 1999) / 2000) + 1048575) / 1048576) * 1048576;
}
```

语义：净载荷按 2000 块为上限反推每块大小，再向上取整到 MiB。

- ≤ 2000 MiB → 固定 **1 MiB**（`((len+1999)/2000) ≤ 1048576` 时上取整仍为 1 MiB）。
- 约 4 GiB → 2 MiB；约 8 GiB → 4 MiB。
- 上限 `(int)` 截断发生在约 2 PiB 量级，实际不可达。

默认值：`MixedToken.chunkSize = 1048576`；`u0`（`UploadConfig`，`:93-94`）默认 `chunkSize = 1048576`、`connectionTimeout = 15000`、`socketTimeout = 30000`、`needInternalRetry = true`。
Qiniu `Configuration.Builder` 自带默认 `2097152`，但 `c0:144` 构造时显式调用 `chunkSize(c(fileLen))` **覆盖**它，因此实际值以 `c()` 为准。

## 6. 云厂商分支

`cloudType` 决定实现类：

| 分支 | 类 | 传输方式 |
| --- | --- | --- |
| Qiniu | `com.xingin.uploader.api.c0` | `UploadManager.put(fileBytes/filePath, fileId, token, …)` |
| 腾讯 COS | `j2b/n.java` | `PutObjectRequest(bucket, fileIds[0], localPath)` |

Qiniu（`c0` 构造函数）：

```java
new Configuration.Builder()
    .chunkSize(c(len))                                   // ← 覆盖默认 2 MiB
    .recorder(new FileRecorder(tmpDir), new d0(this))     // ← 断点记录
    .zone(new pm.c(new String[]{ token.getAddress() }))   // ← 区域来自令牌
    .useHttps(true)
    .build();
```

上传调用：`uploadManager.put(fileBytes, fileId, token, handler, options)`，无 `fileBytes` 时用 `filePath`。完成后以 `jSONObject.getString("key")` 作为结果 URL 组成部分，并 `UploaderResult(..., "qiniu", masterCloudId, bucket, region, ...)`。

COS（`j2b/n.a`）：

```java
new CosXmlServiceConfig.Builder()
    .isHttps(true).setHost(permit.getAddress()).setRegion(permit.getRegion())
    .setConnectionTimeout(25000).setSocketTimeout(25000).enableQuic(false).build();

PutObjectRequest req = new PutObjectRequest(permit.getBucket(), permit.fileIds.get(0), str);
req.setRequestHeaders("Host", permit.getAddress(), false);
req.setRequestHeaders("User-Agent", "xhs-" + VersionInfo.getUserAgent(), false);
cosXmlService.putObject(req);   // 单次整对象 PUT
```

COS 凭据是临时三元组 `secretId/secretKey/token(session_token)` + `expiredTime`，通过 `SessionQCloudCredentials` 注入。**样本中未发现对上传对象使用 `Range`**：COS 路径是整对象 PUT，Qiniu 路径由 SDK 内部按 `chunkSize` 分块。

## 7. 断点续传

- Qiniu：`FileRecorder(tmpDir)` + `KeyGenerator d0`。记录文件名为 `hash(recordKey)`，`hash = SHA-1(recordKey)` 的十六进制；超过 48 小时（`172800000 ms`）视为过期并删除。
  记录键（`d0.gen`）：

```java
return uploadKey + mixedToken.chunkSize + "_._" + reverse(file.getAbsolutePath());
```

- COS 路径无本地断点记录，失败即整对象重传。
- 框架级重试：`UploaderFlow` 在 `retryCount > 0` 时 `retryWhen(new r(f0, …))`（`f0(retryCount, delayMs)`）。
- APM 埋点名 `uploader_breakpoint_and_resume`（`l1.a/b`），带 `task_id`/`file_type`/`error_code`。样本反编译结果中未定位到该埋点的调用方，故**只记录埋点定义，不宣称断点恢复的实际触发路径**。

## 8. MIME 与文件名

```java
// c0.n(MixedToken)
String ct = uploadConfig.e;                       // 调用方显式指定优先
if (isEmpty(ct)) {
    ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.getDefault());
    ct = qka.a.a.get(ext);                        // 扩展名查表
    if (ct == null) ct = qka.a.a.get("bin");      // 回退
}
return new UploadOptions(EMPTY_MAP, ct, true /*checkCrc*/, progressHandler, cancelSignal);
```

`qka.a`（34 条 `map.put`）覆盖 34 个扩展名 → MIME（图片为主：`jpg/jpeg/jpe→image/jpeg`、`png→image/png`、`gif→image/gif`、表内**没有**任何视频或 `webp`/`heic` 扩展名、`bmp/svg/tiff/ico/…`），缺省键 `bin → application/octet-stream`。
`UploadOptions.mime` 对空值再兜底为 `application/octet-stream`；`checkCrc = true`（Qiniu 会校验 CRC32）。

文件名/fileId 生成（`x.d` / `x.e`）：已有 `fileId` 则复用；否则用 `pka.a.a(pka.a.b(path + System.nanoTime()))`。

## 9. 超时与限流

| 项 | 值 | 位置 |
| --- | --- | --- |
| 连接 / 读超时（框架） | 15 s / 30 s | `api/u0.java:93-94` |
| COS 连接 / 读超时 | 25 s / 25 s | `j2b/n.java:52` |
| quick upload 阻塞超时 | 10 s | `api/internal/o.z()` |
| 令牌有效期检查 | 剩余 ≥ 600000 ms（10 min） | `j2b/s.java` 的 `a()` |

## 10. 证据等级

| 结论 | 等级 |
| --- | --- |
| 端点/参数名（permit、capa、permit_no_login、mix_cloud_upload_info、quick_upload_check） | 已验证 |
| `dedupAlgorithm = "md5"`，`dedupIdentifier` = 文件 MD5（全文件） | 已验证（`UploaderFlow` 调用点 + `internal/c.a`） |
| 命中判定 `success && code == 1` | 已验证（`QuickUploadResult.isHitQuickUpload`） |
| 分块公式 `c(fileLen)` 与 1 MiB 基线 | 已验证（源码 + 覆盖点） |
| Qiniu 配置、断点记录键与 48 h 过期 | 已验证 |
| COS 整对象 PUT、无 Range | 已验证（`PutObjectRequest` 单次调用；未发现 `GetObjectRequest.setRange` 用于上传） |
| MIME 表与回退链 | 已验证 |
| `uploader_breakpoint_and_resume` 实际触发路径 | **未定位**：仅埋点定义存在，反编译未见调用方 |
