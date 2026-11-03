# 分析完成度矩阵

## 1. 目标逐项核对

| 要求 | 状态 | 覆盖证据 | 边界 |
|---|---|---|---|
| APK 版本与哈希 | 完成 | `README.md`、`report.md` §1.1、`evidence.md` §1 | 样本固定为 9.05.004 |
| 外壳/载荷结构 | 完成，静态 | `report.md` §1.2、`evidence.md` §2、`obfuscation.md` §6、§8 | 载荷内部格式未还原 |
| 主要网络流程 | 完成，静态 | `network.md` §1–§5 | 未登录、未构造或发送请求 |
| RPC 格式（两层网关） | 完成，静态 | `network.md` §2、`evidence.md` §9 | native 侧实际 HTTP 由 native 代发，客户端不可见 |
| `subSystemNo` / `functionId` 分域 | 完成，静态 | `network.md` §2.3–§2.5 | 70 个模板为静态枚举，未逐个验证服务端响应 |
| 短码表（`IAR_INTERFACE` / `Interface` / `UniqueKey`） | 完成，静态 | `network.md` §2.2、`obfuscation.md` §4.1 | 表即真值，无遗漏分支 |
| 实时行情通道（NDB WebSocket） | 完成，静态 | `network.md` §6 | 帧格式精确；未抓真实流量 |
| 认证机制 | 完成，静态 | `auth.md` §1–§7 | 票据有效期与刷新策略为服务端行为 |
| 登录状态机 | 完成，静态 | `auth.md` §3、§4.1、`report.md` §1.4 | 只证实客户端分支，未触发真实登录 |
| SAS/SSO 票据链 | 完成，静态 | `auth.md` §5 | 请求参数精确；实际有效期由服务端定 |
| native 桥 ABI | 完成，静态 | `obfuscation.md` §4.2、`network.md` §2.1 | 62 个功能号被调用、38 个有文档签名 |
| 下载/上传范围 | 完成，静态 | `transfer.md` §1–§5、`network.md` §5 | 未实际下载/上传业务文件 |
| 越权/提权 | 完成，静态 | `permissions.md` §3 | 未进行运行时授权或攻击复现 |
| 隐私采集与告知 | 部分完成 | `privacy.md` §1–§7、`risk.md` §7.4 | 服务端政策、实际上报频率未知 |
| 风控代码（客户端流程） | 完成，静态 | `risk.md` §3–§6、§7.4 | 六类判定点全部为 `===` 比较；评分与处罚为服务端 |
| 风控代码（服务端评分/阈值） | 不可见 | `risk.md` §8 | 未登录、未抓包，静态不可达 |
| 混淆代码（H5 层） | 完成 | `obfuscation.md` §3、§4 | keystream 全长度恢复，误差 0.0011% |
| 混淆代码（DEX 壳层） | 完成 | `obfuscation.md` §2 | 去平坦化后调用链清晰 |
| 混淆代码（native 层） | 客户端层完成，native 边界明确 | `obfuscation.md` §5–§7、`evidence.md` §10 | 符号表、UPX 容器头、解包器骨架已还原；方法体需运行期解包 |
| H5 `.r` 加密 | 完成，已知误差 | `report.md` §1.5、`evidence.md` §3、`obfuscation.md` §3 | 921/928 完全 UTF-8；7 个文件有少量歧义 |
| 本地数据库格式 | 完成，只读 | `storage.md` §1–§6 | 行数为快照，不代表稳定状态 |
| `shared_prefs` 算法 | 未闭环 | `storage.md` §3 | 只证实 4 帧 AES block 对齐，密钥/模式未知 |
| 设备数据验证 | 完成（只读） | `evidence.md` §6 | 未写入、未启动 App、未抓包 |
| 报告发布 | 完成 | 本目录、仓库索引与 `sample-manifest.json` | 仅发布脱敏文字与哈希 |

## 2. 未完成项

- `libShanYCore.so`、`libencrypt.so`、`libhtsfx.so`、`libndktest.so` 都是
  **标准 UPX 6.02 容器**（`libndktest.so` 头部 `0xcc0` 处 `UPX!` +
  `b_info.u_len=0x17588`）。`upx -d` 报 `NotPackedException` 有两个叠加原因：
  **尾部 `b_info` 块与头部不对应**，以及**节头表被排在尾部块之后、使尾部块
  不再贴住 EOF**（六组对照实验见 `evidence.md` §10.3）。真伪对照是
  `DT_HASH` 的 `nchain=53` 说动态符号表有 53 项，而 `.dynsym` 节头只声明
  3 项；
  可执行段字节三到五成不可解码（`34.8%` / `35.4%` / `33.0%` / `49.5%`），
  而 `DT_INIT` 起 288 字节 100% 可解码，即函数体在文件中是密文。
  符号表与解包器骨架可恢复，**方法体未还原**（解包需要运行期 `MAP_FIXED`
  映射，模拟到该步即终止）；
- `files/i12196010e51071ed` 内部格式未知（非 DEX/ELF/ZIP 魔数，非强加密）；
- `shared_prefs` 的 AES key、模式和明文未知；
- OAID、电话状态、风险数据是否上报及服务端保存期限未知；
- 服务端风控评分、阈值、处罚动作与 `getRiskInfo` 判定规则未知；
- 未登录、未抓真实流量，所有网络结论均为静态证据。

## 3. 研究边界

报告不提供登录绕过、签名伪造、交易接口重放、批量抓取或攻击方法。客户端可证明
“代码能采集什么、在何条件触发、写入哪个字段、调用哪个端点”，不能证明服务端
评分、保存期限、处罚或合规告知。

## 4. 与上一版的差异

| 项 | 上一版 | 本版 |
|---|---|---|
| RPC 模型 | 单一 `/core/api` + `UniqueKey` | **两层**：native `YJBExec(1001)` 与浏览器 `/core/api[/json]` |
| `subSystemNo` / `functionId` | 只提到 `519`/`1000` | 四域 + 70 模板 + 23 条业务码对照 |
| 实时通道 | 「更可能由 native 承担」 | **JS 实现的 NDB WebSocket**，含登录帧/心跳/压缩 |
| 认证 | 只有 `beginLogin` 分支 | 完整状态机 + `code:-1` 重放中间件 + SAS/SSO 票据链 |
| 风控 | 风险中心与适当性 | 追加购前闸门、权限位、深链准入、预警规则、短信验证、遥测信封 |
| 混淆 | 只有 keystream 与 stub | 追加三张短码表、native 功能号 ABI、不可解码比例量化 |
| keystream | `fb1cd667…`，920/928 | `b8f6bbc7…`，921/928，`node --check` 82→369 |
| native 容器 | 「非标准 UPX，`upx -d` 失败」 | **标准 UPX 6.02 + 尾部 b_info 错位 + 节头破坏 EOF 对齐**，六组对照实验可复现 |
| 符号数来源 | 按 `DT_SYMTAB`→`DT_STRTAB` 间距推算 | **`DT_HASH` 的 `nchain` 直接给出**（53），与间距推算互证 |
| native 装载 | 只记录 `libShanYCore.so` 的调用序列 | 追加 `libndktest.so` 同型序列 + 熵分布 + 解包器骨架 |
