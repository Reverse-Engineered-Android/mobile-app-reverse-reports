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

## 5. 风险关注页面

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

客户端侧可验证的风控逻辑到此为止：**状态机 + 优先级推进 + 等级比较弹层 +
问卷/AML 查询**。评分公式、阈值下发、冻结/限制等处罚动作都在服务端，未在
客户端代码中出现判定分支。

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

## 8. 未闭环项

| 项 | 状态 |
|---|---|
| 服务端评分、阈值、模型特征 | 未知 |
| 冻结、限制、警告等处罚动作 | 未知 |
| 风控参数下发与保存期限 | 未知 |
| `libShanYCore.so` 的 ptrace 反调试反应 | 只证实导入，未还原分支 |
| `libencrypt.so` / `libndktest.so` 完整 native 算法调用链 | 符号已恢复，方法体受保护 |
| `<queries>` 各包名的实际判定分支 | 未知 |
| `files/i12196010e51071ed` 内部格式 | 未知 |

## 9. 研究边界

未登录、未抓真实响应、未触发风控、未绕过任何检查。报告中的接口、状态机和
字段只描述静态可复现的客户端行为；服务端不可见部分明确保留为未知。
