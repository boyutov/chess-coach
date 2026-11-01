package com.chesscoach.chess;

/**
 * Ход: откуда, куда и (для пешки на последней горизонтали) в какую фигуру превращается.
 * Клетки нумеруются от a1 = 0 до h8 = 63.
 */
public record Move(int from, int to, int promotion) {

    public static Move of(int from, int to) {
        return new Move(from, to, Board.EMPTY);
    }

    /** Разбирает ход в формате UCI: "e2e4", "e7e8q". */
    public static Move parseUci(String uci) {
        if (uci == null || uci.length() < 4 || uci.length() > 5) {
            throw new IllegalArgumentException("Некорректный ход: " + uci);
        }
        int from = Board.squareFromName(uci.substring(0, 2));
        int to = Board.squareFromName(uci.substring(2, 4));
        int promotion = Board.EMPTY;
        if (uci.length() == 5) {
            promotion = switch (uci.charAt(4)) {
                case 'q' -> Board.QUEEN;
                case 'r' -> Board.ROOK;
                case 'b' -> Board.BISHOP;
                case 'n' -> Board.KNIGHT;
                default -> throw new IllegalArgumentException("Некорректное превращение: " + uci);
            };
        }
        return new Move(from, to, promotion);
    }

    public String toUci() {
        String base = Board.squareName(from) + Board.squareName(to);
        return switch (promotion) {
            case Board.QUEEN -> base + "q";
            case Board.ROOK -> base + "r";
            case Board.BISHOP -> base + "b";
            case Board.KNIGHT -> base + "n";
            default -> base;
        };
    }
}
