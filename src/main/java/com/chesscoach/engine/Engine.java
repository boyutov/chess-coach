package com.chesscoach.engine;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Шахматный движок: перебор с итеративным углублением, альфа-бета отсечением и форсированным
 * вариантом на взятиях. Экземпляр не хранит состояния и безопасен для параллельных вызовов.
 */
public class Engine {

    /** Оценка мата: чем ближе мат, тем больше абсолютное значение. */
    public static final int MATE = 100_000;
    private static final int MATE_THRESHOLD = MATE - 1_000;
    private static final int INFINITY = MATE + 1;
    private static final int MAX_QUIESCENCE_PLY = 12;

    /** Ход с оценкой с точки зрения стороны, которая ходит. */
    public record ScoredMove(Move move, int score) {
    }

    private final Random random = new Random();

    /**
     * Оценивает каждый легальный ход и возвращает их от лучшего к худшему. Глубина растёт, пока
     * есть время; результат берётся из последней полностью завершённой итерации.
     *
     * @param seenPositions позиции, уже встречавшиеся в партии: ход в них считается ничьей
     */
    public List<ScoredMove> rank(Board board, int maxDepth, long timeLimitMs, Set<String> seenPositions) {
        List<Move> moves = board.legalMoves();
        if (moves.isEmpty()) return List.of();

        long deadline = System.currentTimeMillis() + timeLimitMs;
        List<ScoredMove> best = scoreAll(board, moves, 1, Long.MAX_VALUE, seenPositions);
        for (int depth = 2; depth <= maxDepth; depth++) {
            try {
                best = scoreAll(board, best.stream().map(ScoredMove::move).toList(), depth, deadline, seenPositions);
            } catch (SearchTimeout timeout) {
                break;
            }
        }
        return best;
    }

    private List<ScoredMove> scoreAll(Board board, List<Move> moves, int depth, long deadline, Set<String> seen) {
        Search search = new Search(deadline);
        List<ScoredMove> scored = new ArrayList<>(moves.size());
        for (Move m : moves) {
            Board next = board.apply(m);
            int score = seen.contains(next.positionKey())
                    ? 0
                    : -search.negamax(next, depth - 1, 1, -INFINITY, INFINITY);
            scored.add(new ScoredMove(m, score));
        }
        scored.sort(Comparator.comparingInt(ScoredMove::score).reversed());
        return scored;
    }

    /** Выбирает ход на указанном уровне сложности. Возвращает null, если ходов нет. */
    public Move chooseMove(Board board, Level level, Set<String> seenPositions) {
        List<ScoredMove> ranked = rank(board, level.depth(), level.timeLimitMs(), seenPositions);
        if (ranked.isEmpty()) return null;
        if (level.noise() == 0) return ranked.get(0).move();

        // Случайная поправка к оценке заставляет слабые уровни ошибаться правдоподобно.
        // Мат в один ход шум не скрывает: иначе партия тянулась бы бессмысленно.
        ScoredMove top = ranked.get(0);
        if (top.score() >= MATE - 1) return top.move();
        ScoredMove chosen = top;
        int bestNoisy = Integer.MIN_VALUE;
        for (ScoredMove sm : ranked) {
            int noisy = sm.score() + random.nextInt(level.noise() * 2 + 1) - level.noise();
            if (noisy > bestNoisy) {
                bestNoisy = noisy;
                chosen = sm;
            }
        }
        return chosen.move();
    }

    public static boolean isMateScore(int score) {
        return Math.abs(score) >= MATE_THRESHOLD;
    }

    /** Через сколько ходов мат (положительно — ставит сторона, для которой дана оценка). */
    public static int mateInMoves(int score) {
        int plies = MATE - Math.abs(score);
        int moves = (plies + 1) / 2;
        return score > 0 ? moves : -moves;
    }

    private static final class SearchTimeout extends RuntimeException {
        SearchTimeout() {
            super(null, null, false, false);
        }
    }

    private static final class Search {
        private final long deadline;
        private long nodes;

        Search(long deadline) {
            this.deadline = deadline;
        }

        private void checkTime() {
            if ((++nodes & 2047) == 0 && System.currentTimeMillis() > deadline) {
                throw new SearchTimeout();
            }
        }

        int negamax(Board board, int depth, int ply, int alpha, int beta) {
            checkTime();
            if (board.halfmoveClock() >= 100 || board.isInsufficientMaterial()) return 0;
            boolean inCheck = board.inCheck();
            if (depth <= 0 && !inCheck) return quiescence(board, alpha, beta, 0);
            // Шах продлевает перебор на один полуход, чтобы не обрывать форсированные варианты.
            int nextDepth = inCheck ? depth : depth - 1;

            boolean anyLegal = false;
            for (Move m : ordered(board, board.pseudoLegalMoves())) {
                Board next = board.apply(m);
                if (next.leavesMoverInCheck()) continue;
                anyLegal = true;
                int score = -negamax(next, nextDepth, ply + 1, -beta, -alpha);
                if (score >= beta) return score;
                if (score > alpha) alpha = score;
            }
            if (!anyLegal) return inCheck ? -(MATE - ply) : 0;
            return alpha;
        }

        private int quiescence(Board board, int alpha, int beta, int qPly) {
            checkTime();
            int standPat = Evaluator.evaluate(board);
            if (standPat >= beta || qPly >= MAX_QUIESCENCE_PLY) return standPat;
            if (standPat > alpha) alpha = standPat;

            List<Move> captures = new ArrayList<>();
            for (Move m : board.pseudoLegalMoves()) {
                if (isCaptureOrPromotion(board, m)) captures.add(m);
            }
            for (Move m : ordered(board, captures)) {
                Board next = board.apply(m);
                if (next.leavesMoverInCheck()) continue;
                int score = -quiescence(next, -beta, -alpha, qPly + 1);
                if (score >= beta) return score;
                if (score > alpha) alpha = score;
            }
            return alpha;
        }

        private static boolean isCaptureOrPromotion(Board board, Move m) {
            return board.pieceAt(m.to()) != Board.EMPTY || m.promotion() != Board.EMPTY
                    || (Math.abs(board.pieceAt(m.from())) == Board.PAWN && m.from() % 8 != m.to() % 8);
        }

        /** Сначала проверяем превращения и взятия ценной фигуры дешёвой: так отсечения срабатывают раньше. */
        private static List<Move> ordered(Board board, List<Move> moves) {
            moves.sort(Comparator.comparingInt((Move m) -> -orderScore(board, m)));
            return moves;
        }

        private static int orderScore(Board board, Move m) {
            int score = m.promotion() != Board.EMPTY ? Evaluator.VALUES[m.promotion()] * 10 : 0;
            int victim = Math.abs(board.pieceAt(m.to()));
            if (victim != Board.EMPTY) {
                score += Evaluator.VALUES[victim] * 10 - Evaluator.VALUES[Math.abs(board.pieceAt(m.from()))];
            }
            return score;
        }
    }
}
