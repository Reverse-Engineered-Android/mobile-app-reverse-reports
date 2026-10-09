# libMMProtocalJni.so 主要机制

样本：`com.tencent.mm` 8.0.78 `arm64-v8a`，`lib/arm64-v8a/libMMProtocalJni.so`，
BuildID `90e353ed766f9c65edd5129b47d48a11a94ed9f2`，NDK r22b，已 strip。
方法边界由 `.eh_frame_hdr` 的 FDE 表恢复（1493 个函数）；字符串引用由
`.text` 的 `ADRP+ADD` 配对解析。下文地址均为该 ELF 的虚拟地址。

## 1. 库的构成

`DT_NEEDED`：

```
libcxxstl.200.so  libwechatxlog.so  libz.so  libwechatnormsg.so  libc.so  libm.so  libdl.so
```

`libMMProtocalJni.so` 静态内置了完整的 OpenSSL（`.rodata` 内含
`AES-128-CBC`、`AES-256-GCM`、`SM4`、`EVP`、`PBKDF2`、`OpenSSL ECDH method`
等算法名）以及自研 TLV/Protobuf 编解码器。源码路径以断言串形式保留：

```
/data/landun/workspace/libprotocaljni/component_repo/protocol/src/main/cpp/Comm/mmpack.cpp
/data/landun/workspace/libprotocaljni/component_repo/protocol/src/main/cpp/Crypto/iCoreCrypt.cpp
/data/landun/workspace/libprotocaljni/component_repo/protocol/src/main/cpp/Crypto/rsa_crypt.cpp
/data/landun/workspace/libprotocaljni/component_repo/protocol/src/main/cpp/MMProtocalJniImpl.cpp
/data/landun/workspace/libprotocaljni/component_repo/protocol/src/main/cpp/tlvpickle/iTLVPack.cpp
/data/landun/workspace/libprotocaljni/component_repo/protocol/src/main/cpp/tlvpickle/sktlvpickle.cpp
```

## 2. JNI 导出（全部 23 个）

`readelf --dyn-syms libMMProtocalJni.so` 中的 `Java_com_tencent_mm_protocal_*` 导出，
连同 `.eh_frame_hdr` 恢复出的实现函数地址：

| JNI 导出 | 实现地址 | 原始签名 |
| --- | --- | --- |
| `setProtocalJniLogLevel` | `0x7c17c`（直接返回 1） | 空实现 |
| `mergeSyncKey` | `0x76e4c` | `jboolean merge_synckey_impl(JNIEnv*, jclass, jbyteArray, jbyteArray, jobject)` |
| `verifySyncKey` | `0x7720c` | `jboolean verify_synckey_impl(JNIEnv*, jclass, jbyteArray)` |
| `setClientPackVersion` | `0x773b0` | `jboolean protocal_setClientPackVersion_impl(JNIEnv*, jclass, jint)` |
| `setIsLite` | `0x77420` | 同族 setter |
| `setDeviceTypeId` | `0x7743c` | `void protocal_setDevideTypeId(JNIEnv*, jclass, jint)` |
| `pack` | `0x774a0` | `jboolean protocal_pack(JNIEnv*, jclass, jbyteArray, jobject, jbyteArray, jint, jbyteArray, jstring, jint, jint, jint, jbyteArray, jbyteArray, jint, jint, jint, jint, jshort, jint, jshort)` |
| `packHybrid` | `0x77b14` | `jboolean protocal_packHybrid(JNIEnv*, jclass, jobject, jbyteArray, jstring, jint, jint, jint, jbyteArray, jbyteArray, jbyteArray, jbyteArray, jbyteArray, jint, jint, jshort)` |
| `packHybridEcdh` | `0x78178` | `jboolean protocal_packHybridEcdh(JNIEnv*, jclass, jobject, jbyteArray, jstring, jint, jint, jint, jbyteArray, jint, jint, jshort, jint)` |
| `packDoubleHybrid` | `0x78578` | `jboolean protocal_packDoubleHybrid(JNIEnv*, jclass, jobject, jbyteArray, jstring, jint, jint, jint, jbyteArray, jbyteArray, jbyteArray, jbyteArray, jbyteArray, jint, jint, jshort)` |
| `rsaPublicEncrypt` | `0x78bdc` | `jboolean protocal_rsa_encrypt(JNIEnv*, jclass, jbyteArray, jobject, jbyteArray, jbyteArray)` |
| `rsaPublicEncryptPemkey` | `0x79080` | `jboolean protocal_rsa_encrypt_pemkey(...)` |
| `unpack` | `0x79494` | `jboolean protocal_unpack(JNIEnv*, jclass, jobject, jbyteArray, jbyteArray, jobject, jobject, jobject, jobject, jobject, jobject, jobject, jobject)` |
| `decodeSecureNotifyData` | `0x79b70` | `jbyteArray protocal_decodeSecureNotifyData(..., jint×7, jbyteArray)` |
| `aesDecryptFile` | `0x7a2fc` | `int protocal_aesDecryptFile(JNIEnv*, jclass, jstring, jstring, jbyteArray)` |
| `genClientCheckKVRes` | `0x7b2f0` | `void protocal_genClientCheckKVRes(JNIEnv*, jclass, jint, jstring, jbyteArray, jbyteArray, jbyteArray, jbyteArray, jobject)` |
| `generateECKey` | `0x7c2dc` | `jboolean` |
| `aesDecrypt` / `aesEncrypt` | `0x7c2c8` / `0x7c2cc` | `jboolean` |
| `rsaPublicEncrypt`（wrapper） | `0x7c2d4` | `jboolean` |
| `computerKeyWithAllStr` | `0x7c2e0` | `jboolean` |
| `genSignature` | `0x7c2f0` | `jboolean` |
| `compress` | `0x7c2f4` | `jboolean` |

这些导出本身只是把 Java 栈参数重排后 `b` 到真实实现（例如 `pack`
@`0x774a0`、`unpack` @`0x79494`），真正的封包逻辑在
`0x5b8fc` / `0x5debc`。

## 3. 封包总控：EncodePack / DecryptPack

由 `mmpack.cpp` 的 `TLV_LOG` 断言串恢复的签名：

```
bool EncodePack(JNIEnv *, Comm::SKBuffer &, Comm::SKBuffer &, Comm::SKBuffer &,
                Comm::SKBuffer &, unsigned int, unsigned char *, emMMFunc, unsigned int,
                Comm::SKBuffer &, Comm::SKBuffer &, unsigned int, unsigned int, unsigned int,
                int, unsigned int, unsigned short, int, unsigned short)     @0x5b8fc

int  EncryptPack(RBBuffer &, RBBuffer &, Comm::SKBuffer &, int, Comm::SKBuffer &,
                 Comm::SKBuffer &, Comm::SKBuffer &)                         @0x5c450

bool DecryptPack(int, Comm::SKBuffer *, RBBuffer &, Comm::SKBuffer &, int &)   @0x5debc
bool DecodePack (Comm::SKBuffer &, Comm::SKBuffer &, Comm::SKBuffer &,
                 Comm::SKBuffer &, int&, int&, int&, int&, int&, int&, int&)  @0x5e318
```

### 3.1 打包日志暴露的字段

`EncodePack` 逐行打印（`mmpack.cpp` 行号 `0x9d`=157、`0xa3`=163、`0xa6`=166）：

```
compress, length=%d, compAlg=%d, compVer=%d, func=%d, flag=%d, asType=%d, alg=%d nRet=%d
EncodePack fgflag:%d, cver：%d, calgo:%d, clen:%d, cdlen:%d, ealog:%d, routeInfo:%d
packing done, uin=%d, func=%d, ret=%d, encryptAlgo=%d, compressAlgo=%d, compressVer=%d
```

其中 `EncryptPack` 收尾处 `mmpack.cpp:0xdd`=221 行打印
`packing done, ... encryptAlgo=%d, compressAlgo=%d, compressVer=%d`（字符串 @`0x3e83b`）。

### 3.2 算法枚举（由分支直接判定）

`DecryptPack` @`0x5debc` 的分派（`mmpack.cpp:0x252`=594 起）：

| `CryptAlgorithm` | 行为 | 依据指令 |
| --- | --- | --- |
| `0` | `NO_ENCRYPT`，直接返回并记 `wifi DecryptPack never use no_encrypt now !` | `0x5df3c cbz w23` → `0x5e00c` |
| `11`, `12`, `13`, `14` | **不做本地解密**，原样放行 | `0x5df88 sub w8,w23,#0xb`；`0x5df8c cmp w8,#4`；`b.hs 0x5e050` |
| `10` | RSA 公钥解包 | `0x5e054 cmp w23,#0xa` → `0x5e10c` |
| `5` | AES 解密 | `0x5e05c cmp w23,#5` → `0x5e064` |
| 其它 | DES 解密 | `0x5e198` → `0x5e1ec bl 0x66610`，失败日志 `DecryptDES failed: err=%d` |

`EncodePack` @`0x5b8fc` 对同一组常量的判定：

- `0x5c014 cmp w21,#0xd` → 日志 `AES_GCM_ENCRYPT no need encrypt again here:%d`，
  并把 `w20=0xd`（即算法 13 = AES-GCM）；
- `0x5c064 cmp w21,#0xe` → 日志 `SM4_GCM_ENCRYPT no need encrypt again here:%d`，
  并把 `w20=0xe`（即算法 14 = SM4-GCM）；
- `0x5c0bc tbnz w23,#2` → 走 `EncodePack NO_ENCRYPT flag:%d`（flag bit 2 = 明文），
  `w20=0`；
- `EncryptPack` @`0x5c450`：`0x5c4a8 cmp w25,#5` → AES；`0x5c4b0 cmp w25,#3`
  → DES；否则 RSA（`encrypting using RSA, Buflength=%d` / `encrypting using RSA pemkey,...`）。

综合得到 `emMMCryptAlgo` 枚举：

```
0  NO_ENCRYPT
3  DES
5  AES
10 RSA
11 ECDH_ENCRYPT
12 HYBRID_ECDH_ENCRYPT
13 AES_GCM_ENCRYPT
14 SM4_GCM_ENCRYPT
```

`11..14` 在 `DecryptPack` 中被跳过的原因由日志直接给出：
`ECDH_ENCRYPT OR AES_GCM_ENCRYPT OR SM4_GCM_ENCRYPT no need decrypt here len:%d algo:%d`
（@`0x324fc`，引用点 `0x5dfec`）——这四类的密钥派生/解包在
`DecodePack` 或上层已完成。

### 3.3 四条封包路径

| 函数 | 地址 | 触发日志 |
| --- | --- | --- |
| `EncodePack` | `0x5b8fc` | `EncodePack NO_ENCRYPT flag:%d`、`EncodePack fgflag:%d, cver...` |
| `EncodeHybirdEncryptPack` | `0x5cd18` | `HybridEncrypt failed, ret=%d`、`EncodeHybirdEncryptPack fgflag...`、`writing head, g_clientVer: %d, flag:%d` |
| `EncodeHybirdEcdhEncryptPack` | `0x5d374` | `EncodeHybirdEcdhEncryptPack failed`、`writing head, g_clientVer: %d, flag:%d, encryptAlgo:%d`、`EncodeHybirdEcdhEncryptPack fgflag...` |
| `EncodeDoubleHybirdEncryptPack` | `0x5d7ec` | `DoubleHybridEncrypt failed, ret=%d`、`EncodeDoubleHybirdEncryptPack fgflag...` |

`writing head, g_clientVer: %d, flag:%d` 出现 2 处
（引用点 `0x5d038` 属于 `EncodeHybirdEncryptPack`，`0x5db0c` 属于
`EncodeDoubleHybirdEncryptPack`），说明这两条路径共用一个“客户端版本 + 标志位”
头部写入器；`EncodeHybirdEcdhEncryptPack` 额外写入 `encryptAlgo`
（引用点 `0x5d454`）。

### 3.4 包头格式（TLV Pack Header）

由 `Comm::SKTLVBuffer::IsValidTLVPack` @`0x6a8a0` 的指令直接还原
（`mmpack.cpp` 行号 `0x161`=353、`0x16c`=364、`0x174`=372）：

```asm
6a8c4:  cmp   w2, #0xb           ; 包总长必须 >= 12
6a8c8:  b.gt  6a938
6a938:  sub   w22, w21, #0xc     ; payloadSize = totalLen - 12
6a940:  ldrb  w8, [x19]          ; offset 0 : magic (uint8)
6a944:  cmp   w8, #0x81          ; magic 必须 == 0x81
6a94c:  ldr   w8, [x19, #4]      ; offset 4 : size (uint32 LE)
6a950:  cmp   w8, w9             ; 必须 == totalLen - 12
...
6aa30:  ldrh  w8, [x19, #2]      ; offset 2 : checksum (uint16 LE)
6aa34:  cmp   w8, w21, uxth       ; 与计算值比较
```

因此 12 字节包头为：

| 偏移 | 宽度 | 字段 | 校验 |
| --- | --- | --- | --- |
| `0` | `uint8` | magic = `0x81` | `cmp w8,#0x81` |
| `1` | `uint8` | mode | `DumpHeader` 打印 `mode` |
| `2` | `uint16 LE` | checksum | `ldrh [x19,#2]` |
| `4` | `uint32 LE` | payload size | `== totalLen - 12` |
| `8` | `uint32` | reserved | `DumpHeader` 打印 `reserved` |

`DumpHeader` 的格式串（@`0x3a1f6`）：

```
TLVPACK: header magic %i mode %i checksum %i size %i reserved %i
```

### 3.5 checksum 算法

`IsValidTLVPack` @`0x6a9f4`–`0x6aa38`：

```asm
6a9f8:  ldr   x8,  [x20, #8]
6a9fc:  ldp   x9,  x10, [x8]        ; x9 = u16 前缀和表, x10 = 缓冲基址
6aa00:  sub   x8,  x19, x10         ; 包在缓冲中的偏移 off
6aa04:  add   x10, x8, #0xc         ; payloadStart = off + 12
6aa08:  add   w8,  w21, w10         ; totalLen + payloadStart
6aa0c:  sub   w8,  w8, #0xd         ; payloadStart + payloadLen - 1
6aa10:  ldrh  w8,  [x9, w8, sxtw #1]; 表[payloadEnd]
6aa14:  cmp   w10, #1
6aa1c:  sub   w10, w10, #1
6aa20:  ldrh  w9,  [x9, w10, uxtw #1]; 表[payloadStart-1]
6aa24:  sub   w8,  w8, w9           ; 差值
6aa28:  mvn   w21, w8               ; 按位取反
6aa30:  ldrh  w8,  [x19, #2]        ; 读头部 checksum
6aa34:  cmp   w8, w21, uxth
```

结论：`checksum = uint16( ~(table[end] - table[start-1]) )`，
即对 12 字节头之后的 payload 按 `uint16` 前缀和差分取反。
`table[]` 由 `SKTLVBuffer::SetCheckSum` @`0x6a268` 与 `SetHash` @`0x6a568` 维护。

校验失败的三条日志（分别对应 magic / size / checksum）：

```
%s: IsValidTLVPack error header magic error header(%d) magic(%d)
%s: IsValidTLVPack error size error header(%d) size(%d) Actually(%d)
%s: IsValidTLVPack error checksum error header(%d) sum(%d) Actually(%d)
```

### 3.6 RSA 公钥（algo=10 分支）

`DecryptPack` @`0x5e10c`–`0x5e194` 传入两个硬编码常量给 `0x690dc`：

- 模数 `@0x3e8ba`：2048 bit 十六进制串，以 `C97E6E0C...0A38DF31F` 开头、
  `...E1698EC90BF403961A199FFB966DB6FC65913DC60F6B83B927810A38DF31F` 结尾；
- 指数 `@0x3d15b`：`"010001"`（65537）。

失败日志 `rsa_public_decrypt new DISASTER failed: err=%d`（@`0x3ca52`），
函数是 `int rsa_public_encrypt_pemkey(...)` @`0x69730` 的同族实现 @`0x690dc`。

### 3.7 zlib 压缩

`Java_*_compress` 直接走 `compress` / `compressBound` / `uncompress`
（`DT_NEEDED libz.so`）。日志：

```
compressing, length=%d
decompress failed=%d func=%d
DecryptPack succ compress length=%d headCompressLen=%d, algo=%d, ver=%d, funId=%d, encryptAlog:%d
```

注意 `DecryptPack HYBRID_ECDH_ENCRYPT or AES_GCM_ENCRYPT no need to decompress
algo:%d len:%d`（@`0x?`，引用点在 `DecodePack` @`0x5e318` 内）——
这两类在编码侧已跳过压缩（`AES_GCM_ENCRYPT no need compress again here. type:%d`）。

## 4. TLV / Protobuf 二义性

`.rodata` 同时含 `SKTLVPickle` 与 `SKPBPickle` 两套编解码器：

| 组件 | 地址 | 作用 |
| --- | --- | --- |
| `Comm::SKPBDecoder::InitObject` | `0x6f31c` | protobuf 解码初始化 |
| `Comm::SKPBDecoder::GetValue<T>` | `0x6f548`/`0x6f698`/`0x6fa30`/`0x703cc` | `uint64/int/long long/uint32` |
| `Comm::SKPBPickle::Struct2Buffer` | `0x72a24` | 结构 → protobuf 字节 |
| `Comm::SKPBPickle::Buffer2Struct` | `0x73398` | protobuf 字节 → 结构 |
| `Comm::SKTLVPickle::Struct2Buffer` | `0x74bbc` | 结构 → TLV 字节 |
| `Comm::SKTLVPickle::Buffer2Struct` | `0x75b38` | TLV 字节 → 结构 |

外层（包头 + checksum + size）用 TLV，业务字段（请求/响应消息体）可用
protobuf 或 TLV，取决于 `mmpack` 的 `pack` / `unpack` 参数。

## 5. 密钥、签名与安全通知的最终格式

### 5.1 EC key 与 ECDH

`generateECKey` @`0x7c2dc` 把 Java 传入的 curve id 交给
`sub_467ed8`：先按 id 分配 `EC_KEY`，再由 `0x91838` 校验并生成
公钥/私钥，`0x9158c` 释放对象。Java 调用点全部写入 curve id `713`
（`mc5/rg.java:77`、`mc5/pg.java:103,108`、`mc5/ei.java:30`、
`mc5/vg.java:107`），即 OpenSSL `NID_X9_62_prime256v1`；库内也包含
`prime256v1`/`P-256` 字符串。因此该认证路径使用 NIST P-256，
`computerKeyWithAllStr` @`0x7c2e0` 先把 private/public 字符串载入
`EC_KEY`，再调用 `0x915a0` 同族 ECDH 计算；末位参数 `etype` 为 1
时走 cofactor 变体，否则走普通变体，输出写入 Java 的 `PByteArray`。

### 5.2 `genSignature`

`genSignature` @`0x7c2f0` 的输入是 `uin`、session key 和明文
Protobuf；任一缓冲为空时 Java 侧返回 `0`。原生实现 `0x682ac` 的
精确输入序列为：

1. 把 `uin` 做 32-bit endian swap；
2. 初始化 MD5（状态字为 `0x1032547698badcfeefcdab8967452301`），
   更新 `uinBE || key`，得到 16 字节摘要 `h1`；
3. 再次初始化 MD5，更新 `uinBE || key || h1`，得到 16 字节摘要
   `h2`；
4. 以 `Adler32(0)` 初始化，先更新 `h2`，再更新明文缓冲，返回
   32-bit `int`。

日志 `genSignature ecdhkey length=%d, buf length=%d, signature=%d`
与该顺序一致；签名值写入 TLV 包头的 `iGenSignature`。

### 5.3 `decodeSecureNotifyData`

`decodeSecureNotifyData` @`0x79b70` 在 mode `5` 时先把输入
session/salt 字节与 4-byte flag 组装成 16-byte key，调用
`0x674b8` 解密；mode 非 5 时直接以输入作为密文。随后按
`MicroMsg` 标志调用 `sub_45fb04` 解压，最后计算 `crc32(0, plain, len)`
并与调用方 `jcheckSum` 比较；失败日志为
`securenotify checksum failed checksum[%d], jcheckSum[%d]`。解密、
解压或 CRC 失败均返回 `null`，成功才把明文返回给 Java。

### 5.4 `genClientCheckKVRes`

`genClientCheckKVRes` @`0x7b2f0` 的四个 byte[] 参数依次是
`keyn/keye/keyecdh/rsa`，另有 `ecdh` 与 `newEcdhkey` 两个缓冲；
实现将前两个键拼为 `keye + ":" + keyn`，把后两个缓冲各自作为
独立值，调用 `0x683dc` 形成最终 `PByteArray`。因此线上该响应的
K/V 序列化顺序是 `keye:keyn`、`ecdh`、`rsa`，对应日志
`keynLen:%d, keyeLen:%d, keyecdhLen:%d, rsaLen:%d, ecdhLen:%d,
newEcdhkeyLen:%d`。

### 5.5 Hybrid ECDH 封包的 buffer 操作

`EncodeHybirdEcdhEncryptPack` @`0x5d374` 写入 TLV 头后，依次调用
`0x6694c` 取 source buffer 当前长度、`0x667fc` 深拷贝 source 到
目标、`0x668dc` 追加 payload；完成后由 `0x5fcb0` 写出 12-byte
包头。`encryptAlgo`、flag 位 `1/2/4`、client version 和 func id
都进入该 TLV 头，算法选择与外层包头的完整对应关系见 §3。

## 6. 最终结论

`libMMProtocalJni.so` 的认证、签名、ECDH、安全通知和客户端 K/V
结果均由客户端完成确定性的字节序列化；服务端侧的解密顺序、阈值
和降级策略属于服务端控制面，客户端静态样本不承载这些决策。
