# 上传与下载的数据范围

## 1. 结论

静态可见的数据传输分为四类：实名核验材料、账户与评论资料、遥测/资源更新、
下载资源。实名核验是范围最大的上行链路，包含人脸照片、动作照片、活体视频、
传感器动作日志、身份证核验参数和生物特征算法结果；其他上传链路的范围由调用方
传入的文件或 token 字段决定。本节没有发起任何上传、下载、登录或购票请求。

## 2. 实名核验（RP）上行

### 2.1 协议入口

`com.alibaba.security.realidentity.biz.uploadresult.UploadResultRequest.apiName()`
返回：

```java
return "mtop.verifycenter.rp.upload";
```

同一组件还静态声明：

| 用途 | MTOP API |
|---|---|
| 获取无线配置 | `mtop.verifycenter.rp.getwirelessconf` |
| 开始核验 | `mtop.verifycenter.rp.start` |
| 生物特征事件 | `mtop.verifycenter.rp.event` / `mtop.verifycenter.rp.event.sessionless` |
| 上传结果 | `mtop.verifycenter.rp.upload` |
| 提交核验 | `mtop.verifycenter.rp.submit` |
| 日志 | `mtop.verifycenter.rp.log` |

请求构造见 `UploadResultParams.a()`（`payload/classes3.dex`，方法行
99-167），其中 `UploadResultRequest` 的 head 参数是
`verifyToken` 与 `clientInfo` JSON。

### 2.2 `Data` 的精确字段

`UploadResultRequest.Data`（`payload/classes24.dex`，类定义行 27-106）
通过 `@JSONField` 映射出下列字段：

| JSON 字段 | Java 字段/含义 |
|---|---|
| `bigImage` | `bigImagePath`，大图路径 |
| `localImage` | `bigSmallLocalPath`，本地小图路径 |
| `refImage` | `bigSmallPath`，参考图路径 |
| `originalImage` | 原始图像路径 |
| `data` | 业务数据，可携带彩色视频的加密结果 |
| `faceRect` | 人脸矩形 `x,y,w,h` |
| `processDetail` | `ALBiometricsResult.toJson()` 完整处理结果 |
| `recognizeResultScore` | 本地识别分数 |
| `K_FACE_R_ENABLE` | 本地识别开关 |
| `isGaze` | 是否完成注视检测 |
| `useCtid` | 是否调用 CTID 身份证核验 |
| `idCardAuthData` | CTID 参数 |
| `sensorActionLog` | 传感器动作日志 |
| `flActionLog` | 人脸动作日志 |
| `wukong` | 悟空风控数据 |
| `backgroundDetectResult` | 背景检测结果 |
| `movement_1` ... `movement_8` | 每项 `{image_1,type}` |
| `smallImageMode`、`v` | 小图模式和协议版本 |

动作类型由 `UploadResultParams.a(int)`（行 182-184）映射：

```text
1=Blink, 2=OpenMouth, 3=ShakeHead, 10=RaiseHeadDown, 11=KeepStill
```

`UploadResultParams.a()` 的构造证据：

```java
elements.name = "Biometric";
data.bigImagePath = a(this.i, "bigImage");
data.idCardAuthData = ...getCtidParams();
data.useCtid = ...isCtidCalled();
actionType.image_1 = a(this.i, a.d("action", i));
data.faceRect = String.format("%d,%d,%d,%d", ...);
data.backgroundDetectResult = bv4.b(edgeDetectResult);
data.processDetail = this.h.toJson();
```

### 2.3 动作日志与视频

`UploadResultParams.f()`（行 58-73）额外构造 `Elements`：

```java
elements.name = "RISK_ACTION";
data.sensorActionLog = this.h.getCollectedData();
data.flActionLog = this.h.getBh();
data.wukong = this.h.getWukongData();
```

若存在彩虹/活体视频，`a()` 行 147-163 追加：

```java
elements2.name = "BIOMETRIC_COLORFUL";
aVar.url = this.h.getDazzleVideoOssUrl();
aVar.videoHash = this.h.getVideoHash();
aVar.videoExtra.conf = this.h.getDazzleDataConfigs();
data2.data = kl1.a(c25Var.makeResult(
    strB.getBytes(), String.valueOf(System.currentTimeMillis()), umidToken));
```

`kl1.a(byte[])` 只是 `Base64.encodeToString(..., 2)`；`makeResult`
是 `com.alibaba.security.realidentity.algo.jni.CommonUtilJni.makeResult(byte[], String, String)`
的 native 调用，输出经 Base64 后放入 `data`，其密钥由 SecurityGuard
组件按 `umidToken`/时间参数派生，报告不提取密钥。

### 2.4 图片/视频对象存储上传

`tb.wt4` 实现 ARUP 上传任务：

```java
getBizType()  -> "rp_asset"
getFilePath() -> UploadFileModel.getLocalFilePath()
getFileType() -> UploadFileModel.getFileType()
getMetaInfo() -> {arup-directory, arup-file-name}
```

`wt4.a(...)` 的 ASM 残留体（行 69-159）仍可读出完整调用链：
读取 `File` 长度，构造 `{fileName,fileType,fileLength}`，调用
`TrackLog.createOssUploadFileBeginLog`，然后
`IUploaderManager.uploadAsync(task, listener, cancel)`。成功结果读取
`x-arup-biz-ret` 中的 `ossBucketName` / `ossObjectKey`，回调
`oss://bucket:object`；失败上报 `oss upload failed`。

`UploadFileConfigParams` 的可序列化字段为：

```text
mBucket, mContentType, mEndPoint, mExpired, mKey, mPath, mSecret, mToken
```

`UploadFileModel` 的字段为：

```text
mDestDir, mFileType, mLocalFilePath, mRemoteFileName
```

`UploadFileConfigParams` 在 `UploadApi` 的一条调用中传入 `null`，
因此实际 ARUP 凭据来自 uploader SDK 的运行时配置，而不是从该参数
复制到请求体；静态无法证明 bucket 名、对象 key 或有效时间。

`tb.gx4` 另有活体视频上传：

```java
destDir = "biometric/video/<timestamp>/<callback>/<success|failure>"
fileType = "h264"
localFilePath = 本地视频路径
remoteFileName = new File(path).getName()
```

上传成功后把 OSS URL 写入 `ALBiometricsResult`，并计算
`videoHash`；失败/取消删除本地视频。

## 3. 其他上行数据

| 业务 | 静态入口 | 数据范围 |
|---|---|---|
| 头像 | `mtop.damai.wireless.user.uploadHeadImg` | 调用方选择的头像文件/图片参数 |
| 评论 | `mtop.damai.wireless.comment.publish` | 评论文本、图片/视频引用和业务 ID |
| 登录/推送 token | `mtop.damai.mc.push.token.uploadnew` | push token 与设备会话参数 |
| AB 实验 | `mtop.film.AbAPI.uploadAb` | AB 实验结果字段 |
| 实时日志 | `mtop.alibaba.emas.publish.update.resource.upload` | EMAS 资源日志；`.update.get` / `.update.escort.get` 为对应读取接口 |
| 用户资料 | `mtop.damai.wireless.user.customer.update` | 客户资料字段 |
| 关注关系 | `mtop.damai.wireless.follow.relation.update.batch` 等 | 关注/取消关注批量关系 |

MTOP 公共参数、`x-netinfo`（SSID/BSSID）、`x-location`（经纬度）和
页面追踪字段的发送条件见 [network.md](network.md)；这里不重复其范围判断。

## 4. 下载范围

### 4.1 静态资源

`cn.damai.h5container.WindvaneAgent` 声明：

```text
https://androiddownload.damai.cn/uc/
https://androiddownload.damai.cn/uc/release/arm64-v8a/libkernelu4_zip_uc_276.so
https://androiddownload.damai.cn/uc/release/armeabi-v7a/libkernelu4_zip_uc_276.so
```

源码还引用同域图片、Lottie ZIP 和票夹默认图。下载内容是公开静态资源，
不包含用户数据。

### 4.2 动态包与升级

`com.taobao.update.datasource.mtop.UpdateRequest`：

```java
API_NAME = "mtop.client.mudp.update";
// outer 环境为 "mtop.client.mudp.update.outer"
```

`com.taobao.update.apk.MainUpdateData.getDownloadUrl()` 提供 APK 下载
URL；`com.taobao.update.apk.a` 把该 URL 交给 `ge2.doUpdate(...)`。
安装入口使用 `com.taobao.update.provider.UpdateProvider`（manifest 中
`FileProvider` 风格 provider），并声明
`REQUEST_INSTALL_PACKAGES`。这属于更新能力，不构成系统 UID 提权；
本报告没有触发安装。

### 4.3 本地落盘

只读设备检查显示：

| 文件 | 作用/实际 schema |
|---|---|
| `ticketlet.db` | 新安装只有 `android_metadata`，没有票夹行 |
| `data_cache.db` | `data_cache(_id,type,content,timestamp,expire,ret_code,ret_msg,channel)` |
| `accs.db` | `traffic(_id,date,host,serviceid,bid,isbackground,size)` |
| `message_accs_db` | `message(...)`、`accs_message(...)` 消息队列 |
| `ut.db` | `ap_alarm/ap_stat/ap_counter/utap_system/onlineconfig/log/stat_register_temp` 等统计表 |
| `yk_gaiax.db` | `yk_template_v2`、`yk_assets_template_v1`、`room_master_table` |
| `dm_local_kv_data.xml` | `bx_config`、`click_bx`、`pictures_device_level_score`、推荐和 CMS 缓存键 |

设备数据库和 SharedPreferences 仅用于只读验证；没有上传它们的行值。

## 5. 图像/SVG 下载后解密

### 5.1 Java 合约

`com.real.image.decrypt.ImageDecrypt`：

```java
a(safeKey, bytes) -> nativeDecryptImage(bytes, safeKey, "aes128-ctr")
b(safeKey, timeStamp) -> nativeDecryptKey(safeKey, timeStamp, "aes128-ecb")
c(expectedHash, bytes) -> MD5(bytes) 忽略大小写比较
```

`com.real.svg.decrypt.SvgDecrypt`：

```java
a(str, ts) -> nativeDecryptKey(str, ts, "aes128-ecb")
b(str, key) -> nativeDecryptSvg(str, key)
c(expected, input) -> MD5(input) 比较
```

座位 VR 数据的调用点 `tb.bm3.a()`（行 40-83）顺序为：

```text
derive key -> verify imgKeyHash -> decrypt bytes -> verify image MD5 -> decode bitmap
```

### 5.2 native 闭包

`libimage_decrypt.so` 和 `libsvg_decrypt.so` 保留 C++ JNI 符号，
`JNI_OnLoad` 位于 `0x3458`；导出/PLT 符号可直接互相对应：

| 地址 | 符号/用途 |
|---:|---|
| `0x2dc0` | `nativeDecryptKey(JNIEnv*, jclass, jstring, jstring, jstring)` |
| `0x2ffc` | `nativeDecryptSvg(...)` |
| `0x3240` | `nativeDecryptImage(...)` |
| `0x29c4` | `buildKeyKey` |
| `0x3b80` | `str_decrypt` |
| `0x481c` | `svg_decrypt` |
| `0x6d14` | `AES_CBC_decrypt_buffer` |
| `0x6158` | `AES_ECB_encrypt_buffer` |
| `0x656c` | `AES_ECB_decrypt_buffer` |
| `0x6ea4` | `AES_CTR_xcrypt_buffer` |
| `0xa05c` / `0xa1a0` | `SM4_set_key` / `SM4_cbc_decrypt` |
| `0x98e8` | `SM3_Init` |

`nativeDecryptImage` 的 JNI 指针表调用依次是
`GetStringUTFChars`（index 169）、`jstr2str`、`GetArrayLength`（171）、
`GetByteArrayRegion`（200）、`img_decrypt`、`NewByteArray`（176）、
`SetByteArrayRegion`（208）；算法字符串为 `.rodata` 中的
`aes128-ctr` / `aes128-ecb`。`buildKeyKey` 读取 `.data` 中的 SSO
字符串常量、追加 `.rodata+0xdc79` 的一个字节和调用方 `timeStamp`，
再进入 `str_decrypt`；`str_decrypt` 的调用目标是 SHA-256、SM3、SM4、
AES-CBC/ECB/CTR 和 Base64。密钥不是 APK 中可直接读取的固定值，
且 `nativeDecryptKey` 成功才经 `NewStringUTF` 返回。

`libtb_crypto.so` 是 BoringSSL（AES-CBC/CFB/CTR/ECB/GCM/XTS、RC4、RSA、
SHA、HMAC），源码路径字符串指向 `taobao-cxx/boringssl`；它是加密库
实现，不是未知的业务协议。

### 5.3 结论

图像、SVG、VR 座位和 SecurityGuard 动态数据的加密用途已经按
“Java 调用点 → JNI 符号 → native 算法 → 输入输出字段”闭合；
没有保留“待逆向”的加密函数。未复原的服务端密钥和评分结果不冒充
客户端可读事实。

## 6. 静态调查边界

搜索、活动、购票和票夹的请求只从请求类与字面量枚举；没有真实账号、
真实搜索词、真实订单或票夹查询，没有网络流量抓取，也没有构造绕过
风控的请求。上传下载的“范围”因此分为代码能证明的字段范围和服务端
实际接收/留存不可见两层。
