# Blind Watch — the Sounding

Status: IN PROGRESS — hook A chosen (owner, 2026-10-26); the map (`BlindWatchSounding`, after its second, detail pass), mission behaviour (`BlindWatchSequence`) and wiring (`BlindWatchBattleFixture`) are built; the measured runs, tuning and the preview video remain.
Written: 2026-10-26

Read `wayline-corvus-arc.md`, `early-operation-nouns.md` (Wayline across Corvus),
`sabotage-command.md` and `dust-ledger-crossing.md` (the worked example of an
authored revamp) first. Map work also reads `mapgen-nouns.md`, `elevation-nouns.md`,
`forts.md` and `ground-defences.md`; mission work reads `mission-command-nouns.md`
(Side objectives) and `battle-radio-nouns.md` (Battle music).

## Why

The shipped chapter is a procedural Sabotage battle (`SabotageBattleFixture`
from `CorvusChapter.BLIND_WATCH`): a REMOTE settlement grown on ROCK ground,
three interchangeable named sites, a finite light garrison, and one briefing
paragraph. Its headless baseline wins but loses a third of the company, and
nothing in it says why this post, on this moon, matters.

The arc calls Blind Watch its **confidence crest**: intelligence and
preparation let the company choose the target. This story rebuilds it as an
authored map and a mission whose three sites are **not interchangeable**, so
the order the player takes them in is the decision.

The chapter's place is unchanged: Warion, Low risk, three squads recommended,
30,000 credits, after The Dust Ledger and before A Cold Account. Every victory
debrief still says the post is silent and the routing tables point to Garnir.
The two recorded debrief lines (`debrief.blind_watch.initiative`,
`debrief.blind_watch.ready`) stay valid.

## Fiction

**Warion** is a small barren moon of the gas giant Barad, airless and
uninhabited (vanilla: `corvus_IIIb`, type `barren`). Its sibling moon Garnir
holds the Garnir Extraction Depot and, in this arc, Sable Wake's dispatch
compound. Nobody governs Warion and no patrol lands there.

**The Sounding** is Sable Wake's temporary listening post, set on the rim and
floor of a small crater on the hemisphere that faces away from Barad, so the
gas giant never blocks its view of Jangala's departure lanes. It is a season's
work in prefab modules: a dish field that listens to Wayline's departures, a
ranging mast that watches the ground and the sky close by, a tight-beam uplink
that passes what it hears to Garnir, a hab cluster, a lander pad, and rover
tracks between them.

**Who is really there.** Everyone lives in suits and pressurised modules.

- Sable Wake's post crew: a watch boss and about two dozen security hands at
  Low quality, in the bunkers, on the rim guns, driving the post's crawler and
  its rocket truck.
- Hired signals technicians (four or five): contract specialists who run the
  dishes and the decryption racks. Not fighters; they shelter in the hab.
- A rigging crew pressed into staying on to keep the dishes aligned in the
  thermal swings. They built the post on a contract and were not let go.

**The cold drop (owner's flavour idea, 2026-10-26).** The Sounding pays for
itself twice. The intercepts that time Sable Wake's raids also tell smugglers
when Jangala's lanes are clear of Hegemony patrols, and the post sells those
windows. Its lander pad is a cold drop for the Sector's quiet trade, using
vanilla's two contraband goods: **harvested organs** in cryo pods racked
out on the airless surface, where the cold costs nothing, and **recreational
drugs** cut and pressed in a chem module beside the pad. Garnir's depot is a
short hop away. That changes who is there: a lander crew and a chemist beside
the signals techs. It also darkens what the riggers fear. They were not let go,
and the pods are next to their module. This is implied, never shown or said
outright.

**Why the company can reach it.** The Dust Ledger's strongbox gave the post's
location, its approaches and the watch roster. The post listens outward, at
the system; Barad's magnetosphere drowns everything on the gas giant's side, so
the watch is **blind toward Barad**. The company comes in behind the moon and
lands over the horizon from the post. Low risk is the reward of that preparation.

**What each side knows.**

| | Company | Sable Wake |
|---|---|---|
| Before landing | Location, approaches, watch count, what each installation does | A Wayline boat left Jangala; it expects freight on the lanes, not a landing |
| After the alarm | Its own map picture | The ranging mast's picture while it stands; afterwards only what its crews see |
| Never | The hidden job until found | The company's targets or plan |

No faction relation, market, Hegemony or orbital state changes, as with the
rest of the arc.

## The hook (owner, 2026-10-26: A)

**A. Ears, eyes and voice (recommended).** The three charges each take away a
different capability, so the order is the plan:

- **The ranging mast (eyes)**, on the rim. While it stands it is an observer
  for Sable Wake, as a fort's radar spots for its holder (`FortSensors`): the
  post's mortar carrier fires on whatever its side observes, so a company in the
  open is shelled across the flat. Blown, the mortar sees only what the post's
  crews see.
- **The uplink (voice)**, on a knoll reached by a ramp. From the alarm it
  sends Sable Wake's warning to Garnir: a visible progress bar. Blow it before
  the bar fills or Garnir is warned. The battle is still won either way; the
  campaign keeps the mark `corvus.sable_wake.garnir_warned`, for A Cold Account
  to read.
- **The dish field (ears)**, on the crater floor beside the hab cluster: the
  contract's real target, the best defended, and where the hidden job is.

Mast first makes everything after it cheaper, but the warning probably goes
through. Uplink first keeps Garnir in the dark, but crosses the flat under the mast's mortar.
A careful approach delays the alarm and with it the warning's start.

Not chosen: an aurora storm that blinds the post for a timed window (B), and
Garnir's moonrise bringing a relief lander unless the uplink falls first (C).

## Mission flow (hook A)

1. **Landing.** Company boats land on the far side of a low ridge on the
   crater's Barad side, out of the post's sight. The quiet bed plays.
2. **Alarm.** The first of: a shot by or at the company, or the ranging mast or
   a sentry seeing a company unit. The mission declares combat music from here.
3. **Warning.** From the alarm the uplink transmits (about three minutes,
   tuned in play). Charges set at the uplink stop it; the share sent is shown.
4. **Charges.** The existing Sabotage plant at three named sites (`sabotage-command.md`),
   each with its effect on completion: mast down (guns short-sighted, defenders
   lose the radar picture), uplink down (warning stops), dish field down.
5. **Victory** is all three charges, as today. Defeat is the default: the
   company has no forces on the map and no reserve it can still land.

## Side objectives

On the toolkit (`SideObjective`), declared in a `BlindWatchSideObjectives`.
Pay is the owner's call per job (proposed below).

| Job | Window | Payoff in battle | Return |
|---|---|---|---|
| **The riggers** (offered): reach the riggers' module and walk them to the landing ground | until the dish field blows | They give up the post's layout: the bunkers and the crawler's track are revealed | 1,500 from their outfit; mark `corvus.warion.riggers_freed` |
| **The false schedule** (hidden, found at the dish field's control hut): a squad leader holds the console while the uplink stands and before Garnir is warned | uplink standing, warning unsent | Garnir receives a false Wayline sailing schedule instead of the warning | no credits; mark `corvus.garnir.false_schedule`, for A Cold Account |
| **The cold store** (owner, 2026-10-26: option a, scenery and this job, no temptation job; hidden, found by sighting the pod racks): a squad holds the racks to log the manifest | until the dish field blows | none in battle | no credits; mark `corvus.sable_wake.organ_manifest`, evidence Kord's navy can read in A Cold Account |

The false schedule needs the uplink standing, which pulls against blowing it
first: the trade the hook is built on.

## Defences and machines

The owner's standing concern: a lone Hound walks through a mission. Answers:

- **Rim guns.** Two gun nests (`DefensePostStamper.stampNest`) beside the ranging
  mast covering the open regolith between the landing ridge and the post.
  Rockets, the Hound or charges kill them; the boulder field reaches them in cover.
- **Bunkers.** Regolith-bermed pillboxes at the hab cluster's airlock and at the
  foot of the uplink ramp, manned from the security posts (no extra headcount).
- **The mortar carrier.** The shipped `FIRE_SUPPORT_APC` (STANDOFF, indirect
  fire on anything its side observes) works from the crater floor; with the mast
  standing it reaches the whole flat. It has a driven lane along the rover track
  between the three installations, checked with `VehicleRoutePlanner`, and falls
  to rockets, the Hound or a squad that reaches it.
- **The patrol crawler (if the first runs are still easy).** An `UP_ARMORED_APC`
  on the same track. Not before the first measurement (owner: don't over-tune).
- **The rocket truck.** The Dust Ledger's SRM technical behaviour on the uplink
  knoll: from the alarm it works the knoll's corners against company armor.

## Map

Authored like Saltwell Cross: a Java recipe (`BlindWatchSounding`) plus road,
terrain and damage documents (`blind-watch`), 216x112 cells. ROCK palette, the
actual planet's: bare regolith with stones lying in drifts, scree fanning off the
rock, and rover tracks rather than paved roads. No grown settlement or garrison
precinct; buildings publish only as held places, so nothing becomes a capture
point by accident. The crater rim and the uplink knoll are authored elevation in
the recipe (tier one, with ramps), stamped before anything is built on them.

It is a cratered moon, and the ground says so. Impact craters of several sizes lie on the flat and
round the landing ground: each a raised crescent rim (terrain document, real cover and an obstacle)
broken where it slumped, round a bowl swept bare, with ejecta thrown out in rays and boulders on the
big ones' aprons (`BlindWatchSounding.CRATERS` mirrors the rims). The post sits in a crater of its own:
the rim curves round the north, and low walls close it west of the dish field, along the south and
down the east, broken where the rover track and the rille come in.

| Area | Content |
|---|---|
| West | The landing ground behind a low ridge: three berths, one logical landing area (`Layout.landing`). The ridge is three masses with passes between: north (to the boulder field), middle (the rover track), south (to the rille). |
| Centre | Open regolith flat pocked with impact craters (rims, bowls, ejecta and its boulders), crossed by the rover track, worn into ruts, with dead rovers and wreckage where they stopped; a boulder field to the north (basalt formations on a jittered lattice, slow and covered, `Layout.boulderCover`); a dry sinuous rille to the south (excavation: impassable, see-through), broken by two crossings (`Layout.rilleCrossings`), ending under the dish field's south-west corner, with boulder clusters along its south bank. |
| North-east | The crater rim, a long arc one tier up whose south-west spur reaches toward the rover track. On it the ranging mast (gravel pad, `Installation.MAST`) and two rim guns (`DefensePostStamper.stampNest`, heavy MGs): one on the spur's tip over the track and the flat's east half, one beside the mast over the crater floor. Two ramps: a four-cell one at the spur's west flank from the boulder field (`Layout.rimRamps` first), a five-cell one from the crater floor. |
| East floor | Inside the post's crater wall. The fenced dish field (six four-cell listening dishes on concrete footings, their cables laid to the control hut, gates north, south and east, `Installation.DISH_FIELD`) with its control hut and console (`Layout.controlHut`, `Layout.console`); the hab cluster either side of the hab street: north the long bunkhouse and a short bunk module joined by a tube, south the signals racks' module (entered through its airlock) and the riggers' module at the row's east end, a narrow tube between them; power laid along the street, antenna masts, oxygen spheres and gas bottle racks behind the bunks, containers and crates; the cold drop between the riggers' module and the lander pad: the cryo pod racks in two rows with the aisle a squad holds them from (`Layout.podRacks`, `Layout.podHold`), their chiller, the chem module and the chemist (`Layout.chemModule`, `Layout.chemist`); on the lander pad the crates on pallets (`Layout.drugCrates`) and the half-loaded cargo crawler with its crew (`Layout.cargoCrawler`, `Layout.landerCrew`); fuel skids on the pad; the motor pool south of the cold drop, its tanker parked. |
| South-east | The uplink knoll, one tier up, climbed by one six-cell ramp from the west (`Layout.knollRamp`): the uplink's transmitter, switch gear and generator on a gravel pad (`Installation.UPLINK`), and the rocket truck's stands (`Layout.technicalStands`). |

Three approaches are real choices, each proved open on its own ground
(`Layout.approaches`): **flat** (the rover track through the ridge's middle pass
to the hab's airlock, under the spur gun), **boulders** (the north pass, through
the field, up the rim's west ramp to the mast), **rille** (the south pass, over
the west crossing, along the rille's south bank behind its boulders to the dish
field's south gate). The riggers' walk-out (`Layout.riggersWalkOut`) runs from
their module's door down the hab street and the rover track to the landing ground.

**Defences.** Two regolith-bermed pillboxes (`Layout.pillboxes`, sandbags banked
round back and sides): at the hab's airlock, slits west down the rover track, manned
from the hab posts; south of the knoll's ramp foot, slits west over the ground
between the dish field and the knoll, manned from the uplink posts. The mortar
carrier starts in the motor pool (`Layout.mortarStart`); its lane
(`Layout.mortarLane`) runs out past the chem module's east side onto the lander
pad, up the rover track below the rim and back, down the hab street to the dish
field's north gate, below the uplink and home, every leg proved with `VehicleRoutePlanner.routeDrivable` on its hull's
clearance. The rocket truck drives up the knoll's ramp to each of its stands.

**Garrison.** About two dozen Sable Wake hands at Low (`Layout.posts`, 24 posts):
rim (4), mast (2), hab (5, three at the airlock pillbox's slits), dish field (5, one
in the control hut), uplink (5, three at the ramp pillbox's slits), motor pool (2),
the watch boss (1, in the signals module). The signals technicians shelter in the
signals module (`Layout.signalsTechs`), the riggers in their module
(`Layout.riggers`); the chemist and the lander crew are at the cold drop.

**Art needs.** The map dresses these with stand-ins until art exists; none is
generated yet.

| Needed | Stand-in now |
|---|---|
| Ranging mast (lattice mast, sensor head) | `gantry-column` beside a `military-radar-dish`, with a generator and switch gear, on a gravel pad |
| Listening dish (large, three to four cells) | `listening-dish`: the radar dish master at four cells (`listening-post` sheet, `build_listening_post.py`), on a six-cell concrete footing, a cable drum beside it |
| Dish cabling | `ship-dressing.cable-run` laid on the ground (walkable) |
| Uplink transmitter (tight-beam dish on a mount) | `military-radar-dish`, `industrial-reactor-housing`, switch gear and a generator on a gravel pad |
| Prefab pressurised module (hab, signals, riggers) | `PREFAB_MODULE`, `PREFAB_SHORT_MODULE` (olive wall, corrugated roof), `PREFAB_BUNKHOUSE` and `PREFAB_LOCK_MODULE` (white ceramic, slab roof) stamps |
| Airlock | the lock module's end lock: outer hatch, a one-cell lock, inner hatch |
| Connecting tube | `PREFAB_TUBE` and `PREFAB_NARROW_TUBE` stamps between modules, their own hatch to the street |
| Antenna mast | `gantry-column` with a `military-radar-dish` at its head |
| Oxygen and fuel stores | `oxygen-sphere` (the fluid tank master at two cells, `listening-post` sheet), `origin-lab.tank-rack-clean`, `shuttle-bay.fluid-skid`, `parked-tanker-truck` |
| Dead rover, wreckage | `parked-utility-truck`, `parked-armored-apc`, `basin-scrap-*` pieces, with salvage beside them |
| Control hut and its console | `CONTROL_HUT` stamp; `industrial-control-console` and a `civic-server-rack` |
| Signals racks | `civic-server-rack` rows in a module |
| Regolith berm round a pillbox | sandbag runs on the berm's ring |
| Rover tracks (twin ruts) | a narrow worn DIRT road (`blind-watch.road.json`), scarred along its edges (damage layer "Rover track wear") |
| Crater bowl, ejecta | bare regolith in the bowl; basalt stones in rays outside the rim |
| Lander pad markings | concrete slab with hazard corner marks (`StoryServiceYard.bay`) |
| Cryo pod rack (organ pods, chiller) | `galley-cold-store` cabinets in rows; an `industrial-generator` and a fluid tank as the chiller |
| Chem module fittings | `CONTROL_HUT` stamp; `medical-drug-locker` and a `medical-treatment-table` |
| Cargo crawler, half loaded | `parked-cargo-truck` with crate and pallet stacks beside it |

## Music

The quiet bed is **Sequencer pulse** (cold, mechanical: a signals post) unless
the owner picks Night ops. The mission declares quiet until the alarm and combat
from it, as the Dust Ledger does.

## Work pieces

Each is its own session; contracts between them are the named sites.

**A. Map — `BlindWatchSounding`.** Recipe, documents, defences, crawler lane,
look-preset `SnapshotSuite`, and a `Layout` exposing: landing area; the three
sites' plant cells; rim gun and bunker cells; crawler track; truck stands;
riggers' module and walk-out route; the control hut console; the uplink. Proves
every site reachable from the landing ground and all three approaches open.

**B. Mission behaviour — `BlindWatchSequence`.** Alarm, warning bar, per-site
effects (`ChargeSiteObjective` has no completion hook yet; sites today come from
generated POIs through `SabotageSiteLayout.select`, so authored sites need an
override), the mast as an observer, side objectives, music declaration, checklist
and markers. Built against its own `Sites` record on small synthetic grids.

**C. Story and text — `blind-watch-dialogue.md`.** Briefing with Mara and
Castle, field calls (alarm, warning, each charge, side jobs), debrief variants
over warned / not warned and the side jobs, catalog strings under
`corvusBlindWatch*`. The two recorded debrief lines stay. Written: names, script, catalog and hook strings are in `blind-watch-dialogue.md`; wiring reads its debrief selection rule.

**D. Wiring (lead).** `CorvusChapter.BLIND_WATCH` builds the authored fixture,
retire the procedural path for this chapter, `commanderEvidence` with a
worst-approach and a prepared-approach run, the full-mission preview video.

## Acceptance

- [x] The owner picks the hook: A, ears, eyes and voice (2026-10-26).
- [x] Authored map renders from the look preset; no capture point except the three sites.
- [x] Every site is reachable from the landing ground; three approaches stay open; the crawler's lane passes `VehicleRoutePlanner`.
- [x] Second map pass: craters on the flat, the post's crater closed round its floor, large dishes, varied modules with airlock and tubes, stores, wrecks and track wear.
- [x] Each charge's effect fires in a unit test; the warning stops at the uplink charge.
- [x] Each side job pays off only inside its window.
- [ ] `commanderEvidence -Pmission=corvus "-Dbattle.corvus.chapter=BLIND_WATCH"`: worst approach (autonomous, no side jobs) and prepared approach (side jobs granted, mast first) both measured; the prepared one clearly easier.
- [ ] Full-mission preview video with sound, 1920x1080, look preset.
- [ ] Live play on Warion in a campaign.

## Open questions

- Side-job pay (proposed: riggers 1,500, false schedule none).
- Whether Blind Watch reads the Dust Ledger's broker mark: an escaped Teague
  could have warned the post, starting its crew stood-to. It would be the first
  consumer of a side-objective mark.
