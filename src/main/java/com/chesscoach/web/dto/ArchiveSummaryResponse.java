package com.chesscoach.web.dto;

import com.chesscoach.archive.ArchiveService;
import com.chesscoach.archive.ArchivedGame;

/** Строка в списке партий. */
public record ArchiveSummaryResponse(String id, String opponent, boolean playerWhite, String outcome, int moveCount,
                                     long savedAt, boolean analyzed) {

    public static ArchiveSummaryResponse from(ArchivedGame game, String player) {
        boolean white = player.equals(game.ownerWhite());
        return new ArchiveSummaryResponse(game.id(), white ? game.blackName() : game.whiteName(), white,
                ArchiveService.outcome(game, player), (game.plies() + 1) / 2, game.savedAt(), game.analysis() != null);
    }
}
