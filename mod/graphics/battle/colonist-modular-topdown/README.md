# Modular colonist actors

True-overhead civilian, engineer, and scientist layers on the marines' own
silhouettes: body 150x120, independently rotating head 72x88 and foot 38x52, the
same canvases as the marine fatigues, so they register in the compositor exactly
as a marine does. Each profession has its own boot. Noncombatants never emit
weapon or muzzle-flash layers.

Runtime files: `<role>/body.png`, `<role>/head.png` and `<role>/foot.png` for
`civilian`, `engineer` and `scientist`, plus `<role>/downed-0..3.png`, the lying
bodies made under `art-source/downed/`.

The generation prompts, raw masters and export script live under
`art-source/colonist-modular-topdown/`.
