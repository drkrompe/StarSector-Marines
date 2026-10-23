# Asharu battle: fire control

Status: DRAFT. The decision layer is in code (`asharu_gunnery.py`). `scene_fx.planned` does not consume it yet.
Written: 2026-10-23
Updated: 2026-10-23. Every ledger gunfire hit delivered (asharu_damage 43ac3f985). Matched to `scene_fx.planned` (139192a23): its range gate, round flight lead, `walk_r`, `track()`; ledger `by=None` hits left to the ledger.

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

**Range.** The plan asks only for what `planned()` will fire. Every round, a story moment's included,
is gated as `scene_fx` gates it: no further than its speed x `EXEC_FLIGHT_S` (2.2 s, mirrored from
`MAX_FLIGHT_S`), `EXEC_RANGE_M` (3500 m) or the game's range x `RANGE_SCALE` (5). That gives a
light MG 990 m, the needler 1980 m, the ion cannon 1540 m, the blasters 1320 m and the Hellbore 374 m.
`planned()` has no `force`, so a moment past the gate would only be range-skipped there (S6 verify
span, 52-54 s); `FORCE_PAST_GATE` stays False and `--check` reports the moment instead.

**What fires at long range: missiles.** At the kit's round speeds no gun reaches 2.5-4 km.
- **S5 and the first half of S6 belong to the Harpoons.** `shot_plan()` lists each shot's salvos (`missiles`) and when each ship's guns first have a hostile in reach (`opening`).
- **In S6 the first guns in reach** are the Wolves (P) at 48-49 s and the Manticore's needler at 51.5 s, all on the convoy's Hound, and Lasher B on Wolf (P) A at 53.9 s.
- **Out of reach for the whole shot:** the lead Lasher and the Manticore never close to gun range on each other in S6 (2.6 km at the cut), and the Falcon (P) is 2.7 km from everyone.

**The 2.2 s gate also costs the 1.1-1.3 km fights.** It drops five story rakes:
- Lasher B and the merchant Wolf on Hound (P) C (S6b);
- the Mule on Wolf (P) B (S6c);
- Hound (P) B on the convoy's Hound, twice (S6d).

At 3.0 s (light MG 1350 m), all but one come back: Lasher B's opening rake at 60.0 s on Hound (P) C, which the ledger could retime. **Recommended: `MAX_FLIGHT_S` 3.0 in `scene_fx` and `EXEC_FLIGHT_S` 3.0 here, together.** The S6 long-range hits on the lead Lasher and Lasher B stay out of reach either way. They want Harpoons (a second salvo onto the lead, whose 55.9 s breach is a warhead's), or the ledger's own story hits.

**The S6d Hound** (owner, 2026-10-23: angle its gun up to track):
- Her dorsal turret (t2) takes Hound (P) A from −9 degrees up to +33 degrees over 72.5-73.3 s.
- She rolls over, and Hound (P) A passes under her deck. The gun holds and keeps tracking (`track()`).
- She comes back up at +36 to +44 degrees for the kill burst at 76.9-77.2 s.

**Mounts.** `MOUNTS` is each finished build's kit turrets (pivot, rest bearing, arc), from the manifests. The turret limit is −10 to +85 degrees off the deck, and straight up past 75 degrees in any azimuth. Stand-ins use the game's weapon slots.

Assumptions to confirm against the kit anchors:
- the Hound's forward hardpoint is turret 1, her dorsal turret 2;
- the Falcon (P)'s two IR-pulse guns are kit turrets 7 and 8.

## What the plan contradicts or cannot satisfy

`--check` lists these. Nothing is faked.

1. **At `scene_fx`'s 2.2 s gate, eight ledger gunfire hits are out of reach.**
   - Three long-range S6 hits: `lasher-lead@53.8` and `@55.9` from the Manticore, and `lasher-b@55.0` from the Falcon.
   - Five 1.1-1.3 km rakes in S6b, S6c and S6d.
   - At 3.0 s only the three S6 hits and `hound-p-c@60.0` remain (see Range). Until the gate is settled, the ledger should not convert them.
2. **S8-S9: the Colossus Mk.III cannot fire on the Colossus at all.**
   - At 64 degrees of roll the Colossus sits 13-23 degrees below her deck on her low side, past the −10 degree limit, on all twelve turrets.
   - The 89 s pod hit is the ledger's own story hit.
   - Fix: about 10-15 degrees more roll (to roughly 75-80) over 88-96 s, or sit her higher.
3. **S6: the Phaeton cannot defend herself.** S6 uses her gunless wreck build throughout.
4. **The Falcon (P) reads as lightly armed.** She has two kit gun turrets firing a stand-in heavy blaster, and is out of gun reach in S6. Her broadside is missiles: S6 and S9b may want `missiles` entries for her.
5. **Incidental fire the story does not mention.** Doctrine finds it, mostly misses, with `ledger` None:
   - the Wolves (P) on the convoy's Hound (S6);
   - the column's guns on Wolf (P) B (S6c);
   - the Tarsus on Kite (P) A (S7).

## Fire plan per shot

Generated by `python asharu_gunnery.py --summary`: turrets grouped per ship, target and intent. `--table` has every engagement. Windows may open before a shot's cut, because rounds fired then land in the shot. The shots not listed (S1a, S1, S2, S3, S4, S10, S11, S12) are ceasefire.

### S5 (m5) 44-50 s: The opening

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| wolf-p-b | t1 | hound-m | belly | 49.7-50.0 | 16%-16% | 1 | doctrine: closest threat in arc and range |

Missiles: manticore-p -> phaeton (Harpoons)

Guns in reach: wolf-p-b from 48.9 s on hound-m; phaeton never (nearest wolf-p-b at 3803 m at the cut); tarsus never (nearest wolf-p-b at 3515 m at the cut); buffalo-b never (nearest wolf-p-b at 3304 m at the cut); dram never (nearest wolf-p-b at 3091 m at the cut); hound-m never (nearest wolf-p-b at 2851 m at the cut); manticore-p never (nearest hound-m at 3221 m at the cut)

### S6 (k3) 50-58 s: The screen breaks

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| wolf-p-a | t1 | hound-m | belly | 48.3-54.5 | 22%-22% | 7 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | hound-m | starboard flank | 50.2-56.6 | 18%-18% | 10 | doctrine: closest threat in arc and range |
| manticore-p | t3 | hound-m | starboard flank | 52.8-58.0 | 17%-17% | 3 | doctrine: closest threat in arc and range |
| lasher-b | t2 | wolf-p-a | bridge | 54.8-56.0 | 60%-60% | 7 | story moment: wolf-p-a@56.8:rake |
| wolf-p-a | t2 | phaeton | engines | 55.0-56.2 | 19%-19% | 5 | doctrine: closest threat in arc and range |
| hound-m | t2 | wolf-p-b | deck | 55.4-57.4 | 15%-15% | 10 | doctrine: closest threat in arc and range |
| wolf-p-a | t1 | lasher-b | starboard flank | 55.4-55.8 | 28%-28% | 1 | doctrine: answering the ship shooting at her |
| lasher-b | t2 | wolf-p-a | belly | 56.0-58.0 | 12%-12% | 7 | story order |
| wolf-p-b | t1 | buffalo-b | belly | 56.7-58.0 | 20%-20% | 3 | doctrine: closest threat in arc and range |
| wolf-p-a | t1 | tarsus | belly | 57.3-58.0 | 31%-31% | 2 | doctrine: closest threat in arc and range |

Silent: lasher-a (t1, t2, t3): burning and falling back out of the fight (S6)

Missiles: manticore-p -> phaeton (Harpoons)

Guns in reach: wolf-p-a from 48.0 s on hound-m; wolf-p-b from 49.4 s on hound-m; manticore-p from 51.5 s on hound-m; hound-m from 53.5 s on wolf-p-b; lasher-b from 53.9 s on wolf-p-a; buffalo-b from 57.0 s on wolf-p-a; mule never (nearest wolf-p-a at 3312 m at the cut); buffalo-a never (nearest wolf-p-a at 3036 m at the cut); colossus never (nearest wolf-p-a at 2655 m at the cut); morrow never (nearest wolf-p-a at 2617 m at the cut); tarsus never (nearest wolf-p-a at 1980 m at the cut); dram never (nearest wolf-p-a at 1488 m at the cut); lasher-lead never (nearest wolf-p-a at 2649 m at the cut); lasher-a never (nearest wolf-p-a at 2574 m at the cut); falcon-p never (nearest hound-m at 2680 m at the cut); buffalo2-a never (nearest hound-m at 3381 m at the cut)

Cannot deliver: lasher-b@55.0:rake (falcon-p t7 nearest: heavyblaster at 2838 m: round flight 4.7 s, past the execution gate (1320 m)); lasher-lead@53.8:rake (manticore-p t3 nearest: lightneedler at 3691 m: round flight 4.1 s, past the execution gate (1980 m)); lasher-lead@55.9:breach (manticore-p t3 nearest: lightneedler at 3199 m: round flight 3.6 s, past the execution gate (1980 m))

### S6b (k3c) 58-66 s: The escorts answer

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| wolf-m | t1, t2, t3 | hound-p-c | centre | 60.3-62.0 | 60%-60% | 10 | story moment: hound-p-c@61.9:kill |
| lasher-b | t1, t2, t3 | hound-p-c | centre | 61.0-61.4 | 60%-60% | 9 | story moment: hound-p-c@61.9:kill |
| lasher-b | t1, t2, t3 | hound-p-c | deck | 61.4-62.4 | 14%-14% | 15 | story order |
| wolf-m | t2, t3 | hound-p-c | deck | 61.4-62.4 | 13%-14% | 8 | story order |
| manticore-p | t3 | lasher-b | starboard flank | 64.4-66.0 | 16%-16% | 3 | doctrine: closest threat in arc and range |

Silent: hound-p-c (t1, t2): destroyed

Guns in reach: wolf-m from 60.3 s on hound-p-c; hound-p-c from 60.8 s on wolf-m; lasher-b from 61.0 s on hound-p-c; manticore-p from 62.8 s on lasher-b; buffalo2-a never (nearest wolf-m at 4040 m at the cut)

Cannot deliver: hound-p-c@60.0:rake (lasher-b t2 nearest: lightmg at 1295 m: round flight 2.9 s, past the execution gate (990 m)); hound-p-c@61.0:rake (wolf-m t2 nearest: out of range (1261 m > 990))

### S6c (k3d) 66-74 s: The column is cut

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| wolf-p-a | t1 | buffalo-b | starboard flank | 64.7-65.8 | 18%-18% | 2 | doctrine: closest threat in arc and range |
| wolf-m | t1, t3 | wolf-p-a | deck, port flank | 64.8-74.0 | 26%-33% | 38 | story order |
| wolf-p-b | t1 | buffalo-b | starboard flank | 65.1-65.5 | 18%-18% | 1 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | tarsus | drives | 65.8-74.0 | 22%-31% | 7 | story order |
| wolf-p-a | t1 | tarsus | drives, engines | 65.8-70.3 | 60%-60% | 5 | story moment: tarsus@68.0:rake, tarsus@70.5:breach |
| wolf-p-b | t2 | morrow | starboard flank | 66.4-68.5 | 14%-14% | 5 | doctrine: closest threat in arc and range |
| wolf-p-a | t1 | tarsus | drives | 66.9-72.3 | 25%-26% | 6 | story order |
| wolf-p-b | t1 | tarsus | drives | 67.8-69.0 | 60%-60% | 3 | story moment: tarsus@69.6:he, tarsus@70.5:breach |
| morrow | t1 | wolf-p-b | bow | 68.1-70.6 | 20%-20% | 10 | doctrine: closest threat in arc and range |
| colossus | t1, t2, t3, t4 | wolf-p-b | bow, engines | 68.3-74.0 | 17%-22% | 23 | doctrine: closest threat in arc and range |
| buffalo-b | t1 | wolf-p-a | deck | 68.4-74.0 | 17%-17% | 20 | doctrine: closest threat in arc and range |
| wolf-p-b | t2 | colossus | starboard flank | 68.5-69.2 | 17%-17% | 3 | doctrine: closest threat in arc and range |
| tarsus | t2, t3, t4 | wolf-p-b | deck | 68.8-74.0 | 14%-17% | 35 | story order |
| wolf-m | t1, t3 | wolf-p-a | port flank | 70.2-71.6 | 60%-60% | 10 | story moment: wolf-p-a@71.6:he |
| mule | t2, t3 | wolf-p-b | belly | 71.8-73.9 | 13%-14% | 12 | story order |

Silent: tarsus (t1, t2): her aft deck burning over the shot-out drives; wolf-p-a (t1): displacer blink; wolf-p-b (t1): displacer blink

Guns in reach: wolf-p-a from 64.0 s on buffalo-b; wolf-p-b from 64.0 s on buffalo-b; wolf-m from 64.0 s on wolf-p-a; buffalo-b from 66.5 s on wolf-p-a; morrow from 67.3 s on wolf-p-b; colossus from 68.0 s on wolf-p-b; tarsus from 68.4 s on wolf-p-b; mule from 70.6 s on wolf-p-b

Cannot deliver: wolf-p-b@71.2:rake (mule t2 nearest: out of range (1255 m > 990))

### S6d (k3e) 74-80 s: The rear guard

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| hound-m | t2 | hound-p-a | bridge, centre | 72.8-77.2 | 60%-60% | 7 | story moment: hound-p-a@75.0:rake, hound-p-a@76.9:kill |
| hound-p-a | t1 | hound-m | deck | 75.6-76.2 | 15%-15% | 3 | story order |
| hound-p-b | t2 | hound-m | belly | 77.5-80.0 | 19%-19% | 14 | story order |

Silent: hound-p-a (t1, t2): destroyed

Guns in reach: hound-m from 72.8 s on hound-p-a; hound-p-a from 72.9 s on hound-m; hound-p-b from 75.0 s on hound-m

Cannot deliver: hound-m@75.2:rake (hound-p-b t2 nearest: out of range (1179 m > 990)); hound-m@75.5:breach (hound-p-b t2 nearest: out of range (1144 m > 990))

### S7 (k3b) 80-88 s: Boarding the Tarsus

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| tarsus | t3 | kite-p-a | starboard flank | 78.3-80.6 | 27%-27% | 11 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | tarsus | forward guns | 79.8-86.4 | 25%-25% | 8 | story order |
| tarsus | t3 | wolf-p-b | starboard flank | 82.7-84.4 | 17%-17% | 5 | story order |
| wolf-p-b | t1 | tarsus | bridge | 82.7-83.9 | 60%-60% | 3 | story moment: tarsus@84.0:rake |

Silent: tarsus (t1, t2): her aft deck burning over the shot-out drives; tarsus (t3, t4): silenced by the Wolf (P)'s covering run before the boats land

Guns in reach: tarsus from 78.0 s on kite-p-a; kite-p-b from 78.0 s on tarsus; wolf-p-b from 78.9 s on tarsus; kite-p-a never (nearest tarsus at 76 m at the cut); colossus never (nearest kite-p-a at 1463 m at the cut); morrow never (nearest kite-p-a at 1450 m at the cut); mule never (nearest kite-p-a at 2192 m at the cut); buffalo-a never (nearest kite-p-a at 1917 m at the cut)

### S8-S9 (k4) 88-102 s: She will not be taken

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| colossus | t1, t2 | kite-p-b | bow | 89.8-94.8 | 19%-19% | 36 | story order |
| kite-p-b | t3 | colossus | port flank | 90.2-95.6 | 24%-24% | 27 | story order |
| colossus | t2 | wolf-p-a | starboard flank | 94.7-95.9 | 60%-60% | 7 | story moment: wolf-p-a@95.8:he |
| colossus | t1 | colossus3 | port flank | 94.8-95.8 | 60%-60% | 6 | story moment: colossus3@96.0:rake |
| colossus | t1 | kite-p-b | starboard flank | 95.8-96.0 | 60%-60% | 2 | story moment: kite-p-b@96.5:he |
| colossus | t2 | wolf-p-a | port flank | 95.9-96.5 | 12%-12% | 3 | story order |
| colossus3 | t3, t4, t5 | borer-c | deck | 96.8-97.7 | 5%-5% | 12 | point defence |
| morrow | t1 | kite-p-b | engines | 97.6-100.2 | 18%-18% | 10 | doctrine: closest threat in arc and range |
| colossus | t1, t2 | colossus3 | starboard flank | 97.6-102.0 | 36%-37% | 30 | story order |
| colossus3 | t10, t11, t12, t9 | borer-d | deck | 98.3-100.1 | 5%-5% | 20 | point defence |

Guns in reach: wolf-p-a from 88.9 s on colossus; kite-p-b from 89.7 s on borer-a; colossus from 89.9 s on kite-p-b; colossus3 from 91.9 s on colossus; morrow from 95.6 s on kite-p-b

### S9b (k4b) 102-110 s: The lead's last run

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| falcon-p | t7, t8 | lasher-lead | bridge, engines | 101.8-107.1 | 60%-60% | 5 | story moment: lasher-lead@103.5:breach, lasher-lead@107.6:breach, lasher-lead@108.0:rake |
| lasher-lead | t1, t2, t3 | falcon-p | starboard flank | 102.3-107.2 | 21%-26% | 39 | story order |
| falcon-p | t7 | lasher-lead | starboard flank | 103.1-106.3 | 18%-18% | 3 | story order |
| lasher-lead | t1, t2, t3 | falcon-p | bridge, starboard flank | 103.4-104.8 | 60%-60% | 17 | story moment: falcon-p@104.8:rake, falcon-p@105.0:breach |

Silent: lasher-lead (t1, t2, t3): tumbling away burning after the broadside (S9b)

Guns in reach: falcon-p from 100.0 s on lasher-lead; lasher-lead from 102.1 s on falcon-p

