# 闲鱼 7.28.40

研究对象是官方版本 `7.28.40`、versionCode `521`、包名 `com.taobao.idlefish` 的
ARM64 APK、9 个 DEX、125 个随包 native 库、资源与代码路径中的服务端接口定义。
浏览商品与订单、用户评价与历史、搜索、私聊等功能的网络请求全部采用
**静态只读**调查；未构造请求，未登录账号，未读取凭证或个人信息。另对自有设备做
**只读**运行态核对：只经 Android 应用私有数据目录读取数据库 schema、落盘格式与
聚合数量，未写入设备、未发起任何网络请求。

样本 SHA-256：
`57ae1b6963dadae25998b821c05e8fbc7e11cbb760ef23d8287f3934f185650a`，
大小 `113,545,135` 字节；9 个 DEX 内容与 APK 声明一致。

## 覆盖范围

- MTOP 主 API 网关（`acs.m.goofish.com`）的网络流程、URL 结构、请求/响应报文字节格式。
- `x-sign`、`x-mini-wua`、`wua`、`x-sgext`、`x-umt` 的生成链路与降级分支。
- 登录 Cookie（`cookie2`/`sgcookie`/`unb`）与登录态校验，并静态判定 h5api 令牌
  `_m_h5_tk`/`_m_h5_tk_enc` 不在本 APK 的请求链中。
- 商品详情、订单、评价、搜索、私聊的静态接口字段与调用参数。
- 文件上传（MTOP `uploadv2.do`）、对象存储、CDN 下载与消息媒体范围。
- SecurityGuard（`libsgmainso`/`libsgmiscso`）、UMID/UTDID、中间层统一签名、AVMP。
- 内容与行为风控：CCRC 服务、Wukong 原生/行为引擎、4 个闲鱼风控场景、离线跳转拦截。
- 权益/风控拦截：`FAIL_BIZ_FORBIDDEN`、`NEED_REAL_VERIFY`/`RISK_USER_VERIFY`、限流锁。
- 权限声明、导出组件、隐私闸门、越权/提权/超范围采集判断。
- 只读设备端真实数据库 schema、落盘格式与运行态文件核对。
- 所有出现的密码学用途、算法模式、密钥索引与封装格式；无未解释的加密实现。

## 入口

- [综合结论](report.md)
- [网络与协议](network.md)
- [认证机制](auth.md)
- [上传下载范围](transfer.md)
- [风控机制](risk.md)
- [权限与导出面](permissions.md)
- [隐私与告知](privacy.md)
- [本地存储](storage.md)
- [逆向证据](evidence.md)
- [完成度矩阵](completeness.md)

## 研究边界

客户端静态分析可以证明“代码能采集什么、在何条件触发、写入哪个字段、调用哪个端点”，
不能证明服务端实际保存多久、如何评分或采取何种处罚。报告把客户端事实、设备端可核对
事实和服务端不可见部分严格分开；不提供登录绕过、签名伪造、支付攻击或批量抓取方法。
