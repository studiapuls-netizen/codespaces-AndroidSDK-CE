============================================================
ASMR1 NATIVE — GOOD_COMMENTS_START
Версия: полная JWT-авторизация + лайки + подписки + загрузки + поиск
Дата: 2026-10-07
============================================================

ПРОЕКТ: нативное Android-приложение видеохостинга ASMR1
API: https://asmr1.ru/wp-json/yt/v1/
JWT: https://asmr1.ru/wp-json/jwt-auth/v1/token
САЙТ: https://asmr1.ru

============================================================
📱 СТЕК
============================================================

- Kotlin
- ViewBinding, ViewPager2, Material 3
- Media3 ExoPlayer 1.4.1
- Retrofit 2.11 + Gson + OkHttp logging
- Coil 2.7.0
- Coroutines
- androidx.security:security-crypto:1.1.0-alpha06 (JWT токен)
- Min SDK 24, Target SDK 36, compileSdk 36

============================================================
✅ ЧТО РЕАЛИЗОВАНО (полностью рабочее)
============================================================

【 АВТОРИЗАЦИЯ JWT 】
- JWT-плагин на сайте + wp-config.php + .htaccess
- AuthManager — EncryptedSharedPreferences
- AuthInterceptor — Authorization: Bearer <token>
- LoginFragment — вход
- RegisterFragment — регистрация с капчей (арифметика + rate-limit по IP)
- ProfileFragment — профиль + выход

【 ЛЕНТА 】
- FeedFragment с ViewPager2 + 2 постоянных таба: "В тренде" / "Свежее"
- Таб "Скачано" появляется динамически (если есть загрузки / офлайн)
- Табы в HorizontalScrollView — прокручиваются
- Динамическая видимость таба через SharedPreferences-слушатель
- Офлайн-режим: только "Скачано"
- Скелетоны при загрузке
- Превью через Coil
- bottom_nav с 5 табами

【 ПЛЕЕР (большая модалка) 】
- Открытие снизу вверх
- Кастомные контролы с fade, автоскрытие 3.5с
- Жесты: тап (controls), double-tap (seek ±10), RAPID-SEEK, long-press (×2),
  свайп вверх (fullscreen), свайп вниз (mini)
- VideoProgressBar (тонкая/толстая)
- Палитра из превью (Vibrant + Muted)
- AudioBoost ×3 громкость (+9.5 dB)
- ОФЛАЙН-РЕЖИМ: если скачано — играет локальный файл
- АУДИО-РЕЖИМ: если скачано только аудио — показывается превью + затемнение
  + иконка 🎵 по центру; играет локальный mp3

【 МИНИ-ПЛЕЕР 】
- CardView 16:9 + плашка с кнопками
- Drag, pinch-to-zoom (100–400dp), snap к углам
- Тап → разворот в модалку
- PiP (AspectRatio 16:9, sourceRectHint)

【 ЛАЙКИ 】
- POST /yt/v1/like/{id} — toggle (like/unlike)
- GET /yt/v1/like-status/{id} — текущий статус
- Мгновенный UI-фидбек (оптимистично) + откат при ошибке
- Красная залитая иконка при лайке

【 ПОДПИСКИ 】
- POST /yt/v1/subscribe/{id} — sub/unsub/notify/mute
- GET /yt/v1/subscription/{id} — статус
- На бэке: _user_subscriptions, _subscribers_count, _notifications_settings
- Счётчик подписчиков обновляется реально

【 ПОИСК 】
- SearchFragment с автофокусом и клавиатурой
- Авто-поиск с дебаунсом 500мс
- Скелетоны (6 заглушек)
- История поиска (SharedPreferences, до 20 запросов)
- Чипы сортировки ВНИЗУ: По весу / По дате / По просмотрам / По лайкам / По названию
- Переключатель порядка (↓/↑)
- Инфинит-скролл, swipe-to-refresh

【 ПОДПИСКИ (экран) 】
- SubscriptionsFragment с горизонтальным списком авторов
- Первый кружок — "Все"
- Красная обводка если есть новые видео (has_new)
- Вертикальный список видео от подписок
- Фильтр по автору
- markAuthorAsViewed при просмотре

【 СКАЧИВАНИЕ + ОФЛАЙН (Фаза 3 — полностью готова) 】
- Кнопка "Скачать" → bottom sheet с 2 опциями:
  * Скачать видео (с размером через HEAD-запрос)
  * Скачать только аудио (с размером)
- Если уже скачано — опция серая + красная мусорка рядом (удалить)
- Прогресс: DownloadProgressView (кастомный View) заполняется снизу вверх
  полупрозрачным красным + проценты в тексте
- После скачивания: иконка 🎬/🎵 (белая) + текст "Скачано"
- Файлы в context.filesDir/downloads/
- Метаданные в SharedPreferences (JSON)
- Хранятся ВСЕ данные видео (title, author, thumbnail, duration и т.д.)
  → работает офлайн
- PlaybackModeResolver — определяет откуда играть (локальный / онлайн)
- Экран "Скачано" (таб в ленте):
  * Список скачанного (RecyclerView)
  * Чипы фильтров: Все / Видео / Только аудио
  * Сортировка: ↓ Новые / ↑ Старые
  * Тап → открывает плеер офлайн
  * Кнопка удалить + подтверждение
  * Пустой экран если ничего нет

============================================================
🚧 ЧТО В РАБОТЕ / ЗАГЛУШКИ
============================================================

- Комментарии в CommentsFragment — заглушка "будут здесь"
- Мои видео / История / Плейлисты в профиле — тосты
- Уведомления (nav_bell) — заглушка
- prev/next в плеере и мини-плеере — заглушки
- Watch later / Download / Share / Report в ContentFragment — тосты

============================================================
🎯 ЧТО ДЕЛАЕМ ДАЛЬШЕ — ФАЗА 4: КОММЕНТАРИИ
============================================================

ЦЕЛЬ: реальный список комментариев в CommentsFragment
- Загрузка с бэка
- Отправка новых
- Ответы (вложенные)
- Лайки комментариев
- Сортировка (Новые / Популярные)
- Удаление своих

ПЛАН:
1. PHP-эндпоинты (inc/rest-api.php):
   - GET /yt/v1/comments/{video_id}?page=1&per_page=20&order=new|top
   - POST /yt/v1/comment/{video_id}  {content, parent_id?}
   - POST /yt/v1/comment-like/{comment_id}  (toggle)
   
   Использовать существующие функции:
   - inc/functions/comments.php — get_comments, ответы, лайки
   - inc/ajax/comments.php — yt_clone_ajax_add_comment и т.п.
   
2. Kotlin модели: CommentModels.kt
   - Comment, CommentsResponse, CommentLikeResponse, CommentCreateResponse
   
3. Kotlin клиент:
   - CommentsAdapter.kt + item_comment.xml
   - CommentsFragment.kt — заменить заглушку на реальный список
   - fragment_comments.xml — RecyclerView + поле ввода снизу + сортировка сверху
   - ApiService.kt — 3 метода

4. UI:
   - Скелетоны при загрузке
   - Инфинит-скролл
   - Аватар автора
   - Время ("5 мин назад")
   - Лайк с анимацией
   - Ответы (раскрывающиеся)
   - Поле ввода внизу

============================================================
📁 СТРУКТУРА ПРОЕКТА (актуальная)
============================================================

app-native/app/src/main/
├── AndroidManifest.xml
├── java/ru/asmr1/app/
│   ├── MainActivity.kt                 (999 строк — после рефакторинга)
│   ├── MainActivity.Mini.kt            (156 строк — extension-функции мини-плеера)
│   │
│   ├── auth/
│   │   ├── AuthManager.kt
│   │   ├── AuthInterceptor.kt
│   │   ├── LoginFragment.kt
│   │   └── RegisterFragment.kt
│   │
│   ├── profile/
│   │   └── ProfileFragment.kt
│   │
│   ├── data/
│   │   ├── ApiClient.kt
│   │   ├── ApiService.kt
│   │   ├── AuthApiService.kt
│   │   ├── Video.kt
│   │   ├── FeedResponse.kt
│   │   ├── SearchResponse.kt
│   │   ├── SubscribedAuthor.kt
│   │   ├── User.kt
│   │   ├── LoginRequest.kt
│   │   ├── RegisterModels.kt
│   │   └── InteractionModels.kt        (Like / Subscribe)
│   │
│   ├── player/
│   │   ├── PlayerFragment.kt
│   │   ├── ContentFragment.kt
│   │   ├── DescriptionFragment.kt
│   │   ├── CommentsFragment.kt         (ЗАГЛУШКА — переделать в Фазе 4)
│   │   ├── VideoProgressBar.kt
│   │   ├── RippleView.kt
│   │   ├── AudioBoost.kt
│   │   └── DownloadProgressView.kt     (кастомный View — прогресс скачивания)
│   │
│   ├── ui/
│   │   ├── FeedFragment.kt
│   │   ├── VideoListFragment.kt
│   │   ├── VideoAdapter.kt
│   │   ├── StubFragment.kt
│   │   ├── SearchFragment.kt
│   │   ├── SearchAdapter.kt
│   │   ├── SearchHistoryAdapter.kt
│   │   ├── SubscriptionsFragment.kt
│   │   ├── SubscriptionsAuthorsAdapter.kt
│   │   ├── DownloadsFragment.kt        (Фаза 3)
│   │   └── DownloadsAdapter.kt         (Фаза 3)
│   │
│   ├── download/                       (Фаза 3)
│   │   ├── DownloadModels.kt
│   │   ├── DownloadStorage.kt
│   │   ├── Downloader.kt
│   │   └── PlaybackModeResolver.kt
│   │
│   └── utils/
│       ├── Formatters.kt
│       ├── SearchHistoryManager.kt
│       └── NetworkUtils.kt
│
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   ├── fragment_player.xml
    │   ├── fragment_content.xml
    │   ├── fragment_description.xml
    │   ├── fragment_comments.xml       (ЗАГЛУШКА — переделать)
    │   ├── fragment_feed.xml           (с 3-м табом "Скачано")
    │   ├── fragment_video_list.xml
    │   ├── fragment_stub.xml
    │   ├── fragment_login.xml
    │   ├── fragment_register.xml
    │   ├── fragment_profile.xml
    │   ├── fragment_search.xml
    │   ├── fragment_subscriptions.xml
    │   ├── fragment_downloads.xml      (Фаза 3)
    │   ├── item_video.xml
    │   ├── item_video_skeleton.xml
    │   ├── item_video_search.xml
    │   ├── item_video_skeleton_search.xml
    │   ├── item_search_history.xml
    │   ├── item_subscribed_author.xml
    │   ├── item_subscribed_all.xml
    │   ├── item_download.xml           (Фаза 3)
    │   ├── sheet_video_menu.xml
    │   ├── sheet_subscription_menu.xml
    │   └── sheet_download_menu.xml     (Фаза 3)
    ├── drawable/ (все иконки + bg_*)
    ├── values/
    │   ├── themes.xml
    │   ├── colors.xml
    │   └── strings.xml
    ├── menu/
    │   └── bottom_nav_menu.xml
    └── color/
        └── bottom_nav_item.xml

============================================================
🖥️ БЭКЕНД (что настроено на сервере)
============================================================

СЕРВЕР: root@upvsjicojr (SSH)
САЙТ: /var/www/asmr1.ru/public_html/
ТЕМА: /wp-content/themes/Youtube-clone/

1. JWT Authentication for WP REST API (плагин) — активирован
2. wp-config.php: JWT_AUTH_SECRET_KEY + JWT_AUTH_CORS_ENABLE
3. .htaccess: RewriteRule для HTTP_AUTHORIZATION
4. inc/rest-api.php:
   - GET /yt/v1/feed
   - GET /yt/v1/video/{id}
   - GET /yt/v1/search
   - POST /yt/v1/like/{id}                 (реальный, Фаза 2)
   - GET /yt/v1/like-status/{id}           (Фаза 2)
   - GET /yt/v1/subscriptions/authors
   - GET /yt/v1/subscriptions/feed
   - POST /yt/v1/mark-viewed/{id}
   - GET /yt/v1/register/captcha           (Фаза 1)
   - POST /yt/v1/register                  (Фаза 1)
   - GET /yt/v1/subscription/{id}          (Фаза 2)
   - POST /yt/v1/subscribe/{id}            (Фаза 2)

5. inc/ajax/comments.php — существует, содержит:
   - yt_clone_ajax_add_comment
   - (другие AJAX-обработчики комментариев)

6. inc/functions/comments.php — существует, содержит:
   - yt_clone_get_parent_with_replies
   - yt_clone_get_comments_html
   - (другие функции работы с комментариями)

ПРОВЕРКА JWT:
$ TOKEN=$(curl -s -X POST "https://asmr1.ru/wp-json/jwt-auth/v1/token" \
    -H "Content-Type: application/json" \
    -d '{"username":"serovwa","password":"ПАРОЛЬ"}' | jq -r .token)

Логин: serovwa
Email: serovwa@yandex.ru

Тестовые аккаунты (можно удалить через wp user delete):
- testuser2030 / test2030@asmr1.test / secret123

============================================================
🛠️ КОМАНДЫ ДЛЯ СБОРКИ
============================================================

cd /workspaces/codespaces-AndroidSDK-CE/app-native

# Сборка
./gradlew assembleDebug -Dorg.gradle.java.home=$JAVA_HOME 2>&1 | tail -10

# Только ошибки
./gradlew assembleDebug -Dorg.gradle.java.home=$JAVA_HOME 2>&1 | grep -E "^e: " | head -20

# Clean + сборка (если ViewBinding не обновился)
./gradlew clean
./gradlew assembleDebug -Dorg.gradle.java.home=$JAVA_HOME

# APK
app/build/outputs/apk/debug/app-debug.apk

# Почистить .save от nano
find app/src/main -name "*.save" -exec rm {} \;

============================================================
📌 ПРАВИЛА РАБОТЫ (ВАЖНО для нового чата)
============================================================

1. Бэкап перед каждой правкой (GOOD_<timestamp>)
2. Одна правка → сборка → тест → бэкап
3. ПОЛНЫЙ ФАЙЛ вместо патчей (awk/sed ломают XML/Kotlin на телефоне)
4. Не трогать то, что работает
5. Если сломалось — откат из ~/asmr1_working/GOOD_*/
6. ТОЛЬКО cat > file << 'EOF' для полных файлов
7. НЕ использовать sed для многострочных замен в Kotlin — только полный файл
8. В heredoc с << 'EOF' — $ не экранируется
9. Проверять что файл создался через ls / wc -l / grep
10. При правках на бэке — бэкап файла (cp file file.bak_$(date +%Y%m%d_%H%M%S))
11. Проверка PHP: php -l /path/to/file.php
12. JWT живёт 7 дней, потом переполучить
13. ВАЖНО: если присылаешь большой файл — РАЗБИВАЙ на 2-3 сообщения, чтобы не терять строки

⚠️ ИЗВЕСТНЫЕ ГРАБЛИ:
- Мобильный чат может СКЛЕИТЬ строки при копировании — потом compile error
  "Expecting an element" или "Unexpected tokens"
- После правки fragment_*.xml — ОБЯЗАТЕЛЬНО ./gradlew clean перед сборкой,
  иначе ViewBinding не подхватит новые ID

============================================================
🎯 С ЧЕГО НАЧАТЬ В НОВОМ ЧАТЕ
============================================================

1. Прислать:
   - DUMP_COMMENTS_START_<timestamp>.txt (лежит в ~/asmr1_working/ и в app-native/)
   - README.txt (из папки GOOD_COMMENTS_START_*/)

2. Сказать:
   "Продолжаем ASMR1 Native (Android/Kotlin). Контекст в README.txt и DUMP_COMMENTS_START_*.txt.
    Проект полностью рабочий. Фазы 1-3 закрыты: JWT авторизация, лайки, подписки,
    поиск, скачивание видео/аудио с офлайн-воспроизведением.
    Начинаем ФАЗУ 4 — Комментарии. 
    Нужно:
    1. PHP: 3 эндпоинта (GET comments/{video_id}, POST comment/{video_id}, POST comment-like/{comment_id})
    2. Kotlin: CommentModels.kt, CommentsAdapter.kt, item_comment.xml, переделать CommentsFragment.kt + fragment_comments.xml
    3. ApiService.kt — 3 метода"

3. Прислать (если попросят):
   - CommentsFragment.kt (текущий)
   - fragment_comments.xml (текущий)
   - Список функций из inc/functions/comments.php и inc/ajax/comments.php (на сервере через grep)

============================================================
