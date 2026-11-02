import { streamChat } from './api.js';
import { byId, element } from './dom.js';
import { markdownToHtml } from './markdown.js';
import { reviewedMove, state } from './state.js';

export function addMessage(kind, text) {
    const message = element('div', 'msg ' + kind, text);
    byId('chat').append(message);
    message.scrollIntoView({ block: 'end' });
    return message;
}

export async function askCoach(question) {
    if (state.chatting || !state.game || !question.trim()) return;
    if (state.game.preview) {
        addMessage('error', 'Войдите в аккаунт на вкладке «Онлайн», чтобы спросить тренера');
        return;
    }
    if (state.game.archived && state.game.analysis !== 'DONE') {
        addMessage('error', 'Сначала разберите партию на вкладке «Партии»: тренеру нужны расчёты движка');
        return;
    }
    if (state.game.online) {
        addMessage('error', 'В партии с человеком тренер выключен, чтобы игра была честной');
        return;
    }
    state.chatting = true;
    setChatDisabled(true);
    addMessage('user', question);
    const bubble = addMessage('coach pending', 'Тренер анализирует позицию…');
    let answer = '';
    try {
        await streamChat(state.game.id, question, reviewedMove()?.ply ?? null, (event, data) => {
            if (event === 'delta') {
                answer += data.text;
                showAnswer(bubble, answer);
            } else if (event === 'error') {
                addMessage('error', data.message);
            }
        });
    } catch (error) {
        addMessage('error', error.message);
    }
    if (!answer) bubble.remove();
    state.chatting = false;
    setChatDisabled(false);
    byId('chat-input').focus();
}

function showAnswer(bubble, answer) {
    bubble.classList.remove('pending');
    bubble.innerHTML = markdownToHtml(answer);
    bubble.scrollIntoView({ block: 'end' });
}

function setChatDisabled(disabled) {
    byId('chat-send').disabled = disabled;
    document.querySelectorAll('.quick button').forEach((button) => {
        button.disabled = disabled;
    });
}
