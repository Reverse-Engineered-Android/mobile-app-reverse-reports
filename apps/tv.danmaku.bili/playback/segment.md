# 媒体分片字节级结构

## 1. 被完整捕获的分片

服务端返回 `206 Partial Content`，`Content-Length: 258979`，
`Content-Range: bytes 0-258978/116571209`（完整对象约 111 MB，本次取到首段）。
该响应体被完整重组（TCP 流按序号拼接，无缺口），落盘为分析用副本。

响应头要点：

```text
HTTP/1.1 206 Partial Content
Content-Type: application/octet-stream
X-Upsig-Version: 20220801
Content-Range: bytes 0-258978/116571209
Cache-Control: max-age=31536000
server: lego_v1
```

## 2. 容器：普通 DASH fMP4（非加密）

盒树（偏移为分片内绝对偏移）：

```text
ftyp   size=36     brand=iso5, compatible=iso5 iso6 av01 mp41 dash
moov   size=942
  mvhd / trak / tkhd(track_id=1) / edts / elst
  mdia / mdhd(timescale=16000) / hdlr / minf
    stbl / stsd
      av01  (1920x1080, av1C=81080c00, colr=nclx, btrt)
    stts / stsc / stsz / stco
  mvex / mehd / trex
  udta / meta / ilst
    ©too = "Lavf61.7.106"
    desc = "Packed by Bilibili XCoder v2.0.2"
sidx   size=4912   (Segment Index, timescale 0x3e80=16000)
moof   size=604    + mdat size=116831
moof   size=604    + mdat size=178699  (被捕获窗口截断)
```

## 3. 无加密盒（关键证明）

在整个初始化段与媒体段中检索 CENC/DRM 结构，**全部不存在**：

| 盒/字段 | 含义 | 结果 |
| --- | --- | --- |
| `pssh` | Protection System Specific Header（DRM 系统 ID） | 不存在 |
| `tenc` | Track Encryption（默认 KID / IV 大小 / 模式） | 不存在 |
| `senc` | Sample Encryption（每样本 IV） | 不存在 |
| `saiz` / `saio` | 辅助信息大小/偏移（CENC 必需） | 不存在 |
| `schm` / `schmi` | Scheme Type（`cenc`/`cbcs`） | 不存在 |
| `encv` / `enca` | 加密视频/音频样本条目 | 不存在 |
| `sinf` / `frma` | 原始格式盒 | 不存在 |
| `widevine` / `clearkey` | DRM 标识 | 不存在 |

样本条目是 `av01`（明文 AV1），不是 `encv`；`stsd` 中没有 `sinf`。因此这是
**标准 cleartext DASH**，播放器不需要 CDM 参与解密。

## 4. AV1 码流可直接解析（明文旁证）

首个 `mdat` 载荷能被按 AV1 OBU 规则完整遍历，得到合法的 OBU 序列
（`OBU_METADATA`、`OBU_SEQUENCE_HEADER`、连续 `OBU_FRAME`），说明载荷不是
密文随机块。若为 CENC 加密，`mdat` 内的样本字节是 `AES-CTR`/`AES-CBC` 密文，
OBU 头解析会立即失步。

## 5. 音频轨

本次捕获窗口内未取到音频分片（同目录下 `-1-30232.m4s` 请求被 `302` 跳转到
移动 CDN，后续字节未落在窗口内）。因此“音频是否加密”未实测；
按同一 `dashDrmType` 分支，音频与视频共用同一 DRM 决策。
