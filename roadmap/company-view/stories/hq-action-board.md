# HQ action board

Status: IN PROGRESS — implemented in all four steps; live campaign acceptance open
Written: 2026-10-18
Updated: 2026-10-18 — implemented; open questions decided below.

Read `company-view-nouns.md` (Campaign-map home) and `hq-command-deck.md` first.
The board's offers half depends on `c11-the-contract-board.md`.

## Outcome

At a glance, Company HQ says what needs the captain: what is overdue, what the story
is waiting on, what letter wants an answer and what side work is open. Each item has
one answer: respond, plot course or open. The answer is drawn as a holographic board
standing in the air above the deck's plot table. Morgan leans on the table's rim,
reading it. A plain list below the scene carries the same items for keyboard,
accessibility and detail.

## Problem

The lower half of HQ is built around what the company has always had rather than what
needs attention:

- **One story lead at a time.** The opening-work card shows a single arc. When several
  arcs are live, the last one checked wins (Agreus, then Shepherd, Corvus, Supply Route,
  Opening) and the rest are invisible.
- **Side work is elsewhere.** Distress calls, dead letters, defector asylum and last
  testaments live only in Starsector's intel screen. Contract offers are planned for the
  contract board and have no HQ surface.
- **Weight is upside down.** The news column takes most of the width for flavour
  reports, while obligations get a narrow column and finance a whole column of
  reference figures.

## Vocabulary

- **Action item**: one thing the captain can act on, with exactly one primary action.
  Its kinds:
  - **Obligation**: a clock that bites (`CompanyClocks`: response, term ending,
    recovery). Answered by Respond; a term ending with nothing to answer is tracked
    only.
  - **Story lead**: the current step of a live story arc, one per arc. Answered by
    Plot course to that step's destination.
  - **Letter**: correspondence awaiting a decision (auxiliary warrant, Latchkey
    custody). Answered by Open.
  - **Side job**: an open intel event with a place to go (distress call, dead letter,
    defector asylum), and later a contract-board offer. Answered by Plot course; its
    commit or refuse choice stays in its intel entry. With no resolvable market the
    course is unavailable and says why. Capped at four, soonest deadline first.
- **Filter**: All, Story, Contracts (obligations and, later, offers) and Other (letters
  and side jobs). Each tab shows its count.
- **Holo board**: the projection of the action items above the plot table.
- **Action list**: the same items as rows below the scene, with a detail pane.

## Laws

- **The board invents nothing.** Every item projects an existing authority: the
  clocks, an arc's progress and destination, a desk's stage, or an intel event's state.
  HQ adds no persisted state.
- **One primary action per item**, named by verb. An item with no possible action
  (a tracked term) says why instead of offering a disabled button.
- **Urgency orders the list.** Overdue and due-soon obligations first, then story
  leads, letters and side jobs. Within a kind, soonest deadline first.
- **Bounded, with counts.** Story is naturally bounded at one lead per live arc.
  Obligations keep their cap of six. Side jobs are capped too, and each list states its
  hidden count.
- **The picture never holds a fact of its own.** Everything on the holo board is also in
  the action list, which is keyboard-reachable and readable without the scene.
- **Plot course behaves as today**: it sets the ordinary campaign navigation
  destination and closes HQ. An unavailable destination disables it with the reason.

## The holo board

A three-dimensional projection standing in the volume above the plot table, drawn live
and animated: SVG glyphs and lines, projected through the deck camera so it sits in the
room's space.

- **Form:** a tilted sector plot hovering over the table. Story leads, side jobs and
  obligations appear as markers by destination, each with a short stem down to the
  table and a label. Urgent obligations pulse. Without a sector position, a letter sits
  as a card in the board's near corner.
- **Motion:** a slow rotation or scan, markers rising in when an item first appears, and
  a selection beam from the table to the chosen marker. Timing is view-local, like the
  intel motion.
- **Interaction:** a marker or label is a real button, positioned from the same
  projection, as the deck stations are. Hovering lights it. Choosing it selects the item
  in the list and detail pane. Its primary action stays in the detail pane, so a stray
  click on the picture never flies the ship.
- **Light:** the board casts cool light on the table top and on Morgan's face and hands,
  already painted into her card. Castle stands behind the table, inside the board's
  volume; see Open questions.

## The lower half

```
+--------------------------------------------------------------------------------+
| deck scene, holo board above the plot table                                     |
+--------------------------------------------------------------------------------+
| command strip: rating | Castle's assessment | cash and runway | debug           |
+--------------------------------------+-----------------------------------------+
| [All 5] [Story 1] [Contracts 2] [Other 2]                                       |
| Due 1 day  Garrison defense, Sindria   RESPOND  | Detail: the selected item     |
| Story      Corvus account, Asharu      PLOT     | (the story brief, the letter, |
| Letter     Hegemony procurement terms  OPEN     |  the obligation's terms)      |
| Other      Distress call, Eochu Bres   PLOT     |                               |
| 12 days    Stationing term ends        TRACKING | Wire: three latest headlines  |
+--------------------------------------+-----------------------------------------+
```

- **Action list:** the filters, then one row per item: kind and urgency, title and
  place, and the primary action.
- **Detail pane:** the selected item in full. The story brief (contact, location,
  mission, payment, preparation) moves here from its permanent card. Letters open their
  existing correspondence. Under it, a three-headline wire.
- **Cut:**
  - **Finance column:** becomes cash and runway in the command strip, with the full
    figures behind the quartermaster's station.
  - **News column:** the source windows, dossier and archive leave the page's main
    area. The comms station opens them in the detail pane.

## Stations

Each station puts something in the detail pane instead of only pulsing a panel:

| Station | Shows |
| --- | --- |
| Plot table and Morgan | Selects the most urgent item and focuses the action list. |
| Comms | The intel archive: source windows, dossier and rundown. |
| Castle | Her daily assessment, with the guidance she is giving now. |
| Quartermaster | Full finances: runway, cash, upkeep, net and retainers. |

## Acceptance

1. With several story arcs live, each shows as its own lead; none hides another.
2. Every row's action works: Respond follows the event notice's deployment route, Plot
   course sets the course and closes HQ, and Open opens the letter.
3. Filters show correct counts; capped lists state their hidden counts.
4. The holo board shows the same items as the list, animates, and its markers select
   list rows; no information appears only on the board.
5. At 1920x1080 / 100% the scene, command strip, list and detail pane fit without
   scrolling in the common case: up to eight items.
6. Evidence: unit tests for item derivation, ordering, filtering and caps; the
   `hq-intel` snapshot suite renders the board with each kind of item present.

## Implementation order

1. Action-item derivation and ordering as a headless unit, with tests.
2. Action list, filters and detail pane; finance and news cut to the strip and wire;
   station behaviour.
3. Holo board projection and drawing (static).
4. Holo motion and marker buttons.

## Decisions

- **Castle** moved to the table's far-left end, clear of the board's volume.
- **Side jobs** are answered by Plot course to their market; the commit or refuse
  choice stays in the intel entry, which Starsector's own screen already owns.
- **Board scale** is true hyperspace position (about ±25,000 units to the rim,
  clamped inside it); items without a place sit along the near rim in board order.
- **Contract offers** join Contracts once the contract board exists. Until then the
  filter holds obligations only.

## Remaining

- Live campaign acceptance (acceptance 2): Respond, Plot course and Open from a real
  save, and several arcs live at once.
- Keyboard traversal across the holo marker buttons in a live session.
- Obligations past the cap of six are dropped without a hidden count; side jobs state theirs.
