/**
 * Feature domain (cross-actor) — fog of war.
 *
 * <p>Category: feature domain (visibility system over all units).
 * <br>Charter:  per-cell shadowcast vision ({@code Shadowcast}), the
 *           ref-counted visibility state + service ({@code VisionService},
 *           {@code PlayerVisionState}), and building reveal
 *           ({@code BuildingVisibilityPass}).
 * <br>Boundary: gates <em>unit</em> visibility; shots are intentionally
 *           ungated (they read through fog). New reveal sources hook the
 *           service, not the shadowcast core.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} and
 * {@code roadmap/fog-of-war/README.md}.
 */
package com.dillon.starsectormarines.battle.vision;
