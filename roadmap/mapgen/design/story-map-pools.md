# Story map pools

Status: ACTIVE — the four Rav Bloom story maps (Cordon Line, Cordon Road, the
Heart's The Burn and Broad Front) play from pools, three maps for each world
biome; Colony Breach plays from one map per biome while it is tuned; Conquest
and skirmish stay fully random.

Written: 2026-10-23

Updated: 2026-10-28 — a Colony Breach patch places the lift, bores and holdouts. Earlier: Colony Breach plays from a pool of one map per biome.

A story mission's map is the generator's, but the mission does not take any
seed the dice offer. It plays from a **pool**: for each world biome, a short,
vetted list of seeds the team has looked at and owns, each optionally carrying
a small authored **patch**. Generation still makes the map every time, so the pool rides
generator improvements; the pool decides only *which* maps a player can get.

## Why

A story map's outcome swings on small behaviour changes, and evidence over a
sample of random seeds is a sample that keeps moving: two balance readings on
"seeds 3, 11, 42" and "seeds 7, 19, 64" are not comparable, and neither covers
the map the player will actually get. A fixed pool makes evidence **complete**
(every map the player can get is played) and **comparable** (the same maps,
run to run). It also lets a person own a map: look at it, name what is good
about it, and fix the one thing wrong with it.

The setups already vetted informally: Cordon re-rolls a map whose base packed
no shed or whose redoubt has no wall, and the Heart re-rolls one whose bores
would not seat. A pool is that vetting done once, by a person, with a record.

## The pool

One data file per story map, `mod/data/world/pools/<map>.pool.json`
(`StoryMapPools`, installed with the other world catalogs at application
load):

```json
{ "version": 2, "map": "cordon-line",
  "biomes": {
    "VERDANT": [
      { "seed": 3, "note": "rear south; redoubt guns 1-2-2-2" },
      { "seed": 9, "note": "...", "patch": { "pin": [...], "ruin": [...] } } ],
    "ARID": [ ... ], "ROCK": [ ... ], "FROZEN": [ ... ] } }
```

**Biomes.** A bloom surfaces on any world, so its story is fought on that
world's own ground: the event world's `SurfacePalette` (verdant, arid, rock,
frozen) picks the pool, and every biome must have maps. A seed names a
different map on each biome (the palette feeds generation), so a seed is
vetted per biome; the same seed pooled on several biomes keeps one layout
under different ground, which makes the biome the only difference between
those readings.

**Density.** The Cordon is also laid out at its world's density (rural, town
or urban, from the market size; `cordon-nouns.md`, Density), and each density
is a story map of its own with its own pool: `cordon-line-town`,
`cordon-road-urban` and so on. The heart is the same (`heart-map-intent.md`,
Density): `heart-front-town`, `heart-burn-city` and so on, rural, town and city
each from the world's market size. A density changes the whole map a seed names,
so it is vetted as a biome is.

A pool **seed** is the battle seed itself: the map it names on its biome
passes the mission's acceptance on the first generation, with no re-roll. The
note is for people: what the map is and why it is in.

**Choosing.** A campaign battle picks `entries[floorMod(roll, size)]` of its
world's biome from a roll its event already carries (`StoryMapPool.pick`), so
the choice is deterministic per event and saved with it. A fixture names a
seed and a biome (absent means verdant): a pooled seed plays that entry with
its patch; a seed outside the pool plays the old unvetted path, re-rolls and
all, which is how a candidate is tried before it is added. Nothing on the
campaign path reaches the unvetted path.

**Size.** Three per biome, twelve per story map (owner, 2026-10-23): few
enough that each is owned and tuned, and the whole pool plays in one evidence
run. The pool is a dial, not a constant: add a map when one earns it.

**Colony Breach.** The colony (`colony-breach`) is pooled too, one map per
biome while the mission is tuned (owner, 2026-10-26), so every tuning reading
plays the four maps a player can get. A pooled colony is generated on its
world's biome alone, never on the market behind it, with no re-roll; a pooled
seed that no longer seats its shelter, lift, bores, holdouts and opening swarm
fails, in the campaign and in the regeneration check alike. Its entries take
no pin or ruin yet, only the colony's own placements (The patch, below). The
debug stress test and the lower-risk rescues stay random.
The first four were screened from seeds 1 to 40 (18 pass, on any biome alike:
the palette does not move the colony's layout): a different seed per biome,
each with at least two holdouts and the widest room between holdouts and bores.

**How the first pools were chosen.** Seeds 2 to 25 screened on every biome
(the Cordon passes on 60 to 72% of them, the Heart on all); for the Cordon the
three with the fewest gunless redoubts, both rear edges represented, and on
the Road a corridor at least 80% on road; for the Heart the first three seeds
that grow two mothers (the seed alone decides two or three), the same three on
every biome.

## Seeds age; the regeneration check

A seed names a map only for one generator version, and mapgen changes
constantly. Two ways to cope:

| | Seed + patch (chosen) | Baked maps |
|---|---|---|
| Generator improvements | arrive on every pooled map | never arrive; each map is a frozen copy |
| Storage | a few numbers | a whole map result per entry, with a format to version |
| A mapgen change that breaks a map | caught by the check below | cannot happen, and neither can a fix |
| Hand-tweaks | small ops that ride the regeneration | edit anything |

The pool keeps seed and patch, and **`gradlew storyMapPools`** regenerates
every pooled seed and fails when one no longer meets its mission's
acceptance. Opt-in and excluded from `test` (it generates whole maps), and it
is what a session runs after a mapgen change that may move a story map. A
failure names the map and the seed and what it now lacks; the fix is a patch,
a swap for another seed, or the mapgen change itself. Writes
`build/reports/story-map-pools/summary.md`. A Cordon row's detail carries
the line's gap readings (`CordonGapLaw`: each neighbouring pair's widest
gun-to-gun step, `!` where it breaks the law, and the towers' own distance)
and its picket nests per course, with how many were added to close the gaps
and how many of those stand at street mouths. When the redoubts went up onto the
ridge's crest, a Cordon map whose redoubt could not stand there stopped being
a Cordon map, and the rural and town pools were re-screened over seeds 1 to
40: a swapped-in seed's note says every redoubt is on the crest.

**Acceptance** is the mission's own re-roll test, so the check and the setup
cannot disagree:

- **Cordon**: a clear landing field; every redoubt walled; the base packed a
  shed and a carrier can drive from its apron to every redoubt; four redoubts
  west to east spanning the map; on the Road, the compound walled and a
  drivable corridor from the rear edge to it; and the gap law holds on every
  neighbouring pair, its picket nests included (`cordon-nouns.md`, Picket
  line). Both vehicle proofs run on the
  edge-aware clearance mask the convoys lay their routes on, so a fence or low
  wall along a cell edge that would stop a hull fails the map rather than
  jamming a carrier or a truck in play.
- **Heart**: the bores seat (`HeartBores.plan`).

and the battle then stands up on it (the patch applied).

## The patch

The smallest vocabulary that covers what a person needs to fix on a map they
otherwise like. Two ops, each riding an existing seam rather than a new
map-editing path:

- **`pin`** `{place, x, y}` moves a plan precinct's seed (a redoubt, the
  logistics base, the compound) **before generation**, so the map grows round
  the pinned place and every acceptance test sees the result. This is the
  op for "the base sits in the wrong spot" or "redoubt 3 is in the trees".
- **`ruin`** `{radius, points, mode?}` is a stroke of the authored damage
  layer (`mapgen-nouns.md`, Authored damage layers), applied **at battle
  assembly** through the ordinary structural destruction boundary: walls
  breach to rubble, roofs open, edge barriers and props are wrecked. It is the
  op for "clear this cell or edge", "drop this doodad": it clears by
  ruining, which is honest on a bloomed front and leaves no impossible gap. Mode
  is `RUIN` unless it says `BATTERED` or `SCAR`.

A ruin is applied after the setup's map tests run, so it **cannot make a
failing seed pass**: a seed must pass acceptance on its own (with its pins).
Ruin is polish on a good map, not rescue for a bad one. Moving a place is a
pin; there is no op to add a building or road, because a map that needs one is
a map to swap out.

A Colony Breach entry places the mission's own pieces on the generated map
instead (`colony-breach-nouns.md`, The colony), each in cells read off the map:

- **`lift`** `[x, y]`: the evac lift and its cordon, on the clear pickup site
  nearest the cell (within 12 cells).
- **`bores`** `[{x, y, mother?}]`: the first wave in eruption order and exactly
  one mother, each on the doorstep of the place nearest it (within 10 cells) or
  else on the open ground nearest it (within 16).
- **`holdouts`** `[[x, y]]`: in redoubt order, the held building each cell falls
  inside.

They replace the setup's choices and its clearances; a placement that cannot
seat fails the map like any other acceptance. Asharu's ARID entry is placed
this way from the owner's Asharu Placement Board (`tools/picks/pages.md`).

Patches are authored in the pool file, in cell coordinates read off the
pool's whole-map review frames (`createSnapshots -Psnapshot=story-map-pools`).
The Map damage workbench page hosts only Relief so far; giving it the story
maps is the natural next step once patches are common.

## Evidence plays the pool

`commanderEvidence -Pmission=cordon-line`, `cordon-road` and `heart` play
**every pooled seed** by default, side by side, with the mission's checked-in
fixture as the template for everything but the seed. The report gains a
matrix, one row per seed, and each seed keeps its own folder with its trace,
summary and timeline. A balance reading therefore covers every map the player
can get. `-Pfixture=` still plays exactly one fixture, for a candidate or a
question about one map.

## Boundaries

- Conquest, skirmish and every generated contract stay fully random: their
  value is variety, and their evidence is a matrix over constructed fixtures.
- A pool is per story map, not per mission: The Burn and Broad Front are
  different forms at different sizes, so each has its own.
- The pool does not hold the setup's tuning, timetables or rosters; those
  stay in the mission's code and story docs.
- The check generates each map on its biome with no market behind it. The
  story campaign passes only the world's biome, never the market's profile,
  so the map it plays is the map the check saw.
