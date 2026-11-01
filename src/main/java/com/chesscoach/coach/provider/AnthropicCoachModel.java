package com.chesscoach.coach.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.chesscoach.coach.ChatTurn;
import com.chesscoach.coach.CoachModel;
import com.chesscoach.coach.CoachModelException;
import com.chesscoach.config.CoachProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/** Тренер на моделях Claude. Ключ берётся из ANTHROPIC_API_KEY. */
@Component
public class AnthropicCoachModel implements CoachModel {

    private static final Logger log = LoggerFactory.getLogger(AnthropicCoachModel.class);
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final CoachProperties.Anthropic settings;
    private volatile AnthropicClient client;

    public AnthropicCoachModel(CoachProperties properties) {
        this.settings = properties.anthropic();
    }

    @Override
    public void stream(String system, List<ChatTurn> history, String prompt, Consumer<String> onText) {
        try (StreamResponse<BetaRawMessageStreamEvent> response =
                     client().beta().messages().createStreaming(params(system, history, prompt))) {
            response.stream().forEach(event -> handleEvent(event, onText));
        } catch (UnauthorizedException e) {
            throw CoachModelException.badKey("ANTHROPIC_API_KEY");
        } catch (RateLimitException e) {
            throw new CoachModelException("Слишком много запросов к ИИ. Подождите минуту и спросите снова.");
        } catch (AnthropicServiceException e) {
            log.warn("Ошибка Claude API: {} {}", e.statusCode(), e.getMessage());
            throw CoachModelException.serviceError(e.statusCode());
        } catch (AnthropicIoException e) {
            log.warn("Нет связи с Claude API", e);
            throw new CoachModelException(CoachModelException.NO_CONNECTION);
        }
    }

    private MessageCreateParams params(String system, List<ChatTurn> history, String prompt) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(settings.model())
                .maxTokens(settings.maxTokens())
                .system(system)
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(settings.effort())).build())
                .cacheControl(BetaCacheControlEphemeral.builder().build())
                // Если классификатор отклонит запрос, сервер сам подберёт другую модель.
                .addBeta(FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        for (ChatTurn turn : history) {
            builder.addUserMessage(turn.userPrompt());
            builder.addAssistantMessage(turn.assistantText());
        }
        return builder.addUserMessage(prompt).build();
    }

    private static void handleEvent(BetaRawMessageStreamEvent event, Consumer<String> onText) {
        event.contentBlockDelta()
                .flatMap(delta -> delta.delta().text())
                .ifPresent(text -> onText.accept(text.text()));
        event.messageDelta()
                .flatMap(delta -> delta.delta().stopReason())
                .filter(BetaStopReason.REFUSAL::equals)
                .ifPresent(reason -> {
                    throw new CoachModelException(CoachModelException.REFUSED);
                });
    }

    /** Клиент создаётся при первом вопросе: без ключа приложение должно запускаться. */
    private AnthropicClient client() {
        AnthropicClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) client = AnthropicOkHttpClient.fromEnv();
                current = client;
            }
        }
        return current;
    }
}
