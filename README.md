# Календарь звонков


[![hexlet-check](https://github.com/UlanNSA/ai-for-developers-project-386/actions/workflows/hexlet-check.yml/badge.svg)](https://github.com/UlanNSA/ai-for-developers-project-386/actions)

Разработайте совместно с ИИ сервис для бронирования календаря

Учебный проект Хекслета: https://ru.hexlet.io/programs/ai-for-developers
Как это должно работать: https://files.hexlet.app/a/2ipc5m

## Стек

- Backend: Kotlin + Ktor (JDK 21, Gradle wrapper), тесты JUnit, линтер ktlint
- Frontend: React + TypeScript + Vite, тесты Vitest, линтер ESLint
- Контракт API: TypeSpec → OpenAPI 3.0.0 (`api/openapi/openapi.yaml`)
- SDK для frontend: генерируется из OpenAPI через `@hey-api/openapi-ts`
- Инструменты и задачи: [mise](https://mise.jdx.dev/) (Java 21, Node.js 24)

Структура: `api/` (TypeSpec), `backend/` (Ktor), `frontend/` (React). Сгенерированный SDK (`frontend/src/api/generated/`) в git не хранится.

## Установка

Windows (PowerShell):

```powershell
winget install jdx.mise
# перезапустите терминал, затем:
mise activate pwsh   # см. https://mise.jdx.dev/getting-started.html
```

```bash
git clone https://github.com/UlanNSA/ai-for-developers-project-386.git
cd ai-for-developers-project-386
mise trust
mise install
```

## Использование

```bash
mise run check:fast     # всё, что запускает CI: формат TypeSpec, дрейф контракта, SDK, lint (--max-warnings 0), тесты, сборки
mise run check          # полный набор: check:fast + e2e
mise run e2e:install    # скачать Chromium для Playwright (один раз; за прокси см. ниже)
mise run e2e            # браузерные E2E: настоящий Ktor в тестовом режиме + Vite + Playwright 1.64.1 (пакет e2e/)
mise run api:drift      # контракт, собранный заново, должен побайтно совпадать с api/openapi/openapi.yaml
mise run api:update     # пересобрать и перезаписать api/openapi/openapi.yaml (закоммитьте результат)
mise run dev:backend    # Ktor на http://localhost:8080 (GET /api/health)
mise run dev:frontend   # Vite на http://localhost:5173, /api проксируется на backend
```

Отдельные задачи: `mise tasks`. Версии Java (`temurin-21.0.13+9.0.LTS`) и Node.js (`24.22.2`) зафиксированы в `mise.toml`; CI состоит из двух независимых заданий: `check` (`mise run check:fast`) и `e2e` (`mise run e2e`, отчёт Playwright сохраняется артефактом `playwright-report`); mise `2026.10.25`.

Задачи `backend:lint`, `backend:test`, `backend:build` не запускайте параллельно (общий `backend/build`); `check:fast` выполняет их одним запуском Gradle (`backend:check`). Если Gradle нужен локальный каталог с зеркалом и кэшем, укажите его переменной окружения `GRADLE_USER_HOME` (в репозиторий такие настройки не попадают). Команды `mise run ...` одинаковы в PowerShell и bash.

### Коммиты и релизы

Коммиты пишутся по [Conventional Commits](https://www.conventionalcommits.org/): `feat`, `fix`, `docs`, `chore`, `ci`, `test`, `refactor`, `!` для breaking changes, с необязательной областью `api`, `backend`, `frontend` или `e2e`, например `feat(frontend): страница записи`. Автоматической проверки (commitlint) нет.

Релизы делает [release-please](https://github.com/googleapis/release-please) (workflow `.github/workflows/release-please.yml`, запуск при push в `master`): одна версия на весь монорепозиторий, один `CHANGELOG.md`, теги `vX.Y.Z`. Пока версия 0.x: `feat` поднимает minor, `fix` — patch, breaking change — тоже minor. Первый релизный PR появляется после первого `feat:`/`fix:` в `master`; слияние PR создаёт тег, GitHub Release и changelog. Образы и иные артефакты не публикуются. Используется стандартный `GITHUB_TOKEN`; владелец включил в настройках репозитория «Allow GitHub Actions to create and approve pull requests» (Settings → Actions → General). Проверки CI на релизном PR могут не запуститься или ждать ручного одобрения.

Версия хранится в `version.txt`, `package.json` и `package-lock.json` пакетов `api`, `frontend`, `e2e` и в `backend/build.gradle.kts` (строка с комментарием `x-release-please-version`). Вручную версии не меняйте; `mise run version:check` (входит в `check:fast`) падает, если они разошлись.

Профиль владельца календаря (`GET /api/owner`) backend берёт из переменных окружения (все необязательные):

| Переменная | По умолчанию |
| --- | --- |
| `OWNER_NAME` | `Владелец календаря` |
| `OWNER_BIO` | `Выберите удобное время для встречи` |
| `OWNER_TIME_ZONE` | `Asia/Almaty` (IANA-идентификатор; неверное значение останавливает запуск с понятной ошибкой) |

Типы встреч (`GET /api/meeting-types`, `GET /api/meeting-types/{id}`) хранятся в памяти backend: при старте создаются два Типа (15 и 30 минут), после перезапуска данные сбрасываются. Главная страница и страница записи `/book/:meetingTypeId` получают их через API.

### Тестовый режим backend (только для E2E)

Включается только переменной `CALENDAR_TEST_MODE=true`; `e2e/playwright.config.ts` задаёт её процессу backend сам. В тестовом режиме:

- время фиксировано переменной `CALENDAR_FIXED_NOW` (ISO-8601 момент, обязательна; для E2E `2026-11-02T06:00:00Z`, понедельник 11:00 в Asia/Almaty); без неё или с неверным значением backend не стартует;
- есть служебный `POST /__test__/reset` (204): удаляет Брони, возвращает два начальных Типа встреч и начальное время;
- есть служебный `POST /__test__/clock` с телом `{"now": "<ISO-8601 момент UTC>"}` (204; 400 при неверном теле): сдвигает фиксированное время (так E2E проверяет, что начавшаяся встреча пропадает из списка Владельца); в OpenAPI обоих маршрутов нет;
- backend слушает только `127.0.0.1`.

В обычном режиме маршруты не зарегистрированы (404), часы системные, а `CALENDAR_FIXED_NOW` без `CALENDAR_TEST_MODE=true` останавливает запуск. Не включайте тестовый режим ни в каком публично доступном окружении.

E2E-пакет `e2e/` отдельный (`npm ci --ignore-scripts` делает `mise run install`), Vitest и `tsc -b` frontend его не затрагивают; ESLint для него не настроен, типы проверяет `mise run e2e:typecheck`. Браузер: часовой пояс `Asia/Tbilisi`, локаль `ru-RU`. Тесты вызывают сброс состояния перед каждым тестом (авто-фикстура). Необязательная переменная `PW_CHANNEL` (например `msedge`, `chrome`) запускает установленный браузер вместо скачанного Chromium (в CI не используется).

### Корпоративная сеть (прокси, свой TLS-сертификат)

SSL-проверку не отключаем. В репозитории только публичные источники (Maven Central, npm). Локальные настройки хранятся вне репозитория:

- `~/.npmrc`: `registry` корпоративного зеркала npm (в lock-файлах прописан `registry.npmjs.org`, npm подменяет хост на настроенный registry).
- `~/.gradle/gradle.properties`: `systemProp.javax.net.ssl.trustStoreType=WINDOWS-ROOT`, чтобы JVM доверяла корпоративному сертификату из хранилища Windows. Прокси: `systemProp.https.proxyHost` / `proxyPort` (логин и пароль только там, не в репозитории).
- `~/.gradle/init.d/*.init.gradle.kts`: при необходимости подмена репозиториев на корпоративное зеркало Maven.
- Скачивание браузера Playwright (`mise run e2e:install`): задайте в своём окружении `HTTPS_PROXY` и `https_proxy` (адрес прокси; на проверенной машине `mise run` обнулял `HTTPS_PROXY`, если не задана строчная форма) и `NODE_USE_SYSTEM_CA=1` (Node 24 доверяет хранилищу сертификатов ОС, включая корпоративный корневой). Проверено на Windows 11: с `NODE_USE_SYSTEM_CA=1` загрузка проходит, без неё падает с `SELF_SIGNED_CERT_IN_CHAIN`. Если хранилище ОС недоступно, укажите `NODE_EXTRA_CA_CERTS` на PEM-файл корпоративного сертификата. `NODE_TLS_REJECT_UNAUTHORIZED=0` не используйте.

---

<details>
<summary>Автоматические тесты Хекслета</summary>

Тесты запускаются на каждый коммит. За запуск отвечает файл `.github/workflows/hexlet-check.yml` — не удаляйте и не переименовывайте ни его, ни репозиторий.

</details>

## О Хекслете

[Хекслет](https://ru.hexlet.io/) — школа программирования: авторские программы обучения с практикой, поддержкой наставников и реальными проектами, которые остаются в резюме. Этот репозиторий — один из таких проектов.
