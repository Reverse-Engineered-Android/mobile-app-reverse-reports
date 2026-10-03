# 上传与下载的数据范围

## 1. 结论

静态可见的传输分成五类：通用文件/图片、用户资料、支付资料、设备画像和业务
遥测。通用文件接口只上传调用方传入的 multipart body；设备画像的 Java 调用面
明确，但密文内字段由 native 生成，报告不把不可见字段写成明文。

## 2. 对象存储上传

### 2.1 通用文件

`DefaultUploadFileRetrofitService`：

```java
@POST("extrastorage/new/{bucket}")
@Multipart
Call<DefaultUploadFileResponse> upload(
    @Path("bucket") String bucket,
    @Header("token") String token,
    @Header("client-id") String clientId,
    @Part MultipartBody.Part part);
```

另有 `@Query("type")` 版本；旧桶路径为
`POST extrastorage/{bucket}`，鉴权头是 `time` 与
`Authorization`。

签发凭据的静态调用：

```java
getSecureToken(url, clientId, clientSecret, url, maxAge)
getVenusToken(url, bucket, clientId, clientSecret)
```

上传数据范围：

- 实际文件字节（part body）；
- bucket、token/client-id 或 time/Authorization；
- 可选 type；
- 不在接口签名中的业务字段由 multipart builder 追加，因此仅凭 interface
  不应宣称“只有文件”。

### 2.2 图片/地图反馈

`BridgeImageRetrofitService` 与 `VenusImgUpLoadApi` 提供：

- `PUT @Url`：只接收单个 `MultipartBody.Part`；
- `POST extrastorage/new/{bucket}`：token/client-id + part；
- `POST extrastorage/{bucket}`：time/Authorization + part；
- 地图 `FacadeAPI` 的 `postFeedback` / `postMapFeedback` 为
  `@PartMap Map<String, RequestBody>`，字段由调用方组装。

### 2.3 MRN/影视图片

`MRNApiRetrofitService`、`UploadFileService` 等接口使用 `@Multipart`
上传页面或影视业务产生的文件；接口方法签名说明存在文件 part，具体业务字段
属于各调用方。

## 3. 账户与支付上传

| 场景 | 接口 | 数据范围 |
|---|---|---|
| 用户头像 | `POST /user/settings` | `PartMap` + 两个 `MultipartBody.Part` |
| 银行卡图片 | `POST /hellopay/uploadcardimg` | `@FieldMap` + `nb_fingerprint` |
| Soter 指纹公钥 | `POST {path}` | 两个 `@Encrypt @FieldMap` + `nb_fingerprint` |
| 指纹验证 | `POST /qdbverify/publicverify` | merchantNo, verifyNo, orderNo, scene, risk_partnerid；加密的 verify_type, challenge, finger_type, extra |
| 支付密码验证 | `POST /qdbverify/publicverify` | 同上；加密的 pay_password |
| 数字证书结果 | `POST /qdbverify/digicertdownloadresultnotify` | cert_download_status, cert_serial_no, verify_types |

头像/银行卡图片是用户主动选择的文件；`nb_fingerprint` 是支付设备指纹。
报告没有读取任何真实图片、身份证件、银行卡、支付密码或指纹数据。

## 4. 设备指纹上传

入口：

```java
MTGuard.uploadDeviceInfo(provider, callback, force)
MTGuard.deviceFingerprintData(provider)
MTGuard.deviceFingerprintID(callback)
MTGuard.upload(callback)
ShellBridge.main3(103, {callback});
ShellBridge.main3(110, {callback});
```

调用方包括 Yoda、WMS、MGC、Hades、Waimai。DFP worker 至少处理：

- 应用/包信息：`AppInfoWorker`、`InstalledAppManager`；
- 环境信息：`EnvInfoWorker`；
- 厂商设备标识：OAID、ASUS、Lenovo、Meizu、Mi Safety、Nubia、Vivo、
  Xiaomi、ZTE helper；
- 存储键：`dfp_id`、`xid_id`、`dfp_local_id`、`dfp_imei`、
  `dfp_flt`、`dfp_lastB`、过期/上报间隔。

传输端点和 content type 见 [network.md](network.md) §4。Java 侧只把 native
byte[] 发出；因此能证明的范围是“存在设备/环境/厂商 ID 采集入口并可上报”，
不能仅从 DEX 证明每一条明文键都被上传。

## 5. Yoda 行为数据

`com.meituan.android.yoda.model.behavior.collection.b` 将以下内容放入
`HashMap<String,String>`：

- `sT` 起始时间；
- `bI`、`brR` 行为/轨迹摘要；
- `aT`、`kT`、`tT`、`gT` 数组字符串；
- `cts` 当前时间；
- `sign` 对 map 的签名；
- `isEmu/isRoot/hasMalware/isDarkSystem/isVirtualLocation/isRemoteCall/
  isSigCheckOK/inSandBox/isHook/isDebug/isProxy/isCameraHack`。

这些字段属于行为验证/登录风控输入，不是普通内容上传；是否全部发送取决于
Yoda challenge 和调用场景。

## 6. 遥测与日志上传

静态接口包括：

- `POST https://promotionapi.sankuai.com/content/collect`，JSON map；
- `POST https://optimus-mtsi.meituan.com/mtsi-worker/{business}`，JSON；
- `POST /api/app-aggregation/request?project=...&appVersion=...&uuid=...`；
- crash/ANR、Raptor、Laggy、Elsa、Mercury 的上报接口；
- push token/feedback 接口。

可见的聚合字段包括 project、appVersion、uuid、traceId、moduleId、request/
response length 和耗时；具体业务 payload 由事件对象序列化。

## 7. 下载范围

| 类别 | 静态证据 | 数据 |
|---|---|---|
| 图片 | `BridgeImageRetrofitService.download(@Url)` | `ResponseBody` |
| 地图/POI | `@Url`、CDN 请求 | JSON、图片、瓦片 |
| MRN/动态组件 | React Native/Mercury/AB 配置 | bundle、manifest、配置 |
| 应用升级 | `UpgradeManager` | APK/增量包、更新元数据 |
| 媒体 | 图片/视频/音频播放器 | 与当前页面 URL 绑定的媒体字节 |
| 评论/评价 | `ResponseBody` / JSON | 文本、评分、图片 URL |

下载 URL 可以是服务端返回或页面动态构造；本报告没有实际下载账号数据、支付
资料或私有评价。

## 8. 数据范围判断

- **明确会主动发送**：用户触发的 multipart 文件、支付指纹、Yoda 行为 map、
  DFP 配置允许的设备信息；
- **条件发送**：相机/相册图片、位置查询、联系人/日历选择、登录 token；
- **仅本地处理或未证实上传**：静态解析到的大部分日志字段和敏感权限读取；
- **服务端不可见**：上传后保存期限、用途、二次分发。
