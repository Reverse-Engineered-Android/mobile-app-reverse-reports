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
| `dice::BindDijstra::SetHead` / `SetTerminal` | 有起点、终点/终端绑定入口 |
| `dice::BindDijstra::GetRoutePath` | 有路径提取入口 |
| `dice::RouteDJHeap::splitBlock` / `minRoadsToBlock` | 有按最小键组织的堆/前沿结构 |
| `dice::NormalSearch::normalDJSearch` / `sideDJSearch` | 有正向/侧向 Dijkstra-family 搜索入口 |
| `dice::MutiThreadEngine::doSearchFromStart` | 有多线程从起点搜索编排 |
| `dice::RouteSearch::specDJStart` | 有专用 DJ 搜索入口 |

## Dijkstra-family 证据边界

静态字符串、符号和反汇编可确认的是 **Dijkstra-family 堆/最小键最短路引擎**：
搜索区存在两个方向的 frontier，反复提取较低 key，节点记录的 key 位于 `+20`，
并伴随链接状态标记和路径扩展。`RouteDJHeap` 附近的 24 字节节点记录和有序
pointer/frontier 数组支持这一分类。该分类不等价于已还原完整实现。

仍不足以证明：

- 是否严格使用标准 Dijkstra、双向 Dijkstra、A* 或其它变体；
- 边代价由距离、时间、收费、实时交通或权重的何种组合计算；
- 候选路线数量、并行道路比较、罚分和排序策略；
- 离线/在线结果如何合并、降级或选择；
- 图节点、页树、溢出单元和道路属性的逐字段编码。

因此可发布结论是“Dijkstra-family 堆/最小键搜索已由静态证据证实”，而不是
“完整路线算法已还原”。边代价、tie-break、候选路线和在线/离线分派仍为边界。

## 已排除的误导证据

字符串 `Graph edges should start with 2 integers and a float` 位于 OpenCV
持久化代码（`icvReadGraph`、`opencv-sequence-tree` 附近），与 `libamaptbt.so`
路线引擎无调用关系；它不是导航图边格式证据，已从结论中移除。

## 可复核的下一步

若继续研究，应在不导出真实地图数据的前提下增加合成图边向量、构造多候选
路线样例，并对 `RouteSearch`/`RouteEngine` 的 cost、tie-break 和 reroute
分支做差分测试。任何新的结论都应附独立证据等级，不能从函数名直接推出
完整算法。
