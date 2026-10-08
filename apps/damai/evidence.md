# 逆向证据

## 1. 样本与工具链

### 1.1 文件身份

| 文件 | 字节数 | SHA-256 |
|---|---:|---|
| `damai-9.0.35-arm64.apk` | 113,422,261 | `dd33ae183903fb37b9761266d892f34767b3dd4b493fcf5efec0ae169969c87f` |
| `classes.dex` | — | `1b67f3357b4849486f27eb35c2051f37ed06eaecad1bd3921abc5465d46c9bba` |
| `classes2.dex` | — | `8c20543ffdfe0240b21b825588a89ac6f6852cfb0b3b75c075d80c1335665ae5` |
| `assets/data.png` | 34,310,723 | `708a11bd77669dd762fa37ee94994b5bedffde2fab84870a66bd95a2be30bfcc` |
| 解码载荷 ZIP | 126,199,536 | `7d4645849071f560e632d072cbdb6f19a4397d6221f61f3042f34bcef03999a2` |

清单解析结果：

```text
package        cn.damai
versionName    9.0.35
versionCode    109003500
minSdk/target  23 / 35
main activity  cn.damai.launcher.splash.SplashMainActivity
permissions    58
activities     322
services       45
receivers      19
providers      14
native libs    75
```

### 1.2 静态流程

1. 校验 APK、shell DEX、`assets/data.png` 和载荷 ZIP 的 SHA-256。
2. 解码 manifest 与 resources，解析权限、组件和 intent-filter。
3. 解出 25 个 payload DEX，用 JADX 还原 37,487 个 Java 文件，
   共 115,225 个类描述符。
4. 聚合 655 个唯一 MTOP API、网络/认证/上传下载调用面。
5. 对符号化 ARM64 native 库执行 `readelf` / `objdump`，记录导出、
   依赖、导入和函数地址。
6. 对 2,068 个 `Method not decompiled` 残留按完整签名分类，
   并单独筛出密码学信号。
7. 以只读方式检查设备端数据库文件头、完整性、schema 和行数；
   不读取、不发布业务行值。

整个过程没有发送任何业务 HTTP/MTOP 请求，没有登录、搜索、下单、
购买、票夹刷新或真实测试。

## 2. 加固外壳的可重放解码

`com.ali.mobisecenhance.ld.dexmode.ShellDexMode.decodeFile(File,File)`
读取 `assets/data.png` 的字节并输出解码 ZIP。验证得到的格式是：

```text
前缀：明文 ZIP，PK\x03\x04 起始
后缀：最后 1024 字节被 XOR
密钥：该字节在后缀中的下标低 8 位
```

形式化写为，其中 `i` 是后缀下标、`n=1024`、`L` 是原文件长度：

```text
decoded[L-n+i] = encoded[L-n+i] XOR (i & 0xff), 0 <= i < n
```

验证结果是：

```text
data.png                  34310723 bytes
decoded ZIP               126199536 bytes
decoded ZIP SHA-256       7d4645849071f560e632d072cbdb6f19a4397d6221f61f3042f34bcef03999a2
payload DEX count         25
```

类中出现的 `1024` / `8192` 来自
`PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID` 与
`ACTION_PLAY_FROM_URI` 常量，只用于构造后缀长度/索引，不是密钥。

### 2.1 shell RC4 的调用闭包

`com.ali.mobisecenhance.ld.util.RC4` 声明：

```text
KSA：256 元素状态表逐轮交换
PRGA：i/j 双游标、S[i]/S[j] 交换、输出 S[(S[i]+S[j]) & 0xff]
```

对 shell DEX 与 25 个 payload DEX 的全部方法引用做符号统计，
`RC4.encrypt` / `RC4.decrypt` 的 Java 调用点为 0；外壳的
`decodeFile` 使用上面的逐字节 XOR。原生
`libcn.damai_shell_alijtca_plus.so` 中的 RC4 同样是已声明 surface，
没有发现加载/调用它来解码 payload 的证据。结论是
**“已分析、未使用”**，不是“保留未知加密”。

## 3. 其余密码学 surface

### 3.1 图像与 SVG

Java 桥：

```java
// com/real/image/decrypt/ImageDecrypt.java
nativeDecryptImage(bytes, key, "aes128-ctr");
nativeDecryptKey(safeKey, timestamp, "aes128-ecb");
nativeDecryptSvg(svg, key);
```

`com/real/svg/decrypt/SvgDecrypt` 使用同一把
`nativeDecryptKey(..., "aes128-ecb")` 派生 key，再调用 SVG 解密。

符号化 native：

| 符号 | 地址 |
|---|---:|
| `buildKeyKey` | `0x29c4` |
| `nativeDecryptKey` | `0x2dc0` |
| `nativeDecryptSvg` | `0x2ffc` |
| `nativeDecryptImage` | `0x3240` |
| `str_decrypt` | `0x3b80` |
| `svg_decrypt` | `0x481c` |

实现依赖 SHA-256、SM3、SM4、AES-CBC/ECB/CTR 和 Base64；每个算法
都有对应输入/输出或调用链，未发现只以常量名伪装、无法说明用途的
算法实现。

### 3.2 UT/Audid RC4

`UTBaseRequestAuthentication` 中的 RC4 有完整 KSA/PRGA，key 来自
客户端常量，用于本地 UT 默认值编码；它不是登录认证或服务端签名。

### 3.3 TLS 与签名

- `libtb_crypto.so` 是 BoringSSL 实现，服务于 TLS/通用 crypto。
- MTOP `x-sign` 的 base string、MD5、HMAC-SHA1 和 SecurityGuard
  请求类型见 [auth.md](auth.md)。
- `libmtguard_log.so` 还原出的 DEX 加载器和 `libmtguard.so` 的载荷
  契约在静态分析中已归入安全/遥测 surface；它不改变 HTTP 请求报文
  格式。

### 3.4 密码学结论

报告涉及的加密/混淆 surface 均有以下之一：

1. 可重放字节公式与哈希；
2. Java 参数到 native 符号/地址的完整桥接；
3. 标准算法与明确的输入输出；
4. Java/native 调用点统计后确认“声明但未调用”。

**没有保留未分析清楚的加密或混淆代码。**

## 4. JADX 残留的完整归类

JADX 报告 2,068 个唯一 `Method not decompiled` 签名，涉及 1,577 个
文件。按完整签名进行确定性归类，互斥结果如下：

| 类别 | 数量 |
|---|---:|
| 三方 SDK / 基础设施 | 672 |
| 业务 / 票务 | 378 |
| UI / 媒体 | 312 |
| 并发 / 系统 | 223 |
| 统计 / 日志 / 遥测 | 181 |
| 网络 / 下载 | 177 |
| 数据库 / 存储 | 52 |
| SecurityGuard / 安全框架 | 27 |
| 签名 / 摘要 / 加密用途 | 27 |
| 实名 / 生物特征 | 11 |
| BehaviX / 设备风险 | 8 |
| 外壳 / 加固 | 0 |
| 未分类 | 0 |

对 `encrypt|decrypt|cipher|signature|digest|md5|sha|rc4|aes|sm[234]`
等宽关键词筛选出 52 个带密码学信号的残留：其中 27 个落入
SecurityGuard/安全框架，27 个落入“签名/摘要/加密用途”，两个类别
有关键词交叉。它们分别是：

- AndroidX/Kotlin/三方库的校验或序列化方法；
- SecurityGuard 的标准组件调用；
- 报告已单列的签名、摘要、图像/SVG、UT 和 shell surface；
- 没有剩余“输出不可知”的加密函数。

以下关键残留已逐项闭包：

| 残留 | 闭包 |
|---|---|
| `SecurityGuardManagerWraper.findThreeMonthHistoryAccounts` | 只影响历史登录账号查询容器，不参与报文加密 |
| `updateLoginHistoryIndex` | 更新本地登录历史索引 |
| `RecommendRule.getSkuScore` | 用恢复版 Java 对照 native 评分，分支见 [risk.md](risk.md) §3 |
| `PicturesDeviceEvaluatorKt.getDeviceScore` | 输出设备评分数值，不含未知密码变换 |
| `tb.wt4.a(...)` | 归入业务/系统签名辅助，没有独立密钥交换或报文加密 |
| shell RC4 | 全 DEX 无调用点，外壳使用明文前缀 + 后 1024 字节 XOR |

分类以完整签名为主，不把 UI、并发或数据库残留误标为加密实现。

## 5. 精确证据索引

### 5.1 网络与认证

| 证据 | 位置 |
|---|---|
| URL 拼接 | `AbstractNetworkConverter.buildBaseUrl()` |
| 查询串 | `NetworkConverterUtils.createParamQueryStr()` |
| 参数与头映射 | `InnerProtocolParamBuilderImpl` / `InnerNetworkConverter.headerConversionMap` |
| 655 API | 唯一 `apiName` 聚合，见 `mtop-apis.txt` 类型的静态清单 |
| 签名 base string | `InnerSignImpl.convertInnerBaseStrMap()` |
| HMAC-SHA1 降级 | `LocalInnerSignImpl` |
| 中间层与 AVMP | `InnerSignImpl.getSign()` / `getAvmpSign()` |
| 登录 token 链 | Havana、mlogin、UCC、passkey/biometric 调用面 |

### 5.2 风控

| 证据 | 位置 |
|---|---|
| 七项环境检测 | `com.ali.security.RuntimeProtector` + `libsecurity-wrapper.so` |
| 检测值传播 | `RecommendRule.getBeyerData():589-613` |
| 评分阈值 | `ScoreOrangeConfig:41-84` |
| SKU/确认分支 | `RecommendRule:638-713,795-907` |
| native 对拍 | `TocNative 0x3085c/0x31e3c/0x32564/0x33cb8` |
| 419/420 | `AntiAttackAfterFilter:32-75` / `AntiAttackHandlerImpl` |
| 接口锁 | `ApiLockHelper.LOCK_PERIOD = 10` |
| 调用采样 | `PrivacyDoubleListDelegate` JSON `pn/rid/act/lmt/crt/dh` |

### 5.3 隐私与权限

| 证据 | 位置 |
|---|---|
| 同意入口 | `SplashMainActivity:566-569` |
| 拒绝后退出 | `SplashMainActivity:419-449` |
| 同意后初始化 | `SplashMainActivity:451-462` |
| 同意状态 | `tb.nz2:44-54,73-76` |
| 坐标接口白名单 | `cn/damai/common/net/mtop/Util:82-100` |
| 广告开关 | `PrivacyCommonUtils:13-39` |
| 运行时权限分发 | `PermissionDelegateActivity:73` |

## 6. 设备端只读数据库证据

设备端复制文件先检查 SQLite 文件头，再以只读 URI 打开并读取
`sqlite_master`；未发布行值。

| 数据库 | SHA-256 |
|---|---|
| `accs.db` | `b56cb456e5ee9dcfbf51036f9165a6abde641891ed84def7eba9802930531b81` |
| `data_cache.db` | `25492afb32c39da2c6a4eea256ca9ea4ce4823433d779f927cd99b7deb3c4938` |
| `message_accs_db` | `d1fa1c275a20bc4bfa18287f624f8c6d4abd88a8ec97d7b91cd077ada10e3469` |
| `ticketlet.db` | `c7d92c0b9331a63a208ae2c70ee5774fa209210e20d59c0c9462dc24c5f68236` |
| `ut.db` | `e679c56aed3458246ffef9c481173424280b6ce87f5269448c65886ba0d1e3ca` |
| `yk_gaiax.db` | `7cd166e07ecafc35ef0ad4e94a145ec89270fdbae059f9e21669808e250c233a` |

实际 schema：

```text
accs.db
  traffic(_id,date,host,serviceid,bid,isbackground,size)     0 rows

data_cache.db
  data_cache(_id,type,content,timestamp,expire,ret_code,
             ret_msg,channel)                                 1 row

message_accs_db
  message(...)                                                0 rows
  accs_message(...)                                           0 rows

ticketlet.db
  android_metadata                                            schema only

ut.db
  ap_alarm, ap_stat, ap_counter, utap_system, onlineconfig,
  alarm_temp, counter_temp, stat_temp, log, stat_register_temp

yk_gaiax.db
  yk_template_v2                                             150 rows
  yk_assets_template_v1
  room_master_table
```

这证明当前设备样本存在长连接流量、推送、统计配置和动态模板缓存
落盘；它不证明其他设备一定有相同行数，也不公开任何内容字段。

## 7. 证据等级

- **已验证**：文件/DEX/载荷哈希、manifest 计数、API 数量、字节解码
  公式、Java/native 方法地址、静态阈值、schema、导出组件数。
- **结构已证实**：native 风控 wrapper 转发关系、Java/native 评分
  对拍、外壳解码后 ZIP 结构、调用链与参数映射。
- **不可证**：服务端实际校验、评分权重、封禁动作、隐私政策服务端
  版本、真实请求内容；本报告未做任何实际测试。
