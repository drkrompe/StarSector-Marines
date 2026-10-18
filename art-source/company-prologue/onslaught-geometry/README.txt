ONSLAUGHT (XIV) BATTLESHIP - CINEMATIC GEOMETRY
===============================================

A Hegemony picket flagship for the Jangala prologue's shot C1 v2
(second-wind-cinematic/corvus-approach/c1-v2-shot.md), seen in formation at
1.5-3 km. Built through the ship-model-pipeline skill. Undamaged, Hegemony
livery; flat base materials until a texturing pass.

Status: pass 1 blockout (top silhouette IoU 0.964). Pass 2 in progress.

Build
  sh ../../blender.sh --background --factory-startup --python build_onslaught.py -- [--views ...]
  python3 compose_review.py
Renders and the .blend are build outputs (ignored); geometry.json is the
committed manifest.
