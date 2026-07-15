# Релизные архивы модов

## Автоматический релиз (рекомендуется)

Одна команда — сборка, коммит `release/`, тег, публикация на GitHub:

```powershell
cd dev
.\release.ps1 -Version 0.1.0
```

Что происходит:
1. `build-all.ps1` собирает zip в `release/`
2. `git commit` + `push` в `main`
3. `git tag v0.1.0` + `push` тега
4. **GitHub Actions** (`.github/workflows/release.yml`) пересобирает моды и создаёт **Release** с 6 zip

Статус: [Actions](https://github.com/Yauheni-Barodzich/mindustry-mods/actions) → релиз: [Releases](https://github.com/Yauheni-Barodzich/mindustry-mods/releases)

## Без локального git — только GitHub

1. Репозиторий → **Actions** → **Release** → **Run workflow**
2. Ввести версию, например `0.1.0`
3. Workflow соберёт и опубликует Release

## Packages не нужны

Для zip-модов Mindustry используйте только **Releases**, не Packages.

## Куда ставить файлы

| Файлы из Release | Куда |
|------------------|------|
| `CLIENT-*`, `CONTENT-*` | `%AppData%/Mindustry/mods/` |
| `SERVER-*`, `CONTENT-*` | `config/mods/` на сервере |

## Только локальная сборка

```powershell
cd dev
.\build-all.ps1
```

Обновляет `release/` и копирует в `mods/` / `server-mods/` для игры.
