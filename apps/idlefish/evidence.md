# 逆向证据

## 1. 样本与哈希

- 样本：`idlefish-7.28.40-arm64.apk`
- SHA-256：`57ae1b6963dadae25998b821c05e8fbc7e11cbb760ef23d8287f3934f185650a`
- 大小：`113,545,135` 字节
- ZIP 完整性通过；`AndroidManifest` 声明 `versionCode 521`、`minSdk 21`、
  `targetSdk 33`。

### 1.1 DEX 清单（9 个，总 80,344,736 字节）

| 文件 | 字节 |
| --- | ---: |
| classes.dex | 9,606,920 |
| classes2.dex | 9,404,560 |
| classes3.dex | 9,524,452 |
| classes4.dex | 9,020,884 |
| classes5.dex | 9,382,680 |
| classes6.dex | 9,854,644 |
| classes7.dex | 9,965,188 |
| classes8.dex | 9,591,076 |
| classes9.dex | 4,747,416 |

jadx 反编译：**48,234** 个 Java 文件（127 个不可反编译方法已用 smali 复核）。

## 2. native 库（125 个，节选）

| 库 | 大小 | SHA-256（前 16） |
| --- | ---: | --- |
| `libsgmainso-6.7.260202.so` | 2,985,052 | `7d2ae8a83d8b0e56`（加载器 ELF 头被故意破坏） |
| `libsgmiscso-6.5.9.so` | 153,484 | `d1f6c43da92119f6` |
| `libsgmisc.so` | 13,061 | `deb277ae14452085`（**实为 APK**，含独立 manifest 与签名） |
| `libsgmain.so` | 115,083 | `396d9cda681f28ff` |
| `libsgnocaptchaso-6.5.8.so` | 137,140 | `1b74eba2c62c0251` |
| `libwukong_native.so` | 271,960 | `8fa62008e58920d9` |
| `libMMASignature.so` | 13,872 | `7eaefa7cef20935a` |
| `libdps.so` | 1,378,896 | `8a4a1b2d0ac520b3` |
| `libtb_crypto.so` | 1,387,560 | `eee9ff48bb78dd84` |
| `libmmkv.so` | 458,448 | `b4d9cf70050305bf` |
| `libapp.so` | 54,152,520 | —（Flutter AOT） |
| `libflutter.so` | 10,075,352 | — |
| `libtnet.so` | 379,520 | — |
| `libtb_ssl.so` | 358,464 | — |
| `liblsquic.so` | 4,746,800 | — |
| `libxquic.so` | 541,976 | — |
| `libopenssl.so` | 2,140,560 | — |
| `libcrypto.1.0.2.so` | 1,326,152 | — |

**关键观察**：
- `libsgmainso-6.7.260202.so`（SecurityGuard 主库）的 ELF 头字段被破坏，
  无法直接 `readelf`，是阿里加固层。
- `libsgmisc.so` 名为 `.so` 实为 APK，内含独立 AndroidManifest 与签名，
  运行时解压/解密后加载，承载 SecurityGuard 的配置与证书。
- `libwukong_native.so` 为明文 ELF，含 MFE 引擎与 KFC 检索符号。

## 3. 代码证据索引（evidence/java/）

以下文件为 jadx 原始反编译输出（未做任何修改），用于报告精确引用。

### 3.1 签名链
| 文件 | 关键内容 |
| --- | --- |
| `InnerSignImpl.java` | 待签名串拼接、`requestType=7`、中间层调用、统一签名 |
| `LocalInnerSignImpl.java` | 降级签名（HMAC-SHA1） |
| `AbstractSignImpl.java` / `ISign.java` | 签名接口与环境映射 |
| `SignConstants.java` | `x-sign`/`x-mini-wua`/`x-sgext`/`wua` 头名 |
| `SignDegradedUtils.java` | `signDegradedApiList` / AB 开关 |
| `UTBaseRequestAuthentication.java` | RC4 解码的 UT 默认密钥字节 |
| `SecuritySDK.java` | UT 的反射调用 SecurityGuard |
| `InnerNetworkConverter.java` | XState 头 ↔ 线上头映射表 |

### 3.2 网络与业务
| 文件 | 关键内容 |
| --- | --- |
| `MtopLauncher.java` | 域名/TTID/实例绑定、拦截器注册、`send()` 入口 |
| `ApiBusiness.java` | 注解解析、灰度接口重定向、`needLogin/needWua` |
| `ClientHeaderInterceptor.java` | 闲鱼自有头（imei/umid/oaid/x-magic_device/…） |
| `ApiRegistry.java` | 317 条 `(api, version, 说明)` 枚举 |

### 3.3 上传
| 文件 | 关键内容 |
| --- | --- |
| `UploadFileServiceImpl.java` | `uploadv2.do` 完整流程 |
| `UploadConstants.java` / `UploadToken.java` | token 参数与响应头语义 |

### 3.4 风控
| 文件 | 关键内容 |
| --- | --- |
| `SecurityInterceptor.java` | `FAIL_BIZ_FORBIDDEN`/`NEED_REAL_VERIFY` 判定与弹窗 |
| `OffClientWukongGuard.java` | 站外跳转判定码、放行/阻断/超时分支 |
| `CcrcManager.java` | 三个 ccrcCode 的初始化与文本检测入口 |
| `CcrcBHService.java` | 行为风控服务分发（MFE/MO/BH 三态） |
| `WukongResultCode.java` | 10 个精确结果码 |
| `AntiAttackHandlerImpl.java` / `ApiLockHelper.java` | 反攻击池与单 API 加锁 |

## 4. 设备端只读证据

### 4.1 采集方法

- 经 SSH 只读访问自有 Android 设备的闲鱼应用私有数据目录。
- 对每个数据库先核对文件头为 `SQLite format 3\0`，再执行
  `PRAGMA integrity_check`（全部 `ok`），再读取 `sqlite_master` 的 DDL。
- 未读取任何行值；行数仅用于规模估计。

### 4.2 核对结果（与 [storage.md](storage.md) 对应）

| 库 | integrity | 表数 | 实际行数（样例） |
| --- | --- | ---: | --- |
| `fleamarket_idlefish_im_2207341976150.db` | ok | 7 | `SessionInfo=22`，`UserInfo=48`，`Message=0` |
| `fleamarket_datacenter_0.db` | ok | 1 | `fishkv=0` |
| `accs.db` | ok | 1 | `traffic=10` |
| `ut.db` | ok | 14 | `ap_alarm=5`、`stat=119` 等 |
| `dinamicx` | ok | 1 | `template_info=35` |
| `httpdns.db` | ok | 1 | `httpdns=48` |
| `monitor.db` | ok | 1 | `alitx_monitor=19` |
| `files/.wukong/mfe_db/v1.db` | ok | 2 | `mfe_basic=5`、`mfe_original=0` |
| `files/DAI/Database/walle_ut_user_track.db` | ok | 1 | `usertrack=5175` |
| `files/DAI/Database/edge_compute.db` | ok | 30+ | 行为图（多表，`ibfs_attr_count=85`、`ibfs_item_info=147`、`ibfs_kv=131`） |

### 4.3 SP 核对

- `user_provacy_policy.xml` 与 `user_provacy_policy_new.xml` 均 `true`
  （同意状态持久化）。
- `fish_device_activate.xml` 含 `fish_firstOpen_flag = 1`。
- `shared_prefs/` 共 **214** 个文件。

## 5. 静态工具链

| 阶段 | 工具 |
| --- | --- |
| 反编译 | `jadx 1.5.x`（`--threads-count 16`，`--no-src` 仅资源） |
| 字符串 | `strings -n 6` + sort/uniq，产出 `dexstrings.txt`（734,447 行） |
| 哈希 | `sha256sum` |
| ELF | `readelf` / `objdump`（明文库）；加固库仅做文件头/内嵌 ZIP 检查 |
| DB | Python `sqlite3`（`file:...?mode=ro&immutable=1`，URI 只读） |
| 设备 | SSH（只读 cat，无写入） |

## 6. 证据等级

- **已验证**：APK/库哈希、DEX 规模、反编译产物行数、设备端文件头与
  `integrity_check`、全部表结构、SP 键值。
- **结构已证实**：加固库的入口契约（SecurityGuard 组件名与加载方式），
  Wukong MFE 的表语义。
- **不可证**：加固库内部算法、服务端对签名/风控的具体校验逻辑。
