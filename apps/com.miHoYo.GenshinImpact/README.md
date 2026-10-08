# 原神 Android 7.1.0（versionCode 1242）

样本包名 `com.miHoYo.GenshinImpact`，APKM 内 APKM 文件版本名
`7.1.0_48052158_48145775`、versionCode `1242`，minSdk/targetSdk 为
23/36。报告采用 APKM、5 个 DEX、24 个 ARM64 native 库、741 个
asset 文件、80.75 MB IL2CPP metadata 和二次反编译结果进行静态调查。

## 核心结论

| 主题 | 静态结论 |
| --- | --- |
| 网络 | 账号/配置/上报走 HTTPS JSON；游戏实时链路为 UDP socket、KCP 与带 4/12 字节头部、4/12 字节 nonce 和认证标签的 AEAD 帧；未发送网络流量 |
| 认证 | Retrofit JSON + `DS` 签名，签名格式为 `t,r,MD5(salt=&t=&r=&b=&q=)`；cookie 按 SToken/CToken/LToken v1/v2 组装 |
| 风控 | SmartCaptcha/Aigis、`x-rpc-risky`、risk ticket、年龄门、设备指纹、Root/模拟器/ADB/代理/Xposed、黑名单与设备限制 |
| 上传 | 登录诊断、设备基础数据、设备指纹、归因/遥测/日志；具体字段和哈希/HMAC 方式见 `transfer.md` |
| 下载 | 配置、ABTest、语言包、字体、热修、资源 CDN、活动配置和设备指纹扩展配置 |
| 越权/提权 | `GameStateService` 为无权限保护的 exported Binder；10 个命令可被本机任意应用调用，其中 OEM 通知命令另有白名单，其余无调用方身份校验。未证明 Android UID/系统权限提升 |
| 告知 | Firebase analytics/ad consent 默认拒绝，`skipConsent()` 例外自动授予；设备指纹字段明显超出最小统计范围，是否实际发送仍需服务器侧确认 |
| 客户端 | Unity/IL2CPP 游戏进程、Combo SDK Activity、AIDL `GameStateService`、Animage StateMachine、AssetBundle/SerializedFile、Vulkan/GLES 与 GXM/Filament 类命令缓冲渲染链 |

## 文件

- [最终报告](report.md)
- [网络与协议](network.md)
- [认证](auth.md)
- [上传下载范围](transfer.md)
- [风控机制](risk.md)
- [权限与导出面](permissions.md)
- [隐私与告知](privacy.md)
- [客户端、GUI、状态机与渲染](client.md)
- [证据索引](evidence.md)
- [完成度](completeness.md)

## 研究边界

只做本地静态只读调查；不登录、不访问服务端、不运行游戏、不绕过风控，
不发布 APK、native 库、IL2CPP 元数据、完整反汇编或美术素材。所有密钥、
签名盐、设备标识和真实请求材料均不在公开报告中出现。静态可证明的是
“客户端具备某字段、代码路径或接口”，不把它表述成“服务端已经接收”。
