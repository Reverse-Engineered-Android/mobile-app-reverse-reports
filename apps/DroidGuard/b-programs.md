# `.b` 程序逐段可读反混淆

三个缓存程序均已从 `.b` 外层解码进入固定 ARM64 native VM，完成 `ssNative` deferred-setup 路径的完整指令级追踪。下面给出选择器控制流、实际与备选分支、取流范围、寄存器状态变化、HMAC/AES/序列化调用和输出 protobuf 结构；每个 selector 的完整已执行 ARM64 指令、计数与调用表保存在 [`b-program-lift.json`](b-program-lift.json)。

| flow | raw SHA-256 | native 指令 | selector 段 | 输出字节 | 输出 SHA-256 |
|---|---|---:|---:|---:|---|
| `fast` | `174ad7b1d99dc83353ee2db2350352d8222a8b367ceed737b3d10d7bc74d9688` | 607366 | 24 | 49 | `e6dce73ac4b3d1b2415b143c8d02a3e7e617d550f1191fe65c5f6f2f5ff80221` |
| `pia_express` | `c58dc0c4b283568544663f8a92a7187d112e588ca3b01c52c82f97a9c0ebb72d` | 14097 | 14 | 41 | `cbced0d7c748da29374b4ef729234458631f6d318b04394c3839c9e444b4e29a` |
| `ad_attest` | `dad1dc2600626a3ec8130a30eb8b2d30c17fe7bc153639ca756f1464cc6164d9` | 15807 | 54 | 41 | `68525818ba4e0c9c1c508e7c7f679672ab7ded86c0d94008bb80f67987a3727a` |

## 输出结构

三者均产生两个重复 message。外层 field 1 是固定 6 字节 `57 5e b8 cd f1 29`；外层 field 5 内 field 1=3、field 2=4→5、field 12=1→2、field 6=1，field 3 保存执行前后的 VM 寄存器 1。字段顺序、varint 和负数编码均在 JSON 中逐字段展开。

## fast · `0b527259…`

- flow 关联依据：initial app_dgp snapshot association。
- raw：63666 字节，SHA-256 `174ad7b1d99dc83353ee2db2350352d8222a8b367ceed737b3d10d7bc74d9688`。
- 外层解码：63662 字节，SHA-256 `f6d2bc80bc4c98ebcc5f5dac558db0de123f2b1e69891755bdb79c588408ae4a`。
- 执行：607366 条 native 指令，24 个 selector 段，returned=`true`，errors=0。

### 读取与状态

| source offset | decoded/returned length | decoded SHA-256 | evidence |
|---:|---:|---|---|
| 1 | 1 | dbc1b4c900ffe48d575b5da5c638040125f65db0fe3e24494b76ea986457d986 | decoded runtime fetch |
| 6 | 2 | ff44cdd2ad32e36272383e61331a1b73459115d0a6f7f548d9d3414d38f7c847 | decoded runtime fetch |
| 8 | 2 | 418983faed0e9bbd7dc723df9530e3f8b90ca241bc12fe37ceb8828160115280 | decoded runtime fetch |
| 10 | 17222 | 1e22bbf8c2cdad5f9a0c190108b878e01bb67a98f267cda4bfa4e221316bf625 | decoded runtime fetch |
| 1 | 6 | — | VM header snapshot |
| 1 | 7 | — | VM header snapshot |
| 2 | 8 | — | VM header snapshot |

| state | type | before → after |
|---|---|---|
| `r01` | [0, 0] | `47` → `-9161` |
| `r08` | [0, 0] | `1` → `17232` |
| `r09` | [1, 1] | `6930432` → `6963200` |
| `r17` | [0, 0] | `3` → `6` |
| `r1b` | [1, 1] | `6934528` → `6967296` |
| `r21` | [1, 1] | `6938624` → `6971392` |

### 控制流

| # | selector → next | handler | executed / unique | 分支或操作 | alternate | state delta |
|---:|---|---|---:|---|---|---|
| 0 | `0x5b5446c5` → `0x747f9a11` | dispatcher | 730 / 392 | session setup, header decode, first AES block | — | — |
| 1 | `0x747f9a11` → `0x83ec33cc` | `0x2256c` | 2 / 2 | shuffled selector/dispatch path | — | r08=0x1->0x6 type:0->0 |
| 2 | `0x83ec33cc` → `0x4ec83461` | `0x23b28` | 7 / 7 | load word table[(x26 & 0xff)] and select next state | 0x4ec83461 | — |
| 3 | `0x4ec83461` → `0xd69a99c` | `0x268ac` | 10 / 10 | w19 < 0xffffff95 → 0x0d69a99c; otherwise 0x5b972e16 (executed: true) | 0x5b972e16 | — |
| 4 | `0x0d69a99c` → `0xa607214b` | `0x28e24` | 10 / 10 | w19 < 0xffffff95 → 0xa607214b; otherwise 0x2800c7e1 (executed: true) | 0xa607214b | — |
| 5 | `0xa607214b` → `0x77439633` | `0x27548` | 10 / 10 | w19 < 0xffffff95 → 0x77439633; otherwise 0xedcffad6 (executed: true) | 0x77439633 | — |
| 6 | `0x77439633` → `0xcb513a7d` | `0x24d00` | 10 / 10 | w19 < 0xffffff95 → 0xcb513a7d; otherwise 0xbcb965c2 (executed: true) | 0xbcb965c2 | — |
| 7 | `0xcb513a7d` → `0xb2f8f210` | `0x2362c` | 9 / 9 | w19 < 0x0000076f → 0xb2f8f210; otherwise 0xcfff54d8 (executed: true) | 0xb2f8f210 | — |
| 8 | `0xb2f8f210` → `0xd02172d6` | `0x29688` | 9 / 9 | w19 < 0x0000030d → 0xd02172d6; otherwise 0xe430d02c (executed: true) | 0xe430d02c | — |
| 9 | `0xd02172d6` → `0x682fc45e` | `0x1dcb4` | 7 / 7 | w19 == 0x0000019e → 0xd90b9520; otherwise 0x682fc45e (executed: false) | — | — |
| 10 | `0x682fc45e` → `0x15529b4a` | dispatcher | 46 / 46 | w20 == w27 → 0xe8afad93; otherwise 0x15529b4a (executed: false) | 0x15529b4a | — |
| 11 | `0x15529b4a` → `0x83ec33cc` | `0x21584` | 5 / 5 | shuffled selector/dispatch path | 0x83ec33cc | — |
| 12 | `0x83ec33cc` → `0x4ec83461` | `0x23b28` | 7 / 7 | load word table[(x26 & 0xff)] and select next state | 0x4ec83461 | — |
| 13 | `0x4ec83461` → `0xd69a99c` | `0x268ac` | 10 / 10 | w19 < 0xffffff94 → 0x0d69a99c; otherwise 0x5b972e16 (executed: true) | 0x5b972e16 | — |
| 14 | `0x0d69a99c` → `0x2800c7e1` | `0x28e24` | 10 / 10 | w19 < 0xffffff94 → 0xa607214b; otherwise 0x2800c7e1 (executed: false) | 0xa607214b | — |
| 15 | `0x2800c7e1` → `0x7dff4ba0` | `0x22594` | 10 / 10 | w19 < 0xffffff94 → 0x8eb75195; otherwise 0x7dff4ba0 (executed: false) | 0x7dff4ba0 | — |
| 16 | `0x7dff4ba0` → `0x3b862fa8` | `0x1e5bc` | 10 / 10 | w19 < 0xffffff94 → 0x0f0c2666; otherwise 0x3b862fa8 (executed: false) | 0x3b862fa8 | — |
| 17 | `0x3b862fa8` → `0xc015610` | `0x28b04` | 10 / 10 | w19 < 0xffffff94 → 0xcf673461; otherwise 0x0c015610 (executed: false) | 0xcf673461 | — |
| 18 | `0x0c015610` → `0x293ae771` | `0x2685c` | 10 / 10 | w19 < 0xffffff94 → 0x293ae771; otherwise 0x1d6a611b (executed: true) | 0x1d6a611b | — |
| 19 | `0x293ae771` → `0x54856439` | `0x1f704` | 10 / 10 | w19 < 0xffffff94 → 0x54856439; otherwise 0xa443d0f4 (executed: true) | 0x54856439 | — |
| 20 | `0x54856439` → `0x1e575b23` | `0x2b650` | 8 / 8 | w19 == 0xffffff94 → 0x1e575b23; otherwise 0x682fc45e (executed: true) | — | — |
| 21 | `0x1e575b23` → `0x7914e417` | `0x22350` | 594019 / 1063 | long execution handler: AES single-block ×1076, fetch/decode ×3, range decode ×3 | 0x7914e417 | — |
| 22 | `0x7914e417` → `0x9d2333bd` | `0x1dda4` | 5 / 5 | advance VM state and select finalization | 0x9d2333bd | r01=0x2f->0xffffdc37 type:0->0; r08=0x6->0x4350 type:0->0; r09=0x69c000->0x6a4000 type:1->1; r17=0x3->0x6 type:0->0; r1b=0x69d000->0x6a5000 type:1->1; r21=0x69e000->0x6a6000 type:1->1 |
| 23 | `0x9d2333bd` → `None` | `0x43244` | 7249 / 1269 | finalization: HMAC-SHA256 ×2, serialize ×2 | 0xf1a9e49d, 0x45cc1ea9 | — |

### 长 handler 与密码学

- `0x5b5446c5` handler `None`：730 条执行指令、392 个唯一 PC；session setup, header decode, first AES block。
- `0x1e575b23` handler `0x22350`：594019 条执行指令、1063 个唯一 PC；long execution handler: AES single-block ×1076, fetch/decode ×3, range decode ×3。
- `0x9d2333bd` handler `0x43244`：7249 条执行指令、1269 个唯一 PC；finalization: HMAC-SHA256 ×2, serialize ×2。

## pia_express · `4d85a8af…`

- flow 关联依据：initial app_dgp snapshot association, independently retained by later DB rows。
- raw：87050 字节，SHA-256 `c58dc0c4b283568544663f8a92a7187d112e588ca3b01c52c82f97a9c0ebb72d`。
- 外层解码：87046 字节，SHA-256 `b801bb20e9537aeddccd35a1dac579870733560afe71402f1c76532fe89c0e56`。
- 执行：14097 条 native 指令，14 个 selector 段，returned=`true`，errors=0。

### 读取与状态

| source offset | decoded/returned length | decoded SHA-256 | evidence |
|---:|---:|---|---|
| 1 | 1 | c555eab45d08845ae9f10d452a99bfcb06f74a50b988fe7e48dd323789b88ee3 | decoded runtime fetch |
| 6 | 1 | 67586e98fad27da0b9968bc039a1ef34c939b9b8e523a8bef89d478608c5ecf6 | decoded runtime fetch |
| 7 | 1 | 951dcee3a7a4f3aac67ec76a2ce4469cc76df650f134bf2572bf60a65c982338 | decoded runtime fetch |
| 8 | 1 | bd4fc42a21f1f860a1030e6eba23d53ecab71bd19297ab6c074381d4ecee0018 | decoded runtime fetch |
| 9 | 1 | 74e1ade320c66075468e17cfab33f41e8e0eaca45edb6dd7b086c49a358d2a69 | decoded runtime fetch |
| 1 | 6 | — | VM header snapshot |
| 1 | 7 | — | VM header snapshot |
| 1 | 8 | — | VM header snapshot |
| 1 | 9 | — | VM header snapshot |

| state | type | before → after |
|---|---|---|
| `r01` | [0, 0] | `127` → `201` |
| `r08` | [0, 0] | `1` → `10` |
| `r09` | [1, 1] | `7077888` → `7090176` |
| `r1b` | [1, 1] | `7081984` → `7094272` |
| `r21` | [1, 1] | `7086080` → `7098368` |

### 控制流

| # | selector → next | handler | executed / unique | 分支或操作 | alternate | state delta |
|---:|---|---|---:|---|---|---|
| 0 | `0x5b5446c5` → `0x747f9a11` | dispatcher | 730 / 392 | session setup, header decode, first AES block | — | — |
| 1 | `0x747f9a11` → `0x83ec33cc` | `0x2256c` | 2 / 2 | shuffled selector/dispatch path | — | r08=0x1->0x6 type:0->0 |
| 2 | `0x83ec33cc` → `0x4ec83461` | `0x23b28` | 7 / 7 | load word table[(x26 & 0xff)] and select next state | 0x4ec83461 | — |
| 3 | `0x4ec83461` → `0x5b972e16` | `0x268ac` | 10 / 10 | w19 < 0xffffffa3 → 0x0d69a99c; otherwise 0x5b972e16 (executed: false) | 0x5b972e16 | — |
| 4 | `0x5b972e16` → `0x69b0146b` | `0x27170` | 10 / 10 | w19 < 0xffffffa3 → 0x9ad2cc55; otherwise 0x69b0146b (executed: false) | 0x69b0146b | — |
| 5 | `0x69b0146b` → `0x580f36fe` | `0x28834` | 10 / 10 | w19 < 0xffffffa3 → 0x580f36fe; otherwise 0x3a7333bd (executed: true) | 0x580f36fe | — |
| 6 | `0x580f36fe` → `0xa95565` | `0x2b784` | 10 / 10 | w19 < 0xffffffa3 → 0x3372e476; otherwise 0x00a95565 (executed: false) | 0x3372e476 | — |
| 7 | `0x00a95565` → `0xa03583ae` | `0x1e950` | 10 / 10 | w19 < 0xffffffa3 → 0x784a1c9b; otherwise 0xa03583ae (executed: false) | 0xa03583ae | — |
| 8 | `0xa03583ae` → `0x43dd1419` | `0x236ec` | 10 / 10 | w19 < 0xffffffa3 → 0xe89ae3ff; otherwise 0x43dd1419 (executed: false) | 0x43dd1419 | — |
| 9 | `0x43dd1419` → `0x41823714` | `0x25620` | 10 / 10 | w19 < 0xffffffa3 → 0x5e4d72fb; otherwise 0x41823714 (executed: false) | 0x5e4d72fb | — |
| 10 | `0x41823714` → `0xa1a26a7e` | `0x23f10` | 8 / 8 | w19 == 0xffffffa3 → 0xa1a26a7e; otherwise 0x682fc45e (executed: true) | — | — |
| 11 | `0xa1a26a7e` → `0x7914e417` | `0x1edc4` | 2661 / 808 | long execution handler: fetch/decode ×4, range decode ×4 | 0x7914e417 | — |
| 12 | `0x7914e417` → `0x9d2333bd` | `0x1dda4` | 5 / 5 | advance VM state and select finalization | 0x9d2333bd | r01=0x7f->0xc9 type:0->0; r08=0x6->0xa type:0->0; r09=0x6c0000->0x6c3000 type:1->1; r1b=0x6c1000->0x6c4000 type:1->1; r21=0x6c2000->0x6c5000 type:1->1 |
| 13 | `0x9d2333bd` → `None` | `0x43244` | 5780 / 1268 | finalization: HMAC-SHA256 ×2, serialize ×2 | 0xf1a9e49d, 0x45cc1ea9 | — |

### 长 handler 与密码学

- `0x5b5446c5` handler `None`：730 条执行指令、392 个唯一 PC；session setup, header decode, first AES block。
- `0xa1a26a7e` handler `0x1edc4`：2661 条执行指令、808 个唯一 PC；long execution handler: fetch/decode ×4, range decode ×4。
- `0x9d2333bd` handler `0x43244`：5780 条执行指令、1268 个唯一 PC；finalization: HMAC-SHA256 ×2, serialize ×2。

## ad_attest · `8f1a7840…`

- flow 关联依据：initial app_dgp snapshot association retained by the cached analysis mapping。
- raw：64561 字节，SHA-256 `dad1dc2600626a3ec8130a30eb8b2d30c17fe7bc153639ca756f1464cc6164d9`。
- 外层解码：64557 字节，SHA-256 `04c2a5de929ecd2a1956fbc20bfedd98899b9e0fd94f0546f8620ca5a3b9e087`。
- 执行：15807 条 native 指令，54 个 selector 段，returned=`true`，errors=0。

### 读取与状态

| source offset | decoded/returned length | decoded SHA-256 | evidence |
|---:|---:|---|---|
| 1 | 1 | 782e02029374527bd2a5fe7b9545df6c2911078e337a62573970b178d93db481 | decoded runtime fetch |
| 6 | 1 | 08f271887ce94707da822d5263bae19d5519cb3614e0daedc4c7ce5dab7473f1 | decoded runtime fetch |
| 7 | 1 | 79bec7ff3e69d1b470eb8684f2005a867a7980222474cbc1afd26a9a4f206d4a | decoded runtime fetch |
| 1 | 6 | — | VM header snapshot |
| 1 | 7 | — | VM header snapshot |

| state | type | before → after |
|---|---|---|
| `r01` | [0, 0] | `209` → `59` |
| `r08` | [0, 0] | `1` → `9` |
| `r09` | [1, 1] | `6930432` → `6942720` |
| `r1b` | [1, 1] | `6934528` → `6946816` |
| `r21` | [1, 1] | `6938624` → `6950912` |

### 控制流

| # | selector → next | handler | executed / unique | 分支或操作 | alternate | state delta |
|---:|---|---|---:|---|---|---|
| 0 | `0x5b5446c5` → `0x747f9a11` | dispatcher | 730 / 392 | session setup, header decode, first AES block | — | — |
| 1 | `0x747f9a11` → `0x83ec33cc` | `0x2256c` | 2 / 2 | shuffled selector/dispatch path | — | r08=0x1->0x6 type:0->0 |
| 2 | `0x83ec33cc` → `0x4ec83461` | `0x23b28` | 7 / 7 | load word table[(x26 & 0xff)] and select next state | 0x4ec83461 | — |
| 3 | `0x4ec83461` → `0xd69a99c` | `0x268ac` | 10 / 10 | w19 < 0x00000086 → 0x0d69a99c; otherwise 0x5b972e16 (executed: true) | 0x5b972e16 | — |
| 4 | `0x0d69a99c` → `0xa607214b` | `0x28e24` | 10 / 10 | w19 < 0x00000086 → 0xa607214b; otherwise 0x2800c7e1 (executed: true) | 0xa607214b | — |
| 5 | `0xa607214b` → `0x77439633` | `0x27548` | 10 / 10 | w19 < 0x00000086 → 0x77439633; otherwise 0xedcffad6 (executed: true) | 0x77439633 | — |
| 6 | `0x77439633` → `0xcb513a7d` | `0x24d00` | 10 / 10 | w19 < 0x00000086 → 0xcb513a7d; otherwise 0xbcb965c2 (executed: true) | 0xbcb965c2 | — |
| 7 | `0xcb513a7d` → `0xb2f8f210` | `0x2362c` | 9 / 9 | w19 < 0x0000076f → 0xb2f8f210; otherwise 0xcfff54d8 (executed: true) | 0xb2f8f210 | — |
| 8 | `0xb2f8f210` → `0xd02172d6` | `0x29688` | 9 / 9 | w19 < 0x0000030d → 0xd02172d6; otherwise 0xe430d02c (executed: true) | 0xe430d02c | — |
| 9 | `0xd02172d6` → `0x682fc45e` | `0x1dcb4` | 7 / 7 | w19 == 0x0000019e → 0xd90b9520; otherwise 0x682fc45e (executed: false) | — | — |
| 10 | `0x682fc45e` → `0x15529b4a` | dispatcher | 46 / 46 | w20 == w27 → 0xe8afad93; otherwise 0x15529b4a (executed: false) | 0x15529b4a | — |
| 11 | `0x15529b4a` → `0x83ec33cc` | `0x21584` | 5 / 5 | shuffled selector/dispatch path | 0x83ec33cc | — |
| 12 | `0x83ec33cc` → `0x4ec83461` | `0x23b28` | 7 / 7 | load word table[(x26 & 0xff)] and select next state | 0x4ec83461 | — |
| 13 | `0x4ec83461` → `0xd69a99c` | `0x268ac` | 10 / 10 | w19 < 0x00000085 → 0x0d69a99c; otherwise 0x5b972e16 (executed: true) | 0x5b972e16 | — |
| 14 | `0x0d69a99c` → `0xa607214b` | `0x28e24` | 10 / 10 | w19 < 0x00000085 → 0xa607214b; otherwise 0x2800c7e1 (executed: true) | 0xa607214b | — |
| 15 | `0xa607214b` → `0x77439633` | `0x27548` | 10 / 10 | w19 < 0x00000085 → 0x77439633; otherwise 0xedcffad6 (executed: true) | 0x77439633 | — |
| 16 | `0x77439633` → `0xcb513a7d` | `0x24d00` | 10 / 10 | w19 < 0x00000085 → 0xcb513a7d; otherwise 0xbcb965c2 (executed: true) | 0xbcb965c2 | — |
| 17 | `0xcb513a7d` → `0xb2f8f210` | `0x2362c` | 9 / 9 | w19 < 0x0000076f → 0xb2f8f210; otherwise 0xcfff54d8 (executed: true) | 0xb2f8f210 | — |
| 18 | `0xb2f8f210` → `0xd02172d6` | `0x29688` | 9 / 9 | w19 < 0x0000030d → 0xd02172d6; otherwise 0xe430d02c (executed: true) | 0xe430d02c | — |
| 19 | `0xd02172d6` → `0x682fc45e` | `0x1dcb4` | 7 / 7 | w19 == 0x0000019e → 0xd90b9520; otherwise 0x682fc45e (executed: false) | — | — |
| 20 | `0x682fc45e` → `0x15529b4a` | dispatcher | 46 / 46 | w20 == w27 → 0xe8afad93; otherwise 0x15529b4a (executed: false) | 0x15529b4a | — |
| 21 | `0x15529b4a` → `0x83ec33cc` | `0x21584` | 5 / 5 | shuffled selector/dispatch path | 0x83ec33cc | — |
| 22 | `0x83ec33cc` → `0x4ec83461` | `0x23b28` | 7 / 7 | load word table[(x26 & 0xff)] and select next state | 0x4ec83461 | — |
| 23 | `0x4ec83461` → `0xd69a99c` | `0x268ac` | 10 / 10 | w19 < 0x00000084 → 0x0d69a99c; otherwise 0x5b972e16 (executed: true) | 0x5b972e16 | — |
| 24 | `0x0d69a99c` → `0xa607214b` | `0x28e24` | 10 / 10 | w19 < 0x00000084 → 0xa607214b; otherwise 0x2800c7e1 (executed: true) | 0xa607214b | — |
| 25 | `0xa607214b` → `0x77439633` | `0x27548` | 10 / 10 | w19 < 0x00000084 → 0x77439633; otherwise 0xedcffad6 (executed: true) | 0x77439633 | — |
| 26 | `0x77439633` → `0xcb513a7d` | `0x24d00` | 10 / 10 | w19 < 0x00000084 → 0xcb513a7d; otherwise 0xbcb965c2 (executed: true) | 0xbcb965c2 | — |
| 27 | `0xcb513a7d` → `0xb2f8f210` | `0x2362c` | 9 / 9 | w19 < 0x0000076f → 0xb2f8f210; otherwise 0xcfff54d8 (executed: true) | 0xb2f8f210 | — |
| 28 | `0xb2f8f210` → `0xd02172d6` | `0x29688` | 9 / 9 | w19 < 0x0000030d → 0xd02172d6; otherwise 0xe430d02c (executed: true) | 0xe430d02c | — |
| 29 | `0xd02172d6` → `0x682fc45e` | `0x1dcb4` | 7 / 7 | w19 == 0x0000019e → 0xd90b9520; otherwise 0x682fc45e (executed: false) | — | — |
| 30 | `0x682fc45e` → `0x15529b4a` | dispatcher | 46 / 46 | w20 == w27 → 0xe8afad93; otherwise 0x15529b4a (executed: false) | 0x15529b4a | — |
| 31 | `0x15529b4a` → `0x83ec33cc` | `0x21584` | 5 / 5 | shuffled selector/dispatch path | 0x83ec33cc | — |
| 32 | `0x83ec33cc` → `0x4ec83461` | `0x23b28` | 7 / 7 | load word table[(x26 & 0xff)] and select next state | 0x4ec83461 | — |
| 33 | `0x4ec83461` → `0xd69a99c` | `0x268ac` | 10 / 10 | w19 < 0x00000083 → 0x0d69a99c; otherwise 0x5b972e16 (executed: true) | 0x5b972e16 | — |
| 34 | `0x0d69a99c` → `0xa607214b` | `0x28e24` | 10 / 10 | w19 < 0x00000083 → 0xa607214b; otherwise 0x2800c7e1 (executed: true) | 0xa607214b | — |
| 35 | `0xa607214b` → `0x77439633` | `0x27548` | 10 / 10 | w19 < 0x00000083 → 0x77439633; otherwise 0xedcffad6 (executed: true) | 0x77439633 | — |
| 36 | `0x77439633` → `0xcb513a7d` | `0x24d00` | 10 / 10 | w19 < 0x00000083 → 0xcb513a7d; otherwise 0xbcb965c2 (executed: true) | 0xbcb965c2 | — |
| 37 | `0xcb513a7d` → `0xb2f8f210` | `0x2362c` | 9 / 9 | w19 < 0x0000076f → 0xb2f8f210; otherwise 0xcfff54d8 (executed: true) | 0xb2f8f210 | — |
| 38 | `0xb2f8f210` → `0xd02172d6` | `0x29688` | 9 / 9 | w19 < 0x0000030d → 0xd02172d6; otherwise 0xe430d02c (executed: true) | 0xe430d02c | — |
| 39 | `0xd02172d6` → `0x682fc45e` | `0x1dcb4` | 7 / 7 | w19 == 0x0000019e → 0xd90b9520; otherwise 0x682fc45e (executed: false) | — | — |
| 40 | `0x682fc45e` → `0x15529b4a` | dispatcher | 46 / 46 | w20 == w27 → 0xe8afad93; otherwise 0x15529b4a (executed: false) | 0x15529b4a | — |
| 41 | `0x15529b4a` → `0x83ec33cc` | `0x21584` | 5 / 5 | shuffled selector/dispatch path | 0x83ec33cc | — |
| 42 | `0x83ec33cc` → `0x4ec83461` | `0x23b28` | 7 / 7 | load word table[(x26 & 0xff)] and select next state | 0x4ec83461 | — |
| 43 | `0x4ec83461` → `0xd69a99c` | `0x268ac` | 10 / 10 | w19 < 0x00000082 → 0x0d69a99c; otherwise 0x5b972e16 (executed: true) | 0x5b972e16 | — |
| 44 | `0x0d69a99c` → `0xa607214b` | `0x28e24` | 10 / 10 | w19 < 0x00000082 → 0xa607214b; otherwise 0x2800c7e1 (executed: true) | 0xa607214b | — |
| 45 | `0xa607214b` → `0xedcffad6` | `0x27548` | 10 / 10 | w19 < 0x00000082 → 0x77439633; otherwise 0xedcffad6 (executed: false) | 0x77439633 | — |
| 46 | `0xedcffad6` → `0x7a90261f` | `0x25794` | 10 / 10 | w19 < 0x00000082 → 0x7a90261f; otherwise 0x3dc077d6 (executed: true) | 0x7a90261f | — |
| 47 | `0x7a90261f` → `0x4de8f85d` | `0x2683c` | 10 / 10 | w19 < 0x00000082 → 0x4de8f85d; otherwise 0x40fee7a4 (executed: true) | 0x4de8f85d | — |
| 48 | `0x4de8f85d` → `0x9359e442` | `0x269c4` | 10 / 10 | w19 < 0x00000082 → 0x80e242d0; otherwise 0x9359e442 (executed: false) | 0x9359e442 | — |
| 49 | `0x9359e442` → `0x6a535c8b` | `0x1fff0` | 10 / 10 | w19 < 0x00000082 → 0x07ac2960; otherwise 0x6a535c8b (executed: false) | 0x07ac2960 | — |
| 50 | `0x6a535c8b` → `0x6b1c8f72` | `0x208a4` | 8 / 8 | w19 == 0x00000082 → 0x6b1c8f72; otherwise 0x682fc45e (executed: true) | — | — |
| 51 | `0x6b1c8f72` → `0x7914e417` | `0x225d4` | 2735 / 1270 | long execution handler: HMAC-SHA256 ×1, fetch/decode ×2, range decode ×2 | 0x7914e417 | — |
| 52 | `0x7914e417` → `0x9d2333bd` | `0x1dda4` | 5 / 5 | advance VM state and select finalization | 0x9d2333bd | r01=0xd1->0x3b type:0->0; r08=0x6->0x9 type:0->0; r09=0x69c000->0x69f000 type:1->1; r1b=0x69d000->0x6a0000 type:1->1; r21=0x69e000->0x6a1000 type:1->1 |
| 53 | `0x9d2333bd` → `None` | `0x43244` | 5656 / 1268 | finalization: HMAC-SHA256 ×2, serialize ×2 | 0xf1a9e49d, 0x45cc1ea9 | — |

### 长 handler 与密码学

- `0x5b5446c5` handler `None`：730 条执行指令、392 个唯一 PC；session setup, header decode, first AES block。
- `0x6b1c8f72` handler `0x225d4`：2735 条执行指令、1270 个唯一 PC；long execution handler: HMAC-SHA256 ×1, fetch/decode ×2, range decode ×2。
- `0x9d2333bd` handler `0x43244`：5656 条执行指令、1268 个唯一 PC；finalization: HMAC-SHA256 ×2, serialize ×2。
