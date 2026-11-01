package com.chesscoach.engine;

/** Уровень сложности соперника: глубина перебора, лимит времени и случайная ошибка в сантипешках. */
public enum Level {
    BEGINNER(2, 300, 150),
    AMATEUR(3, 500, 60),
    INTERMEDIATE(4, 800, 20),
    STRONG(6, 2_000, 0);

    private final int depth;
    private final long timeLimitMs;
    private final int noise;

    Level(int depth, long timeLimitMs, int noise) {
        this.depth = depth;
        this.timeLimitMs = timeLimitMs;
        this.noise = noise;
    }

    public int depth() {
        return depth;
    }

    public long timeLimitMs() {
        return timeLimitMs;
    }

    public int noise() {
        return noise;
    }
}
