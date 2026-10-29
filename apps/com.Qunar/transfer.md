# 上传下载范围

## 1. 上传

### 1.1 传输形态

上传复用 Hotdog 隧道，但正文改为 `trans` 结构：`TransferContentStruct` 把
`content-type` 与 `body{start:0,length:n}` 写入 manifest，`content` 就是
multipart 编码后的字节流。触发条件是 `HotdogConductor.bParts` 非空：

```java
this.bParts.add(new FormPart("c", encodeValue(jsonString)));
this.bParts.add(new FormPart("b", encodeValue(this.bStr)));
this.bParts.add(new FormPart("v", encodeValue(strVersion)));
for (Map.Entry<String,String> e : this.appendParams.entrySet())
    this.bParts.add(new FormPart(e.getKey(), e.getValue()));
this.bParts.add(new FormPart("ext", encodeValue(string)));
// → setDataType(DATA_TYPE_TRANS)
```

`qunar.lego.utils.FormPart` / `MultipartEntity` 负责实际编码，
`PitcherRequest.getMultipartContent()` 用 `ByteArrayOutputStream` +
`multipartEntity.writeTo(out)` 得到字节数组。

### 1.2 静态可见的上传目标

| 常量 / 端点 | 用途 |
|---|---|
| `ReleaseEnvironment.hotelUploadPicUrl` = `http://ud.client.qunar.com/ud` | 图片上传通道 |
| `PitcherManager` 代理 + `trans` 正文 | 业务表单/文件字段上传 |
| `com.qunar.rc.service.net.Request`（multipart 单字段 `b`） | 设备指纹上报（非用户文件） |
| `https://jr.qunar.com/m/quser/upDevice.do` | 金融设备信息上报（JSON 体） |

### 1.3 数据范围判断

- 用户可见的内容上传（图片、证件、酒店点评图等）都经 `ud.client.qunar.com`
  或业务 `trans` 通道，正文受 `Goblin.sand` + zlib 包裹，但**不是端到端加密**：
  客户端持有全部算法与密钥材料，服务端才是唯一可信边界。
- 设备指纹上报是独立的明文 multipart（仅字段名 `b` 固定），
  加密发生在字段值层面（见 `risk.md` §5），不在传输层。
- 本次未上传任何文件、未触发任何上传链路。

## 2. 下载

### 2.1 安装包内自带

`assets/*.qp` 23 个，合计 `85,564,564` 字节。每个 `.qp` 是
`"qp" + u32le 清单长度 + JSON 清单 + 明文 JS 正文`，正文是 Metro 打包产物。
这意味着主要业务前端代码**随安装包明文分发**，可直接静态提取（本报告未发布
任何正文内容）。

### 2.2 运行期增量

清单 `files[]` 每项给出 `url`（`https://rn.qunar.com/packages/<hybridid>/...`、
`https://s.qunarzz.com/...`）、`md5`、`sha256`（Base64 形态）与
`sl:"start,length"`。客户端下载后按切片替换，用于热更新；`timestamp`、
`version`、`android_vid` 决定版本选择。

### 2.3 其它下载面

| 类别 | 端点族 |
|---|---|
| 静态资源与图片 | `s.qunarzz.com`、`qimgs.qunarzz.com`、`picbed.qunarzz.com`、`source.qunarzz.com` |
| 数据库/公共数据下发 | `p_updateHolidays`、`h_updateHotelCity`、`p_updateHotCity`、`p_prenumList`、`p_updateHolidays` 等 `p_*` / `h_*` 功能键 |
| 热修/升级 | `p_hupgrade`、`HotFixInfoManager`、`libdiffpatch.so` |
| 图片缓存 | `MercuryCacheDB`（`MercuryDownloadEntity` / `UploadEntity` / `CrashEntity`） |

### 2.4 数据范围判断

- 下载内容为**公共业务数据**（城市库、节假日、离线包、图片），不含账号私有
  文件；账号相关数据走 Hotdog 隧道响应体。
- 离线包更新无独立签名层，只靠清单里的 `md5`/`sha256` 与 `version` 比对；
  这属于完整性校验而非来源认证，若清单本身可被替换则校验一并失效。此条为
  从代码结构得出的**结构已证实**结论，未做实测。

## 3. 静态范围结论

| 判断 | 结论 |
|---|---|
| 是否上传联系人 | 未见调用闭环；`READ_CONTACTS` 只有声明（`permissions.md` §4） |
| 是否上传相册全量 | 未见；仅用户选择图片经 `ud.client.qunar.com` |
| 是否上传安装应用列表 | **是**，`ext.ai()` 与 `AppListInfoCollector` 上报包名/版本/首装时间/`sourceDir`/`flags`/`uid`（`privacy.md` §3） |
| 是否上传位置 | 条件性：`cpHasPrivacyInfo` 在拿到缓存定位时写入 `lat`/`lgt`；另有 `qpub_locationUpload` 功能键 |
| 是否上传 TLS 证书链与出口 IP | **是**，`d.getcc()`（`risk.md` §5.3） |
| 是否上传外部存储目录列表 | **是**，`Cmd.a()` 执行 `ls -al /sdcard/Android/data/ | head -n 50` |

所有判断均为静态调用点结论，本次未产生任何真实传输。
