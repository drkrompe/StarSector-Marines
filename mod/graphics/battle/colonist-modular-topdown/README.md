# Modular colonist actors

True-overhead civilian and engineer layers authored for the shoulder-width
runtime compositor. Each body is normalized to a 150 px shoulder canvas and
each independently rotating head to a 72 px canvas. The shared marine foot is
used beneath the body during locomotion; noncombatants never emit weapon or
muzzle-flash layers.

Runtime files:

- `civilian/body.png` and `civilian/head.png`
- `engineer/body.png` and `engineer/head.png`

The approved ImageGen chroma board is retained in `sources/` so later clothing,
helmet, and profession variants can be derived from the same visual language.
Processed alpha intermediates are disposable and are not packaged with the mod.
