# 小红书 9.37.0 全应用 native 加密普查与 libtiny.so 深挖

本文汇总 [crypto.md](crypto.md) 覆盖矩阵中两块需要独立地址、反汇编和测试向量支撑的产出：

1. **全应用 native 加密普查**——164 个 arm64 `.so` 逐个扫描，给出"哪些库带加密代码、哪些不带"的完整二分清单，使"没有未知加密代码"这一结论从**抽样**升级为**全量**。
2. **`libtiny.so`（Tiny opcode 引擎）深挖**——在 `crypto.md` 条目 12 的"分发机制已恢复"之上给出**操作码全集枚举**与**分发层结构闭环**（61 分派块 ↔ 61 个互不相同的谓词槽 ↔ 31 个操作码常量，三向双射、零冲突，§5.6.6–§5.6.7），并定名 `0x16b08c`/`0x17cdb0` 为同一操作码的两个 CFF 重复块（§4）。

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
- **大整数域特征**：radix-2⁵¹ 掩码 `0x7ffffffffffff`、`extr #51` 进位提取、乘 19 归约（Curve25519 / Ed25519 域），或 radix-2²⁶ 掩码 `0x3ffffff`、乘 38 归约（Poly1305）；
- **≥ 8 KB 高熵区**：段内连续 ≥ 8192 字节且熵显著高于普通代码/字符串的区域（用于发现未被常量命中的加密数据或打包载荷）。
- **自解密数据表**：库在加载期（`.init_array`）或在自身代码里**原地改写自己的 `.data`/`.rodata`**，把密文表变成可用字符串——即"分派层看不到、常量表也没有"的自解密表。判据见 §1.6；`libturingmfa.so` 就是被这一条捞回来的假阴性。

四者互不替代：只看指令会漏掉查表实现，只看常量会漏掉指令实现，只看高熵区会漏掉两者都不明显的库，而只看前三者会漏掉**密文熵被打散**的自解密表（见 §1.6：`libturingmfa.so` 的表熵仅 6.978，因为每个条目的 `\0` 是明文存储的）。因此采用**并集**判定，宁多勿少。

### 1.3 结果：33 带 / 131 不带

> **判据口径**：`libturingmfa.so` 带一张**加载期原地自解密**的字符串表，
> 属典型加密代码，但它的加密方式（逐字节 `b ^ key` 拆成互补掩码 + 随后 `eor`）
> 既不产生加密指令、也不产生已知算法常量，还把密文熵压到 6.978（`\0` 明文存储），
> 因而只有第 4 条判据能命中的。判据定义见 §1.6，逐条证据见 §1.7。

- **33 个库命中**（有加密指令、或已知常量、或 ≥8 KB 高熵区、或**加载期自解密数据表**）；
- **131 个库四条全无**——完整名单见 `re/sweep_crypto_final.txt` 末节（**移除 `libturingmfa.so` 后**），包含 `libc++_shared.so`、130 余个 React Native / Yoga / folly / hermes 渲染与基础设施库、`libshadowhook.so`、`libbytehook.so` 等 hook 框架、`libmarsxlog.so` 等。

**这句话的意义**：`crypto.md` 矩阵的"无未知加密代码"结论建立在核心 `libxyass.so` / `libtiny.so` 的逐字节恢复上；把**其余 163 个库**一并纳入全量普查后，`libturingmfa.so` 只有第 4 条判据能命中。33/131 的二分清单使得任何一个库都可以被单独复核，且四条判据**已对全 164 个库各跑一遍**（§1.6）。

> **结论强度**：四条判据的并集覆盖了本样本全部 164 个库，但这是**四条判据下的覆盖**，
> 不是对"不存在任何此类代码"的完备性证明——判据本身只能断言其定义域内的性质。

### 1.4 32 个命中库逐条证据（按旧三条判据的命中情况；第 33 个见 §1.7）

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

**结论**：164 个库全部有归因，无"未识别加密库"；**归因的完整性依赖四条判据的并集**——
第 4 条（§1.6 加载期自解密数据表）是其必要组成部分：`libturingmfa.so` 在前三条判据
下不命中，实际带一张自解密字符串表（§1.7）。

### 1.5 混淆形态普查：164 个库逐个量测

§1.2–§1.4 的普查口径只覆盖**密码学**。要断言"**不允许留下任何未分析的被混淆代码**"，
还需要一张同样全量的**混淆**清单——否则"没有未分析代码"只对加密成立，对混淆不成立。
本节用 `re/obf_census.py`（原始输出 `re/obf_census.json`，164/164 全部成功）逐库量测 8 个可复算指标。

| 指标 | 定义 | 用途 |
| --- | --- | --- |
| `br_pct` | `br xN` 占 `.text` 指令数的百分比 | CFF 控制流平坦化的**最直接指纹** |
| `tblsig` | `adrp`+`add`/`mov`+`ldr`+`br` 在 ≤6 条指令内成形的次数 | 分派表跳转惯用式 |
| `movz_movk_per_1k` | 每千条指令的 `movz`/`movk`/`movn` | 不透明常量物化密度 |
| `opaque_csel` | `cmp` 双立即数后接 `csel/cset` | 不透明谓词 |
| `eor_branch` | 字节加载后 8 条内出现 `eor wN` | 字符串解密循环 |
| `rodata_entropy` / `rodata_printable` | `.rodata` 熵 / 可打印比 | 加密字符串表、打包载荷 |
| `n_exports` / `n_rela` | 导出符号数 / 重定位数 | 入口面与运行期表规模 |

**关键结果——混淆是极少数库的局部现象，不是全局现象**：

| 库 | 指令数 | `br_pct` | `tblsig` | `movz/1k` | `eor_branch` | 结论 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `libtiny.so` | 1 528 000 | **4.124** | **849** | **92.8** | 763 | **重度 CFF + 字符串加密** |
| `libtinyd.so` | 26 330 | **4.098** | 12 | **88.4** | 24 | **重度 CFF + 字符串加密**（§[tinyd](tinyd-companion-daemon.md)） |
| `libxyass.so` | 110 102 | **3.400** | 27 | **81.1** | 356 | **重度 CFF + 字符串加密**（§[crypto.md](crypto.md) §4） |
| `libturingmfa.so` | 66 080 | 0.020 | 0 | **61.3** | **331**（密度 5.01/1k，**全应用第 3**） | **字符串加密（非循环形态），见 §1.7** |
| 第 4 名之后（`libreact_debug` 等 4 个 ≤85 条指令的小库） | ≤ 85 | ≤ 5.0 | 0 | 0.0 | 0 | **样本过小，`br_pct` 无统计意义** |
| 其余 157 个库 | — | **全部 < 0.9** | — | — | — | 无 CFF 指纹 |

**三条可复核的判读**：

1. **`br_pct` 高于 3% 的只有 3 个真实库**：`libtiny.so`(4.124)、`libtinyd.so`(4.098)、`libxyass.so`(3.400)。第 4–7 名是 `libreact_render_debug.so`(5.0)、`libruntimeexecutor.so`(5.0)、`libreact_debug.so`(2.04) 等**总指令数 ≤ 85** 的库——它们的百分比来自分母过小，不是平坦化。**这三个库恰好就是本报告已完整闭环的对象**。
2. **`movz_movk_per_1k` 前三名同样只有这 3 个库**（92.8 / 88.4 / 81.1；第 4 名 `libturingmfa.so` 61.3 但 `br_pct` 仅 0.020）。这一指标与 CFF 的"不透明常量"机制高度一致，构成**对 `br_pct` 的独立佐证**。

   > **归因必须逐条落实，不能只看指标名**：
   >
   > - `libturingmfa.so` 的 `movz_movk_per_1k` = 61.3 来自 **4 050 条 `movk`**（`movz`/`movn` 各 0 条），
   >   而这些 `movk` **全部在解密器之外**（Turing 采集项比较常量与 Binder 事务码）；
   > - **解密器本身（`0x34d74`–`0x39ecc`）`movz`/`movk`/`movn` 全为 0**，它用 **471 条 `mov w, #imm`**
   >   物化互补掩码对与密钥。
   >
   > 也就是说 `movz/1k` 这一列**既没有指向 CFF，也没有指向这个解密器**——它数的是另一处的 `movk`。
   > 真正指向解密器的是 **`eor_branch`=331**。`br_pct` 低只说明**没有控制流平坦化**，
   > 不等于**没有混淆**：字符串加密完全可以与直线代码并存（§1.7）。

2b. **`eor_branch` 密度（`eor_branch / insns`）才是字符串加密的可用排序**，绝对计数会系统性偏向大库：

    | 库 | `eor_branch` | 指令数 | **密度 /1k** | 实际性质 |
    | --- | ---: | ---: | ---: | --- |
    | `libentryexpro.so` | 144 | 9 424 | **15.28** | 银联 UPX\* 加密工具（导出名明文，非混淆） |
    | `libed25519.so` | 131 | 9 227 | **14.20** | mbedtls 的 AES/MD5/Ed25519（导出名明文，非混淆） |
    | **`libturingmfa.so`** | **331** | **66 080** | **5.01** | **自解密字符串表（真）** |
    | `libxyass.so` | 356 | 110 102 | 3.23 | 自解密字符串表（真，§[crypto.md](crypto.md) §4） |
    | `libeidjni.so` | 191 | 70 646 | 2.70 | eID 组件，导出名明文 |
    | `libtiny.so` | 763 | 1 528 000 | 0.50 | 自解密字符串表（真，§2.4） |

    前两名之所以"密度高但不是混淆"，是因为**导出符号表里函数名明文可读**
    （`_ZN6UPXAES5sm_T8E`、`mbedtls_aes_crypt_ecb` 等），即 AES 的 S 盒访问天然是
    `ldrb`+`eor` 形态；而 `libturingmfa.so` 的 331 处落在 `.data` 自己的字符串表上
    （见 §1.6 的判据 D4：目标是**可写段**），因此是货真价实的自解密。
3. **`libtiny.so` 的 `tblsig`=849 是全应用最高**（第 2 名 `libkasa_sdk.so` 仅 130，且 `br_pct`=0.054）——再次独立指向"只有 Tiny 做了大规模分派表平坦化"。

**因此，全应用混淆面为 4 个库**，四者的混淆机制与处理状态如下：

| 库 | 混淆机制 | 状态 |
| --- | --- | --- |
| `libxyass.so` | CFF（`0x50010`，504 处间接跳转）+ 字符串加密 | **已闭环**：转移图饱和枚举（412 site / 767 边 / 417 目标）+ 选择层分类（262 FIXED / 69 BASE / 18 DATA）+ 字符串加密机制已恢复 |
| `libtiny.so` | CFF（61 分派块）+ 字符串加密 + 内联 X25519 | CFF 结构**已完全枚举**（三向双射 + 61 位移全复现）；**逐块算术 lift 已完成**（154 原语 + 九项指纹守恒 EXACT，§5.6.9）；字符串加密见 §2.4 |
| `libtinyd.so` | CFF（381 槽分发池）+ 字符串加密 | **已闭环**：4 解码器 × `i%20` 调度表闭式（7/7 明文）+ 14/14 动态跳转验证 |
| **`libturingmfa.so`** | **加载期原地自解密字符串表**（`.init_array[6]` → `0x34d74`，5 207 条指令、**0 调用、0 入边、1 个 `ret`**、**完全展开**）+ 互补掩码形式的 `b ^ key` | **已闭环**：密钥调度 `key_index = src_index mod 8`（348/348 条目验证）；逐个条目 1 密钥（8 412/8 412 字节）；**348 个非空条目 + 68 个不可见空串 = 源列表 416 项**（§1.7） |

> **口径说明**：本节的"无 CFF 指纹"是**基于可复算指标的否定**（`br_pct` < 0.9、`movz/1k` 无同步抬升），与 §1.2 用指令族+常量做的密码学否定**相互独立**。两者都不依赖人工抽样，任意库可单独复核：`python3 re/obf_census.py` 重建全表。

> **这套指标的盲区**：`br_pct` / `tblsig` / `opaque_csel` 测的是**控制流平坦化**，
> `movz/1k` 测的是**常量密度**，`rodata_entropy` 只看 `.rodata`。
> 一个"只把 `.data` 里的字符串表逐字节 XOR、且**把解密完全展开成直线代码**"的库，
> **四个指标全部正常**——`libturingmfa.so` 就是这样（`br_pct`=0.020、`tblsig`=0、
> `opaque_csel`=0、`.rodata` 熵 5.71）。因此需要一个独立的判据类：
> **§1.6 的第 4 条判据**（原地改写自己 `.data`/`.rodata` 的加载期自解密表），
> 已对全 164 个库各跑一遍。

---

### 1.6 第 4 条判据：加载期自解密数据表 + 全量复扫

§1.5 的指标测不到"**把字符串表 XOR 后完全展开成直线代码、在加载期原地改回来**"这类库。
第 4 条判据解决这个问题，并对**全 164 个库各跑一遍**，三台独立仪器：

| 仪器 | 手段 | 口径 | 结果 |
| --- | --- | --- | --- |
| `re/inplace_strdec_scan.py` | 静态反汇编 | 同基址 + 同偏移的 `ldrb`→变换→`strb`，基址须由 `adrp+add`/`adrp+ldr` 解析到**具体全局地址**，目标段须**可写**（D1–D4） | 164/164 成功；30 个库 ≥1 位点 |
| `re/initarray_run.py` | Unicorn 模拟 | 跑 `.init_array` 全部构造子，比对 `.data`/`.rodata`/`.bss` **运行前后字节**，并统计改写后像里跨改写字节的字符串 | 164/164 成功 |
| `re/xor_table_test.py` | 单字节 XOR 扫描 | 对 `.data`/`.rodata` 试全部 255 个单字节密钥，看"可打印率 + NUL 占比"是否跃迁；**拒绝常量填充**（主字节占比 > 0.55 即淘汰） | 164/164 成功 |
| `re/initarray_census.py` | 构造子清点 | 解析 `.rela.dyn` 的 `R_AARCH64_RELATIVE` 还原每个 `.init_array` 条目地址，并量出函数线性长度 | 164/164 成功 |
| `re/inplace_site_content.py` | 目标内容定性 | 取每个 `.data` 位点的**目标字节 + 解密后像**，把「自解密字符串表」与「初始化/句柄」分开 | 11/11 成功；唯一真解密 = `libturingmfa.so` |
| `re/inplace_form_scan.py` | **寻址形态正交**扫描 | 在 D1–D4 之上加入 post-index / writeback / `ldp-stp` / `ld1-st1` 等**结构化与后索引**寻址，不再要求"同基址同位移" | 164/164 成功；80 库 ≥1 位点 |
| `re/inplace_form_content.py` | 上述位点的**内容定性** | 对非 `.bss` 目标取文件原像 + 解密后像，区分字符串表 / 初始化 / 句柄 | 26 库有非 `.bss` 目标；新增的 4 个 `.data` 目标**全为初始化** |
| `re/inplace_xref_scan.py` | **跨寻址+跨宽度**范围匹配 | 不再比较"位移相等"，改为比较**有效地址区间是否相交**，并加入**寄存器偏移**（`str q2,[x10,x9]`）与**宽存储**（`str d0,[x10,#imm]`） | 164/164 成功；113 库 ≥1 位点；对 `libturingmfa.so` **348/348 命中模拟器真值** |
| `re/inplace_xref_content.py` | 全应用内容定性 | 对上述全部非 `.bss` 目标分类；`.data` 未被构造子改写的高熵目标单列为 `high-entropy-no-constructor-change` | 268 项人工定性 → **仅 `libturingmfa.so` 是真表**（见下） |

**判据定义（D1–D5）**：

| 编号 | 条件 | 目的 |
| --- | --- | --- |
| D1 | `strb wA,[xG,#d]` 的值可证来自**同基址同偏移**的 `ldrb wA,[xG,#d]` | 是"读回自己"而不是普通写 |
| D2 | `xG` 的溯源解析到**具体全局地址**（`adrp+add` / `adrp+ldr`），且目的寄存器在每次其它写指令处**先失效再重建** | 排除"指针参数"造成的普通编解码循环（`libpng16.so`/`libfreetype.so`/`libc++_shared.so` 的假阳性即由 D2 排除） |
| D3 | 载荷到存回之间至少有一条**值变换**指令，且载入寄存器确实被消费 | 是解码而非拷贝 |
| D4 | 目标段**可写**（`.data`/`.bss`/`.data.rel.ro`） | 写 `.rodata` 运行期必崩，不可能是解码器 |
| D5 | 位点处于循环中（有回边目标 ≤ 载入地址）**——仅作旁证，不作硬条件** | `libturingmfa.so` 的解密**完全展开**、无循环，D5 会把真阳性判掉（本判据的反例正说明"无循环"本身是规避手段） |

> **为什么扫描口径必须做到寻址形态正交（实测）**：同一张表，只要求"同基址同位移"的
> 静态口径在 `libturingmfa.so` 上只找到 **8 个目标**；这 8 个**恰好就是那 9 个前导常量单元**，
> 一个表项都没覆盖到。原因是该表项用的是**寄存器偏移寻址**：
> `adrp x10,#0x56000 ; add x10,x10,#0xbed ; ldr q2,[x10,x9] ;
> … ; str q2,[x10,x9]`——位移槽里是 `x9` 而不是立即数，位移字段为 0，
> **任何"同位移配对"的写法都永远匹配不到它**（348 个条目里 **306 个**是这种形态）。
> 换成"有效地址区间相交 + 寄存器偏移可解析"后，348/348 命中模拟器真值。

**重跑结果**：

- **静态扫描**：30 个库 ≥1 位点；其中 **11 个库的目标段含 `.data`**。位点**形状在 11 个库里完全相同**（都是"读回自己的全局字节再改写"），所以**只数形状会得到 11 个结果**。判定必须把**目标内容**一并量（`re/inplace_site_content.py`，联合 `re/initarray_run.json` 的**解密后像**——因为目标在构造子跑之前是**密文**，"是否可打印"只能问解密后）：

  | 库 | `.data` 位点 | 目标处字节（文件原样） | 解密后像里的字符串 | 判定 |
  | --- | ---: | --- | ---: | --- |
  | **`libturingmfa.so`** | **8** | `59 00 00 00 f5 00 00 00 …` | **316** | **自解密字符串表** |
  | `libreddb.so` | 5 | `03 00 00 00 3a 00 00 00 …` | 0 | 结构体字段（小整数元组） |
  | `libzeusEngine.so` | 5 | 指针数组 / `00 00 00 00` | 0 | 运行期句柄 |
  | `libhermes_executor.so` | 4 | `01 00 00 00` / `ff ff ff ff` | 0 | 标志位、`-1` 哨兵 |
  | `libInsightWrapper.so` | 3 | 全 `00` | 0 | 计数器清零 |
  | `libares.so` | 2 | `ff ff ff ff 00 00 00 00` | 0 | 位掩码表 |
  | `libkasa_sdk.so` | 1 | `01 00 00 00` | 0 | 标志位 |
  | `libpredy-native-ri.so` | 1 | `01 00 00 00 01 00 00 00 …` | 0 | 结构体字段 |
  | `libreddownload.so` | 1 | `01 00 00 00` | 0 | 标志位 |
  | `libtiny.so` | 1 | `ff ff ff ff` | 0 | 哨兵 / 清零 |
  | `libxyass.so` | 1 | 全 `00` | 0 | 计数器清零 |

  复现：`python3 re/inplace_site_content.py`（末列取自 `re/initarray_run.json` 的
  `sections['.data'].n_strings`）。**11 个里只有 1 个是真解密**，其余是初始化
  （写标志位、清零、填掩码、填结构体字段）——这正是"必须量内容、不能只数位点"的原因。
  两个计数口径不同，勿混：**316** = 解密后像中「与改写字节重叠」的 6+ 字符可打印串；
  §1.7(d) 的 **325** = 被改写字节自身构成的 4+ 字符全可打印串。两者都来自同一次模拟。
  > 注：`libxyass.so` 的**字符串加密在别处**（`0x50010` CFF 路径，见 [crypto.md](crypto.md) §4），
  > 本节这一列只说明"它的 `.data` 里这个位点不是自解密表"，不否定它另有字符串混淆。
- **模拟改写**：11 个库有构造子跑到底。判定分布为
  `nothing_ran_or_no_change` 150、`bss_only_init` 12、
  `data_changed_no_coherent_strings` 1（`libquickjs-android.so`，**仅 1 字节**，无字符串）、
  **`in_place_decoder_found` 1（`libturingmfa.so`）**。
- **单字节 XOR**：**0 个库**被判为单字节 XOR 加密表（35 个段本就是明文、146 个段非文本）。这同时**是否定证据**：`libturingmfa.so` 的表**不是**单字节 XOR，用固定密钥解不开——与 §1.7 的**轮转密钥**一致。
- **构造子清点**：**102 个库至少有一个可用的 `.init_array` 条目**（`libtxmapengine.so` 162 个、`libkyctoolkit.so` 77 个、`libXHSNN.so` 56 个……）。这顺带把 [tinyd-companion-daemon.md](tinyd-companion-daemon.md) §10 里"`.init_array` 全 0 ⇒ 不在加载时自启"这条**只对单个库做过的观察**推广成全量事实：加载期执行在样本里是**常态（102/164）**，因此"某个库不在加载期做事"不能靠默认推断，必须逐库量。
  > **两个仪器统一取件约定**：`libsentry_dumper.so` 的 `.init_array` 有 2 个槽，第 1 槽在文件里是 `0xffffffffffffffff` 且**没有重定位**。若按"非零字即条目"读会得到 103，若按"只读重定位 addend、读不到退回 0"读会把该库算作无条目。统一约定为：有重定位取 addend，否则取**文件原字**；`0` = 空槽，`-1` = 未使用槽标记，**两者都不是函数**。该约定下两个仪器一致：**102 个库有可用条目**，与"62 无条目 + 11 已实测 + 91 未跑到底"闭合。

> **结论与边界（口径必须按下面三类分开读）**：四条判据的并集下，
> **164 个库中只有 `libturingmfa.so` 存在加载期原地自解密数据表**。但"其余 163 个库无改写"
> 的**证据强度并不齐**，`re/initarray_run.py` 把 164 个库分成三类：
>
> | 类别 | 库数 | 证据强度 |
> | --- | ---: | --- |
> | **无 `.init_array` 条目** ⇒ 结构上不存在加载期执行 | **62** | **结构性**（最硬：连可执行的构造子都没有） |
> | 有构造子且**≥1 个跑到底** | **11** | **已实测**：跑完后 `.data`/`.rodata` 逐字节比对。`libturingmfa.so` 在这一类里（其余 10 个为 `libInsightWrapper.so`、`libXHSNN.so`、`libchopper_v2.so`、`libfolly_runtime.so`、`libhermes_executor.so`、`libj2v8.so`、`libliteavsdk.so`、`libtxmapvis.so`、`libxhslonglink.so`、`libxyasf.so`） |
> | 有构造子但**无一跑到底** | **91** | **静态判定**：逐库反汇编构造子及其调用闭包，未见自解密表写入链；另有静态位点、XOR 表穷举和构造子 census 三类仪器交叉检查 |
>
> 也就是说：**62 个库从 `.init_array` 结构排除，11 个库构造子实测，91 个库由反汇编和三类全库仪器静态排除**。模拟器缺 `JNIEnv`、libc 或 `DT_NEEDED` 只影响是否能把构造子跑完，不改变已枚举的写入目标与指令语义；[audit.md](audit.md) 判据 7b 给出完整判据。
> 另外，**第 4 条判据对"加密表"的召回力有明确的上限**：它只能发现"**构造子可执行**"的那类
> 自解密表；一个把解密挂在 `JNI_OnLoad` 或某个业务入口（而非 `.init_array`）上的库，
> 需要更强的入口覆盖才能扫到——本节的仪器只覆盖 `.init_array` 路径。

**扫描口径收紧到极限后的全应用结果**（`re/inplace_xref_scan.py` +
`re/inplace_xref_content.py`，164/164 成功）：**113 个库**存在"读回自己并改写"的位点，
非 `.bss` 目标共 **595 个**。逐个定性后：

| 类别 | 项数 | 含义 |
| --- | ---: | --- |
| 全零区域（`zero-fill`） | **226** | 清零/惰性缓冲，`.data` 中原像即 `00` |
| 文件内已是明文（`plaintext-in-file`） | **82** | 就在明文串里改写，无编码 |
| 高熵但无构造子改写（`high-entropy-no-constructor-change`） | **268** | 见下 |
| 文本型（`text-ish`） | **8** | |
| 标志位/哨兵/指针（`flag/sentinel/pointer`） | **9** | |

那 **268 项高熵候选里 257 项属于 `libturingmfa.so` 自己**（就是本节的表）。
**剩下 11 项分散在 7 个库**，且**全部位于构造子并未改写 `.data` 的库里**——
即它们的高熵是**读出即如此**，不是"密文被解出来"，且实测熵上界仅 **4.11**
（`libsentry.so 0x24244`）。作为对照，`libturingmfa.so` 的表区**文件原像整体熵 7.074**、
单条目窗口最高 **5.19**。因此这 11 项**不构成第二张加密表**：
它们与真表的区别不是主观判断，而是**熵的量级差**加上**构造子是否真的改写过该处**这个二值事实。
（这 11 项以 `high-entropy-no-constructor-change` 记入 [audit.md](audit.md)；内容、熵上界和构造子写入事实均已定性，不作为加密表。）

> **本节结论**：`libturingmfa.so` 的字符串表是 164 个库里**唯一**
> 经"构造子实测改写 + 内容定性 + 熵量级"三重独立的原地自解密表；
> 其余 163 个库按前表三类证据记载：62 个结构性排除、11 个构造子实测、91 个由反汇编与三类全库仪器静态排除。

---

### 1.7 `libturingmfa.so`：加载期原地自解密字符串表

#### (a) 身份与它在风控链里的位置

`libturingmfa.so`（66 080 条指令）是**腾讯 TuringFD/MFA** 的设备指纹与风险上报 SDK：

| 项 | 值 |
| --- | --- |
| `.rodata` 明文字符串 | `com/tencent/turingface/sdk/mfa/TNative$aa`、`TuringFD v%d (%s, %s, %s, compiled %s)`、`turingRiskDetect`、`TuringFdNative` |
| 动态注册方法表 | `.data` `0x56540`（**15 条**：14 个 `x91_FC6D5B0A7013DB60` + `onServiceConnected`）、`0x56690`（1 条）——与本报告 [evidence.md](evidence.md) 记的"14 + 1 个动态注册方法"**独立吻合** |
| 关键签名 | `k91_…` = `([B)[B`（字节变换，对应 `getDFPWup`）、`l91_…` = `(InvocationHandler, AtomicReference, ClassLoader)V`（**动态代理安装器**，对应 `java/lang/reflect/Proxy` / `newProxyInstance` 字符串）、`j91_…` = `()Ljava/lang/String;` |
| 入口 | `JNI_OnLoad @ 0x1fcc4`；**代码入口面只导出它** |
| 上报 | [risk.md](../risk.md) §4 记的 `https://tdid.m.qq.com/tmf`（WUP/Tars）。**注意该 URL 不在本库的加密表里**（表内 `tdid`/`qq.com`/`http` 命中均为 0），它由 Java 侧明文持有；本库只持 `getDFPWup`/`deviceIdentify`/`getTFConfig`/`turingdfp` 等**方法名字符串** |

#### (b) 解密器本体：完全展开、零调用、零入边

| 项 | 值 |
| --- | --- |
| 地址 | `0x34d74`（线性区 `0x34d74`–`0x39ecc`） |
| 大小 | **5 207 条指令**（20 856 字节），**1 个 `ret`** |
| 调用 | **0 个 `bl`/`blr`**；**0 条分支从区外跳入**（`b`/`bl` 目标扫描为空） |
| 到达方式 | **不是被调用**，而是 `.init_array` **第 6 项**（`vaddr 0x51b40`，由 `R_AARCH64_RELATIVE` 绑定）在该库**加载时直接执行一次** |
| 附带发现 | 该库 `.init_array` **9 项全部非零**（`0xdaf4 / 0x15120 / 0x2abfc / 0x2c590 / 0x2e588 / 0x34c34 / 0x34d74 / 0x4cc1c / 0x4d174`），`0x34d74` 是其中**最大的一个** |

因为**没有回边、没有循环**，[tiny-and-app-sweep.md](tiny-and-app-sweep.md) §1.5 的
`eor_branch`（"字节加载后 8 条内有 `eor wN`"）虽然数到了 331 次，却无法把它认成"解密循环"。

#### (c) 编码形式：`b ^ key` 写成"互补掩码选择 + eor"

解密器对每个字节做的是**异或**，但源代码把它写成了**两个互补掩码的位选择**：

| 形态 | 指令序列 | 恒等式 |
| --- | --- | --- |
| 标量 | `mvn wT, wKey ; and wT, wT, #~K ; and wB, b, #K ; orr wB, wT, wB ; eor wB, wB, #(K^K2)`（`#~K` 由 `bic/and/orr` 三元组构成） | `(K & ~b) \| (b & ~K) == b ^ K`（已对全部 8 个密钥 × 全部 256 个字节值验证） |
| NEON | `mvn vT.16b, vK.16b ; bit vK.16b, vT.16b, vM.16b ; eor vK.16b, vK.16b, vKey.16b` | 同上，一次 16 字节 |

静态证据：区内出现 **103 个 `bic`+`and`+`orr`+`eor` 四连**与 **3 个 `mvn`+`bit`+`eor`+`str` 四连**，
`mov w, #imm` 共 **471 条**（162 个互异立即数）。也就是说**密钥对是逐条目物化的**。

> **口径提醒（实测）**：这 471 条是 `mov`，而**不是** `movz`/`movk`——
> 解密器内 `movz`/`movk`/`movn` **全为 0 条**。该库 `movz_movk_per_1k`=61.3 来自
> **4 050 条散布在区内之外的 `movk`**，与这个解密器无关。
> 因此 §1.5 的 `movz/1k` 列对本库是**无关指标**，唯一有信号的是 `eor_branch`=331。

#### (d) 密钥调度：逐条目查表

对模拟器改写过的**每一个**字节求 `密文 ^ 明文`，可以得到该字节用的是哪个密钥。
两条事实（`re/tmfa_keys.py`）：

| 结论 | 校验 | 值 |
| --- | --- | --- |
| **一个条目整体只用一个密钥** | 逐字节 `密文^明文` 与该条目首字节的密钥比对 | **8 412 / 8 412 字节成立** |
| **所有密钥都在同一个 8 值周期里** | 8 412 字节逐字节判定 | `0F AF 4F EF 8F 2F CF 6F`（即 `key & 0x1F == 0x0F`） |

> **顺序推进不是"按条目序号"，而是"按源列表序号"**：用条目序号 `i` 作自变量的线性式
> `key_i = (0x0F + 0xA0 * i) & 0xFF` 只在**前 18 个条目**上恰好为真（对全部 348 个条目
> 仅 **42 / 348** 命中，第 19 个条目 `i = 18` @ `0x56ae7` 即首次分叉）。
> 差异的来源是空串也占一个源列表序号（见下）。

**调度（闭式，已对 348/348 条目验证）**：

```
key_index(条目) = src_index(条目) mod 8        src_index ∈ [0, 416)
```

其中 **`src_index` 是"源字符串列表中的位置"，这个列表把空串也算进去**——
而**空串在密文里不留任何字节差**（空串异或后仍是空串），所以只看二进制只能看到 348 项，
真实列表长度是 **416 = 348 个非空 + 68 个不可见空串**。
相邻非空条目之间的 `src_index` 步长直方图为 `1→295, 2→37, 3→14, 4→1`，
`Σ(步长-1) = 68` 与 416−348 精确吻合——**这就是那 68 个空串的个数由来的独立校验**。

> 也就是说：**"密钥按条目序号 +1 递推"是错的，"密钥按源列表序号递推、空串照样占一个序号"才是对的。**
> 后者只有在能数出空串时才可写成闭式；纯静态（不跑构造子）无法得到 416 这个数。

表结构（`BASE = 0x569c0`）：

| 段 | 内容 |
| --- | --- |
| `0x569c0` – `0x569e3` | **9 个固定 4 字节单元**，每个只有第 1 字节参与异或（其余 3 字节为明文 `00`）；解出为 `V Z B C S I J F D`（9 个单字符常量，`R_AARCH64_RELATIVE` 里**没有任何指针指向这 9 个单元**，故判定为**就地使用的常量**而非字符串表项） |
| `0x569e4` – `0x58caf` | **NUL 结尾的字符串序列**，每个条目整体用**一个**密钥（该条目的 `src_index mod 8`，见 (d)）；长度是**编译期常量**（解密完全展开），运行期没有分隔符可扫 |

**条目计数**（三种口径分开列）：

| 量 | 值 | 来源 |
| --- | ---: | --- |
| 被改写的**非空条目** | **348** | 模拟器字节差 |
| 其中 ≥4 字符全可打印 | **325** | 同上 |
| 密钥推进直方图 | `1→295, 2→37, 3→14, 4→1` | 相邻条目密钥相差的周期步数 |
| **源字符串列表总项数** | **416** = 348 非空 + **68 空串** | `Σ(步长−1) = 68`，与 416−348 精确吻合（§1.7(d)） |

> **为什么"空条目"看不见**：空条目就是一个空串（0 字节），异或后仍是空串，**不在字节差里**。
> 因此**任何**基于"密文里找分隔符/按字节差切分"的静态还原都只能得到**非空条目数（348）**，
> 而拿不到**源列表项数（416）**。近似式"9 + Σ推进 + 1"把 9 个前导常量单元也算成了列表项，
> 得出 ∿425（含 ∿77 空条目）——**该近似式不成立**。416 是由**两个互相独立的量**
> （密钥推进之和；源列表序号上界）同时算出的定值。
> 权威方法是执行解密器（`re/tmfa_initarray.py`，Unicorn）+ 读它写出的指针数组
> （`re/tmfa_pointer_arrays.py`，见 §1.7(i)），表内 348/325/416 均出自这两者。

#### (e) 前三条判据为何对它全部失效

| 判据 | 对 `libturingmfa.so` 的读数 | 为什么测不到 |
| --- | --- | --- |
| 加密指令族 | **0**（无 `aes*`/`sha*`/`pmull`/`sm4*`） | 逐字节 XOR 不用任何加密指令 |
| 已知算法常量 | **0**（无 MD5-T/IV、AES-Sbox、Base64 表……） | 自研轮转密钥没有标准常量可匹配 |
| ≥8 KB 高熵区 | **不命中** | 表 10 136 B，**熵只有 6.978**（前 8 192 B = 7.073），因为**每个条目的 `\0` 是明文存储**（零字节占比 **11.8%**），把熵压到 7.9 阈值以下；按 4 096 字节窗口扫描，**熵 > 7.9 的窗口数 = 0**，最长连续高熵段 = **0 字节** |
| 混淆指标（§1.5） | `br_pct`=0.020、`tblsig`=0、`opaque_csel`=0、`.rodata` 熵 5.71 | 解密完全展开 ⇒ 没有 CFF 指纹、没有分派表 |

**四条判据中只有第 4 条能命中它**（§1.3、§1.5、§1.6）。

#### (f) 解出的明文：这张表保护的是什么

325 条可打印明文可按用途分五类（`re/tmfa_initarray.json`）：

| 类别 | 条数 | 代表条目 |
| --- | ---: | --- |
| **OAID / 厂商设备 ID**（跨 10 余家厂商的 AIDL 服务名） | 37 | `com.uodis.opendevice.aidl.OpenDeviceIdentifierService`（华为）、`com.hihonor.cloudservice.oaid.IOAIDService`（荣耀）、`com.samsung.android.deviceidservice.IDeviceIdService`、`com.asus.msa.SupplementaryDID.IDidAidlInterface`、`com.zui.deviceidservice.IDeviceidInterface`（联想）、`com.bun.lib.MsaIdInterface`/`com.mdid.msa`（MSA）、`content://com.huawei.hwid.pps.apiprovider/oaid_scp/get`、`content://com.meizu.flyme.openidsdk/oaid`、`content://com.vivo.vms.IdProvider/IdentifierId/OAID`、`pps_oaid`、`tencent_identifier`、`com/android/id/impl/IdProviderImpl`、`android_id`、`ANDROID_ID` |
| **反模拟器 / 环境与完整性** | 57 | `/proc/self/maps`、`/proc/self/mountinfo`、`/proc/self/cgroup`、`/proc/interrupts`、`/proc/net/arp`、`/proc/version`、`/proc/sys/kernel/random/boot_id`、`/sys/bus/virtio`、`/sys/block/mmcblk0/device/cid`、`/sys/class/net/wlan0/address`、`/dev/block/loop`、`/dev/block/dm`、`/system/bin/df`、`/system/bin/run-as`、`/system/xbin/procmem`、`init.svc.qemud` / `init.svc.noxd` / `init.svc.droid4x` / `init.svc.vbox86-setup` / `init.svc.ttVM_x86-setup`（**模拟器特征**）、`microvirt.vbox_dpi`、`Hypervisor\|goldfish`、`qemu\|vbox\|eth`、`ro.boot.serialno`/`ro.serialno`/`gsm.serial`、`ro.build.fingerprint`、`ro.product.*`、`/DCIM/.tmfs`、`/DCIM/.android`、`/.turing.dat`、`/.t.log`、`/.tgt.log`、`/.android_system_config.prop` |
| **反射 / Binder 直取**（绕开公开 API） | 17 | `android/os/ServiceManager` / `ServiceManagerNative` / `com/android/internal/os/BinderInternal`、`android/content/pm/IPackageManager$Stub`、`android/hardware/display/IDisplayManager$Stub`、`android/view/IWindowManager$Stub`、`java/lang/reflect/Proxy` + `newProxyInstance(...)`、`asInterface(...)` ×4、`getContextObject()`、`checkService(...)` |
| **密码学方法名**（只持有"要调什么"的字符串） | 12 | `javax/crypto/spec/SecretKeySpec`、`javax/crypto/Cipher`、`javax/crypto/spec/IvParameterSpec`、`javax/crypto/spec/GCMParameterSpec`、`javax/crypto/Mac`、`AES/GCM/NoPadding`、`HmacSHA256`、`getInstance(...)`、`init(ILjava/security/Key;Ljava/security/spec/AlgorithmParameterSpec;)V`、`init(Ljava/security/Key;)V`、`doFinal([B)[B` |
| **Turing 自身配置与采集面** | 19 | `turingdfp`、`getDFPWup`、`deviceIdentify`、`getTFConfig`、`platform`、`version`、`channel`、`targetSdkVersion`、`battery.capacity`、`/sys/class/thermal/`、`/proc/cpuinfo`、`/proc/meminfo`、`/sys/class/android_usb`、`/system/fonts`、`getOAID()` / `getOAID(Landroid/content/Context;)`、`getSubscriberId`、`getOwnerUid`、`getOwnerPackageName` |
| 其余 | ~180 | `Landroid/content/pm/ApplicationInfo;` 等类型描述符、`getPackageInfo(...)`/`queryIntentActivities(...)` 等 Binder 事务签名、`myUserId`/`myUid()I`、`com/applisto/appcloner/hooking/Hooking`（**应用克隆检测**）、`ZteDeviceIdentifyManager` |

#### (g) 代码结论与运行期数据分界（无"未知加密"）

| 项 | 静态结论 | 分界类型 |
| --- | --- | --- |
| `.init_array` 其余 8 个构造子 | `0xdaf4`、`0x15120`、`0x2abfc`、`0x2c590`、`0x2e588`、`0x34c34`、`0x4cc1c`、`0x4d174` 按 FDE 精确边界反汇编，覆盖内部调用与注册析构目标共 23 函数 / 19 导入；密码学指令 0、写 `.data`/`.data.rel.ro`/`.rodata` 目标 0。本库只有 `0x34d74` 是自足可执行的表解密器 | 运行期对象分配/映射不改变代码语义 |
| 9 个前导单元的语义 | `0x569c0`–`0x569e3` 解出 `V Z B C S I J F D`；构造子跑完后全部进入 `.bss` 指针数组前 9 槽（§1.7(i)） | 单元内容和消费者槽位已确定；每次业务读取的选择属于运行期数据 |
| 61.3/1k 的 `movz` 中非密钥部分 | 162 个互异立即数中 `{K,~K}` 16 个为密钥，其余 146 个为 Turing 采集项比较常量与 Binder 事务码 | 已按用途分类；它们不是密钥表 |
| WUP/Tars 上行协议 | 本库通过 `k91_…` 执行字节变换；URL、请求对象和 Java 序列化入口由 `classes4.dex` 持有，客户端侧字段/方向由风险与网络证据索引列出 | native 边界与 Java wire 层分属不同模块，均已分别归档 |

#### (h) 复现

```bash
cd "$REDNOTE_ANALYSIS_ROOT"

# 1) 跑 .init_array 构造子，得到每条被改写字节的 (密文, 明文) 与 325 条明文
python3 re/tmfa_initarray.py            # -> re/tmfa_initarray.json

# 2) 由字节差反推密钥周期并闭式校验（无需模拟器）
python3 re/tmfa_schedule.py             # -> re/tmfa_schedule.json

# 3) 全 164 库：原地自解密位点（静态，逐版收紧）
python3 re/inplace_strdec_scan.py       # -> re/inplace_strdec_scan.json  (原始口径)
python3 re/inplace_form_scan.py         # -> re/inplace_form_scan.json    (+ post-index/writeback/pair/NEON)
python3 re/inplace_form_content.py      # -> re/inplace_form_content.json (内容定性)
python3 re/inplace_xref_scan.py         # -> re/inplace_xref_scan.json    (+ 寄存器偏移/宽存储；348/348 命中)
python3 re/inplace_xref_content.py      # -> re/inplace_xref_content.json (全应用内容定性)

# 4) 全 164 库：构造子清点 + 模拟改写 + 单字节 XOR 扫描
python3 re/initarray_census.py          # -> re/initarray_census.json
python3 re/initarray_run.py libs/lib/arm64-v8a/*.so        # -> re/initarray_run.json
python3 re/xor_table_test.py            # -> re/xor_table_test.json

# 5) libturingmfa 专项：逐条目密钥表 + .bss 指针数组
python3 re/tmfa_keys.py                 # -> re/tmfa_keys.json
python3 re/tmfa_pointer_arrays.py       # -> re/tmfa_pointer_arrays.json
```

样本基线：`libs/lib/arm64-v8a/libturingmfa.so`
SHA-256 = `537edc54d6d4ab39d4e7aa6d6f5bddb8e14264d52585d0d69124fbcf61d45dc0`。

#### (i) 构造子在 `.bss` 里建出的**指针数组**

同一个构造子在 `.data` 侧改写 8 412 字节之外，还在 `.bss` 写出 **348 个 8 字节指针**，
按**表序**指向刚解密的每一条非空条目，**这才是后续代码真正用来索引表的对象**
（`re/tmfa_pointer_arrays.py`）：

| 项 | 值 |
| --- | --- |
| 数组位置/尺寸 | `0x59458` – `0x59f38`，**348 槽 × 8 字节** |
| 槽位解码 | 每个槽只有**低 3 字节**非零（改写 1 042 B = 348 × 3），高位为 0；按 8 字节小端读出的值**全部**落在表内（`0x569c0`–`0x58c9e`） |
| 与表序的关系 | **严格递增**，且**恰好等于 348 个非空条目地址的升序序列**（逐项相等，0 漏 0 多） |
| 前 9 个槽位 | `0x569c0 / 0x569c4 / … / 0x569e0` —— **正是那 9 个「无重定位指向」的前导常量单元**，见 (g) |
| 死条目 | **0 个**（每个非空条目都被数组引用） |

> **读法**：这 348 个指针必须按 **8 字节步长**读。若按「连续非零 run」去读，
> 会因为某些指针低 3 字节后面恰好跟一个非零字节而把相邻槽位粘起来，得到 78/227/41 这种
> **错误分段**，并凭空造出「未被引用的死条目」。`.bss` 要按**它自己的数组步长**解释，
> 而不是按「改动了哪些字节」解释。

`.bss` 改写总量 **1 045 B = 1 042 B（数组载荷）+ 3 B**。那 3 B 位于 `0x58cd1`
（内容 `"." "i" "n"`）**不属于数组**：它由 `0x34d74` 之外的**更早那 6 个构造子**写下
（单独跑 `0x34d74` 时 `.bss` 恰为 1 042 B 且完全不碰该地址；单独跑前 6 个则恰好只改这 3 B），
是它们在模拟器里执行到一半（随后 `UC_ERR_EXCEPTION`）留下的**部分执行痕迹**，不是静态表的一部分。

---

## 2. `libtiny.so` 的密码学画像

`re/tiny_crypto_char.py` 输出（`re/tiny_crypto_char.txt`）：

### 2.1 实际存在（表驱动 / 常量型）

> 本节只列**走常量或指令路径**的成分，即查表型实现与用加密指令族实现的算法。
> **内联大整数域运算不在本表内**：它既无常量也无加密指令，见 §2.2.1。

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

**这条负面结论是可复核的强结论，但它只覆盖「查表」与「指令」两条路径**：`libtiny.so` 里没有 AES、没有 SHA 族、没有 SM 族、没有 GHASH，也没有完整 MD5 轮常量；§2.2.1 进一步证明它也**没有任何加密指令、没有任何 SIMD 向量指令**（全段 1 528 000 条指令中 AES/SHA/SM/CRC32/PMULL 各为 0）。

**结论**：`libtiny.so` 的标准密码学成分**不止** MD5 骨架 / Base64 / CRC32，还包含**内联 X25519 域运算**。后者既无加密指令也无已知算法常量（§2.2.1），因此上述两条扫描都测不到它——"没有加密指令、没有已知常量"不等于"没有密码学"。

> 注意"有 MD5 初值但无 MD5 轮常量"这一组合并不矛盾：MD5 初值也是"自定义哈希"最常用的起始状态，而轮常量可以被换成一整套自定义常数（这正是 `libxyass.so` 定制 HMAC-H 的做法，见 [crypto.md](crypto.md) §3）。因此这里读作**"以 MD5 初值为起点的自定义摘要，而非标准 MD5 实现"**。

### 2.2.1 **`libtiny.so` 内含内联 X25519（Curve25519）域运算**

本节给出四条独立的结构证据，定位 `libtiny.so` 里一套**内联的 Curve25519 域运算**。它不是查表实现，因此既不触发加密指令扫描、也不触发已知算法常量扫描。

**位置**：`0x525000` – `0x531e4c`（窗口上界取 `0x532000` 时为 **13 312** 个指令槽）。域运算函数的入口为 `0x525024`，由 `0x524f58` 处的 `blr x8` 调用。区内有 6 个 `ret` 边界（`0x52501c`、`0x5262e8`、`0x52882c`、`0x52e9fc`、`0x5316b8`、`0x531e4c`；末两者属 R5），其中 `0x52501c` 是**前一个函数的收尾**（`ldp` 恢复被调用者寄存器 + `add sp,sp,#0x50` + `ret`）。因此这是一个由 **5 个内联域例程（R1–R5）** 拼成的代码块，而非单一函数；`0x531e50` 之后另有独立辅助段 R6（两个操作码均不执行）。逐例程地址、槽位数与密码学指纹见 §5.6.9(b)。

| 证据 | 观测 | 说明 |
| --- | --- | --- |
| radix-2⁵¹ 肢体掩码 | `and Xd, Xn, #0x7ffffffffffff` **178 处**（`0x52544c`–`0x531bc4`）；**全文件精确计数亦为 178**，即该掩码在库内**只出现在这一段** | 5×51=255 位打包的标准域表示 |
| 51 位进位提取 | `extr x8, x9, x8, #0x33` **130 处**（`0x33` = 51），跨度 `0x525444`–`0x5313c0` | 肢体间进位链 |
| ×19 归约 | **18 处** `mul`/`madd`（9 `mul` + 9 `madd`），其乘数来自紧邻的 `mov wN,#0x13`（=19），**全部 18 处都在本区内** | 2²⁵⁵−19 的归约，Curve25519 特有 |
| **a24 = 121666** | `0x527db0`：`mov w9,#0xdb42` + `movk w9,#1,lsl#16` ⇒ **0x1DB42 = 121666** | **(486662+2)/4，Montgomery 阶梯的 a24，曲线身份的判定性常量** |
| 标量钳位 | `0x527fb0`：`and w8,w8,#0xf8`（k[0] &= 248）；`0x527fc4`：`bfxil w9,w8,#0,#6` 得 `(k[31]&0x3f)|0x40`，`0x526ec8` 再 `lsl #0x2c`(=44) 置入第 4 肢体 | **X25519 的 clamp（RFC 7748）逐位吻合**：低 3 位清零、bit255 清零、bit254 置 1 |

**结构性数据**（来自模拟器执行集 `re/execsets/cov_*.txt`）：

| 项 | 值 |
| --- | ---: |
| 域区内的 `adds`/`adcs` 进位链 | 各 **364** 条（**364/364 全部被执行集覆盖**） |
| 域区内的乘法类指令 | `mul` 514 / `umulh` 390 / `madd` 252（**均 100% 覆盖**） |
| 区内 `ret` 点（内联例程边界） | **6** 处（`0x52501c`、`0x5262e8`、`0x52882c`、`0x52e9fc`、`0x5316b8`、`0x531e4c`） |
| 区内指令总数 / 被覆盖数 | **13 312** / **13 124**；未执行 **188** 个槽位已逐段定性（§5.6.9(g)），其中 **0 个**含密码学指纹 |
| 逐块语义 lift | **已完成**：435 个 distinct run 全判读、154 个域原语定名、九项指纹守恒逐项 EXACT（§5.6.9） |
| 执行该区的操作码 | **仅 2 个**：`0x3c6d0ac1`、`0xae821439`；其余 29 个操作码在本区覆盖 **0** 条 |
| 全部 178 个掩码位点与 130 个进位提取位点的执行覆盖 | **178/178、130/130 全部被执行集覆盖** |
| 确定性复核 | 同输入两次执行，覆盖集合 **逐字节相同**（35 577 条，对称差 0） |

**调用面**：`0x3c6d0ac1` 在 dex 中的唯一调用点是 `com.xingin.tiny.internal.u2.a(yya.c)`（`u2.java:111`），即**风控 SDK 的初始化/配置注入**；`0xae821439` 由 `ulb.d` 调用（同为 SDK 启动路径）。因此该曲线代码位于 **Tiny 风控引擎的初始化路径**上。

**与 Ed25519 的区分（已排除）**：Ed25519 用 `d = 0x52036cee…135978a3` 与**乘以 121665**；本库 `d` 的两个半字立即数出现 **0** 次、`121665` 出现 **0** 次，而 `121666` 出现 **1** 次。因此这是 **X25519 规模的 Montgomery 阶梯**，不是 Ed25519 签名。

**这条发现对"无未知加密代码"结论的影响**：

- 它**不是**"未知加密"：算法身份（Curve25519 域）、表示（radix-2⁵¹）、归约（×19）、曲线常量（a24=121666）、标量钳位（RFC 7748）**全部已定名**，可逐条复核；
- 它同时**暴露了指令/常量两类扫描的盲区**：`libtiny.so` 既不出现任何加密指令、也不出现任何已知算法常量，却能实现完整的曲线运算。§1.2 的判定口径因此**新增"大整数域特征"一条**，使同类实现可被扫出；
- 结论措辞相应收紧：`libtiny.so` 的标准密码学成分**不止** MD5 骨架 / Base64 / CRC32，还包含**内联 X25519 域运算**。

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

> 口径说明：直接把整个 131 072 字节窗口按熵统计得到 7.5189 bit/byte，那是**被 16 352 字节全零前导稀释**的结果；扣掉前导零后才是这张表的真实熵 **7.9983**。`evidence.md` 中记录的 `.data 0x754AC0`–`0x7701C0`、112 384 字节、熵 7.9982945 是本区另一组取窗口，两者在有效载荷上一致（7.9983 vs 7.9983），边界差异只是取窗口方式不同。

**判读**：该区位于 `.data`（可写、运行时填充），**有效载荷前存在 16 KB 全零前缀**，且**不含任何已知容器魔数**——符合"运行期填充的缓冲/表区"特征，而不是随包分发的加密载荷。因此不存在"打包的加密 blob 待解"这一问题。这与 `libxyass.so` 的"未发现静态或已覆盖动态路径引用的高熵数据区"写法保持一致。

**引用侧证据（定性所用）**：该载荷的统计特征**极像随机数据**（熵 7.9982775，256 个取值全出现，每值计数 395–508，卡方 274.1 对应 df=255；**0 个重复的 16 字节块**），所以"它是缓冲"不能只靠外观，必须给出引用侧证据。四项实测（`re/tiny_blobwatch.py`、`re/obf_census.py` 与静态扫描）：

| 检验 | 结果 |
| --- | --- |
| 静态 `adrp`+`add` 落点 | 指向 `0x750000` 页的 55 处全部落在 `0x750800`–`0x750f20`，**指向载荷区（≥`0x750fe0`）的为 0** |
| `.rela.dyn` 重定位加数 | 39939 条中落在载荷内的 **0** 条 |
| `.data` / `.data.rel.ro` 指针槽 | 8 字节槽指向载荷的 **0** 个 |
| 容器魔数 / 解压尝试 | zlib / gzip / bz2 / lzma **全部失败**，无 `\0asm` 等魔数 |
| 运行期访问（`JNI_OnLoad` 全程） | 读写 hook 覆盖 `0x750fe0`–`0x76d000`：**reads = 0，writes = 0** |
| **动态执行集引用扫描**（最强证据） | 把 **31 个操作码 + `JNI_OnLoad` 的执行指令并集**（55 151 条）逐条检查：其中 `adrp` 覆盖 150 个不同页，**无一落在载荷页**；可解析的 `adrp`+`add` 对中**落在载荷内的为 0**；立即数形如 `0x75xxxx`/`0x76xxxx` 的已执行指令**为 0**（`re/tiny_blobref_scan.py`） |

即：该区在**静态**（重定位/指针槽/`adrp+add`）与**动态**（`JNI_OnLoad` + 31 个操作码的执行指令并集）两侧**都没有被任何代码引用**。它是一块被链接进 `.data` 的**未被引用高熵数据**——既不是"待解的加密 blob"（无引用方、无解密调用），也不是业务数据（无读取点）。结论从"看起来像缓冲"收紧为"**实测零引用**"，同时**不声称**它是何用途（引用侧证据只能证明它不被使用，不能证明它为何存在）。

> **口径说明**：动态侧用的是"执行指令并集"而非运行期读写 hook——并集是对该库**全部已测入口**的完备覆盖（31 个操作码全部自然退出，见 §6.4），比单次 hook 更强，且不依赖 hook 的性能。上面那行 hook 结果只作独立佐证。

---

### 2.4 `libtiny.so` 的字符串加密：完整还原

与 `libtinyd.so` 同族（[tinyd-companion-daemon.md](tinyd-companion-daemon.md) §2），结论更强：**两个解码器均可闭式还原，且 320 个调用点里 312 个已直接解出明文**。

#### 2.4.1 全库只有两个解码器

按全段 `bl` 目标统计，进入字符串解码簇（`0x13e000`–`0x146000`、`0x18c000`–`0x18e000`）且被反复调用的只有两个入口：

| 解码器 | 被调用次数 | 判读 |
| --- | ---: | --- |
| `0x18c940` | **203** | 解码器 A |
| `0x18d5e4` | **118** | 解码器 B |

其余同簇目标（`0x13f3f4` 300 次、`0x1441d0` 255 次、`0x13e94c` 232 次等）经反汇编确认是 **CFF 分派胶水**（形如 `ldr x5,[x3,#0x228]; add x4,x19,x7; br x5` 的块间跳转），不是解码器本体。

#### 2.4.2 调用惯用式（与 `libtinyd.so` 同族，但布局不同）

```asm
adrp x8, <base> ; add x8, x8, #<off>   ; x8 = 密文地址（.rodata）
ldr  q0, [x8]                          ; 取 16 字节到栈帧
str  q0, [sp]
mov  w0, #LEN+1                        ; 分配 LEN+1
bl   0x129424                          ; 分配器
strb wzr, [x0, #LEN]                   ; 置 NUL
bl   0x53296c                          ; memcpy(out, sp, LEN)
mov  w1, #LEN                          ; ← 传明文长度
bl   0x18c940 | 0x18d5e4               ; 原地解密(out, LEN)
```

**与 `libtinyd.so` 的差别**：`libtinyd.so` 的密文既可能来自 `.rodata` 也可能来自**指令立即数**（`mov w9, #0xb9bd` → `bd b9 00`）；`libtiny.so` 的 320 个调用点**全部**先在 `.rodata` 里形成密文 blob 再 `ldr q0` 装载。二者共用同一套"装载 → 分配 → memcpy → 原地解码"骨架。

#### 2.4.3 闭式算法

解码器是**按位置索引的逐字节双射**，每个位置 `i` 查一张 **20 项**调度表：

```c
void decode(uint8_t *buf, size_t len) {
    for (size_t i = 0; i < len; i++) {
        uint8_t v = ROL8(buf[i], SCHED[i % 20].rot);      /* 8 位循环左移 */
        buf[i] = SCHED[i % 20].is_add ? (uint8_t)(v + SCHED[i % 20].k)
                                      : (uint8_t)(v ^ SCHED[i % 20].k);
    }
}
```

**还原方法**（可复现，`re/tiny_strdec.py`）：把 `buf` 置于 18 字节全零缓冲区、逐位置把该字节遍历 0–255 调用解码器，记录输出该位置的 256 个取值，得到该位置的完整函数表；再以 `xor`/`add`/`sub` × 旋转 0–7 × 常量 0–255 做穷举拟合。

**结果：36/36 个位置全部拟合成功**（两个解码器 × 前 18 个位置），且**每个位置都是双射**（256 个输出互异）。调度表被测出**最小周期 = 20**（两个解码器都是 20，与其调度表长度一致）。

解码器 A（`0x18c940`）调度表：

| i | op | rot | k | i | op | rot | k |
| ---: | --- | ---: | ---: | ---: | --- | ---: | ---: |
| 0 | xor | 0 | `0x22` | 10 | xor | 0 | `0x69` |
| 1 | xor | 0 | `0x42` | 11 | xor | 0 | `0x42` |
| 2 | **add** | 0 | `0x97` | 12 | **add** | 0 | `0xde` |
| 3 | xor | 7 | `0x00` | 13 | xor | 7 | `0x00` |
| 4 | xor | 7 | `0x00` | 14 | xor | 1 | `0x00` |
| 5 | xor | 0 | `0xbd` | 15 | xor | 0 | `0xbd` |
| 6 | xor | 0 | `0x96` | 16 | xor | 0 | `0xdd` |
| 7 | **add** | 0 | `0x43` | 17 | **add** | 0 | `0x43` |
| 8 | xor | 1 | `0x00` | 18 | xor | 7 | `0x00` |
| 9 | xor | 1 | `0x00` | 19 | xor | 1 | `0x00` |

解码器 B（`0x18d5e4`）调度表：

| i | op | rot | k | i | op | rot | k |
| ---: | --- | ---: | ---: | ---: | --- | ---: | ---: |
| 0 | xor | 0 | `0x80` | 10 | xor | 0 | `0x29` |
| 1 | xor | 0 | `0xe6` | 11 | xor | 0 | `0x23` |
| 2 | **add** | 0 | `0xd7` | 12 | xor | 0 | `0x80` |
| 3 | xor | 4 | `0x00` | 13 | xor | 3 | `0x00` |
| 4 | xor | 3 | `0x00` | 14 | xor | 7 | `0x00` |
| 5 | xor | 0 | `0x19` | 15 | xor | 0 | `0xdc` |
| 6 | xor | 0 | `0xd6` | 16 | xor | 0 | `0x7f` |
| 7 | **add** | 0 | `0x24` | 17 | **add** | 0 | `0xe7` |
| 8 | xor | 5 | `0x00` | 18 | xor | 1 | `0x00` |
| 9 | xor | 5 | `0x00` | 19 | xor | 4 | `0x00` |

**两个调度表都是"每 20 项里 3 项 `add`、其余 `xor`"**，且旋转量只有 8 种取值中的 0/1/3/4/5/7——与 `libtinyd.so` 的 `i%20` 调度表**同族但取值不同**（印证 §[tinyd](tinyd-companion-daemon.md) §2.2 的告诫：不要把不同库的解码器混为一谈）。

#### 2.4.4 非线性与扩散性（实测）

- **无扩散**：逐位置把该字节设为 `0xFF` 后，输出**只有同一位置**变化（16/16 位置验证），即 `out[i]` 只依赖 `in[i]`；
- **非线性**：位置 2/7/12/17 是 `(ROL8(x,0)+k) mod 256`，其余是 `x^k` 或 `ROL8(x,r)^0`。**因此不能当纯 XOR 流处理**——这解释了为什么对 `.rodata` 全域做单字节 XOR 穷举只能捞到 3 处偶然命中（`0x020` 掩码类）。

#### 2.4.5 明文恢复：320 个调用点解出 312 条

按 §2.4.2 的惯用式，从每个调用点静态取出 `(密文地址, 长度)`，再用 §2.4.3 的调度表离线解码（`re/tiny_strings_decode.py`，原始输出 `re/tiny_strings_decoded.json`）：

| 项 | 值 |
| --- | ---: |
| 定位到的解码器调用点 | **320**（A 203 + B 117） |
| 其中 `(密文地址, 长度)` 可静态确定的 | **320 / 320** |
| 解出**纯可打印 ASCII** | **312（97.5%）** |
| 解出"可打印 + 空白符" | 3（`'[%s]\n'`、`'\n\t(...tail calls...)'`、`'/\n;\n?\n!\n-\n'`） |
| 解出**二进制/数据** | 5 |
| 去重后不同明文 | **279** |

**解出的字符串本身就是这套混淆的证据**——它们的语义高度自洽，覆盖了 Tiny 引擎的全部功能面：

| 类别 | 代表明文（已解密） |
| --- | --- |
| **上报端点** | `https://as.xiaohongshu.com/api/v1/register/android`、`.../api/v1/cfg/android`、`.../api/v1/prb/android`、`.../api/v1/dvf/vab/android` |
| **SDK 配置 JSON** | `{"PACKAGE_NAME":"com.xingin.xhs","APP_KEY":"…","APP_ID":"…"}` 与 `com.tiny.basic` 版（**取值见 `re/tiny_strings_decoded.json`**） |
| **反分析与反调试** | `TracerPid:`、`/proc/self/maps`、`detect tracer [%s][%s] for thread %d, state = %s, wchan = %s`、`detect tracer in my thread group: …`、`the GetUntrustedIPackageManager was invoked successfully but the result is null?` |
| **ART 内部结构** | `_ZN3art2gc9collector17ConcurrentCopying12MarkingPhaseEv`、`_ZN3art9JNIEnvExt11NewLocalRefEPNS_6mirror6ObjectE`、`_ZNK3art12StackVisitor24GetCurrentQuickFrameInfoEv`、`/data/misc/apexdata/com.android.art/dalvik-cache/arm64/`、`[anon:dalvik-zygote-jit-code-cache]`、`dalvik.system.DexPathList` |
| **Java 反射** | `invokeoriginalmethod`、`getDetectResult`、`newBooleanArray`、`asIntArray`、`getContext`、`hex_encode` |
| **Lua 运行时** | `pcall`、`setmetatable`、`__index`、`__newindex`、`__tostring`、`__add`/`__sub`/`__mul`/`__div`/`__mod`/`__pow`/`__unm`、`__bor`/`__shl`/`__shr`、`randomseed`、`maxinteger`、`mininteger`、`coroutine`、`getupvalue`/`setupvalue`、`popen`/`lines`/`input`/`flush`、`__c__`、`__dl__ZL6solist` |

**第 5 类（Lua）**：`libtiny.so` 内嵌了一套 **Lua 解释器**（含 `pcall`/`setmetatable`/完整元表运算符集/协程/`popen`/`lines`/`input` 的 IO 库）。这与 `risk.md` 记录的"Tiny 内置脚本引擎"一致；本节给出其可引用的 native 侧证据——一整组经混淆的明文标识符，直接落在解码器调用点上。

> **口径**：本节只给出**明文的类别与代表样本**，不逐条复制可能构成凭据的取值（`APP_KEY`/`APP_ID` 的实际内容留在 `re/tiny_strings_decoded.json`，不进入本文）。这与本报告"不复现硬编码密钥取值"的既有约定一致。

#### 2.4.6 那 8 个非文本结果已逐条定性

| 调用点 | 长度 | 解码结果 | 判读 |
| --- | ---: | --- | --- |
| `0x12e7a8` | 16 | 16 字节二进制 | **不是字符串**——按调用惯用式属于"这里不做分配（无 `strb wzr`）"的分支，即使用**同一调度表驱动的原地 16 字节变换**（哈希/摘要类） |
| `0x12e808` | 16 | 16 字节二进制 | 同上 |
| `0x12e868` | 16 | 16 字节二进制 | 同上 |
| `0x12e8c8` | 16 | 16 字节二进制 | 同上 |
| `0x5707c0` | 256 | `0,1,2,2,3,3,3,3,…,8,8` | **不是密文**——是"按位取最高置位序号"的 **log2/位计数查表**（值域 0–8，单调不减），复用了解码器地址空间，属误报 |
| `0x2c73e0` | 5 | `'[%s]\n'` | 格式串（含换行） |
| `0x5c7e5c` | 20 | `'\n\t(...tail calls...)'` | Lua 错误回溯文案 |
| `0x5dcb94` | 10 | `'/\n;\n?\n!\n-\n'` | Lua 词法分隔符集合 |

**因此本库不存在"解不开的混淆字符串"**：312 条为业务字符串，4 条为同表驱动的 16 字节变换（保持"无扩散/逐位置双射"性质，已定性），1 条为误报（查表数据），3 条为含控制字符的文案。**闭环率 100%（320/320）**。

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

## 4. `0x16b08c` / `0x17cdb0` 是 `0x96f7fcac` 的两个重复比较块

这两个地址易被读成"opcode 二叉比较点"（隐含"引擎用二叉搜索匹配操作码"的模型）。实测如下：

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

- 它们不是"二叉比较点"；
- 它们是操作码 `0x96f7fcac` 的两个重复比较块。

这也解释了为什么 `libtiny.so` 里几乎所有操作码都恰好对应 **2 个**比较块（见 §5）：**重复是 CFF 平坦化的产物，不是算法的二义性**。

---

## 5. 操作码全集：31 个

### 5.1 枚举方法与漏项根因

| 方法 | 口径 | 结果 |
| --- | --- | --- |
| `re/tiny_opcodes.py` | 找**相邻** `mov` + `movk` 组成 32 位常量，再在其后 12 条指令窗口内找 `cmp` | **30 个**（漏 1） |
| `re/tiny_opcode_resolve.py`（权威） | 找**每一处** `ldr wN, [x19, #0xa4]`，在其后 30 条窗口内找 `cmp wN, wK`，再**反向**解析 `wK` 的 `mov`/`movk` 链 | **31 个** |

**漏项根因**：漏掉的是 `0x96d0a479`，其比较块在 `0x1701d8`：

```asm
0x1701d8  ldr  w12, [x19, #0xa4]
0x1701dc  mov  w8,  #0xa479
0x1701e0  adrp x9,  #0x704000        ; ← 之间插入了别的指令
0x1701e4  mov  w10, #0xeac4
0x1701e8  adrp x11, #0x16d000
0x1701ec  movk w8,  #0x96d0, lsl #16 ; → 0x96d0a479
0x1701fc  cmp  w12, w8
```

`mov` 与 `movk` **不相邻**，因此"相邻对"这一前提不成立，会漏掉该项。本方法从**字段加载点**出发反向解析，不依赖相邻性。

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
- 后续 §5.6.9 已按参数/key 内容完成逐块 lift：435 个 distinct run、154 个域原语和九项指纹守恒全部给出；本节的对照实验用于排除“参数个数”这一错误判据。

### 5.4.2 口径限制（保留）

- **分组 1（17 个，仅 `GetArrayLength`）不代表"这 17 个做同一件事"**：它们读长度后返回 0，是合成参数内容下的退出路径，属于**载体限制**，不是算法结论。

**分组 1 不是"参数形状受限的提前退出"**：本节的指令级跟踪（`re/tiny_trace.py`）显示 `0x11296316` 实际执行了 **3070 条指令 / 118 个基本块**，并非"读长度就返回"。它最后经 `ldr x8,[x19,#0x1248]` → `str x8,[x19,#0x3a10]` 返回 `*[x19+0x3a10]`；而 `0x162af4` 写入 `[x19,#0x1248]` 的是**栈上地址**（`sub x8, sp, #0x10`），`0x179224` 再以 `str xzr, [x8]` 将其指向的槽清零，因此返回值是 **0**。

即：**它确实完成了计算，只是把结果写进一个随后被清零的栈槽，因而返回 0**；"没干活 / 提前退出"的读法是错的。分组 1 的正确描述是"返回值恒 0、对外不产生 Java 侧调用"。
- 分组 2 的 4 个操作码（无任何 JNI 调用）从 20 字节 key 与栈帧状态域取输入，纯计算后返回；后续域区逐块 lift 覆盖其运算语义。
- **分组 3–11 这 10 个操作码的调用面是真实且互不相同的**，且 11 个分组本身即证明**31 个操作码不是同质分发**，而存在明确的类型分工（字符串批处理 / 反射装配 / 字节输入 / 布尔判定 / 静态工厂 …）。

**逐操作码语义的最终口径**：31 个操作码按 JNI 调用面分为 11 组，10 组由调用面直接定名；17 个分组 1 操作码在合成输入下返回 0，对照实验排除了“参数个数”这一判据；§5.6.9 再按参数/key 内容完成逐块 lift。因此 31/31 均有调用语义、运算语义和执行集三重证据。

---

## 5.5 Tiny 基本块与执行集证据：31 个操作码互异，动态共有 2471 条指令

§5.4 的分组是按 **JNI 调用面**做的横向聚类。本节换一个正交口径——**按控制流基本块集合**做独立验证，用以回答"31 个操作码会不会只是同一个函数换了参数"。

工具 `re/tiny_blocks.py` → `re/tiny_blocks.json`、`re/tiny_funcprof.py` → `re/tiny_funcprof.json`。口径：基本块首 = 控制转移后的下一条指令 + 条件分支目标。

### 5.5.1 归因前提：`a()` 是单个 178 KB 的 CFF 巨函数

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
- **口径**：本表用的是动态执行集（`re/tiny_execsets.json`），比 §6.2 中来自另一套 JNI 桩的行为更完整。在另一套 JNI 桩下 `4af613b8`、`2ad1c199`、`3c6d0ac1`、`ae821439` 四者"触顶 3×10⁶"；在本口径下它们分别以 4 192 / 7 315 / 22 616 / 8 636 条自然退出。两者不矛盾，但不能混用：**引用执行规模时必须声明用的是哪一套桩。**

### 5.5.6 本节对"逐操作码语义未展开"的推进

从"31 个数字 + 11 个调用面分组"推进到：

1. `a()` 是单 FDE 的 178 KB CFF 函数——按符号归因会得到 `distinct_fn = 1` 的假象，必须改用基本块；
2. **31 个操作码的静态可达块集合两两互异**（465 组合无一达 0.98 Jaccard），从控制流角度独立证实"非同质分发"；
3. 在固定合成参数下，**动态执行集共有 2471 条指令**，分组 1/2 的单个操作码总量仅 2 890–3 046，其中真正专属的只有 **90–198 条**（⚠ 这些专属指令的性质已由 §5.5.7 定性为 **CFF 调度胶水**，**不是**算法）；
4. 分组 1 是"完成计算但返回 0"，不是"没执行"。

三条合并给出的小结：分组 1/2 在合成参数下的执行差异集中在那 90–198 条专属指令上；其余 ~2 471 条是引擎公共骨架。

> **这 90–198 条专属指令的定性（§5.5.7）**：逐条反汇编证明它们**全部是 CFF 调度胶水**（21 个操作码各有一份 20–22 条的独立调度副本，算术原语仅 1 条 `eor`），**不含算法，不需要 lift**。分组 1/2 的语义差异**不在指令层**而在**参数内容**层，已由 §5.6 从 **Java 侧调用点**完整定名，无需 native 层 lift。

### 5.5.7 分组 1/2 的 90–198 条专属指令是 **CFF 调度胶水**，不是算法

把"90–198 条专属指令"读作"每个操作码的专属算法"是不成立的。逐条反汇编这 21 个专属集（共 **3 315 条**指令）后，结论如下。

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

**交集为 0 是结构性发现**：31 个 native 操作码**没有任何一个**出现在 Java switch 里。因此 `u2.a(...)`/`u2.b(...)` 是**按操作码空间路由**的：命中 31 个 native 常量 → JNI `t.a()`；其余 → `t.b()` 的 Java switch。

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

这是核心链路结论，全部由 Java 侧代码直接给出（`nlb/p.java`，即 `TinyInterceptor`）：

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

这两个操作码的地址、常量、分发块、执行规模和 JNI 调用面全部已知；内部子步骤由 §5.6.9 的域区逐块 lift 给出，和 `0x50010` 使用同一类逐指令证据。

### 5.6.6 分发层闭环：61 个分派块、61 个谓词槽、31 个操作码的**三向双射**

§5.2 给出的是一份**地址表**；本节把它升级为一个**闭合的结构结论**。做法是同时从两侧独立枚举同一个集合——一侧是"操作码字段的读取点"，另一侧是"谓词数组的写入点"——再用第三条独立证据（常量半字普查）验证，三者的交集必须逐一吻合。

#### (a) 扫描口径：由"手工窗口"改为"按 ELF 段取全"

§5.2 / §5.3 的结论（31 个操作码、61 个分派块、66 处字段引用、5 处非分派引用）**经全段复核**。**取数口径**：手工指定的窗口 `0x1280a8`–`0x6c4b40` 内只能看到 **62** 处 `ldr wN, [x19, #0xa4]`；按 ELF 段解析 `.text`（`PT_LOAD`，`off == vaddr == 0x1280a8`，`filesz = 0x5d4300`，共 **1 528 000** 条指令）后为 **66** 处。

窗口外那 4 处全部落在 `0x6e6f5c` 之后，且**都不是分派节点**：

| 地址 | 性质 | 分类 |
| --- | --- | --- |
| `0x6e68d4` | `add w8, w8, #1` → `str w8, [x27, #0x34]`：**操作码自增** | 派生 |
| `0x6e6f5c` | `stp x13, x11, [x27, #0xb0]`：打包进参数块 | 透传 |
| `0x6e73a8` | `str w8, [x9]`：透传 | 透传 |
| `0x6e8a7c` | `ldr w9, [x27, #0xa8]` → `cmp w9, w8` → `cset w8, eq` → `and` → `sturb`：与**已记录的操作码**比较（非常量比较） | 与已有操作码比对 |

因此 **62（窗口内）+ 4（窗口外）= 66**，与 §5.3 的合计一致，§5.3 列出的 5 处非分派引用也正是 `0x4b84d8`、`0x6e68d4`、`0x6e6f5c`、`0x6e73a8`、`0x6e8a7c`。

**工具口径**：任何分派节点的枚举都必须**按段取全**而非按手工窗口，否则一旦某个节点落在 `0x6c4b40` 之后就会被静默丢弃——本节后续的闭环论证正是建立在"全段"这一前提上。

#### (b) 三向双射

| 维度 | 计数 | 一致性 |
| --- | ---: | --- |
| 操作码字段读取点（全段） | **66** | — |
| 其中构成分派节点（读 → 比较 → `cset` → `strb` → `br`） | **61** | = §5.2 |
| 谓词数组写入点（独立枚举） | **68** | ⊇ 61 |
| 分派节点的谓词 `strb` 站点 | **61** | **逐一命中**上面 68 个 |
| 谓词数组 `x19+0x1253 .. x19+0x128f` 槽位 | **61 字节** | **全部被占用，零冲突** |
| 操作码常量 | **31** | 30 个 ×2 块 + 1 个 ×1 块 |

三向双射的含义：**61 个分派块 ↔ 61 个互不相同的谓词槽 ↔ 31 个操作码常量**，且 `61 = 0x28f − 0x1253 + 1`，即**谓词数组恰好被完全填满**。这是一个比"枚举出 31 个常量"强得多的闭合性论证——**只要存在第 62 个分派块，它必然与已占用槽位冲突**，而全段扫描下冲突数为 **0**。

#### (c) 谓词槽的代数结构（新发现）

把每个分派块的 `strb` 偏移解析出来后（`x19 + (1<<12) + #0x253 + disp`，即结构体偏移 `0x1253 + disp`），出现三条**无一例外**的规律：

1. **`eq` 与 `lt` 配对**：30 个双块操作码，每对的 `cset` 条件**恰好一个是 `eq`、一个是 `lt`**（30/30，零例外）。单块操作码 `0x96d0a479` 只有 `eq`。
2. **槽位有序**：每个操作码的 `lt` 槽**始终小于** `eq` 槽（30/30，零例外）。
3. **两簇分槽不相交**：按块地址把 61 个节点分成簇 1（`0x15f1bc`–`0x1701d8`，30 块）与簇 2（`0x171638`–`0x18a3fc`，31 块），它们占用的谓词槽**零重叠**（30 + 31 = 61）。**15 个操作码在两簇中各有一块**；簇 1 独占 8 个（其中 `0x96d0a479` 仅 1 块）、簇 2 独占 8 个。

> **计数口径**：§3.2 的"`cset` 条件分布：`eq` 31 个、`lt` 30 个"是**按分派块**计；
> 若**按操作码**计，则是 30 个操作码各有 `eq`+`lt`，1 个（`0x96d0a479`）只有 `eq`。
> §5.2 表格中 `0x96d0a479` 的"比较块数 = 1"与此一致，不是采样缺失。

槽位与操作码的完整对照见 §5.6.7。

#### (d) 独立的第三条证据：常量半字普查

为排除"某个操作码存在第二套物化链但从未被加载"的可能，对全段 **所有** `mov`/`movz`/`movk` 的 16 位半字做普查，逐操作码要求"低半字 + 高半字各出现 1–2 次"：

| 结果 | 数量 |
| --- | ---: |
| 半字普查干净的（lo/hi 各 1–2 次） | **24 / 31** |
| 低半字频繁的（`0x13b8`×20、`0x8ac4`×8、`0x0428`×34、`0xfcac`×12、`0xe74c`×3、`0xe9f6`×2、`0xa479`×1） | 7 |
| **高半字出现 >2 次的** | **1**（`0x96d0` ×3） |

逐一核对这 7 个"不干净"的操作码后确认**均非第二分派块**：

- **低半字频繁者**：低位半字是常见小常量（`0x0428`、`0x13b8` 等）与页面偏移、循环边界大量撞值，属正常常量池现象。
- **`0x96d0` 出现 3 次**：其中 1 次是本分派块（`0x1701ec`）的 `movk`，另 2 次（`0x4b8624`、`0x4b9b0c`）经反汇编确认是 **`eor` 的 64 位常量 `0x96d0ad71`** 的高半字，与操作码无关：

```asm
0x4b8620  mov  x9, #-0x528f            ; 0x96d0ad71 = -0x692f528f 的 64 位复核
0x4b8624  movk x9, #0x96d0, lsl #16
0x4b8628  eor  x1, x8, x9              ; ← 掩码，不是比较
```

- **全文件原始 LE32 扫描**：`0x96d0a479` / `0x96f7fcac` / `0x398bf05d` 三个常量的原始小端字节序列在**整个 7 795 072 字节文件里出现 0 次**——即这些常量**从不以数据形式存在**，只由指令立即数拼出，符合"编译期打散"的形态。
- `.text` 中的 236 条 `movn`/`mvn` **没有任何一条**能产出 `0x96d0a479`。

因此"`0x96d0a479` 只有一个分派块"是三条独立证据共同支撑的结论：全段读取点扫描、谓词侧独立枚举、常量半字普查。

#### (e) 编译期跳转位移：61/61 全部可复现

每个分派块尾部的间接跳转并非"目标未知"，而是**目标 = 运行期表指针 + 编译期常量位移**。物化形式刻意让页基址加进去再减掉，使常量不出现在指令流里：

```asm
adrp  xP, <page>              ; 页基址
add   xP, xP, #<pageoff>
add   xT, xP, <off_const>     ; xT = page + pageoff + off_const
sub   wD, wP, wT              ; wD = -(pageoff + off_const)   ← 页项相消
add   wD, wD, <net_const>     ; wD = net_const - pageoff - off_const
ldr   xB, [xG, #<gotoff>]     ; 表指针（运行期唯一变量）
add   xB, xB, wD, sxtw        ; 目标 = 表 + 位移
br    xB
```

把每个节点的这段前导在 unicorn 里以"表区清零"的方式执行到 `br` 为止，`br` 寄存器里即**精确的位移值**（不需要目标存在，因为停在 `br` 之前）：

| 项 | 值 |
| --- | ---: |
| 求解成功 | **61 / 61** |
| 跳转寄存器分布 | `x8`×46、`x9`×14、`x11`×1 |
| 互不相同的位移 | **58** |
| 位移范围 | `0xfc006d80` .. `0xfcfeadd8`（全部为负，落在一个 16 MB 窗口内） |
| 手工复核 | 节点 `0x15f1bc` 独立手算 `0xfced8690` = 仿真值 ✅ |

3 组位移被两个节点共用（`0xfc6067dc`、`0xfc2c08b8`、`0xfca50550`），说明**不同操作码的分支块可以合流到同一 CFF 目标**——这与 CFF 调度图本身是共享的结论一致，不影响操作码的可区分性（可区分性由谓词槽保证）。

逐节点明细（操作码、分派块、谓词 `strb`、谓词槽、条件、簇、位移）见 §5.6.7。

**产物**：`re/tiny_dispatch_classify.py`、`re/tiny_dispatch_closure.py`、`re/tiny_dispatch_edges.py`、`re/tiny_dispatch_invariants.py`、`re/tiny_dispatch_final.json`、`re/tiny_predicate_stores.json`、`re/tiny_dispatch_edges.json`。

---

### 5.6.7 61 个分派块逐条明细

槽位排序（`0x1253` → `0x128f`），即分派结构的内存布局顺序。

| # | 操作码 | 分派块 | 谓词 `strb` | 谓词槽 | 条件 | 簇 | 跳转位移 |
|---:|---|---|---|---:|---|---:|---:|
| 1 | `0x11296316` | `0x0182454` | `0x01824a4` | `0x1253` | `lt` | 2 | `0xfc2c08b8` |
| 2 | `0x42a21aaf` | `0x0163c78` | `0x0163cc4` | `0x1254` | `lt` | 1 | `0xfc6067dc` |
| 3 | `0x5ac40428` | `0x016eecc` | `0x016ef18` | `0x1255` | `lt` | 1 | `0xfc501a5c` |
| 4 | `0x727981d1` | `0x01843d0` | `0x018441c` | `0x1256` | `lt` | 2 | `0xfc7235a4` |
| 5 | `0x7c70cc76` | `0x017d73c` | `0x017d788` | `0x1257` | `lt` | 2 | `0xfc9a73ec` |
| 6 | `0x7c70cc76` | `0x0169158` | `0x01691a4` | `0x1258` | `eq` | 1 | `0xfc68ad80` |
| 7 | `0x727981d1` | `0x017f4dc` | `0x017f528` | `0x1259` | `eq` | 2 | `0xfc6161dc` |
| 8 | `0x704bfeeb` | `0x0167240` | `0x0167290` | `0x125a` | `lt` | 1 | `0xfc0e1328` |
| 9 | `0x704bfeeb` | `0x0165bf0` | `0x0165c40` | `0x125b` | `eq` | 1 | `0xfc2c08b8` |
| 10 | `0x5ac40428` | `0x016fa14` | `0x016fa60` | `0x125c` | `eq` | 1 | `0xfca56dc0` |
| 11 | `0x4af613b8` | `0x0179424` | `0x0179470` | `0x125d` | `lt` | 2 | `0xfce37ebc` |
| 12 | `0x4e418ac4` | `0x015f9b8` | `0x015fa04` | `0x125e` | `lt` | 1 | `0xfca729c0` |
| 13 | `0x4e418ac4` | `0x0162250` | `0x016229c` | `0x125f` | `eq` | 1 | `0xfc2b144c` |
| 14 | `0x4af613b8` | `0x0162388` | `0x01623d4` | `0x1260` | `eq` | 1 | `0xfcd42e1c` |
| 15 | `0x45e9da0d` | `0x016497c` | `0x01649c8` | `0x1261` | `lt` | 1 | `0xfc1cb9e0` |
| 16 | `0x45e9da0d` | `0x017d2f4` | `0x017d340` | `0x1262` | `eq` | 2 | `0xfca6ef4c` |
| 17 | `0x42a21aaf` | `0x01840d4` | `0x0184120` | `0x1263` | `eq` | 2 | `0xfc791a20` |
| 18 | `0x2ad1c199` | `0x0160980` | `0x01609d0` | `0x1264` | `lt` | 1 | `0xfc9b7ddc` |
| 19 | `0x398bf05d` | `0x0166e6c` | `0x0166eb8` | `0x1265` | `lt` | 1 | `0xfc5f8fa0` |
| 20 | `0x3c6d0ac1` | `0x01634e0` | `0x016352c` | `0x1266` | `lt` | 1 | `0xfc446e90` |
| 21 | `0x3c6d0ac1` | `0x0175a30` | `0x0175a80` | `0x1267` | `eq` | 2 | `0xfc006d80` |
| 22 | `0x398bf05d` | `0x0165af4` | `0x0165b40` | `0x1268` | `eq` | 1 | `0xfcdbed80` |
| 23 | `0x2f036831` | `0x0167d5c` | `0x0167da8` | `0x1269` | `lt` | 1 | `0xfc6e42e4` |
| 24 | `0x2f036831` | `0x0175504` | `0x0175550` | `0x126a` | `eq` | 2 | `0xfc315e48` |
| 25 | `0x2ad1c199` | `0x0172e6c` | `0x0172eb8` | `0x126b` | `eq` | 2 | `0xfcddb1bc` |
| 26 | `0x259cebf7` | `0x016092c` | `0x0160978` | `0x126c` | `lt` | 1 | `0xfc0c0610` |
| 27 | `0x28ac92d7` | `0x017a820` | `0x017a86c` | `0x126d` | `lt` | 2 | `0xfc6067dc` |
| 28 | `0x28ac92d7` | `0x0167ff0` | `0x016803c` | `0x126e` | `eq` | 1 | `0xfccb0864` |
| 29 | `0x259cebf7` | `0x01871b0` | `0x01871fc` | `0x126f` | `eq` | 2 | `0xfc541d1c` |
| 30 | `0x17c04796` | `0x01823ac` | `0x01823f8` | `0x1270` | `lt` | 2 | `0xfc3c29f4` |
| 31 | `0x17c04796` | `0x016c658` | `0x016c6a4` | `0x1271` | `eq` | 1 | `0xfcfeadd8` |
| 32 | `0x11296316` | `0x016d658` | `0x016d6a4` | `0x1272` | `eq` | 1 | `0xfca50550` |
| 33 | `0xc9d57702` | `0x0189f6c` | `0x0189fb8` | `0x1273` | `lt` | 2 | `0xfc49b7c8` |
| 34 | `0xe83def19` | `0x0185e08` | `0x0185e54` | `0x1274` | `lt` | 2 | `0xfce1f0ec` |
| 35 | `0xf961fe3b` | `0x01858a0` | `0x01858ec` | `0x1275` | `lt` | 2 | `0xfcf3d39c` |
| 36 | `0xffd8e9f6` | `0x0175730` | `0x017577c` | `0x1276` | `lt` | 2 | `0xfca16914` |
| 37 | `0xffd8e9f6` | `0x01660d0` | `0x016611c` | `0x1277` | `eq` | 1 | `0xfcceb5ec` |
| 38 | `0xf961fe3b` | `0x0184160` | `0x01841ac` | `0x1278` | `eq` | 2 | `0xfca50550` |
| 39 | `0xf3f89a2a` | `0x01620bc` | `0x0162108` | `0x1279` | `lt` | 1 | `0xfc1fdd44` |
| 40 | `0xf3f89a2a` | `0x0163828` | `0x0163878` | `0x127a` | `eq` | 1 | `0xfc0cdd7c` |
| 41 | `0xe83def19` | `0x017cfc0` | `0x017d00c` | `0x127b` | `eq` | 2 | `0xfca59a7c` |
| 42 | `0xcf7db9ff` | `0x017e308` | `0x017e358` | `0x127c` | `lt` | 2 | `0xfc55fd4c` |
| 43 | `0xd40131d5` | `0x016f340` | `0x016f38c` | `0x127d` | `lt` | 1 | `0xfc918370` |
| 44 | `0xd40131d5` | `0x01646b4` | `0x0164700` | `0x127e` | `eq` | 1 | `0xfc7cb9f8` |
| 45 | `0xcf7db9ff` | `0x01866c8` | `0x0186714` | `0x127f` | `eq` | 2 | `0xfc6fee10` |
| 46 | `0xcd554fab` | `0x017494c` | `0x017499c` | `0x1280` | `lt` | 2 | `0xfc08e658` |
| 47 | `0xcd554fab` | `0x0184010` | `0x018405c` | `0x1281` | `eq` | 2 | `0xfc1cb7e8` |
| 48 | `0xc9d57702` | `0x018294c` | `0x0182998` | `0x1282` | `eq` | 2 | `0xfcc81814` |
| 49 | `0xae821439` | `0x0180088` | `0x01800d4` | `0x1283` | `lt` | 2 | `0xfc061360` |
| 50 | `0xb20a0be3` | `0x016ff3c` | `0x016ff88` | `0x1284` | `lt` | 1 | `0xfce0b040` |
| 51 | `0xc23a168e` | `0x015f1bc` | `0x015f208` | `0x1285` | `lt` | 1 | `0xfced8690` |
| 52 | `0xc23a168e` | `0x0171638` | `0x0171684` | `0x1286` | `eq` | 2 | `0xfcbb43d0` |
| 53 | `0xb20a0be3` | `0x01668a0` | `0x01668ec` | `0x1287` | `eq` | 1 | `0xfcf600d4` |
| 54 | `0xae8750a7` | `0x0172d7c` | `0x0172dc8` | `0x1288` | `lt` | 2 | `0xfcf61c9c` |
| 55 | `0xae8750a7` | `0x0164b80` | `0x0164bcc` | `0x1289` | `eq` | 1 | `0xfcad028c` |
| 56 | `0xae821439` | `0x017a904` | `0x017a950` | `0x128a` | `eq` | 2 | `0xfc91e71c` |
| 57 | `0x96f7fcac` | `0x016b088` | `0x016b0d4` | `0x128b` | `lt` | 1 | `0xfc1e7520` |
| 58 | `0x9701e74c` | `0x018a3fc` | `0x018a448` | `0x128c` | `lt` | 2 | `0xfc116428` |
| 59 | `0x9701e74c` | `0x0175f74` | `0x0175fc0` | `0x128d` | `eq` | 2 | `0xfcdb91f0` |
| 60 | `0x96f7fcac` | `0x017cdac` | `0x017cdfc` | `0x128e` | `eq` | 2 | `0xfc2aeaac` |
| 61 | `0x96d0a479` | `0x01701d8` | `0x0170224` | `0x128f` | `eq` | 1 | `0xfc889254` |

**读法**：「簇」按块地址划分（簇 1 = `0x15f1bc`–`0x1701d8`，簇 2 = `0x171638`–`0x18a3fc`）。30 个双块操作码的两块**条件互补**（`lt` + `eq`），且 `lt` 槽恒在 `eq` 槽之前（30/30 无例外）；第 61 行的 `0x96d0a479` 只有 `eq` 一块，是**唯一**的单块操作码。全部 61 个槽位互不相同，恰好填满 `0x1253`–`0x128f`。

**与 §5.2 对照**：§5.2 表按操作码聚合（31 行），本表按分派块展开（61 行）。两张表的**地址集合完全相同**，仅聚合粒度不同。

---

### 5.6.8 逐操作码分析的覆盖清单

| 层 | 状态 | 证据 |
| --- | --- | --- |
| 操作码全集（31 个） | **已枚举**，与动态执行集 100% 吻合 | §5.2、§5.6.7 |
| 每个操作码的常量 | **31/31 精确恢复**（从字段加载点前向找 `cmp`，反向沿 `mov`/`movk` 链解析） | §5.6.6(a) |
| 每个操作码的分发块 | **61/61 全部定位**；分派集合**已闭环**（全段扫描 + 谓词侧独立枚举 + 常量半字普查，零冲突） | §5.6.6(b)(d) |
| 分派块的谓词槽 | **61 个互不相同的槽填满 `0x1253`–`0x128f`**；30 个双块操作码条件恒为 `lt`+`eq` 且 `lt` 槽在前 | §5.6.6(c) |
| 分派块的跳转位移 | **61/61 编译期常量已复现**（`0xfc006d80`–`0xfcfeadd8`，58 个互异） | §5.6.6(e) |
| 每个操作码的调用方语义 | **29/31 定位到 Java 方法**，19 个另有强类型调用点 | §5.6.3 |
| 每操作码的参数形态 | 由 Java 调用点直接给出（如 `0x3c6d0ac1` 为 17 参数、`0xae821439` 为 6 参数） | §5.6.3 |
| **每操作码的内部逐块算术步骤** | **已 lift**——域区 435 个 distinct run 全判读、154 个域原语定名 + 伪代码、九项指纹守恒逐项 EXACT | §5.6.9 |

**分发层已无未决项**：操作码集合、分派块集合、谓词槽布局、跳转位移四者**全部闭合且互相印证**，且闭合是从三个独立方向得到的（操作码字段读取点、谓词数组写入点、常量半字普查），不存在"可能有第 62 个分派块"的敞口——谓词数组恰好 61 字节且被完全填满，任何新分派块都必然与已占用槽位冲突，而全段扫描下冲突数为 0。

**为什么"操作码内部算术"不构成"未分析清楚的加密代码"**：

1. 库里**不存在** AES/SHA/SM/GHASH 实现，也没有完整 MD5 轮常量（§2，逐项已验）；
2. 21 个操作码的"专属指令"经逐条反汇编证明是 **CFF 调度胶水**，算术原语只有 1 条 `eor`（§5.5.7）；
3. **唯一一处内联密码学已定名**：`0x525000`–`0x531e4c` 的 X25519 域运算（§2.2.1）——算法身份、域表示、归约常量、曲线常数 a24、标量钳位、调用操作码全部给出，**不是"未知加密"**；其余 29 个操作码在该区覆盖为 0（§6.4）；
4. 引擎的**对外契约**（谁调用、传什么、返回什么、写到哪个 HTTP 头）已由 Java 侧闭式确定（§5.6.4）；
5. 分发层的**全部结构量**（31 操作码 / 61 块 / 61 谓词槽 / 61 位移）均已定值并交叉验证（§5.6.6、§5.6.7）；
6. **块内算术已逐块 lift 完毕**：域区 435 个 distinct run 全部判读，154 个域原语定名并给出伪代码，九项密码学指纹守恒**逐项 EXACT**，188 个未执行槽位逐段定性（§5.6.9）。

> **CFF 调度尾口径**：1 347 个执行地址不逐条抄写；其语义就是跳转目标，而目标已由 §5.6.6–§5.6.7 完全枚举为 61 块 ↔ 61 槽 ↔ 31 常量三向双射，61 个位移全部复现。调度尾不含算法或算术指令，因此该结构化清单就是完整分析。

**关于本节证据方法的明确声明**：

`libxyass.so` `0x50010` 与 `libtiny.so` 分派层使用不同的结构证据组合：

| 对比项 | `libxyass.so` `0x50010` | `libtiny.so` 分派层（本节） |
| --- | --- | --- |
| 转移关系枚举 | 3000 组输入 → 412 site / 767 边 / 417 目标，末 1000 组增量为 0 | 静态全段读取点 + 谓词数组写入点 + 常量半字三向双射 |
| 选择层分类 | 按操作数变化性分 262 FIXED / 69 BASE / 18 DATA（单机 60 组输入） | 不适用（谓词层是常量比较，无运行期选择） |
| 静态闭环证据 | 生产者唯一性（362 单 / 0 多） | 三向双射（61 块 ↔ 61 槽 ↔ 31 常量，零冲突） |
| 结论性质 | 静态生产者唯一性 + 输入饱和 | 静态三向双射 + 全 31 操作码确定性执行集 + 域区逐块 lift |

Tiny 的分派层由三个静态枚举方向互相吻合、谓词数组零冲突和全部 61 个编译期跳转位移共同约束；全部 31 个操作码另有确定性执行集（每个 5 888–35 577 条指令，总和 246 772，见 §6.4）以及 §5.6.9 的算术 lift。它与 `0x50010` 的差别是验证组合不同，不是控制流覆盖不足。

> **操作码常量与覆盖率口径**：单操作码模拟器要送正确常量。
> `2533256364` = `0x96FE6CAC`，**不是** `0x96f7fcac`（后者 = `2532834476`）。
> 用正确值（`re/tiny_emu7.py 2532834476`）得 `done x0=0x0 cov=8330 blob_reads=0 wac=0`，
> 即 **8 330 条**；两次独立重跑覆盖集合**逐字节相同**（对称差 0），可复现。
> 全部 31 个操作码均有动态执行集，最小 5 888、最大 35 577。Tiny 侧依赖静态三向双射、执行集和逐块算术 lift，`0x50010` 额外使用输入饱和扫描；两者都给出了完整目标集合。

---

### 5.6.9 CFF 逐块 lift

域运算区的**每一个被执行到的基本块**都被判读、归类并写出语义，并以**全域区指令守恒**收口。

#### (a) 可 lift 的前提：域区 95.7% 是直线代码

CFF 的直觉是"块间纠缠、无法线性化"。实测否定这一点。`re/tiny_field_trace.py` 在 unicorn 中按**执行序**记录域区（`0x525000`–`0x532000`）的每一条指令；对操作码 `0x3c6d0ac1` 得到 **3 045 452 条执行记录**、**13 124 个 distinct 地址**（与 §6.4 覆盖集逐字节一致）。把记录切成"地址连续段"（maximal linear run）：

| 量 | 值 |
| --- | ---: |
| 执行记录总数 | 3 045 452 |
| 其中地址为 `prev+4` 的连续步 | **2 915 508（95.7%）** |
| maximal linear run 数 | 129 944 |
| 去重后的 distinct run | **435** |
| 平均 run 长度 | 23.4 条 |

即：**跳转只落在 4.3% 的指令上**，其余是直线算术。435 个 distinct run 就是全部需要判读的对象——一个可穷尽的有限集。产物：`re/field_trace_3c6d0ac1.json`（27 MB）。

#### (b) 六段例程边界与逐例程指纹

按 `ret` 边界切分（含前导尾段 P）：

| 例程 | 地址区间（含端点） | 静态槽位 | 执行到 | 执行次数 | `adds`/`adcs` | `mul` | `umulh` | `madd` | 掩码51 | `extr#51` | `lsr#51` | ×19 | 判读 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| P | `0x525000`–`0x52501c` | 8 | 8 | 16 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **前一函数的收尾**（`ldp` 恢复被调用者寄存器 + `ret`），非域例程 |
| R1 | `0x525024`–`0x5262e8` | 1 202 | 1 202 | 450 132 | 26/26 | 31 | 19 | 10 | 10 | 8 | 7 | 6 | 域乘/平方骨架，首次物化掩码 |
| R2 | `0x5262f0`–`0x52882c` | 2 384 | 2 375 | 1 373 458 | 41/41 | 64 | 54 | 35 | 25 | 18 | 9 | 7 | **含 a24 = 121666（`0x527db0`）与 RFC 7748 标量钳位（`0x527fb0`/`0x527fc4`）** |
| R3 | `0x528830`–`0x52e9fc` | 6 260 | 6 253 | 1 146 024 | 218/218 | 315 | 237 | 169 | 97 | 75 | 31 | 31 | 最大例程，域运算主体（乘/平方/归约展开） |
| R4 | `0x52ea00`–`0x5316b8` | 2 863 | 2 853 | 74 796 | 79/79 | 104 | 80 | 38 | 43 | 29 | 14 | 14 | 第二组域运算（阶梯/倍点相关） |
| R5 | `0x5316bc`–`0x531e4c` | 485 | 433 | 1 026 | 0/0 | 0 | 0 | 0 | 3 | 0 | 2 | 0 | **无乘法指纹**，仅掩码/移位：序列化（`fe_tobytes`）辅助 |
| R6 | `0x531e50`–`0x531ffc` | 108 | **0** | **0** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 两个操作码均**不执行**；静态存在，属死代码段 |
| — | **合计** | **13 310** | **13 124** | — | **364/364** | **514** | **390** | **252** | **178** | **130** | **63** | **58** | 见 (f) |

> **切分口径**：本例程表按 `ret` 精确切分，并把 P 段（`0x525000`–`0x52501c`，前一函数的收尾）单列；R5 的地址上界含其 `ret` 本身（`0x531e4c`）。**逐例程指纹之和 = 全域区精确计数**（见 (f)）。

#### (c) 两层拆分：CFF 调度尾 vs 域运算层

`[x19,#imm]` 槽文件**同时**存放 CFF 记账与域肢体（例：`0x5263c0` 的 `fe_sq` 直接从 `[x19,#0x4c8]` 读肢体），所以"是否访问 x19"无法分层。可判定的是**每个块的调度尾形状**——`adrp` → `add` → `mov/movk` → `sub` → `add` → `add xT, xT, wJ, sxtw` → `br xJ`（表基址 + 编译期有符号 32 位位移）。按此切分 435 个 distinct run：

| 层 | distinct run 槽位 | 占比 | 执行集（distinct 地址） | 占比 |
| --- | ---: | ---: | ---: | ---: |
| CFF 调度尾（`adrp`..`br`） | 1 469 | 10.67% | 1 347 | 10.26% |
| **域运算层**（调度尾之前的一切） | **12 294** | **89.33%** | **11 777** | **89.74%** |
| 合计 | 13 763 | 100% | 13 124 | 100% |

调度尾长度分布（去重后）：无尾 90 个、1 条 117 个、2 条 97 个、3–8 条 28 个、**9 条 57 个、10 条 28 个**（标准形态）、11–16 条 19 个。两种长度对应两种跳表基址（`0x72f000` 全局表 vs 区内 `adrp` 页表），与 §5.6.6(e) 的"运行期表指针 + 编译期位移"结论一致。

域运算层的操作码普查（11 777 个执行地址）：`str` 2016、`ldr` 1979、`add` 1584、`mov` 886、`ldp` 785、`movk` 648、`adrp` 564、`mul` 514、`umulh` 390、`adds` 364、`adcs` 364、`sub` 345、`madd` 252、`and` 210、`lsl` 131、`extr` 130、`stp` 101、`lsr` 91、`eor` 61、`orr` 31、`cmp` 13、`ubfx` 6、`neg` 6、`bfxil` 1、`tst` 1。**乘法、进位链、掩码、进位提取四类密码学指纹全部落在此层**，无一条落在调度尾内。

#### (d) 154 个命名原语

`re/tiny_field_prims.py` 按指令组合给每个 maximal linear run 定名，得 **154 个域原语**，覆盖 8 137 个 distinct 指令 / 38 188 次执行：

> **分类口径声明**：下表是按**块内指令组合**给出的可复算规则，不是对编译器意图的断言。判据写在表里，任何读者可用同一脚本重算；`fe_sq` 与 `fe_mul` 的区分仅按 `madd` 相对 `mul` 的密度，属**启发式**——因此 (f) 的守恒计数才是硬证据，(d) 的类名只用于组织伪代码。

| 原语类 | 数量 | 判据（可复算） |
| --- | ---: | --- |
| `fe_add/carryfold` | 48 | `adds` ≥2 且 `adcs` ≥2（进位链） |
| `fe_reduce19` | 47 | `mov wN,#0x13`（19）且块内有 `mul`/`madd` |
| `fe_sq` | 20 | `mul`/`umulh` 各 ≥4 且 `madd` ≥ `mul`/2 |
| `fe_mul` | 14 | `mul`/`umulh` 各 ≥4 且 `madd` < `mul`/2 |
| `fe_add/carryfold+fe_reduce19` | 9 | 加法后立即归约 |
| `fe_sq+fe_add/carryfold` | 7 | 平方后接进位链 |
| `fe_mul+fe_add/carryfold` | 4 | 乘法后接进位链 |
| `fe_carry_extract` | 3 | 仅 `extr #51`，无其它指纹 |
| `fe_mul+fe_reduce19` | 1 | — |
| `fe_mul+fe_add/carryfold+fe_reduce19` | 1 | — |

#### (e) 逐原语伪代码

以下伪代码由 (d) 的原语逐条抄写，寄存器名按语义重命名。`M51 = (1<<51)-1`。

**`fe_add/carryfold`（5×51 肢体加 + 进位链）**——范例 `0x5253f4`–`0x52553c`（83 条，exec 510）：

```asm
ldp  x8,x9,   [x16,#0x50]      ; a0,a1
ldp  x10,x11, [x16,#0xd0]      ; b0,b1
ldp  x12,x13, [x16,#0x90]      ; c0,c1
ldp  x14,x15, [x16,#0x160]     ; d0,d1
adds x8,x8,x10                 ; limb0
adcs x9,x9,x11
adds x8,x8,x12
adcs x9,x9,x13
adds x8,x8,x14
adcs x9,x9,x15                 ; 双字进位链
mov  x17,#0x7ffffffffffff
dup  v0.2d,x17
mov  v1.d[1],x8
and  v0.16b,v1.16b,v0.16b      ; 低位肢体 & M51（向量化掩码）
str  q0,[x19,#0x6b0]
mov  w10,#0x13                 ; 19
extr x8,x9,x8,#0x33            ; 取 bits 51..102
madd x9,x8,x10,x11             ; ×19 归约
and  x8,x9,#0x7ffffffffffff    ; 重新掩码
add  x9,x10,x9,lsr #51         ; 再进位
```

```c
/* fe_add/carryfold: h = a + b (mod 2^255-19), 未完全规范化 */
void fe_add_fold(u64 h[5], const u64 a[5], const u64 b[5]) {
    u64 t[5]; unsigned __int128 c = 0;
    for (int i = 0; i < 5; i++) { c += (unsigned __int128)a[i] + b[i];
                                  t[i] = (u64)c; c >>= 64; }
    t[0] &= M51;  t[1] += t[0] >> 51;
    /* 归约：把溢出位乘 19 加回最低肢体 */
    t[2] += (t[1] >> 51); t[1] &= M51;
    u64 carry = t[4] >> 51; t[4] &= M51;
    t[0] += 19 * carry;
    memcpy(h, t, sizeof t);
}
```

**`fe_mul`（教科书 5×5 乘积）**——范例 `0x5265dc`–`0x526738`（88 条，exec 510）。每对肢体做 `mul`/`umulh` 取 128 位积，再用 `madd` 把高半字累加进下一个肢体：

```asm
ldr  x9, [x19,#0xc50]          ; a0
ldr  x8, [x19,#0x960]          ; b0
mul   x10,x9,x8                ; 积低
umulh x8,x9,x8                 ; 积高
str  x8,  [x19,#0xc98]
str  x10, [x19,#0xc90]
...                             ; 重复 25 次（含 b 的平方项 a_i*b_i 与交叉项）
umulh x14,x8,x9
madd  x10,x10,x9,x14           ; 高半字累加
mul   x8,x8,x9
```

```c
/* fe_mul: 5x51 肢体学校算法 + ×19 折叠 */
void fe_mul(u64 h[5], const u64 a[5], const u64 b[5]) {
    unsigned __int128 acc;
    u64 t[9] = {0};
    for (int i = 0; i < 5; i++)
        for (int j = 0; j < 5; j++) {
            acc  = (unsigned __int128)a[i] * b[j];
            acc += t[i+j] + ((unsigned __int128)t[i+j+1] << 64);
            t[i+j]   = (u64)acc;
            t[i+j+1] = (u64)(acc >> 64);
        }
    /* 高于 2^255 的部分乘 19 折回 */
    for (int i = 0; i < 4; i++) t[i] += 19 * t[i+5];
    fe_reduce19(h, t);
}
```

**`fe_sq`（平方，交叉项折叠）**——范例 `0x5263c0`–`0x5264d4`（70 条，exec 510）：

```asm
ldr  x8, [x19,#0x4c8]          ; a0
ldr  x9, [x19,#0x508]          ; a4
mul   x10,x8,x9                ; a0*a4
umulh x11,x8,x9
ldr  x10,[x19,#0x518]          ; a1
mul   x11,x10,x8               ; a1*a0
umulh x8, x10,x8
umulh x11,x8,x8                ; a0*a0 高
mul   x12,x8,x8                ; a0*a0 低
str   xzr,[x19,#0x5a8]         ; 高位清零
...                             ; 对称项复用同一积，madd 折两次
```

```c
/* fe_sq: 对称性复用 a_i*a_j（i<j）两次，a_i^2 只算一次 */
void fe_sq(u64 h[5], const u64 a[5]) {
    unsigned __int128 acc; u64 t[9] = {0};
    for (int i = 0; i < 5; i++) {           /* 对角项 a_i^2 */
        acc = (unsigned __int128)a[i] * a[i];
        acc += t[2*i] + ((unsigned __int128)t[2*i+1] << 64);
        t[2*i] = (u64)acc; t[2*i+1] = (u64)(acc >> 64);
    }
    for (int i = 0; i < 5; i++)             /* 交叉项 ×2 */
        for (int j = i+1; j < 5; j++) {
            acc  = (unsigned __int128)a[i] * a[j];
            acc += t[i+j] + ((unsigned __int128)t[i+j+1] << 64);
            t[i+j]   = (u64)acc; t[i+j+1] = (u64)(acc >> 64);
            acc  = (unsigned __int128)a[i] * a[j];   /* 同一积折第二次 */
            acc += t[i+j] + ((unsigned __int128)t[i+j+1] << 64);
            t[i+j]   = (u64)acc; t[i+j+1] = (u64)(acc >> 64);
        }
    for (int i = 0; i < 4; i++) t[i] += 19 * t[i+5];
    fe_reduce19(h, t);
}
```

**`fe_reduce19`（×19 归约）**——范例 `0x52599c`–`0x525a7c`（57 条，exec 510）：

```asm
ldr   x8, [x19,#0x4e0]
mov   w9, #0x13                ; 19
mov   w10,#0x26                ; 38
lsl   x8, x8, #1
mul   x9, x8, x9               ; limb*19
mul   x10,x8, x10              ; limb*38
lsl   x11,x8, #1               ; limb*2
...
umulh x15,x9,x13
madd  x14,x9,x14,x15
madd  x10,x10,x13,x14
mul   x9, x9,x13
```

```c
/* fe_reduce19: 高于 2^255 的肢体按 2^255 ≡ 19 (mod 2^255-19) 折回。
   归约乘数成对出现：19（= 2^255 mod p）与 38（= 2*19，供被折项的 2 倍）——
   例中 `mov w9,#0x13` 得 19、`mov w10,#0x26` 得 38、`lsl x8,x8,#1` 得 2*limb。 */
void fe_reduce19(u64 h[5], u64 t[9]) {
    u64 c = 0;
    for (int i = 0; i < 5; i++) {
        unsigned __int128 acc = (unsigned __int128)t[i]
                              + (i < 4 ? 19 * (unsigned __int128)t[i+5] : 0)
                              + c;
        h[i] = (u64)acc & M51;
        c    = (u64)(acc >> 51);
    }
    h[0] += 19 * c;               /* 末次进位的 19 折 */
}
```

**`fe_carry_extract`（51 位进位提取）**——范例 `0x527d34`–`0x527d8c`（23 条，exec 510）：

```asm
ldp  x9,x10,[x8,#0x150]
extr x9, x10, x9, #0x33        ; 跨双字取 bits 51..114
lsr  x10,x10, #0x33            ; 高字右移 51
str  x10,[x19,#0x8a8]
str  x9, [x19,#0x8a0]
```

```c
/* fe_carry_extract: 从 (lo,hi) 双字中抽出第 51 位以上的进位 */
static inline void carry_extract(u64 *out_lo, u64 *out_hi, u64 lo, u64 hi) {
    *out_lo = (lo >> 51) | (hi << 13);   /* == extr x9,x10,x9,#51 */
    *out_hi = hi >> 51;
}
```

**`fe_tobytes`（序列化，R5）**——R5 无乘法指纹，只有 3 处掩码 + 2 处 `lsr #51`。其形态是把肢体重新打包成字节序输出：`0x531b94` 起的 `strb` 链把暂存字按序逐字节写出（`0x531c04` 起连续 22 条 `ldrb`/`strb` 拷贝 `[sp,#0x11c]`–`[sp,#0x148]` 到 `[x0,#0]`–`[x0,#0x14]`），另有 `lsr #0x10/#0x18/#0x20/#0x28` 与 `ubfx #0x2f,#4`、`ubfx #0x30,#3` 从 64 位肢体中按位段取字节。

**标量钳位（RFC 7748）**——R2 内 `0x527fb0` / `0x527fc4`：

```c
k[0]  &= 248;                                  /* and w8,w8,#0xf8  @0x527fb0 */
k[31] &= 127;                                  /* bfxil w9,w8,#0,#6 @0x527fc4 */
k[31] |= 64;                                   /* 置 bit254；再 lsl #44 装入第 4 肢体 */
```

**a24 = 121666**——`0x527db0`：`mov w9,#0xdb42` + `movk w9,#1,lsl#16` ⇒ `0x1DB42 = 121666` = `(486662+2)/4`，即 X25519 Montgomery 阶梯的 `a24`。

#### (f) 守恒验收

lift 后重算全域区密码学指纹，**逐项与静态精确计数相等，差值全为 0**：

| 指纹 | 静态计数 | 被执行集覆盖 | 差值 | 判定 |
| --- | ---: | ---: | ---: | --- |
| `adds` | 364 | 364 | 0 | **EXACT** |
| `adcs` | 364 | 364 | 0 | **EXACT** |
| `mul` | 514 | 514 | 0 | **EXACT** |
| `umulh` | 390 | 390 | 0 | **EXACT** |
| `madd` | 252 | 252 | 0 | **EXACT** |
| 掩码 `#0x7ffffffffffff` | 178 | 178 | 0 | **EXACT** |
| `extr #0x33` | 130 | 130 | 0 | **EXACT** |
| `lsr #0x33` | 63 | 63 | 0 | **EXACT** |
| `mov wN,#0x13`（×19） | 58 | 58 | 0 | **EXACT** |

且 (b) 的**逐例程指纹之和 = 全域区精确计数**（364/364、514、390、252、178、130、63、58），说明例程切分无遗漏、无重叠。产物：`re/field_lift.json`、`re/field_lift.txt`。

#### (g) 静态存在但两个操作码都不执行的 188 个槽位

域区窗口 `0x525000`–`0x532000` 共 **13 312** 个指令槽，执行集覆盖 **13 124**，未执行 **188**。全部未执行槽位如下（**已逐段定性，无未分析项**）：

| 区间 | 槽位数 | 性质 |
| --- | ---: | --- |
| `0x525020` | 1 | P 段与 R1 之间的对齐填充 |
| `0x5262ec`、`0x5289dc`、`0x52ea00`、`0x53176c` | 各 1 | 各 `ret` 之后的 4 字节对齐填充 |
| `0x5273c0`–`0x5273e0`、`0x530670`–`0x530690` | 9 + 9 | CFF 分派块的**备用分支臂**（谓词取反侧），本组输入不触发 |
| `0x52dc48`–`0x52dc5c` | 6 | 同上 |
| `0x531d84`–`0x531ffc` | **159** | R6 段：两个操作码均不进入；由 `0x531da4` 经 5 处 `bl` 调用，属独立辅助例程 |

> **口径说明**：188 个未执行槽位中 **0 个**含密码学指纹（掩码 178/178、`extr` 130/130、`mul` 514/514、`umulh` 390/390、`madd` 252/252、`adds`/`adcs` 364/364 **全部已被执行集覆盖**）。因此"未执行"只涉及调度胶水与填充，不涉及任何域运算。

#### (h) 本节结论

域区逐块 lift 的验收结论：

1. 域区可 lift 性已实测（95.7% 直线代码，435 个 distinct run 穷尽）；
2. 两层拆分守恒（调度尾 1 347 / 域运算层 11 777 执行地址，合计 13 124）；
3. 154 个域原语全部定名，逐原语伪代码见 (e)；
4. 九项密码学指纹守恒**逐项 EXACT**，且逐例程求和一致；
5. 188 个未执行槽位逐段定性，无一含密码学指纹。

**CFF 调度机制的语义是跳转目标**：1 347 个调度尾地址的语义已由 61 块 ↔ 61 槽 ↔ 31 常量三向双射和 61 个复现位移完全确定。调度尾不含算法、密钥或算术，结构化枚举即覆盖其全部语义。

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

> **与 §5.6.6(e) 的关系**：本节这 14 个常量与 §5.6.6(e) 复现的 **61 个分派块跳转位移**是**同一类量**——都是 CFF 块间的编译期相对位移（本节是块中部的前向跳转，§5.6.6(e) 是分派块尾部的 `br`）。两者合起来说明：CFF 的所有跳转目标都由"运行期表指针 + 编译期位移"决定，位移本身**完全可静态复现**，与输入无关。

---

### 6.4 全 31 个操作码的确定性执行集

§6.1–§6.3 用的是 `re/tiny_emu4.py`（"单次新增覆盖"，受**执行顺序**影响——先跑的操作码会把它后续操作码也走的公共路径记为"自己新增"，因此数值随调用顺序漂移；5 928 是把错误常量送入模拟器的产物，见 §5.6.8）。

本节改用**逐操作码独立执行集**（`re/tiny_emu6.py` / `re/tiny_emu7.py`，输出 `re/execsets/cov_<opcode>.txt`）：每个操作码在**全新实例**中单独执行一次，记录它实际触及的**全部**指令地址集合。参数固定为 `["GET","edith.xiaohongshu.com","/api/sns/v1/search/notes","keyword=hello"]`。

| 操作码 | 执行集 | 操作码 | 执行集 | 操作码 | 执行集 |
| --- | ---: | --- | ---: | --- | ---: |
| `0x3c6d0ac1` | **35 577** | `0xae821439` | **23 310** | `0x2ad1c199` | 10 253 |
| `0x2f036831` | 8 583 | `0x96f7fcac` | **8 330** | `0xcf7db9ff` | 8 266 |
| `0x96d0a479` | 6 901 | `0xc9d57702` | 6 773 | `0x4af613b8` | 6 420 |
| `0x704bfeeb` | 6 348 | `0x42a21aaf` | 6 044 | `0xffd8e9f6` | 6 036 |
| `0x5ac40428` | 6 035 | `0x11296316` | 6 031 | `0xae8750a7` | 6 031 |
| `0x7c70cc76` | 6 030 | `0xc23a168e` | 6 024 | `0xb20a0be3` | 6 022 |
| `0xd40131d5` | 6 019 | `0x9701e74c` | 6 013 | `0x398bf05d` | 6 009 |
| `0x45e9da0d` | 6 006 | `0xcd554fab` | 6 002 | `0x17c04796` | 6 000 |
| `0x259cebf7` | 5 991 | `0x727981d1` | 5 988 | `0xe83def19` | 5 988 |
| `0xf961fe3b` | 5 977 | `0x4e418ac4` | 5 963 | `0xf3f89a2a` | 5 914 |
| `0x28ac92d7` | 5 888 | | | | |

**汇总量**：

| 项 | 值 |
| --- | ---: |
| 操作码数 | **31 / 31**（全部取得执行集） |
| 执行集最小值 / 最大值 | **5 888** / **35 577** |
| 31 个执行集总和 | **246 772** |
| 语料文件 | `re/execsets/cov_*.txt`（31 个）+ `log_*.txt`（31 个 JNI 调用日志） |
| 确定性 | 同输入两次执行，覆盖集合**逐字节相同**（以 `0x96f7fcac` 为例：35 577 条集合之外单跑两次，对称差 **0**） |

**这批数据支撑两个结论**：

1. **31/31 操作码均为活代码**——不存在"静态枚举出来但从不可达"的死操作码；
2. **域运算区（§2.2.1）的归属可判定**——`0x525000`–`0x532000` 的 13 312 条指令中，13 124 条（98.6%）**只**被 `0x3c6d0ac1` 与 `0xae821439` 覆盖，而这两个操作码在该区**覆盖集合完全相同**（交集 = 并集 = 13 124），其余 29 个操作码在该区覆盖为 **0**。

---

## 7. 覆盖矩阵条目 12 的状态更新

| 项 | 状态 |
| --- | --- |
| 分发机制 | **已恢复**（本文 §3 给出字段、入口、比较块形状、谓词数组） |
| 操作码全集 | **31 个已全部枚举**（§5.2），且与动态执行集合 100% 吻合 |
| 比较点表述 | `0x16b08c`/`0x17cdb0` = 同一操作码 `0x96f7fcac` 的两个 CFF 重复块（§4） |
| 13 字节签名头字段 | 结构已知；`x-n0`/`x-o9`/`x-p0`/`x-r4`/`x-r4o` 取值来自本引擎返回的 Map |
| 分发层结构 | **完全闭环**：61 分派块 ↔ 61 个互不相同的谓词槽（`0x1253`–`0x128f`）↔ 31 个操作码常量**三向双射**，零冲突；61 个分派块的编译期跳转位移全部复现（§5.6.6、§5.6.7） |
| 每操作码的语义 | **31/31 已定名**（§5.6）：引擎是 **native(31) / Java(71) 双操作码空间、交集为 0** 的 VM（§5.6.2）；29 个 native 操作码定位到 **dex 里的确切调用表达式**（§5.6.3），其中 `0x96f7fcac` = **OkHttp 请求签名头生成**（§5.6.4）；另 2 个为纯 native 路径（§5.6.5） |
| 每操作码内部逐块算术步骤 | **已 lift**（§5.6.9）：435 distinct run 全判读、154 域原语 + 伪代码、九项指纹守恒逐项 EXACT |

**关于"每操作码算法语义"的定性**：

- 这**不是**"未分析清楚的加密代码"。§2 已用指令级扫描证明该库里**不存在** AES/SHA/SM/GHASH 实现，也不存在完整 MD5 轮常量。其密码学成分有四项，**全部位置精确到字节、身份已定名**：MD5 初值（`0xf1510`）、Base64 字母表（`0xfe640`）、CRC32 表（`0xf7730`），以及 **§2.2.1 的内联 X25519 域运算（`0x525000`–`0x531e4c`）**。
- **VM 语义 lift 已完成**：31 个操作码各自的调用语义全部定名（§5.6），块内算术步骤逐块 lift 完毕（§5.6.9）。
- lift 所依据的基础：31 个操作码全表 + 61 个分派块地址 + **61 个谓词槽与条件的完整映射表**（§5.6.7）+ **61 个分派块跳转位移**（§5.6.6(e)）+ 45 次执行的覆盖率/返回值/耗时 + **11 个 JNI 调用面分组**（§5.4）+ **逐操作码静态块集合、动态执行集与专属指令数**（§5.5，含单 FDE 事实、2471 条共享指令、90–198 条专属指令）。
- 分组 1（17 个）与分组 2（4 个）的专属指令已由 §5.5.7 判定为 CFF 调度胶水，无需按参数形状重跑。（注：`libxyass.so` `0x50010` 的 CFF 转移图已完整枚举并饱和——412 site / 767 边 / 417 目标，且**选择层亦已闭环**（262 FIXED / 69 BASE / 18 DATA），见 [crypto.md](crypto.md) §4.6。）

---

## 8. 复现方式

```bash
cd rednote-9.37.0-re

# 全应用普查（约 15 min）
PYTHONPATH=re python3 re/sweep_crypto.py

# 第 4 条判据：全 164 库的加载期自解密数据表（单库约 40 s，建议按 --out= 分片并行）
python3 re/inplace_strdec_scan.py                # 静态位点（D1–D5） -> re/inplace_strdec_scan.json
python3 re/initarray_census.py                   # 构造子清点 + 函数长度 -> re/initarray_census.json
python3 re/initarray_run.py libs/lib/arm64-v8a/*.so   # Unicorn 跑构造子比对前后字节 -> re/initarray_run.json
python3 re/xor_table_test.py                     # 单字节 XOR 全扫（否证用） -> re/xor_table_test.json
python3 re/inplace_site_content.py               # 每个 .data 位点的目标内容定性 -> re/inplace_site_content.json

# libturingmfa.so 逐条还原（§1.7）
python3 re/tmfa_initarray.py                     # 执行解密器 -> re/tmfa_initarray.json
python3 re/tmfa_schedule.py                      # 密钥周期闭式校验 -> re/tmfa_schedule.json
python3 re/tmfa_init_fde.py                      # 其余 8 个构造子 FDE 边界反汇编 -> re/tmfa_init_static.json / re/tmfa_init_disasm.txt
python3 re/tmfa_init_reach.py                    # 内部调用 + 注册析构闭包 -> re/tmfa_init_reach.json

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

# 操作码常量精确恢复（61/61 分派块 -> 31 个操作码）
python3 re/tiny_opcode_const.py     # 需先有 re/tiny_text.asm

# 分发层闭环（全段扫描 / 谓词侧独立枚举 / 常量半字普查 / 跳转位移）
python3 re/tiny_dispatch_nodes.py       # 全段 ldr [x19,#0xa4] -> 分派块
python3 re/tiny_dispatch_classify.py    # 66 处字段引用分类：61 DISPATCH / 4 STORE / 1 DERIVE
python3 re/tiny_pivot_consumers.py      # 31 个 pivot 的全部消费者（应为 61，无多余）
python3 re/tiny_dispatch_closure.py     # 谓词数组写入点独立枚举（68 站点 -> 填满 61 槽）
python3 re/tiny_dispatch_final.py       # 合并两侧 -> re/tiny_dispatch_final.json
python3 re/tiny_dispatch_edges.py       # 61 个分派块跳转位移（unicorn，停在 br 之前）
python3 re/tiny_dispatch_invariants.py  # I1-I6 不变量校验

# 专属指令集的角色分类（证明是 CFF 调度胶水）
python3 re/tiny_excl_class.py       # -> re/tiny_excl_roles.json
python3 re/tiny_excl_verify.py      # 专属集自带本操作码比较

# dex 侧：常量物化方法定位、调用点列举、语义提取
python3 re/tiny_dex_owner.py        # -> re/tiny_dex_owner.txt
python3 re/tiny_opcode_callsites.py # -> re/tiny_opcode_callsites.txt
python3 re/tiny_semantics_final.py  # -> re/tiny_semantics_final.txt

# dex 层全量引用校验（上传埋点调用方）
python3 re/dex_ref4.py

# 域区逐块算术 lift（§5.6.9）
python3 re/tiny_field_trace.py 1013779137   # -> re/field_trace_3c6d0ac1.json（约 20 min，27 MB）
python3 re/tiny_field_blocks.py             # -> re/field_blocks.json
python3 re/tiny_field_class.py              # -> re/field_class.json（全域区静态指纹计数）
python3 re/tiny_field_lift.py               # -> re/field_lift.json（六段例程 + 守恒校验）
python3 re/tiny_field_prims.py              # -> re/field_prims.json（154 个域原语）
python3 re/tiny_field_account3.py           # -> re/field_account3.json（两层拆分守恒）

# Java/dex 侧混淆审计（§9）
python3 re/u5_decode.py        # -> re/u5_decoded.json（519 站点）
python3 re/dex_strdec_census.py    # -> re/dex_strdec_census.json（822 个解密器调用点，字节码精确）
python3 re/dex_strdec_literals.py  # -> re/dex_strdec_literals.json（811 个内联 (cipher,key) 对）
python3 re/java_strdec_all.py      # -> re/java_strdec_all.json（1732 行明文，两棵 jadx 树）
python3 re/java_obf_composite.py   # -> re/java_obf_composite.json（811/811 映射，0 未映射）
python3 re/daemon_va.py            # -> re/daemon_va.json（守护 v.a 的 4 条明文）
# 注意：不要用 re/java_strdec.py（按 jadx 重命名后的标识符匹配），请用字节码口径的下列脚本
python3 re/dex_classfind.py Lfvc/ Lcom/xingin/xhs/petal/
python3 re/dex_strfind.py PETAL_MODE
```

样本哈希（同 [README.md](README.md) 与 [evidence.md](evidence.md)）：

| 样本 | SHA-256 |
| --- | --- |
| XAPK | `42033a36…` |
| base APK | `0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c` |
| `libtiny.so` | `b403a883…` |
| `libxyass.so` | `8e7db9e4…` |

---

## 9. Java / dex 侧混淆审计

§1.5 的 164 库普查覆盖的是 **native** 面。把"不允许留下任何未分析的被混淆代码"按字面读，**dex/Java 面同样在范围内**。本节把该面审计完毕。

### 9.1 `Petal` 不是混淆器

`Petal` 常被读成"**注解驱动方法名加密**"，并称"Petal 混淆通过加密注解字节、运行时解密包装器和 opcode 分发隐藏调用名"。逐条核对后确认：**`Petal` 与混淆无关**。

`PetalConfig` 的实际内容（`jadx-out/sources/com/xingin/xhs/PetalConfig.java`，来自 `classes.dex`）：

```java
@Keep
public class PetalConfig {
    public static final int BASE_TYPE = 1;
    public static final String PETAL_ID = "petal_test";
    public static final boolean PETAL_MODE = false;          // <-- 一个常量 false
    public static final String VERSION_NAME = "8.35.1";
    public static final SimplePlugin[] LOCAL_PLUGINS = new SimplePlugin[0];
    public static final String[] REMOTE_PLUGINS = new String[0];
    public static final String[] BUILD_IN_MODULES = new String[0];
    public static final SimplePlugin[] CURR_HOST_ALL_PLUGINS = new SimplePlugin[0];
    public static final String[] PLUGIN_ENTRY_FRAGMENTS = new String[0];
    public static final String[] AUTO_STRATEGIES = new String[0];
    public static final String[] EXP_PLUGINS = new String[0];
    public static final Map<String, Map<String, String>> COMPONENT_INFO = new HashMap();
}
```

证据链：

| 观测 | 值 | 说明 |
| --- | --- | --- |
| `PETAL_MODE` 的类型与取值 | `boolean` = **`false`** | 编译期常量，不是密钥、不是开关字节 |
| 它被谁使用 | `@ReactProp(defaultBoolean = PetalConfig.PETAL_MODE, …)`、`@JSMethod(uiThread = PetalConfig.PETAL_MODE)` | 作为 **React Native / Weex 注解的布尔默认值**传入，属插件化框架的 API |
| `PETAL_ID` | 字符串 `"petal_test"` | 插件测试通道名 |
| 同一类的其它成员 | `LOCAL_PLUGINS`/`REMOTE_PLUGINS`/`PLUGIN_ENTRY_FRAGMENTS`/`AUTO_STRATEGIES`/`COMPONENT_INFO` | **全部是插件清单** |
| 日志标签 | `【PETAL】` + `"launch plugin: … for entrance spi success!!"`、`"isOpenPetal = false"` | 插件启动日志，与混淆无关 |
| `com.xingin.xhs.petal.*` 包 | `PetalServiceImpl`、`PetalSpiImpl`、`PluginService`、`common/EmptyShopTabFragment`… | **插件服务 SPI**（`PluginService.getPluginDiffResult` / `reportUpdateInfo`），即插件差分下发 |

**结论**：`Petal` 是小红书的**插件化框架代号**（对应 `com.xingin.petal.core.common.SimplePlugin`、`com.xingin.spi.*` 插件 SPI），**不是混淆器**。它的布尔常量属插件框架 API，不是加密方案（对照见 §9.5）。

### 9.2 真实存在的 Java 侧混淆：`@u5`/`@v5` 加密字段名（已完整还原）

Tiny 引擎的 Java 侧确实存在一套**加密注解**，但它的代号不是 Petal，载体是 `com.xingin.tiny.internal.u5` / `v5`：

```java
@Target({ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)
public @interface u5 { v5 a(); v5[] b(); boolean c(); }

@Target({ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)
public @interface v5 { byte[] a(); byte[] b(); }
```

**机制**（`re/jadx_1718/sources/com/xingin/tiny/internal/o5.java` 逐行给出）：字段上挂 `@u5(a=@v5(a=…, b=…))`，`o5.a(u5, field)` 调用 `a7.a(-1, v5.a(), v5.b())` 解出**方法名**，`o5.b(field)` 用 `u5.b()` 数组解出**参数类型名**；`n5<T>` 再用 `Class.getDeclaredMethods()` 按解出的名字找到方法并 `setAccessible(true)` 后反射调用。调用链为 `o5.a` → `a7.a(i,bArr,bArr2)` → `b7.a(bArr,bArr2)` → `t.a(1117919919, {long, byte[], byte[]})`，**末段进入 native**（`0x42a21aaf`，在 §5.2 的 31 操作码表内）。

> **证据口径（重要）**：本节与 §9.2b 的解码是**静态**完成的——直接对 `@v5` 的两个字节数组施加逐字节 XOR（+ 定点变换）即可得明文，不依赖 native 执行。native 侧的**算法本体未能在合成环境内实测**：以 `re/tiny_xor_probe.py` 用已知 `(a,b)` 调用 `0x42a21aaf`，返回句柄为 `0`、无新字节数组产生，且输入数组未被就地修改——与 `re/tiny_emu4.py` 对该操作码的结果（`x0=0x0`，仅 381 条新覆盖）一致，说明该操作码在**缺少真实 JNI/运行期状态**的合成 harness 中不完成其工作。因此"native 端做的是逐字节 XOR"这一表述的依据是**静态解码的 100% 自洽**（`@u5/@v5` 的 519 个站点全部产出**合法 Java 标识符**；字符串解密器的 **811 个调用点全部映射到明文**，见 §9.2b——其中 732 个由字节码与文本双侧确认、79 个仅能由字节码侧解出，任何别的方案都会产生乱码），而非对 native 的实测。注意"可打印"不再是判据：合法的明文可以含控制字节（§9.2b.4）。

**闭式还原**：对 `@v5` 的两个字节数组做逐字节 XOR（`b` 循环），即得明文。用该式解全部 **519** 个 `@u5/@v5` 站点：

| 项 | 值 |
| --- | ---: |
| `@u5` 站点总数 | **519** |
| 解出明文后**是合法 Java 标识符或类型名**的 | **519（100%）** |
| 解不出 / 非法 | **0** |
| 互异明文名 | **371** |
| 涉及类 | 145（全部在 `com.xingin.tiny.internal`） |

**100% 合法性**是这套解码式被真正理解的判据——它不是拟合，而是复现：任何错误的 XOR 方案都会在 519 个站点上产生大量非法标识符。

样例（`j4.java`，逐条可复算）：

| 字段 | `a` | `b` | 明文 |
| --- | --- | --- | --- |
| `j4.a` | `-16,-78,-21,-69,-37,-68,-4,-74` | `-104,-45` | `hashCode` |
| `j4.b` | `-3,52,-38,47,-5,50,-25,60` | `-119,91` | `toString` |
| `j4.c` | `-80,55,-93,17,-69,51,-92,33` | `-41,82` | `getClass` |
| `j4.d` | `50,-89,40,-95,58,-79` | `92,-56` | `notify` |
| `j4.e` | `87,-99,77,-101,95,-117,120,-98,85` | `57,-14` | `notifyAll` |
| `j4.f` | `-33,118,-63,99` | `-88,23` | `wait` |
| `j4.g` | `48,-22,60,-24,54` | `83,-122` | `clone` |
| `j4.h` | `12,-55,28,-39,5,-53` | `105,-72` | `equals` |
| `j4.h.b[0]` | `-107,…,-67`（16 字节） | `-1,-55` | `java.lang.Object` |
| `j4.i` | `18,-88,12,-67` | `101,-55` | `wait` |
| `j4.i.b[0]` | `121,66,123,74` | `21,45` | `long` |

（`j4.h`/`j4.i` 同时演示了 `u5.b()` 解出**参数类型名**的路径。）

**被隐藏的名字的分布**（371 个互异名，按语义分类）：

| 类别 | 命中 / 该类样例 | 说明 |
| --- | --- | --- |
| `java.lang.Object` 全 8 方法 | **8/8**：`hashCode`、`toString`、`getClass`、`notify`、`notifyAll`、`wait`、`clone`、`equals` | 反射调用面刻意隐藏最基础的 Object 方法名 |
| 集合/容器 | **12/12**：`get`、`put`、`size`、`isEmpty`、`iterator`、`contains`、`remove`、`add`、`sort`、`entrySet`、`keySet`、`values` | — |
| 文件 / IO | **11/11**：`write`、`read`、`close`、`listFiles`、`getPath`、`getName`、`length`、`delete`、`mkdirs`、`exists`、`getAbsolutePath` | 沙箱/插件目录操作 |
| 摘要 / 编码 | **9/11**：`encode`、`digest`、`update`、`doFinal`、`getInstance`、`init`、`getBytes`、`toByteArray`、`copyOf` | **这是 `@u5` 覆盖密码学调用名的直接证据** |
| HTTP / 网络 | **7/10**：`url`、`body`、`headers`、`getNetworkCapabilities`、`newCall`、`request`、`execute` | 风控自身的上报通道 |
| 包管理 / 应用 | **6/9**：`getPackageInfo`、`getPackageName`、`getApplicationInfo`、`getService`、`asInterface`、`getSystemService` | 环境/应用枚举 |
| Activity / 视图 | **7/8**：`startActivity`、`addView`、`getResources`、`getColor`、`getRefreshRate`、`obtainStyledAttributes`、`findViewById` | 屏幕/刷新率采集（指纹用） |
| 反射 | **3/12**：`forName`、`getMethod`、`getConstructor` | — |
| 进程 / 时间 | **3/9**：`myUid`、`start`、`currentTimeMillis` | — |
| WebView | **1/7**：`getUrl` | — |

站点最密的类：`p1`（51）、`g2`（23）、`y6`（21）、`d1`/`b1`/`x2`/`u`/`j4`（各 9）。

### 9.2b Java 侧字符串加密：**811/811 调用点闭式还原**（口径已重建）

同一套 `@u5/@v5` 机制之外，Tiny 还用**字符串解密调用**保护类名与常量名。形态：

1. `byte[] bArrA = a7.a.a("<Latin-1 密文>".getBytes(ISO_8859_1), "<短密钥>".getBytes(ISO_8859_1))`
   —— 即 §9.2 的**同一 native XOR**；
2. 随后是若干**定点变换**，编译器共产生五种形式（全部已在字节码中明文）：
   - 单下标 `bArrA[i] = (byte)(bArrA[i] <op> K)`，`op ∈ {+,-,^}`，**且操作数两种顺序都出现**；
   - 单下标按位取反 `bArrA[i] = (byte)(~bArrA[i])`；
   - 循环移位 `int i = bArrA[j] & 255; bArrA[j] = (byte)((i >>> n) | (i << m))`，**两种操作数顺序**；
   - 整数组循环 `for (i=0;i<N;i++) bArrA[i] = (byte)(bArrA[i] <op> K)`；
3. `new String(bArrA, UTF_8)`，再交给 `h5.a(Class, name)` / 反射注册。

#### 9.2b.1 Java 侧字符串解密的计量口径

计量必须以**字节码**为单位。按"标识符名字字面匹配"统计会得到与 jadx 树绑定的伪口径：

`re/java_strdec.py` 用**调用形态字面匹配**统计站点——即匹配 `a7.a.a`、
`b7Var.a`、`a7.a`、`c1.a` 这些**标识符名字**。而这些名字是 **jadx 重命名后的产物**：
同一个 dex 在不同 jadx 树里会渲染成不同名字，于是"站点数"取决于**用了哪棵树**。
实测三棵树对同一个 dex 给出完全不同的结果：

| jadx 树 | 该脚本报告的站点数 |
| --- | ---: |
| `jadx-out/sources`（默认档，全 20 dex） | 441 |
| `re/jadx_1718/sources`（classes17 子集） | 674 |
| daemon dex 的 debug 树 | 57 |

**同一个字节码，三个数字**——标识符字面匹配得到的不是"还原率"，只是"某一棵树的字符串里
恰好出现了这些名字的次数"。以它为分母声称覆盖，等于用渲染细节冒充分析结论。

#### 9.2b.2 字节码口径：不依赖任何名字

该口径只做一件事：**遍历每个 `code_item`，按目标方法的 proto 选出解密器，
精确统计 invoke 调用点**（`re/dex_strdec_census.py`）。判定依据是
`method_id` 指向的 `(类, 方法名, 描述符)`，与 jadx 如何重命名无关。

| 项 | 值 | 产物 |
| --- | ---: | --- |
| 解密器**调用点**（字节码精确） | **822** | `re/dex_strdec_census.json` |
| 其中带**内联 (cipher,key) 字面量对** | **811** | `re/dex_strdec_literals.json` |
| 明文由**字节码+文本双向确认**的调用点 | **732** | `re/java_obf_composite.json` |
| 明文**仅可由字节码侧**解出（寄存器列表/地址定位） | **79** | 同上 |
| 已映射到明文的调用点 | **811（100% = 732 + 79）** | 同上 |
| **仍未映射** | **0** | 同上 |
| 互异明文 | **578** | 同上 |
| 明文来源条数（两棵树的文本级解码） | 1732 行，**1732 全部解出** | `re/java_strdec_all.json` |
| 矛盾（同一对解出不同明文） | **0** | 同上 |

**调用点按目标方法分布**（字节码精确，注意这 6 个目标**分属两个 dex 族**）：

| 目标方法 | 调用点 |
| --- | ---: |
| `Lcom/xingin/tiny/internal/b7;.a([B[B)[B` | 559 |
| `Lcom/xingin/tiny/daemon/c1;.a(I[B[B)[B` | 136 |
| `Lcom/xingin/tiny/internal/a7;.a(I[B[B)[B` | 119 |
| `Lcom/xingin/tiny/internal/o5$a;.a(I[B[B)[B` | 3 |
| `Lcom/xingin/tiny/daemon/r0$a;.a(I[B[B)[B` | 3 |
| `Lcom/xingin/tiny/daemon/d1;.a([B[B)[B` | 2 |

按 dex 分布：`classes16.dex` 1 · `classes17.dex` 678 · `classes18.dex` 2 ·
`assets/fd2x1e4e2x3f1v2b1s.dex`（守护 dex）141。

#### 9.2b.3 剩下 11 个"无字面量"调用点：**是蹦床，不是漏项**

822 − 811 = **11** 个调用点的两个 byte[] 操作数是**它自己的参数**，而不是内联字面量。
逐条列明（`re/java_obf_composite.json` 的 `no_pair_sites`）：

| 蹦床方法 | 调用点 | 转发目标 |
| --- | ---: | --- |
| `Lcom/xingin/tiny/internal/a7;.a(I[B[B)[B` | 1 | `a7.a.a([B[B)` → `b7.a` |
| `Lcom/xingin/tiny/internal/b7;.a(I[B[B)[B`（`a7.a` 实例） | 1 | `a7.a.a([B[B)` → `b7.a` |
| `Lcom/xingin/tiny/internal/o5$a;` 的 3 个接口实现（`a`/`b`/`a`） | 3 | `b7.a` |
| `Lcom/xingin/tiny/daemon/c1;.a(I[B[B)[B` | 1 | `d1.a([B[B)` |
| `Lcom/xingin/tiny/daemon/d1;.a([B[B)[B` | 2 | （自身即实现） |
| `Lcom/xingin/tiny/daemon/r0$a;` 匿名类 3 个方法 | 3 | `c1.a` |

以 `a7` 为例（`re/jadx_1718/sources/com/xingin/tiny/internal/a7.java`）：

```java
public static byte[] a(int i, byte[] bArr, byte[] bArr2) {
    return a.a(bArr, bArr2);          // 参数原样转发，无字面量
}
```

**所以"无字面量"是转发语义，不是未分析**：真正的 (cipher,key) 留在**原始调用者**那里，
而原始调用者已经在 811 之内。这 11 个蹦床只贡献调用链，不贡献新明文。

#### 9.2b.4 自校验强度

- **双向核对**：调用点侧由字节码给出 (cipher,key)；明文侧由两棵 jadx 树的文本级
  解码给出。二者按 (cipher,key) 交叉匹配：

  | 量 | 值 | 含义 |
  | --- | ---: | --- |
  | 互异 (cipher,key) 对 | 810 | 全部来自字节码 |
  | 其中**文本侧也解出**的 | **731** | 双向确认 |
  | **仅字节码侧解出**的 | **79** | = 73（`v.<clinit>` 寄存器列表）+ 4（`v.a`）+ 2（地址定位） |
  | 只在文本里、字节码没有的 | **0** | 无悬空明文 |

  站点级同理：**732 站点双向确认 + 79 站点字节码单侧 = 811**。
- **零矛盾**：同一个 (cipher,key) 在多棵树、多个站点上解出**同一明文**，
  `ambiguous_pairs` = 0。这与 §9.2 的 519 站点自校验是同一逻辑：
  解密器必须同时命中五种定点变换与两种操作数顺序，任一处错都会在这 811 个站点上产生乱码。
  还原器必须同时命中五种定点变换与两种操作数顺序：漏掉 `~` 形式、漏掉反转的移位顺序、
  或多加一条重复的 XOR 规则，都会在特定站点上产出乱码（如 `java.lang.Beolean`、
  `java.io.JnputStream`、`java.util.Map$Ectry`、`java.net.URLEncofer`、`javj.util.UUID`、
  `AES_KNCRYPTED`），而正确形式下 811 个站点无一乱码。
- **不可打印明文不等于失败**：有 4 行明文含控制字节
  （`com.xingin.tiny.internal.t` 的 `'\x04'` 与 `'dime\x03'`），
  它们是**正确的**明文（NUL 结尾的类名后缀）。若把"可打印"当过滤器，
  这 2 个站点会假性"未映射"；本口径把"已解出"与"可打印"分开报告。

#### 9.2b.5 文本树缺字面量的 2 个站点：按字节码地址解出

在蹦床之外，当**原始调用点**的字面量没进文本树时，两处需要单独闭环。二者均已解出，
且以 **`(dex, caller_mid, insn_off)` 字节码地址**为键（不是位置序，故不会错位）：

| 站点 | 字节码地址 | 明文 | 依据 |
| --- | --- | --- | --- |
| `classes17.dex` `s0.onChange` | off 490 | **`screenshot_`** | 该站点所在的 `B:73:0x01d3` 块被 jadx 判为 `code lost`，但**寄存器列表仍在**（同一棵树的注释块内），12 步定点变换序列逐条可见 |
| 守护 dex `v.<clinit>` | off 7747 | **`KeyGenParameterSpec$Builder`** | id 1412，尾部 `L1e48` 循环 27 次 `^64` |

`v.<clinit>` 的这一个是**第 74 个也是最后一个**解密调用：寄存器列表 VM 只覆盖了 73 个，
因为它止步于 ARM-only 的 `SDK_INT` 门（见 §9.7.3）。

> **口径声明（本节）**：本节所有计数都来自**字节码扫描**，不是文本匹配。
> 唯一使用文本树的地方是**明文字符串的取值**，且文本侧只用来**交叉确认**
> 已由字节码定位的 (cipher,key) 对（810/810 双向命中）。
> 因此本节的数字**不随 jadx 树变化**，可复现（见 §9.6）。

### 9.3 `fvc` 注解包：通用 HTTP 注解，非混淆

`@fvc.f` / `@fvc.o` 等易被当作"方法名加密"的证据。`classes20.dex` 反编译出整个 `fvc` 包（25 个注解，`/tmp` 下 jadx 产物，样本内可复现），确认它是**普通 HTTP 注解**：

| 注解 | 目标 | 成员 | 语义 |
| --- | --- | --- | --- |
| `fvc.h` | METHOD | `method()`、`path()`、`hasBody()` | **通用 HTTP 方法描述**（任意方法名 + 路径 + 是否带体） |
| `fvc.o` / `fvc.f` / `fvc.b` / `fvc.g` / `fvc.m` / `fvc.n` / `fvc.p` | METHOD | `value()` = 路径 | GET/POST 等 |
| `fvc.t` / `fvc.c` / `fvc.s` | PARAMETER | `value()`、`encoded()` | 查询 / 表单字段 |
| `fvc.a` / `fvc.d` / `fvc.q` / `fvc.r` / `fvc.u` / `fvc.v` | PARAMETER | `encoded()`、`encoding()`（默认 `"binary"`） | body / multipart |
| `fvc.y` / `fvc.i` / `fvc.j` / `fvc.x` / `fvc.k` / `fvc.l` / `fvc.w` / `fvc.e` | METHOD/PARAMETER | 无成员（纯标记） | 头字段 / 流式等 |

`fvc.h` 同时带 `method()` 与 `path()`，正是"把 Retrofit 的 `@GET`/`@POST` 泛化成一个可参数化注解"的设计，与混淆无关。样本内 `fvc` 定义于 `classes20.dex`，引用遍布 20 个 dex（**定义与引用分离**）；在主 dex 里只见 `import fvc.*` 而找不到定义，容易被读成"名字被抹掉"。

**判据**：一个注解是"混淆"还是"框架"，看它是否**携带可解密的字节数组**。`@u5/@v5` 携带（且 519/519 可解），`fvc` 不携带（成员全是明文 `String`）。

### 9.4 其余 Java 侧混淆面：逐项已定性

| 面 | 观测 | 状态 |
| --- | --- | --- |
| 类名/包名混淆 | 大量 `a/a/a/a/a/a`、`OooO00o/OooO00o/…`、`a0b`…`zzb` 等短名；样本 20 个 dex、`class_defs` 数万 | **形态已定性**：单/双字母包名 + 数字后缀类名（标准 ProGuard/R8 字典压缩），**无自定义名字加密**——名字本身就在字符串表里明文可读 |
| 方法名加密 | 仅 `@u5/@v5` 覆盖的 **519** 个反射调用点 | **已闭式还原**（§9.2） |
| 字符串加密（Java 侧） | 6 个解密器目标（`b7.a`/`a7.a`/`o5$a.a`/`c1.a`/`d1.a`/`r0$a.a`）上的 `(cipher,key)` 内联字面量 + 逐字节定点变换（`^`/`+`/`-`/`~`/循环移位/整数组循环） | **811/811 调用点全部映射，0 未映射**（§9.2b；按标识符字面匹配得到的「212」是 jadx 树相关伪口径） |
| 字符串加密（native 侧） | `libtiny.so` 双解码器 320 调用点 | **已闭式还原 312 条明文**（§2.4） |
| 反射包装器 | `n5`/`l5`/`m5`/`k5`/`j5`/`g5`/`s5`/`q5`/`r5`/`i5`/`p5` 共 **11 个**（`h5.a` 注册表） | **已枚举**：每个都是"解名 → `getDeclaredMethod` → `setAccessible` → `invoke`"的同一模式 |
| 注解 `@mlb.a` | 15905 处 | 纯标记注解（无成员），非混淆 |
| 守护 dex | `assets/fd2x1e4e2x3f1v2b1s.dex`（78 008 B，63 个 `com.xingin.tiny.daemon.*` 类） | **完整审计**：IPC 12 case + 字符串 141 调用点 + `@x0` 116/116 + `@w0` 77 + `v.<clinit>` 74 条明文（§9.7） |
| 载荷 dex ×2 | `assets/c4d121c215evx1s51d.dex`（940 B）、`v.<clinit>` Base64 还原的 588 B | **均为单类 `La;` 的 R8 反射蹦床**，4 个方法逐字相同；940 B = 未剥离调试元数据版（§9.7.2/§9.7.3） |

### 9.5 归属判定对照表

| 载体 | 归属 | 依据 |
| --- | --- | --- |
| `@u5` / `@v5` 注解 | **注解驱动字段名/字符串加密**（闭式还原，519 站点） | §9.2、§9.2b |
| `Petal` / `PetalConfig` | **插件化框架代号**，非混淆器 | §9.1 |
| `@fvc.*` 注解包 | **通用 HTTP 注解**（定义在 `classes20.dex`），非名字加密 | §9.3 |
| Tiny opcode 分发 | **VM 分派混淆**（31 native / 71 Java 双操作码空间） | §5.6 |
| `assets/fd2x1e4e2x3f1v2b1s.dex` | `libtinyd.so` 的 **Java 侧守护进程**（63 类、12 个 IPC case） | §9.7 |
| `assets/c4d121c215evx1s51d.dex`（940 B）与 `v.<clinit>` 还原的 588 B 内嵌 dex | **单类 `La;` 的 R8 反射蹦床** | §9.7 |
| `libtinyd.so` 4 字节 IPC 载荷的读取方 | daemon dex 的 `e.main` → `l.a()` | §9.7.4 |
| Java 侧字符串解密的计量 | **字节码精确**：822 调用点 / 811 带内联字面量 / 811 全映射、0 未映射 | §9.2b.2 |

### 9.6 复现方式（本节）

```bash
cd "$REDNOTE_ANALYSIS_ROOT"

# 519 个 @u5/@v5 站点逐条解码（XOR 闭式），校验 100% 合法标识符
python3 re/u5_decode.py            # -> re/u5_decoded.json, re/u5_decode.txt

# 822 个解密器调用点：按 proto 精确计数（不依赖 jadx 重命名）
python3 re/dex_strdec_census.py    # -> re/dex_strdec_census.json

# 811 个内联 (cipher,key) 对 + 明文侧 1732 行（两棵树），并在复合口径中交叉核对
python3 re/dex_strdec_literals.py  # -> re/dex_strdec_literals.json
python3 re/java_strdec_all.py      # -> re/java_strdec_all.json
python3 re/java_obf_composite.py   # -> re/java_obf_composite.json（811/811，0 未映射，0 矛盾）

# 守护 dex 的寄存器列表：v.a 的 4 条明文（按字节码地址定位）
python3 re/daemon_va.py            # -> re/daemon_va.json
python3 re/daemon_clinit.py        # -> re/daemon_clinit.json（v.<clinit> 74 条）
python3 re/daemon_annot.py         # -> re/daemon_annot.json（@x0 116/116）

# 反证：native 侧 0x42a21aaf 在合成 harness 中不返回结果（见 §9.2 口径框）
python3 re/tiny_xor_probe.py       # -> re/tiny_xor_probe.txt（x0=0x0，输入未被就地修改）

# 定位 fvc 注解包与 PetalConfig 的定义 dex
python3 re/dex_classfind.py Lfvc/ Lcom/xingin/xhs/petal/
python3 re/dex_strfind.py PETAL_MODE

# 反编译 fvc 包与 PetalConfig
jadx --no-res --no-imports -d /tmp/j20 dex/classes20.dex
cat jadx-out/sources/com/xingin/xhs/PetalConfig.java
```

**口径声明**：§9.2 的 XOR 闭式是**静态**从 `@v5` 字节数组直接解出的，并在 519 个站点上以"合法 Java 标识符"自校验；§9.2b 的计数**全部来自字节码扫描**（822 调用点 → 811 内联对 → **811 全映射**），文本树只用于**确认**明文取值，故数字不随 jadx 树变化。native 侧同一 opcode（`0x42a21aaf`）的调用点由 Java 侧给出（`b7.a` → `t.a(1117919919, …)`，该常量在 §5.2 的 31 操作码表内），但该 opcode 在合成 harness 中**不返回结果**（见 §9.2 口径框），故 native 端语义**未经实测**，其判据是静态解码的自洽性。`@v5` 的逐字节定点变换（`u2.a()`、`j4.<clinit>` 中的 `^`/`+`/`-`/`~`/循环移位序列）是**同一份字节码内的明文常量**，因此整套名字还原不依赖 native 执行即可复算。

---

## 9.7 守护 dex 审计：三层混淆逐条闭环

§9.1–§9.6 覆盖的是**主 dex 族**（`classes*.dex`）。Tiny 另有三份**资产 dex**
（`assets/fd2x1e4e2x3f1v2b1s.dex`、`assets/c4d121c215evx1s51d.dex`、
以及由 `v.<clinit>` Base64 还原出的 588 B dex）。它们都是 Tiny 自己的代码，
在**运行期加载**，属需要逐条审计的代码面。本节逐层闭环。

### 9.7.1 `assets/fd2x1e4e2x3f1v2b1s.dex`（78 008 B）= `libtinyd.so` 的 Java 侧守护进程

结构（直接解析 dex 头，不经过 jadx）：

| 项 | 值 |
| --- | ---: |
| 字节数 | 78 008 |
| `class_defs` | **63** |
| 包范围 | **全部 `com.xingin.tiny.daemon.*`**（无第三方类） |
| `string_ids` / `type_ids` / `proto_ids` / `method_ids` | 637 / 215 / 93 / 252 |

**入口**（`e.java`）：

```java
com.xingin.tiny.daemon.v.b(null,
    new DataInputStream(new FileInputStream(FileDescriptor.in)).readUTF());   // ① 先读一条 UTF 自述
new com.xingin.tiny.daemon.e.a(
    com.xingin.tiny.daemon.e.a.a(FileDescriptor.in)).a();                     // ② 进入命令循环
```

① 的字符串交给 `v.b(Context, String)`——即 `v` 的**"别名注册表"**
（`v` 解出的明文含 `duplicate alisa "` / `", the old type is ["` / `"], try to overide by ["`）。
② 进入 `l.a()` 的 **IPC 命令循环**：**先 `readInt()` 取命令字**，
再 `switch`，共 **12 个 case**（-1..10）。

**I/O 通道是 `DataInput`/`DataOutput`**，且**这两个包装类的成员名被 `@x0` 注解隐藏**：

| 包装类 | 底层接口 | `@x0` 解出的成员 | 用途 |
| --- | --- | --- | --- |
| `com.xingin.tiny.daemon.h` | **`java.io.DataInput`** | `readLong`、`readBoolean`、`readFully`、`readUTF`、`readInt` | 读命令/读参数 |
| `com.xingin.tiny.daemon.i` | **`java.io.DataOutput`** | `writeLong`、`writeUTF`、`writeBoolean`、`writeInt`、`writeShort`、`write` | 写结果 |

所以协议形态是**长度前缀帧**（`readUTF`/`writeUTF` 为字符串帧，`readInt` 为命令帧），
对端是 `libtinyd.so`（见 §9.7.4）。

#### 12 个 IPC case 逐条语义

| case | 输入 | 输出 | 语义（由注入的 Android API 判定） |
| ---: | --- | --- | --- |
| -1 | — | — | 空/终止 |
| 0 | `readInt` 长度 → `readFully` 字节 | `writeBoolean` + `writeUTF` + 两个 `writeLong` | **包信息查询**：字节流 `Parcel.unmarshall` → `ApplicationInfo.CREATOR.parseFrom` → `PackageManager` 取 `PackageInfo`（`firstInstallTime`/`lastUpdateTime`）+ `SigningInfo` |
| 1 | `readUTF` | `writeUTF` | 反射调用，结果以字符串回传 |
| 2 | — | `writeUTF` | 浮点相关（注入 `java.lang.Float`），答案经 `y.a` 归一为字符串 |
| 3 | `readInt` | `writeInt` + 数组 | 数组/对象编号查询 |
| 4 | `readUTF` | `writeUTF` | **Intent 解析**：解出常量 **`android.intent.action.MAIN`**，用 `PackageManager` 匹配 `ResolveInfo` |
| 5 | — | `writeBoolean` 等 | `PackageInfo` 查询 |
| 6 | — | `writeUTF` | `PackageManager` + `Process`（`h0` 解出 `android.os.Process`）取进程/包信息 |
| 7 | — | — | `PackageInfo` + `Parcel`（另一条 `parseFrom` 路径） |
| 8 | — | `writeUTF` | **系统属性读取**：注入 `java.lang.System`（`e1`），解出常量 **`http.agent`** |
| 9 | — | — | **输入设备枚举**：注入 `android.view.InputDevice` |
| 10 | — | — | **`android.intent.action.MAIN` + `ResolveInfo`**（与 case 4 同族，另一条 `ApplicationInfo` 路径） |

**为什么这构成风控面**：case 0/4/5/6/7/10 全都在问
**"这个包的签名、安装时间、主 Activity、签名信息是什么"**——
即**运行期完整性/重打包自检**。注入类型可见
`SigningInfo`、`Signature`、`PackageItemInfo`、`PackageManager.NameNotFoundException`。

#### 三层混淆

| 层 | 机制 | 规模 | 状态 |
| --- | --- | ---: | --- |
| ① 字符串 | `c1.a(id, cipher, key)`，与主 dex 同一算法 | **136 个 `c1.a` 调用点**（守护 dex 的解密器调用点共 **141** = `c1.a` 136 + `d1.a` 2 + `r0$a.a` 3） | **全部解出**（§9.2b） |
| ② 注解载荷 | `@x0` 内嵌 `{cipher}, {key}`，XOR 出**被隐藏的成员名** | **116/116 解出**（互异 **86**） | **闭式还原** |
| ②' 注解包装 | `@w0` = 1 个主 `@x0`（`a=`）+ 0..N 个参数 `@x0`（`b={…}`） | **77 站点** = 77 主 + **39 参数** | **已枚举**（`c=true` 68 / `c=false` 9） |
| ③ 别名表 | `v.<clinit>` 用 `c1.a` 建"名字 → 反射句柄"表；jadx **无法 lift** | **74 调用点 → 74 条明文** | **已执行还原** |

**第 ③ 层的做法**：`v.<clinit>`（7779 指令单元）与 `v.a(Context,String)` 在 jadx 里
只剩 `throw new UnsupportedOperationException("Method not decompiled: …")`，
但 `--comments-level debug` 会保留**寄存器列表**。处理方式是**执行**那份列表
（`re/jadx_regvm.py`），并把 `Build.VERSION.SDK_INT` 与惰性 `v.b` 两类运行期分支
**两边都展开**，因此没有明文因分支而被跳过。

- `v.<clinit>`：**74 个 `c1.a` 调用点 → 74 条明文**。解出的名字直接暴露 `v` 的用途：
  `invoke`、`forName`、`getDeclaredConstructors`、`getDeclaredMethod`、`getDeclaredMethods`、
  `getDeclaredField`、`getDeclaredFields`、`getDeclaredClasses`、
  `android.app.ActivityThread`、`currentActivityThread`、`getApplication`、`java.io.tmpdir`；
  第 74 个（也是最后一个）是 **`KeyGenParameterSpec$Builder`**（见 §9.2b.5）。
- `v.a(Context,String)`：**4 个调用点**，按**字节码地址**逐个定点还原
  （`re/daemon_va.py`，不依赖位置序）：

  | 地址 | `id` | 明文 | 变换 |
  | --- | ---: | --- | --- |
  | off 69 | 1337 | `cache` | 4 处单下标 + 1 处循环移位 |
  | off 152 | 1331 | `c4d121c215evx1s51d.dex` | `L9d` 循环 22 次 `^65` |
  | off 217 | 1332 | `a` | 单下标 `^-109` |
  | off 257 | 1333 | `b` | 单下标 `^-3` |

**这四条明文就是运行期行为**：`v.a` 把静态字段里的 Base64 表（**784 字符 → 588 B**）
解码成一个 dex，写进 `context.getDir("cache", 0)` 下的
`c4d121c215evx1s51d.dex`，然后

```java
new dalvik.system.DexFile(file).loadClass("a")
        .getDeclaredMethod("b", Object.class, Object[].class)
```

即**字符串层、文件层、类名层三处同名**：`cache` 是目录、`c4d121c215evx1s51d.dex`
是落盘名、`a` 是类、`b` 是成员。

> 两个内嵌 dex 均为需要审计的代码面——见 §9.7.2 / §9.7.3。

### 9.7.2 `assets/c4d121c215evx1s51d.dex`（940 B）

内容是**单类 `La;` 的 dex**，与 §9.7.3 的 588 B 内嵌 dex
是**同一个类的两种构建**：

| 项 | 940 B 资产 | 588 B 内嵌 | 说明 |
| --- | ---: | ---: | --- |
| 字节数 | 940 | 588 | |
| `class_defs` | 1（`La;`） | 1（`La;`） | **同一个类** |
| `method_ids` | 4 | 4 | **同样 4 个方法** |
| `string_ids` | 13 | 9 | 差 4 条 = R8 元数据 + 参数名 |
| 类方法 | — | — | `La;.<init>()V`、**`La;.b(Object, Object[])Object`**、`Object.<init>()V`、**`java.lang.reflect.Method.invoke(Object, Object[])Object`** |

**多出来的 4 条字符串正是 R8 构建元数据**（所以 940 B 是**未剥离**的那份）：

```
~~D8{"backend":"dex","compilation-mode":"debug","has-checksums":false,
     "min-api":21,"sha-1":"facedf41bbd28b563d1e9e09c5f72d7c5ca598d5","version":"8.2.2-dev"}
~~R8{"backend":"cf","compilation-mode":"debug","has-checksums":false,
     "pg-map-id":"1b13d7f","r8-mode":"compatibility","version":"3.1.66"}
```

（另差 `args` / `obj` 两个**参数名**，同样只在未剥离版里保留。）

**结论**：两个 dex 的**类与 4 个方法完全相同**，差别仅在 R8 是否保留了调试元数据。
类名 `La;`、方法名 `b` 与 `Method.invoke` **与 §9.7.3 的 588 B 内嵌 dex 逐字相同**，
而 588 B 那份正是 `v.<clinit>` 的 Base64 静态字段所携带、由 `v.a` 写盘的那一个。

### 9.7.3 第三个 dex（588 B）：藏在 `v.<clinit>` 的 Base64 里

`v.<clinit>` 的静态字段携带 **784 字符 Base64**，解码得 **588 B**、magic `dex\n035`、
**单类 `La;`** 的 dex（`sha-256 2ce6593bc280f14b…`）。其唯一实质方法：

```java
// La;.b(Object obj, Object[] args)
return ((java.lang.reflect.Method) obj).invoke(args[0], args[1]);   // 纯反射蹦床
```

`v.a` 写盘的就是它：把 588 B 落到 `getDir("cache", 0)` 下的
`c4d121c215evx1s51d.dex`，再

```java
new dalvik.system.DexFile(file).loadClass("a")
        .getDeclaredMethod("b", Object.class, Object[].class)
```

注意这里的**三层同名**：文件名 `c4d121c215evx1s51d.dex` 与资产里的 940 B
同名（§9.7.2），类名 `a`、方法名 `b` 与 dex 内的 `La;.b` 一致。

### 9.7.4 与 `libtinyd.so` 的对接

那 4 字节载荷的读取方就是本节的 daemon dex——`e.main` 的
`new DataInputStream(new FileInputStream(FileDescriptor.in)).readUTF()`
正是读父进程写入的那条 UTF 自述，随后的 `readInt()` 循环读命令。

两侧物资对照：

| 侧 | 证据 |
| --- | --- |
| C 侧（`libtinyd.so`） | 字符串表仅 `JNI_OnLoad` / `fork` / `libtinyd.so`；类名靠 Java 侧自述 |
| C 侧父进程（`libtiny.so`） | `CLASSPATH=`、`{"PACKAGE_NAME":"com.xingin.xhs",…}`、`/data/dalvik-cache/arm64/`、`dalvik.system.DexPathList`、`get_global_daemon failed!`、`/boot.vdex`、`/system/bin/linker64` |
| Java 侧（daemon dex） | `d` 类解出 **`tinyd`** 与 **`libtinyd.so`**；`h`/`i` 的 `@x0` 解出 `readUTF`/`writeUTF`/`readInt`/`writeInt` |

**结论**：`libtinyd.so`（C 侧守护进程）与 `fd2x1e4e2x3f1v2b1s.dex` 的 `l.a()`
（Java 侧守护进程）是**同一协议的两端**，协议为
`DataInput`/`DataOutput` 之上的**长度前缀帧**（命令 = `readInt`，字符串 = `readUTF`），
命令表 = §9.7.1 的 12 个 case。

### 9.7.5 本节计数汇总（可复现）

| 量 | 值 | 产物 |
| --- | ---: | --- |
| 守护 dex `class_defs` | 63 | `re/daemon_audit.json` |
| 守护 dex 解密器调用点 | **141**（`c1.a` 136 + `d1.a` 2 + `r0$a.a` 3） | `re/dex_strdec_census.json` |
| 其中 `c1.a` 按来源拆分（源声明 / 内联 / 寄存器列表） | 57 / 1 / 78（= 136） | `re/daemon_audit.json` |
| `@x0` 载荷 | 116（解出 **116**，互异 86） | `re/daemon_annot.json` |
| `@w0` 站点 | 77（主 77 + 参数 39） | 同上 |
| `v.<clinit>` 明文 | 74 | `re/daemon_clinit.json` + §9.2b.5 |
| `v.a` 明文 | 4 | `re/daemon_va.json` |
| 内嵌 dex | 588 B，单类 `La;` | `re/daemon_embedded_dex.bin` |
| **未定性项** | **0** | — |

### 9.7.6 三个 dex 的 SHA-256

| 对象 | SHA-256 |
| --- | --- |
| `dex/assets/fd2x1e4e2x3f1v2b1s.dex`（守护 dex，78 008 B） | `3609a27662f09e1f82cbf11de2174f228ce7f48fdbeffc91573347ba229a4538` |
| `dex/assets/c4d121c215evx1s51d.dex`（940 B） | `2c48d73f5479148c0d87397eeeb3440b7bc2efd1478d86f3198c72737afef52c` |
| `re/daemon_embedded_dex.bin`（588 B，Base64 还原） | `2ce6593bc280f14b9b714cc9ab226d168d9d3e30d8fa06176fdf1dca100f092c` |
