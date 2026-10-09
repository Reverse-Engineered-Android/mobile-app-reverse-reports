# 客户端、GUI、状态机与渲染管线

## 1. 进程与 Activity 结构

`base.apk` 是 Unity/IL2CPP 游戏外壳，加上 MiHoYo Combo/Passport SDK。
启动链：

```text
com.miHoYo.GetMobileInfo.MainActivity  (LAUNCHER, exported, singleTask,
                                        sensorLandscape, hardwareAccelerated=false)
        |  meta-data unityplayer.UnityActivity=true
        |  meta-data unityplayer.ForwardNativeEventsToDalvik=true
        v
unityplayer.UnityActivity -> libyuanshen.so (IL2CPP runtime + 游戏逻辑)
        ^
        |  AIDL
com.miHoYo.GameStateService.GameStateService (exported, 无 permission)
```

`MainActivity` 继承 `ComboSDKActivity`，只保留外壳职责：屏幕切口适配、
WiFi/网络类型判定、磁盘与 DNS 查询，`IsEmulator()` 直接返回 `false`
（真正的模拟器判定在 `XDeviceUtils`）。Manifest 声明
`android:isGame="true"`、`appCategory="game"`、
`android:enableOnBackInvokedCallback="true"`、
`requestLegacyExternalStorage="true"`、`usesCleartextTraffic="true"`。

SDK 侧 Activity 覆盖登录/协议/WebView/支付/分享/consent 管理，例如
`com.mihoyo.combo.ad.consent.manage.ConsentManagePreferencesActivity`、
`com.mihoyo.combo.ad.consent.privacy.ConsentPrivacyActivity`、
`com.mihoyoos.sdk.platform.module.pay.webcheckout.ui.WebCheckoutWebActivity`、
`com.combosdk.support.basewebview.WebActivity`。

## 2. 导出的游戏状态服务

`com.miHoYo.GameStateService` 包实现 AIDL 双向通道：

- `IGameStateService`（`DESCRIPTOR = com.miHoYo.GameStateService.IGameStateService`，
  `RegisterReceiver=1`、`UnRegisterReceiver=2`、`GetApiVersion=3`、
  `SendRequestToGame=4`）由客户端调用；
- `IGameStateServiceReceiver` 由游戏回调 `GameStateService.GetReceiver()`
  → `ThreadsTimelineSharedMemCreated(ipcFd1, ipcFd2, 2048)` 等事件；
- `GameInterface.GetCSharpCaller()` 返回 `ICSharpInterface`，是 Java 侧到
  C#/IL2CPP 的游戏内接口；游戏未就绪时 `SendRequestToGame` 直接
  `LogError("Game Not Ready")` 返回；
- `GameInterface.OnReqResponse(id, result)` 通过
  `UpdateGameInfo(JSON)` 把结果回传给注册的 receiver。

命令与授权判定详见 `permissions.md` §4；`risk.md` §2 记录该服务无
permission 的越权面。

## 3. 游戏状态机与 Animage 动画系统

`libyuanshen.so` 字符串包含完整的 Animage 资产/运行时符号，说明角色与
场景动画走自研 Animage 而非仅 Unity Animator：

```text
AnimageManager / AnimageComponentManager / AnimageComponentTable
AnimageStateMachineAsset / AnimageGraphAsset / AnimageGraphPlayable
AnimageSetAsset / AnimageRigAsset / AnimagePoseAsset / AnimageBlendSpaceAsset
AnimageClipAsset / AnimageClipPlayable / AnimageTimelineAsset
AnimageBoneMaskAsset / AnimageChainLookAtAsset / AnimageConstraintRigAsset
AnimageDistanceMatchAsset / AnimageFBIKSettingAsset / AnimageSplineIKSettingAsset
AnimageExpressionRetargeterAsset / AnimageRetargeterAsset / AnimageParameterAsset
AnimageChooserAsset / AnimageSkinnedMeshAsset / AnimageVersion
```

源码路径 `External/AnimageSDK/Plugins/StateMachinePlugin/StateMachineModule/
StateMachineAsset.h` 与 `StateMachineCore.h` 表明状态机是插件模块；
`AnimageBeginUpdate`/`AnimageEndUpdate` 是每帧边界，
`Animator.GotoState: Cannot find statemachine` 说明 Unity Animator 与
Animage 状态机共存。实体状态（角色/场景/UI）由 IL2CPP 托管代码驱动，
没有可读的 `.proto` 或状态转移表被解出，因此只描述机制层级，不虚构
具体状态名。

BuildSettings 的应用级场景为 `BundleDownload`、`Game`、`Home`、
`Level`、`Login`、`PSPrepare`，另有 `cloud`/`zxing` 辅助字符串；GUI
层级、IL2CPP 容器和 `GameStateService` JNI 桥的精确证据见
[`il2cpp.md`](il2cpp.md)。

## 4. 资源格式

| 层 | 格式 | 证据 |
| --- | --- | --- |
| IL2CPP 元数据 | `global-metadata.dat`，80,748,928 字节 + `global-metadata.md5` | `assets/bin/Data/Managed/Metadata/` |
| Unity 数据 | `level0`、6 个 `sharedassets*.assets` | `assets/bin/Data/` |
| 资源块 | 29 个 `AssetBundles/blocks/<NN>/<id>.blk`，magic `Blb\x03` + 两个 u32 头字段 | `assets/AssetBundles/` |
| 音频 | `AudioAssets/Minimum.pck`（CRI）、`VideoAssets/*.blk` | `assets/` |
| 清单 | `svc_catalog`（175,628 字节，`Assets/Standard Assets/ShaderVars/...` 列表） | `assets/` |
| 配置 | `asb_settings.json`、`hardware_model_config.json`、`vulkan_gpu_list_config.txt` | `assets/` |
| 热修 | `hdiff/` + native `libhpatchz.so`、`assets/hdiff/LICENSE.txt` | 差异补丁 |

`asb_settings.json` 给出 `variance: 7.1_rel` 与
`ysdownload.mihoyo.com/bh4/GenshinDownloads/` 下的 `game_res`、
`design_data` 两个下载根，说明资源按“包内基础 + 远端增量”分层，
`AssetBundles/data_revision`、`res_revision`、`silence_revision` 是
8 字节版本锚点。

Unity 版本由 `libyuanshen.so` 常量确认为 `2017.4.30f1`；AssetBundle 头
`CAB-<hash>`、`SerializedFile` 版本校验与 `AssetBundleManifest`
字符串说明包内 Unity 序列化资源具备标准字段；外层 `Blb` 资源块仍按
私有封装记录，不能把整个块标为 UnityFS。`ETC2` 等表示 Android 纹理
编码。

## 5. Shader 与渲染管线

`assets/svc_catalog` 列出按平台的 shader 变体集合
（`Assets/Standard Assets/ShaderVars/Rel/Android/...`，含
`Dynamic#Sky=*`、`Hidden=Post#FX=*` 等 pass 名）。`libyuanshen.so`
中的 shader 子系统证据：

```text
SerializedShader / SerializedSubShader / SerializedSubProgram
SerializedShaderState / SerializedShaderRTBlendState
SerializedShaderFloatValue / SerializedShaderVectorValue / SerializedShaderUsedParamMap
ShaderBlobInfoTable / PerShaderBlobInfoType / PerSubprogramBlobInfoType
MiHoYoShaderIndex::FreeShaderIndex / ShaderIndexProgramCodePool
_mhyShaderInstructions
```

`_mhyShaderInstructions` 是米哈游在标准 Unity `SerializedShader` 之上追加的
指令元数据字段；`ShaderBlobInfoTable` + `PerSubprogramBlobInfoType`
构成“shader index → blob offset/length/hash”的间接寻址，
`HK4EUpload: [Shader Deduplicating] LoadSerializedShaderDirect failed when
Compile! shader: %s, blobOffset: %d, blobLength: %d, hash: %s` 说明同一
shader 变体按 hash 去重，blob 从归档按偏移读取。

图形后端同时支持 Vulkan 与 GLES：

```text
Runtime/GfxDevice/vulkan/VKContext|VKImage|VKDescriptorPool|VKSwapChain|VKTaskExecutor
Runtime/GfxDevice/egl/ContextEGL|ContextGLES|ConfigEGL|WindowSurfaceEGL
Runtime/GfxDevice/opengles/GfxDeviceGLES
/_FORCE_VULKAN_  /UnityVulkanPSO  /UnityShaderCache/  /UnityShaderBin/
HK4EUpload: Vulkan PSO / [SetVulkanDeviceConfig] / vkAcquireNextImageKHR / Swapchain
```

`vulkan_gpu_list_config.txt` 是 GPU 白名单（Samsung Xclipse、Adreno
7xx/8xx、Mali-G7xx/G1-Ultra 等），`hardware_model_config.json` 按
`hardwareModel` 给出 `littleCoreCount`/`bigCoreCount`/核掩码与
`vulkanFlag`，用于线程调度与后端选择。渲染命令通过
`RenderingCommandBuffer`/`CommandBuffer`（`DrawMesh`、
`DrawMeshInstanced`、`DrawProcedural`、`DrawRenderer` 等）提交，
支持 async compute 与临时 RenderTexture 管理；`MetalFX` 字符串属于
跨平台渲染器共用代码，Android 走 Vulkan/GLES 路径。

## 6. 美术素材边界

本报告只描述格式、容器和管线机制：不提取、不导出、不发布任何
AssetBundle、纹理、模型、动画、音频、shader blob 或美术素材，也不提供
解包/还原工具。`blocks/*.blk` 仅作为格式证据被计数，其内容未在公开
文档中展示。
