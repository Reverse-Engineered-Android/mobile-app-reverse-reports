# 设备与行为风险采集面

## 身份验证记录（Journals.db）

`VerifyIdentityModel_table` 共 25 行，全部 `success=1`、`timeout=-1`、`error_code=0`；`biz_id=VerifyIdentity`、`scene_id=fingerprint`。记录的成本范围为 0–1112 ms，聚合平均约 102.6 ms；这些是行为统计，不是设备值。

记录场景聚合：

| 场景 | 行数 |
| --- | ---: |
| `AuthenticatorManager.startAuth` | 2 |
| `FingerprintManager.checkUserStatus` | 2 |
| `FingerprintManager.initHardwarePay` | 11 |
| `SmartPayManager.getFastPayAuthDataWithNoLog` | 10 |

JSON 字段只保留名称、类型和出现次数：

- 请求：`authenticatorType`, `data`, `id`, `switchBtnType`, `type`, `uiType`, `version`。
- 结果：`ai`, `di`, `ap`, `pm`, `sv`, `cm`, `dv`, `rt`, `bi`, `ig`, `le`, `pn`, `rv`, `dd`, `td`。
- 认证器元数据：`mData.authInfoType`, `mData.vendor`, `mData.phoneModel`, `mData.protocolVersion`, `mData.protocolType`, `mData.mfacDownloadUrl`。
- 扩展：`package`, `verifyId`。

上述字段组合表明采集面覆盖认证器能力/硬件状态、设备/ROM/厂商/型号/版本、包名、快速支付授权结果和追踪标识；具体字符串值全部脱敏。

## NFC/支付传感器

`NFCSensorInfoCollector`（`jadx7/sources/com/alipay/android/phone/nfc/core/NFCSensorInfoCollector.java:5`）是 `SensorEventListener`，字段包含 BLE 扫描回调、传感器管理器、手势缓冲、场景配置、事件间隔和跳过页面配置。`GestureInfo` 保存：

- `accelerometer`、`accelNoLinear`、`gyroscope`；
- 旋转矩阵/方向结果；
- 时间戳和传感器采样序列。

这类数据是设备姿态/手势行为信号，通常用于交易场景反欺诈或人机识别。公开报告只列字段族和采集入口，不列采样值。

## 设备请求输入

`DeviceIDManager.getRemoteDeviceID`（`jadx20/sources/mtopsdk/mtop/deviceid/DeviceIDManager.java:267`）组装本地 UTDID、原始 IMEI/IMSI、品牌、型号、序列号和 Android ID，并请求远端生成/返回 device id。该请求是设备身份/风险信号输入；本报告不公开任何值。

## 保护域

`iotauth.biometric.trustdata.config.xml`、`securitypreference00.xml`、`gestureSecurityTag.xml` 和 `aluSecurityDataStore.xml` 仅显示键名或布尔/整数状态；其中 trust data 和安全存储值为空或被保护。`securitypreference00.xml` 的 `KEY_TEE_FAILED`/`KEY_VENDOR_FAILED` 表明硬件可信执行/厂商能力失败状态会被记录。
