# Layered environments and stable motion

Use 2.5D plates where the shot sees mostly the same faces of distant scenery.
Keep near cabin framing, buildings, cranes, landing hardware and contact surfaces
as geometry when the camera reveals depth. Individual trees inside a single plate
have no separate parallax; add depth groups only where the motion needs them.

## Build layers for the whole move

1. Lock the evaluated camera and source timing. Render layout and depth guides
   from the actual camera, including intended overscan. Timeline camera markers
   can override `scene.camera`; clear or update their bindings for alternate
   guide cameras and record the settings that actually rendered.
2. Plan silhouettes and overlap: sky, hills, far forest, medium forest/river,
   near trees, independent clouds, independent traffic. Adapt the grouping to
   the shot rather than forcing seven layers everywhere. Derive visible coverage
   from the full projected move, including disocclusion behind near silhouettes.
3. Use ImageGen to extract/complete the relevant layers from the approved wide
   style master, keeping the guide's layout and margins. Preserve raw outputs
   and alpha. A midpoint still does not establish endpoint coverage; generate
   wider or overlapping art where the near layer travels beyond one canvas.
4. Place cards at deliberate relative depths, align common landmarks at a
   reference time and inspect throughout motion. Give scenery a shared motion
   parent, with independent cloud/traffic transforms below it. Do not bake a
   separately moving freighter into terrain.
5. Match exposure, depth haze, alpha edges and near/far contrast. Check actual
   features crossing the window, not just a convenient alignment at mid-shot.

For opposite-facing exterior cameras, derive the required backdrop direction
from their view. The port-window layers can be behind an exterior camera on the
port side looking toward the hull. Near silhouette ribbons need buried skirts,
tapered ends or other real coverage where the ground is exposed.

## Materials and projection

The accepted parallax study uses ordinary per-plane UVs, texture extension CLIP,
STRAIGHT image alpha, emission mixed with transparency, and Standard color
management. This reproduces painted lighting without relighting it a second time.
It is a baseline for finished plates, not a prescription for all 3D materials.

Center each transparent card's object origin near its actual geometry/depth.
BLENDED sorting with all origins at zero caused ghosted traffic despite correctly
placed vertices. Inspect layers together over both light and dark backgrounds.
Remove faint alpha residue conservatively in the material; preserve soft cloud
and foliage edges. The study's 0.015–0.98 remap is asset-specific tuning, not a
universal cutoff. Keep the original alpha unchanged.

Camera projection onto geometry needs per-pixel perspective division. Interpolated
vertex-projected UVs distorted large faces and faces crossing the projector plane.
Check positive projector depth and image bounds; edge extension can hide invalid
coordinates. Even mathematically valid projection can stretch a painted ridge
across coarse terrain. Use a panorama/layer instead where geometric depth adds
warping without useful motion. For moving objects, see
[object-attached texture projection](dynamic-objects.md).

For nearby ground, budget detail by its largest visible screen footprint. A single
orthographic map spanning several kilometres may establish layout but leave
nearby plots soft. Combine that broad map with denser registered surface textures
where needed; check parcel repetition and ground contact in motion. Do not retain
oversized blockout crop boxes as finished vegetation.

An extracted layer may preserve its outline but change the source's surface style.
Where registration and coverage permit, use the master image for RGB and the
generated cutout for alpha. This cannot invent hidden surfaces: if the master
contains a river or field where opaque woodland completion is needed, generate
that missing content or change the layer plan. Judge the composite through the
whole move rather than treating extraction as automatically faithful.

## Diagnose jitter before adding shake

In the first cabin study, world X near −12,000 produced about 3 pixels of
projection error. Keeping the ship near the origin and moving scenery by inverse
source displacement reduced the measured residual to about 0.023 pixels. There
was no authored re-entry/engine bump. Do not disguise precision errors as acting.

For a derived rebased scene, preserve camera-local animation, ship rotation/scale
and component relationships. Set ship-root translation to zero and bake the
appropriate inverse source translation onto scenery and world-space cameras.
Track whether the source helper includes a reference-origin offset; the two
retained studies differ. Use ordinary keyed transforms, and sample the whole
requested timeline. The older window helper's 73–216 range does not cover later
approach or landing shots.

Preserve elapsed time: a 12 fps source rendered at 24 fps needs genuine half-frame
evaluations, not duplicated frames or simply changing scene fps. Retain source to
output frame mappings, including editorial gaps and intentional holds. For sampled
linear motion, use linear keys so interpolation does not invent acceleration.

## Evidence and reusable implementations

Read the relevant implementation and manifest before adapting it:

- [Parallax window study](../../../../art-source/company-prologue/second-wind-cinematic/parallax-window/README.md): `build_parallax.py`, `stabilize.py`,
  `diagnose_motion.py`, `render_motion.py`, `verify_scene.py`, `validate_media.py`.
  Demonstrates layer motion, alpha sorting and foreground stabilization.
- [Next-scenes blockout](../../../../art-source/company-prologue/second-wind-cinematic/layered-next-scenes/README.md): `build_scene.py`,
  `environment_layers.py`, `measure_layers.py`, `export_layer_maps.py`,
  `verify_scene.py`, `validate_media.py`. Demonstrates full approach/contact
  blocking and coverage diagnostics; its color maps are antialiased review
  visualizations, not production binary extraction masks.
- [Managed-land realism study](../../../../art-source/company-prologue/second-wind-cinematic/managed-land-realism/README.md): orthographic ground detail,
  distant canopy layers, geometry-guided cabin materials and a four-second proof
  using the next-scenes camera. Includes retained rejected extraction variants.

Layer depths, cloud/traffic speeds, Atlas canvas width, overscan and haze in these
studies are compositing choices, not canonical geography, hull dimensions or flight
performance. Recompute them for the shot instead of copying literal values.

Check saved-scene camera transforms, inverse displacement, relevant geometry,
projected coverage and foreground residual motion. To compare depth-based motion,
use points on one reference-camera ray at several depths. Report that synthetic
diagnostic separately from real feature tracks, whose visibility may be occluded
or offscreen. Then inspect rendered motion for gaps, edge halos, repeated scenery,
floating objects, texture drift and convincing speed. Inspect the encoded video
for cadence and compression artifacts; distinguish intentional holds from missing
or duplicated moving frames.
