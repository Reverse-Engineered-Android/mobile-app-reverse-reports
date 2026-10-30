# 风控机制

## 1. 结论先行

**本报告未还原任何风控判定逻辑。** 已确认的只有「风控相关的代码/资源存在」
这一层结构事实：加固器、反调试库、埋点加密、`<queries>` 反分析包名清单、
风险中心页面、适当性接口、信号数据本地库。以下每一项都标注了证据层级。

## 2. 加固与反调试

### 2.1 爱加密加固（结构已证实）

| 文件 | 大小 | 证据 |
|---|---|---|
| `assets/ijiami.dat` | 14,330,583 B | 头部 `04 00 00 00` + LE 长度 + 32 B hex 串 |
| `assets/ijiami.ajm` | 7,514,954 B | 同上魔数 |
| `files/i12196010e51071ed` | 69,114,092 B | 与 `ijiami.dat` 前 40 B 一致，熵 `4.646`，`b2` 占 `32.9%` |
| `assets/ijm_lib/<abi>/libexec{,main}.so` | — | `S.c()` 释放到 `files/` 后 `System.load` |

壳的 `S.attachBaseContext` → `S.c(abi)` → `N.<clinit>` → `System.load` 链条
已在 [evidence.md](evidence.md) §1 逐帧还原。`i12196010e51071ed` 无
`dex`/`ZIP`/ELF/gzip 魔数，**内部格式未复原**。

### 2.2 `libShanYCore.so`（结构已证实，未反汇编）

`JNI_OnLoad` + `ptrace` 反调试。库体含 `UPX!` 标记，但 `upx -d` 报
`NotPackedException`；`.text` 熵 `7.10`，`readelf` 解出的符号 vaddr 落在高熵区。
**未能反汇编，不声称已还原逻辑。**

### 2.3 `libencrypt.so`（结构已证实，未反汇编）

导出 `Java_com_tztEncrypt_{en,de,ajax,webdata}Encrypt13`，符号含 CRC32 表、
`tabKey`、`srand`。`.text` 熵 `7.72`，同上**未能反汇编**。

### 2.4 `libhtsfx.so` / `libbronlinetracking.so`（结构已证实，未反汇编）

同样含 `UPX!` 标记 + 高熵 `.text`。`libhtsfx.so` 有 153 个 `x.NN` / `y.NN`
BSS 槽位，**用途未知**。

## 3. `<queries>` 反分析包名清单

`AndroidManifest.xml` 的 `<queries>` 声明以下包名，用于 Android 11+ 包可见性：

| 类别 | 包名 |
|---|---|
| Root/Root 管理 | `com.topjohnwu.magisk`、`com.topjohnwu.magiskmanager` |
| Hook 框架 | `com.android.frida` |
| 终端模拟器 | `com.termux` |
| 开发工具 | `com.android.creator` |
| 安全/防护 SDK | `com.zhuoyi.security.lite`、`com.freeme.secureguard`、`com.android.secureguard` |
| 设备指纹 | `com.mdid.msa`、`com.samsung.android.deviceidservice`、`com.coolpad.deviceidsupport`、`com.heytap.openid`、`com.huawei.works` |
| 第三方应用 | `com.tencent.mm` |

另外 3 个 `<intent>` 查询项（无显式包名）。这份清单**证明存在环境探测能力**，
不证明具体判定逻辑或处置动作。

## 4. OAID / 设备指纹链

`lib/arm64-v8a/libmsaoaidsec.so` 与 `libmsaoaidauth.so` 是移动安全联盟
（MSA）OAID SDK 的安全库。`<queries>` 中的 `com.mdid.msa` /
`com.samsung.android.deviceidservice` / `com.coolpad.deviceidsupport` /
`com.heytap.openid` 与之对应——这是标准 OAID 获取链，用于跨应用标识符。
详见 [privacy.md](privacy.md) §3。

## 5. 风险中心页面

H5 模块 `trans` 含两个风险中心入口：

| 文件 | 用途 |
|---|---|
| `trans/build/riskcenter_hold.js` | 持仓风险中心 |
| `trans/build/riskcenter_financing.js` | 融资融券风险中心 |

配套资源 `riskcenter_bg.svg`、`riskcenter_icon_dark.svg`。这些是**展示层**，
风险评分、阈值与触发规则均在服务端。

## 6. 适当性 / 金融消费者保护

| 功能号 | 名称 | 说明 |
|---|---|---|
| `4009` | `doneSuitability` | 完成适当性评估 |
| `4017` | — | 适当性相关（H5 侧调用） |
| `3002` | `openAccount` | 开户入口 |

`suitability/build/index.js` 是适当性评估页面。评估结果的判定与保存在服务端。

## 7. 本地信号数据

| 文件 | 表 | 行数 |
|---|---|---|
| `signalData.db` | `SignalDataTable` | 7 |
| `Popup.db` | `taskFlowRuleInfo` / `taskFlowNodeInfo` / `taskTriggerCount` / `taskFlowEvent` / `taskFlowFreqCtrl` / `taskFinishCount` | 0（均空） |

`SignalDataTable` 的 schema 为
`(uid, signalType, stockUinKey, lineType, data, version, saveTime)`，
复合主键 `(uid, signalType, stockUinKey, lineType)`。这是**信号策略的本地缓存**，
7 行内容表明设备上确有策略数据落盘。`Popup.db` 的任务流表当前全空，
说明弹窗任务流引擎存在但本次快照未触发。

## 8. 未闭环项

| 项 | 状态 |
|---|---|
| 风控评分算法 | 未知（服务端） |
| 处置动作（冻结/限制/警告） | 未知（服务端） |
| 风控参数下发与阈值 | 未知（服务端） |
| `libShanYCore` ptrace 反调试的具体反应 | 未知（`.text` 高熵） |
| `libencrypt` 加密算法与用途 | 未知（`.text` 高熵） |
| `<queries>` 清单的实际判定分支 | 未知（native 侧） |
| `i12196010e51071ed` 内部格式 | 未知（无魔数） |

## 9. 边界

未触发风控、未抓真实响应、未反编译高熵 native。报告中「结构已证实」= 符号/资源/
调用点存在且可读；「未知」= 需要运行时或服务端信息才能闭环。
