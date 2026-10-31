package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.MovementService;
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
    private final MechMoveOrderService orders;

    public MechLocomotionSystem(EntityWorld world, BattleComponents components,
                                UnitRosterService roster) {
        this(world, components, roster, null);
    }

    /**
     * @param orders source of the hip heading a finished player move order left
     *               a standing mech holding; {@code null} when nothing orders mechs
     */
    public MechLocomotionSystem(EntityWorld world, BattleComponents components,
                                UnitRosterService roster, MechMoveOrderService orders) {
        this.world = world;
        this.components = components;
        this.roster = roster;
        this.orders = orders;
        this.mechs = world.query(new ComponentType[]{components.MECH_LOCOMOTION,
                components.POSITION, components.HEALTH}, null);
    }

    public void tick(float dt) {
        tick(dt, 0L);
    }

    /** The manual movement pass has already authored the controlled actor's hips. */
    public void tick(float dt, long controlledId) {
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
                if (id == controlledId) continue;
                // Floored locally — the turn-toward math below needs an integer
                // cell delta, not the continuous position. Identical to the old
                // cell-index values this phase (units only ever sit on centers).
                int rowCellX = (int) Math.floor(posX[row]);
                int rowCellY = (int) Math.floor(posY[row]);
                // A staggered mech's hips hold still: no walk, no pivot, until it has its feet back.
                if (roster.stagger().isStaggered(id)) {
                    MechLocomotion.stopTurning(world, components, id, dt);
                    continue;
                }
                // Crippled legs still walk the route, at a crawl (mechs-nouns.md, Section loss), so the
                // route owns the hips as it does on whole legs: the mover only translates a chassis
                // aligned with its next waypoint, and hips turned onto the fight instead would strand
                // a crippled mech on any route that does not lead toward its target, a withdrawal first.
                if (hasMovement && pathIdx[row] < Paths.cellCount((int[]) paths[row])) {
                    // Walking anywhere ends the heading a move order left the mech standing on.
                    if (orders != null) orders.releaseHeldFacing(id);
                    MovementService movement = roster.movement();
                    if (movement.continuousRoute(id) != null) {
                        int next = movement.nextWaypointIndex(id);
                        if (next < movement.waypointCount(id)) {
                            MechLocomotion.turnToward(world, components, id,
                                    MechLocomotion.continuousFacing(movement.waypointX(id, next) - posX[row],
                                            movement.waypointY(id, next) - posY[row]), dt);
                        } else {
                            MechLocomotion.stopTurning(world, components, id, dt);
                        }
                        continue;
                    }
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
                        MechLocomotion.stopTurning(world, components, id, dt);
                    }
                    continue;
                }
                // A dragged move order told the hips which way to stand. That outranks the target: the
                // torso twists to keep fighting while the legs and their armour point where they were put.
                float held = orders == null ? Float.NaN : orders.heldFacing(id);
                if (!Float.isNaN(held)) {
                    MechLocomotion.turnToward(world, components, id, held, dt);
                    continue;
                }
                long target = hasCombat ? targets[row] : 0L;
                MechFacingIntent.Point intent = target != 0L && roster.isAliveById(target)
                        ? new MechFacingIntent.Point(
                        roster.world().x(target), roster.world().y(target))
                        : MechFacingIntent.rememberedContact(id, roster);
                if (intent == null) {
                    MechLocomotion.stopTurning(world, components, id, dt);
                    continue;
                }
                int dx = (int) Math.floor(intent.x()) - rowCellX;
                int dy = (int) Math.floor(intent.y()) - rowCellY;
                if (dx == 0 && dy == 0) {
                    MechLocomotion.stopTurning(world, components, id, dt);
                } else {
                    MechLocomotion.turnToward(world, components, id,
                            MechLocomotion.desiredFacing(dx, dy), dt);
                }
            }
        }
    }
}
