# 混淆闭包：DEX 与全部 native 库

本文件回答"是否还有未分析的混淆代码"。结论按**层**给出，每层都有可复现的判据与
计数。

## 1. 结论速览

| 层 | 结论 |
| --- | --- |
| DEX 字符串 | **无字符串加密**。291,149 条可打印字符串直接以明文存在于 6 个 dex。 |
| DEX 加壳 | **无加壳、无 DEX 加密**。`classes*.dex` 均为标准 `dex\n035` 头，可直接解析 25,156 个 Java 文件。 |
| DEX 动态注册 | APK 内 native 库中 `RegisterNatives` 出现 **0** 次（仅运行时下载的 `libtronplayer.so` 有 1 处、`libmedia_engine.so` 有 3 处）。 |
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

没有出现以下任何一种：自解密代码段、字节码虚拟机、字符串解密循环、控制流伪造
（不透明谓词之外的虚假分支）、常量表异或/分片隐藏、`RegisterNatives` 隐藏绑定、
DEX 加壳。

## 9. native 绑定方式与混淆闭包的边界

§1–§8 的结论都是**对已取得 ELF 的库**成立的。本节补齐两个问题：库用什么方式绑定
JNI 方法、以及哪些库尚未取得。这两点决定了"没有未分析的混淆代码"这句话的确切
范围。

### 9.1 绑定方式（`tools/jnibind.py`）

对全部 1,612 个 DEX 声明的 native 方法逐一判定绑定方式：

| 绑定方式 | 数量 | 判据 |
| --- | ---: | --- |
| 符号导出 `Java_*` | 646 | 库的 `.dynsym` 中存在按 JNI 规范改写的符号 |
| `RegisterNatives` | 87 | 库的 `.rodata` 同时含方法名串与 JNI 签名串，且库导出 `JNI_OnLoad` |
| 未判定 | 879 | 约束到 104 个类，其提供库不在任何快照中 |

需要强调：**没有任何一个库从 `_JNIEnv::RegisterNatives` 导入符号**（对全部 87 个
库检查 `_ZN7_JNIEnv15RegisterNativesEP7jclassPK15JNINativeMethodi` 均为 0）。所有
动态注册都走 `JNIEnv` 函数表指针（`(*env)->RegisterNatives(...)`），这在 ARM64
上表现为经 `x0`（env）取表偏移后 `blr`，不产生导入符号。因此"查
`RegisterNatives` 导入"会得到假阴性；§9.1 的判定改用"方法名串 + 签名串 + 库自身
导出 `JNI_OnLoad`"三重条件。

`RegisterNatives` 注册的典型例子是 `libyoga.so`：该库没有 `YogaNative` 的
`Java_*` 导出，但 `.rodata` 中同时存在 `com/facebook/yoga/YogaNative` 与 58 个
方法签名串，且导出 `JNI_OnLoad`——与 Yoga 上游实现一致。

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

在已取得的 51 个库中，**没有**出现：自解密代码段、字节码虚拟机、字符串解密循环、
不透明谓词之外的虚假分支、常量表异或/分片隐藏、DEX 加壳、`Java_*` 符号抹除。

`libdyncommon.so` 一例可以说明这类判定的可解释性：它混淆最重（19,801 个间接派发
块、330 处 ADR+RET），但 `.rodata` 中的字符串把它完整定性为反注入/反 hook 环境
探测（见 §6.1），不含自解密或虚拟机——重混淆并不等于不可解释。

### 9.4 未覆盖项汇总

| 项 | 状态 | 影响 |
| --- | --- | --- |
| 清单内 54 个未落盘库 | 未取得 | 其内部混淆手法未逐库清点；加载点与用途已在 DEX 侧确认 |
| `libpdd_secure` 选择子语义 | 结构已证实，取值集合未逐一断言 | 见 [algorithm.md](algorithm.md) §5.2 |
| `assets/A94/CDA.cdnMd5` | 假说 | 判为服务端增量基线，见 [algorithm.md](algorithm.md) §6.3 |
| 收包侧 Xlog | 仅确认加密容器 | 见 [storage.md](storage.md) |
