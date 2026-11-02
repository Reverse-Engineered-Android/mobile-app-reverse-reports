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

## 5. Native 方法体边界

`libShanYCore.so`、`libencrypt.so`、`libhtsfx.so`、`libndktest.so` 的
`.text` 位于高熵/受保护区域，section header 被截断，静态反汇编不能直接得到
完整函数体。已验证的是：

```text
符号/导入 -> JNI 入口 -> 加密/压缩/环境探测能力存在
```

未验证的是每个 native 函数的完整指令语义、密钥派生、处罚判定和反调试反应。
代码与常量位于未被节头覆盖的文件间隙（详见 report.md §1.7 的逐库熵值），
因此本报告把它们标为“符号已恢复、方法体受保护”，不声称完整 native 逆向。

## 6. 运行态文件与版本边界

只读设备当前安装版本为 `9.05.007` / `20261028`，与主样本不同。
`files/i12196010e51071ed` 当前大小为 `69,262,848` 字节，头部结构是
`04 00 00 00` + 等于自身大小的 LE 长度 + 32 个十六进制字符（16 字节 ID）；
它与主样本 `assets/ijiami.dat` 的 40 字节头部并不相同（后者长度字段
`69,114,092` 是解包后大小），不能把两者写成同一文件。`assets/ijiami.ajm` 的头部是
`indl01\\0\\0`，也不是 `ijiami.dat` 的魔数。

`files/app_payload.dex`、`app_payload.version`、`gjzq_*` 是分析注入的
`GjzqProbe`，不作为 App 下发载荷或风控证据。

## 7. 结论

已还原：壳控制流的有效调用链、H5 keystream、ELF 符号表恢复方法、JNI/加密
导入面、风险中心/适当性接口和本地信号缓存。

未还原：服务端风控评分与处罚、受保护 native 方法体内部语义、加固器数据体
内部格式、OAID 实际上报和保存期限。
