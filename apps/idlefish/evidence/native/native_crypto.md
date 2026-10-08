# native 加密与混淆证据

样本为 `idlefish-7.28.40-arm64.apk` 中的 125 个 arm64 库；本节只做静态
只读分析，不发起网络请求，不执行 APK，也不调用设备端写接口。

## 1. 库与 ELF 结构

- `libsgmainso-6.7.260202.so` 的 ELF 头被加固器破坏，但 PT_PHDR、9 个
  program header 和 relocation 区完整；其 RX LOAD 为 `0x0..0x1a4b60`，
  RW LOAD 为 `fileoff=0x1a7400, VA=0x1af400, filesz=0x130ed0`。
- 三个 SG 库的 section header 损坏，PHDR 仍可解析；动态符号从
  `.dynsym/.dynstr/rela.dyn` 重建，结果保存在 `sg_dynsym.txt`。
- `libwukong_native.so` 是明文 ELF：321 个导出、10 个 JNI 入口、1612 个
  `R_AARCH64_RELATIVE` relocation 全部指向 RX。
- 4 个伪装 `.so` 的 DEX 已解包：`libsgmain` 130 类、`libsgmisc` 10 类、
  `libsgnocaptcha` 4 类、`libfakedexuc` 1 类；`doCommandNative` 的
  命令号全集为 66 个。

## 2. 加密常量与指令扫描

对 125 个库扫描 AES S-box、SHA-1/SHA-256/MD5/SM4 常量、TEA delta、
Blowfish P 表等标准常量，未发现未归属的算法表。ARMv8 crypto 指令只出现
在：

| 库 | 指令 | 归属 |
| --- | --- | --- |
| `libtb_crypto.so` | `aese`/`sha`/`pmull` | TB crypto |
| `libopenssl.so` | SM3 相关指令 | OpenSSL |

`libsgmainso` 的 `sm4ekey@0x2e548` 位于 `.byte` 数据区，不是可执行指令。
因此加固库没有额外的未命名 AES/SHA/SM4/TEA/Blowfish 实现。

## 3. 控制流混淆

计算跳板只出现在 5 个库：

| 库 | 跳板数 | 静态求解 |
| --- | ---: | ---: |
| `libsgmainso` | 366 | 360 个；其中 358 个落在同一函数 ±0x10000 内 |
| `libALBiometricsJni` | 59 | 静态求解 |
| `libsgnocaptcha` | 42 | 静态求解 |
| `libsgmisc` | 41 | 静态求解 |
| `libdps` | 7 | 静态求解 |

这些跳板的语义是基本块打散和间接跳转，不是加密算法。`libsgmainso` 的
AVMP 调度入口静态可见：

```text
0xbfbf8  ldp  x8,x11,[x0,#8]
0xbfc04  add  x12,x12,#0xba8
0xbfc08  add  x13,x13,#0xb50
0xbfc0c  cmp  w3,#2
0xbfc14  csel x12,x13,x12,eq
0xbfc24  ldr  x9,[x12,w10,sxtw #3]
0xbfc30  blr  x9
```

这是按 `w3` 选择表、按 `w10` 索引后 `blr` 的方法分派；不是动态生成或
执行新的可执行代码。

## 4. zlib、UVM 容器和 data file

`libsgmainso` 的 zlib 导入只有 `crc32`、`deflateInit_`、
`deflateBound/deflateEnd/deflate`、`inflateInit_`、`inflateEnd`、
`inflate`，没有 `uncompress`。调用点：

```text
inflateInit_@0x11cf7c
inflate@0x11d080,0x14df84
crc32@0x10f1e0,0x14df04,0x14dfb8
```

`libsgmainso` 的 26 条 zlib 流中，13 条解压为 UVM 容器，13 条解压为
Base64 ASCII；`liblrc_core` 另有 1 条 UVM 容器和 1 条 Base64 数据。
Base64 解码产物共 14 个，尺寸均为 16 的倍数：

```text
336, 3216, 2240, 352, 256, 3744, 256, 288,
336, 7056, 1920, 1280, 7024, 256
```

12 个共享头 `ff69d455fd48f8c47be4ffa8890cf0f6`，另 2 个共享头
`8078fcb38e26bcf3eba26dc17d9e5566`。每个 Base64 流紧接其对应容器，
Java 侧存在 `SEC_ERROR_GENERIC_AVMP_NO_DATA_FILE(1905)`、
`SEC_ERROR_GENERIC_AVMP_INCORRECT_JPG_FILE(1903)`、
`SEC_ERROR_GENERIC_AVMP_INVLIAD_MWUA_DATA_FILE(1916)` 等明确错误码。
这些流是 AVMP/UVM 的配套 data file，不是可执行代码。

UVM 容器头为：

```text
+0x00 magic 1a0825b2
+0x04 00 04 04 00
+0x08 总长
+0x0c 880（一个对象为 1008）
+0x10 checksum
+0x14 0x00010003（一个对象为 0x00010005）
+0x1c..0x37 四组 (offset,size)
```

校验函数 `0x16ac28` 的精确判定为：

```text
0x16ac3c cmp w1,#0x71
0x16ac50 ldr w9,[x8]
0x16ac54 mov w10,#0x81a
0x16ac58 movk w10,#0xb225,lsl#16   ; 0xb225081a
0x16ac5c cmp w9,w10
0x16ac64 ldr w9,[x8,#0x14]
0x16ac68 mov w10,#2
0x16ac6c movk w10,#1,lsl#16        ; 0x00010002
0x16ac78 ldr w9,[x8,#0x48]
0x16ac7c cmp w9,#2
0x16ac84 w0=0; str x8,[x2]; str w1,[x2,#8]
```

## 5. handler table 与 data descriptor

`libsgmainso` 的 AVMP/UVM handler table 从 `0x2adc60` 到 `0x2af2b0`，
共 715 项。指令布局为：

```text
u16 opcode
u8 op0, op1, op2, op3
u8 pad[2]
u64 imm
```

代表项及静态语义：

| opcode | 地址 | 语义 |
| ---: | --- | --- |
| `0` | `0xd6484` | `r[op0]=r[op1]+imm`，PC+0x10 |
| `0x14` | `0x160ccc` | 按 imm 条目数相对跳转 |
| `0x1e9` | `0x121ef4` | `load u64 [base+imm]` |
| `0x242` | `0xf17c4` | store；对应 x-sign 输出点 |
| `0x244` | `0x15b530` | `store u64 [rA+rB]=rC` |
| `0x262` | `0xbce5c` | `if r==imm32 goto index+delta` |

Java 调用点为：

```text
mtopsdk/security/InnerSignImpl.java:295
com/youku/antitheftchain/encrypt/EncryptAbilityImpl.java:78,80
com/alipay/mobile/common/netsdkextdepend/security/CustomRpcSignUtil.java:38,68
com/mpaas/security/android/a/b.java:65
```

`InnerSignImpl` 明确调用 `createAVMPInstance("mwua","sgcipher")`；
`IAVMPGenericComponent` 与 `SecurityGuardMainPlugin` 注册链也可见。

data descriptor 的精确代码为：

```text
0xa5944  mov w19,w0
0xa59b8  bl  #0x1a41f0
0xa59c8  cmp w19,#1
0xa59d4  adrp x10,#0x1eb000
0xa59e0  add x10,x10,#0x5e8       ; VA 0x1eb5e8
0xa59e4  str x10,[x8]
0xa59e8  str w9,[x8,#8]           ; w9=0x4f5
```

`0x1eb5e8` 起始为 `68 03 00 00 78 da ...`，即长度 872 的 zlib 流，
解压后为 1272 字节的 UVM 容器。

## 6. 高熵原始区

RW 段 `VA=0x1e4985..0x1eb5ec` 长 27,751 字节，熵约 7.9915；没有
zlib/gzip/bzip2/xz/zstd/lz4 magic，也没有 ARM 可执行段属性。`0xa5510`
只写入一个状态字节：

```text
0xa5510 mov w8,#0x22
0xa5514 adrp x9,#0x1e4000
0xa5518 add x9,x9,#0x988          ; VA 0x1e4988
0xa551c strb w8,[x9]
```

区域末端由 `0xa59d4..0xa59e8` 选择长度前缀 UVM 容器，没有 `mprotect`、
`mmap`、`memfd_create` 或 `execve` 调用。该区是 AVMP/UVM 的静态数据与
描述符材料，不是可执行加密代码。

## 7. 结论

native 侧已完成 ELF、常量、跳板、zlib、UVM、handler table、descriptor
和高熵数据区的静态核对；未发现未分析清楚的加密实现。唯一保留的不可证
边界是服务端如何校验 `x-sign`/`wua`/`umid` 以及远端风险评分。
