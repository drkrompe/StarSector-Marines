# Derelict security lore guide

Status: DRAFT — implementation reference for derelict security, the automated
occupant of a ruins expedition (`ruins-expedition.md`). The zones, the
director, the flocks and the heavy line play in battle
(`derelict-security-network.md`, What is built); nodes, posts, cradles, repair
drones and every look are not built.

Written: 2026-11-09

Updated: 2026-11-09 — owner direction: drone swarms with flocking behaviour and heavy mechs. Flocks are the signature and the alarm, an automated heavy line the anchor; posts are secondary.

Read `faction-lore-nouns.md` for evidence rules and shared setting laws, and
`remnants-lore-guide.md`, the guide this one must stay apart from. The battle
this guide gives a voice to is in `ruins-expedition.md` (Occupants) and its
stories.

Derelict security is the **automated site security still running in the
Sector's ruins**: the drones, sentries and control nodes a Domain colony, a
frontier garrison or a corporate black site left behind when its people died,
fled or were evacuated. It is a ground occupant, not a polity. Vanilla gives it
a faction (`derelict`, the Domain Exploration Derelict) and ships, but no ground
presence; everything about how it fights on the surface is mod direction.

## Canon anchors

- **The Domain used war drones, outside the core.** Every Domain drone hull
  (`warden`, `defender`, `picket`, `sentry`, `bastillon`, `berserker`,
  `rampart`) opens with the same line in `strings/descriptions.csv`: automated
  machines of war were forbidden in core Domain space but quietly used beyond
  the officially incorporated volume. Frontier garrisons were bolstered by
  drone ships, and drones were sent against rebellious worlds. The Persean
  Sector is that frontier.
- **A named maker.** The Defender and Bastillon are Mbaye-Gogol products
  (`bastillon`: "MGA"). The drones are specialized by role: point defence
  (Defender), missile fire support (Sentry), close assault (Picket, Warden),
  pursuit with "little consideration for its own survival" (Berserker), and
  artillery built to be cannibalized for repair in the field (Rampart).
- **The Explorarium.** The `derelict` faction is the Domain Exploration
  Derelict, with the Explorarium's logo. Its probes, survey ships and survey
  motherships explored and prepared worlds for colonization; the motherships
  carry autoforges and are "in essence a von Neumann probe" (`derelict_probe`,
  `derelict_mothership`). Derelict probes, survey ships, motherships and
  cryosleepers spawn with `derelict` defender fleets
  (`campaign/procgen/salvage_entity_gen_data.csv`).
- **Its doctrine is a machine's.** `derelict.faction`: AI-core officers,
  blank ranks and posts, no music, `fightToTheLast` and
  `engageWhenEvenStrength`, a broad but dated ballistic and missile arsenal,
  and only makeshift shields.
- **Automated defences outlive their orders.** The red planet's distress
  beacon (`campaign/rules.csv`, `psi_beatDefenders`) tells of a team stranded
  at a secret Tri-Tachyon research facility because its fully automated
  defences over-weighted executive security contingency orders after one of
  the AI Wars, and now shoot down any craft that tries to leave.
- **Sub-gamma control is a real tier.** A gamma core is "the lowest tier core
  considered to be truly intelligent under Domain-era AI protocols"
  (`gamma_core`); the Paladin's targeting is an "embedded autonomous sub-gamma
  targeting core and IFF system" (`guardian` weapon row).
- **Ruins are where the Domain's project died.** The ruins conditions run from
  scattered broken domes to vast ruins where "the dead are in dread abundance"
  (`campaign/market_conditions.csv`). Vanilla's ruins salvage can yield AI
  cores at every grade (`ai_cores3` in every ruins spec), but a ruins site has
  no defenders in vanilla.
- **Remnants are something else.** Remnant hulls are "a remnant of the
  autonomous AI fleets developed by the Tri-Tachyon Corporation", purged in the
  First AI War "so the official histories say"; their battlestations were
  depot, factory, command nexus and weapon platform (`remnant_station2`).

## Strong ground-war inferences

- A Domain colony on the frontier could have had automated site security on
  the ground for the same reason its convoys had Defenders: cheap protection
  where the law did not look. A corporate black site would have had it for the
  reason the red planet shows.
- Such security was built to guard places, not to win wars: perimeters,
  checkpoints, vaults, magazines, power, landing fields.
- After the Collapse nobody revoked its orders. What still runs is whatever its
  power, its repairs and its last orders have kept running, for centuries.
- It is old. Its arsenal is the `derelict` list: ballistic, missile and some
  early energy weapons, not Remnant hardware.
- It is not an intelligence. Sub-gamma control can follow orders, track and
  shoot well; it does not plan, negotiate or adapt its aims.

## Marine Operations direction

Everything in this section is mod direction.

### What it is

- **Site security, not an army.** Each ruins site that has it has its own
  network, built for that place and running alone. Networks do not talk to
  each other, do not spread and do not rebuild beyond the repair stock they
  have. The one exception is a **fallen hull**: an Explorarium survey
  mothership down on a world, whose autoforge prints stakes and walkers from
  the metal it hauls home (`fallen-hull-nouns.md`, pick `hull-spread-law`).
- **Two lineages.** Most networks are **Domain security**: Mbaye-Gogol ground
  units, the frontier garrison's kit, in grey and hazard yellow with Explorarium
  and colonial-authority markings. In a system the game generated as Remnant
  ground (`theme_remnant`), a network is **contingency security**: a
  Tri-Tachyon site whose automated defences are still carrying out an
  executive contingency order, in Tri-Tachyon's white and cyan, gone dull. The
  lineage changes the look and the weapon mix, never the doctrine.
- **Sub-gamma.** A network is run by a **control node** with sub-gamma control:
  a rule-following controller, not a mind. That is the line between it and the
  Remnants, which carry real cores and real intent.
- **Standing orders.** A network does what its last orders say about its
  ground: keep it, deny it, and on a breach of its vault, run its
  **contingency** (lock down, call everything in). It never pursues past its
  perimeter, never takes ground it was not built to hold, and never stops.

### What it is not

- **Not the Remnants.** No nexus, no fleet, no ordo, no alpha-core personality,
  no reach beyond the site. A Remnant ground force belongs to
  `remnants-lore-guide.md` and is not a ruins occupant.
- **Not the Rav.** It does not grow, eat or hunt. Its swarm is a flock of
  identical machines flying as one body, and its numbers are fixed.
- **Not a robot army in human shapes.** No humanoid infantry. The smallest
  armed unit is a swarm drone, and every unit has a job a
  security planner would buy it for.
- **Not loot on legs.** Destroying units does not drop AI cores. A core in the
  ruins is in the vault, and the vault's haul is vanilla's.

### The force: flocks and heavies

Owner direction (2026-11-09): derelict security is **drone swarms with flocking
behaviour, and heavy mechs**. The swarm is the faction's signature, the heavy
walkers its hard core, and the two fight as one force: the flocks find, pin and
screen; the heavies arrive and break what the flocks have pinned.

The canon gives both halves. The Domain sent drones "to tip the terrible balance
of attrition" against rebel worlds, the Picket closes to unleash "a shockingly
dense volume of light-caliber fire", and the Berserker makes "little
consideration for its own survival": cheap, expendable machines in numbers. The
Rampart is "little more than an artillery platform", built so that wrecks repair
the lightly damaged: a slow, heavy anchor that keeps itself running.

### Units

| Unit | What it is | Built from |
| --- | --- | --- |
| **Swarm drone** | A small, cheap, expendable flier with one light gun. Alone it is nothing; it exists in a flock. | The shipped drone's air body, lighter (`Drone`), steered by the flock (`air-nouns.md`, Flocks) |
| **Flock** | Twelve to sixteen swarm drones that move, turn and attack as one body. The unit the network commands. | New: the flock model (`air-nouns.md`, Flocks) |
| **Drone cradle** | A launch housing that keeps one flock docked, puts it up when its zone wakes, takes it back, and slowly rebuilds lost drones while its node stands. | The drone hub (`DroneHub`) with a flock's stock |
| **Security walker** | An unpiloted heavy walker of the derelict line, in three loadouts: the **line frame** (heavy cannon and plate, holds a layer and crushes cover), the **siege frame** (mortar and missile racks, fires on what the flocks see), and the **vault frame** (the heaviest plate, the vault's last guard, an interior guardian that never leaves it). Sized to stand in an interior's corridors. | A new heavy mech line (`mech-lines.md`): one chassis, variants by loadout (`mechs-nouns.md`) |
| **Sentry post** | A folded gun emplacement that rises when its zone wakes. Secondary now: a few per zone at doors and courts. | Defence post (`DefensePost`) with a wake gate |
| **Rounds walker** | The derelict line's medium (MG-25): low, quick and lightly plated, narrow enough for any door. Walks its zone's round while the zone sleeps, and once awake is the zone's first answer anywhere inside its perimeter (`derelict-watch.md`, pick open). | A chassis of the derelict line at its size |
| **Hall frame** | The derelict line's large (MG-70): twice the heavy's height and the heaviest plate outside the vault, too wide for a corridor. Keeps the plant's largest hall and forces a dead door in its way (`derelict-watch.md`, pick open). | A chassis of the derelict line at its size |
| **Tender** | The derelict line's small (MG-12): unarmed, a welding arm in place of guns. Follows its zone's most worn walker once awake and welds plate back onto it, under fire or not; reloads its stock at the service bay; never raises a wreck (`derelict-watch.md`). | A chassis of the derelict line at its size |
| **Repair drone** | An unarmed drone that restores damaged posts, cradles and walkers while its node stands and its stock lasts, and raises a fallen walker or post from its wreck (`derelict-watch.md`); stands down under fire. | The tender's builder drone (`tender-ship.md`) |
| **Service bay** | A low armoured housing beside a node holding the zone's repair stock; the repair drone and the tender reload there, and the zone's walkers re-arm their missile racks there, since they carry no loader of their own (`derelict-watch.md`). | Structure |
| **Reserve bay** | A sealed cradle housing holding a second flock, opened by the facility's wake (`derelict-watch.md`). Destroyed while shut, its flock never rises. | A cradle, sealed |
| **Drone vent** | A hatch in open-sky paving into the plant's service ducts: flocks go in at one and come up at another (`derelict-watch.md`). A charge collapses it. | Ground |
| **Control node** | The zone's controller and relay: armoured, unarmed. Destroyed, its zone stops relaying, repairing and rebuilding drones. | Structure |

**The heavies are a line of their own.** The derelict walker is one new heavy
chassis with its own art, sound and doctrine, not a livery of the Bulwark
(`mech-lines.md`, law 1: a line is never a recolour). It stands outside the
player's tech-by-weight grid as an automated line: no cockpit, no pilot, and
never fielded by the company, since the Sector's law keeps armed machines under
crewed control. Its variants are loadouts on the one chassis (`mech-lines.md`,
law 2), and its Domain and contingency looks are liveries of it. It is never
lifted; it was built where it stands. Ground units take Mbaye-Gogol
designations (*MG-series line frame*), never vanilla's ship class names.

**Strength by grade** (starting points for the stories to tune):

| Grade | Flocks | Heavies | Sentry posts |
| --- | --- | --- | --- |
| Scattered | 1 | 0–1 | a few |
| Widespread | 2 | 1–2 | a few per zone |
| Extensive | 3 | 2–3, one siege frame | a few per zone |
| Vast | 4 | 4–5, one vault frame | a few per zone |

### The flock

- **One drone.** Light: a burst from a rifle drops it. It flies low, below the
  rooflines, carrying one light gun with a short reach. It crosses low cover
  (rubble, sandbags, fences) but not walls or roofs, and it never enters a
  roofed interior: it goes round, through open doors and breaches, over courts.
- **Flocking.** Each drone steers by three local rules: keep apart from its
  neighbours (**separation**, the shipped `DroneSeparation`), fly the way they
  fly (**alignment**), and keep with them (**cohesion**), all drawn after the
  flock's **anchor**, the point the network moves. A wall in the way splits the
  flock: each drone takes the side its own route favours, and cohesion re-forms
  it behind. A flock therefore pours down streets, divides round a building and
  closes again on the far side.
- **Attack.** A flock does not trade fire from cover. It closes on a target,
  wraps round it and strafes in passes, each drone firing when its nose comes
  on; the volume, not any one gun, is the threat. Under fire it spreads, never
  stops.
- **Loss.** Drones die singly and fall where they are hit. A flock below a third
  of its strength goes home to its cradle to re-form; a cradle without a node
  rebuilds nothing.
- **Command.** The network commands flocks, never drones: it gives a flock a
  task (screen, probe, strike, pursue, return) and an anchor route, and the
  members fly it themselves.
- **How the company fights it.** Area fire thins a flock fastest: squad
  automatics, shotguns, grenades and anything that bursts. Shooters indoors
  are out of its reach, since a flock never enters a roofed room; a door shut
  behind them (a sally door) closes the way. Smoke breaks its sight and so the
  siege frames' spotting. Point-defence and flak engage it as any flier. And a
  dead node stops the cradle rebuilding.

### The flock and the Rav

The two must never read as the same thing.

| | Rav (the swarm) | Derelict flocks |
| --- | --- | --- |
| Body | ground animals, by strain | identical fliers |
| Moves as | individuals on a shared prey field, each hunting what it senses | one body, steered by flocking to an anchor the network moves |
| Numbers | grow: bores feed and spawn while the company stays | fixed: each cradle's flock, rebuilt slowly while its node lives |
| Attack | melee and spit, piling on | ranged strafing passes, wrapping round |
| Shape | spreads, crowds into gaps, floods doors | streams, splits round walls and re-forms, keeps out of rooms |
| Answer | gates, choke and kill, starve it | area fire, fight from indoors, smoke, cut the node |

### Weapons and equipment

Domain security keeps the `derelict` arsenal at ground scale: the flocks' light
machine guns, the line frame's heavy cannon and autocannon, the siege frame's
mortar and missile racks, flak and point-defence lasers against the company's
aircraft and missiles. Contingency security leans to pulse and beam lasers and
guided missiles. Nothing it carries is usable by a Marine: a wreck is scrap,
and the field finds stay the ordinary bundles (`loot-nouns.md`).

### Tactics and command

- **Zones.** A network is a set of **security zones**, each a node with the
  cradles, walkers, posts and repair drones it controls, linked to its
  neighbours by relays.
- **Dormant until woken.** A zone starts **dormant**: flocks docked, walkers
  powered down, posts folded. It wakes on a sensor contact, gunfire inside its
  perimeter or damage, and relays the alarm to its linked neighbours, which go
  alert where they stand. With no contact for a while an awake zone settles back
  to alert, never to dormant.
- **The flocks are the alarm and the screen.** A woken zone's flock goes up
  first. It finds the company, relays what it sees to the zone and its
  neighbours, and pins the company down. Flocks have a **leash**: they may leave
  their zone's perimeter to probe and to screen, a bounded way, and come back.
- **The heavies are the anchor.** Walkers hold their zone's layer, its chokes
  and its courts, and never leave their perimeter. They come to what the flocks
  have pinned inside it. Siege frames fire on what the flocks see, so a blinded
  or dead flock leaves them firing on old contacts.
- **Rounds, ducts and the wake** (`derelict-watch.md`, picks open). A sleeping
  zone is not still: its rounds walker walks a round of its places, and its
  flocks use the plant's service ducts between drone vents. Over a delve the
  facility wakes in stages with the site's stir, bringing up what it always had
  and had shut down: the ducts, then the reserve bays' flocks, then the
  security bulkheads and an alert across the site. Nothing is built or grown.
- **The contingency.** Breaching the vault wakes every zone. Every flock that
  can reach the vault or the route out goes for the haul's escort; the line
  frames walk to the egress route through their own ground and hold it, and the
  vault frame stays on the vault. The flocks arrive first and pin; the heavies
  close the way.
- **Inside and outside.** Where a site is a megastructure or bunker (owner,
  2026-11-09), the roofline divides the watch: flocks keep the exterior, the
  approaches and the doorways and never enter a roofed room; walkers and posts
  hold the halls, corridors and the vault inside. The walker is built to fit
  the corridors it guards.
- **Honest knowledge.** A zone knows what its sensors, its flocks and its
  relays tell it. A dead node isolates its zone; jamming, smoke and walls work
  on its sensors as on anyone's. The network is a mission director like the
  Rav's swarm director, not a mirrored squad commander.
- **No morale, no immunity.** Machines do not break or retreat
  (`fightToTheLast`); they take damage by the ordinary rules of their chassis
  and armour.

### How it reads against the other occupants

| | Scavengers (pirates) | Rav | Derelict security |
| --- | --- | --- | --- |
| Wants | the same haul the company wants | food, and to grow | its ground kept, for orders nobody remembers |
| Starts | awake, working the ruins | asleep below, stirred by noise | dormant, woken by its sensors |
| Pressure over time | early and steady; breaks if its captain or its stash is lost | rises the longer the company stays and the louder it is | flat until a zone wakes; peaks at the contingency |
| Pursuit | to its camp and the vault, then lets go | to the lift | flocks on a leash, heavies never past their perimeter, except in the contingency |
| Rewards | speed and violence | speed and quiet | area fire, cover discipline, anti-armour, cutting nodes |
| Leaves behind | its stash (an extra field cache) | nothing | nothing usable; scrap |

### Facilities and sites

Good ground: colony security stations with folded post housings in the
paving, checkpoint gates, a sealed archive or bank vault, a buried magazine, a
power hall feeding the network, a landing field the colony's security never
stopped guarding, a corporate black-site annex behind a second wall. The vault
is where the network's oldest orders point. Geometry is human, built for people
who are gone, with maintenance hatches the drones use and courts wide enough for the
flocks to wheel in.

### Voice and names

- Machines do not speak. The network talks only through what it does, its
  warning tones and, rarely, a looped recorded announcement in Domain colonial
  standard ("This facility is closed by order of the colonial authority").
- The briefing and the company speak of it in spacer terms. A ruins site whose
  security still runs is a **live ruin**; its machines are **the watch**
  ("the watch is up in zone two"). **Derelict** is the formal word, as on the
  game's faction. These names are mod direction and the owner's to change.
- Speculation is allowed and never settled: why a site's orders read as they
  do, whether a contingency site's people were evacuated or killed by it, what
  a vault really held.

## Anti-caricature rules

- Do not make the watch an intelligence with a plan or a grudge; it follows
  orders that no longer make sense.
- Do not let it cheat. It knows what its sensors and relays tell it.
- Do not give it the Remnants' tech, scale or reach, and do not imply the
  Remnants are behind every live ruin.
- Do not make every derelict site a death trap. Most networks are partial,
  decayed and short of repair stock; a vast site's network is the dangerous one.
- Do not make destroying it the point. The point is the vault; the watch is
  what stands between.

## Source anchors

- `strings/descriptions.csv`: the Domain drone hulls and their shared frontier
  line; `derelict_probe`, `derelict_mothership`,
  `station_derelict_survey_mothership`; the AI core rows; the Remnant hulls and
  `remnant_station2`.
- `world/factions/derelict.faction`: name, Explorarium logo, AI officers, blank
  ranks, weapon list, `fightToTheLast`.
- `campaign/procgen/salvage_entity_gen_data.csv`: derelict defenders on derelict
  entities; no defenders on the ruins specs; AI cores in the ruins drops.
- `campaign/market_conditions.csv`: the four ruins conditions.
- `campaign/rules.csv`: `psi_beatDefenders` (automated defences enforcing a
  Tri-Tachyon contingency order).
- `com.fs.starfarer.api.impl.campaign.ids.Tags`: `theme_derelict*` and
  `theme_remnant*` system themes, fixed at sector generation.
