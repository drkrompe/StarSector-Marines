# Swarm harvest

Status: QUEUED on `bore-spawners.md` (bores and the biomass feed seam)

Written: 2026-10-21

Model: `colony-breach-nouns.md` (Harvest, Origin).

## Scope

1. **Seize and drag.** A runner that downs a colonist (ambient colony
   civilian, militia, eventually a Marine) seizes the body instead of leaving a
   corpse and becomes a **carrier**: slowed, dragging the body toward the
   nearest living bore along the swarm's movement path. Carriers do not fight
   unless attacked in melee. Carried body drawn attached to the carrier
   (layered render; a drag pose if cheap, an offset corpse sprite if not).
2. **Delivery.** A carrier reaching a bore's mouth delivers biomass through the
   bores' feed seam: growth advances and the director gets a surge bonus there.
   Emit `first_harvest` and `bore_fed_N`. Deliveries per bore in the director
   snapshot.
3. **Interception.** Killing a carrier drops the body where it fell; it can be
   seized again by another runner. Cohort evacuees are never seized while their
   escort lives.
4. **Colony population.** Make sure the colony carries enough ambient colonists
   for harvest to matter, and that they flee from swarm contact, so the early
   battle has bodies at stake outside the shelter.
5. **Evidence.** A behaviour scene: runners seize colonists, carriers reach a
   bore, the bore's tier advances; a second loop where Marines intercept the
   carriers and the bore does not grow. Mechanic verdicts only.

## Acceptance

- Bodies visibly travel to bores; a fed bore outgrows a starved one in the
  same battle.
- Intercepting carriers measurably starves the bore in the scene.
- Deterministic; tests under a second.
