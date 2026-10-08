# 逆向证据

## 1. 样本指纹

| 项目 | 结果 |
| --- | --- |
| 包名 | `tv.danmaku.bili` |
| versionName / versionCode | `9.13.0` / `9130500` |
| APK 大小 | `218,480,895` 字节 |
| SHA-256 | `d8e74cf3ce4e4332af75920035590979e4347938c65a6d2fc73627b0c132d98e` |
| DEX | 32 个；逐 DEX 反编译 156,568 个 Java 文件 |
| arm64 `.so` | 93 个 |
| 设备 | Android 16，设备端 APK SHA-256 与本地一致 |

## 2. 工具链

| 阶段 | 工具/证据 |
| --- | --- |
| Manifest | JADX AXML 解码，权限与 exported 组件计数 |
| DEX | JADX 1.5.x 逐 `classes*.dex` 反编译，`--show-bad-code`；失败日志逐个归因 |
| 字符串 | DEX 字符串全量扫描（1,673,330 行） |
| ELF | `readelf`、`llvm-objdump -d`，`libbili.dis` 189,360 行 |
| 字符串解密 | `decode_libbili.py`，45 个 `.datadiv_decode`、513 个 ASCII 明文 |
| JNI | `emulate_jni.py`，11 个方法注册、appkey 19 项由受控模拟验证 |
| 签名 | `SignedQuery.r` Java 语义 + native `0xffac/0xffc0` 反汇编交叉验证 |
| 风控 | `gripper-fieldmap.json` 121 项、36 collector 源文件、`dd.json` 3,085 节点 |
| 数据库 | Python `sqlite3` 只读 URI、`PRAGMA table_info`、完整性/表数聚合 |
| 发布 | `tools/sanitize.py`、相对链接检查、人工敏感模式扫描 |

## 3. 证据等级

### 3.1 已验证

- APK 哈希、大小、版本、DEX/native 数量；
- `SignedQuery.r` 的排序、百分号编码和 `&sign=` 追加；
- native appkey 表和 JNI 注册地址；
- `libbili.so` `.datadiv_decode` 明文、MD5 初始化/更新地址；
- Gripper 外层 JSON、121 个字段短码、36 个 collector 注册；
- 设备端 SQLite 表名、列名、DDL 和聚合数量。

### 3.2 结构已证实

- gRPC message/service 的字段结构；
- GAIA 混合加密的 AES key 长度、RSA 公钥用途和输出 pair；
- `dd.json` 的规则节点 schema、布尔运算和属性集合；
- UPOS 分片上传、离线下载、播放/DRM 的客户端调用链。

### 3.3 不能由本样本证明

- 服务端签名校验、access_key 绑定、风险评分和处罚阈值；
- 某一标题在当前时间点的 DRM 状态、许可证有效期或服务端授权范围；
- 用户未见告知时是否实际上传某个字段；
- 远端配置的实时值、服务端留存期限和第三方 key scope。

## 4. 脱敏边界

报告不发布 APK/DEX/SO/数据库文件、完整 RSA PEM、第三方 App Secret、native 私有
常量、真实 Token/Cookie、IP、账号、设备 ID、媒体 URL、数据库行值、真实搜索词或
反汇编全量文件。允许公开的只有版本/哈希、符号/函数地址、算法结构、合成公式、
表结构和聚合数量。
