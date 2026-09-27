# 小红书逆向证据

## `libxyass.so`

| 地址 | 符号/函数 | 证据 |
| ---: | --- | --- |
| `0x3f484` | `JNI_OnLoad` | 获取 Application、解密字符串、注册 native |
| `0x3f6f8` | `RegisterNatives` call | `JNINativeMethod[4]` |
| `0x45764` | `intercept` | `(Lokhttp3/Interceptor$Chain;J)Lokhttp3/Response;` |
| `0x49634` | signer | 输出 `{u32 type, payload[>=64B]}` |
| `0x467dc` | assembler | 按 token type 分派并装配 shield |
| `0x46a14` | type 6/7 path | 会话 token 派生/刷新 |
| `0x4b3d0` | outer builder | P、RC4、blob header、Base64 |
| `0x4b658`-`0x4b9e4` | RC4 KSA/PRGA | S 盒与密钥流 |
| `0x7f094` | digest container | 三阶段摘要状态 |
| `0x7f224` | key setup | 取 token payload 前 64 字节 |
| `0x7feec` | update | 请求字节流输入 |
| `0x8001c` | final | 输出 16 字节摘要 |

### 外层重建

```python
payload = u32be(1) + u32be(app_id) + u32be(1)
payload += u32be(len(build)) + u32be(len(device_id)) + u32be(16)
payload += build + device_id + digest16

blob = u32be(4 | (token_type << 16)) + u32be(1)
blob += u32be(len(payload)) + u32be(len(payload))
blob += rc4(rc4_key, payload)

shield = "XY" + base64(blob)
```

证据等级：**已验证**。`rc4_key` 是 native 静态/解密字符串，公开脚本通过环境变量传入，不展示原值。

### 摘要结构

```python
K64 = token_payload[:64]
inner = H(bytes(a ^ 0x36 for a in K64) + request_bytes)
digest16 = H(bytes(a ^ 0x5c for a in K64) + inner)
```

证据等级：**结构已证实**。`0x7f224`、`0x7feec`、`0x8001c` 的参数和输出长度证明 HMAC 外壳；`H` 的逐轮结构未完整复原。

## `libtiny.so`

| 地址 | 证据 |
| ---: | --- |
| `0x18afd8` | `JNI_OnLoad` |
| `0x15e9f4` | 模拟器捕获的 native handler |
| `0x16b08c` / `0x17cdb0` | opcode 二叉比较点 |
| `0x754AC0` | 约 112 KB 加密 blob |
| `0xf7b40`-`0xf7bb0` | blob 描述符/模块表 |
| `0x631438` | wac 模块注册候选点 |
| `0x143d84` | 模块字节复制点 |

证据等级：**结构已证实**。静态模拟覆盖 JNI 和 opcode 分发；blob 解密仍是未知边界。

## 其他 JNI 表

| 文件 | 位置 | 证据 |
| --- | ---: | --- |
| `libsecurebase.so` | `JNI_OnLoad 0xd13c`，表 `0x38000` | 2 个动态注册方法 |
| `libeidjni.so` | 20 个 `Java_com_eidlink_jni_EIDReadCardJNI_*` | NFC/eID SDK |
| `libturingmfa.so` | `JNI_OnLoad 0x1fcc4`，表 `0x56540` / `0x56690` | 14 + 1 个动态注册方法 |

所有地址均相对于对应 ARM64 SO；仓库不包含 SO 或反汇编全量文件。
