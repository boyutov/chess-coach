package com.chesscoach.chess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest {

    /** Perft: сверка числа позиций с эталонными значениями — лучшая проверка правил ходов. */
    @ParameterizedTest
    @CsvSource({
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 1, 20",
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 3, 8902",
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 4, 197281",
            "'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1', 3, 97862",
            "'8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1', 4, 43238",
            "'r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1', 3, 9467",
            "'rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8', 3, 62379",
    })
    void perftMatchesReferenceCounts(String fen, int depth, long expected) {
        assertEquals(expected, perft(Board.fromFen(fen), depth));
    }

    private long perft(Board board, int depth) {
        if (depth == 0) return 1;
        long total = 0;
        for (Move m : board.legalMoves()) {
            total += perft(board.apply(m), depth - 1);
        }
        return total;
    }

    @Test
    void fenRoundTrips() {
        String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 5 12";
        assertEquals(fen, Board.fromFen(fen).toFen());
    }

    @ParameterizedTest
    @CsvSource({
            "'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', g1f3, Nf3",
            "'r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1', e1g1, O-O",
            "'r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1', e1c1, O-O-O",
            "'6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1', a1a8, Ra8#",
            "'7k/P7/8/8/8/8/8/K7 w - - 0 1', a7a8q, a8=Q+",
            "'k7/8/8/3pP3/8/8/8/K7 w - d6 0 1', e5d6, exd6",
            "'k7/8/8/8/8/8/8/KN3N2 w - - 0 1', b1d2, Nbd2",
    })
    void sanNotation(String fen, String uci, String san) {
        assertEquals(san, Board.fromFen(fen).toSan(Move.parseUci(uci)));
    }

    @Test
    void cannotCastleThroughAttackedSquare() {
        Board board = Board.fromFen("4kr2/8/8/8/8/8/8/4K2R w K - 0 1");
        assertTrue(board.legalMoves().stream().noneMatch(m -> m.toUci().equals("e1g1")));
    }

    @Test
    void rejectsPositionWithoutKing() {
        assertThrows(IllegalArgumentException.class, () -> Board.fromFen("8/8/8/8/8/8/8/4K3 w - - 0 1"));
    }
}
