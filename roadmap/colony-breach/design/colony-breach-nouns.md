# Colony Breach — Nouns

Status: ACTIVE

Written: 2026-10-21

Colony Breach is the refresh of Civilian Rescue, the alien-swarm colony mission.
It replaces Civilian Rescue in place: the same campaign event, mission factory,
fixture lineage and Marine rescue commander, with a colony worth fighting
through and a swarm that comes out of the ground rather than the map edge.

This doc owns the swarm side, the colony, the bores and the breach sequence.
The Marine corridor commander, the cohort, the shelter release and the lift
remain Mission Command authority in `extraction-command.md`; this doc names the
inputs Colony Breach adds to it and nothing more. Live pressure acceptance
inherits `rescue-pressure-acceptance.md`.

## The fantasy

A colony is being eaten from underneath. The company lands at its edge, fights
to the shelter where the survivors are sealed in, and has to hold a lift zone
open long enough for shuttles to take them off. The swarm does not walk in from
the horizon: it erupts out of bores inside the colony, and every bore left open
gets worse. The player can spend force to collapse bores and buy quiet, or
harden the lift with a grounded tender so that the rest of the company is free
to go hunting. Air power can harass but cannot clear the map, because the bores
defend the sky over themselves.

## Vocabulary

**Colony.** The generated settlement the battle is fought in: a town plus an
outlying installation (mine head, refinery or pumping yard), declared through a
precinct plan rather than the stock city. About 400x240 cells. Places in the
colony are where bores erupt, so the precinct layout is gameplay, not set
dressing.

**Colony militia.** Allied, non-player defenders who survived the first night.
They hold a few buildings through ordinary garrison manning, crew whatever
colony turret emplacements still work, and may run a technical or two. They
are a resource the player inherits, not a force the player commands. The
shelter guard is one militia post among several.

**Shelter, cohort, lift.** Unchanged from Civilian Rescue (see
`extraction-command.md`): the cohort is sealed in the shelter until a Marine
reaches it, then walks to the lift under escort and boards only once a shuttle
is physically down.

**Breach.** The mission-level event of the swarm coming up through the colony.
The breach has a shape over time (see *Flow*) driven by the **breach sequence**,
the one story sequence the battle runs.

**Bore.** A physical hole in the ground that the swarm comes out of. A bore is a
destroyable structure on a cell footprint with durability, a death cascade and
a map decal. The swarm director spawns only from living bores (plus a thin
perimeter seepage in the opening, before the first eruption). Collapsing a bore
removes its spawn capacity for the rest of the battle. Bores erupt at
authored-by-rule places in the colony (inside precincts, never inside the
shelter or lift protected zones, never on top of a Marine), not at the map
edge.

**Bore tier.** A bore grows while it stays open: **fissure** (runners only, low
rate) → **bore** (adds brutes and spitters) → **maw** (adds heavy strains, higher
rate, a wider spore umbrella). Growth is on a clock from eruption, reset or
slowed by damage taken. Tier is what makes leaving a bore alone a decision with
a cost.

**Mother bore.** The one bore whose collapse ends spawning from its precinct
cluster and is the target of the optional hive objective. It erupts later than
the others, starts at bore tier and is the only bore that can produce a
matriarch.

**Spore vent.** The swarm's air denial. Each bore projects a spore umbrella over
itself: vents throw flak-like spore bursts at aircraft inside its radius.
Radius grows with tier. Collapsing the bore kills its vents. Spitters can also
engage low fliers. Law: no air sortie should be able to collapse a bore that
still has its umbrella without taking losses; air is for after the ground push.

**Swarm director.** The non-human pressure owner (today
`SwarmReinforcementSystem`). It decides how many, from which bore, and of which
strain, against a population target that scales with the number and tier of
living bores. It publishes an immutable pressure snapshot for evidence and
diagnostics. It never reads hidden Marine state the swarm could not sense.

**Swarm strain.** A kind of swarm body with its own stats, appearance family and
behaviour:

- **Runner** — the shipped 20 HP melee body. Volume.
- **Brute** — slow, armoured, melee; breaks doors, windows and light walls so
  that a garrisoned building is not a permanent safe room. Draws fire.
- **Spitter** — fragile, keeps distance, lobs acid at infantry and low fliers.
  Punishes standing still behind cover.
- **Matriarch** — one per mother bore at maw tier: large, slow, very durable,
  wrecks emplacements. Answered by mechs, heavy weapons or a lot of satchels.

Strains are added to a bore's mix by tier, not chosen per wave by hand.

**Swarm goal.** A place the swarm is drawn toward: the shelter, the lift, the
nearest Marine body. Swarm movement toward a goal is a shared **flow field** per
goal rather than a path per body, which is what lets the swarm be large on a
large map.

- **Prey** is the goal a hunting body descends when its target is far off: every
  combatant the swarm is hostile to, less the shelter guard while the shelter is
  sealed. It is gathered from the battle each refresh, so "nearest" means
  nearest by the way the ground runs, not as the crow flies. Civilians are never
  prey sources; a body that has found one chases it directly.
- **Shelter** and **lift** are disclosed, so the mission declares their cells;
  the swarm knows where they are without sensing them, as the noun's law allows.
- **Bore** is the goal of a body carrying biomass home: the nearest living
  bore. The swarm's own holes, so their cells are declared and redeclared as a
  bore opens or collapses.
- **A field makes a stream.** The descent is the same from every cell and has
  no crowd term, so bodies released together follow one lane down it, which is
  the river a spawning surge needs; they break apart where local pursuit takes
  over. Nothing separates swarm bodies from each other today (Marines steer
  away from them, not they from one another), so a later separation pass must
  leave a surging body on its lane to keep the stream.
- **Strategic movement and local pursuit.** A body whose target is more than a
  few cells away moves down its goal's field; within that range it runs its own
  short path to the one victim it has chosen. Every strain moves through the
  same call, so a change to how the swarm travels reaches all of them at once.
- **A field belongs to the battle's navigation grid.** It is derived from that
  grid's walls and retired by its topology changing, so two battles in one
  process can never read each other's fields (the `LosCache` lesson). It is
  rebuilt when its sources move or the walls change, at a bounded cadence and a
  bounded amount of work per tick, and published whole: a body always descends
  a complete field, an older one at worst, against the current walls. Fields
  are deterministic under the seed, so replays match byte for byte.
- `-Dbattle.swarm.flowField=false` is the control: every body paths for itself,
  as the swarm did before fields. What the fields buy, by map and swarm size, is
  in `swarm-movement-cost.md`.

**Hardpoint.** A position the player has fortified with tender construction
(Vulcans and whatever else the tender can build). A hardpoint is how the player
frees troops: a lift zone that can hold itself is a lift zone that does not
need the whole company.

**Tender call-down.** A command power available only if the fleet brought a
tender. The player spends it, picks a landing site, and the tender lands and
grounds there to build. Not every mission brings every tool.

**Hive posture.** The player's pre-launch answer to the optional hive objective,
frozen into the Marine command envelope at launch:

- **Evacuate** — bores are obstacles; the commander engages one only when it
  sits on the route.
- **Opportunistic** — the commander may send a strike detachment at a bore when
  the readiness gate is open.
- **Purge** — the mother bore is a real objective; the readiness gate is
  relaxed.

**Readiness gate.** The commander's rule for opting in to a bore strike, read
from what it can see: (a) the lift zone is hardened (built defences, no losses
on the lift line for a sustained window), (b) the strength left behind meets
the escort's need, (c) a detachment with demolition or heavy weapons exists.
Purge relaxes (a) and (b). When the gate closes the detachment is recalled. The
player can always order any squad onto a bore; posture governs only what the
AI does unprompted.

**Strike detachment.** The squads (and lance, if one is deployed) the commander
sends against a bore. Stable membership, traced like any other command role.

## Flow

1. **Landfall.** The company lands at the colony edge. A thin perimeter seepage
   of runners is the only pressure. Militia hold their buildings.
2. **Shelter.** A Marine reaches the shelter; the cohort releases. This is the
   breach sequence's first trigger: **tremor**, then the first bores erupt over
   the following minute at precincts between the shelter and the lift and on
   the flanks. Seepage stops.
3. **Dig in.** The lift countdown runs. A called-down tender can be grounded
   and building. Bores grow. The director's target climbs with every living bore
   and tier.
4. **Mother bore.** Later in the battle the mother bore erupts away from the
   lift, already at bore tier, with the widest spore umbrella.
5. **Lift.** Shuttles land; the cohort boards. The battle can end on the lift
   (the rescue win) with or without the mother bore collapsed. Collapsing it is
   the hive bonus, settled through ordinary mission outcome.

The breach sequence owns the clock and the triggers; beats are what it
publishes. Beats are presentation and evidence, never inputs to the simulation.

## Beats

All beats are prefixed `colony_breach.`. Numbered beats index bores in eruption
order from 1. These ids are the contract between the sequence, the audio and
the evidence; change them here first.

| Beat | Audience | When |
| --- | --- | --- |
| `landfall` | PLAYER | First Marine on the ground |
| `militia_contact` | PLAYER | First Marine within sight of a militia post |
| `shelter_reached` | PLAYER | Cohort released |
| `tremor` | PLAYER | Breach begins (before the first eruption) |
| `bore_erupted_N` | PLAYER | Bore N opens |
| `bore_grew_N_bore` / `bore_grew_N_maw` | PLAYER | Bore N reaches that tier |
| `bore_collapsed_N` | PLAYER | Bore N destroyed |
| `mother_bore_erupted` | PLAYER | Mother bore opens |
| `mother_bore_collapsed` | PLAYER | Mother bore destroyed |
| `matriarch_emerged` | PLAYER | A matriarch spawns |
| `tender_down` | PLAYER | A called-down tender has grounded |
| `hardpoint_established` | PLAYER | First tender-built defence completes at the lift |
| `strike_committed_N` | OBSERVER_ONLY | Commander sends a detachment at bore N |
| `strike_recalled_N` | OBSERVER_ONLY | Readiness gate closed, detachment recalled |
| `lift_inbound` | PLAYER | First shuttle committed |
| `lift_down` | PLAYER | First shuttle landed |
| `cohort_boarded` | PLAYER | Last surviving evacuee aboard |
| `colony_lost` | PLAYER | Cohort lost |

## Laws

- **Spawning has a source.** After the first eruption, every swarm body comes
  out of a living bore. No edge spawning in the breach phase.
- **Collapse is permanent.** A collapsed bore never reopens and never hands its
  capacity to another bore.
- **Bores are fought, not scripted.** A bore dies to ordinary damage through
  ordinary durability; nothing collapses a bore by fiat.
- **Strains by tier.** Which strains spawn is a function of the bore's tier and
  the director's budget, deterministic under the battle seed.
- **Air cannot clear the map.** Every bore has an umbrella; the umbrella dies
  with the bore.
- **Posture is frozen, the gate is live.** The hive posture is set at launch;
  the readiness gate is evaluated from the commander's visible picture every
  command pulse.
- **The swarm senses, it does not know.** The director and strains act on what
  swarm bodies sense plus the disclosed goals (shelter, lift); the cohort stays
  hidden until contact, as in Civilian Rescue.
- **Deterministic.** Same seed, same breach: eruption places, timings, strains
  and growth are seeded. Evidence replays must match byte for byte.
- **One story.** The breach sequence is the battle's one story sequence.

## Boundaries

- Marine command, cohort, release, escort and lift: `extraction-command.md`.
- Precinct plans and map requirements: `precincts.md`, `mapgen-nouns.md`.
- Garrison manning for militia posts: `garrisonables.md`.
- Tender, grounded construction and build options: `tender-ship.md`.
- Mech lances and drop allowances: `mechs-nouns.md`.
- Layered bodies and appearance families: `battle-render-nouns.md`.
- Radio and narration: `battle-radio-nouns.md`; beats are consumed there, never
  the other way round.
- Structure durability: `combat-durability-nouns.md`.
