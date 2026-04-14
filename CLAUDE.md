# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

**Requires Java 17** (system default may be Java 8). Set `JAVA_HOME` before running Maven:

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"

mvn compile              # Build
mvn compile exec:java    # Build + run
mvn compile -o           # Offline build (dependencies already downloaded)
```

## Architecture

Single JavaFX desktop application for a Chinese Chess (Xiangqi) game.

**Data layer** (`model/`): `Board` holds 10×9 grid state. `Piece` objects are position-aware (row/col fields). `Move` supports execute/undo for the AI search tree. `MoveValidator` implements all piece movement rules including Chinese chess specifics (blocking, river crossing, palace bounds).

**AI layer** (`ai/`): `ChessAI` uses alpha-beta pruning with启发式排序 (moves that capture pieces searched first). `Evaluator` combines piece material values with per-square positional bonus tables. `Difficulty` enum controls search depth (EASY=2, MEDIUM=4, HARD=6).

**Rendering** (`view/`): `BoardCanvas` extends `Canvas` — all drawing is imperative via `GraphicsContext`. No external image assets. Wood-grain background, traditional KaiTi font for piece characters, gold highlight for selected piece, green dots for valid moves.

**Control** (`controller/`): `GameController` manages a simple state machine (PLAYER_TURN → AI_THINKING → PLAYER_TURN). AI runs on a background thread; results are pushed back to the JavaFX thread via `Platform.runLater`.

## Key Conventions

- `Color.RED` always moves first and is the human player's side.
- All piece movement rules are centralized in `MoveValidator` — piece subclasses only override `getChar()` for display text.
- AI search is capped at 6 moves per node regardless of depth to avoid combinatorial explosion.
- `Board.copy()` is a shallow copy of the grid array (piece references are shared); this is intentional since `Move.execute/undo` restores state in-place.
