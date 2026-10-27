# Blind Watch: dialogue and character notes

Status: ACTIVE — authored script; text-only until recordings are approved.

Written: 2026-10-26

## How to read this script

The chronological register for **Blind Watch** at the Sounding on Warion: Zoey
and Castle's briefing, field calls, side-job barks and the debrief variants.
`blind-watch-sounding.md` owns the mission rules; this file owns what is said.
Zoey is Wayline's tired, businesslike acting head; Castle is dry and terse and
treats quiet and order as resources. Morgan Hale speaks only in field calls about
ground she can see. The arc calls this chapter its confidence crest: the company
chose the target and the order. The debrief lets that land, and the cost turn
keeps casualties in front of it rather than behind a triumphant speech.

Every line is catalogued in `wayline_narration.json` under mission
`story_corvus_blind_watch`, with `textKey` into `strings.json` (`corvusBlindWatch*`)
and `voiced: false`, except the two recorded debrief lines, which keep their
takes. The catalog is the runtime source; nothing here is wired yet.

## Names settled

- **The Sounding** — Sable's listening post (kept from the story).
- **Ivo Brannick** — Sable's watch boss. Named in the briefing and the warning call; never heard.
- **Odile Fenner** — the hired signals chief, a contract technician, not Sable. Her racks keep the routing tables the debrief reads.
- **Pia Lindqvist** — foreman of the pressed rigging crew; her outfit is **Coldframe Rigging**, which pays for its people.
- **Benno Kask** — boss of the lander crew that flies the cold drop's freight to Garnir. Not named in any line yet.
- **Ines Calder** — the chemist in the chem module beside the pad. Not named in any line yet.

None collides with Venn, Vale, Prell, Teague, Harrow, Ankrum, Castle, Hale or Kord.

## Facts every variant keeps

Warion is airless and nobody governs it; no patrol lands there and no one else
comes. The strongbox gave location, approaches and watch roster. The post is
blind toward Barad, so the company lands behind the moon. The mast spots for
the mortar carrier, the uplink warns Garnir from the alarm, the dish field is
the target. Every victory debrief says the Sounding is silent and that the
routing tables point to Sable's dispatch compound on Garnir. The hidden
false-schedule and cold-store jobs are never named before they are found.

**The cold drop.** The post also sells clear-lane windows to smugglers, and its
lander pad stores harvested organs in cryo pods racked in the vacuum, plus drugs
from the chem module. The briefing carries one hint only (Zoey, turn 13: by
Wayline's manifests the lander runs more than signal gear). Lines name the pods,
tags and manifest, never what is in them, and nothing is gory. The riggers' fear
of the pods is implied, never stated: in `riggers_found`, Lindqvist will walk
but not past the pad.

## Briefing (`briefing.01`–`briefing.16`, mission `story_corvus_blind_watch`)

Zoey (odd turns) and Castle (even) alternate: Warion and the Sounding; nobody
governs it; what the strongbox gave (Brannick, the watch, Fenner's technicians);
the blind side and the landing behind the moon; the alarm; the mast and the
mortar; the uplink and the warning; the order is the plan; the dish field;
the defences and approaches (rim guns, bunkers, rocket truck, boulder field,
rille); the riggers and Lindqvist; orders. Keys `corvusBlindWatchBrief01`–`Brief16`,
in catalog order.

## Field calls (events `corvus.blind_watch.*`)

| Event | Speaker | Trigger |
|---|---|---|
| `stood_to` | Castle | mission start, when the Dust Ledger's broker escaped (the post was warned) |
| `alarm_shot` | Castle | alarm by a shot |
| `alarm_sighted` | Castle | alarm by a sentry seeing a company unit |
| `alarm_mast` | Castle | alarm by the ranging mast seeing a company unit |
| `warning_started` | Zoey | the uplink starts transmitting (at the alarm) |
| `warning_half` | Castle | the warning reaches half |
| `warning_sent` | Zoey | the warning completes; Garnir is warned |
| `warning_stopped` | Castle | the uplink charge stops a warning in progress |
| `mast_down` | Castle | ranging mast charge completes |
| `uplink_down` | Zoey | uplink charge completes |
| `dishes_down` | Zoey | dish field charge completes |
| `mortar_destroyed` | Castle | the mortar carrier is destroyed |
| `riggers_found` | Morgan | a company unit reaches the riggers' module |
| `riggers_moving` | Morgan | the riggers leave the module under escort |
| `riggers_out` | Zoey | the riggers reach the landing ground (layout revealed) |
| `riggers_lost` | Castle | the riggers die, or the dish field blows before they are out |
| `schedule_found` | Zoey | a company unit first sights or reaches the control hut console |
| `schedule_working` | Castle | a squad leader starts holding the console |
| `schedule_sent` | Zoey | the false schedule completes |
| `schedule_lapsed` | Castle | found, but the uplink fell or the warning went first |
| `cold_store_found` | Castle | a company unit first sights the pod racks |
| `cold_store_working` | Morgan | a squad starts holding the racks |
| `cold_store_logged` | Zoey | the manifest is logged (mark `corvus.sable_wake.organ_manifest`) |

Keys are `corvusBlindWatchField<Name>`. `warning_stopped` and `uplink_down` can
fire together; play `warning_stopped` only when a warning was in progress.

## Side jobs (`SideObjective.Hook`)

- **`blindWatchSideRiggers`** (offered, offerer `blindWatchSideOffererRiggers`):
  Name, Steps, Offered, Working, Done, Lapsed, Failed, Target (riggers' module),
  Destination and DestinationStatus (landing ground), DebriefDone, DebriefLapsed,
  DebriefFailed, Reward.
- **`blindWatchSideSchedule`** (hidden, offerer `blindWatchSideOffererCompany`):
  Name, Steps, Offered, Working, Done, Lapsed, Timer, Target (control hut
  console), DebriefDone, DebriefLapsed, DebriefMissed, Reward. No destination.
- **`blindWatchSideColdStore`** (hidden, offerer `blindWatchSideOffererCompany`):
  Name, Steps, Offered, Working, Done, Lapsed, Timer, Target (pod racks),
  DebriefDone, DebriefLapsed, DebriefMissed, Reward. No credits, no destination.
  The owner chose scenery plus this job; there is no contraband temptation job.

## Debrief (victory only)

Six turns in order, plus one optional; each beat has one line per variant.

| Beat | Speaker | Variant | Rule |
|---|---|---|---|
| `opening.<g>` | Castle | `quiet`, `warned` | `warned` when the warning completed (`corvus.sable_wake.garnir_warned`), else `quiet` |
| `routing.<r>` | Zoey | `quiet`, `warned`, `schedule` | `warned` when warned; else `schedule` when the false schedule was sent; else `quiet` |
| `initiative` | Zoey | recorded | always |
| `riggers.<f>` | Zoey / Castle | `freed`, `left`, `lost` | DONE → `freed`; FAILED → `lost`; otherwise (lapsed, never reached) → `left` |
| `cold_store.logged` | Zoey | only line | only when the cold store is DONE; otherwise the beat is skipped |
| `cost.<c>` | Castle | `losses`, `clean` | `losses` when any marine was lost, else `clean` |
| `ready` | Castle | recorded | always |

A warning wins over a sent schedule: once Garnir is warned the false schedule is
worthless, so a run that sent it and was warned afterwards reads `warned`. Every
`opening` says the Sounding is silent; every `routing` names the dispatch
compound on Garnir. `clean` does not call the job free: it credits preparation
and luck. Keys `corvusBlindWatchDebrief<Beat><Variant>` (`OpenQuiet`,
`RoutingSchedule`, `RiggersFreed`, `ColdStoreLogged`, `CostLosses`, …); events
`debrief.blind_watch.<beat>.<variant>`.
