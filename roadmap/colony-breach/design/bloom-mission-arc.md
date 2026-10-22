# Rav Bloom mission arc: the defence and the heart

Status: DRAFT

Written: 2026-10-22

Direction for the second and third movements of the Rav Bloom arc that
`colony-breach-nouns.md` (Campaign: blooms, Direction) names: **organising the
defence** and **the heart of the infection**. Colony Breach is the first
movement and is built. This doc proposes the missions, what each one reuses,
what is new, and how the campaign raises them. Nothing here is work yet: each
candidate story becomes a `stories/` doc only when it is picked up, and the
concept choices below wait on the owner.

Read `colony-breach-nouns.md` first. Its laws hold for every mission in the arc
unless this doc names an exception, and its vocabulary (bore, tier, surge,
creep, harvest, hive posture, readiness gate, strike detachment) is reused, not
restated. Lore and voice follow `swarm-lore-guide.md`.

## The arc in one paragraph

A bloom that is not contained in its first days does not stay one colony.
Colony Breach is the company arriving while the colony is still being eaten:
get the people out, put the deep bore down if you can. The **defence** is what
comes when that fails or comes too late: the world's own military has thrown a
cordon across the country, refugees are streaming out behind it, and the
company is hired as the hand that moves while the garrisons hold. The **heart**
is the end of it: months on, the country round the old reservoir is grown over
and the cordon has held long enough to gather a force, and the company leads
the push into the overgrowth to collapse the mother bores at the bloom's root.
Each movement is larger, more allied and less about the company alone.

## What the engine already offers

A survey of what the arc can stand on, by the canonical doc that owns each
piece. "Gap" names what a mission would have to add.

| Need | What exists | Gap |
| --- | --- | --- |
| Allies on the field | The `ALLY` side (`ai-nouns.md`, Sides): fights beside the company, never takes player orders, plans under garrison authority or its own party commander, adds to fog, never ends a battle or touches the roster. `AlliedGarrison` stands a defended market's troops up as a line. Parties with LOCK place directives take and hold a place until below half strength (`garrisonables.md`). | No allied column system: allied forces are small and bespoke per mission (Thunder Run, Junction, Relay Nine, Counterattack in `early-operation-nouns.md`). |
| Columns and convoys | The scripted employer convoy (`convoy-nouns.md`; `ThunderRunColumnDriver`, `FreightConvoyObjective`) on a reserved vehicle corridor. Heavy APC and militia technical vehicles. | Reinforcement means are defender-only; `marine-side-reinforcement.md` is PLANNED. |
| Commander AI | The Mission Command layer (`mission-command-nouns.md`): assignment kinds from attack-move and secure-compound to defend-run, fire missions, escort and withdraw; per-mission commands (`conquest-command.md`, `extraction-command.md`, `phased-assault.md`). The Marine rescue commander carries hive posture, the readiness gate and strike detachments. | Lanes, tracks and phased assault are Conquest geometry; a new mission writes its own command and disclosure adapter, the way Extraction did. |
| Redoubts and forts | Colony redoubts with works (barricade, firing step, gate, heavy gun) and the civil-defence compound. Battery and shield forts with aspects (`forts.md`), sally ports, the place counter-attack. `Fortification.Demand` dials strength by tier. | The fort **redoubt** kind and the citadel are planned in `forts.md`; `fortified-lines.md` (strongpoints joined by picket lines) is a DRAFT. |
| Big maps | `MapScale.CONQUEST` 560x336 and `MapScale.COLONY` 400x240, mission-owned. Swarm flow fields hold 1000 bodies at 560x336 inside a 10 ms tick (`swarm-movement-cost.md`). | Fire, line of sight and rendering at that swarm size were not measured. |
| The tender | The Shepherd tender called down through Ship on Station builds hardpoints for a bounded visit (`tender-ship.md`); the readiness gate reads them. | Only where the fleet brought a Shepherd. |
| Hive posture and strikes | Evacuate, Opportunistic, Purge, chosen at the briefing; the gate read every pulse; detachments with stable membership and beats. | Shaped around one lift and one cohort; a front needs its own gate inputs. |
| The swarm | Bores by tier, surges, packs and leaders, growth stages and age mix, Runner, Brute, Spitter, Matriarch, spore colonies, ground colonies, harvest and the impale. The director's target follows living bores. | A swarm that has been growing for weeks (bores born at maw, elder-heavy mixes) is a tuning input, not a new rule. |
| Creep | `CreepField` on walkable ground, withering on collapse, roof collapse, digestion, the swarm's speed on it. | Creep over trees and vegetation is being built in its own session (hive creep on green worlds); canopies block movement today, so creep stops at a forest edge. |
| Country maps | Precinct plans with REMOTE sprawl (about 94% wild at 560x336), authored and procedural forests on the VERDANT palette with destructible canopies (`precincts.md`, `mapgen-nouns.md`). Rural roads and farm plots are being built in their own session. | No overgrown generator: one is composed from REMOTE sprawl, forest strokes and a palette, and must prove its own reachability. |
| Campaign launch | `RavBloomSystem` raises the Civilian Rescue event; `wakeAt` wakes a bloom at a chosen market; a bloom carries its generation and parent. `PolityDefenceMissionFactory` is the nearest analogue for a defence fought with an allied garrison. | `CampaignEventType` knows only rescue, asylum and silent colony; each new mission needs an event or source, a factory and a resolver under the exactly-once lifecycle (`campaign-event-nouns.md`). |

## Mission 2: organising the defence

### Concepts (owner's pick)

Three ways to put the company on a wider front. The option cards are on the
pick page (`bloom-m2-concept`).

- **The Line (recommended).** The world's military holds a cordon of large
  redoubts across a Conquest-sized map. Behind it, refugees are moving out; in
  front of it, the bloomed country with its bores. The company is the mobile
  reserve: it relieves the redoubt that breaks, plugs the gap, and raids the
  bores between the lines when the line can spare it. It is the owner's brief
  almost word for word (allied columns, large redoubts, sallying in a helping
  role) and it is the shape `fortified-lines.md` already wants for an endgame
  map, played with the defender's side on the company's.
- **The Road.** Relief convoys drive through bloomed country to a besieged
  town; the company clears ahead and pickets the bores beside the road. The
  closest to what is built (convoy, supply route, Thunder Run's road fight),
  but a narrow front: it is an escort mission with a swarm, and the allies are
  cargo.
- **The Sortie.** A fortified city holds behind its walls; the company sallies
  out under its guns to collapse young bores before they mature, and must be
  back inside before the gate shuts. Strong on bore growth as a clock, but the
  allies only watch, and it is Colony Breach's strike detachment made into a
  mission.

The rest of this section describes **The Line**; the other two would reuse
most of the same pieces.

### The player's role

The company is not the defence; it is what makes the defence hold. Allied
garrisons man the redoubts by garrison authority and fight from their works;
allied columns arrive on a schedule along the roads behind the line. The
company lands behind the line (the landing field rule holds: never in
bloomed streets) and is moved where the line needs it. The hive posture
becomes a **line posture**, chosen at the briefing and frozen:

- **Hold** — the company only relieves: it goes where a redoubt is breaking
  and stands there until the redoubt's own strength recovers.
- **Opportunistic** — the commander may send a strike detachment across the
  line at the bore pressing hardest on the line, when its gate is open.
- **Push** — bores in front of the line are objectives; the line's job is to
  let the company go out.

The gate keeps its shape with new inputs: (a) the line is holding (no redoubt
breaking for a sustained window), (b) the strength left behind covers every
redoubt that has asked for relief and every refugee group still walking, and
(c) a capable detachment exists.

### Win conditions

- **Win:** the line holds until the region behind it is evacuated: the last
  refugee group is off the map (or the timer the briefing names runs out) with
  no more than the allowed number of redoubts fallen. Refugees reaching the
  rear edge are the score, as evacuees are in Colony Breach: the win is people.
- **Loss:** too many redoubts fall, or the swarm reaches the evacuation road in
  force and the refugees stop.
- **Bonus:** each bore collapsed in front of the line, recorded on the outcome
  like the hive bonus; every one makes the heart smaller (*Tie to the
  campaign*).

### Map

Conquest scale, 560x336, mission-owned. The long axis runs across the front.
In front: the bloomed colony or farmland, creep across it, bores already at
bore and maw tier (the bloom has had weeks). The cordon: three to five
redoubts as strongpoints with picket lines between them, sited on the roads
the swarm would use. Behind: farmland and the evacuation road the refugees
walk along to the rear edge, the landing field, and the road the allied
columns come in on. A rural map, so it wants the rural road and farm-plot work
landing first.

### What the swarm does differently

Nothing breaks a Colony Breach law. Spawning still has a source (every body
from a living bore in front of the line), surges are still pulses, harvest
still feeds bores. Its goals change: the redoubts and the refugee road replace
the shelter and the lift, and the director spreads its pressure along the line
rather than down one corridor. Ground colonies already answer a gun the swarm
cannot rush, so a redoubt left alone long enough is spined from below: that is
the swarm's siege, and why the line needs a reserve.

### Candidate stories

1. **Bloom defence mission.** The mission, its briefing (line posture), its
   factory, its outcome (refugees out, redoubts lost, bores collapsed), and its
   `commanderEvidence` harness.
2. **Field redoubt.** The planned `forts.md` redoubt kind (a walled nest
   cluster with a small command post, no ship-grade guns), sized for an allied
   garrison, and a first slice of `fortified-lines.md`: strongpoints joined by
   picket lines.
3. **Allied column.** A scripted allied column arriving along a road and
   joining a redoubt, from the employer convoy driver made side-generic; it may
   be the first user of `marine-side-reinforcement.md`'s decisions applied to
   `ALLY`.
4. **Allied line command.** The `ALLY` commander that mans redoubts by LOCK
   directives, reports a redoubt breaking (beats and radio: the company hears
   the call), and moves its columns into the line.
5. **Company reserve command.** The Marine commander for the reserve role: the
   line posture, the generalised readiness gate, relief as an assignment.
6. **Front swarm goals.** Swarm goals and director pressure over many
   redoubts and a refugee road; bores that start grown.
7. **Refugee column.** Civilians walking out behind the line as the harvest's
   stake and the mission's score (the colony population and the cohort's walk
   to the lift are the parts to reuse).

## Mission 3: the heart of the infection

### Concepts (owner's pick)

Option cards on the pick page (`bloom-m3-concept`).

- **Broad Front (recommended).** Allied columns advance on the heart in
  parallel lanes across the largest map; the company is the spearhead in the
  lane that matters. It is the arc's payoff for the defence: the forces that
  held the line now go forward, and the company is still one hand among many.
- **The Spear.** Once the heart's spore cover is broken, the company air-lands
  in a clearing beside the heart and holds until a column fights through to it,
  then strikes. Reuses the landing field, tender hardpoints and the air threat
  ladder; tense, but the allies are off screen for most of it.
- **The Burn.** A multi-battle operation: each battle pushes a firebreak
  forward through the forest and collapses a ring of bores, and what is
  collapsed stays collapsed until the last battle at the heart. The most
  campaign-like, and the most new machinery (state carried between battles).

The rest of this section describes **Broad Front**.

### The heart

The **heart** is spacer talk for the place a bloom's spread chain began: the
old reservoir where the first bloom of the chain erupted, now months grown.
In the model it is not a new kind of thing but a lot of known things at once:
two or three **mother bores** standing on one reservoir, each with her
Matriarch at a high stage, satellite bores round them at maw, and creep over
everything within reach, including the forest. It is the only place a
battle starts with more than one mother bore.

The lore holds: collapsing the heart **ends this bloom and the chain it
seeded, not the weapon**. Text says the heart is down and the country can be
taken back; it never says the Bloom is destroyed or the world is clean, and the
reservoir rests rather than dies. This is the one mission where the hive is
the objective rather than a bonus, and the briefing says why in work terms:
every season it stands, it seeds the traffic again.

### The player's role and win conditions

- **Player:** the spearhead. Allied columns advance on their own lanes under
  their own commander and hold what they take; the company has the lane that
  reaches the heart and the hive posture that matters (Purge, in effect, from
  the start). Strike detachments, satchel carriers and machines are the tools;
  the tender and air support can work the satellite bores once their spore
  colonies are down.
- **Win:** every mother bore of the heart collapsed.
- **Loss:** the company is broken or withdraws; the allied lanes falling is a
  cost on the outcome, not a loss on its own.
- **Score:** allied losses and the satellite bores collapsed, carried back to
  the campaign.

### Map

The largest the arc uses: 560x336 at least, mission-owned, VERDANT palette.
Overgrown country: forest over most of the map, the farms and ruined
installation of a settlement that went dark months ago, the reservoir's old
workings at the heart on the far side, and the allied start line on the near
side with the roads the lanes follow. It depends on two pieces in their own
sessions: **creep over trees and vegetation** (without it the heart's creep
stops at every forest edge and the country does not read as overgrown) and
**rural roads and farm plots**. Forest canopies block movement, so the plan
needs its own reachability proof from the start line to every mother bore.

### Candidate stories

1. **Heart mission.** The mission, briefing, factory, outcome and evidence
   harness, with the allied lanes and the company's lane.
2. **Overgrown country plan.** A precinct plan: REMOTE sprawl, forest strokes,
   a dark settlement's ruins, the heart's site and lanes, with a reachability
   proof.
3. **Heart setup.** Several mother bores on one reservoir at battle start, each
   with her Matriarch's stage set by the heart's age, and satellite bores at
   maw: a setup of existing pieces, plus whatever the two-mother case breaks
   (one Matriarch's leash over the other's packs, the director's budget).
4. **Allied lane command.** An `ALLY` commander advancing columns along lanes
   and holding taken ground; Conquest's lanes are the model, rewritten for an
   allied side against a swarm.
5. **Creep on vegetation** (dependency, in its own session).

## Tie to the campaign

### How the movements connect

The three movements are one bloom's story if the campaign lets them be:

- A Colony Breach that ends with the bloom **dark** (the call refused, expired
  or failed) is what raises the **defence**. Failing the first movement feeds
  the second instead of ending the story.
- The defence's outcome sets the **heart**: redoubts that held are allied
  columns in the heart's lanes, and bores collapsed in front of the line are
  satellite bores the heart does not have. A line that fell means a heart with
  fewer allies and more growth.
- Collapsing the heart **contains** the chain: the bloom at its root ends as a
  contained one does (`colony-breach-nouns.md`, Ending one), and the blooms it
  seeded starve faster. The world's owner and the Hegemony regard it; what it
  pays beyond that is the owner's call.

### Organic or scripted (owner's pick)

Option cards on the pick page (`bloom-arc-trigger`).

- **Organic.** The arc grows out of the ordinary system: a dark bloom whose
  spread chain reaches generation one or two offers the defence, and a chain
  three deep offers the heart at its root. It needs a severity tier harsher
  than benign, since benign blooms never spread; at the shipped tier the arc
  never appears.
- **Sector threat.** A scripted threat: a chosen world is woken with
  `RavBloomSystem.wakeAt` (after the player has answered a first bloom, say),
  and runs the three movements as acts on its own clock and its own strength,
  whatever the severity preset. One bespoke event to author and tune; works
  today.
- **Both, scripted first (recommended).** Ship the sector threat first, since
  it works at benign and gives the arc a designed shape; when the harsher
  tiers ship, the organic offer reuses the same missions and factories, with
  the threat's acts as its template.

Either way the missions need new campaign plumbing: an event type (or a
mission source) for each of the defence and the heart, a factory each, and a
resolver that writes the outcome back to the bloom's ledger exactly once.
`PolityDefenceMissionFactory` is the nearest model for the defence. The
sector threat's state is one more nullable entry on the bloom ledger, created
on first read, so older saves start with none.

## Open questions for the owner

- The three concept picks above (mission 2, mission 3, campaign trigger).
- What collapsing the heart pays beyond regard, and whether a contained heart
  rests its world longer than an ordinary containment.
- Whether the defence can be fought on the player's own colony when an
  exposed player colony blooms (the nearest thing to a home defence the arc
  could offer).
