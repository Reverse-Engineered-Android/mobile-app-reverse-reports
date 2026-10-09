# 网络交互

## 1. 创建请求

实现位于 GMS `bxmu.java`：

- URL base：`https://www.googleapis.com`
- 路径：`/androidantiabuse/v1/x/create?alt=PROTO&key=<redacted>`
- 方法：`POST`
- `User-Agent`：`DroidGuard/263737035`
- `Content-Type`：`application/x-protobuf`
- 超时来源：`jtdz.b() + jtdz.c()`
- 调用方上下文：`Binder.getCallingUid()` 进入请求管线
- body：`hvnz` protobuf
- response：`hvoa` protobuf

`bxmu.java:26-38` 根据 flow 是否为 `fast`/`full` 选择不同请求分支；
`bxmu.java:41-58` 完成 URL 构造、HTTP method/header、序列化与等待解析。
快速 flow 不等于无签名：响应仍在 `bxkz.java:49-56` 统一验签。

## 2. 响应处理与缓存

`bxkz.java:29-72` 的流程：

1. 读取配置的最大重试次数。
2. 每次调用 `bxmu.a` 获取 `hvoa`。
3. `hvoa.c` 是签名消息 body，`hvoa.d` 是签名。
4. 先解析 `hvpj`，再执行 `bxky.a(body, signature, flow)`。
5. 校验四个必需字段：`byteCode`、`vmUrl`、`vmChecksum`、
   `validityDurationSecs`。
6. `hvoa.e` 可提供附加字符串，写入结果对象。
7. 成功对象由 `bxku` 缓存；缓存键含 flow 与上下文。

## 3. VM 下载

`bxkz.java:73-91` 只在 URL 非空时调度
`com.google.android.gms.droidguard.loader.VmPrefetcherTaskService`，并强制：

```text
URL.startsWith("https://www.gstatic.com/droidguard/")
```

预取任务名由 URL 尾部生成，不携带用户数据。`bxkz.java:98-110` 要求下载成功
且 HTTP 状态为 200；否则按网络错误类型重试。下载对象是 DroidGuard VM/程序，
不是用户业务内容。

## 4. Clearcut/StreamZ 遥测出口

DroidGuard 另有一条独立于 `create` 的 Google 上行链路。`bxlr.java:23` 用
`STREAMZ_DROIDGUARD` log source 创建 telemetry factory；`bxmh.java:118` 将
上报组命名为 `gmscore_droidguard`。`arqm.java:78` 在 GMS 注册
`com.google.android.gms.droidguard` 的三个 log source：
`DROIDGUARD`、`DROIDGUARD_ONDEVICE`、`STREAMZ_DROIDGUARD`。

`bxmh.java:12-110` 定义 15 个 `/client_streamz/droidguard/` 指标：

| 指标类别 | 指标 | 字段 |
|---|---|---|
| 状态 | `get_results_status`、`init_status`、`snapshot_status`、`close_status`、`get_response_status`、`verify_response_status` | `flow`、`status` |
| 时延 | `get_results_latency`、`init_latency`、`snapshot_latency`、`close_latency`、`get_response_latency` | `flow`、`success` |
| 失败与等待 | `fallback_count` | `flow`、`cause` |
| 失败与等待 | `fallback_latency` | `flow`、`success` |
| 失败与等待 | `wait_on_init_latency` | `flow`、`cause`、`success` |
| 响应大小 | `client_response_size` | `flow`、`response_type` |

`bxll.java:23-107` 构造带 `flow`、操作/状态、成功标记和毫秒时延的消息，
`bxll.java:114-217` 将它们派发到上述指标。原始 DEX 中 Clearcut uploader 的默认
URL 是 `https://play.googleapis.com/log/batch` 与
`https://play.googleapis.com/log`，批量地址可由
`gms:playlog:uploader:batch_server_url` 配置。设备上的
`STREAMZ_DROIDGUARD` spool 文件存在，证明该 log source 已配置，但文件为空
不能证明本样本发生过实际发送。

这条链路证实 DroidGuard 存在第二类 Google 上行，但字段范围仅是操作元数据：
不含 Build 字段、API key、账号、GPU 像素、触摸、传感器、Bundle/Map、动态 VM
输入或结果 byte[]。

## 5. 结果返回而非 GMS 二次业务上传

`bxgx.d` 计算 `byte[]` 后：

```java
parcel.writeByteArray(resultBytes);
binder.transactOneway(1, parcel);
```

因此可证实的客户端边界是：

| 数据 | 方向 | 终点 |
|---|---|---|
| `hvnz` | 上行 | Google `create` 服务 |
| StreamZ 指标 | 上行 | Clearcut/Google Play 日志服务 |
| VM/bytecode | 下行 | `www.gstatic.com/droidguard/` |
| 风控结果 byte[] | 本地 IPC | Binder 调用方 |
| GPU/触摸/传感器 | 本地 | payload/native session |

在 DroidGuard/GMS 静态调用链中，未发现把上述本地采集项或结果字节发往网络的
端点；Clearcut/StreamZ 只上传上表列出的操作计数。

## 6. 与相邻信号链的区别

代码中另有 `chromesync_get_device_authorization_key`，其语义是设备授权/信号，
不是 DroidGuard `create`。`bxnf` 是 `DroidGuardClientOptions` 的
`reinitializeHandleOnGetSnapshot + extras`，不是 OAuth token。两者的类名或
“authorization”字样不足以并入 DroidGuard 上传协议。
