# Story: collapse the Unit / local* duality (Unit → id handle)

Phase A of the [component model](../component-model.md). Read that for the
why; this is the how.

## Context

`Unit` is half-hollowed. Promoted primitives (hp, cellX/cellY,
cooldownTimer, targetId, the AI timers, …) are canonically stored as SoA
columns on `UnitRegistry`, and `Unit`'s accessors delegate by dense index:

```java
public float getHp()            { return registry != null ? registry.getHp(denseIdx)      : localHp; }
public long  getTargetId()      { return registry != null ? registry.getTargetId(denseIdx): localTargetId; }
```

But every promoted field still has a `local*` twin on `Unit`, live as a
fallback whenever `registry == null` (pre-allocation seed, post-release
corpse snapshot). So we maintain **two stores** bridged by accessors, with
a "don't read `local*` directly" hazard documented at every field site.
That duality is the standing friction every reader hits — and it's about
to get worse as vehicle HP + optional bodies pile on more promoted state.

## Goal

Make the registry the **sole source of truth** for promoted state and
shrink `Unit` toward an id + `denseIdx` + thin accessor shim. Reduce
`local*` to the **minimum the lifecycle genuinely requires**, ideally zero.

## The real question: what does `local*` actually buy?

`local*` exists for the window where a `Unit` is *not* registered:

1. **Pre-allocation seed** — a `Unit` is constructed with stats before
   `UnitRegistry.allocate` copies them into the columns. Could be replaced
   by: pass the seed values *into* `allocate(...)` (a spawn-spec / builder)
   so the unit is never observable in an unregistered-but-live state.
2. **Post-release corpse snapshot** — after `release` tail-swaps the unit
   out, legacy post-death systems (drone-crash sprite, un-migrated
   iteration) still read its last values. Could be replaced by: a small
   explicit `Corpse`/death-snapshot record captured at release, instead of
   every `Unit` permanently carrying a shadow copy of every column.

Pin which of these survive and which dissolve **before** writing code — the
answer decides whether `Unit` keeps a handful of `local*` or none.

## Scope

- The `local*` fields for every promoted primitive + their accessor
  fallback branches.
- The `allocate` / `release` seam in `UnitRegistry` (where `local*` is
  read/written) and the spawn path that constructs units.
- The corpse/post-death read paths (design rule 3 in
  [`overview.md`](../overview.md)) — find every consumer that reads a
  released unit and decide its replacement.
- **Out of scope:** the non-promoted object-side capability fields
  (`primaryWeapon`, `mech`, …) — those are Phase B (component model). This
  story is only about the SoA-promoted-primitive duality.

## Approach

Incremental, registry-truth-first. Likely shape:
1. Route the spawn path through a seed-spec into `allocate` so a live unit
   is always registered — removes the pre-allocation `local*` read.
2. Replace the corpse-snapshot reads with an explicit death record (or
   confirm the consumers can read the column before tail-swap).
3. Delete `local*` + the fallback branch per field once both readers are
   gone; accessors become unconditional `registry.getX(denseIdx)`.

Watch the **xstream/Serializable caveat** (overview.md design rule 2) and
keep the tail-swap denseIdx test green at every step.

## Acceptance

- `Unit` no longer holds authoritative promoted state; accessors read the
  registry unconditionally (or `local*` is reduced to a pinned, documented
  minimum with a clear lifecycle reason).
- The "don't read `local*` directly" hazard is gone or contained to the
  registry's own allocate/release.
- Full suite green; `UnitRegistry` lifecycle tests still cover
  allocate-seed / release-snapshot / tail-swap.
- **Unblocks** the `battle.unit.Unit` → `battle.entity.Entity` rename
  (overview.md naming north star) — though the rename itself stays a
  separate, last step.

## Priority

**High / next.** The user called collapsing this "amazing," and the vehicle
optional-component work is the deadline: do it before more promoted state
ossifies the duality. Pairs naturally with — and slightly precedes —
[`component-grouping`](component-grouping.md).
