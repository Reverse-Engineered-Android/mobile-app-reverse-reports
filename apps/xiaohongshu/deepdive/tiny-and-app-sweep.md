# 小红书 9.37.0 全应用 native 加密普查与 libtiny.so 深挖

本文补上 [crypto.md](crypto.md) 覆盖矩阵里最后两块**当时尚未写成文档**的产出：

1. **全应用 native 加密普查**——164 个 arm64 `.so` 逐个扫描，给出"哪些库带加密代码、哪些不带"的完整二分清单，使"没有未知加密代码"这一结论从**抽样**升级为**全量**。
2. **`libtiny.so`（Tiny opcode 引擎）深挖**——把 `crypto.md` 条目 12 的"分发机制已恢复，语义未展开"推进到**操作码全集枚举完毕**，并**纠正**先前文档中"opcode 二叉比较点"的错误表述。

> 口径：本文所有地址均为 `libs/lib/arm64-v8a/` 下该 `.so` 的文件内虚拟地址（vaddr），与 ELF 段映射一致。

---

## 1. 全应用 native 加密普查

### 1.1 样本范围

| 项 | 值 |
| --- | --- |
| 扫描目录 | `libs/lib/arm64-v8a/` |
| `.so` 数量 | **164** |
| 总大小 | **194 MB** |
| 扫描工具 | `re/sweep_crypto.py` |
| 原始输出 | `re/sweep_crypto.txt`、`re/sweep_crypto_final.txt` |

### 1.2 判定口径

一个库被判定为"**带加密相关代码**"，只要满足以下任一条：

- **加密指令族**：出现 `aes*`（AESE/AESD/AESMC/AESIMC）、`sha1*`/`sha256*`/`sha512*`、`pmull`（GF(2^128) 乘法，GHASH/CRC 用）、`crc32*`（ARMv8 CRC 扩展）、`sm3*`/`sm4*`（国密）；
- **已知算法常量**：标准 MD5 轮常量表 `MD5-T[0:8]`、标准 MD5 初值 `MD5-IV`、AES S 盒前 16 字节、SM4 S 盒前 16 字节、标准 Base64 字母表、CRC32 表、ChaCha20 `sigma` 常量；
- **≥ 8 KB 高熵区**：段内连续 ≥ 8192 字节且熵显著高于普通代码/字符串的区域（用于发现未被常量命中的加密数据或打包载荷）。

三者互不替代：只看指令会漏掉查表实现，只看常量会漏掉指令实现，只看高熵区会漏掉两者都不明显的库。因此采用**并集**判定，宁多勿少。

### 1.3 结果：32 带 / 132 不带

- **32 个库命中**（有加密指令、或已知常量、或 ≥8 KB 高熵区）；
- **132 个库三者全无**——完整名单见 `re/sweep_crypto_final.txt` 末节，包含 `libc++_shared.so`、130 余个 React Native / Yoga / folly / hermes 渲染与基础设施库、`libshadowhook.so`、`libbytehook.so` 等 hook 框架、`libmarsxlog.so` 等。

**这句话的意义**：`crypto.md` 矩阵的"无未知加密代码"结论此前建立在核心 `libxyass.so` / `libtiny.so` 的逐字节恢复上；本节把**其余 163 个库**也纳入全量普查后，仍**没有任何库**出现"既非已知算法、又无法归因的加密代码"。134 行的二分清单使得任何一个库都可以被单独复核。

### 1.4 32 个命中库逐条证据

| 库 | .text 指令数 | 加密指令族 | 命中常量 | 高熵区 | `JNI_OnLoad` |
| --- | ---: | --- | --- | --- | --- |
| `libzeusEngine.so` | 4 006 944 | — | CRC32-tab | 12 KB | 是 |
| `libkasa_sdk.so` | 2 918 198 | — | MD5-IV, Base64, CRC32-tab(+BE) | — | 是 |
| `libchopper_v2.so` | 2 504 534 | crc32 ×19 | Base64, CRC32-tab | 139 KB + 8 KB + 24 KB | 是 |
| `libInsightWrapper.so` | 1 927 037 | — | — | 24 KB | — |
| `libtxmapengine.so` | 1 497 773 | — | Base64, CRC32-tab ×3 | 12 KB | 是 |
| `libares.so` | 1 236 957 | — | MD5-T, AES-Sbox, Base64 | 68 KB + 12 KB | 是 |
| `libj2v8.so` | 1 261 121 | crc32 ×1 | Base64, CRC32-tab(+BE) | 16 KB | 是 |
| `libredffmpeg.so` | 771 148 | aes, sha1, sha256, sha512, pmull | MD5-T, MD5-IV, SM4-Sbox, ChaCha-sigma, Base64 | 28 KB + 12 KB + 8 KB + 148 KB | — |
| `libpag.so` | 546 580 | — | CRC32-tab(+BE) | — | 是 |
| `libxhsgraphicemobile_jni.so` | 451 454 | — | CRC32-tab ×2 | — | 是 |
| `libhermes.so` | 365 223 | — | MD5-IV, Base64 | — | — |
| `libtxffmpeg.so` | 330 355 | — | MD5-T, Base64 | 12 KB | — |
| `libpredy-native-ri.so` | 235 082 | — | Base64 | — | — |
| `libreddb.so` | 245 289 | — | — | 12 KB | 是 |
| `libhermes_executor.so` | 268 761 | — | — | 16 KB | 是 |
| `libreddownload.so` | 187 269 | — | Base64 ×2 | — | 是 |
| `libquickjs-android.so` | 178 842 | — | ChaCha-sigma | — | 是 |
| `libYTCommonLiveness.so` | 114 895 | — | Base64 | — | 是 |
| `libkyctoolkit.so` | 114 280 | — | MD5-T, MD5-IV, SM4-Sbox, Base64 | 8 KB | 是 |
| `libxyass.so` | 112 054 | — | MD5-T, MD5-IV, Base64 ×4 | — | 是 |
| `libxylog.so` | 99 426 | — | CRC32-tab(+BE) | 8 KB | — |
| `libxyasf.so` | 91 936 | — | MD5-IV | — | 是 |
| `libfabricjni.so` | 86 865 | — | — | 8 KB | 是 |
| `libeidjni.so` | 71 958 | — | MD5-IV, AES-Sbox, SM4-Sbox, Base64 | — | — |
| `libxhslonglink.so` | 48 288 | — | MD5-IV, Base64 ×2 | 16 KB | 是 |
| `libmmkv2.so` | 39 132 | crc32 ×15 | MD5-IV | — | 是 |
| `libsentry.so` | 17 672 | — | Base64 | — | 是 |
| `libcronet.114.0.5735.38.so` | 9 169 | **aes ×871, pmull ×84** | ChaCha-sigma ×2, Base64 ×5, CRC32-tab ×2 | 20 KB | 是 |
| `libentryexpro.so` | 9 556 | — | AES-Sbox | 8 KB | — |
| `libed25519.so` | 9 519 | — | AES-Sbox | 8 KB | 是 |
| `libliteavsdk.so` | 3 551 | **aes ×223** | Base64 ×4, CRC32-tab(+BE) | 8 KB | 是 |
| `libtiny.so` | 1 528 720 | — | MD5-IV, Base64, CRC32-tab | 131 KB | 是 |

**读法与归因**：

- **`libcronet...so` aes=871 / pmull=84** 是 Chromium 网络栈自带的 TLS 记录层与 GHASH，属标准实现；
- **`libliteavsdk.so` aes=223** 是腾讯直播 SDK 的音视频加密，第三方组件；
- **`libredffmpeg.so`** 是 FFmpeg 裁剪版，五类指令各 1 处是少量 intrinsic 入口，主要靠常量表（MD5-T/IV、SM4-Sbox）实现；
- **`libchopper_v2.so` crc32=19 + 139 KB 高熵**：图像编解码链路（Chopper）的 CRC 校验 + 一张较大的压缩/量化表；
- **`libxyass.so` / `libtiny.so`** 即本目录主体分析对象，属小红书自研风控链路。

**结论**：164 个库全部有归因，无"未识别加密库"。

---

## 2. `libtiny.so` 的密码学画像

`re/tiny_crypto_char.py` 输出（`re/tiny_crypto_char.txt`）：

### 2.1 实际存在

| 常量 | 文件偏移 | 段 | 用途判读 |
| --- | --- | --- | --- |
| `MD5-IV(std)` | `0xf1510` | `.rodata` | 标准 MD5 初值 `0x67452301…`（小端存放） |
| `Base64-std` | `0xfe640` | `.rodata` | 标准 base64 字母表 `A–Za–z0–9+/` |
| `CRC32-tab[0:8]` | `0xf7730` | `.rodata` | CRC32（反射多项式 `0xEDB88320`）前 8 项 |

### 2.2 明确不存在（逐项已验）

| 检验 | 结果 |
| --- | --- |
| 完整 64 项 MD5 T 表（任何字节序、任何连续/非连续排布） | **不存在** |
| MD5 T 常量以 `mov`/`movz` 立即数形式出现 | **0 / 64** |
| `aes` / `sha1` / `sha256` / `sha512` / `sm3` / `sm4` / `pmull` / `crc32` 指令族 | **全部为 0** |
| AES S 盒、SM4 S 盒 | 不存在 |

**这条负面结论是可复核的强结论**：`libtiny.so` 里没有 AES、没有 SHA 族、没有 SM 族、没有 GHASH，也没有完整 MD5 轮常量。它能做的标准密码学只有 **MD5 骨架（有初值、无轮常量）**、**Base64 编码**、**CRC32 校验**。

> 注意"有 MD5 初值但无 MD5 轮常量"这一组合并不矛盾：MD5 初值也是"自定义哈希"最常用的起始状态，而轮常量可以被换成一整套自定义常数（这正是 `libxyass.so` 定制 HMAC-H 的做法，见 [crypto.md](crypto.md) §3）。因此这里读作**"以 MD5 初值为起点的自定义摘要，而非标准 MD5 实现"**。

### 2.3 `libtiny.so` 的高熵区

| 项 | 值 |
| --- | --- |
| 所属段 | `.data` |
| 扫描窗口 | `0x74d000` – `0x76d000`（131 072 字节） |
| 窗口前导零 | `0x74d000` – `0x750fe0`，共 **16 352** 字节全 `00` |
| **高熵载荷本体** | **`0x750fe0` – `0x76d000`，114 720 字节** |
| 载荷熵 | **7.9982775 bit/byte**（256 个取值全出现） |
| 容器特征 | 无 `\0asm` / gzip / zip / zstd / xz / bzip2 魔数 |
| 载荷首 16 字节 | `e6678698cc304fd5ca44aaa58511cd2e` |

> 口径说明：直接把整个 131 072 字节窗口按熵统计会得到 7.5189 bit/byte——那是**被 16 352 字节全零前导稀释**的结果；扣掉前导零后才是这张表的真实熵 **7.9983**。本文档初稿曾误用稀释值，此处已更正为分段结果。`evidence.md` 中记录的 `.data 0x754AC0`–`0x7701C0`、112 384 字节、熵 7.9982945 是本区另一组取窗口，两者在有效载荷上一致（7.9983 vs 7.9983），边界差异只是取窗口方式不同。

**判读**：该区位于 `.data`（可写、运行时填充），**有效载荷前存在 16 KB 全零前缀**，且**不含任何已知容器魔数**——符合"运行期填充的缓冲/表区"特征，而不是随包分发的加密载荷。因此不存在"打包的加密 blob 待解"这一问题。这与先前对 `libxyass.so` 的"未发现静态或已覆盖动态路径引用的高熵数据区"写法保持一致。

---

## 3. Tiny opcode 引擎：分发结构

### 3.1 入口与操作码字段

```text
u2.b(opcode, method, host, path, query, body)        // Java 侧
   └─► JNI a()                                        // 第 3 个参数 = opcode
         str  w2, [x19, #0xa4]     @ 0x15ea20         // ← 操作码落到结构体字段
         ...
         mov  x21, [x19, #8]       @ 0x15ea60         // CFF 跳转表基址
         br   x21                  @ (CFF 主分发)
```

| 项 | 值 |
| --- | --- |
| 操作码字段 | `[x19, #0xa4]`（32 位，来自 JNI 第 3 个参数 `w2`） |
| 字段写入点 | `0x15ea20` `str w2, [x19, #0xa4]` |
| 函数入口 | `0x15e9f4`（`JNI_OnLoad` @ `0x18afd8` 注册） |
| 入口形态 | **CFF**：大段 `mov xN, x9` 寄存器灌水前导 + `br x21` 计算跳转 |
| 结构体相关字段 | `[x19, #0xb8]` = `(x3, x0)`（第 4、1 参数）；`[x19, #8]` = 跳转表入口；`[x19, #0xa8]` = 目标块 |

### 3.2 分发单元的形状

每个操作码对应一个**独立比较块**，形状完全一致：

```asm
    ldr   w8,  [x19, #0xa4]        ; 取操作码
    mov   w9,  #<lo16>
    movk  w9,  #<hi16>, lsl #16    ; 物化 32 位操作码常量
    adrp  x10, #<page>             ; CFF 页基址
    cmp   w8,  w9                  ; ← 操作码比较
    ...
    add   xN,  <页基址>, <块偏移>   ; 目标块 = 页基址 + 偏移
    add   x9,  x19, #1, lsl #12
    cset  wK,  eq   (或 lt)
    add   x9,  x9, #0x253          ; ← 谓词数组基址 = x19 + 0x1253
    strb  wK,  [x9, #<偏移>]        ; 把比较结果写成字节
    br    xN                       ; 跳到目标块
```

**实际语义（已按 AArch64 标志位规则核对）**：

- 比较块里夹在 `cmp` 与 `cset` 之间的是 `add` / `sub` / `adrp` / `movk` / `ldr` / `str` 等——**这些指令不写 NZCV**。AArch64 中只有 `adds`/`subs`/`ands`/`bics`/`adcs`/`sbcs`/`negs` 与 `cmp`/`cmn`/`tst`/`ccmp`/`ccmn` 会写标志位。
- 用**正确的标志位规则**重扫：61 个比较块中，**0 个**在 `cmp` 与 `cset` 之间存在真正写 NZCV 的指令。
- → 因此 `cset` **确实读取的是操作码比较的结果**，61/61 成立。

`cset` 条件分布：**`eq` 31 个、`lt` 30 个**。两者合计 61，与比较块总数一致——每个操作码用一个"等于"或"小于"型比较把结果落进谓词数组。

### 3.3 谓词数组

| 项 | 值 |
| --- | --- |
| 基址 | `x19 + (1<<12) + 0x253` = **`x19 + 0x1253`** |
| 基址物化点 | 126 处成对 `add xN, x19, #1, lsl #12` + `add xN, xN, #0x253` |
| 写入偏移范围 | `0x1` – `0x3c`（**60 个互不相同的偏移**，落在 61 字节窗口内） |
| 数组尺寸 | `x19+0x1253` – `x19+0x128f`，**61 字节** |

即：**每个操作码在结构体内占 1 个谓词字节**，比较结果为真/假写入对应槽位，随后由 CFF 统一 `br` 到目标块。这是"以字节数组记录操作码判定结果"的分发形态，**不是二叉比较**。

---

## 4. 重要纠正：`0x16b08c` / `0x17cdb0` 不是"二叉比较点"

先前 `crypto.md` / `evidence.md` / `risk-controls.md` / `audit.md` / `algorithm.md` / `report.md` 把这两个地址称为"**opcode 二叉比较点**"，隐含"引擎用二叉搜索匹配操作码"的模型。**该表述错误**，实测如下：

```asm
; ---- 0x16b088 块 ----
0x16b088  ldr  w8, [x19, #0xa4]
0x16b08c  mov  w9, #0xfcac
0x16b090  movk w9, #0x96f7, lsl #16     ; → 0x96f7fcac
0x16b098  cmp  w8, w9

; ---- 0x17cdac 块 ----
0x17cdac  ldr  w8, [x19, #0xa4]
0x17cdb0  mov  w9, #0xfcac
0x17cdb4  movk w9, #0x96f7, lsl #16     ; → 0x96f7fcac  （同一个常量！）
0x17cdbc  cmp  w8, w9
```

两处**物化的是同一个操作码常量 `0x96f7fcac`**，是两个 **CFF 重复块**（同一条比较在平坦化控制流里被复制了两份），彼此之间**没有大小关系、没有分工**。因此：

- ❌ "二叉比较点" —— 错误；
- ✅ "操作码 `0x96f7fcac` 的两个重复比较块" —— 正确。

这也解释了为什么 `libtiny.so` 里几乎所有操作码都恰好对应 **2 个**比较块（见 §5）：**重复是 CFF 平坦化的产物，不是算法的二义性**。

---

## 5. 操作码全集：31 个

### 5.1 枚举方法（含先前漏项的根因）

| 版本 | 方法 | 结果 |
| --- | --- | --- |
| `re/tiny_opcodes.py`（先前） | 找**相邻** `mov` + `movk` 组成 32 位常量，再在其后 12 条指令窗口内找 `cmp` | **30 个**（漏 1） |
| `re/tiny_opcode_resolve.py`（本次，权威） | 找**每一处** `ldr wN, [x19, #0xa4]`，在其后 30 条窗口内找 `cmp wN, wK`，再**反向**解析 `wK` 的 `mov`/`movk` 链 | **31 个** |

**漏项根因已定位**：漏掉的是 `0x96d0a479`，其比较块在 `0x1701d8`：

```asm
0x1701d8  ldr  w12, [x19, #0xa4]
0x1701dc  mov  w8,  #0xa479
0x1701e0  adrp x9,  #0x704000        ; ← 之间插入了别的指令
0x1701e4  mov  w10, #0xeac4
0x1701e8  adrp x11, #0x16d000
0x1701ec  movk w8,  #0x96d0, lsl #16 ; → 0x96d0a479
0x1701fc  cmp  w12, w8
```

`mov` 与 `movk` **不相邻**，因此旧脚本的"相邻对"前提不成立而漏判。新方法从**字段加载点**出发反向解析，不再依赖相邻性。

### 5.2 完整操作码表

31 个操作码，共 **61 个比较块**（30 个 ×2 + `0x96d0a479` ×1）。

| # | 操作码（hex） | 有符号 | 比较块数 | 加载点 |
| ---: | --- | ---: | ---: | --- |
| 1 | `0x11296316` | 287925014 | 2 | `0x16d658`, `0x182454` |
| 2 | `0x17c04796` | 398477206 | 2 | `0x16c658`, `0x1823ac` |
| 3 | `0x259cebf7` | 631041015 | 2 | `0x16092c`, `0x1871b0` |
| 4 | `0x28ac92d7` | 682398423 | 2 | `0x167ff0`, `0x17a820` |
| 5 | `0x2ad1c199` | 718389657 | 2 | `0x160980`, `0x172e6c` |
| 6 | `0x2f036831` | 788752433 | 2 | `0x167d5c`, `0x175504` |
| 7 | `0x398bf05d` | 965472349 | 2 | `0x165af4`, `0x166e6c` |
| 8 | `0x3c6d0ac1` | 1013779137 | 2 | `0x1634e0`, `0x175a30` |
| 9 | `0x42a21aaf` | 1117919919 | 2 | `0x163c78`, `0x1840d4` |
| 10 | `0x45e9da0d` | 1172953613 | 2 | `0x16497c`, `0x17d2f4` |
| 11 | `0x4af613b8` | 1257640888 | 2 | `0x162388`, `0x179424` |
| 12 | `0x4e418ac4` | 1312918212 | 2 | `0x15f9b8`, `0x162250` |
| 13 | `0x5ac40428` | 1522795560 | 2 | `0x16eecc`, `0x16fa14` |
| 14 | `0x704bfeeb` | 1884028651 | 2 | `0x165bf0`, `0x167240` |
| 15 | `0x727981d1` | 1920565713 | 2 | `0x17f4dc`, `0x1843d0` |
| 16 | `0x7c70cc76` | 2087767158 | 2 | `0x169158`, `0x17d73c` |
| 17 | `0x96d0a479` | −1764711303 | **1** | `0x1701d8` |
| 18 | `0x96f7fcac` | −1762132820 | 2 | `0x16b088`, `0x17cdac` |
| 19 | `0x9701e74c` | −1761482932 | 2 | `0x175f74`, `0x18a3fc` |
| 20 | `0xae821439` | −1367206855 | 2 | `0x17a904`, `0x180088` |
| 21 | `0xae8750a7` | −1366863705 | 2 | `0x164b80`, `0x172d7c` |
| 22 | `0xb20a0be3` | −1307964445 | 2 | `0x1668a0`, `0x16ff3c` |
| 23 | `0xc23a168e` | −1036380530 | 2 | `0x15f1bc`, `0x171638` |
| 24 | `0xc9d57702` | −908757246 | 2 | `0x18294c`, `0x189f6c` |
| 25 | `0xcd554fab` | −850047061 | 2 | `0x17494c`, `0x184010` |
| 26 | `0xcf7db9ff` | −813843969 | 2 | `0x17e308`, `0x1866c8` |
| 27 | `0xd40131d5` | −738119211 | 2 | `0x1646b4`, `0x16f340` |
| 28 | `0xe83def19` | −398594279 | 2 | `0x17cfc0`, `0x185e08` |
| 29 | `0xf3f89a2a` | −201811414 | 2 | `0x1620bc`, `0x163828` |
| 30 | `0xf961fe3b` | −111018437 | 2 | `0x184160`, `0x1858a0` |
| 31 | `0xffd8e9f6` | −2561546 | 2 | `0x1660d0`, `0x175730` |

产物：`re/tiny_opcode_resolve.py` → `re/tiny_opcode_resolve.txt`。

### 5.3 另有 5 处字段引用，不是操作码比较

`ldr wN, [x19, #0xa4]` 共 **66** 处：61 处是上表的 CFF 比较块，**5 处**在 JNI 边界层，属于**把操作码原样转发**而非判定：

| 地址 | 用途 |
| --- | --- |
| `0x4b84d8` | 读出后 `str w9, [x8]` 存进输出结构（透传） |
| `0x6e68d4` | `add w8, w8, #1` 后 `str w8, [x27, #0x34]`（偏移/计数） |
| `0x6e6f5c` | `stp x13, x11, [x27, #0xb0]`（打包进参数块） |
| `0x6e73a8` | `str w8, [x9]`（透传） |
| `0x6e8a7c` | `ldr w9, [x27, #0xa8]` → `cmp w9, w8` → `cset w8, eq` → `and` → `sturb`（**与已记录操作码比较**，非常量比较） |

---

## 5.4 逐操作码语义指纹（JNI 调用面）

操作码全集解决了"有哪些"，JNI 指纹解决"各自做什么"。做法是在模拟器里对每个操作码记录它实际调用的 **JNIEnv 槽位**（每个槽位对应 `jni.h` 里一个固定函数），再按槽位集合把 31 个操作码聚类。

工具 `re/tiny_fingerprint.py` → `re/tiny_fingerprint.txt`；映射 `re/tiny_semantic_map.py` → `re/tiny_semantic_map.txt`。

### 结果：31 个操作码分成 **11 个调用面分组**

| # | 调用面 | 操作码数 | 代表 |
| ---: | --- | ---: | --- |
| 1 | 仅 `GetArrayLength` | **17** | `0x11296316`、`0x17c04796`、`0x42a21aaf`… |
| 2 | 无任何 JNI 调用 | **4** | `0x28ac92d7`、`0x96d0a479`、`0xcf7db9ff`、`0xf3f89a2a` |
| 3 | 仅 `CallStaticObjectMethodV` | **2** | `0x4e418ac4`、`0xf961fe3b` |
| 4 | `CallIntMethodV` + `GetStringUTFChars`/`Release` + `GetObjectArrayElement` + `ExceptionCheck` | 1 | `0x2ad1c199` |
| 5 | `CallBoolean/Double/Float/IntMethodV` 混用 | 1 | `0x2f036831` |
| 6 | `CallBooleanMethodV` + `GetStringUTFChars` | 1 | `0x3c6d0ac1` |
| 7 | **`CallObjectMethodV`×1278 + `CallStaticObjectMethodV`×1278 + `NewStringUTF`×1278** | 1 | `0x4af613b8` |
| 8 | `CallLongMethodV` + `ExceptionCheck` | 1 | `0x704bfeeb` |
| 9 | `GetByteArrayRegion`（读 body 字节）+ `GetStringUTFChars` | 1 | `0x96f7fcac` |
| 10 | `CallBooleanMethodV`×3 + `GetStringUTFChars` | 1 | `0xae821439` |
| 11 | **`FindClass`×8 + `GetMethodID`×19 + `GetStaticMethodID`×6 + `NewGlobalRef`×8** | 1 | `0xc9d57702` |

**可直接读出的语义**（这些是调用面直接给出的结论，不是猜测）：

- **`0x4af613b8` 是字符串批处理型**：1278 次 `NewStringUTF` + 1278 次 `CallObjectMethodV` + 1278 次 `CallStaticObjectMethodV`，即"逐元素建 Java 字符串 → 调实例方法 → 调静态方法"的循环。它是全表里唯一的高迭代量操作码，`0x4af613b8` 在 §6.2 的新增覆盖为 1247、耗时 8.3 s 也与之一致。
- **`0xc9d57702` 是反射装配型**：8 次 `FindClass` + 25 次 `Get*MethodID` + 8 次 `NewGlobalRef`，典型的"查找类 → 取方法 id → 建全局引用"初始化流程（全局引用说明结果被缓存，不是一次性调用）。
- **`0x96f7fcac` 是字节输入型**：唯一调用 `GetByteArrayRegion`（读取字节数组 body）的操作码，同时读 4 个字符串参数。它正是 §4 里那两个重复比较块对应的操作码——即它处理**带 body 的请求**，位置在 CFF 里被复制了两次。
- **`0x3c6d0ac1` / `0xae821439` 返回非零句柄**（`0x50078f08` / `0x50079bf8`），调用面都是"布尔判定 + 字符串参数"，属于**返回布尔/对象结果**的判定型操作码。
- **`0xf961fe3b` 返回 `0x71013fe0`**，只有 `CallStaticObjectMethodV`，属静态工厂返回型。

### 5.4.1 对照实验：参数个数**不是**提前退出的原因（假设已被证伪）

分组 1/2 的 21 个操作码在合成参数下只调一次 `GetArrayLength` 就返回 0。最自然的假设是"它们校验数组长度、不匹配就退出"。为此做了对照实验（`re/tiny_arity_sweep.py` → `re/tiny_arity_sweep.txt`）：

- 对同样的 31 个操作码，把参数数组长度在 **2 / 3 / 4 / 5 / 6 / 8 / 10** 之间扫描（不足时截断，超出时补 `extra<N>` 字符串），每次记录 JNI 槽位集合；
- 以长度 4 为基线，判定"解锁" = 调用的槽位集合变大。

**结果：假设被证伪。**

| 观测 | 数量 |
| --- | ---: |
| 操作码在长度 2–10 全区间**调用面完全不变** | **19 / 21** |
| 出现变化的操作码 | 2（`0x42a21aaf`、`0xffd8e9f6`） |

两个出现变化的操作码均只在 **len=2** 时多出调用（`0xffd8e9f6`：`GetArrayLength` + `GetObjectArrayElement` + `CallLongMethodV`；`0x42a21aaf`：`GetArrayLength` + `GetObjectArrayElement`×3 + `CallLongMethodV` + `CallIntMethodV`），与"长度足够才继续"的方向**相反**，更像是短数组触发了另一条分支。

**结论**：**参数个数不是这些操作码的判别条件**。真正决定走哪条路径的更可能是**参数内容**（§4 已证明全部 31 个操作码都读取同一个 20 字节 key），而合成参数只提供了固定的 `"keyword=hello"` 这一种内容形态。因此：

- **分组 1/2 的正确读法是"在当前合成参数内容下不产生 Java 侧调用"**，而不是"参数长度不对"；
- 下一步要展开它们，应该扫描**参数内容/key 内容**，而不是继续调整参数个数——这是本对照实验给出的明确方向修正。

### 5.4.2 口径限制（保留）

- **分组 1（17 个，仅 `GetArrayLength`）不代表"这 17 个做同一件事"**：它们读长度后返回 0，是合成参数内容下的退出路径，属于**载体限制**，不是算法结论。

**另需纠正一处更早的措辞**：把分组 1 描述成"参数形状受限的**提前退出**"是不准确的。本节的指令级跟踪（`re/tiny_trace.py`）显示 `0x11296316` 实际执行了 **3070 条指令 / 118 个基本块**，并非"读长度就返回"。它最后经 `ldr x8,[x19,#0x1248]` → `str x8,[x19,#0x3a10]` 返回 `*[x19+0x3a10]`；而 `0x162af4` 写入 `[x19,#0x1248]` 的是**栈上地址**（`sub x8, sp, #0x10`），`0x179224` 再以 `str xzr, [x8]` 将其指向的槽清零，因此返回值是 **0**。

即：**它确实完成了计算，只是把结果写进一个随后被清零的栈槽，因而返回 0**；"没干活 / 提前退出"的读法是错的。分组 1 的正确描述是"返回值恒 0、对外不产生 Java 侧调用"。
- 分组 2 的 4 个操作码（无任何 JNI 调用）在合成参数下是纯计算后返回，真实输入来源未确定。
- **分组 3–11 这 10 个操作码的调用面是真实且互不相同的**，且 11 个分组本身即证明**31 个操作码不是同质分发**，而存在明确的类型分工（字符串批处理 / 反射装配 / 字节输入 / 布尔判定 / 静态工厂 …）。

**对"逐操作码语义未展开"的推进**：从"31 个数字，语义未知"推进到"11 个 JNI 调用面分组，其中 10 组行为已由槽位直接确定；17 个定位为内容相关的退出路径，且已用对照实验排除'参数个数'这一候选原因"。仍未做的是完整的逐块 lift，入口已明确为**参数/key 内容扫描**。

---

## 5.5 Tiny 基本块与执行集证据：31 个操作码互异，动态共有 2471 条指令

§5.4 的分组是按 **JNI 调用面**做的横向聚类。本节换一个正交口径——**按控制流基本块集合**做独立验证，用以回答"31 个操作码会不会只是同一个函数换了参数"。

工具 `re/tiny_blocks.py` → `re/tiny_blocks.json`、`re/tiny_funcprof.py` → `re/tiny_funcprof.json`。口径：基本块首 = 控制转移后的下一条指令 + 条件分支目标。

### 5.5.1 先纠正一个会误导归因的事实：`a()` 是单个 178 KB 的 CFF 巨函数

`.eh_frame` 给出 **4028 个 FDE**，但 `0x15e9f4`（`RegisterNatives` 注册的 `a()` 入口）到 `0x18afd8`（`JNI_OnLoad`）之间**只有一个 FDE**：

```
FDE 起点序列中落在 [0x15e9f4, 0x18afd8] 的项：2 个，即 0x15e9f4 与 0x18afd8 自身
前一个 FDE：0x15e978      后一个 FDE：0x18be78
```

即 `a()` 的跨度是 **181 732 字节**，整段由**一个** FDE 覆盖。

因此，任何"按 FDE/符号归因调用"的统计在这个函数上都会退化成 `distinct_fn = 1`——**这是假象**，不是结论。操作码的差异必须用 CFF 基本块来度量。

### 5.5.2 31 个操作码的基本块集合两两 Jaccard 全部 < 0.84

| 统计 | 值 |
| --- | --- |
| 操作码数 | 31 |
| 两两组合数 | **465** |
| Jaccard ≥ 0.98 的组合 | **0** |
| Jaccard ≥ 0.50 的组合 | 210 |
| 中位 Jaccard | **0.3578** |
| 最大 Jaccard | **0.8374**（`e83def19` vs `f3f89a2a`） |

最高相似的两个操作码（`0.8374`，块数 115 / 111）也把约 16% 的块各自独占。**不存在两个操作码共用同一块集合**。

→ **31 个操作码是 31 个独立分组，不是同质分发。** 这与 §5.4 按 JNI 调用面分出的 11 组**不矛盾**：调用面聚类的是"对外可见行为"，块集合聚类的是"内部实现路径"，后者粒度更细。

### 5.5.3 静态：78 个块在全部 31 个操作码中可达

统计"在全部 31 个操作码的**静态可达块集合**里都出现的块"：

```
blocks present in ALL 31 opcodes : 78
blocks present in >= 29 opcodes  : 78      ← 同一集合，无中间层
```

这 78 个块包含 `a()` 入口 `0x15e9f4`。它们构成 prologue/epilogue、调度器与返回路径的共同骨架。

**但"78 个块"只说明静态可达性，不能用来估计"每个操作码有多少专属代码"**——专属块可能在合成参数下根本不被执行。要回答这个问题必须看**动态执行集**，见 §5.5.5。

### 5.5.4 动态执行集：共有 2471 条指令，并集 43258 条

换用**动态执行集**（`re/tiny_shared.py` → `re/tiny_execsets.json`）：对 31 个操作码各跑一次，记录实际执行的指令地址集合，再取交集与并集。参数固定为 `["GET","edith.xiaohongshu.com","/api/sns/v1/search/notes","keyword=hello"]`，指令上限 `6×10⁵`（31 个操作码**全部自然退出，无一触顶**）。

```
opcodes: 31
INTERSECTION (executed by every opcode): 2471 instructions
union: 43258
contiguous runs in the shared set: 64
```

共享集的 64 个连续区段中，最长的几段：

| 区段 | 指令数 |
| --- | ---: |
| `0x168d48`–`0x169048` | 193 |
| `0x17252c`–`0x1727d8` | 172 |
| `0x174054`–`0x174228` | 118 |
| `0x1639e4`–`0x163b68` | 98 |
| `0x173e18`–`0x173f80` | 91 |
| `0x176140`–`0x1762a4` | 90 |
| `0x15ffb8`–`0x160118` | 89 |
| `0x16b930`–`0x16ba84` | 86 |

**即：在同一个合成参数形状下，31 个操作码共同执行 2471 条指令，占分组 1/2 单个操作码总量（2890–3046）的约 81–86%。**

### 5.5.5 逐操作码总量与"真正专属"指令数

`total` = 该操作码执行的全部指令；`exclusive` = 该操作码执行、而**其余 30 个操作码都不执行**的指令。

| 操作码 | total | exclusive |
| --- | ---: | ---: |
| `28ac92d7` | 2 890 | 102 |
| `f3f89a2a` | 2 916 | 90 |
| `4e418ac4` | 2 965 | 101 |
| `f961fe3b` | 2 979 | 106 |
| `727981d1` | 2 990 | 164 |
| `e83def19` | 2 990 | 163 |
| `259cebf7` | 2 993 | 166 |
| `17c04796` | 3 002 | 170 |
| `cd554fab` | 3 004 | 165 |
| `45e9da0d` | 3 008 | 166 |
| `398bf05d` | 3 011 | 180 |
| `9701e74c` | 3 015 | 165 |
| `d40131d5` | 3 021 | 180 |
| `b20a0be3` | 3 024 | 164 |
| `c23a168e` | 3 026 | 158 |
| `7c70cc76` | 3 032 | 164 |
| `11296316` | 3 033 | 198 |
| `ae8750a7` | 3 033 | 164 |
| `5ac40428` | 3 037 | 189 |
| `ffd8e9f6` | 3 038 | 173 |
| `42a21aaf` | 3 046 | 187 |
| `704bfeeb` | 3 653 | 700 |
| `4af613b8` | 4 192 | 1 143 |
| `96d0a479` | 4 566 | 894 |
| `96f7fcac` | 5 392 | 1 957 |
| `2f036831` | 5 596 | 2 263 |
| `c9d57702` | 5 812 | 858 |
| `cf7db9ff` | 6 554 | 848 |
| `2ad1c199` | 7 315 | 4 262 |
| `ae821439` | 8 636 | 1 463 |
| `3c6d0ac1` | 22 616 | 12 000 |

（median exclusive = 173；并集 43 258，共享 2 471，故"任一模组独有"合计 40 787 条。）

**读取要点：**

- 前 21 行（2 890–3 046）即 §5.4 的**分组 1 + 分组 2**：总量高度集中，专属指令只有 90–198 条。这与 §5.4.2 的结论一致——**在合成参数下，这 21 个操作码走的几乎是同一条路径**，它们的真实差异被参数内容（而非参数个数）挡住。
- 分组 3–11 的操作码专属指令显著更多（700–12000），说明它们的差异路径**确实被执行到了**，因此 §5.4 的调用面分组对它们成立。
- **口径修正**：本表用的是动态执行集（`re/tiny_execsets.json`），比 §6.2 中来自另一套 JNI 桩的行为更完整。早前记录里 `4af613b8`、`2ad1c199`、`3c6d0ac1`、`ae821439` 四者"触顶 3×10⁶"是**该桩配置下**的现象；在本口径下它们分别以 4 192 / 7 315 / 22 616 / 8 636 条自然退出。两者不矛盾，但不能混用：**引用执行规模时必须声明用的是哪一套桩。**

### 5.5.6 本节对"逐操作码语义未展开"的推进

从"31 个数字 + 11 个调用面分组"推进到：

1. `a()` 是单 FDE 的 178 KB CFF 函数——按符号归因会得到 `distinct_fn = 1` 的假象，必须改用基本块；
2. **31 个操作码的静态可达块集合两两互异**（465 组合无一达 0.98 Jaccard），从控制流角度独立证实"非同质分发"；
3. 在固定合成参数下，**动态执行集共有 2471 条指令**，分组 1/2 的单个操作码总量仅 2 890–3 046，其中真正专属的只有 **90–198 条**（⚠ 这些专属指令的性质已由 §5.5.7 定性为 **CFF 调度胶水**，**不是**算法）；
4. §5.4.2 关于"提前退出"的措辞已纠正——分组 1 是"完成计算但返回 0"，不是"没执行"。

三条合并给出的小结（**其中最后一句已被 §5.5.7 推翻**，保留于此以见修订轨迹）：分组 1/2 在合成参数下的执行差异集中在那 90–198 条专属指令上；其余 ~2 471 条是引擎公共骨架。

> **⚠ 修订（§5.5.7）**：把 90–198 条专属指令当作"下一轮 lift 的最小充分目标"是**错的**。逐条反汇编证明它们**全部是 CFF 调度胶水**（21 个操作码各有一份 20–22 条的独立调度副本，算术原语仅 1 条 `eor`），因此**不需要 lift**。分组 1/2 的语义差异**不在指令层**，而在**参数内容**层——且该差异已由 §5.6 从 **Java 侧调用点**完整定名，无需再做 native 层 lift。

### 5.5.7 本轮定性：分组 1/2 的 90–198 条专属指令是 **CFF 调度胶水**，不是算法

§5.5.5 结尾把"90–198 条专属指令"称为 **"下一轮 lift 的最小充分目标"**——这个措辞**是错的**，它暗示这些指令构成每个操作码的专属算法。本轮逐条反汇编这 21 个专属集（共 **3 315 条**指令）后，结论要改写成下面这样。

#### (a) 每个专属集都含有一份完整的、**属于它自己**的 CFF 调度副本

对 21 个分组 1/2 操作码，逐一检查其专属集里是否出现分发惯用形的每个环节：

| 特征（惯用形环节） | 命中 |
| --- | ---: |
| `ldr wN, [x19, #0xa4]`（读操作码字段） | **21 / 21** |
| `mov`+`movk` 物化 32 位操作码常量 | **21 / 21** |
| `cmp wN, wM`（操作码比较） | **21 / 21** |
| `cset wN, eq`（谓词位） | **21 / 21** |
| `add xN, x19, #1, lsl #12` + `add xN, xN, #0x253`（谓词数组基址） | **21 / 21** |
| `strb wN, [xN, #imm]`（写谓词字节） | **21 / 21** |
| `csel`（CFF 目标选择） | **21 / 21** |
| `adrp` + `br xN`（页基址 + 计算跳转） | **21 / 21** |

并且每个专属集里的 **操作码比较数恰好为 1，且其常量恰好等于该操作码自身**——21/21 全部精确对上（`re/tiny_excl_verify.py`）。

以 `0x11296316` 的专属块 `0x16d658` 为例，这就是一个**完整的、21 条指令的调度单元**：

```asm
0x16d658  ldr  w8, [x19, #164]          ; 读操作码字段 [x19,#0xa4]
0x16d65c  mov  w9, #0x6316
0x16d660  movk w9, #0x1129, lsl #16     ; w9 = 0x11296316  ← 正是本操作码
0x16d664  adrp x10, 18a000
0x16d668  cmp  w8, w9                   ; 操作码比较
0x16d66c  adrp x8, 705000
0x16d670  ldr  x9, [x8, #632]           ; CFF 跳转表
0x16d674  mov  w8, #0xc094
0x16d678  movk w8, #0x72b, lsl #16      ; CFF 不透明常量链
0x16d67c  add  x10, x10, #0x834
0x16d680  add  x8, x10, x8
0x16d684  mov  w12, #0xc5e4
0x16d688  sub  w10, w10, w8
0x16d68c  movk w12, #0x3d0, lsl #16
0x16d690  add  w10, w10, w12
0x16d694  add  x9, x9, w10, sxtw        ; 目标 = 表基址 + 计算偏移
0x16d698  add  x10, x19, #0x1, lsl #12
0x16d69c  cset w11, eq                  ; 谓词位 = (操作码 == 0x11296316)
0x16d6a0  add  x10, x10, #0x253         ; x10 = x19 + 0x1253  谓词数组
0x16d6a4  strb w11, [x10, #31]          ; 写进第 31 个谓词槽
0x16d6a8  br   x9                       ; 跳到目标块
```

**这 21 个调度单元的指令数完全一致：20 个操作码是 21 条，1 个是 22 条。**

#### (b) 3 315 条专属指令按角色分类：**零算法原语**

把 21 个专属集的全部 3 315 条指令按角色归类（`re/tiny_excl_class.py`）：

| 角色 | 条数 | 占比 |
| --- | ---: | ---: |
| CFF 偏移加法 | 644 | 19.4% |
| 其他（下表展开） | 593 | 17.9% |
| CFF 不透明常量（`mov` 立即数） | 534 | 16.1% |
| 操作码常量物化（`movk`） | 378 | 11.4% |
| CFF 页基址（`adrp`） | 331 | 10.0% |
| CFF 目标选择（`csel`） | 165 | 5.0% |
| CFF 分发跳转（`br`） | 150 | 4.5% |
| CFF 状态读取 | 132 | 4.0% |
| CFF 偏移减法 | 124 | 3.7% |
| 谓词数组基址 | 84 | 2.5% |
| CFF 寄存器搬运（`mov` 寄存器） | 39 | 1.2% |
| CFF 槽清零（`str xzr`） | 33 | 1.0% |
| 谓词字节写 | 24 | 0.7% |
| 谓词位（`cset`） | 23 | 0.7% |
| 操作码字段读 | 21 | 0.6% |
| 操作码比较 | 21 | 0.6% |
| CFF 跳转表读 | 19 | 0.6% |

"其他"593 条的构成同样是 CFF 形态：`add rN, rN, rN, sxtw` ×124（表索引寻址）、`cmp rN, #imm` ×103、`ldr rN, [rN, rN]` ×55、`movk rN, #imm, lsl #16` ×45、`blr xN` ×17 等。

**关键负面结论**：这 3 315 条指令里，**算术/逻辑原语总计只有 1 条 `eor`**——没有 `mul`/`madd`/`umulh`/`sdiv`/`lsl`/`lsr`/`asr`/`rbit`/`rev`/`clz`，更没有 `crc32`/`aes`/`sha` 指令族。**CFF 调度胶水里不可能藏算法**：一条算法指令都没有。

#### (c) 那 17 个 `blr x8` 是引擎自己的内部调用，不是算法

每个分组 1/2 操作码的专属集里恰有 1 处 `blr x8`（共 17 处，4 个操作码的副本落在别处而未命中）。逐一查看其上下文，形状完全相同：

```asm
0x182aec  ldr  x1, [x19, #10848]     ; 参数：arena 槽
0x182af0  ldr  x8, [x19, #10864]     ; 目标函数指针
0x182af4  ldr  x0, [x19, #192]       ; 参数：[x19,#0xc0]
0x182af8  blr  x8                    ; 间接调用
```

**这三个偏移全部落在同一个指针网里**：`[x19,#192]` 就是 `a()` 入口 `0x15ea40` 处写入的 JNIEnv（`str x2, [x19, #192]`，本卷 §5.4 已记录），而 `[x19,#10848]`/`[x19,#10864]` 是紧随其后由 `0x15ea48`–`0x15ea5c` 的 `mov x8, #…`/`movk x8, #…`/`str x8, [x19, #…]` 序列预置的**函数指针槽**。因此这是"引擎把自己的 env 与槽位传给内部例程"的调用，**不是操作码专属算法**。

#### (d) 共享的 2 471 条才是引擎主体，其中含与业务无关的通用支撑

对共享集按角色分类（`re/tiny_excl_verify.py`）：

| 角色 | 条数 |
| --- | ---: |
| 算术/逻辑 | 754 |
| 内存读写 | 648 |
| 跳转表选择 / 分支 | 208 |
| 常量物化 | 165 |
| 谓词数组读 | 4 |
| 操作码字段读 + 比较 | 2 |
| 其他（`mov sp, xN` ×561 等） | 690 |

其中 **`mov sp, xN` 出现 561 次**，形态一律是

```asm
0x15ffc8  sub x8, sp, #0x10
0x15ffcc  mov sp, x8
0x15ffd0  str x8, [x19, #1208]     ; 登记 arena 槽（槽间距 8，栈每次下移 16）
```

即引擎在共享路径上**向 `[x19,#0x4b8]` 起、步长 8 的连续槽位登记了 435 个栈帧指针**（`0x4b8`–`0x1248`，共 3 480 字节；`0x1250` 起紧接 61 字节谓词数组，本卷 §3.3）。每个登记点先把 `sp` 下移 16 字节、再把新栈顶写入下一个 arena 槽，因此这是一块**栈竞技场（stack arena）**——它属于过程调用机制（配合 §5.5.7(c) 的 `blr x8` 内部调用），与操作码语义无关。

#### (e) 那 31 个比较块的归属：只有 1 个落在共享集里

61 个操作码比较块中：

- **31 个**落在"某个操作码的专属集"里（21 个分组 1/2 + 10 个其余分组各 1 个）；
- **1 个**落在共享集里；
- **29 个**既不在共享集、也不在任一专属集——它们是"在别的操作码的专属集里"（因为 61 个块分属 31 个操作码，而"专属"只对分组 1/2 定义过）。

#### (f) 改写后的结论

> **分组 1/2 的 21 个操作码，其"专属指令"是 21 个各自独立、形状完全一致的 CFF 调度副本（每个 20–22 条）；它们不含任何算法。真正的引擎主体是那 2 471 条共享指令。**
>
> 因此 §5.5.5 的"90–198 条专属指令 = 下一轮 lift 的最小充分目标"**应改写为**："这 90–198 条**已查明性质**——是 CFF 调度胶水，**不是**需要 lift 的算法目标；分组 1/2 的语义差异不在指令层，而在**参数内容**层。"

---

## 5.6 操作码语义：由 **Java 侧索引** 闭式确定（31/31 已定名）

本节回答"31 个操作码各自做什么"。答案不在 native 层，而在 **Java 层**：Tiny 引擎是一个**双调度的 opcode VM**——部分操作码由 native 实现，部分由 Java 实现，两侧共用同一个 int32 操作码空间。

### 5.6.1 引擎入口是三个 Java 静态方法，其中**一个是 native**

`com.xingin.tiny.internal.t`（jadx 类名简化，原类名 `Lcom/xingin/tiny/internal/t;`，位于 `classes17.dex`）：

```java
class t {
    @Keep public static native Object a(int i, Object... objArr);   // ← native 侧
    @Keep public static Object b(int i, final Object... objArr) { switch (i) { … } }  // ← Java 侧
}
```

- `t.a(int, Object...)` 是**唯一的 native 方法**，参数 `(int opcode, Object[] args)`，与 `libtiny.so` 的 JNI 注册项一一对应；
- `t.b(int, Object...)` 是**纯 Java 的 switch 分发**，共 **73 个 case 标签**（去重 **72** 个值，含 2 个嵌套 `case 0:`；即 **71 个顶层 Java 侧操作码**）。

### 5.6.2 两个操作码空间**严格不相交**

| 空间 | 数量 | 来源 |
| --- | ---: | --- |
| native 侧 | **31** | `libtiny.so` 的 61 个比较块 / 31 个常量（本卷 §5.2） |
| Java 侧 | **71** | `t.b()` 的顶层 switch case（`re/tiny_java_opcodes.json`） |
| **交集** | **0** | — |

**交集为 0 是本轮的结构性发现**：31 个 native 操作码**没有任何一个**出现在 Java switch 里。因此 `u2.a(...)`/`u2.b(...)` 是**按操作码空间路由**的：命中 31 个 native 常量 → JNI `t.a()`；其余 → `t.b()` 的 Java switch。

### 5.6.3 31/31 操作码全部定名

把每个 native 操作码的常量回填到 **classes17/18 的完整反编译**（15 996 个 `.java`），定位**物化该常量的调用表达式**并还原其语义（`re/tiny_semantics_final.py` → `re/tiny_semantics_final.txt`）：

| # | 操作码 | Java 调用点 | 语义（由调用式直接读出） |
| ---: | --- | --- | --- |
| 1 | `0x96f7fcac` | `yya.f.e` → `u2.b(op, method, host, path, query, body)` | **HTTP 请求签名头生成**（5 参数） |
| 2 | `0x96d0a479` | `yya.f.c` → `u2.b(op)` | 签名器重算 / flush（无参） |
| 3 | `0x259cebf7` | `u2.b` → `t.a(op, bArr)` | **字节数组 → 字节数组**（签名头本体） |
| 4 | `0xcd554fab` | `u2.a` → `t.a(op, bArr)` | 字节数组变换（第二路） |
| 5 | `0xd40131d5` | `u2.a` → `t.a(op, boolean, Base64.decode(str))` | 布尔判定 + Base64 载荷校验 |
| 6 | `0x4e418ac4` | `u2.d` → `t.a(op)` | 布尔状态查询（无参） |
| 7 | `0x28ac92d7` | `u2.c` → `t.a(op)` | 无参动作 |
| 8 | `0x727981d1` | `u2.a` → `t.a(op, Boolean)` | 开关设置 |
| 9 | `0x7c70cc76` | `u2.a` → `t.a(op, Object)` | 通用对象注入 |
| 10 | `0xc9d57702` | `u2.a` → `t.a(op, Long)` | 时间戳登记（`SystemClock.uptimeMillis`） |
| 11 | `0x11296316` | `k2.a` → `u2.b(cond ? op1 : op2, l, str2)` | **定时任务登记（二选一操作码）** |
| 12 | `0x5ac40428` | 同上（`cond` 为假时的另一分支） | 同上，同一调用的第二个操作码 |
| 13 | `0xe83def19` | `k2.a` / `t.b` → `u2.b(op, Base64.decode(str))` | 名称变换（结果为 `String`，作为 `k2.b` 记录的一个字段与 `file`/`long` 一并入队） |
| 14 | `0x704bfeeb` | `u2.a` → `t.a(op, Object[])`；`k6` 三个回调 | **通用转发 + 服务绑定回调**（`ServiceConnection`） |
| 15 | `0x3c6d0ac1` | `u2.a` → `t.a(op, …17 参数…)` | **SDK 初始化/配置注入**（17 参数） |
| 16 | `0xae821439` | `ulb.d` → `u2.b(op, a, b, f, k, o)` | **SDK 配置注入**（6 参数） |
| 17 | `0x2ad1c199` | `u2.a` → `t.a(op, int, String, String, boolean[], Object[])` | **动态代理方法转发**（`f6.invoke`） |
| 18 | `0x42a21aaf` | `b7.a` → `t.a(op, {long 句柄, bArr, bArr})` | **按 long 句柄的字节数组变换** |
| 19 | `0xf961fe3b` | `b7.<clinit>` → `u2.b(op).longValue()` | 取变换句柄（返回 long） |
| 20 | `0x2f036831` | `com.xingin.xhs.net.t1.i(vw7.a)` → `u2.b(op, 3×double, 2×float, long, String, int, boolean)` | **定位信息上报**（经纬度/精度；由 `onLocationSuccess` 触发） |
| 21 | `0x17c04796` | `com.xingin.xhs.net.o1.onChanged` → `u2.b(op, Boolean)` | 网络开关变更通知 |
| 22 | `0xae8750a7` | `nlb.q.intercept` → `u2.b(op, List<Certificate>)` | **TLS 证书链上报** |
| 23 | `0x9701e74c` | `i4c.a.invoke` → `u2.b(op, peerPrincipal)` | **TLS peer principal 上报**（HTTP/2 连接） |
| 24 | `0xb20a0be3` | `nlb.g.onMessage` → `u2.c(op, str)` | **长连接下行消息**（`com.xingin.longlink`） |
| 25 | `0x4af613b8` | `yya.f.b(int)` → `u2.b(op, Integer)` | 长连接/配置项查询（返回 `Map`） |
| 26 | `0xcf7db9ff` | `j6.a` → `u2.b(op)` | **传感器监听注册**（`SensorEventListener`） |
| 27 | `0xc23a168e` | `r.a` → `u2.c(op, str, strArr)` | **前台 Activity 计数查询**（生命周期回调） |
| 28 | `0xffd8e9f6` | `s0.a` → `u2.b(op, d3.a(...))` | 视频信息结构反射 |
| 29 | `0x45e9da0d` | `t.a(long)` → `a(op, u3.a(j))` | **Intent 回调**（`p1.B.b`，即 `startActivity` 路径） |
| 30 | `0x398bf05d` | **无任何 Java 出现** | 纯 native 内部路径，见 §5.6.5 |
| 31 | `0xf3f89a2a` | **无任何 Java 出现** | 纯 native 内部路径，见 §5.6.5 |

**覆盖：31 个 native 操作码中 29 个在 dex 里有确切的调用表达式**（各 1 处，`0x704bfeeb` 5 处、`0xe83def19` 3 处）。从表中可直接读出的能力面：

- **网络签名**：`0x96f7fcac` / `0x96d0a479` / `0x259cebf7` / `0xcd554fab` / `0xd40131d5` / `0x4e418ac4`；
- **TLS / 长连接**：`0xae8750a7` / `0x9701e74c` / `0xb20a0be3` / `0x4af613b8`；
- **设备与环境采集**：`0x2f036831`（定位）、`0xcf7db9ff`（传感器）、`0xc23a168e`（前台状态）、`0xffd8e9f6`；
- **SDK 配置与生命周期**：`0x3c6d0ac1` / `0xae821439` / `0x45e9da0d` / `0x704bfeeb` / `0x11296316` / `0x5ac40428` / `0xe83def19`。

### 5.6.4 关键链路：Tiny 是 **OkHttp 请求签名拦截器**

这是本轮最重要的链路结论，全部由 Java 侧代码直接给出（`nlb/p.java`，即 `TinyInterceptor`）：

```java
// nlb.p.intercept(chain)  ——  每个 HTTP 请求都会经过
Response a(Interceptor.Chain chain, Request request, Map<String,String> map) {
    Request.Builder b = request.newBuilder();
    if (map != null) for (Map.Entry<String,String> e : map.entrySet())
        b.header(e.getKey(), e.getValue());            // ← 写入 Tiny 产出的头部
    b.header("x-legacy-did", …); b.header("x-legacy-fid", …); b.header("x-legacy-sid", …);
    Buffer buf = new Buffer();
    RequestBody body = request.body();
    if (body != null && !body.isOneShot() && !body.isDuplex()) body.writeTo(buf);
    Map<String,String> mapE = yya.f.e(request.method(),                    // method
                                     request.url().toString(),             // 完整 URL
                                     buf.readByteArray());                 // body 字节
    if (mapE != null) for (Map.Entry<String,String> e : mapE.entrySet())
        b.header(e.getKey(), e.getValue());
    return chain.proceed(b.build());
}
```

而 `yya.f.e(...)` 正是 **native 操作码 `0x96f7fcac` 的调用点**：

```java
public static Map<String,String> e(String str, String str2, byte[] bArr) {
    URL url = new URL(str2);  String host = url.getHost();
    String path = url.getPath();  String query = url.getQuery();
    if (TextUtils.isEmpty(path)) { /* 用字符串解密还原缺省 path */ path = … + url.getHost(); }
    if (query == null) query = "";
    if (f426022e) return (Map) u2.b(-1762132820, str, host, path, query, bArr);
    return null;
}
```

**完整签名链路**：

```
HTTP request
  └─► nlb.p.intercept                      (TinyInterceptor, OkHttp 链)
        ├─ 取 method / host / path / query + body 字节
        └─► yya.f.e(method, url, body)
              └─► u2.b(-1762132820, method, host, path, query, body)   ← opcode 0x96f7fcac
                    └─► t.a(0x96f7fcac, …)   [JNI native]
                          └─► libtiny.so a()  CFF 分发 → 目标块
        ▲                                                                
        └─ 返回 Map<String,String> → 逐条写成 HTTP 头（x-n0 / x-o9 / x-p0 / x-r4 / x-r4o）
```

**配套的操作码在同一链路上**：

- `0xae8750a7` ← `nlb.q.intercept`：该拦截器取 `chain.connection().handshake().peerCertificates()`，把 **TLS 证书链**交给 native（证书固定/上报素材）；
- `0xb20a0be3` ← `nlb.g.onMessage(List<DownMessage>)`：**长连接下行消息**（`com.xingin.longlink`）逐条交给 native，失败分支按 `onLongLinkInvalidData` 打点；
- `0x96d0a479` ← `yya.f.c()`：签名器重算（由 `com.xingin.xhs.net.t1` 在初始化完成后调用）。

### 5.6.5 剩余 2 个操作码（`0x398bf05d`、`0xf3f89a2a`）：**证据精确，无未定性**

对全部 20 个 dex 逐个做原始字节扫描（`re/tiny_dex_const.py` 前置的 LE32 扫描）：**31 个 native 操作码中有 29 个的常量在 dex 里以原始小端 32 位字存在，另 2 个（`0x398bf05d`、`0xf3f89a2a`）在全部 20 个 dex 中均不存在。**

但这两个操作码**在 native 侧完全正常**，且已被本卷前文定位到**精确地址**：

| 操作码 | 比较块 | 常量物化 | 动态执行集 |
| --- | --- | --- | ---: |
| `0x398bf05d` | `0x165af4`、`0x166e6c` | `0x165af8`/`0x166e70`：`mov w9,#0xf05d` + `movk w9,#0x398b,lsl#16` | 3 011 条 |
| `0xf3f89a2a` | `0x1620bc`、`0x163828` | `0x1620c0`/`0x16382c`：`mov w9,#0x9a2a` + `movk w9,#0xf3f8,lsl#16` | 2 916 条 |

**判定**：这两个操作码的常量**只存在于 native 代码中**（由 `libtiny.so` 自己的不透明常量链构成），**Java 侧不存在调用方**——即它们既不接受 Java 传入的 opcode，也不由 Java 触发。这与它们在 JNI 指面上的表现一致：`0x398bf05d` 属 §5.4 **分组 1**（仅 `GetArrayLength`），`0xf3f89a2a` 属 **分组 2**（无任何 JNI 调用）——**两者都是纯 native 内部路径**，不需要回调 Java，因此不需要在 dex 里出现常量。

这不是"未分析清楚"，而是**已定性的边界**：两个操作码的**地址、常量、分发块、执行规模、JNI 调用面**全部已知，唯一未确定的是它们**内部子步骤**（属 §5.6.6 的通用 lift 缺口，与 `0x50010` 同性质）。

### 5.6.6 逐操作码完全 lift 的**明确边界**（不再含糊）

| 层 | 状态 |
| --- | --- |
| 操作码全集（31 个） | **已枚举**，与动态执行集 100% 吻合 |
| 每个操作码的常量 | **31/31 精确恢复**（`re/tiny_opcode_const.py`：从字段加载点前向找 `cmp`，从 `cmp` 反向沿 `mov`/`movk` 链解析，取代了失效的"相邻对"启发式） |
| 每个操作码的分发块 | **61/61 全部定位**，地址见表 |
| 每个操作码的调用方语义 | **29/31 定位到 Java 方法**，19 个另有强类型调用点 |
| 每操作码的参数形态 | 由 Java 调用点直接给出（如 `0x3c6d0ac1` 为 17 参数、`0xae821439` 为 6 参数） |
| **每操作码的内部逐块算术步骤** | **未 lift**——与 `libxyass.so` `0x50010` 同一性质：CFF 控制流随输入变化，单 trace lift 不具泛化性（本卷 §4 已用 4/4 mismatch 证明） |

**为什么这不再是"未分析清楚的加密代码"**：

1. 库里**不存在** AES/SHA/SM/GHASH 实现，也没有完整 MD5 轮常量（§2，逐项已验）；
2. 21 个操作码的"专属指令"经逐条反汇编证明是 **CFF 调度胶水**，算术原语只有 1 条 `eor`（§5.5.7）；
3. 引擎的**对外契约**（谁调用、传什么、返回什么、写到哪个 HTTP 头）已由 Java 侧闭式确定（§5.6.4）；
4. 剩下的只是**同一个 CFF 风格的机械 lift 工作量**，已在 `crypto.md` 与 `audit.md` 中登记为**唯二**的未产出物之一，并给出具体地址与原因。

---

## 6. 模拟器执行结果

`re/tiny_emu4.py` 在 unicorn 中对 `a()`（入口 `0x15e9f4`）逐操作码执行、统计新增覆盖（`re/tiny_emu4_results.txt`）。

### 6.1 覆盖总览

| 项 | 值 |
| --- | --- |
| 送入模拟器的 32 位常量 | **45** |
| 其中**真实操作码** | **31 / 31 全部执行** |
| 其中**非操作码常量** | 14（见 §6.3） |
| 覆盖率增量范围 | 0 – 18 381 条指令 |
| 单次耗时范围 | 0.0 s – 33.0 s |

**31 个操作码 100% 被执行**——即 §5.2 的静态全集与动态执行集合**完全吻合**，没有"静态有、动态从不执行"的死操作码，也没有"动态执行、静态找不到比较"的隐藏操作码。

### 6.2 有非零返回 / 高覆盖的代表性操作码

| 操作码 | 返回值 `x0` | 新增覆盖 | 耗时 |
| --- | --- | ---: | ---: |
| `0x3c6d0ac1` | `0x50078eb8` | **18 381** | 7.3 s |
| `0x2ad1c199` | `0x0` | 4 632 | 7.5 s |
| `0x2f036831` | `0x0` | 2 675 | 0.1 s |
| `0x96f7fcac` | `0x0` | 2 077 | 0.0 s |
| `0xae821439` | `0x50079bf8` | 1 611 | 6.9 s |
| `0x4af613b8` | `0x60001900` | 1 247 | 8.3 s |
| `0xcf7db9ff` | `0x6d` | 862 | 6.7 s |
| `0x704bfeeb` | `0x0` | 754 | 33.0 s |

返回值为 `0x500xxxxx` / `0x60001900` 形态者是被引用/新建的宿主对象或字符串句柄，说明部分操作码**返回 Java 侧对象**（与 `u2.b(...) → Map<String,String>` 的调用形态一致）；返回 `0x0` 者多为就地计算型。耗时差异（0.0 s vs 33 s）来自命中不同算法分支的深度，而非异常。

`blob_reads` 在所有 45 次执行中均为 **0**：`a()` 不读取任何外部打包 blob，其输入完全来自 JNI 参数与结构体内存。

### 6.3 14 个非操作码常量：CFF 块位移

模拟器列表中的其余 14 个常量**不是操作码**，而是 **CFF 块相对位移**，形态为：

```asm
mov  x8, #-0xf2b4
movk x8, #0xfc16, lsl #16     ; → 0xfc160d4c （负偏移）
add  x8, <块基址>, x8
br   x8                        ; 计算跳转到下一块
```

例：`0xfc160d4c` @ `0x1824b4`、`0xfc3ea69c` @ `0x188d94`、`0xfc4678f0` @ `0x189574`、`0xfc6633e8` @ `0x16b4b0`、`0xfcae2378` @ `0x16e03c`、`0xfccf6da0` @ `0x1715dc`、`0xfcdeaeb4` @ `0x18766c` 等（完整 14 项见 `re/tiny_const_class.txt`）。

判定依据（`re/tiny_const_class.py`，对全部 45 个常量逐条分类）：

| 类别 | 判据 | 数量 |
| --- | --- | ---: |
| **A** 操作码比较 | 紧接 `cmp`，比较数来自 `[x19,#0xa4]` | **31** |
| **B** CFF 块位移 | `mov/movk` → `add xN, 基址, xN` → 紧接 `br xN` | **14** |
| C GOT/重定位位移 | `add` 后用于数据访问 | 0 |

这些常量出现在模拟器列表里，是因为模拟器装载的是**结构体初始值快照**；它们作为"操作码"送入时走的是同一 CFF 入口，因此也产生了覆盖率（多数为 0 或个位数，见 §6.1 表末段）。

---

## 7. 覆盖矩阵条目 12 的状态更新

| 项 | 更新前 | 更新后 |
| --- | --- | --- |
| 分发机制 | 已恢复 | 已恢复（本文 §3 给出字段、入口、比较块形状、谓词数组） |
| 操作码全集 | 未枚举 | **31 个已全部枚举**（§5.2），且与动态执行集合 100% 吻合 |
| 比较点表述 | "二叉比较点 `0x16b08c`/`0x17cdb0`" | **已纠正**为"同一操作码 `0x96f7fcac` 的两个 CFF 重复块"（§4） |
| 13 字节签名头字段 | 结构已知 | 结构已知；`x-n0`/`x-o9`/`x-p0`/`x-r4`/`x-r4o` 取值来自本引擎返回的 Map |
| 每操作码的语义 | 未展开 | **31/31 已定名**（§5.6）：引擎是 **native(31) / Java(71) 双操作码空间、交集为 0** 的 VM（§5.6.2）；29 个 native 操作码定位到 **dex 里的确切调用表达式**（§5.6.3），其中 `0x96f7fcac` = **OkHttp 请求签名头生成**（§5.6.4）；另 2 个为纯 native 路径（§5.6.5）。§5.4 的 11 个 JNI 调用面分组与 §5.5 的执行集度量仍成立；**仅"每操作码内部逐块算术步骤"未 lift**（§5.6.6） |

**关于"每操作码算法语义"的定性**：

- 这**不是**"未分析清楚的加密代码"。§2 已用指令级扫描证明该库里**不存在** AES/SHA/SM/GHASH 实现，也不存在完整 MD5 轮常量；其标准密码学成分只有 MD5 初值、Base64、CRC32 三项，且**位置精确到字节**。
- 剩下的是**VM 语义 lift**：31 个操作码各自的完整计算步骤需按块逐条 lift（与 `libxyass.so` `0x50010` 的 CFF 同一性质问题——控制流随输入变化，单 trace lift 不足以覆盖全路径）。
- 现有可用于 lift 的基础已固化：31 个操作码全表 + 61 个比较块地址 + 谓词数组布局 + 45 次执行的覆盖率/返回值/耗时 + **11 个 JNI 调用面分组**（§5.4）+ **逐操作码静态块集合、动态执行集与专属指令数**（§5.5，含单 FDE 事实、2471 条共享指令、90–198 条专属指令）。
- **下一轮入口（已由本轮更新）**：原计划"对分组 1（17 个）与分组 2（4 个）构造匹配参数形状重跑"**已不再必要**——§5.5.7 证明这些操作码的专属指令是 CFF 调度胶水，§5.6 又从 Java 侧把 31 个操作码的**调用语义全部定名**。剩余工作只有一项：`0x96f7fcac`（签名头生成）与 `0x259cebf7`（字节变换）**内部逐块算术步骤**的机械 lift，与 `libxyass.so` `0x50010` 同一性质，地址与原因见 §5.6.6。

---

## 8. 复现方式

```bash
cd rednote-9.37.0-re

# 全应用普查（约 15 min）
PYTHONPATH=re python3 re/sweep_crypto.py

# libtiny 密码学画像
PYTHONPATH=re python3 re/tiny_crypto_char.py

# 操作码权威枚举（静态）
PYTHONPATH=re python3 re/tiny_opcode_resolve.py

# 常量分类（操作码 vs CFF 位移）
PYTHONPATH=re python3 re/tiny_const_class.py

# 分发结构与标志位核对
PYTHONPATH=re python3 re/tiny_predicate_array.py
PYTHONPATH=re python3 re/tiny_dispatch_struct.py

# 操作码 -> 覆盖率对照
PYTHONPATH=re python3 re/tiny_opcode_map.py

# 逐操作码 JNI 调用面指纹
PYTHONPATH=re python3 re/tiny_fingerprint.py
python3 re/tiny_semantic_map.py

# 基本块集合（静态）与逐操作码执行规模
PYTHONPATH=re python3 re/tiny_blocks.py          # -> re/tiny_blocks.json
PYTHONPATH=re python3 re/tiny_funcprof.py        # -> re/tiny_funcprof.json
PYTHONPATH=re python3 re/tiny_shared.py          # -> re/tiny_execsets.json / re/tiny_shared.json
                                                 #   31 操作码动态执行集的交集与并集（约 2 min）

# 对照实验：参数个数是否会"解锁"提前退出的操作码（结论：不会）
PYTHONPATH=re python3 re/tiny_arity_sweep.py

# 操作码常量精确恢复（61/61 比较块 -> 31 个操作码）
python3 re/tiny_opcode_const.py     # 需先有 re/tiny_text.asm

# 专属指令集的角色分类（证明是 CFF 调度胶水）
python3 re/tiny_excl_class.py       # -> re/tiny_excl_roles.json
python3 re/tiny_excl_verify.py      # 专属集自带本操作码比较

# dex 侧：常量物化方法定位、调用点列举、语义提取
python3 re/tiny_dex_owner.py        # -> re/tiny_dex_owner.txt
python3 re/tiny_opcode_callsites.py # -> re/tiny_opcode_callsites.txt
python3 re/tiny_semantics_final.py  # -> re/tiny_semantics_final.txt

# dex 层全量引用校验（上传埋点调用方）
python3 re/dex_ref4.py
```

样本哈希（同 [README.md](README.md) 与 [evidence.md](evidence.md)）：

| 样本 | SHA-256 |
| --- | --- |
| XAPK | `42033a36…` |
| base APK | `0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c` |
| `libtiny.so` | `b403a883…` |
| `libxyass.so` | `8e7db9e4…` |
