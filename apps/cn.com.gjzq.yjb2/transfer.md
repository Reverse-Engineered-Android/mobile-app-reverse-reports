# 上传下载范围

## 1. 下载

| 类型 | 范围 | 触发条件 |
|---|---|---|
| APK | `appdl.yongjinbao.com.cn/yjb/android/{yjbkh.apk,yongjinbao.apk}` | 用户点下载 |
| APK 落地页 | `linkm.yongjinbao.com.cn/download/app-download.html?useDownload=3` | 未装 App / 版本过低 |
| 应用商店 | `a.app.qq.com/o/simple.jsp?pkgname=cn.com.gjzq.yjb2` | 应用宝跳转 |
| H5 载荷 | `files/yjb/**.r`（运行时下载，设备端实测 `210 MiB` / `3,382` 个文件） | App 启动/热更 |
| 模块清单 | `files/h5info.txt.r` | 比对模块版本 |
| 图片 | `xbl-yjb-pic.oss-cn-shanghai.aliyuncs.com` | 页面渲染 |
| 静态资源 | `growingcdn.yongjinbao.com.cn`、`imgcache.qq.com` | 页面渲染 |

`BaseIndexNew.js:30-45` 在 `userAgent` 不匹配 `yjb3.0` 或版本 < 5.1.1 时，
跳转 `app_update_link` 或 `/me/view/download.html`。鸿蒙（`openharmony`）
分支默认支持 `userId`，跳过版本闸门。

## 2. 上传

客户端 H5 侧**未发现主动上传用户数据**的代码路径。唯一的上报通道是：

| 通道 | 说明 |
|---|---|
| `LOGGER_COLLECT` | 日志上报方法码 |
| `thinking.crash` 进程 | 崩溃采集（独立进程） |

未发现：文件上传表单、头像/证件上传、位置上报、通讯录上传、相册批量上传。
H5 中的 `<input type="file">` 走 `chooseImg`（JSBridge 功能号 `3004`），
由 native 打开系统相册选择器，选中结果以回调返回——**选择动作在 native 侧，
H5 不持有文件句柄**。

## 3. 文件桥

`ycomment/common/vendors/NativeAPI.js` 暴露文件操作功能号：

| 功能号 | 名称 | 说明 |
|---|---|---|
| `2004` | `saveFile` | native → 内存 |
| `2005` | `readFile` | 内存 → native |
| `2006` | `deleteFile` | 删除内存缓存 |
| `2001` | `readSystemToMemory` | 系统文件读入内存 |
| `2002` | `readDataFromMemory` | 内存读取 |
| `2003` | `deleteDataFromMemory` | 内存删除 |

这些是**内存级**文件桥（WebView ↔ native），不是本地文件系统写入。
`2001` 的命名暗示可从系统路径读取，但实际实现受 native 侧校验约束，本报告
未反编译该实现，因此**不判定为任意文件读取**。

## 4. WebView 文件提供

`<application android:grantUriPermissions="true">` + `requestLegacyExternalStorage="true"`
配合 `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` 权限，
允许 WebView 内容通过 `content://` 提供文件。导出组件中未发现直接暴露
`FileProvider` 的 activity；`grantUriPermissions` 更可能服务于推送落地页与
支付回调。

## 5. 落盘格式

| 路径 | 大小 | 格式 |
|---|---|---|
| `files/yjb/**.r` | 210 MiB / 3,382 文件 | 固定 keystream XOR |
| `files/h5info.txt.r` | — | 同上，解密后为模块版本 JSON |
| `files/i12196010e51071ed` | 69,114,092 B | 加固器数据体，无已知魔数 |
| `files/app_payload.dex` | 13,680 B | 运行时下发 DEX，`dex\n035` |
| `files/app_payload.version` | — | 版本戳 `gjzq-objdump-20261029-11` |

`files/uuid` 存放设备唯一标识（发布版已脱敏），属加固器/埋点 SDK 所有。

## 6. 边界

未上传文件、未触发实名、未下载业务文件、未抓真实流量。以上为静态结论。
