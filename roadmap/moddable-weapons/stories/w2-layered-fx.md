# W2 — Layered effect definitions

> A weapon composes its effects. It does not pick one of four.

Status: READY

Written: 2026-08-22

Updated: 2026-08-23 — W1 is folded; layered effects are the recommended next slice.

Read `moddable-weapons-nouns.md` before implementing this story.

## Problem

`ImpactProfile` is a four-arm enum — `RIFLE`, `KINETIC`, `HE`,
`CANNON_HE` — and `ImpactFx.spawnImpact` switches on it into four private
recipe methods. A modder cannot author a fifth, and an existing weapon
cannot vary one element of its look without adopting a whole other
profile's.

The recipes themselves are not complicated. Every one is an ordered
composition of a handful of primitives that each fill the same generic
`Particle` struct:

```
RIFLE   = glow(0.28, 0.10s, spark) + dust(0.22, 0.18s)
KINETIC = glow(0.42, 0.14s, kineticFlash) + dust(0.32, 0.28s) + smoke(0.35, 0.70s)
HE      = glow(0.70, 0.16s, spark) + fire(0.55, 0.45s)
        + smoke x2-3 (0.55-0.80, 1.10-1.50s, jitter 0.45) + dust(0.55, 0.32s)
```

That is data wearing a switch statement.

## Scope

Replace `render.impact: "<profile>"` with an ordered layer list, and make
`ImpactFx` a layer interpreter rather than a recipe dispatcher.

```json
"fx": {
  "impact": [
    { "kind": "glow",  "radius": 0.70, "lifetime": 0.16, "color": "FFE080" },
    { "kind": "fire",  "radius": 0.55, "lifetime": 0.45 },
    { "kind": "smoke", "radius": [0.55, 0.80], "lifetime": [1.10, 1.50],
      "count": [2, 3], "jitter": 0.45 },
    { "kind": "dust",  "radius": 0.55, "lifetime": 0.32 }
  ],
  "muzzle": [
    { "kind": "glow", "radius": 0.30, "lifetime": 0.06, "color": "FFF0C0" }
  ]
}
```

Layer kinds map to the primitives that already exist: `glow` (additive
radial sprite), `dust` (same sprite, non-additive, surface-tinted),
`smoke` and `fire` (animated frames off the shared particle sheet),
`explosion` (vanilla explosion frame), `ring` (expanding shock ring).

Ranged values (`[min, max]`) and `count` / `jitter` cover the HE recipe's
randomised puffs without special-casing it. A scalar is shorthand for a
zero-width range.

**Layer slots** — `impact`, `muzzle`, `tracer`, `trail`. A weapon supplies
the ones it wants. This is the part that makes the design worth doing:
tracer particles become independent of impact particles, so a plasma bolt
can trail sparks while landing like a rifle round, and a future slot is an
additive schema change rather than a new enum arm.

## Out of scope

- New visual capabilities. Every layer kind in this story exists as a
  primitive today; this is a re-expression, not an art pass.
- The draw-list pipeline. `ImpactFx` still appends `Particle`s to the same
  list the `battle-render` pipeline collects.
- Sim-visible behavior of any kind. Effects never feed the simulation.

## Acceptance

- The four shipped profiles are re-expressed as layer lists that produce
  **identical `Particle` values** — same order, same radii, lifetimes,
  colors, growth, additivity. A unit test drives both the old recipe and
  the new layer list through a seeded RNG and asserts field-for-field
  equality, which is what lets a visual change be reviewed without eyes.
- `ImpactProfile` is deleted, or retained only as a named preset that
  expands to a layer list.
- Adding a fifth impact character requires no Java change.

## Open questions

- Do layer lists get their own ids so weapons can share one by reference
  (`"impact": "fx.rifle-strike"`) instead of copying it into every def?
  Leaning yes, since the four current profiles are shared by many weapons
  across all four catalogs — but that is a registry-within-a-registry and
  may be better as its own small store.
- Should `tracer` subsume `MarineWeapon.tracerColor` and
  `projectileSprite`, or do those stay top-level `render` fields? Leaning
  subsume, so all presentation lives in one block.
