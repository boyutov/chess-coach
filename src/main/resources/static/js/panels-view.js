import { renderBoard } from './board-view.js';
import { whiteSharePercent } from './chess.js';
import { byId, element } from './dom.js';
import { isMuted } from './sounds.js';
import { isLive, reviewedMove, state, visibleReviews } from './state.js';

/** Перерисовывает весь экран по текущему состоянию. */
export function render() {
    renderBoard();
    renderMoves();
    renderEval();
    renderVerdict();
    renderStatus();
    renderControls();
}

export function renderStatus() {
    const { premoves, busy, premoveNote, game } = state;
    let text = game.statusText;
    if (premoves.length > 0) text = `Бот думает… Предварительные ходы: ${premoves.map(premoveText).join(', ')}`;
    else if (busy) text = 'Бот думает… Можно сделать предварительный ход';
    else if (!isLive()) text = 'Просмотр хода — нажмите последний ход, чтобы вернуться';
    else if (premoveNote) text = premoveNote;
    byId('status').textContent = text;
}

function premoveText(uci) {
    const promotion = uci[4] ? '=' + uci[4].toUpperCase() : '';
    return `${uci.slice(0, 2)}–${uci.slice(2, 4)}${promotion}`;
}

function renderMoves() {
    const { moves } = state.game;
    const list = byId('moves');
    const items = [];
    if (moves.length > 0 && !moves[0].white) {
        items.push(element('li', 'num', moves[0].number + '.'), element('li'));
    }
    for (const move of moves) {
        if (move.white) items.push(element('li', 'num', move.number + '.'));
        const item = element('li');
        item.append(moveButton(move, moves.length));
        items.push(item);
    }
    list.replaceChildren(...items);
    list.scrollTop = list.scrollHeight;
}

function moveButton(move, moveCount) {
    const button = element('button', 'move ' + (move.review ? move.review.quality : ''), move.san);
    button.type = 'button';
    button.dataset.ply = move.ply;
    const active = isLive() ? move.ply === moveCount - 1 : move.ply === state.viewPly;
    button.classList.toggle('active', active);
    if (move.review) {
        const dot = element('span', 'dot');
        dot.title = move.review.label;
        button.append(dot);
    }
    return button;
}

function renderEval() {
    const fill = byId('eval-fill');
    const text = isLive() ? state.game.evalText : state.game.moves[state.viewPly].review.evalAfter;
    fill.parentElement.classList.toggle('flipped', !state.game.playerWhite);
    byId('eval-text').textContent = text ? text.replace(/^\+/, '') : '';
    if (text) fill.style.height = whiteSharePercent(text) + '%';
}

function renderVerdict() {
    const moves = visibleReviews();
    const box = byId('verdict');
    box.hidden = moves.length === 0;
    box.replaceChildren(...moves.map(verdictRow));
}

function verdictRow(move) {
    const { review } = move;
    const row = element('div', 'verdict ' + review.quality);
    row.append(
        element('span', 'verdict-who', move.byPlayer ? 'Вы' : 'Бот'),
        element('b', '', review.label),
        ` — ${move.san}. `,
        review.bestSan === move.san || review.quality === 'BEST'
            ? `Оценка позиции: ${review.evalAfter}.`
            : `Движок советовал ${review.bestSan} (${review.evalBefore}), `
                + `после ${move.byPlayer ? 'вашего хода' : 'хода бота'} ${review.evalAfter}.`,
    );
    return row;
}

function renderControls() {
    const { game, busy, hintUci, hintArrow } = state;
    byId('undo').disabled = busy || !game.moves.some((move) => move.byPlayer);
    const hint = byId('hint');
    hint.disabled = busy || game.legalMoves.length === 0 || Boolean(hintUci && hintArrow);
    hint.textContent = hintUci ? 'Показать ход' : 'Подсказка';
    const goal = byId('lesson-goal');
    goal.hidden = !game.lessonGoal;
    goal.textContent = game.lessonGoal ? `${game.lessonTitle}: ${game.lessonGoal}` : '';
    const focus = reviewedMove();
    byId('sound').textContent = isMuted() ? 'Звук: выкл' : 'Звук: вкл';
    byId('focus-label').textContent = focus ? `· разбираем ${focus.san}` : '';
}
