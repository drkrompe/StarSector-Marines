# Derelict security lore guide

Status: DRAFT — implementation reference for derelict security, the automated
occupant of a ruins expedition (`ruins-expedition.md`). Nothing is built.

Written: 2026-11-09

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
  have.
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
- **Not the Rav.** It does not grow, eat or come in waves. It holds.
- **Not a robot army in human shapes.** No humanoid infantry. The smallest
  armed unit is a drone or a folded sentry post, and every unit has a job a
  security planner would buy it for.
- **Not loot on legs.** Destroying units does not drop AI cores. A core in the
  ruins is in the vault, and the vault's haul is vanilla's.

### Units

Each unit uses an existing mechanical role (`faction-lore-nouns.md`,
Implementation lookup): the derelict roster adds looks, weapon mixes and a
doctrine, not a new kind of actor, except where a story says so.

| Unit | Role it uses | What it is |
| --- | --- | --- |
| **Sentry post** | Defence post, light to large (`DefensePost`) | A folded gun emplacement that rises from its housing when its zone wakes. Light posts carry machine guns and light autocannon, medium posts heavier autocannon and mortars, large posts missile racks or a heavy cannon. |
| **Drone cradle** | Drone hub (`DroneHub`) | A launch housing that keeps a small flight of security drones docked and puts them up when its zone wakes. Its drones are the network's eyes and its fast response. |
| **Security walker** | Mech (`mechs-nouns.md`) | An unpiloted Mbaye-Gogol walker: a light patrol frame in pairs, and at the larger grades a heavy frame built for vault guard. Its chassis, plates and weapons are ordinary mech parts; it has no cockpit and no pilot to kill. |
| **Repair drone** | Builder drone (`tender-ship.md`), working a damaged post instead of a new one | An unarmed drone docked at the node that flies out to restore damaged posts and cradles while its node stands and its repair stock lasts, and stands down under fire as a builder does. Killing it, or the node, stops the network healing. |
| **Control node** | Structure | The zone's controller and relay: armoured, unarmed, the thing a security planner protects. Destroyed, its zone stops relaying and repairing. |

The walker is the only new chassis. A Domain-lineage walker and a
contingency-lineage walker are liveries of the same chassis, not two variants.
Ground units do not reuse vanilla's ship class names (the Warden is already the
mod's own mech); they take Mbaye-Gogol designations, for example *MG-series
patrol frame*.

### Weapons and equipment

Domain security prefers the `derelict` arsenal at ground scale: machine guns,
autocannon, mortars, light missile racks, flak against aircraft, and the odd
early laser in a point-defence role. Contingency security leans to Tri-Tachyon's
pulse and beam lasers and guided missiles. Neither carries anything a Marine
can pick up and use: a wrecked post is scrap, and the company's field finds stay
the ordinary bundles (`loot-nouns.md`).

### Tactics and command

- **Zones.** A network is a set of **security zones**, each a node with the
  posts, cradles, walkers and repair drones it controls, linked to its neighbours by
  relays. A zone is the network's unit of attention.
- **Dormant until woken.** A zone starts **dormant**: posts folded, drones
  docked, walkers powered down. It goes **alert** when one of its sensors sees
  a company unit, when it hears gunfire inside its perimeter, or when any of
  its units is damaged; an alert zone is **awake** at once. An awake zone
  relays the alarm to its linked neighbours, which go alert but stay where they
  are. With no contact for a while an awake zone settles back to alert, never
  to dormant.
- **It holds.** An awake zone's walkers and drones fight inside their perimeter
  and come back to it. They do not pursue a company that has left their
  ground. That is the counterplay: scout, find the quiet way through, cut a
  relay or a node, and the network can be bypassed, isolated or put to sleep
  zone by zone.
- **The contingency.** Breaching the vault runs the contingency: every zone
  wakes, and every mobile unit that can still reach the vault's zone, or the
  route out of it, is called in. This is the only time the network leaves its
  perimeters, and it is why the way out is the hardest fight.
- **Honest knowledge.** A zone knows what its own sensors see and what linked
  zones relay. A cut relay or a dead node isolates a zone; jamming, smoke and
  walls work on its sensors as on anyone's. The network is a mission director,
  like the swarm's (`extraction-command.md`), not a mirrored squad commander;
  it reads only what its sensors and relays legally give it.
- **No morale, no immunity.** Machines do not break, surrender or retreat
  (`fightToTheLast`), but they take damage by the ordinary rules of their
  chassis and armour (`faction-lore-nouns.md`, Faction flavor is not a hidden
  bonus).

### How it reads against the other occupants

| | Scavengers (pirates) | Rav | Derelict security |
| --- | --- | --- | --- |
| Wants | the same haul the company wants | food, and to grow | its ground kept, for orders nobody remembers |
| Starts | awake, working the ruins | asleep below, stirred by noise | dormant, woken by its sensors |
| Pressure over time | early and steady; breaks if its captain or its stash is lost | rises the longer the company stays and the louder it is | flat until a zone wakes; peaks at the contingency |
| Pursuit | to its camp and the vault, then lets go | to the lift | never past its perimeter, except in the contingency |
| Rewards | speed and violence | speed and quiet | scouting, routes and cutting nodes |
| Leaves behind | its stash (an extra field cache) | nothing | nothing usable; scrap |

### Facilities and sites

Good ground: colony security stations with folded post housings in the
paving, checkpoint gates, a sealed archive or bank vault, a buried magazine, a
power hall feeding the network, a landing field the colony's security never
stopped guarding, a corporate black-site annex behind a second wall. The vault
is where the network's oldest orders point. Geometry is human, built for people
who are gone, with maintenance hatches the drones use.

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
