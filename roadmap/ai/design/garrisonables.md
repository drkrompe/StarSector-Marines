# Garrisonables

Status: ACTIVE — reactive manning is built for fort-belt fighting positions; other kinds remain direction.

Written: 2026-10-13

Updated: 2026-10-14 — story-map inventory: windows read correctly; half the story maps publish no held places.

## Why this exists

Fighting positions shipped with a fort belt garrisoned from the first tick: every
belt nest seats a squad in its trench whether or not anybody ever comes near it.
On the fort control that took 120 defenders out of the patrol pool and left the
defender with no roving reserve at all. The number of places worth holding is
about to grow sharply — sandbag aprons at compounds, building shells with
windows, emplacements — and a model in which every such place is filled because
it exists will spend the whole defending force on ground nobody is attacking.

The direction here is that a place worth holding is **manned in answer to
threat**, not filled by default.

## Nouns

A **garrisonable** is a place that offers **posts** to a squad that mans it: a
set of stand cells, each with the cover it gives and the directions it covers.
It is a kind of place, not a kind of order. Kinds, in the order they are
expected to arrive:

- a **fighting-position run** — trench and parapet cells (built; see
  `ai-nouns.md` and prepared ground in `mapgen-nouns.md`);
- a **building envelope** — the apertures and stance cells the defense frontage
  already derives (`ai-nouns.md`, frontage and standing to);
- a **compound perimeter** — the outer envelope at compound scope, which frontage
  also already derives;
- an **emplacement** — a crewed structure, later and outside this direction's
  first slices.

**Manning** a garrisonable is squad-tier: which member takes which post, claims,
digging in, facing the believed threat, and falling back. **Assignment** is
commander-tier: which squad mans which garrisonable, and when it stops. The two
tiers must stay separate — a squad never decides to go and man a trench nobody
sent it to, and a commander never chooses a marine's cell.

Garrisonables may be **linked into layers**: a belt in front of a fort's works,
a compound perimeter around its buildings, a building shell around its rooms. A
layer is what a squad falls back to when the one in front of it is lost.

## Laws

- **Reactive manning.** A garrisonable is manned in proportion to believed
  pressure on it, read from the side's own faction influence, never from hostile
  truth. A quiet garrisonable is empty and its would-be garrison stays in the
  mobile pool, except for a token picket where a place must be watched to be
  defended at all: pressure is belief, and an empty place nobody can see raises
  none. A fort keeps a small picket at its main gate, and its guns report what
  they engage, so even an unmanned fort raises the alarm that brings its
  garrison. This generalises what standing to already does for a held
  building's apertures, from "which windows" to "which places".
- **Hysteresis.** Manning starts above one pressure level and ends below a lower
  one, after a dwell, so a squad is not marched in and out of a trench on every
  command pulse. Dig-in is lost on leaving, which is the cost that makes churn
  visible.
- **Declared holds stay held.** A place the mission says must be held — a keep,
  a capture objective's garrison — is born manned as today. Reactivity is for
  everything else.
- **Pickets before massing.** Across garrisonables as across apertures: every
  threatened place gets a squad before any gets a second, and the reserve
  yields to that rule rather than outranking it.
- **Fall back through layers.** A squad whose place is breached (enemy inside
  it), destroyed below its useful size, or untenable falls back to the next
  layer in if one is linked; with none, it releases to the commander. A fall
  back is squad-tier movement under an existing assignment, not a new order.
- **Release when the threat passes.** A commander-assigned garrison returns to
  the pool when its pressure has decayed past the release level, so the pool is
  available for the next threatened band.
- **Scale by locality.** With many garrisonables, the commander considers only
  those inside or adjacent to threatened bands, on its own command cadence.
  Nothing scans every garrisonable every tick; candidate lookup is spatial.
- **The player's version is manual.** The occupy order is the player assigning
  a garrisonable by hand. Whether a player's idle squads may opt into reactive
  manning is a later question, not a default.

## Measuring cheaply

Pressure is the side's own hostile influence, already maintained in 8-cell
blocks and published as a snapshot; nothing new is computed per cell. Each
garrisonable records the few blocks it covers, and a block-to-garrisonables
lookup is derived beside it, rebuilt only when the prepared ground changes and
never per tick. On each command pulse the commander walks only the blocks its
believed contacts stand in, prices the garrisonables those blocks can press,
and re-prices only the ones already manned. The work grows with the size of the
fight, not with the number of garrisonables, and hysteresis state exists only
for manned places. Measured on the fort control it is a few hundredths of a
millisecond per pulse once warm.

## Flow, then local assignment

Manning answers pressure only if a squad can reach the place while the pressure
lasts, and a patrol sent from across the map usually cannot. So the commander
works in two tiers.

- **Flow** moves forces toward pressure before they are needed. Believed
  pressure and fighting in an area pull free squads from the mobile pool to a
  staging point on their own side of it, still in the pool and still free to
  be sent elsewhere; a quiet area gives them back after a long dwell. Flow is
  the anticipation tier and the only one that crosses the map.
- **Local assignment** mans garrisonables and does other local work only from
  squads that can arrive in time, estimated cheaply from distance and speed.
  With nobody local it sends nobody rather than a doomed march, and a squad
  already closing is not recalled by a lull.

Flow is what makes local assignment work: it changes who is local. Both run on
the command cadence from the same pressured-block walk.

Pressured blocks cluster into **areas**, each with a believed hostile weight.
An area asks for squads in proportion to that weight and to its pressed
garrisonables nobody holds, less the squads already local to it. Its staging
cell sits a standoff behind the area along the side's own rear, or at a held
fort's works when the area presses that fort's belt. Every area with nobody
local gets one squad before any gets a second, starting and keeping a post have
different thresholds, and an area that stays quiet returns its squads.

The track rally and flow are one **per-area reservation**. A threatened track
maps onto its heaviest area; when that area has something to man, the rally
goes to its staging cell (a fort's works for a fort's belt), and otherwise to
its old band point, so a map with nothing to man keeps the plain rally. Each
squad is reserved to one area at a time — rallied, staged or manning — and
counted once against its demand. Local assignment draws the area's own
reserved squads first, then the roving pool above its reserve floor. Areas with
nothing to man are not staged for.

A squad below useful strength (half its starting strength, and never fewer
than two) is never sent to man a place, and one that falls below it falls back: sending one only
for it to be released the next pulse was the main source of churn. A breach
that makes a garrison fall back is a believed enemy inside the place near the
squad, so one at the far end of a long run, or merely beside it, is not.

## How it fits what exists

The defender commander already raises a threatened track and forward band from
contact and rallies a bounded number of patrols into it (`conquest-nouns.md`).
A garrisonable inside that band is a better destination for a rally than a
point on the track: the rallied squad mans it and digs in instead of meeting the
attack in the open. That is the natural first consumer.

Standing to is already reactive manning of a building envelope by its own
garrison. Unifying it under garrisonable posts is a later step, taken only once
fighting-position runs prove the commander-tier half, because frontage carries
its own laws — what an aperture may open onto, held layers, the breach that
dissolves an envelope — that must survive the move intact.

## Beyond Conquest

The tech is meant for story missions as much as for Conquest, and
`gradlew.bat garrisonEvidence` inventories what each story map offers at tick
zero: per held place, its windows, entrances and stand cells at building and
compound scope, and its fighting-position runs, plus the windows and runs no
held place covers. Three things it shows shape the work ahead:

- Buildings already carry windows in useful numbers; a large barracks or armory
  offers ten to twenty-five stand cells, a small post two to four, and the
  smallest guard posts and bunkers none.
- About half the story maps publish **no held places at all**. Their buildings
  have windows, but nothing names a place a garrison could hold, so neither the
  defender commander nor a player's occupy order can use them. Held places on
  those maps are the precondition for everything in this doc.
- Fighting-position runs are almost absent outside forts.

## Open questions

- How a garrisonable is valued against an objective: a trench in front of the
  keep is worth more than one at the map edge under the same pressure.
- Whether attackers use the same assignment for captured ground (holding a taken
  belt against the counter-attack), or only manual orders.
- Mechs and vehicles: excluded from posts; whether they screen a manned place.

## Stories

- `reactive-garrisons.md` — fort belts become reactively manned from the
  defender pool, with release and fall back.
- `compound-prepared-ground.md` — compounds and facilities offer prepared ground
  and building envelopes as garrisonables.
- `fort-sandbag-damage-art.md` — real art for the placeholder fort sandbag damage
  states.
