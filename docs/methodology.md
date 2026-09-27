# 逆向方法

## 通用流程

1. 记录包名、versionName、versionCode、SDK 范围、文件大小和 SHA-256。
2. 从 APK/XAPK ZIP 目录读取 manifest、`classes*.dex`、`lib/**/*.so` 和配置文件，不盲目解包全部资产。
3. 扫描 Java 类名、JNI 声明、SQL、数据库名、key 长度调用和字符串解密入口。
4. 解析 ELF 符号、导入、重定位和 `.rodata`，再用 ARM64 反汇编确认参数寄存器与调用链。
5. 对混淆函数使用受控模拟或确定性算法重放；真实账号、设备和网络数据留在私有环境。
6. 对数据库副本使用只读 URI，先 `integrity_check`，再枚举对象、列、DDL 和聚合数量。
7. 输出报告前执行凭据、路径、IP、UUID、坐标和数据库行扫描。

## 工具链

| 阶段 | 工具/方法 |
| --- | --- |
| Manifest | `jadx` 解码 AndroidManifest，或等价 AXML 解析 |
| DEX | `jadx`、字符串/类名扫描 |
| ELF | `readelf`、`pyelftools`、Capstone/AArch64 objdump |
| 混淆 | 字符串解密、重定位恢复、CFF/opcode 图、Unicorn 受控模拟 |
| 数据库 | Python `sqlite3` 只读 URI、`PRAGMA` |
| 发布 | Markdown 链接检查、敏感模式扫描、人工复核 |

## 证据等级

- **已验证**：可由符号、反汇编、测试向量或只读查询重复得到。
- **结构已证实**：ABI、调用顺序或输入输出形状确定，但内部算法仍有未知。
- **假说**：只有字符串、邻接关系或行为证据，正文必须显式标注。
