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
- [`stories/progressive-reinforcement.md`](stories/progressive-reinforcement.md)
  — defender reinforcement contests the frontline: recapture targets,
  biome-slice round-robin dispatch, delivery/objective split.
  **Code-complete** (`28e512ab`) — playtest + tuning remain; see
  [`next-session.md`](next-session.md).
- [`stories/biome-counterattack.md`](stories/biome-counterattack.md)
  — the staged defender "bulge" counteroffensive. **Core shipped**
  (`db87ed0d`: slices 1/2/4 — decision + earmark, massed prepaid
  dispatch, emergent resolve); telegraph UI (slice 3) and tuning
  (slice 5) remain.
- [`complete/`](complete/) — shipped stories:
  [`deliberate-compound-capture.md`](complete/deliberate-compound-capture.md)
  (attacker commander captures deliberately),
  [`compound-spread.md`](complete/compound-spread.md) (one compound
  per biome band, PORT → CITY → FORTRESS),
  [`tug-of-war-v2.md`](complete/tug-of-war-v2.md) (marine garrison
  drop on capture; reverse compound flips).

## Related

- [`../reinforcement/`](../reinforcement/) — the orchestration layer
  this design plugs into. Triggers gate on compound life; means picks
  vary by compound kind.
- [`../convoy/`](../convoy/overview.md) — convoy is the supply means that ARMORY
  compounds dispatch under the new model.
