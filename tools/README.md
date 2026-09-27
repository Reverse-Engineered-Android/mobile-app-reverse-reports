# 工具

工具只使用 Python 标准库，不包含样本或生产秘密。

| 脚本 | 用途 |
| --- | --- |
| `apk_strings.py` | 在 APK/XAPK 中搜索 DEX/SO 字符串、JNI、SQL 和 key 线索 |
| `sqlite_schema.py` | 只读输出 SQLite DDL、列、对象和聚合行数 |
| `derive_amap_key.py` | 从环境变量中的 passphrase 派生 `bedstone.db` AES-128 key |
| `assemble_xhs_shield.py` | 用合成/私有输入重放小红书 shield 外层 |
| `xor_strings.py` | 恢复单字节 XOR 字符串 |
| `sanitize.py` | 发布前扫描凭据、路径、IP、UUID 和坐标 |

示例：

```bash
python3 tools/apk_strings.py --help
python3 tools/sqlite_schema.py --help
AMAP_BEDSTONE_PASSPHRASE='private-value' python3 tools/derive_amap_key.py --fingerprint
XHS_RC4_KEY='synthetic-value' python3 tools/assemble_xhs_shield.py --app-id 1 --build 4896 --device-id FAKE --token-type 0
python3 tools/sanitize.py .
```
