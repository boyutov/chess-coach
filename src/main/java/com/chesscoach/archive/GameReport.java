package com.chesscoach.archive;

import com.chesscoach.game.MoveQuality;
import com.chesscoach.game.MoveRecord;
import com.chesscoach.game.MoveReview;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Итоги разобранной партии.
 *
 * @param evalPercent шанс белых на победу после каждого хода, %
 * @param keyMoments  самые дорогие ошибки партии в порядке ходов
 */
public record GameReport(Side white, Side black, List<Double> evalPercent, List<KeyMoment> keyMoments) {

    private static final int MAX_KEY_MOMENTS = 6;
    private static final double ACCURACY_SCALE = 103.1668;
    private static final double ACCURACY_DECAY = 0.04354;
    private static final double ACCURACY_OFFSET = 3.1669;

    public record Side(String name, double accuracy, Map<MoveQuality, Integer> counts) {
    }

    public record KeyMoment(int ply, boolean white, int number, String san, String label, String quality,
                            String bestSan, double winPercentLoss) {
    }

    public static GameReport of(List<MoveRecord> moves, String whiteName, String blackName) {
        List<Double> evalPercent = new ArrayList<>(moves.size());
        List<KeyMoment> moments = new ArrayList<>();
        for (int ply = 0; ply < moves.size(); ply++) {
            MoveRecord move = moves.get(ply);
            MoveReview review = move.review();
            evalPercent.add(MoveReview.winPercent(review.evalAfter()));
            if (review.quality() == MoveQuality.MISTAKE || review.quality() == MoveQuality.BLUNDER) {
                moments.add(new KeyMoment(ply, move.before().isWhiteToMove(), move.before().fullmoveNumber(),
                        move.san(), review.quality().label(), review.quality().name(), review.bestSan(),
                        review.winPercentLoss()));
            }
        }
        List<KeyMoment> key = moments.stream()
                .sorted(Comparator.comparingDouble(KeyMoment::winPercentLoss).reversed())
                .limit(MAX_KEY_MOMENTS)
                .sorted(Comparator.comparingInt(KeyMoment::ply))
                .toList();
        return new GameReport(side(moves, true, whiteName), side(moves, false, blackName), evalPercent, key);
    }

    private static Side side(List<MoveRecord> moves, boolean white, String name) {
        Map<MoveQuality, Integer> counts = new EnumMap<>(MoveQuality.class);
        double accuracy = 0;
        int scored = 0;
        for (MoveRecord move : moves) {
            if (move.before().isWhiteToMove() != white) continue;
            counts.merge(move.review().quality(), 1, Integer::sum);
            // Единственный ход ничего не говорит о точности игрока.
            if (move.review().quality() != MoveQuality.FORCED) {
                accuracy += moveAccuracy(move.review().winPercentLoss());
                scored++;
            }
        }
        return new Side(name, scored == 0 ? 100 : accuracy / scored, counts);
    }

    /** Точность хода по потере шанса на победу. Формула как на lichess. */
    static double moveAccuracy(double winPercentLoss) {
        double value = ACCURACY_SCALE * Math.exp(-ACCURACY_DECAY * winPercentLoss) - ACCURACY_OFFSET;
        return Math.max(0, Math.min(100, value));
    }
}
