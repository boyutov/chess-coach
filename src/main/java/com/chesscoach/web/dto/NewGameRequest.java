package com.chesscoach.web.dto;

import com.chesscoach.engine.Level;
import com.chesscoach.game.Lesson;
import jakarta.validation.constraints.NotNull;

/** Новая партия или, если задан lesson, урок. */
public record NewGameRequest(boolean playAsWhite, @NotNull Level level, Lesson lesson) {
}
