# Story: budget-aware loot picker

**Status:** CODE COMPLETE (2026-08-12) — focused tests + full build green;
in-game visual smoke test remains.

**Implemented in:** `254de744` (shared S1 + S2 foundation commit).

## Goal

Give the frozen S1 recovery manifest a dedicated, interactive review surface:
icons, stack details, selection state, and strict claim-budget enforcement. Do
not mutate cargo until the settlement rules land as their own slice.

## Landed

- `ScreenId.LOOT` + persistent `LootScreen` registered with the screen router.
- Four-column grid for the manifest's maximum 12 stacks, dynamically reduced to
  three columns on narrower panels.
- `LootCardWidget` renders the live commodity/weapon sprite, name, quantity,
  base value, hover/pressed state, selected state, and over-budget state.
- `LootSelection` owns mutable selection outside immutable `LootManifest` and
  enforces `selectedValue <= selectionBudget` on every toggle.
- The picker header continuously shows selected/budget and total pool value.
- Results keeps **Return** and adds **Review Salvage** when a manifest exists.
  Picker's **Back to Debrief** preserves selection because the screen instance
  and selection object persist across routing.
- All player-facing copy routes through `strings.json`.

## Acceptance

- A stack that would exceed budget cannot be selected.
- Deselecting immediately releases its value for another stack.
- Selection survives Loot → Results → Loot for the same frozen manifest and
  resets when a new manifest replaces it.
- No cargo or credit mutation occurs in this slice.
- Focused selection tests and full project build are green.

## In-game visual checkpoint

- Confirm commodity and weapon sprites fit the thumbnail boxes and don't inherit
  stale sprite singleton state.
- Confirm 12 cards fit without overlap at the live custom-dialog resolution.
- Confirm long modded weapon names truncate cleanly and all footer copy fits.
- Click selected/blocked cards and verify state colors remain legible.

## Next

S3 cargo settlement: preview capacity, explicitly confirm, transfer selected
quantities, fence overflow at 75% base value, and make settlement exactly-once.
