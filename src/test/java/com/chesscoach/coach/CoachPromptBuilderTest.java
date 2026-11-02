package com.chesscoach.coach;

import com.chesscoach.chess.Board;
import com.chesscoach.config.AnalysisProperties;
import com.chesscoach.engine.Engine;
import com.chesscoach.engine.Level;
import com.chesscoach.game.Game;
import com.chesscoach.game.GameService;
import com.chesscoach.game.InMemoryGameRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoachPromptBuilderTest {

    private final Engine engine = new Engine();
    private final GameService games = new GameService(engine, new InMemoryGameRepository(), new AnalysisProperties(3, 200));
    private final CoachPromptBuilder builder = new CoachPromptBuilder(engine);

    @Test
    void includesVerdictAndMoveAskedAbout() {
        Game game = games.startGame(true, Level.BEGINNER, null);
        games.playMove(game.id(), "a2a3");

        String prompt = builder.build(game, 0, "Почему не Кf3?");

        assertTrue(prompt.contains("Разбираемый ход ученика: 1. a3"), prompt);
        assertTrue(prompt.contains("Вердикт:"), prompt);
        assertTrue(prompt.contains("- Nf3 "), prompt);
        assertTrue(prompt.endsWith("Вопрос ученика: Почему не Кf3?"), prompt);
    }

    @Test
    void reviewsBotMoveWhenAskedAboutIt() {
        Game game = games.startGame(true, Level.BEGINNER, null);
        games.playMove(game.id(), "a2a3");
        String prompt = builder.build(game, 1, "?");
        assertTrue(prompt.contains("Разбираемый ход бота: 1... " + game.moves().get(1).san()), prompt);
        assertTrue(prompt.contains("после хода бота оценка"), prompt);
    }

    @Test
    void lastPlayerMoveIsReviewedWithBotReply() {
        Game game = games.startGame(true, Level.BEGINNER, null);
        games.playMove(game.id(), "a2a3");
        String prompt = builder.build(game, 0, "?");
        assertTrue(prompt.contains("Разбираемый ход ученика: 1. a3"), prompt);
        assertTrue(prompt.contains("Разбираемый ход бота: 1... "), prompt);
    }

    @Test
    void earlierMoveIsReviewedAlone() {
        Game game = games.startGame(true, Level.BEGINNER, null);
        games.playMove(game.id(), "a2a3");
        games.playMove(game.id(), game.board().legalMoves().get(0).toUci());
        String prompt = builder.build(game, 0, "?");
        assertTrue(prompt.contains("Разбираемый ход ученика: 1. a3"), prompt);
        assertFalse(prompt.contains("Разбираемый ход бота"), prompt);
    }

    @Test
    void botOpeningMoveIsReviewedBeforePlayerMoves() {
        Game game = games.startGame(false, Level.BEGINNER, null);
        assertTrue(builder.build(game, null, "?").contains("Разбираемый ход бота: 1. "));
    }

    @Test
    void recognizesMovesWithoutMatchingInsideOtherMoves() {
        Board board = Board.start();
        assertEquals(List.of("g1f3"), uci(board, "почему не Кf3?"));
        assertEquals(List.of("e2e4"), uci(board, "а если e4"));
        assertEquals(List.of(), uci(board, "Ne4 или слово e45"));
    }

    private static List<String> uci(Board board, String question) {
        return CoachPromptBuilder.mentionedMoves(board, question).stream().map(move -> move.toUci()).toList();
    }
}
