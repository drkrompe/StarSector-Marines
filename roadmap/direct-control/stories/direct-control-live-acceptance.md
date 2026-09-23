# Direct control live acceptance

Status: PLANNED

Written: 2026-09-23

Updated: 2026-09-23 — added eyes-on collision checks for manual motion.

Read `direct-control-nouns.md` first. Begin after the Marine slice; repeat
carrier-specific checks when Mech and vehicle adapters ship.

## Goal

Decide whether direct control is readable, fair, and useful in a real battle,
including its transition back to autonomous play.

## Acceptance pass

- Use each eligible body under contact, behind cover, and near a friendly
  firing lane. Verify cursor direction, shot path and impact, moving fire,
  burst rhythm, sound, and zoom/follow behavior by eye.
- Walk a Marine and a Mech into a wall, along it, through a doorway, and
  toward a blocked diagonal corner. Drive and turn the APC beside the same
  obstacles. No sprite or collision body may pass through them, and opened
  doors must be usable immediately.
- Enter and exit repeatedly while the squad/lance or vehicle has a live
  mission. Observe continuing allied work, mission progress, and an immediate
  handback without a stuck role or stale path.
- Pause, change focus, lose the body, open UI chrome, and leave the battle
  while keys or trigger are held. No stale input may survive.
- Compare zero-input outcome and resource use against the same battle without
  takeover. Tune spread, speed, camera, and any future pacing from the
  observed play rather than granting invisible damage or target knowledge.

Record the resulting control feel and any balance decision in the canonical
noun doc when this story ships; keep raw run notes and traces outside it.
