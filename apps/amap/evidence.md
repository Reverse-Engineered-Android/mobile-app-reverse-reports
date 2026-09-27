# 高德逆向证据

## 样本与完整性

| 材料 | SHA-256/检查 |
| --- | --- |
| `base.apk` | `475ef983285559042ff194029a78ca3a4c79b537c8c052fc121ee796dbbd8e8a` |
| `bedstone.db` | `dbe212368a99e69688312090e1bfa6345b6c8dc24d95a5ccce0a6a71dcdb41ac` |
| `girf_sync.db` | `386294b7a602237fdf8886cca06a5d04a1e0fd591066da7713dd02884e90ea81` |
| `ackor_offline_compile.db` | `e0310ca9b9fc7607bfec344e746ad8cdef0d15f7bdbb979dafa88f88d31b404d` |

数据库哈希仅用于快照完整性，不作为内容标识。数据库行和账户后缀均未公开。

## DEX/JNI 线索

`classes5.dex`/`classes6.dex` 的可靠符号和字符串包括：

```text
com.autonavi.jni.bedstone.BaseMapFrequentLocationsJni
com.autonavi.sync.GirfSyncJni
com.autonavi.sync.GirfSyncServiceJni
girf_sync.db
initDb
initDecrypt
```

## Native 数据库入口

| 文件/偏移 | 证据 |
| --- | --- |
| `libamapbadge.so:0x3e1b5` | `fLocationInfo` 建表 SQL |
| `libamapbadge.so:0x3a59b` | `insert into fLocationInfo (...)` SQL |
| `libamapbadge.so:0x3bb6a` | `bedstone.db` 字符串 |
| `libamapbadge.so:0x87980` | `strlen` 后调用 `girf_sqlite3_key(db, key, key_len)` |
| `libamaprsq.so:0x3c694` | 旧式 key ABI 到内部安装函数的适配 |
| `libamapsync.so:0x2eb34` | 构造 key 缓冲并在 `0x2ebc4` 调用 `girf_sqlite3_key` |
| `libamapsync.so:0x4aebc` | 计算 16 字节摘要 |
| `libamapsync.so:0x4ab70` | 将 16 字节格式化为 32 位十六进制文本 |

`girf_sync.db` 的动态 key 探测通过应用实现成功打开副本，但探测不打印 key；
不公开任何 passphrase、AES key、摘要或独立候选公式。完整动态过程也不进入公开仓库。

## 离线容器证据

`lite_a0_m1.ans`：

- SHA-256 `291e698cf915c55c7fb3eb78b6b7917538d379736aa8fe037c0015797c440cda`；
- 魔数 `AM-zlib\0`，文件大小 `11,853,824`；
- 记录计数头/解析均为 `3090`，首记录偏移 `26633`，记录结束 `11850745`；
- 末尾零填充 `3079` 字节；
- uncompressed chunk size 字段在偏移 **172**，值为 `8192`；
- 帧格式为 4 字节大端 block ID、2 字节大端压缩长度、标准 zlib 流；
- block ID 是 `2..6180` 的完整偶数序列，文件顺序不保证按 ID 排序；
- 按 block ID 排序后重建 `DICE-AM` 镜像，大小 `25,313,280`。
- 重建镜像 SHA-256 为
  `28bb3dd5d2ac1299f774e68c25f31a816012145353811492a7965eac48a76328`。

`DICE-AM\0` 证据：

- 页大小 4096 字节；
- 头部偏移 **18..21** 为大端 32 位 chunk 数；
- 头部偏移 26..27 的 16 位值以 `0xdefe` XOR 解码为逻辑 chunk 大小
  `0x2000`；偏移 8、9、10 分别使用 `0xab`、`0x01`、`0x89` XOR；
- `chunk 数 × 8192` 与镜像大小一致。
- `offlineLiteConfig.db`：4 chunks/8 pages，SHA-256
  `faa0700689132375c3a94823a167c912b99645dccdb703fa2a09ba9fb3669097`；
- `render_gb_v6.ans`：2953 chunks/5906 pages，SHA-256
  `42963b3b8c88779c753f44f04d5dfe9521e2bd82ab4c3c016ada6fe8d519e522`。
- 每个 8192 字节逻辑 chunk 是一个 4096 字节页对；页 0 页对是特殊元数据。
- 偶数页类型 `0x05` 候选目录页的字节 4 是 8 位记录数，字节 12 起是相对页对的 16 位 offset
  表；offset 指向 12 字节 cell，cell 为 4 字节 value/id 加 8 字节
  encoded key/prefix（`cell[4:12]`）。
- 目录遍历顺序按 `cell[4:12]` 的 8 字节 prefix 排序，物理 cell 顺序不保证排序；
  工具验证 offset/span 边界、单元重叠和 prefix 排序，不输出 cell 内容。
  类型 `0x0d` 页不套用此规则。

重建和结构解析脚本只输出元数据/摘要，不输出解压后的记录内容：
[amzlib_inspect.py](tools/amzlib_inspect.py)、
[dice_container_inspect.py](tools/dice_container_inspect.py)。

## 在线导航接口

`com.autonavi.core.network.util.CoreInterface` 的 allowlist 中与导航相关的
路径包括：

```text
/ws/transfer/navigation/auto
/ws/transfer/navigation/routeguide
/ws/navigation/dynamic/data
/ws/shield/ride/navigation
/ws/shield/walkcloud/navigation
/ws/shield/truck/route
```

`com.autonavi.jni.ae.route.observer.HttpInterface` 的
`requestHttpPost(int, int, String, byte[])` 证明 native 路线请求通过
POST 字节数组传递；字节编码不是公开 JSON/protobuf schema，本报告不还原真实 body。

## 导航模型入口

已确认的 DEX 模型字段见 [network-navigation.md](network-navigation.md)。
native 符号/字符串包括：

```text
dice::OfflineRouteRequest::offLineRouteExcute
dice::OfflineRouteRequest::doRequestRoute
dice::OfflineRouteRequest::calcRerouteNum
dice::BindDijstra::SetHead
dice::BindDijstra::SetTerminal
dice::BindDijstra::GetRoutePath
dice::RouteDJHeap::splitBlock
dice::RouteDJHeap::minRoadsToBlock
dice::NormalSearch::normalDJSearch
dice::NormalSearch::sideDJSearch
dice::MutiThreadEngine::doSearchFromStart
dice::RouteSearch::specDJStart
dice::RouteSearch::startLinkMeetEndLink
dice::RouteEngine::bindViaPoint
dice::RouteEngine::bindJourneyPoint
dice::RouteEngine::getReroutePlan
```

这些证据支持 Dijkstra-family 的最小键/堆式最短路搜索、方向性前沿、终端/链接
绑定、途经/旅程点绑定和重规划入口。静态反汇编邻近 `0xa2c080` 可见双向前沿、
重复最小键提取、节点键偏移 `+20` 和链接状态标记；`RouteDJHeap` 结构区
`0xa2f72c`–`0xa2fd94` 使用 24 字节节点记录、`+20` 键和有序前沿队列。它们
**不**证明精确边代价、罚分、并行候选数量或在线/离线分派细节。

旧报告中的 `Graph edges should start with 2 integers and a float` 是 OpenCV
持久化代码（`icvReadGraph`/`opencv-sequence-tree` 附近），不是导航算法证据，
已从结论中移除。

## 复核方法

1. 以只读方式枚举 SQLite 对象、DDL、行数和完整性。
2. 用 `amzlib_inspect.py` 校验魔数、帧边界、压缩长度、块 ID 和 chunk 大小。
3. 用 `dice_container_inspect.py` 校验页大小、头计数和镜像尺寸关系。
4. 用 DEX/native 字符串和符号交叉确认接口边界。
5. 发布前运行 `tools/sanitize.py .` 并人工检查链接和脱敏边界。
