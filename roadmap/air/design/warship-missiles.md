# Warship missiles — flight, swarms and the lore they carry

Status: ACTIVE

Written: 2026-10-11

Updated: 2026-10-11 — slice 2 (swarm bursts) shipped and folded; the swarm design calls below are decided; what remains is listed under Slicing.

Read `air-nouns.md` (**Overhead ships**) first. Vocabulary (warship, mount, barrage, point defence, flux) is that doc's; this one adds the *missile* half of the barrage.

## Owner direction (standing)

Decided 2026-10-11, in answer to the questions at the end of the first design pass.

1. **Spectacle first, and open to changing behaviours.** The reference is a film
   scene in which a cluster munition launches and, on reaching an area, splits into
   submunitions that hit at random for a large area burst at night. The game is 2D,
   so the aim is the *feel*, not the fidelity: a visible carrier, a visible split,
   a sheaf of submunitions walking across the ground.
2. **Point defence is the counter, for ground and for air defence.** Swarm bodies
   are individually killable by point defence, so saturation is real.
3. **Chase fun and spectacle first, then balance and counter it.** The player wants
   power fantasy both ways, with their own ships and with enemy ships. The true MIRV
   payloads (Hurricane 7 x 500, Sabot 5 x 200, Hydra 6 x 500) are wanted.
4. **Why the visual and damage radii differ.** Vanilla's `explosionRadius` in a
   projectile spec is the *visual* fireball size; `explosionSpec.radius` and
   `coreRadius` are the damage footprint (the core takes full damage, the outer
   radius falls off). The first version read the visual figure for damage, which was
   a bug and not a design choice. Damage and wall and roof breach use the damage
   radius; the visual radius sizes only the drawn flash and fireball.

These close questions 1 to 4 below; 5 to 9 remain open.

5. **Spectacle before balance, and a random drumroll.** The reference is a film scene (3D,
   night): a cluster munition launches and, on reaching an area, splits into submunitions
   that hit at random for a large area burst. We work in 2D and aim for the feel. The
   spread is a *random scatter* over a disc with a staggered arrival, not a tidy tulip: the
   bodies fan out of the split, weave, then converge on random aim points. The owner is open
   to changing existing behaviours to get it. Point defence must be able to counter it, for
   ground and for air defence, so swarm bodies are individually killable.

## Decided for the swarm (slice 2)

These are design calls made while building slice 2, recorded as decided.

- **Caps and fusing.** A volley's damage-bearing bodies are many, capped at 32 per pull of
  the trigger, 160 per ship and 480 per battle. Beyond a cap a round fuses into a body
  already in the air (damage and area summed), and a carrier's warheads fuse into fewer,
  heavier ones, so total damage is conserved. A carrier always keeps one warhead, so a ship's
  cap can be exceeded only by carriers, never by rack rounds.
- **Random scatter, staggered arrival.** Aim points are a stratified random scatter over the
  disc (even cover, random placement). The arrival window is a parameter of the preset: 0.6
  to 2.5 s for FLAVOURED, 1.2 to 4 s for CINEMATIC, 0.15 to 0.8 s for FAITHFUL. It is a
  stagger added to each warhead's own travel at its own speed, so a far split does not swallow
  it; a fast warhead's stagger is held to what keeps it at 40% of its speed, so the Sabot
  stays a tight kinetic shotgun rather than a crawl.
- **Presets per ship.** FAITHFUL, FLAVOURED (default) and CINEMATIC, selected by
  `Warship.missilePreset` and, in the debug panel, by F9.
- **The budget.** Damage over the count and radius over its root, so the aggregate footprint
  matches the vanilla weapon's; where the spec gives the count (a burst, `numShots`) the
  shares are the spec's.
- **Which weapons swarm.** MIRVs split into their true warheads as visible bodies; rack and
  pod launchers fire many small bodies already and each is drawn and killable; heavy single
  torpedoes stay single. CINEMATIC also splits the Hammer family into six bodies inside the
  budget rule.
- **Point defence.** The warship's own and the carried interceptor pods read warship bodies
  through one interceptable seam; toughness comes from hit points (a warhead shares its
  carrier's, since the spec names none); a carrier killed before its split loses the payload.
- **Split cue and flash.** One split sound (the spec's own, through the sound-cue seam) and one
  visible flash, ring and smoke per sheaf.
- **Telegraph: yes.** A faint dashed pulsing ring on the target disc while the carrier or volley
  is in flight, for FLAVOURED and CINEMATIC, off for FAITHFUL.
- **A rack's burst is held to a rolling window** (3 s FLAVOURED, 2.5 s CINEMATIC) so a
  Squall's twenty rounds roll out in seconds. This changes an existing cadence.

## Shipped (slice 1)

- **Flight.** Every missile mount's round follows a closed-form path read from its
  own spec: launch, boost, cruise and coast, a launch-fan bulge, and a wobble or
  weave bounded by the missile's own turn rate; arming time honoured; the seed is
  stateless. An unguided rocket and a homing missile are now visibly different
  shapes (a rocket bulges to one side, a homing missile crosses its own line).
  Point defence and the renderer read the same path. Deferred from the story: the
  far-zoom degradation of the trail through `ZoomDetail`, and the contrail
  ribbon, which stays the existing puff chain sampled along the path.
- **Bug fix: MIRV payload.** A multi-warhead missile carries the sub-spec's warheads
  (count, damage, damage type, footprint, speed) read through
  `behaviorSpec.projectileSpec`, flies as a carrier, and on reaching its split point
  releases that many committed detonations, spread over a disc with jitter and a
  stagger. This is the carrier-and-sheaf foundation for slice 2, which makes the
  warheads real bodies.
- **Bug fix: blast radius.** Damage and wall and roof breach use `explosionSpec.radius`
  (falling back to the visual size only where a round declares no footprint); the
  fireball is drawn at the visual radius. The ground detonation has no falloff, so
  the outer radius applies flat and `coreRadius` is read but not yet used.
- **Evidence.** The `warship-missiles` scene and snapshot suite (rocket pod, homing
  Harpoon, Hurricane, Reaper pair); unit tests for the path, the spec reader and
  the scatter.
- **A data-file quirk.** The game files the Locust's projectile as `locust_srm.proj`
  though its weapon names it `locust`; the weapon reader tries that one alternative
  spelling because the data seam cannot list a folder.

## Shipped (slice 2)

- **Bodies.** A round, a carrier and a released warhead are each an interceptable body
  (`Interceptable`): position, speed, faction, hit points, a kill mark. Point defence and
  the carried interceptor pods read them through one seam.
- **Bloom.** A carrier splits at the spec's range (not before its minimum time) with one flash,
  ring, smoke puff and the spec's split sound (`WarshipSoundCue.Kind.SPLIT`). The warheads fan out
  in the spec's arc, curve and weave onto random scatter points (`SwarmLayout`, `BloomPath`),
  staggered across the preset's arrival window.
- **Racks.** A burst of three or more is a volley scattered over a footprint, nearer rounds first for a
  rocket rack and in no order for a swarm; the burst is held to a rolling window. The
  archetype table (`MissileArchetype`) is keyed by weapon id with a spec-derived fallback.
- **Budget, caps, fusing.** `SwarmBudget`: damage over the count, radius over its root, 32 per volley,
  160 per ship, 480 per battle; a round beyond a cap folds into a live body of its weapon and a
  carrier's warheads fuse, both preserving the sums. Fused rounds are counted for evidence.
- **Point defence.** Toughness from hit points (none up to 60, then `1 / (1 + excess / 600)`); a warhead
  shares its carrier's hit points; a carrier killed before its split releases nothing.
- **Presets.** `MissilePreset` (FAITHFUL, FLAVOURED default, CINEMATIC) on `Warship.missilePreset`; F9
  in the debug warship panel steps through them.
- **Rendering.** Each body is drawn with its own sprite, engine glow and a trail sampled from the same
  closed-form path; a dashed pulsing telegraph ring; one split flash per sheaf. Far-zoom degradation
  through `ZoomDetail` (no flame or smoke below six pixels per cell, a shorter tail beyond 96 bodies) and
  view culling cap what is drawn.
- **Evidence.** The `warship-swarm` scene and snapshot suite: Hurricane cluster under each preset, a
  Sabot shotgun, the same cluster against an umbrella of point defence over the district and against a
  picket in the carriers' lane, an Annihilator volley, a Squall barrage, a cinematic Hammer cluster
  and a ship firing Locusts from every slot until the caps bite; unit tests for the budget and fusing,
  layout, path, archetypes, presets and bodies. Verdicts: `bodies-as-designed`, `damage-budget-conserved`,
  `arrivals-staggered`, `footprint-wide`, `landing-clock-preserved`, `bodies-capped`,
  `excess-is-fused-not-deleted`, `bodies-are-individually-killable`, `carrier-kill-loses-payload`,
  `pd-takes-a-measurable-share`.
- **Hardened targets, by reading.** Wall damage is hit-point accumulating (`MapEditor.damageWallsInRadius`),
  so a swarm's many small shares breach in aggregate; per-volley accumulation was not needed. This is
  read from the code, not measured against a compound.

## Why this doc exists

Owner direction: the capital ships bring the game's own weapons into a ground
battle that is modelled **in atmosphere** — it has air, drag, ground and
buildings, not vacuum. Missiles are the plainest part of that model today. A
missile mount fires a `WarshipShell` that flies one straight line from muzzle to
a point and lands as one `PendingDetonation`. A Locust, a Hurricane and a Reaper
differ only in the number on the blast; none of them *behaves*. The owner wants
missiles to have life and interesting flight, wants a salvo that can spread,
weave and arrive as a swarm of many small impacts (the 2021 *Dune* reference:
fleet munitions that arrive as a swarm burst, not a single shell), and wants the
lore of each missile line read into the in-atmosphere model rather than ignored.

## What existed before slice 1 (facts the first design pass built from)

The list below is the state the design started from; the flight model, the MIRV
payload and the radius fix above have since changed the first three points.

- `VanillaWeapon` is read from the weapon table row, the `.wpn` and the `.proj`.
  `missile` is true when the projectile's `specClass` is `missile`. The weapon
  table's `type` column is the **damage type**; "this is a missile mount" lives
  in the `.wpn`'s `type` and the projectile's `specClass`.
- `WarshipSystem.fire` takes the weapon's `damage/shot`, `proj speed`, `min spread`
  and `explosionRadius`, picks one landing point inside the cone, and commits a
  `WarshipShell` whose flight time is `distance / proj speed`. The landing is
  decided at fire time; the shell is a straight lerp. One clock serves the drawn
  round, point defence and the landing.
- Burst rounds (`burst size`, `burst delay`) are already separate shells. A Locust
  (30 per burst) is already thirty straight-line shells; a Squall twenty.
- `WarshipPointDefence` picks the nearest hostile `Projectile` (opted in) or
  hostile `WarshipShell` in reach and arc, and rolls a kill chance from the
  weapon, the range and the round's crossing speed. One engagement kills at most
  one round; a heavy salvo saturates the mounts and the surplus lands.
- `WarshipRenderSystem.drawShell` draws the projectile's own sprite (for a
  missile that is the `.proj` `sprite`) with a short puff chain as its tail.
- Everything is deterministic: the system owns one seeded `Random`.

Three things the data reveals that the current model gets wrong or flattens:

1. **MIRV payload is collapsed to the carrier's table row.** The Hurricane's
   `damage/shot` is 500, but its `.proj` `behaviorSpec` releases `numShots: 7`
   warheads of `damage: 500`: the real payload is 3,500. The Sabot lists 100 and
   releases 5 x 200 kinetic; the Hydra 6 x 500 energy. Today each lands as one
   carrier-sized blast.
2. **The blast radius reads a figure the spec calls "purely visual".** The code
   takes `explosionRadius`; a Reaper's is 350 px (15.8 cells) while its
   `explosionSpec` damage radius is 175 px (7.9 cells, core 75 px). The Hammer is
   230 px visual against 100 px damage radius. Annihilator has only the visual
   figure (75 px, 3.4 cells). See question 4.
3. **A rocket and a homing missile are the same thing.** The `.proj` says which:
   `missileType: ROCKET` with `turnRate: 0` is unguided; `turnRate > 0` steers.

Scale reminder (`WarshipScale`): 1 px = 0.045 cell. An Annihilator reaches 67 cells
at 18 cells/s; a Harpoon 112 cells at 13.5 cells/s over 12.5 s; a Pilum 180 cells.

## Catalogue of the missile lines relevant to capitals

Flight and warhead figures are the game's own (`weapon_data.csv`, `.proj`).
"Guidance" is read from the projectile: `turnRate` 0 and `missileType: ROCKET`
= unguided; otherwise homing. Lore is the in-game description
(`descriptions.csv`) in one line, because it is what the behaviour should
express. What the capital variants carry: Onslaught Standard four Annihilator
Rocket Pods (WS 021-024), Elite four Sabot pods, Outdated four Pilum, xiv Elite
four Typhoon; Paragon Elite four Reaper, Escort four Sabot, Raider four Harpoon.

| Line | Weapons | Shape | Guidance (spec) | Lore and role |
| --- | --- | --- | --- | --- |
| **Annihilator rockets** | `annihilator`, `annihilatorpod` | 200 HE, 50 launch to 400 px/s, 1500 px, burst 5 or stream of 1, 100-round pod | Unguided ROCKET (turnRate 0), 50 hp | Hegemony armour-strippers: "small and unguided, the extra space taken by a larger warhead and thicker armor". A screen before a torpedo strike to saturate PD. Stream and mass are the point. |
| **Hammer family** | `hammer`, `hammer_single`, `hammerrack`, `jackhammer` | 1500 HE, 500 px/s, 1200 px, 350 hp; rack fires 4 at 0.15 s | Unguided ROCKET with a weak motor-trim (turnRate 30) | "Ludd's Hammer": pre-Collapse miners' dumb-fire rocket, the weapon of poor militias, pirates and the Path. Cheap, brutal, quantity over finesse. |
| **Reaper family** | `reaper`, `typhoon` (6), `cyclone` (14, tandem pairs) | 4000 HE, 400 px/s, 1200 px, 500 hp, arming 0.5 s | Unguided ROCKET | Domain AM-catalysed nuclear warhead on a big armoured unguided torpedo. "Three or four slipping through PD arcs destroy the largest capital." The one-shot strike weapon. |
| **Atropos** | `atropos`, `atropos_single` | 1000 HE, 400 px/s, 1200 px, 300 hp | Guided (GUIDED_POOR, turnRate 10) | Guided torpedo: smaller chemical charge because a nuclear warhead would foul the guidance. |
| **Harpoon** | `harpoon`, `harpoon_single`, `harpoonpod` | 750 HE, 300 px/s, 2500 px, 12.5 s, 150 hp; pod bursts 4 | Homing, turnRate 70 | Medium-range high-payload missile "sacrificing manoeuvrability for speed and warhead size". |
| **Sabot** | `sabot`, `sabot_single`, `sabotpod` | Carrier then 5 kinetic penetrators (200 each, 1000 px/s spread) at 400 px | Two-stage MIRV, GUIDED_POOR | "Difficult to stop with PD": a guided first stage projects a dense kinetic penetrator spread to within striking distance; heavy shield and EMP damage. |
| **Hurricane MIRV** | `hurricane` | Carrier splits at 1000 px into 7 shaped-charge warheads (500 each, evenly spread through 180 deg, 200 px/s spread) | Homing carrier, turnRate 150; warheads tracking | "A multiple-missile delivery projector pod programmed with a hit solution... the swarm of small missiles is dangerous to all ship types." A kill weapon. |
| **Hydra** | `hydra` | Carrier splits at 1000 px into 6 DEM platforms (500 energy each) that flank, then fire | Homing, semi-independent submunitions | Submunitions "manoeuvre to flank the primary shield... only then unleash beamfire." Longer flight invites PD. |
| **Pilum LRM** | `pilum`, `pilum_large` | 500 frag, 125 px/s, 4000 px, 30 s, 3 or 6 per burst; second stage faster (300 px/s) | Two-stage, turnRate 35 | Mature long-range support missile; the faster second stage "has a better chance of defeating PD". The catapult version is a concentrated salvo. |
| **Swarmer SRM** | `swarmer` | 100 HE, 300 px/s, 1000 px, 80 rounds, bursts of 4 at 0.2 s, 50 hp | Homing, turnRate 240, ANTI_FTR | "Small missiles with microthrusters, evolutionary targeting that leads the target; they rarely miss and are very hard to avoid." The authentic swarm. |
| **Locust** | `locust` | 200 frag, 600 rounds, bursts of 30 at 0.1 s, 60 hp | Homing, turnRate 240, ANTI_FTR | "Thirty small missiles per salvo; distributed swarm intelligence; microthrusters for a short time"; made to overwhelm fighter squadrons. The reference swarm. |
| **Squall MLRS** | `squall` | 100 kinetic, 600 px/s, 2500 px, 160 rounds, bursts of 20 at 0.5 s | Two-stage "second unguided" (turnRate 180) | Domain-era anti-shield opener; 20-shot salvo, a rocket-artillery barrage. |
| **Breach SRM** | `breach`, `breachpod` | 150 HE anti-armour, 450 hp, bursts 3 or 5 | Homing, good guidance | Heavily armoured, fast, small shaped anti-armour warhead. |
| **Salamander / Gazer / Gorgon DEM** | `heatseeker`, `salamanderpod`, `gazer*`, `gorgon*` | EMP / kinetic / energy specials | Homing | Engine-seeking EMP; graviton-beam harassment; lightweight energy strike from awkward angles. Mostly ship-versus-ship in character. |
| **Dragonfire DEM** | `dragon`, `dragonpod` | 4000 energy, 150 px/s, 12.5 s, 600 hp | CUSTOM: stands off at 700 px, paints a target, fires a bomb-pumped plasma beam | Built to defeat PD: "a focused plasma beam, without worry of point defense interception." |
| **Omega / Threat / Dweller** | `rifttorpedo`, `neutron_torpedo`, `devouring_swarm`, `resonatormrm`, fragments, ... | restricted, no standard fitting | Exotic | Out of scope; they carry no capital lore this design needs and are `restricted`. |

`SYSTEM`-hinted launchers (mines, bomb launchers, fighter-only variants) are not
capital mounts and are out of scope.

### The mount, not the missile, is a lore fact too

Packaging matters as much as the warhead: a **pod** is a stream (Annihilator pod,
Sabot pod, Harpoon pod); a **rack** is a short burst; a **catapult** (Pilum
large) concentrates fire; a **barrage** rack (Hammer, Squall) fires many at a
short delay. The data carries all of this as `burst size`, `burst delay`,
`ammo`, `chargedown` and the hint `MISSILE_SPREAD`, and the model should read it
there.

## Proposal: an in-atmosphere missile model

### Principles

1. **The landing is committed at fire; the path is a closed-form function of the
   volley's seed and the clock.** This keeps the standing rule that one clock
   serves what is drawn, what point defence can shoot and what the ground suffers.
   A missile's position at time *t* is evaluated, never integrated: no per-tick
   steering state to diverge from the renderer, nothing for a replay to drift on,
   and the tail of a contrail is simply the same function sampled a little
   earlier. Nothing about this feeds back into the sim except `position(t)` and
   `speed(t)` for point defence, and `arrive()` for the detonation.
2. **Everything is read from the weapon's own spec.** The mod authors behaviours,
   not numbers: speed, acceleration, turn rate, fuel, arming time, split range,
   warhead count, damage, radii, colours, sprites and sounds all come from the
   weapon row, `.wpn`, `.proj` (`engineSpec`, `behaviorSpec`, `explosionSpec`,
   `engineSlots`, `missileType`) and the submunition's own `.proj`. A modded
   missile arrives with a sensible behaviour with no authoring.
3. **The vanilla weapon's damage and area are a budget, never a ceiling or a
   floor.** However many bodies a salvo becomes, their sums are the spec's.
   Flair redistributes; it does not add or remove ordnance.
4. **Plain physics where it earns its keep, stylised where it reads.** Weave and
   fan are kinematic curves bounded by the spec's own turn limits, not a steering
   simulation.

### Atmosphere: what it changes and what it does not

The ground battle is in air with hovering capitals overhead; missiles are fired
down a long slant to the ground. Decisions:

- **Powered flight is gravity-free**, as `OrdnanceFlight` already decides for a
  powered round: over a few seconds of motor burn gravity moves nothing visible
  and would only make range a quadratic. Gravity applies to nothing a warship
  fires; a bomb is the aircraft's business.
- **Drag shows as the burn profile.** Three kinematic segments, all closed-form:
  *launch* (ejected at the weapon's `launch speed`, guidance off for a beat),
  *boost* (constant `acc` up to `proj speed`), *cruise* (at speed, guidance and
  weave live). A rocket whose range exceeds its fuel runs out of motor and
  **coasts**: speed decays exponentially (drag time constant read from the
  spec's `dec`, else a default) and the round falls short of the aimed point by
  the coast loss; a fast round at long range is not free. This is why a
  capital's rocket pod has a real maximum reach, and it gives rockets a lazy
  end-of-flight arc that homing missiles do not have.
- **Height is presentation.** A hovering capital fires from a drawing-choice
  altitude (`air-nouns.md`: never a physical altitude). A body gets an apparent
  height that falls to zero over the flight, used for its shadow on the ground
  (same layer and sun as a craft's) and a small scale cue. Not simulated.
- **En-route collision is not modelled.** A round fired from altitude does not
  clip roofs on the way. Detonation and wall damage are the existing pipeline's.
- **Arming time** (`armingTime`, 0.5 s on Hammer and Reaper) is honoured as a
  guard: a body does not detonate in its first arming seconds, so a mount firing
  at point-blank range does not spend a torpedo inside its own ship's shadow.

### Guidance classes (derived, not authored)

| Class | Derived from | In the ground battle |
| --- | --- | --- |
| **Unguided rocket** | `missileType: ROCKET` or `turnRate <= 0` (and weak motor-trim ignored) | Aim point chosen at fire inside the cone; **a moving target is missed.** Aero wobble and, in a rack, per-rocket scatter. Dispersal is the answer, which is the counterplay the barrage rule already promises. |
| **Fire-and-forget homing** | `turnRate > 0` and not `GUIDED_POOR` | Flies toward the mount's chosen *unit*, re-sampling that unit's position until a terminal commit distance, then locks on. Small lead error shrinks with turn rate. A running squad is still hit; a squad that dies or breaks LOS before commit is not followed. |
| **Poorly guided** | `GUIDED_POOR` hint | As homing, with a wide terminal error and an early commit: it goes where the target *was*. Atropos, Sabot carrier. |
| **Two-stage** | `MIRV` behaviour or `MISSILE_TWO_STAGE_*` | A carrier flies to `splitRange`, then releases warheads (see below). |
| **Standoff / custom** | `behavior: CUSTOM` (Dragon) | Stops at a standoff, paints, then delivers a beam-class payload that **cannot be intercepted after release** (the weapon's own lore). Later slice. |

Terminal homing at ground units is the one place the model reads the *target's*
motion. A homing body's landing point is therefore not final until its commit
distance; point defence and telegraphs read the aim point at that moment. This
is the single exception to "landing committed at fire", and it is bounded: the
commit radius is small enough that the clock (arrival time) is unchanged.

### How many bodies is a salvo

Three levels, kept distinct:

- **Volley**: one pull of a mount's trigger (a burst). It owns a seed, a cap and
  a sound cue. It is the unit of *saturation* for point defence and the unit of
  cue-thinning.
- **Body**: a thing in flight with its own path. Rockets, homing missiles,
  carriers, warheads. A body owns a damage share and a blast.
- **Visual fluff**: engine flame, contrail, split smoke and sparks. Never
  simulated, never collected as bodies, never interceptable.

**Every body is damage-bearing** and the weapon's burst structure is respected
(a Locust volley is 30 bodies because the spec says 30). Bodies are the unit
point defence can kill, which is what makes saturation true rather than
painted. Visual-only submunitions are rejected as a default because they make
"difficult to stop with PD" a lie.

### Flight shaping: fan, weave, converge

Each body in a volley gets, from a hash of `(volleySeed, bodyIndex)`:

- a **launch fan** angle: bodies leave the muzzle in a trumpet of width *phi*
  (the weapon's `min spread`, widened for `MISSILE_SPREAD` hints and for
  swarm-class weapons);
- an **aim point** inside the volley's footprint, chosen by a golden-angle
  (Vogel) spiral so a swarm covers an area evenly rather than clumping, ordered
  near-to-far (a "drumroll" walking across the ground) or far-to-near by profile;
- a **weave**: lateral offset `A * env(s) * sin(2 pi f t + phase)` with envelope
  `env(s) = sin(pi s)^p` (zero at launch and at impact, so the endpoints are the
  committed ones), amplitude `A` capped by the spec's own turn limit
  (`A <= v * turnRate / (2 pi f)^2`), so a stiff rocket weaves lazily and a
  Locust jinks hard.

Position = base line to the aim point + fan bulge + weave. All terms are
closed-form and bounded, so arrival time is exactly the clock committed at fire.

### Swarm-burst behaviour

Two shapes, chosen from the spec, with a small authored table for the feel:

- **Converge** (racks and pods: Annihilator, Hammer, Squall, Swarmer, Locust,
  Breach): bodies fan out of the muzzle, weave, and converge on distinct aim
  points scattered over the footprint, arriving staggered by the launch cadence
  plus a small flight jitter. The result is a ripple of many small impacts walking
  across the ground over one to two seconds.
- **Bloom** (MIRV: Hurricane, Hydra, Sabot, Pilum): the carrier flies a single
  line to `splitRange` (honouring `minTimeToSplit` and `canSplitEarly`), smoke
  bursts, and the spec's `numShots` warheads leave in the spec's `arc` at the
  spec's `spreadSpeed`, then curve onto aim points distributed over the
  footprint. This is the strongest Dune-like image: one missile becoming a
  weaving sheaf.

**The budget.** For a weapon whose single blast has damage *D* and damage radius
*R* split into *n* bodies, bodies carry *D/n* each and blast radius *R/sqrt(n)*, so
the summed area is preserved; aim points fill a disc of radius about *kR* (*k*
around 1.6, the swarm's spread). Peak intensity falls and coverage rises: a
swarm wrecks dispersed infantry better than the one blast would, and breaches
walls worse per point. For weapons whose spec already gives a body count (burst
size, `numShots`) *n* is the spec's and the shares are the spec's, never
re-derived.

**The authored table.** Behaviour is derived; *feel* is authored, small, and
keyed by archetype and overridden by weapon id:

| Archetype | Keyed by | Authored fields |
| --- | --- | --- |
| `ROCKET_RACK` | tags `rocket*`, hints `MISSILE_SPREAD`, ids `annihilator*`, `hammerrack`, `jackhammer`, `squall` | fan, weave amplitude and rate, aim-point order, stagger jitter |
| `SWARM_LIGHT` | `ANTI_FTR` hint, ids `swarmer`, `locust` | high-rate jink, tight terminal converge |
| `MIRV_BLOOM` | `MIRV` behaviour, ids `hurricane`, `hydra`, `sabot*`, `pilum*` | bloom spread, flank offset (Hydra), post-split weave |
| `HEAVY` | ids `reaper`, `typhoon`, `cyclone`, `atropos`, `harpoon*`, `dragon*` | none, or a single slow wobble; never split |

A weapon matching no entry derives its shape from the spec alone.

### Literalness

Three presets, one default, a per-ship or per-mission switch:

- **FAITHFUL**: spec numbers only, single shaped paths, MIRVs honoured.
- **FLAVOURED** (recommended default): FAITHFUL plus weave, fan and stagger on
  authentic swarm weapons. No synthetic splitting.
- **CINEMATIC**: also splits heavy rockets into *n* submunitions inside the
  budget rule above, for a Dune-like look on demand. Torpedoes keep their
  single-warhead lore unless a mission asks. Owner question 1.

### Torpedoes and heavy slow weapons

A Reaper is one deliberate body: a single large wobble, a long dramatic
contrail, an arming guard, and a blast that is the existing heavy impact. Its
counterplay is telegraph and dispersal, as for a barrage. Its tension with the
swarm is the lore: "three or four through PD destroy a capital" is *a few big
rounds against PD*; the swarm is *many small rounds against PD*. Both saturate
PD in different ways and the model keeps the two shapes distinct.

### Anti-missile interaction

`WarshipPointDefence` generalises from "a `WarshipShell` or an opted-in
`Projectile`" to one *interceptable* shape — `x(t)`, `y(t)`, `speed(t)`,
`faction`, `hp`, `intercepted`. Bodies implement it.

- Saturation is emergent: one engagement still kills at most one body.
- **Toughness** comes from `proj hitpoints` (Locust 60, Annihilator 50, Hammer 350,
  Reaper 500, Sabot 300): the kill chance scales down with hp. A swarm of many
  weak bodies vs flak is the case flak was designed for; a Reaper is not.
- **Speed already matters** in the existing formula, so a Sabot warhead at
  1000 px/s after its split is hard to hit without a new rule. Its carrier,
  before the split, is not.
- **A carrier killed before the split loses the whole payload.** That is the
  Hurricane's lore weakness and the reason `splitRange` is long. After the split
  there are many fast small bodies and PD is saturated.
- **Dragonfire's beam payload is not interceptable once released.** Later.
- Carried interceptor pods and deployable PD (`PointDefenseService`) already
  read the opt-in on `Projectile`; bodies carry the same flag, so they
  intercept swarm bodies without changes to them.

### Ground, hardened targets and air

- All bodies land through the existing detonation pipeline: damage type to
  penetration (`penetration(damageType)`), `wallDamageRadius`, roof loss, friendly
  fire unconditional. Nothing new is invented for the ground.
- **Hardened targets**: kinetic penetrator warheads (Sabot) are small-radius and
  high-penetration, so they punch rather than blast; HE swarms spread. A swarm's
  breach is the sum of its shares; the risk is that structure thresholds ignore
  sub-threshold hits (see risks).
- **Air**: aircraft over the battle are a different target class. `ANTI_FTR`
  weapons (Swarmer, Locust, Pilum, Proximity Charge) are the lore case where a
  missile should prefer an aircraft. Whether a detonation can hurt an aircraft
  today is not established; this design only reserves the hook. Question 5.

### Rendering

Reuse the game's art; author none. Per body:

- the `.proj` `sprite` (graphics/missiles: `missile_annihilator`, `low_tech_torpedo`,
  `missile_torpedo_compact`, `missile_MIRV`, `missile_MIRV_warhead`, `missile_SRM`,
  `missile_locust`, `missile_harpoon`, `missile_squall`, `missile_sabot`,
  `missile_sabot_warhead`, `missile_hydra_mdem`, `dragonfire`, ...), oriented along the
  **tangent of the path**, not the chord;
- an **engine flame and glow** from the projectile's `engineSlots` (`engineColor`,
  slot length and width) using the mod's existing engine-flame and glow sprites
  (graphics/fx `engineflame32`, `engineglow32`, `glow64`), flickering as ship
  nozzles do;
- a **contrail ribbon** from the slot's `styleSpec` (`contrailColor`,
  `contrailDuration`, `contrailWidthMult`, `type` SMOKE or GLOW), drawn by sampling
  the closed-form path at past times, so a trail costs no state. The existing
  `ContrailStyle.MISSILE_SMOKE` ribbon is the first candidate; the current puff
  chain remains the fallback;
- a **split flash** and smoke at carrier release (`smokeSpec`, `splitSound` from
  the behaviour spec), then the warheads' own sprites;
- impacts are the existing heavy impact or the sub-warhead blast, tinted by
  `explosionColor`; a swarm's thousand small flashes are batched and thinned by
  the same sound-gap rule that already thins a burst.

### Determinism and bounds

- **Random numbers**: no body ever draws from the shared stream at flight time.
  A volley takes one number from the system `Random` at fire time to seed a
  stateless hash `(seed, bodyIndex, stream)`; every offset, phase and aim point
  is a pure function of that hash. Adding bodies cannot shift another volley's
  draws, which keeps scene verdicts stable under tuning.
- **Caps**: at most 32 bodies per volley (a Locust is 30; a Hurricane's bloom is 7,
  Squall 20), at most 160 live bodies per ship and 480 in the battle. Over a cap
  a new volley **fuses**: bodies are folded into fewer weighted ones whose damage
  and area are the sums (the budget is preserved, the picture is thinned).
- **Costs**: no per-body integration; arrival is a clock compare. Point defence
  scans the live bodies once per mount tick (<= 480 x ~10 mounts, spatially
  prefiltered by the ship). Rendering is the real cost: about 3 draws per body
  plus ~6 contrail samples, culled to the camera by the existing collect-culling,
  and degraded at far zoom (`ZoomDetail`): no weave sampling, no contrail below
  a threshold.
- **Cue thinning** stays: one fire cue per fraction of a second per mount, one
  impact cue per gap per weapon, not one per body.

## Slicing (open work)

1. Shipped: data-driven flight for every missile mount, the true MIRV payload and
   the damage radius (see **Shipped (slice 1)** above).
2. Shipped: volleys and bodies (see **Shipped (slice 2)** above).
3. Shipped: a homing missile aims ahead of the unit it was fired at (`HomingLead`), by
   its seeker's turn rate; unguided and `GUIDED_POOR` rounds go where the unit was.
   Still approximate: the aim is fixed at fire, not steered in flight, so a unit that
   changes course after launch still escapes.
4. Later, not yet stories: honouring `canSplitEarly`; anti-fighter preference for `ANTI_FTR` weapons once aircraft
   are hittable; Dragonfire standoff and non-interceptable beam payload; Hydra
   flanking; threat-weighted point-defence target choice; a `renderEvidence` figure
   for the `SHUTTLES` layer with a full swarm in view; a measured hardened-target
   breach by a swarm against a compound.

## Evidence plan

- **Unit tests** (one unit each): the path function (endpoints equal the committed
  ones, speed segments continuous, lateral acceleration under the spec's turn
  bound, same seed same path); the budget (summed damage and area equal the
  weapon's for any *n*; the cap fuses without changing sums); the spec reader
  (Annihilator unguided, Locust homing, Hurricane carrier with 7 x 500, Reaper
  with arming 0.5 s) against the install's real files.
- **Behaviour scenes** in `src/test/.../battle/warship`: `warship-rocket-flight`
  (Annihilator volley over a field: landing dispersion and total arrival time
  within tolerance of the straight-line control, wobble visible, a walking
  target missed by unguided and hit by homing), `warship-swarm` (Locust,
  Hurricane and Squall on a dispersed squad against the single-blast control,
  summed damage equal, kill share higher, impacts staggered over seconds),
  extending `warship-point-defence` with a saturation loop (the same PD against
  30 bodies versus 3 Reapers). Verdicts that can fail: `landing-clock-preserved`,
  `damage-budget-conserved`, `bodies-capped`, `pd-saturates`, `carrier-kill-loses-payload`.
- **Snapshots**: a `warship-missiles` `createSnapshots` suite at 1920x1080,
  100% scale: a rocket volley, a swarm converge, a Hurricane bloom and a Reaper
  at close, lane and whole-map framings; a control frame per behaviour.
- **Performance**: a render-evidence figure for the `SHUTTLES` layer with a
  full swarm in view and a cap-hit run, compared against its own `-D` control
  (the project's existing rule), never against a number in a document.

## Risks and rejected alternatives

- **Full steering integration per body** (turn rate, turn acceleration, proportional
  navigation). Rejected: the renderer and the sim would each own a path, replays
  would drift, cost scales with bodies x ticks, and the result is a sim of
  missiles the ground battle does not need. Closed-form paths are the choice.
- **Bridging real vanilla missiles into the combat engine.** Rejected by
  `vanilla-combat-bridge-nouns.md` law 4: the vanilla engine cannot express area
  effect on infantry.
- **Visual-only submunitions.** Rejected as a default: they cannot be intercepted
  or saturate, so the lore (Sabot, Hurricane, Locust) would be a picture. Kept as
  the fuse-over-cap fallback only.
- **Splitting torpedoes by default.** Rejected: it erases the lore of the Reaper
  and Atropos as single dramatic rounds. Available as CINEMATIC.
- **Tiny blasts that do not breach.** Wall and roof damage may have thresholds
  that a swarm of small shares never meets. Mitigation: wall damage rides the
  volley (accumulated per target), not each body, or the share carries a minimum
  breach radius. To be checked in `Detonations` in slice 2, and a verdict.
- **Too many impacts for the effects budget.** Hundreds of simultaneous heavy-impact
  blasts could flood the effect pools. Mitigation: bodies below a damage share
  use a light impact; fusing at the caps; `renderEvidence` check.
- **Homing makes dispersal useless.** If every missile hunts, the counterplay of
  spreading out is gone. Mitigation: terminal commit radius, hp-limited agility,
  and `GUIDED_POOR` lag; check with the existing dispersed-squad control.
- **Length of drumroll.** Stagger lengthens the time from fire to full effect;
  telegraphing must still read. Cap the stagger window.
- **Authored table drift.** A table keyed by weapon id decays as modded weapons
  arrive. Mitigation: archetype first, id only as override; unmatched weapons use
  the derived shape.

## Questions for the owner

The data cannot answer these:

1. **(Answered: spectacle first, open to changing behaviours; feel over fidelity.)**
   **How literal is the Dune swarm look?** Only where the game's weapon already is a
   swarm (Locust, Swarmer, Squall, Annihilator streams, Hurricane/Sabot/Hydra
   bloom), or should heavy rockets (Hammer, even Reaper) also split into
   submunitions when asked? Recommended: FLAVOURED by default, CINEMATIC as a
   per-mission or per-ship switch, torpedoes single by default.
2. **(Answered: yes, point defence is the counter on the ground and in the air.)**
   **Are swarm bodies damage-bearing and individually killable by point defence?**
   Recommended yes (caps and fusing keep it affordable), so saturation is real.
3. **(Answered: yes; fun and spectacle first, balance afterwards. Shipped in slice 1.)**
   **Is a MIRV's true payload the intended power?** The Hurricane is 7 x 500
   (3,500), the Sabot 5 x 200, the Hydra 6 x 500, against the one row each of
   the weapon table the barrage reads today. Honouring the spec makes them
   stronger than they are now; consistent with "overpowered by design", but
   it is a balance change.
4. **(Answered: the damage footprint; the visual radius only draws. Shipped in slice 1.)**
   **Which blast radius is the damage?** The spec's `explosionRadius` is
   "purely visual", the real one is `explosionSpec.radius`/`coreRadius`. The code
   uses the visual figure: a Reaper blasts 15.8 cells rather than 7.9 and a
   Hammer 10.4 rather than 4.5. Keep the large radius (spectacle, owner intent)
   or move to the damage radius?
5. **Can missiles hit aircraft?** `ANTI_FTR` weapons exist for exactly that. Does a
   ground battle's airborne craft count as a missile target, and does a
   hostile capital's swarm threaten the player's own shuttles and fighters?
6. **Do guided missiles track moving ground units?** If yes, a homing swarm
   punishes a running squad (and dispersal becomes the only counter); if no, every
   missile is effectively a rocket aimed at where the target was. Recommended:
   track until a terminal commit, with `GUIDED_POOR` lagging.
7. **(Answered: yes, a faint dashed pulsing ring on the target disc for FLAVOURED and CINEMATIC, off for FAITHFUL. Shipped in slice 2.)**
   **Should a swarm's footprint be telegraphed?** The barrage rule promises a
   telegraph; the swarm's aim points are individual. A ring on the footprint, a
   dot per body, or only the visible flight?
8. **(Answered: 0.6 to 2.5 s of stagger on top of each body's own travel, about 4 s cinematic, a parameter of the preset. Shipped in slice 2.)**
   **How long may a salvo take to arrive?** Staggering a 30-body volley over one
   to two seconds reads as a ripple; the same over eight seconds reads as
   many small separate shellings. What is the longest acceptable drumroll?
9. **Restricted weapons**: Omega, Threat and Dweller launchers (Rift Torpedo,
   Devouring Swarm, Resonator MRM) are out of scope here. Should a hull ever carry
   them, with their own lore, or stay excluded?
