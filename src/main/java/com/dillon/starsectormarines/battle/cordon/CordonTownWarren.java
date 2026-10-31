package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;

import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.LineCourse;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Paving;
import com.dillon.starsectormarines.battle.world.gen.layout.RoadCourse;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Road's town round its compound ({@code cordon-nouns.md}, Cut-off town): the open ground east of the compound
 * and in front of it built up with small houses, shops and workshops on a staggered grid, the alleys between them
 * two or three cells wide ({@link #alley}), so the swarm comes at the compound through streets that break every line of sight and
 * the company has alleys to cover (owner, 2026-10-31). Raised as structures before the map is finished, on open
 * ground only: never on road nor within a cell of one, so the convoys' road stays clear, and never on the compound's
 * claim, so its glacis stays open round the wall.
 */
final class CordonTownWarren {

    /** The layout's name for the warren's structures, numbered. */
    static final String NAME = "town-block-";
    /** Cells east of the compound's claim the warren reaches, and cells in front of it. Untuned. */
    static final int EAST_REACH = 64, FRONT_REACH = 56;
    /** Cells past the compound's claim the warren also takes on its flanks, so its corners are built too. */
    static final int FLANK = 10;
    /** An alley's width between two buildings: two cells, or three where the building's corner draws it. */
    static final int ALLEY = 2, WIDE_ALLEY = 3;
    /**
     * Cells kept clear past the compound's claim (itself three cells past the wall), so a ring six cells deep round
     * the wall stays open for its gates and posterns.
     */
    static final int GLACIS = 3;
    /** Cells kept clear round any road cell: the pavement a building fronts the street across. */
    static final int ROAD_CLEAR = 1;

    /** The buildings the warren is built from, the largest first. */
    static final List<StructureKind> KINDS = List.of(StructureKind.TOWN_HALL, StructureKind.TOWN_DEPOT,
            StructureKind.TOWN_WORKSHOP, StructureKind.TOWN_HOUSE, StructureKind.TOWN_COURT, StructureKind.TOWN_SHOP);

    private CordonTownWarren() {}

    /** The layout's name for the town's streets, numbered. */
    static final String STREET = "town-street-";
    /** Cells across a town street: the colony's own streets' carriageway. */
    static final int STREET_WIDTH = 5;
    /** Cells, at most, the west link runs looking for the colony's streets. */
    static final int LINK_REACH = 70;

    /**
     * The town's streets round the compound, as courses of vertices {x, y} from the claim {@code c} ({@code front}
     * +1 where the front is high y): a front street out of the front postern that turns east across the compound's
     * far side and jogs half way; an east street out of the east postern that jogs the other way; a cross street
     * between them, meeting the east street at a T and the front street at its jog, so the junction is offset; a
     * street on north off the front street east of the jog, with a bend; and one north off its west end, with its
     * own bend. No street runs straight for long. The west link to the colony's streets is {@link #link}.
     */
    static List<List<int[]>> courses(int[] c, int front) {
        int f = front, far = f > 0 ? c[3] : c[1], cx = (c[0] + c[2]) / 2, cy = (c[1] + c[3]) / 2;
        int jog = c[2] + 22, frontRow = far + 14 * f, outRow = far + 19 * f, eastRow = cy - 6 * f;
        return List.of(
                List.of(new int[]{cx, far + f}, new int[]{cx, frontRow}, new int[]{jog, frontRow},
                        new int[]{jog, outRow}, new int[]{c[2] + 62, outRow}),
                List.of(new int[]{c[2] + 1, cy}, new int[]{c[2] + 16, cy}, new int[]{c[2] + 16, eastRow},
                        new int[]{c[2] + 62, eastRow}),
                List.of(new int[]{jog, eastRow}, new int[]{jog, frontRow}),
                List.of(new int[]{c[2] + 38, outRow}, new int[]{c[2] + 38, far + 34 * f},
                        new int[]{c[2] + 44, far + 34 * f}, new int[]{c[2] + 44, far + 54 * f}),
                List.of(new int[]{cx - 10, frontRow}, new int[]{cx - 10, far + 30 * f},
                        new int[]{cx - 16, far + 30 * f}, new int[]{cx - 16, far + 54 * f}));
    }

    /**
     * The front street carried on west from the postern along its row to the colony's nearest street, or
     * {@link #LINK_REACH} cells when none is met.
     */
    static List<int[]> link(LayoutGround ground, int[] c, int front) {
        int far = front > 0 ? c[3] : c[1], cx = (c[0] + c[2]) / 2, row = far + 14 * front;
        int x = cx;
        while (x > cx - LINK_REACH && x > 2) {
            x--;
            if (ground.isRoad(x, row) || ground.topology().getGroundKind(x, row) == CellTopology.GroundKind.STREET) break;
        }
        return List.of(new int[]{cx, row}, new int[]{x, row});
    }

    /** The town's streets as paving, and the cells they and their pavements take, on {@code ground}. */
    static List<Paving> streets(LayoutGround ground, int[] c, int front) {
        List<List<int[]>> courses = new ArrayList<>(courses(c, front));
        courses.add(link(ground, c, front));
        List<Paving> out = new ArrayList<>();
        for (List<int[]> course : courses) {
            out.add(new Paving(STREET + (out.size() + 1), RoadCourse.cells(course, STREET_WIDTH), false));
        }
        return out;
    }

    /** The warren's structures on {@code ground}, in front of the compound toward {@code front} (+1 or -1 in y). */
    static List<Piece> structures(LayoutGround ground, int front) {
        int[] c = ground.claimBounds(CivilDefenceCompound.PLACE);
        if (c == null) return List.of();
        // The compound is square, so its claim is its bounds: its glacis is kept open round the wall.
        List<Paving> streets = streets(ground, c, front);
        Set<Long> kerbed = new HashSet<>();
        for (Paving street : streets) {
            for (int[] cell : street.cells()) {
                for (int dx = -ROAD_CLEAR; dx <= ROAD_CLEAR; dx++) {
                    for (int dy = -ROAD_CLEAR; dy <= ROAD_CLEAR; dy++) kerbed.add(key(cell[0] + dx, cell[1] + dy));
                }
            }
        }
        LineCourse.CellTest free = (x, y) -> ground.buildable(x, y) && !ground.isHeld(x, y) && !kerbed.contains(key(x, y))
                && !(x >= c[0] - GLACIS && x <= c[2] + GLACIS && y >= c[1] - GLACIS && y <= c[3] + GLACIS)
                && !nearRoad(ground, x, y) && !plaza(c, front, x, y);
        List<Piece> out = new ArrayList<>(streets);
        List<int[]> regions = regions(c, front);
        LineCourse.CellTest inRegion = (x, y) -> {
            for (int[] r : regions) if (x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]) return true;
            return false;
        };
        Set<Long> taken = new HashSet<>();
        List<List<int[]>> courses = new ArrayList<>(courses(c, front));
        courses.add(link(ground, c, front));
        // The street fronts first, then what is left of each block behind them, thinner: back yards and sheds.
        List<Lot> lots = new ArrayList<>(frontage(courses, (x, y) -> free.test(x, y) && inRegion.test(x, y), taken));
        for (Lot lot : pack(free, regions, front, taken)) if (!vacant(lot)) lots.add(lot);
        for (Lot lot : lots) {
            out.add(new Structure(NAME + (out.size() + 1), lot.kind(), lot.x(), lot.y(), lot.turns(), false));
        }
        return out;
    }

    /** Gaps along a street front between two buildings, drawn by the first one's corner: mostly an alley, some lots. */
    static final int[] FRONT_GAPS = {1, 2, 1, 3, 2, 1, 6};

    /**
     * The buildings fronting the streets: along each straight stretch of each course, on both sides, a row of
     * buildings set back {@link #ROAD_CLEAR} past the carriageway's edge, their long side to the street, with a gap
     * of one to four cells between neighbours and now and then an empty lot. Marks each building and its gap
     * {@code taken}. Pure.
     */
    static List<Lot> frontage(List<List<int[]>> courses, LineCourse.CellTest free, Set<Long> taken) {
        List<Lot> out = new ArrayList<>();
        int set = STREET_WIDTH / 2 + ROAD_CLEAR + 1;
        for (List<int[]> course : courses) {
            for (int v = 1; v < course.size(); v++) {
                int[] a = course.get(v - 1), b = course.get(v);
                boolean across = a[1] == b[1];
                int lo = across ? Math.min(a[0], b[0]) : Math.min(a[1], b[1]);
                int hi = across ? Math.max(a[0], b[0]) : Math.max(a[1], b[1]);
                if (hi - lo < 6) continue;
                for (int side = -1; side <= 1; side += 2) {
                    int at = lo;
                    while (at < hi) {
                        Lot lot = frontLot(free, taken, across, side, across ? a[1] : a[0], at, hi, set);
                        if (lot == null) {
                            at++;
                            continue;
                        }
                        out.add(lot);
                        int w = width(lot), h = height(lot);
                        for (int cx = lot.x() - 1; cx <= lot.x() + w; cx++) {
                            for (int cy = lot.y() - 1; cy <= lot.y() + h; cy++) taken.add(key(cx, cy));
                        }
                        int gap = FRONT_GAPS[Math.floorMod(lot.x() * 7 + lot.y() * 3, FRONT_GAPS.length)];
                        at += (across ? w : h) + gap;
                    }
                }
            }
        }
        return out;
    }

    /**
     * A building on {@code side} of a straight stretch whose line is {@code line} (its row, or its column), starting
     * {@code at} along it and ending by {@code hi}, its near edge {@code set} cells off the line; {@code null} when
     * none fits there.
     */
    private static Lot frontLot(LineCourse.CellTest free, Set<Long> taken, boolean across, int side, int line, int at,
                                int hi, int set) {
        int start = Math.floorMod(at * 5 + line * 3 + side, KINDS.size());
        for (int k = 0; k < KINDS.size(); k++) {
            StructureKind kind = KINDS.get((start + k) % KINDS.size());
            // The long side along the street: unturned along a row, a quarter turn along a column; the far way
            // round faces the other side.
            int turns = (across ? 0 : 1) + (side > 0 ? 0 : 2);
            int w = (turns & 1) == 0 ? kind.along : kind.deep, h = (turns & 1) == 0 ? kind.deep : kind.along;
            if ((across ? w : h) > hi - at + 1) continue;
            int x, y;
            if (across) {
                x = at;
                y = side > 0 ? line + set : line - set - h + 1;
            } else {
                y = at;
                x = side > 0 ? line + set : line - set - w + 1;
            }
            if (clear(free, taken, x, y, w, h)) return new Lot(kind, x, y, turns);
        }
        return null;
    }

    /** One building of the warren: its kind, its footprint's lower-left corner and its quarter turns. */
    record Lot(StructureKind kind, int x, int y, int turns) { }

    /**
     * The regions the warren is built in, inclusive {x0, y0, x1, y1}: the strip east of the compound's claim
     * {@code c}, running past its front, and the strip in front of it, running past its west flank.
     */
    static List<int[]> regions(int[] c, int front) {
        int frontY0 = front > 0 ? c[3] + 1 : c[1] - FRONT_REACH;
        int frontY1 = front > 0 ? c[3] + FRONT_REACH : c[1] - 1;
        int[] east = {c[2] + 1, front > 0 ? c[1] - FLANK : frontY0, c[2] + EAST_REACH,
                front > 0 ? frontY1 : c[3] + FLANK};
        int[] ahead = {c[0] - FLANK, frontY0, c[2], frontY1};
        return List.of(east, ahead);
    }

    /**
     * Fits buildings into the regions as tightly as the alleys allow: each region scanned row by row from its edge
     * nearest the compound, and at each cell the first building (either way round) whose footprint is all
     * {@code free} and stands at least {@link #ALLEY} cells from every building already placed. Which building is
     * tried first turns with the cell, so neighbours differ and the alleys jog round their different shapes. Pure.
     */
    static List<Lot> pack(LineCourse.CellTest free, List<int[]> regions, int front) {
        return pack(free, regions, front, new HashSet<>());
    }

    /** As {@link #pack(LineCourse.CellTest, List, int)}, round ground already {@code taken}, which it marks. */
    static List<Lot> pack(LineCourse.CellTest free, List<int[]> regions, int front, Set<Long> taken) {
        List<Lot> out = new ArrayList<>();
        for (int[] r : regions) {
            for (int i = 0; i <= r[3] - r[1]; i++) {
                int y = front > 0 ? r[1] + i : r[3] - i;
                for (int x = r[0]; x <= r[2]; x++) {
                    if (taken.contains(key(x, y))) continue;
                    Lot lot = fit(free, taken, r, x, y, front);
                    if (lot == null) continue;
                    out.add(lot);
                    int w = width(lot), h = height(lot), alley = alley(lot);
                    for (int cx = lot.x() - alley; cx < lot.x() + w + alley; cx++) {
                        for (int cy = lot.y() - alley; cy < lot.y() + h + alley; cy++) taken.add(key(cx, cy));
                    }
                }
            }
        }
        return out;
    }

    /** A building whose corner nearest the compound is at {@code (x, y)}, or {@code null} when none fits there. */
    private static Lot fit(LineCourse.CellTest free, Set<Long> taken, int[] r, int x, int y, int front) {
        int start = Math.floorMod(x * 5 + y * 3, KINDS.size());
        for (int k = 0; k < KINDS.size(); k++) {
            StructureKind kind = KINDS.get((start + k) % KINDS.size());
            for (int t = 0; t < 2; t++) {
                int turns = Math.floorMod(x + y + t, 2) + 2 * Math.floorMod(x / 3 + y, 2);
                int w = (turns & 1) == 0 ? kind.along : kind.deep, h = (turns & 1) == 0 ? kind.deep : kind.along;
                int bx = x, by = front > 0 ? y : y - h + 1;
                if (bx + w - 1 > r[2] || by < r[1] || by + h - 1 > r[3]) continue;
                if (clear(free, taken, bx, by, w, h)) return new Lot(kind, bx, by, turns);
            }
        }
        return null;
    }

    private static boolean clear(LineCourse.CellTest free, Set<Long> taken, int x, int y, int w, int h) {
        for (int cx = x; cx < x + w; cx++) {
            for (int cy = y; cy < y + h; cy++) if (taken.contains(key(cx, cy)) || !free.test(cx, cy)) return false;
        }
        return true;
    }

    /**
     * The ground kept round a building, by its corner: a narrow alley for most, a wider one, a back yard or a
     * courtyard's width for others, so the blocks read as lots of different sizes rather than one carpet.
     */
    static int alley(Lot lot) {
        return ALLEYS[Math.floorMod(lot.x() * 7 + lot.y() * 13, ALLEYS.length)];
    }

    /** The ground round a building, drawn by its corner: mostly alleys, some yards. */
    static final int[] ALLEYS = {ALLEY, WIDE_ALLEY, ALLEY, 4, ALLEY, WIDE_ALLEY, 5};

    /** Whether a lot behind the street fronts is left empty, a yard or a gap: one in six, by its corner. */
    static boolean vacant(Lot lot) {
        return Math.floorMod(lot.x() * 11 + lot.y() * 5, 6) == 0;
    }

    /**
     * The town's squares, open ground nothing is built on: one in front of the compound past the front street and
     * one east of it between the cross street and the warren's edge, each where a town bore's band lies, so the bore
     * finds its site in a square rather than an alley. {x, y} of each square's middle, from the claim {@code c}.
     */
    static int[][] plazas(int[] c, int front) {
        int far = front > 0 ? c[3] : c[1];
        return new int[][]{{(c[0] + c[2]) / 2 + 12, far + front * PLAZA_OFF}, {c[2] + PLAZA_OFF, (c[1] + c[3]) / 2}};
    }

    /** Cells off the compound's claim a square's middle lies (inside the town bores' band), and its half side. */
    static final int PLAZA_OFF = 40, PLAZA_HALF = 6;

    private static boolean plaza(int[] c, int front, int x, int y) {
        for (int[] p : plazas(c, front)) {
            if (Math.abs(x - p[0]) <= PLAZA_HALF && Math.abs(y - p[1]) <= PLAZA_HALF) return true;
        }
        return false;
    }

    static int width(Lot lot) {
        return (lot.turns() & 1) == 0 ? lot.kind().along : lot.kind().deep;
    }

    static int height(Lot lot) {
        return (lot.turns() & 1) == 0 ? lot.kind().deep : lot.kind().along;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }

    private static boolean nearRoad(LayoutGround ground, int x, int y) {
        for (int dx = -ROAD_CLEAR; dx <= ROAD_CLEAR; dx++) {
            for (int dy = -ROAD_CLEAR; dy <= ROAD_CLEAR; dy++) if (ground.isRoad(x + dx, y + dy)) return true;
        }
        return false;
    }
}
