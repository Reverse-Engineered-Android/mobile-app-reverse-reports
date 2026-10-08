# 网络与协议

## 1. 出口与分派

业务请求全部由 Dart 快照中的公共拦截器改写，`libapp.so` 中的调用链为：

```
业务闭包 -> gXa.xhd (0xb50a14)
             -> PP 0x186e0 载入 SXa 闭包地址 (0xb50b04)
             -> SXa.xhd (0xb50b04)
                  写 x-fwd-anonymousId / platform-type / platform-version
                  -> bl 0xb4ff88 (公共请求处理)
             -> 按 URL 类型进入 gXa.yhd / gXa.slc
```

- `gXa.yhd` 起始 `0xabc438`：处理 `reLogin`、`ignoreRespError` 与超时/异常文案。
- `gXa.slc` 起始 `0xabd488`：成功分支解包 `code/msg/data`。

`0xb50a14` 的分派条件是 `ldur w1,[x0,#0x17]` 与 `ldur w4,[x3,#7]` 两处类型字，
命中后经 `bl 0x6e3400`、`bl 0x6e31ec`、`bl 0xccf154` 组装，再经
`PP 0x14f18` 取出目标闭包。

## 2. 主机与实时通道

| 用途 | 值 |
|---|---|
| 生产 API | `https://api.livelab.com.cn/` |
| 非生产 API | 同域 `-dev` / `-uat` 变体 |
| 实时 | `wss://ws.livelab.com.cn/stomp`、`/team` |
| 小程序静态资源 | `miniapp.livelab.com.cn` |
| 传输 | Dart `http` / STOMP over WebSocket；`tnet`（`libtnet-3.1.14.so`）供 SDK 侧通道 |
| 缓存 | `DioCache.db` |

## 3. 功能链路静态接口

### 3.1 搜索

| 路径 | PP 偏移 | 说明 |
|---|---|---|
| `search/app/search/searchV2` | `0x21510` | 主搜索 |
| `search/app/search/perform` | `0x214c8` | 演出搜索 |
| `search/app/search/ds` | `0x20d98` | 达人/权益搜索 |
| `search/app/search/text` | `0x84d0` | 文本搜索 |
| `search/appHotWords/app/queryHotWords` | `0x5c9b8` | 热词 |
| `appShow/hotSearch/app/listSearchBanner` | `0x13bb8` | 热搜 Banner |
| `appShow/hotSearch/app/rankList` | `0x417d8` | 热搜榜 |

`searchV2` 返回族字段（PP 池相邻常量）：`displayPrice`、`platformProductCode`、
`artistPerformanceInfoVo`、`appWishesCityVo`、`artistCardVo`、`enableWishes`、
`artistId`、`fanName`、`fansCount`、`followType`、`picUrl`、`cityVos`、
`wishesTotal`、`wishesSuccessCity`。

### 3.2 活动 / 购票

| 路径 | PP 偏移 |
|---|---|
| `performance/app/project/appoint/v3` | `0x26df0` |
| `order/app/center/v3/create` | `0x25dc0` |
| `pay/app/payCloud/v4/pay/prePayInfo` | `0x25ef8` |
| `performance/app/ticket/seat/token` | `0x1aa18` |
| `performance/app/project/seatPlanStatus` | `0x1a368` |
| `marketing/app/exclusive/activity/getSeatPlanList` | `0x1a018` |
| `order/app/center/team/ticket/create` | `0x37958` |
| `performance/app/order/detail` | `0x34208` |
| `pay/app/payCloud/v5/prePay/standby` | `0x23858` |

预约接口 `performance/app/project/appoint/v3` 的请求体由 PP `0x26df8`
的定长记录描述：字段槽 `LVc` / `XMf` / `lci` / `options`，与失败文案
`预约失败，请重试`（PP `0x26de0`）同池，标识 `TicketOnSaleReservation`
（PP `0x26de8`）。

### 3.3 票夹 / 票据

| 路径 | PP 偏移 |
|---|---|
| `performance/app/ticket/list/v2` | `0x33a88` |
| `performance/app/ticket/detail/v2` | `0x3a480` |
| `performance/app/ticket/detail/v3/orders` | `0x3a290` |
| `performance/app/ticket/ticketInfo` | `0x23d28` |
| `performance/app/ticket/ticketAndSeatInfo` | `0x24170` |
| `performance/app/order/list` | `0x5ae08` |
| `performance/app/ticket/present/list` | `0x392f8` |

`list/v2` 同池含 `msgActualPublishTime`、`msgRoute`、`msgRouteParameter`、
`msgRouteType`、`msgSubtitle`（PP `0x33aa8`–`0x33b40`），即票夹条目带跳转路由。

### 3.4 支付 / 发票

`pay/app/payCloud/v4/pay/prePayInfo`、`order/app/center/v3/create`、
`performance/app/order/getOrderPrePaymentDialog`（`0x40b18`）、
`bff/member/invoice/v2/getInvoiceUrl`（`0x413f0`）、
`bff/member/invoice/v2/sendEmail`（`0x413a0`）、
`member/invoiceRecord/app/list`（`0x33a68`）。

## 4. 请求头

| 头 | PP | 构造地址 |
|---|---|---|
| `Authorization` | `0x188b0` | `0xb4fed0` |
| `Bearer ` 前缀 | `0x188d8` | `0xb4fe80` |
| `x-fwd-anonymousId` | `0x186e8` | `0xb50ba4` |
| `platform-type` | `0x186f0` | `0xb50be4` |
| `platform-version` | `0x186f8` | `0xb50c14` |
| `x-fwd-ts` | `0x25db8` | 同族请求改写 |
| `accept-version` | `0x27a58` | 同族请求改写 |
| `global-context` | `0x47a70` | 同族请求改写 |
| `cookie` / `set-cookie` | `0xd120` / `0xd140` | 响应头读取 |

`nonceStr`（`0x40e98`）、`timeStamp`（`0x40ec0`）、`signType`（`0x40f28`）
是签名族参数名；`buildSignature`（`0x24818`）位于 `package_info` 插件字段池，
不是请求签名字段。

## 5. 响应封装与错误

```json
{"code":10000,"msg":"操作成功","data":...}
```

`gXa.yhd` 判定顺序（按常量池出现次序）：`reLogin` → `ignoreRespError` →
`网络连接超时` → `服务器返回错误` → `App请求超时` → `接收数据异常` →
`网络请求异常`。`reLogin` 命中走重新登录事件（PP `0x11868` / `0x118b8`）。

## 6. 实时通道

`wss://ws.livelab.com.cn/stomp` 承载站内推送，`/team` 承载组队购票。组队族
路径：`marketing/app/teamTicketQueue/`（`0x262a0`）、`detail/`（`0x26cc8`）、
`getCaptainQueue`（`0x260b8`）、`unlockSlot`（`0x37428`）、
`updateQueueMemberInfo`（`0x374a0`）、`disband/`（`0x375e0`）、
`project/memberInTeam`（`0x26f28`）、`getUnPayOrder`（`0x27df0`）。

## 7. 静态化说明

以上全部来自 `libapp.so` 快照池与反汇编，未构造请求、未登录、未下单、未支付、
未实际建立 WebSocket。接口的可用性、限流与服务端校验不可由客户端静态分析断定。
