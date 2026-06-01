# Campaign → battle bridge

> The seam that lets the **target world** shape the **battle**. Today a battle
> knows almost nothing about the planet it's being fought over: only
> `Mission.risk` (→ map size + defender count) and a single planet-name-derived
> boolean (`DetachmentResolver.planetHasHeavyArmaments` → "do defenders field
> mechs") survive the trip into `BattleSetup`. Everything richer the campaign
> knows — population, industry, planetary defenses, faction, spaceport tier — is
> in scope at the launch point and then dropped before generation. This feature
> is the deliberate, narrow pipe that carries that read across.

## Why this exists

The player should *feel* the difference between assaulting a hardened core
world and a soft frontier colony — in the map they land on and the guns that
greet them. The receptors already exist on the generation side; they're just
driven by dice instead of data:

- `OverwatchTowerStage` scales its gun budget off `CELLS_PER_TOWER` — a seam
  already flagged for a campaign-driven *defense intensity* multiply.
- `MapDistrictTheme` (9 themes) + `DistrictMap` / `BiomeMap` weight what blocks
  get placed (residential / commercial / industrial / fortified) — selected by
  random + edge-bias (legacy) or axis percentile (conquest), never by data.

So the gap is **plumbing**, not new systems. This feature builds the pipe once
and hangs consumers off it incrementally.

## The boundary rule (non-negotiable)

`battle.world.gen` stays **campaign-free** — no `MarketAPI` import anywhere in
it. It is the headless-testable core every generator/taxonomy test drives
without a running game. So the bridge is a **plain value object**
([`TargetProfile`](../../src/main/java/com/dillon/starsectormarines/battle/world/gen/TargetProfile.java))
— primitives + enums + an interned faction id, no game API — **extracted at the
campaign boundary** and threaded inward.

- **Extraction** lives beside `DetachmentResolver` (the precedent: that class
  already reads the vanilla economy by planet name and hands the battle tier a
  derived boolean — the bridge is the honest generalization, one boolean → a
  small struct). Reads **vanilla** `MarketAPI` — the mission targets a vanilla
  planet, so this is *not* the mod's own `CampaignState` SoA (that's a separate
  store; see [`../campaign/architecture.md`](../campaign/architecture.md)).
- **Threading**: `MissionLaunch` → `BattleSetup.create*` → a new
  `MapGenerator.generate(…, TargetProfile)` overload → bound on `GenContext`
  under `BspKeys.MARKET_PROFILE`. Generator stages read it and **default to
  `TargetProfile.NEUTRAL`** when absent, so tests / legacy previews / story ops
  with no backing market produce byte-identical output to the pre-bridge era.

**Cost of not honoring:** a `MarketAPI` reference inside `gen` breaks every
headless generator test and couples the procedural core to the live sector.

## The profile

`TargetProfile` is extracted **whole, once** (even though the first consumer
reads only `defenseLevel`), so the extraction is stable as consumers opt in:

| field          | source                                              | feeds |
|----------------|-----------------------------------------------------|-------|
| `marketSize`   | `MarketAPI.getSize()` (population proxy)            | urban composition |
| `stability`    | `MarketAPI.getStabilityValue()`                    | (garrison morale, later) |
| `defenseLevel` | ground defenses / heavy batteries / orbital station / high command / planetary shield | **defense intensity (consumer 1)** |
| `spaceportTier`| spaceport / megaport industry                       | port + landing structures |
| `factionId`    | `MarketAPI.getFactionId()`                          | cosmetic / roster, later |

## Consumers (each an independent slice off the shared pipe)

1. **Defense intensity** — ✅ shipped (`794795e`, see
   [`complete/defense-intensity.md`](complete/defense-intensity.md)).
   `defenseLevel` → `OverwatchTowerStage` gun budget multiply + turret-tier
   escalation (`VULCAN → ARBALEST → HEPHAESTUS`). A fortified world fields ×2.3
   the overwatch line, all heavy. "This world is *fortified*."
2. **Urban composition.** `marketSize` / `spaceportTier` → `DistrictMap` /
   `BiomeMap` theme weighting: big worlds skew dense residential + commercial,
   a spaceport pulls in port + landing structures, a backwater reads sparse.
   "Big port city vs. backwater." Touches both theme-selection paths.
3. **Hard installations as map features.** Orbital station / battery / shield
   presence placed as actual map structures — Lever-2-style content injection
   (see [`../mapgen/stories/structural-taxonomy.md`](../mapgen/stories/structural-taxonomy.md)).
   "Fighting under the guns."

The same pipe is what a future **defender-roster-reflects-garrison-strength**
feature wants, so it isn't mapgen-specific — hence its own feature dir.

## Cross-refs

- [`../mapgen/stories/structural-taxonomy.md`](../mapgen/stories/structural-taxonomy.md)
  § "Future slices" item 2 — where this bridge was first parked, as the
  dependency for market-driven defense intensity. Now its own feature.
- [`../campaign/architecture.md`](../campaign/architecture.md) — the mod's own
  campaign-state store; the bridge reads *vanilla* econ, not this.
- `DetachmentResolver` — the extraction precedent the resolver mirrors.
