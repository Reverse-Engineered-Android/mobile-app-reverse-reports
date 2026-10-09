# DroidGuard 最终报告

## 1. 总体结论

DroidGuard 是 Google Play services 中的本地风控执行器：客户端先构造创建请求，
上传设备 Build 指纹、GMS 版本、flow 名与运行模式；服务端返回经 RSA 签名的
程序字节码、VM 地址、校验和与有效期；客户端只允许从固定 gstatic HTTPS 前缀
下载 VM，并在本地 payload 进程内采集 GPU、触摸、传感器等信号，由 native 动态
VM 执行风控程序；`.b` 的 AES 外层、寄存器/PC 编码和 handler MBA 均已还原，
最后把结果字节通过 Binder 交回调用方。

| 维度 | 最终结论 | 精确依据 |
|---|---|---|
| 网络流程 | `create` 上行、gstatic VM 下行、Clearcut/StreamZ 计数上行 | `network.md` §1–§6 |
| 协议 | proto2 typed protobuf，创建请求 14 个 wire 字段 | `protocol.md` §1 |
| 认证 | Binder UID、调用方上下文、API key 查询参数、响应 RSA 验签 | `auth.md` |
| 上传范围 | `create` 为 Build/GMS/flow/模式与上下文；StreamZ 仅为操作计数 | `transfer.md` §1 |
| 下载范围 | 验签后的 bytecode、VM URL、checksum、有效期 | `transfer.md` §2 |
| 本地采集 | GPU 32×32、触摸、传感器、方向、Bundle/Map 上下文 | `risk.md` §3 |
| 动态风控 | 固定 native 解释器执行签名程序；`.b` 外层和数据流编码公式已恢复 | `risk.md` §4 |
| 密码学 | RSA-2048/SHA256withRSA、SHA-1、SHA-256、AES-128 均已定位用途与地址 | `risk.md` §5 |
| 越权 | 未发现 DroidGuard 自身绕过 Android 权限或访问未声明权限的调用闭环 | `permissions.md` §4 |
| 提权 | 未发现提权、注入、静默安装或权限修改链 | `permissions.md` §5 |
| 未经告知 | gTOS/汽车分支存在运行闸门；客户端能力与实际上传分开判断 | `privacy.md` §4 |
| 超范围 | 创建请求确含高熵 Build 指纹；未发现传感器、触摸、GPU 结果由 GMS 直接上传 | `privacy.md` §5 |

## 2. 主要交互流程

1. `DroidGuardResultsRequest` 与 Binder handle 进入 `bxgx.d`。
2. `bxjs.b` 检查缓存；缺失时由 `bxjl.a` 构造 `hvnz`。
3. `bxmu.a` 向
   `/androidantiabuse/v1/x/create?alt=PROTO&key=<redacted>` 发送
   `POST application/x-protobuf`。
4. `bxkz.a` 解析 `hvoa`，以 `bxky.a` 验证 RSA 签名，再解析 `hvpj`。
5. 客户端检查 `byteCode`、`vmUrl`、`vmChecksum`、`validityDurationSecs`；
   仅当 URL 以 `https://www.gstatic.com/droidguard/` 开头时启动预取。
6. payload 构造器再次用两枚 RSA-2048 SPKI 公钥之一验证 bytecode。
7. `initNative` 创建 native session，`ssNative`/`xssNative` 执行风险程序，
   `heNative` 接收额外事件，结果 byte[] 经 Binder 写回调用方。
8. 独立 telemetry 链由 `bxlr.java:23` 构造，经
   `/client_streamz/droidguard/*` 收集 flow、状态、成功、原因、时延和响应大小，
   最终交给 Clearcut/Google Play 日志服务。

## 3. 认证与完整性

创建请求没有携带账号 OAuth token；代码把 `Binder.getCallingUid()` 传入请求
管线，同时使用 GMS API key。响应签名在 `bxky.java:28-46` 以
`SHA256withRSA` 验证；payload bytecode 在 `DroidGuard.java:93-120` 用两枚
294 字节 RSA-2048 SPKI 公钥再次验证。

`bxgx.java:93-95` 存在汽车受限配置下的 gTOS 运行闸门，异常文本明确拒绝在该
分支运行。该谓词由 automotive feature、`ro.android.car.restrictbytos`、
`AutomotiveSetupServices__is_google_disabled_before_gtos` 和
`android.car.KEY_USER_TOS_ACCEPTED` 组成。标准手机端未观察到创建请求携带
账号 token，不能把 API key 误写成用户身份认证。

## 4. 数据范围与隐私

### 已证实上传

`bxjl.debug.java:37-122` 明确写入 GMS 版本、`os.arch`、module 版本/标志和
完整 `android.os.Build` 字段，包括 `BOARD`、`BOOTLOADER`、`BRAND`、ABI、
`DEVICE`、`DISPLAY`、`FINGERPRINT`、`HARDWARE`、`HOST`、`ID`、
`MANUFACTURER`、`MODEL`、`PRODUCT`、`RADIO`、`TAGS`、`TIME`、`TYPE`、
`USER`、版本代号/增量/发布版/SDK。`bxjl.d` 写 flow，`bxjl.c` 写额外 byte[]。

### 已证实下载

服务端返回的 `byteCode`、`vmUrl`、`vmChecksum`、`validityDurationSecs` 是
风控程序交付物，不是用户业务文件。下载完成后仍需通过 URL 白名单、HTTP 200、
checksum 和两层 RSA 验签。

### 本地采集但未由已分析上行携带

payload 采集 GPU 像素、触摸轨迹、传感器/方向事件、动态 VM 输入与调用方
Bundle/Map。结果通过 Binder 返回调用方。已分析链路中没有把这些采集项写入
`/androidantiabuse/v1/x/create`，Clearcut/StreamZ 字段也只有操作计数；因此两类
已证实上行均未携带这些原始值。

## 5. 越权、提权与超范围

| 判断 | 结论 |
|---|---|
| 越权 | **未发现**。payload manifest 无危险权限；采集依赖 GMS/调用方已有上下文与公开 Android API。 |
| 提权 | **未发现**。未见 `su`、root、注入系统进程、静默安装、权限提升或 Binder 绕过。 |
| 未经告知 | **存在运行前同意要求，但客户端静态证据不能证明每个数据项均逐项告知**；汽车受限分支有显式 gTOS 闸门。 |
| 超范围获取 | **创建请求包含高熵设备指纹；StreamZ 仅上传操作计数；本地传感器、触摸、GPU 数据未进入已分析上行**。 |

## 6. 风控机制

- flow 分类：`fast`、`full`、`msa-l` 等进入不同请求与缓存分支。
- 服务端程序：`byteCode` 由两层 RSA-SHA256 签名保护，带 checksum 与有效期。
- 本地缓存：`app_dgp` 下 `.b` 为程序字节、`.d` 为辅助字节，`dg.db`
  按 flow/FINGERPRINT 键、创建时间和过期时间管理；过期时删除行和文件。
- `.b` 外层：前 4 字节 IV，AES-128 key 由 `0x19a70` 初始化，`0x46cb0` 按
  `IV || uint32_le(block) || 8×00` 生成 keystream 并逐块 XOR，解码长度为
  `len(raw)-4`；三个样本的解码摘要见 `evidence.md` §6。
- native 动态 VM：`initNative` 创建 session；`ssNative`/`xssNative` 接收
  flow/Bundle/Map；`heNative` 接收事件；`closeNative` 结束会话。
- 数据流编码：寄存器 type/value 按 `frame+(i+1)*16(+8)` 存放，`0x432c4` 以
  root key、reg8 和 type/rotation 推导 PC/operand offset，字符串缓冲按寄存器
  索引导出 XOR key，handler 再执行专属内联 MBA。
- 环境信号：Build、ABI、GPU renderer/fingerprint、32×32 `glReadPixels`、
  文件/内存摘要、触摸和传感器。
- 密码学：固定代码包含 RSA-2048/SHA256withRSA、SHA-1、SHA-256 和 AES-128；
  AES 硬件路径 `0x6f20/0x71a0` 与 fallback `0xa3d0/0x9c20` 均与标准
  AES-ECB 核对；用途、输入输出与调用点均已列明。

## 7. 研究边界

本报告使用静态反编译/反汇编、本地受控 Unicorn 执行和设备只读核对；没有在设备
执行 payload、请求服务端评分或发送网络流量。服务端评分规则、处罚策略、保存
期限、第三方调用方拿到结果后的上传行为不可由本样本的客户端固定代码证明，
报告不作推测。
