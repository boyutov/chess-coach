package com.chesscoach.web;

import com.chesscoach.game.Game;
import com.chesscoach.game.GameService;
import com.chesscoach.game.Lesson;
import com.chesscoach.web.dto.GameResponse;
import com.chesscoach.web.dto.HintResponse;
import com.chesscoach.web.dto.LessonResponse;
import com.chesscoach.web.dto.MoveRequest;
import com.chesscoach.web.dto.NewGameRequest;
import jakarta.validation.Valid;
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

    public GameController(GameService games) {
        this.games = games;
    }

    @GetMapping("/lessons")
    public List<LessonResponse> lessons() {
        return Arrays.stream(Lesson.values()).map(LessonResponse::from).toList();
    }

    @PostMapping("/games")
    public GameResponse start(@Valid @RequestBody NewGameRequest request) {
        Game game = request.lesson() != null
                ? games.startLesson(request.lesson())
                : games.startGame(request.playAsWhite(), request.level());
        return GameResponse.from(game);
    }

    @GetMapping("/games/{id}")
    public GameResponse get(@PathVariable String id) {
        return GameResponse.from(games.get(id));
    }

    @PostMapping("/games/{id}/moves")
    public GameResponse move(@PathVariable String id, @Valid @RequestBody MoveRequest request) {
        return GameResponse.from(games.playMove(id, request.uci()));
    }

    @PostMapping("/games/{id}/undo")
    public GameResponse undo(@PathVariable String id) {
        return GameResponse.from(games.undo(id));
    }

    @GetMapping("/games/{id}/hint")
    public HintResponse hint(@PathVariable String id) {
        return new HintResponse(games.hint(id).toUci());
    }
}
