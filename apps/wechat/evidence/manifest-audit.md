# Manifest 与导出组件证据

## 来源

- 样本：微信 Android `8.0.78` / `versionCode=3180`
- 文件：`base.apk`
- 方法：JADX 资源反编译读取 `AndroidManifest.xml`，再按组件类型、
  `android:exported`、`android:permission`、`grantUriPermissions` 和
  `uses-permission` 聚合。
- 仅发布聚合计数和公开组件类名，不发布 APK、DEX、完整签名、调用方包名或
  真实 URI。

## 聚合结果

```json
{
  "versionName": "8.0.78",
  "versionCode": "3180",
  "usesPermissionCount": 100,
  "sensitiveRequestedPermissions": 38,
  "permissionDeclarationCount": 18,
  "exportedComponentCount": 59,
  "exportedWithoutComponentPermission": 44,
  "exportedByType": {
    "activity": 29,
    "provider": 15,
    "receiver": 6,
    "service": 9
  },
  "unprotectedByType": {
    "activity": 29,
    "provider": 6,
    "receiver": 3,
    "service": 6
  }
}
```

自定义权限保护级别聚合：

```json
{
  "signature": 13,
  "signatureOrSystem": 3,
  "normal": 2
}
```

## 应用级安全属性

```text
allowBackup=false
usesCleartextTraffic=true
requestLegacyExternalStorage=true
networkSecurityConfig=@xml/<obfuscated-name>
```

解码后的 network security config 基础节点为
`cleartextTrafficPermitted="true"`。该属性证明应用允许明文网络能力，但不
证明某个具体凭据实际通过 HTTP 传输。

## 重点 Provider 校验链

### ShareableChatRecordsProvider

静态反编译显示 `openFile()` 的顺序为：

1. 检查远端 kill switch；
2. `Binder.getCallingUid()` 取得调用 UID；
3. `PackageManager.getPackagesForUid()` 要求 UID 只映射一个包；
4. 从受信包映射取得预期签名证书 SHA-256；
5. 读取调用包签名并比较摘要；
6. 校验 authority、sessionId、action 和路径；
7. 文件不存在或校验失败时返回空。

同一类的 `query()`、`insert()`、`update()`、`delete()` 返回空或 0。
因此，Manifest 的 `normal` 读取权限不是该文件读取路径的唯一控制；现有
静态证据至少还包含调用方、签名和路径校验。

### WXCommProvider

`query()` 在 URI matcher 解析后调用内部方法取得：

- `getCallingPackage()`；
- `Binder.getCallingUid()`；
- `PackageManager.getPackagesForUid()`。

分支代码在多个 open API/分享路径中读取调用包名和签名，并通过受控事件或
command id 分发。没有发现“任意调用方读取任意聊天/支付数据”的无条件分支。

该 Provider 无 Manifest 组件权限，因此仍是重点动态测试面；本证据不宣称
全部分支已完成安全闭环。

## 结论边界

- 已证实：导出面宽、明文网络配置开启、两个重点 Provider 有应用层校验。
- 未证实：任一导出入口可绕过校验、读取其他用户数据或获得更高 Android UID。
- 未覆盖：全部 59 个导出组件的运行时 Intent fuzzing、服务端授权和真实
  调用方测试。
