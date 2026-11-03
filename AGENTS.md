# AGENTS.md — Краткая инструкция для AI-агентов

Учебный сервис бронирования встреч «Календарь звонков» (Calendly-like). Репозиторий: https://github.com/UlanNSA/ai-for-developers-project-386.

## Архитектура и структура

- **Контракт API**: `api/*.tsp` (TypeSpec) → `api/openapi/openapi.yaml` (OpenAPI 3.0.0, committed) → `frontend/src/api/generated/` (SDK, gitignored).
- **Backend**: Kotlin/Ktor в `backend/`, реализует контракт вручную (без codegen). Конфигурация из env vars.
- **Frontend**: React/TypeScript/Vite в `frontend/`.
- **E2E**: Playwright в `e2e/`.
- **Документация**: `README.md`, `docs/domain-rules.md`, `docs/adr/`, `GLOSSARY.md`.

Подробности см. в `README.md` и `CLAUDE.md` (Claude-специфичные указания).

## Команды

```bash
mise run check:fast     # всё кроме E2E: формат TypeSpec, дрейф контракта, SDK, lint, тесты, сборки
mise run check          # check:fast + E2E
mise run api:update     # пересобрать api/openapi/openapi.yaml (коммитить результат вместе с .tsp)
mise run dev:backend    # Ktor на :8080
mise run dev:frontend   # Vite на :5173
```

## Правила коммитов

- Conventional Commits (`feat`, `fix`, `docs`, `chore`, `ci`, `test`, `refactor`) с областью (`api`, `backend`, `frontend`, `e2e`).
- **Каждый коммит должен ссылаться на GitHub Issue** в теле или заголовке: `Closes #N` или `Refs #N`.
- Не смешивай тикеты в одном коммите.

## Запреты

- Не редактировать `frontend/src/api/generated/` вручную — перегенерировать через `sdk:generate`.
- Не редактировать, не удалять и не переименовывать `.github/workflows/hexlet-check.yml`.
- Не коммитить реальные ключи, токены, сертификаты.

## Подробная документация

- `README.md` — установка, команды, Docker/Render, корпоративный прокси.
- `CLAUDE.md` — Claude-специфичные указания.
- `docs/domain-rules.md` — правила предметной области.
- `docs/adr/` — архитектурные решения.
- `GLOSSARY.md` — терминология.
