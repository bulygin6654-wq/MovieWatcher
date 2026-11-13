# MovieWatcher

Android-приложение для двоих — аналог Must: общие списки «Хочу посмотреть» и «Просмотрено», оценки, рейтинг IMDb, поиск по названию, похожие фильмы и аниме на русском.

<img src="docs/icon.png" width="96" align="right" alt="Иконка">

## Что умеет

- **Поиск** фильмов и сериалов (TMDB, названия, описания и постеры на русском) и аниме (Shikimori, поиск по русскому/английскому/японскому названию).
- **Рейтинги**: IMDb (через OMDb), TMDB, Shikimori.
- **Похожее** на каждой карточке и подборка рекомендаций по вашим высоким оценкам (вас обоих).
- **Мои списки**: «Хочу» и «Просмотрено» с оценкой 1–10.
- **Вместе**: что хотите посмотреть оба, что хочет партнёр, что смотрели оба (с обеими оценками), кнопка «Случайный».
- **Пара** через Cloud Firestore: один создаёт код, второй вводит его — списки синхронизируются.
- **Импорт** CSV из Must (формат Letterboxd) и из IMDb.

## Настройка (один раз)

Ключи TMDB и OMDb хранятся в Firebase, а не в приложении и не в репозитории. Приложение читает их при запуске и запоминает на телефоне.

### 1. Ключи API (бесплатно)

- **TMDB**: themoviedb.org → Settings → API → «API Key (v3 auth)».
- **OMDb**: omdbapi.com/apikey.aspx (1000 запросов в сутки).

### 2. Firebase

1. Создайте проект на console.firebase.google.com.
2. **Build → Firestore Database → Create database**.
3. Во вкладке **Rules** вставьте и опубликуйте:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /pairs/{code}/members/{uid} {
      allow read, write: if true;
    }
    match /config/keys {
      allow read: if true;
      allow write: if false;
    }
  }
}
```

4. Во вкладке **Data** создайте коллекцию `config`, в ней документ с ID `keys` и два строковых поля: `tmdb` (ваш ключ TMDB) и `omdb` (ваш ключ OMDb).

Данные пары видит только тот, кто знает код (6 символов). Документ с ключами может прочитать любой, кто знает ID проекта, поэтому используйте только бесплатные ключи. Сменить ключ можно прямо в консоли Firebase, пересборка не нужна.

### 3. Два секрета для сборки

В репозитории: **Settings → Secrets and variables → Actions**:

| Secret | Где взять |
|---|---|
| `FIREBASE_PROJECT` | Firebase Console → Project settings → Project ID |
| `FIREBASE_KEY` | Firebase Console → Project settings → Web API Key |

Эти два значения не секретные в строгом смысле (веб-ключ Firebase предназначен для публичного использования, данные защищают правила Firestore).

## Сборка

APK собирает GitHub Actions при каждом пуше в `main`. Готовый файл — в разделе **Releases → latest → MovieWatcher.apk**. Установите его на оба телефона.

Локально: положите `FIREBASE_PROJECT` и `FIREBASE_KEY` в `local.properties` и выполните `gradle assembleDebug`. При желании туда же можно добавить `TMDB_KEY` и `OMDB_KEY` как запасной вариант, если Firebase недоступен.

## Перенос из Must

В самом Must кнопки экспорта нет. Профиль в Must должен быть публичным.

1. Откройте https://dj-frixz.github.io/export-mustapp-data, введите имя пользователя в Must и скачайте CSV: отдельно «просмотрено» и отдельно «хочу».
2. В приложении: **Ещё → Импорт CSV: просмотрено** (выберите файл с просмотренным), затем **Импорт CSV: хочу посмотреть** (файл со списком «хочу»).
3. Фильмы подбираются через TMDB по id, IMDb id или названию и году. Что не нашлось — приложение покажет числом.

Из IMDb: на странице списка (Watchlist или Your ratings) ⋯ → Export, затем импортируйте так же.

## Структура

- `Api.kt` — TMDB, OMDb, Shikimori
- `Sync.kt` — локальное хранилище и синхронизация пары (Firestore REST)
- `CsvImport.kt` — разбор CSV и сопоставление фильмов
- `AppVM.kt` — состояние и логика
- `Ui.kt` — экраны (Jetpack Compose)

Иконка: два кольца (двое зрителей) и кнопка «play» в пересечении, `res/drawable/ic_launcher_foreground.xml`.
