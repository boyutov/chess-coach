package com.chesscoach.coach;

import java.util.List;
import java.util.function.Consumer;

/** Языковая модель, которая отвечает за тренера. */
public interface CoachModel {

    /**
     * Стримит ответ, передавая куски текста в onText.
     *
     * @throws CoachModelException если не удалось получить ответ
     */
    void stream(String system, List<ChatTurn> history, String prompt, Consumer<String> onText);
}
