package com.chesscoach.game;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.engine.Engine.ScoredMove;

import java.util.List;

/**
 * Сделанный ход.
 *
 * @param ranking оценки всех ходов позиции до хода
 * @param review  разбор хода
 */
public record MoveRecord(Board before, Board after, Move move, String san, boolean byPlayer,
                         List<ScoredMove> ranking, MoveReview review) {

    public static MoveRecord byPlayer(Board before, Move move, List<ScoredMove> ranking) {
        return reviewed(before, move, true, ranking);
    }

    public static MoveRecord byBot(Board before, Move move, List<ScoredMove> ranking) {
        return reviewed(before, move, false, ranking);
    }

    private static MoveRecord reviewed(Board before, Move move, boolean byPlayer, List<ScoredMove> ranking) {
        return new MoveRecord(before, before.apply(move), move, before.toSan(move), byPlayer, ranking,
                MoveReview.of(before, move, ranking));
    }
}
