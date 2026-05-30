# S8 — Pre-Battle Loadout & Briefing Screen

> The full-canvas pre-battle surface where the player reviews the mission,
> commits a detachment, and **slots the powers they'll bring** under a command
> budget. This is the home for the two-phase economy's *loadout phase* and the
> resolution of open fork #2 ("UI surface", overview).

## Why

S2 unified powers + fighter cover + shuttles under an explicit `Detachment`, but
the only place the player commits it today is the **inline expanded dossier
card** in `CommsConsolePanel`. That card is cramped: salvage slider + transport
toggles + fighter toggles + captain rows + accept, all stacked in one expansion.
It can't host the *slotting UI* S2 explicitly deferred, and it has no room to make
the **"your fleet brings vs. the employer brings"** distinction legible.

The old full-screen `BriefingScreen` (two-zone: planet crop + info column) still
exists but is **dead code** — its only entry, `TacticalMapPanel`, is never
instantiated (the three-column ops layout it belonged to was replaced by the
list-view `MissionSelectScreen` + `CommsConsolePanel`). Worse, S2 added the
carrier/transport toggles to *both* screens, so we now maintain two drifting
copies of the detachment UI, one unreachable.

This story **revives the full-screen surface as the single canonical pre-battle
screen**, kills the duplication, and grows it into the loadout/deck-building
moment the command-powers economy was designed around.

## Design anchors (already decided in overview)

- **Two-phase economy** (overview §"The two-phase economy"): the loadout phase is
  a *slot budget* — command level → N slottable powers, each power carries a
  weight. The player weighs which available powers to bring under that budget.
- **Charge at use, not opt-in** (overview §"What powers cost"): slotting stays
  *cheap*. The campaign-resource costs (supplies / crew / CR) are spent when a
  power *fires* mid-battle, not when it's slotted. So the screen's "cost" is the
  slot budget, plus an *informational* readout of each power's at-use cost.
- **Explicit detachment, two co-sources** (overview §"The commitment layer"):
  `Detachment` already carries the player's committed assets and the employer's
  offerings (`Mission.clientFighterSupport` / `employerShuttles` /
  `employerPowerIds`). The screen makes those two sources visually distinct.
- **Command level is a stub here.** A *constant* budget cap is fine for this
  story; the real capacity curve is S5.

## Information architecture

One full-canvas takeover, four regions:

- **MISSION** — type / risk / payout, salvage negotiation, briefing prose. The
  decorative planet crop + reticle is **dropped** (it provided no gameplay and
  ate ~70% of the screen via the old `INFO_W`-fixed split); that space is
  reclaimed for the action regions below. A future *battlespace preview* could
  one day occupy a corner here, but it's parked as a separate speculative story
  — the layout must stand on its own without it. See
  [`../../mapgen/stories/battlespace-preview.md`](../../mapgen/stories/battlespace-preview.md).
- **YOUR FLEET BRINGS** — committable assets as opt-in rows: transports, carriers
  (fighter cover), and **power-source ships** (e.g. an Apogee that grants Recon
  Sweep). Committing a row feeds the `Detachment` *and* the contested-asset
  attrition model — these are exactly the ships at CR/crew risk.
- **EMPLOYER PROVIDES** — read-only: employer shuttles, client fighter support,
  employer-offered powers. The contract co-source made legible.
- **COMMAND DECK** — the slotting UI: available powers (resolved from committed
  ships + employer), each with a slot weight, against a command budget bar. The
  slotted subset is what actually arrives in battle.
- **CAPTAIN** + **Deploy / Back**.

## Slices (each compiles + commits)

### Slice A — Revive & make canonical
- Re-route: `CommsConsolePanel` expanded card's **Accept** becomes
  **"Brief & Deploy"** → `ctx.setSelectedMission(m)` + `goTo(BRIEFING)`. The card
  collapses to a read-only summary (type/risk/payout/prose); all *commitment*
  controls move to the full screen.
- `BriefingScreen` becomes the single pre-battle surface: port it to match the
  live card's behavior (carrier toggles already present; verify parity), keep its
  `onAccept` → `MissionLaunch.buildSimulation` → `goTo(BATTLE)` path.
- Delete the duplication: the toggle/manifest logic lives in one screen, not two.
  Decide `TacticalMapPanel`'s fate — likely delete (dead) or keep only if a
  future map-pick path wants it; if deleted, drop `BRIEFING` plumbing that only
  it fed and re-point the one live entry.
- **Verify:** picking a mission → Brief & Deploy → full screen → Deploy → battle,
  with the same manifest/roster a no-deselect Accept produced pre-revival.

### Slice B — Action-dominant rebuild: two-source presentation (+ member-level commitment)
- **Drop the planet map and rebuild the layout action-dominant.** `BriefingLayout`
  currently pins controls to a fixed 380px `INFO_W` strip and gives the
  decorative map all remaining width — invert that: the full canvas is the
  action area. No map surface at all (the future preview re-introduces its own
  if it ever lands).
- Restructure into "Your Fleet Brings" / "Employer Provides" panels across the
  reclaimed width.
- Surface **power-source ships** as their own committable rows (the Apogee /
  Hi-Res Sensors carrier). This makes **member-level commitment** real — the top
  S2 follow-up — so power narrowing finally has something to narrow against, and
  `DevConfig.ALWAYS_GRANT_RECON_PING` can flip off to feel the gating.

  *May split: B-1 = drop map + two-source reflow of existing controls
  (transports/carriers/employer); B-2 = power-source committable rows +
  narrowing. B-1 alone already answers the "reclaim the map space" feedback.*

### Slice C — Command Deck (slotting UI)
- Slottable-powers list with a *constant* command budget (S5 does the curve).
- Each power: a slot weight + an informational at-use cost readout.
- The slotted subset filters `PowerCatalog.resolve` output before it reaches
  `setCommandPowers`. This is S2's deferred slotting + the deck-building moment.

## Out of scope

- The real command-level *budget curve* (S5).
- New power *behaviors* (S3 Orbital Fire Support, S4 Marine Insertion).
- At-use resource accounting (the cost stack fires in-battle; here it's a readout).
- Drop geography / LZ choice (S6).

## Dependencies

- S1 framework + S2 detachment resolver (both shipped).
- A stub command level (constant budget) — fine; real curve is S5.

## Acceptance

Picking a mission opens the full-screen loadout; the player sees their fleet's
contribution distinct from the employer's, slots powers under a budget, picks a
captain, and Deploy launches a battle whose powers/fighters/shuttles match the
committed detachment + slotted deck. The inline card no longer hosts commitment
controls, and there is exactly one detachment UI in the codebase.
