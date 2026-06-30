/**
 * Engine core — the 2D render mechanism.
 *
 * <p>Category: engine core (reusable rendering mechanism; no single feature owner).
 * <br>Charter:  GPU batch primitives ({@code QuadBatch}, {@code SolidQuadBatch},
 *           {@code RibbonBatch}), hostile-GL bracketing ({@code GlStateBracket}),
 *           the cell&#8596;screen camera ({@code BattleCamera}), the
 *           {@code DecalAccumulator} FBO,
 *           and the deferred draw-command tier — the command vocabulary
 *           ({@code DrawCommand}) and the drain that replays it into batched GL
 *           ({@code DrawListRenderer}). This is the "engine" half of the render
 *           engine/game split: it knows <em>how</em> to batch, flush, and project,
 *           never <em>what</em> a wall/fog/faction is.
 * <br>Boundary: no game-domain imports. The drain takes a flat command list plus a
 *           sheet&#8594;batch map and owns none of the layer set or paint order —
 *           the game ({@code ops.battleview}) owns that. The sole sanctioned
 *           external coupling is the rendering platform ({@code SpriteAPI} / LWJGL).
 *           Known impurity (deferred): {@code DecalAccumulator} still imports game-data
 *           types ({@code Decal}, {@code SpriteSheetFrames}); the FBO
 *           mechanism is reclassified clean once its game-data binding is separated.
 *
 * <p>See {@code roadmap/battle-render/overview.md} for the render reorg.
 */
package com.dillon.starsectormarines.render2d;
