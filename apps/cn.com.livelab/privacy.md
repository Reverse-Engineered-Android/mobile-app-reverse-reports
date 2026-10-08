# 隐私与告知

## 1. 同意闸门

manifest 设置 `android:allowBackup="false"`、`android:usesCleartextTraffic="true"`、
`android:requestLegacyExternalStorage="true"`、
`android:appComponentFactory="com.secneo.apkwrapper.AP"`。

`usesCleartextTraffic="true"` 允许明文 HTTP；这与 API 主机使用 HTTPS 并不冲突，
属于全局放开而非端点强制。静态代码中出现的 `-dev` / `-uat` 主机族与
`http` 变体需按主机白名单分别评估。

## 2. 隐私政策与告知链

| 项 | 静态证据 |
|---|---|
| 隐私政策 URL | `problem.html#/problem/login`（PP `0x294d8`）、`agreeUrl` 字符串族 |
| 协议勾选 | `agree_agreement`、`agreeUrl` 字符串存在 |
| 逐项权限说明文本 | **APK 内未发现**；隐私政策正文来自服务端页面 |
| 厂商推送权限告知 | 13 个非 `android.permission.*` 权限，未见 APK 内说明 |

结论：静态分析**无法证明** 48 个去重权限已被逐项告知，属**告知粒度不足风险**，
不是“已证实未经告知”。

## 3. 采集项与触发条件

| 数据 | 触发条件 | 落点 | 是否观察到上传 |
|---|---|---|---|
| 位置 | 首页城市/广告上下文 | `pgc/advert/app/LocAndType/list` | 仅上下文字段 |
| 联系人 | 票夹持票人选择 | `frequentContactsIds` | 未观察到原始联系人上传 |
| 电话状态/设备信息 | 启动与请求头 | `deviceType`、`x-fwd-anonymousId` | 聚合设备字段 |
| 相机 | 实名/扫码 | `thirdParty/faceid/app/verify` | 活体照 |
| 图片/视频 | 头像、附件 | `thirdParty/qiniu/app/upload` | 直传 |
| 录音 | 无可见调用链 | — | 未观察到 |
| 日历 | 演出日历跳转 | `tearCalendar.html` | 未观察到日历条目上传 |
| 广告 ID | 未见可见调用链 | — | 未观察到 |
| 悬浮窗 | 未见调用闭环 | — | 未观察到 |
| 安装包 | 未见调用闭环 | — | 未观察到 |

## 4. 设备指纹与风控数据

Geetest 四代采集设备指纹并落 `shared_prefs/gt_fp.xml`（217 字节），
`shared_prefs/gt_core.xml`（514 字节）保存核心状态。友盟组件落
`umeng_zero_cache.db`、`sensorsdata`、`ua.db`、`accs.db` 等。这些是风控/统计
输入，客户端可见其存在与落盘，不可见服务端用途与保存期限。

## 5. 数据范围分层

| 层 | 内容 |
|---|---|
| 已证实采集 | 设备与网络上下文、位置上下文、相机图片、实名材料、票夹/订单字段 |
| 已证实上传 | 七牛图片、实名活体照、身份材料、下单联系人、收货地址 |
| 仅声明未见上传 | 联系人原始数据、日历、录音、广告 ID、悬浮窗、安装包 |
| 服务端不可见 | 保存期限、二次用途、风控评分、数据删除响应 |

## 6. 未经告知 / 超范围结论

- **未经告知**：不能静态判定为“已证实”，判定为**告知粒度不足风险**，因隐私政策
  正文不在 APK 内，13 个厂商/广告权限缺少 APK 内说明。
- **超范围获取**：未发现“采集即上传”的超范围闭环；但 6 个权限存在
  **过度声明**（见 `permissions.md` §6）。

未登录、未授权任何权限、未实际采集或上传数据。
