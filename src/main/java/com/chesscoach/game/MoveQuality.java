package com.chesscoach.game;

/** Качество хода: сколько процентов шанса на победу он потерял по сравнению с лучшим. */
public enum MoveQuality {
    FORCED("Единственный ход"),
    BEST("Лучший ход"),
    EXCELLENT("Отличный ход"),
    GOOD("Хороший ход"),
    INACCURACY("Неточность"),
    MISTAKE("Ошибка"),
    BLUNDER("Зевок");

    private static final double EXCELLENT_LOSS = 2;
    private static final double GOOD_LOSS = 10;
    private static final double INACCURACY_LOSS = 20;
    private static final double MISTAKE_LOSS = 30;

    private final String label;

    MoveQuality(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static MoveQuality classify(boolean isBestMove, boolean isOnlyMove, double winPercentLoss) {
        if (isOnlyMove) return FORCED;
        if (isBestMove || winPercentLoss <= 0) return BEST;
        if (winPercentLoss < EXCELLENT_LOSS) return EXCELLENT;
        if (winPercentLoss < GOOD_LOSS) return GOOD;
        if (winPercentLoss < INACCURACY_LOSS) return INACCURACY;
        if (winPercentLoss < MISTAKE_LOSS) return MISTAKE;
        return BLUNDER;
    }
}
