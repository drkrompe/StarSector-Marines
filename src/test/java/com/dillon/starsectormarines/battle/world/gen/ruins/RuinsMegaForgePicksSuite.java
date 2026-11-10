package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.fixture.RuinsBattleFixture;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
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
import java.util.List;

/**
 * Picture options for the owner's process-outward forge ({@code ruins-themed-environments.md}): today's foundry next
 * to (a) the foundry hall laid out from its process, feed to dispatch, and (b) one mega forge hall with production
 * lines down a great crucible's pour line, with the mission objective variants that hall allows. Each card is a real
 * render through the look preset at 1920x1080, roofs off, with the plan drawn over it in floor-material colours.
 * Opt-in: {@code -Psnapshot=ruins-mega-forge-picks}.
 */
public final class RuinsMegaForgePicksSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final Color PLATE = new Color(92, 108, 124, 215), GRATE = new Color(40, 44, 48, 225),
            BAY = new Color(230, 190, 40), WORN = new Color(170, 165, 155, 150), RAIL = new Color(110, 78, 50, 225),
            MELT = new Color(255, 130, 30, 235), FLOW = new Color(120, 230, 255), CRANE = new Color(255, 255, 255, 200),
            WRECK = new Color(255, 70, 60), SLAG = new Color(70, 60, 55, 230), GOAL = new Color(120, 255, 140);

    @Override public String id() { return "ruins-mega-forge-picks"; }

    @Override public String label() { return "Ruins forge: process-outward hall and mega hall picture options"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        RuinsVaultTheme theme = RuinsVaultTheme.PRISTINE_NANOFORGE;
        RuinsVaultPrize prize = new RuinsVaultPrize(theme.family, theme, theme.itemId, 2);
        RuinsBattleFixture fixture = RuinsBattleFixture.fromFactoryInputs(7L, BattleSetup.defaultManifest(), false,
                OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(SurfacePalette.ARID),
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, RuinsGrade.EXTENSIVE, RuinsOccupant.SCAVENGERS, List.of(), prize);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = fixture.build()) {
            RuinsDelve delve = sim.getRuinsWorks().delve();
            RuinsDelve.Area found = null;
            for (RuinsDelve.PlantHall h : delve.interior().halls()) {
                if (h.stage() == RuinsDelve.PlantStage.FOUNDRY) found = h.footprint();
            }
            if (found == null) throw new IllegalStateException("no foundry on the fixture's ground");
            RuinsDelve.Area hall = found;

            BattleReviewFrameRenderer near = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                    hall.centreX() + .5f, hall.centreY() + .5f, hall.width() + 8f).withoutRoofs();
            BufferedImage hallBase = near.render(sim, "");
            BattleCamera hc = near.cameraOf(sim);
            out.add(card("today-foundry.png", hallBase, g -> {
                panel(g, "Today", "Crucibles stand on a bank with nothing feeding them",
                        "Their channels end in beds with no way onward", "Slag heaps lie where the hash put them",
                        "One concrete slab under all of it");
                caption(g, "Today: the foundry. Where does the metal come from? Where do the parts go?");
            }));
            out.add(card("a-forge-line-hall.png", hallBase, g -> {
                dim(g);
                processHall(g, hc, hall);
                legend(g);
                caption(g, "a. The foundry laid out from its process: feed, melt, cast, cool, finish, dispatch");
            }));

            // The mega hall takes the production band round the foundry: the foundry and the halls beside it.
            int mw = Math.max(84, hall.width() * 5 / 2), mh = Math.max(44, hall.height() * 2);
            RuinsDelve.Area mega = new RuinsDelve.Area(hall.left(), hall.centreY() - mh / 2,
                    hall.left() + mw - 1, hall.centreY() - mh / 2 + mh - 1);
            BattleReviewFrameRenderer wide = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                    mega.centreX() + .5f, mega.centreY() + .5f, mega.width() * 1.3f).withoutRoofs();
            BufferedImage wideBase = wide.render(sim, "");
            BattleCamera wc = wide.cameraOf(sim);
            out.add(card("today-chain.png", wideBase, g -> {
                box(g, wc, mega.left(), mega.bottom(), mega.right(), mega.top(), new Color(0, 0, 0, 0), "");
                tag(g, wc, hall.centreX(), hall.top() + 1.5f, "TODAY'S FOUNDRY");
                caption(g, "Today: the chain of halls in the band the mega hall would take");
            }));
            out.add(card("b-mega-forge-hall.png", wideBase, g -> {
                dim(g);
                megaHall(g, wc, mega, 0);
                strip(g);
                caption(g, "b. One mega forge hall: a great crucible at its head, production lines down its pour line");
            }));
            out.add(card("b1-objective-lift-the-core.png", wideBase, g -> {
                dim(g);
                megaHall(g, wc, mega, 1);
                strip(g);
                panel(g, "b1. Lift the furnace core",
                        "Objective: cut the great crucible's control core free",
                        "Breach: hold the furnace while the core cools and unclamps",
                        "Haul: the core on its skid, escorted to a lift point",
                        "Lift: one Hermes load, a cradle-sized core, as today's prize");
                caption(g, "b1. The prize is one large installation: the furnace's core");
            }));
            out.add(card("b2-objective-the-line-is-the-tech.png", wideBase, g -> {
                dim(g);
                megaHall(g, wc, mega, 2);
                strip(g);
                panel(g, "b2. The line is the tech",
                        "Objective: wake the line from its pulpit and record it",
                        "Hold the pulpit while each line's recorder runs, 1 to 6",
                        "Haul: the line's record cores, carried by a marine, no skid",
                        "Lift: any lift point; a light haul, a long hold");
                caption(g, "b2. The room itself is the tech: the company takes its record, not a part of it");
            }));
            out.add(card("b3-objective-strip-the-line.png", wideBase, g -> {
                dim(g);
                megaHall(g, wc, mega, 3);
                strip(g);
                panel(g, "b3. Strip the line",
                        "Objective: pull the line's key modules (pour head, mould master, line controllers)",
                        "Each module is a haul of its own, a Hermes load each",
                        "More modules taken, more time on the ground",
                        "Pull out any time with what is aboard");
                caption(g, "b3. The line is stripped for what the Hermes can carry");
            }));
        }
        return out;
    }

    // ----- (a) the foundry from its process -----

    private static void processHall(Graphics2D g, BattleCamera c, RuinsDelve.Area a) {
        int l = a.left(), b = a.bottom();
        float sx = a.width() / 34f, sy = a.height() / 22f;
        Box z = (x0, y0, x1, y1, fill, label) -> box(g, c, l + Math.round(x0 * sx), b + Math.round(y0 * sy),
                l + Math.round(x1 * sx), b + Math.round(y1 * sy), fill, label);
        // The floor first: worn concrete aisles, deck plate under the hot bay, a painted walkway.
        z.at(1, 1, 32, 20, WORN, "");
        z.at(9, 10, 29, 20, PLATE, "");
        z.at(1, 1, 32, 2, BAY, "");
        tag(g, c, l + 16.5f * sx, b + 1.5f * sy, "PAINTED WALKWAY");
        // Feed: the scrap spur in from the intake dock, the charge bins and the furnace.
        z.at(1, 3, 4, 18, RAIL, "SCRAP RAIL SPUR");
        arrow(g, c, l - 4f, b + 10.5f * sy, l + 2.5f * sx, b + 10.5f * sy, "from intake dock");
        z.at(5, 13, 8, 18, PLATE, "CHARGE BINS");
        arrow(g, c, l + 4.5f * sx, b + 15.5f * sy, l + 9f * sx, b + 16f * sy, "");
        z.at(9, 13, 14, 20, MELT, "TAPPING FURNACE");
        // Slag: tapped off the furnace's foot down a car track to the pit.
        z.at(11, 8, 11, 12, RAIL, "");
        z.at(9, 3, 13, 7, SLAG, "SLAG PIT");
        // Melt: a ladle crane runs from the furnace's tap down the bay.
        rail(g, c, l + 9f * sx, b + 19.5f * sy, l + 30f * sx, b + 19.5f * sy);
        rail(g, c, l + 9f * sx, b + 10.5f * sy, l + 30f * sx, b + 10.5f * sy);
        tag(g, c, l + 22f * sx, b + 20.6f * sy, "OVERHEAD LADLE CRANE RUNWAY");
        crucible(g, c, l + 16f * sx, b + 15f * sy, false, "LADLE");
        crucible(g, c, l + 22f * sx, b + 15f * sy, true, "LADLE TIPPED MID-POUR");
        z.at(15, 11, 27, 12, MELT, "");
        z.at(15, 11, 27, 12, GRATE, "");
        tag(g, c, l + 21f * sx, b + 12.8f * sy, "POUR TRENCH UNDER GRATING");
        // Cast: the mould grid at the trench's foot, one bed half-filled.
        z.at(15, 3, 25, 9, GRATE, "MOULD GRID");
        z.at(15, 3, 18, 6, MELT, "");
        wreck(g, c, l + 16.5f * sx, b + 4.5f * sy, "HALF-FILLED");
        arrow(g, c, l + 21f * sx, b + 10.5f * sy, l + 21f * sx, b + 9.2f * sy, "");
        // Cool, finish, dispatch.
        z.at(26, 3, 29, 12, PLATE, "COOLING RACKS");
        wreck(g, c, l + 27.5f * sx, b + 7f * sy, "CASTINGS LEFT");
        arrow(g, c, l + 25.2f * sx, b + 6f * sy, l + 26.2f * sx, b + 6f * sy, "");
        z.at(30, 9, 32, 16, BAY, "FETTLING + INSPECTION");
        arrow(g, c, l + 29.2f * sx, b + 11f * sy, l + 30.2f * sx, b + 11f * sy, "");
        arrow(g, c, l + 31f * sx, b + 16.5f * sy, a.right() + 4f, b + 18f * sy, "dispatch: to storage hall");
        // The collapse: a crane girder down across the bay.
        g.setColor(WRECK);
        g.setStroke(new BasicStroke(7f));
        g.draw(new Line2D.Float(x(c, l + 19f * sx), y(c, b + 12.5f * sy), x(c, l + 26f * sx), y(c, b + 9.5f * sy)));
        tag(g, c, l + 27.5f * sx, b + 14f * sy, "CRANE GIRDER DOWN (cover)");
    }

    // ----- (b) the mega hall -----

    private static void megaHall(Graphics2D g, BattleCamera c, RuinsDelve.Area m, int objective) {
        int l = m.left(), b = m.bottom();
        float sx = m.width() / 84f, sy = m.height() / 44f;
        Box z = (x0, y0, x1, y1, fill, label) -> box(g, c, l + Math.round(x0 * sx), b + Math.round(y0 * sy),
                l + Math.round(x1 * sx), b + Math.round(y1 * sy), fill, label);
        z.at(0, 0, 83, 43, WORN, "");
        box(g, c, l, b, m.right(), m.top(), new Color(0, 0, 0, 0), "MEGA FORGE HALL " + m.width() + "x" + m.height());
        z.at(14, 17, 76, 27, PLATE, "");
        z.at(1, 1, 82, 2, BAY, "");
        z.at(1, 41, 82, 42, BAY, "");
        // Feed: the ore rail from the intake, the hoppers, the great crucible; slag to its pit.
        z.at(0, 30, 3, 40, RAIL, "ORE RAIL");
        arrow(g, c, l - 6f, b + 35f * sy, l + 1.5f * sx, b + 35f * sy, "from intake");
        z.at(4, 32, 12, 40, PLATE, "CHARGE HOPPERS");
        z.at(2, 13, 14, 30, MELT, "GREAT CRUCIBLE");
        z.at(2, 3, 11, 10, SLAG, "SLAG PIT");
        z.at(6, 10, 7, 13, RAIL, "");
        // The pour line: a trench the length of the hall under the crane bay.
        z.at(14, 21, 74, 23, MELT, "");
        tag(g, c, l + 62f * sx, b + 24.6f * sy, "POUR LINE (trench, grated crossings)");
        rail(g, c, l + 14f * sx, b + 27.5f * sy, l + 76f * sx, b + 27.5f * sy);
        rail(g, c, l + 14f * sx, b + 16.5f * sy, l + 76f * sx, b + 16.5f * sy);
        tag(g, c, l + 50f * sx, b + 18.2f * sy, "OVERHEAD CRANE BAY");
        // Production lines off the pour line, three each side: branch, mould, cooling, machining, out to the aisle.
        int[] xs = {18, 38, 58};
        for (int side = 0; side < 2; side++) {
            for (int k = 0; k < 3; k++) {
                int x = xs[k];
                boolean north = side == 0;
                int y0 = north ? 28 : 4, y1 = north ? 40 : 16;
                z.at(x + 1, north ? 24 : 16, x + 2, north ? 28 : 20, MELT, "");
                z.at(x, north ? 29 : 12, x + 6, north ? 33 : 15, GRATE, "MOULDS");
                z.at(x + 7, y0, x + 11, y1, PLATE, "COOLING");
                z.at(x + 12, north ? 30 : 6, x + 16, north ? 38 : 14, BAY, "MACHINING");
                arrow(g, c, l + (x + 14f) * sx, b + (north ? 38.5f : 5.5f) * sy, l + (x + 14f) * sx,
                        b + (north ? 41.5f : 1.5f) * sy, "");
            }
        }
        arrow(g, c, l + 60f * sx, b + 41.5f * sy, l + 78f * sx, b + 41.5f * sy, "");
        arrow(g, c, l + 60f * sx, b + 1.5f * sy, l + 78f * sx, b + 1.5f * sy, "");
        z.at(77, 4, 82, 39, BAY, "DISPATCH");
        arrow(g, c, l + 82.5f * sx, b + 22f * sy, m.right() + 6f, b + 22f * sy, "to storage / lift court");
        z.at(15, 35, 19, 40, PLATE, "PULPIT");
        // The collapse along the line.
        crucible(g, c, l + 40f * sx, b + 22f * sy, true, "LADLE TIPPED ON THE CRANE");
        wreck(g, c, l + 60f * sx, b + 10f * sy, "ROOF FALL ACROSS LINE 6");
        wreck(g, c, l + 21f * sx, b + 31f * sy, "MOULD HALF-FILLED");
        if (objective == 1) {
            goal(g, c, l + 6f * sx, b + 14f * sy, l + 10f * sx, b + 18f * sy, "FURNACE CORE");
            route(g, c, new float[][]{{l + 10f * sx, b + 16f * sy}, {l + 14f * sx, b + 19f * sy},
                    {l + 76f * sx, b + 19f * sy}, {m.right() + 6f, b + 22f * sy}});
        } else if (objective == 2) {
            goal(g, c, l + 15f * sx, b + 35f * sy, l + 19f * sx, b + 40f * sy, "PULPIT: HOLD");
            int n = 1;
            for (int side = 0; side < 2; side++) {
                for (int x : xs) {
                    float rx = l + (x + 9f) * sx, ry = b + (side == 0 ? 37f : 6f) * sy;
                    badge(g, c, rx, ry, String.valueOf(n++));
                }
            }
        } else if (objective == 3) {
            goal(g, c, l + 13f * sx, b + 20f * sy, l + 15f * sx, b + 24f * sy, "POUR HEAD");
            goal(g, c, l + 38f * sx, b + 29f * sy, l + 44f * sx, b + 33f * sy, "MOULD MASTER");
            goal(g, c, l + 70f * sx, b + 6f * sy, l + 74f * sx, b + 14f * sy, "LINE CONTROLLER");
            goal(g, c, l + 30f * sx, b + 30f * sy, l + 34f * sx, b + 38f * sy, "LINE CONTROLLER");
        }
    }

    // ----- drawing -----

    private interface Box { void at(int x0, int y0, int x1, int y1, Color fill, String label); }

    private interface Paint { void on(Graphics2D g); }

    private static float x(BattleCamera c, float cx) { return c.cellToScreenX(cx); }

    private static float y(BattleCamera c, float cy) { return H - c.cellToScreenY(cy); }

    private static void box(Graphics2D g, BattleCamera c, int l, int b, int r, int t, Color fill, String label) {
        float x0 = x(c, l), x1 = x(c, r + 1), y0 = y(c, t + 1), y1 = y(c, b);
        int rx = Math.round(x0), ry = Math.round(y0), rw = Math.round(x1 - x0), rh = Math.round(y1 - y0);
        if (fill.getAlpha() > 0) {
            g.setColor(fill);
            g.fillRect(rx, ry, rw, rh);
            if (fill == GRATE) {
                g.setColor(new Color(120, 125, 130, 200));
                g.setStroke(new BasicStroke(1.5f));
                for (int k = rx; k < rx + rw; k += 6) g.drawLine(k, ry, k, ry + rh);
            } else if (fill == RAIL) {
                g.setColor(new Color(200, 200, 205, 220));
                g.setStroke(new BasicStroke(2.5f));
                boolean tall = rh > rw;
                if (tall) {
                    g.drawLine(rx + rw / 3, ry, rx + rw / 3, ry + rh);
                    g.drawLine(rx + 2 * rw / 3, ry, rx + 2 * rw / 3, ry + rh);
                } else {
                    g.drawLine(rx, ry + rh / 3, rx + rw, ry + rh / 3);
                    g.drawLine(rx, ry + 2 * rh / 3, rx + rw, ry + 2 * rh / 3);
                }
            }
        }
        g.setColor(new Color(15, 15, 15, 220));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(rx, ry, rw, rh);
        if (!label.isEmpty()) tagAt(g, rx + rw / 2, ry + rh / 2, label, 15);
    }

    private static void rail(Graphics2D g, BattleCamera c, float x0, float y0, float x1, float y1) {
        g.setColor(CRANE);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{14f, 8f}, 0f));
        g.draw(new Line2D.Float(x(c, x0), y(c, y0), x(c, x1), y(c, y1)));
        g.setStroke(new BasicStroke(1f));
    }

    private static void arrow(Graphics2D g, BattleCamera c, float x0, float y0, float x1, float y1, String label) {
        float ax = x(c, x0), ay = y(c, y0), bx = x(c, x1), by = y(c, y1);
        g.setColor(FLOW);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(ax, ay, bx, by));
        double ang = Math.atan2(by - ay, bx - ax);
        for (int s = -1; s <= 1; s += 2) {
            double a = ang + Math.PI + s * .45;
            g.draw(new Line2D.Float(bx, by, (float) (bx + 16 * Math.cos(a)), (float) (by + 16 * Math.sin(a))));
        }
        if (!label.isEmpty()) tagAt(g, Math.round((ax + bx) / 2), Math.round((ay + by) / 2) - 18, label, 15);
    }

    private static void route(Graphics2D g, BattleCamera c, float[][] pts) {
        g.setColor(GOAL);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{18f, 10f}, 0f));
        for (int k = 1; k < pts.length; k++) {
            g.draw(new Line2D.Float(x(c, pts[k - 1][0]), y(c, pts[k - 1][1]), x(c, pts[k][0]), y(c, pts[k][1])));
        }
        tagAt(g, Math.round(x(c, pts[2][0])), Math.round(y(c, pts[2][1])) + 22, "HAUL ROUTE", 15);
    }

    private static void crucible(Graphics2D g, BattleCamera c, float cx, float cy, boolean tipped, String label) {
        int px = Math.round(x(c, cx)), py = Math.round(y(c, cy)), r = Math.round(c.cellPxSize() * 1.4f);
        g.setColor(new Color(60, 55, 52));
        g.fillOval(px - r, py - r, 2 * r, 2 * r);
        g.setColor(MELT);
        g.fillOval(px - r / 2, py - r / 2, r, r);
        if (tipped) {
            g.fillOval(px + r / 2, py - r / 3, r * 2, r);
            g.setColor(WRECK);
            g.setStroke(new BasicStroke(4f));
            g.drawOval(px - r - 4, py - r - 4, 2 * r + 8, 2 * r + 8);
        }
        tagAt(g, px, py - r - 14, label, 15);
    }

    private static void wreck(Graphics2D g, BattleCamera c, float cx, float cy, String label) {
        int px = Math.round(x(c, cx)), py = Math.round(y(c, cy));
        g.setColor(WRECK);
        g.setStroke(new BasicStroke(5f));
        g.drawLine(px - 12, py - 12, px + 12, py + 12);
        g.drawLine(px - 12, py + 12, px + 12, py - 12);
        tagAt(g, px, py + 30, label, 14);
    }

    private static void goal(Graphics2D g, BattleCamera c, float l, float b, float r, float t, String label) {
        int x0 = Math.round(x(c, l)), y0 = Math.round(y(c, t)), x1 = Math.round(x(c, r)), y1 = Math.round(y(c, b));
        g.setColor(GOAL);
        g.setStroke(new BasicStroke(5f));
        g.drawRect(x0 - 4, y0 - 4, x1 - x0 + 8, y1 - y0 + 8);
        tagAt(g, (x0 + x1) / 2, y0 - 18, label, 16);
    }

    private static void badge(Graphics2D g, BattleCamera c, float cx, float cy, String text) {
        int px = Math.round(x(c, cx)), py = Math.round(y(c, cy));
        g.setColor(GOAL);
        g.fillOval(px - 15, py - 15, 30, 30);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(text, px - g.getFontMetrics().stringWidth(text) / 2, py + 6);
    }

    private static void tag(Graphics2D g, BattleCamera c, float cx, float cy, String label) {
        tagAt(g, Math.round(x(c, cx)), Math.round(y(c, cy)), label, 15);
    }

    private static void tagAt(Graphics2D g, int cx, int cy, String label, int size) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        int tw = g.getFontMetrics().stringWidth(label);
        g.setColor(new Color(0, 0, 0, 185));
        g.fillRoundRect(cx - tw / 2 - 6, cy - size + 1, tw + 12, size + 8, 8, 8);
        g.setColor(Color.WHITE);
        g.drawString(label, cx - tw / 2, cy + 3);
    }

    private static void panel(Graphics2D g, String title, String... lines) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 19));
        int w = 0;
        for (String s : lines) w = Math.max(w, g.getFontMetrics().stringWidth(s));
        int h = 44 + lines.length * 28, x = W - w - 60, y = 12;
        g.setColor(new Color(0, 0, 0, 205));
        g.fillRoundRect(x, y, w + 36, h, 14, 14);
        g.setColor(GOAL);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
        g.drawString(title, x + 18, y + 30);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 19));
        for (int k = 0; k < lines.length; k++) g.drawString(lines[k], x + 18, y + 60 + k * 28);
    }

    private static void legend(Graphics2D g) {
        Object[][] rows = {{PLATE, "deck plate"}, {GRATE, "grating over a trench"}, {BAY, "painted bay / walkway"},
                {WORN, "worn concrete (aisles only)"}, {RAIL, "rail bed"}, {MELT, "melt"}, {SLAG, "slag"},
                {FLOW, "process flow"}, {CRANE, "overhead crane runway"}, {WRECK, "the collapse"}};
        int x = 30, y = H - 30 - rows.length * 26;
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(x - 12, y - 30, 300, rows.length * 26 + 26, 12, 12);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        for (int k = 0; k < rows.length; k++) {
            g.setColor((Color) rows[k][0]);
            g.fillRect(x, y - 14 + k * 26, 22, 16);
            g.setColor(Color.WHITE);
            g.drawString((String) rows[k][1], x + 34, y + k * 26);
        }
    }

    /** The legend as a strip along the bottom, under a wide view's hall. */
    private static void strip(Graphics2D g) {
        Object[][] rows = {{PLATE, "deck plate"}, {GRATE, "grating"}, {BAY, "painted bay"}, {WORN, "worn concrete"},
                {RAIL, "rail bed"}, {MELT, "melt"}, {SLAG, "slag"}, {FLOW, "process flow"}, {CRANE, "crane runway"},
                {WRECK, "collapse"}};
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        int x = 40, y = H - 22;
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(24, H - 50, W - 48, 40, 12, 12);
        for (Object[] row : rows) {
            g.setColor((Color) row[0]);
            g.fillRect(x, y - 14, 22, 16);
            g.setColor(Color.WHITE);
            g.drawString((String) row[1], x + 30, y);
            x += 30 + g.getFontMetrics().stringWidth((String) row[1]) + 40;
        }
    }

    private static void dim(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, 0, W, H);
    }

    private static void caption(Graphics2D g, String text) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        int tw = g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(24, 18, tw + 40, 52, 14, 14);
        g.setColor(Color.WHITE);
        g.drawString(text, 44, 53);
    }

    private static SnapshotArtifact card(String file, BufferedImage base, Paint paint) {
        BufferedImage card = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = card.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paint.on(g);
        g.dispose();
        return new SnapshotArtifact(file, card);
    }
}
