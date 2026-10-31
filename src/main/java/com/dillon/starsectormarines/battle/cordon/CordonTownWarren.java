package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;

import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.LineCourse;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
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
    static final int EAST_REACH = 60, FRONT_REACH = 48;
    /** Cells past the compound's claim the warren also takes on its flanks, so its corners are built too. */
    static final int FLANK = 10;
    /** An alley's width between two buildings: two cells, or three where the building's corner draws it. */
    static final int ALLEY = 2, WIDE_ALLEY = 3;
    /**
     * Cells kept clear past the compound's claim (itself three cells past the wall), so a ring six cells deep round
     * the wall stays open for its gates and posterns.
     */
    static final int GLACIS = 3;
    /** Cells kept clear round any road cell. */
    static final int ROAD_CLEAR = 1;

    /** The buildings the warren is built from, the largest first. */
    static final List<StructureKind> KINDS = List.of(StructureKind.TOWN_HALL, StructureKind.TOWN_DEPOT,
            StructureKind.TOWN_WORKSHOP, StructureKind.TOWN_HOUSE, StructureKind.TOWN_COURT, StructureKind.TOWN_SHOP);

    private CordonTownWarren() {}

    /** The warren's structures on {@code ground}, in front of the compound toward {@code front} (+1 or -1 in y). */
    static List<Piece> structures(LayoutGround ground, int front) {
        int[] c = ground.claimBounds(CivilDefenceCompound.PLACE);
        if (c == null) return List.of();
        // The compound is square, so its claim is its bounds: its glacis is kept open round the wall.
        LineCourse.CellTest free = (x, y) -> ground.buildable(x, y) && !ground.isHeld(x, y)
                && !(x >= c[0] - GLACIS && x <= c[2] + GLACIS && y >= c[1] - GLACIS && y <= c[3] + GLACIS)
                && !nearRoad(ground, x, y);
        List<Piece> out = new ArrayList<>();
        for (Lot lot : pack(free, regions(c, front), front)) {
            out.add(new Structure(NAME + (out.size() + 1), lot.kind(), lot.x(), lot.y(), lot.turns(), false));
        }
        return out;
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
        List<Lot> out = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
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

    /** The alley kept round a building: {@link #WIDE_ALLEY} for one in three, by its corner, else {@link #ALLEY}. */
    static int alley(Lot lot) {
        return Math.floorMod(lot.x() * 7 + lot.y() * 13, 3) == 0 ? WIDE_ALLEY : ALLEY;
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
