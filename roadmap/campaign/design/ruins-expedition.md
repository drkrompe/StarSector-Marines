# Ruins expedition: going into a world's ruins with the company

Status: DRAFT — proposed by the owner; nothing is built. The first work is
`ruins-expedition-fork.md`.

Written: 2026-11-09

## Purpose

A surveyed, uncolonized world with ruins offers **Explore the ruins**. The game
sends a salvage team that comes back with whatever the ruins' salvage table
rolls. There's no fight and no risk. This feature lets the player send the
company down instead, as a Marine Operations battle in the ruins, or let the
salvage team go as usual.

The rule for the whole mod: whatever the game resolves for the player, the
player may play out by hand. Playing it is always optional, and it never
replaces the standard path.

This feature does not own a second salvage system. The game's own ruins salvage
decides what the ruins hold. The battle decides whether the company reaches it,
and adds only what the company recovers along the way.

Read `raid-resolution.md` first. This design follows its fork, preference and
commit-once shape on purpose. Also read `loot-nouns.md` (field finds and
settlement) and `campaign-nouns.md` (authority boundaries).

## Vocabulary

- A **ruins site** is the market of an uncolonized world that has a ruins
  condition (`ruins_scattered`, `ruins_widespread`, `ruins_extensive` or
  `ruins_vast`), is surveyed and has not been explored. Vanilla's rows offer
  Explore the ruins only on a ruins site.
- The **ruins grade** is the condition's size, scattered to vast. Vanilla uses
  it to pick the ruins' salvage spec. Here it also sets how large and how deep
  the expedition's map is.
- **Standard exploration** is vanilla's salvage team: the salvage spec rolled
  from the site's seed, the loot screen, then the site marked explored.
- A **supported site** is a ruins site Marine Operations can fight on. Gas
  giants (sinking buoyant platforms) are unsupported at first. They always get
  standard exploration.
- The **ruins fork** is the choice offered when the player selects Explore the
  ruins: send the company (an expedition) or the salvage team (standard
  exploration).
- The **ruins preference** is the save's standing answer: ask, Marine
  Operations, or standard. A new save asks. It is separate from the raid and
  invasion preferences. The planet menu offers a way back to asking whenever
  it is set.
- A **ruins expedition** is the battle: the company lands in the ruins, fights
  or slips past whatever holds them, reaches the **vault** and gets out. It has
  no contract and no employer.
- The **vault** is the expedition's objective: the place the standard haul is
  recovered from. Reaching and holding it is what makes the expedition won.
- The **occupant** is whoever holds the ruins: a fixed, seeded fact of the site,
  hashed from the sector seed and the market and never stored, like a bloom
  reservoir (`colony-breach-nouns.md`). Candidates are scavengers (pirate or
  independent salvage crews), the ruins' own automated Domain security, and,
  on a reservoir world, the Rav. A small scattered site may hold nobody, so its
  danger is the ground itself.

## Laws

1. **The game decides whether; we decide only how.** The fork appears only
   where vanilla offers Explore the ruins and has not disabled it (survey done,
   not explored, no hostile fleet tracking the player). It intercepts only that
   option.
2. **One rule, slightly ahead.** The fork's row outranks vanilla's salvage row
   by a small margin, as the raid fork does. It names no other mod's command.
   Every path back to the game re-fires the option with a one-shot bypass, so
   standard exploration runs exactly as it would have. `salExplore` is shared
   with derelicts and debris fields, so the row also requires the market to have
   ruins.
3. **Choosing standard exploration changes nothing.**
4. **The game's salvage gives the haul.** A won expedition commits by running
   vanilla's own ruins salvage from the same spec and seed. The haul and loot
   screen are vanilla's, and so is the post-salvage rule that marks the site
   explored. Content listening for a salvaged ruins site (including the
   captain-discovery row) therefore sees an ordinary one.
5. **The company's risk buys extras, not a different haul.** What the company
   secures on the way in (field finds and occupant spoils) settles through
   Results as field finds: preselected, zero claim budget, normal capacity
   preview (`loot-nouns.md`). It never changes the vault's roll.
6. **A failed expedition leaves the ruins as they were.** Lost or withdrawn
   means nothing is salvaged and the site stays unexplored. The player may go
   back, with the company or the salvage team. The company's casualties, boats
   and experience settle through Results like any operation's. That is the
   price of failing.
7. **An expedition is not a contract.** It earns no MRB credibility, pays no
   fee and carries no entitlement. Its haul is vanilla's.
8. **An expedition commits once.** Backing out of the briefing commits nothing
   and returns to the fork. A fought expedition is recorded once at Results and
   committed once when the panel closes; a replayed result commits nothing.

## Flow

1. The player opens a surveyed ruins world and selects Explore the ruins.
2. The fork's row fires ahead of vanilla's. Under the standard preference, or
   for an unsupported site, it steps aside and vanilla's salvage runs. Under
   Ask it shows the fork; under Marine Operations it opens the briefing.
3. The briefing shows the ruins grade, what the survey suggests about the
   occupant, and the expected haul range. It may launch understrength.
4. The battle plays: land, push through the ruins, reach and hold the vault,
   get out by lift. Results records won or lost and the field finds.
5. When the panel closes, a won expedition re-fires the option through the
   bypass and vanilla salvages the ruins, showing its own loot. A lost one
   returns to the planet menu with the site unexplored.

## Shape of the battle

Proposed. The owner's picks are on the parent's picks page (`ruins-battle-shape`,
`ruins-empty-site`, `ruins-briefing-disclosure`, `ruins-derelict-identity`,
`ruins-derelict-doctrine`); the recommended option of each is written here.

**A Ruins Expedition is an Extraction-family variant.** It plays the generic
recovery law (`extraction-command.md`): the vault is the source, the haul is the
abstract package, and the egress is the company's first landing area, where its
boat comes back for it. The company must hold the vault uncontested for the
grade's **breach time** (cutting the seals and finding what is worth lifting).
That releases the haul, which then moves only with a settled escort and boards
at the landing area. The expedition is won when the haul boards. It is lost when
the haul is lost to sustained hostile control, when it is abandoned, when the
company is wiped out, or when the company withdraws.

The phases are **landfall**, the **delve** (reaching the vault, with detours to
caches), the **breach** (holding the vault), the **egress** (walking the haul
out) and the **lift**.

Why this shape rather than the others:

- **Not archive-only.** Silent Colony's archive ends on room presence with no
  egress. A delve needs a way out: the walk back with the haul is where the
  contingency and the Rav's peak land, and it is the "get out by lift" the
  feature promises.
- **Not a family of its own.** The geometry is a corridor with a source and an
  egress, which is the Extraction family's. The shared payload projection, the
  disclosure law, the headless corridor commander and the conventional
  interdiction all carry over unchanged.

What the sides know follows the family's law. The company knows the vault (the
survey located it: public geometry), its own landing area, the haul's progress
and escort. The occupant's layout is never disclosed. In a played battle the
player's squads take nothing from mission command (`mission-command-nouns.md`,
Player and faction extensions); headless runs use the generic corridor
commander for the company. An expedition has no employer, so it has no allies
and no employer support; the company's own powers work as in any mission
(`command-powers-nouns.md`).

**The grade sets the delve.** The **delve** is the ground between the landing
area and the vault: how far the route runs and how many **layers** it crosses.
A layer is a band of ruined ground the route must get through, such as a
collapsed street, a breached wall or a sealed gate, and it is where an occupant
holds (a scavenger outpost, a security zone, a nest band). The grade is vanilla's
proxy for the haul's size (rating 0.25 to 1 in the ruins salvage specs), so risk
and reward rise together.

| Grade | Map | Layers | Breach time | Occupant strength | Field caches |
| --- | --- | --- | --- | --- | --- |
| Scattered | small: a few ruined blocks, vault close to the landing | 1 | 30 s | none or light | 2 |
| Widespread | medium | 2 | 45 s | light | 3 |
| Extensive | large | 3 | 60 s | moderate | 4 |
| Vast | largest, vault deep in the ruins | 4 | 90 s | heavy | 5 |

The numbers are starting points for the battle story to tune, and they are tuned
against a player giving orders, not the headless autopilot (`CLAUDE.md`,
Balance missions against a player). Occupant strength depends on the grade
alone, never on the company's size: a vast site is meant to be too much for a
starter company, and the briefing says so.

**Field caches go deeper.** A ruins map places more caches than a contract map's
three, weighted to the far half of the route and the layers' side rooms. They
stay optional detours, the ordinary three materials, settled as field finds
(`loot-nouns.md`). The occupant's only spoils are the scavengers' stash: one
extra cache at their camp. Derelict security and the Rav leave nothing usable.
Equipment caches stay behind their own gate in `loot-nouns.md`.

**Retry meets the same ruins.** The map, the occupant and its strength are all
seeded from the site and never stored, so a second expedition meets the same
ground and a full-strength occupant. Nothing the first expedition destroyed
stays destroyed.

**Ruined ground.** The map is an abandoned settlement with no road out and no
supply link: `SettlementLink.NONE` (`settlement-off-map-link.md`). Mapgen does
not generate ruins yet; `ruins-ground.md` is the mapgen story. Its first need is
the delve: a landing area at one end, a vault at the other, and the grade's
layers between, built from mapgen's existing ruined-lot vocabulary
(`mapgen-nouns.md`: burned-out footprints, rubble ground, authored damage).
Before it exists, the stand-in battle plays on an existing map.

## Occupants

**Who holds a site is a seeded fact.** The occupant is drawn from a hash of the
sector seed and the market, using only facts fixed when the sector was
generated: the ruins grade, the site's reservoir fact
(`colony-breach-nouns.md`, Reservoir), and the system's theme tags. It reads
nothing that changes over a campaign (pirate bases, fleets, ownership), so it
cannot change between visits, saves or retries. It is never stored.

The draw, as starting weights:

| Grade | Nobody | Scavengers | Derelict security |
| --- | --- | --- | --- |
| Scattered | 35 | 45 | 20 |
| Widespread | — | 55 | 45 |
| Extensive | — | 45 | 55 |
| Vast | — | 35 | 65 |

- **On a reservoir world** the Rav hold the site one time in two, before the
  table is drawn. A reservoir is likelier on ruins worlds, so a flat Rav weight
  would make them the commonest occupant; one in two keeps them a known risk of
  such worlds rather than their rule.
- **In a `theme_derelict` system** the derelict weight doubles. **In a
  `theme_remnant` system** it doubles and the network is of the contingency
  lineage (`derelict-lore-guide.md`). Elsewhere it is Domain security.
- **Only a scattered site can hold nobody.** Anything larger was settled enough
  to leave somebody, or something, behind.

### Nobody

The ruins are empty. The expedition is the ground, the caches and the breach
time, and the briefing says so plainly, so a player who wants no walk sends the
salvage team. The ground is the ruined map's own terrain; this design proposes
no hazard system.

### Scavengers

A pirate salvage crew already working the ruins, with its camp near the landing
end and its dig at the vault (`pirates-lore-guide.md`: loot access and escape
are a pirate's priorities). It uses the pirate ground roster
(`target-faction-ground-rosters.md`) and the generic Extraction conventional
defenders (`extraction-command.md`): posted squads at the camp and the dig, a
patrol pool, and the identity-free alarm that mobilizes at most three squads.
Its stash is the one occupant spoil. Independent salvage crews are not
occupants: shooting a licensed crew would carry a reputation meaning this
feature does not own.

### Rav

Under a reservoir world's ruins lies an old seeding site, and its sleepers are
in the vault's reach. The swarm director plays a ruins profile: closed bores in
the deeper layers that noise (gunfire, a breach under way) and time spent below
open one by one, each feeding the director's waves. The breach is the loudest
thing the company does, so pressure peaks on the egress and follows the haul to
the lift. There is no mother bore, no harvest (nobody lives here) and no
cohort; the late strains appear only at the larger grades. The expedition never
wakes, feeds or ends a campaign bloom and never touches the reservoir's rest
(Boundaries).

### Derelict security

The site's own automated security, still running on orders nobody remembers:
Domain security, or Tri-Tachyon contingency security in a Remnant system.
`derelict-lore-guide.md` holds its canon, units, doctrine and voice. In the
battle:

- **Each layer is a security zone**: a control node with the sentry posts,
  drone cradles, security walkers and repair drones it controls, linked to its
  neighbours by relays. The vault zone is the strongest.
- **Zones start dormant** and wake on a sensor contact, gunfire inside their
  perimeter or damage, relaying the alarm to their linked neighbours, which go
  alert where they stand. An awake zone fights inside its perimeter and does
  not pursue past it.
- **The breach runs the contingency**: every zone wakes and every mobile unit
  that can reach the vault zone or the route out of it is called in. The egress
  is the hardest fight.
- **Killing a node isolates its zone**: no relay in or out, no repairs, and the
  contingency's call does not reach it.
- The network is the **security-network director** that `extraction-command.md`
  leaves open for Silent Colony: a mission director with honest knowledge (its
  own sensors and relays), not a mirrored squad commander. It is built here, and
  Silent Colony may adopt it later.

Its strength scales with the grade through the number of zones and what each
holds; a heavy walker guards the vault zone only at the vast grade.

## Briefing

The briefing states the occupant through what the survey saw. The category is
always true; its strength is a band set by the grade (light, moderate, heavy,
severe); its layout is never shown. Draft lines:

| Occupant | What the survey suggests |
| --- | --- |
| Nobody | No sign of anyone below. The ruins are quiet. |
| Scavengers | Landing scars and cooking heat in the ruins: a salvage crew is already working them. |
| Rav | Seismic chatter and warm spots under the ruins. Something is asleep down there. |
| Derelict security | Power signatures and active sensor sweeps: the site's security is still running. A contingency site adds that it answers on Tri-Tachyon codes. |

The haul range is the Flow's (step 3), and the battle never changes it.

## Mismatches the design accepts

- **Two loot screens.** The company's field finds settle at Results, then
  vanilla shows the vault haul. Merging them would mean taking over vanilla's
  salvage.
- **No grading.** A clean win and a narrow one get the same vault roll.
- **Retry is allowed.** A failed expedition can be tried again, or the salvage
  team sent instead. A planet-wide penalty on failure would punish exactly the
  player who chose to play.

## Open questions

- Should an occupant also block standard exploration (vanilla's team turned
  back by an armed occupant), or only shape the expedition? Blocking would make
  the fork more than an option, so the default is no.
- Should a large vault yield a vanilla salvage bonus, the way the salvaging
  skill does? Law 5 says no; the extras are the reward.
- Gas-giant platforms: a later supported site with its own ground, or never.
- Should a scavenger crew break when its captain's squad falls or its boat is
  lost (`pirates-lore-guide.md`)? The first battle slice has no break rule.
- Should holding a node, rather than destroying it, put its zone back to sleep?
  The first derelict slice only destroys nodes.
- Derelict security beyond ruins: Silent Colony's dead colony, or a
  `theme_derelict` installation on the ground. Each would be its own decision.

## Boundaries

- `raid-resolution.md` owns the player's raids on markets. This feature owns
  ruins on uncolonized worlds. They share the fork pattern, not the state.
- `loot-nouns.md` owns field finds and settlement. Vanilla owns the vault's
  haul.
- `colony-breach-nouns.md` owns the reservoir fact an occupant may read. An
  expedition never wakes a bloom.
- Vanilla's salvage spec, its drop groups and the ruins conditions stay the
  game's. Colonizing a ruins world, and the bonus a colony draws from its ruins,
  are untouched.
