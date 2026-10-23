# Asharu battle: fire control

Status: DRAFT. The decision layer is in code (`asharu_gunnery.py`). `scene_fx` does not call it yet.
Written: 2026-10-23

`scene_fx` executes gunfire. It trains each turret at its game turn rate on both axes, fires
bursts only when the turret is on target, in range and has a clear line, flies the rounds straight
and walks each burst so most rounds miss. `asharu_gunnery` decides what that execution layer
fires at, turret by turret: which target, when, at which part of the hull, and how well. It is
plain Python over the blockout's flight paths (`battle_blockout.SHOTS`). It needs no Blender and
no build on disk, and plans every shot in about three seconds.

## The call

```python
import asharu_gunnery
engs = asharu_gunnery.plan(shot_kf, ship_key, turret, weapon, t0, t1)
```

- `shot_kf` may be a key frame (`k3`) or a motion-span frame (`k3_f01320`).
- `turret` is the kit's turret number (`ctx.anchors.guns.turret`). `weapon` is the `WEAPONS` key
  the gun is armed with. Range, turn rate and cadence come from `weapon`.
- Returns **None** when the module has no opinion: an unknown shot, or a ship that is not in the
  shot. Fall back to the blockout's `fx` entry.
- Returns **[]** to hold fire for the whole window.
- Otherwise returns a list of engagements in start order. Each is a dict:

| field | meaning |
|---|---|
| `target` | the ship key fired at (a hull, or a drone for point defence) |
| `region`, `aim` | what is aimed at, and where: metres in the **target's** frame (+X bow, +Y port, +Z dorsal) |
| `walk_to` | for a rake, where the burst walks to (target frame), else None |
| `start`, `end` | sequence seconds, clipped to `[t0, t1]` |
| `bursts` | `[(on, off), ...]` when the trigger is held: concrete and seeded, so the gun code and the ledger see the same bursts |
| `burst_s`, `gap_s` | the (lo, hi) ranges the bursts were drawn from |
| `hit_share` | share of rounds that should strike (hull or shield); the rest fly past and on |
| `walk_m` | how far off the hull the misses pass; the burst walks inside that ring |
| `kind` | `'hull'` or `'pd'` (point defence on a drone) |
| `force` | True for a story moment: the range gate is waived, arc and line are not |
| `ledger` | the `asharu_damage` Hit id this burst delivers, or None for incidental fire |
| `why` | the doctrine in words |
| `shooter`, `turret`, `weapon`, `range_m` | as named; `range_m` is the median over the engagement |

Two more calls:
- `shot_plan(kf)` gives every engagement in a shot, the holds of fire and each ledger moment's
  status.
- `expected_hits(eng)` gives rounds fired, expected hits and damage, from the cadence.

From the command line:
- `python asharu_gunnery.py --summary | --table [kf ...] | --check | --json PATH`

## How it decides

1. **Story moments first.** The damage ledger (`asharu_damage.LEDGER`) is the authority on what
   hits whom, and when:
   - **Which hits count:** every hit drawn by a tracer, with its shooter named in `src`, and every
     `GUN_MADE` hit. `GUN_MADE` covers the ledger's kills and breaches that a blast or a fire draws
     but a gun caused: the Feeder burst that breaks up Hound (P) C, the Hellbore slug that turns the
     lead, the Tarsus's drives.
   - **Who fires each moment:** each one goes to every mount of the shooter that bears on its spot
     with a clear line within 0.8 s before the hit to 0.4 s after it.
   - **Clashes:** a mount already busy with another moment within 1.2 s is skipped, and the
     hardest moment is allocated first.
   - **The burst itself:** a held, decisive burst at the ledger's spot, walking toward its `to`,
     with 60% of rounds on target (`MOMENT_HIT`).
2. **Doctrine for the rest, every 0.1 s.** Each mount scores every hostile in arc, in range and
   with a clear line:
   - the shot's standing order (`INTENT`: the ship the story says is being attacked);
   - nearness against weapon range;
   - whether the target is armed, or is shooting at this ship;
   - a strong bonus for staying on the current target, so guns do not flicker between targets;
   - point defence only on small craft inside 700 m.

   A run under 0.6 s is dropped. Each new target costs slew time at the game turn rate before
   the first burst.
3. **Aim:** the presented aspect, centre mass of the face the shooter sees (a flank, the deck, the
   belly, bow or engines), unless the story names a spot (`INTENT_AIM`: the Tarsus's drives, the
   Colossus's port pod, the Falcon's starboard flank).
4. **Accuracy:** a base share by weapon family, cut by range against reach, by a small hull, and
   by the crossing rate against the turret's turn rate, then times `MISS_TASTE` (.8). Point
   defence on a 3.6 m Borer lands 5%. Misses pass up to about one and a quarter hull lengths off.
   Shares per shot come out at 19-49% of rounds.
5. **Holding fire:**
   - `CEASEFIRE` covers whole shots:
     - S1a, S1, S2: before the ambush;
     - S3: running dark;
     - S4: still climbing; the Manticore opens in S5;
     - S10: "Let her go";
     - S11, S12: entry and the wreckfall.
   - `SILENT` silences ships:
     - Lasher A burning;
     - the Phaeton struck;
     - the lead tumbling after the broadside;
     - kills, from `asharu_damage.killed_at`, and a dead hull stops drawing fire 0.4 s after.
   - `SILENT_TURRET` silences turrets: the Tarsus's aft guns under her drive fire, and her
     forward guns once the covering Wolf (P) has raked them.
   - `HOLD_ON` keeps the prize safe: nobody fires on the Tarsus in S7 while the boats land,
     except the standing order to silence her guns.
   - The Wolves (P) hold fire through each Displacer blink.

**Range.** The shots fight at 0.3-5 km, and the opening exchanges at 2.5-4 km. The game's own
ranges (300-900) would silence all of S5 and S6. `RANGE_SCALE` (5 m per game unit) is the one
cinematic knob: a Hellbore reaches 4.5 km, a light MG 1.5 km. **The execution layer should gate
range with `asharu_gunnery.reach_m(weapon)`, so that both layers agree.** A story moment
(`force`) may exceed it; at 3.8 km the Falcon's stand-in heavy blaster does.

**Mounts.** `MOUNTS` holds each finished build's kit turrets (pivot, rest bearing, arc), read off
the manifests, so the module needs no build on disk. Turret limits are −10 to +85 degrees off the
deck (`battle_blockout.GUN_ELEVATION`), and straight up past 75 degrees in any azimuth. Stand-ins
use the game's weapon slots, so the Mule's and the Buffalos' rounds are planned too, for the
ledger, though the kit does not draw them.

Two assumptions to confirm against the kit anchors:
- **Hound turret numbers:** the forward hardpoint (WS 001, chaingun) is taken as 1 and the dorsal
  turret (WS 002) as 2.
- **The Falcon (P)'s guns:** her two IR-pulse turrets are kit turrets 7 and 8.

## What the plan contradicts or cannot satisfy

`python asharu_gunnery.py --check` lists these. Each needs a flight change, a turret limit
change, or a ledger change. The plan does not fake any of them.

1. **S8-S9: the Colossus Mk.III cannot fire on the Colossus at all.**
   - **The geometry:** at 64 degrees of roll, the port pod (and the rest of the Colossus) sits 13
     to 23 degrees below her deck plane on her low side, past the −10 degree limit, on every one
     of her twelve turrets. The blockout's `--check` says "bears" only because it measures centre
     to centre.
   - **What is lost:** the ledger's `colossus@89.0:rake` and `colossus@94.9:he` (the pod shot
     out) have no shooter. The Mk.III's guns spend S9 on point defence against the Borers.
   - **The fix:** about 10-15 degrees more roll (to roughly 75-80) over 88-96 s, or sit her
     higher over the Colossus.
2. **S6c: `wolf-m@73.0:he`, "a blaster graze as she chases the Wolves (P)".** The merchant Wolf is
   dead astern of both Wolves (P), 77-87 degrees outside their 150-degree forward arcs, so neither
   can graze her. Either drop the hit, or give it to the Tarsus's or Morrow's fire, which a Wolf
   (P)'s shield would not have. That second option is a ledger change.
3. **S8-S9: `wolf-p-a@99.0:he`, from the Colossus.** At 99 s the Wolf (P) is 21 degrees outside
   the Colossus's port turrets' arcs. The nearest delivery is earlier, as she passes at 95-96 s.
4. **S9b: `lasher-lead@108.0:rake`, "the rest of the broadside".** The tumbling Lasher has left
   the Falcon's forward turret arc by 5 degrees. `lasher-lead@107.6:breach` lands; 108.0 does not.
5. **Range license.** The Falcon's S6 hits on the lead Lasher (`lasher-lead@55.4:he`, at 3.8 km)
   and the Buffalo Mk.II's on Lasher B (S6b, at 3.8 km with light MGs) are beyond even the scaled
   range. They are kept as story moments (`force`). If the execution layer gates range strictly,
   they will not fire.
6. **S6: the Phaeton cannot defend herself.** S6 uses the Phaeton's wreck build throughout, and
   the wreck build has no guns, so her point defence never meets the Harpoons. S5 (preview only)
   flies her intact, but it is not lit.
7. **The Falcon (P) reads as lightly armed.** She has only two kit gun turrets. Her stand-in heavy
   blaster fires once a second, so the flagship puts out about 15 rounds over S6 and S9b. Her
   real broadside is missiles (Typhoon, Annihilator, Swarmer), planned by `missiles` entries, not
   here. "The Falcon's broadside answers" may want a missile entry in S9b.
8. **Incidental fire the story does not mention.** The plan has it because doctrine finds targets
   in arc and range:
   - the Wolves (P) and the Manticore's light guns shoot at the convoy's Hound and freighters in
     S5-S6;
   - the Colossus, Morrow and Buffalo B fire on Wolf (P) B as she passes the column in S6c;
   - the Tarsus fires on Kite (P) A alongside before the boats land.

   These are mostly misses. They are ledger-free (`ledger` None), so the damage ledger may scorch
   them at severity 0, or ignore them.

## Fire plan per shot

Generated by `python asharu_gunnery.py --summary`, which groups turrets per ship, target and
intent. `--table` has every engagement. Windows may open up to 2 s before a shot's cut, because
rounds are already in flight at the cut. The shots not listed (S1a, S1, S2, S3, S4, S10, S11, S12)
are ceasefire.

### S5 (m5) 44-50 s: The opening

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| manticore-p | t2, t3, t4 | hound-m | belly | 43.3-48.7 | 19%-21% | 30 | doctrine: closest threat in arc and range |
| manticore-p | t1, t2, t3, t4 | phaeton | engines, starboard flank | 45.0-50.0 | 18%-26% | 15 | story order |
| wolf-p-b | t1 | hound-m | belly | 45.9-50.0 | 20%-20% | 6 | doctrine: closest threat in arc and range |

### S6 (k3) 50-58 s: The screen breaks

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| wolf-p-a | t1, t2 | tarsus | belly, engines | 48.3-58.0 | 19%-33% | 9 | doctrine: closest threat in arc and range |
| wolf-p-a | t1 | lasher-b | engines | 48.6-52.7 | 27%-27% | 6 | doctrine: answering the ship shooting at her |
| wolf-p-b | t1 | hound-m | starboard flank | 48.9-56.6 | 22%-22% | 10 | doctrine: closest threat in arc and range |
| manticore-p | t2, t3, t4 | hound-m | starboard flank | 49.3-53.0 | 22%-25% | 22 | doctrine: closest threat in arc and range |
| wolf-p-a | t2 | phaeton | engines | 50.9-53.7 | 17%-17% | 10 | doctrine: closest threat in arc and range |
| falcon-p | t7, t8 | hound-m | starboard flank | 51.0-58.0 | 19%-22% | 3 | doctrine: closest threat in arc and range |
| manticore-p | t1, t2, t3, t4 | lasher-lead | belly, engines, port flank | 51.0-58.0 | 18%-25% | 18 | story order |
| hound-m | t2 | wolf-p-b | deck | 51.4-57.4 | 19%-19% | 25 | doctrine: closest threat in arc and range |
| tarsus | t1, t2 | wolf-p-a | bow | 51.4-53.1 | 17%-17% | 12 | doctrine: closest threat in arc and range |
| falcon-p | t7, t8 | lasher-a | bridge | 52.0-53.2 | 60%-60% | 4 | story moment: lasher-a@52.8:he |
| lasher-b | t2, t3 | wolf-p-a | belly, port flank | 52.7-58.0 | 14%-19% | 19 | story order |
| manticore-p | t1, t2, t3, t4 | lasher-lead | bow, deck | 53.0-56.4 | 60%-60% | 32 | story moment: lasher-lead@53.8:rake, lasher-lead@55.9:breach |
| wolf-p-a | t2 | colossus | engines | 53.7-54.3 | 20%-20% | 3 | doctrine: closest threat in arc and range |
| wolf-p-a | t1 | hound-m | belly | 54.2-54.5 | 22%-22% | 1 | doctrine: closest threat in arc and range |
| falcon-p | t7, t8 | lasher-b | starboard flank | 54.2-55.5 | 60%-60% | 3 | story moment: lasher-b@55.0:rake |
| wolf-p-a | t2 | lasher-lead | port flank | 54.3-56.0 | 22%-22% | 6 | doctrine: closest threat in arc and range |
| lasher-lead | t1, t2, t3 | manticore-p | bow | 54.7-56.0 | 60%-60% | 24 | story moment: manticore-p@55.5:rake |
| falcon-p | t7 | lasher-lead | port flank | 55.2-55.9 | 60%-60% | 1 | story moment: lasher-lead@55.4:he |
| falcon-p | t7, t8 | lasher-b | starboard flank | 55.5-58.0 | 20%-21% | 2 | story order |
| lasher-b | t2, t3 | wolf-p-a | bridge | 56.0-57.2 | 60%-60% | 12 | story moment: wolf-p-a@56.8:rake |
| wolf-p-a | t2 | mule | engines | 56.0-58.0 | 22%-22% | 7 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | buffalo-b | belly | 56.9-58.0 | 22%-22% | 3 | doctrine: closest threat in arc and range |

Silent: lasher-a (t1, t2, t3): burning and falling back out of the fight (S6)

### S6b (k3c) 58-66 s: The escorts answer

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| manticore-p | t1, t2, t3, t4 | wolf-m | deck, port flank | 57.4-66.0 | 17%-20% | 45 | doctrine: closest threat in arc and range |
| lasher-b | t1, t2, t3 | hound-p-c | centre, port flank | 59.2-62.4 | 60%-60% | 46 | story moment: hound-p-c@60.0:rake, hound-p-c@61.9:kill |
| wolf-m | t2, t3 | hound-p-c | deck | 59.5-62.4 | 8%-18% | 12 | story order |
| wolf-m | t1, t2, t3 | hound-p-c | centre, starboard flank | 60.2-62.4 | 60%-60% | 15 | story moment: hound-p-c@61.0:rake, hound-p-c@61.9:kill |
| lasher-b | t1, t2, t3 | hound-p-c | deck | 60.5-61.1 | 11%-11% | 10 | story order |
| buffalo2-a | t1, t2 | lasher-b | deck | 60.7-62.0 | 60%-60% | 16 | story moment: lasher-b@61.5:rake |
| manticore-p | t4 | lasher-b | starboard flank | 63.1-63.7 | 24%-24% | 3 | doctrine: closest threat in arc and range |
| wolf-m | t1, t2 | manticore-p | port flank | 63.1-66.0 | 9%-19% | 8 | doctrine: closest threat in arc and range |

Silent: hound-p-c (t1, t2): destroyed

### S6c (k3d) 66-74 s: The column is cut

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| wolf-p-a | t2 | morrow | starboard flank | 64.1-66.9 | 15%-15% | 11 | doctrine: closest threat in arc and range |
| wolf-p-a | t1 | tarsus | drives | 64.7-72.3 | 26%-28% | 5 | story order |
| wolf-m | t1, t3 | wolf-p-a | deck, port flank | 64.8-74.0 | 27%-34% | 34 | story order |
| wolf-p-b | t1 | tarsus | drives | 64.9-74.0 | 29%-34% | 10 | story order |
| buffalo-b | t1 | wolf-p-b | deck | 65.7-74.0 | 18%-18% | 28 | doctrine: closest threat in arc and range |
| morrow | t1 | wolf-p-b | bow | 65.7-70.6 | 22%-22% | 17 | doctrine: closest threat in arc and range |
| colossus | t1, t2, t3, t4 | wolf-p-b | bow, engines | 66.1-74.0 | 22%-23% | 35 | doctrine: closest threat in arc and range |
| wolf-p-b | t2 | morrow | starboard flank | 66.4-68.5 | 19%-19% | 5 | doctrine: closest threat in arc and range |
| tarsus | t2, t3, t4 | wolf-p-b | deck | 66.5-74.0 | 18%-20% | 61 | story order |
| wolf-p-a | t2 | colossus | starboard flank | 66.9-67.7 | 20%-20% | 4 | doctrine: closest threat in arc and range |
| wolf-p-a | t1 | tarsus | drives, engines | 67.2-71.0 | 60%-60% | 6 | story moment: tarsus@68.0:rake, tarsus@70.5:breach |
| wolf-p-b | t2 | colossus | starboard flank | 68.5-69.2 | 21%-21% | 3 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | tarsus | drives | 68.8-71.0 | 60%-60% | 4 | story moment: tarsus@69.6:he, tarsus@70.5:breach |
| mule | t2, t3 | wolf-p-b | belly | 69.1-74.0 | 15%-18% | 29 | story order |
| mule | t2, t3 | wolf-p-b | starboard flank | 70.4-71.7 | 60%-60% | 12 | story moment: wolf-p-b@71.2:rake |
| wolf-m | t1, t3 | wolf-p-a | port flank | 70.8-72.1 | 60%-60% | 11 | story moment: wolf-p-a@71.6:he |

Silent: tarsus (t1, t2): her aft deck burning over the shot-out drives; wolf-p-a (t1, t2): displacer blink; wolf-p-b (t1): displacer blink

Cannot deliver: wolf-m@73.0:he (wolf-p-b t1 nearest: out of arc by 77 deg)

### S6d (k3e) 74-80 s: The rear guard

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| hound-p-b | t2 | hound-m | deck, engines, port flank | 74.4-77.9 | 60%-60% | 22 | story moment: hound-m@75.2:rake, hound-m@75.5:breach, hound-m@77.4:he |
| hound-m | t2 | hound-p-a | bridge, centre | 74.5-77.4 | 60%-60% | 12 | story moment: hound-p-a@75.0:rake, hound-p-a@76.9:kill |
| hound-p-a | t1, t2 | hound-m | belly, deck | 75.2-76.2 | 18%-19% | 8 | story order |
| hound-p-b | t2 | hound-m | bow, engines | 76.0-80.0 | 22%-22% | 15 | story order |

Silent: hound-p-a (t1, t2): destroyed

### S7 (k3b) 80-88 s: Boarding the Tarsus

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| tarsus | t3 | kite-p-a | starboard flank | 78.3-80.6 | 27%-27% | 11 | doctrine: closest threat in arc and range |
| wolf-p-b | t1 | tarsus | forward guns | 79.8-86.4 | 26%-30% | 8 | story order |
| tarsus | t3 | wolf-p-b | starboard flank | 82.7-84.4 | 20%-20% | 5 | story order |
| wolf-p-b | t1 | tarsus | bridge | 83.2-84.5 | 60%-60% | 3 | story moment: tarsus@84.0:rake |

Silent: tarsus (t1, t2): her aft deck burning over the shot-out drives; tarsus (t3, t4): silenced by the Wolf (P)'s covering run before the boats land

### S8-S9 (k4) 88-102 s: She will not be taken

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| colossus | t1, t2 | kite-p-b | bow | 86.3-95.7 | 19%-20% | 64 | story order |
| kite-p-b | t3 | colossus | port flank | 86.8-95.6 | 27%-27% | 37 | story order |
| wolf-p-a | t1 | colossus | port flank | 86.8-89.0 | 27%-27% | 3 | story order |
| colossus | t1, t2 | colossus3 | belly, port flank | 95.2-100.0 | 60%-60% | 23 | story moment: colossus3@96.0:rake, colossus3@99.5:rake |
| colossus | t1 | kite-p-b | starboard flank | 95.7-96.0 | 60%-60% | 2 | story moment: kite-p-b@96.5:he |
| colossus | t1, t2 | colossus3 | starboard flank | 96.7-102.0 | 38%-38% | 23 | story order |
| colossus3 | t3, t4, t5 | borer-c | deck | 96.8-97.7 | 5%-5% | 12 | point defence |
| morrow | t1 | kite-p-b | engines | 97.6-102.0 | 19%-19% | 15 | doctrine: closest threat in arc and range |
| colossus3 | t10, t11, t12, t7, t9 | borer-d | deck | 98.3-100.1 | 5%-5% | 21 | point defence |

Cannot deliver: wolf-p-a@99.0:he (colossus t2 nearest: out of arc by 21 deg); colossus@89.0:rake (colossus3 t11 nearest: out of arc by 9 deg); colossus@94.9:he (colossus3 t9 nearest: out of arc by 7 deg)

### S9b (k4b) 102-110 s: The lead's last run

| ship | turrets | target | aim | window s | hit | rounds | intent |
|---|---|---|---|---|---|---|---|
| lasher-lead | t1, t2, t3 | falcon-p | starboard flank | 102.3-107.2 | 22%-32% | 19 | story order |
| falcon-p | t7, t8 | lasher-lead | bridge, engines | 102.7-107.1 | 60%-60% | 4 | story moment: lasher-lead@103.5:breach, lasher-lead@107.6:breach |
| falcon-p | t7 | lasher-lead | starboard flank | 104.0-106.8 | 11%-11% | 3 | story order |
| lasher-lead | t1, t2, t3 | falcon-p | bridge, starboard flank | 104.0-106.5 | 60%-60% | 38 | story moment: falcon-p@104.8:rake, falcon-p@105.0:breach, falcon-p@106.0:he |

Silent: lasher-lead (t1, t2, t3): tumbling away burning after the broadside (S9b)

Cannot deliver: lasher-lead@108.0:rake (falcon-p t7 nearest: out of arc by 5 deg)

