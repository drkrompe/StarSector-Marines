# Blind Watch — the Sounding

Status: PLANNED — hook proposed to the owner (Blind Watch picks page); building on A meanwhile.
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

## The hook (proposed; owner's pick on the Blind Watch picks page)

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

**B. Aurora window.** Barad's magnetic storms blind the post's instruments. The
company lands at a storm's onset; while it lasts the rim guns and the mast are
blind and sentries must see the company by eye. The storm passes on a visible
clock and the post wakes at full strength. A tempo mission: push hard while the
watch is blind.

**C. Moonrise.** The uplink can reach Garnir only while Garnir is above
Warion's horizon. Garnir rises on a visible clock; at moonrise the post calls
for help and a Sable Wake lander arrives with a relief squad. Blowing the uplink
first cancels the lander. Raises the chapter's danger above Low unless the
uplink falls in time.

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
terrain and damage documents (`blind-watch`). ROCK palette, the actual planet's.
No grown settlement or garrison precinct; buildings publish only as held
places, so nothing becomes a capture point by accident.

| Area | Content |
|---|---|
| West | The landing ground behind a low ridge: all berths one logical landing area. |
| Centre | Open regolith flat, the rim guns' killing ground, crossed by the rover track; a boulder field to the north (slow, covered); a dry sinuous rille to the south (impassable, see-through, two crossings) leading under the dish field. |
| North-east | The crater rim: the ranging mast and the two rim guns, reached by ramps. |
| East floor | The dish field (six dishes, the control hut), the hab cluster (bunks, the riggers' module, the signals racks), the lander pad. |
| South-east | The uplink knoll with its ramp, bunker and the rocket truck's stands. |

Three approaches must be real choices: across the flat (direct, under the guns),
through the boulder field to the rim (slow, covered, the mast first), and along
the rille (covered, flanking, to the dish field).

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

- [ ] The owner picks the hook.
- [ ] Authored map renders from the look preset; no capture point except the three sites.
- [ ] Every site is reachable from the landing ground; three approaches stay open; the crawler's lane passes `VehicleRoutePlanner`.
- [ ] Each charge's effect fires in a unit test; the warning stops at the uplink charge.
- [ ] Each side job pays off only inside its window.
- [ ] `commanderEvidence -Pmission=corvus "-Dbattle.corvus.chapter=BLIND_WATCH"`: worst approach (autonomous, no side jobs) and prepared approach (side jobs granted, mast first) both measured; the prepared one clearly easier.
- [ ] Full-mission preview video with sound, 1920x1080, look preset.
- [ ] Live play on Warion in a campaign.

## Open questions

- The hook (A, B or C).
- Side-job pay (proposed: riggers 1,500, false schedule none).
- Whether Blind Watch reads the Dust Ledger's broker mark: an escaped Teague
  could have warned the post, starting its crew stood-to. It would be the first
  consumer of a side-objective mark.
