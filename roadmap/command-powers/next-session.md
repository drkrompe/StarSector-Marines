# Command Powers — next-session handoff

> State of play for picking up the command-powers track cold.

## Where this is

**S1 shipped + S2 in progress (explicit-detachment arc).** S1 (power framework
skeleton — recon ping) is built; see
[`complete/s1-power-framework-skeleton.md`](complete/s1-power-framework-skeleton.md).
**Fork #1 is resolved — explicit detachment** (overview § "The commitment
layer"): the committed detachment is the single source of powers + fighter cover
+ shuttles, with the employer/contract as a co-source. S2 is decomposed into 3
slices (see [`stories/s2-*.md`](stories/s2-fleet-available-powers-resolver.md)):

- **Slice 1 — resolver core + powers-from-fleet.** ✅ shipped, compile-verified —
  [`complete/s2-slice1-detachment-resolver-core.md`](complete/s2-slice1-detachment-resolver-core.md).
- **Slice 2 — commitment narrowing** (powers/shuttles from the committed subset;
  employer-power rolls; baseline ReconPing behind DevConfig). ⏳ next.
- **Slice 3 — fighter-cover opt-in UI.** ⏳ after Slice 2.

**In-game feel-out still pending** across the board.

### Commit chain

- S1 — `battle/power/` (CommandPower, ReconPing, CommandPowerService,
  CommandPowerSystem) + `CommandPowerPanel` + BattleSimulation/BattleScreen
  wire-up. Invoke → target → resolve → cooldown loop end-to-end with one power.
- S2 Slice 1 — `ops/detachment/` (Detachment, DetachmentResolver, PowerCatalog)
  + `ops/MissionLaunch` shared accept path + `BattleSimulation.setCommandPowers`
  + `Mission.employerPowerIds`. Powers now sourced from the fleet; Briefing/Comms
  accept duplication collapsed. Default behavior preserved.

## Docs in this dir

- [`overview.md`](overview.md) — the concept + all decisions. Read first.
- [`ship-hullmod-survey.md`](ship-hullmod-survey.md) — the mined vanilla flavor
  catalog that seeds the power families and the S2 mapping table.
- [`complete/`](complete/) — shipped stories. **S1 lives here now.**
- [`stories/`](stories/) — S2–S7. S2–S4 are implementation-ready; S5–S7 are
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

1. ~~**"Committed" = explicit detachment vs implicit fleet**~~ — **resolved:
   explicit detachment** (overview § "The commitment layer").
2. **Survey Equipment double life** — pre-battle intel vs in-battle scan-ahead
   vs both. Sets the pattern for double-life mods.
3. **UI surface** — power bar placement + zone-targeting flow in the full-canvas
   battle takeover.
4. **CP regen vs cooldowns balance** — feel tuning.
5. **LZ persist vs re-contest** (within S6) — pairs with conquest tug-of-war v2.

## Suggested starting point

S2 Slice 1 (detachment resolver core) is **shipped**. Continue the arc:
1. **Slice 2 — commitment narrowing**: `PowerCatalog.resolve` scans only the
   committed subset; light up `MissionGenerator.rollEmployerPowers`; gate the
   baseline ReconPing behind `DevConfig`.
2. **Slice 3 — fighter-cover opt-in UI**: replace `PlayerFleetWings.fromPlayerFleet`
   whole-fleet auto with committed-carriers → wings + opt-in toggles.

S3/S6 want the parked **flyby → `AirBody` real-air-entity** promotion so
orbital/air assets can be contested.

## Cross-track dependencies

- `reinforcement/` (shared delivery substrate), `convoy/` + `battle/air/`
  (delivery vectors), `ai/` (enemy commander as the counter), `conquest/`
  (the arena; tug-of-war v2 for LZ re-contest), `campaign/` (command level,
  spoils via loot/patron/covert-ops).
