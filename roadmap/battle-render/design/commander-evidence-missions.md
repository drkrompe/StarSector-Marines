# Commander evidence: per-mission detail

Status: ACTIVE

Written: 2026-10-20

Updated: 2026-10-20 — conquest-trial reports the defender's place counter-attack: probes, observations, launches and retakes by fort and compound.

Per-mission matrices, fixture fields and report contents for `gradlew.bat commanderEvidence -Pmission=<mission>`. How to run the task, the mission list, replay count, parallelism and snapshot flags stay in `CLAUDE.md` (Build & deploy); read this when you need one mission's details. Text is moved verbatim from `CLAUDE.md`; "Fixture fields match Relief" and similar refer to the sections below.

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
and remaining durability, vehicle repulsion pushes, and the column driver's
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
Reports separate living militia from their assembled field guns. Default
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
of each; `-Dbattle.conquest.fortCounterattack=false` is its control.

## Conquest fixtures and fort control

Each fixture row reports fighting-position runs and cells at start and end.
The canonical fixtures declare no forts and so have none; the fort-bearing
control is `-Pfixture=src/test/resources/battle-fixtures/conquest-forts-reinforced-south-v1.json`
(four battery forts and a shield fort; about 50 runs, 5,200 cells).
The fort report carries a
`pressure flow:` line and a fighting-position use block per faction. Use
