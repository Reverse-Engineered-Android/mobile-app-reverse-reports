# 朋友圈 / 公众号 / 小程序 请求的共享密码学分发

样本：`com.tencent.mm` 8.0.78 / `versionCode=3180`，`arm64-v8a`。
JADX 源在受控分析环境内完成；公开页只保留可复现的类、方法、行号和字节格式。

## 1. 结论

朋友圈（`mmsns*`）、公众号（`mmbiz-bin/mp*`、`mmbiz-bin/appmsg*`）、
小程序（`mmbiz-bin/wxaapp*`）没有各自独立的加密实现。三者都经过同一个
请求打包器 `com.tencent.mm.modelbase.r2.G2(...)`（日志名
`MicroMsg.RemoteReq`），由该函数按 `jType` / cmdId 选择下列四条封包路径，
再交给 `libMMProtocalJni.so`。因此三类业务的**外层密码学机制完全一致**，
差异只在内层 Protobuf 消息类型和 URI。

## 2. 共享 CGI 清单

对完整 APK 的 17 个 DEX 提取字符串并去重后得到 2116 条
`/cgi-bin/...` 路径（提取结果留在受控分析环境），
其中本报告关心的三类：

| 业务 | 路径前缀 | 样例 |
| --- | --- | --- |
| 朋友圈 | `/cgi-bin/micromsg-bin/mmsns*` | `mmsnstimeline`、`mmsnsupload`、`mmsnsdownload`、`mmsnssync`、`mmsnspost` |
| 公众号 | `/cgi-bin/mmbiz-bin/mp*`、`/cgi-bin/mmbiz-bin/appmsg*`、`/cgi-bin/mmbiz-bin/bizattr/*` | `mp/getmpnews`、`appmsg/getmomentpoiinfo`、`bizattr/subscribemsg` |
| 小程序 | `/cgi-bin/mmbiz-bin/wxaapp*` | `wxaapp_getauthinfo`、`wxaapp/getshareinfo`、`wxaapp/autofill/deleteinfo` |

## 3. 打包器签名与参数

`com.tencent.mm.modelbase.r2.G2` 的实际签名（`r2.java:208`）：

```java
public boolean G2(int i, byte[] bArr, int i2, byte[] bArr2,
                   byte[] bArr3, int i4, boolean z)
```

- `i` = `jType`（业务/命令类型，例如 `701` ManualAuth、`702` AutoAuth、
  `775`、`763`、`3941`）；
- `i2` = **crypt algorithm**，`13` = AES-GCM、`14` = SM4-GCM；
- `bArr` = session key；`bArr3` = ECDH 公钥/密钥材料；
- `z` 影响 flag 位（见 §4）。

## 4. 分发顺序（判定依据的精确代码）

### 4.1 不加密直通

```java
// r2.java:245-252
if (i == 268369922) {
    this.i = ((mc5.ah) r10).toProtoBuf();
    r10.setBufferSize(r0.length);
    return true;
}
```

```java
// r2.java:267-272
if (ahVar.isRawData()) {
    this.i = protoBuf;
    r10.setBufferSize(protoBuf.length);
    return true;
}
```

`jType == 268369922` 或 `isRawData() == true` 时直接把 Protobuf 字节作为
请求体，不经过 `libMMProtocalJni.so`。

### 4.2 flag 位

```java
// r2.java:277-280
int i9 = i == 775 ? 0 : 6;
if (z) {
    i9 |= 1;
}
```

```java
// r2.java:436
int i11 = i == i10 ? i9 & (-3) & (-5) : i9;   // i10 = 775
```

即默认 flag `6`（bit1|bit2），`jType==775` 时清零后再次清 bit1/bit2；
`z` 为真时再置 bit0。

### 4.3 RSA / 混合路径（jType 701/702）

```java
// r2.java:320-366
if (i5 == 381) {
    if (i == 701) {                       // ManualAuth
        bArrE = e(uin, io4Var.f5769d, io4Var.e);
    } else if (i == 702) {                // AutoAuth
        bArrE = e(uin, vcVar.f5945d, vcVar.e);
    }
    ...
    if (i == 702) {
        MMProtocalJni.packDoubleHybrid(pByteArray, bArr2, r10.getDeviceID(),
            (int) uin, ahVar.getFuncId(), rsaInfo.f5152c,
            bArr10[0], bArr10[1], str11.getBytes(), str10.getBytes(),
            F6(), i9, ahVar.getRouteInfo(), this.m);
    } else {
        MMProtocalJni.packHybrid(pByteArray, bArr2, r10.getDeviceID(),
            (int) uin, ahVar.getFuncId(), rsaInfo.f5152c,
            bArr10[0], bArr10[1], str11.getBytes(), str10.getBytes(),
            F6(), i9, ahVar.getRouteInfo(), this.m);
    }
}
```

### 4.4 Hybrid ECDH 路径

```java
// r2.java:516-547
if (r10.useECDH()) {
    com.tencent.mars.xlog.Log.i(str3, "summerauths rsaInfo[%s] USE_ECDH[%s] engine[%s]", ...);
    jD = mc5.dg.d(bArr9);
    r10.setEcdhEngine(jD);
    boolean zPackHybridEcdh = MMProtocalJni.packHybridEcdh(
        pByteArray, bArr2, r10.getDeviceID(), (int) uin, ahVar.getFuncId(),
        mc5.dg.a(), UtilsJni.HybridEcdhEncrypt(jD, protoBuf),
        i9, ahVar.getRouteInfo(), this.m, 12);   // 最后参数 = crypt algo 12
    ...
}
```

`mc5.modelbase.j.c(cmdId)` 命中时也走同一路径（`r2.java:584-598`，
同样传 `12`）。

### 4.5 AES-GCM / SM4-GCM 路径（默认业务请求）

```java
// r2.java:437-466
int iGenSignature = (y8.L0(bArr3) || y8.L0(protoBuf)) ? i8
                    : MMProtocalJni.genSignature((int) uin, bArr3, protoBuf);
if (i2 == 13) {
    bArrAesGcmEncryptWithCompress = UtilsJni.AesGcmEncryptWithCompress(bArr8, protoBuf);
} else {
    bArrAesGcmEncryptWithCompress = protoBuf;
}
MMProtocalJni.pack(bArrAesGcmEncryptWithCompress, pByteArray, bArr8, i2,
                   bArr2, r10.getDeviceID(), (int) uin, ahVar.getFuncId(),
                   rsaInfo.f5152c, str11.getBytes(), str10.getBytes(),
                   iGenSignature, i11, ahVar.getRouteInfo(), this.e, this.m, 0, this.n);
```

`i2 == 14` 时走 SM4-GCM（`r2.java:672`）。

### 4.6 RSA 信息不可用时

```java
// r2.java:799-816
if (rsaInfo.e()) { ... }
boolean zPackHybridEcdh3 = MMProtocalJni.packHybridEcdh(
    pByteArray, bArr2, r10.getDeviceID(), (int) uin, ahVar.getFuncId(),
    mc5.dg.a(), protoBuf, i9, ahVar.getRouteInfo(), this.m, 11);  // algo 11
```

## 5. RSA 公钥材料

`mc5.si`（日志名 `MicroMsg.RsaInfo`）结构：

```java
public class si {
    public final String a;   // exponent
    public final String b;   // modulus (hex)
    public final int f5152c; // version
    public boolean e() { return this.f5152c == 0 || y8.J0(this.a) || y8.J0(this.b); }
}
```

内置两把 2048-bit RSA 公钥，指数均为 `010001`（65537）：

| 方法 | version | modulus 前 32 hex | modulus 后 16 hex |
| --- | --- | --- | --- |
| `mc5.si.a()` | `199` | `ADA9E573417691226521F9FF1B3732DF` | `40085E6FAED67FCF` |
| `mc5.si.d()` fallback | `200` | `9357B6A18EE981DDA2C3CBBF39F5D308` | `65EF9D50D29AE5ED` |

`mc5.si.d()` 先从 `rsa_public_key_prefs` SharedPreferences（`keye`/`keyn`/
`version`/`client_version`）读取，仅当 `e()` 为假（key 可用）时才使用，
否则回落到内置 version `200` 密钥；旧密钥在 `client_version > 637665843`
时会被清除（`u44.f.INSTANCE.idkeyStat(148L, 38/39L, 1L, false)`）。

`e()` 为真表示 RSA 参数缺失或 version==0，此时分发到 §4.6。

## 6. cmdId 白名单

`com.tencent.mm.modelbase.j.c(int i)`（`j.java:13-48`）返回 `true` 的 cmdId：

```
252, 616, 617, 618, 627, 763, 784, 930, 931, 987, 989, 997,
3824, 3941, 9613, 9931, 12106, 13456, 16384
```

命中该集合且 `useECDH()` 未成立时，仍走 `packHybridEcdh(..., 12)`。

## 7. 签名

```java
// r2.java:437, 633
MMProtocalJni.genSignature((int) uin, bArr3, protoBuf)
```

第三个参数是**明文** Protobuf（未 AES-GCM 前），第二个参数是 session key。
返回的 `int` 作为 `pack(...)` 的 `iGenSignature` 参数写入包头。
`bArr3` 或 `protoBuf` 任一为空时签名取 `0`（`r2.java:437` 的三元分支）。

## 8. 与外层包头的衔接

`pack` / `packHybrid` / `packHybridEcdh` / `packDoubleHybrid` 最终都进入
`libMMProtocalJni.so` 的 12 字节 TLV 包头（magic `0x81`、checksum、size、
reserved），算法枚举与 checksum 公式见
[mmprotocaljni-native.md](mmprotocaljni-native.md)。

传输层由 `libwechatnetwork.so` 承载（`DT_NEEDED`：`libssl.so`、
`libcrypto.so`、`libcronet.119.0.6045.214.so`、`libmarsquic.so`、
`libProtobufLite.so`、`libmarscomm.so`）。

## 9. 最终结论

- 客户端侧的算法选择、RSA 材料、签名输入、flag 和 ECDH 组已由本页与
  [mmprotocaljni-native.md](mmprotocaljni-native.md) 给出；
- 服务端解密顺序、阈值和降级策略属于服务端控制面，不在客户端静态样本中；
- `HybridEcdhEncrypt(jD, protoBuf)` 使用 P-256 ECDH，共享密钥由
  `computerKeyWithAllStr` 的 private/public 字符串计算，格式见
  [mmprotocaljni-native.md](mmprotocaljni-native.md)。
