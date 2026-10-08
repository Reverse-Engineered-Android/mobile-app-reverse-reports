# 知乎 11.10.0 综合结论

知乎 11.10.0（versionCode 41012）是中文问答社区应用。本次逆向覆盖 **18 个 DEX、
163 个随包 native 库、48 条权限、61 个导出组件、42 个设备端 SQLite（148 张表）**，
并与自有设备真实数据做了**只读**核对。结论按证据等级给出；全部要求项均已完成，
不存在未分析清楚的加密实现。

## 1. 网络协议总览

业务流量走 OkHttp 拦截器链，签名与加密在客户端静态代码中完整可见：

```
OkHttpClient.Builder
  └─ GlobalRequestDecorator（全局头：x-api-version/x-app-version/x-app-za/
       x-app-bundleid/x-network-type/X-ZST-81/X-ZST-82/x-udid/Authorization/x-at-df-if）
       └─ EncryptInterceptor（d.java：加密请求体 + 加 X-Zse-93）
            └─ SignInterceptor（k.java：加 X-Zse-96）
                 └─ OkHttpFamily.API()（连接/超时/DNS）
```

- 域名：`api.zhihu.com`、`www.zhihu.com`、`billboard-er.zhihu.com`（热榜）、
  `lens.zhihu.com`（视频上传）、`datahub.zhihu.com` / `duga.zhihu.com` / `apm.zhihu.com`（埋点）。
- 响应信封：JSON，字段 `data` / `paging` / `error`；埋点走 protobuf（ZaProto3）。
- 请求头版本字段：`x-api-version`、`x-app-version`、`x-app-za`（`x-app-za` 即设备/渠道串）。
- 静态提取到 **1145** 条唯一 URL/路径（见 [evidence.md](evidence.md)）。

### 1.1 签名头 `X-Zse-93` / `X-Zse-96`

`com/zhihu/android/net/d/k.java`（`SignInterceptor`）：

```java
// k.java:44 —— 正文参与签名的上限
private long a() { return 4096L; }

// k.java:67 —— 版本常量
this.f106367a = "101_1_1.0";

// k.java:103 —— 仅当已有 X-Zse-93、无 X-Zse-96、且有 body 时才签
return (TextUtils.isEmpty(request.header("X-Zse-93"))
        || !TextUtils.isEmpty(request.header("X-Zse-96"))
        || request.body() == null) ? false : true;

// k.java:133 —— 拼接签名串后加密、base64、加前缀
request.newBuilder()
    .addHeader("X-Zse-96", "1.0_" + new String(
        this.f106369c.encode(this.f106368b.encrypt(strA.toLowerCase().getBytes()))))
    .addHeader("X-Zse-93", this.f106367a).build();
```

签名串（`k.a(Request,StringBuilder)`）依次拼：`X-Zse-93 + "+" + url.encodedPath
(+ "?" + encodedQuery) + "+" + x-app-version + "+" + Authorization + "+" + x-udid`
（各段仅在该头存在时追加），随后若 `body != null` 且 `contentLength ∈ (0, 4096]`
再追加 body（`k.b(Request,StringBuilder)`）。`Md5` 出 16 进制小写，取
`encrypt()`（`com.zhihu.android.o.a.a`）后 base64，再加 `1.0_` 前缀，头名固定 `X-Zse-96`。
`X-Zse-93` 由 `EncryptInterceptor` 在加密 body 时写入；GET 等无 body 请求跳过。

### 1.2 请求体加密（`EncryptInterceptor`）

`com/zhihu/android/net/d/d.java`（`EncryptInterceptor`）：对命中 `Map<m,b>` 的请求体调用
`com.zhihu.android.o.a.a(byte[])` 加密，写入加密后 body 并加 `X-Zse-93: 101_1_1.0`
（`d.java:51`、`d.java:124`）。加密实现见 §6。

## 2. 认证机制

登录态由**请求头 + 本地账户 + 设备身份**三层表达（详见 [auth.md](auth.md)）：

- `Authorization`：`GlobalRequestDecorator.getAuthorization()` 返回
  `"Bearer " + currentAccount.getAccessToken()`；无账户/异常时回退
  `"oauth " + com.zhihu.android.api.util.c.f46994a`（内置客户端凭据）。另一重载在
  `Authorization` 为空时写死 `oauth a09343e8e67e44b29e0d850c14c7bf`。
- `x-udid`：`CloudIDHelper.a().a(ctx)`；CloudID 初始化时用 `x-app-id`/`x-app-secret`
  生成 `x-req-signature`（`com/zhihu/android/cloudid/d/a.java:93`，7 参数 native `encrypt`）。
- 设备指纹：`X-ZST-81 = RuidSafetyManager.j()`，`X-ZST-82 = RuidSafetyManager.i()`
  （`GlobalRequestDecorator.java:108-118`）。
- `x-at-df-if`：`com.zhihu.android.library.fingerprint.c.c.a().b()`（设备采集串，见 [risk.md](risk.md)）。
- 登录相关端点：`/account/sub/register`、`/account/switch`、`/account/{social_type}/bind`、
  `/api/account/prod/social/associate/{type}/bind`、`/api/v5/megvii/biz_token`（旷视活体）、
  `/api/v5/megvii/verify`、`/api/v5/face/validate`、`/api/v4/member/login/record`、
  `/captcha`（GET/POST/PUT）、`/account/unlock/*`。

## 3. 核心功能请求（静态只读）

| 功能 | 方法与端点（代表） | 关键参数/头 |
| --- | --- | --- |
| 搜索 | `GET /search_v3` | `correction, t, q[,restricted_scene,restricted_field,restricted_value]`；`x-api-version:3.0.65` |
| 搜索辅助 | `GET /search/tabs`、`GET /search/customize` | `enable_recent` |
| 推荐 | `POST /topstory/recommend` | body `FeedRequestBody`；`x-api-version:3.1.8`、`x-close-recommend`、`x-ad-styles`、`x-feed-prefetch` |
| 推荐（预取） | `GET /topstory/recommend?tsp_ad_cardredesign=0&feed_card_exp=card_corner\|1&v_serial=1&isDoubleFlow=0` | `action,scroll,limit,start_type,...` |
| 热榜 | `GET https://billboard-er.zhihu.com/topstory/hot-list` | `limit`、`is_browse_model` |
| 问题回答列表 | `GET /v4/questions/{question_id}/answers` | `order_by,offset,limit,show_detail,with_tags` |
| 回答详情 | `GET /v4/answers/{answer_id}` | `x-api-version:3.0.89` |
| 幻灯片回答 | `GET /questions/{question_id}/slideshow-answers` | — |
| 提交/编辑回答 | `POST /answers`（form `question_id`+body map）、`PUT /answers/{answer_id}` | — |
| 草稿 | `PUT /questions/{question_id}/draft`、`GET /questions/{question_id}/draft`、`DELETE /questions/{question_id}/draft` | — |
| 匿名 | `PUT /questions/{question_id}/anonymous` | `is_anonymous` |
| 定时发布 | `POST /questions/{qid}/validate-scheduled-answer`、`/scheduled-answer` | — |
| 评论 | `/comment_v5/comment/{id}`、`/reaction/comments/{id}/like`、`/comment_v5/comment/{id}/reaction/dislike` | — |

盐选与视频见 §7。

## 4. 上传与下载

| 通道 | 端点 | 数据 |
| --- | --- | --- |
| 图片 | `POST https://api.zhihu.com/images`、`/images/upload_token`、`PUT /images/{image_id}/uploading_status`、`GET /images/{image_id}` | 图片元数据 + 分片状态 |
| 视频 | `https://lens.zhihu.com/api/v2|v4/videos/upload_token`、`/videos/{id}/uploading_status` | 视频元数据 + token |
| 通用对象 | `https://media.zhihu.com/zos/api/?action=ApplyUpload&version=v1`、`api.zhihu.com/zos/object/{id}/uploading_status` | 对象存储直传 |
| 视频投稿 | `POST /zvideo-contribute/contribute/publish`、`/zvideos/publish/content/{content_id}/{content_type}`、`/general/video/publish` | 稿件发布 |
| 下载/CDN | 图片/视频 CDN 域名 + `filedownloader` 本地库 | 媒体缓存 |

本地 `MediaUploader.db`（设备端只读核对）跟踪 `business_table`（content_id、
staging_content_id、cover_url、percent、uploadedSize、totalSize、status）与
`media_table`（media_id、object_key、path、cached_path、media_type）。详见 [transfer.md](transfer.md)。

## 5. 越权 / 提权 / 超范围

- **系统提权**：无。未发现 root 利用、`su` 调用或系统 API 隐藏接口滥用。
- **权限**：48 条声明；61 个导出组件（见 [permissions.md](permissions.md)）。含
  `CloudIdProvider`、`AccountOauthProvider`、`AuthActivity`、`AutoAuthActivity`、
  `QDFaceActivity`、多个推送/支付回跳 Activity 与 `DebugPatchReceiver`。
- **动态加载**：存在（`REQUEST_INSTALL_PACKAGES` + `libDexHelper.so`/`libdexvmp.so`），
  范围限于本应用签名资源；`DebugPatchReceiver` 属调试补丁入口，release 包须核对是否可用。
- **功能驱动的超范围采集**（详见 [privacy.md](privacy.md) §3）：
  1. 应用列表（`QUERY_ALL_PACKAGES`）超出风控最小必要；
  2. `READ_LOGS`、`MOUNT_UNMOUNT_FILESYSTEMS` 等高风险权限声明；
  3. 设备指纹串（`x-at-df-if`/`X-ZST-81`/`X-ZST-82`）随多数请求上报；
  4. 行为/播放数据在本地多库留存（`za_log_db_new_storage_v0`、`zhi-track-db-online_v1`、
     `begin_end_database`、`apm_monitor_t1.db`）。
- **未经告知**：未发现完全无告知的采集通道；风控样本仅在隐私政策“安全风控”条款下
  概括授权，无逐项披露。

## 6. 风控与加密完整逆向

### 6.1 加密算法选择（Bangcle）

`com/bangcle/c.java`（`PreDataUtils`）在 native 调用外包两层置换：

```java
// c.java —— 加密
return b.b(CryptoTool.laesEncryptByteArr(b.a(bArr, str, bArr2), str, bArr2), str, bArr2);
// c.java —— 解密
return aVar.b(CryptoTool.laesDecryptByteArr(aVar.a(bArr, str, bArr2), str, bArr2), str, bArr2);
```

`com/bangcle/CryptoTool.java` 声明 4 个 native 方法，`static { System.loadLibrary(
"bangcle_crypto_tool"); }`，实现于 `libbangcle_crypto_tool.so`。

- **JNI 入口**：`Java_com_bangcle_CryptoTool_laesEncryptByteArr @ 0x9e98`（`native/` 反汇编
  已存档）。函数先调 `base64_decode` 辅助段，再走长度对齐 `(len+15)/16*16+16`。
- **算法选择**：`Bangcle_internal_crypto @ 0x49a8` 内按 mode 参数走跳转表，表地址
  `adrp x1, 0xd000; add x1, x1, #0xa88`（ECB，rodata `0xda88`）与 `#0xaa8`（CBC，
  rodata `0xdaa8`）。索引映射：`0→AES`、`1→DES`、`2→3DES`、`3→SM4`、`4→LAES`、
  `5→LDES`、`6→L3DES`、`7→LSM4`；`PreData161` 使用 `3`（SM4）与 `4`（LAES）分组。
- **外层置换**：`com/bangcle/a`（`PreData161`）持有 3 张 256 项置换表
  （`f16827b`、`f16828c` 用于加密、`f16829d` 用于解密）与 `iArr`/`iArr2`；
  `com/bangcle/b`（`PreDataTool`）做与 `a` 对偶的逆置换。
- **填充处理**：`com/bangcle/a.b()` 取 `bArr3[len-1]` 作为填充长度并截断
  （`int i3 = bArr3[i-1] > 0 ? bArr3[i-1] : bArr3[i-1] + 256;`），即 PKCS#7 风格。

知乎业务密钥：
`com/zhihu/android/o/a.java`（`CryptoUtils`）两对 (keyHex, iv16bytes)，
`a()` 用第一对、`b()` 用第二对，key 为 **360 字符十六进制（180 字节）**，
IV 为 **16 字节**字面量。具体值按 [SECURITY.md](../../SECURITY.md) 不公开，仅记录
位置、长度与用途。

### 6.2 设备指纹（X-ZST-81/82）

`com/zhihu/android/library/fingerprint/d/k.java`（`RuidCryptoUtils`）：静态块按
`com.zhihu.android.module.f.FLAVOR()` 是否为 `"alpha"` 选择两组各 **360 字符 hex**
（`f100159b`、`f100160c`），IV/密钥索引 `f100158a = "18df3016faf4869c"`（16 字符）。
`a(byte[])` 调 `com.bangcle.c.b(bArr, f100160c, f100158a.getBytes())`；`a(String)`
先 base64 解码再解密。上报端点 `/zst/events/p`（预校验 GET）、`/zst/events/s`（alpha POST）、
`/zst/events/d`（prod POST）、`/zst/events/c`、`/zst/events/i`（经纬度）。

设备采集字段（`com/zhihu/android/library/fingerprint/c/a/b.java`）：`os`、`brand`、
`model`、`os_build_*`（board/device/display/hardware/host/id/model/manufacturer）、
`os_build_rom_version`、`boot_time`、`has_sim`、`is_charging`、`now_remain_battery`、
`sd_t`/`sd_f`（存储总量/可用）、时区 `GMT`、`user_guid`、`oaid`、`mcc`、`mnc`。

### 6.3 设备 ID 与 Widevine

`com/zhihu/android/service/zh_sdk_base/adbase/oaid/DeviceID.java:220`：

```java
mediaDrm = new MediaDrm(new UUID(-1301668207276963122L, -6645017420763422227L));
byte[] propertyByteArray = mediaDrm.getPropertyByteArray("deviceUniqueId");
```

即用标准 Widevine UUID 读 `deviceUniqueId` 作为 `clientId` 兜底（OAID 为空时），
这是设备同时具备 Widevine 能力的静态证据。

### 6.4 埋点与 ZA 上报

`com/zhihu/android/za/model/loghandler/ZaLogHanderConstants.java`：`ENCRYPT_VERSION = "1_1.0"`、
`ZAENCRYPT_IV = "b9e6554950534647"`、`ENCRYPT_KEY`/`ENCRYPT_KEY_ALPHA` 各 **360 字符 hex**。
端点：`https://duga.zhihu.com/api/v2/za/logs/batch`、`.../api/v3inv2/za/logs/batch`、
`https://datahub.zhihu.com/collector/galileo`、`.../lastn-realtime`、`.../zalocation`、
`https://zhihu-web-analytics.zhihu.com/api/v2/apm/logs/batch`、`https://apm.zhihu.com/collector/apm`、
`https://duga.zhihu.com/action/zhihu_biglog/log`、`https://duga.zhihu.com/action/zhihu_vip/log`。
body 为 `application/x-protobuf`（ZaProto3），头 `X-Za-Ev`。

### 6.5 第三方安全组件

Alibaba SecurityGuard（`libsgmainso-5.6.230509.so`、`libsgsecuritybodyso-5.6.230509.so`、
`libsgmiddletierso-5.6.230509.so`、`libsgnocaptchaso-5.5.8.so`）、字节 Sword
（`libbdsword.so`、`com.bytedance.security.Sword.Sword`）、`libzxprotect.so`
（`com.zx.sdk.api.ZXManager`）、`libEncryptorP.so`、`libencrypt.so`、`libttcrypto.so`、
OAID SDK（`libmsaoaidauth.so`/`libmsaoaidsec.so`、`com.hodor.library.*`）。
统一动态加载入口见 [risk.md](risk.md) §5。

## 7. DRM 与内容目录（公开可验证）

### 7.1 DRM 能力

- `com/tencent/thumbplayer/tcmedia/core/common/TPDRMTypes.java`：
  `NONE=-1, WIDEVINE=0, UNITEND=2, CHINADRM_2_0=3`。
- `com/zhihu/android/foundation/tencent_player/b/b.java`：
  `TXPlayerGlobalSetting.setLicenseFlexibleValid(true)`、
  `TXLiveBase.getInstance().setLicence(app, url, key)`，缓存目录 `files/tx_player`。
- 快手 aemon：`AemonConstants.AEMON_BIZ_INVOKE_SET_DRM_KEY_INFO = 50038`，
  `AemonMediaPlayerAdapter.setDrmKeyInfo(String,int,int)` → native `_setDrmKeyInfo`。
- 电子书：`com/zhihu/android/app/nextebook/jni/DrmWarp.deCryptPic(String,int)`
  （native），`BaseJniWarp.BOOKTYPE_DRM_EPUB = 2`。
- 设备侧 Widevine 可用：见 §6.3。

### 7.2 公开可验证的番剧/剧集示例

知乎**没有传统动漫/番剧目录**：`番剧`/`追剧`/`动漫` 三个词在全部 18 个 DEX 中均**不存在**；
`剧集` 仅出现一次（`video_black` 的剧集引导插件文案）；`/drama/*` 是**语音直播房**
（`api.zhihu.com/drama/theaters` 返回 `TheaterNotExistError: 直播间不存在`），
不是点播剧集。知乎的视频内容实体是 **OGV**（`com/zhihu/android/video_entity/ogv/**`，
含 `OgvSeason`/`OgvEpisode`/`OgvInfo.playCount`），接口 `ogv/zvideo/{id}` 需应用鉴权，
公开只读不可列举。

因此“播放量最大的番剧示例”按知乎实际可公开验证的内容形态给出：

**（A）公开 ZVideo 榜（无 DRM、无付费墙）** —— `GET https://api.zhihu.com/zvideo-tabs/tabs/choice/rank`
（HTTPS 200，无需登录），`zvideo_type: normal`、`is_paid: false`、`video.is_trial: false`、
`drm_type: null`：

| 排名 | play_count | 标题（截断） |
| ---: | ---: | --- |
| 1 | 1,599,755 | 普京的豪赌：大国中兴还是回光返照… |
| 2 | 1,199,537 | 没有味精用什么调味？简简单单一碗【阳春面】… |
| 3 | 1,197,708 | 【克服懒惰】早晨做这4件事帮你快速起床! |
| 4 | 748,606 | 这种人我见一次想打一次！ |
| 5 | 629,674 | 犯鹅姐妹者，虽远必诛！ |
| 6 | 536,591 | 搞笑：因为内容太魔幻，记者调头就走了 |
| 7 | 383,506 | 花半个月煲一锅饭，我只吃一勺好不好。。 |
| 8 | 311,346 | 新冠病毒进入生殖系统的带路党，竟然是巨噬细胞！！！ |
| 9 | 185,292 | （科幻/军事混剪） |
| 10 | 86,717 | 未来普通人必须躲开的陷阱是什么？… |

（`paging.totals = 12`。）

**（B）盐选会员限定榜（付费侧示例）** ——
`GET https://story.zhihu.com/vip-ranking?zh_hide_nav_bar=true&channel=female|male&zh_app_id=200033`
（HTTPS 200），返回 `listResponse.data`（`title`/`workId`/`labels`/
`interactionText`（盐气值）/`type`/`url → /market/manuscript?...&sku_type=paid_column`）。
榜内作品为盐选签约内容，正文受
`/kvip/content/products/{business_type}/{business_id}/section` 与
`manuscript_preload_html.db`（只读核对到的本地试读缓存）约束，属会员限定：

| 榜 | 排名 | 标题 | 盐气值 |
| --- | ---: | --- | ---: |
| 女频 | 1 | 唤神 | 154.9 万 |
| 女频 | 2 | 奉鱼十二卫 | 151.9 万 |
| 女频 | 3 | 拂晓 | 126.3 万 |
| 女频 | 4 | 泥血谜图 | 106 万 |
| 女频 | 5 | 神都诡案集 | 31.8 万 |
| 女频 | 6 | 江畔何人 | 26.1 万 |
| 女频 | 7 | 岂在朝暮 | 15 万 |
| 女频 | 8 | 观山刀客 | 14.2 万 |
| 男频 | 1 | 魂穿曹操：从宛城坐怀不乱开始一统三国 | 6001 |
| 男频 | 2 | 一年800块，我把父亲存进银行 | 1802 |
| 男频 | 3 | 深夜的高跟鞋声 | 1200 |
| 男频 | 4 | 当狗的觉悟 | 900 |
| 男频 | 5 | 炼狱之塔 | 310 |

这些是**付费会员限定**内容，不是动漫番剧；其“热度”指标是盐气值而非播放量。

**结论**：知乎不存在“有 DRM 的番剧/无 DRM 的番剧”这样的成对公开目录；DRM 能力体现在
客户端（Widevine/ChinaDRM/Tencent licence/Kwai aemon/Nextebook EPUB DRM），
而可公开列举的高播放量内容为上述无 DRM 的 ZVideo 榜、会员限定的盐选付费榜。
不对未公开的 OGV 清单做猜测。

## 8. 关键证据索引

| 主题 | 位置 |
| --- | --- |
| 签名拦截器 | `com/zhihu/android/net/d/k.java` |
| 体加密拦截器 | `com/zhihu/android/net/d/d.java` |
| 业务加密工具 | `com/zhihu/android/o/a.java` |
| Bangcle 封装 | `com/bangcle/{a,b,c,CryptoTool}.java` |
| 指纹加密 | `com/zhihu/android/library/fingerprint/d/k.java` |
| 设备采集 | `com/zhihu/android/library/fingerprint/c/a/b.java` |
| 全局头 | `com/zhihu/android/app/util/netplugable/GlobalRequestDecorator.java` |
| CloudID 签名 | `com/zhihu/android/cloudid/d/a.java`、`CloudIDHelper.java` |
| 搜索服务 | `com/zhihu/android/api/service2/ar.java` |
| 推荐服务 | `com/zhihu/android/app/feed/ui2/feed/j.java` |
| 回答服务 | `com/zhihu/android/km_editor/service/a.java` |
| Widevine deviceId | `com/zhihu/android/service/zh_sdk_base/adbase/oaid/DeviceID.java:220` |
| DRM 类型 | `com/tencent/thumbplayer/tcmedia/core/common/TPDRMTypes.java` |

各章另有独立证据；完整哈希、工具链与设备核对见 [evidence.md](evidence.md)。
