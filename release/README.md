# CI/CD и релизы

Zip-архивы **не хранятся в git** — только исходники в `dev/`.

## Схема

```
push в main (dev/**)
    │
    ▼
 CI: сборка → проверка → артефакты Actions (14 дней)

тег v*  или  Run workflow "Release"
    │
    ▼
 Release: сборка → GitHub Releases (постоянно)
```

## Workflows

| Workflow | Когда | Результат |
|----------|-------|-----------|
| `ci.yml` | push/PR в `main` | сборка, артефакты в Actions |
| `release.yml` | тег `v*` / Run workflow | **GitHub Releases** для скачивания |

## Где взять zip

| Нужно | Где |
|-------|-----|
| Последняя сборка main | Actions → CI → Artifacts |
| Стабильная версия | [Releases](https://github.com/Yauheni-Barodzich/mindustry-mods/releases) |
| Локально | `dev\build-all.ps1` → `release/` (в .gitignore) |

## Версии

Единый источник: файл **`VERSION`** в корне репозитория.

| Когда | Что происходит |
|-------|----------------|
| push в `main` | версии **не** меняются |
| Release (тег / Run workflow) | `bump-version.ps1` обновляет `VERSION`, все `mod.hjson`, `gradle` → коммит → сборка |

Бамп **только при релизе**, не автоматически на каждый push.

## Опубликовать версию

**GitHub UI:** Actions → Release → Run workflow → `0.1.0`

**Тег:**
```bash
git tag v0.1.0
git push origin v0.1.0
```

**Локально:**
```powershell
cd dev
.\release.ps1 -Version 0.1.0
```

## Куда ставить

| Файлы | Куда |
|-------|------|
| `CLIENT-*`, `CONTENT-*` | `%AppData%/Mindustry/mods/` |
| `SERVER-*`, `CONTENT-*` | `config/mods/` на сервере |

## Имена архивов

| Префикс | Назначение |
|---------|------------|
| `CLIENT-*` | только клиент |
| `SERVER-*` | только сервер |
| `CONTENT-*` | клиент и сервер |
