package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BlockingDoorTest {

    private static final int W = 20, H = 10;

    /** The frame reaches columns 0..9 of every row. */
    private static boolean[] leftHalf() {
        boolean[] reach = new boolean[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < 10; x++) reach[y * W + x] = true;
        return reach;
    }

    private static RuinsDelve.LeafDoor door(int x, int y, RuinsDelve.DoorStance stance, boolean cargo) {
        return new RuinsDelve.LeafDoor(new RuinsDelve.Area(x, y, x, y + 3), false, cargo, stance);
    }

    @Test
    void theFrameRamsTheDeadDoorOnTheEdgeOfItsGroundNearestItsWayOut() {
        RuinsDelve.LeafDoor answers = door(10, 0, RuinsDelve.DoorStance.ANSWERS, false);
        RuinsDelve.LeafDoor cargo = door(10, 5, RuinsDelve.DoorStance.DEAD, true);
        RuinsDelve.LeafDoor farDead = door(17, 3, RuinsDelve.DoorStance.DEAD, false);
        RuinsDelve.LeafDoor nearOut = door(11, 6, RuinsDelve.DoorStance.DEAD, false);
        RuinsDelve.LeafDoor awayFromOut = door(11, 0, RuinsDelve.DoorStance.DEAD, false);
        List<RuinsDelve.LeafDoor> doors = List.of(answers, cargo, farDead, nearOut, awayFromOut);
        assertSame(nearOut, DerelictSecurity.blockingDoor(doors, leftHalf(), W, new int[] {12, 9}));
        assertSame(awayFromOut, DerelictSecurity.blockingDoor(doors, leftHalf(), W, new int[] {12, 0}));
    }

    @Test
    void noDeadDoorBesideItsGroundShutsItIn() {
        List<RuinsDelve.LeafDoor> doors = List.of(door(15, 3, RuinsDelve.DoorStance.DEAD, false),
                door(10, 3, RuinsDelve.DoorStance.ANSWERS, false));
        assertNull(DerelictSecurity.blockingDoor(doors, leftHalf(), W, new int[] {19, 5}));
    }
}
