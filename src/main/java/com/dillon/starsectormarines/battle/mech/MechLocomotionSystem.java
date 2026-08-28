package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

/**
 * Turns mech hips toward their locomotion bearing, or toward combat intent while
 * stationary. Active path steering remains authoritative so pivoting and
 * permission to translate stay an atomic decision; the upper chassis can look
 * farther ahead independently through {@link MechTurretSystem}.
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
            float[] posX = table.floats(components.POSITION, BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION, BattleComponents.POSITION_Y).array();
            Object[] paths = hasMovement
                    ? table.objects(components.MOVEMENT, BattleComponents.MOVEMENT_PATH).array() : null;
            int[] pathIdx = hasMovement
                    ? table.ints(components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX).array() : null;
            long[] targets = hasCombat
                    ? table.longs(components.COMBAT, BattleComponents.COMBAT_TARGET_ID).array() : null;

            for (int row = 0, n = table.rowCount(); row < n; row++) {
                long id = table.entityAt(row);
                // Floored locally — the turn-toward math below needs an integer
                // cell delta, not the continuous position. Identical to the old
                // cell-index values this phase (units only ever sit on centers).
                int rowCellX = (int) Math.floor(posX[row]);
                int rowCellY = (int) Math.floor(posY[row]);
                if (hasMovement && pathIdx[row] < Paths.cellCount((int[]) paths[row])) {
                    int nextX = Paths.cellX((int[]) paths[row], pathIdx[row]);
                    int nextY = Paths.cellY((int[]) paths[row], pathIdx[row]);
                    int dx = nextX - rowCellX;
                    int dy = nextY - rowCellY;
                    if (dx != 0 || dy != 0) {
                        MechLocomotion.turnToward(world, components, id,
                                MechLocomotion.desiredFacing(dx, dy), dt);
                    } else {
                        // Inside the next waypoint's cell (the carrot hasn't
                        // crossed its center yet): hold heading and walk it
                        // out. Falling through to target-turning here would
                        // swivel the chassis off the path bearing mid-segment
                        // and trip the mover's pivot gate at the next waypoint
                        // — a per-cell stutter-step.
                        MechLocomotion.stopTurning(world, components, id);
                    }
                    continue;
                }
                long target = hasCombat ? targets[row] : 0L;
                MechFacingIntent.Point intent = target != 0L && roster.isLive(target)
                        ? new MechFacingIntent.Point(
                        world.getFloat(target, components.POSITION,
                                BattleComponents.POSITION_X),
                        world.getFloat(target, components.POSITION,
                                BattleComponents.POSITION_Y))
                        : MechFacingIntent.rememberedContact(id, roster);
                if (intent == null) {
                    MechLocomotion.stopTurning(world, components, id);
                    continue;
                }
                int dx = (int) Math.floor(intent.x()) - rowCellX;
                int dy = (int) Math.floor(intent.y()) - rowCellY;
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
