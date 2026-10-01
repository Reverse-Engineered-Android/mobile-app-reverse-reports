# 小红书 9.37.0 `libxyasf.so` 设备指纹深挖

本文补上 [risk-controls.md](risk-controls.md) §3 与 §11 中列为未闭环的那一项：**native 侧 70+ 采集点的逐条清单**。结论是该项已闭环——`libxyasf.so` 未做字符串加密，JNI 导出名、protobuf 字段名、上报 URL 全部以明文存在于 `.rodata`，因此可以逐条列出采集面、消息格式与判定逻辑，不需要 VM 级 lift。

样本：`libs/lib/arm64-v8a/libxyasf.so`，621 104 字节，ELF for ARM aarch64。

## 0. 结论摘要

| 项 | 结果 |
| --- | --- |
| 混淆 | **无字符串加密**。`.rodata` 可打印字符占比 60.9%，熵 5.29；79 个静态 JNI 导出名全部明文 |
| 源码路径泄漏 | `.rodata` 内保留构建机路径 `/Users/chixue/Sources/code.devops.xiaohongshu.com/larbn/fingerprint/android/fingerprint_library/src/main/cpp/` |
| JNI 面 | 79 个 `Java_*` 静态导出 + 3 个 `RegisterNatives` 动态注册方法，共 **82** |
| 采集面 | 8 个 protobuf 子消息、**51 个字段**，全部取得字段编号 |
| 上报通道 | `POST https://as.xiaohongshu.com/api/v1/d/upload`，multipart/form-data，字段名 `file`、文件名 `image.jpg` |
| 载荷保护 | 16 字节固定前缀 +**全量 XOR 0x70**（唯一一处常量 XOR，全库共 1 个 `movi vN.16b,#0x70`） |
| 完整性校验 | **标准 MD5**（`MD5/Init/Update/Final/Transform/md5_block_data_order` 全导出，64/64 常量与轮转量逐条核对通过） |
| 反分析 | root（14 路径）、ptrace、maps、进程名/进程列表、Xposed、VirtualApp、模拟器 |
| 线程名 | `fpthread`（native `prctl(PR_SET_NAME)`）、`virposd`、`fpwdog`（Java） |
| 限流 | Java 侧 600 000 ms（10 分钟） |

## 1. 为什么这个库不需要 lift

前几个报告里 `libxyass.so`（Shield）与 `libtiny.so` 都需要应对 OLLVM 字符串加密与 CFF。`libxyasf.so` 两者都没有：

- 全库常量 XOR 只有 **1 处**（`movi v0.16b,#0x70` @ `0x1010c`），其余 `eor` 全是 `#1`/`#0x1f`/`#0x3f` 这类取负/掩码惯用法，不是加密。
- 没有独立 `dlopen`/`dlsym`，没有反调试 `syscall`；3 处 `syscall` 全部在分配器里是 `futex`（`syscall(0x62)`）。
- 1 006 处 PLT 调用点全部直接对应到导入符号名（93 个 PLT 桩已解析 77 个被实际使用），不存在字符串间接寻址的导入。

因此采集面可以直接从符号表 + `.rodata` + 反汇编读出。本报告的全部清单均由脚本从 ELF 静态导出，非人工摘抄。

## 2. JNI 面：79 + 3

### 2.1 动态注册（3 个）

`JNI_OnLoad` @ `0xe124`（3 476 字节）在 `0xe1e4` 处调用 `RegisterNatives`，方法表在 `.data.rel.ro` 的 `0xa6fc8`：

| # | 方法 | 签名 | 实现地址 |
| ---: | --- | --- | --- |
| 0 | `init` | `(Landroid/content/Context;I)V` | `0xef34` |
| 1 | `getFingerPrint` | `()Ljava/lang/String;` | `0xf4d8` |
| 2 | `start` | `(J)V` | `0xfa84` |

`FindClass` 参数 `com/xingin/a/a/f/FingerPrintJni` @ `0x7a56c`，与 `jadx-out/sources/com/xingin/a/a/f/FingerPrintJni.java` 一一对应。

三个入口都是**薄壳**：`getFingerPrint` @ `0xf4d8` 仅 68 字节，直接尾调 `0x11280`（protobuf 序列化 + 返回字符串）；`start` @ `0xfa84` 是真正的工作函数，3 196 字节，见 §4。

### 2.2 静态导出（79 个）

按 Java 类分组（类名来自 `Java_<类>_<方法>` 前缀）：

#### `BasicJniTest` — 27 个（构建与系统标识，全部为纯读属性）

| 地址 | 方法 | | 地址 | 方法 |
| --- | --- | --- | --- | --- |
| `0x30388` | `getAndroidId` | | `0x30dbc` | `getBuildHost` |
| `0x30410` | `getFingerPrintId` | | `0x30e90` | `getBuildId` |
| `0x30454` | `getApplicationName` | | `0x30f64` | `getBuildManufacturer` |
| `0x30498` | `getApplicationVersionName` | | `0x31038` | `getBuildTags` |
| `0x304dc` | `getApplicationVersionCode` | | `0x3110c` | `getBuildTime` |
| `0x30520` | `getOsVersion` | | `0x311e0` | `getBuildIsEmulator` |
| `0x30608` | `getBootTime` | | `0x31224` | `getGsmBaseBrand` |
| `0x3064c` | `getPhoneAbi` | | `0x312c4` | `getBoardPlatform` |
| `0x30690` | `isDebugAble` | | `0x307ac` | `getLang` |
| `0x306d8` | `getModel` | | `0x303cc` | `getImeiId` |
| `0x307f0` | `getBuildBoard` | | `0x308c4` | `getBuildBootloader` |
| `0x30998` | `getBuildBrand` | | `0x30a6c` | `getBuildDevice` |
| `0x30b40` | `getBuildDisplay` | | `0x30c14` | `getBuildFingerprint` |
| `0x30ce8` | `getBuildHardware` | | | |

#### `CallJavaJniTest` — 41 个（回调控 Java 取值）

`0x31364` `getChannel`、`0x313a8` `deviceId`、`0x313ec` `smId`、`0x31430` `sid`、`0x31474` `getInstallApps`、`0x314b8` `getWifiSSid`、`0x314fc` `getWAPMacAddress`、`0x31540` `getWifiMac`、`0x31584` `getOperatorName`、`0x315c8` `getNetworkCountryIso`、`0x3160c` `getCellIp`、`0x31650` `getWifiIp`、`0x31694` `getSensorList`、`0x316d8` `imsi`、`0x3171c` `simState`、`0x31760` `devOpenedCount`、`0x317a4` `getSystemScreenBrightnessValue`、`0x317e8` `getFreeExternalStorageSize`、`0x3182c` `getTotalExternalStorageSize`、`0x31870` `getFreeInternalStorageSize`、`0x318b4` `getTotalInternalStorageSize`、`0x318f8` `existXposed`、`0x3193c` `getPrivateFilePath`、`0x31980` `getApkPath`、`0x319c4` `getAPkSignature`、`0x31a08` `getApkCRC`、`0x31a4c` `getImei1`、`0x31a90` `getImei2`、`0x31ad4` `getMei1`、`0x31b18` `getMei2`、`0x31b5c` `getICCID`、`0x31c94` `getOperatorCode`、`0x31cd8` `isSupportCameraFlash`、`0x31d1c` `getJsFingerPrint`、`0x31d60` `getCheckingXposedResult`、`0x31da4` `getCheckingVirtualAppResult`、`0x31de8` `getRunningProcessListBySdkApi`、`0x31e2c` `getRunningProcessListByCommand`、`0x31e70` `getAccessibilityStatus`、`0x31eb4` `getEnabledAccessibilityServices`、`0x31ef8` `getSecretAppList`

这 41 个**名字带 "Jni" 但不含采集逻辑**：每个都是 "反射调用同名 Java 方法" 的转发壳。例如 `existXposed` @ `0x318f8` 只有 204 字节，函数体是 `FindClass` → `GetMethodID("existXposed", "()Z")` → `CallBooleanMethod`。真正的实现是 **`com.xingin.u.p.c`**（见 §5）。

#### `ComplexStructGatherTest` — 6 个（结构化数据）

`0x323a8` `getDisplayMetrics`、`0x32594` `getMemoryInfo`、`0x32780` `getBatteryInfo`、`0x32988` `getAccelerometerInfo`、`0x32b84` `getGyroscopeInfo`、`0x32d80` `getLocationInfo`

#### `NativeGatherTest` — 4 个（**检测逻辑在 native 内**）

`0x320b4` `isRoot`、`0x32174` `mapsInfo`、`0x32360` `isPtrace`、`0x31f80` `getProcessName`

#### `OkHttpJniTest` — 1 个

`0x31f3c` `sendHttp`

## 3. 检测逻辑：NativeGatherTest 四个方法

### 3.1 `isRoot` @ `0x320b4`（192 字节，完全解码）

遍历一张 **14 项 `char[14][100]` 表**（基址 `0x793cd`，步长 `0x64`），对每一项拼 `"su"` 再 `fopen(path, "r")`：

拼接方式是 `mov w24, #0x7573`（`0x7573` = 小端 `'s'`、`'u'`）后 `strb wzr, [x8, #2]` 截断，因此探测的是 `<dir>su`。

| # | 路径 | # | 路径 |
| ---: | --- | ---: | --- |
| 0 | `/data/local/` | 7 | `/system/bin/failsafe/` |
| 1 | `/data/local/bin/` | 8 | `/system/sd/xbin/` |
| 2 | `/data/local/xbin/` | 9 | `/system/usr/we-need-root/` |
| 3 | `/sbin/` | 10 | `/system/xbin/` |
| 4 | `/su/bin/` | 11 | `/cache/` |
| 5 | `/system/bin/` | 12 | `/data/` |
| 6 | `/system/bin/.ext/` | 13 | `/dev/` |

`fopen` 成功即 `fclose` 并返回 1。判定只用文件是否存在，不读内容，也不校验 `su` 的 ELF 头。

### 3.2 `isPtrace` @ `0x32360`（72 字节）→ 辅助 `0x19498`（140 字节）

`getpid()` → `sprintf(buf, "proc/%d/status", pid)`（格式串 @ `0x7b923`，注意是相对路径，依赖 `/proc` 已被 bind 到 cwd 或直接以 `proc/…` 打开）→ `fopen(buf,"r")` → 逐行 `fgets(·, 0x100, f)` + `strncmp(line, "TracerPid", 9)`（`0x7b932`）→ 命中后 `%lld` 解析。

判定等价于"`/proc/self/status` 的 `TracerPid` 是否非 0"，即是否处于被 ptrace attach 状态。

### 3.3 `mapsInfo` @ `0x32174`（492 字节）

调用 `0x19020`（内部 `getpid`，读 `/proc/<pid>/maps`）返回一个 3 字节结构，然后 `sprintf(buf, "%d%d%d", b[2], b[1], b[0])`（格式串 @ `0x7c231`，注意**逆序**取字节），随后通过 JNIEnv 调用 `jobject` 上的对象方法（slot `#0x538`）回传字符串，尾部是标准 refcount + 类析构释放模式。

用途是探测 instrumentation/injection 框架注入的匿名可执行映射。

### 3.4 `getProcessName` @ `0x31f80`

读取本进程名，与 `ro.board.platform`、`IS_EMULATOR`（`0x311e0` 内的 `getprop` 键 @ `0x7b117`）等属性一起参与模拟器判定。

### 3.5 无 Frida / 反调试系统调用

全库 `prctl` 只有 1 处（`0xf558`，`PR_SET_NAME`=15，参数 `"fpthread"` @ `0x7aa49`），3 处 `syscall` 全在 `0x3f134`/`0x3f2ac`/`0x3f320` 分配器内且为 `futex`。**没有** `PTRACE_TRACEME` 自陷、没有 `/proc/self/maps` 里的 frida 字符串扫描、没有 `dlopen`/`dlsym`。

Xposed 相关字符串只在 Java 侧判定目录出现：`libsubstrate.so`（`0x7b8ee`）、`libsubstrate-dvm.so`（`0x7b8fe`）、`libxposed_art.so`（`0x7b912`）、`de.robv.android.xposed.XposedHelpers`、`…XposedBridge`（均在 `a.a.a.a.a.c` 的 byte 数组里）。

## 4. 上报通道：`as.xiaohongshu.com`

### 4.1 调用链

`start(J)` @ `0xfa84` 在 `0x101f0`–`0x10208` 创建线程，入口 `0xf51c`：

```
0xf51c  prctl(PR_SET_NAME, "fpthread")
0xf59c  blr [x8,#0x20]                 ; 回调 onStart(long)  → com/xingin/u/p/b
0xf5bc  blr [x8,#0x538]                ; env->GetObjectClass / 取 jobject
0xf600  ...                            ; 取 context、deviceId 等
0xf634  "parse"   (Ljava/lang/String;)Lokhttp3/MediaType;
0xf67c  "create"  (Lokhttp3/MediaType;[B)Lokhttp3/RequestBody;
0xf6a4  "method"  (Ljava/lang/String;Lokhttp3/RequestBody;)Lokhttp3/Request$Builder;
0xf6cc  "url"     (Ljava/lang/String;)Lokhttp3/Request$Builder;
0xf6f0  "build"   ()Lokhttp3/Request;
0xf720  "<init>"  ()V                  ; MultipartBody.Builder
0xf778  "addFormDataPart"              ; (String,String,RequestBody)MultipartBody$Builder
0xf798  "()Lokhttp3/MultipartBody;"
0xf890  "POST"
0xf8f4  "newCall" (Lokhttp3/Request;)Lokhttp3/Call;
0xf930  "execute" ()Lokhttp3/Response;
0xf95c  blr [x8,#0x720]                ; 判定成败
0xf99c  blr x8  (x8 = [x19,#0x18] 或 [x19,#0x20])  ; onReportSuccess / onReportFailed
0xfa0c  blr [x8,#0xb0]                 ; 读取错误消息
```

涉及的 Java 类（reflection `FindClass` 参数，全部明文在 `0x7a808`–`0x7a89c`）：

`okhttp3/MediaType`、`okhttp3/RequestBody`、`okhttp3/Request$Builder`、`okhttp3/OkHttpClient`、`okhttp3/MultipartBody`、`okhttp3/MultipartBody$Builder`、`okhttp3/Call`、`okhttp3/OkHttpClient$Builder`，异常类 `java/net/SocketTimeoutException`、`java/net/ConnectException`。

回调接口 `Lcom/xingin/u/p/b;` @ `0x7aa5b`，方法名表在 `0x7a9e0`–`0x7aa40`：

```
onStart(J)V · onReportStart(J)V · onReportSuccess(J)V · onReportFailed(J,Throwable)V
onCompleted(J)V · onFailed(J,Throwable)V
```

### 4.2 请求格式

```
POST https://as.xiaohongshu.com/api/v1/d/upload
Content-Type: multipart/form-data

  part 名 "file"  /  文件名 "image.jpg"  /  Content-Type image/jpeg
  载荷 = §4.3 变换后的二进制块（16 字节前缀 + XOR 0x70 的 protobuf）
```

`image/jpeg`（`0x7b3c4`）、`file`（`0x7b3cf`）、`image.jpg`（`0x7b3d4`）三个常量分别由 `0xf7d0`、`0xf82c`、`0xf848` 载入。part 名与文件名把二进制块伪装成图片上传，**载荷不是真实图片**。

`addFormDataPart` 在函数内只有一个字符串引用点（`0xf778`，签名 `0x7b34f`），因此分片数量不在静态字符串层面体现；无论一个或多个 part，取名字面量都只有上述三处。

两条错误分支（均在 `start` @ `0xfa84` 内，`isPtrace`/`isRoot` 之外的同级错误处理）：

| 字符串 | 地址 | 载入点 | 含义 |
| --- | --- | --- | --- |
| `Serializing protobuf data failed` | `0x7aa6e` | `0xfc8c` | protobuf 序列化失败 |
| `Transforming data failed.` | `0x7aaa6` | `0x10064` | §4.3 前缀+XOR 变换失败（`malloc` 返回空） |

两者都把消息包进 `XYFpException`（`com.xingin.a.a.f.XYFpException`，构造签名 `(ILjava/lang/String;)V` @ `0x7aa8f`，`<init>` 引用点 `0x100ac`/`0x100b0`）经 `0xf398` 构造后回抛，错误码取 `-0xc8`（`mov w3, #-0xc8` @ `0x100c4`，32 位即 **-200**）。

### 4.3 载荷封装：16 字节前缀 + XOR 0x70

`0xffd4`–`0x10028` 是变换函数的全部内容：

```
0xffd4  malloc(len + 0x1e)                  ; 多分配 30 字节
0xffe8  rand()                              ; 取低 16 位 → R
0xffec  w9 = 0xd92866a1                     ; 前缀魔法常量
0x10008  strb w8, [buf]        , w8 = 2     ; buf[0] = 0x02
0x1000c  stur w9, [buf, #1]                 ; buf[1..4] = a1 66 28 d9
0x10000  strb wzr, [buf, #7]                ; buf[7]  = 0
0xfffc  sturh w0, [buf, #5]                 ; buf[5..6] = R (rand 低 16 位)
0x10004  stp  w22, w22, [buf, #8]           ; buf[8..11] = buf[12..15] = len
0x10028  memcpy(buf + 0x10, protobuf, len)  ; 数据从 offset 16 开始
```

16 字节前缀布局：

| 偏移 | 长度 | 取值 | 含义 |
| ---: | ---: | --- | --- |
| 0 | 1 | `0x02` | 版本/标志 |
| 1 | 4 | `a1 66 28 d9` | 魔术常量（`movk` 立即数 `0xd928`、`mov w9,#0x66a1` 拼出） |
| 5 | 2 | `rand() & 0xffff` | 每请求随机 |
| 7 | 1 | `0x00` | 保留 |
| 8 | 4 | `len` (LE) | 明文长度，出现两次 |
| 12 | 4 | `len` (LE) | 冗余副本 |
| 16 | len | protobuf 字节 | 主体 |

随后 `0x10030`–`0x1015c` 做**全量 XOR `0x70`**：

```
0x1010c  movi v0.16b, #0x70
0x10120  eor  v1.16b, v1.16b, v0.16b        ; 主循环，32 字节/迭代
0x10150  eor  w11, w11, #0x70               ; 尾部余数
```

只变换 `[16, 16+len)` 这一段，即**前缀不参与 XOR**。

`rand()` 没有 `srand()` 调用（全库 `rand` 唯一引用点就是 `0xffe8`），C 库默认种子为 1，因此 `buf[5..6]` 在同一进程内是确定序列——不是密码学随机，只用于打乱静态特征。

**这是全库唯一的常量 XOR**：`movi vN.16b` 带非 0/0xff 立即数的只有 `0x1010c` 这一条，标量 `eor` 带 `#0x70` 也只有 `0x10150` 一处。所以不存在"还有别的加密没找到"的可能。

### 4.4 失败的判定

`0xf964` 的后继分支决定走 `onReportSuccess`（`[x19,#0x18]`）还是 `onReportFailed`（`[x19,#0x20]`），并向 Java 抛 `XYFpException(code, msg)`，`code = -0xc8`。

## 5. 采集数据范围：8 个消息、51 个字段

字段编号从**序列化器**取得（`mov w0, #FIELD_NUMBER` 后调 `0x3bb10` 追加字段），因此是权威值而非推测。子消息类型名为 `fingerprint.android.*`；每个子消息对应的 C++ 序列化入口：

| 子消息 | 序列化入口 | 字段数 |
| --- | --- | ---: |
| `Extension` | `0x21c24` | 4 |
| `JavaRuntime` | `0x22f98` | 2 |
| `TelephonyNetwork` | `0x25444` | 14 |
| `Motion` | `0x270a0` | 1 |
| `State` | `0x28724` | 1 |
| `System` | `0x2bac8` | 20 |
| `App` | `0x2dfd4` | 6 |
| `Model` | `0x2fcdc` | 3 |

### 5.1 逐字段清单（51 个）

| 消息 | 字段 | 编号 |
| --- | --- | ---: |
| `App` | `apk_path` | 5 |
| `App` | `build` | 3 |
| `App` | `channel` | 8 |
| `App` | `identifer` | 1 |
| `App` | `private_file_path` | 4 |
| `App` | `version` | 2 |
| `Extension` | `device_id` | 1 |
| `Extension` | `js_fingerprint` | 4 |
| `Extension` | `session_id` | 3 |
| `Extension` | `shumei_fingerprint` | 2 |
| `JavaRuntime` | `app_list` | 6 |
| `JavaRuntime` | `install_apps` | 1 |
| `Model` | `fid` | 3 |
| `Model` | `name` | 1 |
| `Model` | `version` | 2 |
| `Motion` | `sensor_list` | 1 |
| `State` | `accessibility_enabled_services` | 30 |
| `System` | `android_id` | 30 |
| `System` | `board` | 12 |
| `System` | `boot_loader` | 13 |
| `System` | `brand` | 14 |
| `System` | `cpu_abi` | 6 |
| `System` | `device` | 15 |
| `System` | `display` | 16 |
| `System` | `fingerprint` | 17 |
| `System` | `hardware` | 18 |
| `System` | `host` | 19 |
| `System` | `id` | 22 |
| `System` | `imsi` | 28 |
| `System` | `lang` | 5 |
| `System` | `manufacturer` | 20 |
| `System` | `model` | 11 |
| `System` | `process_name` | 7 |
| `System` | `ro_board_platform` | 32 |
| `System` | `running_process_by_api` | 35 |
| `System` | `running_process_by_command` | 36 |
| `System` | `tags` | 21 |
| `TelephonyNetwork` | `carrier_code` | 12 |
| `TelephonyNetwork` | `carrier_name` | 1 |
| `TelephonyNetwork` | `cell_ip` | 3 |
| `TelephonyNetwork` | `gsm_version_basebrand` | 11 |
| `TelephonyNetwork` | `iccid` | 10 |
| `TelephonyNetwork` | `imei1` | 6 |
| `TelephonyNetwork` | `imei2` | 7 |
| `TelephonyNetwork` | `iso_country_code` | 2 |
| `TelephonyNetwork` | `mei1` | 8 |
| `TelephonyNetwork` | `mei2` | 9 |
| `TelephonyNetwork` | `wap_mac` | 14 |
| `TelephonyNetwork` | `wifi_ip` | 4 |
| `TelephonyNetwork` | `wifi_mac` | 13 |
| `TelephonyNetwork` | `wifi_ssid` | 5 |

### 5.2 字段语义分组

**`Extension`（客户端注入的四元组，非设备采集）**

`device_id`(#1)、`shumei_fingerprint`(#2)、`session_id`(#3)、`js_fingerprint`(#4)。

这四个是**外部注入**的值：`device_id` 来自 App 的 `smId`/`deviceId`，`shumei_fingerprint` 是数美设备指纹（第三方 SDK 生成），`js_fingerprint` 是 §4 里那个 AES 加密的 JS 指纹（`getJsFingerprint` 解密后回填）。也就是说 native 把"别人算好的 ID"一起打包上报，而不只是自己采。

**`System`（20 个，设备与进程）**

构建属性直接对应 `android.os.Build`：`board`(#12)、`boot_loader`(#13)、`brand`(#14)、`device`(#15)、`display`(#16)、`fingerprint`(#17)、`hardware`(#18)、`host`(#19)、`manufacturer`(#20)、`model`(#11)、`tags`(#21)、`id`(#22)。

其余：`lang`(#5)、`cpu_abi`(#6)、`process_name`(#7)、`android_id`(#30)、`imsi`(#28)、`ro_board_platform`(#32，对应 `getprop ro.board.platform`)、`running_process_by_api`(#35)、`running_process_by_command`(#36)。

后两个是**进程枚举**，是"是否运行了检测/逆向工具"的直接输入。

**`TelephonyNetwork`（14 个，SIM 与网络）**

`carrier_name`(#1)、`iso_country_code`(#2)、`cell_ip`(#3)、`wifi_ip`(#4)、`wifi_ssid`(#5)、`imei1`(#6)、`imei2`(#7)、`mei1`(#8)、`mei2`(#9)、`iccid`(#10)、`gsm_version_basebrand`(#11)、`carrier_code`(#12)、`wifi_mac`(#13)、`wap_mac`(#14)。

覆盖全部 SIM 标识（IMEI/MEID/ICCID）与全部网络标识（小区 IP、WiFi IP/SSID/MAC）。**这些在 Android 10+ 已受限**：Java 侧 `getImeiBySlot`/`getMeiIDBySlot` 均先检查 `SDK_INT > 28` 返回 `null`、并检查 `READ_PHONE_STATE`；`getWifiIp`/`getWifiSSid`/`getWAPMacAddress` 在新版本恒返回空串（见 §6）。

**`App`（6 个，安装体特征）**

`identifer`(#1)、`version`(#2)、`build`(#3)、`private_file_path`(#4)、`apk_path`(#5)、`channel`(#8)。

`private_file_path` 与 `apk_path` 暴露安装路径；配合 `getApkCRC`（APK 内全部 ZipEntry CRC 累加，`com.xingin.u.p.c`）与 `getAPkSignature`（签名 `hashCode` 累加）构成**篡改/重打包检测**。

注意字段名 `identifer` 的拼写错误在样本里就是如此，不是本文笔误。

**`JavaRuntime`（2 个）** — `install_apps`(#1)、`app_list`(#6)：已安装应用列表。

**`Model`（3 个）** — `name`(#1)、`version`(#2)、`fid`(#3)：设备指纹模型标识。

**`State`（1 个）** — `accessibility_enabled_services`(#30)：无障碍服务列表，**是自动化/群控工具的核心检测面**。

**`Motion`（1 个）** — `sensor_list`(#1)：传感器列表（模拟器通常与实际机型不符）。

### 5.3 结构化数据的格式串

`ComplexStructGatherTest` 的 6 个方法不走 protobuf 字段，而是格式化成字符串，格式串全部明文在 `.rodata`：

| 方法 | 格式串 |
| --- | --- |
| `getDisplayMetrics` | `width:%d,height:%d,density:%f` |
| `getMemoryInfo` | `availMem:%d,totalMem:%d,threshold:%d,isLowMemory:%s` |
| `getBatteryInfo` | `chargeCounter:%d,chargeCurrentAverage:%d,chargeCurrentNow:%d,chargeCapacity:%d,status:%d,isCharging:%s` |
| `getAccelerometerInfo`/`getGyroscopeInfo` | `x:%f,y:%f,z:%f,timestamp:%d,desc:%d` |

C++ 类型名亦明文：`fingerprint::data::{MemoryInfo,JavaRuntime,MotionEvent,DisplayMetrics,TelephonyNetwork,App,State,Motion,System,Battery,Location,Extension}`、`fingerprint::native::maps_so_info`、`fingerprint::android::{JavaRuntime,TelephonyNetwork,App,Model,State,Motion,System,Extension}`。

## 6. Java 层实现：`com.xingin.u.p.*`

native 的 41 个 `CallJavaJniTest` 方法名与 Java 类 **`com.xingin.u.p.c`** 的方法一一对应（`classes17.dex`；该类在既有 jadx 全量导出中缺失，本次单独导出）。

### 6.1 权限门控

几乎所有敏感取值都先过 `p.userGranted()`（`o1b.b.a()`）：

| 方法 | 门控 | 当前版本实际返回值 |
| --- | --- | --- |
| `getImei1/2`、`getMei1/2` | `userGranted()` + `SDK_INT <= 28` + `READ_PHONE_STATE` | Android 10+ 恒为 `null` |
| `getImsi` | `userGranted()` + `READ_PHONE_STATE` | 受限 |
| `getInstallApps` | `userGranted()` | 已装应用列表 |
| `getWifiIp` / `getWifiSSid` / `getWAPMacAddress` | 无 | **本库调用路径恒返回 `"<absent>"`**（WLAN MAC 另有 `com.xingin.utils.core.q.i()` 真实读取路径，见 [privacy-and-permissions.md](../privacy-and-permissions.md) §6.1） |
| `getTotalExternalStorageSize` | `SDK_INT < 30` + `READ_EXTERNAL_STORAGE` | Android 11+ 返回 0 |
| `getWifi()` | — | **空方法体** |

`getWifiIp`/`getWifiSSid`/`getWAPMacAddress` 直接 `return a.a.b.a.a.a;`，该常量初值为字面量 `"<absent>"`，即这三个访问器在本库调用路径上恒返回占位串，字段仍留在 protobuf schema 与采集清单内。**WLAN MAC 的真实读取由另一条独立路径承担**：`com.xingin.utils.core.q.i()`（注册 `80600`/`80601`）按 `WifiInfo → wlan0 NetworkInterface → getByInetAddress → /sys/class/net/<if>/address` 回退，并以 `mac` 键写入账号公共参数，见 [privacy-and-permissions.md](../privacy-and-permissions.md) §6.1。

### 6.2 检测项映射

| 检测 | Java 实现 |
| --- | --- |
| Xposed | `existXposed()` → `o1b.c.a()`：`ClassLoader.getSystemClassLoader().loadClass("de.robv.android.xposed.XposedHelpers")`，成功即真 |
| Xposed（native 侧结果） | `getCheckingXposedResult()` |
| VirtualApp/多开 | `getCheckingVirtualAppResult()` |
| root | native `isRoot`（14 路径）；Java 另有 `io.sentry.core.k0`（崩溃上报 `isRooted`）与 `aqc.l`（8 路径数组） |
| ptrace | native `isPtrace` |
| 进程列表 | `getRunningProcessListByCommand()` / `…BySdkApi()` → `a.a.a.a.a.n.b.b()` / `.a()`，序列化为 `;` 分隔 |
| 无障碍 | `getAccessibilityStatus()` → `new a.a.a.a.a.n.a().b()`；`getEnabledAccessibilityServices()` |
| 模拟器 | `getBuildIsEmulator` → `getprop IS_EMULATOR`；`ro.board.platform` |
| 篡改 | `getApkCRC()`（ZipEntry CRC 累加）、`getApkSignature()`（签名 hashCode 累加）、`getApkPath()` |
| 多用户 | `os.r0`（"MultiUserManager"）反射 `UserHandle.myUserId()` |

### 6.3 JS 指纹的解密侧

`getJsFingerprint()` 是 §4 的**读回**路径：

```java
IvParameterSpec iv = pt.c.a;                      // 16 字节 IV
Cipher cipher = Cipher.getInstance(a.a.a.a.a.c.b());   // "AES/CBC/PKCS5Padding"
cipher.init(DECRYPT_MODE, pt.c.b, pt.c.a);
return new String(cipher.doFinal(Base64.decode(
        context.getSharedPreferences("f", 0).getString("jsf", ""), 0)));
```

写入侧在 `a.a.a.a.a.p.a$b.writeJsFp(String)`（隐藏 WebView 的 JS 桥）：

```java
cipher.init(ENCRYPT_MODE, pt.c.b, pt.c.a);
prefs("f").edit().putString("jsf", Base64.encode(cipher.doFinal(str.getBytes()))).commit();
prefs("jsfsts").edit().putLong("jsfsts", System.currentTimeMillis()).commit();
// 随后删除 JS 文件（含 AlbumDeleteLancet 钩子）并广播 XhsJsService.stop_myself
```

密钥与 IV 硬编码在 `pt.c`（`f306962b` / `f306961a`）与 `a.a.a.a.a.p.a`（`f1103i` / `f1102h`）两处重复定义，算法串 `AES/CBC/PKCS5Padding` 以 byte 数组藏在 `a.a.a.a.a.c.f1069c`，SP 文件名 `f`、键 `jsf`/`jsfsts`/`jsfscapability` 同样以 byte 数组定义（`c.e()`、`c.c()`、`c.d()`）。

即：**JS 指纹的存储加密已完全确定**——AES-128-CBC/PKCS5Padding，密钥与 IV 均为样本内硬编码常量，写入/读取两侧同源。这是本地存储保护，不是服务端绑定密钥，因此同设备上任何能读到 SP 的代码都能解密。

## 7. 调度层与生命周期

### 7.1 `pt.a`（classes5.dex）

- `public static final FingerPrintJni f306944a = new FingerPrintJni();` — 单例。
- 库名以 byte 数组给出：`{120, 121, 97, 115, 102}` = `"xyasf"` → `System.loadLibrary("xyasf")`。
- 限流：`public static final long f306951h = 600000;` — 600 000 ms = **10 分钟**。
- 线程：`virposd`（`qt.a`，uid → `u0_a%d` 换算并从 `/proc` 解析运行身份）、`fpwdog`（`qt.b`，3 000 ms 看门狗）、`fp`（`pt.a$C4037a`，等待 10 000 ms 后调用 `start()`）。

节奏：`init` → 等 10 s → `start` → 上报；`fpwdog` 每 3 s 检查；两次完整上报之间至少间隔 10 分钟。

### 7.2 `com.xingin.u.p.p`（静态门面）

`deviceId`/`sid`/`smId`/`getChannel`/`getLocationInfo`/`getSecretAppList`/`getWifiMac`/`installedApps`/`devOpenedCount`/`userGranted` 全部转发到接口 `o1b.b` 的 `impl` 字段；`setProvider(o1b.b)` 注入实现。`getWifiMac` 直接返回常量 `a.a.b.a.a.f1110a`（初值 `"<absent>"`）。

### 7.3 与 App 的耦合点

`ContextHolder`（`w42.a`）提供 `getPackageCodePath()`、`getPackageManager()` 等；Shield 启动任务 `r3.g` 把 `deviceId`/`appId` 注入 `ContextHolder`，供签名组件读取。即**设备指纹的结果同时被签名子系统消费**，不只是上报。

## 8. 完整性校验：标准 MD5

`libxyasf.so` 导出完整 MD5 实现，逐项核对通过：

| 导出 | 地址 | 大小 |
| --- | --- | --- |
| `MD5` | `0x3cf34` | — |
| `MD5_Update` | `0x3cfd4` | — |
| `MD5_Transform` | `0x3dae4` | — |
| `MD5_Final` | `0x3daec` | 352 B |
| `MD5_Init` | `0x3dc4c` | — |
| `md5_block_data_order` | `0x3d0e0` | 2 564 B |
| `XYSSL_cleanse` | `0x3dc88` | — |

核对结果：

- **IV** @ `0x7d5e0` = `0123456789abcdeffedcba9876543210`，即 `0x67452301`/`0xefcdab89`/`0x98badcfe`/`0x10325476` ✅ 标准。
- **64/64 轮常量**全部以立即数形式出现在 `md5_block_data_order` 中，为标准 T 表 ✅。最后一个 `T[10] = 0xffff5bb1` 以**负立即数** `mov w10, #-0xa44f` @ `0x3d2ec` 编码——这正是朴素的立即数扫描只找到 63 个的原因。
- **64 条 `ror`** 的移位量集合为 `{9,10,11,12,15,16,17,18,20,21,22,23,25,26,27,28}`，恰为 `{32 − S}`，`S = {7,12,17,22,5,9,14,20,4,11,16,23,6,10,15,21}`，每个出现 4 次 ✅ 标准。
- 全库**不存在** T 表的原始字节副本（LE、BE、单字节 XOR 变体均已排除）。

十六进制表 `0123456789abcdef` @ `0x78fc0`。结论：**该 MD5 是原版 vanilla 实现，无任何修改**。

## 9. 未闭环项

| 项 | 未闭环的具体环节 | 原因 |
| --- | --- | --- |
| 根消息字段编号 | 8 个子消息在父消息中的字段编号 | 父消息走的是一张**运行时计算的 type-info 表**（分发循环 @ `0x33990` 用 `ldr w10,[x9]; ldr w10,[x25,x10]` 从类型描述符间接取号），编号不在静态数据里。子消息内部 51 个字段编号**已全部取得** |
| `0x50010` 转移选择谓词 | **已闭环**（412 site/767 边/417 目标；262 FIXED/69 BASE/18 DATA） | 见 [crypto.md](crypto.md) §4.6.4 |
| `libtinyd.so` IPC | 通道协议 | 与本文无关的独立遗留项 |
| 服务端消费逻辑 | `as.xiaohongshu.com` 如何用这 51 个字段做判定 | 服务端逻辑，客户端不可见 |

**已闭环项**：

| 项 | 状态 |
| --- | --- |
| native 侧采集点逐条清单 | **已完成**：79 静态导出 + 3 动态注册，**采集面 51 字段**（8 子消息），检测逻辑 4 个 native 方法逐条解码 |
| JS 指纹硬编码密钥 | **已确定**：AES-128-CBC/PKCS5Padding，密钥+IV 双处硬编码，读写同源 |
| `libxyasf.so` 是否有未发现的加密 | **已排除**：全库仅 1 处常量 XOR（`0x70`），无字符串加密，无自定义密码学原语 |
