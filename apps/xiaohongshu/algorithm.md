# 小红书 shield 算法边界

## 已验证外层

```text
P = version/app/device length fields + build string + device string + digest16
blob = type/header/length fields + RC4(key, P)
shield = "XY" + base64(blob)
```

`digest16` 是请求摘要槽。首轮没有有效会话上下文时可为全零。第二个平台信息头包含 Android、build 和 device 三类字段，但公开仓库不包含真实值。

RC4 key 位于 `libxyass.so` 的静态/解密字符串中。获取流程是：

1. 定位 RC4 KSA/PRGA 函数 `0x4b658`-`0x4b9e4`。
2. 从函数的 immediate、`.rodata` 指针和解密表得到 key 长度与字节。
3. 用合成载荷离线验证 RC4、header 和 Base64 顺序。

公开脚本使用 `XHS_RC4_KEY` 环境变量，不内置原值。

## 摘要与会话

- `token_payload[:64]` 是 HMAC 形状的 key。
- `0x7f224` 初始化两个子哈希，`0x7feec` 更新请求，`0x8001c` 输出 16 字节。
- token type 1-5 对应不同定制哈希变体。
- type 6/7 走会话 token 派生/刷新函数 `0x46a14`。

请求摘要的输入顺序已由调用轨迹确认：

```text
REQ = encoded_path + encoded_query + xy-platform-info + request_body
K64 = token_payload[:64]
inner = H(K64 xor 0x36 * 64, REQ)
digest16 = H(K64 xor 0x5c * 64, inner)
```

`H` 的公共接口是 64 字节分组、MD5 族 16 字节输出：init `0x816ec`/`0x818e8`/`0x8190c`/`0x81930`/`0x81954`，update `0x81710`，final `0x81814`，压缩函数 `0x81cc4`。type 1-5 分别选择 `0x91928`、`0x91960`、`0x91998`、`0x919d0`、`0x91a08` 描述符；对应 K 表位于 `0x94710`、`0x94710`、`0x94810`、`0x94910`、`0x94a10`。这解释了 type 1/2 在部分合成输入上同值，而 type 3-5 使用不同常量表。

## 公开实现边界

| 项目 | 状态 |
| --- | --- |
| 外层 P/blob/Base64 | 已验证 |
| RC4 外层 | 已验证 |
| HMAC ipad/opad 外壳 | 已验证 |
| 定制 H 的压缩轮 | 行为级已闭环；字节级实现不公开 |
| 有效会话 token 产生算法 | 服务器下发/会话 ctx 边界已定位；秘密变换不公开 |
| type 6/7 派生 | 函数路径和输入输出长度已闭环；秘密变换不公开 |
| `libtiny.so` 加密 blob | loader/opcode 边界已闭环；payload 不作为公开代码 |

[assemble_xhs_shield.py](../../tools/assemble_xhs_shield.py) 只重放已验证外层；不会把标准 HMAC-MD5 当成定制 H。

## 本地数据库密码边界

WCDB/SQLCipher 的已确认参数是 compatibility 3、page size 1024、KDF iterations 64000。passphrase 来自 MMKV/Preferences 或数据库配置类中的默认值；Getui/GTC 风险数据库另用 Android Keystore 的不可导出 RSA 私钥包装 AES key/IV。公开脚本不内置任何真实 passphrase、AES key、IV 或 RSA blob。

联系人上传数据使用独立 AES/CBC/PKCS5 + Base64，key 由设备 ID 状态派生，IV 为应用常量。这里只记录算法和依赖，不展示常量、派生输入或可解密样例。
