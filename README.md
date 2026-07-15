# mindustry-mods

Моды для Mindustry v159+.

Скачать: [Releases](https://github.com/Yauheni-Barodzich/mindustry-mods/releases)

## Установка

| Архив | Куда |
|-------|------|
| `*.zip` (моды) | `%AppData%/Mindustry/mods/` и `config/mods/` на сервере |

Оба пакета ставятся одинаково на клиент и сервер.

## Моды

### dune-start — Дюна: Старт

Игровой контент в духе Дюны: ранняя добыча и техника.

- **Гараж вездеходов** — завод без энергии, 50 меди
- **Вездеход-сборщик** — добыча руды, трюм 120, лимит 3 на команду

Клиент и сервер должны иметь одинаковую версию.

### sync-admin — Синхронизация и админка

Единый пакет: синхронизация модов/карт и удалённое управление сервером.

**Синхронизация:** сервер раздаёт моды и карты по HTTP (`gamePort + 1`, 6567 → 6568).  
Клиент: Multiplayer → **Content Sync** → адрес `host:6567` → Fetch → Download.

**Админка:** статус, рестарт, моды, карты, правила (`gamePort + 2`, 6567 → 6569).  
Пароль: `config/mods/server-admin/admin.password` (только FTP/SSH).  
Клиент: **Server Admin** → адрес сервера → пароль.

Конфиги sync и admin по-прежнему в `config/mods/server-content-sync/` и `config/mods/server-admin/`.

## Минимальный набор

**Сервер** `config/mods/` и **клиент** `mods/` — одинаково:

```
sync-admin.zip
dune-start.zip
```

## Карты

`DUNE.msav` — кладётся на сервер в `config/maps/`, клиент подтянет через sync.

## Сборка

```bat
cd dev
build-all.bat       :: → dist\sync-admin.zip, dist\dune-start.zip
release.bat         :: патч-релиз (+ тег GitHub)
```

Аргументы те же, что у `.ps1` (например `release.bat -Increment minor`).

Версия в `VERSION`. Zip публикуются в [Releases](https://github.com/Yauheni-Barodzich/mindustry-mods/releases), в git не хранятся.
