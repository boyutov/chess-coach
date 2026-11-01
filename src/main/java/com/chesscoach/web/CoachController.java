package com.chesscoach.web;

import com.chesscoach.coach.CoachModelException;
import com.chesscoach.coach.CoachService;
import com.chesscoach.game.Game;
import com.chesscoach.game.GameService;
import com.chesscoach.web.dto.ChatRequest;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Передаёт ответ тренера в браузер потоком Server-Sent Events: события delta и error. */
@RestController
@RequestMapping("/api/games/{id}/chat")
public class CoachController {

    private static final Logger log = LoggerFactory.getLogger(CoachController.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000;

    private final GameService games;
    private final CoachService coach;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public CoachController(GameService games, CoachService coach) {
        this.games = games;
        this.coach = coach;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String id, @Valid @RequestBody ChatRequest request) {
        Game game = games.get(id);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        executor.execute(() -> streamAnswer(game, request, emitter));
        return emitter;
    }

    private void streamAnswer(Game game, ChatRequest request, SseEmitter emitter) {
        try {
            coach.answer(game, request.focusPly(), request.message(),
                    text -> send(emitter, "delta", Map.of("text", text)));
        } catch (ClientDisconnected e) {
            log.debug("Браузер закрыл соединение до конца ответа");
        } catch (CoachModelException e) {
            sendError(emitter, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Не удалось получить ответ тренера", e);
            sendError(emitter, "Не удалось получить ответ тренера. Подробности в журнале сервера.");
        } finally {
            emitter.complete();
        }
    }

    private static void send(SseEmitter emitter, String event, Map<String, String> data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            throw new ClientDisconnected();
        }
    }

    private static void sendError(SseEmitter emitter, String message) {
        try {
            send(emitter, "error", Map.of("message", message));
        } catch (ClientDisconnected e) {
            log.debug("Браузер закрыл соединение до показа ошибки");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Браузер закрыл страницу: прерываем поток, чтобы не платить за ненужный ответ. */
    private static final class ClientDisconnected extends RuntimeException {
    }
}
