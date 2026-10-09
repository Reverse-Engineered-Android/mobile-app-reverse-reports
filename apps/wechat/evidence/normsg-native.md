# libwechatnormsg 原生风控机制

样本：`com.tencent.mm` 8.0.78 / `versionCode=3180`，
`lib/arm64-v8a/libwechatnormsg.so`。JNI 注册表由 `JNI_OnLoad` 中的
`JNINativeMethod` 数组还原，共 63 个方法、6 组；方法边界来自
`.eh_frame_hdr` FDE 表。下文地址均为该 ELF 的虚拟地址。

## 1. 采集入口与上传时机

本次触屏上报的 Java 门控是：

```java
// eg0/v.java:389-407
if (motionEvent.getAction() == 1 && !TextUtils.isEmpty(str)) {
    MotionEvent copy = MotionEvent.obtain(motionEvent);
    if (i2 == 540999748) {
        q.Y7("ceu_global", str2);
        q.Bi("ceu_global", motionEvent2, false, str2);
        q.Ha("ceu_global");
        byte[] block = q.Q8("ceu_global", new normsg.i(true, false, true, true));
    }
}
```

因此触屏对象只在 `ACTION_UP` 且场景字符串非空时复制并排队；进入
原生层前固定选择场景 `ceu_global`、mode `3`、四个取数开关
`(true,false,true,true)`。事件不会逐帧或逐点实时发送。

原生入口为：

| 方法 | 地址 | 输入/输出 |
| --- | --- | --- |
| `dg` | `0x7ee6ec` | `(String scene, MotionEvent, int mode, String scene2, int)` → `void` |
| `dj` | `0x8039c8` | `(String scene, Object selector)` → `byte[]` |
| `dm` | `0x84aa7c` | `(String scene, MotionEvent, String)` → `boolean` |

`dg` 的函数体为 `0x14678` 字节，`dj` 为 `0x46648`，`dm` 为
`0x2f3c`。调用顺序是 `dg` 写入/更新场景状态，`Q8 → dj` 读取
当前场景状态并返回序列化 `byte[]`；返回空数组时不发送。

## 2. JNI 反射与混淆字符串

`libwechatnormsg.so` 的 `.rodata` 中没有 `MotionEvent`、`getX`、
`getAction` 等明文字符串。`dg` 通过 JNI 函数表直接解析类、方法和
字段：

- `0x7fba40`：函数表偏移 `248`，即 `GetObjectClass`；
- `0x7fd188`、`0x7ff558`：偏移 `1352`，即 `GetStringUTFChars`；
- `0x7fd070`、`0x7fd1d4`、`0x7ff5a4`：偏移 `264`，即
  `GetMethodID`；
- `0x7f0e38`、`0x7f1f84`、`0x7f24e8`、`0x7f2520`、`0x7f2528`、
  `0x7f5e08`、`0x7f8a78`、`0x7fbe04`、`0x7fbe0c`、`0x7fcd78`、
  `0x7fe22c`、`0x7fe268`：偏移 `752`，即 `GetFieldID`。

字符串种子在指令流中直接构造。`0x7fbaf4`–`0x7fbbf4` 写入的 16
字节为：

```
4f 7c 06 63 48 20 19 41 79 33 1c 70 78 16 76 27
```

`0x7fbc9c`–`0x7fbd30` 对该缓冲执行 9 轮索引交换与异或：每轮从
key/index 表取字节，依次执行
`state ^= table[index]`、`out = state ^ previous`，并把新状态写回；
轮计数与 `9` 比较后回卷。`0x7ee9c4`–`0x7eea2c` 是另一组确定性
解码循环，核心指令为：

```asm
ldrb  w10, [x9, x8]
ldrb  w11, [x21, x11]
eor   w10, w10, w11
eor   w10, w10, #0x77777777
strb  w10, [x9, x8]
add   w8, w8, #1
cmp   w8, #0xb
```

即每字节执行 `p[i] = p[i] ^ table[i] ^ 0x77`，循环 12 次。所有
类名、方法名和字段名都由栈内种子、key/index 表和这两个异或循环
产生；二进制中没有其他密码算法参与 JNI 名称恢复。

`dg` 内统计到 8 次 `GetObjectClass`、8 次 `GetMethodID`、13 次
`GetFieldID`、6 次 `GetStringUTFChars`；`dj` 内为 16、按场景复用
的方法表、16、9。字段句柄在同一场景生命周期内缓存，`DeleteLocalRef`
在各解析分支后统一回收。

## 3. MotionEvent 数据的进入方式

`dg` 在 `0x7fba38` 用 `GetObjectClass(MotionEvent)` 取得类对象，随后
通过上述 `GetMethodID`/`GetFieldID` 调用读取事件的标量字段和
pointer 数组。事件数据的读取结果写入 `x19+0x208` 起始的场景状态
区；`0x7ff2cc` 的 JNI 偏移 `1368` 和 `0x7ff2e0` 的偏移 `1824`
分别用于返回 Java 字符串对象和布尔校验，`0x7ff364` 的偏移 `1384`
在 pointer 循环中调用 `GetArrayLength`，循环以 `x19+356` 为上界。

`dg` 不把 `MotionEvent` 对象交给网络层，也不直接返回坐标明文；它只
把状态写入 native scene buffer。`dj` 读取该 buffer，并用
`GetStringUTFChars(0x803ac4)` 取得 scene 字符串，按同一对象缓存和
混淆表生成结果字节。最终由 JNI 字节数组返回给 Java，再包进
Protobuf。

## 4. 输出块与网络格式

`dj` 返回的 `byte[]` 是当前场景状态的 native 序列化结果，包含：

- `dg` 写入的 MotionEvent 状态；
- `dg` 的 mode、附加 scene 字符串和 int 参数；
- `dj` selector 中四个 bool 的选择位；
- 场景缓存及用于判定的计数/时间状态。

它不包含账号 UIN、手机号、联系人或消息正文。线上外层格式是：

```
pc5.vw5 { e=540999748, d=empty, f=pc5.p16(pc5.od7{
    2=pc5.p16(dj("ceu_global", selector)),
    3=pc5.p16(附加原生块)
}) }
```

对应 CGI `/cgi-bin/micromsg-bin/reportclientcheck`，func/type `771`；
发送前还经过 `ACTION_UP`、非空场景、24 小时计数槽 `13`、默认上限
`20` 和非空 `byte[]` 五重门控。

## 5. 最终结论

该样本的触屏风控不是实时轨迹上传。事件对象仅在抬手后进入原生
状态机；native 层以确定性索引/XOR 变换解析 JNI 名称和序列化
数据，返回的 byte[] 再按场景号、空 bytes、嵌套 Protobuf 的固定字段
上传。静态样本中的执行变换、JNI 调用号、循环边界、Protobuf 字段号
和 CGI 均已还原，不需要依赖未知加密分支。
