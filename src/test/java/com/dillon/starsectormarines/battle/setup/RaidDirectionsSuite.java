package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.CordonedFacility;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.IndustrialZone;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.LoadingBay;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.OpenSquare;
import com.dillon.starsectormarines.battle.world.gen.road.RoadReservation;
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
 * Raid redesign directions for the owner's pick ({@code raid-direction}): one established Raid map (280x168,
 * look preset, roofs on) drawn three times, each with one direction's mock-up laid over the real marks the map
 * publishes, and a close look at the place that direction is about in a corner. {@code 00-today.png} is the map
 * as the Raid plays it now: berths and the one prize. Ground only: nothing is played.
 *
 * <ul>
 *   <li>{@code a-works}: the raid is the walled works (the cordoned facility, else the industrial zone);</li>
 *   <li>{@code b-haul}: stores across the town, the take is what reaches the boats;</li>
 *   <li>{@code c-getaway}: a near prize, then a cordon closing on the town's roads.</li>
 * </ul>
 *
 * <p>Seeds are scanned for a Raid map that lays a cordoned facility and an industrial zone; each seed's marks
 * are printed.
 */
public final class RaidDirectionsSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final int SHUTTLES = 4;
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
            System.out.printf(Locale.ROOT, "[%s] seed %d: facilities %d, industrial zones %d, main streets %d, "
                            + "open squares %d, loading bays %d, points of interest %d%n", id(), seed,
                    map.cordonedFacilities.size(), map.industrialZones.size(), map.mainStreets.size(),
                    map.openSquares.size(), map.loadingBays.size(), map.pointsOfInterest.size());
            if (score > bestScore) {
                best = new Pick(seed, map);
                bestScore = score;
            }
            if (score == 3) break;
        }
        MapResult map = best.map();
        long seed = best.seed();
        System.out.printf(Locale.ROOT, "[%s] drawing seed %d%n", id(), seed);
        List<LandingPad> pads = LandingPadSelector.select(map, SHUTTLES, BattleSetup.LZ_MIN_SEPARATION);
        RaidTargetLayout target = RaidTargetLayout.select(map, pads);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.buildMap(map, List.of(), map.defensePosts, List.of(), seed).sim()) {
            int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
            Frame frame = new Frame(w / 2f, h / 2f, (int) Math.floor(W / (w * 1.04f)));
            BufferedImage base = BattleReviewFrameRenderer.look(context.modRoot(), W, H, w / 2f, h / 2f,
                    w * 1.04f).render(sim, "");
            out.add(new SnapshotArtifact("00-today.png", today(copy(base), frame, map, pads, target, seed)));
            out.add(new SnapshotArtifact("a-works.png", works(context, sim, copy(base), frame, map, pads)));
            out.add(new SnapshotArtifact("b-haul.png", haul(context, sim, copy(base), frame, map, pads)));
            out.add(new SnapshotArtifact("c-getaway.png", getaway(context, sim, copy(base), frame, map, pads, target)));
        }
        return out;
    }

    /** World cell to frame pixel, the renderer's whole pixels per cell. */
    private record Frame(float cx, float cy, int ppc) {
        int x(float cellX) { return Math.round(W / 2f + (cellX - cx) * ppc); }
        int y(float cellY) { return Math.round(H / 2f - (cellY - cy) * ppc); }
    }

    private static BufferedImage today(BufferedImage img, Frame f, MapResult map, List<LandingPad> pads,
                                       RaidTargetLayout target, long seed) {
        Graphics2D g = graphics(img);
        drawPads(g, f, pads, GOLD);
        ring(g, f.x(target.targetCellX() + 0.5f), f.y(target.targetCellY() + 0.5f), 26, RED, 4f);
        tag(g, f.x(target.targetCellX() + 0.5f) - 140, f.y(target.targetCellY() + 0.5f) + 50,
                "the prize: " + target.targetName(), RED);
        arrow(g, f.x(pads.get(0).centerX), f.y(pads.get(0).centerY),
                f.x(target.targetCellX()), f.y(target.targetCellY()), RED, true);
        banner(g, "Today: land, walk to one building, stand 6 s, walk back",
                "Established Raid 280x168. The prize is the non-residential building farthest from the pads; "
                        + "the raid type and target industry never reach the map.");
        g.dispose();
        return img;
    }

    private static BufferedImage works(SnapshotContext context, BattleSimulation sim, BufferedImage img, Frame f,
                                       MapResult map, List<LandingPad> pads) throws Exception {
        Graphics2D g = graphics(img);
        float fx, fy, fw;
        if (!map.cordonedFacilities.isEmpty()) {
            CordonedFacility c = map.cordonedFacilities.get(0);
            fx = (c.left() + c.right() + 1) / 2f;
            fy = (c.top() + c.bottom() + 1) / 2f;
            fw = Math.max(c.right() - c.left(), (c.bottom() - c.top()) * 16f / 9f) + 14;
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
                tag(g, gx + 12, gy - 12, "gate", GOLD);
            }
            int n = 1;
            for (CordonedFacility.Building b : c.buildings()) {
                if (b.doorX() < 0 || n > 4) continue;
                int bx = f.x((b.left() + b.right() + 1) / 2f), by = f.y((b.top() + b.bottom() + 1) / 2f);
                numbered(g, bx, by, n++, RED);
            }
            tag(g, Math.min(x0, x1), Math.min(y0, y1) - 14, "THE WORKS: the named industry, walled", ORANGE);
        } else {
            IndustrialZone z = map.industrialZones.get(0);
            fx = (z.startX() + z.endX()) / 2f;
            fy = (z.startY() + z.endY()) / 2f;
            fw = Math.max(Math.abs(z.endX() - z.startX()), Math.abs(z.endY() - z.startY()) * 16f / 9f) + 30;
            zoneOutline(g, f, z, ORANGE);
            int n = 1;
            for (LoadingBay b : z.bays()) {
                if (n > 4) break;
                numbered(g, f.x(b.standX()), f.y(b.standY()), n++, RED);
            }
            for (IndustrialZone.Dock d : z.docks()) {
                if (n > 4) break;
                numbered(g, f.x(d.doorX() + 0.5f), f.y(d.doorY() + 0.5f), n++, RED);
            }
        }
        drawPads(g, f, pads, GOLD);
        arrow(g, f.x(map.defenderSpawnX), f.y(map.defenderSpawnY), f.x(fx), f.y(fy), CYAN, false);
        tag(g, f.x(map.defenderSpawnX) - 60, f.y(map.defenderSpawnY) + 34, "garrison response", CYAN);
        inset(context, sim, g, fx, fy, fw, f, avoid(map, pads, fx, fy), "close: the works at play zoom");
        banner(g, "A. The Works: the raid is the place you picked",
                "Disrupt: the named industry's works, walled, with machines (1-4) to wreck. Valuables: its stores. "
                        + "Land a block off the wall, pick a gate, the take scales with what you wreck or strip.");
        g.dispose();
        return img;
    }

    private static BufferedImage haul(SnapshotContext context, BattleSimulation sim, BufferedImage img, Frame f,
                                      MapResult map, List<LandingPad> pads) throws Exception {
        Graphics2D g = graphics(img);
        // Alarm rings round the garrison.
        int dx = f.x(map.defenderSpawnX), dy = f.y(map.defenderSpawnY);
        Stroke dash = new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{14f, 10f}, 0f);
        for (int i = 1; i <= 3; i++) {
            int r = i * f.ppc() * 34;
            g.setColor(new Color(70, 220, 255, 200 - i * 40));
            g.setStroke(dash);
            g.drawOval(dx - r, dy - r, 2 * r, 2 * r);
            tag(g, dx - 40, dy - r + 4, "alarm " + i, CYAN);
        }
        tag(g, dx - 50, dy + 30, "garrison", CYAN);
        // Caches: the zone's warehouses and bays, then the stores among the points of interest.
        float ix = -1, iy = -1, iw = 70;
        int caches = 0;
        List<float[]> placed = new ArrayList<>();
        for (IndustrialZone z : map.industrialZones) {
            zoneOutline(g, f, z, new Color(255, 214, 40, 160));
            if (ix < 0) {
                ix = (z.startX() + z.endX()) / 2f;
                iy = (z.startY() + z.endY()) / 2f;
            }
            for (IndustrialZone.Dock d : z.docks()) {
                if (spaced(placed, d.doorX(), d.doorY())) {
                    crate(g, f.x(d.doorX() + 0.5f), f.y(d.doorY() + 0.5f), 2);
                    caches++;
                }
            }
            for (LoadingBay b : z.bays()) {
                if (spaced(placed, b.standX(), b.standY())) {
                    crate(g, f.x(b.standX()), f.y(b.standY()), 2);
                    caches++;
                }
            }
        }
        // The named industry is the headline prize: its works, to wreck or strip.
        if (!map.cordonedFacilities.isEmpty()) {
            CordonedFacility c = map.cordonedFacilities.get(0);
            float wx = (c.left() + c.right() + 1) / 2f, wy = (c.top() + c.bottom() + 1) / 2f;
            placed.add(new float[]{wx, wy});
            ring(g, f.x(wx), f.y(wy), 40, RED, 5f);
            tag(g, f.x(wx) - 120, f.y(wy) + 64, "the named industry's works", RED);
        }
        for (PointOfInterest p : map.pointsOfInterest) {
            int value = switch (p.kind) {
                case ADMINISTRATIVE, LABORATORY -> 3;
                case DEPOT, COMMS -> 2;
                case MEDICAL -> 1;
                default -> 0;
            };
            if (value < 2 || !spaced(placed, p.interiorAnchorX, p.interiorAnchorY)) continue;
            crate(g, f.x(p.interiorAnchorX + 0.5f), f.y(p.interiorAnchorY + 0.5f), value);
            caches++;
            if (ix < 0 && p.kind == PointOfInterest.Kind.DEPOT) {
                ix = p.interiorAnchorX;
                iy = p.interiorAnchorY;
            }
        }
        if (ix < 0) {
            ix = map.defenderSpawnX;
            iy = map.defenderSpawnY;
        }
        drawPads(g, f, pads, GOLD);
        for (LandingPad p : pads) tag(g, f.x(p.centerX) - 20, f.y(p.centerY) + 34, "boat", GOLD);
        inset(context, sim, g, ix, iy, iw, f, avoid(map, pads, ix, iy), "close: warehouses on the service street");
        banner(g, "B. The Haul: choose what to take before the town wakes",
                caches + " stores across the market (bigger = worth more), the named industry the richest. Load and carry to "
                        + "the boats: the take is what reaches a boat. The alarm climbs until the boats must lift.");
        g.dispose();
        return img;
    }

    private static BufferedImage getaway(SnapshotContext context, BattleSimulation sim, BufferedImage img, Frame f,
                                         MapResult map, List<LandingPad> pads, RaidTargetLayout target)
            throws Exception {
        Graphics2D g = graphics(img);
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        // The prize sits near the landing; the cordon is a ring round it the garrison closes on the roads.
        LandingPad first = pads.get(0);
        float px = first.centerX, py = first.centerY;
        PointOfInterest near = null;
        int nearD = Integer.MAX_VALUE;
        for (PointOfInterest p : map.pointsOfInterest) {
            if (p.kind == PointOfInterest.Kind.RESIDENTIAL || p.kind == PointOfInterest.Kind.LANDING_SITE) continue;
            int ddx = p.interiorAnchorX - first.centerX, ddy = p.interiorAnchorY - first.centerY;
            int d = ddx * ddx + ddy * ddy;
            if (d > 400 && d < nearD) {
                nearD = d;
                near = p;
            }
        }
        if (near != null) {
            px = near.interiorAnchorX;
            py = near.interiorAnchorY;
        }
        ring(g, f.x(px + 0.5f), f.y(py + 0.5f), 26, RED, 4f);
        tag(g, f.x(px) - 60, f.y(py) - 36, "the prize, near", RED);
        drawPads(g, f, pads, new Color(255, 214, 40, 120));
        tag(g, f.x(first.centerX) - 70, f.y(first.centerY) + 34, "landing (boats leave)", GOLD);
        float radius = Math.min(w, h) * 0.42f;
        int rr = Math.round(radius * f.ppc());
        g.setColor(new Color(235, 50, 40, 190));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{18f, 12f}, 0f));
        g.drawOval(f.x(px) - rr, f.y(py) - rr, 2 * rr, 2 * rr);
        // Checkpoints where the town's roads cross the ring.
        boolean[][] roads = RoadReservation.mask(map.roadGraph, w, h);
        List<int[]> checkpoints = new ArrayList<>();
        for (int a = 0; a < 720; a++) {
            double t = Math.PI * 2 * a / 720.0;
            int cx = Math.round(px + (float) Math.cos(t) * radius), cy = Math.round(py + (float) Math.sin(t) * radius);
            if (cx < 1 || cy < 1 || cx >= w - 1 || cy >= h - 1 || !roads[cx][cy]) continue;
            boolean close = false;
            for (int[] c : checkpoints) close |= Math.abs(c[0] - cx) + Math.abs(c[1] - cy) < 12;
            if (!close) checkpoints.add(new int[]{cx, cy});
        }
        for (int[] c : checkpoints) cross(g, f.x(c[0] + 0.5f), f.y(c[1] + 0.5f), RED);
        // Pickup lots: open squares and landing sites outside the ring, where a boat can come back in.
        int lots = 0;
        for (LandingPad lot : LandingPadSelector.select(map, 24, BattleSetup.LZ_MIN_SEPARATION)) {
            float ddx = lot.centerX - px, ddy = lot.centerY - py;
            if (ddx * ddx + ddy * ddy < radius * radius * 1.1f || lots >= 5) continue;
            ring(g, f.x(lot.centerX + 0.5f), f.y(lot.centerY + 0.5f), 22, GREEN, 4f);
            tag(g, f.x(lot.centerX) - 30, f.y(lot.centerY) + 40, "pickup?", GREEN);
            lots++;
        }
        for (OpenSquare s : map.openSquares) {
            float ddx = s.centreX() - px, ddy = s.centreY() - py;
            if (ddx * ddx + ddy * ddy < radius * radius * 0.5f) continue;
            ring(g, f.x(s.centreX() + 0.5f), f.y(s.centreY() + 0.5f), 20, GREEN, 4f);
            tag(g, f.x(s.centreX()) - 30, f.y(s.centreY()) + 38, "pickup?", GREEN);
            lots++;
        }
        for (PointOfInterest p : map.pointsOfInterest) {
            if (p.kind != PointOfInterest.Kind.LANDING_SITE) continue;
            ring(g, f.x(p.anchorCellX + 0.5f), f.y(p.anchorCellY + 0.5f), 20, GREEN, 4f);
            tag(g, f.x(p.anchorCellX) - 30, f.y(p.anchorCellY) + 38, "pickup?", GREEN);
            lots++;
        }
        arrow(g, f.x(map.defenderSpawnX), f.y(map.defenderSpawnY), f.x(px), f.y(py), CYAN, false);
        tag(g, f.x(map.defenderSpawnX) - 60, f.y(map.defenderSpawnY) + 34, "garrison sweep", CYAN);
        System.out.printf(Locale.ROOT, "[raid-directions] getaway: %d checkpoints, %d pickup lots%n",
                checkpoints.size(), lots);
        float ix = px, iy = py;
        if (!checkpoints.isEmpty()) {
            ix = checkpoints.get(0)[0];
            iy = checkpoints.get(0)[1];
        }
        inset(context, sim, g, ix, iy, 60, f, avoid(map, pads, px, py), "close: a checkpoint street");
        banner(g, "C. The Getaway: the grab is quick, getting out is the fight",
                "Land beside the prize and take it fast. The alarm closes a cordon: checkpoints (X) on the roads out. "
                        + "The boats left; call one to a lot you can hold (green) and fight your way to it.");
        g.dispose();
        return img;
    }

    /** Whether a cache at the cell is clear of every one placed, and if so places it. */
    private static boolean spaced(List<float[]> placed, float x, float y) {
        for (float[] p : placed) {
            if (Math.hypot(p[0] - x, p[1] - y) < 22) return false;
        }
        placed.add(new float[]{x, y});
        return true;
    }

    /** The places an inset must not cover: the berths, the garrison and the frame's subject. */
    private static List<float[]> avoid(MapResult map, List<LandingPad> pads, float x, float y) {
        List<float[]> out = new ArrayList<>();
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
