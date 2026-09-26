/**
 * ============================================================
 * ЛЕНТА
 * ============================================================
 */

import { getFeed } from '../api.js';
import { formatViews, formatDuration, timeAgo, escapeHtml } from '../utils.js';
import { openVideo } from './video-modal.js';

let currentTab = 'recommended';
let currentPage = 1;
let isLoading = false;
let hasMore = true;
let initialized = false;
let feedEl = null;

export function initFeed() {
    if (initialized) return;
    initialized = true;

    feedEl = document.getElementById('feed');

    feedEl.addEventListener('click', (e) => {
        const card = e.target.closest('.video-card');
        if (!card) return;
        openVideo(card.dataset.id);
    });

    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', () => {
            switchTab(tab.dataset.tab);
        });
    });

    feedEl.addEventListener('scroll', () => {
        if (isLoading || !hasMore) return;
        const scrollTop = feedEl.scrollTop;
        const scrollHeight = feedEl.scrollHeight;
        const clientHeight = feedEl.clientHeight;
        if (scrollTop + clientHeight >= scrollHeight - 500) {
            currentPage++;
            loadFeed(currentTab, currentPage, true);
        }
    });

    loadFeed('recommended', 1, false);
    console.log('✅ Feed инициализирован');
}

async function loadFeed(tab, page = 1, append = false) {
    if (isLoading) return;
    if (!hasMore && page > 1) return;

    isLoading = true;

    if (!append) {
        feedEl.innerHTML = '<div class="empty">Загрузка...</div>';
    }

    try {
        const data = await getFeed(tab, page, 20);

        if (!data.success || !data.videos) {
            if (!append) feedEl.innerHTML = '<div class="empty">Ошибка загрузки</div>';
            isLoading = false;
            return;
        }

        const html = data.videos.map(v => renderVideoCard(v)).join('');

        if (append) {
            feedEl.insertAdjacentHTML('beforeend', html);
        } else {
            feedEl.innerHTML = html || '<div class="empty">Нет видео</div>';
        }

        hasMore = data.has_more;

        if (!hasMore && data.videos.length > 0) {
            feedEl.insertAdjacentHTML('beforeend', '<div class="empty">Больше видео нет</div>');
        }

    } catch (e) {
        console.error('Ошибка загрузки ленты:', e);
        if (!append) feedEl.innerHTML = '<div class="empty">Ошибка сети</div>';
    }

    isLoading = false;
}

function renderVideoCard(v) {
    const thumb = v.thumbnail ? `<img src="${v.thumbnail}" alt="" loading="lazy">` : '';
    const views = formatViews(v.views);
    const duration = formatDuration(v.duration);
    const avatar = v.author.avatar
        ? `<img src="${v.author.avatar}" alt="">`
        : `<span>${(v.author.name || '?').charAt(0).toUpperCase()}</span>`;

    return `
        <div class="video-card" data-id="${v.id}">
            <div class="video-thumbnail">
                ${thumb}
                ${duration ? `<span class="duration">${duration}</span>` : ''}
            </div>
            <div class="video-info">
                <div class="author-avatar">${avatar}</div>
                <div class="video-details">
                    <div class="video-title">${escapeHtml(v.title)}</div>
                    <div class="video-meta">${escapeHtml(v.author.name)} • ${views} • ${timeAgo(v.published)}</div>
                </div>
            </div>
        </div>
    `;
}

function switchTab(tab) {
    if (currentTab === tab) return;
    currentTab = tab;
    currentPage = 1;
    hasMore = true;

    document.querySelectorAll('.tab').forEach(t => {
        t.classList.toggle('active', t.dataset.tab === tab);
    });

    feedEl.scrollTop = 0;
    loadFeed(tab, 1, false);
}
