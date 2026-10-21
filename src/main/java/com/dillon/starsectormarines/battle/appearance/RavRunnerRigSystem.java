package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

import java.util.function.LongPredicate;
import java.util.function.LongToIntFunction;

/**
 * Advances every live Runner's and Brute's skeleton from the tick's settled state: where the body stands,
 * which way {@link FacingSystem} turned it, and the attack cooldown that times its strikes.
 * Presentation only, like {@code MechGaitSystem}; it runs after facing so the pose is solved on
 * this tick's heading. A Brute's rig also reads its charge phase and whether it is stunned, through
 * providers the simulation sets, so this package does not reach into the charge's.
 */
public final class RavRunnerRigSystem {

    private final EntityWorld world;
    private final BattleComponents components;
    private final Query runners;
    private LongToIntFunction chargePhase = id -> RavBruteRig.IDLE;
    private LongPredicate stunned = id -> false;

    public RavRunnerRigSystem(EntityWorld world, BattleComponents components) {
        this.world = world;
        this.components = components;
        this.runners = world.query(new ComponentType[]{components.RAV_RIG, components.POSITION,
                components.LAYERED_ANIMATION, components.COMBAT, components.HEALTH}, null);
    }

    /** Where a Brute's charge phase ({@link RavBruteRig#IDLE} ...) and its stun come from. */
    public void bruteState(LongToIntFunction chargePhase, LongPredicate stunned) {
        this.chargePhase = chargePhase;
        this.stunned = stunned;
    }

    public void tick(float dt) {
        for (ArchetypeTable table : world.matched(runners)) {
            Object[] rigs = table.objects(components.RAV_RIG, BattleComponents.RAV_RIG_STATE).array();
            float[] posX = table.floats(components.POSITION, BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION, BattleComponents.POSITION_Y).array();
            float[] facing = table.floats(components.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_FACING_DEGREES).array();
            float[] cooldown = table.floats(components.COMBAT,
                    BattleComponents.COMBAT_COOLDOWN_TIMER).array();
            long[] target = table.longs(components.COMBAT, BattleComponents.COMBAT_TARGET_ID).array();
            for (int row = 0, n = table.rowCount(); row < n; row++) {
                Object rig = rigs[row];
                if (rig instanceof RavRunnerRig runner) {
                    runner.advance(posX[row], posY[row], facing[row], cooldown[row],
                            target[row] != 0L, dt);
                } else if (rig instanceof RavBruteRig brute) {
                    long id = table.entityAt(row);
                    brute.advance(posX[row], posY[row], facing[row], cooldown[row],
                            chargePhase.applyAsInt(id), stunned.test(id), dt);
                }
            }
        }
    }
}
