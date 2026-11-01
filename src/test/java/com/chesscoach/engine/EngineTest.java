package com.chesscoach.engine;

import com.chesscoach.chess.Board;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {

    private final Engine engine = new Engine();

    @Test
    void findsMateInOne() {
        Board board = Board.fromFen("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1");
        assertEquals("a1a8", engine.chooseMove(board, Level.STRONG, Set.of()).toUci());
    }

    @Test
    void reportsMateDistance() {
        Board board = Board.fromFen("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1");
        int score = engine.rank(board, 3, 2_000, Set.of()).get(0).score();
        assertTrue(Engine.isMateScore(score));
        assertEquals(1, Engine.mateInMoves(score));
    }

    @Test
    void takesHangingQueen() {
        Board board = Board.fromFen("rnb1kbnr/pppp1ppp/8/4p3/3qP3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1");
        assertEquals("f3d4", engine.chooseMove(board, Level.STRONG, Set.of()).toUci());
    }

    @Test
    void avoidsStalemateWhenWinning() {
        Board board = Board.fromFen("7k/8/5K2/6Q1/8/8/8/8 w - - 0 1");
        // Qg6 здесь пат, а Qg7 — мат.
        assertEquals("g5g7", engine.chooseMove(board, Level.STRONG, Set.of()).toUci());
    }

    @Test
    void levelBeginnerStillPlaysLegalMove() {
        Board board = Board.start();
        assertTrue(board.legalMoves().contains(engine.chooseMove(board, Level.BEGINNER, Set.of())));
    }
}
