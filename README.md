# mindustry-mods

Моды для Mindustry v159+.

Скачать: [Releases](https://github.com/Yauheni-Barodzich/mindustry-mods/releases)

## Установка

| Архив | Куда |
|-------|------|
| `CLIENT-*.zip`, `CONTENT-*.zip` | `%AppData%/Mindustry/mods/` |
| `SERVER-*.zip`, `CONTENT-*.zip` | `config/mods/` на сервере |

`SERVER-*` — **только сервер**. На клиент не ставить.

## Моды

### CONTENT-dune-start — Дюна: Старт

Игровой контент в духе Дюны: ранняя добыча и техника.

- **Гараж вездеходов** — завод без энергии, 50 меди
- **Вездеход-сборщик** — добыча руды, трюм 120, лимит 3 на команду

Клиент и сервер должны иметь одинаковую версию.

### SERVER-sync-content + CLIENT-sync-content — Синхронизация

Сервер раздаёт моды и карты по HTTP; клиент скачивает недостающее перед заходом.

**Сервер:** `SERVER-sync-content.zip`, firewall порт `gamePort + 1` (6567 → 6568).

**Клиент:** `CLIENT-sync-content.zip` → Multiplayer → **Content Sync** → адрес `host:6567` → Fetch → Download.

### SERVER-admin + CLIENT-admin — Админка

Удалённое управление сервером: статус, рестарт, моды, карты, правила.

**Сервер:** `SERVER-admin.zip`, пароль в `config/mods/server-admin/admin.password` (только FTP/SSH), firewall `gamePort + 2` (6567 → 6569).

**Клиент:** `CLIENT-admin.zip` → **Server Admin** → адрес сервера → пароль.

## Минимальный набор

**Сервер** `config/mods/`:
```
SERVER-sync-content.zip
SERVER-admin.zip
CONTENT-dune-start.zip
```

**Клиент** `mods/`:
```
CLIENT-sync-content.zip
CLIENT-admin.zip
CONTENT-dune-start.zip
```

## Карты

`DUNE.msav` — кладётся на сервер в `config/maps/`, клиент подтянет через sync.
