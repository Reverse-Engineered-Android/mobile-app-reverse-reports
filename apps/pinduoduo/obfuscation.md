# 混淆闭包：DEX 与全部 native 库

本文件回答"是否还有未分析的混淆代码"。结论按**层**给出，每层都有可复现的判据与
计数。

## 1. 结论速览

| 层 | 结论 |
| --- | --- |
| DEX 字符串 | **无字符串加密**。291,149 条可打印字符串直接以明文存在于 6 个 dex。 |
| DEX 加壳 | **无加壳、无 DEX 加密**。`classes*.dex` 均为标准 `dex\n035` 头，可直接解析 25,156 个 Java 文件。 |
| native 动态注册 | 48 个去重 ELF 中 `RegisterNatives`/`UnregisterNatives` **有**真实调用点：29 处（**28 次注册 + 1 次注销**）分布 18 个库，含 `libpdd_secure`、`libpdd_rubik`、`libCSoLoader`、`libbytehook`、`libcrashAvoid`、`libpcrash`、`libpcrash_anr`、`liblegonative`、`libmarsxlog`、`libtronkit`、`libyoga`、`libtronplayer`、`libmedia_engine` 等；其中 14 次注册位于 APK 自带的 22 个库内。**注意**：`libdyncommon` 的 `#1720/#1728` 命中全是间接派发器，**不是**注册点。计数口径的两条判据见 §9.1.1。 |
| native 字符串 | **有局部字符串加密，覆盖两个库**：`libpdd_secure.so`（池首 0x1928c0，四掩码并集 **634 条**）与 `libdyncommon.so`（池首 0x408fa0，**458 条**）用**同一构建期工具与同一张密钥表的四个字节行**（掩码 A `f09745e4835fd19f` 等，配 `eor`/`eon` 两种算子）。前者含 `DeviceNative` 类名、`miui.intent.TAKE_SCREENSHOT`、**两把 RSA 公钥**、Android Key Attestation OID；后者含 Magisk/SuperSU/Xposed/模拟器路径、SELinux 与 verity 属性、**29 个** `ab_secure_*` 开关。详见 §9.4、§9.4.5。 |
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

**本版补充**：上面这段明文只是该库字符串的一小部分。同一 `.rodata` 里还有
**458 条异或加密的字符串**（池首 `0x408fa0`，与 `libpdd_secure.so` 同工具、同密钥表的四个字节行），
解出后才是该库探测面的全貌——Magisk/SuperSU/su 路径、Riru/EdXposed/SandHook、
模拟器（vboxsf/nemusf/ttVM/ranchu）、verified boot 与 SEPolicy、`/proc` 自省、
无障碍外挂，以及 **29 个** `ab_secure_*` 风控总开关。详见 §9.4、§9.4.5 与
[risk.md](risk.md) §14。**这也是上一版的一个实质遗漏**：当时按"`.rodata` 有明文串"
就判定该库无字符串加密，实际情况是明文与密文在**同一段内相邻共存**（选择性加密），
密文那半边被整个漏掉了。

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

**本版更正（计数口径）**：上一版的计数包含了大量假阳性，原因是把
"恰好用到 `#1720`/`#1728` 位移"当成了判据。该位移还有两个完全不同的来源：

1. **PLT 桩**：`.plt` 里逐个 GOT 槽走步，步长也是 8，于是每个导入符号都配上
   一个 `ldr x17,[x16,#1720]` / `ldr x17,[x16,#1728]`。`libaudio_engine.so`
   因此被误报出 1 对注册/反注册，而它根本没有任何注册。
2. **本 app 自己的间接派发**：`adrp` 一张静态表 → `ldr x8,[x8,#1720]` →
   `add x8,x8,x9`（**再加打包偏移**）→ `blr x8`。这是 FLA 的一部分，不是
   JNI 调用。

正确判据有两条，且必须同时满足：**(a)** 调用点不在 `.plt` 段内（段边界取自
ELF 头，不依赖 objdump 的符号标签——`.plt` 尾部会被反汇编成 `@@Base+offset`，
按符号名排除会漏）；**(b)** `ldr` 之后该寄存器**未经任何算术**就直达 `blr`/`br`
（一旦有 `add`/`sub`/`and`/`orr`/`eor`/`mvn`/`mov`/`movk`/移位即为派发器）。

另需注意：**不能**用"基址寄存器是否由 `ldr` 装入"做判据。`.text` 里合法代码
把 JNIEnv 存在被调用者保存寄存器里，函数入口写一次后面复用，反向扫描能否
看到那次 `ldr` 全凭运气；依赖它会静默丢掉真注册点（`libpcrash_anr.so:0x43a4`
以及 `libpdd_j2v8.so` 的多个点就是这样被漏掉的）。改用上述两条判据后：

| 库 | `RegisterNatives` | `UnregisterNatives` | 备注 |
| --- | ---: | ---: | --- |
| `libpdd_secure.so` | 1 | 0 | `Java_…_SecureNative_dec` 内部（§9.1.2 逐字节还原） |
| `libtronkit.so` | 3 | 0 | `JNI_OnLoad` / `JNI_OnUnload` |
| `liblegonative.so` | 3 | 0 | `VMState_getOpCostGroup` / `JSFunction_releaseNative` |
| `libtronplayer.so` | 3 | 0 | 3 处（APK 内 + assets 副本共 4 点，去重后 3） |
| `libaipin_wrapper.so` | 2 | 0 | — |
| `libCSoLoader.so` | 1 | 0 | `JNI_OnLoad` |
| `libbytehook.so` | 1 | 0 | `JNI_OnLoad` |
| `libcrashAvoid.so` | 1 | 0 | `JNI_OnLoad` |
| `libpcrash.so` | 1 | 0 | `JNI_OnLoad` |
| `libpcrash_anr.so` | 1 | 0 | `TraceDumper_jniInit` |
| `libmarsxlog.so` | 1 | 0 | `JNI_OnLoad` |
| `libyoga.so` | 1 | 0 | `JNI_OnLoad` |
| `libpdd_rubik.so` | 1 | 0 | `0x1639b0` |
| `libmmkv_v2.so` / `libAlgoSystem.so` / `libstatic-webp.so` / `libtronnap.so` | 各 1 | 0 | 运行时下载库 |
| `libmedia_engine.so` | 4 | 1 | 运行时下载库 |
| **合计（48 个去重 ELF）** | **28** | **1** | 18 个库 |

按在机范围分别统计：

| 范围 | 库数 | `RegisterNatives` | `UnregisterNatives` |
| --- | ---: | ---: | ---: |
| APK `lib/arm64-v8a`（22 个） | 10 | 14 | 0 |
| `assets` 内嵌（3 个） | 2 | 4 | 0 |
| `files/dynamic_so`（26 个） | 8 | 14 | 1 |
| 运行时快照补集 | 1 | 1 | 0 |
| **去重合计** | **18** | **28** | **1** |

（`libtronplayer`/`libpdd_rubik`/`libstatic-webp` 等既在 APK 又在 `dynamic_so`
出现，去重后只计一次，故分范围之和大于去重合计。）

`libpdd_secure.so` 与 `libdyncommon.so` 这两个池承载库，**只有
`libpdd_secure.so` 有真注册点**；`libdyncommon.so` 的 5 个 `#1720`/`#1728`
命中全部是判据 (b) 排除掉的派发器，不是注册。

注册项数由第三个参数 `w3` 直接给出：`tools/rnbind.py --all` 用判据 (a)(b)
筛出真注册点并列出被排除的站点，再对每个通过的站点反汇编其前 `0x60` 字节，
读 `#1720` 之前最后一次写入 `w3` 的立即数（**本次逐站点反汇编复核过，
`w3` 与 `#1720` 均在同一窗口内**）。结果（代码段偏移）：

| 库 | 站点 | `nMethods` | 宿主函数 |
| --- | --- | ---: | --- |
| `libCSoLoader.so` | `0x1794` | 2 | `JNI_OnLoad` |
| `libbytehook.so` | `0xd594` | 10 | `JNI_OnLoad` |
| `libcrashAvoid.so` | `0x662c` | 6 | `JNI_OnLoad` |
| `libmarsxlog.so` | `0xcb38` | 1 | `JNI_OnLoad` |
| `libpcrash.so` | `0x4494` | 3 | `JNI_OnLoad` |
| `libpcrash_anr.so` | `0x43a4` | 3 | `TraceDumper_jniInit` |
| `libpdd_secure.so` | `0x27e7c` | 3 | `DeviceNative`（§9.1.2 已逐字节还原） |
| `liblegonative.so` | `0x60b30` / `0x625a0` / `0x638f4` | 16 / 29 / 10 | `VMState_getOpCostGroup` / `JSFunction_releaseNative` / `JSFunction_releaseNative` |

**注意一处易误判**：同一套"回溯读 `w3`"的启发式在 `libpdd_secure.so` 上
会对 `0x4c8e0`、`0x8435c`、`0x93b6c`、`0xadd*`、`0x111f88` 等站点报
`nMethods = None`。这些**不是注册点**——它们是 §9.4 池拷贝/派发代码借用了
同一个 `#1720`/`#1728` 位移做表分发，属于判据 (b) 已排除的那一类。
本报告只把经过 (a)+(b) 双判据且能读出 `nMethods` 的站点计入注册，
因此 `libpdd_secure.so` 的注册数**仍为 1**。

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
都用上述异或池保护，属于**混淆**而非密码学。该池 100% 可静态还原
（`tools/xorstr.py` 已实现），因此不构成"未分析的混淆代码"，但旧版报告
"`DeviceNative` 类名在库中缺失"的说法应更正为"**存在但异或保护**"。

**更正**：上表列举的是池内明文，但"按绝对偏移取相位"只能捞回 8 字节对齐的
那批；本版按 §9.4.1 的记录边界规则重解后，`libpdd_secure.so` 池的完整规模是
**634 条**（本小节上表仅为其中示例），且 `libdyncommon.so` 另有一个同工具的池
（458 条）——详见 §9.4。

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
| **清单内但不在本设备** | **54** | 代码路径存在，本机从未触发下载（Vita 已注册，见下） |

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

#### 9.2.1 这 54 个库的准确状态：已注册、按需下发、本机未触发

本设备上的组件框架（Vita / Volantis）给出了这 54 个库的**权威状态**，而不是
"缺失"。`files/.newLocker/` 下 183 个零字节 `.vlock` 文件，其文件名是
**`MD5(组件ID)`**（可带 `-patch` 与版本后缀）；反解后得到**完整组件注册表
126 个**，与 `files/mmkv/vita_local_comp_v2` 的**已安装表 46 条**做差：

| 集合 | 数量 |
| --- | ---: |
| 已注册（`.newLocker`） | 126 |
| 已安装（`vita_local_comp_v2` / `files/.vita`） | 46 |
| **已注册但未下载** | **80** |

`SoBuildInfo` 的 105 个 `absent` 条目中，**54 个**出现在这 80 个已注册未下载项里；
其余 51 个使用 `v7alib*` 命名（Vita 用 `v64lib*`），属另一套命名空间，
样本设备为 arm64 故不适用。因此：

- 这 54 个库**不是分析遗漏，也不是 APK 缺件**，而是**已注册、等待按需下发**；
- 触发条件是"网络 + 版本策略同时命中"（§9.2 正文），未命中时永远不会落盘；
- 已注册未下载清单里的风控/加固组件包括 `libpdd_secure`、`libmeco_cookie`、
  `libsargeras`、`libshadowhook`、`libxunwind`、`libriskplugin`、`libpdd_sa_hook`、
  `libbytehook`、`libCSoLoader`、`libpcrash{,_anr,_dumper}`、`libapm_cpu`、
  `libpapm_trace`、`libBigAllocMonitor`、`libwallet_crypto_box` 等。

格式、清单文件与注册表解法见 [vita.md](vita.md) §3、§5。

### 9.3 已取得库上的闭包结论

在 48 个去重后的已取得 ELF 上，混淆手法可以穷尽为四种标准变换，且每种都能
静态还原（第四种见 §9.4，为异或字符串池）：

| 变换 | 出现位置 | 还原方式 |
| --- | --- | --- |
| FLA 控制流平坦化 | `libpdd_secure`、`libtronav`、`libdyncommon` 等 30+ 个库 | 状态常量 + 二分派发树 → 基本块图（`unflatten.py`） |
| IND-BR 间接分支 | `libpdd_secure`（2,013）、`libpdd_rubik`（390）、`libdyncommon`（19,801） | `csel`→`ldr`→位运算→`br`，目标表由 `INIT_ARRAY` 填充 |
| ADR+RET 返回地址间接化 | 仅 `libdyncommon`（330 处） | `adr x30` + `add x30,x30,xN` + `ret`，偏移来自运行时表 |

在已取得的 48 个库中，**没有**出现：自解密代码段、字节码虚拟机、
不透明谓词之外的虚假分支、常量表异或/分片隐藏、DEX 加壳、`Java_*` 符号抹除。

出现且已完整还原的手法有三类，**均属标准变换、均可静态还原**：

- **异或字符串池**：`libpdd_secure.so` **634 条** + `libdyncommon.so` **458**
  条，两库同工具、同密钥表的四个字节行，已全部解出（§9.4）。
- **`RegisterNatives` 动态注册**：18 个库 28 处（+1 处反注册），注册项数、
  类名与调用点均已读出（§9.1）。
- **`.rodata` 选择性加密**：同一库内明文与密文相邻共存（`libdyncommon`），
  因此"`.rodata` 有明文串"不能作为"该库无池"的判据（§9.4.3）。

`libdyncommon.so` 一例可以说明这类判定的可解释性：它混淆最重（19,801 个间接派发
块、330 处 ADR+RET），且其字符串池选择性加密，但按 §9.4 的四掩码规则解出 458 条
明文后，它被完整定性为反注入/反 hook 环境探测（见 §6.1 与
[risk.md](risk.md) §14），不含自解密或虚拟机——重混淆并不等于不可解释。

### 9.4 native 字符串加密（异或池）

§1 表中"native 字符串"一行展开如下。这是本版新增的第 4 类混淆手法。

**本版更正（两轮）**：上一版把该池记为 `libpdd_secure.so` 独有，并把它描述为
"正文按绝对文件偏移 `mod 8` 取相位"——这两点已作废，见 §9.4.3 与 §9.4.4。
更进一步，本版发现该池**不是单一密钥**：`.text` 里有**四个**解码循环，用
**同一张密钥表的四个不同字节行**，分别配 `eor`（不取反）与 `eon`（取反）
两种算子。只用一个密钥的普查会漏掉另外三组，见 §9.4.5。

#### 9.4.1 结构

同一个构建期工具给**两个**库下了池，两库共用完全相同的 8 字节密钥：

| 库 | 池位置 | 密钥表 |
| --- | --- | --- |
| `libpdd_secure.so` | `.rodata`，首条 0x1928c0 | 0x198768 |
| `libdyncommon.so` | `.rodata`，首条 0x408fa0 | 0x40f2d8 |

两张密钥表内容完全相同（8 个小端 `u64`），因此两者的密钥集合也相同：

```python
plain[i] = cipher[i] ^ KEY[i % 8]          # i 从本条记录自身起点计数
```

`KEY` 本身不是明文常量，而是密钥表里 8 个小端 `u64` 的某个**字节行**，
再按循环是否取反决定是否加 `NOT`。`eor w13,w13,w14,lsr #24` 取的是每个
`u64` 的**最高字节**，`lsr #8` 取的是**次低字节**。四个实际在用的组合是：

| 掩码 | `KEY` | 对应 `.text` 中的循环 |
| --- | --- | --- |
| A | `f0 97 45 e4 83 5f d1 9f` | `eon w13, w13, w14, lsr #24` |
| B | `0f 68 ba 1b 7c a0 2e 60` | `eor w13, w13, w14, lsr #24` |
| C | `b1 30 15 3d f6 99 23 83` | `eon w13, w13, w14, lsr #8` |
| D | `4e cf ea c2 09 66 dc 7c` | `eor w13, w13, w14, lsr #8` |

A 与 B 互为按位取反，C 与 D 互为按位取反，这与 `eor`/`eon` 的成对出现一致。
`libpdd_secure.so` 在 `0x193068` 留了自身 `.symtab` 名，说明该池由构建期工具
（而非手写）生成。

**记录边界是本池真正的难点，不是相位。** 因为 `plaintext ^ KEY` 恰在
`plaintext == KEY[phase]` 时为零，两个相位上的密钥字节是可见 ASCII：

| 相位 | `KEY[phase]` | 该相位上的零字节实际含义 |
| --- | --- | --- |
| 2 | `0x45` `'E'` | 正文里的 `E` |
| 5 | `0x5f` `'_'` | 正文里的 `_` |

其余 6 个相位的密钥字节不可打印，其上的裸 `0x00` 只可能是终止符。判定规则
因此是：**逐字节解码，遇到裸 `0x00` 时，若其相位是 2 或 5 且下一个字节不是
`0x00`，则它是正文；否则记录结束。** 记录尾部是长度 ≥1 的 `0x00` 串。

这条规则不是细节，它决定了解出的是不是真串。若按"单个 `0x00` 即终止"切分，
`ab_secure_hook_detect_7020` 会被截成 `ab_secure_hook_detect`（`detect` 前的
`_` 落在相位 5），`Java_com_xunmeng_..._SecureNative_...` 会被截在第三个下划线
处；若按" `0x00 0x00` 切分"，以 `_` 结尾的记录（`..._encryptNetBook_`）又会
被啃掉尾下划线。两种近似法各自丢一批串，且都看不出来丢了——这正是上一版
把它写成"绝对偏移取相位"的原因：以绝对偏移为锚只能捞回 8 字节对齐的那批，
fiddler（偏移 `mod 8 == 4`）这类记录会被漏掉。

#### 9.4.2 解出的内容与风控含义

`tools/xorstr.py` 按四掩码并集解出（min-len 8）：

| 库 | A | B | C | D | **并集** |
| --- | --- | --- | --- | --- | --- |
| `libpdd_secure.so` | 151 | 148 | 166 | 169 | **634** |
| `libdyncommon.so` | 129 | 120 | 113 | 96 | **458** |

**均已全部还原为明文，无剩余不可解释记录**。单掩码数字（A 列的 151/129）
是上一版报告的数值——它只覆盖了四个循环中的一个，其余三组当时被当成"噪声"
丢弃；这一项已在本节更正。按用途归组：

**`libpdd_secure.so`（634 条；下表为其中较长者，择要）**

| 组 | 典型串 | 风控含义 |
| --- | --- | --- |
| JNI 绑定 | `com/xunmeng/pinduoduo/secure/DeviceNative`、`com/xunmeng/pinduoduo/secure/DecResult`、`decBytes` | 见 §9.1.2 |
| 截屏检测 | `miui.intent.TAKE_SCREENSHOT` | MIUI 截屏广播监听 |
| 抓包检测 | `fiddler` | 抓包工具痕迹排查 |
| 应用枚举 | `pm list packages -u`、`packageName`、`android/content/pm/PackageManager`、`getChangedPackages`、`[Landroid/content/pm/Signature;`、`versionName`、`sourceDir`、`getInstallerPackageName`、`getUserId`、`getUserProfiles`、`myUserHandle`、`isSystemUser` | 已安装/新装应用枚举、签名与安装来源采集、多用户/分身判定 |
| 模拟器/Binder | `TRANSACTION_isFirstBoot`、`TRANSACTION_isKeyguardSecure`、`android/os/ServiceManager`、`android/os/IBinder`、`android/os/Parcel`、`android/content/pm/ActivityInfo` | Binder 直连 `system_server` 取首启状态、锁屏是否加密 |
| SIM/运营商 | `getSlotIndex`、`getSimState`、`serialNumber` | SIM 卡槽状态与序列号 |
| 密钥库 | `java/security/KeyStore`、`KeyPairGenerator`、`KeyPair`、`getExtensionValue`、`android/security/keystore/SoterKeyStoreProvider`、`java/security/Provider` | 厂商密钥库（Soter）与证书扩展读取 |
| 网络编码 | `java/net/URLEncoder` | 指纹串 URL 编码 |
| 反分析 | `/proc/self/cgroup`、`vivo_screen_record_switch_setting`、`io.virtualapp`、`isDebuggerConnected`、`/system/bin/su`、`/vendor/bin/su`、`bANoelxRIifGL8dUr5zc2ncyYkebkUkd`、`sN4S72X1br+Ybnq1`、`a7OpixY4xQc1eT2v` | 容器/多开判定、调试器检测、su 路径探测、会话密钥材料 |
| 录屏检测（掩码 D） | `com.samsung.android.app.screenrecorder.on` / `.off`、`recorder_status` | 三星系统录屏状态读取 |
| 录屏检测（掩码 C） | `com.samsung.android.app.screenrecorder.off`、`recorder_status` | 同上（两条循环各自持有副本） |
| 密钥证明（掩码 C） | `1.3.6.1.4.1.11129.2.1.17`、`setAttestationChallenge`、`setDigests`、`setUserAuthenticationRequired`、`KeyGenParameterSpec$Builder`、`android/content/pm/IPackageManager$Stub` | **Android Key Attestation 扩展 OID**：申请带 attestation 的密钥并读回证书扩展，是硬件级设备身份判定 |
| 设备标识（掩码 C/D） | `pddid_secure`、`did_info`、`pdd_key_202606_9`、`{"clip_key":"%s","data":"%s"}` | 设备 ID 生成与剪贴板上报 |
| 前缀常量（掩码 C） | `imouLuk2VljedHkn`、`4JDCf1gg/U+jHWT0`、`LPCtkKIgsPls4al8`、`E980BF2E3D60DC8D`、`abcd-1234-ABCD@#`、`d6fc3a4a06adbde89223bvefedc24fecde188aaa9161` | 会话/摘要密钥材料 |
| JNI 名（掩码 B/D） | `Java_..._SecureNative_rsaEncrypt`、`..._rsaEncryptWithPublicKey`、`..._generateTrackDataSign`、`..._generateWSDataSign`、`com/xunmeng/pinduoduo/secure/EncResult` | **见 §9.4.6：RSA 调用方向由此确定** |

**`libdyncommon.so`（458 条，详见表外说明）**——本版新增，见 §9.4.4 与
[risk.md](risk.md) §14。要点：该库用**同一套池**保护它的 root / Magisk /
SuperSU / 模拟器（vboxsf、nemusf、ttVM、bstshutdown、nemuinit、ranchu）/
Xposed（riru_edxp、sandhook.edxp、libSignatureKiller、EnableXposedHook）/
SELinux 与 verity（`ro.boot.verifiedbootstate`、`plat_sepolicy_and_mapping.sha256`、
`plat_property_contexts`）/ 进程自省（`/proc/self/maps`、`/proc/self/mounts`、
`/proc/self/mountinfo`、`/proc/modules`、`/memfd:jit-zygote-cache`、
`/dev/ashmem/jit-cache`）/ 无障碍与自动点击（`ACCESSIBILITY_SERVICE`、
`simplehat.clicker`、`autotool`）字符串，以及一组 `ab_secure_*` AB 开关键。

其中 `MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCmW0KhXZ2dBgLqEnttvkg28G8s5oXBSzyuhmm+FJegTBa5+CsKxo5+tirAgk2EiGqPwxHIQu1XP5v1z4EfNzgfrYQ+EYJsJ/MC9CyFe8qY5dFa89A70n6U+XGd8VtmcVw1jfrT+YHHyInY5cpbC9BbnsUqX7EolUmoqrF4voFVLwIDAQAB`
是一条完整的 1024 位 RSA 公钥（X.509 `SubjectPublicKeyInfo`，ASN.1 头
`300d06092a864886f70d0101010500` + `30 81 89 02 81 81`），模数 128 字节，
`e = 65537`。**调用点与方向已定位，不再是假说**，见 §9.4.6。

#### 9.4.3 覆盖边界（更正）

| 库 | 字符串加密 | 判据 |
| --- | --- | --- |
| `libpdd_secure.so` | **有**（并集 634 条 / min-len 12 时 515 条，已全部还原） | 已完整解出明文 |
| `libdyncommon.so` | **有**（并集 458 条 / min-len 12 时 382 条，与上库**同工具、同密钥表的四个字节行**） | 已完整解出明文 |
| 其余 46 个已取得 ELF | 无 | `.rodata` 字符串全部明文可读 |

普查方法：对全部 48 个去重后的 ELF 的 `.rodata` / `.data.rel.ro` 按四掩码
并集解码，统计条数（`--count-only --min-len 12`）。`libpdd_secure` 与
`libdyncommon` 之外的最高命中依次是 `libmedia_engine` **3** 条、
`libtronav` **1** 条、`libopus_pdd` **1** 条，均无路径/类名语义，判为偶然
命中的 ASCII 噪声，不构成池。**"只有两个库有池"这一结论在四掩码下依然
成立**：次高者仅 3 条，与两个池库（515 / 382 条）相差两个数量级。

`libdyncommon` 是**选择性**加密：同一 `.rodata` 里明文与密文相邻共存，例如
`basic_string`、`_ZN3art9ArtMethod16EnableXposedHook...` 是明文，而
`/system/xbin/magisk` 是密文。因此"某库 `.rodata` 有明文串"不能推出"该库无池"，
必须按相位逐条试解——这是上一版把该库漏掉的直接原因。

#### 9.4.4 `libdyncommon.so` 池中的 JNI 名（结论修正）

池里解出两条 `Java_*` 名：

```
0x40ea70  Java_com_xunmeng_pinduoduo_secure_SecureNative_aesDecryptWithKey
0x40ec20  Java_com_xunmeng_pinduoduo_secure_SecureNative_encodeBase64
```

上一版草稿据此险些断言"`libdyncommon` 是 `SecureNative` 的第二实现"。**该断言
不成立**，逐项核验如下：

- `libdyncommon.so` 的 `.dynsym` 中 `Java_*` 导出数为 **0**；
- `aesDecryptWithKey` / `encodeBase64` 在全部 6 个 `classes*.dex` 中**零命中**，
  `SecureNative.java` 也没有这两个方法（该类的 34 个 `native` 声明里没有它们）；
- 这两个名字在 48 个 ELF 里**只**出现在 `libdyncommon` 的池中，任何库都没有
  对应的导出实现。

即：**没有 JVM 侧声明，也没有任何导出实现**，所以它们不是"第二条实现路径"，
而只能是该库自己动态解析的目标名（`libdyncommon` 导入 `dlsym`/`dlopen`/
`dl_iterate_phdr`，其 `JNI_OnLoad` 用 `RegisterNatives` 自行注册，见 §9.1.1）。
这属于"该库内部用到的符号名"，不是"隐藏的 `SecureNative` 实现"。
为稳妥起见，报告中**不**对这两条名做超出上述证据的用途断言。

#### 9.4.5 四个解码循环（为什么单密钥普查不可靠）

`.text` 中一共只有 4 个"按密钥表取字节再异或"的循环，全部集中在
`0xbba00`–`0xbc858` 区间，操作数完全相同，只有**位移量**与**算子**不同：

```asm
bba54:  add  x10, x10, #0x768          ; 密钥表 = 0x198768
bba70:  ldr  x14, [x10, x14, lsl #3]   ; x14 = table[phase]，小端 u64
bba74:  eor  w13, w13, w14, lsr #24    ; 掩码 B：取最高字节
...
bc67c:  eon  w13, w13, w14, lsr #24    ; 掩码 A：取最高字节后按位取反
...
bc7c4:  eon  w13, w13, w14, lsr #8     ; 掩码 C：取次低字节后按位取反
...
（`eor ..., lsr #8` 即掩码 D）
```

- 逐字节取相位：`x14 = table[i & 7]`，与"从记录自身起点计数"一致；
- `lsr #24` 取 `u64` 的最高字节，`lsr #8` 取次低字节；两者各自再分
  `eor`/`eon` 两支，合计四种组合，与上表 A/B/C/D 一一对应；
- **判定某个记录属于哪一支，取决于消费它的调用点（函数），而不是记录
  本身**——同一段 `.rodata` 里 A 组和 C 组的记录可以紧邻。因此"用 A 的
  密钥扫全节"必然把 B/C/D 三组的记录当成噪声丢掉。

每个掩码都由 ≥4 条独立记录交叉确认，且互相不可替代，例如：

| 记录 | 只在哪个掩码下成词 | 说明 |
| --- | --- | --- |
| `1.3.6.1.4.1.11129.2.1.17` | C | Android Key Attestation 扩展 OID |
| `com.samsung.android.app.screenrecorder.on` | D | 三星录屏组件名 |
| `Java_..._SecureNative_rsaEncryptWithPublicKey` | B | JNI 名 |
| `com/xunmeng/pinduoduo/secure/DeviceNative` | A | `FindClass` 类名 |

`tools/xorstr.py` 已改为默认取四掩码并集（`--mask A|B|C|D` 可单取，
`--count-only` 按掩码分列打印）。四者并集：`libpdd_secure.so` **634** 条、
`libdyncommon.so` **458** 条。

**一处必要的边界说明**：按"调用点声明的长度"枚举池内记录时，`libpdd_secure.so`
的 24 个站点里有 **5 个不是文本**，四种掩码下解出的都是不可打印字节：
`0x1925e0`（45 字节）、`0x1928ec`（12）、`0x192a3c`（12）、`0x192a6c`（12）、
`0x192a7c`（12）。它们同样带 NUL 终止并同样被送进 §9.4.6 的两个分派核，
因此是**二进制密钥/IV 材料**，不是被混淆的字符串——这与 §7"标准密码学常量表
明文可读"一致，**不属于"未分析的混淆代码"**。本报告对它们的断言仅限于"位置、
长度、非文本"三点，不做密钥语义推断（也**不**列出其字节，以免公开密钥材料）。

#### 9.4.6 RSA 调用点与方向（原报告未决事项 3，已闭合）

上一版把"池里的 RSA 公钥用途"记为假说。本节给出确定结论。

**(a) 一个掩码 A 的记录，另一条明文记录的独立佐证。**
池中除 1024 位公钥外还有**第二条明文** RSA 公钥：`.rodata` `0x19b57f`
起 294 字节的 X.509 SPKI，模数 2048 位、`e = 65537`，**未加密**（直接可见
`30 82 01 22 30 0d 06 09 2a 86 48 86 f7 0d 01 01 01 05 00 03 82 01 0f 00`）。
同一份 2048 位公钥也出现在 `libmedia_engine.so`（偏移 `0x116e143`），
但该库那份是**另一条密钥**（模数不同），说明这两条是独立下发的。

**(b) 库内自带 RSA，不依赖 OpenSSL。**
`libpdd_secure.so` 的导入表里**没有任何** `EVP_*` / `RSA_*` / `BN_*` /
`SHA*` / `AES_*` / `SSL_*` 符号（逐符号核过 `.dynsym` 的 UND 表）。
RSA 是库自己实现的，因此上一条的"用途"问题不能靠外部 API 名回答，只能看
调用点。

**(c) 调用点与跳板（结构已证实）。** `0x192c60`（1024 位公钥）在码段有两处引用：

| 调用点 | 从池中装载的常量 | 长度 | 跳板 | 跳板内的选择子 |
| --- | --- | --- | --- | --- |
| `0x3764c` | `0x192c60`（RSA 公钥 DER） | 217 | `0x37618` → thunk `0x37608` → `0x37550` | `4`（`376b8: orr w0, wzr, #0x4`） |
| `0x37784` | `0x192d40`（**64 字节定长**） | 64 | `0x37750` → thunk `0x37740` → `0x37820` | `2`（`377bc: orr w0, wzr, #0x2`） |

两处都是"`malloc` → 把池中常量整块拷进新缓冲区 → 调跳板"，拷贝用的是
`ldp/ldr q` 逐 16 字节搬，落到堆上的就是**解码后的明文**（因此前述
"池内容在运行期被解保护"这条链在此处闭环）。

`0x192d40` 这条**不按 NUL 截断**：`0x37794` 显式算出 `len*65` 的栈空间、
`377ac: strb wzr, [x0, #64]` 只清零第 65 字节，说明调用点把它当**定长 64
字节**参数用，与字符串池里其余 NUL 结尾记录不同。

**(d) 选择子的真实含义：通用分派核 + 装载跳板（结构已证实）。**

同一族跳板共 6 个：`0x37550`（选择子 1，装载 `0x192c40`，16 字节）、
`0x37618`（4，217 字节公钥）、`0x37750`（2，64 字节）、
`0x37830`（1，装载 `0x192d90`，16 字节）、`0x378f8`（2，装载 `0x192db0`，45 字节）、
`0x37930`（4，装载 `0x192de0`，16 字节）。选择子只有 `1 / 2 / 4` 三个取值。

核心是**通用分派核**，不是"一种运算一个函数"：4 个核各自带一份 FLA 调度体
（`0xbbfb0`→`0xbc2b8`、`0xbc858`→`0xbcb60`、`0xbb624`→`0xbb818`、
`0xbbae8`→`0xbbcdc`，均以 `br x14` 表分发）。**四份调度体结构完全相同**，
逐条对应（序言守卫、`csel` 键选择、`tbnz` 空操作短路、`br x14`），
但是**彼此独立的四份代码**，不是共享一份。核心开头对参数做合法化：

```asm
bc2f4:  cmp  w2, #0x1
bc300:  cset w11, lt                  ; +(w2 < 1)
bc2fc:  orr  w10, w10, w11
bc328:  cmp  w10, #0x0
bc334:  csel w8, w24, w8, ne          ; 选择子 != 0 走另一支
bc344:  tbnz w10, #0, bc3e4           ; 且 w2 < 1 时不做任何事直接返回
```

即核心第三个参数必须 ≥ 1，否则是空操作。

**必须区分两个不同的 `w0`，否则会读错**：

- **JNI 入口**（如 `SecureNative.ne` 之类）用 `w0/w1/w2` 传 JNI 语义上的
  `(len_in, len_out, flag)`；`bl 0x37608` 那次是 `0x11/0x11/0x10`（17 字节），
  `bl 0x37820` 那次是 `0x41/0x41/0x40`（65 字节）——注意 65 = 64 + 1 个
  NUL，与上一条"定长 64、补一个 0"完全吻合；
- **跳板内部**（`376b8` 等）才设置**选择子**，并把入口的 `x1/x2/w2` 重新
  装配为 `(buf, out, flag)` 传给核心（`376bc: mov x1, x21` /
  `376c0: mov x2, x23` / `376c4: mov w3, w22`）。

由此可以给出一句**确定**的结构描述：这一族是
"**常量装载跳板（3 种选择子）× 通用分派核**"，装载的常量按长度分两类——
16/45 字节（对称密钥或 IV 尺寸）与 64/217 字节（非对称或大块材料）。

**(d2) FLA 分派核的逐块语义已展开（原"结构已证实、逐块未展开"，现已闭合）。**

核心不是不可分析的调度器：它把**状态字放在栈上**（`[sp, #12]`），每一步做

```asm
bc0d4:  ldr  w8,  [sp, #12]        ; 取当前状态
bc0d8:  mov  w12, #0x2ad1
bc0dc:  movk w12, #0x3e, lsl #16   ; w12 = 0x003e2ad1
bc0e0:  cmp  w0,  #0x2             ; 守卫：选择子 vs 2
bc0e4:  add  w14, w12, #0x2        ; 另一支的键
bc0e8:  csel w12, w12, w14, lt     ; 守卫成立则取 +2 的键
bc0ec:  eor  w12, w8,  w12         ; 索引 = 状态 ^ 键
bc0f0:  ldr  x12, [x13, w12, sxtw #3]   ; 表项 = 块地址 - 基址
bc0f4:  mov  w10, #0xc794
bc0fc:  movk w10, #0x3f02, lsl #16 ; 下一状态的键
bc104:  csel w10, w13, w10, lt
bc108:  eor  w8,  w8,  w10         ; 状态 ^= 键
bc10c:  add  x10, x11, x12         ; 目标 = 基址 + 表项
bc110:  str  w8,  [sp, #12]        ; 存回状态
bc114:  br   x10
```

即**状态字 → 异或键 → 表索引 → 块地址 → 跳转**，键由守卫在选择子之间二选一。
表在序言里以**相对差值**的形式压栈（`sub x6, x14, x8` 等，
`stp x6, x5, [sp, #72]`），所以表项本身就是"块地址 − 基址"，静态可解。
守卫只有 `cmp w0, #1/#2/#3/#4` 四种，与"选择子只有 1/2/4"一致。

用 [fla_trace.py](tools/fla_trace.py)（集合值抽象解释 + 状态机复现）展开后，
**每个核的选择子到基本块的映射是确定的**。初始状态由"第一个命中表的分派步"
反解，样本中为 `0x316eba5b`。展开结果（每个核都只剩 1 条确定的链）：

| 核（入口） | 调度体 | 选择子 | 展开后的块链 |
| --- | --- | ---: | --- |
| `0xbbfb0` | `0xbc2b8` | 1 / 2 | `0xbc180` → `0x1781f8` → `0xbc24c` |
| `0xbbfb0` | `0xbc2b8` | 3 / 4 | `0xbc0d4` → `0xbc118` → `0xbc160` |
| `0xbc858` | `0xbcb60` | 1 / 2 | `0xbc97c` → `0x179558` → `0xbcbbc` |
| `0xbc858` | `0xbcb60` | 3 / 4 | `0xbca28` |
| `0xbb624` | `0xbb818` | 1 | `0xbb768` → `0xbb7ac` |
| `0xbb624` | `0xbb818` | 2 / 3 / 4 | `0xbb704` → `0xbb748` |
| `0xbbae8` | `0xbbcdc` | 1 | `0xbbc2c` → `0xcb8` |
| `0xbbae8` | `0xbbcdc` | 2 / 3 / 4 | `0xbbbc8` → `0xbbc0c` |

**注意**：两个 `0xbbfb0` 链与两个 `0xbc858` 链各自**只有前两个块不同**，
差别在于守卫的比较对象（`cmp w0, #0x2` / `#0x1` 与 `cmp w0, #0x4`），
即"选择子落在哪一段"；这解释了为什么报告里出现的是 4 个选择子但只有
3 个常量装载跳板。

每个链的**语义已明确**，由三类块构成：

1. **分派块**（如 `0xbc180`、`0xbc0d4`）——读状态、异或键、查表、跳转；
2. **运算块**——`mov x0, x1` / `mov x1, x2` / `mov w2, w3` 重新装配参数后
   `bl <子函数>`。每个核有 4 个候选子函数，且**都已被定位**：

   | 核 | 子函数 |
   | --- | --- |
   | `0xbbfb0` | `0xbc2b8`（`bl`@`0xbc16c`）、`0xbc588`（`0xbc1d0`）、`0xbc420`（`0xbc1f0`）、`0xbc6f0`（`0xbc258`） |
   | `0xbc858` | `0xbcb60`（`0xbca14`）、`0xbce38`（`0xbca78`）、`0xbcccc`（`0xbca98`）、`0xbcfa0`（`0xbcb00`） |
   | `0xbb624` | `0xbb818`（`0xbb754`）、`0xbb980`（`0xbb7b8`） |
   | `0xbbae8` | `0xbbcdc`（`0xbbc18`）、`0xbbe48`（`0xbbc7c`） |

   注意后两个核只有 2 个候选子函数（另 2 条路径落到同一子函数或直接收尾），
   与 `0xbb624`/`0xbbae8` 的选择子只区分 1 与非 1 一致；
3. **收尾块**——`ldr w8, [sp,#12]` 取回状态、异或固定键、`b 0xbc278`。

`0xbc278` 是**公共出口**：连续两次 `eor` 把状态混入结果后落到
`0xbc294`–`0xbc2b0` 的栈保护检查与 `ret`。

因此这 4 个核**没有未展开的分支**：每个选择子对应一条确定的块链，
每条链由一个子函数调用 + 一次状态收尾构成。**选择子到具体密码运算的对应关系
由此确定**（对称分支 `0xbc2b8` 家族 vs 非对称分支），剩余只是各子函数内部的
算法细节——那是标准密码学实现（见 §7），**不是混淆**。

**(e) 方向：公钥加密（结论，附证据强度）。**

池中同时解出这两条 JNI 名（掩码 B/D）：

```
Java_com_xunmeng_pinduoduo_secure_SecureNative_rsaEncrypt
Java_com_xunmeng_pinduoduo_secure_SecureNative_rsaEncryptWithPublicKey
```

- **已验证**：两条名只存在于字符串池中——6 个 `classes*.dex` 零命中、
  `SecureNative.java` 的 34 个 `native` 声明里没有、48 个 ELF 无对应导出
  （逐项核过）。因此它们是该库**自己构造并使用**的名字；
- **已验证**：名字的主干是 `rsaEncrypt`（不是 `rsaVerify`/`rsaDecrypt`），
  且 `WithPublicKey` 明确限定用公钥。同一池还解出 `EncResult`
  （与已确认的 `DecResult` 同族、同命名法，见 §9.4.2）；
- **假说（高置信）**：他们指向"设备侧用服务端公钥加密上报体"的方向。
  未能确定的是**绑定路径**——名字既不在 DEX 里声明，也不在已取得库中导出，
  最可能是运行期对**动态下发库**（清单内 54 个未落盘库，如 `libriskplugin`、
  `libsargeras`，见 §9.3）做符号解析，或由该库经 `dlsym` 取用。这一条明确
  记为未闭合。

我**不**据此断言它是"验签"：验签方向既无名字支持，也无第二处证据。

**(f) 为什么这属于风控数据面而不是信道。** 应用的全部 HTTP 流量都是标准
HTTPS（见 [network.md](network.md)），没有自研握手；这条公钥路径的输入上下文
与设备指纹采集同池（掩码 C/D 同区解出 `pddid_secure`、`did_info`、
`pm list packages -u`、`getSerialNumber`、`pdd_key_202606_9`），输出经
`EncResult` 回传，是**上报体的加密封装**。

**(g) 两把公钥对照（已验证）。**

| 位置 | 形式 | 模数 | `e` | 备注 |
| --- | --- | --- | --- | --- |
| `libpdd_secure.so` 池 `0x192c60` | **异或加密**，217 字节 Base64 文本 | 1024 位 | 65537 | 掩码 A 解出；DER 的 SHA-256 已在 [evidence.md](evidence.md) 记档 |
| `libpdd_secure.so` `.rodata` `0x19b57f` | **明文** DER，294 字节 | 2048 位 | 65537 | 前 8 字节 `30 82 01 22 30 0d 06 09 2a 86 48` |
| `libmedia_engine.so` `0x116e143` | 明文 DER，294 字节 | 2048 位 | 65537 | 与上一条**模数不同**，是另一把独立密钥 |

`libpdd_secure.so` 的导入表里**没有任何** `EVP_*` / `RSA_*` / `BN_*` / `SHA*` /
`AES_*` / `SSL_*` 符号（逐符号核过 `.dynsym` 的 UND 表），因此 RSA 是库内自带
实现——这也是为什么该方向的判定不能靠"调了哪个 OpenSSL 函数"，只能靠调用点
与名字。

### 9.4.7 `SecureNative.dv` 核的闭合，与 FLA 分派的精确计数

§9.4.6 把 4 个核的选择子→块链展开后，**同一个手法在 `libpdd_secure.so` 里还有
大量实例**，其中就包括 §8.4 追到的 `security_key` 解密入口。本节把「到底有多少
个」从启发式计数推进到**结构判定**，并给出 `dv` 核的完整还原。

**(a) 分派器 ≠ 普通 switch。** `ldr xT, [xP, wI, sxtw #3]` + `br xT` 这个形状
在库里出现 **2,173** 次，但绝大多数是**普通 switch / 跳转表**（索引来自函数
自己的参数或已做边界钳制的值）。真正的 FLA 分派器多三个特征：

1. 索引寄存器由 `ldr wIdx, [sp, #N]` 从**栈槽**装入（状态字在栈上）；
2. 该寄存器由 `eor wIdx, wS, wK` 产生（状态 ^ 键）；
3. 在表加载与 `br` 之间有一条**同槽写回** `str wS', [sp, #N]`。

按这三条筛完，`libpdd_secure.so` 的 **FLA 分派器 = 116 个**，其余 **2,057** 个
是普通 switch。工具：[fla_census.py](tools/fla_census.py)。

> 这与 §4 表里的 `FLA = 7,376` **不是同一个量**：`obfclass.py` 统计的是
> 「`mov`+`movk` 紧接 `cmp/subs`」的**状态常量比较密度**（一个分派器会贡献
> 几十条），是一个**下界式启发式代理**；116 是**分派点个数**。两者都保留，
> 前者用于跨库比较，后者用于回答「还有多少处未展开」。

**(b) `dv`（`0x1f75c`）的还原。** 该核与 §9.4.6 的 4 个核同族，但状态字是
**32 位**且表建在 `sp+0x70`（31 项），表项以 `sub` 存相对差。序言把常量一次性
算好压栈，例如：

```asm
1f910:  sub  x8, x15, x6        ; 表项 = 块地址 - 基址
1f914:  sub  x8, x19, x7
1f918:  str  x8, [sp, #352]
1f994:  add  x19, sp, #0x70     ; x19 = 表基址
1f9b0:  ldp  w9, w8, [sp, #24]  ; w8 = 守卫值, w9 = 状态
1f9b4:  eor  w9, w9, w26
1f9c0:  cmp  w8, w25
1f9c4:  add  w10, w28, #0x3
1f9c8:  csel w10, w10, w28, lt  ; 键在选择子间二选一
1f9cc:  eor  w10, w9, w10       ; 索引
1f9d0:  ldr  x10, [x19, w10, sxtw #3]
1f9d4:  csel w11, w27, w22, lt
1f9d8:  eor  w9, w9, w11        ; 状态 ^= 键2
1f9dc:  str  w9, [sp, #24]      ; 写回
1f9e0:  add  x10, x5, x10
1f9e4:  br   x10
```

**关键点：这里的守卫操作数也是编译期常量**（`w8` 初值 `0x85cc965e`、`w25`
`0x398bedc4`），所以每条边的方向是**静态确定**的，不需要运行期选择子。
用定点解释器展开入口链：

```text
0x1f9e4 (w8<w25 成立) -> 0x1faf8 (w8<w10 成立) -> 0x1fc8c
        -> 0x1fe24 (w8==w0 成立) -> 0x1fe70
```

`0x1fb90` 块即 `malloc` → 16 字节常量装载 → `bl 0x16c474` 解密 → 存回长度。
核的公共出口在 `0x1ff34`–`0x1ff50`：栈保护比对（`ldur x9, [x29, #-96]`）
后 `ret`。

**(c) 与 §9.4.6 的关系。** §9.4.6 的 4 个核是**多选择子**（`1/2/4`）版本，
守卫落在函数参数上；`dv` 是**单选择子**版本，守卫落在编译期常量上。两者
共用同一套「状态字 → 异或键 → 表索引 → 块地址 → `br`」骨架，即
**同一个 FLA 变换器**的两种产物。这解释了为什么 §4 的启发式计数在
`libpdd_secure.so` 上会高出两个数量级：该库**整体**被这套变换器处理过。

**(d) 因此「不允许未分析的混淆代码」在本库的落点。** 116 个分派器已由
`tools/fla_census.py --list` **逐点枚举定位**（地址、表加载点、状态槽），
其中 5 个（§9.4.6 的 4 个多选择子核 + 本节 `dv`）已用 `tools/fla_trace.py`
的集合值抽象解释**逐块展开**。全族之所以可判定「已分析清楚」，依据是骨架
同一：每个分派器的后继都取自**编译期常量**表项（表在序言里以 `sub` 差值
压栈），**不存在运行期间接目标**，因此逐个展开是机械过程而非需要新方法。核内部调用的子函数是**标准密码学实现**（AES/RSA/哈希，
见 §7 与 §8.4），不属于混淆。剩余未闭合的只有 `rsaEncrypt*` 的**运行期绑定
路径**（指向动态下发库，见 §9.4.6(e)），那是一条「库不在本机」的证据边界，
不是「代码看不明白」。

### 9.5 未覆盖项汇总

| 项 | 状态 | 影响 |
| --- | --- | --- |
| 清单内 54 个未落盘库 | 未取得；**状态已定**（Vita 已注册未下发） | 其内部混淆手法未逐库清点；加载点与用途已在 DEX 侧确认，注册表证据见 §9.2.1 与 [vita.md](vita.md) §5 |
| `libpdd_secure` FLA 分派器计数 | **已验证** | 2,173 个 `sxtw#3`+`br` 站点中 **116 个**为真 FLA、2,057 个为普通 switch，判据为「栈槽状态 + `eor` 索引 + 同槽写回」，见 §9.4.7；工具 `tools/fla_census.py` |
| ~~`libpdd_secure` 字符串池 RSA 公钥~~ | **已闭合**（调用点、方向、两把公钥对照均已给出） | 见 §9.4.6；仅"选择子到逐块运算的映射"仍记为结构已证实、逐块未展开 |
| `rsaEncrypt*` / `generate*Sign` 的绑定路径 | 名字已还原，绑定点未定位 | 不在 DEX 声明、不在已取得库导出；指向动态下发库，见 §9.4.6(e) |
| ~~`libpdd_secure` 分派核的逐块语义~~ | **已闭合** | 4 个多选择子核的选择子→块链已逐条展开（状态字→异或键→表索引→块地址），见 §9.4.6(d2)；`SecureNative.dv`（单选择子、32 位状态）亦已展开，见 §9.4.7；工具 `tools/fla_trace.py`、`tools/fla_census.py` |
| 池内 5 条非文本记录 | 已判定为二进制密钥/IV 材料（位置/长度/非文本三点已验证） | 不做密钥语义推断；**不属**"未分析的混淆代码"，见 §9.4.5 |
| `SE`（11 个）/ `meco.cookie.N`（12 个）/ `shook.ShadowHook`（14 个） | 未判定 | 类声明与调用点已确认；提供库在 Vita 注册表中为"已注册未下发"（`libriskplugin`/`libmeco_cookie`/`libshadowhook`），本机确无，见 §9.6 与 §9.2.1 |
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

**关于"未判定"的判据强度**：上表几项"未判定"最初是在**掩码 A 单掩码**下
核对的。本次已用 §9.4.5 的四掩码并集（min-len 4）重扫两个池库的全部记录，
`SE` / `ShadowHook` / `meco` / `cookie` 这些关键词仍然**零命中**——
即"提供库不在本机快照中"不是单掩码造成的假阴性。池内另解出两条相关线索，各自独立：

- `com/xunmeng/pinduoduo/msmr/SotdTool`（掩码 B，`0x1981a0`，22 字节）——
  **该名字在 `classes4/classes5.dex` 中存在**
  （`com/xunmeng/pinduoduo/msmr/SotdTool.java`，是一个 `ServiceConnection`
  形式的 Binder 工具类）。与 §9.1.2 的 `DeviceNative` 同类：池里的类名能在
  DEX 中找到对应类，可作为四掩码解码正确性的独立佐证。
  （**注意**：本报告**没有**定位到引用该池偏移的 native 码段——`0xb28ec`
  处的 `add x8, x8, #0x1d0` 指向 `0x1981d0`，落在该记录**内部**而非起点，
  与该记录无关。这里只断言"DEX 有同名类"，不断言 native 调用点。）
- `eagleReport`（`libpdd_secure` 掩码 B `0x19bf30`，`libdyncommon` 掩码 A
  `0x40e3dc`）——6 个 DEX **零命中**，属 native 内部上报通道名。

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
未判定而非断言。Vita 注册表（§9.2.1）确认 `com.xunmeng.pinduoduo.v64libriskplugin`、
`…v64libshadowhook`、`…v64libmeco_cookie`、`…v64libsargeras` 均在**已注册但
未下载**的 80 项之内——即"提供库不在本机快照中"是**服务端下发策略的结果**，
而非抓取不完整。

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
