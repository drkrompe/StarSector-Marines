# W1 — Weapon registry and schema

> The catalog moves to disk. Nothing else changes.

**Status:** shipped. See "What landed" below.

## Scope

- `WeaponDef` — the immutable parsed shape, covering all four catalogs'
  field sets even though only marine primaries populate it this story.
- `WeaponRegistry` — id-addressed store. `ingest(JSONObject)` is decoupled
  from `SettingsAPI` so tests feed JSON straight off disk;
  `loadBuiltins()` is the in-game path; `install()` / `installed()` mirror
  `TileRegistry` exactly.
- `mod/data/marines/marine-weapons.weapon.json` — the five primaries,
  carrying their current shipped values verbatim.
- `MarineWeapon` keeps its five constants as **id handles only**. Its
  `public final` stat fields become accessors that resolve through the
  registry, and `designation()` / `modelName()` stop being switch
  statements over the enum and start being data.
- `TileRegistryTestInstaller` gains a weapon-registry block so every test
  gets a loaded registry for free.
- `WeaponRegistryParityTest` pins each JSON value against the value the
  enum shipped, so the migration is provably behavior-preserving.

## Schema

Four blocks. `sim` is what the simulation reads, `render` and `audio` are
presentation, `catalog` is player-facing naming.

```json
{
  "weapons": [
    {
      "id": "weapon.pulse-rifle",
      "mount": "marine-primary",
      "catalog": {
        "displayName": "Pulse Rifle",
        "modelName": "Lancer",
        "designation": "PLS",
        "designationTiered": true
      },
      "sim": {
        "range": 24.0,
        "damage": 9.0,
        "accuracy": 0.35,
        "cooldown": 1.0,
        "vsHardenedMult": 0.30,
        "burstCount": 3,
        "burstSpacing": 0.09,
        "accuracyFalloff": 0.30,
        "hitSpread": 0.4,
        "roundVelocity": 55.0
      },
      "render": {
        "tracerColor": "80FF80",
        "impact": "rifle",
        "projectileSprite": null,
        "projectileVisualCells": 0.0
      },
      "audio": { "fireSound": "pulse_laser_fire" }
    }
  ]
}
```

Notes on specific fields:

- **`designation` / `designationTiered`** replace the switch in
  `MarineWeapon.designation(grade)`. Today the pulse rifle renders as
  `PLS-<tier>` while the field rifle is always `FR-1`; that is a prefix
  plus a boolean, not control flow.
- **`vsHardenedMult`** is `vsTurretMult` renamed at the schema boundary.
  The field name is already documented in `DamageResolver` as "misnamed
  history — it's the vs-hardened multiplier". Data is a fresh surface, so
  it gets the honest name; the Java field keeps its name until W4.
- **`render.impact`** is a named profile reference this story and is
  **replaced** by an authored layer list in
  `w2-layered-fx.md`. That key change is acceptable because the schema
  does not become modder-facing API until the enums retire in W4 — but do
  not ship W1 alone and call the format stable.

## Out of scope

- Any behavior change. Every number in the JSON is the number the enum
  ships today.
- The other three catalogs — that is `w3-remaining-catalogs.md`.
- Removing the enums — that is `w4-retire-enums.md`.

## Acceptance

- `WeaponRegistryParityTest` asserts, per weapon and per field, that the
  registry value equals the value the enum shipped before this story. It is
  the artifact that makes the migration reviewable, and it is deleted by W4
  when the enum-side value no longer exists.
- Every existing test passes with no change to expectations. Any test that
  needs editing means behavior moved and the story is wrong.
- The TTK report is re-run and lands inside its standard error of the
  pre-story numbers.
- A missing or unparseable registry **throws at load**, not at first shot.

## Open questions

- Should `mount` gate what a def may declare — a marine primary with an
  `aoeRadius` is probably an authoring error, but rejecting it costs a
  validation pass. Leaning: validate in W3, when there is more than one
  mount class to get wrong.
- One file per catalog or one per weapon? Leaning per catalog while the
  counts are small, since a submod adding one weapon adds one file either
  way once W5 lands.

## What landed

Shipped as designed, with one deliberate addition and one correction.

**New package `battle.weapon`** — `WeaponDef` (parsed shape), `MountClass`
(which catalog a def belongs to), `WeaponRegistry` (id-addressed store),
plus a `package-info` charter. The registry mirrors `TileRegistry`'s
install/ingest/loadBuiltins shape so the two stores stay learnable as one
pattern, but diverges on one point: `WeaponRegistry.require` **throws**
where the tile registry degrades, because a weapon that silently reads zero
range and zero damage is worse than a crash.

**`MarineWeapon` is now an id handle.** Its `public final` stat fields
became accessors delegating to `def()`; the compiler located all 88 read
sites across 22 files and every one is a mechanical `.field` → `.field()`.
The def is deliberately **not cached** on the enum: a handful of map lookups
per shot is not worth making registry installation order-dependent.

`designation()` and `modelName()` were switch statements over the enum and
are now `designation` + `designationTiered` in data — a prefix and a
boolean, which is all the control flow ever encoded.

**Correction to the plan:** the per-constant javadoc was restating numbers
(`"Per-shot damage is lighter (0.7) so a full burst lands ~2.1"`), and those
had *already* gone stale against the progression S1 balance pass. Rather
than move stale prose, the javadoc now explains intent only and cites no
figures. A number written in two places drifts; this is the second time
these particular ones did.

**Load order matters and is now documented at the call site.**
`WeaponRegistry.loadBuiltins()` runs last in `onApplicationLoad` but must
precede any consumer that walks the catalog at load time — `BattleSprites`
preloads every primary's projectile sprite through it.

### Verification

- `WeaponRegistryParityTest` pins all five weapons field-for-field against
  the values the enum shipped, plus the catalog-naming behavior the two
  switch statements encoded. Expectations are written as literals so a typo
  in the JSON cannot validate itself.
- Full root suite green with **no test expectation changed** — the story's
  own acceptance bar for "nothing moved".
- TTK report re-run: 1.73 / 3.59 / 5.04 / 9.05 s against 1.86 / 3.40 / 5.13
  / 8.90 s before, every row inside one standard error. The parity test is
  the exact proof; this is the end-to-end sanity check.

### Follow-ups this surfaced

- `TurretKind.maxHp` puts an emplacement's health on the *weapon*. W3 should
  question it rather than copy it into the shared schema.
- The `MarineWeapon` naming wart (it hosts `DRONE_PULSE`, and drones are not
  marines) disappears for free at W4 — `weapon.drone-pulse` carries no such
  implication.
