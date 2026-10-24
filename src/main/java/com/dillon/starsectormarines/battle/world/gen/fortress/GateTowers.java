package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The towers either side of a fort's gate, as rooms a squad holds (see {@code forts.md}). Each is set into the wall line
 * and stands proud of the wall face over the approach, so the garrison inside it shoots out at the gate's mouth and
 * along the face of the works, where a cap on solid wall shot at nothing.
 *
 * <p>Laid out in the gate's own frame: depth is counted inward from the gate's mouth (the open cell just outside it, at
 * depth 0) and the two-cell wall stands at depths 1 and 2. A tower's ring runs from depth -1, two cells proud of the
 * wall face, to depth 3, one cell into the yard, where its door is; along the wall it is five cells, its ring meeting
 * the gate passage's three cells on one side. Its floor is the three-by-three inside that ring, and the garrison stands
 * on the floor's outer row, behind the slits.
 *
 * <p>Pure geometry in the footprint frame.
 */
public final class GateTowers {

    private GateTowers() {}

    /** Cells of a tower's ring, across and deep. */
    public static final int SIZE = 5;

    /** How far inward from the gate's mouth the ring's outer side stands: two cells proud of the wall's outer face. */
    private static final int RING_OUTER = -1;

    /** Along the wall, how far each tower's floor centre stands from the gate passage's middle. */
    private static final int ALONG = 4;

    /** The two towers flanking {@code gate}, the one on the lesser side along the wall first. */
    public static List<FortRoom> flanking(FortPlan.Gate gate) {
        return List.of(tower(gate, -1), tower(gate, 1));
    }

    /** The towers flanking every gate, in gate order. */
    public static List<FortRoom> flanking(List<FortPlan.Gate> gates) {
        List<FortRoom> out = new ArrayList<>();
        for (FortPlan.Gate gate : gates) out.addAll(flanking(gate));
        return List.copyOf(out);
    }

    private static FortRoom tower(FortPlan.Gate gate, int side) {
        int inX = -gate.outX(), inY = -gate.outY();
        int alongX = Math.abs(gate.outY()), alongY = Math.abs(gate.outX());
        int centre = side * ALONG;
        // The floor: depths 0 to 2 (the outer row first), three cells along either side of the tower's centre.
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int[][] floor = new int[9][];
        int i = 0;
        for (int d = RING_OUTER + 1; d <= RING_OUTER + 3; d++) {
            for (int a = centre - 1; a <= centre + 1; a++) {
                int[] cell = {gate.mouthX() + inX * d + alongX * a, gate.mouthY() + inY * d + alongY * a};
                floor[i++] = cell;
                minX = Math.min(minX, cell[0]);
                minY = Math.min(minY, cell[1]);
            }
        }
        int doorDepth = RING_OUTER + SIZE - 1;
        int[] door = {gate.mouthX() + inX * doorDepth + alongX * centre, gate.mouthY() + inY * doorDepth + alongY * centre};
        List<int[]> stands = new ArrayList<>();
        for (int k = 0; k < 3; k++) stands.add(new int[]{floor[k][0] - minX, floor[k][1] - minY});
        return new FortRoom(null, false, RoomPurpose.GATE_TOWER, minX, minY, 3, 3,
                List.of(new Doorway(door[0] - minX, door[1] - minY)), List.of(), stands, null);
    }

    /**
     * The slits of a tower: every straight cell of its ring whose outward neighbour lies outside the works, as
     * {@code {x, y, outX, outY}} in the footprint frame. They look out over the approach, along the wall face and into the
     * gate's mouth, and never into the yard; a corner of the ring and the door are never cut.
     */
    public static List<int[]> slits(FortRoom tower, BiPredicate<Integer, Integer> isWorks) {
        List<int[]> out = new ArrayList<>();
        int[][] faces = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        List<int[]> doors = tower.doorCells();
        for (int[] face : faces) {
            for (int k = 0; k < (face[0] == 0 ? tower.width() : tower.height()); k++) {
                int x = face[0] == 0 ? tower.x() + k : face[0] < 0 ? tower.x() - 1 : tower.x() + tower.width();
                int y = face[1] == 0 ? tower.y() + k : face[1] < 0 ? tower.y() - 1 : tower.y() + tower.height();
                boolean door = false;
                for (int[] d : doors) door |= d[0] == x && d[1] == y;
                if (door || isWorks.test(x + face[0], y + face[1])) continue;
                out.add(new int[]{x, y, face[0], face[1]});
            }
        }
        return out;
    }

    /** Every cell of {@code tower}'s ring and floor, in the footprint frame. */
    public static List<int[]> footprint(FortRoom tower) {
        List<int[]> out = new ArrayList<>();
        for (int x = tower.x() - 1; x <= tower.x() + tower.width(); x++) {
            for (int y = tower.y() - 1; y <= tower.y() + tower.height(); y++) out.add(new int[]{x, y});
        }
        return out;
    }
}
