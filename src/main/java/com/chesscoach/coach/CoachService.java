package com.chesscoach.coach;

import com.chesscoach.game.Game;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Разговор с тренером: добавляет к вопросу факты о партии и хранит историю переписки. */
@Service
public class CoachService {

    private final CoachModelSelector models;
    private final CoachPromptBuilder prompts;
    private final Map<String, List<ChatTurn>> conversations = new ConcurrentHashMap<>();

    public CoachService(CoachModelSelector models, CoachPromptBuilder prompts) {
        this.models = models;
        this.prompts = prompts;
    }

    /**
     * Стримит ответ тренера. В историю попадают только законченные ответы.
     *
     * @param focusPly номер хода (с нуля), про который спрашивает ученик; null — последний ход ученика
     * @throws CoachModelException если не удалось получить ответ
     */
    public void answer(Game game, Integer focusPly, String question, Consumer<String> onText) {
        CoachModel model = models.select();
        String prompt = prompts.build(game, focusPly, question);
        List<ChatTurn> history = conversations.computeIfAbsent(game.id(), id -> Collections.synchronizedList(new ArrayList<>()));
        StringBuilder answer = new StringBuilder();
        model.stream(CoachPromptBuilder.SYSTEM_PROMPT, List.copyOf(history), prompt, text -> {
            answer.append(text);
            onText.accept(text);
        });
        history.add(new ChatTurn(prompt, answer.toString()));
    }
}
