package com.chesscoach.web;

import com.chesscoach.archive.ArchiveService;
import com.chesscoach.archive.ArchivedGame;
import com.chesscoach.online.AccountService;
import com.chesscoach.web.dto.ArchiveGameResponse;
import com.chesscoach.web.dto.ArchiveSummaryResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Сохранённые партии игрока, отчёт и разбор. Чужие партии не видны. */
@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    private final AccountService accounts;
    private final ArchiveService archive;

    public ArchiveController(AccountService accounts, ArchiveService archive) {
        this.accounts = accounts;
        this.archive = archive;
    }

    @GetMapping
    public List<ArchiveSummaryResponse> list(@CookieValue(name = OnlineController.COOKIE, required = false) String token) {
        String me = accounts.requirePlayer(token);
        List<ArchivedGame> games = archive.list(me);
        return games.stream().map(game -> ArchiveSummaryResponse.from(game, me)).toList();
    }

    @GetMapping("/{id}")
    public ArchiveGameResponse game(@CookieValue(name = OnlineController.COOKIE, required = false) String token,
                                    @PathVariable String id) {
        return ArchiveGameResponse.from(archive.view(accounts.requirePlayer(token), id));
    }

    @PostMapping("/{id}/analysis")
    public ArchiveGameResponse analyze(@CookieValue(name = OnlineController.COOKIE, required = false) String token,
                                       @PathVariable String id) {
        String me = accounts.requirePlayer(token);
        archive.startAnalysis(me, id);
        return ArchiveGameResponse.from(archive.view(me, id));
    }
}
