# Asharu backdrop plates

Matte-painted replacements for the `backdrop` view layer (planet, air, stars) in the two shots
whose camera barely moves: S1a (k0, straight down from 11 km) and S12 (k6, the horizon hold).
The composite swaps a plate in for that layer wholesale and lays the `ships` and `effects` layers
over it unchanged. The moving shots keep the rendered planet with the surface detail tiles
(`../surface/README.md`).

## Contract (agreed with the battle_blockout owner, 2026-10-22)

- `<kf>.backdrop.exr`, 1920x1080, scene-linear Rec.709, alpha 1, on the key frame's own camera
  (`battle_blockout.KEYFRAMES`, 36 mm sensor width).
- Drive `builds/asharu-plates/<commit>/` with `manifest.json`: each plate's kf, shot, sha256,
  source painting and its sha256. The composite reads it with `--plates <dir>`.

## Pipeline

1. `make_plate_guides.py`: ray-casts the key frame camera onto the 5,000 km sphere and reads the
   ground from the surface detail stack (desert01, ImageGen tiles, settlements), lit by the
   scene's sun. No ships, air or stars. Written to `build/asharu-plates/guides/`, never committed.
2. ImageGen (`tools/codex_imagegen.py`) repaints the guide as a finished matte painting with the
   brief in `prompts/<kf>.txt`, the guide its exact layout authority. The painting is in
   `raw/<kf>.png` with its provenance record. These are the committed images.
3. `finish_plates.py`: resizes to 1920x1080; for S12 remaps the rows above the registered band so
   the painted limb meets the camera's true limb (the ground registers within a pixel below it);
   inverts the stills' grade (AgX, 'AgX - Base Contrast', tabulated by `agx_lut.py` in Blender
   without rendering) so the plate grades back to the painting; writes the EXR, a display PNG
   and the manifest under `build/asharu-plates/out/`. `--post <commit>` copies them to Drive.

The grade cannot reach every painted colour: AgX desaturates bright highlights, so the
brightest, warmest sand (about 3% of S1a) comes back up to about 15 code values less warm.
Everything else round-trips within a code value.
