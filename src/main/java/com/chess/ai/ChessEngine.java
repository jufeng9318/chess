package com.chess.ai;

import com.chess.model.*;

/**
 * 象棋引擎统一接口
 * 所有引擎（自有AI / ElephantEye）都实现此接口
 * GameController 无需知道具体是哪个引擎
 */
public interface ChessEngine {
    /**
     * 获取当前局面下指定颜色的最佳着法
     * @param board 当前棋盘
     * @param color 要走的颜色
     * @return 最佳着法，无着法则返回 null
     */
    Move getBestMove(Board board, Color color);

    /**
     * 关闭引擎，释放资源（如有子进程）
     */
    void shutdown();
}
