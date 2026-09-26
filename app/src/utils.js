/**
 * ============================================================
 * УТИЛИТЫ
 * Хелперы для форматирования и работы с DOM
 * ============================================================
 */

/**
 * 1234 → "1.2K просмотров"
 */
export function formatViews(n) {
    n = parseInt(n) || 0;
    if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M просмотров';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'K просмотров';
    return n + ' просмотров';
}

/**
 * Секунды → "12:34"
 */
export function formatDuration(sec) {
    sec = parseInt(sec) || 0;
    if (!sec) return '';
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return m + ':' + (s < 10 ? '0' + s : s);
}

/**
 * Дата → "5 мин назад"
 */
export function timeAgo(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr.replace(' ', 'T'));
    const sec = Math.floor((Date.now() - date.getTime()) / 1000);
    if (sec < 60) return 'только что';
    if (sec < 3600) return Math.floor(sec / 60) + ' мин назад';
    if (sec < 86400) return Math.floor(sec / 3600) + ' ч назад';
    if (sec < 2592000) return Math.floor(sec / 86400) + ' дн назад';
    if (sec < 31536000) return Math.floor(sec / 2592000) + ' мес назад';
    return Math.floor(sec / 31536000) + ' г назад';
}

/**
 * Экранирование HTML
 */
export function escapeHtml(s) {
    const div = document.createElement('div');
    div.textContent = s || '';
    return div.innerHTML;
}

/**
 * ============================================================
 * DRAG ВНИЗ (для модалок)
 * handle — за что тянуть
 * target — что двигать
 * onRelease — callback, если свайп засчитан
 * ============================================================
 */
export function setupDragDown(handle, target, onRelease) {
    let startY = 0;
    let currentY = 0;
    let isDragging = false;
    let startTime = 0;

    // ---------- TOUCH ----------
    handle.addEventListener('touchstart', (e) => {
        startY = e.touches[0].clientY;
        currentY = startY;
        isDragging = true;
        startTime = Date.now();
        target.classList.add('dragging');
    }, { passive: true });

    handle.addEventListener('touchmove', (e) => {
        if (!isDragging) return;
        currentY = e.touches[0].clientY;
        const deltaY = currentY - startY;
        if (deltaY > 0) {
            target.style.transform = `translateY(${deltaY}px)`;
        }
    }, { passive: true });

    handle.addEventListener('touchend', () => {
        if (!isDragging) return;
        isDragging = false;
        target.classList.remove('dragging');
        target.style.transform = '';

        const deltaY = currentY - startY;
        const deltaTime = Date.now() - startTime;
        const velocity = deltaY / deltaTime;

        if (deltaY > 100 || (deltaY > 30 && velocity > 0.5)) {
            onRelease();
        }
    });

    // ---------- MOUSE (для браузера) ----------
    handle.addEventListener('mousedown', (e) => {
        startY = e.clientY;
        currentY = startY;
        isDragging = true;
        startTime = Date.now();
        target.classList.add('dragging');

        const onMove = (ev) => {
            if (!isDragging) return;
            currentY = ev.clientY;
            const deltaY = currentY - startY;
            if (deltaY > 0) {
                target.style.transform = `translateY(${deltaY}px)`;
            }
        };

        const onUp = () => {
            if (!isDragging) return;
            isDragging = false;
            target.classList.remove('dragging');
            target.style.transform = '';
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);

            const deltaY = currentY - startY;
            if (deltaY > 100) onRelease();
        };

        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
    });
}
