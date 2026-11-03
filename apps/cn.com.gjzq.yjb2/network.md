# 网络与协议

## 1. 出口与分派

本样本是**壳 + 全量 H5** 结构：`classes.dex` 只有 `13,644` 字节，业务代码几乎全部
位于 `assets/yjb.zip`（`186,793,631` B，`2,843` 条目）与 `assets/portal_yjb.zip`
（`15,996,889` B，`542` 条目）。因此网络面由 H5 决定，而不是 DEX 里的
OkHttp/Retrofit 路由——`classes.dex` 中没有业务网络栈。

H5 的出口按宿主分成三层，全部静态可枚举：

| 层 | 触发者 | 出口 |
|---|---|---|
| 交易/YJBAPI（主通道） | `fetchYJBAPI` → `YJBExec(1001, …)` | **由 native 代发**，H5 不直接持有该连接 |
| 资讯/评论/广告（浏览器通道） | `Tool.fetchToIAR` / `@sinolink/http` | `https://webapps.yongjinbao.com.cn/yjbwebservice/core/api[/json]` |
| 资讯直连（GET 路径） | `Tool.fetchTo(…, Interface)` | `stockInformationSite` / `stockInformationSiteBak` |

环境切换由 `env.js`（`ycomment/common/vendors/env.js`）按 URL 正则决定：

| 触发 | `curOriginStr` | `loginOrigin` |
|---|---|---|
| 非 `dev`/`fz`/`localhost` | `https://operating.yongjinbao.com.cn` | `https://webapps.yongjinbao.com.cn` |
| `dev` | `https://devwebapps.yjbtest.com` | `https://devwebapps.yjbtest.com` |
| `fz` | `https://fzoperating.yjbtest.com` | `https://fzwebapps.yjbtest.com` |

`BASE_API = ${originStr}/yjbwebservice/core`，`window.Login_API_URL` /
`login_URL = ${loginOrigin}/yjbwebservice/core/`。`stockInformationSite` 取
`envConfig.STOCKINFORMATION_SERVER_URL`（本地调试为 `…/phpservice/stockInformation/`），
`stockInformationSiteBak` 取 `envConfig.STOCKINFORMATION_ALIYUN_SERVER_URL`，
即阿里云 OSS 静态域（`xbl-…-yjb-pic.oss-cn-shanghai.aliyuncs.com`）；
`Tool.fetchTo` 只在传入 `Interface` 表时才把 `url` 拼成
`stockInformationSiteBak + Interface[UniqueKey]` 并标记 `isDirect`。

## 2. 两层 RPC 网关

同一个方法名在两条链路上走不同的封装。二者**不是**同一协议的两个版本，
而是「native 代发」与「浏览器直发」两种实现并存。

### 2.1 Native 层：`YJBExec(1001, options)`

`treasurybonds/build/index.js` 的 `fetchYJBAPI`（偏移 `0x3116a`）构造
`options`，`_fetchYJBAPI`（偏移 `0x319c5`）删除业务字段后调用
`YJBExec(1001, options, cb)`：

```js
var options = { functionId: args.functionId,
                subSystemNo: args.subSystemNo,
                tradeType: args.tradeType };
delete args.functionId; delete args.subSystemNo;
delete args.tradeType;  delete args.serviceName;
options.param = args;                       // 其余字段整体进 param
YJBExec(1001, options, function (result) { … });
```

判成功需要**两级**都为 0：

```js
+result.code === 0 && +result.result.errorNo === 0
  → var data = result.result.data;          // 字符串
    var oDataResult = JSON.parse(data);
    +oDataResult.code === 0 → resolve({ result: data })
```

即 native 返回 `{code, result:{errorNo, errorInfo, data}}`，`data` 是**被再包一层
JSON 字符串的业务响应**。业务 `code` 非 0 时走 `saveLog({event_id:"3005031"})`
并弹错；`permitType:true` 时跳过内层 `code` 判定（风险中心/权限类接口用这个口子
拿原始串）。`functionId:1000` + `serviceName` 是「按服务名调用」的变体，
`functionId:1001` 是 YJBAPI 自身。上述两级判定在全部 70 个交易模板中一致。

### 2.2 浏览器层：`/core/api` 与 `/core/api/json`

`stockInformation/common/vendors/Tool.js`（`const IAR_INTERFACE` 在 `0x2a`，
`fetchToIAR` 在 `0x2116`）。默认 `API_URL` 为
`window.envConfig.NODE_URL || "https://webapps.yongjinbao.com.cn/yjbwebservice/core/api"`。

```js
const iarConfig = IAR_INTERFACE[param.UniqueKey];   // 短码 → 服务名
param.UniqueKey = iarConfig.UniqueKey;
url = iarConfig.type == 'json' ? `${API_URL}/json` : `${API_URL}`;
type = iarConfig.method || 'POST';
contentType: "application/x-www-form-urlencoded;charset=utf-8"
xhrFields: { withCredentials: false }      // 超时 settings.ajax_time_out = 30000
```

`IAR_INTERFACE` 有 **28** 个短码 → `serviceName.*` 全名（24 个 form、4 个
`type:"json"`，全部 `POST`）。短码本身不携带语义，映射表是唯一真值，例如
`NAQ → serviceName.news.stock.news.narlistinfo.query`、
`SHFP → serviceName.comarket.dwapi.stockholder.fund.page`（json）、
`ANVS → serviceName.comarket.news.scroll`（json）。请求会额外回填
`localStorage.STOCKINFORMATIONPHPSESSID`（存在时）。

`stockInformation/common/vendors/interface.js`（`const Interface` 在 `0x0`）是
**第二张表**，**65** 个短码 → **GET 路径后缀**（不是服务名），配
`fetchTo` 直连阿里云静态域使用，例如 `CMTQ → /news/stock/capital/margin/trade/query`、
`CLTQ → /news/stock/capital/large/trans/query`、
`SHTQ → /news/stock/share/holder/tenshareholders/query`、
`NFRHTT → /news/f10/reits/holder/top/ten`。

### 2.3 `subSystemNo` 分域

`subSystemNo` 决定 native 侧把请求路由到哪个子系统，`tradeType` 决定普通/信用：

| `subSystemNo` | 语义（由同族参数与函数名归纳） | `tradeType` | 静态出现次数 |
|---|---|---:|---:|
| `103` | 普通交易（现货/港股通/组合/国债逆回购） | `1` | 686 |
| `112` | 融资融券（信用） | `2` | 81 |
| `519` | YJBAPI 业务总线（`serviceName.*`、SAS/SSO、权限、协议、公告） | `1`/`2`/缺省 | 911 |
| `200` | 资讯行情（`functionId 1004/1006`） | 缺省 | 8 |

出现次数为解密后 507 个 JS 中 `subSystemNo:` 字面量的静态计数。

### 2.4 `functionId` 族

在 507 个 JS 里静态抽出 **70** 个交易参数模板（`paramNNN = {functionId,
subSystemNo, tradeType, …}`），覆盖 `100/105/120/124/125/132/220/278/282/283/
284/285/308/401/402/403/407/411/415/416/456/701/705/706/707/708/712–716/
719/720/721/728/729/750/751/1004/7760/7765–7769/9976/9981/9987/9991/
9993–9995/17011/28380/28501/28560/28574/28580/28597/28778/28995/61001/
62002/62304/62401–62403/62421/62757`。其中 `103` 域 35 个、`112` 域 34 个、
`200` 域 1 个。

典型下单/撤单模板（`trans/build/position.js` 偏移 `0x2189a`、
`trans/build/bottom_entrust.js` 偏移 `0xf989`）：

```js
{ functionId: 302, subSystemNo: 103, tradeType: 1,
  exchange_type, stock_account, stock_code,
  entrust_amount, entrust_price, entrust_bs, entrust_prop,
  entrust_type, confirm_flag, batch_no }        // 普通委托
{ functionId: 304, … }                          // 撤单
{ functionId: 401, … }                          // 撤单（另一族）
```

信用侧模板（`margintrading/build/commons.js`，偏移见 §2.6 下方表）同样是
`functionId` + `subSystemNo:112` + `tradeType:2`，字段名与普通侧几乎一致，
差异集中在 `asset_prop`、`compact_type`、`compact_source`、`stock_account`。

### 2.5 业务码（`33xxxx`）与函数号对应

日志前缀 `functionId/33xxxx` 直接给出「函数号 ↔ 业务码 ↔ 中文名」三元组
（共 **23** 条静态），例如：

| 函数号 | 业务码 | 名称 |
|---:|---|---|
| 105 | `330300` | 证券代码信息查询 |
| 415 | `331150` | 客户信息查询 |
| 407 | `331300` | 证券股东信息查询 |
| 403 | `333104` | 证券持仓查询 |
| 719 | `335004` | 委托撤单 |
| 62403 | `335102` | 客户信用证券持仓查询 |
| 713 | `335103` | 客户委托流水查询（`func_secu_entrust_serial_qry`） |
| 714 | `335501` | 客户担保证券信息查询 |
| 715 | `335502` | 客户标的证券信息查询 |
| 716 | `335503` | 客户可融券信息查询 |
| 705 | `335504` | 客户信用资产信息获取（`func_crdt_credit_asset_get`） |
| 720 | `335505` | 客户融资融券合约查询（`func_crdt_compact_get`） |
| 728 | `335506` | 客户融资融券实时合约流水查询 |
| 707 | `335510` | 现金还款（`func_crdt_cash_return`） |
| 708 | `335511` | 现券还券 |
| 706 | `335512` | 非交易过户数量获取 |
| 712 | `335513` | 担保券划转 |
| 17015 | `335610` | 融资融券委托交易试算 |
| 28580 | `339314` | 融资融券历史合约流水查询 |
| 62421 | `339313` | 融资融券历史委托查询 |
| 28654 | `339676` | 组合历史委托成分交割查询 |
| 9640 | `332900` | 组合详情查询（同族 `332909/332910/332921/332928`） |
| 28642/28659 | `332921`/`332928` | 组合策略授权/组合持仓划拨 |

`332909 → functionId 9652`、`332910 → 9654`（`query_mode:1`），与
`332900 → 9640` 同属「组合（`comb_*`）」页面的参数对象。

### 2.6 服务名总量

解密后的 507 个 JS 中共抽出 **653** 个唯一服务名：398 个带 `serviceName.`
前缀（网关层统一拼装），255 个不带前缀（`trade.*` 84、`smart.*` 72、
`iadviser.*` 60、`users.*` 46 等）。`payload` 里同时存在 `UniqueKey`
短码（169 个）与全名两种写法，说明短码表只覆盖浏览器通道的一部分，
native 通道直接用全名。

## 3. 响应格式

浏览器通道固定两层结构：

```json
{"ERRORNO": 0, "GRID0": {"code": 0, "message": "...", "result": ...}}
```

`ERRORNO` 在 `Tool.js` 中被显式分支：

| 值 | 处理 |
|---|---|
| `0` | 正常，读 `GRID0` |
| `1` | 登录超时，前端弹「登录已失效」并走重新登录链 |

`GRID0.code` 缺省时回退 `error_no`，缺省值记为 `-1000`；非零码原样交由页面
分支处理。native 通道见 §2.1（`code` 与 `result.errorNo` 两级）。
`@sinolink/http` 的 `transformResponse`（`ParseYJBResponse`）在
`ERRORNO` 与 `GRID0` 同时存在时直接返回 `GRID0`，所以同一份响应在
axios 调用点看起来「只有一层」。

## 4. 域名族

### 4.1 生产域

| 域名 | 用途 |
|---|---|
| `webapps.yongjinbao.com.cn` | 主网关、登录 |
| `operating.yongjinbao.com.cn` | 运营/评论 |
| `quote4j.yongjinbao.com.cn` | 行情 HTTP 快照 |
| `quotezy.yongjinbao.com.cn` | 行情推送 WebSocket（§6） |
| `payment.yongjinbao.com.cn` | 支付 |
| `mall.yongjinbao.com.cn` | 理财商城 |
| `finder.yongjinbao.com.cn` | 发现器 |
| `smart.yongjinbao.com.cn` | 智能交易 |
| `growing.yongjinbao.com.cn` | 增长统计 |
| `appdl.yongjinbao.com.cn` | APK 分发 |
| `linkm.yongjinbao.com.cn` | 下载落地页 |
| `magic.yongjinbao.com.cn` | AI 选股/魔法页 |
| `xbl-yjb-pic.oss-cn-shanghai.aliyuncs.com` | 图片 CDN（阿里云 OSS） |
| `new-yjb-marketing.cn-shanghai.oss.aliyuncs.com` | 营销物料 OSS |
| `growingcdn.yongjinbao.com.cn` / `imgcache.qq.com` | 静态资源 |

### 4.2 仿真域（`yjbtest.com`）

`fz` 前缀走 `fzwebapps.yjbtest.com`、`fzoperating.yjbtest.com`、
`fzquote4j.yjbtest.com`、`fzquotezy.yjbtest.com:30443`、`fzpayment.yjbtest.com`、
`fzmall.yjbtest.com`、`fzfinder.yjbtest.com`、`fzsmart.yjbtest.com`、`fzagreentdl`；
`dev` 前缀走 `devwebapps.yjbtest.com`、`devmall.yjbtest.com:8443`。
另有 `fzwww.yjbtest.com:444`、`xbl-fz-yjb-pic.oss-cn-shanghai.aliyuncs.com` 等。
`pre` 环境指向 `prewebapps.yongjinbao.com.cn`。

### 4.3 明文与内网地址

`android:usesCleartextTraffic="true"` 显式允许明文。H5 资源里还残留仿真服务器
地址，均为 RFC1918 段（发布脱敏）：

| 地址（脱敏） | 上下文 |
|---|---|
| `http://172.28.x.x:8098/gitH5/margintrading/build/*.js` | 融资融券模块本地联调入口 |
| `http://192.168.x.x:8080/yjbapi` | H5 侧 API 直连 |
| `http://192.168.x.x:8080/bankpic/` | 银行图片资源 |
| `http://192.168.x.x:8472/quote/realtimeList/query` | 实时行情直连 |
| `http://172.28.x.x:30110` / `http://172.28.x.x:30110` | NDB 推送测试后端（§6） |
| `http://192.168.x.x/ycomment/phpservice/stockLiving/` | 直播/评论 PHP 服务 |
| `http://localhost:8083/myproj/view/my.jsp` | 本地调试 JSP |

这些地址在生产包中未做条件剥离，属于**静态残留**；未证明生产运行时会请求它们。

## 5. 下载与上传通道

| 通道 | 位置 | 说明 |
|---|---|---|
| APK 下载 | `linkm.yongjinbao.com.cn/download/app-download.html?useDownload=3` | `BaseIndexNew.js` 在未装 App 时跳转 |
| 应用商店 | `a.app.qq.com/o/simple.jsp?pkgname=cn.com.gjzq.yjb2` | 应用宝跳转 |
| 直链 | `appdl.yongjinbao.com.cn/yjb/android/{yjbkh.apk,yongjinbao.apk}` | 两个正式渠道包 |
| 图片 | `xbl-yjb-pic.oss-cn-shanghai.aliyuncs.com` | OSS 静态图 |
| H5 资源 | 同一 OSS 域 `<Interface[code] 路径>` | 65 条 GET 路径，§2.2 |
| 通用上传 | `YJBExec(1004, {url, …})` | `uploadFile`：向任意 http/https POST 数据 |

详见 [transfer.md](transfer.md)。

## 6. 实时通道：NDB WebSocket

行情/推送走独立 WebSocket，实现位于
`relatedfunds|stockInformation|stockWarning|treasurybonds/build/static/NDBCLIAPI.js`
（`function websocketServer` 在 `0x1b602`，`quoteLogin` 在 `0x29c36`，
帧压缩器 `function xGtsJason` 在 `0x71fe`）。它不是 `libndbcliapi.so` 的 H5
封装名——这是**纯 JS 实现**，`host` 由页面传入，`validateHost` 只校验
`ws://` / `wss://` 前缀。

### 6.1 端点

| 环境 | 地址 | 静态出现 |
|---|---|---:|
| 生产（TLS） | `wss://quotezy.yongjinbao.com.cn:8443` | 7 |
| 生产（明文） | `ws://quotezy.yongjinbao.com.cn:30110` | 7 |
| 仿真（TLS） | `wss://fzquotezy.yjbtest.com:30443` | 7 |
| 内网测试 | `ws://172.28.x.x:30110`（两处） | 17 |

### 6.2 登录帧

`onopen` 成功后第一次发送即登录帧，字段固定：

```json
{"type":"login","gid":{"pg":"1","idx":"-1"},
 "account":"<userId>","userid":"<userId>",
 "token":"…","password":"…",
 "device_type":101,"sdk_version":"SDK-web-v1.5.0",
 "device_id_internal":"<uuid v4>","protocol":1,"sdk":1}
```

`device_id_internal` 由 `generateDeviceId()` 现场生成：16 进制字符表 +
`e[14]='4'` + `e[19]=(rand&3)|8` + 4 个连字符，即**每次连接一条新随机 UUID**，
不是硬件标识。`loginInfo.set` 里的额外键会被并入帧。登录应答是同连接的
`{"type":"login","status":"1"}`；`status!="1"` 记为登录失败，`3999` 以外的
关闭码触发 10 s 后重连（`3999` 视为正常关闭，清空订阅表）。

另有一条**未走该封装**的极简登录：`this.login(account, password)` 直接拼
`{"type":"login","gid":{"pg":"1","idx":"-1"},"account":…,"password":…,"time":<ms>}`
字符串发送，不含 token/设备字段——说明该协议存在两个客户端版本。

### 6.3 心跳与订阅

| 项 | 值 |
|---|---|
| 心跳内容 | `{"lang":"zh-cn","gid":{"fm":"heartbeat","pg":"test","idx":"0"},"type":"heartbeat"}` |
| 发送节奏 | `setTimeout(N, 120000)`，任何一次发送都会重置该计时器 |
| 巡检 | `setInterval(w, 5000)`：`isOpened` 后累计 3 次未确认才补心跳 |
| 收到心跳 | `C.gid.fm == "heartbeat"` 时**直接丢弃**，不派发 |

订阅帧用 `{fm, pg, idx}` 三元组定位「帧/页/槽位」，服务端回推同三元组，
客户端据此查 `xSocket.queryList[fm][pg][idx]` 触发 `beginQuery` 与回调；
`type:"push"` 是主动推送、`type:"close"` 是服务端要求关闭。

### 6.4 帧压缩（GTS-JSON）

登录应答里的 `gtsver` 一旦非空，之后所有发送帧都走
`new xGtsJason(payload).compress_json(e, 10)`，再做 10 字节头改写：

```js
a[0] = 152; a[1] = 190;                        // 固定魔数 0x98 0xBE
l = a.length - 10; for (n=2;n<6;n++)  a[n] = 32 + l % 223, l = l/223;
i = payload.length; for (n=6;n<10;n++) a[n] = 32 + i % 223, i = i/223;
```

即「未压缩长度」与「原文长度」各以 4 位 base-223 大端小端混合编码写在
头部；正文是自研 GTS-JSON 二进制（基值 `32`、基数 `223`、类型字节 1–30，
其中 28/29 表示 2/3 字节变长整数，转义字节 27）。压缩失败时原样发送文本，
接收侧的 `JSON.parse` 失败会退化到从 `"gid":{` 起截取再解析，因此**同一连接
可以混用文本帧与二进制帧**。

## 7. 边界

未登录、未构造、未发送任何请求，未抓真实流量。以上全部为**静态**结论：证明
「代码会请求什么、在什么条件下请求、帧长什么样」，不证明服务端实际行为、
当前可达性或真实证书链。
