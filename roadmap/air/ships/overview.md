# Ships

> Ship-class member of the [`air/`](../overview.md) category. The shared
> vanilla-hull → sim-entity pipeline lives in
> [`hull-extraction.md`](../hull-extraction.md); this doc covers what's
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

Pipeline (see [`hull-extraction.md`](../hull-extraction.md) for the extraction):

1. **Broad phase** — `collisionRadius` circle test. Cheap reject before any
   per-segment work. This is the *only* role the circle plays — it is not a
   collision stand-in.
2. **Narrow phase** — projectile/segment vs the rotated concave polygon
   (point-in-polygon for an arrived shot, segment-intersect for a traveling
   one). Concave-safe (ray-cast / even-odd, not a convex-only test).
3. **Contact point** → drives hull-impact SFX + FX at the real hit location.

The polygon is rotated to the ship's facing each tick (we own this; the live
`BoundsAPI.update()` isn't available headlessly — see the runtime caveat in
[`hull-extraction.md`](../hull-extraction.md)).

`mass` (`ship_data.csv`) becomes relevant here if we ever model knockback /
ramming; for "ground weapon hits ship" it's not needed.

## Weapon mounts

`getAllWeaponSlotsCopy()` gives turret positions per hull, in the same pixel
space as `bounds`. Optional, but it opens "aim between the turrets" / "hit a
specific mount" if ship targeting ever wants sub-hull precision. Defer until a
gameplay reason appears.

## Scale & altitude — resolved direction

> **The one-constant footprint is shipped** (for shuttles; ships inherit it) —
> `battle/air/AirScale.METERS_PER_PX = 0.65`, derived length via
> `HullFootprintResolver`. See
> [`../complete/global-pixel-density-scale.md`](../complete/global-pixel-density-scale.md).
> The ladder below is now the live behavior, not a proposal; the constant's
> absolute calibration is still pending playtest.

The earlier "on-map giant vs. high-altitude small" framing dissolved once we
stopped treating Starsector `su` as physical size. Vanilla `su` are *arena*
units (sized so a fleet reads at fleet-camera zoom), not meters. **Anchor:
1 cell = 1 m**, and — the key unblock — **one global `METERS_PER_PX` constant**
sizes every hull, because all Starsector sprites share one pixel density (see
[`hull-extraction.md`](../hull-extraction.md) § "Scale"). Vanilla's own internal
consistency then carries the whole relative-size ladder for free, **and modded
hulls inherit it** (they were built to sit next to vanilla at that same density).

At ~0.65 m/px the roster lands on a coherent, believable ladder — this table is
the **calibration sanity-check** you eyeball to pick the one constant, not a
per-class mechanism:

| class | sprite-height px | → length | on-map? |
| --- | --: | --: | --- |
| fighter (talon) | 24 | ~16 m / ~16 cells | yes |
| frigate (lasher) | 96 | ~62 m | yes |
| destroyer (hammerhead) | 164 | ~107 m | yes |
| cruiser (eagle) | 218 | ~142 m | borderline; map-size dependent |
| capital (onslaught) | 384 | ~250 m | **no — orbital / off-map fire support** |

Two consequences:

- **The silhouette comes from vanilla `bounds`; the absolute size comes from one
  global `METERS_PER_PX` — not `su`, and not per-hull.** Three scales are
  decoupled (see [`hull-extraction.md`](../hull-extraction.md) § "Scale"): polygon
  *shape* (vanilla bounds, normalized), *footprint* (sprite/bounds px × the one
  constant), and *kinematic feel* (engine stats × kinematic `SCALE`).
- **Size self-selects what's even on the battlefield.** A capital doesn't strafe
  infantry; realistically it stands off and bombards from orbit. So capitals are
  off-map fire support, never rendered as an on-map polygon — the fiction
  removes the hardest case rather than us solving "draw a 600-cell ship."

Larger maps push the cutoff *up*: at a planned **512+** cell dimension a frigate
(~60–80 cells) is a comfortable fraction and even a cruiser fits, so more classes
become plausibly on-map.

**Altitude is a real Z, not a fake shrink.** Rather than rendering distant ships
artificially small, airborne craft live at a camera-shared **Z height**; the
current fit-to-viewport camera (`MIN_ZOOM = 1.0` = whole map fits, zoom *in*
only) already provides the "squint and believe" view at its default rest state,
and zoom-in is the hull-detail / hit-FX view. True zoom-out beyond full-map and a
proper altitude axis arrive with the render layer's planned **camera view-proj +
camera-Z** upgrade — see `roadmap/battle-render/overview.md` § "Future: camera
view-projection + camera-Z". Whether ground weapons have a ceiling/range gate
against airborne ships then becomes a deliberate difficulty lever rather than a
scale hack.

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

- ~~Pick the single `METERS_PER_PX` constant~~ — **done: `0.65`** in
  `AirScale` ([`../complete/global-pixel-density-scale.md`](../complete/global-pixel-density-scale.md)).
  Absolute calibration against a fixed target map size (and the resulting **map
  growth** to fit ~172-cell capitals' smaller cousins) is the remaining tuning.
- Do ground weapons have a ceiling / range gate against airborne ships, and is
  that the intended difficulty lever? (Now a camera-Z / altitude question — see
  the render-layer dependency above.)
- Does a ship take cumulative hull damage and get driven off / shot down, or is
  it invulnerable scenery you can only suppress? (Sim commitment scales with the
  answer.)
- Decomposition (stories) — the on-map collision build can proceed for the
  fighter/frigate/destroyer classes; capital off-map fire support is a separate,
  later concern.
