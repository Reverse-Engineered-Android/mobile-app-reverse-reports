# 工具

Python 工具只使用标准库；Java 助手通过反射调用样本运行库。所有工具均不包含样本或生产秘密。

| 脚本 | 用途 |
| --- | --- |
| `apk_strings.py` | 在 APK/XAPK 中搜索 DEX/SO 字符串、JNI、SQL 和 key 线索 |
| `sqlite_schema.py` | 只读输出 SQLite DDL、列、对象和聚合行数 |
| `derive_amap_key.py` | 从环境变量中的 passphrase 派生 `bedstone.db` AES-128 key |
| `apps/amap/tools/amzlib_inspect.py` | 只读检查 AM-zlib 帧、块 ID、zlib 块和 DICE-AM 重建元数据 |
| `apps/amap/tools/dice_container_inspect.py` | 只读检查 DICE-AM 页对、32 位块计数、类型 `0x05` 目录 cell 边界和 prefix 排序 |
| `apps/pinduoduo/tools/obfscan.py` | 逐库扫描 ELF 形态、密码学常量表、字符串与 JNI 导出 |
| `apps/pinduoduo/tools/obfclass.py` | 从 `.text` 反汇编统计 `indbr`/`adrret`/`cffstate`/`.inst%` 指纹并分类 |
| `apps/pinduoduo/tools/unflatten.py` | 把 OLLVM 控制流平坦化状态机还原为基本块图 |
| `apps/pinduoduo/tools/cff4.py` | 按真实函数收尾定界，输出逐 JNI 导出的规模 |
| `apps/pinduoduo/tools/native_closure2.py` | 把 DEX 声明的 native 方法映射到提供它的库导出符号（含 `_1/_2/_3` 转义） |
| `apps/pinduoduo/tools/jnibind.py` | 判定绑定方式：`Java_*` 导出 / `RegisterNatives` / 未判定 |
| `apps/pinduoduo/tools/so_manifest2.py` | 交叉比对 `SoBuildInfo` 清单、设备落盘与 DEX 加载点 |
| `apps/pinduoduo/tools/db_snapshot.py` | 只读 SQLite 快照（含 WAL 重放），只输出列形状聚合而不输出行值 |
| `assemble_xhs_shield.py` | 用合成/私有输入重放小红书 shield 外层 |
| `xor_strings.py` | 恢复单字节 XOR 字符串 |
| `sanitize.py` | 发布前扫描凭据、路径、IP、UUID 和坐标 |
| `wcdb-probe.java` | 在 Android/ART 中通过微信 WCDB 只读验证候选输入，并可输出脱敏表计数/列类型/FTS 词项统计，不输出 key 或内容；拒绝空/缺失/零长度文件及空 schema/零页伪命中，`WDB_COPY_BEFORE_OPEN=1` 可为每次尝试复制隔离输入 |

示例：

```bash
python3 tools/apk_strings.py --help
python3 tools/sqlite_schema.py --help
AMAP_BEDSTONE_PASSPHRASE='private-value' python3 tools/derive_amap_key.py --fingerprint
python3 apps/amap/tools/amzlib_inspect.py --help
python3 apps/amap/tools/dice_container_inspect.py --help
XHS_RC4_KEY='synthetic-value' python3 tools/assemble_xhs_shield.py --app-id 1 --build 4896 --device-id FAKE --token-type 0
python3 apps/pinduoduo/tools/obfscan.py --help
python3 apps/pinduoduo/tools/db_snapshot.py <src-dir> <out.txt>
python3 tools/sanitize.py .
javac -d /tmp/wcdb-classes tools/wcdb-probe.java
```
