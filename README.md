# mindustry-mods

Моды и плагины Mindustry v159+ для своего сервера.

## Архивы

| Файл | Куда |
|------|------|
| `CLIENT-sync-content.zip` | клиент `mods/` |
| `CLIENT-admin.zip` | клиент `mods/` |
| `SERVER-sync-content.zip` | сервер `config/mods/` |
| `SERVER-admin.zip` | сервер `config/mods/` |
| `CONTENT-dune-start.zip` | **клиент и сервер** |

Префикс: `CLIENT-` — только клиент, `SERVER-` — только сервер, `CONTENT-` — везде.  
**Не кладите `SERVER-*` на клиент** — игра упадёт.

Скачать: [Releases](https://github.com/Yauheni-Barodzich/mindustry-mods/releases)

## Разработка

```powershell
cd dev
.\build-all.ps1    # локальная сборка → release/
```

Исходники: `dev/dune-start`, `dev/server-content-sync` (JDK 17).

## Релиз

```powershell
cd dev
.\release.ps1                 # 1.0.0 → 1.0.1 (patch)
.\release.ps1 -Increment minor
```

Или GitHub → **Actions → Release → Run workflow** (version пусто = +1 patch).

Версия в `VERSION`, при релизе обновляются все `mod.hjson`. Zip в git не хранятся — только в Releases.

## CI/CD

| Workflow | Когда |
|----------|-------|
| CI | push/PR → сборка, артефакты 14 дней |
| Release | тег `v*` / Run workflow → GitHub Release |

## Карты

`maps/` — карты сервера (например `DUNE.msav`).
