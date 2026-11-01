package com.chesscoach.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Глубина и лимит времени анализа, по которому оцениваются ходы игрока. */
@ConfigurationProperties("analysis")
public record AnalysisProperties(int depth, long timeLimitMs) {
}
