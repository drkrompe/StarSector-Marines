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
burnt company.

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

| Fuel | Where it comes from | Load | Burns for | Catches | Notes |
| --- | --- | --- | --- | --- | --- |
| Grass | `GRASS` ground, a grass overlay tuft | 30 | 6 s | easily | The fast carrier; a meadow runs. |
| Brush | a shrub or tuft on the nature overlay (a hedge) | 60 | 12 s | easily | Gone from the overlay once burnt. |
| Crown | a standing tree's crown cells | 200 | 40 s | hard | Slow to catch from a grass fire, long and hot once it does, and throws heat a ring further (a crown fire). Each crown cell that burns out spends part of its tree's structure through `MapEditor.damageWall`, so a tree falls once three have, leaving its ordinary destroyed art and passable ground. |
| Living creep | `CreepField` coverage, alive | 75 | 15 s | moderately | Creep over a crown makes it catch as creep does: an overgrown tree goes up readily. |
| Dead creep | creep dying back | 40 | 8 s | easily | Dry; the edge a source has abandoned goes up fast. |
| Debris | timber and furniture rubble | 50 | 10 s | moderately | Defined, not yet read off the map. |

Fuels stack: grass under a hedge under creep burns for all three loads,
at the hottest one's intensity, and catches as easily as the easiest.
Bodies and wrecks are not fuel: a burning cell consumes the bodies lying in
it, and a wreck is an igniter (Starting fires).

**Does not burn:** bare paving (`STREET`, `SIDEWALK`, `COURTYARD`, `BRICK`,
`STONE`, `TILE`, `STRIPED`), bare `DIRT`, `SAND`, `SNOW`, `WATER`, masonry
rubble, walls and rock, and ground that has already burnt. A building's walls
are masonry and do not carry fire; what is inside them may.

**Creep over a road is fuel.** Paving stops a fire only while it is bare. In
country the creep has overgrown, no road is a firebreak; the only break is
one somebody clears or burns, which is what makes the burn line the way in
(the `fire-line` scene's first draft lost its road to exactly this).

**Ignition.** A cell catches when its soaked-up heat passes its fuel's
ignition. Heat comes from burning neighbours and from an **igniter**: an
incendiary blast, a flamer's stream, a burning wreck, an ordinary shell's
blast on dry fuel. An igniter is not a new delivery system; it is a property
of the weapon or effect that already lands there (Starting fires).

**Burning.** A burning cell has an **intensity** that rises as it catches,
peaks, and falls as its fuel runs out; it spends its fuel at a fixed rate, so
its load is its burn time. Each fire step it gives heat to the cells round
it by ring: a quarter of its intensity to the cells touching it, about a
tenth to the ring beyond, and, for a crown fire only, a sixteenth to the ring
past that, each weighted by wind and by a seeded jitter so the front runs
ragged rather than as a diamond. One burning cell cannot light grass two
rings away, but a front of them can, which is why a single furrow does not
stop a field fire and two bare cells do. A cell that has spent its fuel is
**burnt**.

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
and fast to screen. A smoking cell holds smoke over itself and, in a wind of
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
beside a burning cell takes a tenth of its hottest neighbour's. Fire damage
passes through armour that stops rifle rounds; a mech's or vehicle's hull
takes a tenth of it, so armour can drive through a grass fire.

**Morale.** Fire damage carries morale as being hit does, so a team standing
in fire drains toward breaking as one under fire would; fire near a team that
is not touching it costs nothing.

**Static things take nothing.** Turrets, hubs, bores and the swarm's growths
are fought through their durability, never burnt. Burning never collapses a
bore; growths die with the creep a fire takes. Walls and buildings take
nothing from fire.

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
three cells of one, because a grass front moves about as fast as a man walks
and waiting for it to arrive is waiting to be overtaken. He goes to the
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
  seven-cell lob of burning gel that the squad AI spends on clustered infantry
  as it does a grenade). An incendiary round for the field mortar and an
  incendiary air strike are the same field on their weapons, not yet authored.
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

- **Scorch** lies on the ground under everything that stands on it, and
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
- **Glow** is a warm wash over each burning cell, under its flames.
- **Flames** are restrained tongues from the existing smoke-and-fire sheet,
  smaller than a sheet frame and a little transparent so the ground and the
  tree read through the fire, two to a
  burning cell and three to a crown, each on its own seeded phase and rate,
  sized by intensity, so a front reads as a line of fire at play zoom and a
  crown fire stands taller than a grass fire. Zoomed far out a cell keeps one
  tongue and its glow.
- **Embers** are a spark rising over each of the hottest cells, at play zoom
  only.
- **Charred trees.** A tree whose middle burns darkens as it burns and is
  drawn as char once its ground is burnt, whether it still stands or has
  fallen to its destroyed art.
- **Smoke** is a dark puff rising off each smoking cell and leaning
  downwind, at play zoom only: the picture of the screen it holds.
- **Fire light.** A burn lights what is round it, as a few pooled lights
  rather than one per cell (`FireLight`): burning cells are gathered into
  six-cell squares, and each square with fire in it is one light at its
  hottest middle, reaching about as far as its fire is wide and as bright as
  it burns, flickering at its own phase; only the brightest few dozen are
  kept. At night they carve warm orange pools out of the dark as a yard lamp
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
living crown. Its second loop issues the incendiary grenade and the flamer to
squads facing a cluster in a meadow; whether they throw is their AI's call,
so the cells their fires took are readings.

`fire-scale` (`sceneEvidence`) burns a whole 560x336 country of grass and
groves with creep over its middle in a fresh wind, up to a four-cell road. The
break holding at scale is its verdict; the fire's peak burning cells, the
mean, p99 and worst simulation-thread cost of a tick with fire in it
(`FireCost`: the field, its harm and its route costing together), and how
long the country takes to burn are its metrics.

`fire-smoke` (`sceneEvidence`) burns a hedge between two watchers on bare
ground: they see each other before it is lit, not while it burns, and again
once it is out. How long the screen stood is a metric.

## Laws

- **Fire goes where the fuel goes.** Nothing scripts a burn; a fire's shape is
  the fuel's shape and the wind's.
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
