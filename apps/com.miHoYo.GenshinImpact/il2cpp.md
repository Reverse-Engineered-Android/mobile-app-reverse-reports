# IL2CPP、GUI 状态机与运行时边界

## 1. 结论

静态包包含 IL2CPP 托管代码，但代码不是独立的 `libil2cpp.so`。IL2CPP
运行时和托管编译产物静态链接进 ARM64 主库 `libyuanshen.so`，元数据
由 MiHoYo 私有 `MHY` 容器承载。能确定函数边界、运行时入口、元数据
装载链、可见 UI 字符串和场景/状态服务结构；私有容器中的托管类型名、
方法名和指令到托管方法的映射没有在静态包中以标准 IL2CPP schema
出现，因此不把机器码函数推定为具体业务方法。

## 2. IL2CPP 代码位置

### 2.1 ELF 布局

| 位置 | 虚拟地址/大小 | 作用 |
| --- | --- | --- |
| `.text` | `0x43057a0` / `0x378f16c`（58,257,772 字节） | Unity、native SDK 和业务 native 代码 |
| `il2cpp` | `0x7a9490c` / `0xc5fe998`（207,612,312 字节） | 静态链接的 IL2CPP 托管编译代码 |
| `.rodata` | `0x4840c0` / `0x48f4ec` | native 字符串、表和只读数据 |
| `.eh_frame_hdr` | `0xb38194` / `0x9ad4a4` | AArch64 函数边界索引 |
| `.eh_frame` | `0x14e5638` / `0x2e20168` | unwind/FDE 数据 |
| `.plt` | `0x140932b0` | 外部函数跳转表 |

`il2cpp` 段是可执行、非可写段；`__start_il2cpp=0x7a9490c`、
`__stop_il2cpp=0x140932a4`。ELF 已 strip，没有 `.symtab`；`.dynsym`
没有导出 IL2CPP 类型或方法符号。三个 APK split 中均不存在
`libil2cpp.so`，与库内字符串 `[startup] Static link not load libil2cpp`
一致。

### 2.2 运行时身份

可复现字符串包括：

```text
Unity IL2CPP (Sep  9 2026 16:57:53)
IL2CPP Root Domain
IL2CPP Threadpool worker
[IL2CPP] Failed to protect metadata usage memory because page size =
Failed to extract il2cpp stacktrace from Log message
Unity.IL2CPP.Metadata
unexpected metadata version:
il2cpp-codeswitch.dat
libil2cpp.so
[startup] Static link not load libil2cpp
```

Unity 序列化文件头的版本为 `2017.4.30f1`，但 IL2CPP 工具链构建字符串
为 2026-09-09；二者分别标识 Unity runtime 与后期生成的 IL2CPP 构建。

## 3. 函数边界与代码索引

`.eh_frame_hdr` 版本为 1，`fde_count=1,268,337`，表编码为 `0x3b`。
解析 `.eh_frame` 后得到 `0x43057ac..0x14a3dd0c` 范围内的函数起点：

- `il2cpp` 段 1,078,138 个；
- `.text` 段 115,567 个。

该索引用于从字符串交叉引用、异常表和 AArch64 指令片段回溯到函数
入口。它证明大量编译后的托管函数存在，但不会凭栈帧或函数形状猜
C# 类型名。`il2cpp` 段内发现 223,137 个 `sub sp` 模式运行和 223,954
个 `stp x29,x30` 前索引形式；这些是机器码结构计数，不是托管方法数。

## 4. 元数据容器

### 4.1 容器事实

`assets/bin/Data/Managed/Metadata/global-metadata.dat` 为 80,748,928
字节，SHA-256 为
`ba5bc135e453c342a5ee78a1503896e55d6f313afa4edcd26a427e54f4bab74f`，
实际 MD5 为 `c51a2afdd9ac59c895077e01cb18dd18`，与同目录
`global-metadata.md5` 一致。

文件头是 `4d 48 59 00 00 00 00 00`，即 `MHY\0` 加版本 `0`。全文件中
没有标准 IL2CPP magic `0xFAB11BAF` 的小端或大端形式，也没有对应常量
装载指令。文件包含 `HO__BB_OBFUSCATOR_VERSION_2_0_3` 标记。按
4 KiB 块统计，高熵（>7.5）数据约 33.51 MiB，低熵（<5）数据约
21.37 MiB；没有在文件头或抽样高熵区发现 gzip/zlib/zstd/lz4/lzma/bz2
压缩 magic。

`startup-metadata.dat` 为 4,015,756 字节，首字段为 `0x0000592a`，
熵为 4.3379，绝大多数 u32 小于 `0x10000`；它是启动期的小型结构化
辅助索引，不是可直接搜索的字符串堆。

### 4.2 装载与校验

| 内容 | 字符串地址 | 引用代码 | 函数入口 |
| --- | --- | --- | --- |
| `global-metadata.dat` 路径 | `0x571a1c` | `0x4fe4554` | `0x4fe415c` |
| `global-metadata.md5` 路径 | `0x4fbf4a` | `0x4fe4688` | `0x4fe415c` |
| MD5 结果日志 | `0x5cfece` | `0x4fe4b84` | `0x4fe415c` |
| varint 解析错误 | `0x503a6d` | `0x73ed350` | `0x73ecc9c` |
| `il2cpp-codeswitch.dat` | `0x51a339` | `0x4359368` | `0x4359288` |
| `MHY` 容器检查 | `0x5f81fc` | `0x43758f4` | `0x43758a0` |

`0x73ecc9c` 函数执行 LEB128/varuint32 读取，错误串包括
`LEB is outside Varuint32 range`。头部后 12 个 varint 为
`7144, 5032, 38, 8609391, 4, 102, 54349576, 76, 86, 1497912,
1088065, 103`；由于私有 schema 没有映射，不把这些值命名为类型、
方法或代码偏移。

### 4.3 可读字符串与语义边界

从容器中得到 6,080 个长度至少 4 字节的 NUL 结尾字符串，其中 5,516
个唯一；中位长度 5，90 分位 32，99 分位 60，最大 95。分类结果为：

| 类别 | 唯一值 | 例 |
| --- | ---: | --- |
| GUI 层级 | 434 | `ART/UI/Menus/Widget/BagItemSlot` |
| 美术路径 | 100 | `ART/UI/Atlas/...` |
| GUI 类名 | 8 | `ClientUITextWindowControl`、`MiHoYoSDKLoadingTips` |
| UI 常量 | 13 | `UI_ITEM_GET_DIALOG_TITLE` |

同一容器没有出现 `.unity`、`.prefab`、`.cs` 文件路径，也没有
`System.String`、`UnityEngine`、`MonoBehaviour`、`StateMachine`、
`GameState`、`LoginState` 等标准托管类型名。`Dialog`、`Widget`、
`Panel`、`Bag`、`Avatar`、`Quest`、`Login`、`Activity` 等 UI 词汇可读，
但不能据词形还原调用图。托管方法名称表因此对静态报告标记为
不可读；报告不声称已解密该容器或已把机器码反编译成源码。

## 5. GUI 与应用状态机

### 5.1 BuildSettings 场景状态面

`unity/assets/bin/Data/globalgamemanagers` 是 2,003,876 字节的 Unity
2017.4.30f1 序列化文件。BuildSettings 中的 6 个场景字符串为：

| 场景 | 文件偏移 | 静态职责 |
| --- | ---: | --- |
| `BundleDownload` | `0x6ba10` | 包体/资源准备与下载入口 |
| `Game` | `0x6b98c` | 游戏主场景 |
| `Home` | `0x6b9b8` | 登录后的主页 |
| `Level` | `0x6b9e4` | 关卡/战斗入口 |
| `Login` | `0x6ba44` | 登录、协议和账号入口 |
| `PSPrepare` | `0x6ba70` | 平台/启动准备入口 |

另有 `cloud.unity`（`0x1e9328`）和 `zxing.unity`（`0x6aae0`）辅助
场景字符串。由此可确定的应用级状态面为“下载/平台准备 → 登录 →
主页 → 关卡或游戏”，以及云、二维码等辅助分支。静态数据没有给出
完整有向转移图、触发条件或运行时顺序，因此不把 BuildSettings 数组
顺序冒充状态转移边。

### 5.2 UI 层级与子状态

GUI 层级字符串覆盖登录、背包、角色、任务、活动、邮件、商城、设置和
提示等子状态。可读证据包括：

```text
Dialog/BindingForm/PrivacyProtocol
Dialog/MainPanel/BindingForm/ProtocolBtn/Text
ART/UI/Menus/Widget/BagItemSlot
ART/UI/Menus/Widget/AvatarIcon_Quality5
ART/UI/Menus/Widget/Activity/ActivityTips_TopBar
ART/UI/Menus/Widget/CharacterPage/SkillStateRow
BindMailDialog
BindPhoneDialog
ConsoleRealNameDialog
MiHoYoSDKPopupDialog
WeaponStateID
NextWeaponStateID
```

这些字符串证明存在登录协议、账号绑定、实名、SDK 弹窗、背包、角色
成长和技能子状态，但没有恢复每个子状态的进入条件、退出条件或托管
方法地址。GUI 层级与 `Game`/`Home` 场景的绑定由资源命名交叉支持，
不作为确定的调用边。

### 5.3 Animage 与实体状态机

native `.rodata` 存在 Animage 状态机插件的资产和更新入口：

```text
External/AnimageSDK/Plugins/StateMachinePlugin/StateMachineModule/
  StateMachineAsset.h
  StateMachineCore.h
AnimageComponentManager                 0x5ee8a8 -> text 0x473d748
AnimageBeginUpdate                      0x57ad60 -> text 0x473bf50
StateMachineModule                      0x4fb5aa / 0x5060ee
AnimageStateMachineAsset                0x50f965
Animator.GotoState: Cannot find statemachine
```

`AnimageBeginUpdate`/`AnimageEndUpdate` 形成帧更新边界，Animage 的
`StateMachineAsset`/`GraphAsset` 管角色、场景和动画图；Unity Animator
错误串说明两套状态机并存。字符串表引用用于日志和资产名，部分指针
经 `.rela.dyn`/只读数据表间接引用；没有生成不存在的直接分支。

### 5.4 IL2CPP 到状态服务的桥

`libgamestateservice.so` 导出以下 JNI/运行时符号：

```text
Java_com_miHoYo_GameStateService_GameInterface_NativeInitJavaEnv
Java_com_miHoYo_GameStateService_GameInterface_NativeGetGameIl2cppGcEventPtr
_ZN6IL2CPP25NativeOnGameIl2cppGcEventE13Il2CppGCEvent
Java_com_miHoYo_GameStateService_ThreadTimeline_NativeCreateSharedMemory
Java_com_miHoYo_GameStateService_ThreadTimeline_NativeGetSharedMemoryPtr
Java_com_miHoYo_GameStateService_ThreadTimeline_NativeGetSharedMemoryFd
Java_com_miHoYo_GameStateService_ThreadTimeline_NativeDestroySharedMemory
Java_com_miHoYo_GameStateService_ThreadTimeline_NativeMappedMemoryToDirectByteBuffer
```

库导入 `mmap/munmap/close/memcpy/memmove/dlopen/dlsym` 与系统属性读取。
该桥把 IL2CPP GC 事件和线程时间线映射到 Java 可见的共享内存/直接
ByteBuffer；`permissions.md` 的 Binder 命令面进一步把状态快照提供给
本机应用。它是运行时状态观测通道，不证明托管业务状态机的转移表。

## 6. 资源容器与 GUI 加载边界

`split_AssetBundles.apk` 含 29 个
`assets/AssetBundles/blocks/<NN>/<id>.blk`，解压总量 243,786,978 字节。
每个块的前 4 字节都是 `Blb\x03`，随后是小端 u32 变长字段，再是值
恒为 `5` 的小端 u32，之后为高熵不透明数据。例如：

```text
03525029.blk  22826171  426c6203 75000000 05000000 77a5e420...
24230448.blk 133699977  426c6203 4d330000 05000000 33fcabc9...
22551915.blk      3147  426c6203 75000000 05000000 14fd8786...
```

同包的 `data_revision`、`res_revision`、`silence_revision` 均为 8
字节 ASCII 版本号。`Blb` 块是 MiHoYo 私有块封装，不能只因内部可能
含 Unity 资源就标为标准 UnityFS。静态任务只记录 magic、头字段、
数量和字节范围，不解压、不导出、不发布 GUI、纹理、模型、动画或
其他美术素材。

## 7. 证据等级与公开边界

| 结论 | 等级 | 理由 |
| --- | --- | --- |
| 静态包含 IL2CPP 托管代码 | 高 | ELF 段、边界符号、运行时字符串、元数据文件 |
| IL2CPP 静态链接于 `libyuanshen.so` | 高 | `il2cpp` 可执行段和无 `libil2cpp.so` split |
| `MHY` 容器不是标准 IL2CPP magic | 高 | 文件头与全文件 magic 搜索 |
| BuildSettings 存在 6 个应用场景 | 高 | Unity 序列化文件字符串与偏移 |
| Animage/Animator 状态机并存 | 高 | native 字符串、源码路径和代码交叉引用 |
| GameStateService 暴露 IL2CPP GC/线程状态桥 | 高 | JNI 导出、mmap/共享内存导入 |
| 具体托管类型/方法名及方法调用图 | 高度受限 | 私有元数据 schema 未提供名称表映射 |
| 场景转移条件、运行时顺序和服务端反馈 | 不可由静态包证明 | 未运行游戏、未发包、未抓取真实会话 |

公开报告只包含地址、哈希、结构计数、脱敏字符串类别和机制结论，不
包含 APK、native 库、IL2CPP 元数据、完整反汇编、密钥、盐、原始托管
指令表或美术素材。
