# 国金佣金宝 9.05.004

研究对象是官方版本 `9.05.004`、versionCode `20260903`、包名 `cn.com.gjzq.yjb2`
的 ARM64 APK（`minSdk 23` / `targetSdk 32`、`7,627` 个 ZIP 条目、`46` 个
`arm64-v8a` native 库、`347` 个 assets 条目、`381` 个 `<activity>`）。行情、
交易、开户、理财、AI 投顾等业务全部采用**静态只读**调查：未登录账号、未构造或
发送任何请求、未下单、未支付、未上传或下载业务文件、未绕过风控。另对自有
Android 设备做**只读**运行态核对：只读取应用私有目录中的数据库 schema、已落盘
文件头与聚合数量，未写入设备、未发起业务网络请求。

样本 SHA-256：

```
e6cc89f97669d86601843a8c741459c2c3aa74386cb7e2206a7da60e7d10c82d
```

大小 `382,970,270` 字节。

## 覆盖范围

- 应用外壳：`s.h.e.l.l.S` Application + `AppComponentFactory` + `libexec.so`
  装载链，`assets/yjb.zip`（178 MiB）作为二级载荷。
- H5 业务层：`yjb.zip` 内 `2,843` 个条目、`37` 个业务模块，`.r` 后缀资源
  （`131,312,489` 字节）的**位置相关 XOR keystream**已恢复到 `6,165,153`
  字节，覆盖最大文件；928 个文本资源中 921 个完全 UTF-8 合法，7 个文件有
  少量歧义；`node --check` 可解析的 JS 从 `82/507` 提升到 `369/507`。
- 网络面：`yongjinbao.com.cn` / `yjbtest.com` 双环境域名族、**两层 RPC**——
  native `YJBExec(1001)`（`functionId` / `subSystemNo` / `tradeType` / `param`）
  与浏览器 `/yjbwebservice/core/api[/json]`（`UniqueKey` 短码，28 条映射表），
  `subSystemNo` 四域（`103` / `112` / `519` / `200`）、70 个交易参数模板、
  23 条业务码中文名，以及独立 WebSocket 行情通道（`wss://quotezy…:8443`，
  登录帧 / 120 s 心跳 / GTS-JSON 帧压缩）。
- 认证与加密：`APP_SECRET`（`g6j0z0q1w0e9bapp`）与账号标识密钥
  （`z$B&E)H@McQfTjWn`）驱动的 AES-128-ECB + Pkcs7 + Base64URL 变体；
  `fetchYJBAPI` 的登录状态机、`code === -1` + 占位符 → 登录 → 重放中间件、
  SAS/SSO durable/instant token 链、`libndktest.so` 的 RC4/SM4 双栈导出。
- native 桥：`callNative` / `respFromNative` ABI，62 个被调用的功能号
  （38 个有文档签名），`readSystemToMemory(2000)` 与 `nonSystemParam(4004)`
  的键集合。
- 风控与设备指纹：六类客户端判定点（适当性状态机、等级比较、购前闸门、
  `permDesk` 权限位、`access.authority` 深链准入、登录/交易超时）、
  `saveLog(4014)` 设备遥测信封、`libShanYCore.so`（ptrace 导入）、
  `libencrypt.so`（tztEncrypt 四入口）、`imjar`/`ijiami` 加固、
  `libmsaoaidsec.so` 等 OAID 采集链；`<queries>` 中的反分析包名清单。
- 权限、导出组件、明文 HTTP、WebView 文件提供器与 `SYSTEM_ALERT_WINDOW` 面。
- 只读设备端真库 schema：`7` 个 SQLite、`23` 张业务表、`70` 个 `shared_prefs`。

## 入口

- [综合结论](report.md)
- [网络与协议](network.md)
- [认证机制](auth.md)
- [上传下载范围](transfer.md)
- [风控机制](risk.md)
- [混淆与载荷还原](obfuscation.md)
- [权限](permissions.md)
- [隐私](privacy.md)
- [本地存储](storage.md)
- [逆向证据](evidence.md)
- [完成度矩阵](completeness.md)

## 研究边界

客户端静态分析可以证明“代码能采集什么、在何条件触发、写入哪个字段、调用哪个
端点”，不能证明服务端实际保存多久、如何评分或采取何种处罚。报告把客户端事实、
设备端可核对事实和服务端不可见部分严格分开；不提供登录绕过、签名伪造、交易
接口重放或批量抓取方法。
