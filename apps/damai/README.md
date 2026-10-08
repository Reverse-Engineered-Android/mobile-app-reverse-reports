# 大麦 9.0.35

研究对象是官方版本 `9.0.35`、versionCode `109003500`、包名 `cn.damai` 的
ARM64 APK、其阿里加固外壳（`com.ali.mobisecenhance` / alijtca `3.35.2`）、
还原后的 25 个 payload DEX、随包分发的 native 库，以及自有 Android 设备上
的**只读**运行态数据。搜索、活动浏览、购票下单与票夹全部采用**静态只读**
调查：未构造请求，未登录账号，未真实购票，未查询票夹，未读取或修改设备上
的任何数据。

样本 SHA-256：
`dd33ae183903fb37b9761266d892f34767b3dd4b493fcf5efec0ae169969c87f`，
大小 `113,422,261` 字节，`unzip -t` 通过；条目时间戳 2026-09-24 15:08。

## 样本结构

APK 根目录的 `classes.dex` / `classes2.dex` **不含任何应用类**（只有
androidx、compose、WindVane 等，22,617 个描述符，`Lcn/damai/...` 为 0 个）。
真实业务代码藏在 `assets/data.png`（34,310,723 字节）中：前
`size-1024` 字节是明文的 ZIP local-file-header + deflate 条目（25 个 DEX），
末 1024 字节是逐字节 XOR 的中央目录尾部与 EOCD。该变换由外壳
`com.ali.mobisecenhance.ld.dexmode.ShellDexMode.decodeFile` 实现，已在
[evidence.md](evidence.md) §2 给出可重放公式与哈希验证。

## 覆盖范围

- MTOP 主链路：网关域名、入口、参数装配、请求头映射与签名输入面。
- 搜索、活动/项目详情、下单三段式（build/adjust/create）与票夹接口族。
- 认证：Havana/mlogin、UCC/OAuth、passkey/生物识别、会话持久化。
- 上传下载：头像/评论图片、实名核验材料、日志与 OSS 上传、升级包下载。
- 风控：SecurityGuard 组件面、BehaviX 行为评分与设备画像、反爬惩罚队列。
- 加固外壳：所有加解密与混淆逻辑的类型、输入输出契约与调用点闭合性。
- 权限、导出组件、隐私同意闸门与超范围采集判断。
- 2068 个 JADX 残留方法的逐项归类（无未解释的加密实现）。
- 只读设备端真实数据库 schema 与落盘文件核对。

## 入口

- [综合结论](report.md)
- [网络与协议](network.md)
- [认证机制](auth.md)
- [上传下载范围](transfer.md)
- [风控机制](risk.md)
- [权限与导出面](permissions.md)
- [隐私与告知](privacy.md)
- [逆向证据](evidence.md)
- [完成度矩阵](completeness.md)

## 研究边界

客户端静态分析能证明“代码能采集什么、在何条件触发、写入哪个字段、调用哪个
端点”，不能证明服务端实际保存多久、如何评分或采取何种处罚。报告把客户端
事实、结构已证实事实与服务端不可见部分严格分开；不提供登录绕过、签名伪造、
抢票加速或批量抓取方法。
