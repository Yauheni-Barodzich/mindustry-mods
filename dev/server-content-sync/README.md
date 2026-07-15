# Синхронизация и админка (unified)

Один артефакт `sync-admin.zip`: sync + admin, server + client.

```bash
gradlew jar
# → unified/build/libs/sync-admin.zip
```

Структура:

```
unified/
├── plugin.hjson, mod.hjson
├── bundles/
├── assets/
└── src/scs/
    ├── ScsPlugin.java, ScsClient.java
    ├── plugin/          # sync server
    ├── client/          # sync UI
    └── admin/
        ├── plugin/      # admin server
        └── client/      # admin UI
```

Общая документация — [README.md](../../README.md).
