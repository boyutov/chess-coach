/** Звуки партии: набор sfx из lichess, лицензия в sounds/NOTICE.md. */

const MUTE_KEY = 'chess-coach-muted';

const load = (name) => {
    const audio = new Audio(`sounds/${name}.mp3`);
    audio.preload = 'auto';
    return audio;
};
const SOUNDS = { move: load('Move'), check: load('Check'), victory: load('Victory'), defeat: load('Defeat') };

let muted = readMuted();

function readMuted() {
    try {
        return localStorage.getItem(MUTE_KEY) === '1';
    } catch {
        return false;
    }
}

export const isMuted = () => muted;

export function setMuted(value) {
    muted = value;
    try {
        localStorage.setItem(MUTE_KEY, value ? '1' : '0');
    } catch {
        // Настройка просто не переживёт перезагрузку страницы.
    }
}

/** Выбирает звук по записи хода: «#» — мат, «+» — шах. Мат звучит победой или поражением игрока. */
export function playMoveSound(san, byPlayer) {
    if (muted || !san) return;
    const audio = san.endsWith('#') ? (byPlayer ? SOUNDS.victory : SOUNDS.defeat)
        : san.endsWith('+') ? SOUNDS.check
            : SOUNDS.move;
    audio.currentTime = 0;
    // Пока на странице ничего не нажимали, браузер запрещает звук: тогда просто молчим.
    audio.play().catch(() => {});
}
