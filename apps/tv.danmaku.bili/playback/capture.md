# 抓包与主机清单

## 1. 采集方式

在自有测试设备（Android 16，arm64-v8a）上，用设备自身网络栈做只读镜像：
在设备网络命名空间内运行 `tcpdump -i any -s 0 -w <file>`，覆盖 `rmnet`/`tun`/`lo`
全部接口，随后把 pcap 副本离线分析。**未修改 APK、未安装 Zygisk/LSPosed 模块、
未改写响应、未注入进程、未解密 TLS。**

- 采集窗口：`2026-10-12 17:51:45` – `17:54:13`（UTC+08:00），约 148 秒。
- 包数：`67,324`（pcap 仅保留在私有分析工作区，不随报告发布）。
- 期间执行的唯一设备操作：通过 `am start -a android.intent.action.VIEW` 打开
  `https://b23.tv/ep2243599`（等价于用户点击链接）。

## 2. 链接解析

`b23.tv` 短链返回 `302`，`Location: https://www.bilibili.com/bangumi/play/ep2243599`。
应用侧由 `UnitedBizDetailsActivity`（`com.bilibili.ship.theseus.detail`）承接番剧详情。

## 3. 观测到的 Bilibili 主机（TLS SNI / DNS）

| 主机 | 用途 | 传输 |
| --- | --- | --- |
| `api.bilibili.com` | PGC 详情、`pgc/view`、`pgc/player` 系列 | HTTPS/443 |
| `app.bilibili.com` | App 公共接口 | HTTPS/443 |
| `grpc.biliapi.net` | gRPC 播放策略（`bilibili.app.playerunite.v1`） | HTTPS/443 |
| `dataflow.biliapi.com` | 行为上报 | HTTPS/443 |
| `httpdns.bilivideo.com` | 媒体域名的 HTTPDNS 解析（`/resolve`） | **HTTP/80** |
| `upos-sz-mirrorcoso1.bilivideo.com` | 媒体分片（阿里云 CDN） | **HTTP/80** |
| `upos-sz-mirrorhwo1.bilivideo.com` | 媒体分片（华为云 CDN，302 跳转） | **HTTP/80** |
| `upos-sz-static.bilivideo.com` | 静态资源 | HTTPS/443 |
| `*.mcdn.bilivideo.cn` | P2P/CDN 备用地址 | — |
| `broadcast.chat.bilibili.com` | 弹幕/广播长连接 | HTTPS/443 |
| `passport.bilibili.com` | 账号 | HTTPS/443 |
| `s1/i0/i1/i2.hdslb.com` | 图片/静态资源 | HTTPS/443 |

同窗口还观测到与本次目标无关的第三方流量（GitHub、Google、DigiCert OCSP、Miui
统计、推送等），已在分析中排除。

## 4. 连接矩阵（与媒体相关的部分）

| 方向 | 端口 | 协议 | 字节 |
| --- | --- | --- | --- |
| 设备 → `upos-sz-mirrorcoso1` | 80 | 明文 HTTP/1.1 | 请求 ~0.8 KB |
| `upos-sz-mirrorcoso1` → 设备 | 80 | 明文 HTTP/1.1 | **259,603 B**（`206`，媒体分片） |
| 设备 → `upos-sz-mirrorhwo1` | 80 | 明文 HTTP/1.1 | 请求 ~0.8 KB |
| `upos-sz-mirrorhwo1` → 设备 | 80 | 明文 HTTP/1.1 | 989 B（`302`，跳转） |
| 设备 → `httpdns.bilivideo.com` | 80 | 明文 HTTP/1.1 | 3 次 `/resolve` |

媒体链路上**没有任何 TLS 记录**：这些流的首个载荷字节是 `GET ` / `HTTP/1.1` 的
ASCII 文本（`0x47 0x45 0x54 0x20`），不是 `0x16 0x03`（TLS handshake）。
