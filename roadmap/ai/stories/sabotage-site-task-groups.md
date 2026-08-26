# Sabotage site task groups

Status: IN PROGRESS — migrate Marine Sabotage command to the autonomous
frame/plan/commit path and prove the first named-site command picture.

Written: 2026-08-24

Updated: 2026-08-26 — landed the named-site construction contract, autonomous
Marine allocator, planter/retriever preservation, fixture capture, and shared
panel/dump/map/trace picture. Bounded headless metrics and the deeper kit-loss
transition matrix remain before this attacker story ships.

Read `ai-nouns.md`, `autonomous-mission-command-foundation.md`,
`commander-trace-and-balance-harness.md`, and `battle-fixtures-nouns.md` before
implementing this story.

## Intent

Make Marine Sabotage command a complete second autonomous-command vertical
slice organized around named charge sites rather than Conquest tracks. Every
launch exposes exactly three legal targets. The commander keeps a planter and
its security understandable as one site task group, coordinates replacement
kit recovery after a planter loss, and redistributes useful squads without
reading hidden defenders or overriding unit-level planting authority.

This story establishes the attacker and the reusable named-site diagnostic
seam. `sabotage-site-defense-command.md` remains the explicit follow-on that
turns the mission into a paired command duel.

## Scenario and site contract

- A production Sabotage battle contains exactly three distinct
  `ChargeSiteObjective`s. Each has a stable, unique site identity and readable
  name derived deterministically from its authored place.
- Every site cell and objective zone is walkable and reachable from a Marine
  landing approach. Scenario construction deterministically retries or fails
  loudly when it cannot satisfy all three targets; it never launches a partial
  one- or two-site mission.
- `ChargeSiteObjective` remains the authority for target cell, uninterrupted
  plant progress, completion, and Marine victory. Command receives only a
  frozen, legally disclosed copy of those facts.
- Site identity survives frame, plan, snapshot, dump, trace, and fixture replay
  so evidence never has to infer a target from a mutable list position or a
  display label alone.

## Autonomous command migration

- Replace the legacy live-`BattleView` `SabotageCommand` with an
  `AutonomousMissionCommand` using a Sabotage-specific frame, stateless
  disclosure, typed plan detail, and post-commit snapshot.
- The common frame supplies own squads, faction-local influence, topology, and
  current directives. The Sabotage disclosure adds only the three site facts
  and the unit-task facts needed to recognize a live planter, an active dropped
  kit, and its current retriever.
- Build an explicit Marine command pool. A squad or member under stronger
  garrison, payload, reinforcement, scripted, planter, or kit-retrieval
  authority is preserved or handed off through the assignment arbiter rather
  than overwritten through `Squad.assignedObjective`.
- Publish a proposal for every eligible Marine squad on each pulse, including
  an explicit retained, unassigned, or rejected reason. Small score changes do
  not reset useful site affinity inside the common directive-stability window.

## Site task groups

- Publish one task-group state for each unfinished site. It identifies the
  planter-bearing squad when one exists, assigned security squads, inbound
  reinforcements, believed pressure, objective progress, and why the group
  needs or is releasing force.
- Establish initial affinity deterministically from route cost, squad
  capability, planter/kit context, and current coverage. Spread useful force
  across all unfinished reachable sites before adding surplus security to a
  safer or already well-covered site.
- Distinguish `PLANTER`, `SECURITY`, and `REINFORCING` group roles. The planter
  squad keeps its unit-level charge objective while command publishes and
  preserves the surrounding site context; security clears or holds the site's
  approach without competing with the planter's mission goal.
- Rebalance only for a typed reason: site completion, planter loss, active kit
  recovery, squad loss, unreachable context, or critical believed pressure.
  Completing a site releases or redistributes its security deterministically.
- Pressure and reinforcement need use Marine beliefs and own-force state only.
  Lack of a hostile belief is not proof that a site is clear.

## Kit-retriever coordination

- `EquipmentDropSystem` remains the authority that selects an individual
  retriever, assigns the dropped kit, and promotes the carrier to planter on
  pickup. Mission command neither chooses that member nor duplicates its task.
- Disclosure identifies the dropped kit's site and the living retriever's
  squad. Command keeps that squad affiliated with the same site, marks the
  group as recovering its planter capability, and allocates security around
  the recovery/approach instead of retargeting the squad to another site.
- Retriever death or invalidation returns the group to an explained unsupported
  state until `EquipmentDropSystem` selects a replacement. Successful pickup
  changes the published role from recovery/reinforcement to planter support
  without losing site identity or creating a second competing objective.
- Mech eligibility for infantry kit retrieval remains owned by
  `mech-kit-retriever-exclusion.md`; this story coordinates the result and does
  not broaden who may carry a kit.

## Observability and headless evidence

- Add the smallest typed mission-detail presentation/serialization seam needed
  for a second autonomous mission. Keep the generic commander envelope,
  directive provenance, and trace cadence shared; keep site geometry, roles,
  progress, and reasons Sabotage-owned rather than adding another Conquest-only
  type branch at every consumer.
- The selected-squad panel, squad dump, and a selectable Marine Sabotage map
  picture show the same site identity, group role, objective progress,
  planter/retriever state, believed pressure, assignment, and reason from the
  published snapshot.
- Perspective trace rows serialize only the Marine snapshot. Authoritative site
  progress/completion belongs to labelled neutral referee events and is never
  fed back into command.
- Add a versioned `SabotageBattleFixture` for the stable inputs already accepted
  by the production scenario factory, register it with `BattleFixtureJson`, and
  capture it in `MissionLaunch` so the ordinary launch overlay and the headless
  runner rebuild the same battle path.
- Add a bounded forced-serial zero-input run that is byte-stable across two
  replays and reports site coverage, planter/retriever transitions, directive
  churn, site progress/completion, casualties, duration, and terminal or
  timeout outcome. This is behavioral evidence, not a balance gate and not a
  substitute for the paired defender follow-on.

## Acceptance

- [x] Supported seeds either construct exactly three uniquely identified,
  named, reachable charge sites or fail after a bounded deterministic retry;
  no playable Sabotage battle contains fewer targets.
- [x] Marine command runs through the autonomous frame/plan/commit path and has
  no production access to `BattleView`, hidden defender identities, or hidden
  defender cells.
- [ ] Initial assignment gives every unfinished site a planter or an explained
  recovery need plus security coverage before surplus squads concentrate, and
  repeated pulses preserve stable site affinity.
- [ ] A live planter continues to execute its unit-level charge objective while
  its squad's published command context names the same site and security role;
  the arbiter records ownership instead of a direct assignment overwrite.
- [ ] Planter death, dropped-kit recruitment, retriever death/replacement, and
  successful pickup produce deterministic, site-consistent group transitions
  without commander/retriever target contention.
- [x] Site completion releases and redistributes only attacker-command-owned
  squads; externally owned and in-progress kit/planter work remains intact.
- [x] Selected-squad presentation, squad dump, map picture, and perspective
  trace agree on site, group role, directive, reason, and planter/retriever
  state. Neutral progress evidence is clearly separated from Marine belief.
- [ ] Sabotage construction and launch fixtures round-trip through the versioned
  codec, delegate to `BattleSetup.createSabotage`, and reproduce commander/site
  shape. The canonical bounded replay is byte-stable and labels timeout rather
  than inventing a winner.
- [ ] Existing charge planting, cordon, kit recovery, launch overlay, and
  ordinary Sabotage outcome tests continue to pass.

## Paired defender follow-on

This slice deliberately leaves defenders on their current authored
garrison/ambient behavior. It does not claim a complete autonomous command duel
or use ambient defenders as balance evidence. `sabotage-site-defense-command.md`
must consume the same stable site identities while publishing its own legal
alarms, guards, mobile reserve, and perspective-specific snapshot. Paired
freeze-order, no-input progression, and attacker-versus-defender balance
acceptance belong to that follow-on.

## Constraints

- Charge sites are named objectives, not lateral tracks, anonymous sectors, or
  a universal frontline. Group state ends or redistributes when its site
  completes.
- Squad command cannot author charge progress, complete an objective, select
  an individual planter/retriever, or reinterpret a missing enemy belief as
  clearance.
- Do not introduce defender reserve policy, alarms, or omniscient site response
  in this story.
- Do not tune weapons, force budgets, reinforcement budgets, shuttle pacing, or
  planting duration from one deterministic headless run.
- Do not fold these planned semantics into `ai-nouns.md` until they ship.

## Implementation plan

1. Enforce the three-site construction/reachability invariant and add stable
   site identity.
2. Add Sabotage construction/launch fixture capture and codec coverage.
3. Add the typed disclosure, frame, plan detail, and autonomous Marine strategy.
4. Integrate planter and kit-retriever ownership with site-group allocation and
   arbiter handoff.
5. Add site-detail panel, dump, map, trace, and deterministic headless evidence.
6. Run focused planting/kit/command/fixture tests and the ordinary suite, then
   capture the live observations that remain before shipping the attacker
   slice.

## Exit

Fold shipped attacker site-command laws into `ai-nouns.md`, add this story to
the AI shipped ledger, and delete it when the autonomous Marine vertical slice,
fixture, diagnostics, and evidence ship. Keep the paired defender story open
until Sabotage is a complete autonomous command duel.
