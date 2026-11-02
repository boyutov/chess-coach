package com.chesscoach.web;

import com.chesscoach.archive.ArchiveService;
import com.chesscoach.online.OnlineEvents;
import com.chesscoach.online.OnlineGameStore;
import com.chesscoach.online.OnlineService;
import com.chesscoach.online.PlayerStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"analysis.depth=3", "analysis.time-limit-ms=200"})
@AutoConfigureMockMvc
class OnlineControllerTest {

    private record Player(String name, Cookie cookie) {
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PlayerStore playerStore;

    @Autowired
    private OnlineEvents events;

    @Autowired
    private OnlineGameStore gameStore;

    @Autowired
    private ArchiveService archive;

    private Player register() throws Exception {
        String name = "p" + UUID.randomUUID().toString().substring(0, 8);
        Cookie cookie = mvc.perform(post("/api/online/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(name, "secret123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("chess_session");
        return new Player(name, cookie);
    }

    private static String credentials(String name, String password) {
        return "{\"nickname\":\"" + name + "\",\"password\":\"" + password + "\"}";
    }

    private JsonNode call(Player player, String path) throws Exception {
        return call(player, path, "{}", status().isOk());
    }

    private JsonNode call(Player player, String path, String body, ResultMatcher expected) throws Exception {
        String response = mvc.perform(post("/api/online" + path).cookie(player.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(expected).andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : json.readTree(response);
    }

    private JsonNode state(Player player) throws Exception {
        return json.readTree(mvc.perform(get("/api/online/state").cookie(player.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode game(Player player, String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/online/games/" + id).cookie(player.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Подбирает соперников и возвращает их: сначала белые, потом чёрные. */
    private Player[] match(Player a, Player b) throws Exception {
        call(a, "/queue");
        call(b, "/queue");
        String id = state(a).get("activeGameId").asText();
        return game(a, id).get("playerWhite").asBoolean() ? new Player[]{a, b} : new Player[]{b, a};
    }

    private void makeFriends(Player a, Player b) throws Exception {
        call(a, "/friends", "{\"nickname\":\"" + b.name() + "\"}", status().isOk());
        assertEquals(a.name(), state(b).get("incomingRequests").get(0).asText());
        call(b, "/friends/" + a.name() + "/accept");
    }

    @Test
    void activeGameSurvivesServerRestart() throws Exception {
        Player[] players = match(register(), register());
        String id = state(players[0]).get("activeGameId").asText();
        call(players[0], "/games/" + id + "/moves", "{\"uci\":\"e2e4\"}", status().isOk());

        OnlineService restarted = new OnlineService(playerStore, events, gameStore, archive);
        String white = players[0].name().toLowerCase();
        assertEquals(id, restarted.state(white).activeGameId());
        assertEquals("e4", restarted.game(white, id).game().moves().get(0).san());
    }

    @Test
    void stateRequiresLogin() throws Exception {
        mvc.perform(get("/api/online/state")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        Player player = register();
        mvc.perform(post("/api/online/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(player.name(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Неверный ник или пароль"));
        mvc.perform(post("/api/online/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(player.name().toUpperCase(), "secret123")))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateNicknameIsRejected() throws Exception {
        Player player = register();
        mvc.perform(post("/api/online/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(player.name(), "secret123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void friendsCanChallengeEachOther() throws Exception {
        Player a = register();
        Player b = register();
        makeFriends(a, b);
        assertEquals(b.name(), state(a).get("friends").get(0).get("nickname").asText());

        call(a, "/challenges/" + b.name());
        assertEquals(a.name(), state(b).get("incomingChallenges").get(0).asText());
        call(b, "/challenges/" + a.name() + "/accept");

        assertEquals(state(a).get("activeGameId").asText(), state(b).get("activeGameId").asText());
        assertEquals(0, state(b).get("incomingChallenges").size());
    }

    @Test
    void cannotChallengeStranger() throws Exception {
        Player a = register();
        Player b = register();
        call(a, "/challenges/" + b.name(), "{}", status().isConflict());
    }

    @Test
    void crossRequestsMakeFriendsImmediately() throws Exception {
        Player a = register();
        Player b = register();
        call(a, "/friends", "{\"nickname\":\"" + b.name() + "\"}", status().isOk());
        call(b, "/friends", "{\"nickname\":\"" + a.name() + "\"}", status().isOk());
        assertEquals(1, state(a).get("friends").size());
        assertEquals(0, state(b).get("incomingRequests").size());
    }

    @Test
    void playersTakeTurnsAndSeeOppositeColors() throws Exception {
        Player[] players = match(register(), register());
        Player white = players[0];
        Player black = players[1];
        String id = state(white).get("activeGameId").asText();
        String moves = "/games/" + id + "/moves";

        call(black, moves, "{\"uci\":\"e7e5\"}", status().isConflict());
        call(white, moves, "{\"uci\":\"e2e5\"}", status().isBadRequest());
        call(white, moves, "{\"uci\":\"e2e4\"}", status().isOk());

        JsonNode blackView = game(black, id);
        assertEquals("e4", blackView.get("moves").get(0).get("san").asText());
        assertEquals(false, blackView.get("moves").get(0).get("byPlayer").asBoolean());
        assertEquals(true, blackView.get("playerTurn").asBoolean());
        assertEquals(20, blackView.get("legalMoves").size());
        assertEquals(0, game(white, id).get("legalMoves").size());
    }

    @Test
    void foolsMateEndsGameForBoth() throws Exception {
        Player[] players = match(register(), register());
        String id = state(players[0]).get("activeGameId").asText();
        String[] line = {"f2f3", "e7e5", "g2g4", "d8h4"};
        for (int i = 0; i < line.length; i++) {
            call(players[i % 2], "/games/" + id + "/moves", "{\"uci\":\"" + line[i] + "\"}", status().isOk());
        }
        assertEquals("Мат. Вы проиграли", game(players[0], id).get("statusText").asText());
        assertEquals("Мат. Вы победили!", game(players[1], id).get("statusText").asText());
        assertEquals(true, state(players[0]).get("activeGameId").isNull());
    }

    @Test
    void resignFinishesGameAndFreesPlayers() throws Exception {
        Player[] players = match(register(), register());
        String id = state(players[0]).get("activeGameId").asText();
        call(players[0], "/queue", "{}", status().isConflict());
        call(players[0], "/games/" + id + "/resign");
        assertEquals("RESIGNED", game(players[1], id).get("status").asText());
        call(players[0], "/games/" + id + "/moves", "{\"uci\":\"e2e4\"}", status().isConflict());
        call(players[1], "/queue");
        call(players[1], "/queue/leave");
    }

    @Test
    void outsidersCannotSeeGame() throws Exception {
        Player[] players = match(register(), register());
        String id = state(players[0]).get("activeGameId").asText();
        mvc.perform(get("/api/online/games/" + id).cookie(register().cookie())).andExpect(status().isNotFound());
        assertNotEquals(players[0].name(), players[1].name());
    }

    @Test
    void engineHelpIsUnavailableInOnlineGames() throws Exception {
        Player[] players = match(register(), register());
        String id = state(players[0]).get("activeGameId").asText();
        mvc.perform(get("/api/games/" + id + "/hint")).andExpect(status().isNotFound());
        mvc.perform(post("/api/games/" + id + "/undo")).andExpect(status().isNotFound());
    }

    @Test
    void coachChatIsLocalOnly() throws Exception {
        mvc.perform(post("/api/games/any/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.7");
                            return request;
                        }))
                .andExpect(status().isForbidden());
    }
}
