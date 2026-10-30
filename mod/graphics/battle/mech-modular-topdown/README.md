# Modular top-down mech family

This set replaces directional live-mech frames with one true-overhead runtime
composition. `heavy-mech-dead.png` beside this folder provides the corpse
art; there is no live sheet.

Every normalized chassis uses the same 208-pixel canvas as its sizing unit.
`foot.png` is derived from the accepted Bulwark hull's bottom armor tab
(neither generated leg is used). `chaingun-arm.png`,
`srm-rack.png`, `lrm-rack.png`, `hc-20.png`, and `thigh-bone.png` each
represent one reusable part. The shoulder
weapons use classic walking-tank / Patriot-style rectangular canister boxes:
only recessed missile caps are visible near the forward lip. The renderer
can carry matching equipment or one deliberately centered rack. Feet and arms
remain beneath the chassis layer. Shoulder-rack order is authored per hull:
Bulwark and Hound expose their racks above the armor, while Warden's paired
LRMs remain tucked under its broad hull. Rear/anchor regions are intentionally
buried so motion exposes only a toe tip and recoil exposes no arm joint.

Current hull-relative authored sizes are approximately 30% width for each
chaingun arm, 30% for the SRM pod, and 36% for the heavier LRM pod.

Bulwark uses the broad `chassis.png`, exposed SRM/LRM racks, and horizontally
compressed chainguns. Hound uses the generated narrow long-spine silhouette,
flipped so its pointed end faces forward, with one centerline nose chaingun and
one dorsal SRM-5. The two light chassis use exposed mid-body foot anchors so
their alternating locomotion remains visible around the thinner bodies. A
surface-mounted thigh linkage rotates and length-scales from each light mech's
waist toward its live foot position, ending just inside the pad. Warden uses
the generated broad wedge, likewise flipped,
with paired LRM-5 racks and a centerline heavy anti-armor cannon. Their retained
full-resolution ImageGen sources live in `sources/`, including
`sources/hc-20.png`. `chassis-socketed-variant.png` remains available for authored custom loads.
Cannon sprites are named for their parts (`<Family>-<N>`, mechs-nouns.md): `hc-20.png` and `hc-10.png` the HC cannons,
`ac-6.png` and `ac-6-qs.png` the autocannons, `dc-5.png` and `dc-4-qs.png` the demolition cannons. The
retained generated linkage source is `sources/thigh-bone.png`.

`factions/<family>/` contains appearance-only chassis skins for Hegemony,
Tri-Tachyon, Persean League, Luddic Church, Knights of Ludd, Luddic Path,
Sindrian Diktat, Lion's Guard, pirates, and the Independent/mercenary fallback.
Every family repeats `chassis.png`, `chassis-hound.png`,
`chassis-warden.png`, `chaingun-arm.png`, `hc-10.png`,
`hc-20.png`, `srm-rack.png`, `lrm-rack.png`, `shoulder-laser-cannon.png`,
`pulse-laser-arm.png`, `ac-6.png`,
`dc-5.png`, `lions-guard-thermal-lance.png`,
`ac-6-qs.png`, `dc-4-qs.png`, and
`srm-3-qs.png`. The builder applies each accepted base sprite's
exact alpha mask, so a skin cannot change silhouette,
hardpoint authority, scale, or mechanics. Weapon paint is transferred only
through thirteen reviewed casing masks; barrels, missiles, rack cavities, vents,
fasteners, lamps, and gait layers remain the shared base hardware.

Run `python art-source/mech-modular-topdown/build_assets.py` from the repository root after replacing a retained source. The generated
previews use the same hull-relative placement model as the runtime composer and
are written beneath `build/sprite-previews/mech/` rather than packaged with the mod.

Run `python art-source/mech-modular-topdown/render_variants.py` to regenerate the Bulwark/Hound/Warden contact
sheet at `roadmap/mechs/previews/layered-mech-variants.png`. Its upper row keeps
the variants' gameplay-relative render scales; its lower row normalizes all
three hulls to 208 pixels for direct sprite and hardpoint comparison.

Run `python art-source/mech-modular-topdown/render_faction_variants.py` to
regenerate the ten-family contact sheet at
`roadmap/mechs/previews/factional-mech-variants.png`.

Run `python art-source/mech-modular-topdown/render_faction_weapon_variants.py`
to review those chassis with the derived faction weapon casings at
`roadmap/mechs/previews/factional-mech-weapons.png`.

Run `python art-source/mech-modular-topdown/render_commercial_weapon_variants.py`
to review the three common-market components in every supported livery at
`roadmap/mechs/previews/commercial-mech-weapons.png`. These are fit checks, not
authored faction spawn loadouts.
