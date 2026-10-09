# 上传下载与数据范围

## 1. 明确上传到 Google 的数据

### 1.1 `hvnz` 创建请求

`bxjl.debug.java:37-50`：

- GMS 版本 `26.37.37 (260400-994713346)`
- DroidGuard/module 环境标志
- module version

`bxjl.debug.java:91-122` 写入的 Build 键值对：

| 类别 | 字段 |
|---|---|
| 板级/启动 | `BOARD`、`BOOTLOADER` |
| 品牌/设备 | `BRAND`、`DEVICE`、`MANUFACTURER`、`MODEL`、`PRODUCT` |
| ABI | `CPU_ABI`、`CPU_ABI2`、`SUPPORTED_ABIS` |
| 构建标识 | `DISPLAY`、`FINGERPRINT`、`HARDWARE`、`HOST`、`ID` |
| 基带/构建类型 | `RADIO`、`TAGS`、`TIME`、`TYPE`、`USER` |
| Android 版本 | `VERSION.CODENAME`、`VERSION.INCREMENTAL`、`VERSION.RELEASE`、`VERSION.SDK`、`VERSION.SDK_INT` |

此外：

- `bxjl.d(String)` 写 flow 名；
- `bxjl.c(...)` 写额外 byte[]；
- `hvnz.e` 写 `os.arch`；
- `hvnz.l`/`hvnz.m` 写 fast/full 输入与模式；
- `hvnz.o`/`hvnz.p` 写附加字符串/布尔。

这些字段构成已证实的上行集合。`FINGERPRINT`、`BUILD`、ABI、硬件和型号组合
具有较强设备关联性，即使不含账号信息也是高熵设备指纹。

## 2. 明确下载的数据

`hvoa` 解包出的下载/执行材料：

| 字段 | 数据范围 | 本地保护 |
|---|---|---|
| `byteCode` | DroidGuard 风控程序字节码 | 外层 RSA-SHA256 + payload 二次 RSA-SHA256 |
| `vmUrl` | VM 下载地址 | 必须以固定 gstatic HTTPS 前缀开头 |
| `vmChecksum` | VM/程序校验和 | 非空校验 + 下载校验 |
| `validityDurationSecs` | 程序有效期 | 非空校验 + 本地缓存过期 |
| `hvoa.e` | 附加字符串 | 仅在响应包含该字段时保存 |

`bxkz.java:82` 的主机/路径白名单和 `bxkz.java:98-110` 的 HTTP 200 要求
阻止任意 URL 下载。下载内容不是通讯录、位置、照片或账号文件。

下载后的程序与辅助字节会写入 GMS 私有目录 `app_dgp`：

- `.b` 保存 `hvpj.byteCode` 的程序字节；
- `.d` 保存 `hvoa` 解析出的辅助字节；
- `dg.db` 只保存缓存键、创建/过期时间和版本；
- 当前设备有 `fast`、`pia_express`、`ad_attest` 三组缓存，
  程序文件大小为 63666、87050、64561 字节；
- 缓存键含 `Build.FINGERPRINT`，但真实键值和程序正文不进入报告或 Telegram。

该缓存不包含通讯录、短信、照片、位置轨迹、剪贴板或其他用户业务文件。

## 3. 本地采集但不由 GMS 直接上传

| 数据 | 采集点 | 去向 |
|---|---|---|
| GPU renderer/fingerprint | `DroidGuard.java:285-330` | native session/结果输入 |
| 32×32 GPU 像素 | `glReadPixels` | native session/结果输入 |
| 触摸事件 | `events/*.java`、`DroidGuard.java:477-531` | payload 内存/结果输入 |
| 传感器/方向事件 | `events/*.java` | payload 内存/结果输入 |
| 调用方 Bundle/Map | `_seigd`、`xssNative` 参数 | native session |
| 文件/内存摘要 | `droidguasso/h.java` 等 | 本地环境信号 |

结果 byte[] 通过 Binder 返回调用方。静态调用链中没有
`这些采集项 → create 请求` 的路径，也没有第二个 DroidGuard 业务上传端点。

## 4. 不混入 DroidGuard 上传的数据

- `verify_apps.db` 的 apk/verdict/installation 表是 Play Protect 数据库；
- phenotype `config_packages`、`log_sources`、`flag_overrides` 是配置/遥测框架；
- `droid_guard_payload_valuestore.pb` 是 payload 配置存储；
- StreamZ/Clearcut 类框架存在于 GMS，但未形成可证实的 DroidGuard 原始信号
  上传调用链。

上述对象可用于核对 schema，但不等于 DroidGuard 把对应数据上传。

## 5. 静态上传示例

字段级示例见 `protocol.md` §5。示例只包含 flow、版本、ABI 与占位 Build
字段，不包含真实设备值、真实账号、API key、原始 protobuf 或 payload 字节。
