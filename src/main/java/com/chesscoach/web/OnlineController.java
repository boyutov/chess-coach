package com.chesscoach.web;

import com.chesscoach.online.AccountService;
import com.chesscoach.online.OnlineGame;
import com.chesscoach.online.OnlineService;
import com.chesscoach.online.OnlineState;
import com.chesscoach.web.dto.CredentialsRequest;
import com.chesscoach.web.dto.MoveRequest;
import com.chesscoach.web.dto.NicknameRequest;
import com.chesscoach.web.dto.OnlineGameResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;

/** Игра по сети. Сессия хранится в cookie: её отправляет и EventSource, которому нельзя задать заголовки. */
@RestController
@RequestMapping("/api/online")
public class OnlineController {

    public static final String COOKIE = "chess_session";
    private static final Duration COOKIE_AGE = Duration.ofDays(30);

    private final AccountService accounts;
    private final OnlineService online;
    private final boolean secureCookie;

    public OnlineController(AccountService accounts, OnlineService online,
                            @Value("${online.secure-cookie}") boolean secureCookie) {
        this.secureCookie = secureCookie;
        this.accounts = accounts;
        this.online = online;
    }

    @PostMapping("/register")
    public OnlineState register(@RequestBody CredentialsRequest request, HttpServletResponse response) {
        return openSession(accounts.register(request.nickname(), request.password(), request.inviteCode()), response);
    }

    @PostMapping("/login")
    public OnlineState login(@RequestBody CredentialsRequest request, HttpServletResponse response) {
        return openSession(accounts.login(request.nickname(), request.password()), response);
    }

    @PostMapping("/logout")
    public void logout(@CookieValue(name = COOKIE, required = false) String token, HttpServletResponse response) {
        accounts.logout(token);
        setCookie(response, "", Duration.ZERO);
    }

    @GetMapping("/state")
    public OnlineState state(@CookieValue(name = COOKIE, required = false) String token) {
        return online.state(accounts.requirePlayer(token));
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@CookieValue(name = COOKIE, required = false) String token) {
        return online.subscribe(accounts.requirePlayer(token));
    }

    @PostMapping("/friends")
    public OnlineState requestFriend(@CookieValue(name = COOKIE, required = false) String token,
                                     @RequestBody NicknameRequest request) {
        String me = accounts.requirePlayer(token);
        online.requestFriend(me, request.nickname());
        return online.state(me);
    }

    @PostMapping("/friends/{nickname}/accept")
    public OnlineState acceptFriend(@CookieValue(name = COOKIE, required = false) String token,
                                    @PathVariable String nickname) {
        String me = accounts.requirePlayer(token);
        online.acceptFriend(me, nickname);
        return online.state(me);
    }

    @PostMapping("/friends/{nickname}/decline")
    public OnlineState declineFriend(@CookieValue(name = COOKIE, required = false) String token,
                                     @PathVariable String nickname) {
        String me = accounts.requirePlayer(token);
        online.declineFriend(me, nickname);
        return online.state(me);
    }

    @PostMapping("/friends/{nickname}/remove")
    public OnlineState removeFriend(@CookieValue(name = COOKIE, required = false) String token,
                                    @PathVariable String nickname) {
        String me = accounts.requirePlayer(token);
        online.removeFriend(me, nickname);
        return online.state(me);
    }

    @PostMapping("/challenges/{nickname}")
    public OnlineState challenge(@CookieValue(name = COOKIE, required = false) String token,
                                 @PathVariable String nickname) {
        String me = accounts.requirePlayer(token);
        online.challenge(me, nickname);
        return online.state(me);
    }

    @PostMapping("/challenges/{nickname}/accept")
    public OnlineState acceptChallenge(@CookieValue(name = COOKIE, required = false) String token,
                                       @PathVariable String nickname) {
        String me = accounts.requirePlayer(token);
        online.acceptChallenge(me, nickname);
        return online.state(me);
    }

    @PostMapping("/challenges/{nickname}/drop")
    public OnlineState dropChallenge(@CookieValue(name = COOKIE, required = false) String token,
                                     @PathVariable String nickname) {
        String me = accounts.requirePlayer(token);
        online.dropChallenge(me, nickname);
        return online.state(me);
    }

    @PostMapping("/queue")
    public OnlineState joinQueue(@CookieValue(name = COOKIE, required = false) String token) {
        String me = accounts.requirePlayer(token);
        online.joinQueue(me);
        return online.state(me);
    }

    @PostMapping("/queue/leave")
    public OnlineState leaveQueue(@CookieValue(name = COOKIE, required = false) String token) {
        String me = accounts.requirePlayer(token);
        online.leaveQueue(me);
        return online.state(me);
    }

    @GetMapping("/games/{id}")
    public OnlineGameResponse game(@CookieValue(name = COOKIE, required = false) String token,
                                   @PathVariable String id) {
        String me = accounts.requirePlayer(token);
        return view(online.game(me, id), me);
    }

    @PostMapping("/games/{id}/moves")
    public OnlineGameResponse move(@CookieValue(name = COOKIE, required = false) String token,
                                   @PathVariable String id, @Valid @RequestBody MoveRequest request) {
        String me = accounts.requirePlayer(token);
        return view(online.move(me, id, request.uci()), me);
    }

    @PostMapping("/games/{id}/resign")
    public OnlineGameResponse resign(@CookieValue(name = COOKIE, required = false) String token,
                                     @PathVariable String id) {
        String me = accounts.requirePlayer(token);
        return view(online.resign(me, id), me);
    }

    private OnlineGameResponse view(OnlineGame game, String me) {
        synchronized (online) {
            return OnlineGameResponse.from(game, me, online.nickname(game.opponentOf(me)));
        }
    }

    private OnlineState openSession(String token, HttpServletResponse response) {
        setCookie(response, token, COOKIE_AGE);
        return online.state(accounts.requirePlayer(token));
    }

    /** SameSite=Strict не даёт чужим сайтам делать запросы от имени игрока. */
    private void setCookie(HttpServletResponse response, String value, Duration age) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value)
                .httpOnly(true).secure(secureCookie).sameSite("Strict").path("/").maxAge(age).build().toString());
    }
}
