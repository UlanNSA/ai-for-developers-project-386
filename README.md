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
mise run check          # всё, что запускает CI: TypeSpec → OpenAPI → SDK, lint, тесты, сборки
mise run dev:backend    # Ktor на http://localhost:8080 (GET /api/health)
mise run dev:frontend   # Vite на http://localhost:5173, /api проксируется на backend
```

Отдельные задачи: `mise tasks`.

### Корпоративная сеть (прокси, свой TLS-сертификат)

SSL-проверку не отключаем. В репозитории только публичные источники (Maven Central, npm). Локальные настройки хранятся вне репозитория:

- `~/.npmrc`: `registry` корпоративного зеркала npm (в lock-файлах прописан `registry.npmjs.org`, npm подменяет хост на настроенный registry).
- `~/.gradle/gradle.properties`: `systemProp.javax.net.ssl.trustStoreType=WINDOWS-ROOT`, чтобы JVM доверяла корпоративному сертификату из хранилища Windows. Прокси: `systemProp.https.proxyHost` / `proxyPort` (логин и пароль только там, не в репозитории).
- `~/.gradle/init.d/*.init.gradle.kts`: при необходимости подмена репозиториев на корпоративное зеркало Maven.

---

<details>
<summary>Автоматические тесты Хекслета</summary>

Тесты запускаются на каждый коммит. За запуск отвечает файл `.github/workflows/hexlet-check.yml` — не удаляйте и не переименовывайте ни его, ни репозиторий.

</details>

## О Хекслете

[Хекслет](https://ru.hexlet.io/) — школа программирования: авторские программы обучения с практикой, поддержкой наставников и реальными проектами, которые остаются в резюме. Этот репозиторий — один из таких проектов.
