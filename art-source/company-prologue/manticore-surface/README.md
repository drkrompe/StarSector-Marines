# Manticore (P) surfacing

The Manticore (P)'s inputs to the shared surface harness (`second-wind-surface/`):
the pirate livery (red plates over gunmetal structure), `battle` wear and the
sprite's markings. Run any harness script with `--hull ../manticore-surface`;
`rebuild.sh` is the whole sequence.

- `surface_hull.py`: build path (`build/geom/manticore.blend`, built by
  `manticore-geometry/build_manticore.py --keep-studio`), groups, material
  classes, reference camera, wear response, engine and stern soot.
- `groups/hull.py`, `wings.py`, `bow.py`: part tables; `_mirror.py` pairs each
  port part with a starboard mirror (`skin_from`, `mirror_u`). `SEAMS` turns on
  `parts.py` `seam_walls`; it is off because the shipped tiles were fitted to
  guides without it.
- `queue_guides.py`: the guide renders as one light render-queue job.
- `livery_decal.py`: the dome drips and flame flashes read off the sprite.
- `prompts/`, `reference/`, `generated/`, `guide/`: Codex prompts, the finish
  reference and sheets with the guides they were made from.

Fit: 13 tiles at IoU 0.971-0.998; band, horn-in, horn-out, horn-top, pod-out
and wing-armour sit at 0.955-0.969. Their guides' own segmentation stops at
about 0.963-0.972, because hairline gaps open at the steep plate seam walls. Regenerating
them on `seam_walls` guides is the follow-up.
