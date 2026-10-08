# 风控机制

本文件给出每一项风控机制的**具体实现位置与判定依据的精确代码**。全部来自
`libapp.so` 快照反汇编、`libgtc4core.so` 静态反汇编与随包资源，未触发任何风控。

## 1. Geetest 四代验证码

### 1.1 组成

| 组件 | 位置 |
|---|---|
| 本地脚本 | `assets/gt4.js`（15,364 字节）、`assets/gt4-index.html`（13,602 字节） |
| native 核心 | `lib/arm64-v8a/libgtc4core.so`（1,154,024 字节，NDK r23c） |
| 标识常量 | `captchaId` 字面量位于 PP `0x17078` |
| 客户端版本 | `1.8.10` |
| 服务端 | `gcaptcha4.geetest.com`、`gcaptcha4.geevisit.com`、`gcaptcha4.gsensebot.com`，兜底 `v4/bypass.js` |
| 落盘 | `shared_prefs/gt_core.xml`（514 字节）、`shared_prefs/gt_fp.xml`（217 字节） |

### 1.2 参数拼装（精确代码）

`libapp.so` 中同一闭包完成三个票据的字段名转换与提交：

```
b50...: PP 0x1c200  String "captchaOutput"
        PP 0x1c208  String "captcha_output"
        PP 0x1c210  String "passToken"
        PP 0x1c218  String "pass_token"
        PP 0x1c220  String "genTime"
        PP 0x1c228  String "gen_time"
        PP 0x1c230  String "phoneCountryCode"
        PP 0x1c238  String "tool/app/captcha/verifyAndSendSms"
```

判定链：只有 `captcha_output` 与 `pass_token` 同时非空才继续 `gen_time` 转换并
进入 `tool/app/captcha/verifyAndSendSms`；否则停留在验证码页。短信发送接口
`auth/app/login/phoneCaptcha`（PP `0x295e8`）也依赖该闸门。

### 1.3 设备指纹

`gt_fp.xml` 保存 Geetest 采集的 `fp`，长度 `64` 个十六进制字符。采集由
`gt4.js` 在 WebView 内执行，参数回填到 `captchaOutput`。指纹本身是风控输入，
不参与本地密钥派生。

## 2. 请求签名族

| 参数 | PP |
|---|---|
| `x-fwd-ts` | `0x25db8` |
| `nonceStr` | `0x40e98` |
| `timeStamp` | `0x40ec0` |
| `signType` | `0x40f28` |
| `sign` | `0x38438` |
| `accept-version` | `0x27a58` |
| `global-context` | `0x47a70` / `-acx-global-context` `0x47a78` |
| `x-fwd-anonymousId` | `0x186e8` |
| `platform-type` | `0x186f0` |
| `platform-version` | `0x186f8` |

`buildSignature`（PP `0x24818`，相邻 `platformName`/`platform_version` 族，
PP `0x18278`/`0x18280`/`0x18298`）是 `package_info` 插件的**构建签名**字段，
与上述请求签名族不在同一闭包，不得混同。

## 3. 响应级风控分支

`gXa.yhd`（起始 `0xabc438`）按下列顺序判定：

| 序 | 常量 | PP | 动作 |
|---:|---|---|---|
| 1 | `reLogin` | `0x11868` | 触发 `LoginEvent.loginOut`（`0x12d38`） |
| 2 | `ignoreRespError` | `0x13cf0` | 静默不提示 |
| 3 | `网络连接超时` | 池内同族 | 超时提示 |
| 4 | `服务器返回错误` | 池内同族 | 服务端错误提示 |
| 5 | `App请求超时` | 池内同族 | 客户端超时 |
| 6 | `接收数据异常` | 池内同族 | 报文异常 |
| 7 | `网络请求异常` | 池内同族 | 传输异常 |

`is_login`（`0x12e08`）与 `LoginEvent.signed`（`0x1f908`）共同决定后续
重定向到登录页。

## 4. 请求拦截器精确反汇编

`SXa.xhd`（闭包 `0xb50b04`）：

```
b50ba0: add x2, x27, #0x18, lsl #12
b50ba4: ldr x2, [x2, #0x6e8]        ; PP 0x186e8 "x-fwd-anonymousId"
b50ba8: add x30, x0, #0xa06
b50bac: ldr x30, [x21, x30, lsl #3]
b50bb0: blr x30                      ; 写入请求头

b50be0: add x2, x27, #0x18, lsl #12
b50be4: ldr x2, [x2, #0x6f0]        ; PP 0x186f0 "platform-type"
b50be8: add x30, x0, #0xa06
b50bec: ldr x30, [x21, x30, lsl #3]
b50bf0: blr x30

b50c10: add x2, x27, #0x18, lsl #12
b50c14: ldr x2, [x2, #0x6f8]        ; PP 0x186f8 "platform-version"
b50c18: add x30, x0, #0xa06
b50c1c: ldr x30, [x21, x30, lsl #3]
b50c20: blr x30

b50c30: bl #0xb4ff88                ; 公共请求处理
```

令牌装载段（`0xb4fe00` 起）：

```
b4fe80: add x16, x27, #0x18, lsl #12
b4fe84: ldr x16, [x16, #0x8d8]      ; PP 0x188d8 "Bearer "
b4fe88: stur w16, [x0, #0xf]

b4fecc: add x2, x27, #0x18, lsl #12
b4fed0: ldr x2, [x2, #0x8b0]        ; PP 0x188b0 "Authorization"
b4fed4: add x30, x0, #0xa06
b4fed8: ldr x30, [x21, x30, lsl #3]
b4fedc: blr x30
```

`gXa.xhd`（`0xb50a14`）的分派前置条件：

```
b50a20: ldur w1, [x0, #0x17]
b50a24: add  x1, x1, x28, lsl #32
...
b50a7c: ldur w4, [x3, #7]
b50a80: add  x4, x4, x28, lsl #32
b50a84: ldr  x16, [x26, #0x90]
b50a88: cmp  w4, w16
b50a8c: b.eq #0xb50ad4
...
b50a98: stur x4, [x29, #-8]
b50a9c: bl  #0x6e3400
b50aac: bl  #0x6e31ec
b50abc: bl  #0xccf154
...
b50af0: ldr x1, [x1, #0x6e0]        ; PP 0x186e0 -> SXa 闭包 0xb50b04
```

## 5. `libgtc4core.so` 混淆算法还原

### 5.1 结构

| 项目 | 值 |
|---|---|
| `.datadiv_decode*` 符号数 | `58` |
| 非平凡（size > 8） | `17` |
| 合计代码字节 | `174,596` |
| 注册方式 | `.init_array`（`0x1166f0`–`0x1168c0`）ABS64 重定位 |
| 执行时机 | so 加载构造阶段，先于 `JNI_OnLoad`（`0x565d0`） |

`.init_array` 重定位样例：

```
00000000001166f8  R_AARCH64_ABS64  .datadiv_decode8168837727703532391 + 0
0000000000116700  R_AARCH64_ABS64  .datadiv_decode9108658683897431782 + 0
0000000000116708  R_AARCH64_ABS64  .datadiv_decode8979313499633859050 + 0
0000000000116710  R_AARCH64_ABS64  .datadiv_decode5204385744049946370 + 0
...
00000000001168b8  R_AARCH64_ABS64  .datadiv_decode15281058774325335305 + 0
```

### 5.2 算法（逐字节）

所有函数都是**同址原地写回**：读 `.data` 中一段定长字节，做“按位掩码选择 +
异或常量”，再写回同一地址。完整样例
`.datadiv_decode16674591623665779447`（`0x547d4`，size `252`）：

```
0x547d4: adrp x8, 0x120000 ; add x8, x8, #0x68      ; 目标 0x120068
0x547dc: ldrb w16, [x8, #0x3]                        ; b3
0x547e0: ldrb w13, [x8]                              ; b0
0x547e4: ldrb w14, [x8, #0x1]                        ; b1
0x547e8: ldrb w15, [x8, #0x2]                        ; b2
0x547ec: ldrb w17, [x8, #0x4]                        ; b4
0x547f0: mov w11, #0xa4   ; mov w12, #0x5b           ; M=0x5b, ~M=0xa4
0x547f8: mov w9, #0xa9    ; mov w10, #0x56           ; M=0x56, ~M=0xa9
0x54800: bic w11, w11, w16                           ; 0xa4 & ~b3
0x54804: and w12, w16, w12                           ; b3 & 0x5b
0x54808: bic w0,  w9, w13                            ; 0xa9 & ~b0
0x5480c: and w13, w13, w10                           ; b0 & 0x56
0x54810: bic w1,  w9, w14                            ; 0xa9 & ~b1
0x54814: and w14, w14, w10                           ; b1 & 0x56
0x54818: bic w16, w9, w15                            ; 0xa9 & ~b2
0x5481c: and w15, w15, w10                           ; b2 & 0x56
0x54820: bic w9,  w9, w17                            ; 0xa9 & ~b4
0x54824: and w10, w17, w10                           ; b4 & 0x56
0x54828: orr w11, w11, w12                           ; (0xa4&~b3)|(b3&0x5b)
0x5482c: adrp x12, 0x120000 ; add x12, x12, #0x70    ; 目标 0x120070
0x54830: orr w9,  w9, w10
0x54834: mov w10, #0x0d                              ; 异或常量
0x5483c: orr w13, w0, w13                            ; (0xa9&~b0)|(b0&0x56)
0x54840: orr w14, w1, w14
0x54844: orr w15, w16, w15
0x54848: eor w10, w11, w10                           ; ^ 0x0d
0x5484c: strb w9,  [x8, #0x4]
0x54854: strb w13, [x8]
0x54858: strb w14, [x8, #0x1]
0x5485c: strb w15, [x8, #0x2]
0x54860: strb w10, [x8, #0x3]
...
0x54884: mvn  w13, w8        ; w8 = b0' of 0x120070
0x54888: and  w8,  w8, #0xffffffef                   ; b & ~0x10
0x5488c: and  w13, w13, #0x10                        ; ~b & 0x10
0x54890: orr  w8,  w13, w8                           ; == b ^ 0x10
0x548bc: strb w8,  [x12]
...
0x54874: bic  w13, w13, w9                           ; 0xe2 & ~b3'
0x54878: and  w9,  w9, w11                           ; b3' & 0x1d
0x54880: orr  w9,  w13, w9                           ; (0xe2&~b)|(b&0x1d)
0x548b8: eor  w9,  w9, w13                           ; ^ 0xf2
0x548c8: strb w9,  [x12, #0x3]
0x548cc: ret
```

**统一算式**（对每个目标字节 `b`）：

```
f(b) = (M & ~b) | (~M & b)   然后可选 g(b) = f(b) XOR C
```

即“按位掩码位选择 + 可选常量异或”。样例中出现的常量：

| 目标 | M | C |
|---|---|---|
| `0x120068` 的 b0/b1/b2/b4 | `0x56` | 无 |
| `0x120068` 的 b3 | `0x5b` | `0x0d` |
| `0x120070` 的 b0/b1/b2 | `0x10`（等价 `b ^ 0x10`） | 无 |
| `0x120070` 的 b3 | `0x1d` | `0xf2` |

`.datadiv_decode10548260539097012708`（`0x56814`，size `788`）是同一算式的
NEON 向量化版本：`mvn` + `bsl`/`bit`/`bif` 实现按位掩码，随后 `eor` 异或常量，
最后 `stp q4,q19,[x8,#0x30]`、`str q17,[x8,#0x20]`、`strb` 逐字节写回
`0x1201c0` 起的区间。大函数 `.datadiv_decode9108658683897431782`
（`0x2fb0c`，size `83,564`）同样以 `ldr q`/`bsl`/`bit`/`bif`/`eor` 铺开后写回
`.data`。

**结论**：这是**无密钥、无 IV、无随机量的固定字节变换**，用途是把 `.data`
中的字符串/表在加载时还原，属于反静态分析混淆而非密码学加密。因此不存在
未解释的加密实现。

## 6. 其余 native 风控组件

| 库 | 作用 |
|---|---|
| `libgtc4core.so` | Geetest 四代核心，字符串混淆见 §5 |
| `libmegface.so` | 人脸活体 `megface_v2_10_16_csg_faceid` |
| `libMegActionFmpJni.so` | `com.megvii.action.fmp.liveness` |
| `libumeng-spy.so` | 友盟设备/行为采集 |
| `libtnet-3.1.14.so` | 长连接通道 |
| `libCtaApiLib.so` | 运营商 CTA 接口 |
| `libDexHelper.so` / `libdexjni.so` | DEX 装载壳 |
| `libbarhopper_v3.so` | 条码解析 |

## 7. 静态化说明

未触发验证码、未构造签名请求、未测试越权、未绕过风控。服务端评分与处罚策略
不可由客户端静态分析断定。
