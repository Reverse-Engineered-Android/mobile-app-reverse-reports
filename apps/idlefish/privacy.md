# 隐私与告知

## 1. 告知链

### 1.1 首启同意

`com.taobao.idlefish.independent.FishRegProtocolDialogFragment` / `FishPolicyView`
在首启展示：

> 您已阅读并同意《闲鱼社区用户服务协议》 《隐私权政策》 《软件许可使用协议》

同意后由 `launcher/privacy/LaunchPrivacy.setAuthorized()` 写两个 SP 布尔值：

```
user_provacy_policy      -> user_provacy_policy
user_provacy_policy_new  -> user_provacy_policy_new
```

并广播同意事件（`PrivacyAgreedBroadcastReceiver.sendBroadcast`）。
`isAuthorizedGeneral()` 读取这两个值决定是否放行业务初始化。

**设备端只读核对**：`shared_prefs/user_provacy_policy.xml` 与
`shared_prefs/user_provacy_policy_new.xml` 均为 `true`，即同意状态已持久化。

### 1.2 政策文本位置

| 内容 | 位置 |
| --- | --- |
| 用户服务协议/隐私权政策/软件许可 | `terms.alicdn.com/legal-agreement/terms/...` |
| 隐私中心 | `market.m.taobao.com/app/msd/m-privacy-center/index.html` |
| 淘宝会员协议 | `www.taobao.com/go/chn/member/agreement.php` |
| 支付宝相关协议 | `ab.alipay.com/agreement/contract.htm`、`d.alipay.com/agreement/zw.htm` |

### 1.3 隐私相关开关（SP）

- `fish_privicy` → `LOCAL_PRIVACY_RECOMMAND_STATUS`：个性化推荐开关。
  关闭时请求头带 `x-custom-privacy-device-recommend-closed: true`。
- `AMap.privacy.data.xml`、`aliprivacy_sp.xml`、`ali_privacy_recommend.xml`：
  高德与阿里隐私推荐 SDK 的同意状态（独立于主政策）。

## 2. 采集范围与告知的对应关系

| 采集项 | 代码位置 | 是否与告知/功能对应 |
| --- | --- | --- |
| 设备标识（UTDID/UMID/OAID/IMEI） | `ClientHeaderInterceptor`、`DeviceActivateUtils` | 有；隐私政策含设备信息条款 |
| 应用列表 | `QUERY_ALL_PACKAGES` + 统计 | 有；用于安全与推荐，属“超出最小必要” |
| 附近 WiFi BSSID/SSID/频段 | `map/util/LocationUpdate` | 有；用于定位兜底，需位置权限 |
| 精确位置 + 城市/区县编码 | `ApiLBSLocationUpdateRequest` | 有；同城功能必需 |
| 计步与运动 | `WalkStepCounter` | 有；需 `ACTIVITY_RECOGNITION` |
| 文本/图片/音频/视频内容 | Wukong 样本 | **有，但为风控复核**，用户不逐条可见 |
| 行为样本（点击/浏览时序） | `BehaviorRiskSample`、`mfe_db` | 有；风控条款 |
| 站外跳转 URL | `OffClientWukongGuard` | 有；安全拦截 |
| 崩溃/ANR/卡顿 | `anr/`、`fish_block_trace` | 有；质量改进条款 |
| 剪贴板 | **未发现读取代码** | — |
| 通讯录/短信/通话记录 | **未声明对应权限** | — |

## 3. 越权/超范围判断

### 3.1 未发现
- 无系统级权限提升。
- 无通讯录、短信、通话记录、日历读取之外的无关联采集（`WRITE_CALENDAR` 仅用于
  到货提醒写入）。
- 未发现剪贴板、输入法、其他应用数据的读取路径。

### 3.2 存在但功能驱动的超范围
1. **应用列表**（`QUERY_ALL_PACKAGES`）：全量应用清单超出风控所必需，
   与推荐/安全统计共用同一权限。
2. **附近 WiFi 全量扫描**：上报所有可见 AP 的 BSSID/SSID/频段，
   超出“定位到城市”所需，属定位精化与位置风控。
3. **内容样本不区分场景上传**：`ccrc_idle_comment_post_mtee_sns_unify_check`
   在评论发布时对文本做检测，样本与算法结果会经
   `mtop.alibaba.client.ccrc.risk.upload` / `algo.upload` 上报；
   用户侧无逐条授权。
4. **行为时序**：`mfe_basic.ccrcSellerUniqueCnt1h_list`（按小时卖家唯一计数）
   与 `mfe_original`（原始行为）本地留存，属行为画像。

### 3.3 未经告知
- 未发现完全隐蔽、无任何政策条款对应的采集通道。
- 风控样本上传的**具体字段与时机**未在用户可见文案中逐项披露，
  仅在隐私政策的“安全风控”条款下概括授权。

## 4. 数据最小化评估

| 维度 | 评估 |
| --- | --- |
| 与功能必要性 | 位置、相机、麦克风、存储为功能必需 |
| 采集范围 | 应用列表、附近 WiFi、内容/行为样本偏宽 |
| 用户可控性 | 提供个性化推荐开关；无逐场景关闭内容检测的入口 |
| 本地留存 | `mfe_db`、`flybird`/`ut.db` 行为库、`accs.db` 流量库 |
| 传输加密 | 全部走 HTTPS MTOP/OSS；上传通道禁用 Cookie |
| 卸载残留 | `allowBackup=false`，应用数据随卸载清除 |

## 5. 证据等级

- **已验证**：同意 SP 键与值、政策链接、采集代码位置、权限与功能的对应。
- **结构已证实**：风控样本的上报端点与字段，具体上报频率由远端配置决定。
- **不可证**：服务端留存时长与二次利用范围。
