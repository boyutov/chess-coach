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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoachPromptBuilderTest {

    private final Engine engine = new Engine();
    private final GameService games = new GameService(engine, new InMemoryGameRepository(), new AnalysisProperties(3, 200));
    private final CoachPromptBuilder builder = new CoachPromptBuilder(engine);

    @Test
    void includesVerdictAndMoveAskedAbout() {
        Game game = games.startGame(true, Level.BEGINNER);
        games.playMove(game.id(), "a2a3");

        String prompt = builder.build(game, 0, "Почему не Кf3?");

        assertTrue(prompt.contains("Разбираемый ход ученика: 1. a3"), prompt);
        assertTrue(prompt.contains("Вердикт:"), prompt);
        assertTrue(prompt.contains("- Nf3 "), prompt);
        assertTrue(prompt.endsWith("Вопрос ученика: Почему не Кf3?"), prompt);
    }

    @Test
    void botMoveFocusFallsBackToPlayerMove() {
        Game game = games.startGame(true, Level.BEGINNER);
        games.playMove(game.id(), "a2a3");
        assertTrue(builder.build(game, 1, "?").contains("Разбираемый ход ученика: 1. a3"));
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
