# Player command interventions

Status: PLANNED — follows a competent zero-input baseline and the commander trace harness.

Written: 2026-08-25

Updated: 2026-08-27 — moved under the shared Mission Command authority model.

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
  publish the handback to autonomous command.
- Define activation pacing, maximum concurrent leases, and cost authority,
  including whether strategic requests consume the existing command-point
  resource or a distinct bounded budget.
- Surface acceptance, rejection, remaining lease, supersession, and resulting
  directive in the own-side commander snapshot and UI.
- Compare intervention results with the same fixture's zero-input trace.

## Acceptance

- [ ] An accepted intervention changes a legal own-side priority without
  disclosing or targeting a hidden hostile.
- [ ] Garrison, payload, scripted, and reinforcement-owned squads cannot be
  stolen by a lower-authority player request.
- [ ] Invalid or unreachable requests fail visibly without clearing the current
  competent directive.
- [ ] Expiry, completion, cancellation, or hard invalidation returns authority
  to mission command without a planless/ghost-path interval.
- [ ] Cooldown, concurrency, and cost rules prevent repeated requests from
  sustaining permanent manual override.
- [ ] Existing command powers remain immediate tactical interventions and are
  not forced into the leased strategic-request model.

## Constraints

- Do not add raw writes to `Squad.assignedObjective` from presentation code.
- Do not tune missions around constant player order throughput.
- Faction doctrine remains independent of player intent.

## Exit

Fold durable intervention laws into `mission-command-nouns.md`, add this story to the mission-command
shipped ledger, and delete it when the first production UI and mission adapters
ship.
