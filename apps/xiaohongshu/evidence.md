# 小红书逆向证据

## 样本指纹

| 项目 | SHA-256 |
| --- | --- |
| XAPK | `42033a369835209738ee5b4b1ad6553e6289cac09fee8559fbf4286c9d490bbd` |
| base APK | `0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c` |

## 网络与认证

| 证据 | 类/文件 | 结论 |
| --- | --- | --- |
| Retrofit method annotations | `fvc/f`, `fvc/o` | GET/POST |
| form/query/header/part/body | `fvc/e`, `c`, `t`, `u`, `q`, `a` | URL-encoded form、query、header map、multipart、body |
| 公共参数 | `z2c/e`, `it6/h` | 按 `key=value&...` 写入 `xy-common-params` |
| 响应封装 | `EdithBaseResponse` | `{code, success, msg, data}` |
| 登录 | `AccountApi`, `LoginLoginResponse` | 登录路径、form 字段和 token 状态 |
| OAuth | `IOAuthService`, `AuthorizeData`, `AuthorizationData` | authorize/auth_info 字段 |
| 风控 | `IRiskService`, `IVerifyCodeService` | 设备违规、账号异常、干预、captcha、解限和验证码路径 |
| Tiny API | `com.xingin.xhs.antispam.entities.TinyTokenApi` | `POST /api/sc/tt`，请求五字段，响应 `ts` map |
| Shield Java 边界 | `com.xingin.shield.http.Native` | OkHttp interceptor JNI 入口 |
| 主域/旁路域 | Retrofit builders、ModelProfile、HeraAbility | edith、rec、modelportrait、CDN 等 |
| 实时长连接 | `com.xingin.longlink.*`, `libxhslonglink.so` | Mars STN TCP、protobuf、ECDH/AES/gzip、ACK/推送 |

证据等级：**类与字段已确认**。`z2c.e.c(Request)` 进一步证明 `sid` 来自 `IUserService.getSessionId()`、`id_token` 来自 `IUserService.getIdToken()`；session 规范化为 `session.<sessionNum>`。

## 实时长连接

| 证据 | 地址/类 | 结论 |
| --- | --- | --- |
| Java 消息模型 | `BaseSendMessage`, `UpMessage`, `DownMessage`, `LoginMessage` | messageType、ackMode、cmd、messageId、body/extraInfo |
| 登录模型 | `ClientInfo`, `LoginInfo` | 默认 `serializeType="protobuf"`；uid/sid/authType/domain/geoCode |
| JNI 密码学 | `LongLink.C2Java` | secp256r1 ECDH、33 字节压缩公钥、AES/CBC/PKCS5 |
| 传输栈 | `libxhslonglink.so` symbols | `mars::stn::LongLink`, task manager、DNS、heartbeat/reconnect |
| 压缩/握手 | `TNBaseTunnel::Pack` | `Gzip::Compress`、handshake status、server key handling |
| protobuf 常量 | `Options` `0xe3f8c`-`0xe3f9c` | fields 1..5 |
| protobuf 常量 | `AuthInfo` `0xe2fd0`-`0xe2fdc` | fields 1..4 |
| protobuf 常量 | `LoginPacket` `0xe2f90`-`0xe2fb4` | fields 1..12（缺省 7/8） |
| protobuf 常量 | `CSStreamData` `0xe289c`-`0xe28b4` | fields 1..7 |
| frame 常量 | `DataFrame`/`SignalFrame`/`SyncFrame` | data=100，ack=101 |

字段号由 `readelf -Ws` 的 `k*FieldNumber` 符号读取 `.rodata` 32 位值确认；wire 类型由各 `SerializeWithCachedSizes` 调用的 `WriteString/WriteBytes/WriteMessage/WriteEnum/WriteInt32/WriteInt64` 交叉确认。证据等级：**protobuf schema 与 transport 类型已闭环**；shared secret 到 AES key/IV 的运行时秘密派生和外层 frame 头不作为公开实现。

## Shield / Tiny native

### `libxyass.so`

| 地址 | 符号/函数 | 证据 |
| ---: | --- | --- |
| `0x3f484` | `JNI_OnLoad` | 获取 Application、解密字符串、注册 native |
| `0x3f6f8` | `RegisterNatives` call | `JNINativeMethod[4]` |
| `0x45764` | `intercept` | `(Lokhttp3/Interceptor$Chain;J)Lokhttp3/Response;` |
| `0x49634` | signer | 输出 `{u32 type, payload[>=64B]}` |
| `0x467dc` | assembler | 按 token type 分派并装配 shield |
| `0x46a14` | type 6/7 path | 会话 token 派生/刷新 |
| `0x4b3d0` | outer builder | P、RC4、blob header、Base64 |
| `0x4b658`-`0x4b9e4` | RC4 KSA/PRGA | S 盒与密钥流 |
| `0x7f094` | digest container | 三阶段摘要状态 |
| `0x7f224` | key setup | 取 token payload 前 64 字节 |
| `0x7feec` | update | 请求字节流输入 |
| `0x8001c` | final | 输出 16 字节摘要 |

### 外层重建

```python
payload = u32be(1) + u32be(app_id) + u32be(1)
payload += u32be(len(build)) + u32be(len(device_id)) + u32be(16)
payload += build + device_id + digest16

blob = u32be(4 | (token_type << 16)) + u32be(1)
blob += u32be(len(payload)) + u32be(len(payload))
blob += rc4(rc4_key, payload)

shield = "XY" + base64(blob)
```

证据等级：**两组合成向量逐字节验证**。RC4 key 不公开。

### 摘要结构

```python
K64 = token_payload[:64]
inner = H(bytes(a ^ 0x36 for a in K64) + request_bytes)
digest16 = H(bytes(a ^ 0x5c for a in K64) + inner)
```

证据等级：**HMAC 外壳已证实**；定制 `H` 行为级闭环，字节级实现不公开。

### `libtiny.so`

| 地址 | 证据 |
| ---: | --- |
| `0x18afd8` | `JNI_OnLoad` |
| `0x15e9f4` | 模拟器捕获的 native handler |
| `0x16b08c` / `0x17cdb0` | opcode 二叉比较点 |
| `0x754AC0` | 约 112 KB 加密 blob |
| `0xf7b40`-`0xf7bb0` | blob 描述符/模块表 |
| `0x631438` | wac 模块注册候选点 |
| `0x143d84` | 模块字节复制点 |

证据等级：**结构已证实**；loader/opcode 边界闭环，blob payload 不作为公开代码。

## 上传下载

| 证据 | 类 | 结论 |
| --- | --- | --- |
| 上传配置 | `UploadConfig` | contentType、filePaths、tokenConfig、multipart、retry、copy、EXIF |
| token 基类 | `MixedToken` | fileBytes/filePath/fileId，默认 1 MiB chunk |
| 对象 token | `RobusterToken` | address/bucket/cloud/region/QoS、临时 secret/session token |
| 上传 permit | `RobusterTokenPermit` | fileIds、时间、secret、token、uploadAddr、storage |
| Qiniu 执行 | `c0` | `UploadManager.put(fileBytes|filePath, fileId, token)` |
| 上传结果 | `UploaderResult`, `QuickUpload`, `UploadResponse` | ID、URL、scene、失败路径、结果列表 |
| 联系人 | `com.xingin.utils.core.l`, `g38.b0`, `UserServices` | display_name/data1、11 位号码、300 条分页、加密上传 |
| 联系人加密 | `com.xingin.utils.core.h1`, `f0`, `q` | AES/CBC/PKCS5 + Base64，设备派生 key |
| 位置 | `LocationDevelopApi` | latitude/longitude form；开发/诊断组件 |
| POI | `PoiSearchApi` | keyword、lat/lng、page/size/type/context |
| 媒体元信息 | `ImageDetailInfoService`, `VideoDetailInfoService` | fileKey/video_id + caller |
| token 参数 | `ITokenReqParam` | `bid`, `scene`, `tokenCount`, `isDebug` |
| permit API | `TokenService` | filename/token/permit/no-login/quick-upload 七类 GET |
| 对象存在性 | `CapaPostTagService`, `CloudObject*` | cloudObjects -> notExistFileIds |
| 发布网关 | `NoteService` | `POST/PUT /api/sns/v2/note`，`ReportParamModel` |
| 下载回执 | `DownloadService` | `POST /api/sns/v1/note/file/download`，document_id/note_id |
| 登录态使用点 | `UserInfo`, `zu/i`, Diandian/WebView models | sid/id_token、`x-access-token`、账号找回/人脸 user_token、device_password 模型边界 |

证据等级：**请求/模型字段已确认**。对象 token、秒传、去重、存在性检查和 note 创建/编辑网关均已静态闭环。

## 本地存储

### 9.37 代码

| 证据 | 类 | 结论 |
| --- | --- | --- |
| WCDB factory | `com.xingin.xhs.xhsstorage.safe.WCDBOpenHelperFactory` | Room open helper |
| WCDB open | `WCDBOpenHelper` | passphrase + `SQLiteCipherSpec` |
| DB builder | `nbc/e` | page size 1024、KDF 64000、WAL/FTS 配置 |
| message DB | `com.xingin.chatbase.db.config.a` | MMKV 动态 passphrase/默认值 |
| Hedwig DB | `com.xingin.chatbase.hedwig.database.a` | MMKV 动态 passphrase |
| Alpha/Capa/Hey | 对应 `passphrase()` 实现 | MMKV 动态 passphrase |

### 9.47 只读对照

| 项目 | 聚合结果 |
| --- | --- |
| 加密 DB | 9 个成功重建 |
| 明文 DB | 17 个直接可读 |
| `localRelationDB` | 1005 行关系用户 |
| `msgDB` | 0 消息、4 通知摘要 |
| 播放历史 | 0 行 |
| 广告计划 | 8 组 |
| 下载记录 | 1145 行 |
| 自定义埋点/监控 | 10 / 163 行 |
| DSL 模板 | 671 行 |
| 插件/补丁 | 12 / 33 行 |

风险 DB 复核：

- `dim.db`：AES-128-CBC/PKCS7 + 应用绑定标记 + Base64。
- `gtc3-key.db`：TEE Keystore RSA 包装 AES key/IV。
- `pushg3.db`：8 字节 ASCII 前缀 + gzip + 竖线记录。
- `cg.db`：AES 加密的 Java serialized Getui/GTC/GBD 配置。

证据等级：**格式、参数和聚合计数已复核**；真实行、ID、token、key 和坐标不进入仓库。

### 2026-09-30 当前只读复核

对照主机当前安装版本为 `9.48.0`。root SSH 只读查询确认同一存储族仍存在：WCDB/SQLCipher 加密库、明文 SQLite、风险 SDK SQLite、MMKV/Java serialization/gzip 容器。隔离快照的实时表行数包括 `local_relation_user` 1010、`msgDB.message` 652、`prdownloader` 2025、DSL 模板 681、Petal 插件/补丁 14/33；它们相较 9.47 历史快照已变化，因此公开报告把两个时点分开，避免把动态计数误写成固定数据范围。

## 风控

| 面 | 证据 |
| --- | --- |
| 设备指纹 | `pt.a`、Java collectors、`libxyasf.so` |
| 环境检测 | native strings/imports：root、ptrace、maps、Xposed、VirtualApp、Widevine |
| JS 指纹 | `XhsJsService`/`XhsJsJobService` manifest 声明、AES-CBC 缓存读写；服务启动路径与 `fpjs2.min.js` 文件体均未出现 |
| 守护/混淆 | `libtinyd.so`、`com.xingin.tiny.daemon`、`@u5`/`@v5` 加密字段名（519 站点已解）、字符串解密 811/811 调用点已解（daemon dex 三层混淆另见 §9.7）、Tiny opcode 分发 |
| 账号风险 | `IRiskService`、登录风险状态、self-resolve |
| 验证码 | Walify、ValidateActivity |
| 人脸核身 | turingcam、TuringV2、WBCF、活体、SM2 |
| 推送策略 | `dim.db`, `gtc3*.db`, `pushg3.db`, `cg.db` |
| 挑战/核身 | `ValidateActivity`, Walify, `libturingmfa` | H5 验证、活体/实名链路、WUP/Tars DeviceToken 协同；TMF 的 HTTP 外层、JCE 字段、压缩/XXTEA 与响应选择逻辑见 deepdive §7.1 |
| native 上报 | `libxyasf.so` strings/JNI | protobuf 字段、multipart `file=image.jpg`、`POST /api/v1/d/upload` |

## 权限与隐私

| 面 | 证据 |
| --- | --- |
| 权限清单 | `aapt2 dump badging`（`re/privacy/perm_base.txt`、`perm_config.arm64_v8a.txt`、`perm_config.mdpi.txt`、`perm_union.txt`）；`re/privacy/manifest_tree.txt`（8481 行） |
| 弹框链路 | `com.xingin.privacy.policy.PrivacyPolicyDialog`、`PrivacyPolicyPresenter`、`vt9.g.setUp()`；`f72.c`（`ru_dialog_permission_explain`）、`m82.m` |
| 电话权限节流 | `com.xingin.login.permisson.PhonePermissionHelperExtension`、SP `is_permission_dialog_shown`、APM `IMEI_APP`/`IMEI_SYSTEM` |
| 合规自查框架 | `android.xingin.com.spi.privacy.IPrivacyTracker`、`PrivacyTracerImpl`、`PrivacyThrowable`、`FreqPrivacyThrowable`、`xt9.d`、`wt9.*`、`yt9.*`（55 个五位数 API 号 + `zt9.a` 148 个 AppOps 索引） |
| 位置门控 | `wt9.e`；远端键 `andr_enable_coarse_location_check`（默认 0） |
| 协议文本 | `re/privacy/resources_values.txt`（269 580 行）、`strings_parsed.json`（133 680 条）、`privacy_strings.txt`、`long_prompts.txt`、9 条 H5 正文 URL |
| 资源解析工具 | `re/privacy/resolve.py`、`extract_str.py`、`pick.py` |

## 非公开实现与静态可见边界

| 项目 | 状态 |
| --- | --- |
| 定制摘要压缩轮 | 行为级闭环；字节级实现不公开 |
| 会话 token 派生/type 6-7 | 调用/长度/状态边界闭环；秘密变换不公开 |
| Tiny 加密 blob | loader/opcode 边界闭环；payload 不作为公开代码 |
| `user_token` Java 使用点 | 全 22 dex 的直接调用、反射字符串与生成模型已枚举 |
| `device_password` 普通 API/持久化调用点 | 全样本静态扫描为零；模型/开关/设备注册风格 body 已确认 |
| XHS native 风控 URL/transport/上传容器 | 已闭环 |
| native protobuf 字段号与变换算法 | 字段名/上传容器闭环；私有变换不公开 |
| 长连接 protobuf 字段号和 wire 类型 | 已闭环 |
| 长连接 key/IV 派生和外层 frame 头 | 协商边界闭环；运行时秘密派生不公开 |
| 隐私协议正文 | 9 条 H5 URL 已知；本研究保持只读静态边界，不把远端网页正文计入样本证据 |
| 基础模式上报签名 salt | 结构 `MD5(deviceUuid+timestamp+salt)` 已确认；32 位十六进制 salt 不公开 |

所有地址均相对于对应 ARM64 SO；仓库不包含 APK/DEX/SO、数据库或反汇编全量文件。
