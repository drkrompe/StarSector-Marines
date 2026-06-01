# AI — Next Session

## Where we are

Stage 2 tactical stories are mostly shipped (Slices 1–3.5 + partial
Slice 6). All three marine-side commanders now ship: `SabotageCommand`
(objective-cluster), `ConquestCommand` (lateral-strip), and
`AssaultCommand` (sector-grid sweep). Mech GOAP Stage 1 is complete
(two roles, morale, break-contact).

`AssaultCommand` shipped (2026-05-27). Sector-grid partition with
non-sticky assignment, implicit convergence via load-balancing.

Story 17 shipped (2026-06-01): garrison zone-clear scoping + the
`GarrisonCompound`/`GarrisonPatrol` multi-building garrison (marine
captured-compound holder + defender base garrison) + the 0a/0b
command-side fixes. `GarrisonArea` is now the reusable size+containment
gate; `TacticalNode.compoundBounds` persists the gen-time compound
footprint into battle.

## Immediate next

0. ~~**Garrison zone-clear scoping + multi-building garrison**~~ — **shipped**
   (`2b31af4`, `4cebcb8`, `87cf47c`, `94e3060`, `8e73d0d`, `7fc2415`). The AABB
   scoping fix, both command-side mirror bugs (0a exterior-clear guard, 0b
   `HOLD_NODE` gate), and the richer `GarrisonCompound`/`GarrisonPatrol`
   multi-building garrison (marine `HOLD_NODE` holder + defender base garrison,
   primary-node coordination) all landed. See
   `stories/17-garrison-zone-clear-scoping.md` § What shipped. Two small
   follow-ups remain (courtyard move-to-engage; SecureCompound off-path rooms
   — now low priority) in that doc's § Remaining follow-ups.
1. **Perception cheap wins** (`stories/15-perception-and-influence.md`
   § Near-term cheap wins) — threat-direction cover scoring, ranged LoS
   variant, threat-set gate on `HAS_LOS_TO_TARGET`. Lay the data-flow
   seam for the full perception system without committing to it.
2. **Slice 4 (Stories C + F)** — per-member assignment + bounding
   overwatch + objective rush under fire. F may collapse into J (the
   cordon goal hierarchy already covers planter-under-fire). See
   `stories/10-tactical-stories.md` § Slice 4.
3. **Slice 5 (Story H)** — last-stand `HoldPosition` on `MUST_HOLD`
   tactical nodes. Small scope.
4. **Story E (mech-screened advance)** — remaining piece of Slice 6.
   Blocked on mech GOAP Stage 2 work (`stories/13-mech-goap.md`).

## Parked but design-complete

- **Full perception & influence** (`stories/15-perception-and-influence.md`)
  — squad belief map + commander heatmap. Queues after cheap wins land.
- **Commander improvements** (`stories/12-squad-of-squads.md` §
  Improvement path) — contour-aware target picking, cross-strip
  reallocation, defender-side commanders. All gated on doc 15.
- **Mech GOAP Stage 2** (`stories/13-mech-goap.md`) — Recon + Assault
  roles, dynamic re-assignment from commander tier.

## Key files

- `overview.md` — full architecture + staging overview
- `stories/10-tactical-stories.md` — story bank + slicing + primitives map
- `stories/12-squad-of-squads.md` — commander tier design
- `stories/13-mech-goap.md` — mech planner design (Stage 2 future)
- `stories/15-perception-and-influence.md` — perception + influence map
- `stories/16-assault-command.md` — assault commander design (shipped)
- `stories/17-garrison-zone-clear-scoping.md` — AABB-gated SecureCompound
  scoping + `GarrisonCompound`/`GarrisonPatrol` multi-building garrison + 0a/0b
  command fixes (all shipped; `GarrisonArea` is the reusable gate primitive,
  `TacticalNode.compoundBounds` the persisted footprint)
- `complete/` — sealed shipped work (Stage 1 tasks 01–09, Stage 2
  foundation 11, mech Stage 1 14)
