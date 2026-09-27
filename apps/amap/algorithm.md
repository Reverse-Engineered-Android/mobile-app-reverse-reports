# 导航算法边界

## 已验证的概念模型

从 native 符号、字符串和 DEX 模型可确认的最小闭环是：

```text
离线地图块
  -> 图边记录（输入校验）
  -> 链接/端点匹配
  -> 途经点/旅程点绑定
  -> 路线计算或候选路线
  -> 重规划状态与原因
```

支持性证据包括：

| 符号/字符串 | 支持的事实 |
| --- | --- |
| `dice::OfflineRouteRequest::offLineRouteExcute` | 存在独立离线路线执行入口 |
| `dice::OfflineRouteRequest::doRequestRoute` | 存在离线路线请求流程 |
| `dice::OfflineRouteRequest::calcRerouteNum` | 有重规划数量/触发计算 |
| `dice::RouteSearch::startLinkMeetEndLink` | 有起止链接匹配/相交判断 |
| `dice::RouteEngine::bindViaPoint` | 有途经点绑定 |
| `dice::RouteEngine::bindJourneyPoint` | 有旅程点绑定 |
| `dice::RouteEngine::getReroutePlan` | 有重规划计划生成入口 |
| `Graph edges should start with 2 integers and a float` | 图边输入至少有整数端点/属性和浮点字段 |

## 尚未证明的内容

现有样本证据**不足以**证明：

- 使用 Dijkstra、A*、双向搜索或其它具体最短路算法；
- 边代价由距离、时间、收费、实时交通或权重的何种组合计算；
- 候选路线数量、并行道路比较、罚分和排序策略；
- 离线/在线结果如何合并、降级或选择；
- 图节点、页树、溢出单元和道路属性的逐字段编码。

因此不要把这些未证实内容写成“算法已还原”。公开报告只保留可由符号、模型
字段和结构检查直接支持的事实，并把算法实现细节标为待验证。

## 可复核的下一步

若继续研究，应在不导出真实地图数据的前提下增加合成图边向量、构造多候选
路线样例，并对 `RouteSearch`/`RouteEngine` 的 cost、tie-break 和 reroute
分支做差分测试。任何新的结论都应附独立证据等级，不能从函数名直接推出
完整算法。
