# Story: vehicle variants — supply truck + light scout

Status: PLANNED — new payload roles need explicit supply and roster authority.

Written: 2026-05-28

Updated: 2026-08-23 — retained supply/scout roles without assuming they are asset-only variants.

Read `convoy-nouns.md` first. `HEAVY_APC` is the sole operational convoy type.
New variants reuse the route, motion, and lifecycle contracts, but each still
needs its own payload behavior and acceptance.

## Supply truck

Instead of marines, drops crates/ammo at defender garrisons. Different
deboard logic — equipment drops, not units. Ties into compound-as-supply:
a supply run could top up an ARMORY's reinforcement tickets rather than
deliver bodies.

## Light scout vehicle

Faster, smaller footprint, no turret. Runs supplies or carries a 2-man
recon team. The smaller footprint may open the BSP-frame perim
"infiltration" entries that the APC's 5×3 footprint cannot fit. This uses the
map-boundary extension described by `convoy-nouns.md`.

## Out of scope here

Tanks with hull-mounted turrets that fire while moving are a separate
big slice with new combat-side wiring, not a content variant. That boundary is
recorded in `convoy-nouns.md`.
