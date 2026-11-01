package com.chesscoach.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки тренера: провайдер и параметры каждой модели. */
@ConfigurationProperties("coach")
public record CoachProperties(Provider provider, OpenAi openai, Anthropic anthropic) {

    public enum Provider {
        AUTO, OPENAI, ANTHROPIC
    }

    public record OpenAi(String model) {
    }

    public record Anthropic(String model, String effort, long maxTokens) {
    }
}
