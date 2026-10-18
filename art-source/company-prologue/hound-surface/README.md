# Hound (A): surfacing

Status: REVIEW — base skin on every face, light service wear; waiting on the owner's look approval.
Written: 2026-10-18

The Hound (A)'s folder for the shared surfacing harness
(`../second-wind-surface/`, run with `--hull`; see its README for the method).
Inputs and Codex generations live here; textures, review renders and builds are
script outputs and are not committed. `./rebuild.sh` replays everything except
Codex.

## Livery

Hegemony auxiliary, from `hound_hegemony.png`: Hegemony orange armour over
taupe (brown-grey) structure, gunmetal machinery, near-black recesses, cream
capsule nose and fittings, tinted glazing (`surface_hull.py` maps every base
material to one of these six classes). A clean, maintained ship: the wear layer
runs the `service` row of `wear.json` (light abrasion, pitting and fade, some
grime in seams, soot at the two drive bells and the stern; no neglect).

- **Finish reference** (`reference/finish-reference.png`): one Codex reskin of
  the clean build's three-quarter camera with the game sprite as colour key;
  registered at IoU 0.996 (`register_reference.py`). `palette.py` reads the
  classes off it. Gunmetal and recess spreads are owned (edge contamination
  inflated them); glass is an owned derivation (`derive_palette`).
- **Swatches**: one Codex sheet of six squares, graded to the palette within
  about 1 dE, tiled at 6 hull units (5.3 m) per image.

## Parts

Four groups, each authored by an Opus subagent (Sol 6.1 is not offered here),
one Codex sheet each, no retries. 16 generated sources, 18 free reuses
(mirrors, identical rings and tines). Fitted IoU 0.936-0.998; every declared
region held its paint family (lowest: the engine top's orange at 0.66, pixels
under the top plates the part no longer claims).

| Group | Sources | Reuses | Notes |
|---|---|---|---|
| `hull` | side wall, deck (with straps, spar plates and the 7 deck plates), stern, underside (with the 20 keel plates) | starboard wall (mirror) | deck and underside are single 38.5 x 28 polygons: 13.7 t/u. The engine spar and most of the cargo body are hidden by other objects |
| `engines` | side, top, upper-outboard chamfer, intake front (port pod) | the starboard pod, all four mirrored | lofted octagonal pods: each tile spans the whole pod. The top plates take the orange swatch |
| `bow` | fork tine side, capsule nose, cage ring fore face, fork base side | three tine walls, seven ring faces | the capsule hull is entirely sleeved and stays swatch |
| `decks` | deckhouse port wall, front, roof, port stern skid | starboard wall and skid (mirrors) | the ramp underside, aft wall, turret and wheelhouse fittings stay swatch |

## Coverage

Share of the ship's on-screen pixels at 1920x1080 (`compose_review.py`; part
skin / class swatch / textured):

| View | Skin | Swatch | Textured |
|---|---|---|---|
| port-side | 40% | 58% | 99% |
| three-quarter | 45% | 53% | 98% |
| top | 40% | 57% | 97% |
| front | 52% | 46% | 98% |
| stern | 13% | 84% | 97% |
| starboard-side | 56% | 43% | 99% |
| starboard-quarter | 47% | 50% | 98% |
| port-aft-quarter | 35% | 63% | 98% |
| under-quarter | 34% | 65% | 99% |
| bow-detail | 40% | 58% | 99% |
| bridge-detail | 33% | 67% | 100% |
| fork-detail | 30% | 70% | 100% |
| turret-detail | 40% | 60% | 100% |
| engine-detail | 63% | 35% | 98% |
| stern-detail | 25% | 72% | 97% |
| wheelhouse-detail | 37% | 61% | 98% |
| underside-aft | 23% | 76% | 99% |

The untextured remainder is lamps, glazing glow, brass lines, hazard paint and
the RCS green-grey, which keep their own materials.

## Condition states (pass 5)

Not needed yet. No campaign doc puts a Hound (A) anywhere; the one place one
appears is the Jangala port cinematic's background traffic. The campaign's
Hounds are the pirate Hound (P) (`hound_d_pirates`) escorts of A Small War
chapter 5, which can be crippled or lost: a cinematic of that needs a `pirate`
livery (its own finish reference and wear row) plus damaged and wrecked
states. Names reserved: `--state clean|battle-damaged|wrecked` in
`build_hound.py`, `Scar HD<nn> ...` objects (the Hermes holds `Scar H<nn>`), a `pirate` row in `wear.json`.

## Known problems

- Selection tests occlusion only against a part's own objects, so the hull
  tiles paint a great deal of face that other objects hide (side walls 4-7%
  visible square-on).
- The taupe swatch wraps at 1.9x the interior step (the others 0.8-1.4).
- The engine tiles fitted at 0.936-0.937 with a 3-5% vertical stretch.
