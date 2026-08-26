# Target-faction facility treatment

Status: PLANNED — begins after the neutral local-shield relay proves its placement and gameplay contract.

Written: 2026-08-24

Updated: 2026-08-26 — routed faction facility authors through the enduring lore guide catalog.

Read `mapgen-nouns.md`, `campaign-battle-bridge-nouns.md`,
`faction-lore-nouns.md`, and `moddable-tilesets-nouns.md` before implementing
this story.

## Problem

The target profile already carries faction identity, but no map-generation
consumer uses it. A shield relay or orbital fire-control emplacement would
therefore look and fight the same on every world even after its shared tactical
function becomes meaningful. Faction flavor confined to briefing copy and unit
colors does not make the place itself belong to anyone.

## Goal

Give the shared hard-installation family a data-authored **faction treatment**
that changes materials, silhouette, internal footprint composition, cover
rhythm, and approach character while preserving one facility function,
coverage fact, host-compound relationship, and validity contract.

The local shield relay is the first consumer. The later orbital-battery story
may reuse the treatment vocabulary only after the relay proves it; this story
does not ship both installations at once.

## Core-faction direction

| Faction | Facility treatment |
| --- | --- |
| Hegemony | Buried, redundant ballistic-era bunker works; heavy frontal cover and deliberate service access |
| Tri-Tachyon | Compact high-energy pylons and clean control architecture; sparse hard cover, strong sightlines, protected technical core |
| Persean League | Modular midline fortification assembled from standardized sections; balanced approaches and overlapping cover |
| Luddic Church | Older civic or monastic masonry wrapped around maintained Domain machinery; defensible courtyards and narrow service lanes |
| Luddic Path | Scarred, improvised, overdriven conversion with demolition clutter and unsafe-looking access; no secret combat-rule exception |
| Sindrian Diktat | Monumental state-industrial shell, armored control rooms, fuel/power-service infrastructure, rigid axial approach |
| Pirates | Scavenged plates, exposed conduits, broken perimeter, irregular cover and opportunistic side access |
| Independents | Practical utility compound and the neutral fallback for unknown/modded factions |

These directions describe authored spatial/content families, not faction
bonuses. Unknown/modded factions use the Independent treatment unless merged
content maps them to another family or declares a validated treatment.

## Placement and validity

- Select treatment from the campaign-free target faction id already present in
  generation context. Do not import campaign faction objects or read a live
  market from a filler.
- Every treatment fits the installation's declared planning envelope and
  honors the host compound's circulation obligations.
- Variation may change cover and sightline texture, but it must stay inside an
  explicit encounter budget so one faction's relay is not automatically a
  higher difficulty tier. Target defense and force balance retain that job.
- Collision, doors, cover, destructibility, and line of sight come from the
  generated tactical result. Decorative sprites cannot imply walls, openings,
  or protection the navigation/combat model does not contain.
- Missing required treatment assets fail catalog validation. An unknown faction
  id uses the complete Independent fallback rather than generating a partial
  facility.

## Acceptance

- The same seeded relay request can be rendered for every core faction and the
  unknown-faction fallback in deterministic snapshot evidence.
- Every treatment is recognizable in silhouette/material/approach rather than
  only by a recolored emblem.
- All treatments pass connectivity, deployment, door, cover, and host-compound
  validation and publish the identical relay function and coverage contract.
- Comparative tactical metrics state approach count, traversable area, hard
  cover, and major sightline bands; no treatment escapes the agreed budget.
- Live passes confirm at least Hegemony, Tri-Tachyon, Luddic, pirate, and
  Independent treatments feel distinct without altering capture time, shield
  coverage, barrage denial, or defender count.
- A modded faction can select a built-in treatment through merged data without
  Java changes.

## Out of scope

- Faction roster/loadout selection — `target-faction-ground-rosters.md`.
- Faction commander behavior — `target-faction-command-doctrine.md`.
- Contract motive or objective mandate — `faction-ground-contract-policy.md`.
- Facility-specific battle rules, coverage, capture, destruction, loot, or
  campaign writeback.
- A general faction reskin of every economic district, station room, road, or
  building. Expand only when another concrete consumer earns it.
