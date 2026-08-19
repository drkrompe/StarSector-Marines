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

Story 19's cheap slice shipped (2026-08-19, `14d646a`): `EnterZone` no
longer treats contact as a binary halt. A per-tick route threat score combines
local force, retreat posture, and distance from the advance axis; hysteresis
selects press versus commit, committed members prosecute inside a bounded
off-axis leash, and falling threat releases the squad back onto its objective
route automatically. Dump schema v6 exposes the full decision. See
`complete/19-threat-scored-engagement-leash.md`.

Story 15's four tactical cheap wins were already shipped (`5f12ac03`,
`09bf4f70`, `6dd1e63c`, `04e3f814`): directional fallback cover,
speed-scaled fallback scans, bounded LoS, and the interim last-seen threat-set
gate. The full squad-belief + commander-influence layer remains parked.

## Immediate next

1. **Slice 4 (Stories C + F)** — per-member assignment + bounding
   overwatch + objective rush under fire. F may collapse into J (the
   cordon goal hierarchy already covers planter-under-fire). See
   `stories/10-tactical-stories.md` § Slice 4.
2. **Slice 5 (Story H)** — last-stand `HoldPosition` on `MUST_HOLD`
   tactical nodes. Small scope.
3. **Story E (mech-screened advance)** — remaining piece of Slice 6.
   Blocked on mech GOAP Stage 2 work (`stories/13-mech-goap.md`).

## Parked but design-complete

- **Full perception & influence** (`stories/15-perception-and-influence.md`)
  — squad belief map + commander heatmap. Tactical down-payments are shipped;
  the ground-truth threat reads in guard-post and objective-advance leashes are
  explicit swap sites when belief lands.
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
- `complete/19-threat-scored-engagement-leash.md` — shipped commit-vs-press
  zone advance with off-axis leash, hysteresis, auto-release, diagnostics, and
  the belief-layer swap boundary (`8d33ca5`, `14d646a`)
- `complete/` — sealed shipped work (Stage 1 tasks 01–09, Stage 2
  foundation 11, mech Stage 1 14)
