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
- `assets/ijiami.dat`：`14,330,583` 字节；`assets/ijiami.ajm`：`7,514,954` 字节。
- `assets/yjb.zip`：`186,793,631` 字节、`2,843` 条目、`37` 个业务模块。
- `libShanYCore.so` / `libencrypt.so` / `libhtsfx.so` / `libndktest.so` 的
  ELF 符号、导出名和高熵区测量见 [report.md](report.md) §1.2、§1.4。

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
920 个完全 UTF-8 合法，非法字节 1,185，占 `0.000925%`。

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

快照结果：7 个 SQLite、33 张表、69 个非空 `shared_prefs`；未输出设备 UUID、
账号票据、数据库行内容或真实路径。

## 7. 证据等级

| 术语 | 含义 |
|---|---|
| 已验证 | 可从 APK/资源/只读设备快照直接复现 |
| 结构已证实 | 符号、调用点、schema 或格式存在，但完整语义未还原 |
| 未知 | 需要运行时、服务端或高熵 native 深度反汇编才能闭环 |
