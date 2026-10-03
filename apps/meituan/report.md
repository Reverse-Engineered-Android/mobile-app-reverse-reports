# 美团 Android 12.66.404 逆向研究报告

## 1. 最终结论

### 1.1 样本

| 项目 | 值 |
|---|---|
| 包名 | `com.sankuai.meituan` |
| versionName / versionCode | `12.66.404` / `1200660404` |
| minSdk / targetSdk | `21` / `30` |
| APK SHA-256 | `06df08431653502ef9988b7f951dfcfdcdc0758eccbb54aa0fe1cda4e423e8ef` |
| APK 字节数 | `91,327,888` |
| DEX 源文件 | 70,519 |
| 声明权限 | 147（均唯一） |
| 组件总数 | 697 |
| 显式 `android:exported=true` | 265 |
| 未声明 exported 但含 intent-filter | 68 |
| provider | 35，其中 5 个显式导出 |
| ARM64 native 库 | 67 |
| JITX/日志 DEX：`libmtguard_log.so` | 45 类，单独 JADX 成功还原 |
| JADX 残留方法 | 26 文件 / 28 个唯一签名，全部归类 |

JADX `--show-bad-code` 后仍有 50 个还原错误，落在 26 个源文件、28 个唯一
`Method not decompiled` 签名上。全部 28 个已逐一用 `--single-class
--comments-level debug` dump 或源码上下文归类（[evidence.md](evidence.md) §5.2）：
`SecureTools.compress` 是 GZIP，`sankuai.common.utils.b.a` 是 Base64+gzip，
Cronet 拦截器是传输切换，passport `t0.l`、train `deviceinfo.a`、alipay
device/base64、Harmony 元数据等均为埋点/编码/元数据/UI/解析，没有未解释的
密码学实现。每个加密用途的算法/输入/输出见 [network.md](network.md) §7 与
[evidence.md](evidence.md) §5。

### 1.2 网络与协议

应用以 Retrofit/OkHttp 和自研 NVNetwork 两套入口汇入同一签名拦截器族。
关键请求签名头为 `mtgsig`：`CommonCandyInterceptor` 把 method、最终 URI、
body、content-type 和原始 header 收集成 `CandyBaseMaterial`，再进入
`ShellBridge.main3(120, …)`；POST body 会被完整缓存后参与计算，返回的新
URI/header 会替换原请求。旧路径 `gmtkby` 对 query/body 做百分号编码与排序，
body 最多截取 16,200 字节，再调用命令 `2`，只把结果写入 `mtgsig`。

DFP 风控上报固定到 `https://appsec-mobile.meituan.com`，路径为：

| 路径 | 用途 |
|---|---|
| `/api/latte` | DFPID |
| `/api/ristretto` | XID |
| `/api/americano` | 设备信息 |
| `/api/espresso` | 心跳 |
| `/api/cappuccino` | mini-fama |
| `/api/affogato` | 生物/设备证据 |

这六个端点使用 14 个 SHA-256 SubjectPublicKeyInfo pin；任一不匹配都会把
证书链写入 `Ok3NetworkInterceptor.MITM_INFO`、上报事件 `50` / code `303` /
key `n/m/c`，并在本地开关开启时取消请求。

### 1.3 认证

登录不是一个 token 构造，而是多步挑战：

1. `POST v2/account/mobilelogincode` 或 `v3/account/mobileloginapply`
   获取验证码/认证票据，请求含 `encryptMobile`、`verifyLevel`、
   `smsVerifyLevel`、`poiid`、`specialRiskCode`、`fingerprint`、`uuid`、
   `login_auth_ticket/confirm`。
2. `POST v2/account/mobilelogin`、`v3/account/mobilelogin` 或
   `v7/account/login` 以验证码、`requestCode`、Yoda `verifyResponseCode`
   和 `fingerprint` 完成登录。
3. 登录成功返回 `User`，业务侧通过 `UserCenter.getToken()` 复用 token；
   大量接口还把 `userid/token` 显式放进 query 或 form。
4. `POST v1/account/refreshtoken` 携带旧 `token`、`fingerprint`、
   `login_auth_ticket` 与 `need_auth_ticket` 刷新。
5. `account/auth/ticket` 用 `client_id`、PKCE `code_challenge`、`token`
   换 `TicketData`；第三方登录走 `thirdlogin/commonlogin/...` 并携带
   `accessTokensJson/code`、`fingerprint`、`uuid`。

客户端静态证据不能还原服务端会话有效期；但登录、刷新、PKCE、OAuth、
Yoda 校验和身份挑战的字段闭环已给出。

### 1.4 上传下载范围

- 文件上传统一经过 `extrastorage/new/{bucket}` 或 `extrastorage/{bucket}`，
  multipart part 为实际文件；新桶路径用 `token` + `client-id`，旧桶路径用
  `time` + `Authorization`。
- 女神/地图图片上传使用 `PUT @Url` 或同一对象存储路径，仅含文件 part 与
  鉴权头。
- 用户头像调用 `POST /user/settings`，为 `PartMap` + 两个 `Part`。
- 支付银行卡图片调用 `POST /hellopay/uploadcardimg`，并附
  `nb_fingerprint`；支付密码/指纹验证只传密码或挑战结果，不把明文支付
  密码写入日志的代码路径未被发现。
- 设备指纹端点发送的是 native 生成的 byte[]，Java 层明确可见 JSON/text
  content-type 和 URL，不能由 DEX 单独声称明文字段全集；其采集入口由
  `DFPInfoProvider`、`AppInfoWorker`、`EnvInfoWorker` 和 MTGuard 命令限定。

### 1.5 风控

MTGuard 命令号构成客户端风险判定面：Root `15`、模拟器 `16`、恶意软件
`17`、代理 `18`、沙箱/远控 `19`、Hook `20`、调试 `21`、摄像头劫持 `22`、
虚拟定位 `23`、黑产系统 `24`、签名完整性 `10`、无障碍 `101`、
UIAutomator 点击计数 `11`、指纹 `105`、设备信息 `103`、上传 `110`。

Yoda 行为 token 不是单一 Root 位图，而是把这些结果和触摸/键盘/手势时间序列
放进同一个签名 map：`isEmu`、`isRoot`、`hasMalware`、`isDarkSystem`、
`isVirtualLocation`、`isRemoteCall`、`isSigCheckOK`、`inSandBox`、`isHook`、
`isDebug`、`isProxy`、`isCameraHack`。因此“隐藏 su 路径”只能覆盖命令 15，
不能覆盖其余信号。

`MTGuard.java` 的 33 个命令调用点均给出方法声明行与 `ShellBridge.main3`
调用行（[risk.md](risk.md) §2.1），例如 `isRoot` (629) → 636 → 15、
`isSigCheckOK` (645) → 652 → 10、`isAccessibilityEnable` (500) → 508 → 101、
`uiAutomatorClickCount` (1132) → 1140 → 11、`upload` (1148) → 1165 → 110、
`prepareForSo` (759) → 820 → 64。`ShellBridge.java:64-75` 第 71 行委托 native
`main`；`libmtguard_log.so`（实为 DEX）中的 `MainBridge.java:121` 是该 native
方法声明，`main2` 的命令 switch 分别返回 `"6.7.15"`、传感器厂商/OAID/MITM 信息、
`getRunningAppProcesses()` 等运行时数据。

预加载层的封装与校验契约已完整刻画：16 字节版本头（`1.1.4`/`1.1.8`/`1.1.1`）+
16 字节对齐不透明正文；Java `shell.IIVTQYOSF` 常量 `FLWMEVUMVC="6.7.15"`、
`BRFI=6071500`、`FSGIUFGOU={libmtguard_1,2,3.so, libmtguard_log.so}`；
`MTGuard.prepareForSo` (759-936) 经命令 64 后从 APK ZIP 复制这 4 个条目；
`utils/mtguard.IIVTQYOSF.DNFBGIX` 用 `RSA/ECB/NoPadding`
（模数 `D37E339A…72A6CB`、指数 `10001`）校验 MD5 与 ZIP comment；
`libmtguard.so` 的 `dlopen@plt` 唯一调用点 `0x77f38` 以 `RTLD_NOW` 加载
16 字节表项。只读设备检查（PID 34151 的 `/proc/<pid>/maps`）显示该进程只映射
`libmtguard.so` 与 `libmtguard_log.so`，`libmtguard_1/2/3.so` 从未作为 ELF
映射，guard 目标目录 `…/m/64` 为空，`files/.0852110f868f8a20` 不存在——
这三个文件是加密预加载载荷，容器格式、校验与加载路径已完整描述，在本版本中
不执行，故不作为本 APK 的可执行风控逻辑保留未分析代码
（[evidence.md](evidence.md) §3.2-§3.3）。

加密用途闭包（[evidence.md](evidence.md) §5）给出具体字节格式：NVNetwork
外层包 `FF 01 00 <flag> <secure> <totalLength:int32> <noSecureLength:uint16> ||
payload || encryptedBlock`，`encryptedBlock = prefixLength:int32 || prefix || source`；
HMAC-SHA256 以 `encriptData.f12893b + secureProtocolData.id` 为密钥，
Base64 写入 JSON `"h"`、`"z"` 写入 zip 模式；`tool/c.java` 用 `DES`，`tool/f.java`
用 `SHA1WithRSA` 验签；`SecureTools.compress` 是 `GZIPOutputStream` 封装。

### 1.6 权限、越权、提权与告知

- **越权（组件/IPC）**：发现 265 个显式导出组件和 68 个靠 intent-filter
  默认导出的组件；5 个导出 provider 中没有普通签名权限保护。是否可被恶意
  应用读写取决于 URI 路径、权限校验和 provider 实现，因此报告列出攻击面但
  不把“导出”本身写成已复现的数据泄露。
- **提权**：manifest 声明 `REQUEST_INSTALL_PACKAGES`，代码包含安装/升级
  能力；未发现以系统签名或 root 语义获得更高 UID 的客户端代码。
- **未经告知采集**：首启隐私对话框同意后才把 `is_privacy_mode` 从受限态
  切出，并写 `state=true`；隐私态下 MTGuard 初始化为 1、传感器代理直接返回、
  DFP/OneID/遥测有显式闸门。故核心采集有客户端告知闸门，但导出组件、WebView
  bridge 和安装授权仍形成需要独立审计的面。
- **超范围**：声明了后台定位、日历、联系人、相机、录音、外部存储等危险权限；
  代码中能对应到日历事件、联系人选择、媒体上传和位置查询。静态结论是“具备
  访问能力”，不等同于每次会话均已采集或上传。

## 2. 结论矩阵

| 要求 | 结论 | 主要证据 |
|---|---|---|
| 主要网络流程 | 闭环 | Retrofit/OkHttp/NV、WebView/MRN、DFP、对象存储 |
| 协议具体格式 | 闭环到客户端可见字节 | `mtgsig` 收集面、旧签名输入、DFP content-type、multipart |
| 认证机制 | 接口/字段/挑战闭环 | `AccountApi`、`OpenApi`、`UserCenter` |
| 上传下载范围 | 逐类别闭环 | `transfer.md` |
| 风控代码 | Java 33 个命令调用点 + `MainBridge` switch + Yoda + pin 全部闭环；预加载载荷契约与运行态边界闭环 | `risk.md` §2、§11 |
| 加密用途 | 所有出现用途均有具体字节格式或算法边界；28 个 JADX 残留逐一归类 | `evidence.md` §5、§5.2 |
| 只读设备数据库验证 | 实际 schema、落盘格式与运行态文件闭环 | `evidence.md` §8 |
| 支付接口 | 静态字段闭环，未测试 | `network.md` §6 |
| 周边餐厅/项目/评价 | 静态字段闭环，未测试 | `network.md` §5 |
| 越权/提权 | 攻击面和权限保护闭环 | `permissions.md` |
| 告知/超范围 | 同意闸门、权限与采集条件闭环 | `privacy.md` |

## 3. 最终判断

美团 12.66.404 是一套以 MTGuard/DFP/Yoda 为核心的多层风控客户端：native
负责环境判定与签名，DEX 负责行为聚合、端点、验证跳转和服务端挑战，证书 pin
与代理检测保护关键指纹上报。其采集面明显大于一个普通生活服务 App，但静态
证据显示主要危险权限和画像上传均受用途分支或隐私态约束。未发现以客户端静态
代码直接读取他人账号数据或提权到更高 UID 的闭环；最实质的安全问题是大量导出
组件、5 个未加普通权限的导出 provider、targetSdk 30 下的旧运行时权限模型，
以及 native 风控判定不透明导致的服务端不可见风险。

支付与周边餐厅、项目、评价接口没有进行任何网络实测；本报告只记录代码中可
复核的 URL、HTTP 方法、请求字段和调用条件。
