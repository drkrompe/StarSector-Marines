# Fallen hull — Nouns

Status: DRAFT — the model for a derelict capital ship down on a world and the
watch it pours out; nothing built. Its first instance is the authored arc
`long-survey-arc.md`, played on owned seeds before any campaign phenomenon.

Written: 2026-11-10

Read `derelict-lore-guide.md` in full first, then `derelict-watch.md` and
`derelict-security-network.md` (What is built): this doc reuses their
vocabulary (zone, node, flock, cradle, rounds walker, line, siege and vault
frames, hall frame, tender, repair drone, service bay, reserve bay, vent, wreck,
wake, contingency) and never restates it. Read `colony-breach-nouns.md`
(Campaign: blooms) for the Rav's spread, the pattern this one must stay apart
from.

## The fantasy

The owner (2026-11-10): "a derelict capital ship crash land mission set where
the derelict security faction pours out as a problem the player can help to
face", with a mechanism by which it can propagate "similar to bore holes", and
"large scale ground combat with walkers and swarmers vs the factional
defenders' forces".

A centuries-dead Explorarium survey mothership has come down on a settled
world. It was built to prepare worlds for colonists, and on the ground it does
what it was built for: it surveys the country round it, claims it on a grid,
clears what stands on its claim and secures it. Nobody is coming. The colony on
the claim is an obstruction. The world's own military holds the line while the
hull's autoforge prints walkers and **survey stakes** and carries them out, a
lattice point at a time, feeding on the metal it strips from everything it
claims. The company is hired to help: get people out, kill stakes before they
rise, hold the line beside the garrison, and at the end go into the hull and
put its forge out.

## Origin (canon and mod choice)

- **Canon.** Explorarium survey motherships explored and prepared worlds for
  colonization; they carry autoforges and are "in essence a von Neumann probe"
  (`derelict_mothership`, `derelict-lore-guide.md`, Canon anchors). They are
  found derelict in the Sector with `derelict` defenders.
- **Mod choice.** One fell (`long-survey-arc.md`, Why it fell). Its survey
  program is intact and its sub-gamma control runs it to the letter: survey,
  claim, clear, secure, build a landing. Everything below is mod direction.
- **The exception to "networks do not spread".** A site network keeps the
  numbers it was built with (`derelict-lore-guide.md`, What it is). A fallen
  hull is not a site network: its autoforge is the one derelict thing that
  makes more, and only from feedstock it hauls home where anyone can see it.
  Every stake it plants becomes an ordinary site zone the moment its link to
  the hull is cut. (Pick `hull-spread-law`.)

## The hull and the Rav

The two must never read as the same thing (`derelict-lore-guide.md`, The flock
and the Rav):

| | Rav (a bloom) | A fallen hull |
| --- | --- | --- |
| Origin | a weapon under the ground, woken by a dense colony | one machine come down on top of it, by accident |
| Grows by | eating people; a fed bore buds another wherever it is | hauling metal home; the forge prints stakes and walkers in a fixed order |
| Spreads | organically, toward food | on a **claim lattice**: regular points outward from the hull, visible and predictable |
| A new outpost | a bore erupts | a stake is carried out by a **stake train**, planted, and rises in stages over minutes |
| Cut it off | collapse the bores; starve it by lifting the colony out | cut the **relay chain**: a stranded stake keeps what it has and builds nothing more |
| Ended by | the mother bore collapsed | the **autoforge** inside the hull put out |
| Wants | food | the claim cleared for colonists who never come |

## Vocabulary

| Noun | What it is |
| --- | --- |
| **Fallen hull** ("the hull"; spacers: "the Surveyor") | The crashed mothership: an impact trench, a debris field, and the hull itself as a roofed megastructure the company can enter (`ruins-expedition.md`, interiors). It is the network's root zone and holds the autoforge. |
| **Autoforge** | The hull's fabricator: the network's heart, the vault of the hull's interior. It prints the **build queue** while it has feedstock. Put out (destroyed or its power cut), nothing more is printed anywhere, and every stake is stranded. |
| **Build queue** | The fixed order the forge prints in, from the survey program: stakes and walkers at their costs, never chosen by what the company does. Readable: the briefing and the rail can say what comes next. |
| **Forge cycle** | The forge's clock: each cycle, with feedstock for the next item in the queue, it prints it. Cue: the forge's hammer and the hull's stacks venting. |
| **Feedstock** | Metal: the forge's one input, held as a store at the hull and at each established stake. Never people. |
| **Stripper** | A derelict-line walker with cutting arms (unarmed or lightly armed) that cuts up structures, fences, vehicles and wrecks on the claim, the company's and the garrison's included, into feedstock. |
| **Hauler** | A load-carrying walker or crawler that takes stripped metal from strippers to the nearest store along a **haul road**. Destroyed loaded, its load is lost. |
| **Claim lattice** | Points on a regular grid round the hull, spaced a zone's perimeter apart, laid by the ground, never moved by play. The forge plants the nearest open point that links back to the hull. |
| **Survey stake** ("stake") | A prefabricated outpost the forge prints, carried out by a stake train and planted on a lattice point. It rises in stages (below) into an ordinary security zone with its own node. |
| **Stake train** | The escort that carries a printed stake out: its walkers round a hauler with the folded stake. Kill the hauler before it plants and the stake is scrap. |
| **Relay chain** | The links from each stake back to the hull through the stakes between. A stake builds (rises a stage, takes feedstock, receives printed walkers) only while its chain holds. |
| **Stranded** | A stake whose chain is cut, by its node, a node on its chain, or the hull's autoforge going out. It keeps every unit and stage it has and plays by the site-network rules from then on: fixed numbers, repairs from its own bay. |

### A stake's stages

| Stage | What stands | What it does |
| --- | --- | --- |
| **Planted** | A folded pylon, armoured, unarmed | Nothing yet; counts down to raising while its chain holds |
| **Raised** | The pylon opens into a node and a cradle: a new zone, dormant | Its flock launches as its zone wakes; joins the network's relays |
| **Established** | Folded posts, a service bay and a feedstock store | Strippers and haulers work from it; printed walkers are posted to it; the next lattice point beyond it becomes plantable |

A stake killed planted is scrap and leaves no wreck. A raised or established
stake is fought as a zone: kill its node and it is stranded and blind.

## Laws

- **Every new unit is printed.** Nothing appears from nowhere. A walker or stake
  that was not on the map at the start came out of the hull's forge, cost
  feedstock, and walked out where it could be seen. (`derelict-watch.md`'s "no
  growth" law holds for every stranded stake and every site network.)
- **Metal, never people.** The hull strips structures and wrecks. It kills
  people who are on its claim by the watch's ordinary rules; it does not take
  them, eat them or gain anything from them.
- **The lattice is fixed.** Where a stake can go is laid by the ground before
  the battle and shown once seen. The forge's queue is fixed. The company can
  read the spread and plan against it; the counterplay is denial, not guessing.
- **Honest knowledge.** The hull knows what its zones' sensors, flocks and relays
  tell it (`derelict-lore-guide.md`, Honest knowledge); a stranded stake knows
  only its own.
- **The claim is the perimeter.** The hull's walkers pursue to the claim's edge
  (the outermost established stake's perimeter), not past it, except in the
  contingency the autoforge's breach calls.
- **Allies are on the claim too.** The world's garrison fights the watch under
  its own authority (`bloom-mission-arc.md`, Law: a living field); its wrecks
  are feedstock like the company's.

## In a battle

- **Setup.** The ground lays the hull (trench, debris, interior), the claim
  lattice, the haul roads and the stakes that already stand at the chapter's
  point in the arc, each at its stage (`long-survey-arc.md`, chapters).
- **The forge clock.** Each forge cycle with feedstock for it prints the next
  queue item. A stake goes out by train to the nearest open, linked lattice
  point; a walker walks to the stake or zone the queue names.
- **Feedstock flow.** Strippers work what stands on the claim, haulers carry it
  to the nearest store, and a store's metal reaches the forge along the chain
  (abstracted: a linked store counts as the forge's).
- **The company's levers.** Kill haulers loaded; kill a stake planted, before
  it rises; cut a chain at its weakest node to strand everything beyond;
  scrap wrecks before strippers take them (`derelict-watch.md`, Denial); in the
  last chapter, put the autoforge out.

## Campaign (later, not work)

A fallen hull is meant to become a sector phenomenon like a bloom, once the
story missions have found the feel (owner, 2026-11-10: story missions on a
controlled seed first): a derelict mothership's orbit decays over a settled
world, the hull's claim grows by stakes week to week as a market condition, and
the world's owner calls for help. Its stages, its clock and its interaction with
the event gate are not designed yet.

## Open picks

- `hull-spread-law`: (a, recommended) the hull alone spreads, through its
  autoforge and only from feedstock; site networks keep their fixed numbers.
  (b) No spread: the hull pours out what it carried and stops.
- `hull-lineage`: (a, recommended) an Explorarium survey mothership (canon
  autoforge). (b) a Domain-era drone carrier (no forge in canon; spread would
  need inventing). (c) a Tri-Tachyon contingency hull (contingency security's
  look).
- `stripper-hauler`: (a, recommended) two units, the stripper and the hauler,
  of the derelict line. (b) one unit that cuts and carries.

## Boundaries

- Units, their sizes and their doctrine stay in the derelict domain
  (`derelict-lore-guide.md`, `derelict-watch.md`); new units here are chassis of
  the derelict line (`mech-lines.md`, law 1).
- The arc, its world, cast, chapters and seeds: `long-survey-arc.md`.
- The ground (hull, trench, lattice, haul roads): mapgen, through the owned
  story maps (`story-map-pools.md`).
