# Geometry-controlled dynamic objects

Use one accepted model for every guide and view of an identity-critical object.
Image generation supplies surface art and material direction; the model supplies
silhouette, volume, hardpoints, apertures and articulation. A rotating ship cannot
be built from independent generated perspectives and still be trusted to agree.

## Model, then reskin

1. Define object axes, component pivots, silhouette and thickness profile. Record
   dimensions as canonical or provisional. Inspect multiple views and a short
   motion span before expensive surfacing. Keep reference and derived scenes apart.
2. Render guides from that mesh with fixed camera settings and useful object/part
   masks. Include the actual moving components and their travel limits. Guide
   colors identify parts; they are not automatically final livery.
3. Generate guided material/state references with explicit fixed features. Judge
   them against the guide. If a useful result moves a bay or adds an engine,
   correct/reject that result instead of silently changing the accepted model.
4. Build registered textures on UVs or object-attached projection. For rigid
   components, keep projector coordinates fixed relative to that component;
   articulating parts need their own mapping. A stationary world/camera projector
   can make the finish slide over a moving hull. A camera-facing billboard is
   suitable only while its flatness and fixed aspect remain hidden.
5. Cover all visible faces for the intended move. For wider rotation, plan UV
   coverage or complementary object-local projections, with controlled blending
   and occlusion masks. Bake to a shared UV atlas where useful. One attractive
   angle does not supply the hidden side, underside or bay interior.
6. Review a turn/translation/contact test at the final lighting. Track distinctive
   scars and panel boundaries across frames. Check seams, occlusion, texture swim,
   apparent size and changing normals. Avoid baked highlights/shadows fighting
   dynamic scene light; use neutral material textures where relighting is needed,
   or limit finished lit projections to the camera/light range they support.

Small wear belongs in textures; thickness, deep openings, silhouette damage and
moving mechanisms that affect occlusion belong in geometry. Local crater damage
can deform sacrificial armor while retaining pressure backing. Major structural
changes require an updated model, not a painted illusion that fails on rotation.

This is the intended extension of the demonstrated geometry-first guides.
Registered multiangle hull textures, complete hidden-side coverage and a finished
moving textured Second Wind have **not yet been demonstrated**. The existing
four-view skins are concept sheets, not coherent UV atlases. Treat the first
object-attached moving reskin as a focused proof before extending it to every shot.

## Second Wind identity and state

| Source | Authority |
|---|---|
| [V3 geometry guide](../../../../art-source/company-prologue/second-wind-geometry-v3/README.txt), its `second-wind-geometry.blend`, `build_reference.py` and `geometry.json` | Accepted structure and component placement. Render new views from this mesh. |
| [Heavy arrival skin](../../../../art-source/company-prologue/second-wind-skins/arrival-heavy.reference.png) and [skin notes](../../../../art-source/company-prologue/second-wind-skins/README.txt) | Arrival damage/material direction after 8–10 years of small asteroid impacts and emergency recovery. |
| [Arrival animatic](../../../../art-source/company-prologue/second-wind-cinematic/animatic/README.md) and [layered approach](../../../../art-source/company-prologue/second-wind-cinematic/layered-next-scenes/README.md) | Derived viewport/landing hardware, cameras, support mechanics and provisional scale. Source maps and saved scenes own exact timing. |

Axes are +X bow, +Y port and +Z up. Preserve the horizontal port-side pod,
four aft engines with the larger outer pair, four turret mounts and one bay per
flank. Coordinate identity beats screen position: the port pod does not stay at
the top left of every image. Its blue guide color is not livery. Use the worn
cream, oxide orange and gunmetal material family from the accepted references.

Keep the heavy angular construction: stepped forebody/engine-room thickness,
lower waist, level keel, straight chamfers, reinforcing bands and service details.
Do not simplify it into a flat slab or smooth organic swelling. The V3 clean/medium
finish is a baseline, not evidence of what the initial paid conversion repairs.

Arrival shows a cratered, spalled and locally buckled outer hull with mismatched
salvage plates, backing, splints and welds. It has barely limped into port; essential
propulsion and occupied pressure boundaries still work. Do not invent a catastrophic
open occupied breach, extra engines or active fires to communicate damage.

The later berthing conversion is limited work, leaving substantial repairs for
Engineering. The existing `after-refit.reference.png` derives from the older,
lighter arrival study; it does not establish that every heavy crater was repaired.
Keep both Hermes in their story-appropriate wrecked state. Cinematic bay concepts
do not by themselves change the gameplay deck layout or commission shuttles.

## Physical review

Use human and shuttle proxies to judge scale; retain the animatic's documented
scale as provisional rather than presenting it as canonical Cerberus dimensions.
Check window placement, bay clearance, engine thrust direction, exhaust footprints
and whether crew access is sheltered appropriately. Level the hull on the berth.

For the current surface landing, forward travel stops before vertical lowering;
shoes meet support pads before clamps secure the ship, with lift reducing as the
supports take load. Inspect individual support and clamp contacts as well as the
wide shot. Avoid added camera drift that conceals contact or makes a settled hull
appear to slide. Mesh comparisons and conservative bounding-box corridor checks
are useful evidence, but do not claim they prove continuous collision, structural
engineering or exhaust simulation.
