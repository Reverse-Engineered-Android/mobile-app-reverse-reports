# 小程序静态提取证据

## 样本

| 文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| `_63336007_274.wxapkg` | 2,840,506 | `b4cddbc3ddc207331699522d167afdc88624ab6ac4a674a14c7bd587fbacb3e5` |
| `pagesMenu/pagesMenu/app-service.js` | 1,296,427 | `27182f8a0bb30aa3fd94c87de84831bd681dfa029185a606de8f435ae2287a5c` |
| `pagesMenu/pagesMenu/page-frame.js` | 1,486,443 | `63955b33893407e42c47892f7acdee026e2cbacaad87737186bb810681bc71c7` |

解包结果：133 个文件、2,831,841 字节。文件由 WCC/webpack 生成，包含
83 个可拆分的页面/组件 bundle、编译模板和组件 JSON。

同设备页面缓存
`page_scripts/c35c...-%2FpagesMenu%2Fpage-frame.js` 与解包后的
`page-frame.js` SHA-256 完全相同。

## 真机存储与命名对应

同一台真机的应用数据下，`MicroMsg/appbrand/pkg/` 共 **112 个 `.wxapkg`
普通文件**（`general` 96、`firstParty` 13、`commLib` 3）；按 appid 建立的
索引、包文件和 JS 缓存目录通过**同一版本序号**关联：

| 类型 | 模式 | 本样本 |
| --- | --- | --- |
| 索引 | `<appid>_<index>.idx` | `wx8d200a641cdec6a1_274.idx` |
| 包 | `_<crc>_<index>.wxapkg` | `_63336007_274.wxapkg` |
| 缓存目录 | `app-service.js_<appid>`、`<subpkg>_app-service.js_<appid>`、`precompile.<appid>` | `app-service.js_wx8d200a641cdec6a1` 等 |
| 页面脚本 | `page_scripts/<appid>-v<index>/` | 同序号 `274` |

由此可把 appid、版本序号、包文件和运行时缓存对应起来，**不需要运行小程序、
不需要抓包、不需要注入**。注意 `ls | grep wxapkg` 会把 `.wxapkg.zstd`
旁路文件一起计入；按普通文件计数才是 96/112。

链路与真机权限快照见 [`phone-runtime.md`](phone-runtime.md)。

## 环境与 API 字符串

主 AppService 缓存的只读 `strings` 提取给出：

```text
https://dev-api.bkchina.cn
https://uat-api.bkchina.cn
https://bdp-api.bkchina.cn

/unifiedAuth/action/login
/unifiedAuth/action/getUserPhone
/unifiedAuth/action/validateCaptcha
/user/action/getVerCodeV2
/stores/action/queryNearStoreList
/stores/action/queryStoreDetail
/stores/action/queryStoreMenuPathV2
/promotionEngine/action/orderCalculate
/orders/action/createOrderV2
/orders/action/orderPay
/orders/action/queryByPage
/privacyManagementSettings/action/queryOne
/personalInfoCategorys/action/queryList
/personalInfoCollectionItems/action/queryList
```

## 业务调用点

| 页面 | 静态调用 |
| --- | --- |
| `pagesMenu/pages/menu/index` | `getNearlyStoreList`、`queryStoreDetail`、`menuDetailProducts`、`queryStoreSkuStocksList`、`menuCalculatePage` |
| `pagesMenu/pages/order/orderConfirm` | `orderCalculatePage`、`createOrder`、`perpayOrder`、`memberQueryAccount` |
| `pagesMenu/pages/order/orderDetails` | `orderPay`、`getOrderDetails`、`cancelOrder`、`requestPayment` |
| `pagesMenu/pages/login/index` | `getVerCode`、`validateCaptcha`、`getMPVipPhone`、`loginByJverification` |
| `pagesMenu/pages/address/index` | `queryVipAddressList`、`deleteVipAddressOne` |

登录按钮静态使用：

```text
open-type=getPhoneNumber|agreePrivacyAuthorization
```

隐私使用记录调用点：

```text
privacyCollect.TYPES.PHONE   // 手机号登录成功
privacyCollect.TYPES.ADDRESS // 外送地址保存/订单创建成功
```

隐私政策链接：

```text
https://bdp-cdn.bkchina.cn/portal/privacy.html
https://bdp-cdn.bkchina.cn/portal/pdf/privacy.pdf
```

## 边界

- 未运行小程序，未调用业务 API，未执行登录、下单或支付。
- 未发布 token、cookie、手机号、地址、订单、支付、会员或真实网络负载。
- 缓存中出现的其他 appid 只作为跳转目标或缓存候选，不作为汉堡王主 appid
  的确定身份。
- 未解出原始工程、构建配置和服务端代码；结论仅覆盖本样本的发布包与缓存。
