# 算法与 native 层

对象是 `libpdd_secure.so` 与它周边的 native 组件。全部结论来自静态字节比对与调用图，
不含内存读取、不含注入、不含调试。

## 1. `libpdd_secure.so` 基本形态

| 字段 | 值 |
| --- | --- |
| 大小 | 1,912,832 |
| 格式 | ELF64 AArch64，已 strip（无 `.symtab`），有 `.dynsym` |
| `.text` | 0x18b768 = 1,619,816 字节 |
| 指令总数 | 404,537 |
| 可打印字符串 | 337 |
| JNI 导出 | 37（`com.xunmeng.pinduoduo.secure.SecureNative`） |

## 2. 密码学常量表（字节级已验证）

| 常量 | 位置 | 起始字节 |
| --- | --- | --- |
| AES S-box | `.rodata 0x19c9dc` | `637c777bf26b6fc53001672bfed7ab76` |
| AES 逆 S-box | `.rodata 0x19cadc` | `52096ad53036a538bf40a39e81f3d7fb` |
| SHA-256 H0 (LE) | `.rodata 0x19bfb0` | `67e6096a85ae67bb72f36e3c3af54fa5…` |
| MD5 / SHA-1 IV (LE) | `.rodata 0x19db00` | `0123456789abcdeffedcba9876543210…` |
| Base64 字母表 | `.rodata 0x19cbdc` | `ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef…` |
| zlib deflate magic | `0x44760` | `78 9c` |

全镜像中**恰好 2 张** 256 字节置换表，即 AES 正/逆 S-box 这一对。

### 2.1 阴性结果（已搜索，不存在）

SM4 S-box、SM3 IV、ChaCha20/Salsa20 sigma、SHA-512 H0、Curve25519/Ed25519 常量、
P-256 / secp256k1 参数、Poly1305 r、CRC32/CRC32C 表、DES 表。

结论：`libpdd_secure.so` 内**没有国密、没有流密码、没有公钥密码**，分组密码只有 AES。

### 2.2 AES / Base64 使用点（adrp+add 交叉引用）

| 地址 | 所在函数 | 用途 |
| --- | --- | --- |
| `0x16b134`、`0x16b150` | `0x16b0e4` | 正向 S-box，4 路字节代换 + `sdiv`/`msub` 行移位 → AES 轮函数 |
| `0x16b658` | `0x16b64c` | 正向 S-box |
| `0x16bb18` | `0x16b924` | 逆 S-box（单一引用） |
| `0x16dd88` | `0x16dc6c` | Base64 字母表，10 个直接调用者 |

调用关系：`AES_enc@0x16b0e4 ← 0x16b300`；`AES_enc2@0x16b64c ← 0x16b4a4`；
`AES_dec@0x16b924 ← 0x16c61c`。

## 3. 导出 → 密码学能力映射（调用图，深度 24）

| 类别 | 导出 |
| --- | --- |
| 可达 AES | `ae`、`aew`、`eca`、`ecb`、`ecn`、`egv`、`enc`、`hf`、`ne`、`ng`、`dec`、`dv`、`dcc`、`ad`、`adw` |
| 可达 Base64（另加） | `ea`、`eb4`、`mhk`、`ale`、`alm`、`sdr` |
| 不触及密码学 | `b`、`cps`、`cr`、`gal`、`glk`、`gvv`、`itst`、`itst2`、`rs`、`s`、`sb`、`ng2` |

`ng2` 可达节点数 496，是体量最大的导出，且不含任何密码学常量引用 —— 纯设备/元信息
生成器。

## 4. Java 侧语义

`com.xunmeng.pinduoduo.secure.SecureNative` 的包装层：

| Java 方法 | native | 语义 |
| --- | --- | --- |
| `r(byte[])` | `eca` | 字节 → Base64 风格字符串 |
| `y(byte[])` | `ecn` | 字节 → 字符串（另一套编码） |
| `e(String)` | `r(str.getBytes())` | 串 → 编码串 |
| `z(byte[])` | `dv` | 字节解码 |
| `t(byte[], byte[])` | `aew` | 双输入变换 |
| `x(byte[], byte[])` | `adw` | 双输入变换 |
| `u(a, b, c)` | `ad` | 三输入变换 |
| `m(a, b, c)` | `ae` | 三输入变换 |
| `l(a, b)` | `re` | 双输入变换 |
| `h(byte[])` | `ng` | 字节 → 串 |
| `g(JSONObject)` | `ne` | JSON → 串 |
| `f(str, int)` | `glk` | `getLoginKey`（无密码学） |
| `q(String)` | `egv` | 串 → 串 |
| `i(...)` | → `SE.as(...)` | v1 API 签名 |
| `j(...)` | → `SE.ts(...)` | 签名（带 `rctk` → `sctk` 改名） |
| `n(long)` | → `SE.ues(j)` | 错误串 |
| `s(int)` | `s` | 选择子 → 材料（FLA 状态机） |
| `b(Context, Long)` | `b` | 时间戳 + 选择子 → anti-token 长 token |
| `p(Context, Map)` / `v(Context, Map)` | `ng2` 家族 | 按 `data_type` 分派的 extras 生成 |
| `sdr(Context, Map)` | `sdr` | 签名描述符 |
| `enc(byte[], int)` / `dec(String)` | `enc` / `dec` | 结构化加解密（返回 `EncResult`/`DecResult`） |
| `mhk(obj, sdkInt, s1, s2)` | `mhk` | 环境相关串 |
| `itst()` / `itst2()` | `itst` / `itst2` | 初始化（无返回值） |
| `sb(List<String>)` | `sb` | 下发列表 |
| `cr()` | `cr` | root 裁决 |
| `rs()` | `rs` | long 状态值 |
| `gvv()` | `gvv` | int 状态值 |
| `gal(Context, Map)` | `gal` | 上下文相关对象 |

`SecureNative.i/j` 会先调 `w()` 做 `SE` 绑定，再转发到 `SE.as`/`SE.ts`；`SE` 是
另一套导出（10 个 native），属于同一个 native 库。

## 5. 选择子体系（`s(int)` / `b(Context, Long)`）

### 5.1 结构

- `SecureNative.s(int)`：0x36278–0x37290，1,030 条指令，67 个 FLA 状态，反扁平化
  后为普通基本块图（见 [obfuscation.md](obfuscation.md) §8.2）。
- `SecureNative.b(Context, Long)`：0x37294–0x373bc，74 条指令，按 `sel` 索引
  `0x192b71 + sel*32` 的 32 字节描述符表后转发。

### 5.2 选择子取值（由调用点归纳，结构已证实）

`qb2.d.b()` 返回 `qb2.c` 的实现 `lb2.h`，`a(int)` → `SecureNative.s(int)`，
`v(int)` → `SecureNative.b(int)`。调用点穷举：

| 选择子 | 调用点 | 用途 |
| --- | --- | --- |
| `0` | `bx1/g.java:174` | AES IV |
| `1` | `z21/p.java:22,42`、`rp1/q.java:2770,2859`、`AMSecure.java:45,75`、`r80/c.java:104` | AES 密钥（会话密钥；`AMSecure` 中 TEST/STAGING 环境改用选择子 2） |
| `2` | 同上 | AES 密钥（测试环境变体）；**内置配置的 AES 密钥**（`ConfigInitializerV2.c`） |
| `3` | `nb1/b.java:26` | `encrypt.envlp` 的密钥 |
| `7` / `8` | `fr0/f.java:149,155` | 聊天物流信息的 AES 密钥（正式/灰度两套） |
| `9` / `10` | `fr0/f.java:151,157` | 对应的 AES IV |
| `11` | `uc2/d.java:117` | 敏感 API 磁盘缓存 MMKV 的 crypt key |
| `12` / `13` | `kq0/i.java:157,158` | 表情/GIF 相关密钥对 |
| `18` | `lb2/n0.java:128` | 解密用的密钥 |
| 其他 | — | 未在调用点枚举到的取值属未决项 |

### 5.3 内置配置的 AES 参数链

```java
// ConfigInitializerV2.c(byte[] bArr)：
String key = com.xunmeng.pinduoduo.arch.config.a.s().b(2);   // 选择子 2
return ij0.b.s(bArr, new SecretKeySpec(key.getBytes(), "AES"));

// ij0/b.s(bArr, key) → u(bArr, key, P(), f66226b)
//   f66226b = "AES/CBC/PKCS5Padding"
//   P() = new IvParameterSpec(o())
//   o() = com.xunmeng.pinduoduo.arch.config.a.s().w(3)        // 选择子 3
```

即：**内置配置的 AES 密钥与 IV 都由 `libpdd_secure.so` 在使用时现场取得**，APK 里
不存明文 key。

`ij0/b` 的其他常量：`f66225a = "RSA/ECB/PKCS1Padding"`、
`f66227c = "AES/CBC/PKCS7Padding"`；签名验证用
`Signature.getInstance("SHA256WithRSA")`；`S()` = `d(25)` + `MD5(d(25))` 前 7 字符；
`d(int)` 为随机字母数字生成器。

## 6. 内置配置 `assets/A94/A25`

### 6.1 元数据（`assets/A94/CDA`，明文 JSON）

```json
{"cv":"001108260000000463","cvv":"00193326",
 "cdnMd5":"3ea3017fb8d9b55a68536bd89bdc1115",
 "isPartBackup":true,"isCompressed":false}
```

### 6.2 文件本身

| 属性 | 值 |
| --- | --- |
| 大小 | 150,688 字节 |
| MD5 | `be6fb23b2cb573032c6b2703c8017f37` |
| 熵 | 7.9988 bit/byte；256 个不同字节值 |
| 可打印串 | 无有意义内容 |
| 对齐 | 16 的整数倍 |

即：A25 是高熵密文，`isCompressed=false`，因此**无需先解压**，直接
`AES/CBC/PKCS5Padding` 解密即可。

### 6.3 `cdnMd5` 与文件 MD5 不一致的解释

`CDA.cdnMd5 = 3ea3017fb8d9b55a68536bd89bdc1115`，而 `A25` 的实际 MD5 是
`be6fb23b2cb573032c6b2703c8017f37`。同一 JSON 里 `isPartBackup = true`：

- `isPartBackup = true` 表示这是**增量下发的基线标记**，而非当前内置文件的摘要；
- `cv`（`0011082600000004 63`）与 `cvv`（`00193326`）是配置版本号与版本校验号；
- `ConfigInitializerV2` 只读取 `cvv`（`e()`）、`isCompressed`（`d()`）与
  `isPartBackup`（字段），**从不比对 `cdnMd5`**；代码里没有任何
  `cdnMd5` 的读取点。

因此 `cdnMd5` 是给服务端/增量更新链路用的远端基线摘要，不是 APK 内文件的完整性
校验值；二者不同属预期行为。文件完整性由 `SHA256WithRSA` 签名路径
（`ij0/b.z`）与 `cvv` 承担。

## 7. 其他 native 库的密码学职责

| 库 | 常量表 | 职责 |
| --- | --- | --- |
| `libgoldarch.so` | AES 逆 S-box、Base64、SHA-256 H0、MD5/SHA-1 IV | GoldenArch 支付请求/响应体加解密（`/api/wormhole/equator`） |
| `libtitan.so` | MD5/SHA-1 IV、SHA-256 H0、P-256 p | Titan 长连接握手与消息摘要 |
| `libpnet.so` | AES 逆 S-box、Base64、MD5/SHA-1 IV | PNet QUIC/TLS 会话与传输 |
| `libmmkv_v2.so` | AES 逆 S-box、MD5/SHA-1 IV | MMKV crypt key 派生与加密存储 |
| `libmmkv.so` | MD5/SHA-1 IV | 同上（APK 内置旧版） |
| `libmedia_engine.so` | AES S-box、AES 逆 S-box、Base64 | 媒体引擎内 DRM/签名 |
| `libpdd_j2v8.so` | AES S-box、AES 逆 S-box、zlib | JS 引擎桥接 |
| `libtronavx.so` | AES 逆 S-box、Base64、ChaCha20 sigma | 播放器；含 ChaCha20 sigma 常量 |
| `libdyncommon.so` | AES S-box、zlib | 反注入/环境探测，见 [obfuscation.md](obfuscation.md) §6.1 |
| `libcmtreport.so` | CRC32、MD5/SHA-1 IV | 埋点上报 |
| `libpcrash_dumper.so` | SHA-256 H0、Base64 | 崩溃转储 |

`ChaCha20 sigma` 只出现在 `libtronavx.so`，与风控无关。

## 8. v1 / v2 签名的 native 落地

```java
// v1
qb2.d.b().H(accessToken, appVersion, ""+nowMs, path, queryBytes, bodyBytes, isDuplex, outMap);
//   -> lb2.h.H(...) -> SecureNative.i(...) -> w(); SE.as(...)

// v2
qb2.d.b().L(url, bodyBytes, headerMap);
//   -> lb2.h.L(...) -> SecureNative.sdr(ctx, map)
//   结果：headerMap["x-p-t"] 总是存在；内部 code == 0 时再加 headerMap["x-p1"]
```

`SecureNative.sdr` 在导出→密码学映射中属于"可达 Base64"一类，不含 AES。其输入
`map` 的键见 [risk.md](risk.md) §4；输出被 `lb2/j.a(map)` 读 `code`、
`lb2/j.b(map, "x-d1")` 读 `x-d1`。

`lb2.h.D(str, str2, str3, map, str4, i14)` → `SecureNative.j(...)` 在调用后做一次
键名改写：`map.put("sctk", map.remove("rctk"))`（当 `i14 > 0` 且 map 含 `rctk`）。

## 9. 边界说明

- 本报告不给出 AES 密钥/IV 的实际值。选择子表给出的是**取值位置**，不是取值内容。
- `SecureNative.s/b` 的选择子→用途对照来自调用点归纳（结构已证实）；未在调用点
  出现的取值仍属未决，见 [report.md](report.md) "未决事项"。
- `libpdd_secure.so` 内未发现虚拟机、自解密或字符串解密循环，全部函数可静态解释，
  方法见 [obfuscation.md](obfuscation.md)。
