# Generic Extraction corridor command

Status: IN PROGRESS — implement and headlessly verify Marine pickup, escort,
screen, and egress command for the generic recoverable package.

Written: 2026-08-27

Read `mission-command-nouns.md`, `extraction-command.md`,
`autonomous-mission-command-foundation.md`, and
`commander-trace-and-balance-harness.md` before working this story.

## Intent

Turn the shipped generic Extraction objective into a zero-input operation. A
stable payload element must recover the package while the rest of the force
forms a distributed corridor screen, then the same roles must escort it back
to the authored egress without suppressing local contact behavior.

## Scope

- Install a frame-only Marine commander only for generic Extraction.
- Keep one stable payload element, one close escort when strength permits, and
  distribute remaining squads across lead, lateral, and rear screen slots.
- Move the corridor picture with the authoritative payload cell and converge
  only the close elements inside the boarding perimeter.
- Choose only walkable, squad-reachable cells and explain any stranded squad.
- Preserve higher-authority payload, garrison, reinforcement, and scripted
  ownership.
- Publish phase, payload state, role, reason, target, and local-contact state to
  the selected-squad overlay, squad dump, and perspective command trace.
- Upgrade the existing `commanderEvidence -Pmission=extraction` adapter from
  objective-only evidence to duplicate-replay command evidence.

## Acceptance

- [x] A zero-input generic Extraction run assigns every available Marine squad
  a payload, close-escort, or distributed screen duty.
- [x] The payload element reaches and secures the source, remains inside the
  transit leash, and supports boarding at egress unless combat produces an
  explained terminal failure.
- [x] Role membership is stable across ordinary pulses and changes only for
  force arrival, loss, terminal objective state, or lost reachability.
- [x] Screen elements use separate reachable cells and yield to locally
  acquired contact through existing squad doctrine.
- [x] Selected-squad overlay, squad dump, and perspective trace agree on phase,
  role, reason, assignment kind, and target cell.
- [x] Forced-serial duplicate replay is byte-stable and records
  `extraction-attacker` perspective events plus neutral payload transitions.
- [x] Canonical-duration headless evidence reaches success or an explained
  terminal failure; a bounded shorter run remains an honest timeout.
- [ ] Live play confirms the formation reads as a moving corridor rather than
  a brick and that squads react intelligibly under contact.

## Constraints

- The objective remains the sole authority for package motion, control,
  boarding, failure, and victory.
- The commander consumes only Marine disclosure and frozen public topology.
- Civilian Rescue and Silent Colony retain their current command paths and
  their separate stories.
- This story does not add a conventional defender commander.

## Exit

Fold durable generic corridor vocabulary into `extraction-command.md`, record
the implementation in the shipped ledger, and keep this story active until
canonical and live acceptance are complete.
