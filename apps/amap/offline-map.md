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

已验证样本的魔数是 `DICE-AM\0`。页大小为 4096 字节；头偏移 20 的大端
16 位值为 chunk 数；每个 chunk 对应 8192 字节（两个页面），因此：

```text
expected_image_size = chunk_count * 8192
expected_page_count = chunk_count * 2
```

`lite_a0_m1` 的 3090 个压缩块按 ID 排序后得到 25,313,280 字节、6180 页的
镜像。另一个样本 `offlineLiteConfig.db` 的 DICE-AM 头计数为 4，对应 8 页；
`render_gb_v6.ans` 的头计数为 2953，对应 5906 页。

## 已知与未知

| 项目 | 状态 |
| --- | --- |
| 魔数、页大小、chunk 大小、头计数关系 | 已验证 |
| AM-zlib 帧边界、zlib 流、block ID 排序 | 已验证 |
| 由 block ID 重建 DICE-AM 镜像 | 已验证 |
| DICE-AM 页树、溢出页、记录单元编码 | 未完整恢复 |
| 道路拓扑、车道、POI 和路线语义 | 未宣称已解码 |

因此本文描述的是**容器/分页格式**，不是完整地图格式规范。
