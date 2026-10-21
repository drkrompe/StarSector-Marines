# Closable sally doors

Status: IN PROGRESS

Written: 2026-10-21

## The problem

A sally door (`garrisonables.md`, sally doors) opens only for the side holding
its place whole and then stays open to every side for the rest of the battle,
because the navigation grid could only become more permissive at runtime. A
sortie is therefore a permanent hole in the holder's own wall: the first
conquest-trial reading found four of the five extra marine wins came after
marines used a port of a fort they had taken, and a defender that sorties once
has handed the attack a second gate for good. A sortie should be a tactical
choice the holder can take back.

## Rules (decided for fun and fairness)

- **Who shuts.** The side that holds the door's place whole, the same side
  that may open it. Doors are written for sides, never for the side that opened
  one: a place that changes hands serves its new holder, open or shut.
- **When the holder shuts its own door.** On the door pulse, an open door of a
  place held whole is shut when
  - a hostile body on the ground comes within the door's safe distance of the
    mouth (the enemy is approaching: shut the door in its face), or
  - the sortie has passed: no squad or machine of the holder wants it (an order
    out through it, or home through it) and no body of the holder's stands near
    it, for a few seconds.
- **Never on a body.** A door does not shut while any body on the ground, of
  any side, stands in or overlaps its cells; it shuts on the first pulse it is
  clear. No body is ever pushed or crushed.
- **Coming home.** A holder's squad or machine outside the place, near the
  mouth and on the door's face, ordered to ground inside the place, wants the
  door too, so a sortie is a round trip rather than one way.
- **Hysteresis.** A door that changed state does not reopen within a dwell
  time; shutting in the enemy's face is never delayed. Each change costs a
  full zone rebuild, and a door flapping at the edge of the safe distance would
  be noise.
- **The player's order.** The player bars or unbars a door of a place their
  side holds whole with a key over it. A barred door stays shut against the
  side's own reflexes (no order opens it) until unbarred; an unbarred door is
  opened at once, even with the enemy near (the player's call), and is not shut
  by the reflexes until barred again or the place changes hands. A bar ordered
  while a body stands in the door shuts it the moment the door is clear.
- **Forcing a closed door.** A shut door is wall with the wall's strength, as
  before: an enemy forces it by breaching it, with the charges and fire it would
  spend on any wall. A door is not weaker than its wall (the attack is never
  given a door it did not cut) and not stronger (a door is not a keep). A door
  any cell of which has been breached is broken: it is a hole and never shuts
  again.
- **The holder loses the place.** An open door does not shut by itself when the
  holder loses the place. A split or contested place opens and shuts nothing,
  and a door left open by a holder that lost its place is the attack's: that is
  the sortie's risk. Shutting on loss would hand the loser a free wall at the
  moment it lost and cut an assault's route under it. The new whole holder
  inherits the door and may shut it.

## Engineering: closing a door

Shutting restores each door cell to exactly the state recorded when it opened:
the navigation flags, edges and wall hit points, the topology's wall, window,
fixture, ground and surface, and the cover its neighbours had towards it. What
derives from the grid then has to see a cell become **less** permissive, which
nothing had to before.

- Grid-keyed derivations (path components, opacity revision, line-of-sight
  caches, the vehicle clearance mask and its labels, the greedy mesh) are
  invalidated by the grid's own revision and change log; anything that assumed
  a change could only open is made to rebuild or recompute around a closing.
- The zone graph's incremental update only merges zones, so a closing forces
  its full rebuild at the tick's topology flush, and the derived layer
  (mesh, vantage cache, shared-goal fields, squad route batch and jobs) is
  rebuilt with it.
- Every retained route that crosses a shut door is dropped: unit paths, mech
  continuous routes and their pending clearance jobs, defend-track routes and
  anything else that trusted an older route to stay legal.

## Acceptance

- Unit tests on the restore and on each invalidation, each under a second.
- `sally-door` and `sally-port` gain loops: a sortie goes out and the door shuts
  behind it; an enemy approaching an open door shuts it; a shut door blocks the
  enemy's path; a door with a body in it does not shut until the body leaves.
- `simDeterminism` green.
- A conquest-trial comparison against `-Dbattle.sallyDoors.closable=false`:
  hostile use of doors opened by the other side falls.

## Plan

1. `MapEditor.closePassage` and the grid/topology restore; navigation marks a
   closing, the flush rebuilds in full and drops crossing routes.
2. `SallyDoors`: open records, shut rules, bodies, coming home, hysteresis,
   broken doors, the player's bar, fort leaves folded away again.
3. Scenes, tests, evidence; player key in `BattleScreen`.
4. Fold into `garrisonables.md`, `forts.md`, `ai-nouns.md`; ledger; delete.
