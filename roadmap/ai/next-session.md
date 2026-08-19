# AI — Next Session

## Where we are

Stage 2 tactical stories are mostly shipped (Slices 1–5 + partial
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
route automatically. The squad debug dump exposes the full decision. See
`complete/19-threat-scored-engagement-leash.md`.

Story 20 / Slice 4 shipped (2026-08-19, `b72819fc`): committed `EnterZone`
advances now split into stable two-team bounding overwatch. One half holds and
fires stanced while the other moves without firing to distinct, reachable
forward cover; arrival flips the roles, and Story 19's threat release restores
the objective route. Story F is also closed as already shipped by Story J's
planter/portal-holder cordon (`c54991d4`). See
`complete/20-bounding-overwatch.md`.

Story 21 / Slice 5 shipped (2026-08-19, `ef76c7ba`): fortress command posts
carry an explicit `mustHold` node flag. Their lone survivor selects the
MISSION-tier `HoldPosition` goal over `SurviveContact`, claims nearby
threat-facing cover inside the objective leash, holds and fires until killed,
and ignores structural fallback links. Ordinary broken garrisons now yield
cleanly to survival. See
`complete/21-last-stand-objective-camper.md`.

Story 22 / mech Stage 2A shipped (2026-08-19, `1ef74f23`): LOW defender
rosters contain no mechs, MEDIUM introduces one Bulwark, and HIGH uses
deterministic mission-scaled Bulwark/Hound/Sirocco groups without increasing
the old body-count ceiling. Hound now defaults to the faction-neutral ASSAULT
doctrine: it enters commander-assigned zones or advances on locally known
contact, fires while moving, stays inside an ordered zone, and still yields to
mech survival. See `complete/22-assault-mech-and-difficulty-roster.md`.

Story 23 / Story E shipped (2026-08-19, `0daf058e`): objective-advancing
infantry now selects a compatible same-faction ASSAULT mech, occupies distinct
threat-relative FOLLOW cells behind its live chassis, and switches to a short
two-sided firing FAN when the mech can engage. The formation follows the
moving unit rather than a terrain anchor and drops immediately back to normal
`EnterZone`/bounding behavior if the screen is lost. Existing direct-fire
unit-body interception supplies mechanical moving cover. Squad dumps expose
the full unversioned screen state. See `complete/23-mech-screened-advance.md`.

Story 24 / Story I shipped (2026-08-19, `43c619ff`, `8888e6f8`): generic
infantry pursuit now rejects targets that require movement into a two-plus
hostile cluster, switches to a visible isolated alternative when possible,
and otherwise latches a faction-neutral `HoldEngagementLine` goal rendered as
`Overwatch`. Covered members plant; exposed members settle laterally/backward
into wall or doodad cover. `Approach` and out-of-range `Engage` paths are hard
clipped to the squad-cohesion radius, while mission-authored movement keeps its
own leashes. See `complete/24-engagement-discipline.md`.

Story 15's four tactical cheap wins were already shipped (`5f12ac03`,
`09bf4f70`, `6dd1e63c`, `04e3f814`): directional fallback cover,
speed-scaled fallback scans, bounded LoS, and the interim last-seen threat-set
gate. Story 25's first perception vertical now ships too (`db69ed73`): direct
LOS records every hostile into a decaying, id-keyed per-squad belief; GOAP
target/LOS/range predicates and Story 24's formation-density hold consume that
belief; the legacy last-seen point is a derived compatibility projection; and
selected-squad overlays plus unversioned dumps expose the contacts. Audio
detection, cross-squad sharing, and commander influence remain parked.

## Immediate next

1. **Tactical playtest pass** — exercise Stories 19–25 together: objective
   press/commit, bounding, last stands, difficulty-scaled assault mechs,
   mech-screen formations, and clustered-runner release into covered
   overwatch. Confirm magenta selected-squad contact ghosts refresh, remain at
   their last observed cells through LOS loss, and fade out with alert decay.
   Tune the density threshold, three-cell cover-settle radius, and twelve-cell
   cohesion leash only from visible battle results.
2. **Next perception slice: noise-backed contact updates** — contract the
   `NoiseEvent` producer/bus and audio-detection vertical from
   `stories/15-perception-and-influence.md`. Preserve imperfect localization,
   confidence below direct LOS, faction-neutral behavior, and indirect-fire
   secrecy; do not jump to commander aggregation or the influence map.

## Parked but design-complete

- **Perception & influence remainder** (`stories/15-perception-and-influence.md`)
  — audio contacts, cross-squad sharing, and commander heatmap. Direct squad
  belief is shipped; ground-truth threat reads in guard-post and
  objective-advance leashes remain explicit later swap sites.
- **Commander improvements** (`stories/12-squad-of-squads.md` §
  Improvement path) — contour-aware target picking, cross-strip
  reallocation, defender-side commanders. All gated on doc 15.
- **Mech GOAP Stage 2 remainder** (`stories/13-mech-goap.md`) — Recon and
  broad dynamic role re-assignment. Assault shipped in Story 22.

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
- `complete/20-bounding-overwatch.md` — shipped two-team leapfrog layered onto
  Story 19's committed `EnterZone` branch (`b72819fc`); also records Story F's
  prior closure in the sabotage cordon (`c54991d4`)
- `complete/21-last-stand-objective-camper.md` — shipped explicit must-hold
  node authoring, the lone-survivor `HoldPosition` mission override, and its
  cover-aware hold refinement (`ef76c7ba`, `780ea91f`)
- `complete/22-assault-mech-and-difficulty-roster.md` — shipped deterministic
  risk-scaled mech profiles plus the faction-neutral ASSAULT point doctrine
  (`1ef74f23`)
- `complete/23-mech-screened-advance.md` — shipped faction-neutral infantry
  follow/fan geometry around a moving ASSAULT mech plus physical chassis
  interception (`0daf058e`)
- `complete/24-engagement-discipline.md` — shipped density-gated pursuit,
  covered Overwatch hold, release triggers, hard generic pursuit cohesion, and
  unversioned diagnostics (`43c619ff`, `8888e6f8`)
- `complete/25-direct-squad-belief.md` — shipped direct-LOS contact memory,
  linear decay, belief-backed GOAP predicates and Story 24 density, legacy
  last-seen projection, overlay, and unversioned dump diagnostics (`db69ed73`)
- `complete/` — sealed shipped work (Stage 1 tasks 01–09, Stage 2
  foundation 11, mech Stage 1 14)
