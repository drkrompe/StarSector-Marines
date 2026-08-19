# Standalone apartment buildings

## Goal

Reuse the residential-compound apartment planner on qualifying ordinary
residential lots. Street frontage replaces the compound courtyard signal, and
small lots retain the existing compact-home treatment.

## Player-visible acceptance

- A standalone residential lot at least 12x10 cells becomes a roomed apartment
  block with a lobby, clear two-cell common hall, two living rooms, two
  bedrooms, a street-facing entrance, and an opposed rear exit.
- Beds and sofas retain their two-cell footprints and wall-aware orientation.
- Private rooms that touch the facade receive readable firing windows.
  Windows block traversal, permit sight and projectiles in both directions,
  and provide directional wall cover to an adjacent firing position.
- Smaller residential lots remain compact homes.
- Representative generated cities contain standalone apartments without
  sacrificing whole-map connectivity or deterministic generation.

## Boundary

This slice does not fuse multiple BSP lots, replan road edges, create a new
residential compound type, or introduce breakable glass simulation. Windows
are structural firing apertures in destructible wall cells.
