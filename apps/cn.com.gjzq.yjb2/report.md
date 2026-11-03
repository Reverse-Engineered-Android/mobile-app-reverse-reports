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
├─ assets/ijiami.ajm   7,514,954 B  ┘ 加固器数据（同一魔数：04 00 00 00 + 4 字节长度 + 32 个十六进制字符）
└─ assets/yjb.zip    186,793,631 B  H5 业务层（178 MiB，2,843 条目 / 37 模块）
```

`S.ld(Context)`（`S.java:663-749`）的 ABI 判定读
`assets/images/icon_max_data_encrypted_xxxxx.png` 是否存在，再读
`/system/bin/linker` 前 20 字节并按 `bArr[18]` 的 `3`（x86）/ `40`（armeabi）
分支；判定失败时走 `S.a()` 的 `Build.SUPPORTED_ABIS` 反射回退。
`S.il()`（`S.java:464-570`）逐行读 `/proc/self/maps`，以
`/lib64/libart.so`、`/lib64/libaoc.so`、`/bin/linker64` 是否存在区分 64/32 位。

`files/i12196010e51071ed` 是加固器数据体：设备当前版本为 `69,262,848` 字节，
头部是 `04 00 00 00` + 等于自身大小的 LE 长度 + 32 个十六进制字符（16 字节
ID），与 `assets/ijiami.dat` 的 40 字节头部**并不相同**（后者的长度字段是
`69,114,092`，即解包后大小，而文件本身只有 `14,330,583` 字节），两者同格式
但不同实例。`files/app_payload.dex`（`13,680` 字节，`dex\n035`）、
`files/app_payload.version` 与 `files/gjzq_*` 均为本次分析注入的
`GjzqProbe`（版本戳 `gjzq-objdump-20261029-11`），不是 App 下发的动态 DEX
或风控载荷，已排除在样本结论之外。

### 1.3 网络面（静态）

业务 H5 使用**两层 RPC**：交易/风控类请求经 native 代发（H5 只写参数），
资讯/评论类请求由浏览器直发。二者共用一套 `serviceName` 与业务码。

| 层 | 调用点 | 出口 |
|---|---|---|
| Native（主通道） | `fetchYJBAPI` → `YJBExec(1001, {functionId, subSystemNo, tradeType, param})` | 由 native 进程发出 |
| 浏览器 | `fetchToIAR` / `@sinolink/http` | `https://webapps.yongjinbao.com.cn/yjbwebservice/core/api[/json]` |
| 资讯直连 | `fetchTo(…, Interface)` | `stockInformationSiteBak`（阿里云 OSS） |

`subSystemNo` 分四个域：`103` 普通交易、`112` 融资融券、`519` YJBAPI 业务总线、
`200` 资讯行情；`tradeType` 取 `1`（普通）/`2`（信用）。静态抽出 **70** 个
交易参数模板与 **23** 条「函数号 ↔ 业务码 ↔ 中文名」三元组（如
`302` 普通委托、`304`/`401` 撤单、`713`/`335103` 客户委托流水、
`719`/`335004` 委托撤单、`720`/`335505` 两融合约）。业务 H5 只使用一个 RPC
网关与一组独立子域：

| 用途 | 生产 | 仿真 |
|---|---|---|
| 主 Web/API | `https://webapps.yongjinbao.com.cn` | `https://fzwebapps.yjbtest.com` |
| 运营/评论 | `https://operating.yongjinbao.com.cn` | `https://fzoperating.yjbtest.com` |
| 行情 HTTP | `https://quote4j.yongjinbao.com.cn` | `https://fzquote4j.yjbtest.com` |
| 行情推送 WS | `wss://quotezy.yongjinbao.com.cn:8443` | `wss://fzquotezy.yjbtest.com:30443` |
| 支付 | `https://payment.yongjinbao.com.cn` | `https://fzpayment.yjbtest.com` |
| 理财商城 | `https://mall.yongjinbao.com.cn` | `https://fzmall.yjbtest.com` |
| 发现器 | `https://finder.yongjinbao.com.cn` | `https://fzfinder.yjbtest.com` |
| 智能交易 | `https://smart.yongjinbao.com.cn` | `https://fzsmart.yjbtest.com` |
| 直播交易 | `https://fchatwebtrade.yongjinbao.com.cn` | — |
| APK 分发 | `http://appdl.yongjinbao.com.cn/yjb/android/yjbkh.apk` | — |
| 统计 | `https://growing.yongjinbao.com.cn` | — |

浏览器通道为 `POST` + `application/x-www-form-urlencoded;charset=utf-8`，
跨域 `withCredentials:false`，超时 `30,000 ms`；请求以 `UniqueKey` 方法码分派，
短码表 `IAR_INTERFACE` 有 **28** 条（24 form + 4 json），全库 `UniqueKey`
字面量 **169** 个。响应固定两层：

```json
{"ERRORNO": 0, "GRID0": {"code": 0, "message": "...", "result": ...}}
```

`ERRORNO=1` 被前端固定解释为“登录超时请返回重新登录”。native 通道是
**两级判定**：`result.code === 0` 且 `result.result.errorNo === 0`，业务体是
`result.result.data` 这一层 JSON 字符串。评论子站另用 `YCOMMENTPHPSESSID`
（`localStorage` 回填）做会话。明文 HTTP 被显式允许
（`android:usesCleartextTraffic="true"`），且静态资源里有 `http://appdl.…` 与
仿真环境内网地址（报告统一脱敏为 `172.28.x.x` / `192.168.x.x`）。

实时行情走独立 WebSocket（`NDBCLIAPI.js`），不是 `libndbcliapi.so` 的封装：
登录帧带 `device_type:101` / `sdk_version:"SDK-web-v1.5.0"` /
`device_id_internal`（**每次连接新生成的随机 UUID v4**，不是硬件 ID），
心跳 `120 s`，关闭码 `3999` 视为正常，`gtsver` 非空后所有帧改走自研
GTS-JSON 二进制（基值 32、基数 223、头 2 字节固定 `0x98 0xBE`）。
详见 [network.md](network.md) §6。

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

另有第二把常量密钥 `z$B&E)H@McQfTjWn`，只用于**解密服务端返回的账号标识**
（`userId` / `clientId` / `passportId`），解出的值直接喂给火山 finder 做埋点
绑定。两把都是参数混淆而非认证：密钥随包分发、ECB 无 IV，不构成请求真实性
保证。唯一带服务端密钥的签名出现在 `ai-adviser` 的非 App 分支
（`apiSign = md5(apiKey + token + userId + "475830")`，仅 `!window.isApp` 时
生效），App 内走 native 票据、不签名。

账号态分散在四处：`shared_prefs/Token.xml`、`TokenStatus.xml`、
`account_info.xml`（均整体密文存储，见 §1.6）、WebView Cookie
`yjb-webapp-session`（金融账户体系会话），评论子站用 `YCOMMENTPHPSESSID`，
行情/交易经 `yjbInterface` 桥交给 native 会话。登录的真实状态机是
`fetchYJBAPI` 的 `loginStatus` / `tradeOvertime` 判定：未满足时先弹登录、
成功后**在同一 Promise 链里补发原业务请求**；`@sinolink/http` 另有一套
`code === -1` + 占位符（`($fundAccount)` 等）→ 翻译成登录类型 → 登录 →
重放原请求的中间件。详见 [auth.md](auth.md) §3、§4。

### 1.5 H5 载荷加密：位置相关 keystream（已验证全覆盖）

`yjb.zip` 中全部 `.r` 资源按**同一位置相关 keystream**逐字节异或，与文件名、
大小、扩展名无关。`.r` 载荷合计 `131,312,489` 字节，最大成员
`ai-adviser/optional.js.r` 为 `6,165,153` 字节。HTML 与 TrueType 双明文交叉
验证得到前 16 字节：

```
b3 09 cd 5d e7 1c 06 59 d1 77 a8 02 21 4c c7 98
```

UTF-8 状态机约束与二元语言模型逐列求解，把密钥流扩展到 `6,165,153` 字节，
覆盖全部 928 个文本资源（`.html/.js/.css/.svg/.less/.ini/.txt`）的完整长度：

| 指标 | 值 |
|---|---|
| 密钥流长度 | `6,165,153` 字节（最大 `.r` 文件同长） |
| SHA-256 | `b8f6bbc75f3d5b667239114a1366d41d4420e843a477560f2dbd58c35fb2ceba` |
| 已知区复现 | 126,000..131,072 与 128 KiB 基准 100% 一致 |
| UTF-8 完全合法文件 | `921 / 928` |
| 非法字节 | `1,401` / `128,065,750`（`0.001094%`），集中在 7 个文件 |
| TrueType 魔数 | `20 / 20` 通过 |
| `node --check` 通过的 JS | `369 / 507` |
| `portal_yjb.zip` | 542 条目、104 个 `.r`，同一 keystream 正常解密 |

早期版本在偏移 `15`（`0x9f`→`0x98`）与 `21`（`0x51`→`0x52`）各有一个歧义
字节，是两份哈希唯一不同的地方。修正是被 89 个共享 webpack 序言文件共同证实
的：旧值解出 `!function(e){fuiction#t(` / `vau t={}8fu`，新值解出
`!function(e){function t(` / `var t={};fu`；单字节修正让 `node --check` 的
通过数从 `82` 升到 `369`，且没有任何文件由通过变失败。详见
[obfuscation.md](obfuscation.md) §3.2。

剩余 7 个文件的少量非法字节是语言模型歧义（集中在长中文文案与 base64 字面量
附近），不能据此声称逐字节 100% 正确；公开的是可复现的全长度恢复结果与明确的
误差率。同一 keystream 也覆盖设备侧运行时下载的 `files/yjb/**.r`（`210 MiB`、
`3,382` 个文件）及 `files/h5info.txt.r`；解密后得到模块版本 JSON。

解密后 `yjb.zip` 为 37 个业务模块、2,843 个文件，其中 `smarttrade`（353）、
`me`（315）、`financialMalls`（238）、`ai-customer-service`（238）、
`memberSystem`（231）为最大模块。

### 1.6 本地存储

设备端只读核对（`2026-11-02` 快照）得到 `7` 个 SQLite、`30` 个
`sqlite_master` 对象（`23` 张业务表 + `7` 个 `android_metadata`）与
`70` 个 `shared_prefs`（70 个均非空）。`PRAGMA integrity_check` 全部为 `ok`：

| 文件 | 表数 | schema / 快照行数 |
|---|---:|---|
| `10000001@bd_tea_agent.db` | 7 | 埋点事件 schema（`packV2`/`trace`/`eventv3`/`profile`/`custom_event`/`launch`/`page`）；快照 `eventv3=92`、`page=20`，其余 0 |
| `Popup.db` | 6 | 弹窗任务流 schema；快照各表均为 0 |
| `atta.db` | 1 | `record(_id,process,params)`；快照 0 |
| `db_xnzq` | 1 | `CacheTable(key1,version,key2,key3,saveTime,expTime,value)`；快照 44 |
| `mitake.sse.sqlite.db` | 3 | `Company` / `Stock` / `HkCodes(type,code)`；快照 `HkCodes=3,787` |
| `signalData.db` | 1 | `SignalDataTable` 复合主键 `(uid,signalType,stockUinKey,lineType)`；快照 7 |
| `thinkive.db` | 4 | `data_cache_table` / `download_table` / `map_table` / `server_url_table`；快照均为 0 |

行数随 App 使用状态变化，只作本次快照记录。`shared_prefs` 目录 `70` 个 XML
全部非空：`61` 个文件共享同一 56 字节密文前缀，组内首个差异字节落在
`64`/`80`/`96`（少数 `81`）等 16 字节块边界；`6` 个 `81` 字节文件彼此
逐字节相同并共用另一个 56 字节前缀；`crhs_2019.xml` 是唯一明文 XML，
`ijm_ifr.xml`、`ijm_sharedecryption.xml` 使用各自前缀。`66` 个文件长度
`mod 16 == 1`，`4` 个例外（1 个明文 + 3 个长度不满足该式的小文件）。组内
两两 XOR 的可打印 ASCII 比例均值约 `0.22`，低于随机期望 `0.37`，因此
**不支持“跨文件逐位置 keystream 复用”**。算法、密钥与明文均未还原，见
[storage.md](storage.md)。

### 1.7 完成边界

已闭环：外壳装载链、H5 载荷解密（含两字节歧义修正）、两层 RPC 网关与
`subSystemNo`/`functionId` 分域、三张短码表与 native 功能号 ABI、
AES-128-ECB 双常量密钥、登录状态机与 SAS/SSO 票据链、**四个关键 ELF 的
离线解包与逐函数分析**、客户端六类风控判定点、权限面、设备端 schema 与
落盘格式。

native 层不再是边界。四个库都是**标准 UPX 容器**（`libndktest.so` 的
`0xcc0` 处 `UPX!` + `b_info.u_len=0x17588`），尾部 `b_info` 块之后被追加了
真实节头表，把尾部块推离 EOF，因此原样 `upx -d` 报 `NotPackedException`
（六组对照实验见 [evidence.md](evidence.md) §10.3）。把文件截到
「最后一个 `UPX!` 减 4 再加 `0x28`」——四库分别是 `0x702c` / `0x2834` /
`0x202c` / `0x10d01`——再执行 `upx -d -f` 即全部解包成功，脚本见
`tools/upx_recover.py`，四个库的解包哈希见 [evidence.md](evidence.md) §10.6。
解包后 `.text` 线性反汇编覆盖率从 `33.0%` / `35.4%` / `34.8%` / `49.5%`
升到**全部 `100.0%`**，`libndktest.so` 31 个具名函数逐个 `100%`。

逐函数还原的要点（[obfuscation.md](obfuscation.md) §7）：`libndktest.so`
的 `.rodata 0x5830` 是**标准 SM4 S-box**，`sms4_set_encrypt_key` 的
SM4 系统参数与 `CK[0]=0x00070e15` 以立即数出现，仿真实跑得到
`rk[0]=0xf12186f9` / `rk[31]=0x9124a012`，**密钥编排与标准 SM4 一致**；
`AlgorithmApi_Encrypt` 用 `mov`+`strb` 在栈上拼出三条密钥缓冲区（含
`19840826` / `19881105` 两个日期串），`GjEncrypt_Encrypt` 是「位置置换 →
RC4 → 位置置换 → 分段 RC4 → 位置置换」的多段串联；`libencrypt.so` 是
**RC4 + CRC32 完整性校验信封**（`tabKey` 为标准 CRC32 表、8 字节步长）；
`libShanYCore.so` 的 `JNI_OnLoad` 先 `ptrace(PTRACE_TRACEME)`，再用
`b ^ 0x1b`（`;` 与 `d` 转义）解码出 `WifiManager` 反射面与
`disableNetwork`/`enableNetwork`/`disconnect`/`reconnect`；`libhtsfx.so`
是梆梆（ijiami）壳，含 `fake_dlopen`、`/proc/self/maps` 自建链接视图、
zlib 与 `AAsset*` 载荷读取、以及恒真不透明谓词自旋陷阱。

未闭环（三条，均已在 [completeness.md](completeness.md) 逐项标注）：

1. `libndktest.so` 的 SM4 密钥编排与块函数均已用 unicorn 直接调用
   `sms4_set_encrypt_key`（`0x3f80`）/ `sms4_encrypt`（`0x3384`）复现
   GB/T 32907-2016 标准测试向量 `681edf34d206965e86b3e94f536e4246`，
   即**完整等价于标准 SM4**。库内另有 `sm4_crypt_ecb` 走的表驱动优化路径
   依赖运行期填入的 `.bss` 展开表（`SBOX32H`/`SBOX32L`），静态仿真未
   填入所以该路径输出不同，但这不影响 `sms4_encrypt` 本身已证等价标准 SM4
   的结论。
2. `i12196010e51071ed`（`69,262,848` B，熵 `4.639`，`0xb2` 占 `32.8%`）无
   `dex`/ZIP/ELF 魔数，内部格式未复原；壳从它解出的第三层代码只存在于匿名
   `r-xp` 段。
3. 服务端风控评分/阈值/处罚、OAID 实际上报与保存期限，静态不可达。

以上不作为已验证结论；其余各项的边界见
[completeness.md](completeness.md)。
