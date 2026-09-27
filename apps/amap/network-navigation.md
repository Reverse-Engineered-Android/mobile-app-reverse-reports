# 网络与在线导航格式

## 请求通道

`com.autonavi.core.network.util.CoreInterface` 对一批 URL 做 allowlist 匹配，
尾部 `/` 会被规范化。导航相关路径包括：

| 路径 | 推断用途 |
| --- | --- |
| `/ws/transfer/navigation/auto` | 驾车导航/路线请求 |
| `/ws/transfer/navigation/routeguide` | 路线引导 |
| `/ws/navigation/dynamic/data` | 动态导航数据 |
| `/ws/shield/ride/navigation` | 骑行导航 |
| `/ws/shield/walkcloud/navigation` | 步行导航 |
| `/ws/shield/truck/route` | 货车路线 |

同一 allowlist 还包含公交、出租车、支付和搜索类路径；本文只记录导航接口。

`com.autonavi.jni.ae.route.observer.HttpInterface` 的关键签名是：

```java
boolean requestHttpPost(int requestId, int type, String url, byte[] body);
```

这说明 native 路线层把请求 body 作为 opaque `byte[]` 交给 Java/网络层。
现有证据没有给出该字节流的完整 schema，因此不把它误写成 JSON、protobuf 或
可直接重放的请求格式；真实 body、请求头、签名和响应均不公开。

## 路线请求模型

`POIForRequest` 是 `POIInfo` 数组加定位/匹配状态的组合：

| 字段组 | 已见字段 |
| --- | --- |
| 点集合 | `start[]`, `via[]`, `end[]` |
| GPS/航向 | `gpsCredit`, `precision`, `radius`, `reliability`, `angleGps`, `angleComp`, `angleType`, `direction` |
| 链接匹配 | `fittingCredit`, `fittingDir`, `matchingDir`, `speed`, `formWay`, `linkType`, `sigType` |

`POIInfo` 的字段覆盖 POI/建筑/楼层标识与名称、`latitude`/`longitude`、
`naviLat`/`naviLon`、道路 ID/类型、`overhead`、`sigshelter` 等。真实 POI、
名称、标识和坐标均不公开。

## RouteOption 与 RerouteOption

`RouteOption` 暴露三个 native 配置入口：`setConstrainCode`、
`setPOIForRequest`、`setRequestRouteType`。`RerouteOption` 还暴露当前位置、
当前路径、剩余导航信息、路线模式和重规划类型。

### RequestRouteType

| 常量 | 值 |
| --- | ---: |
| `RequestRouteTypeNULL` | -1 |
| `RequestRouteTypeBest` | 0 |
| `RequestRouteTypeMoney` | 1 |
| `RequestRouteTypeDist` | 2 |
| `RequestRouteTypeNorm` | 3 |
| `RequestRouteTypeTMC` | 4 |
| `RequestRouteTypeMulti` | 5 |
| `RequestRouteTypeThree` | 9 |
| `RequestRouteTypeTMCFree` | 12 |
| `RequestRouteTypeMostly` | 13 |

这些是应用中的请求偏好/策略类型，不等于路线算法已经确认。

### RerouteOption.RouteType

| 常量 | 值 | 含义边界 |
| --- | ---: | --- |
| `RouteTypeCommon` | 1 | 普通重规划 |
| `RouteTypeYaw` | 2 | 航向变化 |
| `RouteTypeChangeStratege` | 3 | 策略变化 |
| `RouteTypeParallelRoad` | 4 | 平行道路 |
| `RouteTypeTMC` | 5 | 实时交通 |
| `RouteTypeLimitLine` | 6 | 限制线路 |
| `RouteTypeDamagedRoad` | 7 | 道路损坏 |
| `RouteTypePressure` | 8 | 道路压力 |
| `RouteTypeChangeJnyPnt` | 9 | 旅程点变化 |
| `RouteTypeUpdateCityData` | 10 | 城市数据更新 |
| `RouteTypeLimitForbid` | 11 | 限制/禁行 |
| `RouteTypeManualRefresh` | 12 | 手动刷新 |
| `RouteTypeLimitForbidOffLine` | 13 | 离线限制/禁行 |
| `RouteTypeMutiRouteRequest` | 14 | 多路线请求 |

`RouteTypeMax = 15` 是边界值，不是触发原因。

## 在线/离线边界

已验证存在独立的 `OfflineRouteRequest` 入口和 online HTTP callback 接口，
但没有足够证据证明两者如何选择 provider、如何序列化网络 body 或如何拼接
在线/离线候选路线。本文不提供请求重放或伪造能力。
