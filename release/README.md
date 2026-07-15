# CI/CD через GitHub Actions

## Схема

```
push в main (dev/**)
    │
    ▼
┌─────────┐     ┌──────────────┐
│   CI    │────▶│ sync-release │──▶ коммит release/ в main [skip ci]
│ build   │     └──────────────┘
└─────────┘

push тега v*  или  Run workflow "Release"
    │
    ▼
┌──────────┐
│ Release  │──▶ GitHub Releases (6 zip для скачивания)
│ build+CD │
└──────────┘
```

## Workflows

| Файл | Когда | Что делает |
|------|-------|------------|
| `.github/workflows/ci.yml` | push/PR в `main` при изменении `dev/**` | Сборка, проверка zip, артефакты |
| `.github/workflows/ci.yml` → `sync-release` | push в `main` | Автокоммит `release/` ботом |
| `.github/workflows/release.yml` | тег `v*` или Run workflow | Публикация **GitHub Release** |

## Полностью через Actions (без локальной сборки)

### Шаг 1 — CI (автоматически)

Пушите код в `main` → Actions сам:
1. Соберёт моды
2. Обновит `release/` в репозитории

Ничего запускать не нужно.

### Шаг 2 — CD (публикация Release)

**Вариант A — из GitHub UI:**
1. [Actions → Release](https://github.com/Yauheni-Barodzich/mindustry-mods/actions/workflows/release.yml)
2. **Run workflow** → версия `0.1.0`
3. Появится [Release](https://github.com/Yauheni-Barodzich/mindustry-mods/releases) с zip

**Вариант B — тег:**
```bash
git tag v0.1.0
git push origin v0.1.0
```

**Вариант C — локально одной командой:**
```powershell
cd dev
.\release.ps1 -Version 0.1.0
```

## Куда ставить скачанные файлы

| Файлы | Куда |
|-------|------|
| `CLIENT-*`, `CONTENT-*` | `%AppData%/Mindustry/mods/` |
| `SERVER-*`, `CONTENT-*` | `config/mods/` на сервере |

## Локальная сборка (опционально)

```powershell
cd dev
.\build-all.ps1
```

## Packages

Не используются — только **Releases**.
