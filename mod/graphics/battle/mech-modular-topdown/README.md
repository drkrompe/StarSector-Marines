# Modular top-down mech family

This set replaces directional live-mech frames with one true-overhead runtime
composition. The legacy sheet remains the load-failure fallback and continues
to provide corpse art.

Every normalized chassis uses the same 208-pixel canvas as its sizing unit.
`foot.png` is derived from the accepted Bulwark hull's bottom armor tab
(neither generated leg is used). `chaingun-arm.png`,
`srm-pod.png`, and `lrm-pod.png` each represent one reusable part. The shoulder
weapons use classic walking-tank / Patriot-style rectangular canister boxes:
only recessed missile caps are visible near the forward lip. The renderer
draws matching equipment on both hardpoints. Feet, arms, and shoulder racks are
beneath the chassis layer. Rear/anchor regions are intentionally buried: motion
exposes only a toe tip, recoil exposes no arm joint, and racks extend the outer
silhouette instead of being pasted across the torso armor.

Current hull-relative authored sizes are approximately 30% width for each
chaingun arm, 30% for the SRM pod, and 36% for the heavier LRM pod.

Bulwark uses the broad `chassis.png`. Hound uses the compact wedge-shaped
`chassis-hound.png`; Sirocco uses the narrow long-spine
`chassis-sirocco.png`. Their retained full-resolution ImageGen sources live in
`sources/`. `chassis-socketed-variant.png` remains available for authored custom
loads. `srm-pod.png` is the compact rack shell, while `lrm-pod.png` is the
larger rack shell. `linear-cannon-variant.png` supplies Sirocco's direct-fire
backup arms.

Run `python build_assets.py` after replacing a retained source. The generated
previews use the same hull-relative placement model as the runtime composer and
are written beneath `build/sprite-previews/mech/` rather than packaged with the mod.

Run `python render_variants.py` to regenerate the Bulwark/Hound/Sirocco contact
sheet at `roadmap/mechs/previews/layered-mech-variants.png`. Its upper row keeps
the variants' gameplay-relative render scales; its lower row normalizes all
three hulls to 208 pixels for direct sprite and hardpoint comparison.
