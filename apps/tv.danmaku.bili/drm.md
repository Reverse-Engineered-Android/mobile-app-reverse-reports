# 番剧 DRM

## 1. 静态播放链

播放响应由 `bilibili.app.viewunite.v1`、`bilibili.app.playerunite.v1`、
`bilibili.cheese.gateway.player.v1.PlayViewReply` 或 `/x/playurl/ott` 提供。
字段中可见：

```text
dash / durl / backup_url
dashDrmType
DRM_DEFAULT, DRM_WIDEVINE, DRM_BILIDRM, DRM_FAIRPLAY
WIDEVINE_UUID, widevinePssh
BilidrmCredentialHelper, BilidrmCredentialCallback
BILIDRM_URI_FIELD_NUMBER, WIDEVINE_PSSH_FIELD_NUMBER
```

播放器按 `dashDrmType` 选择：

- **Widevine**：传递 PSSH，交给 Android MediaDrm/CDM；
- **Bilibili DRM**：使用 `BilidrmCredentialHelper` 的 credential JSON；
- **明文/默认**：普通 DASH/MP4 segment，不需要 CDM 解密。

`BILIDRM_OFFLINE_CACHE` 的静态错误字符串明确要求 credential JSON 同时具有
`aes`、`ckc` 字段，并校验 16 字节 AES key、非空 CKC、过期时间；这属于许可证/缓存
解析，不是可绕过 DRM 的明文密钥方案。

## 2. 离线边界

`offlineVideo.db` 只保存 aid/cid/season/episode、清晰度路径、存储位置、分段清单和
`auth_code`；DRM 内容的许可证或 credential 不会因为存在下载表而自动获得。
APK 中没有发现伪造 Widevine CDM、提取真实许可证或绕过 `checkDrmExpired` 的实现。

## 3. 高播放示例

以下示例来自静态公开资料和 APK 的能力矩阵，未请求片源、许可证或播放接口。
“DRM 候选”表示该类高播放作品在当前客户端可能按服务端策略走 Widevine/Bilibili DRM；
“无 DRM 候选”表示静态资料/缓存形态支持常规 DASH/MP4，但不把候选提升为实测事实。

### 3.1 DRM 候选

| 作品 | 静态依据 | 播放量线索 |
| --- | --- | --- |
| 凡人修仙传 | OGV/国创高播放作品，客户端支持 `dashDrmType` 的 VIP/OGV 分支 | 公开榜单约 26.14 亿 |
| 仙逆 | 同上 | 公开榜单约 24.16 亿 |
| 斗破苍穹 年番 | 同上 | 公开榜单约 20.18 亿 |
| 完美世界 | 同上 | 公开榜单约 16.93 亿 |

### 3.2 无 DRM 候选

| 作品 | 静态依据 | 播放量线索 |
| --- | --- | --- |
| 灵笼 第一季 | 公开页面与静态缓存显示常规系列播放形态 | 约 7.9 亿总播放 |
| 中国奇谭 | 常规国创播放页、无本地 DRM credential 线索 | 约 5,216 万（早期公开值） |
| 名侦探柯南 | 番剧索引中的长线高播放作品，客户端存在普通 DASH 路径 | 静态页面高播放 |
| 我是不白吃 | 公开番剧页显示 57.9 亿播放，静态无专属 DRM 线索 | 约 57.9 亿 |

这些数字是公开资料的时间敏感线索，不是本次 APK 实测；标题级 DRM 归属仍由当前
播放响应和许可证决定。本报告没有进行实际测试。
