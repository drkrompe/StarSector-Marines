# The Heart — story beats (candidates)

Status: ACTIVE. The owner picked every beat (2026-10-23). Every beat is in the heart's optional-objective pool (`HeartObjectives`).

Written: 2026-10-23

Step 1 of `heart-mission-depth.md`. `heart-nouns.md` is the model. These are
the moments a player should remember from each variant, beyond the bores
falling in turn. Maps (step 2) and forces (step 3) exist to stage them, so a
beat that needs ground or a force names it here.

Every candidate keeps the heart's laws:
- bores are fought, not scripted;
- the guns cannot take the heart, and the barrage does not wait;
- allies never end the battle.

A beat is a situation the simulation plays, not a cutscene. The beat key is
what the story sequence publishes when it happens.

## Broad Front: many forces converging

**F1. The lost column.** A militia column that went in before the
offensive is cut off in the market town. Its radio is still on, holding a
walled compound with a few teams and a wrecked APC. The nearest lane can
divert to break through, or the company can. A column that reaches it adds the
survivors to that lane. Left alone, the holdout falls at a fixed point in the
battle's clock. Needs: a holdout compound in the town (step 2), the survivors
as allies (step 3). Beat `holdout_reached` / `holdout_fallen`.

**F2. A lane breaks.** The heart's waking surge falls hardest on one lane. Its
column breaks and the front has a hole beside the company. The company is
told; plugging the gap or holding its own line is the player's choice. The
allied commander re-aims the next wave to close it (`heart-allied-commander.md`,
Re-aiming a wave). Builds on `lane_broken_N`; beats `wave_reaimed_N_from_M_n`,
`wave_sent_N_from_M_n`, `gap_closed_N_n`.

**F3. Spores ground the air.** Close air support is promised but grounded
while the satellites' spore colonies stand over the approach. Each colony
burnt or killed opens a corridor, and the first CAS run along it is the
beat. Needs: CAS as an allied tool (step 3), spore colonies as the gate
(already Colony Breach's). Beat `air_corridor_open_N`.

**F4. The Matriarch's charge.** The first elder Matriarch out of a woken
mother goes for the nearest lane's armour, not its infantry. It is an armour
duel the player watches from the company's flank, and the company can
satchel her while she is engaged. Builds on `matriarch_emerged_mother-N`.
Needs: Matriarch target choice that favours hulls (swarm side).

**F5. Linkup at the workings.** The lanes come into sight of each other
across the reservoir yard, and the last push on the mothers is every column
at once. The battle's set piece. Needs: the workings laid out as a yard the
lanes converge on (step 2). Beat `linkup`.

**F6. The supply road.** The country road is the front's one metalled road,
and the armour waves and ammunition come up it. A satellite near the road
cuts it with surges until it falls, so taking that bore is what keeps the
fresh armour coming: while it cuts the road the base restocks no armour or
trucks (`heart-nouns.md`, Logistics base), and the gunship counts it a target.
Beats `road_cut_N` / `road_open`.

## The Burn: fire and the guns

**B1. Ranging shots.** H-hour opens with ranging smoke on the first line
before the incendiary shoot, so the player sees where the walk starts.
Settles `heart-barrage-read`. Beat `ranging`.

**B2. The village still has people.** The village at the infection's edge is
on the barrage's third line, and someone in it is still signalling. The guns
will not wait. The company has until that line opens to reach them and bring
them out, or the burn takes the village. The reward is regard, never
strength. Needs: a holdout house in the village (step 2), civilians as
evacuees (Colony Breach's). Beat `village_signal` / `village_out` /
`village_burned`.

**B3. They go for the guns.** A bore's surge breaks out along the burnt
flank toward the battery, not the company. The gun pits hold or they don't,
and the company can detach a squad back to the line. A battery overrun is
already the loss, so this is the moment that makes it real. Needs: a breakout
route the map offers toward the guns (step 2). Beat `battery_threatened`.

**B4. The ford.** The brook is the burn's natural break, and the fords are
where the swarm comes through and where the company must cross. The barrage
lifts past the brook and the crossing is the company's alone, at a stony ford
under the far bank. Needs: one ford placed as the company's crossing (step 2).
Beat `ford_crossed`.

**B5. One more line.** The battery is low. An ammunition convoy comes up the
farm road mid-battle. If it gets through, the plan gains one more line, the
barrage walks a sixteenth of the approach further, and the briefing said so.
If a surge takes it on the road, it doesn't. The guns still never take the
heart; the extra line stays short of it. Needs: a convoy on the farm road
(step 3). Beat `ammunition_up` / `ammunition_lost`.

**B6. The guns fall silent.** The last line lifts, and the smoke and the quiet
are the company's alone for the last stretch. It is a pacing beat, not a
mechanic: the presentation marks it (`barrage_ended` already exists).

## As optional objectives

Each beat becomes an optional objective (`optional-objectives.md`) that a
battle may roll when its map laid the place it needs, with a reward on
success and a cost on failure. A rural battle runs one or two, a town or city
two or three, drawn from the battle seed over what the map laid.

| Beat | Key | Needs | Succeeds | Fails | Reward / cost |
| --- | --- | --- | --- | --- | --- |
| F1 | `lost_column` | `holdout` | the company reaches it | seven minutes pass first | credits and supplies / one more satellite |
| F5 | `linkup` | `reservoir-yard` | the company and an allied lane in the yard together | never; lapses | credits |
| B2 | `village` | `village-holdout` | the company reaches it | the barrage's third line opens first | regard |
| B3 | `guns_hold` | `gun-flank-1` | every gun standing at the end | the first gun lost | credits / the battery weakened |
| B4 | `ford` | `company-ford` | the company at its ford | never; lapses | credits |
| F2 | `plug_the_gap` | nothing | once a lane breaks, the company in its ground within 90 s | the 90 s pass | credits |
| F3 | `air_corridor` | `ammo-dump-1` | the colonies of the two satellites nearest the company all dead | never; lapses | credits |
| F4 | `matriarch_down` | nothing | the first Matriarchs out dead within 150 s | she lives longer | credits |
| F6 | `road_open` | `supply-road` | the satellite nearest the road collapses | the battle ends first | credits |
| B1 | `clear_of_the_walk` | nothing | the barrage ends without a gun holding its fire for the company | the first held round | credits |
| B5 | `one_more_line` | `vehicle-park` | an ammunition truck reaches the guns before the last line lifts: the plan gains one more line | every truck lost, too late, or the battle ends first | credits |
| B6 | `last_stretch` | nothing | every mother down within four minutes of the barrage's end | the four minutes pass | credits |

The air beats bring the Harrier in under the flier's law (`colony-breach-nouns.md`,
Harrier): a pack flies only where the company or an ally has anti-air.
- B3 digs a flak position among the guns. When the barrage's second line opens, a pack rises on the flank toward the battery, on the side of the nearest living bore, and `battery_threatened` is published.
- F3 digs one at the ammunition dump. While a target satellite's colonies stand, one raises a pack whenever the company or an ally comes within a Harrier's sense of it: at most three packs, two minutes apart.
- With no anti-air standing, no pack rises.
- The base's own flak (`heart-nouns.md`, Logistics base) already stands at the dump and the battery, so these beats find it rather than dig a second, and the satellites' brood spires fly over every heart battle whether or not a beat rolls.

Two beats are read as objectives the moment does not say outright. B1's
ranging smoke is presentation, so its objective asks what the walk asks of
the company: follow close, never close enough to stop a gun. B6's quiet is
presentation too, so its objective is the finish inside it.

B5's trucks are the shared supply delivery (`allied-tools.md`), sent from
the vehicle park's tool shed with the guns' own trucks: two are sent when the
third line opens, waiting while the shed has none in stock or the swarm is on
the park, and the first through refills the battery as well, and drive whatever way the planner finds rather than the farm road.
The extra line is as deep past the last as the plan steps, still short of the
heart; the company's screen keeps to the plan it was given, so it does not
follow the extra line.

Not fully staged yet:
- F4's hull-seeking Matriarch needs the swarm-side behaviour it names. Its objective runs without it.
- F3's close air support is step 3.
- The lost column's survivors do not join a lane yet.

The shared mechanism that rolls and runs them is `optional-objectives.md`.

