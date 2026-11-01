/** Запросы к серверу. */

const JSON_HEADERS = { 'Content-Type': 'application/json' };

async function request(path, body) {
    const response = await fetch('/api' + path, {
        method: body === undefined ? 'GET' : 'POST',
        headers: JSON_HEADERS,
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || 'Ошибка сервера: ' + response.status);
    return data;
}

export const fetchLessons = () => request('/lessons');
export const startGame = (options) => request('/games', options);
export const sendMove = (gameId, uci) => request(`/games/${gameId}/moves`, { uci });
export const undoMove = (gameId) => request(`/games/${gameId}/undo`, {});
export const fetchHint = (gameId) => request(`/games/${gameId}/hint`);

/** Отправляет вопрос тренеру и вызывает onEvent(имя, данные) для событий потока. */
export async function streamChat(gameId, message, focusPly, onEvent) {
    const response = await fetch(`/api/games/${gameId}/chat`, {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({ message, focusPly }),
    });
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
