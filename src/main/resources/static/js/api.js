/** Запросы к серверу. */

const JSON_HEADERS = { 'Content-Type': 'application/json' };

async function request(path, body) {
    const response = await fetch('/api' + path, {
        method: body === undefined ? 'GET' : 'POST',
        headers: JSON_HEADERS,
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        const error = new Error(data.error || 'Ошибка сервера: ' + response.status);
        error.status = response.status;
        throw error;
    }
    return data;
}

export const fetchLessons = () => request('/lessons');
export const startGame = (options) => request('/games', options);
export const sendMove = (gameId, uci) => request(`/games/${gameId}/moves`, { uci });
export const undoMove = (gameId) => request(`/games/${gameId}/undo`, {});
export const fetchHint = (gameId) => request(`/games/${gameId}/hint`);

export const archive = {
    list: () => request('/archive'),
    game: (id) => request(`/archive/${id}`),
    analyze: (id) => request(`/archive/${id}/analysis`, {}),
};

const nick = encodeURIComponent;
export const online = {
    register: (credentials) => request('/online/register', credentials),
    login: (credentials) => request('/online/login', credentials),
    logout: () => request('/online/logout', {}),
    state: () => request('/online/state'),
    requestFriend: (nickname) => request('/online/friends', { nickname }),
    acceptFriend: (name) => request(`/online/friends/${nick(name)}/accept`, {}),
    declineFriend: (name) => request(`/online/friends/${nick(name)}/decline`, {}),
    removeFriend: (name) => request(`/online/friends/${nick(name)}/remove`, {}),
    challenge: (name) => request(`/online/challenges/${nick(name)}`, {}),
    acceptChallenge: (name) => request(`/online/challenges/${nick(name)}/accept`, {}),
    dropChallenge: (name) => request(`/online/challenges/${nick(name)}/drop`, {}),
    joinQueue: () => request('/online/queue', {}),
    leaveQueue: () => request('/online/queue/leave', {}),
    game: (id) => request(`/online/games/${id}`),
    move: (id, uci) => request(`/online/games/${id}/moves`, { uci }),
    resign: (id) => request(`/online/games/${id}/resign`, {}),
};

/** Отправляет вопрос тренеру и вызывает onEvent(имя, данные) для событий потока. */
export async function streamChat(gameId, message, focusPly, onEvent) {
    const response = await fetch(`/api/games/${gameId}/chat`, {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ message, focusPly }),
    });
    if (response.status === 403 || response.status === 429) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || 'Тренер сейчас недоступен');
    }
    if (!response.ok || !response.body) throw new Error('Не удалось связаться с сервером');
    await readEvents(response.body, onEvent);
}

/** Разбирает поток Server-Sent Events: EventSource не умеет отправлять POST. */
async function readEvents(stream, onEvent) {
    const reader = stream.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';
    for (;;) {
        const { value, done } = await reader.read();
        if (done) return;
        buffer += value.replace(/\r\n/g, '\n');
        let end;
        while ((end = buffer.indexOf('\n\n')) >= 0) {
            dispatchFrame(buffer.slice(0, end), onEvent);
            buffer = buffer.slice(end + 2);
        }
    }
}

function dispatchFrame(frame, onEvent) {
    let event = 'message';
    let data = '';
    for (const line of frame.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) data += line.slice(5);
    }
    if (data) onEvent(event, JSON.parse(data));
}
