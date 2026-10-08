# 上传下载范围

## 1. 上传接口

| 路径 | PP 偏移 | 数据范围 |
|---|---|---|
| `thirdParty/qiniu/app/upload` | `0x1d3c0` | 图片/附件直传七牛 |
| `thirdParty/faceid/app/verify` | `0x1b0b8` | 实名活体照 `verify.jpg`（`0x1b0d8`） |
| `member/authInfo/app/identity/authentication` | `0x1c528` | 身份认证材料 |
| `member/authInfo/app/identity/changeBind/verify` | `0x1bfc0` | 换绑验证材料 |
| `performance/app/order/contactInfo` | `0x1f738` | 下单联系人 |
| `performance/app/orderContactInfo/update` | `0x3a948` | 联系人更新 |
| `member/app/address` / `member/app/address/list` | `0x1f3f8` / `0x1e640` | 收货地址 |
| `bff/member/app/personal/send` | `0x1bd18` | 个人资料提交 |

订单/票夹侧上传字段（PP 池同邻域）：`consigneeName`（`0x1e3c0`）、
`consigneeAddress`（`0x1e3c8`）、`idCardNum`（`0x23038`）、
`frequentContactsIds`（`0x26d80`）、`phone_number`（`0x1ea50`）。

## 2. 下载 / 取回接口

| 路径 | PP 偏移 | 数据范围 |
|---|---|---|
| `thirdParty/qiniu/app/getPrivatePdfUrl` | `0x54298` | 私有 PDF 取回 |
| `bff/member/invoice/v2/getInvoiceUrl` | `0x413f0` | 发票文件 URL |
| `bff/member/invoice/v2/sendEmail` | `0x413a0` | 发票邮件投递 |
| `app/homepage/cityModule.json` 等 | `0x280c0`–`0x28130` | 首页配置 |
| `app/homepage/tabs/1.json`、`tabs/2.json` | `0x40820` / `0x40818` | 首页页签配置 |
| `app/static/config/orderTabsConfig.json` | `0x29170` | 订单页签配置 |
| `app/project/get_project_info/` 等 | `0x426e8`–`0x42708` | 演出详情 |
| `bff/project/app/project/getCdnUrl` | `0x31538` | CDN 地址换取 |
| `marketing/app/exclusive/activity/detail/getCdnUrl` | `0x2dd08` | 活动 CDN 地址 |

随包静态资源：`assets/gt4.js`、`assets/gt4-index.html`、`assets/gt4-loading.gif`、
`assets/flutter_assets/`、`assets/mlkit_barcode_models/`。

## 3. 静态范围判断

- **明确会上传**：头像/图片（七牛）、实名活体照、身份材料、下单联系人、收货地址。
- **明确会下载**：私有 PDF、发票文件、首页/演出/订单配置 JSON、CDN 资源。
- **未发现上传闭环**：位置、通话记录、日历、麦克风、安装包等权限未观察到
  汇入上述任一端点的调用链。

未实际上传或下载任何业务文件；未构造请求、未登录、未触发支付。
