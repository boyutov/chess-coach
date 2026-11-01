package com.chesscoach.game;

import com.chesscoach.chess.Board;
import com.chesscoach.config.AnalysisProperties;
import com.chesscoach.engine.Engine;
import com.chesscoach.engine.Engine.ScoredMove;
import com.chesscoach.engine.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameServiceTest {

    private final AtomicInteger analyses = new AtomicInteger();
    private final Engine countingEngine = new Engine() {
        @Override
        public List<ScoredMove> rank(Board board, int depth, long timeLimitMs, Set<String> seen) {
            analyses.incrementAndGet();
            return super.rank(board, depth, timeLimitMs, seen);
        }
    };
    private final GameService service = new GameService(countingEngine, new InMemoryGameRepository(), new AnalysisProperties(3, 200));

    @Test
    void reviewsPlayerMoveAndBotReplies() {
        Game game = service.startGame(true, Level.BEGINNER);
        service.playMove(game.id(), "e2e4");

        List<MoveRecord> moves = game.moves();
        assertEquals(2, moves.size());
        assertNotNull(moves.get(0).review());
        assertFalse(moves.get(1).byPlayer());
        assertNotNull(moves.get(1).review());
        assertTrue(game.isPlayerTurn());
    }

    @Test
    void botMovesFirstWhenPlayerIsBlack() {
        Game game = service.startGame(false, Level.BEGINNER);
        assertEquals(1, game.moves().size());
        assertTrue(game.isPlayerTurn());
    }

    @Test
    void matingMoveIsBestAndEndsGame() {
        Game game = service.startLesson(Lesson.BACK_RANK);
        service.playMove(game.id(), "a1a8");
        assertEquals(MoveQuality.BEST, game.moves().get(0).review().quality());
        assertEquals(GameStatus.CHECKMATE, game.status());
    }

    @Test
    void rejectsIllegalMove() {
        Game game = service.startGame(true, Level.BEGINNER);
        assertThrows(IllegalMoveException.class, () -> service.playMove(game.id(), "e2e5"));
    }

    @Test
    void rejectsMoveAfterGameEnded() {
        Game game = service.startLesson(Lesson.BACK_RANK);
        service.playMove(game.id(), "a1a8");
        assertThrows(GameStateException.class, () -> service.playMove(game.id(), "e1e2"));
    }

    @Test
    void unknownGameIsNotFound() {
        assertThrows(GameNotFoundException.class, () -> service.get("missing"));
    }

    @Test
    void undoRestoresPositionWithoutNewAnalysis() {
        Game game = service.startGame(true, Level.BEGINNER);
        String start = game.board().toFen();
        List<ScoredMove> analysisBefore = game.analysis();
        service.playMove(game.id(), "d2d4");

        int analysesBeforeUndo = analyses.get();
        service.undo(game.id());

        assertEquals(start, game.board().toFen());
        assertTrue(game.moves().isEmpty());
        assertEquals(analysisBefore, game.analysis());
        assertEquals(analysesBeforeUndo, analyses.get());
    }

    @Test
    void undoWithoutMovesIsRejected() {
        Game game = service.startGame(true, Level.BEGINNER);
        assertThrows(GameStateException.class, () -> service.undo(game.id()));
    }

    @Test
    void hintIsLegalMove() {
        Game game = service.startGame(true, Level.BEGINNER);
        assertTrue(game.board().legalMoves().contains(service.hint(game.id())));
    }

    @ParameterizedTest
    @EnumSource(Lesson.class)
    void lessonsStartPlayable(Lesson lesson) {
        Game game = service.startLesson(lesson);
        assertEquals(GameStatus.ACTIVE, game.status());
        assertTrue(game.isPlayerTurn());
    }
}
