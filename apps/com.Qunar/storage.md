# 存储与本地数据

## 1. 平台层：不加密

`com.Qunar` 的 `shared_prefs` **没有**应用层加密。只读核对 50 个文件，
全部以标准明文 XML 序言开始：

```
<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>…
```

这与同仓库 `apps/cn.com.livelab/storage.md` 记录的“37 个文件全为密文、
固定 key+nonce 的计数器流密码”形成直接对照：同一 OEM 生态里不同 App 的
偏好文件保护策略并不统一。去哪儿的做法是把机密性完全交给
`/data/data/com.Qunar` 的 Unix 权限（`allowBackup="false"`）。

`AndroidManifest.xml` 的应用级配置：

```xml
<application android:name="com.mqunar.spider.QunarApp"
             android:allowBackup="false"
             android:largeHeap="true"
             android:extractNativeLibs="true"
             android:requestLegacyExternalStorage="true"
             android:usesCleartextTraffic="true"
             android:networkSecurityConfig="@xml/spider_network_security_config">
```

## 2. 设备端事实（只读核对）

| 目录 | 文件数 | 大小 | 说明 |
|---|---:|---:|---|
| `databases/` | 33 个条目 | 2.6 MB | 18 个 SQLite 格式文件（17 库 + 1 空壳）+ 15 个 journal/wal/shm |
| `shared_prefs/` | 50 个 XML | 684,312 B | **全部明文**，362 个键名 |
| `files/` | — | 33 MB | 含 16 个 MMKV（`kv_storage/`，16.4 MB） |
| `cache/` | — | 219 MB | 图片/离线包/WebView |

本次只读取文件名、尺寸、`PRAGMA` 结果与非敏感键名统计，**未复制任何行值**。

## 3. SQLite 库清单

17 个库 + 1 个 netty 落盘空壳（共 18 个 SQLite 格式文件），
`PRAGMA integrity_check` **全部 `ok`**（18/18）。

### 3.1 城市/公共数据（业务静态库）

| 库 | 表 | 行数量级 |
|---|---|---|
| `qunar_pub_v11.db` | `hcitys`(3977) `recs`(429) `prenum`(244) `country`(221) `hotcitys`(25) `holidays`(45) `version`(5) | 千级 |
| `flight2_v108.db` | `city`(1525) `QF_Nation`(137) `hotcitys`(75) `holidays`(71) `QF_MultiCity`(53) `QF_Subject`(8) `QF_RegionSubject`(8) `QF_Regin`(7) `QF_HotCitysLines`(1) `dbt`(1) `version`(3) | 千级 |
| `resource_db_112.db` | 与 `flight2_v108.db` 同构（12 表，同数量） | 千级 |
| `ctrip_hotel_v21.db` | `hcitys`(2284) `country`(221) `holidays`(45) `hotcitys`(40) `version`(3) `dbt`(1) | 千级 |

DDL 示例（`flight2_v108.db`，`sqlite_master` 原文）：

```sql
CREATE TABLE "city" ( "id" integer, "nameEN" text, "namePY" text,
  "namePYShort" text, "nameCN" text, "code" text, "airport" text,
  "country" text, "nameSearch" text, "cityDesc" text, "subject" text,
  "sectionTitle" text, "isHideInFilter" integer, "visaLable" TEXT,
  timeZoneId text );
CREATE UNIQUE INDEX "" ON city (id COLLATE RTRIM ASC)
```

`ctrip_hotel_v21.db` 的 `hcitys` 额外带 `utc`/`dst`/`dstStart`/`dstEnd`/
`businessType`/`city_level`/`rootCityName`，与携程城市库同源但已合并
去哪儿自有字段。

### 3.2 前端与缓存

| 库 | 表 | 行数 | 说明 |
|---|---|---:|---|
| `RKStorage` | `catalystLocalStorage(key, value)` | 78 | React Native `AsyncStorage` 后端，**明文** |
| `MercuryProxyCache.db` | `SourceInfo(_id,url,mime,length)` | 75 | H5 代理缓存索引 |
| `MercuryCacheDB` | `MercuryDownloadEntity` `UploadEntity` `CrashEntity` | 0 | 下载/上传任务表（列含 `downloadUrl`、`redirectUrl`、`downloadPath`） |
| `qStorageBackup.db` | `backup_data(storageKey,storageValue,version)` | 2 | KV 备份 |

### 3.3 第三方 SDK

| 库 | 表 | 来源 |
|---|---|---|
| `min_applog_1792` | `eventv3`(20) `launch`(8) `pack` | 字节跳动 AppLog（`user_unique_id`、`session_id`、`params` 列齐备） |
| `pushsdk.db` | `config`(5) `runtime`(28) `message` `ral` `bidata` | 个推/厂商推送统一封装 |
| `gtc3.db` | `r`(12) `e` `t` | 个推 GTC |
| `msre.db` | `c`(1) `r` `t` | 阿里云/数盟类 |
| `msvolcano.db` | `msal`(2) | MSA（OAID） |
| `dim.db` | `d`(19) | 设备标识 |
| `cg.db` | `sct`(3) | 配置 |
| `gs-ido.db` | `avt`(2) `c` `e` `t` | 指纹/标识 |
| `mqttAndroidService.db` | `MqttMessageTable`(0) | Paho MQTT 离线队列 |

### 3.4 Netty 落盘

`tfdjfjg9109_nettyhost_false_<hash>.dat`：`SQLite format 3` 头，
`integrity_check=ok`，但**无任何表**（0 tables）——
即 SQLite 壳 + 空 schema，实际数据在 78 KB 的 `-wal` 里。
文件名前缀 `tfdjfjg9109` 是异或/哈希后的模块名，`nettyhost_false` 是
Netty 主机开关标志位，`<hash>` 为 32 位十六进制的模块/环境标识。
（文件名中的哈希值不发布，此处以 `<hash>` 占位。）

## 4. `shared_prefs` 结构

50 个文件、362 个键名、684 KB。按用途分类（只统计键名，不含值）：

| 类别 | 文件（示例） |
|---|---|
| 认证/会话 | `authStatus_com.Qunar.xml`、`authStatus_com.Qunar:remote.xml`、`ssoconfigs.xml`、`sso_config_xf.xml`、`userdefault.xml`、`qunargpsshare.xml` |
| 风控/安全 | `sec_gd_config_mshield.xml`、`msfffppcfg.xml`、`msgzpfc.xml`、`msre_po_rt.xml`、`system_sensitive_api.xml`、`prv_config.xml`、`sp_client_report_status.xml` |
| 设备/安装 | `installation_sp.xml`、`dy_pay_device_info.xml`、`qrc_sp.xml`、`ad_auth.xml` |
| 位置/地图 | `location_sp_info.xml`、`map_pref.xml`、`Map_Privacy.xml`、`BDLocConfigManagerBDLocConfig.xml`、`MapCoreServicePreIA.xml`、`MapCoreServicePregck.xml` |
| 推送 | `mipush.xml`、`mipush_extra.xml`、`mipush_oc_normal.xml`、`getui_sp.xml` |
| 离线包/RN | `QRNQPInfoConfig.xml`、`engine_resource_sp.xml`、`leroadcfg.xml`、`leroadmshieldcfg.xml`、`ctrip_payment_pwd_verify.xml` |
| 支付 | `UnionPayPluginEx.pref.xml`、`ctrip_payment_pwd_verify.xml` |
| 崩溃/日志 | `data_acra.xml`、`AppStartWatchRecordTask_STORAGE.xml`、`AwOriginVisitLoggerPrefs.xml` |
| WebView | `WebViewChromiumPrefs.xml` |
| 其它 | `spUtils.xml`、`sp_pg.xml`、`hotel_remote_city_config.xml`（540 KB，最大）、`qunar_hy_res.xml`（65 KB）、`com.Qunar_preferences.xml`、`CtripConfiguration.xml`、`JZVD_PROGRESS.xml`、`data_qav.xml`、`mini_applog_*`（3 个） |

键名多为 ASCII 缩写与哈希形式（`s_to_re_li`、`xytk`、`wm_in_cco4.2.6`、
`vdrconfig_gz` 等），说明存在**键名混淆**：语义不可读，但文件本身不加密。

## 5. MMKV

`files/kv_storage/` 16 个 MMKV 主文件（各配 `.crc`），合计 16.4 MB，
最大单文件 8 MB（`com.mqunar.atom.hotel`），其余为 `com.mqunar.atom.train`（4 MB）、
`hen`（2 MB）、`com.mqunar.pay`、`com.mqunar.atom.alexhome`（各 512 KB）、
`com.mqunar.atom.flight`（256 KB）、`finance_risk_device`（128 KB）等。

命名规则是**原子模块名**（`com.mqunar.atom.<module>`），即每个业务模块一个
独立 MMKV 空间。MMKV 默认格式为 mmap + 变长编码 + CRC32 校验页，
**未启用加密模式**（无 `cryptKey` 传入的迹象）。

`finance_risk_device` 这个独立存储对应 `com.ctripfinance.risk.device` 的
`risk_install_uuid` / `last_upload_time` / `oaid` 等落盘点。

## 6. `assets/*.qp` 离线包

结构（`qp` 容器，**不加密**）：

```
offset 0   : "qp"            (0x71 0x70)
offset 2   : u32 little-endian manifestLength
offset 6   : manifest (UTF-8 JSON, manifestLength 字节)
offset 6+n : body (明文 JS，Metro 打包)
```

实测 `f_major_bundle_rn_android_2968_10010_20260911213520_prod.qp`：

| 项 | 值 |
|---|---|
| 文件大小 | `10,900,693` |
| 清单长度 | `62,783` |
| 正文长度 | `10,837,904` |
| `files[]` 项数 | 203 |
| `hybridid` | `f_major_bundle_rn_android` |
| `version` | `2968` |
| `pid` | `10010` |
| 正文首 24 字节 | `__d(function(g,r,i,a,m,e,d){` |

`extra` 字段直接给出运行引擎：
`{bundleType:"JSC", engineVersion:"0.0.1", platform:"android",
hermesEnabled:"false", ramEnabled:"false", slimEnabled:"true"}`。
`f_order_rn_android_849_10010_20260911174339_prod.qp` 的 `ramEnabled` 为
`"true"`，说明不同 bundle 可以按需切换 RAM 包模式。23 个包里 `ramEnabled`
为 `true` 的是 6 个（`cmn_order`、`f_home`、`f_order`、`hotel`、
`pf_grand_search`、`t_mavericks`），其余 17 个为 `false`。

23 个 `.qp` 覆盖 `f_major_bundle`、`f_home`、`h_home`、`hotel`、`train`、
`flight_booking`、`f_flight_search`、`f_order`、`cmn_order`、`p_cashier`、
`finance_cashier_hy`、`t_mavericks`、`t_sino`、`vs_list`、`pf_grand_search`、
`route_service`、`cmn_mine`、`commonbusiness`、`f_common_notice`、
`fw_fuwu_taro`、`q_design`、`q_design_qp`、`qpadjust`。

## 7. 敏感判定

| 判定 | 结论 |
|---|---|
| 数据库是否加密 | **否**，17/17 明文，`integrity_check` 全 `ok` |
| `shared_prefs` 是否加密 | **否**，50/50 明文 XML |
| MMKV 是否加密 | **否**，默认格式 |
| 离线包是否加密 | **否**，`qp` 正文为明文 JS |
| 会话是否明文落盘 | **是**，`authStatus_*` / `ssoconfigs` / `userdefault` 均在明文 XML 中 |
| 风控配置是否明文 | **是**，`sec_gd_config_mshield` / `msfffppcfg` / `msgzpfc` |
| 唯一屏障 | `/data/data/com.Qunar` 的 Unix 权限 + `allowBackup=false` |

这构成一个明确结论：**去哪儿把全部本地机密性押在沙箱边界上**，
没有第二道应用层加密。对 root 设备、备份提取、或任何能读取私有目录的
攻击者，本地数据完全敞开。

## 8. 平台层的其它可攻击面

- `android:usesCleartextTraffic="true"` + `res/xml/spider_network_security_config.xml`
  的 `<base-config cleartextTrafficPermitted="true"/>`：明文 HTTP 全局允许。
- `res/xml/network_config.xml` 是另一份策略：`<base-config>` 下同时声明
  `cleartextTrafficPermitted="true"` 与 `src="user"` + `overridePins="true"`
  的信任锚，即“允许明文 + 信任用户 CA + 覆盖证书固定”。
  静态检索显示该资源**只被 `R.xml.network_config` 引用**，`AndroidManifest.xml`
  实际挂的是 `@xml/spider_network_security_config`；本次未找到代码中显式
  `applyNetworkSecurityConfig`/`setNetworkSecurityConfig` 的调用点，
  因此该文件属“随包携带但未见生效路径”的冗余策略，仅作为潜在配置风险记录。
- `android:requestLegacyExternalStorage="true"`：Android 10 上仍用 legacy
  外部存储模型，这是 `Cmd.a()` 能 `ls /sdcard/Android/data/` 的前提之一。

## 9. 未发布内容

本页不包含任何数据库行值、`shared_prefs` 值、MMKV 内容、离线包 JS 正文、
设备标识、会话标识、真实路径或密钥。
