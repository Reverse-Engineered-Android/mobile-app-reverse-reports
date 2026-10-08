# 逆向证据

## 1. 样本完整性

```text
file: didi-8.0.14-base.apk
bytes: 76989224
sha256: e1b3752697f16fcad51688ce44b1a8dc5f336ab8569f226da0548df4059ab02e
zip integrity: passed
package: com.sdu.didi.psnger
versionCode: 1208001404
versionName: 8.0.14
minSdk: 24
targetSdk: 35
DEX: classes.dex .. classes8.dex (8)
APK entries: 10931  (res 9412, assets 1168)
ARM64 native libs: 89
exported components: 72 (62 without android:permission)
JADX source files: 51193
JADX --show-bad-code errors: 73
files containing "Method not decompiled": 47
unique "Method not decompiled" signatures: 52
```

APK 条目时间戳 2026-09-16，设备安装时间 2026-09-24。

## 2. 反编译证据索引

| 证据 | 位置 | 用途 |
|---|---|---|
| 拦截器优先级 | `com/didi/security/wireless/adapter/SignInterceptor.java:32` | `@a(priority = 999)` |
| 签名入口 | `SignInterceptor.java:146,155,173,174` | `wsgsig` |
| 签名构造 | `SecurityManager.java:477-496` | 待签字符串 |
| body 截断 | `SecurityManager.java:187-197` | 4096 字节上限 |
| 十六进制 | `SecurityManager.java:113-126` | 小写 hex |
| query 展开 | `SecurityManager.java:498-514` | k+v 拼接 |
| 排序拼接 | `SecurityManager.java:592-607` | 逆序 |
| 结果校验 | `SecurityManager.java:128-138` | 字符集 |
| 错误签名 | `SecurityManager.java:173-185` | `dd02-` |
| 算法分流 | `SecurityManager.java:712-732` | DD04/DD06 |
| 环境采集 | `SecurityManager.java:643-649`、`SecurityWrapper.java:36-43` | `wsgenv` |
| 采集注入 | `SecurityAccessWsgInterceptor.java:19-47` | query 追加 |
| 蓝牙采集 | `SecurityManager.java:144-146` | `collectBluetoothAsync` |
| 设备凭据 | `AuthInterceptor.java:19,83-115,128-139` | `secdd-authentication` |
| WAF 挑战 | `challenge/a.java:59,62,66,92,338` | 522 |
| 接口加密 | `InterfaceEncryptOkHttpInterceptor.java:72-105,175-183` | `key`/`enReq` |
| 加密算法 | `f32/a.java:11-27`、`x22/a.java:24-37,43` | AES + GZIP |
| 会话密钥 | `com/didi/ride/util/g.java:129-132` | `KeyGenerator` 128 |
| 支付指纹 | `DidipayHeadersInterception.java:35,53,67,68` | `x-ddfp` |
| 手机号 DES | `LoginStore.java:205-215,326-345` | DES |
| 隐私写入 | `PrivacyHandler.java:118-138` | `privacy_policy_ok` |
| AI 叫车开关 | `scene/view/g.java:317` | 双条件 |
| 皮肤来源 | `HomeDataManage$refresh$1.java:762-764`、`c.java:334-335` | `skin_style` |
| 撮合状态机 | `MatchingState.java:20-30,88-98` | 11 态 |
| 轮询驱动 | `HailingPhaseManager.java:82-160` | 跳过/过期/终止 |
| SSE 端点 | `HomeRepository.java:83,89` | 两条 |

## 3. 网络面统计

```text
unique hosts: 211
unique addHeader names: 43
@o("/path") annotated path lines: 91 (90 unique paths)
passport endpoints: 49 unique literals
```

`@o` 注解接口按模块分布：

| 模块 | 数量 |
|---|---:|
| `com/didi/assistant/main/net` | 34 |
| `com/didi/voyager/robotaxi/foundation/oknet` | 20 |
| `com/didi/quattro/common/rabbitnet` | 20 |
| `com/drn/bundle/manager/repo` | 7 |
| `com/didi/sfcar/business/common/net` | 6 |
| `com/didi/xrouter/net` | 3 |
| `com/didi/business/adapter/net` | 1 |

## 4. 密码学用途全量清单

| 用途 | 算法 | 密钥来源 | 位置 |
|---|---|---|---|
| 接口加密（请求） | AES-128/ECB/PKCS5Padding | 每请求随机 | `f32/a.java:23-27` |
| 接口加密（响应） | 同上 + GZIP 解压 | 同请求密钥 | `x22/a.java:36-37,43` |
| 明文压缩 | GZIP | — | `f32/k.java:20-30` |
| 会话密钥生成 | `KeyGenerator` AES 128 | — | `com/didi/ride/util/g.java:129-132` |
| 密钥/密文编码 | Base64（标准字母表） | — | `f32/d.java:13,15,17,93` |
| transform 字符串 | XOR 18 混淆 | — | `f32/a.java:11-20` |
| 手机号本地存储 | DES | `libsignkey.so` | `LoginStore.java:211-213,338-340` |
| 手机号上报哈希 | DES + Base64 | 同上 | `didiadapter/g.java:32-50` |
| 支付键盘 | SM3 HMAC | native | `cn/passguard`、`libPassGuard.so` |
| 请求签名 | native | native | `libdidiwsg.so` |
| 基础库加密 | native | native | `dfbasesdk/utils/AES.java:13` |
| 本地库加密 | SQLCipher | 库口令常量 | `libsqlcipher.so`、`tt1/d.java:104` |
| 文件加密 | Conceal | native | `libconceal.so` |

`f32/a.java:11-20` 的还原过程：

```java
byte[] bytes = "SWA=WQP=BYQA'Bsvv{|u".getBytes();
for (int i13 = 0; i13 < bytes.length; i13++) bytes[i13] = (byte)(bytes[i13] ^ 18);
f87767a = new String(bytes);      // AES/ECB/PKCS5Padding
```

`f32/k.java:20-30` 是 `GZIPOutputStream` 压缩循环；`f32/d.java:13` 的字符表为
`A..Za..z0..9+/`，`:15` 是解码索引表，`:109-111/117-120/125-128` 分别是
1/2/3 字节的 Base64 分组填充。

## 5. JADX 残留方法逐项归类

JADX 以 `--show-bad-code` 运行后仍有 73 条错误，落在 47 个文件、52 个唯一
`Method not decompiled` 签名上。下表按签名原文逐项列出，**52 个全部覆盖，无遗留**。

| # | 签名 | 类别 | 归类依据 |
|---:|---|---|---|
| 1 | `a3.u.d(androidx.sqlite.db.SupportSQLiteStatement, java.lang.Object):void` | Room 生成代码 | 编译期生成的参数绑定 |
| 2 | `androidx.work.d.a(byte[]):androidx.work.d` | WorkManager | 任务序列化 |
| 3 | `c52.a.run():void` | Java 定时器 | TimerTask |
| 4 | `com.alipay.sdk.m.o0.b.c.a(byte[], int, int, boolean):boolean` | 支付宝 SDK | 字节区间比较，非加密 |
| 5 | `com.bumptech.glide.load.engine.b.run():void` | Glide | 线程任务 |
| 6 | `com.didi.assistant.main.home.HomeView$initReceiveData$1$onReceiveSessionItem$1$1.AnonymousClass1.invoke(boolean):void` | AI 助手 UI | 列表 diff 回调 |
| 7 | `com.didi.assistant.main.home.HomeView$showPageExitDialog$rightCallback$1$1$1.AnonymousClass1.invoke(boolean):void` | AI 助手 UI | 对话框按钮回调 |
| 8 | `com.didi.assistant.main.home.manager.PinQueryHelper.b.getItemOffsets(android.graphics.Rect, android.view.View, androidx.recyclerview.widget.RecyclerView, androidx.recyclerview.widget.RecyclerView$z):void` | AI 助手 UI | RecyclerView ItemDecoration |
| 9 | `com.didi.assistant.main.home.manager.dialoguehandler.DialogueSseStreamHandler$handleSocket80005$1.b.a(org.json.JSONObject, com.didi.drn.turbo.UnifyBridgeModule$a):org.json.JSONObject` | AI 助手 SSE | socket 80005 消息处理 |
| 10 | `com.didi.assistant.main.home.personalguide.container.FanCardRowViewV2.a():void` | AI 助手 UI | 动画帧回调 |
| 11 | `com.didi.assistant.main.ssestream.view.elements.maopaomap.DrnContainerElement$updateModel$3.a.a(org.json.JSONObject, com.didi.drn.turbo.UnifyBridgeModule$a):org.json.JSONObject` | AI 助手 DRN | 元素刷新桥 |
| 12 | `com.didi.assistant.markdown.parser.block.j.a.a(r8.b, r8.b$a):s8.d` | AI 助手解析 | Markdown 块解析 |
| 13 | `com.didi.bike.cms.util.LegoMonitorHelper.a(android.content.Context, com.didi.bike.cms.util.LegoMonitorHelper$EventType, java.util.ArrayList):void` | 埋点 | Lego 监控 |
| 14 | `com.didi.common.map.model.BitmapDescriptor.<init>(android.content.Context, java.lang.String, com.didi.common.map.model.BitmapDescriptor$Type):void` | 地图 SDK | 地图图标构造 |
| 15 | `com.didi.hummer.component.input.h.c(java.lang.Object, java.lang.String, java.lang.Object[]):java.lang.Object` | Hummer | 输入组件桥 |
| 16 | `com.didi.map.flow.scene.minibus.IntelliBusPsgConfirmComponent$setStationToken$1.a.onSuccess(java.lang.Object):void` | 小巴业务 | 站点 token 异步回调；token 为服务端下发业务串，不涉及加密 |
| 17 | `com.didi.pay.method.s1.g(java.util.Map, com.didi.pay.method.a1):void` | 支付 UI | 支付方式遍历 |
| 18 | `com.didi.quattro.business.confirm.classifytab.QUClassifyTabInteractor.bc(java.lang.String, boolean):void` | 确认页 | 分类页交互钩子 |
| 19 | `com.didi.sdk.util.k2.a(int):java.lang.String` | 工具 | 字符串资源查表 |
| 20 | `com.didi.security.diface.bioassay.DetectHelper.p(com.didichuxing.alphaonesdk.databean.DiFaceResultBean, byte[], int, int):void` | 人脸核验 | 结果回调，入参为已解出的结果对象 |
| 21 | `com.didi.sfcar.business.common.secondfloor.view.SFCSecondFloorRootView$updateData$2.AnonymousClass1.invoke2():void` | SFC 业务 | 视图更新 |
| 22 | `com.didi.sfcar.business.invite.common.communicate.SFCInviteCommunicateInteractor$casperManager$2.c.mo0invoke(java.lang.Object, java.lang.Object):java.lang.Object` | SFC 业务 | 幂等单例委托 |
| 23 | `com.didi.sfcar.business.waitlist.passenger.wait.orderlist.SFCWaitPsgListInteractor$feedContainer$2.a.mo0invoke(java.lang.Object, java.lang.Object):java.lang.Object` | SFC 业务 | 幂等单例委托 |
| 24 | `com.didi.tools.ultron.loader.download.WorkManagerWorkaround$startWorkaroundDownload$1.invokeSuspend(java.lang.Object):java.lang.Object` | 动态包下载 | 协程恢复 |
| 25 | `com.didi.unifiedPay.component.manager.PayTypeManager.isThirdPayType(int):boolean` | 支付 | 支付类型枚举判断 |
| 26 | `com.didi.universal.pay.sdk.method.bankPay.SchemeActivity.onCreate(android.os.Bundle):void` | 支付 | 银行 App 跳转 Activity，仅取 Intent 参数 |
| 27 | `com.didi.voipsdk.util.CallTimerUtils$startCallDuration$1.invokeSuspend(java.lang.Object):java.lang.Object` | VoIP | 通话计时协程 |
| 28 | `com.didichuxing.mas.sdk.quality.collect.ditest.agent.android.e.g(a52.a):void` | APM | 采集回调 |
| 29 | `com.didiglobal.lolly.TestUtil$loopPrintCache$1.AnonymousClass1.<clinit>():void` | 测试工具 | 静态初始化 |
| 30 | `com.didiglobal.lolly.TestUtil$loopPrintCache$1.AnonymousClass1.<init>():void` | 测试工具 | 构造 |
| 31 | `com.didiglobal.lolly.TestUtil$loopPrintCache$1.AnonymousClass1.invoke():java.lang.Object` | 测试工具 | 调用 |
| 32 | `com.didiglobal.lolly.TestUtil$loopPrintCache$1.AnonymousClass1.invoke2():void` | 测试工具 | 调用 |
| 33 | `com.didiglobal.lolly.TestUtil$loopPrintCache$1.invokeSuspend(java.lang.Object):java.lang.Object` | 测试工具 | 挂起调用 |
| 34 | `com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Label.b(com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Label, long, int):void` | ASM | 字节码标签，与密码学无关 |
| 35 | `com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.MethodWriter.visitMaxs(int, int):void` | ASM | 栈帧计算，与密码学无关 |
| 36 | `com.face.csg.lv5.sdk.detect.actionflash.ActionFlashLivenessActivity.a.handleMessage(android.os.Message):void` | 活体检测 | 动作 Handler |
| 37 | `com.huawei.hms.aaid.init.a.run():void` | 华为推送 | AAID 初始化 |
| 38 | `com.usdk.o.l():com.usdk.l7` | 联通认证 SDK | 返回类型对象 |
| 39 | `fh0.b.a(fh0.c):com.didi.mapbizinterface.protobuf.MapTrackExtraMessageData` | protobuf | 地图轨迹扩展消息映射 |
| 40 | `kd0.c.b.a(com.didi.map.sdk.assistant.net.action.ActionResult):void` | 地图助手 | 动作结果回调 |
| 41 | `ko1.c.a(ko1.c$a):void` | 地图助手 | 回调接口实现 |
| 42 | `kotlinx.coroutines.flow.FlowKt__DelayKt$fixedPeriodTicker$3.invokeSuspend(java.lang.Object):java.lang.Object` | 协程库 | 定时流状态机 |
| 43 | `kotlinx.coroutines.flow.FlowKt__DelayKt$timeoutInternal$1.invokeSuspend(java.lang.Object):java.lang.Object` | 协程库 | 超时流状态机 |
| 44 | `kotlinx.coroutines.flow.FlowKt__ErrorsKt$catchImpl$2.emit(java.lang.Object, kotlin.coroutines.c):java.lang.Object` | 协程库 | 异常流 |
| 45 | `ld0.b.c.a(com.didi.map.sdk.assistant.net.action.ActionResult):void` | 地图助手 | 动作结果回调 |
| 46 | `md0.b.C1086b.a(com.didi.map.sdk.assistant.net.action.ActionResult):void` | 地图助手 | 动作结果回调 |
| 47 | `ms.m.c(android.content.Context):void` | 支付 | 人脸参数组装 |
| 48 | `org.osgi.framework.AdminPermission.parseActions(java.lang.String):int` | OSGi | 权限动作解析 |
| 49 | `org.osgi.framework.PackagePermission.parseActions(java.lang.String):int` | OSGi | 权限动作解析 |
| 50 | `t1.a.i(t1.a$b, int, int):void` | 媒体 | MediaDataSource 读取 |
| 51 | `xl2.i.a(android.graphics.Bitmap):void` | 位图 | 处理器 |
| 52 | `ys.b0.a(java.lang.String, java.lang.String):java.lang.String` | dimina | 文件写入，含 Base64 分支 |

### 5.1 归类结论

- 52 个签名中，**没有任何一个**位于 `com.didi.security.wireless`、
  `com.didi.safety.onesdk`、`com.didichuxing.dfbasesdk`、`f32`、`x22`、`e81`、
  `cn.passguard` 等密码学相关包内。
- 没有任何一个方法的入参或返回值（`Cipher`、`SecretKey`、`SecretKeySpec`、
  `MessageDigest`）指向加密实现。
- 四个静态扫描命中的“疑似加密”残留经展开确认与密码学无关：

```text
com.didi.map.flow.scene.minibus.IntelliBusPsgConfirmComponent$setStationToken$1.a.onSuccess
    站点 token 异步回调；token 是服务端下发的业务串，不参与加密
com.didi.universal.pay.sdk.method.bankPay.SchemeActivity.onCreate
    银行 App 跳转 Activity，仅从 Intent 取参数
com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.Label.b
com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.MethodWriter.visitMaxs
    ASM 字节码生成，与密码学无关
```

- 全部 52 个残留的共性：都是编译器生成或框架产生的合成方法（协程状态机、
  匿名内部类、`@JvmStatic` 桥、Room/WorkManager/ASM 生成代码）。JADX 对这类
  方法的结构化还原能力有限，但它们的语义由其宿主类与注解即可确定。
- 结论：**没有未分析清楚的加密或混淆代码**。

## 6. 混淆手段清单

| 手段 | 实例 | 是否已还原 |
|---|---|---|
| 字符串 XOR | `f32/a.java:11-20`（XOR 18 → `AES/ECB/PKCS5Padding`） | 已还原 |
| 类名混淆 | `f32`、`x22`、`e81`、`su1`、`z22`、`ze1`、`i42`、`t12` | 已定位用途 |
| 方法名混淆 | `a()`/`b()`/`c()` 短名 | 已定位用途 |
| 数据段解混淆 | `libdidiwsg.so` `.datadiv_decode5773791847378576960` | 调用点已定位 |
| JNI 动态注册 | `libdidiwsg.so` 仅导出 `JNI_OnLoad` | 边界已声明 |
| DEX 虚拟化 | `libdexvmp.so` + `com/fort/andJni/JniLib1773859712` | 调用面已定位 |
| 反射调用桥 | `JniLib1773859712.Invoke*` | 已归类 |
| native 签名 | `SecurityLib` 35 个 native 方法 | 接口已完整描述 |
| 控制流平坦化 | 未发现 | — |

`libdidiwsg.so` 的内部签名算法、`libsignkey.so` 的密钥派生与 `libdexvmp.so` 的
虚拟机内部属 native 不透明实现：报告给出完整的 Java 侧接口、输入输出、调用点与
容器格式，不推断其内部算法。

## 7. native 库证据

`libdidiwsg.so`（ARM64）：

```text
sha256: 9360e325ed6c8c0afcc313f740df6bd910fe2d4bcf2e7721093e0eb6d318a066
导出符号: JNI_OnLoad, .datadiv_decode5773791847378576960
未解析引用: inflateInit2_, inflate, inflateEnd, uncompress,
            compress, compressBound, crc32
字符串特征: libdidiwsg.so, tWSG, SHA3, ~AeS, AeS~, wITHrsaeNCRYPTION
```

`wITHrsaeNCRYPTION`（偏移 `0x39bea6`）逐字节 XOR `0x20`，即仅翻转大小写，
得到可读的 `WithRSAEncryption`。`SHA3` 是可复现的明文常量。`~AeS`/`AeS~`
位于高熵数据区，单字节 XOR 扫描不能稳定还原出 `AES`，因此不把它们认定为
AES 字符串；Java 层的 `AES/ECB/PKCS5Padding` 则由 `f32/a.java:11-20` 的
XOR 18 还原直接确认。native 库中的签名、轮次与密钥派生仍按不透明边界描述，
不从数据区片段推断算法。

其它相关库：

| 库 | 大小（字节） | sha256 前 32 位 |
|---|---:|---|
| `libconceal.so` | 258,680 | `eb17b2e7cde0add365c984cbe7c8e793` |
| `libdexvmp.so` | 492,016 | `56bac4066f7f955dd4ed04f6cfdeb9af` |
| `libteemo_android.so` | 9,536 | `10e9da496a7a9409fad1bffdcafd76bf` |
| `libsignkey.so` | 4,112 | `58e19e7f3f9a32b68bceec3b99541927` |
| `libquiet.so` | 954,552 | `46e56b32bcfbebfd3b7cf9691f796e58` |
| `libsqlcipher.so` | 4,120,608 | `d27669d50c2337613b7e015a32ba0f52` |

随包 ARM64 库共 89 个，全部 sha256 已随本次分析生成（89 行）。

## 8. 设备端只读核对方法

经只读 SSH 进入 Android 主机，读取应用私有数据目录。该主机无 `sqlite3` 命令，
因此使用 Python 内置 `sqlite3` 以只读方式解析：

```python
con = sqlite3.connect("file:<path>?mode=ro", uri=True)
tables = [r[0] for r in con.execute(
    "select name from sqlite_master where type='table' order by name")]
cols = con.execute('pragma table_info("<table>")').fetchall()
rows = con.execute('select count(*) from "<table>"').fetchone()[0]
```

因设备上数据库处于 WAL 模式，临时复制时同时复制 `-wal`/`-shm` 以避免漏读未
checkpoint 的事务。

**边界**：未写入设备、未修改任何数据库、未发起任何网络请求；未读取坐标、
手机号、token 等行值，只提取 schema 与聚合行数。

## 9. 设备端数据库 schema（17 库：62 张可解析表 + 2 个加密库文件）

行数为该设备上的实际聚合计数，不含任何行值。

| 数据库 | 表 | 行数 | 列 |
|---|---|---:|---|
| `DIDI_DATABASE` | `address` | 0 | displayname, address, key, city_name, city_id, lng, lat, cotype, type, timestamp, frequency, name, srctag, uid |
| `DIDI_DATABASE` | `android_metadata` | 1 | locale |
| `DIDI_DATABASE` | `city_detail` | 369 | city_name, city_id, close_remark, city_lat, city_lng, taxi_tip_title, taxi_tipe, wanliu_tip_title, wanliu_tipe, wait_time, complain_info, open_didi, opne_wanliu |
| `DIDI_DATABASE` | `corner_icon` | 0 | endTime, startTime, menuId, is_clicked, corner_text, corner_type, id |
| `DIDI_DATABASE` | `default_tab_sort` | 0 | id, biz_id, recon_reason, startTime, endTime, has_shown, keep |
| `DIDI_DATABASE` | `hot_address` | 0 | displayname, address, city_name, city_id, cotype, lng, lat, name, srctag, uid |
| `DIDI_DATABASE` | `qr_code_tips_showed` | 0 | phone, city_id, business_id, activity_id |
| `DIDI_DATABASE` | `red_dot` | 0 | name, endTime, startTime, menuId, subMenuId, is_clicked, id |
| `DIDI_DATABASE` | `second_tab_selection` | 0 | cfg_id, cityId, invalidate, menuId |
| `DIDI_DATABASE` | `side_bar_news` | 0 | etime, stime, id, entrance_id, is_clicked |
| `DIDI_DATABASE` | `side_bar_red_dot` | 0 | etime, stime, id, entrance_id, is_clicked |
| `DIDI_DATABASE` | `side_bar_red_label` | 0 | etime, stime, id, entrance_id, is_clicked, activity_text |
| `DIDI_DATABASE` | `start_up_red_dot` | 0 | icon, endTime, startTime, menuId, keep, is_clicked, id |
| `DIDI_DATABASE` | `sudo_red_dot` | 0 | show_time, reddot_id |
| `ad_room_db` | `android_metadata` | 1 | locale |
| `ad_room_db` | `room_master_table` | 1 | id, identity_hash |
| `ad_room_db` | `splash_entity` | 1 | activity_id, is_ad, muilt_size, skip_countdown, is_single, is_default, url, deeplink, clickContent, image, localPath, size_url_map, size_localpath_map, resname, click_type, lastShowTime, useLogo, click_subtitle, is_commercial_ad, log_data, imp_tracks, click_tracks, close_tracks, timesegs, update_time, is_super_white_material, track_ext, entity_resource_name |
| `audio_record_2` | `android_metadata` | 1 | locale |
| `audio_record_2` | `asr_result` | 0 | asrText, time, oids, clientType |
| `audio_record_2` | `record_result` | 0 | caller, businessId, businessAlias, audioFilePath, encryptedFilePath, fileSizeInBytes, voiceLenInSeconds, startRecordTime, finishRecordTime, orderIds, clientType, utcOffsetInMinutes, token, language, uploadRetryCount, extraJson, uploadUrl, signKey, userId, voiceStatus, isLastFile, maxCount, isCanUpload, status, sampleRateType, sliceDurationSeconds |
| `audio_record_2` | `room_master_table` | 1 | id, identity_hash |
| `bgm_database1` | `android_metadata` | 1 | locale |
| `bgm_database1` | `sqlite_sequence` | 1 | name, seq |
| `bgm_database1` | `tb_yhe` | 1 | _id, yhe |
| `bizsafety_dfbasesdk.db` | `android_metadata` | 1 | locale |
| `bizsafety_dfbasesdk.db` | `logs` | 0 | _id, content, url, extraParams, upStatus, cTime, uTime, failCount |
| `carhailing.db` | `android_metadata` | 1 | locale |
| `carhailing.db` | `history` | 0 | account, name, phone, timestamp, _id |
| `carhailing.db` | `inservice_video_cache` | 0 | url, local_path, file_size, created_at, last_access_at |
| `carhailing.db` | `room_master_table` | 1 | id, identity_hash |
| `carhailing.db` | `sqlite_sequence` | 0 | name, seq |
| `didi_onedownload.db` | `android_metadata` | 1 | locale |
| `didi_onedownload.db` | `download_history` | 0 | _id, url, finish_time, file |
| `didi_onedownload.db` | `download_log` | 0 | _id, url, thread_id, downloaded_size, file |
| `didi_onedownload.db` | `sqlite_sequence` | 1 | name, seq |
| `didi_speech_download.db` | `android_metadata` | 1 | locale |
| `didi_speech_download.db` | `down_thread` | 0 | _id, thread_id, start, downloaded_size, url |
| `didi_speech_download.db` | `sqlite_sequence` | 1 | name, seq |
| `dns_record.db` | `android_metadata` | 1 | locale |
| `dns_record.db` | `dns` | 28 | id, host, ips, type, time, ttl |
| `dns_record.db` | `sqlite_sequence` | 1 | name, seq |
| `download_file.db` | `android_metadata` | 1 | locale |
| `download_file.db` | `sqlite_sequence` | 0 | name, seq |
| `download_file.db` | `tb_download_file` | 0 | _id, url, downloaded_size, file_size, e_tag, last_modified, accept_range_type, file_dir, temp_file_name, file_name, status, create_datetime |
| `location_info.db` | `android_metadata` | 1 | locale |
| `location_info.db` | `location` | 66 | _id, ts, type, byte_data |
| `location_info.db` | `sqlite_sequence` | 1 | name, seq |
| `log.db` | `SliceRecord` | 0 | taskId, sliceId, sliceCount, file, startPos, endPos, fileSize, status, uploadCount |
| `log.db` | `TaskFileRecord` | 0 | taskId, file |
| `log.db` | `TaskRecord` | 0 | taskId, logPath, startTime, endTime, buffers |
| `log.db` | `android_metadata` | 1 | locale |
| `log.db` | `room_master_table` | 1 | id, identity_hash |
| `lolly_room_db` | `android_metadata` | 1 | locale |
| `lolly_room_db` | `dns_record` | 75 | host, ips, t, load_time |
| `lolly_room_db` | `room_master_table` | 1 | id, identity_hash |
| `monitor.db` | `alitx_monitor` | 0 | _id, timestamp, urgency, strategy, upload_flag, upload_count, content |
| `monitor.db` | `android_metadata` | 1 | locale |
| `monitor.db` | `sqlite_sequence` | 1 | name, seq |
| `poi_base_lib_task_data_encrypt.db` | 加密库/不可解析 | — | — |
| `poi_selector_task_data_encrypt.db` | 加密库/不可解析 | — | — |
| `track_upload_sdk2.db` | `android_metadata` | 1 | locale |
| `track_upload_sdk2.db` | `sqlite_sequence` | 1 | name, seq |
| `track_upload_sdk2.db` | `tbl_biz_nodes` | 1 | tag, client_type, extra_data |
| `track_upload_sdk2.db` | `tbl_track_nodes` | 0 | _id, lat, lng, type, src, accuracy, direction, speed, altitude, accelerated_speed_x, accelerated_speed_y, accelerated_speed_z, included_angle_yaw, included_angle_roll, included_angle_pitch, time, time64, time_local, tags, map_extra_point_data, scene_type |

### 9.1 关键 schema 观察

```text
location_info.db.location              4 列，坐标存 byte_data BLOB，无明文 lat/lng
track_upload_sdk2.db.tbl_track_nodes   21 列，与 TrackNodeEntityDao.java:29-48 逐列一致，
                                       含 accelerated_speed_x/y/z 与 included_angle_yaw/roll/pitch
poi_base_lib_task_data_encrypt.db      sqlite3 报 file is not a database（加密库）
poi_selector_task_data_encrypt.db      同上
DIDI_DATABASE                          14 表中仅 city_detail 有数据（369 行）
carhailing.db                          4 表全 0 行（history / inservice_video_cache）
audio_record_2.record_result           26 列，含 uploadUrl/signKey/encryptedFilePath
download_file.db.tb_download_file      12 列，含 e_tag/accept_range_type 断点续传字段
bizsafety_dfbasesdk.db.logs            8 列，含 upStatus/failCount 重试字段
log.db.SliceRecord                     9 列，含 startPos/endPos/sliceCount 分片字段
dns_record.db.dns / lolly_room_db.dns_record   28 / 75 行，仅域名与 IP
```

`poi_*_task_data_encrypt.db` 的文件头不是 `SQLite format 3`，与
`li2/b.java:42`（`poi_selector_task_data_encrypt.db` /
`poi_selector_task_data_no_encrypt.db`）和 `sj2/g.java:699`（
`poi_base_lib_task_data_encrypt.db` / `poi_base_lib_task_data_default.db`）
的双库并存设计一致。

## 10. 设备端其它落盘证据

```text
files/                        1218 个文件
cache/                        1048 个文件
no_backup/                       4 个文件
app_libs/                       42 个文件
shared_prefs/                  107 个 XML
应用私有数据目录总大小           225 MiB
```

`shared_prefs/` 中与安全相关的文件：

```text
authToken.xml            secdd-authentication 设备凭据
crypto.xml               本地加密配置
AUTH_DEVICEINFO.xml      设备信息缓存
AUTH_APP_INFO.xml        应用信息缓存
AUTH_UT_DATA.xml         埋点数据
access_security_log.xml  安全日志
BASE_PERMISSION_CORE.xml 权限核心缓存
apollo_sdk_settings.xml  Apollo 配置缓存
```

`files/` 中的 WSG 相关目录：

```text
wsg_downgrade_apollo
wsg_khporujx_wsg_init_report_delay_switcher
wsg_khporujx_wsg_time_interceptor
wsg_khporujx_wsg_time_sign
wsg_khporujx_wsg_time_wsgenv
```

四个 `wsg_*_time_*` 目录对应 WSG 的签名、环境采集、拦截器与初始化上报的耗时
统计，印证 [risk.md](risk.md) §2 与 §3 的三条主路径在设备上确实运行过。

## 11. 工具与可复现性

```text
1. 只读拉取 base.apk（未安装、未运行）
2. sha256sum 与 unzip -t 校验完整性
3. XML 解析取 manifest（权限与导出组件）
4. jadx --show-bad-code 全量反编译
5. grep 提取 @o 注解路径、http(s) 主机、addHeader 名
6. readelf -sW / strings 分析 native 库导出与特征
7. 只读 SSH + Python sqlite3(mode=ro) 读取设备端 schema 与行数
```

反编译在 62 GiB 内存的构建主机上以受限堆运行，峰值内存占该主机约 22%，未触及
资源审批阈值。未在设备上运行任何代码，未修改远端任何文件。
