# 协议格式

## 1. HTTP 请求信封

### 1.1 公共 query/body 参数

`DefaultRequestInterceptor.addCommonParam` 写入：

```text
platform=android
mobi_app=<配置的平台标识>
appkey=<由 native appkey 表得到>
build=<versionCode>
channel=<渠道>
access_key=<登录态，可为空>
c_locale=<当前语言>
s_locale=<系统语言>
```

`AuthInterceptor.addCommonParam` 还加入 `buvid`、`local_id`。旧拦截器允许
`netPublicParamFactory` 整体替换公共参数，但字段仍是键值字符串。

### 1.2 签名串

`SignedQuery.r(map)` 的可重放结构：

1. 若输入不是 `SortedMap`，先转 `TreeMap`；
2. 跳过空 key，value 为 `null` 时视为空串；
3. key/value 使用 `SignedQuery.b` 百分号编码，保留 `A-Za-z0-9-_.~`；
4. 以 `&` 连接 `encodedKey=encodedValue`，去掉最后一个 `&`；
5. native 计算 MD5 并返回 `SignedQuery{rawParams, sign}`；
6. `toString()` 输出 `<rawParams>&sign=<32位小写hex>`。

### 1.3 JSON 与表单

- 普通 JSON API：`{"code":0,"message":"0","data":{...}}`。
- 表单 POST：`application/x-www-form-urlencoded`，签名覆盖业务参数 + 公共参数。
- 文件上传：`multipart/form-data`，不把 multipart body 当普通 query 签名。
- 风控上报：见第 4 节。

## 2. gRPC/protobuf

protobuf message 类保留字段号和类型。例如播放响应可包含：

```text
PlayViewReply
  ├─ play_info / play_limit / play_strategy
  ├─ dash / durl / backup_url
  ├─ dash_drm_type
  ├─ widevine_pssh
  └─ bilidrm_uri / credential
```

弹幕使用 `KDmSegMobileReply` 分段结构，评论与互动使用各自 Moss service；同一功能
可同时有 REST 与 gRPC 路径，客户端按入口选择，不是两套独立协议。

## 3. 风控加密报文

`BuildBody` 生成的 JSON 外层为：

```json
{
  "header": {
    "encode_type": 2,
    "payload_type": 2,
    "encoded_aes_key": "<RSA(Base64)>",
    "ts": 1700000000000,
    "encoded_version": "<配置版本>"
  },
  "encrypt_payload": "<Base64 或大写hex>"
}
```

`PostBodyModel.toJsonString()` 再把密钥包装为 `{"key":...,"content":...}`。
CongLing/KunLun 接口以 `application/json` POST，查询参数带 `access_key`；
`ExBadBasket` 以 `application/octet-stream` POST。

## 4. 位图风控字段

`RiskCollect` 把 36 个 collector 的布尔结果按每 60 位分组，每组先计算命中数与
parity 标志，再转十进制；字段名经 `g.java` 映射为 4 位十六进制短码。`gripper-fieldmap`
共有 121 项，覆盖：

- 硬件标识：imei、imsi、iccid、mac、bssid、oaid/aaid/vaid、adid/idfa/idfv、btmac；
- 位置/网络：ip、gps_info、Wi-Fi SSID、代理、VPN；
- 环境：root、emu、virtual、adb、debug、bootloader、ROM、传感器、内存、屏幕；
- 应用面：已安装应用位图、系统应用数量、accessibility/input method 等。

短码是压缩/混淆编码，不是加密；字段原名与判定逻辑在 APK 中可直接恢复。

## 5. 缓存/离线数据格式

`video_upload` 的 DDL 含 taskid、file_path、file_length、chunk_list、
uploaded_chunk_bytes、auth、upos_uri、endpoint_list、upload_url_list、chunk_size、
upload_id、key、bucket、profile、meta_url、ip、错误信息等；`video_info` 含
aid/cid/season/episode、qn_path、downloaded_size、storage_path、auth_code 和
sectionsDownloadedList。数据库为普通 SQLite，未见整库加密。
