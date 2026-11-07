# 权限与导出面

## 声明统计

| 项目 | 数量 |
|---|---|
| `uses-permission` | 112 |
| 去重权限 | 108 |
| `<queries>` package | 22 |
| `<queries>` intent | 11 |
| `<queries>` provider | 2 |
| activity | 175，导出 23 |
| activity-alias | 7，导出 7 |
| service | 78，导出 26 |
| receiver | 31，导出 24 |
| provider | 15，导出 9 |

## 与应用列表/检测直接相关的权限

| 权限 | 作用 |
|---|---|
| `QUERY_ALL_PACKAGES` | 查询全部安装包 |
| `com.android.permission.GET_INSTALLED_APPS` | 小米/Android 扩展的已安装应用查询 |
| `PACKAGE_USAGE_STATS` | 读取应用使用时长、启动次数 |
| `REAL_GET_TASKS` / `GET_TASKS` | 任务栈/前台应用相关 |
| `READ_FRAME_BUFFER` | 屏幕帧缓冲 |
| `INJECT_EVENTS` | 注入输入事件 |
| `MANAGE_ACTIVITY_STACKS` | 管理活动栈 |
| `INTERACT_ACROSS_USERS` / `INTERACT_ACROSS_USERS_FULL` | 跨用户操作 |
| `INSTALL_PACKAGES` / `DELETE_PACKAGES` | 系统级安装/删除 |
| `REQUEST_INSTALL_PACKAGES` | 普通安装来源授权 |
| `MANAGE_EXTERNAL_STORAGE` | 广域外部存储 |
| `com.miui.security.permission.managerRiskApp` | 与风险应用管理相关的供应商权限 |

## 其他敏感权限

包括但不限于：

- 网络/账户：`INTERNET`、`ACCESS_NETWORK_STATE`、`CHANGE_NETWORK_STATE`、`GET_ACCOUNTS`、`GET_ACCOUNTS_PRIVILEGED`、`AUTHENTICATE_ACCOUNTS`、`MANAGE_ACCOUNTS`；
- 存储/媒体：`READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、`READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`WRITE_MEDIA_STORAGE`；
- 设备控制：`REBOOT`、`WRITE_SECURE_SETTINGS`、`WRITE_SETTINGS`、`STATUS_BAR`、`EXPAND_STATUS_BAR`；
- 传感/生物：`CAMERA`、`RECORD_AUDIO`、`MANAGE_BIOMETRIC`、`USE_BIOMETRIC_INTERNAL`、`USE_FINGERPRINT`；
- 日历：`WRITE_CALENDAR`；
- 系统集成：`CLEAR_APP_USER_DATA`、`DELETE_CACHE_FILES`、`MOUNT_UNMOUNT_FILESYSTEMS`、`READ_RUNTIME_PROFILES`、`INTERNAL_SYSTEM_WINDOW`、`STOP_APP_SWITCHES`。

## `<queries>` 中的检测相关包

Manifest 显式查询：

```text
de.robv.android.xposed.installer
com.saurik.substrate
```

这说明客户端会显式探测常见 hook 框架安装状态。其余显式 package 包括微信/QQ/Facebook/微博/支付宝/小米支付等生态应用，以及 `com.miui.systemAdSolution`、`com.miui.hybrid`、小米账号、健康、短信等。

## 导出面

导出组件集中在下载、安装、更新、通知、账号回调、推送、桌面推荐、验证回调等能力。完整逐组件审计不在本报告范围；与本任务相关的组件包括：

- `AutoScannerJobScheduler` / `AutoScannerJobService`（安全扫描 job）；
- 风险通知 receiver 与通知调度 job（id 731）；
- 装包校验和安装链路服务。

导出组件并非全部可被任意应用调用；部分由自定义 permission 或系统签名保护。静态清单只给出导出事实，不推断可利用性。
