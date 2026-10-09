# 协议格式

## 1. 创建请求 `hvnz`

根据 `RawMessageInfo` 布局解码：flags=`1`（proto2），字段数 `14`，
字段号范围 `1..27`。

| wire # | Java 字段 | protobuf 类型 | 语义 |
|---:|---|---|---|
| 1 | `c` | message `hvok` | flow 子消息 |
| 2 | `d` | repeated message `hvob` | Build 键值对 |
| 3 | `g` | string | GMS 版本 |
| 7 | `i` | bool | DroidGuard/module 标志 |
| 8 | `j` | bool | 固定 true 标志 |
| 9 | `k` | repeated bytes | 额外 byte[] |
| 10 | `l` | bytes | fast/full 输入字节 |
| 11 | `m` | uint32 | fast/full 模式标志 |
| 13 | `h` | int32 | module version |
| 14 | `e` | string | `os.arch` |
| 23 | `f` | enum `hvnu` | 风控/verifier 模式 |
| 25 | `n` | enum `hvnx` | 配置枚举 |
| 26 | `o` | string | 附加字符串 |
| 27 | `p` | bool | 附加布尔标志 |

### 1.1 子消息

`hvok`：

| wire # | Java 字段 | 类型 | 语义 |
|---:|---|---|---|
| 1 | `c` | string | `fast`、`full`、`msa-l` 等 flow 名 |
| 2 | `d` | string | flow 附加值 |

`hvob`：

| wire # | Java 字段 | 类型 | 语义 |
|---:|---|---|---|
| 1 | `c` | string | Build 键 |
| 2 | `d` | string | Build 值 |

### 1.2 枚举

- `hvnu` 接受的枚举值：`0,2,4,5,6`。
- `hvnx` 接受的枚举值：`0,1,2,3`。

`bxjl.debug.java` 的 setter 与 `bxmu.b` 的 flow 分支共同确定字段填充；
例如 `hvnz.g` 是 GMS 版本，`hvnz.e` 是 `os.arch`，`hvnz.c.c` 是 flow。

## 2. 响应外层 `hvoa`

| wire # | Java 字段 | 类型 | 语义 |
|---:|---|---|---|
| 1 | `c` | bytes | 签名保护的 `hvpj` body |
| 2 | `d` | bytes | RSA 签名 |
| 4 | `e` | string | 附加字符串/URL 上下文 |

`bxkz.java:51-56` 用 `c`/`d` 执行验签，验签失败直接抛出
`Creation response signature verification failed`。

## 3. 响应内层 `hvpj`

| wire # | Java 字段 | 类型 | 语义 |
|---:|---|---|---|
| 1 | `c` | bytes | bytecode |
| 2 | `i` | string | checksum |
| 3 | `d` | bytes | VM URL 的序列化表示 |
| 4 | `f` | uint32 | validity duration |
| 5 | `e` | bytes | 附加程序/上下文 |
| 6 | `g` | bool | 运行标志 |
| 9 | `h` | bytes | 错误/状态上下文 |

`bxkz.java:58-72` 映射 `byteCode`、`vmUrl`、`vmChecksum` 和
`validityDurationSecs`，并把 `hvoa.e` 作为附加字符串保存。

## 4. 序列化与传输

请求由 protobuf runtime 直接写入 HTTP body；不是 JSON、不是表单。响应也按
`hvoa` typed protobuf 解析。payload 内部的 `_seigd` 则是 Base64 编码的
Parcel/Bundle，`DroidGuard.java:540-561` 解码后交给 native；这不是外层 HTTP
协议。

## 5. 请求示例结构

下面是字段级示例，不包含真实设备值、真实 API key 或原始私有数据：

```text
field 1  message:
  field 1 = "fast"
field 2  repeated Build:
  key "MANUFACTURER" = "<example>"
  key "MODEL"        = "<example>"
  key "VERSION.SDK_INT" = "<example>"
field 3  = "26.37.37 (260400-994713346)"
field 7  = true
field 8  = true
field 11 = <fast/full mode>
field 13 = <module version>
field 14 = "aarch64"
```
