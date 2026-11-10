package com.dillon.starsectormarines.battle.breach;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.world.model.BuildingStanding;

/**
 * A squad works the raid's halls when it has nothing else to fight ({@code raid-demolish-the-works.md}, owner pick
 * {@code raid-demolition-effort} a): a structure target ranks behind every enemy in sight, so a squad turns its
 * wall-damage kit on a hall only while none of its members has a body to fire at. The hall is that of a machine not
 * yet wrecked; the cell is the hall's nearest outline cell still standing, within {@link #REACH} of the member. The
 * kit's own gates (range, line, ammunition, cooldown) then decide whether anything is fired ({@link WallBreachSystem}).
 */
final class WorksDemolition {

    private WorksDemolition() { }

    /** A member looks no further than this for a hall's wall to work, in cells. UNTUNED. */
    static final float REACH = 24f;

    /**
     * The wall cell a member at {@code (x, y)} should work on {@code raid}'s halls, packed as {@code x | y << 32}, or
     * -1 for none: none while {@code engaged} (any member of the squad has a body to fight), none when no standing
     * hall wall of an unwrecked machine is within {@link #REACH}.
     */
    static long cell(RaidObjective raid, BuildingStanding.Walls walls, float x, float y, boolean engaged) {
        if (engaged || raid.isComplete() || raid.isFailed()) return -1L;
        long best = -1L;
        float bestDistance = REACH * REACH;
        for (int site = 0; site < raid.sites().size(); site++) {
            if (raid.siteDone(site) || !raid.hasHall(site)) continue;
            for (int[] c : raid.hall(site).outline()) {
                if (!walls.standing(c[0], c[1])) continue;
                float dx = c[0] + 0.5f - x, dy = c[1] + 0.5f - y;
                float d = dx * dx + dy * dy;
                if (d > bestDistance) continue;
                bestDistance = d;
                best = (c[0] & 0xFFFFFFFFL) | ((long) c[1] << 32);
            }
        }
        return best;
    }
}
