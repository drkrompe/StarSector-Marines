# Mech battle doctrine overrides

Status: IN PROGRESS — implementation and deterministic evidence are complete;
the live battle interaction pass remains.

Written: 2026-08-30

Read `mechs-nouns.md`, `ai-nouns.md`, and `ui-nouns.md` before changing this
story.

## Goal

Let the player select an exact deployed friendly mech and change how it serves
its current assignment with one click. The choice is battle-local and may be
cleared back to the role frozen at deployment.

## Player contract

- The selected-Mech plate shows variant identity, deployed doctrine, and the
  currently effective doctrine.
- Brawler, Tank, Long Range Support, and Balanced are available in one click.
  Tank is the concise player label for the canonical Frontline Support doctrine.
- Use Default clears the battle override and restores the deployed doctrine.
- The control appears only for an exact live player mech. Enemy units,
  infantry, stale selections, and rescue-pickup mechs have no doctrine control.
- A click takes effect at the next serialized command phase and produces an
  immediate local replan. It does not rewrite the current assignment, reveal
  enemies, refill weapons, repair damage, reset morale, or change siblings.

## Doctrine acceptance

- Brawler closes to direct pressure and continues a legal advance without an
  ally, while remaining leashed to the current assignment.
- Frontline Support chooses a legal allied infantry or mech anchor, occupies
  its threat-facing side, and does not roam after contacts when no anchor
  exists. It may still hold, turn, and fire in self-defense.
- Long-Range Support seeks useful medium-to-long range, line of sight, and a
  credible allied screen. A close threat causes it to open distance and fight,
  not cling to a stale cached firing cell.
- Balanced approaches the middle or outer part of its installed direct-fire
  band, accepts a close threat already upon it, and returns to its assignment
  when that interruption ends. It does not require special allied geometry.
- Four different roles in one lance execute per member. No shared squad goal
  turns every member into the role with the highest goal priority.
- Survival, rescue, and mission-command laws continue to outrank role manner.

## Deterministic evidence

- Focused tests cover deployed/effective/reset state, request validation,
  same-tick plan invalidation, sibling isolation, and mixed-role execution.
- A bounded doctrine evidence scene uses identical hardware, assignment,
  contact, and seed for the four doctrines. It records threat distance,
  signed position relative to an allied screen, and the effect of removing that
  support midway through the run.
- Existing Sirocco overwatch, assault assignment, mech morale, formation, and
  campaign-freeze tests remain green.

## Out of scope

- Persisting a new default from battle or adding the Mech Lab authoring flow.
- Player-authored destinations, targets, waypoints, or knowledge.
- New chassis, weapons, damage rules, morale tuning, or campaign inventory.
- Treating the UI label Tank as a new hardware family.

## Exit

Keep the durable contracts in `mechs-nouns.md`, `ai-nouns.md`, and
`ui-nouns.md`. Move this story to `shipped.md` and delete it after focused
automation, deterministic evidence, and the live battle interaction pass all
agree.
