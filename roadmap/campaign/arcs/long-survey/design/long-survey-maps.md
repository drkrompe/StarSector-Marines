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

## Chapter 2: Stakes, the mine country

**Objective.** The forge has printed its first stakes. Three are planted in a line
across the hull's south flank, counting down; a fourth has risen beyond them among
the rocks. Kill the planted three before they rise; the raised one is a zone to
fight through (Sabotage, exactly three named sites).

**Ground.** Mine country of its own, a Sabotage plan in remote sprawl for a mining
district, `long-survey-stakes` seed 1, 400 by 240. The installation is the
garrison's cantonment in a square wall at the west (x 20 to 140, y 102 to 224);
outcrops of the mine country's rock cover the south (y 30 to 120).

- **Entry.** The company comes in from the south edge.
- **The hull.** In the middle of the map, broken in three, bow west (sections x 237
  to 364, y 142 to 178), its trench run west toward the cantonment.
- **The stakes.** Planted at 220,120, 300,120 and 340,120, each linked straight to
  the hull, so its chain holds until it is killed; raised at 300,80 among the
  outcrops, chained through the centre stake. Each seats on every world's skin.

**Routes**, from the south edge:

- **West, along the cantonment's wall** to the stake at the trench's mouth, under
  the garrison's guns.
- **Centre, through the outcrops**: rock cover, through the raised stake's zone to
  the centre stake.
- **East, the open flats**: long sightlines and the haul roads' trains, to the
  stake under the hull's midsection.

**Set pieces.** 1, the raised stake in the rocks; 2, the trench mouth, the west
stake beside the trench where trains come out; 3, trains across the flats.

**Allies.** The garrison holds its cantonment and covers the west stake.

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
  stands, and the heart's overgrown commons lie open in front of the hull.
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
2. The commons under the hull: open overgrown ground in front of the hull, the
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

**Ground.** Broad Front's country, `long-survey-surveyor` seed 5, 560 by 336: the
south fields and two airbases, rural country with ponds and ridges, the hull in the
heart's place.

- **Entry.** The garrison's columns and the company from the south fields and the
  airbases (96,8 and 483,8).
- **The hull.** North of centre, broken in three, bow south (sections x 262 to 298,
  y 187 to 314), the forge behind the bow's torn end; its trench runs down the
  supply road the columns come up.
- **The claim.** The full claim stands round the hull: established stakes nearest
  it, raised beyond, planted at its edge (the outer chain, y about 150 to 200).

**Routes**, south to north:

- **Centre, the supply road**: straight up the trench to the bow's torn end and the
  forge behind it.
- **West, the ponds and hollows**: broken ground to the outer chain's west stakes
  and the bow's port flank.
- **East, the ridges and the works**: the berms and the derelict works north-east,
  to the outer chain's east stakes and the midsection's rents.

**Set pieces.** 1, the outer ring, where cutting the chain strands the claim; 2, the
trench run into the bow; 3, the delve: a breach, the spine, the forge hall.

**Allies.** The columns hold the trench mouth and the outer ring while the company
goes in.
