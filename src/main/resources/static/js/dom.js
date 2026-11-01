/** Небольшие помощники для работы с разметкой. */

const GLYPHS = { k: '♚', q: '♛', r: '♜', b: '♝', n: '♞', p: '♟' };
// Просит браузер показать пешку символом, а не цветным эмодзи.
const TEXT_PRESENTATION = '︎';

export const byId = (id) => document.getElementById(id);

export function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

export function pieceElement(piece) {
    const color = piece === piece.toUpperCase() ? 'w' : 'b';
    return element('span', 'piece ' + color, GLYPHS[piece.toLowerCase()] + TEXT_PRESENTATION);
}
