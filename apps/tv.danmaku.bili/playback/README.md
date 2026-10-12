# 番剧播放链实测（间谍过家家 第三季 PV）

本目录记录一次**在自有设备上打开指定番剧短链时**的播放链实测：客户端向哪些主机
发出什么请求、服务端返回什么字节、媒体数据是否加密。所有设备侧操作均为只读
（抓包由设备自身网络栈镜像，未修改 APK、未安装模块、未改写响应、未注入进程），
分析在离线副本上进行。

## 目标与结论

- 目标：`https://b23.tv/ep2243599` → `https://www.bilibili.com/bangumi/play/ep2243599`。
- 对应剧集：`ep_id 2243599`，`aid 115344031158150`，`cid 32945144896`，
  `season_id 112827`，`media_id 27709925`，标题《间谍过家家 第三季》正式PV。
- **结论：本次实测捕获到的媒体数据没有加密，也没有 Widevine/BiliDRM 许可证请求。**
  视频分片以**明文 HTTP（端口 80）**下发，容器是普通 AV1/DASH fMP4。

## 证据摘要

| 观测项 | 实测值 |
| --- | --- |
| 视频分片传输层 | `http://` 明文，端口 80，无 TLS |
| 分片 URL | `/upgcxcode/43/61/<cid>/<cid>-1-100026.m4s?...` |
| CDN 主机 | `upos-sz-mirrorcoso1.bilivideo.com`（阿里）、`upos-sz-mirrorhwo1.bilivideo.com`（华为，302） |
| 容器格式 | fMP4：`ftyp(iso5/iso6/av01/mp41/dash)` + `moov` + `sidx` + `moof` + `mdat` |
| 编码 | `av01`（AV1），1920×1080，`av1C` = `81080c00` |
| 分片大小 | `Content-Length: 258979`，`Content-Range: bytes 0-258978/116571209` |
| 加密盒 | `pssh`/`tenc`/`senc`/`saiz`/`saio`/`schm`/`encv` **全部不存在** |
| 许可证请求 | 无 Widevine/BiliDRM license 或 credential 请求 |
| 客户端标识 | `User-Agent: Bilibili Freedoooooom/MarkII` |

## 边界（必须一并阅读）

1. **实测对象是 PV，不是正片。** `ep2243599` 是《间谍过家家 第三季》的**正式PV**
   （该条目 `rights.is_preview = 1`、`rights.can_watch = 1`）；同季 `payment.tip`
   为“大会员专享观看特权”，正片属大会员内容。本报告不能把“PV 明文”外推为
   “正片明文”。
2. **捕获窗口内的媒体分片来自推荐流预加载。** 抓包期间客户端同时在首页推荐流预取
   了 `cid 42638446143` 的 4K 分片（见 `fallback-cache`），该分片正是被完整捕获的
   明文 m4s；播放页自身的 `playurl` 走 gRPC/HTTP2 且全程 TLS，本次未解密。
   因此“明文分片”证明的是**同一条 CDN 分发路径对非 DRM 内容不做应用层加密**，
   而不是“ep2243599 的正片被明文下发”。
3. 未做任何登录态之外的账号操作、未请求正片片源、未解密 TLS、未注入进程。

## 文件

- [capture.md](capture.md)：抓包与主机清单、连接矩阵。
- [segment.md](segment.md)：分片字节级结构（fMP4 盒树、AV1 OBU、无加密盒证明）。
- [delivery.md](delivery.md)：`playurl`/`upgcxcode` 签名参数与 CDN 重定向链。
- [drm.md](drm.md)：DRM 分支判定与许可证路径的静态对照（何时才会加密）。
- [evidence.md](evidence.md)：命令、哈希与可复现步骤。
- [completeness.md](completeness.md)：要求覆盖与未决边界。
