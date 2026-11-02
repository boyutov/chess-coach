package com.chesscoach.archive;

import java.util.Arrays;
import java.util.List;

/**
 * Сохранённая партия.
 *
 * @param ownerWhite ключ игрока белыми; null, если белыми играл бот
 * @param ownerBlack ключ игрока чёрными; null, если чёрными играл бот
 * @param level      уровень бота; null в партии двух людей
 * @param status     ACTIVE для недоигранной партии с ботом, иначе чем партия закончилась
 * @param moves      ходы UCI через пробел
 * @param analysis   оценки всех ходов каждой позиции, см. {@link AnalysisCodec}; null, если разбора ещё нет
 */
public record ArchivedGame(String id, String ownerWhite, String ownerBlack, String whiteName, String blackName,
                           String level, String status, String resignedBy, String moves, int plies, String analysis,
                           long savedAt) {

    public boolean hasOwner(String player) {
        return player.equals(ownerWhite) || player.equals(ownerBlack);
    }

    public List<String> moveList() {
        return moves.isEmpty() ? List.of() : Arrays.asList(moves.split(" "));
    }
}
