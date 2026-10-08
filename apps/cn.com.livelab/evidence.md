# 逆向证据

## 1. 样本与哈希

| 文件 | SHA-256 | 大小 |
|---|---|---|
| APK | `b00ea747c55ad39b4075745577e97d8d0ce6cc1f068a9bf4b11dc68eb7c9063e` | `84,046,532` |
| `classes.dex` | `41510b4ad572f77ea8f0219869c93050c338140e2f0c8cbced35e1ffbf0d0760` | `40,033,088` |
| `base.vdex` | `6c0ddbf9d6263d607ce577a141c58983326277db3449158b75908c4c2c4a783d` | `40,033,444` |
| `libapp.so` | `97f76391fd38e775320f7516248a5b0364fe27977c545b0c4b9d8e56eac0d7c1` | `14,228,416` |
| `libflutter.so` | `8f4a42a7abfab1b6a689199a82c69eed48f701138d0cbc09f27b86e0309a892c` | `11,320,672` |
| 框架 `classes.dex` | `5ba446c0523155d1f978cea72348b102eccbbb168b8d52ffd2651412c8f431dc` | `9,496,576` |

## 2. Dart 快照符号

`libapp.so` ELF 头受损（`.dynsym` section header 失效），按动态符号记录扫描
恢复出四个 snapshot 符号（symbol `info == 0x11`、值按 64 字节对齐、
`value + size` 不越界）：

| 符号 | 起始 | 大小 |
|---|---|---|
| `_kDartVmSnapshotData` | `0x340` | `0x47d0` |
| `_kDartIsolateSnapshotData` | `0x4b40` | `0x3a60a0` |
| `_kDartVmSnapshotInstructions` | `0x3b0000` | `0x16a20` |
| `_kDartIsolateSnapshotInstructions` | `0x3c6a40` | `0x9b9690` |

快照池 `pool heap offset: 0x900080`，`pp.txt` `78,707` 行，`objs.txt`
`64,418` 行，反汇编 `151,605` 行。PP 常量一律写成 `PP 0x…` 形式，便于复核。

## 3. 关键代码位置

| 机制 | 地址 | 依据常量 |
|---|---|---|
| 令牌装载 | `0xb4fe00`–`0xb4fedc` | PP `0x188d8`、`0x188b0` |
| 请求头写入 | `0xb50b04`–`0xb50c30` | PP `0x186e8`、`0x186f0`、`0x186f8` |
| 分派包装器 | `0xb50a14` | PP `0x186e0`、`0x14f18` |
| 响应/错误 | `0xabc438` 起 | `reLogin`、`ignoreRespError` 等 |
| 成功分支 | `0xabd488` 起 | — |
| 验证码参数 | 同族闭包 | PP `0x1c200`–`0x1c238` |
| `searchV2` | — | PP `0x21510` |
| `appoint/v3` | — | PP `0x26df0` |
| `order create` | — | PP `0x25dc0` |
| `prePayInfo` | — | PP `0x25ef8` |
| `ticket/list/v2` | — | PP `0x33a88` |
| 七牛上传 | — | PP `0x1d3c0` |

## 4. Geetest native 证据

- `libgtc4core.so`：`file` 判为 `ELF 64-bit LSB shared object, ARM aarch64,
  dynamically linked, for Android 21, built by NDK r23c (8568313)`，
  BuildID `e10a9072b6050e9ae10e5f9e0a1a3a07f75749cf`。
- `.datadiv_decode*`：`58` 个符号，`174,596` 字节，由 `.init_array` ABS64
  重定位注册；完整反汇编与逐字节算式见 `risk.md` §5。
- 资源：`assets/gt4.js`、`assets/gt4-index.html`、`assets/gt4-loading.gif`。

## 5. 加密/混淆闭包（无未知实现）

| 对象 | 结论 | 出处 |
|---|---|---|
| `libgtc4core.so` `.datadiv_decode*` | 固定按位掩码位选择 + 可选常量异或，原地写回 `.data`；无密钥无 IV，非密码学 | `risk.md` §5.2 |
| `shared_prefs` 落盘 | 计数器模式流密码、固定 key+nonce、密钥流跨文件复用；已由分叉点/周期/字符集三条独立观测锁定 | `storage.md` §3 |
| Geetest 资源 | 本地 JS 采集 + native 表还原，参数已枚举 | `risk.md` §1 |
| SecNeo 装载壳 | 运行时解包注册 DEX，非数据加密 | `storage.md` §5 |
| `libapp.so` AES 字符串 | 服务端报文/本地偏好加密族，`AES/CTR/NoPadding` 与落盘结构一致 | `storage.md` §3.3 |

**没有任何一项保留为“待分析”**。

## 6. 混淆与打包特征

| 特征 | 证据 |
|---|---|
| SecNeo/Bangcle | `com.secneo.apkwrapper.AW` / `AP` / `CP`；`libDexHelper.so`、`libdexjni.so` |
| stub 规模 | 5 个 class，`class_defs_size=0x5`，数据段止于 `0x4b20`，`dexdata0` 于 `0x4b2c` |
| 尾部熵 | `0x4b60` 起 `7.998`，末尾 `7.996` |
| native 标记 | `__b_a_n_g_c_l_e__check1234567_` |
| Dart 混淆 | 类名/闭包名压缩为 `SXa`、`gXa` 等，符号靠 PP 池与地址恢复 |

## 7. 设备端只读核对

只读检查 `shared_prefs` 37 个文件（尺寸 `115`–`11,943`）、
`databases/` 11 个库、`app_flutter/core_local_storage.hive`、`webview/Cookies`。
未写入设备，未发起网络请求。数据库 schema 与共享配置数量只做只读核对。

## 8. 未发布内容

报告不包含：APK 本体、令牌、Cookie、设备 ID、真实订单/票夹/搜索词、内部路径、
内网地址、私钥、验证码 challenge、解密后的偏好键值与真实响应体。
