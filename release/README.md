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
| Release (тег / Run workflow) | авто **+1 patch** (или minor/major), обновляет `VERSION` + все `mod.hjson` |

Бамп **только при релизе**, не на каждый push.

### Автоинкремент

| Способ | Результат |
|--------|-----------|
| `.\release.ps1` | `1.0.0` → `1.0.1` |
| `.\release.ps1 -Increment minor` | `1.0.0` → `1.1.0` |
| `.\release.ps1 -Version 2.0.0` | явная версия |
| Actions → Release → version **пусто** | +1 patch |
| Actions → Release → bump **minor/major** | соответственно |

## Опубликовать версию

**GitHub UI:** Actions → Release → Run workflow (version пусто = +1 patch)

**Локально:**
```powershell
cd dev
.\release.ps1              # 1.0.0 -> 1.0.1
.\release.ps1 -Increment minor
.\release.ps1 -Version 2.0.0 # явно
```

**Тег вручную:**
```bash
git tag v1.0.1
git push origin v1.0.1
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
