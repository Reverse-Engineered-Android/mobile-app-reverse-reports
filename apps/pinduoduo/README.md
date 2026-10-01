# 拼多多 8.26.0

研究对象是 `com.xunmeng.pinduoduo` 8.26.0 的 base APK、随 APK 分发的 ARM64 native
库、`assets/so_arm64-v8a/*.7z` 内嵌库、以及安装后由动态加载器落盘的运行库；本地数据
部分来自同一设备上该版本的**只读**文件系统快照。

公开部分覆盖：

- 主 API、长连接、CDN、日志/APM/风控上报的网络交互流程与 OkHttp 拦截器链。
- 具体协议格式：公共头、`anti-token`、API 签名（v1/v2）、`sdr` 签名描述符、
  multipart 上传表单、Titan 长连接结构。
- 认证机制：`AccessToken`/`PDDAccessToken`、登录接口族、token 刷新与 54001 挑战。
- 上传下载的**具体数据范围**，逐接口/逐字段列出。
- 全部风控代码：anti-token、enCryptInfoV3、scres、sdr、54001、root/模拟器/多开、
  设备画像、网络降级、Hook 对抗。
- **混淆闭包**：DEX 层与 51 个落盘 ELF（去重 48 个）的混淆手法逐库清点（三种标准
  变换，均可静态还原，含覆盖两个库、四掩码并集的异或字符串池）；`SoBuildInfo`
  清单中另有 54 个库在本设备从未下载，明确列为未覆盖项。
- 本地数据库格式与存储信息范围（含未 checkpoint 的 WAL）。

入口：

- [综合报告](report.md) — 总体结论与结论矩阵
- [网络与协议](network.md) — 拦截器链、域名、协议格式、长连接
- [认证机制](auth.md) — token 来源、登录接口族、刷新与挑战
- [上传下载范围](transfer.md) — 逐接口的字段与数据范围
- [风控机制](risk.md) — 全部风控组件与判定逻辑
- [混淆闭包](obfuscation.md) — DEX/Efix 跳板与逐库 native 混淆清点
- [算法与 native](algorithm.md) — `libpdd_secure.so` 常量表、JNI 导出、CFF 反扁平化
- [本地存储](storage.md) — 数据库格式、WAL、MMKV、动态库落盘
- [逆向证据](evidence.md) — 地址、哈希、符号与证据等级
- [分析完成度矩阵](completeness.md) — 逐项要求与覆盖证据

base APK SHA-256：`d57b1ebcd757207ad569233ec8d7cf663dd1acb69a2fde8915b58af0aa0d7c1c`。
