# 混淆与载荷还原

## 1. 范围

本文件记录 `9.05.004` 样本中可复现的混淆层：DEX 外壳控制流、H5 `.r`
资源加密、ELF 节头/符号表破坏、UPX 容器与 native 方法体还原，以及加固
壳的自保护入口。四个 native 库的压缩载荷已离线解包并逐函数分析（§7），
只有最内层加固载荷 `i12196010e51071ed` 的内部格式仍未还原（§8）。

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
| 原生 | UPX 容器 + 节头破坏（§5、§6、§7） | 可：裁剪尾部后 `upx -d` 解包，方法体逐函数还原 |
| 载荷 | `i12196010e51071ed` 私有格式（§8） | 未还原 |

即「名字层、容器层、代码层全部可静态还原，只有最内层加固载荷格式未还原」。
native 方法体不再列为边界：§7 给出可复现的裁剪配方与四个库的解包哈希，
`libndktest.so` 全部 31 个函数、`libencrypt.so` 全部 4 个 JNI 入口、
`libShanYCore.so` 的 `JNI_OnLoad` 与 `libhtsfx.so` 的壳结构都已逐函数分析。
剩余的 native 边界只有一处：壳从 `ijiami.dat` 解出的第三层代码。
`libndktest.so` 的 SM4 密钥编排与块函数都已用 unicorn 直接调用
`sms4_set_encrypt_key`（`0x3f80`）与 `sms4_encrypt`（`0x3384`）复现
GB/T 32907-2016 标准测试向量。

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
已观测事实。`UPX!` 字符串在四库中各出现 `3` 次；原样执行 `upx -d` 返回
`NotPackedException`，原因是尾部块之后被追加了节头表（§6.1），裁掉那段尾巴
后标准 `upx -d -f` 即可解包，配方与哈希见 §7.1。

### 5.1 `DT_HASH` 才是符号数的真实来源

上面那张表的「恢复表项」不是靠猜间距得来的，而是 `DT_HASH` 直接给出的：
`DT_HASH` 指向的 SysV hash 表头两个 `u32` 就是 `nbucket` 与 `nchain`，而
`nchain` 恒等于动态符号表项数。`libndktest.so` 的 `DT_HASH`（`0x190`）读出
`nbucket=37`、`nchain=53`，与按 `DT_SYMTAB`→`DT_STRTAB` 间距算出的 53 项
互相印证，而 section header 只声明 3 项。也就是说被砍掉的是**节头视图**，
不是符号表本身：按 `nchain` 遍历 `DT_SYMTAB` 即可拿到全部 53 项。

恢复出的符号带有真实的 `st_value` 与 `st_size`，例如
`AlgorithmApi_Encrypt`=`0x1028`/`0x2fc`、`_Z3RC4PhiPKhi`=`0xdc0`/`0x164`、
`sm4_crypt_cbc`=`0x2ff8`/`0x370`、
`Java_cn_com_gjzq_crypto_GjEncrypt_Encrypt`=`0x1d84`/`0x1b8`、
`sms4_set_encrypt_key`=`0x3f80`/`0xbc4`、`SBOX`=`0x57008`。这些
`st_value` 已明显超出文件长度（`0x7288`），说明它们指向的是**解包后**的
内存映像，而不是文件内偏移——这一点也是判定载荷被压缩/加密的直接证据。

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

即“先把自身搬到匿名页并置 RX，再把 64 KB 代码页解出后跳转”。仿真在目标页
映射后停住（`MAP_FIXED` 语义无法照搬），但这段 stub 不需要跑——文件本身是
标准 UPX 容器，按 §7 的裁剪配方离线执行 `upx -d` 即可取出方法体。本节记录
的 **stub 结构与系统调用顺序**仍作为“壳在运行期会做什么”的独立证据。

运行态证据与此一致：应用进程存活时 `/proc/<pid>/maps` 出现 7 个
`00:00 0` 匿名 `r-xp` 段，长度分别为 `0x3800`、`0x1000`、`0x9800`、
`0x20d000`、`0xdf000`、`0x1500`、`0x3c000` 字节，其中 `0x1000` /
`0x1500` 与 `0xfff0` / `0x15e4` 量级相符。`libShanYCore.so`、`libencrypt.so`
在 maps 中只有 `r--p` / `rw-p` 而没有 `r-xp`，其可执行代码只存在于上述匿名
段；进程退出后匿名段与 `.tcache` 一并消失，说明两者都是解包期临时产物。

### 6.1 UPX 容器与 `NotPackedException` 的真实原因

`UPX!` 不是残留字符串。`libndktest.so` 的 `0xcc0` 处是一个真实 UPX
`l_info` 头。字段语义用**同一版本 UPX（5.2.0，格式串 `UPX 6.02`）打包的
正常 `linux/amd64` 样本校准过**：正常样本 `+16` 处的值恰好等于它的原始
（未压缩）文件大小 `0x3e10 = 15888`，据此可以确认 `+16` 是
`b_info.u_len`。

```text
0xcc0  e0 74 5c c1                l_checksum = 0xc15c74e0
0xcc4  55 50 58 21                l_magic    = "UPX!"  (0x21585055)
0xcc8  c0 06 / 0d / 2a            l_lsize=0x06c0, l_version=13, l_format=42
0xccc  00 00 00 00                (对齐空档)
0xcd0  88 75 01 00                b_info.u_len        = 0x17588 = 95,624 B
0xcd4  88 75 01 00                (同值；正常样本此处为 blocksize)
0xcd8  c0 0c 00 00                (= 0xcc0，头部块长度)
0xcdc  c2 04 00 00                (过滤相关参数)
0xce0  02 00 00 00                (过滤相关参数)
0xce4  f6 fb 21 ff                (固定标记 0xff21fbf6，正常样本同位置同值)
0xce8  7f 45 4c 46                "ELF"  <-- 内嵌 ELF 头紧随其后
```

**关键量化**：`b_info.u_len = 0x17588`（95,624 B）远大于文件实际长度
`0x7288`（29,320 B），也远大于 RX 段可容纳的载荷空间
`0x43d4 - 0xd90 = 12,180 B`。同时 `SBOX` 等符号的 `st_value` 达到
`0x57008`，只有解包后才会存在。三者互相印证：**可执行代码在文件里是
压缩态，长度约 1/8**。

注意：`l_checksum=0`、尾部 `l_version=2`、`l_format=8` 这类“看着异常”的
值**本来就是正常 UPX 文件的取值**（正常样本尾部同样如此），不能当作被
篡改的证据。

容器里也保留了 UPX 自述串（`0x3e68`）：
`$Info: This file is packed with the UPX executable packer http://upx.sf.net $`
与 `$Id: UPX 6.02 Copyright (C) 1996-2025 the UPX Team. All Rights Reserved. $`。

**`NotPackedException` 由两个叠加因素造成：尾部 `b_info` 块内容错位，以及
节头表使尾部块不再与 EOF 对齐。** 四组对照实验（正常 UPX 样本）先把边界
划清：

| 实验 | 操作 | `upx -d` 结果 |
|---|---|---|
| 对照 | 原样 | `Unpacked 1 file` |
| A | 尾部 `l_checksum` 清零（长度不变） | `Unpacked 1 file` |
| B | 尾部追加 100–1200 字节零填充 | `Unpacked 1 file` |
| C | 尾部追加 `.dynsym`/`.dynstr`… 节名字符串 | `NotPackedException` |
| D | 追加 7 × 64 字节节头表并改 `e_shoff` | `NotPackedException` |
| E | 尾部 `+0x14`/`+0x18`/`+0x1c`/`+0x20` 任一被改写 | `CantUnpackException` |

由此可知：`l_checksum` 与单纯的长度变化不参与判定（A/B），但尾部 `b_info`
块**确实参与校验**（E），且尾部块必须能被从 EOF 反向定位（C/D）。

本样本两条都踩中：

```text
头部 l_info@0xcc0  +16..+32 = 0x17588, 0x17588, 0xcc0, 0x4c2, 0x2
尾部 l_info@0x7004 +16..+32 = 0xc735a5f9, 0x580, 0x1ce, 0x17588, 0xf4000052
```

尾部的 `b_info` 块与头部完全不对应（`0x17588` 被挪到了 `+28`，其余字段被
换成无关值）。裁剪点必须精确到尾部块结束处（`0x702c`）：截到 `0x7028`
（早 4 字节）仍报 `CantUnpackException: file corrupted`，截到 `0x702c` 后
`upx -d -f` 即成功——说明尾部 `b_info` 块本身是有效的，只是需要与 EOF 对齐。
把尾部 `b_info` 按头部复制过去反而报 `CantUnpackException: header corrupted 3`，
证明不该改写它，只需裁掉它之后的节头表。裁剪配方与四个库的解包哈希见 §7.1。

节头表这一侧则是**真实且自洽**的，不是伪造：文件头声明
`e_shoff=0x70c8`、`e_shnum=7`，`0x70c8` 起确实是 7 项合法节头
（`shstrtab` 在 `0x7090`，内容为
`.dynsym/.dynstr/.symtab/.strtab/.shstrtab/.dynamic`）。它之所以有害，只是
因为它被排在 UPX 尾部块（`0x7004`–`0x7028`）之后，把尾部块推离了 EOF
（`0x7288`），正好复现实验 C/D 的形态。

真正的真伪对照是：`DT_HASH` 的 `nchain=53` 说明动态符号表有 53 项，而
`.dynsym` 节头只声明 `size=0x48`（3 项）——**节头视图被截短，符号表本身
完好**。

## 7. 解包 native 方法体

§6 记录的自解密 stub 能被**离线解包**：四个库都是标准 UPX 容器，只是尾部
`b_info` 块之后被追加了真实的节头表，使尾部块不再贴住 EOF，`upx -d` 因此
报 `NotPackedException`。按「把文件截到尾部块结束处」的方式裁掉这段多余
尾巴，标准 `upx -d -f` 就能正常解包，不需要运行期 `MAP_FIXED` 映射。

### 7.1 可复现的裁剪点

裁剪公式：找到文件里**最后一个** `UPX!`（偏移 `i`），截到
`i - 4 + 0x28`（`i-4` 是 `l_info` 起点，`0x28` 是尾部块长度），再执行
`upx -d -f`。四个库的实测裁剪点：

| 库 | 打包大小 | 最后 `UPX!` | 裁剪到 | 解包后大小 | 解包后 SHA-256（前 16 位） |
|---|---:|---:|---:|---:|---|
| `libndktest.so` | `29,320` | `0x7008` | `0x702c` | `95,624` | `87d74f001ebf87de…` |
| `libencrypt.so` | `10,896` | `0x2810` | `0x2834` | `77,352` | `4142dc6342b2f095…` |
| `libShanYCore.so` | `8,840` | `0x2008` | `0x202c` | `75,456` | `88f2ac1cbda9fcc4…` |
| `libhtsfx.so` | `69,472` | `0x10cdd` | `0x10d01` | `127,520` | `16fb2b4b71c3d7ad…` |

完整哈希见 [evidence.md](evidence.md) §10.6，脚本见
`tools/upx_recover.py`（只做裁剪，解包仍由 `upx -d -f` 完成）。
四个库解包后体积都**大于**打包文件（`95,624 / 29,320 = 3.26×`、
`77,352 / 10,896 = 7.10×`、`75,456 / 8,840 = 8.54×`、
`127,520 / 69,472 = 1.84×`），与 §6.1 里 `b_info.u_len = 0x17588`
（95,624 B）的头部声明一致。

裁剪点必须精确：截到 `0x7028`（早 4 字节）会报
`CantUnpackException: file corrupted`，`0x702c` 才通过。这与 §6.1 的六组
对照实验一致——尾部 `b_info` 块确实参与校验，且必须与 EOF 对齐。

### 7.2 解包后的节结构与可解码率

解包后四个库都恢复出**完整的节头表**，并且 `libhtsfx.so` 还带有
`.symtab`/`.strtab`：

| 库 | `.text` 地址 / 大小 | 解包后线性反汇编覆盖率 | 打包时覆盖率 |
|---|---|---:|---:|
| `libndktest.so` | `0xd70` / `0x4998` | `100.0%`（31 个函数逐个 100%） | `33.0%` |
| `libencrypt.so` | `0x8d0` / `0x0bc0` | `100.0%` | `35.4%` |
| `libShanYCore.so` | `0x860` / `0x0e08` | `100.0%` | `34.8%` |
| `libhtsfx.so` | `0x40d0` / `0x11e18` | `100.0%` | `49.5%` |

即 §7 上一版统计的「三到五成字节不是合法指令」**纯粹是压缩载荷造成的**，
解包后全部是合法 AArch64 指令，`capstone` 线性扫描零空档（`libndktest.so`
唯一的空档是 `0x2f84`–`0x5708` 这段 `.bss` 前的 `0x2784` 字节常量区）。
`libndktest.so` 的 31 个有名字的函数——`AlgorithmApi_*`、`Java_..._GjEncrypt_*`、
`_Z3RC4PhiPKhi`、`sm4_*`、`sms4_*`、`CRYPTO_cbc128_*`——全部 100% 可解码，
没有一个残留不可解码字节。

### 7.3 `libndktest.so`：真实 SM4 与三段 RC4

**S-box 是标准 SM4 S-box。** `.rodata` 内 `0x5830` 起 256 字节与
GB/T 32907 的标准 SM4 S-box **逐字节相同**（代码经 `adrp x10,0x5000` +
`add x10,x10,#0x730` 再 `+0x100` 寻址，即 `0x5730+0x100 = 0x5830`）。
`strings` 在打包文件里搜不到它，是因为它在解包后才可见。

**密钥编排与标准 SM4 完全一致。** `sms4_set_encrypt_key`（`0x3f80`，
`3,012` B）把 SM4 系统参数当作立即数装入
（`0xbac6|0xa3b1<<16`、`0x3350|0x56aa<<16`、`0x9197|0x677d<<16`、
`0x22dc|0xb270<<16` → `0xa3b1bac6` / `0x56aa3350` / `0x677d9197` /
`0xb27022dc`），`CK[0] = 0x00070e15`。用 `unicorn` 直接调用该函数、
输入标准测试密钥 `0123456789abcdeffedcba9876543210`，得到的轮密钥为
`rk[0]=0xf12186f9`、`rk[31]=0x9124a012`，与标准 SM4 密钥编排**完全一致**。

**块函数端到端已复现标准测试向量。** 同一份仿真把上一步得到的 128 字节
（32×u32 LE）轮密钥直接作为 `sms4_encrypt`（`0x3384`，`3,068` B）
第三个参数（`x0`＝明文 16 B、`x1`＝输出 16 B、`x2`＝轮密钥基址）调用，
对标准明文 `0123456789abcdeffedcba9876543210` 输出
`681edf34d206965e86b3e94f536e4246`，与 GB/T 32907-2016 测试向量逐字节一致。
即 **密钥编排与块函数均等价于标准 SM4**。库内另有一条查表优化路径
`sm4_crypt_ecb`（`0x2f90`，经 `0x2c2c` 的表驱动实现），该路径依赖 `.bss`
里 `SBOX32H`/`SBOX32L`（各 `0x40000` B）两张由运行期填入的展开表；静态仿真
未填入这些表所以该路径输出不同，这不影响 `sms4_encrypt` 本身已证等价标准 SM4
的结论。

**三段 RC4 串联。** `Java_cn_com_gjzq_crypto_GjEncrypt_Encrypt`（`0x1d84`，
`440` B）的调用链完整可读：

```text
0x1d84  GetStringUTFChars(jstr)                      取 UTF-8 明文
0x1dc0  GetStringUTFChars(keyjstr)                   取 key
0x1df4  switch(w7): 1/2/3/4 分支                     选择密钥来源
0x1130  mov wN,#imm + strb wN,[sp,#off]              三条栈上密钥缓冲区（立即数）
0x11fc  _Znam + memcpy                               拷贝明文
0x121c  _Z15SwapRC4PositionPhi(buf,len)              位置置换（前置）
0x1238  _Z3RC4PhiPKhi(buf,len,key,strlen(key))       第一段 RC4
0x1244  SwapRC4Position 再次                           位置置换（后置）
0x12a0  SwapRC4Position(result,len)                  输出前再置换
0x12b4  __stack_chk_guard 校验
```

三段密钥缓冲区由 `mov wN,#imm` + `strb` 逐字节写栈，因此**在字符串表里完全
不存在**——这也是打包文件 `strings` 只找到符号名的原因：

| 缓冲区 | 长度 | 内容（含两个 8 位日期） |
|---|---:|---|
| `sp+0x70` | 15 B | `3278f6a8885a308d314398a2e03707` |
| `sp+0x80` | 23 B | `c8c33139383430383236a1783139383831313035e03707`（含 ASCII `19840826`、`19881105`） |
| `sp+0x98` | 31 B | `3443f6a8885a308d317807a2e0379832885a308de0379832317807a23443f6` |

缓冲区之间用 `0x00` 分隔，后两个以 `e0 37 07` 结尾，前两个以 `07` 结尾，
与 `strlen` 截断行为配合使用。`sm4_setkey_enc`（`0x2e30`，`12` B）只做
`str w1=1,[x0]` + 跳转，`sm4_crypt_cbc`（`0x2ff8`，`880` B）做标准
CBC 的 `eor v0.16b` + 16 字节块循环。

### 7.4 `libencrypt.so`：RC4 信封 + CRC32 校验字节

`libencrypt.so` 解包后 4 个 JNI 入口全部可读：

| 符号 | 地址 | 大小 |
|---|---|---:|
| `Java_com_tztEncrypt_enEncrypt13` | `0x968` | `968` B |
| `Java_com_tztEncrypt_deEncrypt13` | `0xd30` | `712` B |
| `Java_com_tztEncrypt_ajaxEncrypt13` | `0xff8` | `588` B |
| `Java_com_tztEncrypt_webdataEncrypt13` | `0x1244` | `588` B |
| `CRC32` | `0x90c` | `92` B |

`enEncrypt13` 的信封结构（`0x968`–`0xd2c`）：

```text
0x9d4  time() + srand() + 4 × rand()%255+1        生成 4 个随机密钥字节
0x978  从 0x14d0 取 32 字节静态种子
0xa90/0xa9c/0xaa4/0xac8  strb 覆盖种子[2],[1],[4],[7]   随机字节替换
0xb34  RC4 KSA（256 项 S 盒，keylen 次循环）
0xb94  RC4 PRGA 写入 out+7，长度 = 入参长度
0xbfc  CRC32(0, out, len+7)                       对「头部 7 字节 + 密文」求校验
0xc1c.. 写回 out[0..3] = 4 个随机字节
          out[4] = crc[0] ^ 0x74
          out[5] = 高半字节/低半字节互换后的 crc[1]
          out[6] = 互换后的 crc[2]
          out[len+7..len+9] = 3 个 CRC 派生校验字节
```

`deEncrypt13`（`0xd30`）先校验长度 ≥ 10，再按同样规则重算
`[len-3] ^ 0x74`、`[len-2] ^ 0x7a` 与头部对应的半字节互换值，任一处不等
即返回空——即这是一个**带完整性校验的自定义 RC4 传输信封**，用于 H5 与
native 之间的参数/响应加密。

`CRC32`（`0x90c`）是字节级查表实现，表在 `.data` 的 `tabKey`
（`0x12010`，`2,048` B）。它的布局不是 256 个连续 `u32`，而是**每个 `u32`
占 8 字节**（偶数槽位是标准 CRC32 表值，奇数槽位为 0），与反汇编里的
`ldr x3,[x3,x1,lsl #3]`（8 字节步长）一致；偶槽位取值与多项式
`0xEDB88320` 生成的标准 CRC32 表**逐项相同**。

`.rodata` 还留有另一组 32 字符十六进制常量
（`0x1528`：`C1490CBF3F5267035D6B3B2D232A1A27`）与短串 `19781117`
（`0x1550`），以及两条带日期的日志串
`Java_com_tztEncrypt_{en,de}Encrypt13[2013-12-13 001]`。

### 7.5 `libShanYCore.so`：反调试 + 运行期拼装字符串 + Wi-Fi 反射

`JNI_OnLoad`（`0x89c`，`484` B）的完整语义：

```text
0x8c4-0x8dc  ptrace(PTRACE_TRACEME=0, 0, 0, 0)      反调试（返回值未检查）
0x8f4        __android_log_print(3, "VVV", "enter jni_onload")
0x914        (*env)->GetEnv(env, &vm, 0x10006)      要求 JNI 1.6
             返回非 0 时 w19 = -1 直接 return，即加载失败
0x94c        malloc(2) + strh #0x75                 拼出单字符 "n"
0x960        decode(x0)  -> 见下
0x98c        malloc(0x22) + 16 B 常量 + 'h'          拼类名片段
0x9b8        decode(x0)
0x9d4        类名 = "android/content/Context"（decode 后）
             FindClass / GetStaticFieldID 取 WIFI_SERVICE 常量
0xa1c        通过 decode 出的 "com/chuanglan/shanyan_sdk/utils/..."
             反射调用 getSystemService("wifi")
0xa34        CallObjectMethod -> WifiManager
0xa58        CallVoidMethod   -> getConnectionInfo / getNetworkId
```

**字符串解码器**在 `0xa80`（`312` B），逻辑是逐字节
`if (b == ';' || b == 'd') keep else b ^= 0x1b`（`0xaf4`–`0xb18`，
`w11 = 0x1b`，`cmp w12,#0x3b` / `cmp w12,#0x64` 两个原样跳过条件）。
`;`(0x3b) 与 `d`(0x64) 恰好是 XOR 0x1b 自身的不动点，所以这两个字符必须
显式跳过——这是该解码器的辨识特征。

用它解 `.rodata 0x16a6` 起 194 字节的混淆块，得到：

```text
WIFI_SERVICE
android/net/wifi/WifiManager
disconnect
com/chuanglan/shanyan_sdk/utils/s
getSystemService
reconnect
disableNetwork
enableNetwork
getConnectionInfo
getNetworkId
()Landroid/net/wifi/WifiInfo;
```

以及 `0x16d9`/`0x16e9` 两段 16 字节拼接出的
`com/chuanglan/shanyan_sdk/utils/`。即该库在 `JNI_OnLoad` 里**反射拿到
`WifiManager`，并静态持有 `disableNetwork` / `enableNetwork` /
`disconnect` / `reconnect` 四个方法名**，同时读取
`getConnectionInfo` / `getNetworkId` 做 Wi-Fi 状态采集。这解释了
[risk.md](risk.md) 里「`libShanYCore.so` 只证实导入 `ptrace`」之外的另一半
能力面：**它不只是反调试，还具备关闭/重连 Wi-Fi 的反射调用面**——客户端
静态证据只能证明这些方法名与反射路径存在，不能证明实际调用时机。

### 7.6 `libhtsfx.so`：梆梆（ijiami）加固壳

`libhtsfx.so` 解包后 `432` 个符号（其中 `312` 个在本库定义，其余为导入），
是四个库里唯一保留 `.symtab`/`.strtab` 的。它的职责是**壳**而不是业务加密：

- `ijiami`（`0xf2d4`）本身只是单条 `ret`，真正的入口在 `JNI_OnLoad`
  （`0xf404`，`92` B）与它尾跳的 `0x13364`；
- `JNI_OnLoad` 与 `0x13364`、`0x13468` 里充满**不透明谓词**：读 `.data`
  里两个 `u32` 全局（`x.13`=`0x1b780`、`x.15`=`0xf0003`），做
  `(v-1)*v & 1`、`v<10`、`v<<2 < 0x196`、`(a^b)>0x2b` 之类的恒真组合，
  再 `cbz w8, <自身地址>` 形成「恒不跳转」的自旋陷阱。用这两个全局值实算
  得到 `w8 = 1`，即正常路径继续；若被静态改写则原地死循环；
- 导入 `fake_dlopen` / `fake_dlsym` / `slSubstrate`，以及
  `/proc/self/maps`、`/proc/%d/maps`、`/system/lib64/%s`、
  `/apex/com.android.runtime/lib64/%s`、`ro.build.version.sdk` 等字符串，
  即通过解析 maps 自建动态链接视图（对抗 hook 与 dlopen 监控）；
- 导入 `inflate` / `inflateInit2_` / `inflateEnd`（zlib）与
  `AAssetManager_open` / `AAsset_read` / `AAsset_getLength`，即从 assets
  读加密载荷再用 zlib 解压——对应 `assets/ijiami.dat`（见 §8）；
- 导入 `mmap` / `mprotect` / `open` / `read` / `lseek`，即解密后置可执行。

这些与本报告 §6 记录的「自解密 stub + 匿名 `r-xp` 段」互相印证：**壳负责
把 `ijiami.dat` 解出来并映射执行，业务库（`libndktest`/`libencrypt`/
`libShanYCore`）再提供密码学与风控能力**。

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
方法、六份自解密 stub 的地址/结构/系统调用顺序、**四个 native 库的离线解包
与逐函数分析**（`libndktest.so` 全部 31 个函数 100% 可解码，SM4 密钥编排与
块函数均复现 GB/T 32907-2016 标准测试向量，三段 RC4 串联链、三条立即数密钥
缓冲区、`libencrypt.so` 的 RC4+CRC32 信封、`libShanYCore.so` 的 `ptrace`
反调试与 Wi-Fi 反射面、`libhtsfx.so` 的梆梆壳结构与不透明谓词）、JNI 与
加密导入面、风险中心与适当性接口、本地信号缓存。

未还原：`libencrypt.so` 随机密钥字节与 `time()`/`rand()` 的具体关联、
加固载荷 `i12196010e51071ed` 的内部格式、服务端风控评分与处罚、OAID 实际
上报与保存期限。运行期解密后的第三层代码页（壳从 `ijiami.dat` 解出的业务
DEX/so）只存在于匿名 `r-xp` 段，静态文件不足以闭环，需在进程存活时只读捕获
才能继续。
