# 小红书网络、存储与风控逆向报告

## 样本与证据边界

| 字段 | 值 |
| --- | --- |
| 包名 | `com.xingin.xhs` |
| 主样本 | `9.37.0`，versionCode `9370802` |
| minSdk / targetSdk | `21` / `35` |
| XAPK SHA-256 | `42033a369835209738ee5b4b1ad6553e6289cac09fee8559fbf4286c9d490bbd` |
| base APK SHA-256 | `0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c` |
| 存储对照 | 另一设备的 `9.47.0` 只读私有数据快照 |

9.37.0 的网络、代码格式和 native 签名结论来自 APK 静态逆向。9.47.0 快照只用于验证本地数据库格式、解密链路和数据类别，不能反推 9.37.0 当时实际保存了哪些业务内容。全程未登录真实账号、未发送业务请求、未修改对照设备数据。

## 总体结论

1. 主业务走 HTTPS/JSON 或表单到 `edith.xiaohongshu.com`，推荐、搜索、设备画像、动态资源和对象存储使用独立域名或 CDN。接口响应通常封装为 `{code, success, msg, data}`。
2. 每个请求叠加公共参数、登录态、请求摘要和设备标识。Shield 与 Tiny 是两套 native 请求保护；hera 路径可同时出现两套签名。
3. 登录响应返回 `session`、`secure_session`、`user_token`、`userid`、`device_password` 等状态；普通请求至少在 `xy-common-params` 中携带 `id_token`。具体 token 到各请求头的完整映射部分位于混淆/native 路径，报告不作猜测。
4. 图片/视频从对象存储凭证换取临时授权后上传，默认分片 1 MiB；下载包括业务图片视频、前端资源、广告素材、DSL 模板和插件。快照中未发现完整笔记正文或私信正文。
5. 联系人上传只筛选并归一化中国大陆 11 位手机号和显示名，按 300 条分页，并进行应用绑定的 AES-CBC/Base64 加密。位置上传接口仅传经纬度，但调用点属于开发/诊断组件，不能据此断言普通用户每次启动都上传位置。
6. 本地存储混合使用明文 SQLite、WCDB/SQLCipher 加密 SQLite、MMKV/Preferences、Java serialization 和 gzip。9.47 快照可重建 9 个加密 DB 和读取 17 个明文 DB。
7. 风控是纵深体系：请求签名、设备/环境指纹、完整性检查、JS/native 风控上报、账号风险接口、验证码、人脸核身和第三方推送 SDK 策略共同工作。

## 主要交互流程

### 启动与会话

```text
启动
  -> 初始化设备 ID、会话 ID、公共参数与网络客户端
  -> 加载 Shield/Tiny native 组件
  -> 从本地 KV/DB 恢复账号、下载、模板、插件和风控缓存
  -> 首个 Activity resume 后触发设备指纹采集/上报

登录
  -> 短信/运营商/找回流程
  -> form-urlencoded 登录请求
  -> Edith JSON 响应
  -> 保存 session / secure_session / user_token / userid / device_password
```

### 内容与媒体

```text
业务 API 返回 note/user/media 元数据和 CDN URL
  -> 图片/视频 URL 可带 imageView2/format/q/sign/t/aegis 参数
  -> 按 URL 下载并写入私有缓存/资源下载库

发布媒体
  -> UploadConfig(filePaths, contentType, tokenConfig, multipart, retry, EXIF)
  -> 获取对象存储 token/permit
  -> Qiniu/Robuster 上传文件或字节，默认 1 MiB chunk
  -> 返回 fileId/videoId/staticUrl/previewUrl
  -> 业务端使用 fileIds 创建后续内容
```

### 风控响应

```text
普通请求 -> 公共参数 + 登录态 + Shield/Tiny
         -> 服务端返回业务数据或风险码
风险命中 -> H5 ValidateActivity / Walify
         -> 账号异常查询、确认或 self-resolve
高敏场景 -> 实名/人脸/支付安全组件
```

## 协议格式摘要

| 项目 | 格式 |
| --- | --- |
| 公共参数 | `xy-common-params: k=v&k=v` |
| JSON | Retrofit body/Moshi adapter，常见响应 `{code,success,msg,data}` |
| 表单 | `application/x-www-form-urlencoded`，字段由 `@Field`/field map 编码 |
| 查询 | URL query，字段由 `@Query` 编码 |
| Multipart | 二进制 part，字段由 `@Part` 编码 |
| 请求保护 | `shield`、`xy-platform-info`、Tiny 混淆 header |
| 对象上传 | 临时 secret/token + fileId；文件或 1 MiB 分片 |
| 本地风险值 | AES-CBC/PKCS7 + 标记 + Base64，或 RSA 包装 AES key/IV |

详细字段、端点、认证边界和未知项见 [network.md](network.md)、[transfer.md](transfer.md)、[storage.md](storage.md)。

## 风控机制概览

- 请求层：Shield 外层 `XY` + Base64 blob，内部包含定制 16 字节摘要；Tiny 生成 `x-n0`、`x-o9`、`x-p0`、`x-r4`、`x-r4o` 等混淆 header。
- 环境层：root、模拟器、Xposed、VirtualApp、ptrace、进程列表、无障碍、传感器、Build、Widevine ID、APK 签名/CRC。
- 网络层：公共参数、埋点、熔断、failover、优先级和 APM；网络切换会通知 Tiny 重新处理请求保护。
- 账号层：登录风险状态、设备违规、账号异常、手机号绑定提醒、干预配置和自助解限。
- 验证层：Walify、H5 验证码、人脸核身/活体、SM2 封装和支付安全组件。
- 本地与第三方：Getui/GTC/GBD 设备维度缓存、推送状态和远程采集策略。

详见 [risk.md](risk.md)。对外报告不包含真实设备 ID、账号 ID、token、密钥、坐标、联系方式或请求样本。
