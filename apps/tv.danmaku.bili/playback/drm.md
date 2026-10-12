# 何时才会加密：DRM 分支的静态对照

实测已证明本次目标（PV）走明文 DASH。为回答“什么情况下视频数据会加密”，
对 9.14.0 APK 做静态对照。

## 1. DRM 类型枚举

`tv.danmaku.ijk.media.player.utils.IjkDrmUtils.DrmType`：

```java
DRM_DEFAULT(0),   // 明文
DRM_FAIRPLAY(1),
DRM_WIDEVINE(2),
DRM_BILIDRM(3);
```

`com.bilibili.lib.media.resource.PlayIndex.DrmType` 是同一枚举的映射层。

## 2. 分片字段

`com.bapis.bilibili.app.playurl.v1.DashVideo` 的字段编号（protobuf）：

```text
BASE_URL_FIELD_NUMBER = 1        BACKUP_URL_FIELD_NUMBER = 2
BANDWIDTH_FIELD_NUMBER = 3       CODECID_FIELD_NUMBER = 4
MD5_FIELD_NUMBER = 5             SIZE_FIELD_NUMBER = 6
AUDIO_ID_FIELD_NUMBER = 7        NO_REXCODE_FIELD_NUMBER = 8
FRAME_RATE_FIELD_NUMBER = 9      WIDTH_FIELD_NUMBER = 10
HEIGHT_FIELD_NUMBER = 11         WIDEVINE_PSSH_FIELD_NUMBER = 12
BILIDRM_URI_FIELD_NUMBER = 13
```

`KDashItem` / `KDashVideo` 另有 `dashDrmType`、`biliDrmUri`、`drmExpireTime`、
`drmCredential` 等字段（离线模型 `video.biz.offline.base.model.model.VideoPlayInfo`
中可见同名序列化键）。

判定规则：只要任一 `MediaAssetStream.drmType != DRM_DEFAULT`，
`IjkMediaAsset.getDrmType()` 就返回该非默认值（`IjkMediaAsset.java`）。
即**明文与加密由服务端下发的 `dashDrmType` 决定，客户端不自选**。

## 3. 加密时的两条路径

- **Widevine**（`DRM_WIDEVINE`）：`DashVideo.widevinePssh` 携带 PSSH，
  交给 Android `MediaDrm`/CDM；`IjkDrmUtils.WIDEVINE_UUID =
  new UUID(-1301668207276963122L, -6645017420763422227L)`（即标准 Widevine UUID）。
- **BiliDRM**（`DRM_BILIDRM`）：走 `BilidrmCredentialHelper`：

```java
public class BilidrmCredentialHelper {
    static { System.loadLibrary("ijkplayer"); IjkJ4ALoader.ensureInitialized(); }
    public static native String fetchCredentialForKid(String str);
}
```

`IjkPlayerManager.fetchBilidrmCredentialForKidAsync` 剥掉 `uri:bili://` 前缀后调用
该 native 方法取 credential，回调 `BilidrmCredentialCallback`。
`OfflineResolver` 会记录 `dashDrmType` / `drmKid` / `drmCredential` 日志。

**本次实测未出现任何上述调用**：抓包中没有任何 license/credential 请求，
分片 `stsd` 是 `av01` 而非 `encv`。

## 4. 判定表

| 条件 | 传输加密 | 内容加密 | 实测证据 |
| --- | --- | --- | --- |
| 非 DRM 内容（本次 PV） | 否（明文 HTTP/80） | 否（cleartext AV1） | 本次实测 |
| Widevine 内容 | 视 CDN 策略 | 是（CENC + PSSH + CDM） | 静态代码路径 |
| BiliDRM 内容 | 视 CDN 策略 | 是（native credential） | 静态代码路径 |
| 大会员正片 | 视 CDN 策略 | 由服务端 `dashDrmType` 决定 | **未实测** |

要点：**TLS 与 DRM 是两件事。** 媒体分片用明文 HTTP 传输不等于内容不加密
（CENC 密文也可以明文 HTTP 传）；反之本次分片既无 TLS 也无 CENC，属于双重明文。
