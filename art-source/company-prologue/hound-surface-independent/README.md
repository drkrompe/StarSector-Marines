# Hound: independent convoy livery

Status: ACTIVE — round 1 for the owner (Asharu battle, the convoy's rear escort).
Written: 2026-10-19

A texture-only livery of the finished Hound (A). The geometry, part tables and
face selection are `../hound-surface/`'s (this folder's `groups/` re-exports
them, `surface_hull.py` loads its constants); only the paint differs. Run every
harness script with `--hull ../hound-surface-independent`; `./rebuild.sh`
replays everything except Codex, after `../hound-surface/rebuild.sh` has built
the clean geometry.

## Livery

The Asharu convoy's independent escort scheme, matched to the Lasher's
(`lasher-geometry`, `--livery independent`): pale warm-grey armour from the
independent faction colour where the Hegemony auxiliary is orange, slate-blue
structure where it is taupe, the same gunmetal machinery, near-black recesses,
cream capsule nose and fittings, tinted glazing. The harness class keys are the
Hound's own, so `orange` is the armour class (grey here) and `taupe` the
structure class (slate blue). Light `service` wear, as the Hound (A).

- **Finish reference**: one Codex reskin of the Hound's reference camera, with
  the independent Lasher's render (`reference/livery-key.png`) as colour key;
  registered at IoU 0.994. Owned in `derive_palette`: the grey armour's spread
  (the reference's shading inflated it) and a slightly bluer slate, toward the
  Lasher's accent. Wear colours are owned in `palette.json` (`chalk` fades to a
  pale neutral, not tan).
- **Parts**: four Codex sheets (hull, engines, bow, decks) on the Hound's own
  sheet guides, one swatch sheet; no retries. Every tile registered at IoU
  0.979 or better.

## Coverage

As the Hound (A) within a point per view (97-100% textured; `review/`).

## Export

`../hound-export/export_hound.py -- --livery independent` writes
`hound-export/independent/`: collection `HOUND (independent) | clean`, root
`HOUND (independent) | clean | root`, the same anchors and the optional `SWFX`
effects. Verify and review with `--state independent`.
