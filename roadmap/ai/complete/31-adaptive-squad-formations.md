# 31 — Adaptive squad formations

**Shipped 2026-08-22 in `4c0864d9`.**

## Player-facing result

Moving mech lances no longer treat 2.5 cells as a normal battlefield
formation. In open terrain they spread to roughly six cells between adjacent
platforms, with assault chassis forward, long-range support rearward, and
armored support on the shoulders. Tight terrain progressively releases the
slot steer while retaining the 2.5-cell mech compression floor, so walkers can
pass through streets and one-cell corridors without adopting the old bunched
open-ground posture.

The implementation is squad-generic rather than mech-specific. Infantry
fireteams use the same formation engine at a 0.75–1.75-cell scale, ordering
shorter-range weapons forward and longer-range weapons rearward.

## Shipped contract

- `SeparationSystem` assigns stable two-member, wedge, diamond, or circular
  slots around the moving members' centroid and mean path heading.
- A formation engages only when at least two active-path members of one
  organized, non-drone squad agree on their direction of travel. Divergent
  individual orders release the formation rather than creating a tug.
- `FormationProfile` supplies family scale and clearance requirements. Mechs
  blend from 2.5 cells in constrained terrain to six cells with three clear
  rings; infantry blend from 0.75 to 1.75 cells with two clear rings.
- Formation pressure fades with terrain openness and is absent at the fully
  compressed floor. Independent same-faction mech pair separation still
  preserves the 2.5-cell minimum, including between different squads.
- Mech slot priority is ASSAULT, ARMORED_SUPPORT, then LR_SUPPORT. Infantry
  priority uses the live primary attack range, putting close-range members in
  front and longer-range members behind them.
- Idle units, hostile pairs, drone squads, stalled mechs using collision
  escape, and squadmates following incoherent paths receive no slot steer.
- All new tick state uses reusable primitive scratch buffers; the steady-state
  formation pass allocates no per-member objects.

## Verification

- `SeparationSystemTest` covers a mixed-role four-mech open-ground diamond,
  six-cell interval, role depth, and the gentle correction cap.
- A one-cell corridor fixture verifies the 2.5-cell mech floor remains usable
  without leaving walkable terrain.
- Infantry coverage verifies the shared profile and weapon-range depth order.
- Divergent-path and separate-lance cases verify formations do not override
  individual orders or merge unrelated squads.
- The focused separation and mech collision-escape suites passed before the
  code commit.

## Manual follow-up

Use the DEBUG four-mech lance and normal marine deployments on both open maps
and urban maps. Confirm the six-cell mech footprint reads as one formation
without making the walkers lag behind their paths; watch a lance compress at a
street or doorway and expand again afterward. For infantry, verify the
1.75-cell interval improves readability without fighting cover cells, bounding
overwatch, or explicit mission formations.
