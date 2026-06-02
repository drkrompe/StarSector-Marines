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

Story 18 shipped (2026-06-01): turret-emplacement area patrol. Turret
defender squads now run `GuardPost` → `GuardPostPatrol` (open-terrain
counterpart to `GarrisonPatrol`) — they wander an AABB box centred on the
post anchor with half-extent `squad.patrolRadius` instead of `HoldPost`'s
static 6-cell leash. Finally a live consumer for the per-tier
`DefensePostKind.patrolRadius`. See `stories/18-guardpost-area-patrol.md`.

Deliberate compound capture shipped (2026-06-01): `ConquestCommand` no
longer relies on the strip-local "compound is behind my front line" ripe
heuristic — which left objectives uncaptured while squads swarmed
search-and-destroy after convoy drops. New map-global
`assignCompoundCaptures` pass peels a capped detachment (1 squad, 2 for a
multi-room keep) onto each compound the moment it's **uncontested**
(judged over `GarrisonArea` AABB-gated rooms, so an exterior defender
doesn't block it); contested compounds only commit an already-adjacent
squad; everyone else stays on the strip search-and-destroy push. See
`roadmap/conquest/complete/deliberate-compound-capture.md`.

## Immediate next

00. **Threat-scored engagement leash** (`stories/19-threat-scored-engagement-leash.md`)
    — replace the binary `haltOnContact` gate in the zone-push family with a
    per-tick threat-scored commit-vs-press leash that self-releases (generalize
    `GuardPostPatrol.computeLeash` to the advance axis). Shots-of-opportunity
    down-payment already shipped (`8d33ca5` — `closestEnemyInAttackRange`).
    Cheap slice (omniscient force-ratio + retreating-discount + astride-route)
    is independently shippable; full honest-threat version parked behind the
    perception layer (story 15).

0. ~~**Garrison zone-clear scoping + multi-building garrison**~~ — **shipped**
   (`2b31af4`, `4cebcb8`, `87cf47c`, `94e3060`, `8e73d0d`, `7fc2415`, `08ef31e`).
   The AABB scoping fix, the 0a exterior-clear guard, the richer
   `GarrisonCompound`/`GarrisonPatrol` multi-building garrison (marine holder +
   defender base garrison, primary-node coordination), and 0b — the captured
   compound is held by the dedicated born-holding garrison squad shipped in by
   `CompoundGarrisonSystem` (the capturing assault squad keeps advancing). See
   `stories/17-garrison-zone-clear-scoping.md` § What shipped. Small follow-ups
   remain (courtyard move-to-engage; SecureCompound off-path rooms;
   release-when-quiet) in that doc's § Remaining follow-ups.
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
- `stories/19-threat-scored-engagement-leash.md` — commit-vs-press during the
  zone-push advance as a per-tick threat-scored leash with auto-release
  (shots-of-opportunity slice shipped `8d33ca5`; scored leash designed,
  generalizes `GuardPostPatrol.computeLeash`)
- `complete/` — sealed shipped work (Stage 1 tasks 01–09, Stage 2
  foundation 11, mech Stage 1 14)
