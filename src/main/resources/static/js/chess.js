/** Шахматные расчёты для экрана. Правила партии проверяет сервер. */

export const FILES = 'abcdefgh';

const PIECE_VALUES = { p: 1, n: 3, b: 3, r: 5, q: 9, k: 0 };
const CAPTURE_ORDER = ['q', 'r', 'b', 'n', 'p'];

export const isWhite = (piece) => piece === piece.toUpperCase();

export function parseFen(fen) {
    const squares = {};
    fen.split(' ')[0].split('/').forEach((row, rowIndex) => {
        let file = 0;
        for (const char of row) {
            if (char >= '1' && char <= '8') {
                file += Number(char);
            } else {
                squares[FILES[file] + (8 - rowIndex)] = char;
                file++;
            }
        }
    });
    return squares;
}

/**
 * Куда фигура ходит геометрически. Позиция к моменту предварительного хода ещё неизвестна, поэтому
 * преграды не учитываются, а легальность проверяется уже после ответа бота.
 */
export function premoveTargets(from, pieces) {
    const piece = pieces[from];
    const white = isWhite(piece);
    const fromFile = FILES.indexOf(from[0]);
    const fromRank = Number(from[1]) - 1;
    const canReach = {
        p: (dx, dy, forward) => (dx === 0 && (forward === 1 || (forward === 2 && fromRank === (white ? 1 : 6))))
            || (dx === 1 && forward === 1),
        n: (dx, dy) => dx * dy === 2,
        b: (dx, dy) => dx === dy,
        r: (dx, dy) => dx === 0 || dy === 0,
        q: (dx, dy) => dx === dy || dx === 0 || dy === 0,
        k: (dx, dy) => (dx <= 1 && dy <= 1) || (from[0] === 'e' && dy === 0 && dx === 2 && fromRank === (white ? 0 : 7)),
    }[piece.toLowerCase()];

    const targets = [];
    for (let file = 0; file < 8; file++) {
        for (let rank = 0; rank < 8; rank++) {
            const dx = Math.abs(file - fromFile);
            const dy = Math.abs(rank - fromRank);
            const forward = (rank - fromRank) * (white ? 1 : -1);
            if ((dx !== 0 || dy !== 0) && canReach(dx, dy, forward)) targets.push(FILES[file] + (rank + 1));
        }
    }
    return targets;
}

/** Перевес белых в материале и взятые фигуры каждого цвета. Точка отсчёта — начальная позиция партии. */
export function materialBalance(pieces, startPieces) {
    const count = (map) => {
        const counts = {};
        for (const piece of Object.values(map)) counts[piece] = (counts[piece] ?? 0) + 1;
        return counts;
    };
    const current = count(pieces);
    const start = count(startPieces);

    let whiteAdvantage = 0;
    for (const [piece, number] of Object.entries(current)) {
        whiteAdvantage += PIECE_VALUES[piece.toLowerCase()] * number * (isWhite(piece) ? 1 : -1);
    }
    const lostPieces = (white) => CAPTURE_ORDER.flatMap((type) => {
        const piece = white ? type.toUpperCase() : type;
        const lost = Math.max(0, (start[piece] ?? 0) - (current[piece] ?? 0));
        return Array(lost).fill(piece);
    });
    return { whiteAdvantage, whiteLost: lostPieces(true), blackLost: lostPieces(false) };
}

/** Доля белых на шкале оценки, %. evalText: "+0.40", "M+3" или "M-3". */
export function whiteSharePercent(evalText) {
    let centipawns = Number(evalText) * 100;
    if (evalText.startsWith('M')) centipawns = evalText.startsWith('M+') ? 2000 : -2000;
    return 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * centipawns)) - 1);
}
