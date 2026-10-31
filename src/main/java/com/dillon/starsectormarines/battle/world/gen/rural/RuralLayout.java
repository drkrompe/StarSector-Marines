package com.dillon.starsectormarines.battle.world.gen.rural;

import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The surveyed country outside a colony's edge: its roads by grade and the
 * plots between them (`mapgen-nouns.md`, Rural country).
 *
 * <p>The country is the band an open-field landing claims along its map edge.
 * Nothing in it is grown: the town's street network stops at the band and this
 * lays the country's own, by role rather than by whatever width an arm happened
 * to draw. One paved {@link Grade#COUNTRY country road} carries the town's
 * widest street on to the map edge; a gravel {@link Grade#FARM farm road} runs
 * the length of the town's edge, so every other street that reaches the fields
 * meets a road instead of stopping in a crop; dirt {@link Grade#TRACK field
 * tracks} run between the plots. Every road is straight and one width along its
 * whole length, and the plots are the rectangles the roads leave.
 *
 * <p>Pure geometry over a road mask; draws only from its own seeded random, so
 * planning it moves nothing else on the map.
 *
 * @param depth rows of country from the map edge, the band {@code edgeDistance < depth}
 */
public record RuralLayout(int width, int height, int edge, int depth, FieldOfFire field,
                          List<Road> roads, List<Plot> plots, List<Farmstead> farmsteads) {

    /** A road's role, which fixes its width and what it is made of. */
    public enum Grade {
        /** Where a main street leaves town for the map edge. As wide as a cross-street, paved. */
        COUNTRY(5, Material.ASPHALT),
        /** Along the town's edge, the road the fields are worked from. Gravel. */
        FARM(3, Material.GRAVEL),
        /** Between plots. Two ruts of dirt. */
        TRACK(2, Material.DIRT);

        public final int width;
        public final Material material;

        Grade(int width, Material material) {
            this.width = width;
            this.material = material;
        }
    }

    /**
     * What a plot is used for, which decides how it is painted. {@link #WILD}
     * is ground nobody works: it keeps the planet's own surface.
     */
    public enum Use { ROW_CROP, GRAIN, PASTURE, FALLOW, WILD }

    /**
     * One straight road as an inclusive rect, {@code alongX} when it runs along
     * x. Its breadth across that direction is its grade's width.
     */
    public record Road(Grade grade, int x0, int y0, int x1, int y1, boolean alongX) {
        public boolean contains(int x, int y) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }
        public int breadth() { return alongX ? y1 - y0 + 1 : x1 - x0 + 1; }
        public int length() { return alongX ? x1 - x0 + 1 : y1 - y0 + 1; }
    }

    /**
     * One field as an inclusive rect; its crop rows run along x when
     * {@code rowsAlongX}, and a {@code hedged} field keeps a hedge on its
     * headland wherever a road or track runs past it.
     */
    public record Plot(int x0, int y0, int x1, int y1, Use use, boolean rowsAlongX, boolean hedged) {
        public boolean contains(int x, int y) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }
    }

    /**
     * A farm's buildings and the bare yard round them, cut from one plot's
     * corner and set back from the road that plot's yard opens onto. Each
     * building is a lower-left corner and the quarter turns that face its door
     * at that road.
     */
    public record Farmstead(int yardX0, int yardY0, int yardX1, int yardY1,
                            int houseX, int houseY, int barnX, int barnY, int turns) {
        public boolean inYard(int x, int y) { return x >= yardX0 && x <= yardX1 && y >= yardY0 && y <= yardY1; }
    }

    /** The farmhouse's plan, door side along: as authored in {@code BuildingStamp.FARMHOUSE}. */
    public static final int HOUSE_ALONG = 9, HOUSE_DEEP = 7;
    /** The barn's plan: as authored in {@code BuildingStamp.BARN}. */
    public static final int BARN_ALONG = 13, BARN_DEEP = 8;
    /** Cells between the road and a farm's buildings, and between the two buildings. */
    static final int SETBACK = 3, BETWEEN = 3;
    /** The yard: a margin round the buildings, open to the road. */
    static final int YARD_ALONG = 2 + HOUSE_ALONG + BETWEEN + BARN_ALONG + 2;
    static final int YARD_DEEP = SETBACK + Math.max(HOUSE_DEEP, BARN_DEEP) + 3;
    /** Farms a country is surveyed with at most, and how far apart along it they stand. */
    static final int FARMSTEADS = 2, FARM_SPACING = 80;
    /** A farm's nearest corner stands this far past the landing's field of fire. */
    static final int FARM_CLEARANCE = 14;

    /** The farmstead whose yard holds this cell, or {@code null}. */
    public Farmstead farmsteadAt(int x, int y) {
        for (Farmstead farm : farmsteads) if (farm.inYard(x, y)) return farm;
        return null;
    }

    /** Narrowest plot worth surveying; a sliver narrower than this joins the plot beside it. */
    static final int MIN_PLOT = 24;
    /** Plot frontage along the farm road is drawn from this range. */
    static final int PLOT_LO = 36, PLOT_HI = 56;
    /**
     * How far along the edge from the country road the fields thin out to
     * nothing, as a share of the edge: on the town's side, and out by the map
     * edge, where a plot is also worked less often to begin with.
     */
    static final double TOWN_SIDE_REACH = .5, FAR_SIDE_REACH = .3, FAR_SIDE_WORKED = .6;
    /** Share of crop plots with a hedge, in percent; pasture always has one. */
    static final int HEDGED_PERCENT = 50;
    /** A hedge leaves a gateway this wide in the middle of each side it runs along. */
    public static final int GATEWAY = 4;
    /** Cells past the landing's field of fire that stay unhedged, so the field reads open. */
    public static final int HEDGE_CLEARANCE = 6;

    /**
     * Whether a hedge stands on this cell: the outer headland of a hedged plot,
     * along a side a road or track runs past, outside the gateway and clear of
     * the landing's field.
     */
    public boolean hedgeAt(int x, int y) {
        Plot plot = plotAt(x, y);
        if (plot == null || !plot.hedged() || farmsteadAt(x, y) != null) return false;
        if (field != null) {
            long dx = x - field.centerX(), dy = y - field.centerY(), r = field.radius() + HEDGE_CLEARANCE;
            if (dx * dx + dy * dy <= r * r) return false;
        }
        boolean west = x == plot.x0(), east = x == plot.x1(), south = y == plot.y0(), north = y == plot.y1();
        if (west && fronts(x - 1, y) && !gateway(y, plot.y0(), plot.y1())) return true;
        if (east && fronts(x + 1, y) && !gateway(y, plot.y0(), plot.y1())) return true;
        if (south && fronts(x, y - 1) && !gateway(x, plot.x0(), plot.x1())) return true;
        return north && fronts(x, y + 1) && !gateway(x, plot.x0(), plot.x1());
    }

    private boolean fronts(int x, int y) {
        return inCountry(x, y) && roadAt(x, y) != null;
    }

    private static boolean gateway(int at, int lo, int hi) {
        int mid = (lo + hi) / 2;
        return at >= mid - GATEWAY / 2 && at < mid - GATEWAY / 2 + GATEWAY;
    }

    /** Rows a street must run into town to count as arriving at the country. */
    static final int ARRIVAL_ROWS = 8;

    /**
     * The same country with nothing worked on the inclusive rect: every plot
     * touching it left wild, and every farm whose yard touches it not built.
     * The roads stay, since the country's traffic runs on them.
     */
    public RuralLayout without(int x0, int y0, int x1, int y1) {
        List<Plot> keptPlots = new ArrayList<>(plots.size());
        for (Plot p : plots) {
            boolean touches = p.x0() <= x1 && p.x1() >= x0 && p.y0() <= y1 && p.y1() >= y0;
            keptPlots.add(touches && p.use() != Use.WILD
                    ? new Plot(p.x0(), p.y0(), p.x1(), p.y1(), Use.WILD, p.rowsAlongX(), false) : p);
        }
        List<Farmstead> keptFarms = new ArrayList<>(farmsteads.size());
        for (Farmstead f : farmsteads) {
            if (f.yardX0() <= x1 && f.yardX1() >= x0 && f.yardY0() <= y1 && f.yardY1() >= y0) continue;
            keptFarms.add(f);
        }
        return new RuralLayout(width, height, edge, depth, field, roads, keptPlots, keptFarms);
    }

    /** Cells a farm keeps off ({@link #farmsKeepingOff}). */
    public interface Cells {
        boolean test(int x, int y);
    }

    /**
     * The same country with no farm on {@code kept} cells (a way cut across the fields after they were surveyed, a
     * lane's track and the ground kept clear beside it): a farm whose yard touches one steps aside along its plot's
     * frontage to the nearest place clear of them, keeping its depth, so its doors still face the road they did; a
     * farm with no such place in its plot is not built.
     */
    public RuralLayout farmsKeepingOff(Cells kept) {
        List<Farmstead> out = new ArrayList<>(farmsteads.size());
        for (Farmstead f : farmsteads) {
            Farmstead moved = stepAside(f, kept, out);
            if (moved != null) out.add(moved);
        }
        return out.size() == farmsteads.size() && out.equals(farmsteads) ? this
                : new RuralLayout(width, height, edge, depth, field, roads, plots, out);
    }

    private Farmstead stepAside(Farmstead f, Cells kept, List<Farmstead> standing) {
        Plot plot = plotAt(f.yardX0(), f.yardY0());
        boolean alongX = edge >= 2;
        int lo = plot == null ? 0 : alongX ? plot.x0() - f.yardX0() : plot.y0() - f.yardY0();
        int hi = plot == null ? 0 : alongX ? plot.x1() - f.yardX1() : plot.y1() - f.yardY1();
        for (int step = 0; step <= Math.max(-lo, hi); step++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int s = step * sign;
                if (s < lo || s > hi || (step == 0 && sign < 0)) continue;
                int dx = alongX ? s : 0, dy = alongX ? 0 : s;
                Farmstead g = new Farmstead(f.yardX0() + dx, f.yardY0() + dy, f.yardX1() + dx, f.yardY1() + dy,
                        f.houseX() + dx, f.houseY() + dy, f.barnX() + dx, f.barnY() + dy, f.turns());
                if (clear(g, kept, standing)) return g;
            }
        }
        return null;
    }

    private static boolean clear(Farmstead g, Cells kept, List<Farmstead> standing) {
        for (Farmstead o : standing) {
            if (o.yardX0() <= g.yardX1() && o.yardX1() >= g.yardX0() && o.yardY0() <= g.yardY1() && o.yardY1() >= g.yardY0()) {
                return false;
            }
        }
        for (int x = g.yardX0(); x <= g.yardX1(); x++) {
            for (int y = g.yardY0(); y <= g.yardY1(); y++) if (kept.test(x, y)) return false;
        }
        return true;
    }

    /** Whether a cell is in the country at all. */
    public boolean inCountry(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && depthOf(x, y) < depth;
    }

    /** The road of the highest grade on this cell, or {@code null}. */
    public Road roadAt(int x, int y) {
        Road best = null;
        for (Road road : roads) {
            if (road.contains(x, y) && (best == null || road.grade().ordinal() < best.grade().ordinal())) best = road;
        }
        return best;
    }

    /** The plot holding this cell, or {@code null} on a road or outside every plot. */
    public Plot plotAt(int x, int y) {
        for (Plot plot : plots) if (plot.contains(x, y)) return plot;
        return null;
    }

    /** Cells of the country that carry a {@link Grade#COUNTRY} or {@link Grade#FARM} road: the ones vehicles route on. */
    public boolean carriesTraffic(int x, int y) {
        Road road = roadAt(x, y);
        return road != null && road.grade() != Grade.TRACK;
    }

    private int depthOf(int x, int y) {
        return switch (edge) {
            case 0 -> x;
            case 1 -> width - 1 - x;
            case 2 -> y;
            default -> height - 1 - y;
        };
    }

    /**
     * Surveys the country of {@code field}. {@code townRoad} is the road the
     * town grew, read only on the first row past the country, where its streets
     * arrive at the fields.
     */
    public static RuralLayout plan(int width, int height, FieldOfFire field, boolean[][] townRoad, long seed) {
        Frame f = new Frame(width, height, field.edge(width, height));
        int depth = Math.min(field.countryDepth(width, height) + 1, f.across - 1);
        int span = f.along;
        Random rng = new Random(seed ^ 0x52555241L);

        // The main street: the widest of the town's streets that reach the fields,
        // the nearest the landing on a tie. No street at all is a country road
        // past the landing, which the weld will join to whatever the town has.
        int fieldAlong = f.alongOf(field.centerX(), field.centerY());
        int bestStart = -1, bestLen = 0;
        for (int a = 0; a < span; ) {
            if (!f.arrives(townRoad, a, depth)) { a++; continue; }
            int end = a;
            while (end + 1 < span && f.arrives(townRoad, end + 1, depth)) end++;
            int len = end - a + 1;
            int mid = (a + end) / 2;
            if (len > bestLen || (len == bestLen && Math.abs(mid - fieldAlong)
                    < Math.abs((bestStart + bestStart + bestLen - 1) / 2 - fieldAlong))) {
                bestStart = a;
                bestLen = len;
            }
            a = end + 1;
        }
        int mainMid = bestStart < 0 ? fieldAlong : bestStart + (bestLen - 1) / 2;
        int half = Grade.COUNTRY.width / 2;
        mainMid = Math.max(half + 1, Math.min(span - 2 - half, mainMid));

        List<Road> roads = new ArrayList<>();
        int farmLo = depth - Grade.FARM.width;
        // Higher grades first: a junction is the higher road's ground.
        roads.add(f.road(Grade.COUNTRY, mainMid - half, mainMid + half, 0, depth - 1, false));
        roads.add(f.road(Grade.FARM, 0, span - 1, farmLo, depth - 1, true));

        // Bands across the fields' depth, the town's side first: one band, or
        // two with a cross-track band between when deep enough for two plots.
        List<int[]> bands = new ArrayList<>();
        if (farmLo >= 2 * MIN_PLOT + Grade.TRACK.width) {
            int crossLo = farmLo / 2 - Grade.TRACK.width / 2;
            bands.add(new int[]{crossLo + Grade.TRACK.width, farmLo - 1, PLOTS});
            bands.add(new int[]{crossLo, crossLo + Grade.TRACK.width - 1, CROSSING});
            bands.add(new int[]{0, crossLo - 1, PLOTS});
        } else {
            bands.add(new int[]{0, farmLo - 1, PLOTS});
        }

        // Slices along the edge, outward from the country road both ways: plot
        // columns, the track-wide boundaries between them, and the road itself.
        // Each carries how far its near side lies from the country road.
        List<int[]> slices = new ArrayList<>();
        slices.add(new int[]{mainMid - half, mainMid + half, ROAD, 0});
        int edgeHi = mainMid + half + 1;
        int last = edgeHi;
        while (true) {
            int start = last + PLOT_LO + rng.nextInt(PLOT_HI - PLOT_LO + 1);
            if (start + Grade.TRACK.width - 1 > span - 1 - MIN_PLOT) break;
            slices.add(new int[]{last, start - 1, PLOTS, last - edgeHi});
            slices.add(new int[]{start, start + Grade.TRACK.width - 1, CROSSING, start - edgeHi});
            last = start + Grade.TRACK.width;
        }
        slices.add(new int[]{last, span - 1, PLOTS, last - edgeHi});
        int edgeLo = mainMid - half - 1;
        last = edgeLo;
        while (true) {
            int end = last - PLOT_LO - rng.nextInt(PLOT_HI - PLOT_LO + 1);
            if (end - Grade.TRACK.width + 1 < MIN_PLOT) break;
            slices.add(new int[]{end + 1, last, PLOTS, edgeLo - last});
            slices.add(new int[]{end - Grade.TRACK.width + 1, end, CROSSING, edgeLo - end});
            last = end - Grade.TRACK.width;
        }
        slices.add(new int[]{0, last, PLOTS, edgeLo - last});
        slices.removeIf(slice -> slice[1] < slice[0]);
        slices.sort(Comparator.comparingInt(slice -> slice[0]));
        int n = slices.size(), b = bands.size(), road = 0;
        for (int i = 0; i < n; i++) if (slices.get(i)[2] == ROAD) road = i;

        // Which plots are worked. The fields are land in use, so they lie where
        // a farm works from: against the town, beside the country road, and
        // thinner the farther out along the edge. What nobody works stays the
        // planet's own ground. The town-side plots beside the road always are.
        boolean[][] worked = new boolean[n][b];
        for (int i = 0; i < n; i++) {
            if (slices.get(i)[2] != PLOTS) continue;
            int gap = slices.get(i)[3];
            for (int k = 0; k < b; k++) {
                if (bands.get(k)[2] != PLOTS) continue;
                boolean townSide = k == 0;
                double reach = span * (townSide ? TOWN_SIDE_REACH : FAR_SIDE_REACH);
                double chance = (townSide ? 1 : FAR_SIDE_WORKED) * Math.max(0, 1 - gap / reach);
                worked[i][k] = !landUse() || (townSide && Math.abs(i - road) == 1) || rng.nextDouble() < chance;
            }
        }

        // Tracks run only where they serve a worked plot: between plots where
        // either is worked, along the cross band past a worked plot, and through
        // a crossing where any of those meet.
        boolean[][] track = new boolean[n][b];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < b; k++) {
                int kind = slices.get(i)[2];
                if (kind == CROSSING && bands.get(k)[2] == PLOTS) {
                    track[i][k] = at(worked, i - 1, k) || at(worked, i + 1, k);
                } else if (kind == PLOTS && bands.get(k)[2] == CROSSING) {
                    track[i][k] = at(worked, i, k - 1) || at(worked, i, k + 1);
                }
            }
        }
        // A worked plot out by the map edge behind wild ground is reached down
        // the boundary on its road side, from the farm road to the cross band.
        for (int i = 0; i < n; i++) {
            if (b < 3 || !worked[i][2] || worked[i][0]) continue;
            int toward = i > road ? i - 1 : i + 1;
            if (slices.get(toward)[2] == CROSSING) track[toward][0] = true;
        }
        for (int i = 0; i < n; i++) {
            if (slices.get(i)[2] != CROSSING) continue;
            for (int k = 0; k < b; k++) {
                if (bands.get(k)[2] != CROSSING) continue;
                track[i][k] = at(track, i - 1, k) || at(track, i + 1, k)
                        || at(track, i, k - 1) || at(track, i, k + 1);
            }
        }

        // Tracks as straight runs: down each boundary across the bands, and
        // along the cross band past the plots.
        for (int i = 0; i < n; i++) {
            if (slices.get(i)[2] != CROSSING) continue;
            for (int k = 0; k < b; ) {
                if (!track[i][k]) { k++; continue; }
                int end = k;
                while (end + 1 < b && track[i][end + 1]) end++;
                if (end > k || bands.get(k)[2] != CROSSING) {
                    roads.add(f.road(Grade.TRACK, slices.get(i)[0], slices.get(i)[1],
                            bands.get(end)[0], bands.get(k)[1], false));
                }
                k = end + 1;
            }
        }
        for (int k = 0; k < b; k++) {
            if (bands.get(k)[2] != CROSSING) continue;
            for (int i = 0; i < n; ) {
                if (!track[i][k]) { i++; continue; }
                int end = i;
                boolean pastPlot = false;
                while (end < n && track[end][k]) pastPlot |= slices.get(end++)[2] == PLOTS;
                if (pastPlot) {
                    roads.add(f.road(Grade.TRACK, slices.get(i)[0], slices.get(end - 1)[1],
                            bands.get(k)[0], bands.get(k)[1], true));
                }
                i = end;
            }
        }

        Use[] crops = {Use.ROW_CROP, Use.GRAIN, Use.PASTURE, Use.FALLOW};
        int[] weights = {40, 25, 20, 15};
        List<Plot> plots = new ArrayList<>();
        List<int[]> plotsFrame = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int[] column = slices.get(i);
            if (column[2] != PLOTS) continue;
            for (int k = 0; k < b; k++) {
                if (!worked[i][k]) continue;
                int[] row = bands.get(k);
                int pick = rng.nextInt(100);
                Use use = crops[crops.length - 1];
                for (int c = 0, sum = 0; c < crops.length; c++) {
                    sum += weights[c];
                    if (pick < sum) { use = crops[c]; break; }
                }
                // Rows run the plot's long way, as a drill would.
                boolean rowsAlongEdge = column[1] - column[0] >= row[1] - row[0];
                // Stock is hedged in; a crop field is hedged about half the time.
                boolean hedged = use == Use.PASTURE || rng.nextInt(100) < HEDGED_PERCENT;
                plots.add(f.plot(column[0], column[1], row[0], row[1], use, rowsAlongEdge, hedged));
                plotsFrame.add(new int[]{column[0], column[1], row[0], row[1]});
            }
        }
        // The rest is wild: in each band, every run of ground that no road,
        // track or worked plot holds.
        for (int k = 0; k < b; k++) {
            int[] row = bands.get(k);
            for (int i = 0; i < n; ) {
                if (!wild(slices, worked, track, i, k)) { i++; continue; }
                int end = i;
                while (end + 1 < n && wild(slices, worked, track, end + 1, k)) end++;
                plots.add(f.plot(slices.get(i)[0], slices.get(end)[1], row[0], row[1], Use.WILD, true, false));
                i = end + 1;
            }
        }
        List<Farmstead> farms = farmsteads(f, field, plotsFrame);
        return new RuralLayout(width, height, f.edge, depth, field, List.copyOf(roads), List.copyOf(plots), farms);
    }

    /**
     * Whether the country's plots are worked by land use.
     * {@code -Dbattle.mapgen.ruralLandUse=false} is the control: every plot is a field.
     */
    static boolean landUse() {
        return Boolean.parseBoolean(System.getProperty("battle.mapgen.ruralLandUse", "true"));
    }

    /** Kinds of slice and band in the survey's grid. */
    private static final int PLOTS = 0, CROSSING = 1, ROAD = 2;

    private static boolean at(boolean[][] grid, int i, int k) {
        return i >= 0 && i < grid.length && k >= 0 && k < grid[i].length && grid[i][k];
    }

    private static boolean wild(List<int[]> slices, boolean[][] worked, boolean[][] track, int i, int k) {
        return slices.get(i)[2] != ROAD && !worked[i][k] && !track[i][k];
    }

    /**
     * Up to {@link #FARMSTEADS} farms, each in the plot farthest from the
     * landing that has room for its yard, a good way along from the last. A
     * farm's yard sits in the plot's corner on its town side, where a cross
     * track or the farm road runs past, and its doors face that road.
     */
    private static List<Farmstead> farmsteads(Frame f, FieldOfFire field, List<int[]> plotsFrame) {
        int fa = f.alongOf(field.centerX(), field.centerY());
        int fd = f.depthOf(field.centerX(), field.centerY());
        long clear = field.radius() + FARM_CLEARANCE;
        List<int[]> candidates = new ArrayList<>();
        for (int[] p : plotsFrame) {
            if (p[1] - p[0] + 1 < YARD_ALONG + 6 || p[3] - p[2] + 1 < YARD_DEEP + 6) continue;
            // The yard takes the plot's end that is farther from the landing.
            boolean high = Math.abs(p[1] - fa) > Math.abs(p[0] - fa);
            int a0 = high ? p[1] - YARD_ALONG + 1 : p[0], a1 = a0 + YARD_ALONG - 1;
            int d1 = p[3], d0 = d1 - YARD_DEEP + 1;
            long da = Math.max(0, Math.max(a0 - fa, fa - a1)), dd = Math.max(0, Math.max(d0 - fd, fd - d1));
            long distance = da * da + dd * dd;
            if (distance <= clear * clear) continue;
            candidates.add(new int[]{a0, a1, d0, d1, (int) Math.min(Integer.MAX_VALUE, distance)});
        }
        candidates.sort((x, y) -> Integer.compare(y[4], x[4]));
        List<Farmstead> out = new ArrayList<>();
        List<Integer> placedAt = new ArrayList<>();
        for (int[] c : candidates) {
            if (out.size() >= FARMSTEADS) break;
            int mid = (c[0] + c[1]) / 2;
            if (placedAt.stream().anyMatch(m -> Math.abs(m - mid) < FARM_SPACING)) continue;
            placedAt.add(mid);
            int houseA = c[0] + 2, barnA = houseA + HOUSE_ALONG + BETWEEN;
            int front = c[3] - SETBACK;
            out.add(f.farmstead(c[0], c[1], c[2], c[3],
                    houseA, houseA + HOUSE_ALONG - 1, front - HOUSE_DEEP + 1, front,
                    barnA, barnA + BARN_ALONG - 1, front - BARN_DEEP + 1, front));
        }
        return List.copyOf(out);
    }

    /**
     * Along-and-depth coordinates for one edge: {@code along} runs parallel to
     * the edge, {@code depth} inland from it.
     */
    private record Frame(int width, int height, int edge, int along, int across) {
        Frame(int width, int height, int edge) {
            this(width, height, edge, edge < 2 ? height : width, edge < 2 ? width : height);
        }

        int x(int a, int d) {
            return switch (edge) {
                case 0 -> d;
                case 1 -> width - 1 - d;
                default -> a;
            };
        }

        int y(int a, int d) {
            return switch (edge) {
                case 2 -> d;
                case 3 -> height - 1 - d;
                default -> a;
            };
        }

        int alongOf(int x, int y) { return edge < 2 ? y : x; }

        int depthOf(int x, int y) {
            return switch (edge) {
                case 0 -> x;
                case 1 -> width - 1 - x;
                case 2 -> y;
                default -> height - 1 - y;
            };
        }

        /** Quarter turns that face a stamp's south door toward rising depth. */
        int doorTurns() {
            return switch (edge) {
                case 0 -> 1;
                case 1 -> 3;
                case 2 -> 2;
                default -> 0;
            };
        }

        Farmstead farmstead(int ya0, int ya1, int yd0, int yd1,
                            int ha0, int ha1, int hd0, int hd1, int ba0, int ba1, int bd0, int bd1) {
            return new Farmstead(minX(ya0, ya1, yd0, yd1), minY(ya0, ya1, yd0, yd1),
                    maxX(ya0, ya1, yd0, yd1), maxY(ya0, ya1, yd0, yd1),
                    minX(ha0, ha1, hd0, hd1), minY(ha0, ha1, hd0, hd1),
                    minX(ba0, ba1, bd0, bd1), minY(ba0, ba1, bd0, bd1), doorTurns());
        }

        int minX(int a0, int a1, int d0, int d1) { return Math.min(x(a0, d0), x(a1, d1)); }
        int maxX(int a0, int a1, int d0, int d1) { return Math.max(x(a0, d0), x(a1, d1)); }
        int minY(int a0, int a1, int d0, int d1) { return Math.min(y(a0, d0), y(a1, d1)); }
        int maxY(int a0, int a1, int d0, int d1) { return Math.max(y(a0, d0), y(a1, d1)); }

        boolean at(boolean[][] mask, int a, int d) {
            int x = x(a, d), y = y(a, d);
            return x >= 0 && y >= 0 && x < width && y < height && mask[x][y];
        }

        /**
         * Whether a street arrives at the country here: road on the first town
         * row and on the rows behind it. A street running along the town's edge
         * is road on that row too, but it leads nowhere out of town, and
         * counting it made the longest stretch of kerb-side street the main road.
         */
        boolean arrives(boolean[][] mask, int a, int d) {
            for (int k = 0; k < ARRIVAL_ROWS; k++) if (!at(mask, a, d + k)) return false;
            return true;
        }

        /** {@code alongEdge} is the road's direction in the frame. */
        Road road(Grade grade, int a0, int a1, int d0, int d1, boolean alongEdge) {
            int x0 = Math.min(x(a0, d0), x(a1, d1)), x1 = Math.max(x(a0, d0), x(a1, d1));
            int y0 = Math.min(y(a0, d0), y(a1, d1)), y1 = Math.max(y(a0, d0), y(a1, d1));
            return new Road(grade, x0, y0, x1, y1, alongEdge == (edge >= 2));
        }

        Plot plot(int a0, int a1, int d0, int d1, Use use, boolean rowsAlongEdge, boolean hedged) {
            int x0 = Math.min(x(a0, d0), x(a1, d1)), x1 = Math.max(x(a0, d0), x(a1, d1));
            int y0 = Math.min(y(a0, d0), y(a1, d1)), y1 = Math.max(y(a0, d0), y(a1, d1));
            return new Plot(x0, y0, x1, y1, use, rowsAlongEdge == (edge >= 2), hedged);
        }
    }
}
