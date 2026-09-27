# 离线导航地图格式

## `AM-zlib` 外层

`lite_a0_m1.ans` 的文件开头是 `AM-zlib\0`。已验证的记录封装如下：

```text
record :=
  block_id       uint32_be
  compressed_len uint16_be
  zlib_stream    [compressed_len bytes]
```

解析器从头字段给出的首记录偏移开始，连续读取到记录结束偏移。每条记录解压
后固定为 8192 字节。样本的 block ID 是 `2,4,...,6180` 的完整偶数序列，
但物理顺序不等于 block ID 顺序；按 block ID 排序后写入即可得到 DICE-AM
镜像。文件尾部的剩余字节是零填充。

工具 [amzlib_inspect.py](tools/amzlib_inspect.py) 只校验上述边界、块大小和
摘要，不输出解压后的道路/POI/坐标内容。

## `DICE-AM` 封装

已验证样本的魔数是 `DICE-AM\0`。页大小为 4096 字节；每个逻辑 chunk 对应
8192 字节（两个页面）。**chunk 数是头偏移 18..21 的大端 32 位字段**，不是
旧报告中写的 20..21 的 16 位字段。样本还使用了固定 XOR 头字段：偏移 8、9、
10 分别以 `0xab`、`0x01`、`0x89` 混淆，偏移 26..27 的 16 位值以 `0xdefe`
混淆后得到逻辑 chunk 大小 `0x2000`（8192）。

```text
chunk_count       = uint32_be(header[18:22])
decoded_chunk_size = uint16_be(header[26:28]) XOR 0xdefe
expected_image_size = chunk_count * decoded_chunk_size
expected_page_count = expected_image_size / 4096
```

`lite_a0_m1` 的 3090 个压缩块按 ID 排序后得到 25,313,280 字节、6180 页的
镜像。另一个样本 `offlineLiteConfig.db` 的 DICE-AM 头计数为 4，对应 8 页；
`render_gb_v6.ans` 的头计数为 2953，对应 5906 页。

## 页对和目录单元

容器把 8192 字节逻辑块视为一个页对；第 0 页对是特殊元数据页对。已观察到的
类型 `0x05` 目录页满足以下结构：

```text
directory_page[0]  = 0x05
directory_page[4]   = record_count (uint8)
directory_page[12:12 + record_count * 2] =
    big-endian uint16 offsets, relative to the 8192-byte page pair

cell = pair[offset : offset + 12]
cell[0:4]   = value/id
cell[4:12]  = encoded key/prefix
```

单元位于配对 payload 页的尾部；目录表的遍历顺序按 `cell[4:12]` 的 8 字节
prefix 排序，物理 cell 顺序不保证排序。工具把偶数页上的类型 `0x05` 作为候选
目录页，并检查 offset/span 边界、单元是否重叠和 prefix 排序；不输出 key、
value 或道路/POI 内容。类型 `0x0d` 页使用不同的布局，不能套用 `0x05` 规则。

## 已知与未知

| 项目 | 状态 |
| --- | --- |
| 魔数、页大小、chunk 大小、32 位头计数关系 | 已验证 |
| AM-zlib 帧边界、zlib 流、block ID 排序 | 已验证 |
| 由 block ID 重建 DICE-AM 镜像 | 已验证 |
| 类型 `0x05` 页对目录、offset/span 和 8 字节 prefix 排序 | 已验证 |
| 类型 `0x0d` 页、溢出页和完整记录单元编码 | 未完整恢复 |
| 道路拓扑、车道、POI 和路线语义 | 未宣称已解码 |

因此本文描述的是**容器/分页格式**，不是完整地图格式规范。
