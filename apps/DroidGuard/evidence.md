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

## 3. 公钥位置

| 公钥 | 位置 | DER SHA-256 |
|---|---|---|
| 创建响应公钥 | `jadx-gms-deps/bxky.java:23` | `cfa6b5243de9e3fcc0408aae5b40b5b480c3cfc6db8828f338c176c57c312954` |
| bytecode 公钥 1 | payload `DroidGuard.java:93` | `cfa6b5243de9e3fcc0408aae5b40b5b480c3cfc6db8828f338c176c57c312954` |
| bytecode 公钥 2 | payload `DroidGuard.java:93` | `03551c17e169af5110d7a42b33cad55b156661536ff054e4c0e8fe55a77e3660` |

公钥是公开验证材料；报告不复制完整 Base64，API key 已脱敏。

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
| JNI 注册恢复 | `evidence/native/base_jni_emulation.json` |
| native 函数清单 | `evidence/native/base_functions.afl` |
| 反汇编 | `evidence/native/base.disasm` |
| 字符串 | `evidence/native/base.strings` |
| init 反编译 | `evidence/native/base_init_decomp.txt` |

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

三个 `.b` 的熵为 7.997305、7.998246、7.997515 bit/byte，均覆盖 256 个字节值。
代码映射：`bxkw.java:56-79` 读取，`bxkw.java:121-127` 生成摘要文件名，
`bxkw.java:137-162` 按缓存键和有效期查询，`bxkw.java:230-292` 原子写入与过期清理。

## 7. 设备只读核对

只读核对确认：

- GMS phenotype：`config_packages`、`android_packages`、`log_sources`、
  `cross_logged_tokens`、`flag_overrides`；
- Vending phenotype：`static_config_packages`、`experiment_states`、
  `param_partitions`、`accounts`；
- `verify_apps.db`：`package_verdict_cache(data, pk)`、`apk_info`、
  `installation_attempts`、`package_installation_state`；
- verdict cache 450 行、apk info 451 行、installation state 442 行；
- `droid_guard_payload_valuestore.pb` 存在，大小 152 字节；
- DroidGuard phenotype 配置文件存在。

数据库行内容、真实账号与 payload 内容未写入本报告。

## 8. 资源与操作边界

本报告不附 APK/DEX/SO、原始 protobuf、payload 字节、真实数据库行、真实设备
值、账号、完整 API key、token、密码或私钥。设备访问只读，无网络请求和写入。
