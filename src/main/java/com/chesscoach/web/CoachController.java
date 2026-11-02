package com.chesscoach.web;

import com.chesscoach.coach.CoachModelException;
import com.chesscoach.coach.CoachService;
import com.chesscoach.game.Game;
import com.chesscoach.game.GameService;
import com.chesscoach.online.AccountService;
import com.chesscoach.online.NotAuthenticatedException;
import com.chesscoach.web.dto.ChatRequest;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.util.WebUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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

    private final AccountService accounts;
    private final ChatLimiter limiter;
    private final boolean localOnly;

    public CoachController(GameService games, CoachService coach, AccountService accounts, ChatLimiter limiter,
                           @Value("${coach.local-only}") boolean localOnly) {
        this.accounts = accounts;
        this.limiter = limiter;
        this.localOnly = localOnly;
        this.games = games;
        this.coach = coach;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String id, @Valid @RequestBody ChatRequest request,
                           HttpServletRequest http) {
        if (localOnly) requireLocal(http.getRemoteAddr());
        Game game = games.get(id);
        limiter.acquire(limiterKey(http));
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        executor.execute(() -> streamAnswer(game, request, emitter));
        return emitter;
    }

    /** Чат тратит деньги с API-ключа владельца, поэтому гостям из сети он недоступен. */
    private static void requireLocal(String address) {
        try {
            if (InetAddress.getByName(address).isLoopbackAddress()) return;
        } catch (UnknownHostException e) {
            log.debug("Не удалось разобрать адрес клиента {}", address);
        }
        throw new LocalOnlyException();
    }

    private String limiterKey(HttpServletRequest http) {
        Cookie cookie = WebUtils.getCookie(http, OnlineController.COOKIE);
        try {
            return accounts.requirePlayer(cookie == null ? null : cookie.getValue());
        } catch (NotAuthenticatedException e) {
            return "";
        }
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
