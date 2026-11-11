package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.HaulRoad;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.LatticePoint;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.Stake;
import com.dillon.starsectormarines.battle.world.gen.road.RoadReservation;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Apron;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsRoofStage;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finishes a fallen hull on the finished ground ({@code fallen-hull-nouns.md}, In a battle: Setup): one roof over the
 * hull's solid mass, as the ruins megastructure has ({@link RuinsRoofStage#roofSolid}); the claim lattice on the ground
 * as the country finally grew ({@link ClaimLattice}); the haul roads from every point back to the hull's aprons
 * ({@link HaulRoads}), worn into the ground as trodden earth where an established stake's haulers already walk them; and
 * the patch's starting stakes, each on the lattice point nearest the cell it names. Publishes the {@link HullSite}. Runs last, after the landscape and the road surfaces.
 */
public final class CrashSiteFinishStage implements GenStage {

    /** Cells either side of a haul road's line worn to trodden earth. */
    static final int ROAD_WEAR = 1;

    @Override
    public void run(GenContext ctx) {
        CrashSiteStage.Laid laid = ctx.get(HullKeys.LAID);
        if (laid == null) return;
        RuinsRoofStage.roofSolid(ctx, laid.footprint(), CrashSiteStage.HULL_KIND);
        splitCompartments(ctx, laid);
        CrashSiteStage.dropFields(ctx, laid.hullCells(), laid.trenchCells(), laid.aprons());
        // The landscape strews its loose stones and plants over open ground after the hull is laid: none on its decks or
        // in its trench, which it ploughed bare.
        for (BitSet bare : new BitSet[]{laid.hullCells(), laid.trenchCells()}) {
            for (int k = bare.nextSetBit(0); k >= 0; k = bare.nextSetBit(k + 1)) {
                ctx.topology.setNatureOverlayIndex(k % ctx.width, k / ctx.width, -1);
            }
        }
        // And a stage that dresses the country after the hull (a heart's meadows) may have grassed the trench over: the
        // trench is ploughed earth, last.
        BitSet ploughed = laid.trenchCells();
        for (int k = ploughed.nextSetBit(0); k >= 0; k = ploughed.nextSetBit(k + 1)) {
            int x = k % ctx.width, y = k / ctx.width;
            if (!ctx.grid.isWalkable(x, y) || ctx.topology.isWall(x, y)) continue;
            CellTopology.GroundKind kind = CrashSiteStage.trenchGround(ctx.seed, x, y);
            String block = CrashSiteStage.trenchBlock(kind);
            if (ctx.topology.getGroundKind(x, y) == kind && (block == null
                    || block.equals(ctx.topology.getSurfaceId(x, y)))) continue;
            ctx.topology.setGroundKind(x, y, kind);
            if (block != null) ctx.topology.setSurface(x, y, ctx.topology.surfaceIndex(block));
        }
        int w = ctx.width, h = ctx.height;
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        boolean[][] roads = RoadReservation.mask(ctx.get(BspKeys.ROAD_GRAPH), w, h);
        BitSet hull = laid.hullCells(), trench = laid.trenchCells(), aprons = laid.aprons();
        List<String> problems = new ArrayList<>(laid.problems());

        // The shower falls on the finished country: after the mission's structures and terraces are sited, before the
        // lattice seats its stakes round it.
        List<DamagePlan.Stroke> strikes = DebrisShower.fall(ctx, new CrashSiteStage.Frame(laid.plan().x(), laid.plan().y(), laid.plan().heading()),
                laid.plan().trench(), hull, trench, aprons, FieldOfFire.of(ctx.get(BspKeys.PRECINCTS)));
        ctx.put(HullKeys.STRIKES, strikes);
        ClaimLattice.Ground stakeGround = (x, y) -> {
            if (x < 0 || y < 0 || x >= w || y >= h) return false;
            int k = y * w + x;
            if (hull.get(k) || trench.get(k) || aprons.get(k) || roads[x][y]) return false;
            if (!grid.isWalkable(x, y) || topology.isWall(x, y)
                    || topology.isFixture(x, y) || topology.hasBuildingKindHint(x, y)) return false;
            GroundKind g = topology.getGroundKind(x, y);
            return g != GroundKind.INDOOR && g != GroundKind.WATER && g != GroundKind.STREET;
        };
        Area f = laid.footprint();
        List<LatticePoint> lattice = ClaimLattice.lay(w, h, f.centreX(), f.centreY(), ClaimLattice.SPACING, stakeGround);

        List<Cell> sources = new ArrayList<>();
        for (Apron apron : laid.interior().aprons()) {
            Area g = apron.ground();
            for (int y = g.bottom(); y <= g.top(); y++) for (int x = g.left(); x <= g.right(); x++) sources.add(new Cell(x, y));
        }
        HaulRoads.Walk walk = (x, y) -> !hull.get(y * w + x) && grid.isWalkable(x, y)
                && topology.getGroundKind(x, y) != GroundKind.INDOOR;
        List<HaulRoad> haul = HaulRoads.lay(w, h, walk, sources, lattice);

        List<Stake> stakes = new ArrayList<>();
        Set<Integer> taken = new HashSet<>();
        for (HullSitePlan.StakeAt at : laid.plan().stakes()) {
            LatticePoint best = null;
            long bestD = (long) (ClaimLattice.SPACING / 2) * (ClaimLattice.SPACING / 2);
            for (LatticePoint p : lattice) {
                if (taken.contains(p.index())) continue;
                long dx = p.cell().x() - at.x(), dy = p.cell().y() - at.y(), d = dx * dx + dy * dy;
                if (d <= bestD) {
                    bestD = d;
                    best = p;
                }
            }
            if (best == null) {
                problems.add("no lattice point near the " + at.stage().name().toLowerCase() + " stake at " + at.x() + "," + at.y());
                continue;
            }
            if (haulRoad(haul, best.index()) == null) {
                problems.add("the stake at lattice point " + (best.index() + 1) + " has no haul road back to the hull");
            }
            taken.add(best.index());
            stakes.add(new Stake(best.index(), at.stage()));
        }

        // Only a road a hauler already walks is worn into the ground: an established stake's.
        List<HaulRoad> walked = new ArrayList<>();
        for (Stake stake : stakes) {
            HaulRoad road = haulRoad(haul, stake.point());
            if (stake.stage() == HullSite.StakeStage.ESTABLISHED && road != null) walked.add(road);
        }
        wear(ctx, walked, roads, hull);
        problems.addAll(HullSiteCheck.problems(grid, laid));
        ctx.put(HullKeys.SITE, new HullSite(laid.footprint(), laid.plan().heading(), laid.interior(), laid.forgeHall(),
                laid.forge(), laid.bay(), laid.trench(), laid.debris(), laid.sections(), lattice, haul, stakes, problems, w, hull,
                trench));
    }

    /**
     * One building per compartment ({@code fallen-hull-nouns.md}, The ground): the flood fill finds the hull's deck as
     * one building, joined through its docks, so boarding any of it opened the whole ship. Each building on the hull's
     * deck is split by the compartment its cells are in, so the roof lifts over the compartment a unit stands in and
     * the rest of the ship stays roofed. The first compartment keeps the building's id; the others are numbered on.
     */
    static void splitCompartments(GenContext ctx, CrashSiteStage.Laid laid) {
        Buildings buildings = ctx.get(BspKeys.BUILDINGS);
        if (buildings == null || buildings.isEmpty()) return;
        Buildings split = splitCompartments(buildings, ctx.topology, laid.compartments(), laid.compartmentNames().size());
        if (split != buildings) ctx.put(BspKeys.BUILDINGS, split);
    }

    /**
     * {@code buildings} with every building on a compartment's deck split by compartment ({@code compartments} per map
     * cell, -1 for none, {@code count} of them), cell ids rewritten on {@code topology}; {@code buildings} itself when
     * no building is on the deck.
     */
    static Buildings splitCompartments(Buildings buildings, CellTopology topology, int[] compartments, int count) {
        int w = topology.getWidth();
        int nextId = 1;
        for (Building b : buildings.all()) nextId = Math.max(nextId, b.id + 1);
        Buildings split = new Buildings();
        boolean changed = false;
        for (Building b : buildings.all()) {
            int home = -1;
            for (int i = 0; i < b.cellCount() && home < 0; i++) home = compartments[b.cellsY[i] * w + b.cellsX[i]];
            if (home < 0) {
                split.add(b);
                continue;
            }
            List<List<int[]>> parts = new ArrayList<>();
            for (int k = 0; k < count; k++) parts.add(new ArrayList<>());
            for (int i = 0; i < b.cellCount(); i++) {
                int c = compartments[b.cellsY[i] * w + b.cellsX[i]];
                // A cell of the building off every compartment (none on a pooled seed) goes with its first compartment.
                parts.get(c < 0 ? home : c).add(new int[]{b.cellsX[i], b.cellsY[i]});
            }
            boolean first = true;
            for (List<int[]> part : parts) {
                if (part.isEmpty()) continue;
                int id = first ? b.id : nextId++;
                first = false;
                split.add(building(topology, id, b, part));
            }
            changed = true;
        }
        return changed ? split : buildings;
    }

    private static Building building(CellTopology topology, int id, Building of, List<int[]> cells) {
        int n = cells.size();
        int[] xs = new int[n], ys = new int[n];
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            int x = cells.get(i)[0], y = cells.get(i)[1];
            xs[i] = x;
            ys[i] = y;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            topology.setBuildingId(x, y, id);
        }
        return new Building(id, of.kind, minX, maxX, minY, maxY, xs, ys, of.tintR, of.tintG, of.tintB);
    }

    private static HaulRoad haulRoad(List<HaulRoad> roads, int point) {
        for (HaulRoad r : roads) if (r.point() == point) return r;
        return null;
    }

    /**
     * The haul roads worn into the ground: open outdoor cells along each road and {@link #ROAD_WEAR} either side become
     * trodden earth. Paving, floors, water and the trench keep their own ground; nothing about passage changes.
     */
    private static void wear(GenContext ctx, List<HaulRoad> haul, boolean[][] roads, BitSet hull) {
        CellTopology topology = ctx.topology;
        int trodden = topology.surfaceIndex(CrashSiteStage.TRODDEN);
        int w = ctx.width, h = ctx.height;
        boolean[] done = new boolean[w * h];
        for (HaulRoad road : haul) {
            for (Cell c : road.cells()) {
                for (int dy = -ROAD_WEAR; dy <= ROAD_WEAR; dy++) {
                    for (int dx = -ROAD_WEAR; dx <= ROAD_WEAR; dx++) {
                        int x = c.x() + dx, y = c.y() + dy;
                        if (x < 0 || y < 0 || x >= w || y >= h || done[y * w + x]) continue;
                        done[y * w + x] = true;
                        if (hull.get(y * w + x) || roads[x][y] || !ctx.grid.isWalkable(x, y)
                                || topology.hasBuildingKindHint(x, y)) continue;
                        GroundKind g = topology.getGroundKind(x, y);
                        if (g != GroundKind.GRASS && g != GroundKind.DIRT && g != GroundKind.SAND
                                && g != GroundKind.SNOW && g != GroundKind.STONE) continue;
                        topology.setGroundKind(x, y, GroundKind.DIRT);
                        topology.setSurface(x, y, trodden);
                    }
                }
            }
        }
    }
}
