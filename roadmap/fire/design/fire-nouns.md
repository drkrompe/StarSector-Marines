# Fire — Nouns

Status: ACTIVE
Written: 2026-10-22

How fire works in a battle: what burns, how it catches, spreads and burns
out, what it does to whatever stands in it, how it is started, and how it is
drawn. Fire is battle state any mission may have; The Burn
(`bloom-mission-arc.md`) is the mission that leans on it hardest, and the
creep (`colony-breach-nouns.md`, Creep) is the fuel it was first asked to
burn. Open work is on `stories.md`.

## The fantasy

A line of flame walking into a forest the swarm has overgrown, the creep
crisping and shrinking back in front of it, smoke rolling off the crowns,
and behind it black ground the creep cannot take back for a while. Fire is a
weapon nobody fully controls: it goes where the fuel goes, it stops where
the fuel stops, and a road is the difference between a burn line and a
burnt company. But green country does not carry it far (owner, 2026-10-23:
"we should have spread, but control the spread"): a flamer's burst in a
meadow burns a patch, not the meadow, and what runs is the swarm's creep,
dry country, and a building's rooms.

## Vocabulary

**Fire field.** Burning as simulation state the battle owns, beside the creep
field (`FireField`, `sim.fireField()`; created on first ignition, so a battle
that never burns pays nothing). Per cell it holds the cell's **fuel** (what
is left to burn), the **heat** it has soaked up, and its **state**:
*unburnt*, *burning* (with an intensity), or *burnt* (with how long ago).
It belongs to one simulation, steps on the simulation thread, and is
deterministic in the seed: every random choice in it is a hash of the seed,
the cell and the step, never a draw from a shared generator, so the same
battle burns the same way however it is scheduled.

**Fuel.** What a cell has to burn, read from the map when the field is made
and kept up to date as the battle changes it (creep spreads, a body falls, a
wreck stops). Each fuel kind has a **load** (how much heat it gives, which is
how hot and how long it burns) and an **ignition** (how much heat it must
soak up before it catches). A cell's fuel is the sum of what stands on it:
grass under a hedge under creep is three loads.

| Fuel | Where it comes from | Load | Burns for | Catches | Spread | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| Grass | `GRASS` ground, a grass overlay tuft | 30 | 6 s | easily | 2 | The fast carrier, but green: a patch, not a meadow. |
| Brush | a shrub or tuft on the nature overlay (a hedge) | 60 | 12 s | easily | 3 | Gone from the overlay once burnt. |
| Crown | a standing tree's crown: the cells of its footprint inside the round canopy the art draws, so a crown burns round; the square's corners burn as what lies under them | 200 | 40 s | hard | 4 | Slow to catch from a grass fire, long and hot once it does, and throws heat a ring further (a crown fire). Each crown cell that burns out spends part of its tree's structure through `MapEditor.damageWall`, so a tree falls once three have, leaving its ordinary destroyed art and passable ground. |
| Living creep | `CreepField` coverage, alive | 75 | 15 s | moderately | all | Creep over a crown makes it catch as creep does: an overgrown tree goes up readily. |
| Dead creep | creep dying back | 40 | 8 s | easily | all | Dry; the edge a source has abandoned goes up fast. |
| Debris | a standing wooden prop: a crate or box, a pallet stack, wooden furniture (the props `EnvironmentMaterial` calls wood) | 50 | 10 s | moderately | 2 | The prop is destroyed when its cell burns out and stays as its destroyed art. Wire fences, drums, sandbags, hedgehogs, machinery and stone do not burn. |
| Structure | a light wall or window: one of at most 100 hit points, in a structure that is not fortified | 120 | 24 s | hard | 4 | Catches only from fire held against it, so a passing grass front catches it some of the time; a cell that burns through falls to rubble (Buildings). |
| Furnishing | a plain indoor or panelled floor (`INDOOR`, `TILE`) of a building that is not fortified | 80 | 16 s | moderately | 3, then the room | A room's furniture and boards; a room half caught flashes over whole (Buildings), and a cell that catches loses its roof. |

Fuels stack: grass under a hedge under creep burns for all three loads,
at the hottest one's intensity, and catches as easily as the easiest.
Bodies and wrecks are not fuel: a burning cell consumes the bodies lying in
it, and a wreck is an igniter (Starting fires).

**Does not burn:** bare outdoor paving (`STREET`, `SIDEWALK`, `COURTYARD`,
`BRICK`, `STONE`), military floor (`STRIPED`), bare `DIRT`, `SAND`, `SNOW`,
`WATER`, masonry rubble, rock, any wall heavier than a light one, anything of
a fortified structure, and ground that has already burnt.

**Creep over cleared ground is a film.** Creep is fuel where it has ground to
feed it: over grass, brush or a crown it burns, living or dead. Over cleared
ground with nothing else on the cell (bare `DIRT`, any paving, `STRIPED`) it
is a thin film that does not burn, so a road, a ride or a bare yard keeps its
firebreak after a bloom has grown over it, and a burn in creep country stops
at each break until something relights it past (the owner's pick, "firebreaks
hold"). `SAND` and `SNOW` keep creep fuel: they are a biome's open ground, not
a cleared break. A break holds when it is wider than heat crosses: two cells
against a grass, brush or creep fire, three against a crown fire, four
against a crown fire in a strong wind (Wind).
`-Dbattle.fire.creepFilm=false` is the control: creep burns over any ground.

**Ignition.** A cell catches when its soaked-up heat passes its fuel's
ignition. Heat comes from burning neighbours and from an **igniter**: an
incendiary blast, a flamer's stream, a burning wreck, an ordinary shell's
blast on dry fuel. An igniter is not a new delivery system; it is a property
of the weapon or effect that already lands there (Starting fires).

**Burning.** A burning cell has an **intensity** that rises over six seconds
as it catches, peaks, and falls as its fuel runs out; it spends its fuel at a fixed rate, so
its load is its burn time. Each fire step it gives heat to the cells round
it by ring: a quarter of its intensity to the cells touching it, about a
tenth to the ring beyond, and, for a crown fire only, a sixteenth to the ring
past that, each weighted by wind and by a seeded jitter so the front runs
ragged rather than as a diamond. One burning cell cannot light grass two
rings away, but a front of them can, which is why a single furrow does not
stop a field fire and two bare cells do. A cell that has spent its fuel is
**burnt**.

**Pace.** The slow rise is what sets how fast a front runs: a newly caught
cell gives little heat until it is well alight, so a grass front walks at
about two thirds of a cell a second in calm air, a third of a man's walking
pace (owner, 2026-10-23: the front that kept pace with a walking man overran
everyone). It does not change how hot a fire gets or how far it throws heat,
so the firebreak widths below hold as they did.

**Spread.** A fire carries only so far from where it was lit
(`FireFuel.spread`): each burning cell knows its distance from its fire's
ignition, counted in a straight line hop by hop (in quarter cells), and a cell
further from the ignition than it can carry does not catch. What a cell can
carry is its fuel's spread times its own share, 65% to 115%, seeded per cell
and clumped in three-cell blocks; its ignition varies the same way, 85% to
120%. So a burn is a ragged disc round where it was lit, never a box: a
flamer sweep's patch fills about three quarters of its bounding box, where
counting distance as rings filled all of it. Grass carries a fire two cells,
brush three, a crown four, debris two, a light wall four and a room's boards
three; creep, living or dead, carries it as far as it stands, and a cell of
creep is a fresh ignition for whatever it lights. So a flamer's burst in a
meadow burns a patch about two cells round where it landed and goes out, and a
fire that reaches creep runs through all of it. A room is the exception: once
half of it has caught it flashes over (Buildings). Dry country (`FireField.setTinder`, a mission's word) carries a
fire three times as far. Every igniter (an incendiary's splash, a burning
wreck, a shell's blast on dry fuel) lights at distance nought, so a second
burst further on starts a second patch. `-Dbattle.fire.spreadScale` scales
every limited spread, `0` being the old unbounded fire, while the owner picks
how far grass carries it. A flamer throws only where its fire cannot come
back (Starting fires): on grass that needs the target about five and a half
cells off (two cells lit, up to two and a third carried, one beside), in
brush six and a half and among crowns eight, which is why the flamer reaches
eight cells (the owner's fire-flamer-woods pick, 2026-10-24); at seven it
had half a cell to throw in from any fuel. An eight-charge sweep at full
reach burns about 85 cells of meadow, 100 of brush or 120 of wood, and its
nearest flame stays five, four or three cells from the squad.

**Blazes.** A fire big enough becomes a **blaze** (`FireField.isBlaze`): a
burning cell with 96 or more of the 168 cells in the 13-cell square round it
burning too. A blaze is lit afresh where it stands with twice its fuel's carry
onward, and throws its heat a ring further, so a big fire runs on through fuel
a burst further than a small one would. Nothing a blaze lights, nor anything
those cells light in turn, can become a blaze itself, so a blaze is one burst
outward that dies down inside the fuel, not a chain across every wood. One
flamer charge never sets enough burning at once to blaze (about 70 cells at
most, in forest), nor does a sweep of charges through meadow or brush. A sweep
through forest does, and so does a firebomb stick
(`AirOrdnance.INCENDIARY_BOMBS`, the napalm strike: six rounds, each lighting a
disc four cells round) into brush or wood. A forest sweep that would burn about
200 cells burns about 780. Dry country needs no lower bar: its fires carry three
times as far and grow to a blaze sooner. A break still holds against a blaze if it
is up to a cell wider than one that stops the same fire unblazed (a crown blaze
reaches a fourth ring only in a strong wind, downwind, as a crown fire does). A flamer's throw gate (Starting fires) does not
foresee a blaze, because one throw never makes one.

**Burnt ground.** Scorched, fuel-less, walkable where it was walkable, and a
firebreak by definition. It stays scorched for the battle (it does not
regrow); what it refuses, it refuses for a while (Firebreak).

**Fire step.** The field steps five times a second (every 6 ticks), not every tick: fire is slow next to bullets, and the field's cost scales
with the cells burning, never with the map.

**Wind.** A battle may have a wind: a direction and a strength, fixed for the
battle by its mission (calm unless the mission says otherwise). Wind tilts
how heat is shared (more downwind, less upwind) and so stretches the reach of
a fire downwind; a strong wind lets a crown fire throw heat over a gap a calm
one could not. Smoke drifts with it.

**Firebreak.** Any band of ground with nothing to burn, wide enough that no
burning cell's reach crosses it: two cells stop a ground fire in calm air,
three a crown fire, more downwind. Roads, cleared ground, water, and above
all ground that has already burnt. A **burn line** is a firebreak moving: the
burnt ground behind a front someone lit on purpose.

- **Burnt ground refuses creep, for a while.** For about three minutes after
  it burns out a cell cannot be claimed by creep (the creep field asks the
  fire field); after that the sources that reach it fill it again. A
  source keeps its claim on the ground it held, so the creep comes back over
  a burn from the edges it still holds once the refusal ends. A living source
  behind the line cannot re-take what the line has passed until then, and
  ground a source still reaches beside the break is still the creep's.
- **Burning creep recedes.** A burning cell of creep is lost to the creep as
  it burns: its coverage goes to nothing and it does not regrow while it is
  refused: the creep under a fire crisps and is gone in about three seconds
  (`CreepField.scorch`, the refusal hook). Growths standing on it die as they
  do when their creep dies.
- **Burning never collapses a bore.** A bore is fought through its durability;
  fire takes the creep and the growths on it, and scorches the bore's heaved
  earth, but does no harm to the bore itself.

**Smoke.** A brush or crown fire smokes; a grass or creep fire burns too low
and fast to screen and gives off only a thin haze, which is drawn and hides
nothing (`FireSmoke.hazes`). A smoking cell holds smoke over itself and, in a wind of
any strength, over the cell downwind of it, for exactly as long as it burns
(`FireSmoke`). The smoke is the grid's transient opacity, the same a smoke
grenade's cloud holds, so sight, the fog and every observer read it with no
new path: a burning hedge or forest hides the fight beyond it, and the screen
is gone when the fire is. `-Dbattle.fire.smoke=false` is the control.

## Damage

**Fire hurts what stands in it, and a little what stands beside it**
(`FireHarm`). Every fire step a unit in a burning cell takes damage scaled by
the cell's intensity (8 hit points a second at full heat, so a marine dies in
about three seconds in a crown fire and a few more in a grass fire); a unit
beside a burning cell takes a tenth of its hottest neighbour's.

**What fire does depends on what is burning, not on what stops bullets.**
Plate that turns a rifle round does not keep heat off the body inside it, so
fire is judged by the kind of target rather than by its armour pool:

- **Soft bodies** (infantry in ordinary armour, militia, civilians) take fire
  straight to health, their armour untouched. A marine in full flame is dead
  in about three seconds.
- **The swarm** is flesh with nothing over it and takes half as much again: a
  brute dies in about three seconds, a matriarch in about a minute and a half.
- **Sealed power suits** are the armour that does keep heat out: their armour
  soaks fire as it soaks a round, and a suit lasts several times as long.
- **Hulls** (mechs, machine frames, vehicles) take a tenth, so armour can drive
  through a grass fire.

**Morale.** Fire damage carries morale as being hit does, so a team standing
in fire drains toward breaking as one under fire would; fire near a team that
is not touching it costs nothing.

**Static things take nothing.** Turrets, hubs, bores and the swarm's growths
are fought through their durability, never burnt. Burning never collapses a
bore; growths die with the creep a fire takes. Buildings burn by what they
are made of (Buildings).

**Trees** burn as fuel and fall when three crown cells have burnt out.

**Bodies.** A swarm body lying in a burning cell is consumed, as a digested
one is. The dead of every other side are left to be recovered; a body being
dragged is lying nowhere and does not burn.

**Pathing.** Fire is folded into each side's route costing
(`FireRouteCost`): a burning cell costs up to ten times its ground by
intensity and a cell beside one three times, republished at most twice a
second while anything burns and not at all once nothing does, so routes go
round a fire where there is a way round and through it where there is not.
A fleeing unit never picks a refuge in or beside fire (`FireEffects`).
`-Dbattle.pathfinding.fireRouteCost=false` is the control; in `fire-harm`'s
crossing it is the difference between a squad that goes round a burning band
and one that walks into it and dies there.

**Getting out of the way** (`FireEscape`). A person in the fire's way moves
before doing anything else: in a burning cell, or standing on fuel within
three cells of one, because waiting for a front to arrive is waiting to be
overtaken, however slowly it walks. He goes to the
nearest safe cell, one out of and not beside the fire that cannot burn (burnt
out or bare); ground just ahead of a front is not safe, since the front takes
it. Steps through burning cells count five times, so a thin front is crossed
into the burnt ground behind it and a deep one is outrun to bare ground. For a
marine this is a reflex below grenade evasion and above everything else
(`InfantryReflexes.IN_FIRE`): he neither shoots nor takes up a new step until
he is out. Out of the way, nobody but the swarm, armour and drones steps from
a clear cell into a burning one: the walk waits at the fire's edge, wherever
the route came from, since the route costing already sent it round where
there was a way round and a fire passes. A squad holding ground the fire
takes therefore stands off at the edge and returns when it has burnt out.
`-Dbattle.fire.stepOut=false` is the control.

**The swarm fears fire too.** Its flow fields charge each step by the fire's
multiplier on the cell entered (`SwarmFlowFields.setStepCost`, fed the fire
alone, not a side's costing), so the swarm streams round a burning stand
where there is a way round and through it where its prey lies beyond. Fields
rebuild as the fire changes, at their ordinary cadence, and pay nothing while
nothing burns. The fire route-cost control switches this off with the rest.

## Buildings

**A building burns by its materials, not as a whole** (`BattleFire`). A light
wall or window (an ordinary building's, at most 100 hit points) is structure
fuel; a plain indoor or panelled floor is furnishing. A **fortified
structure**, one walled anywhere by something heavier than a light wall or
floored as military, burns at none of its walls, windows, doors or floors, so
compounds, redoubts, military buildings and forts stand in a burning field.
Which structures count as fortified is read off their walls when the battle's
fire is made.

**Walls stop heat.** A standing wall or window stops heat passing over it, so
fire reaches a room only through a doorway or through a wall or window that
itself burns. A light wall held in a fire catches slowly; once it burns
through it falls to rubble as a breach does (`MapEditor.damageWall`), opening
the building to movement and sight, and its rubble is burnt ground.

**Rooms flash over.** A **room** is the furnished floor its walls close round,
cut off at its doorways: a floor cell walled on both sides is a doorway, not
part of either room. Fire that gets in catches a patch of the floor, carried
three cells as through brush. Once half the room has caught, the rest of it
**flashes over**: every cell left catches at once, and the room burns whole
with its walls held in the heat of all of it. Floor joined without a doorway,
an open-plan house's, is one room and goes at once. A floor of more than
400 cells is a hall, not a room, and burns only as far as its boards carry.
Each cell that catches burns through the roof above it, so the burning is seen
from above and the room is open to the sky. Burnt rooms char and ash as burnt
ground does.

**A house burns as hard as it is hit.** Fire passes from a room to the next
through a doorway or a wall that burns through. A flashed room keeps a single
cell of carry beyond itself: it holds its walls in its heat, so they burn and
fall, and scorches its doorways, but does not carry its fire on into the next
room. So one flamer charge into a room of a light-walled house burns that room
whole and leaves the rest standing, and a house burns down room by room only as
fast as fire is put into it: charges into several rooms, a grass front against
its walls, a firebomb. The
flamer's throw gate (Starting fires) takes a building's floors and walls to
burn as far as they stand, so it never throws into a room its squad stands in
or beside.

**What it costs.** Whether a cell stops heat is cached in the field and
reread only on a step after the map's walls have changed, and a map with no
walls never asks, so a fire in open country pays nothing for buildings.

The owner chose this (2026-10-22): light walls the fire burns against fall,
and fire gets into rooms where the materials can burn.

## Starting fires

**Deliberate fire always lights; ordinary war lights only dry country**
(`FireIgnition`). A battle starts with its country not dry: in it, only an
incendiary starts a fire. A mission whose country is dry says so
(`FireField.setTinder`), and there ordinary war starts fires too. The rule
keeps every battle that never asked for fire as it was: a grenade in a park
does not set Conquest alight.

- **Incendiary weapons.** A weapon whose data carries `incendiaryHeat` puts
  that heat into every cell its blast covers, through its ordinary delivery
  (`PendingDetonation.incendiary`). Two specials carry it, issued by mission kit
  until the economy decides their price: the **incendiary grenade** (a thrown
  thermite charge with little blast) and the **pack flamer** (a short, flat,
  seven-cell lob of burning gel).
- **Incendiary rounds.** A fire mission may be incendiary
  (`FireMission.incendiary`): the tubes serving it load incendiary rounds for
  the bodies on its ground. A bomber may be called with firebombs
  (`AirPoolService.requestIncendiary`, `AirOrdnance.INCENDIARY_BOMBS`), the
  same airframe and stick as an ordinary strike from the same stock. Both
  follow the throwing rule below: a round or a stick whose fire could come to
  the side's own goes out ordinary instead, so the choice of ammunition is the
  order's and the safety is the crew's.
- **Throwing fire.** A squad spends an incendiary as it does a frag, on soft
  infantry it has seen, with two differences, both because the fire does the
  rest of the work. One man standing in fuel is worth a throw, where a frag
  wants two in its blast. It is aimed at fuel that can still catch: on ground
  already burnt or burning it moves to the nearest unburnt fuel that keeps the
  target in the blast, and with none there the throw is judged as a frag. And a carrier does not throw where the fire could
  come to its own side (`FireReach`): the fuel the ordnance would light (its
  blast and scatter round the point; for a firebomb stick, the director's
  danger-close ring, since a stick runs along the pass), followed outward
  through any gap of three cells or less, which heat crosses, and no further
  from where it was lit than each cell's fuel carries a fire (Spread), counted
  as the field counts it; a friend on that ground or beside it vetoes the
  throw. So the check is the fire the field will actually burn: a flamer at
  its eight-cell reach throws into a meadow, brush or a wood, and one closer
  holds when the fuel between could carry the fire back. A squad on a road, a
  firebreak or burnt ground throws freely.
  `-Dbattle.fire.throwSafety=false` is the control, for thrown fire, rounds
  and firebombs alike.
- **Shells.** In dry country a heavy ordinary blast (radius 2.5 cells and up:
  shells, not grenades) puts enough heat into the ground it covers to catch
  grass and dying creep, never a living crown. A walking barrage of ordinary
  shells over dry country therefore starts grass fires; one of incendiaries
  lights the forest. The barrage itself is the artillery work's
  (`bloom-mission-arc.md`, Walking barrage): it only has to fire its blasts as
  detonations for this to hold.
- **Wrecks.** In dry country a vehicle, mech or turret wreck burns as an
  igniter for a minute, heating its cell and the ring round it, so a burning
  wreck in a field lights the field.

## Look

The fire field drawn (`FireRenderSystem`), from what the field already holds;
nothing is drawn for a battle that never lit anything.

- **Scorch** lies on the ground under everything that stands on it (the grass
  under a wire fence or a prop chars like the grass beside it), and
  reads as a field burnt days ago: singed straw-brown, black only where heavy
  fuel stood, pale ash drifting over it. It is not a fill: it darkens and
  browns the ground's own albedo, so the texture always reads, and lays straw,
  charcoal, ash or a creep's cracked crust over that. It varies at three
  scales of noise and with the fuel that burnt (grass a light singe, sprinkled
  ash and a few surviving tufts; hedges and the ground under trees darker,
  with ash piles; creep its own crust; pavement only soot smudges), and its
  outline wanders through cells with a singed band just past it. One shader
  draws it (`ScorchShader`, a multiply then an overlay, inside the ground's
  colour target with the creep) and its CPU model (`ScorchShading`) draws
  review frames; `shaderEvidence` holds the two to the same pixels.
- **Glow** is a soft warm disc under each burning cell, a little wider than
  the cell, so a small fire's ground glows round rather than in squares.
- **Small fires and big ones look different.** The field weighs every burning
  cell's **mass**, the cells within three of it burning too
  (`FireField.massAt`, 0 to 48). A small fire (a flamer's patch, a
  smouldering edge: a mass of 22 or less) draws one small flame tuft and a
  scatter of glowing embers per cell; a big one (42 and more, the heart of a
  wide fire) draws the full flame beds below; between, a cell grows from the
  one into the other. So a squad's flamer work reads as embers and small
  flames with a few hot cores, and the big flame carpet is kept for a fire
  that has grown large. The same mass is what a blaze will be judged on.
- **Flames** are seen from straight above, as the rest of the battle is:
  patches of burning ground, irregular, with several hot spots and a ragged
  rim, never a starburst (a ring of long tongues round one core reads from
  above as a blast), from a sheet of their own
  (`graphics/fx/fire_topdown_a-flame-bed.png`, a bed's eight-frame loop and a grass
  fire's few small flame islands). Each is drawn larger than its cell, so a
  front's patches run together into one carpet. The fuel sets the bed: grass
  burns as islands, brush, creep and debris as one bed, a building as two, a
  crown as three large ones, so a crown fire spreads wider than a grass fire. Inside a
  burn, where a cell has fire on every side, the beds thin and fade, so a
  fire's edge burns brightest and the ground and units in a burnt-over area
  still read. Each bed
  is turned to its own angle, starts its loop at its own frame and rate and
  turns slowly as it burns, and at play zoom it fades from one frame into the
  next, so neither the loop nor the grid shows; intensity sets its size and
  strength, and it is a little transparent so ground and units read through.
  Zoomed far out a cell keeps one bed and its glow. The sheet is built
  geometry first (`art-source/fx/fire-topdown/`: a code-drawn guide reskinned
  by ImageGen); the owner picked the flame bed of three looks (the others' sources stay
  there).
- **Embers** are a spark rising over each of the hottest cells, at play zoom
  only.
- **Charred trees.** A tree whose middle burns darkens as it burns and is
  drawn as char once its ground is burnt, whether it still stands or has
  fallen to its destroyed art.
- **Smoke** is a dark puff rising off each smoking cell and leaning
  downwind, at play zoom only: the picture of the screen it holds. It is
  drawn thick and dark enough to read over flame and over grey creep
  (a faint grey puff left smoke that blocked sight barely visible). A low fire's haze is a paler, thinner
  puff over every other burning cell.
- **Fire light.** A burn lights what is round it, as a few pooled lights
  rather than one per cell (`FireLight`): burning cells are gathered into
  six-cell squares, and each square with fire in it is one light at its
  hottest middle, reaching about as far as its fire is wide and as bright as
  it burns, flickering at its own phase. Every square is kept; a frame
  draws those whose pools reach the view, the brightest 160 of them when more
  do, so a fire in view is never left dark by fires elsewhere. At night they carve warm orange pools out of the dark as a yard lamp
  does, and, like a lamp, do not light through a roof, so a night burn reads
  from across the map and lights the men beside it. In game the same lights
  are the ground relief's event lights (`GroundLightService`), competing with
  muzzle and impact flashes for its nearest eight. The night pools are drawn
  in review frames (`fire-colony` draws the front, a grove and the whole map at
  night with no lamps); `renderEvidence`'s fire fixture is by day, so their
  cost is not yet measured there.
- The look is costed against its own control in `renderEvidence`
  (`-Dbattle.render.fire=false`), on the green colony with a front burnt into
  its groves: there its two layers cost about 0.3 ms of collection and drain
  at a lane framing and about 0.65 ms at the whole map, with nothing else
  moving; the scorch layer adds about 0.05 ms of drain at the whole map. The
  `fire-colony` snapshot suite shows the look on real ground: the canonical
  colony on a verdant world, burnt from its west edge.
- On The Burn at its peak (`renderEvidence` maps `the burn` and `the burn at
  night`, about 6,000 cells alight, framed on the densest fire) the fire's
  flames, glow and smoke cost about 4 ms of CPU at a lane framing, almost all
  of it drain, and little at the whole map, where flames are one bed a cell
  and smoke is not drawn. At night the fire light's pools add a few ms at a
  lane and more at the whole map, but the night shade itself costs more than
  the fire there, with or without one. The simulation's fire costs about
  0.2 ms a tick on average and 2 ms at p99 (`burn-sim.md` beside the render
  report). `fire-burn` shows The Burn at its peak.

## Evidence

`fire-line` (`sceneEvidence`) lights a line along a road and lets it burn east
through a hedged meadow into a creep-grown forest and up to a bare four-cell
road with grass, trees and a second creep patch beyond it, once calm and once
in a strong tailwind. Its verdicts are the mechanics: nothing past the road
catches, burning creep is gone within three seconds, burnt ground holds no
living creep while it refuses it, and a tree with three burnt-out crown cells
has fallen. How fast the front ran and how much creep it took are metrics.
`-Dbattle.fire=false` is the control: nothing catches.

`fire-harm` (`sceneEvidence`) holds the damage mechanics: a squad caught
standing in a meadow as a grass fire runs into it, with a dead runner in the
way, and a squad crossing a field with a burning band across it and a way
round its end. Verdicts: a body in a burning cell loses health every fire
step, a swarm body lying in fire is consumed, and burning ground costs more
than bare ground in the published route costing. Health lost, deaths and the
crossing's time in fire are metrics.

`fire-fear` (`sceneEvidence`) holds getting out of the way: a squad holding a
meadow as a grass front runs at it, bare ground a few cells past the meadow's
edge, and runners on the prey field after marines beyond a burning band of
brush with a way round its end. Verdicts: no marine stands in fire longer
than the time to cross a front's depth, and no runner sets foot in a burning
cell; each passes trivially under its own control, where time in fire is the
reading (863 marine-ticks in fire against 113, and 1,877 runner-ticks against
none, when it was written).

`fire-ignition` (`sceneEvidence`) holds the starting rules: an incendiary blast
on grass lights it, an ordinary shell lights nothing in country that is not
dry and catches grass once it is, and an ordinary shell alone never lights a
living crown. Its `kit` loop holds the carrier's rules: grenadiers on a
firebreak throw at a cluster in grass, flamers on a firebreak burn a lone man
in grass, and flamers standing in the meadow their targets stand in hold their
fire; with `throwSafety=false` that squad lights its own meadow and the lone
man is left alone, which is the control. `fire-rounds` does the same for the
support weapons: a tube on an incendiary mission and a bomber with firebombs
light the grass round a militia section, and with two marines in that grass
the tube fires shells and the bomber an ordinary stick.

`fire-scale` (`sceneEvidence`) burns a whole 560x336 country of grass and
groves with creep over its middle in a fresh wind, up to a four-cell road. The
break holding at scale is its verdict; the fire's peak burning cells, the
mean, p99 and worst simulation-thread cost of a tick with fire in it
(`FireCost`: the field, its harm and its route costing together), and how
long the country takes to burn are its metrics.

`fire-buildings` (`sceneEvidence`, and the `fire-buildings` snapshot suite)
runs a grass fire into two houses on the green colony: the light-walled house
standing most fully in grass, and its neighbour, built fortified for the
occasion (walls raised to 150 hit points before the fire is made). Verdicts:
a light wall cell that burns through is rubble, the light house's rooms burn,
every room cell that caught is open to the sky, and the fortified house burns
nowhere although the fire reaches it. Walls fallen, room cells burnt and when
the fire got in are metrics.

`fire-smoke` (`sceneEvidence`) burns a hedge between two watchers on bare
ground: they see each other before it is lit, not while it burns, and again
once it is out. How long the screen stood is a metric.

## Laws

- **Fire goes where the fuel goes, as far as the fuel carries it.** Nothing
  scripts a burn; a fire's shape is the fuel's shape and the wind's, and green
  country lets it go out.
- **A break wide enough always holds.** Spread has a hard reach, so a firebreak
  is a rule a player can plan with, not a chance.
- **Deterministic in the seed.** Hashes, never shared random draws; the field
  steps on the simulation thread at a fixed rate.
- **Pays for what burns.** A battle with no fire pays nothing; a burning one
  pays per burning cell, never per map cell.
- **Fire is ordinary.** Its damage, its smoke and its effect on creep go
  through the systems that already own those (damage, smoke partitions, the
  creep field), never through a second model of them.

## Boundaries

- **Creep** (`colony-breach-nouns.md`) owns creep; fire tells it a cell has
  burnt and answers whether a cell is refused.
- **Creep on vegetation** (the creep-vegetation work) owns how creep climbs
  and kills trees; fire burns trees whether creep has killed them or not.
- **The walking barrage** and the advance behind it belong to the artillery
  work; fire only asks that a shell's blast can carry heat.
- **Smoke as a partition** (`ai-nouns.md`) owns how smoke cuts sight; fire
  only holds the grid's smoke opacity over what burns.
