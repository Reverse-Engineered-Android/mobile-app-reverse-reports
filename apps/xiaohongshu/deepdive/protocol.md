# 小红书 9.37.0 网络协议格式

本文只记录可从 9.37.0 样本源码/字节码直接复核的协议形状：域名、方法、路径、参数位置、头部构造、编解码与重试。所有参数名出现在样本自身；本文不包含任何真实取值。

## 1. 域名与链路

| 角色 | 域名 | 证据 |
| --- | --- | --- |
| 主 API | `edith.xiaohongshu.com` | `it6/h.java:50` 的 `k.f406377a.c(nbc.i.h("").n("edith.xiaohongshu.com", "https://edith.xiaohongshu.com"))` |
| 推荐 | `rec.xiaohongshu.com` | `classes7/9/18.dex` 字符串表 |
| 设备画像 | `modelportrait.xiaohongshu.com` | `ModelProfile.java:132` 接入点 |
| 搜索 | `so.xiaohongshu.com` | 搜索 Retrofit 服务组（5 个源文件引用） |
| 更新分发 | `redgray.xhscdn.com` | 升级检查链路的 CDN 主机 |
| 灰度/上报 | `debut.devops.xiaohongshu.com/apps/visionx-analyzer/analyze` | `classes18.dex` 字符串表（非 API 链路） |

主链路是自研 OkHttp 封装（`yta.g`），拦截器顺序（`yta.g.c()`）：

```
i0 埋点(头)
  → 业务自定义（XhsHttpInterceptor / TinyInterceptor）
  → h0  xy-common-params
  → f0  User-Agent
  → u   Referer
  → e0  熔断（超时 10s，合成错误码 586）
  → l   Failover / Cronet 切换
  → v   优先级
  → o0  埋点(尾)
  → 网络层 m0/n0 + APM(X-Apm-*)
```

## 2. Retrofit 注解混淆映射

样本用自定义注解替代 Retrofit 原生注解，映射关系由注解类本身确定（`classes20.dex`）：

| 自定义注解 | 等价 Retrofit | 目标 | 证据文件 |
| --- | --- | --- | --- |
| `fvc.f` | `@GET` | `METHOD` / `value()` | `fvc/f.java` |
| `fvc.o` | `@POST` | `METHOD` / `value()` | `fvc/o.java` |
| `fvc.t` | `@Query` | `PARAMETER` / `value()` + `encoded()` | `fvc/t.java` |
| `fvc.c` | `@Field`（表单字段） | `PARAMETER` / `value()` + `encoded()` | `fvc/c.java` |
| `fvc.x` | 参数级请求标签 | `PARAMETER` | `TokenService.quickUploadStr` |

因此“接口路径搜不到”是因为字符串被放进注解而非 URL 常量；按 `fvc.f` / `fvc.o` 检索即可枚举全部端点。

## 3. 头部协议

### 3.1 `xy-common-params`

由 `it6/h.java` 的 `intercept()` 写入；`sources/z2c/e.java`（`ApiArgumentsProviderImpl`）定义 36 个字段名。构造方式是键值对按插入顺序拼接：

```
header("xy-common-params", "k1=v1&k2=v2&…")   // 末尾 & 由 StringsKt.trim(..., '&') 去掉
```

字段名清单（全部来自 `z2c/e.java` 的字符串常量）：

```
platform, deviceId, versionName, channel, origin_channel, sid, lang, dlang, t, fid,
build, launch_id, teenager, tz, cpu_name, device_model, device_level, cpu_abi,
nqe_score, gid, did, holder_ctry, data_ctry, active_ctry, mlanguage, id_token,
x_trace_page_current, app_id, project_id, uis, auto_trans, identifier_flag
```

`t` 是请求时间戳，`sid` 是会话标识，`identity_flag`/`id_token` 只在已登录态出现。

### 3.2 签名类头部（native 生成）

| 头部 | 生成方 | 证据 |
| --- | --- | --- |
| `shield` | `libxyass.so` assembler `0x467dc` | 头部名不落 Java，Java 侧检索 0 命中 |
| `xy-platform-info` | `libxyass.so` | 同上 |
| `x-legacy-did` / `x-legacy-sid` | `libtiny.so` 经 `jt6.a` 写入 | `jt6.a` 先写 did/sid 再调用 native |
| `x-n0` `x-o9` `x-p0` `x-r4` `x-r4o` | `libtiny.so` opcode 引擎返回 Map | `nlb.p.intercept` → `yya.f.e(method,url,body)` → `u2.b(0x96f7fcac, method, host, path, query, body)` → `t.a(op, args)`（**已定名**） |

对 `libxyass.so` 做明文字符串检索：`x-s` / `x-t` / `shield` / `xy-platform-info` 均 **0 命中**，与报告中“头部名只在 native 内出现”的结论一致。

### 3.3 APM

尾部拦截器 `o0` 与 APM 核心写入 `X-Apm-*` 系列头部；`TrackerEventDetail`（`com.xingin.android.apm_core`）承载采样率与 `pointer_id`。

## 4. 端点清单（可复核）

以下端点均由 `fvc.f` / `fvc.o` 注解直接给出。

### 4.1 启动 / 系统服务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/sns/v1/system_service/launch` | 启动配置 |
| GET | `/api/sns/v2/system_service/octopus_dns` | DNS 下发 |
| GET | `/api/sns/v2/system_service/config` | 通用配置（带 `parameter=` 查询位） |
| POST | `/api/sns/v1/system_service/screen_notice` | 屏幕状态上报 |
| POST | `/api/sns/v1/user/data/sync` | 用户数据同步 |

### 4.2 上传/媒资（`TokenService`）

全部为 **GET + Query 参数**，无请求体：

| 路径 | 参数（`fvc.t`） |
| --- | --- |
| `/api/sns/v1/system_service/qcloud_filename` | `type`, `num` |
| `/api/sns/v2/system_service/qcloud_filename` | `md5s`, `type`, `num` |
| `/api/sns/v2/system_service/mix_cloud_upload_info` | `operator`, `type`, `business`, `env`, `cross_upload`, `dynamic`, `version` |
| `/api/media/v1/upload/permit` | `bid`, `biz_name`, `scene`, `file_count`, `version` |
| `/api/media/v1/upload/capa/permit` | 同上 |
| `/api/media/v1/upload/permit_no_login` | 同上 |
| `/api/media/v1/upload/quick_upload_check` | `bid`, `bizName`, `scene`, `dedupIdentifier`, `dedupAlgorithm`（+ 重载含 `dedupChecksum`） |

### 4.3 风控 / 账号

| 方法 | 路径 | 服务 |
| --- | --- | --- |
| POST | `/api/sns/v1/system/ares/device/violation/query` | `IRiskService`（服务端引擎代号 ares） |
| GET | `/api/sns/v2/user/account_info/anomalies` | `IRiskService` |
| POST | `/api/sns/v2/user/account_info/anomalies/confirm` | `IRiskService` |
| GET | `/api/sns/v1/account/intervention` | `IRiskService` |
| POST | `/api/security/antispam/v1/restriction/self-resolve` | 自助解封 |
| GET | `/api/sns/v1/account/phone-binding-dialog` | 绑定引导 |
| POST | `api/sns/v1/user/login/devices/history` | `IDeviceService`（注意：路径字符串**无前导斜杠**） |
| POST | `api/sns/v1/user/login/sid_reason` | 掉线原因，`@d Map<String,String>` 为表单体 |
| POST | `api/sns/v1/user/login/devices/remove/history` | 踢设备，`@c("device_id"/"biz_id"/"template_id")` + `@d` |

### 4.4 人机验证页面的 URL 来源

`ValidateActivity`（`com.xingin.xhs.net.error.activity`）本身**不含 URL 常量**：它是被风控错误响应触发的全屏 WebView，页面地址来自服务端错误体/跳转字段，UA 为

```java
settings.setUserAgentString(System.getProperty("http.agent") + " XHS/3.0.0 NetType/" + i0.d());
```

本次样本中检索不到 `/api/.../captcha_link` 这类字面端点，因此不宣称存在专用的“取验证 URL”接口；验证页面地址属于服务端下发内容。

## 5. 响应体解码

- Retrofit 转换器使用 `@mf.c("字段名")` 标注 JSON 键（例：`RobusterToken.token_info.tmp_secret_id`、`QuickUploadData.fileId`）。
- 上传 token 类响应是 **JSON 字符串再解码**：`Observable<String>` → `Gson` → `RobusterToken`/`RobusterTokenPermit`。
- `CheckVideoFileResult` 的 JSON 字段为 `md5`、`filename`、`exists`；`exists=true` 表示去重命中。

## 6. 重试与降级

| 机制 | 参数 | 位置 |
| --- | --- | --- |
| 上传 token 重试 | `f0(retryCount, delayMs)`；`retryWhen(new r(f0, …))` | `UploaderFlow` 的 `A()` 调用处 |
| 上传内部重试 | `UploadConfig.needInternalRetry`（默认 `true`） | `api/u0.java:93-94` |
| 快速上传超时 | `blockingSubscribe(..., 10, TimeUnit.SECONDS, "UploadFlowImpl.quickUpload")` | `api/internal/o.z()` |
| 熔断 | 超时 10 s，合成错误码 586 | `e0` 拦截器 |
| 上传超时 | `connectionTimeout 15000` / `socketTimeout 30000` | `api/u0.java:93-94` |
| COS 超时 | `connectTimeout 25000` / `socketTimeout 25000` | `j2b/n.java:52` |

## 7. 证据等级

| 结论 | 等级 |
| --- | --- |
| 域名、拦截器顺序、`xy-common-params` 格式与字段名 | 已验证（源码逐行） |
| 注解映射 `fvc.f/o/t/c` | 已验证（注解定义 + 使用点） |
| 端点路径与参数名 | 已验证（注解字符串） |
| `shield`/`xy-platform-info` 头部名 | 结构已证实（Java 侧 0 命中 + native 入口存在；不带值还原） |
| `x-n0`/`x-o9`/`x-p0`/`x-r4`/`x-r4o` 语义 | **生成机制已定名**（`0x96f7fcac` 返回 `Map<String,String>`，由 `nlb.p` 逐条写成 header）；头名分布于 `classes2/15/16/17/20.dex`；具体取值属运行期产物 |
| 熔断阈值 10 s / 586 | 结构已证实；触发实例由运行期计数决定 |
