# 完成度矩阵

## 1. 交付物

| 文件 | 内容 | 状态 |
|---|---|---|
| [README.md](README.md) | 样本、范围、研究边界与入口 | 完成 |
| [report.md](report.md) | 最终结论、关键事实与风险判断 | 完成 |
| [network.md](network.md) | MTOP URL、参数、头、接口族、搜索/浏览/下单/票夹 | 完成 |
| [auth.md](auth.md) | 签名、登录、token、cookie、生物识别与会话 | 完成 |
| [transfer.md](transfer.md) | 上传下载字段、媒体、动态包、设备缓存 | 完成 |
| [risk.md](risk.md) | 运行环境、评分阈值、419/420、设备风险、采样 | 完成 |
| [permissions.md](permissions.md) | 58 权限、79 导出组件、越权/提权结论 | 完成 |
| [privacy.md](privacy.md) | 同意闸门、位置/标识/媒体/遥测、告知边界 | 完成 |
| [evidence.md](evidence.md) | 哈希、公式、符号地址、残留归类、设备 schema | 完成 |
| [completeness.md](completeness.md) | 完成度、限制、验收门 | 完成 |

## 2. 用户要求验收

| 要求 | 覆盖位置 | 结论 |
|---|---|---|
| 最新 `cn.damai` APK | [report.md](report.md) §1.1 | `9.0.35`、versionCode `109003500`、APK 哈希已记录 |
| 主要网络交互流程 | [network.md](network.md)、[auth.md](auth.md)、[transfer.md](transfer.md) | 搜索、活动浏览、购票、票夹、认证、上传下载均有静态链路 |
| 协议具体格式 | [report.md](report.md) §1.3、[network.md](network.md) | URL、入口、参数、头、查询串、响应/错误字段均给出 |
| 认证机制 | [auth.md](auth.md) | MTOP 签名、Havana/mlogin/UCC、cookie、token、passkey/生物识别均覆盖 |
| 上传下载具体范围 | [transfer.md](transfer.md) | 实名、视频、头像、评论、推送 token、动态包、SO、VR/资源均有字段级范围 |
| 越权判断 | [permissions.md](permissions.md) §5 | 区分清单能力、导出入口、未做 exploit 的边界 |
| 提权判断 | [permissions.md](permissions.md) §5 | 未发现系统 UID/私有权限绕过；安装权限不当作提权 |
| 未经告知或超范围 | [privacy.md](privacy.md) §8 | 同意链与数据能力已核；服务端用途/保留期不可证 |
| 风控代码完整逆向 | [risk.md](risk.md) | 七项检测、评分分支、native 对拍、419/420、接口锁、设备证据已覆盖 |
| 精确判定依据 | [risk.md](risk.md) §2-4 | 给出 Java 行号、函数地址、寄存器语义、阈值和调用分流 |
| 不保留未分析加密 | [evidence.md](evidence.md) §2-4 | shell XOR、RC4、AES/SM、TLS、签名用途全部闭包 |
| 搜索/购票/票夹静态只读 | [network.md](network.md) | 只读源码调用面，没有实际请求 |
| 手机 DB 只读验证 | [evidence.md](evidence.md) §6 | schema、行数、哈希已记录；无行值发布 |
| 不保留过程性修订措辞 | 全部文件 | 最终文本只给结论 |

## 3. 覆盖深度

### 3.1 网络接口

- 解析唯一 MTOP API `655` 个。
- 明确 `protocol + domain + entrance + apiName + version + "/"`。
- 明确 `gw` / `gw-open` 三个环境域名和 38 项参数到头映射。
- 搜索：`mtop.damai.wireless.search.search` 及搜索筛选/联想/结果字段。
- 活动浏览：项目详情、场馆地图、影片/榜单、周边 POI、门店与评论。
- 购票：构建订单 → 调整 → 创建订单的静态调用链。
- 票夹：票夹列表、票券详情、转赠接受/管理、评论等接口族。
- 未对上述接口发请求，未观察服务端响应。

### 3.2 认证与敏感交易

- `InnerSignImpl` 的四种载荷和 base string 字段顺序。
- 直接 HMAC-SHA1、SecurityGuard `requestType=7`、MiddleTier、AVMP。
- Havana/mlogin/UCC、cookie 持久化、refresh、passkey/biometric。
- 密码 RSA 加密、支付校验、银行卡图片、实名接口字段。
- 只描述协议和调用面，不提供绕过、伪造或攻击步骤。

### 3.3 风控

- `RuntimeProtector` 七项检测的 Java/native ABI、导出 RVA、转发关系。
- `libalisecuritysdk.so` 的系统调用、文件路径、`xhook` 证据。
- BehaviX `getSkuScore` / `getConfirmScore` 的全部分支和默认阈值。
- `TocNative` 四个评分/摘要函数地址和 Java/native 对拍错误码。
- 419/420 的 SESSION/ANTI/普通错误分流、20 秒验证窗口、重试/失败。
- 流控错误码、`ApiLockHelper` 的 10 秒回退锁。
- `wua`、`umidToken`、`Dm-token`、设备等级评分的客户端传输边界。
- `PrivacyDoubleList` 的方法集合、JSON 结构、`lmt/crt/dh` 类型。
- 每个加密/混淆 surface 均有算法、参数、调用点或未使用证明。

### 3.4 权限与隐私

- 58 个权限按网络、位置、相机/音频、存储/安装、推送厂商分类。
- 52 个 activity、15 个 service、11 个 receiver、1 个 provider 的
  exported 清单和保护状态。
- 主启动同意 → 初始化、拒绝 → 二次选择、拒绝后退出的精确分支。
- 坐标 API 白名单、权限前置、广告开关、媒体缩放、日历能力。
- 风险结论区分“静态能证明”与“服务端/运行时不可证”。

### 3.5 本地数据格式

只读验证六个实际数据库，覆盖：

```text
accs.db
data_cache.db
message_accs_db
ticketlet.db
ut.db
yk_gaiax.db
```

记录完整 SHA-256、表名、列名、行数；不记录行值、设备 ID、坐标、
token 或私人内容。

## 4. 残留与混淆完成度

| 指标 | 数值 | 完成状态 |
|---|---:|---|
| JADX 残留唯一签名 | 2,068 | 100% 归类 |
| 涉及文件 | 1,577 | 全部纳入签名统计 |
| 密码学宽关键词子集 | 52 | 算法/用途/调用闭包 |
| 外壳/加固残留 | 0 | 不存在未归类残留 |
| 未分类残留 | 0 | 完成 |

分类结果为：三方/基础设施 672、业务/票务 378、UI/媒体 312、
并发/系统 223、统计/日志/遥测 181、网络/下载 177、数据库/存储 52、
SecurityGuard 27、签名/摘要/加密用途 27、实名/生物特征 11、
BehaviX/设备风险 8、外壳/加固 0。具体闭包见
[evidence.md](evidence.md) §4。

## 5. 已知限制

- 没有发送任何业务请求，因此没有实际响应、限流状态、真实错误码或
  服务端账号处罚样本。
- 没有做登录绕过、签名伪造、支付攻击、越权 exploit 或提权测试。
- 远端 Orange 配置可能改变坐标白名单、AB 开关、阈值和接口灰度；
  本报告记录 APK 内默认值与读取机制。
- 服务端权重、保存期限、共享对象、处罚规则在客户端不可见。
- 静态分析不能穷尽运行时反射、动态下发脚本或所有后台初始化器；
  因此隐私结论严格限定为主启动链和可见调用点。
- 设备 DB 是一次只读快照，行数不代表所有版本/设备。

## 6. 完成门

- [x] 样本身份、大小、哈希、版本、SDK 与组件计数。
- [x] 网络协议、搜索/浏览/购票/票夹静态接口。
- [x] 认证、签名、token、cookie 与敏感交易调用面。
- [x] 上传下载字段、媒体范围和动态资源更新。
- [x] 风控检测、评分、阈值、native 对拍、流控与设备证据。
- [x] 58 权限与 79 导出组件的越权/提权判断。
- [x] 同意闸门、位置/标识/媒体/遥测与告知边界。
- [x] 外壳 XOR、RC4、AES/SM/TLS、签名 surface 的密码学闭包。
- [x] 2,068 个 JADX 残留 100% 分类，未分类为 0。
- [x] 六个设备数据库 schema 的只读核对。
- [x] 没有实际请求、测试或可复现攻击步骤。
- [x] 全文只给最终结论，不含修订措辞。
- [x] 没有未分析清楚的加密或混淆代码。
