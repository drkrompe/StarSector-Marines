# Command Powers — next-session handoff

> State of play for picking up the command-powers track cold.

## Where this is

**Design stage.** No code yet. This session created the track via a brainstorm
with the user and seeded it from a vanilla ship/hull-mod flavor survey. The
design skeleton is load-bearing; several forks are decided, a few remain open.

## Docs in this dir

- [`overview.md`](overview.md) — the concept + all decisions. Read first.
- [`ship-hullmod-survey.md`](ship-hullmod-survey.md) — the mined vanilla flavor
  catalog that seeds the power families and the S2 mapping table.
- [`stories/`](stories/) — S1–S7. S1–S4 are implementation-ready; S5–S7 are
  design-forward stubs.

## Decisions locked (this session)

- **Fleet is your spellbook** — power *availability* is diegetic, sourced from
  the ships + hull mods the player brings.
- **Two-phase economy** — pre-battle loadout budget (the hard early choice) +
  in-battle command-point activation economy.
- **Capacity vs roster are separate axes** — command level scales *capacity*
  (budget, CP pool/regen, cooldowns); *roster* (which powers exist) rides
  vanilla acquisition (baseline) + bespoke "super"-mod **spoils** (end-game,
  deliberately scaling toward OP).
- **Projection lens** — a ship system is a power only if it *becomes* or
  *scales* a ground capability; pure ship-survival mods (Heavy Armor, Blast
  Doors) are rejected.
- **Counterplay = attrition, not deletion** — contested assets take hull
  damage / forced retire / lowered CR (and **crew** for manned craft), following
  the ship back into the campaign.
- **Cost stack (charge at use, not opt-in)** — command points (pace) →
  consumables on use → crew at risk (manned only; drone/automated alternative
  risks none) → commitment opportunity cost.
- **Drop geography is the LZ layer** — LZs become a limited/incentivized player
  choice; air defence contests by craft class (heavy-safe vs light-hot); the
  clear-AA → unlock-forward-LZ reach loop; FOB = an *established* forward LZ.

## Open forks (resolve as you reach them)

1. **"Committed" = explicit detachment vs implicit fleet** — gates S2's design.
   Recommendation in the doc: explicit detachment (legible risk). **Settle
   before building S2.**
2. **Survey Equipment double life** — pre-battle intel vs in-battle scan-ahead
   vs both. Sets the pattern for double-life mods.
3. **UI surface** — power bar placement + zone-targeting flow in the full-canvas
   battle takeover.
4. **CP regen vs cooldowns balance** — feel tuning.
5. **LZ persist vs re-contest** (within S6) — pairs with conquest tug-of-war v2.

## Suggested starting point

S1 (framework skeleton, recon-ping) is self-contained and design-fork-free —
start there. Then settle fork #1 before S2. S3/S6 want the parked
**flyby → `AirBody` real-air-entity** promotion so orbital/air assets can be
contested.

## Cross-track dependencies

- `reinforcement/` (shared delivery substrate), `convoy/` + `battle/air/`
  (delivery vectors), `ai/` (enemy commander as the counter), `conquest/`
  (the arena; tug-of-war v2 for LZ re-contest), `campaign/` (command level,
  spoils via loot/patron/covert-ops).
