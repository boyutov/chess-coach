package com.chesscoach.game;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.config.AnalysisProperties;
import com.chesscoach.engine.Engine;
import com.chesscoach.engine.Engine.ScoredMove;
import com.chesscoach.engine.Level;
import org.springframework.stereotype.Service;

import java.util.List;

/** Ход партии: ход игрока, его разбор и ответ бота. */
@Service
public class GameService {

    private final Engine engine;
    private final GameRepository games;
    private final AnalysisProperties analysis;

    public GameService(Engine engine, GameRepository games, AnalysisProperties analysis) {
        this.engine = engine;
        this.games = games;
        this.analysis = analysis;
    }

    public Game startGame(boolean playerWhite, Level level) {
        return start(new Game(Board.start(), playerWhite, level, null));
    }

    public Game startLesson(Lesson lesson) {
        Board board = Board.fromFen(lesson.fen());
        return start(new Game(board, board.isWhiteToMove(), Level.INTERMEDIATE, lesson));
    }

    public Game get(String id) {
        return games.findById(id).orElseThrow(GameNotFoundException::new);
    }

    public Game playMove(String id, String uci) {
        Game game = get(id);
        synchronized (game) {
            requirePlayerTurn(game);
            Board board = game.board();
            Move move = Move.parseUci(uci);
            if (!board.legalMoves().contains(move)) {
                throw new IllegalMoveException(uci);
            }
            game.addMove(MoveRecord.byPlayer(board, move, game.analysis()));
            advance(game);
            return game;
        }
    }

    public Game undo(String id) {
        Game game = get(id);
        if (!game.undoLastPlayerMove()) {
            throw new GameStateException("Отменять пока нечего");
        }
        return game;
    }

    /** Лучший ход текущей позиции. */
    public Move hint(String id) {
        Game game = get(id);
        synchronized (game) {
            requirePlayerTurn(game);
            return game.analysis().get(0).move();
        }
    }

    private Game start(Game game) {
        games.save(game);
        synchronized (game) {
            advance(game);
        }
        return game;
    }

    private static void requirePlayerTurn(Game game) {
        if (game.status() != GameStatus.ACTIVE) {
            throw new GameStateException("Партия уже закончена");
        }
        if (!game.isPlayerTurn()) {
            throw new GameStateException("Сейчас не ваш ход");
        }
    }

    /** Отвечает за бота, если сейчас его очередь, и заранее оценивает ходы для игрока. */
    private void advance(Game game) {
        if (game.status() == GameStatus.ACTIVE && !game.isPlayerTurn()) {
            Board board = game.board();
            game.addMove(MoveRecord.byBot(board, engine.chooseMove(board, game.level(), game.seenPositions())));
        }
        if (game.status() == GameStatus.ACTIVE) {
            List<ScoredMove> ranking = engine.rank(game.board(), analysis.depth(), analysis.timeLimitMs(), game.seenPositions());
            game.setAnalysis(ranking);
        }
    }
}
