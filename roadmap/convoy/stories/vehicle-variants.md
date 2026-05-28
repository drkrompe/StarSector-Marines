# Story: vehicle variants — supply truck + light scout

**Queued (content slice).** HEAVY_APC shipped as the sole `VehicleType`.
Both variants below reuse the same kinematic + path-following + wall
constraint code; the work is asset-side (sprites) + variant-specific
deboard logic. Each is a one-enum-constant addition plus its sprite sheet
(`VehicleType.spritePath` + `spriteFrame` + facing offsets parameterize
the rest; `TurretAuthorPanel` validates mounts).

## Supply truck

Instead of marines, drops crates/ammo at defender garrisons. Different
deboard logic — equipment drops, not units. Ties into compound-as-supply:
a supply run could top up an ARMORY's reinforcement tickets rather than
deliver bodies.

## Light scout vehicle

Faster, smaller footprint, no turret. Runs supplies or carries a 2-man
recon team. The smaller footprint may open the BSP-frame perim
"infiltration" entries that the APC's 5×3 footprint can't fit (see the
Parked list in [`../overview.md`](../overview.md)).

## Out of scope here

Tanks with hull-mounted turrets that fire while moving are a separate
big slice (new combat-side wiring), not a content variant — parked in
[`../overview.md`](../overview.md).
