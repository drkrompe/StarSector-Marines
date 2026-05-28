/**
 * Feature domain (cross-actor) — the battlefield itself.
 *
 * <p>Category: feature domain (the map: data + generation + rendering).
 * This package is a pure container; all code lives in its subpackages.
 * <br>Charter + routing — when making a change, it goes in:
 * <ul>
 *   <li>{@code model/} — map DATA structures (buildings, cell topology,
 *       wall masks, doodads, room purpose, themes, scale, time of day).</li>
 *   <li>{@code gen/} — map GENERATION (BSP partition, fill, roads, the
 *       urban generator).</li>
 *   <li>{@code tiles/} — tile/sprite RENDERING (tilesets, tile drawers,
 *       sprite sheets, the tile sink).</li>
 * </ul>
 * <br>Boundary: keep data, generation, and rendering in their lane —
 *           don't grow a generator inside {@code model/} or a data type
 *           inside {@code gen/}.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} and {@code roadmap/mapgen/}.
 */
package com.dillon.starsectormarines.battle.world;
