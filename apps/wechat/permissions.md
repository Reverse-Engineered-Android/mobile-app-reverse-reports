# Android 权限、导出组件与提权审计

## 结论

对微信 `8.0.78` / `versionCode=3180` 的 `AndroidManifest.xml`、**全部 6 个无
Manifest 权限的导出 Provider** 的反编译源码、现有 smali/ELF 证据，以及 2026-10-02
在真机上采集的运行时权限/AppOps 只读快照进行了审计：

1. **未发现本地提权漏洞。** 没有发现 root exploit、SELinux 绕过、任意代码
   执行、以 `system`/`root` UID 运行，或把普通应用变成特权应用的可复现链路。
   越权方面确认了 1 处受限的 Provider 未鉴权入口，见第 4 点；它不构成提权。
2. **导出组件攻击面较大，但导出不等于越权。** Manifest 声明 59 个导出
   组件，其中 44 个没有 Manifest 级组件权限；这说明需要逐组件检查运行时
   身份、URI、Intent 和签名校验，不能仅凭 `exported=true` 判定漏洞。
3. **两个高风险 Provider 的重点结论均为“有防护、未证明可绕过”。**
   `ShareableChatRecordsProvider` 在 `openFile()` 中校验调用方 UID、单一
   包名、签名证书摘要、会话与路径，并且 `query/insert/update/delete`
   返回空结果或 0；`WXCommProvider` 会读取 calling package/UID，并在多个
   分支使用包名、签名和 URI matcher。没有静态证据证明这些检查可绕过。
4. **其余 4 个无权限 Provider 也都有调用方或作用域约束；唯一例外是
   `XWebCoreContentProvider`，它是本轮唯一确认缺少调用方鉴权的入口。**
   它的 `openFile()` 把调用方 UID 只用于日志，真正的 `c()` 判断传入的是
   **Provider 自身包名**，因此恒为真。可读文件被 `filelist.config` 键集合
   限制在 XWeb 引擎版本目录内，并以只读方式打开——属于**已确认的越权读取面
   （信息泄露，限于 XWeb 资源）**，不是提权，也不涉及用户数据。
5. **Manifest 请求宽权限，但请求权限不等于运行时已授权或已经读取。**
   真机快照显示 10 项用户敏感权限已授予、6 项未授予；快照只证明**当前授权
   状态**，不证明这些权限已被调用过，更不证明数据已上传。

## Manifest 概况

| 项目 | 结果 |
| --- | --- |
| 版本 | `8.0.78` / `versionCode=3180` |
| `uses-permission` 条目 | 100 |
| 敏感权限子集 | 38 |
| 自定义权限声明 | 18 |
| 保护级别 | `signature` 13、`signatureOrSystem` 3、`normal` 2 |
| 导出组件 | 59 |
| 导出但无组件权限 | 44 |
| 导出 Provider | 15，其中 6 个无组件权限、3 个 `grantUriPermissions=true` |
| 应用级安全属性 | `allowBackup=false`、`usesCleartextTraffic=true`、`requestLegacyExternalStorage=true` |

`networkSecurityConfig` 的基础配置也为 `cleartextTrafficPermitted="true"`。
该配置只证明应用允许明文连接；本轮未发现实际外发明文凭据的证据，因此不作
直接等同于凭据泄露或权限提升。

敏感请求权限覆盖位置、蓝牙/Nearby、相机、麦克风、联系人、媒体、存储、
通知、前台服务、任务/覆盖窗口、屏幕捕获检测、生物识别和人脸识别等能力。
其中 Android 高风险权限的实际授权状态由本文“真机运行时快照”一节回答，
调用时间线与是否上传仍无法由静态或授权快照回答。

## 导出组件

| 类型 | 导出数 | 无组件权限 |
| --- | ---: | ---: |
| Activity | 29 | 29 |
| Provider | 15 | 6 |
| Receiver | 6 | 3 |
| Service | 9 | 6 |

29 个导出 Activity 大多是分享、支付回调、深链、快捷方式、WebView、恢复
和入口 Activity；这是 Android 应用常见的集成面。当前证据只证明它们可被
外部 Intent 触达，没有证明任意 Intent 都能读取聊天数据、改变账户状态或
绕过服务端鉴权。

### 重点 Provider

| Provider | Manifest 防护 | 静态检查结果 |
| --- | --- | --- |
| `ShareableChatRecordsProvider` | `normal` 自定义读取权限 + `grantUriPermissions=true` | `openFile()` 先检查远端 kill switch，再用 `Binder.getCallingUid()` 取得调用包，要求 UID 只映射到一个包，并验证包签名 SHA-256；随后校验 authority、sessionId、action 和路径。`query/insert/update/delete` 返回空/0。 |
| `WXCommProvider` | 无组件权限 | `query()` 等待初始化后解析 URI matcher，再取得 calling package/UID；分支中读取包名、签名，并通过 `ExtOpenApiCallEvent` 等受控分发。未发现无条件返回聊天、支付或账户数据的路径。 |
| `ExtControlProvider*` | `signatureOrSystem` + `signature` 读写权限 | Android 权限层面限制为同签名或系统应用。 |
| `WaidProvider` | `signature` 写权限 | Manifest 层面限制为同签名应用。 |
| `WeSeeProvider`（视频背景） | 无组件权限 | `query()/delete()` 先走 `a()`：`Binder.getCallingUid()` → `getPackagesForUid()` → 取第 1 个包的签名 → 与硬编码 32 位十六进制签名 MD5 常量比较，再要求内核已初始化并检查内部表的 `time` 窗口。`insert()/update()` 返回 null/0。**调用方鉴权存在**；jadx 未能完全还原该布尔分支的极性，故不宣称其正确性已闭环。 |
| `GameResourceDownloadProvider` | 无组件权限 + `grantUriPermissions=true` | `query()/delete()` 都先取 `getCallingPackage()`，再以该包名为键查 `t1(callingPackage)`；返回列只有该包自己的下载记录（文件名、MD5、大小、状态、`fileUri`），随后只把读取权限授给调用方自己。`a()` 只校验包名非空 + 内核初始化，**没有调用方白名单**，但数据按框架校验过的 calling package 隔离。 |
| `OVCInfoProvider` | 无组件权限 + `grantUriPermissions=true` | `query()` 只解析 `sdk_version` 并做 `0x20000`–`0x2FFFF` 范围校验，返回单列 `errorCode` 的 `MatrixCursor`；`insert()/delete()/update()` 返回 null/0。**不读取用户数据**。 |
| `NormsgInfoProvider` | 无组件权限 | `query()/insert()/update()/delete()` 全部返回 null/0。只有 `call("m0", …)` 有效，且先 `getCallingPackage()` 与解密后的期望包名比较，不匹配即返回 null。**有调用方校验**。 |
| `XWebCoreContentProvider` | 无组件权限 | `openFile()` 解析 `…/<callerName>/<2>/<version>/<file>`；`Binder.getCallingUid()` → `getPackagesForUid()` 的结果**只写入日志/上报字段**，随后 `c(context.getPackageName())` 判断的是 Provider 自身包名，恒为真。`insert()` 的包名判断同样是自检，恒为真。**已确认缺少调用方鉴权。** |

### `XWebCoreContentProvider` 缺少调用方鉴权的边界

这是本轮唯一能静态确认的越权面，但影响被三层结构限制：

1. URI 必须满足 `opType=2` 且为四段式 `<callerName>/<2>/<version>/<file>`；
2. `<file>` 必须是该 XWeb 版本目录下 `filelist.config` 里已存在的键，
   `base.apk` 还被单独映射到版本目录下的 `apk/base.apk`，未命中即返回 `-7`；
3. 返回句柄一律以只读方式打开，且路径解析落在应用私有目录内。

因此**未发现**：写入任意文件、路径穿越到应用私有目录之外、读取聊天/账号/
数据库，或获得更高 UID。`insert()` 分支允许任意应用注入 15625/15626 两类
上报字符串，属于上报数据污染，不是提权。

本轮**没有**从第三方 UID 发起实际调用验证可达性——该 Provider 会把调用方
写入日志与上报，属于可被发现的动态行为，按“不可被检测”的约束排除。

## 提权与越权边界

### 已排除

- 没有发现可复现的本地提权原语或已执行的注入、Hook、ptrace、`su` 提权。
- `ShareableChatRecordsProvider` 不是“任何应用都能读聊天记录”的简单导出
  Provider；现有反编译代码包含调用方与文件路径校验。
- 没有静态证据证明能通过导出 Activity/Service/Provider 读取其他用户会话、
  冒充微信进程或获得 `system` 权限。
- 6 个无 Manifest 权限的 Provider 中，5 个存在调用方身份、签名或作用域约束；
  不存在“任意应用读取任意聊天记录”的无条件路径。
- `GameResourceDownloadProvider` 与 `OVCInfoProvider` 虽带
  `grantUriPermissions=true`，但分别只对调用方自己的下载记录授予只读 URI、
  只返回一个版本号错误码，没有扩大到跨应用数据。

### 已确认的越权面

- `XWebCoreContentProvider` 的 `openFile()` 与 `insert()` 不校验调用方身份，
  只校验自身包名（恒真）；可读范围被 `filelist.config` 限定为 XWeb 引擎
  版本目录内的只读资源，可写范围仅为两个固定上报字段。
- 结论定级：**越权读取/写入面已静态确认；提权未发现；用户数据未涉及；
  动态可达性未验证（有意不做，避免被应用记录）。**

### 验证边界

- `XWebCoreContentProvider` 在第三方 UID 下的真实可达性与 `filelist.config`
  实际键集合；`WeSeeProvider` 被 jadx 判断为真/假的分支极性。
- 29 个导出 Activity 对 Intent extra、`Intent.setData()`、pending intent 和
  回调来源的校验。
- 服务端对第三方调用、支付回调和 open API 的授权状态。
- 危险权限授予后实际 API 调用是否发生在用户同意之后（当前只拿到了授权
  状态快照，没拿到调用时间线）。

上述项目属于潜在攻击面，最终结论是本轮唯一已静态确认的
问题点是 `XWebCoreContentProvider` 未鉴权入口。最终结论为：
**本轮确认 1 处越权读取/上报注入面（`XWebCoreContentProvider`，影响限于
XWeb 资源与上报字段），未发现本地提权，也未发现读取其他用户会话的路径。**

## 真机运行时快照

| 项目 | 结果 |
| --- | --- |
| 系统 | Android 16（API 36），安全补丁 `2025-11-01` |
| 版本 | `8.0.78` / `versionCode=3180`，`minSdk=24`、`targetSdk=34` |
| `base.apk` | 280,614,450 字节，SHA-256 与静态样本完全一致 |
| 危险权限已授予 | `ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`CAMERA`、`RECORD_AUDIO`、`READ_MEDIA_VISUAL_USER_SELECTED`、`NEARBY_WIFI_DEVICES`、`BLUETOOTH_CONNECT`、`BLUETOOTH_ADVERTISE`、`BLUETOOTH_SCAN`、`POST_NOTIFICATIONS` |
| 危险权限未授予 | `READ_CONTACTS`、`READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_EXTERNAL_STORAGE`、`ACTIVITY_RECOGNITION`、`ACCESS_MEDIA_LOCATION` |
| AppOps `foreground` | 位置（粗/精）、相机、麦克风、剪贴板读写、媒体音量 |
| AppOps `ignore` | 联系人、通话/日历/短信、电话状态与号码、外部存储、GET_ACCOUNTS、历史媒体、媒体位置、活动识别等 |

全部已授予项带 `USER_SET`；`READ_CONTACTS`/`ACTIVITY_RECOGNITION`/
`ACCESS_MEDIA_LOCATION` 无 `USER_SET`（用户尚未选择）。位置、相机、麦克风、
剪贴板被限制在 `foreground`，方向与“敏感权限不默认开启”的政策一致。

**授权状态 ≠ 已调用 ≠ 已上传。** AppOps 的 `time=`/`rejectTime=` 是读取时刻
的相对增量，采集中同一字段在两次相隔约 15 秒的只读读取间就发生位移，故
不作为微信采集证据。完整方法、边界与限制见
[`evidence/phone-runtime.md`](evidence/phone-runtime.md)。

## 复现

```bash
jadx --no-src -d decoded base.apk
jadx --no-src -d provider-src base.apk <provider-fqcn>
jadx -r --show-bad-code --comments-level debug \
  --single-class <provider-fqcn> --single-class-output out.java base.apk
# 从 decoded/resources/AndroidManifest.xml 按 exported、permission、
# grantUriPermissions、uses-permission 和自定义 permission 聚合。
# 真机侧仅执行只读的 dumpsys/appops/pidof 与 procfs 进程根视图
# （/proc/<pid> + root/<...>）摘要，
# 不注入、不 Hook、不修改权限状态、不从第三方 UID 调用导出组件。
```

公开证据索引见 [evidence.md](evidence.md)；不发布原始 APK、完整 DEX、
签名值、真实 URI、调用方包名或网络负载。
