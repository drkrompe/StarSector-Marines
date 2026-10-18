# C1 v2 — CORVUS, the turn-around

Status: DRAFT — layout animatic in progress (`c1v2_scene.py`); sun decided.
Written: 2026-10-18

C1 v1 is a locked-off parallax plate: the whole system laid out at once, Second
Wind a card drifting into it. It works as a picture and fails as an opening,
because nothing tells the eye where to look first and the ship we care about is
one element among a dozen. v2 keeps v1's world — the planet, the station, the
flotilla, the lanes and the sun — and changes how we meet it.

## The idea

**Rags first, riches second.** The shot opens on the ship and only on the ship.
The camera faces back up the inbound lane, away from Jangala, which the player
has not seen yet. Second Wind comes toward us out of the dark: close, real
perspective, real velocity, every scar readable. It passes the camera, and the
camera turns to follow it, a 180-degree pan, which is what reveals the planet,
the station and the Hegemony fleet the ship is limping into.

The hail is already written for this. The controller describes the ship while
we are looking at it; Castle answers while the planet opens up behind it.

## Beats against the hail

Times are the shipped hail takes (`c1.json`); the shot stays about 38 s.

| t (s) | Audio | Picture | Camera |
|---|---|---|---|
| 0.0 – 3.1 | Cabin bed, then squelch | Black space and stars. Second Wind, small and bow-on, comes at us out of the dark, its deck catching the sun and its running lights on. Its engines face away; they are not seen until it has passed. | Locked, facing back up the lane. |
| 3.1 – 12.3 | **Hail 01** — "Cerberus on the inbound lane… a decommissioned mining escort. State your business." | The ship closes and fills the frame: dorsal rips, the crushed port engine, the dead turrets, sparks at the conduit breaks, RCS correcting. It slides past the camera's left at close range. | Slow tilt and pan to hold it as it passes; slight shake is **not** used (vacuum). |
| 12.6 – 23.6 | **Hail 02** — "This is Second Wind. Civilian. Crew and families aboard… Greywater Down, private cradle Kilo Nine." | The turn completes. Jangala swings into frame beyond the ship's stern; the station and lanes come with it. The ship's two engines now point at us as it pulls away toward the planet. | The 180-degree pan, eased, peaking near 15 s so **the planet lands on "Civilian. Crew and families"**. |
| 23.8 – 32.2 | **Hail 03** — customs: "AI cores… heavy armaments." | Scrutiny. A Hegemony Hound crosses close, between camera and ship; the Legion and escorts hold station mid-distance. | Small follow on the Hound, then back to the ship. |
| 32.5 – 37.7 | **Hail 04** — "Nothing to declare, Approach. Ore dust and tired people." | The ship small against the planet, one patched hull among working ships. Hold. | Settles; near-still at the cut to C2. |

## How it is built

v1's 2D plate compositing cannot turn a camera 180 degrees. v2 is a **single
Blender scene** with the camera at the centre:

- **Camera.** Mostly rotation (pan and tilt) with a small forward drift. Under
  pure rotation, anything far away keeps a correct perspective whatever it is,
  so distant objects can stay cheap.
- **Hero ship.** Second Wind in its `patched` state, appended from
  `second-wind-states/patched`, at 0.65 m per hull unit, with the VFX kit: the
  two running engines, RCS puffs on its anchors and the conduit sparks. Its
  path is a real trajectory with real speed past the camera.
- **Close traffic, 3D.** The modelled hulls at their own scales: Hound (A) in
  Hegemony auxiliary livery (textured), the Legion for the Hegemony picket
  (untextured, so mid-distance or flat-lit only), and Mudskipper as local
  working traffic (textured). Hermes is 9 m long and untextured, so at most a
  distant shuttle.
- **Far traffic, imposters.** v1's painted cards — flotilla, freighters, the
  station — as camera-facing cards placed at distance in the scene. A
  rotating camera does not betray them.
- **Planet.** The v1 sphere, maps and atmosphere, now in the scene rather than
  a separate layer.
- **Sky.** A full 360-degree star field and galactic band. v1's star plate is a
  single frame and cannot cover a 180-degree turn. Procedural in Blender, or
  an equirectangular ImageGen plate.
- **Delivery unchanged.** Frames to `build/cinematic/c1-corvus/frames/`,
  captions and the planet lock as runtime data, `cinematic_export.py --shot c1`.
  The lock appears once the planet is in frame.

## Ship orders

The Hegemony picket is 3D, not v1's painted flotilla cards: the Legion leads
Hound (A) escorts in formation, each hull at its own range and attitude, all
drifting on one heading. Hulls are appended once and instanced. On order from
the ship pipeline (2026-10-18), to take over slots in that formation:

1. **Onslaught**: the picket's capital and flagship. Use the detail pass 1
   build, Drive `starsector/builds/onslaught/85faadc8f/onslaught.blend`, or any
   later build. Earlier builds, 4c82f83aa, 6ac30ff4b and 11509aacf included, are
   superseded. It
   appends as collection `ONSLAUGHT | clean` with root
   `ONSLAUGHT | clean | root`, at 0.86 m per unit (about 301 m).
2. **Lasher**: frigate escorts, instanced several times.

Still painted imposters: the station and the distant freighter traffic.
Textures for the Legion are wanted if it is ever framed closer than the
picket's 2–3 km.

## Light

**Decided: the sun stands to the side of the lane, high, leaning slightly toward
the opening view** (`SUN_DIR` in `c1v2_scene.py`). The opening exists to make
the damage read, so the hero's dorsal deck and near flank are lit from the first
frame, and the pass is from above so the dorsal rips face the camera. Jangala is
about half lit: day side left, a hard terminator, city lights on the night side.

The rejected alternative kept v1's sun, which a 180-degree turn puts in front of
the opening camera: the ship would arrive out of the glare as a rim-lit
silhouette. More drama, but the first seconds would show no damage at all.

## Production order

1. **Layout animatic.** Grey shapes in EEVEE at final timing with the hail
   audio: camera path, ship trajectory, Hound crossing, imposter placement.
   This is where the owner signs off the move. Minutes per pass, not hours.
2. **Sky panorama** and imposter placement checked at the turn.
3. **Look pass.** Cycles on stills at the five beat moments.
4. **Final render**, then compose and export.

A final 905-frame Cycles render of a whole 3D scene is hours, not the 30
minutes v1's single hull took. The animatic exists so it is only run once.

## Tone

The arrival should feel **hopeful but fragile**: these people have escaped one
nightmare and arrive owning nothing, under the guns of a fleet that has not
decided about them yet. The picture carries the fragility (the damage, the
scale of the fleet, the scrutiny of the hail); the music should carry the hope,
quietly, and must leave room for the radio voices.
