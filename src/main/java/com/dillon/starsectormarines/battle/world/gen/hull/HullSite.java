package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Interior;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fallen hull's ground as a mission reads it ({@code fallen-hull-nouns.md}): the hull, the trench it ploughed and the
 * debris it shed, its interior (the ruins' {@link Interior}, reused: entrances, aprons, chambers, halls and leaf doors),
 * the forge hall the autoforge stands in, the claim lattice round it, the haul roads from each lattice point back to
 * the hull, and the stakes that stand at the chapter's start. Laid by the ground before the battle; nothing here
 * moves in play.
 *
 * <p>Published names ({@link #places}): {@code hull}, {@code hull.trench}, {@code hull.forge}, {@code hull.bay},
 * {@code hull.interior} and its {@code ruins.interior.*} chambers and aprons, {@code hull.debris.N} and
 * {@code hull.lattice.N}.
 *
 * <p>{@code problems} are what the ground could not do as the patch asked (a lattice point it could not seat a stake
 * at, a hull laid over a road); a story map's acceptance fails on any.
 */
public record HullSite(Area footprint, Direction heading, Interior interior, Area forgeHall, Cell forge, Area bay,
                       Area trench, List<Area> debris, List<LatticePoint> lattice, List<HaulRoad> roads,
                       List<Stake> stakes, List<String> problems, int mapWidth, BitSet hullCells, BitSet trenchCells) {

    public static final String HULL = "hull", TRENCH = "hull.trench", FORGE = "hull.forge", BAY = "hull.bay",
            INTERIOR = "hull.interior";

    /** How far a stake has risen ({@code fallen-hull-nouns.md}, A stake's stages). */
    public enum StakeStage { PLANTED, RAISED, ESTABLISHED }

    /**
     * One point of the claim lattice: {@code col, row} its place on the grid about the hull's middle, {@code ring} its
     * Chebyshev distance from it in lattice steps, {@code cell} the middle of the 5x5 ground a stake stands on.
     */
    public record LatticePoint(int index, int col, int row, int ring, Cell cell) { }

    /** The way a hauler walks from lattice point {@code point} back to the hull: its cells, from the point to an apron. */
    public record HaulRoad(int point, List<Cell> cells) {
        public HaulRoad {
            cells = List.copyOf(cells);
        }
    }

    /** A stake standing at the chapter's start, on lattice point {@code point}. */
    public record Stake(int point, StakeStage stage) { }

    public HullSite {
        debris = List.copyOf(debris);
        lattice = List.copyOf(lattice);
        roads = List.copyOf(roads);
        stakes = List.copyOf(stakes);
        problems = List.copyOf(problems);
    }

    /** Whether the cell is the hull's own: its walls and its decks. */
    public boolean onHull(int x, int y) {
        return footprint.contains(x, y) && hullCells.get(y * mapWidth + x);
    }

    /** Whether the cell is in the trench the hull ploughed. */
    public boolean inTrench(int x, int y) {
        return trench.contains(x, y) && trenchCells.get(y * mapWidth + x);
    }

    /** The lattice point with this index. */
    public LatticePoint point(int index) {
        for (LatticePoint p : lattice) if (p.index() == index) return p;
        throw new IllegalArgumentException("no lattice point " + index);
    }

    /** The haul road from lattice point {@code index}, or null where it has none. */
    public HaulRoad road(int index) {
        for (HaulRoad r : roads) if (r.point() == index) return r;
        return null;
    }

    /** The name of lattice point {@code index}. */
    public static String latticeName(int index) { return "hull.lattice." + (index + 1); }

    /** Every published place by name. */
    public Map<String, Area> places() {
        Map<String, Area> out = new LinkedHashMap<>();
        out.put(HULL, footprint);
        out.put(TRENCH, trench);
        out.put(FORGE, forgeHall);
        out.put(BAY, bay);
        out.put(INTERIOR, interior.footprint());
        for (int k = 0; k < interior.chambers().size(); k++) out.put(Interior.chamberName(k), interior.chambers().get(k));
        for (var apron : interior.aprons()) out.put(apron.name(), apron.ground());
        for (int k = 0; k < debris.size(); k++) out.put("hull.debris." + (k + 1), debris.get(k));
        for (LatticePoint p : lattice) {
            out.put(latticeName(p.index()), new Area(p.cell().x() - 2, p.cell().y() - 2, p.cell().x() + 2, p.cell().y() + 2));
        }
        return out;
    }
}
