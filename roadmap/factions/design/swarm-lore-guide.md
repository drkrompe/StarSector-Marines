# Rav Bloom lore guide

Status: ACTIVE — implementation reference for the Rav Bloom, the Colony Breach
swarm: what it is, what people call it, and how briefings, radio and item text
speak about it.

Written: 2026-10-21

Updated: 2026-10-25 — the lobster thread becomes owner direction: a pressure-pinned super-evolver, handling laws, easter-egg winks (owner); 2026-10-24 the lobster thread, provisional and open (owner); 2026-10-22 spores travel on cargo (owner); 2026-10-21 owner settled the name (the Rav Bloom); added the
whispered-reputation, sporeship-rumour and red-tide direction.

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
- **Sporeships seeded worlds with Earth life** in the early days of human
  colonization, long before the Domain's last years: Chalcedon "bloomed green for
  the first human arrivals", Ailmar and Cibola were seeded under a sporeship's
  colonization parameters, and Telepylus station is built around an
  Expansion-era sporeship's core (`descriptions.csv`). What their payloads held,
  and what else they may have carried, canon does not say.
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

**The Rav Bloom** (owner's choice, settled 2026-10-21). The name covers both the
weapon and an outbreak of it. **Ravager Bloom** is the older, fuller form, seen
in records and heard from old spacers, and rarely said aloud. "Bloom" works the
way a red tide does, when a lake or a bay turns overnight: something that was
always in the water multiplies all at once and kills what lives there.
Descriptions may use that imagery (a colony "blooming", ground or water
"turning"), but they never fix a colour for the creep or the bodies. Art decides
that.

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
7. **Spore colonies.** Bores grow static anti-air growths round their mouths. Read it as part of
   the denial design: the weapon was built expecting aircraft.
8. **The mother bore.** One bore in a cluster is the deep one, the root of the
   reservoir reaching the surface. It erupts late, already grown, and is the only
   stage that can raise a **matriarch**. Collapse it and the cluster stops.

### Why it recurs, and what ending an outbreak means

- **It was seeded widely.** Whoever deployed it seeded many worlds and many sites
  per world; the records of where are lost. Outbreaks therefore surface across
  the Persean Sector, at no predictable world. Dormant material also travels:
  it rides on cargo and arrives with people who do not know they carry it, and
  wakes wherever it lands in a colony busy and dense enough to feed it (owner,
  2026-10-22). Where and how often belongs to `colony-breach-nouns.md` (Campaign: blooms); the lore only
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
  radio and bar talk. Cargo is the one that is a mechanic: it is how blooms reach the colonies they wake in (`colony-breach-nouns.md`, Campaign: blooms); the rest stay talk until something makes
  it one.

### Names: what people call it

**The settled vocabulary.**

- **The Rav Bloom**: the weapon and the phenomenon. Used in briefings, intel and
  item text, and by people being careful or formal.
- **A bloom**: spacers' shorthand for one outbreak, deliberately understated,
  like a weather event: "There's been a bloom on Asharu." Everybody knows what it
  means, and nobody needs it explained.
- **Ravager Bloom**: the full form, rare. Old records, old spacers, a
  Hegemony file header.
- **Ravs**: the bodies. Ordinary people talk about them as monsters and
  creatures, not as a weapon: "ravs", "ravagers", "the things", "bugs". Militia
  and Marines say **ravs**.
- **the swarm**: stays the lowercase, generic word in mission UI ("swarm
  pressure", strain names, bore counts). It is a UI term, not a name.

**Whispered but known.** The Bloom is a story everyone has heard and few have
lived through. Almost nobody has seen a rav; almost everybody knows someone who
knew a colony that went quiet. The full name is not said casually. People
lower their voices, change the subject, or say "a bloom" and let it sit. Write
it the way the Sector talks about a disaster that happens to other people, until
it does not.

Each name belongs to who says it. Mixing them in one voice is a tone error; using
the right one tells the player who is talking.

| Speaker | Calls it | Notes |
| --- | --- | --- |
| Colonists | "the things", "ravs", "the ground" ("the ground took them"); the Bloom only in a whisper | Plain, frightened, local. Strain nicknames: runners "grabbers", brutes "breakers", spitters "spitters", skitterers "ticks". |
| Spacers / traders | "a bloom", "the Rav Bloom" | Understated, weather-like; "Ravager Bloom" from the old hands. |
| Colony militia | "ravs", "bugs" | Practical radio shorthand; calls bores "holes" or "the pit". |
| Marine company | "ravs" on the radio; strain names and "the swarm" as in the mission UI; bores by number | Clear, countable, unexcited. |
| Hegemony | a dry hazard designation and an incident number, never the folk name in an official voice | Treats a bloom as a quarantine matter: the world is cordoned and the records are sealed. Never confirms what it knows. |
| Tri-Tachyon | "the organism", "the specimen", "Bloom material" | Clinical and acquisitive; wants samples and asks the company to bring them. |
| Luddic Church | "the Bloom", "a judgement" | A judgement on the Domain's pride, not a demon. A curate may liken it to Moloch's works; it never says it *is* Moloch. |
| Luddic Path | "the Bloom", "the Domain's sin come back" | Sees the Domain's crime coming due; may treat survivors' suffering as deserved. |
| Pirates / scavengers | "a bloom world", "dead ground" | Mercenary interest in what a dead colony leaves lying around. |
| Galatia Academy / scientists | "the agent", "Bloom reservoir" | Curious, careful, the voice most likely to say "we don't know". |

Names considered before the owner chose: Fallow, Undercrop, Gravebloom, The Deep
Harvest, Hollowseed. Still avoid as names: **Swarm** as a proper noun (vanilla's
Attack Swarm, Devouring Swarm and Swarmer), **Hive** (the Threat's hive unit),
**Sleeper** (cryosleepers), **Plague** (it eats, it does not infect), anything with
Moloch, Remnant, Threat, Shrouded, Abyss or Dweller, and **Tithe** (Luddic Path
usage).

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
- **Spacers (the sporeship rumour):** the Bloom was a sporeship payload: the
  seeding stock that turned dead worlds green, bred into something that turns
  green worlds dead. It explains why blooms surface on old seeded worlds and why
  nobody can say who did it. It stays rumour; no text confirms it, and the
  Hegemony and Tri-Tachyon both dismiss it in the same breath.
- **Scientists:** something older or stranger than the records admit, offered
  as a question, never a revelation. Do not pay this off into an alien
  precursor.

### The lobster thread (owner direction)

**Owner direction** (2026-10-25, building on the provisional thread of
2026-10-24). Volturnian lobster, the luxury shipped from Volturn to every
market in the Sector, is the Ravs' stock, held in its lobster form only by
pressure.

**The anchor.** The installed commodity text says the lobster is a mix of
Old Earth species "and a little imagination", and that it grows wild in the
crushing depths of Volturn's sea-floor. Volturn is the only source. Everything
below is mod direction built on those two facts.

**The reading.** The stock is a super-evolver. At the bottom of Volturn's
world-sea the pressure pins it in one shape: the tasty crustacean, the way
crab-like bodies keep re-evolving on Earth from unrelated lines. Off the
sea-floor and out of pressure it runs through forms quickly, and on a world
with food it becomes the Bloom. The earlier larval and locust readings fold
into this one: the lobster is the pinned form, and release, not crowding, is
the switch. The AI-War weapon-stock reading survives only as a voice's guess.

**The handling laws.** Because the lobster is so good and so dear, the Diktat
and other polities keep harsh food-preparation laws on the books, framed as
luxury protection: it must be kept in pressure tanks until it is cooked,
cooked in a pressure vessel, and cooking it is a licensed trade. Wasting a
lobster by letting it out of pressure is a crime. Nobody says aloud why the
law is that strict, and most people take it for snobbery and price-fixing.

**Where outbreaks come from.** Shoddy handling: a retailer cutting corners on
tanks, a smuggler moving it cheap, a kitchen without a licence. Or a polity
trying to farm its own lobster to break Volturn's price. This sits under the
cargo route above and explains why blooms surface anywhere goods travel and
break out only sometimes.

Rules for the thread:

- **Winks, never a confirmation.** Easter eggs may play the joke: a civilian
  bark, a radio aside, a market note ("finally cheap Sindrian lobster on the
  market and I can't enjoy it because it's doomsday"). Lobster crates, tanks
  and pressure cookers can stand where a heart came up. No briefing, intel,
  codex or item text states that the lobster becomes the Ravs, and no voice in
  the maker list above names it as the cause. A wink is funny only to a player
  who has put it together.
- **The law can be shown straight.** The handling laws, licences and pressure
  tanks are ordinary in-world facts any voice may mention, as food snobbery or
  Diktat bureaucracy, never as containment.
- **Set dressing.** Freight-yard lobster crates, tanks in a research annex, a
  market's tanks under the creep (`heart-origins.md`), a burst pressure tank in
  a back kitchen. Most factions have not connected the dots; a research site
  that had is one of the heart's origins. The props are `lobster-trade` (a
  pressure tank, the tank burst, a pressurised crate, a licensed range); a main
  street's back kitchen, a market's fish stall and a freight yard's first stack
  carry them.
- **Art is not changed for it.** A faint crustacean resemblance is welcome in
  new swarm art but never required, and accepted strain art (the Runner, the
  Brute, the Matriarch and the rest) is not reworked to show it (owner).

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

**Words to use:** the Rav Bloom, a bloom, ravs, bore, fissure, maw, mother bore,
surge, brood, harvest,
biomass (in-world: "they're taking the bodies down"), dormant, reservoir,
seeding, outbreak, eruption, quiet.

**Words to avoid:** queen, hive mind, overmind, evil, demon, invasion, aliens
(except as a colonist's mistake; "monsters" and "creatures" are fine in folk
speech), infection or zombie framing, "extinct",
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
- `strings/descriptions.csv`: sporeship seeding (`planet_chalcedon`,
  `planet_ailmar`, Cibola / `desert_moons_ruins`, `station_telepylus`).
- `campaign/rules.csv`: research station bioweapons lab (`salSRBW*`); Tri-Tachyon
  specimen buying; Jangala xenolife; Moloch usage; Treaty of Crom Cruach; Second
  AI War EW traps.
- `campaign/market_conditions.csv`: Inimical Biosphere, Decivilized, Decivilized
  Subpopulation.
- `strings/descriptions.csv`: First AI War relic stations.
- Vanilla names to avoid colliding with: `hulls/attack_swarm.ship`,
  `weapons/devouring_swarm.wpn`, `weapons/swarmer.wpn`, `variants/threat/hive_unit_*`.
