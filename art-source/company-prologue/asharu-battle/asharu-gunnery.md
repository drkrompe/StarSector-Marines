# Asharu battle: fire control

Status: DRAFT. The decision layer is in code (`asharu_gunnery.py`). `scene_fx.planned` does not consume it yet.
Written: 2026-10-23
Updated: 2026-10-23. Every ledger gunfire hit delivered (asharu_damage 0b7c2166d); 3.0 s flight gate; exact arrival timing. Matched to `scene_fx.planned` (139192a23): its range gate, round flight lead, `walk_r`, `track()`; ledger `by=None` hits left to the ledger.

`scene_fx.planned` executes gunfire. It slews each turret on both axes at its turn rate, with feed-forward. It fires in bursts gated on target (5 degrees), in range and with a clear line, and walks each burst across the target (`WALK_R`). Each round hits only where its straight line meets the hull.

`asharu_gunnery` is the decision layer above it. For each turret it decides:
- which target, and when;
- at which part of the hull;
- in which bursts;
- with how wide a walk.

It is plain Python over the blockout's flight paths (`battle_blockout.SHOTS`). It needs no Blender and no build on disk, and plans every shot in about three seconds.

## The calls

```python
import asharu_gunnery
engs = asharu_gunnery.plan(shot_kf, ship_key, turret, weapon, t0, t1)
lay = asharu_gunnery.track(shot_kf, ship_key, turret, weapon, t0, t1)
```

- `shot_kf` may be a key frame (`k3`) or a motion-span frame (`k3_f01320`).
- `turret` is the kit's turret number (`ctx.anchors.guns.turret`).
- `weapon` is the `WEAPONS` key the gun is armed with.
- Pass `t0` as the span's first frame less `PREROLL_S`, so rounds already in flight at the cut are planned.
- **None** means no opinion (an unknown shot, or a ship not in it): keep the blockout's `fx` entry.
- **[]** means hold fire for the whole window.
- Otherwise `plan()` returns engagements in start order, each a dict:

| field | meaning | feeds `planned()` as |
|---|---|---|
| `target` | ship key fired at (a hull, or a drone for point defence) | `b` |
| `region`, `aim` | what is aimed at; metres in the **target's** frame (+X bow, +Y port, +Z dorsal) | `aim_local` for `hit_point`, replacing the fx entry's `hit_l` |
| `walk_to` | for a rake, where the burst walks to (target frame), else None | the walk's end, if wanted |
| `start`, `end` | sequence seconds | |
| `bursts` | `[(on, off), ...]` seconds: when the trigger is held. Seeded, so the gun code and the ledger see the same bursts | replaces the seeded `RHYTHM` bursts (`round(t * fps)`) |
| `walk_r` | the `WALK_R` that lands `hit_share` of this engagement's rounds | replaces `WALK_R` |
| `hit_share` | share of rounds that should strike (hull or shield) | |
| `walk_m` | metres off the hull the misses pass | |
| `elevation_deg`, `traverse_deg` | (lo, hi): the mount's lay off its deck and keel over the engagement | a check on the baked slew |
| `flight_s` | seconds a round takes at `range_m` at the kit's round speed | |
| `arrive` | for a story moment, the ledger time its rounds land; its bursts fire `flight_s` earlier | |
| `kind` | `'hull'` or `'pd'` | |
| `force` | True for a story moment, which may fly past the execution gate (see Range) | must bypass `MAX_FLIGHT_S` / `MAX_RANGE_M` |
| `ledger`, `ledgers` | the `asharu_damage` Hit id(s) this burst delivers; None or [] for incidental fire | |
| `why` | the doctrine in words | |
| `shooter`, `turret`, `weapon`, `range_m` | as named | |

**`track()` is the gun's continuous lay**, for the slew. It returns `[(from, to, target, aim)]` covering the whole window. Between engagements the gun is already swinging onto its next target, or holding on its last.

`planned()` takes one target per call and bakes the slew over the whole span. A turret whose plan has several targets therefore needs `track(f)` built piecewise from this list, and `allow(f)` from the active engagement's bursts.

Two more calls:
- `shot_plan(kf)` gives every engagement in a shot, the holds of fire and each ledger moment's status.
- `expected_hits(eng)` gives rounds, expected hits and damage, from the cadence.

From the command line: `python asharu_gunnery.py --summary | --table [kf ...] | --check | --json PATH`.

## How it decides

1. **Story moments first.** The damage ledger (`asharu_damage.LEDGER`, agreed with its session 2026-10-23) is the authority on who hits whom, and when.
   - **Which hits count:**
     - every hit drawn by a tracer, its shooter being the token after `tracer` in `src`;
     - every `GUN_MADE` hit: a kill or breach that a blast or a fire draws but a gun caused. These are the Feeder burst that breaks up Hound (P) C, the hit that turns the lead, and the Tarsus's drives.
     - Hits with no `by` are story hits the ledger keys itself, and get no gun.
   - **Firing early.** Each round must *arrive* on the ledger time, so it is fired a flight time earlier. At the kit's speeds that is 0.4-6 s.
   - **Which mounts deliver it:** every mount that bears with a clear line, and can fly that far, within 0.8 s before to 0.4 s after the fire time. A kill allows 0.9 s after, for a finishing burst into the breaking wreck.
   - **Clashes:** moments are shared out across the ship's mounts. The hardest goes first. A later clashing moment may take a mount from an earlier one that has another to spare.
   - **The burst:** held through the moment, at the ledger's spot (`at`, walking toward `to`), with 60% of rounds on target (`MOMENT_HIT`).
2. **Doctrine for the rest, every 0.1 s.** Each mount scores every hostile in arc, in reach and with a clear line, on:
   - the shot's standing order (`INTENT`: the ship the story says is under attack);
   - nearness against reach;
   - whether the target is armed, or is shooting at this ship;
   - a strong bonus for staying on target;
   - point defence only on small craft inside 700 m.

   A run under 0.6 s is dropped. A new target costs slew time at the game turn rate.
3. **Aim.** The gun aims at the presented aspect: the centre of mass of the face it sees. A spot the story names wins (`INTENT_AIM`: the Tarsus's drives, the Colossus's port pod, the Falcon's starboard flank).
4. **Accuracy.**
   - A base share by weapon family, cut by range against reach, by a small hull, and by the crossing rate against the turn rate. Then times `MISS_TASTE` (.8).
   - Point defence on a Borer lands 5%.
   - `walk_r` turns the share into `planned()`'s walk. It plays the same walk (open 0.9-1.6 reach off, cross to 0-1.3 the far side, jitter 0.08) against an ellipsoid hull (0.5 x 0.3 x 0.1 of her length) seen along this engagement's own line of fire. It then solves for the reach that lands the share.
   - Per shot, 20-44% of rounds hit.
5. **Holding fire.**
   - `CEASEFIRE` covers whole shots:
     - S1a, S1, S2: before the ambush;
     - S3: running dark;
     - S4: still climbing; the Manticore opens in S5;
     - S10: "Let her go";
     - S11, S12.
   - `SILENT` stops ships:
     - Lasher A burning;
     - the Phaeton struck;
     - the lead tumbling;
     - kills, from `killed_at`. A dead hull draws fire for 0.4 s more.
   - `SILENT_TURRET` stops turrets: the Tarsus's aft guns under her drive fire, and her forward guns once the covering Wolf (P) has raked them.
   - `HOLD_ON`: nobody shoots the prize in S7 while the boats land, except the order to silence her guns.
   - The Wolves (P) hold through each Displacer blink.

**Range.** The plan asks only for what `planned()` will fire. Every round is gated as `scene_fx` gates it, story moments included. Its reach is the smallest of:
- its speed x `EXEC_FLIGHT_S` (3.0 s, matching `MAX_FLIGHT_S` since cc7532591);
- `EXEC_RANGE_M` (3500 m);
- the game's range x `RANGE_SCALE` (5).

That gives a light MG 1350 m, the needler 2700 m, the ion cannon 2100 m, the blasters 1800 m and the Hellbore 510 m.

**A story moment's rounds must land on the ledger's time.** Each step's round is flown at the range it leaves at, and must land within 0.4 s of the hit (up to 0.9 s after, for a kill's finishing burst). `arrival_windows(kf, shooter, target, at)` gives, per turret, when a ship's rounds can land, which is how the ledger times its gunfire hits.

**What fires at long range: missiles.** No gun reaches 2.5-4 km at the kit's round speeds.
- S5 and the first half of S6 belong to the Harpoons. `shot_plan()` lists each shot's salvos (`missiles`) and when every ship's guns first have a hostile in reach (`opening`).
- The lead Lasher's 55.9 s breach is a warhead from a second Manticore salvo. **It needs a `missiles` entry, manticore-p -> lasher-lead, in `k3`'s fx.**
- The Manticore's 53.8 s rake and the Falcon's 55.0 s rake are the ledger's own story hits.

**The S6d Hound** (owner, 2026-10-23: angle its gun up to track):
- Her dorsal turret (t2) takes Hound (P) A from −9 degrees, firing the 75.0 s rake over 72.3-73.3 s.
- She rolls over, and Hound (P) A passes under her deck. The gun holds and keeps tracking (`track()`).
- She fires the kill burst at 74.6-75.6 s, at up to +48 degrees. The rounds take about 2 s, so they land on the 76.9 s kill.
- The gun holds on Hound (P) A until the kill lands, then `track()` swings it onto Hound (P) B. At the turret's 50 degrees a second, the swing runs to the end of the shot, so she does not fire again in S6d.

**Mounts.** `MOUNTS` is each finished build's kit turrets (pivot, rest bearing, arc), from the manifests. The turret limit is −10 to +85 degrees off the deck, and straight up past 75 degrees in any azimuth. Stand-ins use the game's weapon slots. An engagement runs only over the steps where the one point it lays on (its region) is inside the mount's arc, and ends at the last such step. The firing-arc lint (`fire_arc_lines` in `battle_blockout.py`) holds every burst to that, every story hit a gun delivers to a mount that reaches it, every tracer a shot asks for to a mount that bears, and a Borer's mining laser (a fixed nose mount, game arc 30 deg) to her nose. The hard check is the hull-cross lint (`hull_cross_lint`, in every Blender build; owner, 2026-10-27: "Specifically I just want to make sure it's not firing through it's own hull"; "Right, the viewer would spot tracers that obviously went through a hull"): every planned burst, and every Borer beam, is cast from just off its mount against the shooter's own plating and every other hull but its target's, as posed at that moment, and one that crosses a hull where the crossing is in frame fails the build, however small the ships (a streak is bright and long); one crossing out of frame is a listed cheat. Arc faults likewise fail only where they read (`readable`: 150 px, or 60 px near the frame centre).

Assumptions to confirm against the kit anchors:
- the Hound's forward hardpoint is turret 1, her dorsal turret 2;
- the Falcon (P)'s two IR-pulse guns are kit turrets 7 and 8.

## What the plan contradicts or cannot satisfy

`--check` reports every ledger gunfire hit delivered, against asharu_damage 0b7c2166d. The ledger retimed or redrew the rest to fit the guns (agreed 2026-10-23):
- 53.8, 55.0 and 60.0 became the ledger's own story hits;
- 61.0 moved to 61.6, 71.2 to 72.4, and 108.0 to 106.9.

What the story still asks and the geometry does not give:

1. **S6b: Lasher B's Feeder burst lands after Hound (P) C breaks up.**
   - Lasher B is out of arc until about 59.8 s, and her rounds then take 2.5-3 s to cross 1.2 km. The first lands at 62.8 s; the breakup is at 61.9.
   - The merchant Wolf's rounds land from 61.5 and deliver the kill, and Lasher B's burst lands into the breaking wreck.
   - If the Feeder burst must be what breaks her up, the breakup has to move to about 63 s (`k3c`'s flight hits and blast_on).
2. **S8-S9: the Colossus Mk.III cannot fire on the Colossus at all.**
   - At 64 degrees of roll the Colossus sits 13-23 degrees below her deck on her low side, past the −10 degree limit, on all twelve turrets.
   - The 89 s pod hit is the ledger's own story hit.
   - Fix: about 10-15 degrees more roll (to roughly 75-80) over 88-96 s, or sit her higher.
3. **S6: the Phaeton cannot defend herself.** S6 uses her gunless wreck build throughout.
4. **The Falcon (P) reads as lightly armed.** She has two kit gun turrets firing a stand-in heavy blaster, and is out of gun reach in S6. Her broadside is missiles.
5. **Incidental fire the story does not mention.** Doctrine finds it, mostly misses, with `ledger` None:
   - the Wolves (P) on the convoy's Hound (S6);
   - the column's guns on Wolf (P) B (S6c);
   - the Tarsus on Kite (P) A (S7).

## Fire plan per shot

Generated by `python asharu_gunnery.py --summary`: turrets grouped per ship, target and intent. `--table` has every engagement. Windows may open before a shot's cut, because rounds fired then land in the shot. The shots not listed (S1a, S1, S2, S3, S4, S10, S11, S12) are ceasefire.

### S5 (m5) 44-50 s: The opening

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| manticore-p | t3 | hound-m | belly | 45.5-50.0 | 20%-20% | 3 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | hound-m | belly | 46.5-50.0 | 19%-19% | 5 | doctrine: closest threat in arc and range |

Missiles: manticore-p -> phaeton (Harpoons)

Guns in reach: manticore-p from 45.5 s on hound-m; wolf-p-b from 46.5 s on hound-m; phaeton never (nearest wolf-p-b at 3803 m at the cut); tarsus never (nearest wolf-p-b at 3515 m at the cut); buffalo-b never (nearest wolf-p-b at 3304 m at the cut); dram never (nearest wolf-p-b at 3091 m at the cut); hound-m never (nearest wolf-p-b at 2851 m at the cut)

### S6 (k3) 50-58 s: The screen breaks

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| wolf-p-a | t1 | lasher-b | engines | 48.6-52.7 | 25%-25% | 6 | doctrine: answering the ship shooting at her |
| wolf-p-a | t1, t2 | tarsus | belly, engines | 48.7-58.0 | 15%-32% | 10 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | hound-m | starboard flank | 48.9-56.6 | 21%-21% | 10 | doctrine: closest threat in arc and range |
| manticore-p | t3 | hound-m | starboard flank | 49.3-57.6 | 21%-21% | 6 | doctrine: closest threat in arc and range |
| hound-m | t2 | wolf-p-b | deck | 50.8-57.4 | 18%-18% | 27 | doctrine: closest threat in arc and range |
| wolf-p-a | t2 | phaeton | engines | 50.9-56.2 | 13%-22% | 15 | doctrine: closest threat in arc and range |
| tarsus | t1, t2 | wolf-p-a | bow | 51.5-53.1 | 16%-16% | 13 | doctrine: closest threat in arc and range |
| lasher-b | t2, t3 | wolf-p-a | belly, bow | 51.6-58.0 | 13%-17% | 23 | story order |
| wolf-p-a | t2 | colossus | engines | 53.7-54.3 | 16%-16% | 3 | doctrine: closest threat in arc and range |
| wolf-p-a | t1 | hound-m | belly | 54.2-54.5 | 22%-22% | 1 | doctrine: closest threat in arc and range |
| lasher-b | t2 | wolf-p-a | bridge | 54.4-55.7 | 60%-60% | 8 | story moment: wolf-p-a@56.8:rake |
| falcon-p | t7, t8 | hound-m | starboard flank | 56.3-58.0 | 15%-15% | 3 | doctrine: closest threat in arc and range |
| wolf-p-a | t2 | mule | engines | 56.3-58.0 | 17%-17% | 7 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | buffalo-b | belly | 56.9-58.0 | 21%-21% | 3 | doctrine: closest threat in arc and range |
| buffalo-b | t1 | wolf-p-a | deck | 57.0-58.0 | 18%-18% | 5 | doctrine: closest threat in arc and range |
| hound-m | t2 | manticore-p | port flank | 57.8-58.0 | 17%-17% | 2 | doctrine: closest threat in arc and range |

Silent: lasher-a (t1, t2, t3): burning and falling back out of the fight (S6)

Missiles: manticore-p -> phaeton (Harpoons)

Guns in reach: manticore-p from 48.0 s on hound-m; wolf-p-a from 48.0 s on hound-m; wolf-p-b from 48.0 s on hound-m; hound-m from 50.9 s on wolf-p-b; tarsus from 51.7 s on wolf-p-a; lasher-b from 51.8 s on wolf-p-a; falcon-p from 56.3 s on hound-m; buffalo-b from 57.0 s on wolf-p-a; morrow from 57.9 s on wolf-p-b; mule never (nearest wolf-p-a at 3312 m at the cut); buffalo-a never (nearest wolf-p-a at 3036 m at the cut); colossus never (nearest wolf-p-a at 2655 m at the cut); dram never (nearest wolf-p-a at 1488 m at the cut); lasher-lead never (nearest wolf-p-a at 2649 m at the cut); lasher-a never (nearest wolf-p-a at 2574 m at the cut); buffalo2-a never (nearest hound-m at 3381 m at the cut)

### S6b (k3c) 58-66 s: The escorts answer

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| manticore-p | t3 | wolf-m | deck | 57.5-66.0 | 16%-16% | 6 | doctrine: closest threat in arc and range |
| wolf-m | t1, t2 | hound-p-c | centre, starboard flank | 58.6-62.0 | 60%-60% | 8 | story moment: hound-p-c@61.6:rake, hound-p-c@61.9:kill |
| wolf-m | t2, t3 | hound-p-c | deck | 59.2-62.4 | 10%-15% | 17 | story order |
| lasher-b | t1, t2, t3 | hound-p-c | deck | 59.8-62.4 | 12%-12% | 38 | story order |
| hound-p-c | t2 | lasher-b | deck | 59.9-61.9 | 19%-19% | 10 | story order |
| wolf-m | t1, t2 | manticore-p | port flank | 63.8-66.0 | 8%-17% | 7 | doctrine: closest threat in arc and range |

Silent: hound-p-c (t1, t2): destroyed

Guns in reach: manticore-p from 56.0 s on wolf-m; wolf-m from 58.6 s on hound-p-c; lasher-b from 59.9 s on hound-p-c; hound-p-c from 59.9 s on wolf-m; buffalo2-a never (nearest wolf-m at 4040 m at the cut)

### S6c (k3d) 66-74 s: The column is cut

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| wolf-p-a | t1 | tarsus | drives | 64.7-72.3 | 23%-30% | 6 | story order |
| wolf-m | t1, t3 | wolf-p-a | deck, port flank | 64.8-74.0 | 27%-34% | 40 | story order |
| wolf-p-a | t2 | morrow | starboard flank | 64.8-66.9 | 13%-13% | 7 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | tarsus | drives | 64.9-74.0 | 26%-33% | 9 | story order |
| wolf-p-a | t1 | tarsus | drives, engines | 65.3-70.2 | 60%-60% | 5 | story moment: tarsus@68.0:rake, tarsus@70.5:breach |
| morrow | t1 | wolf-p-b | bow | 65.5-70.6 | 22%-22% | 15 | doctrine: closest threat in arc and range |
| buffalo-b | t1 | wolf-p-b | deck | 65.6-74.0 | 18%-18% | 33 | doctrine: closest threat in arc and range |
| colossus | t1, t2, t3, t4 | wolf-p-b | bow, engines | 66.3-74.0 | 21%-23% | 50 | doctrine: closest threat in arc and range |
| wolf-p-b | t2 | morrow | starboard flank | 66.4-68.5 | 17%-17% | 5 | doctrine: closest threat in arc and range |
| tarsus | t2, t3, t4 | wolf-p-b | deck | 66.5-74.0 | 17%-19% | 66 | story order |
| wolf-p-a | t2 | colossus | starboard flank | 66.9-67.7 | 16%-16% | 4 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | tarsus | drives | 67.6-69.2 | 60%-60% | 3 | story moment: tarsus@69.6:he, tarsus@70.5:breach |
| wolf-p-b | t2 | colossus | starboard flank | 68.5-69.2 | 19%-19% | 3 | doctrine: closest threat in arc and range |
| mule | t2, t3 | wolf-p-b | starboard flank | 69.2-70.7 | 60%-60% | 18 | story moment: wolf-p-b@72.4:rake |
| wolf-m | t1, t3 | wolf-p-a | port flank | 70.6-71.6 | 60%-60% | 7 | story moment: wolf-p-a@71.6:he |
| mule | t1, t2, t3 | wolf-p-b | belly | 71.6-74.0 | 17%-17% | 25 | story order |

Silent: tarsus (t1, t2): her aft deck burning over the shot-out drives; wolf-p-a (t1, t2): displacer blink; wolf-p-b (t1): displacer blink

Guns in reach: wolf-p-a from 64.0 s on buffalo-b; wolf-p-b from 64.0 s on buffalo-b; wolf-m from 64.0 s on wolf-p-a; buffalo-b from 64.5 s on wolf-p-b; morrow from 65.7 s on wolf-p-b; colossus from 66.4 s on wolf-p-b; tarsus from 66.5 s on wolf-p-b; mule from 69.2 s on wolf-p-b

### S6d (k3e) 74-80 s: The rear guard

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| hound-p-b | t2 | hound-m | deck, port flank | 72.1-73.4 | 60%-60% | 10 | story moment: hound-m@75.2:rake, hound-m@75.5:breach |
| hound-m | t2 | hound-p-a | bridge, centre | 72.3-75.6 | 60%-60% | 16 | story moment: hound-p-a@75.0:rake, hound-p-a@76.9:kill |
| hound-p-b | t2 | hound-m | belly | 73.4-80.0 | 20%-20% | 26 | story order |
| hound-p-a | t1, t2 | hound-m | belly, deck | 74.8-76.2 | 17%-17% | 10 | story order |

Silent: hound-p-a (t1, t2): destroyed

Guns in reach: hound-m from 72.0 s on hound-p-a; hound-p-a from 72.0 s on hound-m; hound-p-b from 72.0 s on hound-m

### S7 (k3b) 80-88 s: Boarding the Tarsus

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| tarsus | t3 | kite-p-a | starboard flank | 78.3-80.6 | 27%-27% | 11 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | tarsus | forward guns | 79.1-86.4 | 27%-28% | 9 | story order |
| tarsus | t3 | wolf-p-b | starboard flank | 82.7-84.4 | 20%-20% | 5 | story order |
| wolf-p-b | t1 | tarsus | bridge | 82.8-83.8 | 60%-60% | 2 | story moment: tarsus@84.0:rake |

Silent: tarsus (t1, t2): her aft deck burning over the shot-out drives; tarsus (t3, t4): silenced by the Wolf (P)'s covering run before the boats land

Guns in reach: tarsus from 78.0 s on kite-p-a; kite-p-b from 78.0 s on tarsus; wolf-p-b from 78.9 s on tarsus; kite-p-a never (nearest tarsus at 76 m at the cut); colossus never (nearest kite-p-a at 1463 m at the cut); morrow never (nearest kite-p-a at 1450 m at the cut); mule never (nearest kite-p-a at 2192 m at the cut); buffalo-a never (nearest kite-p-a at 1917 m at the cut)

### S8-S9 (k4) 88-102 s: She will not be taken

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| wolf-p-a | t1 | colossus | port flank | 86.8-89.0 | 24%-24% | 3 | story order |
| colossus | t1, t2 | kite-p-b | bow | 87.2-95.2 | 19%-19% | 56 | story order |
| kite-p-b | t3 | colossus | port flank | 87.6-95.6 | 26%-26% | 36 | story order |
| colossus | t2 | wolf-p-a | starboard flank | 94.5-95.9 | 60%-60% | 8 | story moment: wolf-p-a@95.8:he |
| colossus | t1 | colossus3 | port flank | 95.2-95.9 | 60%-60% | 4 | story moment: colossus3@96.0:rake |
| morrow | t1 | kite-p-b | engines | 95.6-102.0 | 18%-18% | 21 | doctrine: closest threat in arc and range |
| colossus | t1 | kite-p-b | starboard flank | 95.9-96.0 | 60%-60% | 1 | story moment: kite-p-b@96.5:he |
| colossus | t2 | wolf-p-a | port flank | 95.9-96.5 | 12%-12% | 3 | story order |
| colossus3 | t3, t4, t5, t7, t9 | borer-c | deck | 96.6-97.7 | 5%-5% | 20 | point defence |
| colossus | t1, t2 | colossus3 | starboard flank | 97.5-102.0 | 37%-38% | 34 | story order |
| colossus3 | t1, t10, t11, t12, t3, t7, t8, t9 | borer-d | deck | 97.7-101.1 | 5%-5% | 51 | point defence |
| wolf-p-a | t1 | borer-c | deck | 99.2-100.3 | 5%-5% | 2 | point defence |

Guns in reach: wolf-p-a from 86.0 s on borer-d; kite-p-b from 87.2 s on borer-a; colossus from 87.4 s on kite-p-b; colossus3 from 91.9 s on colossus; morrow from 95.6 s on kite-p-b

### S9b (k4b) 102-110 s: The lead's last run

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| falcon-p | t7, t8 | lasher-lead | bridge, deck, engines | 101.6-107.1 | 60%-60% | 6 | story moment: lasher-lead@103.5:breach, lasher-lead@106.9:rake, lasher-lead@107.6:breach |
| lasher-lead | t1, t2, t3 | falcon-p | starboard flank | 102.1-107.2 | 22%-30% | 44 | story order |
| falcon-p | t7 | lasher-lead | starboard flank | 102.8-106.3 | 20%-20% | 3 | story order |
| lasher-lead | t1, t2, t3 | falcon-p | bridge, starboard flank | 103.0-104.7 | 60%-60% | 23 | story moment: falcon-p@104.8:rake, falcon-p@105.0:breach |

Silent: lasher-lead (t1, t2, t3): tumbling away burning after the broadside (S9b)

Guns in reach: falcon-p from 100.0 s on lasher-lead; lasher-lead from 102.1 s on falcon-p

