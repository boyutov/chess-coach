package com.chesscoach.coach;

import com.chesscoach.chess.Board;
import com.chesscoach.chess.Move;
import com.chesscoach.engine.Engine;
import com.chesscoach.engine.Engine.ScoredMove;
import com.chesscoach.game.EvalFormatter;
import com.chesscoach.game.Game;
import com.chesscoach.game.MoveRecord;
import com.chesscoach.game.MoveReview;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Собирает для тренера факты о партии: позицию, ходы и расчёты движка. Модель не считает варианты
 * сама и не обязана помнить доску, иначе она могла бы объяснять ходы, которых на доске нет.
 */
@Component
public class CoachPromptBuilder {

    public static final String SYSTEM_PROMPT = """
            Ты — терпеливый шахматный тренер в приложении, где ученик играет с ботом. Ученик спрашивает,
            почему его ход или ход бота хорош или плох и почему нужно было играть иначе. Отвечай на языке ученика.

            К каждому вопросу приложение прикладывает блок <факты>: текущую позицию, список ходов и
            расчёты движка. Расчёты движка — источник истины о том, какой ход сильнее и какие варианты
            возможны. Не придумывай варианты и оценки, которых там нет. Если нужного варианта в фактах нет,
            так и скажи и рассуждай общими принципами, явно отделяя их от расчёта. Блок <факты> виден только
            тебе: ученик видит доску и свой вопрос.

            Как объяснять:
            - Объясняй идею подробно: что угрожает, какая фигура что защищает, какой план стоит за ходом.
              Оценка в пешках сама по себе ничего не объясняет — перескажи её человеческим языком.
            - Сравнивая ходы, показывай конкретный вариант: «если вы так, то соперник так, и тогда…».
            - Называй ходы так же, как на экране (Nf3, exd5, O-O), и уточняй словами: «конь на f3».
            - Если ученик спросил про ход, который слабее рекомендованного лишь на доли пешки, скажи, что
              оба хода нормальные, и объясни разницу в планах.
            - Подстраивайся под уровень ученика. Новые термины (вилка, связка, оппозиция) объясняй сразу.
            - Когда ученик просит подсказку, сначала дай намёк, а готовый ход назови, только если он просит.
            - Хвали по делу и не ругай за ошибки: ошибка — материал для урока.

            Оценки указаны в пешках с точки зрения белых: плюс — лучше у белых, минус — у чёрных.
            M+3 значит, что белые ставят мат за 3 хода, M-3 — что чёрные.
            """;

    private static final int LINE_PLIES = 5;
    private static final int LINE_DEPTH = 3;
    private static final long LINE_TIME_MS = 120;
    private static final int TOP_LINES = 3;

    private final Engine engine;

    public CoachPromptBuilder(Engine engine) {
        this.engine = engine;
    }

    /** @param focusPly номер хода (с нуля), про который спрашивает ученик: его или бота; null — последний ход ученика */
    public String build(Game game, Integer focusPly, String question) {
        StringBuilder facts = new StringBuilder("<факты>\n");
        appendGameSummary(facts, game);
        movesToReview(game.moves(), focusPly).forEach(move -> appendReview(facts, move, question));
        List<ScoredMove> analysis = game.analysis();
        if (!analysis.isEmpty()) {
            facts.append("\nРасчёт текущей позиции (ход и оценка, от лучшего к худшему):\n");
            appendRanking(facts, game.board(), analysis);
            appendLines(facts, game.board(), analysis, null, question);
        }
        return facts.append("</факты>\n\nВопрос ученика: ").append(question).toString();
    }

    private static void appendGameSummary(StringBuilder facts, Game game) {
        Board board = game.board();
        facts.append("Ученик играет ").append(game.playerWhite() ? "белыми" : "чёрными")
                .append(game.level() == null ? ". Соперник — человек, а не бот.\n"
                        : ". Уровень бота: " + game.level() + ".\n");
        if (game.lesson() != null) {
            facts.append("Урок: ").append(game.lesson().title())
                    .append(". Задача: ").append(game.lesson().goal()).append('\n');
        }
        facts.append("Статус партии: ").append(game.status()).append('\n')
                .append("Ходы партии: ").append(moveList(game.moves())).append('\n')
                .append("Текущая позиция (FEN): ").append(board.toFen()).append('\n')
                .append(pieceList(board))
                .append("Сейчас ходят ").append(board.isWhiteToMove() ? "белые" : "чёрные").append(".\n");
    }

    /** Вместе с последним ходом ученика разбираем и ответ бота: на экране видны оценки обоих, спросить могут про любой. */
    private static List<MoveRecord> movesToReview(List<MoveRecord> moves, Integer focusPly) {
        if (moves.isEmpty()) return List.of();
        MoveRecord last = moves.get(moves.size() - 1);
        Optional<MoveRecord> focus = findFocus(moves, focusPly);
        if (focus.isEmpty()) return List.of(last);
        boolean focusIsBeforeBotReply = !last.byPlayer() && moves.size() >= 2 && moves.get(moves.size() - 2) == focus.get();
        return focusIsBeforeBotReply ? List.of(focus.get(), last) : List.of(focus.get());
    }

    /** Выбранный ход, а если выбора нет — последний ход ученика. */
    private static Optional<MoveRecord> findFocus(List<MoveRecord> moves, Integer focusPly) {
        if (focusPly != null && focusPly >= 0 && focusPly < moves.size()) {
            return Optional.of(moves.get(focusPly));
        }
        for (int i = moves.size() - 1; i >= 0; i--) {
            if (moves.get(i).byPlayer()) return Optional.of(moves.get(i));
        }
        return Optional.empty();
    }

    private void appendReview(StringBuilder facts, MoveRecord focus, String question) {
        MoveReview review = focus.review();
        Board before = focus.before();
        facts.append(focus.byPlayer() ? "\nРазбираемый ход ученика: " : "\nРазбираемый ход бота: ").append(before.fullmoveNumber())
                .append(before.isWhiteToMove() ? ". " : "... ").append(focus.san()).append('\n')
                .append("Позиция перед ходом (FEN): ").append(before.toFen()).append('\n')
                .append("Вердикт: ").append(review.quality().label())
                .append(String.format(Locale.ROOT, " (потеря шанса на победу: %.1f%%)", review.winPercentLoss())).append('\n')
                .append("Лучший ход по движку: ").append(review.bestSan())
                .append(", оценка ").append(EvalFormatter.format(review.evalBefore()))
                .append(focus.byPlayer() ? "; после хода ученика оценка " : "; после хода бота оценка ").append(EvalFormatter.format(review.evalAfter())).append('\n')
                .append("Все ходы в той позиции (ход и оценка, от лучшего к худшему):\n");
        appendRanking(facts, before, focus.ranking());
        appendLines(facts, before, focus.ranking(), focus.move(), question);
    }

    private static void appendRanking(StringBuilder facts, Board board, List<ScoredMove> ranking) {
        int whiteSign = board.isWhiteToMove() ? 1 : -1;
        facts.append(ranking.stream()
                .map(scored -> board.toSan(scored.move()) + " " + EvalFormatter.format(scored.score() * whiteSign))
                .collect(Collectors.joining(", "))).append('\n');
    }

    /** Примерные продолжения для лучших ходов, сыгранного хода и ходов, упомянутых в вопросе. */
    private void appendLines(StringBuilder facts, Board board, List<ScoredMove> ranking, Move played, String question) {
        Set<Move> moves = new LinkedHashSet<>();
        ranking.stream().limit(TOP_LINES).forEach(scored -> moves.add(scored.move()));
        if (played != null) moves.add(played);
        moves.addAll(mentionedMoves(board, question));

        facts.append("Вероятные продолжения (расчёт движка):\n");
        for (Move move : moves) {
            facts.append("- ").append(line(board, move)).append('\n');
        }
    }

    private String line(Board board, Move first) {
        StringBuilder line = new StringBuilder(board.toSan(first));
        Board current = board.apply(first);
        for (int ply = 1; ply < LINE_PLIES; ply++) {
            List<ScoredMove> ranking = engine.rank(current, LINE_DEPTH, LINE_TIME_MS, Set.of());
            if (ranking.isEmpty()) break;
            Move next = ranking.get(0).move();
            line.append(' ').append(current.toSan(next));
            current = current.apply(next);
        }
        return line.toString();
    }

    /** Ищет в вопросе ходы этой позиции; понимает и русские буквы фигур: Кf3, Сc4, Лe1, Фd2, Крe2. */
    static List<Move> mentionedMoves(Board board, String question) {
        String normalized = normalizeNotation(question);
        List<Move> found = new ArrayList<>();
        for (Move move : board.legalMoves()) {
            if (containsToken(normalized, board.toSan(move).replaceAll("[+#]", ""))) {
                found.add(move);
            }
        }
        return found;
    }

    private static String normalizeNotation(String text) {
        return text.replace("Кр", "K").replace("кр", "K")
                .replace('К', 'N').replace('С', 'B').replace('Л', 'R').replace('Ф', 'Q')
                .replace('х', 'x').replace("0-0-0", "O-O-O").replace("0-0", "O-O");
    }

    /** "e4" не должно находиться внутри "Ne4": вокруг хода не должно быть букв и цифр. */
    private static boolean containsToken(String text, String token) {
        for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + 1)) {
            int end = at + token.length();
            boolean startOk = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            boolean endOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) return true;
        }
        return false;
    }

    private static String moveList(List<MoveRecord> moves) {
        if (moves.isEmpty()) return "(ходов ещё не было)";
        StringBuilder list = new StringBuilder();
        for (MoveRecord move : moves) {
            Board before = move.before();
            if (before.isWhiteToMove()) {
                list.append(before.fullmoveNumber()).append(". ");
            } else if (list.isEmpty()) {
                list.append(before.fullmoveNumber()).append("... ");
            }
            list.append(move.san());
            if (move.review() != null) list.append(" {").append(move.review().quality().label()).append('}');
            list.append(' ');
        }
        return list.toString().trim();
    }

    private static String pieceList(Board board) {
        StringBuilder white = new StringBuilder();
        StringBuilder black = new StringBuilder();
        for (int square = 0; square < 64; square++) {
            int piece = board.pieceAt(square);
            if (piece == Board.EMPTY) continue;
            String item = "?PNBRQK".charAt(Math.abs(piece)) + Board.squareName(square) + " ";
            (piece > 0 ? white : black).append(item);
        }
        return "Белые фигуры: " + white.toString().trim() + "\nЧёрные фигуры: " + black.toString().trim() + "\n";
    }
}
