# 设备风控信号上传链：精确代码与协议格式

样本：`com.tencent.mm` 8.0.78 / `versionCode=3180`，`arm64-v8a`。
JADX 反编译源位于受控环境 `analysis/wechat-risk-closure-20261009/java/<dex>/sources/`。
本文件只记录可复现的调用链、判定条件、字段号和编码算法；不包含账号、设备 ID 或真实负载。

## 1. 触屏事件的采集、触发时机与上传格式

### 1.1 事件入口

接口 `n84.k` 声明 `void mc(int, MotionEvent, String)`（`n84/k.java`），
单例代理 `n84.l` 转发给服务容器中的真实实现：

```java
// n84/l.java:143-145
public void mc(int i, android.view.MotionEvent motionEvent, java.lang.String str) {
    ((n84.k) ph5.n0.c(n84.k.class)).mc(i, motionEvent, str);
}
```

真实实现为 `eg0.v`（日志名 `MicroMsg.SecInfoReporterImpl`）。

### 1.2 触发条件（判定依据的精确代码）

```java
// eg0/v.java:389-391
public void mc(final int i, android.view.MotionEvent motionEvent, final java.lang.String str) {
    if (motionEvent.getAction() == 1 && !android.text.TextUtils.isEmpty(str)) {
        final android.view.MotionEvent motionEventObtain = android.view.MotionEvent.obtain(motionEvent);
```

判定结论：

- 只有 `getAction() == 1`（`ACTION_UP`，抬手）才会进入上报分支；
- 场景字符串 `str` 为空时直接放弃，不排队；
- 进入分支时用 `MotionEvent.obtain(...)` 复制一份事件，随后在工作线程
  `"SIRI.GTE"` 上执行，因此即使原事件被复用/回收也不影响后续序列化。

### 1.3 场景门控

```java
// eg0/v.java:400-407
if (i2 == 540999748) {
    hu3.q qVar = hu3.q.INSTANCE;
    qVar.Y7("ceu_global", str2);
    qVar.Bi("ceu_global", motionEvent2, false, str2);
    qVar.Ha("ceu_global");
    final boolean z = true;
    final byte[] bArrQ8 = qVar.Q8("ceu_global", new com.tencent.mm.normsg.i(true, false, true, true));
```

- `540999748` 是该上报场景的数值场景号（该常量在本样本中只出现在
  `eg0/v.java:400` 这一处）。
- 采集场景标签固定为字符串 `"ceu_global"`。
- `Bi(..., false, ...)` 第三个参数 `false` 决定 `dg` 的 mode 取值为 `3`
  （见 1.4）。
- `com.tencent.mm.normsg.i(true, false, true, true)` 是从原生层取数时的
  四个开关位，决定序列化哪些字段。

### 1.4 MotionEvent 如何进入原生层

调用链：`eg0.v` → `hu3.q.Bi` → `hu3.h` 实现 `com.tencent.mm.plugin.normsg.t`
→ `com.tencent.mm.normsg.l.i(...)` → 原生 `c.p.dg(...)`。

```java
// com/tencent/mm/plugin/normsg/t.java:36-38
public void Bi(java.lang.String str, android.view.MotionEvent motionEvent, boolean z, java.lang.String str2) {
    com.tencent.mm.normsg.l.i(str, motionEvent, z, str2);
}

// com/tencent/mm/normsg/l.java:37-39
public static void i(java.lang.String str, android.view.MotionEvent motionEvent, boolean z, java.lang.String str2) {
    com.tencent.mm.normsg.c.p.dg(str, motionEvent, z ? 2 : 3, str2, 0);
}

// com/tencent/mm/normsg/c.java:88
public static native void dg(java.lang.String str, android.view.MotionEvent motionEvent,
                             int i, java.lang.String str2, int i2);
```

取数侧：

```java
// com/tencent/mm/plugin/normsg/t.java:853-855
public byte[] Q8(java.lang.String str, com.tencent.mm.normsg.i iVar) {
    return com.tencent.mm.normsg.l.c(str, iVar);
}

// com/tencent/mm/normsg/l.java:13-15
public static byte[] c(java.lang.String str, com.tencent.mm.normsg.i iVar) {
    return com.tencent.mm.normsg.c.p.dj(str, iVar);
}

// com/tencent/mm/normsg/c.java:94
public static native byte[] dj(java.lang.String str, java.lang.Object obj);
```

原生库名由类初始化器拼接而来（`"tahcew".reverse()` + `"gsmron".reverse()`），
即 `System.loadLibrary("wechatnormsg")`，对应 APK 内 `lib/arm64-v8a/libwechatnormsg.so`。
`MotionEvent` 全部字段与序列化结果都在该库内完成。

### 1.5 上传频率与阈值

```java
// eg0/v.java:411-416
int i4 = 20;
int iFj = ((jb2.d) ph5.n0.c(h92.e0.class)).fj(h92.d0.ZC, 20);
if (iFj >= 0) {
    i4 = iFj;
}
if (eg0.o0.c(13, 86400000L, i4) && !com.tencent.mm.sdk.platformtools.y8.L0(bArrQ8)) {
```

- 计数槽位 `13`，窗口 `86400000L` ms = 24 小时，默认上限 `20` 次/天；
- 上限可由远端配置项 `h92.d0.ZC` 覆盖（默认 `20`，负值回退为 `20`）；
- 原生返回的 `byte[]` 为空时同样不发送。

结论：触屏数据不是逐次实时上传，而是“抬手事件 + 指定场景 + 远端限频”
三重门控后按天配额上传。

### 1.6 上报的 Protobuf 格式

```java
// eg0/v.java:418-433
pc5.od7 od7Var = new pc5.od7();
pc5.p16 p16Var = new pc5.p16();
p16Var.d(bArr);            // 原生 normsg 序列化结果
od7Var.e = p16Var;         // 字段 2
if (z) {                   // z 恒为 true（eg0/v.java:406）
    pc5.p16 p16Var2 = new pc5.p16();
    p16Var2.d(hu3.q.INSTANCE.h());
    od7Var.f = p16Var2;    // 字段 3
}
vVar2.cj(i5, od7.toByteArray(), false);
```

字段号由 `pc5/od7.java` 的序列化代码确定（`pc5/od7.java:48-55`）：

| Protobuf 字段号 | Java 字段 | 内容 | Wire type |
| --- | --- | --- | --- |
| `2` | `pc5.od7.e` | `Q8("ceu_global", i)` 返回的原生 normsg 字节块 | `LEN`（`p16` 嵌套 bytes 消息） |
| `3` | `pc5.od7.f` | `hu3.q.INSTANCE.h()` 返回的附加原生块 | `LEN`（`p16` 嵌套 bytes 消息） |

`pc5.p16` 是只含 `bytes` 字段的包装消息，因此字段 2/3 在线上是
`field 2, wire type 2` / `field 3, wire type 2`。

### 1.7 网络出口

```java
// eg0/v.java:274-291
public void cj(int i, byte[] bArr, boolean z) {
    com.tencent.mm.modelbase.l lVar = new com.tencent.mm.modelbase.l();
    lVar.f1176c = hu3.q.INSTANCE.U6("Q\u001e\u001b\u0012W\u001b\u0011\u0019Y\u0018\u001d\u0010\u0000\u001e\u001d\u001c\t@\u000e\u0002\u0004F\u001a\u0002\u0016\n\u0016\u0017\u0001\r\t:0)?3?:3");
    lVar.f1177d = 771;
    lVar.a = new pc5.vw5();
    lVar.b = new pc5.ww5();
    ...
    pc5.vw5 vw5Var = oVarA.a.a;
    vw5Var.e = i;                      // 场景号，即 540999748
    vw5Var.d = new com.tencent.mm.protobuf.g("".getBytes());
    pc5.p16 p16Var = new pc5.p16();
    p16Var.d(bArr);                    // od7 序列化结果
    vw5Var.f = p16Var;
```

URI 字符串经 `hu3.h.U6` 解混淆（`com/tencent/mm/plugin/normsg/t.java:889-898`）：

```java
int length = str.length();
java.lang.StringBuilder sb = new java.lang.StringBuilder(length);
int i = 0;
while (i < length) {
    int iCharAt = str.charAt(i) ^ (-89);
    i++;
    sb.append((char) (iCharAt ^ ((byte) (~(i ^ length)))));
}
return sb.toString();
```

对本段密文独立复算得到：

```
out[i] = ((in[i] ^ 0xA7) ^ (uint8)(~(((i + 1) ^ L) & 0xFF))) & 0xFFFF
```

解密结果：

```
/cgi-bin/micromsg-bin/reportclientcheck
```

结论：

- 上传路径：`/cgi-bin/micromsg-bin/reportclientcheck`；
- 网络 func/type：`771`；
- 外层请求 `pc5.vw5`：字段 `e`（场景号，本例 `540999748`）、
  字段 `d`（空 bytes）、字段 `f`（`od7` 字节块）；
- 响应类型：`pc5.ww5`，处理回调 `eg0.c0`；
- 统计埋点 `u44.f.INSTANCE.idkeyStat(416L, 0L, 1L, false)`（`eg0/v.java:292`）。

## 2. 其他 normsg 上报口（同一发送器）

`eg0.v` 其余方法最终也走 `cj(...)` 或同族 CGI，包括：

| 入口 | 场景/计数 | 用途 |
| --- | --- | --- |
| `eg0.v.Z7`（`eg0/v.java:212`） | 组合字段 `i,i2,i4,i5,bArr,z` | 设备/环境结构化上报 |
| `eg0.v.qg`（`eg0/v.java:459`） | `WCProbe$Info.e(560)` | 原生探测块 560 |
| `eg0.v.ec`（`eg0/v.java:302`） | `cssi` 标签 | 上下文状态上报 |
| `eg0.v.hd/he`（`eg0/v.java:311,331`） | `byte[]` | 风控数据块 |
| `eg0.a0` | `eg0.o0.c(12, 86400000L, i)` | 24h 计数槽 12 |
| `eg0.y` | `eg0.o0.c(11, 86400000L, i4)` | 24h 计数槽 11 |
| `eg0.b0` | `eg0.o0.c(8, 86400000L, ...)`，默认 `10` | 24h 计数槽 8 |

## 3. 原生数据与最终结论

`MotionEvent` 在 `libwechatnormsg.so` 中由 JNI 类/方法/字段解析、
索引交换和 XOR 循环转换为场景状态，`dj` 再返回 `byte[]`；精确函数
边界、JNI 偏移、混淆种子、循环公式和输出组成见
[normsg-native.md](normsg-native.md)。该 native 块与附加块进入
`pc5.od7` 的字段 `2/3`，外层 `pc5.vw5` 再携带场景号 `540999748`、
空 bytes 和嵌套请求，最终由 func/type `771` 发往
`/cgi-bin/micromsg-bin/reportclientcheck`。
