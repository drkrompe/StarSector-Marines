# Hive creep

Status: IN PROGRESS — creep field built and driven by eruption sites; look, art and the bore hookup open. Bores and the growth-site seam (`BoreGrowthSites`) are on main.

Written: 2026-10-21

Updated: 2026-10-21 — field shipped ahead of bores; sites are interim sources.

Model: `colony-breach-nouns.md` (Bore, Spore colony, Origin).

## Intent

An ordinary settlement should look, within a few minutes of the breach, like
something is very, very wrong with it. Living bores spread a biological growth
(**creep**) out across the colony: over streets, up walls, into buildings.
Creep is the visual signature of the outbreak and the ground the hive's static
growths stand on. The owner's reference is StarCraft's Zerg creep and colonies.
Mobile strains stay what they are: no melee body gains a ranged attack.

## Scope

1. **Creep field.** A per-cell creep coverage grown outward from each living
   bore at a seeded, tier-scaled rate along walkable ground and onto adjacent
   walls, faster where fed (biomass), blocked by nothing but slowed by
   pavement or fire if those hooks are cheap. When a bore collapses, its creep
   stops growing and slowly recedes, dying back over a visible interval rather
   than vanishing. Owned by the simulation, deterministic, exposed in the
   director snapshot (coverage per bore).
2. **Look.** Creep is a ground layer drawn over the colony's tiles: organic,
   wet, veined, with an animated pulse toward its bore, a ragged spreading
   edge, and creep climbing building edges. It must read at battle zoom and at
   the whole-map framing. Art through the geometry-first ImageGen workflow
   (`art-source/geometry-first-imagegen.md`, the `codex-imagegen` skill):
   creep tiles/autotile edges and wall-climb overlays, plus a receding/dead
   state. Prefer a resident ground layer patched by deltas over per-frame
   collection (`renderEvidence` rules; compare against a control run). Owner
   review set on Drive under `review/hive-creep/`.
3. **Ground colony.** A second static growth that roots only on creep: a
   sunken-colony analogue that strikes ground targets in short range (impaling
   spines from below), wrecks emplacements, hardpoints and buildings it
   reaches, and is destroyable. It reuses the bores' growth-site placement.
   Spore colonies (anti-air) may also root on creep away from bores once creep
   carries them, extending the umbrella across the infested colony.
4. **Creep effects.** Swarm bodies move faster on creep; Marines and colonists
   do not get slowed (keep it readable and fair), but colonists will not shelter
   in creeped buildings. Keep effects few until play shows a need.
5. **Lore and audio.** Creep is the weapon converting a world (see
   `swarm-lore-guide.md`); ask for a creep-spread ambience and ground-colony
   strike sound through the Colony Breach audio pipeline (`colony-breach-nouns.md`, Sound).
6. **Evidence.** A behaviour scene: a bore left alone creeps toward a
   hardpoint and a ground colony roots and wrecks it; a second loop where the
   bore is collapsed and the creep recedes. A `renderEvidence` comparison with
   creep on and off at the canonical colony. Mechanic verdicts only.

## Acceptance

- At the whole-map framing, a colony an outbreak has run in for several
  minutes is unmistakably infested; owner signs off the look.
- Ground colonies only exist on creep; collapsing a bore withers its creep and
  colonies.
- Render cost is measured against its own control and stays within the
  layer's budget.
- Deterministic; tests under a second.
