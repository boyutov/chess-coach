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
    onlineStale: false, // пока ход отправлялся, соперник мог ответить: партию нужно перечитать
};

const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

/** Доска до входа на закрытом сервере: начальная позиция, в которой ходить нельзя. */
export function previewGame() {
    return {
        preview: true, online: true, opponent: 'Соперник', id: null, fen: START_FEN, startFen: START_FEN,
        playerWhite: true, playerTurn: false, status: 'PREVIEW',
        statusText: 'Войдите или создайте аккаунт на вкладке «Онлайн», чтобы играть',
        legalMoves: [], legalMoveSans: {}, moves: [],
    };
}

export function isLive() {
    return state.viewPly === null;
}

/** Сохранённая партия: подсказки, отмена и ходы в ней не нужны. */
export const isArchived = () => Boolean(state.game?.archived);

/** Ждём ответа соперника, поэтому нажатия на доску планируют предварительные ходы. */
export function isWaitingForOpponent() {
    const { game, pendingUci } = state;
    return Boolean(pendingUci) || Boolean(game?.online && game.status === 'ACTIVE' && !game.playerTurn);
}

/** Ход, подсвеченный на доске: просматриваемый или последний. */
export function shownMove() {
    const { moves } = state.game;
    if (moves.length === 0) return null;
    return (isLive() ? moves[moves.length - 1] : moves[state.viewPly]) ?? null;
}

/** Ход, про который спрашиваем тренера: выбранный или последний ход игрока. */
export function reviewedMove() {
    if (!state.game) return null;
    const { moves } = state.game;
    if (!isLive()) return moves[state.viewPly] ?? null;
    return moves.findLast((move) => move.byPlayer) ?? null;
}

/** Ходы, оценка которых видна на экране: выбранный или два последних — ваш и бота. */
export function visibleReviews() {
    const { moves } = state.game;
    // В партиях с людьми движок ходы не оценивает.
    return (isLive() ? moves.slice(-2) : [moves[state.viewPly]]).filter((move) => move?.review);
}

/** Фигуры без учёта предварительных ходов. Свой ход виден сразу, пока сервер считает ответ бота. */
export function confirmedPieces() {
    const { game, viewPly } = state;
    // viewPly === -1 — начальная позиция при просмотре партии.
    const fen = isLive() ? game.fen : viewPly < 0 ? game.startFen : game.moves[viewPly].fenAfter;
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
