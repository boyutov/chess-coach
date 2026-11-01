package com.chesscoach.coach.provider;

import com.chesscoach.coach.ChatTurn;
import com.chesscoach.coach.CoachModel;
import com.chesscoach.coach.CoachModelException;
import com.chesscoach.config.CoachProperties;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Тренер на моделях OpenAI (Responses API). Ключ берётся из OPENAI_API_KEY. */
@Component
public class OpenAiCoachModel implements CoachModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCoachModel.class);

    private final String model;
    private volatile OpenAIClient client;

    public OpenAiCoachModel(CoachProperties properties) {
        this.model = properties.openai().model();
    }

    @Override
    public void stream(String system, List<ChatTurn> history, String prompt, Consumer<String> onText) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .instructions(system)
                .inputOfResponse(toInput(history, prompt))
                .build();

        try (StreamResponse<ResponseStreamEvent> response = client().responses().createStreaming(params)) {
            response.stream().forEach(event -> handleEvent(event, onText));
        } catch (UnauthorizedException e) {
            throw CoachModelException.badKey("OPENAI_API_KEY");
        } catch (RateLimitException e) {
            throw new CoachModelException("OpenAI отклонил запрос из-за лимита: подождите минуту. "
                    + "Если не поможет, проверьте баланс на platform.openai.com: у API отдельная оплата, не связанная с подпиской ChatGPT.");
        } catch (OpenAIServiceException e) {
            log.warn("Ошибка OpenAI API: {} {}", e.statusCode(), e.getMessage());
            throw toCoachException(e.statusCode());
        } catch (OpenAIIoException e) {
            log.warn("Нет связи с OpenAI API", e);
            throw new CoachModelException(CoachModelException.NO_CONNECTION);
        }
    }

    private static void handleEvent(ResponseStreamEvent event, Consumer<String> onText) {
        event.outputTextDelta().ifPresent(delta -> onText.accept(delta.delta()));
        if (event.refusalDone().isPresent()) {
            throw new CoachModelException(CoachModelException.REFUSED);
        }
        if (event.failed().isPresent() || event.error().isPresent()) {
            log.warn("OpenAI вернул ошибку в потоке: {}", event);
            throw new CoachModelException("Сервис ИИ не смог закончить ответ. Попробуйте ещё раз.");
        }
    }

    private CoachModelException toCoachException(int statusCode) {
        if (statusCode == 400 || statusCode == 404) {
            return new CoachModelException("OpenAI отклонил запрос (" + statusCode + "). Возможно, модель «" + model
                    + "» недоступна вашему ключу: поменяйте coach.openai.model в application.properties.");
        }
        return CoachModelException.serviceError(statusCode);
    }

    private static List<ResponseInputItem> toInput(List<ChatTurn> history, String prompt) {
        List<ResponseInputItem> input = new ArrayList<>();
        for (ChatTurn turn : history) {
            input.add(message(EasyInputMessage.Role.USER, turn.userPrompt()));
            input.add(message(EasyInputMessage.Role.ASSISTANT, turn.assistantText()));
        }
        input.add(message(EasyInputMessage.Role.USER, prompt));
        return input;
    }

    private static ResponseInputItem message(EasyInputMessage.Role role, String text) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder().role(role).content(text).build());
    }

    /** Клиент создаётся при первом вопросе: без ключа приложение должно запускаться. */
    private OpenAIClient client() {
        OpenAIClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) client = OpenAIOkHttpClient.fromEnv();
                current = client;
            }
        }
        return current;
    }
}
