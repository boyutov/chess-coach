const LIST_ITEM = /^\s*[-*] /;

const escapeHtml = (text) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const inlineMarkup = (text) => text
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>');

/** Небольшой Markdown для ответов тренера. Сначала экранируем HTML: текст модели — не разметка. */
export function markdownToHtml(text) {
    return escapeHtml(text).split(/\n{2,}/).map((block) => {
        const lines = block.split('\n');
        if (lines.every((line) => LIST_ITEM.test(line))) {
            const items = lines.map((line) => `<li>${inlineMarkup(line.replace(LIST_ITEM, ''))}</li>`);
            return `<ul>${items.join('')}</ul>`;
        }
        return `<p>${inlineMarkup(block.replace(/^#+\s*/, '')).replace(/\n/g, '<br>')}</p>`;
    }).join('');
}
