# Project: Starsector Marines

A Starsector mod (game version 0.98a-RC8). Source-of-truth game install is at
`C:\Program Files (x86)\Fractal Softworks\Starsector` — read-only reference, never edit.

For project vision, current focus, and immediate next-up, see
[`roadmap/`](roadmap/). Read `roadmap/README.md` first; each feature
directory has its own design doc and `next-session.md` for handoff state.

## Build & deploy

- Toolchain: Eclipse Adoptium JDK 25 (registered via Gradle's auto-detected toolchain).
- Bytecode target: Java 17 (`--release 17`). The game ships Zulu 17.0.10 + `--enable-preview`,
  so do NOT use language features newer than Java 17, and do NOT rely on preview features
  at compile time.
- `gradlew.bat build` → `mod/jars/StarsectorMarines.jar` (directly into the mod folder; no
  intermediate copy step).
- `gradlew.bat deployMod` → syncs `mod/` into `<starsectorDir>/mods/StarsectorMarines/`.
- `gradlew.bat runStarsector` → deploys then launches via `starsector-core/starsector.bat`.

## Mod layout

The `mod/` folder in this repo is what ships. `mod_info.json` lists the jar at
`jars/StarsectorMarines.jar`. The `modPlugin` entry point is
`com.dillon.starsectormarines.StarsectorMarinesModPlugin`.

## Starsector API conventions

- Compile-only deps (never bundle into the jar): `starfarer.api.jar`, `starfarer_obf.jar`,
  `lwjgl.jar`, `lwjgl_util.jar`, `json.jar`, `log4j-1.2.9.jar`, `xstream-1.4.10.jar`,
  `fs.common_obf.jar` — all live in `<starsectorDir>/starsector-core/`.
- API sources are in `<starsectorDir>/starsector-core/starfarer.api.zip` — unzip locally
  for IDE attachment, do not check in.
- Logging: `Global.getLogger(Class)` returns a log4j 1.2 `Logger`. Game logs to
  `<starsectorDir>/starsector-core/starsector.log`.
- The `BaseModPlugin` lifecycle: `onApplicationLoad` (once at game start, before any save),
  `onNewGame`/`onNewGameAfterEconomyLoad`/`onNewGameAfterTimePass`, `onGameLoad(newGame)`
  (every load), `beforeGameSave`/`afterGameSave`.
- Faction definitions: `mod/data/world/factions/<id>.faction` (JSON despite the extension).
- Hulls/variants: `mod/data/hulls/`, `mod/data/variants/` mirroring vanilla.
- Strings (for i18n): `mod/data/strings/strings.json`.

## Doc-driven development

Feature directories under `roadmap/` follow this layout:

```
roadmap/<feature>/
  overview.md        — concept, scope, cross-refs to related systems
  stories/           — active story/slice docs (one per story)
  complete/          — shipped stories move here (commit hash, what landed)
  next-session.md    — handoff state for picking up cold
  *.md               — other feature-specific docs (options analysis, etc.)
```

- Before implementing a feature, ensure `overview.md` exists with the concept
  and decomposition into stories.
- As stories ship, move them from `stories/` to `complete/` with
  shipped-with-details (commit hash, what actually landed vs. planned).
- **Update docs at commit boundaries.** When committing a story or slice,
  update `next-session.md` (state of play, commit chain, strike-through
  shipped stories) and log shipped work in `complete/` in the same commit
  or immediately after. Don't accumulate doc debt across multiple commits.
- Keep `roadmap/README.md` current focus and immediate next-up sections honest —
  if priorities shifted, say so.
- Existing feature dirs are migrated incrementally as they're touched.

## Conventions for this repo

- Package root: `com.dillon.starsectormarines`.
- Mod ID: `starsector_marines` (snake_case is the Starsector convention).
- Version in `mod_info.json` and `build.gradle` should match.
- Do not edit anything under `C:\Program Files (x86)\Fractal Softworks\Starsector` — it's
  read-only reference. Vanilla files there are the canonical examples for data schemas.

## Code style

- **Prefer `import` + simple name over inline fully-qualified names** in code
  you write or edit (`Vehicle v`, not
  `com.dillon.starsectormarines.battle.vehicle.Vehicle v`). Applies to new code;
  not enforced retroactively — don't do sweeping FQN→import refactors of
  existing files unasked, and a mechanical package-move rewrite that preserves a
  file's pre-existing FQN style is fine. Javadoc `{@link}` FQN is fine — no need
  to add an import used *solely* for a doc link.

## Committing (concurrent sessions share this tree)

Several Claude sessions run in parallel against the same working tree, index,
and HEAD. Commit loop:

1. Stage explicit paths only — `git add <path> …`, never `git add -A`/`.`.
2. Commit with the same pathspec — `git commit -- <path> …`, never a bare
   `git commit` (a bare commit records the whole index, sweeping in files
   another session staged).
3. Never `git stash` — it hides other sessions' in-flight work.

A stray file or mixed hunks from a parallel session are fine — leave them
rather than rewriting shared history to extract them.

## Your PWD is C:/Users/Dillon/IdeaProjects/starsectormarines
Note that unless you `cd` to a different directory your primary directory will already be:
```sh
cd "C:/Users/Dillon/IdeaProjects/starsectormarines";
```
- 99% of the time, there is no need to cd to the project root for things like git or reading files.

## Multi-project layout

- `:` (root) — the mod itself. `src/main/java` holds `StarsectorMarinesModPlugin`, the
  bridge intel plugin, the scene-graph renderer.
- `:asset-pipeline` — vendored copy of MoonLight Engine's asset code. Two source sets:
    - `main` (runtime) — MeshData, LoadedModel, MaterialInfo, Animation, Skeleton, Bone,
      BvhParser, AnimationRetargeter, ModelSerializer. **Bundled into the mod jar via
      fat-jar.** Depends only on JOML at runtime. No Lombok, no Log4j 2, no LWJGL 3.
    - `tool` (build-time importer) — ModelLoader, MeshExtractor, MaterialExtractor,
      AnimationExtractor, BoneRemapConfig, ProcessModelsTask, ConventionNormalizer,
      AssetConventionConfig. Uses Assimp + LWJGL 3 + Log4j 2. **Never ships.**
      Invoke via `gradlew :asset-pipeline:processModels`.
- The mod's `jar` task pulls in `:asset-pipeline:main` outputs + JOML via the
  runtime classpath, producing a single fat jar at `mod/jars/StarsectorMarines.jar`.
