package com.chesscoach.archive;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.engine.Engine;
import com.chesscoach.engine.Engine.ScoredMove;
import com.chesscoach.engine.Level;
import com.chesscoach.game.Game;
import com.chesscoach.game.GameNotFoundException;
import com.chesscoach.game.GameRepository;
import com.chesscoach.game.GameStateException;
import com.chesscoach.game.GameStatus;
import com.chesscoach.game.MoveRecord;
import com.chesscoach.game.MoveReview;
import com.chesscoach.online.OnlineGame;
import com.chesscoach.online.PlayerAccount;
import com.chesscoach.online.PlayerStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** Сохраняет партии, разбирает партии с людьми после их окончания и восстанавливает их для просмотра. */
@Service
public class ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);
    private static final String BOT_NAME = "Бот";

    public enum AnalysisStatus { NONE, RUNNING, DONE }

    /**
     * Партия глазами игрока.
     *
     * @param game     восстановленная партия с разбором, если он есть; номер у неё свой, для чата с тренером
     * @param progress сколько ходов уже разобрано, пока идёт разбор
     */
    public record View(ArchivedGame saved, Game game, boolean viewerWhite, AnalysisStatus analysis, int progress,
                       GameReport report) {
    }

    private final ArchiveStore store;
    private final PlayerStore players;
    private final GameRepository games;
    private final Engine engine;
    private final int depth;
    private final long timeLimitMs;
    private final Map<String, AtomicInteger> running = new ConcurrentHashMap<>();
    /** Разбор один за раз: на слабом сервере он не должен мешать играть. */
    private final ExecutorService analyzer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "game-analysis");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        return thread;
    });

    public ArchiveService(ArchiveStore store, PlayerStore players, GameRepository games, Engine engine,
                          @Value("${analysis.depth}") int depth,
                          @Value("${analysis.report-time-limit-ms}") long timeLimitMs) {
        this.store = store;
        this.players = players;
        this.games = games;
        this.engine = engine;
        this.depth = depth;
        this.timeLimitMs = timeLimitMs;
    }

    /** Партия с ботом сохраняется после каждого хода: у неё нет сдачи, и её часто бросают. Оценки уже посчитаны. */
    public void saveBotGame(Game game) {
        String owner = game.owner();
        if (owner == null || game.lesson() != null) return;
        synchronized (game) {
            List<MoveRecord> moves = game.moves();
            if (moves.isEmpty()) {
                // После отмены всех ходов сохранять нечего.
                store.delete(game.id());
                return;
            }
            String ownerName = players.find(owner).map(PlayerAccount::nickname).orElse(owner);
            String botName = BOT_NAME + " · " + levelName(game.level());
            boolean white = game.playerWhite();
            store.save(new ArchivedGame(game.id(), white ? owner : null, white ? null : owner,
                    white ? ownerName : botName, white ? botName : ownerName, game.level().name(),
                    game.status().name(), null, uci(moves), moves.size(),
                    AnalysisCodec.encode(moves.stream().map(MoveRecord::ranking).toList()),
                    System.currentTimeMillis()));
        }
    }

    public void saveFinishedOnlineGame(OnlineGame online) {
        List<MoveRecord> moves = online.game().moves();
        store.save(new ArchivedGame(online.id(), online.white(), online.black(), name(online.white()),
                name(online.black()), null, online.resignedBy() != null ? "RESIGNED" : online.game().status().name(),
                online.resignedBy(), uci(moves), moves.size(), null, System.currentTimeMillis()));
    }

    /** Партии, закончившиеся до появления архива. */
    public void saveIfMissing(OnlineGame online) {
        if (!store.exists(online.id())) saveFinishedOnlineGame(online);
    }

    public List<ArchivedGame> list(String player) {
        return store.listFor(player);
    }

    public View view(String player, String id) {
        ArchivedGame saved = find(player, id);
        boolean viewerWhite = player.equals(saved.ownerWhite());
        Game game = restore(saved, viewerWhite);
        AtomicInteger progress = running.get(id);
        AnalysisStatus status = saved.analysis() != null ? AnalysisStatus.DONE
                : progress != null ? AnalysisStatus.RUNNING : AnalysisStatus.NONE;
        GameReport report = null;
        if (status == AnalysisStatus.DONE) {
            games.save(game);
            report = GameReport.of(game.moves(), saved.whiteName(), saved.blackName());
        }
        return new View(saved, game, viewerWhite, status, progress == null ? 0 : progress.get(), report);
    }

    /** Запускает разбор, если его ещё нет. В архив партия с людьми попадает только законченной. */
    public void startAnalysis(String player, String id) {
        ArchivedGame saved = find(player, id);
        if (saved.analysis() != null) return;
        if ("ACTIVE".equals(saved.status())) throw new GameStateException("Партия ещё не закончена");
        AtomicInteger progress = new AtomicInteger();
        if (running.putIfAbsent(id, progress) != null) return;
        analyzer.execute(() -> {
            try {
                store.saveAnalysis(id, AnalysisCodec.encode(analyze(saved, progress)));
            } catch (RuntimeException e) {
                log.warn("Не удалось разобрать партию {}", id, e);
            } finally {
                running.remove(id);
            }
        });
    }

    private List<List<ScoredMove>> analyze(ArchivedGame saved, AtomicInteger progress) {
        List<List<ScoredMove>> rankings = new ArrayList<>();
        Board board = Board.start();
        Set<String> seen = new HashSet<>(Set.of(board.positionKey()));
        for (String uci : saved.moveList()) {
            rankings.add(engine.rank(board, depth, timeLimitMs, seen));
            board = board.apply(Move.parseUci(uci));
            seen.add(board.positionKey());
            progress.incrementAndGet();
        }
        return rankings;
    }

    private ArchivedGame find(String player, String id) {
        return store.find(id).filter(game -> game.hasOwner(player)).orElseThrow(GameNotFoundException::new);
    }

    /** Копия только для просмотра и вопросов тренеру. У каждого игрока своя: «ученик» в подсказках тренера разный. */
    private static Game restore(ArchivedGame saved, boolean viewerWhite) {
        Level level = saved.level() == null ? null : Level.valueOf(saved.level());
        Game game = new Game(saved.id() + (viewerWhite ? "-w" : "-b"), Board.start(), viewerWhite, level, null);
        List<List<ScoredMove>> rankings = saved.analysis() == null ? null : AnalysisCodec.decode(saved.analysis());
        Board board = Board.start();
        List<String> moves = saved.moveList();
        for (int ply = 0; ply < moves.size(); ply++) {
            Move move = Move.parseUci(moves.get(ply));
            List<ScoredMove> ranking = rankings == null ? List.of() : rankings.get(ply);
            MoveReview review = rankings == null ? null : MoveReview.of(board, move, ranking);
            game.addMove(new MoveRecord(board, board.apply(move), move, board.toSan(move),
                    board.isWhiteToMove() == viewerWhite, ranking, review));
            board = board.apply(move);
        }
        game.markReadOnly();
        return game;
    }

    private static String uci(List<MoveRecord> moves) {
        return moves.stream().map(record -> record.move().toUci()).collect(Collectors.joining(" "));
    }

    private String name(String key) {
        return players.find(key).map(PlayerAccount::nickname).orElse(key);
    }

    private static String levelName(Level level) {
        return switch (level) {
            case BEGINNER -> "Новичок";
            case AMATEUR -> "Любитель";
            case INTERMEDIATE -> "Средний";
            case STRONG -> "Сильный";
        };
    }

    /** Итог для игрока: WIN, LOSS, DRAW или UNFINISHED. */
    public static String outcome(ArchivedGame saved, String player) {
        boolean white = player.equals(saved.ownerWhite());
        String status = saved.status();
        if ("ACTIVE".equals(status)) return "UNFINISHED";
        if ("RESIGNED".equals(status)) return player.equals(saved.resignedBy()) ? "LOSS" : "WIN";
        if (GameStatus.CHECKMATE.name().equals(status)) {
            boolean whiteWasMated = saved.plies() % 2 == 0;
            return whiteWasMated == white ? "LOSS" : "WIN";
        }
        return "DRAW";
    }

    @PreDestroy
    void shutdown() {
        analyzer.shutdownNow();
    }
}
