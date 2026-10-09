# 权限、导出面与越权提权

## 1. payload 权限面

四个 DroidGuard payload APK 的 manifest 均为：

```xml
package="com.google.ccc.abuse.droidguard"
minSdkVersion="24"
targetSdkVersion="14"
```

manifest 只声明一个默认禁用、未导出的
`com.google.android.build.data.PropertiesServiceHolder`，没有
`uses-permission`。因此 payload 自身没有额外危险权限授予面。

## 2. GMS 宿主权限

GMS base APK 是宿主，manifest 声明 `INTERNET`、`GET_ACCOUNTS`、
`QUERY_ALL_PACKAGES`、系统广播与大量系统能力。它们服务整个 Google Play
services，不能整体归因给 DroidGuard。

DroidGuard 相关 manifest 组件：

| 组件 | exported | 说明 |
|---|---|---|
| `DroidGuardService` | true | `INIT/PING/START` intent-filter |
| `DroidGuardPersistentService` | true，默认 disabled | 同类 intent-filter |
| `DroidGuardGmsService` | true，默认 disabled | 同类 intent-filter |
| `DroidGuard.ui.GenericActivity` | false | 默认 disabled |

## 3. 导出服务的调用方检查

`DroidGuardChimeraService.onBind` 只对 `START` action 返回
`dlcv` 包装的 Binder。`dlcv.e`：

- 读取调用 UID 与 calling package name；
- 调用 GMS 包签名/身份校验 `bqvu.a.a(context).j(uid, packageName)`；
- 校验失败抛出 `SecurityException("Unknown calling package name ...")`；
- 只有配置允许的自调用例外。

返回的 `bxgx` 还通过 Binder interface token、调用 UID 和结果回写 token
约束后续调用。故导出声明是可观察攻击面，但静态代码存在调用方身份校验，
不能仅凭 `exported="true"` 判定可被任意应用滥用。

## 4. 越权判断

**未发现 DroidGuard 自身越权。**

依据：

1. payload manifest 无危险权限；
2. Build、`os.arch`、GPU、传感器、触摸均通过 GMS 已有上下文或公开 API 获取；
3. 未发现绕过 Android 权限检查、伪造 UID、复用其他应用 Binder token；
4. 未发现读取其他应用私有目录；
5. VM 下载被固定 HTTPS 前缀和验签限制。

## 5. 提权判断

**未发现提权链。**

未发现以下调用闭环：

- `su`、root shell、SELinux 修改；
- 注入系统进程或写入系统分区；
- `pm grant`/`RENOUNCE_PERMISSIONS` 的 DroidGuard 专用滥用；
- 静默安装、更新或执行任意 APK；
- 修改其他应用 UID、权限或签名状态；
- 绕过 Binder caller UID 校验。

`droidguard/ui.GenericActivity` 默认禁用且未导出，不是 UI 提权入口。

## 6. 边界

结论限于固定 GMS 26.37.37 与四个 payload APK。Android framework、系统签名
权限和服务端策略属于平台边界；未测试真实攻击，也未尝试连接导出组件。
