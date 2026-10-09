# 证据索引

## 1. 样本

| 对象 | SHA-256 |
|---|---|
| Google Play services base APK | `bda6a95f1bb8dd707ad9333dce9639cc8104c4bbc97a639e14a428e63b4a6852` |
| payload APK A | `2b117da596db23fb9c74350cb0ab620245423411d3c915600052c0d314e06034` |
| payload APK B | `5b9b2e8832a6f1c8e1a049bbe699c1277f0da1f104296d2bbcb9b9a11f6be2a1` |
| payload APK C | `a85a3dd8e4634ecd76d6d482c0ca3721adac6324dca92837aa58ec26ca4437f5` |
| payload APK D | `f1739903647cfdd894fdd4136e7f13c2e85f3922e8792316c145e39b6d3b625c` |

payload package 均为 `com.google.ccc.abuse.droidguard`。

四个 payload manifest 的 `minSdkVersion=24`、`targetSdkVersion=14`，并以
`compileSdkVersion=37`/`DEV` 编译；解码后 `uses-permission` 与自定义
`permission` 数量均为 0。仅有一个默认禁用且未导出的
`PropertiesServiceHolder` service。

四个 APK 使用同一旧式签名证书：

| 项目 | 值 |
|---|---|
| Subject/Issuer | `CN=Unknown, OU=Google, Inc, O=Google, Inc, L=Mountain View, ST=CA, C=US` |
| Serial | `0x4934987e` |
| Certificate DER SHA-256 | `3D7A1223019AA39D9EA0E3436AB7C0896BFB4FB679F4DE5FE7C23F326C8F994A` |
| APK certificate signature/key | `md5WithRSAEncryption` / RSA 1024 bit |

证书 Subject 明确标注 `Google, Inc`；这是 APK 签名归属证据，不等同于本报告
独立验证 Android 平台的信任链。

## 2. 网络与协议代码

| 结论 | 代码位置 |
|---|---|
| User-Agent | `jadx-gms-deps/bxmu.java:14` |
| create URL/method/header/body | `jadx-gms-deps/bxmu.java:41-58` |
| fast/full 分支 | `jadx-gms-deps/bxmu.java:26-38` |
| Build 字段 | `jadx-gms-deps/bxjl.debug.java:91-122` |
| GMS/module 字段 | `jadx-gms-deps/bxjl.debug.java:37-50` |
| response parse/verify/required fields | `jadx-gms-deps/bxkz.java:49-72` |
| VM host allowlist/HTTP 200 | `jadx-gms-deps/bxkz.java:82-110` |
| result Binder write | `jadx-gms-deps/bxgx.java:144-146` |
| StreamZ telemetry factory | `jadx-gms-deps/bxlr.java:23` |
| 15 个 DroidGuard StreamZ 指标与字段 | `jadx-gms-network/classes7-full/sources/defpackage/bxmh.java:12-118` |
| StreamZ 消息构造/派发 | `jadx-gms-network/classes7-full/sources/defpackage/bxll.java:23-217` |
| log source 注册 | `jadx-streamz-classes5/sources/defpackage/arqm.java:78` |

## 3. 公钥位置

| 公钥 | 位置 | 结构/算法 | DER SHA-256 |
|---|---|---|---|
| 创建响应公钥 | `jadx-gms-deps/bxky.java:23` | 294-byte RSA-2048 SPKI | `cfa6b5243de9e3fcc0408aae5b40b5b480c3cfc6db8828f338c176c57c312954` |
| bytecode 公钥 1 | payload `DroidGuard.java:93` | 294-byte RSA-2048 SPKI | `cfa6b5243de9e3fcc0408aae5b40b5b480c3cfc6db8828f338c176c57c312954` |
| bytecode 公钥 2 | payload `DroidGuard.java:93` | 294-byte RSA-2048 SPKI | `03551c17e169af5110d7a42b33cad55b156661536ff054e4c0e8fe55a77e3660` |

三处均用 `SHA256withRSA`。公钥是公开验证材料；报告不复制完整 Base64，
API key 已脱敏。

## 4. protobuf 元数据

字段表按 protobuf `RawMessageInfo` 布局解析：

- `[0]` flags；
- `[1]` field count；
- `[2]` oneof count；
- `[3]` hasbits count；
- `[4]` min field number；
- `[5]` max field number；
- `[6]` total entries；
- `[7]` map count；
- `[8]` repeated count；
- `[9]` checkInitialized count；
- 后续为 field number、type/flags、presence offset。

`hvnz`：flags `1`、14 字段、范围 `1..27`。详细映射见 `protocol.md`。

## 5. native 证据

| 证据 | 位置 |
|---|---|
| JNI 注册恢复 | `JNI_OnLoad/RegisterNatives`，本报告私有分析目录 |
| native 函数清单与反汇编 | `libd669FBAAEF4B3.so`，SHA-256 `64e32d741efff46fcac0d74884a41dafecda4287cb7b379e18cb2cb4345abe07` |
| init/注册反编译 | `0x135a0`、`0x14280`、`0x1462c`、`0x14660` |
| `.b` 解码本地执行 | `0x19308` → `0x1c5e8` → `0x46cb0`，无执行错误 |

JNI 方法地址：`0x135a0`、`0x14280`、`0x14368`、`0x14464`、`0x144e8`、
`0x1462c`、`0x14660`。

## 6. `app_dgp` 缓存核对

只读核对的 `dg.db` schema：

```sql
CREATE TABLE main (
  a TEXT UNIQUE NOT NULL,
  b LONG NOT NULL,
  h LONG NOT NULL,
  d TEXT NOT NULL
);
CREATE INDEX expiration_idx ON main (h);
```

脱敏后的记录只保留 flow、键值长度、时间与版本：

| flow | `a` 长度 | `b` 创建秒 | `h` 过期秒 | `d` 版本 |
|---|---:|---:|---:|---|
| `fast` | 80 | 1791501031 | 1792105831 | `8348DD80A734238B4413C219FE27351BE6B49361` |
| `pia_express` | 87 | 1791504299 | 1791583499 | `8348DD80A734238B4413C219FE27351BE6B49361` |
| `ad_attest` | 85 | 1791504633 | 1791504643 | `8348DD80A734238B4413C219FE27351BE6B49361` |

`main.a` 的真实值包含 `Build.FINGERPRINT`，不在报告中复制。

| 缓存对象 | 字节数 | SHA-256 |
|---|---:|---|
| `.b` A | 63666 | `174ad7b1d99dc83353ee2db2350352d8222a8b367ceed737b3d10d7bc74d9688` |
| `.b` B | 87050 | `c58dc0c4b283568544663f8a92a7187d112e588ca3b01c52c82f97a9c0ebb72d` |
| `.b` C | 64561 | `dad1dc2600626a3ec8130a30eb8b2d30c17fe7bc153639ca756f1464cc6164d9` |
| `.d` A | 66 | `4197154d213be0b5050583142b0af99ebc91c98b2a26bb520aa8d1c6b6e6085e` |
| `.d` B | 66 | `79846ff6a6e5e5fe350c4f30ae8098a79ad0000787cfb3e40d1401341cc87322` |
| `.d` C | 66 | `118ed483730746b57dcb7c15f27fd1fdebcf696fa2ce2f81bd8bf54aee4a6f49` |

`.b` 的正确解码结果（只发布长度与摘要，不发布字节）：

| 文件前缀 | 解码字节数 | 解码 SHA-256 |
|---|---:|---|
| `0b5272…cdda0.b` | 63662 | `f6d2bc80bc4c98ebcc5f5dac558db0de123f2b1e69891755bdb79c588408ae4a` |
| `4d85a8…aeeb7.b` | 87046 | `b801bb20e9537aeddccd35a1dac579870733560afe71402f1c76532fe89c0e56` |
| `8f1a78…100e.b` | 64557 | `04c2a5de929ecd2a1956fbc20bfedd98899b9e0fd94f0546f8620ca5a3b9e087` |

三个 `.b` 的熵为 7.997305、7.998246、7.997515 bit/byte，均覆盖 256 个字节值。
代码映射：`bxkw.java:56-79` 读取，`bxkw.java:121-127` 生成摘要文件名，
`bxkw.java:137-162` 按缓存键和有效期查询，`bxkw.java:230-292` 原子写入与过期清理。

## 7. 设备只读核对

Android data 目录在 SSH 进程的挂载命名空间中不可直接遍历，实际核对时通过
宿主挂载命名空间进入 `data/...`；没有写入文件。

只读核对确认：

- GMS phenotype：`config_packages`、`android_packages`、`log_sources`、
  `cross_logged_tokens`、`flag_overrides`；
- Vending phenotype：`static_config_packages`、`experiment_states`、
  `param_partitions`、`accounts`；
- `data/user/0/com.google.android.gms/databases/dg.db`：
  24576 字节，SHA-256
  `3304d831f32914029d65212fd9f63949532565749f005897cffcec93cca1e12b`，
  与本地只读副本完全相同；
- `data/user/0/com.google.android.gms/app_dgp/` 中三组
  `.b`/`.d` 的长度与 SHA-256 均与本地副本完全相同；
- `data/data/com.google.android.gms/files/clearcut/0/STREAMZ_DROIDGUARD`
  存在但为空，证明 log source spool 已配置，不证明该样本发生实际发送；
- `data/user/0/com.android.vending/databases/verify_apps.db`
  （Play Store/Vending）：`package_verdict_cache(data, pk)`、`apk_info`、
  `installation_attempts`、`package_installation_state`；
- verdict cache 450 行、apk info 451 行、installation state 442 行；
- `data/user/0/com.android.vending/files/finsky/shared/droid_guard_payload_valuestore.pb`
  存在，大小 152 字节，属于 Vending 的 payload 配置存储；
- DroidGuard phenotype 配置文件存在。

数据库行内容、真实账号与 payload 内容未写入本报告。

## 8. 资源与操作边界

本报告不附 APK/DEX/SO、原始 protobuf、payload 字节、真实数据库行、真实设备
值、账号、完整 API key、token、密码或私钥。设备访问只读，无网络请求和写入。
