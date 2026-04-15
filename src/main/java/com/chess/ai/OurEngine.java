package com.chess.ai;

import com.chess.model.*;

/**
 * 自有AI引擎包装器
 * 将改进后的 ChessAI 封装为 ChessEngine 接口
 */
public class OurEngine implements ChessEngine {
    private final ChessAI ai;

    public OurEngine(Difficulty difficulty) {
        this.ai = new ChessAI(difficulty);
    }

    @Override
    public Move getBestMove(Board board, Color color) {
        return ai.getBestMove(board, color);
    }

    @Override
    public void shutdown() {
        // 自有AI无需额外清理
    }
}
