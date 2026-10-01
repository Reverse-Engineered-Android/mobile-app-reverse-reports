# `libtinyd.so` 伴随守护进程深挖

本文件关闭 [risk-controls.md](risk-controls.md) §10 遗留的"IPC 协议未还原"，并把该组件从**结构推测**升级为**已恢复**。

- 样本：`libs/lib/arm64-v8a/libtinyd.so`
- 大小：133 816 B
- SHA-256：`ea32c231da936398b06fa42b1a2726e195ee5fe36bf799a0a4d42ff9c66ac618`
- MD5：`6309f7da9ed2e0481cb3e14c861dfc64`

结论先行：**它不是网络组件，也不是加密组件；它是一条"fork 子进程 + 管道上报 + 崩溃兜底"的伴随守护链，配合一套自研 XOR 字符串加密，且该加密已完整还原为闭式。**

---

## 1. 结构事实

### 1.1 段布局

| 段 | 地址 | 大小 |
| --- | --- | --- |
| `.rodata` | `0x33b0` | `0x9f8` |
| `.text` | `0x5324` | `0x19b68` |
| `.plt` | `0x1ee90` | `0x250` |
| `.data.rel.ro` | `0x230e0` | `0x58` |
| `.fini_array` | `0x23138` | `0x10` |
| `.data` | `0x23150` | `0xc90` |
| `.init_array` | `0x23de0` | `0x78` |
| `.got` | `0x24028` | `0x28` |
| `.bss` | `0x24180` | `0x4b8` |

FDE 数量 **127**；全部位于 `.text` 内。

### 1.2 导出面：只有 `JNI_OnLoad`

```
导出符号： ['JNI_OnLoad']          # 唯一
Java_* 导出： []                   # 零个
dynsym 条目：40
```

`JNI_OnLoad` @ `0xa630`。

**没有 `Java_*` 导出，且在 `.data.rel.ro` / `.data` 中扫不到任何 `{name, signature, fnPtr}` 形态的 `JNINativeMethod` 三元组**：

```
.data.rel.ro 的 5 个重定位槽：
  0x230e0 -> 0x230e0     (自引用，容器头)
  0x230f8 -> 0x195f0
  0x23110 -> 0x19040
  0x23118 -> 0x192c8
  0x23130 -> 0x194b8
```

这 4 个函数指针（`0x195f0`、`0x19040`、`0x192c8`、`0x194b8`）都以 `sub sp, sp, #…` + `stp x29,x30` 开头，是**普通内部过程**，且下游表项只有 5 个 → **不是 4 方法注册表**（`RegisterNatives` 表至少需要 `3×方法数` 项）。注册表在 `JNI_OnLoad`（CFF）内**动态构造**，与 `libtiny.so` 同款做法。

### 1.3 `.init_array` 全零：没有构造函数

```
.init_array @ 0x23de0，120 B = 15 项，全部为 0x0000000000000000
.fini_array @ 0x23138，16 B  =  2 项，全部为 0
```

→ 该库**不在加载时自动启动守护进程**。所有动作必须由 `JNI_OnLoad` 或 Java 侧显式调用触发。这一点修正了"加载即驻留"的直觉读法。

### 1.4 导入面：38 个，无网络、无 `dlopen`、无 `system`

```
__cxa_atexit  __cxa_finalize  __errno  pthread_create  __read_chk  fork  close
write  abort  getprogname  fdopen  feof  fgets  fclose  environ  malloc
__stack_chk_fail  free  __sF  pthread_cond_broadcast  pthread_cond_wait
pthread_mutex_lock  pthread_mutex_unlock  syscall  android_set_abort_message
closelog  fputc  openlog  syslog  vasprintf  vfprintf  __cxa_thread_atexit_impl
pthread_key_create  pthread_setspecific  pthread_getspecific  pthread_key_delete
pthread_once  realloc
```

危险导入扫描：

```
socket/connect/send/recv/dlopen/system/popen/exec*/curl/SSL/inet/bind/listen
→ 命中 0 个
```

**该库不具备网络能力**。它的一切对外通信必须经由父进程的 fd 或继承的 Binder/JNI 通道。

---

## 2. 字符串加密：完整还原（闭式）

### 2.1 调用惯用式

全库只有 **7 处**字符串构造点，统一形态（以 `0x18e48` 为例）：

```asm
adrp x8, 0x3000 ; add x8, x8, #0xc24     ; x8 = 密文地址（在 .rodata）
ldr  x10, [x8] ; ldr w8, [x8,#8]         ; 取 12 字节密文
str  x10, [sp,#8] ; str w8, [sp,#16]
mov  w0, #LEN+1                          ; 分配 LEN+1
bl   0x5af4                              ; 分配器
mov  w2, #LEN                            ; memcpy 长度 = 明文长度
strb wzr, [x0, #LEN]                     ; 置 NUL
bl   0x1c904                             ; memcpy(out, sp+ofs, LEN)
mov  w1, #LEN                            ; ← 传的是明文长度，不是 LEN-1
bl   <decoder>                           ; 原地解密(out, LEN)
```

参数约定：`x0 = 缓冲区`，`x1 = 明文长度`。

**密文来源有两种**，两者都必须按 `LEN` 截取：

| 来源 | 形态 | 实例 |
| --- | --- | --- |
| `.rodata` blob | `adrp/add` 取地址后 `ldr` 装载 | `0x3be8`、`0x3c24`、`0x3b00`、`0x3cdc`、`0x3cfc` |
| **代码立即数** | `mov w9, #imm` + `str x9, [sp]` | `0x1a05c` 处 `mov w9, #0xb9bd` → 字节 `bd b9 00`，取 2 字节 |

第二条是本次新增的关键发现：**部分字符串不以数据形式存在，而是直接编码在指令立即数里**。这也解释了为什么按"密文地址"穷举会漏掉 `am`。

### 2.2 逐位置替换表（非纯 XOR）

**不要把这里的算法与 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md) §4.3 的 XOR `0x70` 混为一谈**——后者属于 `libxyasf.so`，证据是该库全库仅一条 `movi v0.16b, #0x70`（`0x1010c`）；本节属于 `libtinyd.so`，是**另一套完全不同的构造**。

在 `libtinyd.so` 上，一个很自然的错误假设是"纯 XOR 流"：因为输出里 `i%5 ∈ {3,4}` 的字节恒为 0，容易误判成"密钥流在固定位置取 0"。该假设**已被否证**（实测非线性，见下），旧工作记录中据此产生的 `p6ro` 等结论也已撤回（§2.6）。

正确算法是**按位置索引的字节置换**：每个位置 `i` 用 `i % 20` 查一张 20 项的调度表，每项是 `(旋转位数 r, 运算, 常量 c)`：

```c
void decode(uint8_t *buf, size_t len) {
    for (size_t i = 0; i < len; i++) {
        uint8_t v = ROL8(buf[i], SCHED[i % 20].rot);      /* 循环左移 */
        buf[i] = SCHED[i % 20].is_add ? (uint8_t)(v + SCHED[i % 20].k)
                                      : (uint8_t)(v ^ SCHED[i % 20].k);
    }
}
```

`ROL8` 是 8 位循环左移。**算法对输入非线性**：实测 200 次单字节翻转，输出只有同一位置变化（无扩散），但 `f(a) ≠ f(b)` 的仿射关系不成立——即它是"位置相关的逐字节双射"，不是 XOR 流。

### 2.3 四个解码器与完整调度表

全库共 **4 个**解码器（不是 3 个；旧记录遗漏了 `0x70c8`）：

| 位置 | `rot` / 运算 / `c`（按 `i % 20 = 0..19`） |
| --- | --- |
| `0x7b8c` | `R0^78 R0^cd R0+fd R5^00 R2^00 R0^32 R0^fc R0+f0 R6^00 R2^00 R0^03 R0^ef R0+88 R6^00 R4^00 R0^10 R0^87 R0+ce R4^00 R3^00` |
| `0x14fa0` | `R0^dc R0^d4 R0+fe R5^00 R4^00 R0^2b R0^fd R0+f0 R4^00 R2^00 R0^02 R0^ef R0+24 R6^00 R3^00 R0^10 R0^23 R0+d5 R5^00 R3^00` |
| `0x1b5e4` | `R0^c6 R0^4a R0+fd R5^00 R3^00 R0^b5 R0^fc R0+f0 R5^00 R7^00 R0^03 R0^ef R0+3a R1^00 R4^00 R0^10 R0^39 R0+4b R4^00 R3^00` |
| `0x70c8` | `R0^8e R0^57 R0+ff R5^00 R3^00 R0^a8 R0^fe R0+f0 R5^00 R1^00 R0^01 R0^ef R0+72 R7^00 R2^00 R0^10 R0^71 R0+58 R6^00 R3^00` |

`R n` 表示 `ROL8(v, n)`；`^cc` 为 XOR，`+cc` 为模 256 加。**每个位置 `i%5 ∈ {3,4}` 的项都是纯旋转（常量 0）**，位置 `i%5 ∈ {0,1}` 是"旋转 0 + XOR"，位置 `i%5 = 2` 是"旋转 0 + 加"。

调度表以 `i % 20` 为周期：实测 `len = 256` 时 `SCHED[i] == SCHED[i+20]` 对全部 `i` 成立。

### 2.4 验证

- **逐位置全 256 值穷举**：对 `i = 0..47` 的每个位置，把输入该字节取遍 `0x00`–`0xFF`（其余为 0），记录输出该字节，得到 256 项真值表；再用上表闭式重算，**48/48 个位置与真值表逐项全等**。
- **周期自洽**：`i % 20` 周期在 `len = 256` 上验证通过。
- **无扩散**：单字节翻转只影响同一位置（200 次测试 0 例外），确认是逐字节双射而非分组密码。
- **端到端**：用闭式解出全部 7 条明文（§2.5），与模拟器实跑结果逐字节一致。

### 2.5 明文清单：全部 7 条

| 来源 | 解码器 | 长度 | 明文 |
| --- | --- | --- | --- |
| `.rodata` `0x3be8` | `0x7b8c` | 16 | `LD_LIBRARY_PATH=` |
| `.rodata` `0x3c24` | `0x7b8c` | 6 | `State:` |
| `.rodata` `0x3c6c` | `0x7b8c` | 6 | `zygote` |
| `.rodata` `0x3b00` | `0x70c8` | 10 | `TracerPid:` |
| 立即数 `0x00b9bd`（`0x1a05c`） | `0x14fa0` | 2 | `am` |
| `.rodata` `0x3cdc` | `0x1b5e4` | 8 | `setArgV0` |
| `.rodata` `0x3cfc` | `0x1b5e4` | 18 | `android/os/Process` |

### 2.6 对旧稿的显式纠正

**(a) 撤回 `p6ro`。** 早期记录中出现的"明文 `p6ro`"是**长度参数错配**的产物（误用长度 4，实际调用点 `0x1a05c` 给出 `mov w2,#2` / `mov w1,#2`）。正确结果是 `am`，即 Android **Activity Manager** 命令名。

**(b) 撤回"乱码残留"。** `0x3c30`、`0x3c78`、`0x3c8c` 在旧记录中留下的 `85qo6j$d`、`` `TYLj|ta/ ``、`s4Jjakc/lXlg` **不是字符串**：它们落在 `0x3c10`–`0x3cdc` 段内**密文数据的中间偏移**，没有对应调用点。全库密文起点只有 7 个（§2.5），加上立即数 1 个。

**(c) 撤回"纯 XOR 密钥流"的读取。** 旧工作记录把 `i%5 ∈ {3,4}` 位置恒零的现象读作 "XOR 密钥流在该位取 0"，并据此用 `密文 ^ 密钥流` 反推明文，产生了 `p6ro` 之外的一批乱码。正确读法见 §2.2：算法是**位置相关的旋转 + 模加/XOR 双射**，不可用固定密钥流反推。

### 2.7 穷举阴性结论

对 `.rodata` 全域 `0x33b0`–`0x3da8`、按纯 XOR 假设逐起点穷举长度 3–48，得到 44 个"全可打印"结果，**全部是长度 3 的偶然碰撞**（如 `0x3af3` → `&Wf`），无一对应调用点。

按 §2.3 的正确调度表复跑，同样不能从非调用点起点产生有意义的明文。

→ **`libtinyd.so` 不存在未还原的字符串加密。**（`libtiny.so` 侧同款结论见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md)。）

## 3. IPC 协议：管道 + 4 字节定长信令

### 3.1 通道建立

`IPC` 通道由**内联系统调用**建立，不经过 libc 包装：

| 位置 | syscall | 语义 | 参数 |
| --- | --- | --- | --- |
| `0xfb00`/`0xfb04` | `59` = **`pipe2`** | 建管道 | `x0 = &fds[2]`，`x1 = 0`（**无 `O_CLOEXEC`**） |
| `0x1a2d0`/`0x1a2d4` | `260` = **`wait4`** | 回收子进程 | `x0 = pid`，`x1 = &status(1 B)`，`x2 = 1`，`x3 = 0` |
| `0x19940`/`0x19944` | `56` = `openat` | `AT_FDCWD`(`-100`) | 打开 `/proc/<pid>/status` |
| `0x19940` 之后 `0x19980` | `fdopen@plt` | 包装为 `FILE*` | 模式串 `"r"`（位于 `.rodata` `0x38f9`，**明文字面量，未加密**） |
| `0x19944` | `80` = `fstat` | 取 fd 状态 | — |
| `0x19fcc`/`0x19fd0` | `57` = `close` | 关闭 | — |
| `0x9fc4`/`0x9fc8` | `56` = `openat` | 通用文件打开 | — |
| `0xa204`/`0xa208` | `57` = `close` | 通用关闭 | — |

管道 fd 的落点（均在同一个 `x19` 上下文结构上）：

| 偏移 | 写入点 | 值 |
| --- | --- | --- |
| `[x19+0x18c]` (396) | `0xefe8` | `pipe2` 结果的**存储 fd**（取 `&fds[1]`） |
| `[x19+0x190]` (400) | `0xefec` | 指向 `x19+0x4cc` 的**固定缓冲指针** |
| `[x19+0x19c]` (412) | `0x133a8` | 从 `[x19+400]` 解引用的 fd（**读 fd**） |
| `[x19+0x1d0]` (464) | `0x13768` | `&x19[0x494]`，即**待发消息缓冲** |

→ **存储 fd 与读 fd 是同一对 pipe 的两端，由 `fork` 的两侧各自持有。** 这是标准的"父读子写"（或反向）单工管道。

### 3.2 信令格式：定长 4 字节

唯一写入形态（`0x12380`、`0x12f48`、`0xe104`）完全一致：

```asm
ldr  w0, [x19, #412]      ; fd  = 读/写端
ldr  x1, [x19, #464]      ; buf = &x19[0x494]
mov  w2, #4               ; len = 4                 ← 定长 4 字节
bl   write@plt
ldr  w0, [x19, #412]
bl   close@plt
```

唯一读取形态（`0x13624`–`0x13644`）：

```asm
ldr  x8,  [x19, #520]     ; buf 基址
ldr  x9,  [x19, #528]     ; 已读字节数
ldr  x10, [x19, #528]     ; 已读字节数（再次）
ldr  w0,  [x19, #396]     ; fd
mov  w11, #4
add  x1, x8, x9           ; buf + 已读
sub  x2, x11, x10         ; 4 - 已读        ← 只读到凑满 4 字节
mov  x3, #-1              ; (__read_chk 的 buflen 哨兵)
bl   __read_chk@plt       ; 4 字节循环读取
str  x0, [x19, #536]      ; 返回值
cmp  x11, #1
cset w10, lt              ; 返回值 < 1 → 错误标志存入 [x19,#551]
```

**协议结论：**

| 项 | 值 |
| --- | --- |
| 传输层 | 无名管道（`pipe2`，无 `O_CLOEXEC`） |
| 帧格式 | **定长 4 字节，无长度前缀、无类型字段、无魔数** |
| 帧语义 | 由 `[x19+1172]` (`+0x494`) 承载，与 `+0x484`(1156) 成组，即 `{u32 值, 伴随状态}` |
| 读取语义 | 循环 `__read_chk` 直到 **恰好收满 4 字节**；`< 1` 置错误标志 |
| 关闭语义 | 写完立即 `close` 该端 → 对端读到 0 字节，作为**流结束信号** |
| 端序 | 原生小端（`str w` 直接落内存） |

即：**这不是一个可扩展的 IPC 框架，而是一个"4 字节状态字 + 写后即关"的一次性通知戳。** 这也解释了为什么 `write`/`close` 调用点成对出现且三处一模一样。

### 3.3 4 字节载荷的来源

`[x19+0x494]` 在 `0x9e6c` 处由 `str x0, [x8, #1168]` 写入（`x8 = x19`），值来自 `getppid`/`fork` 返回域。

`[x19+0x2a8]` (680，`getppid` 的镜像字段) 在三个分支被"取负"后写入 `[x19+1172]`：

| 位置 | 操作 |
| --- | --- |
| `0x12388`–`0x12390` | `ldr w8,[x19,#680]` → `neg w8, w8` → `str w8,[x19,#1172]` |
| `0xee00` / `0x11124` / `0x137ac` | `str w9,[x19,#1172]`（直接存 fork 返回值） |

→ 载荷是**进程标识 / 退出状态**一类的单调 4 字节值，语义为"通知父进程：我起来了 / 我退出了 / 状态是 N"。

### 3.4 `fork` 处理：两处，均在巨型 CFF 函数体内

| 位置 | 所属函数（FDE 起点） | 上下文 |
| --- | --- | --- |
| `0x1110c` | `0xdf2c` | `fork` 前后各一次 `blr` 间接调用；`str w0,[x19,#676]` 与 `str w9,[x19,#1172]` 落 pid |
| `0x13770` | `0xdf2c` | 同一函数**第二次** `fork`（与上者在 CFF 内是两个平坦化块）；`str w0,[x19,#472]`、`str w10,[x19,#1172]` |

两个 `fork` 点各自紧跟 `close`（`0x123a4`、`0x13be8`）与父进程侧的 `wait4`。

### 3.5 退出路径：内联 `exit(0)` + `prctl(PR_SET_NAME)`

| 位置 | 序列 |
| --- | --- |
| `0x11e98` / `0x123c4` / `0x12f80` | `mov x0, xzr` → `mov x8, #93` → `svc #0`，即**内联 `exit(0)`**（不经 libc `exit`，故不跑 atexit 链） |
| `0x15d0c` / `0x16434` | `w0 = 0x0f`(=15, **`PR_SET_NAME`**)、`x1 = [x29-104]` / `[x29-160]`（字符串指针）、`x2=x3=x4=0`，`x8 = 167` → **`prctl(PR_SET_NAME, name)`** |
| `0x10438` | `x8 = 221` = `execve`；`x0 = [x19+960]`、`x1 = [x19+968]`、`x2 = [x19+976]`，即 `execve(path, argv, envp)` |
| `0x104e8` / `0x1a34c` | `x8 = 101` = `nanosleep` |
| `0xfb00` | `x8 = 59` = **`pipe2`** |

**`prctl(PR_SET_NAME, …)` 是第二条改名通道**：它设置的是**线程名**（`/proc/<pid>/task/<tid>/comm`），与 §4 的 `setArgV0`（改 `argv[0]`，影响 `/proc/<pid>/cmdline` 与 `ps` 的 `COMMAND` 列）互补。两者合起来把子进程在 `ps`、`/proc/*/comm`、`/proc/*/cmdline` 三个视图里都伪装成非本进程的名字。

内联 `exit(0)` 直接走 `svc 93` 而不调用 libc `exit`，意味着**不触发 `atexit`/`__cxa_atexit` 注册的清理函数**——与 §1.3 的"`.init_array` 全 0、无构造函数"以及 `.rodata` 里 `__cxa_guard_acquire` / `__cxa_thread_atexit` 的 ABI 字符串一致：该库刻意避免在退出时留下可观测的清理行为。

---

## 4. 进程伪装链：`setArgV0` + `android/os/Process` + `zygote`

这是一条**完整闭环**的证据链，全部来自 §2 解出的字符串与它们的使用点：

| 环节 | 证据 |
| --- | --- |
| 反射类 | `android/os/Process`（`0x3cfc`/`0x1b5e4`，18 B） |
| 调用方法名 | `setArgV0`（`0x3cdc`/`0x1b5e4`，8 B） |
| 目标进程名 | `zygote`（`0x3b0c`/`0x7b8c`，6 B） |
| 环境变量前缀 | `LD_LIBRARY_PATH=`（`0x3be8`/`0x7b8c`，16 B） |
| 状态前缀 | `State:`（`0x3b00`/`0x7b8c`，6 B） |
| AM 命令 | `am`（`0x3c10`/`0x14fa0`，2 B） |
| 反调试探针 | `TracerPid:`（`0x3028`/`0x7b8c`，11 B） |

`setArgV0` 是 Android 上**改写进程 `argv[0]`（进而影响 `/proc/<pid>/cmdline` 与 `ps` 的 `COMMAND` 列）**的标准手法；配合 `zygote` 常量，效果是把子进程伪装成 zygote 派生出来的普通进程。

再叠加 §3.5 的 `prctl(PR_SET_NAME, …)`（改线程名 / `comm`），该库对进程身份做了**三重改写**：

| 视图 | 机制 | 证据 |
| --- | --- | --- |
| `/proc/<pid>/cmdline`、`ps COMMAND` | `android/os/Process.setArgV0` | `0x1c084`、`0x1c184` 包裹器 + `0x1c408` 调用点 |
| `/proc/<pid>/task/<tid>/comm` | `prctl(PR_SET_NAME)` | `0x15d0c`、`0x16434`（`w0=15`） |
| 环境 | `LD_LIBRARY_PATH=` 前缀 | `0x3be8`（16 B 明文） |

该链在静态侧闭合，与 §1.4 的"无网络导入"和 §3 的"管道 4 字节戳"彼此独立验证。

---

## 5. 反调试与进程身份探测

`0x18ec8` 起的函数（包裹器可执行验证）产出 `"TracerPid:"`，与 `libxyasf.so` 的 `isPtrace`（见 [xyasf-device-fingerprint.md](xyasf-device-fingerprint.md)）**共用同一探针字符串**，但**两库各自独立实现**：

| 库 | 探针 | 读取方式 |
| --- | --- | --- |
| `libxyasf.so` | `proc/%d/status` + `TracerPid` | `fgets` 逐行 + `strncmp` + `%lld` |
| `libtinyd.so` | `/proc/<pid>/status` | `openat`(56) → `fdopen("r")` → `fgets` |

对应 `libxyasf.so` 的 `/proc` 身份判定：

- `openat(AT_FDCWD, "/proc/<pid>/status")` → `fdopen` → `fgets` → 逐行比对（`0xa4` 处 10 字节比对缓冲区）。
- 同一函数族在 `0x199e8`–`0x19a2c` 做行内 `strncmp`，命中即计数。

其他身份/环境探针：

| 位置 | syscall | 语义 |
| --- | --- | --- |
| `0xe5d8` | `172` = `getppid` | 取父 pid → 写 `[x19,#616]` |
| `0x1168c` | `172` = `getppid` | 同上（CFF 另一块） |
| `0xc864` / `0xd05c` | `134` = `rt_sigaction` | 装信号处理（崩溃/终止兜底） |
| `0xff04` / `0x13424` | `129` = `kill` | 发信号 |
| `0x78` | `readlinkat` | — |

`syscall@plt` 仅 2 处，参数均为 `178` = `gettid`，位于 `0x61b0` / `0x61c4` 的**互斥量状态机**里（配套 `pthread_mutex_lock` `0x6194`、`pthread_cond_wait` `0x6208`、`pthread_cond_broadcast` `0x62e4`）：

```
0x61ac: mov w0, #0xb2   (178 = gettid)
0x61b0: bl  syscall@plt
0x61b4: mov w20, w0
...
0x61d0: cmp w21, w20     ; 比对上一次 owner 的 tid
0x61d4: b.eq ...
```

→ **自旋/条件变量锁的 owner-tid 判据**（防"非 owner 解锁"）。这是典型的 `std::recursive_mutex` 风格实现，属健壮性代码，非风控功能。

---

## 6. 日志与崩溃兜底：辅助路径，不是主功能

| 导入 | 调用点 | 说明 |
| --- | --- | --- |
| `openlog` | `0x63f4` | 单点 |
| `syslog` | `0x6408` | **仅 1 处** |
| `closelog` | `0x641c` | 单点 |
| `android_set_abort_message` | `0x63ec` | 单点 |
| `vfprintf` / `fputc` / `vasprintf` | `0x63c0` / `0x63cc` / `0x63ec` | 格式化 |
| `abort` | 13 处 | 含 `0x5ab4`、`0x5b08`、`0x1acf8` 等 |
| `__stack_chk_fail` | **52 处** | 栈保护，全库最高频 |

**修正旧稿侧重**：`risk-controls.md` §10 原文把 `fork / syslog / abort-message` 并列作为"独立守护进程"的依据。实测 `syslog` 只有 **1 个** 调用点，而 `fork` 有 2 个、`close` 7 个、`write` 3 个、`__read_chk` 2 个——**日志是辅助能力，主功能是 §3 的管道信令与 §4 的进程伪装**。

`__stack_chk_fail` 的 52 个调用点说明该库**全程开启栈保护**，与其"崩溃兜底"定位一致。

---

## 7. 控制流平坦化：可还原

### 7.1 惯用式

`.text` 内反复出现如下形态（示例 `0xdf00`–`0xdf28`）：

```asm
adrp x8, 0x23000
ldr  x9, [x8, #2392]        ; x9 = 分发池槽（.data，R_AARCH64_RELATIVE）
mov  w8, #0xac64
movk w8, #0x72a, lsl #16    ; w8 = 0x072aac64
add  x8, x24, x8            ; 64 位加
sub  w10, w24, w8           ; 取 x8 低 32 位参与
mov  w11, #0x72a8
movk w11, #0x3df, lsl #16   ; w11 = 0x03df72a8
add  w10, w10, w11
add  x9, x9, w10, sxtw      ; 池值 + 符号扩展的 32 位链
br   x9
```

w 寄存器链由**成对的 `mov`/`movk` 立即数**构成，两个立即数之和恒为一个与块索引相关的常量（本例 `0x072aac64 + 0x03df72a8` 域）。这是 OLLVM 系列的 **CFF + 常量不透明化**。

### 7.2 分发池

- 位置：`.data`，`0x23150`–`0x23de0`。
- 槽数：**381** 个 `R_AARCH64_RELATIVE`（总计 408 个，另 27 个在 `.data.rel.ro`/`.got`/`.bss` 页）。
- 槽值形态：`0x3ab8`、`0x339fb80`、`0x3ef5a9c`、`0x3c42110`、`0x3fe71b0`… 与块偏移量做模 2³² 加/减后落回 `.text`。

### 7.3 动态验证：平坦化完整可解

对 `0xdf2c` 起的巨型函数做指令级跟踪，取长度 264 条指令的窗口：

```
transitions: 14
  br@0xdfbc -> 0x10404    x9 命中
  br@0x10410 -> 0x1052c   x8 命中
  br@0x10574 -> 0x115fc   x10 命中
  br@0x11608 -> 0x116ec   x8 命中
  br@0x11728 -> 0xf514    x9 命中
  br@0xf534 -> 0xf01c     x9 命中
  br@0xf154 -> 0x11bec    x10 命中
  br@0x11c08 -> 0x10e78   x9 命中
  br@0x10e84 -> 0x112c0   x8 命中
  br@0x1135c -> 0x12950   x10 命中
  br@0x1295c -> 0x138b8   x8 命中
  br@0x138e8 -> 0x101f0   x9 命中
  br@0x10210 -> 0xfa00    x9 命中
  br@0xfa0c -> 0xfad0     x8 命中
```

**14/14 个 `br` 的目标全部落在 `.text` 的合法基本块起点**，且每次都由一个通用寄存器直接承载。

→ **平坦化没有隐藏语义**：它只是把顺序块打散后用查表跳转串起来。按 `br` 目标重建后继边即可还原原始控制流，不需要求解不透明谓词。

### 7.4 与 `libtiny.so` 的对比

| 项 | `libtiny.so` | `libtinyd.so` |
| --- | --- | --- |
| `.text` 大小 | 6 112 000 B（`0x5d4300`） | 105 320 B（`0x19b68`） |
| FDE 数 | 4028 | 127 |
| 最大单函数 | `a()` = **181 732 B**（`0x15e9f4`→`0x18afd8`，仅 1 个 FDE） | `0xdf2c`（跨度覆盖 `0x10438`/`0x1110c`/`0x13770`） |
| 平坦化 | 有 | 有，同款惯用式 |
| 字符串加密 | 有 | 有（§2，已闭式） |
| 分发池 | `x19+0x1253` 谓词数组 + 61 比较块 | `.data` 381 槽 + `br` 跳转 |

---

## 8. JNI 面

`JNI_OnLoad` @ `0xa630`，立即被 CFF 接管（首块 `0xa630`–`0xa6b8` 后即 `br x9`）。

- 无 `FindClass` / `GetMethodID` / `NewGlobalRef` 的**明文常量**可直接归因——类名与方法名来自 §2 的 7 条密文 + `.rodata` 的明文字面量。
- `JNI_OnLoad` 在模拟器中**无法独立跑通**（`Invalid memory read (UC_ERR_READ_UNMAPPED)`）：它依赖真实 `JNIEnv` 与 `JavaVM`，且入口块立即进入需要外部状态的平坦化分发。这与"注册表动态构造"一致。
- `.rodata` 明文中可打印连续串（≥4 字符）共 **84 条**，可打印字符占比 62.6%（1597/2552）；其中**没有** `Java_` 前缀的导出名，反射面完全由密文侧提供。

**反射使用面**（来自 `.rodata` 明文，`0x3530`–`0x3b00`）：`android/content/Context`、`getContentResolver`、`android/content/Intent`、`getIntExtra`、`getBooleanExtra`、`addCategory`、`checkPermission`、`getClassLoader`、`getSystemService`、`java/lang/{String,Object,Class,Integer,Long,Float,Boolean,CharSequence}`、`java/util/{HashMap,ArrayList,Map,Map$Entry,Iterator,Collection,List,Set}`、`keySet`/`entrySet`/`values`/`iterator`/`hasNext`/`next`/`getKey`/`getValue`/`size`/`isEmpty`/`remove`/`getName`、`valueOf`/`toString`/`intValue`/`longValue`/`floatValue`/`booleanValue`。

以及库内部诊断串（明文的 C++ ABI 消息）：

```
__cxa_guard_acquire detected recursive initialization
std::__libcpp_tls_create() failed in __cxa_thread_atexit()
%s failed to broadcast
%s failed to acquire mutex
%s failed to release mutex
libc++abi
```

→ 该库是 **libc++ 编译的 C++ 代码**（不是纯 C），使用 `std::shared_mutex`/`std::recursive_mutex` 与线程局部存储。

---

## 9. 数据表（非加密）

`.data` `0x23188` 处的重定位指向 `.rodata` `0x3ab8`；而 `0x3d18` 起是一张**标准 10 的幂次 double 表**：

```
0x3d10: 0.0
0x3d18: 1.0
0x3d20: 10.0
0x3d28: 100.0
0x3d30: 1000.0
...  直至 1e16
```

→ **不是 S 盒、不是轮常量、不是密钥表**，而是 `strtod`/格式化用的浮点缩放表（`0x03ff…` 系列是 IEEE-754 正整幂）。

`.rodata` `0x3a80`–`0x3b80` 段内的 `0x3b7f`（全 `0x24`）、`0x3b10`（`0x12`/`0x0c`/`0x0f` 序列）等**不是密文**：它们在 `.rodata` 明文区（`0x3530`–`0x3b00` 已有多条可读串），属于**内联查找表/位掩码**，无调用点引用为字符串。

---

## 10. 结论与未闭环边界

### 10.1 闭环项

| 项 | 状态 |
| --- | --- |
| 字符串加密算法 | **已闭式还原**（§2.2/§2.3，4 个解码器 × `i%20` 调度表，逐位置 256 值穷举全等） |
| 明文全集 | **7/7 全部解出**（§2.5，含代码立即数来源），另有非调用点起点的穷举阴性结论（§2.7） |
| IPC 传输层 | **已还原**：`pipe2`（内联 svc 59） + `__read_chk`/`write`（§3.1） |
| IPC 帧格式 | **已还原**：定长 4 字节、无类型字段、写后即 `close`（§3.2） |
| IPC 载荷**形状与来源** | **已还原**：`{u32 值, 伴随状态}` 于 `+0x494`/`+0x484`，值来自 `fork`/`getppid` 域（§3.3）；**取值含义未闭环**，见 §10.2 |
| 进程伪装链 | **已还原**：`setArgV0("zygote")` + `prctl(PR_SET_NAME)` 双重改名（§3.5、§4） |
| 退出/替换路径 | **已还原**：内联 `exit(0)` ×3、`execve` ×1、`prctl(PR_SET_NAME)` ×2、`nanosleep` ×2（§3.5） |
| 反调试探针 | **已还原**：`/proc/<pid>/status` + `TracerPid:`（§5） |
| CFF | **可解**，14/14 动态跳转目标验证通过（§7.3） |
| 网络能力 | **确证为零**（§1.4） |
| 加载期自启 | **确证为零**（`init_array` 全 0，§1.3） |

### 10.2 未闭环项（给出具体地址与原因）

| 项 | 具体环节 | 地址 | 原因 |
| --- | --- | --- | --- |
| `JNI_OnLoad` 注册的 `JNINativeMethod` 表 | 类名 + 方法名 + 签名三元组 | `0xa630` 起，表实体在 `.bss` `0x24180`+ | 三元组在 `JNI_OnLoad`（CFF）内**运行时构造**；静态数据中不存在。需带真实 `JNIEnv` 的进程内插桩才能取表内容，静态侧不可达 |
| 4 字节载荷的**取值语义** | `+0x494` 具体字段含义（pid？退出码？状态枚举？） | 写点 `0x9e6c`、`0x12390`、`0x11124`、`0x137ac` | 协议**形状**已确定（定长 4 字节、写后关），但"哪个值代表哪种状态"需要 Java 侧调用方或运行时观测。**协议本身不再是"未知"** |
| `.data` 381 槽分发池的**逐槽→块映射** | 各槽配对的 w 寄存器常量 | `.data` `0x23150`–`0x23de0` | 需要逐块符号求值 32 位不透明链；**平坦化已被证明不隐藏语义**（§7.3），此项只影响反编译可读性，不影响任何算法结论 |
| 父进程侧的接收者 | 谁读这 4 字节 | 本库外部（Java 侧或 `libtiny.so`） | `libtinyd.so` 只实现一端；对端不在本样本边界内 |

**没有"未知加密"**：本库唯一的加密（字符串的旋转+模加/XOR 双射）已闭式（§2），其余全部为明文表或 C++ ABI 常量。

---

## 11. 复现命令

工作目录：`rednote-9.37.0-re/`（`PYTHONPATH=re`）。

```bash
export PYTHONPATH=re

# 段布局 / 导出面 / 导入面 / init_array
python3 re/tinyd_imports.py

# 7 个解码调用点与包裹函数
python3 re/tinyd_sites_scan.py
python3 re/tinyd_decoders.py

# 字符串解密（闭式表）
python3 re/tinyd_sweep.py
python3 re/tinyd_decode_all.py          # 穷举，含阴性结论

# 调度表闭式（逐位置 256 值穷举拟合）
python3 - <<'EOS'
import json
S=json.load(open('re/tinyd_schedule.json'))          # 4 个解码器 × 48 位置项（周期 20）
def rol(x,r): r%=8; return ((x<<r)|(x>>(8-r)))&0xff if r else x
def dec(k,buf):
    s=S[k]; o=bytearray()
    for i,x in enumerate(buf):
        r,op,c=s[i%20]; v=rol(x,r)
        o.append((v^c) if op=='xor' else ((v+c)&0xff))
    return bytes(o)
d=open('libs/lib/arm64-v8a/libtinyd.so','rb').read()
print(dec('7b8c', d[0x3be8:0x3be8+16]))             # b'LD_LIBRARY_PATH='
print(dec('70c8', d[0x3b00:0x3b00+10]))             # b'TracerPid:'
print(dec('14fa0', (0xb9bd).to_bytes(3,'little')[:2]))  # b'am'
EOS

# IPC / 调用点
python3 re/tinyd_pltsites.py
python3 re/tinyd_xref.py
```

反汇编基线（供逐地址复核）：

```bash
aarch64-linux-gnu-objdump -d --start-address=0x5324 --stop-address=0x1ee90 \
    libs/lib/arm64-v8a/libtinyd.so > re/tinyd_text.asm
```

关键产物：`re/tinyd_text.asm`、`re/tinyd_schedule.json`（4 解码器 × 48 位置项，周期 20）、`re/tinyd_posmaps_7b8c.json`（位置真值表）、`re/tinyd_strings.json`、`re/tinyd_strings2.json`、`re/tinyd_sites.json`、`re/tinyd_pltsites.json`、`re/tinyd_fde.json`。
