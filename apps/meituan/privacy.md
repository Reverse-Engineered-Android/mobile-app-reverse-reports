# 隐私、告知与超范围判断

## 1. 隐私状态

核心状态是 `IPermissionGuard.isPrivacyMode(context)`，持久化在
`privacy_config` 的 `is_privacy_mode`。首启隐私对话框 `ShowPrivacyDialogHandler`
在用户同意时执行：

```java
json.put("isAgree", true);
permissionGuard.setPrivacyMode(activity, false);
Dsp.getSharedPreference().edit().putBoolean("state", true).commit();
startActivity(MainActivity.class);
```

拒绝只回调 `isAgree=false`，不切换隐私态。`PrivacyProvider` 也提供
`setPrivacyMode(context,false)` 的入口。这里 `false` 是“退出受限隐私态”的
布尔值，不表示关闭隐私保护。

## 2. 同意前后的采集边界

| 采集面 | 隐私态证据 | 静态判断 |
|---|---|---|
| MTGuard | `internalInit(..., isPrivacyMode ? 1 : 2)` | 受限/正常初始化不同 |
| MainBridge 权限 | `isPrivacyMode -> return 5` | 权限查询被解释为隐私态 |
| DFP/OneID | `OneIdPrivacyHelper.isPrivacyMode`、DFP 上报分支 | 可阻止上报 |
| 传感器代理 | `v0` 多处先判 `isPrivacyMode` | 未同意时直接返回 |
| Push | privacy mode 检查 | 受限 |
| Launcher/statistics | privacy mode 分支 | 受限 |
| 地图 | `MapsInitializer.agreePrivacy` | 需要同意 |

因此核心采集存在客户端告知闸门；但不能证明每个第三方 SDK、Web 页面和导出
组件都经过同一个闸门。

## 3. 实际可见的采集字段

### 3.1 设备与环境

- 系统属性、Build、ABI、CPU/GPU、屏幕、存储、电量；
- 首次启动/安装时间、进程、包安装列表；
- OAID 和厂商标识 helper；
- DFPID、XID、DPID、UUID；
- 代理、VPN、Root、Hook、模拟器、沙箱、调试；
- Wi-Fi、蜂窝 IP、基站、位置、传感器、音量、时间；
- 摄像头/无障碍/UIAutomator 状态。

### 3.2 行为

- 触摸、键盘、手势时间数组 `aT/kT/tT/gT`；
- 起始/当前时间；
- Yoda 行为签名 `sign`；
- 行为摘要 `bI/brR`。

### 3.3 业务内容

- 用户主动选择的图片/文件；
- 头像、银行卡图片、Soter 指纹；
- 搜索词、位置查询、POI/评论页面数据；
- 订单、支付、聊天和评论接口返回的 JSON。

## 4. 采集条件与用途

| 数据 | 触发条件 | 可见用途 |
|---|---|---|
| 位置 | 用户查询附近/地图功能 + 授权 | POI、路线、周边餐厅 |
| 联系人 | 用户选择联系人或分享 | 分享/地址簿功能 |
| 日历 | 用户创建订单/提醒 | 写入日历事件 |
| 相机 | 拍照/扫码/门脸 | 图片上传 |
| 录音 | 语音输入/客服 | 音频识别/客服 |
| 文件/媒体 | 用户选择或页面下载 | 上传/展示 |
| 设备画像 | 登录、支付、风控 challenge | 账号/欺诈检测 |
| 行为 token | Yoda challenge、登录风控 | 行为验证 |

没有静态证据表明这些数据在所有启动阶段无条件上传。相反，Privacy guard 和
权限返回码提供了限制路径。

## 5. 未经告知或超范围的结论

### 5.1 设备端落盘实测（只读）

只读 SSH 检查运行中应用的实际数据库（完整 schema 见
[evidence.md](evidence.md) §8）：

| 落盘面 | 实测格式 | 范围 |
|---|---|---|
| `com.sankuai.meituanMTLocationDb.db` / `MTLocationTableV2` | 34 行；`LOC` 是 Base64 外观不透明串（2764 字节，解码 2073），`TIME` epoch 毫秒，`GEOHASH` 明文；`WIFI`/`CELL` 可空 | 定位历史 |
| `mt-statistics-db-cache.event.evs` | 明文 JSON | `dpid`、`uuid`、`oaid`、`android_id`、`mac`、`bssid`、`union_id`、`micro_msid`、`app_session`、`locate_city_id`、`cityid`、`district_id`、`pushid`、`msid`、`mk_trackid`、`ad_tracking_enabled`、`ch`、`logintype`、`svs` |
| `kitefly.db.log` | 6 行，`token` / `env` 明文 | `babelUserId`、`babelid`、`deviceType`、`mccmnc`、`networkType`、`sdkVersion`、`buildVersion` |
| `request_monitor.db` / `hades_db_sql` | 0 行 | 本次未积累 |
| `privacy_config/kv` + `assets/*.conf` | 二进制 KV 与同意记录 | `is_privacy_mode`、`current_config`、`Android-mtguard`、`Locate.once`、`Phone.read`、`BlueTooth.admin`、`Microphone`、`Pasteboard`、`locate_token` |

`event.evs` 的样例事件里同时出现明文经纬度（报告中保留为 `22.22 / 113.55`，
仅说明存在坐标字段）与稳定设备标识，说明统计事件在本地以明文 JSON 保存位置与设备指纹；这属于
**本地落盘**事实，不等同于已证明该事件被上报。`kitefly.db` 与
`mt-statistics-db-cache` 同时是 `upload`/遥测链路的候选队列，报告据此区分
“已本地记录”与“已确认上报”。

### 可以确认

- 首次同意前存在 `isPrivacyMode` 受限态；
- 传感器、DFP、OneID、push、launcher/statistics 有隐私态检查；
- 147 项权限和 265 个显式导出组件形成较大的能力面；
- DFP/Yoda 能采集稳定的设备与行为信号，远超普通订单功能的最小需要；
- 代码具备读取日历、联系人、相机、录音和外部存储的能力。

### 5.2 不能从静态分析确认


- 服务端是否超范围保存、共享或画像；
- 每次启动是否真的调用全部采集函数；
- 第三方 SDK 是否绕过同一 consent gate；
- 用户在具体 UI 中是否看到每一字段的用途说明。

因此报告使用“具备能力/存在条件性上传/静态未证实”三层表述，不把权限声明
直接写成实际泄露。定位库的 `LOC` 实测为不透明串而非明文经纬度，这一点明确
写入最终结论，避免把 Base64/opaque 存储误判为明文位置留存。

## 6. 最终隐私判断

**存在未经充分静态证明的超范围可能性**，主要是 DFP/Yoda 设备画像、行为
时间序列、后台定位和大量导出组件；**未发现客户端静态代码在用户拒绝首启
隐私对话框后仍然无条件上传核心画像的闭环**。若要判定实际违法或服务端越权，
需要另行取得运行时网络包、隐私政策版本和服务端数据流，本报告不作该推断。
