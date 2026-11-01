package com.chesscoach.coach;

/** Вопрос вместе с приложенными фактами и ответ модели. */
public record ChatTurn(String userPrompt, String assistantText) {
}
