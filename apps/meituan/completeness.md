# 分析完成度矩阵

## 1. 用户要求逐项核对

| 要求 | 状态 | 覆盖证据 | 边界 |
|---|---|---|---|
| 最新 APK | 完成 | 12.66.404、1200660404、hash | 官方当前版本，样本固定 |
| 主要网络交互流程 | 完成 | `network.md` §2 | 未抓真实流量 |
| 协议具体格式 | 完成 | `network.md` §3-4、§5-6 | native 密文明文字段不从密文猜 |
| 认证机制 | 完成 | `auth.md` | 服务端 token 时长未知 |
| 上传具体范围 | 完成 | `transfer.md` §2-6 | 运行时调用条件未实测 |
| 下载具体范围 | 完成 | `transfer.md` §7 | 动态 URL 未实测 |
| 越权 | 完成 | `permissions.md` §3、§5 | 未运行恶意 app 复现 |
| 提权 | 完成 | `permissions.md` §6 | 未发现系统 UID 提权闭环 |
| 未经告知 | 完成 | `privacy.md` §1-6 | 服务端数据流未知 |
| 超范围获取 | 完成 | `permissions.md`、`privacy.md` | 分“具备/条件/未证实” |
| 完整风控代码 | 完成 | `risk.md` §2、§11 | 服务端评分/处罚不可见；预加载载荷不执行，其容器/校验/加载契约已闭环 |
| 精确判定代码 | 完成 | `risk.md` §2.1、§3、§7-8 | 给出 33 个调用点的声明行、调用行、命令号与 `MainBridge` switch |
| 加密代码不留未知用途 | 完成 | `evidence.md` §5、§5.1-§5.2 | 报文字节格式已给出；28 个 JADX 残留逐一归类；不提取密钥 |
| 支付接口 | 完成，静态 | `network.md` §6 | 未构造支付请求 |
| 周边餐厅/项目/评价 | 完成，静态 | `network.md` §5 | 未构造查询请求 |
| 只读 SSH 数据库验证 | 完成 | `evidence.md` §8、`privacy.md` §5.1、`transfer.md` §9 | 只读经 `/proc/1/root/data/user/0/com.sankuai.meituan`；未写入远端、未发请求 |
| 报告目录 | 完成 | `apps/meituan/` | 见下表 |

## 2. 文件清单

| 文件 | 作用 |
|---|---|
| `README.md` | 样本、范围、入口 |
| `report.md` | 最终结论矩阵 |
| `network.md` | 网络、协议、支付和周边静态接口 |
| `auth.md` | 登录/刷新/PKCE/OAuth |
| `transfer.md` | 上传下载字段与数据范围 |
| `risk.md` | 全部风控命令、采集和 native 库 |
| `permissions.md` | 147 权限、697 组件、越权/提权 |
| `privacy.md` | 同意闸门、采集条件、超范围判断 |
| `evidence.md` | 哈希、源码位置、加密闭包与报文格式、28 个残留归类、预加载契约、设备端 schema |
| `completeness.md` | 本矩阵 |

## 3. 静态调查完成定义

本报告的“完成”指：对固定 APK 的 DEX/XML/native 字符串可读证据完成枚举，
并把每个结论限定到代码能证明的层级。它不包含：

- 登录真实账号；
- 发起支付；
- 请求周边餐厅、项目或评价；
- 抓取账号数据；
- 绕过风控；
- 验证服务端响应、评分或留存；
- 写入或修改设备端任何数据。

这些行为按要求保持未执行。

只读 SSH 检查已实际执行，覆盖：`MTLocationTableV2`、`kitefly.db.log`、
`mt-statistics-db-cache.event`、`request_monitor.db`、`hades_db_sql`、`battery.db`、
`1857661084_message_db.db`、`imkit_db.db`、`dx_sdk_statistics_report.db` 的真实
schema 与行数，`privacy_config` 同意记录、`files/horn` 风控配置、
`._mtg_mtdfp_up` 指纹文件，以及运行进程 `/proc/<pid>/maps` 中的库映射。
残留错误统计为：JADX `--show-bad-code` 50 条，26 个文件含
`Method not decompiled`，共 28 个唯一签名，全部完成归类。
