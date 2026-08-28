# Silent Colony expedition branches

Status: DRAFT — stabilize and explain the archive and survivor branches of the blind expedition.

Written: 2026-08-24

Updated: 2026-08-27 — grouped as an Extraction-family adapter while retaining
stable expedition branches and a separately undecided security-director shape.

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

## Exit

Fold durable expedition-branch vocabulary and laws into `extraction-command.md`, add the
story to the mission-command shipped ledger, and delete it when implementation and live
acceptance ship.
