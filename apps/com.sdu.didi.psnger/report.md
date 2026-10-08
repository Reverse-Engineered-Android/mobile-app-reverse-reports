# 滴滴出行 Android 8.0.14 逆向研究报告

## 1. 最终结论

### 1.1 样本

| 项目 | 值 |
|---|---|
| 包名 | `com.sdu.didi.psnger` |
| versionName / versionCode | `8.0.14` / `1208001404` |
| minSdk / targetSdk | `24` / `35` |
| APK SHA-256 | `e1b3752697f16fcad51688ce44b1a8dc5f336ab8569f226da0548df4059ab02e` |
| APK 字节数 | `76,989,224` |
| DEX 数量 | 8（`classes.dex`…`classes8.dex`） |
| APK 条目 | 10,931（资源 9,412、assets 1,168） |
| ARM64 native 库 | 89 |
| 声明权限 | 65 个（40 个 `android.permission` + 25 个非 `android.permission` 条目） |
| 显式导出组件 | 72（其中 62 个未加 `android:permission` 约束） |
| JADX 反编译产物 | 51,193 个 `.java` |
| JADX 残留方法 | 47 文件 / 52 个唯一签名，全部归类 |

APK 条目时间戳为 2026-09-16，设备安装时间 2026-09-24。JADX 以
`--show-bad-code` 运行后仍有 52 个 `Method not decompiled` 签名，全部 52 个已
逐项归类（[evidence.md](evidence.md) §5）：其中 34 个是 Kotlin 协程/`AnonymousClass`
状态机、6 个是 ASM/OSGi/WorkManager 框架方法、4 个是 Retrofit/Glide/计时器回调、
其余为地图 SDK 与生物活体回调。**没有一个是密码学实现**，也没有任何一个落在
`com/didi/security`、`com/didi/safety/onesdk`、`com/didichuxing/dfbasesdk` 或
`f32`/`x22`/`e81` 加密包内。

### 1.2 网络与协议

应用只有一条 HTTP 出口：`com.didichuxing.foundation.net.rpc.http.OkHttpRpc`，
所有业务接口（Retrofit 风格 `@o("/path")` 注解接口 + Rabbit/Cronet 传输）都汇入
同一拦截器族，优先级由 `@i42.a(priority=…)` 决定：

| 优先级 | 拦截器 | 作用 |
|---:|---|---|
| -990 | `com/didi/security/wireless/adapter/AuthInterceptor` | `secdd-authentication` |
| 999 | `com/didi/security/wireless/adapter/SignInterceptor` | `wsgsig` |
| 990 | `com/didichuxing/security/challenge/DiChallengeInterceptor` | WAF 522 挑战 |
| — | `com/didi/sdk/net/NewHeaderContentInterceptor` | `didi-header-hint-content` |
| — | `com/didi/sdk/net/ParameterInterceptor` | 公共参数合并 |
| — | `com/didichuxing/dfbasesdk/http/SecurityAccessWsgInterceptor` | `wsgenv` |

`didi-header-hint-content` 是一段 JSON，由
`com/didi/sdk/net/interceptor/NewHeaderContentInterceptor.java:119` 读出后按
白名单保留字段，写入 `lang`、`locale`、`call_id`、`app_timeout_ms`、`Cityid`、
`location_cityid`、`utc_offset`、`currency`，再补 `xregionkeyname`/`xregionkeyvalue`
与 `TripCountry`。`com/didi/sdk/net/CommonParamsInterceptor.java` 追加
`terminal_id=11` 与 `1` 两个查询参数；`com/didi/sdk/net/ParameterInterceptor.java`
把公共参数 map 合并进 GET query、JSON POST body 或 `walletParam` 查询 JSON。

静态扫描出 43 个不同的 `addHeader` 头名与 211 个主机。业务主机集中在
`api.diditaxi.com.cn`、`api.udache.com`、`common.diditaxi.com.cn`、
`conf.diditaxi.com.cn`、`pay.diditaxi.com.cn`、`llab-asst.xiaojukeji.com`、
`mapi.xiaojukeji.com`，静态资源在 `didistatic.com`/`dpubstatic.udache.com` CDN。

打车下单链路（静态）：

```text
onetravel://  deeplink
   -> NextNavigationInterceptor（dache_anycar/flash/unitaxi/dache/…）
   -> /entrance_v2 -> /scenehome -> /confirm
   -> QUCreateOrderInteractor.createOrderWithConfig(config)
   -> pNewOrder / anycarNewOrder
   -> pOrderMatch / pOrderStatus / pOrderDetail
   -> /inservice -> /endservice
```

`com/didi/quattro/common/net/RegionInterceptor.java:28` 给出区域白名单
`pMultiEstimatePrice,V2,V3,pNewOrder,pCancelOrder,pCancelTrip,anycarNewOrder`；
`com/didi/carhailing/framework/net/k.java:10` 给出订单接口名集合
`{pNewOrder, pCancelOrder, pOrderStatus, pOrderDetail, pGetOrderMatchInfo, pOrderMatch}`。
估价与订单的 HTTP 路径分布在 `/gulfstream/mamba/v1/…`、
`/gulfstream/pre-sale/v1/core/pMultiEstimatePriceV3`、
`/gulfstream/transaction/v2/other/pOrderMatch`、`/gulfstream/porsche/v1/…` 与
`/intercity/ticket/api/v1/other/pOrderMatch`。

### 1.3 认证

认证不是一个 token，而是三层并存：

1. **设备级** `secdd-authentication`。`AuthInterceptor.java:19,32,33,136` 从
   SharedPreferences `authToken` 读 `authToken`，为空时退化为
   `System.currentTimeMillis()/1000`，并在响应中刷新回写。
2. **账号级** `token`。`com/didi/carhailing/net/ApiBaseRepository.java:103,156`
   与 `com/didi/carhailing/framework/net/HttpParams.java:113` 执行
   `map.put("token", su1.p.f110463b.getToken())`；`su1/e` 是登录态接口，暴露
   `getToken/getUid/getPhone/a()`。
3. **请求级** `wsgsig`。见 §1.4。

登录面由 `/passport/login/v5/*` 一族接口构成，共有 49 个唯一路径字面量，覆盖
`signInByCode`、`signInByPassword`、`signInByFace`、`signByAuth`、`refreshTicket`、
`validateTicket`、`getCaptcha`、`verifyCaptcha`、`verifySlider`、`codeMT`、
`gatekeeper`、`generateQRCode`、`queryQRCodeStatus`、`confirmQRCodeLogin`、
`signOff`、`deleteAccount` 与 `mfa/*`。`LoginNetInterceptor` 对 host 含
`passport` 的请求打印请求与响应，但把 `uid`、`ticket`、`password`、
`new_password` 字段统一掩码为 `"didi"`。

### 1.4 风控

风控由五个相互独立的机制组成，全部给出精确判定代码（详见 [risk.md](risk.md)）：

**（1）请求签名 `wsgsig`** —
`com/didi/security/wireless/SecurityManager.java:477-496 prepareSign`：

```java
String query = getQuery(str);
byte[] body = getBody(bArr);            // body 截断到前 4096 字节
Map<String,String> m = queryStringToMap(query);
String hex = bytesToHex(body);          // 小写十六进制
if (!TextUtils.isEmpty(hex)) m.put(hex, "");
return signMapToString(m);
```

`queryStringToMap`（:498-514）把每个 query 对解码后拼成 `key+value` 作为 map
键；`signMapToString`（:592-607）用 `Collections.reverseOrder()` 排序后拼接，
跳过以 `__x_` 开头的键与 `wsgsig` 本身。签名结果由
`SecurityManager.java:712-720` 分派：`SecurityLib.nativeIsDD06(context)==1` 走
`dd04Sign`，否则走 `SecurityLib.sign`；两者都在 `libdidiwsg.so`
（sha256 `9360e325…a066`）内。`checkSign`（:128-138）要求结果只含
`[A-Za-z0-9+/-]`，失败时 `errSign` 返回 `dd02-<base64(json{vc,vn,ec})>`。

**（2）环境采集 `wsgenv`** —
`SecurityWrapper.a(bizType, url)` → `SecurityManager.collect(bizType, url)`
（:643-649）→ `SecurityLib.nativeCollect(host+path)`，结果作为查询参数
`wsgenv=` 由 `com/didichuxing/dfbasesdk/http/SecurityAccessWsgInterceptor.java:38`
附加。`collectBluetoothAsync`（:144-146）经 `BtDelegate.asyncCollect` 单独采集
蓝牙环境。

**（3）WAF 挑战** —
`com/didichuxing/security/challenge/DiChallengeInterceptor.java:22-23`，开关键
`sec_close_challenge_toggle`。`challenge/a.java:62,66,92,338`：请求已带
`secdd-challenge` 或哨兵 `hbGxlbmdlZGVtbyIsInZj` 时跳过；命中 HTTP **522**
时解析 WAF 返回的 JS，交由 `ClgJsExecutor`（优先 QuickJS，回退 WebView）执行，
再用 `secdd-challenge` 重发，并读取响应头 `set-secch-sessionid`。

**（4）设备指纹 `x-ddfp`** —
`com/didi/didipay/pay/net/DidipayHeadersInterception.java:67-68` 把
`e82.f.i(context)` 的结果写入 `x-ddfp`，与 `Authorization` 一起用于支付域请求。

**（5）设备画像字段** —
`com/didichuxing/security/safecollector/DeviceInfoNameEnum.java:4-34` 枚举 31 个
字段：`appName, packageName, appVersionCode/Name/Issue, osVersion, model, brand,
cpu, cpuSerialNo, pixels, screenHeight/Width, totalSpace, totalDisk, isRoot,
customId, screenSize, emulatorType, utcOffset, countryCode, locale, mcc, mnc,
networkOperator, simCarrier, networkType, localIp, batteryLevel, phoneTime,
isDebug`。其中 `cpuSerialNo`、`isRoot`、`emulatorType`、`isDebug` 属于风控专用
信号，与打车功能本身无关。

### 1.5 上传与下载

上传分五类（详见 [transfer.md](transfer.md)）：

| 类别 | 端点/字段 | 判定 |
|---|---|---|
| 通用文件 | `MultipartBody` `file` part | 调用方决定 |
| 日志分片 | `/catch/log/slice_upload`，`taskid/sliceid/sliceAt/file/os/api/ts/appname/filelength/sdk_ver` | 固定协议 |
| 一键报警 | `https://poi.map.xiaojukeji.com` `trafficevent`，`piccontent/photocontent/audiocontent/speechcontent/debugcontent` | 用户主动 |
| 设备连接上报 | `/api/guard/deviceConnect/reportV2`，Base64 包裹 `{"body":…}` | 自动 |
| 轨迹上报 | `track_upload_sdk2.db` → protobuf `TrackUploadReq` | 条件触发 |

下载侧包括 `didi_onedownload.db`（`download_history`/`download_log`）、
`download_file.db`（`tb_download_file`，含 `e_tag`、`accept_range_type` 断点续传
字段）、`didi_speech_download.db`、DRN/Hummer 资源包
（`/bundle/api/pre/query`、`/bundle/api/batch/query`、`/bundle/api/single/query`、
`/bundle/api/recommend/download`）以及 CDN 静态资源。

### 1.6 越权、提权与超范围

- **未发现系统 UID 提权闭环。** 全量 22 个 `Runtime.getRuntime().exec` 调用点中
  没有任何一处执行 `su` 或 `pm grant`。存在一个 `sh` 命令代理
  （`com/megvii/lv5/v8.java:46`，把字符串写入 `sh` 的标准输入），但其唯一调用者
  `com/megvii/lv5/w8.java:47,48,68,139,155,186` 全部传入编译期字面量
  （`which su`、`pm list package -3`、`cat /proc/self/cgroup`、`ps`），
  无外部输入路径。`REQUEST_INSTALL_PACKAGES` 仅用于
  `com/didichuxing/unifybridge/core/permission/install/ORequest.java:31,41`
  与 `y82/a.java:157-158` 的自身升级安装，安装源由系统 `PackageInstaller` 校验。
  完整调用点清单见 [permissions.md](permissions.md) §6。
- **导出组件面偏大。** 72 个导出组件中 62 个未加 `android:permission`，包括
  `com.didi.sdk.app.scheme.SchemeDispatcherActivity`、全部支付 `SchemeActivity`
  族与 `WebProxyActivity`。任意第三方应用可构造 Intent 直达这些页面；是否产生
  越权取决于这些页面是否再次校验调用方，静态代码显示它们依赖登录态而非调用方
  身份。
- **超范围采集需分档。** `QUERY_ALL_PACKAGES`（targetSdk 35）配合
  `queryIntentActivities` 被用于支付/导航/推送的可用性判断，属功能必需；
  但 `READ_LOGS`、`com.android.launcher.permission.INSTALL_SHORTCUT`、
  `MOUNT_UNMOUNT_FILESYSTEMS`、`SYSTEM_OVERLAY_WINDOW` 与
  `DETECT_SCREEN_CAPTURE` 属过度声明。`DeviceInfoNameEnum` 中的 `cpuSerialNo`
  与 `isRoot`/`emulatorType`/`isDebug` 在用户可见范围内无对应功能。

### 1.7 AI 叫车（`llm_assistant`）

**开启条件（两层与运算）** —
`com/didi/carhailing/component/scene/view/g.java:317`：

```java
if (HomeContainer.a.a() || l.b(1, "llm_assistant_main", "llm_ut_ai_callcar_switch") != 1) {
    // 隐藏动画
} else {
    // 显示 assets 动画
}
```

`HomeContainer.a.a()`（`HomeContainer.java:273-276`）等价于
`HomeContainer.O == 1`，而 `O` 由首页返回的
`common_params["skin_style"]` 赋值（`HomeDataManage$refresh$1.java:762-764`、
`c.java:334-335`，默认 0）。因此动画只在
**`skin_style != 1` 且远程配置 `llm_assistant_main.llm_ut_ai_callcar_switch == 1`**
时出现。

**功能面** — 路由 `llm_assistant://` 提供 `/home`
（`AssistantFragment.java:153`）、`/hhxd`（`HHXiaoDiFragment.java:37`）、
`/citypicker`。服务端入口列表由
`com/didi/assistant/main/net/g.java:100` `/asst/w/update/general_entrance/data`
下发；该模块共有 34 条 `@o` 声明，其中 32 条为 `/asst/w/…` 路径，涵盖
`new_home_page/v1`、`sidebar/v3`、`qid/create|stop|boxView/v1`、
`sid/new|del|fav|listMsg|tagMsg/v1`、`riding/getReorderQuery/v1`、
`asst_match/drivers|confirm|online|auto_confirm/toggle/v1`、
`delegate_riding/create/v1`（委托叫车）、`prepay/status/v1`、
`getInTripStatus/v1`、`input/association/v1`、`t/check_picture/legal`、
`t/check_poi/legal`、`t/tool/getGeoInfo`。

**流式协议** — `HomeRepository.java:83` 与 `:89` 两个 SSE 端点：
`https://llab-asst.xiaojukeji.com/asst/w/map/query/sse/stream` 与
`https://llab-asst.xiaojukeji.com/asst/w/stream/v1`。

**撮合状态机** —
`com.didi.assistant.main.home.model.sse_model.MatchingState.java:20-30` 定义
11 个状态 `CONTROL_GROUP(0) … CANCELLED_AFTER_SUCCESS(10)`；`:88-90`
`isHailingActive()` = `MATCHING|MATCHED|HANDED_TO_XIAODI`，`:96-98`
`isTerminal()` = `TIMEOUT|USER_STOP|CANCELLED|CONFIRM_TIMEOUT|CONFIRM_SUCCESS|CANCELLED_AFTER_SUCCESS`。
轮询由 `HailingPhaseManager` 驱动：`:82-85` 应用在后台时直接跳过；`:113-116`
丢弃 `seq` 小于已见值的过期响应；`:128-149` 在 `MATCHED`/`HANDED_TO_XIAODI`
时结束阶段并保留 socket；`:150-154` 在终态时停止。轮询间隔为
`l.b(10, "llm_assistant_main", "llm_hailing_poll_interval") * 1000`（`:158-160`，
默认 10 秒）。

**前置条件** — `AssistantFragment.java:733-739` 用
`LoginLocationAuthComponent` 把登录态与定位权限串成
`isHomeDataLoadFlow`，登录失败时由 `llm_assistant_experiment` 的
`llm_exit_when_login_failed_android` 决定是否退出。

## 2. 加密与混淆闭包

加密与混淆的最终结论如下：

| 用途 | 算法 | 位置 |
|---|---|---|
| 接口加密请求 | AES-128/ECB/PKCS5Padding + GZIP | `f32/a.java:11-20,23-27`、`f32/k.java` |
| 接口加密响应 | 同上，解密后 GZIP 解压 | `x22/a.java:24-37,43` |
| 会话密钥 | `KeyGenerator.getInstance("AES").init(128)` | `com/didi/ride/util/g.java:129-132` |
| 密钥编码 | Base64（自实现表） | `f32/d.java:13,93-130` |
| 手机号本地存储 | `DES/ECB/PKCS5Padding`，密钥固定 `*&^%$#@!` | `LoginStore.java:211-213,338-340`、`libsignkey.so:0x618` |
| 手机号上报 | 同一 DES 密钥 + Base64 | `SignKey.java:8`、`didiadapter/g.java:32-50` |
| 支付键盘 | SM3 HMAC | `cn/passguard`、`libPassGuard.so` |
| 请求签名 | `0x4d1e0 → 0x618f4 → 0x1b0a78 → 0x211374 Base64` | `libdidiwsg.so` |
| 基础库 AES | native | `libdfbasenative.so`（`dfbasesdk/utils/AES.java:13`） |
| 本地库加密 | SQLCipher | `libsqlcipher.so` |

`f32/a.java` 的 transform 字符串在静态初始化块中由
`"SWA=WQP=BYQA'Bsvv{|u"` 逐字节异或 `18` 还原，结果为
`AES/ECB/PKCS5Padding`——这是混淆而非未知算法。`e81/a.java:101,105,111` 生成
每请求随机密钥后调用 `g.c(...)`，`e81/a.java:50-55,85-86` 用 `x22.a.a(...)`
解密响应，`SgConstants.KEY = "key"` 与 `"enReq"`/`"enRes"` 构成密文容器。
`libdidiwsg.so` 的 `.datadiv_decode...@0x57c2c` 完成 74 段 XOR 解码；
`fcn.000588dc(index)` 以两张 32 位表 XOR 得到状态值，JNI 入口再按
`br (基址 + (状态 + g[index]) & mask + offset)` 进入平坦化决策树。
`checkMethod@0x567dc` 的五个判定叶子已逐条还原，签名网关的实际执行轨迹
覆盖 `0x618f4`、`0x1b0a78` 和使用两种 64 字符字母表的 `0x211374`。

`libdexvmp.so` 由 `com/fort/andJni/JniLib1773859712.java:13` 加载，其
`Invoke*` 反射桥被 `cn/wh/auth/*`（联通认证 SDK）使用，作用是把 Java 反射调用
下沉到 native，与风控签名无关。

## 3. 设备端只读验证

经只读 SSH 读取应用私有数据目录，实际识别 17 个数据库：62 张可解析表，另有
2 个加密库文件。全部 DDL 与行数见 [evidence.md](evidence.md) §9。要点：

- `location_info.db.location`（66 行）只有 `_id, ts, type, byte_data BLOB` 四列，
  坐标以 BLOB 落盘，不落明文经纬度；
- `track_upload_sdk2.db.tbl_track_nodes`（0 行）21 列，含
  `lat/lng/accuracy/speed/direction/altitude/accelerated_speed_x|y|z/`
  `included_angle_yaw|roll|pitch/scene_type/map_extra_point_data`，与
  `TrackNodeEntityDao.java:29-48` 的属性定义逐列一致；
- `poi_base_lib_task_data_encrypt.db` 与 `poi_selector_task_data_encrypt.db`
  文件头不是 `SQLite format 3`，`sqlite3` 报 `file is not a database`，与
  `li2/b.java:42`、`sj2/g.java:699` 使用加密变体一致；
- `DIDI_DATABASE` 14 张表中只有 `city_detail` 有数据（369 行），
  `address`/`hot_address` 均为 0 行；
- `dns_record.db.dns`（28 行）与 `lolly_room_db.dns_record`（75 行）为 DNS 缓存，
  列为 `host/ips/type/time/ttl`。

设备端不存在明文手机号、明文坐标或明文 token 落盘。

## 4. 报告边界

本报告固定 APK 的 DEX/XML/native 结论，客户端代码可证明的网络流程、协议、认证、
上传下载、权限、隐私、风控和打车/AI 叫车静态链路均已形成最终结论。服务端评分
阈值、处罚和画像留存时长不在 APK 内，不能静态证明。按静态只读要求，未登录、
未发请求、未估价、未下单、未支付、未开启 AI 叫车、未抓取账号数据、未绕过风控，
也未写入或修改设备端数据。
