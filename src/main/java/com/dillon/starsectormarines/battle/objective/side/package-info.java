/**
 * Side objectives: optional, mission-owned jobs a story offers, the player chooses to take, the battle answers and the
 * campaign remembers through a mark ({@code side-objective-toolkit.md}). A mission declares each job's hook, trigger,
 * window, effect and return; the battle's {@link com.dillon.starsectormarines.battle.objective.side.SideObjectiveService}
 * runs them, the shared mission picture shows them in its Optional section, and settlement writes their marks. Nothing
 * here touches victory or defeat. Rolled pools with credit rewards are the sibling package
 * {@code battle.objective.optional}.
 */
package com.dillon.starsectormarines.battle.objective.side;
