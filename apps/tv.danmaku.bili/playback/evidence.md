# 证据与可复现步骤

## 1. 样本

| 项 | 值 |
| --- | --- |
| 包名 | `tv.danmaku.bili` |
| 版本 | `9.14.0`（versionCode `9140400`） |
| APK 大小 | `220,271,429` 字节 |
| APK SHA-256 | `63c14370673d74233df211e2efe553c4ce1219c08e6a83fdbd441845ef72cc91` |
| DEX / arm64 库 | 33 个 DEX / 93 个 `.so` |

APK 与设备端 `base.apk` 哈希一致（设备侧 `sha256sum` 与本地副本比对通过）。

## 2. 目标

| 项 | 值 |
| --- | --- |
| 短链 | `https://b23.tv/ep2243599` → `https://www.bilibili.com/bangumi/play/ep2243599` |
| `ep_id` | `2243599` |
| `aid` | `115344031158150` |
| `cid` | `32945144896` |
| `season_id` / `media_id` | `112827` / `27709925` |
| 标题 | 《间谍过家家 第三季》正式PV |

## 3. 采集与分析方法

```text
# 设备侧（只读镜像，设备自身网络栈）
tcpdump -i any -s 0 -nn -U -w <pcap>          # 覆盖全部接口
# 离线分析（本地副本，不触碰设备）
dpkt 解析 pcap（LINKTYPE_LINUX_SLL2 = 276）
手工解析 TLS ClientHello 取 SNI
按 TCP 序号重组 HTTP/1.1 流，提取 206 响应体
按 ISO-BMFF 盒规则遍历 ftyp/moov/sidx/moof/mdat
按 AV1 OBU 规则遍历 mdat 载荷
```

## 4. 关键观测（可复核）

1. 明文 HTTP 请求行（端口 80）：

```text
GET /upgcxcode/43/61/42638446143/42638446143-1-100026.m4s?e=<redacted>&deadline=<redacted>...
host: upos-sz-mirrorcoso1.bilivideo.com
Range: bytes=0-258978
User-Agent: Bilibili Freedoooooom/MarkII
```

2. `206` 响应体完整重组长度 `258,979` 字节；首字节 `00 00 00 24 66 74 79 70`
   （`ftyp`），非 TLS（`16 03`）也非密文随机块。
3. `stsd` 样本条目类型 `av01`，宽高 1920×1080，`av1C = 81 08 0c 00`。
4. `pssh`/`tenc`/`senc`/`saiz`/`saio`/`schm`/`encv` 检索结果均为**不存在**。
5. 全窗口检索：无 Widevine/BiliDRM license/credential 请求；无 `License`/`PSSH`
   相关明文。

## 5. 复现命令（脱敏，秘密从环境变量传入）

```bash
# 只读检查分片盒结构（不含原始字节导出）
python3 - <<'PY'
import struct
b=open(os.environ["SEGMENT"],"rb").read()
p=0
while p+8<=len(b):
    size=struct.unpack(">I",b[p:p+4])[0]; typ=b[p+4:p+8]
    print(typ.decode("latin1","ignore"), size); p+=size
PY

# 检索加密盒
python3 - <<'PY'
import os
b=open(os.environ["SEGMENT"],"rb").read()
for t in (b"pssh",b"tenc",b"senc",b"saiz",b"saio",b"schm",b"encv",b"sinf",b"widevine"):
    print(t.decode(), "FOUND" if b.find(t)>=0 else "absent")
PY
```

## 6. 公开范围

公开：版本、哈希、盒结构、字段编号、类名/方法名、参数名与语义、聚合数量。
不公开：pcap 原始字节、媒体分片本体、签名令牌值、`buvid`/`mid`/`oi`/`trid` 等
设备与账号标识、内网地址、设备序列号。
