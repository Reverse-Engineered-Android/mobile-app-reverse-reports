# 去哪儿旅行 Android 10.4.23 逆向研究报告

## 1. 最终结论

### 1.1 样本

| 项目 | 值 |
|---|---|
| 包名 | `com.Qunar` |
| versionName / versionCode | `10.4.23` / `345` |
| minSdk / targetSdk（compileSdk） | `24` / `36`（`36`） |
| APK SHA-256 | `6834433914f2ad469fc63b64b0bbd6ff4bc1f072377b3509be96e2cf046a3c2b` |
| APK 字节数 | `92,254,498` |
| APK 条目 | `5,928` |
| `classes*.dex` | `15` 个（`classes.dex` … `classes15.dex`） |
| ARM64 native 库 | `63` 个（无 32 位 ABI 目录） |
| `assets/*.qp` 离线包 | `23` 个，合计 `85,564,564` 字节 |
| `AndroidManifest.xml` SHA-256 | `8dc48ccb9db03f8063922ac73d51d5dd4929775828b32eda35e5c1a802cf4a5f` |
| `resources.arsc` SHA-256 | `d518c1bb47fe8289638f362a1171c782ca75d847512ac213903f51936dbac254` |
| 声明权限 | 51 条 `uses-permission` 加 1 条 `uses-permission-sdk-23`（共 52 个请求元素、51 个去重，其中 9 个非 `android.permission.*`） |
| 组件 | 438 个；显式导出 26 个，其中 22 个未加 `android:permission` |
| `<queries>` 探测 | 183 个包名（182 去重）+ 12 个 intent 过滤器 |
| 应用入口 | `com.mqunar.spider.QunarApp` |

应用是“壳 + 原子化模块（atom）”结构：`com.mqunar.*` 承担宿主与网络/存储底座，
`qunar.lego.utils.*` 是 Hotdog 传输层，`com.qunar.rc` 与
`com.ctripfinance.risk.device` 是两套独立设备风控 SDK，
`com.Qunar.wxapi.*` / `com.alipay.sdk.*` 等是支付回调入口。

### 1.2 网络与协议

业务流量统一走 **Hotdog**（内部代号 Pitcher）隧道：客户端把真实目标 URL 放进请求头，
正文是 `Goblin.sand(zlib(manifest+content))`，POST 到 `slugger` 代理域名。
静态可确认的四段结构：

```
明文 → zlib.compress → "hdog" | u32le(manifestLen) | manifest(JSON) | content → Goblin.sand → POST
响应 → Goblin.drift → zlib.decompress → 明文
```

- 代理出口：`https://slugger.qunar.com/slugger-proxy`（主）、
  `https://minislugger.qunar.com/slugger-proxy`（其它业务线）、
  `https://cptest.qunar.com/slugger-proxy`（beta 常量）。
- URL 追加 `?qrt=<tStr>`；`tStr` 即业务功能键（如 `p_login`、`p_updateHolidays`）。
- 非 `hotdog` 路由会额外写 `Target-Url: <真实 URL>` 头；`hotdog` 路由把目标放在
  manifest 里，不再写该头。
- 固定头：`Content-Type: application/x-www-form-urlencoded`、
  `L-Date: yyyy-MM-dd HH:mm:ss.SSS Z z`（非 ASCII 时降级为空串）、
  `Accept-Encoding: identity`。
- 两种正文：`normal`（键值按 TreeMap 顺序拼成一段字节，manifest 记录每段的
  `start`/`length`）与 `trans`（`content-type` + `body{start:0,length:n}`）。
  当请求带 `FormPart`（如 multipart 上传）时走 `trans`。

### 1.3 `libgoblin_6_1_1.so` 信封（已验证）

`Goblin.sand` 输出 22 字节头 + 内部块：

| 偏移 | 长度 | 内容 |
|---|---|---|
| `0x00` | 6 | `05 61 31 39 30 37`（`0x05` + ASCII `a1907`） |
| `0x06` | 8 | `BE64(adler32(明文, 1))`，零扩展 |
| `0x0e` | 8 | `BE64(adler32(内部块, 1))`，零扩展 |
| `0x16` | n | 内部块（`Ena1907_req` 生成） |

两处 `adler32` 的种子来自 zlib 约定的 `adler32(0, NULL, 0) = 1`，
6 组 0 / 1 / 17 / 64 / 5,000 / 70,000 字节明文全部逐字节吻合。

**`sand` 与 `drift` 是方向不同的两条信封。** `DriftsandDe`（`0xa9fc`）
的输入是**去掉 6 字节魔数之后**的缓冲区，此时两道校验是纯 64 位大端比对：

```
第一道（失败返回 7）：BE64(hdr[8:16]) == adler32(hdr[16:], 1)
第二道（失败返回 6）：BE64(hdr[0:8])  == adler32(明文, 1)
```

128 位基向量探测显示 `hdr[0:8]` 只影响第二道、`hdr[8:16]` 只影响第一道，
互不串扰；40 组随机头下两道各自 40/40 吻合，真实 `sand` 输出截去魔数后
两道也都通过。但随后 `Dea1907_resp` 返回 `3`：它的分派键是内部块头 4 字节，
而 `sand` 写下的状态词是大端 `T29[usec % 29]`（`T29` 为 `.rodata 0x20cc94`
的 29 词表，`usec` 取 `gettimeofday`），29 个表项**没有一个落在**
`Dea1907_resp` 的 2,161 个键值里。也就是说请求侧信封本来就不是响应侧
信封的输入，`sand → drift` 并非互逆运算。

`Ena1907_req`（`0x125f58`，`235,383` 条指令）与 `Dea1907_resp`（`0xacd8`，
`289,654` 条指令）内部**没有一条间接跳转**，只有一个函数出口：不是解释器循环，
而是把状态机编译进直连控制流的等价变换。两段合计约 `0x200d94` 字节 `.text`，
配合 `JNI_OnLoad` 注册的 18 个 native 方法与两处完整性自检
（`.rodata 0x20cc94` / `0x20cd08` 表比对），构成该库的反分析主体。
**响应侧状态块的键值集与其变换未复原**，本报告只给出信封布局、失败码与
可重复的观测，不臆测算法。

### 1.4 认证

- 登录走 `p_login` 功能键，`LoginTask` → `HotdogConductor.setParams(PARAM_HOST,
  "p_login", json)`；`PARAM_HOST` = `GlobalEnv.getHotDogUrl()`。发行版把该常量
  指向内网 corp 主机（此处脱敏为 `<corp-host>/fca`），真机流量实际由
  `PitcherManager` 的 `slugger` 代理承接。
- 其余登录相关功能键：`mk_appIds`、`mk_oaidCert`、`mkd_preInstallInfo`、
  `p_ucUpdateCookie`、`f_apollo_x_urs_task_query`。
- 票据与 Cookie：`PitcherCookieUtils.insertCookie/saveCookie` 在
  `onInterceptRequest` / `onInterceptResponse` 两侧同步 Cookie；
  `GlobalEnv` 维护 `usid/uid/initUid/gid/sid/pid/cid/vid`，全部进入 cparam。
- 本次未登录、未持有任何真实账号票据，故不发布任何令牌值。

### 1.5 风控

三层，互相独立：

1. **传输层信封**：`libgoblin_6_1_1.so` 的 `sand`/`drift` 与两份完整性表，
   由 `JNI_OnLoad` 在加载时校验；改包/改库会使 `sand` 或 `drift` 直接失败。
2. **请求指纹层**：`HotdogConductor.cpHasPrivacyInfo` 组装 cparam，含设备与
   会话标识、位置、APN、分辨率、热修版本、安装 ID、网络类型；`isHook()` 扫描
   栈中的 `ZygoteInit`/Substrate/`XposedBridge` 生成位掩码 `hk`；`catom`/`cas`
   上报调用方原子模块与栈信息；`hotdogParamToHeaderSwitch` 打开时 `fp` 被提升为
   请求头并从 JSON 中移除。
3. **独立风控 SDK 层**：
   - `com.qunar.rc`（`RC.init(ctx, APP_TOKEN)`，发行版 `APP_TOKEN` 为
     `97d7407497855043c7c8d55fcc40710b`，客户端应用标识，非账号凭据）：
     `FpService` 用 `NUtils.cd(fpUuid, 10002/10003)` 生成指纹体，以
     `multipart/form-data` 单字段 `b` 上报，最多重试 3 次；`Checker` 做模拟器
     评分（>3 判为可疑）、keystore `qr_alias`（`secp256r1`）attestation、
     `Debug.isDebuggerConnected` 判定；`Cmd` 执行
     `ls -al /sdcard/Android/data/ | head -n 50` 并读取 `SystemProperties`；
     `d.getcc()` 主动连 `www.qunar.com:443`，回传 DNS 解析 IP、本地出口 IP 与
     对端证书链（Base64）。
   - `com.ctripfinance.risk.device`（金融设备指纹）：17 个收集器
     （`AppListInfo`、`Build`、`Cpu`、`Hardware`、`Identity`、`Permission`、
     `Sensor`、`Sim`、`Network`、`Screen`、`Battery`、`BasicState`、`Safe`、
     `BKB`、`Others`、`AppInfo`、`Basic`）；`id5 = MD5hex(BRAND|MODEL|androidId|
     StorageUtil.a()|WxH)`（取 MD5 的 **hex 字符串**，非裸摘要）；`instUUID` 持久化在
     `risk_install_uuid`；`PermissionInfoCollector` 输出 8 个授权布尔；结果经
     `AES/CBC/PKCS5Padding`（key = `MD5hex(gid)[0:8] + timestamp[0:8]`，
     即 8 个 hex 字符 + 8 位时间戳 = 16 字节 ASCII；IV = 固定 16 字节常量）
     加密后 Base64，POST 到
     `https://jr.qunar.com/m/quser/upDevice.do`，头部带 `clientId`/`reqChannel`，
     最短 300 秒节流。

### 1.6 上传下载范围

- 上传：`trans` 正文承载 `FormPart`（含文件流），由 `PitcherOkHttpClientManager`
  统一发出；静态可见的上传型业务键包括图片/酒店图（`hotelUploadPicUrl`，走
  `ud.client.qunar.com`）与若干 `b` 参数业务。本次未上传任何文件。
- 下载：`assets/*.qp` 离线包在安装包内直接携带（明文 JS 正文），运行期按
  `rn.qunar.com/packages/...`、`s.qunarzz.com/...` 增量更新；manifest 内
  `files[]` 用 `sl:"start,length"` 描述每个子文件在正文中的切片，附带
  `md5`/`sha256` 的 Base64 形态与长度。本次未触发任何下载。

### 1.7 越权 / 提权 / 未告知 / 超范围

| 判断 | 结论 | 依据 |
|---|---|---|
| 越权 | **客户端未发现越权调用** | 无 `pm grant`、无 `su`、无静默安装链；`READ_CONTACTS`/`WRITE_CALENDAR` 仅有声明，未找到进入业务上报的调用闭环（`permissions.md` §4） |
| 提权 | **未发现** | `SYSTEM_ALERT_WINDOW`、`REQUEST_INSTALL_PACKAGES`、`MOUNT_UNMOUNT_FILESYSTEMS` 有声明，但导出面 22 个无保护组件均为支付回调/分享/推送/快捷设置类，未构成提权路径（`permissions.md` §5） |
| 未经告知 | **存在告知粒度不足风险** | `<queries>` 显式探测 183 个包名（含 Magisk/KernelSU/APatch/虚拟摄像头/34 家银行类包名），`Cmd` 读取外部存储目录列表；隐私政策正文在服务端，客户端无法静态证明逐项告知（`privacy.md` §2、§6） |
| 超范围获取 | **存在“声明即可采集”的强耦合点** | 设备指纹 SDK 的 `AppListInfoCollector` 会上报非系统应用列表；`ext.ai()` 上报已安装包名、首装时间、`sourceDir`、`versionName`、`flags`、`uid`；位置仅在有缓存定位时进入 cparam（`privacy.md` §3、§6） |

### 1.8 存储结论

- 17 个业务/SDK 明文 SQLite 库 + 1 个 netty IM SQLite（共 18 个 SQLite 格式文件），
  `integrity_check` 18/18 全部 `ok`；后续只读复查时 netty 库已有 4 张 IM 表，
  说明它是运行时创建 schema 的动态存储，而不是固定空壳；
  无 SQLCipher/WCDB 封装，无页级加密。
- 50 个 `shared_prefs` XML **全部明文可解析**（362 个键名），与同一仓库中
  纷玩岛的密文偏好形成鲜明对比：同一 OEM 体系下不同 App 的存储策略并不一致。
- 16 个 MMKV 存储（`files/kv_storage`），采用 MMKV 默认的 mmap + CRC 校验格式。
- 敏感判定：`shared_prefs/` 内可见 `authStatus_com.Qunar.xml`、
  `ssoconfigs.xml`、`qunargpsshare.xml`、`sec_gd_config_mshield.xml` 等
  会话/风控配置文件，说明**会话与风控配置在静态存储层不受额外保护**；
  应用私有目录仍是唯一屏障（`allowBackup=false`）。
- `assets/*.qp` 的清单字段 `extra` 明确标出 `engineVersion`、`hermesEnabled`、
  `ramEnabled`、`slimEnabled`、`bundleType`，可用于判断某版本走 JSC 还是 Hermes。

详细结构见 `storage.md`。
