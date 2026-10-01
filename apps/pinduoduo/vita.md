# 动态组件框架（Vita / Volantis）

Vita（内部代号 Volantis）是拼多多的**动态组件分发框架**：它把 Dex、So、Lego 模板、
JS/Lua 资源、字体、模型、Web 资源等按"组件（component）"粒度从服务端下发到应用私有
目录，并按需装载。本文件覆盖它的落盘格式、组件清单格式、本地登记表、网络协议、
完整性/加密链，以及与 `SoBuildInfo` 库清单的关系。

**取证强度**：本文件的落盘结论全部来自**只读取证**（`ls`/`find`/`cat`/`md5sum`/
`base64` 复制后离线解析）。**没有**注入、调试、内存读取或任何会被运行时检测的操作。
组件二进制**没有被复制出设备**：校验一律在设备上以 `md5sum`/`sha256sum` 完成，只把
**哈希与元数据**带回。`.md5checker` 声明的 146 条记录中 **134 条**在设备上逐字节
核对通过（§10）。

## 1. 结论速览

1. Vita 有**两套落盘目录**，是同一批产物的两种视图：`files/.vita/<组件>/<版本>/`
   与 `files/dynamic_so/<库名>_<毫秒>_<MD5>/`。So 类组件的**载荷**只落在
   `dynamic_so`，`.vita` 里只保留 `manifest` + `md5checker`；另有 10 个组件两处都有
   同一份 So 的独立副本（不同 inode，非硬链接）。
2. 组件清单是**三个文件**：`PDD_MANIFEST`（保留清单）、`<组件>.md5checker`
   （JSON `{length, md5}` 全文件表）、`extra_info.json`（`{uuid, md5, virtualVersion}`）。
3. `files/.newLocker/*.vlock` 的文件名是 **`MD5(组件ID)`**，可选 `-patch` 后缀与
   版本后缀。这给出**完整组件注册表 126 个**，其中**只有 46 个已安装**，
   **80 个已注册但从未下载**。
4. 本地"已安装组件"登记表**不在 SQLite 里**，而在 MMKV 存储
   `files/mmkv/vita_local_comp_v2`（46 条 `LocalComponentInfo` JSON）。SQLite
   `vita-database` 只存 Uri 映射、访问统计与版本流水（§9）。
5. 网络协议共 7 个端点，其中 6 个走 `/api/app/v1/component/*` 与
   `/api/one-gateway-client/zone/v1/component/*`；载荷 CDN 走
   `/volantis3-open[/aes]/component/...`。
6. 完整性链是 **MD5（清单）+ SHA256WithRSA（组件签名）+ 可选 AES/CBC 解密**
   （`security_level ∈ {1,2}`）。全部为可静态还原的公开算法，无自定义密码学。
7. `SoBuildInfo` 清单里此前列为"本设备从未下载"的 **54 个库**，正是 Vita 注册表里
   已注册未下载的组件——**不是缺失，而是按需下发**（§6）。

## 2. 两套落盘目录

### 2.1 `files/.vita/<组件ID>/<版本>/`

顶层 46 个组件目录，合计约 34.6 MB。布局：

| 文件 | 说明 |
| --- | --- |
| `<组件ID>.manifest` | `PDD_MANIFEST` 保留清单，见 §3.1 |
| `<组件ID>.md5checker` | 全文件 `{length, md5}` 表，见 §3.2 |
| `<组件ID>.so` / `lib*.so` | So 载荷（仅 10 个组件在此） |
| `extra_info.json` | `{uuid, md5, virtualVersion}`，仅 So 类组件有 |
| `config.json` | 按文件 ID 索引的摘要表（almighty / ti 类） |
| `*.pkg` / `*.apk` / `*.js` / `*.ttf` / `*.json` | 组件载荷本体 |
| `.volantis/component.yaml` | **构建**配置，见 §3.5 |
| `resources/oat/arm64/` | ART 编译产物目录 |

按载荷文件数分组（去掉 `manifest`/`md5checker` 后的文件计数）：

| 载荷数 | 组件数 | 说明 |
| ---: | ---: | --- |
| 0 | 13 | So 类组件，载荷只在 `dynamic_so` |
| 1 | 12 | 单文件组件（`.apk`/`.pkg`/`.ttf`/web 资源） |
| 2 | 15 | So + `extra_info.json` |
| 3 | 3 | 多资源组件 |
| 5 | 1 | `com.almighty.model.touchRecognize.v0.6.1` |
| 6 | 1 | `com.xunmeng.pinduoduo.almighty.three` |

### 2.2 `files/dynamic_so/<库名>_<毫秒>_<MD5>/`

26 个目录、约 74 MB。目录名格式：

```
<libname>_<epoch毫秒>_<md5>
```

每个目录内：

| 文件 | 内容 |
| --- | --- |
| `lib<name>.so` | 明文 ELF |
| `extra_info.json` | `{"uuid":"<32位hex>","md5":"<32位hex>","virtualVersion":"<epoch毫秒>"}` |
| `modified_<epoch毫秒>` | 空标记 |
| `uuid_<32位hex>` | 空标记 |
| `version_<x.y.z>` | 空标记 |

**`<md5>` 与 `extra_info.json.md5` 与 `<组件>.md5checker` 中该 So 的 md5 三者相等**，
这就是两套目录的关联键。

### 2.3 两者是同一批产物的两种视图

对全部 23 个 So 类组件核对（§10）：

| 关系 | 数量 | 证据 |
| --- | ---: | --- |
| `.vita` 与 `dynamic_so` 均有同一 So，MD5 相同 | 10 | 两处 `md5sum` 相等 |
| 仅 `dynamic_so` 有 So 载荷，`.vita` 只有清单 | 13 | `.vita` 目录下无 `.so` |
| 两处副本的 inode | 不同 | `ls -li` 显示独立 inode，**非硬链接** |

因此 `.vita` 是**组件视图**（面向 Vita 的清单/校验/装载语义），`dynamic_so` 是
**库视图**（面向 `System.load` 的扁平化目录）。二者对同一组件共享同一份 MD5。

## 3. 组件清单格式

### 3.1 `PDD_MANIFEST`

纯文本、`\n` 分隔，首行固定 `PDD_MANIFEST`：

```
PDD_MANIFEST
VERSION: 1.0.0
KEEP:
libtronavx.so
com.xunmeng.pinduoduo.v64libtronavx.md5checker
extra_info.json
```

`KEEP:` 之后是**该组件允许保留在磁盘上的文件名白名单**（含清单自身与 md5checker）。
清理逻辑据此删除不在白名单内的文件；`ManifestReader` 负责解析
（`com/xunmeng/pinduoduo/arch/vita/fs/manifest/ManifestReader.java`，返回 `Set<String>`，
解析失败抛 `ManifestParseException` 并带 `compUniqueName`/`compVersion`/`mark`）。

### 3.2 `<组件ID>.md5checker`

单行 JSON。**注意 `version` 字段恒为 `1.0.0`，是清单格式版本，不是组件版本**
（组件版本在目录名与 `vita_local_comp_v2` 里）：

```json
{"component_id":"com.xunmeng.pinduoduo.v64libtronavx",
 "version":"1.0.0",
 "md5_list":{
   "com.xunmeng.pinduoduo.v64libtronavx.manifest":{"length":110,"md5":"0885aabc03cd1b5f44bbd9df629d1690"},
   "libtronavx.so":{"length":4000176,"md5":"5b122de57929bae97add77d386799431"},
   "extra_info.json":{"length":117,"md5":"bb88e1d4c83137f18b72bd750c331a0e"}}}
```

46 个 md5checker 共 **146 条**记录。键是**组件内相对路径**（可含子目录，如
`lottie2/data_left.json`、`resources/push_plugin.apk`）。校验器在
`ol0/o.a()`：读文件 → `JSONFormatUtils.getGson().fromJson(..., Md5Checker.class)`，
失败时上报 `invalidMd5Checker` / `invalidMd5CheckerWithIOE` / `invalidMd5CheckerWithJSE`
并附带 `parsed_md5_checker`、`can_read`、`last_modified`、`file_length`。

### 3.3 `extra_info.json`

117 字节定长，把组件 ID 与库视图的目录名绑定：

```json
{"uuid":"2f37bbe9011d85e3a5500ade90aa9453","md5":"5b122de57929bae97add77d386799431","virtualVersion":"1789654040321"}
```

| 字段 | 含义 |
| --- | --- |
| `uuid` | 该次下载实例的随机 ID，等于 `dynamic_so` 内的 `uuid_<hex>` 空标记名 |
| `md5` | So 文件 MD5，等于目录名末段与 md5checker 条目 |
| `virtualVersion` | 下发时间戳（毫秒），等于目录名中段与 `version_<x.y.z>` 之外的标记 |

`vita_local_comp_v2` 里的 `virtual_version` 与 `privateProperties.virtualVersion` 取同一值。

### 3.4 `config.json` 与 `.pkg`（almighty / ti 类组件）

`almighty`/`ti` 类组件不落独立文件，而是打包进 `.pkg`，由 `config.json` 建立
**文件 ID → 摘要**的索引：

```json
{"2001.pkg":{"id":"2001","digest":"<172 字符 base64>","subType":"plugin",
             "type":"binary","version":1,"digestVersion":1,"timestamp":1591959548078}}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 组件内文件 ID，与 `.pkg` 文件名去掉扩展名一致 |
| `digest` | **base64 编码 128 字节 = RSA-1024 签名；`digestVersion: 1` 为 SHA-256 + PKCS#1 v1.5，覆盖完整 `.pkg` 文件字节** |
| `subType` | 样本中均为 `plugin` |
| `type` | `binary` |
| `version` | 该 pkg 的**内容版本**，随组件版本递增（样本中 1/3/5/8、142 等） |
| `timestamp` | 毫秒时间戳 |

`digest` 长度**逐条实测为 128 字节**（base64 172 字符），算法、公钥与签名
范围见 §8.1。字段语义为**已验证**，不再是长度推定。

### 3.5 `.volantis/component.yaml`

仅 `com.xunmeng.component.personalLottie` 带有该文件，且内容为**构建期**配置
（不是运行时配置）：

```yaml
base:
  release: zip -r personalLottie.zip . -x ".git/*" "README.md"
  debug:   zip -r personalLottie.zip . -x ".git/*" "README.md"
components:
  com.xunmeng.component.personalLottie:
    release: echo 'release'
    debug:   echo 'debug'
    output: personalLottie.zip
```

它随组件一起被打包下发，属于**构建脚本泄漏**，说明组件在服务端由
`volantis` 构建流水线产出。**结构已证实**。

## 4. 已安装组件登记表（MMKV）

### 4.1 `vita_local_comp_v2`

`files/mmkv/vita_local_comp_v2`（65,536 字节）保存 `LocalComponentInfo` 的 JSON 序列化，
是**"这台设备装了什么"的唯一权威表**。解码方式见 `tools/vita_registry.py`：
MMKV 头为 4 字节 `actualSize` + 4 字节 CRC32，随后 7 字节条目头，之后是
varint 键长/键/varint 值长的 KV 序列；同一键重复出现时**后写覆盖**。

去重后 **46 条**，与 `.vita` 目录数一致：

| 字段 | 说明 |
| --- | --- |
| `uniqueName` | 组件 ID |
| `version` | 组件版本 |
| `dirName` | 相对目录 `<组件ID>/<版本>` |
| `abs_files_dir` | **绝对路径**（仅 23 个 So 类组件有），指向 `dynamic_so` 实例目录 |
| `type` | `binary.pinduoduo` / `dex.pinduoduo` / `ti.pinduoduo` / `web.pinduoduo` / `lego.pinduoduo` |
| `build_no` | 构建号，与版本成比例（如 `1.33.5` → `133005`） |
| `tags` | `code` / `so` / `resource` / `model` |
| `install_time` | 安装毫秒时间戳 |
| `virtual_version` | 下发时间戳 |
| `basic_info_md5` | 基本信息摘要（32 位大写 hex） |
| `min_app_version` / `max_app_version` | 生效 App 版本区间（样本为 `-∞` / `+∞`） |
| `mcm_group_en_name` | 服务端分组名（如 `C-Android-APM`） |
| `flat_so` / `use_new_dir` | 目录扁平化与新版目录布局开关 |
| `isFileSeparatePatching` / `isUsedFileSeparatePatch` | 文件级分离补丁开关 |
| `upgrade_type` | 升级策略（`0` 非自动 / `1` 自动） |
| `privateProperties` | 附加 JSON，含 `virtualVersion` |
| `dir_schemas` | 目录结构声明（样本均为空） |

类型分布：`binary.pinduoduo` 28、`ti.pinduoduo` 8、`dex.pinduoduo` 7、
`web.pinduoduo` 2、`lego.pinduoduo` 1；带 `so` 标签 23 个。

### 4.2 其余 `vita_*` 存储

| 存储 | 大小 | 内容 |
| --- | ---: | --- |
| `Vita` | 64 KB | 189 个 `vita-comp-<组件ID>` 键，值为**文件锁令牌**（`opt3Logic<uuid>`）；另有 118 个 `vita_downloading_components_*` 键 |
| `comp_index` | 8 KB | 每个组件的**文件清单**（清单/md5checker/载荷文件名串联） |
| `comp_index_common` | 8 KB | `update_<组件ID>/<版本>` 形式的公共索引 |
| `comp_resource_used` | 4 KB | 组件被引用的资源名集合 |
| `comp_resource_visit` / `_ratio` | 16 KB | 资源访问计数与比例 |
| `vita_last_update_comp_time_v2` | 4 KB | `<组件ID>` → 最近更新时间戳 |
| `vita-upgrading-comp-pool` | 4 KB | 升级中组件池（样本为空） |
| `vita_version_block_info` / `_fake_info` | 4 KB | 版本封禁表（样本为空） |
| `vita_comp_offline_index` / `vita_comp_offline_comp_info_index` / `vita_comp_offline_visit_count` | 4 KB | 离线（内置）组件索引（样本为空） |
| `push_pull_comp_meta_mmkv` | 512 KB | push-pull 元信息 |
| `vita-debugger` / `scan-status-vita-debugger` | 4 KB | 调试器开关状态 |

**信息范围**：组件级访问频次与时间、资源使用画像、升级/封禁状态、锁持有者。
这些是**本地行为画像**，不直接上报原始键值，但会聚合进 §7.2 的上报体。

## 5. 组件注册表（`.newLocker`）

### 5.1 命名规则

`files/.newLocker/` 下 183 个零字节 `.vlock` 文件。命名规则**已完全解出**：

```
MD5(组件ID)              .vlock          # 组件主锁
MD5(组件ID)              -patch.vlock    # 补丁锁
MD5(组件ID) <版本>       .vlock          # 版本锁
```

| 类别 | 数量 |
| --- | ---: |
| 框架自身锁（`mmkv` / `gc` / `vita_database` / `comp_meta_info_v3` / `installed_comp_record`） | 5 |
| 组件主锁 + 补丁锁（成对） | 126 × 2 |
| 版本锁（`-patch` 之外的版本后缀形式） | 4 |
| 未解析 | 2 |

4 个版本锁与其组件：

| 文件名 | 组件 |
| --- | --- |
| `21BC04FE…0.22.0.vlock` | `com.xunmeng.pinduoduo.power.monitor` |
| `41E226BD…1.5.0.vlock` | `com.xunmeng.pinduoduo.data_info` |
| `A10750CF…0.4.0.vlock` | `com.xunmeng.pinduoduo.push_main_dex` |
| `D77A3883…1.33.5.vlock` | `com.xunmeng.pinduoduo.PushComp` |

2 个未解析项（`650C169F…`、`6B20DF37…`）的 MD5 反查遍及 APK 的 6 个 DEX、
`assets-so`、`SoBuildInfo` 清单与设备端组件记录（约 44.5 万条候选串）均无命中，
判为**服务端已注册但本版本 APK 与本地均无对应声明**的组件。**假说**。

> 注：另有 1 个组件 `com.xunmeng.pinduoduo.emoji` 仅出现在 APK 的 DEX 字符串里
> （不在 `SoBuildInfo` 清单内），其 vlock 存在但无载荷，说明注册表来自
> **服务端下发**而非完全由 APK 决定。

### 5.2 已安装 / 已注册未下载 / 未注册

| 集合 | 数量 | 来源 |
| --- | ---: | --- |
| 已安装 | 46 | `vita_local_comp_v2` / `.vita` 目录 |
| 已注册 | 126 | `.newLocker` 反解 |
| **已注册但未下载** | **80** | 差集 |

已注册未下载的 80 个包含大量**风控与加固组件**，即"注册了但本设备没触发下载"：

```
com.xunmeng.pinduoduo.v64libBigAllocMonitor   com.xunmeng.pinduoduo.v64libmeco_cookie
com.xunmeng.pinduoduo.v64libCSoLoader         com.xunmeng.pinduoduo.v64libpcrash
com.xunmeng.pinduoduo.v64libFPUnwind          com.xunmeng.pinduoduo.v64libpcrash_anr
com.xunmeng.pinduoduo.v64libGlProcessor       com.xunmeng.pinduoduo.v64libpcrash_dumper
com.xunmeng.pinduoduo.v64libNativeBitmap      com.xunmeng.pinduoduo.v64libpdd_secure
com.xunmeng.pinduoduo.v64libREPlugin          com.xunmeng.pinduoduo.v64libpdd_sa_hook
com.xunmeng.pinduoduo.v64libSceneTreeEngine   com.xunmeng.pinduoduo.v64libriskplugin
com.xunmeng.pinduoduo.v64libapm_cpu           com.xunmeng.pinduoduo.v64libsargeras
com.xunmeng.pinduoduo.v64libapm_thread_monitor com.xunmeng.pinduoduo.v64libshadowhook
com.xunmeng.pinduoduo.v64libbytehook          com.xunmeng.pinduoduo.v64libshadowhook_nothing
com.xunmeng.pinduoduo.v64libchat_msg          com.xunmeng.pinduoduo.v64libwallet_crypto_box
com.xunmeng.pinduoduo.v64libfastdump          com.xunmeng.pinduoduo.v64libxdl
com.xunmeng.pinduoduo.v64libmdumper           com.xunmeng.pinduoduo.v64libxunwind
com.xunmeng.pinduoduo.v64libpapmLeak          com.xunmeng.pinduoduo.v64libyuv
com.xunmeng.pinduoduo.v64libpapm_trace        …（共 80 个）
```

## 6. 与 `SoBuildInfo` 的关系

APK 内 `SoBuildInfo` 清单 199 条，按 `where` 分三类：

| `where` | 数量 | 含义 |
| --- | ---: | --- |
| `APK` | 44 | 随 APK 分发（`lib/arm64-v8a` 22 个 + 其他） |
| `device` | 50 | 预期落在 `dynamic_so` |
| `absent` | 105 | 本设备不存在 |

关键交叉验证：

| 关系 | 结果 |
| --- | --- |
| `absent` 中属于 Vita 注册表的 | **54 / 105** |
| `absent` 且不在注册表的 | 51 |
| `device` ∩ 已安装 | 22 |

因此此前报告里"54 个库在本设备从未下载、列为未覆盖项"的表述需要**修正为**：
这 54 个是 **Vita 已注册、按需下发、本设备未触发**的组件。它们**不是分析遗漏**，
而是"注册但未下载"这一正常状态。剩余 51 个 `absent` 条目使用 `v7alib*` 命名
（而 Vita 用 `v64lib*`），属于另一套（32 位 / 旧架构）命名空间，样本设备为
arm64，故不适用。**结构已证实**。

## 7. 网络协议

### 7.1 端点

| 端点 | 方法 | 用途 |
| --- | --- | --- |
| `/api/app/v1/component/query` | POST | 查询组件是否有更新（主接口） |
| `/api/app/v1/component/manual/query` | POST | 手动触发检查 |
| `/api/app/v1/component/manual/query/titan` | POST | 手动触发（Titan 通道） |
| `/api/app/v1/component/report` | POST | 下载/补丁/解密结果上报 |
| `/api/one-gateway-client/zone/v1/component/fetch` | POST | V3 拉取 |
| `/api/one-gateway-client/zone/v1/component/pull` | POST | push-pull 元信息拉取 |
| `/api/one-gateway-client/zone/v1/component/cdn/check` | POST | CDN 可用性检查 |
| `/api/one-gateway-client/zone/v1/component/upload` | POST | 组件上传 |

载荷 CDN（`ik0.a.w().k()` 返回的 CDN 基址 + 路径）：

```
/volantis3-open/component[/flat]/<build_no>/<组件ID>.zip
/volantis3-open/component[/flat]/<build_no>/<组件ID>.7z
/volantis3-open/component[/flat]/<build_no>/<组件ID>.br
/volantis3-open/aes/component[/flat]/<build_no>/<组件ID>.br     # security_level == 1
/volantis3-open/component/diff/<本地build_no>/<远端build_no>/<组件ID>.br
/volantis3-open/component/index/<build_no>/<组件ID>.zip         # 离线索引
```

`/flat` 仅在组件 ID 匹配扁平化规则**且** `flat_so == true` 时插入。

### 7.2 上报体

`/api/app/v1/component/report` 的事件码（`VitaConstants$ReportPatchCode`）：

| 值 | 名称 | 值 | 名称 |
| ---: | --- | ---: | --- |
| 1 | `download_start` | 12 | `zip_diff_start_patch` |
| 2 | `download_succ` | 13/14 | `file_sepa_prepare_succ/fail` |
| 3 | `download_fail` | 15/16/17 | `file_sepa_callback_start/succ/fail` |
| 4 | `decompress_start` | 18/19 | `patch_to_extra_dir_start/succ` |
| 5/6 | `decompress_succ/fail` | 20 | `patch_upgrade_succ` |
| 7 | `decrypt_start` | 21 | `ipc_download_start` |
| 8/9 | `decrypt_fail/succ` | 22/23 | `ipc_download_succ/fail` |
| 10 | `dir_modify` | | |

失败上报（`inner/e0.java`）附带字段：`available_space`、`patching_file_name`、
`patching_old_file_size`、`lock_file_existed`、`manifest_exists`、`secure_level`、
`secure_key`、`secure_version`、`is_support_zip_patch`、`is_zip_diff_package`。

> **`secure_key` 会被上报**。它同时是 AES 解密所需的密文材料（§8.2），
> 因此该字段是"客户端把解密材料回传服务端"的强证据。**结构已证实**。

另有两类统计上报：`comp_daily_usage_statistics`、`comp_visit_statistics`
（本地 `comp_resource_visit*` 的聚合）。

### 7.3 拉取响应字段（`RemoteComponentInfo`）

`FetchResp` 顶层：`latest[]`（组件数组）、`delay_time`、`help_msg`。
每个组件的字段（`@SerializedName`）：

| 字段 | 说明 |
| --- | --- |
| `cpnt_id` | 组件 ID |
| `version` / `build_no` / `virtual_version` | 版本三元组 |
| `type` / `tags` / `dir_name` / `dir_schemas` | 类型与目录布局 |
| `security_level` / `security_key` | 加密级别与密钥材料（§8.2） |
| `signkey` / `signkey_brotli` / `signkey_sevenz` | 完整包签名（按压缩格式） |
| `zip_diff_signkey` / `brotli_diff_signkey` / `sevenz_diff_signkey` | 差分包签名 |
| `url` / `url_brotli` / `url_sevenz` | 载荷地址（按压缩格式） |
| `zip_diff_url` / `brotli_diff_url` / `sevenz_diff_url` | 差分地址 |
| `diff_flag` | 是否支持差分 |
| `flat_so` | 扁平化 So |
| `deploy_id` / `cpnt_sort` / `sort_seq` | 部署与排序 |
| `release_stage` / `release_status` | 发布阶段/状态 |
| `auto_upgrade` / `old_version_auto_upgrade` | 自动升级策略 |
| `background_download` | 后台下载 |
| `min_app_version` / `max_app_version` | 生效区间 |
| `mcm_group_en_name` | 服务端分组 |
| `private_properties` | 附加属性 |
| `basic_info_md5` | 基本信息摘要 |
| `offline` | 离线（内置）标记 |

**压缩/差分组合共 6 种**（`VitaDownload.PatchType`）：`ZIP_DIFF(1)`、`ZIP_FULL(2)`、
`Z7_DIFF(3)`、`Z7_FULL(4)`、`BR_DIFF(5)`、`BR_FULL(6)`，由下载 URL 与
`*_pair.first` 比对判定。

查询请求侧（`UpdateComp`，`@SerializedName`）：`cpnt_id`、`version`、
`build_no`、`private_properties`、`bizTypes`、`flat_so_diff`。即**客户端上报
已装组件的版本三元组与私有属性**，服务端据此决定下发完整包还是差分包。

### 7.4 证书固定

`rn0/a.java:284` 的 `Network.certificate_pinning_enable_uris_77700` 配置里，
`forceEnalbePinner: true` 的路径**只有三个**，全部是 Vita 接口：

```json
{"pinnerItems":[
  {"forceEnalbePinner":true,"path":"/api/app/v1/component/manual/query"},
  {"forceEnalbePinner":true,"path":"/api/app/v1/component/manual/query/titan"},
  {"forceEnalbePinner":true,"path":"/api/app/v1/component/query"}],
 "version":1}
```

组件查询是全 App **唯一被强制证书固定**的接口族，说明组件下发链路被当作
最高信任边界。**已验证**（配置字符串与调用点）。

## 8. 完整性与加密链

三层，全部使用公开算法：

| 层 | 算法 | 作用 | 证据 |
| --- | --- | --- | --- |
| 1 | MD5 | 每个文件的内容校验 | `<组件>.md5checker` |
| 2 | SHA256WithRSA | 组件/索引签名 | `ol0/a0.k()` |
| 3 | AES/CBC/PKCS5Padding | `security_level ∈ {1,2}` 时解密载荷 | `vita/patch/inner/a.b()` |

### 8.1 SHA256WithRSA 验签

`ol0/a0.java:185–199`：

```java
Signature signature = Signature.getInstance("SHA256WithRSA");
signature.initVerify(d(Base64.decode("<RSA-1024 SPKI>")));
// 流式 update，最后：
return signature.verify(Base64.decode(signKey));
```

同文件 `d()` 用 `KeyFactory.getInstance("RSA")` + `X509EncodedKeySpec`。
索引签名走同一函数（`inner/AutoDownloadCompHelper.java:141`，用
`OfflineIndexComponentInfo.index_signkey`）。

### 8.2 `security_level` 与 AES

`uv2/a.a(int)` 判定 `1 == level || 2 == level` 为"加密组件"。
`uv2/a.b(String)` 把 `security_key`（base64）解密成原始字节并缓存（`ArrayMap`），
`vita/patch/inner/a.b(File, level, key)` 在 `level == 1` 时构造：

```java
Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
byte[] key = secureKey(str);                      // uv2/a.b -> 明文密钥
cipher.init(DECRYPT_MODE,
            new SecretKeySpec(Arrays.copyOfRange(key, 0, 16), "AES"),
            new IvParameterSpec(new byte[16]));   // 全零 IV
```

即 **AES-128-CBC、取密钥前 16 字节、全零 IV**。`uv2/a` 的密钥来源是
`xj0.h` 接口（`byte[] b(byte[])` + `int d()`），由 `mv2/b.a` 桥接到
`qb2.d.b()`；`qb2.d.b()` 返回 `qb2.c` 实现。**该实现已定位并逐层闭合**，
见 §8.4。

> 全零 IV 是**固定 IV**，等价于 ECB 的确定性；这是可静态判定的实现缺陷，
> 但对"按块流式解密大文件"是常见取舍。

### 8.4 `security_key` 的解密实现（原"未取得"，现已闭合）

原报告把这一环记为"`qb2/h` 不在 jadx 输出中"。**结论是错的**：`qb2/h` 确实
不在 DEX 里，但它也不需要存在——真正的实现是一条**纯 native** 的调用链，
`xj0.h` 的实现类由 `mv2/b` 以匿名内部类提供，转手就打进 `libpdd_secure.so`。
完整链路（全部为静态读取所得）：

```text
uv2/a.b(String base64)                 ; 组件解密入口
  -> xj0.h.b(byte[])                    ; 接口
     -> mv2/b.a.b(byte[])               ; 匿名内部类，mv2/b.r() 返回
        -> qb2.d.b().b(byte[])          ; qb2/d.c() = new lb2.h()
           -> lb2.h.b(byte[])           ; qb2.c 实现
              -> SecureNative.z(byte[]) ; native
                 -> libpdd_secure.so  Java_..._SecureNative_dv
```

四步逐一落实：

- **`mv2/b.r()` 返回 `new a()`**，`mv2.b.a` 是 `implements xj0.h` 的匿名内部
  类，`b()` 直接 `return d.b().b(bArr);`、`d()` 直接 `return d.b().d();`。
- **`qb2/d.c()` 返回 `new lb2.h()`**，`lb2.h implements qb2.c`。
- **`lb2.h.b(byte[])`** 返回 `SecureNative.z(bArr)`，异常时记 `L.e2(41200)`。
- **`SecureNative.z(byte[])`** 是 `private static native byte[] dv(byte[]);`
  的包装，`libpdd_secure.so` 中以
  `Java_com_xunmeng_pinduoduo_secure_SecureNative_dv` 导出。

**`dv` 的 native 结构（已逐层反扁平化）。** 导出体 `0x1f75c` 是一个**栈上
状态字的 FLA 分派核**（状态槽 `[sp,#24]`，31 项跳转表建在 `sp+0x70`，每项
以 `sub` 存成相对差，`br` 前 `add x10, x5, x10` 还原绝对地址）。`tools/fla_trace.py` 能把它的
11 个分派步的键与守卫全部解出（`--table-sp 0x70`），但该核的状态字是 **32 位**
且表按**字节**寻址，与工具面向 64 位栈表的假设不同，故链的还原改用配套的
定点解释器（对常量守卫直接求值）；两者结果一致，入口链为唯一确定路径：

```text
0x1f9e4 --(w8 < w25)--> 0x1faf8 --(w8 < w10)--> 0x1fc8c
        --> 0x1fe24 --(w8 == w0)--> 0x1fe70
```

其中 `0x1fb90` 一块的语义是：

```asm
ldr  x0, [sp, #48]        ; 输入长度
bl   malloc@plt           ; 分配输出缓冲
str  x0, [sp, #56]
mov  w0/w1/w2, #0x10      ; 16 字节常量
bl   0x33a14              ; 16 字节常量装载跳板
bl   0x16c474             ; 核心：解密
str  w0, [sp, #68]        ; 返回明文长度
```

**核心 `0x16c474` 已用指令级模拟（Unicorn）逐字节验证。** 它内部：

- `0x16b03c` 是**标准 AES-128 密钥扩展**——喂入
  `000102030405060708090a0b0c0d0e0f` 后，输出第 0 轮
  `000102030405060708090a0b0c0d0e0f`、第 1 轮
  `d6aa74fdd2af72fadaa678f1d6ab76fe`、第 10 轮
  `13111d7fe3944a17f307a78b4d2b30c5`，**与 FIPS-197 逐字节一致**；
- 密钥材料来自 `.rodata` 常量 `0x1926b0`（16 字节）与 `0x1926c0`
  （16 字节），即**硬编码对称密钥**，不是 RSA；
- `0x16b924` 是**块变换**。按 `adrp/add` 对解析出的 S-box 引用点：
  `.rodata 0x19c9dc`（正向 S-box `637c777bf26b6fc5…`）在 `0x16b134`、
  `0x16b150`（均属密钥扩展 `0x16b03c`）与 `0x16b658`（属块变换）被引用；
  `.rodata 0x19cadc`（**逆 S-box** `52096ad53036a538…`）在 `0x16bb18`
  （属块变换）被引用。即**密钥扩展只用正向 S-box**，**块变换两个方向都带**，
  与一个同时含加密/解密路径的块例程相符；
- 尾部做 **PKCS#7 去填充**并返回明文长度：
  `add x8, x8, w19, sxtw` / `ldurb w8, [x8, #-1]` / `sub w0, w19, w8`；
- `0x33a14` 的跳板里有一段用 `lrand48` 填缓冲的**反分析噪声分支**
  （`scvtf`/`fmul`/`fcvtzs`/`strb`），不参与密钥派生。

因此 `security_key` 的语义是**对称密钥**，由 `uv2/a` 缓存后取前 16 字节作
AES-128 密钥、全零 IV 解 `.pkg`/组件分片；**与 §8.3 的 RSA 公钥无关**。
原报告 §12 第 2 项据此撤销。

**仍未逐字节断言的部分**（诚实标注，不影响上面结论）：块例程内部把中间状态
写在**自身栈帧**里而不回写调用方缓冲，且入口对 `w3` 做表行选择，因此
"密钥扩展 + 逆 S-box + PKCS#7 去填充"这三点是**已验证**的，而
"逐块反馈模式究竟是 CBC 还是 OFB、以及 `0x16c474` 第 4 个参数的精确语义"
只到**结构已证实**；本机无法取得真实 `security_key` 密文样本，故未做
端到端明文回环。

### 8.3 组件签名公钥（DEX 侧）

全 APK 的 6 个 DEX 中共有 **6 把硬编码 X.509 SPKI 公钥**（`MI…` base64 常量），
全部为**验签公钥**（无对应私钥材料）：

| # | DEX | 位宽 | DER 字节 | SHA-256(DER) 前 16 位 |
| ---: | --- | ---: | ---: | --- |
| 1 | `classes.dex` | 1024 | 162 | `f0a5723ee90872b1…` |
| 2 | `classes.dex` | 1024 | 162 | `c9daed6d9bfbbabd…` |
| 3 | `classes3.dex` | 1024 | 162 | `0ad4904c26c83028…` |
| 4 | `classes3.dex` | 1024 | 162 | `9f70fdf2f9b774fa…` |
| 5 | `classes3.dex` | 1024 | 162 | `cb5ef78dcd0427f7…`（**Vita 组件验签，§8.1**） |
| 6 | `classes3.dex` | 2048 | 294 | `1a40e8d0a36b8a5f…` |

第 6 把（RSA-2048）在 `ij0/a.b(byte[], String)` 中使用：先 base64 解出
密文密钥，再用 RSA 解出明文密钥，最后 `b.v(bArr, key)` 解密数据。这是
**"RSA 包裹对称密钥"**的密钥交换形态，与 §8.2 的 `uv2/a` 链路同族。
**结构已证实**（调用链与位宽），其填充方案未逐字节断言（§12）。

### 8.5 组件签名公钥（native 侧）

`libpdd_secure.so` 的异或字符串池中另有一把 **明文 2048 位 SPKI**
（294 字节 DER，位于 `.rodata` 池内 `0x19b57f`），方向已由池内解出的 JNI 名
`rsaEncrypt` / `rsaEncryptWithPublicKey` 判定为**公钥加密**（非验签），
详见 [obfuscation.md](obfuscation.md) §9.4.6。`libmedia_engine.so` 另带一把
**不同**的公钥。三处公钥互不相同，属不同信任域。

## 9. 本地数据库（`vita-database`）

### 9.1 表结构

`vita-database`（Room，`identity_hash 3690909d808ef65a2536b562f1230ac9`），
4 张表，DDL 与设备端**逐字节一致**：

```sql
CREATE TABLE `UriInfo` (`uri` TEXT NOT NULL, `comp_id` TEXT NOT NULL,
  `version` TEXT NOT NULL, `relative_path` TEXT, `absolute_path` TEXT,
  `length` INTEGER NOT NULL, `md5` TEXT, PRIMARY KEY(`uri`,`comp_id`,`version`));

CREATE TABLE `VitaAccessInfo` (`comp_id` TEXT NOT NULL, `version` TEXT NOT NULL,
  `access_count` INTEGER NOT NULL, `access_history` TEXT NOT NULL,
  PRIMARY KEY(`comp_id`,`version`));

CREATE TABLE `VitaVersionInfo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `comp_id` TEXT NOT NULL, `version` TEXT NOT NULL, `time` INTEGER NOT NULL,
  `operator` TEXT NOT NULL);

CREATE TABLE `VitaCleanInfo` (`comp_id` TEXT NOT NULL, `clean_time` INTEGER NOT NULL,
  `recover_time` INTEGER NOT NULL, `is_auto` INTEGER NOT NULL,
  PRIMARY KEY(`comp_id`));
```

迁移链 `MIGRATION_2_3`（给 `UriInfo` 加 `length`/`md5`）、`MIGRATION_3_4`
（建 `VitaAccessInfo`/`VitaVersionInfo`）、`MIGRATION_4_5`（建 `VitaCleanInfo`）。
库文件自带 `vita_database.vlock` 锁，超时 10,000 ms（`VITA_DATA_BASE_LOCK_TIMEOUT`）。

### 9.2 实际行数与内容范围

读取时**必须连同未 checkpoint 的 WAL**（`vita-database-wal` 416,152 字节）
一起打开，否则行数偏低：

| 表 | 主库行数 | 含 WAL 行数 | 信息范围 |
| --- | ---: | ---: | --- |
| `UriInfo` | 0 | **3** | 组件内资源的 URI→本地路径映射 |
| `VitaAccessInfo` | 39 | **43** | 组件访问次数与最近 20 次访问时间 |
| `VitaVersionInfo` | 42 | **46** | 版本变更流水（`operator` 取值 `install`） |
| `VitaCleanInfo` | 0 | 0 | 清理/恢复时间与是否自动 |

`UriInfo` 是**唯一含绝对路径**的表：`absolute_path` 形如
`/data/user/0/<包名>/files/.vita/<组件ID>/<版本>/<相对路径>`（按 `SECURITY.md`
不公开实际值）。样本中 3 行全部指向 `com.xunmeng.pinduoduo.lego.template.scene`
的 `.lego` 资源，`uri` 为 `https://lego.pdd.com/api/...` 形式的**原始请求 URL**，
`length`/`md5` 为该资源的长度与摘要。

`VitaAccessInfo.access_history` 是 JSON 字符串数组（最多 20 个毫秒时间戳，
`VitaAccessInfo.HISTORY_SIZE = 20`，超出时 `remove(0)` 淘汰最旧项），
由 `recordAccess()` 维护。

**信息范围小结**：Vita 的本地持久化只保存**组件装载与访问行为**——
装了哪些组件、哪些资源映射到哪个本地路径、访问了多少次、什么时候访问、
版本何时变更、何时被清理。**不含**账号标识、位置、通讯录或消息内容。

### 9.3 与源码侧实体的一致性

| 表 | 实体类 | 一致点 |
| --- | --- | --- |
| `UriInfo` | `arch/vita/database/uri/UriInfo.java` | 7 字段与 DDL 逐字段对应；`equals/hashCode` 用 `uri+compId+version` 三元组，与主键一致 |
| `VitaAccessInfo` | `.../access/VitaAccessInfo.java` | `HISTORY_SIZE = 20` 与 `access_history` 上限一致 |
| `VitaVersionInfo` | `.../version/VitaVersionInfo.java` | 构造器 `time <= 0` 时回填 `System.currentTimeMillis()` |
| `VitaCleanInfo` | `.../clean/VitaCleanInfo.java` | 4 字段对应 |

DAO 方法（`UriDao`：`load`/`loadAll`/`loadByCompIds`/`insertAll`/`deleteAll`/
`deleteByCompId`）与 `UriTableAccessException` 说明 Uri 表是**可整体重建的缓存**，
而访问/版本表是**历史记录**。

## 10. 完整性核验结果

对 `<组件>.md5checker` 的全部 146 条记录，在设备上以 `md5sum` 实测比对：

| 结果 | 条数 | 说明 |
| --- | ---: | --- |
| 通过 | **134** | 声明 `md5` 与实测一致 |
| 仅 `extra_info.json` 不匹配 | 12 | 见下 |
| 缺失 | 0 | — |

12 条不匹配**全部**是 `extra_info.json`，且**只发生在载荷在 `dynamic_so`
的 So 类组件**上。原因：`.vita` 目录里的 `extra_info.json` 与
`dynamic_so/<库目录>/extra_info.json` 是**两份不同的文件**（前者是安装时
的快照，后者随每次重新下发更新 `virtualVersion`），因此 `.vita` 内的
md5checker 记录会与 `dynamic_so` 内的当前副本不符。

**So 载荷本身 23/23 全部通过**（`.vita` 10 + `dynamic_so` 13），
说明**组件载荷的完整性链是自洽的**，不匹配只出现在这个可变的元数据文件上。

`.vita` 内 170 个文件的 MD5 全量实测，`dynamic_so` 内 49 个非空文件全量实测。
样本 `libtronavx.so`：4,000,176 字节，MD5 `5b122de57929bae97add77d386799431`，
SHA-256 `71d88a54ca242206c039e5f8e9b60903b9f3004b35b5067f0fb0c271715f7592`，
`md5checker`、目录名末段、`extra_info.json.md5` 三处一致。

## 11. 与风控的关系

| 机制 | 与 Vita 的关系 |
| --- | --- |
| 证书固定 | **仅**对 3 个 Vita 接口强制（§7.4） |
| 组件签名 | SHA256WithRSA，公钥硬编码在 DEX（§8.1、§8.3） |
| 载荷加密 | `security_level ∈ {1,2}` → AES-128-CBC，密钥由 `security_key` 经 RSA 链解出（§8.2） |
| 反分析组件 | 注册表中 80 个未下载项含 `libpdd_secure`、`libmeco_cookie`、`libsargeras`、`libshadowhook`、`libxunwind`、`libriskplugin`、`libpdd_sa_hook`、`libbytehook`、`libCSoLoader` 等（§5.2） |
| 行为画像 | `comp_resource_visit*`、`comp_daily_usage_statistics` 聚合后上报（§7.2） |
| 调试器开关 | `vita-debugger` / `scan-status-vita-debugger` MMKV 存储 |

**关键含义**：Vita 是这些风控库的**下发通道**。若某库不在设备上，
"分析遗漏"与"服务端未下发"必须区分——本设备上 `libpdd_secure.so` 在
**APK 内**（已分析），而 `libshadowhook`、`libmeco_cookie`、`libsargeras`
等只在**注册表**里（未下发）。这解释了此前 [obfuscation.md](obfuscation.md)
§9.6 中 `SE`/`ShadowHook`/`meco` 三个 native 方法族在四掩码并集下
**零命中**的原因：其提供库确实不在这台设备上。

## 12. 未决事项

1. ~~`config.json` 中 `.pkg` 的 `digest` 字段（128 字节）判为 RSA-1024 签名~~
   **已闭合**：`digestVersion: 1` 为 SHA-256 + RSA/PKCS#1 v1.5，签名范围是
   完整下载文件；已用断点库保留的 `x-pos-meta-digest` 与对应 CDN 对象、以及
   §8.3 第 5 把公钥复验（见 §8.1）。
2. ~~`qb2/h`（`xj0.h` 的实现类，负责 `security_key` 的 RSA 解密）不在 jadx
   输出中~~ **已闭合且原判断有误**：`security_key` 不是 RSA，而是
   **硬编码 16 字节对称密钥的 AES-128 解密**；实现是纯 native
   （`uv2/a` → `xj0.h` → `mv2/b.a` → `qb2.d.b()` → `lb2.h` →
   `SecureNative.dv` → `libpdd_secure.so 0x1f75c`）。AES-128 密钥扩展已用
   指令级模拟与 FIPS-197 逐字节对齐，见 §8.4。
3. `.vita` 与 `dynamic_so` 中同一 So 的 10 份副本**内容相同**（MD5 相等）
   但 inode 不同；谁先写入、谁复制谁未从日志断言，判为**同一次安装的两次落盘**。
4. 2 个未解析 vlock（`650C169F…`、`6B20DF37…`）的组件 ID 未知，
   反查范围已覆盖 APK 全部 DEX 字符串与 `SoBuildInfo` 清单。
5. `vita_version_block_info` / `_fake_info` / `vita_comp_offline_index` 在本
   样本中为空，其非空形态未取得样本。

## 13. 复现方式

```bash
# 组件注册表（.vlock 名 -> 组件 ID）
python3 tools/vita_registry.py locks <newLocker目录> <dex或清单文件...>

# 已安装组件登记表（MMKV）
python3 tools/vita_registry.py mmkv <vita_local_comp_v2>

# md5checker 逐条核验（在设备上先跑 md5sum 取回哈希清单）
python3 tools/vita_registry.py verify <md5checker目录> <md5sum输出...>
```

工具不读取任何凭据，不写入被分析设备；哈希清单一律由设备上的 `md5sum`
产出后带回。
