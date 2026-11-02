package com.chesscoach.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"analysis.depth=2", "analysis.time-limit-ms=100", "analysis.report-time-limit-ms=50"})
@AutoConfigureMockMvc
class ArchiveControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private Cookie register() throws Exception {
        String name = "a" + UUID.randomUUID().toString().substring(0, 8);
        return mvc.perform(post("/api/online/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + name + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(OnlineController.COOKIE);
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private static MockHttpServletRequestBuilder jsonPost(String path, Cookie cookie, String body) {
        return post(path).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String playFoolsMate(Cookie white, Cookie black) throws Exception {
        ok(post("/api/online/queue").cookie(white));
        ok(post("/api/online/queue").cookie(black));
        String id = ok(get("/api/online/state").cookie(white)).get("activeGameId").asText();
        boolean firstIsWhite = ok(get("/api/online/games/" + id).cookie(white)).get("playerWhite").asBoolean();
        Cookie w = firstIsWhite ? white : black;
        Cookie b = firstIsWhite ? black : white;
        String[] line = {"f2f3", "e7e5", "g2g4", "d8h4"};
        for (int i = 0; i < line.length; i++) {
            ok(jsonPost("/api/online/games/" + id + "/moves", i % 2 == 0 ? w : b, "{\"uci\":\"" + line[i] + "\"}"));
        }
        return id;
    }

    private JsonNode waitForAnalysis(String id, Cookie cookie) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            JsonNode game = ok(get("/api/archive/" + id).cookie(cookie));
            if ("DONE".equals(game.get("analysis").asText())) return game;
            Thread.sleep(50);
        }
        throw new AssertionError("Разбор не закончился");
    }

    @Test
    void finishedOnlineGameIsSavedForBothAndAnalyzedOnDemand() throws Exception {
        Cookie first = register();
        Cookie second = register();
        String id = playFoolsMate(first, second);

        JsonNode list = ok(get("/api/archive").cookie(first));
        assertEquals(id, list.get(0).get("id").asText());
        assertEquals(1, ok(get("/api/archive").cookie(second)).size());
        assertEquals("NONE", ok(get("/api/archive/" + id).cookie(first)).get("analysis").asText());

        ok(post("/api/archive/" + id + "/analysis").cookie(first));
        JsonNode game = waitForAnalysis(id, first);
        assertEquals(4, game.get("moves").size());
        assertTrue(game.get("moves").get(0).get("review").hasNonNull("quality"));
        assertEquals(4, game.get("report").get("evalPercent").size());
        assertTrue(game.get("report").get("white").get("accuracy").asDouble() >= 0);

        // Копию партии в архиве можно обсуждать с тренером, но нельзя менять.
        String coachId = game.get("id").asText();
        mvc.perform(post("/api/games/" + coachId + "/undo")).andExpect(status().isConflict());
        mvc.perform(get("/api/games/" + coachId + "/hint")).andExpect(status().isConflict());
    }

    @Test
    void activeOnlineGameCannotBeAnalyzed() throws Exception {
        Cookie first = register();
        Cookie second = register();
        ok(post("/api/online/queue").cookie(first));
        ok(post("/api/online/queue").cookie(second));
        String id = ok(get("/api/online/state").cookie(first)).get("activeGameId").asText();
        mvc.perform(post("/api/archive/" + id + "/analysis").cookie(first)).andExpect(status().isNotFound());
    }

    @Test
    void strangersCannotOpenArchivedGame() throws Exception {
        String id = playFoolsMate(register(), register());
        mvc.perform(get("/api/archive/" + id).cookie(register())).andExpect(status().isNotFound());
        mvc.perform(get("/api/archive/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void botGameIsSavedWithReviewsForItsOwnerOnly() throws Exception {
        Cookie player = register();
        JsonNode started = ok(jsonPost("/api/games", player, "{\"playAsWhite\":true,\"level\":\"BEGINNER\"}"));
        String id = started.get("id").asText();
        assertEquals(0, ok(get("/api/archive").cookie(player)).size());

        ok(jsonPost("/api/games/" + id + "/moves", player, "{\"uci\":\"e2e4\"}"));
        JsonNode list = ok(get("/api/archive").cookie(player));
        assertEquals("UNFINISHED", list.get(0).get("outcome").asText());
        assertTrue(list.get(0).get("analyzed").asBoolean());
        JsonNode game = ok(get("/api/archive/" + id).cookie(player));
        assertEquals("DONE", game.get("analysis").asText());
        assertFalse(game.get("report").isNull());

        ok(post("/api/games/" + id + "/undo"));
        assertEquals(0, ok(get("/api/archive").cookie(player)).size());
    }

    @Test
    void anonymousBotGameIsNotSaved() throws Exception {
        mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON)
                .content("{\"playAsWhite\":true,\"level\":\"BEGINNER\"}")).andExpect(status().isOk());
    }
}
