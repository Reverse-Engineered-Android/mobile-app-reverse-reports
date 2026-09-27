# 高德地图本地数据逆向报告

## 结论摘要

| 主题 | 结论 | 证据强度 |
| --- | --- | --- |
| 本地 SQLite | `bedstone.db`、`girf_sync.db` 可由应用自己的 native 路径读取，完整性检查通过 | 已验证 |
| 内容边界 | 可列出表结构、对象类别和聚合计数；真实行值全部排除 | 已验证 |
| 离线地图 | `AM-zlib` 外层是按块 ID 存放的 zlib 帧，块 ID 排序可重建 `DICE-AM` 页面镜像 | 已验证 |
| `DICE-AM` | 已确认魔数、4096 字节页、8192 字节逻辑块和头字段关系；页面树/记录单元语义未完整恢复 | 结构已证实 |
| 在线导航 | 已确认导航 allowlist、POST 字节数组接口和请求模型字段；未公开真实 body | 已验证 |
| 导航算法 | 已确认离线路线请求、图边校验、端点/途经点/重规划入口；未证明具体最短路算法和代价函数 | 证据边界 |

## 样本与方法

研究对象为授权设备上的 `com.autonavi.minimap` `base.apk` 及本地数据库副本。
APK 的 SHA-256、大小和版本见 [README](README.md)。分析按以下顺序进行：

1. 用 `jadx` 解码 manifest/DEX，定位 JNI、数据库和导航模型。
2. 对 ARM64 SO 做符号、字符串和调用点检查，确认 key 安装 ABI。
3. 用只读 SQLite 检查对象、DDL、完整性和聚合计数。
4. 对离线容器做魔数、帧边界、压缩块、页面关系和重建镜像检查。
5. 从 DEX/native 接口梳理在线导航请求字段，并把已验证事实与假说分开。

不包含的材料包括 APK、SO、DEX 全量、数据库、离线地图、重建镜像、反汇编全量、
真实请求/响应、密钥、账号/设备标识和用户行为时间线。

## 本地数据库

### `bedstone.db`

- native 字符串包含 `bedstone.db`、`fLocationInfo` 建表/插入 SQL 和
  `girf_sqlite3_key` 导入。
- `libamapbadge.so` 的调用点先对 key 字符串执行 `strlen`，再把指针、长度和
  数据库句柄交给 `girf_sqlite3_key`。
- 通过应用的 key 安装路径打开副本后，`PRAGMA integrity_check` 为 `ok`。
- 当前样本 `fLocationInfo` 为 **35** 行；表列和 DDL 见 [schema.md](schema.md)。
- passphrase、AES key、任何行值均不公开，也不提供脱库脚本。

### `girf_sync.db`

- `libamapsync.so` 的 key 构造函数先在 native 内构造 16 字节摘要，再以 32 位
  十六进制文本传给数据库 key 安装函数；动态探测通过应用实现成功打开副本。
- 动态探测只报告“成功/失败”和结构检查结果，不打印 key；独立候选公式未可靠
  打开数据库，因此不宣称存在可复用的独立公式。
- 库有 18 个表、40 个索引，完整性检查通过。聚合结果见
  [content.md](content.md)。
- 表名中的账户维度后缀统一替换为 `<user>`；真实用户、城市、路线和搜索内容
  不公开。

### `ackor_offline_compile.db`

- 明文 SQLite，完整性检查通过。
- `ackor_offline` 有 4 行，类型类别为 `map`/`route`，状态字段为 `7`；
  每行的下载大小与数据大小相符。只发布对象结构，不发布 `data_content`、
  城市标识或行值。

## 离线地图

已验证 `lite_a0_m1.ans` 的 `AM-zlib\0` 外层：记录由 4 字节大端 block ID、
2 字节大端压缩长度和标准 zlib 流组成；解压块固定为 8192 字节。block ID 是
`2,4,...,6180` 的完整偶数序列，但文件顺序未排序，按 ID 排序后可得到
25,313,280 字节的 `DICE-AM` 镜像。`DICE-AM\0` 的页大小为 4096 字节，
头中 16 位块计数与 `块数 × 8192 = 镜像大小` 关系已核对。

这支持离线地图的封装和可重建性，不等于已经恢复道路拓扑、页树、溢出单元或
完整记录语义。详见 [offline-map.md](offline-map.md)。

## 在线导航

Java 层确认了导航相关 endpoint allowlist 和 native HTTP 回调接口：
POST 回调接收 `byte[]` opaque body。`POIForRequest`、`POIInfo`、`RouteOption`
和 `RerouteOption` 暴露请求/重规划模型字段。endpoint、字段分组、路线类型和
重规划原因见 [network-navigation.md](network-navigation.md)。

## 导航算法边界

native 符号和字符串确认了离线路线执行、图边输入校验、端点/途经点绑定和
重规划计划入口。现有证据支持“图边遍历 + 链接匹配 + 途经/旅程点绑定 +
重规划候选”的概念模型，但不足以证明 Dijkstra/A*、精确边代价、候选路线生成
或在线/离线分派细节。详见 [algorithm.md](algorithm.md)。

## 脱敏与发布边界

公开报告只使用聚合数字、列名、公开 endpoint、必要函数地址/片段和结构化容器
字段。任何数据库行、真实 POI/路线/搜索、地址/坐标、账号/设备标识、密钥、
token、cookie、绝对路径、内网地址、真实请求/响应和逐步恢复操作都不进入仓库。
