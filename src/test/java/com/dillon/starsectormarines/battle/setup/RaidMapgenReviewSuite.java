package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.CordonedFacility;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.IndustrialZone;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MainStreet;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
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
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The Raid map generator under review (owner: "this is just bad. This is a bad map generator"): today's Raid maps
 * for three markets (a mining world, a farming world, a military and heavy-industry world) at a first contract and
 * established, two seeds each, plus the Raid evidence fixture's market, and for comparison an Assault and an
 * Extraction map ({@code NN-*.png}), whole and fitted to the frame through the look preset, roofs on, each with the
 * marks the generator publishes drawn over it: the boat's berth and the two a company buys (yellow), the prize the
 * Raid picks (red ring), the set-piece districts (main street green, industrial zone orange, cordoned facility cyan),
 * the defender's spawn and emplacements (red). Then the generator directions for the owner's pick
 * ({@code d1-*}, {@code d2-*}, {@code d3-*}): mock-ups drawn over a real established Raid map, labelled as such.
 * Ground only: nothing is played. Prints each map's points of interest by kind and its districts.
 */
public final class RaidMapgenReviewSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;

    private static final Color GOLD = new Color(255, 214, 40);
    private static final Color RED = new Color(235, 50, 40);
    private static final Color ORANGE = new Color(255, 140, 30);
    private static final Color CYAN = new Color(70, 220, 255);
    private static final Color GREEN = new Color(90, 240, 120);
    private static final Color VIOLET = new Color(200, 120, 255);
    private static final Color SHADE = new Color(0, 0, 0, 180);

    private record Market(String key, String name, TargetProfile profile) { }

    private static final Market MINING = new Market("mining", "mining world (mining, refining), size 4, arid",
            new TargetProfile(4, 5, 2, 1, "independent", EnumSet.of(EconomicFunction.MINING, EconomicFunction.REFINING),
                    SurfacePalette.ARID, SettlementLink.ROAD));
    private static final Market FARMING = new Market("farming",
            "farming world (agriculture, commerce, habitation), size 5, verdant",
            new TargetProfile(5, 6, 2, 1, "independent",
                    EnumSet.of(EconomicFunction.AGRICULTURE, EconomicFunction.COMMERCE, EconomicFunction.HABITATION),
                    SurfacePalette.VERDANT, SettlementLink.ROAD));
    private static final Market MILITARY = new Market("military",
            "military world (military, heavy industry, spaceport), size 6, rock",
            new TargetProfile(6, 5, 6, 3, "independent",
                    EnumSet.of(EconomicFunction.MILITARY, EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.SPACEPORT),
                    SurfacePalette.ROCK, SettlementLink.ROAD));
    /** The Raid evidence fixture's market, the one the owner saw. */
    private static final Market FIXTURE = new Market("fixture",
            "fixture market (spaceport, heavy industry, commerce), size 6, rock",
            new TargetProfile(6, 5, 4, 2, "independent",
                    EnumSet.of(EconomicFunction.SPACEPORT, EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.COMMERCE),
                    SurfacePalette.ROCK, SettlementLink.ROAD));

    private static final long[] SEEDS = {141_418L, 7L};
    private static final long FIXTURE_SEED = 141_418L;

    @Override public String id() { return "raid-mapgen-review"; }

    @Override public String label() { return "Today's Raid maps across markets and seeds, and generator directions"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        int n = 1;
        for (Market market : List.of(MINING, FARMING, MILITARY)) {
            for (OperationTier tier : List.of(OperationTier.FIRST_CONTRACT, OperationTier.ESTABLISHED)) {
                for (long seed : SEEDS) {
                    String file = String.format(Locale.ROOT, "%02d-raid-%s-%s-seed%d.png", n++, market.key(),
                            tierKey(tier), seed);
                    out.add(new SnapshotArtifact(file, diagnosis(context, MissionType.RAID, tier, market, seed,
                            MapScale.forRaid(tier), file)));
                }
            }
        }
        String fixtureFile = String.format(Locale.ROOT, "%02d-raid-fixture-established-seed%d.png", n++, FIXTURE_SEED);
        out.add(new SnapshotArtifact(fixtureFile, diagnosis(context, MissionType.RAID, OperationTier.ESTABLISHED,
                FIXTURE, FIXTURE_SEED, MapScale.forRaid(OperationTier.ESTABLISHED), fixtureFile)));
        String assaultFile = String.format(Locale.ROOT, "%02d-assault-military-established-seed%d.png", n++, SEEDS[0]);
        out.add(new SnapshotArtifact(assaultFile, diagnosis(context, MissionType.ASSAULT, OperationTier.ESTABLISHED,
                MILITARY, SEEDS[0], MapScale.forTier(OperationTier.ESTABLISHED), assaultFile)));
        String extractionFile = String.format(Locale.ROOT, "%02d-extraction-farming-established-seed%d.png", n,
                SEEDS[0]);
        out.add(new SnapshotArtifact(extractionFile, diagnosis(context, MissionType.EXTRACTION,
                OperationTier.ESTABLISHED, FARMING, SEEDS[0], MapScale.forTier(OperationTier.ESTABLISHED),
                extractionFile)));
        out.addAll(directions(context));
        return out;
    }

    private static String tierKey(OperationTier tier) {
        return tier == OperationTier.FIRST_CONTRACT ? "first-contract" : "established";
    }

    // ---- today's maps ----

    private static MapResult map(MissionType type, OperationTier tier, Market market, long seed, MapScale scale) {
        RiskLevel risk = RiskLevel.MEDIUM;
        PrecinctPlan precincts = BattleSetup.precinctPlanFor(type, tier, risk, market.profile(), scale, seed, null);
        return BattleSetup.proceduralOperationMap(type, tier, risk, market.profile(), scale, seed, null, precincts);
    }

    /** World cell to frame pixel, the renderer's whole pixels per cell. */
    private record Frame(float cx, float cy, int ppc) {
        int x(float cellX) { return Math.round(W / 2f + (cellX - cx) * ppc); }
        int y(float cellY) { return Math.round(H / 2f - (cellY - cy) * ppc); }
    }

    private record Shot(MapResult map, BufferedImage image, Frame frame, List<LandingPad> pads,
                        RaidTargetLayout target) { }

    private static Shot shoot(SnapshotContext context, MissionType type, OperationTier tier, Market market, long seed,
                              MapScale scale) throws Exception {
        MapResult map = map(type, tier, market, seed, scale);
        List<LandingPad> pads = LandingPadSelector.select(map, 3, BattleSetup.LZ_MIN_SEPARATION);
        RaidTargetLayout target = type == MissionType.RAID ? RaidTargetLayout.select(map, pads) : null;
        try (BattleSimulation sim = BattleSetup.buildMap(map, List.of(), map.defensePosts, List.of(), seed).sim()) {
            int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
            float across = w * 1.04f;
            Frame frame = new Frame(w / 2f, h / 2f, (int) Math.floor(W / across));
            BufferedImage image = BattleReviewFrameRenderer.look(context.modRoot(), W, H, w / 2f, h / 2f, across)
                    .render(sim, "");
            return new Shot(map, image, frame, pads, target);
        }
    }

    private static BufferedImage diagnosis(SnapshotContext context, MissionType type, OperationTier tier,
                                           Market market, long seed, MapScale scale, String file) throws Exception {
        Shot shot = shoot(context, type, tier, market, seed, scale);
        MapResult map = shot.map();
        Frame f = shot.frame();
        Map<PointOfInterest.Kind, Integer> kinds = new EnumMap<>(PointOfInterest.Kind.class);
        for (PointOfInterest p : map.pointsOfInterest) kinds.merge(p.kind, 1, Integer::sum);
        String poi = kinds.entrySet().stream()
                .map(e -> e.getKey().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + e.getValue())
                .collect(Collectors.joining(", "));
        System.out.printf(Locale.ROOT, "[raid-mapgen-review] %s: %dx%d, POIs %d (%s), main streets %d, industrial "
                        + "zones %d, cordoned facilities %d, defence posts %d, landing pads %d, target %s%n",
                file, map.grid.getWidth(), map.grid.getHeight(), map.pointsOfInterest.size(), poi,
                map.mainStreets.size(), map.industrialZones.size(), map.cordonedFacilities.size(),
                map.defensePosts.size(), map.landingPads.size(),
                shot.target() == null ? "-" : shot.target().targetName());

        BufferedImage img = shot.image();
        Graphics2D g = graphics(img);
        drawMarks(g, f, map, shot.pads(), shot.target());
        String title = (type == MissionType.RAID ? "Raid" : type == MissionType.ASSAULT ? "Assault" : "Extraction")
                + ", " + tierKey(tier).replace('-', ' ') + "  " + scale.width + "x" + scale.height + ", seed " + seed;
        banner(g, title, market.name() + ".  POIs " + map.pointsOfInterest.size() + ": " + poi);
        legend(g, map);
        g.dispose();
        return img;
    }

    private static void drawMarks(Graphics2D g, Frame f, MapResult map, List<LandingPad> pads,
                                  RaidTargetLayout target) {
        for (MainStreet s : map.mainStreets) {
            g.setColor(GREEN);
            g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(f.x(s.startX()), f.y(s.startY()), f.x(s.endX()), f.y(s.endY()));
        }
        for (IndustrialZone z : map.industrialZones) {
            float half = z.width() / 2f + 10;
            box(g, f, Math.min(z.startX(), z.endX()) - half, Math.min(z.startY(), z.endY()) - half,
                    Math.max(z.startX(), z.endX()) + half, Math.max(z.startY(), z.endY()) + half, ORANGE, true);
        }
        for (CordonedFacility c : map.cordonedFacilities) {
            box(g, f, Math.min(c.left(), c.right()), Math.min(c.top(), c.bottom()),
                    Math.max(c.left(), c.right()) + 1, Math.max(c.top(), c.bottom()) + 1, CYAN, true);
        }
        g.setColor(RED);
        for (DefensePost d : map.defensePosts) {
            int x = f.x(d.anchorX + 0.5f), y = f.y(d.anchorY + 0.5f);
            g.fillRect(x - 4, y - 4, 8, 8);
        }
        cross(g, f.x(map.defenderSpawnX + 0.5f), f.y(map.defenderSpawnY + 0.5f), RED);
        g.setStroke(new BasicStroke(4f));
        for (int i = 0; i < pads.size(); i++) {
            LandingPad p = pads.get(i);
            g.setColor(i == 0 ? GOLD : new Color(255, 214, 40, 130));
            int x0 = f.x(p.centerX - p.halfWidth), x1 = f.x(p.centerX + p.halfWidth + 1);
            int y0 = f.y(p.centerY + p.halfHeight + 1), y1 = f.y(p.centerY - p.halfHeight);
            g.drawRect(x0, y0, x1 - x0, y1 - y0);
        }
        if (target != null) {
            int tx = f.x(target.targetCellX() + 0.5f), ty = f.y(target.targetCellY() + 0.5f);
            ring(g, tx, ty, 26, RED, 5f);
        }
    }

    private static void legend(Graphics2D g, MapResult map) {
        int y = H - 18;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        String[] items = {"boat berth (+2 bought)", "Raid prize", "main street", "industrial zone",
                "cordoned facility", "defender spawn / emplacements"};
        Color[] colors = {GOLD, RED, GREEN, ORANGE, CYAN, RED};
        int x = 18;
        int total = 0;
        for (String s : items) total += g.getFontMetrics().stringWidth(s) + 52;
        g.setColor(SHADE);
        g.fillRect(8, y - 24, total + 10, 34);
        for (int i = 0; i < items.length; i++) {
            g.setColor(colors[i]);
            g.fillRect(x, y - 15, 22, 14);
            g.setColor(Color.WHITE);
            g.drawString(items[i], x + 28, y);
            x += g.getFontMetrics().stringWidth(items[i]) + 52;
        }
    }

    // ---- directions (mock-ups over a real map) ----

    private List<SnapshotArtifact> directions(SnapshotContext context) throws Exception {
        Shot shot = shoot(context, MissionType.RAID, OperationTier.ESTABLISHED, MILITARY, SEEDS[0],
                MapScale.forRaid(OperationTier.ESTABLISHED));
        List<SnapshotArtifact> out = new ArrayList<>();
        out.add(new SnapshotArtifact("d1-target-first.png", targetFirst(shot)));
        out.add(new SnapshotArtifact("d2-economy-town.png", economyTown(shot)));
        out.add(new SnapshotArtifact("d3-site-templates.png", siteTemplates(shot)));
        return out;
    }

    /** Map width and height in cells, read off the grid. */
    private static float mw(Shot s) { return s.map().grid.getWidth(); }

    private static float mh(Shot s) { return s.map().grid.getHeight(); }

    /**
     * Direction 1: a Raid-only plan seeded from the target. The works the player named at the deep end, the garrison
     * precinct beside it, a market town between, one Hermes lot inset into the town's near side, open country round
     * the edge. Mock-up: blocks drawn over today's map.
     */
    private static BufferedImage targetFirst(Shot shot) {
        BufferedImage img = dim(shot.image(), 0.55f);
        Graphics2D g = graphics(img);
        Frame f = shot.frame();
        float w = mw(shot), h = mh(shot);
        // country round the edge
        zone(g, f, 0, 0, w, h, new Color(120, 150, 90, 40), null, null);
        // town between
        blob(g, f, w * 0.20f, h * 0.14f, w * 0.60f, h * 0.80f, new Color(90, 240, 120, 70), GREEN,
                "MARKET TOWN: main street, homes, shops");
        // garrison precinct where today's map put it, its own wall
        blob(g, f, w * 0.65f, h * 0.40f, w * 0.99f, h * 0.88f, new Color(235, 50, 40, 90), RED,
                "GARRISON precinct (alarm waves)");
        // the works (the industry named in the raid menu), beside the garrison at the deep end
        blob(g, f, w * 0.62f, h * 0.04f, w * 0.97f, h * 0.36f, new Color(255, 140, 30, 95), ORANGE,
                "THE WORKS: heavy industry (Break target)");
        machine(g, f, w * 0.70f, h * 0.16f, "forge press");
        machine(g, f, w * 0.84f, h * 0.10f, "power plant");
        machine(g, f, w * 0.86f, h * 0.25f, "assembly hall");
        // stores of goods (Steal)
        crate(g, f, w * 0.40f, h * 0.62f, "admin vault");
        crate(g, f, w * 0.58f, h * 0.44f, "depot");
        crate(g, f, w * 0.56f, h * 0.22f, "warehouses");
        // boat lot inset into town
        float bx = w * 0.27f, by = h * 0.42f;
        pad(g, f, bx, by, "Hermes lot (1 boat, 2 bought)");
        // approaches
        arrow(g, f.x(bx + 8), f.y(by - 2), f.x(w * 0.68f), f.y(h * 0.18f), GOLD, false);
        arrow(g, f.x(bx + 8), f.y(by + 2), f.x(w * 0.39f), f.y(h * 0.60f), GOLD, false);
        // alarm waves out of the garrison
        arrow(g, f.x(w * 0.80f), f.y(h * 0.46f), f.x(w * 0.80f), f.y(h * 0.30f), RED, true);
        arrow(g, f.x(w * 0.68f), f.y(h * 0.56f), f.x(w * 0.46f), f.y(h * 0.52f), RED, true);
        mockStamp(g);
        banner(g, "1. Target first: works, garrison, town between (recommended)",
                "Raid-only plan: seed the named works at the deep end and the garrison beside it, grow the town "
                        + "between, inset the boat lot in the town. Shared generator gets one new program kind.");
        g.dispose();
        return img;
    }

    /**
     * Direction 2: a compact market town grown from the economy's districts, every function its own district round a
     * main street, country beyond. Shared: the town character all precinct maps take.
     */
    private static BufferedImage economyTown(Shot shot) {
        BufferedImage img = dim(shot.image(), 0.55f);
        Graphics2D g = graphics(img);
        Frame f = shot.frame();
        float w = mw(shot), h = mh(shot);
        // the town draws in: what was streets beyond it is country
        zone(g, f, 0, 0, w, h, new Color(70, 95, 50, 150), null, null);
        float cx = w * 0.52f, cy = h * 0.50f;
        // main street through the middle
        g.setColor(GREEN);
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(f.x(cx - w * 0.20f), f.y(cy), f.x(cx + w * 0.20f), f.y(cy));
        tag(g, f.x(cx - w * 0.10f), f.y(cy) - 14, "main street", GREEN);
        blob(g, f, cx - w * 0.24f, cy + h * 0.03f, cx - w * 0.02f, cy + h * 0.30f, new Color(255, 140, 30, 95),
                ORANGE, "HEAVY INDUSTRY district");
        blob(g, f, cx + w * 0.02f, cy + h * 0.03f, cx + w * 0.26f, cy + h * 0.30f, new Color(255, 214, 40, 80),
                GOLD, "SPACEPORT district");
        blob(g, f, cx - w * 0.22f, cy - h * 0.28f, cx - w * 0.01f, cy - h * 0.03f, new Color(90, 240, 120, 70),
                GREEN, "homes + civic core");
        blob(g, f, cx + w * 0.02f, cy - h * 0.30f, cx + w * 0.24f, cy - h * 0.03f, new Color(235, 50, 40, 90),
                RED, "MILITARY: garrison");
        machine(g, f, cx - w * 0.15f, cy + h * 0.14f, "key machine");
        crate(g, f, cx + w * 0.10f, cy + h * 0.12f, "cargo terminal");
        crate(g, f, cx - w * 0.12f, cy - h * 0.16f, "admin vault");
        pad(g, f, cx - w * 0.30f, cy - h * 0.02f, "Hermes lot");
        tag(g, f.x(w * 0.04f), f.y(h * 0.08f), "country: farms, scrub, roads out", new Color(200, 230, 170));
        mockStamp(g);
        banner(g, "2. Compact market town grown from its economy",
                "Shared: every economic function becomes its own district round one main street; the town is "
                        + "smaller and denser, country beyond. Changes Assault and Sabotage too; re-pool story maps.");
        g.dispose();
        return img;
    }

    /**
     * Direction 3: an authored site skeleton per industry (a Raid site template), varied by seed: fixed relations
     * between the works, the town strip, the garrison and the lot, the members drawn from the set-piece kit.
     */
    private static BufferedImage siteTemplates(Shot shot) {
        BufferedImage img = dim(shot.image(), 0.55f);
        Graphics2D g = graphics(img);
        Frame f = shot.frame();
        float w = mw(shot), h = mh(shot);
        zone(g, f, 0, 0, w, h, new Color(120, 150, 90, 40), null, null);
        // a road spine from the lot through the town, forking to the works and the garrison gate
        g.setColor(new Color(220, 220, 220));
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(f.x(w * 0.12f), f.y(h * 0.50f), f.x(w * 0.60f), f.y(h * 0.50f));
        g.drawLine(f.x(w * 0.60f), f.y(h * 0.50f), f.x(w * 0.74f), f.y(h * 0.30f));
        g.drawLine(f.x(w * 0.60f), f.y(h * 0.50f), f.x(w * 0.70f), f.y(h * 0.62f));
        zone(g, f, w * 0.08f, h * 0.36f, w * 0.22f, h * 0.62f, new Color(255, 214, 40, 70), GOLD, "LANDING LOT");
        zone(g, f, w * 0.24f, h * 0.26f, w * 0.58f, h * 0.74f, new Color(90, 240, 120, 70), GREEN, "TOWN STRIP");
        zone(g, f, w * 0.66f, h * 0.04f, w * 0.97f, h * 0.32f, new Color(255, 140, 30, 95), ORANGE,
                "WORKS TEMPLATE (per industry)");
        zone(g, f, w * 0.68f, h * 0.44f, w * 0.97f, h * 0.86f, new Color(235, 50, 40, 90), RED, "GARRISON TEMPLATE");
        machine(g, f, w * 0.72f, h * 0.12f, "key machine");
        machine(g, f, w * 0.84f, h * 0.20f, "key machine");
        machine(g, f, w * 0.90f, h * 0.10f, "key machine");
        crate(g, f, w * 0.40f, h * 0.60f, "vault");
        tag(g, f.x(w * 0.04f), f.y(h * 0.08f), "one skeleton per industry, mirrored/rotated per seed",
                new Color(230, 230, 230));
        mockStamp(g);
        banner(g, "3. Authored Raid site per industry",
                "Raid-only: a hand-laid skeleton per economic function (works, town strip, garrison, lot) "
                        + "filled from the set-piece kit and varied by seed. Most legible, least variety, most authoring.");
        g.dispose();
        return img;
    }

    // ---- drawing ----

    private static BufferedImage dim(BufferedImage src, float keep) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - keep));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.dispose();
        return out;
    }

    private static void zone(Graphics2D g, Frame f, float x0, float y0, float x1, float y1, Color fill, Color line,
                             String label) {
        int px0 = f.x(x0), px1 = f.x(x1), py0 = f.y(y1), py1 = f.y(y0);
        g.setColor(fill);
        g.fillRect(px0, py0, px1 - px0, py1 - py0);
        if (line != null) {
            g.setColor(line);
            g.setStroke(new BasicStroke(4f));
            g.drawRect(px0, py0, px1 - px0, py1 - py0);
        }
        if (label != null) tag(g, px0 + 10, py0 + 30, label, line == null ? Color.WHITE : line);
    }

    /** A lobed area between two corners, so a mock place does not read as a laid rectangle. */
    private static void blob(Graphics2D g, Frame f, float x0, float y0, float x1, float y1, Color fill, Color line,
                             String label) {
        int px0 = f.x(x0), px1 = f.x(x1), py0 = f.y(y1), py1 = f.y(y0);
        float cx = (px0 + px1) / 2f, cy = (py0 + py1) / 2f, rx = (px1 - px0) / 2f, ry = (py1 - py0) / 2f;
        Polygon p = new Polygon();
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            double k = 0.92 + 0.08 * Math.sin(a * 3 + px0) + 0.05 * Math.cos(a * 5 + py0);
            double c = Math.cos(a), s = Math.sin(a);
            // squarish superellipse
            double ex = Math.signum(c) * Math.pow(Math.abs(c), 0.45), ey = Math.signum(s) * Math.pow(Math.abs(s), 0.45);
            p.addPoint((int) Math.round(cx + ex * rx * k), (int) Math.round(cy + ey * ry * k));
        }
        g.setColor(fill);
        g.fillPolygon(p);
        g.setColor(line);
        g.setStroke(new BasicStroke(4f));
        g.drawPolygon(p);
        if (label != null) tag(g, px0 + 18, py0 + 34, label, line);
    }

    private static void machine(Graphics2D g, Frame f, float x, float y, String label) {
        int px = f.x(x), py = f.y(y);
        g.setColor(ORANGE);
        g.setStroke(new BasicStroke(5f));
        g.drawLine(px - 12, py - 12, px + 12, py + 12);
        g.drawLine(px - 12, py + 12, px + 12, py - 12);
        ring(g, px, py, 18, ORANGE, 3f);
        tag(g, px + 22, py + 6, label, ORANGE);
    }

    private static void crate(Graphics2D g, Frame f, float x, float y, String label) {
        int px = f.x(x), py = f.y(y);
        g.setColor(VIOLET);
        g.fillRect(px - 10, py - 10, 20, 20);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(px - 10, py - 10, 20, 20);
        tag(g, px + 16, py + 6, label, VIOLET);
    }

    private static void pad(Graphics2D g, Frame f, float x, float y, String label) {
        int px = f.x(x), py = f.y(y), r = 7 * f.ppc();
        g.setColor(new Color(255, 214, 40, 90));
        g.fillRect(px - r, py - r, 2 * r, 2 * r);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(5f));
        g.drawRect(px - r, py - r, 2 * r, 2 * r);
        tag(g, px - r, py + r + 26, label, GOLD);
    }

    private static void mockStamp(Graphics2D g) {
        String text = "MOCK-UP drawn over today's established Raid map (military world, seed 141418)";
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        int tw = g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(160, 20, 20, 220));
        g.fillRect(W - tw - 40, H - 52, tw + 24, 36);
        g.setColor(Color.WHITE);
        g.drawString(text, W - tw - 28, H - 26);
    }

    private static void box(Graphics2D g, Frame f, float x0, float y0, float x1, float y1, Color color,
                            boolean dashed) {
        int px0 = f.x(x0), px1 = f.x(x1), py0 = f.y(y1), py1 = f.y(y0);
        Stroke stroke = dashed
                ? new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{16f, 8f}, 0f)
                : new BasicStroke(4f);
        g.setColor(color);
        g.setStroke(stroke);
        g.drawRect(px0, py0, px1 - px0, py1 - py0);
    }

    private static void cross(Graphics2D g, int x, int y, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(5f));
        g.drawLine(x - 14, y - 14, x + 14, y + 14);
        g.drawLine(x - 14, y + 14, x + 14, y - 14);
    }

    private static void ring(Graphics2D g, int x, int y, int r, Color color, float width) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width));
        g.drawOval(x - r, y - r, 2 * r, 2 * r);
    }

    private static void arrow(Graphics2D g, int x0, int y0, int x1, int y1, Color color, boolean dashed) {
        g.setColor(color);
        g.setStroke(dashed
                ? new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{18f, 12f}, 0f)
                : new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x0, y0, x1, y1);
        double a = Math.atan2(y1 - y0, x1 - x0);
        int hx1 = (int) Math.round(x1 - 22 * Math.cos(a - 0.45)), hy1 = (int) Math.round(y1 - 22 * Math.sin(a - 0.45));
        int hx2 = (int) Math.round(x1 - 22 * Math.cos(a + 0.45)), hy2 = (int) Math.round(y1 - 22 * Math.sin(a + 0.45));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x1, y1, hx1, hy1);
        g.drawLine(x1, y1, hx2, hy2);
    }

    private static void banner(Graphics2D g, String title, String line) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        int tw = g.getFontMetrics().stringWidth(title);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        int lw = g.getFontMetrics().stringWidth(line);
        g.setColor(SHADE);
        g.fillRect(12, 12, Math.min(W - 24, Math.max(tw, lw) + 28), 86);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        g.drawString(title, 26, 50);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        g.drawString(line, 26, 84);
    }

    private static void tag(Graphics2D g, int x, int y, String text, Color color) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        int tw = g.getFontMetrics().stringWidth(text);
        g.setColor(SHADE);
        g.fillRect(x - 4, y - 19, tw + 8, 25);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }
}
