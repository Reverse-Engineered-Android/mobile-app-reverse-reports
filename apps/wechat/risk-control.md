# 设备、认证与行为风控

版本边界：登录请求字段、AutoAuth、NewSendMsg/NewSync/NewInit 和 Hybrid 封包来自 8.0.68 JADX Java 源码；`cp/w0.smali`、Normsg、支付设备信息和数据库证据来自 8.0.78。下文仅在两版本都存在相同调用结构时合并结论。

## 1. 已确认进入登录/协议结构

以下字段由请求构造代码直接赋值或在协议封装接口中出现：

- UIN、SessionKey、DeviceID、ClientVersion、DeviceType、Scene。
- SoftType、ClientSeqID、Signature、DeviceName、DeviceType。
- Language、TimeZone、渠道、客户端版本、AndroidPackageName。
- 登录标识/认证上下文、ECDH 公钥、CGI 校验公钥。
- 厂商、型号、Android release/incremental/display、APK 签名证书哈希。

主要代码位置：

- `evidence/java/manualauth-request.java:54-158`：BaseRequest 与设备/环境字段。
- `evidence/java/autoauth-request.java:48-115`：AutoAuth BaseRequest 与 EC key。
- `evidence/java/mmprotocal-jni.java:21-47`：签名、EC key、封包和解包 ABI。
- `evidence/java/hybrid-pack.java:33-48`：DeviceID、UIN、funcId、RSA/Hybrid ECDH、pass key、route info。

## 2. 触屏与设备信号的采集、时机与上传格式

`cp/w0.smali` 明确读取 `adb_enabled`、`development_settings_enabled`、CPU 信息、
IMSI/SIM country、Android ID、device ID、首次安装时间等，并在 ManualAuth
日志中写入认证环境结构；这部分字段的最终上传边界见
[privacy.md](privacy.md)。触摸 MotionEvent、相机状态、音频模式与显示设备
的编码和上传链已逐段还原如下，完整代码见
[evidence/risk-upload-chain.md](evidence/risk-upload-chain.md)。

### 2.1 触摸事件：是否上传、何时上传

- 入口是接口 `n84.k.mc(int, MotionEvent, String)`，由单例代理
  `n84.l` 转发（`n84/l.java:143-145`），真实实现为 `eg0.v`
  （日志名 `MicroMsg.SecInfoReporterImpl`）。
- 进入上报分支的必要条件是 `motionEvent.getAction() == 1`
  （`ACTION_UP`，抬手）**且**场景字符串非空（`eg0/v.java:389-391`）。
- 事件随后通过 `MotionEvent.obtain(...)` 复制，并丢到工作线程
  `"SIRI.GTE"` 上处理。
- 场景门控为 `i2 == 540999748`，采集标签固定为 `"ceu_global"`
  （`eg0/v.java:400-407`）。
- 上传受 24 小时限频：计数槽 `13`、窗口 `86400000L` ms、
  默认上限 `20` 次/天，上限可由远端配置 `h92.d0.ZC` 覆盖
  （`eg0/v.java:411-416`）。原生返回空字节则不发送。

结论：触摸数据不是逐次实时上传，而是“抬手 + 指定场景 + 远端限频”
三重门控后按天配额上传。

### 2.2 触摸事件如何进入原生层

调用链：

```
eg0.v.mc
  → hu3.q.Bi("ceu_global", motionEvent, false, str)
  → hu3.h 实现 com.tencent.mm.plugin.normsg.t.Bi   (t.java:36-38)
  → com.tencent.mm.normsg.l.i(...)                  (normsg/l.java:37-39)
  → 原生 c.p.dg(scene, MotionEvent, mode=3, str, 0) (normsg/c.java:88)
```

取数侧：

```
hu3.q.Q8("ceu_global", new normsg.i(true,false,true,true))   (t.java:853-855)
  → normsg.l.c(...)                                          (normsg/l.java:13-15)
  → 原生 c.p.dj(scene, flags)                                (normsg/c.java:94)
```

原生库名由类初始化器 `"tahcew".reverse() + "gsmron".reverse()` 拼接为
`wechatnormsg`，即 `lib/arm64-v8a/libwechatnormsg.so`；MotionEvent 的
字段读取与序列化全部发生在该库内。

### 2.3 上传的 Protobuf 格式与 CGI

```java
// eg0/v.java:418-433
pc5.od7 od7Var = new pc5.od7();
pc5.p16 p16Var = new pc5.p16();
p16Var.d(bArr);        // Q8 返回的原生 normsg 字节块
od7Var.e = p16Var;     // 字段 2
if (z) {               // z 恒为 true（eg0/v.java:406）
    pc5.p16 p16Var2 = new pc5.p16();
    p16Var2.d(hu3.q.INSTANCE.h());
    od7Var.f = p16Var2; // 字段 3
}
vVar2.cj(i5, od7.toByteArray(), false);
```

字段号由 `pc5/od7.java:48-55` 的序列化代码确定：字段 `2` = 原生 normsg
块，字段 `3` = `hu3.q.h()` 附加块，均为 `LEN`（wire type 2）嵌套
`pc5.p16` bytes 消息。

外层请求由 `eg0/v.java:274-291` 构造：

```java
lVar.f1176c = hu3.q.INSTANCE.U6("<obfuscated URI>");  // 解混淆
lVar.f1177d = 771;                                    // 网络 func/type
lVar.a = new pc5.vw5();
lVar.b = new pc5.ww5();
vw5Var.e = i;                                          // 场景号 540999748
vw5Var.d = new com.tencent.mm.protobuf.g("".getBytes()); // 空 bytes
vw5Var.f = p16Var;                                     // od7 字节块
```

URI 解混淆函数 `hu3.h.U6`（`com/tencent/mm/plugin/normsg/t.java:889-898`）
逐字符执行：

```java
int iCharAt = str.charAt(i) ^ (-89);          // 0xA7
i++;
sb.append((char) (iCharAt ^ ((byte) (~(i ^ length)))));
```

对本样本密文独立复算得到：

```
out[i] = ((in[i] ^ 0xA7) ^ (uint8)(~(((i + 1) ^ L) & 0xFF))) & 0xFFFF
```

解出的路径为 `/cgi-bin/micromsg-bin/reportclientcheck`。

最终结论：触屏与设备风控数据通过
`/cgi-bin/micromsg-bin/reportclientcheck`（func/type `771`）上传，
外层 `pc5.vw5` 承载场景号、空 bytes 字段和 `pc5.od7` 字节块，
`pc5.od7` 的字段 `2`/`3` 分别承载原生 normsg 数据与附加原生数据。

### 2.4 其他 normsg 上报口

同一发送器 `eg0.v.cj` 还承载 `Z7`（结构化设备/环境字段）、
`qg`（原生探测块 560）、`ec`（`cssi` 上下文）、`hd`/`he`（风控字节块）。
`eg0.a0`、`eg0.y`、`eg0.b0` 分别使用 24 小时限频槽 `12`、`11`、`8`，
其中槽 `8` 默认上限 `10`。

## 3. Root、Hook 与环境完整性

Java RiskScanner 的静态检查包括：

- `/system/bin/su`、`/system/xbin/su`、`/sbin/su` 与 PATH 中的 `su`。
- root UID/zygote 进程关系、root daemon 进程名。
- SELinux 非 enforcing 时的 setuid 文件。
- debugger/recovery 文件的 ELF/script 形态。
- 结果进入 `viruscheck` / `RiskCheck`。

另有：

- QQ/WLogin 设备结构中的环境字段。
- UnionPay SDK 报告 `/system/bin/su` 是否存在。
- Tinker 崩溃保护检查 Java 栈中的 XposedBridge，这是进程内 Hook 指示。
- `libmis.so` 针对小游戏 V8/JS 的调试、断点、调用栈、变量和 Wasm 信号。
- `libhuiyanandroidsdk.so` 与 `libYTAGReflectLiveCheck.so` 是活体核验组件。

需要区分误报：WCDB `integrity_check`、下载配置完整性、Matrix/crash/unwind、Owl JNI hook、MarsQUIC TracerPid 都不是通用 root/Frida 上传链。`KindaDeviceServiceImpl.isRoot()` 当前调用链硬编码 `false`。

## 4. 服务端可能组合的信号

- 设备可信度：官方签名、稳定 device ID、Build 自洽性、账号/设备切换频率。
- 环境完整性：Root/Hook/虚拟显示/模拟器/远程控制、APK 变化、APPRISK。
- 操作行为：触摸轨迹、登录/扫码/支付时序、相机/音频/页面状态。
- 网络与账号关系：出口 IP/地区、时区、SIM country、历史设备、session/UIN/device ID/ECDH 是否匹配。

以上是基于客户端字段和采集能力的防御性分析，不是服务端评分规则。

## 5. 证据强度

### 已验证

- 登录/协议字段、EC key、签名/封包接口、消息 Protobuf 字段。
- Java RiskScanner 的 su/进程/setuid/debugger 检查入口。
- 设备信息读取 API 和 Normsg 事件入口。
- 支付 PSK/AES-GCM/signature/root-cert/CGI 映射字符串。

### 已完成的原生链

- Normsg 数据块的 JNI 解析、索引/XOR 变换、`ACTION_UP` 时机、
  `byte[]` 输出、限频、Protobuf 字段和 CGI 均已给出精确代码，
  见 `evidence/normsg-native.md` 与 `evidence/risk-upload-chain.md`。
- 支付组件具备设备信息、交易上下文、签名和 MMTLS 能力，但未触发真实支付。

### 客户端样本的最终边界

- 服务端权重、封禁阈值和远程 feature gate 由服务端控制，APK 内不存在
  这些决策代码；本报告只给出客户端实际采集和进入封包的字段。
- `getInstalledPackages` 只在系统设置页异常兜底时做本机枚举并返回命中的
  单个 `Intent`；Normsg 的 `getRunningAppProcesses` 只输出“本进程存在且
  未 standby”的布尔值。完整安装应用列表和完整进程列表均没有进入本轮
  还原的风险上传结构；客户端最终结论是风险请求不上传完整列表，只上传
  已列举的散装设备/环境信号。设置页兜底的逐包 Xlog 行在 IPxx 诊断日志
  上传被触发时可随日志切片外发，完整进程列表没有这一入口；精确调用链见
  `evidence/risk-upload-chain.md` §4。
- 动态 UDR 模块的网络行为属于服务端下发内容的运行时状态，不在客户端
  静态风险上传链中；本报告不把它写成已发生的上传。
