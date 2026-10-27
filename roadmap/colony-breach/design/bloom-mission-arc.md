# Rav Bloom mission arc: the defence and the heart

Status: DRAFT

Written: 2026-10-22

Updated: 2026-10-22 — a living field as a standing law for every mission in the arc; the owner's confirmed shape: three missions per outbreak (Colony Breach; the Line or the Road; Broad Front or The Burn), each a single battle, the campaign picking each variant; lifecycle hooks agreed with the bloom lifecycle owner.

Direction for the second and third movements of the Rav Bloom arc that
`colony-breach-nouns.md` (Campaign: blooms, Direction) names: **organising the
defence** and **the heart of the infection**. Colony Breach is the first
movement and is built. This doc proposes the missions, what each one reuses,
what is new, and how the campaign raises them. Nothing here is work yet: each
candidate story becomes a `stories/` doc only when it is picked up. The owner
has picked the concepts for both missions (each a mission with two variants)
and how the campaign raises the arc (three beats per infected world).

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

## Law: a living field

Every mission in the arc is fought on a **living field** (owner, 2026-10-22: "a
lot of these missions should feel like a more dynamic field"). The company is
never the only thing on the map that fights: people of the world, allied and
civilian, are scattered across it on their own business, the swarm finds and
hunts them by the rules it already has, and fights and losses go on away from
the company, which sees a world being overrun rather than a board laid out for
it. The laws that keep this honest:

- **Scattered, not escorted.** What is scattered is the world's, never the
  company's: allies under their own authority (`ai-nouns.md`, Sides), civilians
  as ambient people. Nothing scattered ends the battle, touches the roster or is
  the mission's score; it is read on the outcome where it matters, not won.
- **Prey by the existing rules.** The swarm treats them as it treats anyone:
  allies are prey sources, civilians are found by sensing, and what it kills
  feeds a bore through harvest. No rule is added so the swarm can find them.
- **Their losses are the swarm's gain.** A field that feeds the swarm makes the
  swarm bigger and older, so the living field is part of the mission's pressure,
  and evidence reads it against its control.
- **Those who run, run toward the soldiers.** People fleeing leave by way of
  where the company is (its way in is their way out), so the company's ground
  and theirs cross, and the packs that come for the company cross them too.

Colony Breach's form is the *Living field* in `colony-breach-nouns.md`
(homesteads, militia knots, refugee families, stragglers). The Cordon's
refugees and redoubts and the heart's allied columns are its forms there and
should be built to the same laws.

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

## Mission 2: organising the defence — the Cordon

### The owner's pick

Two of the three concepts, as **variants of one mission** (owner, 2026-10-22:
"I like both The Line and the Road for mission 2. Maybe we have these as
variants?"). The mission is **the Cordon**: the world's military has thrown a
line of redoubts across the country, the bloomed ground lies in front of it,
and the company is hired to help it hold. Its two variants are **the Line**
(the reserve behind the redoubts) and **the Road** (the relief road to a town
cut off beyond them). **The Sortie** (sallying out of a held city to burn
young bores) is rejected: its allies only watch, and it is Colony Breach's
strike detachment made into a mission.

### What the variants share

- **The map.** One precinct plan, Conquest scale (560x336), mission-owned,
  rural. The long axis runs across the front. In front: bloomed farmland and
  settlement, creep across it, bores already at bore and maw tier (the bloom
  has had weeks). The cordon: three to five redoubts as strongpoints with
  picket lines between them, sited on the roads the swarm would use. Behind:
  farmland, the landing field (never in bloomed streets) and the roads allied
  columns come in on. The Road adds one thing to the same plan: a **cut-off
  town** in the bloomed ground beyond the line, and a road to it from a gap in
  the cordon, reserved as a vehicle corridor the way the employer convoy's is.
  It wants the rural road and farm-plot work landing first.
- **The allies.** Allied garrisons man the redoubts by garrison authority and
  fight from their works; allied columns arrive on a schedule along the roads
  behind the line. In the Line they reinforce redoubts; in the Road they are
  the convoys. Either way the company commands none of them.
- **The swarm's pressure.** Nothing breaks a Colony Breach law. Every body
  comes out of a living bore in front of the line, surges are pulses, harvest
  feeds bores, and ground colonies spine any gun the swarm cannot rush, which
  is the swarm's siege of a redoubt left alone. The swarm's goals change: the
  redoubts and whatever people are on the move replace the shelter and the
  lift, and the director spreads its pressure along the line rather than down
  one corridor.
- **The posture.** The hive posture becomes a **line posture**, chosen at the
  briefing and frozen: **Hold** (the company only relieves and escorts),
  **Opportunistic** (the commander may send a strike detachment at the bore
  pressing hardest when its gate is open), **Push** (bores in front of the
  line are objectives). The readiness gate keeps its three conditions with new
  inputs: (a) the line or the road is holding for a sustained window, (b) the
  strength left behind covers every redoubt that has called for relief and
  every group of people on the move, and (c) a capable detachment exists.
- **The bonus.** Each bore collapsed is recorded on the outcome, like the hive
  bonus, and makes the heart smaller (*Tie to the campaign*).

### What differs

| | **The Line** | **The Road** |
| --- | --- | --- |
| The company's role | The mobile reserve behind the redoubts: relieve the one that breaks, plug the gap, raid the bores between the lines when the line can spare it. | The road's guard: clear ahead of the allied convoys, picket the bores that bear on the road, keep the gap in the cordon open. |
| Whose people | Refugees walking out behind the line to the rear edge. | The cut-off town's people, brought out on the convoys' return trip. |
| Win | The region behind the line is evacuated (the last refugee group off the map, or the briefing's timer) with no more than the allowed redoubts fallen. | Enough convoys reach the town and come back out with its people; the gap in the cordon still held. |
| Loss | Too many redoubts fall, or the swarm reaches the evacuation road in force. | The convoys stop (too many lost) or the gap closes behind them. |
| Score | Refugees out, as evacuees are in Colony Breach: the win is people. | Townspeople out, convoys lost. |
| Lineage | `fortified-lines.md` with the company on the holder's side. | The employer convoy, supply route and Thunder Run's road fight. |

### Who chooses the variant

**The campaign, from how Colony Breach went** (*Tie to the campaign*). The
variant is part of the offer's frozen terms, as a posture or a payment is,
and follows the world's state: people left behind in the colony (fewer than
half lifted, or the call never taken) raise **the Road**, to the colony's
cut-off people; a colony got out raises **the Line**, holding while the
region's refugees walk. The player chooses whether to take the beat, not how
a fixed situation is fought, which keeps the briefing honest about what is on
the ground.

### Candidate stories

The Cordon is now its own feature: its model is `cordon-nouns.md` (the map,
the redoubts, the beats and the end conditions of both variants) and its board
is `roadmap/cordon/design/stories.md`. The list below is the original survey,
which those stories now carry.

1. **Cordon mission.** The mission with both variants: briefing (variant and
   line posture), factory, outcome (people out, redoubts lost, convoys lost,
   bores collapsed) and a `commanderEvidence` harness with a fixture per
   variant.
2. **Cordon map plan.** The precinct plan: the front, the cordon's sites, the
   rear, and the Road's cut-off town and corridor as an optional element.
3. **Field redoubt.** The planned `forts.md` redoubt kind (a walled nest
   cluster with a small command post, no ship-grade guns), sized for an
   allied garrison, and a first slice of `fortified-lines.md`: strongpoints
   joined by picket lines.
4. **Allied column.** A scripted allied column on a road, from the employer
   convoy driver made side-generic: it joins a redoubt (the Line) or runs to
   the town and back (the Road). It may be the first user of
   `marine-side-reinforcement.md`'s decisions applied to `ALLY`.
5. **Allied line command.** The `ALLY` commander that mans redoubts by LOCK
   directives, calls a redoubt breaking (beats and radio: the company hears
   the call) and moves its columns.
6. **Company reserve command.** The Marine commander for both roles: the line
   posture, the generalised readiness gate, relief and road picket as
   assignments.
7. **Front swarm goals.** Goals and director pressure over many redoubts and
   people on the move; bores that start grown.
8. **People on the move.** Refugees walking out behind the line, or
   townspeople boarding convoys: the harvest's stake and the score, from the
   colony population and the cohort's walk to the lift.

## Mission 3: the heart of the infection

### The owner's pick

Two variants of one mission, as the Cordon has the Line and the Road: **Broad
Front** and **The Burn**, each a whole battle in its own right (owner,
2026-10-22). Broad Front "gives us the ability to show off large scale
combat"; The Burn "gives interesting 'fire' / 'burning' mechanics which might
be fun to develop and play with. Also could play around with long range
artillery there for a WW1 style walking barrage."

Rejected:

- **Combined** (a broad front advancing as a burn line behind the barrage, in
  one battle): the owner does not want combined missions ("I don't want
  combined missions"). Each variant keeps its own character.
- **The multi-battle operation** (The Burn as linked battles carrying state
  between them, or two burns ending in the front): rejected for the same
  reason. The Burn is one battle.
- **The Spear** (air-landing beside the heart): not taken forward.

### What the variants share

- **The heart** (below): the same objective and the same win, every mother
  bore of the heart collapsed. The company plays it as Purge from the start;
  strike detachments, satchel carriers and machines are the tools in both, and
  the tender and air support can work satellite bores once their spore
  colonies are down.
- **The country.** The same overgrown map kit on the VERDANT palette: forest
  over most of the map, the farms and ruined installation of a settlement that
  went dark weeks ago, the reservoir's old workings at the heart on the far
  side. It depends on **creep over trees and vegetation** (without it the
  heart's creep stops at every forest edge and the country does not read as
  overgrown) and on **rural roads and farm plots**, both in their own
  sessions. Forest canopies block movement, so each plan needs its own
  reachability proof from the start line to every mother bore, one that never
  counts on fire to open the way.
- **The campaign plumbing.** One event family (`BLOOM_HEART`), one factory and
  one resolver, with the variant among the frozen terms, as the Cordon carries
  its own.
- **The score.** Satellite bores collapsed and losses, carried back to the
  bloom's ledger; a heart down contains the bloom.

### The heart

The **heart** is spacer talk for a bloom's root on its world: the reservoir
under the mother bore that Colony Breach did not collapse, weeks or months
grown, out in the country round the colony. In the model it is not a new kind
of thing but a lot of known things at once: two or three **mother bores**
standing on one reservoir, each with her Matriarch at a high stage, satellite
bores round them at maw, and creep over everything within reach, including the
forest. It is the only place a battle starts with more than one mother bore.

The lore holds: collapsing the heart **ends this bloom (and, at a harsher
tier, starves what it seeded), not the weapon**. Text says the heart is down
and the country can be taken back; it never says the Bloom is destroyed or the
world is clean, and the reservoir rests rather than dies. This is the one
mission where the hive is the objective rather than a bonus, and the briefing
says why in work terms: every season it stands, it seeds the traffic again.

### What differs

| | **Broad Front** | **The Burn** |
| --- | --- | --- |
| What it shows | Large-scale combat: many forces converging on the heart at once. | Fire and artillery: the company burns its way in behind a walking barrage. |
| The company's role | The spearhead lane among allied columns advancing on their own lanes under their own commander and holding what they take. | Alone on the ground: the world's military lends its guns, not its men. The company follows a walking barrage into the forest and burns the overgrowth as it goes, so its front is a **burn line** the creep cannot cross back over. |
| Win | Every mother bore of the heart collapsed. | The same. |
| Loss | The company is broken or withdraws; allied lanes falling is a cost on the outcome, not a loss. | The company is broken or withdraws; the barrage runs out on its timetable whatever the company does. |
| Score | Allied losses, satellite bores collapsed. | Satellite bores collapsed, how much of the creep the burn took, the company's losses. |
| Map | The largest the arc uses: 560x336 at least, mission-owned, with the allied start line along the near side and roads for the lanes. | Deep rather than wide: one approach through the thickest forest to the heart, sized for one company and its barrage (400x240 is the first guess). |
| New machinery | Allied lane command. | Fire, firebreaks, incendiaries, the walking barrage, advancing behind it. |

### The Burn: the barrage and the burn line, as laws

- **The guns cannot take the heart.** The barrage is bounded: it lifts on its
  timetable to a last line short of the heart (the batteries' reach, or the
  ammunition the briefing names), and the last stretch is the company's. It is
  the artillery form of *air cannot clear the map*: fire support shapes the
  approach, people finish it.
- **Barrage damage is ordinary damage.** A shell landing on a bore hurts it
  through its ordinary durability, so *bores are fought, not scripted* holds;
  the barrage's value is what it does to surges caught in the open and to the
  creep and growths along its line.
- **Lean into it, or lose it.** Infantry that keep close behind the barrage
  arrive as it lifts and meet the swarm stunned; infantry that lag meet the
  swarm recovered, and the barrage does not wait. The danger-close veto that
  stops a mortar firing on its own side is the model for how close is too
  close.
- **A firebreak holds creep back, for a while.** Burnt ground is not claimable
  by creep until it recovers, so a living source behind the line cannot re-take
  what the burn line has passed; ground a source still reaches beside the
  break is still creep's. Burning never collapses a bore: it takes the creep
  and the growths that stand on it.

### Who chooses the variant

**The campaign, from how the Cordon went**, as the Cordon's own variant
follows from Colony Breach (*Tie to the campaign*). The variant is among the
heart's frozen terms and follows what the world's military has left to give:

- enough redoubts held that it has columns to spare: **Broad Front**, its
  columns in the lanes beside the company;
- too many redoubts lost, in either Cordon variant: **The Burn**,
  because it has guns behind the cordon but no men to send, so the company
  goes in alone behind its barrage.

The player chooses whether to take the beat, not which battle it is, which
keeps the briefing honest about what the world has left. The threshold (how
many redoubts held is enough) is tuning, set when the Cordon's evidence exists.

### What exists for fire and artillery (for The Burn)

| Need | What exists | Gap |
| --- | --- | --- |
| Fire | Presentation only: wreck fire and smoke (`EffectsService`, `ImpactFx`, the smoke-and-fire sheet), weapon fx entries of kind fire, an airframe cook-off that is a one-shot blast (`AirframeCookOffSystem`). | No burning ground, no fire spread, no flammable terrain, no damage over time, no incendiary weapon anywhere in the data. Canopies have no burn state. |
| Smoke | A standing smoke cloud is a partition that cuts sight both ways (`ai-nouns.md`, smoke as a partition); squads carry smoke grenades and can be given a screen. | Fire's smoke is not yet a cloud. |
| Crew-served fire | The fire mission (a cell and a radius) on a squad's order; field mortar sections (52-cell range, set up, serve, pack, lost; friendly veto at launch); standing fire missions and the base of fire (`phased-assault.md`, `conquest-command.md`). | Fire missions are single places; nothing steps forward on a timetable. |
| Off-map fire | Orbital Barrage, a placed blast after a warning (player command power, charges, ship-grade; `command-powers-nouns.md`), locked by an intact hostile battery; ship and air powers; the defending world's hostile ship support. | No off-map ground artillery, and nothing for the `ALLY` side; no creeping or rolling barrage for ground guns (the rack volley in `air-nouns.md` is a launcher's footprint, not a timetable). |

### Candidate stories

The heart is now its own feature: its model is `heart-nouns.md` (the country,
the bores, the flow, beats and end conditions of both variants, the barrage)
and its board is `roadmap/heart/design/stories.md`. The list below is the
original survey, which those stories now carry.

Shared by both variants:

1. **Heart mission.** The mission with both variants: briefing (the variant
   among its frozen terms), factory, outcome, and a `commanderEvidence`
   harness with a fixture per variant.
2. **Overgrown country plan.** A precinct plan: REMOTE sprawl, forest strokes,
   a dark settlement's ruins and the heart's site, wide with lanes for Broad
   Front and deep with one approach for The Burn, each with a reachability
   proof that does not count on fire.
3. **Heart setup.** Several mother bores on one reservoir at battle start, each
   with her Matriarch's stage set by the heart's age, and satellite bores at
   maw: a setup of existing pieces, plus whatever the two-mother case breaks
   (one Matriarch's leash over the other's packs, the director's budget).
4. **Creep on vegetation** (dependency, in its own session).

Broad Front:

5. **Allied lane command.** An `ALLY` commander advancing columns along lanes
   and holding taken ground; Conquest's lanes are the model, rewritten for an
   allied side against a swarm.

The Burn:

Fire itself (stories 6 to 8 below) is its own feature now, modelled in
`fire-nouns.md` with its board in `roadmap/fire/`; the fire field and the
firebreak have landed there.

6. **Fire field.** Burning as simulation state the battle owns, beside the
   creep field: per cell, fuel, burning and burnt. Fire spreads by fuel
   (crops, brush, canopies, dead and living creep), burns out, hurts whatever
   stands in it, and makes smoke that is a partition like a smoke grenade's.
   Deterministic in the seed; drawn through the existing fire and smoke fx and
   a scorch on the ground. Canopies burn and fall to passable burnt ground.
7. **Firebreak.** Burnt ground refuses creep until it recovers (the creep
   field asks the fire field); living creep burns and withers; growths on
   burnt creep die as they do when their creep dies. The burn line is this
   rule moving.
8. **Incendiaries.** Something to light it with, through the existing weapon
   data and orders: an infantry flamer special, an incendiary round for the
   field mortar's fire mission, perhaps an incendiary air strike. Each sets
   cells burning; none is a new delivery system.
9. **Walking barrage.** A barrage plan of lines that lift forward by a set
   distance on a timetable, fired by off-map batteries as a stream of placed
   blasts (the Orbital Barrage's pending detonation, many times, on a
   schedule), with a warning at each line and a last line it stops at. Fired
   for the company by the world's batteries here; usable later by the `ALLY`
   side and, as a command power, by the player.
10. **Advance behind the barrage.** An assignment that keeps a squad a set
    distance behind the current line and steps it forward when the line
    lifts, with the danger-close veto as its near limit. The company
    commander issues it.
11. **Evidence scenes.** `sceneEvidence` scenes for fire spread over a forest
    edge and creep, a firebreak holding against a living source, and a squad
    following a barrage; mechanics asserted, outcomes read.

## Tie to the campaign

### The pick: three beats per infected world

The arc is **per planet** (owner, 2026-10-22: "I like the idea of infected
planets having the 3 mission beat with our variants in there. The player can
choose whether they follow through with all of them."). Every bloom on every
world can run the three beats in turn: **Colony Breach**, then **the Cordon**
(the Line or the Road), then **the heart**. Each beat is offered on its own,
the player opts into it, and the player can stop after any of them. This
replaces the earlier choice between organic offers on a spread chain (which
needed a harsher severity) and a scripted sector threat as the trigger: the
arc appears at the shipped benign tier, on any world that blooms.

**Three missions per outbreak, each a single battle** (owner, 2026-10-22: "I
want 3 missions per outbreak. Mission 2/3 have 2 variants that I decided
earlier."):

1. **Colony Breach.**
2. **The Line** or **the Road** (the Cordon).
3. **Broad Front** or **The Burn** (the heart).

No beat is a multi-battle operation and no two beats merge into one battle;
a variant is one battle, picked by the campaign, never a second mission.

### How a world moves through the beats

| After | The world | Next offer |
| --- | --- | --- |
| Colony Breach, mother bore collapsed | **Contained**: the bloom ends at once (as today). | None: the arc is won early. |
| Colony Breach, half or more lifted | **Starved**: the bloom lingers, its people out. | The Cordon, **the Line** (the colony is out; the region's refugees are walking). |
| Colony Breach, fewer than half lifted, or the call refused or expired | Stays **erupted** (the report changes no stage) until it goes **dark**: forty-five days after eruption, or at once on a refusal or expiry. | The Cordon, **the Road** (the colony's people are still in there, cut off), offered when the bloom goes dark, not when the report lands. |
| The Cordon, won or lost | As before; the bloom is still running. | The heart: **Broad Front** if enough redoubts held, else **The Burn**, with what the Cordon left (below). |
| The heart, every mother bore collapsed | **Contained**: ends at once, the reservoir rests. | None: the arc is complete. |
| The heart, lost | As before. | None. |

So the variant of the Cordon is not a separate pick: it follows from how
Colony Breach went. The Road exists because people were left behind; the Line
because they got out. A call the player could not take (refused, expired, or
held at the gate by another event) does not close the arc: the Cordon can still
be offered to the dark world, so missing the first beat is a worse start, not
the end of the story. The heart is offered only after a committed Cordon.
At the benign tier a dark bloom is put down sixty days after going dark, so
the Road's fourteen-day window has to open inside those sixty days; the
clock hold below protects it only once the Cordon is committed.

### What the player leaves behind by stopping

Declining a beat, letting its offer expire, or losing it ends the arc on that
world, and the bloom runs on the ordinary clock from where it stands. At the
benign tier that is what happens today: a dark bloom is **put down** by the
world's own military sixty days after going dark, a starved one burns out, and
the market keeps the condition until then. The player has lost nothing they
had; they have given up the regard, pay and containment the later beats could
have earned. At a harsher tier the same stop leaves a bloom that eats size and
may spread, which is where following through will start to matter.

### How one beat feeds the next

Outcomes are measured facts on the bloom's ledger, written by each beat's
report and read by the next beat's setup:

- **Colony Breach → the Cordon.** The evacuation picks the variant (above).
  Bores collapsed stay collapsed: the Cordon's front starts with that many
  fewer bores. Biomass the bores were fed (people not lifted) shows as an older
  age mix and higher tiers in front of the line, so a bad rescue makes a harder
  defence.
- **The Cordon → the heart.** Redoubts that held pick the variant: enough of
  them and the heart is Broad Front, with those garrisons as the allied
  columns in its lanes; a line that fell makes it The Burn, the company alone
  behind the world's guns. Bores collapsed in front
  of the line are satellite bores the heart does not have. People brought out
  (refugees or townspeople) are regard, not strength.
- **Across the arc.** The mother bore that Colony Breach did not collapse is
  the heart's root: the heart's mother bores are hers and what grew from her
  reservoir in the weeks since, and their Matriarchs' stages follow how long
  the bloom has fed.

### Time, and the one-open-event rule

A bloom's own clock leaves room for three beats, with two hooks agreed with the
session that owns the bloom lifecycle (`RavBloomSystem`, *Campaign: blooms*):

- **The clocks.** The call window is fourteen days; an erupted bloom goes dark
  forty-five days after eruption with nobody lifted; at benign a dark bloom is
  put down sixty days later, and a starved one lingers ninety. A Colony Breach
  fought around day twenty leaves a dark bloom about eighty days and a starved
  one ninety: enough for two more fourteen-day windows and the travel between,
  but not with slack.
- **Hook: a committed beat holds the clock.** While one of a bloom's beats is
  committed and unreported, its end-by-clock checks (put down or burned out,
  starved out) are skipped. When a beat settles, the bloom's stage deadline
  becomes the later of its own clock and the settle day plus the next window
  (fourteen days) and a travel allowance (thirty). The world's military waits
  for the company it hired. Today a call still open when its bloom ends is
  closed as expired; under this hook a committed beat is never cut off that
  way. The cost: an arc's bloom holds one of the two running places longer, so
  fewer new blooms wake while an arc runs.
- **Hook: the next offer opens as the last one settles.** The shared gate lets
  only one campaign event be open at a time. The handoff happens in the
  bloom's daily tick that reads the beat's report (where it already reads the
  rescue's terminal state), not inside the mission's resolution. The bloom
  system ticks after the event lifecycle and before every other event source,
  so the next offer is raised before any other source ticks that day, and a
  report arriving between ticks cannot be pre-empted, since nothing raises
  events outside the tick. The system order test pins it.
- **The cost to other blooms.** A committed beat is an open event, so an arc
  holds the gate for its whole length, and other blooms' calls lapse as they do
  behind any open event. At the default *frequent* rate (about four and a half
  blooms a year) an arc of about 120 days sees about 1.4 other calls arrive,
  most of which lapse. A lapsed call sends its bloom dark, and a dark bloom can
  still offer the Cordon (the Road) once the gate frees inside its sixty days,
  so a lapsed call delays the content rather than losing it. `bloomEvidence`
  measures held calls once the arc exists.

### Campaign plumbing

Each beat is the bloom's next call, never an event without a bloom behind it,
as the rescue is today.

- **Families.** Each beat is its own event family under
  `campaign-event-nouns.md`'s extension laws: `BLOOM_CORDON` and `BLOOM_HEART`
  are appended to `CampaignEventType` rather than overloading the rescue, each
  with its own frozen commitment (the variant, the posture, the inherited
  bores and allies) and its own terminal report. The bloom is their only
  source, so there is still no parallel scheduler.
- **Ledger.** A nullable arc record on the bloom holds the current beat, each
  beat's event id and its report (share lifted, bores collapsed, redoubts
  held). Colony Breach keeps the bloom's existing rescue event id; the Cordon's
  and the heart's live on the arc record. Older saves read it as none.
- **Missions.** A factory and a resolver per beat, the resolver writing its
  report back to the bloom exactly once. `PolityDefenceMissionFactory` is the
  nearest model for the Cordon.

### The sector threat

No longer the trigger. It stays as an optional, later **authored instance** of
the same three beats: a named world, woken with `RavBloomSystem.wakeAt`, whose
arc runs with fixed, harsher terms (a stronger heart, a set cordon, story
briefings) whatever the severity preset. It costs one authored setup over
machinery the ordinary arc already needs, and nothing in the ordinary arc
waits on it.

## Open questions for the owner

- What collapsing the heart pays beyond regard, and whether a contained heart
  rests its world longer than an ordinary containment.
- Whether the arc runs on the player's own colony when an exposed player
  colony blooms (the nearest thing to a home defence the arc could offer).
