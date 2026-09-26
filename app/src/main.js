import { registerScreen, navigate, goBack } from './router.js';
import {
    initVideoModal,
    closeVideo,
    isVideoOpen
} from './components/video-modal.js';
import { initFeed } from './components/feed.js';
import { App } from '@capacitor/app';

// ============================================================
// DEBUG-ПЛАШКА
// ============================================================
window.showJavaDebug = function(msg) {
    let el = document.getElementById('java-debug-toast');
    if (!el) {
        el = document.createElement('div');
        el.id = 'java-debug-toast';
        el.style.cssText = `
            position: fixed;
            top: 60px;
            left: 50%;
            transform: translateX(-50%);
            background: rgba(255, 100, 0, 0.95);
            color: #fff;
            padding: 10px 16px;
            border-radius: 8px;
            font-size: 12px;
            font-family: monospace;
            z-index: 999999;
            max-width: 90vw;
            pointer-events: none;
            white-space: pre-wrap;
        `;
        document.body.appendChild(el);
    }
    el.textContent = msg;
    clearTimeout(el._t);
    el._t = setTimeout(() => el.remove(), 5000);
};

// ============================================================
// ЭКРАНЫ
// ============================================================
registerScreen('feed', document.getElementById('screen-feed'), {
    onEnter: () => initFeed(),
});
registerScreen('profile', document.getElementById('screen-profile'));
registerScreen('channel', document.getElementById('screen-channel'));
registerScreen('playlists', document.getElementById('screen-playlists'));
registerScreen('subscriptions', document.getElementById('screen-subscriptions'));
registerScreen('history', document.getElementById('screen-history'));
registerScreen('studio', document.getElementById('screen-studio'));
registerScreen('search', document.getElementById('screen-search'));
registerScreen('faq', document.getElementById('screen-faq'));

document.querySelectorAll('[data-back]').forEach(btn => {
    btn.addEventListener('click', () => goBack());
});

initVideoModal();
navigate('feed');

// ============================================================
// СИСТЕМНАЯ КНОПКА "НАЗАД"
// ============================================================
App.addListener('backButton', () => {
    if (isVideoOpen()) {
        closeVideo();
        return;
    }
    if (goBack()) return;
    App.exitApp();
});

console.log('✅ ASMR1 приложение запущено');
