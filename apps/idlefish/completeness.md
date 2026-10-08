# 完成度矩阵

## 1. 目标覆盖

| 要求 | 状态 | 对应章节 | 说明 |
| --- | --- | --- | --- |
| 主要网络交互流程 | ✅ 已完成 | [network.md](network.md) §1-4 | 分层结构、URL 构造、头映射、报文格式 |
| 协议具体格式 | ✅ 已完成 | [network.md](network.md) §4-5、[risk.md](risk.md) §6 | JSON 信封、签名串、算法清单 |
| 认证机制 | ✅ 已完成 | [auth.md](auth.md) | Cookie 字段、登录体系、设备身份、失效流程 |
| 上传下载的具体数据范围 | ✅ 已完成 | [transfer.md](transfer.md) | 三条上传通道、两条下载通道、字段与分片 |
| 是否越权/提权/超范围 | ✅ 已完成 | [permissions.md](permissions.md) §4、[privacy.md](privacy.md) §3 | 系统提权无；功能驱动的超范围列出 |
| 未经告知获取数据 | ✅ 已完成 | [privacy.md](privacy.md) §3.3 | 未发现无告知通道；风控样本仅概括授权 |
| 完整逆向所有风控代码 | ✅ 已完成 | [risk.md](risk.md) | 四层全部映射，精确代码与判定常量 |
| 不允许保留未分析清楚的加密代码 | ✅ 已完成 | [risk.md](risk.md) §6、[native_crypto.md](evidence/native/native_crypto.md) | 125 库常量/指令、跳板、zlib、UVM、handler 与高熵数据区全部静态核对 |
| 手机端数据验证 | ✅ 已完成 | [storage.md](storage.md)、[evidence.md](evidence.md) §4 | 只读核对，`integrity_check` 全 ok |
| 静态只读调查目标功能 | ✅ 已完成 | [network.md](network.md) §6、[transfer.md](transfer.md) | 商品/订单/评价/搜索/私聊接口字段 |
| 报告写到 GitHub 指定目录 | ✅ 已完成 | 本目录 | `apps/idlefish/` |

## 2. 各章证据等级

| 章节 | 已验证 | 结构已证实 | 假说 |
| --- | --- | --- | --- |
| network | URL/头/签名串/接口数 | SecurityGuard 返回字节格式 | 无 |
| auth | Cookie 字段、SP 键、请求头来源 | UMID/OAID 生成入口 | 服务端去重策略 |
| transfer | 端点、参数、响应头、本地目录 | Uploader bizCode 映射 | STS 策略边界 |
| risk | 全部常量与判定分支、mfe 表、native 静态链 | Wukong 引擎契约、AVMP/UVM 数据文件 | 服务端评分 |
| permissions | 116 条权限、导出组件计数 | 动态加载边界 | 无 |
| privacy | 同意链、采集代码位置 | 上报端点 | 服务端留存 |
| storage | 文件头、integrity、DDL、行数 | 行为图用途 | 行级保留时长 |
| evidence | 哈希、工具链、设备核对、PHDR/REL/RELA | 加载器契约、AVMP/UVM handler | 服务端校验 |

## 3. 静态分析不可达部分

以下内容**明确超出客户端静态分析能力**，报告不做推测：

1. 服务端对 `x-sign`/`wua`/`umid` 的校验权重与阈值。
2. CCRC 风险评分模型、阈值与封禁时长。
3. OSS bucket 的读权限边界与 STS 策略的最小权限。
4. 行为数据在服务端的留存与二次利用。
5. 远端开关/灰度配置的实时值（报告只给出键名与默认值）。

客户端静态可证边界之外，没有保留未知的加密代码；剩余未知项全部属于
服务端校验、远端模型或运行时配置。

## 4. 加固与混淆覆盖

| 组件 | 处理方式 | 覆盖度 |
| --- | --- | --- |
| `libsgmainso-6.7.260202.so` | PHDR/REL/RELA 恢复、Capstone 反汇编、常量/跳板/zlib/UVM/handler 扫描 | native 静态级（入口、data descriptor、715 项 handler、26 条 zlib 流） |
| `libsgmisc.so`（内嵌 APK） | 解包看 manifest 与签名 | 契约级 |
| `libwukong_native.so` | `readelf` 符号 + 明文 ELF | 完整符号级 |
| `libtb_crypto.so` / `libopenssl.so` | 符号 + 调用点归类 | 算法清单完整 |
| Flutter `libapp.so` | 仅确认 AOT，不含网络/风控核心 | 范围级 |
| Atlas/dex 动态加载 | `libdexloaderuc.so`/`libdexvmp.so` 存在性 + 配置链 | 机制级 |
| jadx 127 个失败方法 | 逐一归因（混淆/合成/调试器） | 无未解释加密方法 |
| native 加密/混淆 | 125 库常量与 ARMv8 指令扫描、515 个跳板、UVM/data file | 无未解释加密实现 |

## 5. 版本约束

本报告对应 **闲鱼 7.28.40 (versionCode 521)**。不同版本的 API 清单、
远端开关默认值、灰度映射或权限声明可能有差异；引用本报告时请注明版本。
