# Silent Colony expedition branches

Status: IN PROGRESS — stable frame-only Marine branches, spawn ownership, and
diagnostics are implemented; headless mission evidence and live acceptance remain.

Written: 2026-08-24

Updated: 2026-08-28 — migrated the Marine expedition to frozen objective
disclosure with stable archive/survivor membership and explicit completion
handoff; automated opposition remains scripted.

Read `mission-command-nouns.md`, `extraction-command.md`,
`campaign-event-nouns.md`, and
`autonomous-mission-command-foundation.md` before planning this story.

## Intent

Keep the expedition visibly divided between recovering the physical archive and
reaching or escorting survivors without assigning branch ownership by lowest
squad id each command tick. Branches should remain stable, react to losses and
completion, and reinforce one another when one objective no longer needs its
original force.

## Scope

- Publish stable archive and survivor branch membership, objective state,
  believed pressure, and assignment reason.
- Choose initial branch membership deterministically from route cost and squad
  capability, then preserve it until loss, completion, or a bounded emergency
  rebalance.
- Define how the archive branch rejoins survivor escort after recovery and how
  the survivor branch can reinforce archive recovery when no active survivor
  cohort remains.
- Expose branch state through selected-squad presentation and the state dump.

## Constraints

- Silent Colony branches are objective roles, not Conquest tracks. They do not
  imply territory, a shared forward edge, or hidden knowledge of the site.
- The campaign event continues to own frozen stakes and independent terminal
  survivor/archive reports. Battle command may not infer loot or moral outcome.
- Local squad doctrine retains authority over contact reaction within each
  branch's mission assignment.
- Stabilize and publish the marine branches before deciding whether opposition
  remains scripted, gains a bounded security-network director, or mixes both.
  Shared diagnostics do not require fabricating a mirrored squad commander.

## Implemented slice

- The Marine planner consumes only a frozen own-force frame, faction-local
  influence, public topology, the survivor cohort projection, and the sealed
  archive projection. Exact automated-defense placement is not disclosed.
- Landing shuttles carry `MISSION_COMMAND` ownership into every sortie. Active
  player leases and payload, garrison, reinforcement, or scripted ownership
  remain outside the expedition pool; an expired player lease returns on the
  first command pulse.
- Initial membership assigns one reachable squad to each active branch when
  force size permits. Route length selects the better-positioned squad and
  surviving member count is the currently available capability proxy; the
  frozen command row does not yet expose loadout capability.
- Membership persists across ordinary pulses. Loss of a branch member repairs
  the empty branch, archive recovery moves that branch into survivor escort,
  and an exhausted survivor cohort moves its branch into archive recovery.
- The published command picture, selected-squad panel, and squad dump expose
  both objective states, branch membership and assignment reasons, targets,
  and only faction-known pressure.

## Acceptance

- [x] Repeated pulses preserve archive and survivor membership while both
  objectives remain active.
- [x] Route cost and surviving strength deterministically seed the branches.
- [x] Archive completion and survivor exhaustion transfer squads without a
  planless pulse.
- [x] External ownership and active intervention leases are preserved, while
  expired intervention returns to expedition command.
- [x] Hidden Defender placement cannot change the Marine plan without entering
  Marine belief.
- [x] Selected-squad and dump output explain branch, reason, objective state,
  pressure, and target.
- [ ] Add deterministic mission-duration evidence under the existing
  argument-selected Extraction harness.
- [ ] Live-accept branch pacing, archive assault behavior, survivor escort, and
  completion handoff.

## Exit

Fold durable expedition-branch vocabulary and laws into `extraction-command.md`, add the
story to the mission-command shipped ledger, and delete it when implementation and live
acceptance ship.
