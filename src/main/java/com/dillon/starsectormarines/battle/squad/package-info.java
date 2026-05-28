/**
 * Feature domain (cross-actor) — squad coordination.
 *
 * <p>Category: feature domain (a tier above per-unit GOAP).
 * <br>Charter:  the {@code Squad} grouping + {@code SquadPlan}, the
 *           coordination Systems ({@code SquadAlertSystem},
 *           {@code SquadFallbackSystem}, {@code SquadMoraleSystem},
 *           {@code SquadReplanSystem}), and {@code SquadAlertLevel}.
 * <br>Boundary: cross-unit coordination only; per-unit decisions live in
 *           the actor domains + {@code decision/}. Cohesion is
 *           leader-pull (not centroid-pull) so squads don't bifurcate
 *           around large obstacles; the leader is promotable on death.
 *           Squad-coordination <em>goals</em> currently compose under
 *           {@code infantry/}; move them here only when a second actor
 *           type composes them.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.squad;
