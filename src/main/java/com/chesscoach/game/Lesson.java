package com.chesscoach.game;

/** Учебные позиции. Игрок всегда играет за сторону, которая ходит первой. */
public enum Lesson {
    BACK_RANK("Мат по последней горизонтали", "Поставьте мат в один ход.",
            "6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1"),
    KNIGHT_FORK("Вилка конём", "Найдите ход конём, который нападает сразу на короля и ладью.",
            "r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1"),
    ROOK_LADDER("Линейный мат", "Поставьте мат двумя ладьями, оттесняя короля к краю доски.",
            "7k/8/8/8/8/8/R7/1R4K1 w - - 0 1"),
    QUEEN_MATE("Мат ферзём и королём", "Прижмите короля к краю и поставьте мат. Берегитесь пата!",
            "8/8/8/4k3/8/8/8/3QK3 w - - 0 1"),
    PAWN_ENDGAME("Король и пешка", "Проведите пешку в ферзи. Спросите тренера, что такое оппозиция.",
            "4k3/8/4K3/4P3/8/8/8/8 w - - 0 1"),
    OPENING("Принципы дебюта", "Займите центр, развивайте фигуры и сделайте рокировку.",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

    private final String title;
    private final String goal;
    private final String fen;

    Lesson(String title, String goal, String fen) {
        this.title = title;
        this.goal = goal;
        this.fen = fen;
    }

    public String title() {
        return title;
    }

    public String goal() {
        return goal;
    }

    public String fen() {
        return fen;
    }
}
