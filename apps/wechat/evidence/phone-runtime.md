# 真机只读运行时快照

## 采集方式与边界

本文件记录 2026-10-02 在一台已 root 的 Android 物理真机上对微信
`8.0.78` 进程所做的**只读**检查。所有读取通过 shell 侧的
`dumpsys package`、`appops get`、`pidof` 和 procfs 进程根视图
（`/proc/<pid>` + `root/<...>`）完成，
没有注入、Hook、ptrace、内存写入、进程附加、权限状态修改，也没有从第三方
UID 发起 ContentProvider 调用——后一类调用会在微信日志中留下调用方记录，
属于可被发现的行为，本轮明确排除。

不公开：真实设备型号/指纹/序列号、真实绝对路径、进程 UID/包内目录、内存
内容、账号、联系人、消息、媒体、位置、支付与小程序缓存原始文件。

## 样本与版本一致性

| 项目 | 结果 |
| --- | --- |
| 系统 | Android 16（API 36），安全补丁级别 `2025-11-01` |
| 内核 | `6.1.118-android14-...-ab13624819`（内核分支名含 `android14`，不等于系统版本） |
| 包名 | `com.tencent.mm` |
| `versionName` / `versionCode` | `8.0.78` / `3180` |
| `minSdk` / `targetSdk` | 24 / 34 |
| 首次安装 / 最后更新 | 2026-08-16 22:37:52 / 2026-09-17 00:38:26 |
| `base.apk` 大小 | 280,614,450 字节 |
| `base.apk` SHA-256 | `41f7dc1f720767fa78fa20dd13ea034b817bbf6ebd23dfd1324c647499c9c1ba` |

真机 `base.apk` 的 SHA-256 与本仓库静态分析所用的 `8.0.78` 样本完全一致，
因此 Manifest、Provider 反编译与数据库证据的样本边界与该真机一致。

> 说明：设备报告的内核命令行字符串含 `android14`，但
> `ro.build.version.release=16`、`ro.build.version.sdk=36`。此前草稿中的
> “Android 14”是按内核字符串推断的误记，本文件以系统属性为准。

### 读取方式

`pm path` 返回的是应用私有目录下的绝对路径，root shell 直接 `stat` 会失败
（无访问该挂载点的能力）；同一文件经 procfs 进程根视图（`/proc/<pid>` →
`root/<path>`）只读打开后可
成功计算摘要。这是同一 inode 的只读读取，没有复制 APK，也没有修改应用状态。

## 小程序运行时存储

微信应用数据下的 `MicroMsg/appbrand/pkg/` 共有 **112 个 `.wxapkg` 文件**
（按普通文件计数）：

| 子目录 | wxapkg 文件数 |
| --- | ---: |
| `general/` | 96 |
| `firstParty/` | 13 |
| `commLib/` | 3 |
| **合计** | **112** |

> 注意：`ls | grep -c wxapkg` 会把同名的 `.wxapkg.zstd` 压缩旁路文件一并计入，
> 早先读数 108 即来自该误计；`find -type f -name '*.wxapkg'` 的 96 为正确值。

### appid 与缓存索引的对应关系

同一次只读采集得到三条指向同一小程序的证据链：

| 位置 | 内容 | 说明 |
| --- | --- | --- |
| `MicroMsg/appbrand/pagesidx/` | `wx8d200a641cdec6a1_274.idx`（4,708 字节） | 索引文件名由 appid + `_` + 版本序号组成 |
| `MicroMsg/appbrand/pkg/general/` | `_63336007_274.wxapkg`（2,840,506 字节） | 包文件名由内容 CRC + `_` + 同一版本序号组成 |
| `cache/appbrand/jscache/` | `app-service.js_wx8d200a641cdec6a1/`、`pagesMenu_app-service.js_wx8d200a641cdec6a1/`、`precompile.wx8d200a641cdec6a1` | 缓存目录名直接带 appid |

`_63336007_274.wxapkg` 的 SHA-256 为
`b4cddbc3ddc207331699522d167afdc88624ab6ac4a674a14c7bd587fbacb3e5`，
与 `evidence/miniprogram-static.md` 记录的汉堡王样本一致。

因此“appid → 版本序号 `274` → `_63336007_274.wxapkg` → `app-service.js_*`
缓存目录”是在真机上可复现的静态对应关系，不需要运行小程序，也不需要抓包。

## 运行时危险权限状态

来自 `dumpsys package com.tencent.mm` 的 User 0 `grantedPermissions`：

| 状态 | 权限 |
| --- | --- |
| 已授予 | `POST_NOTIFICATIONS`、`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`READ_MEDIA_VISUAL_USER_SELECTED`、`NEARBY_WIFI_DEVICES`、`BLUETOOTH_CONNECT`、`BLUETOOTH_ADVERTISE`、`BLUETOOTH_SCAN`、`CAMERA`、`RECORD_AUDIO` |
| 未授予 | `READ_CONTACTS`、`READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_EXTERNAL_STORAGE`、`ACTIVITY_RECOGNITION`、`ACCESS_MEDIA_LOCATION` |

已授予项均带 `USER_SET`（用户手动选择过）；`READ_CONTACTS`、
`ACTIVITY_RECOGNITION`、`ACCESS_MEDIA_LOCATION` 无 `USER_SET`，即用户尚未对该
权限做过选择；`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` 为 false 且带
`USER_FIXED`，同时 `READ_MEDIA_VISUAL_USER_SELECTED=true`，符合 Android 14+
的“仅选择部分照片视频”状态。

**这条证据只说明当前授权状态，不说明这些权限已经被调用过，更不说明数据已上传。**

## AppOps 模式

来自 `appops get` 的 uid 级模式（User 0）：

| 模式 | AppOps |
| --- | --- |
| `foreground`（仅前台可调用） | `COARSE_LOCATION`、`FINE_LOCATION`、`CAMERA`、`RECORD_AUDIO`、`READ_CLIPBOARD`、`WRITE_CLIPBOARD`、`AUDIO_MEDIA_VOLUME` |
| `ignore`（默认拒绝） | `READ_CONTACTS`、`WRITE_CONTACTS`、`READ_CALL_LOG`、`READ_CALENDAR`、`CALL_PHONE`、`READ_SMS`、`READ_PHONE_STATE`、`READ_PHONE_NUMBERS`、`READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、`GET_ACCOUNTS`、`READ_MEDIA_AUDIO`、`READ_MEDIA_VIDEO`、`READ_MEDIA_IMAGES`、`ACCESS_MEDIA_LOCATION`、`ACTIVITY_RECOGNITION`、`BODY_SENSORS` 等 |
| `allow` / `ask` | `USE_FULL_SCREEN_INTENT=allow`、`WRITE_EXTERNAL_STORAGE=ask` |

位置、相机、麦克风、剪贴板被限制在 `foreground`，联系人、短信、通话、媒体
历史读取等默认 `ignore`，与“敏感权限不默认开启、仅在相应功能中按同意范围调用”
的政策表述方向一致。

### 时间戳不能用作采集证据

AppOps 的 `time=` / `rejectTime=` 是**读取时刻相对当前时间的增量**，每次读取
都会变化。本次采集中，`GET_USAGE_STATS` 的 `rejectTime` 在相隔约 15 秒的两次
只读读取之间就从 `+2s` 移动到 `+17s`；位置、相机、Wi-Fi、蓝牙相关条目在本次
会话期间也发生过位移。

因此：

- 这些时间戳**不能**归因为“某次微信行为发生在某个时刻”；
- 本次只读检查本身可能改变 AppOps 计数与时间戳；
- 本文件只采用 uid 级**模式**（`foreground`/`ignore`/`allow`/`ask`），
  不采用任何 `time=` 条目作为微信采集或上传的证据。

## 未覆盖

- 未修改任何权限或 AppOps 状态；
- 未抓取网络流量，未验证任何字段是否上传；
- 未读取账号、会话、媒体、位置、支付或小程序原始缓存内容；
- 未对导出组件发起跨 UID 调用；
- 未读取/导出进程内存，未附加或注入微信进程。
