/**
 * МОДАЛКА ВИДЕО + НАТИВНЫЙ ПЛЕЕР (ExoPlayer) + описание/комментарии
 */

import { getVideo } from '../api.js';
import { formatViews, timeAgo, escapeHtml } from '../utils.js';

let backdrop, contentModal, contentBody;
let descriptionModal, descriptionBody;
let commentsModal, commentsBody, commentsCount;

let currentVideo = null;
let isOpen = false;
let onCloseCallback = null;

// URL AJAX (для комментариев)
const AJAX_URL = 'https://asmr1.ru/wp-admin/admin-ajax.php';

export function initVideoModal() {
    backdrop = document.getElementById('backdrop');
    contentModal = document.getElementById('content-modal');
    contentBody = document.getElementById('content-body');
    descriptionModal = document.getElementById('description-modal');
    descriptionBody = document.getElementById('description-body');
    commentsModal = document.getElementById('comments-modal');
    commentsBody = document.getElementById('comments-body');
    commentsCount = document.getElementById('comments-count');

    // Кнопки закрытия sheet-модалок
    document.querySelectorAll('[data-close]').forEach(el => {
        el.addEventListener('click', (e) => {
            const target = el.dataset.close;
            if (target === 'description') closeDescription();
            if (target === 'comments') closeComments();
        });
    });

    // Слушаем возврат из нативного плеера
    window.addEventListener('playerReturn', (e) => {
        const t = (e.detail && typeof e.detail.time === 'number') ? e.detail.time : -1;
        console.log('📺 playerReturn, time =', t);
        // Закрываем модалку (плеер закрыт)
        closeVideo();
    });
}

export async function openVideo(id) {
    console.log('🎬 Открываем видео:', id);
    isOpen = true;

    if (backdrop) backdrop.classList.add('active');
    if (contentModal) contentModal.classList.add('active');
    if (contentBody) contentBody.innerHTML = '<div class="empty">Загрузка...</div>';

    document.querySelectorAll('.screen.active').forEach(s => s.classList.add('dimmed'));

    try {
        const data = await getVideo(id);
        if (!data.success || !data.video) {
            if (contentBody) contentBody.innerHTML = '<div class="empty">Видео не найдено</div>';
            return;
        }
        currentVideo = data.video;
        renderContent(currentVideo);

        // Открываем нативный плеер
        if (window.AndroidPlayer && window.AndroidPlayer.open) {
            window.AndroidPlayer.open(
                currentVideo.video_url || '',
                0,
                currentVideo.title || '',
                (currentVideo.author && currentVideo.author.name) || ''
            );
        }
    } catch (e) {
        console.error('Ошибка загрузки:', e);
        if (contentBody) contentBody.innerHTML = '<div class="empty">Ошибка сети</div>';
    }
}

export function closeVideo() {
    if (!isOpen) return;
    contentModal?.classList.remove('active');
    backdrop?.classList.remove('active');
    document.querySelectorAll('.screen.active').forEach(s => s.classList.remove('dimmed'));
    closeDescription();
    closeComments();
    currentVideo = null;
    isOpen = false;
    if (onCloseCallback) { onCloseCallback(); onCloseCallback = null; }
}

export function isVideoOpen() { return isOpen; }
export function isPlayerMinimized() { return false; }
export function onVideoClose(cb) { onCloseCallback = cb; }

// ============================================================
// ОПИСАНИЕ
// ============================================================

export function openDescription() {
    if (!currentVideo || !descriptionModal) return;
    descriptionBody.innerHTML = `<div class="description-text">${escapeHtml(currentVideo.description || 'Описание отсутствует')}</div>`;
    descriptionModal.classList.add('active');
}

export function closeDescription() {
    descriptionModal?.classList.remove('active');
}

// ============================================================
// КОММЕНТАРИИ
// ============================================================

export async function openComments() {
    if (!currentVideo || !commentsModal) return;
    commentsModal.classList.add('active');
    commentsBody.innerHTML = '<div class="empty">Загрузка комментариев...</div>';

    try {
        const url = AJAX_URL + '?action=yt_clone_get_comments_html&post_id=' + currentVideo.id;
        const res = await fetch(url);
        const html = await res.text();

        // Считаем количество комментариев
        const temp = document.createElement('div');
        temp.innerHTML = html;
        const count = temp.querySelectorAll('.comment-item').length;
        if (commentsCount) commentsCount.textContent = count;

        if (count === 0) {
            commentsBody.innerHTML = '<div class="empty">Комментариев пока нет</div>';
        } else {
            commentsBody.innerHTML = html;
        }
    } catch (e) {
        console.error('Ошибка комментариев:', e);
        commentsBody.innerHTML = '<div class="empty">Ошибка загрузки</div>';
    }
}

export function closeComments() {
    commentsModal?.classList.remove('active');
}

// ============================================================
// РЕНДЕР КОНТЕНТА
// ============================================================

function renderContent(v) {
    if (!contentBody) return;

    const avatar = v.author.avatar
        ? `<img src="${v.author.avatar}" alt="">`
        : `<span>${(v.author.name || '?').charAt(0).toUpperCase()}</span>`;

    const shortDesc = (v.description || '').slice(0, 200);
    const hasMoreDesc = (v.description || '').length > 200;

    contentBody.innerHTML = `
        <h1 class="content-title">${escapeHtml(v.title)}</h1>
        <div class="content-meta">${formatViews(v.views)} • ${timeAgo(v.published)}</div>

        <div class="content-author">
            <div class="content-author-avatar">${avatar}</div>
            <div class="content-author-name">${escapeHtml(v.author.name)}</div>
        </div>

        <div class="content-actions">
            <button class="action-btn" id="btn-like">❤️ ${v.likes || 0}</button>
            <button class="action-btn" id="btn-save">🔖 Сохранить</button>
            <button class="action-btn" id="btn-share">🔗 Поделиться</button>
        </div>

        <div class="content-description" id="short-description">
            ${escapeHtml(shortDesc)}${hasMoreDesc ? '...' : ''}
        </div>
        ${hasMoreDesc ? '<button class="action-btn" id="btn-more-desc" style="margin-top:8px;">Показать ещё</button>' : ''}

        <button class="action-btn" id="btn-comments" style="margin-top:16px;width:100%;justify-content:center;">
            💬 Комментарии
        </button>
    `;

    // Обработчики кнопок
    document.getElementById('btn-like')?.addEventListener('click', (e) => {
        e.currentTarget.classList.toggle('active');
        console.log('TODO: лайк');
    });
    document.getElementById('btn-save')?.addEventListener('click', (e) => {
        e.currentTarget.classList.toggle('active');
        console.log('TODO: сохранить');
    });
    document.getElementById('btn-share')?.addEventListener('click', () => {
        console.log('TODO: поделиться');
    });
    document.getElementById('btn-more-desc')?.addEventListener('click', () => {
        openDescription();
    });
    document.getElementById('btn-comments')?.addEventListener('click', () => {
        openComments();
    });
}
