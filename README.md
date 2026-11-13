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

Ключи в код не зашиты. В репозитории: **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Где взять |
|---|---|
| `TMDB_KEY` | themoviedb.org → Settings → API → «API Key (v3 auth)» (бесплатно) |
| `OMDB_KEY` | omdbapi.com/apikey.aspx (бесплатно, 1000 запросов в сутки) |
| `FIREBASE_PROJECT` | Firebase Console → Project settings → Project ID |
| `FIREBASE_KEY` | Firebase Console → Project settings → Web API Key |

### Firebase

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
  }
}
```

Данные видит только тот, кто знает код пары (6 символов). Для двоих этого достаточно.

## Сборка

APK собирает GitHub Actions при каждом пуше в `main`. Готовый файл — в разделе **Releases → latest → MovieWatcher.apk**. Установите его на оба телефона.

Локально: положите ключи в `local.properties` (`TMDB_KEY=...` и т. д.) и выполните `gradle assembleDebug`.

> Ключи попадают внутрь APK. Если репозиторий публичный, APK из релиза скачает кто угодно и сможет вытащить ключи. Лучше сделать репозиторий приватным.

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
