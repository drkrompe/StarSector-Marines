# Units as sheet quads, roofs as residency

Status: IN PROGRESS

Written: 2026-09-03

The two ceilings `renderEvidence` names after the ground layer stopped being
one. Two levers, each measured against its own control and integrated on its
own.

## The measurement this starts from

`renderEvidence` at `f54d204b5`, every existing lever on:

| map | framing | our ms | largest layer |
|---|---|--:|---|
| 280x168 | close | 1.64 | `UNITS` 0.64 (0.38 collect / 0.27 drain) |
| 280x168 | whole-map | 1.86 | `ROOFS` 0.80 (0.60 collect / 0.20 drain) |
| 560x336 | close | 1.16 | `UNITS` 0.59 (0.28 collect / 0.31 drain) |
| 560x336 | mid | 1.23 | `UNITS` 0.48 (0.31 collect / 0.18 drain) |
| 560x336 | whole-map | 1.67 | `GROUND` 0.53 |

`UNITS` at 560x336 close: 496 commands, **230 whole sprites, 232 draws across
230 texture binds**. It cannot coalesce, because a whole sprite is a foreign
call that binds its own texture — and a body is not one sprite but six or seven,
since the authored composition draws feet, body, weapon, head and muzzle flash
from separate PNGs.

`ROOFS` at 280x168 whole-map: 6,035 commands leaving as **one draw**. That is
collection-bound, and wants fewer commands rather than fewer binds.

## Lever 1 — the unit atlas

Every image a body is composed from, composited into one GL texture at battle
load, with each keeping an origin inside it; a whole-image draw is emitted as a
rotated sheet quad over its own slot instead of as a foreign sprite call. The
ground atlas at a different scale.

- `SpriteAtlas` holds the mechanism the ground atlas already proved — shelf
  pack, read-back into slots, gutter, fail-soft — and `GroundAtlas` and
  `UnitAtlas` differ only in which images they hold.
- The redirect is in `DrawList.addSprite`, so no composer knows the atlas exists
  and none of them can disagree with another. Painter order untouched.
- Toggle `battle.render.unitAtlas`; any failure at all returns every body to the
  sprite path with the same picture.
- Bounded by what an image is *for*: an atlas pays where many of its images are
  on screen, which a composed body is and a vanilla aircraft hull is not.

## Lever 2 — resident roofs

Roof quads are a pure function of the topology and of which building owns the
cell; the only per-frame thing about them is a building's fade alpha. So the
geometry is baked into buffers in its own sub-layer of `ROOFS`, patched from the
`CellTopology` change log on a cave-in, and a building whose alpha moved is a
per-vertex alpha patch of its own contiguous range.

Toggle `battle.render.residentRoofs`, same fail-soft.

## Acceptance

- `renderEvidence`, both maps, three framings, each lever against its own run's
  control: `UNITS` draws under 10 at close and mid with the atlas on and `UNITS`
  ms no worse at any framing; `ROOFS` under 0.3 ms at 280x168 whole-map; nothing
  worse anywhere.
- GL pixel evidence per lever under `shaderEvidence`, each carrying a control
  proving the frames were not both blank.
- `createSnapshots -Psnapshot=layers,armory,sun-shadows,frontage-scene,perception-sweep,mech-doctrine,ship-decks`
  byte-identical to a pre-change run.
- `verifyModJarBoundary`, `:test`, `shaderEvidence` green.

A lever that fails acceptance ships off, with its table and the reason.
