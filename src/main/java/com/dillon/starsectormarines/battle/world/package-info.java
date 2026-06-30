/**
 * Feature domain (cross-actor) — the battlefield itself.
 *
 * <p>Category: feature domain (the map: data + generation + rendering).
 * The top level hosts only {@code MapEditor} — the runtime map-modification
 * coordinator (wall breach / roof crack / structure-to-rubble), which spans
 * data ({@code model/}) and navigation, so it belongs to neither alone.
 * Everything else lives in a subpackage.
 * <br>Charter + routing — when making a change, it goes in:
 * <ul>
 *   <li>top level — {@code MapEditor}: cross-domain runtime map mutation
 *       (and, later, generation orchestration). Sequences topology writes +
 *       navigation walkability/zone-graph writes + the roof-collapse FX sink.</li>
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
