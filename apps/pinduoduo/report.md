# 拼多多网络、认证、存储与风控逆向报告

## 样本与证据边界

| 字段 | 值 |
| --- | --- |
| 包名 | `com.xunmeng.pinduoduo` |
| 版本 | `8.26.0`，versionCode `82600` |
| minSdk / targetSdk / compileSdk | `21` / `34` / `36` |
| Application | `com.xunmeng.pinduoduo.app.PDDApplicationLike` |
| ABI | `arm64-v8a` |
| base APK 大小 / SHA-256 | `26325735` / `d57b1ebcd757207ad569233ec8d7cf663dd1acb69a2fde8915b58af0aa0d7c1c` |
| DEX | `classes.dex` … `classes6.dex`，291,149 条可打印字符串 |
| 随 APK 的 native 库 | `lib/arm64-v8a` 22 个 |
| assets 内嵌 native 库 | `assets/so_arm64-v8a/*.7z` 3 个（LZMA，内嵌 MD5 与内容一致） |
| 运行时下载 native 库 | 26 个目录，`files/dynamic_so` 共 74 MB |

网络、协议、认证、混淆与 native 结论来自 APK 静态逆向。本地存储结论来自同一设备上
该版本的**只读**快照：只读取文件与数据库页，不做注入、不做调试、不做内存读取、不
重启应用、不产生任何可被检测的操作。快照中存在未 checkpoint 的 `-wal`，已通过
只读方式重放后再读 schema 与行数，见 [storage.md](storage.md)。

## 总体结论

1. 主业务走 HTTPS/JSON 到 `*.yangkeduo.com` / `*.pinduoduo.com`，由一条固定的
   OkHttp 拦截器链处理：HttpDns → 熔断/降级 → 重定向/CDN 改写 → https→http 改写
   → 指标。连接池 `1 / 55s`，仅 GET 在网络失败时重试。
2. 请求头分成四层：**公共头**（`Content-Type`、`Referer: Android`、`AccessToken`、
   `lat`、`ETag`）、**UI/环境头**（`x-app-ui`、`x-pdd-info`、UA 查询串）、
   **反爬头**（`anti-token`，短 token / 长 token 两条路径）、**签名头**
   （v1 走 `SecureNative.i`，v2 走 `SecureNative.sdr`，产出 `x-p-t`/`x-p1`）。
3. 认证以 `AccessToken` 为核心。`AccessToken` 来自 MMKV `pdd_config_common` 的
   `jsSecureKey___ACCESS_TOKEN__`；同时存在 `PDDAccessToken` cookie 与
   `pdd_user_id`/`pdd_user_uin`/`install_token`，这 5 个键被列入 native 保护名单，
   不参与普通 cookie 清理。登录接口族集中在 `/api/sigerus/*` 与 `/api/galilei/*`，
   这些路径同时是 v2 签名的默认启用列表。
4. 风控是纵深体系：anti-token（每请求）、`enCryptInfoV3` 设备信息密文、`scres`
   签名内容注册表、`sdr` 签名描述符、`error_code 54001` 的 `verify_auth_token`
   挑战、root/模拟器/多开检测、设备画像上报、网络降级与 Hook 对抗。
5. 上传走 `/api/galerie/*` 与 `/image|/file/signature`，multipart 固定 boundary，
   视频类任务额外带秒传字段；下载走 iris/okdownload 两个下载器，断点续传元数据
   落在 SQLite。
6. 本地存储以明文 SQLite 为主（22 个库），MMKV 397 个文件（约 19 MB）多数未加密，
   仅少数模块显式传入 crypt key。运行时下载的 native 库以**明文、未加壳**形式
   落在 `files/dynamic_so`，目录名携带 `名称_epoch毫秒_MD5`。
7. **混淆闭包**：DEX 层无字符串加密、无加壳；APK 内 6,648 处 Efix 跳板
   （`h4.g.*`）在未安装热补丁时全部短路。native 侧实际出现的手法有三种，均已
   完整还原：OLLVM 控制流平坦化、ADR+RET 返回地址间接化与 .text 内嵌数据、
   **异或字符串池**（`libpdd_secure.so` 634 条 + `libdyncommon.so` 458 条，
   两库同工具、同密钥表的四个字节行 × `eor`/`eon` 两种算子）、以及
   **`RegisterNatives` 动态注册**（18 个库 28 处注册
   + 1 处注销）。其中字符串池承载了 `libdyncommon` 的 native 侧反 root/反 hook/
   反模拟器探测链与全部 `ab_secure_*` 风控总开关（[risk.md](risk.md) §14）。
   逐库清点见 [obfuscation.md](obfuscation.md) §9。

## 覆盖矩阵

| 要求 | 已覆盖内容 | 入口 |
| --- | --- | --- |
| 主要网络交互流程 | 拦截器链、域名分类、Titan 长连接、下载/上传链路 | [network.md](network.md) |
| 协议具体格式 | 公共头、anti-token、v1/v2 签名、`sdr` 描述符、multipart 表单、Titan 结构 | [network.md](network.md) |
| 认证机制 | `AccessToken` 来源链、登录接口族、token 刷新、54001 挑战、cookie 保护名单 | [auth.md](auth.md) |
| 上传下载数据范围 | 逐接口字段清单与上传表单全字段 | [transfer.md](transfer.md) |
| 全部风控代码 | anti-token / enCryptInfoV3 / scres / sdr / 54001 / root / 模拟器 / 多开 / 画像 / 降级 / Hook / 支付宝设备指纹 | [risk.md](risk.md) |
| 无未分析混淆代码 | DEX 层、Efix 层、逐库 native 层结论、字符串池、动态注册 | [obfuscation.md](obfuscation.md) |
| 本地数据库格式与信息范围 | 22 个库 DDL、行数、列形状；WAL 重放；MMKV 键名 | [storage.md](storage.md) |

## 证据等级

沿用仓库 `docs/evidence-standard.md`：

- **已验证**：字节级常量表、符号、DDL、行数、列形状——可直接复现。
- **结构已证实**：调用链、字段名、gating 开关——入口与数据边界明确。
- **假说**：由调用点归纳的选择子语义、未取到样本的分支。

本报告不公开 APK/SO/DEX/DB 二进制、真实行值、账号/设备标识、token、内网地址与
绝对路径。

## 未决事项

1. `assets/A94/A25` 内置配置的 `CDA.cdnMd5` 与文件实际 MD5 不一致，见
   [algorithm.md](algorithm.md) 的"内置配置"一节：`isPartBackup=true` 表明该
   `cdnMd5` 指向增量下发基线，而不是当前内置文件。
2. `SecureNative.b/s` 的选择子语义由调用点归纳，选择子取值集合尚未逐一断言到
   具体密钥用途。
3. ~~`libpdd_secure.so` 异或字符串池中的 RSA 公钥尚未定位到调用点~~
   **已闭合**：调用点（`0x3764c`/`0x37784`）、跳板与选择子结构、两把公钥的
   逐字节对照、以及"公钥加密上报体"的方向均已给出，见
   [obfuscation.md](obfuscation.md) §9.4.6。仍未展开的只剩 FLA 分派核的逐块
   语义（结构已证实）与 `rsaEncrypt*` 名字的绑定路径。
4. `SE`（11 个）、`meco.cookie.N`（12 个）、`shook.ShadowHook`（14 个）native
   方法的提供库不在本机快照中，见 [obfuscation.md](obfuscation.md) §9.6。
