# Shoulder laser cannon ImageGen concept

Generated with the built-in ImageGen tool on 2026-08-31. The retained
`shoulder-laser-cannon.png` is an art-first prototype for a direct-fire energy
weapon that can occupy a shoulder missile-pod position. It has no runtime or
mechanical authority yet.

The first draft established the accepted material language but was rejected as
geometry: its circular emitter read as firing upward out of the battlefield and
its long body read as an arm cannon. The following prompt sequence produced the
retained compact, north-firing source.

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

ImageGen returned a baked light checker. The retained source was normalized to
real alpha with `clean_imagegen_backgrounds.py`; no generated background enters
a future runtime layer.

