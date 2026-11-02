import * as api from './api.js';
import { byId, element } from './dom.js';
import { showArchivedGame, viewMove } from './game-actions.js';
import { state } from './state.js';
import { onTabOpen, showTab } from './tabs.js';

const SVG_NS = 'http://www.w3.org/2000/svg';
const POLL_MS = 2000;
const OUTCOMES = { WIN: 'Победа', LOSS: 'Поражение', DRAW: 'Ничья', UNFINISHED: 'Не доиграна' };
const QUALITIES = [
    ['BEST', 'Лучшие'], ['EXCELLENT', 'Отличные'], ['GOOD', 'Хорошие'], ['FORCED', 'Единственные'],
    ['INACCURACY', 'Неточности'], ['MISTAKE', 'Ошибки'], ['BLUNDER', 'Зевки'],
];

let pollTimer = null;
let openedId = null;

const panel = () => byId('archive-panel');

export function initArchive() {
    onTabOpen('archive', () => {
        if (!openedId) showList();
    });
}

/** Кнопка «Отчёт о партии» под доской: партия только что закончилась. */
export function openCurrentReport() {
    openGame(state.game.id);
}

async function showList() {
    stopPolling();
    openedId = null;
    try {
        const games = await api.archive.list();
        const rows = [element('h2', '', 'Сохранённые партии')];
        if (games.length === 0) rows.push(element('p', 'online-empty', 'Пока пусто. Партии с ботом и с людьми появятся здесь сами.'));
        rows.push(...games.map(gameRow));
        panel().replaceChildren(...rows);
    } catch (error) {
        panel().replaceChildren(element('p', 'online-empty',
            error.status === 401 ? 'Войдите в аккаунт на вкладке «Онлайн», чтобы партии сохранялись.' : error.message));
    }
}

function gameRow(game) {
    const row = element('button', 'archive-row');
    row.type = 'button';
    const name = element('span', 'name', game.opponent);
    const details = `${game.playerWhite ? 'белыми' : 'чёрными'} · ходов: ${game.moveCount} · `
        + new Date(game.savedAt).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
    name.append(document.createElement('br'), element('small', '', details));
    row.append(element('span', 'outcome', OUTCOMES[game.outcome]), name);
    row.addEventListener('click', () => openGame(game.id));
    return row;
}

async function openGame(id) {
    stopPolling();
    openedId = id;
    try {
        const game = await api.archive.game(id);
        showArchivedGame(game);
        showTab('archive');
        renderReport(game);
        if (game.analysis === 'RUNNING') poll(id);
    } catch (error) {
        panel().replaceChildren(backButton(), element('p', 'online-error', error.message));
    }
}

async function analyze(id) {
    try {
        renderReport(await api.archive.analyze(id));
        poll(id);
    } catch (error) {
        panel().append(element('p', 'online-error', error.message));
    }
}

function poll(id) {
    stopPolling();
    pollTimer = setTimeout(async () => {
        try {
            const game = await api.archive.game(id);
            if (openedId !== id) return;
            if (game.analysis === 'RUNNING') {
                renderReport(game);
                poll(id);
                return;
            }
            // Разбор закончен или прервался: показываем партию уже с оценками, не сбивая просмотр.
            if (state.game?.archiveId === id) showArchivedGame(game, true);
            renderReport(game);
        } catch (error) {
            panel().append(element('p', 'online-error', error.message));
        }
    }, POLL_MS);
}

function stopPolling() {
    clearTimeout(pollTimer);
    pollTimer = null;
}

function backButton() {
    const back = element('button', '', '← Все партии');
    back.type = 'button';
    back.addEventListener('click', showList);
    return back;
}

function renderReport(game) {
    const parts = [backButton(), element('p', 'report-section', game.statusText)];
    if (game.analysis === 'DONE') {
        parts.push(reportSides(game.report), element('div', 'report-section', 'Оценка по ходу партии'),
            evalGraph(game), ...keyMoments(game.report));
        const ask = element('button', 'wide', 'Спросить тренера об этой партии');
        ask.type = 'button';
        ask.addEventListener('click', () => showTab('coach'));
        parts.push(ask);
    } else if (game.analysis === 'RUNNING') {
        parts.push(element('p', '', `Движок разбирает партию: ход ${game.analysisProgress} из ${game.plies}…`));
    } else if (game.plies === 0) {
        parts.push(element('p', 'online-empty', 'В партии не было ходов.'));
    } else {
        const start = element('button', 'primary wide', 'Разобрать партию');
        start.type = 'button';
        start.addEventListener('click', () => analyze(game.archiveId));
        parts.push(start);
    }
    panel().replaceChildren(...parts);
}

function reportSides(report) {
    const box = element('div', 'report-sides');
    box.append(sideCard(report.white, 'Белые'), sideCard(report.black, 'Чёрные'));
    return box;
}

function sideCard(side, color) {
    const card = element('div', 'report-side');
    const name = element('div', 'side-name', side.name);
    const counts = element('div', 'report-counts');
    for (const [quality, label] of QUALITIES) {
        const row = element('div', 'report-count ' + quality);
        const title = element('span');
        title.append(element('span', 'q'), label);
        row.append(title, element('b', '', side.counts[quality] ?? 0));
        counts.append(row);
    }
    card.append(element('small', '', color), name, element('div', 'accuracy', side.accuracy.toFixed(1) + '%'),
        element('small', '', 'точность'), counts);
    return card;
}

/** Светлая область — доля белых, как на шкале оценки у доски. Наведение показывает ход, нажатие открывает его. */
function evalGraph(game) {
    const values = [50, ...game.report.evalPercent];
    const width = Math.max(values.length - 1, 1);
    const wrap = element('div', 'graph-wrap');
    const svg = svgElement('svg', {
        viewBox: `0 0 ${width} 100`, preserveAspectRatio: 'none', class: 'eval-graph', role: 'img',
        'aria-label': 'График оценки: шанс белых на победу после каждого хода',
    });
    const points = values.map((value, index) => `${index},${100 - value}`).join(' ');
    const cursor = svgElement('line', { class: 'cursor', y1: 0, y2: 100, x1: 0, x2: 0, visibility: 'hidden' });
    svg.append(
        svgElement('rect', { class: 'plot', x: 0, y: 0, width, height: 100 }),
        svgElement('polygon', { class: 'area', points: `0,100 ${points} ${width},100` }),
        svgElement('line', { class: 'mid', x1: 0, x2: width, y1: 50, y2: 50 }),
        cursor,
    );
    const tip = element('div', 'graph-tip');
    tip.hidden = true;
    const plyAt = (event) => {
        const box = svg.getBoundingClientRect();
        const ratio = Math.max(0, Math.min(1, (event.clientX - box.left) / box.width));
        return Math.max(0, Math.round(ratio * width) - 1);
    };
    svg.addEventListener('pointermove', (event) => {
        const ply = plyAt(event);
        const move = game.moves[ply];
        if (!move) return;
        const x = (ply + 1) / width;
        cursor.setAttribute('x1', ply + 1);
        cursor.setAttribute('x2', ply + 1);
        cursor.setAttribute('visibility', 'visible');
        tip.hidden = false;
        tip.style.left = `${Math.max(12, Math.min(88, x * 100))}%`;
        tip.textContent = `${move.number}${move.white ? '.' : '…'} ${move.san}: ${move.review.evalAfter}`;
    });
    svg.addEventListener('pointerleave', () => {
        cursor.setAttribute('visibility', 'hidden');
        tip.hidden = true;
    });
    svg.addEventListener('click', (event) => {
        if (game.moves.length > 0) viewMove(plyAt(event));
    });
    wrap.append(svg, tip);
    return wrap;
}

function keyMoments(report) {
    const header = element('div', 'report-section', 'Главные ошибки');
    if (report.keyMoments.length === 0) {
        return [header, element('p', 'online-empty', 'Серьёзных ошибок нет.')];
    }
    return [header, ...report.keyMoments.map((moment) => {
        const row = element('button', 'key-moment ' + moment.quality);
        row.type = 'button';
        const who = moment.white ? report.white.name : report.black.name;
        row.append(
            element('span', 'q'),
            element('b', '', `${moment.number}${moment.white ? '.' : '…'} ${moment.san}`),
            `${moment.label}. Лучше ${moment.bestSan}`,
            element('small', '', who),
        );
        row.addEventListener('click', () => viewMove(moment.ply));
        return row;
    })];
}

function svgElement(tag, attributes) {
    const node = document.createElementNS(SVG_NS, tag);
    for (const [name, value] of Object.entries(attributes)) node.setAttribute(name, value);
    return node;
}
