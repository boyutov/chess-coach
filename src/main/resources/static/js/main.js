import { fetchLessons } from './api.js';
import { addMessage, askCoach } from './chat.js';
import { byId, element } from './dom.js';
import { initArchive, openCurrentReport } from './archive.js';
import { cancelPremove, newGame, onSquareClick, resign, showHint, stepMove, toggleSound, undo, viewMove } from './game-actions.js';
import { initOnline } from './online.js';
import { render } from './panels-view.js';
import { previewGame, state } from './state.js';
import { initTabs } from './tabs.js';

const startFromForm = () => newGame({
    playAsWhite: byId('color').value === 'w',
    level: byId('level').value,
    lesson: byId('lesson').value || null,
});

function bindBoard() {
    const board = byId('board');
    board.addEventListener('click', (event) => {
        const square = event.target.closest('.sq');
        if (square) onSquareClick(square.dataset.square);
    });
    // Правая кнопка отменяет предварительный ход.
    board.addEventListener('contextmenu', (event) => {
        if (cancelPremove()) event.preventDefault();
    });
    byId('moves').addEventListener('click', (event) => {
        const move = event.target.closest('.move');
        if (move) viewMove(Number(move.dataset.ply));
    });
    byId('undo').addEventListener('click', undo);
    byId('hint').addEventListener('click', showHint);
    byId('resign').addEventListener('click', resign);
    byId('open-report').addEventListener('click', openCurrentReport);
    byId('nav-start').addEventListener('click', () => viewMove(-1));
    byId('nav-prev').addEventListener('click', () => stepMove(-1));
    byId('nav-next').addEventListener('click', () => stepMove(1));
    byId('nav-end').addEventListener('click', () => viewMove(state.game.moves.length - 1));
    document.addEventListener('keydown', (event) => {
        if (!state.game?.archived || event.target.matches('input, textarea, select')) return;
        if (event.key === 'ArrowLeft') stepMove(-1);
        if (event.key === 'ArrowRight') stepMove(1);
    });
    byId('sound').addEventListener('click', toggleSound);
}

function bindGameForm() {
    byId('new-game').addEventListener('submit', (event) => {
        event.preventDefault();
        startFromForm();
    });
    byId('lesson').addEventListener('change', () => {
        const isLesson = Boolean(byId('lesson').value);
        byId('color').disabled = isLesson;
        byId('level').disabled = isLesson;
    });
}

function bindChat() {
    const input = byId('chat-input');
    byId('chat-form').addEventListener('submit', (event) => {
        event.preventDefault();
        const question = input.value;
        input.value = '';
        askCoach(question);
    });
    input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            byId('chat-form').requestSubmit();
        }
    });
    document.querySelectorAll('.quick button').forEach((button) => {
        button.addEventListener('click', () => askCoach(button.dataset.q));
    });
}

async function loadLessons() {
    try {
        for (const lesson of await fetchLessons()) {
            const option = element('option', '', 'Урок: ' + lesson.title);
            option.value = lesson.id;
            byId('lesson').append(option);
        }
    } catch (error) {
        addMessage('error', error.message);
    }
}

bindBoard();
bindGameForm();
bindChat();
initTabs();
initArchive();
// Сначала онлайн: незаконченная партия с человеком важнее новой партии с ботом, а вкладки работают сразу.
await Promise.all([loadLessons(), initOnline(startFromForm)]);
if (!state.game) await startFromForm();
// На закрытом сервере партия с ботом требует входа: сразу показываем форму.
if (!state.game) showPreview();

function showPreview() {
    state.game = previewGame();
    render();
    byId('tab-online').click();
}
