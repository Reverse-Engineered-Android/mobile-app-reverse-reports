# 存储与加密

## 1. 平台层：不加密

只读核对 Android 框架反编译产物（`SharedPreferencesImpl`）：

```java
private void loadFromDisk() {
    ...
    str = new BufferedInputStream(new FileInputStream(this.mFile), 16384);
    map2 = XmlUtils.readMapXml(str);
}

private static FileOutputStream createFileOutputStream(File file) {
    FileOutputStream str = new FileOutputStream(file);
    return str;
}
```

即 `loadFromDisk()` 直接 `FileInputStream` + `XmlUtils.readMapXml`，
`createFileOutputStream()` 直接 `FileOutputStream`，**框架层没有加密**。
框架 `classes.dex` SHA-256：
`5ba446c0523155d1f978cea72348b102eccbbb168b8d52ffd2651412c8f431dc`。
设备为 Android 16 / API 36 / MIUI V816。

## 2. 设备端事实

`shared_prefs/` 下 `37` 个 `.xml` 文件全部是二进制密文（`file` 判为 `data`），
不是 XML 文本。文件尺寸 `115`–`11,943` 字节。SELinux 标签为普通
`app_data_file`，`lsattr` 只有 `E`（extent），不显示文件系统加密标记。

同设备 `cn.damai` 等应用的 `shared_prefs` 是明文 XML，说明这不是 ROM 对全部
应用的统一处理。

## 3. 密文结构还原

### 3.1 观测

| 观测 | 值 |
|---|---|
| 37 个文件公共前缀 | `68` 字节 |
| 公共前缀十六进制 | `f28cee39a4e56c0df531623537debcff…6e4e5721` |
| 第一对文件分叉点 | 第 `81` 字节 |
| 偏移 68 的不同取值 | `4` 个，计数 `23 / 8 / 4 / 2` |
| 偏移 69–77 的不同取值 | `3`–`4` 个 |
| 偏移 79 起不同取值 | `7 / 13 / 11 / 22`… |
| 37 个文件公共后缀 | `0` 字节 |

### 3.2 推论（逐条）

1. **密钥流跨文件共享（密钥流复用）**：对偏移 `0..299`，存在单一字节
   `K[i]`，使 37 个文件在 `279/300` 个偏移上同时落在可打印 XML 字符集内。
   若每文件使用独立随机密钥流，该概率可忽略。
2. **明文前 67 字节固定，第 67 字节是 `<`**：公共前缀 `68` 字节意味着
   `P[0..67]` 全部相同；偏移 `68` 只有 `4` 个取值，故 `P[68]` 是首标签名的
   首字母，`P[67]` 必为 `<`。首标签类型共 `4` 种（计数 `23/8/4/2`）。
3. **排除周期性重复密钥 XOR**：以各文件结尾的已知明文 `</map>` 对周期
   `1..2048` 全部产生冲突，短周期不成立。
4. **排除分组密码 CBC/ECB**：明文在第 `68` 字节分叉（非 16 字节块边界），
   密文也恰在第 `68` 字节分叉；CBC/ECB 会使整个块从块首（第 `64` 字节）分叉。
5. **公共后缀为 0 的解释**：闭合标签 `</map>` 在不同长度文件中处于不同偏移，
   在位置相关的密钥流下自然得到不同密文；这与密钥流复用并不矛盾。

### 3.3 算法结论

与全部观测一致的唯一结构是**计数器模式流密码，固定 key + 固定 nonce**，
即密钥流 `KS = AES_K(nonce‖counter)`、`C = P ⊕ KS`，且 `K`、`nonce` 在全部
37 个文件间复用。

APK 字符串表中的对应证据（`classes.dex` 偏移）：

| 字符串 | 偏移 |
|---|---|
| `AES/CTR/NoPadding` | `0x18b155b` |
| `AES/CBC/PKCS5Padding` | `0x18b152f` |
| `AES/CBC/PKCS7Padding` | `0x18b1545` |
| `AES/ECB/PKCS7Padding` | `0x18b156e` |
| `AES/GCM/NoPadding` | `0x18b1584` |
| `AES_IV` / `AES_KEY` | `0x18b1597` / `0x18b159f` |
| `aes_iv` / `aes_key` | `0x19460eb` / `0x19460f3` |
| `android.app.SharedPreferencesImpl` | `0x1947486` |
| `KeystoreUtil` / `AndroidKeyStore` | `dex-strings` 池内 |
| `Ljavax/crypto/spec/SecretKeySpec;` | `dex-strings` 池内 |

`AES/CBC`、`AES/ECB` 与第 3.2 节第 4 条的分叉点观测冲突，故**不适用于**
`shared_prefs` 落盘；`AES/GCM` 会引入每文件随机 nonce 与认证标签，与“公共前缀
68 字节、偏移 68 仅 4 个取值”冲突。**唯一与观测一致的是 CTR。**

### 3.4 攻击面

固定 XML 序言是已知明文，长度 `67` 字节，直接给出等长密钥流
`KS[0..66] = C[0..66] ⊕ P[0..66]`。由于密钥流跨文件共享，这 `67` 字节的暴露
对**全部 37 个文件同时生效**；结合标签名/键名的有限字符集，可用逐字节约束求解
继续扩展。因此该存储加密**不提供机密性保障**。

本报告不发布解密后的键值、令牌、设备标识或真实偏好内容。

## 4. 数据库与其它落盘

| 文件 | 格式 |
|---|---|
| `DioCache.db`、`MessageStore.db`、`MsgLogStore.db` | SQLite，明文 schema |
| `accs.db`、`message_accs_db` | SQLite |
| `sensorsdata`、`share.db`、`ua.db` | SQLite |
| `umeng_zero_cache.db` | SQLite |
| `com.google.android.datatransport.events` | SQLite |
| `app_flutter/core_local_storage.hive` | Hive，明文；含 `userInfo`、`token`、`mallTokenCache_*`、票夹缓存 |
| `webview/Cookies` | WebView Cookie 库，核对时 0 行 |

Hive 与上述 SQLite 均**未加密**，令牌与票夹缓存以明文落盘。这是与
`shared_prefs` 加密相对的另一面：真正敏感的 `token` / `mallTokenCache_*`
反而落在未加密的 Hive 文件中。

## 5. DEX 装载壳

`classes.dex` 是 SecNeo/Bangcle 装载壳：stub 逻辑结束于约 `0x4b20`，
`dexdata0` 标记位于 `0x4b2c`，文件 `0x4b60` 起熵为 `7.998`，`base.vdex` 中
偏移 `0x40` 处的 DEX 与 `classes.dex` 同 SHA-256，说明设备侧 VDEX 未提供
未封装的原始 DEX。`libDexHelper.so` 导出 `JNI_OnLoad`、`prepare_vmdex`、
`DexFileLoader::Load*`，并含 `__b_a_n_g_c_l_e__check1234567_` 标记。

壳的作用是**运行时解包并注册 DEX**，不是数据加密；本报告只描述其装载边界与
可验证特征，不臆测未被静态证据支持的内部算法。
