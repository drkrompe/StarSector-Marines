package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strike drone's blast as a heavy shell delivered by hand: it kills any rifleman in a standard pattern it reaches,
 * and it reaches a man beside its target only when the picket stands a cell apart. The `drone-strike-spacing` scene
 * measures the same law in a played fight.
 */
class StrikeBlastTest {

    /** How far from a marine's centre the blast still catches him: the splash test adds the body's own radius. */
    private static final float REACH = DroneBaySystem.STRIKE_BLAST_RADIUS + UnitType.MARINE.radius;

    @Test
    void killsEveryStandardPatternAndNotAPowerSuit() {
        // Unarmoured, starter (cordon shell), line, and the heaviest standard pattern (redoubt).
        assertTrue(kills(0f, 0f));
        assertTrue(kills(3f, 2f));
        assertTrue(kills(10f, 8f));
        assertTrue(kills(22f, 13f));
        // A power suit's plate soaks a whole strike.
        assertFalse(kills(60f, 12f));
    }

    @Test
    void catchesANeighbourOneCellAsideButNotTwo() {
        // The drone fuses anywhere up to STRIKE_FUSE_CELLS short of its target on its line of approach; a picket
        // standing across that line puts the neighbour square to the side of it.
        for (float fuse = 0f; fuse <= DroneBaySystem.STRIKE_FUSE_CELLS; fuse += .25f) {
            assertTrue(Math.hypot(fuse, 1f) <= REACH, "one cell aside, fused " + fuse + " short");
            assertFalse(Math.hypot(fuse, 2f) <= REACH, "two cells aside, fused " + fuse + " short");
        }
    }

    @Test
    void neverReachesASecondManThreeCellsAway() {
        // Worst case: the drone comes in along the file and fuses a full fuse short, toward the next man.
        assertFalse(3f - DroneBaySystem.STRIKE_FUSE_CELLS <= REACH);
    }

    private static boolean kills(float armor, float rating) {
        DurabilityModel.Resolution out = new DurabilityModel.Resolution();
        DurabilityModel.resolveInto(DroneBaySystem.STRIKE_DAMAGE, DroneBaySystem.STRIKE_PENETRATION, 0f,
                armor, rating, UnitType.MARINE.maxHp, out);
        return out.structureDamage() >= UnitType.MARINE.maxHp;
    }
}
