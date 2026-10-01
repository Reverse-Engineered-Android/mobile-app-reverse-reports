# 小红书 9.37.0 深挖证据索引

每个关键结论对应到具体文件与地址。表中“本地路径”指逆向工作副本内的相对路径，公开仓库不包含这些文件本体。

## `libxyass.so`（597 016 字节，SHA-256 `8e7db9e41ec7cacaf504fa07e68a325aa1bae220cabb5a745d31e3154922a17b`）

| 地址 | 符号/函数 | 说明 | 等级 |
| ---: | --- | --- | --- |
| `0x3f484` | `JNI_OnLoad` | 取 Application、解字符串、注册 native | 已验证 |
| `0x3f6f8` | `RegisterNatives` | `JNINativeMethod[4]` | 已验证 |
| `0x45764` | `intercept` | `(Lokhttp3/Interceptor$Chain;J)Lokhttp3/Response;` | 已验证 |
| `0x49634` | signer | 输出 `{u32 type, payload[≥64B]}` | 已验证 |
| `0x467dc` | assembler | 按 token type 分派装配 `shield` | 已验证 |
| `0x46a14` | type 6/7 调用点 | 调用 `0x50010` / `0x5a128`，结果写入 `0x28` 长度前缀的 `std::string` | 已验证 |
| `0x46cf0`-`0x46d0c` | 输出写回 | `mov w9,#0x28; strb w9,[x10]`（长度前缀 `(`）+ `stur q0,[x10,#1]` 写 16 字节 + `strb wzr,[x10,#0x15]`；失败路径写 0x20 个空格 | 已验证 |
| `0x4b3d0` | 外层装配入口 | 组装 `P` → RC4 → 16B 头 → Base64 → `NewStringUTF` | 已验证 |
| `0x4b658`-`0x4b9e4` | RC4 KSA/PRGA | S 盒栈上构建，key 长 13 硬编码 | 已验证 |
| `0x7f094` | 摘要容器 | 三阶段摘要状态 | 已验证 |
| `0x7f224` | key setup | 取 token payload 前 64 字节 | 已验证 |
| `0x7feec` | update | 请求字节流输入 | 已验证 |
| `0x8001c` | final | 输出 16 字节摘要 | 已验证 |
| `0x50010` | type 6/7 会话变换 | `(payload*, 64, key*, 20, out*)` → 16 字节 | 已验证 |
| `0x552f0`/`0x55b64`/`0x569c8`/`0x57320`/`0x580dc`/`0x58858`/`0x590a0`/`0x5980c` | 8 个算法块 | 跳转表目标，各自独立栈帧 | 已验证 |
| `0x557c4` / `0x557d8` | CFF 分派 | `ldur` + `ldr x8,[x8,x9,lsl #3]; blr x8` | 已验证 |
| `0x55b2c` | 状态字节读 | `ldrb w9, [x20]`（`x20` 为栈帧） | 已验证 |
| `0x55b58` / `0x55b5c` | 块索引计算与保存 | `and x9,x9,#3` / `stp x9,x10,[x29,#-0x28]` | 已验证 |
| `0x91868` | 8 项跳转表 | 指向上面 8 个块 | 已验证 |
| `0x59f84`-`0x5a124` | 16 个字节置换器 | 16/20/28 字节；读 `x0[i]` 与常量/下标混合后写 `x0[i+0x14]`；含 `0x96=~0x69` 互补对与 `0x5a0d4`/`0x5a0e8` 重复体 | 已验证 |
| `0x5a128` | 会话上下文刷新 | `strb w1,[x0,#0x13]`，键字节 `0x33`/`0xb9`… | 已验证 |
| `0x5a264` | type 7 入口（带时间字节） | 6 个 callee-saved 寄存器 + `1<<12` 栈 | 已验证 |
| `.rodata:0x13d9e` | RC4 key | ASCII，13 字节（abort 消息串复用） | 已验证（不复现取值） |
| 全域 | 头部名字符串 | `x-s`/`x-t`/`shield`/`xy-platform-info` **各 0 命中** | 已验证 |

### 区域统计（`0x50010`..`0x5a264`）

- 41 556 字节 / 10 389 条指令 / 26 个 `ret` 结束的叶子函数。
- 504 处寄存器间接跳转（`br`/`blr`）。
- 唯一 `bl` 目标：`0x8aed0`（PLT 桩，5 处）。
- AES/SHA1/SHA256/SHA512/SM3/SM4/CRC32/PMULL 指令：**各 0 条**。
- rodata 对象引用：**29 个**（16 张 16 字节序列表 + 13 个常量对象；`.rodata` 范围 `0x12de0`+`0x5d50`）。
- 表引用形式：全部为整块向量加载（`ldr qN` 16 字节 / `ldr dN` 8 字节）；`tbl`/`tbx`/`ld1 {vN.16b}` 索引查表 **0 处**。
- 动态实测（全零输入）：`.rodata` 访问 88 次，全部 size=8，来自 30 个 PC，size=1 的访问 **0 次**。
- 动态可达性：`.rodata` 读钩子下，16 张表在两组输入中**全部被真实读取**（各 1 次）。
- 区域**不引用** K 表族（`0x94710`/`0x94810`/`0x94910`/`0x94a10` 共 4 张 64 字表，服务定制 H type 1–5（type 1/2 复用）；`0x94710` 与未修改 MD5 T 表 64/64 一致，另三张分别 6/64、3/64、3/64 槽相同）、定制 H init 的 IV（`0x15ca0`，由 `0x81978` 读取）、`0x4c0c8` 使用的标准序 IV（`0x15d40`）与 SHA-1 `H4`（`0x15e70`）；`0x15000` 起 libc++ 字符串区不在引用清单内。
- 修正说明：早前“rodata 常量表引用 0 处”的结论**已撤回**，原因见 [crypto.md](crypto.md) §4.2。

## `libtiny.so`（7 795 072 字节，SHA-256 `b403a883b6bba843197fe076deb332b71be5c74c442781b26c89be69de21f6fd`）

| 地址 | 说明 | 等级 |
| ---: | --- | --- |
| `0x18afd8` | `JNI_OnLoad` | 已验证 |
| `0x15e9f4` | 模拟器捕获的 native handler | 已验证 |
| `0x16b08c` / `0x17cdb0` | opcode `0x96f7fcac` 的**两个 CFF 重复比较块**（非二叉比较） | 已验证（见 [tiny-and-app-sweep.md](tiny-and-app-sweep.md) §4）|
| `.data 0x754AC0`-`0x7701C0` | 112 384 字节高熵区，熵 7.9982945 | 已验证（见下） |
| `0xf7b80` | 普通字符串常量拷贝（非解密） | 已验证 |
| `0x4948A8` | `adrp/add/ldr` 序列 | 已验证 |
| `0x53296C` | `memcpy` | 已验证 |

高熵区三路引用扫描（`adrp` 目标 / `mov` 立即数 / 重定位加数）**合计 0 命中**；区域内容 SHA-256 `706dbd99b5d75c8ba5529a538f8e9ae86fe4a355d6c7fb39a1c454dbefb4ff62`。
结论：**未发现静态或已覆盖动态路径引用的高熵数据区**。

## `libtinyd.so`

fork / syslog / abort-message 特征支持独立守护进程判断（`assets/fd2x1e4e2x3f1v2b1s.dex` 内 `com.xingin.tiny.daemon.*` 与之配套）。

## 其他 JNI 表

| 文件 | 位置 | 说明 |
| --- | --- | --- |
| `libsecurebase.so` | `JNI_OnLoad 0xd13c`，表 `0x38000` | 2 个动态注册方法；登录态 KV 文件名派生与定制哈希 |
| `libeidjni.so` | 20 个 `Java_com_eidlink_jni_EIDReadCardJNI_*` | NFC/eID SDK |
| `libturingmfa.so` | `JNI_OnLoad 0x1fcc4`，表 `0x56540` / `0x56690` | 14 + 1 个动态注册方法 |

## 网络与协议证据

| 结论 | 位置 |
| --- | --- |
| 主 API 域名 | `it6/h.java:50` 的 `nbc.i.h("").n("edith.xiaohongshu.com", "https://edith.xiaohongshu.com")` |
| `xy-common-params` 写入 | `it6/h.java:31,39`（另有 `omb/c.java:33`） |
| 参数定义（36 字段） | `z2c/e.java`（`ApiArgumentsProviderImpl`） |
| 注解映射 | `fvc/f.java`(GET)、`fvc/o.java`(POST)、`fvc/t.java`(Query)、`fvc/c.java`(Field) |
| 上传端点全集 | `com/xingin/uploader/api/internal/TokenService.java` |
| 风控端点 | `com/xingin/account/net/api/IRiskService.java` |
| 设备端点 | `IDeviceService`（路径无前导斜杠） |
| 验证页 UA | `ValidateActivity.java:75` `" XHS/3.0.0 NetType/" + i0.d()` |

## 上传证据

| 结论 | 位置 |
| --- | --- |
| 令牌选择逻辑 | `api/internal/o.java:56-69`（`A()`） |
| 去重调用 | `api/internal/o.java:322`（`quickUploadStr(...)`） |
| 去重算法字面量 | `UploaderFlow` 的 `z(..., "md5", …)` |
| 全文件 MD5 | `api/internal/c.java:22`（`MessageDigest("MD5")` 全文件流式） |
| 分块公式 | `api/x.java:54`（`c(long)`） |
| Qiniu 配置与覆盖 | `api/c0.java:144` |
| 断点记录键 | `api/d0.java:17`（`chunkSize + "_._" + reverse(path)`） |
| 记录文件命名与过期 | `com/qiniu/android/storage/persistent/FileRecorder.java`（SHA-1 命名，48 h） |
| COS 整对象 PUT | `j2b/n.java:51` 起 |
| MIME 表 | `qka/a.java`（34 项，无视频/webp/heic） |
| 超时默认值 | `api/u0.java:93-94` |

## 下载证据

| 结论 | 位置 |
| --- | --- |
| 初始 GET 无 Range | `AndroidHttpEngineOptimizer.java:288` |
| seek 用 `Range: bytes=<offset>-` | 同文件 `:404` |
| seek 前缓冲复位 | 同文件 `:380-400` |
| `Content-Length` 解析 | 同文件 `:135-150` |
| seek 后校验 | 同文件 `:431` |
| 服务端 Range 字段 | `l6a/o.java`、`l6a/p.java`（`@mf.c`） |
| 高峰兜底默认值 | `l6a/p.java` 合成构造 |
| HPPlay Range | `com/hpplay/common/asyncmanager/FileRequest.java:195` |
| COS Range API | `com/tencent/cos/xml/model/object/GetObjectRequest.java:230-231` |

## 验证脚本（逆向工作副本 `re/` 内，不随仓库发布）

| 脚本 | 作用 | 当前输出 |
| --- | --- | --- |
| `xyass_hmacverify.py` | 定制 HMAC-H 参考实现断言 | `vectors=3 mismatches=0` |
| `xyass_type67_probe.py` | type 6/7 合成向量 | 见 [crypto.md](crypto.md) §4.5 |
| `xyass_lift50010.py` | `0x50010` 逐指令 trace + 符号化 lift | 单 trace 内自洽；**跨输入 4/4 mismatch，不作参考实现** |
| `xyass_tabscan3.py` | rodata 引用扫描（补 `add` 基址与向量块加载） | 找回被跳过的 `ldr qN` 引用；但含寄存器复用假阳性 |
| `xyass_tabscan4.py` | rodata 引用扫描（寄存器精确，写寄存器即失效） | 区域 29 个 .rodata 对象、161 个总目标（最终采用） |
| `xyass_tabtouch.py` | `.rodata` 读钩子动态可达性 | 3 组输入，表命中如上 |
| `xyass_depmatrix.py` | 字节级依赖矩阵 | 64/64 payload、20/20 key 全域扩散 |
| `xyass_final_vectors.py` | 8 组确定性向量 | 重复执行一致 |
| `xyass_blkchain.py` | 间接分支目标普查 | 337 个目标、283–2861 次 `blr` |
| `xyass_type6_character.py` | 确定性 / 路径 / 输出空间统计 | 全输入确定性成立 |
| `xyass_avalanche.py` | 单比特扩散统计 | 见 [crypto.md](crypto.md) §4.7 |
| `xyass_cfg_saturate_full.py` | CFF 转移图饱和实测（3 000 组、10 个输入族） | **412 site / 767 边 / 417 目标**；末 1 000 组 +0 边、+0 site（饱和） |
| `xyass_sel_lastwriter.py` | 执行序最后写者追踪，定每个 site 的生产者 | 每 site 生产者**唯一**（362 单 / 0 多） |
| `xyass_sel_classify.py` | 选择层按操作数变化性分类 | **FIXED 262 / BASE 69 / DATA 18**（60 组输入） |
| `xyass_sel_predicate.py` | 18 个 DATA site 的判定链提取 | 18/18 为二路分支，16 带显式 `tst`/`cmp`，目标均为立即数 |
| `xyass_inertbits.py` | payload/key 位级影响穷举（×4 基准） | payload **16 个结构性惰性位**（偏移 3、7 的上位）；key 0 个 |
| `xyass_avalanche_fast.py` | 无钩子 oracle 的扩散统计 | payload 穷举 512 位：均值 61.52/128、区间 **0–82** |
| `xyass_cfg_defuse.py` | 递归 def-use 解析每个 `br`/`blr` 目标来源（重写） | 504 个 site 中可达者全为 `add Xd,base,wOff`；**0 个静态常量目标** |
| `xyass_cfg_tables.py` | `.data` 槽位取值 + 跨实例确定性 | 16 槽位跨 3 次全新实例逐字节一致 |
| `xyass_cfg_memstatic.py` | 区域内访存值与文件镜像比对 | 表数据**与文件不同**（`.data` 槽位文件值为 0），静态不可读 |
| `xyass_cfg_siteindex.py` | 逐 site 目标集合与出度 | 110 个 site 出度 ≥ 2，最大 16 |
| `xyass_type67_static.py` | 区域指令族/加密指令普查 | 加密扩展指令 0 条 |
| `xyass_type67_map.py` | 函数边界 / 调用 / 常量集 | 26 函数、1 调用目标、299 常量 |
| `xyass_const_split.py` | 数据常量 vs 地址加数分离 + 已知表比对 | 已知表全 0 命中 |
| `xyass_sel_write.py` | 分派选择子来源定位 | `ldrb w9,[x20]` / `and #3` |
| `xyass_sel_dep.py` | 单字节扰动下的选择子变化 | 选择子可达 {0,1,3}，与路径无关 |
| `tiny_blobref3.py` | 高熵区引用三路扫描 | `total: 0` |

## 证据等级定义

| 等级 | 含义 |
| --- | --- |
| 已验证 | 有源码/反汇编行 + 可复现脚本或向量，结论可直接复核 |
| 结构已证实 | 结构、位置、参数确定，但取值或运行路径需运行时环境 |
| 未闭环 | 已定位待办的具体环节，并写明推进条件 |
