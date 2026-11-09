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

Read `raid-resolution.md` first. This design follows its fork, fork setting and
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
- The **ruins fork setting** is the Settings page's "Ask before exploring
  ruins" key, saved with the game and shown only where the build offers the
  fork. On (a new save's default), every supported site asks; off, vanilla's
  salvage team goes untouched. The fork has no "always" answer: the player
  chooses each time (owner). It is separate from the raid and invasion fork
  settings.
- A **ruins expedition** is the battle: the company lands in the ruins, fights
  or slips past whatever holds them, reaches the **vault** and gets out. It has
  no contract and no employer.
- The **vault** is the expedition's objective: the place the standard haul is
  recovered from. Reaching and holding it is what makes the expedition won.
- The **occupant** is whoever holds the ruins: a fixed, seeded fact of the site,
  hashed from the sector seed and the market and never stored, like a bloom
  reservoir (`colony-breach-nouns.md`). Candidates are scavengers (a pirate
  salvage crew), derelict security (`derelict-lore-guide.md`), and, on a
  reservoir world, the Rav. Only a scattered site may hold nobody; it then
  offers environmental puzzles in the site's works, and sealed stores to reward
  them.

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
6. **A failed expedition leaves the ruins as they were.** Lost or pulled out
   means nothing comes out of the vault and the site stays unexplored. The
   player may go back, with the company or the salvage team. A pulled-out
   expedition keeps the finds it banked; a lost one keeps nothing. The
   company's casualties, boats and experience settle through Results like any
   operation's. That is the price of failing.
   **The site remembers only what was taken.** The one fact a ruins site
   stores is the set of its finds already brought home, written once when a
   won or pulled-out expedition commits. A later expedition finds those places
   empty and meets everything else as seeded: the occupant at full strength,
   the works reset, every other cache and store in place. A won expedition's
   set no longer matters, since vanilla then marks the site explored.
7. **An expedition is not a contract.** It earns no MRB credibility, pays no
   fee and carries no entitlement. Its haul is vanilla's.
8. **An expedition commits once.** Backing out of the briefing commits nothing
   and returns to the fork. A fought expedition is recorded once at Results and
   committed once when the panel closes; a replayed result commits nothing.

## Flow

1. The player opens a surveyed ruins world and selects Explore the ruins.
2. The fork's row fires ahead of vanilla's. With the fork setting off, or for
   an unsupported site, it steps aside and vanilla's salvage runs. Otherwise it
   shows the fork.
3. The briefing shows the ruins grade, what the survey suggests about the
   occupant, and the expected haul range. It may launch understrength.
4. The battle plays in two phases. In the approach the company lands, fights
   across the open ruins and takes the entrance of the megastructure or
   bunker. In the delve it works inside to the vault, holds it, and gets the
   haul out to a lift point. At any time the company may instead pull out from
   its pull-out point, which stands at the landing until the entrance is taken
   and at the entrance after. Results records **won**, **pulled out** or
   **lost**, and the field finds: a won or pulled-out expedition brings its
   banked finds home, and a lost one leaves them behind.
5. When the panel closes, a won expedition re-fires the option through the
   bypass and vanilla salvages the ruins, showing its own loot. A pulled-out or
   lost one returns to the planet menu with the site unexplored.

## Shape of the battle

Owner picks (2026-11-09): the expedition is a mission family of its own
(`ruins-battle-shape` c); a scattered site may hold nobody, and then offers
environmental puzzles (`ruins-empty-site` a); the briefing names the occupant
truthfully through survey readings (`ruins-briefing-disclosure` a); derelict
security is as `derelict-lore-guide.md` describes it (`ruins-derelict-identity`
a, `ruins-derelict-doctrine` a).

**Every expedition gives gameplay and a reward** (owner). Whether the ruins are
held or empty, the company has something to do on the way to the vault and
something to bring home besides the vault's haul: a fight and the field caches
where somebody holds the site, the site's works and their sealed stores where
nobody does. No expedition is a walk to a box.

### The Ruins mission family

A **Ruins Expedition** is a mission family of its own in `mission-command-nouns.md`
terms, beside Conquest, Sabotage, Assault, Raid and Extraction. Its geometry is
a **delve**: layered ground the company works through from its landing to a
vault, with works and detours off the route, and then a way out. That is
neither Extraction's corridor (a payload moving between two fixed endpoints)
nor Assault's search sectors.

It plays in two phases (owner, 2026-11-09):

- **Phase A, the approach**: landfall, then the fight across the open ruins
  outside, up to the megastructure or bunker, ending when the company takes
  its entrance.
- **Phase B, the delve**: inside, through the interior layers to the vault
  with detours to caches and works, the **breach** (holding the vault), the
  **egress** (getting the haul to a lift point) and the **lift**.

The two phases fight differently. Outside is open ground: derelict flocks, the
scavengers' camp and patrols. Inside is roofed rooms, corridors, seal doors and
the works: derelict heavies, the scavengers' dig, the Rav's deeper bores.

- **The vault and the breach.** The vault is the family's one source. The
  company must hold it uncontested for the grade's **breach time**. Routing
  power to the vault's seal shortens the breach (Works).
- **The haul.** The breach releases the haul, which moves only with a settled
  escort within its leash. It is lost to sustained hostile control of its cell
  while unattended, and abandoned if nobody escorts it for long.
- **Lift points.** The haul boards at a lift point: the landing area, the
  **entry** once taken (The entrance and the pull-out point), or any **landing
  court** the company holds, an open court the ruins ground publishes in a
  layer. Calling the boat to a nearer court shortens the egress but means
  holding the court while it comes in.
- **Won, pulled out and lost.** Won when the haul boards. **Pulled out** when
  the company leaves while its pull-out point is held, taking its banked finds
  home and leaving the vault (Pressing on or pulling out). Lost when the haul
  is lost or abandoned, the company is wiped out, or it retreats while its
  pull-out point is lost.

**What it borrows from Extraction** (`extraction-command.md`): the source-hold
rule (uncontested presence for a time releases the package), the escorted
package with its leash, loss and abandonment, boarding at an egress, the
payload projection's shape (so the rail and map show the haul like any
package), and the family's disclosure law: the company knows the vault, its
lift points and the haul; the occupant's layout is never disclosed.

**What it does not borrow:** the single corridor and its corridor commander;
the fixed egress (a lift point can be any held court); the generic defenders'
source-perimeter pool and identity-free alarm as the whole opposition (only the
scavengers use them, and only in part); and Extraction's assumption that there
is always an opposing commander.

**The commander pair.** A command duel needs a strategy per side with agency
(`mission-command-nouns.md`, Command duel). Here:

- **The delve commander** plays the company in headless runs only; in a played
  battle the player's squads take nothing from mission command. It advances a
  layer at a time with a lead element ahead of the body, sends a squad to a
  cache or a works task when its layer is quiet, forms the breach hold on the
  vault, and after the breach screens the haul to the nearest lift point it
  holds. It plans from the same frozen frame as every commander.
- **The occupant's side** is the occupant's own: the **crew commander** for
  scavengers (a conventional squad commander over a camp and a dig), the swarm
  director under a ruins profile for the Rav, and the **security-network
  director** for derelict security (`derelict-lore-guide.md`). An empty site has
  no opposing side; the delve commander plays alone.

An expedition has no employer, so it has no allies and no employer support. The
company's own powers work as in any mission (`command-powers-nouns.md`).

### The grade sets the delve

The **delve** is the ground between the landing area and the vault: how far the
route runs and how many **layers** it crosses. A layer is a band of ruined
ground the route must get through, such as a collapsed street, a breached wall
or, inside the megastructure, a sealed gate. It is where an occupant holds (a scavenger outpost, a security
zone, a nest band) and where the site's works stand. The grade is vanilla's
proxy for the haul's size (rating 0.25 to 1 in the ruins salvage specs), so risk
and reward rise together.

| Grade | Map | Layers | Breach time (unpowered / powered) | Occupant strength | Field caches |
| --- | --- | --- | --- | --- | --- |
| Scattered | 200x112: a dead town across the route | 1 | 30 / 10 s | none or light | 2 |
| Widespread | 280x168 | 2 | 45 / 15 s | light | 3 |
| Extensive | 400x240 | 3 | 60 / 20 s | moderate | 4 |
| Vast | 560x336, vault deep in the ruins | 4 | 90 / 30 s | heavy | 5 |

The numbers are starting points, tuned against a player giving orders, not the
headless autopilot (`CLAUDE.md`, Balance missions against a player). Occupant
strength depends on the grade alone, never on the company's size: a vast site
is meant to be too much for a starter company, and the briefing says so.

**Field caches go deeper.** A ruins map places more caches than a contract
map's three, weighted to the far half of the route and the layers' side rooms.
They stay optional detours, the ordinary three materials, settled as field
finds (`loot-nouns.md`). The scavengers' stash is one extra cache at their
camp; derelict security and the Rav leave nothing usable. Equipment caches stay
behind their own gate in `loot-nouns.md`.

**Retry meets the same ruins.** The map, the occupant, its strength and the
works are all seeded from the site and never stored, so a second expedition
meets the same ground at full strength and its works reset. Nothing the first
expedition opened, powered or destroyed stays that way.

**Ruined ground.** The map is an abandoned settlement with no road out and no
supply link: `SettlementLink.NONE` (`settlement-off-map-link.md`).
The ground is mapgen's (`mapgen-nouns.md`, Ruins ground): a landing area
toward one end, set in from the map's edge among the ruins, the vault at the
other, the grade's layers between with their side
rooms, landing courts and works sites, every place published by name, built
from mapgen's ruined-lot vocabulary. Its map grows with the grade, two steps up
the tier band, larger than a mission at the same tier, so the player must judge
whether they still have the forces to go on (owner's pick): 200x112, 280x168,
400x240 and 560x336 cells.
`BattleSetup.createRuins` plays on it: the vault and every lift point come from
the ground's published places, never from a search of the map.

**Outside, then inside** (owner, 2026-11-09). A ruins site is a hybrid: the
company lands outside, on the landing area or a court, and works into a
megastructure or bunker, roofed rooms, corridors and seal doors with the vault
deep inside, then brings the haul back out to a lift point. The interior grows
with the grade: a bunker round the vault on scattered ground, the last layer
at widespread, the last two at extensive and vast (`mapgen-nouns.md`, Ruins
ground). One roof covers the whole megastructure, the owner's pick; a court
inside is a light well, so every lift point is under open sky. Inside is where
derelict flocks
cannot follow and heavies hold, and where the works' seal doors and power hall
stand.

### Works

The **works** are the ruins' own machinery, still there and partly working:
the pieces of a site the company can operate, open or clear. Every ruins map
carries some. On an empty site they are the expedition: the vault's way in is
behind them. On an occupied site they are optional detours like the caches.

Each piece is built from something the mod has or nearly has:

| Piece | What it is | Worked by | Built from |
| --- | --- | --- | --- |
| **Seal door** | A sealed door on the vault, a store or a passage | Power, or a breaching charge (the vault's: power only) | A locked door (`garrisonables.md`, Locked doors and hardened walls) unlocked by power; the satchel and wall breach for the forced way (`SatchelChargeService`, `WallBreachSystem`); the vault's door and walls hardened |
| **Power hall** | The site's generator, cold but charged | Restarting it: infantry hold its console | A mission-owned interaction (the side objective's interact trigger and world prompt, `mission-command-nouns.md`, Side objectives) on the main path |
| **Junction** | A switch that feeds one of two branches | Infantry at its box, either way, any number of times | Interaction as above, feeding a small power graph the mission owns (new, unit-testable) |
| **Collapse** | Rubble choking the short way | A breaching charge, or the long way round | A breachable wall run in rubble materials |
| **Records terminal** | The colony's records, still readable | Infantry hold its console | Interaction; reading it reveals the site's power plan and hidden stores (a discovery, as a hidden side objective's) |

**Power is the puzzle.** The power hall has a fixed **capacity**: it can run
only so many consumers at once (seal doors, a lift to a landing court, the
vault's seal). Junctions decide which branches it feeds. A site's puzzle is the
choice and the order: power the store first and route the vault later, or
take the vault fast and leave the store; restart the hall, or spend a charge on
the door and keep moving. The records terminal turns the unknown wiring into a
plan; without it the company learns each junction by throwing it.

**The wiring follows the delve.** Each layer has one junction at its stand. It
feeds either that layer's sealed store or the way on: the layer's seal gate, and
at the last layer the vault's seal. Junctions start on the way on, and the
capacity is the way on's load plus one, so one store at a time can be fed
beside it. An overloaded hall powers nothing until a junction sheds load.
The vault's seal is its inner door, and on scattered ground the bunker's door is
sealed too, both on the last layer's way on; once the vault is breached they
stay open. The vault's door and its suite's walls are hardened: no charge and
no heavy hull gets in, so the hall is the vault's one way.
A store, a gate or the collapse is forced through its prompt: a satchel carrier
brought within reach sets the charge.

**Charges are the price of skipping it.** A breaching charge forces a store or
gate or clears a collapse at once, but charges are finite issue
(`DemolitionKit`). The vault is the exception: its door opens only by the
hall's power. Nothing is ever locked for good: every other puzzle has a forced
way and the hall is always on the site, so a wrong choice costs time and
charges, never the expedition.

**What the works pay.** A powered vault breaches in a third of the time. Each
**sealed store** behind a seal door holds an extra field cache. An empty site
has two stores, where they are its main reward; an occupied site has at most one
per layer. A hidden store is shown only by the records terminal. All of it
settles as field finds, preselected and outside vanilla's haul (Law 5).

**On an empty site** the works are laid as one chain that ends at the vault:
the vault seal is unpowered, the short way is choked by a collapse, the power
hall stands in a side layer, and the capacity covers the vault and one other
consumer, so the player chooses between the sealed store and the landing court.
It is short (scattered is the smallest map) and every step is visible from the
one before it.

**On an occupied site** the works are fewer and optional, and they touch the
occupant: restarting a power hall is loud (it wakes the Rav's nearest bores and
any derelict zone that hears it), and at a derelict site the power hall feeds
the network, so a junction that cuts a branch powers down the zones on it.

### The entrance and the pull-out point

Owner (2026-11-09, on `ruins-pull-out`): "pull out keeps the banked, also we
should have phase (A) - where the player fights up to the ruin entrance, then
Phase (B) exploration - where we move up the "pull out" trigger area to be near
the entrance so that the player doesn't need to "walk back to drop point"".

**The entrance.** The interior's entrances are the ways across its first wall
from open ground (`mapgen-nouns.md`, Ruins ground: `ruins.interior`); on
scattered ground there is one, the bunker's door. Every entrance has an
**apron**, the open ground outside it under open sky, which the company sees
from the start: the map shows the megastructure's outline and its entrances,
never what is inside.

**Taking the entrance starts phase B.** The company takes an entrance by
holding its apron uncontested for the same time a landing court takes
(`COURT_HOLD_SECONDS`): company infantry on the apron and no hostile in it.
The first entrance taken becomes the **entry**, and phase B begins. The door
need not be open: a sealed bunker door is the works' first puzzle, worked from
the apron the company now holds. Taking a second entrance later adds it as a
further lift point; the entry stays the first one.

**The pull-out point.** In phase A the pull-out point is the landing. From
phase B on it is the entry: the boat comes to its apron, so pulling out no
longer means walking back to the drop point. The landing and any held court
stay lift points for the haul; the entry is where the rail and the pull-out
point to.

**Holding it.** The entry stays held without a garrison. It is lost only if the
occupant **retakes** it: hostiles hold its apron uncontested, with no company
infantry in it, for the same hold time. A lost entry is retaken by holding it
again. While the pull-out point is held, PULL OUT pays every banked find,
wherever the company stands. Where the company stands decides who gets home:
survivors inside the pull-out point's extraction circle, or any other held lift
point's, extract unhurt, and the rest roll to get home as in any Retreat
(`campaign-nouns.md`, Ending an operation). So the finds depend on keeping the
way out, and the soldiers on being near it when the call comes. A squad left on
the apron is insurance against a retake, not a requirement.

Retakes come from what each occupant already does. In the contingency the
derelict heavies walk to the egress route, which runs through the entry. The
scavengers' camp and patrols are outside. The Rav follow noise and the haul to
the lift. An empty site never retakes it.

### Pressing on or pulling out

The owner picked the larger maps: "The larger maps give us the more interesting
exploration. The player needs to determine whether they still have the forces
to "continue"". So the delve is a run of decisions, not one objective. Each
layer pays something and costs something, and at any layer the player may stop
and leave with what the company has.

**Deeper pays more.** Caches are weighted to the far half of the route, an
occupied site has a sealed store per layer, and the vault is at the end. The
vault's haul is vanilla's, and the salvage team brings the same haul (Law 4,
Law 6), so the haul alone is no reason to press on to the vault. What the vault
pays the expedition is the **vault store**: the deepest find, a bundle per layer
of the delve (one at scattered, four at vast), recovered when the haul boards
and settled as field finds beside it (owner pick `ruins-vault-worth` a).

**Banked finds.** Elsewhere a secured find comes home only on a victory
(`loot-nouns.md`). In a Ruins battle a secured find is **banked**: it comes
home if the expedition is won or pulled out, and it is left behind only if the
company is wiped out or retreats while its pull-out point is lost. What the company
stands to lose grows with every layer, because a wipe-out costs everything
banked so far.

**Pulling out** is the family's Retreat (owner pick `ruins-pull-out` a).
Its confirmation draws the extraction circle round the pull-out point and every
other held lift point, lists the banked finds that will come home, and says
the vault stays sealed and the site unexplored. It pays the banked finds while
the pull-out point is held (The entrance and the pull-out point). Survivors
inside a circle extract unhurt, and the rest roll to get home. With the
pull-out point lost it is an ordinary Retreat, and the finds stay behind.

| Pull out | Comes home | Stays in the ruins |
| --- | --- | --- |
| Before any find | Nothing; the losses settle | Everything |
| In phase A, from the landing | Every find banked outside | The interior, the vault and its store |
| In phase B, from the entry | Every banked find, each store opened | The deeper caches, the vault and its store |
| During the breach or the egress | Every banked find | The haul, abandoned; the vault store |
| (Won) The haul boards | Banked finds, the vault store, then vanilla's haul | Nothing |

**The outcome.** A pulled-out expedition is neither won nor lost. Results reads
**Pulled out**: it settles the banked finds as field finds (Law 5) and the
company's losses. Nothing comes out of the vault and the site stays unexplored
(Law 6), so the player may come back with the company or send the salvage team.

**What the site remembers** (owner pick `ruins-after-pull-out` a): what was
taken stays taken (Law 6). Without it an empty scattered site is a farm, with
its caches and two stores free of risk on every visit.

**The rail shows the phase.** The vault's card gains a first step, so its
steps read **Entrance > Breach > Escort > Board**. In phase A the card reads
"Take the entrance": every entrance is marked on the map with its apron, and an
apron being held shows its hold timer, as a court does. When the entry is taken
the card reads "Entry held" for a moment, then "Reach the vault", and the map
moves the pull-out marker from the landing to the entry. The **Delve row**, with
the vault's card, heads with the phase and the pull-out point: "Approach · pull
out at the landing", then "Delve · pull out at the entry", or "Delve · ENTRY
LOST" in the alert colour while the occupant holds it, with PULL OUT dimmed to
an ordinary Retreat.

**Judging the forces.** The Delve row gives the player three honest reads, none
of which names an enemy unit or place:

- **The company**: effectives on the ground against the number landed, squads
  below half strength, machines lost, and breaching charges left (the works'
  forced ways spend them).
- **What it has**: the banked finds by material, the stores opened, and the
  route length back to the pull-out point.
- **What lies ahead**: how deep the company is ("Layer 2 of 4"), and a **close
  reading** of the next layer once the company holds a layer's stand. The close
  reading is the briefing's survey reading sharpened by the company's own
  sensors: the layer's strength band (quiet, held, strongly held) and the
  occupant's sign. For derelict security that is power draw and flock sweeps,
  for the Rav lifesigns that rise with noise and time, and for scavengers
  chatter and generator heat. The vault's layer always reads strongest.

**The way back is part of the cost.** The haul is escorted from the vault to a
lift point under the occupant's heaviest pressure: the breach wakes every
derelict zone, peaks the Rav and brings the crew to the dig. A company that
just reaches the vault has still to come out. Once the vault is sighted, the
Delve row shows its route to the nearest held lift point, usually the entry. A
held court inside, nearer the vault, is shorter, and holding one costs a squad.

**Losses are felt at the stands.** Taking the entry is the first stand of the
delve: the interior's close reading comes in and the phase A tally is shown.
After that, each time the company holds a layer's stand,
the Delve row reports the tally since the last one ("Layer 2 held: 6 down, a
Hound lost, 2 finds banked") and offers PULL OUT beside it. A stand is the
natural place to judge: the layer behind is cleared, the next one's close
reading has just come in, and the way back is the shortest it will be. Between
stands the losses read where they happen: a squad below half strength dims on
the rail, and the comms feed reports each squad that breaks and each machine
lost.

**The briefing frames it.** Beside the grade's strength band, the briefing
gives the delve's depth in layers, outside and inside, and one line on
leaving: the company may pull out from the landing, and from the entrance once
it is taken, and what it has recovered comes home. The band
already tells a starter company that a vast site is too much; the pull-out
makes it worth going in anyway, as deep as the company can, and coming back.

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

The ruins are empty, and the expedition is the site's works: a short chain of
a power hall, junctions, a collapse and seal doors that ends at the vault, with
two sealed stores off it (Works). The briefing's readings say the site is quiet
and hint at the chain.

### Scavengers

A pirate salvage crew already working the ruins, with its camp near the landing
end and its dig at the vault (`pirates-lore-guide.md`: loot access and escape
are a pirate's priorities). It uses the pirate ground roster
(`target-faction-ground-rosters.md`) under the **crew commander**, which
borrows the generic Extraction defenders' pieces (`extraction-command.md`):
posted squads at the camp and the dig, a patrol pool, and the identity-free
alarm that mobilizes at most three squads. What is its own is the two places it
answers for, the dig first and the stash second.
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
Domain security, or Tri-Tachyon contingency security in a Remnant system. It
fights as **drone flocks and heavy walkers** (owner, 2026-11-09).
`derelict-lore-guide.md` holds its canon, units, doctrine and voice. In the
battle:

- **Each layer is a security zone**: a control node with the drone cradles,
  heavy walkers, sentry posts and repair drones it controls, linked to its
  neighbours by relays. The vault zone is the strongest.
- **Zones start dormant** and wake on a sensor contact, gunfire inside their
  perimeter or damage, relaying the alarm to their linked neighbours, which go
  alert where they stand.
- **Flocks are the alarm and the screen.** A woken zone's flock goes up first,
  finds and pins the company, and may range a bounded leash beyond its
  perimeter. **Heavies are the anchor**: they hold their layer's chokes and
  courts, never leave their perimeter, and come to what the flocks have pinned.
- **The breach runs the contingency**: every zone wakes, the flocks go for the
  haul's escort and the heavies walk to the egress route and hold it. The
  egress is the hardest fight.
- **Killing a node isolates its zone**: no relay in or out, no repairs, no
  rebuilt drones, and the contingency's call does not reach it.
- The network is the **security-network director** that `extraction-command.md`
  leaves open for Silent Colony: a mission director with honest knowledge (its
  own sensors, flocks and relays), not a mirrored squad commander. It commands
  flocks as units (`derelict-drone-flocks.md`) and walkers as ordinary mechs.

Its strength scales with the grade: one to four flocks, and from none or one
heavy at scattered to four or five, a vault frame among them, at vast
(`derelict-lore-guide.md`, Strength by grade).

## Briefing

The briefing names the occupant truthfully, and it does so through the
**survey readings**: what the fleet's sensors picked up from orbit. Each
occupant has its own kind of reading (owner, 2026-11-09), so a player learns to
read a site before the assessment line says what it is.

The briefing shows the **assessment** (the occupant's kind, always true), a
**strength band** set by the grade (light, moderate, heavy, severe), and two or
three readings drawn from that occupant's pool, seeded from the site so a
revisit reads the same. A reading is always true of the site; it never names a
place on the map or a unit count.

| Occupant | Kind of reading | Draft readings |
| --- | --- | --- |
| Nobody | Quiet, with structure and power | No lifesigns, no movement, no emissions. Residual charge in a buried power hall. The vault seal reads unpowered. A collapse across the main approach. |
| Scavengers | Comms, heat and traces | Comms chatter on an unlicensed band. Cooking heat and a generator in the ruins. Fresh landing scars; a transponder-dark boat on the ground. Vehicle tracks to the vault. |
| Rav | Biological lifesigns | Faint lifesigns below the ruins, clustered, too many and too cold to be people. Seismic chatter under the deep layers. Warm spots that move between scans. |
| Derelict security | Movement and power | Movement on fixed patrol paths. Power draw from buried halls. Active sensor sweeps that answer the survey's ping. A contingency site's emissions carry Tri-Tachyon codes. |

The strength band reads through the same instruments: the lifesign count, the
power draw, the chatter's volume. An empty site's readings are its puzzle's
first hints: they say there is a power hall, a dead seal and a collapse, and
leave the wiring to the records terminal.

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
- Should a sealed store be the first place an equipment cache appears, once
  `loot-nouns.md` opens that gate?
- Derelict security beyond ruins: Silent Colony's dead colony, or a
  `theme_derelict` installation on the ground. Each would be its own decision.
- Ruins ground tuning waits on played battles: the dead town's densities,
  layer, room and court sizes, and the interior's chamber count and spine width
  are first guesses, to be tuned against a player giving orders.
- What keeps the entry held is on the picks page (`ruins-entry-hold`): held
  until retaken with no garrison (as written), a squad on its apron at the
  moment of the pull-out, or a standing garrison.

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
