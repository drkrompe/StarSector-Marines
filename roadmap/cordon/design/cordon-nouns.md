# The Cordon — Nouns

Status: DRAFT

Written: 2026-10-22

The Cordon is the second movement of the Rav Bloom arc (`bloom-mission-arc.md`,
Mission 2): a world's military has thrown a line of redoubts across the country
in front of a bloom, and the company is hired to help it hold. One mission, two
variants, each a single battle: **the Line** (the company is the mobile reserve
behind the redoubts while the region behind them is evacuated) and **the Road**
(the company keeps a relief road open through a gap in the line to a town cut
off in the bloomed ground). The campaign picks the variant from how Colony
Breach went; the player picks only whether to take the beat.

This doc owns the Cordon's map, its allies, its people on the move, its beats
and its end conditions. The swarm is Colony Breach's swarm: bores, tiers,
surges, packs, harvest, creep, spore and ground colonies, strains and growth
stages are `colony-breach-nouns.md`'s, reused and never restated here. Read that
doc first; every one of its laws holds here unless this doc names an exception.

## The fantasy

The colony was not saved, or not all of it. Weeks on, the bloom has grown out
over the farmland round it and the world's own garrison has dug in across the
country: a row of redoubts on the roads the swarm would use, picket works
between them, guns in the redoubts. They will hold where they stand; they will
not move. The company is the hand that moves. In the Line it runs from one
redoubt to the next as each one starts to go, plugs the gap, and when the line
can spare it goes out between the lines to put a bore down. In the Road it
walks ahead of the relief convoys through the gap, clears the road, pickets
the bores that bear on it, and keeps the gap open until the last truck is back.
Either way the allies are the line and the company is what keeps it a line.

## Vocabulary

**Front.** The bloomed side of the map: the country in front of the line and
the colony the bloom came out of, overgrown with creep, its bores already grown
(the bloom has had weeks). The swarm owns it; nobody lives there.

**Rear.** The near side: farmland and roads behind the line, the landing field
the company comes down on (an `OPEN_FIELD` landing, `precincts.md`, never in
bloomed streets), and the roads allied columns come in on and people go out on.

**The cordon (the line).** The row of redoubts across the map's long axis
between the front and the rear, with picket works between them. The line is
what the allies hold. It runs across the whole map, edge to edge, so there is
no way round it, only through it.

**Field redoubt.** One strongpoint of the line: a small walled work laid out
square on its site (the planned redoubt kind of `forts.md`, a nest cluster with
a small command post and no ship-grade guns), sized for an allied garrison.
It is a place in the map's plan, as the civil-defence compound is in the
colony's (`colony-breach-nouns.md`, Redoubt), so roads run up to it and its
gates are where they cross its wall; the garrison makes the rest of it at
setup: a gun tower beside each gate, firing slits and wall posts along its
runs, floodlights at its corners. Its wall is field grade: a Brute, a charge or
a ground colony's spines open it. Each redoubt is numbered across the line from
west to east, from 1.

**Picket line.** The ground between two neighbouring redoubts, held by works
rather than walls: a sandbag course with a nest at each end, the outer belt of
`forts.md` run out from one redoubt to the next (`fortified-lines.md`). A
picket line is broken by gaps the roads pass through, so the line is a set of
prepared positions, never a wall: routes through always exist. A picket team
mans its course under the same order a holdout's line team has, and falls back
into the nearer redoubt when it breaks.

**Gap.** Where a road crosses the line: between two redoubts, where it is left
open in the picket works and the redoubts either side cover it, or through a
redoubt's own gates, where the redoubt holds it. The Road's corridor runs through one gap; in the Line the gaps are
where the swarm is funnelled and where columns come up.

**Garrison.** The allied troops in a redoubt (`ALLY`, under garrison
authority, never in the player's command pool, as the colony militia are). A
redoubt's garrison is three **wall teams** at the posts behind its slits, the
wall facing the front first, and the guns in its gate towers. The wall is
dressed exactly as the colony's civil-defence compound is
(`CivilDefenceCompound.dress`); a wall the dressing cannot read as closed is
still manned, its teams inside it with no towers. The teams hold the redoubt's
ground and fight from the slits: an order that draws them to the middle of the
yard leaves the wall empty and the gates open. The garrison is kitted as the
colony's best (troops dug in for weeks). A garrison is what makes a redoubt
stand; the company never commands it.

**Redoubt states.** A redoubt is **holding** while its garrison stands and no
swarm body is inside its wall. It is **calling** (for relief) once a swarm
body is inside the wall or half its garrison is down: the company hears the
call. It is **relieved** when a Marine reaches its gate while it is calling:
the call is answered, and it calls again only when a swarm body is inside its
wall once more. A relieved redoubt whose garrison is gone is held by the
company while Marines stand in it. It has **fallen** when its garrison is dead
and a swarm body is inside the wall. A
fallen redoubt is not re-raised in the battle (see *Open choices*). The line
**breaks** when more redoubts have fallen than the briefing allows.

**Tide.** The crisis the battle is timed round: every living bore's next
surge brought forward to the same tick, and the swarm let out at the tide's
strength for the tide's length. Between tides the swarm presses at the
**ordinary pressure**: the director holds the field at a share of the living
bores' capacities, so the line is worn rather than overrun, and the tide is
what breaks redoubts. A tide is heard, not seen coming.

**Timetable.** What the briefing holds the battle to: when the tides come and
how long each runs, the ordinary and tide pressures, the window, and how many
redoubts may fall. The standard timetable is three one-minute tides at minutes
four, eight and twelve of a fifteen-minute window, the swarm at a third of its
strength between tides and two thirds in one, one redoubt allowed to fall.

**Allied column.** Allied troops that arrive during the battle along a rear
road, on a timetable set at the briefing: a few trucks of riflemen. A column
is the allies' answer to a redoubt calling: it goes to the redoubt holding
with the least garrison (the Line) or is the convoy itself (the Road). The
column is scripted, not commanded by an allied commander; the full allied
column system (`marine-side-reinforcement.md` applied to `ALLY`) is a later
story.

**Refugees (the Line).** People walking out of the region behind the line to
the rear edge: groups of civilians leaving the rear villages on the
briefing's timetable and walking the country road off the map. They are the
Line's score, as evacuees are Colony Breach's, and they are prey: the swarm
harvests a refugee it reaches as it harvests a colonist. A group is **out**
when its last member leaves the map, **caught** when the swarm reaches it.

**Cut-off town (the Road).** The colony's own town in the front, beyond the
line, where the people Colony Breach left behind are still holding out in its
civil-defence compound with what is left of the militia. Its people are the
Road's score.

**Corridor.** The relief road from the rear edge through the line to the
cut-off town's compound: the way the convoys drive (*The map*). Nothing is
parked or built on it, so a convoy can always drive it if nothing is standing
in the way.

**Convoy (the Road).** An allied column of trucks that drives the corridor to
the town, waits at the compound's gate while the townspeople board, and drives
back out. It does not fight its way through: it stops when the road ahead is
blocked by the swarm, and goes on when it is clear. A convoy is **lost** when
all its trucks are destroyed; its passengers die with their truck.

**Line posture.** The briefing's choice of how far the company goes out, the
Cordon's version of the hive posture (`colony-breach-nouns.md`, Hive posture),
frozen at launch: **Hold** (the company only relieves and escorts),
**Opportunistic** (the commander may send a strike detachment at the bore
pressing hardest when the readiness gate is open), **Push** (bores in front of
the line are objectives). The readiness gate keeps its three conditions with
the Cordon's inputs: (a) the line or the road has held for a sustained window,
(b) the strength left behind covers every calling redoubt and every group of
people on the move, (c) a capable detachment exists.

## The map

One plan for both variants (CordonPlan), 560x336, mission-owned, the long
axis across the front. It is green colony country whatever world the event
names (the VERDANT palette, as the colony is laid out the same whatever
market raised the call). Every place is stated by its depth from the rear
edge, which is the north or the south (the seed's choice), as a fraction of
the ground the landing's country leaves:

| Band, from the rear edge | Depth | What stands there |
| --- | --- | --- |
| Country | the open-field landing's country (about 80 cells) | the landing field, farmland as RuralLayout surveys it, the country road to the rear edge |
| Rear hamlets | 0.04 to 0.2 | two hamlets, west and east, the refugees' homes |
| The cordon | 0.36 to 0.42 | four field redoubts in a row, one in each quarter of the width, numbered west to east |
| The front | 0.55 to 0.92 | the colony (a frontier town) in the middle, its installation and suburb either side |

The places are grown small on purpose: a town's or a hamlet's stock arms run
a third of the map's short side, and at 560x336 that grew one town across the
whole map with the redoubts buried in its streets. The colony's streets reach
a twentieth to a ninth of the short side and a hamlet's less, so the map
reads as country with places in it and the line stands in open ground.

The redoubts are programmed places (FortressProgram.fieldRedoubt, laid
square), so they claim first and the rest grows round them. Their roads are
short arms, and their gates are where roads cross their walls, so a road may
run through a redoubt rather than beside it: the redoubt then guards that
road's way through the line. A map on which a redoubt has no wall, or the
landing's field of fire is not clear, re-rolls.

The Road keeps the colony's civil-defence compound on the town's near side
(depth 0.6 to 0.7). Its **corridor** (CordonCorridor) is found on the
finished map, not carved: the cheapest way a truck can drive from where the
country road leaves the rear edge to a street at the compound, every cell
open with room either side and road five times cheaper than open ground. It
keeps to the country road and the streets where they go its way and crosses
country where the generator left no road (it prunes links between places
that carry nobody to a map edge). On the first seeds it runs 76 to 91% on
road and wanders through a hamlet, a redoubt and the bloomed suburb on its
way; a single paved relief road is open work (cordon-battle.md). A map
with no drivable way re-rolls.

**The front is grown at setup** (CordonFront). The front is cut across the
width into one slot per bore, west to east (six by default: bore, maw, bore,
bore, maw, bore). Even slots stand 60 to 100 cells in front of the line, odd
ones 100 to 165; a bore-tier creep reaches fifty cells and a maw's eighty, so
the creep's edge lies in front of the line and only now and then against a
redoubt's wall, never behind it. A bore keeps fourteen cells off the Road's
corridor and seventy off the town's compound (the town is besieged, not yet
eaten). Each bore opens through the ordinary seam and is fed to its tier one
body at a time, so its age mix is a fed bore's. Then the creep is **matured**:
every source grown to its full reach at once and filled, walls crusted, as
creep that has stood for weeks (CreepField.mature), and the trees under it
die. No mother bore stands on this map: she is the heart's (Mission 3). How
many bores and at what tiers will follow what Colony Breach left
(cordon-campaign.md): bores it collapsed stay collapsed, and biomass it fed
shows as higher tiers.

cordon-mapgen (createSnapshots) renders both variants on three seeds:
the whole map with the redoubts, places, bores and corridor named, the line,
a redoubt and the colony at closer zooms. CordonMapEvidenceTest
(generatedSpaceEvidence) checks four seeds of each variant: the map within
its re-rolls, four redoubts west to east spanning the map, and the Road's
corridor.
## Flow

### The Line

1. **Landfall.** The company comes down in the rear fields. The line is
   holding; bores in front are quiet between surges. Allied columns are on
   the timetable.
2. **The first surges.** Each bore surges on its own clock at the nearest
   prey, which is the redoubt in front of it, so pressure lands along the line
   where the bores are, not down one corridor. Refugees start leaving the rear
   villages.
3. **The tide.** At set minutes every living bore surges at once, heard as a
   tremor along the front: the crisis the battle is built round. A redoubt
   under a tide calls for relief; the company has to choose which call to
   answer.
4. **The allies answer.** A column comes up the rear road and reinforces the
   redoubt holding with the least garrison. A redoubt the company relieved and
   a column reinforced stands; one nobody reached falls, and the swarm comes
   through its gap at the refugees' road.
5. **Raids.** Between tides, posture allowing and the gate open, a strike
   detachment goes out between the lines at the bore pressing hardest.
6. **The end.** The last refugee group is off the map (or caught) and the
   evacuation window has closed: the Line is won if no more redoubts fell than
   the briefing allows. It is lost the moment the line breaks, or when the
   swarm holds the refugees' road in force for a sustained window, or when the
   company is wiped out.

### The Road

1. **Landfall.** The company comes down in the rear fields, near the
   corridor's entry. The gap is open; the town is holding.
2. **Clear ahead.** The first convoy enters on the timetable and drives the
   corridor. The company walks ahead of it through the gap and pickets the
   bores that bear on the road.
3. **Ambush.** A surge reaches the road while a convoy is on it: the convoy
   halts and the company has to clear it on. A convoy whose road is not cleared
   is eaten where it stands.
4. **The town.** A convoy at the compound's gate waits while its share of the
   townspeople board, then turns for home. The town's own militia hold the
   compound while it waits.
5. **The tide.** As in the Line, at set minutes every bore surges at once; on
   the Road the tide falls on the gap and the town. The two redoubts either
   side of the gap call; if both fall the gap is closed.
6. **The end.** Won when enough convoys are back out with their people (the
   briefing's number) and the gap is still held. Lost when too many convoys
   are lost to reach that number, when the gap closes, or when the company is
   wiped out.

### The swarm without a rescue

The Cordon has no cohort and no shelter, so the swarm director runs in its
bore-only form (`colony-breach-nouns.md`, Swarm director): bore-sourced from
the first tick over the bores the front was grown with, no perimeter waves, and
no rescue to end it, so it spawns while a bore lives. Two rules keep the
pressure along the whole line rather than at one end: due bores are served in
the order of fewest surges so far, and a bore's pulse is sized to the share the
director is held at, so a big bore held back to a third of its strength still
surges instead of waiting for headroom it never gets.

### The company

Until `cordon-company-command.md`, the company's autonomous commander is the
minimum the evidence needs: the company stands as the reserve behind the middle
of the line, each squad goes to the calling redoubt nearest it, and returns to
the reserve when no redoubt calls. It hears every call and knows where every
redoubt stands; it sees the swarm only through its own squads.

### Evidence

`commanderEvidence -Pmission=cordon-line` and `-Pmission=cordon-road` play a
fixture per variant through production construction. Asserted mechanics: the
company lands, every tide the timetable reached came on its tick, a held line
lost no more redoubts than allowed, a broken one says how. Read, not asserted:
the beats in order, each redoubt's state and garrison minute by minute, the
swarm's size, and how it ended.

## Beats

All beats are prefixed `cordon.`. Numbered beats index redoubts west to east
from 1, refugee groups and convoys in departure order from 1, and bores in
`SwarmBores` order. These ids are the contract between the sequence, the audio
and the evidence; change them here first. Bores' own beats (grew, collapsed,
surge, fed, harvest, impale) are Colony Breach's, published under this prefix
with the same keys.

| Beat | Variant | Audience | When |
| --- | --- | --- | --- |
| `landfall` | both | PLAYER | First Marine on the ground |
| `line_contact` | both | PLAYER | First swarm body inside any redoubt's reach |
| `tide_N` | both | PLAYER | The Nth tide: every living bore surges at once |
| `redoubt_calling_N` | both | PLAYER | Redoubt N calls for relief |
| `redoubt_relieved_N` | both | PLAYER | A Marine reaches redoubt N's gate while it calls |
| `redoubt_fell_N` | both | PLAYER | Redoubt N falls |
| `column_arrived_N` | the Line | PLAYER | The Nth allied column reaches its redoubt |
| `refugees_moving_N` | the Line | OBSERVER_ONLY | Refugee group N leaves its village |
| `refugees_out_N` | the Line | OBSERVER_ONLY | Refugee group N is off the map |
| `refugees_caught_N` | the Line | PLAYER | The swarm reaches refugee group N |
| `evacuation_complete` | the Line | PLAYER | The last group is out or caught and the window has closed |
| `convoy_entered_N` | the Road | PLAYER | Convoy N enters the map |
| `convoy_through_gap_N` | the Road | OBSERVER_ONLY | Convoy N passes the gap outbound |
| `convoy_ambushed_N` | the Road | PLAYER | A swarm body reaches convoy N |
| `convoy_at_town_N` | the Road | PLAYER | Convoy N is at the compound's gate |
| `convoy_loaded_N` | the Road | OBSERVER_ONLY | Convoy N's people are aboard; it turns for home |
| `convoy_out_N` | the Road | PLAYER | Convoy N leaves the map with its people |
| `convoy_lost_N` | the Road | PLAYER | Convoy N's last truck is destroyed |
| `gap_closed` | the Road | PLAYER | Both redoubts beside the gap have fallen |
| `line_broken` | both | PLAYER | More redoubts have fallen than the briefing allows |
| `cordon_held` | both | PLAYER | The battle is won |

## Laws

- **Colony Breach's laws hold.** Every body comes out of a living bore;
  collapse is permanent; bores are fought, not scripted; biomass drives growth;
  surges, not trickles; air cannot clear the map; the swarm senses, it does not
  know. A tide is the ordinary surge brought forward on every living bore, never
  a spawn from nowhere.
- **The allies are the line; the company commands none of them.** Garrisons
  hold their redoubts by garrison authority; columns and convoys follow their
  timetable. The player can order only the company.
- **The line is prepared positions, not a wall.** Every gap is a route; a
  picket line can be broken through by whatever a body already carries.
- **Fair knowledge.** A redoubt's call is radio, so the company knows which
  redoubt is calling without seeing it; it does not know what is coming until
  it sees it. Bores are known from the start: they stand in plain sight across
  open country and the garrison has been watching them for weeks.
- **People are the score.** The Line is won on refugees, the Road on
  townspeople; bores collapsed are a bonus recorded on the outcome and carried
  to the heart (fewer satellite bores).
- **Deterministic.** Same seed, same cordon, same timetable, same tides.
- **One story.** The cordon sequence is the battle's one story sequence.

## Open choices

Design choices sent to the owner as options cards; the doc records the
recommended answer until one is picked.

- **Where the Line's refugees walk** (recommended: out of the rear villages
  to the rear edge along the country road, behind the line).
- **Whether a fallen redoubt can be retaken** (recommended: no; a fallen
  redoubt is a hole the next column cannot fill).
- **How the swarm escalates** (recommended, and built: tides on a timetable
  over the bores' own surges; new eruptions in front as the alternative).
- **How the Road's convoys run** (recommended: three convoys on a timetable,
  each one out and back).

## Boundaries

- The swarm, bores, creep, harvest and strains: `colony-breach-nouns.md`.
- The arc, the campaign hooks and how one beat feeds the next:
  `bloom-mission-arc.md`.
- Precinct plans, the open-field landing and rural country: `precincts.md`,
  `mapgen-nouns.md`.
- Forts, the redoubt kind and fortified lines: `forts.md`,
  `fortified-lines.md`.
- Garrison manning: `garrisonables.md`.
- Convoys and corridors: `convoy-nouns.md`.
- The campaign event and its exactly-once lifecycle: `campaign-event-nouns.md`.
