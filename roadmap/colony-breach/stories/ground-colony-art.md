# Ground colony art and creep sound

Status: PLANNED — the ground colony and the creep work and are on main; what they look and sound like is borrowed.

Written: 2026-10-21

Model: `colony-breach-nouns.md` (Creep: Ground colony; Beats: Sound).

## Scope

1. **Ground colony sprite.** `structure.ground-colony` borrows the fissure
   spore colony's sprites, so it reads as an anti-air colony. Give it its own
   art through the swarm structure pipeline (`art-source/swarm-bores/`): a
   low, ground-hugging growth on the creep, spine-ridged, clearly not a vent
   pointed at the sky, in the creep's own palette with the creatures' accents;
   intact, wounded and remains states like the other colonies.
2. **Spines' own sound.** The weapon fires the Runner's impale clip
   (`marines_breach_runner_impale`). Give it a heavier eruption from below
   through the Colony Breach audio pipeline (`tools/generate_colony_breach_audio.py`).
3. **Creep ambience.** A wet, slow spread bed heard on creep ground the
   player has seen, posted as a presentation cue the simulation never reads.

## Acceptance

- At play zoom a ground colony is told from a spore colony at a glance.
- A spine strike is heard as its own sound.
- Nothing here changes the simulation.
