package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictPostTest {

    @Test
    void aPostUnfoldsOnlyWhileItsZoneIsAwakeAndFoldsWhenItSettles() {
        SecurityZone zone = new SecurityZone(0, 10, 10, 8);
        assertFalse(DerelictPost.unfolded(zone));
        zone.hearRelay();
        assertFalse(DerelictPost.unfolded(zone), "alert is not awake");
        zone.stir(0);
        assertTrue(DerelictPost.unfolded(zone));
        zone.settle(SecurityZone.SETTLE_TICKS);
        assertFalse(DerelictPost.unfolded(zone));
    }

    @Test
    void postsGoToTheInteriorsDoorsAndRoomsTheZoneKeepsBeforeTheRoute() {
        RuinsDelve.Vault vault = new RuinsDelve.Vault(new Area(90, 40, 99, 60), new Cell(90, 50), new Cell(95, 50),
                new Cell(88, 50), new Cell(97, 50));
        RuinsDelve.Interior interior = new RuinsDelve.Interior(RuinsDelve.INTERIOR, new Area(60, 0, 99, 99), 2,
                List.of(new Area(60, 48, 61, 51)), List.of(new Area(70, 46, 74, 50), new Area(70, 90, 79, 99)));
        RuinsDelve delve = new RuinsDelve(3, new Area(0, 40, 10, 60), List.of(), vault, List.of(), null, List.of(),
                null, null, interior);
        SecurityZone zone = new SecurityZone(0, 66, 50, 10);
        List<int[]> anchors = DerelictPost.anchors(zone, List.of(new int[] {66, 50}, new int[] {62, 50}), delve);

        assertEquals(4, anchors.size(), "the far chamber is outside the zone");
        assertArrayEquals(new int[] {60, 49}, anchors.get(0));
        assertArrayEquals(new int[] {72, 48}, anchors.get(1));
        assertArrayEquals(new int[] {62, 50}, anchors.get(2));
        assertArrayEquals(new int[] {66, 50}, anchors.get(3), "the node cell last");
    }
}
