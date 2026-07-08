# 中国象棋（Xiangqi）

一款使用 JavaFX 构建的跨平台中国象棋桌面游戏，支持人机对弈，并可接入外部 UCCI 引擎。

![Java](https://img.shields.io/badge/Java-17+-blue)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![Maven](https://img.shields.io/badge/Maven-3.8+-orange)

## 功能特性

- **完整规则实现**：将帅出行、仕士拱卫、相象巡河、马走日、车走直线、炮翻山、兵卒渡河
- **将帅对面检测**：自动识别并禁止形成将帅直接对脸
- **AI 对弈**：Alpha-Beta 剪枝搜索 + PVS + LMR + 置换表 + Killer Move + History Heuristic + 静态搜索，支持三个难度等级
- **外部引擎支持**：可通过 UCCI 协议接入 ElephantEye 等外部象棋引擎
- **游戏控制**：选子高亮、落点提示、吃子反馈、和棋/认输判定、新游戏/难度切换
- **中文棋子显示**：红方楷体 / 黑方仿宋，符合传统象棋视觉规范
- **CLI 测试工具**：提供命令行接口，支持 FEN 局面解析与引擎基准测试

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+

### 构建与运行

```bash
# 编译
mvn compile

# 运行
mvn compile exec:java
```

> Windows 下需指定 JDK 17 路径：
> ```bash
> set JAVA_HOME="C:\Program Files\Java\jdk-17"
> mvn compile exec:java
> ```

## 项目结构

```
src/main/java/com/chess/
├── Main.java                    # 程序入口
├── model/
│   ├── Board.java               # 棋盘（10×9 网格）
│   ├── Piece.java               # 棋子基类
│   ├── PieceType.java           # 棋子类型枚举
│   ├── Color.java               # 颜色枚举（RED/BLACK）
│   ├── Move.java                # 着法（execute/undo 支持搜索树）
│   └── MoveValidator.java       # 所有移动规则、将军检测、将帅对面检测
├── pieces/
│   ├── King.java                # 将/帥
│   ├── Advisor.java             # 士/仕
│   ├── Elephant.java            # 象/相（含巡河限制）
│   ├── Horse.java               # 马（含蹩马腿）
│   ├── Chariot.java             # 车
│   ├── Cannon.java              # 炮/砲（含翻山吃子）
│   └── Pawn.java                # 兵/卒（含过河横向移动）
├── ai/
│   ├── ChessAI.java             # Alpha-Beta 搜索 + PVS + LMR + 置换表 + 静态搜索
│   ├── Evaluator.java           # 局面评估（子力值 + PST + 王安全）
│   ├── ChessEngine.java         # 引擎接口
│   ├── OurEngine.java           # 自有引擎包装
│   ├── ElephantEyeEngine.java   # 外部 UCCI 引擎适配器（可选）
│   ├── EngineCLI.java           # 引擎 CLI 测试工具
│   └── Difficulty.java          # 难度枚举（EASY/MEDIUM/HARD）
├── view/
│   ├── BoardCanvas.java         # JavaFX Canvas 绘制棋盘与棋子
│   └── GameView.java            # 游戏主窗口与工具栏
└── controller/
    └── GameController.java      # 状态机（玩家回合 → AI 思考 → 玩家回合）
```

## AI 算法

| 组件 | 实现方式 |
|------|---------|
| 搜索算法 | Alpha-Beta 剪枝 + PVS（主要变例搜索）+ LMR（延迟缩减） |
| 置换表 | Zobrist 哈希 + 定长数组，支持 ALPHA/BETA/EXACT 三种节点类型 |
| 着法排序 | MVV-LVA（吃子着法优先）+ Killer Move + History Heuristic |
| 动态搜索 | 深度 ≥4 时限制搜索宽度；静态搜索（Quiescence Search）解决地平线效应 |
| 期望窗口 | Aspiration Window，以上一层分数为中心开窗口加速搜索 |
| 时间控制 | 6 秒上限 |
| 评估函数 | 子力价值 + 位置加成表（PST）+ 王安全 + 过河兵增值 |
| 难度控制 | EASY=4层 / MEDIUM=6层 / HARD=8层 |

### 子力价值

| 棋子 | 分值 |
|------|------|
| 将/帥 | 100000 |
| 车 | 1000 |
| 马 | 420 |
| 炮 | 450 |
| 仕/士 | 200 |
| 象/相 | 200 |
| 兵/卒 | 100 |

## 棋子字符说明

红方使用楷体，黑方使用仿宋，字符如下：

| 类型 | 红方 | 黑方 |
|------|------|------|
| 将帅 | 帥 | 將 |
| 仕士 | 仕 | 士 |
| 相象 | 相 | 象 |
| 马 | 馬 | 馬 |
| 车 | 車 | 車 |
| 炮砲 | 砲 | 炮 |
| 兵卒 | 兵 | 卒 |

## 技术栈

- **Java 17** — 编程语言
- **JavaFX 21** — UI 框架
- **Maven** — 构建工具
- **Alpha-Beta 剪枝 + PVS + LMR** — AI 搜索算法
