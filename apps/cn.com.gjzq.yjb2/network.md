# 网络与协议

## 1. 出口与分派

本样本是**壳 + 全量 H5** 结构：`classes.dex` 只有 `13,644` 字节，业务代码几乎全部
位于 `assets/yjb.zip`（`186,793,631` B，`2,843` 条目）与 `assets/portal_yjb.zip`
（`15,996,889` B，`542` 条目）。因此网络面由 H5 中的 `Service.js` / `Tool.js` /
`env.js` 决定，而非 DEX 里的 OkHttp/Retrofit 路由。

统一网关：

```text
https://webapps.yongjinbao.com.cn/yjbwebservice/core
https://webapps.yongjinbao.com.cn/yjbwebservice/core/api
https://webapps.yongjinbao.com.cn/yjbwebservice/core/api/json
```

`env.js`（`ycomment/common/vendors/env.js`）按 URL 正则切环境：

| 触发 | `curOriginStr` | `loginOrigin` |
|---|---|---|
| 非 `dev`/`fz`/`localhost` | `https://operating.yongjinbao.com.cn` | `https://webapps.yongjinbao.com.cn` |
| `dev` | `https://devwebapps.yjbtest.com` | `https://devwebapps.yjbtest.com` |
| `fz` | `https://fzoperating.yjbtest.com` | `https://fzwebapps.yjbtest.com` |

`BASE_API = ${originStr}/yjbwebservice/core`，`window.Login_API_URL` /
`login_URL = ${loginOrigin}/yjbwebservice/core/`。评论子站另外通过
`Tool.fetchTo(..., window.YComment.config.ycommentSite)` 走 `site/` 前缀。

## 2. 请求与响应格式

`Tool.js` 的统一请求函数以 `application/x-www-form-urlencoded;charset=utf-8`
发送 `POST`，`withCredentials:false`，默认超时 `5000` ms（`ajaxTimeout`）。
请求体带 `UniqueKey` 作为方法码，其余为业务参数；响应固定两层结构：

```json
{"ERRORNO": 0, "GRID0": {"code": 0, "message": "...", "result": ...}}
```

`ERRORNO` 在 `Tool.js` 中被显式分支：

| 值 | 处理 |
|---|---|
| `0` | 正常，读 `GRID0` |
| `1` | 登录超时，前端弹「登录已失效」并走重新登录链 |

客户端不校验 `GRID0.code` 的具体取值域，只做 `=== 0` 判定；非零码原样交由页面
分支处理。这层 JSON 包装对所有子站（评论、行情、交易、理财）一致。

## 3. 方法码族

H5 里静态可枚举的 `UniqueKey` 有 **169** 个唯一值。方法码是短码而非路径，例如：

| 码 | 语义（源码注释/上下文） |
|---|---|
| `PERBO` | 某业务对象下帖子列表 |
| `PERBP` | 按热度排序帖子列表 |
| `PERBCPN` | 热度排序帖子（新版） |
| `PED` | 删除帖子（已注释停用） |
| `CEA` / `CED` / `CEI` | 评论实体创建/删除/查询 |
| `ANSR` / `ANST` / `ANVS` | 告警/通知订阅类 |
| `UCC` / `UCF` | 用户中心配置/功能 |
| `LOGGER_COLLECT` | 客户端日志上报 |

完整码表见 [evidence.md](evidence.md) §3。

## 4. 域名族

### 4.1 生产域

| 域名 | 用途 |
|---|---|
| `webapps.yongjinbao.com.cn` | 主网关、登录 |
| `operating.yongjinbao.com.cn` | 运营/评论 |
| `quote4j.yongjinbao.com.cn` | 行情 |
| `payment.yongjinbao.com.cn` | 支付 |
| `mall.yongjinbao.com.cn` | 理财商城 |
| `finder.yongjinbao.com.cn` | 发现器 |
| `smart.yongjinbao.com.cn` | 智能交易 |
| `growing.yongjinbao.com.cn` | 增长统计 |
| `appdl.yongjinbao.com.cn` | APK 分发 |
| `linkm.yongjinbao.com.cn` | 下载落地页 |
| `xbl-yjb-pic.oss-cn-shanghai.aliyuncs.com` | 图片 CDN（阿里云 OSS） |
| `growingcdn.yongjinbao.com.cn` / `imgcache.qq.com` | 静态资源 |

### 4.2 仿真域（`yjbtest.com`）

`fz` 前缀走 `fzwebapps.yjbtest.com`、`fzoperating.yjbtest.com`、
`fzquote4j.yjbtest.com`、`fzpayment.yjbtest.com`、`fzmall.yjbtest.com`、
`fzfinder.yjbtest.com`、`fzsmart.yjbtest.com`；`dev` 前缀走
`devwebapps.yjbtest.com`、`devmall.yjbtest.com:8443`。
另有 `fzwww.yjbtest.com:444`、`fzagreentdl.yjbtest.com:8068` 等端口变体。

### 4.3 明文与内网地址

`android:usesCleartextTraffic="true"` 显式允许明文。H5 资源里还残留仿真服务器
地址，均为 RFC1918 内网段（发布版已脱敏）：

| 地址（脱敏） | 上下文 |
|---|---|
| `http://172.28.x.x:8098/gitH5/margintrading/build/*.js` | 融资融券模块本地联调入口 |
| `http://192.168.x.x:8080/yjbapi` | H5 侧 API 直连 |
| `http://192.168.x.x:8080/bankpic/` | 银行图片资源 |
| `http://192.168.x.x:8472/quote/realtimeList/query` | 实时行情直连 |
| `http://192.168.x.x/ycomment/phpservice/stockLiving/` | 直播/评论 PHP 服务 |
| `http://localhost:8083/myproj/view/my.jsp` | 本地调试 JSP |
| `https://devmall.yjbtest.com:8443/mall-web/...` | 商城仿真环境 |

这些地址在生产包中未做条件剥离，属于**静态残留**；未证明生产运行时会实际请求它们。

## 5. 下载与上传通道

| 通道 | 位置 | 说明 |
|---|---|---|
| APK 下载 | `linkm.yongjinbao.com.cn/download/app-download.html?useDownload=3` | `BaseIndexNew.js` 在未装 App 时跳转 |
| 应用商店 | `a.app.qq.com/o/simple.jsp?pkgname=cn.com.gjzq.yjb2` | 应用宝跳转 |
| 直链 | `appdl.yongjinbao.com.cn/yjb/android/{yjbkh.apk,yongjinbao.apk}` | 两个正式渠道包 |
| 图片 | `xbl-yjb-pic.oss-cn-shanghai.aliyuncs.com` | OSS 静态图 |

详见 [transfer.md](transfer.md)。

## 6. 实时通道

行情推送经 `quote4j.yongjinbao.com.cn`；直播交易域 `fchatwebtrade.yongjinbao.com.cn`
在 H5 环境表中出现但无独立协议栈。本样本未发现 WebSocket/长连接的 H5 侧实现，
实时行情更可能由 native 侧 `libndbcliapi.so`（NDB CLI API）承担——该库名出现在
`relatedfunds/build/static/NDBCLIAPI.js` 中。

## 7. 边界

未登录、未构造、未发送任何请求，未抓真实流量。以上全部为**静态**结论：证明
「代码会请求什么、什么条件下请求」，不证明服务端实际行为或当前可达性。
