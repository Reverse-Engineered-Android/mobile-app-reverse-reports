# 隐私与告知

## 1. 同意闸门

`HotdogConductor.cpHasPrivacyInfo(Context, Boolean)` 是**唯一**的隐私降级开关，
由 `getCParamWithPrivacyInfo(ctx, z)` 透传：

| 调用方 | 传参 | 效果 |
|---|---|---|
| `cp(ctx)` | `Boolean.TRUE` | 全部字段真实采集 |
| `getCParamWithPrivacyInfo(ctx, true)`（`prepareParams` 实际使用） | `true` | 全部字段真实采集 |
| `getCParamWithPrivacyInfo(ctx, z2)`（`z2` 为 false 时） | `false` | `ma/adid/mno/uid/initUid` 替换为 `*Default` |

降级覆盖范围（读源码得到）：

```java
if (bool.booleanValue()) {
    map.put("ma", ...getMacAddress(context));
    map.put("adid", ...getADID(context));
    map.put("mno", ...getSimOperator(context));
    map.put("uid", ...getUid());
    map.put("initUid", ...getInitUid());
} else {
    map.put("ma", "maDefault");  map.put("adid", "adidDefault");
    map.put("mno", "mnoDefault"); map.put("uid", "uidDefault");
    map.put("initUid", "initUidDefault");
}
```

**注意三点**：

1. 降级只覆盖 5 个字段；`gid`、`sid`、`pid`、`cid`、`usid`、`fp`、
   `instid`、`lat`/`lgt` **不在降级范围内**，即即使隐私开关为 false，
   设备与会话标识仍然真实上报。
2. `prepareParams()` 硬编码传入 `true`，未从任何用户同意状态读取。
   因此这条路径上的降级开关**在正常业务流程里不会触发**。
3. `getCParamWithPrivacyInfo` 还会追加 `vid`（版本维度标识）。

`GlobalEnv.isTouristMode()` 为真时会再走 `TouristCparam.parse(...)` 调整字段，
说明存在“游客态”分支，但该分支同样在 `cpHasPrivacyInfo` 之后执行。

## 2. 隐私政策与告知链

- APK 内**不含**隐私政策正文（无 `assets/privacy*` 一类长文本）；
  政策由 H5 页面下发，客户端无法静态证明逐项告知。
- `shared_prefs/Map_Privacy.xml`、`prv_config.xml`、
  `system_sensitive_api.xml`、`sec_gd_config_mshield.xml` 是本地隐私/敏感 API
  配置落点（键名层面，只读核对观察到）。`Map_Privacy` 属百度地图 SDK 的
  隐私合规开关，不是去哪儿自有的全局闸门。
- 系统能力的使用与 `targetSdkVersion=36` 一致：`FOREGROUND_SERVICE_*`
  细分类型、`POST_NOTIFICATIONS`、`READ_MEDIA_*` 都是新版权限模型。

## 3. 采集项与触发条件

| 采集项 | 触发条件 | 落点 |
|---|---|---|
| 设备型号/OS 版本/分辨率 | 每次 Hotdog 请求 | cparam `model`/`osversion`/`resolution` |
| APN / 网络类型 | 同上 | `nt`/`nettype` |
| MAC / ADID / SIM 运营商 | 同上（隐私开关 true） | `ma`/`adid`/`mno` |
| 安装 ID / 热修版本 | 同上 | `instid`/`hotfixVersion` |
| 位置 | **仅当**拿到最新缓存定位 | `lat`/`lgt` |
| 页面/Activity 归属 | 每次请求 | `ref` |
| 调用方原子模块 | 每次请求 | `catom`/`cas` |
| Hook 位掩码 | 每次请求 | `hk` |
| 已安装应用列表 | 独立 SDK 采集时 | `ext.ai()` → `NUtils.cd()`；`AppListInfoCollector` → `upDevice.do` |
| 外部存储目录列表 | 独立 SDK 采集时 | `Cmd.a()` |
| 出口 IP / DNS / 证书链 | 独立 SDK 采集时 | `d.getcc()` |
| keystore attestation 链 | 独立 SDK 采集时 | `Checker.a()` |
| 传感器数值 + 时间戳 | `Sensor.SensorUtils` 监听 | 字段值层面 |
| 8 项运行时授权状态 | `upDevice.do` 上报时 | `PermissionInfoCollector` |

## 4. 设备指纹与风控数据

| 标识 | 派生方式 | 稳定期 |
|---|---|---|
| `fp` | `NUtils.ep()`（原生），服务端可下发 `df` 覆盖（`sfp`），落 `qrc_sp` | 长期 |
| `gid` | `GlobalEnv` | 长期 |
| `instid` | `GlobalEnv.getInstId()` | 安装期 |
| `id5` | `f(BRAND|MODEL|androidId|StorageUtil.a()|WxH)` | 设备期 |
| `instUUID` | `UUID.randomUUID()`，落 `risk_install_uuid` | 安装期 |
| `oaid` | 厂商 OAID 服务（6 个 provider 在 `<queries>` 中声明） | 可重置 |
| `vid` | 版本维度 | 版本期 |
| 匿名 UUID | `FpService.f43332b = UUID.a()` | 进程期 |

`Checker.a()` 的 keystore attestation 链是**硬件级**证明：链由设备 TEE 签发，
服务端可验证“这个密钥确实生成于这台设备的可信执行环境”。这比软件指纹更难伪造，
也意味着该指纹的稳定性高于 `oaid`（后者可被用户重置）。

## 5. 数据范围分层

| 层 | 内容 | 是否离开设备 |
|---|---|---|
| 仅本地 | `shared_prefs` 配置、MMKV 业务 KV、13 个 SQLite 的城市/节假日库 | 否 |
| 本地 + 上报 | cparam 字段、设备指纹、应用列表、证书链、授权状态 | **是** |
| 仅上报 | `hk`、`catom`、`cas`、`ke`、`ref`、`brush` | 是 |
| 不采集 | 联系人、短信、通话记录、剪贴板（本次静态检索范围内） | — |

## 6. 未经告知 / 超范围结论

| 判断 | 结论 | 依据 |
|---|---|---|
| 是否逐项告知应用列表采集 | **无法证明** | 隐私政策不在 APK 内；`<queries>` 声明 183 包 + 两处应用列表上报通道同时存在 |
| 隐私开关是否真正生效 | **可疑** | `prepareParams()` 硬编码 `true`；即便为 false 也只覆盖 5 个字段 |
| 位置采集是否超范围 | **条件性**：仅缓存定位，不是连续定位 | `QLocUtils.onlyForPltGetNewestCacheLocation()` |
| 是否在后台采集 | 未在本次范围内确认 | 后台服务（`NetService`、`XMPushService`）存在，需逐个核对采集时机 |
| 出口 IP / 证书链采集是否告知 | **无法证明** | `d.getcc()` 主动连 `www.qunar.com:443`，无用户交互 |

**综合判断**：本应用的隐私设计是“**声明式合规**”——权限、`<queries>`、
`shared_prefs` 隐私配置都存在，但代码路径上的降级开关在正常业务流程中
被硬编码为“全量采集”。这不构成违规的确证，但在静态证据层面构成
“**告知粒度不足 + 降级机制失效**”的组合风险，需服务端侧政策配合才能判断合规性。

## 7. 静态化说明

本页结论全部来自反编译代码结构与清单声明。是否有真实的超范围采集，
需要运行期流量与服务端策略共同验证；报告不提供任何规避隐私弹窗的方法。
