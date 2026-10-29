# 逆向证据

## 1. 样本与哈希

| 文件 | 字节数 | SHA-256 |
|---|---:|---|
| APK | `92,254,498` | `6834433914f2ad469fc63b64b0bbd6ff4bc1f072377b3509be96e2cf046a3c2b` |
| `AndroidManifest.xml` | `190,072` | `8dc48ccb9db03f8063922ac73d51d5dd4929775828b32eda35e5c1a802cf4a5f` |
| `resources.arsc` | `1,892,528` | `d518c1bb47fe8289638f362a1171c782ca75d847512ac213903f51936dbac254` |
| `classes.dex` | `9,293,784` | `e4ea85cdaeeba852453c68e78e1a37a57ee3770fff04a3ce7469878d68eba463` |
| `classes2.dex` | `189,420` | `eee6605437caac7ee7f544e5f3f10888e1f136d53b8954fc3b19c17a4f5e0492` |
| `classes3.dex` | `2,269,836` | `31d82842620c96478840a5caa113da5199124247070c0faeb1b0b9cb3c17ed8a` |
| `classes4.dex` | `2,508,308` | `eab7d3e4a66f7ec9306d98ceb8305be15061e5740db53ff25d2cb2d18ad439f2` |
| `classes5.dex` | `4,839,048` | `8614ac717e2e33671b9745c309eb670c39a3eeee1df454f8e45ac173dc8c9e4d` |
| `classes6.dex` | `2,675,980` | `7f36ffd2d1c2536dc56e995f05471148974ff434cf9c08e8effca5aefde54419` |
| `classes7.dex` | `4,149,248` | `de6f5c9b49659b44f83adf64f8c2bc1621f9b11643504faf85c18a4eb4ac5818` |
| `classes8.dex` | `9,087,088` | `1d5c84d8478c7eac9c4c214609805c5ac40d0625d05c451bd5686c71d5db94f6` |
| `classes9.dex` | `5,236,752` | `62d318b93397dc9d02a2aeb6b2ba9a28204a44502cfdb840f30278ae94f59bc5` |
| `classes10.dex` | `4,103,280` | `e82a92b9103247444a3e57dc129a3e802ea391cf4652fb34ab152c0d91f77260` |
| `classes11.dex` | `4,671,984` | `26d060b505a61a477794ab0ee30f5db02d18c5e1d282b06d467ec5f3bd17bbdc` |
| `classes12.dex` | `4,943,580` | `5af271f9ff699417a72d4e63c17f9a283d4503d48118c3cfc5d68067f893cdc6` |
| `classes13.dex` | `6,726,552` | `847cffcbcd195dd22582880a34dc75280c17affb5eb7a53b3719ab1354b31e7b` |
| `classes14.dex` | `1,775,208` | `3299adcc191c7be2ac73177cf2c662cda5c816ccfc7250dce5d6c57da185b309` |
| `classes15.dex` | `2,165,556` | `fcb0aa1fd5432ba090ae6ed6223c2601218033778c3a5911549bda8c4d916d95` |
| `lib/arm64-v8a/libgoblin_6_1_1.so` | `2,156,224` | `ceef53a8bc8f893503b8e494775104a7b56ff81975601e4a0e064a2fd7ec2b4a` |
| `lib/arm64-v8a/libqlynx_v++.so` | `2,810,216` | `06903588b611156d873f1d1ffc15a246507269cd5d8ddd87dcc0f9860411a5cf` |
| `lib/arm64-v8a/libqrust.so` | `5,733,152` | `69411e58f083a2448fe2a8dff9968f08e66a6d2479ce061eba1b24f7fe0ad5e5` |
| `lib/arm64-v8a/libverify.so` | `5,824` | `863a838dbad76a8885082a056045f15302e917af9836c3c3ceb0b5714f13567a` |
| `lib/arm64-v8a/libpayenc.so` | `9,848` | `f68284400ad60f26413b3b3cbb574ef359c30f19a693f63b0e7476eda8eb1031` |
| `lib/arm64-v8a/libmmkv.so` | `588,296` | `58e0007042ef946979988cfe9cc94041cd3e735d92dc549ec0d87ac7fbb150e7` |
| `lib/arm64-v8a/libtiny_magic.so` | `84,168` | `94cde61fb52989568167e449a423837f70dd2e40cb3536b92a8cbbffdeff5b78` |
| `lib/arm64-v8a/libentryexpro.so` | `181,720` | `4f0a54e830db6f16a50770c1d3ad9bcba8d5b461161526132355f8e83f35758f` |
| `lib/arm64-v8a/libuptsmaddon.so` | `781,280` | `cd4aee3aef2e31ccfc3855e5aeb5caa782f06f23b56fdfc08c5f097602033fc8` |
| `lib/arm64-v8a/libmsaoaidsec.so` | `685,960` | `38757cc75418654893b029d8c0410c4a08cc143dcbbf9d0d387736075bec65d0` |
| `lib/arm64-v8a/libdiffpatch.so` | `82,792` | `32d17109d1c71d55e2962da50a66c3c1db60e1c414feb7bf6cc4512abab62ecf` |
| `lib/arm64-v8a/libhermes.so` | `2,249,600` | `e717f8c846168631364c7fcf003c65702ce1d14729b5d8b87766969602741f8e` |
| `lib/arm64-v8a/libjsc.so` | `10,173,344` | `e29f4fea1e46d14b479029c62248036b2455ab41495a51bc5b9b79ee2412b589` |

APK 解压后总大小 `260,515,880` 字节；条目分布：`res/` 5,303、`assets/` 395、
`META-INF/` 94、`lib/` 63、根目录 32、`org/` 18、`google/` 12、`kotlin/` 7、
`com/` 2、`okhttp3/` 2。无 `armeabi-v7a` / `x86` 目录 —— **仅 ARM64**。

## 2. `libgoblin_6_1_1.so` 证据

工具链：`readelf -SW/-dW`、`nm -D --defined-only`、`llvm-objdump -d`、
`pyelftools`、Unicorn（受控模拟，仅加载 APK 自带 `.so` 镜像）。

### 2.1 符号与节

| 符号 | 地址 | 说明 |
|---|---|---|
| `EnBufferLHint` | `0xa714` | `a71c: sub w9,w0,w8` … `a728: ret`，净效果恒等 |
| `DeBufferLHint` | `0xa72c` | 同上，另一组常数 |
| `DriftsandEn` | `0xa744` | 编码主函数 |
| `DriftsandDe` | `0xa9fc` | 解码主函数 |
| `Dea1907_resp` | `0xacd8` | 解码内部块 |
| `Ena1907_req` | `0x125f58` | 编码内部块 |
| `JNI_OnLoad` | `0xe7c` | 注册 + 自检 |
| `JNI_OnUnLoad` | `0x1c8c` | — |
| `register_handler` | `0xcac` | `sigaction(SIGSEGV)` |
| `en` | `0xd80` | `sand` 的 wrapper |

节：`.text` `0xc70` / `0x20b5cc`，`.rodata` `0x20c240` / `0xb6c`，
`.data` `0x21e000` / `0x8`。

### 2.2 `DriftsandEn` 的头部写入（`0xa7a0`–`0xa840`）

```asm
a7a0: mov  w11, #0x30390000
a7ac: movk w11, #0x3161
a7b4: strb w9,  [x21]          ; w9 = 5
a7bc: stur w11, [x21, #1]      ; "a1907"[0..3] = 'a','1','9','0'
a7b8: strb w10, [x21, #5]      ; w10 = 0x37 ('7')
...
a7ec: bl   adler32@plt         ; adler32(0,0,0)
a7f8: bl   adler32@plt         ; adler32(x, in, inLen)
a7fc-d838: lsr 56/48/40/32/24/16/8/0 + strb [x21,#6..#13]
a848: bl   gettimeofday@plt
a870: smulh x9, x8, #magic     ; usec / 29
a880: msub w8, w9, w10, w8     ; usec % 29
a894: ldr  w26, [x9, x8, lsl #2]   ; T29[usec % 29]
a8b8: smulh x9, x8, #magic     ; usec / 41
a8d4: msub w8, w9, w10, w8     ; usec % 41
a8d8: ldr  w27, [x9, w8, uxtw #2]  ; T41[usec % 41]
a934: bl   Ena1907_req@plt
a95c: bl   adler32@plt         ; adler32(0,0,0)
a970: bl   adler32@plt         ; adler32(x, inner, innerLen)
a978-a9b0: lsr 56..0 + strb [x21,#0xe..#0x15]
```

`0xa880` 的 `msub x8, x9, x10, x8`（`w10 = 29`）与 `0xa8cc` 的
`msub w8, w9, w10, w8`（`w10 = 41`）是两处取模。两次 `gettimeofday`
（`0xa858`、`0xa8a0`）取的是同一个 `tv_usec` 字段（`ldr x8, [sp, #0x20]`），
所以两个索引同源于微秒：实测固定 `usec` 只改 `tv_sec`，
`T29`/`T41` 的索引都不变。

### 2.3 `DriftsandDe` 的两道校验

```asm
aa28: subs w22, w2, #0x10       ; len - 16
aa30: b.hs aa3c                 ; 不足 16 字节 → 返回 1
aa3c-ab98: 逐字节 load + bic/eor/orn 位混合（x3 的 byte 0..15）
aba8: add  x24, x3, #0x10       ; body 起点
abac: sub  x25, x8, x9          ; x9 = -hdr[15]
abb0: bl   adler32@plt          ; adler32(0,0,0)
abbc: bl   adler32@plt          ; adler32(x, body, len-16)
abc0: cmp  x25, x0
abc4: b.ne acb4                 ; → 返回 7
abe0: bl   Dea1907_resp@plt
abe4: cbnz w0, acb8             ; → 返回 3
ac94: bl   adler32@plt
aca4: cmp  x21, x0
aca8: orr  w8, wzr, #6
acac: csel w0, wzr, w8, eq      ; 相等 → 0，否则 6
acb4: orr  w0, wzr, #7
```

128 位基向量探测（逐位置 1 后读 `x25`/`x21`）给出精确归属：
`hdr[0:8]` 的 64 个位只影响第二道比较值，`hdr[8:16]` 的 64 个位只影响
第一道比较值，两者互不串扰；`hdr` 之后的字节不影响任何一道。逐位映射是
标准大端读出 —— 第 *k* 字节的第 *j* 位映射到 `1 << (56 - 8k + j)`。
因此两道校验是**纯 64 位大端字段比对**，不含位混合：

```asm
abc0: cmp  x25, x0            ; x25 = BE64(hdr[8:16])，x0 = adler32(hdr[16:], 1)
ac94: bl   adler32@plt        ; adler32(明文, 1)
aca4: cmp  x21, x0            ; x21 = BE64(hdr[0:8])
```

40 组随机 16 字节头：`check1 == BE64(hdr[8:16])` 40/40、
`check2 == BE64(hdr[0:8])` 40/40。

### 2.4 `.rodata` 字符串编码

`0x20c240` 起 `0xb6c` 字节全部是 ASCII 十六进制字符，按字节 `XOR 0xFF`
解码后为可打印字符串。解码结果 41 条，含
`com/mqunar/core/basectx/application/QApplication`、`getContext`、
`getPackageName`、`getPackageManager`、`getPackageInfo`、
`(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;`、`signatures`、
`[Landroid/content/pm/Signature;`、`getInstance`、
`(Ljava/lang/String;)Ljava/security/MessageDigest;`、`SHA1`、`digest`。
JNI 方法名 `getCrc32`、`ePoll`、`dPoll`（`(Ljava/lang/String;)Ljava/lang/String;`）、
`espirt`、`roller`、`duch`、`getPayKey`、`version`、`drift`、`sand` 位于
`0x20c7a4`–`0x20c832`。`hehe : NNN` 形式共 6 条，是内部埋点。

### 2.5 两张完整性表（`.rodata`）

`T29` @ `0x20cc94`，29 项，首 8 项：
`c9d00381 ea1f0784 c412e189 377bb38b 115c7510 7e873091 4094a513 0bd52214`。

`T41` @ `0x20cd08`，41 项，首 8 项：
`fb1f9882 e88e9183 49f08f87 ad266488 390a1e8c 6800400e ede4a88f 478c6f13`。

两表均无重复元素。实测：`usec ∈ {0,1,29,40,41,58,123456}` 七组，
内部块前 4 字节（大端）**全部等于** `T29[usec % 29]`；在 `0xa898`/`0xa8dc`
两处载入后直接读出寄存器，`T29[usec % 29]` 与 `T41[usec % 41]` 与表项
逐项吻合。固定 `usec` 而变化 `tv_sec` 时两个索引都不变，故两表都由
**微秒字段**索引、与秒无关。`T41` 的值不以原始 4 字节形式出现在内部块中
（参与 `Ena1907_req` 的变换），因此不可从输出直接读出。

### 2.6 两个内部块的形态

`llvm-objdump` 统计（`--start-address/--stop-address` 精确限定）：

| 函数 | 字节 | 指令 | 间接分支 | `ret` | `B`/`BL` | `B.cond` |
|---|---:|---:|---:|---:|---:|---:|
| `Ena1907_req` | `0xe5ddc` | `235,383` | **0** | 1 | 3,273 | 12,192 |
| `Dea1907_resp` | `0x11add8` | `289,654` | **0** | 1 | 2,547 | 11,738 |

补充校验：按 4 字节字扫描 `(w & 0xfffffc1f) ∈ {0xd61f0000, 0xd63f0000,
0xd71f0000, 0xd73f0000}`（`br`/`blr`/`ret` 族）在两段内均得到 0 命中；
`0xd65f03c0`（`ret`）各 1 处。

### 2.7 辅助证据

- `JNI_OnLoad` 在 `0xf0c: cmp w0, #0` 后 `csinv w0, w20, wzr, ne`，
  `w20 = 0x10006`；`0xf3c` 起的自检函数比对两张表。
- `0x1bbc: ldr x9, [x9, #0x6b8]` 后 `blr x9` 即 `RegisterNatives`，
  `0x1bc4: mov w3, #0x12`（18 个方法）。
- `0xd1c` 的 `SIGSEGV` 处理：`ldr w19,[x1,#0x18]` 取 `siginfo` 里的 fd，
  `write(fd, {1}, 1)` 后 `close(fd)`。

## 3. `libqlynx_v++` 与 `libqrust`

| 项目 | 值 |
|---|---|
| `libqlynx_v++` 大小 / SHA-256 | `2,810,216` / `06903588b611156d873f1d1ffc15a246507269cd5d8ddd87dcc0f9860411a5cf` |
| `libqrust.so` 大小 / SHA-256 | `5,733,152` / `69411e58f083a2448fe2a8dff9968f08e66a6d2479ce061eba1b24f7fe0ad5e5` |
| `libqlynx_v++` 节 | `.text` `0xaaa50`/`0x17f7d8`；`.mytext` `0x22a228`/`0xb60`；`.rodata` `0x263bb0`/`0x36b84`；`.qnpv` `0x29a738`/`0x20` |
| 依赖 | `libEGL.so`、`libGLESv2.so`、`libz.so`、`liblog.so`、`libmediandk.so`、`libm.so`、`libdl.so`、`libc.so`；`BIND_NOW` |
| `JNI_OnLoad` | `0xf3808`，`bl 0x22aee0 <vmEntry@plt>`；返回值 `> 1` 则 `brk #1` |
| `Java_*` 导出 | **无**（动态注册） |

`.rodata` 中的短名块（`0x267950`–`0x2679d8`，NUL 分隔）：
`setSaveVal`、`init_fp`、`qje`、`risk_file`、`hf`、`jbp`、`uti`、
`get_opengl_info`、`init_base_env`、`root`、`cjd`、`get_fp`、
`build_env_deal`、`has`、`get_w`。

`0x2784a8` 处为 `QMOD\x02…` 自描述块，长 32 字节以上。

`j.uti` 的 `JNINativeMethod` 条目：`.data.rel.ro` 槽位 `0x23e5a8` 的重定位
指向 `.rodata 0x26797c`（`"uti"`），说明该方法经 `RegisterNatives` 注册而非
`Java_*` 导出。其余 5 个方法（`cd`/`ep`/`jcd`/`sepa`/`sfp`）的槽位未逐一定位
（**未完成**）。

**未复原**：`NUtils` 六个方法的内部实现。`libqlynx_v++` 的 `vmEntry` 是
统一的动态注册入口，方法体在 `.text` 中以间接 `blr` 调度的形式存在，
需要逐槽解析重定位表才能定位实现地址，本次未完成。

## 4. `com.qunar.rc` / `com.ctripfinance.risk.device` 证据

### 4.1 关键常量与位置

| 常量 / 字符串 | DEX 与偏移 |
|---|---|
| `97d7407497855043c7c8d55fcc40710b`（`Sepa.APP_TOKEN`） | `classes10.dex@0x25b3b2` |
| `/m/quser/upDevice.do` | `classes10.dex@0x2f9fdd` |
| `qr_alias` | `classes7.dex@0x351a74` |
| `qrc_sp`（`Store` 的 SharedPreferences 名） | `classes7.dex@0x351a7e` |
| `aardtroPdEaallt`（`StringUtil.f9345a` 固定 IV） | `classes.dex@0x6ca51c`、`classes6.dex@0x1af7f1` |
| `risk_install_uuid` | `classes.dex@0x77ec93` |
| `ls -al /sdcard/Android/data/ | head -n 50` | `classes7.dex@0x32861e` |
| `ZygoteInit` | `classes.dex@0x6ec4a1`、`classes4.dex@0x19a2d5` |
| `de.robv.android.xposed.XposedBridge` | `classes.dex@0x6f3de9`、`classes4.dex@0x19cd10` |
| `com.saurik.substrate.MS$2` | `classes.dex@0x6ed672`、`classes4.dex@0x19a8c3` |
| `signature_b_para_config` | `classes4.dex@0x1dbb93` |
| `append_data_b_para_config` | `classes4.dex@0x18b86d` |
| `hotdogParamToHeaderSwitch` | `classes4.dex@0x1a881e` |
| `qunar_hd` | `classes4.dex@0x1d5cfa`、`classes13.dex@0x5403ee` |

### 4.2 代码位置（`jadx` 反编译，按 DEX 归属）

| 类 | DEX |
|---|---|
| `qunar.lego.utils.{Pitcher,PitcherRequest,PitcherManager,Goblin,content.*}` | `classes15.dex` |
| `com.mqunar.libtask.{HotdogConductor,NetWorkInterceptor,Response}` | `classes4.dex` |
| `com.qunar.rc.{NUtils,RC}`、`com.qunar.rc.d.{j,Checker,d,ext}`、`com.qunar.rc.utils.{Cmd,Store}`、`com.qunar.rc.service.FpService` | `classes7.dex` |
| `com.mqunar.atom.defensive.Sepa`、`com.mqunar.atom.finance.risk.DeviceInfoRequest` | `classes10.dex` |
| `com.ctripfinance.risk.device.*` | `classes.dex` |
| `com.mqunar.atom.attemper.login.LoginTask` / `LoginParam` | `classes9.dex` |

### 4.3 关键代码片段

`HotdogConductor.prepareParams()`（`classes4.dex`）：

```java
if (str2 == null || !str2.contains("?")) this.hostUrl += "?qrt=" + this.tStr;
else                                     this.hostUrl += "&qrt=" + this.tStr;
...
TreeMap treeMap = new TreeMap();
treeMap.put("c", jsonString);
treeMap.put("b", str3);
treeMap.putAll(this.appendParams);
treeMap.put("ext", string);
...
setUrl(pitcherRequestBuild.getProxyUrl() + "?qrt=" + this.tStr);
```

`PitcherRequest.getLDate()`（`classes15.dex`）：

```java
SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z z");
String s = f.format(new Date());
for (int i = 0; i < s.length(); i++) {
    char c = s.charAt(i);
    if ((c <= 31 && c != '\t') || c >= 127) return "";
}
return s;
```

`EncryptUtil.b()`（`classes.dex`）：

```java
String d = d(str);                                     // 每字符 ^1 + Base64
sb.append(c(str2.getBytes(UTF_8)).substring(0, 8));    // MD5(gid) 前 8
sb.append(str3.substring(0, 8));                       // timestamp 前 8
Cipher.getInstance("AES/CBC/PKCS5Padding")
      .init(ENCRYPT_MODE, new SecretKeySpec(sb.toString().getBytes(), "AES"),
            new IvParameterSpec(StringUtil.f9345a.getBytes()));
return Base64.encodeToString(cipher.doFinal(d.getBytes(UTF_8)), 2);
```

`Cmd.a()`（`classes7.dex`）：

```java
Process p = Runtime.getRuntime().exec("ls -al /sdcard/Android/data/ | head -n 50");
// ...
QASMDispatcher.dispatchVirtualMethod(p, "java.lang.Process|destroy|[]|void|0");
```

## 5. 清单与资源证据

| 项目 | 数值 |
|---|---|
| `uses-permission` | 52 条，51 去重，9 条非 `android.permission.*` |
| 自定义 `<permission>` | 4 条 |
| 组件 | 438（activity 391 / alias 3 / service 23 / receiver 12 / provider 9） |
| 导出 | 26（无权限 22：activity 11 + alias 3 + service 4 + receiver 4） |
| `<queries>` | 183 个 `<package>`（182 去重）+ 12 个 `<intent>` |
| `<meta-data>` | 52 条 |
| `res/xml/*.xml` | 27 个 |
| `usesCleartextTraffic` | `true` |

`<queries>` 分类计数（按包名字符串匹配，存在性证据）：
Root/Hook/反检测 15、虚拟摄像头 4、银行金融类 34、支付 11、
OAID provider 6、其余知名应用 100+。

`assets/*.qp`：23 个，`85,564,564` 字节。

### 5.1 计数口径与被修正项

清单计数只按 XML 元素字面量统计，且**同类元素不重复计入**：

| 计数项 | 值 | 方法 |
|---|---:|---|
| `<activity>` | 391 | 元素数 |
| `<activity-alias>` | 3 | 元素数 |
| `<service>` | 23 | 元素数 |
| `<receiver>` | 12 | 元素数 |
| `<provider>` | 9 | 元素数 |
| 组件合计 | **438** | 上五项相加 |
| 显式 `android:exported="true"` | **26** | 字面量 `exported="true"` 26 处 |
| 其中无 `android:permission` | **22** | 逐组件读 `android:permission` |
| 其中带 `android:permission` | **4** | 同上 |
| 带 `intent-filter` 但省略 `exported` | **0** | 无隐式导出项 |

早期草稿曾把 3 个 `activity-alias` 同时算作 `activity`（得出 441 个组件、
29 个导出、25 个无权限），并误记为“alias 与同名 Activity 并存”。核对清单后
更正为 438 / 26 / 22，且 3 个 alias 名在清单里**只以 alias 出现**、
指向的 `targetActivity` 类名与之不同。本节表格即更正后的口径。

## 6. 设备端只读核对

只读（`ssh` 到自有设备，经 init 进程根目录前缀访问应用私有目录 `data/data/com.Qunar`）：

| 项 | 结果 |
|---|---|
| `databases/` 条目 | 33（含 15 个 journal/wal/shm），18 个 SQLite 格式文件：17 库 + 1 个 0 表 netty 空壳 |
| `integrity_check` | 18/18 个可打开库全部 `ok` |
| `shared_prefs/` | 50 个文件，684,312 字节，362 个键名，**全部明文 XML** |
| `files/kv_storage/` | 16 个 MMKV + 16 个 `.crc`，16,441,344 字节 |
| `files/` 总计 | 33 MB |
| `cache/` 总计 | 219 MB |

未写入任何文件、未修改任何数据库、未发起网络请求。
数据库行值、偏好值、MMKV 内容一律未采集。

## 7. 模拟与重放证据

使用 Unicorn 加载 APK 自带的 `libgoblin_6_1_1.so`，用 GOT 打桩替换
`gettimeofday`/`adler32`/`malloc`/`memset`/`memcpy`/`strlen`/`sprintf`/`strcpy`/
`strcat`/`crc32`/`free`/`sigaction`/`close`/`write`。

可重复结论：

1. `DriftsandEn` 对同一输入 + 同一 `gettimeofday` 输出**完全确定**，
   返回 `0`，`outLen = 22 + innerLen`。
2. 头部 6 字节恒为 `05 61 31 39 30 37`。
3. `hdr[6:14] == BE64(adler32(input, 1))`，6/6 组合成输入成立。
4. `hdr[14:22] == BE64(adler32(inner, 1))`，6/6 成立。
5. `inner[0:4] == BE32(T29[usec % 29])`，7/7 成立。
6. `DriftsandDe` 的输入是**去掉 6 字节魔数**的缓冲区；此时
   第一道校验 `BE64(hdr[8:16]) == adler32(hdr[16:], 1)` 与
   第二道校验 `BE64(hdr[0:8]) == adler32(明文, 1)` 均成立
   （随机头 40/40 + 40/40，真实信封全通过）。
7. 校验通过后 `DriftsandDe` 停在 `Dea1907_resp` 并返回 `3`：分派键
   `inner[0:4] = T29[usec % 29]` 不在 `Dea1907_resp` 的 2,161 个键值里
   （29/29 表项均不在），走默认分支。故 `sand` 产出的是**请求**信封、
   `drift` 消费的是**响应**信封，两者方向不同，
   **请求→响应往返本就不成立**；报告不宣称可重放。

## 8. 未发布内容

本报告不含：APK/DEX/SO 本体、反汇编全量文件、模拟器 trace、
真实请求与响应、Cookie、令牌、账号/设备/安装标识、`APP_TOKEN` 的使用方式、
内部主机名与内网 IP、真实城市库行值、`shared_prefs` 键值、MMKV 内容、
`.qp` 正文 JS、任何可用于重放或绕过的完整参数组合。
