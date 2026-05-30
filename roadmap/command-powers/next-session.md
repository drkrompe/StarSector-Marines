# Command Powers — next-session handoff

> State of play for picking up the command-powers track cold.

## Where this is

**S1 + S2 shipped (explicit-detachment arc complete).** S1 (power framework
skeleton — recon ping) is built; see
[`complete/s1-power-framework-skeleton.md`](complete/s1-power-framework-skeleton.md).
**Fork #1 is resolved — explicit detachment** (overview § "The commitment
layer"): the committed detachment is the single source of powers + fighter cover
+ shuttles, with the employer/contract as a co-source. S2 shipped across 3 slices
(see [`complete/s2-*.md`](complete/s2-fleet-available-powers-resolver.md)):

- **Slice 1 — resolver core + powers-from-fleet.** ✅ — `a338cc7`.
- **Slice 2 — employer co-source + baseline gate.** ✅ — `3362e9b`.
- **Slice 3 — fighter-cover opt-in UI.** ✅ — carrier opt-in toggles in both
  pre-battle screens; `PlayerFleetWings` committed-carrier resolution.

**In-game feel-out still pending** across the board — `gradlew runStarsector`,
accept a mission via both entry points, confirm carriers/transports/powers track
the committed detachment.

**Top remaining follow-up:** power narrowing rides the whole fleet until a
*member-level* commitment surface exists (a recon-source ship is neither
transport nor carrier). See the Slice 3 doc § follow-ups.

### Commit chain

- S1 — `battle/power/` (CommandPower, ReconPing, CommandPowerService,
  CommandPowerSystem) + `CommandPowerPanel` + BattleSimulation/BattleScreen
  wire-up. Invoke → target → resolve → cooldown loop end-to-end with one power.
- S2 Slice 1 — `ops/detachment/` (Detachment, DetachmentResolver, PowerCatalog)
  + `ops/MissionLaunch` shared accept path + `BattleSimulation.setCommandPowers`
  + `Mission.employerPowerIds`. Powers now sourced from the fleet; Briefing/Comms
  accept duplication collapsed. Default behavior preserved.
- S2 Slice 2 — `DevConfig.ALWAYS_GRANT_RECON_PING` gate on the baseline ReconPing
  + `MissionGenerator.rollEmployerPowers` lighting up the contract co-source
  (`PowerCatalog` now reads `Mission.employerPowerIds`).
- S2 Slice 3 — `PlayerFleetWings` committed-carrier resolution (`committableCarriers`
  / `rosterFrom`) + carrier opt-in toggles in BriefingScreen + CommsConsolePanel;
  `onAccept` passes the committed-carrier roster, not the whole-fleet scan.

## Docs in this dir

- [`overview.md`](overview.md) — the concept + all decisions. Read first.
- [`ship-hullmod-survey.md`](ship-hullmod-survey.md) — the mined vanilla flavor
  catalog that seeds the power families and the S2 mapping table.
- [`complete/`](complete/) — shipped stories. **S1 + S2 (umbrella + 3 slice
  docs) live here now.**
- [`stories/`](stories/) — S3–S7. S3–S4 are implementation-ready; S5–S7 are
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

S2 (explicit-detachment arc) is **fully shipped**. **Direction chosen
(2026-05-30):** revive the full-screen pre-battle surface as the canonical
loadout/briefing screen — see [S8](stories/s8-pre-battle-loadout-screen.md). The
inline `CommsConsolePanel` card is too cramped for the "your fleet brings vs.
employer brings" distinction + the power-slotting (deck-building) the two-phase
economy needs, and the dead `BriefingScreen` carries a drifting duplicate of the
detachment UI. S8 collapses both into one screen.

**S8 build order:**
1. ~~**Slice A — Revive & make canonical.**~~ ✅ — inline card → read-only
   summary + "Brief & Deploy" → full-screen `BriefingScreen` (now canonical);
   deleted dead `TacticalMapPanel` + `MissionPopupOverlay`. See
   [`complete/s8-slice1-revive-canonical.md`](complete/s8-slice1-revive-canonical.md).
2. **Slice B — Two-source presentation + member-level commitment** (the top S2
   follow-up): power-source ships as their own committable rows; then flip
   `DevConfig.ALWAYS_GRANT_RECON_PING` off to feel the gating.
3. **Slice C — Command Deck (slotting UI)**: slottable powers under a constant
   command budget (S5 does the real curve); slotted subset filters
   `PowerCatalog.resolve`.

Alternatives if priorities shift: **S3 — Orbital Fire Support** (first real
combat power, resolver + detachment plumbing is ready). In-game **feel-out** of
the S2 arc is still pending but is largely subsumed by S8 Slice A (which reworks
the same surface).

S3/S6 want the parked **flyby → `AirBody` real-air-entity** promotion so
orbital/air assets can be contested.

## Cross-track dependencies

- `reinforcement/` (shared delivery substrate), `convoy/` + `battle/air/`
  (delivery vectors), `ai/` (enemy commander as the counter), `conquest/`
  (the arena; tug-of-war v2 for LZ re-contest), `campaign/` (command level,
  spoils via loot/patron/covert-ops).
