package com.chesscoach.game;

import com.chesscoach.chess.Board;
import com.chesscoach.engine.Engine.ScoredMove;
import com.chesscoach.engine.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Состояние одной партии. Отдельные методы потокобезопасны; чтобы несколько вызовов видели одну и ту же
 * позицию, вызывающий код синхронизируется на объекте партии.
 */
public class Game {

    private static final int REPETITION_DRAW_COUNT = 3;
    private static final int FIFTY_MOVE_DRAW_HALFMOVES = 100;

    private final String id = UUID.randomUUID().toString();
    private final Board initial;
    private final boolean playerWhite;
    private final Level level;
    private final Lesson lesson;
    private final List<MoveRecord> moves = new ArrayList<>();
    /** Оценки всех ходов текущей позиции. Заполнены, только когда очередь игрока. */
    private List<ScoredMove> analysis = List.of();

    public Game(Board initial, boolean playerWhite, Level level, Lesson lesson) {
        this.initial = initial;
        this.playerWhite = playerWhite;
        this.level = level;
        this.lesson = lesson;
    }

    public String id() {
        return id;
    }

    public boolean playerWhite() {
        return playerWhite;
    }

    public Level level() {
        return level;
    }

    public Lesson lesson() {
        return lesson;
    }

    public Board initial() {
        return initial;
    }

    public synchronized Board board() {
        return moves.isEmpty() ? initial : moves.get(moves.size() - 1).after();
    }

    public synchronized boolean isPlayerTurn() {
        return board().isWhiteToMove() == playerWhite;
    }

    public synchronized List<MoveRecord> moves() {
        return List.copyOf(moves);
    }

    public synchronized void addMove(MoveRecord move) {
        moves.add(move);
        analysis = List.of();
    }

    public synchronized List<ScoredMove> analysis() {
        return analysis;
    }

    public synchronized void setAnalysis(List<ScoredMove> analysis) {
        this.analysis = analysis;
    }

    /**
     * Отменяет последний ход игрока вместе с ответом соперника и возвращает сохранённую в этом ходе
     * оценку позиции, поэтому повторный анализ не нужен.
     *
     * @return false, если отменять нечего
     */
    public synchronized boolean undoLastPlayerMove() {
        int last = moves.size() - 1;
        while (last >= 0 && !moves.get(last).byPlayer()) last--;
        if (last < 0) return false;
        analysis = moves.get(last).ranking();
        moves.subList(last, moves.size()).clear();
        return true;
    }

    /** Все позиции партии, включая текущую. */
    public synchronized Set<String> seenPositions() {
        return new HashSet<>(positionCounts().keySet());
    }

    public synchronized GameStatus status() {
        Board board = board();
        if (board.legalMoves().isEmpty()) {
            return board.inCheck() ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        }
        if (board.isInsufficientMaterial()) return GameStatus.DRAW_MATERIAL;
        if (board.halfmoveClock() >= FIFTY_MOVE_DRAW_HALFMOVES) return GameStatus.DRAW_FIFTY_MOVES;
        if (positionCounts().get(board.positionKey()) >= REPETITION_DRAW_COUNT) return GameStatus.DRAW_REPETITION;
        return GameStatus.ACTIVE;
    }

    private Map<String, Integer> positionCounts() {
        Map<String, Integer> counts = new HashMap<>();
        counts.merge(initial.positionKey(), 1, Integer::sum);
        for (MoveRecord move : moves) {
            counts.merge(move.after().positionKey(), 1, Integer::sum);
        }
        return counts;
    }
}
