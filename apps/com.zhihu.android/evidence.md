# 逆向证据

## 1. 样本

| 项 | 值 |
| --- | --- |
| 包名 | `com.zhihu.android` |
| versionName | `11.10.0` |
| versionCode | `41012` |
| 文件 | `base.apk` |
| 大小 | `183,316,018` 字节 |
| SHA-256 | `93d5ee0d6273d88b85f719fe1c1bd92a3343aeae8f25b17ec5c69649963ad809` |
| 架构 | arm64-v8a |
| minSdk / targetSdk | 21 / 34 |
| DEX | 18（`classes.dex` + `classes2.dex` … `classes18.dex`） |
| 安装时间（设备） | 2026-09-30 |

APK 顶层结构：`AndroidManifest.xml`、`classes*.dex`、`lib/`、`res/`、
`resources.arsc`、`assets/`、`META-INF/`、`track.proto`、`DebugProbesKt.bin`、
`com/`、`google/`、`kotlin/`、`org/`。`DebugProbesKt.bin` 表明含 kotlinx-coroutines
调试代理；`track.proto` 为埋点 protobuf 描述。

## 2. 关键 native 库哈希

| 库 | 大小 | SHA-256 |
| --- | ---: | --- |
| `libbangcle_crypto_tool.so` | 66,976 | `c1803ecd59c144ea06423887c42a63af5e28a3f5508a2d3bb42dee29b56cd428` |
| `libsgmainso-5.6.230509.so` | 2,110,076 | `01c58dbec858d344d5400f86cc42d70ea1122a06e9b6a5f74d39f7078b55af15` |
| `libbdsword.so` | — | `7860fa1f0856fc4457ac37ea271e835ecaef647e2fdf46b46b53e67b1aa78433` |
| `libzxprotect.so` | 486,456 | `f90aa7c1a1833866f8c75c60045f527804abe1f45506c3930d4f4af039ec2d8d` |

共提取 163 个 `lib/arm64-v8a/*.so`（123 MB）。

## 3. 工具链

| 阶段 | 工具 |
| --- | --- |
| 反编译 | `jadx`（dev build），全量 Java 反编译 |
| APK 结构 | `unzip -l` |
| ELF | `readelf`、`objdump -d`（AArch64），`native/bangcle_laesEncryptByteArr.asm` |
| 数据库 | `python3 sqlite3` 只读 URI + `PRAGMA integrity_check`/`table_info` |
| 设备核对 | 自有设备 root shell，只读 `ls`/`grep -o` 键名 |
| 公开接口 | `curl`（只读 GET，无登录） |

## 4. 反编译覆盖与失败归因

- jadx 全量处理 **43,589** 个类，输出 **62,061** 个 `.java`。
- 结束状态：`finished with errors, count: 65`（非致命）。
- 65 个错误全部为 jadx 无法完整还原的**合成/内联方法**（`$$Lambda$`、
  `$ExternalSynthetic`、`PatchProxy` 代理方法）与个别 `switch` 反编译告警
  （如 `net/d/k.java` 的 `a(Request,RequestBody)` 有 `Can't fix incorrect switch cases`
  告警，但语义可由 `case "delete"/"put"/"post"/"patch"` 完整读出）。
- **无未解释的加密方法**：所有 `System.loadLibrary` 目标、`native` 声明与
  加密调用点均已归类（见 [risk.md](risk.md) §6、§8）。

## 5. 静态提取统计

| 项 | 数量 |
| --- | ---: |
| 唯一 URL/路径 | 1,145 |
| 唯一主机 | 52 |
| `android.permission.*` | 48 |
| 全部唯一 `<uses-permission>` | 66 |
| Activity / Service / Receiver / Provider 声明 | 299 / 56 / 24 / 33 |
| `exported="true"` 组件 | 61 |
| native 库 | 163 |
| 设备端 SQLite | 42（148 表，677 行） |
| 设备端 SharedPreferences | 103 |

## 6. 设备端只读核对

- 命令：`ls`、`du -sh`、`sqlite3`（只读）、`grep -o '<name="...">'`（仅键名）。
- 未执行：写入、删除、`SELECT *`、导出值、网络请求。
- 全部 42 库 `integrity_check = ok`；`phone-db-schema.err` 为空。
- NetCache 实样（9 个 JSON，解密后）用于确认响应信封字段：
  `CACHE_KEY_FEED_NEW`（`FeedList`，851 KB）、`feed_hotlist`（`RankFeedList`，
  30 项）、`CACHE_KEY_SEARCH_HOT`、`CACHE_KEY_SEARCH_GUESS`、
  `svip_channel_info`（`ChannelInfo`）、`ZIM_STICKER_GROUP`、
  `AppCloudConfigNetCache`、`WebbSamplerNetCache`、`ApmSamplerNetCache`。
  格式：`{"className":…, "key":…, "result":{"stability":…, "data":[…]}}`。

## 7. 公开接口核对（无登录、只读 GET）

| 请求 | 结果 |
| --- | --- |
| `GET https://api.zhihu.com/zvideo-tabs/tabs/choice/rank` | 200，`paging.totals=12`，含 `play_count` |
| `GET https://api.zhihu.com/topstory/hot-lists` | 200 |
| `GET https://story.zhihu.com/vip-ranking?channel=female&zh_app_id=200033` | 200，HTML 内嵌 `listResponse.data` |
| `GET https://api.zhihu.com/drama/theaters` | 401 `AuthenticationInvalidRequest` |
| `GET https://www.zhihu.com/api/v4/drama/theaters` | 405 |
| `GET https://api.zhihu.com/drama/theaters/1` | 400 `TheaterNotExistError`（直播间不存在） |
| `GET https://www.zhihu.com/api/v4/ogv/**` | 404（OGV 接口需应用鉴权） |
| `GET https://www.zhihu.com/api/v4/search_v3?t=general&q=ogv` | 403 |

以上用于区分「公开可列举」与「需鉴权」的内容，不构成对目标服务的压力测试。

## 8. 代码引用索引

| 主题 | 文件:行 |
| --- | --- |
| X-Zse-93/96 签名串 | `net/d/k.java:44,67,103,111,119,133,139-171,176-233` |
| MD5 大写 hex | `net/d/k.java:216` |
| 体加密拦截器 | `net/d/d.java:21,51,124` |
| 业务密钥 | `o/a.java:13-20` |
| Bangcle 封装 | `com/bangcle/c.java`、`com/bangcle/a.java`、`com/bangcle/b.java` |
| JNI 入口 | `libbangcle_crypto_tool.so @ 0x9e98`（`native/bangcle_laesEncryptByteArr.asm`） |
| 算法分派 | `Bangcle_internal_crypto @ 0x49a8`，跳转表 rodata `0xda88`/`0xdaa8` |
| 指纹加密 | `library/fingerprint/d/k.java:15,25,26,53` |
| 指纹管理 | `library/fingerprint/b.java:189-195`、`d/e.java`（SP `zhihu_ruid_shared_preferences`） |
| 设备采集 | `library/fingerprint/c/a/b.java` |
| 风控上报 | `library/fingerprint/d/j.java`（`/zst/events/{p,s,d}`） |
| 动态风控 | `library/fingerprint/dynamic/{a,b,c}.java` |
| Widevine deviceId | `service/zh_sdk_base/adbase/oaid/DeviceID.java:220-226` |
| 全局头 | `app/util/netplugable/GlobalRequestDecorator.java:84-130,154-165,199-202` |
| CloudID 签名 | `cloudid/d/a.java:77,93`、`CloudIDHelper.java:78,80,112` |
| 搜索 | `api/service2/ar.java`、`km_editor/service/c.java` |
| 推荐 | `app/feed/ui2/feed/j.java:34-66` |
| 问答 | `km_editor/service/a.java` |
| 盐选目录 | `feature/kvip_catalog/catalog/a.java` |
| OGV | `video_entity/ogv/a/a.java`、`ogv/bean/{Ogv,OgvSeason,OgvEpisode,OgvInfo}.java` |
| DRM 类型 | `com/tencent/thumbplayer/tcmedia/core/common/TPDRMTypes.java` |
| 腾讯 licence | `foundation/tencent_player/b/b.java`（`setLicence`） |
| 快手 aemon DRM | `com/kwai/video/aemonplayer/AemonMediaPlayerAdapter.java:1587` |
| 电子书 DRM | `app/nextebook/jni/DrmWarp.java`、`BaseJniWarp.BOOKTYPE_DRM_EPUB=2` |
| 应用列表上报 | `com/hodor/library/c/f.java:293-300,358-368,236-249`、`c/c.java:46-58` |
| 应用枚举策略 | `service/zh_sdk_base/adbase/common/SDKSmellUtils.java:187-203` |
| ZA 常量 | `za/model/loghandler/ZaLogHanderConstants.java` |
| 图像上传 | `picture/upload/processor/oss/c.java` |

## 9. 证据等级

| 结论 | 等级 |
| --- | --- |
| 版本/哈希/权限/组件计数/端点 | 已验证 |
| 签名串构成、MD5 编码、base64/前缀 | 已验证 |
| Bangcle 算法选择表与置换表 | 已验证（反汇编 + 源码表） |
| Widevine UUID 常量 | 已验证（字面量） |
| 应用列表枚举与上传 | 已验证（源码 + 设备端键名） |
| SecurityGuard/Sword/zxprotect 内部 | 结构已证实（入口/参数），内部实现不做推测 |
| 服务端评分/阈值/留存 | 不可达 |
