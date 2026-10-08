# 闲鱼 7.28.40 综合结论

闲鱼 7.28.40（versionCode 521）是阿里巴巴淘宝体系内的二手交易应用。本次逆向
覆盖 **9 个 DEX、125 个 native 库、214 个 SP 与 21 个 SQLite 文件**，并与自有
设备的真实数据做了**只读**核对；native 侧进一步核对了常量、跳板、zlib、
UVM handler 与高熵 data file。结论按证据等级给出；全部要求项均已完成，
不存在未分析清楚的加密实现。

## 1. 网络协议总览

闲鱼业务流量全部走阿里 MTOP 网关（`acs.m.goofish.com`），结构与签名链在客户端
静态代码中完整可见：

```
BaseApiProtocol 子类
  └─ @ApiConfig(api, version, needLogin, needWua, needJsonReq)
       └─ MtopLauncher.send()
            └─ ClientHeaderInterceptor（闲鱼自有头）
                 └─ ApiBusiness（灰度重定向 / 注解解析）
                      └─ mtopsdk.mtop.MtopBuilder
                           └─ InnerProtocolParamBuilderImpl
                                └─ InnerSignImpl（SecurityGuard 三条签名路径）
                                     └─ anet.channel（HTTP/2、SPDY、QUIC）
```

- 域名三环境：`acs.m.goofish.com` / `acs.wapa.goofish.com` / `acs.wapatest.goofish.com`；
  淘宝域单独走 `guide-acs.m.taobao.com`。
- TTID = `36137` + `21407387` + `700502`（appkey + 渠道版本）。
- URL：`<scheme>://<domain>/<entrance>/<api>/<version>/`，默认 `GET`，
  `needJsonReq` 时改 POST 并包 `RequestWrapper{ "req": "<json>" }`。
- 响应信封：`{ api, v, ret:["SUCCESS::..."], data }`。
- 签名头：`x-sign`（`requestType=7`，`ATLAS_FAST`）+ `x-mini-wua`/`x-sgext`/`x-umt`
  统一产出，或降级 HMAC-SHA1（`requestType=3`）。
- `createAVMPInstance("mwua","sgcipher")` 进入 `libsgmainso` 的 715 项
  AVMP/UVM handler；data file 由 `0xa5944` descriptor 选择长度前缀 zlib
  容器，未映射为可执行代码。
- 317 条 `Api` 枚举接口、320 个静态请求类；**55** 个 `needLogin=true`、
  **5** 个 `needWua=true`。
- 灰度开关 `search_api_fl_switch` / `main_api_fl_switch` / `msg_api_fl_switch`
  可把旧接口映射到新网关（精确映射见 [network.md](network.md) §7）。

## 2. 认证机制

登录态由**本地 SP + 请求头 + Cookie** 三层表达：

- Cookie：`sid/subSid/uid/userId/nick/ssoToken/auto_login/sessionKey/
  sessionExpiredTime/…`；`cookie2` 显式校验，`sgcookie` 由 Orange 控制。
  请求头层面不使用 h5api 的 `_m_h5_tk`，而是 `x-sign` + `x-sid`/`x-uid`。
- 会话失效广播：`NOTIFY_CLEAR_SESSION`、`NOTIFY_SESSION_VALID`。
- 设备身份：UTDID（`UTDevice.getUtdid`）、UMID（`IUMIDComponent.initUMID`）、
  OAID（多厂商兼容：`FishOaid.getOaidFromCache` / `aliOaid` / `oaid2` 荣耀兼容）、
  自生成 `fish_imei`。
- `mtop.taobao.idle.device.report/1.0`（`needWua=true`）上报
  `brand/osVersion/processUuid/oaid/oaid2/gzuOaid/aliOaid/first_open/extendArgs`。

## 3. 上传与下载

| 通道 | 端点 | 数据 |
| --- | --- | --- |
| 图片/视频/文件 | `<https?>://<domain>/uploadv2.do` | `token, offset, retrytimes, appkey, t, utdid, userid, fileid, filename, filesize, segmentsize`；二进制分片 |
| 对象存储 | `https://oss-cn-hangzhou.aliyuncs.com` + STS | 聊天文件/知识库/模型包（`mtop.alibaba.idlefish.getststoken` 取 STS） |
| 行为/风险 | `mtop.alibaba.client.ccrc.risk.upload` / `algo.upload` | 样本与算法结果（见 §5） |
| 静态资源 | `appdownload.alicdn.com`、`gw.alicdn.com`、`img.alicdn.com`、`cloud.video.taobao.com` | 规则包、图片、视频 |
| 消息媒体 | `mtop.taobao.idlemessage.file.token.v1` | IM 附件 token |

上传请求 `setCookieEnabled(false)`，不携带业务 Cookie。

## 4. 越权 / 提权 / 超范围

- **系统提权**：无。`ReflectHelper.unseal` 只解除 Android 隐藏 API 限制，
  非提权。
- **权限**：116 条声明；50 个导出 Activity、30 个导出 Service、24 个导出 Receiver、
  2 个导出 Provider。无 `signature` 级权限，无 root 利用代码。
- **动态加载**：存在（`REQUEST_INSTALL_PACKAGES` + Atlas/`libdexloaderuc.so`），
  范围限于本应用签名资源。
- **功能驱动的超范围采集**（详见 [privacy.md](privacy.md) §3.2）：
  1. 应用列表（`QUERY_ALL_PACKAGES`）超出风控所必需；
  2. 附近 WiFi 全量 BSSID/SSID/频段超出“定位到城市”所需；
  3. 内容/行为样本上传未提供逐场景授权；
  4. 行为时序在 `mfe_db`、`walle_ut_user_track.db`、`edge_compute.db` 本地留存。
- **未经告知**：未发现完全无告知的采集通道；风控样本仅在隐私政策“安全风控”
  条款下概括授权，无逐项披露。

## 5. 风控完整逆向

### 5.1 四层结构

| 层 | 组件 | 判定 |
| --- | --- | --- |
| L1 反攻击 | `AntiAttackHandlerImpl`、`ApiLockHelper` | 41x 返回码、单 API 频次锁 |
| L2 设备签名 | `InnerSignImpl` + `libsgmainso` | `x-sign`、`wua`、`umid`、`x-sgext` |
| L3 业务拦截 | `SecurityInterceptor` | `FAIL_BIZ_FORBIDDEN` / `NEED_REAL_VERIFY` / `RISK_USER_VERIFY` |
| L4 内容/行为 | `CcrcService` + `OffClientWukongGuard` + Wukong | 精确结果码 |

### 5.2 精确判定代码（节选）

```java
// SecurityInterceptor.java
private boolean isPenalty(String s) { return StringUtil.isEqual(s, Constants.FAIL_BIZ_FORBIDDEN); }
private boolean isRealVerify(String s) {
    return StringUtil.isEqual(s, Constants.NEED_REAL_VERIFY)
        || StringUtil.isEqual(s, Constants.RISK_USER_VERIFY);
}

// OffClientWukongGuard.java —— 站外跳转
boolean hit = code==DETECT_HIT_ACTION || code==DETECT_HIT_NO_ACTION || code==DETECT_NO_HIT;
if (code == DETECT_HIT_NO_ACTION) { callback.onBlock(); }   // 阻断
else if (hit)                     { callback.onAllow(); }   // 放行
else                              { finishPending(...); }   // 失败 → fail-open
```

超时：`off_client_wukong_timeout_ms` 默认 **200 ms**；降级开关
`ccrc_off_client_risk_downgrade` / `off_client_wukong_fallback` 均为 fail-open。

### 5.3 设备端四场景（SP 可核对）

| ccrcCode | 场景 |
| --- | --- |
| `wukong_ccrc_idlefish_chat_swindle_risk` | 私聊诈骗 |
| `wukong_ccrc_idlefish_comment_risk` | 评论 |
| `wukong_ccrc_idlefish_swindle_risk` | 交易诈骗 |
| `wukong_ccrc_idlefish_off_client_risk` | 站外跳转 |

结果码（`WukongResultCode`）：
`ACTIVATE_SUCCESS=100000`、`ACTIVATE_ING=1000001`、`ACTIVATED=1000002`、
`UN_ACTIVATE=1000003`、`ACTIVATE_FAIL=1000004`、
`DETECT_HIT_ACTION=200000`、`DETECT_NO_HIT=200001`、
`DETECT_HIT_NO_ACTION=200002`、`DETECT_PRE_FAIL=200003`、
`DETECT_ENGINE_EVALUATE_FAIL=200004`。

样本类型：文本、行为、图片、音频、视频、文件、实时流、多模态。

### 5.4 本地行为库（设备端只读核对）

| 库 | 内容 | 实际行数 |
| --- | --- | ---: |
| `files/.wukong/mfe_db/v1.db` → `mfe_basic` | `ccrcSellerUniqueCnt1h_list BLOB`（按小时卖家唯一计数） | 5 |
| `files/DAI/Database/walle_ut_user_track.db` → `usertrack` | `page_name, event_id, args, page_stay_time` | 5175 |
| `files/DAI/Database/edge_compute.db` | 行为图（PV/点击/滚动/曝光/搜索） | 多表 |

## 6. 密码学全量清单（无未解释项）

| 算法 | 调用格式 | 次数 | 用途 |
| --- | --- | ---: | --- |
| AES-CBC | `PKCS5/PKCS7/NoPadding` | 49 | 本地票据/媒体 |
| AES-GCM | `NoPadding` | 8 | 完整性加密 |
| AES-ECB | `PKCS7/NoPadding` | 4 | 密钥表 |
| RSA | `OAEP-SHA256/PKCS1/NoPadding` | 10 | 密钥协商/封装 |
| SM4 | `CBC/PKCS5Padding` | 1 | 国密对称 |
| DES | `DES` | 4 | 推送配置、支付宝支付/安全组件的旧协议兼容 |
| HMAC-SHA1 | `HmacSHA1/HmacSha1` | 10 | `x-sign`、UT 签名 |
| HMAC-SHA256 | `HmacSHA256/hmacsha256` | 3 | 新签名 |
| MD5 | `MD5/md5` | 161 | 签名串、去重 |
| SHA-1/SHA-256 | `SHA1/SHA256` | 19 | 校验 |
| RC4 | `RC4.rc4(...)` | 14 | UT 默认密钥解码 |
| SM2/SM3 | `SM2Engine` 1 次、`SM3Digest` 4 次 | 1/4 | 在线账号内核、Mpaas RPC、客户端签名的国密运算 |
| Base64/GZIP | — | 124 | 编码/压缩 |

native 静态扫描覆盖 125 个库：标准 AES/SHA/MD5/SM4/TEA/Blowfish 常量
无未归属项；ARMv8 crypto 指令只在 `libtb_crypto.so` 与
`libopenssl.so`。`libsgmainso` 的 515 个跳板用于基本块打散，26 条 zlib
流解析为 13 个 UVM 容器与 13 个 Base64 data file；UVM 校验、715 项
handler、`w3==2` 表选择和 `0xa5944` data descriptor 均有精确地址。
`VA=0x1e4985..0x1eb5ec` 的高熵区位于 RW LOAD，无执行权限，且 syscall
白名单不含 `mprotect/mmap/memfd_create/execve`。完整证据见
[native_crypto.md](evidence/native/native_crypto.md)。

唯一硬编码密钥材料：`UTBaseRequestAuthentication.getDefaultAppAppSecret()`
中的 32 字节数组，经 RC4 解码为 UT 埋点默认 HMAC-SHA1 key。
其余密钥在 `libsgmainso`（白盒/加固）与 STS 临时凭证中；白盒侧没有
未解释的加密实现。

## 7. 手机端真实数据格式

全部 21 个 SQLite 文件头均为明文 `SQLite format 3\0`，`integrity_check` 全 `ok`。
关键表：

- **IM**（`fleamarket_idlefish_im_*.db`）：
  `SessionInfo(sessionId, sessionType, summary, unread, isAtTop, draft, …)`（22 行）、
  `Message(messageId, sid, contentType, textContent, readState, sendState, …)`（0 行）、
  `UserInfo(userIdType, userId, fishNick, nick, logo, userInfo)`（48 行）、
  `FileInfo(fileKey, sessionId, localId, data)`。
- **键值**：`fishkv(key, moduleName, value, createTime, updateTime)`。
- **行为**：`usertrack(page_name, event_id, args, page_stay_time, owner_id)`（5175 行）、
  `mfe_basic(ts, ccrcSellerUniqueCnt1h_list BLOB)`（5 行）。
- **隐私同意**：`user_provacy_policy.xml` 与 `user_provacy_policy_new.xml` 均 `true`；
  `fish_device_activate.xml` 含 `fish_firstOpen_flag=1`。
- **MMKV**：`files/mmkv/splash_ad_mmkv`（明文二进制）。

## 8. 结论

1. **网络**：闲鱼走标准 MTOP 网关，签名链完整可静态还原；协议具体格式与
   317 条接口清单均已闭环。
2. **认证**：淘宝账号体系，本地 SP + Cookie + `x-sign`/`sid`/`uid` 三层；
   不使用 h5api 的 `_m_h5_tk`。
3. **上传/下载**：三条独立上传通道与两条下载通道，字段、分片与响应头语义完整。
4. **风控**：四层结构全部映射，精确到常量值与判定分支；Java/native
   算法、跳板、UVM handler 与 data file 均已闭合，无未解释加密实现。
5. **隐私**：无系统提权、无完全无告知采集；存在**功能驱动的超范围采集**
   （应用列表、附近 WiFi、行为/内容样本），均有对应功能或风控场景。
6. **本地数据**：21 个 SQLite 全为明文，可直接核对 schema；与代码入口一一对应。

详细论证与精确代码引用见
[network.md](network.md)、[auth.md](auth.md)、[transfer.md](transfer.md)、
[risk.md](risk.md)、[permissions.md](permissions.md)、[privacy.md](privacy.md)、
[storage.md](storage.md)、[evidence.md](evidence.md)、
[native_crypto.md](evidence/native/native_crypto.md)、
[completeness.md](completeness.md)。
