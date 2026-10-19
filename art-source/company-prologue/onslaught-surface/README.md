# Onslaught (XIV): surfacing

Status: REVIEW. Base skin on every face, service wear; pass 6 round 1 awaiting the owner.
Written: 2026-10-19

The Onslaught (XIV)'s folder for the shared surfacing harness
(`../second-wind-surface/`, run with `--hull`; see its README for the method).
Inputs and Codex generations live here; textures, grading, review renders and
builds are script outputs and are not committed. `./rebuild.sh` replays
everything except Codex.

## Livery

Hegemony XIV, read off `onslaught_hegemony.png` and matched to the Legion
(XIV)'s armour colours: Hegemony orange armour bands over a warm brown-grey
hull, charcoal armour blocks, a bone-white spine and citadel, gunmetal and pale
steel machinery, near-black recesses. A picket flagship on station: the `service`
row of `wear.json`, a little heavier than the Hound's, with soot at six drives.
Pitting is kept low (0.1): at 0.22 its larger dings read as scattered pale rings
across the big plates.

Detail passes 1-2 built the plating as alternating tones. Graded to their
parents those tones would vanish, so each is its own class, derived from the
measured parent by the offset the geometry was designed with
(`surface_hull.DERIVED`): `orange_w`, `brown_dark`, `bone_brown`, `steel_blue`,
`steel_pale`. Six classes are measured off the finish reference; gunmetal's
mean is owned (grey + 9 L, the geometry's offset), because the reference sees
it only in shadow in the wells and trenches.

- **Finish reference** (`reference/finish-reference.png`): one Codex reskin of
  the clean build's three-quarter camera, with the sprite (turned bow-right,
  4x, built into `build/` and never committed) as colour key; registered at
  IoU 0.985.
- **Swatches**: one Codex sheet of six squares, graded to the palette within
  about 0.1 dE (derived classes regraded from their parent square), tiled at 9
  hull units (7.7 m) per image.
- **Turrets** are the shared turret kit's, in its own weathered military
  livery: never skinned.
- The glazing, lamps, running lights, conduit bronze and blast bags keep their
  own materials.

## Parts

Four groups, each authored by an Opus subagent (Sol 6.1 is not offered here).
The plating meshes are many separate plates in one object, so members use the
harness's whole-object / box seeds to cut them into tiles; the base hull under
them joins the same part, and the plates hide it there. `SHEARED_FACES`: the
hull's lofts and heightfield masses carry non-planar quads, which the polygon
visibility tree dropped.

| Group | Sources / reuses | Codex calls | Fitted IoU | Texels per unit | Notes |
|---|---|---|---|---|---|
| `arms` | 16 / 16 | 9 | 0.979-0.994 | 10.5-14.7 | v1 painted bone-brown near bone white, v2 every class ~10 L bright; `pale-v3` regenerated four tiles. Two starboard deck mirrors at 0.97 |
| `top` | 16 / 11 | 6 | 0.970-0.997 | 15.5-20.5 | v1 glacis and prow painted the pale-steel panels warm off-white (a bone swap); v2 pinned them grey. `glacis-grey-aft-stbd`, `core-mid-stbd` have own tiles |
| `hull` | 16 / 11 | 4 | 0.991-0.998 | 15.0-22.8 | prow flank, forefoot, six bilge slices, five keel tiles |
| `stern` | 16 / 13 | 6 | 0.959-1.000 | 8-13 (pod top 18) | v1 blade painted bone-brown cream, v1 tail painted the radiator rust-red. The leg and tail faces are single 60-85-unit loft polygons, which caps density |

Plus the finish reference and the swatch sheet: 27 generations in all. The
superseded v1 masters stay in `generated/` with their provenance.

## Coverage

Share of the ship's on-screen pixels at 1920x1080 (`compose_review.py`; part
skin / class swatch / textured; the untextured rest is the kit turrets' own
paint, glazing, lamps and bronze):

| View | Skin | Swatch | Textured |
|---|---|---|---|
| three-quarter | 43% | 49% | 92% |
| port-aft-quarter | 36% | 56% | 92% |
| port-side | 40% | 56% | 96% |
| top | 43% | 49% | 92% |
| starboard-side | 40% | 56% | 96% |
| stern | 27% | 70% | 97% |
| belly | 21% | 78% | 99% |
| under-quarter | 20% | 79% | 99% |
| bow-detail | 31% | 64% | 95% |
| arm-detail | 51% | 42% | 93% |
| flank-detail | 56% | 36% | 93% |
| deck-detail | 38% | 55% | 92% |
| waist-detail | 61% | 27% | 88% |
| bridge-detail | 31% | 63% | 94% |
| turret-detail | 49% | 29% | 78% |
| machinery-detail | 29% | 67% | 96% |
| engine-detail | 41% | 58% | 99% |
| stern-detail | 28% | 66% | 94% |
| belly-detail | 32% | 68% | 100% |

## Left to swatch

The machinery wells and trenches and everything in them, drive cans and bells,
the aft-body underside (one 44 x 172-unit polygon), the bilge aft of X 60 and
the waist undersides, the arm undersides and keel bar, the big single base
polygons of the core and prow decks between plates, pipes, fins, louvres and
every chamfer.

## Export

`build/export/onslaught.blend` (step 6 of `rebuild.sh`): the geometry
package's `export_onslaught.py` run on `build/skinned.blend`, every image
packed (about 47 MB). Same collection, root, anchors and manifest as the
untextured build.
