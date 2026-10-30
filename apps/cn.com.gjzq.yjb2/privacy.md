# 隐私与设备标识

## 1. 调查边界

以下结论只来自 APK 清单、H5/native 调用点和设备只读快照。未登录账号、未上传
数据、未打开相机/麦克风、未读取通讯录、位置或相册。隐私政策正文在服务端，客户端
静态分析不能证明实际告知、保存期限或第三方共享情况。

## 2. `<queries>` 包可见性探测

清单通过 `<queries>` 声明 14 个包名和 3 个 intent：

| 类别 | 包名 |
|---|---|
| 社交/支付 | `com.tencent.mm` |
| Root/管理 | `com.topjohnwu.magisk`、`com.topjohnwu.magiskmanager` |
| Hook/终端 | `com.android.frida`、`com.termux` |
| 安全工具 | `com.android.creator`、`com.zhuoyi.security.lite`、`com.freeme.secureguard`、`com.android.secureguard` |
| OAID/设备标识 | `com.mdid.msa`、`com.samsung.android.deviceidservice`、`com.coolpad.deviceidsupport`、`com.heytap.openid`、`com.huawei.works` |

这是 Android 11+ 包可见性声明，能证明客户端准备查询这些包，不能证明每次启动
都上报结果或据此处罚用户。具体判定分支未从高熵 native 闭合。

## 3. OAID 链

`lib/arm64-v8a/libmsaoaidsec.so`、`libmsaoaidauth.so` 与上述 MSA/OAID 包名
共同构成移动安全联盟 OAID 获取链。该链的目标是提供跨应用设备标识；本报告未
还原 native 采集调用顺序，也未观察真实 OAID 值。

## 4. 设备状态与媒体权限

| 能力 | 权限/入口 | 边界 |
|---|---|---|
| 网络状态 | `ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE` | 常规连接判断，未见位置结论 |
| 电话状态 | `READ_PHONE_STATE` | 存在能力声明；未确认字段上传 |
| 相机/麦克风/视频 | `CAMERA`、`RECORD_AUDIO`、`RECORD_VIDEO` | 由业务功能触发，静态未证明后台采集 |
| 图片/视频 | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO` | H5 `chooseImg` 由 native 系统选择器承接 |
| 悬浮窗 | `SYSTEM_ALERT_WINDOW` | 需用户显式授权，未证明用于后台监控 |

## 5. 浏览与本地缓存

设备只读快照存在 `browsing_history.xml`、`Token.xml`、`account_info.xml`、
`pref_user_info.xml` 等整体密文 prefs；`signalData.db` 有本地信号策略缓存。
这些字段说明“客户端具备缓存空间”，不等同于“已上报服务端”。缓存格式和密文
边界见 [storage.md](storage.md)。

## 6. AI 客服/投顾页面

`ai-customer-service`（238 个条目）与 `ai-adviser`（154 个条目）是 H5 业务模块。
可证明存在对话、行情建议或客服页面及其 RPC 调用面；未登录、未实际提交问题，
不推断输入内容、服务端保存期限或人工复核流程。

## 7. 未闭环项

| 项 | 状态 |
|---|---|
| OAID 实际值及上报频率 | 未知（未运行、未抓包） |
| `READ_PHONE_STATE` 字段是否上传 | 未知 |
| 风险/反分析结果上报字段 | 未知 |
| 隐私政策逐项告知一致性 | 未知（政策在服务端） |
| WebView Cookie 与 native 会话生命周期 | 未知 |

客户端事实只能证明能力和数据落地位置，不能替代服务端合规审计。
