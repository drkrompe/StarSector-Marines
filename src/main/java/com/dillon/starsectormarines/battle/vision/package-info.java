/**
 * Feature domain (cross-actor) — fog of war.
 *
 * <p>Category: feature domain (visibility system over all units).
 * <br>Charter:  per-cell shadowcast vision ({@code Shadowcast}), the
 *           ref-counted reveal/presentation authority ({@code FogOfWarService},
 *           {@code PlayerVisionState}), live sight inputs ({@code VisionService}),
 *           and building reveal
 *           ({@code BuildingVisibilityPass}).
 * <br>Boundary: gates <em>unit</em> visibility; shots are intentionally
 *           ungated (they read through fog). New reveal sources hook the
 *           service, not the shadowcast core.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} and
 * {@code fog-of-war-nouns.md}.
 */
package com.dillon.starsectormarines.battle.vision;
