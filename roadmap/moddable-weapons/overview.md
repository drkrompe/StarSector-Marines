# Moddable weapons

> A weapon should be a data file, not a Java enum constant.

## Concept

Every weapon in the mod is an enum entry with `public final` stat fields:
`MarineWeapon` (5 primaries), `MarineSecondary` (1 rocket), `MechWeapon`
(5 mech mounts), `TurretKind` (8 emplacements). Nineteen weapons, four
near-identical field sets, all requiring a recompile to change and none
reachable by a submod.

This track moves the catalog into id-addressed JSON behind a registry —
the same shape the `moddable-tilesets` track
already proved for tiles — and takes the presentation layer with it, so a
modder can author a weapon's stats, sounds, projectile art **and its
particle effects** without touching Java.

## Why now

Three separate threads want this and none of them can move cleanly without
it:

- **`progression` S2 — weapon catalog
  expansion.** Adding weapon families means adding enum constants and
  recompiling, and every new entry has to be hand-wired into
  `designation()` and `modelName()` switch statements.
- **Progression S6 Slice 3 — factional equipment.** Faction × family is a
  cross product. As enum constants that is a combinatorial explosion; as
  data files it is a directory.
- **Modder reach.** `TileRegistry` gave submods the tile catalog. Weapons
  are the obvious next thing a submod wants, and the loading, id, and test
  infrastructure already exists to serve them.

## Design commitments

These are settled. Change them here, not in a story.

1. **One schema across all four catalogs.** Marine primaries, secondaries,
   mech mounts and turret mounts share a field set already; they will share
   a `WeaponDef`. Which catalog a weapon belongs to becomes a *mount class*
   property, not a separate Java type.
2. **Registry is the single source of truth.** No dual authoring — a stat
   lives in JSON or it does not exist. The enums degrade to id handles and
   are then retired.
3. **FX are data-defined and layered.** Not "pick one of four impact
   profiles" — a weapon composes an ordered list of effect layers, so a
   tracer can carry its own particles independently of what its impact
   does, and a later layer kind (smoke trail, heat shimmer, shell ejection)
   is an additive schema change rather than a new enum arm. This follows
   the existing rule that render effects are keyed by composable
   capability, not by carrier type ([[feedback_compose_effects_not_carrier]]).
4. **Parity before migration.** Each catalog's JSON is pinned field-for-field
   against the enum it replaces before any consumer switches over, so the
   migration is provably behavior-preserving. This is the `TileRegistry`
   Phase 1a playbook and it is what makes a numeric refactor reviewable.
5. **Ids are stable and namespaced.** `weapon.pulse-rifle`, matching the
   `doodad.*` / tile id convention. Ids are save-persisted, so they are API
   once shipped.
6. **Fail loud on a missing registry.** A tile registry that is not
   installed degrades to "no overlay scatter". A weapon registry that is not
   installed would mean zero-damage weapons, so it throws instead.

## Stories

| # | Story | Scope |
| --- | --- | --- |
| W1 | `w1-weapon-registry.md` | `WeaponDef` + `WeaponRegistry` + marine-primary JSON + parity test. Enum becomes an id handle. |
| W2 | `w2-layered-fx.md` | Effect layers in data, replacing `ImpactProfile` dispatch. Parity-pinned against the current recipes. |
| W3 | `w3-remaining-catalogs.md` | Secondaries, mech mounts, turret mounts onto the same schema. |
| W4 | `w4-retire-enums.md` | Ids as the only handle; `MarineSoldier` save migration. |
| W5 | `w5-submod-merge.md` | Discovery, load order, id override, validation. Deferred — shared with moddable-tilesets Phase 3. |

Ship order is W1 → W2 → W3 → W4, with W5 deferred until a real submod
exists (the same call moddable-tilesets made for its Phase 3).

## Relationships

- **The `moddable-tilesets` track** owns the
  registry pattern this copies: id-addressed store, `ingest(JSONObject)`
  decoupled from `SettingsAPI` so tests feed JSON straight off disk,
  `loadBuiltins()` for the in-game path, and a parity test pinning data
  against the enum during migration. **W5 and its Phase 3 are the same
  problem** — mod discovery, load order, id override and validation should
  be solved once for both catalogs, not twice.
- **The `progression` track** is the consumer. S2
  (catalog expansion) and S6 Slice 3 (factional equipment) both get
  dramatically cheaper once this lands, and S1's balance pass established
  the TTK harness that makes any data-authored weapon measurable.
- **The `ballistics` track** owns round resolution.
  This track changes where a weapon's numbers come from, never how a round
  resolves.
- **The `battle-render` track** owns the draw-list
  pipeline. W2 changes what `ImpactFx` spawns, not how particles are
  collected or drawn.

## Non-goals

- Changing any weapon's shipped behavior. This track is a substrate move;
  progression S1 already did the tuning and the numbers carry over
  unchanged.
- A modder-facing weapon *editor*. JSON authored by hand is the target.
- Data-driving the AI's weapon reasoning (`TacticalScoring.isHardened`,
  rocket commit gates). Those stay code.
- Data-driving `EquipmentGrade` or armor patterns. Related, and probably
  next, but out of scope here.
