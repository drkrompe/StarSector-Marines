/**
 * Behaviour scenes: small purpose-built battles played headless in seconds,
 * each asking one question about one behaviour and answering it with a
 * verdict rather than a picture.
 *
 * <p>The instrument has four parts. A {@code SceneBuilder} stands the world
 * up — arena, walls, squads with kit and orders — so a scene is forty lines
 * rather than three hundred. A {@code ScriptedPlayer} issues the orders a
 * player would, at the ticks a player would. An {@code OrderTrace} samples
 * what every squad was ordered, planning and doing on every tick, so "it
 * handed back within a tick" is a number. And a scene's {@link Verdict}s are
 * predicates over that trace, written to a report the {@code sceneEvidence}
 * task prints as a PASS/FAIL table.
 *
 * <p>Test infrastructure only: nothing here ships in the mod jar, and nothing
 * here is a unit test — a scene plays a battle, which is evidence, and lives
 * behind an opt-in task for the reason {@code CLAUDE.md} gives. A snapshot
 * suite may wrap any scene to record the same run as an animation, so the
 * picture and the verdict come from one play rather than two.
 */
package com.dillon.starsectormarines.battle.scene;
