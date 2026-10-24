package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Building;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Cluster;

import java.util.ArrayList;
import java.util.List;

/**
 * The battery fort as an authored compound layout, in one cardinally rotatable local frame, built as defence in depth
 * from the outside in (see {@code forts.md}):
 *
 * <ul>
 *   <li>the <b>outer belt</b> ({@link FortBelt}): an irregular ring of trench, sandbag line and fence line, with nests, standing
 *       off the main works across an open killing ground;</li>
 *   <li>the <b>main works</b>: a chamfered trace two cells thick with a pointed bastion at each corner, and at each gate
 *       a baffle and two towers set into the wall ({@link GateTowers}) that the garrison fights from;</li>
 *   <li>the <b>interior</b>: a hardened fire-control bunker, a bermed magazine standing apart, a power plant with its
 *       yard, and the battery in a revetted gun pit.</li>
 * </ul>
 *
 * <p>The footprint is the whole of it, belt included; the works stand in the middle at ({@link #OX}, {@link #OY}).
 * Everything here is pure geometry in the local frame: {@code FortStage} places it on ground the city has left
 * free and rotates it as one. A fort is stamped into a city that already exists, so the works and their glacis need
 * clear ground and the belt follows whatever ground is open.
 */
public final class BatteryFort {

    /** Strength of every wall cell of the main works, the gate towers' walls included. */
    public static final int WALL_HP = 400;

    /** Local width and height of the footprint, belt and fence included. */
    public static final int WIDTH = 82, HEIGHT = 72;

    /** Where the main works' own frame begins in the footprint, and its size. */
    public static final int OX = 17, OY = 17, WORKS_W = 48, WORKS_H = 36;

    private static final int CUT = 8;
    private static final int BASTION_RADIUS = 8;

    /** The cells of the wall left open as gates: the main gate on the west face, a postern on the north. */
    public static final List<int[]> GATE_CELLS = List.of(
            new int[]{OX, OY + 16}, new int[]{OX, OY + 17}, new int[]{OX, OY + 18},
            new int[]{OX + 1, OY + 16}, new int[]{OX + 1, OY + 17}, new int[]{OX + 1, OY + 18},
            new int[]{OX + 22, OY}, new int[]{OX + 23, OY}, new int[]{OX + 24, OY},
            new int[]{OX + 22, OY + 1}, new int[]{OX + 23, OY + 1}, new int[]{OX + 24, OY + 1});

    /**
     * The sally ports, one in the middle of each face that has no gate: the south face and the east. Each is two cells of
     * the two-deep wall, closed until the fort's holder unbars it.
     */
    public static final List<FortPlan.SallyPort> SALLY_PORTS = List.of(
            FortPlan.SallyPort.onFace(OX + 24, OY + 35, 0, 1, 2),
            FortPlan.SallyPort.onFace(OX + 47, OY + 17, 1, 0, 2));

    /** The open cell just outside the middle of each gate, which is where the way out to the road network begins. */
    public static final List<int[]> GATE_MOUTHS = List.of(new int[]{OX - 1, OY + 17}, new int[]{OX + 23, OY - 1});

    /** The main gate faces west, the postern north. */
    private static final List<FortPlan.Gate> GATES = List.of(
            new FortPlan.Gate(OX - 1, OY + 17, -1, 0), new FortPlan.Gate(OX + 23, OY - 1, 0, -1));

    /** The towers either side of each gate, set into the wall and standing proud of it over the approach. */
    private static final List<FortRoom> TOWERS = GateTowers.flanking(GATES);

    /** The middle of the battery platform's three-by-three footprint. */
    public static final int[] PLATFORM = {OX + 36, OY + 28};

    /**
     * Each corner bastion's centre in the works' frame, the diagonal its missile battery stands toward (its tip), and the axis
     * its nest looks along, in nest order. Every nest looks along the same axis as its pair: the two on one side of the works look one way and the two on the other side look the opposite way, so the four stand mirrored about the works (the older mix of north, east and diagonal looks read as inconsistent). It stands on the bastion face on that axis, as a
     * flanking gun does: it sees almost everything outside the works and enfilades the face beside it, which is also what keeps
     * the gates and the breach within its reach.
     */
    private static final int[][] BASTIONS = {
            {1, 1, -1, -1, 0, -1}, {WORKS_W - 2, 1, 1, -1, 0, -1}, {1, WORKS_H - 2, -1, 1, 0, 1}, {WORKS_W - 2, WORKS_H - 2, 1, 1, 0, 1}};

    /**
     * Bastion nests, one in an embrasure on the outer face of each corner bastion, where it can fire out of the works
     * (a nest in the open floor of the pocket was boxed in by the wall round it). Each is a notch cut in the wall's outer
     * layer, so the wall stands behind it.
     */
    public static final List<int[]> NESTS;

    /** Which bastions (indices of {@link #NESTS}) stand a Locust missile battery when the fort carries the anti-air aspect: the two farthest from the main gate. */
    private static final int[] AIR_BASTIONS = {1, 3};

    /** Which bastions stand a Swarmer missile tower: the two nearest the main gate, so all four corners carry anti-air. */
    private static final int[] SWARMER_BASTIONS = {0, 2};

    /** How far from a bastion's centre, toward its tip, its missile battery's middle stands. */
    private static final int BATTERY_POCKET_STEPS = 2;

    /**
     * The gatehouse pieces: the gate leaves over each passage. A west-facing gate is authored as drawn; the postern on
     * the north is the same art turned one quarter.
     */
    public static final List<FortPlan.Piece> GATEHOUSE = List.of(
            new FortPlan.Piece(FortPlan.PieceKind.GATE, OX, OY + 16, 2, 3, 0),
            new FortPlan.Piece(FortPlan.PieceKind.GATE, OX + 22, OY, 3, 2, 1));

    /** The optional anti-air yard: the open ground between the platform's pit and the bunker, off every lane. */
    public static final AirDefenceYard AIR_DEFENCE = new AirDefenceYard(OX + 23, OY + 13, 0, 1);

    /**
     * The aspects' rooms in the footprint frame: the fire-control suite (an operations hall in front of the plotting
     * vault), the ammunition store behind its loading bay, and the generator hall. The first room of each aspect is its
     * main room, whose floor the roof art is registered to and which holds the core.
     */
    private static final List<FortRoom> ROOMS = FortRooms.battery(OX, OY);

    /** Paved vehicle lanes (x, y, width, height) in the footprint frame: concrete only where wheels need it. */
    public static final int[][] LANES = {
            {OX, OY + 16, 6, 3}, {OX + 4, OY + 13, 2, 9}, {OX + 4, OY + 19, 37, 3},
            {OX + 29, OY + 19, 3, 10}, {OX + 10, OY + 22, 3, 5},
            {OX + 38, OY + 11, 3, 9}, {OX + 11, OY + 12, 3, 8},
            {OX + 22, OY, 3, 5}};

    /** Open ground every placement must find round the works: the works dilated by this many cells. */
    public static final int GLACIS = 4;

    private static final boolean[][] CLEAR = new boolean[WIDTH][HEIGHT];
    private static final List<int[]> CLEAR_CELLS = new ArrayList<>();

    private static final boolean[][] INSIDE = new boolean[WIDTH][HEIGHT];
    private static final boolean[][] WALL = new boolean[WIDTH][HEIGHT];

    static {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) INSIDE[x][y] = trace(x - OX, y - OY);
        }
        // The gate towers are part of the works where they stand proud of the trace.
        for (FortRoom tower : TOWERS) for (int[] c : GateTowers.footprint(tower)) INSIDE[c[0]][c[1]] = true;
        // The baffles stand inside the trace as solid blocks.
        boolean[][] block = new boolean[WIDTH][HEIGHT];
        mark(block, OX + 6, OY + 13, 2, 7);
        mark(block, OX + 20, OY + 6, 7, 2);
        boolean[][] layer1 = new boolean[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (INSIDE[x][y] && (!in(x + 1, y) || !in(x - 1, y) || !in(x, y + 1) || !in(x, y - 1))) layer1[x][y] = true;
            }
        }
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (!INSIDE[x][y]) continue;
                boolean layer2 = !layer1[x][y] && (l1(layer1, x + 1, y) || l1(layer1, x - 1, y)
                        || l1(layer1, x, y + 1) || l1(layer1, x, y - 1));
                WALL[x][y] = layer1[x][y] || layer2 || block[x][y];
            }
        }
        for (int[] gate : GATE_CELLS) WALL[gate[0]][gate[1]] = false;
        // A tower is a building: its ring is stamped as one, and its floor is open.
        for (FortRoom tower : TOWERS) for (int[] c : GateTowers.footprint(tower)) WALL[c[0]][c[1]] = false;
        List<int[]> nests = new ArrayList<>();
        for (int[] b : BASTIONS) {
            int[] nest = BastionSites.embrasure(INSIDE, OX + b[0], OY + b[1], b[4], b[5]);
            WALL[nest[0]][nest[1]] = false;
            // A second cell behind the notch is wall too: at an arm's tip the wall is one cell thick, and a one-thick wall open on both
            // faces draws as nothing, so the notch read as a breach through the works.
            WALL[nest[0] - 2 * b[4]][nest[1] - 2 * b[5]] = true;
            nests.add(nest);
        }
        NESTS = List.copyOf(nests);
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                boolean near = false;
                for (int dx = -GLACIS; dx <= GLACIS && !near; dx++) {
                    for (int dy = -GLACIS; dy <= GLACIS && !near; dy++) near = in(x + dx, y + dy);
                }
                CLEAR[x][y] = near;
                if (near) CLEAR_CELLS.add(new int[]{x, y});
            }
        }
    }

    private BatteryFort() {}

    /** The battery fort as a {@link FortPlan}, for the stage that places every kind. */
    public static final FortPlan PLAN = new FortPlan() {
        @Override public FortKind kind() { return FortKind.BATTERY_FORT; }
        @Override public List<FortPlan.Piece> gatehouse() { return GATEHOUSE; }
        @Override public int width() { return WIDTH; }
        @Override public int height() { return HEIGHT; }
        @Override public CompoundLayout layout() { return BatteryFort.layout(); }
        @Override public boolean isWorks(int x, int y) { return BatteryFort.isWorks(x, y); }
        @Override public boolean isWall(int x, int y) { return BatteryFort.isWall(x, y); }
        @Override public List<int[]> clearCells() { return BatteryFort.clearCells(); }
        @Override public List<Gate> gates() { return GATES; }
        @Override public List<int[]> gateCells() { return GATE_CELLS; }
        @Override public List<FortPlan.SallyPort> sallyPorts() { return SALLY_PORTS; }
        @Override public List<int[]> nests() { return NESTS; }
        @Override public int[][] lanes() { return LANES; }
        @Override public List<FortBelt.Cell> belt() { return FortBelt.cells(); }
        @Override public List<FortAspect> aspects() { return ASPECTS; }
        @Override public List<FortRoom> rooms() { return ROOMS; }
        @Override public List<FortRoom> towers() { return TOWERS; }
        @Override public int[] floor(FortAspect aspect) { return BatteryFort.floor(aspect); }
        @Override public int[] door(FortAspect aspect) { return BatteryFort.door(aspect); }
        @Override public int[] coreCentre(FortAspect aspect) { return BatteryFort.coreCentre(aspect); }
        @Override public String coreStructureId(FortAspect aspect) {
            return switch (aspect) {
                case FIRE_CONTROL -> TurretCatalogRegistry.FORT_FIRE_CONTROL_STRUCTURE_ID;
                case MAGAZINE -> TurretCatalogRegistry.FORT_MAGAZINE_STRUCTURE_ID;
                case POWER -> TurretCatalogRegistry.FORT_POWER_STRUCTURE_ID;
                case AIR_DEFENCE -> TurretCatalogRegistry.FORT_AIR_CONTROL_STRUCTURE_ID;
                default -> throw new IllegalArgumentException("a battery fort has no " + aspect);
            };
        }
        @Override public String centrepieceStructureId() { return TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID; }
        @Override public int[] centrepiece() { return PLATFORM; }
        @Override public FortAspect centrepieceAspect() { return null; }
        @Override public AirDefenceYard airDefence() { return AIR_DEFENCE; }
        @Override public List<int[]> airBatteries() { return AIR_BATTERIES; }
        @Override public List<int[]> airTowers() { return AIR_TOWERS; }
    };

    /** The Locust batteries' middles: in the pocket behind the nest of each of the {@link #AIR_BASTIONS}. */
    private static final List<int[]> AIR_BATTERIES = pockets(AIR_BASTIONS);

    /** The Swarmer towers' middles: in the pocket of each of the {@link #SWARMER_BASTIONS}. */
    private static final List<int[]> AIR_TOWERS = pockets(SWARMER_BASTIONS);

    private static List<int[]> pockets(int[] bastions) {
        List<int[]> out = new ArrayList<>();
        for (int i : bastions) {
            int[] b = BASTIONS[i];
            out.add(BastionSites.pocketClearOfWall(OX + b[0], OY + b[1], b[2], b[3], BATTERY_POCKET_STEPS, BatteryFort::isWall));
        }
        return List.copyOf(out);
    }

    private static final List<FortAspect> ASPECTS = List.of(FortAspect.FIRE_CONTROL, FortAspect.MAGAZINE, FortAspect.POWER);

    private static void mark(boolean[][] grid, int x, int y, int w, int h) {
        for (int dx = 0; dx < w; dx++) for (int dy = 0; dy < h; dy++) grid[x + dx][y + dy] = true;
    }

    private static boolean in(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && INSIDE[x][y];
    }

    private static boolean l1(boolean[][] layer, int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && layer[x][y];
    }

    /** The chamfered trace with a diamond bastion at each corner, in the works' own frame. */
    private static boolean trace(int wx, int wy) {
        boolean rect = wx >= 0 && wy >= 0 && wx < WORKS_W && wy < WORKS_H
                && wx + wy >= CUT && (WORKS_W - 1 - wx) + wy >= CUT
                && wx + (WORKS_H - 1 - wy) >= CUT && (WORKS_W - 1 - wx) + (WORKS_H - 1 - wy) >= CUT;
        if (rect) return true;
        int[][] centres = {{1, 1}, {WORKS_W - 2, 1}, {1, WORKS_H - 2}, {WORKS_W - 2, WORKS_H - 2}};
        for (int[] c : centres) {
            if (Math.abs(wx - c[0]) + Math.abs(wy - c[1]) <= BASTION_RADIUS) return true;
        }
        return false;
    }

    /** The cells that must be open ground for a fort to stand: the works and the glacis round them. */
    public static List<int[]> clearCells() {
        return CLEAR_CELLS;
    }

    /** Whether the footprint cell is part of the works or its glacis. */
    public static boolean isCleared(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && CLEAR[x][y];
    }

    /** Whether the footprint cell is within the main works, wall included. */
    public static boolean isWorks(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && INSIDE[x][y];
    }

    /** Whether the local cell is part of the works' wall, gatehouse and baffles included, and not a gate. */
    public static boolean isWall(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && WALL[x][y];
    }

    /**
     * The layout: every room of every aspect's suite, then the gate towers. Walls, belt, lanes and dressing are the
     * host's to stamp from this frame.
     */
    public static CompoundLayout layout() {
        List<Building> core = new ArrayList<>();
        for (FortRoom room : PLAN.buildings()) core.add(room.building());
        return new CompoundLayout(RoomShape.rectangle(WIDTH, HEIGHT), List.of(
                new Cluster("Fort buildings", core)), List.of(), List.of());
    }

    /** The floor of the main room housing {@code aspect}, the one its roof art is registered to: x, y, width, height in the footprint frame. */
    public static int[] floor(FortAspect aspect) {
        if (aspect == FortAspect.AIR_DEFENCE) return AIR_DEFENCE.floor();
        return main(aspect).floor();
    }

    /** The primary door of the main room housing {@code aspect}, as the footprint cell of its wall ring. */
    public static int[] door(FortAspect aspect) {
        if (aspect == FortAspect.AIR_DEFENCE) return AIR_DEFENCE.gapCell();
        return main(aspect).doorCells().get(0);
    }

    /** The middle of the three-by-three core of {@code aspect}, in the local frame. */
    public static int[] coreCentre(FortAspect aspect) {
        if (aspect == FortAspect.AIR_DEFENCE) return AIR_DEFENCE.consoleCentre();
        return main(aspect).coreCentre();
    }

    private static FortRoom main(FortAspect aspect) {
        for (FortRoom room : ROOMS) if (room.main() && room.aspect() == aspect) return room;
        throw new IllegalArgumentException("a battery fort has no " + aspect);
    }
}
