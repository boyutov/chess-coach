package com.chesscoach.web.dto;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.game.Game;
import com.chesscoach.game.GameStatus;
import com.chesscoach.game.MoveRecord;
import com.chesscoach.online.OnlineGame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Партия глазами одного игрока. Поля совпадают с {@link GameResponse}, чтобы доску рисовал один и тот же код. */
public record OnlineGameResponse(
        String id,
        boolean online,
        String opponent,
        String fen,
        String startFen,
        boolean playerWhite,
        boolean playerTurn,
        String status,
        String statusText,
        List<String> legalMoves,
        Map<String, String> legalMoveSans,
        List<GameResponse.MoveResponse> moves) {

    public static OnlineGameResponse from(OnlineGame online, String viewer, String opponentName) {
        Game game = online.game();
        Board board = game.board();
        boolean white = online.white().equals(viewer);
        boolean turn = board.isWhiteToMove() == white;
        boolean canMove = online.isActive() && turn;
        String status = online.resignedBy() != null ? "RESIGNED" : game.status().name();
        return new OnlineGameResponse(online.id(), true, opponentName, board.toFen(), game.initial().toFen(), white,
                turn, status, statusText(online, viewer, opponentName, board, white),
                canMove ? board.legalMoves().stream().map(Move::toUci).toList() : List.of(),
                canMove ? GameResponse.legalMoveSans(board) : Map.of(),
                moves(game.moves(), white));
    }

    private static String statusText(OnlineGame online, String viewer, String opponent, Board board, boolean white) {
        if (online.resignedBy() != null) {
            return online.resignedBy().equals(viewer) ? "Вы сдались" : opponent + " сдаётся. Вы победили!";
        }
        GameStatus status = online.game().status();
        return GameResponse.statusText(status, board, white);
    }

    private static List<GameResponse.MoveResponse> moves(List<MoveRecord> records, boolean viewerWhite) {
        List<GameResponse.MoveResponse> moves = new ArrayList<>(records.size());
        for (int ply = 0; ply < records.size(); ply++) {
            MoveRecord record = records.get(ply);
            boolean moverWhite = record.before().isWhiteToMove();
            moves.add(new GameResponse.MoveResponse(ply, record.before().fullmoveNumber(), moverWhite, record.san(),
                    record.move().toUci(), moverWhite == viewerWhite, record.after().toFen(), null));
        }
        return moves;
    }
}
