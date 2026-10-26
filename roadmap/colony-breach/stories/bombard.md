# Bombard: the Rav long arm

Status: IN PROGRESS — steps 1 to 4 (the spore haze, the body and the lob, the counters, the look) are built, steps 1 to 3 scene-tested, the look's animation pass is with the owner, and the probe (step 6) read on Cordon and Colony Breach with the battle cap in. The sound (step 5) is with the creature sound session.

Written: 2026-10-25

Model: `colony-breach-nouns.md` (Swarm strain, Creep, Air threat ladder);
`ballistics-nouns.md` (Obscuration).

## Why

Owner, 2026-10-25: "Are the Ravs in need of a bad long range support
weapon/monster? Ala starship troopers artillery bug?" and "We could even have
it do debuff more than damage -> aka fog cloud with light damage that more
lowers accuracy giving melee crawlers more of a chance to get into melee
range". Nothing Rav reaches a line that stands off from the swarm: a Cordon
redoubt is threatened only by bodies at its wall, and forward observers and the
air wing have no high-value Rav ground target. The Bombard cannot kill that
line. It spoils its aim, so the Runners get across.

## The strain (to fold into *Swarm strain* when it ships)

- **What it is.** A large, lumbering crustacean, about one and a half Brutes
  long. Its build is a lobster's: a heavy charcoal carapace edged in dried-blood
  red, four pairs of thick armoured walking legs, withered vestigial claws, two
  short antennae. Its segmented tail is a **catapult arm** ending in a bony
  sling cup. Its back shell is split open down its seam over a swollen
  **bile sac** glowing amber, and raw sinew shows at the tail's hinge. That
  split is its weak point, and the look says so: it is **unplated and low in
  health for its size** (about a Brute's health with none of its plate). It is
  slow, about a third of a Runner's pace, with no melee. Distance and the surge
  in front are its only protection. Growth stage scales it as it scales every
  strain: a whelp's haze is smaller, an elder's larger and longer-lived.
- **Rooted to the nest.** It fires only while **planted on creep**, so its
  reach is the nest's reach plus its range. Off creep it lumbers back toward
  the nearest creep. Planting and uprooting each take about three seconds, and
  planted it cannot move. Burning the creep uproots it.
- **The lob.** Each lob is a **wind-up** of about three seconds, with the tail
  cocking right back over the body and the glob glowing in the sling. Then
  comes the **release**: the tail whips through its arc and flings the glob.
  The glob is a real arcing projectile on the indirect-fire contract, slow and
  high (three to four seconds aloft), glowing and trailing haze. It lands on
  its aim point plus a scatter disk that grows with range (about two cells
  near, six at full range). It reloads in about fifteen seconds. Its range is
  about 20 to 55 cells, with a **minimum range** inside which it cannot lob.
- **What it aims at.** Ground targets only: infantry, guns, emplacements; never
  a craft. It picks the sensed enemy group with the most bodies inside a
  haze's radius, preferring a group **standing still and firing**, and one
  that Rav bodies are closing on. It never lobs a haze that would cover a
  melee: a swarm body already on anyone the haze would catch. Inside a pack
  leader's leash it shares the pack's quarry (not built yet). The
  landing field and lift ring are a later rung, only if play shows the lift
  needs it.
- **The spore haze.** The glob bursts into an amber haze about three cells in
  radius. It stands about twelve seconds, then thins from the outside in over
  about six more. It is a **see-through obscurant**: it never blocks sight,
  fog of war or target acquisition, but every haze cell on a shooter-to-target
  segment (standing inside it included) compounds an accuracy multiplier. That
  is the obscuration law with a second kind of obscurant beside smoke: smoke's
  accuracy price without its opacity, milder per cell. The law is neutral, so
  the swarm's own shots through it pay too. Rav melee does not, and that
  asymmetry is the whole weapon.
- **Air is neutral.** Flak and anti-air firing through a haze pay its price,
  so a hazed flak mount is the opening a Harrier pack dives into. A Bombard
  never lobs at a craft.
- **Sting and pulses.** The burst stings: a small splash on the ordinary
  detonation path (the acid's penetration, so armour blunts it without turning
  it; spares the swarm; stopped by an intact roof, as the Spitter's acid is). After that, every two seconds a body standing in
  the haze takes a small pulse of the same kind, as repeated small hits rather
  than a status. Standing in one haze for its whole life costs about a rifle
  hit. It is a reason to step out, not a killer.
- **Wear and death.** Hits on it show on the split sac as amber ooze, and its
  shell wears by the Matriarch's worn-chitin law. When it dies, the sac
  bursts into one last small haze where it falls.
- **Rarity.** Drawn from a bore's strain table at **maw tier only**, at a small
  weight, at most **one living Bombard per bore**, and at most **three across the
  battle** (`BombardQuota`), drawn only by the three such bores nearest the line;
  one that dies frees its place, so the long arm regrows. An old bloom's nests may
  stand with one at setup. In a fresh Colony Breach it appears only once a bore
  has been fed to maw, as part of the feeding ramp.
- **The tell.** Everything shows on the creature and in the air: the tail
  cocking back, the whip and the wet crack of the hinge, the slow amber glob,
  and the glob's **shadow** closing on its landing point. Nothing is drawn on
  the ground as a marker.
- **Counters.** A squad standing in a haze with no Rav in melee reach steps a
  few cells out of it to the nearest clear standable cover, and a garrison
  steps back from a hazed window. A launch reveals the Bombard's cell through
  fog of war for a few seconds, as a muzzle flash does. A planted Bombard is a
  forward-observer and air-wing designation in its own right. Inside its
  minimum range it is helpless.
- **Missions.** Cordon (Line and Road) first, then the Heart and Broad Front,
  then Colony Breach late, from maw bores only. No non-Rav mission.

## Look (owner's picks)

The look took five rounds: the catapult tail over a gland launcher, palette C
(charcoal, dried blood, amber), tank size, the lobster build with its split
shell over the sac, then the withered claws and short antennae. The master is
`art-source/rav-redesign/artillery/raw/bombard5-withered.png`, drawn as two
frames (wind-up and release) after its parent
`art-source/rav-redesign/artillery/raw/bombard4-armoured.png`. Both have their
prompts and provenance.

## Constraints

- Comes after m3's march spacing (landed, 06703f0fe): an area effect on a
  bunched company stacks with the Spitter splash problem.
- The haze reuses the obscuration law and the transient-field machinery; it
  adds a kind of obscurant, not a parallel accuracy system. Sight never reads
  it.
- Rendering is presentation only; nothing in the simulation reads the rig.
- Any temporary A/B switch follows `switches.md`.

## Plan, in order

1. **Spore haze.** A transient haze field with an accuracy-only depth that the
   shot reads and sight does not, plus the burst sting and the two-second
   pulse. Unit tests on the depth and pulse rules. Scene `bombard-haze`: a
   squad firing from inside a haze hits less than its control, and still
   acquires. **Built** (`SporeHazes`, `SmokeFieldService#deployHaze`); the
   haze's tint in the battle view is a placeholder until step 4.
2. **The body and the lob.** The unit type, stats and kit through
   `SwarmStrains`; the maw-only slot with the one-per-bore cap; rooting to
   creep, planting, the target pick, the minimum range, the scatter, the
   reload; the glob as an indirect projectile that lands a haze; the death
   burst. Scene `bombard`, with loops: it lobs on a standing line and the haze
   lands there; it never lobs into melee; off creep it does not fire; a squad
   inside its minimum range is never lobbed on. **Built**
   (`SwarmBombardBehavior`, `SwarmBombards`, `BoreStrainTable.Slot#BOMBARD`,
   `weapon.swarm-bombard`).
3. **Counters.** The step-out reaction, the launch reveal, the officer's
   designation. **Built**: `InfantryReflexes.IN_HAZE` (`HazeStepOut`) and the
   haze edge wait (`HazeEdge`), scene-tested in `bombard-haze`; the launch
   reveal (`SwarmBombards#launchReveals`, projected into fog by the battle
   screen), checked in `bombard`; planted Bombards as `AirStrikeDirector`
   candidates, which no scene covers yet (the probe will). The garrison's step
   back from a hazed window is the same reflex, since the haze lies indoors too.
   The melee exception has no loop: marines back off a melee anyway.
4. **Look.** Cut the G1 master into a rig (body, sac, tail segments, sling,
   legs, claws) with the wind-up and release; the glob and its falling shadow;
   the amber haze; sac wear. Look suite `bombard-look` at 1920x1080. **Built**:
   `RavBombardRig` and `RavBombardComposer`, cut by
   `art-source/rav-redesign/artillery/export_bombard_rig.py` (legs, shell, six tail
   slices, sling, glob); the tail curls over the back through the wind-up and
   whips straight on the throw, the sac glows and flares, the shell wears
   (`RavWear` slots 0 to 6). The glob
   flies on its own sprite with a ground shadow that tightens as it lands
   (`groundShadow` on the weapon's render block). The haze's amber was already
   in `SmokeRenderSystem`.
   **Animation pass** (owner's picks pending: `bombard-throw`, `bombard-plant`,
   `bombard-death`, built on the recommended options): the tail arches up off the
   ground and over the back in pitch, drawn larger and shorter as it rises, so the
   sling hangs over the shell as the master draws it, instead of hooking sideways.
   Planting is a dig, three stamps a side with the body sinking, and uprooting
   reverses it. The body's height, lurch and rock are springs: it crouches and
   trembles at full draw, and on the throw it lurches and rocks while the sac flares,
   goes dull and refills through the reload. Dead, it falls apart its own way: the
   sac flashes, the shell cracks open on its seam (`shell-l`, `shell-r`), the legs
   buckle and the tail falls limp to one side. Hits on it puff ichor
   (`SwarmHitFeedback`). The throw happens on the same sim tick as before.
   `bombard-look` shows the walk, the lob through to the uproot, and a death in
   mid wind-up. The battle frames are `bombard`'s look shots: `bombard-lob`, and
   `bombard-fire` (a rifle squad shooting a Bombard until it dies).
5. **Sound.** Wind-up creak, whip and hinge crack, burst, haze hiss.
6. **Probe.** One or two probe fixtures (`cordon-line`, then
   `colony-breach-v1`), judged by behaviour.
   **First reading** (`BombardProbe`, printed as `[bombard]` by the
   `cordon-line` and `colony-breach` evidence): over Cordon's 36 pooled seeds a
   maw bore keeps its one Bombard all battle, about seven planted per battle
   from the first minute, and few die (16 of 275). They throw rarely, 100 globs
   in all, none in 17 battles, since the line seldom stands 20 to 55 cells off
   creep. The globs hazed 81 troops, who stepped out in about two seconds on
   average; each throw gave a launch reveal; 10 of 409 allied air calls went at
   planted Bombards, and 8 Bombards died under one. Ground fire almost never
   reaches them. Colony Breach v1: 7 fielded, 2 globs, 3 troops hazed. The
   one-per-bore cap, not the slot's weight, set how many there were.
   The air gap was a bug: the launch reveal reached only the player's fog, so the
   strike director could designate a Bombard only on an operator's own sight,
   and they plant beyond it. A revealed Bombard is now remembered while it stays
   planted and called on belief, at a score above all but a dense pack: over
   the same seeds, air calls on Bombards rose from 10 to 65 and Bombards killed
   under one from 8 to 25 (35 held, 1 broken).
   The owner picked a few at once (`bombard-count`): with the battle cap of
   three, at most 3 alive in every battle, 122 fielded over the 36 seeds with
   regrowth, 59 globs (81 uncapped), 48 troops hazed, 49 air calls on them;
   33 held, 3 broken.

## Acceptance

- The scenes above pass. Unit tests stay under a second each.
- On the Cordon probe, a planted Bombard hazes a wall team, the team steps out
  or shoots worse, and the observers or air wing go for it.
- The look suite shows the wind-up, release, glob shadow and haze at
  1920x1080, judged by the owner.
