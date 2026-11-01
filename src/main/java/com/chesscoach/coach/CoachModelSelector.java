package com.chesscoach.coach;

import com.chesscoach.coach.provider.AnthropicCoachModel;
import com.chesscoach.coach.provider.OpenAiCoachModel;
import com.chesscoach.config.CoachProperties;
import org.springframework.stereotype.Component;

/** Выбирает модель по настройкам и заданным ключам. В режиме AUTO первым идёт OpenAI. */
@Component
public class CoachModelSelector {

    private static final String NO_KEY_MESSAGE = "Чат с тренером не настроен: задайте переменную OPENAI_API_KEY "
            + "и перезапустите приложение. Играть и видеть оценки ходов можно и без неё.";

    private final OpenAiCoachModel openAi;
    private final AnthropicCoachModel anthropic;
    private final CoachProperties.Provider provider;

    public CoachModelSelector(OpenAiCoachModel openAi, AnthropicCoachModel anthropic, CoachProperties properties) {
        this.openAi = openAi;
        this.anthropic = anthropic;
        this.provider = properties.provider();
    }

    public CoachModel select() {
        boolean hasOpenAiKey = hasEnv("OPENAI_API_KEY");
        boolean hasAnthropicKey = hasEnv("ANTHROPIC_API_KEY") || hasEnv("ANTHROPIC_AUTH_TOKEN");
        if (provider == CoachProperties.Provider.ANTHROPIC) return anthropic;
        if (hasOpenAiKey) return openAi;
        if (provider == CoachProperties.Provider.AUTO && hasAnthropicKey) return anthropic;
        throw new CoachModelException(NO_KEY_MESSAGE);
    }

    private static boolean hasEnv(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
