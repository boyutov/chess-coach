import * as api from './api.js';
import { renderBoard } from './board-view.js';
import { addMessage } from './chat.js';
import { isWhite, premoveTargets } from './chess.js';
import { byId, pieceElement } from './dom.js';
import { render, renderStatus } from './panels-view.js';
import { clearHint, displayedPieces, isLive, state } from './state.js';

export async function newGame(options) {
    state.busy = true;
    try {
        state.game = await api.startGame(options);
        state.viewPly = null;
        state.selected = null;
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
            askPromotion(selected + square);
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
    if (state.premove) {
        cancelPremove();
        return;
    }
    if (state.selected && premoveTargets(state.selected, pieces).includes(square)) {
        state.premove = state.selected + square;
        state.selected = null;
    } else {
        const own = piece && isWhite(piece) === state.game.playerWhite;
        state.selected = own && state.selected !== square ? square : null;
    }
    renderBoard();
    renderStatus();
}

/** @returns false, если отменять было нечего */
export function cancelPremove() {
    if (!state.premove && !(state.pendingUci && state.selected)) return false;
    state.premove = null;
    state.selected = null;
    renderBoard();
    renderStatus();
    return true;
}

function askPromotion(base) {
    const dialog = byId('promotion');
    dialog.querySelectorAll('button').forEach((button) => {
        const type = button.dataset.p;
        button.replaceChildren(pieceElement(state.game.playerWhite ? type.toUpperCase() : type));
        button.onclick = () => {
            dialog.hidden = true;
            play(base + type);
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
    render();
    try {
        state.game = await api.sendMove(state.game.id, uci);
    } catch (error) {
        state.premove = null;
        addMessage('error', error.message);
    }
    state.pendingUci = null;
    state.busy = false;
    state.selected = null;
    if (!playQueuedPremove()) render();
}

/** @returns true, если предварительный ход сыгран */
function playQueuedPremove() {
    const queued = state.premove;
    state.premove = null;
    if (!queued) return false;
    // Превращение в предварительном ходе — всегда в ферзя.
    const legal = [queued, queued + 'q'].find((move) => state.game.legalMoves.includes(move));
    if (legal) {
        play(legal);
        return true;
    }
    state.premoveNote = 'Предварительный ход отменён: после ответа бота он невозможен';
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

export function viewMove(ply) {
    state.viewPly = ply === state.game.moves.length - 1 ? null : ply;
    state.selected = null;
    render();
}
