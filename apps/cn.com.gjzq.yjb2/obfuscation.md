# 混淆与载荷还原

## 1. 范围

本文件记录 `9.05.004` 样本中可复现的混淆层：DEX 外壳控制流、H5 `.r`
资源加密、ELF 节头/符号表破坏，以及 native 自保护入口。它不把不可读的
方法体写成已还原算法。

## 2. DEX 外壳控制流

APK 只有 `classes.dex`（`13,644` 字节），类为
`s.h.e.l.l.{S,A,C,N}`。`S.c(String)` 和 `S.il()` 的反编译结果包含大量
`new Object()`、`hashCode()`、重复 `try/catch(Throwable)` 的无副作用块；
这是控制流平坦化/垃圾块，不是反调试判定。

去混淆后的有效链条是：

```text
S.attachBaseContext
  -> S.c(abi)
  -> ZipFile(APK).getEntry("assets/ijm_lib/<abi>/libexec{,main}.so")
  -> CRC 比对后释放到 files/
  -> N.<clinit> System.load(files/libexec.so, files/libexecmain.so)
  -> A.instantiateApplication 反射 ActivityThread.mBoundApplication
  -> 替换真实 Application / ClassLoader
```

`S.il()` 的有效语义只是逐行读取 `/proc/self/maps`，判断 `/lib64/libart.so`、
`/lib64/libaoc.so`、`/bin/linker64` 是否存在，用于 ABI/运行环境识别；不能写成
反调试。`S.ld()` 另有 `Build.SUPPORTED_ABIS` 和 linker 前缀回退。

## 3. H5 `.r` 位置相关 keystream

`assets/yjb.zip` 有 `967` 个 `.r` 条目（其中 `966` 个位于 `yjb/`，另有
`h5info.txt.r`），最大文件 `6,165,153` 字节。`portal_yjb.zip` 有 `542`
个条目、`104` 个 `.r`。恢复的 keystream 长度为 `6,165,153` 字节，SHA-256：

```text
fb1cd66790b967059021938cb1adc6e430387b6d5007286e24e446b1e875c1fd
```

前 16 字节已由 HTML/TrueType 明文交叉验证：

```text
b3 09 cd 5d e7 1c 06 59 d1 77 a8 02 21 4c c7 9f
```

结构检查覆盖文本（928）+ 字体（20）共 `948` 个成员：`940` 个满足
UTF-8 合法或 TrueType 魔数，`8` 个文本成员含少量非法序列；其中 `920` 个
文本成员完全合法。非法字节合计 `1,185`（966 个非法序列），占
`128,065,750` 字节的 `0.000925%`。同一 keystream 在设备侧也成立：取
`files/h5info.txt.r` 的 1,910 字节异或后得到模块版本 JSON，首个键名带一个
歧义字节（解密为 `%margimtrading`）。因此报告给出全长度恢复和明确误差率，
不声称逐字节 100% 正确。解密脚本见 [tools/yjb_keystream.py](tools/yjb_keystream.py)。

## 4. ELF 节头与符号表混淆

## 5. 统一的自解密 stub 与运行态载荷

四个 APK 库、`.tcache` 中的 `libhts.so` 以及 `files/libijmDataEncryption.so`
都不是可直接反汇编的常规 ELF：可执行段被替换为**同一套自解密 stub**，函数体
在文件里不以明文存在。判据是：`DT_INIT` 处字节可以正常反汇编，而 `dynsym`
指向的函数体（包括标准 C++ 符号）无法解码为合法 AArch64 指令。以 `.tcache`
中未 strip 的 `libhts.so` 为例，`__cxa_get_globals_fast`（`0x1c8d8`）文件首
8 字节是 `38 6c d8 2b 1f 9f 3e a0`，`_ZNSt10bad_typeidD0Ev`（`0x1bed8`）是
`5f 4c 85 0f c2 46 53 72`，都不是指令。

| 库 | 恢复符号 | `DT_INIT` | 缓存维护指令对 |
|---|---:|---:|---|
| `libShanYCore.so` | 23 | `0x13b8` | `0x1400` / `0x1404` |
| `libencrypt.so` | 27 | `0x13e8` | `0x1430` / `0x1434` |
| `libndktest.so` | 53 | `0x3d28` | `0x3d70` / `0x3d74` |
| `libhtsfx.so` | 226 | `0xdf20` | `0xdf68` / `0xdf6c` |
| `.tcache/libs/libhts.so` | 239 | `0x20458` | `0x204a0` / `0x204a4` |
| `files/libijmDataEncryption.so` | 640 | `0x52040` | 同型 stub |

每份 stub 的 `+0x48` / `+0x4c` 都是 `dc cvau, x0` / `ic ivau, x0` 组成的
按 cache line 步进循环，即运行期改写代码页后自行刷 I-cache。以
`libShanYCore.so` 的 stub 为样本，指令级语义可归并为三层：

```text
0x141c  位读取器：adds w4,w4,w4 / ldr w4,[x0],#4        MSB-first 32 位字流
0x1434  gamma 长度：w1=1；do { w1=w1*2+bit } while(bit==0)
0x1450  字面量复制；0x14a4..0x14b0 匹配复制（dst+w5 负偏移回填）
0x1470..0x147c  结束标记：~(byte<<8 | (len-3)) == 0
```

在 AArch64 模拟器中执行该 stub 可观测到固定顺序的系统调用：

```text
mmap(0, 0x15e4, PROT_RW, MAP_PRIVATE|MAP_ANONYMOUS)      自拷贝缓冲
mprotect(<buf>, 0x15e4, PROT_READ|PROT_EXEC)             置为可执行
mmap(0, 0xfff0, PROT_RW, MAP_FIXED|MAP_PRIVATE|MAP_ANON) 代码目标页
```

即“先把自身搬到匿名页并置 RX，再把 64 KB 代码页解出后跳转”。模拟在目标页
映射后停住，未完成整个解包，因此本节只把 **stub 结构与系统调用顺序**列为
已验证，不把解出的方法体内容列为已还原。

运行态证据与此一致：应用进程存活时 `/proc/<pid>/maps` 出现 7 个
`00:00 0` 匿名 `r-xp` 段，长度分别为 `0x3800`、`0x1000`、`0x9800`、
`0x20d000`、`0xdf000`、`0x1500`、`0x3c000` 字节，其中 `0x1000` /
`0x1500` 与 `0xfff0` / `0x15e4` 量级相符。`libShanYCore.so`、`libencrypt.so`
在 maps 中只有 `r--p` / `rw-p` 而没有 `r-xp`，其可执行代码只存在于上述匿名
段；进程退出后匿名段与 `.tcache` 一并消失，说明两者都是解包期临时产物。


四个关键 ARM64 库的 section header 表都只有 `7` 个条目（`6` 个实际节：
`.dynsym`/`.dynstr`/`.symtab`/`.strtab`/`.shstrtab`/`.dynamic`，另有 1 个
空 `NULL` 节），**没有 `.text` 节**；`.dynsym sh_size` 只声明 `72` 字节
（3 个符号），普通 `readelf` 因 `BAD[...]` 索引报错。利用 `DT_SYMTAB` 到
`DT_STRTAB` 的实际间距和 `DT_SYMENT=24` 可恢复完整动态符号表：

| 库 | 声明符号 | 恢复表项 / 带名 | `DT_INIT` |
|---|---:|---:|---:|
| `libShanYCore.so` | 3 | 23 / 20 | `0x13b8` |
| `libencrypt.so` | 3 | 27 / 24 | `0x13e8` |
| `libndktest.so` | 3 | 53 / 50 | `0x3d28` |
| `libhtsfx.so` | 3 | 226 / 223 | `0xdf20` |

恢复出的关键符号包括：

- `libShanYCore.so`：`JNI_OnLoad`、`ptrace`、`__android_log_print`、`usleep`；
- `libencrypt.so`：四个 `Java_com_tztEncrypt_*Encrypt13`、`CRC32`、`tabKey`、
  `srand`/`rand`；
- `libndktest.so`：`Java_cn_com_gjzq_crypto_GjEncrypt_*`、`_Z3RC4PhiPKhi`、
  `sm4_*`、`sms4_*`、`_Z14pkcs7_input_pdPhPi`、`AlgorithmApi_*`；
- `libhtsfx.so`：`JNI_OnLoad`、`ijiami`、`mmap`、`mprotect`、`dlopen`、
  `dlsym`、`inflate*`、`AAsset*`，以及大量 `x.NN`/`y.NN` BSS 槽位。

四个库的有效 `DT_INIT` 内 `+0x48` 处都有 ARM64 缓存维护指令
`dc cvau, x0` / `ic ivau, x0`（改写在 AArch64 上必须显式刷 I-cache），
对应地址分别为 `0x1400` / `0x1404`、`0x1430` / `0x1434`、
`0x3d70` / `0x3d74`、`0xdf68` / `0xdf6c`，与运行时解密/打补丁边界一致；
反汇编中**没有**任何库存在字面字符串 `PROTECT_EXIT`，因此不把该标记写成
已观测事实。`UPX!` 字符串在四库中各出现 `3` 次，但 `upx -d` 返回
`NotPackedException`，所以不把它们标为可直接解包的标准 UPX 文件。

## 6. Native 方法体边界

`libShanYCore.so`、`libencrypt.so`、`libhtsfx.so`、`libndktest.so` 的
`.text` 位于高熵/受保护区域，section header 被截断，静态反汇编不能直接得到
完整函数体。已验证的是：

```text
符号/导入 -> JNI 入口 -> 加密/压缩/环境探测能力存在
```

未验证的是每个 native 函数的完整指令语义、密钥派生、处罚判定和反调试反应。
代码与常量位于未被节头覆盖的文件间隙（详见 report.md §1.7 的逐库熵值），
因此本报告把它们标为“符号已恢复、方法体受保护”，不声称完整 native 逆向。

## 7. 运行态文件与版本边界

只读设备当前安装版本为 `9.05.007` / `20261028`，与主样本不同。

`files/i12196010e51071ed` 完整取回后为 `69,262,848` 字节，SHA-256：

```text
58d34180407eb06e901802c520d1a45d8145a113745d29f9919447d2c42074f
```

头部是 `04 00 00 00` + 等于**自身大小**的 LE 长度 `69,262,848` + 32 个
十六进制字符 ID `ed186bf3f8a4acf0d8450fdb005efa66`。它的 mtime
`2026-11-02 12:01:41.631` 与同目录 `libexecmain.so` 的
`12:01:41.047` 同秒，说明两者由同一次解包写出。主样本 `assets/ijiami.dat`
的头部是同一格式但不同实例：`14,330,583` 字节、长度字段 `69,114,092`
（`0x041E98EC`，即解包后大小）、ID `d39c9da2c2ccce5f0f6f0129e91152ce`，
因此两者不能写成同一文件。

该载荷的统计特征已实测：正文熵 `4.639`、字节 `0xB2` 占 `32.8%`、
`0x41` 占 `22.8%`、`gzip -c` 后为 `14,513,395` 字节（约 `4.77:1`）。全文
不含 `dex\n`、`\x7fELF`、`PK\x03\x04`、`cn/com`、`Ljava/`、
`yongjinbao` 任一标记；对周期 `1/2/4/8/16/32` 的按列众数 XOR 密钥做解扰后
熵仍为 `4.64`–`4.76`、零字节占比 `0.33`，均不出现 DEX/ELF 头。因此可以否定
“该载荷是明文 DEX/ELF”和“该载荷是强加密数据”，但**内部格式仍未还原**，
不能写成已解开。

`.tcache/` 是解包期临时目录，应用进程存活时可读，进程退出后消失。取回时
内容与哈希：

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `.templ.jar` | `423,320` | `ce15254e51e3cc2a1354a9ed6d70642da2c1f50c1d5bb0fd8523379b09085b0` |
| `.tempr.jar` | `29,075` | `2dac063e5aec6bfe0fbbb9e002cba9e1d4f88391b3d8f4c3139c4c65ca1b4828` |
| `libs/libhts.so` | `151,840` | `d6d9c0f6c607a27d4c06c01bd0f6a64f35128ff801159c2bb0f695fd44ef2df5` |

`.templ.jar` 有 5 个成员（1 个 161 字节 PNG 与 `lib/{armeabi-v7a,arm64-v8a,
x86,x86_64}/libhts.so`）；`.tempr.jar` 有 8 个成员，包括
`assets/yjb_cert.der`（`1,654` 字节）、`assets/client_ca.crt`（`1,360`
字节）、4 个 `assets/open_sdk/errorview*` 页面和 `AndroidManifest.xml`
（`148,388` 字节）。`.tcache/libs/libhts.so` 与 `.templ.jar` 的
`lib/arm64-v8a/libhts.so` 成员逐字节相同。

`files/app_payload.dex`、`app_payload.version`、`gjzq_*` 是分析注入的
`GjzqProbe`，不作为 App 下发载荷或风控证据。

## 8. 结论

已还原：壳控制流的有效调用链、H5 keystream、ELF 符号表恢复方法、六份
自解密 stub 的地址/结构/系统调用顺序、JNI 与加密导入面、风险中心与适当性
接口、本地信号缓存。

未还原：受保护 native 方法体的指令语义与密钥派生、加固载荷
`i12196010e51071ed` 的内部格式、服务端风控评分与处罚、OAID 实际上报与
保存期限。运行期解密后的代码页只存在于匿名 `r-xp` 段，需在进程存活时
只读捕获才能继续，静态文件不足以闭环。
