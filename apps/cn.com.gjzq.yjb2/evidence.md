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
b3 09 cd 5d e7 1c 06 59 d1 77 a8 02 21 4c c7 9f
```

UTF-8 状态机 + 二元语言模型逐列求解的产物为 `6,165,153` 字节，SHA-256：

```
fb1cd66790b967059021938cb1adc6e430387b6d5007286e24e446b1e875c1fd
```

自检：126,000..131,072 区间与早期基准 100% 一致；928 个文本文件中
920 个完全 UTF-8 合法，非法字节 1,185，占 `0.000925%`；结构检查
`940 / 948` 通过、8 个失败。

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

