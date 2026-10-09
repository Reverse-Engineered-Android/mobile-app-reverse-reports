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

失败会记录 DroidGuard telemetry 计数，但该计数代码没有形成可证实的
DroidGuard 业务数据上传端点。

## 3. VM 下载

`bxkz.java:73-91` 只在 URL 非空时调度
`com.google.android.gms.droidguard.loader.VmPrefetcherTaskService`，并强制：

```text
URL.startsWith("https://www.gstatic.com/droidguard/")
```

预取任务名由 URL 尾部生成，不携带用户数据。`bxkz.java:98-110` 要求下载成功
且 HTTP 状态为 200；否则按网络错误类型重试。下载对象是 DroidGuard VM/程序，
不是用户业务内容。

## 4. 结果返回而非 GMS 二次上传

`bxgx.d` 计算 `byte[]` 后：

```java
parcel.writeByteArray(resultBytes);
binder.transactOneway(1, parcel);
```

因此可证实的客户端边界是：

| 数据 | 方向 | 终点 |
|---|---|---|
| `hvnz` | 上行 | Google `create` 服务 |
| VM/bytecode | 下行 | `www.gstatic.com/droidguard/` |
| 风控结果 byte[] | 本地 IPC | Binder 调用方 |
| GPU/触摸/传感器 | 本地 | payload/native session |

在 DroidGuard/GMS 静态调用链中，未发现第四个把上述本地采集项发往网络的端点。

## 5. 与相邻信号链的区别

代码中另有 `chromesync_get_device_authorization_key`，其语义是设备授权/信号，
不是 DroidGuard `create`。`bxnf` 是 `DroidGuardClientOptions` 的
`reinitializeHandleOnGetSnapshot + extras`，不是 OAuth token。两者的类名或
“authorization”字样不足以并入 DroidGuard 上传协议。
