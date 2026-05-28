/**
 * Feature domain (cross-actor) — the strategic layer above per-squad AI.
 *
 * <p>Category: feature domain (cross-actor; mission-level orchestration).
 * <br>Charter:  the mission commander ({@code CommanderService},
 *           {@code MissionCommand} + {@code Conquest/Assault/Sabotage}),
 *           objective assignment ({@code ObjectiveAssignment},
 *           {@code AssignmentKind}), battle resource pools
 *           ({@code BattleResources}, {@code ResourceType}), and the
 *           {@code objective/}, {@code reinforcement/}, {@code compound/}
 *           subsystems.
 * <br>Boundary: per-unit and per-squad <em>tactical</em> decisions live in
 *           {@code decision/} and {@code squad/}; {@code command/} owns
 *           strategic objective assignment and mission win/loss state.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.command;
