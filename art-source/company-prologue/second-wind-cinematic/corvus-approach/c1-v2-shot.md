# C1 v2 — CORVUS, the turn-around

Status: DRAFT — layout animatic in progress (`c1v2_scene.py`); sun decided.
Written: 2026-10-18
Updated: 2026-10-19 — Second Wind final with its own effects; every ship final.

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
| 23.8 – 32.2 | **Hail 03** — customs: "AI cores… heavy armaments." | Scrutiny. A Hegemony Hound crosses close, between camera and ship; the picket comes on down the lane beside the station. | Small follow on the Hound, then back to the ship. |
| 32.5 – 37.7 | **Hail 04** — "Nothing to declare, Approach. Ore dust and tired people." | The ship small against the planet, one patched hull among working ships. Hold. | Settles; near-still at the cut to C2. |

## How it is built

v1's 2D plate compositing cannot turn a camera 180 degrees. v2 is a **single
Blender scene** with the camera at the centre:

- **Camera.** Mostly rotation (pan and tilt) with a small forward drift. Under
  pure rotation, anything far away keeps a correct perspective whatever it is,
  so distant objects can stay cheap.
- **Hero ship.** Second Wind in its `patched` state, appended from
  `second-wind-states/patched` (final export, Drive `builds/second-wind/80c78dfac/`),
  at 0.65 m per hull unit, with its own VFX kit effects: the two running
  engines, RCS puffs on five attitude corrections during the pass (`HERO_RCS`),
  the conduit sparks and the seal wisps; the exhaust haze and landing dust are
  smoke in air and stay out. Its path is a real trajectory with real speed past
  the camera.
- **Traffic, 3D.** Every ship is a modelled hull at its own scale (see Ship
  orders). Finished cinematic exports that carry the VFX kit's effects (drive
  plumes, gun rigs, lamps) are linked one copy each; hulls that appear several
  times are instanced and carry stand-in drive glows.
- **Far imposter.** Only the station is still a painted, camera-facing card. A
  rotating camera does not betray it.
- **Planet.** The v1 sphere, maps and atmosphere, now in the scene rather than
  a separate layer.
- **Sky.** A full 360-degree star field and galactic band. v1's star plate is a
  single frame and cannot cover a 180-degree turn. Procedural in Blender, or
  an equirectangular ImageGen plate. The procedural stars are judged by their
  angle from the view direction, never by distance from the sky sphere: the
  latter sorted them into rings straight down the lane (owner, 2026-10-18).
- **Delivery unchanged.** Frames to `build/cinematic/c1-corvus/frames/`,
  captions and the planet lock as runtime data, `cinematic_export.py --shot c1`.
  The lock appears once the planet is in frame.

## Ship orders

The Hegemony picket is 3D, not v1's painted flotilla cards. It is **out by the
station, 5.8–7.4 km off**, left of it in the final framing: it has come out to
meet the inbound lane and is heading back down it toward the player, bows on
but about 20 degrees off head-on, at 30 m/s. Over the shot it grows a little
and slides toward the lane. This sets up C2, where the Legion meets Second Wind
bow first, nearly head-on (owner, 2026-10-18: the earlier picket, 2–3 km off and
crossing broadside, read as comical). Every hull keeps its own range and a small
attitude of its own; hulls are appended once and instanced. In formation order:

1. **Onslaught**, the flagship, leads at 6.0 km: Drive
   `starsector/builds/onslaught/1480919b1/onslaught.blend` (textured, with its
   effects) or any later build; earlier builds are superseded. Collection
   `ONSLAUGHT | clean` plus `SWFX`, 0.86 m per unit (about 301 m).
2. **Lashers**, four frigates screening ahead of and around the flagship,
   5.8–6.2 km: Drive `starsector/builds/lasher/4e2ed50/lasher.blend`,
   collection `LASHER`, 0.95 m per unit (about 90 m).
3. **Legion**, second line, above and behind the flagship at 6.9 km: the
   finished `legion-cinematic` export with its effects (0.99 m per unit), or
   the untextured `legion-geometry` until that build is staged.
4. **Hound (A)** pair closing up the rear, 7.3–7.4 km.
5. **Broadswords**, the Legion's air wing (owner, 2026-10-18): four pairs flying
   loops of 450–950 m round the Legion and the flagship, each loop in its own
   tilted plane, banked into the turn and carried along with the formation.
   The repo's `broadsword-geometry` at its 0.5 m per unit (about 15.5 m). At
   picket range a fighter is a few pixels, so each engine carries a bright bead
   larger than the bell: the wing reads as sparks working round the fleet,
   which gives the capitals their scale.

The two capitals run their own effects: every drive at full throttle. Instanced
hulls carry a stand-in orange drive glow per engine instead, read from their
exhaust anchors and hung on the root. Running lights on the picket are capped
so they do not bloom past the hull at picket range. A further Hound (A) crosses
close in hail 03 (customs) under thrust: the `hound-export` asset with the
VFX kit's plumes on both drives. No hull carries the kit's exhaust haze, which
is smoke in air. The layout passes (EEVEE) render volumes only within 700 m,
which keeps the customs Hound's plumes sharp; the far plumes are a few pixels
and appear in the Cycles final.

Civilian freight is 3D too (`c1_fleet.py`): a Colossus outbound 5.2 km out
where the painted Atlas card was, and a queue inbound to the station's docks at
8-9 km (a Colossus, two Shepherds and a Mudskipper), each a few dozen pixels
long, with the picket's stand-in drive glows. The Mudskippers (the queue's and
the one crossing the lane at the reveal) become the finished
`mudskipper-cinematic` export once it is staged.

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
4. **Final render** (`c1v2_scene.py --final`), then compose and export.

A final 905-frame Cycles render of a whole 3D scene is hours, not the 30
minutes v1's single hull took. The animatic exists so it is only run once.
`--final` skips frames already on disk and holds the one in progress with a
placeholder, so it can be split across machines (`--frames a:b`) and resumed.
Sketch passes render at half size (`CINE_SCALE=50`), and a motion question
renders only its seconds (`--animatic --from s --to s --step n`).

## Tone

The arrival should feel **hopeful but fragile**: these people have escaped one
nightmare and arrive owning nothing, under the guns of a fleet that has not
decided about them yet. The picture carries the fragility (the damage, the
scale of the fleet, the scrutiny of the hail); the music should carry the hope,
quietly, and must leave room for the radio voices.
