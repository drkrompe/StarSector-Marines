# Shoulder laser cannon ImageGen concept

Generated with the built-in ImageGen tool on 2026-08-31. The retained
`shoulder-laser-cannon.png` is an art-first prototype for a direct-fire energy
weapon that occupies a shoulder missile-pod position. The retained concept is
the geometry authority for the registered runtime shoulder laser cannon.

The first draft established the accepted material language but was rejected as
geometry: its circular emitter read as firing upward out of the battlefield and
its long body read as an arm cannon. A compact revision removed the visible bore,
but its broad glowing top channel still read as a vertical emitter or reactor.
The final retained source uses an edge-on muzzle and long in-plane focusing
rails so the firing direction survives the top-down camera.

## Initial generation

> Use case: stylized-concept
>
> Asset type: production-source concept for a modular top-down game weapon sprite
>
> Primary request: create one new shoulder-mounted laser cannon module for a heavy combat mech. It replaces a rectangular missile-rack shoulder position but is unmistakably a direct-fire energy cannon with a strong forward axis.
>
> Subject: a compact precision laser cannon pod with a single recessed cyan emitter throat at the north/front, a reinforced focusing collar, dark exposed optical hardware, a dense capacitor block, restrained lateral heat-sink rails, and a broad rear mounting plate designed to disappear beneath a mech chassis. No missiles, rocket cells, ammunition tubes, hand grips, legs, vehicle body, or turret base.
>
> Style/medium: crisp high-resolution painted game sprite with slightly pixel-painted edge discipline, practical industrial science-fiction hardware, matching a detailed top-down tactical game asset. Advanced proprietary construction expressed through precise seams and compact optical hardware, while the external faction-paintable armor casing is neutral olive-drab for later recoloring. Gunmetal mechanisms, small amber service lamps, restrained cyan emitter glow.
>
> Composition/framing: strict 90-degree zenith orthographic view; north/up is the firing direction; exactly one isolated module; vertically centered with generous transparent padding; approximately as wide as a shoulder missile pod and only modestly longer, not a long arm cannon. Symmetrical enough to mirror onto either shoulder. The rear/bottom mounting third should be visually sturdy and partially concealable under chassis armor.
>
> Lighting/mood: neutral upper-left studio illumination baked into the painted sprite; crisp readable shadows only on the object itself.
>
> Materials/textures: olive painted composite casing with subtle wear and fine surface noise; dark gunmetal rails and mechanisms; cool titanium focusing collar; glassy cyan emitter aperture; small fasteners and vents.
>
> Constraints: genuinely transparent background and clean alpha; preserve a strong readable silhouette at small gameplay scale; exactly one object; no external cast shadow or halo; no scenery; no checkerboard; no text; no insignia; no logo; no watermark.
>
> Avoid: perspective or isometric view, side view, rotated object, multiple weapons, missile pod geometry, exposed rockets, huge bloom, fantasy ornament, overly smooth plastic, photoreal product-shot background, cropping, redesigning it as a rifle or vehicle.

## Forward-axis correction

The first edit retained the initial draft's material language while replacing
the upward-facing dish with a north-firing aperture and compacting the body.

> Use case: precise-object-edit
>
> Input images: Image 1 is the edit target and supplies the accepted material language, rendering quality, olive casing, gunmetal hardware, cyan optics, lighting, and general identity.
>
> Primary request: change the module's proportions and emitter orientation so it works as a compact shoulder-pod replacement. Shorten and compact the body to approximately 1.4 times as long as it is wide, comparable to a rectangular missile pod. Replace the large circular face-up emitter dish with a forward-firing laser muzzle at the extreme north/top edge: a narrow recessed cyan aperture and focusing throat visibly aimed north along the image plane, framed by short gunmetal focusing rails. The rear/bottom third remains a broad mounting plate intended to tuck under chassis armor.
>
> Composition/framing: strict 90-degree zenith orthographic view; north/up is the firing direction; exactly one vertically centered object with generous transparent padding; bilateral silhouette suitable for mirroring onto either shoulder.
>
> Constraints: preserve the existing crisp painted sprite style, olive paint, gunmetal mechanisms, subtle wear/noise, upper-left object lighting, capacitor detail, vents, fasteners, amber service lamps, and restrained cyan illumination. Use a genuinely transparent background with clean alpha. No cast shadow, glow halo, backdrop, checkerboard, scenery, text, logo, or watermark.
>
> Avoid: a face-up circular aperture, upward-firing dish, long rifle or arm-cannon proportions, missile cells, rockets, turret base, perspective, isometric angle, side view, multiple objects, cropping.

## Rear-mount trim

The forward-axis edit still carried an unnecessary lower tail. This final edit
made the X-braced plate the concealed rear mount and produced the retained
source. Its cleaned visible content is 1.74:1; normalization into a `76x112`
shoulder canvas would yield an approximately `64x112` module beside the current
`76x96` LRM pod.

> Use case: precise-object-edit
>
> Input images: Image 1 is the sole edit target.
>
> Primary request: shorten only the rear/bottom of the module. Remove the entire long tail assembly below the large olive X-braced armor plate, including the lower hinge, twin circular ports, vent bar, and final olive bumper. Make the existing X-braced plate the rear mounting cover and terminate the module immediately beneath it with one shallow, sturdy gunmetal mounting lip. The finished silhouette should be a compact shoulder pod approximately 1.5 times as long as it is wide.
>
> Constraints: preserve everything from the X-braced plate northward exactly in identity and visual design: forward north-firing cyan aperture, focusing rails, olive casing, dark mechanisms, capacitor display, heat sinks, conduits, vents, fasteners, lamps, paint wear, strict zenith orthographic view, north/up orientation, crisp sprite rendering, and object lighting. Keep exactly one centered module on a genuinely transparent background with generous padding. No external shadow, halo, text, logo, or watermark.
>
> Avoid: changing the emitter or upper body, adding new components, missile cells, rockets, perspective, isometric view, side view, multiple objects, scenery, checkerboard background, cropping.

## In-plane laser-cannon redesign

The compact pod was rejected because the broad cyan channel still behaved like
a glowing top surface. The retained redesign used the old source as its material
reference, the shipped linear cannon only as a perspective reference, and the
LRM pod only as a rear-footprint reference. The muzzle is deliberately reduced
to a thin cyan line at the north edge: the camera sees no bore because the bore
faces away along the battlefield plane.

> Use case: precise-object-edit
>
> Asset type: production-source concept for a modular top-down tactical-game weapon sprite
>
> Input images: Image 1 is the edit target and supplies the accepted olive armor, gunmetal mechanisms, cyan optics, amber lamps, fine wear/noise, and object lighting. Image 2 is perspective and visual-scale reference only: its barrels lie flat in the battlefield plane and point north; do not copy its six-barrel geometry. Image 3 is footprint and mounting reference only; do not copy missile cells or rockets.
>
> Primary request: substantially redesign Image 1 so it reads immediately as a single shoulder-mounted laser cannon, not a reactor, shield generator, engine, or vertical emitter. The forward 55–60% is an unmistakable cannon barrel: two parallel dark-gunmetal focusing spars running north-south and flanking a narrow recessed optical beam guide. The rear 40–45% is a compact armored capacitor housing and broad shoulder mounting plate that can tuck beneath a mech chassis. Keep faction-paintable armor neutral olive-drab.
>
> Critical perspective: strict 90-degree zenith orthographic top-down view. The cannon fires horizontally toward the north/top edge, parallel to the battlefield plane. The muzzle faces away from the camera, so no circular bore, aperture face, dish, lens, or glowing front surface is visible. At the extreme northmost tip show only a very thin, almost edge-on horizontal cyan exit slit between the focusing spars. Keep the cyan beam guide narrow, recessed, and mostly dark rather than a broad luminous window.
>
> Composition/framing: exactly one isolated vertically oriented module, centered with generous genuinely transparent padding. Compact shoulder-weapon proportions, roughly 1.6–1.8 times as long as wide, with a strong north-south firing axis. The rear mounting cover is broader than the forward barrel, but the silhouette remains clearly a cannon rather than a box.
>
> Constraints: preserve Image 1's crisp painted sprite style and material language; genuinely transparent background; clean alpha; no external cast shadow or glow halo; one object only; readable at small gameplay scale; no text, insignia, logo, watermark, scenery, or checkerboard.
>
> Avoid: any circular or oval muzzle visible to camera, face-up aperture, upward-pointing barrel, vertical emitter, glowing dish, broad cyan window, reactor-core silhouette, engine pod, missile cells, rockets, six-barrel cluster, rifle stock, hand grips, arm cannon, turret base, mech body, vehicle, perspective, three-quarter, isometric, side view, multiple objects, or cropping.

ImageGen returned a baked light checker in both accepted concept generations.
The retained source was normalized to real alpha with
`clean_imagegen_backgrounds.py`; no generated background enters a future runtime
layer.

# Rapid pulse-laser arm ImageGen concept

Generated with the built-in ImageGen tool on 2026-08-31. The retained
`pulse-laser-arm.png` is a compact direct replacement for the modular chaingun
arm. Image 1 (`chaingun-arm-v2.png`) supplied only the buried rear-mount
footprint, registration, and sprite density. Image 2
(`weapon-concepts/shoulder-laser-cannon.png`) supplied only the strict-zenith,
in-plane optical language and material treatment.

> Use case: stylized-concept
>
> Asset type: production-source concept for one modular top-down tactical-game mech arm weapon sprite
>
> Primary request: create one compact rapid pulse-laser arm module that directly replaces the chaingun arm position. Use two stout parallel dark-gunmetal focusing rails around a narrow recessed optical path, small paired capacitor banks, restrained cyan indicators, and a broad olive-drab armored rear housing designed to disappear beneath the chassis.
>
> Critical perspective: strict 90-degree zenith orthographic top-down view. The weapon fires horizontally toward the north/top edge, parallel to the battlefield plane. The muzzle faces away from the camera, so no circular bore, aperture face, lens, dish, or glowing front surface is visible. At the extreme northmost tip show only a very thin edge-on cyan exit slit between constant-width north-south focusing rails.
>
> Composition/framing: exactly one isolated vertical arm module on the chaingun's compact footprint, approximately 1.65–1.8 times as long as wide, centered with generous transparent padding and a south/rear mounting tab. Symmetrical enough to mirror to the opposite arm.
>
> Style/medium: crisp high-resolution painted game sprite matching the existing modular family: fine surface noise, panel seams, fasteners, restrained wear, upper-left baked object lighting, olive painted composite casing, dark gunmetal mechanisms, small amber lamps, and restrained cyan illumination.
>
> Constraints: genuinely transparent background; exactly one object; no external cast shadow or glow halo; no scenery, checkerboard, text, insignia, logo, or watermark.
>
> Avoid: visible circular muzzle, upward barrel, face-up aperture, broad cyan top window, reactor, rotary barrels, ammunition belt, missiles, shoulder pod, rifle, turret, mech body, perspective, isometric, side view, multiple objects, or cropping.

The first result satisfied the arm footprint and forward-axis read, so no
geometry iteration was required. Its baked checker was removed with
`clean_imagegen_backgrounds.py`; the cleaned source is the sole runtime geometry
authority.

# Faction-signature mech weapons

Generated with the built-in ImageGen tool on 2026-08-31. These three concepts
were accepted together because their silhouettes and mechanics express three
different equipment institutions rather than applying cosmetic faction motifs
to the same gun. Each generation used shipped mech parts only as style,
perspective, and hardpoint-scale references.

## Hegemony Bastion autocannon

> Create one isolated modular mech arm weapon concept for a 2D tactical game: a
> Hegemony service-issue burst autocannon intended to render as a mirrored
> left/right pair. Match the painterly, weathered, high-detail language and
> transparent-cutout presentation of the reference modular weapons, but design
> new geometry. True orthographic top-down view, camera directly above at 90
> degrees, zero perspective and zero foreshortening. The gun lies flat in the
> ground plane. Rear mounting pivot at the bottom; barrel points toward the top
> edge. The muzzle opening must not face the viewer: show only a thin edge-on
> dark slit at the topmost end. Use a long constant-width armored receiver,
> twin recoil rails, thick practical barrel shroud, exposed ammunition feed,
> standardized bolts, and replaceable olive-drab plates with restrained orange
> service markings. Heavy, maintainable institutional military engineering;
> no ornament, hands, or chassis. Strong small-sprite silhouette, centered with
> generous transparent padding; no shadow, floor, background, text, or border.

The output required no geometry revision. The checker was converted to real
alpha before registration as `hegemony-bastion-autocannon.png`.

## Luddic Path Foundry Breaker

> Create one isolated modular mech weapon concept for a 2D tactical game: a
> Luddic Path converted industrial demolition cannon, a single centerline
> arm/nose weapon for a light assault mech. Match the painterly, weathered,
> high-detail language and transparent-cutout presentation of the references,
> but use unmistakably improvised new geometry. True orthographic top-down view,
> camera directly above at 90 degrees, zero perspective and foreshortening. The
> cannon lies flat in the ground plane. Rear pivot at the bottom; barrel points
> to the top edge. The muzzle must not face the viewer: depict only a narrow
> edge-on black slit at the top. Use a brutally short oversized bore in a welded
> industrial jacket, asymmetrical recoil cylinder, repurposed mining braces,
> exposed hose and breech clamps, and scavenged olive, rust-red, and bare dark
> steel panels. It must read as a dangerous workshop conversion for one
> demolition shell, not a sleek cannon or rocket launcher. No religious symbol,
> fantasy ornament, hands, or chassis; transparent padded cutout with no shadow,
> floor, background, text, or border.

The accepted first generation became `pather-demolition-cannon.png` after
checker cleanup.

## Lion's Guard thermal lance

> Create one isolated modular mech shoulder weapon concept for a 2D tactical
> game: a Lion's Guard prestige thermal lance, an experimental direct-fire
> energy projector that replaces a missile rack. Match the painterly,
> weathered, high-detail language and transparent-cutout presentation of the
> reference weapons while creating distinct geometry. True orthographic
> top-down view, camera directly above at 90 degrees, zero perspective and
> foreshortening. The whole weapon lies flat in the ground plane. Rear shoulder
> pivot at the bottom; emitter points toward the top edge. The emitter must not
> point toward the viewer: show only a thin edge-on luminous slit at the top,
> never a circular lens. Use an armored rectangular projector body, oversized
> symmetrical cooling vanes, compact fuel-cell heat exchangers, dark gunmetal
> machinery, deep Sindrian red lacquer, brass-gold trim, and restrained cream
> identification marks. Prestigious, aggressive, heavily supported prototype
> hardware; a subtle hot-amber seam is acceptable, but no beam or VFX. No lion
> icon, text, hands, or chassis; padded transparent cutout with no cast shadow,
> floor, background, label, or border.

The concept returned with a dark amber halo. A cleanup edit preserved its
accepted identity while requesting fully transparent pixels outside the hard
weapon silhouette and removal of all black backdrop, halo, bloom, haze, and
cast light. ImageGen simplified a few subpixel details during that cleanup, so
the cleaned result—not the haloed draft—is the explicit retained geometry
authority for `lions-guard-thermal-lance.png`.
