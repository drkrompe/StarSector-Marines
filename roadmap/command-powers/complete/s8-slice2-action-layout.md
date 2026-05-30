# S8 Slice B (part 1) — action-dominant layout, map dropped — ✅ SHIPPED

> Part of [S8 — Pre-Battle Loadout & Briefing Screen](../stories/s8-pre-battle-loadout-screen.md).
> Reclaims the ~70% of `BriefingScreen` the decorative planet map was eating and
> rebuilds it as a full-canvas, two-source action layout.

## What landed

- **`BriefingLayout` → full-canvas two columns.** Dropped the map zone and the
  fixed `INFO_W = 380` strip that inverted the proportions. Now: a shared
  mission-name header + two equal columns (`leftCol`, `rightCol`) across the
  whole canvas.
- **`BriefingScreen` rebuilt into two sources.**
  - **Left column — Mission:** type / risk / payout, salvage negotiation (−/+),
    requirements, enemy-air intel, briefing prose, and the captain list.
  - **Right column — Detachment:** a **Your Fleet Brings** block (transport
    opt-in toggles with sortie-cycle annotations + fighter-cover carrier
    toggles) and an **Employer Provides** block (employer shuttles, client
    fighter support, and — new — a readout of employer-offered powers via
    `summarizePowerIds`). Deploy / Back sit at the bottom of this column.
- **Removed:** all planet-crop / reticle rendering (`renderMapZone`,
  `renderReticle`, `cropForMission`, `drawCircleLine`, `drawCrosshair`, `clamp`,
  `CROP_FRAC`, reticle constants) and now-dead helpers (`effectiveAlliedRoster`,
  the never-called `summarizeTransport`). `SpriteAPI` / `GL_LINES` imports gone.
- **Behavior preserved:** transport + carrier commitment, the sortie-cycle
  annotations, the transport-sufficiency gate on Deploy, salvage mutation, the
  default-first-active-captain convenience, and the resolve →
  `MissionLaunch.buildSimulation` → BATTLE path.

## Why

Feel-out surfaced that the map provided no gameplay yet dominated the screen
while the controls were crammed into 380px. Per the no-stopgap rule we skipped a
throwaway proportion-tweak and did the real action-dominant layout. The
"your fleet vs. the employer" split (already the two co-sources the `Detachment`
resolver produces) is now legible instead of merged into one "Allied Air" line.

## Still ahead

- **Slice B part 2 — member-level commitment.** Surface power-source ships
  (Apogee / Hi-Res Sensors) as their own committable rows in *Your Fleet Brings*,
  so `PowerCatalog` can narrow to the committed subset and
  `DevConfig.ALWAYS_GRANT_RECON_PING` can flip off. (Not in this part.)
- **Slice C — Command Deck.** Slottable powers under a budget.

## Verification

- `gradlew compileJava` green.
- In-game feel-out pending. Density caveat: with a high
  `DevConfig.DEBUG_SEED_PLAYER_VALKYRIES` (8) the right column gets tall —
  lower the seed if the Employer block crowds the Deploy row. Realistic fleets
  (1–3 transports) sit comfortably.
- `SalvageSliderWidget` remains orphaned (the screen uses −/+ buttons) — still a
  pending cleanup from Slice 1.
