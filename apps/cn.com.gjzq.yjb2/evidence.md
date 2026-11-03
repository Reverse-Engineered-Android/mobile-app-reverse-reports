# 逆向证据

## 1. 样本指纹

| 项目 | 值 |
|---|---|
| 包名 | `cn.com.gjzq.yjb2` |
| versionName / versionCode | `9.05.004` / `20260903` |
| APK 大小 | `382,970,270` 字节 |
| APK SHA-256 | `e6cc89f97669d86601843a8c741459c2c3aa74386cb7e2206a7da60e7d10c82d` |
| ZIP 条目 | `7,627` |
| `classes.dex` | `13,644` 字节 |
| ARM64 库 | `46` 个、`97,838,372` 字节 |

## 2. 壳与载荷证据

- `classes.dex` 中可读类：`s.h.e.l.l.S`、`s.h.e.l.l.A`、`s.h.e.l.l.C`、`s.h.e.l.l.N`。
- `S.attachBaseContext` 调用 `S.c(abi)`，从 APK 读取
  `assets/ijm_lib/<abi>/libexec{,main}.so` 并释放到应用 `files/`。
- `N.<clinit>` 释放后 `System.load`；`A` 通过反射 `ActivityThread.mBoundApplication`
  替换真实 Application。
- `assets/ijiami.dat`：`14,330,583` 字节，头部 `04 00 00 00` + LE 长度 +
  32 个十六进制字符（16 字节 ID）；`assets/ijiami.ajm`：`7,514,954` 字节，头部
  `indl01\\0\\0`，两者不是同一魔数。
- `assets/yjb.zip`：`186,793,631` 字节、`2,843` 条目、`37` 个业务模块。
- 四个关键 ELF 的 `.dynsym sh_size` 都只有 3 个符号；按 `DT_SYMTAB`→
  `DT_STRTAB` 间距恢复为 `23` / `27` / `53` / `226` 个表项，各 `DT_INIT`
  内 `+0x48` 处都有 `dc cvau, x0` / `ic ivau, x0` 缓存维护指令，详见
  [obfuscation.md](obfuscation.md) §4。

## 3. H5 keystream 证据

已知明文交叉验证前 16 字节：

```
b3 09 cd 5d e7 1c 06 59 d1 77 a8 02 21 4c c7 98
```

UTF-8 状态机 + 二元语言模型逐列求解、再经 webpack 序言与 `node --check`
两轮校验的产物为 `6,165,153` 字节，SHA-256：

```
b8f6bbc75f3d5b667239114a1366d41d4420e843a477560f2dbd58c35fb2ceba
```

早期版本在偏移 `15`（`0x9f`→`0x98`）与 `21`（`0x51`→`0x52`）各有一个歧义
字节；只有这两处与最终值不同（逐字节比对全部 921 个成员，差异位置集合恰为
`{15, 21}`）。修正判据是 89 个共享 webpack 序言文件：

| 偏移 | 旧值 | 新值 | 序言解出 |
|---:|---|---|---|
| `15` | `0x9f` | `0x98` | `fuiction#t(` → `function t(` |
| `21` | `0x51` | `0x52` | `vau t={}8fu` → `var t={};fu` |

自检：126,000..131,072 区间与早期基准 100% 一致；928 个文本文件
（`128,065,750` 字节）中 921 个完全 UTF-8 合法，非法序列 1,182、非法字节
1,401，占 `0.001094%`；20 个 TrueType 成员魔数全部通过；`node --check`
通过的 JS 由 `82/507` 升至 `369/507`，且无一个文件由通过变失败。

## 4. 加密证据

H5 明文常量：

```js
const key = CryptoJS.enc.Utf8.parse("g6j0z0q1w0e9bapp");
CryptoJS.AES.encrypt(content, key, {
  mode: CryptoJS.mode.ECB,
  padding: CryptoJS.pad.Pkcs7,
}).toString();
```

出现位置包括 `stockInformation/con-f/paramConfig.js`、
`BaseIndexNew.js`、`BaseIndex.js` 及多个 `paramConfigFor*.js`。该常量是包内
参数混淆密钥，不是认证根密钥。

Native 符号：`Java_cn_com_gjzq_crypto_GjEncrypt_*`、`_Z3RC4PhiPKhi`、
`sm4_setkey_{enc,dec}`、`sm4_crypt_cbc`、`sm4_crypt_ecb`、
`sms4_{encrypt,cbc_encrypt}`、`_Z14pkcs7_input_pdPhPi`、
`Java_com_tztEncrypt_{en,de,ajax,webdata}Encrypt13`。

## 5. Manifest 证据

`<uses-permission>` 47 个标签、44 个唯一名；`<queries>` 14 个包名 + 3 个
intent；导出组件 12 activity、3 service、3 receiver、1 provider。`QUERY_ALL_PACKAGES`
和 `MANAGE_EXTERNAL_STORAGE` 不存在。完整清单见 [permissions.md](permissions.md)。

## 6. 设备只读核对

使用 Android 私有目录的只读快照，执行：

```bash
# 仅列出目录、尺寸和 schema；不写入设备
find <app-private-data>/databases -maxdepth 1 -type f -printf '%f %s\n'
find <app-private-data>/shared_prefs -maxdepth 1 -type f -printf '%f %s\n'
python3 tools/sqlite_schema.py <database-dir> <snapshot.txt>
PRAGMA integrity_check;
```

快照结果：7 个 SQLite、30 个 `sqlite_master` 对象（23 张业务表）、
70 个非空 `shared_prefs`；未输出设备 UUID、
账号票据、数据库行内容或真实路径。设备安装版本为 `9.05.007` / `20261028`，
只用于格式核对，不与 `9.05.004` 主样本混写。

## 7. 证据等级

| 术语 | 含义 |
|---|---|
| 已验证 | 可从 APK/资源/只读设备快照直接复现 |
| 结构已证实 | 符号、调用点、schema 或格式存在，但完整语义未还原 |
| 未知 | 需要运行时、服务端或高熵 native 深度反汇编才能闭环 |

## 8. 运行态载荷与临时解包目录

样本 `9.05.007`（只读设备当前安装版本）在进程存活期间产生两类解包期文件，
取回后的哈希如下，均只用于格式核对，不与 `9.05.004` 主样本混写：

| 对象 | 大小 | SHA-256 |
|---|---:|---|
| `files/i12196010e51071ed` | `69,262,848` | `58d34180407eb06e901802c520d1a45d8145a113745d29f9919447d2c42074f` |
| `.tcache/.templ.jar` | `423,320` | `ce15254e51e3cc2a1354a9ed6d70642da2c1f50c1d5bb0fd8523379b09085b0` |
| `.tcache/.tempr.jar` | `29,075` | `2dac063e5aec6bfe0fbbb9e002cba9e1d4f88391b3d8f4c3139c4c65ca1b4828` |
| `.tcache/libs/libhts.so` | `151,840` | `d6d9c0f6c607a27d4c06c01bd0f6a64f35128ff801159c2bb0f695fd44ef2df5` |

`i12196010e51071ed` 的 40 字节头部与主样本 `assets/ijiami.dat` 同格式但
不同实例（长度字段 `69,262,848` 对 `69,114,092`，ID
`ed186bf3f8a4acf0d8450fdb005efa66` 对 `d39c9da2c2ccce5f0f6f0129e91152ce`）。
实测统计：正文熵 `4.639`、`0xB2` 占 `32.8%`、`0x41` 占 `22.8%`、
`gzip` 后 `14,513,395` 字节；全文无 `dex\n` / `\x7fELF` /
`PK\x03\x04` / `cn/com` / `Ljava/` / `yongjinbao` 标记，周期
`1/2/4/8/16/32` 的按列众数 XOR 解扰也不产生 DEX/ELF 头。

`.tcache` 在应用进程退出后消失，因此这些对象属于解包期临时产物；复现需在
进程存活时只读取回，命令形如：

```bash
ssh root@<host> 'nsenter -t 1 -m -- cat /data/data/cn.com.gjzq.yjb2/.tcache/.templ.jar'
ssh root@<host> 'nsenter -t 1 -m -- cat /data/data/cn.com.gjzq.yjb2/files/i12196010e51071ed'
```

匿名可执行段（解密后的代码页）只存在于 `/proc/<pid>/maps` 的 `00:00 0`
`r-xp` 区间，静态文件不含明文代码。

## 9. 本轮新增静态证据（网络 / 认证 / 风控 / 混淆）

以下证据全部来自解密后的 `yjb.zip`（keystream 见 §3）。偏移为解密后单个
JS 文件内的字节偏移，可按 `open(path,'rb').read()[offset:]` 直接复现。

| 对象 | 文件 | 偏移 |
|---|---|---:|
| `IAR_INTERFACE`（28 短码 → 服务名） | `stockInformation/common/vendors/Tool.js` | `0x2a` |
| `fetchToIAR`（短码 → `/core/api[/json]`） | 同上 | `0x2116` |
| `API_URL` 默认值 | 同上 | `0xb26` |
| `Interface`（65 短码 → GET 路径） | `stockInformation/common/vendors/interface.js` | `0x0` |
| `stockInformationSiteBak`（OSS 直连） | `stockInformation/con-f/paramConfig.js` | `0xc4` |
| `callNative`（native 桥 ABI） | `stockInformation/common/js/NativeAPI.js` | `0x642` |
| `window.respFromNative` | 同上 | `0xc2b` |
| `YJBExec = callNative` | 同上 | `0x4d9` |
| `fetchYJBAPI`（登录状态机入口） | `treasurybonds/build/index.js` | `0x3116a` |
| `_fetchYJBAPI`（`YJBExec(1001,…)`） | 同上 | `0x319c5` |
| `loginLogic`（`loginStatus/tradeOvertime` 状态机） | 同上 | `0x2dc2c` |
| `LoginTypeEnum`（占位符 → 登录类型） | `ycomment/build/ycomment.js` | `0xc28fd` |
| `AUTH_ERROR_CODE = -1` | 同上 | `0xc2dd9` |
| `LoginAndReTry`（拦截 + 重放） | 同上 | `0xc2e6e` |
| `CHANNELID` 默认 `YPaJ9axolEpz` | 同上 | `0x12a781` |
| `checkLogin`（uKey → webapps 会话） | 同上 | `0x12d1e8` |
| `decrypt(value, "z$B&E)H@McQfTjWn")` | 同上 | `0x12af1a` |
| `ssoDToken/sasDToken/sasIToken` 模板 | `margintrading/build/commons.js` | `0x192e5` |
| `opKey`（AES-128-ECB + Base64URL） | 同上 | `0xa980` |
| `getRiskInfo` 购前闸门 | `ai-adviser/optional.js` | `0x179280` |
| `agreement.signQuery` | 同上 | `0x4c564` |
| `checkTransactionTime` | 同上 | `0x1798d9` |
| `permDesk.v2.query` | `vas/build/buy0.js` | `0x184c` |
| `access.authority` + `is_auth` | `vas/build/commons.js` | `0x8cd3` |
| `saveLog` 设备遥测信封 | 同上 | `0x9ee6` |
| `spew.rule.*` 预警规则模板 | `stockWarning/build/index.js` | `0x1f956` |
| 普通委托 `functionId:302` | `trans/build/position.js` | `0x2189a` |
| 撤单 `functionId:304` | `trans/build/bottom_entrust.js` | `0xf989` |
| NDB WebSocket 服务器实现 | `relatedfunds/build/static/NDBCLIAPI.js` | `0x1b602` |
| `quoteLogin` 登录帧 | 同上 | `0x29c36` |
| `xGtsJason` 帧压缩 | 同上 | `0x71fe` |

派生表（可复核，均由上述文件静态抽取）：

| 产物 | 条目数 | SHA-256 |
|---|---:|---|
| `iar-interface.tsv`（短码 → 服务名） | 28 | `47985d5987c57940935dbffc57d5f825d16f0ea8ab3ffc10c0a7b2a466ce8d0f` |
| `method-codes.txt`（`UniqueKey` 短码） | 169 | `7652d7c08dac789306f8f8d57d9b65a85e040aba9a18d30475c2b9f20e081cb0` |
| `native-bridge-codes.tsv`（有文档的功能号） | 38 | `3720f487732df09d8dbd4680788fc082a60f530f1d017a342c7f0ac8f015b9a1` |
| `trade-functionids.json`（交易参数模板） | 70 | `8bd42e7e12eee3e5d3b06cd003389bc98818ed53e3b03fba7b778d41d2657394` |
| `jssdk-bridge-raw.json`（JSSDK 绑定） | 236 | `fe96efe174650d99639d7b757568b0d8aea50e7230609af695e8b7dca944b4d0` |
| `service-names.json`（服务名，按文件） | 653 唯一 | `f2d463d96ac0c5ea2c27437f53675b9872f93ef1e424e958e331eaa10b349661` |
| `ks6m-v2.bin`（修正后 keystream） | 6,165,153 B | `b8f6bbc75f3d5b667239114a1366d41d4420e843a477560f2dbd58c35fb2ceba` |

复现命令：

1. `.r` 条目计数与最大成员

```bash
python3 -c "import zipfile;z=zipfile.ZipFile('assets/yjb.zip');\
n=[x for x in z.namelist() if x.endswith('.r')];\
print(len(n), sum(z.getinfo(x).file_size for x in n), max((z.getinfo(x).file_size,x) for x in n))"
```

2. native 功能号实际调用点（62 个号 / 10,372 处）

```bash
grep -ohE '(YJBExec|nativeIOOperation|callNative)\( *[0-9]{4}' yjb/*/*/*.js | sort -u | wc -l
```

3. native 库不可解码比例（capstone `skipdata`，见 obfuscation.md §7）

```python
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
md.skipdata = True
for name in ('libShanYCore.so', 'libencrypt.so', 'libndktest.so', 'libhtsfx.so'):
    blob = open(name, 'rb').read()
    ins = list(md.disasm(blob, 0))
    bad = sum(1 for i in ins if i.mnemonic == '.byte')
    print(name, len(ins), bad, round(100 * bad / len(ins), 1))
```

`node --check` 计数（对解密后 507 个 JS）：

| keystream | 通过 | 未通过 |
|---|---:|---:|
| 修正前（偏移 15/21 旧值） | 82 | 425 |
| 修正后（`ks6m-v2.bin`） | 369 | 138 |

## 10. Native 容器证据（UPX 头部 / DT_HASH / 模拟装载）

本节的三条结论都可用下面的脚本在未修改的 `libndktest.so` 上复现。

### 10.1 `DT_HASH` 给出的真实符号数

```python
import struct
d = open('libndktest.so', 'rb').read()
u32 = lambda o: struct.unpack_from('<I', d, o)[0]

print('DT_HASH nbucket/nchain =', u32(0x190), u32(0x194))   # 37 / 53
print('section header says     =', 3)                        # .dynsym sh_size/24
```

`nchain=53` 与按 `DT_SYMTAB`(0x300)→`DT_STRTAB`(0x7f8) 间距
`(0x7f8-0x300)/24 = 53` 一致，而节头只声明 3 项。

### 10.2 UPX 容器头字段

```python
import struct
d = open('libndktest.so', 'rb').read()
print(d[0xcc4:0xcc8])                        # b'UPX!'
print(hex(struct.unpack_from('<I', d, 0xcc0)[0]))  # 0xc15c74e0  l_checksum
print(hex(struct.unpack_from('<H', d, 0xcc8)[0]))  # 0x6c0       l_lsize
print(d[0xcca], d[0xccb])                          # 13 42       l_version/l_format
print(hex(struct.unpack_from('<I', d, 0xcd0)[0]))  # 0x17588     b_info.u_len

# 偏移校准：同一版本 UPX 打包的正常样本，+16 处即其原始文件大小
print(hex(struct.unpack_from('<I', open('a.upx','rb').read(), 0x218 + 16)[0]))  # 0x3e10
```

### 10.3 尾部伪造节头导致 `NotPackedException`

对照实验（正常 UPX 打包的 `linux/amd64` 小 ELF）：

```bash
upx -d a.upx                                  # Unpacked 1 file.
python3 - <<'PY'
import struct
d = bytearray(open('a.upx','rb').read())
shnum = 7
tbl = b''.join(struct.pack('<IIQQQQIIQQ', i,1,6,0,0x1000,0x40,0,0,1,0)
               for i in range(shnum))
off = len(d)
d += tbl
struct.pack_into('<Q', d, 0x28, off)          # e_shoff
struct.pack_into('<H', d, 0x3c, shnum)        # e_shnum
open('b.so','wb').write(d)
PY
upx -d b.so                                   # NotPackedException
```

本样本把文件截到 `0x7028` 后，报错从 `NotPackedException` 变为
`CantUnpackException: file corrupted`，说明 UPX 已认出容器。

### 10.4 模拟装载序列

```python
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_INTR, UcError
```

以 `mem_write` 把整个文件映射到地址 0，从 `DT_INIT`=`0x3d28` 起
`emu_start`，在 `UC_HOOK_INTR` 里把 `x8` 当 syscall 号记录，可观察到：

```text
mmap(0, 0x3d24, RW, MAP_PRIVATE|MAP_ANON)
mprotect(buf, 0x3d24, R|X)
mprotect(0, 0x3d24, R|X)
mmap(0, 0xfff0, RW, MAP_FIXED|MAP_PRIVATE|MAP_ANON)
```

随后以 `UC_ERR_READ_UNMAPPED` 终止（`pc=0x800`），即未完成解包。

### 10.5 熵分布佐证

| 文件区间 | 字节数 | 熵 |
|---|---:|---:|
| `0x0d90`–`0x1000` | 624 | 7.190 |
| `0x1000`–`0x2000` | 4,096 | 7.655 |
| `0x2000`–`0x3000` | 4,096 | 7.669 |
| `0x3000`–`0x3d24` | 3,364 | 7.780 |
| `0x3d24`–`0x43d4`（stub 区） | 1,712 | 6.527 |
| `0x43d4`–`0x6000` | 7,212 | 0.840 |
| `0x6000`–`0x7008` | 4,104 | 0.282 |

高熵段是压缩载荷，低熵段是填充与字符串表，stub 区居中——与
“文件里不存在明文方法体”的结论一致。
