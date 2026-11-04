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

`gradlew.bat playBattle` opens the mod's real screens in a window of their own,
with no game booted, for a playtest in seconds rather than a launch and a campaign
(`PlayBattleMain`). It hosts `MarineOpsPanelPlugin` itself, over a stand-in campaign
(`PlayCompany`, through `CampaignBackend`): a veteran company aboard the converted
Second Wind, docked at Jangala to begin with, with stores that do not run out, 10 million credits
and the cheats on. It starts on the mission board, as the game does at a planet, and
leaving a room comes back to it. Leaving the board opens a world menu (`PlayWorldMenu`,
standing in for the campaign map) of the story's worlds in Corvus and Arcadia and who
has work on each; picking one docks there and opens a fresh dialog, and Quit is there.
Zoey Venn's story contracts run on the company itself, through settlement, rewards and
the next chapter. Debug
Missions opens the catalog with every mission unlocked, on disposable forces.
`-Pbattle=<source>` jumps straight into one battle instead. It needs the install
only for its jars, natives
and vanilla assets, and no `deployMod`. Windows only for now: the game's Mac
natives do not load on the Mac's JDK. It does not run
`compileTestJava`: `compilePlayBattle` builds its `Play*` classes with the test
tree as a javac source path, so only the few dozen test sources they reach
compile. A new `Play*` class or helper needs nothing more; one found only by
reflection would not compile, and fails at run time with `NoClassDefFoundError`.

- **`-Pbattle` sources:** `HeadlessBattles.build`'s (`relief`, `thunder-run`,
  `cordon`, `salvage-rights`, `cold-start`, `working-passage`, each with its
  `:key=value` options; `fixture:<path>`), or the bare name of a fixture in
  `src/test/resources/battle-fixtures`, such as `conquest-launch-v3`.
- **Controls** are the screens' own. In a catalog mission F9 leaves the battle for
  the catalog with nothing settled; with `-Pbattle` it rebuilds the battle, and
  continuing from a finished battle quits, printing the tick and the winner.
  Closing the window always quits.
- **Saves:** every arrival at the mission board checkpoints `auto`, and one save per
  story mission on offer, named for it and docked where it is offered, under
  `~/.starsector-marines/play-saves` (`PlaySaves`). `-PplayLoad=auto` picks up where
  you stopped; `-PplayLoad=the-dust-ledger` opens the board just before that mission,
  so a tweak can be played again in seconds. The world menu lists them and has Save
  here; scripts have `save <name>` and `load <name>`. They are XML through XStream, as
  the game's saves are: an element a class no longer has is skipped and a new field
  stays unset, so a save outlives a tweak, as long as the code copes with that field
  being unset as it must on a real load.
- **Save profiles:** a run writes only to its own profile, so a session's test cannot
  move where the owner stopped. A run opened by hand writes to the owner's, the directory
  above; a run with `-PplayScript` writes to the script profile, its `script` folder, and
  also reads the owner's saves, so `-PplayLoad=thunder-run` starts from either and changes
  neither of the owner's. `-PplayProfile=owner` or `script` overrides that, which is how
  the owner opens saves a scripted walk left, and how a session opening a window by hand
  stays out of the owner's.
- **The company** is a veteran one under its founding field commander, Morgan Hale, with
  the officer lesson behind it; a save from before Hale was added gets Hale on load.
- **What is not there:** stationing and a colony's screens land on HQ. The worlds have
  nothing on them the mission board does not read, and there is no flight between them. The clock stands
  at day zero and nothing saves: story progress holds for the run and a run starts
  over. What else the rooms cannot yet read waits on `campaign-backend.md`.
- **`-Pwindow=<w>x<h>`** changes the window from 1920x1080. A window as large as
  the desktop has no title bar. `-Dbattle.*` and `-Dmarines.*` switches are
  forwarded, so `-Dmarines.dev=true` gives the developer HUD.
- **It plays as the game plays:** the asynchronous defaults, not the evidence
  tasks' serial switches, and the player commands the company as in any played
  battle (`mission-command-nouns.md`).
- **`-PplayScript=<file>`** drives a run unattended and takes screenshots into
  `build/play-battle`; the format is on `PlayBattleMain`. Prefer `press <id>`, a
  control by its markup id, to pixel clicks: it waits for the control, respects
  modals and fails naming what could be pressed. `ids` prints that list.
  Canvas controls, such as a battle's retreat and cheat keys, are still clicks.
- **`-PplayUiScale=<scale>`** runs the window at a Starsector UI scale (in PowerShell,
  quote it: `"-PplayUiScale=1.5"`). The script step `audit <label>` reports every
  clickable control cut off by the page or a non-scrolling clip, and every label wider or taller
  than its box, so a screen can be
  checked at 125%, 133% and 150% without reading frames.
- **`-PplayServer[=<port>]`** opens a live diagnosis server, so a Claude session can
  question the battle while the owner plays it (`PlayDiagnosis`). See below. Off by
  default: an ordinary playtest opens no port.

### Asking a played battle questions

`gradlew.bat playBattle -PplayServer` serves MCP over HTTP at
`http://127.0.0.1:47811/mcp`, on the loopback address only, and prints that URL. A
second window on the same port fails at once; `-PplayServer=<port>` picks another.
A session calls it through the `play-battle` skill's shell scripts, which need no
connection and no restart whenever the window opens:

```bash
.agents/skills/play-battle/scripts/play-query.sh "why 4"
```

An MCP client can connect too, though it connects as it starts, so the window has to be
open first:

```bash
claude mcp add --transport http --scope user play-battle http://127.0.0.1:47811/mcp
```

- **`play_status`**: the screen, the world, what can be pressed, the battle's tick and
  seconds, frames shown and the slowest call so far.
- **`battle_query`**: `commands`, lines of `BattleProbe`'s command language, the same
  surface as `battleProbe` (`evidence-tasks.md`), its reads only. Start with `squads`,
  then `why <squad>`: who owns the squad, its directive with reason and disposition,
  the commander's phase, the goal, the plan step by step and each member. `command`
  is every side's mission command and the objectives; `units [label=] [squad=]` every
  body's position, health, goal and order. Commands sent together see one tick.
- **`battle_pause`, `battle_resume [speed]`, `battle_run_to tick|by|beat [speed]`**, the
  writes to the clock: they press the battle's own time buttons, as the player would, so
  the HUD shows them and the player's hand wins (`PlayRunTo`). A run stops on the exact
  tick: it pauses up to 8 ticks short, since a frame can run that many, and steps the
  rest, which show no effects. A beat stops the frame it fired in. It only goes forward,
  and the asynchronous defaults do not repeat, so to see one moment again exactly use
  `battleProbe`.
- **`battle_hitlog`**: switches the hit recorder that `hits` and `hits-by` read. An
  observer; it changes nothing in the battle.

There is no order: mission design asks, the player plays. Stepping commands in
`battle_query` (`advance`, `until`, `watch`, `topology` and the rest) are refused, since a
frame has no room for them; `BattleProbe` names the reads, so a new command stays refused
until it is listed.

Reads run on the host's own frame, between `advance` and `render`, on the thread that
runs every tick; the HTTP thread only queues one and waits, up to 5 s. There is one HTTP
thread, so a frame answers at most one call. Host and Origin are checked against
loopback, so a page in a browser cannot reach the port. The server is in the test tree
with a JDK HTTP server and `:layer-authoring`'s `McpServer`, and never ships.

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
offered). **It cannot replace a run in the
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
