# Player command interventions

Status: PLANNED — the tactical squad orders and their scene have shipped; the strategic request vocabulary follows a competent zero-input baseline and the commander trace harness.

Written: 2026-08-25

Updated: 2026-09-01 — the player's tactical orders (attack move, secure
compound, defend area) ship as arbiter leases with `PlayerOrderScene` as their
acceptance; what remains is the strategic request vocabulary, refusing a lease
over a higher authority, and pacing.

Read `mission-command-nouns.md`, `autonomous-mission-command-foundation.md`,
`commander-trace-and-balance-harness.md`, and `command-powers-nouns.md` before
planning this story.

## Intent

Let the player tip an autonomous battle through bounded strategic intent rather
than becoming the only source of competent movement. Direct intervention should
change priority or tempo, remain explainable, and expire cleanly back into
mission command.

## Scope

- Define a small legal request vocabulary such as prioritize objective, rally,
  commit reserve, focus support, or fallback.
- Validate requests against own-side knowledge, mission law, command-pool
  ownership, navigation, and directive authority.
- Lease accepted requests for a bounded duration or completion condition, then
  publish the handback to autonomous command. *Landed for tactical orders*:
  `AssignmentArbiter.lease` at `PLAYER_INTERVENTION` shelves the commander's
  directive and `endLease` / `expireLease` restore it on the tick the order
  ends; each kind's completion rule and lease length are rows in
  `OrderCatalog`, and the goal a player order wins with is the same row. A
  strategic request reuses that door.
- Define activation pacing, maximum concurrent leases, and cost authority,
  including whether strategic requests consume the existing command-point
  resource or a distinct bounded budget.
- Surface acceptance, rejection, remaining lease, supersession, and resulting
  directive in the own-side commander snapshot and UI. The shelved directive
  is readable through `BattleView.getShelvedSquadDirective` and the squad
  state dump shows the mission and the player's order side by side; nothing
  in the production UI shows either yet.
- Compare intervention results with the same fixture's zero-input trace. At
  scene scale this exists: `PlayerOrderScene` plays the click beside the same
  world with nobody clicking, under `sceneEvidence`. The fixture-scale
  comparison against a Conquest trace is still open.

## Acceptance

- [ ] An accepted intervention changes a legal own-side priority without
  disclosing or targeting a hidden hostile.
- [ ] Garrison, payload, scripted, and reinforcement-owned squads cannot be
  stolen by a lower-authority player request. **Open, and now a known gap
  rather than an unknown**: `AssignmentArbiter.lease` shelves whatever
  directive stands without consulting the ownership check that commander
  proposals go through, so a click on a payload escort or a garrison leases
  over `PAYLOAD` or `GARRISON`; `SquadMoveOrderSystem` filters the squad by
  type and shelter, not by who owns it. Decide whether the order system
  refuses before leasing or the arbiter refuses the lease, and make the
  refusal name the authority that held the squad.
- [ ] Invalid or unreachable requests fail visibly without clearing the current
  competent directive. The tactical half holds: an unreachable click is
  rejected and the standing order is untouched, pinned in
  `SquadMoveOrderSystemTest`. A strategic request's rejection surface is
  open.
- [x] Expiry, completion, cancellation, or hard invalidation returns authority
  to mission command without a planless/ghost-path interval. `PlayerOrderScene`
  is the evidence: the handback tick, the mission replanned on that same tick,
  and zero plan-less ticks across the run.
- [ ] Cooldown, concurrency, and cost rules prevent repeated requests from
  sustaining permanent manual override. Tenure is bounded today: an order that
  can finish holds a lease of `OrderCatalog.LEASE_TICKS` (two minutes of
  ship's time) and a re-click buys another interval rather than tenure, while
  a defend area stands until superseded or withdrawn. No cooldown,
  concurrency cap, or cost exists.
- [ ] Existing command powers remain immediate tactical interventions and are
  not forced into the leased strategic-request model.

## Constraints

- Do not add raw writes to `Squad.assignedObjective` from presentation code.
  The tactical squad move now goes through the arbiter as a lease at
  `PLAYER_INTERVENTION`, so the click path is already provenanced; a strategic
  request must arrive the same way rather than reopening a side channel.
- Do not tune missions around constant player order throughput.
- Faction doctrine remains independent of player intent.

## Exit

Fold durable intervention laws into `mission-command-nouns.md`, add this story to the mission-command
shipped ledger, and delete it when the first production UI and mission adapters
ship.
