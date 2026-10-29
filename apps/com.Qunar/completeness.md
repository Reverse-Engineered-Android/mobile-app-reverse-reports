# 分析完成度矩阵

## 1. 用户要求逐项核对

| 要求 | 状态 | 覆盖证据 | 边界 |
|---|---|---|---|
| 最新 APK | 完成 | `README.md`、`report.md` §1.1 | 样本固定为 `10.4.23` / `345`，SHA-256 已固定 |
| 主要网络交互流程 | 完成，静态 | `network.md` §2、§3、§7 | 未构造请求、未抓真实流量 |
| 协议具体格式 | 完成，静态 | `network.md` §3–§6、`risk.md` §3 | 请求信封布局 + 两道校验已验证；响应侧状态词集未复原 |
| 认证机制 | 完成，静态 | `auth.md` §1–§6 | 服务端票据有效期与风控响应不可见；未登录真实账号 |
| 上传具体范围 | 完成，静态 | `transfer.md` §1、§3 | 未上传文件、未触发实名 |
| 下载具体范围 | 完成，静态 | `transfer.md` §2、§3 | 未实际拉取 CDN/离线包增量 |
| 越权 | 完成 | `permissions.md` §4 | 未安装恶意第三方应用复现 |
| 提权 | 完成 | `permissions.md` §5 | 22 个无保护导出组件均为支付回调/分享/推送类；未发现 `su`/`pm grant`/静默安装闭环 |
| 未经告知 | 完成 | `privacy.md` §2、§6；`permissions.md` §6.1 | 隐私政策正文在服务端，客户端无法证明逐项告知 |
| 超范围获取 | 完成 | `permissions.md` §3、§6.2；`privacy.md` §3、§6 | 按“已采集 / 条件触发 / 未发现”三层划分 |
| 完整风控代码 | 完成，静态闭包 | `risk.md` §1–§8 | 服务端评分、处罚与配置下发结果不可见 |
| 精确判定代码 | 完成 | `risk.md` §2、§3.3、§4、§5；`evidence.md` §2、§4 | 每项机制给出函数地址、DEX 偏移或常量字面量 |
| 加密/混淆不留未知用途 | 部分完成，见 §4 | `risk.md` §3–§5、`storage.md` §1、§9 | 未复原项已逐条列明（响应侧 VM、`NUtils` 六方法），未以推测填补 |
| cparam 指纹族 | 完成 | `network.md` §5、`risk.md` §2 | 字段语义按代码赋值点确定，服务端如何加权不可见 |
| 两套设备风控 SDK | 完成，静态 | `risk.md` §4、§5 | `NUtils` 六个 native 的内部实现未复原 |
| 只读 SSH 数据格式验证 | 完成（设备侧只读核对） | `evidence.md` §6、`storage.md` §2–§5 | 只读核对 schema、文件数量与尺寸，未写入设备 |
| `libgoblin` 信封与两道校验 | 完成 | `risk.md` §3.3、`evidence.md` §2.2–§2.3、§7 | 请求侧布局逐字节验证；响应侧键值集未复原 |
| 离线包 `assets/*.qp` 结构 | 完成 | `network.md` §10、`storage.md` §6 | 未执行离线包 JS |
| 报告目录 | 完成 | `apps/com.Qunar/` | 文件清单见 §2 |

## 2. 文件清单

| 文件 | 作用 |
|---|---|
| `README.md` | 样本、覆盖范围、研究边界与入口 |
| `report.md` | 最终结论与综合矩阵 |
| `network.md` | Hotdog/Pitcher 出口、信封格式、请求头、cparam、`b` 加签、离线包 |
| `auth.md` | 登录功能键、票据与 Cookie 载体、本地凭据、第三方回调 |
| `transfer.md` | `trans` 上传形态、上传目标、下载面与数据范围 |
| `risk.md` | 三层风控：`libgoblin` 信封、cparam 指纹、两套设备风控 SDK |
| `permissions.md` | 52 条权限声明、438 个组件、导出面、越权/提权/超范围判定 |
| `privacy.md` | 同意闸门、告知链、采集触发条件与分层结论 |
| `storage.md` | 明文落盘结论、14 个库、`shared_prefs`、MMKV、离线包、平台面 |
| `evidence.md` | 哈希、地址、反汇编片段、`RegisterNatives` 表、设备只读核对、模拟证据 |
| `completeness.md` | 本完成度矩阵 |

## 3. 静态调查完成定义

本报告的“完成”指：对固定 APK 的 DEX、XML、资源、`assets/*.qp` 与 native
可读证据完成枚举，把每个结论限定到代码能够证明的层级，并对无法从客户端确定
的服务端行为明确标注边界。下列行为按要求保持未执行：

- 未登录真实账号；
- 未构造或发送业务请求；
- 未搜索、未下单、未支付；
- 未上传或下载业务文件，未触发设备数据上报；
- 未触发验证码、未绕过任何风控；
- 未测试越权或提权；
- 未写入或修改设备端任何数据；
- 未抓取真实网络流量。

设备侧操作仅限只读核对：读取应用私有目录下 `databases/` 的文件名与
`PRAGMA`/`integrity_check` 结果、`shared_prefs/` 的文件数量尺寸与键名统计、
`files/kv_storage/` 的 MMKV 文件清单。数据库行值、偏好值、MMKV 内容一律未采集。

`libgoblin_6_1_1.so` 的受控模拟只针对 APK 自带的 `.so` 镜像，输入为合成数据，
未连接任何服务端。

## 4. 加密与混淆闭包

| 对象 | 状态 | 结论 |
|---|---|---|
| Hotdog 明文层 `AbsContentStruct.pack()` | 闭包 | 键值按 TreeMap 顺序拼接，manifest 记录 `start`/`length` |
| `hdog` 信封外层（22 字节头 + 内部块） | 闭包 | 6 字节魔数、两处 `BE64(adler32(…, 1))`、`T29[usec % 29]` 均已逐字节验证 |
| `libgoblin` 信封两处 `adler32` | 闭包 | `BE64(adler32(数据, 1))`，zlib 种子 1，6 组明文逐字节吻合 |
| `DriftsandDe` 第一道校验 | 闭包 | `BE64(hdr[8:16]) == adler32(hdr[16:], 1)`，128 位基向量 + 40 组随机头确认 |
| `DriftsandDe` 第二道校验 | 闭包 | `BE64(hdr[0:8]) == adler32(明文, 1)`，128 位基向量 + 40 组随机头确认 |
| `T29` / `T41` 索引 | 闭包 | 两表均按 `gettimeofday` 的**微秒**字段取模索引（29 / 41），寄存器级读出核对 |
| `libgoblin` `.rodata` 字符串 | 闭包 | 全部为 ASCII 十六进制串按字节 `XOR 0xFF`，解码得 JNI 类名/方法名与 `hehe : NNN` 埋点 |
| `Dea1907_resp` 分派结构 | 结构已证实 | 分派键为内部块头 4 字节；3,242 次比较 / 2,161 个键值；默认分支 `0xae28` 置 `w0=3` |
| `Ena1907_req` / `Dea1907_resp` 的内部变换 | **未复原** | 编译型状态机（0 间接分支、各 1 个 `ret`、3,273/2,547 个直接 `B`/`BL`），内部字节码未还原 |
| 响应侧有效状态词集 | **未复原** | 前 400 个候选键中仅 `0c4ad3e0` 不返回 `3`，且返回 `2`；其余键值语义未还原 |
| `sand` → `drift` 互逆性 | 已否定 | 请求侧状态词取自 `T29`，29/29 不在响应侧键集；两函数方向不同，非互逆 |
| `NUtils` 六个 native（`cd`/`ep`/`uti`/`jcd`/`sepa`/`sfp`） | **未复原** | 经 `libqlynx_v++` 的 `vmEntry` 动态注册，无 `Java_*` 导出；`uti` 槽位已定位（`.data.rel.ro 0x23e5a8` → `.rodata 0x26797c`），其余 5 个槽位未逐一解析 |
| `com.ctripfinance.risk.device` 结果加密 | 闭包 | `AES/CBC/PKCS5Padding`，key = `MD5(gid)[0:8] + timestamp[0:8]`，IV 为固定 16 字节常量，Base64 后上报 |
| `com.qunar.rc` `FpService` 上报体 | 结构已证实 | `multipart/form-data` 单字段 `b`，最多重试 3 次；`b` 的字节内容由 `NUtils.cd()` 生成，内部实现未复原 |
| `shared_prefs` / `files` 落盘 | 闭包 | 全部明文，无应用层加密，机密性依赖 `/data/data` Unix 权限 |
| MMKV 存储 | 闭包 | MMKV 默认 mmap + CRC 格式，非应用自定义加密 |
| `res/xml/network_config.xml` | 闭包 | 只被 `R.xml` 引用，未在清单挂载，属未生效的冗余策略 |
| `assets/*.qp` 离线包正文 | 闭包 | `qp` + u32le 清单长度 + UTF-8 JSON 清单 + 明文 Metro JS 正文，无字节码加密 |

报告未复原的两项（`Ena1907_req`/`Dea1907_resp` 的内部变换，含响应侧有效
状态词集；`NUtils` 六个 native 的方法体）均已在上表与 `risk.md` §7 中显式
标注为未完成，未以推测替代结论。信封外层、两道校验与两表索引已完整复原。
