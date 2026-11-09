package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.CordonedFacility;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.IndustrialZone;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.LoadingBay;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.road.RoadReservation;
import com.dillon.starsectormarines.battle.world.gen.ServiceLane;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Raid redesign directions for the owner's picks ({@code raid-steal}, {@code raid-break}): one established Raid map (280x168,
 * look preset, roofs on) drawn once per direction, each with its mock-up laid over the real marks the map
 * publishes, and a close look at the place that direction is about in a corner. {@code 00-today.png} is the map
 * as the Raid plays it now: berths and the one prize. Ground only: nothing is played.
 *
 * <p>The objective follows the raid's intent, so there are two questions. A valuables raid steals what the
 * player picked in the raid menu: {@code s1-stores} (each good where the market keeps it, carried to the boat),
 * {@code s2-vault} (everything in one bonded store, the cordoned facility) and {@code s3-callin} (the boat called
 * to a lot the company holds). A disrupting raid breaks the industry it named, laid as its works (the industrial
 * district): {@code b1-wreck} (a charge on each machine, days down per machine) and {@code b2-hold} (hold the
 * works while it burns). Every card draws the company's real lift: one Hermes, two more berths once bought.
 *
 * <p>Seeds are scanned for a Raid map that lays a cordoned facility and an industrial zone; each seed's marks
 * are printed.
 */
public final class RaidDirectionsSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final OperationTier TIER = OperationTier.ESTABLISHED;
    private static final long[] SEEDS = {141_418L, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    private static final TargetProfile MARKET = new TargetProfile(
            6, 5, 4, 2, "independent",
            EnumSet.of(EconomicFunction.SPACEPORT, EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.COMMERCE),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    private static final Color GOLD = new Color(255, 214, 40);
    private static final Color RED = new Color(235, 50, 40);
    private static final Color ORANGE = new Color(255, 140, 30);
    private static final Color CYAN = new Color(70, 220, 255);
    private static final Color GREEN = new Color(90, 240, 120);
    private static final Color SHADE = new Color(0, 0, 0, 175);

    @Override public String id() { return "raid-directions"; }

    @Override public String label() { return "Raid redesign directions drawn on an established Raid map"; }

    @Override public boolean inAll() { return false; }

    private record Pick(long seed, MapResult map) { }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        MapScale scale = MapScale.forRaid(TIER);
        Pick best = null;
        int bestScore = -1;
        for (long seed : SEEDS) {
            PrecinctPlan precincts = BattleSetup.precinctPlanFor(MissionType.RAID, TIER, RiskLevel.MEDIUM, MARKET,
                    scale, seed, null);
            MapResult map = BattleSetup.proceduralOperationMap(MissionType.RAID, TIER, RiskLevel.MEDIUM, MARKET,
                    scale, seed, null, precincts);
            int score = (map.cordonedFacilities.isEmpty() ? 0 : 2) + (map.industrialZones.isEmpty() ? 0 : 1);
            System.out.printf(Locale.ROOT, "[%s] seed %d: facilities %d, industrial zones %d, points of interest %d%n",
                    id(), seed, map.cordonedFacilities.size(), map.industrialZones.size(), map.pointsOfInterest.size());
            if (score > bestScore) {
                best = new Pick(seed, map);
                bestScore = score;
            }
            if (score == 3) break;
        }
        MapResult map = best.map();
        long seed = best.seed();
        // The generic arrival plan picks four berths; the company flies one boat (three once two more berths
        // are bought), so only the first is the boat's and the next two are drawn as the berths a company buys.
        List<LandingPad> pads = LandingPadSelector.select(map, 3, BattleSetup.LZ_MIN_SEPARATION);
        RaidTargetLayout target = RaidTargetLayout.select(map, pads);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.buildMap(map, List.of(), map.defensePosts, List.of(), seed).sim()) {
            int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
            Frame frame = new Frame(w / 2f, h / 2f, (int) Math.floor(W / (w * 1.04f)));
            BufferedImage base = BattleReviewFrameRenderer.look(context.modRoot(), W, H, w / 2f, h / 2f,
                    w * 1.04f).render(sim, "");
            Places places = Places.of(map);
            out.add(new SnapshotArtifact("00-today.png", today(copy(base), frame, map, pads, target)));
            out.add(new SnapshotArtifact("s1-stores.png",
                    stealStores(context, sim, copy(base), frame, map, pads, places)));
            out.add(new SnapshotArtifact("s2-vault.png", stealVault(context, sim, copy(base), frame, map, pads)));
            out.add(new SnapshotArtifact("s3-callin.png",
                    stealCallIn(context, sim, copy(base), frame, map, pads, places)));
            out.add(new SnapshotArtifact("b1-wreck.png", breakWreck(context, sim, copy(base), frame, map, pads)));
            out.add(new SnapshotArtifact("b2-hold.png", breakHold(context, sim, copy(base), frame, map, pads)));
        }
        return out;
    }

    /** World cell to frame pixel, the renderer's whole pixels per cell. */
    private record Frame(float cx, float cy, int ppc) {
        int x(float cellX) { return Math.round(W / 2f + (cellX - cx) * ppc); }
        int y(float cellY) { return Math.round(H / 2f - (cellY - cy) * ppc); }
    }

    /** Where a market keeps what a valuables raid takes: one place per kind of goods, read off the map. */
    private record Places(PointOfInterest vault, PointOfInterest armoury, PointOfInterest lab,
                          List<float[]> warehouses) {
        static Places of(MapResult map) {
            PointOfInterest vault = null, armoury = null, lab = null;
            for (PointOfInterest p : map.pointsOfInterest) {
                if (vault == null && p.kind == PointOfInterest.Kind.ADMINISTRATIVE) vault = p;
                if (armoury == null && p.kind == PointOfInterest.Kind.DEPOT) armoury = p;
                if (lab == null && p.kind == PointOfInterest.Kind.LABORATORY) lab = p;
            }
            List<float[]> warehouses = new ArrayList<>();
            for (IndustrialZone z : map.industrialZones) {
                for (IndustrialZone.Dock d : z.docks()) {
                    if (warehouses.size() < 3) warehouses.add(new float[]{d.doorX() + 0.5f, d.doorY() + 0.5f});
                }
            }
            return new Places(vault, armoury, lab, warehouses);
        }
    }

    private static BufferedImage today(BufferedImage img, Frame f, MapResult map, List<LandingPad> pads,
                                       RaidTargetLayout target) {
        Graphics2D g = graphics(img);
        List<LandingPad> four = LandingPadSelector.select(map, 4, BattleSetup.LZ_MIN_SEPARATION);
        drawPads(g, f, four, GOLD);
        tag(g, f.x(four.get(0).centerX) - 60, f.y(four.get(0).centerY) + 40,
                "4 berths: the generic arrival plan", GOLD);
        ring(g, f.x(target.targetCellX() + 0.5f), f.y(target.targetCellY() + 0.5f), 26, RED, 4f);
        tag(g, f.x(target.targetCellX() + 0.5f) - 140, f.y(target.targetCellY() + 0.5f) + 50,
                "the prize: " + target.targetName(), RED);
        arrow(g, f.x(pads.get(0).centerX), f.y(pads.get(0).centerY),
                f.x(target.targetCellX()), f.y(target.targetCellY()), RED, true);
        banner(g, "Today: one building, stand 6 s, walk back",
                "Valuables or disrupt, the battle is the same: the non-residential building farthest from the "
                        + "berths. Neither the goods chosen nor the industry reach the map.");
        g.dispose();
        return img;
    }

    // ---- steal ----

    private static BufferedImage stealStores(SnapshotContext context, BattleSimulation sim, BufferedImage img,
                                             Frame f, MapResult map, List<LandingPad> pads, Places places)
            throws Exception {
        Graphics2D g = graphics(img);
        LandingPad boat = pads.get(0);
        int bx = f.x(boat.centerX), by = f.y(boat.centerY);
        List<float[]> goods = new ArrayList<>();
        goods(g, f, places.vault(), "AI core: the admin vault", goods);
        goods(g, f, places.armoury(), "ship weapons: the depot", goods);
        goods(g, f, places.lab(), "blueprint: the laboratory", goods);
        for (int i = 0; i < places.warehouses().size(); i++) {
            float[] wh = places.warehouses().get(i);
            crate(g, f.x(wh[0]), f.y(wh[1]), 2);
            goods.add(wh);
            if (i == 0) tag(g, f.x(wh[0]) - 40, f.y(wh[1]) + 38, "heavy machinery: warehouses", GOLD);
        }
        for (float[] gd : goods) carry(g, f.x(gd[0]), f.y(gd[1]), bx, by);
        lift(g, f, pads);
        alarm(g, f, map);
        float ix = places.warehouses().isEmpty() ? map.defenderSpawnX : places.warehouses().get(0)[0];
        float iy = places.warehouses().isEmpty() ? map.defenderSpawnY : places.warehouses().get(0)[1];
        inset(context, sim, g, ix, iy, 70, f, avoid(map, pads, boat.centerX, boat.centerY, goods),
                "close: warehouses on the service street");
        banner(g, "Steal 1. Strip the stores (recommended)",
                "The goods you picked in the raid menu, each where the market keeps it. Break in, load, carry it "
                        + "back (slowed). The Hermes ferries each load up: the take is what reaches the boat.");
        g.dispose();
        return img;
    }

    private static BufferedImage stealVault(SnapshotContext context, BattleSimulation sim, BufferedImage img,
                                            Frame f, MapResult map, List<LandingPad> pads) throws Exception {
        Graphics2D g = graphics(img);
        CordonedFacility c = map.cordonedFacilities.get(0);
        float fx = (c.left() + c.right() + 1) / 2f, fy = (c.top() + c.bottom() + 1) / 2f;
        facility(g, f, c, "BONDED STORE: everything you picked, under guard");
        int n = 1;
        for (CordonedFacility.Building b : c.buildings()) {
            if (b.doorX() < 0 || n > 3) continue;
            crate(g, f.x((b.left() + b.right() + 1) / 2f), f.y((b.top() + b.bottom() + 1) / 2f), 3);
            n++;
        }
        LandingPad boat = pads.get(0);
        carry(g, f.x(fx), f.y(fy), f.x(boat.centerX), f.y(boat.centerY));
        lift(g, f, pads);
        alarm(g, f, map);
        inset(context, sim, g, fx, fy, Math.max(c.right() - c.left(), (c.bottom() - c.top()) * 16f / 9f) + 14, f,
                avoid(map, pads, fx, fy, List.of()), "close: the bonded store at play zoom");
        banner(g, "Steal 2. One score: the bonded store",
                "Everything picked is in one walled store. Choose a gate or breach, crack the strongroom, haul the "
                        + "loads out to the Hermes. One hard fight, short walks.");
        g.dispose();
        return img;
    }

    private static BufferedImage stealCallIn(SnapshotContext context, BattleSimulation sim, BufferedImage img,
                                             Frame f, MapResult map, List<LandingPad> pads, Places places)
            throws Exception {
        Graphics2D g = graphics(img);
        LandingPad first = pads.get(0);
        drawPads(g, f, List.of(first), new Color(255, 214, 40, 140));
        tag(g, f.x(first.centerX) - 70, f.y(first.centerY) + 40, "drop-off (the boat leaves)", GOLD);
        List<float[]> goods = new ArrayList<>();
        goods(g, f, places.armoury(), "ship weapons", goods);
        goods(g, f, places.vault(), "AI core", goods);
        // Lots the Hermes can be called to: the landing-site picker's sites away from the drop-off.
        int lots = 0;
        float lx = -1, ly = -1;
        double nearest = Double.MAX_VALUE;
        List<float[]> drawn = new ArrayList<>();
        for (LandingPad lot : LandingPadSelector.select(map, 24, BattleSetup.LZ_MIN_SEPARATION)) {
            float ddx = lot.centerX - first.centerX, ddy = lot.centerY - first.centerY;
            if (ddx * ddx + ddy * ddy < 60 * 60 || lots >= 4) continue;
            boolean crowded = false;
            for (float[] d : drawn) crowded |= Math.hypot(d[0] - lot.centerX, d[1] - lot.centerY) < 30;
            if (crowded) continue;
            drawn.add(new float[]{lot.centerX, lot.centerY});
            ring(g, f.x(lot.centerX + 0.5f), f.y(lot.centerY + 0.5f), 22, GREEN, 4f);
            tag(g, f.x(lot.centerX) - 40, f.y(lot.centerY) + 40, "call the boat?", GREEN);
            double d = goods.isEmpty() ? 0 : Math.hypot(goods.get(0)[0] - lot.centerX, goods.get(0)[1] - lot.centerY);
            if (d < nearest) {
                nearest = d;
                lx = lot.centerX;
                ly = lot.centerY;
            }
            lots++;
        }
        System.out.printf(Locale.ROOT, "[raid-directions] call-in lots %d%n", lots);
        if (!goods.isEmpty() && lx >= 0) carry(g, f.x(goods.get(0)[0]), f.y(goods.get(0)[1]), f.x(lx), f.y(ly));
        alarm(g, f, map);
        lift(g, f, List.of());
        float ix = lx < 0 ? first.centerX : lx, iy = lx < 0 ? first.centerY : ly;
        inset(context, sim, g, ix, iy, 60, f, avoid(map, pads, ix, iy, goods),
                "close: a lot to hold while the Hermes shuttles");
        banner(g, "Steal 3. Call the boat to the goods",
                "Drop in, take the goods, then call the Hermes to a lot you can hold (green). It lifts one load or "
                        + "one squad a trip; holding the lot while it shuttles is the fight.");
        g.dispose();
        return img;
    }

    // ---- break ----

    private static BufferedImage breakWreck(SnapshotContext context, BattleSimulation sim, BufferedImage img,
                                            Frame f, MapResult map, List<LandingPad> pads) throws Exception {
        Graphics2D g = graphics(img);
        float[] works = industry(g, f, map);
        List<float[]> machines = machines(map);
        int n = 1;
        for (float[] m : machines) numbered(g, f.x(m[0]), f.y(m[1]), n++, RED);
        lift(g, f, pads);
        alarm(g, f, map);
        inset(context, sim, g, works[0], works[1], 70, f, avoid(map, pads, works[0], works[1], machines),
                "close: the works at play zoom");
        banner(g, "Break 1. Wreck the works (recommended)",
                "The industry you named, laid as its own works (here heavy industry). Its machines (1-"
                        + machines.size() + ") take a charge each; every one wrecked adds days it stays disrupted.");
        g.dispose();
        return img;
    }

    private static BufferedImage breakHold(SnapshotContext context, BattleSimulation sim, BufferedImage img,
                                           Frame f, MapResult map, List<LandingPad> pads) throws Exception {
        Graphics2D g = graphics(img);
        float[] works = industry(g, f, map);
        // The garrison comes back at the works up the roads into it.
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        boolean[][] roads = RoadReservation.mask(map.roadGraph, w, h);
        float radius = Math.min(w, h) * 0.32f;
        List<int[]> ways = new ArrayList<>();
        for (int a = 0; a < 720; a++) {
            double t = Math.PI * 2 * a / 720.0;
            int cx = Math.round(works[0] + (float) Math.cos(t) * radius);
            int cy = Math.round(works[1] + (float) Math.sin(t) * radius);
            if (cx < 1 || cy < 1 || cx >= w - 1 || cy >= h - 1 || !roads[cx][cy]) continue;
            boolean close = false;
            for (int[] c : ways) close |= Math.abs(c[0] - cx) + Math.abs(c[1] - cy) < 30;
            if (!close && ways.size() < 4) ways.add(new int[]{cx, cy});
        }
        for (int[] c : ways) arrow(g, f.x(c[0]), f.y(c[1]), f.x(works[0]), f.y(works[1]), CYAN, false);
        if (!ways.isEmpty()) tag(g, f.x(ways.get(0)[0]) - 60, f.y(ways.get(0)[1]) + 30, "counter-attack", CYAN);
        lift(g, f, pads);
        tag(g, 28, H - 104, "Clock: every minute held adds days down.", Color.WHITE);
        inset(context, sim, g, works[0], works[1], 70, f, avoid(map, pads, works[0], works[1], List.of()),
                "close: the works at play zoom");
        banner(g, "Break 2. Hold it and burn it",
                "Take the works and hold it while your engineers burn it down. How long it stays broken follows how "
                        + "long you held and what burned. The garrison comes back up every road.");
        g.dispose();
        return img;
    }

    /** Tints the map's industrial district (the industry's works) and returns its centre in cells. */
    private static float[] industry(Graphics2D g, Frame f, MapResult map) {
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        long sx = 0, sy = 0, count = 0;
        g.setColor(new Color(255, 140, 30, 70));
        if (map.biomeMap != null) {
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    if (map.biomeMap.themeAt(x, y) != MapDistrictTheme.INDUSTRIAL) continue;
                    g.fillRect(f.x(x), f.y(y + 1), f.ppc(), f.ppc());
                    sx += x;
                    sy += y;
                    count++;
                }
            }
        }
        float cx = count > 0 ? (float) sx / count : map.defenderSpawnX;
        float cy = count > 0 ? (float) sy / count : map.defenderSpawnY;
        // A precinct town themes no district industrial; its industrial zone (factory row, yards, warehouses)
        // stands for the works.
        if (count == 0 && !map.industrialZones.isEmpty()) {
            IndustrialZone z = map.industrialZones.get(0);
            float half = z.width() / 2f + 12;
            int x0 = f.x(Math.min(z.startX(), z.endX()) - half), x1 = f.x(Math.max(z.startX(), z.endX()) + half);
            int y0 = f.y(Math.max(z.startY(), z.endY()) + half), y1 = f.y(Math.min(z.startY(), z.endY()) - half);
            g.fillRect(x0, y0, x1 - x0, y1 - y0);
            zoneOutline(g, f, z, ORANGE);
            cx = (z.startX() + z.endX()) / 2f;
            cy = (z.startY() + z.endY()) / 2f + half;
        }
        tag(g, f.x(cx) - 120, f.y(cy) - 40, "THE WORKS: the industry you named", ORANGE);
        System.out.printf(Locale.ROOT, "[raid-directions] industrial cells %d, centre %.0f,%.0f%n", count, cx, cy);
        return new float[]{cx, cy};
    }

    /** Up to five machines in the works: non-residential buildings standing on industrial ground, spaced. */
    private static List<float[]> machines(MapResult map) {
        List<float[]> out = new ArrayList<>();
        for (PointOfInterest p : map.pointsOfInterest) {
            if (out.size() >= 5 || p.kind == PointOfInterest.Kind.RESIDENTIAL) continue;
            if (map.biomeMap == null || map.biomeMap.themeAt(p.interiorAnchorX, p.interiorAnchorY)
                    != MapDistrictTheme.INDUSTRIAL) continue;
            float x = p.interiorAnchorX + 0.5f, y = p.interiorAnchorY + 0.5f;
            boolean near = false;
            for (float[] o : out) near |= Math.hypot(o[0] - x, o[1] - y) < 18;
            if (!near) out.add(new float[]{x, y});
        }
        // The industrial zone's working points stand for the machines where no district is themed.
        List<float[]> points = new ArrayList<>();
        for (IndustrialZone z : map.industrialZones) {
            for (ServiceLane lane : z.lanes()) {
                points.add(new float[]{(lane.gateAX() + lane.gateBX()) / 2f + 0.5f,
                        (lane.gateAY() + lane.gateBY()) / 2f + 0.5f});
            }
            for (LoadingBay b : z.bays()) points.add(new float[]{b.standX(), b.standY()});
            for (IndustrialZone.Dock d : z.docks()) points.add(new float[]{d.doorX() + 0.5f, d.doorY() + 0.5f});
            float half = z.width() / 2f + 12;
            for (PointOfInterest p : map.pointsOfInterest) {
                if (p.interiorAnchorX >= Math.min(z.startX(), z.endX()) - half
                        && p.interiorAnchorX <= Math.max(z.startX(), z.endX()) + half
                        && p.interiorAnchorY >= Math.min(z.startY(), z.endY()) - half
                        && p.interiorAnchorY <= Math.max(z.startY(), z.endY()) + half) {
                    points.add(new float[]{p.interiorAnchorX + 0.5f, p.interiorAnchorY + 0.5f});
                }
            }
        }
        for (float[] p : points) {
            if (out.size() >= 5) break;
            boolean near = false;
            for (float[] o : out) near |= Math.hypot(o[0] - p[0], o[1] - p[1]) < 9;
            if (!near) out.add(p);
        }
        return out;
    }

    private static void facility(Graphics2D g, Frame f, CordonedFacility c, String label) {
        int x0 = f.x(c.left()), x1 = f.x(c.right() + 1), y0 = f.y(c.bottom() + 1), y1 = f.y(c.top());
        g.setColor(new Color(255, 140, 30, 50));
        g.fillRect(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0));
        g.setColor(ORANGE);
        g.setStroke(new BasicStroke(5f));
        g.drawRect(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (CordonedFacility.Gatehouse gate : c.gatehouses()) {
            int gx = f.x((gate.outX() + gate.inX() + 1) / 2f), gy = f.y((gate.outY() + gate.inY() + 1) / 2f);
            g.setColor(GOLD);
            g.fillRect(gx - 9, gy - 9, 18, 18);
        }
        tag(g, Math.min(x0, x1) - 60, Math.max(y0, y1) + 30, label, ORANGE);
    }

    private static void goods(Graphics2D g, Frame f, PointOfInterest p, String label, List<float[]> placed) {
        if (p == null) return;
        float x = p.interiorAnchorX + 0.5f, y = p.interiorAnchorY + 0.5f;
        crate(g, f.x(x), f.y(y), 3);
        tag(g, f.x(x) - 40, f.y(y) + 40, label, GOLD);
        placed.add(new float[]{x, y});
    }

    /** The company's real lift: one Hermes on its berth, and the two berths a company may buy drawn as ghosts. */
    private static void lift(Graphics2D g, Frame f, List<LandingPad> pads) {
        if (!pads.isEmpty()) {
            LandingPad boat = pads.get(0);
            drawPads(g, f, List.of(boat), GOLD);
            tag(g, f.x(boat.centerX) - 50, f.y(boat.centerY) + 40, "Hermes", GOLD);
            Stroke dash = new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{8f, 8f}, 0f);
            for (int i = 1; i < pads.size(); i++) {
                LandingPad p = pads.get(i);
                g.setStroke(dash);
                g.setColor(new Color(255, 214, 40, 150));
                int x0 = f.x(p.centerX - p.halfWidth), x1 = f.x(p.centerX + p.halfWidth + 1);
                int y0 = f.y(p.centerY + p.halfHeight + 1), y1 = f.y(p.centerY - p.halfHeight);
                g.drawRect(x0, y0, x1 - x0, y1 - y0);
            }
        }
        tag(g, 28, H - 74, "Lift: one Hermes, 12 seats: one squad a trip, then back for the next.", GOLD);
        tag(g, 28, H - 44, "Berths 2 and 3 (dashed) only once bought.", GOLD);
    }

    private static void alarm(Graphics2D g, Frame f, MapResult map) {
        int dx = f.x(map.defenderSpawnX), dy = f.y(map.defenderSpawnY);
        Stroke dash = new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[]{14f, 10f}, 0f);
        for (int i = 1; i <= 2; i++) {
            int r = i * f.ppc() * 40;
            g.setColor(new Color(70, 220, 255, 180 - i * 50));
            g.setStroke(dash);
            g.drawOval(dx - r, dy - r, 2 * r, 2 * r);
        }
        tag(g, dx - 50, dy + 30, "garrison", CYAN);
    }

    private static void carry(Graphics2D g, int x0, int y0, int x1, int y1) {
        g.setColor(new Color(255, 214, 40, 200));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                new float[]{6f, 10f}, 0f));
        g.drawLine(x0, y0, x1, y1);
    }

    /** The places an inset must not cover: the berths, the garrison, the frame's subject and the marks given. */
    private static List<float[]> avoid(MapResult map, List<LandingPad> pads, float x, float y, List<float[]> more) {
        List<float[]> out = new ArrayList<>(more);
        for (LandingPad p : pads) out.add(new float[]{p.centerX, p.centerY});
        out.add(new float[]{map.defenderSpawnX, map.defenderSpawnY});
        out.add(new float[]{x, y});
        return out;
    }

    // ---- drawing ----

    private static void inset(SnapshotContext context, BattleSimulation sim, Graphics2D g, float cellX, float cellY,
                              float cellsAcross, Frame f, List<float[]> avoid, String caption) throws Exception {
        BufferedImage close = BattleReviewFrameRenderer.look(context.modRoot(), W, H, cellX, cellY,
                Math.max(cellsAcross, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS)).render(sim, "");
        int iw = 640, ih = 360, m = 24;
        // The corner whose nearest marked place is farthest away.
        int[][] corners = {{m, 130}, {W - iw - m, 130}, {m, H - ih - m}, {W - iw - m, H - ih - m}};
        int x = m, y = 130;
        double best = -1;
        for (int[] c : corners) {
            double nearest = Double.MAX_VALUE;
            for (float[] a : avoid) {
                double ddx = Math.max(Math.max(c[0] - f.x(a[0]), 0), f.x(a[0]) - (c[0] + iw));
                double ddy = Math.max(Math.max(c[1] - f.y(a[1]), 0), f.y(a[1]) - (c[1] + ih));
                nearest = Math.min(nearest, Math.hypot(ddx, ddy));
            }
            if (nearest > best) {
                best = nearest;
                x = c[0];
                y = c[1];
            }
        }
        g.drawImage(close.getScaledInstance(iw, ih, Image.SCALE_SMOOTH), x, y, null);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3f));
        g.drawRect(x, y, iw, ih);
        tag(g, x + 8, y + ih - 12, caption, Color.WHITE);
    }

    private static void banner(Graphics2D g, String title, String line) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        int tw = g.getFontMetrics().stringWidth(title);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 21));
        int lw = g.getFontMetrics().stringWidth(line);
        g.setColor(SHADE);
        g.fillRect(14, 14, Math.max(tw, lw) + 28, 92);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        g.drawString(title, 28, 54);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 21));
        g.drawString(line, 28, 90);
    }

    private static void tag(Graphics2D g, int x, int y, String text, Color color) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        int tw = g.getFontMetrics().stringWidth(text);
        g.setColor(SHADE);
        g.fillRect(x - 4, y - 19, tw + 8, 25);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private static void drawPads(Graphics2D g, Frame f, List<LandingPad> pads, Color color) {
        g.setStroke(new BasicStroke(4f));
        g.setColor(color);
        for (LandingPad p : pads) {
            int x0 = f.x(p.centerX - p.halfWidth), x1 = f.x(p.centerX + p.halfWidth + 1);
            int y0 = f.y(p.centerY + p.halfHeight + 1), y1 = f.y(p.centerY - p.halfHeight);
            g.drawRect(x0, y0, x1 - x0, y1 - y0);
        }
    }

    private static void zoneOutline(Graphics2D g, Frame f, IndustrialZone z, Color color) {
        float half = z.width() / 2f + 12;
        int x0 = f.x(Math.min(z.startX(), z.endX()) - half), x1 = f.x(Math.max(z.startX(), z.endX()) + half);
        int y0 = f.y(Math.max(z.startY(), z.endY()) + half), y1 = f.y(Math.min(z.startY(), z.endY()) - half);
        g.setColor(color);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{16f, 8f}, 0f));
        g.drawRect(x0, y0, x1 - x0, y1 - y0);
    }

    private static void ring(Graphics2D g, int x, int y, int r, Color color, float width) {
        g.setStroke(new BasicStroke(width));
        g.setColor(color);
        g.drawOval(x - r, y - r, 2 * r, 2 * r);
    }

    private static void numbered(Graphics2D g, int x, int y, int n, Color color) {
        g.setColor(color);
        g.fillOval(x - 16, y - 16, 32, 32);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(x - 16, y - 16, 32, 32);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        String s = Integer.toString(n);
        g.drawString(s, x - g.getFontMetrics().stringWidth(s) / 2, y + 7);
    }

    private static void crate(Graphics2D g, int x, int y, int value) {
        int r = 7 + value * 4;
        int[] xs = {x, x + r, x, x - r}, ys = {y - r, y, y + r, y};
        g.setColor(new Color(255, 214, 40, 230));
        g.fillPolygon(xs, ys, 4);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2f));
        g.drawPolygon(xs, ys, 4);
    }

    private static void cross(Graphics2D g, int x, int y, Color color) {
        g.setColor(SHADE);
        g.fillOval(x - 16, y - 16, 32, 32);
        g.setColor(color);
        g.setStroke(new BasicStroke(5f));
        g.drawLine(x - 10, y - 10, x + 10, y + 10);
        g.drawLine(x - 10, y + 10, x + 10, y - 10);
    }

    private static void arrow(Graphics2D g, int x0, int y0, int x1, int y1, Color color, boolean twoWay) {
        g.setColor(color);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{20f, 12f}, 0f));
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len < 1) return;
        double ux = (x1 - x0) / len, uy = (y1 - y0) / len;
        int ex = (int) (x1 - ux * 34), ey = (int) (y1 - uy * 34);
        g.drawLine(x0, y0, ex, ey);
        head(g, ex, ey, ux, uy);
        if (twoWay) head(g, (int) (x0 + ux * 4), (int) (y0 + uy * 4), -ux, -uy);
    }

    private static void head(Graphics2D g, int x, int y, double ux, double uy) {
        int s = 18;
        int[] xs = {(int) (x + ux * s), (int) (x - uy * s * 0.6), (int) (x + uy * s * 0.6)};
        int[] ys = {(int) (y + uy * s), (int) (y + ux * s * 0.6), (int) (y - ux * s * 0.6)};
        g.fillPolygon(xs, ys, 3);
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g;
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}
