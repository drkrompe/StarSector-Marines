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
import java.util.Locale;
import java.util.Map;

/**
 * Picture options for each theme's works hall ({@code ruins-expedition.md}, A hall is laid out from its process): the
 * hall as built from its plan (a), the plan drawn over that render (a-plan), the theme's process as a chain of today's
 * halls instead (b), and today's line hall for reference. Real renders through the look preset at 1920x1080, roofs
 * off. Every theme whose dressing names a works hall, or those named by {@code -Dsnapshot.works.themes=A,B}.
 * Opt-in: {@code -Psnapshot=ruins-works-hall-picks}.
 */
public final class RuinsWorksHallPicksSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final Color LANE = new Color(150, 105, 60, 120), BAY = new Color(230, 190, 40, 90),
            PLATE = new Color(92, 108, 124, 70), WATER = new Color(60, 150, 255, 120), DROP = new Color(255, 130, 30, 150),
            FLOW = new Color(120, 230, 255), WRECK = new Color(255, 70, 60), OVER = new Color(255, 255, 255, 170),
            STATION = new Color(255, 255, 255, 230);

    @Override public String id() { return "ruins-works-hall-picks"; }

    @Override public String label() { return "Ruins works halls: each theme's hall laid from its process"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        String only = System.getProperty("snapshot.works.themes", "");
        for (RuinsVaultTheme theme : RuinsVaultTheme.values()) {
            WorksHall works = RuinsThemeDressing.of(theme).works();
            if (only.isEmpty() ? !works.any() || works == WorksHall.FORGE : !List.of(only.split(",")).contains(theme.name())) {
                continue;
            }
            String dir = theme.name().toLowerCase(Locale.ROOT).replace('_', '-') + "/";
            try (BattleSimulation sim = fixture(theme).build()) {
                RuinsDelve.Area hall = null;
                for (RuinsDelve.PlantHall h : sim.getRuinsWorks().delve().interior().halls()) {
                    if (h.stage() == RuinsDelve.PlantStage.WORKS) hall = h.footprint();
                }
                if (hall == null) throw new IllegalStateException(theme + " laid no works hall");
                WorksHallPlan plan = works.plan(hall.width(), hall.height(), RuinsDelveStage.worksSeed(hall));
                float span = Math.max(hall.width() + 10f, (hall.height() + 6f) * 16f / 9f);
                BattleReviewFrameRenderer whole = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                        hall.centreX() + .5f, hall.centreY() + .5f, span).withoutRoofs();
                BufferedImage base = whole.render(sim, "");
                BattleCamera cam = whole.cameraOf(sim);
                RuinsDelve.Area at = hall;
                out.add(new SnapshotArtifact(dir + "a-hall.png", base));
                out.add(card(dir + "a-plan.png", base, g -> {
                    drawPlan(g, cam, at, plan);
                    legend(g);
                    caption(g, "a. " + title(works) + ", laid from its process");
                }));
                // The head close, where the process starts.
                BattleReviewFrameRenderer head = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                        hall.centreX() + .5f, hall.top() - 11f, hall.width() + 22f).withoutRoofs();
                out.add(new SnapshotArtifact(dir + "a-head.png", head.render(sim, "")));
                BattleReviewFrameRenderer line = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                        hall.centreX() + .5f, hall.centreY() + .5f, hall.width() + 22f).withoutRoofs();
                out.add(new SnapshotArtifact(dir + "a-line.png", line.render(sim, "")));
            }
            // Today, and (b) the same process as a chain of today's halls.
            try (BattleSimulation sim = fixture(null).build()) {
                List<RuinsDelve.PlantHall> halls = sim.getRuinsWorks().delve().interior().halls();
                RuinsDelve.Area first = null;
                int l = Integer.MAX_VALUE, b = Integer.MAX_VALUE, r = Integer.MIN_VALUE, t = Integer.MIN_VALUE;
                List<RuinsDelve.PlantHall> chain = new ArrayList<>();
                int band = -1;
                for (RuinsDelve.PlantHall h : halls) {
                    if (h.stage() == RuinsDelve.PlantStage.STORAGE) continue;
                    if (first == null) first = h.footprint();
                    if (chain.size() < 3) {
                        chain.add(h);
                        RuinsDelve.Area f = h.footprint();
                        l = Math.min(l, f.left());
                        b = Math.min(b, f.bottom());
                        r = Math.max(r, f.right());
                        t = Math.max(t, f.top());
                    }
                }
                if (first == null) continue;
                BattleReviewFrameRenderer near = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                        first.centreX() + .5f, first.centreY() + .5f, first.width() + 20f).withoutRoofs();
                BufferedImage today = near.render(sim, "");
                out.add(card(dir + "today.png", today, g -> caption(g,
                        "Today: a line hall of modules on one slab, the same for every theme")));
                float cx = (l + r) / 2f, cy = (b + t) / 2f;
                BattleReviewFrameRenderer wide = BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx + .5f,
                        cy + .5f, Math.max(r - l + 16f, (t - b + 10f) * 16f / 9f)).withoutRoofs();
                BufferedImage chainBase = wide.render(sim, "");
                BattleCamera wc = wide.cameraOf(sim);
                List<String> steps = chainSteps(works);
                out.add(card(dir + "b-chain.png", chainBase, g -> {
                    for (int k = 0; k < chain.size(); k++) {
                        RuinsDelve.Area f = chain.get(k).footprint();
                        String step = k < steps.size() ? steps.get(k) : "";
                        box(g, wc, f, new Color(255, 255, 255, 40), (k + 1) + ". " + step);
                        if (k > 0) {
                            RuinsDelve.Area e = chain.get(k - 1).footprint();
                            arrow(g, x(wc, e.centreX() + .5f), y(wc, e.centreY() + .5f), x(wc, f.centreX() + .5f),
                                    y(wc, f.centreY() + .5f));
                        }
                    }
                    caption(g, "b. " + title(works) + " as a chain of today's halls, one step each");
                }));
            }
        }
        return out;
    }

    /** The extensive fixture for a theme's prize; a plain site with no prize, its line halls as they stand, for null. */
    private static RuinsBattleFixture fixture(RuinsVaultTheme theme) {
        RuinsVaultPrize prize = theme == null ? null : new RuinsVaultPrize(theme.family, theme, theme.itemId, 2);
        return RuinsBattleFixture.fromFactoryInputs(7L, BattleSetup.defaultManifest(), false,
                OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(SurfacePalette.ARID),
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, RuinsGrade.EXTENSIVE, RuinsOccupant.SCAVENGERS, List.of(), prize);
    }

    static String title(WorksHall works) {
        return switch (works) {
            case COLD -> "The cold hall";
            case CULTURE -> "The culture hall";
            case PRINT -> "The print hall";
            case CORE -> "The core hall";
            case FIELD -> "The field hall";
            case RING -> "The ring hall";
            case SHAFT -> "The shaft hall";
            case CRACKER -> "The reactor train";
            case DRAW -> "The draw line";
            case STAGE -> "The stage hall";
            case HIGH_BAY -> "The high bay";
            case FILLING -> "The filling hall";
            default -> works.name();
        };
    }

    private static List<String> chainSteps(WorksHall works) {
        return switch (works) {
            case COLD -> List.of("COLD PLANT + LATTICE", "CHARGE CELLS", "BURN-IN RACKS");
            case CULTURE -> List.of("TANK FARM", "VAT HALL (WET BAYS)", "HARVEST + SEALING");
            default -> List.of("INTAKE", "WORK", "FINISH");
        };
    }

    // ----- the plan over the render -----

    private static void drawPlan(Graphics2D g, BattleCamera c, RuinsDelve.Area hall, WorksHallPlan plan) {
        int l = hall.left(), b = hall.bottom();
        float cell = c.cellPxSize();
        for (int y = 0; y < plan.length(); y++) {
            for (int x = 0; x < plan.width(); x++) {
                Color fill = plan.drop(x, y) ? DROP : plan.water(x, y) ? WATER : switch (plan.zone(x, y)) {
                    case LANE -> LANE;
                    case BAY -> BAY;
                    case PLATE -> PLATE;
                    default -> null;
                };
                if (fill == null) continue;
                g.setColor(fill);
                g.fillRect(Math.round(x(c, l + x)), Math.round(y(c, b + y + 1)), (int) Math.ceil(cell),
                        (int) Math.ceil(cell));
            }
        }
        // Overheads as dashed runs.
        g.setColor(OVER);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 6f}, 0f));
        for (WorksHallPlan.Piece p : plan.pieces()) {
            if (p.layer() != WorksHallPlan.Layer.OVERHEAD) continue;
            float px = x(c, l + p.x() + p.w() / 2f);
            g.draw(new Line2D.Float(px, y(c, b + p.y()), px, y(c, b + p.y() + p.h())));
        }
        // Each station's bounds, labelled; the flow between them.
        Map<String, float[]> boxes = new LinkedHashMap<>();
        for (WorksHallPlan.Station s : plan.flow()) boxes.put(s.name(), null);
        for (WorksHallPlan.Piece p : plan.pieces()) {
            if (p.layer() != WorksHallPlan.Layer.STAND && p.layer() != WorksHallPlan.Layer.FLOOR) continue;
            if (!boxes.containsKey(p.station())) continue;
            float[] bx = boxes.get(p.station());
            if (bx == null) bx = new float[]{p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), 0, 0, 0};
            bx[0] = Math.min(bx[0], p.x());
            bx[1] = Math.min(bx[1], p.y());
            bx[2] = Math.max(bx[2], p.x() + p.w());
            bx[3] = Math.max(bx[3], p.y() + p.h());
            bx[4] += p.x() + p.w() / 2f;
            bx[5] += p.y() + p.h() / 2f;
            bx[6]++;
            boxes.put(p.station(), bx);
        }
        float[] last = null;
        int n = 1;
        for (Map.Entry<String, float[]> e : boxes.entrySet()) {
            float[] bx = e.getValue();
            if (bx == null) continue;
            float mx = bx[4] / bx[6], my = bx[5] / bx[6];
            if (last != null) arrow(g, x(c, l + last[0]), y(c, b + last[1]), x(c, l + mx), y(c, b + my));
            last = new float[]{mx, my};
            g.setColor(STATION);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(Math.round(x(c, l + bx[0])), Math.round(y(c, b + bx[3])),
                    Math.round((bx[2] - bx[0]) * cell), Math.round((bx[3] - bx[1]) * cell));
            tagAt(g, Math.round(x(c, l + mx)), Math.round(y(c, b + my)),
                    n++ + ". " + e.getKey().toUpperCase(Locale.ROOT), 15);
        }
        for (WorksHallPlan.Piece p : plan.pieces()) {
            if (!WorksHallPlan.COLLAPSE.equals(p.station())) continue;
            int px = Math.round(x(c, l + p.x() + p.w() / 2f)), py = Math.round(y(c, b + p.y() + p.h() / 2f));
            g.setColor(WRECK);
            g.setStroke(new BasicStroke(4f));
            g.drawLine(px - 10, py - 10, px + 10, py + 10);
            g.drawLine(px - 10, py + 10, px + 10, py - 10);
            tagAt(g, px, py + 26, name(p.id()), 13);
        }
    }

    private static String name(String id) {
        return id.replace("doodad.ruins-", "").replace('-', ' ').toUpperCase(Locale.ROOT);
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
        float x0 = x(c, a.left()), x1 = x(c, a.right() + 1), y0 = y(c, a.top() + 1), y1 = y(c, a.bottom());
        int rx = Math.round(x0), ry = Math.round(y0), rw = Math.round(x1 - x0), rh = Math.round(y1 - y0);
        g.setColor(fill);
        g.fillRect(rx, ry, rw, rh);
        g.setColor(STATION);
        g.setStroke(new BasicStroke(3f));
        g.drawRect(rx, ry, rw, rh);
        tagAt(g, rx + rw / 2, ry + rh / 2, label, 18);
    }

    private static void arrow(Graphics2D g, float ax, float ay, float bx, float by) {
        g.setColor(FLOW);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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
        Object[][] keys = {{LANE, "rail / lane"}, {BAY, "painted bay"}, {PLATE, "deck plate"}, {WATER, "shallow water"},
                {DROP, "drop"}, {FLOW, "process flow"}, {OVER, "overhead"}, {WRECK, "collapse"}};
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
