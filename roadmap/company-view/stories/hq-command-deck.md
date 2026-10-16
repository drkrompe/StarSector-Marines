# HQ command deck

Status: PLANNED — prompts drafted; art not generated, layout not implemented
Written: 2026-10-16

Read `company-view-nouns.md` (Company HQ) first.

## Outcome

Company HQ stops being a wall of panels and becomes a place: a painted banner of
the company's operations deck, with Castle, two comms officers, a commander at the
chart table and a quartermaster on watch. The scene replaces the abstract intel
network drawing and the separate assessment strip. Each station on deck is a
hotspot for the panel that already holds its information, so the picture shows
where things live without becoming the only way to reach them.

## Scope and laws

- **One painted plate, not a live scene.** The art lives in
  `art-source/hq-command-deck/` (`prompts.json`, master, pack script) and ships a
  downsampled banner the way `story-scenes` does. The generic deck is the company's
  home whatever its flagship; per-hull plates are out of scope.
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

Today the HQ is: page nav, a 94px assessment strip, then a 406px sidebar (force,
finance, standing) beside the situation board (obligations column and the news
column, whose top 300px is the intel network canvas).

Proposed:

```
+--------------------------------------------------------------------------------+
| page nav                                                                        |
+---------------------+----------------------------------------------------------+
| SIDEBAR 406px       | COMMAND DECK BANNER  (board width ~1486 x 300)            |
|  force status       |  [comms]  [chart table + commander]  [Castle] [ledger] [hatch]
|  finance            |  +-----------------------------------------------------+ |
|  standing           |  | RATING  B+  | CASTLE // BRIDGE ADJUTANT  "Assessment"| |  <- 72px caption
|                     |  +-----------------------------------------------------+ |
|                     +----------------------------------------------------------+
|                     | situation board heading                                  |
|                     | obligations | source windows / dossier / rundown         |
+---------------------+----------------------------------------------------------+
```

- The banner sits inside the board column, above the board heading, so the sidebar
  keeps its full height. Dropping the 94px assessment strip gives that height to
  the whole main area; the banner then takes the 300px the network canvas had, so
  the sidebar and news column each come out about 100px taller and only the
  obligations column gets shorter (about 200px). It normally holds a handful of
  clocks.
- **Caption strip:** the bottom 72px of the banner, over a dark gradient scrim
  (`background-image` linear gradient). Left: `ratingHeader` / rating / score.
  Right: Castle's name plate and the daily `assessment` text. The plate's
  lower-right is painted quiet for this.
- **Crop:** the master is 3:1 and the banner is about 5:1, so the centre band is
  shown and the painted ceiling and deck are cut. The retained `<img>` has no
  cover-crop today; either the pack step writes the 5:1 band at a fixed size and the
  banner keeps that aspect, or `UiElement` gains a bounded `object-fit: cover`.
  Decide when implementing; a banner that stretches is not acceptable.
- **Other resolutions** keep the same banner aspect and let the board below absorb
  the difference.

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
| Quartermaster's ledger | FINANCE & STANDING | Scrolls the sidebar to finance and pulses it. |
| Hatch and roster board | FORCE STATUS | Scrolls the sidebar to force status and pulses it; the existing Barracks route stays on that panel. |

## Alert state

When an obligation is overdue (`due now`), the banner swaps to the
`command-deck-alert` plate, the same composition under red-amber light. It is
generated only after the main plate is approved, as an edit of that master, and is
optional for the first cut.

## Acceptance

1. The plate matches the prompt's station order, keeps every figure inside the
   centre band, and Castle matches her portrait.
2. HQ at 1920x1080 / 100% shows the banner with caption, no network canvas and no
   assessment strip; rating and assessment text are fully readable over the scrim.
3. Every hotspot is reachable by keyboard and pointer, shows its label, and points
   at a panel that is visible at the same time.
4. Long assessment text, no employers, no obligations and an overdue obligation all
   render without overlap. The alert plate appears only while something is overdue.
5. Evidence comes from the existing `HqIntelSnapshotSuite` or one small HQ suite,
   rendering only the states above, not a new broad matrix.

## Implementation order

Generate and approve the master; pack the banner and measure station rectangles;
replace the canvas and strip in `company-hq.mlx` and the view model; add hotspots
and panel pulses; snapshot evidence; optional alert plate.

## Open questions

- Before the company has a ship, HQ shows the founding flow. Does it keep this deck
  (a borrowed office) or a different dockside plate?
- Should the commander figure follow the founding commander (Sabine Hale) rather
  than the player? The draft keeps the player's faceless stand-in.
