# 大麦 Android 9.0.35 逆向研究报告

## 1. 最终结论

### 1.1 样本

| 项目 | 值 |
|---|---|
| 包名 | `cn.damai` |
| versionName / versionCode | `9.0.35` / `109003500` |
| minSdk / targetSdk | `23` / `35` |
| APK SHA-256 | `dd33ae183903fb37b9761266d892f34767b3dd4b493fcf5efec0ae169969c87f` |
| APK 字节数 | `113,422,261` |
| 2026-10-08 Android 元数据 | `versionName=9.0.35`，下载文件名 `cn.damai_9.0.35.apk` |
| 签名证书 SHA-256 | `4ACD9A208AF31123608CF1355AC63D53E27547387E4E254BCD232E72EFE2E3C9` |
| 加固 | 阿里 mobisecenhance / alijtca `3.35.2`，载荷在 `assets/data.png` |
| 载荷 DEX | 25 个，解压后 `126,199,536` 字节 |
| 业务源文件 | JADX 还原 `37,487` 个 `.java` |
| 类描述符 | `115,225` |
| MTOP 接口名 | `655` 个唯一 |
| 声明权限 | `58` |
| activity / service / receiver / provider | `322` / `45` / `19` / `14` |
| 显式 `exported=true` | `79`（activity 52、service 15、receiver 11、provider 1） |
| ARM64 native 库 | `75` |
| JADX 残留方法 | 2068 个签名，全部归类 |

### 1.2 加固外壳与混淆

APK 里的应用代码只有一份，且被整体加密。根 `classes.dex`、`classes2.dex`
是外壳与第三方库；业务代码在 `assets/data.png`。外壳类
`com.ali.mobisecenhance.ld.dexmode.ShellDexMode.decodeFile(File,File)` 的算法是
**逐字节 XOR**，密钥为文件内索引低 8 位，常量来自
`PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID = 1024` 与
`ACTION_PLAY_FROM_URI = 8192`；详细公式与哈希在
[evidence.md](evidence.md) §2。外壳中另声明了 native RC4
(`com.ali.mobisecenhance.ld.util.RC4` → `libcn.damai_shell_alijtca_plus.so`)，
但在外壳与载荷的全部 DEX 中**没有任何 Java 调用点**，`decodeFile` 也不使用它；
该 surface 在 [evidence.md](evidence.md) §4.3 单独标记为“已声明未使用”。

### 1.3 网络与协议

请求走 MTOP（`mtopsdk`）。基础 URL 由
`AbstractNetworkConverter.buildBaseUrl()` 拼装：

```text
protocol + domain + "/" + entrance + "/" + apiName + "/" + version + "/"
```

- 域名由 `cn.damai.ultron.net.DMQueryKey.setDomain()` 按环境选择：
  `mtop.damai.cn`（线上）、`pre-mtop.damai.cn`（预发）、`daliy-mtop.damai.cn`
  （测试）；旧的 `https://mapi.damai.cn` 与新 `https://gw.damai.cn` 出现在
  `cn/damai/net/DamaiDataAccessApi.java` 的 `IP` / `NEW_IP`。
- 入口为 `EntranceEnum`：`gw` / `gw-open`，默认 `GW_INNER`。
- 参数在 `InnerProtocolParamBuilderImpl.buildParams()` 装配：`utdid uid
  reqBizExt appKey data t api v sid ttid deviceId lat lng extdata x-features
  routerId placeId openBiz miniAppKey reqAppKey accessToken openBizData`，再由
  `buildExtParams()` 追加 `netType NQ umidToken x-app-ver x-orange-q
  x-app-conf-v user-agent client-trace-id client-falco-id f-refer=mtop
  x-netinfo x-page-name x-page-url x-page-mab`。
- 参数→请求头映射见 `InnerNetworkConverter.headerConversionMap`（38 项，
  `sid→x-sid`、`t→x-t`、`sign→x-sign`、`wua→wua`、`x-mini-wua→x-mini-wua`
  等）。`buildRequestHeaders()` 逐值 `URLEncoder`，并把 `lat`/`lng` 合并为
  `x-location = lng,lat` 后从参数表移除。
- 查询串由 `NetworkConverterUtils.createParamQueryStr()` 生成，标准
  `URLEncoder.encode(k,"utf-8") + "=" + encode(v)`，以 `&` 连接。

接口族与字段见 [network.md](network.md)。**未发任何请求**，URL 均为源码
字面量。

### 1.4 认证

签名走 `mtopsdk.security.InnerSignImpl`，四种载荷：

| 方法 | base string | 后端 |
|---|---|---|
| `getMtopApiSign` | `convertInnerBaseStrMap(map, appKey, z)` | `LocalInnerSignImpl` 直接 HMAC-SHA1 或 SecurityGuard `requestType=7` |
| `getSign` | 同 base string 的 `INPUT` 槽 | MiddleTier `getSign(data, env, appkey)` 取 `x-sign` |
| `getAvmpSign` | 已算出的 `sign` | AVMP 实例 `"mwua"/"sgcipher"` 的 `invokeAVMP("sign",…)` |
| `getSecBodyDataEx` | `data, appKey, authCode, params, flag` | `getSecurityBodyDataEx(...,flag,env)` |

Base string 字段顺序（`convertInnerBaseStrMap`，23 字段版本）为
`utdid & uid & reqBizExt & appKey & MD5(data) & t & api & v & sid & ttid &
deviceId & lat & lng & [extdata] & x-features & routerId & placeId & openBiz &
miniAppKey & reqAppKey & accessToken & openBizData`，其中 `MD5(data)` 是
`SecurityUtils.getMd5()`（Java `MessageDigest("MD5")` over UTF-8，小写 hex）。
`LocalInnerSignImpl` 的 15 字段版本同前缀但不含后 8 个字段。

登录接口族、token 链与本地持久化见 [auth.md](auth.md)。

### 1.5 风控

完整风控面在 [risk.md](risk.md)，包含：

- SecurityGuard 组件接口与 native 边界（`libsgmainso-6.8.260704.so` 等）。
- BehaviX 行为评分：`RecommendRule.getSkuScore` / `getConfirmScore` 的
  精确阈值与加减分，默认配置在 `ScoreOrangeConfig`（`coScoreManualCancelUser
  = -50`、`coScoreForZeroTime = 500`、`coThresoldForLessThan = 100`、
  `coScoreForLessThan = 50`、`coThresoldForGreaterThan = 1000`、
  `coScoreForGreaterThan = -50`；`skuScoreForManualSwitch = -50`、
  `skuScoreForZeroTime = 500`，阈值 200/100）。
- 设备画像：`RecommendRule.getBeyerData()` 调 `YyBeyer` →
  `com.ali.security.RuntimeProtector`（native `libsecurity-wrapper.so`）
  的 `checkHook / checkRoot / checkDebug / checkInject / checkEmulator /
  checkMem`，结果写入 `isHook/isRoot/isDebug/isInject/isEmulator/isMem`。
- 风险上下文：登录/扫描、UCC、Alipay、Havana 分别把
  `wua/t/umidToken/设备型号与屏幕/utdid/scanfaceWua` 按三种不同的
  `riskControlInfo` JSON 格式发送，精确字段和调用点见
  [risk.md](risk.md) §5.1。
- 反爬惩罚队列：`mtopsdk.mtop.antiattack.AntiAttackHandlerImpl` 与
  `ApiLockHelper`，错误码 `ANDROID_SYS_API_41X_ANTI_ATTACK`。

### 1.6 上传下载

上传面：头像 `mtop.damai.wireless.user.uploadHeadImg`、评论图片、实名核验
`mtop.verifycenter.rp.upload`（材料经 `UploadFileModel` + 服务端签发的
`UploadFileConfigParams` 走对象存储）、日志 OSS 上传与埋点上传。
下载面：座位图/VR 图片（`libimage_decrypt.so`，`aes128-ctr` 解密 +
`aes128-ecb` 派生 key）、SVG 座位图（`libsvg_decrypt.so`）、票夹二维码图片、
动态包与升级包。字段级范围见 [transfer.md](transfer.md)。

### 1.7 越权 / 提权 / 未经告知 / 超范围

结论矩阵在 [permissions.md](permissions.md) 与 [privacy.md](privacy.md)。
要点：未发现系统 UID 提权闭环；`REQUEST_INSTALL_PACKAGES` 与
`EXTERNAL_STORAGE` 属于高风险授权面而非提权；隐私闸门在
`SplashMainActivity.initView()`（`nz2.d()` 为假先弹协议），同意前不进入
`initSetting()`。

### 1.8 完成度

逐项核对见 [completeness.md](completeness.md)。全部结论限定在客户端代码证据层级；服务端评分、留存与处罚由服务端执行，
不写入客户端事实断言。
