# HQ command deck

Status: IN PROGRESS — banner, command strip and station hotspots shipped on HQ; edge-case evidence and the alert plate remain
Written: 2026-10-16
Updated: 2026-10-18 — lower half is now the action board (`hq-action-board.md`); stations answer into its detail pane.

Read `company-view-nouns.md` (Company HQ) first.

## Outcome

Company HQ stops being a wall of panels and becomes a place: a painted banner of
the company's operations deck, with Castle, two comms officers, Morgan Hale and a
quartermaster on watch. The scene replaces the abstract intel
network drawing and the separate assessment strip. Each station on deck is a
hotspot for the panel that already holds its information, so the picture shows
where things live without becoming the only way to reach them.

## Scope and laws

- **Painted layers, not a live scene.** The art lives in `art-source/hq-command-deck/`
  (`prompts.json`, masters, briefs, provenance). A Blender blockout
  (`blender/deck_blockout.py`) is the layout authority; the **deck plate**
  (`deck-plate-v5.master.png`, approved) is painted over its guide, and one
  transparent **figure card** per person (Castle, the comms pair, the quartermaster,
  Morgan Hale) is cut by its stand's occluder matte.
  `pack.py` writes the shipped band, figure sprites and
  `mod/data/ui/hq-command-deck.json`; a repaint registered on the same guide needs
  only a re-run. Layers let a station highlight its own figure, let an alert
  relight the deck alone, and let one figure be redone without repainting the scene.
  The generic deck is the company's home whatever its flagship; per-hull plates are
  out of scope.
- **Live overlays.** Console screens and the chart-table top are painted blank so the
  game can draw over them: readouts, blinking lamps and a live plot on the table.
  The lit monitor faces are the one thing measured off the painting rather than the
  guide (ImageGen moved them): `displays.json` holds each face's corners and content
  kind, the pack carries them, and `DeckDisplays` draws animated readouts in each
  face's perspective. Faces a painted operator sits in front of stay as painted.
  The readouts are scenery, not information: nothing on them is a fact.
  Stations sit in fixed bands so a station can later become its own layer (for
  example one that appears as the company grows) or carry motion.
- **Remove** the `company-hq-network-canvas` intel drawing and the
  `company-hq-assessment` strip. Every fact they carried still appears: the rating
  and Castle's daily assessment move onto the banner, and the source windows,
  rundown and dossier stay where they are.
- **The picture never holds information on its own.** Hotspots point at panels that
  stay on screen. Every hotspot is a real, keyboard-focusable button with a
  label; nothing is reachable only by clicking a painted person.
- **No figure stands in for the player.** A faceless commander at the plot table
  was tried and removed: it read as a stranger, not as the player.
- **Castle on deck must read as the Castle of her portrait.** Generate with
  `castle-master.png` as a reference where the tool accepts one; reject a plate
  where she doesn't match.

## Layout at 1920x1080 / 100%

The HQ has no sidebar. Under the page nav, one full-width board holds the command
deck banner (372px), the command strip, and the action board from
`hq-action-board.md`: the action list beside its detail pane. Nothing is laid over the
scene except the holo board above the plot table, its marker buttons and the station
hotspots, which are invisible until pointed at.

```
+--------------------------------------------------------------------------------+
| page nav                                                                        |
+--------------------------------------------------------------------------------+
| COMMAND DECK BANNER (full width x 372)                                          |
|  [comms]  [plot table + Morgan]  [Castle]  [quartermaster]               |
+--------------------------------------------------------------------------------+
| RATING  RECOGNIZED  +24 | BRIDGE ADJUTANT // DAILY ASSESSMENT | RUNWAY, CASH [debug]|  <- command strip
+--------------------------------------------------------------------------------+
| [All] [Story] [Contracts] [Other]           | detail pane (640)                  |
| action rows, one action each                | wire: three headlines              |
+--------------------------------------------------------------------------------+
```

- Removed with the sidebar: force status, the change-ship and ship-view tiles
  (Engineering stays on the page nav), and client standing, including its
  military-warrant rows. The long notice rows under the nav are gone too; first
  refit remains a nav notice on Barracks.
- Letters awaiting an answer (the auxiliary warrant, the Latchkey custody
  agreements) are action items of their own, answered by Open.
- **Command strip:** the row under the banner (`company-hq-board-heading`) holds the
  mercenary rating and Castle's daily assessment, with the debug-missions button at
  its end in debug builds. It replaced a caption laid over the scene's foot, which
  covered the table and the nearest figures, and the board's title and intro
  sentence, which the source windows below make redundant. The packed band is chosen
  to keep every head inside its top and reach as far down the deck as that allows.
- **Crop (decided):** the pack writes a ~5:1 band of the 3:1 master, and the banner
  is a canvas that **cover-fits** it: the document never scales up, so the banner's
  aspect varies with resolution, and a box wider than the band loses ceiling and
  deck while a narrower one loses the side banks. Stations are positioned in code
  from the same fit, so they stay over their people.

## Stations

Hotspot rectangles are authored as fractions of the packed banner in a small
layout file beside the art, so a regenerated plate only needs its rectangles
re-measured. Hotspots are absolutely positioned buttons (`UiPosition.ABSOLUTE`):
a faint outline and label chip on hover or focus, a short pulse on the panel
they answer into.

| Station on deck | Label | What using it does |
| --- | --- | --- |
| Comms bank, two officers | COMMS // INTEL | Shows the transmission archive in the detail pane and focuses its tabs. |
| Plot table, Morgan | SITUATION BOARD | Selects the most urgent action item under All and focuses the action list. |
| Castle | CASTLE // ADJUTANT | Shows her daily assessment and her first priority in the detail pane. |
| Quartermaster's ledger | FINANCE & STANDING | Shows the full finances in the detail pane. |

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
2. HQ at 1920x1080 / 100% shows the banner unobstructed, with rating and assessment
   fully readable in the command strip beneath it.
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
- Morgan stands on deck as herself, leaning on the plot table's right rim and reading
  the space above it, where `hq-action-board.md` puts its holographic board. Her card
  is painted over a Blender pose guide; a posed figure that touches furniture gets a
  depth-clipped matte, so her hands stay on the table top.

## Remaining

- The bottom-half pass and the holographic board: `hq-action-board.md`.

- Acceptance 4: render long assessment, no employers, no obligations and overdue
  states; check keyboard traversal reaches all four stations.
- Optional alert plate.
