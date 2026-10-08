# 知乎 11.10.0

研究对象是官方版本 `11.10.0`、versionCode `41012`、包名 `com.zhihu.android` 的
ARM64 单 APK（18 个 DEX、163 个随包 native 库、`targetSdk 34`、`minSdk 21`）。
搜索、推荐、浏览问题与回答、提交回答、阅读盐选会员内容等功能的网络请求全部采用
**静态只读**调查；未构造请求，未登录账号，未读取凭证或个人信息。另对自有设备做
**只读**运行态核对：只经 Android 应用私有数据目录读取数据库 schema、落盘格式与
聚合数量，未写入设备、未发起任何业务网络请求。

样本 SHA-256：

```
93d5ee0d6273d88b85f719fe1c1bd92a3343aeae8f25b17ec5c69649963ad809
```

大小 `183,316,018` 字节；18 个 DEX 与 APK 声明一致。

## 覆盖范围

- 主 API 网关（`api.zhihu.com` / `www.zhihu.com` / `billboard-er.zhihu.com`）的网络流程、
  URL 结构、请求/响应报文字节格式。
- `X-Zse-93` / `X-Zse-96` 签名链、请求体加密拦截器、`x-udid`/CloudID 签名。
- 登录态与 `Authorization`（`Bearer` 令牌 / `oauth` 客户端凭据）判定链。
- 搜索（`search_v3`）、推荐（`topstory/recommend`）、回答浏览/提交、评论、盐选
  （`kvip`）目录与试读的静态接口字段与调用参数。
- 图片/视频上传（`api.zhihu.com/images`、`lens.zhihu.com`）与下载/播放链路。
- 风控：`X-ZST-81`/`X-ZST-82` 指纹、`/zst/events/*` 上报、设备采集字段、动态风控配置、
  验证码与第三方安全组件（Alibaba SecurityGuard、Bangcle、字节 Sword）。
- Bangcle `PreData161` + `libbangcle_crypto_tool.so` 的算法选择、置换表与填充处理。
- 权限声明、导出组件、隐私闸门、越权/提权/超范围采集判断。
- 只读设备端真实数据库 schema（42 个 SQLite、148 张表）与运行态文件核对。
- DRM 能力与可公开验证的公开番剧/剧集示例（见 [report.md](report.md) §7）。

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
事实和服务端不可见部分严格分开；不提供登录绕过、签名伪造、付费墙绕过或批量抓取方法。
