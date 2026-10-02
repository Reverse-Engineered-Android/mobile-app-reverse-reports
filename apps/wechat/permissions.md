# Android 权限、导出组件与提权审计

## 结论

对微信 `8.0.78` / `versionCode=3180` 的 `AndroidManifest.xml`、两个重点
ContentProvider 的反编译源码及现有 smali/ELF 证据进行了静态审计：

1. **未发现本地提权漏洞或已利用的越权路径。** 没有发现 root exploit、
   SELinux 绕过、任意代码执行、以 `system`/`root` UID 运行，或把普通应用
   变成特权应用的可复现链路。
2. **导出组件攻击面较大，但导出不等于越权。** Manifest 声明 59 个导出
   组件，其中 44 个没有 Manifest 级组件权限；这说明需要逐组件检查运行时
   身份、URI、Intent 和签名校验，不能仅凭 `exported=true` 判定漏洞。
3. **两个高风险 Provider 的重点结论均为“有防护、未证明可绕过”。**
   `ShareableChatRecordsProvider` 在 `openFile()` 中校验调用方 UID、单一
   包名、签名证书摘要、会话与路径，并且 `query/insert/update/delete`
   返回空结果或 0；`WXCommProvider` 会读取 calling package/UID，并在多个
   分支使用包名、签名和 URI matcher。没有静态证据证明这些检查可绕过。
4. **Manifest 请求宽权限，但请求权限不等于运行时已授权或已经读取。**
   本轮没有新的运行时 `grantedPermissions`/AppOps 快照，因此不把 100 个
   `uses-permission` 条目描述成“实际调用过”的权限。

## Manifest 概况

| 项目 | 结果 |
| --- | --- |
| 版本 | `8.0.78` / `versionCode=3180` |
| `uses-permission` 条目 | 100 |
| 敏感权限子集 | 38 |
| 自定义权限声明 | 18 |
| 保护级别 | `signature` 13、`signatureOrSystem` 3、`normal` 2 |
| 导出组件 | 59 |
| 导出但无组件权限 | 44 |
| 导出 Provider | 15，其中 6 个无组件权限、3 个 `grantUriPermissions=true` |
| 应用级安全属性 | `allowBackup=false`、`usesCleartextTraffic=true`、`requestLegacyExternalStorage=true` |

`networkSecurityConfig` 的基础配置也为 `cleartextTrafficPermitted="true"`。
这是允许明文网络连接的安全风险，但仍需结合具体域名和传输内容判断，不能
直接等同于凭据泄露或权限提升。

敏感请求权限覆盖位置、蓝牙/Nearby、相机、麦克风、联系人、媒体、存储、
通知、前台服务、任务/覆盖窗口、屏幕捕获检测、生物识别和人脸识别等能力。
其中 Android 高风险权限的实际授权状态，只能由设备运行时快照回答。

## 导出组件

| 类型 | 导出数 | 无组件权限 |
| --- | ---: | ---: |
| Activity | 29 | 29 |
| Provider | 15 | 6 |
| Receiver | 6 | 3 |
| Service | 9 | 6 |

29 个导出 Activity 大多是分享、支付回调、深链、快捷方式、WebView、恢复
和入口 Activity；这是 Android 应用常见的集成面。当前证据只证明它们可被
外部 Intent 触达，没有证明任意 Intent 都能读取聊天数据、改变账户状态或
绕过服务端鉴权。

### 重点 Provider

| Provider | Manifest 防护 | 静态检查结果 |
| --- | --- | --- |
| `ShareableChatRecordsProvider` | `normal` 自定义读取权限 + `grantUriPermissions=true` | `openFile()` 先检查远端 kill switch，再用 `Binder.getCallingUid()` 取得调用包，要求 UID 只映射到一个包，并验证包签名 SHA-256；随后校验 authority、sessionId、action 和路径。`query/insert/update/delete` 返回空/0。 |
| `WXCommProvider` | 无组件权限 | `query()` 等待初始化后解析 URI matcher，再取得 calling package/UID；分支中读取包名、签名，并通过 `ExtOpenApiCallEvent` 等受控分发。未发现无条件返回聊天、支付或账户数据的路径。 |
| `ExtControlProvider*` | `signatureOrSystem` + `signature` 读写权限 | Android 权限层面限制为同签名或系统应用。 |
| `WaidProvider` | `signature` 写权限 | Manifest 层面限制为同签名应用。 |
| `ShareableChatRecordsProvider` 之外的无权限 Provider | 无组件权限 | 分别服务于分享视频、游戏资源下载、开放语音控制、Normsg 和 XWeb；本轮未逐一分解全部数据面，故保留攻击面，不宣称其全部安全。 |

## 提权与越权边界

### 已排除

- 没有发现可复现的本地提权原语或已执行的注入、Hook、ptrace、`su` 提权。
- `ShareableChatRecordsProvider` 不是“任何应用都能读聊天记录”的简单导出
  Provider；现有反编译代码包含调用方与文件路径校验。
- 没有静态证据证明能通过导出 Activity/Service/Provider 读取其他用户会话、
  冒充微信进程或获得 `system` 权限。

### 仍需动态验证

- 6 个无组件权限 Provider 的全部 URI/command 分支，尤其是带
  `grantUriPermissions=true` 的 provider。
- 29 个导出 Activity 对 Intent extra、`Intent.setData()`、pending intent 和
  回调来源的校验。
- 服务端对第三方调用、支付回调和 open API 的授权状态。
- 真实设备上危险权限是否已授予，以及调用是否发生在用户同意之后。

这些项目属于潜在攻击面和证据缺口，不是已确认漏洞。最终结论仍为：
**本轮未发现越权或提权；发现较宽的导出/权限面，需要逐入口验证。**

## 复现

```bash
jadx --no-src -d decoded base.apk
jadx --no-src -d provider-src base.apk \
  com.tencent.mm.app.provider.ShareableChatRecordsProvider \
  com.tencent.mm.plugin.base.stub.WXCommProvider
# 从 decoded/resources/AndroidManifest.xml 按 exported、permission、
# grantUriPermissions、uses-permission 和自定义 permission 聚合。
```

公开证据索引见 [evidence.md](evidence.md)；不发布原始 APK、完整 DEX、
签名值、真实 URI、调用方包名或网络负载。
