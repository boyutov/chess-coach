package com.chesscoach.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"analysis.depth=3", "analysis.time-limit-ms=200", "online.invite-code=open-sesame",
        "online.require-login=true", "online.secure-cookie=true", "coach.local-only=false",
        "coach.max-questions-per-hour=1"})
@AutoConfigureMockMvc
class ClosedServerTest {

    private static final String BOT_GAME = "{\"playAsWhite\":true,\"level\":\"BEGINNER\"}";

    @Autowired
    private MockMvc mvc;

    private static String registration(String name, String invite) {
        return "{\"nickname\":\"" + name + "\",\"password\":\"secret123\",\"inviteCode\":\"" + invite + "\"}";
    }

    private static String name() {
        return "c" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Cookie register() throws Exception {
        return mvc.perform(post("/api/online/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(name(), "open-sesame")))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(OnlineController.COOKIE);
    }

    @Test
    void registrationNeedsInviteCode() throws Exception {
        mvc.perform(post("/api/online/register").contentType(MediaType.APPLICATION_JSON)
                .content(registration(name(), "wrong"))).andExpect(status().isBadRequest());
    }

    @Test
    void sessionCookieIsSecure() throws Exception {
        assertTrue(register().getSecure());
    }

    @Test
    void botAndChatNeedLogin() throws Exception {
        mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON).content(BOT_GAME))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/games/any/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/games").cookie(register()).contentType(MediaType.APPLICATION_JSON).content(BOT_GAME))
                .andExpect(status().isOk());
    }

    @Test
    void passwordGuessingLocksNickname() throws Exception {
        String name = name();
        mvc.perform(post("/api/online/register").contentType(MediaType.APPLICATION_JSON)
                .content(registration(name, "open-sesame"))).andExpect(status().isOk());
        String wrong = "{\"nickname\":\"" + name + "\",\"password\":\"wrong-one\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/online/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/online/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + name + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatLimitIsPerHour() {
        ChatLimiter limiter = new ChatLimiter(1);
        limiter.acquire("a");
        limiter.acquire("b");
        org.junit.jupiter.api.Assertions.assertThrows(ChatLimiter.LimitExceeded.class, () -> limiter.acquire("a"));
    }
}
