# HQ command deck

Status: IN PROGRESS — banner, caption and station hotspots shipped on HQ; edge-case evidence and the alert plate remain
Written: 2026-10-16
Updated: 2026-10-18 — sidebar and notice rows removed; banner full width, finance moved under the scene.

Read `company-view-nouns.md` (Company HQ) first.

## Outcome

Company HQ stops being a wall of panels and becomes a place: a painted banner of
the company's operations deck, with Castle, two comms officers, a commander at the
chart table and a quartermaster on watch. The scene replaces the abstract intel
network drawing and the separate assessment strip. Each station on deck is a
hotspot for the panel that already holds its information, so the picture shows
where things live without becoming the only way to reach them.

## Scope and laws

- **Painted layers, not a live scene.** The art lives in `art-source/hq-command-deck/`
  (`prompts.json`, masters, briefs, provenance). A Blender blockout
  (`blender/deck_blockout.py`) is the layout authority; the **deck plate**
  (`deck-plate-v5.master.png`, approved) is painted over its guide, and one
  transparent **figure card** per person (Castle, the comms pair, the commander,
  the quartermaster, Sabine Hale) is cut by its stand's occluder matte.
  `pack.py` writes the shipped band, figure sprites and
  `mod/data/ui/hq-command-deck.json`; a repaint registered on the same guide needs
  only a re-run. Layers let a station highlight its own figure, let an alert
  relight the deck alone, and let one figure be redone without repainting the scene.
  The generic deck is the company's home whatever its flagship; per-hull plates are
  out of scope.
- **Live overlays.** Console screens and the chart-table top are painted blank so the
  game can draw over them: readouts, blinking lamps and a live plot on the table.
  Stations sit in fixed bands so a station can later become its own layer (for
  example one that appears as the company grows) or carry motion.
- **Remove** the `company-hq-network-canvas` intel drawing and the
  `company-hq-assessment` strip. Every fact they carried still appears: the rating
  and Castle's daily assessment move onto the banner, and the source windows,
  rundown and dossier stay where they are.
- **The picture never holds information on its own.** Hotspots point at panels that
  stay on screen. Every hotspot is a real, keyboard-focusable button with a
  label; nothing is reachable only by clicking a painted person.
- **The commander is the player's stand-in** and is painted from behind, face
  hidden, so it never contradicts who the player imagines the captain to be.
- **Castle on deck must read as the Castle of her portrait.** Generate with
  `castle-master.png` as a reference where the tool accepts one; reject a plate
  where she doesn't match.

## Layout at 1920x1080 / 100%

The HQ has no sidebar. Under the page nav, one full-width board holds the command
deck banner (372px) and, below its heading, three columns: company finance,
active obligations, and the news column.

```
+--------------------------------------------------------------------------------+
| page nav                                                                        |
+--------------------------------------------------------------------------------+
| COMMAND DECK BANNER (full width x 372)                                          |
|  [comms]  [plot table + commander]  [Castle]  [quartermaster]  [Sabine]         |
|  +--------------------------------------------------------------------------+   |
|  | RATING | BRIDGE ADJUTANT // DAILY ASSESSMENT                              |   |  <- 72px caption
|  +--------------------------------------------------------------------------+   |
+--------------------------------------------------------------------------------+
| board heading                                                                   |
| FINANCE 260 | OBLIGATIONS 260      | source windows / dossier / archive         |
|             |  waiting letters     |                                            |
+--------------------------------------------------------------------------------+
```

- Removed with the sidebar: force status, the change-ship and ship-view tiles
  (Engineering stays on the page nav), and client standing, including its
  military-warrant rows. The long notice rows under the nav are gone too; first
  refit and secondhand wings remain nav notices on Barracks and Boats.
- Letters awaiting an answer (the auxiliary warrant, the Latchkey custody
  agreements) sit at the head of the obligations column, since they are
  decisions owed.
- **Caption strip:** the bottom 72px of the banner, over a flat dark scrim. The
  packed band is chosen so the plotting table's near rim sits above it. Left: `ratingHeader` / rating / score.
  Right: Castle's name plate and the daily `assessment` text. The plate's
  lower-right is painted quiet for this.
- **Crop (decided):** the pack writes a ~5:1 band of the 3:1 master, and the banner
  is a canvas that **cover-fits** it: the document never scales up, so the banner's
  aspect varies with resolution, and a box wider than the band loses ceiling and
  deck while a narrower one loses the side banks. Stations are positioned in code
  from the same fit, so they stay over their people.

## Stations

Hotspot rectangles are authored as fractions of the packed banner in a small
layout file beside the art, so a regenerated plate only needs its rectangles
re-measured. Hotspots are absolutely positioned buttons (`UiPosition.ABSOLUTE`):
a faint outline and label chip on hover or focus, a short pulse on the target
panel when used.

| Station on deck | Label | What using it does |
| --- | --- | --- |
| Comms bank, two officers | COMMS // INTEL | Focuses the transmission archive and pulses the source windows. |
| Chart table, commander | SITUATION BOARD | Focuses the first obligation; with no obligations, pulses the board heading. |
| Castle | CASTLE // ADJUTANT | Pulses the assessment caption; where the opening guide is active, focuses its next control. |
| Quartermaster's ledger | FINANCE & STANDING | Pulses the finance column. |
| Sabine Hale | FORCE // BARRACKS | Opens Barracks, where the force is. |

The theme dresses every button and is the cascade's final layer, so stations are
cleared with inline style and their hover/focus outline is drawn by the banner
canvas (corner brackets, the station's people lit additively). A report arriving
lights the comms pair the same way; the plotting table carries a radar sweep with
one contact per received report.

## Alert state

When an obligation is overdue (`due now`), the deck plate swaps to
`deck-plate-alert`, the same composition under red-amber light, generated as an
edit of the approved plate. Figure cards stay as they are; the live overlays can
carry the urgency too. Optional for the first cut.

## Acceptance

1. The deck plate matches the prompt's station order inside the centre band; each
   figure card has true transparency, matches the plate's lighting, and Castle
   matches her portrait.
2. HQ at 1920x1080 / 100% shows the banner with caption, no network canvas and no
   assessment strip; rating and assessment text are fully readable over the scrim.
3. Every hotspot is reachable by keyboard and pointer, shows its label, and points
   at a panel that is visible at the same time.
4. Long assessment text, no employers, no obligations and an overdue obligation all
   render without overlap. The alert plate appears only while something is overdue.
5. Evidence comes from the existing `HqIntelSnapshotSuite` or one small HQ suite,
   rendering only the states above, not a new broad matrix.

## Implementation order

Generate and approve the deck plate, then the figure cards; pack the layers and measure station rectangles;
replace the canvas and strip in `company-hq.mlx` and the view model; add hotspots
and panel pulses; snapshot evidence; optional alert plate.

## Open questions

- Before the company has a ship, HQ shows the founding flow. Does it keep this deck
  (a borrowed office) or a different dockside plate?
- Sabine stands on deck as herself (FORCE // BARRACKS); the commander stays the
  player's faceless stand-in.

## Remaining

- Acceptance 4: render long assessment, no employers, no obligations and overdue
  states; check keyboard traversal reaches all five stations.
- Optional alert plate.
