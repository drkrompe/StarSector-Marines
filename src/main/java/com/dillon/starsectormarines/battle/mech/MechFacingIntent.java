package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vehicle.PurePursuit;

/** Shared, belief-honest look points used to make mech intent readable. */
final class MechFacingIntent {

    /** Visual route horizon: far enough to anticipate a nearby bend without staring at the final goal. */
    static final float PATH_LOOKAHEAD_CELLS = 3f;

    private MechFacingIntent() {}

    /** The squad's primary remembered hostile position, never the hidden unit's live position. */
    static Point rememberedContact(long mech, UnitRosterService roster) {
        if (!roster.squad().hasSquad(mech)) return null;
        Squad squad = roster.getSquad(roster.squad().squadId(mech));
        if (squad == null) return null;
        SquadContactPicture picture = squad.contactPicture;
        if (!picture.hasContacts() || picture.primaryContactId() == 0L
                || picture.primaryCellX() < 0 || picture.primaryCellY() < 0) {
            return null;
        }
        return new Point(picture.primaryCellX() + 0.5f,
                picture.primaryCellY() + 0.5f);
    }

    /** A short visual horizon along the current route, or {@code null} when no route remains. */
    static Point pathLookAhead(float x, float y, int[] path, int pathIdx) {
        if (path == null || pathIdx >= Paths.cellCount(path)) return null;
        PurePursuit.Carrot carrot = PurePursuit.pick(
                x, y, path, pathIdx, PATH_LOOKAHEAD_CELLS);
        return new Point(carrot.x, carrot.y);
    }

    /** Continuous route horizon used by the torso; does not steer or skip route corners. */
    static Point pathLookAhead(long mech, UnitRosterService roster) {
        MovementService movement = roster.movement();
        if (movement.continuousRoute(mech) == null) {
            return pathLookAhead(roster.world().x(mech), roster.world().y(mech),
                    movement.path(mech), movement.pathIdx(mech));
        }
        int count = movement.waypointCount(mech);
        int index = movement.nextWaypointIndex(mech);
        if (index >= count) return null;
        float x = roster.world().x(mech), y = roster.world().y(mech);
        float remaining = PATH_LOOKAHEAD_CELLS;
        for (int i = index; i < count; i++) {
            float nextX = movement.waypointX(mech, i), nextY = movement.waypointY(mech, i);
            float distance = (float) Math.hypot(nextX - x, nextY - y);
            if (distance >= remaining && distance > 0f) {
                return new Point(x + (nextX - x) / distance * remaining,
                        y + (nextY - y) / distance * remaining);
            }
            remaining -= distance;
            x = nextX; y = nextY;
        }
        return new Point(x, y);
    }

    record Point(float x, float y) {}
}
