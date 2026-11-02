import { byId } from './dom.js';

const PANELS = { coach: 'coach-panel', online: 'online-panel', archive: 'archive-panel' };
const listeners = {};

/** Вкладки в правой колонке. */
export function initTabs() {
    for (const name of Object.keys(PANELS)) {
        byId('tab-' + name).addEventListener('click', () => showTab(name));
    }
}

export function showTab(name) {
    for (const [tab, panel] of Object.entries(PANELS)) {
        byId(panel).hidden = tab !== name;
        byId('tab-' + tab).classList.toggle('active', tab === name);
    }
    listeners[name]?.();
}

/** @param listener вызывается при открытии вкладки */
export function onTabOpen(name, listener) {
    listeners[name] = listener;
}
