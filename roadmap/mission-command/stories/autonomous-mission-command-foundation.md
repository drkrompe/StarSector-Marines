# Autonomous mission-command foundation

Status: IN PROGRESS — the shared frame/plan/commit envelope, paired Conquest migration, common diagnostics, spawn-time ownership, disclosure boundary, directive stability, and assignment-writer ownership are implemented; live acceptance remains.

Written: 2026-08-25

Updated: 2026-08-28 — Opening Operations has migrated to paired frozen command;
the remaining Silent Colony legacy planner submits through scoped directive
control. Foundation acceptance still uses Conquest as the production reference.

Read `mission-command-nouns.md`, `conquest-command.md`, `ai-nouns.md`,
`conquest-nouns.md`, and `battle-fixtures-nouns.md` before
implementing this story.

## Intent

Make autonomous side command the normal battle baseline. Both sides should
plan from frozen, faction-honest inputs, publish inspectable directives, and
progress the mission without depending on player micromanagement. Reuse the
machinery across missions while leaving tracks, sectors, sites, corridors,
branches, objective phases, and convergence with the mission strategy that
owns them.

## Scope

### Current migration boundary

Conquest is the only production mission being advanced through this story now.
Other missions keep whatever assignment behavior they have today: an existing
legacy commander where one is already installed, or the generic no-op/ambient
behavior where none is installed. They are not acceptance blockers for the
Conquest reference implementation, and this slice must not silently absorb
their squads into a generic autonomous strategy. Mission-specific migrations
remain separate future stories.

- Introduce a perspective-specific command frame containing immutable own-force
  state, faction-local influence, legally disclosed mission state, public
  topology queries, current directives, and frozen doctrine.
- Make that frame and its narrow topology surface the only production strategy
  input. A strategy may not receive or retain `BattleView`; a mission-owned,
  perspective-specific disclosure adapter is the sole path for objective facts
  to enter the frame.
- Split the slow command pulse into freeze, independent side planning,
  validation/commit, snapshot publication, and squad-replan handoff. No side's
  committed decisions may change the frame used by the other side on the same
  pulse.
- Introduce explicit command-pool ownership. Garrison, payload, reinforcement,
  scripted, and other externally owned squads do not enter a mission strategy's
  pool until their owning system hands them off.
- Route every assignment writer through the ownership registry/arbiter or an
  explicit handoff, including garrison, payload, reinforcement, scripted, and
  future intervention systems. Form-up suspends execution of an owned
  directive; it does not become an assignment writer.
- Wrap each proposed `ObjectiveAssignment` in a directive that records issuer,
  authority, reason, issue tick, target semantics, lease/stability state, and
  supersession reason.
- Publish a generic immutable commander snapshot with side, strategy, phase,
  command/influence tick, force-pool counts, reserves, objective summaries, and
  per-squad directives, including explicit unassigned and rejected-proposal
  reasons. Preserve typed mission detail rather than flattening it into generic
  tracks or sectors.
- Make the selected-squad panel and JSON dump consume the same published
  directive. Debug tooling may inspect both perspectives; ordinary player
  presentation must not gain enemy command knowledge through this seam.
- Audit infantry and mech consumers for every assignment kind that migrated
  commanders can issue.

## Player-intervention seam

Define the authority boundary and data seam, but do not build direct-order UI
in this story. A future intervention may request a legal focus, rally,
commit-reserve, or fallback with a bounded lease. Validation must reject hidden
target disclosure, objective-law bypass, and reassignment of externally owned
actors. Existing command powers continue to operate as immediate tactical
interventions.

## Acceptance

- [x] Two installed side strategies plan from frozen perspective frames and
  commit only after both proposals exist; reversing dispatch order produces the
  same directives and snapshots.
- [x] A strategy cannot read a hidden hostile identity or live cell except
  through its faction-local influence/contact evidence or an explicitly
  authorized mission fact.
- [x] Assignment provenance prevents mission command from overwriting born
  garrisons, payload guards, reinforcement/counterattack ownership, or a valid
  leased intervention.
- [x] Directive stability spans at least one useful squad-plan interval unless
  objective completion, squad loss, unreachable context, ownership handoff, or
  a legal intervention invalidates it.
- [x] Conquest attacker and defender migrate without losing their typed front
  snapshot, asymmetric authority, or existing automated acceptance.
- [x] A synthetic topology-agnostic paired fixture proves the envelope is not
  coupled to directional tracks; the first non-Conquest production migration
  remains a later mission story.
- [x] Selected-squad and dump output agree on perspective, strategy, phase,
  assignment, reason, authority, issue tick, and lease/supersession state.
- [x] Deterministic tests cover empty command pools, disconnected topology,
  invalid targets, simultaneous side changes, and assignment handoff.

## Constraints

- Mission objectives retain progress, completion, victory, and disclosure
  authority. The command layer cannot infer or replace objective truth.
- Faction doctrine is a later weight profile over competent legal choices; it
  must not disguise missing baseline command behavior.
- The current 2.5-second commander pulse remains the initial cadence. Add
  directive hysteresis before considering a faster global decision loop.
- No universal frontline, track, sector, or mirrored-attacker abstraction.
- No direct player-order controls, faction personality tuning, or RAID/generic
  Extraction commander until their prerequisite stories are ready.

## Exit

Fold the shipped frame, pool, directive, arbiter, snapshot, and intervention
laws into `mission-command-nouns.md`, add this story to `shipped.md`, and delete it when the
foundation and Conquest reference migration ship.
