package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import static com.dillon.starsectormarines.battle.combat.FireGroups.Mount.CREWED;
import static com.dillon.starsectormarines.battle.combat.FireGroups.Mount.GUN;
import static com.dillon.starsectormarines.battle.combat.FireGroups.Mount.ORDNANCE;
import static com.dillon.starsectormarines.battle.combat.FireGroups.Mount.SECOND_TURRET;
import static org.junit.jupiter.api.Assertions.*;

/** How any carrier's mounts sort onto the two buttons, and how each button cycles its group. */
class FireGroupsTest {
    private static final int LINKED = FireGroups.LINKED;

    private static FireGroups.Mount[] of(FireGroups.Mount... mounts) {
        return mounts;
    }

    private static final FireGroups.Mount SECOND_RACK = new FireGroups.Mount(true, true, false);

    @Test
    void gunsGoLeftAndOrdnanceGoesRight() {
        var mounts = of(GUN, ORDNANCE, GUN, ORDNANCE);
        assertEquals(0b0101, FireGroups.primaryMask(mounts));
        assertEquals(0b1010, FireGroups.secondaryMask(mounts));
    }

    @Test
    void aSecondTurretIsOnTheRightButtonWhateverItThrows() {
        assertEquals(0b01, FireGroups.primaryMask(of(GUN, SECOND_TURRET)), "a hull's two guns are not merged");
        assertEquals(0b10, FireGroups.secondaryMask(of(GUN, SECOND_TURRET)));
        assertEquals(0b01, FireGroups.primaryMask(of(ORDNANCE, SECOND_TURRET)),
                "a launcher hull's rack is its primary; its second gun stays on the right");
        assertEquals(0b10, FireGroups.secondaryMask(of(ORDNANCE, SECOND_TURRET)));
        assertEquals(0b110, FireGroups.secondaryMask(of(GUN, SECOND_TURRET, SECOND_RACK)));
    }

    @Test
    void aCarrierWithOnlyOrdnanceFiresItFromTheLeftButton() {
        var racks = of(ORDNANCE, ORDNANCE);
        assertEquals(0b11, FireGroups.primaryMask(racks));
        assertEquals(0, FireGroups.secondaryMask(racks));
        assertEquals(0b10, FireGroups.primaryMask(of(null, SECOND_TURRET)),
                "a hull whose main gun is gone fires its second gun from the left");
    }

    @Test
    void aCrewedDefenceAndAnAbsentMountAreNeverListed() {
        var mounts = of(GUN, CREWED, null, SECOND_TURRET);
        assertEquals(0b0001, FireGroups.primaryMask(mounts));
        assertEquals(0b1000, FireGroups.secondaryMask(mounts));
        assertEquals("", FireGroups.group(mounts, 1));
        assertEquals("", FireGroups.badge(mounts, TriggerPicks.LINKED, 1), "a crewed mount keeps its own gunner");
        assertArrayEquals(new int[0], FireGroups.members(of(CREWED), FireTrigger.LEFT));
    }

    @Test
    void eachButtonCyclesLinkedThenEachMountAndWraps() {
        var mounts = of(GUN, GUN, SECOND_TURRET, SECOND_RACK);
        assertArrayEquals(new int[]{LINKED, 1, 2}, FireGroups.members(mounts, FireTrigger.LEFT));
        assertArrayEquals(new int[]{LINKED, 3, 4}, FireGroups.members(mounts, FireTrigger.RIGHT));
        int pick = LINKED;
        for (int expected : new int[]{3, 4, LINKED}) {
            pick = FireGroups.next(mounts, FireTrigger.RIGHT, pick);
            assertEquals(expected, pick);
        }
        assertEquals(2, FireGroups.member(mounts, FireTrigger.LEFT, 2), "key 3 is the left button's second gun");
        assertEquals(-1, FireGroups.member(mounts, FireTrigger.LEFT, 3));
        assertEquals(LINKED, FireGroups.next(mounts, FireTrigger.LEFT, 3), "a pick off the list falls back to LINKED");
        assertArrayEquals(new int[]{LINKED}, FireGroups.members(of(GUN, SECOND_TURRET), FireTrigger.RIGHT),
                "a group of one is only LINKED");
    }

    @Test
    void anUnpickedMemberHoldsFire() {
        var mounts = of(GUN, GUN, SECOND_TURRET, SECOND_RACK);
        assertEquals(0b0011, TriggerPicks.LINKED.leftMask(mounts));
        var picks = new TriggerPicks(2, 4);
        assertEquals(0b0010, picks.leftMask(mounts));
        assertEquals(0b1000, picks.rightMask(mounts));
        assertEquals(0b1000, picks.heldMask(mounts, false, true), "only a held button fires");
        assertEquals("", FireGroups.badge(mounts, picks, 2));
        assertEquals("R", FireGroups.group(mounts, 2), "an unpicked mount stays listed on its button");
        assertEquals(0, FireGroups.mask(mounts, FireTrigger.LEFT, 3), "a right-button mount is never the left's pick");
    }
}
