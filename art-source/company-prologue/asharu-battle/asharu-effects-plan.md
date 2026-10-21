Status: DRAFT — work plan for the Asharu effects day (2026-10-21)
Written: 2026-10-20
Updated: 2026-10-21 — W0 done: per-ship SWFX copies proven independent; the bench and its job template are in.

# Asharu battle: effects work plan

`asharu-effects.md` is the source. It says what each effect looks like in the game and in the
cinematic. This plan says how it gets built: workstreams, what each reuses, which shots need it
first, the light test that proves it, and how the day splits into sessions. There are no full
renders until the bench has answered the taste questions.

## What already exists

| Asset | Where | Gives us |
|---|---|---|
| Shared VFX kit (`SWFX`) | `second-wind-vfx/` | thrusters, guns (flash, light, tracer, puff, brass), launchers (bloom, missile with motor flame and smoke), energy mounts, beams, fire/vent/explosion, shields, lamps |
| `SWFX` in every Asharu build | each build's `.blend` | the effects collection exported with the ship; the blockout already appends the Phaeton wreck's this way |
| Shield group | `swfx_shield.py`, `review_shields.py` | FRONT and OMNI shells fitted to the hull; tech colour schemes by rule (`low-tech` red, `midline` blue, `high-tech` blue reference finish); `shield_strength` from vanilla efficiency (thins and drops out in patches when low); 4 hit flashes; `in_arc` for front shields; review strips including flicker |
| Energy mounts | `swfx_guns.py`; `wolf-cinematic/vfx_hull.py` `ENERGY_COLOURS` | coloured flash, light and thick bolt for ion cannon and mining blaster |
| Beams | `swfx_beams.py`; `paragon-cinematic/vfx_hull.py` `BEAMS` | vanilla width, colours and charge timings; `clear_line` and `aim` helpers |
| Turret kit | `turret-kit/` | every gun, launcher box and PD laser on the hulls, with `muzzle anchor <k>` at each bore and cell, and recoil empties |
| Cook-off | `phaeton-cinematic/`, `swfx_fire.py` | the Phaeton wreck: 22 fires, 6 vents, an 8-blast cook-off driven from `break_frame` (accepted 2026-10-19) |
| Shield profiles | `colossus-cinematic`, `colossus-mk3-cinematic`, `tarsus-cinematic` `vfx_hull.py` | vanilla FRONT/OMNI shields already declared |
| Launcher profiles | `manticore`, `falcon`, `kite`, `shepherd`, `wolf` `vfx_hull.py` | missile tubes declared |
| Blockout placeholders | `battle_blockout.py` | tracers, missiles, blasts, fire, smoke, debris, flares, entry trails: what the key frames draw today |

## Workstreams

Each workstream lists what it reuses, what is genuinely new, and its proof.

### W0 Integration spike (serial, first): done 2026-10-21

The kit was built for **one hull per file**: one `SWFX controls` object whose knobs drive that
ship's effects. The scene places several hulls of one build (three Lashers, two Hounds (P), two
Wolves (P), two Kites (P)), and collection instances would share one set of knobs.

**Result: every placed ship gets its own full copy, and the copies are proven independent.**
`ship_fx.ShipFx` copies the build's `SWFX` tree for one ship: objects, lights, meshes and
materials. It then points every driver, parent and pointer at the copy. A build that drives hull
parts from its own control (the Wolfs' `gear_down`, the Mk.III's `grapples`, the Phaeton wreck's
`break_frame`) also gets its hull copied, sharing the meshes. Each copy also shifts the kit's
`frame`-noise drivers by its own offset, so engine flicker and beacon blinks are out of step too.
Drivers keyed to a `*_frame` knob stay on the scene clock, so the wreck's cook-off timing is
unchanged.

Proof (no render):
- `python battle_blockout.py --fx-check` stages every cued key frame under the kit and reads
  each ship's copy back from Blender's evaluated scene. On k3 (S6) the three Lashers read
  throttle 0.2 / 0.6 / 1.0, firing none / `t3_1` / all three, at 0 / 2,500 / 7,500 W of flash
  light. On k3e (S6d) the two Hounds (P) differ the same way. The audit covers 14 ships: none
  of them reaches another ship's copy or the library original. No driver is invalid. Verdict
  `PASS`, written to `fx-check.json`.
- `python effects_bench.py --tests w0.two-lashers --check`: two copies of the Lasher build
  keyed through the kit's own `swfx_guns.fire`. The flash envelopes interleave as keyed: A
  fires on frames 2 and 5, B on 3–4. Each ship's tracers sit in its own Guns collection, ride its
  own muzzle anchors, and use its own tracer material reading its own controls.
- The harness ran end to end through the light lane: job `asharu-fx-w0-two-lashers-2026-10-21`
  (now `bench-job.template.json`) finished `ok` on Windows. It took 97 s for four frames, about
  15 s a frame at half size and 16 samples, and posted
  `review/asharu-fx-w0/2026-10-21_w0-two-lashers_windows/`.
  - On the sheet, the full-throttle Lasher's hot plume stands beside the quarter-throttle one's
    faint haze, and the tracers alternate between the ships as keyed.
  - Light MG flashes are too small to judge at that 360 m framing. Gun tests should frame closer.
  - That run's sky came out 0.05 grey rather than black: a new world's own Background node.
    The bench now zeroes it.

**What W1–W6 build on:**

- **The bench.** `effects_bench.py` sets up a black sky, a fixed key and fill light and a fixed
  camera. Each test places 1–3 builds, each ship with its own copy. It renders at half size and
  16 samples or fewer, and skips frames already rendered, so a swapped-out job resumes.
  - Each workstream adds tests in its own `bench_<module>.py`: `bench_shields.py`,
    `bench_missiles.py`, `bench_pd.py`, `bench_guns.py`, `bench_impacts.py`. Copy the
    `bench_w0.py` pattern:
    `TESTS = {name: dict(builds=[...], frames=[...], note=..., setup=fn)}`.
  - `setup(bench)` calls `bench.ship(key, build, at=, heading=, throttle=)`,
    `bench.camera(loc, target, lens)` and `bench.at(ship, local)`.
  - `python effects_bench.py --list` lists every test; `--tests <module.name> --check` builds and
    keys the rig in Blender, prints each ship's readings, and renders nothing.
  - `--write-job --tests ... [--machine any] --date D --what <slug> --out DIR` writes the
    light-lane job. It pins only the builds the tests name, by sha256. Leave the machine as `any`
    (the default): Drive on the Mac stopped syncing on 2026-10-21, so a Mac-only job stalls. Submit it with
    `tools/render_queue.py submit <job> --commit <rev>`. One job may carry several tests (keep it
    under 30 minutes).
  - Each module posts to its own review asset, `review/asharu-fx-<module>/<date>_<what>_<machine>/`:
    four frames to a 1920x1080 sheet, with a `NOTES.md` that records each ship's readings per
    frame. That asset alone is pruned after a post, so one workstream's posts never prune
    another's, or the scene rounds.
- **Per-ship knobs.**
  - `ship.set(name, v)` and `ship.key(name, frame, v)` act on one ship only. They reach both the
    SWFX knobs and hull knobs such as `gear_down`.
  - `ship.flash(label)` lights one gun for a still.
  - `ship.reading()` reports what the copy evaluates to on the current frame.
  - `ship.object('<kit name>')` finds this ship's copy of a kit object.
- **The kit's own helpers, per ship:**

  ```python
  with ship.kit() as (ctx, kit):
      kit.guns.fire(ctx, 1, 4, shots=6, interval_frames=3)
  ```

  This also works for `kit.thrusters.ignition` and the other helpers. Inside the block:
  - the kit's modules are loaded for that build's hull profile and scale;
  - its rigs (`swfx_guns._RIGS`) are this ship's flash and launch volumes;
  - this ship's copies carry the kit's own names, because the kit finds materials and meshes by
    name.

  Anything the helpers create belongs to that ship and is renamed out of the way afterwards. A new
  kit group (shield flux, overload, PD, missile paths, impacts) gets per-ship behaviour for free
  if it does two things:
  - keeps its state in `SWFX controls` knobs and objects under the `SWFX` collection;
  - reaches the ship through `ctx` (`ctx.controls`, `ctx.collection(group)`, `ctx.anchors`)
    rather than through `bpy.data` lookups by name.
- **Builds must carry the new group.** A build's `SWFX` is baked into its `.blend`, so a
  new group (W1's shield, say) reaches a test once of two things happens. Either the hull is
  re-exported with the group applied, or the test builds the group onto the ship's copy. The
  second way means calling the group's `build(ctx, ...)` inside `ship.kit()`, with
  `ctx.anchors` already this ship's.
- **The scene's cue list (W6 and the final integration go through `battle_blockout.py`).**
  - A key frame takes `cues={ship: {'fire': 'all' | ['t1_1', ...], '<knob>': value}}`.
    `--check` lists each frame's cues beside the other ships of the same build.
  - `--swfx` turns the kit on for every finished build. The kit's drives then follow each
    placement's throttle, and the placeholder plumes are dropped for those ships. Without
    `--swfx` the frames render as before.
  - Cues are on k3 and k3e so far.

**Limits W1–W6 should know:**
- A hull without its own control object stays one shared collection. Turret traverse is
  therefore the build's pose, the same for every copy. The guns' effects ride their own copies'
  anchors, which ride the shared turrets.
- Per-round randomness in `swfx_guns` is seeded by turret, frame and round. Two ships firing the
  same gun on the same frame draw the same flash shape. Stagger them, or set `gun_seed_*`.
- Some kit lookups are still by name outside `ship.kit()`: `swfx_shield` (its anchor),
  `swfx_beams` (muzzle, rig) and `swfx_lighting` (the grapple head). Run that code inside the
  block, or pass the object.

### W1 Shields

- **Reuse:** the shield group entire: the fitted shell, FRONT/OMNI, tech schemes by rule,
  strength, hits, `in_arc`, flicker.
- **New:**
  1. `SHIELD` entries for the Lasher (FRONT 210, low tech), Wolf and Wolf (P) (FRONT 150, high
     tech), Falcon (P) (FRONT 270, midline), Kite (P) (OMNI 150, midline), Manticore (P) (OMNI
     150, low tech), Phaeton and Shepherd (OMNI 90, low tech). These are data in each hull's
     `vfx_hull.py`, `SHIELD` block only.
  2. **Flux load:** a `shield_flux` knob (0-1) that brightens the face toward white and enlarges
     hit flashes.
  3. **Overload:** the shell shatters into arcs crawling over the hull, then the lamps stutter
     (`shield_overload` envelope; arcs reuse the beam core shader).
  4. **Vent:** `vent` anchors exist in the fire group; a running ship's vent is a short hot
     shimmer from its radiators, not a fire. Add a `shield_vent` envelope that drops the shield
     and lights the vent cones.
- **Files:** `swfx_shield.py`, `review_shields.py`, the `SHIELD` blocks of the hull profiles,
  `bench_shields.py`.
- **Proof (light):** a Lasher front shield and a Kite omni at 300 m, 8 frames each: raise, take
  four hits, flux climbing, overload, vent.
- **Done when:** the tech colours read correctly by rule, and an overloaded shield is unmistakable
  at 28 mm (S9b).

### W2 Point defence

- **Reuse:** guns (Vulcan, light MG tracers), beams (`swfx_beams`, vanilla PD laser and LR PD laser
  colours), and the fire group's explosion at small `size_m` for a missile kill.
- **New:**
  1. **Intercept:** a PD source picks missiles from a salvo and kills some short: a tracer stream or
     beam walks onto the missile, which flashes and its motor dies.
  2. **Canister flak:** a ring of small bursts around the Manticore.
  3. **Flares:** white-orange decoys from the flare anchors (the Colossus has them and the kit has
     no flare effect); a missile peels off to chase one.
- **Files:** `swfx_pd.py` (new), the `BEAMS` blocks for the Wolf's PD lasers,
  `bench_pd.py`.
- **Proof (light):** a four-Harpoon salvo into a Lasher's three light MGs: one or two die short.
  Then a Swarmer salvo into a PD laser. Then a Hound firing flares.
- **Done when:** the S5 missile screen reads as the Lashers fighting the salvo, not as random
  sparks.

### W3 Missiles

- **Reuse:** the launcher group (launch bloom and light, missile body, motor flame, motor smoke)
  and the tube anchors on the Manticore's Harpoon pods (`Anchor turret 5/6 tube <k> muzzle`).
- **New:**
  1. **Curved flight:** the kit flies a missile straight down the line of fire. Add authored
     curves from the game's numbers: the Harpoon swings wide and curves in, the Swarmer weaves,
     the Pilum lumbers in and splits, rockets fly straight in a fan of five.
  2. **Contrails** left in place, thinning out with no drift, in vanilla colours: the Harpoon's
     blue (100,165,255), and grey smoke for the others.
  3. **Terminal:** a hit (an explosion at the target's armour or shield) or a kill short (handed
     to W2).
- **Files:** `swfx_guns.py` (missile path only), `swfx_missile_paths.py` (new), `bench_missiles.py`.
- **Proof (light):** a Manticore pod salvo of four Harpoons across a 3 km test lane. 8 frames
  showing the fan-out, the curve and the convergence, plus one kill short.
- **Done when:** the salvo reads as Harpoons to a player: blue, curving and converging.

### W4 Cannons and energy weapons

- **Reuse:** gun flash, light and tracer; energy mounts; turret-kit muzzles; beams for PD.
- **New, per weapon look from `asharu-effects.md`:**
  - **Manticore main gun:** the build models a Hellbore (the variant says Mark IX). Hellbore in the
    game: an orange-white slug, glow 255,165,30, a huge flash (50 particles, 0.75 s, 255,120,50).
    Decide which (taste question 1).
  - **MG and Vulcan:** burst cadence exact; the Vulcan's twin-barrel zig-zag braid.
  - **Ion cannon:** the charge-up glow, the jagged bolt, and EMP arcs crawling over the target
    (shared with W1's overload arcs).
  - **Mining blaster and heavy blaster:** a white core in a coloured corona that lights the hulls
    it passes.
  - **Ammo Feeder:** an amber weapon glow (255,200,0) and the cadence doubled.
- **Files:** `swfx_guns.py` (gun and energy paths), the `ENERGY_COLOURS`/`BEAMS` blocks of the hull
  profiles, `bench_guns.py`.
- **Proof (light):**
  - the Manticore main gun, one shot;
  - a Lasher MG burst, then with the Ammo Feeder;
  - the Wolf's ion cannon on a target, with arcs;
  - the Wolf (P)'s mining blaster.
  6 frames each.
- **Done when:** each reads as its game weapon without a caption.

### W5 Impacts: bench pass 2 posted 2026-10-21, awaiting the owner

`swfx_impacts` (opt-in, never in `swfx.GROUPS`) places kinetic and HE hits, breaches and debris
at runtime on one ship's copy (`ship.kit()`), each timed by its own empty. `strike` routes a round
through W1's shield knobs only. `bench_impacts.armour-breach` plays the two beats on a Hound (P):
`review/asharu-fx-impacts/2026-10-21_w5-impacts-pass2b_mac/`.


- **Reuse:** shield hit flashes (W1) and the fire group's small explosion, fire and vent at
  `size_m`.
- **New:**
  1. **Armour hit:** a kinetic spark spray, or an HE orange flash with spall, leaving a glowing
     pock.
  2. **Hull breach:** a fire and vent spawned at a runtime empty on the hull, rather than at the
     builds' scar anchors (the clean builds have none).
  3. **Debris:** tumbling plate fragments and sparks, to replace the blockout's debris placeholder.
- **Files:** `swfx_impacts.py` (new), `bench_impacts.py`.
- **Proof (light):** a Hound (P) taking a light MG stream (sparks), then light assault
  gun HE (flashes), then a breach that starts burning. 8 frames.
- **Done when:** S6b's "armour soaks it, then she breaks up" reads in two beats.

### W6 The S6 cook-off tie-in: cause authored 2026-10-21, cue hand-over to session 1

`bench_impacts.COOKOFF` is the cause as cue data: two MG bursts and three Harpoon warheads, the
third on the wreck's strike point at `break_frame`. `k3_cue` gives the k3 entries for
`swfx_impacts.apply_cue`. Bench test `impacts.cookoff-caused`, same review run as W5.


- **Reuse:** the accepted Phaeton wreck and its `break_frame` cook-off, unchanged.
- **New:** the strike that causes it. The Harpoons from W3 arrive at `break_frame` and the lead
  Lasher's fire from W4 is visible in the frame, so cause and effect line up. This is scene
  timing, not new effects.
- **Files:** `battle_blockout.py` shot cues (through W0's owner).
- **Proof (light):** the k3 framing at half size and 16 samples, three frames around
  `break_frame`.
- **Done when:** the cook-off is caused on screen.

## Shot map, most visible first

| Priority | Shot | Effects |
|---|---|---|
| 1 | S9b (28 mm, close) | Falcon front shield under load, then overload; Lasher MGs with Ammo Feeder; armour hits on the open flank; IR pulse broadside; the Lasher burning |
| 2 | S6 (the cook-off) | Harpoon salvo arriving, Phaeton cook-off, Lasher hit and burning, MG fire |
| 3 | S5 (85 mm, the opener) | Manticore main gun and Harpoon salvo over 3-4 km, the Lashers' PD screen killing some short |
| 4 | S6c | Displacer blink, Wolf (P) front shields up under the Mule's fire then down, ion cannon and mining blaster on the Tarsus's drives |
| 5 | S6b | Ammo Feeder burst, armour soaking hits on a Hound (P), breach, break-up |
| 6 | S6d | chaingun, flares, no shields, drives hit |
| 7 | S9 | Colossus flares and turrets, a Kite's omni shield driven off |
| 8 | S7, S8 | boarding cover fire; the Colossus's tug pod shot out, debris |
| — | S1-S4, S10-S12 | drives and lamps only (already in the builds) |

## Proof jobs: three light-lane jobs

Each job stays under the light lane's 30-minute cap: half size, 16 samples, about 40 frames. Each
runs `effects_bench.py --tests <list> --samples 16 --half --root {work} --post {machine}`, written by
`effects_bench.py --write-job` (see W0), and posts each module's sheets to
`review/asharu-fx-<module>/<date>_<what>_<machine>`.

| Job | Tests | Machine | Why |
|---|---|---|---|
| A | shields (W1), guns and energy (W4) | Windows (RTX 3080, 10 GB) | emissive shells and beams, little volume; fits 10 GB |
| B | missiles (W3), point defence (W2) | Mac (M5 Max, 48 GB unified) | motor smoke and many lights; volume-heavy |
| C | impacts (W5), cook-off (W6) | Mac | fire and explosion volumes; the cook-off framing used 7.5 GB at half size on Windows |

A test's job is submitted when its workstream says done. Jobs submitted after the Jangala finals
are released, per the owner.

## Split into sessions

| Session | Workstream | Files it owns | Machine | Done when |
|---|---|---|---|---|
| 1 (first, serial) | W0 integration spike and bench harness | `battle_blockout.py`, `effects_bench.py` | Windows | two copies of one build fire independently; the harness runs one test through the light lane |
| 2 | W1 shields | `swfx_shield.py`, `review_shields.py`, `SHIELD` blocks, `bench_shields.py` | Windows | job A's shield tests read as specified |
| 3 | W3 missiles, then W2 point defence | `swfx_missile_paths.py`, `swfx_pd.py`, the missile path in `swfx_guns.py`, `bench_missiles.py`, `bench_pd.py` | Mac | job B shows a curving salvo with kills short |
| 4 | W4 cannons and energy weapons | `swfx_guns.py` (gun/energy paths), `ENERGY_COLOURS`/`BEAMS` blocks, `bench_guns.py` | Windows | job A's gun tests read as their weapons |
| 5 | W5 impacts, then W6 cook-off tie-in | `swfx_impacts.py`, `bench_impacts.py`; W6's cues through session 1 | Mac | job C reads in two beats; the cook-off is caused |

- **Serial:**
  - W0 goes first. Every bench test needs its harness.
  - W6 needs W3's arrival and W0's cue list.
  - W2's intercepts need W3's missile paths, which is why one session holds both.
  - The scene integration of everything (key frames using the kit) is a last serial step on
    `battle_blockout.py`, owned by session 1.
- **Shared files:**
  - Sessions 3 and 4 both touch `swfx_guns.py`, in different paths. Session 3 owns the
    launcher/missile code; session 4 owns guns and energy. Both merge main before integrating.
  - The hull profiles' `vfx_hull.py` are split by block: `SHIELD` belongs to session 2; `BEAMS`
    and `ENERGY_COLOURS` belong to session 4.
- **Kit compatibility:** `second-wind-vfx` serves other ships too. Every change is opt-in, by new
  knob or group, and `check_kit.py` passes on the Second Wind unchanged.

## Risks

- **One hull per file (highest).** If per-ship copies of `SWFX` can't keep independent drivers,
  the scene needs another route: apply the kit per ship at scene time with namespaced controls,
  or bake effects per ship. W0 exists to find out first.
- **Render cost.** Every flash and launch is a light, and smoke scatters. A full key frame with
  a dozen firing ships could cost several times today's render. Measure at the bench, then budget
  lights per shot.
- **AgX washing colour to white.** The kit notes strong emitters go white, so tech colours and
  energy colours must be kept low. This is a check on every test.
- **Scale.** Game ranges are sprite units, and the scene is kilometres. The order is kept, the
  numbers aren't; tracer speeds are cinematic (the kit's `gun_muzzle_velocity` note).
- **10 GB on Windows.** Volume-heavy tests go to the Mac.

## The owner's answers (2026-10-20)

1. **Main gun:** the Hellbore (the turret kit's model, already on the build) is the leaning.
2. **Shields:**
   - visible while up (a faint rim, and the direction reads);
   - opacity tied to hits, with ripple rings spreading along the surface;
   - raising takes time (the kit's unfold);
   - collapse: see 5.
3. **Flashes** linger a little for spectacle.
4. **Missiles:** guided ones are chase missiles that hunt and curve, which is harder for PD.
   Torpedoes and rockets fly straight. Launches are cold-ejected, then lit.
5. **Overload:** the shield is a film of suspended plasma consumed by what it absorbs and
   replenished by the emitters. Flux thins it: wider ripples, patchy thin spots, soap-film
   banding, hits leaking through. Overload tears it open from the last hit and it shatters into
   fading shards. The tech finishes (smooth high tech, rough midline and low tech) are the film's
   quality.

`asharu-effects.md` carries the full treatment. For W1 this replaces the "brightens toward white"
flux idea with **thinning**: `shield_flux` drives patchiness, ripple spread and leak-through.
`shield_overload` is tear, shatter and arcs to the nodes, and recovery repaints from the nodes.

## References (web, 2026-10-20)

- **Shield construction:** a Fresnel rim, a surface pattern, flowing noise, a noise-threshold
  dissolve with a glowing edge for appear and collapse, and hits as rings whose radius grows with
  time, measured along the sphere's surface ([pmndrs, Flow shield](https://pmnd.rs/blog/creating-flow-shield);
  [GameDev.net shader lab](https://gamedev.net/shaderlab/35-energy-shield/);
  [Godot shield with impacts](https://godotshaders.com/shader/energy-shield-with-impact-effect/)).
  The kit's four shrinkwrapped hit empties are this ring buffer.
- **Opacity:** most of a shield's read comes from a Fresnel term on opacity
  ([Halo shield recreation](https://brendansullivan.artstation.com/projects/nJq6X1)).
- **Thin-film banding** on a thinning bubble: film thickness drives the colour, near-black below
  ~100 nm and saturated bands at 200-600 nm. Animate thickness down to sell thinning. Cycles'
  Principled BSDF has a Thin Film input
  ([Blender Market thin-film docs](https://superhivemarket.com/products/thin-film/docs)).
- **Missile launches:** in *The Expanse* missiles are passively ejected before their drives
  ignite ([The Companion, CQB](https://thecompanion.app/the-expanse-cqb-space-battle/);
  [Spine VFX breakdown reel](https://digitalproduction.com/2021/05/31/the-expanse-vfx-breakdown/)).
