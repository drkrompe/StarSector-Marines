# Slice D4c — Intervention mission resolution

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Make a successful player intervention actually stop the learned hostile plot,
while preventing stale or malformed contract lineage from mutating a chain.

## Locked rules

- Only a COMPLETED contract with a valid `contractOpposedChainId` can intervene.
- The opposed chain must still be ACTIVE and autonomous.
- Contract patron must equal the threatened chain target; contract target must
  equal the plotting actor. Both identities are validated at resolution time.
- Success transitions the chain to `FAILED` and records the mission day once.
- Player defeat leaves the chain running. Already-terminal and player-backed
  chains are never overwritten.
- The ordinary victorious-Strike political shift still applies, so intervention
  both stops the plot and leaves the existing stake/promotion impact.

## Automated verification

- `ChainInterventionTest` covers success, exact-once terminal time, incomplete
  contracts, party mismatch, player-backed chains, and terminal preservation.
- Full build verifies `MissionResolver` invokes the shared operation only after
  intervention contract completion.
