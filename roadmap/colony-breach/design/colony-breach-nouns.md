# Colony Breach — Nouns

Status: ACTIVE

Written: 2026-10-21

Updated: 2026-10-21 — breach clock, eruption sites and the colony map as built by the mission spine.

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

## Origin (mod choice)

Evidence tier: **mod choice** (`faction-lore-nouns.md`). Nothing in Starsector
0.98a canon describes this organism; it is ours, and it must be written so that
it never contradicts canon.

The swarm is not an alien species that arrived from outside. It is a weapon. A
self-perpetuating biological weapon from the late Domain / AI War period,
engineered to deny worlds and outlast whoever released it. It does not need its
makers: it lies dormant underground in reservoirs and old seeding sites, and it
wakes when a colony grows dense enough above it to be worth eating. That is why
it surfaces across the Persean Sector from time to time as a recurring problem
rather than a war with a front: a mining colony goes quiet, a relief call comes
in, and somebody's company is paid to get people out.

Consequences that the design keeps:

- **It is a system, not a society.** No diplomacy, no faction relations, no
  commander with intent. The swarm director is a process, and the strains are
  stages of a life cycle that someone designed.
- **It eats to grow.** Biomass is its economy. Colonists are its food (see
  *Harvest*), which is why the colony is the target and why rescue starves it.
- **It recurs.** Outbreaks are a sector phenomenon: they show up at colonies
  across the campaign, are never "defeated" for good, and a collapsed mother
  bore ends an outbreak, not the weapon.
- **Who made it is a question, not an answer.** Briefings and radio may
  speculate (Domain denial programme, a Tri-Tachyon-era leak, something older);
  no text states the maker as fact.

Names, the life cycle in in-world terms, each faction's view and the tone and
vocabulary for briefings, radio and item text are in `swarm-lore-guide.md`.

## Vocabulary

**Colony.** The generated settlement the battle is fought in: a town plus an
outlying installation (mine head, refinery or pumping yard), declared through a
precinct plan rather than the stock city. About 400x240 cells. Places in the
colony are where bores erupt, so the precinct layout is gameplay, not set
dressing.

The colony is the campaign's high-risk rescue. It is laid out rather than
derived from the event's market — a town at the centre, a working
installation (depot character) to one side and a dormitory suburb to the
other, the side seeded — because the colony is the same kind of place whatever
world the event names, and a rescue fixture has no market to derive from. Its
map is mission-owned (`MapScale.COLONY`, 400x240) and must contain a
residential shelter, a clear lift footprint and enough buildings for the
eruption sites; a map that lacks any re-rolls the seed. Lower risks and the
debug stress path keep their tier map and run the same breach on it, seating
whatever eruption sites that smaller map has room for.

**Colony militia.** Allied, non-player defenders who survived the first night.
They hold a few buildings through ordinary garrison manning, crew whatever
colony turret emplacements still work, and may run a technical or two. They
are a resource the player inherits, not a force the player commands. The
shelter guard is one militia post among several.

On the colony map three more buildings near the cohort's walk are held, spaced
apart and clear of the shelter, the lift and the landing pads; each fireteam
holds its building's held place under garrison authority, so it mans the
building's windows and doors the way any held building is manned. Two of those
posts keep a working heavy-machine-gun emplacement outside the door and two a
parked militia technical. Placed hardware stands on open ground away from the
cohort's walk to the lift, because a gun or a truck in a one-lane street closes
it. Lower risks keep the shelter guard alone, and
`-Dbattle.colonyBreach.militia=false` is the control that drops the rest.

**Colony population.** The colonists outside the shelter: ordinary fleeing
civilians, not part of the cohort and not counted by the evacuation, spread
through the colony's buildings a few to a house (every house before any gets a
second), clear of the shelter and the lift. They are what *Harvest* feeds on, so
there are bodies at stake wherever a bore comes up. The count is drawn from the
seed between 48 and 64, and the breach snapshot records how many were placed
and how many are still alive. Lower risks keep the stock handful of ambient
civilians.

**Heavy assets are what the fleet brought.** The company's mechs and vehicles
reach the battle through its own Mech Support power, which every battle
honours; the rescue adds nothing to that. A mech-lance allowance for the
Marine commander is honoured only where the mission declares one, never derived
from the company's mechs, because an allowance is a separate payment and would
field the same machines twice.

**Shelter, cohort, lift.** Unchanged from Civilian Rescue (see
`extraction-command.md`): the cohort is sealed in the shelter until a Marine
reaches it, then walks to the lift under escort and boards only once a shuttle
is physically down.

**Breach.** The mission-level event of the swarm coming up through the colony.
The breach has a shape over time (see *Flow*) driven by the **breach sequence**,
the one story sequence the battle runs.

**Breach clock.** The breach sequence's own time. It starts at the cohort's
release: the **tremor** follows four seconds later, the first wave of
eruptions opens over the minute after that (the first six seconds in, then
every fifteen), and the mother bore three minutes after the tremor. The clock
is relative to the release and nothing else, so the same colony breaks open on
the same schedule however long the company took to reach the shelter.

**Eruption site.** Where a bore will come up, chosen once at setup and fixed
for the battle (`ColonyEruptionPlan`). A site is the open ground outside a
colony building's door — a place, not a cell picked off the map — and is never
inside the shelter's clearance, the lift's safe ring or a marine landing pad's
clearance, never closer to another site than an eighth of the map's short side,
and always on ground the swarm can walk from to the shelter. The first wave is
four sites: up to half on the corridor between shelter and lift, one on each
flank of it, the rest from whatever is left; the mother site is the eligible
place furthest from the lift. The first wave is numbered in eruption order from
1, corridor sites first; the mother is named rather than numbered. Sites and
their scheduled ticks are published in the breach snapshot from the start, and
each eruption as its beat, whether or not anything yet stands up on the site.
Eruption is the seam: the sequence opens a site at its tick and hands it to the
installed eruption handler, which is where a bore is built.

**Seepage.** The thin perimeter pressure before the tremor. The swarm director
restores only a fraction of its population floor while the colony is seeping,
and its full floor from the tremor on.

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
rate, a wider spore umbrella). Growth runs on a slow base clock from eruption,
is slowed by damage taken, and is **driven mainly by biomass fed to it** (see
*Harvest*). Tier is what makes leaving a bore alone a decision with a cost.

**Harvest.** The swarm's economy. Colonists are food. A runner that brings down
a colonist (ambient colony civilians, militia, and in the end Marines) does not
just kill it: it seizes the body and drags it back toward the nearest living
bore, slowed, as a visible carrier. A carried body delivered into a bore is
**biomass**: it advances that bore's growth and buys the director a burst of
spawn capacity there. A carrier killed on the way drops the body, and the
delivery is lost. Laws:

- Biomass is the only fast path to growth. A starved bore stays a fissure for
  a long time; a fed one reaches maw quickly.
- Every colonist the company gets out, or keeps alive, is biomass the hive
  never gets. Rescue and hive pressure are the same fight seen from two sides.
- Carriers are readable and interceptable: a stream of bodies being dragged to
  a bore is the player's signal that a bore is about to grow.
- Evacuees in the cohort are never harvested while their escort lives. A lost
  evacuee may be.
- Harvest is deterministic and traced: deliveries per bore appear in the
  director snapshot.

**Spawning surge.** How the swarm moves out of a bore. Bodies do not trickle
out one at a time: a bore **spawns**, pushing a dense pulse of bodies out of
its mouth that pours along the flow field in a tight stream (a river, not a
crowd), then breaks apart and fans out on contact or at the end of the run,
and the bore goes quiet until its next pulse. Surges are timed by the director
and sized by tier and fresh biomass, so a just-fed bore answers with a big
surge. A surge is what the player sees and hears coming; the gap between surges
is the window to push a bore.

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
behaviour. A strain is a designed stage of the one organism (see *Origin*), not
another animal: every strain is the Runner's body plan and chitin specialised
for one job in the cycle, and reads that way on screen.

- **Runner** — the shipped 20 HP melee body. Volume.
- **Brute** — slow, plated, heavy melee; the strain that makes a garrisoned
  building stop being a safe room. Its plate (20 points at rating 10) is what
  small arms struggle with: a DMR or rocket goes through it, an SMG barely
  scratches it. A sensed emplacement outranks infantry as its target. When its
  prey stands inside a building it is not in and no opening into that building
  is near, it makes one: it walks to the nearest *light* wall or window of that
  building, breaks it through the ordinary structural path, and goes in through
  the hole. A door is already an opening, so a Brute uses a near door rather
  than breaking the wall beside it. "Light" means a colony wall (at most 120 of
  wall left); fortified and fort walls are out of its reach until something
  else has worn them down. Its blow carries penetration, so it hurts plated
  bodies and emplacements where a Runner's slash only scratches.
- **Spitter** — fragile, keeps its distance, lobs acid. It backs away from any
  ground enemy that closes inside its charge radius and does not lob while it
  runs. Otherwise a low hostile craft in reach comes first, then the sensed
  enemy with the most others inside the splash; the glob is a real arcing
  projectile whose splash goes through the ordinary detonation path, spares the
  swarm and is stopped by an intact roof. Acid on a low flier lands on the hull
  through the aimed-damage path; a craft at cruise altitude is out of reach.
  It stops advancing as soon as something is in range, so the Runners it came
  with close past it and it ends up behind them. No damage over time: the
  damage model has none for ground bodies.
- **Matriarch** — one per mother bore at maw tier: large, slow, very durable,
  wrecks emplacements. Answered by mechs, heavy weapons or a lot of satchels.

Strains are added to a bore's mix by tier, not chosen per wave by hand.
Every strain is the same swarm to everything outside it: one side, one
pressure role, one sensing law, one strategic move toward its quarry
(`SwarmPressureBehavior.pressToward`, the call a flow field speeds for all of
them at once). What differs is what a body does once it has a target. Every
strain body enters the battle through `SwarmStrains`, which issues the
strain's kit (the Brute's plate), so a director, a scene and a debug mission
spawn the same Brute.

A body is carried as itself. A corpse is an entity with its own position and
facing that the renderer reads every frame, and a layered swarm body is drawn
as parts, so a carrier (`swarm-harvest.md`) moves the colonist's corpse with
it and draws it as one more attached sprite; neither the strains nor their
corpse sheets assume a body stays where it fell.

**Creep.** The biological growth living bores spread across the colony:
streets, walls, buildings. It is what makes an ordinary settlement look
unmistakably wrong, and it is the only ground the hive's static growths stand on
away from a bore's mouth. A ground colony (anti-infantry, anti-emplacement)
roots only on creep. Creep recedes when its bore collapses. Mobile strains never
gain ranged attacks to answer fortifications: static growth on creep does that.
Planned in `hive-creep.md`.

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
grounds there to build. Not every mission brings every tool. The power is
mission-agnostic (`TenderCallDown`, offered by the catalog for each tender in
the committed fleet, one charge per tender): the hull comes in through the
ordinary ship call-in, berths at landed scale on a clear footprint, becomes a
grounded host and registers the existing yard construction. It announces itself
with the generic `tender_landed` story beat, which the breach sequence
re-emits as `colony_breach.tender_down`. A landed hull builds only ahead of its
bay (the nose heading it came in on), so where the player lands decides which
way the hardpoint can face.

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

**Built so far.** Landfall, seepage, the release, the tremor and the eruption
schedule run, and every beat through `mother_bore_erupted` and the lift beats
fires. Until bores exist an eruption stands nothing up and the director keeps
spawning at the perimeter after the tremor, so the "spawning has a source" law
is not yet in force. The lift has no countdown yet: the pickup craft comes down
at the start of the battle and waits, so `lift_inbound` and `lift_down`
normally fire before the shelter is reached.

**Known gap: the escort can park the cohort on a long walk.** A civilian holds
while its nearest marine is behind it toward the lift, and the close escort is
sent to the cohort's own cell, so an escort member standing on the cohort's
tail holds it indefinitely once the screens are out of leash. The colony's long,
bending walk found this on the canonical fixture at 0adc5de81 (it timed out
with the cohort parked); the fixture's trajectory no longer meets it, but the
rule is unchanged and it is Marine corridor command's to fix
(`extraction-command.md`). The corridor guide no longer collapses onto the
cohort when its centroid falls on a wall; it routes from the nearest open cell.

## Beats

All beats are prefixed `colony_breach.`. Numbered beats index first-wave bores
in eruption order from 1; the mother bore is not numbered. These ids are the contract between the sequence, the audio and
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
| `first_harvest` | PLAYER | First body seized and dragged toward a bore |
| `bore_fed_N` | OBSERVER_ONLY | Biomass delivered into bore N |
| `surge_N` | OBSERVER_ONLY | Bore N spawns a surge |
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
- **Biomass drives growth.** A bore that is not fed grows slowly; a bore that
  is fed grows fast. Saving colonists starves the hive.
- **Surges, not trickles.** Bores release bodies in timed pulses that move as
  a stream; the quiet between pulses is real.
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
- Setting and canon tiers: `faction-lore-nouns.md`; the swarm's own lore
  guide is `swarm-lore-guide.md`.
- Where and how often outbreaks appear in the campaign: the Civilian Rescue
  campaign event (`CivilianRescueSpawnSystem`), extended by
  `sector-outbreaks.md`.
