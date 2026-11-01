import { applyMove, parseFen } from './chess.js';

/** Состояние интерфейса. Партию присылает сервер, остальное относится только к экрану. */
export const state = {
    game: null,
    selected: null,     // выбранная клетка, например "e2"
    viewPly: null,      // номер просматриваемого хода; null — текущая позиция
    hintUci: null,      // подсказанный ход: первое нажатие подсвечивает фигуру
    hintArrow: false,   // второе нажатие рисует стрелку
    premoves: [],       // очередь предварительных ходов, например ["e7e5", "a2a1q"]
    premoveNote: '',    // почему предварительные ходы не сыграны
    pendingUci: null,   // ход игрока, отправленный на сервер
    busy: false,        // ждём ответа сервера
    chatting: false,    // тренер отвечает
};

export function isLive() {
    return state.viewPly === null;
}

/** Ход, подсвеченный на доске: просматриваемый или последний. */
export function shownMove() {
    const { moves } = state.game;
    if (moves.length === 0) return null;
    return isLive() ? moves[moves.length - 1] : moves[state.viewPly];
}

/** Ход, про который спрашиваем тренера: выбранный или последний ход игрока. */
export function reviewedMove() {
    if (!state.game) return null;
    const { moves } = state.game;
    if (!isLive()) return moves[state.viewPly];
    return moves.findLast((move) => move.byPlayer) ?? null;
}

/** Ходы, оценка которых видна на экране: выбранный или два последних — ваш и бота. */
export function visibleReviews() {
    const { moves } = state.game;
    return isLive() ? moves.slice(-2) : [moves[state.viewPly]];
}

/** Фигуры без учёта предварительных ходов. Свой ход виден сразу, пока сервер считает ответ бота. */
export function confirmedPieces() {
    const fen = isLive() ? state.game.fen : state.game.moves[state.viewPly].fenAfter;
    const pieces = parseFen(fen);
    return state.pendingUci && isLive() ? applyMove(pieces, state.pendingUci) : pieces;
}

/** Фигуры на показываемой доске: следующий предварительный ход строится от уже запланированных. */
export function displayedPieces() {
    const pieces = confirmedPieces();
    return isLive() ? state.premoves.reduce(applyMove, pieces) : pieces;
}

export function clearHint() {
    state.hintUci = null;
    state.hintArrow = false;
}
