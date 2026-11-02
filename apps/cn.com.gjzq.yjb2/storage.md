# 本地存储

## 1. 只读核对口径

设备核对使用 Android 私有目录的只读快照；没有启动 App、没有写入数据库、没有
删除或修改文件。SQLite 通过只读方式执行 `PRAGMA integrity_check` 和 schema
查询，行数只作为快照，会随使用状态变化。

## 2. 数据库清单

设备快照有 **7 个 SQLite、30 个 `sqlite_master` 对象（23 张业务表 + 7 个 `android_metadata`）**，`PRAGMA integrity_check` 全部为 `ok`。

| 数据库 | 表数 | 关键 schema |
|---|---:|---|
| `10000001@bd_tea_agent.db` | 9 | `custom_event`、`eventv3`、`launch`、`packV2`、`page`、`profile`、`trace`；事件表含 `local_time_ms`、`tea_event_index`、`nt`、`user_id`、`session_id`、`event_type`、`properties` 等列 |
| `Popup.db` | 7 | `taskFinishCount(taskId,ssid,count)`、`taskFlowEvent(ssid,ruleId,logId,nodeId,eventName,eventParams,eventTime)`、`taskFlowFreqCtrl`、`taskFlowNodeInfo`、`taskFlowRuleInfo`、`taskTriggerCount` |
| `atta.db` | 3 | `record(_id,process,params)` |
| `db_xnzq` | 2 | `CacheTable(key1,version,key2,key3,saveTime,expTime,value)`，复合主键 `(key1,version,key2,key3)` |
| `mitake.sse.sqlite.db` | 4 | `Company`、`Stock`、`HkCodes(type,code)` |
| `signalData.db` | 2 | `SignalDataTable(uid,signalType,stockUinKey,lineType,data,version,saveTime)`，复合主键 `(uid,signalType,stockUinKey,lineType)` |
| `thinkive.db` | 6 | `data_cache_table(_id,key,size,ttl)`、`download_table`、`map_table`、`server_url_table` |

快照行数示例：`eventv3=92`、`page=20`、`CacheTable=44`、`HkCodes=3,787`、
`SignalDataTable=7`；其余业务表为 0。行数随使用状态变化，只作快照记录。

## 3. `shared_prefs`

目录共 **70 个 XML 文件，全部非空**。观测到的结构：

- 61 个文件共享同一 56 字节密文前缀；
- 6 个 81 字节文件（`CopyAssetsFolderRecord.xml`、`ZzxCache.xml`、
  `codesTime.xml`、`optionLevel.xml`、`update_site.xml`、`user_login.xml`）
  彼此逐字节相同并共用另一个 56 字节前缀；
- `ijm_ifr.xml`、`ijm_sharedecryption.xml` 使用各自前缀，`crhs_2019.xml`
  是唯一明文 XML；
- 66 个文件长度 mod 16 = 1，4 个例外（1 明文 + 3 个小文件）；
- 主组内两两 XOR 的可打印 ASCII 比例均值约 0.22，低于随机期望 0.37。

因此这是**4 帧 AES block 对齐的整体密文结构**，共享的只是相同 XML 明文头和
块级处理结果，组内首个差异字节落在 16 字节块边界（64/80/96 等）；现有
证据**不支持“同一逐位置 keystream 跨文件复用”**。算法、密钥、IV/模式和
明文未还原，不能把前缀相似性误报为可直接解密。

## 4. H5 `.r` 落盘

`files/yjb/**.r` 是运行时下载的 H5 文本资源，设备快照约 `210 MiB`、
`3,382` 个文件；`files/h5info.txt.r` 解密后为模块版本 JSON。`yjb.zip` 内的
同名格式使用位置相关 keystream，完整恢复与误差见 [report.md](report.md) §1.5。

## 5. 加固器与动态载荷

| 路径 | 格式结论 |
|---|---|
| `files/i12196010e51071ed` | 设备当前 `69,262,848` B；`04 00 00 00` + LE 长度 + 32 个十六进制字符（16 字节 ID），长度字段等于自身大小；与主样本 `ijiami.dat` 前 40 B 不同；无 `dex`/ZIP/ELF 魔数 |
| `files/app_payload.dex` / `app_payload.version` / `gjzq_*` | 分析注入的 `GjzqProbe`，排除出 App 载荷结论 |
| `files/uuid` | 设备唯一标识缓存（报告不发布真实值） |

## 6. 未闭环项

`Token.xml` 等账号缓存内容未解密；`shared_prefs` 加密算法与 key 未知；
`i12196010e51071ed` 内部格式未知。所有未知项均不作为已验证结论。
