import { FILES, materialBalance, parseFen, premoveTargets } from './chess.js';
import { byId, element, pieceElement } from './dom.js';
import { confirmedPieces, displayedPieces, isLive, shownMove, state, visibleReviews } from './state.js';

const BADGES = { FORCED: '□', BEST: '★', EXCELLENT: '!', GOOD: '✓', INACCURACY: '?!', MISTAKE: '?', BLUNDER: '??' };
const SVG_NS = 'http://www.w3.org/2000/svg';

const squaresOf = (uci) => [uci.slice(0, 2), uci.slice(2, 4)];

export function renderBoard() {
    const board = byId('board');
    const pieces = displayedPieces();
    const targets = currentTargets(pieces);
    const squares = [];
    for (let index = 0; index < 64; index++) {
        squares.push(squareElement(index, pieces, targets));
    }
    board.replaceChildren(...squares);
    if (isLive() && state.hintUci && state.hintArrow) board.append(arrowElement(state.hintUci));
    // Запланированные взятия ещё не случились, поэтому в счёт материала не входят.
    renderMaterial(confirmedPieces());
}

function currentTargets(pieces) {
    const { selected, pendingUci, game } = state;
    if (!isLive() || !selected || !pieces[selected]) return [];
    if (pendingUci) return premoveTargets(selected, pieces);
    return game.legalMoves.filter((move) => move.startsWith(selected)).map((move) => move.slice(2, 4));
}

function squareElement(index, pieces, targets) {
    const { playerWhite } = state.game;
    const fileIndex = playerWhite ? index % 8 : 7 - (index % 8);
    const rank = playerWhite ? 8 - Math.floor(index / 8) : 1 + Math.floor(index / 8);
    const name = FILES[fileIndex] + rank;
    const piece = pieces[name];

    const square = element('button', 'sq ' + ((fileIndex + rank) % 2 === 0 ? 'light' : 'dark'));
    square.type = 'button';
    square.dataset.square = name;
    square.setAttribute('aria-label', name + (piece ? ' ' + piece : ''));
    addHighlights(square, name, piece, targets);

    if (piece) square.append(pieceElement(piece));
    if (index % 8 === 0) square.append(element('span', 'coord rank', rank));
    if (index >= 56) square.append(element('span', 'coord file', FILES[fileIndex]));
    const badge = qualityBadge(name);
    if (badge) square.append(badge);
    return square;
}

function addHighlights(square, name, piece, targets) {
    const { classList } = square;
    const move = shownMove();
    if (move && squaresOf(move.uci).includes(name)) classList.add('last');
    if (!isLive()) return;
    if (state.selected === name) classList.add('selected');
    if (state.hintUci && state.hintUci.slice(0, 2) === name) classList.add('best');
    if (state.premoves.some((premove) => squaresOf(premove).includes(name))) classList.add('premove');
    if (targets.includes(name)) {
        classList.add('target');
        if (piece) classList.add('occupied');
    }
}

/** Значки стоят и на вашем ходе, и на ответе бота. Если бот взял на той же клетке, виден значок его хода. */
function qualityBadge(name) {
    const move = visibleReviews().findLast((candidate) => candidate.uci.slice(2, 4) === name);
    if (!move) return null;
    const badge = element('span', 'badge ' + move.review.quality, BADGES[move.review.quality]);
    badge.title = move.review.label;
    return badge;
}

function arrowElement(uci) {
    const HEAD_LENGTH = 0.42;
    const HEAD_HALF_WIDTH = 0.3;
    const START_OFFSET = 0.25;
    const center = (square) => {
        const file = FILES.indexOf(square[0]);
        const rank = Number(square[1]) - 1;
        return state.game.playerWhite ? [file + 0.5, 7.5 - rank] : [7.5 - file, rank + 0.5];
    };
    const [[x1, y1], [x2, y2]] = squaresOf(uci).map(center);
    const length = Math.hypot(x2 - x1, y2 - y1);
    const ux = (x2 - x1) / length;
    const uy = (y2 - y1) / length;
    const baseX = x2 - ux * HEAD_LENGTH;
    const baseY = y2 - uy * HEAD_LENGTH;

    const svg = svgElement('svg', { viewBox: '0 0 8 8', class: 'arrows', 'aria-hidden': 'true' });
    svg.append(
        svgElement('line', {
            x1: x1 + ux * START_OFFSET, y1: y1 + uy * START_OFFSET, x2: baseX, y2: baseY, 'stroke-width': 0.17,
        }),
        svgElement('polygon', {
            points: [
                [x2, y2],
                [baseX - uy * HEAD_HALF_WIDTH, baseY + ux * HEAD_HALF_WIDTH],
                [baseX + uy * HEAD_HALF_WIDTH, baseY - ux * HEAD_HALF_WIDTH],
            ].map((point) => point.join(',')).join(' '),
        }),
    );
    return svg;
}

function svgElement(tag, attributes) {
    const node = document.createElementNS(SVG_NS, tag);
    for (const [name, value] of Object.entries(attributes)) node.setAttribute(name, value);
    return node;
}

function renderMaterial(pieces) {
    const { game } = state;
    const { whiteAdvantage, whiteLost, blackLost } = materialBalance(pieces, parseFen(game.startFen));
    const playerLead = game.playerWhite ? whiteAdvantage : -whiteAdvantage;
    const [playerLost, botLost] = game.playerWhite ? [whiteLost, blackLost] : [blackLost, whiteLost];
    fillMaterialRow(byId('material-top'), 'Бот', playerLost, -playerLead);
    fillMaterialRow(byId('material-bottom'), 'Вы', botLost, playerLead);
}

function fillMaterialRow(row, name, capturedPieces, lead) {
    const captured = element('span', 'captured');
    captured.append(...capturedPieces.map(pieceElement));
    row.replaceChildren(element('span', 'player-name', name), captured);
    if (lead > 0) row.append(element('span', 'material-plus', '+' + lead));
}
