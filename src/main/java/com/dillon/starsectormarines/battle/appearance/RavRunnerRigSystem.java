package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

import java.util.function.LongPredicate;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;

/**
 * Advances every live Runner's, Brute's, Spitter's and Matriarch's skeleton from the tick's settled state: where the body stands,
 * which way {@link FacingSystem} turned it, and the attack cooldown that times its strikes.
 * Presentation only, like {@code MechGaitSystem}; it runs after facing so the pose is solved on
 * this tick's heading. A Runner hauling a body ({@link #setLoads}) is posed facing it, and a dead
 * Runner keeps its rig and bursts into pieces and ichor around its corpse. A Brute's rig also
 * reads its charge phase and whether it is stunned, through providers the simulation sets, so this
 * package does not reach into the charge's. Each rig is told its body's growth-stage scale every
 * tick ({@link SwarmStage}), so it walks a whelp's shorter body at its own stride and a Matriarch
 * that matures in the field grows on screen.
 */
public final class RavRunnerRigSystem {

    private final EntityWorld world;
    private final BattleComponents components;
    private final Query runners;
    private final Query dead;
    private LongUnaryOperator loadOf = runner -> 0L;
    private LongToIntFunction chargePhase = id -> RavBruteRig.IDLE;
    private LongPredicate stunned = id -> false;
    private LongToIntFunction matriarchAction = id -> RavMatriarchRig.NONE;
    private LongToDoubleFunction matriarchAge = id -> 0.0;

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

    /** Where a Brute's charge phase ({@link RavBruteRig#IDLE} ...) and its stun come from. */
    public void bruteState(LongToIntFunction chargePhase, LongPredicate stunned) {
        this.chargePhase = chargePhase;
        this.stunned = stunned;
    }

    /** Where a Matriarch's action ({@link RavMatriarchRig#NONE} ...) and its age in seconds come from. */
    public void matriarchState(LongToIntFunction action, LongToDoubleFunction ageSeconds) {
        this.matriarchAction = action;
        this.matriarchAge = ageSeconds;
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
                Object state = rigs[row];
                if (state instanceof RavBruteRig brute) {
                    long id = table.entityAt(row);
                    brute.setStage(stage(id));
                    brute.advance(posX[row], posY[row], facing[row], cooldown[row],
                            chargePhase.applyAsInt(id), stunned.test(id), dt);
                    continue;
                }
                if (state instanceof RavSpitterRig spitter) {
                    spitter.setStage(stage(table.entityAt(row)));
                    spitter.advance(posX[row], posY[row], facing[row], cooldown[row], target[row] != 0L, dt);
                    continue;
                }
                if (state instanceof RavMatriarchRig matriarch) {
                    long id = table.entityAt(row);
                    matriarch.setStage(stage(id));
                    matriarch.advance(posX[row], posY[row], facing[row], matriarchAction.applyAsInt(id),
                            (float) matriarchAge.applyAsDouble(id), dt);
                    continue;
                }
                if (!(state instanceof RavRunnerRig rig)) continue;
                rig.setStage(stage(table.entityAt(row)));
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
                if (rigs[row] instanceof RavRunnerRig rig) rig.advanceDead(posX[row], posY[row], dt);
                else if (rigs[row] instanceof RavBruteRig brute) brute.advanceDead(posX[row], posY[row], dt);
                else if (rigs[row] instanceof RavSpitterRig spitter) spitter.advanceDead(posX[row], posY[row], dt);
            }
        }
    }

    private SwarmStage stage(long id) {
        if (!world.has(id, components.IDENTITY)) return SwarmStage.ADULT;
        return SwarmStage.of((SwarmStage) world.getObject(id, components.IDENTITY,
                BattleComponents.IDENTITY_SWARM_STAGE));
    }
}
