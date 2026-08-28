package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

/** Advances every live mech's deterministic, presentation-only gait state. */
public final class MechGaitSystem {

    private final EntityWorld world;
    private final BattleComponents components;
    private final Query mechs;

    public MechGaitSystem(EntityWorld world, BattleComponents components) {
        this.world = world;
        this.components = components;
        this.mechs = world.query(new ComponentType[]{components.MECH_GAIT_STATE,
                components.MECH_LOCOMOTION, components.POSITION, components.HEALTH}, null);
    }

    public void tick(float dt) {
        for (ArchetypeTable table : world.matched(mechs)) {
            Object[] gaitStates = table.objects(components.MECH_GAIT_STATE,
                    BattleComponents.MECH_GAIT_STATE_STATE).array();
            float[] posX = table.floats(components.POSITION,
                    BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION,
                    BattleComponents.POSITION_Y).array();
            float[] hipFacing = table.floats(components.MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES).array();
            float[] angularVelocity = table.floats(components.MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY).array();
            for (int row = 0, n = table.rowCount(); row < n; row++) {
                MechGaitState state = (MechGaitState) gaitStates[row];
                if (state != null) {
                    state.advance(posX[row], posY[row], hipFacing[row],
                            angularVelocity[row], dt);
                }
            }
        }
    }
}
