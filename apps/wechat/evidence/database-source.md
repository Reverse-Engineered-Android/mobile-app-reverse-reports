# 数据库源码证据

版本边界：本页 smali、WCDB 打开参数和 AppBrand ORM 注册均来自微信 8.0.78 / versionCode 3180。

## SQLCipher 参数

来源：`smali-classes11/kh5/f.smali`。

```text
94  new-instance SQLiteCipherSpec
98  const/16 v1, 0x400
100 setPageSize(I)
104 const/4 v1, 0x1
106 setSQLCipherVersion(I)
110 sput-object ... kh5/f;->l
```

这直接证明微信 WCDB 封装使用 page size 1024 和 SQLCipher version 1。

## key 派生与历史 device ID

来源：`smali-classes11/kh5/b0.smali:1658-1716`。

```text
1658 IMEISave.a()
1687 append(deviceId)
1689 append(uin)
1695 getBytes()
1699 pk/k.g(bytes)
1705-1707 substring(0, 7)
1716 kh5/f.w(path, digest7, ...)
```

来源：`smali-classes11/com/tencent/mm/storagebase/IMEISave.smali:15-145`。

```text
28-32  add current device-id candidate
36-40  add fallback/empty candidate
45-75  RC4 decrypt KeyInfo.bin
86-92  read historical device-id lines
141-143 add compatibility candidate
```

结论：数据库 key 是 device ID 与 UIN 拼接后的摘要前 7 个十六进制字符；历史 device ID 从加密 `KeyInfo.bin` 恢复。公开报告不包含任何实际 key、UIN、device ID 或文件内容。

## EnMicroMsg 与 WxFileIndex 特殊打开标志

来源：`smali-classes11/kh5/f.smali:1507-1524`。

当文件名以 `EnMicroMsg.db` 或 `WxFileIndex.db` 结尾时，打开 flags 追加 `0x20`；两者走相同的加密打开路径。

## AppBrandComm 注册

来源：`smali-classes7-full/com/tencent/mm/plugin/appbrand/app/fa$$a.smali:31-115`。

```text
31 requireAccountInitialized()
41 account database directory
47 append "AppBrandComm.db"
59-108 iterate AppBrand ORM registry
115 x91/l0.a(hashCode, path, tableMap, true)
```

来源：`smali-classes7-full/com/tencent/mm/plugin/appbrand/app/l.smali:20-298`。

该静态构造器逐个注册 AppBrand 业务表类。`f5.smali:14-40` 进一步给出表名 `DevPkgLaunchExtInfo` 及 `getCreateSQLs` 调用。
