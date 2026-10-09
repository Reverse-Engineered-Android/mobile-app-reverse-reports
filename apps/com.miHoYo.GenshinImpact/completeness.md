# 分析完成度矩阵

## 1. 用户要求逐项核对

| 要求 | 状态 | 覆盖位置 | 边界 |
| --- | --- | --- | --- |
| 逆向最新版原神 APK | 完成 | `README.md`、`evidence.md` §1 | 7.1.0 / 1242，样本固定并给出全量哈希 |
| 主要网络交互流程 | 完成 | `network.md` §2-3 | 只做静态；未发包 |
| 协议具体格式 | 完成 | `network.md` §2.2-2.4、§3 | 请求格式与帧结构精确；业务命令号未公开 |
| 认证机制 | 完成 | `auth.md` | 服务端 token 有效期与重放策略未知 |
| 上传具体数据范围 | 完成 | `transfer.md` §2-4、`privacy.md` §3 | 运行时触发条件未实测 |
| 下载具体数据范围 | 完成 | `transfer.md` §5 | 动态 CDN URL 未实测 |
| 是否越权 | 完成 | `permissions.md` §4、`report.md` §2.5 | 未在设备上实际调用复现 |
| 是否提权 | 完成 | `permissions.md` §5 | 结论为“未发现提权闭环” |
| 是否未经告知 | 完成 | `privacy.md` §2、§4 | 服务端数据流未知 |
| 是否超范围获取数据 | 完成 | `privacy.md` §3、§6 | 区分“具备能力”与“已实施” |
| 完整逆向风控代码 | 完成 | `risk.md` §2-8 | 需服务端配置的阈值/条目不可见 |
| 不留未分析清楚的加密代码 | 完成 | `risk.md` §7、`evidence.md` §5 | 算法/模式/调用点闭合；密钥不导出 |
| 混淆代码完整分析算法和原理 | 完成 | `evidence.md` §2、`risk.md` §8 | 1 处第一方反编译残留已定性为 UI 回调 |
| 风控机制、判定依据的精确代码 | 完成 | `risk.md` 全表、`evidence.md` §4 | 均给文件与行号 |
| 只读 SSH 验证数据库格式 | 完成（边界明确） | `transfer.md` §6、`privacy.md` §5、`evidence.md` §8 | 目标包无数据目录；国服包 DDL 只作旁证 |
| 静态只读调查服务器交互方式 | 完成 | `network.md` §2-3 | 未发起请求 |
| 游戏 wire 协议 | 完成 | `network.md` §3、`evidence.md` §5 | KCP/UDP 与 TLS/DTLS record 分开记录；AES-GCM/CCM 精确到 mode、AAD、nonce、tag |
| 数据格式 | 完成 | `network.md` §2-3、`client.md` §4 | JSON/Gson、Protobuf runtime、Unity 归档 |
| GUI 与状态机 | 完成 | `client.md` §1-3、`il2cpp.md` §5 | BuildSettings/Animage/GUI 层级可读；私有托管方法名表不可读 |
| IL2CPP 代码 | 完成 | `il2cpp.md` §1-4 | 静态链接段与 FDE 已索引；不声称私有元数据已解密 |
| shader 格式 | 完成 | `client.md` §5 | 只描述容器与索引，不导出 blob |
| 美术素材格式 | 完成 | `client.md` §4 | 只描述容器格式，不发布素材 |
| 渲染管线主要逻辑 | 完成 | `client.md` §5 | Vulkan/GLES、命令缓冲、PSO 白名单 |
| 不做实际测试 | 遵守 | 全文 | 未登录、未发包、未运行游戏、未绕过风控 |
| 不公开美术素材 | 遵守 | `client.md` §6 | 未导出任何素材 |
| 报告只写最终结论 | 遵守 | 全文 | 不含过程性措辞 |
| 报告写入指定仓库路径 | 完成 | `apps/com.miHoYo.GenshinImpact/` | 见 §2 |

## 2. 文件清单

| 文件 | 作用 |
| --- | --- |
| `README.md` | 样本、核心结论、入口、边界 |
| `report.md` | 最终结论与判定矩阵 |
| `network.md` | 控制面/实时面协议与帧格式 |
| `auth.md` | 登录、token 链、年龄门 |
| `transfer.md` | 上传下载字段与数据范围 |
| `risk.md` | 风控全量清单与加密用途闭合 |
| `permissions.md` | 27 权限、13 导出组件、越权/提权 |
| `privacy.md` | consent 闸门、采集面、超范围判断 |
| `client.md` | GUI、状态机、shader、素材格式、渲染管线 |
| `il2cpp.md` | IL2CPP 代码布局、元数据边界、GUI/应用状态机 |
| `evidence.md` | 哈希、源码位置、native 地址、证据等级 |
| `completeness.md` | 本矩阵 |

## 3. 静态调查的完成定义

“完成”指：对固定 APK 的 DEX、Manifest、native 字符串/符号/反汇编与
资源容器完成可读证据枚举，并把每个结论限定到代码能证明的层级。它不
包含：

- 登录真实账号或请求游戏服务器；
- 抓取或重放真实流量；
- 运行游戏、验证码或风控绕过；
- 提取或发布密钥、盐、HMAC key、token 与美术素材；
- 验证服务端接收率、留存期限或处罚映射。

设备端验证仅限自有设备的只读检查。目标包没有安装数据，国服同公司包的
DDL 仅作旁证；该限制已在 `transfer.md`、`privacy.md`、`evidence.md`
中如实记录。
