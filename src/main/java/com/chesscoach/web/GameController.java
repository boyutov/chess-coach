package com.chesscoach.web;

import com.chesscoach.archive.ArchiveService;
import com.chesscoach.game.Game;
import com.chesscoach.game.GameService;
import com.chesscoach.game.Lesson;
import com.chesscoach.online.AccountService;
import com.chesscoach.online.NotAuthenticatedException;
import com.chesscoach.web.dto.GameResponse;
import com.chesscoach.web.dto.HintResponse;
import com.chesscoach.web.dto.LessonResponse;
import com.chesscoach.web.dto.MoveRequest;
import com.chesscoach.web.dto.NewGameRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
public class GameController {

    private final GameService games;
    private final ArchiveService archive;
    private final AccountService accounts;

    public GameController(GameService games, ArchiveService archive, AccountService accounts) {
        this.archive = archive;
        this.accounts = accounts;
        this.games = games;
    }

    @GetMapping("/lessons")
    public List<LessonResponse> lessons() {
        return Arrays.stream(Lesson.values()).map(LessonResponse::from).toList();
    }

    @PostMapping("/games")
    public GameResponse start(@Valid @RequestBody NewGameRequest request,
                              @CookieValue(name = OnlineController.COOKIE, required = false) String token) {
        Game game = request.lesson() != null
                ? games.startLesson(request.lesson())
                : games.startGame(request.playAsWhite(), request.level(), playerOrNull(token));
        archive.saveBotGame(game);
        return GameResponse.from(game);
    }

    @GetMapping("/games/{id}")
    public GameResponse get(@PathVariable String id) {
        return GameResponse.from(games.get(id));
    }

    @PostMapping("/games/{id}/moves")
    public GameResponse move(@PathVariable String id, @Valid @RequestBody MoveRequest request) {
        Game game = games.playMove(id, request.uci());
        archive.saveBotGame(game);
        return GameResponse.from(game);
    }

    @PostMapping("/games/{id}/undo")
    public GameResponse undo(@PathVariable String id) {
        Game game = games.undo(id);
        archive.saveBotGame(game);
        return GameResponse.from(game);
    }

    /** Без входа партия просто не сохраняется. */
    private String playerOrNull(String token) {
        try {
            return accounts.requirePlayer(token);
        } catch (NotAuthenticatedException e) {
            return null;
        }
    }

    @GetMapping("/games/{id}/hint")
    public HintResponse hint(@PathVariable String id) {
        return new HintResponse(games.hint(id).toUci());
    }
}
