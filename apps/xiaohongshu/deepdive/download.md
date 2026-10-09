# 小红书 9.37.0 下载链路与 Range 范围

本文覆盖下载侧的：HTTP Range 的构造点、总长解析、缓冲语义、配置项与“哪些资源走哪种下载”。

## 1. 结论先行

| 问题 | 结论 | 证据 |
| --- | --- | --- |
| 首次打开是否带 Range | **否**，先发无 `Range` 的 GET 拿总长 | `AndroidHttpEngineOptimizer.openUrl()` 只 `setHttpMethod("GET")` |
| seek 是否带 Range | **是**，`Range: bytes=<offset>-` | 同文件 `:404` |
| Range 是否一直用到末尾 | 是，seek 后丢弃旧缓冲，从 offset 续读到 EOF | `mChunks.clear(); mFirstChunkOffset = 0;` |
| 206 如何取总长 | 解析 `Content-Range` 中 `/` 之后的值 | `:103-150` 附近 |
| 非 206 如何取总长 | 用 `Content-Length` | `:135-150` |
| 分片大小是否固定 | 客户端读取服务端配置 `http_range_size`；`o()`/`p()` 的静态兜底均为 0 | `l6a/o.java`、`l6a/p.java` |
| 是否所有下载都走 Range | **否**：本地播放器引擎走 Range；业务下载/升级走各自 SDK | 见第 5、6 节 |

## 2. 播放引擎的 Range 实现

`com.xingin.library.videoedit.internal.AndroidHttpEngineOptimizer` 是一个 `MediaDataSource` 风格的可随机访问 HTTP 读取器（API 34+ 的 `HttpEngine`）。

### 2.1 初始打开：无 Range

```java
// :288
UrlRequest req = httpEngine.newUrlRequestBuilder(url, executor, cb)
        .setHttpMethod("GET")
        .setPriority(4)
        .build();
req.start();
```

响应到达后按状态码取总长（`:103-150` 区域）：

- 有 `Content-Range` → 解析 `/` 之后的总长（206 语义）。
- 否则读 `Content-Length`（`:135` `asMap.get("Content-Length")`），解析失败置 `mError = "Invalid Content-Length: " + …`。
- 总长 `<= 0` 时抛 `IOException`。

### 2.2 seek：带 Range 重取

```java
// :404
UrlRequest req = httpEngine.newUrlRequestBuilder(mUrl, executor, cb)
        .setHttpMethod("GET")
        .addHeader("Range", "bytes=" + offset + "-")
        .setPriority(4)
        .build();
```

seek 前先彻底复位，避免把旧数据当新数据：

```java
// :380-400
urlRequest.cancel();
mUrlRequest = null;
mChunks.clear();
mFirstChunkOffset = 0;
mBufferedSize = 0;
mReadEof = false;
mReadFailed = false;
// 并复位 mFileLen = -1, mResponseReceived = false, mError = null
```

seek 后同样要求响应带 `Content-Range`，否则按 `Content-Length` 计算；错误信息会变成 `"Invalid Content-Length after seek: …"`（`:431`）。

### 2.3 读语义

`read(byte[], int, int)` 从 `mChunks` 缓冲取数据；缓冲空且未 EOF/未失败时阻塞等待。因此引擎是**顺序读 + 显式 seek** 的组合：播放器按需 seek，HTTP 层用 `bytes=<offset>-` 续传。

## 3. 服务端 Range 配置项

`l6a/o.java`（非高峰时段配置）与 `l6a/p.java`（PeakPeriod）用 `@mf.c` 描述服务端可下发的字段：

```
http_range_size, enable_dynamic_range, enable_dynamic_first_preload,
enable_network_speed_strategy, enable_playerai_strategy,
enable_related_cdn_start, enable_switch_cdn_to_pcdn, disable_mobile_use_pcdn,
buffer_scaling_for_first, buffer_scaling_for_related,
first_video_preload_duration, video_preload_by_page,
player_buffer_duration_string_range_end, player_buffer_first_buffer_range_end
```

`p`（高峰时段）还含 `startTime`、`endTime`、`preloadCount`。

**静态默认值**：`o` 的无参构造把这些字段全部置零/`false`：

```java
public o() { this(null, 0, 0, 0, 0, null, 0L, false, false, false, false, false, false, false, 16383, null); }
```

而 `p` 的合成构造给出的兜底默认值是另一套（`preloadCount=-1`、`bufferScalingForFirst=150`、`bufferScalingForRelated=100`、`firstVideoPreloadDuration=10`、`enableDynamicRange`/`enableDynamicFirstPreload`/`enablePlayerAIStrategy`/`enableNetworkSpeedStrategy`/`enableSwitchCdnToPcdn`/`enableRelatedCdnStart` 为 `true`、`disableMobileUsePcdn` 为 `false`）。因此：

- 配置对象的静态兜底不等于某次运行收到的服务端值；
- 客户端代码分析到此已经完整：`http_range_size`、`enable_dynamic_range` 是配置输入，静态缺省分别为 0 和 `true`；线上取值属于服务器/CDN 运行期数据，不属于客户端控制流缺口。

## 4. 其他 Range 使用点

| 位置 | 形态 | 说明 |
| --- | --- | --- |
| `com/hpplay/common/asyncmanager/FileRequest.java:195` | `Range: bytes=<downloadedSize>-` | HPPlay 投屏/本地下载 |
| `com/tencent/cos/xml/model/object/GetObjectRequest.java:230-231` | `addHeader("Range", range.getRange())` | COS 支持 `setRange`，但样本内未见业务侧调用 |
| `com/tencent/cos/xml/common/Range.java:22` | `String.format("bytes=%s-%s", …)` | COS Range 值格式（双端闭区间） |
| `xb/a.java:51` | `String.format(null, "bytes=%s-%s", …)` | 另一处区间格式化 |
| `com/hpplay/sdk/source/localserver/LelinkFileServer.java:141` | 解析入站 `bytes=` | 作为本地服务端**接收** Range |
| `com/hpplay/http/e.java:274`、`org/cybergarage/http/HTTPPacket.java:275` | `hasHeader("Content-Range") \|\| hasHeader("Range")` | 判断是否支持分段 |

注意 `xb/a.java` 使用 `bytes=%s-%s` 双端形式，与播放引擎的 `bytes=<offset>-` 开区间形式不同——两者是不同子系统的实现。

## 5. 上传侧的 Range 情况

- Qiniu：由 SDK 按 `chunkSize` 分块上传，不使用 HTTP `Range`（分块是 Qiniu 自己的 multipart 协议）。
- COS：`PutObjectRequest` 单次整对象 PUT，**样本内无 Range**。
- 因此“上传是否用 Range”的答案是**否**，与下载侧相反。

## 6. 资源/模型/升级下载

| 类别 | 走向 | 证据等级 |
| --- | --- | --- |
| 视频/图片播放 | `AndroidHttpEngineOptimizer`（Range 续读） | 已验证 |
| 端智能模型（PMML/分割模型） | 解包目录 `models_root/` 内随包分发；样本内未见独立的带 Range 模型下载器 | 结构已证实（无独立下载器） |
| APK/热修复/插件 | 走独立的下载服务（`com.xingin.advert.download`、`ScarletBundleProxyImpl` 等），使用文件级下载而非播放器引擎 | 结构已证实 |
| 主题/贴纸等资源 | 走通用文件下载工具，失败重取 | 结构已证实 |

**响应行为边界**：本文的结论范围是客户端如何构造请求、解析响应并调度下载器；CDN 实际返回的 `Accept-Ranges` 和不同资源的在线断点能力属于服务端运行期响应，不会改变客户端代码的已确认语义。播放器引擎的 Range 语义已逐行确认，其余类别已确认到实际下载器与入口。

## 7. 证据等级

| 结论 | 等级 |
| --- | --- |
| 初始 GET 无 Range | 已验证（`:288`） |
| seek 使用 `Range: bytes=<offset>-` | 已验证（`:404`） |
| seek 前清空缓冲与文件长度状态 | 已验证（`:380-400`） |
| 206 用 `Content-Range`、非 206 用 `Content-Length` | 已验证 |
| `http_range_size` / `enable_dynamic_range` 为服务端字段 | 已验证（`@mf.c`） |
| 服务端实际取值 | 运行期配置输入；静态缺省 0，线上值由服务器下发决定 |
| COS 路径支持 Range 但业务未调用 | 结构已证实（API 存在，调用点未见） |
| CDN `Accept-Ranges` 行为 | 服务端响应属性；客户端代码不预设该值 |
