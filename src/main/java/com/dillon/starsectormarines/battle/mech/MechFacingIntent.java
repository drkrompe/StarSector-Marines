package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.Paths;
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

    record Point(float x, float y) {}
}
