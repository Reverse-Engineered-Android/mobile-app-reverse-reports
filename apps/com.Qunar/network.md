# 网络与协议

## 1. 调查边界

本页只使用 APK 内 DEX、资源、`assets/*.qp` 清单与 native 字符串/符号。
所有 URL、功能键与字段名都是反编译字面量或调用点，**不代表本次验证过服务端可达性**。
未登录、未构造或重放任何请求、未抓取真实流量。内部 corp 主机名已脱敏为
`<corp-host>`。

## 2. 出口与分派

应用没有“业务 URL 直连”的常规路径：所有业务请求先被 `HotdogConductor`
组装成 Pitcher 请求，再 POST 到 slugger 代理。

| 常量 | 值 | 引用 |
|---|---|---|
| `PitcherManager.PROXY_URL_10010` | `https://slugger.qunar.com/slugger-proxy` | `qunar/lego/utils/PitcherManager.java` |
| `PitcherManager.PROXY_URL_OTHER` | `https://minislugger.qunar.com/slugger-proxy` | 同上 |
| `PitcherManager.BETA_PROXY_URL` | `https://cptest.qunar.com/slugger-proxy` | 同上 |
| `ReleaseEnvironment.hotdogUrl` | `http://<corp-host>/fca` | `com/mqunar/atomenv/env/ReleaseEnvironment.java` |
| `RetryTest.RETRY_URL` | `https://ccptest.qunar.com/slugger-check/q/h` | `com/mqunar/qapm/network/netretry/RetryTest.java` |
| `QutuiLog.SLUGGER_BETA` | `https://cptest.qunar.com/slugger-proxy` | `com/mqunar/qutui/QutuiLog.java` |
| QAV 上报 | `https://minislugger.qunar.com/slugger-proxy` | `com/mqunar/qav/uelog/DefaultSender.java` |

`HotdogConductor.prepareParams()` 是唯一的分派点：

```java
if (hostUrl == null || !hostUrl.contains("?")) hostUrl += "?qrt=" + tStr;
else                                        hostUrl += "&qrt=" + tStr;
...
setUrl(pitcherRequestBuild.getProxyUrl() + "?qrt=" + this.tStr);
setContent(NetWorkInterceptorManager.getInstance().buildRequetContent(pitcherRequestBuild.getContent()));
```

代理域名由 `PitcherOkHttpClientManager` 按请求选择（10010 通道 vs 其它业务线），
`PitcherManager.setDefaultProxyUrl()` 可在运行期覆盖，`lockUrl()` 之后不可再改。

## 3. 协议格式

### 3.1 明文层（`AbsContentStruct.pack()`）

```
"hdog" (0x68 0x64 0x6f 0x67)
u32 little-endian  manifestLength
manifest (UTF-8 JSON)
content  (字节流)
```

### 3.2 `normal` 清单

`NormalContentStruct` 按传入 `Map` 的顺序把非空值依次拼接，manifest 记录每段的
字节偏移：

```json
{"route-type":"hotdog","data-type":"normal","version":"1.0.0",
 "params":{"<key>":{"start":<int>,"length":<utf8Bytes>}, ...}}
```

`HotdogConductor` 传入的是 `TreeMap`，所以实际顺序为字典序：

```java
TreeMap treeMap = new TreeMap();
treeMap.put("c", jsonString);          // cparam JSON
treeMap.put("b", str3);                // 加签后的 b 参数
treeMap.putAll(this.appendParams);     // DataPip 下发的附加键
treeMap.put("ext", string);            // qunar_hd 存储里的 ext
```

键序即 `b, c, ext`（`appendParams` 若有则插在中间）。`start` 按 UTF-8 字节累计，
不是字符数。

### 3.3 `trans` 清单

带 `FormPart`（文件流或 multipart 字段）时改用 `TransferContentStruct`：

```json
{"route-type":"<routeType>","data-type":"trans","version":"1.0.0",
 "params":{"content-type":"<mime>","body":{"start":0,"length":<n>}}}
```

`bParts` 包装阶段会依次追加 `c`、`b`、`v`、`appendParams`、`ext` 五个
`FormPart`，全部经 `URLEncoder.encode(..., "utf-8")`。

### 3.4 加密层

```java
// Pitcher.encodeContent
Goblin.sand(ZLib.compress(pitcherRequest.getContent()))
// Pitcher.buildResult
Goblin.drift(responseBody); 然后 ZLib.decompress(...)
```

`libgoblin_6_1_1.so` 的 `sand`/`drift` 布局与校验见 `risk.md` §3。

## 4. 请求头

`PitcherRequest.getHeaders()` 精确构造：

| 头 | 值 / 条件 |
|---|---|
| `Content-Type` | 固定 `application/x-www-form-urlencoded` |
| `Target-Url` | **仅当** `routeType != "hotdog"` 时写入真实 URL |
| `L-Date` | `new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z z")`；逐字符检查，任一字符 `<=31`（`\t` 除外）或 `>=127` 即返回空串 |
| `Accept-Encoding` | 调用方未指定时补 `identity` |
| 染色头 | `MiniFlowPLTDyeingMng.getInstance().getDyeingHeader()`（灰度分流） |

`hotdogParamToHeaderSwitch` 打开（默认开，`DataPipStorage` 键
`hotdogParamToHeaderSwitch`，值不为 `"false"` 即启用）时，cparam 里的 `fp`
被 `C_PARAM_TO_HEADER_KEY_LIST` 移到请求头，并从 JSON 中移除；非 ASCII 值会被
丢弃并记 `cparam` 日志。

## 5. cparam（`c` 字段）

`HotdogConductor.cpHasPrivacyInfo(ctx, bool)` 组装的键，全部是字符串：

| 键 | 来源 | 隐私开关关闭时的替代值 |
|---|---|---|
| `plat` | `AndroidUtils.getOsName` | — |
| `usid` / `uid` / `initUid` | `GlobalEnv` | `uidDefault` / `initUidDefault` |
| `username` | `GlobalEnv.getUserName` | — |
| `fp` | `GlobalEnv.getFingerPrint`（可能被提升为头） | — |
| `model` / `osversion` | `AndroidUtils` | — |
| `gid` / `sid` / `pid` / `cid` | `GlobalEnv` | — |
| `msg` | `PhoneCallStat.toMsgString()` | — |
| `ma` | `AndroidUtils.getMacAddress` | `maDefault` |
| `adid` | `AndroidUtils.getADID` | `adidDefault` |
| `mno` | `AndroidUtils.getSimOperator` | `mnoDefault` |
| `nt` | `AndroidUtils.getApnName` | — |
| `tsv` | `targetSdkVersion`（游客态为空） | — |
| `resolution` | `WxH` | — |
| `hotfixVersion` | `HotFixInfoManager` | — |
| `instid` | `GlobalEnv.getInstId` | — |
| `q_loc_lang` | `GlobalEnv.getLanguage` | — |
| `nettype` | `AndroidUtils.getNetType`（`unknown` 时不写） | — |
| `brush` | 固定 `{"lt":"0"}` | — |
| `lat` / `lgt` | 仅当拿到最新缓存定位时写入 | 不写 |
| `t` | 本次功能键 `tStr` | — |
| `cust` / `cqp` | 调用方设置 | — |
| `hk` | `isHook()` 位掩码 | — |
| `catom` / `cas` | `getAtomInfo()` 的原子模块名与栈信息集合 | 为空时不写 |
| `ke` | `String.valueOf(ke)`（毫秒时间戳） | — |
| `ref` | 页面名或宿主 Activity/Fragment 类名 | 缺省 `unknown#unknown` |
| `vid` | `getCParamWithPrivacyInfo` 追加 | — |

`isHook()` 的位掩码语义（`HotdogConductor.java`）：

| 位 | 触发条件 |
|---|---|
| `1` | 栈中出现第二次 `com.android.internal.os.ZygoteInit` |
| `+2` | `com.saurik.substrate.MS$2.invoked` |
| `+4` | `de.robv.android.xposed.XposedBridge.main` |
| `+8` | `de.robv.android.xposed.XposedBridge.handleHookedMethod` |

`getAtomInfo()` 从调用栈逆序找到第一个 `com.mqunar` 类，查
`AtomInfoWrap.getAtomInfos()` 得到模块名（`catom`）与调用方集合（`cas`）；
解析失败会写入 `atomerror1_0` 之类的错误标记。

## 6. `b` 参数与加签

`BParamConfigHelper` 从 DataPip 读两个开关集合：

| 键 | 作用 |
|---|---|
| `signature_b_para_config` | 命中 `tStr` 或 `autoSignature=true` 时调 `signatureForBPara()` |
| `append_data_b_para_config` | 命中时调 `appendDataForBPara()`，向 `b` 追加 `c=<k>&v=<version>` 形式的字段 |

`signatureForBPara()`：

```java
String strUti = PubInterfaces.uti(this.bStr);
if (strUti.isEmpty()) strUti = "{}";
JSONObject object2 = JSON.parseObject(strUti);
for (String str : object2.keySet()) {
    if (object.containsKey(str)) { /* 记 duplicated key 错误并放弃 */ }
    object.put(str, object2.get(str));
}
this.bStr = object.toString();
```

即原生函数返回**一个 JSON 对象**，其键被合并进 `b`；键冲突直接放弃本次加签。
`PubInterfaces.uti` 最终落到 `libqlynx_v++` 的 `j.uti`（见 `risk.md` §4）。

## 7. 响应

`NetWorkInterceptor.buildResult` → `Pitcher.buildResult` → `Goblin.drift`，
再由 `Response.parseResponse` 做 `ZLib.decompress`，返回
`{"json", byte[]}` 二元组；`buildResult` 把结果交给 `parseResponse` 得到
`Serializable[]`，未过业务校验时抛 `IllegalStateException("can not parse result")`。

`Response.COMMON_RESPONSE_HEADER_LEN = 9`，另有 `TYPE_PROTOBUF = "proto"` /
`TYPE_STRING = "json"` 两条类型标记；图片等资源通过 `setResource` 从响应里
`ImageLoader.putDataByKey` 落缓存。

## 8. 静态域名面

268 个去重主机串（`classes*.dex` 字面量扫描，含 `qunar`/`qunarzz`/`ctrip`
家族）。按调用频次排名前列的是 `s.qunarzz.com`（静态资源）、`pay.qunar.com`、
`mob.uc.qunar.com`、`touch.qunar.com`、`m.flight.qunar.com`、
`browser.qunar.com`、`qim.qunar.com`（IM）、`jr.qunar.com`（金融）、
`rn.qunar.com`（RN 离线包）、`hotel/qunar` 与 `train` 系列。
测试与内网主机（`*.beta.qunar.com`、`*.qa.nt.ctripcorp.com`、`*.corp.qunar.com`）
只作为存在性证据列出，不用于任何请求。

## 9. 实时通道与推送

| 通道 | 实现 | 可见常量 |
|---|---|---|
| 自建 IM | MQTT（Paho）+ `qim.qunar.com` | `mqttAndroidService.db.MqttMessageTable` |
| 长连存储 | Netty 落盘 | `tfdjfjg9109_nettyhost_false_<hash>.dat`（含 WAL/SHM） |
| 厂商推送 | 小米 MiPush、个推 GTC、华为 HMS | `pushsdk.db`、`gtc3.db`、`msre.db`、`msvolcano.db` |
| 桌面小组件 | `QAppSignWidgetProvider`、`miuiWidget*` | 见 `permissions.md` §2 |

## 10. 离线包 `assets/*.qp`

23 个 `.qp` 与 `hdog` 是同一族容器，但**不加密**：

```
"qp" (0x71 0x70)
u32 little-endian  manifestLength
manifest (UTF-8 JSON)
body (明文 JS)
```

`f_major_bundle_rn_android_2968_10010_20260911213520_prod.qp` 实测：
文件 `10,900,693` 字节，清单 `62,783` 字节、203 个文件，正文 `10,837,904` 字节，
正文以 `__d(function(g,r,i,a,m,e,d){` 开头（Metro 模块包装）。
清单键：`files[], timestamp, hybridid, version, iOS_vid, android_vid,
harmony_vid, pid, extra, rnpackage, platform, dependency_qp, common_qp,
weak_common_qp`；`extra` 为
`{bundleType:"JSC", engineVersion:"0.0.1", platform:"android",
hermesEnabled:"false", ramEnabled:"false", slimEnabled:"true"}`。
`files[]` 每项含 `url`、`md5`、`sha256` 与 `sl:"start,length"`。

`sl` 的语义是“在正文里的起始偏移与长度”，已对全部 23 个包核验。以最大的
`f_major_bundle` 为例：203 项都从偏移 0 起首尾相接、互不重叠，长度之和
`10,837,904` 与正文长度**精确相等**，即分片连续铺满正文。

23 个包分成两类：**16 个**是连续铺满（`sum(sl) == body` 且范围首尾相接），
**7 个**是稀疏分布——范围之间留有大段空洞、`sum(sl)` 远小于正文长度，
但所有范围的末端仍正好等于正文长度。7 个稀疏包里有 6 个的 `extra.ramEnabled`
为 `true`（另一个 `cmn_mine` 为 `false`），说明稀疏形态与 RAM 包/按需加载
相关：这类包里 `files[]` 只列出运行期真正需要下载的片段，其余正文内容
留待增量下发。

`md5`/`sha256` 两个字段虽名为摘要、取值形如 Base64，但**不满足摘要语义**：
Base64 解码后分别是 `43` 与 `86` 字节（不是 16 / 32），且对 `sl` 指出的
对应分片重算 `md5`/`sha256`（含 `sha1`、`sha512` 对照）**全部不匹配**。
字段是 203/203 互不相同的高熵串，因此它们是服务端下发的比对材料，
本次未复原其构造，也不把它当作“分片摘要”使用。

## 11. 静态化说明

所有路径、头、字段都来自反编译字面量与 `new`/`put` 调用点。服务端是否校验某个
字段、`qrt` 的具体放行策略、`b` 的签名是否必需，均无法从客户端静态确定。
