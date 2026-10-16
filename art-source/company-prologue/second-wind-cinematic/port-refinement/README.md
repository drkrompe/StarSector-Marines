# Refined working port / shot 04

This editable motion study replaces the shed grid and broad empty verge with
repair halls, a sawtooth freight warehouse, tank farms, service compounds,
cargo handling and a control station. Connected service roads carry a freight
tug, maintenance carrier and utility van. An airborne Hound and Venture add a
recognizable ship scale hierarchy.

The scene derives from the retained
[earlier port study](../port-approach-realism/README.md). It preserves the cabin,
Second Wind's 171 meshes, camera and inverse scenery motion. Source frames
`400 + 0.5*i`, for `i=0..95`, run at source 12 fps and output 24 fps:
four seconds of actual movement. The forest and managed-land source shots
are unchanged.

## Review

- [Four-second port movie](review/jangala-port-refined.mp4)
- [Forest, farmland and refined port cut](review/jangala-approach-refined.mp4)
- [Midpoint](review/poster.jpg), [start](review/start.jpg),
  [end](review/end.jpg), [earlier-port comparison](review/contact-sheet.jpg)

The twelve-second cut contains editorial time ellipses between three four-second
excerpts. It is not a continuous twelve-second descent or the complete cinematic.
All outputs are silent 1920x1080 H.264/yuv420p; media checks record actual encoded
size, source hashes, frame counts and a full error-checked decode. Authoring
masters, scenes and movies remain outside shipped `mod/` content.

Exact movie sizes and playback observations are recorded in the media and motion
reports below. Full-resolution start, midpoint and end views accompany the
normal-speed review. The retained material fallbacks and broad apron treatment still have
the character of a cinematic study; the flight cards are not a landing solution.

## Buildings and vehicles

`industrial_district.py` owns the varied geometry and ground layout.
The original industrial atlas plus the
[secondary atlas](assets/secondary-materials-v1.png) provide physically scaled
slate, warm concrete, muted teal and tan surfaces. Tank UVs wrap around their
circumference and roofs retain actual sawtooth geometry.

The [architecture finish](assets/port-architecture-finish-v1.png) adds fine
construction detail to selected visible building faces through a fixed midpoint
projector attached to the scenery. Geometry still supplies parallax and
occlusion. Thin structures, hidden/backward faces and areas outside the source
canvas retain physical material fallbacks. Generated ground, cabin and ships
are not used by this projection. This is bounded single-view coverage, not a
complete building UV texture set; details beyond real silhouettes remain absent.

`port_traffic.py` moves three independent vehicles along the roads with wheel
rotation. Their source-space speeds are authored at 8, 7 and 6 m/s. Conservative
road/obstacle and contact checks sample all 96 output times.
[Visibility evidence](review/traffic-visibility.json) separately measures
camera visibility; geometry clearance does not imply screen visibility.

## Hound and Venture cards

The user-suggested [Hound](https://starsector.wiki.gg/wiki/Hound) and
[Venture](https://starsector.wiki.gg/wiki/Venture) use retained vanilla sprites,
collision outlines and engine locations from the read-only game install.
Copies in `references/` make this authoring study reproducible.
The scenario lengths are **authored** at 90 m and 220 m, not canonical dimensions.

Rough geometry establishes angle and footprint. The final visible ships
are separate fixed-orientation RGBA cards at different source-world positions,
moving independently through the port's airspace rather than swiveling toward
the camera every frame. Their cradles are removed and restrained engine/lift
glow distinguishes powered flight from a drifting parked vessel.
The generated Hound keeps a low industrial profile; the Venture has deeper
armor volumes, multiple deck levels and a raised bridge/engineering structure.
The accepted Second Wind V3 sheet supplied material quality only, not their shape.

The earlier grounded-card attempt failed visual review: a foot could be aligned
at the reference frame, but the painted support arrangement did not hold contact
through the moving camera. Its [grounded registration](artwork-registration.json)
and v1/v2 art remain as rejected studies, not an accepted grounding technique.
The original [guide contract](ship-card-layout.json) remains separate.

The final [air-traffic registration](airtraffic-registration.json) records
independent linear source-space movement and altitude. Venture covers
`[850,250,40]` metres and Hound `[1000,300,48]` over four seconds, at authored
speeds of 222 and 261 m/s. Both travel bow-first toward screen right and away,
with a gentle climb; their midpoint heights are 180 and 120 m.
Across the sampled span Venture moves 197 pixels right and shrinks to 76 percent
width; Hound moves 166 pixels right and shrinks to 69 percent. The fixed painted
aspect approximates an 8.0/6.7-degree viewpoint change, so it remains a bounded
shot technique. [Departure measurements](review/departure-motion.json) compare
the actual motion against a stationary-world control at all 96 output times.
Full quad clearance and screen coverage remain checked separately.
These are authored traffic paths, not canonical flight performance.

The first airborne pass was also rejected: its 50/85-metre X-only paths were
overtaken by 413 metres of camera travel, making the ships drift backward across
the window. Merely adding engine glow and independent keyframes did not read as
departure. The revised motion explicitly accounts for camera speed and depth.
These cards support this distant fixed-aspect passage, not ship rotation,
close inspection, relighting or a later landing-mechanics shot.

Raw native images and exact prompts are retained:
[top-down skins](generation.json),
[material atlas](secondary-generation.json),
[perspective cards](card-generation.json),
[architecture finish](architecture-generation.json).
The card masters are 2048x768; the architecture master is 1672x941 and the
secondary atlas is 1254x1254. No blanket 4K upscaling is used. Their application
at review resolution, not the master dimensions alone, determines usefulness.

## The recurring ground-edge defect

The prior pad top was at source Z=0.4 m, while its adjoining terrain edge lay
at 3.82-7.47 m. The visible strip was a real height discontinuity.
Removing terrain under the slab had fixed overlapping ownership but left
the outside join detached. This diagnosis is recorded in
[ground diagnosis](review/ground-diagnosis.json), with a
[controlled repair](review/ground-controlled-repair-midpoint.png) of the old
footprint before the district expansion.

`ground_surfaces.py` freezes the original terrain triangulation, cuts the
developed footprint, removes covered terrain, fits the shared boundary exactly
to the pavement and grades back to original elevation over 40 m.
One contiguous precinct top owns roads, aprons and drainage as material cells.
Road paint is in its material, with no lifted marking or grass overlay.
The expanded rectangle contains the earlier cutout; shrinking it requires
the original uncut terrain. This is a bounded rectangular utility, not a
universal terrain boolean.

`verify_ground.py` checks area, boundary height, outer grade return and 323
vertical ownership/contact probes.
[Regression evidence](review/ground-regressions.json) deliberately raises a
join vertex, adds a duplicate owner with an arbitrary name, removes a surface
cell and raises the outer grade. Each fault must fail its relevant check.
Finite probes cannot rule out every tiny hole or an unrelated untagged overlay,
so grazing-angle motion remains part of review. The reusable ownership/contact
distinction is also recorded in the cinematic skill's parallax guide.

## Rebuild and verify

Run from this directory in background Blender 5.2 with `--factory-startup`
and `--python-exit-code 1`:

1. `--python build_scene.py -- --preview` builds the final scene and renders
   three preview frames. `--geometry-ships --without-architecture-finish`
   exposes the earlier geometry/material stage for diagnostics.
2. `--python verify_scene.py` compares the actual saved scene with its retained
   source, checks assets, ground, traffic and card registration.
3. `--python verify_ground_regressions.py` runs temporary faults without
   saving a scene. `--python verify_traffic_visibility.py` records visibility;
   `--python verify_departure.py` measures ship travel, recession and a stationary
   counterfactual from the actual saved scene.
4. `--python render_motion.py` renders 96 half-frame evaluations at 64 samples.
5. With Python/Pillow, run `assemble_review.py --ffmpeg '<ffmpeg.exe>' --ffprobe
   '<ffprobe.exe>'` to encode both movies and validate the source render hashes.

The accepted artwork and registration are retained inputs: ordinary rebuilding
does not regenerate images or guides. To intentionally change a ship's rough
guide, first build the geometry stage, then run `generate_ship_cards.py`,
regenerate artwork and recalibrate its registration before returning to cards.

[Scene validation](review/scene-validation.json),
[media validation](review/media-validation.json),
[sequence validation](review/sequence-validation.json), and
[motion review](review/motion-review.json) distinguish measured geometry/media
facts from visual findings. This is a textured cinematic study, with no audio,
game integration or production-ready rotating hull claim.
