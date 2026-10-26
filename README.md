# 茶工艺 / Tea Craft

Minecraft 模组：按群系温度种茶，经三条真实工艺链（绿茶 / 红茶 / 凤凰单丛）加工，
再配一整套茶具与家具泡制、品饮。

作者：[bilibili 黑山大叔](https://space.bilibili.com/)

---

## 三支工程

同一个模组，三套加载器实现。MC 那一层的代码高度共享，加载器层各自独立。

| 目录 | MC 版本 | 加载器 | 产物 |
|---|---|---|---|
| `tea-craft-fabric-26.3/` | 26.3 | Fabric | `tea-craft-fabric-26.3-1.4.12.jar` |
| `tea-craft-neoforge-26.3/` | 26.3 | NeoForge | `tea-craft-neoforge-26.3-1.4.12.jar` |
| `tea-craft-neoforge-1.21.1/` | 1.21.1 | NeoForge | `tea-craft-neoforge-1.21.1-1.1.6.jar` |

> **为什么是三个独立工程而不是一套代码多平台**
> 加载器层的 API 完全不同（注册表、事件、网络、客户端钩子），
> 强行抽象反而更难维护。做法是「以 Fabric 支为骨架」，
> 移植时只改加载器层，MC 那层代码原样复用。

---

## 玩法概要

**找茶树种茶**
茶树种无法合成 —— 击杀僵尸有概率掉落（概率与「僵尸手持铁锹」严格相同，
困难 3.33% / 其它 0.67%，由反编译原版 `populateDefaultEquipmentSlots` 得到）。

**温度决定品质**
茶树会记住自己生长的群系温度，成品茶等级由它决定：

| 群系温度 | 等级 | 效果时长 |
|---|---|---|
| 热带 (≥1.10) | 1 级 | 20 分钟 |
| 亚热带 (≥0.85) | 2 级 | 40 分钟 |
| 温带 (≥0.55) | 3 级 | 60 分钟 |
| 寒带 (≥0.15) | 4 级 | 80 分钟 |
| 极寒 (<0.15) | 5 级 | 100 分钟 |

越冷的地方茶越好。寒带与极寒必须在茶树 2 格内放篝火，否则不生长。

**三条工艺链**（各 6 个阶段）
- 绿茶：晒青 → 杀青 → 揉捻 → 炒干 → 成品
- 红茶：萎凋 → 揉捻 → 发酵 → 毛火 → 足火 → 成品
- 凤凰单丛：晒青 → 摇青 → 杀青 → 揉捻 → 发酵 → 碳焙

**家具与茶具**
茶桌、烧水茶桌（嵌岩浆，不烫伤）、茶椅（可坐）、茶盒（27 格 × 999）、
茶盘（自放盖碗与茶杯）、烧水壶（5 秒烧开）。

详细玩法见 [`docs/茶工艺-使用说明.txt`](docs/茶工艺-使用说明.txt)。

---

## 构建

三支都需要 **JDK 25 以下不兼容**：26.3 两支用 JDK 25，1.21.1 用 JDK 21。

```bash
# 26.3 Fabric
cd tea-craft-fabric-26.3
set JAVA_HOME=<jdk25 路径>
gradlew.bat build --offline

# 26.3 NeoForge
cd tea-craft-neoforge-26.3
set JAVA_HOME=<jdk25 路径>
gradlew.bat build --offline

# 1.21.1 NeoForge
cd tea-craft-neoforge-1.21.1
set JAVA_HOME=<jdk25 同款 jdk21> 路径
gradlew.bat build --offline
```

产物在各自 `build/libs/`。

### 版本坐标说明

26.3 两支的 `gradle.properties` 里写的 NeoForge / Fabric 版本都是
**离线缓存里实际存在的构建**（NeoForge 是 `26.3.0.67-beta`，不是最新的 0.86）。
`neoforge.mods.toml` 写成下界形式 `[26.3.0.67-beta,)`，更新的构建也满足。
这意味着编译用的 API 可能比运行环境略旧，**装好后需要实测**。

---

## 自检

26.3 两支带运行时自检（1.21.1 支没有）。

```bash
set TEACRAFT_SELFCHECK=1
gradlew.bat runServer --offline
```

日志里会看到：

```
[teacraft] TEST <名称> -> PASS/FAIL  <说明>
[teacraft] ===== 自检结束 pass=1029 fail=0 skip=0 =====
```

覆盖范围：注册完整性、物品名与语言键、资源链路、工艺阶段推进、
方块实体交互、菜单、掉落、HUD、跨版本行为约束。

**自检不是装饰** —— 这个项目大量 bug 属于「编译能过、进游戏才炸」，
例如实体缺渲染器（渲染线程 NPE）、菜单缺 buf 写入器（客户端崩溃）、
`items/` 指向错误导致模型变换失效。这些都在自检里有对应断言。

> 26.3 NeoForge 支的 `gradlew runServer` 不可用（ModDevGradle 要下载
> `moddev-config.json`，源站不可达）。该支提供了 `tools/run-server.ps1`
> 自行拼接 classpath 启动。

---

## 文档

- [`docs/茶工艺-使用说明.txt`](docs/茶工艺-使用说明.txt) —— 面向玩家
- [`docs/茶工艺-技术说明.txt`](docs/茶工艺-技术说明.txt) —— 架构、API 差异、实现要点
- [`docs/茶工艺-经验与踩坑总结.txt`](docs/茶工艺-经验与踩坑总结.txt) —— **推荐先读这份**

第三份记录了 26 条实际踩到的坑，每条包含「现象 / 原因 / 修法 / 教训」。
几个典型：

- 完全不可见的实体仍然必须注册渲染器，否则渲染线程 NPE
- `openMenu` 少传数据写入器，客户端 `buf` 为 null 直接崩
- GUI 数字被图标遮住是 **z 轴层级**问题（原版装饰绘制在 `z=200`），不是绘制顺序
- NeoForge 延迟注册：静态字段初始化顺序、静态块里不能 `.get()`、注册器必须挂总线
- 断言写太松会假绿 —— 必须做破坏性验证（本项目踩了四次）

---

## 许可证

见 [LICENSE](LICENSE)。

模组代码与资源版权归作者所有。禁止商用。
