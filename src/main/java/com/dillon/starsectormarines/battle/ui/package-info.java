/**
 * Presentation — the battle HUD.
 *
 * <p>Category: presentation (reads sim state; owns no gameplay logic).
 * <br>Charter:  the HUD root + context ({@code BattleHud},
 *           {@code BattleUiContext}, {@code HudPanel}, {@code ScrollState})
 *           and the {@code panel/}, {@code picking/}, {@code highlight/},
 *           {@code compound/}, {@code debug/} subpackages.
 * <br>Boundary: presentation only — render and input routing, never
 *           gameplay state. The game hands UI hooks a polluted GL state
 *           (alpha colorMask off, scissor on, matrix-stack landmines), so
 *           any new GL draw path must use the state-bracket pattern rather
 *           than assuming clean defaults.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.ui;
