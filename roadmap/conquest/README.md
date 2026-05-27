# Conquest

The flagship mission mode — push from beachhead to keep, taking
tactical structures along the way. Conquest is the gameplay surface
the long-term [`../README.md`](../README.md) sub-game vision was
designed around.

## Contents

- [`central-keep.md`](central-keep.md) — central-keep map shape +
  compound-as-supply model. **V1 complete** (all 6 slices shipped):
  compound state machine, world/HUD markers, reinforcement gating,
  ConquestObjective, BSP compound generation, multi-chamber keep.
  V2 (territory tug-of-war) is the next phase.

## Related

- [`../reinforcement/`](../reinforcement/) — the orchestration layer
  this design plugs into. Triggers gate on compound life; means picks
  vary by compound kind.
- [`../convoy/`](../convoy/) — convoy is the supply means that ARMORY
  compounds dispatch under the new model.
