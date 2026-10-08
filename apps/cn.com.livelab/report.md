# 纷玩岛 Android 3.42.0 逆向研究报告

## 1. 最终结论

### 1.1 样本

| 项目 | 值 |
|---|---|
| 包名 | `cn.com.livelab` |
| versionName / versionCode | `3.42.0` / `362` |
| minSdk / targetSdk | `24` / `36` |
| APK SHA-256 | `b00ea747c55ad39b4075745577e97d8d0ce6cc1f068a9bf4b11dc68eb7c9063e` |
| APK 字节数 | `84,046,532` |
| APK 条目 | `2,316` |
| ARM64 native 库 | `18` |
| `classes.dex` SHA-256 | `41510b4ad572f77ea8f0219869c93050c338140e2f0c8cbced35e1ffbf0d0760` |
| `libapp.so` SHA-256 | `97f76391fd38e775320f7516248a5b0364fe27977c545b0c4b9d8e56eac0d7c1` |
| `libflutter.so` SHA-256 | `8f4a42a7abfab1b6a689199a82c69eed48f701138d0cbc09f27b86e0309a892c` |
| Dart | `3.11.5`，snapshot hash `78da37fed6bf1489361a312568249f3f` |
| 声明权限 | 49 条 `uses-permission`（48 个去重，13 个非 `android.permission.*`） |
| 组件 | 137 个；显式导出 29 个，其中 22 个未加 `android:permission` |

运行时为双运行时架构：Flutter 主壳 + Vue 小程序（`miniapp.livelab.com.cn`
静态资源）。业务逻辑主体位于 `libapp.so` 的 AOT/snapshot 指令段，通过 Blutter
恢复出 78,707 行快照池与 151,605 行反汇编，故本报告的 Dart 侧结论以
**精确地址 + PP 池常量偏移**给出。

### 1.2 网络与协议

应用只有一个业务 HTTP 出口。请求由 Dart 侧 `SXa` 公共处理闭包统一改写，再经
`gXa.xhd` 包装器按 URL 类型分派到 `gXa.yhd`（响应/错误）与 `gXa.slc`（成功）。
静态抽出 **240 条业务路径**，前缀为 `app/ appShow/ auth/ member/ order/
orderAfterSale/ pay/ performance/ search/ thirdParty/ marketing/ bff/ tool/`。
统一响应封装：

```json
{"code":10000,"msg":"操作成功","data":...}
```

错误分支常量包括 `reLogin`、`ignoreRespError`、`网络连接超时`、`服务器返回错误`、
`App请求超时`、`接收数据异常`、`网络请求异常`。实时通道为
`wss://ws.livelab.com.cn/stomp` 与 `/team`，HTTP 缓存落 `DioCache.db`。

请求头由两段代码精确构造（`libapp.so`）：

| 地址 | 常量 | 含义 |
|---|---|---|
| `0xb4fe80` | PP `0x188d8` | `"Bearer "` |
| `0xb4fed0` | PP `0x188b0` | `"Authorization"` |
| `0xb50ba4` | PP `0x186e8` | `"x-fwd-anonymousId"` |
| `0xb50be4` | PP `0x186f0` | `"platform-type"` |
| `0xb50c14` | PP `0x186f8` | `"platform-version"` |
| `0xb50c30` | — | 调用公共处理 `0xb4ff88` |

另有 `x-fwd-ts`（PP `0x25db8`）、`accept-version`（PP `0x27a58`）、
`global-context`（PP `0x47a70`）。

### 1.3 认证

登录族包含 `auth/app/login/phone`、`phoneCaptcha`、`phoneHw`、`v3/login/wx`、
`v3/login/phoneWx`、`login/identity`、`logout`。票据为 `access_token` /
`refresh_token`（PP `0x17e78` / `0x17ea0`），商城侧另存 `mallToken`（缓存键前缀
`mallTokenCache_`，PP `0x12bb8`），并在 URL 上以 `&token=`（PP `0x1aa18`）与
`?token=`（PP `0x508c0`）追加。短信验证码必须先过 Geetest，再调
`tool/app/captcha/verifyAndSendSms`（PP `0x1c238`）。

### 1.4 风控

- **Geetest 四代**：`gt4.js` / `gt4-index.html` 随包，native `libgtc4core.so`。
  验证参数名 `captchaOutput`/`captcha_output`、`passToken`/`pass_token`、
  `genTime`/`gen_time` 分别位于 PP `0x1c200`–`0x1c228`。
- **设备指纹**：Geetest `fp` 落盘于 `shared_prefs/gt_fp.xml`，服务端校验。
- **请求签名**：`x-fwd-ts`、`nonceStr`（PP `0x40e98`）、`timeStamp`
  （PP `0x40ec0`）、`signType`（PP `0x40f28`）参与签名族；`buildSignature`
  （PP `0x24818`）是 `package_info` 插件字段，**不是**请求签名字段。
- **混淆闭包**：`libgtc4core.so` 的 58 个 `.datadiv_decode*` 全部由
  `.init_array` ABS64 重定位注册，在 `JNI_OnLoad` 前执行；算法已逐字节还原
  （见 `risk.md` §5），**不保留未解释实现**。

### 1.5 上传下载范围

上传走 `thirdParty/qiniu/app/upload`（PP `0x1d3c0`）；下载/取回走
`thirdParty/qiniu/app/getPrivatePdfUrl`（PP `0x54298`）、
`bff/member/invoice/v2/getInvoiceUrl`（PP `0x413f0`）、以及
`app/homepage/*.json`、`app/project/*`、CDN 资源。人脸凭证经
`thirdParty/faceid/app/bizToken` 取得后传给 `libmegface.so` 做活体，回传
`thirdParty/faceid/app/verify`。全部为静态调用点结论，未实际传输。

### 1.6 越权 / 提权 / 未告知 / 超范围

| 判断 | 结论 | 依据 |
|---|---|---|
| 越权 | **客户端未发现越权调用** | 未发现 `pm grant`、`su`、`Runtime.exec` 提权链；`READ_CONTACTS` 等仅用于本地选票联系人，见 `permissions.md` §4 |
| 提权 | **未发现** | 137 个组件中 `SYSTEM_ALERT_WINDOW`、`REQUEST_INSTALL_PACKAGES` 有声明但无对应调用闭环；导出面见 §5 |
| 未经告知 | **存在告知粒度不足风险** | 48 个去重权限中 13 个为厂商推送/广告类，隐私政策文本不在 APK 内，无法静态证明逐项告知，见 `privacy.md` |
| 超范围获取 | **未发现“采集即上传”的闭环** | 位置、通话记录、日历、录音、安装包权限声明了但缺少到业务端点的上传调用链；见 `permissions.md` §3 |

### 1.7 存储加密结论

平台层**不加密**：`SharedPreferencesImpl.loadFromDisk()` 直接
`new FileInputStream` + `XmlUtils.readMapXml`，`createFileOutputStream()` 直接
`new FileOutputStream`。设备端 37 个 `shared_prefs/*.xml` 实为二进制密文，
结构还原为**位置相关、跨文件共享同一密钥流的流式加密**：

- 37 个文件前 `68` 字节完全相同，`69` 字节起分叉 → 明文前 `67` 字节固定，
  第 `67` 字节必为 `<`（XML 首标签起始），第 `68` 字节仅 4 个取值
  （计数 `23/8/4/2`）→ 4 种首标签类型；
- 密钥流**跨文件复用**：对偏移 `0..299`，同一个单字节密钥流可使 `279/300`
  个偏移上全部文件落在可打印 XML 字符集内；独立随机加密不可能出现该分布；
- 密钥流**非周期**：以 `</map>` 已知明文对周期 `1..2048` 全部产生冲突，
  排除重复密钥 XOR；以密文逐字节分叉点在 `68` 而非 `64` 排除 16 字节分组的
  CBC/ECB。唯一与全部观测一致的是**计数器模式流密码、固定 key+nonce**。
  APK 字符串表含 `AES/CTR/NoPadding`（`classes.dex` 偏移 `0x18b155b`）、
  `AES_KEY`/`AES_IV`（`0x18b159f`/`0x18b1597`）、`aes_key`/`aes_iv`
  （`0x19460f3`/`0x19460eb`）与 `android.app.SharedPreferencesImpl`
  （`0x1947486`），与该结构一致。

已知明文攻击下，固定 XML 序言 `67` 字节直接暴露等长密钥流，且因密钥流共享，
该暴露对**全部 37 个文件**同时生效。因此该存储加密不提供机密性保障。
详细还原见 `storage.md`。

## 2. 综合矩阵

| 维度 | 结论 | 主要证据 |
|---|---|---|
| 网络交互 | 240 条路径、单一拦截器族 | `network.md` §1-§3 |
| 协议格式 | JSON 封装 + 8 类头 + WS/STOMP | `network.md` §4-§6 |
| 认证 | 6 种登录 + 双令牌 + 验证码闸门 | `auth.md` |
| 风控 | Geetest + 设备指纹 + 签名 + 响应分支 | `risk.md` |
| 上传下载 | 七牛上传/私有下载/发票/CDN | `transfer.md` |
| 权限 | 48 去重、29 导出、22 无权限保护 | `permissions.md` |
| 隐私 | 采集能力与上传闭环分离判断 | `privacy.md` |
| 存储 | 平台不加密；密钥流复用已还原 | `storage.md` |
| 加密/混淆 | Geetest 解码算法逐字节还原，无未知实现 | `risk.md` §5、`evidence.md` §5 |

## 3. 研究边界

本报告只使用静态反汇编、DEX/XML/资源解析与设备只读核对，不构造请求、不登录、
不支付、不实际上传下载、不绕过风控。服务端评分规则、处罚策略、数据保存期限
不可从客户端观察，报告不作推测。
