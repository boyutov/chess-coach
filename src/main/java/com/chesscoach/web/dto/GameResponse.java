package com.chesscoach.web.dto;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.engine.Engine.ScoredMove;
import com.chesscoach.game.EvalFormatter;
import com.chesscoach.game.Game;
import com.chesscoach.game.GameStatus;
import com.chesscoach.game.MoveRecord;
import com.chesscoach.game.MoveReview;

import java.util.ArrayList;
import java.util.List;

/** Состояние партии для браузера. */
public record GameResponse(
        String id,
        String fen,
        String startFen,
        boolean playerWhite,
        boolean playerTurn,
        String level,
        String status,
        String statusText,
        List<String> legalMoves,
        List<MoveResponse> moves,
        String evalText,
        String lessonTitle,
        String lessonGoal) {

    public record MoveResponse(int ply, int number, boolean white, String san, String uci, boolean byPlayer,
                               String fenAfter, ReviewResponse review) {
    }

    public record ReviewResponse(String quality, String label, String bestSan, String bestUci, double winPercentLoss,
                                 String evalBefore, String evalAfter) {

        static ReviewResponse from(MoveReview review) {
            return review == null ? null : new ReviewResponse(review.quality().name(), review.quality().label(),
                    review.bestSan(), review.bestUci(), review.winPercentLoss(),
                    EvalFormatter.format(review.evalBefore()), EvalFormatter.format(review.evalAfter()));
        }
    }

    public static GameResponse from(Game game) {
        synchronized (game) {
            Board board = game.board();
            GameStatus status = game.status();
            boolean canMove = status == GameStatus.ACTIVE && game.isPlayerTurn();
            return new GameResponse(game.id(), board.toFen(), game.initial().toFen(), game.playerWhite(),
                    game.isPlayerTurn(), game.level().name(), status.name(),
                    statusText(status, board, game.playerWhite()),
                    canMove ? board.legalMoves().stream().map(Move::toUci).toList() : List.of(),
                    moves(game.moves()), evalText(game.analysis(), board),
                    game.lesson() == null ? null : game.lesson().title(),
                    game.lesson() == null ? null : game.lesson().goal());
        }
    }

    private static List<MoveResponse> moves(List<MoveRecord> records) {
        List<MoveResponse> moves = new ArrayList<>(records.size());
        for (int ply = 0; ply < records.size(); ply++) {
            MoveRecord record = records.get(ply);
            moves.add(new MoveResponse(ply, record.before().fullmoveNumber(), record.before().isWhiteToMove(),
                    record.san(), record.move().toUci(), record.byPlayer(), record.after().toFen(),
                    ReviewResponse.from(record.review())));
        }
        return moves;
    }

    private static String evalText(List<ScoredMove> analysis, Board board) {
        if (analysis.isEmpty()) return null;
        return EvalFormatter.format(analysis.get(0).score() * (board.isWhiteToMove() ? 1 : -1));
    }

    private static String statusText(GameStatus status, Board board, boolean playerWhite) {
        boolean playerToMove = board.isWhiteToMove() == playerWhite;
        return switch (status) {
            case ACTIVE -> board.inCheck() ? "Шах!" : (playerToMove ? "Ваш ход" : "Ход соперника");
            case CHECKMATE -> playerToMove ? "Мат. Вы проиграли" : "Мат. Вы победили!";
            case STALEMATE -> "Пат. Ничья";
            case DRAW_REPETITION -> "Ничья: троекратное повторение";
            case DRAW_FIFTY_MOVES -> "Ничья: правило 50 ходов";
            case DRAW_MATERIAL -> "Ничья: недостаточно фигур для мата";
        };
    }
}
