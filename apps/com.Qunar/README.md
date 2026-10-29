# 去哪儿旅行 10.4.23

研究对象为官方版本 `10.4.23`（versionCode `345`）、包名 `com.Qunar` 的 ARM64 APK
及其随包 native 库、DEX、`assets/*.qp` 离线包与自有设备上的只读落盘数据。
全部业务链路（搜索、下单、支付、登录、上传）均为**静态只读**调查：未登录、未构造
或发送任何请求、未下单、未支付、未上传或下载业务文件、未绕过任何风控。
设备侧只做**只读**核对：读取应用私有目录中数据库 schema、`shared_prefs` 文件数量
与尺寸、MMKV 文件名与尺寸，不写入设备、不发起网络请求。

样本 SHA-256：
`6834433914f2ad469fc63b64b0bbd6ff4bc1f072377b3509be96e2cf046a3c2b`，
大小 `92,254,498` 字节，APK 条目 `5,928` 个，DEX `15` 个，ARM64 native 库 `63` 个。

## 覆盖范围

- **Hotdog / Pitcher 传输协议**：`hdog` 信封、`normal`/`trans` 两种清单、
  `slugger` 代理出口、`Target-Url` 与 `L-Date` 请求头、`?qrt=` 路由标识。
- **`libgoblin_6_1_1.so` 信封编码**：`sand`/`drift` 外层字节布局、两处 adler32
  校验位、29/41 词表与 `gettimeofday` 索引、`Ena1907_req`/`Dea1907_resp` 编译型
  VM 的规模与分派形态、`JNI_OnLoad` 的 18 项 `RegisterNatives` 与完整性自检。
- **cparam 客户端指纹族**：`plat/usid/fp/gid/sid/pid/cid/ma/adid/mno/uid/instid`
  等字段的构造点、`hk` 反 Hook 位掩码、`catom/cas` 组件栈信息、`ke`/`ref` 语义、
  `hotdogParamToHeaderSwitch` 把 `fp` 提升为请求头。
- **请求加签**：`signature_b_para_config` / `append_data_b_para_config` 两个
  DataPip 开关、`PubInterfaces.uti` → `libqlynx_v++` 的 `j.uti` 原生实现、
  `b`/`c`/`ext` 的 TreeMap 拼接顺序。
- **两套设备风控 SDK**：`com.qunar.rc`（`NUtils.cd/ep/uti/jcd/sepa/sfp`、
  `FpService` 的 multipart 上报、`Checker` 模拟器评分与 keystore attestation、
  `Cmd` 的 `ls`/`SystemProperties` 采集、`d.getcc()` 的 TLS 证书链+本地 IP+DNS）
  与 `com.ctripfinance.risk.device`（17 个收集器、`id5`/`instUUID`/`oaid`、
  AES-CBC 结果加密、`/m/quser/upDevice.do` 上报与 5 分钟节流）。
- **权限与组件面**：52 条 `uses-permission`（51 去重）、438 个组件、26 个导出组件
  （22 个无 `android:permission`）、183 条 `<queries>` 探测包名。
- **本地存储**：13 个明文 SQLite 与 1 个 netty 存储、50 个明文
  `shared_prefs`（362 个键名，与 livelab 的密文落盘不同）、16 个 MMKV。
- **离线包 `assets/*.qp`**：23 个包的 `qp` + 小端 u32 清单长度 + JSON 清单 +
  明文 JS 正文结构。

## 入口

- [综合结论](report.md)
- [网络与协议](network.md)
- [认证机制](auth.md)
- [上传下载范围](transfer.md)
- [风控机制](risk.md)
- [权限与导出面](permissions.md)
- [隐私与告知](privacy.md)
- [存储与本地数据](storage.md)
- [逆向证据](evidence.md)
- [完成度矩阵](completeness.md)

## 研究边界

客户端静态分析能证明“代码采集什么、在什么条件下触发、写入哪个字段、发往哪个
端点”，不能证明服务端如何评分、保存多久或采取何种处罚。报告把客户端事实与
服务端不可见部分严格分开；不提供登录绕过、签名伪造、批量抓取或规避风控的方法。

`Ena1907_req` / `Dea1907_resp` 两个编译型 VM 的内部字节码未复原，报告只给出
可重复验证的规模、分派形态、信封布局与失败码，不臆测其内部算法。请求侧信封
（6 字节魔数 + 两处 `adler32` + `Ena1907_req` 状态块）的两道校验已完整复原，
并已证实 `sand` 与 `drift` 是方向不同的两条信封、并非互逆运算。
