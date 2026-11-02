package com.chesscoach.web.dto;

import com.chesscoach.archive.ArchiveService;
import com.chesscoach.archive.ArchivedGame;
import com.chesscoach.archive.GameReport;
import com.chesscoach.chess.Board;
import com.chesscoach.game.Game;
import com.chesscoach.game.GameStatus;
import com.chesscoach.game.MoveRecord;

import java.util.List;
import java.util.Map;

/**
 * Сохранённая партия. Поля партии совпадают с {@link GameResponse}, чтобы её показывал тот же код доски.
 * id нужен для чата с тренером, archiveId — для отчёта.
 */
public record ArchiveGameResponse(
        String id,
        String archiveId,
        boolean archived,
        String opponent,
        String fen,
        String startFen,
        boolean playerWhite,
        boolean playerTurn,
        String status,
        String statusText,
        List<String> legalMoves,
        Map<String, String> legalMoveSans,
        List<GameResponse.MoveResponse> moves,
        String evalText,
        String analysis,
        int analysisProgress,
        int plies,
        GameReport report) {

    public static ArchiveGameResponse from(ArchiveService.View view) {
        ArchivedGame saved = view.saved();
        Game game = view.game();
        Board board = game.board();
        List<MoveRecord> records = game.moves();
        String evalText = records.isEmpty() || records.get(records.size() - 1).review() == null ? null
                : com.chesscoach.game.EvalFormatter.format(records.get(records.size() - 1).review().evalAfter());
        return new ArchiveGameResponse(game.id(), saved.id(), true,
                view.viewerWhite() ? saved.blackName() : saved.whiteName(), board.toFen(), game.initial().toFen(),
                view.viewerWhite(), false, saved.status(), statusText(view, board), List.of(), Map.of(),
                GameResponse.moves(records), evalText, view.analysis().name(), view.progress(), saved.plies(),
                view.report());
    }

    private static String statusText(ArchiveService.View view, Board board) {
        ArchivedGame saved = view.saved();
        String opponent = view.viewerWhite() ? saved.blackName() : saved.whiteName();
        return switch (saved.status()) {
            case "ACTIVE" -> "Партия не доиграна";
            case "RESIGNED" -> "WIN".equals(outcome(view)) ? opponent + " сдался. Вы победили" : "Вы сдались";
            default -> GameResponse.statusText(GameStatus.valueOf(saved.status()), board, view.viewerWhite());
        };
    }

    private static String outcome(ArchiveService.View view) {
        ArchivedGame saved = view.saved();
        return ArchiveService.outcome(saved, view.viewerWhite() ? saved.ownerWhite() : saved.ownerBlack());
    }
}
