# DEBUG player mech drops

**Status:** In progress.

## Goal

Make the shipped mech family playable on the marine side in real battles,
especially Conquest, without prematurely inventing the ownership, salvage, and
refit progression reserved for S3.

## Slice

- Debug mission briefings expose a mech-support count, defaulting to three and
  capped at eight, plus a reroll action.
- The briefing shows the resulting randomized Bulwark/Hound/Sirocco makeup.
- A non-zero roster adds Mech Support to the debug command deck with one charge
  per configured chassis.
- Each activation uses the existing targetable Valkyrie delivery and unloads
  the next configured chassis into its own marine-side mech squad.
- Production-sourced Mech Support remains one Bulwark.

The randomized roster is stable while the player edits other briefing choices;
only changing the mission or explicitly rerolling changes its seed.

## Acceptance

1. A DEBUG Conquest briefing can configure zero through eight player mechs.
2. The command deck and in-battle charge count match the picker.
3. Repeated activations deliver the displayed variant makeup on the marine side
   through physical dropships.
4. Zero removes the debug-granted power.
5. The ordinary `MechSupport` constructor retains its one-Bulwark behavior.
6. Focused tests and the full Gradle build pass.

## Boundary

This is battle-iteration scaffolding, not player mech progression. It adds no
ownership, inventory, salvage, refit, recovery, or campaign cost model.
