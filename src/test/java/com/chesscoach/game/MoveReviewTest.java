package com.chesscoach.game;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.engine.Engine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveReviewTest {

    @Test
    void hangingQueenIsABlunder() {
        // Qg4 подставляет ферзя под слона c8.
        Board board = Board.fromFen("rnbqkbnr/ppp2ppp/8/3pp3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 3");
        var ranking = new Engine().rank(board, 4, 3_000, Set.of());
        assertEquals(MoveQuality.BLUNDER, MoveReview.of(board, Move.parseUci("d1g4"), ranking).quality());
    }

    @ParameterizedTest
    @CsvSource({
            "true, false, 0, BEST",
            "false, true, 50, FORCED",
            "false, false, 1, EXCELLENT",
            "false, false, 5, GOOD",
            "false, false, 15, INACCURACY",
            "false, false, 25, MISTAKE",
            "false, false, 35, BLUNDER",
    })
    void classifiesByWinPercentLoss(boolean best, boolean only, double loss, MoveQuality expected) {
        assertEquals(expected, MoveQuality.classify(best, only, loss));
    }

    @Test
    void winPercentIsEvenAtZeroAndCapped() {
        assertEquals(50, MoveReview.winPercent(0), 0.001);
        assertEquals(MoveReview.winPercent(2000), MoveReview.winPercent(99_000), 0.001);
    }
}
