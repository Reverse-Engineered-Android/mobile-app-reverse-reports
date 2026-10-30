# 本地存储

## 1. 只读核对口径

设备核对使用 Android 私有目录的只读快照；没有启动 App、没有写入数据库、没有
删除或修改文件。SQLite 通过只读方式执行 `PRAGMA integrity_check` 和 schema
查询，行数只作为快照，会随使用状态变化。

## 2. 数据库清单

设备快照有 **7 个 SQLite、33 张业务表**，`PRAGMA integrity_check` 全部为 `ok`。

| 数据库 | 表数 | 关键 schema |
|---|---:|---|
| `10000001@bd_tea_agent.db` | 9 | `custom_event`、`eventv3`、`launch`、`packV2`、`page`、`profile`、`trace`；事件表含 `local_time_ms`、`tea_event_index`、`nt`、`user_id`、`session_id`、`event_type`、`properties` 等列 |
| `Popup.db` | 7 | `taskFinishCount(taskId,ssid,count)`、`taskFlowEvent(ssid,ruleId,logId,nodeId,eventName,eventParams,eventTime)`、`taskFlowFreqCtrl`、`taskFlowNodeInfo`、`taskFlowRuleInfo`、`taskTriggerCount` |
| `atta.db` | 3 | `record(_id,process,params)` |
| `db_xnzq` | 2 | `CacheTable(key1,version,key2,key3,saveTime,expTime,value)`，复合主键 `(key1,version,key2,key3)` |
| `mitake.sse.sqlite.db` | 4 | `Company`、`Stock`、`HkCodes(type,code)` |
| `signalData.db` | 2 | `SignalDataTable(uid,signalType,stockUinKey,lineType,data,version,saveTime)`，复合主键 `(uid,signalType,stockUinKey,lineType)` |
| `thinkive.db` | 6 | `data_cache_table(_id,key,size,ttl)`、`download_table`、`map_table`、`server_url_table` |

快照行数示例：`page=46`、`CacheTable=43`、`HkCodes=3,785`；App 当前会话的
`eventv3`、`record` 为 0，说明这些表存在但不保证持续有数据。

## 3. `shared_prefs`

目录共 **69 个 XML 文件，全部非空**。观测到的结构：

- 60 个文件共享前 56 字节密文前缀；
- 6 个 81 字节文件（如 `CopyAssetsFolderRecord.xml`、`ZzxCache.xml`、
  `codesTime.xml`、`optionLevel.xml`、`update_site.xml`、`user_login.xml`）
  密文完全相同；
- 另 6 个文件前 44 字节相同，但从第 48 字节起分叉；
- `ijm_ifr.xml` 使用不同前缀，`crhs_2019.xml` 为明文 XML；
- 密文长度 mod 16 = 1，互 XOR 的可打印率约 0.38，符合随机外观。

因此这是**4 帧 AES block 对齐的整体密文结构**，共享的只是相同 XML 明文头和
块级处理结果；现有证据**不支持“同一逐位置 keystream 跨文件复用”**。算法、
密钥、IV/模式和明文未还原，不能把前缀相似性误报为可直接解密。

## 4. H5 `.r` 落盘

`files/yjb/**.r` 是运行时下载的 H5 文本资源，设备快照约 `210 MiB`、
`3,382` 个文件；`files/h5info.txt.r` 解密后为模块版本 JSON。`yjb.zip` 内的
同名格式使用位置相关 keystream，完整恢复与误差见 [report.md](report.md) §1.5。

## 5. 加固器与动态载荷

| 路径 | 格式结论 |
|---|---|
| `files/i12196010e51071ed` | 与 `assets/ijiami.dat` 前 40 字节一致的加固器数据体；无 `dex`/ZIP/ELF/gzip 魔数 |
| `files/app_payload.dex` | 运行时下发 DEX，`dex\n035` |
| `files/app_payload.version` | 版本戳文本；具体服务端策略未知 |
| `files/uuid` | 设备唯一标识缓存（报告不发布真实值） |

## 6. 未闭环项

`Token.xml` 等账号缓存内容未解密；`shared_prefs` 加密算法与 key 未知；
`i12196010e51071ed` 内部格式未知。所有未知项均不作为已验证结论。
