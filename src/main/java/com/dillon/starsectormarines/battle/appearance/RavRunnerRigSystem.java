package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

import java.util.function.LongUnaryOperator;

/**
 * Advances every live Runner's skeleton from the tick's settled state: where the body stands,
 * which way {@link FacingSystem} turned it, and the attack cooldown that times its strikes.
 * Presentation only, like {@code MechGaitSystem}; it runs after facing so the pose is solved on
 * this tick's heading. A Runner hauling a body ({@link #setLoads}) is posed facing it, and a dead
 * Runner keeps its rig and falls onto its flank, then lies where its corpse is.
 */
public final class RavRunnerRigSystem {

    private final EntityWorld world;
    private final BattleComponents components;
    private final Query runners;
    private final Query dead;
    private LongUnaryOperator loadOf = runner -> 0L;

    public RavRunnerRigSystem(EntityWorld world, BattleComponents components) {
        this.world = world;
        this.components = components;
        this.runners = world.query(new ComponentType[]{components.RAV_RIG, components.POSITION,
                components.LAYERED_ANIMATION, components.COMBAT, components.HEALTH}, null);
        this.dead = world.query(new ComponentType[]{components.RAV_RIG, components.POSITION,
                components.CORPSE}, null);
    }

    /** Who each Runner is dragging ({@code 0} for none), so its rig hauls the body. */
    public void setLoads(LongUnaryOperator loadOf) {
        this.loadOf = loadOf;
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
                RavRunnerRig rig = (RavRunnerRig) rigs[row];
                if (rig == null) continue;
                long load = loadOf.applyAsLong(table.entityAt(row));
                boolean carrying = load != 0L && world.has(load, components.POSITION);
                float loadX = carrying ? world.getFloat(load, components.POSITION, BattleComponents.POSITION_X) : 0f;
                float loadY = carrying ? world.getFloat(load, components.POSITION, BattleComponents.POSITION_Y) : 0f;
                rig.advance(posX[row], posY[row], facing[row], cooldown[row],
                        target[row] != 0L, carrying, loadX, loadY, dt);
            }
        }
        for (ArchetypeTable table : world.matched(dead)) {
            Object[] rigs = table.objects(components.RAV_RIG, BattleComponents.RAV_RIG_STATE).array();
            float[] posX = table.floats(components.POSITION, BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION, BattleComponents.POSITION_Y).array();
            for (int row = 0, n = table.rowCount(); row < n; row++) {
                RavRunnerRig rig = (RavRunnerRig) rigs[row];
                if (rig != null) rig.advanceDead(posX[row], posY[row], dt);
            }
        }
    }
}
