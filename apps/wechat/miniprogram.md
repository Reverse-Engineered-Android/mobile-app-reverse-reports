# 小程序静态提取与汉堡王点餐流程

## 结论

微信小程序的本地 `wxapkg` 可以在不执行、不注入、不修改手机数据的情况下
静态解包。对汉堡王点餐小程序样本的结论是：

1. **可以静态取得可执行发布包中的源码等价物。** 本次解出 133 个文件，
   包含 `app-service.js`、`page-frame.js` 和编译后的页面/组件 HTML/JSON；
   可以恢复路由、状态机、页面方法、API 名称、环境域名、支付与登录调用链。
2. **不能把发布包称作“完整原始工程源码”。** 包内没有原始 Vue/uni-app
   工程目录、构建配置、依赖锁文件和未经压缩的原始 WXML/Vue 文件；模板和
   JS 已经过 WCC/webpack 编译。准确表述是“静态恢复编译源码/可读业务逻辑”，
   而不是“拿到开发者原始仓库”。
3. **汉堡王点餐主链路已静态还原。** 门店选择 → 菜单/库存 → 购物车与优惠
   → 订单试算 → 创建订单 → 微信支付/支付回调 → 订单详情/成功页。
4. **登录和地址采集有明确的隐私入口。** 手机号登录使用
   `getPhoneNumber|agreePrivacyAuthorization`，并在成功后记录
   `privacyCollect.TYPES.PHONE`；配送地址成功保存后记录
   `privacyCollect.TYPES.ADDRESS`。本报告只确认登记调用点，不把它等同于
   上传前同意；运行时流量不在本轮范围内。
5. **本轮未执行真实登录、下单或支付。** 所有结论来自只读解包和静态分析，
   没有重放 API，也没有读取或发布真实手机号、地址、订单、支付或会员数据。
6. **同意与撤回链路在静态层可见，但撤回只证明清理了本地状态。**
   `getPrivacyConfig()` 读取 `isOpen` / `revokePolicyDescription` 决定是否展示
   个人信息列表入口与撤回文案；`revokeAgreement()` 清空 storage、清空
   globalData 中的会员字段并写入 `revoke` 标记后重新拉起首页。代码中**没有**
   可见的服务端撤回请求，因此不能据此断言服务端同意状态已被撤销。

## 样本与解包

| 项目 | 结果 |
| --- | --- |
| 本地缓存样本 | `_63336007_274.wxapkg` |
| 包大小 | 2,840,506 字节 |
| 包 SHA-256 | `b4cddbc3ddc207331699522d167afdc88624ab6ac4a674a14c7bd587fbacb3e5` |
| 解包文件数 | 133 |
| 解包总大小 | 2,831,841 字节 |
| `app-service.js` | 1,296,427 字节，SHA-256 `27182f8a0bb30aa3fd94c87de84831bd681dfa029185a606de8f435ae2287a5c` |
| `page-frame.js` | 1,486,443 字节，SHA-256 `63955b33893407e42c47892f7acdee026e2cbacaad87737186bb810681bc71c7` |
| 代码格式 | WCC/webpack 编译后的 JS、编译模板与组件配置 |

`page-frame.js` 与同一设备缓存中的
`page_scripts/c35c...-%2FpagesMenu%2Fpage-frame.js` SHA-256 完全一致，
说明静态解包得到的是运行时实际使用的发布内容。

`app-service.js` 中可识别 83 个 webpack 页面/组件 bundle。对发布 JS 做
格式化和模块拆分后，可以直接检查页面方法；另一份主 AppService 缓存是
V8 快照格式，使用只读 `strings` 提取环境域名、API 路径和配置键。

### 能恢复什么

- 页面路径、跳转参数和子包结构；
- 页面生命周期、computed/methods、购物车与订单状态机；
- 调用的 API 函数名、环境域名、业务路径；
- 登录、手机号、地址、支付、订阅消息、埋点调用点；
- 编译后的模板、事件绑定、可见文案和组件关系。

### 不能恢复什么

- 原始源码文件名、原始注释、构建脚本、依赖版本锁；
- 未编译的 Vue/SFC、原始 WXML/WXSS；
- 服务端源码、数据库和接口鉴权规则；
- 缓存缺失或被加密的独立分包。

因此，“能否静态获取源码”的答案是：
**能静态获取足够能分析业务的发布源码；不能恢复完整原始工程。**

## API 与环境

只读缓存字符串给出三套业务环境：

```text
https://dev-api.bkchina.cn
https://uat-api.bkchina.cn
https://bdp-api.bkchina.cn
```

业务前缀包括：

```text
/miniprogram  /openapi-ebuy  /mp-marketing  /mp-goods
/mp-order     /mp-sage       /mp-store      /mp-crm  /mp-auth
```

与点餐主链直接相关的路径包括：

| 阶段 | API/能力 |
| --- | --- |
| 门店 | `/stores/action/queryNearStoreList`、`/stores/action/queryByPage`、`/stores/action/queryStoreDetail` |
| 地理编码 | `/gaoDeMap/action/geo`、`/gaoDeMap/action/reGeo`、`/gaoDeMap/action/aroundPlace` |
| 菜单 | `/stores/action/queryStoreMenuPathV2`、`/menuDetailProducts/action/queryDetail` |
| 库存/价格 | `/storeSkuStocks/action/queryList`、`/skus/action/querySkuPrice` |
| 试算 | `/promotionEngine/action/orderCalculate` |
| 创建订单 | `/orders/action/createOrderV2` |
| 支付 | `/orders/action/orderPay`，随后调用小程序 `requestPayment` |
| 查询/取消 | `/orders/action/queryByPage`、`/orders/action/queryInvoiceUrl`、`/orders/action/cancelOrder` |
| 登录 | `/unifiedAuth/action/login`、`/unifiedAuth/action/getUserPhone`、`/unifiedAuth/action/validateCaptcha`、`/user/action/getVerCodeV2` |
| 隐私管理 | `/privacyManagementSettings/action/queryOne`、`/personalInfoCategorys/action/queryList`、`/personalInfoCollectionItems/action/queryList` |

只发布这些静态路径，不重放、不携带 token、cookie、手机号或订单参数。

## 汉堡王点餐流程

### 1. 选择用餐方式与门店

`pagesMenu/pages/menu/index` 接收 `type=eatIn|delivery` 等参数：

- 堂食进入 `eatIn`，外送进入 `delivery`；
- 使用附近门店、城市/地图搜索、门店详情或门店收藏；
- 外送还需读取/选择配送地址，堂食会检查门店是否支持
  `receiveDineInOrder`、营业状态和取餐时段；
- 门店详情、餐段、配送费、包装费和餐桌/取餐信息由独立 API 组合得到。

关键调用包括 `getNearlyStoreList`、`getreGeoInfo`、`queryStoreDetail`、
`queryStoreMealSectionTimeList`、`queryTakeoutStore`、
`queryStoreDeliveryFee` 和 `queryStorePackageFee`。

### 2. 加载菜单并选品

菜单页调用：

- `getMenuPageSetting` / `getMenuPageDecList`：菜单页设置和装修；
- `menuDetailProducts`：商品详情；
- `queryStoreSkuStocksList` / `skuQueryDetail`：SKU、库存和价格；
- `queryAddonSkuList`：加料/附加商品；
- `menuQueryDisabledList` / `queryDisabledAndUnEnabledList`：停售、
  限购和不可用商品；
- `menuCalculatePage`：购物车/优惠试算。

商品详情页支持套餐、加料、优惠卡、券和活动规则；页面间通过
`storeCode`、`type`、`cartCode`、`couponId`、`columnId` 等参数传递状态。

### 3. 购物车与优惠

购物车状态在页面/store 中维护，并调用 `modifyCart`、`cartList`、
`replaceCartList` 等方法。优惠相关包括：

- 券列表、券详情、适用门店；
- 国王卡/权益卡、两杯半价、多件折扣；
- `queryOfferRecCard`、`userBenefitCardList`、
  `getCouponsFerriQueryList` 等推荐与可用性校验；
- 优惠券、支付卡和商品活动在下单前重新试算。

### 4. 订单确认与创建

`pagesMenu/pages/order/orderConfirm` 组合门店、地址、时间、备注、餐具费、
优惠和支付卡，然后：

1. `memberQueryAccount` / `userBenefitCardList` 读取会员与可用权益；
2. `orderCalculatePage` 做最终金额试算；
3. `queryStoreDetail` 再次核验门店和用餐方式；
4. `createOrder` 对应 `/orders/action/createOrderV2`；
5. 成功返回 `orderId`、订单时间和支付信息。

外送订单在创建成功后记录 `privacyCollect.TYPES.ADDRESS`。这是隐私使用
记录调用点，不等于已经把地址上传到政策之外的位置。

### 5. 支付与结果页

订单创建成功后进入 `orderPay`。若返回微信支付 `package`，页面调用
`requestPayment`；成功回调再进入：

- `orderSucess`：订单号、订单时间、继续活动/优惠；
- `orderDetails`：订单状态、支付、取消、退款、发票和门店核验；
- `historyOrder`：分页订单列表。

支付失败、取消和超时都保留订单号并回到详情或重试，不静态假定支付成功。

### 6. 手机号登录与隐私

登录页根据 `canIUse("getPrivacySetting")` 在
`getPhoneNumber|agreePrivacyAuthorization` 与
`getPhoneNumber` 之间选择按钮 open-type。静态流程为：

1. 展示汉堡王隐私权政策链接和勾选状态；
2. 未勾选时提示“请先同意隐私协议”；
3. 获取手机号后调用 `getMPVipPhone` 或验证码登录；
4. 登录成功写入会员/签名缓存，记录
   `privacyCollect.TYPES.PHONE`，并发送 `user_agree_privacy_terms` 等埋点；
5. 页面还提供撤回同意、注销与隐私管理入口。

这能证明代码存在同意提示和隐私使用记录，但不能单独证明所有网络请求都
严格发生在同意之后。该边界与微信主应用的 [privacy.md](privacy.md) 一致。

### 7. 同意记录与撤回链路

| 环节 | 静态调用点 | 行为 |
| --- | --- | --- |
| 隐私设置读取 | `getPrivacyConfig()` → `getPrivacySetting({ body: { params: {} } })` | `code==0` 时取 `body.isOpen` 写入 `showPersonList`、`body.revokePolicyDescription` 写入 `revokeContent` |
| 个人信息入口 | `accountSetting` 渲染 `showPersonList ? i18n(index13) : null` | 入口是否出现由服务端 `isOpen` 控制 |
| 撤回提示 | `showRevokePop()` → `popup(revokeContent \|\| " ", "撤回同意隐私申请")` | 文案来自服务端 `revokePolicyDescription` |
| 执行撤回 | `revokeAgreement()` → `removeAllCache()` + `setStorageSync("revoke","revoke")` + `reLaunch("/pages/index/index")` | 重新进入首页 |
| 本地清理 | `removeAllCache()` → `clearStorageSync()`，并把 `miniOpenid`、`sageMemberCode`、`birthday`、`oldMemberCode`、`nickName`、`headImg`、`unionid`、`vipPhone`、`alipayUserId` 置空，写 `isLogout="Y"` | **只清本地** |

登录按钮的 open-type 按能力探测选择：

```text
canIUse("getPrivacySetting")
  ? "getPhoneNumber|agreePrivacyAuthorization"
  : "getPhoneNumber"
```

`privacyCollect.record()` 共 6 个调用点：

| 类型 | 调用位置 | 触发时机 |
| --- | --- | --- |
| `TYPES.PHONE` | `pagesMenu/pages/login/index` ×3 | 手机号/验证码登录成功后 |
| `TYPES.ADDRESS` | `pagesMenu/pages/address/detail` ×2 | 新增/保存地址成功后 |
| `TYPES.ADDRESS` | `pagesMenu/pages/order/orderConfirm` ×1 | 创建订单成功且 `salesScene==2`（外送）时 |

这些是**隐私使用记录**调用点，能证明“采集后做登记”，不能证明登记先于上传，
也不能证明登记结果被服务端接受。

### 8. 个人信息清单接口的证据边界

真机 V8 缓存的只读字符串提取给出三个接口：

```text
/privacyManagementSettings/action/queryOne
/personalInfoCategorys/action/queryList
/personalInfoCollectionItems/action/queryList
```

但在本次解包得到的 `app-service.js` 及其拆分模块中**没有找到这三个接口的
调用点**。缓存与解包包是两份不同产物（V8 快照与 WCC 发布包），因此只能确认
“设备缓存中存在这些接口名”，**不能**确认当前版本实现了个人信息清单页，也
不能确认它们被谁调用。缓存中的接口名不作为当前版本已实现个人信息清单页的证据。

## 隐私与安全观察

- 手机号、地址、订单和支付信息属于高敏感业务数据；本报告不包含真实值。
- 登录页把用户资料加密后写入本地 storage；这属于客户端混淆/存储保护，
  不应描述为已证明的强端到端加密。
- 埋点域包含 `atom-track.bkchina.cn`，但本轮没有抓包，不能确认每个事件
  是否携带手机号、地址或订单标识。
- 未发现静态命令注入、提权或绕过微信支付的证据；真实授权、签名和服务端
  校验仍由服务端控制。

## 复现

```bash
python3 tools/unpack_wxapkg.py _63336007_274.wxapkg unpacked --list
npx js-beautify -f unpacked/pagesMenu/pagesMenu/app-service.js \
  -o app-service.pretty.js
strings -a -n 4 app-service-cache > strings.txt
rg -n 'createOrder|orderPay|getPhoneNumber|privacyCollect|bkchina' \
  app-service.pretty.js strings.txt
```

复现只读取本地缓存，不连接业务 API，不执行小程序代码。
