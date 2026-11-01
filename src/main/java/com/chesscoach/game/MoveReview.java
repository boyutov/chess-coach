package com.chesscoach.game;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.engine.Engine.ScoredMove;

import java.util.List;

/**
 * Разбор хода игрока.
 *
 * @param evalBefore оценка лучшего хода в сантипешках с точки зрения белых
 * @param evalAfter  оценка сыгранного хода в сантипешках с точки зрения белых
 */
public record MoveReview(MoveQuality quality, String bestSan, String bestUci, double winPercentLoss,
                         int evalBefore, int evalAfter) {

    private static final int WIN_PERCENT_CP_LIMIT = 2000;
    private static final double WIN_PERCENT_SLOPE = 0.00368208;

    /** Сравнивает сыгранный ход с лучшим. Ранжирование должно содержать все легальные ходы позиции. */
    public static MoveReview of(Board board, Move played, List<ScoredMove> ranking) {
        ScoredMove best = ranking.get(0);
        int playedScore = ranking.stream()
                .filter(scored -> scored.move().equals(played))
                .findFirst()
                .orElseThrow()
                .score();
        double loss = Math.max(0, winPercent(best.score()) - winPercent(playedScore));
        MoveQuality quality = MoveQuality.classify(best.move().equals(played), ranking.size() == 1, loss);
        int whiteSign = board.isWhiteToMove() ? 1 : -1;
        return new MoveReview(quality, board.toSan(best.move()), best.move().toUci(), loss,
                best.score() * whiteSign, playedScore * whiteSign);
    }

    /** Переводит оценку в сантипешках в шанс на победу, %. Формула как на lichess. */
    static double winPercent(int centipawns) {
        int clamped = Math.max(-WIN_PERCENT_CP_LIMIT, Math.min(WIN_PERCENT_CP_LIMIT, centipawns));
        return 50 + 50 * (2 / (1 + Math.exp(-WIN_PERCENT_SLOPE * clamped)) - 1);
    }
}
