# 隐私、告知与超范围

## 1. 同意闸门

`bxgx.java:93-95` 的汽车受限分支在 `gtrs.a(context)` 为真时拒绝运行，异常
文本为 `Can't run DroidGuard without gTOS acceptance`。`gtrs.java:11` 组合：

- automotive feature；
- `ro.android.car.restrictbytos`；
- `AutomotiveSetupServices__is_google_disabled_before_gtos`；
- `android.car.KEY_USER_TOS_ACCEPTED == 1`。

这证明客户端存在同意/运行条件检查，但单凭该谓词不能证明普通手机上每个
DroidGuard 输入都有逐项可见提示。隐私政策正文、服务端告知和保存期限不在
APK 内，静态分析不能替代实际告知证据。

## 2. 已证实采集

| 数据 | 触发 | 归属 |
|---|---|---|
| 26 项 Build 字段 | 构造 `hvnz` | 创建请求 |
| GMS 版本、`os.arch`、module 信息 | 构造 `hvnz` | 创建请求 |
| flow、fast/full 输入 | 调用方请求 | 创建请求 |
| GPU renderer/fingerprint | payload 运行 | 本地 |
| 32×32 GPU 像素 | payload 运行 | 本地 |
| 触摸、传感器、方向事件 | payload 运行 | 本地 |
| Bundle/Map extras、FileDescriptor | 调用方请求 | 本地 IPC |
| 本地文件前 1024 字节摘要 | payload 运行 | 本地 |

## 3. 已证实上传

DroidGuard 有两类已证实的 Google 上行：

1. `hvnz` 创建请求，字段集合见 `transfer.md` §1.1；
2. Clearcut/StreamZ 指标，字段集合见 `transfer.md` §1.2。

`hvnz` 的业务字段具有以下范围：

- 不含原始照片、位置、通讯录、短信、通话记录；
- 不含密码、私钥、OAuth token；
- 含高熵 Build 指纹，可用于稳定关联同型/同设备构建；
- `hvnz.k/l/o/p` 可包含调用方提供的额外字节、输入与字符串，故实际大小
  受调用方控制。

StreamZ 字段只包含 `flow`、`status`、`success`、`cause`、
`response_type`、毫秒时延和响应大小，不包含上述业务字段，也不包含本地采集值。

## 4. 本地采集不等于已上传

GPU、触摸、传感器与动态 VM 输入的采集调用点可证实；到
`/androidantiabuse/v1/x/create` 的字段映射不可证实。`bxjl.debug` 的 Build
setter 与 payload 的事件采集是两条不同数据路径。

结果 byte[] 经 Binder 返回调用方。DroidGuard/GMS 链路中存在 StreamZ 计数遥测，
但其字段中没有这些采集项或结果字节；因此不能写成“传感器、触摸、GPU 已直接
上传 Google”。

## 5. 超范围判断

| 判断 | 结论 |
|---|---|
| 创建请求超范围 | **存在高熵设备指纹风险**：Build 字段覆盖板级、硬件、型号、指纹、ABI、版本；这不是最小化模型信息 |
| 遥测上传范围 | **仅限操作计数**：flow、状态、成功、原因、响应类型、时延和大小，不含风控原始信号 |
| 本地信号超范围 | **静态证据不足**：采集能力明确，但未形成到网络的直接闭环 |
| 未经告知 | **不能仅凭客户端断言已证实**：有 gTOS 闸门，但逐项隐私告知文本不可见 |
| 超范围下载 | **未发现**：只下载固定 gstatic 前缀的风控程序/VM |

## 6. 服务端不可见项

评分阈值、结果用途、二次用途、保存期限、删除响应和第三方业务方收到结果后的
上传行为不能从固定客户端观察。本报告不推测这些服务端事实。
