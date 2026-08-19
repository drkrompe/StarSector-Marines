# Modular top-down mech family

This set replaces directional live-mech frames with one true-overhead runtime
composition. The legacy sheet remains the load-failure fallback and continues
to provide corpse art.

Every normalized chassis uses the same 208-pixel canvas as its sizing unit.
`foot.png` is derived from the accepted Bulwark hull's bottom armor tab
(neither generated leg is used). `chaingun-arm.png`,
`srm-pod.png`, `lrm-pod.png`, and `heavy-cannon.png` each represent one reusable part. The shoulder
weapons use classic walking-tank / Patriot-style rectangular canister boxes:
only recessed missile caps are visible near the forward lip. The renderer
can carry matching equipment or one deliberately centered rack. Feet and arms
remain beneath the chassis layer. Shoulder-rack order is authored per hull:
Bulwark and Hound expose their racks above the armor, while Sirocco's paired
LRMs remain tucked under its broad hull. Rear/anchor regions are intentionally
buried so motion exposes only a toe tip and recoil exposes no arm joint.

Current hull-relative authored sizes are approximately 30% width for each
chaingun arm, 30% for the SRM pod, and 36% for the heavier LRM pod.

Bulwark uses the broad `chassis.png`, exposed SRM/LRM racks, and horizontally
compressed chainguns. Hound uses the generated narrow long-spine silhouette,
flipped so its pointed end faces forward, with one centerline nose chaingun and
one dorsal SRM-5. The two light chassis use exposed mid-body foot anchors so
their alternating locomotion remains visible around the thinner bodies. Sirocco uses
the generated broad wedge, likewise flipped,
with paired LRM-5 racks and a centerline heavy anti-armor cannon. Their retained
full-resolution ImageGen sources live in `sources/`, including
`sources/heavy-cannon.png`. `chassis-socketed-variant.png` and
`linear-cannon-variant.png` remain available for authored custom loads.

Run `python build_assets.py` after replacing a retained source. The generated
previews use the same hull-relative placement model as the runtime composer and
are written beneath `build/sprite-previews/mech/` rather than packaged with the mod.

Run `python render_variants.py` to regenerate the Bulwark/Hound/Sirocco contact
sheet at `roadmap/mechs/previews/layered-mech-variants.png`. Its upper row keeps
the variants' gameplay-relative render scales; its lower row normalizes all
three hulls to 208 pixels for direct sprite and hardpoint comparison.
