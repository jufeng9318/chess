# 中国象棋（Xiangqi）

一款使用 JavaFX 构建的跨平台中国象棋桌面游戏，支持 AI 对弈。

![Java](https://img.shields.io/badge/Java-17+-blue)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![Maven](https://img.shields.io/badge/Maven-3.8+-orange)

## 功能特性

- **完整规则实现**：将帅出行、仕士拱卫、相象巡河、马走日车走直线、炮翻山、兵渡河
- **将帅对面检测**：自动识别并禁止形成将帅直接对脸
- ** AI 对弈**：Alpha-Beta 剪枝搜索 + MVV-LVA 着法排序 + Killer Move 启发，支持三个难度等级
- **游戏控制**：选子、落点高亮、吃子提示、和棋/认输判定
- **中文棋子显示**：红方楷体 / 黑方仿宋，符合传统象棋视觉规范

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
> export JAVA_HOME="/c/Program Files/Java/jdk-17"
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
│   └── MoveValidator.java       # 所有移动规则与将军检测
├── pieces/
│   ├── King.java                # 将/帥
│   ├── Advisor.java             # 士/仕
│   ├── Elephant.java            # 象/相（含巡河限制）
│   ├── Horse.java               # 马（含蹩马腿）
│   ├── Chariot.java             # 车
│   ├── Cannon.java              # 炮（含翻山吃子）
│   └── Pawn.java                # 兵/卒（含过河横向移动）
├── ai/
│   ├── ChessAI.java             # Alpha-Beta 搜索 + 着法排序
│   ├── Evaluator.java           # 局面评估（子力值 + 位置表）
│   └── Difficulty.java          # 难度枚举（EASY/MEDIUM/HARD）
├── view/
│   └── BoardCanvas.java         # JavaFX Canvas 绘制棋盘与棋子
└── controller/
    └── GameController.java      # 状态机（玩家回合 → AI 思考 → 玩家回合）
```

## AI 算法

| 组件 | 实现方式 |
|------|---------|
| 搜索算法 | Alpha-Beta 剪枝 |
| 着法排序 | MVV-LVA（吃子着法优先搜索大子） |
| 启发式 | Killer Move（导致剪枝的着法优先） |
| 动态宽度 | 叶节点允许搜索更多着法（6→10） |
| 评估函数 | 子力价值 + 每棋子位置加成表 |
| 难度控制 | EASY=2层 / MEDIUM=4层 / HARD=6层 |

### 子力价值

| 棋子 | 分值 |
|------|------|
| 将/帥 | 10000 |
| 车 | 1000 |
| 马 | 450 |
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
| 炮 | 炮 | 炮 |
| 兵卒 | 兵 | 卒 |

## 技术栈

- **Java 17** — 编程语言
- **JavaFX 21** — UI 框架
- **Maven** — 构建工具
- **Alpha-Beta 剪枝** — AI 搜索算法
