package com.chesscoach.archive;

import com.chesscoach.chess.Move;
import com.chesscoach.engine.Engine.ScoredMove;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Записывает оценки компактной строкой: "e2e4:30,d2d4:28;e7e5:-20,…". Позиции разделяет «;», ходы — «,». */
final class AnalysisCodec {

    private AnalysisCodec() {
    }

    static String encode(List<List<ScoredMove>> rankings) {
        return rankings.stream()
                .map(ranking -> ranking.stream()
                        .map(scored -> scored.move().toUci() + ":" + scored.score())
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining(";"));
    }

    static List<List<ScoredMove>> decode(String text) {
        List<List<ScoredMove>> rankings = new ArrayList<>();
        if (text.isEmpty()) return rankings;
        for (String position : text.split(";")) {
            List<ScoredMove> ranking = new ArrayList<>();
            for (String entry : position.split(",")) {
                int colon = entry.indexOf(':');
                ranking.add(new ScoredMove(Move.parseUci(entry.substring(0, colon)),
                        Integer.parseInt(entry.substring(colon + 1))));
            }
            rankings.add(ranking);
        }
        return rankings;
    }
}
