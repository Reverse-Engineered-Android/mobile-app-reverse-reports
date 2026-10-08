# 完成度矩阵

| 要求 | 状态 | 证据 |
| --- | --- | --- |
| 主要网络交互流程 | ✅ | [network.md](network.md) §1-4 |
| 协议具体格式 | ✅ | [protocol.md](protocol.md) §1-5 |
| 认证机制 | ✅ | [auth.md](auth.md) §1-5 |
| 上传下载数据范围 | ✅ | [transfer.md](transfer.md) |
| 是否越权、提权 | ✅ | [privacy.md](privacy.md) §3.1-3.2 |
| 未经告知或超范围 | ✅ | [privacy.md](privacy.md) §3.3-3.4 |
| 完整逆向风控代码 | ✅ | [risk-control.md](risk-control.md) §1-8 |
| 不保留未分析清楚的加密/混淆 | ✅ | [risk-control.md](risk-control.md) §4、§8；[evidence.md](evidence.md) §3 |
| 手机端只读验证实际数据库格式 | ✅ | [database-inventory.md](database-inventory.md) |
| 搜索/直播/会员购/播放/缓存/互动静态调查 | ✅ | [network.md](network.md) §3 |
| 番剧 DRM 与高播放示例 | ✅ | [drm.md](drm.md) |
| 写入指定 GitHub 目录 | ✅ | `apps/tv.danmaku.bili/` |
| 不做实际测试 | ✅ | 全部网络、播放、上传、登录和 DRM 结论均来自静态/只读证据 |

## 静态不可达项

以下内容不作推测，也不伪装为结论：

1. 服务端对签名、指纹和风险字段的权重、阈值与处罚；
2. 远端 `dd.json` 同名开关的实时值；
3. 标题级 Widevine/Bilibili DRM 的当前授权状态；
4. 服务端留存期限、二次利用和跨产品共享；
5. 第三方 App ID/Secret 的服务端 scope 与是否发生实际越权。

## 版本约束

报告对应 `tv.danmaku.bili 9.13.0 (9130500)`。不同版本的权限、接口、风控字段、
DRM 策略、远端开关和混淆布局可能变化；引用时必须注明样本版本和哈希。
