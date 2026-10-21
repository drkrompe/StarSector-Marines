# Downed bodies

Top-down lying bodies for layered infantry, one sheet of four poses per outfit:
0 face down, 1 on the back with arms out, 2 curled on one side, 3 sprawled with an
arm overhead. They replace the old three-quarter corpse sheets for every outfit that
ships them (`LayeredArmorFamily.hasDownedArt`): every infantry outfit does, and only the
alien keeps its unit type's sheet.

`downed.py guide` builds the pose guide (`../templates/downed-poses.png`) from
anatomy in shoulder widths, so every outfit shares the same four bodies at the live
compositor's scale (150 source px per shoulder width; frames 360x520: the 300x460 guide figure plus a 30 px margin so drift is not clipped, head toward the
top). `downed.py export [outfit ...]` cuts each raw back into
`downed-<pose>.png` beside the outfit's body and head with nearest sampling, keeping
the generated alpha near the guide figure. The reskin may move a limb a little;
export keeps it rather than clipping it to the guide.

Charcoal was generated first against the live charcoal sources; every other outfit
used the accepted charcoal sheet as its style reference and its own live layers
(marine and faction-armor sources, the ship-crew and colonist raws) as identity. The
articulated shells (Specter, its olive Arbalest paint, the Boarding Jack and its pirate
paint) have no single source showing the whole suit, so `identity/` holds each one's
shipped torso, shoulders, helmet and feet assembled at 4x; their lying body is the
whole suit, shoulder shells included. Prompts are under `prompts/`
(`<outfit>.full.txt` is the exact text sent), raws and provenance under `raw/`.

Blood pools: `downed.py pool-guide` draws four seeded pool shapes
(`../templates/blood-pools.png`), one generation reskins them
(`prompts/blood-pools.txt`, `raw/blood-pools.png`), and `downed.py pool-export` writes
`mod/graphics/battle/downed/blood-pool-<n>.png` at 240x240. Pools are shared by every
outfit.

Adding an outfit: write `prompts/<outfit>.txt`, generate, add it to `OUTFITS` in
`downed.py`, export, then add the family to `LayeredArmorFamily.hasDownedArt`.
