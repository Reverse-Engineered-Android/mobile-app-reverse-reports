# 解密尝试记录

版本边界：数据库文件来自微信 8.0.78 / versionCode 3180 的授权快照。所有测试在只读文件副本的首页上进行，不修改源库，不输出候选值。

## 已验证主库路径

- `EnMicroMsg.db` 使用 SQLCipher v1：page size 1024、PBKDF2-HMAC-SHA1、64000 次迭代、HMAC-SHA1 20 字节。
- `kh5/b0.smali:1658-1716` 与 `com/tencent/mm/storagebase/IMEISave.smali:15-145` 给出 device ID + UIN 摘要前 7 个十六进制字符和历史 device ID 恢复路径。
- 历史快照使用该参数成功解密并通过 `integrity_check=ok`；实际 key/UIN/device ID 不公开。

## 独立加密库测试

2026-09-27 对当前本地快照 `AppBrandComm.db`、`WxExpt.db`、`WxCgiReport.db`、`newuba.db` 测试了：

1. 已恢复主库 key 候选；
2. 历史 device ID / UIN 派生的 7 字符候选集合；
3. SQLCipher v1 的 HMAC 首页布局（36/48 字节 reserve、ciphertext+IV+page number），以及 SHA1/SHA512 KDF 和 salt/XOR-salt 兼容变体。

四个库均未得到可验证的首页 HMAC 命中，因此没有生成“解密成功”结论。其余 `EnResDown.db`、`MicroMsgPriority.db`、`enFavorite.db`、`Edge.db` 和 `WxFileIndex.db` 当前没有内容级解密证据。它们的表/列/行内容不进入公开报告。

## 边界

未命中只证明当前候选和已验证参数不足，不证明文件损坏，也不证明它使用某个特定加密版本。下一步应从对应数据库的 ORM/打开入口恢复独立 key、参数或自定义容器头，再进行只读验证。
