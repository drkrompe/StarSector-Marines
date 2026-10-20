# Player reactive manning

Status: IN PROGRESS

Written: 2026-10-20

## Intent

Owner decision 2026-10-20 (`garrisonables.md`, Decided direction): a player's
idle squads may opt into reactive manning. Opt-in, never a default; the manual
occupy and line orders stay the primary verbs. Where a choice is open, prefer
the one that adds fun.

The fun is in the delegation: a player parks a squad by a street of trenches
and buildings, flicks one switch, and the squad jumps into whichever of them the
enemy leans on, then comes back when it passes. It reads as a squad with a job
("watching this corner") rather than as automation.

## Decisions

- **What the player toggles: a per-squad GARRISON stance.** A second card on the
  squad order bar, `[G] GARRISON`, beside `[F] DEFEND AREA`, and the `G` key.
  With several squads selected it turns the stance on for all of them unless all
  already have it, in which case it turns it off. Infantry only: machines never
  man (`garrisonables.md`). The stance is not an order; it is a standing
  permission the squad acts on only while it carries no player order.
- **Where it looks: a watch around an anchor.** The anchor is where the squad
  stood when it began watching: when the stance was turned on, or when a player
  order it was carrying ended (a move order arriving). It considers only places
  within the **watch radius** of that anchor, twenty cells, the same ring as a
  defend area, so the player already knows how big it is. A squad the mission
  commander walks well outside its watch re-anchors where it stands, so the watch
  goes with the squad and is never a reason to march back across the map.
- **Which places count.** Fighting-position runs (the garrisonable registry) and
  held places (the buildings a map publishes), on the player side's ground: not
  inside a compound the marines do not hold (a click there secures it), and not
  believed to hold an enemy (that is a clear order, which stays the player's to
  give). A place another marine squad is manning or has been ordered onto is
  taken: pickets before massing, one squad per place.
- **How it decides.** The same laws as the defender commander, from the player
  side's own influence picture: a place is pressed at the start level, the most
  pressed is manned first, ties to the nearer; the squad is released only after
  the pressure has stayed under the lower release level for the dwell, and never
  while it is in a fight. A squad below useful strength is never sent and falls
  back if it drops below it on a place; a believed enemy inside its place near it
  is a breach, it falls back, and that place is not manned again by it for a
  while. The thresholds are the assignment tier's own (`GarrisonDirector`), not a
  second set. The decision runs per squad on a 2.5 s pulse, staggered by squad id.
- **What it does.** Manning is the very order a click would give: an occupy on a
  run, a hold on a held building. Release and fall back walk the squad back to its
  anchor (an attack move that ends on arrival), and it keeps watching.
- **How it reads.** The card's detail line says what the stance is doing: OFF,
  WATCHING, MANNING TRENCH, HOLDING BUILDING, RETURNING, UNDER ORDERS. With the
  squad selected the map draws its watch ring around the anchor, and a manned
  trench shows the occupy circle every occupy shows.
- **Explicit orders win.** Any player order the squad is given replaces the
  stance's own order on the spot (the order mailbox is the same) and suspends the
  stance while it stands. When it ends the stance resumes from where the squad
  stopped. Turning the stance off releases whatever order the stance itself gave,
  and nothing the player gave.

## Constraints

- The assignment tier stays side-neutral: nothing in `GarrisonDirector` changes.
  The squad-scope decision is its own pure class beside it (`ReactiveWatch`),
  reading the director's thresholds.
- Belief only: pressure is the marine side's influence snapshot and the breach and
  enemy-held tests read that side's believed contacts.
- Bounded cost: per watching squad per pulse, the registry's block lookup for runs
  and a block bucket of held places, one read per covered block. Nothing scans
  every garrisonable; nothing runs per tick.

## Acceptance

- Unit tests on `ReactiveWatch`: start threshold, most pressed then nearest,
  taken / enemy-held / locked-out places skipped, outside the watch skipped,
  useful-strength floor; hysteresis (held between the levels, released only after
  the dwell, never in a fight), breach and depletion fall back.
- Behaviour scene `player-reactive-manning`, mechanics only:
  - `opted-in`: an opted-in idle squad beside a trench mans it within a few pulses
    of its side believing the trench pressed, digs in, and once the pressure has
    passed is released and walks back to its anchor.
  - `not-opted`: the same squad without the stance does nothing.
  - `explicit-order`: an opted-in squad given a player move while it mans the
    trench takes the move at once; the stance does not re-take the trench while
    the move stands.

## Plan

1. `ReactiveWatch` (pure, `battle.command`) and its tests.
2. `ReactiveManningService` / `ReactiveManningSystem` (`battle.squad`), issuing
   through `SquadMoveOrderSystem`'s own activation paths; wired after the order
   system in the tick.
3. UI: order-bar card and key, watch ring highlight.
4. Scene; noun docs (`ai-nouns.md`, `garrisonables.md`); fold and ledger.
