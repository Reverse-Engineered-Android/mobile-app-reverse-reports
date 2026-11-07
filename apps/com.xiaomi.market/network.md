# 网络与协议

## 主要端点

| 用途 | 方法与路径 | Host / base | 关键字段 |
|---|---|---|---|
| 安全检测 V1 | `GET app/securityDetect` | `https://app.market.xiaomi.com/apm/` | `packageNameList` |
| 安全检测 V2 | `POST app/securityDetectV2` | `https://hyperos.developer.xiaomi.com/xms/` | form 字段 `securityDetectRequestParamList` |
| 更新检查 | `updateinfo` / `updateinfo/diffsize` | `updateinfo.market.xiaomi.com` 或 `app.market.xiaomi.com` | `packageName`、`versionCode`、`oldApkHash`、`packageInstalledByMarket`、`apkSource`、`appSplits` |
| 桌面推荐 | `desktopfolder/recapps` | 商店 API base | 本机相关应用状态 |
| 桌面分类 | `desktopfolder/category` | 商店 API base | 分类请求 |
| 装包校验 | `pi/pinstaller/apkcheck`、`apkcheckresult`、`verify`、`verifyresult`、`judge` | `https://a0.app.xiaomi.com/pi/` | 包名、版本、签名哈希、显示名、installer、installerVersionCode、timestamp、nonce |
| 活跃统计 | `statistics/appactive` | 商店 API base | 每应用 `AppActiveStatRecord` 字段 |
| AI 广告候选 | `featuredV4/getAdCandidates` | 商店 API base | 行为列表与 UsageStats 聚合 |
| OneTrack | `/track/v4`、明文兜底 `/tracks` | `tracking.miui.com` 及国际区域 host | JSON → GZIP → AES/ECB/PKCS5 |

代码常量：

- `Constants.HOST_CN = app.market.xiaomi.com`，`SERVICE_CN = /apm/`；
- `Constants.DEVELOP_HOST_CN = hyperos.developer.xiaomi.com`，`DEVELOP_SERVICE_CN = /xms/`；
- `Constants.HOST_UPDATE_ONLINE = updateinfo.market.xiaomi.com`；
- `CommonCoroutineServiceApi` 280–281 行声明 V1；
- `DevelopCoroutineServiceApi` 31–33 行声明 V2；
- `Constants` 2328–2329 行定义 job id 730/731。

## 安全检测协议

### V1

```http
GET /apm/app/securityDetect
packageNameList=<pkg1>,<pkg2>,...
```

`requestAppSecurityCheckParams()` 在请求前记录 V1 `receive` 事件。

### V2

```http
POST /xms/app/securityDetectV2
Content-Type: application/x-www-form-urlencoded

securityDetectRequestParamList=[
  {"packageName":"...","versionCode":"...","signatureMD5":"..."}
]
```

`signatureMD5` 来自 `LocalAppInfo.getSignatureMD5V2()`，即签名证书 MD5。请求仍带 `Parameter.getBaseParameters()` 的通用参数。

### 响应

设备可见响应保存到 `files/app_security_data`，主要字段：

| 字段 | 含义 |
|---|---|
| `judgingRules` / `judgingRulesV2` | 客户端评分阈值 |
| `riskAppList[]` | 风险包名、版本、签名 MD5、类型、等级 |
| `appSecurityText` | 标题、气泡、忽略提示文案 |
| `unknownAppClearRules` | 可执行动作，如 `uninstall`、`openPermissions` |
| `checkDuration` | 下次检查间隔（小时），客户端换算为毫秒 |

## OneTrack

OneTrack SDK 的请求结构：

- 路径 `/track/v4`，另有明文常量 `https://tracking.miui.com/tracks`；
- 请求头包含 `OT_SID`、`OT_ts`、`OT_net`、`OT_sender`、`OT_protocol: 3.0`；
- payload：JSON 压缩 GZIP，再 AES/ECB/PKCS5 加密；
- 密钥通过 `/track/key_get` RSA 换取；代码中还有内置混淆 key 兜底。

安全检测相关 OneTrack 事件：

| 事件 | 触发点 | 内容 |
|---|---|---|
| `app_security_check` / `receive` | 每次 V1/V2 请求 | `is_auto_check`、`check_version`、实验开关 |
| `risk_notification_model_feature` / `receive` | 自动扫描后生成特征 | 风险数量级、保留应用数量级、占比等级、风险应用 3/1 天使用时长 |
| `active` | 每应用活跃统计 | `AppActiveStatRecord` 字段 |
| `uninstall` | 卸载上报 | 普通卸载事件 |

## 本地业务连接

`Connection` / `ConnectionBuilder` 承担商店业务请求；部分敏感上传走 `ConnectionRSA`。通用参数由 `Parameter.getBaseParameters()` 注入，服务端返回后 `parseAppSecurityData()` 把安全数据写入文件与 MMKV。
