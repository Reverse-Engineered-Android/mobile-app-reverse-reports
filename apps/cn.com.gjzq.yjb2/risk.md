# 风控机制

## 1. 结论与边界

本报告已还原**客户端可达的风控流程、接口参数、状态机、展示规则和本地缓存**，
但没有登录、没有构造业务请求，也没有访问服务端风控引擎。因此可以证明“客户端
如何请求、判断、展示和跳转”，不能证明服务端评分公式、阈值下发、处罚动作或
数据保存期限。下文把“客户端已验证”和“服务端不可见”分开标注。

## 2. 样本与运行态核对

主样本固定为 `9.05.004` / `20260903`，APK SHA-256 为
`e6cc89f97669d86601843a8c741459c2c3aa74386cb7e2206a7da60e7d10c82d`。
只读设备核对时安装的是 `9.05.007` / `20261028`，APK SHA-256 为
`427de1dcb6c60f57ba0e5475dc7f4933f4f18bb50ec6ffd666a811fa3313871a`；
该版本仅用于格式核对，不与主样本混写。

设备上 `files/app_payload.dex`、`app_payload.version` 和 `gjzq_*` 文件是
分析期间注入的 `GjzqProbe`（版本戳 `gjzq-objdump-20261029-11`），不是 App
下发动态 DEX，也不是风控载荷。它们被排除在样本结论之外。

## 3. 风险中心与持仓风险

`trans/build/riskcenter_hold.js` 的请求封装为：

```js
const service = holdType === "margin"
  ? "trade.counselor.airiskcenter.margin.hold.list"
  : "trade.counselor.airiskcenter.hold.list";
fetchYJBAPI({
  functionId: 1000,
  subSystemNo: 519,
  serviceName: service,
  clientId: "($clientId)",
  fundAccount: holdType === "margin" ? "($marginAccount)" : "($fundAccount)",
}, { wantToLogin: true, loginType: holdType === "margin" ? 2 : 1 });
```

成功条件是响应外层 `code === 0`；页面只消费 `result.cardList`。每张卡包含
`stockCode`、`stockName` 和 `risk[]`；每个风险项包含 `id`、`eventCode`、
`factoryCode`、`tag`、时间/分析文本等展示字段。页面按 `risk[]` 渲染风险标签、
历史交易分析、指标和操作入口，并把 `stockName`、`stockCode`、`eventCode`、
`factoryCode` 作为埋点/跳转参数。客户端不计算分数，只显示服务端返回的卡片。

## 4. 融资融券风险中心

`trans/build/riskcenter_financing.js` 另有两个同族接口：

| serviceName | 用途 | 登录类型 |
|---|---|---:|
| `trade.counselor.airiskcenter.margin.exerciseFinanceMarginRatio` | 融资保证金比例 | `1` |
| `trade.counselor.airiskcenter.margin.exerciseFinanceContractExpiring` | 融资合同到期清单 | `1` |

两者都使用 `functionId=1000`、`subSystemNo=519`，参数包含
`clientId`、`fundAccount`、`opStation`、`password`；成功条件同样是 `code===0`。
页面展示比例、到期合同和处置入口，阈值与最终处置由服务端给出。

## 5. 风险关注页面与购前闸门

### 5.1 风险关注列表

`information/build/optional/riskAttention.js` 不是评分器，而是服务端风险结果的
消费端：

1. 读取 `scene`（默认 `fav`，可为 `hold`），并通过 native 的 `currentStocks`
   生成最多 100 个 `stockCode.codeType` 过滤串；`codeType` 只接受三个位掩码
   类别。
2. 调用共享 API helper 的 `{scene, codeList, chanceType:"risk"}` 查询。
3. 从 `dataList` 只保留 `productType === "AI_RISK"` 的条目，再用
   `contractStatus[productType].unlockedStock` 判断是否已解锁。
4. 展示 `riskId`、`stockName`、`stockCode`、`type`、`content`、
   `factoryCode`、`eventCode`；点击通过 `3045` 跳转，URL 中的
   `($riskId)`、`($stockName)`、`($type)`、`($stockCode)`、`($factoryCode)`、
   `($eventCode)` 会被替换。

页面埋点为 `watchlist_show` / `watchlist_click`，空态为“暂无符合条件的数据”。
没有发现客户端风险评分或处罚判定分支。

### 5.2 购前风险闸门（AI 投顾解锁）

`ai-adviser/optional.js`（偏移 `0x179280`、`0x1c8bec`、`0x4c564`）在下单/解锁
前并联查询**两件事**，任一不满足即拦截：

```js
// m = <origin>/yjbwebservice/core/api/json
function Y()  { return http.post(m, {userId:"($userId)",
  UniqueKey:"serviceName.users.external.profile.getRiskInfo"},
  {directResponse:true}); }                                   // 客户风险等级
function fe(e){ return http.post(m, {loginId:"dev4", ...e,
  UniqueKey:"serviceName.agreement.signQuery"}); }            // 协议签署状态

Promise.allSettled([
  Y(),
  fe({khh:"($userId)", wbkhlx:"6",
      xylxdm:"AIGPJT-TY-SDXPGJGQRS;AIGPJT-TY-CPFXBSDJSJTZZQRS"}),
]).then(([a, b]) => {
  const l = a.value.result, u = b.value.result;
  if (l?.riskLevel !== 99 &&                       // 99 = 专业投资者，直接放行
      (l?.riskExpired || l?.riskLevel === -1 ||    // 已过期 / 未测评
       !(u?.length > 0)))                          // 无已签协议
      return blocked();                            // 弹免费体验/跳签约页
  …unlock();
});
```

`wbkhlx` 是「外部客户类型」编码，同文件的映射表把它写成
`{USER:"6"→($userId), PID:"2"→($passportId), CLIENT:"2"→($clientId),
KHH:"5"→($clientId), PRESIGN:"1"→($preappoint)}`，即 AI 投顾用 `6`（=userId）。
同一段闸门在 **AI 选好股**（`xylxdm:"AIXHG-…"`，偏移 `0x1a9162`）里复用，
只是协议号不同；两条路径都在通过后调
`serviceName.aiStrategyProduct.freeTrail` 解锁。另有
`professionUser` 分支：已是专业投资者时直接弹「使用成功」并返回，不再查协议。

`getRiskInfo` 返回 `{riskLevel, riskExpired}`；`riskLevel` 的取值域与
`ai-customer-service/business-risk.js` 的客户等级表一致（`C0…C6`，`C6` =
“暂不启用”），`99` 是专业投资者旁路，`-1` 视为未测评。被拦截时的对话框
指向 `dialog.html?orderInfo=<urlencode(JSON)>&title=<name>&biz_type=aigpjt`，
其中 `orderInfo` 含 `strategyType / targetCode / targetType / targetName /
gainType / authType`，即**拦截结果被编码进 H5 路由参数**，服务端不参与该分支。

同文件的 `postRiskInfo`（`serviceName.users.external.profile.postRiskInfo`，
`POST /core/api`）是等级写入入口，`getUserInfo`
（`serviceName.users.external.profile.getUserInfo`，带 `desensitize:false`）是
等级展示的数据源；问卷侧对应
`serviceName.questionnaire.result.pre.computed`（预计算）与
`serviceName.questionnaire.result.save`（保存）。

### 5.3 权限位：`permDesk.*`

`vas/build/buy0.js`（偏移 `0x184c`）在购买增值服务前查询可用性：

```js
{ serviceName:"permDesk.v2.query", userId:"($userId)",
  nameCodes:<"HSLV2"|"HKLV2"|bizCode>, functionId:1000, subSystemNo:519 }
→ result[0].availability ∈ AVAILABLE | COMING | EXPIRED | DISABLE
→ 可用/即将可用取 result[0].endDate 作为 startDate，
  过期/停用则取当天；随后再按 startDate + value + timeUnit 调
  serviceName:"permDesk.calculate"
```

`permDesk.calculate` 只做「到期日推算」，真正的权限判定在 `availability` 字段，
由服务端下发。`permDesk.v2.calculate` 是同族的 v2 名称。

### 5.4 二维码/深链的访问授权

`vas/build/commons.js`（偏移 `0x8cd3`）在扫码进入外部 URL 前调用：

```js
fetchYJBAPI({functionId:1000, subSystemNo:519,
             serviceName:"access.authority", url: y.url},
            {bulletError:true, permitType:true});
→ parseInt(result.is_auth) !== 1
   → 重定向到 envConfig.SCANQR_RESULT_URL + "?is_auth=<code>&content=<原始串>"
```

`is_auth` 的三值语义在 `investCalendar/build/Check.js` 的注释里写明：

| `is_auth` | 含义 | 客户端动作 |
|---:|---|---|
| `1` | 在白名单中，允许访问 | 继续 |
| `2` | 在黑名单中，不允许访问 | 跳 `SCANQR_RESULT_URL?is_auth=2` |
| `3` | 不在黑名单中（未白名单） | 跳 `SCANQR_RESULT_URL?is_auth=3` |

即**被扫 URL 的准入结论由服务端给出**，客户端只做 `=== 1` 判定与失败跳转；
`permitType:true` 让它拿到未经 `code===0` 过滤的原始串。
`treasurybonds/build/tradeToday.js`、`select.js` 与
`ycomment/common/vendors/NativeAPI.js` 有同族调用。这是本样本里唯一出现
「黑名单」字样的判定路径，且判定结果是服务端返回的枚举，不含本地计算。

### 5.5 价格预警规则（`spew.rule.*`）

`stockWarning/build/index.js`（偏移 `0x1f956`）定义了两套并行的规则模板，
均为 `functionId:1000` + `subSystemNo:519`：

| 服务名 | 关键参数 |
|---|---|
| `passportfront.trade.spew.rule.{add,queryOne,queryAll,delete}` | `source_account`、`source_account_type`、`stockCode`、`stockName`、`marketCode`、`priceUpTo`、`priceDownTo`、`percentRise`、`percentFalling`、`lastSequence`、`pageSize` |
| `trade.spew.rule.{add,queryOne,queryAll,delete}` | `userId` + 同一组股票/价格字段 |

两套方法名完全对称（`add / queryOne / queryAll / delete`），`passportfront.*` 面向
「资金账号 + 账号类型」维度，`trade.*` 面向 `userId` 维度；页面按当前登录类型
二选一，登录前用 `passportfront.*` 并提示「用户登录后即可设置股价提醒」。
阈值由用户输入，客户端只做上下限校验（`priceUpTo > priceDownTo` 之类），
触发与推送都在服务端。

### 5.6 短信验证与设备绑定

| 服务名 | 参数 | 用途 |
|---|---|---|
| `uasd.v2.smsPush` | `{mobile, template_no:17, expire_minutes:10, validate_mobile:1}` | 发送验证码 |
| `uasd.v2.validate` | `{mobile, template_no:17, captcha_code, expire_now:2}` | 校验验证码 |

出现在 `me/build/unbind_account.js`、`change_phone_num.js`、
`change_phone_num_old.js`、`investCalendar/build/unbindAccount.js`，即
**换绑手机/解绑设备**类高危操作的验证入口。`template_no:17` 是模板号，
`expire_minutes:10` 是客户端声明的有效期参数。

### 5.7 交易时段校验

```js
serviceName.core.timeservice.checkTransactionTime
  { type:3, time_kind:"E", exchange_type:1 }        // GET
```

`ai-adviser/optional.js`（偏移 `0x1798d9`）用它判断当前是否在可交易时段；
返回布尔值。`type:3` 与 `time_kind:"E"` 是固定值（`E` = 交易时段类目），
具体时段表在服务端。

## 6. 适当性、问卷与等级比较

### 6.1 检查项的优先级表

`suitability/build/index.js` 从 URL 双重解码 `check_items`，读取 `login_type`，
把每个条目映射到一张**有序数组** `suitabilityPriority`（导出名
`t.suitabilityPriority`）：

```js
h=[{name:"checkStaticEcontract",render:i.default,need:!1},
   {name:"checkDynamicEcontract",render:c.default,need:!1},
   {name:"checkAML",render:l.default,need:!1},
   {name:"properCheck",render:u.default,need:!1}],
v={5017:{name:"generalAccount",render:p.default}};
h.get=function(e){...}
```

`dynamicEcontractMapping` 只有一项 `5017 -> generalAccount`。条目顺序即页面
推进顺序，`need` 由服务端 `check_items` 的 `item_status` 决定：

```js
t.forEach(function(e){
  ("checkAML"===e.item_name && 1!==+e.item_status ||
   "checkStaticEcontract"===e.item_name && 0===+e.item_status ||
   "checkDynamicEcontract"===e.item_name && 0===+e.item_status ||
   "properCheck"===e.item_name && 1!==+e.item_status)
   && (P.suitabilityPriority.get(e.item_name).need=!0)
})
```

注意两组条目的**判定极性相反**：两个合同项在 `item_status===0` 时需要处理，
而 `checkAML` 与 `properCheck` 在 `item_status!==1`（即非“已完成”）时需要处理。`checkEcontract` 这个合成条目会按
`econtract_nos` 拆成静态/动态两组：命中 `dynamicEcontractMapping` 的编号进
`checkDynamicEcontract`，其余进 `checkStaticEcontract`；当 `login_type==16`、
`properCheck.item_status==1` 且 `item_status_level==0` 时，客户端先取
`getSystemParam(["optionAccount"])`，若拿到 `optionAccount` 就把期权账号
`5018` 追加进该账号的 `econtract_nos`。

### 6.2 未完成项的查询接口

`checkAML` 未完成时调用：

```text
serviceName: bc-bff-server.business.strategyCheck
strategyNo:  gjzq_business_30091
clientId / fundAccount / password / opStation: 占位符 ($…)
```

响应体被 `JSON.parse(e.result)`，外层 `code===0` 时把
`data.strategyResult` 写入 `data.conclusion`，并把 `data.result` 里
`passed` 为假的项回填成 `check_items`。

问卷保存接口为 `questionnaire.result.save`，使用
`functionId=1000`、`subSystemNo=519`、`to_local=1`、`to_counter=0`；
两个内置问卷 ID 是 `20210713001`（模块 `E`）和 `20200727001`。

### 6.3 完成/取消回传

完成或取消通过 native `4009: doneSuitability({type, valid})` 回传：

| 场景 | `valid` |
|---|---:|
| 全部检查完成或用户选择跳过 | `1` |
| 用户取消/放弃（`window.suitability.cancel`） | `0` |

### 6.4 风险测评状态机（`properCheck`）

状态枚举与按钮文案在同一个文件内定义：

```js
I={EXPIRED:0,CORRECT:1,WILL_EXPIRE:2,NON_ESTIMATED:3}
M=["重新测评","测评","重新测评","立即测评"]
```

`riskLevel` 在此文件里**就是上述状态码**，不是风险等级：

- `0` 过期：渲染 `.tips.expired`，文案“根据适当性管理规定，投资者风险承受
  能力测评的结果有效期为两年，您的风险承受能力测评结果**已经过期**”；
- `2` 即将过期：`.tips.will_expire`，同一“有效期为两年”表述，状态词为
  **即将过期**；
- `3` 未测评：`.tips.no_estimate`，“您尚未进行风险承受能力评估”；
- 其它值：`.tips.no_estimate` + “未知的测评状态”，并上报
  `saveLog({event_id:"3005028",eventName:"适当性状态无法识别",riskLevel})`。

自动动态测评的条件是三者同时成立，且状态必须是 `0` 或 `2`：

```js
+is_auto_check_on && +auto_check_data_exist && (0===riskLevel || 2===riskLevel)
```

启用时过期/即将过期页提供两个按钮：“我的个人信息发生变化需要重新测评”
（`redirectToRiskEstimate`）与“我的个人信息无变化请进行动态测评”
（`goAutomatic`）；未启用时只显示 `M[riskLevel]` 对应的单按钮。

### 6.5 签约前的产品/承受等级比较

等级比较不在 `suitability/build/index.js`，而在签约/下单前的确认弹层。
`ai-adviser/optional.js`（`smarttrade/build/vendor.js` 与
`ai-customer-service/business-risk.js` 有同源副本）中：

```js
function mt(e){var t=e.riskLevel,n=e.buyLevel,...;
  ...99==t?"专业投资者":"C".concat(t,"（").concat(ne.kf["C".concat(t)],"）")...
  ..."风险适当性", n>+(t||0)?"不":"", "匹配"...
}
```

即 `buyLevel`（产品风险等级）**大于** `riskLevel`（客户承受等级）时显示
“风险适当性不匹配”。同文件的签约前置校验更进一步：

```js
if(!(u && v && P>+v)){...return!0}   // 不满足则不提示
t="C".concat(v);
... "的风险等级为", m, "（", ne.wA[m], "）",
    "，高于您的风险承受等级", "C"+v, "（", ne.kf[t], "）",
    "，您确认要签约该产品吗？"
    confirmText:"确认签约", cancelText:"取消"
```

**该弹层只是二次确认，不是硬拦截**：确认后继续签约（`z(!0)`），取消则上报
`(0,Je.g)((0,w.Ay)("AI_FOUR_ONE"),{},!0)`。同模块还导出两张等级文案表：`ne.kf` 为客户侧 `C0…C6`（“最低风险承受”…
“暂不启用”）、`ne.wA` 为产品侧 `R0…R6`（“默认等级”…“暂不启用等级”），
另有 `ne.be` 把 `-1`/`0` 归一为“C1（最低风险等级承受）”、`99` 归一为
“专业投资者”。这是**客户端侧的等级比较与二次确认**，最终是否放行仍由
服务端决定。

### 6.6 个人中心展示

`me/build/personalSetting.js` 用另一套文案渲染同一字段：

```js
e = E ? "99"==E ? "专业投资者"
        : "-1"==E ? "C1最低类别"
        : "C".concat(E)
    : "未测评";
```

即 `99`→专业投资者、`-1`→`C1最低类别`、`1..5`→`C{level}`、空→未测评。

### 6.7 小结

客户端侧可验证的风控逻辑到此为止，可归纳为**六类判定点**：

| 类型 | 位置 | 判定依据 |
|---|---|---|
| 适当性状态机 | `suitability/build/index.js` | 服务端 `check_items[].item_status` |
| 等级比较弹层 | `ai-adviser/optional.js` §6.5 | `buyLevel > riskLevel` → 二次确认（非硬拦截） |
| 购前闸门 | `ai-adviser/optional.js` §5.2 | `riskLevel !== 99` 且（过期/未测评/无协议）→ 拦截 |
| 权限可用性 | `vas/build/buy0.js` §5.3 | `availability ∈ {AVAILABLE,COMING}` |
| 深链准入 | `vas/build/commons.js` §5.4 | `is_auth === 1` |
| 登录/交易超时 | `fetchYJBAPI` 状态机 | `loginStatus / tradeOvertime` |

六类全部是**对服务端下发字段的 `===` 比较与跳转**，没有一处计算分数、阈值或
惩罚力度。全库检索 `限制交易` / `冻结` / `黑名单` 三个词：`限制交易` 只出现在
`knowledge/build/transferErrorInfo.js` 的银行转账错误对照表（静态文案）；
`冻结` 只出现在 `floorfundtrading/build/commons.js` 的业务代码表
（`4081 资金冻结` / `4083 证券冻结` 等枚举文案）；`黑名单` 只出现在 §5.4 的
`is_auth` 注释里。三者都没有对应的客户端判定分支。评分公式、阈值下发、冻结/限制等
处罚动作都在服务端。

## 7. 反分析、设备标识与本地信号

### 7.1 `<queries>` 环境探测面

Manifest 的 `<queries>` 声明 Magisk、Frida、Termux、开发工具、防护 SDK、
OAID 提供方和微信等包名；这证明 Android 11+ 包可见性查询能力，不证明每个包
名都有对应处置分支。

### 7.2 OAID 链

`libmsaoaidsec.so` / `libmsaoaidauth.so` 与 `com.mdid.msa`、
`com.samsung.android.deviceidservice`、`com.coolpad.deviceidsupport`、
`com.heytap.openid` 查询项对应，属于 MSA OAID 获取链；是否实际上报及保存期限
未知。

### 7.3 本地信号与弹窗引擎

| 文件 | 表 | 快照 |
|---|---|---|
| `signalData.db` | `SignalDataTable(uid,signalType,stockUinKey,lineType,data,version,saveTime)` | 7 行 |
| `Popup.db` | `taskFlowRuleInfo`、`taskFlowNodeInfo`、`taskTriggerCount`、`taskFlowEvent`、`taskFlowFreqCtrl`、`taskFinishCount` | 均为 0 |

`SignalDataTable` 是信号策略缓存；`Popup.db` 证明任务流/频控引擎存在，但本次
快照没有触发任务。

### 7.4 客户端身份/设备遥测信封

`vas/build/commons.js`（偏移 `0x9ee6`）的 `getBasicLogInfo()` 把两路 native
查询拼成一条埋点信封，凡是要带设备上下文的日志都复用它：

```js
readSystemToMemory({keys:["machine","appVersion","IP","interfaceVersion",
                          "systemName","systemVersion","uid"]})
nonSystemParam({keys:["clientId","passportId","mobileCode","productId","userId"]})
→ { uid, machine, appVersion, ip, interfaceVersion, systemName, systemVersion,
    clientid, pid, userid, productId, time }
```

再交给 `saveLog({event_id, eventName, …})` → `YJBExec(4014, …)`。
即**风控/埋点判断所依据的设备与账号标识，全部来自 native 侧同一组键**；
H5 不自行拼装设备指纹。`event_id` 是可枚举的数字串（如适当性状态异常
`3005028`、YJBAPI 业务码异常 `3005031`），客户端只上报，不据此做本地判定。

另一条设备上下文来自 `readSystemToMemory({keys:["machine"]})` 的 `machine`
与 `IP`——二者的生成逻辑在 native 内（见
[obfuscation.md](obfuscation.md) §5），静态不可见。

## 8. 未闭环项

| 项 | 状态 |
|---|---|
| 服务端评分、阈值、模型特征 | 未知 |
| 冻结、限制、警告等处罚动作 | 未知 |
| 风控参数下发与保存期限 | 未知 |
| `libShanYCore.so` 的 ptrace 反调试**反应** | `ptrace(PTRACE_TRACEME)` 调用已还原且返回值未检查；被调试时的实际反应分支仍未知 |
| `libShanYCore.so` 的 Wi-Fi 反射面**调用时机** | 已还原方法名字符串（`disableNetwork`/`enableNetwork`/`disconnect`/`reconnect`）与受控反射路径；何时实际调用未知 |
| `libencrypt.so` / `libndktest.so` 完整 native 算法调用链 | **已还原**：`obfuscation.md` §7.3–§7.4（四库离线解包、逐函数分析）；SM4 密钥编排与块函数均复现 GB/T 32907-2016 标准向量 |
| `<queries>` 各包名的实际判定分支 | 未知 |
| `getRiskInfo` / `permDesk` / `access.authority` 的服务端判定规则 | 未知（只证实客户端 `===` 判定与跳转） |
| `saveLog(4014)` 的服务端留存与用途 | 未知 |
| `files/i12196010e51071ed` 内部格式 | 未知 |

## 9. 研究边界

未登录、未抓真实响应、未触发风控、未绕过任何检查。报告中的接口、状态机和
字段只描述静态可复现的客户端行为；服务端不可见部分明确保留为未知。
