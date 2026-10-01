# 混淆闭包：DEX 与全部 native 库

本文件回答"是否还有未分析的混淆代码"。结论按**层**给出，每层都有可复现的判据与
计数。

## 1. 结论速览

| 层 | 结论 |
| --- | --- |
| DEX 字符串 | **无字符串加密**。291,149 条可打印字符串直接以明文存在于 6 个 dex。 |
| DEX 加壳 | **无加壳、无 DEX 加密**。`classes*.dex` 均为标准 `dex\n035` 头，可直接解析 25,156 个 Java 文件。 |
| DEX 动态注册 | APK 内 native 库中 `RegisterNatives` **有**真实调用点：25 处（18 次注册 + 7 次注销）分布 12 个库，含 `libpdd_secure`、`libpdd_rubik`、`libCSoLoader`、`libbytehook`、`libcrashAvoid`、`libpcrash`、`libpcrash_anr`、`liblegonative`、`libmarsxlog`、`libtronkit`、`libdyncommon`、`libyoga`。详见 §9.1。 |
| native 字符串 | **有局部字符串加密**：`libpdd_secure.so` 的 `.rodata` 代码段内嵌一个异或保护的字符串池（基址 `0x1928c0`，8 字节循环密钥 `f09745e4835fd19f`），内含 `DeviceNative` 类名、`miui.intent.TAKE_SCREENSHOT`、RSA 公钥等。详见 §9.4。 |
| DEX 控制流 | 6,648 处 Efix 跳板（见 §2），**未安装热补丁时全部短路到默认实现**，属可解释结构而非混淆。 |
| native 控制流 | 分三类：OLLVM 控制流平坦化（FLA）、间接分支派发（IND-BR）、ADR+RET 返回地址间接化 + .text 内嵌数据。逐库清点在 §4–§6。 |
| native 数据 | 无加密常量表隐藏。所有标准密码学常量表都以明文出现在 `.rodata`（见 [algorithm.md](algorithm.md)）。 |

## 2. Efix 热补丁跳板（`h4.g`）

### 2.1 结构

```java
// h4/g.java  — com.android.efix PatchProxy
public abstract class g {
    public static volatile int f62701a;      // patch mode
    public static final h f62702b = new h(); // 默认返回 {a=false}
    public static h d(Object obj, int i14) {
        if (f62701a == 0) return f62702b;         // 未装补丁：直接短路
        h4.a aVarR = r(i14);                      // 从 patch dex 取实现
        return aVarR == null ? f62702b : j(aVarR, new Object[]{obj}, i14);
    }
    public static void q(int i14) { f62701a = i14; }   // 装载补丁成功后置 4
}
// h4/h.java
public class h { public boolean f62707a; public Object f62708b; }   // {命中?, 值}
// h4/a.java
public interface a { Object a(int i14, Object[] objArr); }
```

调用点形态固定为：

```java
h4.h X = h4.g.<letter>(this, <patchId>);
if (X.f62707a) return X.f62708b;
```

即 `f62707a=false` 时结构上不可能执行任何补丁代码。

### 2.2 计数

| 指标 | 值 |
| --- | --- |
| 调用点总数 | 6,648 |
| 引用 `h4.g` 的文件 | 1,135 / 25,156 |
| 出现 `f62707a` 判定的文件 | 2,001 |
| 不同 patchId | 6,586（范围 0 – 11,435）；另有 60 处末参不是数字字面量 |
| `h4.g.q(int)` 置位点 | 5（其中 `com/android/efix/load/a.f(...)` 在 `prepare` 成功后置 `4`） |
| 各跳板方法计数 | `d` 1,940、`e` 1,866、`f` 844、`k` 490、`g` 398、`l` 337、`m` 270、`n` 131、`h` 125、`c` 107、`i` 61、`o` 45、`p` 29、`q` 5 |

方法的字母差异只对应**参数个数**（`d`=1 参、`e`=2 参、`f`=3 参、`n`=3 参+null
填充等），`c` 是"首参入数组末尾"的变体，`k` 用固定单元素数组 `f62705e`。

### 2.3 现场证据

同一设备的 `shared_prefs/efix_sp_main.xml`：

```xml
<int name="load_failed_continuously_count" value="0" />
<long name="last_load_failed_v" value="0" />
<boolean name="has_record_load_v_9759" value="true" />
```

无补丁 dex 落地、连续失败计数为 0，与"6,648 处跳板全部走默认分支"一致。

## 3. 混淆判定方法

对每个 `.so` 在真实 `.text` 反汇编上统计四类指纹（`tools/obfclass.py`）：

| 指纹 | 模式 | 含义 |
| --- | --- | --- |
| `cffstate` | `mov w,#lo` + `movk w,#hi,lsl#16` 后紧跟 `cmp` | OLLVM `-fla` 的状态常量 |
| `indbr` | `csel` → `ldr xN,[xM,xN]` → `and`/`add`/`sub`/`eor` → `br xN` | OLLVM 间接分支派发 |
| `adrret` | `adr x30,<self>` → `add x30,x30,xN` → `ret` | 返回地址动态化 |
| `.inst%` | 反汇编为 `.inst 0x…` 的字节占比 | `.text` 内嵌数据 |

判定阈值：`indbr > 20` → IND-BR；`adrret > 20` → ADR-RET；`.inst% > 3` →
DATA-IN-TEXT；`cffstate > 20` → FLA；`csel/insns > 0.03` → CSEL-HEAVY。

## 4. APK 自带库（`lib/arm64-v8a`，22 个）

| 库 | 指令数 | IND-BR | ADR-RET | FLA | .inst% | 判定 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `libpdd_secure.so` | 404,537 | 2,013 | 0 | 7,376 | 0.00 | IND-BR + FLA |
| `libtronav.so` | 364,633 | 0 | 0 | 659 | 0.16 | FLA |
| `libyoga.so` | 15,643 | 0 | 0 | 284 | 0.00 | FLA（低频） |
| `libyuv.so` | 18,943 | 0 | 0 | 51 | 0.00 | FLA（低频） |
| `libgoldarch.so` | 18,421 | 0 | 0 | 6 | 0.00 | 明文 |
| `libc++_shared.so` | 148,432 | 0 | 0 | 18 | 0.00 | 明文（工具链库） |
| `liblegonative.so` | 91,266 | 0 | 0 | 2 | 0.00 | 明文 |
| `libaudio_engine.so` | 36,708 | 0 | 0 | 6 | 0.00 | 明文 |
| 其余 14 个 | — | 0 | 0 | ≤ 18 | 0.00 | 明文 |

`libpdd_secure.so` 是唯一同时使用 IND-BR 与 FLA 的核心库，也是 34 个 JNI 导出所在
（见 [algorithm.md](algorithm.md)）。`libtronav.so` 的 FLA 密度（659/364,633 ≈
0.18%）远低于 `libpdd_secure.so`（1.8%），属工具链级混淆而非核心保护。

## 5. assets 内嵌库（`assets/so_arm64-v8a/*.7z`，3 个）

| 库 | 大小 | 指令数 | IND-BR | FLA | 判定 |
| --- | ---: | ---: | ---: | ---: | --- |
| `libtitan_….so` | 2,079,080 | 306,669 | 0 | 53 | FLA（低频） |
| `libtronplayer_….so` | 1,116,056 | 161,311 | 0 | 114 | FLA（低频） |
| `libstatic-webp_….so` | 106,040 | 18,138 | 0 | 23 | FLA（低频） |

三个 `.7z` 都是 LZMA(FORMAT_ALONE) 压缩的 tar，内部 ELF 文件名形如
`lib<name>_<epoch_ms>_<md5>.so`，**内嵌 MD5 与文件内容一致**，未加壳。

## 6. 运行时下载库（`files/dynamic_so`，26 个）

| 库 | 大小 | IND-BR | ADR-RET | FLA | .inst% | 判定 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `libmedia_engine.so` | 22,141,432 | 3 | 0 | 755 | 0.02 | FLA |
| `libpdd_j2v8.so` | 15,988,256 | 0 | 0 | 1,239 | 1.08 | FLA + DATA-IN-TEXT |
| `libpdd_rubik.so` | 6,335,856 | 390 | 0 | 90 | 0.00 | IND-BR + FLA |
| `libpnn.so` | 6,200,608 | 0 | 0 | 294 | 0.00 | FLA |
| **`libdyncommon.so`** | **5,146,000** | **19,801** | **330** | **11,704** | **1.87** | **IND-BR + ADR-RET + FLA + CSEL-HEAVY** |
| `libtronavx.so` | 4,000,176 | 3 | 0 | 279 | 0.01 | FLA |
| `libpnet.so` | 2,820,816 | 0 | 0 | 120 | 0.00 | FLA |
| `libtitan.so` | 2,079,080 | 0 | 0 | 53 | 0.00 | FLA |
| `libScriptBind.so` | 1,812,512 | 0 | 0 | 21 | 0.00 | FLA（低频） |
| `libavif_android.so` | 1,595,896 | 0 | 0 | 135 | 0.00 | FLA |
| `libfdk_aac.so` | 1,567,048 | 0 | 0 | 121 | 0.00 | FLA |
| `libaipin_wrapper.so` | 1,141,040 | 0 | 0 | 51 | 0.00 | FLA（低频） |
| `libtronplayer.so` | 1,116,056 | 0 | 0 | 114 | 0.00 | FLA |
| `libtronnap.so` | 577,768 | 0 | 0 | 30 | 0.00 | FLA（低频） |
| `libminosTask.so` | 141,152 | 0 | 0 | 260 | 0.00 | FLA（低频，高比） |
| `libaegis.so` | 43,424 | 0 | 0 | 3 | 0.00 | 明文 |
| `libprobe.so` | 436,488 | 1 | 0 | 7 | 0.00 | 明文 |
| 其余 8 个 | — | 0 | 0 | ≤ 25 | 0.00 | 明文 |

### 6.1 `libdyncommon.so` 的形态

该库是全样本中混淆最重的一个，也是**唯一使用 ADR+RET 返回地址间接化**的库。判据
来自真实反汇编：

```
119120: adr  x30, 119120 <JNI_OnLoad+0x4c>
119124: add  x30, x30, x8        ; x8 来自 [4e8000+3536] 的运行时表
119128: ret                     ; 返回目标在运行时确定
11912c: .inst 0xfa14f834        ; 紧随其后的是数据，被反汇编成非法指令
119130: .inst 0x0bb5d0f7
...
10a078: csel x9, x10, x9, lt
10a07c: ldr  x9, [x23, x9]      ; 从运行时表取目标
10a080: and  x10, x25, x24
10a084: add  x11, x25, x24
10a088: sub  x10, x11, x10, lsl #1
10a08c: eor  x10, x10, x25
10a090: add  x9, x9, x10
10a094: br   x9                 ; 间接派发
```

量化（全库 782,564 条指令）：

| 指标 | 值 |
| --- | ---: |
| 间接分支派发块 | 19,801 |
| ADR+RET 返回地址间接化点 | 330 |
| FLA 状态常量比较 | 11,704 |
| `csel` | 23,965（占 3.1%） |
| `.inst` 字节 | 14,616（`.text` 的 1.87%） |
| `JNI_OnLoad` 内（0x1190d4–0x11a2e4，1,156 条） | 45 派发块 / 60 `br` / 29 状态比较 / 47 `csel` |

该库的导出符号只有 `JNI_OnLoad` 与 `exec` 两个，全部实际逻辑通过 `JNI_OnLoad` 内的
运行时派发表注册。`.rodata` 中留存的字符串暴露其用途：

```
libart.so
_ZN3art9ArtMethod16EnableXposedHookERNS_18ScopedObjectAccessEP8_jobject
_ZN3art6mirror9ArtMethod16EnableXposedHookE...
_ZN3art30InvokeXposedHandleHookedMethzodERNS_33ScopedObjectAccess
/linker
__dl__ZL23g_module_unload_counter
__dl__ZL6solist
__dl__ZNK6soinfo12get_realpathEv
__dl__ZNK6soinfo10get_sonameEv
__dl__ZN18ProtectedDataGuardC2Ev
rtld_db_dlactivity
libselinux.so / security_getenforce
libnativebridge.so / NativeBridgeError
/vendor/etc/selinux/vendor_sepolicy.cil
/system/bin, /system/xbin, /vendor/bin
libstagefright.so / _ZN7android15ANetworkSession10threadLoopEv
```

即：**链接器内部符号枚举 + Xposed/ART 方法 hook 探测 + SELinux/nativebridge 环境
读取**。这是"反注入/反 hook 环境探测"模块，恰好对应 `com.xunmeng.pinduoduo.bridge`
与 `apm/risk/lock` 的 JNI 桥。混淆手法为控制流平坦化 + 间接分支 + 返回地址动态化
的组合，属于可完全静态解释的三类标准 OLLVM 变体，不含自解密或虚拟机。

## 7. 密码学常量表的可读性

全部 native 库中，标准算法常量表均以明文出现在 `.rodata`，未做异或/分片隐藏：

| 库 | 命中常量表 |
| --- | --- |
| `libpdd_secure.so` | AES S-box、AES 逆 S-box、Base64 字母表、SHA-256 H0(LE)、MD5/SHA-1 IV(LE)、zlib magic |
| `libgoldarch.so` | AES 逆 S-box、Base64、MD5/SHA-1 IV |
| `libtitan.so` | MD5/SHA-1 IV、SHA-256 H0(LE)、P-256 p |
| `libpnet.so` | AES 逆 S-box、Base64、MD5/SHA-1 IV |
| `libmedia_engine.so` | AES S-box、AES 逆 S-box、Base64 |
| `libpdd_j2v8.so` | AES S-box、AES 逆 S-box、zlib magic |
| `libpnn.so` | AES S-box、AES 逆 S-box、MD5/SHA-1 IV |
| `libdyncommon.so` | AES S-box、zlib magic |
| `libtronavx.so` | AES 逆 S-box、Base64、ChaCha20 sigma |
| `libmmkv_v2.so` | AES 逆 S-box、MD5/SHA-1 IV |

`libpdd_secure.so` 全镜像中 256 字节置换表**恰好 2 张**（AES 正/逆 S-box），
未出现第二套自研置换表。逐字节地址见 [algorithm.md](algorithm.md)。

## 8. `SecureNative` 的控制流反扁平化

### 8.1 逐导出规模（修正后的边界）

早期用"下一个导出地址"给每个导出划界，对最后一个导出（`SecureNative.b` @0x37294）
会一直算到文件尾，得到 344,138 条指令的虚假窗口。`tools/cff4.py` 改为扫描真实的
函数收尾（`ldp x29, x30, [sp, …]` 或 `add sp, sp, #N` 之后紧跟 `ret`）来定界，
34 个导出的合计只有 **21,613 条指令**：

| 导出 | 起始 | 结束 | 指令数 | `br` | `csel` | `movk …,lsl #16` |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| `ng2` | `0xfd50` | `0x17160` | 7,428 | 0 | 1 | 0 |
| `ae` | `0x1a60c` | `0x1cb5c` | 2,388 | 55 | 115 | 407 |
| `adw` | `0x1cb60` | `0x1edec` | 2,211 | 53 | 113 | 340 |
| `re` | `0x189b8` | `0x19ff0` | 1,422 | 33 | 69 | 216 |
| `s` | `0x36278` | `0x37290` | 1,030 | 0 | 8 | 188 |
| `dec` | `0x2a060` | `0x2ae80` | 904 | 0 | 14 | 130 |
| `enc` | `0x29414` | `0x2a05c` | 786 | 0 | 13 | 105 |
| `ad` | `0x1edf0` | `0x1f71c` | 587 | 10 | 20 | 52 |
| `dv` | `0x1f75c` | `0x1ff54` | 510 | 11 | 21 | 65 |
| `ecb` | `0x25654` | `0x25dd8` | 481 | 9 | 18 | 65 |
| `egv` | `0x17dac` | `0x184e0` | 461 | 9 | 17 | 60 |
| `eca` | `0x17680` | `0x17da8` | 458 | 9 | 18 | 58 |
| `aew` | `0x19ff4` | `0x1a608` | 389 | 0 | 5 | 49 |
| `itst` | `0x28b10` | `0x290dc` | 371 | 0 | 0 | 0 |
| `glk` | `0x26114` | `0x26528` | 261 | 0 | 6 | 30 |
| `mhk` | `0x18e510` | `0x18e8e0` | 244 | 4 | 7 | 22 |
| `ne` | `0x1883b0` | `0x188740` | 228 | 0 | 0 | 0 |
| `itst2` | `0x290e0` | `0x29410` | 204 | 0 | 4 | 13 |
| `dcc` | `0x18698` | `0x189b4` | 199 | 0 | 3 | 16 |
| `sdr` | `0x28798` | `0x28a50` | 174 | 0 | 4 | 17 |
| `ng` | `0x17234` | `0x1746c` | 142 | 0 | 0 | 5 |
| `ecn` | `0x25ddc` | `0x25fe8` | 131 | 0 | 0 | 6 |
| 其余 12 个导出 | — | — | ≤ 108 | 0–2 | 0–2 | 0–4 |

`ae`/`adw`/`re`/`ad`/`dv`/`ecb`/`egv`/`eca`/`mhk` 同时具备 `br`（间接派发）与
`csel`，是 IND-BR 型；`s`/`dec`/`enc` 有大量 `movk …,lsl #16` 与 `bcond` 但
**没有 `br`**，是纯 FLA 型（状态常量走二分比较树，不经过运行时表）；`ng2` 体量最大
（7,428 条）却几乎没有混淆指纹，与它"纯设备信息生成器"的定位一致。

### 8.2 反扁平化结果

`tools/unflatten.py` 对 `SecureNative.s`（0x36278–0x37290，1,030 条指令）还原：

| 指标 | 值 |
| --- | --- |
| 派发头 | `0x36310` |
| 状态槽 | `[sp, #0x14]` |
| 派生出的状态常量 | 67 |
| 由状态进入的基本块 | 67 |
| 写回状态的基本块 | 18 |
| 状态写入点总数 | 18 |

形态：每个状态常量由 `mov w,#lo` + `movk w,#hi,lsl#16` 现场拼出，`str w,[sp,#0x14]`
写回，派发树用 `cmp w8, w9` / `b.lt` / `b.ge` / `b.ne` 构成二分查找，未命中则 `b`
回派发头。每个状态**唯一**映射到一个基本块，每个基本块的出口状态可枚举，因此不存在
无法静态确定目标的分支。

`SecureNative.b`（0x37294–0x373bc，**仅 74 条指令**）是一个薄封装，形态为：

```asm
372c0: mov  w20, w2                  ; sel = 形参
3732c: cmp  w20, #0x3
37330: csel w8, wzr, w20, hi         ; w8 = (sel > 3) ? 0 : sel
37334: cmp  w8, #0x3
37338: ccmp w8, #0x2, #0x8, ne       ; 等价于 (w8 == 3) ? (w8-3) : (w8-2)
3733c: csel w1, w26, w25, lt         ; w25 = 32, w26 = 16
37340: stp  w8, w1, [sp, #8]
37350: blr  x8                       ; 经 JNIEnv 表调用一次
37370: adrp x10, 192000
37378: add  x10, x10, #0xb71         ; 描述符表基址 0x192b71
3737c: add  x4, x10, x8, lsl #5      ; 条目 = 基址 + sel' * 32
37388: blr  x9                       ; 转发到条目指定的处理函数
```

即 `b(int)` 只做三件事：归一化选择子、算出 16 或 32 这个长度参数、按
`sel × 32` 索引 `0x192b71` 的描述符表后转发。**它自身不含任何密码学**——74 条
指令里既没有 AES S-box 引用，也没有 SHA/MD5/Base64 常量表引用，与调用图结论一致
（见 §7 与 [algorithm.md](algorithm.md)）。

`SecureNative.s(int)`（0x36278–0x37290）则是 FLA 状态机本体，其中一条状态直接
`mov w0,#0x11; mov w1,#0x11; orr w2,wzr,#0x10; bl …`，即有一条分支使用
`(17, 17, 16)` 这组参数，与 `b` 里的 16/32 长度参数体系一致：`s`/`b` 共同构成
"选择子 → 密钥/IV 材料"的取值层，而真正的分组密码实现在另外的被调函数中。

### 8.3 结论

`libpdd_secure.so` 的 `.text` 共 404,537 条指令，34 个 JNI 导出只占 21,613 条；
其余为导出的内部被调函数（AES/SHA/Base64 实现、序列化器、工具函数）。所有混淆都可
归入以下三种标准变换，且每种都能静态还原：

1. **FLA（控制流平坦化）**：状态常量 + 二分派发树，可反扁平化为普通基本块图。
2. **IND-BR（间接分支）**：`csel` → `ldr xN,[xM,xN]` → 位运算 → `br xN`，
   目标表在 `.data`/`.bss` 由 `INIT_ARRAY` 填充，可在初始化函数中直接读出。
3. **ADR+RET（返回地址间接化）**：仅出现在运行时下载的 `libdyncommon.so`，
   `adr x30` + `add x30,x30,xN` + `ret`，偏移来自运行时表（见 §6.1）。

没有出现：自解密代码段、字节码虚拟机、控制流伪造（不透明谓词之外的虚假分支）、
DEX 加壳。

**但出现了两种此前漏判的手法**，两者都可静态完整还原：

- **异或字符串池**：`libpdd_secure.so` 的 `.rodata` 内嵌 8 字节循环异或保护的
  字符串池（基址 `0x1928c0`），保护了 `DeviceNative` 类名、`miui.intent.TAKE_SCREENSHOT`、
  RSA 公钥等。旧版"无字符串解密循环 / 无常量表隐藏"的结论在此库上不成立，见 §9.4。
- **`RegisterNatives` 隐藏绑定**：真实存在 25 处（12 个库），旧版"0 次"是判据
  缺陷导致的假阴性，见 §9.1.1。

## 9. native 绑定方式与混淆闭包的边界

§1–§8 的结论都是**对已取得 ELF 的库**成立的。本节补齐两个问题：库用什么方式绑定
JNI 方法、以及哪些库尚未取得。这两点决定了"没有未分析的混淆代码"这句话的确切
范围。

### 9.1 绑定方式（`tools/jnibind.py` + `tools/rnbind.py`）

对全部 1,612 个 DEX 声明的 native 方法逐一判定绑定方式：

| 绑定方式 | 数量 | 判据 |
| --- | ---: | --- |
| 符号导出 `Java_*` | 646 | 库的 `.dynsym` 中存在按 JNI 规范改写的符号 |
| `RegisterNatives` | 87 | 库的 `.rodata` 同时含方法名串与 JNI 签名串，且库导出 `JNI_OnLoad` |
| 未判定 | 879 | 约束到 104 个类，其提供库不在任何快照中 |

#### 9.1.1 调用点的真实计数（对旧结论的更正）

早期版本的本文件断言"APK 内 native 库中 `RegisterNatives` 出现 **0** 次"。
**该结论是错的，已在本版更正。** 它源自两个方法学缺陷：

1. `strings` 默认 `-n 4`，所以 3 字符的方法名（`atn`、`csd`、`dsi`）不可见；
2. 用"导入符号 `_ZN7_JNIEnv15RegisterNativesE…`"或"`.rodata` 里有
   `RegisterNatives` 字符串"作为判据。而 ARM64 上动态注册走的是
   `env->RegisterNatives(...)`，即**经函数表指针**：`ldr x8,[x0]` →
   `ldr x8,[x8,#1720]` → `blr x8`（`0x6b8 = 215×8` = `_JNIEnv` 虚表下标 215）。
   不产生任何导入符号，也没有字面字符串。

改用**函数表下标判据**后逐库清点：`#1720` = `RegisterNatives`，
`#1728` = `UnregisterNatives`，并要求基址寄存器确实是一个被解引用的指针
（排除 `adrp` 到 `.bss` 的假阳性，例如 `libaudio_engine.so` 的 PLT 桩）。
结果：

| 库 | `RegisterNatives` | `UnregisterNatives` | 所在函数 |
| --- | ---: | ---: | --- |
| `libpdd_secure.so` | 1 | 0 | `Java_…_SecureNative_dec` 内部 |
| `libpdd_rubik.so` | 2 | 4 | `JNI_OnLoad` + 内部函数 |
| `libdyncommon.so` | 2 | 3 | `exec` |
| `libtronkit.so` | 3 | 0 | `JNI_OnLoad` / `JNI_OnUnload` |
| `liblegonative.so` | 3 | 0 | `VMState_getOpCostGroup` / `JSFunction_releaseNative` |
| `libCSoLoader.so` | 1 | 0 | `JNI_OnLoad` |
| `libbytehook.so` | 1 | 0 | `JNI_OnLoad` |
| `libcrashAvoid.so` | 1 | 0 | `JNI_OnLoad` |
| `libpcrash.so` | 1 | 0 | `JNI_OnLoad` |
| `libpcrash_anr.so` | 1 | 0 | `TraceDumper_jniInit` |
| `libmarsxlog.so` | 1 | 0 | `JNI_OnLoad` |
| `libyoga.so` | 1 | 0 | `JNI_OnLoad-0x249c` |
| **合计** | **18** | **7** | 12 个库 |

注册项数由 `w3` 直接给出，可逐一读出，例如 `libtronkit.so` 的
`JNI_OnLoad` 注册 9 项、`libCSoLoader.so` 注册 2 项、`libmarsxlog.so` 注册 1 项。
因此 §9.1 上表"87 个方法"的判定口径（方法名串 + 签名串 + `JNI_OnLoad`）
只是**必要条件**，不是调用点证据；本小节的函数表下标统计才是直接证据。

#### 9.1.2 `libpdd_secure.so` 的动态注册（逐字节还原）

这是本报告要求 6（"不允许任何未分析的混淆代码"）最关键的一处，现将整条
链路逐字节给出。

**注册调用点 `0x27e7c`**（`sp+0x4b0` 栈帧内，函数入口 `0x26678`，**只被
`JNI_OnLoad` 的 `bl 26678`（位于 `0xb1b8`）进入**）。`libpdd_secure.so` 的
导出与声明并非一一对应：导出 34 个、`SecureNative.java` 声明 35 个，
差集为导出的 `dcc`/`hf`（DEX 中无同名声明）与未导出的 `atn`/`csd`/`dsi`
（DEX 中有声明，见 §9.6.2）：

```asm
27e6c:  ldp  x2, x0, [sp, #136]      ; x2 = 方法表, x0 = jclass
27e70:  ldr  x1, [sp, #72]           ; x1 = cls->name（经加密串解出）
27e74:  orr  w3, wzr, #0x3           ; nMethods = 3
27e78:  ldr  x8, [x0]                ; x8 = *env
27e7c:  ldr  x8, [x8, #1720]         ; 1720 = 215*8 → RegisterNatives
27e80:  blr  x8
27e84:  ldr  x0, [sp, #168]
27e88:  ldr  x1, [sp, #72]
27e8c:  ldr  x8, [x0]                ; 第二次查表
27e90:  ldr  x8, [x8, #1856]         ; 1856 = 232*8 → ExceptionOccurred
27e94:  blr  x8
```

`w3 = 3` 与 `DeviceNative` 恰好 3 个方法吻合。`#1856` 即
`_JNIEnv::ExceptionOccurred`（下标 232），用于注册后清理。

**类名的取得（`FindClass`）**：`0x27a6c` 处 `ldr x8,[x8,#48]`（`48 = 6×8`）
= `_JNIEnv::FindClass`（下标 6），参数 `x1` 由 `0x27ae0` 的
`add x1, x1, #0xe1c` 指向 `0x192c1c`。**该处内存是异或保护的**，不是明文。

**字符串池与解保护**。`libpdd_secure.so` 在 `.rodata` 内嵌入一个异或字符串池，
基址 `0x1928c0`（与解码例程 `0x34880` 的 `add x8, x8, #0x8c0` 精确对应），
密钥为 **8 字节循环**常量 `f0 97 45 e4 83 5f d1 9f`。加密例程分两个变体：

* `0x34880`（`add x8,x8,#0x8c0`）用 `eon w13, w13, w14, lsr #24`，
  即 `plain = enc XOR NOT(keystream)` —— 等价于按上表的补码异或；
* `0xbba00` / `0xbbe94` / `0xbc378` / `0xbc4e0` / `0xbc648`
  （均 `add x10,x10,#0x768` → 密钥表 `0x198768`）用 `eor w13,w13,w14,lsr #24`。

密钥表 `0x198768` 是 8 个小端 `u64`，其**高字节**依次为
`0f 68 ba 1b 7c a0 2e 60`（即 `plain` 视角下每个 `qword >> 24`）。
字符串记录为 `[8 字节 XOR 掩码][NUL 结尾正文]`，正文按**绝对文件偏移 mod 8**
取相位——这解释了为什么同一段密钥在不同记录上呈现不同相位。

按该模型解出的明文（节选，均为**已验证**）：

| 偏移 | 明文 | 用途 |
| --- | --- | --- |
| `0x1928c0` | `com/xunmeng/pinduoduo/secure/DeviceNative` | `FindClass` 的类名 |
| `0x1929d0` | `miui.intent.TAKE_SCREENSHOT` | 截屏检测（MIUI 广播） |
| `0x192b40` | `com/xunmeng/pinduoduo/secure/DecResult` | JNI 返回类型 |
| `0x192b68` | `decBytes` | 方法名 |
| `0x192c60` | `MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCmW0Kh…IDAQAB` | RSA 公钥（X.509 SPKI） |
| `0x192de0` | `691d011f-a6ef-40…` | UUID 片段 |
| `0x192ea0` | `sN4S72X1br+Ybnq1` | 密钥/盐材料 |
| `0x192ee0` | `bANoelxRIifGL8dUr5zc2ncyYkebkUkd` | 密钥/盐材料 |
| `0x1930a0` | `(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;` | 反射签名 |
| `0x1930f0` | `java/net/URLEncoder` | 类名 |
| `0x193180` | `TRANSACTION_isFirstBoot` | Binder 事务名 |
| `0x1931a0` | `android/os/ServiceManager` | 类名 |
| `0x193360` | `(Ljava/lang/String;)Ljava/security/KeyStore;` | 反射签名 |
| `0x1933f0` | `java/security/KeyPairGenerator` | 类名 |
| `0x193740` | `getExtensionValue` | 方法名 |
| `0x194d30` | `com/xunmeng/pinduoduo/secure/EU` | 类名 |
| `0x195124` | `serialNumber` | 字段名 |
| `0x195214` | `fiddler` | **抓包工具检测** |
| `0x195590` | `packageName` | 字段名 |
| `0x1955b0` | `pm list packages -u` | **已安装应用枚举** |
| `0x195630` | `[Landroid/content/pm/Signature;` | 反射签名 |
| `0x195860` | `ab_extra_sourcedir_7080` | AB 开关 |
| `0x1958a0` | `a7OpixY4xQc1eT2v` | 密钥材料 |
| `0x195a30` | `isSystemUser` | 方法名 |
| `0x198180` | `ro.product.brand` | 设备属性读取 |
| `0x198240` | `android/security/keystore/SoterKeyStoreProvider` | 厂商密钥库 |
| `0x198540` | `android/view/inputmethod/InputMethodManager` | 类名 |

**结论**：`DeviceNative.info2/info3/info4` 的类名与 `SecureNative` 的字符串
都用上述异或池保护，属于**混淆**而非密码学。该池 100% 可静态还原（
`tools/pddstr.py` 已实现），因此不构成"未分析的混淆代码"，但旧版报告
"`DeviceNative` 类名在库中缺失"的说法应更正为"**存在但异或保护**"。

### 9.2 动态库清单与在机情况

`com.aimi.android.common.build.a` 中的 `SoBuildInfo` 列表是本版本的**权威动态库
清单**：199 条记录，每条含 `epoch 毫秒`、库名、虚拟版本、32 位 hex MD5、Vita
组件 ID（`com.xunmeng.pinduoduo.v64lib<name>` / `v7alib<name>`）与优先级。

按该清单与设备实际落盘对照（`tools/so_manifest2.py`）：

| 位置 | 数量 | 说明 |
| --- | ---: | --- |
| APK `lib/arm64-v8a` | 22 | 冷启动必需，随包分发 |
| `files/dynamic_so` | 26 | 本设备已按需下载并落盘 |
| `assets/so_arm64-v8a/*.7z` | 3 | 内嵌压缩，启动时解出（`libtitan`、`libtronplayer`、`libstatic-webp`） |
| **清单内但不在本设备** | **54** | 代码路径存在，本机从未触发下载 |

54 个未落盘库全部能在 DEX 中找到加载点，例如 `libmeco_cookie.so`（`w33.a`
`System.loadLibrary`）、`libriskplugin.so`（`apm/risk/lock/c`）、
`libshadowhook.so`（`shook/ShadowHook`）、`libxunwind.so`（`apm/xunwind/XUnwind`）、
`libpapm_trace.so`（`apm/native_trace/d`）、`libBigAllocMonitor.so`（`apm/alloc`）、
`libwallet_crypto_box.so`（`wallet_pandora/Pandora`）、`libsargeras.so`、
`libgiflib.so`、`libchat_msg.so`。获取路径统一为
`arch.vita` 组件拉取（`dynamic_so.b.Q` → `dynamic_so.b.D/H`），需要网络与版本
策略同时命中。

**这 54 个库的混淆情况未纳入本报告的逐库清点。** §4–§6 覆盖的是 APK 自带 22 个、
assets 内嵌 3 个、运行时落盘 26 个，合计 51 个 ELF。清单中另有
`libwallet_crypto_box.so` 一次都没有被取得，其命名指向密码学职责，属于明确的
未覆盖项。

### 9.3 已取得库上的闭包结论

在 51 个已取得 ELF 上，混淆手法可以穷尽为三种标准变换，且每种都能静态还原：

| 变换 | 出现位置 | 还原方式 |
| --- | --- | --- |
| FLA 控制流平坦化 | `libpdd_secure`、`libtronav`、`libdyncommon` 等 30+ 个库 | 状态常量 + 二分派发树 → 基本块图（`unflatten.py`） |
| IND-BR 间接分支 | `libpdd_secure`（2,013）、`libpdd_rubik`（390）、`libdyncommon`（19,801） | `csel`→`ldr`→位运算→`br`，目标表由 `INIT_ARRAY` 填充 |
| ADR+RET 返回地址间接化 | 仅 `libdyncommon`（330 处） | `adr x30` + `add x30,x30,xN` + `ret`，偏移来自运行时表 |

在已取得的 51 个库中，**没有**出现：自解密代码段、字节码虚拟机、
不透明谓词之外的虚假分支、常量表异或/分片隐藏、DEX 加壳、`Java_*` 符号抹除。

出现且在 §9.4 完整还原的手法有两类，**均属标准变换、均可静态还原**：

- **异或字符串池**：仅 `libpdd_secure.so`，113 条，已全部解出（§9.4）。
- **`RegisterNatives` 动态注册**：12 个库 25 处，注册项数与类名均已读出（§9.1）。

`libdyncommon.so` 一例可以说明这类判定的可解释性：它混淆最重（19,801 个间接派发
块、330 处 ADR+RET），但 `.rodata` 中的字符串把它完整定性为反注入/反 hook 环境
探测（见 §6.1），不含自解密或虚拟机——重混淆并不等于不可解释。

### 9.4 native 字符串加密（异或池）

§1 表中"native 字符串"一行展开如下。这是本版新增的第 4 类混淆手法。

#### 9.4.1 结构

`libpdd_secure.so` 在 `.rodata` 的 `0x1928c0`–`0x199000` 区间放了一个字符串池。
记录格式：

```
[8 字节 XOR 掩码][正文…][0x00]
```

其中正文按**绝对文件偏移 `mod 8`** 与循环密钥对齐——同一段密钥在不同起始
偏移的记录上呈现不同相位，这是静态识别该池的主要障碍。

解保护的唯一计算式（8 个解密例程变体都归约到它）：

```python
KEY = bytes([0xf0,0x97,0x45,0xe4,0x83,0x5f,0xd1,0x9f])
plain[i] = cipher[i] ^ KEY[(abs_off + i) % 8]
```

`KEY` 本身不是明文常量，而是密钥表 `0x198768` 中 8 个小端 `u64` 的**高字节
取反**：`0f 68 ba 1b 7c a0 2e 60` 取反即得 `f0 97 45 e4 83 5f d1 9f`。
编码端与解码端在 `0x193068` 记录了自身 `.symtab` 名，说明该池由构建期工具
（而非手写）生成。

#### 9.4.2 解出的内容与风控含义

共 **113 条**有意义的标识符 / 路径 / 密钥材料（`tools/pddstr.py` 共输出 268 条候选，差额为被记录边界切断的片段），**已验证**。按用途归组：

| 组 | 典型串 | 风控含义 |
| --- | --- | --- |
| JNI 绑定 | `com/xunmeng/pinduoduo/secure/DeviceNative`、`DecResult`、`decBytes` | 见 §9.1.2 |
| 截屏检测 | `miui.intent.TAKE_SCREENSHOT` | MIUI 截屏广播监听 |
| 抓包检测 | `fiddler` | 抓包工具痕迹排查 |
| 应用枚举 | `pm list packages -u`、`packageName`、`android/content/pm/PackageManager`、`getChangedPackages`、`[Landroid/content/pm/Signature;`、`versionName`、`sourceDir`、`getInstallerPackageName`、`getUserId`、`getUserProfiles`、`myUserHandle`、`isSystemUser` | 已安装/新装应用枚举、签名与安装来源采集、多用户/分身判定 |
| 模拟器/Binder | `TRANSACTION_isFirstBoot`、`TRANSACTION_isKeyguardSecure`、`android/os/ServiceManager`、`android/os/IBinder`、`android/os/Parcel`、`android/content/pm/ActivityInfo` | Binder 直连 `system_server` 取首启状态、锁屏是否加密 |
| SIM/运营商 | `getSlotIndex`、`getSimState`、`serialNumber` | SIM 卡槽状态与序列号 |
| 密钥库 | `java/security/KeyStore`、`KeyPairGenerator`、`KeyPair`、`getExtensionValue`、`android/security/keystore/SoterKeyStoreProvider`、`java/security/Provider` | 厂商密钥库（Soter）与证书扩展读取 |
| 网络编码 | `java/net/URLEncoder` | 指纹串 URL 编码 |
| 反分析 | `/proc/self/cgroup`、`vivo_screen_record_switch_setting`、`uw)K`、`bANoelxRIifGL8dUr5zc2ncyYkebkUkd`、`sN4S72X1br+Ybnq1`、`a7OpixY4xQc1eT2v` | 容器/多开判定、录屏开关、会话密钥材料 |

其中 `MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCmW0KhXZ2dBgLqEnttvkg28G8s5oXBSzyuhmm+FJegTBa5+CsKxo5+tirAgk2EiGqPwxHIQu1XP5v1z4EfNzgfrYQ+EYJsJ/MC9CyFe8qY5dFa89A70n6U+XGd8VtmcVw1jfrT+YHHyInY5cpbC9BbnsUqX7EolUmoqrF4voFVLwIDAQAB`
是一条完整的 1024 位 RSA 公钥（X.509 `SubjectPublicKeyInfo`，ASN.1 头
`300d06092a864886f70d0101010500` + `30 81 89 02 81 81`），模数 128 字节。
**用途判为假说**：它只出现在字符串池中，未在本次静态分析中定位到解密/验签
调用点；按上下文（与 `DecResult`/`decBytes` 同池）推测用于**上报内容的非对称
加密或签名校验**，而非传输层握手（传输均为标准 HTTPS，见
[network.md](network.md)）。这一点记入 §9.5 未覆盖项。

#### 9.4.3 覆盖边界

| 库 | 字符串加密 | 判据 |
| --- | --- | --- |
| `libpdd_secure.so` | **有**（异或池，113 条有效串，已还原） | 已完整解出明文 |
| 其余 50 个已取得 ELF | 无 | `.rodata` 字符串全部明文可读 |
| 54 个未落盘库 | 未取值 | 未纳入 |

### 9.5 未覆盖项汇总

| 项 | 状态 | 影响 |
| --- | --- | --- |
| 清单内 54 个未落盘库 | 未取得 | 其内部混淆手法未逐库清点；加载点与用途已在 DEX 侧确认 |
| `libpdd_secure` 字符串池 RSA 公钥 | 明文已还原，调用点未定位 | 用途判为假说（上报加密/验签），见 §9.4.2 |
| `SE`（11 个）/ `meco.cookie.N`（12 个）/ `shook.ShadowHook`（14 个） | 未判定 | 类声明与调用点已确认，提供库不在任何快照中，见 §9.6 |
| `libpdd_secure` 选择子语义 | 结构已证实，取值集合未逐一断言 | 见 [algorithm.md](algorithm.md) §5.2 |
| `assets/A94/CDA.cdnMd5` | 假说 | 判为服务端增量基线，见 [algorithm.md](algorithm.md) §6.3 |
| 收包侧 Xlog | 仅确认加密容器 | 见 [storage.md](storage.md) |

### 9.6 未绑定的 native 方法：逐类定位结果

要求 6 的"不允许未分析清楚"在这里落到"每个 native 方法都能指认提供库，或明确
记为未落盘库"。已完成逐类定位的结论如下。

| 类 | 方法数 | 绑定方式 | 依据 |
| --- | ---: | --- | --- |
| `com.xunmeng.pinduoduo.secure.SecureNative` | 34 导出 + 3 注册 | 符号导出 + `RegisterNatives` | 34 个 `Java_…_SecureNative_*` 导出；3 个走 §9.1.2 的注册 |
| `com.xunmeng.pinduoduo.secure.DeviceNative` | 3 | `RegisterNatives` | 类名从异或池解出，见 §9.1.2 |
| `com.xunmeng.pinduoduo.secure.SE` | 11 | **未判定** | 见下 |
| `com.xunmeng.pinduoduo.secure.SecureNative` 的 `atn`/`csd`/`dsi` | 3 | **未绑定** | 见下 |
| `com.xunmeng.pinduoduo.shook.ShadowHook` | 14 | 未落盘库 | 调用点 `shook/ShadowHook.java:149` 处 `loadLibrary("shadowhook")` → 清单内 `libshadowhook.so`（假说） |
| `meco.cookie.N` | 12 | 未落盘库 | 清单内 `libmeco_cookie.so`；DEX 加载点 `w33.a` |
| `com.media.tronplayer.TronMediaPlayer` | 38 | 未落盘库（assets 内嵌） | `assets/so_arm64-v8a/libtronplayer.7z` |

#### 9.6.1 `SE` 的 11 个方法

`SE.java` 声明 11 个 `public static native` 方法：
`as, ed, gem, ir, it, sv, ts, ue, ues, us, wtp`。**11 个全部**在 APK 内 51 个
ELF 中找不到提供者（既无 `Java_…_SE_*` 导出，也无 `#1720` 注册）。**调用链是活的**，
不是死代码：

| 调用点 | 插入的键 | 上游 `data_type` |
| --- | --- | --- |
| `lb2/p.java:19` | `"wtp"` | `"17"` |
| `lb2/r.java:20` | `"s_f_d"` | `"20"` |
| `lb2/u.java:27` | `"info"`（`atn`） | — |
| `lb2/w.java:27` | `"info"`（`csd`） | — |
| `lb2/s0.java:128` | `dsi` 的返回值 | — |
| `SecureNative.java:300/313/342/347/361` | `it`/`ue`/`as`/`ts`/`ues` | — |

两点直接证据表明提供库**不在本机快照中**：

1. 按 `JNIEnv` 函数表下标 `#1720` 逐库扫描，51 个 ELF 中没有任何一处注册
   `SE` 的方法名；
2. 在 51 个 ELF 中做原始字节搜索，`SE` 独有的超长签名
   `(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B[BZLjava/util/Map;)V`
   与 `(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B[BZ)Ljava/util/Map;`
   命中数为 **0**；3 字符名 `atn`/`csd`/`dsi` 同样为 **0**。

清单内 54 个未落盘库中，`libriskplugin.so` 与 `libshook`/`libsargeras` 是
`SE` 的**候选**提供者（`libriskplugin` 的 DEX 加载点在 `apm/risk/lock/c`，
与 `SE` 同属安全域），但本设备从未下载该库，**无法用符号确认**，故记为
未判定而非断言。

#### 9.6.2 `atn` / `csd` / `dsi`

这 3 个方法（`SecureNative` 的 35 个声明中未被 34 个导出覆盖的 3 个）同样是
活路径：`lb2/u.java`、`lb2/w.java`、`lb2/s0.java` 分别调用它们并把结果放进
上报 JSON 的 `"info"` 字段。它们既不在 `libpdd_secure.so` 的 34 个导出中，
也不在该库的异或字符串池中，51 个 ELF 的原始字节搜索同样为 0。

**注意**：旧版 [evidence.md](evidence.md) §8 称这 3 个方法"`JNI_OnLoad` 内只见
2 次 `__android_log_print` + 1 次初始化调用，未见 `RegisterNatives` 路径"。
`libpdd_secure.so` 确实只有 1 处注册点（`0x27e7c`，注册 3 项 = `DeviceNative`），
但该结论的**推理方式**（据"未导入 `RegisterNatives` 符号"下判断）是错的——见
§9.1.1。3 个方法未绑定的结论本身仍然成立，只是判据应改为本节的两项直接证据。
