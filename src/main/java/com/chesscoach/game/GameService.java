package com.chesscoach.game;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.config.AnalysisProperties;
import com.chesscoach.engine.Engine;
import com.chesscoach.engine.Engine.ScoredMove;
import com.chesscoach.engine.Level;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Ход партии: ход игрока, ответ бота и разбор обоих ходов. */
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

    public Game startGame(boolean playerWhite, Level level, String owner) {
        Game game = new Game(Board.start(), playerWhite, level, null);
        game.setOwner(owner);
        return start(game);
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
        requireWritable(game);
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

    private static void requireWritable(Game game) {
        if (game.readOnly()) throw new GameStateException("Это сохранённая партия, её можно только смотреть");
    }

    private static void requirePlayerTurn(Game game) {
        requireWritable(game);
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
            playBotMove(game);
        }
        if (game.status() == GameStatus.ACTIVE) {
            List<ScoredMove> ranking = engine.rank(game.board(), analysis.depth(), analysis.timeLimitMs(), game.seenPositions());
            game.setAnalysis(ranking);
        }
    }

    /**
     * Ход бота разбирается той же глубиной, что и ход игрока, а не глубиной его уровня: иначе слабый бот
     * сам бы не замечал своих ошибок. Разбор идёт параллельно с выбором хода, чтобы бот не отвечал дольше.
     */
    private void playBotMove(Game game) {
        Board board = game.board();
        Set<String> seen = game.seenPositions();
        CompletableFuture<List<ScoredMove>> ranking = CompletableFuture.supplyAsync(
                () -> engine.rank(board, analysis.depth(), analysis.timeLimitMs(), seen));
        Move move = engine.chooseMove(board, game.level(), seen);
        game.addMove(MoveRecord.byBot(board, move, ranking.join()));
    }
}
