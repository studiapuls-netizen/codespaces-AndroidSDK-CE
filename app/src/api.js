/**
 * ============================================================
 * API МОДУЛЬ
 * Все fetch-запросы к REST API asmr1.ru
 * ============================================================
 */

const API_URL = 'https://asmr1.ru/wp-json/yt/v1';

/**
 * Лента видео
 * @param {string} tab - 'recommended' | 'latest'
 * @param {number} page - номер страницы (с 1)
 * @param {number} perPage - сколько за раз (по умолч. 20)
 */
export async function getFeed(tab = 'recommended', page = 1, perPage = 20) {
    const url = `${API_URL}/feed?tab=${tab}&page=${page}&per_page=${perPage}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    return res.json();
}

/**
 * Одно видео по ID
 */
export async function getVideo(id) {
    const res = await fetch(`${API_URL}/video/${id}`);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    return res.json();
}

/**
 * Поиск
 */
export async function searchVideos(query, page = 1) {
    const url = `${API_URL}/search?q=${encodeURIComponent(query)}&page=${page}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    return res.json();
}
