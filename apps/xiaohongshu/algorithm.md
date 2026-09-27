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

## 未闭环

| 项目 | 状态 |
| --- | --- |
| 外层 P/blob/Base64 | 已验证 |
| RC4 外层 | 已验证 |
| HMAC ipad/opad 外壳 | 已验证 |
| 定制 H 的压缩轮 | 未完整复原 |
| 有效会话 token 产生算法 | 未完整复原 |
| type 6/7 派生 | 未完整定性 |
| `libtiny.so` 加密 blob | 未离线闭环 |

[assemble_xhs_shield.py](../../tools/assemble_xhs_shield.py) 只重放已验证外层；不会把标准 HMAC-MD5 当成定制 H。
