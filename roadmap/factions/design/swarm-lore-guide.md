# Swarm lore guide

Status: ACTIVE — implementation reference for the Colony Breach swarm: what it is,
what people call it, and how briefings, radio and item text speak about it.

Written: 2026-10-21

Read `faction-lore-nouns.md` for evidence rules and shared setting laws, and
`colony-breach-nouns.md` for the mechanics this guide gives a voice to (bores,
strains, harvest, surges, the mother bore). The noun doc's *Origin* section calls
the swarm's backstory a **mod choice**; in this guide's tiers that is **mod
direction**, and nearly everything below is in that tier.

Unlike the faction guides, this one is not a translation of a vanilla identity.
Starsector 0.98a has no such organism. The guide's job is to keep an invented
thing inside the canon's edges: every claim is either anchored to installed game
text, a careful inference from it, or marked as ours.

## Canon anchors

What the installed game actually says that the swarm leans on. None of it
mentions the swarm.

- **The Domain made weapons that kill worlds, and restricted them.** The
  Planetkiller is "a specialized warhead capable of sundering a world" in the
  *Domain Restricted* item category (`campaign/special_items.csv`). World denial
  was within the Domain's means and vocabulary.
- **Bioweapons research existed, and its records did not survive.** A salvageable
  research station can hold a sealed bioweapons laboratory whose computer core
  destroyed itself on a dead man's switch, so nothing of what was made there is
  recoverable (`campaign/rules.csv`, `salSRBW*`). Canon leaves the products of
  that work unknown.
- **The Sector has real, dangerous xenolife.** Inimical Biosphere worlds are deadly
  to unprotected humans and work under quarantine protocols
  (`market_conditions.csv`); Jangala's toxic, "ferociously regenerative" xenolife
  is a Luddic Path pilgrimage hazard requiring masks and antixenols. Ordinary
  people know the difference between a world's native life and a crop blight,
  and also know the line can blur.
- **The era names.** The Collapse ended the Domain's reach; the First and Second AI
  Wars followed in the Sector, the second concluded by the cycle 194 Treaty of
  Crom Cruach. Old-war remnants still wake and kill: Remnant stations are "a relic
  from the First AI War ... fully armed and operational", and Tri-Tachyon left
  booby-trapped hulls in the Second (`descriptions.csv`, `rules.csv`).
- **Tri-Tachyon buys strangeness.** The Company "pays very well indeed for unique
  specimens ... Domain tech, xenobiology" (`rules.csv`, shrouded substrate
  dialogue).
- **Luddic vocabulary for abomination is Moloch**, aimed at AI and forbidden
  machine work ("work of Moloch", "servant of Moloch").
- **Collapsed colonies are a market state.** *Decivilized* and *Decivilized
  Subpopulation* describe worlds where civilization fell and an unknown
  population of survivors remains.

## Strong inferences

Conservative readings of the anchors above, true whether or not the swarm exists.

- A late-Domain or AI-War power that could build a Planetkiller and run sealed
  bioweapons labs could plausibly have built a biological denial weapon. Canon
  neither confirms nor rules it out.
- Old weapons outliving their makers is a standing Sector pattern (Remnants,
  derelict defences, EW traps). A dormant weapon that wakes is in keeping with
  the setting's tone, not a departure from it.
- Records of such a programme would be scarce, contradictory or deliberately
  destroyed. Nobody in the Sector has an authoritative answer about it.
- Colonies on marginal worlds have weak defences and slow relief. When something
  goes wrong underground, mercenaries are a believable response.
- Factions would each see what their interests let them see: a threat to order,
  a specimen worth money, a judgement, or somebody else's crime.

## Marine Operations direction

### What it is

A **self-perpetuating biological denial weapon** from the late Domain or the AI
Wars, made to make a world unusable and to keep doing that after whoever released
it was gone. It does not need its makers, orders, or a supply chain. It is a life
cycle someone designed: stages that produce more of themselves out of whatever is
living above them.

### What it is not

- **Not a species, a faction or a society.** It has no language, diplomacy,
  culture, leaders or goals beyond the designed cycle. Nobody negotiates with it,
  and it does not hate anybody.
- **Not intelligent.** The swarm director is a process. Strains respond to what
  they sense. Coordination in the cycle comes from its design, not from a mind.
  A matriarch is a heavy stage, not a queen with plans.
- **Not native xenolife**, though colonists may mistake it for that at first and
  some people insist on it (see *Names*).
- **Not AI.** Do not connect it to Remnants, AI cores, the Threat or the Abyss. A
  character may draw the comparison as rhetoric; the text never confirms a link.
- **Not a Domain secret that can be solved.** No vault, cure or kill code ends it
  everywhere. That keeps outbreaks a recurring problem rather than a quest chain.

### The life cycle, as bores and strains express it

Each stage below maps to a mechanic owned by `colony-breach-nouns.md`. The lore
explains the mechanic; it never adds behaviour the mechanic does not have.

1. **Dormancy.** The weapon waits underground in reservoirs: old seeding sites,
   aquifers, deep rock, mine workings. Dormant, it is invisible to ordinary
   survey. Colonists build on top of it without knowing.
2. **Waking.** It wakes when a colony above becomes dense enough to be worth
   eating: heat, vibration, waste, bodies. Early signs are deniable: dead
   livestock, lost miners, ground tremors, a sealed shaft that will not stay
   sealed. In play: the opening's thin **perimeter seepage**.
3. **Eruption.** It breaks surface through **bores**, inside the colony rather
   than from the wilderness. The **tremor** beat is the ground giving way.
4. **Harvest.** The weapon's purpose is to convert a colony into more of itself.
   Runners take the living and drag them down a bore. People are biomass; that is
   the horror and the economics in one sentence. Every survivor lifted out is
   food the bore never gets.
5. **Growth.** Fed bores grow: **fissure → bore → maw**. Each stage puts out
   heavier strains: runners for volume, brutes to break open the places people
   hide, spitters to deny the open ground and the low sky.
6. **Surges.** A bore spawns in pulses, a river of bodies and then quiet. Lore
   reading: each pulse is a brood the bore has finished making. The quiet is real
   and is the moment to push.
7. **Spore umbrella.** Bores defend the sky over themselves. Read it as part of
   the denial design: the weapon was built expecting aircraft.
8. **The mother bore.** One bore in a cluster is the deep one, the root of the
   reservoir reaching the surface. It erupts late, already grown, and is the only
   stage that can raise a **matriarch**. Collapse it and the cluster stops.

### Why it recurs, and what ending an outbreak means

- **It was seeded widely.** Whoever deployed it seeded many worlds and many sites
  per world; the records of where are lost. Outbreaks therefore surface across
  the Persean Sector, at no predictable world, whenever a colony grows over a
  reservoir. Where and how often belongs to `sector-outbreaks.md`; the lore only
  requires that it can happen anywhere a colony has grown, and that nobody holds
  the map.
- **Collapsing the mother bore ends an outbreak, not the weapon.** The surface
  goes quiet. The reservoir below is damaged, dormant again, or simply quiet for
  now; nobody can prove which. A cleared colony may be resettled, abandoned or
  quarantined. Text says "the outbreak is over", "the bores are down", never
  "the swarm is destroyed" or "the world is clean".
- **The win is people.** Lifting the survivors is the mission. Collapsing bores
  is a bonus and a mercy for whoever comes next, not victory over the thing.
- Rumoured vectors between worlds (dormant material in ore, salvage or ballast;
  a reservoir cut open by a new mine) are allowed as **in-world speculation** for
  radio and bar talk. None of them is a mechanic until `sector-outbreaks.md` makes
  it one.

### Names: what people call it

Each name belongs to who says it. Mixing them in one voice is a tone error; using
the right one tells the player who is talking.

| Speaker | Calls it | Notes |
| --- | --- | --- |
| Colonists | "the rot", "the diggers", "the things under the field", "the ground" ("the ground took them") | Plain, local, often refusing a name. Strains get nicknames: runners "grabbers", brutes "breakers", spitters "spitters". |
| Colony militia | "the swarm", "bugs", "crawlers" | Practical radio shorthand; calls bores "holes" or "the pit". |
| Marine company | "the swarm", strain names as in the mission UI, bores by number | The UI register: clear, countable, unexcited. |
| Hegemony | a classification, not a name: "a restricted biological agent", "Domain-restricted hazard", incident numbers | Treats it as a compliance and quarantine matter. Never confirms what it knows. |
| Tri-Tachyon | "the organism", "the specimen", "a self-propagating denial organism" | Clinical and acquisitive; wants samples and asks the company to bring them. |
| Luddic Church | "the Hunger", "the work of the old hands", "the fruit of pride" | A judgement on the Domain's hubris, not a demon. A curate may liken it to Moloch's works; it never says it *is* Moloch. |
| Luddic Path | "the reckoning", "the harvest the machine-builders sowed" | Sees the Domain's crime coming due; may treat survivors' suffering as deserved. |
| Pirates / scavengers | "the meat-mines", "dead ground" | Mercenary interest in what a dead colony leaves lying around. |
| Galatia Academy / scientists | "the agent", "the reservoir organism" | Curious, careful, the voice most likely to say "we don't know". |

#### Working-name candidates (owner's choice, not settled)

Mission UI and briefings can keep saying **"the swarm"** for the bodies on the map
whatever is chosen; the candidates below name the weapon itself, for briefing
headlines, intel and item text. Recommendation first.

1. **Fallow** — what the weapon was for: to leave a world fallow. Reads as a
   recovered programme codename without saying whose ("the Fallow agent", "a
   Fallow outbreak"). Short, unused in canon, works in every register.
2. **Undercrop** — the colonists' word that stuck: what grows under the fields
   and harvests the farmers. Most grounded; least military.
3. **Gravebloom** — the eruption seen from the surface; vivid and a little lurid.
   Good for radio and bar talk, heavier in item text.
4. **The Deep Harvest** — stresses the biomass economy and the mother bore.
   Rhetorical; suits Church and Path voices better than UI.
5. **Hollowseed** — stresses dormancy and seeding; quieter, more literary.

Avoid as the proper name: **Swarm** (vanilla already has the Threat's Attack
Swarm and Devouring Swarm and the Swarmer missile; keep "swarm" lowercase and
generic), **Hive** (the Threat's hive unit), **Sleeper** (cryosleepers),
**Plague** (wrong mechanism: it eats, it does not infect), anything with Moloch,
Remnant, Threat, Shrouded, Abyss or Dweller (canon entities), and **Tithe**
(Luddic Path usage).

### The open maker rule

**No text settles who made it.** Briefings, radio, intel and item descriptions
may speculate; none states the maker as fact, and no two authoritative sources
agree. Facts the text may state: it is old, it was engineered, it predates the
current colony, and nobody now living claims it.

Speculation each voice is allowed to carry:

- **Hegemony officers:** a Domain denial programme, sealed for good reason; or
  a Tri-Tachyon weapon from the AI Wars that the Treaty of Crom Cruach never
  named. Either way, the Hegemony's business, not yours.
- **Tri-Tachyon staff:** a Domain military project from before the Collapse;
  the Hegemony's own wartime work, which would explain its silence. Never their
  own company's work.
- **Luddic Church:** the Domain's pride, whoever pressed the switch; the maker
  matters less than the sin.
- **Luddic Path:** the Domain and everyone who keeps its machines; proof that
  the Path is right.
- **Independents and colonists:** a corp lab, "the old war", or "nobody made it,
  it was always here" (the native-xenolife misreading).
- **Scientists:** something older or stranger than the records admit, offered
  as a question, never a revelation. Do not pay this off into an alien
  precursor.

### Tone and vocabulary

**Register.** Dread through specifics, not adjectives. The horror is the
economics: people are its food, rescue starves it, and it will come back
somewhere. Keep the setting's dry, institutional voice; the swarm is a hazard
people work around and get paid to face, not a cosmic evil.

**Briefings** (mission offer, launch, intel):

- Lead with the people: how many are sealed in, where the lift is, how long.
- Name the place and the stakes in work terms: "a pumping yard", "a mine head",
  "the shelter under the admin block".
- One line of speculation about the maker at most, attributed to a speaker.
- Close with consequence, not triumph: "Get them out. If you can put the deep
  bore down, the next people to settle here will thank you."

**Radio** (militia, administrator, company; consumed from beats per
`battle-radio-nouns.md`):

- Short, practical, present tense. Bores by number; strains by UI name in
  company lines, by nickname in militia and colonist lines.
- Specific sensory cues over labels: "ground's moving under the depot", "they're
  dragging Harlan toward the pit".
- Fear is allowed, panic is rare, jokes are dry. Nobody monologues about the
  maker under fire.
- Endings stay honest: "Mother bore's down. It's quiet." Not "we killed it".

**Item and intel text** (samples, recovered records, decorations):

- Clinical or archival voice; partial records and redactions are good texture.
- Anything recovered from the swarm is dangerous custody first and value second:
  sealed sample cases, contaminated core plugs, a survey log with the last
  entries corrupted.

**Words to use:** bore, fissure, maw, mother bore, surge, brood, harvest,
biomass (in-world: "they're taking the bodies down"), dormant, reservoir,
seeding, outbreak, eruption, quiet.

**Words to avoid:** queen, hive mind, overmind, evil, demon, invasion, aliens
(except as a colonist's mistake), infection or zombie framing, "extinct",
"eradicated", "cured".

## Anti-caricature rules

- Do not write the swarm as an army with a general, a hive mind, or a will.
- Do not let any text, achievement or reward reveal the maker.
- Do not end the weapon. Outbreaks end; the weapon recurs.
- Do not make colonists faceless fodder: they have names, jobs and a shelter
  they built, and saving them is the point.
- Do not give it powers its mechanics lack (mind control, infection of Marines,
  space travel of its own).
- Do not borrow canon horror as the explanation: no Abyss, Dwellers, Threat or
  Remnant link.

## Source anchors

- `campaign/special_items.csv`: Planetkiller (*Domain Restricted*).
- `campaign/rules.csv`: research station bioweapons lab (`salSRBW*`); Tri-Tachyon
  specimen buying; Jangala xenolife; Moloch usage; Treaty of Crom Cruach; Second
  AI War EW traps.
- `campaign/market_conditions.csv`: Inimical Biosphere, Decivilized, Decivilized
  Subpopulation.
- `strings/descriptions.csv`: First AI War relic stations.
- Vanilla names to avoid colliding with: `hulls/attack_swarm.ship`,
  `weapons/devouring_swarm.wpn`, `weapons/swarmer.wpn`, `variants/threat/hive_unit_*`.
