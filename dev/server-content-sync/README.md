# Server Content Sync

Синхронизация **модов** и **карт** со своего Mindustry-сервера (v159+).

## Артефакты

После `gradlew :plugin:jar :client:jar`:

| Файл | Куда |
|------|------|
| `plugin/build/libs/server-content-sync-plugin.jar` | сервер: `config/mods/` |
| `client/build/libs/server-content-sync-client.jar` | клиент: `%AppData%/Mindustry/mods/` |

## Сервер

1. Положи `server-content-sync-plugin.jar` в `config/mods/`.
2. Контент-моды (zip/jar) — туда же; карты — в `config/maps/`.
3. Открой в firewall порт **`gamePort + 1`** (для 6567 → **6568**), либо задай свой в `config/mods/server-content-sync/config.hjson`:

```hjson
enabled: true
bind: "0.0.0.0"
port: 0
```

`port: 0` = автоматически `gamePort + 1`.

4. Рестарт сервера. В консоли:
   - `sync-status` — порт и число файлов
   - `sync-reload` — пересканировать mods/maps и перезапустить HTTP

Проверка: `http://SERVER_IP:6568/api/v1/info` и `/api/v1/manifest`.

В манифест **не** попадают: hidden-моды, плагины (`plugin.hjson`), сам sync.

## Клиент

1. Положи `server-content-sync-client.jar` в `mods/` (мод `hidden`, mismatch не вызывает).
2. Multiplayer → **Content Sync** (или Settings → Server Sync).
3. Укажи адрес сервера `host:6567` → **Auto URL** даст `http://host:6568`.
4. **Fetch** → список missing/outdated → **Download missing**.
5. Если скачались моды — подтверди **Restart**, затем заходи на сервер.

## API

- `GET /api/v1/info`
- `GET /api/v1/manifest`
- `GET /api/v1/file/mods/{fileName}`
- `GET /api/v1/file/maps/{fileName}`

Файлы проверяются по SHA-256 после скачивания.

## Сборка

Нужен JDK 17+.

```bat
gradlew.bat :plugin:jar :client:jar :admin-plugin:jar :admin-client:jar
```

---

# Server Admin

Удалённое управление **своим** сервером (v159+): статус, рестарт, моды, карты, конфиги.

## Пароль (только на сервере)

Файл **`config/mods/server-admin/admin.password`** — задаётся **только через FTP/SSH**, по API **не отдаётся**.

Первая строка без `#` — пароль. Пример:

```
# my secret
MyStrongPassword123
```

## Артефакты

| Файл | Куда |
|------|------|
| `admin-plugin/build/libs/server-admin-plugin.jar` | сервер: `config/mods/` |
| `admin-client/build/libs/server-admin-client.jar` | клиент: `%AppData%/Mindustry/mods/` |

## Сервер

1. JAR в `config/mods/`, пароль в `admin.password`.
2. Firewall: порт **`gamePort + 2`** (6567 → **6569**).
3. Конфиг: `config/mods/server-admin/config.hjson` (`port: 0` = auto).
4. Рестарт → `admin-status` в консоли.

Рестарт через API вызывает `Core.app.exit()` — нужен **systemd/supervisor** с автоперезапуском.

## Клиент

Join → **Server Admin** (или Settings → Server Admin).

1. Адрес `host:6567` → Auto URL → `http://host:6569`
2. Пароль (из FTP-файла на сервере) → **Login**
3. Status / Restart / Mods / Maps / Config

Пароль **не пишется в settings**. Для быстрого входа скопируй тот же файл на клиент:

`%AppData%/Mindustry/mods/server-admin/admin.password`

При открытии диалога, если файл есть — **Quick login** / авто-логин. Пустое поле Password тоже берёт пароль из этого файла.

## Rules (`config/rules.hjson`)

В админке секция **Server Rules**:
- чекбоксы / числа по группам (Mode, Combat, Logic, Units…)
- **Load / Save** → файл на сервере
- **Apply live** → применить к текущей игре без рестарта
- **Reset defaults** → шаблон с дефолтами из `Rules.java` (ваши оверрайды в шаблоне: `reactorExplosions/logicUnitBuild/logicUnitDeconstruct = false`)

## API (Bearer token после POST /login)

- `POST /api/v1/admin/login` — `{"password":"..."}`
- `GET /api/v1/admin/status`
- `POST /api/v1/admin/restart`
- `GET|POST /api/v1/admin/mods` — список / upload (`?name=file.zip`)
- `DELETE /api/v1/admin/mods/{name}`
- `GET|POST /api/v1/admin/maps` — аналогично
- `DELETE /api/v1/admin/maps/{name}`
- `GET /api/v1/admin/configs` — список текстовых конфигов
- `GET|PUT /api/v1/admin/configs/{path}` — чтение/запись (admin.password недоступен)

