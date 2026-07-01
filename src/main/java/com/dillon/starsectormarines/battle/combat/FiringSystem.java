package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.RepositionToCover;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Executes the per-tick fire intent behaviors queue on {@code COMBAT} — the
 * proving slice of the FiringSystem epic
 * ({@code roadmap/ecs-migration/stories/firing-system.md} § The decision),
 * wired for {@link com.dillon.starsectormarines.battle.infantry.EngagePosture}
 * only today; the remaining ~11 inline fire sites keep firing the old way
 * until the sweep phase flips them.
 *
 * <p><b>The intent contract.</b> A behavior that decides to shoot calls
 * {@link CombatService#setFireIntent} instead of firing inline —
 * {@code fireTargetId} (LONG, {@code 0L} = no intent = hold fire),
 * {@code fireStance} (the {@link FireStance} ordinal for the shot), and
 * {@code fireReposition} (run {@code RepositionToCover} after a successful
 * shot, same tick). Target <em>selection</em> and every posture-specific
 * pre-gate — leash windows, portal triggers, opportune picks, rocket-branch
 * suppression — stay in the behavior; this system applies only the uniform
 * gate every one of those fire sites duplicated inline: cooldown ready,
 * target in range, and line-of-sight. <b>Consume-once</b>: {@code
 * fireTargetId} is cleared every tick whether or not the shot actually
 * fired, so a stale intent (the behavior didn't run again this tick, or
 * wrote a hold) can never re-fire on a later tick.
 *
 * <p><b>The decrement is NOT here.</b> {@code InfantryUnitPrep.tickCooldowns}
 * stays the canonical once-per-unit cooldown decrement — it's coupled to the
 * mid-aim short-circuit (cooldowns freeze during the rocket-aim window) and
 * the secondary/reposition cooldowns, concerns this system has no business
 * owning. By the time this system runs, {@code cooldownTimer} already
 * reflects this tick's decrement (it ran earlier, during the behavior
 * dispatch that wrote the intent); this system only ever reads it and
 * resets it on a fire.
 *
 * <p><b>Table-walk safety.</b> {@code sim.fireShot} resolves damage inline
 * (same serial phase) and can kill the target — releasing it from the
 * roster and zeroing its {@code HEALTH} — but the death cascade only
 * <em>transmutes</em> the {@link EntityWorld} row (removing {@code COMBAT}
 * to the corpse archetype) on the buffered death-dispatcher drain, later in
 * the tick. So no row this walk is touching moves out from under it; the
 * {@link UnitRosterService#isAliveById}/{@link UnitRosterService#getOrNull}
 * guards below exist purely to skip a shooter or target that a
 * <em>different</em> row's shot already killed earlier in this same walk,
 * not to guard against structural churn.
 *
 * <p><b>Known ordering shift.</b> Execution moves from inline-during-the-
 * parallel-behavior-dispatch to this serial phase immediately after it
 * (still before {@code infantry.tick()}'s burst continuation, so the burst
 * continuation sees this tick's {@code beginBurst} state exactly as it did
 * when postures fired inline). Within-tick shot ordering across units
 * shifts as a result; cadence (shots per unit per second) is unchanged —
 * see the story's cadence golden test.
 */
public final class FiringSystem {

    private final NavigationGrid grid;
    private final UnitRosterService roster;

    public FiringSystem(NavigationGrid grid, UnitRosterService roster) {
        this.grid = grid;
        this.roster = roster;
    }

    /**
     * Consumes every live combatant's fire intent over {@link
     * BattleComponents#combatants}. {@code sim} is the mutation surface for
     * the shot itself ({@link BattleControl#fireShot}) and the post-fire
     * reposition hook ({@link RepositionToCover#tryReposition}); everything
     * else is read off the injected {@link UnitRosterService}.
     */
    public void tick(BattleControl sim) {
        EntityWorld world = roster.entityWorld();
        BattleComponents components = roster.components();
        CombatService combat = roster.combat();
        World w = roster.world();

        for (ArchetypeTable t : world.matched(components.combatants)) {
            long[] fireTarget = t.longs(components.COMBAT, BattleComponents.COMBAT_FIRE_TARGET_ID).array();
            float[] cooldownTimer = t.floats(components.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER).array();
            float[] attackRange = t.floats(components.COMBAT, BattleComponents.COMBAT_ATTACK_RANGE).array();
            int[] fireStance = t.ints(components.COMBAT, BattleComponents.COMBAT_FIRE_STANCE).array();
            int[] fireReposition = t.ints(components.COMBAT, BattleComponents.COMBAT_FIRE_REPOSITION).array();

            for (int r = 0, n = t.rowCount(); r < n; r++) {
                long ft = fireTarget[r];
                if (ft == 0L) continue; // no intent — hold fire

                // Consume-once: cleared whether or not this shot actually
                // fires, so a stale intent never carries into a later tick.
                fireTarget[r] = 0L;

                long shooterId = t.entityAt(r);
                if (!roster.isAliveById(shooterId)) continue; // killed earlier this walk
                Entity shooter = roster.getOrNull(shooterId);
                Entity target = roster.getOrNull(ft); // tolerant of death-in-flight
                if (shooter == null || target == null) continue;

                if (cooldownTimer[r] > 0f) continue;
                int sx = w.cellX(shooterId);
                int sy = w.cellY(shooterId);
                int tx = w.cellX(ft);
                int ty = w.cellY(ft);
                if (TacticalScoring.cellDistance(sx, sy, tx, ty) > attackRange[r]) continue;
                if (!grid.hasLineOfSight(sx, sy, tx, ty)) continue;

                FireStance stance = FireStance.VALUES[fireStance[r]];
                sim.fireShot(shooter, target, stance);
                combat.setCooldownTimer(shooterId, combat.attackCooldown(shooterId));
                shooter.beginBurst(combat, target);
                if (fireReposition[r] != 0) RepositionToCover.tryReposition(shooter, sim);
            }
        }
    }
}
