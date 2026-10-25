package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * How each {@link CordonConstruction} other than the generated compound and shed is built on a redoubt's or the
 * base's ground ({@code cordon-nouns.md}, Field redoubt, Logistics base), from production building stamps, walls,
 * sandbags, fences, hedgehogs, crate stacks and the auto shop's gantry pieces through {@link CordonWorks}.
 *
 * <p>A plan is written once in a local frame ({@link Frame}: {@code u} across the line, {@code v} toward the front)
 * and turned half round on a map whose front is south. Each builder leaves what the garrison needs in a {@link Built}:
 * the cells its guns are seated on and the cells its wall teams stand behind sandbags. A base builder leaves its
 * gantries as machine berths with the cells they are serviced from.
 */
final class CordonBuilders {

    private CordonBuilders() {}

    /** Half the side of a redoubt's ground, from its middle: the generated compound's wall box is 25 across. */
    static final int R = 12;
    static final String MASONRY = "colony.masonry-wall";
    static final String CERAMIC = "colony.ceramic-wall";
    static final String BLAST = InstallationWalls.BUILDING;
    static final String HEDGEHOG = "doodad.fort-hedgehog";
    static final String CRATES = "doodad.industrial-crate-stack";

    /**
     * One machine berth a base builder laid: the gantry, the cells it is serviced from (each {x, y, fx, fy}), and
     * the bench work beside it (its tool chest, diagnostic and service run), which a technician's watch rotates
     * through as it does a shed's stores, terminals and benches.
     */
    record Berth(Gantry gantry, List<int[]> service, List<FixtureTask> work) { }

    /**
     * What a builder leaves for the battle: where it is centred (for the look), the gun cells, the cells behind
     * sandbags its teams stand at, and for a base the gantries it laid with the room they share.
     */
    record Built(float focusU, float focusV, List<int[]> guns, List<int[]> posts, List<Berth> berths,
                 List<int[]> bayCells) {
        static Built at(float u, float v) {
            return new Built(u, v, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        Built focusedOn(float u, float v) {
            return new Built(u, v, guns, posts, berths, bayCells);
        }
    }

    /** The ground a construction stands on: the generated works' bounds, and the way to the front. */
    record Site(int x0, int y0, int x1, int y1, int front) {
        int cx() { return (x0 + x1) / 2; }
        int cy() { return (y0 + y1) / 2; }
    }

    /** A road coming in to the works: which side, and its span along that side in local cells. */
    record Entry(char side, int lo, int hi) { }

    interface Builder {
        Built build(CordonWorks works, Frame frame, Site site);
    }

    /** The builder of a construction, or {@code null} for the generated compound and shed. */
    static Builder of(CordonConstruction construction) {
        return switch (construction) {
            case FARM -> CordonBuilders::farm;
            case SCHOOL -> CordonBuilders::school;
            case BLOCK -> CordonBuilders::block;
            case TENEMENT -> CordonBuilders::tenement;
            case WAREHOUSE -> CordonBuilders::warehouse;
            case CAR_PARK -> CordonBuilders::carPark;
            case GOODS_YARD -> CordonBuilders::goodsYard;
            case COMPOUND, SHED -> null;
        };
    }

    // ---------------------------------------------------------------- the redoubts

    /**
     * Town: a fortified farm. A farmhouse and a barn make the front face, a second barn the flank; a stone yard wall
     * closes the rest round a worked-earth farmyard, with a sandbagged gun nest inside each gate.
     */
    static Built farm(CordonWorks w, Frame f, Site site) {
        List<Entry> entries = entries(w, f, R + 1);
        CordonWorks.Ground grass = w.sample(site.cx(), site.cy(), GroundKind.GRASS);
        f.clear(w, -R - 3, -R - 3, R + 3, R + 3, grass, null);
        Built built = Built.at(0f, 2f);
        for (int v = -R + 1; v <= 3; v++) for (int u = -R + 1; u <= R - 1; u++) f.worked(w, u, v);
        f.stamp(w, BuildingStamp.FARMHOUSE, -R, 5, 0);
        f.stamp(w, BuildingStamp.BARN, -1, 4, 0);
        f.stamp(w, BuildingStamp.BARN, -R, -R + 1, 1);
        // The yard wall: the rear and the open flank, the gap between the front buildings, the flank's gaps.
        f.wall(w, MASONRY, -R, -R, R, -R, gaps(entries, 'R'));
        f.wall(w, MASONRY, R, -R, R, R - 1, gaps(entries, 'E'));
        f.wall(w, MASONRY, -3, R - 1, -2, R - 1);
        f.wall(w, MASONRY, -R, 2, -R, 4);
        gateNests(w, f, entries, built, 'R', 'E');
        sealDoors(w, f, entries);
        frontNests(w, f, built);
        tracks(w, f, entries);
        // Farm stores and the farm's own truck in the yard.
        f.obstacle(w, 4, -2, "doodad.industrial-pallet-stack");
        f.obstacle(w, 5, -2, "doodad.industrial-pallet-stack");
        f.obstacle(w, 4, -3, "doodad.industrial-pallet-stack");
        f.prop(w, 8, 0, "main-stores.drums", 0, true);
        f.prop(w, -3, -6, "doodad.parked-flatbed-truck", 1, true);
        return built;
    }

    /**
     * Town: a fortified school. The school's courtyard block is the front face, its hall the flank; a ceramic yard
     * wall closes the paved playground behind them, sandbags line the playground's open front, and a sandbagged gun
     * nest stands inside each gate.
     */
    static Built school(CordonWorks w, Frame f, Site site) {
        List<Entry> entries = entries(w, f, R + 1);
        CordonWorks.Ground grass = w.sample(site.cx(), site.cy(), GroundKind.GRASS);
        f.clear(w, -R - 3, -R - 3, R + 3, R + 3, grass, null);
        Built built = Built.at(0f, 2f);
        CordonWorks.Ground paving = new CordonWorks.Ground(GroundKind.COURTYARD,
                w.topology.surfaceIndex("colony.terracotta"), -1);
        f.floor(w, -R + 1, -R + 1, R - 1, R - 2, paving);
        f.stamp(w, BuildingStamp.COURT_HOUSE, -6, 2, 0);
        f.stamp(w, BuildingStamp.MARKET_HALL, -R, -R + 1, 1);
        f.wall(w, CERAMIC, -R, R - 1, -7, R - 1);
        f.wall(w, CERAMIC, 7, R - 1, R, R - 1);
        f.wall(w, CERAMIC, -R, 2, -R, R - 2);
        f.wall(w, CERAMIC, -R, -R, R, -R, gaps(entries, 'R'));
        f.wall(w, CERAMIC, R, -R + 1, R, R - 2, gaps(entries, 'E'));
        gateNests(w, f, entries, built, 'R', 'E');
        sealDoors(w, f, entries);
        // The playground's firing step: sandbags inside the front wall's open stretches, facing the front.
        for (int u = 8; u <= R - 1; u++) if (f.sandbag(w, u, R - 2, 0, 1)) built.posts().add(f.cell(u, R - 2));
        for (int u = -R + 1; u <= -8; u++) if (f.sandbag(w, u, R - 2, 0, 1)) built.posts().add(f.cell(u, R - 2));
        frontNests(w, f, built);
        tracks(w, f, entries);
        return built;
    }

    /**
     * Urban: the buildings round a crossroads walled up. A building on each corner of the crossing, their outer
     * faces the redoubt's face; a blast wall across each street arm closes the gaps, a gate in it on the road with a
     * gun beside it in the wall's own line and hedgehogs in front.
     */
    static Built block(CordonWorks w, Frame f, Site site) {
        int[] crossing = crossing(w, site);
        Frame c = new Frame(crossing[0], crossing[1], f.f);
        int a = Math.max(1, (crossing[3] - crossing[2] + 1) / 2), b = Math.max(1, (crossing[5] - crossing[4] + 1) / 2);
        CordonWorks.Ground pavement = w.sample(crossing[0], crossing[1], GroundKind.SIDEWALK);
        int reach = 19;
        c.clear(w, -reach, -reach, reach, reach, pavement, (x, y) -> road(w, x, y)
                && (inBand(x, crossing[2], crossing[3]) || inBand(y, crossing[4], crossing[5])));
        Built built = Built.at(0f, 0f);
        int off = 2;
        // Corner buildings: each one's near corner a pavement's width off both roads.
        int[] ne = place(w, c, BuildingStamp.MARKET_HALL, 0, a + off, b + off, 1, 1);
        int[] nw = place(w, c, BuildingStamp.CORNER_HOME, 0, -a - off, b + off, -1, 1);
        int[] se = place(w, c, BuildingStamp.CORNER_SHOP, 2, a + off, -b - off, 1, -1);
        int[] sw = place(w, c, BuildingStamp.OFFSET_DEPOT, 2, -a - off, -b - off, -1, -1);
        // Each building's local bounds {u0, v0, u1, v1}. An arm's wall runs where both its buildings stand.
        int frontV = Math.min(ne[3], nw[3]), rearV = Math.max(se[1], sw[1]);
        int eastU = Math.min(ne[2], se[2]), westU = Math.max(nw[0], sw[0]);
        int gate = 1, gun = gate + 1;
        // A double blast wall across each arm, a gate on the road; beside each gate a gun in the wall's own line,
        // the way the compound's gun towers stand in theirs.
        for (int d = 0; d < 2; d++) {
            int[] tower = d == 0 ? new int[]{gun, gun} : new int[]{-gate, gate};
            int[] rearTower = d == 0 ? new int[]{-gun, -gun} : new int[]{-gate, gate};
            c.wall(w, BLAST, nw[2] + 1, frontV - d, ne[0] - 1, frontV - d, new int[]{-gate, gate}, tower);
            c.wall(w, BLAST, sw[2] + 1, rearV + d, se[0] - 1, rearV + d, new int[]{-gate, gate}, rearTower);
            c.wall(w, BLAST, eastU - d, se[3] + 1, eastU - d, ne[1] - 1, new int[]{-gate, gate}, rearTower);
            c.wall(w, BLAST, westU + d, sw[3] + 1, westU + d, nw[1] - 1, new int[]{-gate, gate}, tower);
        }
        built.guns().add(c.cell(gun, frontV));
        built.guns().add(c.cell(-gun, rearV));
        built.guns().add(c.cell(eastU, -gun));
        built.guns().add(c.cell(westU, gun));
        // Hedgehogs staggered outside each gate: a man walks through, a column has to slow for them.
        for (int k : new int[]{-1, 1}) {
            c.obstacle(w, k * 2, frontV + 3 + (k > 0 ? 2 : 0), HEDGEHOG);
            c.obstacle(w, k * 2, rearV - 3 - (k > 0 ? 2 : 0), HEDGEHOG);
            c.obstacle(w, eastU + 3 + (k > 0 ? 2 : 0), k * 2, HEDGEHOG);
            c.obstacle(w, westU - 3 - (k > 0 ? 2 : 0), k * 2, HEDGEHOG);
        }
        int[] at = c.cell(0, 0);
        return built.focusedOn(f.u(at[0], at[1]), f.v(at[0], at[1]) + 2f);
    }

    /**
     * Urban: a single fortified apartment block. Four courtyard tenements built as one block across the road behind
     * the crossing, a light well between its two rows; sandbags along the pavement in front, a gun nest at each front
     * corner, and hedgehogs across the street arms it covers.
     */
    static Built tenement(CordonWorks w, Frame f, Site site) {
        int[] crossing = crossing(w, site);
        Frame c = new Frame(crossing[0], crossing[1], f.f);
        int b = Math.max(1, (crossing[5] - crossing[4] + 1) / 2);
        CordonWorks.Ground pavement = w.sample(crossing[0], crossing[1], GroundKind.SIDEWALK);
        int top = -b - 3;
        c.clear(w, -17, top - 25, 16, top + 1, pavement, (x, y) -> road(w, x, y) && inBand(y, crossing[4], crossing[5]));
        Built built = Built.at(0f, 0f);
        c.stamp(w, BuildingStamp.COURT_HOUSE, -13, top - 9, 0);
        c.stamp(w, BuildingStamp.COURT_HOUSE, 0, top - 9, 0);
        c.stamp(w, BuildingStamp.COURT_HOUSE, -13, top - 21, 2);
        c.stamp(w, BuildingStamp.COURT_HOUSE, 0, top - 21, 2);
        // The light well's ends walled.
        c.wall(w, BLAST, -13, top - 11, -13, top - 10);
        c.wall(w, BLAST, 12, top - 11, 12, top - 10);
        // Sandbags along the pavement in front of the block, facing the street.
        for (int u = -12; u <= 11; u++) if (c.sandbag(w, u, top + 1, 0, 1)) built.posts().add(c.cell(u, top + 1));
        nest(w, c, built, -15, top, 0, 1);
        nest(w, c, built, 14, top, 0, 1);
        // Hedgehogs across the arm in front and along the line's road either side.
        for (int k = -2; k <= 2; k += 2) c.obstacle(w, k, b + 4 + (k == 0 ? 1 : 0), HEDGEHOG);
        for (int k = -b; k <= b; k += 2) {
            c.obstacle(w, -19, k, HEDGEHOG);
            c.obstacle(w, 18, k, HEDGEHOG);
        }
        int[] at = c.cell(0, top - 6);
        return built.focusedOn(f.u(at[0], at[1]), f.v(at[0], at[1]));
    }

    // ---------------------------------------------------------------- the base

    /**
     * Urban base: a depot in a warehouse block. Two freight warehouses on the front side of the road, their loading
     * doors on an apron where the trucks stand; behind the road a yard with two open-air mech gantries (the auto
     * shop's bay between lift columns), the depot office and its stores; an industrial fence round it all.
     */
    static Built warehouse(CordonWorks w, Frame f, Site site) {
        int[] road = roadRows(w, f, site);
        int hu = (site.x1() - site.x0()) / 2, hv = (site.y1() - site.y0()) / 2;
        CordonWorks.Ground yard = w.sample(site.cx(), site.cy(), GroundKind.SIDEWALK);
        f.clear(w, -hu - 1, -hv - 1, hu + 1, hv + 1, yard, (x, y) -> street(w, x, y) && inBand(f.v(x, y), road[0], road[1]));
        Built built = Built.at(0f, road[0] - 3f);
        // The warehouses, doors on the apron by the road.
        int apron = road[1] + 1;
        f.stamp(w, BuildingStamp.FREIGHT_L, -hu + 2, apron + 4, 0);
        f.stamp(w, BuildingStamp.FREIGHT_T, 2, apron + 4, 0);
        for (int u : new int[]{-hu + 4, -hu + 8, 4, 8, 14}) f.prop(w, u, apron, "doodad.parked-cargo-truck", 0, true);
        // Behind the road: the gantry yard.
        int v0 = road[0] - 12;
        gantries(w, f, v0, built);
        f.stamp(w, BuildingStamp.OFFSET_DEPOT, hu - 12, road[0] - 15, 2);
        for (int u = -hu + 3; u <= -hu + 8; u++) f.obstacle(w, u, road[0] - 17, "doodad.industrial-pallet-stack");
        f.prop(w, -hu + 3, v0 + 2, "main-stores.cases", 0, true);
        f.prop(w, -hu + 3, v0 + 5, "main-stores.drums", 0, true);
        f.prop(w, -hu + 6, v0 + 2, "main-stores.pallet", 0, true);
        fenceRing(w, f, hu, hv, road);
        return built;
    }

    /**
     * Urban base: a depot in a car park. The lot under asphalt, trucks and vans parked in rows on the front side of
     * the road, each bay's head marked; behind it two open-air gantries, a site office and stacked stores; a stockade
     * of stacked crates round it with sandbagged ways in.
     */
    static Built carPark(CordonWorks w, Frame f, Site site) {
        int[] road = roadRows(w, f, site);
        int hu = (site.x1() - site.x0()) / 2, hv = (site.y1() - site.y0()) / 2;
        CordonWorks.Ground asphalt = new CordonWorks.Ground(GroundKind.COURTYARD,
                w.topology.surfaceIndex("pavement.asphalt.worn"), -1);
        f.clear(w, -hu - 1, -hv - 1, hu + 1, hv + 1, asphalt, (x, y) -> street(w, x, y) && inBand(f.v(x, y), road[0], road[1]));
        Built built = Built.at(0f, road[0] - 3f);
        String[] vehicles = {"doodad.parked-cargo-truck", "doodad.parked-box-van", "doodad.parked-flatbed-truck",
                "doodad.parked-utility-truck", "doodad.parked-tanker-truck", "doodad.parked-armored-apc"};
        int n = 0;
        // Parking rows on the front side of the road, each bay's head marked.
        for (int row = 0; row < 3; row++) {
            int v = road[1] + 3 + row * 6;
            for (int k = 0, u = -hu + 3; u + 1 <= hu - 3; u += 3, k++) {
                f.prop(w, u, v + 3, "doodad.fl-striped-yellow", 0, false);
                f.prop(w, u + 1, v + 3, "doodad.fl-striped-yellow", 0, false);
                if ((k * 7 + row * 3) % 5 != 0) f.prop(w, u, v, vehicles[n++ % vehicles.length], 1, true);
            }
        }
        // Behind the road: the gantries in the open, the site office and the stores.
        int v0 = road[0] - 12;
        gantries(w, f, v0, built);
        f.stamp(w, BuildingStamp.CORNER_SHOP, hu - 12, road[0] - 15, 2);
        for (int u = -hu + 3; u <= -hu + 9; u += 2) {
            f.prop(w, u, road[0] - 18, "main-stores.cases", 0, true);
            f.prop(w, u, road[0] - 21, "main-stores.cartons", 0, true);
        }
        stockade(w, f, hu, hv, road);
        return built;
    }

    /** Rows either side of the middle a goods yard's own drive takes, where no road crosses the yard. */
    static final int YARD_DRIVE = 1;

    /**
     * Town base: the town's goods yard. The goods shed on the front side of the road, its loading doors on an apron
     * where flatbeds and cargo trucks stand beside a cargo loader, the freight waiting to go out stacked in rows along
     * it; behind the road the two open-air gantries, the yard's workshop and its stores; an industrial fence round it.
     * Where the main road only reaches the gate, the yard's own drive takes its place across the middle.
     */
    static Built goodsYard(CordonWorks w, Frame f, Site site) {
        int[] main = roadRows(w, f, site);
        // A town's main road may reach the gate without crossing the yard: then the yard's own drive crosses its middle.
        int[] road = main[1] - main[0] > (site.y1() - site.y0()) / 2 ? new int[]{-YARD_DRIVE, YARD_DRIVE} : main;
        int hu = (site.x1() - site.x0()) / 2, hv = (site.y1() - site.y0()) / 2;
        CordonWorks.Ground yard = w.sample(site.cx(), site.cy(), GroundKind.SIDEWALK);
        f.clear(w, -hu - 1, -hv - 1, hu + 1, hv + 1, yard, (x, y) -> street(w, x, y) && inBand(f.v(x, y), road[0], road[1]));
        Built built = Built.at(0f, road[0] - 3f);
        // The goods shed, its doors on the apron by the road, and the freight waiting beside it: the shed on the west
        // half where it stands, else the east, else either turned narrower (the ammo dump a beat laid keeps its
        // ground), the freight on the other half.
        int apron = road[1] + 1;
        int side = -1, shed0 = -hu + 2;
        shed:
        for (int turns : new int[]{0, 1}) {
            int width = CordonWorks.size(BuildingStamp.FREIGHT_L, turns)[0];
            for (int s : new int[]{-1, 1}) {
                int u0 = s < 0 ? -hu + 2 : hu - 1 - width;
                if (f.stamp(w, BuildingStamp.FREIGHT_L, u0, apron + 4, turns)) {
                    side = s;
                    shed0 = u0;
                    break shed;
                }
            }
        }
        String[] trucks = {"doodad.parked-flatbed-truck", "doodad.parked-cargo-truck", "doodad.parked-flatbed-truck"};
        for (int k = 0; k < trucks.length; k++) f.prop(w, shed0 + 2 + 5 * k, apron, trucks[k], 0, true);
        int loader = side < 0 ? 6 : -9;
        f.prop(w, loader, apron + 1, "doodad.stores-cargo-loader", 0, true);
        int f0 = side < 0 ? 6 : -hu + 4, f1 = side < 0 ? hu - 4 : -6;
        for (int row = 0; row < 3; row++) {
            int v = apron + 5 + row * 4;
            for (int u = f0; u <= f1; u += 3) {
                f.obstacle(w, u, v, Math.floorMod(u + row, 2) == 0 ? "doodad.industrial-pallet-stack" : CRATES);
            }
        }
        f.prop(w, side < 0 ? hu - 4 : -hu + 3, apron + 1, "doodad.industrial-drum-cluster", 0, true);
        // Behind the road: the gantries, the yard's workshop and its stores.
        int v0 = road[0] - 12;
        gantries(w, f, v0, built);
        f.stamp(w, BuildingStamp.T_WORKSHOP, hu - 12, road[0] - 15, 2);
        f.prop(w, -hu + 3, v0 + 2, "main-stores.cases", 0, true);
        f.prop(w, -hu + 3, v0 + 5, "main-stores.drums", 0, true);
        f.prop(w, -hu + 6, v0 + 2, "main-stores.pallet", 0, true);
        fenceRing(w, f, hu, hv, road);
        return built;
    }

    // ---------------------------------------------------------------- pieces

    /** Local {@code u} of the base's two gantries' left edges. */
    static final int[] GANTRY_U = {-12, -1};
    /** The auto shop's bay: the berth a machine stands in, {@link #BAY_W} across and {@link #BAY_D} deep. */
    static final int BAY_W = 5, BAY_D = 7;

    /**
     * The base's two open-air gantries, side by side behind the road at local row {@code v0}, and the one vehicle bay
     * they share: one machine on the stocks at a time, as in the shed.
     */
    static void gantries(CordonWorks w, Frame f, int v0, Built built) {
        for (int u0 : GANTRY_U) gantry(w, f, u0, v0, built);
        // The bay the gantries share: their bays, columns and the ground they are worked from, as one room.
        for (int v = v0 - 1; v <= v0 + BAY_D; v++) {
            for (int u = GANTRY_U[0] - 2; u <= GANTRY_U[1] + BAY_W; u++) built.bayCells().add(f.cell(u, v));
        }
    }

    /**
     * A gantry: the auto shop's bay floor between four lift columns, a service run and a tool chest beside it. Its
     * berth faces the road (local +v); it is serviced from either flank and from its head.
     */
    static void gantry(CordonWorks w, Frame f, int u0, int v0, Built built) {
        f.prop(w, u0, v0, "auto-shop.bay", 0, false);
        for (int du : new int[]{-1, BAY_W}) for (int dv : new int[]{-1, BAY_D}) f.prop(w, u0 + du, v0 + dv, "auto-shop.lift-column", 0, true);
        f.prop(w, u0 + BAY_W + 1, v0, "auto-shop.service-run", 0, true);
        f.prop(w, u0 - 2, v0 + 2, "auto-shop.tool-chest", 0, true);
        f.prop(w, u0 - 2, v0 + 4, "auto-shop.diagnostic", 0, true);
        int[] a = f.cell(u0, v0), b = f.cell(u0 + BAY_W - 1, v0 + BAY_D - 1);
        Gantry gantry = Gantry.covering(Math.min(a[0], b[0]), Math.min(a[1], b[1]), BAY_W, BAY_D,
                Gantry.Facing.of(0, f.f), Gantry.Holds.MACHINE);
        List<int[]> service = new ArrayList<>();
        int mid = BAY_D / 2;
        int[][] stands = {{u0 - 1, v0 + mid, u0, v0 + mid}, {u0 + BAY_W, v0 + mid, u0 + BAY_W - 1, v0 + mid},
                {u0 + BAY_W / 2, v0 - 1, u0 + BAY_W / 2, v0}};
        for (int[] s : stands) {
            int[] at = f.cell(s[0], s[1]), on = f.cell(s[2], s[3]);
            // A point stands on open floor: a crate, a wall or a fence the yard kept there leaves that side unworked.
            if (!w.grid.inBounds(at[0], at[1]) || !w.grid.isWalkable(at[0], at[1]) || w.topology.isFixture(at[0], at[1])
                    || w.topology.isWall(at[0], at[1])) continue;
            service.add(new int[]{at[0], at[1], on[0], on[1]});
        }
        // The rest of a technician's watch: without it every hand would weld all shift, four times a shed's pace.
        List<FixtureTask> work = new ArrayList<>();
        work(w, f, work, u0 - 1, v0 + 2, u0 - 2, v0 + 2, Affordance.STOW);
        work(w, f, work, u0 - 1, v0 + 4, u0 - 2, v0 + 4, Affordance.READOUT);
        work(w, f, work, u0 + BAY_W, v0 + 1, u0 + BAY_W + 1, v0 + 1, Affordance.REPAIR);
        built.berths().add(new Berth(gantry, service, work));
    }

    /** A work point standing at {@code (u, v)} on the fixture at {@code (fu, fv)}, where both are there. */
    private static void work(CordonWorks w, Frame f, List<FixtureTask> out, int u, int v, int fu, int fv,
                             Affordance job) {
        int[] at = f.cell(u, v), on = f.cell(fu, fv);
        if (!w.grid.inBounds(at[0], at[1]) || !w.grid.isWalkable(at[0], at[1]) || w.topology.isFixture(at[0], at[1])
                || w.topology.isWall(at[0], at[1]) || !w.grid.inBounds(on[0], on[1])
                || !w.topology.isFixture(on[0], on[1])) return;
        out.add(FixtureTask.at(at[0], at[1], job, on[0], on[1]));
    }

    /** A sandbagged gun nest: the gun at {@code (u, v)}, bags across the side facing {@code (du, dv)} and its flanks. */
    static void nest(CordonWorks w, Frame f, Built built, int u, int v, int du, int dv) {
        int pu = dv, pv = du;
        for (int k = -1; k <= 1; k++) f.sandbag(w, u + du + pu * k, v + dv + pv * k, du, dv);
        f.sandbag(w, u - pu, v - pv, -pu, -pv);
        f.sandbag(w, u + pu, v + pv, pu, pv);
        built.guns().add(f.cell(u, v));
    }

    /** A gun nest inside each gate on the named sides, beside the way in. */
    static void gateNests(CordonWorks w, Frame f, List<Entry> entries, Built built, char... sides) {
        for (Entry e : entries) {
            boolean wanted = false;
            for (char s : sides) wanted |= e.side() == s;
            if (!wanted) continue;
            for (int beside : new int[]{e.hi() + 3, e.lo() - 3}) {
                int[] at = switch (e.side()) {
                    case 'R' -> new int[]{beside, -R + 2, 0, -1};
                    case 'F' -> new int[]{beside, R - 2, 0, 1};
                    case 'E' -> new int[]{R - 2, beside, 1, 0};
                    default -> new int[]{-R + 2, beside, -1, 0};
                };
                if (!f.roomFor(w, at[0], at[1])) continue;
                nest(w, f, built, at[0], at[1], at[2], at[3]);
                break;
            }
        }
    }

    /**
     * A gun nest facing the front where the front face leaves room for one, so a farm or a school with no road at
     * its rear or flank still has a gun on the side the swarm comes from: the compound always has two towers.
     */
    static void frontNests(CordonWorks w, Frame f, Built built) {
        // The nearest the front a nest fits, the outer ends of the face first: the gun sees past the buildings' ends.
        for (int v = R - 2; v >= 0 && built.guns().size() < CordonPlan.REDOUBT_GATES; v--) {
            for (int k = R - 2; k >= 0 && built.guns().size() < CordonPlan.REDOUBT_GATES; k--) {
                for (int u : new int[]{k, -k}) {
                    if (built.guns().size() >= CordonPlan.REDOUBT_GATES || !f.roomFor(w, u, v)) continue;
                    boolean near = false;
                    for (int[] g : built.guns()) near |= Math.abs(f.u(g[0], g[1]) - u) < 8 && Math.abs(f.v(g[0], g[1]) - v) < 8;
                    if (!near) nest(w, f, built, u, v, 0, 1);
                }
            }
        }
    }

    /** Worked-earth tracks from each road coming in to the middle of the yard. */
    static void tracks(CordonWorks w, Frame f, List<Entry> entries) {
        for (Entry e : entries) {
            for (int t = R + 3; t >= 0; t--) {
                for (int k = e.lo(); k <= e.hi(); k++) {
                    switch (e.side()) {
                        case 'R' -> f.worked(w, k, -t);
                        case 'F' -> f.worked(w, k, t);
                        case 'E' -> f.worked(w, t, k);
                        default -> f.worked(w, -t, k);
                    }
                }
            }
        }
    }

    /** An industrial fence on the base's edge, open where the roads come in. */
    static void fenceRing(CordonWorks w, Frame f, int hu, int hv, int[] road) {
        List<Entry> entries = entries(w, f, Math.max(hu, hv) + 2);
        for (int u = -hu; u <= hu; u++) {
            if (!open(entries, 'F', u)) f.fence(w, u, hv, true);
            if (!open(entries, 'R', u)) f.fence(w, u, -hv, true);
        }
        for (int v = -hv + 1; v <= hv - 1; v++) {
            if (inBand(v, road[0] - 1, road[1] + 1)) continue;
            if (!open(entries, 'E', v)) f.fence(w, hu, v, false);
            if (!open(entries, 'W', v)) f.fence(w, -hu, v, false);
        }
    }

    /** A stockade of stacked crates on the base's edge, sandbags either side of each way in. */
    static void stockade(CordonWorks w, Frame f, int hu, int hv, int[] road) {
        List<Entry> entries = entries(w, f, Math.max(hu, hv) + 2);
        for (int u = -hu; u <= hu; u++) {
            stack(w, f, entries, 'F', u, u, hv, 0, 1);
            stack(w, f, entries, 'R', u, u, -hv, 0, -1);
        }
        for (int v = -hv + 1; v <= hv - 1; v++) {
            if (inBand(v, road[0] - 1, road[1] + 1)) {
                if (v == road[0] - 1 || v == road[1] + 1) {
                    f.sandbag(w, hu - 1, v, 1, 0);
                    f.sandbag(w, -hu + 1, v, -1, 0);
                }
                continue;
            }
            stack(w, f, entries, 'E', v, hu, v, 1, 0);
            stack(w, f, entries, 'W', v, -hu, v, -1, 0);
        }
    }

    private static void stack(CordonWorks w, Frame f, List<Entry> entries, char side, int along, int u, int v,
                              int du, int dv) {
        if (open(entries, side, along)) return;
        if (open(entries, side, along - 1) || open(entries, side, along + 1)) {
            f.sandbag(w, u - du, v - dv, du, dv);
            return;
        }
        f.obstacle(w, u, v, CRATES);
    }

    private static boolean open(List<Entry> entries, char side, int along) {
        for (Entry e : entries) if (e.side() == side && along >= e.lo() - 1 && along <= e.hi() + 1) return true;
        return false;
    }

    /**
     * Places a stamp in a corner of the crossing: its near corner at local {@code (u, v)}, growing away along
     * {@code (su, sv)}. Returns its local bounds {@code {u0, v0, u1, v1}}.
     */
    static int[] place(CordonWorks w, Frame c, BuildingStamp stamp, int turns, int u, int v, int su, int sv) {
        int[] size = CordonWorks.size(stamp, turns);
        int u0 = su > 0 ? u : u - size[0] + 1, v0 = sv > 0 ? v : v - size[1] + 1;
        c.stamp(w, stamp, u0, v0, turns);
        return new int[]{u0, v0, u0 + size[0] - 1, v0 + size[1] - 1};
    }

    // ---------------------------------------------------------------- the ground

    /** Whether the cell is road: a street, or hard pavement the road surface laid. */
    static boolean road(CordonWorks w, int x, int y) {
        if (!w.grid.inBounds(x, y) || w.topology.isWall(x, y)) return false;
        if (w.topology.isStreet(x, y)) return true;
        RoadSurface surface = w.topology.roadSurface();
        return surface != null && surface.underlay(x, y) != null && surface.underlay(x, y).hard();
    }

    /**
     * Whether the cell is the carriageway itself, wholly under asphalt: a base's yard keeps its main road through it
     * and paves everything else over, the pavements, the kerbs and the corner returns of the side streets that met
     * it, so no strip or fragment of a pavement is left standing in the lot.
     */
    static boolean street(CordonWorks w, int x, int y) {
        if (!w.grid.inBounds(x, y) || w.topology.isWall(x, y) || !w.topology.isStreet(x, y)) return false;
        RoadSurface surface = w.topology.roadSurface();
        if (surface == null) return true;
        RoadSurface.Patch asphalt = surface.patch(x, y, RoadPlan.Material.ASPHALT.ordinal());
        return asphalt != null && asphalt.area() >= WHOLE_CELL;
    }

    /** A patch's share of its cell that counts as the whole cell, for rounding. */
    static final double WHOLE_CELL = 0.999;

    static boolean inBand(int value, int lo, int hi) {
        return value >= Math.min(lo, hi) && value <= Math.max(lo, hi);
    }

    /**
     * The crossing nearest the site: the road column and road row through the site's ground with the longest runs
     * of road. Returns {@code {x, y, colLo, colHi, rowLo, rowHi}}.
     */
    static int[] crossing(CordonWorks w, Site site) {
        int pad = 14;
        int x0 = site.x0() - pad, x1 = site.x1() + pad, y0 = site.y0() - pad, y1 = site.y1() + pad;
        int[] col = band(w, x0, x1, y0, y1, true, 0.7);
        int[] row = band(w, y0, y1, x0, x1, false, 0.7);
        return new int[]{(col[0] + col[1]) / 2, (row[0] + row[1]) / 2, col[0], col[1], row[0], row[1]};
    }

    /** How many cells of road the crossing's busiest column and row each hold over the site's padded ground. */
    static int[] crossingRoad(CordonWorks w, Site site) {
        int pad = 14;
        int x0 = site.x0() - pad, x1 = site.x1() + pad, y0 = site.y0() - pad, y1 = site.y1() + pad;
        return new int[]{most(w, x0, x1, y0, y1, true), most(w, y0, y1, x0, x1, false)};
    }

    private static int most(CordonWorks w, int a0, int a1, int b0, int b1, boolean columns) {
        int best = 0;
        for (int a = a0; a <= a1; a++) {
            int n = 0;
            for (int b = b0; b <= b1; b++) if (columns ? road(w, a, b) : road(w, b, a)) n++;
            best = Math.max(best, n);
        }
        return best;
    }

    /** The run of neighbouring lines (columns when {@code columns}) round the most-road line, each this share of it. */
    private static int[] band(CordonWorks w, int a0, int a1, int b0, int b1, boolean columns, double share) {
        int[] count = new int[a1 - a0 + 1];
        int bestLine = a0, best = -1;
        for (int a = a0; a <= a1; a++) {
            for (int b = b0; b <= b1; b++) if (columns ? road(w, a, b) : road(w, b, a)) count[a - a0]++;
            if (count[a - a0] > best) {
                best = count[a - a0];
                bestLine = a;
            }
        }
        int lo = bestLine, hi = bestLine;
        while (lo - 1 >= a0 && count[lo - 1 - a0] >= best * share) lo--;
        while (hi + 1 <= a1 && count[hi + 1 - a0] >= best * share) hi++;
        return new int[]{lo, hi};
    }

    /** The main road through the base, as local {@code v} rows {@code {rear, front}}. */
    static int[] roadRows(CordonWorks w, Frame f, Site site) {
        int[] row = band(w, site.y0(), site.y1(), site.x0(), site.x1(), false, 0.95);
        int a = f.v(site.cx(), row[0]), b = f.v(site.cx(), row[1]);
        return new int[]{Math.min(a, b), Math.max(a, b)};
    }

    /**
     * The roads that reach the works' edge, within a few cells of a ring {@code ring} cells out: 'F' front, 'R' rear,
     * 'E' the +u flank, 'W' the -u flank.
     */
    static List<Entry> entries(CordonWorks w, Frame f, int ring) {
        List<Entry> out = new ArrayList<>();
        for (char side : new char[]{'F', 'R', 'E', 'W'}) {
            int start = Integer.MIN_VALUE;
            for (int t = -ring + 1; t <= ring; t++) {
                boolean on = false;
                // A road reaching to within a few cells of the edge counts: a compound's gate road stops at its wall.
                for (int d = ring; t < ring && d <= ring + 3 && !on; d++) {
                    int u = side == 'E' ? d : side == 'W' ? -d : t;
                    int v = side == 'F' ? d : side == 'R' ? -d : t;
                    on = road(w, f.x(u, v), f.y(u, v));
                }
                if (on && start == Integer.MIN_VALUE) start = t;
                if (!on && start != Integer.MIN_VALUE) {
                    // A road coming in, not one running along the side: a gate's width at most, at its middle.
                    int lo = start, hi = t - 1;
                    if (hi - lo > 6) {
                        int mid = (lo + hi) / 2;
                        lo = mid - 2;
                        hi = mid + 2;
                    }
                    out.add(new Entry(side, lo, hi));
                    start = Integer.MIN_VALUE;
                }
            }
        }
        return out;
    }

    static int[][] gaps(List<Entry> entries, char side) {
        List<int[]> out = new ArrayList<>();
        for (Entry e : entries) if (e.side() == side) out.add(new int[]{e.lo(), e.hi()});
        return out.toArray(new int[0][]);
    }

    /** The widest opening in a face that is a door rather than open ground. */
    static final int DOOR_WIDEST = 3;

    /**
     * The doors in a built redoubt's front and flanks bricked up: on the front face's row ({@code v = R - 1}, where the
     * front buildings' outer walls stand in line with the yard wall) and down each flank's outer column, every opening
     * no wider than {@link #DOOR_WIDEST} with wall at both its ends is walled in its building's own surface, except
     * where a road comes in ({@code entries}). A school's courtyard block faced the front with two archways and its hall
     * the flank with two doors, and the swarm walked in at them and killed its wall teams inside (town Line arid 10,
     * redoubt 1 fell at 1.9 minutes); troops making do close the doors facing the enemy. The rear is left as it
     * stands: its gap is the rear gate carriers and trucks come to. Front nests stand further in.
     */
    static void sealDoors(CordonWorks w, Frame f, List<Entry> entries) {
        CellTest open = (x, y) -> w.grid.inBounds(x, y) && w.grid.isWalkable(x, y);
        CellTest wall = (x, y) -> w.grid.inBounds(x, y) && w.topology.isWall(x, y);
        List<List<int[]>> lines = new ArrayList<>();
        lines.add(line(f, entries, 'F', R - 1));
        lines.add(line(f, entries, 'W', -R));
        lines.add(line(f, entries, 'E', R));
        for (List<int[]> line : lines) {
            for (int[] c : doors(open, wall, line)) {
                String surface = neighbourWall(w, c[0], c[1]);
                w.wall(c[0], c[1], surface != null ? surface : MASONRY);
            }
        }
    }

    /**
     * One face's cells in order: the front's row {@code at}, or a flank's column {@code at}, from {@code -R} to
     * {@code R}; a cell where a road comes in on that side is {@code null}, so a run beside it is never walled.
     */
    static List<int[]> line(Frame f, List<Entry> entries, char side, int at) {
        List<int[]> out = new ArrayList<>();
        for (int t = -R; t <= R; t++) {
            boolean road = false;
            for (Entry e : entries) road |= e.side() == side && t >= e.lo() && t <= e.hi();
            out.add(road ? null : side == 'F' ? f.cell(t, at) : f.cell(at, t));
        }
        return out;
    }

    /** A test on a cell. */
    interface CellTest {
        boolean test(int x, int y);
    }

    /**
     * The door cells along a face, its cells in order ({@code null} for none of its own): each run of open cells no
     * wider than {@link #DOOR_WIDEST} with wall at both its ends.
     */
    static List<int[]> doors(CellTest open, CellTest wall, List<int[]> line) {
        List<int[]> out = new ArrayList<>();
        int i = 0;
        while (i < line.size()) {
            int[] c = line.get(i);
            if (c == null || !open.test(c[0], c[1])) {
                i++;
                continue;
            }
            int end = i;
            while (end + 1 < line.size() && line.get(end + 1) != null
                    && open.test(line.get(end + 1)[0], line.get(end + 1)[1])) end++;
            int[] before = i > 0 ? line.get(i - 1) : null, after = end + 1 < line.size() ? line.get(end + 1) : null;
            boolean walled = before != null && after != null && wall.test(before[0], before[1])
                    && wall.test(after[0], after[1]);
            if (walled && end - i + 1 <= DOOR_WIDEST) out.addAll(line.subList(i, end + 1));
            i = end + 1;
        }
        return out;
    }

    /** The surface of a wall cell beside {@code (x, y)} along either axis, or {@code null}. */
    private static String neighbourWall(CordonWorks w, int x, int y) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int nx = x + d[0], ny = y + d[1];
            if (w.grid.inBounds(nx, ny) && w.topology.isWall(nx, ny)) return w.topology.getSurfaceId(nx, ny);
        }
        return null;
    }

    // ---------------------------------------------------------------- local frame

    /**
     * Cells local to a redoubt or base: {@code u} across the line, {@code v} toward the front. On a map whose front is
     * south the whole layout is turned half round, so a plan written once faces the front on either.
     */
    static final class Frame {
        final int ox, oy, f;

        Frame(int ox, int oy, int front) {
            this.ox = ox;
            this.oy = oy;
            this.f = front >= 0 ? 1 : -1;
        }

        int x(int u, int v) { return ox + f * u; }
        int y(int u, int v) { return oy + f * v; }
        float fx(float u, float v) { return ox + f * u + 0.5f; }
        float fy(float u, float v) { return oy + f * v + 0.5f; }
        int u(int x, int y) { return f * (x - ox); }
        int v(int x, int y) { return f * (y - oy); }
        int[] cell(int u, int v) { return new int[]{x(u, v), y(u, v)}; }

        void clear(CordonWorks w, int u0, int v0, int u1, int v1, CordonWorks.Ground ground,
                   BiPredicate<Integer, Integer> keep) {
            w.clear(x(u0, v0), y(u0, v0), x(u1, v1), y(u1, v1), ground, keep);
        }

        void floor(CordonWorks w, int u0, int v0, int u1, int v1, CordonWorks.Ground ground) {
            w.floor(x(u0, v0), y(u0, v0), x(u1, v1), y(u1, v1), ground);
        }

        void worked(CordonWorks w, int u, int v) { w.worked(x(u, v), y(u, v)); }

        boolean stamp(CordonWorks w, BuildingStamp stamp, int u0, int v0, int turns) {
            int[] size = CordonWorks.size(stamp, turns);
            int left = f > 0 ? ox + u0 : ox - (u0 + size[0] - 1);
            int bottom = f > 0 ? oy + v0 : oy - (v0 + size[1] - 1);
            return w.stamp(stamp, left, bottom, f > 0 ? turns : (turns + 2) % 4);
        }

        /** A straight wall from local {@code (u0, v0)} to {@code (u1, v1)}; gaps are local spans along it. */
        void wall(CordonWorks w, String material, int u0, int v0, int u1, int v1, int[]... gaps) {
            boolean across = v0 == v1;
            int a = across ? Math.min(u0, u1) : Math.min(v0, v1), b = across ? Math.max(u0, u1) : Math.max(v0, v1);
            for (int t = a; t <= b; t++) {
                boolean open = false;
                for (int[] gap : gaps) open |= t >= gap[0] && t <= gap[1];
                if (open) continue;
                int u = across ? t : u0, v = across ? v0 : t;
                w.wall(x(u, v), y(u, v), material);
            }
        }

        /** Whether a gun nest fits at {@code (u, v)}: the cell and its eight neighbours open floor the works own. */
        boolean roomFor(CordonWorks w, int u, int v) {
            for (int dv = -1; dv <= 1; dv++) {
                for (int du = -1; du <= 1; du++) {
                    int cx = x(u + du, v + dv), cy = y(u + du, v + dv);
                    if (!w.free(cx, cy) || !PreparedGround.open(w.ctx, cx, cy)) return false;
                }
            }
            return true;
        }

        boolean sandbag(CordonWorks w, int u, int v, int du, int dv) {
            return w.sandbag(x(u, v), y(u, v), new int[]{f * du, f * dv}, null);
        }

        boolean obstacle(CordonWorks w, int u, int v, String id) {
            return w.obstacle(x(u, v), y(u, v), id);
        }

        boolean fence(CordonWorks w, int u, int v, boolean acrossLine) {
            return w.fence(x(u, v), y(u, v), acrossLine);
        }

        /** A prop whose footprint's local lower-left is {@code (u, v)}. */
        boolean prop(CordonWorks w, int u, int v, String id, int turns, boolean blocking) {
            if (f > 0) return w.prop(x(u, v), y(u, v), id, turns, blocking);
            int[] size = w.propSize(id, turns);
            return w.prop(ox - (u + size[0] - 1), oy - (v + size[1] - 1), id, (turns + 2) % 4, blocking);
        }
    }
}
