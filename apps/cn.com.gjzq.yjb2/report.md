# 国金佣金宝 Android 9.05.004 逆向研究报告

## 1. 最终结论

### 1.1 样本

| 项目 | 值 |
|---|---|
| 包名 | `cn.com.gjzq.yjb2` |
| versionName / versionCode | `9.05.004` / `20260903` |
| minSdk / targetSdk / compileSdk | `23` / `32` / `32` |
| APK SHA-256 | `e6cc89f97669d86601843a8c741459c2c3aa74386cb7e2206a7da60e7d10c82d` |
| APK 字节数 | `382,970,270` |
| ZIP 条目 | `7,627` |
| DEX | 1（`classes.dex`，`13,644` 字节，仅壳代码） |
| ARM64 native 库 | `46`（`97,838,372` 字节） |
| ARMv7 native 库 | `41`（`63,726,088` 字节） |
| assets 条目 | `347`（`264,825,336` 字节） |
| `res/` 条目 | `7,091`（`47,904,595` 字节） |
| `<activity>` / `<service>` / `<receiver>` / `<provider>` | `381` / `20` / `8` / `10` |
| 声明权限 | `<uses-permission>` 47 条 / 44 个唯一名：28 个 `android.permission` + 2 个 `android.hardware` + 14 个别名/第三方 |
| Application 类 | `s.h.e.l.l.S` |
| `appComponentFactory` | `s.h.e.l.l.A` |

APK 内 `classes.dex` 只有 `13,644` 字节，真正的业务代码不在此处：外壳
`S.attachBaseContext` 把 `assets/ijm_lib/<abi>/libexec.so` 与 `libexecmain.so`
加密释放到 `files/` 后 `System.load`，再由 `N.al`/`N.b2b` 在 native 侧
还原并注册 DEX。二级载荷是 `assets/yjb.zip`（`178 MiB`，
`2,843` 个 H5 条目、`37` 个业务模块），其中文本资源全部带 `.r` 后缀并被
同一套位置相关 XOR 覆盖（见 §1.5）。

### 1.2 外壳与载荷结构

```text
base.apk
├─ classes.dex                 13,644 B  s.h.e.l.l.{S,A,C,N}
│    ├─ S extends Application   attachBaseContext -> c("<abi>")
│    ├─ S.c(String)             ZipFile(APK).getEntry("assets/ijm_lib/<abi>/libexec{,main}.so")
│    │                          -> 释放到 files/libexec{,main}.so
│    ├─ N.<clinit>              System.load(files/libexec.so)、files/libexecmain.so
│    └─ A extends AppComponentFactory
│                               al()/ga() 反射 ActivityThread.mBoundApplication，
│                               instantiateApplication 返回真实 Application
├─ lib/arm64-v8a/libShanYCore.so  8,840 B   JNI_OnLoad + ptrace 反调试
├─ lib/arm64-v8a/libencrypt.so   10,896 B   Java_com_tztEncrypt_{en,de,ajax,webdata}Encrypt13
├─ lib/arm64-v8a/libhtsfx.so     69,472 B   153 个 x.NN / y.NN BSS 槽位
├─ lib/arm64-v8a/libndktest.so   29,320 B   cn.com.gjzq.crypto.GjEncrypt + RC4/SM4
├─ assets/ijm_lib/{arm64-v8a,armeabi,x86,x86_64}/libexec{,main}.so
├─ assets/ijiami.dat  14,330,583 B  ┐
├─ assets/ijiami.ajm   7,514,954 B  ┘ 加固器数据（同一魔数：04 00 00 00 + 4 字节长度 + 32 字节 hex 串）
└─ assets/yjb.zip    186,793,631 B  H5 业务层（178 MiB，2,843 条目 / 37 模块）
```

`S.ld(Context)`（`S.java:663-749`）的 ABI 判定读
`assets/images/icon_max_data_encrypted_xxxxx.png` 是否存在，再读
`/system/bin/linker` 前 20 字节并按 `bArr[18]` 的 `3`（x86）/ `40`（armeabi）
分支；判定失败时走 `S.a()` 的 `Build.SUPPORTED_ABIS` 反射回退。
`S.il()`（`S.java:464-570`）逐行读 `/proc/self/maps`，以
`/lib64/libart.so`、`/lib64/libaoc.so`、`/bin/linker64` 是否存在区分 64/32 位。

`files/i12196010e51071ed`（设备回落 `69,114,092` 字节，与 `assets/ijiami.dat`
前 40 字节完全一致）是加固器主数据体；`files/app_payload.dex`
（`13,680` 字节，`dex\n035`）是运行时下发的动态 DEX，版本戳记
`files/app_payload.version` 为 `gjzq-objdump-20261029-11`（设备实际写入值为
本次核对时刻的采样标签）。

### 1.3 网络面（静态）

业务 H5 只使用一个 RPC 网关与一组独立子域：

| 用途 | 生产 | 仿真 |
|---|---|---|
| 主 Web/API | `https://webapps.yongjinbao.com.cn` | `https://fzwebapps.yjbtest.com` |
| 运营/评论 | `https://operating.yongjinbao.com.cn` | `https://fzoperating.yjbtest.com` |
| 行情 | `https://quote4j.yongjinbao.com.cn` | `https://fzquote4j.yjbtest.com` |
| 支付 | `https://payment.yongjinbao.com.cn` | `https://fzpayment.yjbtest.com` |
| 理财商城 | `https://mall.yongjinbao.com.cn` | `https://fzmall.yjbtest.com` |
| 发现器 | `https://finder.yongjinbao.com.cn` | `https://fzfinder.yjbtest.com` |
| 智能交易 | `https://smart.yongjinbao.com.cn` | `https://fzsmart.yjbtest.com` |
| 直播交易 | `https://fchatwebtrade.yongjinbao.com.cn` | — |
| APK 分发 | `http://appdl.yongjinbao.com.cn/yjb/android/yjbkh.apk` | — |
| 统计 | `https://growing.yongjinbao.com.cn` | — |

统一 RPC 端点为 `<origin>/yjbwebservice/core[/]`（`Service.js` / `env.js`），
传输为 `POST` + `application/x-www-form-urlencoded;charset=utf-8`，
跨域 `withCredentials:false`，默认超时 `5000 ms`。请求以 `UniqueKey` 方法码分派，
静态可枚举 `169` 个唯一方法码（`method-codes.txt` 与 §2.2）。响应统一包：

```json
{"ERRORNO": 0, "GRID0": {"code": 0, "message": "...", "result": ...}}
```

`ERRORNO=1` 被前端固定解释为“登录超时请返回重新登录”，其它值统一落到
“服务器错误”。评论子站另用 `YCOMMENTPHPSESSID`（`localStorage` 回填）做会话。
明文 HTTP 被显式允许（`android:usesCleartextTraffic="true"`，
`@xml/network_security_config`），且静态资源里有 `http://appdl.…` 与
仿真环境内网地址（报告统一脱敏为 `172.28.x.x` / `192.168.x.x`）。

### 1.4 认证与传输加密

H5 层只有一处对称加密，密钥为常量：

```js
// BaseIndexNew.js:117-124（同 BaseIndex.js:274-280、paramConfig.js APP_SECRET）
const key = CryptoJS.enc.Utf8.parse("g6j0z0q1w0e9bapp");   // 16 B = AES-128
CryptoJS.AES.encrypt(content, key, { mode: ECB, padding: Pkcs7 }).toString();
```

`margintrading/commons.js`、`transfer/announce.js`、`stockLiving/bridgeAPI.js`
在 `u.opKey` 上使用同一模式，并叠加 Base64URL 变体
（`+`→`-`、`/`→`_`、`=`→`.`）。ECB 无 IV、密钥为仓库内常量，因此同一
明文在同一 build 内产生相同密文。Native 侧另有两套：

| 库 | 入口 | 算法 |
|---|---|---|
| `libndktest.so` | `Java_cn_com_gjzq_crypto_GjEncrypt_{Encrypt,Decrypt,ExternProc}` | `_Z3RC4PhiPKhi`（RC4）、`sm4_setkey_{enc,dec}`/`sm4_crypt_cbc`/`sm4_crypt_ecb`/`sms4_{encrypt,cbc_encrypt}`（SM4）、`_Z14pkcs7_input_pdPhPi`（Pkcs7） |
| `libencrypt.so` | `Java_com_tztEncrypt_{en,de,ajax,webdata}Encrypt13` | 单表 CRC32 + `tabKey`，含 `srand` |
| `libEncryptorP.so` | `JNI_OnLoad` → `RegisterNatives("com/bytedance/applog/encryptor/EncryptorUtil","ttEncrypt","([BI)[B")` | AppLog 埋点加密 |

`libndktest.so` 的 `SBOX`/`SBOX32H`/`SBOX32L`（各 `262,144` 字节）表明 SM4
使用 T-table 展开实现；`AlgorithmApi_Encrypt_File` / `Decrypt_File`
（各 `384` 字节）给出文件级加解密入口。

账号态分散在三处：`shared_prefs/Token.xml`、`TokenStatus.xml`、
`account_info.xml`（均整体密文存储，见 §1.6），评论子站用
`YCOMMENTPHPSESSID`，行情/交易经 `yjbInterface` 桥交给 native 会话。

### 1.5 H5 载荷加密：位置相关 keystream（已验证全覆盖）

`yjb.zip` 中全部 `.r` 资源按**同一位置相关 keystream**逐字节异或，与文件名、
大小、扩展名无关。HTML 与 TrueType 双明文交叉验证得到前 16 字节：

```
b3 09 cd 5d e7 1c 06 59 d1 77 a8 02 21 4c c7 9f
```

UTF-8 状态机约束与二元语言模型逐列求解，把密钥流扩展到 `6,165,153` 字节，
覆盖全部 928 个文本资源（`.html/.js/.css/.svg/.less/.ini/.txt`）的完整长度：

| 指标 | 值 |
|---|---|
| 密钥流长度 | `6,165,153` 字节（最大 `.r` 文件同长） |
| SHA-256 | `fb1cd66790b967059021938cb1adc6e430387b6d5007286e24e446b1e875c1fd` |
| 已知区复现 | 126,000..131,072 与 128 KiB 基准 100% 一致 |
| UTF-8 完全合法文件 | `920 / 928` |
| 非法字节 | `1,185` / `128,065,750`（`0.000925%`），集中在 8 个文件 |
| `portal_yjb.zip` | 542 条目、104 个 `.r`，同一 keystream 正常解密 |

剩余 8 个文件的少量非法字节是语言模型歧义或原文件本身含非 UTF-8 字节，不能
据此声称逐字节 100% 正确；公开的是可复现的全长度恢复结果与明确的误差率。
同一 keystream 也覆盖设备侧运行时下载的 `files/yjb/**.r`（`210 MiB`、
`3,382` 个文件）及 `files/h5info.txt.r`；解密后得到模块版本 JSON。

解密后 `yjb.zip` 为 37 个业务模块、2,843 个文件，其中 `smarttrade`（353）、
`me`（315）、`financialMalls`（238）、`ai-customer-service`（238）、
`memberSystem`（231）为最大模块。

### 1.6 本地存储

设备端只读核对得到 `7` 个 SQLite（`33` 张表）与 `69` 个 `shared_prefs`
（69 个均非空）。`PRAGMA integrity_check` 全部为 `ok`：

| 文件 | 表数 | schema / 快照 |
|---|---:|---|
| `10000001@bd_tea_agent.db` | 9 | 埋点事件 schema；快照 `page=46`、`eventv3=0` |
| `Popup.db` | 7 | 弹窗任务流 schema；快照各任务流表为 0 |
| `atta.db` | 3 | 附件 record schema |
| `db_xnzq` | 2 | `CacheTable(key1,version,key2,key3,saveTime,expTime,value)`；快照 `CacheTable=43` |
| `mitake.sse.sqlite.db` | 4 | `Company` / `Stock` / `HkCodes`；快照 `HkCodes=3,785` |
| `signalData.db` | 2 | `SignalDataTable` 复合主键 `(uid,signalType,stockUinKey,lineType)` |
| `thinkive.db` | 6 | 缓存、下载、映射、服务 URL 表 |

`shared_prefs` 是 4 帧 AES block 结构，60 个文件共享前 56 B 密文前缀，6 个
小文件（81 B）完全相同，另 6 个从第 48 字节起分叉；`ijm_ifr.xml` 使用不同
前缀，`crhs_2019.xml` 为明文。密文长度 mod 16 = 1，互 XOR 的可打印率约
0.38，符合随机外观，因此**不支持“跨文件逐位置 keystream 复用”**。具体
算法、密钥与明文均未还原，见 [storage.md](storage.md)。

### 1.7 完成边界

已闭环：外壳装载链、H5 载荷解密、RPC 网关与方法码族、AES-128-ECB 常量密钥、
native 侧 RC4/SM4 符号与调用面、权限面、设备端 schema 与落盘格式。
未闭环：`libShanYCore.so` / `libencrypt.so` / `libhtsfx.so` / `libndktest.so`
的 `.text` 被 `UPX!` 标记段与高熵区包裹（熵 `7.10`–`7.72`），静态反汇编不可
直接读取；`i12196010e51071ed`（`69,114,092` B，熵 `4.646`，`b2` 占 `32.9%`）
无 `dex`/`ZIP`/ELF/gzip 魔数，未复原内部压缩。以上均在
[completeness.md](completeness.md) 中逐项标注为「结构已证实」或「未知」，
不作为已验证结论。
