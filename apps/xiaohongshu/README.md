# 小红书 9.37.0

研究对象是 `com.xingin.xhs` 9.37.0 的 base APK 与 ARM64 native libraries。公开部分覆盖：

- `libxyass.so` 的 JNI 注册、shield 外层公式、HMAC 形状和函数地址。
- `libtiny.so` / `libtinyd.so` 的 CFF/opcode、加密 blob 和伴随进程特征。
- `libsecurebase.so`、`libeidjni.so`、`libturingmfa.so` 的 JNI 与业务角色。

入口：

- [分析报告](report.md)
- [逆向证据](evidence.md)
- [算法边界](algorithm.md)

base APK SHA-256：`0de5ed7daf838bf379d5c069b225910128109e8af132e63b13fc6765c0f7991c`。
