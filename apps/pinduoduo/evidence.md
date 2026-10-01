# 逆向证据

本文件给出可复核的标识、地址与命令。证据等级沿用 `docs/evidence-standard.md`：
**已验证**（字节/哈希级）、**结构已证实**（调用链与字段）、**假说**（调用点归纳）。

## 1. 样本标识

| 项 | 值 | 等级 |
| --- | --- | --- |
| 包名 | `com.xunmeng.pinduoduo` | 已验证 |
| versionName / versionCode | `8.26.0` / `82600` | 已验证 |
| minSdk / targetSdk / compileSdk | `21` / `34` / `36` | 已验证 |
| base.apk 大小 | 26,325,735 | 已验证 |
| base.apk SHA-256 | `d57b1ebcd757207ad569233ec8d7cf663dd1acb69a2fde8915b58af0aa0d7c1c` | 已验证 |
| ABI | `arm64-v8a`（单一 split） | 已验证 |
| DEX | `classes.dex` … `classes6.dex` | 已验证 |
| 可打印字符串总数 | 291,149 | 已验证 |
| Application | `com.xunmeng.pinduoduo.app.PDDApplicationLike` | 已验证 |
| 权限数 | 59（含 33 个第三方 launcher/推送自定义权限） | 已验证 |

## 2. APK 自带 native 库

| 库 | 大小 | SHA-256 前缀 |
| --- | ---: | --- |
| `libpdd_secure.so` | 1,912,832 | `9969a7dba6f8369c…` |
| `libtronav.so` | 2,165,376 | `bc90148204f8eabd…` |
| `libc++_shared.so` | 1,058,904 | `218ecc677aa79e19…` |
| `liblegonative.so` | 505,752 | `aee516a1950f2155…` |
| `libaudio_engine.so` | 350,128 | `a8c81d4750387203…` |
| `libmarsxlog.so` | 128,832 | `8d58e7e7692064ea…` |
| `libmmkv.so` | 128,784 | `cf9be69299923200…` |
| `libgoldarch.so` | 120,488 | `f2b4bd77c20ea178…` |
| `libpcrash_dumper.so` | 105,472 | `8b3f03979868222d…` |
| `libbytehook.so` | 98,888 | `879dd6cad97bc19a…` |
| `libyuv.so` | 96,160 | `e57c90b961a270df…` |
| `libyoga.so` | 93,480 | `6b3b6afcb4e66c11…` |
| `libpcrash.so` | 76,168 | `3318a4767eb0371e…` |
| `libcmtreport.so` | 73,112 | `4e28193ed210e7db…` |
| `libcrashAvoid.so` | 39,464 | `61a5c13d26359743…` |
| `libtronkit.so` | 39,992 | `1ee2cfa57ee5350b…` |
| `libpcrash_anr.so` | 30,624 | `069ea182484d3ec2…` |
| `libxdl.so` | 13,776 | `ff0c3760cf9f2ddc…` |
| `libCSoLoader.so` | 12,480 | `43f71edf07456d86…` |
| `libdokodoor.so` | 9,992 | `aff449ea7574e6ef…` |
| `libpdd_sa_hook.so` | 7,504 | `959740434edf6fda…` |
| `libxlog_api.so` | 5,824 | `67df7492b2ff843a…` |

## 3. assets 内嵌与运行时下载库

三个 `.7z`（LZMA FORMAT_ALONE 压缩的 tar）解出的 ELF：

| 库 | 大小 | SHA-256 前缀 |
| --- | ---: | --- |
| `libstatic-webp_1770901356715_fce07ac1….so` | 106,040 | `63c3043dad468777…` |
| `libtitan_1789549221223_a7390f36….so` | 2,079,080 | `02228b027e994db8…` |
| `libtronplayer_1789704199351_8bcad214….so` | 1,116,056 | `0bcab79a13ef2c35…` |

运行时目录 `files/dynamic_so`：26 个库、77,203,139 字节（含标记文件 74 MB 计），
每个目录含 `lib<name>.so`、`extra_info.json`（117 字节）、
`modified_<epoch>`、`uuid_<hex>`、`version_<x.y.z>`。完整清单见
[storage.md](storage.md) §7。

## 4. `libpdd_secure.so` 关键地址

| 项 | 地址 / 值 | 等级 |
| --- | --- | --- |
| AES S-box | `.rodata 0x19c9dc` | 已验证 |
| AES 逆 S-box | `.rodata 0x19cadc` | 已验证 |
| SHA-256 H0 (LE) | `.rodata 0x19bfb0` | 已验证 |
| MD5 / SHA-1 IV (LE) | `.rodata 0x19db00` | 已验证 |
| Base64 字母表 | `.rodata 0x19cbdc` | 已验证 |
| zlib deflate magic | `0x44760` | 已验证 |
| AEW/ADW 型 AES 轮函数 | `0x16b0e4`（S-box 引用 0x16b134、0x16b150） | 已验证 |
| AES 第二实现 | `0x16b64c`（S-box 引用 0x16b658） | 已验证 |
| AES 解密 | `0x16b924`（逆 S-box 引用 0x16bb18） | 已验证 |
| Base64 实现 | `0x16dc6c`（字母表引用 0x16dd88，10 个调用者） | 已验证 |
| `SecureNative.s` | `0x36278`，派发头 `0x36310`，状态槽 `[sp,#0x14]` | 已验证 |
| `SecureNative.b` | `0x37294`，描述符表 `0x192b71`，条目 32 字节 | 已验证 |
| JNI 导出数 | 34（`.dynsym` 中 `Java_com_xunmeng_pinduoduo_secure_SecureNative_*`） | 已验证 |
| 34 个导出合计指令数 | 21,613（`.text` 共 404,537） | 已验证 |
| `atn` / `csd` / `dsi` | 声明为 native 但**不在** 34 个导出中。判据是两项直接证据：(1) 51 个 ELF 中无任何 `#1720` 注册点登记这 3 个名字；(2) 原始字节搜索 `atn\0`/`csd\0`/`dsi\0` 与 `SE` 独有签名均为 0 命中。注意：不能用"未导入 `RegisterNatives` 符号"来判定，`#1720` 派发式注册本就不产生导入符号，见 [obfuscation.md](obfuscation.md) §9.1.1、§9.6.2 | 已验证（未绑定） |

## 5. Java 侧关键入口

| 主题 | 类 / 方法 |
| --- | --- |
| OkHttp 链 | `com.aimi.android.common.http.f` 静态块 |
| 公共头 | `sv1.d.e(boolean)`、`sv1.d.d()`、`sv1.d.f()`、`sv1.d.g()`、`sv1.d.a()`、`sv1.d.b()` |
| anti-token | `com.aimi.android.common.http.i.a/b/c/e/f` |
| 签名 v1 | `com.aimi.android.common.http.a.e(...)`、`a.c(path)` |
| 签名 v2 | `com.aimi.android.common.http.a.f(...)`、`a.d(path)`、`a.a(path)` |
| 头部落盘 | `com.aimi.android.common.http.h.g(builder, request, flag)` |
| 路径归一化 | `rv1.h.b(url)` |
| GoldenArch | `com/aimi/android/common/http/unity/internal/interceptor/j.java` |
| 上传表单 | `z51.b.r(w51.d)` |
| 上传掩码 | `y51.a` 的 `galerie_upload.anti_token_path` |
| 54001 | `net_adapter/hera/specialcode/riskcontrol/VerifyAuthTokenProcessor`、`iv1.d.a(url)` |
| sdr | `lb2.o0.d(...)`、`lb2.j`、`nb2.a.a()` |
| scres | `lb2.h.y()`、`lb2.o0.e()` |
| Root | `com.xunmeng.pinduoduo.pmm.DefaultRootServiceImpl`、`u43.l`、`v12.a`、`d60.c`、`g2.b.g()` |
| 模拟器 | `gn0.d.c(Context)` |
| 设备画像 | `mi0.a.a(Map)` |
| Efix 跳板 | `h4.g`、`h4.h`、`com.android.efix.load.a.f(...)` |
| 内置配置 | `com/xunmeng/pinduoduo/arch/config/newstartup/ConfigInitializerV2`、`ij0.b` |

## 6. `assets` 关键文件

| 文件 | 大小 | 内容 |
| --- | ---: | --- |
| `assets/A94/CDA` | 129 | 明文 JSON 元数据 |
| `assets/A94/A25` | 150,688 | AES 密文，MD5 `be6fb23b2cb573032c6b2703c8017f37`，熵 7.9988 bit/byte |
| `assets/so_arm64-v8a/*.7z` | — | LZMA tar，内嵌 ELF + 内嵌 MD5 校验 |

## 7. 本地数据快照

| 项 | 值 |
| --- | --- |
| 数据库数 | 22（全部 `SQLite format 3`） |
| MMKV 文件数 | 397（约 19 MB；第二次取证为 396，差 1 个属运行时增删） |
| SharedPreferences | 12 个 xml |
| 应用数据目录合计 | 约 157 MB |
| `files/dynamic_so` | 26 个目录，77,203,139 字节 |

代表性行数（只读重放 WAL 之后）：

| 库 | 表 | 行数 |
| --- | --- | ---: |
| `MsgDB_<UIDMD5>` × 8 | 全部表 | 0 |
| `MsgboxDB_V2_<UID>` | `conversation` | 0 |
| `pdd.db` | `t_notification` | 0 |
| `<UID>.db` | `t_mall*` | 0 |
| `vita-database` | `VitaAccessInfo` / `VitaVersionInfo` | 39 / 42 |
| `iris_downloader_main_v12.db` | `irisCallerInfo` / `irisStartInfo` | 2 / 111 |
| `okdownload-breakpoint.db` | `breakpoint` / `block` / `taskFileDirty` | 2 / 2 / 3 |
| `event_data.db` | `event_data` | 2 |

`MsgboxDB_V2_<UID>` 的 WAL 头：`magic=0x377f0682`、`page_size=4096`、
`seq=151100134`、10 帧、覆盖页 `1,2,3,3,1,2,4,5,6,7`。

### 7.1 组件框架（Vita）取证

只读取证，组件二进制**不出设备**，只在设备上跑 `md5sum`/`sha256sum` 后把
**哈希与元数据**带回：

| 项 | 数量 | 核对结果 |
| --- | ---: | --- |
| `files/.vita` 组件目录 | 46 | 170 个文件全量 MD5 实测 |
| `.md5checker` 声明记录 | 146 | **134 通过**；12 条不匹配**全为** `extra_info.json`（可变元数据） |
| So 载荷 | 23 | **23/23 全部通过**（`.vita` 10 + `dynamic_so` 13） |
| `files/.newLocker` | 183 | 反解出 126 个组件 ID；4 个版本锁；2 个未解析 |
| `vita_local_comp_v2`（MMKV） | 46 条 | 与 `.vita` 目录数一致 |

样本 `libtronavx.so`（4,000,176 字节）：

| 校验 | 值 |
| --- | --- |
| MD5 | `5b122de57929bae97add77d386799431` |
| SHA-256 | `71d88a54ca242206c039e5f8e9b60903b9f3004b35b5067f0fb0c271715f7592` |
| 一致的三处 | `md5checker` 条目 / 目录名末段 / `extra_info.json.md5` |

`vita-database` 行数**必须连同未 checkpoint 的 WAL 读取**：
`UriInfo` 0→3、`VitaAccessInfo` 39→43、`VitaVersionInfo` 42→46、`VitaCleanInfo` 0。

DEX 侧硬编码 RSA 公钥（X.509 SPKI，全部为验签公钥）：

| # | DEX | 位宽 | DER 字节 | SHA-256(DER) 前 16 位 |
| ---: | --- | ---: | ---: | --- |
| 1 | `classes.dex` | 1024 | 162 | `f0a5723ee90872b1…` |
| 2 | `classes.dex` | 1024 | 162 | `c9daed6d9bfbbabd…` |
| 3 | `classes3.dex` | 1024 | 162 | `0ad4904c26c83028…` |
| 4 | `classes3.dex` | 1024 | 162 | `9f70fdf2f9b774fa…` |
| 5 | `classes3.dex` | 1024 | 162 | `cb5ef78dcd0427f7…`（Vita 组件验签） |
| 6 | `classes3.dex` | 2048 | 294 | `1a40e8d0a36b8a5f…` |

## 8. 复现方式

分析全部在离线状态完成：

```text
unpack base.apk                   -> classes*.dex, lib/arm64-v8a/*.so, assets/
jadx -d jadx-out apk/base.apk     -> 25,156 个 Java 文件
readelf --dyn-syms -W <lib>       -> JNI 导出与符号表
aarch64-linux-gnu-objdump -d <lib>-> .text 反汇编
7z x assets/so_arm64-v8a/*.7z     -> 内嵌 ELF
```

本地数据部分只使用**只读**操作：列出目录、读取文件、复制库与 WAL 到临时目录后以
`mode=ro` 打开并 checkpoint。全程未注入目标进程、未附加调试器、未读取目标内存、
未重启应用、未修改任何设备文件，因此不产生可被检测的行为。

各工具随报告发布在 [tools/](tools/)，共 11 个，全部只读、只用标准库、不含样本秘密：

| 工具 | 作用 | 产出 |
| --- | --- | --- |
| [obfscan.py](tools/obfscan.py) | ELF 形态 + 常量表 + 字符串 + JNI 导出 | 逐库密码学命中表 |
| [obfclass.py](tools/obfclass.py) | `indbr`/`adrret`/`cffstate`/`.inst%` 指纹 | 逐库混淆分类 |
| [unflatten.py](tools/unflatten.py) | FLA 状态机 → 基本块图 | `SecureNative.s` 的 67 状态还原 |
| [cff4.py](tools/cff4.py) | 按真实函数收尾定界的逐导出规模 | 34 个导出共 21,613 条指令 |
| [native_closure2.py](tools/native_closure2.py) | DEX 声明 native ↔ 各库导出符号 | 646 已解析 / 966 未解析 |
| [jnibind.py](tools/jnibind.py) | 绑定方式三分（导出 / `RegisterNatives` / 未判定） | 646 / 87 / 879（按必要条件口径） |
| [rnbind.py](tools/rnbind.py) | `RegisterNatives`/`UnregisterNatives` 真实调用点（排除 `.plt` 桩与 packed-offset 派发器）；`--all` 列出被排除项 | 28 / 1（18 个库，29 处）；`nMethods` 逐站点反汇编复核见 [obfuscation.md](obfuscation.md) §9.1.1 |
| [xorstr.py](tools/xorstr.py) | `libpdd_secure.so` + `libdyncommon.so` 异或字符串池（四掩码并集；自校验：掩码 A 从文件自身密钥表推导） | 634 条 / 458 条，全部还原 |
| [alipay_map.py](tools/alipay_map.py) | 支付宝 SDK `AD`/`AL` 编码 → 采集函数 | 41 / 41 全部定位 |
| [so_manifest2.py](tools/so_manifest2.py) | `SoBuildInfo` 清单 ↔ 设备落盘 ↔ 加载点 | 199 条：22 APK / 26 落盘 / 54 未落盘 |
| [db_snapshot.py](tools/db_snapshot.py) | 只读 SQLite 快照（含 WAL 重放），只输出列形状聚合，不输出行值 | 22 个库的 DDL、行数、列形状 |

输入约定：`rnbind.py <lib.so|dir>` 直接反汇编并报告调用点（`--all` 附带被排除的
假阳性）；`xorstr.py <lib.so|dir>` 解出字符串池（默认四掩码并集；`--min-len` 调最短
长度，`--count-only` 按掩码分列打印条数，`--mask A|B|C|D` 单取一个掩码，
`--key` 覆盖为任意 8 字节密钥）；
`alipay_map.py <jadx-sources>` 从反编译源码重建编码映射；
`obfclass.py` 需要反汇编文本（`objdump -d`）与输出 JSON 两个参数；
`cff4.py` 读取工作目录下的 `libpdd_secure.dis`；`native_closure2.py`、
`jnibind.py`、`so_manifest2.py` 从 `jadx-out/sources`、`unpack/lib/arm64-v8a`、
`unpack/assets-so`、`evidence/runtime-so`、`evidence/dynso/dynamic_so` 读取；
`db_snapshot.py <src-dir> <out.txt>` 直接对目录做只读快照。

### 8.1 设备端复验（第二次独立取证）

本节记录对同一台取证设备的**第二次只读取证**，用于确认报告中设备侧结论不是
一次性快照的偶然结果。全部操作仍是只读（列目录、读文件、`tar` 到本地后分析），
未注入、未调试、未读进程内存、未重启应用、未修改设备任何文件。

**样本同一性（关键前提，已验证）**：设备上 `base.apk` 的
`sha256 = d57b1ebcd757207ad569233ec8d7cf663dd1acb69a2fde8915b58af0aa0d7c1c`、
大小 26,325,735 字节、`versionCode=82600` / `versionName=8.26.0`、
`lib/arm64` 下 22 个 `.so`——与 [README.md](README.md) 记录的分析样本**逐项一致**。
因此本报告对 APK 的全部静态结论直接适用于该设备上正在运行的这一版。

**数据库结构一致性（已验证）**：把 `databases/` 下 66 个文件（22 个库 + 伴随
文件，合计 1.9 MB）只读取回后重放 WAL 并快照，与报告 §2 的清单逐库逐表比对：

| 比对项 | 结果 |
| --- | --- |
| 库数量 | 22（与 §1 一致） |
| 表数量 | 89 |
| **DDL 文本** | **逐字节相同**，无一条新增/删除/改名 |
| 行数差异 | 仅 6 处计数变化，全部是使用量增长（见下） |

行数差异全部落在"会随使用增长"的表上，没有出现任何结构变化：

| 库 / 表 | 首次取证 | 第二次取证 |
| --- | ---: | ---: |
| `iris_downloader_main_v12.db` / `irisStartInfo` | 111 | 112 |
| `okdownload-breakpoint.db` / `block` | 2 | 1 |
| `okdownload-breakpoint.db` / `breakpoint` | 2 | 1 |
| `okdownload-breakpoint.db` / `taskFileDirty` | 3 | 4 |
| `vita-database` / `UriInfo` | 0 | 3 |
| `vita-database` / `VitaAccessInfo` | 39 | 43 |
| `vita-database` / `VitaVersionInfo` | 42 | 46 |

`vita-database` 的主库也从 49,152 增长到 57,344 字节（WAL 从 416 KB 重放而来），
与"vita 是组件/路由的增量登记库"这一判断一致（见 [storage.md](storage.md) §2.5）。

**动态库逐字节同一性（已验证）**：设备 `files/dynamic_so` 下 26 个 `.so` 的
**总字节数 77,200,448**，与本报告分析所用副本的合计**逐字节相同**；抽查
`libdyncommon.so`（`00cd567d…`）、`libpdd_rubik.so`（`662ab3e3…`）、
`libmedia_engine.so`（`98209345…`）三个库的 SHA-256，设备副本与分析副本**一致**。
因此 §2、§3 与 [obfuscation.md](obfuscation.md) §9.4 中对这些库的结论直接适用于
设备上运行的那一份——这排除了"设备上的库与分析的库不是同一份"这一最根本的
有效性风险。

**其余存储面复核（已验证）**：`files/mmkv` 396 个文件 / 19,931 KB（首次取证为
397，差 1 个属运行时增删，非结构差异）；
`shared_prefs` 12 个 XML；`files/dynamic_so` 27 个条目（26 个库目录 + 1 个
`buildInSoFix.config`）/ 75,687 KB；`files/secure` 仅 `p29_info.cache`；
`files/network` 为 `pnet` + `titancache`；`no_backup` 为空。均与
[storage.md](storage.md) 的记载一致。

**结论**：设备侧结论（库数量、DDL、信息范围）在两次独立取证间**稳定**，
唯一变化是随使用增长的行数。这使 [storage.md](storage.md) 从"一次性快照"
升级为"经两次独立只读取证交叉确认"。

## 9. native 声明与库的归属

`native_closure2.py` 在 187 个类中发现 **1,612** 个原生方法声明，按提供方分类：

| 提供方 | 解析数 | 主要库 |
| --- | ---: | --- |
| APK 自带 | 203 | `libmmkv.so` 59、`libaudio_engine.so` 49、`libpdd_secure.so` 31、`libmarsxlog.so` 20、`libcmtreport.so` 11、`libcrashAvoid.so` 8、`libgoldarch.so` 7、`libbytehook.so` 5、`libdokodoor.so` 1 |
| assets 内嵌 | 86 | `libtitan.so` 86 |
| 运行时下载 | 357 | `libmedia_engine.so` 109、`libpnn.so` 30、`libpnet.so` 26、`libaudio_engine_ext.so` 18、`libavif_android.so` 14、`libprobe.so` 13、`libpdd_pnn_plugins.so` 12、`libaegis.so` 6、`libfdk_aac.so` 2、`libmedia_engine_ext.so` 2 |
| 未解析 | 966 | 见下 |

未解析的顶部类及其归属（库不在本机快照中，或使用 `RegisterNatives`）：

| 类 | 声明数 | 归属 |
| --- | ---: | --- |
| `com.eclipsesource.v8.V8` | 97 | `libpdd_j2v8.so`（112 个 `Java_com_eclipsesource_v8_V8__*` 导出；因方法名以数字开头，JNI 名带 `_1` 转义，需按 `_1`→`1` 归一化才能匹配） |
| `com.tencent.mmkv.MMKVV2` | 81 | `libmmkv_v2.so`（0 个 `Java_` 导出，走 `JNI_OnLoad` 注册） |
| `com.xunmeng.effect.render_engine_sdk.EffectJniBase` | 60 | `libmedia_engine.so` 家族 |
| `com.facebook.yoga.YogaNative` | 58 | `libyoga`（随 `libmedia_engine` 分发） |
| `com.media.tronplayer.TronMediaPlayer` | 38 | `libtronplayer.so`（assets 内嵌 `libtronplayer.7z`，本机未解开） |
| `com.xunmeng.pinduoduo.shook.ShadowHook` | 14 | `libpdd_sa_hook.so` / `libbytehook.so` 之外的独立静态库 |
| `org.aomedia.avif.android.AvifDecoder` | 14 | `libavif_android.so` |
| `com.xunmeng.sargeras.*` | 200+ | `libmedia_engine.so` 子模块 |
| `xmg.mobilebase.lego.c_m2.VM*` | 60+ | `libScriptBind.so` |

未解析的根因共四类，全部可解释，**不含"符号被故意抹除"的情况**：

- **（a）JNI 名转义**：方法名以数字/下划线开头时符号里被改写为 `_1`/`_2`/`_3`。
  例如 `com.eclipsesource.v8.V8` 的 88 个方法以 `_1` 开头（`V8__1_1release` 等），
  按转义归一化后可与 `libpdd_j2v8.so` 的 111 个 `Java_*` 导出对上。
- **（b）`RegisterNatives` 动态注册**：库不导出 `Java_*`，在 `JNI_OnLoad` 里用
  `JNIEnv` 函数表注册。判定口径为**函数表下标**（`#1720` = `RegisterNatives`、
  `#1728` = `UnregisterNatives`）**并叠加两条排除判据**：调用点不在 `.plt` 段内
  （`.plt` 以同样步长走 GOT 槽，每个导入符号都会命中一次该位移），且该寄存器在
  `ldr` 之后未经任何算术直达 `blr`/`br`（本 app 自己的 FLA 派发器是
  `ldr → add → blr`）。按此口径：48 个去重 ELF 中共 **28 处注册 + 1 处注销，
  分布 18 个库**（其中 APK 自带的 22 个库内为 14 次注册）。
  "方法名串 + 签名串 + `JNI_OnLoad`"只是必要条件，不构成调用点证据；
  仅按函数表下标、不排除 PLT 桩与 FLA 派发器，会把计数抬到"32 处 / 20 个库"
  （含同一 ELF 的重复副本）。必须按上述 (a)(b) 双判据折算到去重后的 ELF 集。
  见 [obfuscation.md](obfuscation.md) §9.1.1。
- **（c）库不在快照中**：`SoBuildInfo` 清单列出的 199 个动态库中，本设备只落盘了
  22（APK）+ 26（`files/dynamic_so`）+ 3（assets 内嵌）个，其余 54 个从未下载
  （`libsargeras`、`libchat_msg`、`libgiflib`、`libmeco_cookie`、`libxunwind` 等）。
  这类方法占未解析的绝大多数。
- **（d）包名与库名不一致**：已落盘的 `libpdd_sa_hook.so` 明确只提供
  `sensitive_api_hook.SensitiveApiHook` 的 3 个方法（`init`/`isDebugNative`/
  `setEnableNative`，`.rodata` 中同时含 `libbytehook.so` 与 `libpdd_sa_hook.so`
  两个自身名，并调用 `bytehook_hook_partial`）。`shook.ShadowHook` 的 14 个方法
  在调用点 `shook/ShadowHook.java:149` 处 `loadLibrary("shadowhook")`，因而应归属
  清单中的 `libshadowhook.so`（**假说**：该库未落盘，无法用符号直接确认）。归属
  需按组件 ID 而非类名前缀判断。
