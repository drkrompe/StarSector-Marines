# Mission-command stories

Status: ACTIVE — shared architecture and mission-specific migrations are tracked here; each mission keeps its own strategy and prerequisites.

Written: 2026-08-27

Updated: 2026-10-14 — mortar use is a squad behaviour (see `phased-assault.md`, Mortar sections); muster with plans is standing behaviour and the trickle is a mission's own declaration; evidence on the conquest trial ground.
snapshots are folded into the canonical model; open work remains below.

Read `mission-command-nouns.md`, then the relevant mission design before an
implementation story.

## Shared architecture

| Story | State | Intent |
|---|---|---|
| `shared-mission-objectives.md` | IN PROGRESS | Adapt remaining mission families to shared named map objectives, progress/blockers and friendly squad activity; live-accept Extraction and Colony. |
| `command-frame-costs.md` | IN PROGRESS | Attribute command-frame freeze and remove redundant work without weakening perspective isolation. |
| `autonomous-mission-command-foundation.md` | IN PROGRESS | Close assignment-writer ownership and live acceptance around the shared frame/plan/commit contract. |
| `commander-trace-and-balance-harness.md` | IN PROGRESS | Replace construction-only Conquest evidence with launch-faithful fixtures and later compare bounded intervention. |
| `player-command-interventions.md` | PLANNED | Tactical squad orders lease through the arbiter and hand back (shipped 2026-09-01). Add the strategic priority/rally/reserve/fallback vocabulary, refuse a lease over a higher authority, and bound pacing. |
| `target-faction-command-doctrine.md` | PLANNED | Bias established legal choices with frozen faction profiles after paired baselines exist. |

## Conquest

Design: `conquest-command.md`

| Story | State | Intent |
|---|---|---|
| `front-command-and-keep-convergence.md` | IN PROGRESS | Live-accept tracks, cross-track support, front staging, and culminating convergence. |
| `defender-track-mobilization.md` | IN PROGRESS | Live-accept belief-honest bounded patrol mobilization on the shared tracks. |
| `defender-convoy-deployment-and-handoff.md` | IN PROGRESS | Live-accept rear-edge deployment and commander ownership of convoy relief squads. |
| `phased-assault.md` (design) slice 1 | BUILT, STANDING | Foothold, muster and assault are in (standing behaviour; a mission may declare `AssaultStyle.TRICKLE`); on the trial ground it takes the objective on 3 of 3 seeds with the fort on the lane against 2 of 3 for the trickle, and wins more (`phased-assault.md`, Evidence lives on the trial ground); readiness counts nests and the gate, lanes keep half their squads, a short muster stands down. On the four fort fixtures the control still holds more compounds on three, the owner turned it on regardless (2026-10-14); open: companies standing at a rally with nobody at it, and a muster with a lance in play on Conquest.
| `phased-assault.md` (design) slice 2 | BUILT | Call surface, allowance and delivery are built (`mech-drop` scene); the muster calls it for a short company (`ConquestCommand.afterCommit`); a Conquest comparison with a lance in play is open. |
| `phased-assault.md` (design) slice 3 | BUILT, ON WITH THE MUSTER | `FortPlan.approaches()` and the `FortAssault` executor (base of fire, entry, objective, hold) drive a committed company; the `assault-muster` scene compares control, slice 1 and plan. It takes forts the control never touches; infantry rifles do not kill nests, so its value waits on machines. Capability profiles and role assignment pick the way in by what the company can staff and publish a shortfall; breaches are fired at a wall (cannon mech: distance breach, charge sections: close breach), `breach-cannon` and `breach-charges` loops; standoff demolition, tuning and a Conquest comparison are open.
| `phased-assault.md` (design) mortar team and air strike | BUILT, ON WITH THE MUSTER | Plans assign a fire mission to a tube squad (base of fire, standoff placement; standing missions for lane sections) and request air strikes through an observer under the pool's risk ceiling; `assault-muster` loops `plan-mortar`, `plan-air`, `plan-mortar-air`, `combined-arms`. Conquest declares a mission squad kit (mortar teams, observers) and an AI-directed marine pool; strikes are used heavily on the fort fixtures, tubes rarely; a dead nest is learned from sight and a tube is re-crewed. No fort falls; muster leads on one fixture of three. A marching squad with a fire mission halts a four-person crew to work the tube while the rest is leashed (30-second window, extended while the tube is engaged, capped at 90; `battle.mortar.*` dials): on 36 trial seeds per placement it raises mission rounds (24 to 57 on the lane, 39 to 73 off it) and leaves battles level, while the broader belief triggers cost battles and are off. Open: sections are rare (15 of 72 runs); mortars still kill almost no nests (the air pool does); tuning the kit; campaign acquisition cost. |

## Sabotage

Design: `sabotage-command.md`. The paired migration is shipped; its retired
stories are recorded in `shipped.md`. Add another story only for a new bounded
capability or evidence gap.

## Assault

Design: `assault-command.md`

| Story | State | Intent |
|---|---|---|
| `assault-search-sector-picture.md` | IN PROGRESS | Finish live and canonical-duration acceptance of attacker search. |
| `assault-area-defense-command.md` | IN PROGRESS | Finish live and canonical-duration acceptance of defender area security. |
| `opening-operation-objective-command.md` | IN PROGRESS | Adapt the shared architecture to finite preserve/secure opening scenarios; real COMMS/depot place legibility and live acceptance remain. |

## Raid

Design: `raid-command.md`

| Story | State | Intent |
|---|---|---|
| `raid-objective-and-command-contract.md` | IN PROGRESS | Canonical-duration and live-accept the shipped primary-target objective, paired command duel, and diagnostics. |

## Extraction

Design: `extraction-command.md`

| Story | State | Intent |
|---|---|---|
| `generic-extraction-corridor-command.md` | IN PROGRESS | Ship and accept stable Marine payload/escort/screen command for generic Extraction. |
| `generic-extraction-interdiction-command.md` | IN PROGRESS | Ship and accept bounded source-alarm security and belief-driven defender interdiction. |
| `silent-colony-expedition-branches.md` | IN PROGRESS | Add headless/live acceptance for the stable Marine archive and survivor branches before choosing the opposing director shape. |
