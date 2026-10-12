# 播放地址下发与 CDN 直取

## 1. 播放地址获取（本次未解密）

播放策略走 gRPC：`bilibili.app.playerunite.v1.Player/PlayViewUnite`
（静态字符串，见 `dex-strings.txt`），番剧侧另有 `bilibili.pgc.gateway.player.v2.PlayURL`
与 `bilibili.app.playurl.v1` 的 `PlayURLReq/PlayURLReply`。这些请求全程 TLS，
**本次未解密**，因此“服务端在 `playurl` 响应里给了什么”不是本报告的实测结论，
只作为静态对照（见 [drm.md](drm.md)）。

## 2. CDN 直取（实测，明文）

客户端随后直接向 CDN 取分片，链路为明文 HTTP。请求行形态：

```text
GET /upgcxcode/43/61/<cid>/<cid>-1-<quality_id>.m4s?<signed params> HTTP/1.1
host: upos-sz-mirrorcoso1.bilivideo.com
Range: bytes=0-258978
User-Agent: Bilibili Freedoooooom/MarkII
accept-encoding: gzip, deflate, br
connection: keep-alive
```

签名/防滥用参数（**值一律脱敏**，仅保留参数名与语义）：

| 参数 | 语义 |
| --- | --- |
| `e` | 路径级签名令牌（127 字符，非 hex 编码的 Base64 变体） |
| `deadline` | 令牌过期时间戳（Unix 秒，实测签发后约 2 小时） |
| `upsig` | 32 位十六进制请求签名 |
| `uparams` | 参与 `upsig` 计算的参数名列表 |
| `uipk` | 客户端 IP 相关策略等级 |
| `trid` | 会话级跟踪 ID |
| `mid` | 账号 ID（已脱敏） |
| `oi` | 上传/归属标识 |
| `os` | 存储源（实测 `coso1bv` / `hwo1bv`） |
| `og` | 存储厂商（实测 `cos` / `hw`） |
| `gen` | 地址生成器版本（实测 `playurlv3`） |
| `platform` | 平台（实测 `android`） |
| `nbs` | 新地址方案开关（实测 `1`） |
| `bvc` | 业务码（`vod`） |
| `bw` | 码率 |
| `buvid` | 设备指纹（已脱敏） |
| `build` | 客户端构建号（实测 `9140400`，对应 9.14.0） |
| `f` / `dl` / `lrs` / `agrr` | 播放器与限速相关开关 |
| `qn_dyeid` | 清晰度实验标识（已脱敏） |

`X-Upsig-Version: 20220801` 说明该签名方案版本。**这些参数是访问控制令牌，
不是内容加密**：令牌过期即需重新取地址，但拿到地址后的响应体本身不含加密。

## 3. CDN 重定向

华为云源 `upos-sz-mirrorhwo1.bilivideo.com` 对同一对象返回 `302`，把客户端导向
第三方 CDN 域名（实测指向一个 `*.yirujs.com` 主机，其 DNS 链为
`yirujs.com` → `cdn.chinamobile.com` → `d.cdn.10086.cn` → 移动 CDN）。
重定向目标同样是 **`http://` 明文**。

结论：**跨 CDN 的调度重定向不改变传输层，媒体仍是明文 HTTP**。

## 4. 备用地址形态

客户端缓存中同时存在 `*.mcdn.bilivideo.cn:8000` 形式的 P2P/CDN 备用地址
（`/v1/resource/upgcxcode/...`）。本次捕获窗口内这些主机只有连接建立，
未取到可判定加密的媒体字节，因此不对其内容加密性下结论。
