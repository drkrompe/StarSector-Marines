Status: DRAFT — grounding the Asharu brawl's weapons, missiles, shields and systems in the game
Written: 2026-10-20

# Asharu battle: effects

The Asharu cinematic is a fight the player has seen a thousand times as 2D sprites. The effects
must be recognisably those weapons, the same colours, cadences and behaviours, so a player names
the gun before the caption does. Then they are reimagined as real machinery in vacuum at true
scale. **Ground first, then reimagine:** every entry below states what the game does (read from
`starsector-core/data`), then the cinematic treatment.

None of this is in the blockout yet. Today's frames draw tracers, missile trails, blasts and
smoke as placeholders (`battle_blockout.py`). The real treatment comes from the Second Wind VFX
kit (`second-wind-vfx/`), which every Asharu build already carries as its `SWFX` collection.
The kit has muzzle flash, tracers, missiles with motor flame and smoke, front and omni shields,
beams, fire, vents and explosions. What it lacks is listed under "New for the kit".

## Loadouts

A build that models its guns names them in its manifest. Its `variant_weapon` is the game
variant it fits, and that variant is the loadout. Where a build models no guns, the loadout is the
game's stock variant for that hull, so what the player has fought is what they see.

| Ship | Variant | Guns | Missiles | System |
|---|---|---|---|---|
| Manticore (P) | `manticore_pirates_Support` (build) | **Hellbore** (modelled; the variant's Mark IX swapped for it, owner leaning 2026-10-20), 2 light assault guns, light needler | 2 Harpoon MRM pods | canister flak |
| Wolf (P) x2 | `wolf_d_pirates_Attack` (build) | ion cannon, mining blaster | Annihilator rockets | Displacer |
| Wolf, merchant | `wolf_Assault` (build) | ion cannon, 2 PD lasers, heavy blaster | Harpoon | Displacer |
| Lasher x3 | build: 3 light machine guns | light MGs (a PD-heavy escort) | none modelled | Ammo Feeder |
| Hound (P) x2, Hound | `hound_d_pirates_Standard` / `hound_Standard` | assault chaingun, light dual MG | none | flares |
| Falcon (P) | `falcon_p_Strike` | IR pulse lasers | Typhoon Reaper, Annihilator pod, Swarmers | (front shield 270) |
| Kite (P) x2 | `kite_pirates_Raider` | light dual MG | Hammer torpedo, Swarmer | manoeuvring jets |
| Colossus Mk.III | `colossus3_Pirate` | light autocannons, light MGs | light mortar | flares |
| Colossus | `colossus_Standard` | light MGs | none | flares |
| Buffalo Mk.II (distant) | `buffalo2_FS` | antimatter blaster, LR PD laser, Vulcans | Pilum LRM, Swarmer | flares |
| Mule | `mule_Standard` | heavy autocannon, light MG | Salamander | manoeuvring jets |
| Dram | `dram_Light` | Vulcan | Harpoon | flares |
| Tarsus, Phaeton | stock | light MGs | none | burn drive / flares |

**The Lashers keep the build's three light machine guns** (owner, 2026-10-20: only the
cinematic look matters). That makes them PD boats, and it fits what they do in the scene:

- **S5:** they are the convoy's missile screen, hosing at the Harpoon salvo and killing some short.
- **S9b:** kinetic is the game's anti-shield damage, so the lead's guns honestly press the
  Falcon's front shield into overload.
- **S6b:** the Ammo Feeder doubles their rate for the kill.

One beat leans on the game: an unshielded Hound (P) (S6b) is the target kinetic fire handles worst.
If it should read true to players, Lasher B's feeder burst strips her and the merchant Wolf's heavy
blaster breaks her up. The radio line "She's breaking up. One down." still works.

## Guns: the families the player knows

The game draws a shot two ways:

- `BALLISTIC_AS_BEAM`: an instant streak with a fading tail. Machine guns, the Vulcan, and every
  energy bolt work this way.
- `BALLISTIC`: a travelling shell sprite (the autocannons).

The colours below are the projectile's fringe and core in the game's RGBA.

**Flashes linger (owner, 2026-10-20).** A muzzle flash and its gas cloud hang a few frames longer
than true vacuum would allow, as spectacle. They thin out and never become the game's lingering
particle clouds.

| Family | Weapons here | The game | Cinematic treatment |
|---|---|---|---|
| **Machine guns** | light MG, light dual MG | 5-round bursts 0.1 s apart, then a 0.4 s pause (dual: 0.2 s). Warm white streaks (fringe 240,220,220). A small warm flash (255,240,165), 7 particles, 0.15 s. PD role. | Short hot slugs, white-yellow. The flash is a tight cone of propellant gas that expands and is gone within two frames in vacuum: no lingering cloud. The burst rhythm stays exact; it is the sound the player knows, made visible. |
| **Vulcan** | Vulcan (Buffalo Mk.II, Dram) | Twin barrels alternating, 20 rounds/s (cooldown 0.05). Fragmentation, 250 range. Pale gold streaks (240,220,180), same warm flash. PD. | A double-barrelled heavy machine gun, a bolter (owner). An unbroken braid of tracer from two muzzles alternating, so the stream visibly zig-zags. It hoses at missiles and fighters more than hulls; frag rounds flash into small bursts on what they hit. |
| **Autocannons** | light AC/dual AC, heavy AC, Mark IX | Green-white shells (fringe 235,255,215), travelling at 800. A wide pale-green flash cloud (165,225,185), 28 deg spread, 18 particles. Mark IX: 4-round bursts 0.1 s apart every 2 s. | Visible shells with a green-white heat glow, slow enough to follow across the frame. A broad green-tinged flash and a recoil kick on the barrel (visualRecoil 7). The Mark IX's four-round slam is the Manticore's signature rhythm. |
| **Hellbore** | Manticore (P) main gun | One heavy HE slug every 3 s, at 500 (slow). Orange-white (fringe 255,165,30, core white) with a glow radius 60. The game's biggest flash: 50 particles (255,120,50), 0.75 s, 15 deg spread, recoil 10. Passes through missiles. | The Manticore's signature. A big, slow, glowing orange slug you can follow across kilometres, and a huge orange flash and recoil kick that lingers. An HE detonation on impact, the largest gun hit in the fight. The modelled turret: the turret kit's Hellbore (large, caged barrel, square brake). |
| **High-explosive guns** | assault chaingun, light assault gun | Orange shells (fringe 255,200,70). The chaingun's rotary turret spins (4 frames at 20 fps); HE impacts. | Orange tracers. The chaingun's barrels visibly spin up. HE rounds burst on armour with orange flashes and debris, not sparks. |
| **Needler** | light needler | 15-round bursts 0.05 s apart, at 1200. Long white needles (length 150, fringe 255,255,255). | A fan of long white needles, much faster than anything else on screen. They read as kinetic flechettes, with sparks and spall against shields. |
| **Ion cannon** | Wolf (both) | Charge-up 0.5 s. A long crackling blue-white bolt (fringe 25,100,155, core 225,225,255; texture scrolls backwards). On hit: EMP arcs (`IonCannonOnHitEffect`). | A charge glow at the muzzle, then a jagged blue bolt. On a hit, lightning arcs crawl over the target's hull and its lights flicker. EMP is the effect, not damage. |
| **Blasters** | heavy blaster (blue-white), mining blaster (red), antimatter blaster (violet) | A short fat bolt with a soft glow radius (heavy 35, antimatter 75). | Plasma bolts: a white-hot core in a coloured corona that lights the hulls they pass. The antimatter blaster's violet flare is the biggest single flash in the fight. |
| **IR pulse laser** | Falcon (P) | Red pulses (fringe 200,0,0), charge 0.33 s. | Red light pulses, instant to the eye. They read by the glow on the target and a short red bloom in the camera, not by a moving bolt. |
| **PD lasers** | PD laser (red), LR PD laser (orange) | Continuous rough-textured beams that pierce projectiles. PD: fringe 155,0,0 with a red glow. LR: fringe 200,100,0. | Thin flickering beams that snap from missile to missile. A missile caught in one glows, sparks and pops. This makes point defence a visible screen around a ship. |

## Missiles: they track

Missiles are the most Starsector thing in the fight, because each type flies differently. Turn rate
and acceleration from each `.proj` set the flight. The engine and contrail colours set the look.

**Chase missiles and torpedoes (owner, 2026-10-20).** Guided missiles are chase missiles. They
hunt, curve and re-acquire, which is more fun to watch and a harder problem for point defence
in this setting. The Harpoon, Swarmer, Salamander and Pilum are chasers. Torpedoes and rockets
fly essentially straight: the Reaper, Hammer and Annihilator. A launch is a cold eject before
ignition, the missile pushed clear of the tube and then lit, as the space battles in *The
Expanse* stage it.

| Missile | Launcher | How it flies | Look | Treatment |
|---|---|---|---|---|
| **Harpoon MRM** | Manticore pods (bursts of 4, 0.25 s apart), Wolf, Dram | Guided. Turns at 70 deg/s, so a salvo swings wide off the launcher and curves back onto the target. Range 2500. | Blue engine (100,165,255), blue smoke contrail, blue explosion 150 | The S5 opener. Four blue motors fan out from the pods, arc across 3–4 km and converge. The curving contrails are the shot. |
| **Swarmer SRM** | Falcon (P), Kite (P), Buffalo Mk.II | Very agile: 240 deg/s, acceleration 2500, in salvos of 4. Weave in flight. | Orange motor (255,145,75), short grey smoke trail | A swarm of small orange motors jinking around each other on the way in. PD picks some off. |
| **Salamander MRM** | Mule | Heat-seeker (`HEATSEEKER` hint): goes for engines. Extremely agile (300 deg/s, acceleration 5500). EMP 1500. | Orange motor, grey trail, red-orange burst | A single missile that loops round a ship to hit its drives from behind. Good for one beat. |
| **Pilum LRM** | Buffalo Mk.II | Slow (125) and lumbering (35 deg/s). At 800 from the target it splits into a fast second stage (MIRV). Range 4000. | Orange motor, long grey smoke | Three slow motors drifting in from far off. A visible stage-split flash, then a fast terminal dart. |
| **Annihilator rockets** | Wolf (P), Falcon (P) pod | Unguided rockets, 5 per burst with fixed spread offsets (±7°, ±3.5°, 0). | Smoke blowback puff at launch (190,180,160) | A shotgun fan of five straight rockets with a smoky launch. They miss wide as often as they hit. |
| **Reaper torpedo** | Falcon (P) Typhoon | Unguided, no turning, slow acceleration. Flames out after 3 s. | Red glow motor (255,100,100), long red trail, explosion radius 350 | The heaviest hit in the fight: a red-glowing torpedo coasting in dead straight, then a big red blast. |
| **Hammer torpedo** | Kite (P) | Unguided strike torpedo, 1500 damage. | (to read when used) | Kite's sucker punch at close range. |

**Flight in the cinematic:** missiles fly along authored curves shaped by these numbers. A Harpoon
gets a wide arc, a Swarmer a weave, a Pilum a slow arc and then a straight dart. The curves are
not simulated. The kit's launcher knobs set launch speed, acceleration, motor brightness and
smoke. Vacuum gives no buoyancy and no wind, so contrails stay put and thin out rather than drift.
They are read as the motor's exhaust left where it was spent.

**Point defence answers them:** Vulcans, light MGs and PD lasers fire at incoming missiles, and
some salvos lose a missile or two to a small flash short of the target. That is the game's rhythm,
and the Manticore's canister flak is the loudest version of it.

## Shields, flux, venting, overload

From `hull_styles.json`, a shield is a translucent bubble with a white rim (255,255,255) and an
inner tint by tech: **low tech reddish** (255,125,125, alpha 75) and **midline and high tech
blue** (125,125,255). So the Lashers, the Colossus, the Manticore and the Mule tint red, and the Wolfs,
Kites and Falcon tint blue. This is a strong read across the screen.

**What a shield is (owner, 2026-10-20).** A film of suspended plasma, held by the emitters.
Absorbing a hit consumes the film where it lands, and the emitters replenish it. Flux is how
thin the film has been worn. The tech finishes already in the kit fit this:
- high tech is a smooth, even film;
- midline and low tech are coarser and murkier, a rougher film.

- **Up and down (game):** raising the shield takes a moment and costs flux upkeep. Every hit on it
  becomes hard flux.
  **Treatment:**
  - Visible whenever it is up: a faint Fresnel rim and a thin face, enough to read that it is
    up and which way a front shield faces.
  - **Raising takes time:** the film unfolds from the emitter both ways, led by a bright edge.
    The kit's unfold does this; it is held to about a second, never a pop. Lowering folds it
    back the same way.
  - **Hits sell where the action is:** opacity is tied to hits. Each impact brightens the film
    around it and throws a ripple ring outward along the surface, like an elastic water skin. The
    ring fades with distance and several overlap. Between bursts the shield sinks back to faint.
    Front shields cover their arc (Lasher 210°, Wolf 150°, Falcon 270°); omni shields turn to face
    the threat.
- **Flux, the film wearing thin (new):**
  - As flux climbs, ripples spread wider and ring longer.
  - The face goes patchy, with moving thin spots (the kit's low `shield_strength` already drops
    the film out in moving patches).
  - Thin-film banding shows where it is thinnest, the way a soap bubble colours just before it
    bursts.
  - Late in the load, a hit on a thin patch leaks through: a dimmed flash on the shield and a
    spark on the hull behind it.
- **Venting (new):** the ship drops the film and can't fire for a few seconds while it dumps heat.
  Radiators and vents glow and shed shimmering vapour jets. The ship is visibly helpless, guns
  silent: the beat to punish.
- **Overload, the collapse (new):** the film is consumed faster than the emitters can replace it.
  - It tears open from the last hit and the tear runs across the arc.
  - The film breaks into shards of plasma that flare, scatter and fade: a shatter, not a pop.
  - Arcs snap back to the emitter nodes, the lamps stutter, and the guns go dark.
  - Recovery is the emitters repainting the film outward from the nodes.

  This is the turn of each kill (pressure, overload, hull).

## Ship systems

| System | Ships | The game | Treatment |
|---|---|---|---|
| Accelerated Ammo Feeder | Lasher | Ballistic rate of fire doubled. The weapons glow amber (255,200,0). | The Lasher's guns glow amber and the burst cadence visibly doubles: the "she's going in" moment. |
| Displacer | Wolf (both) | A short teleport (range 300). Ten blue jitter copies (100,165,255) and a copy rendered during the jump. | The Wolf blinks: a smear of blue afterimages along a short jump, then she is suddenly elsewhere. New for the kit. |
| Canister flak | Manticore (P) | Flak canisters burst around the ship to kill missiles and fighters. | A ring of small flak bursts around the Manticore, swatting incoming missiles. New. |
| Flare launcher | Hound, Colossus, Mk.III, others | Decoy flares that attract missiles. | White-orange flares arcing off the hull, with missiles visibly peeling away to chase them. Placeholders exist (`flares`). |
| Burn drive | Tarsus | A straight-line burst of speed. | The drives flare long and hot: a bid to run. |

## Explosions: bursts, never orbs

Owner (2026-10-21): "In general I don't like orb explosions — they don't make sense." Nothing
in vacuum billows into a round fireball, so every detonation is an irregular, directional,
short-lived burst in one language (`swfx_burst`):

- a **jagged, lobed flash**, white-hot for a frame and gone in about three, leaning along the
  direction the blast throws (out of a hit face, back along a warhead's flight);
- **shrapnel** that leaves white-hot and cools through orange and dull red to dark within about
  a third of its flight, then flies on as dark fragments;
- **smoke** broken up by noise into a torn, patchy puff that thins and disperses, no billow.

Size, lobes, spikes, turn and fragments are seeded per burst, so no two match. This covers
flak, missile hits, kill pops, decoys and ignitions, torpedoes, HE shells and breaches, and the
muzzle gas of armed weapons. The fire group's explosion anchors take it with style `jagged`,
and the Phaeton wreck's baked cook-off is switched on its scene copy
(`swfx_fire.jagged_cookoff`, after its break_frame is set; coordinator call 2026-10-21 under
the owner's rule): its volume fireballs and shock rings are hidden and jagged blasts keyed in
their place. The opening flash is hot yellow-white running to the blast's colour, feathered at
the edge, for one to three frames. The old looks stay behind a switch as controls
(`blasts='orb'`, `STYLE='orb'`, `style='orb'`).

## Atmospheric entry: plasma shaped by the hull

Look passed (owner, 2026-10-22, on S12 close still 9): "this would work enough for me. Not perfect but passable."

S12 brings the Colossus and Morrow down into Asharu's air, with the battle's lost ships falling
around them in pieces. Owner on lit span 3 (2026-10-22): "still pretty far off. Reads like hard
shapes / arrowhead. The burn up doesn't take into account the shape of the ship." A cone or tube
behind a hull reads as engine exhaust, or as an arrowhead. Entry plasma is the hull's own
silhouette lit on its windward side.

**What sells it.**
- **Shape.** The hottest, whitest light sits on the faces turned into the airflow and in the shock
  layer just ahead of them. The hull reads dark against it. The wake is a consequence of that
  glow, not a cone fixed to the tail.
- **Wake.** It peels off the silhouette edges: corners, modules, pods, fins. Each edge sheds its own
  thin streamer, and the streamers merge downstream into one wake.
- **Colour.** White, then yellow, orange and deep red, ending in dull embers and grey ablation smoke,
  with a faint magenta at the shock's edge. Temperature follows distance from the windward face
  and from the hull, never a flat tint.
- **Turbulence.** Streamers leave the edge thin and laminar and tear up with distance; they never
  have constant width.
- **Breakup.** A bright main body sheds chunks, each with its own short tail. Small pieces burn
  briefly; older ones fall behind and redden.

**Sources.**
- Leah Lindner, *Atmospheric Entry*, a real-time re-entry shader:
  <https://leah-lindner.com/blog/atmospheric-entry/>. It extrudes the mesh's edges by their angle to
  the velocity, so flames peel off the silhouette, and projects the front faces forward as the air
  cushion. KSP's Firefly mod is built on it: <https://spacedock.info/mod/3813/Firefly>.
- KSP2 modding wiki, *Configuring the reentry effects*:
  <https://modding.kerbal.wiki/Configuring_the_reentry_effects>. Each part carries an envelope, a
  voxel-remeshed and smoothed copy slightly larger than the part, and the flame shader runs on it.
- Blender Artists, *Spacecraft re-entry effects*:
  <https://blenderartists.org/t/spacecraft-re-entry-effects/623771>. Streaked halo particles from an
  emitter shaped like the craft.
- Mantaflow fire on a moving meteor, KHAOS:
  <https://www.blendernation.com/2023/10/14/meteor-in-blender-3d-pro-blender-tutorial-ft-khaos-add-on/>.
  This is the volume-sim route.
- Reference photos (NASA, public domain):
  - STS-135 Atlantis re-entry seen from the ISS:
    <https://commons.wikimedia.org/wiki/File:STS-135_Space_Shuttle_Atlantis_reentry_seen_from_the_ISS.jpg>;
  - Progress M-10M plasma trail from the ISS:
    <https://commons.wikimedia.org/wiki/File:Progress_M-10M_reentry_plasma_trail.jpg>;
  - the plasma from the Shuttle's flight deck (STS-42):
    <https://commons.wikimedia.org/wiki/File:Space_Shuttle_reentry_aboard_flight_deck.jpg>.
- ESA, ATV-1 *Jules Verne* breakup, filmed from aircraft:
  <https://www.esa.int/ESA_Multimedia/Videos/2015/02/ATV-1_reentry>. The breakup reference for the
  wreckfall.

No published breakdown of a film's re-entry (*Gravity*, *Interstellar*) gave its method.

**The technique: an envelope plus edge streamers, built from the hull's own mesh**
(`swfx_entry.sheath`). It is the KSP2 envelope and Lindner's edge extrusion, done offline:

1. **Envelope.** The hull's world-space meshes are voxel-remeshed into one clean skin, which fills
   the greebles and concavities.
2. **Sheath.** A shell offset a little from the skin, and further on the windward side. Its emission
   and opacity follow each point's windward share, max(0, n · −airflow), so it is white on the
   belly and leading edges, orange at the flanks, and nothing on the lee side.
3. **Shock layer.** A fainter outer shell that stands off only the windward side, brightest at its
   rim, with a magenta edge.
4. **Edge streamers.** Seeded on the envelope where the surface turns edge-on to the airflow (the
   hull's silhouette as the air sees it). Each sweeps downstream, wanders, tears up with noise and
   converges toward the hull's lee into one wake. They cool to smoke on the wake ramp.
5. **Debris.** Each piece is a rough charred rock carrying the same windward sheath and a few
   streamers. Two chunks break up in the shot.

**Why this technique.** It is shaped by the hull, so the Colossus's modules and Morrow's outline
read in the glow. It is deterministic geometry built per frame, so a span renders frame by frame
in the effects layer like the rest of the kit. It costs surface shading, not a fluid sim, which
keeps it inside the light lane (half size, 16 samples). A Mantaflow volume would need a baked sim
per hull, per attitude, cached across the span, and would be noisy at 16 samples. It stays the
fallback for the far wake if the surfaces read thin.

## Scale

The game's ranges (Vulcan 250, light MG 300, Mark IX 900, Harpoon 2500, Pilum 4000) are in
game units at sprite scale. The scene stands ships kilometres apart (S5: 3–4 km). We keep the
**order**: PD and machine guns are short-range knife-fight weapons, autocannons medium,
missiles long. We don't keep the game's numbers. Projectile speeds scale up with the shot so a
tracer still crosses the frame in a readable beat.

## New for the kit

- the shield's flux brightening;
- overload arcs;
- the vent glow and vapour;
- the Displacer blink;
- the Ammo Feeder weapon glow and doubled cadence;
- canister flak;
- PD beams that track and kill missiles;
- curved missile flight paths (the kit's missile flies from launch speed and acceleration).

## The effects bench (before any full render)

Timing (owner, 2026-10-20): after the Jangala finals finish, probably 2026-10-21, so the
machines are free. The plan is light-lane tests: half size, 16 samples or fewer, close-ups on the real builds, one
shooter and one target, a short burst of frames each.

1. **Lasher light MG burst.** Normal cadence, then the Ammo Feeder doubling with the amber glow.
2. **Manticore volley.** The Mark IX four-round burst and a Harpoon pod salvo, with the wide arc and
   blue contrails converging.
3. **Shields.** A Lasher front shield (low tech, red) and a Kite omni (midline, blue): up, taking
   hits, flux brightening, venting, overload.
4. **Wolf.** The ion cannon's charge, bolt and EMP arcs, and the Displacer blink.
5. **Point defence.** A Swarmer salvo into Vulcans and a PD laser, some missiles killed short.

Each test ends in an owner decision: how the game's colours translate, tracer density, how big a
hit reads at a few kilometres, and whether a shield is always visible or only where struck. Once
those are settled, the key frames move from placeholders to the kit for round 9.
