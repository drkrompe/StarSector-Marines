# Derelict walker gun art (concept)

Concept for the derelict walkers' mount weapons (`MechBodyHardpoints`, `DerelictWalkerGuns`), raised as the owner pick
`derelict-gun-art`. Not shipped: the walkers draw the company's weapon drawings until the pick is answered and
production pieces exist.

- `guide.png`: the company drawings' silhouettes (HC-10, AC-6, chaingun, SRM rack, LRM rack) at 4.5x, the geometry
  authority.
- `concept-v2.png` (+ brief, provenance): codex-imagegen fill in the sentinel torso's plate. v1 was refused by the
  image tool's filter on weapon wording; v2 describes the pieces as equipment modules.
- Preview in-engine: cut each piece to its company drawing's canvas (same length and pivot) into a folder laid out
  like `mod/` under `graphics/battle/mech-modular-topdown/`, then
  `createSnapshots -Psnapshot=derelict-guns-look -Dsnapshot.artOverlay=<folder>`.
