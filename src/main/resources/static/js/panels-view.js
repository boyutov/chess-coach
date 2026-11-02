import { renderBoard } from './board-view.js';
import { whiteSharePercent } from './chess.js';
import { byId, element } from './dom.js';
import { isMuted } from './sounds.js';
import { isLive, reviewedMove, state, visibleReviews } from './state.js';

/** Перерисовывает весь экран по текущему состоянию. */
export function render() {
    if (!state.game) return;
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
    const thinking = game.online ? 'Соперник думает…' : 'Бот думает…';
    if (premoves.length > 0) text = `${thinking} Предварительные ходы: ${premoves.map(premoveText).join(', ')}`;
    else if (busy) text = game.online ? 'Ход отправлен…' : 'Бот думает… Можно сделать предварительный ход';
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
    const text = isLive() ? state.game.evalText : state.game.moves[state.viewPly]?.review?.evalAfter;
    const noEval = state.game.online || (state.game.archived && state.game.analysis !== 'DONE');
    fill.parentElement.style.visibility = noEval ? 'hidden' : '';
    fill.parentElement.classList.toggle('flipped', !state.game.playerWhite);
    byId('eval-text').textContent = text ? text.replace(/^\+/, '') : '';
    fill.style.height = (text ? whiteSharePercent(text) : 50) + '%';
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
        element('span', 'verdict-who', move.byPlayer ? 'Вы' : state.game.opponent ?? 'Бот'),
        element('b', '', review.label),
        ` — ${move.san}. `,
        review.bestSan === move.san || review.quality === 'BEST'
            ? `Оценка позиции: ${review.evalAfter}.`
            : `Движок советовал ${review.bestSan} (${review.evalBefore}), `
                + `после ${move.byPlayer ? 'вашего хода' : 'хода соперника'} ${review.evalAfter}.`,
    );
    return row;
}

function renderControls() {
    const { game, busy, hintUci, hintArrow } = state;
    const noHelp = Boolean(game.online || game.archived);
    byId('undo').hidden = noHelp;
    byId('hint').hidden = noHelp;
    byId('review-nav').hidden = !game.archived;
    // Отчёт есть у партий, сохранённых в архиве: уроки и незавершённые партии сюда не относятся.
    byId('open-report').hidden = game.archived || game.preview || game.status === 'ACTIVE' || Boolean(game.lessonGoal);
    byId('resign').hidden = !(game.online && game.status === 'ACTIVE');
    byId('undo').disabled = busy || !game.moves.some((move) => move.byPlayer);
    const hint = byId('hint');
    hint.disabled = busy || game.legalMoves.length === 0 || Boolean(hintUci && hintArrow);
    hint.textContent = hintUci ? 'Показать ход' : 'Подсказка';
    const goal = byId('lesson-goal');
    goal.hidden = !game.lessonGoal;
    goal.textContent = game.lessonGoal ? `${game.lessonTitle}: ${game.lessonGoal}` : '';
    const focus = reviewedMove();
    byId('sound').textContent = isMuted() ? 'Звук: выкл' : 'Звук: вкл';
    byId('focus-label').textContent = game.preview ? '' : game.archived && game.analysis !== 'DONE' ? '· сначала разберите партию' : game.online ? '· выключен в партии с человеком' : focus ? `· разбираем ${focus.san}` : '';
}
