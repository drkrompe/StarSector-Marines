# Bore spawners

Status: READY — the spine's eruption-site seam is on main

Written: 2026-10-21

Model: `colony-breach-nouns.md` (Bore, Bore tier, Mother bore, Spore vent,
Swarm director).

## Scope

1. **Bore structure.** A destroyable bore on a cell footprint, registered the
   way fort aspect cores and turret structures are (durability, death cascade,
   wreck decal). Collapsed bores leave a crater decal. Bores are hostile
   targets that Marine target selection, satchel/demolition and mech fire
   already know how to attack; prove that with a behaviour scene rather than
   special-casing.
2. **Eruption.** Consume the spine's eruption sites and times: install an
   `EruptionHandler` on the battle's `ColonyBreachSequence` (sites in
   `ColonyEruptionPlan`), spawn the bore structure there, clear or crater the
   cells it opens on, and never erupt on a body. The sequence already emits
   `bore_erupted_N` / `mother_bore_erupted` when a site opens.
3. **Director from bores.** Rework `SwarmReinforcementSystem` so that after the
   first eruption it spawns only at living bores (in the ring around the bore's
   mouth), with population target and wave rate a function of living bores and
   their tiers. Collapse removes capacity permanently. Extend the pressure
   snapshot with per-bore state for evidence.
4. **Tiers.** Fissure → bore → maw on a seeded growth clock from eruption,
   slowed by damage taken. Emit `bore_grew_N_*`. Tier picks the strain mix
   through a small table keyed by tier; this story fills it with runners only
   and leaves named slots for brute, spitter and matriarch, which
   `swarm-strains.md` and `matriarch.md` fill.
5. **Spore vents.** Each bore projects an air-denial umbrella: an air-defence
   effect that engages aircraft inside a tier-scaled radius, reusing the
   existing ground air-defence / flak machinery (`ground-defences.md`) rather
   than a new projectile system. Dies with the bore.
6. **Evidence.** A behaviour scene for "a squad with satchels collapses a
   bore" and one for "a fighter sortie over a live bore takes fire"; verdicts
   assert mechanics, not outcomes (see `CLAUDE.md` Behavior scenes).

## Out of scope

New strain bodies, flow-field movement, hive posture.

## Acceptance

- After `tremor`, no swarm body spawns away from a living bore.
- Collapsing every bore drops spawning to zero for the rest of the battle.
- Tier growth and strain table are deterministic and visible in the snapshot.
- Spore vents engage aircraft inside their radius and stop when the bore dies.
- `commanderEvidence -Pmission=colony-breach` replays deterministically.
- Noun doc updated; story folded per the retiring procedure when shipped.
