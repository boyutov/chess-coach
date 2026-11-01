import * as api from './api.js';
import { renderBoard } from './board-view.js';
import { addMessage } from './chat.js';
import { isPromotion, isWhite, premoveTargets } from './chess.js';
import { byId, pieceElement } from './dom.js';
import { render, renderStatus } from './panels-view.js';
import { isMuted, playMoveSound, setMuted } from './sounds.js';
import { clearHint, displayedPieces, isLive, state } from './state.js';

export async function newGame(options) {
    state.busy = true;
    try {
        state.game = await api.startGame(options);
        // Играя чёрными, вы получаете партию уже с первым ходом бота.
        state.game.moves.slice(-1).forEach((move) => playMoveSound(move.san, move.byPlayer));
        state.viewPly = null;
        state.selected = null;
        state.premoves = [];
        state.premoveNote = '';
        clearHint();
    } catch (error) {
        addMessage('error', error.message);
    }
    state.busy = false;
    if (state.game) render();
}

export function onSquareClick(square) {
    if (!state.game || !isLive()) return;
    if (state.pendingUci) {
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
    if (state.pendingUci) {
        renderBoard();
        renderStatus();
    } else if (!playQueuedPremove()) {
        render();
    }
}

/** Отменяет всю очередь. @returns false, если отменять было нечего */
export function cancelPremove() {
    if (state.premoves.length === 0 && !(state.pendingUci && state.selected)) return false;
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
        state.game = await api.sendMove(state.game.id, uci);
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
}

/** @returns true, если предварительный ход сыгран */
function playQueuedPremove() {
    const queued = state.premoves.shift();
    if (!queued) return false;
    if (state.game.legalMoves.includes(queued)) {
        play(queued);
        return true;
    }
    // Следующие ходы рассчитаны на этот, поэтому без него теряют смысл. Итог партии заметка не заслоняет.
    state.premoves = [];
    if (state.game.status === 'ACTIVE') {
        state.premoveNote = 'Предварительные ходы отменены: после ответа бота ход невозможен';
    }
    return false;
}

export async function undo() {
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

export function viewMove(ply) {
    state.viewPly = ply === state.game.moves.length - 1 ? null : ply;
    state.selected = null;
    render();
}
