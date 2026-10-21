# Hive posture and strike detachments

Status: QUEUED on `breach-mission-spine.md` (bores are on main; a squad is pointed at one by an order naming its cell, `colony-breach-nouns.md` Bore)

Written: 2026-10-21

Model: `colony-breach-nouns.md` (Hive posture, Readiness gate, Strike
detachment). Commander: `extraction-command.md`.

## Scope

1. Briefing choice of hive posture (Evacuate / Opportunistic / Purge), carried
   on the mission into the frozen command envelope.
2. Readiness gate in the rescue commander, evaluated from its visible picture
   each command pulse: lift hardened, residual strength vs escort need,
   demolition/heavy detachment available. Purge relaxes the first two.
3. Strike detachment role with stable membership, committed and recalled with
   `strike_committed_N` / `strike_recalled_N` beats and command-trace entries.
4. Mother-bore bonus settled through the ordinary mission outcome path.
5. Evidence: the `colony-breach` matrix gains one fixture per posture; the
   trace shows commit and recall decisions with their gate inputs.

## Acceptance

- Evacuate never commits a detachment off-route.
- Opportunistic commits only with the gate open and recalls when it closes.
- Purge commits against the mother bore once it is known.
