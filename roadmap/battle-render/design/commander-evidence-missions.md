# Commander evidence: per-mission detail

Status: ACTIVE

Written: 2026-10-20

Updated: 2026-10-21 — story summaries carry the allied party's manning and locks (story manning); Conquest traces name the garrisons a counter-attack takes to sortie; the nine story missions without a planner of their own have harnesses (story missions without a planner); every battle harness about places prints a `[place-fight]` line, and story summaries carry it and the story commanders' manning.

Per-mission matrices, fixture fields and report contents for `gradlew.bat commanderEvidence -Pmission=<mission>`. How to run the task, the mission list, replay count, parallelism and snapshot flags stay in `CLAUDE.md` (Build & deploy); read this when you need one mission's details. Text is moved verbatim from `CLAUDE.md`; "Fixture fields match Relief" and similar refer to the sections below.

## Places and fights (every harness about places)

Conquest, conquest-trial, Relay Nine, Thunder Run, Raid, Assault, Sabotage and
Extraction print one `[place-fight] <run> <json>` line per battle
(`PlaceFightProbe`; conquest-trial also puts it in `summary.json` as
`placeFight`). `fightMix` counts, per shooting faction, every ground shooter
that fired in a thirty-tick window once, as it stood at its last shot, with
each end **on a post** (dug in, on a fighting-position cell, under an intact
roof, or an emplacement) or **in the open**: `openOpen` is a brawl,
`openAtPost` an assault on a post, `postAtOpen` a defence from one, `postPost`
posts trading fire. `counterattacks` lists each run of samples in which a side
had counter-attack launch orders: whether a launched squad came within 24
cells of a hostile squad at all (`met`), of one on a post (`metPost`), of one
holding taken ground or a story gain (`metHold`, `metDugInHold`), what it lost
and whether a place was won back in the window (from the trace's retake notes
or a compound flipping back to the defender). `hold` counts holder
squad-samples and those with a counter-attack squad of any stage within 40
cells. Read a run against its control run: same fixture, one `-Dbattle.*`
switch, `-PevidenceDir` set apart. A Conquest trace's `draws garrisons out to sortie` notes name
the garrisons a counter-attack took (`conquest-command.md`, garrisons sortie;
`-Dbattle.counterattack.garrisonSortie=false` is its control). The figures
these lines produce live with the mechanisms they measure: `garrisonables.md`
(fight mix by mission) and `conquest-command.md` (a place the defender loses).

## Colony Breach (`colony-breach`)

One fixture per hive posture (`colony-breach-nouns.md`, Hive posture), side by
side: the canonical colony (Evacuate, no posture key), Opportunistic with two
Vulcans standing at the lift from the start (`liftHardpoints`, a fixture
statement for a tender's finished builds) and six boats rather than four, a
company with strength to spare, so the readiness gate can open,
Opportunistic without them so it is seen to hold, and Purge. Each writes its own
folder under the report; the top `summary.md` tabulates posture, end, evacuated,
commits, recalls and whether the mother collapsed. Asserted as mechanics: every
commit was made with the gate open, Opportunistic only with all three
conditions met, every recall with it closed, Purge commits only at the mother
once she is open, and every commit and recall is its beat. Who collapsed what is
a reading; read it against `-Dbattle.colonyBreach.strikes=false` on the same
fixture. `-Pfixture` plays one fixture alone.

## The heart (`heart`)

Both variants of the heart (`heart-nouns.md`) at seed 202, four boats of six:
`heart-front-v1` (Broad Front, 560x336) and `heart-burn-v1` (The Burn, 400x240),
side by side, 27,000 ticks by default. Each folder holds the trace
(`heart.jsonl`), `summary.json` and `summary.md`: the end and winner, whether
the heart woke, mothers and satellites collapsed, marines and swarm left, and per
bore its cell, tier, integrity, surges, bodies, Matriarch and collapse tick, then
the beats. Asserted: every beat is the heart's, no mother surged while the heart
slept, Broad Front's first satellite collapse woke the heart on its tick, and
the heart was down exactly when every mother had collapsed. Who won is a reading.
A heart fixture may carry `density` (`RURAL`, `TOWN`, `CITY`) and an
`objectives` list of optional-objective keys, which runs those beats instead
of the battle's draw, each still only where its place was laid.

## Relief

Relief's default matrix is three verdant-surface production maps, each played
four ways within a 200s observation window. The story chapter's **ground start**
puts twelve named starter recruits (surplus Rooks and Cordon shells) ashore at
tick zero on the rear apron beside Wayline's parked hauler, with only the
employer's militia shuttle flying; a **held** run keeps that company at its
start for 30s under a diagnostic player order, then hands it back to the
mission; **boat-lift** is the repeatable posting's shape, one standard Hermes
making three four-seat trips; **no-player** is that boat arriving after the
window. Ground-start rows assert the start's mechanics: twelve named marines in
one led squad, no company aircraft, one employer shuttle, one untouched civilian
hauler, and the original militia firing before the company reaches the gate.
Its `-Pfixture` takes a JSON array of objects with `id` and `seed`. Optional
`groundStart` (default true unless `playerType` is given) selects the story
start, with optional `companySize` (default 12) and `holdAtStart` (seconds).
A lift fixture sets `groundStart: false` with `playerType`, `playerCycles`,
`playerSeats` and `playerDelay` (additional seconds); optional `playerBoatId`,
`playerPlating` and `playerDrive` pin the boat's identity and catalog fitting ids
(default: `relief-boat` and standard fit). Optional `surface` selects the terrain
palette (custom fixtures default to `ROCK`). `starterCompany: true` applies a
detached new-company roster through the campaign deployment overlay; a ground
start always does, a lift fixture that omits it keeps the factory loadout.
Employer lift remains one six-seat Aeroshuttle. Reports include each recruit's
arrival, company strength at that moment, full-squad assembly, company arrival
at the gate, first company fire and loss, losses, employer gate arrival,
committed road-arrival times and pending raider counts, and separate original
militia from arriving allies; timeout is not defeat. This is controlled
construction with a detached roster, not a live Jangala save capture.
The Relief task also writes `campaign-lift.json`, through production scenario
capture with a twelve-person named company at two seeds. Story Relief must stand
all twelve identities ashore at tick zero in one led squad beside the hauler,
with no company boat flying, and then withdraw with no boat on the map and no
fabricated losses. Counterattack, the first ferry battle, runs standard and
upgraded Hermes fittings and verifies three physical trips, all twelve
identities landing, and their reunion into one squad. This uses a detached
starter roster, never a live campaign save.

## Counterattack

Counterattack's default matrix has three verdant depot interiors, each with
ordinary, +30s delayed and absent-player controls (300s observation), plus a
paired-boat case. Ordinary lift is two six-seat sorties on one fitted boat;
paired lift is one sortie each on two distinct fitted boats. The employer
contributes one six-seat boat. Fixture fields match Relief's lift fixtures, with optional
`playerBoats` (default 1). Reports include gate/yard/warehouse vicinity times,
defender orders and casualties; vicinity is not capture. The harness verifies
that the production air entities retain each committed player airframe. These
are factory-loadout controls, not current campaign roster acceptance.

## Junction

Junction's default matrix flies the lift a company has at that point in the
campaign: three interiors with two Hermes making three four-seat trips (24),
one Hermes making six, and one Hermes making three (12, understrength), plus
militia-held-at-landing and absent-player controls.
`-Dbattle.junction.talonTwo=false` is the no-gunship control for the trenches.
`-PsnapshotEveryTicks=N`
records a whole-map `review.gif`; add `-PsnapshotFocus=x,y,cellsAcross` for a
close view instead (the Weigh Station is about `72,50`). `-Pmission=junction`
replays the production finite territorial factory; it does not create a campaign
offer. Custom fixture fields are `id`, `seed`, `playerType`, `playerBoats`,
`playerSeats`, and `playerCycles`, plus optional fitting ids and `playerDelay`.
`holdAtLanding` is an explicit diagnostic player-order control that keeps the
employer force at the apron. Reports include territory reversals, actual boat
identities, casualties, accepted story beats and reserve positions. Source
frames use 1920x1080. Timeout remains unresolved, not a win or a defeat.

## Relay Nine

Relay Nine uses `-Pmission=relay-nine` for its finite capture/hold factory.
Its eleven cases cover three interiors at two/four squads, a first-Gate
approach, militia held at landing, one fitted ferry, refitted boats and absent
player arrival. Fixture fields match Junction, with `gateFirst` selecting an
explicit diagnostic player-order approach. Reports include captures, enemy
recaptures, restoration, actual militia hold beats and the one eight-person
road reserve. Source frames remain 1920x1080. These are factory-loadout
battle controls; campaign offers, roster launch and settlement are separate.

## Supply route (`supply-route`)

`-Pmission=supply-route` checks the campaign construction/roster-overlay seam
for Junction, Thunder Run and recurring Route Security on the Relay Nine map
with eight/twenty-four named marines.
It verifies fitted airframes, exact named seats and untouched employer/civilian
craft across mixed lift, paired six-seat boats and repeated single-boat trips.
Every committed landing is observed (up to 300 seconds), checking one battle
squad per campaign squad, NCOs, fire teams and completed assembly; it renders
1920x1080 PNGs at the final arrival.
It is not a live campaign save or a full-mission balance replay. Broker/HQ flow
evidence uses `createSnapshots -Psnapshot=supply-route` at 100% scale.

## Power-suit campaign (`power-suit-campaign`)

`-Pmission=power-suit-campaign` checks a detached twelve-person mixed section:
explicit recovered-template acquisition, ordinary fitting, two six-seat ferry
trips, real combat, personnel writeback and Java roster serialization. It
writes `build/reports/commander/power-suit-campaign/summary.json` and explicitly
leaves live Starsector save/reload and host settlement receipts unverified.
Default observation is 9000 ticks; an unresolved fight ends with controlled
retreat. This is equipment-loop evidence, not acceptance of a story reward.

## Corvus

`-Pmission=corvus` plays the three fixed Corvus chapters with their recommended
named companies, starter squad equipment and recovered chaingun-only Hound.
It requests normal Mech Support at the first legal pad, then leaves command
autonomous. Each chapter starts fresh; no campaign casualty carryover or officer
bonuses are assumed. Default observation is 18000 ticks with two Aeroshuttles.
`-Dbattle.corvus.boats=4` selects a different allowed lift count and separate
output folder; `-Dbattle.corvus.chapter=BLIND_WATCH` selects one chapter.
`-Dbattle.corvus.machines=LRM_CARRIER,FIRE_SUPPORT_APC` puts those vehicles on
the lance's gantries beside the Hound; the run calls each down after it, one
machine per order.
Reports retain exact launch fixtures, losses, delivery times, mech output,
command traces, minute frames and unit/route diagnostics. Corvus also accepts
`-PsnapshotEveryTicks=15` for denser action replay frames with weapons, impacts
and shadows; phase-change frames are always retained. Unit/route dumps keep
their minute/phase cadence independently of the frame interval. Outcomes are findings,
not victory assertions: a successful task does not certify mission balance.
For `COLD_ACCOUNT`, `-Dbattle.corvus.damageReview=true` also records a compound
overview GIF (five times simulation speed), game-scale detail stills and a
before/after prop inventory under `prop-damage/`. It observes the real replay;
it never supplies targets or damage. Inventory scope is initial positive-cover
doodads centered inside the strike circle. `damagePair` means both state assets
exist, not proof that a runtime damage channel registered the prop;
`groundDamaged` records the terrain tag, not a direct hit on the prop.

## Thunder Run

`-Pmission=thunder-run` uses named campaign squads and fitted boats through
the production launch seam on the 320x144 freight corridor. Three ordinary
interiors, absent arrival, total cargo loss, partial-cargo retry and confirmed
withdrawal controls report physical exits, losses, phase times, once-only
settlement and story hooks. Frames are 1920x1080 at 100%; close views change
camera framing only. Optional fixtures select `id`, `seed`, `absent`,
`destroyCargo`, `retry`, `retreat`, `playerDefend` (places Defend Area during
both dispatch windows and replaces it with a move afterward), or `unarmedFinal` (diagnostic empty escort
magazines on the final leg), or `silentTankFinal` (only the heavy lead
loses its cannon ammunition). Reports include exit-gun destruction ticks and actual escort shots, damage, kills
and remaining durability, vehicle repulsion pushes, the rising-pressure waves sent (`pressureWaves`), and the column driver's
reroutes, wreck/parked-hull swerves, station waits and hauler sprints.
`holdDamageTaken` says who absorbed the raiders' fire in phases 1-2 (marines,
militia infantry, militia guns, each hull by structure+armor lost), split by
phase and hold/push; `holdDamageDealtByRaiders` splits the shooting into
garrison, waves and emplacements.
`companySize` selects named personnel (default 24). `-PsnapshotEveryTicks=N`
records `review.gif` (whole map every N ticks), `crossroads.gif` (a fixed
camera on the first crossroads through both holds, with the militia guns and
technical labelled) and `convoy-run.gif` (a stepped follow camera on the column
through the final run) per fixture.
Reports separate living militia from their assembled field guns. The
withdrawal control retreats at its first delivery and asserts the frozen
ledger (no pay, the undelivered cargo still waiting) only when that retreat
fired; `retreatTick` is -1 when the convoy was lost before anything got out,
which is an ordinary defeat and leaves the control unexercised. Default
observation is 27000 ticks.

## Conquest trial (`conquest-trial`)

`-Pmission=conquest-trial` plays the same Conquest mission layer and both
commanders on a synthetic 240x300 trial ground (`ConquestTrialGround`): one
landing, one battery fort pinned on the lane or offset east of it, one
objective compound and the keep. The default matrix is both placements at
three seeds with a combined-arms force (sixteen six-seat Aeroshuttles, the
Conquest squad kit, two lances, a small air pool; fort-on-lane runs to 18,000
ticks), plus a rifles-only control per placement (four twelve-seat Valkyries,
the old default), which shows freezes a bigger force hides. A fixture may set
`maxTicks`; `-PmaxTicks` overrides all. `-Pfixture` takes a JSON array setting
`seed`, `placement`, `offset`, `shuttleType`, `shuttles`, `seats`, `cycles`,
`elites`, `militia`, `patrolSize`, `objectiveGarrison`, `keepGarrison` and
`risk`, and optional `airDefence` (true stamps the fort's anti-air yard). Use it for a commander
question; the city map is unfinished. Each fixture's fighting-position block carries a
`DEFENDER garrison focus` line: belt-garrison samples taken while the company
stands at a fort and how many of them have no marine within thirty cells, split
into those in a fight, those with a believed contact that near, those still
marching (and of those, marching to an order cell a marine is near: to the
fight), with the nearest believed contact in fifteen-cell bins; the **stranded**
share (arrived on a stretch no marine is near, or marching to one) is the
garrison-placement reading. Also stretch shifts (a garrison order moved to another
stretch of its run), how
often a rallied squad's post moved and how often it moved back to the one before,
and the defender's marches (a new order cell thirty or more cells off) with
those of a thousand ticks or more, arrivals and members lost on the way. A `MARINE holds taken ground` line (and `attackerHold` in
`summary.json`) says when the fort was whole in marine hands and whether it was
won back, and how the attacker held its belt: manning orders, holder
squad-samples, the most at once, dug-in member-samples and fall backs;
`-Dbattle.conquest.attackerHoldsGround=false` is its control.
A `DEFENDER counter-attacks its lost fort` line (and `counterattack` in `summary.json`) counts the
defender's counter-attack reserves, musters and launches (squads and strength at each launch against
the strength it believed it needed), retakes, failed attempts and forts abandoned, with the first tick
of each; `-Dbattle.counterattack=false` is its control.

## Conquest fixtures and fort control

Each fixture row reports fighting-position runs and cells at start and end.
The canonical fixtures declare no forts and so have none; the fort-bearing
control is `-Pfixture=src/test/resources/battle-fixtures/conquest-forts-reinforced-south-v1.json`
(four battery forts and a shield fort; about 50 runs, 5,200 cells).
The fort report carries a
`pressure flow:` line and a fighting-position use block per faction. Use

## Story missions without a planner

`yard-owners`, `water-under-seal`, `limited-warrant`, `district-escort`,
`terms-of-protection`, `small-war` (A Small War at Black Sluice),
`salvage-rights`, `cold-start` and `working-passage` share one harness
(`StoryCommandEvidenceTest`, one tag per mission). Each fixture stands up the
mission's production factory with its own story commanders on every side, puts
the company the mission's snapshot suite issues into two six-seat Aeroshuttles
(the Agreus company from the debug catalog's expected roster, the A Working
Ship company from its starter roster; ashore where the mission starts it on the
ground) and plays it with no input until it ends or its tick budget runs out.
The exception is a mission that waits on the player's ship bar: Water Under
Seal and Terms of Protection call Ship on Station once the plot is seen and
order the road gun, Cold Start requests yard construction, Working Passage
orders a gun at each stop, when the snapshot suite does, and a gun order is
given again every 150 ticks until the ship command service reports it laid.
Every such request is listed in `playerInputs`.

Default matrices are four fixtures (six for A Small War): three seeds at the
suite's company size and one at the other size (24 or 36), with Limited
Warrant's records-retained variant and the custody / compact agreement where
the mission has one. `-Pfixture` takes a JSON array of `{id, seed, personnel,
variant}`; `variant` is `custody`, `compact` or `records`. Each fixture's
`summary.json` has the termination, winner, ticks, final phase and the phase
walk, the receipt (`report(sim)` as text), remaining and lost combatants by
side, `playerInputs`, `storyBeats` with audience, `placeFight` and
`reactiveDefence`; the mission's `summary.md` is one row a fixture. A matrix
plays in under a minute and a half.

Baselines (2026-10-21, before any of the nine was wired):

| mission | fixtures | outcomes | brawls | defender shooting from a post |
|---|---:|---|---:|---:|
| The Yard's Owners | 4 | marines 4 | 0% | 93% |
| Water Under Seal | 4 | marines 3; 15301 times out at construction with the gun laid | 45% | 40% |
| Limited Warrant | 4 | marines 4 | 3% | 96% |
| District Escort | 4 | marines 4 | 59% | 37% |
| Terms of Protection | 4 | marines 4 | 89% | 5% |
| A Small War | 6 | marines 5, Greyhook 1 (16205 at 24 seats) | 39% | 51% |
| Salvage Rights | 4 | marines 2, Greyhook 2 | 57% | 22% |
| Cold Start | 4 | marines 1, Greyhook 2, one timeout at power (36 seats) | 85% | 8% |
| Working Passage | 4 | Greyhook 4 (the starter company) | 90% | 3% |

## Story manning

Every story fixture's `summary.json` (Junction, Relay Nine, Relief,
Counterattack, Last Lift per attempt, Thunder Run per fixture, and the nine
story missions without a planner above) carries
`placeFight`, the `PlaceFightProbe` reading above, and `reactiveDefence`, the
defender commander's manning figures with, for a story commander, the
squad-pulses it offered and the pulses its holder share was closed
(`garrisonables.md`, story commanders). Read both against
`-Dbattle.story.reactiveDefence=false` on the same build.

Where the allied party's commander has places of its own (`garrisonables.md`,
parties: Thunder Run, The Yard's Owners, Limited Warrant), `reactiveDefence`
also carries `ally`: the same manning figures for the allied party, plus its
`directiveActivations`, `directiveReleases` and `lockedSquadPulses` (squad-pulses
spent on a locked position). The story table adds the allied shooters' share
from a post and the allied manning orders and locked pulses. Read them against
`-Dbattle.party.places=false` on the same build, which removes the allied
picture, the allied manning and the hold directives and keeps the locks that
re-express an authored duty (Thunder Run's militia).
