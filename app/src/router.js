/**
 * ============================================================
 * SPA-РОУТЕР
 * Работает как в приложении YouTube:
 * - Стек экранов
 * - Системная кнопка "Назад" откатывает стек
 * - URL в history (для дебага)
 * ============================================================
 */

const routes = new Map();      // name -> { el, onEnter, onLeave }
let currentRoute = null;
let stack = [];                // стек экранов

/**
 * Регистрация экрана
 */
export function registerScreen(name, element, options = {}) {
    routes.set(name, {
        el: element,
        onEnter: options.onEnter || null,
        onLeave: options.onLeave || null,
    });
    console.log('📌 Экран зарегистрирован:', name);
}

/**
 * Переход на экран (добавляет в стек)
 */
export function navigate(name, params = {}) {
    if (currentRoute === name) return;

    const target = routes.get(name);
    if (!target) {
        console.error('❌ Экран не найден:', name);
        return;
    }

    // Скрываем текущий
    if (currentRoute) {
        const current = routes.get(currentRoute);
        if (current.onLeave) current.onLeave();
        current.el.classList.remove('active');
    }

    // Показываем новый
    currentRoute = name;
    target.el.classList.add('active');
    if (target.onEnter) target.onEnter(params);

    // Пушим в стек + history
    stack.push({ name, params });
    history.pushState({ screen: name, params }, '', '/#' + name);

    console.log('📍 Переход:', name, params, '| Стек:', stack.map(s => s.name).join(' → '));
}

/**
 * Назад (по стеку)
 * @returns {boolean} true, если откатили, false — если некуда
 */
export function goBack() {
    if (stack.length <= 1) {
        console.log('⛔️ Стек пустой, некуда откатывать');
        return false;
    }

    stack.pop();  // снимаем текущий
    const prev = stack[stack.length - 1];

    // Скрываем текущий
    if (currentRoute) {
        const current = routes.get(currentRoute);
        if (current.onLeave) current.onLeave();
        current.el.classList.remove('active');
    }

    // Показываем предыдущий
    currentRoute = prev.name;
    const target = routes.get(prev.name);
    target.el.classList.add('active');
    if (target.onEnter) target.onEnter(prev.params);

    console.log('⬅️ Назад:', prev.name, '| Стек:', stack.map(s => s.name).join(' → '));
    return true;
}

/**
 * Заменить текущий экран (без пуша в стек)
 */
export function replace(name, params = {}) {
    stack.pop();
    navigate(name, params);
}

/**
 * Текущий экран
 */
export function getCurrentRoute() {
    return currentRoute;
}

/**
 * Слушаем браузерную "Назад"
 */
window.addEventListener('popstate', () => {
    goBack();
});
