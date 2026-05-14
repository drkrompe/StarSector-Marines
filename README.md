# Starsector Marines

A mod for [Starsector](https://fractalsoftworks.com/) (built against 0.98a-RC8).

## Layout

```
build.gradle             Java compile + deploy pipeline
gradle.properties        starsectorDir override
mod/                     The actual mod directory that gets copied into <Starsector>/mods/
  mod_info.json          Manifest (id, version, modPlugin entry point)
  data/                  Vanilla data overrides and mod-specific JSON/CSV files
  graphics/              Sprites and other image assets
  sounds/                Sound files
  jars/                  Compiled mod jar lands here (declared in mod_info.json)
src/main/java/           Mod source (compiles into mod/jars/StarsectorMarines.jar)
src/test/java/           JUnit 5 tests
```

## Build

Gradle uses a JDK 25 toolchain (Adoptium) and emits Java 17 bytecode so the jar runs
on the Zulu 17 JRE that Starsector ships.

```
gradlew.bat build            # compile + run tests + produce mod/jars/StarsectorMarines.jar
gradlew.bat deployMod        # copy the mod/ folder into <Starsector>/mods/StarsectorMarines/
gradlew.bat undeployMod      # remove the deployed copy
gradlew.bat runStarsector    # deployMod, then launch the game via starsector-core/starsector.bat
```

The Starsector install path is read from `gradle.properties` (`starsectorDir`). Override
that property if you move the game.

## Adding content

- **Java behavior** (mod plugins, scripts, combat AI, hullmods, ship systems, weapons, campaign listeners): add classes under `src/main/java/com/dillon/starsectormarines/...` and wire them up from `StarsectorMarinesModPlugin`.
- **Data** (ship hulls, variants, weapons, factions, market conditions, commodities): JSON/CSV under `mod/data/`, mirroring the structure of `<Starsector>/starsector-core/data/`.
- **Art**: PNG/WebP under `mod/graphics/`.
- **Sound**: OGG under `mod/sounds/` plus an entry in `mod/data/config/sounds.json`.
