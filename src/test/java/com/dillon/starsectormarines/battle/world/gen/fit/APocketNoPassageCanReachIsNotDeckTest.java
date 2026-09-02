package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A room does not go unplaced because the best-packed ground is sealed.
 *
 * <p>The packer scores a position by how much of its bulkhead ring already
 * backs onto something solid, which is the whole of the packing: rooms gather
 * into blocks and leave their gaps together. A sealed pocket is therefore the
 * best-scoring ground on the map by construction — it is the most enclosed
 * there is — and it is also the one place no passage can be cut from. Nothing
 * recorded that, so every room whose footprint fitted the pocket shortlisted
 * the same unreachable positions, spent its whole attempt budget on them, and
 * was published unplaced with open ground standing empty elsewhere.
 *
 * <p>The fixture is that shape and nothing else: one strip of buildable ground
 * walled off from everything, offering more positions than the packer has
 * attempts, beside an open yard with a corridor in it. Asked of the packer
 * directly, because the fault is in the search rather than in any hull that
 * happens to generate one of these.
 */
class APocketNoPassageCanReachIsNotDeckTest {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 24;

    /** Open ground, with the whole of the map's border outside it. */
    private static final int YARD_RIGHT = 20;

    /**
     * A strip sealed on every side, three cells deep so the room fills it top
     * to bottom and every position along it backs onto the wall twice over.
     * Sixteen wide, so it offers twelve positions where the packer has eight
     * attempts — which is what makes one unreachable pocket enough to lose a
     * room outright.
     */
    private static final int POCKET_LEFT = 23;
    private static final int POCKET_RIGHT = 38;
    private static final int POCKET_TOP = 10;
    private static final int POCKET_BOTTOM = 12;

    private static final int ROOM_WIDTH = 5;
    private static final int ROOM_DEPTH = 3;

    private static final RoomPacker.Palette PALETTE =
            new RoomPacker.Palette(GroundKind.INDOOR, GroundKind.INDOOR, GroundKind.STRIPED);

    @Test
    void aPocketNoPassageCanReachIsNotDeck() {
        RoomPacker packer = packer();

        RoomPacker.Placed room = packer.place(request(), true);

        assertNotNull(room, "the room went unplaced with the whole yard standing empty");
        assertTrue(room.originX() + room.shape().width() <= YARD_RIGHT + 1,
                "the room was placed at " + room.originX() + "," + room.originY()
                        + ", which is inside a pocket nothing can reach");
    }

    /**
     * And the next room of the same footprint does not pay for the discovery.
     *
     * <p>One refusal is enough to answer for every position in the pocket, so
     * the second room's scan never offers it one. Without that memory the
     * second room repeats the first room's eight fruitless searches and is
     * published unplaced in its turn, which is how a liner came to place half
     * of her berthing and none of the rest.
     */
    @Test
    void aDeadPocketIsNotOfferedTwice() {
        RoomPacker packer = packer();

        RoomPacker.Placed first = packer.place(request(), true);
        RoomPacker.Placed second = packer.place(request(), true);

        assertNotNull(first, "the first room went unplaced");
        assertNotNull(second, "the second room paid the pocket's search over again");
        assertTrue(second.originX() + second.shape().width() <= YARD_RIGHT + 1,
                "the second room was placed at " + second.originX() + "," + second.originY()
                        + ", which is inside a pocket nothing can reach");
        assertTrue(first.originX() != second.originX() || first.originY() != second.originY(),
                "both rooms were laid down in the same place");
    }

    private static RoomPacker.Request request() {
        // A purpose with no fitting, so what is measured is the packing rather
        // than a room's authored doors.
        return new RoomPacker.Request(RoomPurpose.GENERIC,
                RoomShape.rectangle(ROOM_WIDTH, ROOM_DEPTH));
    }

    private static RoomPacker packer() {
        GenContext ctx = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(7L), WIDTH, HEIGHT, 7L);

        boolean[][] buildable = new boolean[WIDTH][HEIGHT];
        for (int x = 1; x <= YARD_RIGHT; x++) {
            for (int y = 1; y < HEIGHT - 3; y++) buildable[x][y] = true;
        }
        for (int x = POCKET_LEFT; x <= POCKET_RIGHT; x++) {
            for (int y = POCKET_TOP; y <= POCKET_BOTTOM; y++) buildable[x][y] = true;
        }

        // A stub of corridor out in the middle of the yard, so reaching it is a
        // passage the packer has to cut rather than a wall it already backs on.
        boolean[][] circulation = new boolean[WIDTH][HEIGHT];
        circulation[10][10] = true;
        circulation[11][10] = true;

        return new RoomPacker(ctx, buildable, circulation, PALETTE);
    }
}
