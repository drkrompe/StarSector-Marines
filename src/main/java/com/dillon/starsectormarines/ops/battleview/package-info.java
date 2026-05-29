/**
 * Game render behavior — the battle's world-draw layer.
 *
 * <p>Category: feature render (the concrete passes layered on the render2d engine).
 * <br>Charter:  per-frame render orchestration ({@code BattleRenderer}), the
 *           concrete producers ({@code RenderSystem} impls such as
 *           {@code DoodadRenderSystem}, and the inline passes still being
 *           migrated), the paint-order layer set ({@code RenderLayer}), the
 *           per-frame collector + carrier ({@code DrawList},
 *           {@code RenderContext}), the producer hook ({@code RenderSystem}),
 *           and the asset/sprite registry ({@code BattleSprites},
 *           {@code UnitSpriteCache}, {@code ShuttleSpriteCache}).
 * <br>Boundary: this is the "game" half of the render engine/game split. It decides
 *           <em>which</em> layers exist and <em>what order</em> they paint; the
 *           engine ({@code render2d}) decides <em>how</em> a layer's commands
 *           batch and flush. Systems append {@code DrawCommand}s tagged by
 *           {@code RenderLayer} and never touch GL directly; the engine drains
 *           layers in {@code RenderLayer} ordinal order, so occlusion is an
 *           enforced invariant, not a call-order convention. Depends on
 *           {@code render2d}; the reverse edge must never appear.
 *
 * <p>See {@code roadmap/battle-render/overview.md} for the render reorg.
 */
package com.dillon.starsectormarines.ops.battleview;
