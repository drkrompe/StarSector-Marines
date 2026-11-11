package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.fixture.RuinsBattleFixture;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.ruins.RuinsThemeDressing;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The rooms off a plant's corridors as built ({@code ruins-expedition.md}, Every room serves the process): per theme,
 * the interior with each room named by its role, and each role's room close, as built and with its plan drawn over
 * it (its steps boxed and joined in process order, its collapse crossed, its spill tinted). Real renders through the
 * look preset at 1920x1080, roofs off. The themes named by {@code -Dsnapshot.rooms.themes=A,B}, else a spread of five.
 * Opt-in: {@code -Psnapshot=ruins-room-look}.
 */
public final class RuinsRoomLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final List<String> DEFAULT_THEMES = List.of("CRYOARITHMETIC_ENGINE", "SOIL_NANITES", "MANTLE_BORE",
            "SERVER_HALL", "DRONE_REPLICATOR");
    private static final Color FLOW = new Color(120, 230, 255), WRECK = new Color(255, 70, 60),
            STATION = new Color(255, 255, 255, 230), SPILL = new Color(160, 255, 120, 70), OVER = new Color(255, 255, 255, 170);
    private static final Map<RuinsRoom, Color> TINT = Map.of(RuinsRoom.CONTROL, new Color(80, 170, 255, 80),
            RuinsRoom.WORKSHOP, new Color(255, 170, 60, 80), RuinsRoom.LAB, new Color(120, 255, 160, 80),
            RuinsRoom.CREW, new Color(255, 110, 200, 80), RuinsRoom.STORE, new Color(230, 220, 90, 80));

    @Override public String id() { return "ruins-room-look"; }

    @Override public String label() { return "Ruins rooms: each role's room as built, and its plan"; }

    @Override public boolean inAll() { return false; }

    /** One room as the ground laid it. */
    private record Laid(RuinsRoom kind, RuinsDelve.Area area, RuinsDelve.Cell keep, int q) {}

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        String only = System.getProperty("snapshot.rooms.themes", "");
        List<String> themes = only.isEmpty() ? DEFAULT_THEMES : List.of(only.split(","));
        for (String name : themes) {
            RuinsVaultTheme theme = RuinsVaultTheme.valueOf(name);
            WorksHall works = RuinsThemeDressing.of(theme).works();
            String dir = theme.name().toLowerCase(Locale.ROOT).replace('_', '-') + "/";
            try (BattleSimulation sim = fixture(theme).build()) {
                RuinsDelve delve = sim.getRuinsWorks().delve();
                List<Laid> rooms = rooms(delve);
                RuinsDelve.Area anchor = RuinsRoom.anchor(delve.interior().halls(), delve.vault().footprint());
                // The interior: every room named by its role, the hall it serves.
                RuinsDelve.Area box = delve.interior().footprint();
                float span = Math.max(box.width() + 6f, (box.height() + 6f) * 16f / 9f);
                BattleReviewFrameRenderer whole = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                        box.centreX() + .5f, box.centreY() + .5f, span).withoutRoofs();
                BufferedImage base = whole.render(sim, "");
                BattleCamera cam = whole.cameraOf(sim);
                Map<String, RuinsDelve.Area> spaces = delve.interior().spaces();
                if (!spaces.isEmpty()) {
                    // The plant laid from its brief: every space by its name and size.
                    out.add(card(dir + "plant.png", base, g -> {
                        box(g, cam, anchor, new Color(255, 255, 255, 40), "WORKS HALL");
                        for (Map.Entry<String, RuinsDelve.Area> e : spaces.entrySet()) {
                            RuinsDelve.Area a = e.getValue();
                            String label = e.getKey().substring(RuinsDelve.INTERIOR.length() + 1).toUpperCase(Locale.ROOT);
                            box(g, cam, a, new Color(255, 210, 90, 70), label + " " + a.width() + "x" + a.height());
                        }
                        caption(g, RuinsWorksHallLookSuite.title(works) + "'s plant, laid from its brief");
                    }));
                    // Each store close, stocked from its brief, and the ruin: the collapsed bay and a breach.
                    for (String store : List.of("raw-stock", "finished-goods", "collapsed-bay", "breached-bay-1")) {
                        RuinsDelve.Area a = spaces.get(RuinsDelve.Interior.spaceName(store));
                        if (a == null) continue;
                        float ss = Math.max(a.width() + 10f, (a.height() + 8f) * 16f / 9f);
                        out.add(new SnapshotArtifact(dir + store + ".png", BattleReviewFrameRenderer.look(
                                context.modRoot(), W, H, a.centreX() + .5f, a.centreY() + .5f, ss).withoutRoofs()
                                .render(sim, "")));
                    }
                }
                out.add(card(dir + "rooms.png", base, g -> {
                    box(g, cam, anchor, new Color(255, 255, 255, 40), "WORKS HALL");
                    for (Laid r : rooms) {
                        box(g, cam, r.area(), TINT.get(r.kind()), r.kind().name());
                        arrow(g, x(cam, r.area().centreX() + .5f), y(cam, r.area().centreY() + .5f),
                                x(cam, anchor.centreX() + .5f), y(cam, anchor.centreY() + .5f), new Color(255, 255, 255, 70), 2f);
                    }
                    caption(g, RuinsWorksHallLookSuite.title(works) + "'s rooms, each by its role");
                }));
                // Each role's room close: the first of its kind, nearest the hall.
                for (RuinsRoom kind : RuinsRoom.values()) {
                    Laid r = rooms.stream().filter(l -> l.kind() == kind).findFirst().orElse(null);
                    if (r == null) continue;
                    WorksHallPlan plan = kind.laid(works, r.area(), r.keep(), r.q());
                    float rs = Math.max(r.area().width() + 10f, (r.area().height() + 8f) * 16f / 9f);
                    BattleReviewFrameRenderer close = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                            r.area().centreX() + .5f, r.area().centreY() + .5f, rs).withoutRoofs();
                    BufferedImage shot = close.render(sim, "");
                    BattleCamera cc = close.cameraOf(sim);
                    String file = kind.name().toLowerCase(Locale.ROOT);
                    out.add(new SnapshotArtifact(dir + file + ".png", shot));
                    out.add(card(dir + file + "-plan.png", shot, g -> {
                        drawPlan(g, cc, r.area(), plan);
                        legend(g);
                        caption(g, "The " + kind.name().toLowerCase(Locale.ROOT) + " room, laid from its role");
                    }));
                }
            }
        }
        return out;
    }

    /**
     * The rooms the ground laid: the plain chambers by rank (or, on a plant laid from its brief, its spaces by their
     * roles, the maintenance shop as a workshop), the stores inside the megastructure.
     */
    private static List<Laid> rooms(RuinsDelve delve) {
        RuinsDelve.Interior interior = delve.interior();
        List<RuinsDelve.Area> plain = new ArrayList<>(interior.chambers());
        for (RuinsDelve.PlantHall hall : interior.halls()) plain.remove(hall.footprint());
        RuinsDelve.Area anchor = RuinsRoom.anchor(interior.halls(), delve.vault().footprint());
        List<RuinsDelve.Area> ranked = RuinsRoom.ranked(plain, anchor);
        Map<RuinsDelve.Area, RuinsRoom> roles = new HashMap<>();
        if (!interior.spaces().isEmpty()) {
            Map<String, RuinsRoom> byName = new HashMap<>();
            for (int band = 0; band < 2; band++) {
                for (PlantBrief.Space s : PlantBrief.spaces(band, 2, 3)) {
                    RuinsRoom role = s.stage() == RuinsDelve.PlantStage.MAINTENANCE ? RuinsRoom.WORKSHOP : s.role();
                    if (role != null) byName.put(RuinsDelve.Interior.spaceName(s.name()), role);
                }
            }
            for (Map.Entry<String, RuinsDelve.Area> e : interior.spaces().entrySet()) {
                if (byName.containsKey(e.getKey())) roles.put(e.getValue(), byName.get(e.getKey()));
            }
            ranked = new ArrayList<>(roles.keySet());
            ranked.sort(Comparator.comparingInt(RuinsDelve.Area::centreX).thenComparingInt(RuinsDelve.Area::centreY));
        }
        List<Laid> out = new ArrayList<>();
        for (int rank = 0; rank < ranked.size(); rank++) {
            RuinsDelve.Area a = ranked.get(rank);
            RuinsRoom kind = roles.isEmpty() ? RuinsRoom.chamberRole(rank) : roles.get(a);
            out.add(new Laid(kind, a, new RuinsDelve.Cell(a.centreX(), a.centreY()),
                    RuinsRoom.facing(anchor.centreX() - a.centreX(), anchor.centreY() - a.centreY())));
        }
        for (RuinsDelve.Layer layer : delve.layers()) {
            if (!interior.contains(layer.index())) continue;
            for (RuinsDelve.SideRoom side : layer.rooms()) {
                RuinsDelve.Area f = side.footprint();
                RuinsDelve.Area inner = new RuinsDelve.Area(f.left() + 1, f.bottom() + 1, f.right() - 1, f.top() - 1);
                out.add(new Laid(RuinsRoom.STORE, inner, side.interior(), side.door().y() == f.top() ? 2 : 0));
            }
        }
        return out;
    }

    private static RuinsBattleFixture fixture(RuinsVaultTheme theme) {
        RuinsVaultPrize prize = new RuinsVaultPrize(theme.family, theme, theme.itemId, 2);
        return RuinsBattleFixture.fromFactoryInputs(7L, BattleSetup.defaultManifest(), false,
                OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(SurfacePalette.ARID),
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, RuinsGrade.EXTENSIVE, RuinsOccupant.SCAVENGERS, List.of(), prize);
    }

    // ----- the plan over the render -----

    private static void drawPlan(Graphics2D g, BattleCamera c, RuinsDelve.Area area, WorksHallPlan plan) {
        int l = area.left(), b = area.bottom();
        float cell = c.cellPxSize();
        g.setColor(SPILL);
        for (int y = 0; y < plan.length(); y++) {
            for (int x = 0; x < plan.width(); x++) {
                if (plan.skin(x, y) == null) continue;
                g.fillRect(Math.round(x(c, l + x)), Math.round(y(c, b + y + 1)), (int) Math.ceil(cell), (int) Math.ceil(cell));
            }
        }
        g.setColor(OVER);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 6f}, 0f));
        for (WorksHallPlan.Piece p : plan.pieces()) {
            if (p.layer() != WorksHallPlan.Layer.OVERHEAD) continue;
            g.drawRect(Math.round(x(c, l + p.x())), Math.round(y(c, b + p.y() + p.h())), Math.round(p.w() * cell),
                    Math.round(p.h() * cell));
        }
        Map<String, float[]> centres = new LinkedHashMap<>();
        for (WorksHallPlan.Station s : plan.flow()) centres.put(s.name(), null);
        for (WorksHallPlan.Piece p : plan.pieces()) {
            if (p.layer() != WorksHallPlan.Layer.STAND && p.layer() != WorksHallPlan.Layer.FLOOR) continue;
            if (!centres.containsKey(p.station())) continue;
            g.setColor(STATION);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(Math.round(x(c, l + p.x())), Math.round(y(c, b + p.y() + p.h())), Math.round(p.w() * cell),
                    Math.round(p.h() * cell));
            float[] m = centres.get(p.station());
            if (m == null) m = new float[3];
            m[0] += p.x() + p.w() / 2f;
            m[1] += p.y() + p.h() / 2f;
            m[2]++;
            centres.put(p.station(), m);
        }
        float[] last = null;
        int n = 1;
        for (Map.Entry<String, float[]> e : centres.entrySet()) {
            float[] m = e.getValue();
            if (m == null) continue;
            float mx = m[0] / m[2], my = m[1] / m[2];
            if (last != null) arrow(g, x(c, l + last[0]), y(c, b + last[1]), x(c, l + mx), y(c, b + my), FLOW, 4f);
            last = new float[]{mx, my};
            tagAt(g, Math.round(x(c, l + mx)), Math.round(y(c, b + my)), n++ + ". " + e.getKey().toUpperCase(Locale.ROOT), 16);
        }
        for (WorksHallPlan.Piece p : plan.pieces()) {
            if (!WorksHallPlan.COLLAPSE.equals(p.station())) continue;
            int px = Math.round(x(c, l + p.x() + p.w() / 2f)), py = Math.round(y(c, b + p.y() + p.h() / 2f));
            g.setColor(WRECK);
            g.setStroke(new BasicStroke(4f));
            g.drawLine(px - 12, py - 12, px + 12, py + 12);
            g.drawLine(px - 12, py + 12, px + 12, py - 12);
            tagAt(g, px, py + 30, p.id().replace("doodad.ruins-", "").replace("doodad.", "").replace('-', ' ')
                    .toUpperCase(Locale.ROOT), 14);
        }
    }

    // ----- drawing -----

    private interface Paint { void on(Graphics2D g); }

    private static SnapshotArtifact card(String path, BufferedImage base, Paint paint) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(base, 0, 0, null);
        paint.on(g);
        g.dispose();
        return new SnapshotArtifact(path, img);
    }

    private static float x(BattleCamera c, float cx) { return c.cellToScreenX(cx); }

    private static float y(BattleCamera c, float cy) { return H - c.cellToScreenY(cy); }

    private static void box(Graphics2D g, BattleCamera c, RuinsDelve.Area a, Color fill, String label) {
        int x0 = Math.round(x(c, a.left())), y0 = Math.round(y(c, a.top() + 1));
        int w = Math.round(x(c, a.right() + 1) - x(c, a.left())), h = Math.round(y(c, a.bottom()) - y(c, a.top() + 1));
        g.setColor(fill);
        g.fillRect(x0, y0, w, h);
        g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue()));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(x0, y0, w, h);
        tagAt(g, x0 + w / 2, y0 + h / 2, label, 13);
    }

    private static void arrow(Graphics2D g, float ax, float ay, float bx, float by, Color col, float width) {
        g.setColor(col);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(ax, ay, bx, by));
        double ang = Math.atan2(by - ay, bx - ax);
        for (int s = -1; s <= 1; s += 2) {
            double a = ang + Math.PI + s * .45;
            g.draw(new Line2D.Float(bx, by, (float) (bx + 14 * Math.cos(a)), (float) (by + 14 * Math.sin(a))));
        }
    }

    private static void tagAt(Graphics2D g, int cx, int cy, String label, int size) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        int tw = g.getFontMetrics().stringWidth(label);
        g.setColor(new Color(0, 0, 0, 185));
        g.fillRoundRect(cx - tw / 2 - 6, cy - size + 1, tw + 12, size + 8, 8, 8);
        g.setColor(Color.WHITE);
        g.drawString(label, cx - tw / 2, cy + 3);
    }

    private static void caption(Graphics2D g, String text) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        int tw = g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(0, 0, 0, 205));
        g.fillRoundRect(24, 18, tw + 40, 52, 12, 12);
        g.setColor(Color.WHITE);
        g.drawString(text, 44, 54);
    }

    private static void legend(Graphics2D g) {
        Object[][] keys = {{STATION, "a piece, by its step"}, {FLOW, "process flow"}, {OVER, "overhead"},
                {WRECK, "collapse"}, {SPILL, "spill"}};
        g.setColor(new Color(0, 0, 0, 205));
        g.fillRoundRect(24, H - 56, W - 48, 44, 10, 10);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        int x = 40;
        for (Object[] k : keys) {
            Color col = (Color) k[0];
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue()));
            g.fillRect(x, H - 42, 26, 16);
            g.setColor(Color.WHITE);
            g.drawString((String) k[1], x + 34, H - 28);
            x += 60 + g.getFontMetrics().stringWidth((String) k[1]);
        }
    }
}
