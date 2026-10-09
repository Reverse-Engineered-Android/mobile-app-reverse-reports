# 风控、native 与密码学

## 1. 风控总链

```text
调用方 flow/Bundle/Map
        │
        ├─ GMS: Build + os.arch + flow ── POST create ── Google
        │                                      │
        │                              signed hvoa/hvpj
        │                                      │
        └──────── bytecode + VM URL ───────────┘
                     │
              RSA + checksum + HTTPS allowlist
                     │
          payload JVM 采集环境/事件
                     │
        initNative → ssNative/xssNative/heNative
                     │
           native dynamic VM 风控程序
                     │
              result byte[] → Binder caller

        └─ bxlr/bxmh operation counters ── Clearcut/StreamZ
```

## 2. Java 层具体机制

### 2.1 flow 与请求

- `bxmu.b` 将 `fast`/`full` 识别为特定请求分支。
- `bxjs.debug.java:49-98` 缺失缓存时构造 flow、附加 byte[]、布尔标志并
  调用创建链。
- `hvnz.c.c` 保存 `fast`、`full`、`msa-l` 等 flow 名。
- `DroidGuardResultsRequest` 携带 Bundle extras、ParcelFileDescriptor 和
  调用方请求上下文。

### 2.2 payload 执行

- `DroidGuard.java:400-568` 的 `run/init/ss` 建立 session。
- `ss(Map)` 删除 `_seigd`，将 Base64 Parcel/Bundle 解为 native 输入。
- `xssNative(..., Map, Map)` 接收额外 Map。
- `heNative(..., Map)` 接收事件。
- `lcsNative` 固定返回 0。
- `closeNative` 结束 session。

### 2.3 环境采集

- Build 字段在 GMS 创建请求中上传。
- payload 读取 GPU renderer/fingerprint，并执行 32×32 `glReadPixels`。
- `events/*.java` 收集触摸、传感器、方向事件。
- `droidguasso/h.java` 读取本地文件前 1024 字节并计算摘要。
- `droidguasso/c.java` 对 ByteBuffer 计算 MessageDigest 摘要。

## 3. native JNI 闭包

从 `JNI_OnLoad/RegisterNatives` 静态恢复的类
`com/google/ccc/abuse/droidguard/DroidGuard`：

| 方法 | 地址 | 行为 |
|---|---:|---|
| `initNative` | `0x135a0` | 校验参数，读 byte[]/String，构造 session，调用初始化族并返回句柄 |
| `ssNative` | `0x14280` | 把 flow/Bundle 转为 native 执行输入 |
| `xssNative` | `0x14368` | 把额外 Map 加入执行上下文 |
| `heNative` | `0x14464` | 事件注入 |
| `lcsNative` | `0x144e8` | `mov w0,wzr; ret`，固定返回 0 |
| `entryHelperNative` | `0x1462c` | 进入/帮助执行入口 |
| `closeNative` | `0x14660` | 结束 session |

`initNative` 的关键调用：

| 地址 | 作用 |
|---:|---|
| `0x185a4` | 创建 native session/state |
| `0x19308` | 初始化输入/资源 |
| `0x1c5e8` | 初始化动态执行上下文 |
| `0x17b50` | 按环境分支写状态 |
| `0x448e0` | 建立大执行上下文 |
| `0x48fc8` | 把 JNI/Bundle/Map 转成内部字符串或字节流 |
| `0x48e8c` | 执行/结果桥接 |

`0x4563c` 是核心大函数：起始 `0x4563c`，规模约 217088 字节，约 7150 basic
blocks、42314 条指令、递归/高复杂度。其栈式解释循环、AES/SHA XREF 和长度
缓冲共同证明它是服务端程序的动态执行核心，而不是普通业务 helper。

## 4. 动态 VM 与混淆闭包

### 4.1 程序模型

`hvpj.byteCode` 是签名程序；native 大函数从输入缓冲区读取 opcode，维护栈、
内存和长度计数，并调用固定算术、内存、AES/SHA 原语。核心循环的 switch 分支
覆盖整数运算、位运算、内存读写、调用和跳转；`0x49f58` 附近可见：

- `0x40` 字节 block 缓冲；
- length counter；
- `memcpy`；
- `pthread_once` 初始化；
- SHA 上下文更新/收尾。

因此“混淆”不是未识别的自定义加密壳，而是两层：

1. 服务端程序字节码的动态 opcode；
2. native 固定解释器调用标准密码学原语。

程序字节码必须先通过两层 RSA-SHA256 与 checksum；当前样本没有把未验签
bytecode 当作可信代码。固定 native/Java 中的密码学实现均已按算法和用途定位。

### 4.2 JNI/输入混淆

`_seigd` 的 Base64 是 Parcel/Bundle 传输编码，不是加密：

- decode → `Bundle`；
- 读取 byte[]、String、Parcelable/FileDescriptor；
- 传给 native session。

报告没有把它写成未知加密。

### 4.3 `app_dgp` 程序缓存

固定缓存链由 `bxkw.java:27,56-79,121-162,230-292` 组成：

- `Context.getDir("dgp", 0)` 是本地缓存目录；
- `main.a` 保存 `flow + "/" + Build.FINGERPRINT`，查询时必须同时命中该键、
  创建时间和过期时间；
- `main.b` 是创建秒数，`main.h` 是过期秒数，`main.d` 是 VM/程序版本；
- `.b` 文件写入 `bxku.a`（程序字节），`.d` 写入 `bxku.c`（辅助字节）；
- 文件名是 `hypq.a.d(main.a).toString() + ".b"/".d"`，即对完整缓存键做摘要，
  而不是以 flow 或设备值直接命名；
- 过期清理同时删除 SQLite 行和两个文件。

设备只读核对得到的 schema 为
`main(a TEXT UNIQUE NOT NULL, b LONG NOT NULL, h LONG NOT NULL, d TEXT NOT NULL)`
及 `expiration_idx(h)`。三条脱敏记录分别为 `fast`、`pia_express`、
`ad_attest`；键值长度为 80、87、85 字节，VM 版本一致。报告不复制 `main.a`
中的真实 `Build.FINGERPRINT`。

三个 `.b` 文件分别为 63666、87050、64561 字节，逐字节 Shannon 熵为
7.997305、7.998246、7.997515 bit/byte，且 256 个取值全部出现；zlib、gzip、
bzip2、xz 头/解压探测均失败。它们是服务端交付后原样缓存的高熵程序数据，
不是 APK 内未识别的固定加密代码。三个 `.d` 文件均为 66 字节，属于 `hvpj.h`
辅助字节，不是私钥。固定代码里的 RSA、SHA 与 AES 已在 §5 按算法、调用点和
参数闭包；动态程序的评分语义属于服务端数据，不能从客户端样本凭空改写。

## 5. 密码学精确位置

### 5.1 RSA-SHA256

| 位置 | 用途 |
|---|---|
| `bxky.java:23` | 创建响应验签公钥 |
| `bxky.java:28-46` | `SHA256withRSA.verify(hvoa.c, hvoa.d)` |
| `DroidGuard.java:93` | 两枚 bytecode 验签公钥 |
| `DroidGuard.java:107-152` | RSA KeyFactory + `SHA256withRSA` |

### 5.2 SHA-1

| 证据 | 地址 |
|---|---:|
| `.rodata` SHA-1 constants | `0x1fc0` |
| SHA-1 initial state | `0x3aa0` |
| scalar 尾部/实现区 | `0x8800`–`0x8acc` |
| ARMv8 `sha1h/sha1c/sha1p/sha1m/sha1su0/sha1su1` | `0x8900` 起 |
| core XREF | `0x359b4`、`0x35a24` |

### 5.3 SHA-256

| 证据 | 地址 |
|---|---:|
| `.rodata` SHA-256 constants | `0x2080` |
| SHA-256 initial state | `0x3c50` |
| scalar | `0x8b00`–`0x9928` |
| ASIMD `sha256h/sha256h2/sha256su0/sha256su1` | `0x9940`–`0x9b30` |
| core XREF | `0x49e20`、`0x4a008`、`0x4a074` |
| `.rodata` CRYPTOGAMS 标识 | `0x2184` |

### 5.4 AES

| 证据 | 地址 |
|---|---:|
| `.rodata` ARMv8 AES 标识 | `0x24ee` |
| key expansion / schedule | `0x6f20` |
| inverse schedule/mix | `0x7120` |
| single-block encrypt | `0x71a0` |
| multi-block path | `0x7660` |
| `aese/aesd/aesmc/aesimc` 指令区 | `0x6f7c`–`0x78c4` |
| `0x17cd0` 内的 128-bit key-schedule 分支 | `0x17e10`–`0x17e38` |
| core schedule XREF | `0x31674`、`0x35994` |
| core block XREF | `0x359b4`、`0x35a24`、`0x49870`、`0x49930` |

`0x6f20` 的输入长度检查接受 `0x80`–`0x100` bits，并要求 64-bit 对齐；随后
执行标准 AES key schedule。`0x71a0` 使用 expanded round keys 做单块加密，
`0x7660` 做多块路径。该用途由核心函数 XREF 与上下文长度缓冲共同确定：
它是动态程序的对称密码原语，不是独立未知后门。

具体分支在 `fcn.00017cd0`：`0x17e10` 把 `sp+8` 放入 `x0`、把 `0x80`
（128-bit）放入 `w1`、把输出上下文 `x20` 放入 `x2`，然后在 `0x17e1c` 调
`0xa390`；在另一分支 `0x17e2c`–`0x17e38` 以同样参数调 `0x6ee0`。`0xa390`
把 `w1 >> 5 + 5` 写入状态偏移 `0xf0` 并进入参数初始化；`0x6ee0` 在
`0x6eec`–`0x6f00` 验证长度属于 `{0x80,0xc0,0x100}` 且 64-bit 对齐，
随后在 `0x6f30`–`0x6fd4` 用 `aese`、轮常量和 key-schedule 循环展开轮密钥，
并把轮数写入输出状态。`0x17e3c` 再检查返回值，失败进入 `abort`。因此该处
是 AES-128/192/256 族 key schedule 的精确调用，不是未标注的自定义加密。

## 6. 风控判定依据

客户端代码可证明的判定输入包括：

1. flow 和运行环境；
2. Build 指纹；
3. bytecode/VM 的签名、checksum、有效期；
4. GPU、传感器、触摸与 Bundle/Map；
5. native session 中的动态程序执行结果。

服务端返回什么阈值、如何把结果转成拒绝/挑战/放行，以及处罚策略，不在客户端
固定代码中；本报告不编造阈值。
