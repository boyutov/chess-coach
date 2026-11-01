package com.chesscoach.game;

import com.chesscoach.engine.Engine;

import java.util.Locale;

/** Текстовая запись оценки с точки зрения белых: "+1.25", "M+3" или "M-3". */
public final class EvalFormatter {

    private EvalFormatter() {
    }

    public static String format(int whiteCentipawns) {
        if (Engine.isMateScore(whiteCentipawns)) {
            int moves = Engine.mateInMoves(whiteCentipawns);
            return "M" + (moves > 0 ? "+" : "-") + Math.abs(moves);
        }
        return String.format(Locale.ROOT, "%+.2f", whiteCentipawns / 100.0);
    }
}
