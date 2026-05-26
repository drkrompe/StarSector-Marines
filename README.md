# Starsector Marines

A [Starsector](https://fractalsoftworks.com/) mod (0.98a-RC8) that adds a
ground-combat sub-game inspired by MechWarrior Mercenaries / MechCommander.
Instead of marines being anonymous cargo, the player runs a merc company:
named captains lead squads, ships ferry the team between planets, and contracts
come from faction patrons, independent brokers, pirates, and deniable covert-ops
tracks.

Each marine ops session is a full-canvas takeover — own UI pipeline, own input
routing, own rendering, no vanilla chrome in the play area. The screens grow
into their own universe over time, not retrofitted into intel slots.

**Status: pre-release / active development (v0.1.0).** Not yet playable as a
complete gameplay loop — published as a reference for modders interested in
custom UIs, ground-combat systems, 3D asset pipelines, or campaign-tier
architecture in Starsector.

## Features (so far)

- **Tactical battle simulation** — tile-based ground combat with fog of war
  (shadowcast vision), squad AI (GOAP action planner), pathfinding, weapon
  ballistics, and visual effects.
- **Unit variety** — infantry, mechs, vehicles (bicycle-model ground
  kinematics), drones, air units (helicopters/shuttles with AirBody physics),
  and turret emplacements.
- **Procedural map generation** — BSP room carving, road/path networks, tileset
  autotiling, nature overlays, and a compound/keep system for objective-based
  missions.
- **Campaign layer** — SoA-backed persistent state, contract lifecycle with
  patron houses, mission briefing/debriefing screens, and a comms-officer
  narrator surface.
- **3D asset pipeline** — a sister Gradle module that imports FBX/BVH models
  via Assimp at build time and serializes them for runtime rendering inside
  Starsector's OpenGL context.
- **Custom 2D renderer** — batched quad rendering with GL state bracketing,
  sprite animation, and particle effects, all layered on top of Starsector's
  existing graphics pipeline.

## Project layout

```
mod/                         The shippable mod directory (deployed to <Starsector>/mods/)
  mod_info.json              Manifest: id, version, modPlugin entry point
  data/                      JSON/CSV: campaign rules, sounds, tilesets, mission configs
  graphics/                  Sprites, tilesets, icons, particle atlases
  sounds/                    OGG audio: ambient, explosions, small arms, music, voice
  jars/                      Compiled fat jar (built by Gradle)
src/main/java/               Mod source (~420 classes)
  .../battle/                Tactical sim: AI, air, ground, mapgen, nav, vision, weapons, fx
  .../campaign/              Contract lifecycle, patron houses, campaign state (SoA)
  .../ops/                   Player-facing Marine Ops UI: briefing, battle, mission select
  .../marine/                Captain roster, ranks, status tracking
  .../render/                3D model rendering (scene graph, materials, animation)
  .../render2d/              2D quad batching, GL state brackets
  .../ui/                    Shared UI components (fonts, widgets, layout)
src/test/java/               JUnit 5 tests
asset-pipeline/              Sister Gradle module for 3D asset import
  src/main/java/             Runtime classes bundled into the mod jar (mesh, skeleton, animation)
  src/tool/java/             Build-time importers (Assimp, LWJGL 3) — never shipped
  src/tool/resources/        Source assets (models, audio) processed at build time
roadmap/                     Design docs, session logs, and feature-track plans
```

## Building from source

### Prerequisites

- **JDK 25** (Eclipse Adoptium) — Gradle's toolchain resolver will auto-download it.
- **Starsector 0.98a-RC8** installed locally.

### Configuration

Edit `gradle.properties` and set `starsectorDir` to your Starsector install path:

```properties
starsectorDir=C:/Program Files (x86)/Fractal Softworks/Starsector
```

### Gradle tasks

```
gradlew.bat build            # compile + test → mod/jars/StarsectorMarines.jar
gradlew.bat deployMod        # sync mod/ into <starsectorDir>/mods/StarsectorMarines/
gradlew.bat undeployMod      # remove the deployed copy
gradlew.bat runStarsector    # deploy + launch the game

# Asset pipeline (only needed if you modify 3D models or source audio)
gradlew.bat :asset-pipeline:processModels
```

The build produces a single fat jar containing the mod code, the asset-pipeline
runtime classes, JOML (3D math), and fastutil (primitive collections, shaded to
avoid classpath collisions with other mods). Starsector's own jars are
compile-only dependencies and are never bundled.

Bytecode targets Java 17 (`--release 17`) to match the Zulu 17 JRE that
Starsector ships.

## For modders

This repo may be useful as a reference for:

- **Custom full-screen UIs** — `showCustomVisualDialog` takeover pattern with
  own input routing and dismiss handling (`ops/`, `ui/`, `render2d/`).
- **Ground/tactical combat** — tile-based sim with GOAP AI, pathfinding,
  fog of war, and squad coordination (`battle/`).
- **3D rendering in Starsector** — loading and rendering 3D models inside the
  game's OpenGL 2 context (`render/`, `asset-pipeline/`).
- **Campaign persistence** — `EveryFrameScript` + Serializable POJO pattern
  for save/load round-tripping without custom xstream converters (`marine/`).
- **Gradle build setup** — multi-project build with fat-jar packaging,
  dependency shading, and game-launch tasks (`build.gradle`).
- **GL state management** — Starsector hands UI hooks a polluted GL state;
  the `GlStateBracket` pattern in `render2d/` shows how to safely bracket
  custom rendering.

## Starsector API notes

- Compile-only deps (never bundle): `starfarer.api.jar`, `starfarer_obf.jar`,
  `lwjgl.jar`, `lwjgl_util.jar`, `json.jar`, `log4j-1.2.9.jar`,
  `xstream-1.4.10.jar`, `fs.common_obf.jar` — all from `starsector-core/`.
- API sources: `starsector-core/starfarer.api.zip` — unzip for IDE attachment.
- Logging: `Global.getLogger(Class)` → log4j 1.2 `Logger`.
- ModPlugin lifecycle: `onApplicationLoad` → `onGameLoad` → frame loop.
- Faction/hull/variant data: JSON/CSV under `mod/data/`, mirroring vanilla's
  `starsector-core/data/` structure.

## License

Not yet licensed. All rights reserved until a license is added.
