# Missile AA effects, sound and behavior review

[Seven-second comparison with sound](review.mp4) · [Exhaust still at 1.2 seconds](review.png)

The three columns replay separate production simulations at the same clock.
Each uses its actual catalog weapon, approved tower/head, painted release mouths,
guidance, contact, damage, and authored particle effects. The lower views enlarge
the same simulation around the mount. An unarmed Aeroshuttle orbits at sixteen
cells with 20,000 HP so the entire salvo remains visible; this is a diagnostic
target, not an ordinary aircraft balance claim. No paths or impacts are drawn
by hand. `timeline.json` retains the actual launch/impact events used for audio.

## Behavior and visual treatment

| Ground weapon | Release pattern | Flight and payload | Effects |
| --- | --- | --- | --- |
| Locust | 30 rounds, 0.1 seconds apart; 5 seconds reload after final round | Agile homing, closer-aircraft retargeting, 200 direct damage, no friendly hits | Small orange motor flames, dissipating gray smoke puffs and compact impacts; cycles twelve painted front holes |
| Swarmer | 4 rounds, 0.2 seconds apart; 5 seconds reload after final round | Agile homing, closer-aircraft retargeting, 100 direct damage, no friendly hits | Same motor/smoke family, four individual tube mouths, stronger launch puffs |
| Proximity Charge | One every 2 seconds | Unguided slowing charge, proximity fuse, 500 maximum radial damage including friendlies | Brief launch smoke, purple charge glow, large purple detonation; no powered exhaust trail |

Locust and Swarmer turn at 240 degrees/second and can switch to a closer live
aircraft while the original target survives. Direct missile damage does not gain
invented area splash. Proximity charges have a four-second lifetime, a two-cell
fuse extension, a 7.5-cell blast radius with a 3.75-cell full-damage core, and a
20-degree launch cone. A ten-cell minimum automatic engagement range gives the
ground launcher stand-off from its own blast. Charges expire quietly when they
miss. Terrain contact spends the same blast payload as aircraft contact.

Flight distances use twenty vanilla units per ground cell. Native specifications
supply release counts/timing, damage, speed, acceleration, turning, fuel and fuse
dimensions. Ground adaptations include static unlimited ammunition, hostile
aircraft target selection, the launch cone, minimum engagement range, and
armor penetration (Locust 0, Swarmer 16, Proximity 24). These penetration values,
mount traverse and shared tower durability are initial balance choices. The
mod's damage model does not reproduce Starsector shield/hull damage multipliers.
Missile shoot-down by point defence is outside this pass.

Proximity collateral damage includes grounded actor bodies when the blast
reaches their vertical volume, subject to existing roof and line-of-sight
protection. It does not damage terrain/wall HP. The paired simulation control
proves airborne friendly fire; grounded/roof exposure has geometry tests and
source review, not a full-scene acceptance fixture.

Exhaust particle emission samples the shot clock at twenty samples/second,
outside render collection. Paused/repeated draws produce no additional samples,
and a resolved or expired flight emits no more. Powered flames attach behind
the body; unpowered charge glow attaches to its center. Textured smoke puffs
expand and disperse behind the guided missiles.

Each launch also vents smoke behind the turret head, separately from its front
launch puff and the missile's moving exhaust. The rear origin follows the firing
bearing and barrel lane, with an offset beyond the painted rear silhouette.
Locust emits three rear puffs per release with 1.2-1.65-second lifetimes: its
ten releases per second overlap into a sustained plume through the salvo, then
clear during reload. Swarmer's shorter puffs form four distinct bursts, while
Proximity has a modest purple-gray ejection puff. These are launch-event effects,
so they do not continue emitting after firing ends or while the simulation pauses.

## Sound provenance

Files below are read directly from the installed Starsector 0.98a-RC8 core,
`C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core/`.
No duplicate vanilla audio files are shipped with these weapons.

| Cue | Runtime sound ID | Core-relative sound file | Configured pitch / volume |
| --- | --- | --- | --- |
| Locust and Swarmer launch | `swarmer_fire` | `sounds/sfx_wpn_missiles/pilum_lrm_fire_01.ogg` | 1 / 0.6 |
| Proximity launch | `proximity_charge_launcher_fire` | `sounds/sfx_wpn_missiles/proximity_charge_launcher_fire_01.ogg` | 1 / 0.8 |
| Proximity blast | `prox_charge_explosion` | `sounds/sfx_systems/mine_strike_explosion_01.ogg` through `_04.ogg` | 1.2 / 0.5 |
| Guided missile impact | `marines_missile_impact` | `sounds/sfx_impacts/explosion_missile_01.ogg`, `_02.ogg` | 1 / 1 |

The guided-impact cue reuses the mod's existing alias; it is a mod sound choice,
not an explicit vanilla Locust/Swarmer hit cue. The preview mixes these source
clips against actual event times, with nominal pitch, fixed attenuation and
left/center/right placement for the columns. It alternates the two guided impact
clips and uses the first Proximity explosion variant. It is a reference mix,
not a capture of the live host's positional audio, pitch jitter or random choices.

Source references: installed `data/weapons/weapon_data.csv`, `locust.wpn`,
`swarmer.wpn`, `phasecl.wpn`, `proj/locust_srm.proj`, `proj/type_1_srm.proj`,
`proj/phase_charge.proj`, `data/config/sounds.json`, and the mod's sound aliases.
The user's [missile overview](https://starsector.wiki.gg/wiki/Missile),
[Locust](https://starsector.wiki.gg/wiki/Locust_SRM_Launcher),
[Swarmer](https://starsector.wiki.gg/wiki/Swarmer_SRM_Launcher), and
[Proximity Charge](https://starsector.wiki.gg/wiki/Proximity_Charge_Launcher)
pages provide player-facing context; installed files resolve implementation details.

## Reproduce and interpret

From the repository root with the configured JDK and ffmpeg on PATH:

```powershell
./gradlew.bat sceneEvidence -Pscene=missile-aa-effects --console=plain
./gradlew.bat createSnapshots -Psnapshot=missile-aa-effects --console=plain
./art-source/defences/missile-aa/effects/encode-review.ps1
```

The encoder accepts `-Ffmpeg` and `-StarsectorCore` paths. Scene reports go to
`build/reports/scenes/missile-aa-effects`; the snapshot produces 140 full-HD
frames at twenty frames/second. Raw frames remain untracked build output.

Ten loops cover the three firing subjects, three friendly-only controls, two
live-target retarget controls, and the Proximity friendly-blast/no-launcher pair.
The subjects launch 30 Locust, 8 Swarmer and 4 Proximity rounds in seven seconds;
all three launchers survive the diagnostic orbit. The nearby hostile/friendly
pair both take blast damage, versus zero without a launcher. These observations
validate behavior, not mission balance. The retained effects clip predates the
guided cruise-speed calibration and Proximity launch lead; it preserves the
approved visual treatment. Re-running the commands uses current flight timing.

The ordinary turret workbench preserves the dedicated missile catalog and all
flight/muzzle fields, but its illustrative flight controls and three-round
preview use legacy trajectory fields. Use this production scene for missile
physics review; tune `airMissile` flight parameters in the weapon data.
The three catalog entries are available for placement. The debug fighter range
below uses them together; campaign unlocks are unchanged.

## In-game fighter range

Open **Company HQ → DEBUG → STORY + REPEATABLE**, choose the **Missile range**
location, then **Missile AA / Fighter Range** and deploy from its briefing.
This is the ordinary detached debug-company launch: twelve named marines arrive
at a southern observation apron while three allied batteries defend separate
lanes. The Locust is west, Swarmer center and Proximity east. The neutral observer
view reveals the approaching hostile aircraft without changing their targeting.

Six finite waves bring one fighter per lane, starting after four seconds and
spaced fifteen seconds apart: Talon, Wasp, Thunder, Broadsword, Talon, Wasp.
These are ordinary armed strike sorties with normal hull health, steering and
physical exits. Towers can be destroyed. Each lane marker tracks its assigned
fighter waves, losses and escapes; missiles can retarget across lanes, so these
are cohort counts rather than isolated battery scores. The battle stays open
after the waves; withdraw to finish.
Pause and zoom in to inspect the launch mouths, backblast and missile paths.

Guided cruise speed is calibrated for the existing atmospheric fighter speeds:
40 cells/second for Locust and 30 for Swarmer. Guided turn rate is calibrated
to 60 degrees/second, giving a fighter's lateral break a physical escape window. Their
finite fuel, salvos and payloads remain. Fighters react to a closing guided missile
with a delayed, finite steering break using their own hull handling. Contacts
sweep both moving bodies over the same tick. Proximity predicts the target's
motion once at launch, including the charge's deceleration; its spread and
unsteered flight still permit misses.

Run the paired production-factory evidence and its short visual excerpt with:

```powershell
./gradlew.bat sceneEvidence -Pscene=missile-aa-fighters --console=plain
./gradlew.bat createSnapshots -Psnapshot=missile-aa-fighters --console=plain
```

Reports install and verify the actual hull engine specifications, then compare
the same finite attack with normal evasion, evasion disabled, and no batteries.
They retain actual launches, contacts, expiries, losses,
physical exits, unresolved sorties and battery survival. A successful run checks
accounting and exercised mechanisms; it does not assert a universal hit rate.
The snapshot excerpt begins at the first actual AA release and changes only the
camera. The earlier effects clip uses its durable diagnostic transport and is
not the fighter balance comparison.

At the retained 40/30 speed and 60-degree turn calibration, three seeds each
finish with 17 fighters destroyed and one Thunder escaped, all three batteries surviving.
All 54 fighters escape the paired no-AA control. Disabling evasion instead
produces 41 losses and 13 escapes across the three runs, but falling aircraft
destroy batteries in those runs; that total is not a clean measure of dodge
effectiveness. The normal runs demonstrate an escape with all batteries active,
not a universal 94% kill probability. Across those runs, actual aircraft-contact
triggers are 30/540 Locust rounds, 16/96 Swarmer rounds and 12/28 Proximity
charges; other rounds expire without contact. Multiple direct hits can be
needed against armor, and a proximity blast can damage more than its trigger.
