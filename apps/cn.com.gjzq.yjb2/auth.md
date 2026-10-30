# 认证机制

## 1. 三处账号态

| 位置 | 载体 | 内容 |
|---|---|---|
| `shared_prefs/Token.xml` | 177 B 密文 | 登录票据 |
| `shared_prefs/TokenStatus.xml` | 129 B 密文 | 票据状态（有效/过期） |
| `shared_prefs/account_info.xml` | 545 B 密文 | 账号信息 |
| `shared_prefs/user_login.xml` | 81 B 密文 | 登录态标志 |
| `shared_prefs/auth.xml` | 817 B 密文 | 授权信息 |
| WebView Cookie `YCOMMENTPHPSESSID` | Cookie | 评论子站会话 |

五个文件**整体密文存储**（非逐 key 加密），前 48 字节密文完全一致，说明相同
XML 明文头进入了相同的块级处理结果；这不等同于已还原逐位置 keystream（见
[storage.md](storage.md) §3）。

## 2. H5 侧票据流程

`stockInformation/common/js/NativeAPI.js` 的 `beginLogin()` 分支：

| `loginType` | 含义 |
|---|---|
| `'u'` | 用户登录 |
| `'p'` | 扫码登录（passportId） |
| `1` / `2` / `3` | 数值态映射到 `oLoginType` 表 |

登录前置检查（`NativeAPI.js:226-300`）：

1. `getAppId()` 读当前应用 id；`yjb3.0` 走内部通道直接取 pid。
2. `getAppVersion()` 解析版本；要求 `major >= 5 && minor >= 1 && patch >= 1`
   才启用 `userId`，否则跳下载页。
3. `checkLogin("u")` 通过 `YJBExec(1001, ...)` 查登录态。
4. 返回的 `loginStatus` 不在 `oLoginType[sLoginStatus]` 白名单内 → 触发
   `beginLogin`；`oNextStep.action` 决定下一步（`4002` 原生跳转、`2007`
   关闭旧页开新页、`2008` 开新页、`2009` 关窗）。

`oNextStep.action` 的四个分支在 `NativeAPI.js:368-384`，是登录失败后的
**页面级重定向**，不是票据刷新。

## 3. 交易超时与重新登录

`checkTradeOvertime` 判定 `sTradeOvertime`；命中 `overTimeType` 列表时要求
重新登录。`clearTradeOvertime`（`NativeAPI.js:299`）清除该标志。
`loginSuccessCode === 1` 时 `isNeedClose` 为假，即登录成功但不关闭当前页。

## 4. 轻量级加密参数

H5 请求参数中出现 `u.opKey` 时，客户端以 **AES-128-ECB + Pkcs7** 加密：

```js
// BaseIndexNew.js:117-124
const key = CryptoJS.enc.Utf8.parse("g6j0z0q1w0e9bapp");   // 16 字节
CryptoJS.AES.encrypt(content, key, {
  mode: CryptoJS.mode.ECB,
  padding: CryptoJS.pad.Pkcs7,
}).toString();
```

同一常量在 `stockInformation/con-f/paramConfig.js:50` 以 `APP_SECRET` 出现，
并在 `paramConfigForConvert.js` / `paramConfigForFloorFund.js` /
`paramConfigForStockRotation.js` 重复出现。`opKey` 随后做 Base64URL 变体
（`+`→`-`、`/`→`_`、`=`→`.`），见 `margintrading/build/commons.js`、
`transfer/build/announce.js`、`stockLiving/common/js/bridgeAPI.js`。

**这是参数混淆，不是认证机制**：密钥随包分发，ECB 无 IV，同一明文在同一
build 内产生相同密文。它不构成对请求真实性的保证。

## 5. Native 侧票据

账号票据的真正持有方是 native 进程：H5 通过 `window.yjbInterface.execute`
把登录/交易请求交给 native，native 会话再与 `webapps` 网关通信。`Token.xml`
等仅是缓存。本报告未反编译该会话实现（壳内 `.text` 高熵，见
[completeness.md](completeness.md) §4）。

## 6. 未闭环项

| 项 | 状态 |
|---|---|
| `Token.xml` 密文内容 | 未知（结构已证实：整体密文 + 共享密钥流前 48 B） |
| 票据有效期与刷新策略 | 未知（服务端行为） |
| `libencrypt.so` 的 `tztEncrypt` 输入输出 | 未知（`.text` 高熵） |
| `libndktest.so` RC4/SM4 的具体用途 | 结构已证实（符号存在），调用链未知 |
| `YCOMMENTPHPSESSID` 生命周期 | 未知 |

## 7. 边界

未登录真实账号、未构造登录请求、未抓真实票据。以上全部为静态代码结论。
