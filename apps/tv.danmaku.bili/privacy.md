# 权限与隐私

## 1. Manifest 面

`manifest-summary.tsv`：包名 `tv.danmaku.bili`，versionName `9.13.0`，
versionCode `9130500`。Manifest 声明 **74 个权限**、**87 个 exported components**。
重点权限：

- `QUERY_ALL_PACKAGES`、`READ_LOGS`；
- `READ_CALENDAR`、`WRITE_CALENDAR`；
- `ACCESS_FINE_LOCATION`、`ACCESS_BACKGROUND_LOCATION`（以 manifest 原文为准）；
- `SYSTEM_ALERT_WINDOW`、`REQUEST_INSTALL_PACKAGES`；
- `READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、
  `MANAGE_EXTERNAL_STORAGE`/全部外部存储能力；
- 广告服务的 AD_ID/custom-audience 能力；
- USB、通知、网络和常用传感器能力。

导出组件包括 `com.bilibili.lib.accessbridge.BiliAccessContentProvider`、
`IntentHandlerActivity`、`SSOActivity`、`BiliUSBAccessoryHandler`、
`AuthLoginActivity` 等；`<queries>` 枚举 40+ 第三方包（微信、QQ、地图、MSA 等）。

## 2. 采集与落盘范围

静态代码证明的采集类别：

1. 设备稳定标识与硬件指纹：buvid、OAID/AAID/VAID、广告 ID、MAC/BSSID/SSID、
   IMEI/IMSI/ICCID（按版本/权限条件）；
2. 环境与安全状态：root、Hook、模拟器、多开、ADB、VPN/代理、bootloader、ROM、
   USB、传感器、屏幕、内存、已安装应用数量；
3. 行为与内容：搜索建议词、播放历史、下载任务、网络页面统计、互动消息；
4. 投稿与会员购：用户主动提交的视频、地址、购票人、订单和图片；
5. 远端决策：`dd.json` 的 mid/av/buvid/brand/model/ip_region 等属性参与规则匹配。

设备端 SQLite 为普通未加密表，schema 见 [database-inventory.md](database-inventory.md)。

## 3. 越权、提权、未经告知、超范围结论

### 3.1 Android 提权

**未发现**。root/adb/解锁检测只是读取属性、进程和文件；没有系统分区写入、
UID 提升、SELinux 绕过或系统服务注入代码。

### 3.2 客户端越权

**未发现已证实的客户端越权**。登录请求使用 `access_key`、签名和服务端返回的
授权字段；会员购订单/购票人、投稿上传和 DRM credential 均由服务端决定。
APK 中的第三方 App ID/Secret 是凭据暴露面，不足以证明第三方越权成功。

### 3.3 未经告知

**静态样本不能单独证明未经告知**。客户端存在风险采集、安装应用枚举、传感器读取、
广告 ID 和远端决策能力，但实际上传条件、隐私文本版本、运行时同意状态和远端开关
需要同版本隐私政策与运行时网络观测才能闭合。报告不把“存在代码”写成“已无告知采集”。

### 3.4 超范围

明确的静态风险是：

- 74 权限和 87 导出组件明显超出单一播放功能的最小权限；
- 风控字段覆盖标识、位置、应用列表、传感器和环境，服务端用途与保留期不可见；
- 本地保存投稿认证串、上传分片、下载 auth code、搜索词和行为事件；
- `QUERY_ALL_PACKAGES` 与 `READ_LOGS` 在现代 Android 上属于高敏感能力。

未发现通讯录、短信、通话记录全量读取或相册全量自动上传的调用链。结论是
“权限/能力范围偏宽，存在潜在超范围面；实际数据是否越界未由静态样本证实”。
