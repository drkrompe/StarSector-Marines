# Give `urban-tileset.raw.png` its own alpha

Status: Proposed

Written: 2026-08-28

Read `moddable-tilesets-nouns.md` first — in particular the authoring-pass law
that a raw sheet must carry its own alpha.

## Why

`urban-tileset` is fully adopted into its authoring document: all four blocks
with their layouts and fill, all 23 doodad ids with their cover and ballistic
half-height, and the three deck-paving ids that used to be reached by
coordinate. Everything the shipped tileset states, the document now states.

It still cannot be exported, and the reason is not in the document. The raw
sheet under `art-source/` is RGB and fully opaque. The alpha that makes a crate
a crate instead of a black square has never lived in `art-source/` at all: it
lives only in the shipped PNG, because `normalize_tilesets.py` derives each new
atlas by transferring fresh colour onto the *previous* shipped atlas's alpha
topology, which it treats as that sheet's hard contract.

So the shipped PNG is a build input that no source of truth can replace, and an
export today packs every piece as an opaque black-backed tile. Measured on a
real export of the adopted document:

| Piece | Opaque before | Opaque after |
| --- | --- | --- |
| `urban.door-open` (slim overhead bar) | 12.5% | 100% |
| `urban.wall` centre (the hollow case the `0x060A10` fill exists for) | 0% | 100% |
| `doodad.box`, `doodad.crate` | 76.4% | 100% |
| `doodad.chair-south-yellow` | 38.9% | 100% |
| `doodad.decal-rubble-1` | 21.7% | 100% |

Doorways become black holes and every prop becomes a black square. That is why
the adoption shipped and the export did not.

## Scope

Give the sheet an alpha channel in `art-source/`, then export. Either:

- key the background into `art-source/tilesets/urban-tileset.raw.png` once, as a
  deliberate, reviewable edit to the art — the alpha becomes part of the
  version-controlled source, which is what the law asks for; or
- teach the authoring pass a per-sheet background key, so an opaque plate
  declares what its background is and the exporter cuts it. This is the more
  general answer and covers the other opaque plates, but it makes "what is
  background" a setting rather than a picture, and a soft-edged prop keys badly.

The first is preferred unless a second opaque plate turns up wanting the same
treatment.

`normalize_tilesets.py`'s role shrinks once the raw sheet is authoritative: it
should no longer need the previous atlas as an input for this sheet.

## Acceptance

- Every id in the shipped tileset survives the export, with its cover and
  ballistic half-height unchanged — `UrbanTilesetContractTest` already states
  this and must stay green.
- Per-piece opacity after export is within a small tolerance of the shipped
  sheet's, measured piece by piece rather than as a whole-sheet diff: the two
  atlases are packed differently, so only the same named piece cut from each is
  comparable.
- A visual comparison of shipped and exported pieces over a contrasting backdrop
  is reviewed before the export is kept. This is the check that caught the
  problem; a passing test suite did not.
- `mod/` changes are limited to `graphics/tilesets/urban-tileset.png`,
  `data/tilesets/urban-tileset.tileset.json` and its generated `.tileset.md`.

## Also

The row axis of the cut is still the plain canvas division — `GridFit` refused
it, and it sits roughly 9px off the drawn cells. The column axis is fitted
(origin 2.31, pitch 123.25). Correcting the row axis is worth doing in the same
sitting, because it is the other half of the reason this sheet was going to be
re-exported at all.
