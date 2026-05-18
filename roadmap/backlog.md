# Backlog

Known future work, grouped by area. Loose priorities; the README's
"Immediate next-up" is what's actually queued.

## Music
https://davidkbd.itch.io/eternity-metal-scfi-music-pack

## Gameplay

- **Briefing screen** — mission detail view with accept/decline. Reads
  selected mission from `MarineOpsContext`.
- **Mission resolution** — consume marines (from cargo and/or captain's
  squad), apply trait bonuses, award XP, roll for injury/death on bad
  outcomes. Hooks into `MarineRosterScript`.
- **Captain discovery system** — cryo-pod recovery from salvageable
  derelicts as the canonical in-universe acquisition path. Hook into
  vanilla salvage events.
- **Roster cap scaling** — replace hardcoded 10 with `f(playerLevel)`.
- **Trait mechanics** — currently placeholder enums (`SIEGE_SPECIALIST`,
  `SAPPER`, etc). Wire to mission resolution modifiers.
- **Captain rank promotion** — XP threshold, ranks unlock larger squad
  capacities (already encoded in `Rank` enum).
- **Injury recovery** — periodic tick in `MarineRosterScript.advance` to
  return injured captains to ACTIVE after some days.
- **Faction-enemy covert ops** — high-rep with a faction unlocks
  "deniable contracts against their enemies" as a mission category in the
  current client's list. Not a separate client row.
- **Mission gating by reputation** — locked client rows already exist;
  extend to per-mission gating (e.g., high-risk only at WELCOMING+).

## UI

- **Briefing screen** (also gameplay item above).
- **Mission resolution screen** — outcome readout, casualty list, XP +
  loot.
- **Captain roster screen** — the eventual 3D bridge view, accessed from
  the intel screen. Replaces the cube placeholder.
- **Text wrapping in `BitmapFont`** — single-line method exists; add
  `drawStringWrapped` returning consumed height. Briefing screen will
  force this.
- **Tooltip widget** — generic hover popup primitive. Mission popup is
  the prototype; generalize when a second consumer appears.
- **Scrolling for the client list** — currently fits 4–6 rows; if a
  planet ends up with many factions present, we need scroll.
- **Faction-themed UI colors per selected client** — column borders /
  accents pick up the client's faction color for a "you're in their
  context now" feel.

## Asset pipeline

- **Real asset import end-to-end** — drop a test FBX/glTF into
  `asset-pipeline/src/tool/resources/`, run
  `gradlew :asset-pipeline:processModels`, load resulting `.mlmodel` at
  runtime, render via a new `MeshDrawable`. Proves the pipeline beyond
  the round-trip test.
- **Skinned animation runtime** — `Animation` / `Skeleton` /
  `AnimationRetargeter` are already in the runtime source set; wire to
  a `SkinnedMeshDrawable` once a test asset exists.

## Polish

- **Pole warping on planet sphere** — fragment-shader fade-to-tint near
  poles to hide the equirectangular squish. Costs one shader change.
- **Texture-aware mission placement** — read pixel color on the planet
  texture at the candidate position, reject if it's water/ice. Long-term
  ask from playtest.
- **`BridgeRenderer` debug scaffolding cleanup** — `DEBUG_QUADRANTS` and
  `DEBUG_DIRECT_QUADRANTS` flags can go now that the foundation is solid.

## Architecture / refactor candidates

- **Screen abstraction** — pull when the second screen (briefing) actually
  needs to transition. One screen is a guess; two informs the interface.
- **Per-panel `WidgetRoot`** — current plugin rebuilds the entire widget
  tree on selection change (`ClientRowWidget` hover state flickers for
  one frame). Per-panel subtrees would let the tactical map rebuild in
  isolation.
- **`BridgeRenderer` naming** — it's a generic FBO scene renderer at
  this point, not bridge-specific. Rename when it gets a third consumer.
- **`PlanetIntelPanel` `nameRowH` duplication** — same calc in
  `buildIntelBlock` and `onRender`; extract.
- **`LabelWidget` variable-height** — for wrapped text. Tied to text
  wrapping in `BitmapFont`.
- **Mission name generation through i18n** — currently hardcoded English
  templates in `MissionGenerator`. Move to strings.json with template
  formatting.

## Translation / community

- **i18n coverage audit** — all user-facing strings should already route
  through `Strings.get(...)`. Periodically sweep for hardcoded English.
- **Translation mod template** — eventually ship a `strings-template.json`
  with comments explaining the override pattern.
