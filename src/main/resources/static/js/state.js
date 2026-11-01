import { parseFen } from './chess.js';

/** Состояние интерфейса. Партию присылает сервер, остальное относится только к экрану. */
export const state = {
    game: null,
    selected: null,     // выбранная клетка, например "e2"
    viewPly: null,      // номер просматриваемого хода; null — текущая позиция
    hintUci: null,      // подсказанный ход: первое нажатие подсвечивает фигуру
    hintArrow: false,   // второе нажатие рисует стрелку
    premove: null,      // предварительный ход, например "e7e5"
    premoveNote: '',    // почему предварительный ход не сыгран
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

/** Ход игрока, который сейчас разбираем: выбранный или последний. */
export function reviewedMove() {
    if (!state.game) return null;
    const { moves } = state.game;
    if (!isLive()) return moves[state.viewPly].byPlayer ? moves[state.viewPly] : null;
    return moves.findLast((move) => move.byPlayer) ?? null;
}

/** Фигуры на показываемой доске. Свой ход виден сразу, пока сервер считает ответ бота. */
export function displayedPieces() {
    const fen = isLive() ? state.game.fen : state.game.moves[state.viewPly].fenAfter;
    const pieces = parseFen(fen);
    if (state.pendingUci && isLive()) {
        const from = state.pendingUci.slice(0, 2);
        pieces[state.pendingUci.slice(2, 4)] = pieces[from];
        delete pieces[from];
    }
    return pieces;
}

export function clearHint() {
    state.hintUci = null;
    state.hintArrow = false;
}
