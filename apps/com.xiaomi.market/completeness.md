# 完成度矩阵

| 主题 | 状态 | 依据 |
|---|---|---|
| APK 版本、哈希、Manifest 统计 | 已完成 | APK SHA-256、Manifest 解析 |
| 是否上传本机 app 列表 | 已证实 | V1/V2 参数构造、更新检查、活跃统计、AI 请求、OneTrack |
| 自动扫描触发与调度 | 已证实 | `AutoScannerManager`、`AutoScannerJobService`、设备 job #730 |
| V2 上传字段 | 已证实 | `packageName`、`versionCode`、`signatureMD5` |
| 风险判定位置 | 服务端判定已证实 | 响应模型与设备返回；服务端内部算法未复原 |
| 客户端评分公式 | 结构已证实 | `AppSecurityCheckRules` |
| 检测结果逐包回传 | 未发现专用端点 | 全局端点/字符串/调用点审计；不等价于服务端无关联能力 |
| 忽略操作是否上传 | 未发现 | 只写入本地 set |
| 风险通知特征上报 | 已证实 | `RiskNotificationModelManager` |
| OneTrack 传输格式 | 结构已证实 | GZIP + AES/ECB/PKCS5、key 交换 |
| 验证 SDK 环境上传 | 已证实字段面 | `SensorHelper`、`CheckHook`、`CheckVirtual` |
| 隐私开关影响范围 | 部分证实 | `PkgUtils` 主入口已证实；安全扫描链路是否全停需动态验证 |
| 服务端风险库与留存 | 未复原 | 客户端不可见 |
| AI 广告请求完整序列化 | 部分未复原 | `ClientAIRepo.fetchAdCandidate()` 反编译失败，但请求对象字段完整 |
| 证书/密钥值 | 不公开 | 发布边界排除 |
| 真实包名列表/用户标识 | 不公开 | 仅保留结构、聚合与服务端返回示例 |

## 明确未复原项

1. `securityDetect` / `securityDetectV2` 服务端如何生成 `riskAppList`；
2. 服务端是否将每日清单与风险库做长期设备画像；
3. `ClientAIRepo.fetchAdCandidate()` 的完整实现；
4. 动态请求中服务端可能附加的未在静态代码中出现的字段；
5. 所有需要运行时系统服务权限的路径在异常/未授权设备上的实际行为。
