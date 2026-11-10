# Derelict walker guns: turret heads

The derelict walkers draw each mount's weapon from its own drawing under a turret head in the walker's plate
(`DerelictWalkerGuns`; owner: turret heads so any weapon renders underneath without art per weapon). Shipped heads:
`mod/graphics/battle/derelict-walker/turret-head-port.png` (gun port cowl, barrel slot at the top) and
`turret-head-rack.png` (rack cradle), each centred on its mount's pivot.

- `turret-heads-guide.png`: the two silhouettes (port cowl centred at 420,512, cradle at 1110,512), geometry authority.
- `turret-heads-v1.png` (+ brief, provenance): the codex-imagegen master the shipped heads are cut from. Each head is
  cropped at alpha 128, padded so the image centre is its guide centre, and scaled to 256 px wide.
- `concept-v2.png` (+ brief, provenance, `guide.png`): earlier per-weapon derelict gun concept, not picked; kept as the
  material reference the heads were generated against.
