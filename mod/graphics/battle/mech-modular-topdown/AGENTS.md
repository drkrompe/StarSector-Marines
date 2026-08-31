# Shipped modular mech asset instructions

Follow the repository-root `AGENTS.md` and `CLAUDE.md` first. These additional
rules apply to runtime modular mech textures in this directory.

- Files below `factions/` are derived outputs. Do not hand-paint them in place;
  edit retained sources, masks, prompts, or builders under
  `art-source/mech-modular-topdown/`, then run the deterministic asset build.
- One faction livery is an all-or-nothing presentation family: all three
  chassis (`chassis.png`, `chassis-hound.png`, `chassis-sirocco.png`) and all
  five painted weapon modules must exist together. Runtime must fall back to
  the complete base family, never mix a faction chassis with base or another
  faction's weapon casing because one file is missing.
- Feet, thigh linkages, socketed authoring chassis, muzzle flash, geometry,
  layer order, mount transforms, and runtime scale remain shared base
  authorities. A livery changes only accepted chassis surfaces and authored
  equipment casing.
- Every derived sprite must preserve the base layer's dimensions, registration,
  alpha footprint, silhouette, and protected hardware. Extend
  `LayeredMechAssetTest` whenever the family contract changes.

