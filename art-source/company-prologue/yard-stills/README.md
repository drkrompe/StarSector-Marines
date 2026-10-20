# Yard stills

Status: SHIPPED — Y1, Y2 and P7 pages in the game (2026-10-19)
Written: 2026-10-19

The pictures on the company yard screens after the Jangala arrival cinematic
(`CompanyYardScreen`, `storyboard-v3.md` rows Y1–Y2) and the prologue page P7 they pair with.

| Page | Shipped file | Source |
|---|---|---|
| Y1 estimate | `mod/graphics/illustrations/company/yard-hull.jpg` | Two layers, packed by `python compose_y1.py --pack`: `yard-hull-bg-v3.png` (yard background, no foreground people, painted over `guides/y1-guide-v7.png`) and `nessa-y1-cutout-v1.png` (Nessa on transparency, placed from `guides/nessa-y1-silhouette.png`). It has no `story-stills` master, so `pack.ps1` leaves it alone. |
| P7 before conversion | `cargo-billets.jpg` | `cargo-billets-v2.png`, a repaint of the old picture: Sabine's twelve asleep beside their rifles. Copied to `story-stills/cargo-billets.master.png`, packed by `pack.ps1`. |
| Y2 after conversion | `berths-fitted.jpg` | `berths-fitted-v2.png`, the matching after picture of the same hold. Copied to `story-stills/berths-fitted.master.png`. |

Every generation keeps its exact prompt and provenance beside it.

| Script | What it does |
|---|---|
| `y1_guide.py` | Renders the Y1 guide from the C5 yard scene (`industrial-exterior/c5_scene.py`): a level eye-height camera that picks a clear spot by ray-casting. `--no-figure` leaves Nessa's stand-in out. One half-size still, about 15 minutes, because it builds the whole port. |
| `nessa_silhouette.py` | Renders Nessa's stand-in figure alone from a guide's camera, in seconds, as the cut-out's placement guide. |
| `compose_y1.py` | Joins the layers: scales and places the cut-out, cools her toward the yard's daylight, adds a soft shade, and `--pack` writes the page. |

The yard pictures are layered (owner, 2026-10-19): backgrounds carry no foreground character,
and a person is a separate cut-out, so they can be changed or left out without repainting.

## Open

- Nessa's wall of old pressure seals (Castle's Y2 completion line) has no picture.
- Nessa's portrait is not used in any dialogue: every yard line is Castle's, and the owner
  asked for no new lines for now.
- C5's crowd still draws its own dark-coated stand-in for Nessa.
