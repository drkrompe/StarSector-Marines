# Building, running and shipping the mod

Status: ACTIVE

Written: 2026-10-24

The detail behind `CLAUDE.md`'s build section: deploying, launching the game
and reading why it died, packaging the release, fitted textures, the catalog
smoke tests and the Blender render queue. Read it when you run the game or
touch packaging.

## Deploy and launch

- `gradlew.bat deployMod` generates the gitignored `mod/sounds/` outputs
  (requires `ffmpeg` on `PATH`) and syncs `mod/` into
  `<starsectorDir>/mods/StarsectorMarines/`.
- `gradlew.bat runStarsector` deploys, then runs the java command line read out
  of `starsector-core/starsector.bat` rather than the .bat itself (the .bat's
  malformed last block hides the JVM's exit status).
  - The JVM's own output never reaches `starsector.log`. The task tees it to
    `build/starsector-run/console.log`, points `-XX:ErrorFile` at
    `build/starsector-run/hs_err_pid<pid>.log`, and a finalizer writes
    `build/starsector-run/run-summary.txt` with the decoded exit status, even
    when the launch task is aborted. When the game dies without explanation,
    read those before `starsector.log`; log4j buffers and can lose its last
    lines.
  - **The exit status separates the cases.** `0` means something asked the
    process to stop, so `0` with no shutdown banner means it was killed from
    outside the JVM (a `gradlew --stop` from another session looks exactly
    like this: the game is a child of the daemon; grep
    `~/.gradle/daemon/<version>/*.log` for `stop() called on daemon` at the
    matching second). `0xC0000005` is a native
    crash; `0xC000001D` (illegal instruction) points at the experimental
    `-XX:UseAVX` / vector flags in the installed `vmparams`, not at the mod.
  - `-PstockJvm` drops every `-XX:` flag the install carries, plus `-noverify`,
    keeping heap, `--enable-preview`, module opens, properties and classpath.
    The installed `vmparams` is a community performance file, not stock. Use it
    as the control for "is it the flags?", not as a recommendation.
  - `-PplayerBuild` launches without `-Dmarines.dev=true`, so every debug
    surface behind `DevConfig.DEV_MODE` is off, as on a player's install.

## Playing a battle without the game

`gradlew.bat playBattle -Pbattle=<source>` opens the real `BattleScreen` in a
window of its own, with no game booted, for a playtest in seconds rather than a
launch and a campaign (`PlayBattleMain`). It needs the install only for its
jars, natives and vanilla assets, and no `deployMod`. Windows only for now: the
game's Mac natives do not load on the Mac's JDK. It does not run
`compileTestJava`: `compilePlayBattle` builds its `Play*` classes with the test
tree as a javac source path, so only the few dozen test sources they reach
compile. A new `Play*` class or helper needs nothing more; one found only by
reflection would not compile, and fails at run time with `NoClassDefFoundError`.

- **Sources:** `HeadlessBattles.build`'s (`relief`, the default; `thunder-run`,
  `cordon`, `salvage-rights`, `cold-start`, `working-passage`, each with its
  `:key=value` options; `fixture:<path>`), or the bare name of a fixture in
  `src/test/resources/battle-fixtures`, such as `conquest-launch-v3`.
- **Controls** are the battle's own. F9 rebuilds the battle. Closing the window
  or continuing from a finished battle quits, printing the tick and the winner.
- **`-Pwindow=<w>x<h>`** changes the window from 1920x1080. A window as large as
  the desktop has no title bar. `-Dbattle.*` and `-Dmarines.*` switches are
  forwarded, so `-Dmarines.dev=true` gives the developer HUD.
- **It plays as the game plays:** the asynchronous defaults, not the evidence
  tasks' serial switches, and the player commands the company as in any played
  battle (`mission-command-nouns.md`).
- **`-PplayScript=<file>`** drives a run unattended and takes screenshots into
  `build/play-battle`; the format is on `PlayBattleMain`.

- **Sound** plays through OpenAL (`PlayBattleSound`), the game's own native and
  Vorbis library, from the clips `:asset-pipeline:processAudio` generates, so it
  needs `ffmpeg` as `deployMod` does. `-Pvolume=<0..1>` sets the level; `-Psilent`
  turns it off and needs neither. The run's last line counts what played and
  names any id with no clip. **Distance roll-off is an approximation:** the
  game's engine is obfuscated, so judge what plays and when here, and final
  levels in the game. To check output without speakers, point OpenAL Soft's
  `ALSOFT_CONF` at a config with `drivers = wave` and a `[wave]` `file`, and set
  `ALSOFT_DRIVERS=wave`: the run writes a WAV of exactly what it mixed.

**Not there yet:** any campaign state (nothing settles, and a story retry is not
offered), and vanilla combat (`combathybrid`). **It cannot replace a run in the
game:** the game hands the screen a polluted GL state and refuses file access to
shipped code, and neither happens here, so sign a change off with
`runStarsector`, and `verifyScriptSandbox` when it touches I/O.

## Release

`gradlew.bat packageRelease` builds `build/release/StarsectorMarines-<version>.zip`:
one `StarsectorMarines/` folder holding `mod/` without its generated folders,
the shaded jar (after `verifyModJarBoundary`) and sounds transcoded fresh, so
stale clips cannot ship. It fails when `mod_info.json` and `build.gradle`
disagree on the version. See `v0.1-public-build.md`.

## Fitted textures

Shipped textures are fitted to how they are drawn (`texture-fit.md`).
`optimizePngs`, fed to `packageRelease` and `deployMod`, downscales each image
to the largest size the usage record `art-source/texture-usage/usage.json` says
it is drawn at, keeps unmeasured images whole, and reports to
`build/reports/texture-fit/report.md`; `-PfitTextures=false` deploys the
sources. **Load every mod texture through `ModTextures`** (`sprite`,
`loadTexture`, `readImage`), never `Global.getSettings().getSprite` or
`openStream` directly: code addresses images in source pixels, and only
`ModTextures` maps a fitted image back to them. Grow the record with
`-PtextureUsage` on `createSnapshots` or `commanderEvidence` (plus a dev-mode
game), then `mergeTextureUsage`.

## Terrain companions

`gradlew.bat :asset-pipeline:deriveTileMaps` bakes `<sheet>_height.png` /
`<sheet>_normal.png` beside each terrain albedo listed in
`asset-pipeline/src/tool/resources/tilemaps/tilemaps.json`. **Re-run it after
re-exporting any tileset that has those companions**: they are sampled at the
albedo's frame coordinates, so a re-packed atlas leaves them silently reading
the old sheet's relief. `UrbanTileset3AlphaTest` fails when they drift.

## Catalog smoke tests

- `gradlew.bat prepareCatalogSmoke` stages the two-provider catalog acceptance
  fixture without launching Starsector; `-PcatalogSmokeMode=collision` stages
  the duplicate-id variant.
- `gradlew.bat catalogSmokeLive` launches the installed game against the staged
  fixture, with mods, saves, screenshots and logs redirected under
  `build/catalog-smoke/`, captures application-load evidence, and terminates
  only its own process tree. Use it only when a live acceptance pass is
  intentionally required.

## Kit Ladder page

`tools/kits/` builds the owner's Kit Ladder page from the game data and carries
the owner's edits back: `export_state.py` (diff report and weapon notes from an
ArtifactData export), `apply_ladder.py` (accepted stations and card contents into the
Kit Ladder's data files),
`check.py` (validation, about five seconds; `--self-test` for the tools
themselves), `balance.py` (the cheap balance evidence in one markdown) and
`refresh.py` (the local steps of a page refresh). When to run which, and the
Artifact-tool steps between them: `kit-ladder.md`.

## Blender render queue

Blender renders go through the render queue, not a session's own shell:
`python tools/render_queue.py submit job.json --commit HEAD`, then
`status [--job <id>]`. The queue is `render-queue/` on the shared Drive. A
runner on each machine runs one job at a time and swaps finals out at a frame
boundary for review and light jobs. Gated lanes run only while that machine's
gate is `run`; the `light` lane (half size, at most 16 samples) is not gated.
The gate belongs to the render coordinator role, which changes it only on the
owner's word. Job format and collecting output: the `render-queue` skill.
