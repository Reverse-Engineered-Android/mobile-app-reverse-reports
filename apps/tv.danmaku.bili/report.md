# 哔哩哔哩 9.13.0 综合结论

## 1. 样本与方法

本次对象为 `tv.danmaku.bili` `9.13.0` (`9130500`)，SHA-256 为
`d8e74cf3ce4e4332af75920035590979e4347938c65a6d2fc73627b0c132d98e`。
覆盖 32 个 DEX、156,568 个反编译 Java 文件、93 个 arm64 动态库、
3,085 条本地决策规则、74 个权限、87 个导出组件和 27 个设备端 SQLite 文件。
所有网络与 DRM 结论均为静态只读调查；设备端只读核对未写入数据、未发起网络请求。

## 2. 主要网络交互流程

业务请求由 Retrofit/OkHttp 拦截器统一补参和签名，播放详情、弹幕、评论和互动
消息同时使用 gRPC/protobuf；媒体、图片和资源下载使用服务端返回的 CDN URL。

```
Retrofit / gRPC / Ktor
  └─ DefaultRequestInterceptor / AuthInterceptor
       ├─ 公共参数：platform、mobi_app、appkey、build、channel、access_key、locale
       ├─ 请求头：Display-ID、Buvid、Device-ID、fp_local、fp_remote、session_id、GuestId
       └─ LibBili.signQuery：规范串 → MD5 → 32 位小写十六进制
  └─ HTTP 网关 / media CDN / bilibili gRPC Moss
```

公共 API 主要位于 `api.bilibili.com`、`app.bilibili.com`、`live.bilibili.com`、
`member.bilibili.com`、`show.bilibili.com` 和 `api.vc.bilibili.com`。HTTP JSON 的
响应信封为 `{"code":<整数>,"message":<字符串>,"data":<对象或数组>}`；错误码非零时
`message` 给出文本原因。gRPC 消息按 `bilibili.*` protobuf 服务的字段编号编码。
精确接口见 [network.md](network.md) 与 [protocol.md](protocol.md)。

## 3. 认证与签名

- `access_key` 是登录态访问令牌，由公共参数与 `Buvid` 头共同表达设备/会话身份。
- `appkey` 来自 native 的 19 项 `mobi_app → appkey` 表，未知值回退到 Android 默认项。
- 默认签名输入是 `SignedQuery.r(map)`：TreeMap 字典序排序、RFC3986 风格百分号编码、
  `k=v&k=v` 拼接，再由 native MD5 计算并以 `&sign=` 追加。
- `AuthInterceptor.signQuery` 有三条分支：配置 appSecret、AB 开关自定义 key/secret、
  native 默认路径；最终都回到同一 native 入口，未发现自定义非标准密码学。
- 第三方流量入口内存在硬编码 App ID/Secret 对，用于特定运营商 API；这是静态存在的
  客户端凭据暴露，不等同于能够越权访问第三方账号。

完整链路见 [auth.md](auth.md)。

## 4. 上传下载数据范围

投稿视频从 `member.bilibili.com/preupload` 获得 UPOS 分片信息，`video_upload` 表保存
任务 ID、文件路径/长度、分片列表、已传字节、端点、上传 URL、chunk 大小、重试参数、
认证串、`upos_uri`、bucket/profile 和诊断字段。接口上传头包含 `X-Upos-Auth`。

离线下载由 `offlineVideo.db` 与 `periodic_downloader.db` 管理，字段覆盖 aid、cid、
season/episode、清晰度路径、已下载字节、存储路径、auth code、已下载分段和任务状态。
播放历史保存位置、时长和内层 aid/cid；缓存表保存资源 key、过期时间和本地路径。
范围和 DDL 见 [transfer.md](transfer.md) 与 [database-inventory.md](database-inventory.md)。

## 5. 权限、越权与提权结论

- **未发现 Android 提权实现**：代码读取 root/adb/解锁状态、Hook、模拟器和代理特征，
  但没有写入 system 分区、提权到 UID 0、注入系统进程或绕过 SELinux 的代码。
- **未发现客户端越权**：登录后仍以 `access_key`、buvid 和服务端返回的授权信息调用接口；
  样本中没有伪造服务端身份、扩展令牌权限或绕过 DRM/验证码的已证实路径。
- **存在高权限声明**：74 个权限包含 `QUERY_ALL_PACKAGES`、`READ_LOGS`、日历读写、
  精确位置、悬浮窗、安装包、全部外部存储和广告 ID/受众能力；87 个导出组件扩大了
  可被其他应用触达的入口面。是否实际滥用取决于调用条件与运行时授权。
- **采集范围偏宽**：设备、传感器、已安装应用、Hook、root、VPN 与代理等 121 个风控字段
  会进入本地风险位图并按条件上报；该能力与“每项都持续上传”不同。

最终判断见 [privacy.md](privacy.md)。

## 6. 风控与密码学完整性

Gripper 报文外层为：

```json
{"header":{"encode_type":2,"payload_type":2,"encoded_aes_key":"…","ts":1700000000000,"encoded_version":"…"},"encrypt_payload":"…"}
```

36 个风险判定器把结果编码为分组位图，再把字段名转换为 4 位十六进制码。
GAIA 使用每条消息随机 16 字符 AES-128 密钥加密载荷，再用内置 2048 位 RSA 公钥
加密 AES key；风险 token 路径使用 `RSA/ECB/PKCS1Padding`；native 签名使用 MD5；
`libbili.so` 的 45 个 `.datadiv_decode` 初始化函数已全部还原，恢复 513 个明文字符串。
所有算法、密钥来源、调用地址、报文字段和判定常量均见 [risk-control.md](risk-control.md)，
不存在未分析清楚的加密或混淆实现。

## 7. 功能静态结论

| 功能 | 静态交互与原理 |
| --- | --- |
| 搜索 | `/x/v2/search/{live,episodes,episodes_new,recommend,square}`、专题搜索；建议词写入 `suggestions.db` |
| 直播 | `live.bilibili.com/app/{all-live,area,myfollow,mytag}`、弹幕设置 get/set、gRPC 房间消息 |
| 会员购 | `api/ticket` 的地址、购票人、订单、验票、收藏和图片上传；下单受服务端 session/订单授权控制 |
| 播放 | `viewunite`/`playerunite` gRPC、cheese `PlayViewReply`、`/x/playurl/ott`，返回 DASH/MP4、备份 URL 和授权限制 |
| 缓存 | MP4/M4S 分段下载、manifest/资源表、过期与校验；DRM 内容只缓存许可证允许的密文或受控 credential |
| 互动 | 弹幕、回复、点赞、投币、收藏、直播互动走 gRPC 与 `/x/v2/*`，登录态决定可执行动作 |
| 番剧 DRM | 响应字段选择 Widevine、Bilibili DRM 或明文；WIDEVINE PSSH、BILIDRM URI/CKC/离线 AES credential 进入播放器 |

## 8. 高播放番剧 DRM 示例

静态资料中可确认客户端支持 `DRM_WIDEVINE`、`DRM_BILIDRM` 与明文 DASH 三条路径；
标题级 DRM 归属由服务端 `dashDrmType`/CDM 授权响应决定，客户端代码和 APK 内没有
全站番剧清单。以下为高播放量且按静态资料归入相应能力组的示例，**不构成对当前片源
加密状态的实测断言**：

- **DRM 候选（高播放）**：《凡人修仙传》、《仙逆》、《斗破苍穹 年番》、《完美世界》。
  这些作品在本地/公开静态榜单中为高播放国创，APK 对 VIP/OGV 高码率片源走
  Widevine 或 Bilibili DRM 的能力完整；具体标题/分集仍以播放响应为准。
- **无 DRM 候选（高播放）**：《灵笼 第一季》、《中国奇谭》、《名侦探柯南》、
  《我是不白吃》。静态缓存与公开页面表明这些系列存在常规 DASH/MP4 播放形态；
  本样本未取得对应播放响应，因此标记为“无 DRM 候选”而非已实测明文。

本报告没有请求片源、许可证或播放接口；分级与示例依据见 [drm.md](drm.md)。

## 9. 最终判断

APK 具备广泛设备指纹、安装应用、传感器、环境和 Hook/root 检测能力，也具备把这些
字段压缩上报的完整链路；这是客户端能力。未发现 Android 提权、服务端身份伪造、
DRM 绕过或已证实的越权利用。未经告知方面，风险数据采集与远端决策组件存在，但静态
样本无法证明每种字段都在用户未见告知的情况下实际上传，故不能把“可采集”直接写成
“已持续超范围上传”。最明确的超范围风险来自过宽权限声明、导出组件、硬编码第三方
凭据和设备/安装列表类高敏风控字段；最终范围见 [privacy.md](privacy.md)。
