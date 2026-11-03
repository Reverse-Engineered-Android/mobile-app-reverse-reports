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
`..h5info.txt.r`），`.r` 载荷合计 `131,312,489` 字节，最大成员
`yjb/ai-adviser/optional.js.r` 为 `6,165,153` 字节。`portal_yjb.zip` 有 `542`
个条目、`104` 个 `.r`。全部 `.r` 按**同一位置相关 keystream** 逐字节异或，
与文件名、大小、扩展名无关。

### 3.1 长度与哈希

恢复的 keystream 长度为 `6,165,153` 字节（等于最大成员）：

```text
b8f6bbc75f3d5b667239114a1366d41d4420e843a477560f2dbd58c35fb2ceba
```

前 16 字节由 HTML/TrueType 双明文交叉验证：

```text
b3 09 cd 5d e7 1c 06 59 d1 77 a8 02 21 4c 7? 9? 1a? ...
b3 09 cd 5d e7 1c 06 59 d1 77 a8 02 21 4c c7 98 43 75 36 53 df 52 a1 ae …
```

### 3.2 两个歧义字节的修正

早期求解在这两个位置给出了**语义上不成立**的候选值，它们是唯一的系统性误差，
且可用共享 webpack 序言交叉验证：

| 偏移 | 旧值 | 修正为 | 判据 |
|---:|---|---|---|
| `15` | `0x9f` | `0x98` | 89 个文件共享 `!function(e){…` 序言，旧值解密出 `fuiction#t(`，修正后为 `function t(` |
| `21` | `0x51` | `0x52` | 同上，旧值解出 `vau t={}8fu…`，修正后为 `var t={};fu…`；UMD 头 `sblf.weapack`→`self.webpack` 亦同 |

修正只影响这两字节，其余 `6,165,151` 字节不变（逐字节比对全库 921 个成员，
差异位置集合恰为 `{15, 21}`）。修正后的统计：

| 指标 | 旧 keystream | 修正后 |
|---|---:|---:|
| 文本成员（`.js/.html/.css/.svg/.less/.ini/.txt`） | 928 | 928 |
| 文本字节 | `128,065,750` | `128,065,750` |
| 完全 UTF-8 合法成员 | `920` | `921` |
| 含非法序列的成员 | `8` | `7` |
| 非法序列 / 非法字节 | `1,185` / `1,404` | `1,182` / `1,401` |
| 非法占比 | `0.001096%` | `0.001094%` |
| TrueType 魔数通过 | `20 / 20` | `20 / 20` |
| `node --check` 通过的 JS | `82 / 507` | **`369 / 507`** |
| 共享 webpack 序言（89 个文件）通过 | `0 / 89` | `60 / 89` |
| `!function(` 开头的 JS 通过 | `9 / 263` | `194 / 263` |

`node --check` 是这里最强的独立判据：单字节修正让可解析文件数从 `82` 涨到
`369`（`+287`），且**没有任何文件由可解析变为不可解析**（回退集合为空）。
说明这两个字节不只是在局部「看起来更像」，而是被 287 个文件的语法结构共同
证实。

### 3.3 剩余歧义与错误目录

其余 `1,182` 个非法序列集中在 7 个文件（`optional.js` 340、`vendor.js` 298、
`postText.js` 263、`ycomment.js` 101、`askSecretary.js` 92、`userCenter.js` 85、
`main.js` 3），它们的共同点是**含有大量长中文文案与 base64 图片字面量**——
keystream 求解依赖 UTF-8 合法性约束，而这些位置的中文/`kAAAB…`串提供的约束
比 JS 关键字少。解出的系统性错字可以逐个定位到具体偏移，例如：

| 解出错字 | 应为 | 说明 |
|---|---|---|
| `fuiction#` / `functnon` | `function ` | 序言区（已由 §3.2 修正） |
| `sblf.weapack` | `self.webpack` | UMD 头 |
| `Chrnkai_` / `webpackChrnkai_bdviser` | `webpackChunkai_adviser` | 分块名 |
| `vau t={}` | `var t={}` | 变量声明 |
| `ctnon(mogule` | `cont(inue)`+`module` | 循环体 |
| `%objecw` | `typeof` | 关键字；同族还有 `"use strict"` 里的反引号字节被解成 `t` |
| `aunctiln` / `vonse` / `deft(` | `function` / `console` / `default` | 常见标识符 |

这些错字的共同形态是「相邻字节差 1–2 位」，与「在多个候选值中选错」一致，
而不是整体偏移。因此报告给出全长度恢复 + 精确误差率，不声称逐字节 100%。
解密脚本见 [tools/yjb_keystream.py](tools/yjb_keystream.py)（`PREFIX` 已按上表修正）。

### 3.4 设备侧交叉验证

同一 keystream 也适用于设备运行时下载的 `files/yjb/**.r`（`210 MiB`、
`3,382` 个文件）。取 `files/h5info.txt.r` 的 1,910 字节异或后得到模块版本
JSON，其中 `m`→`%` 的单个歧义字节仍在（首个键名解出 `%margimtrading`），
与 §3.3 的误差率一致。

## 4. JS 层混淆与 native 桥 ABI

H5 载荷除了 §3 的整包异或，还有两层「名字层面」的混淆：短码表与 native
功能号。两者都不是加密，但都是阅读业务代码必须过的第一道门槛。

### 4.1 三张短码表

| 表 | 位置 | 条目 | 值域 |
|---|---|---:|---|
| `IAR_INTERFACE` | `stockInformation/common/vendors/Tool.js` `0x2a` | 28 | `serviceName.*` 全名 + `type:"json"` + `method` |
| `Interface` | `stockInformation/common/vendors/interface.js` `0x0` | 65 | GET 路径后缀（`/news/…`） |
| `UniqueKey` 字面量 | 全库 | 169 | 短码直接作参数名 |

短码与语义之间**没有可推导关系**（`NAQ` / `NNQOP` / `KFCPNEWS` 之类），唯一
真值是表本身，因此这三张表必须整表中继才能读懂调用点。`Tool.fetchToIAR`
在发请求前做一次 `param.UniqueKey = IAR_INTERFACE[param.UniqueKey].UniqueKey`，
即短码只活在前端源码里，出网关时已还原为全名。

### 4.2 native 功能号 ABI

`stockInformation/common/js/NativeAPI.js` 定义唯一桥函数
`callNative(funcId, param, cb, noNative)`（`0x642`）：

```js
let callbackId = uuid().replace(/-/g, '');
if (functionIDClearOnDisappear.indexOf(funcId) === -1)   // [1000, 1001] 之外
    callbackId = 'keep_' + callbackId;                    // 加前缀 = 页面销毁后仍保留
window.respCallbacks[callbackId] = cb;
window.yjbInterface.execute(funcId, JSON.stringify(param), callbackId);   // Android
window.webkit.messageHandlers.yjbInterface.postMessage({functionId, param, callbackId}); // iOS
```

native 侧固定回调 `window.respFromNative(data, callbackId)`（`0xc2b`），取出并
删除回调。**`param` 是二次序列化的 JSON 字符串**，返回值也是字符串，业务层
`JSON.parse`。常量 `YJBExec = callNative`（`0x4d9`）。

功能号按千位分域，静态可枚举 **62** 个实际被调用的号（共 10,372 处调用点），
其中 38 个在 SDK 文档注释里有完整签名：

| 段 | 语义 | 代表功能号 |
|---|---|---|
| `1xxx` | 交易/行情/文件 | `1000` 交易数据、`1001` YJBAPI、`1002` 行情、`1003` `readFile`、`1004` `uploadFile` |
| `2xxx` | 系统参数与内存/磁盘 | `2000` `readSystemParameter`、`2001/2002/2003` 内存读/写/删（`{namespace,keys}`）、`2004/2005` 磁盘写/读、`2006` 导航条定制、`2008` `openNewWindow`、`2009` `closePage`、`2011` 生命周期、`2012` 右侧按钮 |
| `3xxx` | 设备能力 | `3000` 拨号、`3002` `openApp`、`3003` `showAlert`、`3004` `chooseImg`、`3005` AnyChat 视频见证、`3007` 扫码、`3011` 系统设置、`3012` 支付宝、`3013` 微信支付、`3014` 角标、`3015` 微信分享、`3016` 状态栏、`3017` 关 loading、`3018` 活体检测、`3019` 权限申请、`3020/3021` Apple Pay、`3022–3024` 日历、`3038` base64 图片、`3039` 微信客服、`3040` 截屏监听、`3045` 配置跳转 |
| `4xxx` | 金融/登录/风控 | `4000` 金融登录取 `uKey`、`4001` 注销（`1` 普通/`2` 信用/`3` 担保）、`4002` 本地跳转、`4003` 持仓联动、`4004` `hybridGetPassport`、`4007` 错误描述、`4009` `doneSuitability`、`4011/4020` 自选增删、`4012` 登录状态、`4014` `saveLog`、`4015` `userLogin`、`4016` `userLogOut`、`4017` 资金流向、`4024–4026` 人脸/皮肤/开页、`4028` `userLoginModal`、`4030` `loginWithAccount`、`4031` 登录扩展、`4735` `needUKey` 变体 |

`readSystemToMemory(2000)` 的键集合是设备上下文的唯一来源：
`appVersion / systemName / machine / IP / systemVersion / interfaceVersion / uid`；
`nonSystemParam(4004)` 提供
`passportId / userId / loginStatus / tradeOvertime / clientId / mobileCode /
productId / optionAccount / uKey(s) / opStation / fundAccount / marginAccount`。
这两组键名就是全报告里出现的 `($opStation)`、`($fundAccount)` 等占位符的
取值来源。

### 4.3 混淆的分层结论

| 层 | 手段 | 是否可静态还原 |
|---|---|---|
| H5 资源 | 整包位置相关 XOR（§3） | 可，全长度恢复 + 0.0011% 误差 |
| H5 名字 | 短码表 / `UniqueKey` / `serviceName` 三套并存 | 可，表即真值 |
| 桥接 | 数字功能号 + 二次 JSON 序列化 | 可，`callNative` 单一入口 |
| DEX | `s.h.e.l.l` 控制流平坦化 + 垃圾块（§2） | 可，去平坦化后调用链清晰 |
| 原生 | 节头破坏 + 自解密 stub（§5、§6） | 符号可恢复，方法体不可 |
| 载荷 | `i12196010e51071ed` 私有格式（§8） | 未还原 |

即「名字层与容器层的混淆全部可还原，代码层（native 方法体）不可静态还原」。
这是本报告在混淆维度上的边界：不把受保护的方法体写成已还原算法。

## 5. ELF 节头与符号表混淆

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

## 6. 统一的自解密 stub 与运行态载荷

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


## 7. Native 方法体边界

四个库的「stub 可读、方法体不可读」不是主观判断，可以量化：用 capstone 对
整个文件做线性反汇编（`skipdata=True`，不可解码字节记为 `.byte`），统计
不可解码指令占比——

| 库 | 文件大小 | RX `LOAD` filesz | 线性反汇编指令 | 不可解码 | 占比 | `DT_INIT` 起 288 B |
|---|---:|---:|---:|---:|---:|---:|
| `libShanYCore.so` | `0x2288` | `0x1a64` | 2,210 | 770 | `34.8%` | 72 / **0** |
| `libencrypt.so` | `0x2a90` | `0x1a94` | 2,724 | 963 | `35.4%` | 72 / **0** |
| `libndktest.so` | `0x7288` | `0x43d4` | 7,330 | 2,416 | `33.0%` | 72 / **0** |
| `libhtsfx.so` | `0x10f60` | `0xe5cc` | 17,368 | 8,592 | `49.5%` | 72 / **0** |

即**入口 stub 100% 是合法指令，其余三到五成字节不是指令**。抽查
`dynsym` 指向的函数体同样失败：`libShanYCore.so` 的 `JNI_OnLoad`（`0x89c`）
首 160 字节里 29/40 条不可解码（`72%`），`libndktest.so` 的 `_ZdlPv`（`0x27`）
12/40（`30%`），`libencrypt.so` 的 `ava_com_tzt_en…`（`0x2c8`）2/40（`5%`）。
这些地址的字节分布与正常 AArch64 代码的指令字段约束不符，佐证 §6 的自解密
结论：**文件里的函数体是密文，只有 stub 是明文**。

结构上还有一条独立证据：四个库的 program header 都只有两个 `LOAD`
（`R+X` 与 `R+W`），而 section header 表的 `7` 个条目全部落在 `R+W` 段
（`.dynsym`/`.dynstr`/`.symtab`/`.strtab`/`.shstrtab`/`.dynamic` + `NULL`），
**没有任何 section 覆盖可执行段**。因此「读节头」永远拿不到代码，
必须读 program header。可执行段与读写段之间的虚拟地址空洞（`0x11d58` /
`0x11d18` / `0x16d88` / `0x1b700`）在文件里对应一段低熵填充
（熵 `3.90` / `4.40` / `0.78` / `5.10`），不是代码。

已验证的是：

```text
符号/导入 -> JNI 入口 -> 加密/压缩/环境探测能力存在
```

未验证的是每个 native 函数的完整指令语义、密钥派生、处罚判定和反调试反应。
因此本报告把它们标为“符号已恢复、方法体受保护”，不声称完整 native 逆向。

## 8. 运行态文件与版本边界

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

## 9. 结论

已还原：壳控制流的有效调用链、H5 keystream（含两字节歧义修正与
`node --check` 交叉验证）、三张短码表与 native 功能号 ABI、ELF 符号表恢复
方法与不可解码比例、六份自解密 stub 的地址/结构/系统调用顺序、JNI 与加密
导入面、风险中心与适当性接口、本地信号缓存。

未还原：受保护 native 方法体的指令语义与密钥派生、加固载荷
`i12196010e51071ed` 的内部格式、服务端风控评分与处罚、OAID 实际上报与
保存期限。运行期解密后的代码页只存在于匿名 `r-xp` 段，需在进程存活时
只读捕获才能继续，静态文件不足以闭环。
