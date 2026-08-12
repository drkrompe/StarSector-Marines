package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

/**
 * Turns idle mech chassis toward their combat target. Active path steering is
 * handled by MovementService so pivoting and permission to translate remain an
 * atomic decision; this pass owns the no-path case.
 */
public final class MechLocomotionSystem {

    private final EntityWorld world;
    private final BattleComponents components;
    private final UnitRosterService roster;
    private final Query mechs;

    public MechLocomotionSystem(EntityWorld world, BattleComponents components,
                                UnitRosterService roster) {
        this.world = world;
        this.components = components;
        this.roster = roster;
        this.mechs = world.query(new ComponentType[]{components.MECH_LOCOMOTION,
                components.POSITION, components.HEALTH}, null);
    }

    public void tick(float dt) {
        for (ArchetypeTable table : world.matched(mechs)) {
            boolean hasMovement = table.has(components.MOVEMENT);
            boolean hasCombat = table.has(components.COMBAT);
            int[] cellX = table.ints(components.POSITION, BattleComponents.POSITION_CELL_X).array();
            int[] cellY = table.ints(components.POSITION, BattleComponents.POSITION_CELL_Y).array();
            Object[] paths = hasMovement
                    ? table.objects(components.MOVEMENT, BattleComponents.MOVEMENT_PATH).array() : null;
            int[] pathIdx = hasMovement
                    ? table.ints(components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX).array() : null;
            long[] targets = hasCombat
                    ? table.longs(components.COMBAT, BattleComponents.COMBAT_TARGET_ID).array() : null;

            for (int row = 0, n = table.rowCount(); row < n; row++) {
                long id = table.entityAt(row);
                if (hasMovement && pathIdx[row] < Paths.cellCount((int[]) paths[row])) {
                    int nextX = Paths.cellX((int[]) paths[row], pathIdx[row]);
                    int nextY = Paths.cellY((int[]) paths[row], pathIdx[row]);
                    int dx = nextX - cellX[row];
                    int dy = nextY - cellY[row];
                    if (dx != 0 || dy != 0) {
                        MechLocomotion.turnToward(world, components, id,
                                MechLocomotion.desiredFacing(dx, dy), dt);
                        continue;
                    }
                }
                long target = hasCombat ? targets[row] : 0L;
                if (target == 0L || !roster.isLive(target)) {
                    MechLocomotion.stopTurning(world, components, id);
                    continue;
                }
                int dx = world.getInt(target, components.POSITION,
                        BattleComponents.POSITION_CELL_X) - cellX[row];
                int dy = world.getInt(target, components.POSITION,
                        BattleComponents.POSITION_CELL_Y) - cellY[row];
                if (dx == 0 && dy == 0) {
                    MechLocomotion.stopTurning(world, components, id);
                } else {
                    MechLocomotion.turnToward(world, components, id,
                            MechLocomotion.desiredFacing(dx, dy), dt);
                }
            }
        }
    }
}
