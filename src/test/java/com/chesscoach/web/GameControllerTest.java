package com.chesscoach.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"analysis.depth=3", "analysis.time-limit-ms=200"})
@AutoConfigureMockMvc
class GameControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String startGame() throws Exception {
        String body = mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playAsWhite\":true,\"level\":\"BEGINNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalMoves.length()").value(20))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private String moveBody(String uci) {
        return "{\"uci\":\"" + uci + "\"}";
    }

    @Test
    void playsAndUndoesMove() throws Exception {
        String id = startGame();
        mvc.perform(post("/api/games/" + id + "/moves").contentType(MediaType.APPLICATION_JSON).content(moveBody("e2e4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moves.length()").value(2))
                .andExpect(jsonPath("$.moves[0].review.quality").exists());
        mvc.perform(post("/api/games/" + id + "/undo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moves.length()").value(0))
                .andExpect(jsonPath("$.evalText").exists());
    }

    @Test
    void illegalMoveIsBadRequest() throws Exception {
        String id = startGame();
        mvc.perform(post("/api/games/" + id + "/moves").contentType(MediaType.APPLICATION_JSON).content(moveBody("e2e6")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Нелегальный ход: e2e6"));
    }

    @Test
    void malformedMoveIsBadRequest() throws Exception {
        String id = startGame();
        mvc.perform(post("/api/games/" + id + "/moves").contentType(MediaType.APPLICATION_JSON).content(moveBody("zz")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void undoWithoutMovesIsConflict() throws Exception {
        mvc.perform(post("/api/games/" + startGame() + "/undo")).andExpect(status().isConflict());
    }

    @Test
    void unknownGameIsNotFound() throws Exception {
        mvc.perform(get("/api/games/missing")).andExpect(status().isNotFound());
    }

    @Test
    void listsLessons() throws Exception {
        mvc.perform(get("/api/lessons")).andExpect(jsonPath("$.length()").value(6));
    }
}
