SECOND WIND - CURRENT GEOMETRY REFERENCE (V3 DESIGN REVISION)

This is the single accepted clean-hull geometry source. The old V2 model and
build scripts were removed; reference-history/v2/ retains only their reference
images. Historical prompt path mappings are in ../cleanup-record.json.
The surfaced damaged cinematic master is a separate derivative under
../second-wind-cinematic/second-wind-full-surface/.

Purpose
Restore heavier industrial hull volumes and layered construction while retaining
V2's corrected forward port pod and engine layout. This is an editable blockout, not
a production game mesh or an assertion of canonical Cerberus interior geometry.
All four views render the SAME mesh objects; only the active camera changes.

Coordinates and decisions
+X = bow; +Y = port; +Z = dorsal. Hull and bay deck datum are level.
The forward port appendage is proposed as a low horizontal service/airlock pod,
long axis parallel to X. Its center is (37,33,1), dimensions (19,8,6).
Its function and height are art interpretation; the game sprite does not supply
those facts. The pod is not a mast and there is no mirrored starboard pod.

Four aft-facing engine centers retain vanilla warhound.ship (hullId cerberus)
XY locations: (-41,31), (-39,24), (-39,-24), (-41,-31).
All four are at proposed Z=0. Outer pair is larger and slightly farther aft.
Vanilla engine-slot widths 12:8 supply the 3:2 relative size; solid nozzle radii
are 0.31 * slot width to leave space between collars. Effect widths are not
treated as literal solid nozzle diameters. No metre scale is claimed.
Four weapon mount XY locations also follow that vanilla hull specification.

V3 shell construction
Upper shell height has plateaus over the forebody and engine rooms, joined by
linear chamfer transitions to a lower waist. These use the explicit station
table in geometry.json and build_reference.py. Lower shell depth is increased
uniformly by 25 percent, preserving a level keel. Planform edges use broad flats
and short rounded corner transitions. The first smooth thickness experiment
looked organic and was rejected; no Gaussian or continuous spline swelling is
used in the retained model. Hull depth and turret support heights change; the
port pod, four engines and bay thresholds retain their V2 coordinates.

Conforming raised armor plates, reinforcement bands, a stepped service spine,
exposed protected conduits, vents, bay buttresses and flank maintenance housings
supply real structural depth for the surface interpretation. Detailed plating
and hardware in the ImageGen finish remain conceptual, not a consistent UV map.

Port bay is open with an empty cradle. Starboard bay is sealed. Their location,
depth and one-bay-per-flank arrangement are proposed art, not a procedural deck
layout change. The initial two Hermes remain wrecked. No boat is granted or
repaired, and no runtime art or gameplay is changed by this source reference.

Files
second-wind-geometry.blend: saved editable Blender 5.2 scene, cameras and objects.
build_reference.py: deterministic scene and camera construction; runs in Blender.
geometry.json: coordinates and provenance for the proposed layout.
port-quarter.png, top.png, port-side.png, stern.png: direct Blender guide renders.
compose_guide.py: Pillow composition of those untouched renders into a guide.
second-wind-geometry.reference.png: 1920x1080 guide sent to ImageGen.
../second-wind-design-v3.reference.png: generated surface interpretation for review.
../second-wind-design-v3.prompt.txt: exact ImageGen request and review notes.

Authority
The .blend and script define geometry. The generated surface interpretation
does not override geometry if it introduces small detail or projection drift.
Inspect the model views for placement and the generated sheet for finish.
The model is deliberately simplified; detailed pressure spaces, structural
engineering and a production-ready surface mesh are not represented.

Rebuild from this folder
blender --background --factory-startup --python build_reference.py
python compose_guide.py
The second command uses Pillow. Blender is not a runtime mod dependency.
Do not use scene generation against a user's unsaved live Blender session.

Shipping
Everything in this directory is source-only. Reference sheets use .reference.png,
not .master.png, and are excluded from the prologue packer's master inputs.
Keep the current campaign scenes until the user finishes reference review.
