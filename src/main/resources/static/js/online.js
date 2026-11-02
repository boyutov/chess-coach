import * as api from './api.js';
import { byId, element } from './dom.js';
import { receiveOnlineGame } from './game-actions.js';
import { state } from './state.js';
import { showTab } from './tabs.js';

const remote = api.online;
const RECONNECT_MS = 3000;

let me = null;              // состояние с сервера; null — не вошли
let events = null;          // поток обновлений
let knownActiveId = null;   // партия, которую уже открывали
let refreshing = false;
let refreshAgain = false;
let onSignedIn = () => {};

/** @param signedIn вызывается после входа: можно начинать партию с ботом, если экран ещё пуст */
export async function initOnline(signedIn) {
    onSignedIn = signedIn;
    bindForms();
    try {
        await applyState(await remote.state());
        connect();
    } catch (error) {
        if (error.status !== 401) showError(error.message);
        render();
    }
}

function bindForms() {
    const credentials = () => ({ nickname: byId('auth-nick').value, password: byId('auth-pass').value });
    byId('auth-form').addEventListener('submit', (event) => {
        event.preventDefault();
        enter(remote.login(credentials()));
    });
    byId('auth-register').addEventListener('click', () => {
        enter(remote.register({ ...credentials(), inviteCode: byId('auth-invite').value }));
    });
    byId('online-logout').addEventListener('click', logout);
    byId('online-queue').addEventListener('click', () => {
        act(me.searching ? remote.leaveQueue() : remote.joinQueue());
    });
    byId('friend-form').addEventListener('submit', (event) => {
        event.preventDefault();
        const input = byId('friend-nick');
        const nickname = input.value.trim();
        if (!nickname) return;
        act(remote.requestFriend(nickname)).then((done) => {
            if (done) input.value = '';
        });
    });
}

async function enter(login) {
    try {
        const loggedIn = await login;
        byId('auth-pass').value = '';
        showError('');
        await applyState(loggedIn);
        connect();
        if (!state.game || state.game.preview) onSignedIn();
    } catch (error) {
        showError(error.message);
    }
}

async function logout() {
    try {
        await remote.logout();
    } catch (error) {
        showError(error.message);
    }
    signedOut();
}

function signedOut() {
    events?.close();
    events = null;
    me = null;
    knownActiveId = null;
    render();
}

/**
 * Подписка на обновления; при открытии данные перечитываются. При обрыве браузер переподключается сам,
 * но после ответа с ошибкой, например пока сервер перезапускается, сдаётся: тогда подключаемся заново сами.
 */
function connect() {
    events?.close();
    const source = new EventSource('/api/online/events');
    events = source;
    source.addEventListener('update', refresh);
    source.addEventListener('open', refresh);
    source.addEventListener('error', () => {
        if (source.readyState !== EventSource.CLOSED) return;
        setTimeout(() => {
            if (events === source) connect();
        }, RECONNECT_MS);
        refresh();
    });
}

async function refresh() {
    if (refreshing) {
        refreshAgain = true;
        return;
    }
    refreshing = true;
    try {
        applyState(await remote.state());
        await refreshShownGame();
    } catch (error) {
        if (error.status === 401) signedOut();
    }
    refreshing = false;
    if (refreshAgain) {
        refreshAgain = false;
        refresh();
    }
}

async function refreshShownGame() {
    const { game } = state;
    if (game?.online && game.status === 'ACTIVE') receiveOnlineGame(await remote.game(game.id));
}

/** @returns обещание, которое выполнится, когда новая партия будет на экране */
function applyState(next) {
    me = next;
    let opened = Promise.resolve();
    if (me.activeGameId && me.activeGameId !== knownActiveId) {
        // Новая партия открывается сама, откуда бы ни пришла: из очереди или от друга.
        knownActiveId = me.activeGameId;
        opened = openGame(me.activeGameId);
        showTab('online');
    }
    if (!me.activeGameId) knownActiveId = null;
    render();
    return opened;
}

async function openGame(id) {
    try {
        receiveOnlineGame(await remote.game(id));
        render();
    } catch (error) {
        showError(error.message);
    }
}

/** @returns true, если запрос удался */
async function act(request) {
    try {
        const next = await request;
        showError('');
        applyState(next);
        return true;
    } catch (error) {
        if (error.status === 401) signedOut();
        showError(error.message);
        return false;
    }
}

function showError(text) {
    const box = byId('online-error');
    box.hidden = !text;
    box.textContent = text;
}

function render() {
    byId('auth-form').hidden = Boolean(me);
    byId('online-main').hidden = !me;
    const badge = byId('online-badge');
    const pending = me ? me.incomingRequests.length + me.incomingChallenges.length : 0;
    badge.hidden = pending === 0;
    badge.textContent = pending;
    if (!me) return;

    byId('online-me').textContent = me.nickname;
    const queue = byId('online-queue');
    queue.textContent = me.searching ? 'Ищем соперника… Отменить' : 'Найти соперника';
    queue.disabled = Boolean(me.activeGameId);
    renderActive();
    renderLists();
}

function renderActive() {
    const box = byId('online-active');
    if (!me.activeGameId) {
        box.replaceChildren();
        return;
    }
    const card = element('div', 'online-active row spread');
    card.append(element('span', '', 'Партия с ' + me.activeOpponent));
    if (state.game?.id !== me.activeGameId) {
        card.append(button('Вернуться', () => openGame(me.activeGameId)));
    }
    box.replaceChildren(card);
}

function renderLists() {
    const sections = [];
    addSection(sections, 'Вас вызывают', me.incomingChallenges, (name) => [
        button('Играть', () => act(remote.acceptChallenge(name)), 'primary'),
        button('Отказать', () => act(remote.dropChallenge(name))),
    ]);
    addSection(sections, 'Заявки в друзья', me.incomingRequests, (name) => [
        button('Принять', () => act(remote.acceptFriend(name)), 'primary'),
        button('Отклонить', () => act(remote.declineFriend(name))),
    ]);
    addSection(sections, 'Вы вызвали', me.outgoingChallenges, (name) => [
        button('Отменить', () => act(remote.dropChallenge(name))),
    ]);
    sections.push(friendsSection());
    addSection(sections, 'Ждут подтверждения', me.outgoingRequests, () => []);
    byId('online-lists').replaceChildren(...sections);
}

function addSection(sections, title, names, actions) {
    if (names.length === 0) return;
    const section = element('div', 'online-section');
    section.append(element('h3', '', title));
    for (const name of names) section.append(item(name, null, actions(name)));
    sections.push(section);
}

function friendsSection() {
    const section = element('div', 'online-section');
    section.append(element('h3', '', 'Друзья'));
    if (me.friends.length === 0) {
        section.append(element('p', 'online-empty', 'Пока никого. Добавьте друга по нику.'));
    }
    for (const friend of me.friends) {
        const buttons = [];
        const challenged = me.outgoingChallenges.includes(friend.nickname);
        if (friend.online && !friend.playing && !challenged && !me.activeGameId) {
            buttons.push(button('Вызвать', () => act(remote.challenge(friend.nickname))));
        }
        buttons.push(button('Удалить', () => {
            if (confirm(`Удалить ${friend.nickname} из друзей?`)) act(remote.removeFriend(friend.nickname));
        }));
        section.append(item(friend.nickname, friend, buttons));
    }
    return section;
}

function item(name, friend, buttons) {
    const row = element('div', 'online-item');
    if (friend) {
        const dot = element('span', 'presence' + (friend.online ? ' on' : ''));
        dot.title = friend.online ? 'В сети' : 'Не в сети';
        row.append(dot);
    }
    const suffix = friend?.playing ? ' · играет' : '';
    row.append(element('span', 'name', name + suffix), ...buttons);
    return row;
}

function button(text, onClick, className = '') {
    const node = element('button', className, text);
    node.type = 'button';
    node.addEventListener('click', onClick);
    return node;
}
