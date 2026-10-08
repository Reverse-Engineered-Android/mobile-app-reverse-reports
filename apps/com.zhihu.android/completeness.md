# 完成度矩阵

## 1. 目标覆盖

| 要求 | 状态 | 对应章节 | 说明 |
| --- | --- | --- | --- |
| 主要网络交互流程 | ✅ 已完成 | [network.md](network.md) §1-4 | 拦截器链、URL 构造、头映射 |
| 协议具体格式 | ✅ 已完成 | [network.md](network.md) §4-5、[risk.md](risk.md) §6 | JSON 信封、签名串、加密报文、protobuf 埋点 |
| 认证机制 | ✅ 已完成 | [auth.md](auth.md) | `Authorization` 两分支、CloudID 签名、udid |
| 上传下载的具体数据范围 | ✅ 已完成 | [transfer.md](transfer.md) | 图片/视频/对象上传、下载/缓存、本地跟踪表 |
| 是否越权 | ✅ 已完成 | [permissions.md](permissions.md) §4 | 无系统提权、无跨应用越权 |
| 是否提权 | ✅ 已完成 | [permissions.md](permissions.md) §4 | 无用 root、无隐藏 API 提权链 |
| 是否超范围获取数据 | ✅ 已完成 | [privacy.md](privacy.md) §3.1-3.2 | 应用列表枚举 + 上传超出最小必要 |
| 是否未经告知 | ✅ 已完成 | [privacy.md](privacy.md) §4 | 无完全无告知通道；概括授权 + 服务端遥控 |
| **完整逆向所有风控代码** | ✅ 已完成 | [risk.md](risk.md) | 7 层全部映射，精确代码与判定依据 |
| **不允许保留未分析清楚的加密/混淆代码** | ✅ 已完成 | [risk.md](risk.md) §6、[evidence.md](evidence.md) §4 | 算法选择表 + 白盒边界，65 个 jadx 失败已逐一归因 |
| 手机端数据验证实际数据库格式 | ✅ 已完成 | [storage.md](storage.md)、[evidence.md](evidence.md) §6 | 只读核对 42 库，`integrity_check` 全 ok |
| 静态只读调查搜索 | ✅ 已完成 | [network.md](network.md) §5.1 | `/search_v3`、`/search/tabs`、`/search/customize` |
| 静态只读调查推荐 | ✅ 已完成 | [network.md](network.md) §5.2 | `/topstory/recommend`、`/feed-root/*` |
| 静态只读调查浏览问题与回答 | ✅ 已完成 | [network.md](network.md) §5.3 | `/v4/questions/{id}/answers`、`/v4/answers/{id}` |
| 静态只读调查提交回答 | ✅ 已完成 | [network.md](network.md) §5.3、[transfer.md](transfer.md) §2 | `POST /answers`、草稿、定时、匿名 |
| 静态只读调查阅读盐选会员限定内容 | ✅ 已完成 | [network.md](network.md) §5.5、[storage.md](storage.md) §2.7 | `kvip/content/products/**`、`manuscript_html` 缓存 |
| 列出有 DRM 与无 DRM 的番剧示例 | ✅ 已完成（并给出结论） | [report.md](report.md) §7 | 知乎无动漫/番剧目录；给出公开 ZVideo 榜与盐选付费榜，DRM 能力逐项列出 |
| 不做实际测试 | ✅ 遵守 | 全文 | 仅静态反编译 + 只读公开 GET + 设备端只读核对 |
| 报告写到 GitHub 指定目录 | ✅ 已完成 | 本目录 | `apps/com.zhihu.android/` |

## 2. 各章证据等级

| 章节 | 已验证 | 结构已证实 | 假说 |
| --- | --- | --- | --- |
| network | URL/头/签名串/端点计数 | SecurityGuard 返回字节格式 | 无 |
| auth | `Authorization` 两分支、CloudID 7 参数、udid 端点 | CloudID 动态下载 native 段 | 服务端令牌策略 |
| transfer | 端点/参数/本地表 | OSS/ZOS 授权边界 | 服务端存储策略 |
| risk | 全部常量/分支/置换表/native 地址 | SecurityGuard/Sword 内部 | 服务端评分与阈值 |
| permissions | 66 权限、412 组件、61 导出 | `DebugPatchReceiver` 可达性 | 无 |
| privacy | 弹窗/端点/SP 键名/枚举代码 | 服务端采样率取值 | 留存时长 |
| storage | 文件清单/integrity/DDL/行数/键名 | 库清理周期 | 行级内容 |
| evidence | 哈希/工具链/计数 | 加固库内部 | 无 |

## 3. 静态分析不可达部分

以下**明确超出客户端静态分析能力**，报告不做推测：

1. 服务端对 `X-Zse-96`、RUID、`x-at-df-if` 的校验权重与阈值。
2. 服务端风控评分模型、封禁时长与处罚梯度。
3. ZA/APM 事件在服务端的留存周期与二次利用。
4. `appListPermissionEnabledRatio` 等远端采样率的线上实际值。
5. CloudID 动态下载 native 段的内部算法（加固库）。
6. OGV/剧集目录的实际内容清单（接口需应用鉴权，公开不可列举）。

## 4. 加固与混淆覆盖

| 组件 | 处理方式 | 覆盖度 |
| --- | --- | --- |
| `libbangcle_crypto_tool.so` | `objdump -d` 全函数 + 跳转表定位 | **完整**（算法选择/置换表/填充全复原） |
| `libsgmainso-5.6.230509.so` 等 SecurityGuard | 文件头/版本/符号/调用点归类，不逐函数反汇编 | 契约级（入口/参数/用途） |
| `libbdsword.so`、`libbdauthorization.so` | 符号 + Java 侧调用点 | 契约级 |
| `libzxprotect.so` | `com.zx.sdk.api.ZXManager` 入口 | 契约级 |
| `libDexHelper.so`、`libdexvmp.so` | 加载链 + 云控配置 | 机制级 |
| Robust（`PatchProxy`/`ChangeQuickRedirect`） | 反编译已展开为真实逻辑 | 完整（不影响语义读取） |
| jadx 65 个失败方法 | 逐一归因（Lambda/合成/switch 告警） | 无未解释加密方法 |

## 5. 版本约束

本报告对应 **知乎 11.10.0 (versionCode 41012)**，安装于 2026-09-30。
不同版本的 API 清单、远端开关默认值、权限声明、native 库版本与 DRM 策略
可能有差异；引用本报告时请注明版本与 SHA-256。
