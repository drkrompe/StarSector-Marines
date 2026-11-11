# The Long Survey: chapter maps

Status: ACTIVE. Each chapter's map is designed around its objective first and then
built as one owned seed whose patch lays that design (`long-survey-arc.md`, Seeds;
`story-map-pools.md`). This doc holds each chapter's map plan: where the company
enters, where the objective sits, the routes between and their character, where
the hull, trench and claim press in, the set-piece fights, and where allies stand.
Coordinates are map cells, `x` east, `y` north.

## Chapter 1: The Fall, the colony

**Objective.** The hull has come down beside a colony. The company lands at the
lift and brings the civilians of three holdouts out to it while the watch spreads
west from the hull through the town (the Colony Breach's rescue, `long-survey-arc.md`).

**Ground.** The Colony Breach's colony on its own seed, `long-survey-fall` seed 38,
400 by 240: the lift, bores and holdouts as the owner placed them, the town north
of the rail line, fields and desert south of it.

- **Entry.** The lift at 164,18 on the south edge, its landing field of fire kept
  clear of the hull, the trench and every strike.
- **The hull.** Down in the desert south-east of the town, broken in three, bow west
  (sections x 255 to 382, y 36 to 72), its trench run west toward the lift's field
  and stopping short of it. The impact band flattens the blocks along the town's
  south-east edge; the rest of the town stands.
- **The holdouts.** 79,86 at the town's south-west edge, 18,200 in the far
  north-west and 145,199 in the north wards.

**Routes**, from the lift:

- **West, the fields and wards**: across the fields to the first holdout, then north
  through the west wards to the second; the longest walk, furthest from the hull.
- **Centre, the avenue**: north through the town's middle to the third holdout,
  crossing the town square.
- **The watch's road**: from the hull west along the impact band's edge into the
  town's south-east, the side the company must hold while the civilians pass.

**Set pieces.** 1, holding the lift as the civilians come in; 2, the town square on
the avenue; 3, the band's edge, where the watch breaks into the town.

**Allies.** The colony's militia in the holdouts; the company is the rescue.

## Chapter 2: Stakes, the mine

**Objective.** The forge has printed its first stakes and walked them out into the
mine beside it. Three are planted and counting down, each in a different kind of
ground; kill them before they rise. A raised stake is the junction of their chain,
a zone to fight through (Sabotage, exactly three named sites).

**Ground.** Garnir's mine: the owned Raid mine's works (`RaidOwnedMap`, the
mining site against the same reference market), `long-survey-stakes` seed 2, 400
by 240, laid out frozen and airless. The pirates' walled works fill the
north-west (x 86 to 229, y 92 to 235), the port's mining town the west and south,
the head works where the ore line lands (x 237 to 292, y 47 to 97) and the open
pit in the south-east (x 332 to 392, y 45 to 105), the raised ore line between
them along y 69.

- **Entry.** The company comes in from the south edge.
- **The hull.** Down in the open ground east of the works, broken in three, bow
  west (centre 314,185; x 251 to 378, y 167 to 203), its trench stopping short of
  the works' east wall; the impact band wrecks the works' east yards.
- **The claim.** A trunk south from the hull's middle: established at 314,145,
  raised at 314,105 (the junction, in the snow between the iced pools); planted
  at 274,105 by the head works and 314,65 on the ore line. A second chain down the
  east side: established at 394,145, planted at 394,105 on the pit's north-east
  rim. Every stake seats on every world's skin.

**Routes**, from the south edge:

- **West, the town and the head works**: through the mining town's streets and the
  head works' yards to the planted stake at its north gate.
- **Centre, the ore line**: up the open snow along the ore line to the planted
  stake on it, then north to the junction.
- **East, round the pit**: along the pit's east rim to its planted stake.

**Set pieces.** 1, the ore line, open ground under the line's trestles; 2, the
junction: cutting the raised stake at 314,105 strands the head works' and the ore
line's stakes; 3, the pit's rim, the east stake on its lip.

**Allies.** The pirates hold their works behind its walls and cover the west.

## Chapter 3: The Line, the district capital

**Objective.** The claim has reached the district capital. The garrison's columns
and the company push the watch out of the city street by street, north toward
the hull, each column's lane taking its next stake, and the company cuts the
chains and the haul roads that feed them (`long-survey-arc.md`, The Line as a city
push). A ground war through a populated city, built to show the destruction
mechanics: walls and roofs break, blocks collapse and burn under the fight.

**Ground.** Broad Front's city (`HeartCountry.planFor(BROAD_FRONT, CITY)`),
`long-survey-line` seed 2, 560 by 336. Fields along the south edge, then dense
blocks to the north edge. The city is intact at the start apart from the hull's
impact band (`fallen-hull-nouns.md`, The ground), so the battle's damage reads.

- **Entry.** The garrison's columns and the company step off from the fields
  along the south edge (y 0 to 70), the landing on the Avenue at 279,39.
- **The hull.** Down across the north edge in the heart's place, broken in three,
  bow west (centre 330,296; x 267 to 394, y 278 to 314), the forge in the bow, the
  bay's door facing the city. Its trench runs on west from the bow. The impact
  band flattens the blocks round it (to about y 250); south of that the city
  stands. The commons in front of the hull is ploughed ground: burnt earth,
  rubble and craters, its trees felled, the old workings' yard wrecked, the
  Avenue ending in rubble short of the hull.
- **The claim.** Two chains run south from the hull into the city and branch
  along the boulevard (y about 186): established stakes nearest the hull
  (330,256; 330,216; 330,176; 370,256; 370,216), raised ones at the lane heads
  (290,176; 330,136; 370,176) and planted ones beyond (250,176; 410,176). Every
  stake sits on a lattice point that seats on every world's skin, and every
  lattice point has a haul road home through the streets.

**Lanes**, south to north, one per column, three of different character:

- **West lane** (x about 150 to 250): the dense west wards, narrow streets and
  short sightlines, fought building to building to the planted stake at 250,176
  and the raised one behind it.
- **Centre lane, the Avenue** (x about 279): the main road from the landing north
  to the boulevard; the armour's axis, wide with long sightlines, to the raised
  stake at 330,136 and the chain's trunk above it.
- **East lane** (x about 410 to 470): the east wards to the planted stake at
  410,176 and up the second chain to the hull's stern.

**Set pieces.**

1. The Cross: the boulevard junction where the lanes meet the chains' branches;
   cutting the trunk at 330,176 strands both west stakes.
2. The commons under the hull: open ploughed ground in front of the hull, the
   established stakes standing in it under the watch's guns.
3. The rubble band: the flattened blocks round the hull, the last push to its
   mouth. The hull and its forge stay out of reach; that is chapter 4.

**Allies.** Each lane is a garrison column, infantry screening the streets and
armour on the open ground, guns and air behind; the company works between the
lanes, cutting haul roads and chains where the columns cannot reach.

## Chapter 4: The Surveyor, the hull itself

**Objective.** The garrison's columns go in on the trench. Cut the outer chain to
strand the claim, break into the hull and put the autoforge out in its forge hall,
guarded by the vault frame and a hall frame (Broad Front, the hull as its heart,
then the interior as a delve). With chapter 3 on Broad Front too, the outer push is
kept short and the chapter weighted on the delve (`long-survey-arc.md`, Open picks).

**Ground.** Broad Front's country on Garnir, `long-survey-surveyor` seed 5, 560 by
336, laid out frozen and airless: the south fields and two airbases, the cordon
line, then works country, two sealed works districts either side of the approach
(sheds, yards, a tank farm; `LongSurveyGround`, `WORKS_WEST` and `WORKS_EAST`), the
hull in the heart's place between them.

- **Entry.** The pirates' columns and the company from the south fields and the
  airbases (96,8 and 483,8).
- **The hull.** North of centre, broken in three, bow south (sections x 262 to 298,
  y 187 to 314), the forge behind the bow's torn end; its trench runs down the
  supply road the columns come up.
- **The claim.** Established stakes round the hull and a ring of them at y 210
  (200, 240, 320 and 360); the outer chain beyond: on the west raised at 200,170
  and planted at 200,130, on the east raised at 320,170 and 360,130 and planted
  at 400,170. Cutting the ring at 200,210 strands the west outer chain, at 360,210
  most of the east: the outer phase is won by those cuts. Every stake seats on
  every world's skin.

**Routes**, south to north:

- **Centre, the supply road**: straight up the open ground between the works to
  the bow's torn end and the forge behind it; the trench run.
- **West, through the west works**: sheds, yards and the tank farm, close cover,
  to the west outer chain and the ring's west cut.
- **East, through the east works**: the denser district, to the east outer chain.

**Set pieces.** 1, the ring cuts, where the outer chain strands; 2, the trench run
into the bow; 3, the delve: a breach, the spine, the forge hall.

**Allies.** The pirates' columns hold the trench mouth and the works' edges while
the company goes in.
