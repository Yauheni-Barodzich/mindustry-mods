# CI/CD и сборка

См. workflow в `.github/workflows/`.

```powershell
cd dev
.\build-all.ps1    # локальная сборка
.\release.ps1      # релиз (+1 patch)
```

Версия в `VERSION`. Zip публикуются в [Releases](https://github.com/Yauheni-Barodzich/mindustry-mods/releases), в git не хранятся.
