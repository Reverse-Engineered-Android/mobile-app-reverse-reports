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

## 3. native JNI 与固定解释器

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
缓冲共同证明它是服务端程序的动态执行核心，而不是普通业务 helper。本地
Unicorn 对 `0x19308`、`0x1c5e8`、`0x46cb0` 的受控执行分别完成初始化、建立
寄存器帧和解码 `.b`，调用完成且无执行错误；这用于核对静态反汇编恢复的算法，
不等同于在设备上运行 payload 或请求服务端评分。

## 4. 动态 VM、`.b` 解码与混淆分析

### 4.1 程序模型

`hvpj.byteCode` 是签名程序；native 大函数从输入缓冲区读取 opcode，维护栈、
内存和长度计数，并调用固定算术、内存、AES/SHA 原语。核心循环的 switch 分支
覆盖整数运算、位运算、内存读写、调用和跳转；`0x49f58` 附近可见：

- `0x40` 字节 block 缓冲；
- length counter；
- `memcpy`；
- `pthread_once` 初始化；
- SHA 上下文更新/收尾。

因此混淆是四条可分离的数据流：

1. `.b` 缓存的 AES 可变块密码外层；
2. 服务端程序字节码的动态 opcode 与操作数索引；
3. 寄存器值、PC 和字符串缓冲区的 XOR/轮换编码；
4. 每个解释器 handler 内联的 Mixed Boolean-Arithmetic（MBA）操作数变换。

程序字节码必须先通过两层 RSA-SHA256 与 checksum；`.b` 外层使用固定
AES-128，不是第二套独立认证。外层密文、寄存器/PC 编码和 handler 内联 MBA
已分别从 `0x46cb0`、`0x432c4`/`0x177a4` 与解释器 XREF 还原；固定
native/Java 中的密码学实现均已按算法和用途定位。

### 4.2 JNI/输入混淆

`_seigd` 的 Base64 是 Parcel/Bundle 传输编码，不是加密：

- decode → `Bundle`；
- 读取 byte[]、String、Parcelable/FileDescriptor；
- 传给 native session。

这是传输编码，不参与认证或保密。

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

初始抓取时三个 `.b` 文件分别为 63666、87050、64561 字节，逐字节 Shannon 熵
为 7.997305、7.998246、7.997515 bit/byte，且 256 个取值全部出现；zlib、gzip、
bzip2、xz 头/解压探测均失败。2026-10-10 的只读复核显示同一文件名对象已轮换为
64857、85727、63844 字节，证明缓存是可更新的服务端交付物。它们是交付后原样
缓存的高熵程序数据，不是 APK 内未识别的压缩包或固定业务源码。三个 `.d` 文件
两次核对均为 66 字节，属于 `hvpj.h` 辅助字节，不是私钥。固定代码里的 RSA、
SHA 与 AES 已在 §5 按算法、调用点和参数还原；服务端程序交付后的评分阈值仍
属于服务端内容，不能从客户端样本凭空改写。

### 4.4 `.b` 外层解码算法

变体 A 的程序头四个字节是固定 IV。`0x19a70` 初始化 session 寄存器 42，得到的
AES-128 key（小端寄存器值 `0x8685c6899bc75b21`，按 8 字节重复）为：

```text
21 5b c7 9b 89 c6 85 86 21 5b c7 9b 89 c6 85 86
```

在变体 A 中，`0x177a4` 在写寄存器 42 时于 `0x178cc`–`0x1790c` 保存该值；
`0x46cb0` 读取
session 的 buffer/长度/指针/AES schedule 缓存字段，从逻辑偏移读取 IV 和
16 字节块，构造状态、调用 AES 单块加密并 XOR。session 精确布局为：

| 偏移 | 含义 |
|---:|---|
| `+0xc58` | buffer 模式/标志 |
| `+0xc60` | buffer 长度 |
| `+0xc68` | buffer 指针 |
| `+0xc70` | AES key schedule 指针 |
| `+0xca0` | 当前缓存 block index，初值 `-1` |
| `+0xca8`–`+0xcb7` | 当前 AES state |
| `+0xcb8` | 程序头固定的前四个 IV 字节 |

变体 B、D 的 `.b` 解码入口分别为 `0x182bc`、`0x15e0c`，函数内复用上表的
同一组 session 偏移；变体 C 的入口为 `0x48a88`，但其 AES schedule/cache 区
改落在 `+0xc80`、`+0xc88`、`+0xc90`、`+0xea8`、`+0xec0`–`+0xed8`。因此
上表不是四个二进制共享的绝对布局，C 必须按其入口函数的实际 load/store 还原。

对第 `j` 个 block，状态和 keystream 是：

```text
state_j   = raw[0:4] || uint32_le(j) || 00 00 00 00 00 00 00 00
keystream = AES-ECB-128(state_j)
```

解码长度为 `len(raw) - 4`，从第一个 `raw[4:16]` 开始消耗，逐块 XOR：

```text
j = 0: raw[4:16]  XOR keystream[4:16] -> decoded[0:12]
j > 0: raw[16j:16j+16] XOR keystream -> decoded[16j-4:16j+12]
```

末尾只 XOR 实际剩余字节。`+0xcb8` 只在首次 cache miss 读取一次，之后不刷新；
`+0xca0` 保存命中的 block index，后续 miss 从缓存 keystream 取数据。
初始抓取三个样本的正确解码结果如下（不发布原始或解码后的程序字节）：

| 文件前缀 | 原始长度 | 解码长度 | 解码 SHA-256 |
|---|---:|---:|---|
| `0b5272…cdda0.b` | 63666 | 63662 | `f6d2bc80bc4c98ebcc5f5dac558db0de123f2b1e69891755bdb79c588408ae4a` |
| `4d85a8…aeeb7.b` | 87050 | 87046 | `b801bb20e9537aeddccd35a1dac579870733560afe71402f1c76532fe89c0e56` |
| `8f1a78…100e.b` | 64561 | 64557 | `04c2a5de929ecd2a1956fbc20bfedd98899b9e0fd94f0546f8620ca5a3b9e087` |

该变换后的高熵来自后两层 VM 数据流编码，而不是残余 AES 密文：解码结果仍需
按 handler 的操作数公式、寄存器索引和 MBA 语义逐次展开。

2026-10-10 只读复核得到的同一文件名对象快照如下；raw SHA-256 与解码 SHA-256
都发生变化，文件名仍只是缓存键摘要，不是内容不变量：

| 文件前缀 | raw 长度 | raw SHA-256 | 解码长度 | 解码 SHA-256 |
|---|---:|---|---:|---|
| `0b5272…cdda0.b` | 64857 | `3fd499e17b570837420c751611bf8a7dbd1b7d3a3ad3adf77d2a0770f2c3dcf4` | 64853 | `6be6d284aa2dbdb352c3ef299d2816039b172021246f3c4b21b42d76541130fe` |
| `4d85a8…aeeb7.b` | 85727 | `2d7ae94ca6b1c08c21d1bef1ccfaefee529eaebe9046e6d2980a71f47aefeb0d` | 85723 | `787c3a7c9c5f11fbecdfe5eb2521c107678ad8bdca49ebda679379c95069d14e` |
| `8f1a78…100e.b` | 63844 | `b65959d65e3c0b7e146a937086cfa1ddb1769f3961cb648181766e29b24428c6` | 63840 | `1e38e343a748e6c3af3d143adc2367bafd31b2eb693e405a5228b8043d85559a` |

### 4.5 寄存器、PC 与字符串编码

寄存器帧中第 `i` 个寄存器的 type 位于 `frame + (i+1)*16`，value 位于
`frame + (i+1)*16 + 8`。初始化时 root key 的前 8 字节与寄存器 8 的关系是
`rotate_left(root_key[0:8], 8) XOR reg8.value == 0`；报告不发布 root key。
`0x432c4` 从 session `+0xc10` 的 root key、寄存器 8 的编码值以及寄存器
type/rotation 字段计算当前解码偏移，调用 `0x46cb0`，最后把返回值按同一
rotation/XOR 规则写回寄存器 8 作为新的 PC。于是读取不是直接
`pc = *(uint32_t*)pc`，而是：

```text
offset = rotate(root_key, derived(type/rotation)) XOR reg8.value
offset = decode46cb0(session, dst, offset, operand_length)
reg8.value = rotate(root_key, derived(type/rotation)) XOR returned_pc
```

`reg8.value` 的第一轮是上式初始化结果，后续调用保持同构，因此 PC 的推进和
operand 解码互相绑定。字符串/缓冲区使用同样的“寄存器索引 + VM key”导出 XOR
key；搬运缓冲时直接做两次 XOR，避免临时变量暴露明文。handler 读取寄存器索引
后还要执行该 handler 专属的内联 MBA；MBA 只改变索引/操作数，不改变其语义。

对变体 A 的受控 Unicorn 复算还单独验证了 PC 推进：初始化完成后，以逻辑偏移
0、长度 1 调用 `0x432c4`，`0x46cb0` 在首次 cache miss 时从 `raw[0:4]` 载入
IV、返回写入目标的首字节 `0x07`，并把寄存器 8 的新 PC 推进到 5。这个 4→1
的推进差正好对应“4 字节 IV 后才是程序字节”，说明报告给出的 offset、读取长度、
返回 PC 与寄存器编码四者是一套闭合公式，不是只按字节流猜测出的格式。

### 4.6 opcode 分派、JNI 接口与操作数解码

`session+0x1528` 不是自定义 C++ vtable，而是 `0x185a4` 在创建 session 时保存的
Android `JNIEnv*`。`0x186a4` 写入该字段；后续每个间接调用都先从 `[JNIEnv*]`
取函数表，再以固定字节偏移取 JNI 函数。下表中的偏移因此是标准 JNI 函数表偏移，
不是动态程序自带的函数编号。

外层 opcode 统一按 `index = opcode - 0x42`、`0 <= index <= 0x19` 校验。合法项
从有符号 32 位表读取相对位移，`target = adr_base + int32(table[index])`；越界
进入对应错误路径。变体 A 的 11 个主分派点为：

| 分派点 | 表地址 | `adr` base | 作用 | 越界路径 |
|---|---:|---:|---|---|
| `0x329ec` | `0x3ef0` | `0x32a28` | 数组/对象数组分配 | `0x3e0d8` |
| `0x3e4f4` | `0x3db8` | `0x3e508` | 写数组前类型检查 | `0x4044c` |
| `0x3e6b8` | `0x3e88` | `0x3e6cc` | 实例字段写 | `0x41e90` |
| `0x3e77c` | `0x3f98` | `0x3e790` | 读数组前类型检查 | `0x405e8` |
| `0x3f594` | `0x4068` | `0x3f5a8` | 静态字段写 | `0x42e2c` |
| `0x3fbb4` | `0x3c80` | `0x3fbc8` | 实例字段读 | `0x413ec` |
| `0x3ff3c` | `0x3ce8` | `0x3ff54` | 实例方法调用 | `0x426c4` |
| `0x40004` | `0x4110` | `0x4001c` | 静态方法调用 | `0x42754` |
| `0x400a0` | `0x3d50` | `0x400b4` | 静态字段读 | `0x41fd4` |
| `0x4036c` | `0x3e20` | `0x40380` | 数组写 | `0x40c34` |
| `0x4056c` | `0x4000` | `0x40580` | 数组读 | `0x405e8` |

`0x48cdc`、`0x48d54` 虽同样做 `opcode - 0x42`，但位于类型解析/规范化路径，
不是解释循环的主 opcode 表。

另外三个 SO 各自重新生成了相同语义的 11 张表，不能复用 A 的地址。以下为
B、C、D 的精确分派点；每个 role 都是 26 项，全部 target 均在对应 SO 中存在：

**变体 B**

| 作用 | `site` | `table` | `adr` base | 越界路径 |
|---|---:|---:|---:|---:|
| 分配 | `0x314d4` | `0x4010` | `0x31510` | `0x3c82c` |
| 写数组类型检查 | `0x3d000` | `0x4078` | `0x3d014` | `0x3edf8` |
| 实例字段写 | `0x3ccc8` | `0x3dc8` | `0x3ccdc` | `0x405c4` |
| 读数组类型检查 | `0x3cacc` | `0x3e70` | `0x3cae0` | `0x3ebe4` |
| 静态字段写 | `0x3deb4` | `0x3fa8` | `0x3dec8` | `0x415f0` |
| 实例字段读 | `0x3df04` | `0x3cf8` | `0x3df18` | `0x3fc64` |
| 实例方法调用 | `0x3e7fc` | `0x3c90` | `0x3e814` | `0x40c60` |
| 静态方法调用 | `0x3e894` | `0x3d60` | `0x3e8a8` | `0x40f5c` |
| 静态字段读 | `0x3e76c` | `0x3f40` | `0x3e780` | `0x40850` |
| 数组写 | `0x3ed14` | `0x40e0` | `0x3ed28` | `0x3f744` |
| 数组读 | `0x3eb68` | `0x3ed8` | `0x3eb7c` | `0x3f230` |

**变体 C**

| 作用 | `site` | `table` | `adr` base | 越界路径 |
|---|---:|---:|---:|---:|
| 分配 | `0x304c8` | `0x40a0` | `0x30504` | `0x3ba54` |
| 写数组类型检查 | `0x3c36c` | `0x3fd0` | `0x3c380` | `0x3e338` |
| 实例字段写 | `0x3c104` | `0x3ec0` | `0x3c118` | `0x3fc80` |
| 读数组类型检查 | `0x3c2dc` | `0x4108` | `0x3c2f0` | `0x3e130` |
| 静态字段写 | `0x3d3d0` | `0x3d20` | `0x3d3e4` | `0x40d30` |
| 实例字段读 | `0x3d724` | `0x3d88` | `0x3d738` | `0x3f214` |
| 实例方法调用 | `0x3dcb0` | `0x3f68` | `0x3dcc8` | `0x405b4` |
| 静态方法调用 | `0x3ddec` | `0x3df0` | `0x3de04` | `0x4067c` |
| 静态字段读 | `0x3dd54` | `0x3e58` | `0x3dd68` | `0x3fe80` |
| 数组写 | `0x3e24c` | `0x4038` | `0x3e260` | `0x3ead4` |
| 数组读 | `0x3e0ac` | `0x4170` | `0x3e0c0` | `0x3e8e8` |

**变体 D**

| 作用 | `site` | `table` | `adr` base | 越界路径 |
|---|---:|---:|---:|---:|
| 分配 | `0x30d20` | `0x4060` | `0x30d5c` | `0x3d140` |
| 写数组类型检查 | `0x3d468` | `0x3ca0` | `0x3d47c` | `0x3f91c` |
| 实例字段写 | `0x3d72c` | `0x4108` | `0x3d740` | `0x412a8` |
| 读数组类型检查 | `0x3d538` | `0x3f10` | `0x3d54c` | `0x3fab4` |
| 静态字段写 | `0x3eea4` | `0x3ea8` | `0x3eeb8` | `0x4207c` |
| 实例字段读 | `0x3f0ac` | `0x4170` | `0x3f0c0` | `0x40a70` |
| 实例方法调用 | `0x3f5a0` | `0x3e40` | `0x3f5b8` | `0x419e4` |
| 静态方法调用 | `0x3f510` | `0x3d70` | `0x3f528` | `0x4195c` |
| 静态字段读 | `0x3f484` | `0x3dd8` | `0x3f498` | `0x41170` |
| 数组写 | `0x3f834` | `0x3d08` | `0x3f848` | `0x401ac` |
| 数组读 | `0x3fa38` | `0x3f78` | `0x3fa4c` | `0x403e4` |

四份的 JNI slot 角色一致：分配为 `New*Array`，字段读写为 `Get/Set*Field`，
方法为 `Call*MethodA`/`CallStatic*MethodA`，数组读写为
`Get*ArrayRegion`/`Set*ArrayRegion`。两个类型检查表先经共同的
`IsInstanceOf`/`GetObjectClass` 门，再分别进入数组写或数组读公共块；role
不是按地址顺序猜测，而是由表目标落到的实际数组写/读公共分派点确认。

各表的受支持类型与精确 JNI slot 如下；`0x4c` 与 `0x5b` 均走对象引用路径。

| 类型 | opcode | 分配 | 实例字段读/写 | 静态字段读/写 | 实例调用 | 静态调用 |
|---|---|---|---|---|---|---|
| byte | `0x42` | `1408 NewByteArray` | `776/848` | `1176/1248` | `336 CallByteMethodA` | `976 CallStaticByteMethodA` |
| char | `0x43` | `1416 NewCharArray` | `784/856` | `1184/1256` | `360 CallCharMethodA` | `1000 CallStaticCharMethodA` |
| double | `0x44` | `1456 NewDoubleArray` | `824/896` | `1224/1296` | `480 CallDoubleMethodA` | `1120 CallStaticDoubleMethodA` |
| float | `0x46` | `1448 NewFloatArray` | `816/888` | `1216/1288` | `456 CallFloatMethodA` | `1096 CallStaticFloatMethodA` |
| int | `0x49` | `1432 NewIntArray` | `800/872` | `1200/1272` | `408 CallIntMethodA` | `1048 CallStaticIntMethodA` |
| long | `0x4a` | `1440 NewLongArray` | `808/880` | `1208/1280` | `432 CallLongMethodA` | `1072 CallStaticLongMethodA` |
| object/reference | `0x4c`,`0x5b` | `48 FindClass` + `1376 NewObjectArray` | `760/832` | `1160/1232` | `288 CallObjectMethodA` | `928 CallStaticObjectMethodA` |
| short | `0x53` | `1424 NewShortArray` | `792/864` | `1192/1264` | `384 CallShortMethodA` | `1024 CallStaticShortMethodA` |
| void | `0x56` | 不支持 | 不支持 | 不支持 | `504 CallVoidMethodA` | `1144 CallStaticVoidMethodA` |
| boolean | `0x5a` | `1400 NewBooleanArray` | `768/840` | `1168/1240` | `312 CallBooleanMethodA` | `952 CallStaticBooleanMethodA` |

对象数组的公共尾部是 `0x3eb64`–`0x3ebf8`：`FindClass` 得到元素类，在 `0x3ebf4`
调用 `1376 NewObjectArray`，随后 `184 DeleteLocalRef` 清理临时类引用。数组读
先在对应 handler 调 `1368 GetArrayLength` 并做边界检查，再调 `GetBoolean/Byte/
Char/Short/Int/Long/Float/DoubleArrayRegion` 的 `1592/1600/1608/1616/1624/1632/
1640/1648`；数组写对应 `1656/1664/1672/1680/1688/1696/1704/1712`。对象数组读
和写分别直接使用 `1384 GetObjectArrayElement` 与 `1392 SetObjectArrayElement`。

`0x3e4f4`、`0x3e77c` 在真正读写数组前按 opcode 选择缓存的类型描述对象，加载
`x2=[x8]` 后调用 `0x447bc`。`0x447bc` 的 `0x447ec`–`0x447f0` 调用 JNI `256
IsInstanceOf`；失败进入 `0x17380` 的类型/运行时错误路径。对象类型还会先经过
`248 GetObjectClass`。因此这些表是动态程序的运行时类型门，不是另一套业务 opcode。

`fcn.0004563c` 是独立的操作数/值类型解码器。它同样要求 `opcode-0x42 <= 0x19`，
但从表 `0x41b8` 读取 16 位相对量，按 `target = 0x456c8 + 4*uint16` 跳转，共
26 项。明确支持的值类型是：`0x42` byte、`0x43`/`0x53` 16-bit、`0x44` double、
`0x46` float、`0x49` int、`0x4a`/`0x4c`/`0x5b` 64-bit/object、`0x56` no-op、
`0x5a` boolean；其他项在 `0x45870` 汇合。每个解码 handler 都执行：

```text
control = session[+0xc18]
root    = session[+0xc10]
rot     = -(((control & 0x10) | reg_index)
            & ((~control) | 0x3e)) & 0x3f
value   = ror64(root, rot) XOR encoded_value
```

`ror(root, -8 & 63)` 等价于 `rotl(root, 8)`，该等价关系已数值核对。`0x45870`
用 XOR/rotate key `0xc3f77cf110beca42` 构造 28 字节错误文本，独立解码为
`Conversion not supported: `。至此，动态程序的外层 opcode、JNI 操作、类型检查、
值类型、寄存器/PC 解码和不支持分支均已给出固定地址、公式或精确 JNI slot。

### 4.7 逐程序语义与执行轨迹边界

固定解释器与某个缓存程序不是同一个分析对象。本样本已经对 A 完成
`0x432c4 → 0x46cb0` 的连续取流：共返回 63658 个解码字节，SHA-256 为
`63ded1fab3a6c2616d34967e6d785dbe85bba182962f5681527cfd547cb935b5`，
完整返回且没有错误。这排除了“外层函数只能解一个块”或“函数返回即失败”的
可能性，但输出仍是覆盖全部 256 字节值的高熵流，不能据此写出程序控制流。

对 `entryHelperNative=0x1462c`、`ssNative=0x14280` 和
`xssNative=0x14368` 的受控 Unicorn 追踪均以 `0xdead` 正常返回，输出也一致为
49 字节；但是它们只进入 `0x1c5e8` 的初始化/建帧路径，`0x4563c` 的命中数为 0，
没有产生解释循环级 opcode、PC、跳转目标、JNI 调用参数或 handler 结果轨迹。
三个 `.b` 样本的初始抓取追踪同样只读取头部字节，未命中 `0x4563c`。

因此本报告可以最终断言的是：

1. 三个 `.b` 的外层 AES 解码算法、长度和摘要已经恢复；
2. 固定 native 解释器的 opcode 校验、11×26 分派表、JNI slot、类型门、
   操作数解码器、寄存器/PC/字符串编码和已识别的密码学用途已经恢复；
3. 尚未恢复这三个缓存程序各自的完整 instruction-level trace、逐 handler
   语义链和业务可读控制流；
4. 未闭合第 3 项时，不能声称评分阈值、规则优先级、分支含义或“`.b` 内程序
   已完全反混淆”，报告不以固定解释器反编译替代这些证据。

这一边界只否定对逐 `.b` 程序的完成度，不改变前面章节已经定位的网络、认证、
固定采集、固定解码、固定分派和固定密码学代码。

## 5. 密码学精确位置

### 5.1 RSA-SHA256

| 位置 | 用途 |
|---|---|
| `bxky.java:23` | 创建响应验签公钥 |
| `bxky.java:28-46` | `SHA256withRSA.verify(hvoa.c, hvoa.d)` |
| `DroidGuard.java:93` | 两枚 bytecode 验签公钥 |
| `DroidGuard.java:107-152` | RSA KeyFactory + `SHA256withRSA` |

三处 payload/响应公钥均为 294 字节 RSA-2048 SubjectPublicKeyInfo；签名算法是
`SHA256withRSA`，不是 APK 证书的 `MD5withRSA`。

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

HWCAP 检测从 `0x49a14` 读取 `AT_HWCAP` 并把 AES 可用性放在状态 bit 2。
实际 aarch64 手机设备带 `aes` flag 时，`.b` 解码走 key schedule `0x6f20` 和
单块加密 `0x71a0`；没有 AES 硬件时走可移植 fallback：key schedule `0xa3d0`、
单块加密 `0x9c20`。两套路径均以标准 AES-ECB 结果核对，错配调用只是路径
不匹配，不是另一种密码算法。

`0x6f20` 的输入长度检查接受 `0x80`–`0x100` bits，并要求 64-bit 对齐；随后
执行标准 AES key schedule。`0x71a0` 使用 expanded round keys 做单块加密，
`0x7660` 做多块路径。该用途由核心函数 XREF 与上下文长度缓冲共同确定：
它是 `.b` 外层与动态程序共用的对称密码原语，不是独立未知后门。

具体分支在 `fcn.00017cd0`：`0x17e10` 把 `sp+8` 放入 `x0`、把 `0x80`
（128-bit）放入 `w1`、把输出上下文 `x20` 放入 `x2`，然后在 `0x17e1c` 调
`0xa390`；在另一分支 `0x17e2c`–`0x17e38` 以同样参数调 `0x6ee0`。`0xa390`
把 `w1 >> 5 + 5` 写入状态偏移 `0xf0` 并进入参数初始化；`0x6ee0` 在
`0x6eec`–`0x6f00` 验证长度属于 `{0x80,0xc0,0x100}` 且 64-bit 对齐，
随后在 `0x6f30`–`0x6fd4` 用 `aese`、轮常量和 key-schedule 循环展开轮密钥，
并把轮数写入输出状态。`0x17e3c` 再检查返回值，失败进入 `abort`。因此该处
是 AES-128/192/256 族 key schedule 的精确调用，不是未标注的自定义加密。

### 5.5 HMAC-SHA256

固定 native 代码还包含标准 HMAC-SHA256，用于 VM 数据/寄存器完整性，不属于
AES 或单纯 SHA-256 调用。变体 A 的完整 HMAC 入口是 `0x434c4`：

| 证据 | 精确位置 |
|---|---:|
| HMAC 入口与寄存器参数校验 | `0x434c4`–`0x43588` |
| key/hash 输入读取与寄存器解码 | `0x43590`–`0x436d0` |
| inner digest 写回与清理 | `0x43d4c`–`0x43ea8` |
| inner key pad（逐字节/8 字节/16 字节 XOR `0x36`） | `0x43fc0`–`0x44070` |
| outer key pad（逐字节/8 字节/16 字节 XOR `0x5c`） | `0x440d8`–`0x44174` |
| SHA-256 初始化、更新、收尾与 32 字节输出 | `0x44074`–`0x4423c` |
| 调用点 1/2 | `0x1cc80`、`0x1d1f8` |
| 调用点 3/4/5 | `0x2d4dc`、`0x3a4b8`、`0x3bba0` |

入口先按 `w1` 指定的寄存器读取 type/value，再解码 key 与 message；`0x43fc0`
和 `0x440d8` 分别生成 `key XOR 0x36…`、`key XOR 0x5c…`，随后两次进入
SHA-256 上下文，输出 32 字节。这个 `ipad/opad + SHA-256` 结构唯一确定为
HMAC-SHA256；它由 5 个 VM 路径调用，所以密码学覆盖必须把它与 `.b` 的 AES、
响应的 RSA-SHA256、完整性校验的 SHA-256 分开列示。四份 SO 均包含同一组
CRYPTOGAMS SHA-256 原语和 AES 指令；A 的地址是本节基准，其他变体按各自
重排后的函数体核对，不跨 SO 复用绝对地址。

## 6. 风控判定依据

客户端代码可证明的判定输入包括：

1. flow 和运行环境；
2. Build 指纹；
3. bytecode/VM 的签名、checksum、有效期；
4. GPU、传感器、触摸与 Bundle/Map；
5. native session 中的动态程序执行结果。

客户端可执行的固定判定链、输入、解码和认证门均给出精确地址与公式；三个缓存
程序的逐指令语义仍是 §4.7 所列边界。服务端返回什么阈值、如何把结果转成
拒绝/挑战/放行，以及处罚策略，不在客户端固定代码中；本报告不编造阈值。
