# Tender call-down

Status: READY

Written: 2026-10-21

Model: `colony-breach-nouns.md` (Hardpoint, Tender call-down). Tender itself:
`tender-ship.md`.

## Scope

Today grounded construction exists only where a story installs it at setup
(`ColdStartSequence` registers `YardConstruction` for an already-grounded
`Tender`). Colony Breach wants the tender to be optional and called down:

1. **Call-down power.** A command power available when the launch manifest
   carries a tender (the fleet brought one). Spending it asks for a landing
   site (a clear footprint; reject sites that do not fit), flies the tender in
   from the nearest map edge through the existing air/sortie path, lands it,
   grounds it, and then registers the existing `YardConstruction` for it.
   Emit `colony_breach.tender_down` when the battle is a colony breach (or a
   generic beat the breach sequence re-emits — keep the power mission-agnostic).
2. **Build list.** Reuse `BuildOption` and the tender's metered stock; Vulcan
   is enough to start. If a second cheap option already exists (sandbags,
   barricade), expose it too; do not invent new structures here.
3. **Rescue wiring.** Make the power available in the rescue battle when the
   manifest allows; one install call, kept small, since `breach-mission-spine.md`
   owns the rest of rescue setup. Coordinate on merge rather than reaching
   into the spine's files.
4. **Evidence.** A behaviour scene: call the tender down beside a lift zone,
   build a Vulcan, Vulcan engages runners. Verdicts assert mechanics.

## Acceptance

- Without a tender in the manifest, no power is offered.
- With one, the tender lands where chosen, grounds, and builds.
- Mission-agnostic: nothing in the power names Colony Breach.
- Tests under a second; scene verdict passes.
