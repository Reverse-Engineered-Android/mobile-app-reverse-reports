# 拼多多 8.26.0 分析完成度矩阵

本文件把用户提出的要求拆成可核验的代码边界，并明确哪些结论是**已验证**、
哪些是**结构已证实**、哪些仍是**假说**或**未覆盖**。它不是登录绕过、签名伪造、
token 生成或风控规避指南；仓库只发布脱敏结构、证据地址和不含秘密的工具。

## 1. 覆盖矩阵

| 要求 | 已覆盖内容 | 主要入口 | 公开结论 |
| --- | --- | --- | --- |
| 主要网络交互流程 | OkHttp 客户端构造、四类拦截器链、域名分级、Titan 长连接、上传/下载链路、降级与熔断、客户端网络留痕 | [network.md](network.md) | 域名、调用链、请求/响应封装、连接池参数与重试策略已整理 |
| 协议具体格式 | 公共头、`x-app-ui`/`x-pdd-info`、`anti-token` 短/长 token、v1/v2 签名头、`sdr` 描述符、multipart 上传表单、Titan 结构 | [network.md](network.md)、[transfer.md](transfer.md) | 字段与 wire 类型按证据等级标注；不含真实请求/响应 |
| 认证机制 | `AccessToken`/`PDDAccessToken` 来源链、登录接口族、token 刷新、`53001`/`54001` 挑战、cookie 保护名单、`getLoginKey` | [auth.md](auth.md) | 已确认来源、规范化与传输边界；秘密值与可重放材料不公开 |
| 上传数据范围 | 对象存储端点与任务类型、multipart 全字段、视频秒传字段、风控/环境上报字段、上传相关开关 | [transfer.md](transfer.md) | 逐接口、逐字段列出；区分"业务上传""诊断上报""风险上报" |
| 下载数据范围 | iris/okdownload 两个下载器、断点续传元数据、下载内容分类、落盘结构 | [transfer.md](transfer.md)、[storage.md](storage.md) | 按来源、格式与本地缓存范围列出 |
| 全部风控代码 | anti-token、`enCryptInfoV3`、`scres`、`sdr`、54001、root/模拟器/多开、设备画像、网络降级、Hook 对抗、网络留痕、**第三方支付宝设备指纹 SDK** | [risk.md](risk.md) | 组件、Java/native 入口、采集类别、上报边界与处置链已覆盖；含 41 个 `AD`/`AL` 编码的逐条定位 |
| 本地数据库格式与信息范围 | 22 个 SQLite 库（89 张表）的 DDL/行数/列形状、未 checkpoint 的 WAL 重放、396 个 MMKV 文件、`files/secure`、`files/network`、`files/dynamic_so`、SharedPreferences | [storage.md](storage.md) | 逐库列出表名、列名、行数与值域形状；不公开任何行值。**经两次独立只读取证交叉确认**（[evidence.md](evidence.md) §8.1） |
| 无未分析混淆代码 | DEX 层、Efix 跳板层、51 个已取得 ELF 的逐库混淆清点、`RegisterNatives` 调用点、异或字符串池、动态库清单与在机情况 | [obfuscation.md](obfuscation.md) | 见 §3 与 §4：已取得库上闭环，54 个未落盘库与 3 类未绑定 native 方法明确列为未覆盖 |
| 权限弹框追踪 | 59 项 `uses-permission` 分层、设备实测授权态与 appops、三级弹框代码路径与资源文案、MMKV 弹框状态实测、同意闸门链 | [permissions.md](permissions.md) | 声明面、运行时面、弹框文案与状态机均已闭环；`READ_PHONE_STATE` 未声明但保留 15 处代码引用 |
| 未告知/超范围收集 | 两条采集面（`ob2/b.e()` 49 键、`ob2/f.e()` 37 键）字段表、服务端字段黑名单、11 个采集剥离开关、零危险权限采集面 | [privacy.md](privacy.md) | 四项未在政策列举的采集已定位；`privacy_passed_5200` 闸门 6 入口闭环 |
| 隐私协议 vs 实际采集 | 政策 V4.1.1 与协议 V4.2 原文引用及 SHA-256、逐项覆盖判定、剪贴板承诺的可证边界 | [privacy.md](privacy.md) | 三份引用清单不可达（302），对照以正文为基准；剪贴板上传限缩明确标注未证实 |

## 2. 风控组件清单

| 层 | 组件 | 已分析的代码边界 | 证据状态 |
| --- | --- | --- | --- |
| 请求反爬 | `anti-token`（短/长 token 两条路径） | 生成链、有效期、与 `ApiSignatureProcessor` 的顺序关系 | 结构已证实 |
| 请求签名 | `SecureNative.i`（v1）、`SecureNative.sdr`（v2） | 输入（method/url/body/时间戳）、产出头 `x-p-t`/`x-p1`、默认启用路径列表 | 结构已证实 |
| 设备信息密文 | `enCryptInfoV3` | 采集项、序列化容器、AES 封装、上报端点 | 结构已证实 |
| 签名内容注册表 | `scres` | 字段集合、与 MMKV `secure` 的对应关系 | 结构已证实 |
| 签名描述符 | `sdr` | 描述符表 `0x192b71`、条目 32 字节、选择子归一化 | 已验证 |
| 服务端挑战 | `error_code 54001` → `verify_auth_token` | 触发条件、重试与刷新链路 | 结构已证实 |
| 环境完整性 | root / 模拟器（加权打分）/ 多开 / 应用克隆 | 判定项、权重、上报字段 | 结构已证实 |
| 设备画像 | `mi0/a.java` | 采集键含 `adb_enabled`、`development_settings_enabled` | 已验证 |
| Hook 对抗 | `libdyncommon.so` native 环境探测、`libpdd_sa_hook.so`、bytehook 链 | 字符串池已全部还原（四掩码并集 458 条）：Magisk/SuperSU/su、Riru/EdXposed/SandHook/`libSignatureKiller`、模拟器（vboxsf/nemusf/ttVM/ranchu）、SELinux 与 verified-boot、`/proc` 自省、无障碍外挂；外加 `ab_secure_*` 总开关，见 [risk.md](risk.md) §14 与 [obfuscation.md](obfuscation.md) §9.4.5 | 已验证 |
| 风控灰度开关 | `ab_secure_*` 键族（共 50 个：native 11 + DEX 39） | native 侧在 `libdyncommon` 池内；DEX 侧 `ab_secure_skip_*_7630` 一族可逐项剥离位置/基站/WiFi/应用清单/进程采集 | 已验证 |
| 网络降级 | 熔断、https→http 改写、CDN 改写 | 触发条件与优先级 | 结构已证实 |
| 客户端留痕 | `net_adapter/hera/netcapture` | 保留字段范围 | 结构已证实 |
| 第三方指纹 | `apmobilesecuritysdk` + `SecurityClientMobile` | 支付链路触发、41 个 `AD`/`AL` 编码到采集函数的逐条映射、`mobilegw.alipay.com` 上报协议 | 结构已证实（传输与字段）；文件落盘为代码判定 |

## 3. 混淆闭包（用户重点要求）

用户明确要求：**不允许任何未分析的混淆代码，且这不只是指密码学代码**。本节按层
回答，并把边界说清楚。

### 3.1 已取得 ELF 上可以闭环

覆盖范围是 51 个落盘 ELF（去重后 48 个）：APK 自带 22 个 + `files/dynamic_so` 26 个 + assets 内嵌 3 个。

| 层 | 结论 | 判据 |
| --- | --- | --- |
| DEX 字符串 | 无字符串加密 | 291,149 条可打印字符串以明文存在于 6 个 dex |
| DEX 加壳 | 无加壳、无 DEX 加密 | 全部 `classes*.dex` 为标准 `dex\n035` 头，可解析出 25,156 个 Java 文件 |
| DEX 控制流 | 6,648 处 Efix 跳板，未装补丁时全部短路 | `h4.g.f62701a == 0` 时 `h4.g.<letter>()` 直接返回 `{a=false}`；设备 `efix_sp_main.xml` 显示无补丁 dex、失败计数 0 |
| native 控制流 | 三种标准变换，均可静态还原 | FLA 7,376+ 处（`libpdd_secure`）、IND-BR 19,801 处（`libdyncommon`）、ADR+RET 330 处（仅 `libdyncommon`） |
| native 数据 | 标准常量表明文，**但存在异或字符串池，覆盖两个库** | 算法常量表明文位于 `.rodata`；`libpdd_secure`（池首 `0x1928c0`，**四掩码并集 634 条**）与 `libdyncommon`（池首 `0x408fa0`，**458 条**）用**同一构建期工具、同一张密钥表的四个字节行**，两库均已全部还原，见 [obfuscation.md](obfuscation.md) §9.4 |
| native 绑定 | 无符号抹除，注册点已逐一定位 | 1,612 个声明方法中 646 个符号导出；`RegisterNatives`/`UnregisterNatives` 在 18 个库共 **28 处注册 + 1 处注销**真实调用点（函数表下标 `#1720`/`#1728`，两条判据见 [obfuscation.md](obfuscation.md) §9.1.1）；其余归属未落盘库 |

在 48 个去重库中，**没有**出现：自解密代码段、字节码虚拟机、
不透明谓词之外的虚假分支、常量表异或/分片隐藏、DEX 加壳。

实际出现并被完整还原的手法有三类：

- **异或字符串池**（`libpdd_secure.so` 634 条 + `libdyncommon.so` 458 条，并集；同一工具、同一密钥表的四个字节行 × `eor`/`eon` 两种算子；含 `DeviceNative` 类名、两把 RSA 公钥、Android Key Attestation OID、三星录屏组件名、Magisk/SuperSU/Xposed/模拟器路径与 `ab_secure_*` 开关）：见 [obfuscation.md](obfuscation.md) §9.4 与 [risk.md](risk.md) §14；
- **`RegisterNatives` 动态注册**（18 个库 28 处注册 + 1 处注销）：见 [obfuscation.md](obfuscation.md) §9.1；
- **`.rodata` 选择性加密**（明文与密文在 `libdyncommon` 同段共存，"有明文串"不能推出"无池"）：见 [obfuscation.md](obfuscation.md) §9.4.3。

逐库清点见 [obfuscation.md](obfuscation.md) §4–§6。

### 3.2 明确未覆盖的部分

`com.aimi.android.common.build.a` 的 `SoBuildInfo` 清单给出本版本**权威动态库清单
共 199 条**。与设备实际落盘对照，只有 51 个能取得，另外 54 个在本设备上从未下载：

| 状态 | 数量 | 例子 |
| --- | ---: | --- |
| 随 APK 分发 | 22 | `libpdd_secure.so`、`libtronav.so`、`libyoga.so` |
| `files/dynamic_so` 已落盘 | 26 | `libmedia_engine.so`、`libdyncommon.so`、`libpdd_j2v8.so` |
| assets 内嵌 `.7z` | 3 | `libtitan`、`libtronplayer`、`libstatic-webp` |
| **清单内但未落盘** | **54** | `libmeco_cookie.so`、`libriskplugin.so`、`libshadowhook.so`、`libxunwind.so`、`libpapm_trace.so`、`libsargeras.so`、`libchat_msg.so`、`libgiflib.so`、`libwallet_crypto_box.so` |

这 54 个库的加载点在 DEX 侧已全部定位（例如 `libmeco_cookie.so` 在 `w33.a` 的
`System.loadLibrary`、`libriskplugin.so` 在 `apm/risk/lock/c`、`libshadowhook.so`
在 `shook/ShadowHook`），获取路径统一为 `arch.vita` 组件拉取
（`dynamic_so.b.Q` → `dynamic_so.b.D/H`），但**其内部混淆手法没有逐库清点**。
其中 `libwallet_crypto_box.so` 的命名指向密码学职责，属明确的未覆盖项。

本报告不在这些库上做任何"已闭环"的断言。这也是为什么 §3.1 的范围被限定为
"已取得的 51 个 ELF"。

#### 3.2.1 这 54 个库的状态已定：Vita 已注册、按需下发、本机未触发

"未落盘"不等于"缺失"。组件框架的注册表给出了权威状态：

`files/.newLocker/` 下 183 个零字节 `.vlock`，文件名是 **`MD5(组件ID)`**
（可带 `-patch` 与版本后缀）。反解后得**注册表 128 个组件**；
`files/mmkv/vita_local_comp_v2` 的**已安装表 46 条**。两者做差得
**82 个已注册但从未下载**：

| 集合 | 数量 |
| --- | ---: |
| 已注册（`.newLocker` 反解） | 128 |
| 已安装（`vita_local_comp_v2` / `files/.vita`） | 46 |
| **已注册但未下载** | **82** |

`SoBuildInfo` 的 105 个 `absent` 条目中 **54 个**落在这 82 项内；其余 51 个用
`v7alib*` 命名（Vita 用 `v64lib*`），属另一套命名空间，样本设备为 arm64 故不适用。

因此这 54 个库的准确表述是：**已注册、等待按需下发、本机未触发**——
触发需"网络 + 版本策略同时命中"。这**不是分析遗漏**，而是分发策略的正常状态，
也直接解释了 §3.1 之外为什么 `SE`/`meco.cookie.N`/`shook.ShadowHook` 三族
native 方法在 51 个 ELF 中零命中：其提供库
（`libriskplugin` / `libmeco_cookie` / `libshadowhook`）就在这 82 项里。

格式与清单见 [vita.md](vita.md) §3、§5；风控含义见 [risk.md](risk.md) §15.1。

## 4. 可复核证据

| 主题 | 复核位置 | 结果 |
| --- | --- | --- |
| 网络/协议 | [network.md](network.md) | 拦截器链、域名分级、头部构造、Titan 结构闭环 |
| 认证 | [auth.md](auth.md) | token 来源链、登录接口族、刷新与挑战闭环 |
| 上传/下载 | [transfer.md](transfer.md) | 端点、表单全字段、数据分类闭环 |
| 风控 | [risk.md](risk.md) | 组件、入口、采集类别与处置链闭环 |
| 混淆 | [obfuscation.md](obfuscation.md) | 逐库指纹表 + 反扁平化结果 + 绑定三分 + 动态库清单 |
| 算法/常量 | [algorithm.md](algorithm.md) | 字节级常量表地址、导出→密码学映射、选择子体系 |
| 本地存储 | [storage.md](storage.md) | 22 个库 DDL/行数/列形状、WAL 重放、MMKV 边界 |
| 权限/弹框 | [permissions.md](permissions.md) | 声明面 59 项、运行时 22/13/12/1、三级弹框、同意闸门链闭环 |
| 隐私对照 | [privacy.md](privacy.md) | 政策原文、两条采集面字段表、未列举项、零权限采集面闭环 |
| 函数地址与 JNI | [evidence.md](evidence.md) | 地址、哈希、符号、绑定方式与证据等级可复查 |

## 5. 可复现工具

上述每一项结论都随报告附带了工具，位于 [tools/](tools/)：12 个只读脚本，只用 Python
标准库，不含样本或秘密。最常用的两个是
[db_snapshot.py](tools/db_snapshot.py)（对目录做只读 SQLite 快照，含 WAL 重放，
只输出列形状聚合而不输出行值）与 [obfclass.py](tools/obfclass.py)（逐库混淆指纹）。
字符串池用 [xorstr.py](tools/xorstr.py) 还原（默认取四个已还原掩码的并集，
自校验会从文件自身的密钥表推导掩码 A），注册点计数用 [rnbind.py](tools/rnbind.py)。
组件框架用 [vita_registry.py](tools/vita_registry.py)（`locks` 反解 `.vlock` 名、
`mmkv` 解码 MMKV 登记表、`verify` 核验 `md5checker`）。
输入约定见 [evidence.md](evidence.md) §8。

## 6. 研究边界

本报告只适用于自有或获授权样本。它不提供真实账号操作、登录绕过、签名伪造、token
生成、批量抓取或规避服务端风控的方法。服务端评分、阈值、留存、灰度和最终处置规则
不在客户端静态逆向的可证明范围内。

设备侧取证全部为**只读**：读取文件与数据库页，不做注入、不做调试、不做内存读取、
不重启应用、不产生任何可被检测的操作。

## 7. 动态组件框架（Vita）覆盖

用户要求"分析本地数据库格式和存储的信息范围"。组件框架除 `vita-database`
的 4 张表外，其全部落盘面一并给出，见 [vita.md](vita.md)。

| 面 | 状态 | 证据 |
| --- | --- | --- |
| `vita-database` 4 表 DDL + 行数（含 WAL） | **已验证** | DDL 与设备端逐字节一致；行数 3/43/46/0 |
| 组件视图 `files/.vita/<ID>/<版本>/` | **已验证** | 46 个组件、170 个文件全量 MD5 |
| 库视图 `files/dynamic_so/<name>_<epoch>_<md5>/` | **已验证** | 26 个目录、49 个非空文件全量 MD5 |
| 两视图关系（同 MD5、不同 inode） | **已验证** | 10 份双副本 + 13 份仅库视图 |
| `PDD_MANIFEST` 格式 | **已验证** | 46 个文件逐字节读取 |
| `<组件>.md5checker` 格式与逐条核验 | **已验证** | 146 条中 134 条通过，12 条不匹配全为可变 `extra_info.json` |
| `extra_info.json` / `config.json` / `.pkg` 格式 | **已验证**（`digest` 语义已闭合） | 字段、签名公钥、PKCS#1 v1.5 与完整文件范围实测 |
| `.volantis/component.yaml`（构建脚本泄漏） | **已验证** | 1 个组件带该文件 |
| `.newLocker` 命名规则与注册表 128 个 | **已验证** | 183 个 vlock 全部反解，4 个版本锁 |
| 已注册未下载 82 个 | **已验证** | 注册表 128 − 已安装 46 |
| MMKV 登记表 `vita_local_comp_v2` 46 条 | **已验证** | 逐字段解码 |
| 其余 14 个 `vita_*`/`comp_*` MMKV | **已验证**（键族与大小） | 样本多为空 |
| 网络协议 8 个端点 + CDN 路径模板 | **已验证** | 常量与调用点 |
| 拉取响应 / 查询请求字段 | **已验证** | `RemoteComponentInfo` / `UpdateComp` 的 `@SerializedName` |
| 证书固定仅覆盖 3 个 Vita 接口 | **已验证** | `certificate_pinning_enable_uris_77700` 配置串 |
| MD5 + SHA256WithRSA + AES 完整性链 | **已验证**（算法、参数与 `digest` 范围） | `ol0/a0.k()`、`vita/patch/inner/a.b()`、断点库保留的 `x-pos-meta-digest` |
| DEX 侧 6 把 RSA 公钥 | **已验证** | 位宽、DER 长度、SHA-256 |
| `security_key` 解密实现 | **已验证** | 纯 native 链路 `uv2/a`→…→`SecureNative.dv`；AES-128 密钥扩展与 FIPS-197 逐字节一致，见 [vita.md](vita.md) §8.4 |
| `.vlock` 组件 ID 反解 | **已验证** | 183/183 命中（122.5 万候选串），0 未解析 |

**结论**：组件框架的落盘格式、清单格式、登记表、注册表、网络协议与完整性链
已全部给出，可逐条复现；剩余未决已在 [vita.md](vita.md) §12 列明。
