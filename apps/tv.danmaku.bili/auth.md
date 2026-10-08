# 认证机制

## 1. 请求层

### 1.1 公共身份参数

`AuthInterceptor.addCommonParam` 加入 `platform=android`、`mobi_app`、`appkey`、
`build`、`buvid`、`local_id`、`channel`、`c_locale`、`s_locale`；登录态
`access_key` 由 `BiliConfig`/账号 delegate 注入。对应请求头包括：

```text
Display-ID, Buvid, User-Agent, Device-ID, fp_local, fp_remote,
session_id, x-bili-locale-bin, GuestId
```

`Buvid` 同时出现在 query/body 与 `Buvid` 头，用于稳定设备关联；`access_key` 是
服务端会话凭据，不能从客户端静态分析推出其有效期或撤销策略。

### 1.2 三条签名路径

`AuthInterceptor.signQuery(Map)`：

```java
if (paramDelegate.enableAppSecretSign()) {
    return LibBili.signQuery(map, hexToBytes(paramDelegate.getAppSecret()));
}
if (ab("api.enable-custom-key-secret", true)) {
    return LibBili.signQuery(map, 1, 0);
}
return super.signQuery(map);
```

第一、二条只是向 native 提供不同 key/secret 选择；第三条使用 native 默认 appkey。
native `getAppKey` 通过 19 项平坦 `strcmp` 链解析 `mobi_app`，未知值回退
`android`。appkey 是客户端标识而非账号 secret。

## 2. native 签名

JNI 注册（地址均在 `libbili.so`）：

| Java 方法 | 地址 | 作用 |
| --- | --- | --- |
| `a(String)` / `ao(String,int,int)` | `0x9030` / `0x9038` | 取 appkey |
| `b(String)` | `0x9048` | 构造 AES IV |
| `s(SortedMap)` | `0x9050` | 默认签名 |
| `so(SortedMap,int,int)` | `0x9058` | key/secret 选择 |
| `so(SortedMap,byte[])` | `0x9068` | 显式 secret |
| `dp(boolean)` / `st(byte[],SortedMap,String)` | `0x9228` / `0x9230` | 设备/签名辅助 |

`SignedQuery.r` 负责排序、百分号编码和拼接；`libbili.so` 在 `0xffac` 初始化 MD5、
在 `0xffc0` 更新数据，调用点 `0x175f0`、`0x17600`、`0x177d8`、`0x17824`，
然后用 `.data 0xdcbc` 的 `"%02x"` 格式逐字节转成小写 32 位摘要。MD5 IV 常量位于
`.rodata 0xc0a50`，算法结构和地址均已由反汇编与受控模拟交叉确认。

## 3. 账号与登录

APK 中存在 passport/SSO、扫码、OAuth 授权和第三方登录 Activity，登录成功后把
`access_key`、账号/设备参数交给 `AccountConfig`。导出的 `SSOActivity`、
`AuthLoginActivity`、`IntentHandlerActivity` 等是 Android 组件入口，不代表任何调用者
都能获得令牌；实际授权仍需服务端校验。

## 4. 第三方凭据

`CommonParamDelegate` 内含移动、联通、电信入口的 App ID/Secret 常量，用于特定
免登录或渠道请求。报告不公开具体值。其结论是：客户端包中存在可被逆向获得的第三方
静态凭据，存在凭据暴露面；是否能越权取决于第三方服务端的 key scope，客户端静态
证据不能证明已发生越权。

## 5. 未验证项

服务端对签名、access_key、buvid、设备指纹的权重、绑定关系和失效策略不在 APK 内，
因此不作推断。本报告没有登录、重放、伪造签名或令牌提取测试。
