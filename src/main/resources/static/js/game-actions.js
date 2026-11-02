import * as api from './api.js';
import { renderBoard } from './board-view.js';
import { addMessage } from './chat.js';
import { isPromotion, isWhite, premoveTargets } from './chess.js';
import { byId, pieceElement } from './dom.js';
import { render, renderStatus } from './panels-view.js';
import { isMuted, playMoveSound, setMuted } from './sounds.js';
import { clearHint, displayedPieces, isLive, isWaitingForOpponent, state } from './state.js';

/** Растёт при каждой смене партии на доске: ответ сервера на устаревший запрос ей не мешает. */
let boardGeneration = 0;

export async function newGame(options) {
    state.busy = true;
    const generation = ++boardGeneration;
    try {
        const game = await api.startGame(options);
        // Пока бот считал, могла открыться партия с человеком: она важнее.
        if (generation !== boardGeneration) return;
        state.game = game;
        // Играя чёрными, вы получаете партию уже с первым ходом бота.
        state.game.moves.slice(-1).forEach((move) => playMoveSound(move.san, move.byPlayer));
        state.viewPly = null;
        state.selected = null;
        state.premoves = [];
        state.premoveNote = '';
        clearHint();
    } catch (error) {
        if (error.status === 401) byId('tab-online').click();
        else addMessage('error', error.message);
    }
    state.busy = false;
    if (state.game) render();
}

export function onSquareClick(square) {
    if (!state.game || !isLive()) return;
    if (isWaitingForOpponent()) {
        onPremoveClick(square);
    } else if (!state.busy) {
        onMoveClick(square);
    }
}

function onMoveClick(square) {
    const { selected, game } = state;
    if (selected) {
        const options = game.legalMoves.filter((move) => move.startsWith(selected + square));
        if (options.length === 1) {
            play(options[0]);
            return;
        }
        if (options.length > 1) {
            askPromotion(selected + square, game.playerWhite, play);
            return;
        }
    }
    const canSelect = game.legalMoves.some((move) => move.startsWith(square)) && selected !== square;
    state.selected = canSelect ? square : null;
    renderBoard();
}

function onPremoveClick(square) {
    const pieces = displayedPieces();
    const piece = pieces[square];
    const { selected } = state;
    if (selected && premoveTargets(selected, pieces).includes(square)) {
        state.selected = null;
        if (isPromotion(pieces[selected], square)) {
            renderBoard();
            askPromotion(selected + square, state.game.playerWhite, addPremove);
            return;
        }
        state.premoves.push(selected + square);
    } else {
        const own = piece && isWhite(piece) === state.game.playerWhite;
        state.selected = own && state.selected !== square ? square : null;
    }
    renderBoard();
    renderStatus();
}

/** Пока открыт выбор фигуры, бот мог уже ответить: тогда ход не ждёт в очереди, а играется сразу. */
function addPremove(uci) {
    state.premoves.push(uci);
    if (isWaitingForOpponent()) {
        renderBoard();
        renderStatus();
    } else if (!playQueuedPremove()) {
        render();
    }
}

/** Отменяет всю очередь. @returns false, если отменять было нечего */
export function cancelPremove() {
    if (state.premoves.length === 0 && !(isWaitingForOpponent() && state.selected)) return false;
    state.premoves = [];
    state.selected = null;
    renderBoard();
    renderStatus();
    return true;
}

function askPromotion(base, white, onChoose) {
    const dialog = byId('promotion');
    dialog.querySelectorAll('button').forEach((button) => {
        const type = button.dataset.p;
        button.replaceChildren(pieceElement(white ? type.toUpperCase() : type));
        button.onclick = () => {
            dialog.hidden = true;
            onChoose(base + type);
        };
    });
    dialog.hidden = false;
}

async function play(uci) {
    state.selected = null;
    state.premoveNote = '';
    clearHint();
    state.busy = true;
    state.pendingUci = uci;
    playMoveSound(state.game.legalMoveSans[uci], true);
    render();
    try {
        const { length } = state.game.moves;
        state.game = state.game.online
            ? await api.online.move(state.game.id, uci)
            : await api.sendMove(state.game.id, uci);
        // Свой ход уже прозвучал при нажатии; теперь — ответ бота, если партия продолжается.
        state.game.moves.slice(length + 1).forEach((move) => playMoveSound(move.san, move.byPlayer));
    } catch (error) {
        state.premoves = [];
        addMessage('error', error.message);
    }
    state.pendingUci = null;
    state.busy = false;
    state.selected = null;
    if (!playQueuedPremove()) render();
    if (state.onlineStale && !state.pendingUci) await reloadOnlineGame();
}

async function reloadOnlineGame() {
    state.onlineStale = false;
    if (!state.game?.online) return;
    try {
        receiveOnlineGame(await api.online.game(state.game.id));
    } catch (error) {
        addMessage('error', error.message);
    }
}

/** Показывает партию с человеком: открывает новую или применяет ход соперника. */
export function receiveOnlineGame(game) {
    if (state.pendingUci) {
        state.onlineStale = true;
        return;
    }
    const previous = state.game;
    const same = previous?.id === game.id;
    // Ответ на более ранний запрос мог прийти позже нового.
    if (same && game.moves.length < previous.moves.length) return;
    const fresh = same ? game.moves.slice(previous.moves.length) : [];
    if (!same) boardGeneration++;
    state.game = game;
    if (!same) {
        state.busy = false;
        state.viewPly = null;
        state.premoves = [];
        state.premoveNote = '';
        state.selected = null;
        clearHint();
    }
    if (fresh.length > 0) state.selected = null;
    fresh.forEach((move) => playMoveSound(move.san, move.byPlayer));
    if (!playQueuedPremove()) render();
}

export async function resign() {
    if (!state.game?.online || !confirm('Сдаться в этой партии?')) return;
    try {
        state.premoves = [];
        receiveOnlineGame(await api.online.resign(state.game.id));
    } catch (error) {
        addMessage('error', error.message);
    }
}

/** @returns true, если предварительный ход сыгран */
function playQueuedPremove() {
    // Соперник ещё не ответил: очередь ждёт.
    if (isWaitingForOpponent()) return false;
    const queued = state.premoves.shift();
    if (!queued) return false;
    if (state.game.legalMoves.includes(queued)) {
        play(queued);
        return true;
    }
    // Следующие ходы рассчитаны на этот, поэтому без него теряют смысл. Итог партии заметка не заслоняет.
    state.premoves = [];
    if (state.game.status === 'ACTIVE') {
        state.premoveNote = 'Предварительные ходы отменены: после ответа соперника ход невозможен';
    }
    return false;
}

export async function undo() {
    if (!state.game) return;
    state.busy = true;
    try {
        state.game = await api.undoMove(state.game.id);
        state.viewPly = null;
        state.selected = null;
        state.premoveNote = '';
        clearHint();
    } catch (error) {
        addMessage('error', error.message);
    }
    state.busy = false;
    render();
}

/** Первое нажатие показывает, какой фигурой ходить, второе — сам ход стрелкой. */
export async function showHint() {
    if (!state.game) return;
    if (state.hintUci) {
        state.hintArrow = true;
        render();
        return;
    }
    try {
        const { uci } = await api.fetchHint(state.game.id);
        state.viewPly = null;
        state.hintUci = uci;
        state.hintArrow = false;
        render();
    } catch (error) {
        addMessage('error', error.message);
    }
}

export function toggleSound() {
    setMuted(!isMuted());
    render();
}

/** Показывает партию из архива на доске. keepView оставляет просматриваемый ход, когда готов разбор. */
export function showArchivedGame(game, keepView) {
    boardGeneration++;
    state.busy = false;
    state.game = game;
    if (!keepView) state.viewPly = null;
    state.selected = null;
    state.premoves = [];
    state.premoveNote = '';
    state.pendingUci = null;
    clearHint();
    render();
}

/** Шаг по партии: -1 назад, 1 вперёд. Перед первым ходом стоит начальная позиция. */
export function stepMove(delta) {
    if (!state.game || state.game.moves.length === 0) return;
    const last = state.game.moves.length - 1;
    const current = state.viewPly ?? last;
    viewMove(Math.max(-1, Math.min(last, current + delta)));
}

export function viewMove(ply) {
    state.viewPly = ply === state.game.moves.length - 1 ? null : ply;
    state.selected = null;
    render();
}
