# Story: capacity-aware cargo settlement

**Status:** CODE COMPLETE (2026-08-12) — focused planner tests + full build
green; in-game cargo-delta smoke test remains.

**Implemented in:** S3a planner `037586cf`; S3b live apply + confirmation UI
`90f01acc`.

## Goal

Turn the picker's selected stacks into an explicit, exactly-once settlement:
carry what fits in the correct vanilla capacity bucket and fence overflow for
75% of base value. The player sees the complete kept/fenced preview before an
explicit confirmation.

## Slice plan

### S3a — pure settlement planner

- Model cargo, fuel, and personnel as independent capacity buckets.
- Walk selected stacks in manifest order and split each into kept and fenced
  quantities. Partial stacks are allowed.
- Fence credits are `floor(fenced base value × 0.75)` per line.
- Return an immutable plan with per-stack lines and aggregate kept/fenced totals.
- No `Global`, live cargo, or UI dependencies in planner tests.

### S3b — campaign apply + confirmation UI

- Adapt live `CargoAPI` free capacity into the planner input.
- Apply kept commodities/weapons to cargo and fenced credits once.
- Store the completed result on `MarineOpsContext`; a second confirmation must
  be a no-op even if the old screen receives another click event.
- Replace the preview-only footer with kept/fenced totals and explicit
  **Confirm Salvage**. Zero-selection confirmation is allowed and means forfeit.
- After confirmation, return to mission select and clear the resolved
  mission/battle recovery flow so the old outcome cannot be claimed again.

## Acceptance

- Fuel uses fuel capacity; marines use personnel; weapons/supplies/machinery use
  ordinary cargo capacity.
- A partially fitting stack keeps the fitting units and fences only overflow.
- Fenced credits use exactly 75% base value with deterministic integer rounding.
- Applying the same settlement twice cannot duplicate items or credits.
- Full build green; in-game cargo-delta smoke test remains the shipping gate.

## Landed

- Independent cargo/fuel/personnel capacity allocation with partial-stack
  splits and deterministic 75% integer fencing.
- Live confirmation preview continuously recomputes carry/fence totals against
  the player's current free capacity.
- Explicit confirmation transfers weapons/commodities/fuel/marines through the
  matching `CargoAPI` methods and pays aggregate fenced credits.
- The context claims an exactly-once gate before mutation; duplicate clicks
  return the existing receipt. A mid-apply exception keeps the gate closed to
  avoid duplicating already-added lines.
- Results makes the alternative explicit as **Forfeit Salvage** and clears the
  resolved mission flow on confirm/forfeit.
