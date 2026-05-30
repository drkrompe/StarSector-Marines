# Ships

> Ship-class member of the [`air/`](overview.md) category. The shared
> vanilla-hull → sim-entity pipeline lives in
> [`hull-extraction.md`](hull-extraction.md); this doc covers what's
> ship-specific. **Design stage.**

## What this is

Full-size vanilla/modded ships — frigates up through capitals — present
**overhead** in a ground battle, modeled in our sim so that **ground defenses
can target them and hits land on the actual hull**. The fiction: craft flying
over the battlefield in atmosphere (close air support, a bombardment platform,
a dropship on approach), not abstract off-map artillery.

A ship is, mechanically, just a **big slow `AirBody`** — its `getEngineSpec()`
numbers (low turn rate, large effective radius) give capital-ship handling with
no new kinematic code. The ship-specific work is the **collision geometry** and
the **scale**.

## Collision: full concave polygon (committed)

Ships use the **full concave hull polygon** from `bounds` — not a circle, not an
OBB, no stand-in. This is a deliberate fidelity choice, not a perf compromise to
be relaxed: hits resolve against the real hull silhouette, which is what lets us
**play hull-impact SFX (and spawn impact FX) at the true contact point** and
gives the Starsector-faithful "shoot the exposed flank, not the empty space
between the prongs" feel. Frigate hulls already carry the detail to make this
read — wolf is a 16-point concave poly, lasher 18.

Pipeline (see [`hull-extraction.md`](hull-extraction.md) for the extraction):

1. **Broad phase** — `collisionRadius` circle test. Cheap reject before any
   per-segment work. This is the *only* role the circle plays — it is not a
   collision stand-in.
2. **Narrow phase** — projectile/segment vs the rotated concave polygon
   (point-in-polygon for an arrived shot, segment-intersect for a traveling
   one). Concave-safe (ray-cast / even-odd, not a convex-only test).
3. **Contact point** → drives hull-impact SFX + FX at the real hit location.

The polygon is rotated to the ship's facing each tick (we own this; the live
`BoundsAPI.update()` isn't available headlessly — see the runtime caveat in
[`hull-extraction.md`](hull-extraction.md)).

`mass` (`ship_data.csv`) becomes relevant here if we ever model knockback /
ramming; for "ground weapon hits ship" it's not needed.

## Weapon mounts

`getAllWeaponSlotsCopy()` gives turret positions per hull, in the same pixel
space as `bounds`. Optional, but it opens "aim between the turrets" / "hit a
specific mount" if ship targeting ever wants sub-hull precision. Defer until a
gameplay reason appears.

## Scale & altitude — the hard part

The scale problem the fighters track waves at **bites hard** here. A capital is
~400–600 su long; a marine-scale cell is tiny by comparison. Two framings, and
the targeting model flows from the choice:

- **On-map giant** — the ship is a literal huge silhouette over the battlefield.
  Faithful poly collision, dramatic, but dominates the map and strains the
  ground camera.
- **High-altitude small** — rendered small/distant at altitude; collision poly
  scaled down to match. Reads as "up there," and raises the real question of
  **whether ground weapons can even reach it** (range/ceiling gating becomes a
  gameplay lever, not a bug).

This is the same atmospheric-altitude flavor lever from
[`hull-extraction.md`](hull-extraction.md), turned up. Likely a per-craft or
per-mission altitude band rather than one global answer. **Open — needs a
call before ship collision is built.**

## Modules deferred

Multi-part hulls (`getModuleAnchor()`, station modules) carry separate `bounds`
per module. Single-bounds covers nearly all combat ships; treat modules as a
later story.

## Relationship to the vanilla-combat-bridge track

"Ground defenses target ships" is adjacent to
`roadmap/vanilla-combat-bridge/overview.md`'s sim-authoritative **proxy targets**
thread (vanilla carriers/fighters engaging our sim). Same problem — vanilla
ships and our sim interacting — approached from opposite ends. Keep the two docs
cross-linked so the eventual integration story doesn't get designed twice.

## Open questions

- On-map giant vs high-altitude small (above) — gates the whole collision build.
- Do ground weapons have a ceiling / range gate against airborne ships, and is
  that the intended difficulty lever?
- Does a ship take cumulative hull damage and get driven off / shot down, or is
  it invulnerable scenery you can only suppress? (Sim commitment scales with the
  answer.)
- Decomposition (stories) — deferred until the scale/altitude call is made.
